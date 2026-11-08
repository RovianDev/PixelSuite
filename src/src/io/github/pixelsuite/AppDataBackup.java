package io.github.pixelsuite;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppDataBackup {
    private AppDataBackup() {}

    static final String DIR = "/data/adb/pixelsuite/backups";
    static final String MODE_KEY = "pixelsuite_store_backup_data";

    interface Progress { void at(float fraction); }

    private static final String[] SPECIAL_OPS = {"SYSTEM_ALERT_WINDOW", "MANAGE_EXTERNAL_STORAGE", "REQUEST_INSTALL_PACKAGES", "GET_USAGE_STATS",
            "WRITE_SETTINGS", "PICTURE_IN_PICTURE", "SCHEDULE_EXACT_ALARM", "USE_FULL_SCREEN_INTENT", "RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND", "MANAGE_MEDIA"};
    private static final Set<String> SPECIAL = new HashSet<String>(Arrays.asList(SPECIAL_OPS));
    private static final Pattern PERM = Pattern.compile("^\\s+([A-Za-z0-9._]+): granted=(true|false)");
    private static final Pattern OP = Pattern.compile("^\\s*(?:Uid mode: )?([A-Z_]+): ([a-z]+)");

    private static String tarCmd() {
        return "T=tar; for b in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox; do if [ -x $b ]; then T=\"$b tar\"; break; fi; done; ";
    }

    private static String parts(String p) {
        return "ce /data/user/0/" + p + " de /data/user_de/0/" + p + " extdata /data/media/0/Android/data/" + p
                + " extmedia /data/media/0/Android/media/" + p + " extobb /data/media/0/Android/obb/" + p;
    }

    private static final String LIST = "set --; for f in .* *; do case \"$f\" in .|..|cache|code_cache) continue;; esac; "
            + "[ \"$f\" = lib ] && [ -L \"$f\" ] && continue; [ -e \"$f\" ] || [ -L \"$f\" ] || continue; set -- \"$@\" \"$f\"; done; ";

    static Map<String, long[]> sizes(List<String> packages) {
        Map<String, long[]> out = new HashMap<String, long[]>();
        StringBuilder sh = new StringBuilder();
        for (String p : packages) {
            if (!StoreBackup.isSafe(p)) continue;
            sh.append("a=0; for x in $(pm path ").append(p).append(" | sed 's/^package://'); do k=$(du -sk \"$x\" | cut -f1); a=$((a+k)); done; d=0; ")
              .append("set -- ").append(parts(p)).append("; while [ $# -ge 2 ]; do w=$2; shift 2; [ -d \"$w\" ] || continue; ")
              .append("k=$( ( cd \"$w\" && ").append(LIST).append("[ $# -gt 0 ] && du -sk \"$@\" ) 2>/dev/null | { s=0; while read q rest; do s=$((s+q)); done; echo $s; } ); d=$((d+${k:-0})); done; ")
              .append("echo \"").append(p).append("|$a|$d\"; ");
        }
        sh.append("true");
        List<String> lines = run(sh.toString(), 120, null);
        if (lines == null) return out;
        for (String l : lines) {
            String[] f = l.split("\\|");
            if (f.length < 3) continue;
            try { out.put(f[0], new long[]{Long.parseLong(f[1].trim()), Long.parseLong(f[2].trim())}); } catch (Throwable ignored) { }
        }
        return out;
    }

    static String backup(String pkg, long code, final Progress progress) {
        if (!StoreBackup.isSafe(pkg)) return "Unknown app";
        String d = DIR + "/" + pkg + "/" + code;
        String sh = "umask 077; " + tarCmd() + "P=" + pkg + "; D=" + d + "; "
                + "rm -rf $D; mkdir -p $D || { echo \"PS_ERR Couldn't make the backup folder\"; exit 1; }; chmod 700 /data/adb/pixelsuite " + DIR + " " + DIR + "/$P $D; "
                + "am force-stop $P; U=$(stat -c %u /data/user/0/$P 2>/dev/null); [ -n \"$U\" ] || { echo \"PS_ERR Its data wasn't found\"; exit 1; }; "
                + "echo \"uid=$U\" > $D/meta.txt; "
                + "tot=0; set -- " + parts(pkg) + "; while [ $# -ge 2 ]; do w=$2; shift 2; [ -d \"$w\" ] || continue; "
                + "k=$( cd \"$w\" && " + LIST + "[ $# -gt 0 ] && find \"$@\" | wc -l ); tot=$((tot+${k:-0})); done; echo \"PS_TOTAL $tot\"; "
                + "part() { n=$1; w=$2; [ -d \"$w\" ] || return 0; cd \"$w\" || return 0; echo \"dir.$n=$(stat -c '%u:%g:%a' .)\" >> $D/meta.txt; " + LIST
                + "[ $# -gt 0 ] || return 0; c=$(find \"$@\" | wc -l); echo \"count.$n=$c\" >> $D/meta.txt; "
                + "$T --numeric-owner -cvf $D/$n.tar \"$@\" 2>&1 || { echo \"PS_ERR Couldn't save $n\"; return 1; }; }; "
                + "set -- " + parts(pkg) + "; while [ $# -ge 2 ]; do n=$1; w=$2; shift 2; ( part $n \"$w\" ) || exit 1; done; "
                + "chmod -R go-rwx $D; echo PS_OK";
        final int[] total = {0}, done = {0};
        List<String> out = run(sh, 3600, new Line() { public void line(String l) {
            if (l.startsWith("PS_TOTAL ")) { try { total[0] = Integer.parseInt(l.substring(9).trim()); } catch (Throwable ignored) { } return; }
            if (l.startsWith("PS_")) return;
            done[0]++;
            if (progress != null && total[0] > 0) progress.at(Math.min(.96f, done[0] / (float) total[0]));
        }});
        String error = error(out);
        if (error != null) { run("rm -rf " + d, 60, null); return error; }
        StringBuilder meta = new StringBuilder();
        List<String> dump = run("dumpsys package " + pkg, 60, null);
        if (dump != null) {
            boolean runtime = false;
            for (String l : dump) {
                if (l.contains("runtime permissions:")) { runtime = true; continue; }
                if (runtime && !l.startsWith("        ") && !l.trim().isEmpty() && !PERM.matcher(l).find()) runtime = false;
                Matcher m = PERM.matcher(l);
                if (runtime && m.find()) meta.append("perm.").append(m.group(1)).append('=').append("true".equals(m.group(2)) ? 1 : 0).append('\n');
            }
        }
        List<String> ops = run("cmd appops get " + pkg, 30, null);
        if (ops != null) for (String l : ops) { Matcher m = OP.matcher(l); if (m.find() && SPECIAL.contains(m.group(1)) && m.group(2).matches("allow|ignore|deny|default|foreground")) meta.append("op.").append(m.group(1)).append('=').append(m.group(2)).append('\n'); }
        List<String> idle = run("dumpsys deviceidle whitelist", 30, null);
        if (idle != null) for (String l : idle) if (l.contains("," + pkg + ",")) { meta.append("idle=1\n"); break; }
        meta.append("done=1\n");
        List<String> w = run("cat >> " + d + "/meta.txt << 'PS_META'\n" + meta + "PS_META\necho ok", 30, null);
        if (w == null || !w.contains("ok")) { run("rm -rf " + d, 60, null); return "Couldn't finish the backup"; }
        if (progress != null) progress.at(1f);
        return null;
    }

    static boolean has(String pkg, long code) {
        if (!StoreBackup.isSafe(pkg)) return false;
        List<String> out = run("grep -q '^done=1' " + DIR + "/" + pkg + "/" + code + "/meta.txt && echo ok", 15, null);
        return out != null && out.contains("ok");
    }

    static long installedCode(String pkg) {
        List<String> out = run("pm list packages --show-versioncode " + pkg, 20, null);
        if (out != null) for (String l : out) {
            if (!l.startsWith("package:" + pkg + " ")) continue;
            int i = l.indexOf("versionCode:");
            if (i > 0) try { return Long.parseLong(l.substring(i + 12).trim()); } catch (Throwable ignored) { }
        }
        return -1;
    }

    static String restore(String pkg, long code, boolean withData, final Progress progress) {
        if (!StoreBackup.isSafe(pkg)) return "Unknown app";
        final boolean[] installing = {true};
        final float[] at = {0f};
        Thread tick = null;
        if (installedCode(pkg) != code) {
            tick = new Thread(new Runnable() { public void run() {
                long start = System.currentTimeMillis();
                while (installing[0]) {
                    float t = (System.currentTimeMillis() - start) / 8000f;
                    at[0] = (withData ? .3f : .9f) * (1f - (float) Math.pow(1 - Math.min(1f, t), 2.6));
                    if (progress != null) progress.at(at[0]);
                    try { Thread.sleep(60); } catch (InterruptedException e) { return; }
                }
            }});
            tick.start();
            String error = StoreBackup.restore(pkg, code);
            installing[0] = false;
            try { tick.join(200); } catch (InterruptedException ignored) { }
            if (error != null) return error;
        }
        installing[0] = false;
        if (!withData) { if (progress != null) progress.at(1f); return null; }
        final float base = Math.max(at[0], tick == null ? 0f : .3f);
        String d = DIR + "/" + pkg + "/" + code;
        String sh = "umask 0; " + tarCmd() + "P=" + pkg + "; D=" + d + "; M=$D/meta.txt; "
                + "grep -q '^done=1' $M 2>/dev/null || { echo \"PS_ERR Its saved data is incomplete\"; exit 1; }; "
                + "am force-stop $P; N=$(stat -c %u /data/user/0/$P 2>/dev/null); [ -n \"$N\" ] || { echo \"PS_ERR The app isn't installed\"; exit 1; }; "
                + "O=$(sed -n 's/^uid=//p' $M); [ -n \"$O\" ] || O=$N; OA=$((O % 100000)); OB=$((O - OA)); NA=$((N % 100000)); NB=$((N - NA)); "
                + "tot=0; for k in $(sed -n 's/^count\\.[a-z]*=//p' $M); do tot=$((tot+k)); done; echo \"PS_TOTAL $tot\"; "
                + "map() { v=$1; if [ \"$v\" = \"$O\" ]; then echo $N; return; fi; for o in 20000 30000 50000; do if [ \"$v\" = $((OB+OA+o)) ]; then echo $((NB+NA+o)); return; fi; done; echo $v; }; "
                + "remap() { w=$1; find \"$w\" -user 0 -exec chown -h $N {} + 2>/dev/null; find \"$w\" -group 0 -exec chgrp -h $N {} + 2>/dev/null; "
                + "[ \"$O\" = \"$N\" ] && return 0; find \"$w\" -user $O -exec chown -h $N {} +; find \"$w\" -group $O -exec chgrp -h $N {} +; "
                + "for o in 20000 30000 50000; do find \"$w\" -group $((OB+OA+o)) -exec chgrp -h $((NB+NA+o)) {} +; done; }; "
                + "put() { n=$1; w=$2; tf=$D/$n.tar; [ -f $tf ] || return 0; "
                + "if [ ! -d \"$w\" ]; then m=$(sed -n \"s/^dir\\.$n=//p\" $M); mkdir -p \"$w\" || return 1; "
                + "if [ -n \"$m\" ]; then u=${m%%:*}; r=${m#*:}; g=${r%%:*}; a=${r#*:}; chown $(map $u):$(map $g) \"$w\"; chmod $a \"$w\"; fi; fi; "
                + "set -- $(ls -Zd \"$w\"); x=$1; cd \"$w\" || return 1; " + LIST + "[ $# -gt 0 ] && rm -rf -- \"$@\"; "
                + "$T --numeric-owner -xvf $tf 2>&1 || { echo \"PS_ERR Couldn't put back $n\"; return 1; }; remap \"$w\"; "
                + "case $n in ce|de) restorecon -RF \"$w\" >/dev/null 2>&1;; *) chcon -hR \"$x\" \"$w\" 2>/dev/null || restorecon -RF \"$w\" >/dev/null 2>&1;; esac; return 0; }; "
                + "set -- " + parts(pkg) + "; while [ $# -ge 2 ]; do n=$1; w=$2; shift 2; ( put $n \"$w\" ) || exit 1; done; "
                + "sed -n 's/^perm\\.\\([^=]*\\)=1$/\\1/p' $M | while read q; do pm grant $P $q 2>/dev/null; done; "
                + "sed -n 's/^perm\\.\\([^=]*\\)=0$/\\1/p' $M | while read q; do pm revoke $P $q 2>/dev/null; done; "
                + "sed -n 's/^op\\.\\([A-Z_]*\\)=\\([a-z]*\\)$/\\1 \\2/p' $M | while read q v; do appops set $P $q $v 2>/dev/null; done; "
                + "grep -q '^idle=1' $M && cmd deviceidle whitelist +$P >/dev/null 2>&1; "
                + "am force-stop $P; echo PS_OK";
        final int[] total = {0}, done = {0};
        List<String> out = run(sh, 3600, new Line() { public void line(String l) {
            if (l.startsWith("PS_TOTAL ")) { try { total[0] = Integer.parseInt(l.substring(9).trim()); } catch (Throwable ignored) { } return; }
            if (l.startsWith("PS_")) return;
            done[0]++;
            if (progress != null && total[0] > 0) progress.at(base + (.97f - base) * Math.min(1f, done[0] / (float) total[0]));
        }});
        String error = error(out);
        if (error != null) return error;
        if (progress != null) progress.at(1f);
        return null;
    }

    private static String error(List<String> out) {
        if (out == null) return "Root isn't available, or it took too long";
        for (String l : out) if (l.startsWith("PS_ERR ")) return l.substring(7);
        if (!out.contains("PS_OK")) return "It didn't finish";
        return null;
    }

    interface Line { void line(String l); }

    private static final String NS = "S=$(cat); for b in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox; do "
            + "if [ -x $b ] && $b nsenter -t 1 -m -- /system/bin/true </dev/null 2>/dev/null; then exec $b nsenter -t 1 -m -- /system/bin/sh -c \"$S\" </dev/null; fi; done; "
            + "if nsenter -t 1 -m -- /system/bin/true </dev/null 2>/dev/null; then exec nsenter -t 1 -m -- /system/bin/sh -c \"$S\" </dev/null; fi; exec /system/bin/sh -c \"$S\" </dev/null";

    private static List<String> run(String command, int seconds, final Line each) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", NS});
            { java.io.OutputStream in = p.getOutputStream(); in.write(command.getBytes("UTF-8")); in.write('\n'); in.close(); }
            final Process proc = p;
            final List<String> out = new ArrayList<String>();
            Thread reader = new Thread(new Runnable() { public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    for (String line; (line = r.readLine()) != null; ) {
                        String t = line.trim();
                        if (t.startsWith("PS_") || each == null) synchronized (out) { out.add(t); }
                        if (each != null) try { each.line(t); } catch (Throwable ignored) { }
                    }
                } catch (Throwable ignored) { }
            }});
            reader.start();
            if (!p.waitFor(seconds, TimeUnit.SECONDS)) return null;
            reader.join(5000);
            synchronized (out) { return new ArrayList<String>(out); }
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
