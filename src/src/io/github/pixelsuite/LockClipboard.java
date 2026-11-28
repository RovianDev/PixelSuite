package io.github.pixelsuite;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Binder;
import android.provider.Settings;

final class LockClipboard {
    private LockClipboard() { }

    static final String LIVE = "pixelsuite_lock_clip_live";
    private static final long FRESH = 30L * 60L * 1000L;
    private static volatile long sAt;
    private static volatile boolean sWatching;

    static void mark(Context c, boolean open) {
        try { Settings.Global.putLong(c.getContentResolver(), LIVE, open ? System.currentTimeMillis() : 0L); } catch (Throwable ignored) { }
    }

    static boolean live(Context c) {
        if (c == null) return false;
        watch(c);
        if (fresh(sAt)) return true;
        long token = Binder.clearCallingIdentity();
        try { sAt = Settings.Global.getLong(c.getContentResolver(), LIVE, 0L); } catch (Throwable ignored) { } finally { Binder.restoreCallingIdentity(token); }
        return fresh(sAt);
    }

    private static boolean fresh(long at) {
        return at > 0 && Math.abs(System.currentTimeMillis() - at) < FRESH;
    }

    private static void watch(final Context c) {
        if (sWatching) return;
        sWatching = true;
        long token = Binder.clearCallingIdentity();
        try {
            sAt = Settings.Global.getLong(c.getContentResolver(), LIVE, 0L);
            c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(LIVE), false, new ContentObserver(null) {
                @Override public void onChange(boolean self) {
                    long t = Binder.clearCallingIdentity();
                    try { sAt = Settings.Global.getLong(c.getContentResolver(), LIVE, 0L); } catch (Throwable ignored) { } finally { Binder.restoreCallingIdentity(t); }
                }
            });
        } catch (Throwable t) { sWatching = false; } finally { Binder.restoreCallingIdentity(token); }
    }

    static void installSystem(ClassLoader cl) {
        Class<?> k;
        try { k = Xp.findClass("com.android.server.clipboard.ClipboardService", cl); } catch (Throwable t) { return; }
        Xp.hookAllMethods(k, "isDeviceLocked", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Object c = Xp.callMethod(p.thisObject, "getContext");
                    if (c instanceof Context && live((Context) c)) p.setResult(false);
                } catch (Throwable ignored) { }
            }
        });
    }

    static void installGboard() {
        Xp.Callback open = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!GboardPlus.searchField()) return;
                    Object c = null;
                    try { c = Xp.getObjectField(p.thisObject, "mContext"); } catch (Throwable ignored) { }
                    if (!(c instanceof Context)) c = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                    if (c instanceof Context && live((Context) c)) p.setResult(false);
                } catch (Throwable ignored) { }
            }
        };
        Xp.hookAllMethods(android.app.KeyguardManager.class, "isDeviceLocked", open);
        Xp.hookAllMethods(android.app.KeyguardManager.class, "isKeyguardLocked", open);
    }
}
