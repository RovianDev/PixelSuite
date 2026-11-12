package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class PersianFont {
    private PersianFont() { }

    private static Typeface sRegular, sMedium, sBold;
    private static boolean sTried;

    static boolean wanted() { return "fa".equals(I18n.lang()); }

    static Typeface get(Context c, int weight) {
        if (!wanted() || !load(c)) return null;
        return weight >= 600 ? sBold : weight >= 500 ? sMedium : sRegular;
    }

    private static synchronized boolean load(Context c) {
        if (sTried) return sRegular != null;
        sTried = true;
        try {
            Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
            int id = app.getResources().getIdentifier("vazirmatn", "raw", app.getPackageName());
            if (id == 0) return false;
            File f = new File(app.getFilesDir(), "vazirmatn-1.ttf");
            if (!f.exists() || f.length() == 0) {
                File tmp = new File(app.getFilesDir(), "vazirmatn.tmp");
                InputStream in = app.getResources().openRawResource(id);
                FileOutputStream out = new FileOutputStream(tmp);
                try { byte[] b = new byte[16384]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); } finally { in.close(); out.close(); }
                if (!tmp.renameTo(f)) return false;
            }
            sRegular = build(f, 400); sMedium = build(f, 500); sBold = build(f, 700);
            return sRegular != null;
        } catch (Throwable t) { sRegular = sMedium = sBold = null; return false; }
    }

    private static Typeface build(File f, int weight) throws Exception {
        Class<?> b = Class.forName("android.graphics.Typeface$Builder");
        Object builder = b.getConstructor(File.class).newInstance(f);
        b.getMethod("setFontVariationSettings", String.class).invoke(builder, "'wght' " + weight);
        b.getMethod("setWeight", int.class).invoke(builder, weight);
        return (Typeface) b.getMethod("build").invoke(builder);
    }

    private static java.lang.reflect.Method sGetWeight; private static boolean sLooked;
    private static int weightOf(Typeface t) {
        try {
            if (!sLooked) { sLooked = true; sGetWeight = Typeface.class.getMethod("getWeight"); }
            return sGetWeight == null ? (t.isBold() ? 700 : 400) : (Integer) sGetWeight.invoke(t);
        } catch (Throwable e) { return t.isBold() ? 700 : 400; }
    }

    static Typeface medium() { return wanted() ? sMedium : null; }

    static void watch(final Activity a) {
        if (!wanted() || !load(a)) return;
        final View root = a.getWindow().getDecorView();
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> apply(root));
        apply(root);
    }

    static void apply(View v) {
        if (v == null || sRegular == null || !wanted()) return;
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            Typeface cur = tv.getTypeface();
            if (cur != sRegular && cur != sMedium && cur != sBold) {
                int w = 400;
                if (cur != null) { w = weightOf(cur); if (cur.isBold() && w < 600) w = 700; }
                tv.setTypeface(w >= 600 ? sBold : w >= 500 ? sMedium : sRegular);
            }
        }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i)); }
    }
}
