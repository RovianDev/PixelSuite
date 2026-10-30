package io.github.pixelsuite;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Backup {
    private Backup() {}

    static final int SAVE = 7301, OPEN = 7302;
    static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final Set<String> LEGACY = new HashSet<String>(Arrays.asList(
            "powertorch_double_press", "threefingershot_enabled", "voldowncam_enabled", "voldowncam_launch_at"));
    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList(
            "pixelsuite_dna_status", "pixelsuite_super_dark_blur_off", "pixelsuite_super_dark_turned_dark",
            "pixelsuite_token", "pixelsuite_device_id", "pixelsuite_last_alignment", "pixelsuite_align_pending", "pixelsuite_align_skipped"));

    private static final Set<String> PATREON = new HashSet<String>(Arrays.asList(AdBlock.KEY, GoogleFeedAds.KEY, CallRecording.KEY));

    static boolean wanted(String key) {
        if (key == null || SKIP.contains(key)) return false;
        if (key.startsWith("pixelsuite_patron") || key.startsWith("pixelsuite_esim")) return false;
        return key.startsWith("pixelsuite_") || LEGACY.contains(key);
    }

    private static String[][] sources(Context c) {
        return new String[][]{
                {LAUNCHER, "shared_prefs/pixelsuite_launcher.xml files/pixelsuite-icons"},
                {ShareSheet.PACKAGE, "shared_prefs/pixelsuite_share.xml"},
                {c.getPackageName(), "shared_prefs/app-label-bridge.xml"}};
    }

    static String fileName() {
        return "PixelSuite-backup-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date()) + ".json";
    }

    static String create(Context c) throws Exception {
        List<String> lines = Root.run("settings list secure");
        if (lines == null) throw new IllegalStateException("root");
        JSONObject o = new JSONObject();
        o.put("pixelsuite_backup", 1);
        try { o.put("app", c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName); } catch (Throwable ignored) { }
        o.put("created", System.currentTimeMillis());
        JSONObject secure = new JSONObject();
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq), value = line.substring(eq + 1);
            if (wanted(key) && !"null".equals(value)) secure.put(key, value);
        }
        o.put("secure", secure);
        JSONArray files = new JSONArray();
        for (String[] s : sources(c)) {
            List<String> out = Root.run("cd /data/data/" + s[0] + " 2>/dev/null || exit 0; find " + s[1] + " -type f 2>/dev/null | grep -v '/packs/' | while IFS= read -r f; do printf 'F\\t%s\\t' \"$f\"; base64 -w 0 \"$f\"; echo; done");
            if (out == null) continue;
            for (String line : out) {
                if (!line.startsWith("F\t")) continue;
                int t = line.indexOf('\t', 2);
                if (t < 0) continue;
                JSONObject f = new JSONObject();
                f.put("pkg", s[0]); f.put("path", line.substring(2, t)); f.put("data", line.substring(t + 1));
                files.put(f);
            }
        }
        o.put("files", files);
        return o.toString();
    }

    static JSONObject read(String text) {
        try {
            JSONObject o = new JSONObject(text);
            return o.optInt("pixelsuite_backup", 0) >= 1 ? o : null;
        } catch (Throwable t) { return null; }
    }

    static String summary(JSONObject o) {
        int settings = 0, icons = 0;
        boolean launcher = false, share = false;
        JSONObject secure = o.optJSONObject("secure");
        if (secure != null) for (Iterator<String> it = secure.keys(); it.hasNext(); ) if (wanted(it.next())) settings++;
        JSONArray files = o.optJSONArray("files");
        if (files != null) for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            String pkg = f.optString("pkg"), path = f.optString("path");
            if (LAUNCHER.equals(pkg)) { if (path.startsWith("files/")) icons++; else launcher = true; }
            if (ShareSheet.PACKAGE.equals(pkg)) share = true;
        }
        StringBuilder b = new StringBuilder();
        long when = o.optLong("created", 0);
        if (when > 0) b.append("Saved ").append(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new java.util.Date(when)));
        String app = o.optString("app", "");
        if (app.length() > 0) b.append(" with Pixel Suite ").append(app);
        if (b.length() > 0) b.append(".\n\n");
        b.append("• ").append(settings).append(settings == 1 ? " setting" : " settings").append('\n');
        if (launcher) b.append("• Pixel Launcher setup: drawer folders, sorting, renames and locks\n");
        if (icons > 0) b.append("• ").append(icons).append(icons == 1 ? " custom icon\n" : " custom icons\n");
        if (share) b.append("• Share menu: pinned order and hidden apps\n");
        b.append("\nYour current Pixel Suite setup will be replaced. Pixel Launcher restarts once.");
        return b.toString();
    }

    static boolean restore(Context c, JSONObject o) {
        File tmp = new File(c.getCacheDir(), "restore");
        try {
            deleteAll(tmp);
            if (!tmp.mkdirs() && !tmp.isDirectory()) return false;
            StringBuilder cmd = new StringBuilder();
            boolean patron = AdBlock.entitled(c);
            JSONObject secure = o.optJSONObject("secure");
            if (secure != null) for (Iterator<String> it = secure.keys(); it.hasNext(); ) {
                String key = it.next();
                if (!wanted(key) || !key.matches("[A-Za-z0-9_.]+")) continue;
                if (!patron && PATREON.contains(key)) { cmd.append("settings put secure ").append(key).append(" 0;\n"); continue; }
                cmd.append("settings put secure ").append(key).append(' ').append(quote(secure.optString(key))).append(";\n");
            }
            Map<String, StringBuilder> perPkg = new LinkedHashMap<String, StringBuilder>();
            Set<String> allowed = new HashSet<String>();
            for (String[] s : sources(c)) allowed.add(s[0]);
            JSONArray files = o.optJSONArray("files");
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f == null) continue;
                String pkg = f.optString("pkg"), path = f.optString("path");
                if (!allowed.contains(pkg) || !safePath(path)) continue;
                byte[] data = android.util.Base64.decode(f.optString("data"), android.util.Base64.DEFAULT);
                File part = new File(tmp, i + ".bin");
                FileOutputStream out = new FileOutputStream(part);
                try { out.write(data); } finally { out.close(); }
                StringBuilder p = perPkg.get(pkg);
                if (p == null) { p = new StringBuilder(); perPkg.put(pkg, p); }
                p.append("mkdir -p \"$d/").append(parent(path)).append("\"; cp '").append(part.getAbsolutePath()).append("' \"$d/").append(path).append("\";\n");
            }
            for (Map.Entry<String, StringBuilder> e : perPkg.entrySet()) {
                String pkg = e.getKey();
                cmd.append("d=/data/data/").append(pkg).append("; if [ -d \"$d\" ]; then\n");
                if (!c.getPackageName().equals(pkg)) cmd.append("am force-stop ").append(pkg).append(";\n");
                if (LAUNCHER.equals(pkg)) cmd.append("[ -d \"$d/files/pixelsuite-icons\" ] && find \"$d/files/pixelsuite-icons\" -maxdepth 1 -type f -delete;\n");
                cmd.append(e.getValue());
                cmd.append("u=$(stat -c %u \"$d\"); chown -R $u:$u \"$d/shared_prefs\"; [ -d \"$d/files/pixelsuite-icons\" ] && chown -R $u:$u \"$d/files/pixelsuite-icons\";\n");
                cmd.append("find \"$d/shared_prefs\" -name 'pixelsuite_*.xml' -exec chmod 660 {} +; find \"$d/shared_prefs\" -name 'app-label-bridge.xml' -exec chmod 660 {} +; restorecon -R \"$d/shared_prefs\" \"$d/files\" 2>/dev/null;\n");
                cmd.append("fi\n");
            }
            cmd.append("true");
            boolean ok = Root.run(cmd.toString()) != null;
            if (!patron) {
                try { AdBlock.stop(c); } catch (Throwable ignored) { }
                try { PatronAccess.reconcile(c); } catch (Throwable ignored) { }
            }
            return ok;
        } catch (Throwable t) {
            return false;
        } finally {
            deleteAll(tmp);
        }
    }

    private static boolean safePath(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("..") || path.contains("'") || path.contains("\"") || path.contains("$") || path.contains("`")) return false;
        return path.equals("shared_prefs/pixelsuite_launcher.xml") || path.equals("shared_prefs/pixelsuite_share.xml")
                || path.equals("shared_prefs/app-label-bridge.xml") || path.startsWith("files/pixelsuite-icons/");
    }

    private static String parent(String path) { int s = path.lastIndexOf('/'); return s < 0 ? "." : path.substring(0, s); }

    private static String quote(String v) { return "'" + v.replace("'", "'\\''") + "'"; }

    private static void deleteAll(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        f.delete();
    }
}
