package io.github.pixelsuite;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

final class SystemRev {
    private SystemRev() { }
    static final String REV = "af14ccb2a4c1";
    static final String KEY = "pixelsuite_system_rev";

    static void installSystem(ClassLoader cl) {
        Class<?> pwm = Xp.findClass("com.android.server.policy.PhoneWindowManager", cl);
        if (pwm == null) return;
        Xp.Callback write = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    Context c = (Context) Xp.getObjectField(p.thisObject, "mContext");
                    if (c != null) write(c);
                } catch (Throwable ignored) { }
            }
        };
        Xp.hookAllMethods(pwm, "systemReady", write);
        Xp.hookAllMethods(pwm, "systemBooted", write);
    }

    private static void write(Context c) {
        long bootSec = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000;
        String boot = bootId();
        Settings.Global.putString(c.getContentResolver(), KEY, REV + "|" + bootSec + (boot != null ? "|" + boot : ""));
    }

    private static String sBoot;
    private static boolean sBootRead;
    static synchronized String bootId() {
        if (sBootRead) return sBoot;
        sBootRead = true;
        String v = null;
        try { v = (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, "ro.boottime.init"); } catch (Throwable ignored) { }
        if (v == null || v.trim().isEmpty()) {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[] { "getprop", "ro.boottime.init" });
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                v = r.readLine();
            } catch (Throwable ignored) { } finally { if (p != null) p.destroy(); }
        }
        if (v == null || v.trim().isEmpty()) return null;
        return sBoot = v.trim();
    }

    static Boolean behind(Context c) {
        try {
            String v = Settings.Global.getString(c.getContentResolver(), KEY);
            if (v == null) return null;
            String[] f = v.split("\\|");
            if (f.length < 2 || f[0].isEmpty()) return null;
            String boot = bootId();
            if (f.length >= 3 && boot != null) {
                if (!f[2].equals(boot)) return null;
            } else {
                long written = Long.parseLong(f[1]);
                long now = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000;
                if (Math.abs(written - now) > 90) return null;
            }
            return !REV.equals(f[0]);
        } catch (Throwable t) { return null; }
    }
}
