package io.github.pixelsuite;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

final class AodPlus {
    private AodPlus() {}

    static final String KEY_CHARGING = "pixelsuite_aod_charging";
    static final String KEY_AFTER = "pixelsuite_aod_after_off";
    static final String KEY_SECONDS = "pixelsuite_aod_after_off_seconds";
    static final int DEFAULT_SECONDS = 5;
    private static final int SLEEP_REASON_POWER_BUTTON = 4;

    private static volatile boolean sCharging, sAfter, sPlugged, sRealAod;
    private static volatile int sSeconds = DEFAULT_SECONDS;
    private static volatile boolean sAwake = true, sPowerSleep, sExpired;
    private static volatile int sGeneration;
    private static boolean sStarted;
    private static Handler sMain;
    private static WeakReference<Object> sDoze = new WeakReference<Object>(null), sMachine = new WeakReference<Object>(null);
    private static Object DOZE, DOZE_AOD, DOZE_AOD_PAUSED, DOZE_AOD_PAUSING;
    private static volatile Runnable sExpiry;

    static int seconds(String v) {
        try { int s = Integer.parseInt(v.trim()); return s >= 1 && s <= 60 ? s : DEFAULT_SECONDS; } catch (Throwable t) { return DEFAULT_SECONDS; }
    }

    private static void readOptions(ContentResolver cr) {
        try {
            sCharging = Settings.Secure.getInt(cr, KEY_CHARGING, 0) == 1;
            sAfter = Settings.Secure.getInt(cr, KEY_AFTER, 0) == 1;
            String s = Settings.Secure.getString(cr, KEY_SECONDS);
            sSeconds = s == null ? DEFAULT_SECONDS : seconds(s);
            sRealAod = Settings.Secure.getInt(cr, "doze_always_on", 0) == 1;
        } catch (Throwable ignored) { }
    }

