package io.github.pixelsuite;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

final class FeedBackGesture {
    private FeedBackGesture() {}

    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private static final long AFTER_MS = 700;

    private static final long LEAVE = (1L << 2) | (1L << 3) | (1L << 9) | (1L << 11);

    private static volatile boolean sFeedOpen, sPolling;
    private static volatile long sFeedLeftAt = -1;
    private static volatile String sHome;
    private static Handler sWorker;

    static void install(final ClassLoader cl) {
        final Class<?> edge;
        try { edge = Xp.findClass(EDGE, cl); }
        catch (Throwable t) { return; }

        Xp.hookAllConstructors(edge, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                sHandler = new java.lang.ref.WeakReference<Object>(p.thisObject);
                if (!sListening) { sListening = listen(cl, p.thisObject); }
            }
        });

        int touch = Xp.hookAllMethods(edge, "isWithinTouchRegion", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { if (Boolean.TRUE.equals(p.getResult()) && block(p.thisObject, null)) p.setResult(Boolean.FALSE); } catch (Throwable ignored) { }
            }
        }).size();
        if (touch == 0) {
            Xp.Callback after = new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { try { afterDown(p.thisObject, p.args.length == 1 ? p.args[0] : null); } catch (Throwable ignored) { } }
            };
            for (int i = 0; i < 160; i++) {
                Class<?> k;
                try { k = Xp.findClass(EDGE + "$$ExternalSyntheticLambda" + i, cl); } catch (Throwable t) { continue; }
                for (Method m : k.getDeclaredMethods()) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (!"invoke".equals(m.getName()) || ps.length != 1 || !(ps[0] == Object.class || android.view.InputEvent.class.isAssignableFrom(ps[0]))) continue;
                    Xp.hookMethod(m, after);
                }
            }
            for (int i = 1; i <= 4; i++) {
                try { Xp.hookAllMethods(Xp.findClass("com.android.systemui.navigationbar.gestural.DisplayBackGestureHandlerImpl$inputEventReceiver$" + i, cl), "onInputEvent", after); } catch (Throwable ignored) { }
            }
            Xp.hookAllMethods(edge, "onMotionEvent", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { try { afterDown(p.thisObject, p.args.length == 1 ? p.args[0] : null); } catch (Throwable ignored) { } }
            });
        }
    }

    private static final String EDGE = "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler";
    private static volatile java.lang.ref.WeakReference<Object> sHandler = new java.lang.ref.WeakReference<Object>(null);
    private static volatile boolean sListening;
    private static long sLastDownTime = -1;

    private static void afterDown(Object self, Object event) {
        if (!(event instanceof MotionEvent)) return;
        MotionEvent ev = (MotionEvent) event;
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN || !ev.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)) return;
        if (ev.getDownTime() == sLastDownTime) return;
        Object h = sHandler.get();
        if (h == null && self != null) {
            if (self.getClass().getName().equals(EDGE)) h = self;
            else for (java.lang.reflect.Field f : self.getClass().getDeclaredFields())
                try { f.setAccessible(true); Object v = f.get(self); if (v != null && v.getClass().getName().equals(EDGE)) { h = v; sHandler = new java.lang.ref.WeakReference<Object>(v); break; } } catch (Throwable ignored) { }
        }
        if (h == null) return;
        boolean allowed;
        try { allowed = Xp.getBooleanField(h, "mAllowGesture"); } catch (Throwable t) { return; }
        if (!allowed) return;
        sLastDownTime = ev.getDownTime();
        if (!block(h, ev)) { return; }
        MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);

        if (self != null && self.getClass().getName().contains("$$ExternalSyntheticLambda")) {
            try {
                Xp.callMethod(self, "invoke", cancel);
                boolean still = Xp.getBooleanField(h, "mAllowGesture");
                if (still) Xp.setBooleanField(h, "mAllowGesture", false);
                cancel.recycle();
                return;
            } catch (Throwable ignored) { }
        }
        try { Xp.setBooleanField(h, "mAllowGesture", false); } catch (Throwable t) { cancel.recycle(); return; }
        try { Xp.callMethod(h, "dispatchToBackAnimation", cancel); } catch (Throwable ignored) { }
        for (Class<?> k = h.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getType().getName().contains("EdgeBackPlugin")) continue;
                try { f.setAccessible(true); Object plugin = f.get(h); if (plugin != null) Xp.callMethod(plugin, "onMotionEvent", cancel); } catch (Throwable ignored) { }
            }
        cancel.recycle();
    }

    private static boolean block(Object handler, MotionEvent ev) {
        Context c = context();
        if (c == null) return false;
        ComponentName top = top(c);
        if (top == null) return false;
        long now = SystemClock.uptimeMillis();
        boolean feed = isFeed(top);
        seen(feed, now);
        if (!feed && !(sFeedLeftAt > 0 && now - sFeedLeftAt < AFTER_MS && top.getPackageName().equals(home(c)))) return false;
        long flags = sysUiFlags(handler);
        if ((flags & LEAVE) != 0) { return false; }
        try { KeyguardManager k = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE); if (k != null && k.isKeyguardLocked()) { return false; } } catch (Throwable ignored) { }
        return true;
    }

    private static void seen(boolean feed, long now) {
        if (feed && !sFeedOpen) {
            sFeedOpen = true;
            poll();
        } else if (!feed && sFeedOpen) {
            sFeedOpen = false;
            sFeedLeftAt = now;
        }
    }

    private static boolean isFeed(ComponentName top) {
        return GOOGLE.equals(top.getPackageName()) && top.getClassName().toLowerCase(java.util.Locale.ROOT).contains("minusone");
    }

    private static boolean listen(ClassLoader cl, Object handler) {
        try {
            Class<?> listeners = Xp.findClass("com.android.systemui.shared.system.TaskStackChangeListeners", cl);
            Class<?> type = Xp.findClass("com.android.systemui.shared.system.TaskStackChangeListener", cl);
            if (!type.isInterface()) return false;
            Object instance = null;

            for (Class<?> k = handler.getClass(); k != null && k != Object.class && instance == null; k = k.getSuperclass())
                for (java.lang.reflect.Field f : k.getDeclaredFields())
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && listeners.isAssignableFrom(f.getType())) { f.setAccessible(true); instance = f.get(handler); if (instance != null) break; }
            if (instance == null) try { instance = Xp.callStaticMethod(listeners, "getInstance"); } catch (Throwable ignored) { }
            if (instance == null) try { java.lang.reflect.Field f = listeners.getDeclaredField("INSTANCE"); f.setAccessible(true); instance = f.get(null); } catch (Throwable ignored) { }
            if (instance == null) try { java.lang.reflect.Field f = listeners.getDeclaredField("Companion"); f.setAccessible(true); instance = Xp.callMethod(f.get(null), "getInstance"); } catch (Throwable ignored) { }
            if (instance == null) { return false; }
            Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, new InvocationHandler() {
                public Object invoke(Object self, Method m, Object[] args) {
                    String n = m.getName();
                    if ("hashCode".equals(n)) return System.identityHashCode(self);
                    if ("equals".equals(n)) return args != null && args.length == 1 && self == args[0];
                    if ("toString".equals(n)) return "PixelSuiteFeedBackGesture";
                    if ("onTaskStackChanged".equals(n) || "onTaskMovedToFront".equals(n) || "onActivityRestartAttempt".equals(n) || "onTaskFocusChanged".equals(n)) refreshLater();
                    Class<?> r = m.getReturnType();
                    return r == boolean.class ? Boolean.FALSE : null;
                }
            });
            Xp.callMethod(instance, "registerTaskStackListener", proxy);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized Handler worker() {
        if (sWorker == null) {
            HandlerThread t = new HandlerThread("PixelSuiteFeedBack");
            t.start();
            sWorker = new Handler(t.getLooper());
        }
        return sWorker;
    }

    private static void refreshLater() {
        worker().post(new Runnable() { public void run() {
            Context c = context();
            ComponentName top = c == null ? null : top(c);
            if (top != null) seen(isFeed(top), SystemClock.uptimeMillis());
        } });
    }

    private static void poll() {
        if (sPolling) return;
        sPolling = true;
        worker().postDelayed(new Runnable() { public void run() {
            Context c = context();
            boolean awake = true;
            try { PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE); awake = pm == null || pm.isInteractive(); } catch (Throwable ignored) { }
            if (c == null || !sFeedOpen || !awake) { sPolling = false; return; }
            ComponentName top = top(c);
            if (top != null) seen(isFeed(top), SystemClock.uptimeMillis());
            if (sFeedOpen) worker().postDelayed(this, 400); else sPolling = false;
        } }, 400);
    }

    @SuppressWarnings("deprecation")
    private static ComponentName top(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am == null ? null : am.getRunningTasks(1);
            return tasks == null || tasks.isEmpty() ? null : tasks.get(0).topActivity;
        } catch (Throwable t) { return null; }
    }

    private static String home(Context c) {
        String h = sHome;
        if (h != null) return h;
        try {
            ResolveInfo r = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            h = r == null || r.activityInfo == null ? "" : r.activityInfo.packageName;
        } catch (Throwable t) { h = ""; }
        sHome = h;
        return h;
    }

    private static long sysUiFlags(Object handler) {
        try {
            Object v = Xp.getObjectField(handler, "mSysUiFlags");
            return v instanceof Number ? ((Number) v).longValue() : 0L;
        } catch (Throwable t) { return 0L; }
    }

    private static Context context() {
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); }
        catch (Throwable t) { return null; }
    }
}
