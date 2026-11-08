package io.github.pixelsuite;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import java.util.Calendar;

final class DarkPages {
    private DarkPages() {}

    static final String KEY = "pixelsuite_dark_pages";
    static final String KEY_STYLE = "pixelsuite_dark_pages_style";
    static final String KEY_WHEN = "pixelsuite_dark_pages_when";
    static final String KEY_FROM = "pixelsuite_dark_pages_from", KEY_TO = "pixelsuite_dark_pages_to";
    static final int ALWAYS = 0, WITH_DARK_MODE = 1, SCHEDULE = 2;
    static final int DEFAULT_FROM = 19 * 60, DEFAULT_TO = 7 * 60;
    static final String[] STYLES = { "Dark", "Super Dark" };
    static final String[] WHEN = { "Always", "With the phone's dark mode", "On a schedule" };

    static boolean systemDark() {
        try { return (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; } catch (Throwable t) { return false; }
    }

    static int minuteOfDay(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    static boolean inSchedule(int minute, int from, int to) {
        if (from == to) return true;
        return from < to ? minute >= from && minute < to : minute >= from || minute < to;
    }

    static boolean activeNow(boolean on, int when, int from, int to, long now) {
        if (!on) return false;
        if (when == WITH_DARK_MODE) return systemDark();
        if (when == SCHEDULE) return inSchedule(minuteOfDay(now), from, to);
        return true;
    }

    static long untilNextChange(int from, int to, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        long best = Long.MAX_VALUE;
        for (int m : new int[] { from, to }) {
            Calendar t = (Calendar) c.clone();
            t.set(Calendar.HOUR_OF_DAY, m / 60); t.set(Calendar.MINUTE, m % 60); t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() <= now) t.add(Calendar.DAY_OF_MONTH, 1);
            best = Math.min(best, t.getTimeInMillis() - now);
        }
        return best;
    }

    static String time(Context c, int minutes) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, minutes / 60); t.set(Calendar.MINUTE, minutes % 60);
        return android.text.format.DateFormat.getTimeFormat(c).format(t.getTime());
    }

    static boolean known(String key) {
        return KEY.equals(key) || KEY_STYLE.equals(key) || KEY_WHEN.equals(key) || KEY_FROM.equals(key) || KEY_TO.equals(key);
    }

    static int defaultFor(String key) {
        if (KEY_FROM.equals(key)) return DEFAULT_FROM;
        if (KEY_TO.equals(key)) return DEFAULT_TO;
        return 0;
    }

    static boolean valid(String key, int v) {
        if (KEY.equals(key) || KEY_STYLE.equals(key)) return v == 0 || v == 1;
        if (KEY_WHEN.equals(key)) return v >= 0 && v <= 2;
        return v >= 0 && v < 24 * 60;
    }

    static Bundle serve(Context c, String key, Bundle extras) {
        int v = extras == null ? -1 : extras.getInt("value", -1);
        if (!known(key) || !valid(key, v)) throw new IllegalArgumentException("Invalid Dark pages setting");
        boolean ok = Root.run("settings put secure " + key + " " + v) != null;
        Bundle out = new Bundle();
        out.putBoolean("ok", ok);
        return out;
    }
}
