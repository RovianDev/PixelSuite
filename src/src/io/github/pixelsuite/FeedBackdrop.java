package io.github.pixelsuite;

import java.util.HashMap;
import java.util.Map;

final class FeedBackdrop {
    private FeedBackdrop() {}
    private static final Map<String, Boolean> callers = new HashMap<String, Boolean>();
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private static volatile boolean watching;
    private static volatile boolean stale;

    static void install() {
        Xp.Callback black = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!watching) watchGoogleUpdates();
                try {
                    if (p.args.length != 1 || !SuperDark.inEffectNow()) return;
                    int c;
                    if (p.args[0] instanceof Integer) c = (Integer) p.args[0];
                    else if (p.args[0] instanceof Long) c = (Integer) android.graphics.Color.class.getMethod("toArgb", long.class).invoke(null, p.args[0]);
                    else return;
                    if (!panelGrey(c) || !fromForeignView()) return;
                    if (p.args[0] instanceof Integer) p.args[0] = 0xff000000;
                    else p.args[0] = 0xff000000L << 32;
                } catch (Throwable ignored) { }
            }
        };
        try { Xp.hookAllMethods(Class.forName("android.graphics.BaseRecordingCanvas"), "drawColor", black); } catch (Throwable ignored) { }
        try {
            Xp.hookAllMethods(android.app.Application.class, "onCreate", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { watchGoogleUpdates(); }
            });
        } catch (Throwable ignored) { }
    }

    private static synchronized void watchGoogleUpdates() {
        if (watching) return;
        try {
            Object a = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (!(a instanceof android.content.Context)) return;
            final android.content.Context app = ((android.content.Context) a).getApplicationContext();
            watching = true;
            android.content.IntentFilter pkgs = new android.content.IntentFilter();
            pkgs.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED);
            pkgs.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);
            pkgs.addDataScheme("package");
            app.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, android.content.Intent i) {
                    android.net.Uri d = i.getData();
                    if (d == null || !GOOGLE.equals(d.getSchemeSpecificPart())) return;
                    stale = true;
                    if (!awake(app)) restart();
                }
            }, pkgs);
            app.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, android.content.Intent i) {
                    if (stale) restart();
                }
            }, new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF));
        } catch (Throwable ignored) { }
    }

    private static boolean awake(android.content.Context c) {
        try { return ((android.os.PowerManager) c.getSystemService(android.content.Context.POWER_SERVICE)).isInteractive(); }
        catch (Throwable t) { return true; }
    }

    private static void restart() {
        stale = false;
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    static boolean panelGrey(int c) {
        if ((c >>> 24) != 0xff) return false;
        int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max >= 0x10 && max <= 0x30 && max - min <= 0x0c;
    }

    private static boolean fromForeignView() {
        StackTraceElement[] s = new Throwable().getStackTrace();
        for (int i = 1; i < s.length && i < 14; i++) {
            if (!"onDraw".equals(s[i].getMethodName())) continue;
            String k = s[i].getClassName();
            synchronized (callers) {
                Boolean hit = callers.get(k);
                if (hit == null) {
                    hit = !(k.startsWith("android.") || k.startsWith("com.android.") || k.startsWith("androidx.") || k.startsWith("io.github.pixelsuite"));
                    callers.put(k, hit);
                }
                return hit;
            }
        }
        return false;
    }
}
