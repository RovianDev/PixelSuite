package io.github.pixelsuite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class SearchDock extends View implements Choreographer.FrameCallback {
    private static final String TAG = "PixelSuite";
    private static ClassLoader loader;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final HashMap<String, Drawable> ICONS = new HashMap<String, Drawable>();
    private static final android.graphics.ColorMatrixColorFilter GRAY = gray();

    private static android.graphics.ColorMatrixColorFilter gray() {
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
        m.setSaturation(0);
        return new android.graphics.ColorMatrixColorFilter(m);
    }

    static final class Zone {
        final PixelSearch.Slot slot; final RectF box = new RectF(), dest = new RectF(); final boolean pill, circle; final String key;
        float press, pressV, pressTo; Drawable icon; long born;
        Zone(PixelSearch.Slot slot, boolean pill, boolean circle) { this.slot = slot; this.pill = pill; this.circle = circle; key = key(slot); }
    }


    static String key(PixelSearch.Slot s) { return s == null ? "pill" : s.type + "|" + s.value; }

    boolean editing;
    private boolean wiggle;
    private PixelSearch.Slot lead;
    private Drawable leadShown;
    private final RectF leadBox = new RectF();

    void wiggle(boolean on) {
        if (wiggle == on) return;
        wiggle = on;
        if (editing && look != null) spares();
        invalidate();
    }

    private void addSpares(Context c, HashMap<String, Zone> kept, long stamp) {
        List<PixelSearch.Slot> have = PixelSearch.slots(c);
        for (char side : new char[]{'L', 'I', 'R'}) {
            for (int n = 0; n < PixelSearch.max(side) - PixelSearch.count(have, side); n++) {
                Zone z = new Zone(new PixelSearch.Slot(side, "empty", "+" + side + n, ""), false, side != 'I');
                z.icon = new SearchGlyph(SearchGlyph.PLUS, look.ink);
                Zone had = kept.get(z.key);
                z.born = had != null ? had.born : stamp;
                zones.add(z);
            }
        }
    }

    private void spares() {
        long stamp = SystemClock.uptimeMillis();
        HashMap<String, Zone> was = new HashMap<String, Zone>();
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (z.pill || !spare(z.key)) continue;
            was.put(z.key, z);
            zones.remove(i);
        }
        if (wiggle) {
            addSpares(getContext(), was, stamp);
            for (Zone z : zones) { Zone old = was.get(z.key); if (old != null) { z.box.set(old.box); was.remove(z.key); } }
        }
        for (Zone gone : was.values()) if (!gone.box.isEmpty()) { gone.born = stamp; leaving.add(gone); }
        if (getWidth() > 0 && getHeight() > 0) layoutZones();
        invalidate();
    }

    static boolean spare(String key) { return key != null && key.startsWith("empty|+"); }

    static char spareSide(String key) { return key.charAt(7); }

    float[] spot(String key) {
        View b = host != null ? host : this;
        int[] at = new int[2]; b.getLocationOnScreen(at);
        float dx = at[0] - (getWidth() - b.getWidth()) / 2f, dy = at[1] - (getHeight() - b.getHeight()) / 2f;
        for (Zone z : zones) if (!z.pill && z.key.equals(key)) return new float[]{z.dest.centerX() + dx, shell.top + dy};
        return null;
    }

    boolean wiggling() { return wiggle; }

    View anchor() { return host != null ? host : this; }

    List<Object[]> pins() {
        ArrayList<Object[]> out = new ArrayList<Object[]>();
        View b = host != null ? host : this;
        int[] at = new int[2]; b.getLocationOnScreen(at);
        float dx = at[0] - (getWidth() - b.getWidth()) / 2f, dy = at[1] - (getHeight() - b.getHeight()) / 2f;
        for (Zone z : zones) if (!z.pill && !"empty".equals(z.slot.type)) out.add(new Object[]{z.key, z.box.centerX() + dx, shell.top + dy});
        if (!leadBox.isEmpty()) out.add(new Object[]{"lead", leadBox.centerX() + dx, shell.top + dy});
        return out;
    }

    String emptyAt(float x, float y) { Zone z = zoneAt(x, y); return z != null && "empty".equals(z.slot.type) ? z.key : null; }

    private static boolean plain(Drawable bg) {
        try {
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(6, 6, android.graphics.Bitmap.Config.ARGB_8888);
            bg.setBounds(0, 0, 6, 6);
            bg.draw(new Canvas(b));
            boolean light = true;
            for (int x = 0; x < 6 && light; x++) for (int y = 0; y < 6; y++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 250 || Color.red(p) < 236 || Color.green(p) < 236 || Color.blue(p) < 236) { light = false; break; }
            }
            b.recycle();
            return light;
        } catch (Throwable t) { return false; }
    }

    static Drawable bare(Drawable d) {
        try {
            if (d != null && d.getClass().getName().equals("android.graphics.drawable.AdaptiveIconDrawable")) {
                d = d.mutate();
                Object bg = d.getClass().getMethod("getBackground").invoke(d), fg = d.getClass().getMethod("getForeground").invoke(d);
                if (fg instanceof Drawable && (bg == null || (bg instanceof Drawable && plain((Drawable) bg)))) return new Inset(((Drawable) fg).mutate());
            }
        } catch (Throwable ignored) { }
        return null;
    }
    String dragKey;
    private PixelSearch.Look look;
    private final ArrayList<Zone> zones = new ArrayList<Zone>(), leaving = new ArrayList<Zone>();
    private Zone pillZone, down;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shell = new RectF(), pill = new RectF();
    private final SearchGlyph search;
    private GlassRim rim, pillRim;
    private float rimRadius = -1, pillRimRadius = -1;
    private Drawable blur;
    private float blurRadius;
    private final RectF body = new RectF();
    private float hidden, ks = 1f;
    private boolean animating, longFired;
    private long lastFrame;
    private float downX, downY;
    private final Runnable longPress = new Runnable() { public void run() { if (down != null) { longFired = true; haptic(HapticFeedbackConstants.LONG_PRESS); SearchEditor.open(SearchDock.this); release(); } } };

    private void haptic(int kind) { View v = host != null ? host : this; v.performHapticFeedback(kind); }

    private final boolean preview;

    SearchDock(Context c) { this(c, false); }

    SearchDock(Context c, boolean preview) {
        super(c);
        this.preview = preview;
        search = new SearchGlyph(SearchGlyph.SEARCH, 0);
        setClickable(!preview);
        setFocusable(!preview);
        if (preview) setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(I18n.cs("Search"));
        reload();
    }

    boolean widget;
    private static final java.util.WeakHashMap<View, SearchDock> WIDGETS = new java.util.WeakHashMap<View, SearchDock>();

    private static void installWidget() {
        try {
            Xp.Callback take = new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try { adopt((android.appwidget.AppWidgetHostView) p.thisObject); }
                    catch (Throwable t) { android.util.Log.w(TAG, "search widget: adopt", t); }
                }
            };
            Xp.hookAllMethods(android.appwidget.AppWidgetHostView.class, "updateAppWidget", take);
            Xp.hookAllMethods(android.appwidget.AppWidgetHostView.class, "setAppWidget", take);
            Xp.hookAllMethods(android.appwidget.AppWidgetHostView.class, "onLayout", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try {
                        final android.appwidget.AppWidgetHostView v = (android.appwidget.AppWidgetHostView) p.thisObject;
                        SearchDock d = WIDGETS.get(v);
                        if (d == null && !ours(v)) return;
                        boolean settled = d != null && d.getParent() == v && v.getChildAt(v.getChildCount() - 1) == d && d.getWidth() == v.getWidth() && d.getHeight() == v.getHeight();
                        if (settled && PatronGate.ok(v.getContext())) for (int i = 0; i < v.getChildCount(); i++) { View k = v.getChildAt(i); if (k != d && k.getVisibility() == VISIBLE) { settled = false; break; } }
                        if (settled) return;
                        v.post(new Runnable() { public void run() { try { adopt(v); } catch (Throwable ignored) { } } });
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { android.util.Log.w(TAG, "search widget: unavailable", t); }
        try {
            Xp.hookAllMethods(android.appwidget.AppWidgetProviderInfo.class, "loadPreviewImage", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try {
                        android.appwidget.AppWidgetProviderInfo info = (android.appwidget.AppWidgetProviderInfo) p.thisObject;
                        if (info.provider == null || !PixelSearch.SUITE.equals(info.provider.getPackageName()) || !"io.github.pixelsuite.SearchWidget".equals(info.provider.getClassName())) return;
                        if (p.args.length == 0 || !(p.args[0] instanceof Context)) return;
                        Drawable shot = portrait((Context) p.args[0]);
                        if (shot != null) p.setResult(shot);
                    } catch (Throwable t) { android.util.Log.w(TAG, "search widget: preview", t); }
                }
            });
        } catch (Throwable t) { android.util.Log.w(TAG, "search widget: preview unavailable", t); }
    }

    private static Drawable portrait(Context c) {
        if (!PatronGate.ok(c)) return null;
        SearchDock d = new SearchDock(c, true);
        float den = c.getResources().getDisplayMetrics().density;
        int w = Math.round(330 * den), h = Math.round(Math.max(66, 62 * d.look.height + 6) * den);
        d.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        d.layout(0, 0, w, h);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(b));
        return new android.graphics.drawable.BitmapDrawable(c.getResources(), b);
    }

    static boolean widgetView(Object v) {
        try {
            for (View x = v instanceof View ? (View) v : null; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null)
                if (x instanceof android.appwidget.AppWidgetHostView) return ours((android.appwidget.AppWidgetHostView) x);
        } catch (Throwable ignored) { }
        return false;
    }

    static void customize(Context c) {
        try { c.startActivity(new Intent().setClassName(PixelSearch.SUITE, "io.github.pixelsuite.SettingsActivity").putExtra("page", PixelSearch.PAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable t) { android.widget.Toast.makeText(c, I18n.cs("Couldn't open it"), android.widget.Toast.LENGTH_SHORT).show(); }
    }

    private static boolean ours(android.appwidget.AppWidgetHostView v) {
        android.appwidget.AppWidgetProviderInfo info = v.getAppWidgetInfo();
        return info != null && info.provider != null && PixelSearch.SUITE.equals(info.provider.getPackageName()) && "io.github.pixelsuite.SearchWidget".equals(info.provider.getClassName());
    }

    private static void adopt(android.appwidget.AppWidgetHostView v) {
        if (!ours(v)) return;
        Context c = v.getContext();
        SearchDock d = WIDGETS.get(v);
        boolean ok = PatronGate.ok(c);
        if (!ok) {
            if (d != null && d.getParent() == v) v.removeView(d);
            for (int i = 0; i < v.getChildCount(); i++) v.getChildAt(i).setVisibility(VISIBLE);
            return;
        }
        if (d == null) {
            d = new SearchDock(c);
            d.widget = true;
            WIDGETS.put(v, d);
            watch(c.getApplicationContext());
        }
        boolean changed = false;
        if (d.getParent() != v) {
            if (d.getParent() instanceof ViewGroup) ((ViewGroup) d.getParent()).removeView(d);
            v.addView(d, new android.widget.FrameLayout.LayoutParams(-1, -1));
            changed = true;
        } else if (v.getChildAt(v.getChildCount() - 1) != d) { d.bringToFront(); changed = true; }
        for (int i = 0; i < v.getChildCount(); i++) { View k = v.getChildAt(i); if (k != d && k.getVisibility() != INVISIBLE) { k.setVisibility(INVISIBLE); changed = true; } }
        if (d.getVisibility() != VISIBLE) { d.setVisibility(VISIBLE); changed = true; }
        if (changed || d.getWidth() != v.getWidth() || d.getHeight() != v.getHeight()) { d.reload(); d.requestLayout(); d.invalidate(); }
        SearchOverlay.warm(c.getApplicationContext());
    }

    private static void refreshWidgets() {
        try {
            for (java.util.Map.Entry<View, SearchDock> e : new ArrayList<java.util.Map.Entry<View, SearchDock>>(WIDGETS.entrySet())) {
                View v = e.getKey();
                SearchDock d = e.getValue();
                if (v == null || d == null) continue;
                if (v instanceof android.appwidget.AppWidgetHostView) adopt((android.appwidget.AppWidgetHostView) v);
                if (d.getParent() == v) { d.reload(); d.requestLayout(); d.invalidate(); }
            }
        } catch (Throwable t) { android.util.Log.w(TAG, "search widget: refresh", t); }
    }

    static boolean wanted(Context c) {
        try {
            if (!PixelSearch.on(c) || !PatronGate.ok(c)) return false;
            return Settings.Secure.getInt(c.getContentResolver(), LauncherFeatures.HIDE_SEARCH, 0) != 1;
        } catch (Throwable t) { return false; }
    }

    static void install(ClassLoader cl) {
        loader = cl;
        installWidget();
        Class<?> launcher = Xp.findClass("com.android.launcher3.Launcher", cl);
        Xp.hookAllMethods(launcher, "onCreate", new Xp.Callback() {
            protected void afterHookedMethod(Xp.Param p) {
                Activity a = (Activity) p.thisObject;
                watch(a.getApplicationContext());
                MAIN.post(KEEP);
            }
        });
        Xp.hookAllMethods(launcher, "onResume", new Xp.Callback() {
            protected void afterHookedMethod(Xp.Param p) {
                ensure(false);
                try {
                    Context c = ((Activity) p.thisObject).getApplicationContext();
                    if ((PixelSearch.on(c) || PixelSearch.feedOn(c)) && PatronGate.ok(c)) SearchOverlay.warm(c);
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(launcher, "onDestroy", new Xp.Callback() {
            protected void beforeHookedMethod(Xp.Param p) { if (hosted != null && hosted.dock.getContext() == p.thisObject) unhost(); }
        });
        Xp.hookAllMethods(launcher, "onNewIntent", new Xp.Callback() {
            protected void afterHookedMethod(Xp.Param p) {
                SearchOverlay.closeSoon(); SearchEditor.closeSoon(); SearchHearts.close();
                try {
                    final Activity a = (Activity) p.thisObject;
                    if (p.args.length > 0 && p.args[0] instanceof android.content.Intent && ((android.content.Intent) p.args[0]).getBooleanExtra(FeedPull.OPEN_EXTRA, false)) {
                        ((android.content.Intent) p.args[0]).removeExtra(FeedPull.OPEN_EXTRA);
                        MAIN.postDelayed(() -> FeedPull.openOnHome(a), 320);
                    }
                } catch (Throwable t) { android.util.Log.w(TAG, "search dock: open after unlock", t); }
            }
        });
        try {
            Xp.hookAllMethods(Xp.findClass("com.android.quickstep.TouchInteractionService", cl), "onInputEvent", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try { if (p.args.length > 0 && p.args[0] instanceof android.view.MotionEvent) SearchOverlay.homeSwipe((android.view.MotionEvent) p.args[0]); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: home swipe", t); }
        try {
            Xp.hookAllMethods(android.view.InputEventReceiver.class, "dispatchInputEvent", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try { for (Object arg : p.args) if (arg instanceof android.view.MotionEvent) { SearchOverlay.homeSwipe((android.view.MotionEvent) arg); break; } } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: input swipe", t); }
        Xp.hookAllMethods(launcher, "onStop", new Xp.Callback() {
            protected void afterHookedMethod(Xp.Param p) { SearchOverlay.dismissNow(); SearchEditor.dismissNow(); SearchHearts.close(); }
        });
        Xp.hookAllMethods(launcher, "onPause", new Xp.Callback() {
            protected void beforeHookedMethod(Xp.Param p) { SearchHearts.close(); }
        });
        try {
            final Class<?> states = Xp.findClass("com.android.launcher3.LauncherState", cl);
            Xp.hookAllMethods(launcher, "onStateSetStart", new Xp.Callback() {
                protected void afterHookedMethod(Xp.Param p) {
                    try { if (p.args.length > 0 && p.args[0] != states.getField("NORMAL").get(null)) SearchOverlay.dismissNow(); } catch (Throwable ignored) { }
                    SearchHearts.close();
                }
            });
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: states", t); }
    }

    private static boolean watching;

    private static void watch(final Context app) {
        if (watching) return;
        watching = true;
        ContentObserver o = new ContentObserver(MAIN) {
            public void onChange(boolean self, Uri uri) {
                String key = uri == null ? null : uri.getLastPathSegment();
                if (PixelSearch.ASK_KEY.equals(key)) { answer(app); return; }
                MAIN.removeCallbacks(ENSURE);
                MAIN.postDelayed(ENSURE, 120);
            }
        };
        for (String key : new String[]{PixelSearch.KEY, PixelSearch.THEME_KEY, PixelSearch.COLOR_KEY, PixelSearch.SHELL_KEY, PixelSearch.PILL_KEY, PixelSearch.GLASS_KEY,
                PixelSearch.SLOTS_KEY, PixelSearch.SCAN_KEY, PixelSearch.COLORFUL_KEY, PixelSearch.WIDTH_KEY, PixelSearch.HEIGHT_KEY, PixelSearch.ASK_KEY, PatronGate.KEY, SuperDark.KEY, SurfaceStyle.KEY, LauncherFeatures.HIDE_SEARCH})
            try { app.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, o); } catch (Throwable ignored) { }
    }

    private static final Runnable ENSURE = new Runnable() { public void run() { ensure(true); } };
    private static final Runnable KEEP = new Runnable() { public void run() { ensure(false); } };

    static final class Hosted {
        ViewGroup hotseat, parent; View stock, shield; SearchDock dock;
        final Rect clip = new Rect(); boolean hadClip, touch, longSet, longClickable;
        View.OnLongClickListener longWas;
        android.view.ViewOutlineProvider outline;
        android.view.ViewTreeObserver observer;
        android.view.ViewTreeObserver.OnPreDrawListener draw;
        View.OnLayoutChangeListener layout;
        final ArrayList<View> quiet = new ArrayList<View>();
        final ArrayList<Integer> quietWas = new ArrayList<Integer>();
        int checks, uiMode;
    }

    private static Hosted hosted;
    private static final Rect NOTHING = new Rect();
    private static final Rect PROBE = new Rect();
    private static boolean warned;

    static ViewGroup hotseat(Activity a) {
        try { return (ViewGroup) LauncherFeatures.get(a, "mHotseat"); } catch (Throwable t) {
            try { return (ViewGroup) LauncherFeatures.call(a, "getHotseat"); } catch (Throwable ignored) { return null; }
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (String n : new String[]{name, Xp.otherSpelling(name)}) {
            if (n == null) continue;
            for (Class<?> k = c; k != null; k = k.getSuperclass()) try { Field f = k.getDeclaredField(n); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    static View qsb(ViewGroup h) {
        try { return (View) field(h.getClass(), "mQsb").get(h); } catch (Throwable t) {
            try { return (View) LauncherFeatures.call(h, "getQsb"); } catch (Throwable ignored) { return null; }
        }
    }

    static SearchDock current() { Hosted x = hosted; return x == null ? null : x.dock; }

    static void ensure() { ensure(true); }

    static void ensure(boolean refresh) {
        if (refresh) refreshWidgets();
        try {
            Activity a = LauncherFeatures.activity.get();
            ViewGroup h = a == null ? null : hotseat(a);
            View stock = h == null ? null : qsb(h);
            boolean want = stock != null && wanted(a) && stock.getParent() instanceof ViewGroup
                    && !"EmptySearch".equals(stock.getClass().getSimpleName()) && stock.getScaleX() != 0f;
            if (hosted != null && (!want || hosted.stock != stock || hosted.hotseat != h || hosted.parent != stock.getParent())) unhost();
            if (want && hosted == null) host(h, stock);
            else if (hosted != null && (refresh || hosted.uiMode != a.getResources().getConfiguration().uiMode)) { hosted.uiMode = a.getResources().getConfiguration().uiMode; hosted.dock.reload(); place(hosted); }
        } catch (Throwable t) {
            if (!warned) { warned = true; android.util.Log.w(TAG, "search dock: unsupported home screen layout", t); }
        }
    }

    private static void host(ViewGroup h, final View stock) {
        final Hosted x = new Hosted();
        x.hotseat = h; x.stock = stock; x.parent = (ViewGroup) stock.getParent();
        x.dock = new SearchDock(h.getContext());
        x.dock.host = stock;
        x.uiMode = h.getResources().getConfiguration().uiMode;
        x.hadClip = stock.getClipBounds(x.clip);
        x.outline = stock.getOutlineProvider();
        stock.setOutlineProvider(null);
        stock.setClipBounds(NOTHING);
        if (stock instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) stock;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                x.quiet.add(c); x.quietWas.add(c.getImportantForAccessibility());
                c.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            x.shield = new Shield(h.getContext(), x.dock);
            g.addView(x.shield, new ViewGroup.LayoutParams(0, 0));
        } else {
            stock.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent e) { return x.dock.forward(e); } });
            x.touch = true;
        }
        try {
            Object info = field(View.class, "mListenerInfo").get(stock);
            x.longWas = info == null ? null : (View.OnLongClickListener) field(info.getClass(), "mOnLongClickListener").get(info);
            x.longClickable = stock.isLongClickable();
            stock.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { x.dock.longPressNow(); return true; } });
            x.longSet = true;
        } catch (Throwable ignored) { }
        x.parent.getOverlay().add(x.dock);
        place(x);
        x.layout = new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { place(x); }
        };
        stock.addOnLayoutChangeListener(x.layout);
        x.draw = new android.view.ViewTreeObserver.OnPreDrawListener() { public boolean onPreDraw() { return frame(x); } };
        x.observer = h.getViewTreeObserver();
        x.observer.addOnPreDrawListener(x.draw);
        hosted = x;
        frame(x);
        stock.invalidate();
    }

    private static void place(Hosted x) {
        View s = x.stock;
        int wide = Math.max(0, s.getWidth()), stock = Math.max(0, s.getHeight());
        SearchDock d = x.dock;
        int hh = stock <= 0 ? 0 : Math.max(stock, Math.round(d.dp(62) * d.look.height) + d.dp(4));
        int w = wide <= 0 ? 0 : Math.max(wide, Math.min(x.parent.getWidth(), Math.round(wide * d.look.width)));
        int top = s.getTop() - (hh - stock) / 2, left = s.getLeft() - (w - wide) / 2;
        if (d.getWidth() == w && d.getHeight() == hh && d.getLeft() == left && d.getTop() == top) return;
        d.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hh, MeasureSpec.EXACTLY));
        d.layout(left, top, left + w, top + hh);
    }

    private static boolean frame(Hosted x) {
        if (hosted != x) return true;
        View s = x.stock;
        SearchDock d = x.dock;
        if (s.getParent() != x.parent || (++x.checks % 30 == 0 && (qsb(x.hotseat) != s || x.uiMode != s.getResources().getConfiguration().uiMode))) { MAIN.post(KEEP); return true; }
        place(x);
        if (d.getAlpha() != s.getAlpha()) d.setAlpha(s.getAlpha());
        if (d.getTranslationX() != s.getTranslationX()) d.setTranslationX(s.getTranslationX());
        if (d.getTranslationY() != s.getTranslationY()) d.setTranslationY(s.getTranslationY());
        if (d.getScaleX() != s.getScaleX()) d.setScaleX(s.getScaleX());
        if (d.getScaleY() != s.getScaleY()) d.setScaleY(s.getScaleY());
        if (d.getPivotX() != s.getPivotX()) d.setPivotX(s.getPivotX());
        if (d.getPivotY() != s.getPivotY()) d.setPivotY(s.getPivotY());
        int vis = s.getVisibility() == VISIBLE ? VISIBLE : INVISIBLE;
        if (d.getVisibility() != vis) d.setVisibility(vis);
        if (!s.getClipBounds(PROBE) || !PROBE.isEmpty()) s.setClipBounds(NOTHING);
        if (s.getOutlineProvider() != null) s.setOutlineProvider(null);
        if (x.shield != null && x.shield.getParent() != s) { hosted = null; restore(x); MAIN.post(KEEP); }
        return true;
    }

    private static void unhost() {
        Hosted x = hosted;
        hosted = null;
        if (x == null) return;
        SearchOverlay.dismissNow();
        SearchEditor.dismissNow();
        restore(x);
    }

    private static void restore(Hosted x) {
        try { if (x.observer != null && x.observer.isAlive()) x.observer.removeOnPreDrawListener(x.draw); else x.hotseat.getViewTreeObserver().removeOnPreDrawListener(x.draw); } catch (Throwable ignored) { }
        try { x.stock.removeOnLayoutChangeListener(x.layout); } catch (Throwable ignored) { }
        try { x.parent.getOverlay().remove(x.dock); } catch (Throwable ignored) { }
        try { if (x.shield != null && x.shield.getParent() instanceof ViewGroup) ((ViewGroup) x.shield.getParent()).removeView(x.shield); } catch (Throwable ignored) { }
        try { if (x.touch) x.stock.setOnTouchListener(null); } catch (Throwable ignored) { }
        try { if (x.longSet) { x.stock.setOnLongClickListener(x.longWas); x.stock.setLongClickable(x.longClickable); } } catch (Throwable ignored) { }
        for (int i = 0; i < x.quiet.size(); i++) try { x.quiet.get(i).setImportantForAccessibility(x.quietWas.get(i)); } catch (Throwable ignored) { }
        try { x.stock.setClipBounds(x.hadClip ? x.clip : null); x.stock.setOutlineProvider(x.outline); x.stock.invalidate(); } catch (Throwable ignored) { }
    }

    static final class Shield extends View {
        final SearchDock dock;
        Shield(Context c, SearchDock dock) {
            super(c);
            this.dock = dock;
            setOutlineProvider(null);
            setTranslationZ(100000f);
            setClickable(true);
            setContentDescription(I18n.cs("Search"));
        }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(0, 0); }
        @Override public void layout(int l, int t, int r, int b) {
            android.view.ViewParent p = getParent();
            if (p instanceof View) super.layout(0, 0, ((View) p).getWidth(), ((View) p).getHeight()); else super.layout(l, t, r, b);
        }
        @Override public boolean onTouchEvent(MotionEvent e) { return dock.forward(e); }
        @Override public boolean performClick() { super.performClick(); dock.openSearch(); return true; }
    }

    View host;

    boolean forward(MotionEvent e) {
        float dx = host == null ? 0 : (getWidth() - host.getWidth()) / 2f, dy = host == null ? 0 : (getHeight() - host.getHeight()) / 2f;
        e.offsetLocation(dx, dy);
        onTouchEvent(e);
        e.offsetLocation(-dx, -dy);
        return true;
    }

    void pulse() {
        if (pillZone == null) return;
        pillZone.pressTo = 1; kick();
        MAIN.postDelayed(() -> { if (pillZone != null) { pillZone.pressTo = 0; kick(); } }, 150);
    }

    void longPressNow() {
        MAIN.removeCallbacks(longPress);
        if (down != null) { longPress.run(); return; }
        longFired = true;
        haptic(HapticFeedbackConstants.LONG_PRESS);
        SearchEditor.open(this);
    }

    void openSearch() { if (PatronGate.ok(getContext())) SearchOverlay.open(this); else ensure(); }

    void reload() {
        Context c = getContext();
        look = new PixelSearch.Look(c);
        search.setColor(look.ink);
        lead = null; leadShown = null;
        try {
            String raw = PixelSearch.string(c, PixelSearch.LEAD_KEY);
            if (raw != null && !raw.trim().isEmpty()) {
                List<PixelSearch.Slot> one = PixelSearch.slots(raw);
                if (!one.isEmpty() && !"empty".equals(one.get(0).type)) {
                    lead = one.get(0);
                    Drawable d = icon(c, lead, look.ink);
                    if (d instanceof SearchGlyph || d instanceof Avatar) leadShown = d;
                    else if (look.colorful) { Drawable b = bare(d); leadShown = b != null ? b : d; }
                    else {
                        Drawable m = monochrome(d, look.ink);
                        if (m == null) try { m = monochrome(c.getPackageManager().getApplicationIcon(lead.pkg()), look.ink); } catch (Throwable ignored) { }
                        if (m == null) { d.mutate().setColorFilter(GRAY); m = d; }
                        leadShown = m;
                    }
                }
            }
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: lead", t); lead = null; leadShown = null; }
        ICONS.clear();
        HashMap<String, Zone> before = new HashMap<String, Zone>();
        for (Zone z : zones) before.put(z.key, z);
        long stamp = SystemClock.uptimeMillis();
        HashMap<String, Zone> kept = new HashMap<String, Zone>(before);
        zones.clear();
        pillZone = new Zone(null, true, false);
        zones.add(pillZone);
        for (PixelSearch.Slot s : PixelSearch.slots(c)) {
            if ("empty".equals(s.type) && !editing) continue;
            Zone z = new Zone(s, false, s.side != 'I');
            Zone old = before.get(z.key);
            z.icon = "empty".equals(s.type) ? new SearchGlyph(SearchGlyph.PLUS, look.ink) : old != null && editing && old.icon != null && !(old.icon instanceof SearchGlyph) ? old.icon : icon(c, s, look.ink);
            zones.add(z);
        }
        if (editing && wiggle) addSpares(c, kept, stamp);
        if (editing) for (Zone z : zones) { Zone old = before.get(z.key); if (old != null) z.box.set(old.box); }
        for (Zone z : zones) kept.remove(z.key);
        for (Zone gone : kept.values()) if (!gone.pill && spare(gone.key) && !gone.box.isEmpty()) { gone.born = stamp; leaving.add(gone); }
        rimRadius = -1; pillRimRadius = -1; blur = null;
        if (getWidth() > 0 && getHeight() > 0) layoutZones();
        requestLayout();
        invalidate();
    }

    static Drawable icon(Context c, PixelSearch.Slot s, int ink) {
        try {
            if ("action".equals(s.type)) return new SearchGlyph(s.value, ink);
            if ("app".equals(s.type)) {
                ComponentName cn = ComponentName.unflattenFromString(s.value);
                Drawable d = cn != null ? c.getPackageManager().getActivityIcon(cn) : c.getPackageManager().getApplicationIcon(s.pkg());
                return d;
            }
            if ("shortcut".equals(s.type)) {
                Drawable d = shortcutIcon(c, s.pkg(), s.rest());
                return d != null ? d : c.getPackageManager().getApplicationIcon(s.pkg());
            }
            if ("contact".equals(s.type)) return new Avatar(c, s, ink);
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: icon " + s.value, t); }
        return new SearchGlyph("dot", ink);
    }

    static Drawable monochrome(Drawable d, int ink) {
        try {
            if (d != null && d.getClass().getName().equals("android.graphics.drawable.AdaptiveIconDrawable")) {
                Object m = d.getClass().getMethod("getMonochrome").invoke(d);
                if (m instanceof Drawable) { Drawable x = ((Drawable) m).mutate(); x.setTint(ink); return new Inset(x); }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    static final class Inset extends Drawable {
        final Drawable d; Inset(Drawable d) { this.d = d; }
        @Override public void draw(Canvas c) { Rect b = getBounds(); int pad = Math.round(b.width() * -0.25f); d.setBounds(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad); d.draw(c); }
        @Override public void setAlpha(int a) { d.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { d.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    static Object shortcutInfo(Context c, String pkg, String id) {
        try {
            LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            Class<?> qc = Class.forName("android.content.pm.LauncherApps$ShortcutQuery");
            Object q = qc.newInstance();
            qc.getMethod("setPackage", String.class).invoke(q, pkg);
            ArrayList<String> ids = new ArrayList<String>(); ids.add(id);
            qc.getMethod("setShortcutIds", List.class).invoke(q, ids);
            qc.getMethod("setQueryFlags", int.class).invoke(q, 1 | 2 | 8 | 16);
            List<?> list = (List<?>) LauncherApps.class.getMethod("getShortcuts", qc, UserHandle.class).invoke(la, q, android.os.Process.myUserHandle());
            return list == null || list.isEmpty() ? null : list.get(0);
        } catch (Throwable t) { return null; }
    }

    static Drawable shortcutIcon(Context c, String pkg, String id) {
        Object info = shortcutInfo(c, pkg, id);
        if (info == null) return null;
        try {
            LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            return (Drawable) LauncherApps.class.getMethod("getShortcutIconDrawable", Class.forName("android.content.pm.ShortcutInfo"), int.class).invoke(la, info, c.getResources().getDisplayMetrics().densityDpi);
        } catch (Throwable t) { return null; }
    }

    static List<Object> shortcuts(Context c, String pkg) {
        ArrayList<Object> out = new ArrayList<Object>();
        try {
            LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            Class<?> qc = Class.forName("android.content.pm.LauncherApps$ShortcutQuery");
            Object q = qc.newInstance();
            if (pkg != null) qc.getMethod("setPackage", String.class).invoke(q, pkg);
            qc.getMethod("setQueryFlags", int.class).invoke(q, 1 | 2 | 8 | 16);
            Object user = android.os.Process.myUserHandle();
            List<?> list = (List<?>) LauncherApps.class.getMethod("getShortcuts", qc, UserHandle.class).invoke(la, q, user);
            if (list != null) out.addAll(list);
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: shortcuts", t); }
        return out;
    }

    static String shortcutText(Object info, String method) {
        try { Object v = info.getClass().getMethod(method).invoke(info); return v == null ? "" : v.toString(); } catch (Throwable t) { return ""; }
    }

    static boolean startShortcut(Context c, String pkg, String id, Rect bounds) {
        try {
            LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            LauncherApps.class.getMethod("startShortcut", String.class, String.class, Rect.class, Bundle.class, UserHandle.class).invoke(la, pkg, id, bounds, null, android.os.Process.myUserHandle());
            return true;
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: start shortcut", t); return false; }
    }

    private static void answer(final Context app) {
        final String ask = PixelSearch.string(app, PixelSearch.ASK_KEY);
        if (ask == null || ask.isEmpty() || !PatronGate.ok(app)) return;
        final String pkg = ask.split("\\|")[0];
        new Thread(new Runnable() { public void run() {
            ArrayList<String> ids = new ArrayList<String>(), labels = new ArrayList<String>();
            for (Object s : shortcuts(app, pkg)) {
                String id = shortcutText(s, "getId"), label = shortcutText(s, "getShortLabel");
                if (id.isEmpty() || ids.contains(id)) continue;
                ids.add(id); labels.add(label.isEmpty() ? shortcutText(s, "getLongLabel") : label);
            }
            Bundle b = new Bundle(); b.putStringArrayList("ids", ids); b.putStringArrayList("labels", labels);
            try { app.getContentResolver().call(Uri.parse("content://" + PixelSearch.AUTHORITY), "shortcuts", pkg, b); } catch (Throwable t) { android.util.Log.w(TAG, "search dock: answer", t); }
        } }, "PixelSearchShortcuts").start();
    }

    static final class Avatar extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), image = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String letter; Bitmap bitmap; final Matrix m = new Matrix();
        Avatar(final Context c, PixelSearch.Slot s, int ink) {
            String name = s.label.trim();
            letter = name.isEmpty() ? "#" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
            fill.setColor(PixelSearch.mix(ink, Color.TRANSPARENT, 0.75f));
            text.setColor(ink); text.setTextAlign(Paint.Align.CENTER); text.setFakeBoldText(true);
            final String id = contactId(s);
            if (id != null) new Thread(new Runnable() { public void run() {
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://" + PixelSearch.AUTHORITY + "/photo/" + id))) {
                    final Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) MAIN.post(new Runnable() { public void run() { bitmap = b; image.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)); invalidateSelf(); } });
                } catch (Throwable ignored) { }
            } }, "PixelSearchAvatar").start();
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float r = Math.min(b.width(), b.height()) / 2f;
            if (bitmap != null) {
                float scale = 2 * r / Math.min(bitmap.getWidth(), bitmap.getHeight());
                m.setScale(scale, scale); m.postTranslate(b.exactCenterX() - bitmap.getWidth() * scale / 2, b.exactCenterY() - bitmap.getHeight() * scale / 2);
                image.getShader().setLocalMatrix(m);
                c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, image);
                return;
            }
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, fill);
            text.setTextSize(r * 1.05f);
            c.drawText(letter, b.exactCenterX(), b.exactCenterY() - (text.descent() + text.ascent()) / 2, text);
        }
        @Override public void setAlpha(int a) { fill.setAlpha(a); text.setAlpha(a); image.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    static String contactId(PixelSearch.Slot s) {
        String[] p = s.value.split("/", 3);
        return p.length >= 2 && p[1].matches("\\d+") ? p[1] : null;
    }

    static String contactNumber(PixelSearch.Slot s) {
        String[] p = s.value.split("/", 3);
        return p.length == 3 ? p[2] : "";
    }

    static String contactMode(PixelSearch.Slot s) {
        String[] p = s.value.split("/", 3);
        return p.length >= 1 && !p[0].isEmpty() ? p[0] : "call";
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int height = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? dp(64) : MeasureSpec.getSize(h);
        setMeasuredDimension(width, height);
    }

    private float fit = 1f;
    private boolean fitted;

    @Override public void draw(Canvas c) {
        if (fit >= 1f) { super.draw(c); return; }
        int save = c.save();
        c.scale(fit, fit, 0, getHeight() / 2f);
        try { super.draw(c); } finally { c.restoreToCount(save); }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); layoutZones(); }

    @Override protected void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); reload(); }

    private void layoutZones() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float shellH = Math.min(h - dp(2), dp(62) * look.height);
        ks = shellH / dp(62);
        fit = 1f;
        if (widget) {
            int sides = 0, in = 0;
            for (Zone z : zones) { if (z.pill) continue; if (z.slot.side == 'I') in++; else sides++; }
            float need = dp(4) + dp(10) * ks + sides * (shellH - dp(10) * ks + dp(6) * ks) + in * dp(46) * ks + dp(12) * ks + dp(72) * ks;
            if (w < need) { fit = w / need; w = Math.round(need); }
        }
        float inset = dp(5) * ks, side = dp(2) + (look.width < 1f ? w * (1 - look.width) / 2f : 0);
        float top = (h - shellH) / 2f;
        shell.set(side, top, w - side, top + shellH);
        float inner = shellH - 2 * inset, gap = dp(6) * ks;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float left = shell.left + inset, right = shell.right - inset;
        if (editing && wiggle) trimSpares(right - left, inner, gap);
        ArrayList<Zone> leading = new ArrayList<Zone>(), trailing = new ArrayList<Zone>(), inside = new ArrayList<Zone>();
        for (Zone z : zones) {
            if (z.pill) continue;
            if (z.slot.side == 'L') leading.add(z); else if (z.slot.side == 'R') trailing.add(z); else inside.add(z);
        }
        for (Zone z : leading) { z.dest.set(left, shell.top + inset, left + inner, shell.top + inset + inner); left += inner + gap; }
        for (int i = trailing.size() - 1; i >= 0; i--) { Zone z = trailing.get(i); z.dest.set(right - inner, shell.top + inset, right, shell.top + inset + inner); right -= inner + gap; }
        pill.set(left, shell.top + inset, right, shell.top + inset + inner);
        pillZone.dest.set(pill);
        float icon = dp(46) * ks, edge = dp(6) * ks;
        for (int i = 0; i < inside.size(); i++) {
            Zone z = inside.get(inside.size() - 1 - i);
            float cy = pill.centerY();
            if (!rtl) z.dest.set(pill.right - edge - icon * (i + 1), cy - icon / 2, pill.right - edge - icon * i, cy + icon / 2);
            else z.dest.set(pill.left + edge + icon * i, cy - icon / 2, pill.left + edge + icon * (i + 1), cy + icon / 2);
        }
        boolean glide = false;
        for (Zone z : zones) {
            if (!editing || z.box.isEmpty()) z.box.set(z.dest);
            else if (!z.box.equals(z.dest)) glide = true;
        }
        if (glide) kick();
    }

    private void trimSpares(float across, float inner, float gap) {
        float icon = dp(46) * ks, need = dp(18) * ks + dp(24) * ks + dp(16) * ks + dp(6) * ks;
        while (true) {
            int sides = 0, in = 0;
            Zone drop = null;
            int worst = -1;
            for (Zone z : zones) {
                if (z.pill) continue;
                if (z.slot.side == 'I') in++; else sides++;
                if (!spare(z.key)) continue;
                int n = z.key.charAt(8) - '0', rank = n * 3 + (z.slot.side == 'I' ? 0 : z.slot.side == 'R' ? 1 : 2);
                if (rank > worst) { worst = rank; drop = z; }
            }
            if (across - sides * (inner + gap) >= need + in * icon || drop == null) return;
            zones.remove(drop);
        }
    }

    void toLocal(float rawX, float rawY, float[] out) {
        View b = host != null ? host : this;
        int[] at = new int[2]; b.getLocationOnScreen(at);
        out[0] = rawX - at[0] + (getWidth() - b.getWidth()) / 2f;
        out[1] = rawY - at[1] + (getHeight() - b.getHeight()) / 2f;
    }

    boolean outside(float y) { return y < shell.top - dp(48) || y > shell.bottom + dp(48); }

    Zone zoneAt(float x, float y) { Zone z = hit(x, y); return z == null || z.pill ? null : z; }

    char sideAt(float x) { return x < pill.left ? 'L' : x > pill.right ? 'R' : 'I'; }

    int indexAt(char side, float x, String skip) {
        int n = 0;
        for (Zone z : zones) if (!z.pill && z.slot.side == side && !z.key.equals(skip) && z.dest.centerX() < x) n++;
        return n;
    }

    void settle(String key, float cx, float cy) {
        for (Zone z : zones) {
            if (z.pill || !z.key.equals(key) || z.dest.isEmpty()) continue;
            float hw = z.dest.width() / 2f * 1.18f, hh = z.dest.height() / 2f * 1.18f;
            z.box.set(cx - hw, cy - hh, cx + hw, cy + hh);
            kick();
        }
    }

    void drawLifted(Canvas c, Zone z, float cx, float cy, float scale, int alpha) {
        if (look == null) return;
        int s = c.save();
        c.translate(cx - z.box.centerX(), cy - z.box.centerY());
        c.scale(scale, scale, z.box.centerX(), z.box.centerY());
        if (!z.circle) {
            float r = z.box.height() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(look.pill);
            paint.setAlpha(alpha);
            c.drawCircle(z.box.centerX(), z.box.centerY(), r, paint);
        } else background(c, z, alpha);
        icon(c, z, alpha);
        c.restoreToCount(s);
    }

    Zone zone(String key) { for (Zone z : zones) if (z.key.equals(key)) return z; return null; }

    RectF shellOnScreen() {
        View b = host != null ? host : this;
        int[] at = new int[2]; b.getLocationOnScreen(at);
        float sx = b.getScaleX(), sy = b.getScaleY(), dx = (b.getWidth() - getWidth()) / 2f, dy = (b.getHeight() - getHeight()) / 2f;
        if (fit < 1f) { float mid = getHeight() / 2f; return new RectF(at[0] + shell.left * fit * sx, at[1] + (mid + (shell.top - mid) * fit) * sy, at[0] + shell.right * fit * sx, at[1] + (mid + (shell.bottom - mid) * fit) * sy); }
        return new RectF(at[0] + (shell.left + dx) * sx, at[1] + (shell.top + dy) * sy, at[0] + (shell.right + dx) * sx, at[1] + (shell.bottom + dy) * sy);
    }

    void drawGhost(Canvas c, float scale, int alpha) {
        if (look == null || shell.isEmpty()) return;
        for (Zone z : zones) {
            int save = c.save();
            c.scale(scale, scale, z.box.centerX(), z.box.centerY());
            if (z.pill || z.circle) background(c, z, alpha);
            icon(c, z, alpha);
            c.restoreToCount(save);
        }
    }

    private long assembleAt;

    private String closeStyle = "classic";
    private final float[] landed = new float[6], swell = new float[2];
    private final Path bodyPath = new Path(), bump = new Path();

    void assemble() { closeStyle = SearchClose.style(getContext()); assembleAt = SystemClock.uptimeMillis(); invalidate(); }

    private float puffInset() { return Math.min(dp(9) * ks, shell.height() * 0.2f); }

    float puffStart() { return puffInset() * (host != null ? host.getScaleY() : 1f); }

    private float puff(long since) {
        if (since < 0) return 0;
        float t = since / 1000f;
        if (t > 0.9f) return 0;
        double zeta = 0.45, omega = 19.0, wd = omega * Math.sqrt(1 - zeta * zeta);
        double e = Math.exp(-zeta * omega * t);
        return (float) (puffInset() * e * (Math.cos(wd * t) + zeta * omega / wd * Math.sin(wd * t)));
    }

    float shellRadius() { return shell.height() / 2f * (host != null ? host.getScaleY() : 1f); }

    int shellColor() { return look == null ? 0 : PixelSearch.alpha(look.shell, look.glass ? Math.round(look.shellOpacity * 0.7f) : look.shellOpacity); }

    int pillColor() { return look == null ? 0 : PixelSearch.alpha(look.pill, look.pillOpacity); }

    void setHidden(float v) { if (hidden != v) { hidden = v; invalidate(); } }

    private static volatile boolean sQuietRim;
    static void quietRim(boolean on) {
        if (sQuietRim == on) return;
        sQuietRim = on;
        try { SearchDock d = current(); if (d != null) d.invalidate(); } catch (Throwable ignored) { }
    }

    @Override protected void onDraw(Canvas c) {
        if (look == null || shell.isEmpty() || hidden >= 1f) return;
        long since = assembleAt == 0 ? -1 : SystemClock.uptimeMillis() - assembleAt;
        boolean styled = since >= 0 && !SearchClose.classic(closeStyle);
        float in = styled ? 0 : puff(since), ox = 0, oy = 0;
        if (styled) { SearchClose.bar(closeStyle, since, shell.height(), shell.width() / 2f, swell); ox = swell[0]; oy = swell[1]; }
        body.set(shell.left + in - ox, shell.top + in - oy, shell.right - in + ox, shell.bottom - in + oy);
        float radius = body.height() / 2f;
        if (look.glass && !preview) {
            if (blur == null) blur = blurDrawable(radius);
            if (blur != null) {
                if (Math.abs(blurRadius - radius) > .5f) { try { LauncherFeatures.call(blur, "setCornerRadius", radius); } catch (Throwable ignored) { } blurRadius = radius; }
                blur.setBounds(Math.round(body.left), Math.round(body.top), Math.round(body.right), Math.round(body.bottom)); blur.draw(c);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(shellColor());
        float wave = styled ? SearchClose.bulge(closeStyle, since, shell.width() / 2f, shell.height()) : Float.NaN;
        if (styled) { bodyPath.reset(); bodyPath.addRoundRect(body, radius, radius, Path.Direction.CW); }
        if (!Float.isNaN(wave)) {
            float bh = shell.height();
            bump.reset(); bump.addOval(shell.left + wave - bh * 0.9f, shell.centerY() - bh * 0.62f, shell.left + wave + bh * 0.9f, shell.centerY() + bh * 0.62f, Path.Direction.CW);
            bump.op(bodyPath, Path.Op.UNION);
            c.drawPath(bump, paint);
        } else c.drawRoundRect(body, radius, radius, paint);
        if (look.glass && !sQuietRim) {
            if (rim == null || Math.abs(rimRadius - radius) > .5f) { rim = new GlassRim(radius, dp(1.5f), look.dark); rimRadius = radius; }
            rim.draw(c, body.left, body.top, body.right, body.bottom, radius, Math.round(255 * Math.max(.35f, look.shellOpacity / 100f)));
        }
        boolean forming = since >= 0 && since < 900;
        int order = -1;
        for (Zone z : zones) {
            order++;
            if (z.key.equals(dragKey)) continue;
            float s = 1 - 0.06f * z.press, shift = 0;
            int alpha = 255;
            if (spare(z.key)) {
                float grown = Math.min(1f, (SystemClock.uptimeMillis() - z.born) / 220f);
                if (grown < 1f) { alpha = Math.round(255 * grown); s *= 0.55f + 0.45f * (1f - (1f - grown) * (1f - grown)); invalidate(); }
            }
            if (styled) {
                SearchClose.zone(closeStyle, since, order, (z.box.centerX() - shell.centerX()) / Math.max(1f, shell.width() / 2f), z.pill, shell.height(), shell.width() / 2f, landed);
                if (landed[4] < 0.5f) continue;
                c.save();
                if (landed[5] > 0.5f) c.clipPath(bodyPath);
                c.translate(landed[2], landed[3]);
                c.scale(s * landed[0], s * landed[1], z.box.centerX(), z.box.centerY());
                if (z.pill || z.circle) background(c, z, 255);
                icon(c, z, 255);
                c.restore();
                continue;
            }
            if (since >= 0) {
                float t = (since - 40f) / 460f;
                if (t < 1f) forming = true;
                if (t <= 0f) continue;
                if (t < 1f) {
                    float e = 1f - (float) Math.pow(1f - t, 5);
                    s *= 0.82f + 0.18f * e;
                    alpha = Math.round(255 * Math.min(1f, (since - 40f) / 160f));
                    shift = (shell.centerX() - z.box.centerX()) * (1f - e) * 0.55f;
                }
            }
            c.save();
            c.translate(shift, 0);
            if (wiggle && !z.pill && !z.key.equals(dragKey)) c.rotate(2.4f * (float) Math.sin(SystemClock.uptimeMillis() / 46.0 + z.box.centerX() * 0.05), z.box.centerX(), z.box.centerY());
            c.scale(s, s, z.box.centerX(), z.box.centerY());
            if (!z.pill && "empty".equals(z.slot.type)) ring(c, z, alpha);
            else if (z.pill || z.circle) background(c, z, alpha);
            icon(c, z, alpha);
            c.restore();
        }
        if (since >= 0) { if (forming) invalidate(); else assembleAt = 0; }
        if (!leaving.isEmpty()) {
            long t = SystemClock.uptimeMillis();
            for (int i = leaving.size() - 1; i >= 0; i--) {
                Zone z = leaving.get(i);
                float u = (t - z.born) / 180f;
                if (u >= 1f) { leaving.remove(i); continue; }
                float k = 1f - u * u, sc = 0.6f + 0.4f * k;
                c.save();
                c.scale(sc, sc, z.box.centerX(), z.box.centerY());
                ring(c, z, Math.round(255 * k));
                icon(c, z, Math.round(255 * k));
                c.restore();
            }
            invalidate();
        }
        if (wiggle) postInvalidateDelayed(30);
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.PathEffect dash;
    private float dashKs;

    private void ring(Canvas c, Zone z, int alpha) {
        float r = Math.min(z.box.width(), z.box.height()) / 2f - dp(z.circle ? 3 : 5) * ks;
        ringPaint.setStyle(Paint.Style.FILL);
        ringPaint.setColor(look.ink);
        ringPaint.setAlpha(Math.round(26 * alpha / 255f));
        c.drawCircle(z.box.centerX(), z.box.centerY(), r, ringPaint);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.5f) * ks);
        if (dash == null || dashKs != ks) { dash = new android.graphics.DashPathEffect(new float[]{dp(4) * ks, dp(3.5f) * ks}, 0); dashKs = ks; }
        ringPaint.setPathEffect(dash);
        ringPaint.setAlpha(Math.round(150 * alpha / 255f));
        c.drawCircle(z.box.centerX(), z.box.centerY(), r, ringPaint);
        ringPaint.setPathEffect(null);
    }

    private void background(Canvas c, Zone z, int alpha) {
        float r = z.box.height() / 2f;
        paint.setColor(pillColor());
        paint.setAlpha(Math.round(paint.getAlpha() * alpha / 255f));
        c.drawRoundRect(z.box, r, r, paint);
        if (look.glass && !sQuietRim) {
            if (pillRim == null || Math.abs(pillRimRadius - r) > .5f) { pillRim = new GlassRim(r, dp(1.2f), look.dark); pillRimRadius = r; }
            pillRim.draw(c, z.box.left, z.box.top, z.box.right, z.box.bottom, r, Math.round(160 * alpha / 255f));
        }
    }

    private void icon(Canvas c, Zone z, int alpha) {
        if (z.pill) {
            float size = dp(24) * ks, x = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? z.box.right - dp(18) * ks - size : z.box.left + dp(18) * ks;
            leadBox.set(x, z.box.centerY() - size / 2, x + size, z.box.centerY() + size / 2);
            int turned = -1;
            if (wiggle) { turned = c.save(); c.rotate(2.4f * (float) Math.sin(SystemClock.uptimeMillis() / 46.0 + 1.7), leadBox.centerX(), leadBox.centerY()); }
            if (leadShown != null) {
                float grow = leadShown instanceof SearchGlyph ? 0 : size * 0.1f;
                leadShown.setBounds(Math.round(leadBox.left - grow), Math.round(leadBox.top - grow), Math.round(leadBox.right + grow), Math.round(leadBox.bottom + grow));
                leadShown.setAlpha(alpha);
                leadShown.draw(c);
                if (turned >= 0) c.restoreToCount(turned);
                return;
            }
            search.setBounds(Math.round(x), Math.round(z.box.centerY() - size / 2), Math.round(x + size), Math.round(z.box.centerY() + size / 2));
            search.setAlpha(alpha);
            search.draw(c);
            if (turned >= 0) c.restoreToCount(turned);
            return;
        }
        if (z.icon == null) return;
        boolean glyph = z.icon instanceof SearchGlyph;
        boolean avatar = z.icon instanceof Avatar;
        float size = avatar ? (z.circle ? z.box.height() - dp(8) * ks : dp(30) * ks) : glyph ? dp(24) * ks : dp(z.circle ? 30 : 26) * ks;
        Drawable d = z.icon;
        if (!glyph && !avatar) {
            if (look.colorful) {
                d.setColorFilter(null);
                String key = "bare:" + z.slot.encode();
                Drawable bare = ICONS.get(key);
                if (bare == null) { bare = bare(d); ICONS.put(key, bare == null ? d : bare); }
                if (bare != null && bare != d) d = bare;
            } else {
                Drawable mono = ICONS.get(z.slot.encode());
                if (mono == null) {
                    mono = monochrome(d, look.ink);
                    if (mono == null) try { mono = monochrome(getContext().getPackageManager().getApplicationIcon(z.slot.pkg()), look.ink); } catch (Throwable ignored) { }
                    if (mono == null) { d.mutate().setColorFilter(GRAY); mono = d; }
                    ICONS.put(z.slot.encode(), mono);
                }
                if (mono != d) { d = mono; size = dp(26) * ks; }
            }
        }
        d.setBounds(Math.round(z.box.centerX() - size / 2), Math.round(z.box.centerY() - size / 2), Math.round(z.box.centerX() + size / 2), Math.round(z.box.centerY() + size / 2));
        d.setAlpha(alpha);
        d.draw(c);
    }

    private Drawable blurDrawable(float radius) {
        try {
            Object root = LauncherFeatures.call(this, "getViewRootImpl");
            Drawable glass = (Drawable) LauncherFeatures.call(root, "createBackgroundBlurDrawable");
            LauncherFeatures.call(glass, "setBlurRadius", Math.round(FrostedSurface.depthBlurRadius(getContext()) * 0.6f));
            LauncherFeatures.call(glass, "setCornerRadius", radius);
            blurRadius = radius;
            try { LauncherFeatures.call(glass, "setColor", Color.TRANSPARENT); } catch (Throwable ignored) { }
            return glass;
        } catch (Throwable t) { return null; }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); blur = null; }

    RectF shell() { return shell; }

    PixelSearch.Look look() { return look; }

    private Zone hit(float x, float y) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (z.pill) continue;
            RectF b = z.box;
            if (x >= b.left - dp(2) && x <= b.right + dp(2) && y >= shell.top && y <= shell.bottom) return z;
        }
        if (x >= shell.left && x <= shell.right && y >= shell.top - dp(6) && y <= shell.bottom + dp(6)) return pillZone;
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (preview) return false;
        if (fit < 1f && !fitted) {
            MotionEvent m = MotionEvent.obtain(e);
            android.graphics.Matrix back = new android.graphics.Matrix();
            back.setScale(1f / fit, 1f / fit, 0, getHeight() / 2f);
            m.transform(back);
            fitted = true;
            try { return onTouchEvent(m); } finally { fitted = false; m.recycle(); }
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = hit(e.getX(), e.getY());
                if (down == null) return false;
                downX = e.getX(); downY = e.getY(); longFired = false;
                down.pressTo = 1; kick();
                if (!widget) MAIN.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (down != null && Math.hypot(e.getX() - downX, e.getY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop() * 1.5f) release();
                return true;
            case MotionEvent.ACTION_UP:
                Zone z = down;
                release();
                if (z != null && !longFired) { haptic(HapticFeedbackConstants.VIRTUAL_KEY); tap(z); }
                return true;
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void release() {
        MAIN.removeCallbacks(longPress);
        if (down != null) { down.pressTo = 0; down = null; kick(); }
    }

    private void kick() {
        if (animating) return;
        animating = true; lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long now) {
        float dt = lastFrame == 0 ? 1 / 60f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        boolean moving = false;
        for (Zone z : zones) {
            float k = z.pressTo > 0 ? 900 : 520, damping = z.pressTo > 0 ? 2 * (float) Math.sqrt(k) : 2 * 0.55f * (float) Math.sqrt(k);
            float a = -k * (z.press - z.pressTo) - damping * z.pressV;
            z.pressV += a * dt; z.press += z.pressV * dt;
            if (Math.abs(z.press - z.pressTo) > 0.002f || Math.abs(z.pressV) > 0.01f) moving = true; else { z.press = z.pressTo; z.pressV = 0; }
            if (!z.box.equals(z.dest)) {
                float f = 1 - (float) Math.exp(-dt * (preview ? 11 : 18));
                z.box.left += (z.dest.left - z.box.left) * f; z.box.top += (z.dest.top - z.box.top) * f;
                z.box.right += (z.dest.right - z.box.right) * f; z.box.bottom += (z.dest.bottom - z.box.bottom) * f;
                if (Math.abs(z.box.left - z.dest.left) + Math.abs(z.box.right - z.dest.right) + Math.abs(z.box.top - z.dest.top) < 0.6f) z.box.set(z.dest); else moving = true;
            }
        }
        invalidate();
        if (moving) Choreographer.getInstance().postFrameCallback(this); else animating = false;
    }

    private void tap(Zone z) {
        Context c = getContext();
        if (!PatronGate.ok(c)) { ensure(); return; }
        if (z.pill && lead != null && downX >= leadBox.left - dp(10) && downX <= leadBox.right + dp(10) && downY >= leadBox.top - dp(12) && downY <= leadBox.bottom + dp(12)) {
            if ("action".equals(lead.type) && "hearts".equals(lead.value)) { SearchHearts.show(this); return; }
            Rect area = new Rect();
            int[] where = new int[2]; (host != null ? host : this).getLocationOnScreen(where);
            area.set(where[0] + Math.round(leadBox.left), where[1] + Math.round(leadBox.top), where[0] + Math.round(leadBox.right), where[1] + Math.round(leadBox.bottom));
            run(c, lead, area);
            return;
        }
        if (z.pill) { SearchOverlay.open(this); return; }
        PixelSearch.Slot s = z.slot;
        Rect bounds = new Rect();
        int[] at = new int[2]; (host != null ? host : this).getLocationOnScreen(at);
        bounds.set(at[0] + Math.round(z.box.left), at[1] + Math.round(z.box.top), at[0] + Math.round(z.box.right), at[1] + Math.round(z.box.bottom));
        if ("action".equals(s.type) && "hearts".equals(s.value)) { SearchHearts.show(this); return; }
        run(c, s, bounds);
    }

    static void run(Context c, PixelSearch.Slot s, Rect bounds) {
        try {
            if ("action".equals(s.type)) action(c, s.value);
            else if ("app".equals(s.type)) {
                ComponentName cn = ComponentName.unflattenFromString(s.value);
                LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (cn != null) la.startMainActivity(cn, android.os.Process.myUserHandle(), bounds, null);
                else { Intent i = c.getPackageManager().getLaunchIntentForPackage(s.pkg()); if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            } else if ("shortcut".equals(s.type)) {
                if (!startShortcut(c, s.pkg(), s.rest(), bounds)) { Intent i = c.getPackageManager().getLaunchIntentForPackage(s.pkg()); if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            } else if ("contact".equals(s.type)) {
                String number = contactNumber(s), mode = contactMode(s);
                if ("sms".equals(mode)) c.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else if ("whatsapp".equals(mode)) SearchOverlay.whatsapp(c, number);
                else SearchOverlay.suiteAction(c, "call", number);
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "search dock: slot", t);
            android.widget.Toast.makeText(c, I18n.cs("Couldn't open it"), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    static final String GSA = "com.google.android.googlequicksearchbox";

    static boolean appShortcut(Context c, String pkg, String word) {
        try {
            for (Object info : shortcuts(c, pkg)) {
                String sid = shortcutText(info, "getId"), label = shortcutText(info, "getShortLabel") + " " + shortcutText(info, "getLongLabel");
                if (sid.toLowerCase(java.util.Locale.ROOT).contains(word) || label.toLowerCase(java.util.Locale.ROOT).contains(word)) {
                    if (startShortcut(c, pkg, sid, null)) return true;
                }
            }
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: " + word, t); }
        return false;
    }

    static void action(Context c, String id) {
        ArrayList<Intent> tries = new ArrayList<Intent>();
        if ("scan".equals(id)) { if (PixelSearch.scanOn(c)) SearchOverlay.suiteAction(c, "scan", "1"); return; }
        if ("voice".equals(id)) {
            tries.add(new Intent("android.speech.action.WEB_SEARCH").setPackage(GSA));
            tries.add(new Intent(Intent.ACTION_VOICE_COMMAND));
        } else if ("lens".equals(id)) {
            if (appShortcut(c, GSA, "lens")) return;
            Intent standalone = c.getPackageManager().getLaunchIntentForPackage("com.google.ar.lens");
            if (standalone != null) tries.add(standalone);
            tries.add(new Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GSA));
        } else if ("song".equals(id)) {
            tries.add(new Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").setPackage(GSA));
            tries.add(new Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH"));
            Intent shazam = c.getPackageManager().getLaunchIntentForPackage("com.shazam.android");
            if (shazam != null) tries.add(shazam);
        } else if ("assistant".equals(id)) {
            tries.add(new Intent(Intent.ACTION_VOICE_COMMAND));
            tries.add(new Intent(Intent.ACTION_ASSIST));
        } else if ("camera".equals(id)) {
            tries.add(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        } else if ("qr".equals(id)) {
            tries.add(new Intent().setClassName("com.google.android.gms", "com.google.android.gms.mlkit.barcode.ui.PlatformBarcodeScanningActivityProxy"));
        }
        for (Intent i : tries) {
            try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Throwable ignored) { }
        }
        if ("qr".equals(id) && appShortcut(c, GSA, "lens")) return;
        android.widget.Toast.makeText(c, I18n.cs("Couldn't open it"), android.widget.Toast.LENGTH_SHORT).show();
    }

    void openSettings() {
        try {
            Intent i = new Intent().setClassName(PixelSearch.SUITE, "io.github.pixelsuite.SettingsActivity").putExtra("page", PixelSearch.PAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Throwable t) { android.util.Log.w(TAG, "search dock: settings", t); }
    }
}
