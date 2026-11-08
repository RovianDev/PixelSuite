package io.github.pixelsuite;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GboardAutoCorrectPage {
    private final Activity a;
    private final LinearLayout page;
    private final int text, text2, accent, onAccent, trackOff, badgeBg, badgeInk;
    private final boolean dark;
    private LinearLayout langs;
    private Switch global;
    private TextView globalSub;
    private boolean updating;
    private final Set<String> off = new LinkedHashSet<String>();
    private final Runnable changed = new Runnable() { public void run() { if (langs != null && langs.isAttachedToWindow()) refresh(); } };

    GboardAutoCorrectPage(Activity activity, LinearLayout page, int text, int text2, int accent, int onAccent, int trackOff, int badgeBg, int badgeInk, boolean dark) {
        a = activity; this.page = page; this.text = text; this.text2 = text2; this.accent = accent; this.onAccent = onAccent; this.trackOff = trackOff; this.badgeBg = badgeBg; this.badgeInk = badgeInk; this.dark = dark;
        readOff();
        build();
    }

    private void readOff() {
        off.clear();
        try {
            String v = Settings.Secure.getString(a.getContentResolver(), GboardAutoCorrect.KEY_OFF);
            if (v == null) { if (Settings.Secure.getInt(a.getContentResolver(), GboardPlus.KEY_AR_NO_AUTOCORRECT, 0) == 1) off.add("ar"); }
            else if (!"none".equals(v.trim())) for (String t : v.split(",")) { t = t.trim(); if (!t.isEmpty()) off.add(t); }
        } catch (Throwable ignored) { }
    }

    private void build() {
        LinearLayout top = group();
        LinearLayout row = row(top, "Auto-correction", "", null);
        global = (Switch) row.getTag();
        globalSub = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1);
        global.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { if (!updating) setGlobal(on); }
        });
        finish(top);
        section("Languages");
        langs = group();
        explanation("The top switch is Gboard's own auto-correction. Turn it off for any language below: words aren't replaced there, but suggestions still show. Changes apply the next time the keyboard opens.");
        page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { KeyboardPreviewProvider.LISTENERS.addIfAbsent(changed); refresh(); read(); }
            public void onViewDetachedFromWindow(View v) { KeyboardPreviewProvider.LISTENERS.remove(changed); }
        });
        refresh();
    }

    private void read() {
        final android.content.Context app = a.getApplicationContext();
        new Thread(new Runnable() { public void run() {
            final List<String> l = GboardAutoCorrect.languages(app, SuperDarkApps.GBOARD);
            Boolean sw = null;
            try {
                String pkg = SuperDarkApps.GBOARD, f = pkg + "_preferences.xml";
                List<String> out = Root.run("cat /data/user_de/0/" + pkg + "/shared_prefs/" + f + " /data/data/" + pkg + "/shared_prefs/" + f + " 2>/dev/null | grep -o 'name=\"" + GboardAutoCorrect.PREF + "\" value=\"[a-z]*\"'; true");
                if (out != null) { sw = Boolean.TRUE; for (String line : out) if (line.contains("value=\"false\"")) sw = Boolean.FALSE; }
            } catch (Throwable ignored) { }
            SharedPreferences.Editor e = KeyboardPreviewProvider.state(app).edit();
            if (!l.isEmpty()) e.putString("languages", android.text.TextUtils.join("\n", l));
            if (sw != null) e.putBoolean("autocorrect", sw).putBoolean("known", true);
            e.apply();
            a.runOnUiThread(new Runnable() { public void run() { if (langs != null && langs.isAttachedToWindow()) refresh(); } });
        }}, "PixelSuiteAutoCorrectRead").start();
    }

    private void refresh() {
        SharedPreferences st = KeyboardPreviewProvider.state(a);
        boolean known = st.getBoolean("known", false), on = st.getBoolean("autocorrect", true);
        updating = true;
        global.setChecked(on); global.jumpDrawablesToCurrentState();
        updating = false;
        globalSub.setText(I18n.cs(!known ? "Gboard's own switch (reading it…)" : on ? "On: Gboard corrects words in the languages turned on below" : "Off: Gboard corrects nothing; turn it on to choose languages"));
        langs.removeAllViews();
        List<String[]> list = new ArrayList<String[]>();
        String l = st.getString("languages", null);
        if (l != null) for (String line : l.split("\n")) { int t = line.indexOf('\t'); if (t > 0) list.add(new String[] { line.substring(0, t), line.substring(t + 1) }); }
        for (String o : off) { boolean seen = false; for (String[] x : list) if (same(x[0], o)) { seen = true; break; } if (!seen) list.add(new String[] { o, Locale.forLanguageTag(o).getDisplayName() }); }
        if (list.isEmpty()) {
            TextView t = text("Reading Gboard's languages…", 15, text2, false);
            t.setPadding(dp(18), dp(18), dp(18), dp(18));
            langs.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        shown.clear(); shown.addAll(list);
        for (final String[] x : list) {
            boolean isOff = isOff(x[0]);
            LinearLayout r = row(langs, x[1], isOff ? "Words are never replaced; suggestions still show" : "Corrects words as you type", x[0]);
            final Switch sw = (Switch) r.getTag();
            updating = true; sw.setChecked(!isOff); sw.jumpDrawablesToCurrentState(); updating = false;
            final TextView sub = (TextView) ((LinearLayout) r.getChildAt(1)).getChildAt(1);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean corrects) {
                    if (updating) return;
                    List<String> keep = new ArrayList<String>();
                    for (String[] y : shown) if (y != x && isOff(y[0])) keep.add(y[0]);
                    for (String o : new ArrayList<String>(off)) if (same(x[0], o)) off.remove(o);
                    if (!corrects) off.add(x[0]);
                    for (String k : keep) if (!isOff(k)) off.add(k);
                    sub.setText(I18n.cs(corrects ? "Corrects words as you type" : "Words are never replaced; suggestions still show"));
                    saveOff();
                }
            });
            r.setAlpha(on ? 1f : 0.45f);
            sw.setEnabled(on);
        }
        finish(langs);
    }

    private static boolean same(String a, String b) {
        Locale x = Locale.forLanguageTag(a), y = Locale.forLanguageTag(b);
        if (!x.getLanguage().equals(y.getLanguage())) return false;
        return x.getCountry().isEmpty() || y.getCountry().isEmpty() || x.getCountry().equalsIgnoreCase(y.getCountry());
    }

    private final List<String[]> shown = new ArrayList<String[]>();

    private boolean isOff(String tag) { for (String o : off) if (same(tag, o)) return true; return false; }

    private void saveOff() {
        StringBuilder b = new StringBuilder();
        for (String o : off) { if (!o.matches("[A-Za-z0-9-]+")) continue; if (b.length() > 0) b.append(','); b.append(o); }
        final String cmd = "settings put secure " + GboardAutoCorrect.KEY_OFF + " " + (b.length() == 0 ? "none" : b) + " && settings put secure " + GboardPlus.KEY_AR_NO_AUTOCORRECT + " 0";
        new Thread(new Runnable() { public void run() { Root.run(cmd); } }, "PixelSuiteAutoCorrect").start();
    }

    private void setGlobal(boolean on) {
        KeyboardPreviewProvider.state(a).edit().putBoolean("autocorrect", on).apply();
        final String cmd = "settings put secure " + GboardAutoCorrect.KEY_SET + " " + (on ? 1 : 0) + ":" + System.currentTimeMillis();
        new Thread(new Runnable() { public void run() { Root.run(cmd); } }, "PixelSuiteAutoCorrect").start();
        refresh();
    }

    private LinearLayout group() {
        LinearLayout g = new LinearLayout(a); g.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(12);
        page.addView(g, lp);
        return g;
    }

    private void finish(LinearLayout g) {
        int n = g.getChildCount();
        for (int i = 0; i < n; i++) {
            float big = dp(24), small = dp(6), t = i == 0 ? big : small, b = i == n - 1 ? big : small;
            GradientDrawable bg = AppSurfaces.card(a, 24);
            bg.setCornerRadii(new float[] { t, t, t, t, b, b, b, b });
            g.getChildAt(i).setBackground(bg);
            if (i > 0) ((LinearLayout.LayoutParams) g.getChildAt(i).getLayoutParams()).topMargin = dp(3);
        }
    }

    private void section(String label) {
        TextView t = text(label, 14, accent, true);
        t.setPadding(dp(8), dp(16), dp(8), dp(8));
        t.setTag("ps_section");
        page.addView(t);
    }

    private LinearLayout row(LinearLayout g, String title, String summary, String tag) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(16), dp(16));
        row.setMinimumHeight(dp(72));
        ImageView iv = new ImageView(a);
        Drawable d = null;
        try { d = AppBrandIcons.load(a, SuperDarkApps.GBOARD, badgeInk); } catch (Throwable ignored) { }
        if (d == null) { int id = a.getResources().getIdentifier("ic_ps_google_apps", "drawable", a.getPackageName()); if (id != 0) { d = a.getDrawable(id).mutate(); d.setTint(badgeInk); } }
        if (d != null) iv.setImageDrawable(d);
        iv.setPadding(dp(9), dp(9), dp(9), dp(9));
        GradientDrawable disc = new GradientDrawable(); disc.setShape(GradientDrawable.OVAL); disc.setColor(badgeBg); iv.setBackground(disc);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40)); ip.rightMargin = dp(16);
        row.addView(iv, ip);
        LinearLayout labels = new LinearLayout(a); labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(title, 17, text, false);
        name.setTextDirection(View.TEXT_DIRECTION_LOCALE); name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        labels.addView(name);
        TextView s = text(summary, 14, text2, false); s.setPadding(0, dp(2), 0, 0); labels.addView(s);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(a);
        SwitchStyle.apply(sw, accent, trackOff, onAccent, text2);
        sw.setContentDescription(I18n.cs(title));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2); sp.leftMargin = dp(12);
        row.addView(sw, sp);
        row.setTag(sw);
        if (tag != null) row.setContentDescription(I18n.cs(title));
        g.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void explanation(String value) {
        TextView t = text(value, 15, text2, false); t.setTag("ps_explanation");
        t.setPadding(dp(18), dp(16), dp(18), dp(18)); t.setLineSpacing(dp(3), 1); t.setBackground(AppSurfaces.card(a, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(12);
        page.addView(t, lp);
    }

    private TextView text(String s, float sp, int color, boolean medium) {
        TextView t = new TextView(a); t.setText(I18n.cs(SettingsText.format(s))); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color);
        if (medium) t.setTypeface(I18n.medium());
        return t;
    }

    private int dp(float v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
}
