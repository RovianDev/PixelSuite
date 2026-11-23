package io.github.pixelsuite;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

final class ChromeTabGrid {
    private ChromeTabGrid() {}

    private static final Map<View, Integer> sHeight = new WeakHashMap<View, Integer>();
    private static final Map<View, Integer> sPad0 = new WeakHashMap<View, Integer>();
    private static final Map<View, Boolean> sWatched = new WeakHashMap<View, Boolean>();
    private static Class<?> sGrid;

    static void install(ClassLoader l) {
        try {
            final Class<?> grid = l.loadClass("org.chromium.chrome.browser.tasks.tab_management.TabListRecyclerView");
            sGrid = grid;

            Xp.hookAllMethods(grid, "onLayout", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) { try { watch((ViewGroup) p.thisObject); } catch (Throwable ignored) { } }
            });

            Method base = null;
            for (Class<?> k = grid.getSuperclass(); k != null && k != Object.class && base == null; k = k.getSuperclass())
                try { base = k.getDeclaredMethod("onLayout", boolean.class, int.class, int.class, int.class, int.class); } catch (NoSuchMethodException ignored) { }
            if (base != null) {
                Xp.hookMethod(base, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try { if (sGrid.isInstance(p.thisObject)) placed((ViewGroup) p.thisObject); } catch (Throwable ignored) { }
                    }
                });
            }
        } catch (Throwable ignored) { }
    }

    private static int gap(View g) { return Math.round(16 * g.getResources().getDisplayMetrics().density); }

    private static void watch(final ViewGroup g) {
        pad(g, ChromeLayout.enabled(g.getContext()));
        if (sWatched.containsKey(g)) return;
        sWatched.put(g, Boolean.TRUE);
        g.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                try { pad(g, ChromeLayout.enabled(g.getContext())); } catch (Throwable ignored) { }
                return true;
            }
        });
    }

    private static void pad(ViewGroup g, boolean on) {
        int want = gap(g);
        if (on) {
            int now = g.getPaddingBottom();
            if (now >= want) return;
            if (!sPad0.containsKey(g)) sPad0.put(g, now);
            g.setPadding(g.getPaddingLeft(), g.getPaddingTop(), g.getPaddingRight(), want);
        } else {
            Integer was = sPad0.remove(g);
            if (was != null && g.getPaddingBottom() == want) g.setPadding(g.getPaddingLeft(), g.getPaddingTop(), g.getPaddingRight(), was);
        }
    }

    private static void placed(ViewGroup g) {
        int h = g.getHeight();
        Integer before = sHeight.put(g, h);
        boolean changed = before == null || before != h;
        long now = android.os.SystemClock.uptimeMillis(), opened = ChromeTabTransition.openedAt();
        boolean opening = opened > 0 && now - opened < 1500;
        if (!changed && !opening) return;
        if (h <= 0 || g.getChildCount() == 0 || !ChromeLayout.enabled(g.getContext())) return;
        int end = h - g.getPaddingBottom();
        String[] how = new String[1];
        View target = current(g, how);
        int hidden, by;
        int left = remaining(g);
        if (target != null) {
            hidden = target.getBottom() + marginBottom(target) - end;
            if (hidden <= 0) return;
            by = Math.min(hidden, Math.max(0, target.getTop() - g.getPaddingTop()));
            if (left >= 0) by = Math.min(by, left);
        } else {
            if (opening && Long.valueOf(opened).equals(sDone.get(g))) return;
            View lowest = null;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c.getVisibility() != View.VISIBLE || c.getHeight() == 0) continue;
                if (lowest == null || c.getBottom() > lowest.getBottom()) lowest = c;
            }
            if (lowest == null) return;
            hidden = lowest.getBottom() + marginBottom(lowest) - end;
            if (hidden <= 0) return;
            int room = roomAtTop(g);
            if (hidden > lowest.getHeight() / 2 || left <= 0 || left > hidden + 2 * gap(g) || room < hidden) {
                return;
            }
            by = Math.min(hidden, left);
            how[0] = "the last card (no card marked current)";
            if (opening) sDone.put(g, opened);
        }
        if (by <= 0) return;
        g.scrollBy(0, by);
    }

    private static int roomAtTop(ViewGroup g) {
        int room = Integer.MAX_VALUE, top = g.getPaddingTop();
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getHeight() == 0 || c.getTop() < top) continue;
            room = Math.min(room, c.getTop() - top);
        }
        return room;
    }

    private static View current(ViewGroup g, String[] how) {
        java.util.HashMap<Integer, Integer> count = new java.util.HashMap<Integer, Integer>();
        java.util.HashMap<View, Integer> tint = new java.util.HashMap<View, Integer>();
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getHeight() == 0) continue;
            View cv = byName(c, "card_view", 0);
            if (cv == null || cv.getBackgroundTintList() == null) continue;
            int col = cv.getBackgroundTintList().getDefaultColor();
            tint.put(c, col);
            Integer n = count.get(col);
            count.put(col, n == null ? 1 : n + 1);
        }
        if (count.size() >= 2) {
            int common = 0;
            for (int n : count.values()) common = Math.max(common, n);
            View only = null;
            boolean many = false;
            if (common >= 2) for (java.util.Map.Entry<View, Integer> e : tint.entrySet()) if (count.get(e.getValue()) == 1) { if (only != null) many = true; only = e.getKey(); }
            if (only != null && !many) { how[0] = "the current tab's card (its own colour)"; return only; }
        }
        View marked = null;
        int marks = 0;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getHeight() == 0) continue;
            String m = marked(c, 0);
            if (m != null && ++marks == 1) { marked = c; how[0] = "the current tab's card (" + m + ")"; }
        }
        return marks == 1 ? marked : null;
    }

    private static View byName(View v, String id, int depth) {
        try { if (v.getId() != View.NO_ID && id.equals(v.getResources().getResourceEntryName(v.getId()))) return v; } catch (Throwable ignored) { }
        if (depth >= 4 || !(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { View f = byName(g.getChildAt(i), id, depth + 1); if (f != null) return f; }
        return null;
    }

    private static final Map<View, Long> sDone = new WeakHashMap<View, Long>();

    private static String marked(View v, int depth) {
        if (v.isSelected()) return depth == 0 ? "selected" : v.getClass().getSimpleName() + " selected";
        if (v.isActivated()) return depth == 0 ? "activated" : v.getClass().getSimpleName() + " activated";
        if (depth >= 2 || !(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) { String m = marked(g.getChildAt(i), depth + 1); if (m != null) return m; }
        return null;
    }

    private static int remaining(ViewGroup g) {
        try {
            int range = ((Number) Xp.callMethod(g, "computeVerticalScrollRange")).intValue();
            int extent = ((Number) Xp.callMethod(g, "computeVerticalScrollExtent")).intValue();
            int offset = ((Number) Xp.callMethod(g, "computeVerticalScrollOffset")).intValue();
            return range - extent - offset;
        } catch (Throwable t) { return -1; }
    }

    private static int marginBottom(View v) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        return lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).bottomMargin : 0;
    }
}
