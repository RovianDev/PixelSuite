package io.github.pixelsuite;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class PowerHold {
    private PowerHold() {}

    static final String KEY = "pixelsuite_power_hold";
    static final String KEY_LOCKED_GEMINI = "pixelsuite_power_hold_locked_gemini";
    static final String KEY_LOCKED = "pixelsuite_power_hold_locked";
    static final String GEMINI = "gemini", SCREENSHOT = "screenshot", SILENCE = "silence", DND = "dnd", DNA = "dna", ALL = "all";
    static final String OFF = "off", CIRCLE = "circle";
    private static final int HAPTIC_LONG_PRESS_POWER = 10003;
    private static final int RINGER_SILENT = 0, RINGER_NORMAL = 2;

    private static volatile String sChoice = GEMINI, sLockedChoice = GEMINI;
    private static volatile String sLatched;
    private static volatile Slot sLatchedSlot;
    private static volatile boolean sStarted;
    private static Handler sWorker;
    private static ClassLoader sLoader;
    private static final class Slot {
        volatile int ringerBefore = RINGER_NORMAL;
        volatile boolean allOn, allDndBefore, allDnaBefore;
        volatile int allRingerBefore = RINGER_NORMAL;
    }
    private static final Slot UNLOCKED = new Slot(), LOCKED = new Slot();

    static void installSystem(ClassLoader cl) {
        sLoader = cl;
        Class<?> rule = Xp.findClass("com.android.server.policy.PhoneWindowManager$PowerKeyRule", cl);
        for (Method m : rule.getDeclaredMethods()) {
            if (!"onLongPress".equals(m.getName()) || m.getParameterTypes().length != 1) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { onLongPress(p); } catch (Throwable ignored) { }
                }
            });
        }
        Xp.deoptimizeAll(rule, "onKeyGesture");
        try { installCircle(cl); } catch (Throwable ignored) { }
    }

    private static void onLongPress(Xp.Param p) throws Exception {
        Object pwm = outer(p.thisObject);
        if (pwm == null) return;
        Context c = (Context) Xp.getObjectField(pwm, "mContext");
        start(c);
        int action = (Integer) Xp.callMethod(p.args[0], "getAction");
        if (action == 0) { boolean lk = locked(c); sLatched = lk ? sLockedChoice : sChoice; sLatchedSlot = lk ? LOCKED : UNLOCKED; }
        String choice = sLatched != null ? sLatched : sChoice;
        final Slot slot = sLatchedSlot != null ? sLatchedSlot : UNLOCKED;
        if (action != 0) { sLatched = null; sLatchedSlot = null; }
        if (choice == null || GEMINI.equals(choice)) return;
        try {
            Object detector = Xp.getObjectField(pwm, "mSingleKeyGestureDetector");
            boolean fromOff = Boolean.TRUE.equals(Xp.callMethod(detector, "beganFromNonInteractive"));
            if (fromOff && !Xp.getBooleanField(pwm, "mSupportLongPressPowerWhenNonInteractive")) return;
        } catch (Throwable ignored) { }
        p.setResult(null);
        if (action != 1) return;
        try { Xp.setBooleanField(pwm, "mPowerKeyHandled", true); } catch (Throwable ignored) { }
        if (OFF.equals(choice)) return;
        try { Xp.callMethod(pwm, "performHapticFeedback", HAPTIC_LONG_PRESS_POWER, "Pixel Suite - Hold power"); } catch (Throwable ignored) { }
        final String run = choice;
        final Context ctx = c;
        sWorker.post(new Runnable() { public void run() { act(ctx, run, slot); } });
    }

    private static Object outer(Object rule) {
        for (Class<?> k = rule.getClass(); k != null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getType().getName().equals("com.android.server.policy.PhoneWindowManager")) {
                    try { f.setAccessible(true); return f.get(rule); } catch (Throwable ignored) { }
                }
            }
        }
        return null;
    }

    private static synchronized void start(final Context c) {
        if (sStarted || c == null) return;
        sStarted = true;
        HandlerThread t = new HandlerThread("PixelSuitePowerHold");
        t.start();
        sWorker = new Handler(t.getLooper());
        final ContentResolver cr = c.getContentResolver();
        sChoice = read(cr);
        sLockedChoice = lockedChoice(cr);
        try {
            ContentObserver o = new ContentObserver(sWorker) {
                @Override public void onChange(boolean self) { sChoice = read(cr); sLockedChoice = lockedChoice(cr); }
            };
            cr.registerContentObserver(Settings.Secure.getUriFor(KEY), false, o);
            cr.registerContentObserver(Settings.Secure.getUriFor(KEY_LOCKED), false, o);
            cr.registerContentObserver(Settings.Secure.getUriFor(KEY_LOCKED_GEMINI), false, o);
        } catch (Throwable ignored) { }
    }

    static String lockedChoice(ContentResolver cr) {
        try {
            String v = Settings.Secure.getString(cr, KEY_LOCKED);
            if (GEMINI.equals(v) || SILENCE.equals(v) || DND.equals(v) || DNA.equals(v) || ALL.equals(v)) return v;
            if (v == null) {
                if (Settings.Secure.getInt(cr, KEY_LOCKED_GEMINI, 0) == 1) return GEMINI;
                String u = read(cr);
                if (SILENCE.equals(u) || DND.equals(u) || DNA.equals(u) || ALL.equals(u)) return u;
            }
        } catch (Throwable ignored) { }
        return GEMINI;
    }

    private static boolean locked(Context c) {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return (km != null && km.isKeyguardLocked()) || (pm != null && !pm.isInteractive());
        } catch (Throwable t) { return false; }
    }

    private static String read(ContentResolver cr) {
        try {
            String v = Settings.Secure.getString(cr, KEY);
            if (OFF.equals(v) || CIRCLE.equals(v) || SCREENSHOT.equals(v) || SILENCE.equals(v) || DND.equals(v) || DNA.equals(v) || ALL.equals(v)) return v;
        } catch (Throwable ignored) { }
        return GEMINI;
    }

    static final String CIRCLE_ACTION = "io.github.pixelsuite.action.CIRCLE_TO_SEARCH";
    private static final String CS_PERMISSION = "android.permission.ACCESS_CONTEXTUAL_SEARCH";
    private static final ThreadLocal<Boolean> sMine = new ThreadLocal<Boolean>();

    private static void installCircle(ClassLoader cl) {
        Class<?> svc;
        try { svc = Xp.findClass("com.android.server.contextualsearch.ContextualSearchManagerService", cl); } catch (Throwable t) { return; }
        java.util.ArrayList<Class<?>> classes = new java.util.ArrayList<Class<?>>();
        classes.add(svc);
        try { for (Class<?> k : svc.getDeclaredClasses()) classes.add(k); } catch (Throwable ignored) { }
        for (Class<?> k : classes) {
            for (Method m : k.getDeclaredMethods()) {
                String n = m.getName();
                if (n.toLowerCase(java.util.Locale.ROOT).contains("enforce") && m.getReturnType() == void.class) {
                    try {
                        Xp.hookMethod(m, new Xp.Callback() {
                            @Override protected void beforeHookedMethod(Xp.Param p) { if (Boolean.TRUE.equals(sMine.get())) p.setResult(null); }
                        });
                    } catch (Throwable ignored) { }
                }
            }
            try { Xp.deoptimizeAll(k, "startContextualSearch"); } catch (Throwable ignored) { }
        }
    }

    private static int entry(Object manager) {
        try { return manager.getClass().getField("ENTRYPOINT_LONG_PRESS_NAV_HANDLE").getInt(null); } catch (Throwable ignored) { return 1; }
    }

    private static boolean circle(Context c) {
        try {
            Object m = c.getSystemService("contextual_search");
            if (m == null) throw new IllegalStateException("no contextual_search service");
            sMine.set(Boolean.TRUE);
            try { m.getClass().getMethod("startContextualSearch", int.class).invoke(m, entry(m)); }
            finally { sMine.remove(); }
            return true;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            android.util.Log.w("PixelSuitePower", "Circle to Search in system_server failed, asking Pixel Launcher", cause);
        }
        try {
            android.content.Intent i = new android.content.Intent(CIRCLE_ACTION).setPackage("com.google.android.apps.nexuslauncher")
                    .addFlags(android.content.Intent.FLAG_RECEIVER_FOREGROUND);
            Method send = Context.class.getMethod("sendBroadcastAsUser", android.content.Intent.class, android.os.UserHandle.class, String.class);
            send.invoke(c, i, android.os.UserHandle.class.getField("CURRENT").get(null), null);
            return true;
        } catch (Throwable t) {
            android.util.Log.w("PixelSuitePower", "Circle to Search: couldn't reach Pixel Launcher", t);
            return false;
        }
    }

    static void installLauncher(ClassLoader cl) {
        Method create;
        try { create = android.app.Instrumentation.class.getMethod("callApplicationOnCreate", android.app.Application.class); } catch (Throwable t) { return; }
        Xp.hookMethod(create, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    final Context app = (Context) p.args[0];
                    if (app == null || !"com.google.android.apps.nexuslauncher".equals(app.getPackageName())) return;
                    android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
                        @Override public void onReceive(Context ctx, android.content.Intent intent) {
                            try {
                                Object m = app.getSystemService("contextual_search");
                                m.getClass().getMethod("startContextualSearch", int.class).invoke(m, entry(m));
                            } catch (Throwable t) {
                                android.util.Log.w("PixelSuitePower", "Circle to Search in Pixel Launcher failed", t);
                            }
                        }
                    };
                    android.content.IntentFilter f = new android.content.IntentFilter(CIRCLE_ACTION);
                    boolean done = false;
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        try {
                            Context.class.getMethod("registerReceiver", android.content.BroadcastReceiver.class, android.content.IntentFilter.class, String.class, Handler.class, int.class)
                                    .invoke(app, r, f, CS_PERMISSION, null, 2 );
                            done = true;
                        } catch (Throwable t) { android.util.Log.w("PixelSuitePower", "Circle to Search relay (exported) not registered", t); }
                    }
                    if (!done) app.registerReceiver(r, f, CS_PERMISSION, null);
                } catch (Throwable t) {
                    android.util.Log.w("PixelSuitePower", "Circle to Search relay not registered", t);
                }
            }
        });
    }

    private static void act(Context c, String choice, Slot slot) {
        long id = Binder.clearCallingIdentity();
        try {
            if (SCREENSHOT.equals(choice)) { ThreeFinger.screenshot(c); return; }
            if (CIRCLE.equals(choice)) { if (!circle(c)) toast(c, "Circle to Search isn't available"); return; }
            if (SILENCE.equals(choice)) { boolean on = !silent(c); silence(c, on, slot); toast(c, on ? "Silence on" : "Silence off"); return; }
            if (DND.equals(choice)) { boolean on = zen(c) == 0; setZen(c, on); toast(c, on ? "Do Not Disturb on" : "Do Not Disturb off"); return; }
            if (DNA.equals(choice)) { boolean on = !dna(c); setDna(c, on); toast(c, on ? "Do Not Annoy on" : "Do Not Annoy off"); return; }
            if (ALL.equals(choice)) { boolean on = !(silent(c) && zen(c) != 0 && dna(c)); all(c, on, slot); toast(c, on ? "No Annoyance on" : "No Annoyance off"); }
        } catch (Throwable ignored) {
        } finally {
            Binder.restoreCallingIdentity(id);
        }
    }

    private static int ringer(Context c) throws Exception {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return (Integer) AudioManager.class.getMethod("getRingerModeInternal").invoke(am);
    }
    private static void setRinger(Context c, int mode) throws Exception {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        AudioManager.class.getMethod("setRingerModeInternal", int.class).invoke(am, mode);
    }
    private static boolean silent(Context c) throws Exception { return ringer(c) == RINGER_SILENT; }
    private static void silence(Context c, boolean on, Slot s) throws Exception {
        if (on) { int now = ringer(c); if (now != RINGER_SILENT) s.ringerBefore = now; setRinger(c, RINGER_SILENT); }
        else setRinger(c, s.ringerBefore == RINGER_SILENT ? RINGER_NORMAL : s.ringerBefore);
    }

    private static int zen(Context c) throws Exception {
        Object nm = c.getSystemService(Context.NOTIFICATION_SERVICE);
        return (Integer) nm.getClass().getMethod("getZenMode").invoke(nm);
    }
    private static void setZen(Context c, boolean on) throws Exception {
        Object nm = c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.getClass().getMethod("setZenMode", int.class, android.net.Uri.class, String.class, boolean.class)
                .invoke(nm, on ? 1 : 0, null, "Pixel Suite hold power", true);
    }

    private static boolean dna(Context c) {
        try { return "1".equals(Settings.Secure.getString(c.getContentResolver(), DoNotAnnoy.KEY)); } catch (Throwable t) { return false; }
    }
    private static void setDna(Context c, boolean on) {
        try { Settings.Secure.putString(c.getContentResolver(), DoNotAnnoy.KEY, on ? "1" : "0"); } catch (Throwable ignored) { }
    }

    private static void all(Context c, boolean on, Slot s) throws Exception {
        if (on) {
            int r = ringer(c);
            s.allRingerBefore = r;
            s.allDndBefore = zen(c) != 0;
            s.allDnaBefore = dna(c);
            s.allOn = true;
            if (r != RINGER_SILENT) { s.ringerBefore = r; setRinger(c, RINGER_SILENT); }
            if (!s.allDndBefore) setZen(c, true);
            if (!s.allDnaBefore) setDna(c, true);
        } else if (s.allOn) {
            s.allOn = false;
            setRinger(c, s.allRingerBefore);
            setZen(c, s.allDndBefore);
            setDna(c, s.allDnaBefore);
        } else {
            setRinger(c, RINGER_NORMAL);
            setZen(c, false);
            setDna(c, false);
        }
    }

    private static void toast(final Context c, final CharSequence text) {
        try {
            Looper ui = (Looper) Xp.callMethod(Xp.callStaticMethod(Xp.findClass("com.android.server.UiThread", sLoader), "get"), "getLooper");
            new Handler(ui).post(new Runnable() {
                public void run() {
                    try {
                        Method make = Toast.class.getMethod("makeText", Context.class, Looper.class, CharSequence.class, int.class);
                        ((Toast) make.invoke(null, c, Looper.myLooper(), text, Toast.LENGTH_SHORT)).show();
                    } catch (Throwable t) {
                        try { Toast.makeText(c, I18n.cs(text), Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
                    }
                }
            });
        } catch (Throwable ignored) { }
    }
}
