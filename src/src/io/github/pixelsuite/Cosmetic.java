package io.github.pixelsuite;

import android.content.Context;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Cosmetic {
    private Cosmetic() {}

    static final String AUTHORITY = "io.github.roviandev.pixelsuite.cosmetic";
    static final String LIST = "https://easylist.to/easylist/easylist.txt";
    static final String MIRROR = "https://raw.githubusercontent.com/easylist/easylist/gh-pages/easylist.txt";
    private static final String FILE = "cosmetic_rules.txt";

    private static long sLoaded = -1;
    private static List<String> sGeneric = new ArrayList<String>();
    private static Map<String, List<String>> sSpecific = new HashMap<String, List<String>>();
    private static Map<String, List<String>> sExcept = new HashMap<String, List<String>>();
    private static Map<String, List<String>> sNoGeneric = new HashMap<String, List<String>>();
    private static String[] sGenericParts = new String[0];
    static final int PART = 3000;

    private static Set<String> sBlocked = new LinkedHashSet<String>(), sAllowed = new LinkedHashSet<String>();
    private static Set<String> sBlocking, sAllowing;

    static Set<String> blocked() { return sBlocked; }
    static Set<String> allowed() { return sAllowed; }

    static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    static boolean present(Context c) { File f = file(c); return f.isFile() && f.length() > 10000; }

    static boolean update(Context c) {
        List<String> generic = new ArrayList<String>(16000);
        List<String> pairs = new ArrayList<String>(12000);
        if (!download(LIST, generic, pairs) && !download(MIRROR, generic, pairs)) return false;
        if (generic.size() < 1000) return false;
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"), 1 << 16);
            for (String s : generic) { w.write("G\t"); w.write(s); w.write('\n'); }
            for (String s : pairs) { w.write(s); w.write('\n'); }
            w.close();
            File dst = file(c);
            if (!tmp.renameTo(dst)) { tmp.delete(); return false; }
            return true;
        } catch (Throwable t) {
            tmp.delete();
            return false;
        }
    }

    private static boolean download(String url, List<String> generic, List<String> pairs) {
        HttpURLConnection h = null;
        generic.clear(); pairs.clear();
        sBlocking = new LinkedHashSet<String>(50000); sAllowing = new LinkedHashSet<String>();
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(20000);
            h.setReadTimeout(60000);
            h.setRequestProperty("User-Agent", "PixelSuite");
            if (h.getResponseCode() != 200) return false;
            BufferedReader in = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"), 1 << 16);
            Set<String> seen = new LinkedHashSet<String>();
            for (String line; (line = in.readLine()) != null; ) parse(line.trim(), seen, pairs);
            in.close();
            generic.addAll(seen);
            sBlocked = sBlocking; sAllowed = sAllowing;
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    private static void parse(String line, Set<String> generic, List<String> pairs) {
        if (line.isEmpty() || line.charAt(0) == '!' || line.charAt(0) == '[') return;
        if (line.startsWith("@@||")) {
            String rest = line.substring(4);
            int d = rest.indexOf('$');
            String options = d < 0 ? "" : rest.substring(d + 1), domain = d < 0 ? rest : rest.substring(0, d);
            if (domain.endsWith("^")) domain = domain.substring(0, domain.length() - 1);
            domain = domain.toLowerCase(Locale.ROOT);
            if (!domain(domain)) return;
            if (options.isEmpty()) { if (sAllowing != null) sAllowing.add(domain); return; }
            for (String o : options.split(",")) if (o.equals("generichide") || o.equals("ghide") || o.equals("elemhide") || o.equals("ehide")) { pairs.add("N\t" + domain + "\t-"); return; }
            return;
        }
        if (line.startsWith("||") && line.endsWith("^") && line.indexOf('$') < 0) {
            String domain = line.substring(2, line.length() - 1).toLowerCase(Locale.ROOT);
            if (domain(domain) && sBlocking != null) sBlocking.add(domain);
            return;
        }
        if (line.contains("#?#") || line.contains("#$#") || line.contains("#@?#") || line.contains("#@$#") || line.contains("#%#") || line.contains("##^") || line.contains("##+js")) return;
        boolean except = false;
        int at = line.indexOf("#@#");
        if (at >= 0) except = true; else at = line.indexOf("##");
        if (at < 0) return;
        String domains = line.substring(0, at), selector = line.substring(at + (except ? 3 : 2)).trim();
        if (!usable(selector)) return;
        if (domains.isEmpty()) { if (!except) generic.add(selector); return; }
        boolean positive = false;
        for (String d : domains.split(",")) {
            d = d.trim().toLowerCase(Locale.ROOT);
            if (d.isEmpty() || d.indexOf('*') >= 0 || d.indexOf('/') >= 0) continue;
            if (d.charAt(0) == '~') { if (!except) pairs.add("X\t" + d.substring(1) + "\t" + selector); continue; }
            positive = true;
            pairs.add((except ? "X\t" : "S\t") + d + "\t" + selector);
        }
        if (!positive && !except) generic.add(selector);
    }

    private static boolean domain(String d) {
        int n = d.length();
        if (n < 3 || n > 253 || d.indexOf('.') < 0 || d.startsWith(".") || d.endsWith(".")) return false;
        for (int i = 0; i < n; i++) {
            char ch = d.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '_')) return false;
        }
        return true;
    }

    private static boolean usable(String s) {
        if (s.isEmpty() || s.length() > 500 || s.indexOf('{') >= 0 || s.indexOf('}') >= 0 || s.indexOf('\n') >= 0) return false;
        String l = s.toLowerCase(Locale.ROOT);
        return !(l.contains(":-abp-") || l.contains(":has-text(") || l.contains(":xpath(") || l.contains(":matches-css") || l.contains(":upward(")
                || l.contains(":remove(") || l.contains(":style(") || l.contains(":min-text-length(") || l.contains(":watch-attr(")
                || l.contains(":matches-path(") || l.contains(":matches-attr(") || l.contains(":others(") || l.contains(":nth-ancestor("));
    }

    private static synchronized void load(Context c) {
        File f = file(c);
        long stamp = f.isFile() ? f.lastModified() : 0;
        if (stamp == sLoaded) return;
        List<String> generic = new ArrayList<String>(16000);
        Map<String, List<String>> specific = new HashMap<String, List<String>>(), except = new HashMap<String, List<String>>(), noGeneric = new HashMap<String, List<String>>();
        if (stamp != 0) {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 1 << 16);
                for (String line; (line = in.readLine()) != null; ) {
                    if (line.length() < 3 || line.charAt(1) != '\t') continue;
                    char kind = line.charAt(0);
                    if (kind == 'G') { generic.add(line.substring(2)); continue; }
                    int tab = line.indexOf('\t', 2);
                    if (tab < 0) continue;
                    String host = line.substring(2, tab), sel = line.substring(tab + 1);
                    Map<String, List<String>> into = kind == 'S' ? specific : kind == 'X' ? except : kind == 'N' ? noGeneric : null;
                    if (into == null) continue;
                    List<String> l = into.get(host);
                    if (l == null) { l = new ArrayList<String>(2); into.put(host, l); }
                    l.add(sel);
                }
                in.close();
            } catch (Throwable t) { generic.clear(); specific.clear(); except.clear(); noGeneric.clear(); }
        }
        int n = (generic.size() + PART - 1) / PART;
        String[] parts = new String[n];
        for (int i = 0; i < n; i++) {
            StringBuilder b = new StringBuilder(PART * 24);
            for (int j = i * PART; j < Math.min(generic.size(), (i + 1) * PART); j++) b.append(generic.get(j)).append('\n');
            parts[i] = b.toString();
        }
        sGeneric = generic; sSpecific = specific; sExcept = except; sNoGeneric = noGeneric; sGenericParts = parts; sLoaded = stamp;
    }

    static Bundle generic(Context c, String part) {
        load(c);
        int i = 0;
        try { if (part != null) i = Integer.parseInt(part); } catch (NumberFormatException ignored) { }
        String[] parts = sGenericParts;
        Bundle b = new Bundle();
        b.putString("rules", i >= 0 && i < parts.length ? parts[i] : "");
        b.putInt("parts", parts.length);
        b.putLong("stamp", sLoaded);
        return b;
    }

    static Bundle host(Context c, String host) {
        load(c);
        StringBuilder show = new StringBuilder(), hide = new StringBuilder();
        boolean generic = true;
        if (host != null) {
            String h = host.toLowerCase(Locale.ROOT);
            while (h.indexOf('.') > 0) {
                if (sNoGeneric.containsKey(h)) generic = false;
                List<String> s = sSpecific.get(h), x = sExcept.get(h);
                if (s != null) for (String v : s) show.append(v).append('\n');
                if (x != null) for (String v : x) hide.append(v).append('\n');
                h = h.substring(h.indexOf('.') + 1);
            }
        }
        Bundle b = new Bundle();
        b.putString("rules", show.toString());
        b.putString("except", hide.toString());
        b.putBoolean("generic", generic);
        b.putLong("stamp", sLoaded);
        return b;
    }
}
