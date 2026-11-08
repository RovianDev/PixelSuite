package io.github.pixelsuite;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.HashSet;
import java.util.Set;

final class PhotosShareColors {
    private PhotosShareColors() {}

    static void install(ClassLoader cl) {
        try {
            Xp.Callback shown = new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Activity a = (Activity) p.thisObject;
                        if (!isSharePage(a) || !Feature.on(a, ShareSheet.KEY)) return;
                        apply(a);
                        compose(a, true);
                    } catch (Throwable ignored) { }
                }
            };
            Xp.hookAllMethods(Activity.class, "onStart", shown);
            Xp.hookAllMethods(Activity.class, "onPostResume", shown);
            Xp.Callback gone = new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { Activity a = (Activity) p.thisObject; if (isSharePage(a)) compose(a, false); } catch (Throwable ignored) { }
                }
            };
            Xp.hookAllMethods(Activity.class, "onStop", gone);
            Xp.hookAllMethods(Activity.class, "onDestroy", gone);
        } catch (Throwable ignored) { }
    }

    static boolean isSharePage(Activity a) {
        String n = a.getClass().getName();
        return n.endsWith(".share.SharouselActivity") || n.endsWith(".share.BottomSheetShareActivity")
                || n.contains(".share.sharesheet.splitti.");
    }

    private static final java.util.Map<Object, Integer> pages = new java.util.WeakHashMap<>();
    private static final ThreadLocal<Integer> drawing = new ThreadLocal<>();
    private static final java.util.List<Object> handles = new java.util.ArrayList<>();
    private static java.lang.reflect.Field rootView;
    private static int resumed;

    private static synchronized void compose(Activity a, boolean on) {
        View decor = a.getWindow().getDecorView();
        if (on) {
            if (pages.containsKey(decor)) return;
            pages.put(decor, sheetColor(a));
            notes = 0;
            if (resumed++ == 0) hookDraw();
        } else {
            if (pages.remove(decor) == null) return;
            if (--resumed <= 0) { resumed = 0; unhookDraw(); }
        }
    }

    private static void hookDraw() {
        try {
            Class<?> vri = Class.forName("android.view.ViewRootImpl");
            rootView = vri.getDeclaredField("mView"); rootView.setAccessible(true);
            handles.addAll(Xp.hookAllMethods(vri, "performDraw", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { Object v = rootView.get(p.thisObject); Integer t; synchronized (PhotosShareColors.class) { t = pages.get(v); } drawing.set(t); }
                    catch (Throwable ignored) { drawing.set(null); }
                }
                @Override protected void afterHookedMethod(Xp.Param p) { drawing.set(null); }
            }));
            Class<?> c = Class.forName("android.graphics.BaseRecordingCanvas");
            Fill fill = new Fill();
            add(c.getDeclaredMethod("drawRect", float.class, float.class, float.class, float.class, android.graphics.Paint.class), fill);
            add(c.getDeclaredMethod("drawRect", android.graphics.RectF.class, android.graphics.Paint.class), fill);
            add(c.getDeclaredMethod("drawRect", android.graphics.Rect.class, android.graphics.Paint.class), fill);
            add(c.getDeclaredMethod("drawRoundRect", float.class, float.class, float.class, float.class, float.class, float.class, android.graphics.Paint.class), fill);
            add(c.getDeclaredMethod("drawRoundRect", android.graphics.RectF.class, float.class, float.class, android.graphics.Paint.class), fill);
            add(c.getDeclaredMethod("drawPaint", android.graphics.Paint.class), fill);
            Xp.Callback colour = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    Integer target = drawing.get();
                    if (target == null || !(p.args[0] instanceof Integer)) return;
                    int c = (Integer) p.args[0];
                    if ((c >>> 24) >= 0xE0 && pageColour(c)) p.args[0] = (target & 0x00FFFFFF) | (c & 0xFF000000);
                    else note("drawColor", c, -1, -1);
                }
            };
            for (java.lang.reflect.Method m : c.getDeclaredMethods())
                if (m.getName().equals("drawColor") && m.getParameterTypes().length >= 1 && m.getParameterTypes()[0] == int.class) add(m, colour);
        } catch (Throwable ignored) { }
    }

    private static void add(java.lang.reflect.Method m, Xp.Callback cb) { Object h = Xp.hookMethod(m, cb); if (h != null) handles.add(h); }

    private static void unhookDraw() {
        for (Object h : handles) try { if (h instanceof io.github.libxposed.api.XposedInterface.HookHandle) ((io.github.libxposed.api.XposedInterface.HookHandle) h).unhook(); } catch (Throwable ignored) { }
        handles.clear();
    }

    private static final ThreadLocal<Object[]> saved = new ThreadLocal<>();

    private static final class Fill extends Xp.Callback {
        @Override protected void beforeHookedMethod(Xp.Param p) {
            Integer target = drawing.get();
            if (target == null) return;
            try {
                android.graphics.Paint paint = (android.graphics.Paint) p.args[p.args.length - 1];
                if (paint == null || paint.getStyle() != android.graphics.Paint.Style.FILL || paint.getShader() != null) return;
                int color = paint.getColor();
                float w, top;
                Object a0 = p.args[0];
                if (p.args.length == 1) { w = Float.MAX_VALUE; top = -1; }
                else if (a0 instanceof android.graphics.RectF) { w = ((android.graphics.RectF) a0).width(); top = ((android.graphics.RectF) a0).top; }
                else if (a0 instanceof android.graphics.Rect) { w = ((android.graphics.Rect) a0).width(); top = ((android.graphics.Rect) a0).top; }
                else { w = (Float) p.args[2] - (Float) a0; top = (Float) p.args[1]; }
                if (w < 0.9f * android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels) return;
                if ((color >>> 24) < 0xE0 || !pageColour(color)) { return; }
                paint.setColor((target & 0x00FFFFFF) | (color & 0xFF000000));
                saved.set(new Object[]{paint, color});
            } catch (Throwable ignored) { }
        }
        @Override protected void afterHookedMethod(Xp.Param p) {
            Object[] s = saved.get();
            if (s == null) return;
            saved.set(null);
            try { ((android.graphics.Paint) s[0]).setColor((Integer) s[1]); } catch (Throwable ignored) { }
        }
    }

    private static int notes;
    static void note(String how, int color, float w, float top) {
        if (notes >= 24) return;
        notes++;
    }

    static boolean pageColour(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max - min > 24) return false;
        return min >= 0xF2 || max <= 0x26;
    }

    static boolean night(Activity a) {
        if ((a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) return true;
        try {
            int attr = Resources.getSystem().getIdentifier("isLightTheme", "attr", "android");
            android.util.TypedValue v = new android.util.TypedValue();
            if (attr != 0 && a.getTheme().resolveAttribute(attr, v, true) && v.type == android.util.TypedValue.TYPE_INT_BOOLEAN) return v.data == 0;
        } catch (Throwable ignored) { }
        try {
            android.util.TypedValue v = new android.util.TypedValue();
            if (a.getTheme().resolveAttribute(android.R.attr.textColorPrimary, v, true)) {
                int c = v.resourceId != 0 ? a.getColor(v.resourceId) : v.data;
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
                return r * 299 + g * 587 + bl * 114 > 128000;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static int sheetColor(Activity a) {
        boolean night = night(a);
        int id = Resources.getSystem().getIdentifier(night ? "system_surface_container_dark" : "system_surface_container_light", "color", "android");
        if (id != 0) { try { return a.getColor(id); } catch (Throwable ignored) { } }
        return night ? 0xFF211F26 : 0xFFF0EDF4;
    }

    private static final int TAG = 0x7f7ffd31;

    private static void dump(View decor) {
        final int limit = Math.round(Resources.getSystem().getDisplayMetrics().heightPixels * 0.2f);
        final StringBuilder sb = new StringBuilder();
        dumpInto(decor, 0, limit, sb);
    }
    private static void dumpInto(View v, int depth, int limit, StringBuilder sb) {
        if (depth > 12 || sb.length() > 3000) return;
        int[] at = new int[2]; v.getLocationInWindow(at);
        if (at[1] > limit) return;
        Drawable b = v.getBackground();
        sb.append(" | ").append(depth).append(':').append(v.getClass().getSimpleName()).append(' ').append(at[1]).append('+').append(v.getHeight())
                .append(" w").append(v.getWidth()).append(" bg=").append(b == null ? "-" : b instanceof ColorDrawable ? "#" + Integer.toHexString(((ColorDrawable) b).getColor()) : b.getClass().getSimpleName());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) dumpInto(((ViewGroup) v).getChildAt(i), depth + 1, limit, sb);
    }

    static void apply(final Activity a) {
        final int target = sheetColor(a);
        final Set<Integer> whites = new HashSet<>();
        int id = a.getResources().getIdentifier("photos_daynight_white", "color", a.getPackageName());
        if (id != 0) { try { whites.add(a.getColor(id)); } catch (Throwable ignored) { } }
        final View decor = a.getWindow().getDecorView();
        Drawable wb = decor.getBackground();
        if (wb instanceof ColorDrawable) whites.add(((ColorDrawable) wb).getColor());
        boolean night = night(a);
        if (!night) whites.add(0xFFFFFFFF);
        whites.remove(target);
        a.getWindow().setBackgroundDrawable(new ColorDrawable(target));
        try {
            android.view.Window w = a.getWindow();
            if (pageColour(w.getStatusBarColor()) && (w.getStatusBarColor() >>> 24) >= 0xE0) w.setStatusBarColor(target);
            if (pageColour(w.getNavigationBarColor()) && (w.getNavigationBarColor() >>> 24) >= 0xE0) w.setNavigationBarColor(target);
        } catch (Throwable ignored) { }
        paint(decor, whites, target);
        if (decor.getTag(TAG + 1) == null) { decor.setTag(TAG + 1, Boolean.TRUE); decor.postDelayed(() -> dump(decor), 900); }
        if (decor.getTag(TAG) == null) {
            decor.setTag(TAG, Boolean.TRUE);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    try { if (!a.isDestroyed()) paint(decor, whites, target); } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static void paint(View v, Set<Integer> whites, int target) {
        Drawable b = v.getBackground();
        boolean wide = v.getWidth() >= 0.9f * Resources.getSystem().getDisplayMetrics().widthPixels;
        if (b instanceof ColorDrawable) {
            int c = ((ColorDrawable) b).getColor();
            if (whites.contains(c) || (wide && (c >>> 24) >= 0xE0 && pageColour(c))) v.setBackgroundColor(target);
        } else if (b != null && wide) {
            try {
                java.lang.reflect.Method get = b.getClass().getMethod("getFillColor");
                Object csl = get.invoke(b);
                if (csl instanceof android.content.res.ColorStateList) {
                    int c = ((android.content.res.ColorStateList) csl).getDefaultColor();
                    if ((c >>> 24) >= 0xE0 && pageColour(c) && c != target)
                        b.getClass().getMethod("setFillColor", android.content.res.ColorStateList.class).invoke(b, android.content.res.ColorStateList.valueOf(target));
                }
            } catch (Throwable ignored) { }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) paint(g.getChildAt(i), whites, target);
        }
    }
}
