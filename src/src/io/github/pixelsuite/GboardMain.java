package io.github.pixelsuite;

import android.content.Context;
import android.provider.Settings;

import java.lang.reflect.Method;

final class GboardMain {
    private GboardMain() {}

    static final String KEY = "pixelsuite_gboard_plus";

    abstract static class Kept extends Xp.Callback {}

    private static volatile int sState = -1;
    private static volatile Thread sReader;
    private static Method sApp;

    static boolean owns(Object cb) {
        if (cb == null || cb instanceof Kept) return false;
        String n = cb.getClass().getName();
        return n.startsWith("io.github.pixelsuite.Gboard") && !n.startsWith("io.github.pixelsuite.GboardMain");
    }

    static boolean off() {
        int s = sState;
        if (s >= 0) return s != 1;
        if (sReader == Thread.currentThread()) return false;
        Context c = app();
        if (c == null) return true;
        synchronized (GboardMain.class) {
            if (sState >= 0) return sState != 1;
            sReader = Thread.currentThread();
            try {
                if (!SuperDarkApps.GBOARD.equals(c.getPackageName())) sState = 1;
                else sState = PatronGate.ok(c) ? state(c.getContentResolver()) : 0;
            } catch (Throwable t) {
                sState = 0;
            } finally {
                sReader = null;
            }
            return sState != 1;
        }
    }

    static boolean turnedOff() { return off() && sState == 2; }

    static boolean on(android.content.ContentResolver r) { return state(r) == 1; }

    private static int state(android.content.ContentResolver r) {
        String v = Settings.Secure.getString(r, KEY);
        if ("1".equals(v)) return 1;
        if ("0".equals(v)) return 2;
        for (String k : OPTIONS) if ("1".equals(Settings.Secure.getString(r, k))) return 1;
        for (String k : CHOICES) { String x = Settings.Secure.getString(r, k); if (x != null && !x.trim().isEmpty() && !"null".equals(x.trim())) return 1; }
        return 0;
    }

    private static final String[] OPTIONS = {
        GboardFrost.KEY, GboardToolbar.KEY, GboardPlus.KEY_SHORTCUTS, GboardPlus.KEY_KEYS, GboardPlus.KEY_CLIP, GboardPlus.KEY_HIDE_CHIPS,
        GboardPlus.KEY_HIDE_MENU, GboardPlus.KEY_HIDE_RIGHT, GboardPlus.KEY_ROWS, GboardPlus.KEY_ROWS_TOP, GboardPlus.KEY_FREE,
        GboardPlus.KEY_DEL_LETTERS, GboardPlus.KEY_DICTATION, GboardPlus.KEY_AR_NO_AUTOCORRECT, GboardPlus.KEY_LOWER,
    };
    private static final String[] CHOICES = { GboardPlus.KEY_BAR, GboardPlus.KEY_GEO, GboardAutoCorrect.KEY_OFF, GboardAutoCorrect.KEY_SET };

    private static Context app() {
        try {
            Method m = sApp;
            if (m == null) { m = Class.forName("android.app.ActivityThread").getMethod("currentApplication"); sApp = m; }
            return (Context) m.invoke(null);
        } catch (Throwable t) { return null; }
    }
}
