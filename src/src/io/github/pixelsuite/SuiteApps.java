package io.github.pixelsuite;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

final class SuiteApps {
    static final String DIALER = "io.github.roviandev.phone";
    static final String ALIAS = "io.github.pixelsuite.ScanApp";
    static final String LOOK_KEY = "pixelsuite_scan_look", QUALITY_KEY = "pixelsuite_scan_quality";
    static final String[] LOOK_NAMES = {"Auto", "Original", "Enhanced", "Gray", "Black & white", "Lighten"};
    static final String[] QUALITY_NAMES = {"Standard", "High"};
    private static final String[] LOOKS = {"auto", "original", "enhanced", "gray", "bw", "lighten"};

    interface Toggle { void set(boolean on); }

    private SuiteApps() { }

    private static int number(Context c, String key) {
        try { return Settings.Secure.getInt(c.getContentResolver(), key, 1); } catch (Throwable t) { return 1; }
    }

    static String look(Context c) { return LOOKS[Math.max(0, Math.min(LOOKS.length - 1, number(c, LOOK_KEY) - 1))]; }

    static int edge(Context c) { return number(c, QUALITY_KEY) == 2 ? 3000 : 2200; }

    static boolean pdfInstalled(Context c) {
        try { c.getPackageManager().getApplicationInfo(PdfHost.APP, 0); return true; } catch (Throwable t) { return false; }
    }

    static boolean pdfOn(Context c) {
        try { return c.getPackageManager().getApplicationInfo(PdfHost.APP, 0).enabled; } catch (Throwable t) { return false; }
    }

    private static void dropAlias(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            ComponentName alias = new ComponentName(c.getPackageName(), ALIAS);
            if (pm.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        } catch (Throwable ignored) { }
    }

