package io.github.pixelsuite;

import android.content.Context;

final class StudioSupport {
    private StudioSupport() { }

    static final String NOTE = "Needs Pixel Studio (Pixel 9 and newer).";
    private static Boolean sSupported;

    static boolean needsStudio(String key) {
        return Feature.SCREENSHOT_FREE_CROP.equals(key) || Feature.SCREENSHOT_DOODLES.equals(key);
    }

    static boolean supported(Context c) {
        if (sSupported == null) {
            boolean yes = true;
            try { yes = c.getPackageManager().hasSystemFeature("com.google.android.feature.PIXEL_2024_EXPERIENCE"); } catch (Throwable unknown) { }
            sSupported = yes;
        }
        return sSupported;
    }

    static boolean blocked(Context c, String key) { return needsStudio(key) && !supported(c); }
}
