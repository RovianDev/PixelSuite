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
        try { installSceneBouncer(cl); } catch (Throwable ignored) { }
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

    private static void installSceneBouncer(ClassLoader cl) throws Exception {
        final Class<?> vm = Xp.findClass("com.android.systemui.bouncer.ui.viewmodel.PinBouncerViewModel", cl);
        int hooked = 0;
        for (Method m : vm.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            String n = m.getName();
            boolean digit = t.length == 1 && t[0] == int.class && ("onPinButtonClicked".equals(n) || n.startsWith("onDigit") || n.startsWith("onNumber"));
            if (!digit) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { onSceneInput(p.thisObject); } catch (Throwable ignored) { }
                }
            });
            hooked++;
        }
        if (hooked == 0) throw new NoSuchMethodException("onPinButtonClicked");
    }

    private static Object sSceneLpu;

    private static void onSceneInput(Object vm) throws Exception {
        Context ctx = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        if (ctx == null || Settings.Secure.getInt(ctx.getContentResolver(), KEY, 0) != 1) return;
        int user = ((Number) Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null)).intValue();
        if (user != 0) return;
        if (sSceneLpu == null) sSceneLpu = Class.forName("com.android.internal.widget.LockPatternUtils").getConstructor(Context.class).newInstance(ctx);
        watch(ctx, sSceneLpu);
        int typed = sceneTyped(vm);
        if (typed != 4) return;
        int length = sceneLength(vm, user);
        int failed = ((Number) Xp.callMethod(sSceneLpu, "getCurrentFailedPasswordAttempts", user)).intValue();
        if (length != 4 || failed >= 5) return;
        pressEnter(vm);
    }

    private static Method sEnter;
    private static int sEnterKind;

    private static boolean pressEnter(Object vm) throws Exception {
        if (sEnter == null) {
            Method list = null, def = null, bool = null;
            for (Class<?> k = vm.getClass(); k != null && k != Object.class && sEnter == null; k = k.getSuperclass())
                for (Method m : k.getDeclaredMethods()) {
                    String n = m.getName(); Class<?>[] t = m.getParameterTypes();
                    if (t.length == 0 && (n.equals("onAuthenticateButtonClicked") || n.startsWith("onAuthenticateButtonClicked$") || n.startsWith("onAuthenticateKeyClicked"))) { sEnter = m; sEnterKind = 0; break; }
                    if (!n.startsWith("tryAuthenticate")) continue;
                    if (n.contains("$default") && java.lang.reflect.Modifier.isStatic(m.getModifiers()) && t.length >= 3 && t[0].isInstance(vm)) def = m;
                    else if (t.length == 2 && java.util.List.class.isAssignableFrom(t[0]) && t[1] == boolean.class) list = m;
                    else if (t.length == 1 && t[0] == boolean.class) bool = m;
                }
            if (sEnter == null) {
                if (list != null) { sEnter = list; sEnterKind = 1; }
                else if (def != null) { sEnter = def; sEnterKind = 2; }
                else if (bool != null) { sEnter = bool; sEnterKind = 3; }
            }
            if (sEnter == null) return false;
            sEnter.setAccessible(true);
        }
        switch (sEnterKind) {
            case 0: sEnter.invoke(vm); break;
            case 1: sEnter.invoke(vm, sceneInput(vm), Boolean.FALSE); break;
            case 2: {
                Class<?>[] t = sEnter.getParameterTypes();
                Object[] a = new Object[t.length];
                a[0] = vm;
                for (int i = 1; i < t.length; i++) {
                    if (java.util.List.class.isAssignableFrom(t[i])) a[i] = sceneInput(vm);
                    else if (t[i] == boolean.class) a[i] = Boolean.FALSE;
                    else if (t[i] == int.class) a[i] = Integer.valueOf(0);
                    else a[i] = null;
                }
                sEnter.invoke(null, a);
                break;
            }
            default: sEnter.invoke(vm, Boolean.FALSE);
        }
        return true;
    }

    private static Object sceneInput(Object vm) throws Exception {
        for (Class<?> k = vm.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Method m : k.getDeclaredMethods())
                if (m.getParameterTypes().length == 0 && (m.getName().equals("getInput") || m.getName().startsWith("getInput$")) && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true); return m.invoke(vm);
                }
        Object state = Xp.callMethod(vm, "getPinInput");
        try { Object v = Xp.callMethod(state, "getValue"); if (v != null) state = v; } catch (Throwable ignored) { }
        return Xp.callMethod(state, "getPin");
    }

    private static int sceneTyped(Object vm) {
        Object state = null;
        for (String getter : new String[]{"getPinInput", "getMutablePinInput"}) {
            try { state = Xp.callMethod(vm, getter); break; } catch (Throwable ignored) { }
        }
        if (state == null) for (String field : new String[]{"pinInput", "mutablePinInput"}) {
            try { state = Xp.getObjectField(vm, field); break; } catch (Throwable ignored) { }
        }
        if (state == null) return -1;
        try { Object v = Xp.callMethod(state, "getValue"); if (v != null) state = v; } catch (Throwable ignored) { }
        try { return ((java.util.List<?>) Xp.callMethod(state, "getPin")).size(); } catch (Throwable ignored) { }
        try { return ((CharSequence) Xp.callMethod(state, "getPinAsString")).length(); } catch (Throwable ignored) { }
        return -1;
    }

    private static int sceneLength(Object vm, int user) {
        synchronized (sKnown) { Integer k = sKnown.get(vm); if (k != null && k == 4) return 4; }
        long now = SystemClock.uptimeMillis();
        synchronized (sAsked) {
            Long at = sAsked.get(vm);
            if (at != null && now - at < RECHECK_MS) return -1;
            sAsked.put(vm, now);
        }
        try {
            int l = ((Number) Xp.callMethod(sSceneLpu, "getPinLength", user)).intValue();
            if (l == 4) synchronized (sKnown) { sKnown.put(vm, 4); }
            return l;
        } catch (Throwable t) { return -1; }
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
