package io.github.pixelsuite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.UserHandle;
import android.widget.Toast;
import java.lang.reflect.Method;

final class ForceStop {
    private ForceStop() {}

    static final String ACTION = "io.github.pixelsuite.FORCE_STOP_APP";
    private static final String EXTRA_PACKAGE = "pixelsuite_package", EXTRA_USER = "pixelsuite_user";
    private static final String[] NEVER = {"android", "com.android.systemui", "com.android.phone", PrivacyLock.LAUNCHER};
    private static volatile int launcherUid = -1;
    private static volatile Context system;
    private static Method stop;

    static String target(Object info) {
        try {
            if (info == null || DrawerFolders.isFolder(info)) return null;
            boolean app = LauncherFeatures.type("model.data.AppInfo").isInstance(info);
            if (!app) { Object kind = LauncherFeatures.get(info, "itemType"); if (!(kind instanceof Number) || ((Number) kind).intValue() != 0) return null; }
            ComponentName c = (ComponentName) LauncherFeatures.call(info, "getTargetComponent");
            if (c == null) return null;
            String pkg = c.getPackageName();
            for (String n : NEVER) if (n.equals(pkg)) return null;
            Activity a = LauncherFeatures.activity.get();
            if (a != null) {
                ApplicationInfo ai = a.getPackageManager().getApplicationInfo(pkg, 0);
                if ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) return null;
            }
            return pkg;
        } catch (Throwable t) { return null; }
    }

    static void request(Object info) {
        try {
            Activity a = LauncherFeatures.activity.get();
            String pkg = target(info);
            if (a == null || pkg == null) return;
            UserHandle user = (UserHandle) LauncherFeatures.get(info, "user");
            int id = user == null ? 0 : user.hashCode();
            a.sendBroadcast(new Intent(ACTION).setPackage("android").putExtra(EXTRA_PACKAGE, pkg).putExtra(EXTRA_USER, id));
            CharSequence label = pkg;
            try { Object title = LauncherFeatures.get(info, "title"); if (title instanceof CharSequence && ((CharSequence) title).length() > 0) label = (CharSequence) title; } catch (Throwable ignored) { }
            Toast.makeText(a, I18n.cs(label + " stopped"), Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { }
    }

    static void installSystem(ClassLoader cl) {
        try {
            Class<?> ams = Xp.findClass("com.android.server.am.ActivityManagerService", cl);
            Xp.hookAllMethods(ams, "systemReady", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { system = (Context) Xp.getObjectField(p.thisObject, "mContext"); } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(ams, "broadcastIntentWithFeature", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Intent i = null; for (Object o : p.args) if (o instanceof Intent) { i = (Intent) o; break; }
                        if (i == null || !ACTION.equals(i.getAction())) return;
                        p.setResult(0);
                        int caller = Binder.getCallingUid(), home = launcher();
                        if (home < 0 || caller % 100000 != home % 100000) return;
                        String pkg = i.getStringExtra(EXTRA_PACKAGE);
                        int user = i.getIntExtra(EXTRA_USER, caller / 100000);
                        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return;
                        for (String n : NEVER) if (n.equals(pkg)) return;
                        long token = Binder.clearCallingIdentity();
                        try {
                            try { if ((system.getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_PERSISTENT) != 0) return; } catch (PackageManager.NameNotFoundException ignored) { }
                            if (stop == null) {
                                for (Class<?> k = p.thisObject.getClass(); k != null && stop == null; k = k.getSuperclass())
                                    for (Method m : k.getDeclaredMethods())
                                        if (m.getName().equals("forceStopPackage") && m.getParameterTypes().length == 2 && m.getParameterTypes()[0] == String.class && m.getParameterTypes()[1] == int.class) { stop = m; break; }
                                if (stop == null) return;
                                stop.setAccessible(true);
                            }
                            stop.invoke(p.thisObject, pkg, user);
                        } finally { Binder.restoreCallingIdentity(token); }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable error) { }
    }

    private static int launcher() {
        int uid = launcherUid;
        if (uid >= 0 || system == null) return uid;
        long token = Binder.clearCallingIdentity();
        try { uid = system.getPackageManager().getApplicationInfo(PrivacyLock.LAUNCHER, 0).uid; } catch (Throwable ignored) { }
        finally { Binder.restoreCallingIdentity(token); }
        launcherUid = uid;
        return uid;
    }
}
