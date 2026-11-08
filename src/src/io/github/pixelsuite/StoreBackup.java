package io.github.pixelsuite;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class StoreBackup {
    private StoreBackup() {}

    static final String KEY = "pixelsuite_store_backup";
    static final String ALLOW_KEY = "pixelsuite_store_allow";
    static final String DIR = "/sdcard/PixelSuite/APK backups";
    static final String RESTORE_PROP = "debug.pixelsuite.restore";
    private static final long ALLOW_MS = 60 * 60 * 1000L;
    private static final int KEEP = 3;

    static final class Saved {
        final long code; final String version; final long kb;
        final boolean data; final long dataKb;
        Saved(long code, String version) { this(code, version, 0); }
        Saved(long code, String version, long kb) { this(code, version, kb, false, 0); }
        Saved(long code, String version, long kb, boolean data, long dataKb) { this.code = code; this.version = version; this.kb = kb; this.data = data; this.dataKb = dataKb; }
    }

    private static boolean safe(String pkg) { return pkg != null && pkg.matches("[A-Za-z0-9._]+"); }

    private static String dir(String pkg) { return "\"" + DIR + "/" + pkg + "\""; }

    static boolean backup(String pkg, String version, long code) {
        if (!safe(pkg)) return false;
        String d = "\"" + DIR + "/" + pkg + "/" + code + "\"";
        String v = version == null ? "" : version.replace("'", "").replace("\"", "").replace("$", "").replace("`", "");
        List<String> out = run("set -e; rm -rf " + d + "; mkdir -p " + d + "; "
                + "for p in $(pm path " + pkg + " | sed 's/^package://'); do cp \"$p\" " + d + "/; done; "
                + "printf '%s' '" + v + "' > " + d + "/version.txt; "
                + "ls -1 " + dir(pkg) + " | grep -E '^[0-9]+$' | sort -rn | tail -n +" + (KEEP + 1) + " | while read x; do rm -rf " + dir(pkg) + "/\"$x\"; done; "
                + "for x in $(ls -1 " + AppDataBackup.DIR + "/" + pkg + " 2>/dev/null); do [ -d " + dir(pkg) + "/\"$x\" ] || rm -rf " + AppDataBackup.DIR + "/" + pkg + "/\"$x\"; done; "
                + "[ -f " + d + "/base.apk ] && echo ok", 180);
        return out != null && out.contains("ok");
    }

    static List<Saved> list(String pkg) {
        List<Saved> l = new ArrayList<Saved>();
        if (!safe(pkg)) return l;
        List<String> out = run("for x in $(ls -1 " + dir(pkg) + " 2>/dev/null | grep -E '^[0-9]+$' | sort -rn); do "
                + "[ -f " + dir(pkg) + "/\"$x\"/base.apk ] && echo \"$x|$(cat " + dir(pkg) + "/\"$x\"/version.txt 2>/dev/null)\"; done; true", 30);
        if (out == null) return l;
        for (String line : out) {
            int bar = line.indexOf('|');
            if (bar <= 0) continue;
            try { l.add(new Saved(Long.parseLong(line.substring(0, bar)), line.substring(bar + 1))); } catch (Throwable ignored) { }
        }
        return l;
    }

    static java.util.Map<String, List<Saved>> all() {
        java.util.Map<String, List<Saved>> m = new java.util.HashMap<String, List<Saved>>();
        List<String> out = run("cd \"" + DIR + "\" 2>/dev/null || exit 0; for p in *; do [ -d \"$p\" ] || continue; "
                + "for x in $(ls -1 \"$p\" | grep -E '^[0-9]+$' | sort -rn); do [ -f \"$p/$x/base.apk\" ] || continue; "
                + "q=" + AppDataBackup.DIR + "/$p/$x; if [ -f $q/meta.txt ] && grep -q '^done=1' $q/meta.txt; then dd=1; dk=$(du -sk $q 2>/dev/null | cut -f1); else dd=0; dk=0; fi; "
                + "echo \"$p|$x|$(du -sk \"$p/$x\" 2>/dev/null | cut -f1)|$dd|$dk|$(cat \"$p/$x/version.txt\" 2>/dev/null)\"; done; done; true", 60);
        if (out == null) return m;
        for (String line : out) {
            String[] f = line.split("\\|", 6);
            if (f.length < 2 || !safe(f[0])) continue;
            try {
                List<Saved> l = m.get(f[0]);
                if (l == null) { l = new ArrayList<Saved>(); m.put(f[0], l); }
                long kb = 0, dk = 0; try { kb = f.length > 2 ? Long.parseLong(f[2].trim()) : 0; } catch (Throwable ignored) { }
                try { dk = f.length > 4 ? Long.parseLong(f[4].trim()) : 0; } catch (Throwable ignored) { }
                l.add(new Saved(Long.parseLong(f[1]), f.length > 5 ? f[5] : "", kb, f.length > 3 && "1".equals(f[3].trim()), dk));
            } catch (Throwable ignored) { }
        }
        return m;
    }

    static String restore(String pkg, long code) {
        if (!safe(pkg)) return "Unknown app";
        String d = "\"" + DIR + "/" + pkg + "/" + code + "\"";
        String t = "/data/local/tmp/pixelsuite-restore-" + pkg;
        List<String> out = run("setprop " + RESTORE_PROP + " " + pkg + "; "
                + "s=$(pm install-create -r -d --user 0 2>&1); id=$(echo \"$s\" | sed -n 's/.*\\[\\([0-9][0-9]*\\)\\].*/\\1/p'); r=\"$s\"; "
                + "if [ -n \"$id\" ]; then ok=1; for f in " + d + "/*.apk; do z=$(stat -c %s \"$f\"); "
                + "w=$(cat \"$f\" | pm install-write -S $z $id \"${f##*/}\" - 2>&1); case \"$w\" in *Success*) ;; *) ok=0; r=\"$w\"; break;; esac; done; "
                + "if [ $ok = 1 ]; then r=$(pm install-commit $id 2>&1); else pm install-abandon $id >/dev/null 2>&1; fi; fi; "
                + "case \"$r\" in *Success*|*'Failure ['*) ;; *) rm -rf " + t + "; "
                + "if mkdir -p " + t + " && cp " + d + "/*.apk " + t + "/; then chmod 755 " + t + "; chmod 644 " + t + "/*.apk; "
                + "q=$(pm install -r -d --user 0 " + t + "/*.apk 2>&1); case \"$q\" in *Success*|*'Failure ['*) r=\"$q\";; esac; fi; rm -rf " + t + ";; esac; "
                + "setprop " + RESTORE_PROP + " ''; echo \"$r\"; true", 300);
        if (out == null) return "Root isn't available";
        for (String line : out) if (line.contains("Success")) return null;
        for (String line : out) if (!line.isEmpty()) return line;
        return "It didn't install";
    }

    static boolean delete(String pkg, long code) {
        if (!safe(pkg)) return false;
        String d = "\"" + DIR + "/" + pkg + "/" + code + "\"";
        List<String> out = run("rm -rf " + d + " " + AppDataBackup.DIR + "/" + pkg + "/" + code + "; rmdir " + dir(pkg) + " " + AppDataBackup.DIR + "/" + pkg + " 2>/dev/null; [ -e " + d + " ] || echo ok", 60);
        return out != null && out.contains("ok");
    }

    static java.io.File readableCopy(android.content.Context c, String pkg, long code) {
        if (!safe(pkg)) return null;
        java.io.File f = new java.io.File(c.getCacheDir(), "ps-meta-" + pkg + ".apk");
        int uid = android.os.Process.myUid();
        List<String> out = run("cat \"" + DIR + "/" + pkg + "/" + code + "/base.apk\" > '" + f.getAbsolutePath() + "' && chown " + uid + ":" + uid + " '" + f.getAbsolutePath() + "' && chmod 600 '" + f.getAbsolutePath() + "' && echo ok", 60);
        return out != null && out.contains("ok") && f.isFile() ? f : null;
    }

    static boolean openUpdate(String pkg, long code) {
        if (!safe(pkg)) return false;
        long until = System.currentTimeMillis() + ALLOW_MS;
        return run("settings put secure " + ALLOW_KEY + " '" + pkg + ":" + code + ":" + until + "'; "
                + "am force-stop " + StoreBlocker.PLAY + "; "
                + "am start -a android.intent.action.VIEW -d 'market://details?id=" + pkg + "' -p " + StoreBlocker.PLAY + " >/dev/null 2>&1; echo ok", 30) != null;
    }

    static void endUpdate(String pkg) {
        if (!safe(pkg)) return;
        run("v=$(settings get secure " + ALLOW_KEY + "); case \"$v\" in " + pkg + ":*) settings delete secure " + ALLOW_KEY + ";; esac; true", 15);
    }

    static boolean isSafe(String pkg) { return safe(pkg); }

    private static List<String> run(String command, int seconds) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            final Process proc = p;
            final List<String> out = new ArrayList<String>();
            Thread reader = new Thread(new Runnable() { public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    for (String line; (line = r.readLine()) != null; ) synchronized (out) { out.add(line.trim()); }
                } catch (Throwable ignored) { }
            }});
            reader.start();
            if (!p.waitFor(seconds, TimeUnit.SECONDS)) return null;
            reader.join(2000);
            synchronized (out) { return p.exitValue() == 0 ? new ArrayList<String>(out) : null; }
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
