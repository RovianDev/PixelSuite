package io.github.pixelsuite;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.net.Uri;
import android.widget.Toast;

final class PdfHost {
    private PdfHost() { }

    static final String SUITE = "io.github.roviandev.pixelsuite", APP = "io.github.roviandev.pdf";

    private static GlassRim rim;
    private static float rimRadius;
    private static boolean rimDark;

    static boolean ok(Context c) { return PatronGate.ok(c); }

    static void locked(Context c) { }

    static void grantFiles(Context c) { Root.run("appops set " + c.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow; true"); }

    static String look(Context c) { return SuiteApps.look(c); }

    static int edge(Context c) { return SuiteApps.edge(c); }

    static boolean superDark(Context c) { return SuperDark.inEffect(c); }

    static void toast(Context c, CharSequence text) { AppToast.makeText(c, text, Toast.LENGTH_SHORT).show(); }

    static Uri uri(String path) { return SearchFileProvider.uri(path); }

    static void settings(Context c) {
        c.startActivity(new Intent().setClassName(SUITE, "io.github.pixelsuite.SettingsActivity").putExtra("page", "app_scan"));
    }

    static int alpha(int color, int percent) { return PixelSearch.alpha(color, percent); }

    static void rim(Context ctx, Canvas c, float l, float t, float r, float b, float radius, float width, boolean dark) {
        if (rim == null || rimRadius != radius || rimDark != dark) { rim = new GlassRim(radius, width, dark); rimRadius = radius; rimDark = dark; }
        rim.context = ctx;
        rim.draw(c, l, t, r, b, radius, 230);
    }
}
