package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GboardAutoCorrect {
    private GboardAutoCorrect() {}

    static final String KEY_OFF = "pixelsuite_gboard_autocorrect_off";
    static final String KEY_SET = "pixelsuite_gboard_autocorrect_set";
    static final String PREF = "pref_key_auto_correction";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static SharedPreferences sPrefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener sListener;
    private static ContentObserver sObserver;
    private static String sLastLanguages;
    private static long sLanguagesAt;

    static void start(final InputMethodService ims) {
        MAIN.post(new Runnable() { public void run() {
            try {
                if (sObserver != null) return;
                final Context app = ims.getApplicationContext();
                sPrefs = gboardPrefs(ims);
                if (sPrefs != null) {
                    sListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                        public void onSharedPreferenceChanged(SharedPreferences p, String key) { if (PREF.equals(key)) report(app, true, false); }
                    };
                    sPrefs.registerOnSharedPreferenceChangeListener(sListener);
                }
                sObserver = new ContentObserver(MAIN) {
                    @Override public void onChange(boolean self) { applyRequest(app); }
                };
                app.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY_SET), false, sObserver);
                applyRequest(app);
                report(app, true, true);
            } catch (Throwable ignored) { }
        }});
    }

    static void opened(InputMethodService ims) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sLanguagesAt < 30000) return;
        sLanguagesAt = now;
        report(ims.getApplicationContext(), false, true);
    }

    private static SharedPreferences gboardPrefs(Context c) {
        String name = c.getPackageName() + "_preferences";
        SharedPreferences ce = null, de = null;
        try { ce = c.getSharedPreferences(name, Context.MODE_PRIVATE); } catch (Throwable ignored) { }
        try { Context d = (Context) Context.class.getMethod("createDeviceProtectedStorageContext").invoke(c); de = d.getSharedPreferences(name, Context.MODE_PRIVATE); } catch (Throwable ignored) { }
        int nce = ce == null ? -1 : ce.getAll().size(), nde = de == null ? -1 : de.getAll().size();
        return nde >= nce ? de : ce;
    }

    private static void applyRequest(Context app) {
        try {
            String v = Settings.Secure.getString(app.getContentResolver(), KEY_SET);
            if (v == null || v.indexOf(':') <= 0 || sPrefs == null) return;
            boolean on = v.startsWith("1");
            long at = Long.parseLong(v.substring(v.indexOf(':') + 1).trim());
            File f = new File(app.getCacheDir(), "pixelsuite_autocorrect_applied");
            long done = 0;
            if (f.isFile()) {
                byte[] b = new byte[(int) Math.min(64, f.length())];
                FileInputStream in = new FileInputStream(f);
                try { int n = in.read(b); done = Long.parseLong(new String(b, 0, Math.max(0, n), "UTF-8").trim()); } catch (Throwable ignored) { } finally { in.close(); }
            }
            if (at <= done) return;
            FileOutputStream out = new FileOutputStream(f);
            try { out.write(String.valueOf(at).getBytes("UTF-8")); } finally { out.close(); }
            if (sPrefs.getBoolean(PREF, true) != on) sPrefs.edit().putBoolean(PREF, on).apply();
            report(app, true, false);
        } catch (Throwable ignored) { }
    }

    private static void report(final Context app, final boolean sw, final boolean languages) {
        final Bundle b = new Bundle();
        try {
            if (sw && sPrefs != null) b.putBoolean("autocorrect", sPrefs.getBoolean(PREF, true));
            if (languages) {
                List<String> l = languages(app, app.getPackageName());
                String joined = android.text.TextUtils.join("\n", l);
                if (!l.isEmpty() && !joined.equals(sLastLanguages)) { sLastLanguages = joined; b.putStringArray("languages", l.toArray(new String[0])); }
            }
        } catch (Throwable ignored) { }
        if (b.isEmpty()) return;
        new Thread(new Runnable() { public void run() {
            try { app.getContentResolver().call(Uri.parse("content://" + GboardPreviewShot.AUTHORITY), "gboard_state", null, b); } catch (Throwable ignored) { }
        }}, "PixelSuiteGboardState").start();
    }

    static List<String> languages(Context c, String pkg) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        try {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            InputMethodInfo mine = null;
            for (InputMethodInfo i : imm.getInputMethodList()) if (pkg.equals(i.getPackageName())) { mine = i; break; }
            android.content.pm.ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            if (mine == null) return new ArrayList<String>();
            for (InputMethodSubtype s : imm.getEnabledInputMethodSubtypeList(mine, true)) {
                if (!"keyboard".equals(s.getMode())) continue;
                String tag = tag(s);
                if (tag == null || tag.isEmpty() || out.containsKey(tag)) continue;
                String name = null;
                try { CharSequence n = s.getDisplayName(c, pkg, ai); if (n != null) name = n.toString(); } catch (Throwable ignored) { }
                if (name == null || name.isEmpty()) name = Locale.forLanguageTag(tag).getDisplayName();
                out.put(tag, name.replace('\t', ' ').replace('\n', ' '));
            }
        } catch (Throwable ignored) { }
        List<String> l = new ArrayList<String>();
        for (Map.Entry<String, String> e : out.entrySet()) l.add(e.getKey() + "\t" + e.getValue());
        return l;
    }

    static String tag(InputMethodSubtype s) {
        try { Object t = InputMethodSubtype.class.getMethod("getLanguageTag").invoke(s); if (t instanceof String && !((String) t).isEmpty()) return (String) t; } catch (Throwable ignored) { }
        try { String l = s.getLocale(); if (l != null && !l.isEmpty()) return Locale.forLanguageTag(l.replace('_', '-')).toLanguageTag(); } catch (Throwable ignored) { }
        return null;
    }

    private static List<Locale> sOff = new ArrayList<Locale>();
    private static long sOffAt;

    private static List<Locale> off(Context c) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sOffAt < 2000) return sOff;
        sOffAt = now;
        List<Locale> out = new ArrayList<Locale>();
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), KEY_OFF);
            if (v == null) { if (Settings.Secure.getInt(c.getContentResolver(), GboardPlus.KEY_AR_NO_AUTOCORRECT, 0) == 1) out.add(new Locale("ar")); }
            else if (!"none".equals(v.trim())) for (String t : v.split(",")) { t = t.trim(); if (!t.isEmpty()) out.add(Locale.forLanguageTag(t)); }
        } catch (Throwable ignored) { }
        sOff = out;
        return out;
    }

    static boolean offFor(Context c, Locale l) {
        if (c == null || l == null) return false;
        for (Locale o : off(c)) {
            if (!o.getLanguage().equals(l.getLanguage())) continue;
            if (o.getCountry().isEmpty() || l.getCountry().isEmpty() || o.getCountry().equalsIgnoreCase(l.getCountry())) return true;
        }
        return false;
    }
}
