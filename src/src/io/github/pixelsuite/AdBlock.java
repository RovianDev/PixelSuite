package io.github.pixelsuite;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AdBlock {
    private AdBlock() {}

    static final String KEY = "pixelsuite_adblock";
    static final int JOB_ID = 0x5AD0;
    static final String MOD = "/data/adb/modules/pixelsuite_adblock";
    static final String HOSTS = MOD + "/system/etc/hosts";
    static final String MARK = "# Pixel Suite ad blocker";
    private static final String PREFS = "pixelsuite_adblock";
    private static final String PKG = "io.github.roviandev.pixelsuite";
    private static final String STOCK = "127.0.0.1 localhost\n::1 ip6-localhost\n";
    private static final String[] LISTS = {
            "https://raw.githubusercontent.com/hagezi/dns-blocklists-legacy/main/hosts/light.txt",
            "https://adaway.org/hosts.txt",
    };
    private static final String[] ALLOW = { "localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback", "0.0.0.0" };

    static final class Result {
        boolean ok;
        int domains;
        boolean needsReboot;
        String message;
    }

    static boolean isOn() {
        List<String> out = Root.run("settings get secure " + KEY);
        if (out == null || out.isEmpty()) return true;
        return !"0".equals(out.get(0).trim());
    }

    static android.os.Bundle serveChrome(Context c, String key, android.os.Bundle extras) {
        int v = extras == null ? -1 : extras.getInt("value", -1);
        if (!ChromeShield.KEY_ON.equals(key) || (v != 0 && v != 1)) throw new IllegalArgumentException("Invalid Ad Blocker setting");
        android.os.Bundle out = new android.os.Bundle();
        out.putBoolean("ok", Root.run("settings put secure " + key + " " + v) != null);
        return out;
    }

    static boolean entitled(Context c) {
        try {
            List<String> out = Root.run("settings get secure " + PatronGate.KEY);
            return out != null && !out.isEmpty() && PatronGate.passOk(c, out.get(0));
        } catch (Throwable t) {
            return false;
        }
    }

    static Result setEnabled(Context c, boolean on) {
        Result r = new Result();
        if (on && !entitled(c)) {
            cancel(c);
            r.message = "The Ad blocker is part of Pixel Suite's Patreon features";
            return r;
        }
        if (Root.run("settings put secure " + KEY + (on ? " 1" : " 0")) == null) {
            r.message = "Root access is needed";
            return r;
        }
        if (on) {
            schedule(c);
            return update(c);
        }
        cancel(c);
        writeHosts(c, MARK + " (off)\n" + STOCK, r);
        if (r.ok) r.message = "Off: ads are no longer blocked";
        return r;
    }

    static void ensure(Context c) {
        if (!isOn() || !entitled(c)) { stop(c); return; }
        schedule(c);
        long age = System.currentTimeMillis() - prefs(c).getLong("updated", 0);
        if (!installed() || age > 24L * 3600 * 1000 || !Cosmetic.present(c)) update(c);
    }

    private static boolean installed() {
        List<String> head = Root.run("head -n1 " + HOSTS + " 2>/dev/null");
        return head != null && !head.isEmpty() && head.get(0).startsWith(MARK) && !head.get(0).contains("(off)");
    }

    static void stop(Context c) {
        cancel(c);
        try { if (installed()) writeHosts(c, MARK + " (off)\n" + STOCK, new Result()); } catch (Throwable ignored) { }
    }

    static Result update(Context c) {
        Result r = new Result();
        if (!entitled(c)) { stop(c); r.message = "The Ad blocker is part of Pixel Suite's Patreon features"; return r; }
        Set<String> domains = new LinkedHashSet<String>(240000);
        int listsOk = 0;
        for (String url : LISTS) if (download(url, domains)) listsOk++;
        if (Cosmetic.update(c)) { listsOk++; domains.addAll(Cosmetic.blocked()); domains.removeAll(Cosmetic.allowed()); }
        for (String a : ALLOW) domains.remove(a);
        if (listsOk == 0 || domains.size() < 1000) {
            r.message = "Couldn't download the ad lists (no connection?). The current list stays.";
            prefs(c).edit().putString("error", r.message).apply();
            return r;
        }
        StringBuilder sb = new StringBuilder(domains.size() * 28 + 256);
        sb.append(MARK).append('\n');
        sb.append("# Lists: HaGeZi Multi LIGHT (GPL-3.0, github.com/hagezi/dns-blocklists-legacy), AdAway (CC BY 3.0, adaway.org), EasyList (GPL-3.0 / CC BY-SA 3.0, easylist.to)\n");
        sb.append(STOCK);
        for (String d : domains) sb.append("0.0.0.0 ").append(d).append('\n');
        writeHosts(c, sb.toString(), r);
        if (r.ok) {
            r.domains = domains.size();
            prefs(c).edit().putInt("domains", r.domains).putLong("updated", System.currentTimeMillis()).remove("error").apply();
            r.message = r.needsReboot ? "Ready: restart the phone once to start blocking" : null;
        }
        return r;
    }

    private static boolean download(String url, Set<String> into) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(20000);
            h.setReadTimeout(60000);
            h.setRequestProperty("User-Agent", "PixelSuite");
            if (h.getResponseCode() != 200) return false;
            BufferedReader in = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"), 1 << 16);
            int before = into.size();
            for (String line; (line = in.readLine()) != null; ) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                String ip = parts[0];
                if (!ip.equals("0.0.0.0") && !ip.equals("127.0.0.1") && !ip.equals("::1") && !ip.equals("::")) continue;
                for (int i = 1; i < parts.length; i++) {
                    String d = parts[i].toLowerCase(Locale.ROOT);
                    if (validHost(d)) into.add(d);
                }
            }
            in.close();
            return into.size() > before;
        } catch (Throwable t) {
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    private static boolean validHost(String d) {
        int n = d.length();
        if (n < 3 || n > 253 || d.indexOf('.') < 0 || d.startsWith(".") || d.endsWith(".")) return false;
        for (int i = 0; i < n; i++) {
            char ch = d.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '_')) return false;
        }
        return true;
    }

    private static void writeHosts(Context c, String content, Result r) {
        File tmp = new File(c.getCacheDir(), "adblock_hosts");
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"), 1 << 16);
            w.write(content);
            w.close();
        } catch (Throwable t) {
            r.message = "Couldn't prepare the list";
            return;
        }
        File prop = new File(c.getCacheDir(), "adblock_module.prop");
        File service = new File(c.getCacheDir(), "adblock_service.sh");
        try {
            writeText(prop, "id=pixelsuite_adblock\nname=Pixel Suite ad blocker\nversion=1.0\nversionCode=1\nauthor=RovianDev\n"
                    + "description=Blocks ad and tracker domains for every app (hosts file). Managed by Pixel Suite > Google apps > Ad blocker; removes itself if Pixel Suite is uninstalled.\n");
            writeText(service, serviceScript());
        } catch (Throwable t) {
            r.message = "Couldn't prepare the module";
            return;
        }
        String s = "set -e; M=" + MOD + "; F=" + HOSTS + ";"
                + "mkdir -p $M/system/etc;"
                + "cat '" + prop.getAbsolutePath() + "' > $M/module.prop;"
                + "cat '" + service.getAbsolutePath() + "' > $M/service.sh; chmod 755 $M/service.sh;"
                + "rm -f $M/remove $M/disable;"
                + "[ -f $F ] || touch $F;"
                + "cat '" + tmp.getAbsolutePath() + "' > $F;"
                + "chmod 644 $F; chcon -R u:object_r:system_file:s0 $M/system;"
                + "set +e;" + liveMount() + ";"
                + "if nsenter -t $(pidof zygote64 zygote | cut -d' ' -f1) -m -- head -n1 /system/etc/hosts 2>/dev/null | grep -q 'Pixel Suite'; then echo LIVE; else echo REBOOT; fi";
        List<String> out = Root.run(s);
        tmp.delete();
        prop.delete();
        service.delete();
        if (out == null) {
            r.message = "Root access is needed";
            return;
        }
        r.ok = true;
        r.needsReboot = out.contains("REBOOT");
    }

    private static String liveMount() {
        return "for p in 1 $(pidof zygote64 zygote webview_zygote 2>/dev/null); do "
                + "nsenter -t $p -m -- /system/bin/sh -c \"head -n1 /system/etc/hosts | grep -q 'Pixel Suite' || mount -o bind $F /system/etc/hosts\" 2>/dev/null; "
                + "done";
    }

    private static String serviceScript() {
        return "#!/system/bin/sh\n"
                + "# Pixel Suite ad blocker\n"
                + "M=${0%/*}; F=$M/system/etc/hosts\n"
                + "[ -f \"$F\" ] || exit 0\n"
                + "chcon -R u:object_r:system_file:s0 \"$M/system\" 2>/dev/null\n"
                + liveMount() + "\n"
                + "until [ \"$(getprop sys.boot_completed)\" = 1 ]; do sleep 5; done\n"
                + "sleep 20\n"
                + "if ! pm path " + PKG + " >/dev/null 2>&1; then\n"
                + "  printf '" + MARK + " (removed)\\n127.0.0.1 localhost\\n::1 ip6-localhost\\n' > \"$F\"\n"
                + "  touch \"$M/remove\"\n"
                + "fi\n";
    }

    private static void writeText(File f, String text) throws Exception {
        FileOutputStream o = new FileOutputStream(f);
        o.write(text.getBytes("UTF-8"));
        o.close();
    }

    static boolean otherBlocker() {
        List<String> out = Root.run("[ -d /data/adb/modules/hosts ] && [ ! -f /data/adb/modules/hosts/disable ] && echo YES");
        return out != null && out.contains("YES");
    }

    static String status(Context c) {
        SharedPreferences p = prefs(c);
        String err = p.getString("error", null);
        int n = p.getInt("domains", 0);
        long t = p.getLong("updated", 0);
        if (n == 0) return err != null ? err : "Getting the ad lists…";
        long now = System.currentTimeMillis();
        String when;
        if (I18n.on()) {
            long m = Math.max(0, (now - t) / 60000), h = m / 60, d = h / 24;
            when = m < 1 ? "just now" : m < 60 ? (m == 1 ? "1 minute ago" : m + " minutes ago") : h < 24 ? (h == 1 ? "1 hour ago" : h + " hours ago") : (d == 1 ? "1 day ago" : d + " days ago");
        } else when = now - t < 60000 ? "just now" : android.text.format.DateUtils.getRelativeTimeSpanString(t, now,
                android.text.format.DateUtils.MINUTE_IN_MILLIS).toString();
        String s = "Blocking " + String.format(Locale.US, "%,d", n) + " ad domains · updated " + when;
        return err != null ? s + "\n" + err : s;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, AdBlockJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(24L * 3600 * 1000)
                    .setPersisted(true)
                    .build());
        } catch (Throwable ignored) { }
    }

    static void cancel(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(JOB_ID);
        } catch (Throwable ignored) { }
    }
}
