package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class ChromeLayout {
    private ChromeLayout() {}

    static final String KEY = "pixelsuite_chrome_bottom_bar";
    private static final java.util.WeakHashMap<View, Integer> sHeldGone = new java.util.WeakHashMap<View, Integer>();
    private static final String[] HIDE = {"home_button", "tab_switcher_button", "optional_toolbar_button_container", "optional_toolbar_button", "signin_button"};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Holder> sHolders = new WeakHashMap<View, Holder>();
    private static boolean sObserving;
    private static volatile boolean sOn;
    static boolean on() { return sOn; }

    static final WeakHashMap<View, Integer> sForcedPadTop = new WeakHashMap<View, Integer>();
    static final WeakHashMap<View, int[]> sForcedMargins = new WeakHashMap<View, int[]>();
    private static boolean sGeometryHooks;
    private static final WeakHashMap<View, Boolean> sTransitionSnapshots = new WeakHashMap<View, Boolean>();
    private static boolean sTransitionOpening;
    private static float sTransitionFraction;
    static void snapshotStarted(View image, boolean opening) {
        sTransitionOpening=opening;sTransitionFraction=0f;
        sTransitionSnapshots.put(image, Boolean.TRUE);
        image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { }
            public void onViewDetachedFromWindow(View v) {
                sTransitionSnapshots.remove(v);
                v.removeOnAttachStateChangeListener(this);
                for (Holder h : live()) if (h != null) h.apply();
            }
        });
        for (Holder h : live()) if (h != null) h.apply();
    }
    static void snapshotProgress(boolean opening,float fraction) {
        sTransitionOpening=opening;sTransitionFraction=fraction;
        for(Holder h:live())if(h!=null)h.apply();
    }
    private static boolean transitionActive() {
        if (ChromeTabTransition.active()) return true;
        for (View image : sTransitionSnapshots.keySet()) if (image != null && image.isAttachedToWindow()) return true;
        return false;
    }

    static void refresh() {
        for (Holder h : live()) if (h != null) h.apply();
    }

    private static java.util.List<Holder> live() {
        java.util.ArrayList<Holder> out = new java.util.ArrayList<Holder>();
        java.util.Iterator<Map.Entry<View, Holder>> it = sHolders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<View, Holder> e = it.next();
            View t = e.getKey();
            Activity a = t == null ? null : activity(t.getContext());
            if (t == null || e.getValue() == null || a == null || a.isDestroyed() || a.isFinishing()) {
                try { if (t != null) t.getViewTreeObserver().removeOnGlobalLayoutListener(e.getValue()); } catch (Throwable ignored) { }
                it.remove();
                continue;
            }
            out.add(e.getValue());
        }
        return out;
    }

    static int barColor(int fallback) {
        int any = 0;
        for (Holder h : live()) if (h != null && h.bar != null && h.bar.bgColor != 0) {
            if (h.bar.isAttachedToWindow()) return h.bar.bgColor;
            if (any == 0) any = h.bar.bgColor;
        }
        return any != 0 ? any : fallback;
    }

    private static int sNight = -1;

    private static void theme(Context c) {
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (sNight == night) return;
        boolean changed = sNight != -1;
        sNight = night;
        sNtpMeasured = 0;
        try { Integer n = (Integer) barPrefs(c).getAll().get(ntpKey()); if (n != null) sNtpMeasured = n; } catch (Throwable ignored) { }
        if (changed) {
            try { ChromeTabTransition.themeChanged(); } catch (Throwable ignored) { }
        }
    }

    private static String ntpKey() { return sNight == Configuration.UI_MODE_NIGHT_YES ? "ntpD" : "ntpL"; }

    static int sheetColor(int fallback) { return sheetColor(fallback, null); }

    static int sheetColor(int fallback, View any) {
        boolean dark = darkColor(fallback);
        int bar = barColor(0);
        if (sheetFits(bar, dark)) return bar;
        View root = any == null ? null : any.getRootView();
        if (root != null) {
            for (String id : new String[]{"toolbar", "control_container", "toolbar_container"}) {
                int c = ChromeTabTransition.solid(find(root, id));
                if (sheetFits(c, dark)) return c;
            }
        }
        int ntp = ntpColor();
        if (sheetFits(ntp, dark)) return ntp;
        return fallback;
    }

    private static boolean sheetFits(int c, boolean dark) {
        if (c == 0 || Color.alpha(c) != 255 || darkColor(c) != dark) return false;
        int chroma = Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c))) - Math.min(Color.red(c), Math.min(Color.green(c), Color.blue(c)));
        return chroma <= 24;
    }

    static void refineSheet(View any, final android.graphics.drawable.GradientDrawable shape, final int strokePx, final int fg) {
        try {
            if (any == null || shape == null) return;
            View bar = find(any.getRootView(), "control_container");
            if (bar == null || !bar.isShown() || bar.getWidth() < 20 || bar.getHeight() < 20) return;
            int[] at = new int[2];
            bar.getLocationOnScreen(at);
            int mid = at[1] + bar.getHeight() / 2;
            ChromeTabTransition.readBack(any, new android.graphics.Rect(at[0] + 1, mid - 4, at[0] + 5, mid + 4), new ChromeTabTransition.Got() {
                public void got(int c) {
                    if (!sheetFits(c, !darkColor(fg))) return;
                    shape.setColor(c);
                    int o = sheetOutline(c, fg);
                    if (o != 0) shape.setStroke(strokePx, o);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static boolean darkColor(int c) {
        return (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 < 128;
    }

    static int sheetOutline(int bg, int fg) {
        int l = (Color.red(bg) * 299 + Color.green(bg) * 587 + Color.blue(bg) * 114) / 1000;
        if (l < 24) return ChromeTabTransition.mix(bg, fg, 0.16f);
        if (l > 200) return ChromeTabTransition.mix(bg, fg, 0.08f);
        return 0;
    }

    static boolean barShowsNtp() {
        for (Holder h : live()) if (h != null && h.bar != null && h.bar.isAttachedToWindow()) return h.bar.ntp;
        return false;
    }

    static int barTotal() {
        for (Holder h : live()) if (h != null && h.bar != null && h.bar.isAttachedToWindow()) return h.bar.total();
        return 0;
    }

    static void expectNtp() {
        for (Holder h : live()) if (h != null && h.bar != null && !h.bar.ntp && h.bar.getAlpha() < 0.05f) h.bar.mode(true, false);
    }

    private static long sSwitcherAt;

    private static boolean sMeasureFix;
    private static int sEdge;
    private static long sMeasureLogAt;

    private static synchronized void measureFix(Class<?> toolbarClass) {
        if (sMeasureFix) return;
        sMeasureFix = true;
        Xp.hookAllMethods(toolbarClass, "onMeasure", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Holder h = sOn ? sHolders.get(p.thisObject) : null;
                    if (h != null && h.holding) h.holdButtons((ViewGroup) p.thisObject);
                } catch (Throwable ignored) { }
                try { holdHidden("measuring"); } catch (Throwable ignored) { }
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { steadyLocationBar((ViewGroup) p.thisObject); } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(toolbarClass, "draw", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try { holdHidden("drawing"); } catch (Throwable ignored) { }
            }
        });
    }

    private static void holdHidden(String when) {
        if (!sOn || sHeldGone.isEmpty()) return;
        for (View v : new java.util.ArrayList<View>(sHeldGone.keySet())) {
            if (v == null) continue;
            int was = v.getVisibility();
            if (was == View.GONE) continue;
            sHeldGone.put(v, was);
            v.setVisibility(View.GONE);
        }
    }

    private static volatile boolean sLastAtBottom;
    private static int[] sHideIds;

    private static int[] hideIds(Context c) {
        int[] ids = sHideIds;
        if (ids != null) return ids;
        ids = new int[HIDE.length];
        for (int i = 0; i < HIDE.length; i++) ids[i] = resId(c, HIDE[i]);
        sHideIds = ids;
        return ids;
    }

    private static final WeakHashMap<ViewGroup, View[]> sBarParts = new WeakHashMap<ViewGroup, View[]>();

    private static View[] parts(ViewGroup t) {
        View[] p = sBarParts.get(t);
        if (p != null && p[0] != null && p[0].getParent() == t && p[1] != null && p[1].getParent() == t) return p;
        p = new View[]{find(t, "location_bar"), find(t, "toolbar_buttons"), find(t, "url_bar")};
        if (p[0] != null && p[1] != null) sBarParts.put(t, p);
        return p;
    }

    private static void steadyLocationBar(ViewGroup t) {
        if (!sOn) return;
        Holder holder = sHolders.get(t);
        if (holder == null || !holder.holding) return;
        View[] parts = parts(t);
        View lb = parts[0], buttons = parts[1], url = parts[2];
        if (lb == null || buttons == null || lb.getParent() != t || !(lb.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        if (url != null && url.hasFocus()) return;
        for (View h : sHeldGone.keySet()) if (h != null && h.getVisibility() != View.GONE) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) lb.getLayoutParams();
        int dp16 = Math.round(16 * t.getResources().getDisplayMetrics().density);
        if (lp.leftMargin > 0 && lp.leftMargin <= dp16 && (sEdge == 0 || lp.leftMargin < sEdge)) sEdge = lp.leftMargin;
        if (sEdge == 0) return;
        int edge = sEdge;
        int left = edge;
        for (int i = 0; i < t.getChildCount(); i++) {
            View c = t.getChildAt(i);
            if (c == lb || c == buttons || c.getVisibility() == View.GONE || c.getWidth() == 0 || c.getHeight() == 0) continue;
            if (c.getRight() <= lb.getLeft() + lb.getWidth() / 2 && c.getLeft() < t.getWidth() / 3 && c.getRight() > left && c.getRight() < t.getWidth() / 2) left = c.getRight();
        }
        int right = buttons.getVisibility() == View.GONE ? 0 : buttons.getMeasuredWidth();
        int width = t.getMeasuredWidth() - left - right;
        if (width <= 0 || lp.width >= width - 1 || lp.width <= 0) return;
        int oldLeft = lp.leftMargin, oldWidth = lp.width;
        lp.leftMargin = left;
        lp.width = width;
        lb.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(lb.getMeasuredHeight(), View.MeasureSpec.EXACTLY));
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sMeasureLogAt > 2000) {
            sMeasureLogAt = now;
        }
    }

    private static synchronized void geometryHooks() {
        if (sGeometryHooks) return;
        sGeometryHooks = true;
        try {
            Xp.Callback pad = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (sForcedPadTop.isEmpty() || p.args.length < 4) return;
                    Integer top = sForcedPadTop.get(p.thisObject);
                    if (top != null) p.args[1] = top;
                }
            };
            Xp.hookAllMethods(View.class, "setPadding", pad);
            Xp.Callback hold = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (sHeldGone.isEmpty() || p.args.length != 1 || !(p.args[0] instanceof Integer)) return;
                    Object v = p.thisObject;
                    if (!sHeldGone.containsKey(v)) return;
                    int want = (Integer) p.args[0];
                    if (want == View.GONE) return;
                    sHeldGone.put((View) v, want);
                    p.args[0] = View.GONE;
                }
            };
            Xp.hookAllMethods(View.class, "setVisibility", hold);
            Xp.hookAllMethods(ImageView.class, "setVisibility", hold);
            Xp.hookAllMethods(View.class, "setPaddingRelative", pad);
            Xp.hookAllMethods(View.class, "setLayoutParams", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (sForcedMargins.isEmpty() || p.args.length < 1 || !(p.args[0] instanceof ViewGroup.MarginLayoutParams)) return;
                    int[] m = sForcedMargins.get(p.thisObject);
                    if (m == null) return;
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) p.args[0];
                    lp.topMargin = m[0];
                    lp.bottomMargin = m[1];
                }
            });
        } catch (Throwable ignored) { }
    }


    static void attach(final ViewGroup toolbar) {
        try { ChromeUrlBar.install(toolbar.getClass().getClassLoader()); } catch (Throwable ignored) { }
        try { ChromeGeo.install(toolbar.getClass().getClassLoader()); } catch (Throwable ignored) { }
        try { measureFix(toolbar.getClass()); } catch (Throwable ignored) { }
        geometryHooks();
        Context c = toolbar.getContext();
        if (!(activity(c) instanceof Activity) || !activity(c).getClass().getName().endsWith("ChromeTabbedActivity")) return;
        observe(c.getApplicationContext());
        theme(c);
        live();
        if (sHolders.containsKey(toolbar)) return;
        final Holder h = new Holder(toolbar);
        sHolders.put(toolbar, h);
        toolbar.getViewTreeObserver().addOnGlobalLayoutListener(h);
        toolbar.post(new Runnable() { public void run() { h.apply(); } });
    }

    private static Activity activity(Context c) {
        for (int i = 0; i < 10 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof android.content.ContextWrapper)) break;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static synchronized void observe(final Context app) {
        sOn = read(app);
        if (sObserving) return;
        sObserving = true;
        try {
            app.registerComponentCallbacks(new android.content.ComponentCallbacks() {
                public void onConfigurationChanged(Configuration cfg) { MAIN.post(new Runnable() { public void run() {
                    for (Holder h : live()) { ViewGroup t = h.toolbar.get(); if (t != null && t.isAttachedToWindow()) { theme(t.getContext()); return; } }
                } }); }
                public void onLowMemory() { }
            });
        } catch (Throwable ignored) { }
        try {
            app.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, new ContentObserver(MAIN) {
                @Override public void onChange(boolean self) {
                    sOn = read(app);
                    for (Holder h : live()) if (h != null) h.apply();
                }
            });
        } catch (Throwable ignored) { }
    }

    private static boolean read(Context c) {
        return enabled(c);
    }

    private static volatile long premiumUntil;
    private static volatile boolean premiumAllowed;

    static boolean enabled(Context c) {
        try {
            if (Settings.Secure.getInt(c.getContentResolver(), KEY, 0) != 1) return false;
            long now = android.os.SystemClock.uptimeMillis();
            if (now >= premiumUntil) {
                premiumAllowed = PatronGate.ok(c);
                premiumUntil = now + 30000;
            }
            return premiumAllowed;
        } catch (Throwable t) { return false; }
    }

    private static String name(View v) {
        try { return v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return ""; }
    }

    private static View find(View root, String id) {
        if (root == null) return null;
        if (id.equals(name(root))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), id); if (f != null) return f; }
        }
        return null;
    }

    private static int resId(Context c, String name) {
        try { return c.getResources().getIdentifier(name, "id", c.getPackageName()); } catch (Throwable t) { return 0; }
    }

    private static int insetType(String name) throws Exception {
        return (Integer) Class.forName("android.view.WindowInsets$Type").getMethod(name).invoke(null);
    }

    private static Boolean insetVisible(WindowInsets in, String type) {
        try { return (Boolean) WindowInsets.class.getMethod("isVisible", int.class).invoke(in, insetType(type)); } catch (Throwable t) { return null; }
    }

    static int navInset(View v) {
        WindowInsets in = v == null ? null : v.getRootWindowInsets();
        if (in == null) return 0;
        try {
            Object ins = WindowInsets.class.getMethod("getInsetsIgnoringVisibility", int.class).invoke(in, insetType("navigationBars"));
            return (Integer) ins.getClass().getField("bottom").get(ins);
        } catch (Throwable t) {
            try { return in.getStableInsetBottom(); } catch (Throwable ignored) { return 0; }
        }
    }

    private static WeakReference<View> sNtpHost = new WeakReference<View>(null);

    static boolean newTabPage(View root) {
        if (findNtp(root)) return true;
        View host = sNtpHost.get();
        return host != null && host.isAttachedToWindow() && host.isShown() && host.getWidth() > 0;
    }

    private static boolean findNtp(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE) return false;
        String n = root.getClass().getName();
        if ((n.endsWith("NewTabPageLayout") || n.endsWith("NewTabPageView")) && root.isShown() && root.getWidth() > 0) {
            View host = root;
            for (Object q = root.getParent(); q instanceof View; q = ((View) q).getParent()) {
                if (q.getClass().getName().contains("RecyclerView")) { host = (View) q; break; }
                if (q.getClass().getName().endsWith("CompositorViewHolder")) break;
            }
            if (sNtpHost.get() != host) sNtpHost = new WeakReference<View>(host);
            return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) if (findNtp(g.getChildAt(i))) return true;
        }
        return false;
    }

    static boolean ntpFeedShown() {
        View host = sNtpHost.get();
        return host != null && host.isAttachedToWindow() && host.isShown() && host.getWidth() > 0;
    }

    static int ntpColor() { return sNtpMeasured; }

    private static final HashMap<String, Integer> sBarMeasured = new HashMap<String, Integer>();
    private static int sNtpMeasured;
    private static boolean sBarLoaded;

    private static android.content.SharedPreferences barPrefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("pixelsuite_chrome_ui", Context.MODE_PRIVATE);
    }

    private static void loadBar(Context c) {
        if (sBarLoaded) return;
        sBarLoaded = true;
        try {
            for (Map.Entry<String, ?> e : barPrefs(c).getAll().entrySet()) {
                if (!(e.getValue() instanceof Integer)) continue;
                if (e.getKey().startsWith("bar_")) {
                    String key = e.getKey().substring(4);
                    int c0 = (Integer) e.getValue();
                    if (!keepable(key, c0)) { barPrefs(c).edit().remove(e.getKey()).apply(); continue; }
                    sBarMeasured.put(key, c0);
                }
                else if (e.getKey().equals(ntpKey())) sNtpMeasured = (Integer) e.getValue();
            }
        } catch (Throwable ignored) { }
    }

    private static boolean keepable(String key, int color) {
        boolean shared = key.endsWith("0") && key.length() <= 3;
        int chroma = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color))) - Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
        return !shared || chroma <= 24;
    }

    private static void saveBar(Context c, String key, int color, boolean ntp) {
        if (!keepable(key, color)) return;
        try {
            android.content.SharedPreferences.Editor e = barPrefs(c).edit().putInt("bar_" + key, color);
            if (ntp) e.putInt(ntpKey(), color);
            e.apply();
        } catch (Throwable ignored) { }
    }

    private static int ntpBackground() {
        for (View v = sNtpHost.get(); v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            int c = ChromeTabTransition.solid(v);
            if (c != 0) return c;
        }
        return 0;
    }

    static boolean menuAction(Activity a, String id) {
        int res = resId(a, id);
        if (res == 0) return false;
        for (Class<?> k = a.getClass(); k != null && k != Activity.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (m.getReturnType() != boolean.class || t.length != 4 || t[0] != int.class || t[1] != boolean.class || t[2] != Bundle.class || t[3].isPrimitive()) continue;
                try { m.setAccessible(true); return Boolean.TRUE.equals(m.invoke(a, res, false, null, null)); } catch (Throwable ignored) { }
            }
        }
        return false;
    }

    private static final class Holder implements ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<ViewGroup> toolbar;
        final Map<View, Integer> before = new HashMap<View, Integer>();
        Bar bar;
        View lifted;
        int liftedBy;
        String lastWhy = "";
        WeakReference<View> movedHub = new WeakReference<View>(null), movedPane = new WeakReference<View>(null);
        boolean searchHidden;
        WeakReference<View> hairline = new WeakReference<View>(null);
        WeakReference<android.graphics.drawable.Drawable> headerBg;
        WeakReference<View> watchedHeader = new WeakReference<View>(null), watchedBox = new WeakReference<View>(null);
        int headerColor = 0xFFFBF8FD;
        int boxTop0, boxBottom0, wantTop;

        boolean holding;

        Holder(ViewGroup t) { toolbar = new WeakReference<ViewGroup>(t); holding = sOn && !sLastAtBottom; }

        @Override public void onGlobalLayout() { apply(); }

        void apply() {
            ViewGroup t = toolbar.get();
            if (t == null || !t.isAttachedToWindow()) return;
            Activity a = activity(t.getContext());
            if (a == null) return;
            boolean bottom = toolbarAtBottom(t);
            sLastAtBottom = bottom;
            boolean want = sOn && !bottom;
            if (want) show(a, t); else hide(t);
        }

        void holdButtons(ViewGroup t) {
            int[] ids = hideIds(t.getContext());
            for (int i = 0; i < HIDE.length; i++) {
                View v = ids[i] != 0 ? t.findViewById(ids[i]) : find(t, HIDE[i]);
                if (v == null) continue;
                if (!before.containsKey(v)) before.put(v, v.getVisibility());
                if (!sHeldGone.containsKey(v)) sHeldGone.put(v, before.get(v));
                if (v.getVisibility() != View.GONE) {
                    v.setVisibility(View.GONE);
                }
            }
        }

        private boolean toolbarAtBottom(View t) {
            int[] at = new int[2];
            t.getLocationOnScreen(at);
            return at[1] > t.getResources().getDisplayMetrics().heightPixels / 2;
        }

        private void show(Activity a, ViewGroup t) {
            holding = true;
            for (String id : HIDE) {
                View v = find(t, id);
                if (v == null) continue;
                if (!before.containsKey(v)) before.put(v, v.getVisibility());
                if (!sHeldGone.containsKey(v)) sHeldGone.put(v, before.get(v));
                if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
            }
            View cv = a.findViewById(android.R.id.content);
            if (!(cv instanceof ViewGroup)) return;
            ViewGroup content = (ViewGroup) cv;
            if (content == null) return;
            if (bar == null || bar.getParent() != content) {
                if (bar != null && bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
                bar = new Bar(a, t);
                content.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            }
            String why = (transitionActive() || hubShown(a)) ? "tab switcher" : ime(content) ? "keyboard" : fullscreen(content) ? "fullscreen" : "";
            if (!why.equals(lastWhy)) { lastWhy = why; }
            boolean hidden = !why.isEmpty();
            bar.inset(navInset(content));
            boolean moving = transitionActive();
            float reveal = hidden ? 0f : 1f;
            if (moving) reveal = sTransitionOpening ? 1f - ChromeTabTransition.smooth(sTransitionFraction / 0.22f) : ChromeTabTransition.DECELERATE.getInterpolation(ChromeTabTransition.clamp((sTransitionFraction - 0.5f) / 0.4f));
            bar.reveal(reveal, moving);
            if (moving && reveal > 0.001f && content.getChildAt(content.getChildCount() - 1) != bar) bar.bringToFront();
            bar.style("tab switcher".equals(why));
            bar.cover(ChromeTabTransition.covering());
            bar.fillCutout();
            chin(content, "tab switcher".equals(why) && !moving && hubShown(a));
            lift(a, hidden && !"tab switcher".equals(why) ? 0 : bar.lift());
            moveHub(a, true);
        }

        View chin;

        private void chin(ViewGroup content, boolean up) {
            int h = up ? navInset(content) : 0;
            if (h <= 0) { if (chin != null && chin.getVisibility() != View.GONE) chin.setVisibility(View.GONE); return; }
            if (chin == null || chin.getParent() != content) {
                if (chin != null && chin.getParent() instanceof ViewGroup) ((ViewGroup) chin.getParent()).removeView(chin);
                chin = new View(content.getContext());
                chin.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                content.addView(chin, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM));
                followHub(chin);
            }
            ViewGroup.LayoutParams lp = chin.getLayoutParams();
            if (lp.height != h) { lp.height = h; chin.setLayoutParams(lp); }
            if (!(chin.getBackground() instanceof ColorDrawable)) chin.setBackgroundColor(headerColor);
            if (chin.getVisibility() != View.VISIBLE) chin.setVisibility(View.VISIBLE);
            Activity act = activity(content.getContext());
            View hub = act == null ? null : hubView(act, "hub_main_container");
            View branch = hub;
            while (branch != null && branch.getParent() != content) branch = branch.getParent() instanceof View ? (View) branch.getParent() : null;
            if (branch != null) {
                int hubAt = content.indexOfChild(branch), chinAt = content.indexOfChild(chin);
                if (chinAt < hubAt) {
                    content.removeView(chin);
                    content.addView(chin, Math.min(content.indexOfChild(branch) + 1, content.getChildCount()), lp);
                }
            }
        }

        private void followHub(final View strip) {
            final WeakReference<View> tbRef[] = new WeakReference[] { new WeakReference<View>(null) };
            strip.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    try {
                        if (chin != strip) { strip.getViewTreeObserver().removeOnPreDrawListener(this); return true; }
                        if (strip.getVisibility() != View.VISIBLE) return true;
                        View tb = tbRef[0].get();
                        if (tb == null || !tb.isAttachedToWindow()) {
                            Activity act = activity(strip.getContext());
                            tb = act == null ? null : hubView(act, "hub_toolbar");
                            tbRef[0] = new WeakReference<View>(tb);
                        }
                        int c = ChromeTabTransition.solid(tb);
                        if (c != 0 && (!(strip.getBackground() instanceof ColorDrawable) || ((ColorDrawable) strip.getBackground()).getColor() != c)) strip.setBackgroundColor(c);
                    } catch (Throwable ignored) { }
                    return true;
                }
            });
        }

        private void hide(ViewGroup t) {
            holding = false;
            for (Map.Entry<View, Integer> e : before.entrySet()) {
                View v = e.getKey();
                Integer wanted = v == null ? null : sHeldGone.remove(v);
                if (v != null && v.getVisibility() == View.GONE) v.setVisibility(wanted != null ? wanted : e.getValue());
            }
            before.clear();
            if (bar != null) {
                if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
                bar = null;
            }
            if (chin != null) {
                if (chin.getParent() instanceof ViewGroup) ((ViewGroup) chin.getParent()).removeView(chin);
                chin = null;
            }
            Activity a = activity(t.getContext());
            if (a != null) { lift(a, 0); moveHub(a, false); }
            lastWhy = "";
        }

        private View hubView(Activity a, String id) {
            int res = resId(a, id);
            return res == 0 ? null : a.findViewById(res);
        }

        private boolean hubShown(Activity a) {
            View hub = hubView(a, "hub_main_container");
            if (hub == null || !hub.isShown() || hub.getAlpha() < 0.02f) return false;
            android.graphics.Rect r = new android.graphics.Rect();
            return hub.getGlobalVisibleRect(r) && r.height() > hub.getResources().getDisplayMetrics().heightPixels / 2;
        }

        private void moveHub(Activity a, boolean bottom) {
            View tb = hubView(a, "hub_toolbar");
            View header = tb != null && tb.getParent() instanceof View ? (View) tb.getParent() : null;
            View pane = hubView(a, "hub_pane_host");
            if (pane == null) pane = hubView(a, "hub_pane_host_container");

            View root = hubView(a, "hub_main_container");
            if (!bottom) {
                ChromeIncognitoPlus.remove(a);
                View hl = hairline.get();
                if (hl != null) { hl.setAlpha(1f); hairline = new WeakReference<View>(null); }
                View h = movedHub.get();
                if (h != null) { h.setTranslationY(0); h.setAlpha(1f); if (h.getForeground() instanceof ColorDrawable) h.setForeground(null); if (headerBg != null) h.setBackground(headerBg.get()); }
                headerBg = null;
                sForcedPadTop.clear();
                sForcedMargins.clear();
                restoreLists();
                View pn = movedPane.get();
                if (pn != null && pn.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) pn.getLayoutParams();
                    m.topMargin = boxTop0;
                    m.bottomMargin = boxBottom0;
                    pn.setLayoutParams(m);
                }
                movedHub = new WeakReference<View>(null);
                movedPane = new WeakReference<View>(null);
                return;
            }
            if (header == null || root == null || !header.isShown() || header.getHeight() == 0) return;
            ChromeIncognitoPlus.ensure(a, tb);
            int[] h = new int[2], r = new int[2];
            header.getLocationOnScreen(h);
            root.getLocationOnScreen(r);
            float headerTop = h[1] - header.getTranslationY();
            float target = r[1] + root.getHeight() - navInset(root) - header.getHeight();
            float dy = Math.max(0, target - headerTop);
            if (Math.abs(header.getTranslationY() - dy) > 0.5f) header.setTranslationY(dy);
            if (watchedHeader.get() != header) {
                watchedHeader = new WeakReference<View>(header);
                final WeakReference<View> hdr = new WeakReference<View>(header);
                final WeakReference<View> overlay = new WeakReference<View>(hubView(a, "hub_overlay_container"));
                final WeakReference<View> main = new WeakReference<View>(root);
                final WeakReference<View> bar = new WeakReference<View>(tb);
                final long[] since = {0};
                final int hairId = resId(a, "pane_hairline");
                header.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        View h = hdr.get();
                        if (h == null || movedHub.get() != h) return true;
                        View t = bar.get();
                        boolean anim = transitionActive() || (t != null && t.getAlpha() < 0.99f) || animating(overlay.get(), main.get());
                        if(ChromeTabTransition.active())anim=false;
                        long now = android.os.SystemClock.uptimeMillis();
                        if (anim && since[0] == 0) since[0] = now;
                        if (!anim) since[0] = 0;
                        if (anim && now - since[0] > 1200) anim = false;
                        if (h.getAlpha() != 1f) h.setAlpha(1f);
                        View hl = main.get() == null || hairId == 0 ? null : main.get().findViewById(hairId);
                        if (hl != null && hl.getAlpha() != 0f) { hl.setAlpha(0f); hairline = new WeakReference<View>(hl); }
                        View sb = find(h, "search_box");
                        if (sb != null) {
                            boolean tiny = sb.getHeight() <= Math.round(4 * h.getResources().getDisplayMetrics().density);
                            if (tiny && sb.getAlpha() > 0f) { sb.setAlpha(0f); searchHidden = true; }
                            else if (!tiny && searchHidden) { searchHidden = false; if (sb.getAlpha() == 0f) sb.setAlpha(1f); }
                        }
                        android.graphics.drawable.Drawable cover = anim ? new ColorDrawable(headerColor) : null;
                        boolean covered = h.getForeground() instanceof ColorDrawable;
                        if (covered != anim) {
                            h.setForeground(cover);
                        }
                        return true;
                    }
                });
            }
            if (headerBg == null) headerBg = new WeakReference<android.graphics.drawable.Drawable>(header.getBackground());
            int color = ChromeTabTransition.gridColor(root);
            if (color != headerColor || !(header.getBackground() instanceof ColorDrawable) || ((ColorDrawable) header.getBackground()).getColor() != color) {
                header.setBackgroundColor(color);
                headerColor = color;
            }
            movedHub = new WeakReference<View>(header);
            if (pane != null && pane != header) {
                View box = pane;
                while (box.getParent() instanceof View && box.getParent() != root && !holds((View) box.getParent(), header)) box = (View) box.getParent();
                ViewGroup.LayoutParams lp = box.getLayoutParams();
                if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
                    return;
                }
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                if (movedPane.get() != box) {
                    int[] p = new int[2];
                    box.getLocationOnScreen(p);
                    boxTop0 = m.topMargin;
                    boxBottom0 = m.bottomMargin;
                    wantTop = Math.max(0, m.topMargin - Math.round(p[1] - headerTop));
                    movedPane = new WeakReference<View>(box);
                }
                int wantBottom = boxBottom0 + header.getHeight() + navInset(root);
                sForcedMargins.put(box, new int[]{wantTop, wantBottom});
                if (m.topMargin != wantTop || m.bottomMargin != wantBottom) {
                    m.topMargin = wantTop;
                    m.bottomMargin = wantBottom;
                    box.setLayoutParams(m);
                }
                if (watchedBox.get() != box) {
                    watchedBox = new WeakReference<View>(box);
                    final WeakReference<View> bx = new WeakReference<View>(box);
                    box.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            View b = bx.get();
                            int[] want = b == null ? null : sForcedMargins.get(b);
                            if (want == null || !(b.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return true;
                            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) b.getLayoutParams();
                            if (lp.topMargin == want[0] && lp.bottomMargin == want[1]) return true;
                            lp.topMargin = want[0];
                            lp.bottomMargin = want[1];
                            b.setLayoutParams(lp);
                            return false;
                        }
                    });
                }
                trimLists(box, Math.round(8 * box.getResources().getDisplayMetrics().density));
            }
        }

        private final WeakHashMap<View, Integer> listPads = new WeakHashMap<View, Integer>();

        private void trimLists(View v, int want) {
            if (v == null) return;
            if ("tab_list_recycler_view".equals(name(v))) {
                if (!listPads.containsKey(v)) {
                    listPads.put(v, v.getPaddingTop());
                }
                if (listPads.get(v) > want) {
                    sForcedPadTop.put(v, want);
                    if (v.getPaddingTop() != want) v.setPadding(v.getPaddingLeft(), want, v.getPaddingRight(), v.getPaddingBottom());
                }
                return;
            }
            if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) trimLists(g.getChildAt(i), want); }
        }

        private void restoreLists() {
            for (Map.Entry<View, Integer> e : listPads.entrySet()) {
                View v = e.getKey();
                if (v != null) v.setPadding(v.getPaddingLeft(), e.getValue(), v.getPaddingRight(), v.getPaddingBottom());
            }
            listPads.clear();
        }

        private boolean animating(View overlay, View main) {
            if (main != null && main.getAlpha() < 0.99f) return true;
            return overlay instanceof ViewGroup && image((ViewGroup) overlay, main, 0);
        }

        private boolean image(ViewGroup g, View skip, int depth) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c == skip || c.getVisibility() != View.VISIBLE) continue;
                if (c instanceof ImageView && ((ImageView) c).getDrawable() != null) return true;
                if (depth < 2 && c instanceof ViewGroup && image((ViewGroup) c, skip, depth + 1)) return true;
            }
            return false;
        }

        private boolean holds(View group, View child) {
            for (Object v = child; v instanceof View; v = ((View) v).getParent()) if (v == group) return true;
            return false;
        }

        private boolean ime(View root) {
            WindowInsets in = root.getRootWindowInsets();
            if (in == null) return false;
            Boolean v = insetVisible(in, "ime");
            if (v != null) return v;
            return in.getSystemWindowInsetBottom() - in.getStableInsetBottom() > 80 * root.getResources().getDisplayMetrics().density;
        }

        private boolean fullscreen(View root) {
            WindowInsets in = root.getRootWindowInsets();
            if (in == null) return false;
            Boolean status = insetVisible(in, "statusBars"), nav = insetVisible(in, "navigationBars");
            if (status != null && nav != null) return !status && !nav;
            return false;
        }

        private void lift(Activity a, int by) {
            View holder = lifted;
            if (holder == null || !holder.isAttachedToWindow()) {
                int id = resId(a, "compositor_view_holder");
                holder = id == 0 ? null : a.findViewById(id);
                lifted = holder;
                liftedBy = 0;
            }
            if (holder == null || liftedBy == by) return;
            ViewGroup.LayoutParams lp = holder.getLayoutParams();
            if (!(lp instanceof ViewGroup.MarginLayoutParams)) return;
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            m.bottomMargin = Math.max(0, m.bottomMargin - liftedBy + by);
            liftedBy = by;
            holder.setLayoutParams(m);
        }
    }

    private enum Kind { BACK, FORWARD, HOME, TABS, PROFILE }

    static final class Bar extends LinearLayout {
        private final WeakReference<ViewGroup> toolbar;
        private final int height;
        private int inset;

        Bar(final Activity a, ViewGroup t) {
            super(a);
            toolbar = new WeakReference<ViewGroup>(t);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setClickable(true);
            height = Math.round(56 * getResources().getDisplayMetrics().density);
            for (Kind k : Kind.values()) {
                Button b = new Button(a, k, this);
                addView(b, new LinearLayout.LayoutParams(0, height, 1));
            }
            inset(navInset(a.getWindow().getDecorView()));
        }

        void inset(int b) { if (b != inset) { inset = b; setPadding(0, 0, 0, inset); } }

        int total() { return height + inset; }

        int lift() { return height; }

        boolean ntp;
        int bgColor;
        private long sampledAt;
        private int sampled;
        private boolean pendingPage;
        private long pendingSince;
        private final Runnable restyle = new Runnable() { public void run() { refresh(); } };

        private boolean covered;

        void cover(boolean on) {
            if (on == covered) return;
            covered = on;
            if (bgAnim != null) bgAnim.cancel();
            if (on) setBackgroundColor(0);
            else if (bgColor != 0) { shownBg = bgColor; setBackgroundColor(bgColor); }
        }

        private boolean jumped;

        void reveal(float r) { reveal(r, false); }

        void reveal(float r, boolean slide) {
            if (r < 0.999f && !jumped) { jumped = true; for (int i = 0; i < getChildCount(); i++) getChildAt(i).jumpDrawablesToCurrentState(); }
            else if (r >= 0.999f) jumped = false;
            int visibility = r > 0.001f ? View.VISIBLE : View.INVISIBLE;
            if (getVisibility() != visibility) setVisibility(visibility);
            float d = getResources().getDisplayMetrics().density;
            if (slide) {
                float a = ChromeTabTransition.clamp(r * 5f);
                if (getAlpha() != a) setAlpha(a);
                setTranslationY((1f - r) * (getHeight() > 0 ? getHeight() : total()));
                for (int i = 0; i < getChildCount(); i++) {
                    float local = ChromeTabTransition.DECELERATE.getInterpolation(ChromeTabTransition.clamp((r - i * 0.03f) / 0.88f));
                    getChildAt(i).setTranslationY((1f - local) * 8f * d);
                }
                return;
            }
            if (getAlpha() != r) setAlpha(r);
            setTranslationY((1f - r) * 6f * d);
            for (int i = 0; i < getChildCount(); i++) {
                float local = ChromeTabTransition.DECELERATE.getInterpolation(ChromeTabTransition.clamp((r - i * 0.05f) / 0.8f));
                getChildAt(i).setTranslationY((1f - local) * 10f * d);
            }
        }

        void mode(boolean page, boolean animate) {
            ntp = page;
            for (int i = 0; i < getChildCount(); i++) {
                Button b = (Button) getChildAt(i);
                b.setContentDescription(b.label());
                if (animate) b.morph(i * 40L); else b.invalidate();
            }
        }

        int ink() {
            ViewGroup t = toolbar.get();
            View menu = t == null ? null : find(t, "menu_button");
            if (menu instanceof ImageView) {
                ColorStateList l = ((ImageView) menu).getImageTintList();
                if (l != null) return l.getDefaultColor();
            }
            return night() ? 0xFFE3E3E3 : 0xFF444746;
        }

        boolean night() {
            return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }

        void style(boolean switcher) {
            ViewGroup t = toolbar.get();
            int ink = ink();
            boolean lightInk = (Color.red(ink) * 299 + Color.green(ink) * 587 + Color.blue(ink) * 114) / 1000 > 140;
            int bg = lightInk ? 0xFF1F1F1F : 0xFFFFFFFF;
            int exact = 0;
            if (t != null && t.getBackground() instanceof ColorDrawable && Color.alpha(((ColorDrawable) t.getBackground()).getColor()) == 255) exact = ((ColorDrawable) t.getBackground()).getColor();
            View[] parts = t == null ? null : parts(t);
            boolean focused = parts != null && parts[2] != null && parts[2].hasFocus();
            if (focused != lastFocused) {
                lastFocused = focused;
                readAt = 0;
                removeCallbacks(restyle);
                postDelayed(restyle, 350);
            }
            final String key = (ntp ? "m" : "p") + (lightInk ? "L" : "D") + Integer.toHexString(exact) + (focused ? "f" : "");
            loadBar(getContext());
            Integer measured = sBarMeasured.get(key);
            int ntpBg = ntp ? ntpBackground() : 0;
            boolean darkNtpBg = ntpBg != 0 && (Color.red(ntpBg) * 299 + Color.green(ntpBg) * 587 + Color.blue(ntpBg) * 114) / 1000 < 128;
            boolean ntpKnown = ntp && !focused && ((ntpBg != 0 && darkNtpBg == lightInk) || (sNtpMeasured != 0 && ((Color.red(sNtpMeasured) * 299 + Color.green(sNtpMeasured) * 587 + Color.blue(sNtpMeasured) * 114) / 1000 < 128) == lightInk));
            if (measured != null) bg = measured;
            else if (exact != 0 && !ntpKnown) bg = exact;
            else if (ntpBg != 0 && darkNtpBg == lightInk) bg = ntpBg;
            else if (ntp && sNtpMeasured != 0 && ((Color.red(sNtpMeasured) * 299 + Color.green(sNtpMeasured) * 587 + Color.blue(sNtpMeasured) * 114) / 1000 < 128) == lightInk) bg = sNtpMeasured;
            else if (t != null) {
                {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - sampledAt > 400 && t.getWidth() > 0 && t.getHeight() > 0) {
                        sampledAt = now;
                        try {
                            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(16, 4, android.graphics.Bitmap.Config.ARGB_8888);
                            Canvas c = new Canvas(b);
                            c.scale(16f / t.getWidth(), 4f / t.getHeight());
                            t.draw(c);
                            int px = b.getPixel(0, 0);
                            b.recycle();
                            sampled = Color.alpha(px) == 255 ? px : 0;
                        } catch (Throwable ignored) { sampled = 0; }
                    }
                    if (sampled != 0) {
                        boolean darkBg = (Color.red(sampled) * 299 + Color.green(sampled) * 587 + Color.blue(sampled) * 114) / 1000 < 128;
                        if (darkBg == lightInk) bg = sampled;
                    }
                }
            }
            boolean guessed = measured == null && exact == 0 && !(ntpBg != 0 && darkNtpBg == lightInk) && sampled == 0;
            boolean keepCurrent = bgColor != 0 && keepable("pD0", bgColor)
                    && ((Color.red(bgColor) * 299 + Color.green(bgColor) * 587 + Color.blue(bgColor) * 114) / 1000 < 128) == lightInk;
            if (guessed && keepCurrent) bg = bgColor;
            if (!transitionActive()) paintBg(bg, bgColor != 0 && isShown() && getAlpha() > 0.5f);
            Activity a = activity(getContext());
            boolean page = a != null && newTabPage(a.getWindow().getDecorView());
            long now = android.os.SystemClock.uptimeMillis();
            boolean frozen = switcher || transitionActive() || now - sSwitcherAt < 700;
            if (page == ntp) pendingSince = 0;
            else if (frozen) {
                pendingSince = 0;
                if (page && !sTransitionOpening && sTransitionFraction > 0.4f && getAlpha() < 0.05f) mode(true, false);
            } else if (pendingSince == 0 || pendingPage != page) {
                pendingPage = page;
                pendingSince = now;
                removeCallbacks(restyle);
                postDelayed(restyle, 130);
            } else if (now - pendingSince >= 110) {
                pendingSince = 0;
                mode(page, isShown() && getAlpha() > 0.05f);
            } else {
                removeCallbacks(restyle);
                postDelayed(restyle, 130 - (now - pendingSince));
            }
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).invalidate();
            if (!transitionActive() && !switcher && !covered && isShown() && getAlpha() > 0.99f && pendingSince == 0) measure(t, key, lightInk);
        }

        private boolean lastFocused;
        private ValueAnimator bgAnim;
        private int shownBg;
        private long readAt;
        private int lastRead;
        private String lastKey = "";

        private View sideL, sideR;

        void fillCutout() {
            try {
                if (!(getParent() instanceof ViewGroup)) return;
                ViewGroup p = (ViewGroup) getParent();
                android.view.WindowInsets in = p.getRootWindowInsets();
                int l = in == null ? 0 : in.getSystemWindowInsetLeft(), r = in == null ? 0 : in.getSystemWindowInsetRight();
                if (ChromeCutout.sActive) { l = 0; r = 0; }
                sideL = side(p, sideL, l, Gravity.LEFT);
                sideR = side(p, sideR, r, Gravity.RIGHT);
            } catch (Throwable ignored) { }
        }

        private View side(ViewGroup p, View v, int w, int gravity) {
            if (w <= 0 || bgColor == 0 || !isShown() || getAlpha() < 0.5f || covered) {
                if (v != null && v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                return v;
            }
            if (v == null || v.getParent() != p) {
                if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
                v = new View(getContext());
                v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                int at = p.indexOfChild(this);
                p.addView(v, at < 0 ? p.getChildCount() : at, new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT, gravity));
            }
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp.width != w) { lp.width = w; v.setLayoutParams(lp); }
            if (!(v.getBackground() instanceof ColorDrawable) || ((ColorDrawable) v.getBackground()).getColor() != shownOr(bgColor)) v.setBackgroundColor(shownOr(bgColor));
            if (v.getVisibility() != View.VISIBLE) v.setVisibility(View.VISIBLE);
            return v;
        }

        private int shownOr(int c) { return shownBg != 0 ? shownBg : c; }

        void paintBg(int c, boolean animate) {
            if (bgColor != 0 && c == bgColor) fillCutout();
            if (c == bgColor && (covered || shownBg == c)) return;
            bgColor = c;
            fillCutout();
            if (covered) return;
            if (bgAnim != null) bgAnim.cancel();
            if (!animate || shownBg == 0 || shownBg == c) { shownBg = c; setBackgroundColor(c); return; }
            final int from = ChromeSuperDark.mapActive(shownBg), to = ChromeSuperDark.mapActive(c);
            bgAnim = ValueAnimator.ofFloat(0f, 1f);
            bgAnim.setDuration(220);
            bgAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    if (covered) return;
                    float f = (Float) a.getAnimatedValue();
                    shownBg = f >= 1f ? c : ChromeTabTransition.mix(from, to, f);
                    setBackgroundColor(shownBg);
                    if (sideL != null && sideL.getVisibility() == View.VISIBLE) sideL.setBackgroundColor(shownBg);
                    if (sideR != null && sideR.getVisibility() == View.VISIBLE) sideR.setBackgroundColor(shownBg);
                }
            });
            bgAnim.start();
        }

        private void measure(final View t, final String key, final boolean lightInk) {
            if (t == null || !t.isShown() || t.getHeight() == 0) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - readAt < 450) return;
            readAt = now;
            int[] at = new int[2];
            t.getLocationOnScreen(at);
            if (at[1] < 0) return;
            android.graphics.Rect r = new android.graphics.Rect(at[0] + 1, at[1] + t.getHeight() * 3 / 10, at[0] + 5, at[1] + t.getHeight() * 7 / 10);
            final boolean wasNtp = ntp;
            ChromeTabTransition.readBack(t, r, new ChromeTabTransition.Got() {
                public void got(int c) {
                    boolean darkBg = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 < 128;
                    if (darkBg != lightInk) return;
                    if (c == lastRead && key.equals(lastKey)) {
                        Integer old = keepable(key, c) ? sBarMeasured.put(key, c) : sBarMeasured.remove(key);
                        boolean ntpLook = wasNtp && !key.endsWith("f");
                        if (ntpLook) sNtpMeasured = c;
                        if (old == null || old != c) saveBar(getContext(), key, c, ntpLook);
                        if (wasNtp == ntp && !transitionActive()) paintBg(c, isShown() && getAlpha() > 0.5f);
                    } else {
                        lastRead = c;
                        lastKey = key;
                        removeCallbacks(restyle);
                        boolean known = sBarMeasured.containsKey(key);
                        if (!known) readAt = 0;
                        postDelayed(restyle, known ? 480 : 120);
                    }
                }
            });
        }

        int tabs() {
            ViewGroup t = toolbar.get();
            View v = t == null ? null : find(t, "tab_switcher_button");
            CharSequence d = v == null ? null : v.getContentDescription();
            if (d == null) return -1;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(d);
            return m.find() ? Integer.parseInt(m.group()) : -1;
        }

        void act(Kind k, boolean lng) { act(k, lng, null); }

        void act(Kind k, boolean lng, View from) {
            Activity a = activity(getContext());
            ViewGroup t = toolbar.get();
            if (a == null) return;
            try { Haptics.tick(a); } catch (Throwable ignored) { }
            switch (k) {
                case BACK:
                    if (ntp) ChromeExtensions.show(from != null ? from : this); else a.onBackPressed();
                    break;
                case FORWARD:
                    if (ntp) { menuAction(a, "all_bookmarks_menu_id"); break; }
                    if (!menuAction(a, "forward_menu_id")) {
                        try { ChromeCosmetic.runAll(currentWebContents(), "(function () { if (window === window.top) history.forward(); })()"); } catch (Throwable ignored) { }
                    }
                    break;
                case HOME: {
                    if (ntp && ChromeSpeedDial.search(a.getWindow().getDecorView())) break;
                    View home = t == null ? null : find(t, "home_button");
                    if (home != null && home.performClick()) ChromeTabTransition.entrance(a.getWindow().getDecorView(), -1, -1, false);
                    else if (menuAction(a, "new_tab_menu_id")) grow(a, from);
                    break;
                }
                case TABS: {
                    if (lng) { if (menuAction(a, "new_tab_menu_id")) grow(a, from != null ? from : getChildAt(Kind.TABS.ordinal())); break; }
                    View tabs = t == null ? null : find(t, "tab_switcher_button");
                    sSwitcherAt = android.os.SystemClock.uptimeMillis();
                    ChromeTabTransition.openSwitcher(this, tabs);
                    break;
                }
                case PROFILE: {
                    View disc = ntp && t != null ? find(t, "optional_toolbar_button") : null;
                    CharSequence d = disc == null ? null : disc.getContentDescription();
                    String s = d == null ? "" : d.toString().toLowerCase(java.util.Locale.ROOT);
                    if (disc != null && (s.contains("account") || s.contains("profile") || s.contains("sign") || s.contains("@")) && disc.performClick()) break;
                    menuAction(a, "preferences_id");
                    break;
                }
            }
            postDelayed(new Runnable() { public void run() { for (int i = 0; i < getChildCount(); i++) getChildAt(i).invalidate(); } }, 400);
        }

        private void grow(Activity a, View from) {
            float x = -1, y = -1;
            if (from != null) { int[] at = new int[2]; from.getLocationOnScreen(at); x = at[0] + from.getWidth() / 2f; y = at[1] + from.getHeight() / 2f; }
            ChromeTabTransition.entrance(a.getWindow().getDecorView(), x, y, true);
        }

        private Object currentWebContents() {
            try { return ChromeShield.current(this); } catch (Throwable t) { return null; }
        }
    }

    static final class Button extends View {
        private final Kind kind;
        private final Bar bar;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();
        private float press = 1f, tap = -1f, morph = 1f, count = 1f;
        private ValueAnimator pressAnim, tapAnim, morphAnim, countAnim;
        private int shownTabs = -1, oldTabs = -1;

        Button(Context c, Kind k, Bar b) {
            super(c);
            kind = k;
            bar = b;
            setClickable(true);
            setLongClickable(k == Kind.TABS || k == Kind.BACK);
            setContentDescription(label());
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) {
                try { setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { }
            }
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            setOnClickListener(new OnClickListener() { public void onClick(View v) { flourish(); bar.act(kind, false, v); } });
            if (k == Kind.TABS) setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { flourish(); bar.act(kind, true, v); return true; } });
            if (k == Kind.BACK) setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) {
                if (!bar.ntp) return false;
                try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                ChromeExtensions.openPixelSuite(v.getContext(), "extensions");
                return true;
            } });
        }

        String label() {
            if (bar.ntp && kind == Kind.BACK) return "Extensions";
            if (bar.ntp && kind == Kind.FORWARD) return "Bookmarks";
            if (bar.ntp && kind == Kind.HOME) return "Search";
            return new String[]{"Back", "Forward", "Home", "Tabs", "Profile"}[kind.ordinal()];
        }

        private ValueAnimator run(ValueAnimator old, ValueAnimator a) {
            if (old != null) old.cancel();
            a.start();
            return a;
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: {
                    ValueAnimator a = ValueAnimator.ofFloat(press, 0.84f);
                    a.setDuration(130);
                    a.setInterpolator(ChromeTabTransition.DECELERATE);
                    a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { press = (Float) v.getAnimatedValue(); invalidate(); } });
                    pressAnim = run(pressAnim, a);
                    break;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    release();
                    break;
            }
            return super.onTouchEvent(e);
        }

        private void release() {
            final float start = press, dip = Math.min(start, 0.88f);
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(620);
            a.setInterpolator(null);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator v) {
                    float f = (Float) v.getAnimatedValue();
                    press = f < 0.1f ? ChromeTabTransition.lerp(start, dip, ChromeTabTransition.DECELERATE.getInterpolation(f / 0.1f))
                            : ChromeTabTransition.lerp(dip, 1f, ChromeTabTransition.SPRING.getInterpolation((f - 0.1f) / 0.9f));
                    invalidate();
                }
            });
            pressAnim = run(pressAnim, a);
        }

        private void flourish() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(kind == Kind.TABS ? 420 : 560);
            a.setInterpolator(null);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { tap = (Float) v.getAnimatedValue(); invalidate(); } });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator x) { tap = -1f; invalidate(); }
            });
            tapAnim = run(tapAnim, a);
        }

        void morph(long delay) {
            morph = 0f;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setStartDelay(delay);
            a.setDuration(480);
            a.setInterpolator(null);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { morph = (Float) v.getAnimatedValue(); invalidate(); } });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator x) { morph = 1f; invalidate(); }
            });
            morphAnim = run(morphAnim, a);
            invalidate();
        }

        private void rollCount() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(380);
            a.setInterpolator(ChromeTabTransition.DECELERATE);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { count = (Float) v.getAnimatedValue(); invalidate(); } });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator x) { count = 1f; oldTabs = -1; invalidate(); }
            });
            countAnim = run(countAnim, a);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (pressAnim != null) pressAnim.cancel();
            if (tapAnim != null) tapAnim.cancel();
            if (morphAnim != null) morphAnim.end();
            if (countAnim != null) countAnim.end();
            press = 1f;
        }

        @Override protected void onDraw(Canvas c) {
            int save = c.save();
            try { paint(c); } catch (Throwable ignored) { } finally { c.restoreToCount(save); }
        }

        private void paint(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, s = 11 * d;
            if (kind == Kind.TABS) {
                int n = bar.tabs();
                if (n != shownTabs) {
                    if (shownTabs >= 0 && n >= 0 && isShown() && bar.getAlpha() > 0.05f) {
                        oldTabs = shownTabs;
                        count = 0f;
                        post(new Runnable() { public void run() { rollCount(); } });
                    }
                    shownTabs = n;
                }
            }
            int save = c.save();
            c.scale(press, press, cx, cy);
            boolean changes = kind == Kind.BACK || kind == Kind.FORWARD || kind == Kind.HOME;
            if (morph < 1f && changes) {
                float dir = kind == Kind.BACK ? -1f : 1f;
                float o = ChromeTabTransition.clamp(morph / 0.5f), oe = ChromeTabTransition.ACCELERATE.getInterpolation(o);
                if (o < 1f) {
                    int s1 = c.save();
                    float k = 1f - 0.5f * oe;
                    c.rotate(-50f * oe * dir, cx, cy);
                    c.scale(k, k, cx, cy);
                    icon(c, !bar.ntp, 1f - oe, cx, cy, s, -1f);
                    c.restoreToCount(s1);
                }
                float i = ChromeTabTransition.clamp((morph - 0.28f) / 0.72f);
                if (i > 0f) {
                    float ie = ChromeTabTransition.SPRING.getInterpolation(i);
                    int s2 = c.save();
                    float k = 0.5f + 0.5f * ie;
                    c.rotate(40f * (1f - ie) * dir, cx, cy);
                    c.scale(k, k, cx, cy);
                    icon(c, bar.ntp, ChromeTabTransition.clamp(i * 2.2f), cx, cy, s, -1f);
                    c.restoreToCount(s2);
                }
            } else {
                if (morph < 1f) { float k = 1f - 0.08f * (float) Math.sin(Math.PI * morph); c.scale(k, k, cx, cy); }
                icon(c, bar.ntp, 1f, cx, cy, s, tap);
            }
            c.restoreToCount(save);
        }

        private void ink(float alpha) {
            int ink = bar.ink();
            p.setColor(ink);
            p.setAlpha(Math.round(Color.alpha(ink) * ChromeTabTransition.clamp(alpha)));
        }

        private void icon(Canvas c, boolean ntpMode, float alpha, float cx, float cy, float s, float t) {
            if (alpha <= 0.001f) return;
            float d = getResources().getDisplayMetrics().density;
            boolean tapping = t >= 0f;
            float bump = tapping ? (float) Math.sin(Math.PI * t) : 0f, decay = tapping ? 1f - t : 0f;
            int save = c.save();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(2 * d);
            ink(alpha);
            path.reset();
            if (ntpMode && kind == Kind.HOME) {
                if (tapping) { c.rotate(-18f * bump, cx, cy); float k = 1f + 0.12f * bump; c.scale(k, k, cx, cy); }
                p.setStrokeWidth(2.2f * d);
                c.drawCircle(cx - s * 0.18f, cy - s * 0.18f, s * 0.58f, p);
                c.drawLine(cx + s * 0.25f, cy + s * 0.25f, cx + s * 0.85f, cy + s * 0.85f, p);
            } else if (ntpMode && kind == Kind.BACK) {
                if (tapping) c.rotate(16f * (float) Math.sin(3 * Math.PI * t) * decay, cx, cy + s * 0.2f);
                ChromeBarIcons.extensions(c,path,p,cx,cy,s);
            } else if (ntpMode && kind == Kind.FORWARD) {
                if (tapping) { c.rotate(72f * ChromeTabTransition.EMPHASIZED.getInterpolation(t), cx, cy + s * 0.08f); float k = 1f + 0.18f * bump; c.scale(k, k, cx, cy); }
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + Math.PI / 5 * i;
                    float rr = (i % 2 == 0) ? s : s * 0.45f;
                    float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr + s * 0.08f;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, p);
            } else switch (kind) {
                case BACK:
                case FORWARD: {
                    float dir = kind == Kind.BACK ? 1 : -1;
                    if (tapping) c.translate(-dir * 5f * d * bump, 0);
                    path.moveTo(cx + dir * s * 0.9f, cy);
                    path.lineTo(cx - dir * s * 0.9f, cy);
                    path.moveTo(cx - dir * s * 0.15f, cy - s * 0.75f);
                    path.lineTo(cx - dir * s * 0.9f, cy);
                    path.lineTo(cx - dir * s * 0.15f, cy + s * 0.75f);
                    if (kind == Kind.FORWARD) ink(alpha * 170f / 255f);
                    c.drawPath(path, p);
                    break;
                }
                case HOME: {
                    if (tapping) {
                        c.translate(0, -4f * d * bump);
                        float sq = 0.08f * (float) Math.sin(2 * Math.PI * t) * decay;
                        c.scale(1f + sq, 1f - sq, cx, cy + s * 0.85f);
                    }
                    path.moveTo(cx - s * 0.85f, cy - s * 0.05f);
                    path.lineTo(cx, cy - s * 0.85f);
                    path.lineTo(cx + s * 0.85f, cy - s * 0.05f);
                    path.moveTo(cx - s * 0.65f, cy - s * 0.2f);
                    path.lineTo(cx - s * 0.65f, cy + s * 0.85f);
                    path.lineTo(cx + s * 0.65f, cy + s * 0.85f);
                    path.lineTo(cx + s * 0.65f, cy - s * 0.2f);
                    path.moveTo(cx - s * 0.2f, cy + s * 0.85f);
                    path.lineTo(cx - s * 0.2f, cy + s * 0.3f);
                    path.lineTo(cx + s * 0.2f, cy + s * 0.3f);
                    path.lineTo(cx + s * 0.2f, cy + s * 0.85f);
                    c.drawPath(path, p);
                    break;
                }
                case TABS: {
                    if (tapping) { float k = 1f + 0.06f * bump; c.scale(Math.max(0.06f, Math.abs((float) Math.cos(Math.PI * t))) * k, k, cx, cy); }
                    box.set(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f);
                    c.drawRoundRect(box, 4 * d, 4 * d, p);
                    int n = shownTabs >= 0 ? shownTabs : bar.tabs();
                    if (n >= 0) {
                        txt.setColor(bar.ink());
                        float base = cy;
                        if (count < 1f && oldTabs >= 0) {
                            int clip = c.save();
                            c.clipRect(box.left + 1.5f * d, box.top + 1.5f * d, box.right - 1.5f * d, box.bottom - 1.5f * d);
                            float up = n >= oldTabs ? 1f : -1f, shift = 9f * d;
                            number(c, oldTabs, cx, base - up * shift * count, alpha * (1f - count), d);
                            number(c, n, cx, base + up * shift * (1f - count), alpha * count, d);
                            c.restoreToCount(clip);
                        } else number(c, n, cx, base, alpha, d);
                    }
                    break;
                }
                case PROFILE: {
                    if (tapping) { float k = 1f + 0.06f * bump; c.scale(k, k, cx, cy); }
                    c.drawCircle(cx, cy, s * 0.95f, p);
                    c.drawCircle(cx, cy - s * 0.22f, s * 0.32f, p);
                    path.moveTo(cx - s * 0.6f, cy + s * 0.68f);
                    path.cubicTo(cx - s * 0.45f, cy + s * 0.2f, cx + s * 0.45f, cy + s * 0.2f, cx + s * 0.6f, cy + s * 0.68f);
                    c.drawPath(path, p);
                    if (tapping) {
                        ink(alpha * 0.45f * (1f - t));
                        p.setStrokeWidth(1.5f * d);
                        c.drawCircle(cx, cy, s * (0.95f + 0.6f * ChromeTabTransition.DECELERATE.getInterpolation(t)), p);
                    }
                    break;
                }
            }
            c.restoreToCount(save);
        }

        private void number(Canvas c, int n, float cx, float cy, float alpha, float d) {
            if (alpha <= 0.001f) return;
            int ink = bar.ink();
            txt.setColor(ink);
            txt.setAlpha(Math.round(Color.alpha(ink) * ChromeTabTransition.clamp(alpha)));
            txt.setTextSize((n > 99 ? 8 : 11) * d);
            String label = n > 99 ? "99+" : String.valueOf(n);
            c.drawText(label, cx, cy - (txt.descent() + txt.ascent()) / 2f, txt);
        }
    }
}
