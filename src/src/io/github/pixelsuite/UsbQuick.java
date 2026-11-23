package io.github.pixelsuite;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.concurrent.Executor;

final class UsbQuick {
    private UsbQuick() { }

    static final String KEY = "pixelsuite_usb_quick";
    private static final String TAG = "PixelSuite";
    private static volatile Context sContext;
    private static volatile long sReadAt; private static volatile boolean sOn;

    private static Context context() {
        Context c = sContext;
        if (c == null) try { c = sContext = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable ignored) { }
        return c;
    }

    static boolean on() {
        Context c = context(); if (c == null) return false;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sReadAt > 1000) { sReadAt = now; try { sOn = Settings.Secure.getInt(c.getContentResolver(), KEY, 0) == 1; } catch (Throwable t) { sOn = false; } }
        return sOn;
    }

    private static boolean allowed() {
        if (!on()) return false;
        try {
            android.app.KeyguardManager k = (android.app.KeyguardManager) context().getSystemService(Context.KEYGUARD_SERVICE);
            return k != null && !k.isKeyguardLocked();
        } catch (Throwable t) { return false; }
    }

    private static boolean fromUsb() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String n = e.getClassName();
            if (n.startsWith("com.android.settings.connecteddevice.usb.") || (n.startsWith("com.android.settings") && n.contains("Usb"))) return true;
        }
        return false;
    }

    private static Runnable runnable(Object[] args) { for (Object a : args) if (a instanceof Runnable) return (Runnable) a; return null; }

    static void installSettings(ClassLoader cl) {
        Class<?> controller = Xp.findClass("com.android.settings.connecteddevice.usb.UsbDetailsController", cl);
        if (controller != null) Xp.hookAllMethods(controller, "requireAuthAndExecute", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Runnable r = runnable(p.args);
                    if (r == null || !allowed()) return;
                    p.setResult(null); r.run();
                } catch (Throwable t) { Log.w(TAG, "quick usb: controller", t); }
            }
        });
        Class<?> dpp = Xp.findClass("com.android.settings.wifi.dpp.WifiDppUtils", cl);
        if (dpp != null) Xp.hookAllMethods(dpp, "showLockScreen", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Runnable r = runnable(p.args);
                    if (r == null || !allowed() || !fromUsb()) return;
                    p.setResult(null); r.run();
                } catch (Throwable t) { Log.w(TAG, "quick usb: prompt", t); }
            }
        });
        try {
            Class<?> prompt = Class.forName("android.hardware.biometrics.BiometricPrompt");
            Xp.hookAllMethods(prompt, "authenticate", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!allowed() || !fromUsb()) return;
                        Object callback = null; Executor ex = null;
                        for (Object a : p.args) {
                            if (a instanceof Executor) ex = (Executor) a;
                            else if (isCallback(a)) callback = a;
                        }
                        if (!isCallback(callback)) return;
                        final Object cb = callback; final Object result = success();
                        if (result == null) return;
                        p.setResult(null);
                        Runnable go = () -> { try { cb.getClass().getMethod("onAuthenticationSucceeded", result.getClass()).invoke(cb, result); } catch (Throwable t) { Log.w(TAG, "quick usb: success", t); } };
                        if (ex != null) ex.execute(go); else new android.os.Handler(android.os.Looper.getMainLooper()).post(go);
                    } catch (Throwable t) { Log.w(TAG, "quick usb: biometric", t); }
                }
            });
        } catch (Throwable t) { Log.w(TAG, "quick usb: biometric hook", t); }
    }

    private static boolean isCallback(Object a) {
        if (a == null) return false;
        for (Class<?> k = a.getClass(); k != null; k = k.getSuperclass()) if ("android.hardware.biometrics.BiometricPrompt$AuthenticationCallback".equals(k.getName())) return true;
        return false;
    }

    private static Object success() {
        try {
            Class<?> r = Class.forName("android.hardware.biometrics.BiometricPrompt$AuthenticationResult");
            for (Constructor<?> c : r.getDeclaredConstructors()) {
                Class<?>[] t = c.getParameterTypes(); Object[] v = new Object[t.length];
                for (int i = 0; i < t.length; i++) v[i] = t[i] == int.class ? (Object) 1  : t[i] == long.class ? (Object) 0L : t[i] == boolean.class ? (Object) false : null;
                c.setAccessible(true);
                try { return c.newInstance(v); } catch (Throwable ignored) { }
            }
        } catch (Throwable t) { Log.w(TAG, "quick usb: result", t); }
        return null;
    }
}
