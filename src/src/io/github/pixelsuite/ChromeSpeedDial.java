package io.github.pixelsuite;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

final class ChromeSpeedDial {
    private ChromeSpeedDial() {}

    static final int COLUMNS = 5, ROWS = 4;
    private static Method sSetMeasured;
    private static final java.util.WeakHashMap<View, Integer> sRelayouts = new java.util.WeakHashMap<View, Integer>();

    private static boolean on(View v) {
        try { return ChromeLayout.enabled(v.getContext()); } catch (Throwable t) { return false; }
    }

    static void install(ClassLoader l) {
        try {
            sSetMeasured = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
            sSetMeasured.setAccessible(true);
        } catch (Throwable ignored) { }
        try {
            Class<?> grid = l.loadClass("org.chromium.chrome.browser.suggestions.tile.MostVisitedTilesLayout");
            Xp.hookAllMethods(grid, "onMeasure", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if (on((View) p.thisObject)) measure((ViewGroup) p.thisObject, (Integer) p.args[0]); } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(grid, "onLayout", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if (on((View) p.thisObject)) { TileDrag.grid((View) p.thisObject); layout((ViewGroup) p.thisObject); } } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(grid, "removeAllViews", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        final ViewGroup g = (ViewGroup) p.thisObject;
                        if (!on(g)) return;
                        new android.os.Handler(android.os.Looper.getMainLooper()).postAtFrontOfQueue(new Runnable() { public void run() { ChromeFolders.ensure(g); TileAdd.ensure(g); } });
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        try {
            Xp.hookAllMethods(View.class, "setOnTouchListener", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length == 1 && p.args[0] instanceof View.OnTouchListener && !(p.args[0] instanceof TileDrag)
                                && p.thisObject.getClass().getName().endsWith("SuggestionsTileView")) {
                            TileDrag.handler((View) p.thisObject, p.args[0]);
                            p.args[0] = new TileDrag((View.OnTouchListener) p.args[0]);
                        }
                    } catch (Throwable ignored) { }
                }
            });
            Xp.Callback shown = new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { TileDrag.shown(p.thisObject); }
            };
            Xp.hookAllMethods(android.widget.PopupWindow.class, "showAtLocation", shown);
            Xp.hookAllMethods(android.widget.PopupWindow.class, "showAsDropDown", shown);
            Xp.hookAllMethods(android.app.Dialog.class, "show", shown);
        } catch (Throwable ignored) { }
        try {
            Class<?> tile = l.loadClass("org.chromium.chrome.browser.suggestions.tile.SuggestionsTileView");
            Xp.hookAllMethods(tile, "onFinishInflate", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if (on((View) p.thisObject)) { plain((View) p.thisObject); ChromeFolders.style((View) p.thisObject); } } catch (Throwable ignored) { }
                }
            });
            for (Method m : tile.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || t.length != 1 || m.getName().length() > 2) continue;
                if (t[0] == boolean.class) {
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param p) {
                            try { if (on((View) p.thisObject)) p.args[0] = false; } catch (Throwable ignored) { }
                        }
                    });
                } else if (!t[0].isPrimitive() && !t[0].getName().startsWith("android.") && !t[0].getName().startsWith("java.")) {
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void afterHookedMethod(Xp.Param p) {
                            try { if (on((View) p.thisObject)) ChromeFolders.style((View) p.thisObject); } catch (Throwable ignored) { }
                        }
                    });
                }
            }
        } catch (Throwable ignored) { }
        try {
            ChromeTabTransition.install();
            Class<?> anim = l.loadClass("org.chromium.chrome.browser.hub.ShrinkExpandAnimator");
            Xp.hookAllConstructors(anim, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length >= 3 && p.args[0] instanceof android.widget.ImageView && p.args[1] instanceof android.graphics.Rect && p.args[2] instanceof android.graphics.Rect)
                            ChromeTabTransition.start(p.thisObject, (android.widget.ImageView)p.args[0], (android.graphics.Rect)p.args[1], (android.graphics.Rect)p.args[2]);
                    } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(anim, "setRect", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { if (p.args.length > 0 && p.args[0] instanceof android.graphics.Rect) ChromeTabTransition.frame(p.thisObject, (android.graphics.Rect)p.args[0]); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        try { ChromeTileIcons.install(); } catch (Throwable ignored) { }
        try { ChromeDialBookmarks.install(l); } catch (Throwable ignored) { }
        try { ChromeTabGrid.install(l); } catch (Throwable ignored) { }
        try { ChromeCutout.install(); } catch (Throwable ignored) { }
        try {
            Class<?> bridge = l.loadClass("org.chromium.chrome.browser.suggestions.mostvisited.MostVisitedSitesBridge");
            Xp.hookAllMethods(bridge, "onURLsAvailable", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        android.content.Context c = (android.content.Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                        if (c == null || !ChromeLayout.enabled(c)) return;
                        ChromeFolders.group(p.thisObject);
                        int i = p.args.length - 1;
                        if (i < 0 || !(p.args[i] instanceof java.util.List)) return;
                        java.util.List<?> all = (java.util.List<?>) p.args[i];
                        java.util.ArrayList<Object> mine = new java.util.ArrayList<Object>();
                        for (Object o : all) if (source(o) == 3) mine.add(o);
                        java.util.ArrayList<ChromeFolders.Site> sites = new java.util.ArrayList<ChromeFolders.Site>();
                        for (Object o : mine) { ChromeFolders.Site x = ChromeFolders.siteOf(o); if (x != null) sites.add(x); }
                        ChromeDialBookmarks.tiles(sites);
                        if (mine.size() != all.size()) {
                            p.args[i] = mine;
                            if (sLeftOut != all.size() - mine.size()) { sLeftOut = all.size() - mine.size(); }
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        try {
            Class<?> natives = l.loadClass("J.N");
            Xp.hookAllMethods(natives, "VIJO", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length == 4 && p.args[3] != null && p.args[1] instanceof Integer && (Integer) p.args[1] == 8
                                && p.args[3].getClass().getName().endsWith("MostVisitedSitesBridge")) {
                            android.content.Context c = (android.content.Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                            if (c != null && ChromeLayout.enabled(c)) p.args[1] = 48;
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static int sLeftOut = -1;

    private static int source(Object suggestion) {
        try {
            java.lang.reflect.Field f = suggestion.getClass().getDeclaredField("d");
            f.setAccessible(true);
            return f.getInt(suggestion);
        } catch (Throwable t) { return 3; }
    }

    private static String name(View v) {
        try { return v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return ""; }
    }

    private static void plain(View tile) {
        if (tile instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) tile;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if ("tile_view_icon_background".equals(name(c))) c.setVisibility(View.INVISIBLE);
                else plain(c);
            }
        }
    }

    private static boolean tile(View v) {
        return v.getVisibility() != View.GONE && !v.getClass().getName().endsWith("Divider");
    }

    private static int width(ViewGroup g, int spec) {
        int mode = View.MeasureSpec.getMode(spec), size = View.MeasureSpec.getSize(spec);
        if (mode != View.MeasureSpec.UNSPECIFIED && size > 0) return size;
        if (g.getParent() instanceof View) {
            View p = (View) g.getParent();
            int w = p.getWidth() > 0 ? p.getWidth() : p.getMeasuredWidth();
            if (w > 0) return w - p.getPaddingLeft() - p.getPaddingRight();
        }
        return g.getResources().getDisplayMetrics().widthPixels;
    }

    private static void measure(ViewGroup g, int widthSpec) throws Exception {
        if (sSetMeasured == null) return;
        int avail = width(g, widthSpec) - g.getPaddingLeft() - g.getPaddingRight();
        int cell = Math.max(1, avail / COLUMNS), gap = Math.round(8 * g.getResources().getDisplayMetrics().density);
        int rowH = 0;
        java.util.ArrayList<View> folders = new java.util.ArrayList<View>();
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (!c.getClass().getName().endsWith("Divider")) {
                if (c.getVisibility() == View.GONE) continue;
            } else { if (c.getVisibility() != View.GONE) c.setVisibility(View.GONE); continue; }
            if (ChromeFolders.isFolder(c)) { folders.add(c); continue; }
            ViewGroup.LayoutParams lp = c.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                if (m.getMarginStart() != 0 || m.getMarginEnd() != 0 || m.leftMargin != 0 || m.rightMargin != 0) { m.setMarginStart(0); m.setMarginEnd(0); m.leftMargin = 0; m.rightMargin = 0; }
            }
            c.measure(View.MeasureSpec.makeMeasureSpec(cell, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (!ChromeFolders.leaving(c)) { rowH = Math.max(rowH, c.getMeasuredHeight()); ChromeFolders.reference(c); }
        }
        for (View f : folders) {
            f.measure(View.MeasureSpec.makeMeasureSpec(cell, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            rowH = Math.max(rowH, f.getMeasuredHeight());
        }
        int n = Math.min(COLUMNS * ROWS, order(g).size());
        int rows = Math.min(ROWS, (n + COLUMNS - 1) / COLUMNS);
        int h = rows == 0 ? 0 : rows * rowH + (rows - 1) * gap + g.getPaddingTop() + g.getPaddingBottom();
        sSetMeasured.invoke(g, avail + g.getPaddingLeft() + g.getPaddingRight(), h);
    }

    private static void layout(final ViewGroup g) {
        if (g.getParent() instanceof View) {
            View p = (View) g.getParent();
            int pw = p.getWidth() - p.getPaddingLeft() - p.getPaddingRight();
            if (pw > 0 && Math.abs(pw - g.getMeasuredWidth()) > 2) {
                Integer tries = sRelayouts.get(g);
                int n = tries == null ? 0 : tries;
                if (n < 3) { sRelayouts.put(g, n + 1); g.post(new Runnable() { public void run() { g.requestLayout(); } }); }
            }
        }
        int avail = g.getWidth() - g.getPaddingLeft() - g.getPaddingRight();
        int cell = Math.max(1, avail / COLUMNS), gap = Math.round(8 * g.getResources().getDisplayMetrics().density);
        int rowH = 0;
        for (int i = 0; i < g.getChildCount(); i++) { View c = g.getChildAt(i); if (tile(c) && !ChromeFolders.leaving(c)) rowH = Math.max(rowH, c.getMeasuredHeight()); }
        java.util.List<View> order = order(g);
        boolean busy = TileDrag.busy(g);
        int folders = 0;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (ChromeFolders.isFolder(c)) folders++;
            else if (tile(c)) ChromeFolders.style(c);
            if (!order.contains(c) && tile(c)) c.layout(0, 0, 0, 0);
        }
        if (folders != ChromeFolders.folders(g.getContext()).size()) ChromeFolders.ensureLater(g);
        for (int at = 0; at < order.size(); at++) {
            View c = order.get(at);
            if (at >= COLUMNS * ROWS) { c.layout(0, 0, 0, 0); continue; }
            int col = at % COLUMNS, row = at / COLUMNS;
            int w = c.getMeasuredWidth(), hh = c.getMeasuredHeight();
            int left = g.getPaddingLeft() + col * cell + (cell - w) / 2;
            int top = g.getPaddingTop() + row * (rowH + gap);
            if (!busy && (c.getTranslationX() != 0 || c.getTranslationY() != 0)) { c.animate().cancel(); c.setTranslationX(0); c.setTranslationY(0); }
            c.layout(left, top, left + w, top + hh);
        }
        for (View c : order) if (!ChromeFolders.isFolder(c)) { ChromeFolders.reference(c); break; }
    }

    static java.util.List<View> order(ViewGroup g) {
        java.util.ArrayList<View> tiles = new java.util.ArrayList<View>(), folders = new java.util.ArrayList<View>(), tail = new java.util.ArrayList<View>();
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (!tile(c)) continue;
            if (ChromeFolders.isFolder(c)) folders.add(c);
            else if (ChromeFolders.leaving(c)) continue;
            else if (TileDrag.pinned(c) || hasSuggestion(c)) tiles.add(c);
            else tail.add(c);
        }
        java.util.Collections.sort(folders, new java.util.Comparator<View>() {
            public int compare(View a, View b) { return Integer.compare(ChromeFolders.folderPos(a), ChromeFolders.folderPos(b)); }
        });
        int n = tiles.size() + folders.size(), ti = 0, fi = 0;
        java.util.ArrayList<View> out = new java.util.ArrayList<View>(n + tail.size());
        for (int i = 0; i < n; i++) {
            if (fi < folders.size() && (ChromeFolders.folderPos(folders.get(fi)) <= i || ti >= tiles.size())) out.add(folders.get(fi++));
            else out.add(tiles.get(ti++));
        }
        out.addAll(tail);
        return out;
    }

    private static boolean hasSuggestion(View tile) {
        for (Class<?> k = tile.getClass(); k != null && k != View.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (!f.getType().getName().endsWith(".SiteSuggestion")) continue;
                try { f.setAccessible(true); return f.get(tile) != null; } catch (Throwable t) { return false; }
            }
        }
        return false;
    }

    static int slotOf(ViewGroup g, java.util.List<View> items, int i) { return i; }

    static int[] slot(ViewGroup g, int slot, View c) {
        int avail = g.getWidth() - g.getPaddingLeft() - g.getPaddingRight();
        int cell = Math.max(1, avail / COLUMNS), gap = Math.round(8 * g.getResources().getDisplayMetrics().density);
        int rowH = 0;
        for (int i = 0; i < g.getChildCount(); i++) { View t = g.getChildAt(i); if (tile(t)) rowH = Math.max(rowH, t.getMeasuredHeight()); }
        int col = slot % COLUMNS, row = slot / COLUMNS;
        return new int[]{g.getPaddingLeft() + col * cell + (cell - c.getWidth()) / 2, g.getPaddingTop() + row * (rowH + gap)};
    }

    static boolean isTile(View v) { return tile(v); }

    static boolean on(View v, boolean unused) { return on(v); }

    static boolean search(View root) {
        View ntp = findNtp(root);
        View box = ntp == null ? null : find(ntp, "search_box");
        if (box != null && box.isShown() && box.performClick()) return true;
        View url = find(root, "url_bar");
        if (url != null) { url.requestFocus(); return url.performClick() || url.isFocused(); }
        return false;
    }

    private static View findNtp(View v) {
        if (v == null) return null;
        String n = v.getClass().getName();
        if ((n.endsWith("NewTabPageLayout") || n.endsWith("NewTabPageView")) && v.isShown()) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = findNtp(g.getChildAt(i)); if (f != null) return f; }
        }
        return null;
    }

    private static View find(View v, String id) {
        if (v == null) return null;
        if (id.equals(name(v))) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), id); if (f != null) return f; }
        }
        return null;
    }
}
