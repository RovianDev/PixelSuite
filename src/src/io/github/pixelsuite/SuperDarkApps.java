package io.github.pixelsuite;

import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;

final class SuperDarkApps {
    static final String GBOARD = "com.google.android.inputmethod.latin";
    private SuperDarkApps() {}

    private static long sAt;
    private static boolean sActive;

    static final String SETTINGS = "com.android.settings";
    static final String SETTINGS_SEARCH = "com.google.android.settings.intelligence";
    private static int sBlackMax = 0x2c;

    private static final java.util.List<Object> sHandles = new java.util.ArrayList<Object>();

    private static boolean sGboard;
    static final int KEY = 0x2C2C2E, KEY_PRESSED = 0x48484A;

    static void install(ClassLoader cl, String pkg) {
        if (SETTINGS.equals(pkg) || SETTINGS_SEARCH.equals(pkg)) sBlackMax = 0x18;
        sGboard = GBOARD.equals(pkg);
        sHandles.clear();
        hookResult(android.content.res.Resources.class, "getColor");
        hookResult(android.content.res.TypedArray.class, "getColor");
        hookStateResult(android.content.res.ColorStateList.class, "getColorForState");
        hookStateResult(android.content.res.ColorStateList.class, "getDefaultColor");
        hookFirstArg(android.graphics.drawable.GradientDrawable.class, "setColor");
        hookFirstArg(android.graphics.drawable.ColorDrawable.class, "setColor");
        hookFirstArg(android.view.View.class, "setBackgroundColor");
        if (!GBOARD.equals(pkg)) hookFirstArg(android.graphics.Paint.class, "setColor");
        if (AliasProvider.GOOGLE.equals(pkg)) {
            try {
                keep(Xp.hookAllMethods(android.graphics.Paint.class, "setColor", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (p.args.length != 1 || !(p.args[0] instanceof Long) || !active()) return;
                        try {
                            if (sToArgb == null) { sToArgb = android.graphics.Color.class.getMethod("toArgb", long.class); sPack = android.graphics.Color.class.getMethod("pack", int.class); }
                            int c = (Integer) sToArgb.invoke(null, p.args[0]), v = remap(c);
                            if (v != c) p.args[0] = sPack.invoke(null, v);
                        } catch (Throwable ignored) { }
                    }
                }));
            } catch (Throwable t) { }
        }
        try {
            keep(Xp.hookAllMethods(android.graphics.drawable.GradientDrawable.class, "setColors", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length == 0 || !(p.args[0] instanceof int[]) || !active()) return;
                    int[] in = (int[]) p.args[0], out = null;
                    for (int i = 0; i < in.length; i++) { int v = remap(in[i]); if (v != in[i]) { if (out == null) out = in.clone(); out[i] = v; } }
                    if (out != null) p.args[0] = out;
                }
            }));
        } catch (Throwable t) { }
        if (AliasProvider.GOOGLE.equals(pkg)) {
            try {
                keep(Xp.hookAllMethods(android.view.View.class, "setBackgroundDrawable", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (p.args.length != 1 || !(p.args[0] instanceof android.graphics.drawable.ColorDrawable) || !active()) return;
                        android.graphics.drawable.ColorDrawable d = (android.graphics.drawable.ColorDrawable) p.args[0];
                        int c = d.getColor(), v = remap(c);
                        if (v != c) d.setColor(v);
                    }
                }));
            } catch (Throwable t) { }
            try {
                Xp.Callback black = new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try {
                            if (!activeAnywhere() || Boolean.TRUE.equals(Xp.getAdditionalInstanceField(p.thisObject, "pixelsuite_task_black"))) return;
                            Xp.setAdditionalInstanceField(p.thisObject, "pixelsuite_task_black", Boolean.TRUE);
                            android.app.Activity a = (android.app.Activity) p.thisObject;
                            Object td;
                            try {
                                Class<?> b = Class.forName("android.app.ActivityManager$TaskDescription$Builder");
                                Object builder = b.getConstructor().newInstance();
                                b.getMethod("setBackgroundColor", int.class).invoke(builder, 0xff000000);
                                b.getMethod("setNavigationBarColor", int.class).invoke(builder, 0xff000000);
                                td = b.getMethod("build").invoke(builder);
                            } catch (Throwable old) {
                                td = new android.app.ActivityManager.TaskDescription();
                                android.app.ActivityManager.TaskDescription.class.getMethod("setBackgroundColor", int.class).invoke(td, 0xff000000);
                            }
                            a.setTaskDescription((android.app.ActivityManager.TaskDescription) td);
                        } catch (Throwable ignored) { }
                    }
                };
                keep(Xp.hookAllMethods(android.app.Activity.class, "onCreate", black));
                keep(Xp.hookAllMethods(android.app.Activity.class, "onResume", black));
            } catch (Throwable t) { }
        }
    }
    private static java.lang.reflect.Method sToArgb, sPack;

    private static int keep(java.util.Set<Object> handles) { sHandles.addAll(handles); return handles.size(); }

    private static void unhookAll() {
        for (Object h : sHandles) try { if (h instanceof io.github.libxposed.api.XposedInterface.HookHandle) ((io.github.libxposed.api.XposedInterface.HookHandle) h).unhook(); } catch (Throwable ignored) { }
        sHandles.clear();
    }

    private static int hookResult(Class<?> c, String name) {
        try {
            return keep(Xp.hookAllMethods(c, name, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object r = p.getResult();
                    if (r instanceof Integer && active()) { int v = remap((Integer) r); if (v != (Integer) r) p.setResult(v); }
                }
            }));
        } catch (Throwable t) { return 0; }
    }

    private static int hookStateResult(Class<?> c, String name) {
        try {
            return keep(Xp.hookAllMethods(c, name, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object r = p.getResult();
                    if (r instanceof Integer && active()) { int v = remap((Integer) r); if (v != (Integer) r) p.setResult(v); }
                }
            }));
        } catch (Throwable t) { return 0; }
    }

    private static int hookFirstArg(Class<?> c, String name) {
        try {
            return keep(Xp.hookAllMethods(c, name, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length == 1 && p.args[0] instanceof Integer && active()) p.args[0] = remap((Integer) p.args[0]);
                }
            }));
        } catch (Throwable t) { return 0; }
    }

    private static boolean sScoped; private static volatile boolean sForeign;

    static void installLauncherSettings(final ClassLoader cl) {
        sScoped = true;
        sBlackMax = 0x50;
        try {
            final Class<?> home = Class.forName("com.android.launcher3.Launcher", false, cl);
            Xp.hookAllMethods(android.app.Activity.class, "onResume", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    sForeign = !home.isInstance(p.thisObject); sAt = 0;
                    if (sForeign && sHandles.isEmpty()) install(cl, "com.google.android.apps.nexuslauncher (settings)");
                    else if (!sForeign && !sHandles.isEmpty()) unhookAll();
                }
            });
        } catch (Throwable t) { }
    }

    static void installLauncher(ClassLoader cl) {
        try {
            Class<?> scrim = Class.forName("com.android.launcher3.views.ScrimView", false, cl);
            Xp.hookAllMethods(scrim, "setBackgroundColor", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length == 1 && p.args[0] instanceof Integer && activeAnywhere()) p.args[0] = ((Integer) p.args[0]) & 0xff000000;
                }
            });
            Xp.hookAllMethods(scrim, "onDraw", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { if (activeAnywhere()) blackInt(p.thisObject, "mBackgroundColor"); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { }
        try {
            Class<?> apps = Class.forName("com.android.launcher3.allapps.ActivityAllAppsContainerView", false, cl);
            Xp.Callback black = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { if (activeAnywhere()) drawerBlack(p.thisObject); else drawerBack(p.thisObject); } catch (Throwable ignored) { }
                }
            };
            Xp.hookAllMethods(apps, "dispatchDraw", black);
            Xp.hookAllMethods(apps, "drawOnScrimWithScaleAndBottomOffset", black);
            Xp.Callback panel = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) { sPanel = activeAnywhere(); }
                @Override protected void afterHookedMethod(Xp.Param p) { sPanel = false; }
            };
            Xp.hookAllMethods(apps, "drawOnScrimWithScaleAndBottomOffset", panel);
            Xp.hookAllMethods(apps, "updateHeaderScroll", panel);
            Xp.hookMethod(android.content.res.Resources.class.getDeclaredMethod("getColor", int.class, android.content.res.Resources.Theme.class), new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (!sPanel || android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return;
                    Object r = p.getResult();
                    if (!(r instanceof Integer) || (Integer) r == 0xff000000) return;
                    p.setResult(0xff000000);
                    if (!sPanelSaid) { sPanelSaid = true; }
                }
            });
        } catch (Throwable t) { }
    }

    private static volatile boolean sPanel;
    private static boolean sPanelSaid;

    private static boolean sDrawerSaid;

    private static void drawerBlack(Object apps) throws Throwable {
        blackInt(apps, "mHeaderColor");
        blackInt(apps, "mHeaderProtectionColor");
        for (String name : new String[] {"mHeaderPaint", "mNavBarScrimPaint"}) {
            try {
                Object paint = Xp.getObjectField(apps, name);
                if (paint instanceof android.graphics.Paint) {
                    android.graphics.Paint pt = (android.graphics.Paint) paint;
                    int c = pt.getColor();
                    if ((c & 0xffffff) != 0) pt.setColor(c & 0xff000000);
                }
            } catch (Throwable ignored) { }
        }
        Object sheet;
        try { sheet = Xp.getObjectField(apps, "mBottomSheetBackground"); } catch (Throwable none) { sheet = null; }
        if (!(sheet instanceof android.view.View)) return;
        android.graphics.drawable.Drawable bg = ((android.view.View) sheet).getBackground();
        if (bg == null || Boolean.TRUE.equals(Xp.getAdditionalInstanceField(bg, "pixelsuite_black"))) return;
        String was;
        if (bg instanceof android.graphics.drawable.GradientDrawable) {
            android.graphics.drawable.GradientDrawable g = (android.graphics.drawable.GradientDrawable) bg;
            android.content.res.ColorStateList now = null;
            try { now = (android.content.res.ColorStateList) android.graphics.drawable.GradientDrawable.class.getMethod("getColor").invoke(g); } catch (Throwable ignored) { }
            was = now == null ? "gradient" : Integer.toHexString(now.getDefaultColor());
            if (now != null) Xp.setAdditionalInstanceField(bg, "pixelsuite_was", now);
            g.setColor(0xff000000);
        } else if (bg instanceof android.graphics.drawable.ColorDrawable) {
            int c = ((android.graphics.drawable.ColorDrawable) bg).getColor();
            was = Integer.toHexString(c);
            Xp.setAdditionalInstanceField(bg, "pixelsuite_was", c);
            ((android.graphics.drawable.ColorDrawable) bg).setColor(0xff000000);
        } else {
            was = bg.getClass().getName();
            bg.setColorFilter(new android.graphics.PorterDuffColorFilter(0xff000000, android.graphics.PorterDuff.Mode.SRC_IN));
        }
        try { Xp.setAdditionalInstanceField(bg, "pixelsuite_alpha", (Integer) android.graphics.drawable.Drawable.class.getMethod("getAlpha").invoke(bg)); } catch (Throwable ignored) { }
        bg.setAlpha(255);
        Xp.setAdditionalInstanceField(bg, "pixelsuite_black", Boolean.TRUE);
        if (!sDrawerSaid) { sDrawerSaid = true; }
    }

    private static void drawerBack(Object apps) throws Throwable {
        Object sheet;
        try { sheet = Xp.getObjectField(apps, "mBottomSheetBackground"); } catch (Throwable none) { return; }
        if (!(sheet instanceof android.view.View)) return;
        android.graphics.drawable.Drawable bg = ((android.view.View) sheet).getBackground();
        if (bg == null || !Boolean.TRUE.equals(Xp.getAdditionalInstanceField(bg, "pixelsuite_black"))) return;
        Object was = Xp.getAdditionalInstanceField(bg, "pixelsuite_was");
        if (bg instanceof android.graphics.drawable.GradientDrawable && was instanceof android.content.res.ColorStateList) ((android.graphics.drawable.GradientDrawable) bg).setColor((android.content.res.ColorStateList) was);
        else if (bg instanceof android.graphics.drawable.ColorDrawable && was instanceof Integer) ((android.graphics.drawable.ColorDrawable) bg).setColor((Integer) was);
        else bg.setColorFilter(null);
        Object a = Xp.getAdditionalInstanceField(bg, "pixelsuite_alpha");
        if (a instanceof Integer) bg.setAlpha((Integer) a);
        Xp.setAdditionalInstanceField(bg, "pixelsuite_black", Boolean.FALSE);
        ((android.view.View) sheet).invalidate();
    }

    private static final java.util.Map<String, java.lang.reflect.Field> sFields = new java.util.HashMap<String, java.lang.reflect.Field>();

    private static void blackInt(Object o, String field) {
        try {
            String key = o.getClass().getName() + "#" + field;
            java.lang.reflect.Field f;
            if (sFields.containsKey(key)) f = sFields.get(key);
            else {
                f = null;
                for (Class<?> k = o.getClass(); k != null && f == null; k = k.getSuperclass()) try { f = k.getDeclaredField(field); } catch (NoSuchFieldException none) { }
                if (f != null && f.getType() != int.class) f = null;
                if (f != null) f.setAccessible(true);
                sFields.put(key, f);
            }
            if (f == null) return;
            int c = f.getInt(o);
            if ((c & 0xffffff) != 0) f.setInt(o, c & 0xff000000);
        } catch (Throwable ignored) { }
    }

    static boolean active() {
        if (sScoped && !sForeign) return false;
        return activeAnywhere();
    }

    static boolean activeAnywhere() {
        long now = SystemClock.uptimeMillis();
        if (sAt != 0 && now - sAt < 500) return sActive;
        sAt = now;
        boolean on = false;
        try {
            Context ctx = app();
            if (ctx != null) {
                boolean night = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                on = night && android.provider.Settings.Secure.getInt(ctx.getContentResolver(), SuperDark.KEY, 0) == 1;
            }
        } catch (Throwable ignored) { }
        sActive = on;
        return on;
    }

    private static volatile Context sApp;
    private static java.lang.reflect.Method sCurrentApplication;
    private static Context app() throws Exception {
        Context c = sApp;
        if (c != null) return c;
        if (sCurrentApplication == null) sCurrentApplication = Class.forName("android.app.ActivityThread").getMethod("currentApplication");
        return sApp = (Context) sCurrentApplication.invoke(null);
    }

    static int remap(int c) {
        int a = c >>> 24; if (a == 0) return c;
        if (sGboard && ((c & 0xffffff) == KEY || (c & 0xffffff) == KEY_PRESSED)) return c;
        int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max > 0x50 || max - min > 0x14 || max == 0) return c;
        if (sGboard && max >= 0x30) return (a << 24) | (max < 0x40 ? KEY : KEY_PRESSED);
        int B = sBlackMax;
        int v = max <= B ? 0 : 0x10 + (max - B) * 0x14 / (0x50 - B);
        if (v == max) return c;
        return (a << 24) | (v << 16) | (v << 8) | v;
    }
}
