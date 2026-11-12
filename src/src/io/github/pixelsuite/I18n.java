package io.github.pixelsuite;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class I18n {
    private I18n() {}

    static final String MODULE = "io.github.roviandev.pixelsuite";
    static final String[] LANGS = { "zh", "hi", "es", "ar", "fr", "pt", "ru", "bn", "id", "de", "ja", "tr", "fa", "he" };

    private static volatile boolean sReady;
    private static volatile String sLang;
    private static Map<String, String> sMap = new HashMap<String, String>();
    private static final List<Pat> sPats = new ArrayList<Pat>();
    private static final Map<String, String> sCache = new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 600; }
    };

    private static final class Pat {
        final Pattern re; final String out; final String head;
        Pat(Pattern re, String out, String head) { this.re = re; this.out = out; this.head = head; }
    }

    static String langOf(Locale l) {
        if (l == null) return null;
        String g = l.getLanguage();
        if ("iw".equals(g)) g = "he";
        if ("in".equals(g)) g = "id";
        if ("zh".equals(g)) {
            String script = "";
            try { script = l.getScript(); } catch (Throwable ignored) { }
            String country = l.getCountry();
            if ("Hant".equals(script) || "TW".equals(country) || "HK".equals(country) || "MO".equals(country)) return null;
        }
        for (String x : LANGS) if (x.equals(g)) return x;
        return null;
    }

    private static Locale locale(Context c) {
        try {
            Configuration cfg = (c != null ? c : null) == null ? null : c.getResources().getConfiguration();
            if (cfg != null) {
                try {
                    Object list = Configuration.class.getMethod("getLocales").invoke(cfg);
                    Object first = list.getClass().getMethod("get", int.class).invoke(list, 0);
                    if (first instanceof Locale) return (Locale) first;
                } catch (Throwable ignored) { }
                if (cfg.locale != null) return cfg.locale;
            }
        } catch (Throwable ignored) { }
        return Locale.getDefault();
    }

    static final String KEY = "pixelsuite_language";
    private static volatile String sPicked;
    private static volatile boolean sWatching;

    private static Context app(Context c) {
        if (c != null) return c;
        try {
            Object a = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (a instanceof Context) return (Context) a;
        } catch (Throwable ignored) { }
        return null;
    }

    static String choice(Context c) {
        String v = sPicked;
        if (v == null) {
            c = app(c);
            try { v = c == null ? null : android.provider.Settings.Secure.getString(c.getContentResolver(), KEY); } catch (Throwable ignored) { v = null; }
        }
        if (v == null || v.isEmpty()) return "auto";
        if ("en".equals(v)) return v;
        for (String x : LANGS) if (x.equals(v)) return v;
        return "auto";
    }

    private static String wanted(Context c) {
        String pick = choice(c);
        if ("en".equals(pick)) return null;
        if (!"auto".equals(pick)) return pick;
        return langOf(locale(c));
    }

    static void init(Context c) {
        if (sReady) return;
        synchronized (I18n.class) {
            if (sReady) return;
            c = app(c);
            watch(c);
            String lang = wanted(c);
            if (lang != null) {
                try { load(c, lang); } catch (Throwable t) { sMap = new HashMap<String, String>(); sPats.clear(); lang = null; }
                if (sMap.isEmpty() && sPats.isEmpty()) lang = null;
            }
            sLang = lang;
            sReady = true;
        }
    }

    private static void watch(Context c) {
        if (sWatching || c == null) return;
        try {
            final android.content.ContentResolver r = c.getContentResolver();
            r.registerContentObserver(android.provider.Settings.Secure.getUriFor(KEY), false, new android.database.ContentObserver(null) {
                @Override public void onChange(boolean self) { sPicked = null; forget(); }
            });
            sWatching = true;
        } catch (Throwable ignored) { }
    }

    private static void forget() {
        synchronized (I18n.class) {
            sReady = false; sMap = new HashMap<String, String>(); sPats.clear();
            synchronized (sCache) { sCache.clear(); }
        }
    }

    static void refresh(Context c) {
        if (!sReady) { init(c); return; }
        String now = wanted(app(c));
        if (now == null ? sLang == null : now.equals(sLang)) return;
        forget();
        init(c);
    }

    static void pick(Context c, String code) {
        sPicked = code == null ? "auto" : code;
        forget();
        init(c);
    }

    static Locale shownLocale() {
        String l = lang();
        if (l == null) return Locale.ENGLISH;
        if ("zh".equals(l)) return Locale.SIMPLIFIED_CHINESE;
        if ("pt".equals(l)) return new Locale("pt", "BR");
        return new Locale(l);
    }

    static boolean on() { if (!sReady) init(null); return sLang != null; }

    static String lang() { if (!sReady) init(null); return sLang; }

    static android.graphics.Typeface medium() {
        String l = lang();
        android.graphics.Typeface fa = PersianFont.medium();
        if (fa != null) return fa;
        boolean none = "ar".equals(l) || "fa".equals(l) || "he".equals(l) || "hi".equals(l) || "bn".equals(l);
        return none ? android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) : android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL);
    }

    static boolean rtl() { String l = lang(); return "ar".equals(l) || "fa".equals(l) || "he".equals(l); }

    private static String apk(Context c) {
        try {
            if (c != null && MODULE.equals(c.getPackageName())) return c.getApplicationInfo().sourceDir;
        } catch (Throwable ignored) { }
        try {
            ApplicationInfo me = Xp.x() == null ? null : Xp.x().getModuleApplicationInfo();
            if (me != null && me.sourceDir != null) return me.sourceDir;
        } catch (Throwable ignored) { }
        try {
            if (c != null) return c.getPackageManager().getApplicationInfo(MODULE, 0).sourceDir;
        } catch (Throwable ignored) { }
        try {
            Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                Context a = (Context) app;
                if (MODULE.equals(a.getPackageName())) return a.getApplicationInfo().sourceDir;
                return a.getPackageManager().getApplicationInfo(MODULE, 0).sourceDir;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static void load(Context c, String lang) throws Exception {
        String path = apk(c);
        if (path == null) return;
        ZipFile zip = new ZipFile(path);
        try {
            ZipEntry e = zip.getEntry("res/raw/i18n_" + lang + ".txt");
            if (e == null) return;
            InputStream in = zip.getInputStream(e);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"), 1 << 16);
            Map<String, String> map = new HashMap<String, String>(4096);
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0 || tab == line.length() - 1) continue;
                String en = unescape(line.substring(0, tab)), tr = unescape(line.substring(tab + 1));
                if (en.contains("{0}")) addPattern(en, tr); else map.put(en, tr);
            }
            r.close();
            sMap = map;
        } finally {
            try { zip.close(); } catch (Throwable ignored) { }
        }
    }

    private static void addPattern(String en, String tr) {
        StringBuilder re = new StringBuilder("^");
        Matcher m = Pattern.compile("\\{(\\d)\\}").matcher(en);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) re.append(Pattern.quote(en.substring(at, m.start())));
            re.append("(.+?)");
            at = m.end();
        }
        if (at < en.length()) re.append(Pattern.quote(en.substring(at)));
        re.append("$");
        StringBuilder out = new StringBuilder();
        Matcher n = Pattern.compile("\\{(\\d)\\}").matcher(en);
        List<String> order = new ArrayList<String>();
        while (n.find()) order.add(n.group(1));
        Matcher o = Pattern.compile("\\{(\\d)\\}").matcher(tr);
        int last = 0;
        while (o.find()) {
            out.append(tr, last, o.start()).append('\u0000').append((char) ('0' + order.indexOf(o.group(1)) + 1));
            last = o.end();
        }
        out.append(tr.substring(last));
        int first = en.indexOf("{0}");
        String head = first > 0 ? en.substring(0, Math.min(first, 3)) : "";
        sPats.add(new Pat(Pattern.compile(re.toString(), Pattern.DOTALL), out.toString(), head));
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'n') b.append('\n'); else if (n == 't') b.append('\t'); else b.append(n);
            } else b.append(ch);
        }
        return b.toString();
    }

    static String t(String en) {
        if (en == null || en.isEmpty()) return en;
        if (!sReady) init(null);
        if (sLang == null) return en;
        String hit = sMap.get(en);
        if (hit != null) return hit;
        if (sPats.isEmpty()) return en;
        synchronized (sCache) {
            String cached = sCache.get(en);
            if (cached != null) return cached;
        }
        String out = en;
        for (Pat p : sPats) {
            if (!p.head.isEmpty() && !en.startsWith(p.head)) continue;
            Matcher m = p.re.matcher(en);
            if (!m.matches()) continue;
            StringBuilder b = new StringBuilder();
            String f = p.out;
            for (int i = 0; i < f.length(); i++) {
                char ch = f.charAt(i);
                if (ch == '\u0000' && i + 1 < f.length()) {
                    int g = f.charAt(++i) - '0';
                    if (g >= 1 && g <= m.groupCount()) b.append(t(m.group(g)));
                } else b.append(ch);
            }
            out = b.toString();
            break;
        }
        if (out == en) out = pieces(en);
        synchronized (sCache) { sCache.put(en, out); }
        return out;
    }

    private static String pieces(String en) {
        String trimmed = en.trim();
        if (!trimmed.isEmpty() && trimmed.length() < en.length()) {
            int lead = en.indexOf(trimmed);
            String inner = t(trimmed);
            return inner.equals(trimmed) ? en : en.substring(0, lead) + inner + en.substring(lead + trimmed.length());
        }
        for (String sep : new String[]{"\n", "  ·  ", " · "}) {
            if (!en.contains(sep)) continue;
            String[] parts = en.split(java.util.regex.Pattern.quote(sep), -1);
            StringBuilder b = new StringBuilder(); boolean changed = false;
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) b.append(sep);
                String x = parts[i].isEmpty() ? parts[i] : t(parts[i]);
                changed |= !x.equals(parts[i]);
                b.append(x);
            }
            return changed ? b.toString() : en;
        }
        if (en.startsWith("• ") && en.length() > 2) { String x = t(en.substring(2)); return x.equals(en.substring(2)) ? en : "• " + x; }
        return en;
    }

    static CharSequence cs(CharSequence en) {
        if (en == null || en instanceof Spanned || !(en instanceof String) && en.length() == 0) return en;
        if (!sReady) init(null);
        if (sLang == null) return en;
        return t(en.toString());
    }

    static String[] all(String[] en) {
        if (en == null) return null;
        if (!sReady) init(null);
        if (sLang == null) return en;
        String[] out = new String[en.length];
        for (int i = 0; i < en.length; i++) out[i] = t(en[i]);
        return out;
    }

    static CharSequence[] all(CharSequence[] en) {
        if (en == null) return null;
        if (!sReady) init(null);
        if (sLang == null) return en;
        CharSequence[] out = new CharSequence[en.length];
        for (int i = 0; i < en.length; i++) out[i] = cs(en[i]);
        return out;
    }

    static void fit(android.graphics.Paint p, float room, String... texts) {
        if (p == null || room <= 0 || texts == null) return;
        float widest = 0;
        for (String s : texts) if (s != null) widest = Math.max(widest, p.measureText(s));
        if (widest > room) p.setTextSize(p.getTextSize() * Math.max(0.7f, room / widest));
    }

    static void apply(View v) {
        if (v == null) return;
        if (!sReady) init(v.getContext());
        if (sLang == null) return;
        try {
            CharSequence d = v.getContentDescription();
            if (d instanceof String) { String t = t((String) d); if (!t.equals(d)) v.setContentDescription(t); }
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                if (!(v instanceof EditText)) {
                    CharSequence s = tv.getText();
                    if (s != null && !(s instanceof Spanned) && s.length() > 0) { String t = t(s.toString()); if (!t.contentEquals(s)) tv.setText(t); }
                }
                CharSequence h = tv.getHint();
                if (h != null && !(h instanceof Spanned)) { String t = t(h.toString()); if (!t.contentEquals(h)) tv.setHint(t); }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i));
            }
        } catch (Throwable ignored) { }
    }

    static void fitLine(final TextView tv) {
        if (tv == null || !on()) return;
        try {
            if (tv.getEllipsize() == null) tv.setEllipsize(TextUtils.TruncateAt.END);
            final float base = tv.getTextSize();
            tv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { shrink(tv, base); }
            });
            tv.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    if (Math.abs(tv.getTextSize() - base) > 0.25f) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, base);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void shrink(final TextView tv, float base) {
        int room = tv.getWidth() - tv.getCompoundPaddingLeft() - tv.getCompoundPaddingRight();
        CharSequence s = tv.getText();
        if (room <= 0 || s == null || s.length() == 0) return;
        float need = tv.getPaint().measureText(s, 0, s.length());
        if (need <= room + 0.5f) return;
        final float size = Math.max(base * 0.7f, tv.getTextSize() * room / need);
        if (Math.abs(size - tv.getTextSize()) < 0.25f) return;
        tv.post(new Runnable() { @Override public void run() { tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size); } });
    }

    static String phrases(String en) {
        if (en == null || en.isEmpty() || !on()) return en;
        String whole = t(en);
        if (!whole.equals(en)) return whole;
        String[] w = en.split(" ");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length; ) {
            int took = 0; String hit = null;
            for (int j = Math.min(w.length, i + 14); j > i; j--) {
                StringBuilder c = new StringBuilder(w[i]);
                for (int k = i + 1; k < j; k++) c.append(' ').append(w[k]);
                hit = sMap.get(c.toString());
                if (hit != null) { took = j - i; break; }
            }
            if (b.length() > 0) b.append(' ');
            if (hit != null) { b.append(hit); i += took; } else { b.append(w[i]); i++; }
        }
        return b.toString();
    }
}
