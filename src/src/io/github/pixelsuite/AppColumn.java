package io.github.pixelsuite;

import android.content.Context;
import android.content.res.Configuration;

final class AppColumn {
    private AppColumn() { }
    static final int MAX_DP = 640;

    static int side(Context c, int width) {
        try {
            Configuration cf = c.getResources().getConfiguration();
            if (cf.orientation != Configuration.ORIENTATION_LANDSCAPE || cf.smallestScreenWidthDp >= 600) return 0;
            float d = c.getResources().getDisplayMetrics().density;
            return Math.max(0, Math.round((width - MAX_DP * d) / 2f));
        } catch (Throwable t) { return 0; }
    }
}
