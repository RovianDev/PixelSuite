package io.github.pixelsuite;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class PanelBack {
    private static final int PRIORITY_OVERLAY = 1000000;
    private final Activity activity;
    private final Runnable onBack;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Object dispatcher, callback;
    private Method unregister;

    PanelBack(Activity activity, Runnable onBack) { this.activity = activity; this.onBack = onBack; }

    void on() {
        if (callback != null || activity == null) return;
        try {
            Object d = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(activity);
            Class<?> type = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherType = Class.forName("android.window.OnBackInvokedDispatcher");
            Object cb = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, new InvocationHandler() {
                public Object invoke(Object self, Method m, Object[] args) {
                    switch (m.getName()) {
                        case "onBackInvoked": main.post(onBack); return null;
                        case "hashCode": return System.identityHashCode(self);
                        case "equals": return self == args[0];
                        case "toString": return "PixelSuitePanelBack";
                        default: return null;
                    }
                }
            });
            dispatcherType.getMethod("registerOnBackInvokedCallback", int.class, type).invoke(d, PRIORITY_OVERLAY, cb);
            dispatcher = d; callback = cb; unregister = dispatcherType.getMethod("unregisterOnBackInvokedCallback", type);
        } catch (Throwable olderAndroid) { dispatcher = callback = null; unregister = null; }
    }

    static boolean atBackEdge(android.view.View any, android.view.MotionEvent e) {
        if (any == null) return false;
        float d = any.getResources().getDisplayMetrics().density;
        int left = Math.round(24 * d), right = left;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.view.WindowInsets in = any.getRootWindowInsets();
                if (in != null) {
                    Object g = android.view.WindowInsets.class.getMethod("getSystemGestureInsets").invoke(in);
                    left = Math.max(left, g.getClass().getField("left").getInt(g)); right = Math.max(right, g.getClass().getField("right").getInt(g));
                }
            }
        } catch (Throwable ignored) { }
        int width = any.getRootView() != null ? any.getRootView().getWidth() : any.getResources().getDisplayMetrics().widthPixels;
        int[] at = new int[2]; any.getRootView().getLocationOnScreen(at);
        float x = e.getRawX() - at[0];
        return x < left + 8 * d || x > width - right - 8 * d;
    }

    void off() {
        if (callback == null) return;
        try { unregister.invoke(dispatcher, callback); } catch (Throwable ignored) { }
        dispatcher = callback = null; unregister = null;
    }
}
