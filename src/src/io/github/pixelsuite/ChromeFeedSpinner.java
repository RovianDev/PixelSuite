package io.github.pixelsuite;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

final class ChromeFeedSpinner {
    private ChromeFeedSpinner() {}

    private static int sFeedId = -1;
    private static final Map<View, Long> sFreeSince = new WeakHashMap<View, Long>();
    private static final Map<View, Boolean> sHidden = new WeakHashMap<View, Boolean>();

    static void watch(final Activity a) {
        if (!ChromePatron.ok(a)) return;
        final View decor = a.getWindow().getDecorView();
        if (Xp.getAdditionalInstanceField(decor, "pixelsuite_feed_spinner") != null) return;
        ViewTreeObserver.OnPreDrawListener l = new ViewTreeObserver.OnPreDrawListener() {
            long last;
            final ArrayList<View> spinners = new ArrayList<View>();
            final int[] at = new int[2], at2 = new int[2];
            @Override public boolean onPreDraw() {
                try {
                    long now = SystemClock.uptimeMillis();
                    boolean slow = now - last >= 150;
                    if (slow) last = now;
                    if (sFeedId == -1) sFeedId = a.getResources().getIdentifier("feed_stream_recycler_view", "id", a.getPackageName());
                    View found = sFeedId == 0 ? null : decor.findViewById(sFeedId);
                    if (!(found instanceof ViewGroup) || !found.isShown()) return true;
                    ViewGroup feed = (ViewGroup) found;
                    spinners.clear();
                    for (int i = 0; i < feed.getChildCount(); i++) collect(feed.getChildAt(i), spinners, slow ? 0 : 5);
                    for (View s : spinners) {
                        View row = s;
                        while (row.getParent() != feed && row.getParent() instanceof View) row = (View) row.getParent();
                        boolean covered = s.getWidth() > 0 && s.getHeight() > 0 && covered(feed, row, s);
                        if (covered) {
                            sFreeSince.remove(s);
                            if (!sHidden.containsKey(s)) hide(s);
                            else if (s.getAlpha() != 0f) s.setAlpha(0f);
                        } else if (sHidden.containsKey(s)) {
                            Long since = sFreeSince.get(s);
                            if (since == null) sFreeSince.put(s, now);
                            else if (now - since > 300) { sFreeSince.remove(s); show(s); }
                        }
                    }
                } catch (Throwable ignored) { }
                return true;
            }

            boolean covered(ViewGroup feed, View row, View s) {
                s.getLocationOnScreen(at);
                int l = at[0], t = at[1], r = l + s.getWidth(), b = t + s.getHeight();
                long need = (long) s.getWidth() * s.getHeight() / 2;
                for (int i = 0; i < feed.getChildCount(); i++) {
                    View c = feed.getChildAt(i);
                    if (c == row || c.getVisibility() != View.VISIBLE || c.getHeight() <= 0 || c.getWidth() <= 0 || c.getAlpha() < 0.5f) continue;
                    c.getLocationOnScreen(at2);
                    int w = Math.min(r, at2[0] + c.getWidth()) - Math.max(l, at2[0]);
                    int h = Math.min(b, at2[1] + c.getHeight()) - Math.max(t, at2[1]);
                    if (w > 0 && h > 0 && (long) w * h >= need) return true;
                }
                return false;
            }
        };
        Xp.setAdditionalInstanceField(decor, "pixelsuite_feed_spinner", l);
        decor.getViewTreeObserver().addOnPreDrawListener(l);
    }

    private static void collect(View v, ArrayList<View> out, int depth) {
        if (v == null || depth > 8) return;
        if (v.getClass().getName().endsWith("MaterialSpinnerView")) { out.add(v); return; }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out, depth + 1);
        }
    }

    private static void hide(final View s) {
        sHidden.put(s, Boolean.TRUE);
        s.setAlpha(0f);
        if (Xp.getAdditionalInstanceField(s, "pixelsuite_spinner_detach") != null) return;
        View.OnAttachStateChangeListener l = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { sFreeSince.remove(s); }
        };
        Xp.setAdditionalInstanceField(s, "pixelsuite_spinner_detach", l);
        s.addOnAttachStateChangeListener(l);
    }

    private static void show(View s) {
        if (sHidden.remove(s) != null) s.setAlpha(1f);
    }
}