    static boolean openPdf(Context c, boolean scanner) {
        if (!pdfInstalled(c) || !pdfOn(c)) return false;
        try {
            c.startActivity(new Intent().setClassName(PdfHost.APP, scanner ? "io.github.pixelsuite.SearchScan" : "io.github.pixelsuite.PdfApp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) { return false; }
    }

    static boolean scanShown(Context c) {
        if (pdfInstalled(c)) return pdfOn(c);
        try { return c.getPackageManager().getComponentEnabledSetting(new ComponentName(c.getPackageName(), ALIAS)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED; }
        catch (Throwable t) { return false; }
    }

    static void showScan(Context c, boolean on) {
        if (on && !pdfInstalled(c) && PatronAccess.unlocked()) install(c, PdfHost.APP);
        if (pdfInstalled(c)) {
            dropAlias(c);
            Root.run("pm " + (on ? "enable " : "disable-user --user 0 ") + PdfHost.APP);
            return;
        }
        try {
            c.getPackageManager().setComponentEnabledSetting(new ComponentName(c.getPackageName(), ALIAS),
                    on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            c.getSharedPreferences("suite_apps", 0).edit().putBoolean("scan_seeded", true).apply();
        } catch (Throwable ignored) { }
    }

    static void sync(Context c) {
        try {
            if (c == null) return;
            if (pdfInstalled(c)) { dropAlias(c); return; }
            if (!PatronAccess.initialized()) return;
            PackageManager pm = c.getPackageManager();
            ComponentName alias = new ComponentName(c.getPackageName(), ALIAS);
            android.content.SharedPreferences p = c.getSharedPreferences("suite_apps", 0);
            if (!PatronAccess.unlocked()) {
                if (pm.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
                    p.edit().putBoolean("scan_seeded", false).apply();
                }
                return;
            }
            if (!p.getBoolean("scan_seeded", false)) {
                if (pm.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                    pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                p.edit().putBoolean("scan_seeded", true).apply();
            }
        } catch (Throwable ignored) { }
    }

    static void membership(Context c, boolean allowed) {
        try {
            if (c == null) return;
            android.content.SharedPreferences p = c.getSharedPreferences("suite_apps", 0);
            firstSeen(c, p);
            java.util.Set<String> off = new java.util.HashSet<String>(p.getStringSet("lapse_off", java.util.Collections.<String>emptySet()));
            StringBuilder cmd = new StringBuilder();
            if (allowed) {
                if (off.isEmpty()) return;
                for (String pkg : off) cmd.append("pm enable ").append(pkg).append(" >/dev/null 2>&1;");
                if (off.contains(DIALER) && p.getBoolean("lapse_dialer_role", false))
                    cmd.append("cmd role add-role-holder --user 0 android.app.role.DIALER ").append(DIALER).append(" >/dev/null 2>&1;");
                if (Root.run(cmd.toString()) == null) return;
                p.edit().remove("lapse_off").remove("lapse_dialer_role").apply();
                return;
            }
            boolean role = p.getBoolean("lapse_dialer_role", false);
            for (String pkg : new String[]{DIALER, PdfHost.APP}) {
                if (!enabled(c, pkg)) continue;
                if (DIALER.equals(pkg)) {
                    if (inCall(c)) continue;
                    role = role || DIALER.equals(defaultDialer(c));
                }
                off.add(pkg);
                cmd.append("pm disable-user --user 0 ").append(pkg).append(" >/dev/null 2>&1;");
            }
            if (cmd.length() == 0) return;
            if (Root.run(cmd.toString()) == null) return;
            p.edit().putStringSet("lapse_off", off).putBoolean("lapse_dialer_role", role).apply();
        } catch (Throwable ignored) { }
    }

    private static void firstSeen(Context c, android.content.SharedPreferences p) {
        java.util.Set<String> seen = new java.util.HashSet<String>(p.getStringSet("seen_apps", java.util.Collections.<String>emptySet()));
        StringBuilder cmd = new StringBuilder();
        boolean changed = false, fresh = !Defaults.legacy();
        for (String pkg : new String[]{DIALER, PdfHost.APP}) {
            if (seen.contains(pkg)) continue;
            try { c.getPackageManager().getApplicationInfo(pkg, 0); } catch (Throwable missing) { continue; }
            if (fresh && enabled(c, pkg)) {
                if (DIALER.equals(pkg) && inCall(c)) continue;
                cmd.append("pm disable-user --user 0 ").append(pkg).append(" >/dev/null 2>&1;");
            }
            seen.add(pkg);
            changed = true;
        }
        if (!changed) return;
        if (cmd.length() > 0 && Root.run(cmd.toString()) == null) return;
        p.edit().putStringSet("seen_apps", seen).apply();
    }

    private static boolean enabled(Context c, String pkg) {
        try { return c.getPackageManager().getApplicationInfo(pkg, 0).enabled; } catch (Throwable t) { return false; }
    }

    private static String defaultDialer(Context c) {
        try { return ((android.telecom.TelecomManager) c.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage(); } catch (Throwable t) { return null; }
    }

    private static boolean inCall(Context c) {
        try {
            int mode = ((android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).getMode();
            return mode == android.media.AudioManager.MODE_IN_CALL || mode == android.media.AudioManager.MODE_IN_COMMUNICATION || mode == android.media.AudioManager.MODE_RINGTONE;
        } catch (Throwable t) { return true; }
    }

    static boolean dialerInstalled(Context c) {
        try { c.getPackageManager().getApplicationInfo(DIALER, 0); return true; } catch (Throwable t) { return false; }
    }

    static boolean dialerOn(Context c) {
        try { ApplicationInfo a = c.getPackageManager().getApplicationInfo(DIALER, 0); return a.enabled; } catch (Throwable t) { return false; }
    }

    static boolean dialer(boolean on) { return Root.run("pm " + (on ? "enable " : "disable-user --user 0 ") + DIALER) != null; }

    static boolean openDialer(Context c) {
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(DIALER);
            if (i == null) return false;
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) { return false; }
    }

    static boolean dialerSettings() { return Root.run("am start -n " + DIALER + "/.SettingsActivity") != null; }

    static final String MODULE = "/data/adb/modules/pixelsuiteapps";

    private static java.io.File unpacked(Context c) {
        try {
            int id = c.getResources().getIdentifier("suite_apps", "raw", c.getPackageName());
            if (id == 0) return null;
            android.content.pm.PackageInfo me = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            java.io.File base = c.getCodeCacheDir();
            String name = "suite-apps-" + me.lastUpdateTime;
            java.io.File dir = new java.io.File(base, name), done = new java.io.File(dir, ".done");
            if (done.isFile()) return dir;
            java.io.File[] old = base.listFiles();
            if (old != null) for (java.io.File f : old) if (f.getName().startsWith("suite-apps-")) wipe(f);
            if (!dir.mkdirs() && !dir.isDirectory()) return null;
            String root = dir.getCanonicalPath() + java.io.File.separator;
            java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(c.getResources().openRawResource(id));
            try {
                byte[] buf = new byte[65536];
                for (java.util.zip.ZipEntry e; (e = z.getNextEntry()) != null; ) {
                    java.io.File f = new java.io.File(dir, e.getName());
                    if (!f.getCanonicalPath().startsWith(root)) continue;
                    if (e.isDirectory()) { f.mkdirs(); continue; }
                    f.getParentFile().mkdirs();
                    java.io.FileOutputStream o = new java.io.FileOutputStream(f);
                    try { for (int n; (n = z.read(buf)) > 0; ) o.write(buf, 0, n); } finally { o.close(); }
                }
            } finally { z.close(); }
            if (!new java.io.File(dir, "payload/RovianPhone.apk").isFile()) { wipe(dir); return null; }
            done.createNewFile();
            return dir;
        } catch (Throwable t) { return null; }
    }

    private static void wipe(java.io.File f) {
        java.io.File[] in = f.listFiles();
        if (in != null) for (java.io.File x : in) wipe(x);
        f.delete();
    }

    private static java.io.File apk(java.io.File dir, String pkg) { return new java.io.File(dir, DIALER.equals(pkg) ? "payload/RovianPhone.apk" : "payload/PixelPDF.apk"); }

    private static long code(android.content.pm.PackageInfo i) {
        if (i == null) return -1;
        long v = i.versionCode;
        if (android.os.Build.VERSION.SDK_INT >= 28) try { v = ((Number) android.content.pm.PackageInfo.class.getMethod("getLongVersionCode").invoke(i)).longValue(); } catch (Throwable ignored) { }
        return v;
    }

    static long installedVersion(Context c, String pkg) {
        try { return code(c.getPackageManager().getPackageInfo(pkg, 0)); } catch (Throwable t) { return -1; }
    }

    static long bundledVersion(Context c, String pkg) {
        java.io.File dir = unpacked(c);
        if (dir == null) return -1;
        java.io.File f = apk(dir, pkg);
        if (!f.isFile()) return -1;
        try {
            android.content.pm.PackageInfo i = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
            return i != null && pkg.equals(i.packageName) ? code(i) : -1;
        } catch (Throwable t) { return -1; }
    }

    private static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    static boolean install(Context c, String pkg) {
        java.io.File dir = unpacked(c);
        if (dir == null) return false;
        java.io.File f = apk(dir, pkg);
        if (!f.isFile()) return false;
        boolean dialer = DIALER.equals(pkg);
        String tmp = "/data/local/tmp/pixelsuite-" + (dialer ? "dialer" : "pdf") + ".apk";
        StringBuilder cmd = new StringBuilder();
        if (dialer) {
            cmd.append("M=").append(MODULE).append("; ")
               .append("[ -d /data/adb/modules/rovianphone ] && touch /data/adb/modules/rovianphone/remove; ")
               .append("mkdir -p $M && cp -rf ").append(q(dir.getPath())).append("/. $M/ || exit 1; ")
               .append("rm -f $M/.done $M/remove $M/disable $M/payload/PUT-APK-HERE.txt; ")
               .append("chown -R 0:0 $M; chmod 755 $M $M/payload $M/webroot 2>/dev/null; ")
               .append("chmod 644 $M/module.prop $M/payload/* $M/webroot/* 2>/dev/null; chmod 755 $M/*.sh; ");
        }
        cmd.append("cp -f ").append(q(f.getPath())).append(" ").append(tmp).append(" && chmod 644 ").append(tmp).append(" || exit 1; ")
           .append("pm install -r ").append(tmp).append(" >/dev/null 2>&1; r=$?; rm -f ").append(tmp).append("; [ $r = 0 ] || exit 1; ")
           .append("pm enable ").append(pkg).append(" >/dev/null 2>&1; exit 0");
        if (Root.run(cmd.toString()) == null) return false;
        try {
            android.content.SharedPreferences p = c.getSharedPreferences("suite_apps", 0);
            java.util.Set<String> seen = new java.util.HashSet<String>(p.getStringSet("seen_apps", java.util.Collections.<String>emptySet()));
            seen.add(pkg);
            p.edit().putStringSet("seen_apps", seen).apply();
        } catch (Throwable ignored) { }
        return installedVersion(c, pkg) >= 0;
    }

    static void freshen(Context c) {
        try {
            if (c == null || !PatronAccess.unlocked()) return;
            for (String pkg : new String[]{DIALER, PdfHost.APP}) {
                long have = installedVersion(c, pkg);
                if (have < 0 || !enabled(c, pkg)) continue;
                if (DIALER.equals(pkg) && inCall(c)) continue;
                if (bundledVersion(c, pkg) > have) install(c, pkg);
            }
        } catch (Throwable ignored) { }
    }
}
