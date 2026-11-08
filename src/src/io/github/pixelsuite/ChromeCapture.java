package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class ChromeCapture {
    interface Got { void got(Bitmap page); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Field sManagerField, sScaleField;
    private static Method sCapture, sActivityTab, sWebContents;
    private static boolean sResolved, sBroken;

    static boolean page(Activity a, View anyView, final Got cb) {
        if (sBroken) { return false; }
        try {
            if (a == null) a = activity(anyView);
            if (a == null) { return false; }
            if (!resolve(a)) { sBroken = true; return false; }
            final Object manager = sManagerField.get(a);
            if (manager == null) { return false; }
            Object tab = sActivityTab.invoke(a);
            if (tab == null) { return false; }
            Object wc = sWebContents.invoke(tab);
            if (wc == null) { return false; }
            Class<?> callback = Class.forName("org.chromium.base.Callback", false, manager.getClass().getClassLoader());
            final boolean[] answered = {false};
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, new java.lang.reflect.InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] args) {
                    String n = m.getName();
                    if ("onResult".equals(n)) {
                        final Bitmap b = args != null && args.length == 1 && args[0] instanceof Bitmap ? (Bitmap) args[0] : null;
                        MAIN.post(new Runnable() { public void run() { if (!answered[0]) { answered[0] = true; cb.got(b); } } });
                        return null;
                    }
                    if ("hashCode".equals(n)) return System.identityHashCode(p);
                    if ("equals".equals(n)) return args != null && args.length == 1 && p == args[0];
                    if ("toString".equals(n)) return "PixelSuite.ChromeCapture";
                    return null;
                }
            });
            float scale0 = sScaleField.getFloat(manager);
            try {
                sScaleField.setFloat(manager, 2f);
                sCapture.invoke(manager, tab, Boolean.TRUE, proxy);
            } finally {
                sScaleField.setFloat(manager, scale0);
            }
            MAIN.postDelayed(new Runnable() { public void run() { if (!answered[0]) { answered[0] = true; cb.got(null); } } }, 400);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean sHooked;
    private static java.lang.ref.WeakReference<Object> sManager = new java.lang.ref.WeakReference<Object>(null);
    private static float sScale0;
    private static Method sCacheBitmap;

    static void recache(Bitmap full) {
        try {
            Object manager = sManager.get();
            Object tab = sTapTab;
            if (manager == null || tab == null || full == null || full.isRecycled() || sScale0 <= 0f || sScale0 >= 1f) return;
            if (sCacheBitmap == null) {
                Class<?> n = Class.forName("J.N", false, manager.getClass().getClassLoader());
                sCacheBitmap = n.getMethod("VFJOO", float.class, long.class, Object.class, Object.class);
            }
            long ptr = (Long) manager.getClass().getMethod("getNativePtr").invoke(manager);
            if (ptr == 0) return;
            Bitmap src = full;
            if (src.getConfig() != null && "HARDWARE".equals(src.getConfig().name())) src = src.copy(Bitmap.Config.ARGB_8888, false);
            if (src == null) return;
            int w = Math.max(1, Math.round(src.getWidth() * sScale0)), h = Math.max(1, Math.round(src.getHeight() * sScale0));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            sCacheBitmap.invoke(null, sScale0, ptr, tab, small);
        } catch (Throwable t) {
        }
    }
    private static long sTapAt;
    private static Object sTapTab;

    static void tapped(View anyView) {
        try {
            Activity a = activity(anyView);
            if (a == null) return;
            if (!resolve(a)) return;
            sTapAt = android.os.SystemClock.uptimeMillis();
            sTapTab = sActivityTab.invoke(a);
            Object manager = sManagerField.get(a);
            sManager = new java.lang.ref.WeakReference<Object>(manager);
            if (manager != null) sScale0 = sScaleField.getFloat(manager);
            if (!sHooked) { sHooked = true; hook(); }
        } catch (Throwable t) { }
    }

    private static void hook() {
        Class<?> k = sCapture.getDeclaringClass();
        for (final Method m : k.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getName().equals("getNativePtr") || m.getName().equals("getTabById")) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                final ThreadLocal<Float> saved = new ThreadLocal<Float>();
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    long since = android.os.SystemClock.uptimeMillis() - sTapAt;
                    if (since > 2000) return;
                    StringBuilder sb = new StringBuilder();
                    if (p.args != null) for (Object o : p.args) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(o == null ? "null" : o == sTapTab ? "currentTab" : (o instanceof Boolean || o instanceof Number || o instanceof android.util.Size) ? String.valueOf(o) : o.getClass().getSimpleName());
                    }
                    if (m.equals(sCapture) && p.args.length == 3 && p.args[0] == sTapTab && since < 1200 && "hook".equals(ChromeTabTransition.captureMode())) {
                        try {
                            saved.set(sScaleField.getFloat(p.thisObject));
                            sScaleField.setFloat(p.thisObject, 2f);
                        } catch (Throwable t) { }
                    }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Float f = saved.get();
                    if (f == null) return;
                    saved.remove();
                    try { sScaleField.setFloat(p.thisObject, f); } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static boolean resolve(Activity a) throws Exception {
        if (sResolved) return sManagerField != null;
        sResolved = true;
        ClassLoader cl = a.getClass().getClassLoader();
        Class<?> managerClass = Class.forName("org.chromium.chrome.browser.tab_ui.TabContentManager", false, cl);
        Class<?> tabClass = Class.forName("org.chromium.chrome.browser.tab.Tab", false, cl);
        Class<?> callback = Class.forName("org.chromium.base.Callback", false, cl);
        for (Class<?> k = a.getClass(); k != null && k != Activity.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getType() == managerClass && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); sManagerField = f; break; }
            }
            if (sManagerField != null) break;
        }
        for (Class<?> k = a.getClass(); k != null && k != Activity.class && sActivityTab == null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getReturnType() == tabClass && m.getParameterTypes().length == 0 && !Modifier.isStatic(m.getModifiers())) { m.setAccessible(true); sActivityTab = m; break; }
            }
        }
        for (Field f : managerClass.getDeclaredFields()) {
            if (f.getType() == float.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); sScaleField = f; break; }
        }
        for (Method m : managerClass.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 3 && p[0] == tabClass && p[1] == boolean.class && p[2] == callback) { m.setAccessible(true); sCapture = m; break; }
        }
        sWebContents = tabClass.getMethod("getWebContents");
        if (sManagerField == null || sActivityTab == null || sScaleField == null || sCapture == null) { sManagerField = null; return false; }
        return true;
    }

    private static Activity activity(View v) {
        android.content.Context c = v.getContext();
        for (int i = 0; i < 10 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof android.content.ContextWrapper)) break;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
