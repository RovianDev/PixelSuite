package io.github.pixelsuite;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;

final class ScreenshotWarm {
    private ScreenshotWarm() {}
    static final String KEY = "pixelsuite_fast_screenshot";
    private static final String KEEP = "io.github.pixelsuite.KEEP_SCREENSHOT_READY";
    private static final String ENDED = "io.github.pixelsuite.SCREENSHOT_ENDED";
    private static final String SENDER = "android.permission.STATUS_BAR_SERVICE";
    private static final String SYSTEMUI = "com.android.systemui", PROCESS = ":screenshot";

    private static Context system; private static Handler worker;
    private static ServiceConnection connection; private static volatile ComponentName target;
    private static volatile boolean armed;

    static void installSystem(ClassLoader cl) {
        try {
            Xp.hookAllMethods(Xp.findClass("com.android.server.am.ActivityManagerService", cl), "systemReady", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { start((Context) Xp.getObjectField(p.thisObject, "mContext")); } catch (Throwable error) { }
                }
            });
        } catch (Throwable error) { }
        try {
            Xp.hookAllMethods(Class.forName("android.app.ContextImpl"), "bindServiceCommon", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        for (int i = 0; i < p.args.length; i++) {
                            if (!(p.args[i] instanceof Intent)) continue;
                            Intent in = (Intent) p.args[i];
                            ComponentName c = in.getComponent();
                            if (c == null || target == null || !c.equals(target) || KEEP.equals(in.getAction())) return;
                            if (armed && connection == null && worker != null) worker.postDelayed(new Runnable() { public void run() { synchronized (ScreenshotWarm.class) { if (armed) bind(); } } }, 1500);
                            for (Object a : p.args) if (a instanceof ServiceConnection) synchronized (real) { real.put((ServiceConnection) a, Boolean.TRUE); }
                            if (connection == null) return;
                            Intent fresh = new Intent(in);
                            Intent.class.getMethod("setIdentifier", String.class).invoke(fresh, "pixelsuite-shot-" + (++shots));
                            p.args[i] = fresh;
                            return;
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable error) { }
        try {
            Xp.hookAllMethods(Class.forName("android.app.ContextImpl"), "unbindService", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length != 1 || !(p.args[0] instanceof ServiceConnection)) return;
                        boolean was; synchronized (real) { was = real.remove(p.args[0]) != null; }
                        if (was && worker != null) worker.postDelayed(new Runnable() { public void run() { recycle(); } }, 400);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable error) { }
    }
    private static int shots;
    private static final java.util.WeakHashMap<ServiceConnection, Boolean> real = new java.util.WeakHashMap<ServiceConnection, Boolean>();

    private static synchronized void recycle() {
        synchronized (real) { if (!real.isEmpty()) return; }
        if (connection == null) return;
        unbind();
        worker.postDelayed(new Runnable() { public void run() { synchronized (ScreenshotWarm.class) { if (armed) bind(); } } }, 600);
    }

    static void installScreenshotProcess(ClassLoader cl) {
        try {
            Class<?> service = Class.forName("com.android.systemui.screenshot.TakeScreenshotService", false, cl);
            Xp.hookAllMethods(service, "onBind", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length == 1 && p.args[0] instanceof Intent && KEEP.equals(((Intent) p.args[0]).getAction())) p.setResult(new android.os.Binder());
                }
            });
            Xp.hookAllMethods(service, "onUnbind", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length != 1 || !(p.args[0] instanceof Intent)) return;
                    if (KEEP.equals(((Intent) p.args[0]).getAction())) { p.setResult(Boolean.FALSE); return; }
                    try { ((Context) p.thisObject).sendBroadcast(new Intent(ENDED).setPackage("android")); } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(service, "onRebind", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length == 1 && p.args[0] instanceof Intent && KEEP.equals(((Intent) p.args[0]).getAction())) p.setResult(null);
                }
            });
        } catch (Throwable error) { }
    }

    private static synchronized void start(final Context c) {
        if (system != null || c == null) return;
        system = c;
        HandlerThread t = new HandlerThread("PixelSuiteShotWarm"); t.start();
        worker = new Handler(t.getLooper());
        try {
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, new ContentObserver(worker) {
                @Override public void onChange(boolean self) { update(); }
            });
        } catch (Throwable ignored) { }
        try {
            android.content.BroadcastReceiver ended = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (worker != null) worker.postDelayed(new Runnable() { public void run() { recycle(); } }, 400);
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter(ENDED);
            try {
                Context.class.getMethod("registerReceiver", android.content.BroadcastReceiver.class, android.content.IntentFilter.class, String.class, Handler.class, int.class)
                        .invoke(c, ended, filter, SENDER, worker, 2);
            } catch (NoSuchMethodException older) { c.registerReceiver(ended, filter, SENDER, worker); }
        } catch (Throwable ignored) { }
        worker.post(new Runnable() { public void run() { target = pick(); update(); } });
        worker.postDelayed(new Runnable() { public void run() { synchronized (ScreenshotWarm.class) { if (armed) bind(); } } }, 30_000);
    }

    private static boolean on() {
        try { return !"0".equals(Settings.Secure.getString(system.getContentResolver(), KEY)); } catch (Throwable t) { return true; }
    }

    private static synchronized void update() {
        armed = on();
        if (armed) { if (system != null && worker != null && android.os.SystemClock.elapsedRealtime() > 60_000) bind(); }
        else unbind();
    }

    private static ComponentName pick() {
        try {
            PackageInfo info = system.getPackageManager().getPackageInfo(SYSTEMUI, PackageManager.GET_SERVICES | 0x200 );
            ServiceInfo best = null, shot = null;
            if (info.services != null) for (ServiceInfo s : info.services) {
                if (s.processName == null || !s.processName.endsWith(PROCESS)) continue;
                if (s.name.endsWith("TakeScreenshotService")) { shot = s; continue; }
                if (best == null || s.name.contains("CrossProfile")) best = s;
            }
            if (best == null) best = shot;
            return best == null ? null : new ComponentName(SYSTEMUI, best.name);
        } catch (Throwable error) { return null; }
    }

    private static void bind() {
        if (connection != null) return;
        ComponentName c = target;
        if (c == null) { return; }
        ServiceConnection conn = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { }
            @Override public void onServiceDisconnected(ComponentName name) { }
            public void onBindingDied(ComponentName name) {
                worker.post(new Runnable() { public void run() { synchronized (ScreenshotWarm.class) { unbind(); } } });
            }
        };
        try {
            Intent i = new Intent(KEEP).setComponent(c);
            boolean ok;
            try {
                Object user = android.os.UserHandle.class.getMethod("of", int.class).invoke(null, 0);
                ok = (Boolean) Context.class.getMethod("bindServiceAsUser", Intent.class, ServiceConnection.class, int.class, android.os.UserHandle.class)
                        .invoke(system, i, conn, Context.BIND_AUTO_CREATE, user);
            } catch (Throwable older) { ok = system.bindService(i, conn, Context.BIND_AUTO_CREATE); }
            if (ok) { connection = conn; }
        } catch (Throwable error) { }
    }

    private static void unbind() {
        ServiceConnection conn = connection; connection = null;
        if (conn == null) return;
        try { system.unbindService(conn); } catch (Throwable ignored) { }
    }
}
