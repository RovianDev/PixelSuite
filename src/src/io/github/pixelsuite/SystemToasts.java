package io.github.pixelsuite;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;

final class SystemToasts {
    private SystemToasts() {}

    static final String KEY = "pixelsuite_toast_position";
    static final int TOP = 0, MIDDLE = 1, BOTTOM = 2;

    static int position(View v) {
        try {
            String value = android.provider.Settings.Secure.getString(v.getContext().getContentResolver(), KEY);
            if ("middle".equals(value)) return MIDDLE;
            if ("bottom".equals(value)) return BOTTOM;
        } catch (Throwable ignored) { }
        return TOP;
    }
    static int gravity(int where) { return where == MIDDLE ? Gravity.CENTER : Gravity.TOP | Gravity.CENTER_HORIZONTAL; }
    static int offset(View v, int where) { return where == MIDDLE ? 0 : TOP_DP(v); }

    static int TOP_DP(View v) { return dp(v, 98) - statusBar(v) - dp(v, 22); }

    static void install(ClassLoader cl) { install(); }

    private static Class<?> sPresenterClass;
    private static Field sParams, sManager;
    private static synchronized boolean fields(Class<?> presenter) {
        if (sPresenterClass == presenter) return sParams != null && sManager != null;
        sPresenterClass = presenter; sParams = null; sManager = null;
        for (Class<?> c = presenter; c != null && (sParams == null || sManager == null); c = c.getSuperclass())
            for (Field f : c.getDeclaredFields()) {
                if (sParams == null && f.getType() == android.view.WindowManager.LayoutParams.class) { f.setAccessible(true); sParams = f; }
                else if (sManager == null && android.view.WindowManager.class.isAssignableFrom(f.getType())) { f.setAccessible(true); sManager = f; }
            }
        return sParams != null && sManager != null;
    }

    static void install() {
        try {
            Class<?> presenter = Class.forName("android.widget.ToastPresenter");
            Xp.hookAllMethods(presenter, "show", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        int view = -1;
                        for (int i = 0; i < p.args.length; i++) if (p.args[i] instanceof View) { view = i; break; }
                        if (view < 0) return;
                        int ints = 0, gravity = -1, y = -1;
                        for (int i = view + 1; i < p.args.length; i++) {
                            if (!(p.args[i] instanceof Integer)) continue;
                            ints++;
                            if (ints == 2) gravity = i; else if (ints == 4) { y = i; break; }
                        }
                        View v = (View) p.args[view];
                        frost(v);
                        if (gravity < 0 || y < 0) return;
                        int where = position(v);
                        if (where == BOTTOM) return;
                        p.args[gravity] = gravity(where);
                        p.args[y] = offset(v, where);
                    } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        View v = null; for (Object a : p.args) if (a instanceof View) { v = (View) a; break; }
                        if (v == null || !v.isAttachedToWindow() || !fields(p.thisObject.getClass())) return;
                        android.view.WindowManager.LayoutParams lp = (android.view.WindowManager.LayoutParams) sParams.get(p.thisObject);
                        Object wm = sManager.get(p.thisObject);
                        if (lp == null || wm == null) return;
                        int where = position(v);
                        if (where == BOTTOM) return;
                        int g = gravity(where), y = offset(v, where);
                        if (lp.gravity == g && lp.y == y) return;
                        lp.gravity = g; lp.y = y;
                        ((android.view.WindowManager) wm).updateViewLayout(v, lp);
                    } catch (Throwable error) { }
                }
            });
        } catch (Throwable error) { }
    }

    static void frost(View v) {
        if (!SurfaceStyle.restyled(v.getContext())) return;
        View pill = pill(v);
        if (pill == null || pill.getBackground() instanceof PillFrost) return;
        pill.setPaddingRelative(dp(v, 16), dp(v, 10), dp(v, 16), dp(v, 10));
        PillFrost.apply(pill, v, false);
    }

    static View pill(View v) {
        if (v.getBackground() != null) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = pill(g.getChildAt(i)); if (f != null) return f; } }
        return null;
    }

    static int statusBar(View v) {
        int id = v.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? dp(v, 32) : v.getResources().getDimensionPixelSize(id);
    }
    static int dp(View v, float d) { return Math.round(d * v.getResources().getDisplayMetrics().density); }
}
