package io.github.pixelsuite;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

final class TextMenuStyle {
    private TextMenuStyle() { }
    private static boolean installed;
    private static final int TAG = 0x7f5e0363;

    static synchronized void install() {
        if (installed) return;
        installed = true;
        Xp.Callback after = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { try { style(p.thisObject); } catch (Throwable ignored) { } }
        };
        for (String name : new String[]{"com.android.internal.widget.floatingtoolbar.LocalFloatingToolbarPopup",
                "com.android.internal.widget.FloatingToolbar$FloatingToolbarPopup",
                "android.service.selectiontoolbar.RemoteSelectionToolbar"}) {
            Class<?> popup; try { popup = Class.forName(name); } catch (Throwable missing) { continue; }
            java.util.Set<String> done = new java.util.HashSet<String>();
            for (java.lang.reflect.Method m : popup.getDeclaredMethods()) {
                String mn = m.getName().toLowerCase(java.util.Locale.ROOT);
                if (!(mn.startsWith("show") || mn.startsWith("update") || mn.startsWith("layout") || mn.startsWith("refresh") || mn.startsWith("setwidth") || mn.startsWith("preparepopup"))) continue;
                if (!done.add(m.getName())) continue;
                try { Xp.hookAllMethods(popup, m.getName(), after); } catch (Throwable ignored) { }
            }
        }
        try {
            Class<?> host = Class.forName("android.view.SurfaceControlViewHost");
            Xp.hookAllMethods(host, "setView", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length == 0 || !(p.args[0] instanceof View) || !fromSelectionToolbar()) return;
                        final View root = (View) p.args[0];
                        if (root.getTag(TAG + 1) == null) {
                            root.setTag(TAG + 1, Boolean.TRUE);
                            root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { styleRemote(root); }
                            });
                        }
                        root.post(new Runnable() { public void run() { styleRemote(root); } });
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable missing) { }
        Xp.Callback shown = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    android.widget.PopupWindow w = (android.widget.PopupWindow) p.thisObject;
                    android.view.View root = w.getContentView();
                    if (root == null) return;
                    android.view.View box = root.findViewWithTag("floating_toolbar");
                    if (!(box instanceof ViewGroup) && ((Integer) android.widget.PopupWindow.class.getMethod("getWindowLayoutType").invoke(w)) == 1005 && fromToolbar()) box = container(root, 0);
                    if (box instanceof ViewGroup) styleBox((ViewGroup) box);
                } catch (Throwable ignored) { }
            }
        };
        try { Xp.hookAllMethods(android.widget.PopupWindow.class, "showAtLocation", shown); } catch (Throwable ignored) { }
        try { Xp.hookAllMethods(android.widget.PopupWindow.class, "update", shown); } catch (Throwable ignored) { }
    }

    private static boolean fromSelectionToolbar() {
        for (StackTraceElement e : new Throwable().getStackTrace()) if (e.getClassName().toLowerCase(java.util.Locale.ROOT).contains("selectiontoolbar")) return true;
        return false;
    }

    private static void styleRemote(View root) {
        try {
            java.util.List<View> parts = new java.util.ArrayList<View>();
            surfaces(root, 0, parts);
            boolean superDark = false;
            try { superDark = SuperDark.inEffect(root.getContext()); } catch (Throwable ignored) { }
            if (!parts.isEmpty()) { if (root instanceof ViewGroup) styleBox((ViewGroup) root); else for (View v : parts) paint(v, superDark); return; }
            boolean darkened = Boolean.TRUE.equals(root.getTag(TAG + 2));
            if (superDark && !darkened) {
                float k = (255f - 10f) / (255f - 43f), o = 10f - 43f * k;
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{k,0,0,0,o, 0,k,0,0,o, 0,0,k,0,o, 0,0,0,1,0}));
                root.setLayerType(View.LAYER_TYPE_HARDWARE, paint); root.setTag(TAG + 2, Boolean.TRUE);
            } else if (!superDark && darkened) { root.setLayerType(View.LAYER_TYPE_NONE, null); root.setTag(TAG + 2, null); }
        } catch (Throwable t) {
            if (!sFailed) { sFailed = true; android.util.Log.w("PixelSuiteTextMenu", "couldn't style SystemUI's menu", t); }
        }
    }

    private static boolean fromToolbar() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName().toLowerCase(java.util.Locale.ROOT);
            if (c.contains("floatingtoolbar") || c.contains("selectiontoolbar") || c.contains("floatingactionmode") || c.contains("texttoolbar")) return true;
        }
        return false;
    }

    private static ViewGroup container(View v, int depth) {
        if (v instanceof ViewGroup && v.getBackground() != null) return (ViewGroup) v;
        if (!(v instanceof ViewGroup) || depth > 3) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { ViewGroup r = container(g.getChildAt(i), depth + 1); if (r != null) return r; }
        return depth == 0 && v instanceof ViewGroup ? (ViewGroup) v : null;
    }

    private static boolean sDumped;
    private static void style(Object popup) throws Exception {
        ViewGroup box = null, any = null; StringBuilder names = new StringBuilder();
        for (Class<?> k = popup.getClass(); k != null && k != Object.class && box == null; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true); Object v = f.get(popup);
                if (!sDumped) names.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(v == null ? "=null " : " ");
                if (!(v instanceof ViewGroup)) continue;
                ViewGroup g = (ViewGroup) v;
                if ("floating_toolbar".equals(g.getTag()) || g.findViewWithTag("floating_toolbar") == g) { box = g; break; }
                if (any == null && g.getRootView() != g && !g.getClass().getName().contains("DecorView")) any = g;
            }
        }
        if (box == null) box = any;
        if (box == null) { if (!sDumped) { sDumped = true; android.util.Log.w("PixelSuiteTextMenu", "no menu container in " + popup.getClass().getName() + ": " + names); } return; }
        styleBox(box);
    }

    private static void styleBox(final ViewGroup box) {
        Context c = box.getContext();
        boolean superDark = false, glass = false;
        try { superDark = SuperDark.inEffect(c); } catch (Throwable ignored) { }
        try { glass = !superDark && GlassRim.wanted(c); } catch (Throwable ignored) { }
        String step = "find";
        try {
            java.util.List<View> parts = new java.util.ArrayList<View>();
            surfaces(box, 0, parts);
            if (!parts.contains(box)) unstyle(box);
            if (parts.isEmpty()) {
                return;
            }
            step = "tint";
            for (View v : parts) paint(v, superDark);
            step = "rim";
            boolean want = superDark || glass;
            for (View v : parts) rim(v, c, want, superDark);
        } catch (Throwable t) {
            if (!sFailed) { sFailed = true; android.util.Log.w("PixelSuiteTextMenu", "couldn't style the menu at " + step, t); }
        }
    }
    private static boolean sFailed;

    private static void surfaces(View v, int depth, java.util.List<View> out) {
        if (depth > 5) return;
        boolean panel = v instanceof ViewGroup || !(v instanceof android.widget.TextView || v instanceof android.widget.ImageView);
        if (panel && solid(v.getBackground())) out.add(v);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) surfaces(g.getChildAt(i), depth + 1, out); }
    }

    private static boolean solid(Drawable d) {
        if (d == null) return false;
        if (d instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable l = (android.graphics.drawable.LayerDrawable) d;
            for (int i = 0; i < l.getNumberOfLayers(); i++) { int id = View.NO_ID; try { id = l.getId(i); } catch (Throwable ignored) { } if (id != android.R.id.mask && solid(l.getDrawable(i))) return true; }
            return false;
        }
        if (d instanceof android.graphics.drawable.InsetDrawable) return solid(((android.graphics.drawable.InsetDrawable) d).getDrawable());
        if (d instanceof android.graphics.drawable.DrawableContainer) { Drawable cur = d.getCurrent(); return cur != null && cur != d && solid(cur); }
        if (d instanceof android.graphics.drawable.ColorDrawable) return (((android.graphics.drawable.ColorDrawable) d).getColor() >>> 24) != 0;
        if (d.getAlpha() == 0) return false;
        if (d instanceof GradientDrawable) {
            try { Object cs = GradientDrawable.class.getMethod("getColor").invoke(d); if (cs instanceof android.content.res.ColorStateList) return (((android.content.res.ColorStateList) cs).getDefaultColor() >>> 24) != 0; } catch (Throwable ignored) { }
            return true;
        }
        return true;
    }

    private static Holder rim(final View v, Context c, boolean want, boolean superDark) {
        final Holder h = holder(v);
        if (!want) { if (h.rim != null) { v.getOverlay().remove(h.rim); h.rim = null; } return h; }
        float radius = radius(v);
        if (h.rim == null || h.superDark != superDark || Math.abs(h.radius - radius) > 0.5f) {
            if (h.rim != null) v.getOverlay().remove(h.rim);
            h.radius = radius; h.superDark = superDark; h.rim = GlassRim.of(c, radius);
            v.getOverlay().add(h.rim);
        }
        h.rim.setBounds(0, 0, v.getWidth(), v.getHeight());
        return h;
    }

    private static void unstyle(View v) {
        Object t = v.getTag(TAG);
        if (t instanceof Holder && ((Holder) t).rim != null) { v.getOverlay().remove(((Holder) t).rim); ((Holder) t).rim = null; }
        paint(v, false);
    }

    private static void paint(View v, boolean superDark) {
        if (superDark) v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SuperDarkPalette.MENU));
        else if (v.getBackgroundTintList() != null && v.getBackgroundTintList().getDefaultColor() == SuperDarkPalette.MENU) v.setBackgroundTintList(null);
    }

    private static float radius(View box) {
        try {
            android.graphics.Outline o = new android.graphics.Outline();
            if (box.getOutlineProvider() != null) { box.getOutlineProvider().getOutline(box, o); float r = (Float) android.graphics.Outline.class.getMethod("getRadius").invoke(o); if (r > 0) return r; }
        } catch (Throwable ignored) { }
        Drawable bg = box.getBackground();
        Drawable base = bg instanceof android.graphics.drawable.InsetDrawable ? ((android.graphics.drawable.InsetDrawable) bg).getDrawable() : bg;
        if (base instanceof GradientDrawable) { try { float r = (Float) GradientDrawable.class.getMethod("getCornerRadius").invoke(base); if (r > 0) return r; } catch (Throwable ignored) { } }
        float d = box.getResources().getDisplayMetrics().density;
        return Math.min(28 * d, box.getHeight() > 0 ? box.getHeight() / 2f : 28 * d);
    }

    private static final class Holder { GlassRim rim; boolean superDark; float radius; }

    private static Holder holder(final View part) {
        Object t = part.getTag(TAG);
        if (t instanceof Holder) return (Holder) t;
        final Holder h = new Holder();
        part.setTag(TAG, h);
        part.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t2, int r, int b, int ol, int ot, int or, int ob) {
                if (h.rim == null) return;
                float radius = radius(part);
                if (Math.abs(radius - h.radius) > 0.5f) {
                    part.getOverlay().remove(h.rim); h.radius = radius; h.rim = GlassRim.of(part.getContext(), radius); part.getOverlay().add(h.rim);
                }
                h.rim.setBounds(0, 0, r - l, b - t2);
            }
        });
        return h;
    }
}
