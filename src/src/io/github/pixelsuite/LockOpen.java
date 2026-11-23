package io.github.pixelsuite;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;

final class LockOpen {
    private LockOpen() {}

    private static volatile boolean sOpen, sPanel;
    private static volatile Set<String> sApps = Collections.emptySet();

    private static volatile String sSession;

    private static volatile int sSetup = -1, sReported = -1;
    private static Context sContext;
    private static boolean sInstalled, sWatching;
    private static Handler sWorker;

    static void install(ClassLoader cl) throws Throwable {
        if (sInstalled) return;
        Class<?> record = Xp.findClass("com.android.server.wm.ActivityRecord", cl);
        if (record == null) throw new ClassNotFoundException("com.android.server.wm.ActivityRecord");
        Set<Object> hooks = Xp.hookAllMethods(record, "canShowWhenLocked", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (!sPanel) return;
                String session = sSession;
                if (session == null) return;
                if (Boolean.TRUE.equals(p.getResult())) return;
                Object activity = p.thisObject != null ? p.thisObject : (p.args != null && p.args.length > 0 ? p.args[0] : null);
                ComponentName component = component(activity);
                if (component == null) return;
                String pkg = component.getPackageName();
                if (sSetup >= 0 && "android".equals(pkg) && WidgetPanel.CONFIG_PROXY.equals(component.getClassName())) {
                    if (allowed(session) || setupAllowed(session)) p.setResult(Boolean.TRUE);
                    return;
                }
                int slash = session.indexOf('/');
                if (slash >= 0) {
                    if (WidgetPanel.LOCK_PICKER.equals(component.getClassName()) && session.equals(pkg + "/" + component.getClassName())) p.setResult(Boolean.TRUE);
                    return;
                }
                if (!pkg.equals(session)) return;

                if (!allowed(pkg) && !(sSetup >= 0 && setupAllowed(pkg))) return;
                p.setResult(Boolean.TRUE);
            }
        });
        if (hooks.isEmpty()) throw new NoSuchMethodException("ActivityRecord.canShowWhenLocked");
        sInstalled = true;
        android.os.HandlerThread thread = new android.os.HandlerThread("PixelSuiteLockOpen");
        thread.start();
        sWorker = new Handler(thread.getLooper());
        try { setupResults(cl, record); } catch (Throwable t) { android.util.Log.w("PixelSuite", "lock open: setup results", t); }

        try {
            Class<?> ams = Xp.findClass("com.android.server.am.ActivityManagerService", cl);
            Xp.hookAllMethods(ams, "finishBooting", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { sWorker.post(LockOpen::settle); }
            });
        } catch (Throwable ignored) { }
        retry(0);
    }

    private static void retry(final int attempt) {
        if (sWatching || attempt > 24) return;
        sWorker.postDelayed(() -> { settle(); retry(attempt + 1); }, 5000);
    }

    private static synchronized void settle() {
        if (sWatching) return;
        Context c = sContext != null ? sContext : systemContext();
        if (c == null) return;
        sContext = c;
        try {
            ContentObserver observer = new ContentObserver(sWorker) {
                @Override public void onChange(boolean self) { load(sContext); }
            };
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(WidgetPanel.LOCK_KEY), false, observer);
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(WidgetPanel.LOCK_OPEN_KEY), false, observer);
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(WidgetPanel.LOCK_APPS_KEY), false, observer);
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(WidgetPanel.LOCK_SESSION_KEY), false, observer);
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(WidgetPanel.LOCK_CONFIG_KEY), false, observer);

            c.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context context, android.content.Intent intent) {
                    sSession = null;
                    try { Settings.Secure.putString(sContext.getContentResolver(), WidgetPanel.LOCK_SESSION_KEY, null); } catch (Throwable ignored) { }
                }
            }, new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF), null, sWorker);
            sWatching = true;
        } catch (Throwable ignored) { return; }
        try { Settings.Secure.putString(c.getContentResolver(), WidgetPanel.LOCK_SESSION_KEY, null); } catch (Throwable ignored) { }
        try { Settings.Secure.putString(c.getContentResolver(), WidgetPanel.LOCK_CONFIG_KEY, null); } catch (Throwable ignored) { }
        load(c);
    }

    private static void load(Context c) {
        if (c == null) return;
        try { sPanel = Settings.Secure.getInt(c.getContentResolver(), WidgetPanel.LOCK_KEY, 0) == 1; } catch (Throwable t) { sPanel = false; }
        try { sOpen = Settings.Secure.getInt(c.getContentResolver(), WidgetPanel.LOCK_OPEN_KEY, 0) == 1; } catch (Throwable t) { sOpen = false; }
        try { sApps = MiscOptions.parse(Settings.Secure.getString(c.getContentResolver(), WidgetPanel.LOCK_APPS_KEY)); }
        catch (Throwable t) { sApps = Collections.emptySet(); }
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), WidgetPanel.LOCK_SESSION_KEY);
            int bar = v == null ? -1 : v.indexOf('|');
            String what = v == null || v.isEmpty() ? null : bar >= 0 ? v.substring(0, bar) : v;
            sSession = what == null || what.isEmpty() || "null".equals(what) ? null : what;
        } catch (Throwable t) { sSession = null; }
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), WidgetPanel.LOCK_CONFIG_KEY);
            int bar = v == null ? -1 : v.indexOf('|');
            sSetup = v == null || v.isEmpty() || "null".equals(v) ? -1 : Integer.parseInt(bar >= 0 ? v.substring(0, bar) : v);
        } catch (Throwable t) { sSetup = -1; }
    }

    private static boolean setupAllowed(String pkg) {
        if (pkg == null || pkg.indexOf('/') >= 0) return false;
        for (String always : WidgetPanel.LOCK_ALWAYS_ASK) if (always.equals(pkg)) return false;
        return true;
    }

    private static boolean allowed(String pkg) {
        if (pkg == null || pkg.indexOf('/') >= 0 || !sOpen || !sApps.contains(pkg)) return false;
        for (String always : WidgetPanel.LOCK_ALWAYS_ASK) if (always.equals(pkg)) return false;
        return true;
    }

    private static void setupResults(ClassLoader cl, Class<?> record) throws Throwable {
        Class<?> client = Xp.findClass("com.android.server.wm.ActivityClientController", cl);
        if (client == null) throw new ClassNotFoundException("com.android.server.wm.ActivityClientController");
        java.lang.reflect.Method found = null;
        for (String name : new String[]{"forToken", "forTokenLocked"}) {
            try { found = record.getDeclaredMethod(name, android.os.IBinder.class); found.setAccessible(true); break; } catch (Throwable ignored) { }
        }
        if (found == null) throw new NoSuchMethodException("ActivityRecord.forToken");
        final java.lang.reflect.Method forToken = found;
        Set<Object> hooks = Xp.hookAllMethods(client, "finishActivity", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                final int watch = sSetup;
                if (watch < 0 || watch == sReported || p.args == null || p.args.length < 2 || !(p.args[0] instanceof android.os.IBinder) || !(p.args[1] instanceof Integer)) return;
                try {
                    Object activity = forToken.invoke(null, p.args[0]);
                    if (activity == null) return;
                    Field f = field(activity.getClass(), "intent");
                    Object v = f != null ? f.get(activity) : null;
                    if (!(v instanceof android.content.Intent)) return;
                    android.content.Intent intent = (android.content.Intent) v;
                    ComponentName component = component(activity);
                    boolean screen = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(intent.getAction());
                    boolean proxied = component != null && "android".equals(component.getPackageName()) && WidgetPanel.CONFIG_PROXY.equals(component.getClassName());
                    if (!screen && !proxied) return;
                    if (intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1) != watch) return;
                    final int code = (Integer) p.args[1];

                    sReported = watch;
                    sWorker.post(() -> {
                        try { Settings.Secure.putString(sContext.getContentResolver(), WidgetPanel.LOCK_CONFIG_RESULT_KEY, watch + "|" + code + "|" + android.os.SystemClock.uptimeMillis()); }
                        catch (Throwable t) { android.util.Log.w("PixelSuite", "lock open: setup result", t); }
                    });
                } catch (Throwable ignored) { }
            }
        });
        if (hooks.isEmpty()) throw new NoSuchMethodException("ActivityClientController.finishActivity");
    }

    private static ComponentName component(Object activity) {
        if (activity == null) return null;
        try {
            Field f = field(activity.getClass(), "mActivityComponent");
            if (f != null) { Object v = f.get(activity); if (v instanceof ComponentName) return (ComponentName) v; }
        } catch (Throwable ignored) { }
        try {
            Field f = field(activity.getClass(), "info");
            if (f != null) { Object v = f.get(activity); if (v instanceof ActivityInfo) return new ComponentName(((ActivityInfo) v).packageName, ((ActivityInfo) v).name); }
        } catch (Throwable ignored) { }
        return null;
    }

    private static Field field(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (Throwable ignored) { }
        }
        return null;
    }

    private static Context systemContext() {
        try {
            Object thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            if (thread == null) return null;
            Object ctx = thread.getClass().getMethod("getSystemContext").invoke(thread);
            return ctx instanceof Context ? (Context) ctx : null;
        } catch (Throwable ignored) { return null; }
    }
}
