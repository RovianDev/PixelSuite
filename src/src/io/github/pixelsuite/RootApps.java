package io.github.pixelsuite;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RootApps {
    private RootApps() {}

    static final class App {
        final ApplicationInfo info;
        final String label, version;
        final long code;
        App(ApplicationInfo info, String label, String version, long code) { this.info = info; this.label = label; this.version = version; this.code = code; }
    }

    static final class Scan {
        final Set<String> packages = new HashSet<String>();
        final Set<String> launchable = new HashSet<String>();
        final Map<String, String> paths = new HashMap<String, String>();
        boolean ok;
    }

    static Scan scan() {
        Scan s = new Scan();
        try {
            List<String> out = Root.run("cmd package query-activities --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER; echo __PIXELSUITE_PATHS__; pm list packages -f --user 0; true");
            if (out == null) return s;
            boolean paths = false;
            for (String line : out) {
                if (line.equals("__PIXELSUITE_PATHS__")) { paths = true; continue; }
                if (!paths) { if (line.startsWith("packageName=")) s.launchable.add(line.substring(12).trim()); }
                else if (line.startsWith("package:")) {
                    int eq = line.lastIndexOf('=');
                    if (eq > 8) { String pkg = line.substring(eq + 1).trim(); s.packages.add(pkg); s.paths.put(pkg, line.substring(8, eq)); }
                }
            }
            s.ok = !s.packages.isEmpty();
        } catch (Throwable ignored) { }
        return s;
    }

    static List<App> missing(PackageManager pm, Scan scan, Set<String> known, boolean launcherOnly, Set<String> always, String what) {
        List<App> out = new ArrayList<App>();
        if (scan == null || !scan.ok) return out;
        for (String pkg : scan.packages) {
            if (known.contains(pkg)) continue;
            if (launcherOnly && !scan.launchable.contains(pkg) && (always == null || !always.contains(pkg))) continue;
            String apk = scan.paths.get(pkg);
            PackageInfo pi = null;
            try { pi = pm.getPackageArchiveInfo(apk, 0); } catch (Throwable ignored) { }
            if (pi == null || pi.applicationInfo == null) continue;
            ApplicationInfo ai = pi.applicationInfo;
            ai.packageName = pkg;
            ai.sourceDir = apk;
            ai.publicSourceDir = apk;
            if (apk != null && apk.startsWith("/system") || apk != null && apk.startsWith("/product") || apk != null && apk.startsWith("/vendor")) ai.flags |= ApplicationInfo.FLAG_SYSTEM;
            String label;
            try { label = ai.loadLabel(pm).toString(); } catch (Throwable t) { label = pkg; }
            long code = pi.versionCode;
            if (Build.VERSION.SDK_INT >= 28) try { code = ((Number) PackageInfo.class.getMethod("getLongVersionCode").invoke(pi)).longValue(); } catch (Throwable ignored) { }
            out.add(new App(ai, label, pi.versionName == null ? "Unknown" : pi.versionName, code));
        }
        return out;
    }
}
