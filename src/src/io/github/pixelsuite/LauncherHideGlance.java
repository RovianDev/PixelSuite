package io.github.pixelsuite;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Field;

final class LauncherHideGlance {
    static final String KEY = "pixelsuite_hide_glance";
    private static volatile Boolean on;
    private LauncherHideGlance() { }

    static boolean supported() { return android.os.Build.VERSION.SDK_INT <= 36; }

    private static boolean on() {
        Boolean v = on;
        if (v != null) return v;
        try {
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c == null) return false;
            v = Settings.Secure.getInt(c.getContentResolver(), KEY, 0) == 1;
            on = v;
            return v;
        } catch (Throwable t) { return false; }
    }

    private static Field field(Class<?> c, String... names) {
        for (String n : names)
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
                try { Field f = k.getDeclaredField(n); f.setAccessible(true); return f; } catch (Throwable ignored) { }
        return null;
    }

    static void install(ClassLoader cl) {
        if (!supported()) return;
        try {
            Xp.hookAllMethods(Xp.findClass("com.android.launcher3.Workspace", cl), "bindAndInitFirstWorkspaceScreen", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if (on()) unpin(p.thisObject); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        try {
            Class<?> cursor = Xp.findClass("com.android.launcher3.model.LoaderCursor", cl);
            final ThreadLocal<Object[]> held = new ThreadLocal<Object[]>();
            Xp.Callback freeRow = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!on() || held.get() != null) return;
                        Field idpField = field(p.thisObject.getClass(), "mIDP", "idp");
                        Object idp = idpField == null ? null : idpField.get(p.thisObject);
                        Field cols = idp == null ? null : field(idp.getClass(), "numSearchContainerColumns");
                        if (cols == null) return;
                        int was = cols.getInt(idp);
                        if (was <= 0) return;
                        held.set(new Object[] { idp, cols, was });
                        cols.setInt(idp, 0);
                    } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object[] h = held.get();
                    if (h == null) return;
                    held.remove();
                    try { ((Field) h[1]).setInt(h[0], (Integer) h[2]); } catch (Throwable ignored) { }
                }
            };
            Xp.hookAllMethods(cursor, "checkAndAddItem", freeRow);
            Xp.hookAllMethods(cursor, "checkItemPlacement", freeRow);
        } catch (Throwable ignored) { }
        try {
            Xp.hookAllMethods(Xp.findClass("com.android.launcher3.dagger.AppModule_ProvideSmartspaceItemFactory", cl), "get", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { if (on() && p.args.length == 0) p.setResult(java.util.Collections.emptySet()); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void unpin(Object workspace) throws Exception {
        Field f = field(workspace.getClass(), "mFirstPagePinnedItem", "mQsb");
        Object v = f == null ? null : f.get(workspace);
        if (!(v instanceof View)) return;
        View pinned = (View) v;
        ViewParent parent = pinned.getParent();
        if (parent instanceof ViewGroup) {
            ViewParent screen = parent.getParent();
            if (screen instanceof ViewGroup && screen.getClass().getName().endsWith("CellLayout")) ((ViewGroup) screen).removeView(pinned);
            else ((ViewGroup) parent).removeView(pinned);
        }
        f.set(workspace, null);
    }
}
