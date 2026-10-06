package io.github.pixelsuite;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.database.ContentObserver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * "Tap or Double Tap to check phone".
 *
 * Double tap to wake (Feature.DOUBLE_TAP_WAKE), in system_server: the stock "Tap to check
 * phone" asks PowerManagerService to wake with reason WAKE_REASON_TAP. Only a plain single tap
 * (PULSING_SINGLE_TAP) is ignored, unless a second one follows quickly. The phone's own double
 * tap (PULSING_DOUBLE_TAP) and System UI's follow-up taps (e.g. "NODOZE tap") always wake, so
 * the screen can never get stuck. The phone's double-tap sensor only works while the stock
 * single tap is on, so doze_tap_gesture stays on in both modes.
 *
 * Double tap lock screen to sleep (Feature.LOCK_SLEEP), in system_server: a read-only touch
 * monitor sees a double tap anywhere on the lock screen. The under-display fingerprint icon is
 * its own window (UdfpsControllerOverlay): it hides while the PIN pad (or the fingerprint
 * prompt) is up and comes back when you return to the clock, so its visibility switches the
 * double tap off and on again. A swipe or long press also switches it off until the icon
 * comes back or the screen wakes again. On phones without that window, a lone single tap
 * switches it off too. No vibration.
 */
final class DoubleTapWake {

    static final String KEY_TAP_GESTURE = "doze_tap_gesture";
    static final String KEY_DOUBLE_TAP_GESTURE = "doze_pulse_on_double_tap";

    private static final String OLD_TITLE = "Tap to check phone";
    private static final String NEW_TITLE = "Tap or Double Tap to check phone";

    private static final String KEY_TAP = "pixelsuite_wake_tap";
    private static final String KEY_DOUBLE = "pixelsuite_wake_double_tap";
    private static final String KEY_SLEEP = "pixelsuite_lock_sleep";
    private static final String CLICK = "androidx.preference.Preference$OnPreferenceClickListener";
    private static final String CHANGE = "androidx.preference.Preference$OnPreferenceChangeListener";

    private static final String PWM = "com.android.server.policy.PhoneWindowManager";
    private static final String PMS = "com.android.server.power.PowerManagerService";
    private static final String POWER_GROUP = "com.android.server.power.PowerGroup";

    /** Max gap between the two taps of a lock screen double tap (finger up to finger down). */
    private static final long DOUBLE_GAP_MS = 400L;
    /** A touch held longer than this is a press, not a tap. */
    private static final long TAP_MAX_MS = 400L;
    private static final int SLEEP_REASON_POWER_BUTTON = 4;
    /** The Tap to check phone page is short; bigger pages (Display > Lock screen) are skipped. */
    private static final int MAX_PAGE_PREFS = 5;

    private static volatile boolean sWakeOn, sSleepOn, sInitDone;
    private static volatile long sLastSingle;
    private static volatile boolean sUdfpsSeen;
    private static volatile int sUdfpsVisibility = -1;
    private static final String UDFPS_WINDOW = "UdfpsControllerOverlay";
    /** Two single-tap events this close together also count as a double tap. */
    private static final long SINGLE_PAIR_MS = 800L;
    private static int sTapReason = 15;   // PowerManager.WAKE_REASON_TAP in AOSP

    private static Context sCtx;
    private static Object sPwm;
    private static PowerManager sPm;
    private static Handler sWorker;
    private static Object sMonitor;
    private static InputChannel sChannel;
    private static Receiver sReceiver;
    private static int sTouchSlop, sDoubleTapSlop;

    private static Handler sMain;

    private interface Action {
        void run(Object pref, Object value);
    }

    private DoubleTapWake() {}

    /** Not used: System UI does not load the module on this phone. Kept for Module. */
    static void installSystemUi(ClassLoader cl) { }

    // ------------------------------------------------------------------ system_server

