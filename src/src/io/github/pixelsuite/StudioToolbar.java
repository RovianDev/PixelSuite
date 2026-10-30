package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.List;

final class StudioToolbar {
    private StudioToolbar() {}

    private static final ThreadLocal<Boolean> IN_TOOLBAR = new ThreadLocal<Boolean>();
    private static volatile Object sController;
    private static volatile List<?> sColours;
    private static volatile long sColour;
    private static final java.util.Map<Object, Object> toggles = new java.util.WeakHashMap<Object, Object>();
    private static java.lang.ref.WeakReference<View> sOpen = new java.lang.ref.WeakReference<View>(null);
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    static void install(ClassLoader cl) {
        try {
            Class<?> ui = StudioNames.cls("ui", cl), fn1 = StudioNames.cls("fn1", cl), composer = StudioNames.cls("composer", cl);
            Method bottom = null, palette = null;
            for (Method m : ui.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (t.length == 29 && allBoolean(t, 11)) bottom = m;
                else if (t.length == 8 && t[0] == boolean.class && t[1] == List.class && t[2] == long.class && t[3] == boolean.class
                        && t[4] == fn1 && t[5] == fn1 && t[6] == composer && t[7] == int.class) palette = m;
            }
            if (bottom == null || palette == null) return;
            Xp.hookMethod(bottom, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    p.args[4] = Boolean.TRUE;
                    if (p.args[22] != null) { sController = p.args[22]; watchController(p.args[22]); }
                }
            });
            try {
                String row = StudioNames.get("toolbarLambda");
                Class<?> state = StudioNames.cls("state", cl);
                if (row != null) {
                    for (Method m : Class.forName(row, false, cl).getDeclaredMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 2) continue;
                        Xp.hookMethod(m, new Xp.Callback() {
                            @Override protected void beforeHookedMethod(Xp.Param p) { IN_TOOLBAR.set(Boolean.TRUE); }
                            @Override protected void afterHookedMethod(Xp.Param p) { IN_TOOLBAR.set(null); }
                        });
                    }
                    for (Method m : Class.forName("a", false, cl).getDeclaredMethods()) {
                        Class<?>[] t = m.getParameterTypes();
                        if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class || t.length != 1 || !t[0].isAssignableFrom(state)) continue;
                        if (!"J".equals(m.getName())) continue;
                        Xp.hookMethod(m, new Xp.Callback() {
                            @Override protected void beforeHookedMethod(Xp.Param p) {
                                if (Boolean.TRUE.equals(IN_TOOLBAR.get())) { IN_TOOLBAR.set(Boolean.FALSE); p.setResult(Boolean.FALSE); }
                            }
                        });
                    }
                }
            } catch (Throwable ignored) { }
            final Class<?> fn1Type = fn1;
            Xp.hookMethod(palette, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        sColours = (List<?>) p.args[1];
                        sColour = (Long) p.args[2];
                        p.args[0] = Boolean.FALSE;
                        p.args[5] = toggle(fn1Type, p.args[5]);
                        View open = sOpen.get();
                        if (open != null) main.post(StudioToolbar::refresh);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static volatile Class<?> sWatched;
    private static void watchController(Object controller) {
        Class<?> k = controller.getClass();
        if (sWatched == k) return;
        sWatched = k;
        try {
            java.util.Set<String> picks = new java.util.HashSet<String>();
            for (Class<?> i : k.getInterfaces()) for (Method m : i.getMethods())
                if (m.getParameterTypes().length == 0 && m.getReturnType() == void.class) picks.add(m.getName());
            for (Method m : k.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0 || m.getReturnType() != void.class || !picks.contains(m.getName())) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (ScreenshotEditor.lastDownAt <= ScreenshotEditor.openedAt) return;
                        if (now - ScreenshotEditor.lastDownAt > 800) return;
                        if (!lowerQuarter(ScreenshotEditor.lastDownRawY)) return;
                        otherMenu();
                    }
                });
            }
        } catch (Throwable ignored) { }
    }

    private static boolean lowerQuarter(float rawY) {
        try { return rawY >= android.content.res.Resources.getSystem().getDisplayMetrics().heightPixels * 0.75f; } catch (Throwable t) { return true; }
    }

    private static boolean nearToolbar(float rawY) {
        try {
            for (ComposeTree.Node n : ComposeTree.clickables(ComposeTree.composeView(ScreenshotEditor.canvasView.get()))) {
                String l = n.label == null ? "" : n.label.toLowerCase(java.util.Locale.ROOT);
                if ((l.contains("stroke") || l.contains("eraser")) && Math.abs(n.bounds.centerY() - rawY) <= n.bounds.height()) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static void otherMenu() {
        main.post(new Runnable() { @Override public void run() {
            try { if (DoodleOverlay.active()) DoodleOverlay.leave(false); } catch (Throwable ignored) { }
            close();
        } });
    }

    private static boolean allBoolean(Class<?>[] t, int n) {
        for (int i = 0; i < n; i++) if (t[i] != boolean.class) return false;
        return true;
    }

    private static Object toggle(Class<?> fn1, final Object original) {
        if (original == null || Proxy.isProxyClass(original.getClass())) return original;
        synchronized (toggles) {
            Object w = toggles.get(original);
            if (w != null) return w;
            final Object unit = unit();
            w = Proxy.newProxyInstance(fn1.getClassLoader(), new Class<?>[]{fn1}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                    if (m.getDeclaringClass() == Object.class) {
                        if (m.getName().equals("equals")) return proxy == args[0];
                        if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        return original.toString();
                    }
                    main.post(new Runnable() { @Override public void run() { if (sOpen.get() == null) show(); else close(); } });
                    return unit;
                }
            });
            toggles.put(original, w);
            return w;
        }
    }

    private static Object unit() { try { return ScreenshotEditor.unit(ScreenshotEditor.loader); } catch (Throwable t) { return null; } }

    private static boolean dark, superDark, frosted;
    private static int surface, ring;

    private static void colours(Activity a) {
        dark = DoodleOverlay.dark(a);
        superDark = dark && SuperDark.inEffect(a);
        surface = DoodleOverlay.system(a, dark ? "system_surface_container_high_dark" : "system_surface_container_high_light", dark ? 0xff2b2930 : 0xffece6f0);
        if (superDark) surface = 0xff000000;
        else if (dark) surface = ComposeFrost.toolbarColour != 0 ? ComposeFrost.toolbarColour : 0xff1f1f21;
        frosted = !dark && SurfaceStyle.get(a) == SurfaceStyle.FROSTED && FrostedSurface.blurEnabled(a);
        ring = DoodleOverlay.system(a, dark ? "system_on_surface_dark" : "system_on_surface_light", dark ? 0xffe6e0e9 : 0xff1d1b20);
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static void close() {
        final View open = sOpen.get();
        sOpen = new java.lang.ref.WeakReference<View>(null);
        if (open == null) return;
        View panel = ((ViewGroup) open).getChildCount() > 0 ? ((ViewGroup) open).getChildAt(0) : open;
        panel.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(140)
                .withEndAction(new Runnable() { @Override public void run() { ViewGroup p = (ViewGroup) open.getParent(); if (p != null) p.removeView(open); } }).start();
    }

    private static void refresh() {
        View open = sOpen.get();
        if (open != null) invalidateAll(open);
    }
    private static void invalidateAll(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateAll(((ViewGroup) v).getChildAt(i));
    }

    static FrameLayout shell(Activity a, float radiusDp) {
        colours(a);
        FrameLayout shell = new FrameLayout(a) { @Override public boolean hasOverlappingRendering() { return false; } };
        float radius = dp(a, radiusDp);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadius(radius);
        shell.setBackground(frosted ? HoleFrost.panel(a, radius) : bg);
        if (superDark && GlassRim.superDarkRims(a)) shell.setForeground(new DoodleOverlay.Rim(radius, dp(a, 1.5f)));
        else if (GlassRim.wanted(a)) shell.setForeground(GlassRim.of(a, radius));
        shell.setElevation(dp(a, 6));
        shell.setClipToOutline(true);
        shell.setClickable(true);
        return shell;
    }
    static int ringColour() { return ring; }
    static boolean darkMenu() { return dark; }

    private static void show() {
        try {
            final Activity a = ScreenshotEditor.activity();
            List<?> list = sColours;
            if (a == null || list == null || list.isEmpty()) return;
            colours(a);
            if (DoodleOverlay.active()) DoodleOverlay.leave(false);
            ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            final FrameLayout layer = new FrameLayout(a) { @Override public boolean hasOverlappingRendering() { return false; } };
            layer.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent e) { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) close(); return true; }
            });
            FrameLayout shell = new FrameLayout(a) { @Override public boolean hasOverlappingRendering() { return false; } };
            float radius = dp(a, 26);
            GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadius(radius);
            shell.setBackground(frosted ? HoleFrost.panel(a, radius) : bg);
            if (superDark && GlassRim.superDarkRims(a)) shell.setForeground(new DoodleOverlay.Rim(radius, dp(a, 1.5f)));
            else if (GlassRim.wanted(a)) shell.setForeground(GlassRim.of(a, radius));
            shell.setElevation(dp(a, 6));
            shell.setClipToOutline(true);
            shell.setClickable(true);
            LinearLayout rows = new LinearLayout(a);
            rows.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(a, 14), size = dp(a, 30), gap = dp(a, 12), perRow = 6;
            rows.setPadding(pad, pad, pad, pad);
            LinearLayout row = null;
            for (int i = 0; i < list.size(); i++) {
                final long colour = colourOf(list.get(i));
                if (i % perRow == 0) {
                    row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
                    if (i > 0) rp.topMargin = gap;
                    rows.addView(row, rp);
                }
                Swatch s = new Swatch(a, colour);
                s.setContentDescription(description(a, list.get(i)));
                s.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { choose(colour); close(); }
                });
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(size, size);
                if (i % perRow > 0) sp.leftMargin = gap;
                row.addView(s, sp);
            }
            shell.addView(rows, new FrameLayout.LayoutParams(-2, -2));
            int bottom = decor.getHeight() - anchorTop(a) + dp(a, 12);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = bottom;
            layer.addView(shell, lp);
            decor.addView(layer, new FrameLayout.LayoutParams(-1, -1));
            sOpen = new java.lang.ref.WeakReference<View>(layer);
            shell.setAlpha(0f); shell.setScaleX(0.94f); shell.setScaleY(0.94f);
            shell.setPivotY(dp(a, 120));
            shell.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } catch (Throwable ignored) { }
    }

    static boolean menuShowing() { return sOpen.get() != null; }

    static int topBarBottom(Activity a) {
        View decor = a.getWindow().getDecorView();
        View compose = ComposeTree.composeView(ScreenshotEditor.canvasView.get());
        int[] at = new int[2]; decor.getLocationOnScreen(at);
        int bottom = 0, limit = at[1] + decor.getHeight() / 4;
        if (compose != null) for (ComposeTree.Node n : ComposeTree.clickables(compose)) if (!n.bounds.isEmpty() && n.bounds.bottom < limit) bottom = Math.max(bottom, n.bounds.bottom);
        if (bottom == 0) return -1;
        return bottom - at[1];
    }

    static int topBarGuess(Activity a) {
        int status = 0;
        android.view.WindowInsets in = a.getWindow().getDecorView().getRootWindowInsets();
        if (in != null) status = in.getSystemWindowInsetTop();
        return status + dp(a, 72);
    }

    static int anchorTop(Activity a) {
        View compose = ComposeTree.composeView(ScreenshotEditor.canvasView.get());
        int top = Integer.MAX_VALUE;
        for (ComposeTree.Node n : ComposeTree.clickables(compose)) {
            String l = n.label == null ? "" : n.label.toLowerCase(java.util.Locale.ROOT);
            if (l.contains("stroke") || l.contains("eraser") || l.contains("caption") || l.contains("crop")) top = Math.min(top, n.bounds.top);
        }
        Rect slider = ComposeTree.boundsOf(compose, new String[]{label(a, "stroke_menu_stroke_weight_slider_accessibility"), "stroke weight", "weight"});
        if (slider != null && !slider.isEmpty()) top = Math.min(top, slider.top);
        if (top == Integer.MAX_VALUE) top = a.getWindow().getDecorView().getHeight() - dp(a, 200);
        int[] at = new int[2]; a.getWindow().getDecorView().getLocationOnScreen(at);
        return top - at[1];
    }

    private static String label(Context c, String name) {
        try { int id = c.getResources().getIdentifier(name, "string", ScreenshotEditor.PACKAGE); return id == 0 ? null : c.getString(id); } catch (Throwable t) { return null; }
    }

    private static long colourOf(Object item) {
        try { for (Field f : item.getClass().getDeclaredFields()) if (f.getType() == long.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f.getLong(item); } } catch (Throwable ignored) { }
        return 0L;
    }

    private static String description(Context c, Object item) {
        try {
            for (Field f : item.getClass().getDeclaredFields()) if (f.getType() == int.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return c.getString(f.getInt(item)); }
        } catch (Throwable ignored) { }
        return null;
    }

    private static void choose(long colour) {
        try {
            Object ctl = sController;
            if (ctl == null) return;
            for (Class<?> k = ctl.getClass(); k != null; k = k.getSuperclass())
                for (Class<?> i : k.getInterfaces())
                    for (Method m : i.getMethods())
                        if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == long.class && m.getReturnType() == void.class) {
                            m.invoke(ctl, colour); sColour = colour; return;
                        }
        } catch (Throwable ignored) { }
    }

    static int argb(long packed) { return (int) (packed >>> 32); }

    static final class Swatch extends View {
        final long colour; final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        Swatch(Context c, long colour) {
            super(c); this.colour = colour;
            fill.setColor(argb(colour));
            edge.setStyle(Paint.Style.STROKE);
            setClickable(true);
        }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density, r = Math.min(w, h) / 2f;
            boolean chosen = argb(colour) == argb(sColour);
            float inner = chosen ? r - 4.5f * d : r - 1f * d;
            canvas.drawCircle(w / 2f, h / 2f, inner, fill);
            edge.setStrokeWidth(1f * d); edge.setColor((ring & 0xffffff) | 0x33000000);
            if (!chosen) canvas.drawCircle(w / 2f, h / 2f, inner, edge);
            if (chosen) { edge.setStrokeWidth(2.2f * d); edge.setColor(ring); canvas.drawCircle(w / 2f, h / 2f, r - 1.2f * d, edge); }
        }
    }
}
