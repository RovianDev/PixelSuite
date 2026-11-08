package io.github.pixelsuite;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

final class PinAuto {
    private PinAuto() {}

    static final String KEY = "pixelsuite_pin_autoconfirm";
    private static final long RECHECK_MS = 3000;
    private static final Map<Object, Long> sAsked = new WeakHashMap<Object, Long>();
    private static final Map<Object, Integer> sKnown = new WeakHashMap<Object, Integer>();
    private static volatile boolean sSaved, sWatching;

    static void installSystem(ClassLoader cl) {
        Class<?> spm = Xp.findClass("com.android.server.locksettings.SyntheticPasswordManager", cl);
        for (Method m : spm.getDeclaredMethods()) {
            if (!"derivePinLength".equals(m.getName()) || m.getParameterTypes().length != 3) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (((Number) p.getResult()).intValue() != -1) return;
                        int size = ((Number) p.args[0]).intValue(), user = ((Number) p.args[2]).intValue();
                        if (size != 4 || !Boolean.TRUE.equals(p.args[1]) || user != 0) return;
                        if (optionOnSystem()) p.setResult(Integer.valueOf(4));
                    } catch (Throwable ignored) { }
                }
            });
        }
        Xp.deoptimizeAll(spm, "refreshPinLengthOnDisk");
        Xp.deoptimizeAll(spm, "createLskfBasedProtector");
    }

    private static boolean optionOnSystem() {
        try {
            Object thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            Context c = (Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
            return Settings.Secure.getInt(c.getContentResolver(), KEY, 0) == 1;
        } catch (Throwable t) { return false; }
    }

    private static void saveLength(final Object lpu) {
        new Thread(new Runnable() { public void run() {
            try { } catch (Throwable t) { }
        } }, "PixelSuitePinLength").start();
    }

    private static synchronized void watch(final Context ctx, final Object lpu) {
        if (sWatching) return;
        sWatching = true;
        try {
            ctx.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                @Override public void onChange(boolean self) {
                    sSaved = false;
                    saveLength(lpu);
                }
            });
        } catch (Throwable ignored) { }
    }

    static void installSystemUi(ClassLoader cl) {
        final Class<?> ctl = Xp.findClass("com.android.keyguard.KeyguardPinViewController", cl);
        for (Method m : ctl.getDeclaredMethods()) {
            if (!"onUserInput".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { onInput(p.thisObject, ctl); } catch (Throwable t) { }
                }
            });
        }
        for (Class<?> k = ctl; k != null && k.getName().startsWith("com.android.keyguard."); k = k.getSuperclass())
            for (Method m : k.getDeclaredMethods()) if (!"onUserInput".equals(m.getName()) || k != ctl) Xp.deoptimize(m);
    }

    private static void onInput(Object c, Class<?> ctl) throws Exception {
        if (c.getClass() != ctl) return;
        Context ctx = ((View) Xp.getObjectField(c, "mView")).getContext();
        boolean on = Settings.Secure.getInt(ctx.getContentResolver(), KEY, 0) == 1;
        watch(ctx.getApplicationContext(), Xp.getObjectField(c, "mLockPatternUtils"));
        if (!on) { return; }
        int user = ((Number) Xp.callMethod(Xp.getObjectField(c, "mSelectedUserInteractor"), "getSelectedUserId")).intValue();
        Object entry = Xp.getObjectField(c, "mPasswordEntry");
        int typed = ((CharSequence) Xp.getObjectField(entry, "mText")).length();
        int length = length(c, user);
        Object lpu = Xp.getObjectField(c, "mLockPatternUtils");
        int failed = ((Number) Xp.callMethod(lpu, "getCurrentFailedPasswordAttempts", user)).intValue();
        boolean go = user == 0 && length == 4 && typed == 4 && failed < 5;
        if (user == 0 && length == 4 && !sSaved) { sSaved = true; saveLength(lpu); }
        if (go) Xp.callMethod(c, "verifyPasswordAndUnlock");
    }

    private static int length(Object c, int user) {
        synchronized (sKnown) { Integer k = sKnown.get(c); if (k != null && k == 4) return 4; }
        try {
            long f = ((Number) Xp.getObjectField(c, "mPinLength")).longValue();
            if (f == 4) { synchronized (sKnown) { sKnown.put(c, 4); } return 4; }
        } catch (Throwable ignored) { }
        long now = SystemClock.uptimeMillis();
        synchronized (sAsked) {
            Long at = sAsked.get(c);
            if (at != null && now - at < RECHECK_MS) return -1;
            sAsked.put(c, now);
        }
        try {
            int l = ((Number) Xp.callMethod(Xp.getObjectField(c, "mLockPatternUtils"), "getPinLength", user)).intValue();
            if (l == 4) synchronized (sKnown) { sKnown.put(c, 4); }
            return l;
        } catch (Throwable t) { return -1; }
    }
}
