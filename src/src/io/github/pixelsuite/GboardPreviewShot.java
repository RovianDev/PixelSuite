package io.github.pixelsuite;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

final class GboardPreviewShot {
    private GboardPreviewShot() {}

    static final String AUTHORITY = "io.github.roviandev.pixelsuite.keyboard";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<InputMethodService> sIms = new WeakReference<InputMethodService>(null);
    private static WeakReference<View> sWatched = new WeakReference<View>(null);
    private static long sLast;
    private static int sLastSig;

    private static final Runnable SHOOT = new Runnable() { public void run() {
        InputMethodService ims = sIms.get();
        if (ims != null) try { shoot(ims); } catch (Throwable ignored) { }
    }};

    static void schedule(InputMethodService ims, long delay) {
        if (ims == null) return;
        sIms = new WeakReference<InputMethodService>(ims);
        MAIN.removeCallbacks(SHOOT);
        MAIN.postDelayed(SHOOT, delay);
    }

    private static void watch(final View holder) {
        if (sWatched.get() == holder) return;
        sWatched = new WeakReference<View>(holder);
        holder.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                if (sWatched.get() != holder) { try { holder.getViewTreeObserver().removeOnGlobalLayoutListener(this); } catch (Throwable ignored) { } return; }
                if (!holder.isShown()) return;
                InputMethodService ims = sIms.get();
                if (ims != null) schedule(ims, 900);
            }
        });
    }

    private static void shoot(final InputMethodService ims) {
        if (GboardPlus.previewBusy()) return;
        if (ims.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sLast < 1500) { schedule(ims, 1500 - (now - sLast)); return; }
        android.app.Dialog w = ims.getWindow();
        View decor = w == null || w.getWindow() == null ? null : w.getWindow().getDecorView();
        View holder = find(decor, "keyboard_holder");
        if (holder == null || !holder.isShown() || holder.getWidth() <= 0 || holder.getHeight() <= 0) return;
        watch(holder);
        if (unsafe(decor)) return;
        View body = byTag(holder, "keyboard-body-area");
        if (body == null || !body.isShown() || body.getHeight() <= 0) return;
        View header = find(holder, "keyboard_header_view_holder");
        int[] hp = new int[2], pp = new int[2], hd = new int[2], bd = new int[2];
        holder.getLocationInWindow(hp);
        body.getLocationInWindow(bd);
        int headTop = 0, headH = 0;
        boolean headClean = false;
        if (header != null && header.isShown() && header.getHeight() > 0) {
            header.getLocationInWindow(hd);
            headTop = hd[1] - hp[1]; headH = header.getHeight();
            headClean = clean(header);
        }
        int bodyTop = bd[1] - hp[1], bodyH = Math.min(body.getHeight(), holder.getHeight() - Math.max(0, bodyTop));
        if (bodyTop < 0 || bodyH <= 0) return;
        View panel = holder;
        for (View v = holder; v != null && v != decor; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            Object t = v.getTag();
            if (t instanceof CharSequence && t.toString().contains("keyboard-base-area")) { panel = v; break; }
        }
        panel.getLocationInWindow(pp);
        int W = holder.getWidth(), H = holder.getHeight();
        final List<View> bodyLabels = new ArrayList<View>(), headLabels = new ArrayList<View>();
        labels(body, false, bodyLabels);
        if (headClean && header != null) labels(header, false, headLabels);
        List<View> all = new ArrayList<View>(bodyLabels); all.addAll(headLabels);
        float[] alphas = new float[all.size()];
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        if (headClean) c.clipRect(0, Math.max(0, headTop), W, Math.min(H, bodyTop + bodyH));
        else c.clipRect(0, bodyTop, W, Math.min(H, bodyTop + bodyH));
        c.translate(pp[0] - hp[0], pp[1] - hp[1]);
        for (int i = 0; i < all.size(); i++) { alphas[i] = all.get(i).getAlpha(); all.get(i).setAlpha(0f); }
        try { panel.draw(c); } finally { for (int i = 0; i < all.size(); i++) all.get(i).setAlpha(alphas[i]); }
        sLast = now;
        final Bitmap head = headClean && headH > 0 && headTop >= 0 && headTop + headH <= H ? Bitmap.createBitmap(bm, 0, headTop, W, headH) : null;
        final Bitmap keys = Bitmap.createBitmap(bm, 0, bodyTop, W, bodyH);
        bm.recycle();
        final int[] bodyMeta = new int[bodyLabels.size() * 6], headMeta = new int[headLabels.size() * 6];
        final Bitmap bodyAtlas = atlas(holder, body, bodyLabels, bodyMeta);
        final Bitmap headAtlas = head != null && header != null ? atlas(holder, header, headLabels, headMeta) : null;
        int sig = signature(keys) * 31 + (head == null ? 0 : signature(head));
        if (bodyAtlas != null) sig = sig * 31 + signature(bodyAtlas);
        if (sig == sLastSig) { keys.recycle(); if (head != null) head.recycle(); if (bodyAtlas != null) bodyAtlas.recycle(); if (headAtlas != null) headAtlas.recycle(); return; }
        sLastSig = sig;
        final int headHeight = headH;
        float gh = 1f;
        try { gh = GboardPlus.geo().h; } catch (Throwable ignored) { }
        final float geoH = gh;
        final android.content.Context ctx = ims.getApplicationContext();
        final String theme = SuperDarkApps.activeAnywhere() ? "s" : (ims.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES ? "d" : "l";
        new Thread(new Runnable() { public void run() {
            try {
                Bundle b = new Bundle();
                b.putString("theme", theme);
                b.putByteArray("body", encode(keys));
                if (bodyAtlas != null) { b.putByteArray("bodyAtlas", encode(bodyAtlas)); b.putIntArray("bodyLabels", bodyMeta); }
                if (head != null) {
                    b.putByteArray("header", encode(head));
                    if (headAtlas != null) { b.putByteArray("headerAtlas", encode(headAtlas)); b.putIntArray("headerLabels", headMeta); }
                }
                b.putInt("width", keys.getWidth());
                b.putInt("headH", headHeight);
                b.putFloat("geoH", geoH);
                ctx.getContentResolver().call(Uri.parse("content://" + AUTHORITY), "put", null, b);
            } catch (Throwable ignored) {
            } finally { keys.recycle(); if (head != null) head.recycle(); if (bodyAtlas != null) bodyAtlas.recycle(); if (headAtlas != null) headAtlas.recycle(); }
        }}, "PixelSuiteKeyboardPreview").start();
    }

    private static void labels(View v, boolean inKey, List<View> out) {
        if (!v.isShown() || v.getWidth() <= 0 || v.getHeight() <= 0 || v.getAlpha() < 0.05f) return;
        boolean key = inKey || v.getClass().getName().endsWith("SoftKeyView");
        if (key && (v instanceof TextView || (v instanceof ImageView && ((ImageView) v).getDrawable() != null))) { out.add(v); return; }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) labels(g.getChildAt(i), key, out); }
    }

    private static Bitmap atlas(View holder, View part, List<View> labels, int[] meta) {
        if (labels.isEmpty()) return null;
        int[] pl = new int[2], vl = new int[2], hl = new int[2];
        part.getLocationInWindow(pl);
        holder.getLocationInWindow(hl);
        pl[0] = hl[0];
        int max = 1024, x = 0, y = 0, row = 0;
        for (int i = 0; i < labels.size(); i++) {
            View v = labels.get(i);
            int w = Math.min(max, v.getWidth()), h = v.getHeight();
            if (x + w > max) { x = 0; y += row; row = 0; }
            v.getLocationInWindow(vl);
            meta[i * 6] = vl[0] - pl[0]; meta[i * 6 + 1] = vl[1] - pl[1]; meta[i * 6 + 2] = w; meta[i * 6 + 3] = h;
            meta[i * 6 + 4] = x; meta[i * 6 + 5] = y;
            x += w; row = Math.max(row, h);
        }
        int H = y + row;
        if (H <= 0 || H > 4096) return null;
        Bitmap b = Bitmap.createBitmap(max, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        for (int i = 0; i < labels.size(); i++) {
            int s = c.save();
            c.clipRect(meta[i * 6 + 4], meta[i * 6 + 5], meta[i * 6 + 4] + meta[i * 6 + 2], meta[i * 6 + 5] + meta[i * 6 + 3]);
            c.translate(meta[i * 6 + 4], meta[i * 6 + 5]);
            try { labels.get(i).draw(c); } catch (Throwable ignored) { }
            c.restoreToCount(s);
        }
        return b;
    }

    private static byte[] encode(Bitmap b) {
        Bitmap.CompressFormat f;
        try { f = Bitmap.CompressFormat.valueOf("WEBP_LOSSY"); } catch (Throwable t) { f = Bitmap.CompressFormat.WEBP; }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(f, 88, out);
        if (out.size() > 450 * 1024) { out.reset(); b.compress(f, 60, out); }
        return out.toByteArray();
    }

    private static int signature(Bitmap b) {
        int h = b.getWidth() * 7 + b.getHeight();
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) h = h * 31 + b.getPixel(x * (b.getWidth() - 1) / 23, y * (b.getHeight() - 1) / 23);
        return h;
    }

    private static boolean unsafe(View v) {
        if (v == null || !v.isShown()) return false;
        String n = v.getClass().getName();
        if (n.contains("Clipboard") || n.contains("Emoji") || n.contains("Gif") || n.contains(".search.") || n.contains("RecyclerView")
                || n.contains("InlineContentView") || v instanceof android.widget.EditText || v instanceof android.view.SurfaceView) return true;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) if (unsafe(g.getChildAt(i))) return true; }
        return false;
    }

    private static boolean clean(View v) {
        if (!v.isShown()) return true;
        String n = v.getClass().getName();
        if (n.contains("InlineContentView") || n.contains("Proactive") || v instanceof android.view.SurfaceView) return false;
        if (v instanceof TextView) { CharSequence t = ((TextView) v).getText(); if (t != null && t.length() > 0) return false; }
        if (v instanceof ImageView && v.getWidth() > 0) {
            Drawable d = ((ImageView) v).getDrawable();
            if (d instanceof BitmapDrawable && (v.getWidth() > v.getHeight() * 1.6f || inCandidates(v))) return false;
        }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) if (!clean(g.getChildAt(i))) return false; }
        return true;
    }

    private static boolean inCandidates(View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) if (p.getClass().getName().contains("Candidate")) return true;
        return false;
    }

    private static View find(View v, String id) {
        if (v == null) return null;
        if (v.getId() != View.NO_ID && v.getId() != 0) { try { if (id.equals(v.getResources().getResourceEntryName(v.getId()))) return v; } catch (Throwable ignored) { } }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), id); if (f != null) return f; } }
        return null;
    }

    private static View byTag(View v, String tag) {
        if (v == null) return null;
        Object t = v.getTag();
        if (t instanceof CharSequence && t.toString().contains(tag) && v.isShown()) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = byTag(g.getChildAt(i), tag); if (f != null) return f; } }
        return null;
    }
}