    static void installSystem(ClassLoader cl) {
        try {
            sTapReason = PowerManager.class.getField("WAKE_REASON_TAP").getInt(null);
        } catch (Throwable ignored) {
            // Hidden field not reachable: keep the AOSP value.
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
    }

    /** Watches the fingerprint icon window's visibility (PIN pad open = icon hidden). */
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
            Object attrs = Xp.getObjectField(windowState, "mAttrs");
            if (!(attrs instanceof android.view.WindowManager.LayoutParams)) return;
            CharSequence title = ((android.view.WindowManager.LayoutParams) attrs).getTitle();
            if (title == null || !title.toString().contains(UDFPS_WINDOW)) return;
            sUdfpsSeen = true;
            if (visibility == sUdfpsVisibility) return;
            sUdfpsVisibility = visibility;
            final boolean visible = visibility == android.view.View.VISIBLE;
            Handler h = sWorker;
            final Receiver r = sReceiver;
            if (h == null || r == null) return;
            h.post(new Runnable() {
                @Override
                public void run() {
                    r.onFingerprintIcon(visible);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static synchronized void init(Object pwm, Context ctx) {
        if (sInitDone || ctx == null) return;
        sPwm = pwm;
        sCtx = ctx;
        sPm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
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
                Module.log("double-tap-wake: wake=" + sWakeOn + " lockSleep=" + sSleepOn);
            }
        };
        ContentResolver cr = ctx.getContentResolver();
        cr.registerContentObserver(Settings.Secure.getUriFor(Feature.DOUBLE_TAP_WAKE), false, obs);
        cr.registerContentObserver(Settings.Secure.getUriFor(Feature.LOCK_SLEEP), false, obs);

        // Repair from the previous test build, which switched the stock single tap off.
        sWorker.post(new Runnable() {
            @Override
            public void run() {
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
        });

        try {
            Object im = ctx.getSystemService(Context.INPUT_SERVICE);
            sMonitor = Xp.callMethod(im, "monitorGestureInput",
                    "PixelSuiteLockTap", Display.DEFAULT_DISPLAY);
            sChannel = (InputChannel) Xp.callMethod(sMonitor, "getInputChannel");
            sReceiver = new Receiver(sChannel, t.getLooper());
        } catch (Throwable e) {
            Module.log("double-tap-wake: touch monitor unavailable", e);
        }
        sInitDone = true;
        Module.log("double-tap-wake: initialised, wake=" + sWakeOn + " lockSleep=" + sSleepOn
                + " monitor=" + (sReceiver != null));
    }

    private static void readFlags() {
        sWakeOn = Feature.on(sCtx, Feature.DOUBLE_TAP_WAKE);
        sSleepOn = Feature.on(sCtx, Feature.LOCK_SLEEP);
    }

    /**
     * Runs inside PowerManagerService's lock: no settings reads, no blocking. Every wake re-arms
     * the lock screen double tap.
     */
    private static void onWakeRequest(Xp.Param param) {
        Handler h = sWorker;
        final Receiver r = sReceiver;
        if (h != null && r != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    r.rearm();
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
        if (details != null && details.contains("SINGLE_TAP")) {
            if (sLastSingle != 0 && now - sLastSingle <= SINGLE_PAIR_MS) {
                sLastSingle = 0;
                Module.log("double-tap-wake: second tap, waking (" + details + ")");
            } else {
                sLastSingle = now;
                param.setResult(null);
                Module.log("double-tap-wake: single tap ignored (" + details + ")");
            }
        } else {
            sLastSingle = 0;
            Module.log("double-tap-wake: waking (" + details + ")");
        }
    }

    // ------------------------------------------------------------------ lock screen taps

    private static final class Receiver extends InputEventReceiver {
        private boolean mArmed = true;
        private boolean mIgnore, mTap;
        private long mDownAt, mPrevUpAt;
        private float mDownX, mDownY, mPrevX, mPrevY;

        Receiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            try {
                if (event instanceof MotionEvent && sSleepOn) onTouch((MotionEvent) event);
            } catch (Throwable t) {
                Module.log("double-tap-wake: touch handling failed", t);
            } finally {
                finishInputEvent(event, false);   // read-only: never block input
            }
        }

        /** Screen woke: start fresh. */
        void rearm() {
            mArmed = true;
            mPrevUpAt = 0;
        }

        /** Fingerprint icon window shown (back at the clock) or hidden (PIN pad / prompt up). */
        void onFingerprintIcon(boolean visible) {
            if (!sSleepOn) return;
            if (visible) {
                if (lockScreenShowing()) {
                    if (!mArmed) Module.log("double-tap-wake: back at the lock screen, double tap on");
                    rearm();
                }
            } else {
                disarm("PIN pad or fingerprint prompt open");
            }
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
                    if (screenOn && !lock) {   // unlocked: start clean next time
                        mArmed = true;
                        mPrevUpAt = 0;
                    }
                    if (mIgnore) return;
                    long down = ev.getEventTime();
                    if (mPrevUpAt != 0 && down - mPrevUpAt > DOUBLE_GAP_MS) {
                        // The fingerprint icon tells us when the PIN pad opens; without it,
                        // a lone tap (which may open the PIN pad) switches the double tap off.
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
                        disarm("two fingers");
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!mIgnore && mTap && (Math.abs(ev.getX() - mDownX) > sTouchSlop
                            || Math.abs(ev.getY() - mDownY) > sTouchSlop)) {
                        mTap = false;
                        disarm("swipe");
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
                        disarm("long press");
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
                                    + "(PIN pad was opened or the screen was swiped)");
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

    private static void onLockDoubleTap() {
        boolean screenOn = sPm != null && sPm.isInteractive();
        boolean lockShowing = lockScreenShowing();
        Module.log("double-tap-wake: lock screen double tap, screenOn=" + screenOn
                + " lockScreen=" + lockShowing);
        if (!screenOn || !lockShowing) return;
        try {
            PowerManager.class.getMethod("goToSleep", long.class, int.class, int.class)
                    .invoke(sPm, SystemClock.uptimeMillis(), SLEEP_REASON_POWER_BUTTON, 0);
            Module.log("double-tap-wake: screen off");
        } catch (Throwable t) {
            Module.log("double-tap-wake: goToSleep failed", t);
        }
    }

    /** Lock screen visible and not covered by an app (camera, alarm, call). */
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

    // ------------------------------------------------------------------ Settings

    static void installSettings(ClassLoader cl) {
        Xp.Callback rename = new Xp.Callback() {
            @Override
            protected void afterHookedMethod(Xp.Param param) {
                Object r = param.getResult();
                if (r instanceof CharSequence) {
                    CharSequence s = (CharSequence) r;
                    if (s.length() == OLD_TITLE.length() && OLD_TITLE.contentEquals(s)) {
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
                if (total <= MAX_PAGE_PREFS) {
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
        if (!has(p, "setChecked", boolean.class)) return false;   // links to the page are not
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
                    "Tap the screen twice to wake it. A single tap does nothing.");
            Object sleep = make(switchClass(loader), ctx, KEY_SLEEP,
                    "Double tap lock screen to sleep",
                    "Double-tap the lock screen to turn the screen off. Never while the PIN pad "
                            + "is open.");

            Object radioClick = listener(loader, CLICK, "onPreferenceClick", new Action() {
                @Override
                public void run(Object pref, Object value) {
                    boolean d = KEY_DOUBLE.equals(String.valueOf(Xp.callMethod(pref, "getKey")));
                    ContentResolver cr = ctx.getContentResolver();
                    writeReal(cr, Feature.DOUBLE_TAP_WAKE, d ? 1 : 0);
                    // Picking a mode also turns the feature on (both modes need the tap sensor).
                    writeReal(cr, KEY_TAP_GESTURE, 1);
                    Xp.callMethod(mainSwitch, "setChecked", true);
                    refresh(group, ctx);
                }
            });
            Object sleepChange = listener(loader, CHANGE, "onPreferenceChange", new Action() {
                @Override
                public void run(Object pref, Object value) {
                    Settings.Secure.putInt(ctx.getContentResolver(), Feature.LOCK_SLEEP,
                            Boolean.TRUE.equals(value) ? 1 : 0);
                }
            });
            Xp.callMethod(tap, "setOnPreferenceClickListener", radioClick);
            Xp.callMethod(dbl, "setOnPreferenceClickListener", radioClick);
            Xp.callMethod(sleep, "setOnPreferenceChangeListener", sleepChange);

            int order = (Integer) Xp.callMethod(mainSwitch, "getOrder");
            if (order < Integer.MAX_VALUE - 10) {
                Xp.callMethod(tap, "setOrder", order + 1);
                Xp.callMethod(dbl, "setOrder", order + 2);
                Xp.callMethod(sleep, "setOrder", order + 3);
            }
            Xp.callMethod(group, "addPreference", tap);
            Xp.callMethod(group, "addPreference", dbl);
            Xp.callMethod(group, "addPreference", sleep);
            Module.log("double-tap-wake: options added (" + radio.getSimpleName() + ")");
        }
        refresh(group, ctx);
    }

    private static Object make(Class<?> c, Context ctx, String key, String title, String summary) {
        Object p = Xp.newInstance(c, ctx);
        Xp.callMethod(p, "setKey", key);
        Xp.callMethod(p, "setTitle", (CharSequence) title);
        Xp.callMethod(p, "setSummary", (CharSequence) summary);
        Xp.callMethod(p, "setPersistent", false);
        return p;
    }

    private static void refresh(Object group, Context ctx) {
        boolean d = Feature.on(ctx, Feature.DOUBLE_TAP_WAKE);
        check(find(group, KEY_TAP), !d);
        check(find(group, KEY_DOUBLE), d);
        check(find(group, KEY_SLEEP), Feature.on(ctx, Feature.LOCK_SLEEP));
    }

    private static Object find(Object group, String key) {
        return Xp.callMethod(group, "findPreference", (CharSequence) key);
    }

    private static void check(Object pref, boolean on) {
        if (pref != null) Xp.callMethod(pref, "setChecked", on);
    }

    private static boolean has(Object o, String name, Class<?>... types) {
        try {
            o.getClass().getMethod(name, types);
            return true;
        } catch (Throwable t) {
            return false;
        }
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

    private static Class<?> switchClass(ClassLoader loader) throws ClassNotFoundException {
        try {
            return Class.forName("androidx.preference.SwitchPreferenceCompat", false, loader);
        } catch (ClassNotFoundException e) {
            return Class.forName("androidx.preference.SwitchPreference", false, loader);
        }
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
