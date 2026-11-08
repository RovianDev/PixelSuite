package io.github.pixelsuite;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.InputConnection;

final class GboardSelectMenu {
    private GboardSelectMenu() { }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int sFrom = -1, sTo = -1, sCaret = -1;
    private static boolean sAll, sComposed;
    private static boolean sWatchedBefore, sReporting;
    private static boolean sTouchedReq, sSecondPass;
    private static int sMarkFrom = -1, sMarkTo = -1;
    private static volatile CursorAnchorInfo sLatest;
    private static long sAskedAt, sOursAt, sLastSeen, sToken;
    private static boolean sInstalled;

    static synchronized void install() {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Xp.hookAllMethods(InputMethodService.class, "onUpdateCursorAnchorInfo", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length == 0 || !(p.args[0] instanceof CursorAnchorInfo)) return;
                    long now = SystemClock.uptimeMillis();
                    InputMethodService ims = (InputMethodService) p.thisObject;
                    if (!sReporting && now - sOursAt > 400) sLastSeen = now;
                    if (sFrom < 0 || now - sAskedAt > 600) return;
                    CursorAnchorInfo info = (CursorAnchorInfo) p.args[0];
                    if (sComposed && !marked(info)) { sLatest = info; return; }
                    consume(ims, info);
                }
            });
        } catch (Throwable ignored) { }
    }


    private static boolean marked(CursorAnchorInfo info) {
        CharSequence c = info.getComposingText();
        return info.getComposingTextStart() == sMarkFrom && c != null && c.length() == sMarkTo - sMarkFrom;
    }

    private static void consume(InputMethodService ims, CursorAnchorInfo info) {
        int from = sFrom, to = sTo, caret = sCaret; boolean all = sAll, composed = sComposed, touched = sTouchedReq;
        if (from < 0) return;
        sFrom = sTo = -1; sLatest = null; sToken++;
        try { run(ims, info, from, to, caret, all, composed, touched); }
        catch (Throwable t) { set(ims, from, to); doneReporting(ims); }
    }

    static boolean select(final InputMethodService ims, InputConnection ic, final int from, final int to, int caret, boolean all) {
        if (ims == null || ic == null || from < 0 || to <= from || android.os.Build.VERSION.SDK_INT < 34) return false;
        install();
        final long token = ++sToken;
        sTouchedReq = sSecondPass; sSecondPass = false;
        sFrom = from; sTo = to; sCaret = caret; sAll = all; sComposed = false; sMarkFrom = sMarkTo = -1; sLatest = null; sAskedAt = sOursAt = SystemClock.uptimeMillis();
        sWatchedBefore = SystemClock.uptimeMillis() - sLastSeen < 5000; sReporting = true;
        int mode = InputConnection.CURSOR_UPDATE_IMMEDIATE | InputConnection.CURSOR_UPDATE_MONITOR;
        boolean asked = false;
        try {
            if (!all) { sMarkFrom = from; sMarkTo = to; ic.setComposingRegion(from, to); sComposed = true; }
            else { int a = caret > 0 ? Math.min(caret, to) - 1 : 0; if (a >= from && a + 1 <= to) { sMarkFrom = a; sMarkTo = a + 1; ic.setComposingRegion(a, a + 1); sComposed = true; } }
            asked = ic.requestCursorUpdates(mode);
            if (!asked && sComposed) ic.finishComposingText();
        } catch (Throwable ignored) { }
        if (!asked) { sFrom = sTo = -1; sReporting = false; return false; }
        MAIN.postDelayed(new Runnable() { public void run() {
            CursorAnchorInfo latest = sLatest;
            if (sToken != token || sFrom < 0 || latest == null) return;
            consume(ims, latest);
        }}, 300);
        MAIN.postDelayed(new Runnable() { public void run() {
            if (sToken != token || sFrom < 0) return;
            boolean composed = sComposed; sFrom = sTo = -1; if (composed) uncompose(ims); set(ims, from, to); doneReporting(ims);
        }}, 900);
        return true;
    }

    private static void doneReporting(InputMethodService ims) {
        sReporting = false;
        if (sWatchedBefore) return;
        try { InputConnection ic = ims.getCurrentInputConnection(); if (ic != null) ic.requestCursorUpdates(0); } catch (Throwable ignored) { }
    }

    private static void uncompose(InputMethodService ims) {
        try { InputConnection ic = ims.getCurrentInputConnection(); if (ic != null) ic.finishComposingText(); } catch (Throwable ignored) { }
    }

    private static void set(InputMethodService ims, int from, int to) {
        try { InputConnection ic = ims.getCurrentInputConnection(); if (ic != null) ic.setSelection(from, to); } catch (Throwable ignored) { }
    }

    private static void ensure(InputMethodService ims, int from, int to) {
        try {
            InputConnection ic = ims.getCurrentInputConnection();
            if (ic == null) return;
            android.view.inputmethod.ExtractedText et = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
            if (et != null && et.selectionStart >= 0) {
                int a = et.startOffset + Math.min(et.selectionStart, et.selectionEnd), b = et.startOffset + Math.max(et.selectionStart, et.selectionEnd);
                if (a == from && b == to) return;
                if (et.startOffset == 0 && et.text != null && a == 0 && b >= et.text.length()) return;
            } else {
                CharSequence sel = ic.getSelectedText(0);
                if (sel != null && sel.length() == to - from) return;
            }
            ic.setSelection(from, to);
        } catch (Throwable ignored) { }
    }

    private static void run(final InputMethodService ims, CursorAnchorInfo info, final int from, final int to, final int caret, final boolean all, boolean composed, boolean touched) throws Exception {
        InputConnection ic = ims.getCurrentInputConnection();
        if (ic == null) return;
        RectF area = all ? lines(info) : letters(info, from, to);
        if (area == null && !all) area = word(info, from, to, caret);
        if (composed) uncompose(ims);
        if (area == null) { set(ims, from, to); doneReporting(ims); return; }
        if (!touched) {
            final android.graphics.PointF at = touchPoint(ims, info, area);
            if (at != null) {
                final long t = sToken;
                new Thread(new Runnable() { public void run() {
                    touch(ims, at);
                    MAIN.post(new Runnable() { public void run() {
                        if (sToken != t) return;
                        InputConnection c = ims.getCurrentInputConnection();
                        if (c == null) return;
                        sSecondPass = true;
                        if (!select(ims, c, from, to, caret, all)) { sSecondPass = false; set(ims, from, to); }
                    }});
                }}, "pixelsuite-select-touch").start();
                return;
            }
        }
        Class<?> hg = Class.forName("android.view.inputmethod.HandwritingGesture");
        Class<?> b = Class.forName("android.view.inputmethod.SelectGesture$Builder");
        Object builder = b.getConstructor().newInstance();
        b.getMethod("setSelectionArea", RectF.class).invoke(builder, area);
        b.getMethod("setGranularity", int.class).invoke(builder, hg.getField(all ? "GRANULARITY_CHARACTER" : "GRANULARITY_WORD").getInt(null));
        gesture(ims, b.getMethod("build").invoke(builder), from, to, all, sToken, 0);
    }

    private static void gesture(final InputMethodService ims, final Object gesture, final int from, final int to, final boolean all, final long token, final int attempt) {
        InputConnection ic = ims.getCurrentInputConnection();
        if (ic == null || sToken != token) { doneReporting(ims); return; }
        java.util.function.IntConsumer done = new java.util.function.IntConsumer() { public void accept(int result) {
            if (sToken != token) return;
            if (result == 1 ) {
                doneReporting(ims);
                if (all) MAIN.postDelayed(new Runnable() { public void run() { if (sToken == token) ensure(ims, from, to); } }, 250);
                return;
            }
            if (attempt == 0) { MAIN.postDelayed(new Runnable() { public void run() { gesture(ims, gesture, from, to, all, token, 1); } }, 120); return; }
            set(ims, from, to); doneReporting(ims);
        } };
        try {
            Class<?> hg = Class.forName("android.view.inputmethod.HandwritingGesture");
            InputConnection.class.getMethod("performHandwritingGesture", hg, java.util.concurrent.Executor.class, java.util.function.IntConsumer.class)
                    .invoke(ic, gesture, (java.util.concurrent.Executor) new java.util.concurrent.Executor() { public void execute(Runnable r) { MAIN.post(r); } }, done);
        } catch (Throwable t) { set(ims, from, to); doneReporting(ims); }
    }

    private static android.graphics.PointF touchPoint(InputMethodService ims, CursorAnchorInfo info, RectF area) {
        float x = area.centerX(), y = area.centerY();
        if (Float.isNaN(x) || Float.isNaN(y) || x < 0 || y < 0) { return null; }
        try {
            android.util.DisplayMetrics dm = ims.getResources().getDisplayMetrics();
            int big = Math.max(dm.widthPixels, dm.heightPixels);
            if (x > big || y > big) { return null; }
            int top = keyboardTop(ims);
            if (top > 0 && y >= top - 2 * dm.density) { return null; }
        } catch (Throwable ignored) { }
        try {
            Object eb = CursorAnchorInfo.class.getMethod("getEditorBoundsInfo").invoke(info);
            if (eb != null) {
                RectF local = (RectF) eb.getClass().getMethod("getEditorBounds").invoke(eb);
                if (local != null && !local.isEmpty()) {
                    RectF screen = new RectF(local); Matrix m = info.getMatrix(); if (m != null) m.mapRect(screen);
                    RectF wide = new RectF(screen); wide.inset(-12, -12);
                    if (!wide.contains(x, y) && !local.contains(x, y)) { return null; }
                }
            }
        } catch (Throwable ignored) { }
        return new android.graphics.PointF(x, y);
    }

    private static int keyboardTop(InputMethodService ims) {
        try {
            InputMethodService.Insets in = new InputMethodService.Insets();
            ims.onComputeInsets(in);
            android.view.View decor = ims.getWindow().getWindow().getDecorView();
            int[] l = new int[2]; decor.getLocationOnScreen(l);
            int t = in.visibleTopInsets;
            if (t <= 0 || t >= decor.getHeight()) return -1;
            return l[1] + t;
        } catch (Throwable t) { return -1; }
    }

    private static long sWarmedAt;
    static void warm(final InputMethodService ims) {
        long now = SystemClock.uptimeMillis();
        if (ims == null || android.os.Build.VERSION.SDK_INT < 34 || now - sWarmedAt < 5000) return;
        sWarmedAt = now;
        new Thread(new Runnable() { public void run() {
            try { ims.getContentResolver().call(android.net.Uri.parse("content://" + GboardPreviewShot.AUTHORITY), "warm", null, null); } catch (Throwable ignored) { }
        }}, "pixelsuite-select-warm").start();
    }

    private static void touch(InputMethodService ims, android.graphics.PointF at) {
        try {
            android.os.Bundle b = new android.os.Bundle(); b.putFloat("x", at.x); b.putFloat("y", at.y);
            ims.getContentResolver().call(android.net.Uri.parse("content://" + GboardPreviewShot.AUTHORITY), "touch", null, b);
        } catch (Throwable t) { }
    }

    private static RectF letters(CursorAnchorInfo info, int from, int to) {
        RectF out = null;
        for (int i = from; i < to; i++) {
            RectF r = info.getCharacterBounds(i);
            if (r == null || r.isEmpty()) continue;
            if (out == null) out = new RectF(r); else out.union(r);
        }
        if (out == null) return null;
        float inx = Math.min(out.width() * 0.25f, out.height() * 0.3f), iny = out.height() * 0.3f;
        out.inset(inx, iny);
        if (out.width() <= 1f) { float cx = out.centerX(); out.left = cx - 1f; out.right = cx + 1f; }
        Matrix m = info.getMatrix();
        if (m != null) m.mapRect(out);
        return out;
    }

    private static RectF word(CursorAnchorInfo info, int from, int to, int caret) {
        float x = info.getInsertionMarkerHorizontal(), t = info.getInsertionMarkerTop(), bt = info.getInsertionMarkerBottom();
        if (Float.isNaN(x) || Float.isNaN(t) || Float.isNaN(bt) || bt <= t) return null;
        float h = bt - t, w = h * 0.45f;
        boolean rtl = (info.getInsertionMarkerFlags() & CursorAnchorInfo.FLAG_IS_RTL) != 0;
        float before = Math.max(0, caret - from) * w, after = Math.max(0, to - caret) * w;
        float left = rtl ? x - after : x - before, right = rtl ? x + before : x + after;
        float mid = (left + right) / 2f, half = Math.max(1f, (right - left) * 0.3f);
        RectF r = new RectF(mid - half, t + h * 0.3f, mid + half, bt - h * 0.3f);
        Matrix m = info.getMatrix();
        if (m != null) m.mapRect(r);
        return r;
    }

    private static RectF lines(CursorAnchorInfo info) {
        RectF out = null;
        try {
            @SuppressWarnings("unchecked")
            java.util.List<RectF> lines = (java.util.List<RectF>) CursorAnchorInfo.class.getMethod("getVisibleLineBounds").invoke(info);
            if (lines != null) for (RectF l : lines) { if (l == null || l.isEmpty()) continue; if (out == null) out = new RectF(l); else out.union(l); }
        } catch (Throwable ignored) { }
        if (out == null) {
            try {
                Object eb = CursorAnchorInfo.class.getMethod("getEditorBoundsInfo").invoke(info);
                if (eb != null) { RectF r = (RectF) eb.getClass().getMethod("getEditorBounds").invoke(eb); if (r != null && !r.isEmpty()) out = new RectF(r); }
            } catch (Throwable ignored) { }
        }
        if (out == null) return null;
        Matrix m = info.getMatrix();
        if (m != null) m.mapRect(out);
        return out;
    }
}
