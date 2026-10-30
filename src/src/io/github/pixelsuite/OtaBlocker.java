package io.github.pixelsuite;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

final class OtaBlocker {
    private OtaBlocker() {}

    static final String KEY = "pixelsuite_ota_block";
    static final String RESET_KEY = "pixelsuite_ota_reset_lock";
    static final String GMS = "com.google.android.gms";
    private static final String PREFIX = "com.google.android.gms.update.";
    private static final int AVAILABLE_NEVER = 3 , GREYED = 5 ;

    private static volatile boolean sOn;
    private static volatile Context sSystem;
    private static final ThreadLocal<Boolean> sOurs = new ThreadLocal<Boolean>();

    static void installSystem(ClassLoader cl) {
        try {
            Xp.hookAllMethods(Xp.findClass("com.android.server.am.ActivityManagerService", cl), "systemReady", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { start((Context) Xp.getObjectField(p.thisObject, "mContext")); } catch (Throwable error) { }
                }
            });
            for (String name : new String[]{"com.android.server.pm.IPackageManagerBase", "com.android.server.pm.PackageManagerService$IPackageManagerImpl"}) {
                Class<?> k; try { k = Xp.findClass(name, cl); } catch (Throwable t) { continue; }
                if (k == null) continue;
                Xp.hookAllMethods(k, "setComponentEnabledSetting", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (!sOn || Boolean.TRUE.equals(sOurs.get()) || p.args.length < 2 || !(p.args[0] instanceof ComponentName) || !(p.args[1] instanceof Integer)) return;
                        ComponentName c = (ComponentName) p.args[0];
                        int state = (Integer) p.args[1];
                        if (GMS.equals(c.getPackageName()) && c.getClassName().startsWith(PREFIX) && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) p.setResult(null);
                    }
                });
            }
        } catch (Throwable error) { }
    }

    private static synchronized void start(final Context c) {
        if (sSystem != null || c == null) return;
        sSystem = c;
        HandlerThread t = new HandlerThread("PixelSuiteOta"); t.start();
        final Handler h = new Handler(t.getLooper());
        final Runnable apply = new Runnable() { public void run() { apply(c); } };
        h.postDelayed(apply, 20000);
        try {
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, new ContentObserver(h) {
                @Override public void onChange(boolean self) { apply.run(); }
            });
            IntentFilter f = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED); f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addDataScheme("package");
            c.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context x, Intent i) {
                    if (i.getData() != null && GMS.equals(i.getData().getSchemeSpecificPart())) h.postDelayed(apply, 5000);
                }
            }, f, null, h);
        } catch (Throwable error) { }
    }

    private static void apply(Context c) {
        boolean on = false;
        try { on = "1".equals(Settings.Secure.getString(c.getContentResolver(), KEY)); } catch (Throwable ignored) { }
        sOn = on;
        long id = Binder.clearCallingIdentity();
        sOurs.set(Boolean.TRUE);
        try {
            PackageManager pm = c.getPackageManager();
            int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS | 0x200 ;
            PackageInfo pi = pm.getPackageInfo(GMS, flags);
            List<ComponentInfo> all = new ArrayList<ComponentInfo>();
            if (pi.activities != null) for (ComponentInfo x : pi.activities) all.add(x);
            if (pi.services != null) for (ComponentInfo x : pi.services) all.add(x);
            if (pi.receivers != null) for (ComponentInfo x : pi.receivers) all.add(x);
            if (pi.providers != null) for (ComponentInfo x : pi.providers) all.add(x);
            int want = on ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            for (ComponentInfo x : all) {
                if (x.name == null || !x.name.startsWith(PREFIX)) continue;
                ComponentName cn = new ComponentName(GMS, x.name);
                try {
                    if (pm.getComponentEnabledSetting(cn) == want) continue;
                    pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP);
                } catch (Throwable error) { }
            }
        } catch (Throwable error) { }
        finally { sOurs.remove(); Binder.restoreCallingIdentity(id); }
    }

    static void installSettings(ClassLoader cl) {
        Xp.Callback hidden = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { if (setting(KEY)) p.setResult(AVAILABLE_NEVER); }
        };
        for (String k : new String[]{"com.google.android.settings.update.SoftwareUpdateController2", "com.google.android.settings.update.SoftwareUpdateController",
                "com.google.android.settings.update.SystemUpdatePreferenceController", "com.google.android.settings.update.GoogleSystemUpdatePreferenceController",
                "com.android.settings.system.SystemUpdatePreferenceController", "com.android.settings.system.AdditionalSystemUpdatePreferenceController"}) {
            try { Xp.hookAllMethods(Class.forName(k, false, cl), "getAvailabilityStatus", hidden); } catch (Throwable missing) { }
        }
        try {
            Xp.hookAllMethods(Class.forName("com.google.android.settings.update.SoftwareUpdateScreen", false, cl), "isAvailable", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { if (setting(KEY)) p.setResult(Boolean.FALSE); }
            });
        } catch (Throwable missing) { }
        try {
            Class<?> pref = Class.forName("androidx.preference.Preference", false, cl);
            Xp.Callback category = new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (!"software_update_category".equals(Xp.callMethod(p.thisObject, "getKey")) || !setting(KEY)) return;
                        Xp.callMethod(p.thisObject, "setVisible", false);
                    } catch (Throwable ignored) { }
                }
            };
            Xp.hookAllMethods(pref, "onAttached", category);
            Xp.hookAllMethods(pref, "onAttachedToHierarchy", category);
        } catch (Throwable missing) { }
        try {
            Class<?> reset = Class.forName("com.android.settings.system.FactoryResetPreferenceController", false, cl);
            Xp.hookAllMethods(reset, "getAvailabilityStatus", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { if (resetLocked() && Integer.valueOf(0).equals(p.getResult())) p.setResult(GREYED); }
            });
            Xp.hookAllMethods(reset, "handlePreferenceTreeClick", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) { if (resetLocked()) p.setResult(Boolean.TRUE); }
            });
        } catch (Throwable missing) { }
    }

    private static boolean resetLocked() { return setting(KEY) && setting(RESET_KEY); }
    private static boolean setting(String key) {
        try {
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return c != null && "1".equals(Settings.Secure.getString(c.getContentResolver(), key));
        } catch (Throwable t) { return false; }
    }
}
