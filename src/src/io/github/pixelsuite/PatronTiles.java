package io.github.pixelsuite;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

final class PatronTiles {
    private PatronTiles() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Context sApp;
    private static final Runnable CHANGED = new Runnable() { public void run() { sync(sApp); } };

    static void watch(Context c) {
        if (c == null) return;
        sApp = c.getApplicationContext();
        PatronAccess.watch(CHANGED);
        settle(0);
    }

    private static void settle(final int tries) {
        if (PatronAccess.initialized()) { sync(sApp); return; }
        if (tries < 30) MAIN.postDelayed(new Runnable() { public void run() { settle(tries + 1); } }, 1000);
    }

    static void sync(Context c) {
        try {
            if (c == null || !PatronAccess.initialized()) return;
            PackageManager pm = c.getPackageManager();
            ComponentName tile = new ComponentName(c, VpnTile.class);
            int want = PatronAccess.unlocked() ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (pm.getComponentEnabledSetting(tile) != want) pm.setComponentEnabledSetting(tile, want, PackageManager.DONT_KILL_APP);
        } catch (Throwable ignored) { }
        SuiteApps.sync(c);
    }
}
