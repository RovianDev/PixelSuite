package io.github.pixelsuite;

import java.lang.reflect.Method;

final class Downgrade {
    private Downgrade() {}

    static void installSystem(ClassLoader cl) {
        Class<?> utils = Xp.findClass("com.android.server.pm.PackageManagerServiceUtils", cl);
        for (Method m : utils.getDeclaredMethods()) {
            if (!"checkDowngrade".equals(m.getName())) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        String want = prop();
                        if (want == null || want.isEmpty()) return;
                        for (Object a : p.args) if (want.equals(name(a))) { p.setResult(null); return; }
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static String prop() {
        try { return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, StoreBackup.RESTORE_PROP); }
        catch (Throwable t) { return null; }
    }

    private static String name(Object o) {
        if (o == null) return null;
        try { Object v = o.getClass().getField("packageName").get(o); if (v instanceof String) return (String) v; } catch (Throwable ignored) { }
        try { Object v = o.getClass().getMethod("getPackageName").invoke(o); if (v instanceof String) return (String) v; } catch (Throwable ignored) { }
        return null;
    }
}
