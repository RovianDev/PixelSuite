package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class CropGrab {
    private CropGrab() {}

    private static boolean active, started;
    private static float downX, downY, lastX, lastY;
    private static long downAt;
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    static boolean wouldTake(View c, float rawX, float rawY) {
        try {
            if (!CropKeep.keeping() || "CROP_TOOL".equals(TopBarKeep.currentPage())) return false;
            RectF cut = CropKeep.lastCut;
            if (cut == null) { return false; }
            float reach = 48f * c.getResources().getDisplayMetrics().density;
            RectF sc = onScreen(c, cut);
            float[] pts = {sc.left, sc.top, sc.right, sc.top, sc.left, sc.bottom, sc.right, sc.bottom};
            for (int i = 0; i < 8; i += 2)
                if (Math.abs(rawX - pts[i]) <= reach && Math.abs(rawY - pts[i + 1]) <= reach) return true;
            return false;
        } catch (Throwable t) { return false; }
    }

    static boolean busy() { return active; }

    static boolean touch(Activity a, View c, MotionEvent e) {
        int am = e.getActionMasked();
        if (am == MotionEvent.ACTION_DOWN) {
            active = false; started = false;
            String page = TopBarKeep.currentPage();
            if (!CropKeep.keeping() || "CROP_TOOL".equals(page)) return false;
            RectF cut = CropKeep.lastCut;
            if (cut == null || !(onMark(c, cut, e.getRawX(), e.getRawY()) || (TextTool.textSelected() && wouldTake(c, e.getRawX(), e.getRawY())))) return false;
            if (TextTool.onSelectedText(e.getRawX(), e.getRawY())) return false;
            active = true;
            downX = lastX = e.getX(); downY = lastY = e.getY(); downAt = e.getDownTime();
            toolLabel = "CAPTION".equals(page) ? toolNamed(c, "caption", "text") : selectedTool(c);
            if ("CAPTION".equals(page)) { TextTool.stopEditing(true); TextTool.cropDragFromText(); }
            CropRemember.pressCrop();
            waitForCrop(a, 0);
            return true;
        }
        if (!active) return false;
        lastX = e.getX(); lastY = e.getY();
        if (started) {
            if (am == MotionEvent.ACTION_MOVE || am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL)
                inject(a, am == MotionEvent.ACTION_MOVE ? MotionEvent.ACTION_MOVE : am, lastX, lastY);
        }
        if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) { active = false; if (started) backToTool(a); }
        return true;
    }

    private static String toolLabel;

    private static String toolNamed(View c, String... words) {
        try {
            for (ComposeTree.Node n : ComposeTree.clickables(ComposeTree.composeView(c))) {
                String l = n.label == null ? "" : n.label.toLowerCase(java.util.Locale.ROOT);
                for (String w : words) if (l.contains(w)) return n.label;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static String selectedTool(View c) {
        try {
            for (ComposeTree.Node n : ComposeTree.clickables(ComposeTree.composeView(c)))
                if (n.selected && n.label != null && n.label.length() > 0 && !n.label.toLowerCase(java.util.Locale.ROOT).contains("crop")) return n.label;
            for (ComposeTree.Node n : ComposeTree.clickables(ComposeTree.composeView(c)))
                if (n.label != null && n.label.toLowerCase(java.util.Locale.ROOT).contains("stroke")) return n.label;
        } catch (Throwable ignored) { }
        return null;
    }

    private static void backToTool(final Activity a) {
        final String label = toolLabel;
        toolLabel = null;
        if (label == null) { return; }
        main.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if (!"CROP_TOOL".equals(TopBarKeep.currentPage())) return;
                    View compose = ComposeTree.composeView(ScreenshotEditor.canvasView.get());
                    for (ComposeTree.Node n : ComposeTree.clickables(compose))
                        if (label.equals(n.label)) { ComposeTree.press(a, compose, n); return; }
                } catch (Throwable ignored) { }
            }
        }, 120);
    }

    private static boolean onMark(View c, RectF cut, float rawX, float rawY) {
        float reach = 30f * c.getResources().getDisplayMetrics().density;
        RectF s = onScreen(c, cut);
        float[] pts = {s.left, s.top, s.right, s.top, s.left, s.bottom, s.right, s.bottom};
        for (int i = 0; i < 8; i += 2)
            if (Math.abs(rawX - pts[i]) <= reach && Math.abs(rawY - pts[i + 1]) <= reach) return true;
        return false;
    }

    static RectF onScreen(View c, RectF r) {
        int[] at = new int[2]; c.getLocationOnScreen(at);
        float lx = at[0] - c.getTranslationX(), ly = at[1] - c.getTranslationY();
        RectF s = new RectF(r);
        Matrix m = c.getMatrix();
        m.mapRect(s);
        s.offset(lx, ly);
        return s;
    }

    private static void waitForCrop(final Activity a, final int attempt) {
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (!active && !started) return;
                if ("CROP_TOOL".equals(TopBarKeep.currentPage()) && attempt >= 2) {
                    started = true;
                    inject(a, MotionEvent.ACTION_DOWN, downX, downY);
                    if (lastX != downX || lastY != downY) inject(a, MotionEvent.ACTION_MOVE, lastX, lastY);
                    if (!active) { inject(a, MotionEvent.ACTION_UP, lastX, lastY); backToTool(a); }
                    return;
                }
                if (attempt < 60) waitForCrop(a, attempt + 1);
            }
        }, 16);
    }

    private static long injectedDown;
    private static void inject(Activity a, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) injectedDown = now;
        MotionEvent ev = MotionEvent.obtain(injectedDown, now, action, x, y, 0);
        MotionEvent one = ev;
        try { one = StudioZoom.injectedCrop(a, ev); } catch (Throwable ignored) { }
        StudioItems.injecting = true;
        try { a.getWindow().superDispatchTouchEvent(one); } catch (Throwable ignored) { }
        finally { StudioItems.injecting = false; ev.recycle(); if (one != ev) one.recycle(); }
    }
}
