package io.github.pixelsuite;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Display;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.database.ContentObserver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class DoubleTapWake {

    static final String KEY_TAP_GESTURE = "doze_tap_gesture";
    static final String KEY_DOUBLE_TAP_GESTURE = "doze_pulse_on_double_tap";

    static final String OLD_TITLE = "Tap to check phone";
    static final String NEW_TITLE = "Tap or Double Tap to check phone";

    private static final String KEY_TAP = "pixelsuite_wake_tap";
    private static final String KEY_DOUBLE = "pixelsuite_wake_double_tap";
    private static final String KEY_MODDED = "pixelsuite_lock_modded";
    private static final String KEY_STOCK = "pixelsuite_lock_stock";
    private static final String KEY_WAKE_GROUP = "pixelsuite_wake_group";
    private static final String KEY_LOCK_GROUP = "pixelsuite_lock_group";
    private static final String CLICK = "androidx.preference.Preference$OnPreferenceClickListener";

    private static final String PWM = "com.android.server.policy.PhoneWindowManager";
    private static final String PMS = "com.android.server.power.PowerManagerService";
    private static final int STATE_ON = 2, STATE_DOZE = 3, STATE_DOZE_SUSPEND = 4;
    private static final String POWER_GROUP = "com.android.server.power.PowerGroup";

    private static final long DOUBLE_GAP_MS = 400L;
    private static final long TAP_MAX_MS = 400L;
    private static final int SLEEP_REASON_POWER_BUTTON = 4;
    private static final int MAX_PAGE_PREFS = 5;

    private static volatile boolean sWakeOn, sSleepOn, sInitDone;
    private static volatile long sLastSingle;
    private static volatile boolean sUdfpsSeen;
    private static volatile int sUdfpsVisibility = -1;
    private static final String UDFPS_WINDOW = "UdfpsControllerOverlay";
    private static final long SINGLE_PAIR_MS = 800L;
    private static final long BACKUP_PAIR_MS = 1000L;
    private static final long BACKUP_WAIT_MS = 150L;
    private static final String BACKUP_DETAILS = "io.github.pixelsuite:DOUBLE_TAP";
    private static final String PULSE_ACTION = "com.android.systemui.doze.pulse";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final long PULSE_DELAY_MS = 150L;
    private static final long WAKE_GUARD_MS = 800L;
    private static final float WAKE_GUARD_TOP = 0.70f;
    private static volatile long sTapWakeAt;
    private static final String ALT_BOUNCER_WINDOW = "AlternateBouncerView";
    private static int sAltWindowId;
    private static volatile int sAltVisibility = -1;
    private static volatile long sAltShownAt;
    private static volatile boolean sLockShortcuts;
    private static volatile boolean sMasterOn = true;
    private static volatile boolean sAodTouch;
    private static volatile boolean sPowerSave, sBatteryLow, sCharging;
    private static volatile int sAodTouchLast = -1;
    private static volatile Object sAodPms;
    private static volatile Method sAodMethod;
    private static volatile Object[] sAodArgs;
    private static volatile long sChipTapAt;
    private static long sHandledChipAt;
    private static long sBackSentFor;
    private static final long CHIP_WINDOW_MS = 1000L;
    private static final long CHIP_PROMPT_MAX_MS = 800L;
    private static final long BACK_DELAY_MS = 80L;
    private static final long LAUNCH_DROP_MS = 1200L;
    private static final int START_CANCELED = -96;
    private static volatile boolean sDropArmed;
    private static volatile long sUnlockedAt;
    private static volatile int sSystemUiUid = -1;
    private static volatile long sTouchDownAt;
    private static volatile long sTouchUpAt;
    private static volatile long sTouchCancelAt;
    private static volatile boolean sTouchTap;
    private static float sTouchX, sTouchY;
    private static volatile int sDownSeq;
    private static final Object sTouchLock = new Object();
    private static final long QUIET_AFTER_UP_MS = 600L;
    private static final long DOWN_WAIT_MS = 60L;
    private static volatile long sFingerAt;
    private static final long FINGER_QUIET_MS = 2000L;
    private static final String VMS = "com.android.server.vibrator.VibratorManagerService";
    private static final int USAGE_CLASS_MASK = 0x0F;
    private static final int USAGE_CLASS_FEEDBACK = 0x02;
    private static final int USAGE_ACCESSIBILITY = 0x42;
    private static final int USAGE_TOUCH = 0x12;
    private static final String SMARTSPACE_STUB =
            "com.android.server.smartspace.SmartspaceManagerService$SmartspaceManagerStub";
    private static final int SMARTSPACE_TAP = 1;
    private static int sTapReason = 15;

    private static Context sCtx;
    private static Object sPwm;
    private static PowerManager sPm;
    private static Handler sWorker;
    private static volatile Object sMonitor;
    private static volatile InputChannel sChannel;
    private static volatile Receiver sReceiver;
    private static int sMonitorFailures;
    private static final int MAX_MONITOR_FAILURES = 3;
    private static int sTouchSlop, sDoubleTapSlop;

    private static Handler sMain;

    private interface Action {
        void run(Object pref, Object value);
    }

    private DoubleTapWake() {}

    static void installSystemUi(ClassLoader cl) { }

    static void installSystem(ClassLoader cl) {
        try {
            sTapReason = PowerManager.class.getField("WAKE_REASON_TAP").getInt(null);
        } catch (Throwable ignored) {
        }

        try {
            Xp.hookAllMethods(Xp.findClass(PWM, cl), "systemBooted", new Xp.Callback() {
                @Override
                protected void afterHookedMethod(Xp.Param param) {
                    try {
                        init(param.thisObject,
                                (Context) Xp.getObjectField(param.thisObject, "mContext"));
                    } catch (Throwable t) {
                        Module.log("double-tap-wake: init failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            Module.log("double-tap-wake: systemBooted hook failed", t);
        }

        Xp.Callback wake = new Xp.Callback() {
            @Override
            protected void beforeHookedMethod(Xp.Param param) {
                try {
                    onWakeRequest(param);
                } catch (Throwable t) {
                    Module.log("double-tap-wake: wake handling failed", t);
                }
            }
        };
        int n = 0;
        try {
            n = Xp.hookAllMethods(Xp.findClass(PMS, cl), "wakePowerGroupLocked", wake).size();
        } catch (Throwable ignored) { }
        if (n == 0) {
            try {
                n = Xp.hookAllMethods(Xp.findClass(POWER_GROUP, cl), "wakeUpLocked", wake).size();
            } catch (Throwable ignored) { }
        }
        Module.log("double-tap-wake: hooked " + n + " wake method(s), tap reason " + sTapReason);
        installWindowWatch(cl);
        installSmartspaceWatch(cl);
        installLaunchGuard(cl);
        installTapQuiet(cl);
        installFingerWatch(cl);
        installAodTouch(cl);
        installShadeGuard(cl);
        installUnlockTouchEnd(cl);
    }

    private static volatile long sEndedTouchAt;

    private static void installUnlockTouchEnd(ClassLoader cl) {
        Xp.Callback cb = new Xp.Callback() {
            @Override
            protected void beforeHookedMethod(Xp.Param param) {
                try {
                    if (!param.thisObject.getClass().getName().contains("fingerprint")) return;
                    for (Object a : param.args) {
                        if (a instanceof Boolean) {
                            if ((Boolean) a) endTouchAtUnlock();
                            return;
                        }
                    }
                } catch (Throwable t) {
                    Module.log("double-tap-wake: unlock touch end failed", t);
                }
            }
        };
        String base = "com.android.server.biometrics.sensors.";
        String[] classes = {
                base + "fingerprint.aidl.FingerprintAuthenticationClient",
                base + "AuthenticationClient",
        };
        int n = 0;
        for (String c : classes) {
            try {
                n += Xp.hookAllMethods(Xp.findClass(c, cl), "onAuthenticated", cb).size();
            } catch (Throwable ignored) { }
        }
        Module.log("double-tap-wake: unlock touch end " + (n > 0 ? "installed (" + n + ")"
                : "unavailable (shade guard only)"));
    }

    private static void endTouchAtUnlock() {
        if (sMonitor == null) return;
        long down = sTouchDownAt, up = sTouchUpAt, cancel = sTouchCancelAt;
        if (down == 0 || up != 0 || cancel >= down) return;
        if (down == sEndedTouchAt) return;
        if (!lockScreenShowing()) return;
        sEndedTouchAt = down;
        pilfer("fingerprint unlock with the finger still on the lock screen window, touch "
                + "ended before unlocking (no shade pull-down)");
    }

    private static final long SHADE_GUARD_MS = 1000L;
    private static final long[] SHADE_CLOSE_AT_MS = { 300L, 700L, 1200L };
    private static volatile boolean sShadeOpen;
    private static volatile long sShadeRevealAt;

    private static void installShadeGuard(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> sbms = Xp.findClass(
                    "com.android.server.statusbar.StatusBarManagerService", cl);
            n = Xp.hookAllMethods(sbms, "onPanelRevealed", new Xp.Callback() {
                @Override
                protected void afterHookedMethod(Xp.Param param) {
                    sShadeOpen = true;
                    sShadeRevealAt = SystemClock.uptimeMillis();
                    maybeCloseShade(param.thisObject);
                }
            }).size();
            Xp.hookAllMethods(sbms, "onPanelHidden", new Xp.Callback() {
                @Override
                protected void afterHookedMethod(Xp.Param param) {
                    sShadeOpen = false;
                }
            });
        } catch (Throwable t) {
            Module.log("double-tap-wake: shade guard failed", t);
        }
        Module.log("double-tap-wake: shade guard " + (n > 0 ? "installed" : "unavailable"));
    }

    private static void maybeCloseShade(final Object statusBarService) {
        long unlocked = sUnlockedAt;
        final long revealAt = sShadeRevealAt;
        if (unlocked == 0 || revealAt - unlocked > SHADE_GUARD_MS) return;
        long down = sTouchDownAt, up = sTouchUpAt;
        if (down == 0 || down > unlocked || (up != 0 && up < unlocked)) return;
        Handler h = sWorker;
        if (h == null) return;
        for (long delay : SHADE_CLOSE_AT_MS) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!sShadeOpen) return;
                    if (sShadeRevealAt != revealAt) return;
                    if (sTouchDownAt > revealAt) return;
                    if (keyguardShowing()) return;
                    try {
                        Xp.callMethod(statusBarService, "collapsePanels");
                        Module.log("double-tap-wake: notification shade opened by the unlocking "
                                + "touch, closing it");
                    } catch (Throwable t) {
                        Module.log("double-tap-wake: could not close the shade", t);
                    }
                }
            }, delay);
        }
    }

    private static void installAodTouch(ClassLoader cl) {
        int n = 0;
        try {
            n = Xp.hookAllMethods(Xp.findClass(PMS, cl), "setDozeOverrideFromDreamManagerInternal",
                    new Xp.Callback() {
                        @Override
                        protected void beforeHookedMethod(Xp.Param param) {
                            if (param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                            if (param.method instanceof Method) {
                                sAodPms = param.thisObject;
                                sAodMethod = (Method) param.method;
                                sAodArgs = param.args.clone();
                            }
                            int state = (Integer) param.args[0];
                            boolean doze = state == STATE_DOZE || state == STATE_DOZE_SUSPEND;
                            if (!doze) return;
                            boolean keep = aodTouchWanted();
                            if (keep) param.args[0] = STATE_ON;
                            int now = keep ? 1 : 0;
                            if (now != sAodTouchLast) {
                                sAodTouchLast = now;
                                Module.log(keep
                                        ? "double-tap-wake: always-on display kept in full touch"
                                                + " mode (state " + state + " -> ON"
                                                + (sCharging ? ", charging" : "") + ")"
                                        : "double-tap-wake: always-on display left in low-power"
                                                + " mode (" + aodTouchWhyNot() + ")");
                            }
                        }
                    }).size();
        } catch (Throwable t) {
            Module.log("double-tap-wake: always-on touch hook failed", t);
        }
        Module.log("double-tap-wake: always-on touch hooked " + n + " method(s)");
    }

    private static boolean aodTouchWanted() {
        if (!sAodTouch || sPowerSave) return false;
        return sCharging || !sBatteryLow;
    }

    private static String aodTouchWhyNot() {
        if (!sAodTouch) return "double tap to wake off";
        if (sPowerSave) return "Battery Saver on";
        if (sBatteryLow) return "battery low";
        return "?";
    }

    private static void initAodPower(Context ctx, Looper looper) {
        try {
            sPowerSave = sPm.isPowerSaveMode();
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            sCharging = bm.isCharging();
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int lowAt = 15;
            try {
                lowAt = ctx.getResources().getInteger(ctx.getResources().getIdentifier(
                        "config_lowBatteryWarningLevel", "integer", "android"));
            } catch (Throwable ignored) { }
            sBatteryLow = level > 0 && level <= lowAt;

            IntentFilter f = new IntentFilter();
            f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            f.addAction(Intent.ACTION_BATTERY_LOW);
            f.addAction(Intent.ACTION_BATTERY_OKAY);
            f.addAction(Intent.ACTION_POWER_CONNECTED);
            f.addAction(Intent.ACTION_POWER_DISCONNECTED);
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    String a = i.getAction();
                    if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(a)) {
                        sPowerSave = sPm.isPowerSaveMode();
                    } else if (Intent.ACTION_BATTERY_LOW.equals(a)) {
                        sBatteryLow = true;
                    } else if (Intent.ACTION_BATTERY_OKAY.equals(a)) {
                        sBatteryLow = false;
                    } else if (Intent.ACTION_POWER_CONNECTED.equals(a)) {
                        sCharging = true;
                    } else if (Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                        sCharging = false;
                    }
                    reapplyAodState();
                }
            };
            registerReceiver(ctx, r, f, new Handler(looper));
            Module.log("double-tap-wake: always-on touch power watch on (saver=" + sPowerSave
                    + " low=" + sBatteryLow + " charging=" + sCharging + ")");
        } catch (Throwable t) {
            Module.log("double-tap-wake: always-on touch power watch unavailable", t);
        }
    }

    private static void reapplyAodState() {
        final Object pms = sAodPms;
        final Method m = sAodMethod;
        final Object[] args = sAodArgs;
        if (pms == null || m == null || args == null || sPm == null) return;
        if (sPm.isInteractive()) return;
        int state = args[0] instanceof Integer ? (Integer) args[0] : -1;
        if (state != STATE_DOZE && state != STATE_DOZE_SUSPEND) return;
        int want = aodTouchWanted() ? 1 : 0;
        if (want == sAodTouchLast) return;
        Handler h = sWorker;
        if (h == null) return;
        h.post(new Runnable() {
            @Override
            public void run() {
                try {
                    m.setAccessible(true);
                    m.invoke(pms, args.clone());
                } catch (Throwable t) {
                    Module.log("double-tap-wake: always-on display re-apply failed", t);
                }
            }
        });
    }

    private static void installLaunchGuard(ClassLoader cl) {
        Xp.Callback unlock = new Xp.Callback() {
            @Override
            protected void beforeHookedMethod(Xp.Param param) {
                for (Object a : param.args) {
                    if (a instanceof Boolean) {
                        if (!(Boolean) a) onUnlocked();
                        return;
                    }
                }
            }
        };
        int u = 0;
        String tms = "com.android.server.trust.TrustManagerService";
        try {
            Class<?> c = Xp.findClass(tms, cl);
            u += Xp.hookAllMethods(c, "setDeviceLockedForUser", unlock).size();
            u += Xp.hookAllMethods(c, "notifyKeystoreOfDeviceLockState", unlock).size();
        } catch (Throwable t) {
            Module.log("double-tap-wake: unlock watch failed", t);
        }
        for (int i = 1; i <= 15; i++) {
            try {
                Class<?> c = Class.forName(tms + "$" + i, false, cl);
                u += Xp.hookAllMethods(c, "setDeviceLockedForUser", unlock).size();
            } catch (Throwable ignored) { }
        }

        int a = 0;
        try {
            a = Xp.hookAllMethods(Xp.findClass("com.android.server.wm.ActivityStarter", cl),
                    "executeRequest", new Xp.Callback() {
                        @Override
                        protected void beforeHookedMethod(Xp.Param param) {
                            if (sDropArmed && param.args.length > 0) maybeDropLaunch(param);
                        }
                    }).size();
        } catch (Throwable t) {
            Module.log("double-tap-wake: launch guard failed", t);
        }
        Module.log("double-tap-wake: launch guard " + (a > 0 && u > 0 ? "installed"
                : "incomplete (starts " + a + ", unlock " + u + ")"));
    }

    private static void installTapQuiet(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = Xp.findClass(VMS, cl);
            String[] methods = {
                    "vibrate", "performHapticFeedback", "performHapticFeedbackForInputDevice"
            };
            for (final String m : methods) {
                n += Xp.hookAllMethods(c, m, new Xp.Callback() {
                    @Override
                    protected void beforeHookedMethod(Xp.Param param) {
                        if (undoingChips()) maybeQuietVibration(param, m);
                    }
                }).size();
            }
        } catch (Throwable t) {
            Module.log("double-tap-wake: tap vibration guard failed", t);
        }
        Module.log("double-tap-wake: tap vibration guard "
                + (n > 0 ? "installed (" + n + ")" : "unavailable"));
    }

    private static void trackQuietTap(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sTouchUpAt = 0;
                sTouchTap = true;
                sTouchX = ev.getX();
                sTouchY = ev.getY();
                sTouchDownAt = ev.getEventTime();
                synchronized (sTouchLock) {
                    sDownSeq++;
                    sTouchLock.notifyAll();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                sTouchTap = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                sTouchTap = false;
                sTouchCancelAt = ev.getEventTime();
                break;
            case MotionEvent.ACTION_MOVE:
                if (sTouchTap && (Math.abs(ev.getX() - sTouchX) > sTouchSlop
                        || Math.abs(ev.getY() - sTouchY) > sTouchSlop)) {
                    sTouchTap = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                sTouchUpAt = ev.getEventTime();
                break;
            default:
                break;
        }
    }

    private static boolean tapActive(long now) {
        long down = sTouchDownAt;
        long up = sTouchUpAt;
        if (!sTouchTap || down == 0) return false;
        if (up == 0) return now - down <= TAP_MAX_MS;
        return up >= down && up - down <= TAP_MAX_MS && now - up <= QUIET_AFTER_UP_MS;
    }

    private static boolean mayAwaitDown(long now) {
        long down = sTouchDownAt, up = sTouchUpAt, cancel = sTouchCancelAt;
        boolean ended = down == 0 || up != 0 || cancel >= down;
        if (!ended) return false;
        long finger = sFingerAt;
        return finger == 0 || now - finger > FINGER_QUIET_MS;
    }

    private static void installFingerWatch(ClassLoader cl) {
        Xp.Callback finger = new Xp.Callback() {
            @Override
            protected void beforeHookedMethod(Xp.Param param) {
                sFingerAt = SystemClock.uptimeMillis();
            }
        };
        String base = "com.android.server.biometrics.sensors.";
        String[][] targets = {
                { base + "fingerprint.aidl.FingerprintProvider", "onPointerDown" },
                { base + "fingerprint.aidl.FingerprintAuthenticationClient", "onAcquired" },
        };
        int n = 0;
        for (String[] t : targets) {
            try {
                n += Xp.hookAllMethods(Xp.findClass(t[0], cl), t[1], finger).size();
            } catch (Throwable ignored) { }
        }
        Module.log("double-tap-wake: fingerprint watch " + (n > 0 ? "installed (" + n + ")"
                : "unavailable (fingerprint haptics may wait up to " + DOWN_WAIT_MS + " ms)"));
    }

    private static boolean awaitDown(int seq, long max) {
        long deadline = SystemClock.uptimeMillis() + max;
        synchronized (sTouchLock) {
            while (sDownSeq == seq) {
                long left = deadline - SystemClock.uptimeMillis();
                if (left <= 0) return false;
                try {
                    sTouchLock.wait(left);
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean quietPlace() {
        return (sUdfpsVisibility == android.view.View.VISIBLE || chipTapRecent())
                && lockScreenShowing();
    }

    private static void maybeQuietVibration(Xp.Param param, String method) {
        int seq = sDownSeq;
        if (!lockScreenShowing()) return;
        boolean haptic = method.startsWith("performHapticFeedback");
        String pkg = null;
        int usage = haptic ? USAGE_TOUCH : 0;
        int constant = -1, ints = 0;
        for (Object a : param.args) {
            if (a instanceof String) {
                if (pkg == null) pkg = (String) a;
            } else if (a instanceof Integer) {
                if (++ints == 3 && haptic) constant = (Integer) a;
            } else if (!haptic && a != null
                    && "android.os.VibrationAttributes".equals(a.getClass().getName())) {
                Object u = Xp.callMethod(a, "getUsage");
                if (u instanceof Integer) usage = (Integer) u;
            }
        }
        boolean touch = usage == 0 || ((usage & USAGE_CLASS_MASK) == USAGE_CLASS_FEEDBACK
                && usage != USAGE_ACCESSIBILITY);
        long now = SystemClock.uptimeMillis();
        if (!touch) {
            if (tapActive(now) && quietPlace()) {
                Module.log("double-tap-wake: lock screen tap, vibration kept ("
                        + describeVibration(pkg, method, constant, usage) + ")");
            }
            return;
        }
        long waited = -1;
        if (!tapActive(now)) {
            if (!SYSTEMUI.equals(pkg) || !mayAwaitDown(now) || !quietPlace()) return;
            if (!awaitDown(seq, DOWN_WAIT_MS)) return;
            long after = SystemClock.uptimeMillis();
            waited = after - now;
            if (!tapActive(after)) return;
        }
        if (!quietPlace()) return;
        param.setResult(null);
        Module.log("double-tap-wake: lock screen tap vibration blocked ("
                + describeVibration(pkg, method, constant, usage)
                + (waited >= 0 ? ", waited " + waited + " ms for the touch" : "") + ")");
    }

    private static String describeVibration(String pkg, String method, int constant, int usage) {
        return pkg + " " + method + (constant >= 0 ? " constant=" + constant : "")
                + " usage=0x" + Integer.toHexString(usage);
    }

    private static void onUnlocked() {
        final long at = SystemClock.uptimeMillis();
        sUnlockedAt = at;
        Handler h = sWorker;
        if (h == null || !sDropArmed) return;
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sDropArmed && sUnlockedAt == at) {
                    sDropArmed = false;
                    Module.log("double-tap-wake: no queued card launch after unlock");
                }
            }
        }, LAUNCH_DROP_MS + 100);
    }

    private static void maybeDropLaunch(Xp.Param param) {
        long unlocked = sUnlockedAt;
        if (unlocked == 0 || SystemClock.uptimeMillis() - unlocked > LAUNCH_DROP_MS) return;
        Object req = param.args[0];
        Object real = Xp.getObjectField(req, "realCallingUid");
        if (!(real instanceof Integer) || (Integer) real != sSystemUiUid || sSystemUiUid < 0) return;
        sDropArmed = false;
        Object intent = null;
        try {
            intent = Xp.getObjectField(req, "intent");
            Object opts = Xp.getObjectField(req, "activityOptions");
            if (opts != null) Xp.callMethod(opts, "abort");
        } catch (Throwable ignored) { }
        param.setResult(START_CANCELED);
        Module.log("double-tap-wake: queued lock screen card launch cancelled ("
                + describeIntent(intent) + ")");
    }

    private static String describeIntent(Object o) {
        if (!(o instanceof android.content.Intent)) return "?";
        android.content.Intent i = (android.content.Intent) o;
        android.content.ComponentName c = i.getComponent();
        return (i.getAction() != null ? i.getAction() : "") + " "
                + (c != null ? c.flattenToShortString() : String.valueOf(i.getPackage()));
    }

    private static void installSmartspaceWatch(ClassLoader cl) {
        int n = 0;
        try {
            n = Xp.hookAllMethods(Xp.findClass(SMARTSPACE_STUB, cl), "notifySmartspaceEvent",
                    new Xp.Callback() {
                        @Override
                        protected void beforeHookedMethod(Xp.Param param) {
                            for (Object a : param.args) {
                                if (a == null || !a.getClass().getName()
                                        .endsWith("SmartspaceTargetEvent")) continue;
                                Object type = Xp.callMethod(a, "getEventType");
                                if (type instanceof Integer && (Integer) type == SMARTSPACE_TAP) {
                                    onChipTap();
                                }
                            }
                        }
                    }).size();
        } catch (Throwable t) {
            Module.log("double-tap-wake: smartspace watch failed", t);
        }
        Module.log("double-tap-wake: smartspace watch " + (n > 0 ? "installed" : "unavailable"));
    }

    private static boolean undoingChips() {
        return sSleepOn && !sLockShortcuts && sReceiver != null;
    }

    private static boolean chipTapRecent() {
        long t = sChipTapAt;
        return t != 0 && SystemClock.uptimeMillis() - t <= CHIP_WINDOW_MS;
    }

    private static void onChipTap() {
        if (!undoingChips()) return;
        if (android.os.Binder.getCallingUid() != sSystemUiUid) return;
        if (!lockScreenShowing()) return;
        long now = SystemClock.uptimeMillis();
        long prev = sChipTapAt;
        if (prev != 0 && now - prev < 300) return;
        sChipTapAt = now;
        if (deviceLocked()) sDropArmed = true;
        Handler h = sWorker;
        if (h == null) return;
        h.post(new Runnable() {
            @Override
            public void run() {
                maybeUndoChipTap();
            }
        });
    }

    private static void maybeUndoChipTap() {
        long tap = sChipTapAt;
        if (tap == 0 || tap == sHandledChipAt || !chipTapRecent()) return;
        if (!undoingChips() || !lockScreenShowing()) {
            sChipTapAt = 0;
            return;
        }
        long shown = sAltShownAt;
        if (shown == 0 || shown < tap - 300 || shown > tap + CHIP_PROMPT_MAX_MS) return;
        sHandledChipAt = tap;
        long wait = Math.max(0, shown + BACK_DELAY_MS - SystemClock.uptimeMillis());
        sWorker.postDelayed(backFor(tap, "lock screen card tap undone"), wait);
    }

    private static Runnable backFor(final long tap, final String why) {
        return new Runnable() {
            @Override
            public void run() {
                if (sBackSentFor == tap) return;
                if (!lockScreenShowing() || sAltVisibility != android.view.View.VISIBLE) return;
                sBackSentFor = tap;
                pressBack();
                sDropArmed = true;
                Module.log("double-tap-wake: " + why);
            }
        };
    }

    private static void checkIconAfterChip() {
        Handler h = sWorker;
        long tap = sChipTapAt;
        if (h == null || tap == 0) return;
        long wait = Math.max(0, tap + CHIP_WINDOW_MS + 50 - SystemClock.uptimeMillis());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Receiver r = sReceiver;
                if (r != null && sUdfpsVisibility != android.view.View.VISIBLE
                        && lockScreenShowing()) {
                    r.disarmFromIcon();
                }
            }
        }, wait);
    }

    private static void installWindowWatch(ClassLoader cl) {
        int n = 0;
        try {
            n = Xp.hookAllMethods(Xp.findClass("com.android.server.wm.WindowState", cl),
                    "setViewVisibility", new Xp.Callback() {
                        @Override
                        protected void afterHookedMethod(Xp.Param param) {
                            if (param.args.length > 0 && param.args[0] instanceof Integer) {
                                onWindowVisibility(param.thisObject, (Integer) param.args[0]);
                            }
                        }
                    }).size();
        } catch (Throwable ignored) { }
        if (n == 0) {
            try {
                n = Xp.hookAllMethods(Xp.findClass("com.android.server.wm.WindowManagerService", cl),
                        "relayoutWindow", new Xp.Callback() {
                            @Override
                            protected void afterHookedMethod(Xp.Param param) {
                                relayoutFallback(param);
                            }
                        }).size();
                if (n > 0) Module.log("double-tap-wake: watching windows via relayoutWindow");
            } catch (Throwable ignored) { }
        }
        Module.log("double-tap-wake: window watch " + (n > 0 ? "installed" : "unavailable"));
    }

    private static void relayoutFallback(Xp.Param param) {
        try {
            if (param.args.length < 2 || !(param.args[1] instanceof android.os.IInterface)) return;
            int ints = 0;
            int vis = -1;
            for (Object a : param.args) {
                if (a instanceof Integer && ++ints == 3) {
                    vis = (Integer) a;
                    break;
                }
            }
            if (vis < 0) return;
            Object map = Xp.getObjectField(param.thisObject, "mWindowMap");
            if (!(map instanceof java.util.Map)) return;
            Object ws = ((java.util.Map<?, ?>) map).get(
                    ((android.os.IInterface) param.args[1]).asBinder());
            if (ws != null) onWindowVisibility(ws, vis);
        } catch (Throwable ignored) { }
    }

    private static void onWindowVisibility(Object windowState, int visibility) {
        try {
            Object attrs = attrsOf(windowState);
            if (!(attrs instanceof android.view.WindowManager.LayoutParams)) return;
            CharSequence title = ((android.view.WindowManager.LayoutParams) attrs).getTitle();
            if (title == null) return;
            if (title.toString().contains(ALT_BOUNCER_WINDOW)) {
                int id = System.identityHashCode(windowState);
                if (id != sAltWindowId || visibility != sAltVisibility) {
                    sAltWindowId = id;
                    sAltVisibility = visibility;
                    if (visibility == android.view.View.VISIBLE) {
                        sAltShownAt = SystemClock.uptimeMillis();
                        Handler h = sWorker;
                        if (h != null) {
                            h.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (sDropArmed && !chipTapRecent()) sDropArmed = false;
                                    maybeUndoChipTap();
                                }
                            });
                        }
                    }
                }
                return;
            }
            if (!title.toString().contains(UDFPS_WINDOW)) return;
            sUdfpsSeen = true;
            if (visibility == sUdfpsVisibility) return;
            sUdfpsVisibility = visibility;
            final boolean visible = visibility == android.view.View.VISIBLE;
            Handler h = sWorker;
            if (h == null) return;
            h.post(new Runnable() {
                @Override
                public void run() {
                    if (visible) ensureMonitor("lock screen shown");
                    Receiver r = sReceiver;
                    if (r != null) r.onFingerprintIcon(visible);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static volatile java.lang.reflect.Field sAttrsField;

    private static Object attrsOf(Object windowState) throws Exception {
        java.lang.reflect.Field f = sAttrsField;
        if (f == null || !f.getDeclaringClass().isInstance(windowState)) {
            f = null;
            for (Class<?> c = windowState.getClass(); c != null && f == null;
                    c = c.getSuperclass()) {
                try {
                    f = c.getDeclaredField("mAttrs");
                } catch (NoSuchFieldException ignored) { }
            }
            if (f == null) return null;
            f.setAccessible(true);
            sAttrsField = f;
        }
        return f.get(windowState);
    }

    private static synchronized void init(Object pwm, Context ctx) {
        if (sInitDone || ctx == null) return;
        sPwm = pwm;
        sCtx = ctx;
        sPm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        try {
            sSystemUiUid = ctx.getPackageManager()
                    .getApplicationInfo("com.android.systemui", 0).uid;
        } catch (Throwable t) {
            Module.log("double-tap-wake: System UI uid unknown", t);
        }
        HandlerThread t = new HandlerThread("PixelSuiteDoubleTap");
        t.start();
        sWorker = new Handler(t.getLooper());

        ViewConfiguration vc = ViewConfiguration.get(ctx);
        sTouchSlop = vc.getScaledTouchSlop();
        sDoubleTapSlop = vc.getScaledDoubleTapSlop();

        readFlags();
        ContentObserver obs = new ContentObserver(sWorker) {
            @Override
            public void onChange(boolean selfChange) {
                readFlags();
                repairTapSensor();
                updateMonitorForFlags();
                Module.log("double-tap-wake: on=" + sMasterOn + " doubleTapWake=" + sWakeOn
                        + " lockScreen=" + (sLockShortcuts ? "stock" : "modded"));
            }
        };
        ContentResolver cr = ctx.getContentResolver();
        cr.registerContentObserver(Settings.Secure.getUriFor(Feature.TAP_CHECK), false, obs);
        cr.registerContentObserver(Settings.Secure.getUriFor(Feature.DOUBLE_TAP_WAKE), false, obs);
        cr.registerContentObserver(Settings.Secure.getUriFor(Feature.LOCK_SHORTCUTS), false, obs);
        initAodPower(ctx, t.getLooper());

        sWorker.post(new Runnable() {
            @Override
            public void run() {
                repairTapSensor();
            }
        });

        IntentFilter off = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(ctx, new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                ensureMonitor("screen off");
            }
        }, off, sWorker);

        sInitDone = true;
        sWorker.post(new Runnable() {
            @Override
            public void run() {
                ensureMonitor("boot");
                Module.log("double-tap-wake: initialised, on=" + sMasterOn + " doubleTapWake="
                        + sWakeOn + " lockScreen=" + (sLockShortcuts ? "stock" : "modded")
                        + " monitor=" + (sReceiver != null));
            }
        });
    }

    private static boolean monitorWanted() {
        return sSleepOn || sWakeOn;
    }

    private static void ensureMonitor(String why) {
        if (sReceiver != null || sCtx == null || sWorker == null || !monitorWanted()) return;
        if (sMonitorFailures >= MAX_MONITOR_FAILURES) return;
        Object monitor = null;
        try {
            Object im = sCtx.getSystemService(Context.INPUT_SERVICE);
            monitor = Xp.callMethod(im, "monitorGestureInput",
                    "PixelSuiteLockTap", Display.DEFAULT_DISPLAY);
            InputChannel channel = (InputChannel) Xp.callMethod(monitor, "getInputChannel");
            Receiver r = new Receiver(channel, sWorker.getLooper());
            sMonitor = monitor;
            sChannel = channel;
            sReceiver = r;
            Module.log("double-tap-wake: lock screen touch monitor on (" + why + ")");
        } catch (Throwable e) {
            sMonitorFailures++;
            if (monitor != null) {
                try {
                    Xp.callMethod(monitor, "dispose");
                } catch (Throwable ignored) { }
            }
            Module.log("double-tap-wake: touch monitor unavailable", e);
        }
    }

    private static void releaseMonitor(String why) {
        Receiver r = sReceiver;
        Object monitor = sMonitor;
        if (r == null && monitor == null) return;
        sReceiver = null;
        sMonitor = null;
        sChannel = null;
        sTouchCancelAt = SystemClock.uptimeMillis();
        try {
            if (r != null) r.dispose();
        } catch (Throwable ignored) { }
        try {
            if (monitor != null) Xp.callMethod(monitor, "dispose");
        } catch (Throwable t) {
            Module.log("double-tap-wake: monitor dispose failed", t);
        }
        Module.log("double-tap-wake: lock screen touch monitor off (" + why + ")");
    }

    private static void updateMonitorForFlags() {
        if (!monitorWanted()) {
            releaseMonitor("switched off");
        } else if (sPm != null && (!sPm.isInteractive() || keyguardShowing())) {
            ensureMonitor("switched on");
        }
    }

    private static boolean keyguardShowing() {
        try {
            return !Boolean.FALSE.equals(Xp.callMethod(sPwm, "isKeyguardShowing"));
        } catch (Throwable t) {
            return true;
        }
    }

    private static void registerReceiver(Context ctx, BroadcastReceiver r, IntentFilter f,
                                         Handler handler) {
        try {
            try {
                Context.class.getMethod("registerReceiver", BroadcastReceiver.class,
                        IntentFilter.class, String.class, Handler.class, int.class)
                        .invoke(ctx, r, f, null, handler, 4);
            } catch (NoSuchMethodException e) {
                ctx.registerReceiver(r, f, null, handler);
            }
        } catch (Throwable t) {
            Module.log("double-tap-wake: receiver registration failed", t);
        }
    }

    private static void readFlags() {
        sMasterOn = Feature.on(sCtx, Feature.TAP_CHECK);
        sWakeOn = sMasterOn && Feature.on(sCtx, Feature.DOUBLE_TAP_WAKE);
        sSleepOn = sMasterOn;
        sLockShortcuts = Feature.on(sCtx, Feature.LOCK_SHORTCUTS);
        sAodTouch = sWakeOn;
        reapplyAodState();
    }

    private static void repairTapSensor() {
        try {
            ContentResolver r = sCtx.getContentResolver();
            if (sWakeOn && Settings.Secure.getInt(r, KEY_TAP_GESTURE, 1) == 0) {
                Settings.Secure.putInt(r, KEY_TAP_GESTURE, 1);
                Settings.Secure.putInt(r, KEY_DOUBLE_TAP_GESTURE, 1);
                Module.log("double-tap-wake: tap sensor switched back on");
            }
        } catch (Throwable e) {
            Module.log("double-tap-wake: settings check failed", e);
        }
    }

    private static void onWakeRequest(Xp.Param param) {
        Handler h = sWorker;
        if (h != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    ensureMonitor("wake");
                    Receiver r = sReceiver;
                    if (r != null) r.rearm();
                }
            });
        }
        int reason = -1;
        String details = null;
        for (Object a : param.args) {
            if (reason == -1 && a instanceof Integer) reason = (Integer) a;
            if (details == null && a instanceof String) details = (String) a;
        }
        if (reason != sTapReason || !sWakeOn) return;
        long now = SystemClock.uptimeMillis();
        if (isSingleTap(details)) {
            if (sLastSingle != 0 && now - sLastSingle <= SINGLE_PAIR_MS) {
                sLastSingle = 0;
                sTapWakeAt = now;
                Module.log("double-tap-wake: second tap, waking (" + details + ")");
            } else {
                sLastSingle = now;
                param.setResult(null);
                Module.log("double-tap-wake: single tap ignored (" + details + ")");
                if (isGestureEngineTap(details)) requestPulse();
            }
        } else {
            sLastSingle = 0;
            sTapWakeAt = now;
            Module.log("double-tap-wake: waking (" + details + ")");
        }
    }

    static boolean isSingleTap(String details) {
        if (details == null) return false;
        if (details.contains("SINGLE_TAP")) return true;
        return details.endsWith(" tap");
    }

    static boolean isGestureEngineTap(String details) {
        return details != null && details.contains("NODOZE");
    }

    private static void requestPulse() {
        Handler h = sWorker;
        final Context ctx = sCtx;
        if (h == null || ctx == null) return;
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(PULSE_ACTION).setPackage(SYSTEMUI);
                    UserHandle all = (UserHandle) UserHandle.class.getField("ALL").get(null);
                    ctx.sendBroadcastAsUser(i, all);
                    Module.log("double-tap-wake: pulse requested to re-arm taps");
                } catch (Throwable t) {
                    Module.log("double-tap-wake: pulse request failed", t);
                }
            }
        }, PULSE_DELAY_MS);
    }

    private static void onDozeDoubleTap() {
        final long single = sLastSingle;
        long now = SystemClock.uptimeMillis();
        if (single == 0 || now - single > BACKUP_PAIR_MS) return;
        Handler h = sWorker;
        if (h == null) return;
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sPm == null || sPm.isInteractive() || sLastSingle != single) return;
                Module.log("double-tap-wake: System UI missed the second tap (lock screen was "
                        + "still coming up), waking from Pixel Suite's own double tap");
                wakeForTap();
            }
        }, BACKUP_WAIT_MS);
    }

    private static void wakeForTap() {
        try {
            PowerManager.class.getMethod("wakeUp", long.class, int.class, String.class)
                    .invoke(sPm, SystemClock.uptimeMillis(), sTapReason, BACKUP_DETAILS);
        } catch (Throwable t) {
            Module.log("double-tap-wake: backup wake failed", t);
        }
    }

    private static final class Receiver extends InputEventReceiver {
        private boolean mArmed = true;
        private boolean mIgnore, mTap;
        private boolean mPilfered;
        private long mDownAt, mPrevUpAt;
        private float mDownX, mDownY, mPrevX, mPrevY;

        Receiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        private boolean mDzActive, mDzTap;
        private long mDzDownAt, mDzPrevUpAt;
        private float mDzX, mDzY, mDzPrevX, mDzPrevY;

        private void onDozeTouch(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDzActive = sPm != null && !sPm.isInteractive();
                    if (!mDzActive) {
                        mDzPrevUpAt = 0;
                        return;
                    }
                    mDzTap = true;
                    mDzDownAt = ev.getEventTime();
                    mDzX = ev.getX();
                    mDzY = ev.getY();
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    mDzTap = false;
                    mDzPrevUpAt = 0;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mDzActive && mDzTap && (Math.abs(ev.getX() - mDzX) > sTouchSlop
                            || Math.abs(ev.getY() - mDzY) > sTouchSlop)) {
                        mDzTap = false;
                        mDzPrevUpAt = 0;
                    }
                    break;
                case MotionEvent.ACTION_UP: {
                    if (!mDzActive || !mDzTap) return;
                    long up = ev.getEventTime();
                    if (up - mDzDownAt > TAP_MAX_MS) {
                        mDzPrevUpAt = 0;
                        return;
                    }
                    float dx = mDzX - mDzPrevX, dy = mDzY - mDzPrevY;
                    boolean close = dx * dx + dy * dy <= (float) sDoubleTapSlop * sDoubleTapSlop;
                    if (mDzPrevUpAt != 0 && close && mDzDownAt - mDzPrevUpAt <= DOUBLE_GAP_MS) {
                        mDzPrevUpAt = 0;
                        onDozeDoubleTap();
                    } else {
                        mDzPrevUpAt = up;
                        mDzPrevX = mDzX;
                        mDzPrevY = mDzY;
                    }
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public void onInputEvent(InputEvent event) {
            try {
                if (event instanceof MotionEvent) {
                    trackQuietTap((MotionEvent) event);
                    if (sWakeOn) onDozeTouch((MotionEvent) event);
                    if (sSleepOn || sWakeOn) onTouch((MotionEvent) event);
                }
            } catch (Throwable t) {
                Module.log("double-tap-wake: touch handling failed", t);
            } finally {
                finishInputEvent(event, false);
            }
        }

        void rearm() {
            mArmed = true;
            mPrevUpAt = 0;
            sChipTapAt = 0;
        }

        void onFingerprintIcon(boolean visible) {
            if (!sSleepOn) return;
            boolean chip = undoingChips() && chipTapRecent();
            if (visible) {
                if (lockScreenShowing()) {
                    if (chip) {
                        mArmed = true;
                        return;
                    }
                    if (!mArmed) Module.log("double-tap-wake: back at the lock screen, double tap on");
                    rearm();
                }
            } else if (!chip) {
                disarm("PIN pad or fingerprint prompt open");
            } else {
                checkIconAfterChip();
            }
        }

        private boolean offWithoutIcon() {
            return !mPilfered && !sUdfpsSeen;
        }

        void disarmFromIcon() {
            disarm("PIN pad or fingerprint prompt open");
        }

        private void disarm(String why) {
            if (mArmed) Module.log("double-tap-wake: lock screen double tap off until next wake ("
                    + why + ")");
            mArmed = false;
            mPrevUpAt = 0;
        }

        private void onTouch(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    boolean screenOn = sPm != null && sPm.isInteractive();
                    boolean lock = lockScreenShowing();
                    mIgnore = !screenOn || !lock;
                    mTap = false;
                    mPilfered = false;
                    if (!mIgnore) mPilfered = cancelIfLeftoverAfterTapWake(ev);
                    if (!sSleepOn) mIgnore = true;
                    if (screenOn && !lock) {
                        mArmed = true;
                        mPrevUpAt = 0;
                        if (!keyguardShowing()) {
                            final Receiver self = this;
                            sWorker.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (sReceiver == self) releaseMonitor("unlocked");
                                }
                            });
                        }
                    }
                    if (mIgnore) return;
                    long down = ev.getEventTime();
                    float px = ev.getX() - mPrevX, py = ev.getY() - mPrevY;
                    if (mPrevUpAt != 0 && down - mPrevUpAt <= DOUBLE_GAP_MS
                            && px * px + py * py <= (float) sDoubleTapSlop * sDoubleTapSlop
                            && undoingChips() && chipTapRecent()) {
                        if (pilfer("second tap kept off the card prompt")) mPilfered = true;
                    }
                    if (mPrevUpAt != 0 && down - mPrevUpAt > DOUBLE_GAP_MS) {
                        if (sUdfpsSeen) {
                            mPrevUpAt = 0;
                        } else {
                            disarm("single tap");
                        }
                    }
                    mDownAt = down;
                    mDownX = ev.getX();
                    mDownY = ev.getY();
                    mTap = true;
                    break;
                }
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (!mIgnore) {
                        mTap = false;
                        mPrevUpAt = 0;
                        if (offWithoutIcon()) disarm("two fingers");
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!mIgnore && mTap && (Math.abs(ev.getX() - mDownX) > sTouchSlop
                            || Math.abs(ev.getY() - mDownY) > sTouchSlop)) {
                        mTap = false;
                        mPrevUpAt = 0;
                        if (offWithoutIcon()) disarm("swipe");
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mTap = false;
                    mPrevUpAt = 0;
                    break;
                case MotionEvent.ACTION_UP: {
                    if (mIgnore || !mTap) return;
                    long up = ev.getEventTime();
                    if (up - mDownAt > TAP_MAX_MS) {
                        mPrevUpAt = 0;
                        if (offWithoutIcon()) disarm("long press");
                        return;
                    }
                    float dx = mDownX - mPrevX, dy = mDownY - mPrevY;
                    boolean close = dx * dx + dy * dy <= (float) sDoubleTapSlop * sDoubleTapSlop;
                    if (mPrevUpAt != 0 && close && mDownAt - mPrevUpAt <= DOUBLE_GAP_MS) {
                        mPrevUpAt = 0;
                        if (mArmed) {
                            onLockDoubleTap();
                        } else {
                            Module.log("double-tap-wake: lock screen double tap ignored "
                                    + "(PIN pad, prompt or shade was open)");
                        }
                    } else {
                        mPrevUpAt = up;
                        mPrevX = mDownX;
                        mPrevY = mDownY;
                    }
                    break;
                }
            }
        }
    }

    private static boolean cancelIfLeftoverAfterTapWake(MotionEvent ev) {
        long wakeAt = sTapWakeAt;
        if (wakeAt == 0) return false;
        long since = ev.getEventTime() - wakeAt;
        if (since < 0 || since > WAKE_GUARD_MS) return false;
        int height = screenHeight();
        if (height > 0 && ev.getY() > height * WAKE_GUARD_TOP) return false;
        return pilfer("leftover tap " + since + " ms after tap wake cancelled");
    }

    private static boolean pilfer(String why) {
        Object monitor = sMonitor;
        if (monitor == null) return false;
        try {
            Xp.callMethod(monitor, "pilferPointers");
            Module.log("double-tap-wake: " + why);
            return true;
        } catch (Throwable t) {
            Module.log("double-tap-wake: could not cancel touch (" + why + ")", t);
            return false;
        }
    }

    private static void pressBack() {
        try {
            Object im = sCtx.getSystemService(Context.INPUT_SERVICE);
            long now = SystemClock.uptimeMillis();
            int[] actions = { KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP };
            for (int action : actions) {
                KeyEvent ev = new KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0, 0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                        KeyEvent.FLAG_FROM_SYSTEM,
                        InputDevice.SOURCE_KEYBOARD);
                Xp.callMethod(im, "injectInputEvent", ev, 0 );
            }
            Module.log("double-tap-wake: fingerprint prompt closed, queued app cancelled");
        } catch (Throwable t) {
            Module.log("double-tap-wake: could not close the fingerprint prompt", t);
        }
    }

    private static int sScreenHeight;

    private static int screenHeight() {
        if (sScreenHeight > 0) return sScreenHeight;
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                    sCtx.getSystemService(Context.WINDOW_SERVICE);
            android.graphics.Point p = new android.graphics.Point();
            wm.getDefaultDisplay().getRealSize(p);
            sScreenHeight = Math.max(p.x, p.y);
        } catch (Throwable t) {
            sScreenHeight = sCtx.getResources().getDisplayMetrics().heightPixels;
        }
        return sScreenHeight;
    }

    private static void onLockDoubleTap() {
        boolean screenOn = sPm != null && sPm.isInteractive();
        boolean lockShowing = lockScreenShowing();
        Module.log("double-tap-wake: lock screen double tap, screenOn=" + screenOn
                + " lockScreen=" + lockShowing);
        if (!screenOn || !lockShowing) return;
        long tap = sChipTapAt;
        Handler h = sWorker;
        if (h != null && undoingChips() && chipTapRecent() && sBackSentFor != tap) {
            long shown = sAltShownAt;
            long now = SystemClock.uptimeMillis();
            long wait = (shown != 0 && shown >= tap - 300)
                    ? Math.max(0, shown + BACK_DELAY_MS - now) : BACK_DELAY_MS;
            h.postDelayed(backFor(tap, "card prompt closed before sleeping"), wait);
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    goToSleep();
                }
            }, wait + BACK_DELAY_MS);
            return;
        }
        goToSleep();
    }

    private static void goToSleep() {
        sChipTapAt = 0;
        try {
            PowerManager.class.getMethod("goToSleep", long.class, int.class, int.class)
                    .invoke(sPm, SystemClock.uptimeMillis(), SLEEP_REASON_POWER_BUTTON, 0);
            Module.log("double-tap-wake: screen off");
        } catch (Throwable t) {
            Module.log("double-tap-wake: goToSleep failed", t);
        }
    }

    private static boolean deviceLocked() {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager)
                    sCtx.getSystemService(Context.KEYGUARD_SERVICE);
            return km == null || km.isDeviceLocked();
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean lockScreenShowing() {
        try {
            Object r = Xp.callMethod(sPwm, "isKeyguardShowingAndNotOccluded");
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable ignored) { }
        try {
            boolean showing = Boolean.TRUE.equals(Xp.callMethod(sPwm, "isKeyguardShowing"));
            boolean occluded = Boolean.TRUE.equals(Xp.callMethod(sPwm, "isKeyguardOccluded"));
            return showing && !occluded;
        } catch (Throwable t) {
            return false;
        }
    }

    static void installSettings(ClassLoader cl) {
        Xp.Callback rename = new Xp.Callback() {
            @Override
            protected void afterHookedMethod(Xp.Param param) {
                Object r = param.getResult();
                if (r instanceof CharSequence) {
                    CharSequence s = (CharSequence) r;
                    if (s.length() == OLD_TITLE.length() && OLD_TITLE.contentEquals(s)
                            && masterOnInSettings()) {
                        param.setResult(NEW_TITLE);
                    }
                }
            }
        };
        hookQuiet(cl, "android.content.res.Resources", "getText", rename);
        hookQuiet(cl, "android.content.res.TypedArray", "getText", rename);
        hookQuiet(cl, "android.content.res.TypedArray", "getString", rename);

        try {
            final Class<?> prefFragment =
                    Xp.findClass("androidx.preference.PreferenceFragmentCompat", cl);
            Class<?> fragment = Xp.findClass("androidx.fragment.app.Fragment", cl);
            int n = Xp.hookAllMethods(fragment, "onResume", new Xp.Callback() {
                @Override
                protected void afterHookedMethod(Xp.Param param) {
                    if (prefFragment.isInstance(param.thisObject)) schedule(param.thisObject);
                }
            }).size();
            Module.log("double-tap-wake: settings hooks installed (" + n + ")");
        } catch (Throwable t) {
            Module.log("double-tap-wake: settings page hook failed", t);
        }
    }

    private static final Feature.Cached sMasterCached = new Feature.Cached(Feature.TAP_CHECK);

    private static boolean masterOnInSettings() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return !(app instanceof Context) || sMasterCached.on((Context) app);
        } catch (Throwable t) {
            return true;
        }
    }

    private static void writeReal(ContentResolver cr, String key, int value) {
        Settings.Secure.putInt(cr, key, value);
    }

    private static void hookQuiet(ClassLoader cl, String cls, String method, Xp.Callback cb) {
        try {
            Xp.hookAllMethods(Xp.findClass(cls, cl), method, cb);
        } catch (Throwable t) {
            Module.log("double-tap-wake: rename hook skipped for " + cls + "." + method, t);
        }
    }

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    private static void schedule(final Object fragment) {
        main().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Object screen = Xp.callMethod(fragment, "getPreferenceScreen");
                    if (screen != null) scan(screen, screen);
                } catch (Throwable t) {
                    Module.log("double-tap-wake: page scan failed", t);
                }
            }
        }, 200L);
    }

    private static boolean scan(Object root, Object group) throws Exception {
        int n = (Integer) Xp.callMethod(group, "getPreferenceCount");
        for (int i = 0; i < n; i++) {
            Object p = Xp.callMethod(group, "getPreference", i);
            if (p == null) continue;
            if (isTapCheckSwitch(p)) {
                int total = (Integer) Xp.callMethod(root, "getPreferenceCount");
                if (total <= MAX_PAGE_PREFS || find(group, KEY_TAP) != null) {
                    inject(group, p);
                } else {
                    Module.log("double-tap-wake: skipped a page with " + total + " items");
                }
                return true;
            }
            if (has(p, "getPreferenceCount") && scan(root, p)) return true;
        }
        return false;
    }

    private static boolean isTapCheckSwitch(Object p) {
        Object t = Xp.callMethod(p, "getTitle");
        if (t == null) return false;
        String s = t.toString();
        if (!OLD_TITLE.equals(s) && !NEW_TITLE.equals(s)) return false;
        if (!has(p, "setChecked", boolean.class)) return false;
        return Xp.callMethod(p, "getFragment") == null;
    }

    private static void inject(final Object group, final Object mainSwitch) throws Exception {
        final Context ctx = (Context) Xp.callMethod(mainSwitch, "getContext");
        if (find(group, KEY_TAP) == null) {
            ClassLoader loader = mainSwitch.getClass().getClassLoader();
            Class<?> radio = radioClass(loader);
            Object tap = make(radio, ctx, KEY_TAP, "Tap to wake",
                    "Tap the screen once to wake it.");
            Object dbl = make(radio, ctx, KEY_DOUBLE, "Double tap to wake",
                    "Tap the screen twice to wake it. A single tap does nothing.\n\n"
                            + "On the Always On Display the touchscreen stays fully awake, so "
                            + "the first double tap always works. This uses a little extra "
                            + "battery while the Always On Display is showing (none while "
                            + "charging), and pauses itself in a pocket, with Battery Saver on, "
                            + "or when the battery is low.");
            Object modded = make(radio, ctx, KEY_MODDED, "Modded behavior (recommended)",
                    "Double-tap anywhere on the lock screen to turn the screen off, even on the "
                            + "alarm, weather or date under the clock: they act like empty "
                            + "space, so a double tap never opens them by mistake. Taps on the "
                            + "lock screen don't vibrate.");
            Object stock = make(radio, ctx, KEY_STOCK, "Stock behavior",
                    "Double-tap the lock screen to turn the screen off, and keep the lock screen "
                            + "as Google made it: the alarm, weather and date under the clock "
                            + "open their apps, and taps vibrate.\n\n"
                            + "Trade-off: a double tap that lands on one of them can open it "
                            + "instead of turning the screen off.");

            Object radioClick = listener(loader, CLICK, "onPreferenceClick", new Action() {
                @Override
                public void run(Object pref, Object value) {
                    String key = String.valueOf(Xp.callMethod(pref, "getKey"));
                    ContentResolver cr = ctx.getContentResolver();
                    if (KEY_TAP.equals(key) || KEY_DOUBLE.equals(key)) {
                        writeReal(cr, Feature.DOUBLE_TAP_WAKE, KEY_DOUBLE.equals(key) ? 1 : 0);
                        writeReal(cr, KEY_TAP_GESTURE, 1);
                        Xp.callMethod(mainSwitch, "setChecked", true);
                    } else {
                        writeReal(cr, Feature.LOCK_SHORTCUTS, KEY_STOCK.equals(key) ? 1 : 0);
                    }
                    refresh(group, ctx);
                }
            });
            for (Object p : new Object[]{tap, dbl, modded, stock}) {
                Xp.callMethod(p, "setOnPreferenceClickListener", radioClick);
            }

            int order = (Integer) Xp.callMethod(mainSwitch, "getOrder");
            boolean ordered = order < Integer.MAX_VALUE - 10;
            Class<?> category = categoryClass(loader);
            if (category != null) {
                Object wake = make(category, ctx, KEY_WAKE_GROUP, "Wake the screen", null);
                Object lock = make(category, ctx, KEY_LOCK_GROUP,
                        "Double tap lock screen to sleep", null);
                if (ordered) {
                    Xp.callMethod(wake, "setOrder", order + 1);
                    Xp.callMethod(lock, "setOrder", order + 2);
                }
                Xp.callMethod(group, "addPreference", wake);
                Xp.callMethod(group, "addPreference", lock);
                addInOrder(wake, tap, dbl);
                addInOrder(lock, modded, stock);
            } else {
                if (ordered) {
                    Xp.callMethod(tap, "setOrder", order + 1);
                    Xp.callMethod(dbl, "setOrder", order + 2);
                    Xp.callMethod(modded, "setOrder", order + 3);
                    Xp.callMethod(stock, "setOrder", order + 4);
                }
                for (Object p : new Object[]{tap, dbl, modded, stock}) {
                    Xp.callMethod(group, "addPreference", p);
                }
            }
            Module.log("double-tap-wake: options added (" + radio.getSimpleName()
                    + (category != null ? ", grouped" : "") + ")");
        }
        refresh(group, ctx);
    }

    private static void addInOrder(Object category, Object... prefs) {
        for (int i = 0; i < prefs.length; i++) {
            Xp.callMethod(prefs[i], "setOrder", i);
            Xp.callMethod(category, "addPreference", prefs[i]);
        }
    }

    private static Class<?> categoryClass(ClassLoader loader) {
        try {
            return Class.forName("androidx.preference.PreferenceCategory", false, loader);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object make(Class<?> c, Context ctx, String key, String title, String summary) {
        Object p = Xp.newInstance(c, ctx);
        Xp.callMethod(p, "setKey", key);
        Xp.callMethod(p, "setTitle", (CharSequence) title);
        if (summary != null) Xp.callMethod(p, "setSummary", (CharSequence) summary);
        Xp.callMethod(p, "setPersistent", false);
        return p;
    }

    private static void refresh(Object group, Context ctx) {
        boolean master = Feature.on(ctx, Feature.TAP_CHECK);
        boolean d = Feature.on(ctx, Feature.DOUBLE_TAP_WAKE);
        boolean stock = Feature.on(ctx, Feature.LOCK_SHORTCUTS);
        Object tap = find(group, KEY_TAP), dbl = find(group, KEY_DOUBLE);
        Object mod = find(group, KEY_MODDED), stk = find(group, KEY_STOCK);
        check(tap, !d);
        check(dbl, d);
        check(mod, !stock);
        check(stk, stock);
        for (Object p : new Object[]{find(group, KEY_WAKE_GROUP), find(group, KEY_LOCK_GROUP),
                tap, dbl, mod, stk}) {
            visible(p, master);
        }
    }

    private static void visible(Object pref, boolean on) {
        if (pref != null && has(pref, "setVisible", boolean.class)) {
            Xp.callMethod(pref, "setVisible", on);
        }
    }

    private static Object find(Object group, String key) {
        return Xp.callMethod(group, "findPreference", (CharSequence) key);
    }

    private static void check(Object pref, boolean on) {
        if (pref != null) Xp.callMethod(pref, "setChecked", on);
    }

    private static final java.util.concurrent.ConcurrentHashMap<Class<?>,
            java.util.concurrent.ConcurrentHashMap<String, Boolean>> sHas =
            new java.util.concurrent.ConcurrentHashMap<Class<?>,
                    java.util.concurrent.ConcurrentHashMap<String, Boolean>>();

    private static boolean has(Object o, String name, Class<?>... types) {
        Class<?> c = o.getClass();
        java.util.concurrent.ConcurrentHashMap<String, Boolean> byName = sHas.get(c);
        if (byName == null) {
            byName = new java.util.concurrent.ConcurrentHashMap<String, Boolean>();
            sHas.put(c, byName);
        }
        String sig = types.length == 0 ? name : name + java.util.Arrays.toString(types);
        Boolean hit = byName.get(sig);
        if (hit == null) {
            boolean found;
            try {
                c.getMethod(name, types);
                found = true;
            } catch (Throwable t) {
                found = false;
            }
            hit = found;
            byName.put(sig, hit);
        }
        return hit;
    }

    private static Class<?> radioClass(ClassLoader loader) throws ClassNotFoundException {
        String[] names = {
                "com.android.settingslib.widget.SelectorWithWidgetPreference",
                "com.android.settingslib.widget.RadioButtonPreference"
        };
        for (String n : names) {
            try {
                return Class.forName(n, false, loader);
            } catch (ClassNotFoundException ignored) { }
        }
        return Class.forName("androidx.preference.CheckBoxPreference", false, loader);
    }

    private static Object listener(ClassLoader loader, String iface, final String method,
                                   final Action action) throws ClassNotFoundException {
        Class<?> type = Class.forName(iface, false, loader);
        return Proxy.newProxyInstance(loader, new Class<?>[]{type}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method m, Object[] a) {
                String name = m.getName();
                if (method.equals(name)) {
                    try {
                        action.run(a != null && a.length > 0 ? a[0] : null,
                                a != null && a.length > 1 ? a[1] : null);
                    } catch (Throwable t) {
                        Module.log("double-tap-wake: saving failed", t);
                    }
                    return Boolean.TRUE;
                }
                if ("equals".equals(name)) return proxy == a[0];
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                return "PixelSuite.DoubleTapWake";
            }
        });
    }
}
