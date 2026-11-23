package io.github.pixelsuite;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

final class GoogleFeedTap {
    private GoogleFeedTap() {}

    private static float sX0, sY0, sTravel, sLastTravel;
    private static boolean sDown;
    private static long sUpAt = -1;

    static void install(ClassLoader cl) {
        try {
            Xp.hookAllMethods(Activity.class, "dispatchTouchEvent", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!feed(p.thisObject) || p.args.length != 1 || !(p.args[0] instanceof MotionEvent)) return;
                        MotionEvent e = (MotionEvent) p.args[0];
                        switch (e.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN: sDown = true; sX0 = e.getRawX(); sY0 = e.getRawY(); sTravel = 0; break;
                            case MotionEvent.ACTION_MOVE: sTravel = Math.max(sTravel, (float) Math.hypot(e.getRawX() - sX0, e.getRawY() - sY0)); break;
                            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                                sTravel = Math.max(sTravel, (float) Math.hypot(e.getRawX() - sX0, e.getRawY() - sY0));
                                sDown = false; sUpAt = SystemClock.uptimeMillis(); sLastTravel = sTravel; break;
                            default: break;
                        }
                    } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(android.app.Instrumentation.class, "execStartActivity", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        float limit = 48 * android.content.res.Resources.getSystem().getDisplayMetrics().density;
                        boolean swiping = (sDown && sTravel >= limit) || (!sDown && sUpAt > 0 && SystemClock.uptimeMillis() - sUpAt <= 400 && sLastTravel >= limit);
                        if (!swiping) return;
                        android.content.Intent i = null;
                        for (Object a : p.args) if (a instanceof android.content.Intent) { i = (android.content.Intent) a; break; }
                        if (i == null) return;
                        p.setResult(null);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Log.w("google feed tap", t); }
    }

    private static boolean feed(Object activity) {
        return activity != null && activity.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("minusone");
    }
}
