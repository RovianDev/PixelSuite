package io.github.pixelsuite;

import android.content.Context;

final class GboardToolbar {
    private GboardToolbar() {}

    static final String KEY = "pixelsuite_gboard_toolbar";
    static final int MAX = 7;
    private static final String PROP = "ro.com.google.ime.top_icon_num";
    private static Boolean sOn;

    static void install() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Xp.hookAllMethods(sp, "get", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length == 0 || !PROP.equals(p.args[0]) || !on()) return;
                        p.setResult(String.valueOf(MAX));
                                            } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static boolean on() {
        Boolean v = sOn;
        if (v != null) return v;
        try {
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c == null) return false;
            v = PatronGate.ok(c);
            sOn = v;
            return v;
        } catch (Throwable t) { return false; }
    }
}
