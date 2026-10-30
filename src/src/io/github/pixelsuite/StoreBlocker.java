package io.github.pixelsuite;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class StoreBlocker {
    private StoreBlocker() {}

    static final String PLAY = "com.android.vending";
    static final String KEY = "pixelsuite_store_block";
    static final String UNLOCK_KEY = "pixelsuite_store_unlock_oem";
    static final String[] CORE = {
            "com.google.android.apps.nexuslauncher",
            "com.google.android.dialer",
            "com.google.android.apps.photos",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.pixel.creativeassistant",
            "com.google.android.apps.nbu.files",
            "com.google.android.inputmethod.latin",
            "com.google.android.settings.intelligence",
            "com.android.chrome",
            "com.google.android.apps.magazines",
            "com.google.android.apps.wear.companion",
            "org.futo.inputmethod.latin",
            "org.futo.inputmethod.latin.playstore",
            "org.futo.inputmethod.latin.unstable" };
    private static final long FAR_AHEAD = Integer.MAX_VALUE - 1;

    private static volatile Set<String> sBlocked;

    static Set<String> blocked(ContentResolver cr) {
        Set<String> out = new HashSet<String>();
        String list = null, unlock = null;
        try { list = Settings.Secure.getString(cr, KEY); unlock = Settings.Secure.getString(cr, UNLOCK_KEY); } catch (Throwable ignored) { }
        if (list == null) Collections.addAll(out, CORE);
        else for (String p : list.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        if (!"1".equals(unlock)) Collections.addAll(out, CORE);
        return Collections.unmodifiableSet(out);
    }
    static Set<String> guarded(ContentResolver cr) {
        String unlock = null;
        try { unlock = Settings.Secure.getString(cr, UNLOCK_KEY); } catch (Throwable ignored) { }
        if ("1".equals(unlock)) return Collections.emptySet();
        Set<String> out = new HashSet<String>(); Collections.addAll(out, CORE);
        return Collections.unmodifiableSet(out);
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> apm = Class.forName("android.app.ApplicationPackageManager");
            Xp.Callback fix = new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { try { ahead(p.getResult()); } catch (Throwable ignored) { } }
            };
            for (String m : new String[]{"getPackageInfo", "getPackageInfoAsUser", "getInstalledPackages", "getInstalledPackagesAsUser",
                    "getApplicationInfo", "getApplicationInfoAsUser", "getInstalledApplications", "getInstalledApplicationsAsUser"})
                Xp.hookAllMethods(apm, m, fix);
        } catch (Throwable error) { return; }
    }

    private static Set<String> set() {
        Set<String> s = sBlocked;
        if (s != null) return s;
        try {
            final Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c == null) return Collections.emptySet();
            s = blocked(c.getContentResolver());
            sBlocked = s;
            ContentObserver o = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean self) { sBlocked = blocked(c.getContentResolver()); }
            };
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, o);
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(UNLOCK_KEY), false, o);
            return s;
        } catch (Throwable t) { return Collections.emptySet(); }
    }

    private static void ahead(Object r) {
        if (r == null) return;
        if (r instanceof List) { for (Object o : (List<?>) r) ahead(o); return; }
        if (r instanceof PackageInfo) {
            PackageInfo pi = (PackageInfo) r;
            if (pi.packageName == null || !set().contains(pi.packageName)) return;
            try { PackageInfo.class.getMethod("setLongVersionCode", long.class).invoke(pi, FAR_AHEAD); } catch (Throwable t) { pi.versionCode = (int) FAR_AHEAD; }
            if (pi.applicationInfo != null) ahead(pi.applicationInfo);
        } else if (r instanceof ApplicationInfo) {
            ApplicationInfo ai = (ApplicationInfo) r;
            if (ai.packageName == null || !set().contains(ai.packageName)) return;
            for (String f : new String[]{"longVersionCode", "versionCode"}) {
                try { java.lang.reflect.Field x = ApplicationInfo.class.getDeclaredField(f); x.setAccessible(true);
                      if (x.getType() == long.class) x.setLong(ai, FAR_AHEAD); else x.setInt(ai, (int) FAR_AHEAD); } catch (Throwable ignored) { }
            }
        }
    }
}