    private static boolean pluggedNow(Context c) {
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            return b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        } catch (Throwable t) { return false; }
    }

    private static boolean wanted() {
        return (sCharging && sPlugged) || (sAfter && !sExpired && (sAwake || sPowerSleep));
    }

    private static boolean timed() {
        return sAfter && !sRealAod && !(sCharging && sPlugged);
    }

    static void installSystem(ClassLoader cl) {
        Class<?> config = Xp.findClass("android.hardware.display.AmbientDisplayConfiguration", cl);
        Xp.hookAllMethods(config, "enabled", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!Boolean.TRUE.equals(p.getResult()) && ((sCharging && sPlugged) || sAfter)) p.setResult(Boolean.TRUE);
            }
        });
        final ThreadLocal<Boolean> forced = new ThreadLocal<Boolean>();
        try { Xp.hookAllMethods(Xp.findClass("com.android.server.power.PowerManagerService", cl), "shouldWakeUpWhenPluggedOrUnpluggedLocked", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!sCharging || Boolean.TRUE.equals(Xp.getObjectField(p.thisObject, "mAlwaysOnEnabled"))) return;
                    Xp.setObjectField(p.thisObject, "mAlwaysOnEnabled", Boolean.TRUE);
                    forced.set(Boolean.TRUE);
                } catch (Throwable ignored) { }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!Boolean.TRUE.equals(forced.get())) return;
                forced.remove();
                try { Xp.setObjectField(p.thisObject, "mAlwaysOnEnabled", Boolean.FALSE); } catch (Throwable ignored) { }
            }
        }); } catch (Throwable ignored) { }
        Xp.hookAllMethods(Xp.findClass("com.android.server.policy.PhoneWindowManager", cl), "systemBooted", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { startSystem((Context) Xp.getObjectField(p.thisObject, "mContext")); } catch (Throwable ignored) { }
            }
        });
    }

    private static synchronized void startSystem(final Context c) {
        if (sStarted || c == null) return;
        sStarted = true;
        HandlerThread t = new HandlerThread("PixelSuiteAod");
        t.start();
        Handler h = new Handler(t.getLooper());
        final ContentResolver cr = c.getContentResolver();
        ContentObserver o = new ContentObserver(h) { @Override public void onChange(boolean self) { readOptions(cr); } };
        for (String k : new String[]{KEY_CHARGING, KEY_AFTER, KEY_SECONDS}) {
            try { cr.registerContentObserver(Settings.Secure.getUriFor(k), false, o); } catch (Throwable ignored) { }
        }
        readOptions(cr);
        IntentFilter f = new IntentFilter(); f.addAction(Intent.ACTION_POWER_CONNECTED); f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try {
            c.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context x, Intent i) { sPlugged = Intent.ACTION_POWER_CONNECTED.equals(i.getAction()); }
            }, f, null, h);
        } catch (Throwable ignored) { }
        sPlugged = pluggedNow(c);
    }

    static void installSystemUi(final ClassLoader cl) {
        Class<?> state = Xp.findClass("com.android.systemui.doze.DozeMachine$State", cl);
        try {
            DOZE = state.getField("DOZE").get(null);
            DOZE_AOD = state.getField("DOZE_AOD").get(null);
            DOZE_AOD_PAUSED = state.getField("DOZE_AOD_PAUSED").get(null);
            DOZE_AOD_PAUSING = state.getField("DOZE_AOD_PAUSING").get(null);
        } catch (Throwable t) { return; }

        Xp.hookAllMethods(Xp.findClass("android.hardware.display.AmbientDisplayConfiguration", cl), "alwaysOnEnabled", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (Boolean.TRUE.equals(p.getResult()) || !wanted() || p.args.length != 1) return;
                    Object c = p.thisObject;
                    boolean available = Boolean.TRUE.equals(Xp.callMethod(c, "alwaysOnAvailable"));
                    boolean inverted = Boolean.TRUE.equals(Xp.callMethod(c, "accessibilityInversionEnabled", p.args[0]));
                    if (available && !inverted) p.setResult(Boolean.TRUE);
                } catch (Throwable ignored) { }
            }
        });

        Xp.hookAllConstructors(Xp.findClass("com.android.systemui.statusbar.phone.DozeParameters", cl), new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                sDoze = new WeakReference<Object>(p.thisObject);
                try { startSystemUi((Context) Xp.getObjectField(Xp.getObjectField(p.thisObject, "mAmbientDisplayConfiguration"), "mContext")); } catch (Throwable ignored) { }
            }
        });

        Class<?> machine = Xp.findClass("com.android.systemui.doze.DozeMachine", cl);
        Xp.hookAllConstructors(machine, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { sMachine = new WeakReference<Object>(p.thisObject); }
        });
        for (Method m : machine.getDeclaredMethods()) {
            if (!"transitionTo".equals(m.getName()) || m.getParameterTypes().length != 2) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { sMachine = new WeakReference<Object>(p.thisObject); onState(Xp.getObjectField(p.thisObject, "mState")); } catch (Throwable ignored) { }
                }
            });
        }

        for (int i = 1; i <= 8; i++) {
            Class<?> k;
            try { k = Class.forName("com.android.systemui.keyguard.KeyguardService$" + i, false, cl); } catch (Throwable t) { continue; }
            Method sleep = null, wake = null;
            for (Method m : k.getDeclaredMethods()) {
                Class<?>[] a = m.getParameterTypes();
                if ("onStartedGoingToSleep".equals(m.getName()) && a.length == 1 && a[0] == int.class) sleep = m;
                if ("onStartedWakingUp".equals(m.getName()) && a.length >= 1 && a[0] == int.class) wake = m;
            }
            if (sleep == null || wake == null) continue;
            Xp.hookMethod(sleep, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    int reason = p.args[0] instanceof Integer ? (Integer) p.args[0] : -1;
                    boolean before = wanted();
                    sAwake = false;
                    sPowerSleep = reason == SLEEP_REASON_POWER_BUTTON;
                    sGeneration++;
                    cancelExpiry();
                    if (before != wanted()) refreshFirst();
                }
            });
            Xp.hookMethod(wake, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    boolean before = wanted();
                    sAwake = true; sPowerSleep = false; sExpired = false;
                    sGeneration++;
                    cancelExpiry();
                    if (before != wanted()) refreshFirst();
                }
            });
            break;
        }
    }

    private static synchronized void startSystemUi(Context c) {
        if (sStarted || c == null) return;
        sStarted = true;
        sMain = new Handler(Looper.getMainLooper());
        final ContentResolver cr = c.getContentResolver();
        readOptions(cr);
        sPlugged = pluggedNow(c);
        ContentObserver o = new ContentObserver(sMain) {
            @Override public void onChange(boolean self) {
                boolean before = wanted(), real = sRealAod;
                readOptions(cr);
                if (before != wanted() || real != sRealAod) apply();
                else if (sAfter) schedule();
            }
        };
        for (String k : new String[]{KEY_CHARGING, KEY_AFTER, KEY_SECONDS, "doze_always_on"}) {
            try { cr.registerContentObserver(Settings.Secure.getUriFor(k), false, o); } catch (Throwable ignored) { }
        }
        IntentFilter f = new IntentFilter(); f.addAction(Intent.ACTION_POWER_CONNECTED); f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try {
            c.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context x, Intent i) {
                    boolean before = wanted();
                    sPlugged = Intent.ACTION_POWER_CONNECTED.equals(i.getAction());
                    if (before != wanted()) apply(); else schedule();
                }
            }, f, null, sMain);
        } catch (Throwable ignored) { }
        sMain.post(new Runnable() { public void run() { refresh(); } });
    }

    private static void refreshFirst() {
        Handler h = sMain;
        if (h == null) return;
        h.postAtFrontOfQueue(new Runnable() { public void run() { refresh(); } });
    }

    private static void refresh() {
        Object d = sDoze.get();
        if (d == null) return;
        try { Xp.callMethod(d, "onTuningChanged", "doze_always_on", sRealAod ? "1" : "0"); } catch (Throwable ignored) { }
    }

    private static void apply() {
        refresh();
        if (sAwake) return;
        Object m = sMachine.get();
        if (m == null) return;
        try {
            Object state = Xp.callMethod(m, "getState");
            boolean on = sRealAod || wanted();
            if (state == DOZE && on) Xp.callMethod(m, "requestState", DOZE_AOD);
            else if ((state == DOZE_AOD || state == DOZE_AOD_PAUSED || state == DOZE_AOD_PAUSING) && !on) Xp.callMethod(m, "requestState", DOZE);
        } catch (Throwable ignored) { }
        schedule();
    }

    private static void onState(Object state) {
        if (state == DOZE_AOD) schedule();
    }

    private static void schedule() {
        Handler h = sMain;
        if (h == null || sAwake || !sPowerSleep || sExpired || !timed() || sExpiry != null) return;
        Object m = sMachine.get();
        if (m == null) return;
        try { if (Xp.getObjectField(m, "mState") != DOZE_AOD) return; } catch (Throwable t) { return; }
        final int gen = sGeneration;
        sExpiry = new Runnable() { public void run() { sExpiry = null; expire(gen); } };
        h.postDelayed(sExpiry, sSeconds * 1000L);
    }

    private static void cancelExpiry() {
        Handler h = sMain;
        Runnable r = sExpiry;
        sExpiry = null;
        if (h != null && r != null) h.removeCallbacks(r);
    }

    private static void expire(int gen) {
        if (gen != sGeneration || sAwake || !timed()) return;
        sExpired = true;
        apply();
    }
}
