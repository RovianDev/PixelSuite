package io.github.pixelsuite;

import android.content.Context;

final class ChromePatron {
    private ChromePatron() {}

    private static volatile long sUntil;
    private static volatile boolean sOk;

    static boolean ok(Context c) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            if (now < sUntil) return sOk;
            Context app = c != null ? c.getApplicationContext() : null;
            if (app == null) app = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            boolean ok = app != null && PatronGate.ok(app);
            sOk = ok;
            sUntil = now + 30000;
            return ok;
        } catch (Throwable t) { return false; }
    }
}
