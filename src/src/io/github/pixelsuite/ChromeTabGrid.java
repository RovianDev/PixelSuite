package io.github.pixelsuite;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

final class ChromeTabGrid {
    private ChromeTabGrid() {}

    private static final Map<View, Boolean> sShown = new WeakHashMap<View, Boolean>();

    static void install(ClassLoader l) {
        try {
            Class<?> grid = l.loadClass("org.chromium.chrome.browser.tasks.tab_management.TabListRecyclerView");
            Xp.hookAllMethods(grid, "onLayout", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { watch((ViewGroup) p.thisObject); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void watch(final ViewGroup g) {
        if (sShown.containsKey(g)) return;
        sShown.put(g, Boolean.FALSE);
        g.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                try {
                    boolean shown = g.isShown() && g.getHeight() > 0 && g.isAttachedToWindow();
                    boolean was = Boolean.TRUE.equals(sShown.get(g));
                    sShown.put(g, shown);
                    if (!shown || was) return true;
                    if (!ChromeLayout.enabled(g.getContext())) return true;
                    if (g.getChildCount() < 3) return true;
                    int cut = cut(g);
                    if (cut <= 0) return true;
                    g.scrollBy(0, cut);
                    return false;
                } catch (Throwable t) { return true; }
            }
        });
    }

    private static int cut(ViewGroup g) {
        View lowest = null;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getHeight() == 0) continue;
            if (lowest == null || c.getBottom() > lowest.getBottom()) lowest = c;
        }
        if (lowest == null) return 0;
        int bottom = g.getHeight() - g.getPaddingBottom();
        int hidden = lowest.getBottom() + marginBottom(lowest) - bottom;
        if (hidden <= 0 || hidden > lowest.getHeight() * 0.7f) return 0;
        if (!g.canScrollVertically(1)) return 0;
        return hidden;
    }

    private static int marginBottom(View v) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        return lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).bottomMargin : 0;
    }
}
