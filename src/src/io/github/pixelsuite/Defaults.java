package io.github.pixelsuite;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Defaults {
    private Defaults() { }

    static final String KEY = "pixelsuite_defaults", OLD = "legacy", NEW = "off";
    private static final Set<String> OLD_KEYS = new HashSet<String>(Arrays.asList(
            "powertorch_double_press", "threefingershot_enabled", "voldowncam_enabled", "voldowncam_launch_at"));
    private static volatile int sState = -1;
    private static volatile long sAt;

    static boolean legacy() {
        long now = SystemClock.uptimeMillis();
        if (sState >= 0 && now - sAt < 60000L) return sState == 1;
        try {
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c == null) return true;
            ContentResolver r = c.getContentResolver();
            String v = Settings.Secure.getString(r, KEY);
            boolean old = OLD.equals(v) || (!NEW.equals(v) && Settings.Global.getString(r, SystemRev.KEY) != null);
            remember(old);
            return old;
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean on(boolean before) { return before && legacy(); }

    private static void remember(boolean old) {
        sState = old ? 1 : 0;
        long now = SystemClock.uptimeMillis();
        sAt = now == 0 ? 1 : now;
    }

    private static boolean counts(String name) {
        if (name == null || KEY.equals(name)) return false;
        if (OLD_KEYS.contains(name)) return true;
        if (!name.startsWith("pixelsuite_")) return false;
        return !name.startsWith("pixelsuite_patron") && !name.startsWith("pixelsuite_esim") && !name.startsWith("pixelsuite_device")
                && !name.startsWith("pixelsuite_dna_status") && !name.startsWith("pixelsuite_token");
    }

    static void installSystem(ClassLoader cl) {
        Class<?> pwm = Xp.findClass("com.android.server.policy.PhoneWindowManager", cl);
        if (pwm == null) return;
        Xp.hookAllMethods(pwm, "systemReady", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Context c = (Context) Xp.getObjectField(p.thisObject, "mContext");
                    if (c != null) decideSystem(c.getContentResolver());
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void decideSystem(ContentResolver r) {
        if (Settings.Secure.getString(r, KEY) != null) return;
        boolean used = Settings.Global.getString(r, SystemRev.KEY) != null;
        if (!used) {
            Cursor q = null;
            try {
                q = r.query(Settings.Secure.CONTENT_URI, new String[]{"name"}, null, null, null);
                while (q != null && q.moveToNext()) if (counts(q.getString(0))) { used = true; break; }
            } catch (Throwable ignored) {
            } finally {
                if (q != null) q.close();
            }
        }
        Settings.Secure.putString(r, KEY, used ? OLD : NEW);
    }

    static void decide() {
        try {
            List<String> got = Root.run("settings get secure " + KEY);
            if (got == null) return;
            String v = got.isEmpty() ? "" : got.get(0).trim();
            if (OLD.equals(v) || NEW.equals(v)) { remember(OLD.equals(v)); return; }
            boolean used = false;
            List<String> rev = Root.run("settings get global " + SystemRev.KEY);
            if (rev != null && !rev.isEmpty() && rev.get(0).trim().length() > 0 && !"null".equals(rev.get(0).trim())) used = true;
            if (!used) {
                List<String> all = Root.run("settings list secure");
                if (all != null) for (String line : all) {
                    int eq = line.indexOf('=');
                    if (counts(eq > 0 ? line.substring(0, eq) : line)) { used = true; break; }
                }
            }
            if (Root.run("settings put secure " + KEY + " " + (used ? OLD : NEW)) != null) remember(used);
        } catch (Throwable ignored) { }
    }
}
