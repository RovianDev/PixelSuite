package io.github.pixelsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

final class WidgetPanel {
    private WidgetPanel() {}

    static final String KEY = "pixelsuite_widget_panel";
    static final String HEADER_KEY = "pixelsuite_widget_panel_no_header";
    static final String OPACITY_KEY = "pixelsuite_widget_panel_opacity";

    static final String SIDE_KEY = "pixelsuite_widget_panel_side", NO_GOOGLE_KEY = "pixelsuite_widget_panel_no_google", LOCK_SIDE_KEY = "pixelsuite_lock_widget_panel_side";

    static boolean rtlOf(Context c) { return c.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }
    private static int secureInt(Context c, String key, int def) { try { return Settings.Secure.getInt(c.getContentResolver(), key, def); } catch (Throwable t) { return def; } }

    static boolean homeFar(Context c) { return secureInt(c, SIDE_KEY, 0) == 1; }

    static boolean homeRight(Context c) { return rtlOf(c) != homeFar(c); }

    static boolean lockRight(Context c) { return rtlOf(c) == lockOther(c); }

    static boolean lockOther(Context c) { return secureInt(c, LOCK_SIDE_KEY, 0) == 1; }
    static boolean keepGoogle(Context c) { return secureInt(c, NO_GOOGLE_KEY, 0) == 0; }

    static final String PAGES_KEY = "pixelsuite_home_pages";
    static final String LOCK_PIN_KEY = "pixelsuite_lock_pin_up";

    static final String LOCK_KEY = "pixelsuite_lock_widget_panel", LOCK_HEADER_KEY = "pixelsuite_lock_widget_panel_no_header", LOCK_OPACITY_KEY = "pixelsuite_lock_widget_panel_opacity";

    static final String LOCK_OPEN_KEY = "pixelsuite_lock_widget_panel_open", LOCK_APPS_KEY = "pixelsuite_lock_widget_panel_apps";
    static final String LOCK_SESSION_KEY = "pixelsuite_lock_widget_panel_session";
    static final long SESSION_DELAY = 200;
    static final long OPEN_DELAY = 100;

    static final String LOCK_CONFIG_KEY = "pixelsuite_lock_widget_panel_config", LOCK_CONFIG_RESULT_KEY = "pixelsuite_lock_widget_panel_config_result";
    static final String CONFIG_PROXY = "android.appwidget.AppWidgetConfigActivityProxy";

    static final String[] LOCK_ALWAYS_ASK = {"io.github.roviandev.pixelsuite", "com.android.settings"};
    static final String LOCK_PICKER = "com.android.launcher3.QuickstepWidgetPickerActivity";
    private static final String LOCK_MARK = "pixelsuite_widget_panel_lock", LOCK_ADD = "io.github.pixelsuite.widgetpanel.LOCK_ADD", BIND = "android.permission.BIND_APPWIDGET";

    static boolean sLock;
    static String headerKey() { return sLock ? LOCK_HEADER_KEY : HEADER_KEY; }
    private static final String PICKER = "com.android.launcher3.QuickstepWidgetPickerActivity", MARK = "pixelsuite_widget_panel";
    private static final int COLS = 12;
    private static final int[] WIDTHS = {3, 4, 6, 8, 9, 12};
    private static final int HOST_ID = 0x50535750;
    private static final int REQ_CONFIG = 0x5351, REQ_BIND = 0x5352;
    private static final String PREFS = "pixelsuite_widget_panel", IDS = "ids";
    private static final String WEATHER_APP = "com.google.android.apps.weather", CALENDAR_APP = "com.google.android.calendar";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static ClassLoader sCl;
    private static Class<?> sProxyType, sManagerType;
    private static WeakReference<Activity> sLauncher = new WeakReference<Activity>(null);
    private static final Map<Activity, Object> sOurs = new WeakHashMap<Activity, Object>();
    private static final Map<Activity, Object> sGoogle = new WeakHashMap<Activity, Object>();
    private static final Set<Class<?>> sHookedManagers = new HashSet<Class<?>>();
    private static Object sCallbacks;
    private static boolean sSetting;
    private static final Map<Activity, Integer> sState = new WeakHashMap<Activity, Integer>();
    private static float sFling;
    private static Panel sPanel;
    private static AppWidgetHost sHost;
    private static int sPending = -1;

    static void installLock(ClassLoader cl) throws Throwable { Lock.install(cl); }

    static void install(ClassLoader cl) throws Throwable {
        sCl = cl;
        final Class<?> launcher = cl.loadClass("com.android.launcher3.Launcher");
        sProxyType = cl.loadClass("com.android.systemui.plugins.shared.LauncherOverlayManager$LauncherOverlayTouchProxy");
        sManagerType = cl.loadClass("com.android.systemui.plugins.shared.LauncherOverlayManager");
        Xp.hookAllMethods(launcher, "setLauncherOverlay", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || !(p.thisObject instanceof Activity)) return;
                    Activity a = (Activity) p.thisObject;
                    Object given = p.args[0];
                    if (sSetting) return;
                    if (given != null && !ours(given)) synchronized (sGoogle) { sGoogle.put(a, given); }
                    if (given == null) synchronized (sGoogle) { sGoogle.remove(a); }
                    int want = slotFor(a);
                    if (want == 1) p.args[0] = proxy(a);
                    else if (want == 2) p.args[0] = null;
                    else if (ours(given)) p.args[0] = null;
                    synchronized (sState) { sState.put(a, want); }
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(launcher, "onCreate", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { attach((Activity) p.thisObject); } catch (Throwable t) { Log.w("widget panel attach", t); }
            }
        });
        Xp.hookAllMethods(launcher, "onNewIntent", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try { if (sPanel != null && sPanel.activity == p.thisObject && sPanel.progress > 0) sPanel.home(); } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(launcher, "onActivityResult", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    int req = (Integer) p.args[0];
                    if (req != REQ_CONFIG && req != REQ_BIND) return;
                    p.setResult(null);
                    clearPending(p.thisObject);
                    result((Activity) p.thisObject, req, (Integer) p.args[1], (Intent) p.args[2]);
                } catch (Throwable t) { Log.w("widget panel result", t); }
            }
        });
        Xp.hookAllMethods(launcher, "onDestroy", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (sPanel != null && sPanel.activity == p.thisObject) { sPanel.detach(); sPanel = null; }
                    unwatchSettings((Activity) p.thisObject);
                    if (sLauncher.get() == p.thisObject && sHost != null) { try { sHost.stopListening(); } catch (Throwable ignored) { } sListening = false; }
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(Activity.class, "onResume", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (!launcher.isInstance(p.thisObject)) return;
                    Activity a = (Activity) p.thisObject;
                    apply(a);
                    if (sPanel != null && sPanel.activity == a && sPanel.progress > 0) sPanel.refresh();
                } catch (Throwable ignored) { }
            }
        });
        try { Weather.install(); } catch (Throwable t) { Log.w("widget panel weather", t); }
        try { hookPicker(cl); } catch (Throwable t) { Log.w("picker", t); }

        try { hookSwipe(cl); } catch (Throwable t) { Log.w("swipe", t); }
        try { hookFar(cl); } catch (Throwable t) { Log.w("far side", t); }
        try { GoogleFeedSwipe.install(cl); } catch (Throwable t) { Log.w("google feed swipe", t); }
    }

    static boolean homeNormal() { return sNormal; }

    static boolean panelShowing() { Panel p = sPanel; return p != null && !sLock && (p.progress > 0f || p.pulling); }

    static void slideAwayNow() {
        Panel p = sPanel;
        if (p == null || sLock || p.pulling || p.progress <= 0f || p.settle == null || !p.settle.isRunning() || p.settleTarget > 0f) return;
        p.settle.end();
    }

    static int slotNow(Activity a) { synchronized (sState) { Integer v = sState.get(a); return v == null ? -1 : v; } }

    private static void hookFar(ClassLoader cl) throws Throwable {
        Class<?> pages = cl.loadClass("com.android.launcher3.Workspace");
        final Map<View, Float> before = new HashMap<View, Float>();
        Xp.hookAllMethods(pages, "onOverlayScrollChanged", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                before.clear();
                if (!farIn()) return;
                Activity l = sLauncher.get();
                Object layer = null;
                try { layer = l == null ? null : Xp.callMethod(l, "getDragLayer"); } catch (Throwable ignored) { }
                if (!(layer instanceof ViewGroup)) return;
                ViewGroup g = (ViewGroup) layer;
                before.put(g, g.getTranslationX());
                for (int i = 0; i < g.getChildCount(); i++) before.put(g.getChildAt(i), g.getChildAt(i).getTranslationX());
                View w = (View) p.thisObject;
                if (!before.containsKey(w)) before.put(w, w.getTranslationX());
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (before.isEmpty()) return;
                for (Map.Entry<View, Float> e : before.entrySet()) {
                    float now = e.getKey().getTranslationX();
                    if (now != e.getValue()) e.getKey().setTranslationX(-now);
                }
                before.clear();
            }
        });
        Xp.Callback still = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (!sSelf || sPanel == null || !sPanel.far) return;
                Class<?> r = ((Method) p.method).getReturnType();
                p.setResult(r == float.class ? Float.valueOf(0f) : null);
            }
        };
        Xp.hookAllMethods(android.widget.EdgeEffect.class, "onPullDistance", still);
        Xp.hookAllMethods(android.widget.EdgeEffect.class, "onPull", still);
    }

    private static boolean farIn() { Panel p = sPanel; return p != null && !sLock && p.far && (p.progress > 0f || p.pulling); }

    private static boolean sSelf, sSelfOk, sNormal = true, sCatch;
    private static float sSelfX0, sSelfY0, sSelfBase;
    private static long sSeenAt = -1; private static int sSeenAction = -1;
    private static void hookSwipe(ClassLoader cl) throws Throwable {
        final Class<?> workspace = cl.loadClass("com.android.launcher3.Workspace");
        Xp.Callback watch = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!workspace.isInstance(p.thisObject) || p.args.length != 1 || !(p.args[0] instanceof MotionEvent)) return;
                    swiped((View) p.thisObject, (MotionEvent) p.args[0]);
                } catch (Throwable t) { Log.w("swipe watch", t); }
            }
        };
        Set<Class<?>> done = new HashSet<Class<?>>();
        for (Class<?> k = workspace; k != null && k != ViewGroup.class && k != View.class; k = k.getSuperclass()) {
            if (!done.add(k)) continue;
            Xp.hookAllMethods(k, "onInterceptTouchEvent", watch);
            Xp.hookAllMethods(k, "onTouchEvent", watch);
        }
    }

    private static void swiped(View pages, MotionEvent ev) {
        Activity a = sLauncher.get();
        if (a == null || !homeEnabled(a)) { sSelf = false; sSelfOk = false; return; }
        int action = ev.getActionMasked();
        if (ev.getEventTime() == sSeenAt && action == sSeenAction) return;
        sSeenAt = ev.getEventTime(); sSeenAction = action;
        Panel panel = panel(a);
        if (action == MotionEvent.ACTION_DOWN) {
            sSelf = false;
            slideAwayNow();
            boolean first = false;
            panel.far = homeFar(a);
            try {
                int at = ((Number) Xp.callMethod(pages, "getNextPage")).intValue();

                first = panel.far ? at == ((Number) Xp.callMethod(pages, "getPageCount")).intValue() - 1 : at == 0;
            } catch (Throwable ignored) { }
            sSelfOk = first && sNormal && panel.progress <= 0f;

            sCatch = !sSelfOk && first && sNormal && !sLock && !panel.pulling && panel.progress > 0f && panel.progress < 1f;
            if (sCatch) sSelfOk = true;
            if (sSelfOk && GoogleFeedSwipe.blocksWidgets()) { sSelfOk = false; sCatch = false; }
            sSelfX0 = ev.getRawX(); sSelfY0 = ev.getRawY();
            return;
        }
        if (!sSelfOk) { return; }
        float w = Math.max(1, a.getResources().getDisplayMetrics().widthPixels);
        float dx = panel.right() ? sSelfX0 - ev.getRawX() : ev.getRawX() - sSelfX0, dy = ev.getRawY() - sSelfY0;
        if (action == MotionEvent.ACTION_MOVE) {
            if (ev.getPointerCount() > 1) { if (!sSelf) sSelfOk = false; return; }
            if (!sSelf) {
                int slop = ViewConfiguration.get(a).getScaledTouchSlop();
                if (Math.abs(dy) > slop * 2 && Math.abs(dy) > Math.abs(dx)) { sSelfOk = false; return; }
                if ((dx > slop && dx > Math.abs(dy) * 1.2f) || (sCatch && -dx > slop && -dx > Math.abs(dy) * 1.2f)) {
                    if (GoogleFeedSwipe.googleHasTouch() || GoogleFeedSwipe.blocksWidgets()) { sSelfOk = false; return; }
                    sSelf = true;
                    GoogleFeedSwipe.widgetsTookTouch();
                    if (!panel.pulling) panel.beginPull();
                    sSelfBase = panel.progress - dx / w;
                }
            }
            if (sSelf) { panel.pull(Math.max(0f, Math.min(1f, sSelfBase + dx / w))); }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (sSelf) panel.release(0f);
            sSelf = false; sSelfOk = false; sCatch = false;
        }
    }

    private static void clearPending(Object launcher) {
        for (Class<?> k = launcher.getClass(); k != null && k != Activity.class; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField("mPendingActivityRequestCode"); f.setAccessible(true); f.setInt(launcher, -1); return; }
            catch (NoSuchFieldException ignored) { }
            catch (Throwable t) { return; }
        }
    }

    private static void hookPicker(ClassLoader cl) throws Throwable {
        final Class<?> picker = cl.loadClass(PICKER);
        Xp.hookAllMethods(Activity.class, "setResult", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!picker.isInstance(p.thisObject) || p.args.length != 2 || !(p.args[1] instanceof Intent)) return;
                    Activity a = (Activity) p.thisObject;
                    if (a.getIntent() == null || !a.getIntent().getBooleanExtra(MARK, false)) return;
                    Intent data = (Intent) p.args[1];
                    final ComponentName provider = data.getParcelableExtra("android.intent.extra.COMPONENT_NAME");
                    final android.os.UserHandle user = data.getParcelableExtra(Intent.EXTRA_USER);
                    if (provider == null) return;
                    if (a.getIntent().getBooleanExtra(LOCK_MARK, false)) {
                        Intent add = new Intent(LOCK_ADD).setPackage("com.android.systemui").putExtra("provider", provider).putExtra("user", user);
                        add.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                        a.sendBroadcast(add, BIND);
                        return;
                    }
                    MAIN.post(() -> { Activity l = sLauncher.get(); if (l != null && !l.isDestroyed()) addProvider(l, provider, user, -1); });
                } catch (Throwable t) { Log.w("picker result", t); }
            }
        });
        Xp.hookAllMethods(picker, "onCreate", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    Activity a = (Activity) p.thisObject;
                    if (a.getIntent() == null || !a.getIntent().getBooleanExtra(MARK, false)) return;
                    if (a.getIntent().getBooleanExtra(LOCK_MARK, false)) { lockPickerBackground(a); lockPickerFade(a); return; }
                    Object controller = View.class.getMethod("getWindowInsetsController").invoke(a.getWindow().getDecorView());
                    Class<?> type = Class.forName("android.view.WindowInsets$Type");
                    int bars = (Integer) type.getMethod("statusBars").invoke(null) | (Integer) type.getMethod("navigationBars").invoke(null);
                    if (controller != null) Class.forName("android.view.WindowInsetsController").getMethod("show", int.class).invoke(controller, bars);
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void lockPickerBackground(Activity a) {
        try {
            Class<?> b = Class.forName("android.app.ActivityManager$TaskDescription$Builder");
            Object builder = b.getConstructor().newInstance();
            b.getMethod("setBackgroundColor", int.class).invoke(builder, Color.BLACK);
            Object description = b.getMethod("build").invoke(builder);
            Activity.class.getMethod("setTaskDescription", android.app.ActivityManager.TaskDescription.class).invoke(a, description);
        } catch (Throwable t) { Log.w("lock picker background", t); }
    }

    private static void lockPickerFade(final Activity a) {
    }

    private static boolean homeEnabled(Context c) {
        return Feature.on(c, KEY) && PatronGate.ok(c);
    }

    private static boolean lockEnabled(Context c) {
        return Feature.on(c, LOCK_KEY) && PatronGate.ok(c);
    }

    private static final String OBSERVERS = "pixelsuite_widget_panel_observers";

    private static void unwatchSettings(Activity a) {
        Object list = Xp.getAdditionalInstanceField(a, OBSERVERS);
        if (!(list instanceof ContentObserver[])) return;
        for (ContentObserver o : (ContentObserver[]) list) try { a.getContentResolver().unregisterContentObserver(o); } catch (Throwable ignored) { }
        Xp.setAdditionalInstanceField(a, OBSERVERS, null);
    }

    private static void attach(final Activity a) {
        sLauncher = new WeakReference<Activity>(a);
        ContentObserver observer = new ContentObserver(MAIN) {
            @Override public void onChange(boolean self) {
                if (a.isDestroyed()) return;
                apply(a);
                if (sPanel != null && sPanel.activity == a) sPanel.settingsChanged();
            }
        };
        ContentObserver patron = new ContentObserver(MAIN) {
            @Override public void onChange(boolean self) { MAIN.postDelayed(() -> observer.onChange(self), 3100); }
        };
        for (String key : new String[]{KEY, HEADER_KEY, OPACITY_KEY, SIDE_KEY, NO_GOOGLE_KEY, SurfaceStyle.KEY, SuperDark.KEY})
            a.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
        a.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(PatronGate.KEY), false, patron);
        Xp.setAdditionalInstanceField(a, OBSERVERS, new ContentObserver[]{observer, patron});
        watchStates(a);
        hookManager(a);
        apply(a);
        try { watchPages(a); } catch (Throwable t) { Log.w("home pages", t); }
    }

    private static void watchPages(final Activity a) {
        Object found = Xp.callMethod(a, "getWorkspace");
        if (!(found instanceof ViewGroup)) return;
        final ViewGroup pages = (ViewGroup) found;
        final int[] seen = {-1};
        pages.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int n = pages.getChildCount();
            if (n == seen[0] || a.isDestroyed()) return;
            try { Object drag = Xp.callMethod(a, "getDragController"); if (drag != null && Boolean.TRUE.equals(Xp.callMethod(drag, "isDragging"))) return; } catch (Throwable ignored) { }
            seen[0] = n;
            if (n <= 0 || secureInt(a, PAGES_KEY, -1) == n) return;
            try { Settings.Secure.putInt(a.getContentResolver(), PAGES_KEY, n); } catch (Throwable t) { Log.w("home pages write", t); }
        });
    }

    private static int slotFor(Activity a) {
        if (!homeEnabled(a)) return 0;
        if (!homeFar(a)) return 1;
        return keepGoogle(a) ? 0 : 2;
    }

    private static void apply(Activity a) {
        int want = slotFor(a);
        Integer now;
        synchronized (sState) { now = sState.get(a); }
        boolean far = homeEnabled(a) && homeFar(a);
        if (sPanel != null && sPanel.activity == a && sPanel.progress > 0 && (!homeEnabled(a) || sPanel.far != far)) sPanel.closeNow();
        if (sPanel != null && sPanel.activity == a) sPanel.far = far;
        if (now != null && now == want) return;
        Object own;
        synchronized (sGoogle) { own = sGoogle.get(a); }
        sSetting = true;
        try { Xp.callMethod(a, "setLauncherOverlay", want == 1 ? proxy(a) : want == 0 ? own : null); } catch (Throwable t) { Log.w("widget panel slot", t); }
        finally { sSetting = false; }
        synchronized (sState) { sState.put(a, want); }
    }

    private static void watchStates(final Activity a) {
        try {
            Object manager = Xp.callMethod(a, "getStateManager");
            Class<?> listener = sCl.loadClass("com.android.launcher3.statemanager.StateManager$StateListener");
            final Object normal = sCl.loadClass("com.android.launcher3.LauncherState").getField("NORMAL").get(null);
            Object l = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, new InvocationHandler() {
                @Override public Object invoke(Object self, Method m, Object[] args) {
                    if (args != null && args.length == 1 && m.getName().startsWith("onStateTransition")) { sNormal = args[0] == normal; }
                    if ("onStateTransitionStart".equals(m.getName()) && args != null && args.length == 1 && args[0] != normal
                            && sPanel != null && sPanel.activity == a && sPanel.progress > 0) {
                        if ((sPanel.wiggle || sPanel.staying()) && !String.valueOf(args[0]).contains("Overview")) sPanel.home();
                        else sPanel.closeNow();
                    }
                    return basic(self, m, args);
                }
            });
            Xp.callMethod(manager, "addStateListener", l);
        } catch (Throwable t) { Log.w("widget panel states", t); }
    }

    private static void hookManager(Activity a) {
        Object manager = null;
        for (Class<?> k = a.getClass(); k != null && k != Activity.class && manager == null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!sManagerType.isAssignableFrom(f.getType())) continue;
                try { f.setAccessible(true); manager = f.get(a); } catch (Throwable ignored) { }
                if (manager != null) break;
            }
        }
        if (manager == null) return;
        for (Class<?> k = manager.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            synchronized (sHookedManagers) { if (!sHookedManagers.add(k)) continue; }
            Xp.hookAllMethods(k, "hideOverlay", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Activity l = sLauncher.get();
                        if (l == null || !homeEnabled(l)) return;
                        if (homeFar(l) && (sPanel == null || sPanel.progress <= 0)) return;
                        p.setResult(null);
                        if (sPanel == null || sPanel.progress <= 0) return;
                        if (sPanel.wiggle || sPanel.staying()) { sPanel.home(); return; }
                        boolean animate = p.args.length == 1 && (p.args[0] instanceof Boolean ? (Boolean) p.args[0] : ((Integer) p.args[0]) > 0);
                        if (animate) sPanel.animateTo(0f, 0f); else sPanel.closeNow();
                    } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(k, "openOverlay", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Activity l = sLauncher.get();
                        if (l == null || !homeEnabled(l) || homeFar(l)) return;
                        p.setResult(null);
                        panel(l).show();
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static final class Handler2 implements InvocationHandler {
        final WeakReference<Activity> activity;
        Handler2(Activity a) { activity = new WeakReference<Activity>(a); }
        @Override public Object invoke(Object self, Method m, Object[] args) {
            String n = m.getName();
            try {
                if ("onOverlayMotionEvent".equals(n)) { pulled(activity.get(), (MotionEvent) args[0], (Float) args[1]); return null; }
                if ("onFlingVelocity".equals(n)) { sFling = (Float) args[0]; return null; }
                if ("setOverlayCallbacks".equals(n)) { sCallbacks = args == null ? null : args[0]; return null; }
            } catch (Throwable t) { Log.w("widget panel pull", t); return null; }
            return basic(self, m, args);
        }
    }

    private static Object basic(Object self, Method m, Object[] args) {
        String n = m.getName();
        if ("equals".equals(n)) return args != null && self == args[0];
        if ("hashCode".equals(n)) return System.identityHashCode(self);
        if ("toString".equals(n)) return "PixelSuiteWidgetPanel";
        Class<?> r = m.getReturnType();
        if (r == boolean.class) return false;
        if (r == int.class || r == long.class || r == float.class || r == double.class || r == short.class || r == byte.class) return 0;
        return null;
    }

    private static Object proxy(Activity a) {
        synchronized (sOurs) {
            Object o = sOurs.get(a);
            if (o == null) { o = Proxy.newProxyInstance(sProxyType.getClassLoader(), new Class<?>[]{sProxyType}, new Handler2(a)); sOurs.put(a, o); }
            return o;
        }
    }

    private static boolean ours(Object o) {
        return o != null && Proxy.isProxyClass(o.getClass()) && Proxy.getInvocationHandler(o) instanceof Handler2;
    }

    private static float sPullX0, sPullBase;
    private static boolean sPullRaw;
    private static void pulled(Activity a, MotionEvent ev, float distance) {
        if (a == null || sSelf) return;
        if (!homeEnabled(a)) { apply(a); return; }
        Panel panel = panel(a);
        int action = ev.getActionMasked();
        boolean real = ev.getDeviceId() != 0 && ev.getPointerCount() > 0;
        float w = Math.max(1, a.getResources().getDisplayMetrics().widthPixels);
        if (action == MotionEvent.ACTION_DOWN) {
            sFling = 0;
            panel.beginPull();
            sPullRaw = real; sPullX0 = ev.getRawX(); sPullBase = panel.progress;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float p = sPullRaw && real ? sPullBase + (panel.right() ? sPullX0 - ev.getRawX() : ev.getRawX() - sPullX0) / w : distance;
            panel.pull(Math.max(0f, Math.min(1f, p)));
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { panel.release(sFling); sFling = 0; }
    }

    private static Method sScrolled;
    private static Class<?> sScrolledOn;
    private static void scrolled(float progress) {
        Object c = sCallbacks;

        Panel now = sPanel;
        if (now != null && !sLock && now.far) {
            try {
                Activity l = sLauncher.get();
                Object ws = l == null ? null : Xp.callMethod(l, "getWorkspace");
                if (ws != null) { c = ws; }
            } catch (Throwable t) { Log.w("far scroll", t); }
        }
        if (c == null) return;
        try {
            if (sScrolledOn != c.getClass()) {
                sScrolled = null; sScrolledOn = c.getClass();
                for (Class<?> k = c.getClass(); k != null && sScrolled == null; k = k.getSuperclass())
                    for (Method m : k.getDeclaredMethods())
                        if ("onOverlayScrollChanged".equals(m.getName()) && m.getParameterTypes().length == 1) { m.setAccessible(true); sScrolled = m; break; }
            }
            if (sScrolled != null) sScrolled.invoke(c, progress);
        } catch (Throwable ignored) { }
    }

    private static Panel panel(Activity a) {
        if (sPanel != null && sPanel.activity != a) { sPanel.detach(); sPanel = null; }
        if (sPanel == null) sPanel = new Panel(a);
        return sPanel;
    }

    private static boolean sListening;
    static void listen(Context c) {
        if (sListening) return;
        try { host(c).startListening(); sListening = true; } catch (Throwable t) { Log.w("listen", t); }
    }

    static AppWidgetHost host(Context c) {
        if (sHost == null) sHost = new AppWidgetHost(c.getApplicationContext(), HOST_ID);
        return sHost;
    }

    private static void pick(Activity a) {
        if (!homeEnabled(a)) return;
        try {
            Intent pick = new Intent(Intent.ACTION_PICK);
            pick.setClassName(a.getPackageName(), PICKER);
            pick.putExtra("ui_surface", "widgets_hub");
            pick.putExtra(MARK, true);
            a.startActivity(pick);
        } catch (Throwable noPicker) {
            Log.w("picker start", noPicker);
            ownPicker(a);
        }
    }

    static void addProvider(Activity a, ComponentName provider, android.os.UserHandle user, int index) {
        if (!panel(a).editAllowed()) return;
        AppWidgetHost host = host(a);
        int id = host.allocateAppWidgetId();
        boolean bound = false;
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(a);
            bound = user == null ? manager.bindAppWidgetIdIfAllowed(id, provider) : manager.bindAppWidgetIdIfAllowed(id, user, provider, null);
        } catch (Throwable t) { Log.w("bind", t); }
        if (!bound) { host.deleteAppWidgetId(id); return; }
        sInsertAt = index;
        picked(a, id);
    }

    private static int sInsertAt = -1;

    private static void ownPicker(final Activity a) {
        final int id = host(a).allocateAppWidgetId();
        final AppWidgetManager manager = AppWidgetManager.getInstance(a);
        final List<AppWidgetProviderInfo> all = manager.getInstalledProviders();
        final PackageManager pm = a.getPackageManager();
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            AppWidgetProviderInfo info = all.get(i);
            CharSequence app;
            try { app = pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(), 0)); } catch (Throwable t) { app = info.provider.getPackageName(); }
            names[i] = app + " · " + info.loadLabel(pm);
        }
        new AlertDialog.Builder(a).setTitle(I18n.cs("Add widget")).setItems(names, (d, which) -> {
            if (!panel(a).editAllowed()) { host(a).deleteAppWidgetId(id); return; }
            AppWidgetProviderInfo info = all.get(which);
            if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) { picked(a, id); return; }
            Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            sPending = id;
            try { a.startActivityForResult(bind, REQ_BIND); } catch (Throwable t) { host(a).deleteAppWidgetId(id); }
        }).setOnCancelListener(d -> host(a).deleteAppWidgetId(id)).show();
    }

    private static void result(Activity a, int req, int code, Intent data) {
        int id = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, sPending) : sPending;
        sPending = -1;
        if (id < 0) return;
        if (code != Activity.RESULT_OK) { host(a).deleteAppWidgetId(id); return; }
        if (req == REQ_CONFIG) { added(a, id); return; }
        picked(a, id);
    }

    private static void picked(Activity a, int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(a).getAppWidgetInfo(id);
        if (info == null) { host(a).deleteAppWidgetId(id); return; }
        if (info.configure != null && !configurationOptional(info)) {
            sPending = id;
            try { host(a).startAppWidgetConfigureActivityForResult(a, id, 0, REQ_CONFIG, null); return; } catch (Throwable ignored) { }
        }
        added(a, id);
    }

    private static boolean configurationOptional(AppWidgetProviderInfo info) {
        try {
            int features = AppWidgetProviderInfo.class.getField("widgetFeatures").getInt(info);
            return (features & 4) != 0;
        } catch (Throwable t) { return false; }
    }

    private static void added(Activity a, int id) {
        Panel p = panel(a);
        int at = sInsertAt; sInsertAt = -1;
        p.addCard(id, true, at);
        save(a, p.grid.ids());
        if (at < 0) p.scrollToEnd();
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static List<Integer> load(Context c) {
        List<Integer> ids = new ArrayList<Integer>();
        String s = prefs(c).getString(IDS, "");
        for (String part : s.split(",")) { try { if (!part.isEmpty()) ids.add(Integer.parseInt(part.trim())); } catch (Throwable ignored) { } }
        return ids;
    }

    static int[] size(Context c, int id) {
        try {
            String[] s = prefs(c).getString("s" + id, "").split(",");
            if (s.length == 2) return new int[]{snapWidth(Integer.parseInt(s[0])), Math.max(2, Math.min(16, Integer.parseInt(s[1])))};
        } catch (Throwable ignored) { }
        return null;
    }

    static void saveSize(Context c, int id, int w, int h) { prefs(c).edit().putString("s" + id, w + "," + h).apply(); }

    static int snapWidth(float units) {
        int best = WIDTHS[0];
        for (int w : WIDTHS) if (Math.abs(w - units) < Math.abs(best - units)) best = w;
        return best;
    }

    static int opacity(Context c) {
        try { return Math.max(0, Math.min(100, Settings.Secure.getInt(c.getContentResolver(), sLock ? LOCK_OPACITY_KEY : OPACITY_KEY, 100))); } catch (Throwable t) { return 100; }
    }

    static void save(Context c, List<Integer> ids) {
        StringBuilder b = new StringBuilder();
        for (Integer id : ids) { if (b.length() > 0) b.append(','); b.append(id); }
        prefs(c).edit().putString(IDS, b.toString()).apply();
    }

    private static float sHigh = Float.NaN, sDefault = Float.NaN;
    private static Method sRate;
    private static boolean sRateKnown;

    static void fast(View v, boolean on) {
        if (v == null) return;
        try {
            if (!sRateKnown) {
                sRateKnown = true;
                sRate = View.class.getMethod("setRequestedFrameRate", float.class);
                sHigh = View.class.getField("REQUESTED_FRAME_RATE_CATEGORY_HIGH").getFloat(null);
                sDefault = View.class.getField("REQUESTED_FRAME_RATE_CATEGORY_DEFAULT").getFloat(null);
            }
            if (sRate != null) sRate.invoke(v, on ? sHigh : sDefault);
        } catch (Throwable ignored) { sRate = null; }
    }

    static final class Spring implements TimeInterpolator {
        final float zeta, omega, seconds;
        Spring(float zeta, float omega, long ms) { this.zeta = zeta; this.omega = omega; this.seconds = ms / 1000f; }
        @Override public float getInterpolation(float t) {
            if (t >= 1f) return 1f;
            double s = t * seconds, wd = omega * Math.sqrt(1 - zeta * zeta);
            return (float) (1 - Math.exp(-zeta * omega * s) * (Math.cos(wd * s) + zeta * omega / wd * Math.sin(wd * s)));
        }
    }

    static final class Swing implements android.view.Choreographer.FrameCallback {
        static final float RUBBER = 0.55f, STIFFNESS = 180f, OMEGA = (float) Math.sqrt(STIFFNESS), ARRIVE_DP = 600f;
        final Panel panel; final float density;
        float pos, vel, target; boolean running; long frameAt;
        Swing(Panel p) { panel = p; density = p.activity.getResources().getDisplayMetrics().density; }

        float width() { int w = panel.root.getWidth(); return Math.max(1f, w > 0 ? w : panel.activity.getResources().getDisplayMetrics().widthPixels); }
        float rubber(float f) { float s = width(); return Math.signum(f) * (1f - 1f / (Math.abs(f) * RUBBER / s + 1f)) * s; }
        float unrubber(float o) { float s = width(), a = Math.min(Math.abs(o), s * 0.99f); return Math.signum(o) * (1f / (1f - a / s) - 1f) * s / RUBBER; }
        float soften(float v) { float most = Math.min(width() * 0.14f, 90f * density); float cap = most * OMEGA * (float) Math.E; return cap <= 0f ? 0f : cap * (float) Math.tanh(v / cap); }

        float raw() { return pos >= 0f ? pos : unrubber(pos); }

        void drag(float raw) { stop(false); pos = raw >= 0f ? raw : rubber(raw); show(); }

        void arrive(float speed) { pos = 0f; fling(-gently(speed), 0f); }

        float gently(float v) { float most = Math.min(width() * 0.035f, 14f * density); float cap = most * OMEGA * (float) Math.E; return cap <= 0f ? 0f : cap * (float) Math.tanh(v / (3f * cap)); }

        void release(float v, boolean away) {
            if (!away && pos < 0f && v < 0f) v = -soften(-v);

            fling(v, away ? width() * 1.35f : 0f);
        }

        void fling(float v, float to) {
            vel = v; target = to;
            if (!running) {
                running = true; frameAt = 0;
                panel.layer(true); fast(panel.root, true);
                android.view.Choreographer.getInstance().postFrameCallback(this);
            }
        }

        void stop(boolean reset) {
            if (running) { running = false; android.view.Choreographer.getInstance().removeFrameCallback(this); }
            vel = 0f;
            if (reset) { pos = 0f; if (panel.content != null) panel.content.setTranslationX(0f); }
        }

        @Override public void doFrame(long nanos) {
            if (!running) return;
            float dt;
            if (frameAt == 0) { float hz = 60f; try { if (panel.root.getDisplay() != null) hz = Math.max(30f, panel.root.getDisplay().getRefreshRate()); } catch (Throwable ignored) { } dt = 1f / hz; }
            else dt = (nanos - frameAt) / 1e9f;
            frameAt = nanos;
            dt = Math.max(0f, Math.min(dt, 1f / 20f));
            int steps = Math.max(1, (int) Math.ceil(dt / 0.002f)); float h = dt / steps;
            boolean done = false;
            for (int i = 0; i < steps; i++) {
                vel += (-STIFFNESS * (pos - target) - 2f * OMEGA * vel) * h;
                pos += vel * h;
                if (target > 0f && pos >= width()) { pos = width(); done = true; break; }
                if (Math.abs(pos - target) < 0.25f && Math.abs(vel) < 6f * density) { pos = target; done = true; break; }
            }
            if (done && target > 0f) { running = false; vel = 0f; panel.layer(false); panel.leftByFinger(); return; }
            show();
            if (!running) return;
            if (!done) { android.view.Choreographer.getInstance().postFrameCallback(this); return; }
            running = false; vel = 0f;
            panel.layer(false);
            fast(panel.root, false);
        }

        void show() {
            int dir = panel.right() ? 1 : -1;
            if (!sLock) {
                if (panel.content != null) panel.content.setTranslationX(Math.round(dir * Math.min(0f, pos)));
                panel.moving();
                return;
            }
            panel.own = Math.max(0f, Math.min(1f, 1f - Math.max(0f, pos) / width()));
            if (panel.content != null) panel.content.setTranslationX(Math.round(dir * Math.min(0f, pos)));
            panel.lockApply();
            panel.moving();
        }
    }

    static final class Log {
        static void w(String what, Throwable t) { android.util.Log.w("PixelSuite", "widget panel: " + what, t); }
    }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static final class Look {
        final boolean dark, superDark, blur, overWallpaper, lightBehind;
        final int opacity, text, muted, accent, onCard, card, badge, menu, base;
        final String signature;
        Look(Context c) {
            dark = FrostedSurface.dark(c);
            superDark = SuperDark.inEffect(c);
            blur = !superDark && FrostedSurface.blurEnabled(c);
            opacity = opacity(c);
            boolean night = superDark || dark;
            onCard = night ? LauncherFeatures.surface(c, "system_on_surface_dark", 0xffe3e2e6) : LauncherFeatures.surface(c, "system_on_surface_light", 0xff1b1b1f);
            if (superDark) { card = FrostedSurface.SUPER_DARK_CLOSED_FOLDER; base = Color.BLACK; menu = 0xff1c1c1c; }
            else if (blur) { card = dark ? 0x38ffffff : 0xa6ffffff; base = FrostedSurface.tone(c) | 0xff000000; menu = dark ? 0xff2c2e34 : 0xfff7f7fb; }
            else {
                card = LauncherFeatures.surface(c, dark ? "system_surface_container_highest_dark" : "system_surface_container_highest_light", dark ? 0xff35353a : 0xffe2e1e7);
                base = LauncherFeatures.surface(c, dark ? "system_surface_container_dark" : "system_surface_container_light", dark ? 0xff1f1f23 : 0xfff3f2f7);
                menu = LauncherFeatures.surface(c, dark ? "system_surface_container_high_dark" : "system_surface_container_high_light", dark ? 0xff2a2a2f : 0xffe9e8ee);
            }

            float cover = (blur ? 0.8f : 1f) * opacity / 100f;
            int behind = wallpaper(c, night);
            double lum = (lum(base) * cover + lum(behind) * (1 - cover));
            lightBehind = lum > 0.6;
            overWallpaper = !lightBehind && (opacity < 100 || lum(base) <= 0.6) && !(opacity >= 100 && night);
            text = overWallpaper ? 0xffffffff : lightBehind ? LauncherFeatures.surface(c, "system_on_surface_light", 0xff1b1b1f) : onCard;
            muted = overWallpaper ? 0xe6ffffff : lightBehind ? LauncherFeatures.surface(c, "system_on_surface_variant_light", 0xff45464f) : LauncherFeatures.surface(c, "system_on_surface_variant_dark", 0xffc5c6d0);
            accent = overWallpaper ? 0xffffffff : lightBehind ? LauncherFeatures.surface(c, "system_accent1_600", 0xff3f5f91) : LauncherFeatures.surface(c, "system_accent1_200", 0xffaac7ff);
            badge = superDark ? 0xff2a2a2a : dark ? 0xff44474f : 0xffdfe2eb;
            signature = dark + "/" + superDark + "/" + blur + "/" + SurfaceStyle.get(c) + "/" + overWallpaper + "/" + lightBehind;
        }

        static double lum(int color) { return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0; }

        static int wallpaper(Context c, boolean night) {
            try {
                Object manager = c.getSystemService(Context.WALLPAPER_SERVICE);
                Object colors = manager.getClass().getMethod("getWallpaperColors", int.class).invoke(manager, 1);
                Object primary = colors.getClass().getMethod("getPrimaryColor").invoke(colors);
                return (Integer) primary.getClass().getMethod("toArgb").invoke(primary);
            } catch (Throwable t) { return night ? 0xff101010 : 0xff808080; }
        }

        void background(View bg) {
            float op = opacity / 100f;
            Context c = bg.getContext();
            if (opacity <= 0) { bg.setBackground(null); return; }
            if (!blur) { bg.setBackgroundColor((base & 0xffffff) | (Math.round(255 * op) << 24)); return; }
            int tint = (base & 0xffffff) | (Math.round(0xcc * op) << 24);
            try {
                Object root = LauncherFeatures.call(bg, "getViewRootImpl");
                Drawable glass = (Drawable) LauncherFeatures.call(root, "createBackgroundBlurDrawable");
                LauncherFeatures.call(glass, "setBlurRadius", Math.round(FrostedSurface.depthBlurRadius(c) * op));
                LauncherFeatures.call(glass, "setCornerRadius", 0f);
                try { LauncherFeatures.call(glass, "setColor", Color.TRANSPARENT); } catch (Throwable ignored) { }
                bg.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{glass, new android.graphics.drawable.ColorDrawable(tint)}));
            } catch (Throwable noBlur) { bg.setBackgroundColor((base & 0xffffff) | (Math.round(255 * op) << 24)); }
        }

        boolean lightBackground() { return lightBehind; }

        void shadow(TextView t) { if (overWallpaper) t.setShadowLayer(dp(t.getContext(), 4), 0, dp(t.getContext(), 1), 0xb3000000); }
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static final class Glyph extends Drawable {
        static final int PLUS = 0, MINUS = 1, CHECK = 2, CORNER = 3, GEAR = 4, OPACITY = 5;
        float level = .5f;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int kind;
        Glyph(int kind, int color, float stroke) {
            this.kind = kind;
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height(), x = getBounds().left, y = getBounds().top, s = Math.min(w, h) * 0.5f, cx = x + w / 2, cy = y + h / 2;
            if (kind == PLUS || kind == MINUS) c.drawLine(cx - s / 2, cy, cx + s / 2, cy, paint);
            if (kind == PLUS) c.drawLine(cx, cy - s / 2, cx, cy + s / 2, paint);
            if (kind == CHECK) {
                Path p = new Path();
                p.moveTo(cx - s * 0.5f, cy + s * 0.02f); p.lineTo(cx - s * 0.14f, cy + s * 0.36f); p.lineTo(cx + s * 0.52f, cy - s * 0.34f);
                c.drawPath(p, paint);
            }
            if (kind == CORNER) {
                android.graphics.RectF arc = new android.graphics.RectF(cx - s * 0.75f, cy - s * 0.75f, cx + s * 0.45f, cy + s * 0.45f);
                c.drawArc(arc, 0, 90, false, paint);
            }
            if (kind == OPACITY) {
                float r = s * 0.72f;
                int a = paint.getAlpha();
                paint.setStyle(Paint.Style.FILL); paint.setAlpha(Math.round(a * Math.max(0f, Math.min(1f, level))));
                c.drawCircle(cx, cy, r, paint);
                paint.setStyle(Paint.Style.STROKE); paint.setAlpha(a);
                c.drawCircle(cx, cy, r, paint);
            }
            if (kind == GEAR) {
                c.drawCircle(cx, cy, s * 0.22f, paint);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    c.drawLine(cx + (float) Math.cos(a) * s * 0.42f, cy + (float) Math.sin(a) * s * 0.42f, cx + (float) Math.cos(a) * s * 0.58f, cy + (float) Math.sin(a) * s * 0.58f, paint);
                }
            }
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static final class PillSurface extends Drawable {
        final Context ctx; final Look look;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF box = new android.graphics.RectF();
        GlassRim rim, movingRim; float rimRadius = -1; boolean rimKnown;

        boolean moving;
        int alpha = 255;
        PillSurface(Context c, Look look) { ctx = c; this.look = look; }
        @Override public void draw(Canvas c) {
            box.set(getBounds());
            float radius = Math.min(box.width(), box.height()) / 2f;
            if (radius <= 0) return;
            fill.setColor(look.card);
            fill.setAlpha(Math.round(Color.alpha(look.card) * alpha / 255f));
            c.drawRoundRect(box, radius, radius, fill);
            if (!rimKnown || Math.abs(radius - rimRadius) > .5f) {
                rimKnown = true; rimRadius = radius;
                try {
                    rim = GlassRim.wanted(ctx) ? GlassRim.of(ctx, radius) : null;
                    movingRim = rim != null && look.superDark ? new GlassRim(radius, FrostedSurface.dp(ctx, 1.5f), true) : null;
                } catch (Throwable t) { rim = null; movingRim = null; }
            }
            if (rim != null) (moving && movingRim != null ? movingRim : rim).draw(c, box.left, box.top, box.right, box.bottom, radius, alpha);
        }
        @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static final class ActionPill extends View implements android.view.Choreographer.FrameCallback {
        final Panel panel;
        final PillSurface surface;
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), plus = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path clip = new android.graphics.Path();
        final android.graphics.RectF box = new android.graphics.RectF();
        final String edit, add;
        final float wEdit, wAdd, pad, icon, gap, textEdit, textAdd;
        float s, sv, to, press, pressV, pressTo;
        boolean ticking; long last;

        ActionPill(Context c, Panel panel) {
            super(c);
            this.panel = panel;
            surface = new PillSurface(c, panel.look);
            edit = String.valueOf(I18n.cs("Edit")); add = String.valueOf(I18n.cs("Add widget"));
            text.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 15, c.getResources().getDisplayMetrics()));
            text.setTypeface(I18n.medium());
            text.setColor(panel.look.onCard);
            plus.setColor(panel.look.onCard); plus.setStyle(Paint.Style.STROKE); plus.setStrokeWidth(dp(c, 2)); plus.setStrokeCap(Paint.Cap.ROUND);
            pad = dp(c, 22); icon = dp(c, 18); gap = dp(c, 6);
            textEdit = text.measureText(edit); textAdd = text.measureText(add);
            wEdit = textEdit + 2 * pad; wAdd = icon + gap + textAdd + 2 * pad;
            setClickable(true);
            setContentDescription(edit);
        }

        void show(boolean adding, boolean animate) {
            to = adding ? 1 : 0;
            setContentDescription(adding ? add : edit);
            if (!animate || getWidth() == 0) { s = to; sv = 0; invalidate(); return; }
            if (s != to) { fast(panel.root, true); tick(); }
        }

        @Override protected void onMeasure(int ws, int hs) {
            int w = Math.round(Math.max(wEdit, wAdd) + dp(getContext(), 20));
            setMeasuredDimension(resolveSize(w, ws), dp(getContext(), 44) + dp(getContext(), 8));
        }

        static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
        static float ease(float t) { t = clamp(t); return 1 - (1 - t) * (1 - t) * (1 - t); }

        float pillWidth() { return Math.max(dp(getContext(), 44), Math.min(getWidth(), wEdit + (wAdd - wEdit) * s)); }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float w = pillWidth(), h = dp(ctx, 44), cx = getWidth() / 2f, cy = getHeight() / 2f;
            box.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            float scale = 1 - .05f * press;
            int save = c.save();
            c.scale(scale, scale, cx, cy);
            surface.moving = ticking && Math.abs(s - to) > .002f;
            surface.setBounds(Math.round(box.left), Math.round(box.top), Math.round(box.right), Math.round(box.bottom));
            surface.draw(c);
            clip.reset(); clip.addRoundRect(box, h / 2, h / 2, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            Paint.FontMetrics fm = text.getFontMetrics();
            float base = cy - (fm.ascent + fm.descent) / 2f, lift = dp(ctx, 12);
            int ink = panel.look.onCard;

            float out = clamp(1 - s * 2.4f);
            if (out > 0) {
                text.setColor(ink); text.setAlpha(Math.round(Color.alpha(ink) * out));
                c.drawText(edit, cx - textEdit / 2, base - (1 - ease(out)) * lift, text);
            }

            float in = ease((s - .3f) / .7f);
            if (in > 0) {
                float group = icon + gap + textAdd, left = cx - group / 2, dy = (1 - in) * lift;
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                float iconX = rtl ? left + textAdd + gap + icon / 2 : left + icon / 2, textX = rtl ? left : left + icon + gap;
                text.setColor(ink); text.setAlpha(Math.round(Color.alpha(ink) * in));
                c.drawText(add, textX, base + dy, text);
                plus.setColor(ink); plus.setAlpha(Math.round(Color.alpha(ink) * in));
                int ps = c.save();
                c.translate(iconX, cy + dy);
                c.rotate(-90 * (1 - in));
                float k = .5f + .5f * in, arm = icon * .27f * k;
                c.drawLine(-arm, 0, arm, 0, plus); c.drawLine(0, -arm, 0, arm, plus);
                c.restoreToCount(ps);
            }
            c.restoreToCount(save);
        }

        boolean inside(MotionEvent e, float extra) {
            return e.getX() > box.left - extra && e.getX() < box.right + extra && e.getY() > box.top - extra && e.getY() < box.bottom + extra;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            float reach = dp(getContext(), 8);
            if (a == MotionEvent.ACTION_DOWN) {
                if (!inside(e, reach)) return false;
                pressTo = 1; tick(); return true;
            }
            if (a == MotionEvent.ACTION_MOVE && !inside(e, reach * 2.5f)) { pressTo = 0; tick(); }
            if (a == MotionEvent.ACTION_UP) { boolean was = pressTo > 0; pressTo = 0; tick(); if (was) performClick(); }
            if (a == MotionEvent.ACTION_CANCEL) { pressTo = 0; tick(); }
            return true;
        }

        void tick() { if (ticking) return; ticking = true; last = 0; android.view.Choreographer.getInstance().postFrameCallback(this); }

        @Override public void doFrame(long nanos) {
            float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;

            float[] r = spring(s, sv, to, to > .5f ? 340 : 420, to > .5f ? .68f : .86f, dt); s = r[0]; sv = r[1];
            r = spring(press, pressV, pressTo, 900, .7f, dt); press = r[0]; pressV = r[1];
            boolean settled = Math.abs(s - to) < .001f && Math.abs(sv) < .005f && Math.abs(press - pressTo) < .002f && Math.abs(pressV) < .01f;
            if (settled) { s = to; sv = 0; press = pressTo; pressV = 0; ticking = false; MAIN.removeCallbacks(panel.idle); MAIN.postDelayed(panel.idle, 160); }
            else android.view.Choreographer.getInstance().postFrameCallback(this);
            invalidate();
        }

        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); ticking = false; android.view.Choreographer.getInstance().removeFrameCallback(this); s = to; sv = 0; press = pressTo = 0; }
    }

    static float[] spring(float x, float v, float to, float k, float zeta, float dt) {
        float c = 2 * zeta * (float) Math.sqrt(k);
        int steps = Math.max(1, Math.round(dt / .004f)); float h = dt / steps;
        for (int i = 0; i < steps; i++) { float a = -k * (x - to) - c * v; v += a * h; x += v * h; }
        return new float[]{x, v};
    }

    static final class Menu extends FrameLayout implements android.view.Choreographer.FrameCallback {
        final Panel panel;
        final LinearLayout rows;
        final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint(), dots = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF pill = new android.graphics.RectF(), target = new android.graphics.RectF(), now = new android.graphics.RectF();
        float p, pv, q, qv, press, pressV, pressTarget;
        boolean open, ticking, down;
        long last, openedAt, closedAt;
        int count;

        GlassRim rim, movingRim;
        String rimFor;
        SoftScrollEdges.NativeBlur blur;
        boolean blurFailed;
        final android.graphics.Path shape = new android.graphics.Path();

        final android.graphics.RectF list = new android.graphics.RectF(), bar = new android.graphics.RectF();
        final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph drop;
        boolean slider, dragging;
        float s, sv, value = 1f, at = 1f, atV, grab, grabV;
        int written = -1;

        Menu(Context c, Panel panel) {
            super(c);
            this.panel = panel;
            setWillNotDraw(false);
            rows = new LinearLayout(c);
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
            rows.setVisibility(INVISIBLE);
            addView(rows, new LayoutParams(0, 0));
        }

        void item(String label, int glyph, final Runnable action) {
            Context c = getContext();
            Look look = panel.look;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c, 14), 0, dp(c, 14), 0);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(new Glyph(glyph, look.onCard, dp(c, 1.8f)));
            row.addView(icon, new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
            TextView text = new TextView(c);
            text.setText(I18n.cs(label)); text.setTextSize(15.5f); text.setTypeface(I18n.medium()); text.setTextColor(look.onCard); text.setSingleLine(true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f); tp.setMarginStart(dp(c, 14));
            row.addView(text, tp);
            GradientDrawable mask = round(Color.WHITE, dp(c, 18));
            row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(look.dark || look.superDark ? 0x22ffffff : 0x18000000), null, mask));
            row.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (action == null) { slide(); return; }
                close(); postDelayed(action, 140);
            });
            rows.addView(row, new LinearLayout.LayoutParams(-1, dp(c, 52)));
            count++;
        }

        void measure() {
            Context c = getContext();
            float right = getWidth() - dp(c, 14), top = panel.topInset + dp(c, 6), size = dp(c, 40);
            pill.set(right - size, top, right, top + size);
            float w = Math.min(dp(c, 232), getWidth() - dp(c, 28));
            list.set(right - w, top, right, top + dp(c, 16) + count * dp(c, 52));

            bar.set(dp(c, 14), top, right, top + dp(c, 56));
            float k = ease(s);
            target.set(lerp(list.left, bar.left, k), top, right, lerp(list.bottom, bar.bottom, k));
        }

        void slide() {
            if (slider) return;
            value = at = Math.max(.01f, opacity(getContext()) / 100f); atV = 0; written = Math.round(value * 100);
            slider = true;
            tick();
        }

        float trackLeft() { return bar.left + dp(getContext(), 58); }
        float trackRight() { return bar.right - dp(getContext(), 26); }

        void put(float x) {
            value = Math.max(.01f, Math.min(1f, (x - trackLeft()) / Math.max(1f, trackRight() - trackLeft())));
            tick();
        }

        void write() {
            if (!PatronGate.ok(getContext())) return;
            int now = Math.max(1, Math.min(100, Math.round(at * 100)));
            if (now == written) return;
            written = now;
            try { Settings.Secure.putInt(getContext().getContentResolver(), sLock ? LOCK_OPACITY_KEY : OPACITY_KEY, now); }
            catch (Throwable t) { Log.w("opacity", t); }
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            measure();
            rows.measure(MeasureSpec.makeMeasureSpec(Math.round(list.width()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(list.height()), MeasureSpec.EXACTLY));
            rows.layout(Math.round(list.left), Math.round(list.top), Math.round(list.right), Math.round(list.bottom));
        }

        static float lerp(float a, float b, float t) { return a + (b - a) * t; }
        static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
        static float ease(float t) { t = clamp(t); return 1 - (1 - t) * (1 - t) * (1 - t); }

        @Override protected void dispatchDraw(Canvas c) {
            if (getWidth() == 0) return;
            measure();
            Context ctx = getContext();
            Look look = panel.look;
            float pw = clamp(p), shown = Math.max(pw, clamp(q));
            if (shown > 0) { scrim.setColor(Color.BLACK); scrim.setAlpha(Math.round((look.dark || look.superDark ? 110 : 46) * shown * (1 - clamp(s * 1.4f)))); c.drawRect(0, 0, getWidth(), getHeight(), scrim); }
            now.set(lerp(pill.left, target.left, p), pill.top, pill.right, lerp(pill.bottom, target.bottom, q));
            if (now.bottom < pill.bottom) now.bottom = pill.bottom;
            if (now.left > pill.left) now.left = pill.left;
            float squeeze = 1 - .06f * press;
            int save = c.save();
            c.scale(squeeze, squeeze, pill.centerX(), pill.centerY());
            float radius = lerp(pill.height() / 2f, dp(ctx, 26), clamp(p * 1.2f));

            int from = look.card;
            int to = look.superDark ? FrostedSurface.SUPER_DARK_MENU : look.blur ? (FrostedSurface.tone(ctx) & 0xffffff) | 0xd9000000 : look.menu;
            float mix = clamp(shown * 1.6f);
            int color = Color.argb(Math.round(lerp(Color.alpha(from), Color.alpha(to), mix)), Math.round(lerp(Color.red(from), Color.red(to), mix)), Math.round(lerp(Color.green(from), Color.green(to), mix)), Math.round(lerp(Color.blue(from), Color.blue(to), mix)));
            boolean frosted = false;
            if (look.blur && shown > 0.02f && !blurFailed && c.isHardwareAccelerated() && android.os.Build.VERSION.SDK_INT >= 31 && getParent() instanceof ViewGroup) {
                try {
                    if (blur == null) blur = new SoftScrollEdges.NativeBlur(Math.max(1, FrostedSurface.depthBlurRadius(ctx) / 2));
                    int h = Math.min(getHeight(), Math.round(now.bottom) + dp(ctx, 48));
                    Canvas rec = blur.begin(getWidth(), h);
                    rec.drawColor(look.base | 0xff000000);
                    ViewGroup parent = (ViewGroup) getParent();
                    for (int k = 0; k < parent.getChildCount(); k++) {
                        View child = parent.getChildAt(k);

                        if (child == this || child == panel.bg || child.getVisibility() != VISIBLE) continue;
                        int rs = rec.save();
                        rec.translate(child.getLeft() + child.getTranslationX(), child.getTop() + child.getTranslationY());
                        child.draw(rec);
                        rec.restoreToCount(rs);
                    }
                    blur.end();
                    frosted = true;
                } catch (Throwable t) { blurFailed = true; if (blur != null) blur.finish(); }
            }
            if (shown > 0.02f) glass.setShadowLayer(dp(ctx, 18) * shown, 0, dp(ctx, 6) * shown, look.dark || look.superDark ? 0x66000000 : 0x2a000000); else glass.clearShadowLayer();
            if (frosted) {
                glass.setColor(look.base | 0xff000000);
                c.drawRoundRect(now, radius, radius, glass);
                shape.reset(); shape.addRoundRect(now, radius, radius, android.graphics.Path.Direction.CW);
                int bs = c.save();
                c.clipPath(shape);
                try { blur.draw(c); } catch (Throwable t) { blurFailed = true; }
                c.restoreToCount(bs);
                glass.clearShadowLayer();
            }
            glass.setColor(color);
            c.drawRoundRect(now, radius, radius, glass);

            if (!look.signature.equals(rimFor)) {
                rimFor = look.signature;
                rim = GlassRim.wanted(ctx) ? GlassRim.of(ctx, dp(ctx, 26)) : null;

                movingRim = rim != null && look.superDark ? new GlassRim(dp(ctx, 26), FrostedSurface.dp(ctx, 1.5f), true) : null;
            }
            if (rim != null) (ticking && movingRim != null ? movingRim : rim).draw(c, now.left, now.top, now.right, now.bottom, radius, 255);
            float face = clamp(1 - pw * 2.6f);
            if (face > 0) {
                dots.setColor(look.onCard); dots.setAlpha(Math.round(255 * face));
                float cx = pill.centerX(), cy = pill.centerY(), gap = dp(ctx, 6.5f), r = dp(ctx, 1.9f);
                int rs = c.save(); c.rotate(90 * clamp(p), cx, cy);
                for (int i = -1; i <= 1; i++) c.drawCircle(cx, cy + i * gap, r, dots);
                c.restoreToCount(rs);
            }
            if (shown > 0.001f) {
                rows.setVisibility(VISIBLE);
                rows.setClipBounds(new android.graphics.Rect(Math.round(Math.max(0, now.left - list.left)), 0, Math.round(now.right - list.left), Math.round(now.bottom - list.top)));
                long t = android.os.SystemClock.uptimeMillis() - openedAt;
                float out = open ? 1 : clamp(q * 1.6f - .35f);
                float leave = 1 - clamp(s * 2.4f);
                for (int i = 0; i < rows.getChildCount(); i++) {
                    View r = rows.getChildAt(i);
                    float e = (open ? ease((t - 90 - i * 40) / 260f) : out) * leave;
                    r.setAlpha(e); r.setTranslationY((1 - e) * dp(ctx, 18) * (s > 0 ? -.5f : 1f)); float sc = .94f + .06f * e; r.setScaleX(sc); r.setScaleY(sc);
                }
                if (leave > 0.01f) super.dispatchDraw(c);
                if (s > 0.01f) slider(c, ctx, look, clamp(s * 2.2f - 1f) * out);
            } else rows.setVisibility(INVISIBLE);
            c.restoreToCount(save);
        }

        void slider(Canvas c, Context ctx, Look look, float in) {
            if (in <= 0) return;
            int ink = look.onCard;
            float cy = bar.centerY(), left = trackLeft(), right = trackRight();
            int sv2 = c.save();
            c.clipRect(now);
            c.translate(0, (1 - in) * dp(ctx, 10));
            if (drop == null) drop = new Glyph(Glyph.OPACITY, ink, dp(ctx, 1.8f));
            drop.paint.setColor(ink); drop.setAlpha(Math.round(255 * in)); drop.level = at;
            int half = dp(ctx, 11), ix = Math.round(bar.left + dp(ctx, 29));
            drop.setBounds(ix - half, Math.round(cy) - half, ix + half, Math.round(cy) + half);
            drop.draw(c);

            float end = lerp(left, right, ease(in));
            float h = dp(ctx, 3) + dp(ctx, 1.2f) * grab, x = lerp(left, right, at);
            track.setStyle(Paint.Style.FILL);
            track.setColor(ink); track.setAlpha(Math.round(46 * in));
            c.drawRoundRect(left, cy - h, end, cy + h, h, h, track);
            track.setAlpha(Math.round(255 * in));
            c.drawRoundRect(left, cy - h, Math.min(end, x), cy + h, h, h, track);

            float pop = ease(clamp(in * 1.6f - .6f));
            float r = dp(ctx, 10) * pop * (1 + .22f * grab);
            if (grab > 0.01f) { track.setAlpha(Math.round(34 * grab * in)); c.drawCircle(x, cy, r + dp(ctx, 9) * grab, track); }
            track.setColor(look.dark || look.superDark ? Color.WHITE : ink); track.setAlpha(Math.round(255 * in));
            track.setShadowLayer(dp(ctx, 4), 0, dp(ctx, 1.5f), 0x40000000);
            c.drawCircle(x, cy, r, track);
            track.clearShadowLayer();
            c.restoreToCount(sv2);
        }

        boolean inside(MotionEvent e, float extra) {
            return e.getX() > pill.left - extra && e.getX() < pill.right + extra && e.getY() > pill.top - extra && e.getY() < pill.bottom + extra;
        }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            float reach = dp(getContext(), 10);
            if (!open && p < .02f) {
                if (a == MotionEvent.ACTION_DOWN) {
                    if (!inside(e, reach)) return false;
                    down = true; pressTarget = 1; tick(); return true;
                }
                if (!down) return false;
                if (a == MotionEvent.ACTION_MOVE && !inside(e, reach * 2.5f)) { pressTarget = 0; tick(); }
                if (a == MotionEvent.ACTION_UP) { boolean was = pressTarget > 0; down = false; pressTarget = 0; if (was) { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); open(); } tick(); }
                if (a == MotionEvent.ACTION_CANCEL) { down = false; pressTarget = 0; tick(); }
                return true;
            }
            if (open && a == MotionEvent.ACTION_DOWN && !now.contains(e.getX(), e.getY())) { close(); return true; }
            if (!open) return true;
            if (slider) {
                if (s < .6f) return true;
                if (a == MotionEvent.ACTION_DOWN) {
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    put(e.getX());
                } else if (a == MotionEvent.ACTION_MOVE && dragging) put(e.getX());
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { dragging = false; tick(); }
                return true;
            }
            return super.dispatchTouchEvent(e);
        }

        void open() {
            if (open) return;
            open = true; openedAt = android.os.SystemClock.uptimeMillis();
            rows.setVisibility(VISIBLE);
            panel.backCallback(true);
            fast(panel.root, true);
            tick();
        }

        void close() {
            if (!open) return;
            open = false; closedAt = android.os.SystemClock.uptimeMillis();
            tick();
        }

        void reset() { open = false; p = q = pv = qv = 0; press = pressTarget = 0; down = false; slider = dragging = false; s = sv = grab = grabV = 0; rows.setVisibility(INVISIBLE); invalidate(); }

        void tick() { if (ticking) return; ticking = true; last = 0; android.view.Choreographer.getInstance().postFrameCallback(this); }

        @Override public void doFrame(long nanos) {
            float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
            long nowMs = android.os.SystemClock.uptimeMillis();
            float to = open ? 1 : 0;
            float[] r;
            if (open) {
                r = spring(p, pv, to, 380, .62f, dt); p = r[0]; pv = r[1];
                if (nowMs - openedAt > 45) { r = spring(q, qv, to, 300, .66f, dt); q = r[0]; qv = r[1]; }
            } else {
                r = spring(q, qv, to, 520, .92f, dt); q = r[0]; qv = r[1];
                if (nowMs - closedAt > 40) { r = spring(p, pv, to, 460, .78f, dt); p = r[0]; pv = r[1]; }
            }
            r = spring(press, pressV, pressTarget, 900, .7f, dt); press = r[0]; pressV = r[1];

            float sTo = slider ? 1 : 0, grabTo = dragging ? 1 : 0;
            boolean sRest = true;
            if (open) {
                r = spring(s, sv, sTo, 340, .78f, dt); s = r[0]; sv = r[1];
                r = spring(grab, grabV, grabTo, 700, .7f, dt); grab = r[0]; grabV = r[1];
                if (slider) {
                    r = spring(at, atV, value, 900, 1f, dt); at = Math.max(.01f, Math.min(1f, r[0])); atV = r[1];
                    write();
                }
                sRest = Math.abs(s - sTo) < .001f && Math.abs(sv) < .005f && Math.abs(grab - grabTo) < .002f && Math.abs(grabV) < .01f
                        && (!slider || (Math.abs(at - value) < .0015f && Math.abs(atV) < .01f));
                if (sRest) { s = sTo; sv = 0; grab = grabTo; grabV = 0; if (slider) { at = value; atV = 0; write(); } }
            }
            invalidate();
            boolean settled = Math.abs(p - to) < .001f && Math.abs(pv) < .005f && Math.abs(q - to) < .001f && Math.abs(qv) < .005f
                    && Math.abs(press - pressTarget) < .002f && Math.abs(pressV) < .01f && (!open || nowMs - openedAt > 500) && sRest;
            if (settled) {
                p = q = to; pv = qv = 0; press = pressTarget; ticking = false; invalidate();
                if (!open) {
                    boolean was = slider;
                    rows.setVisibility(INVISIBLE); slider = dragging = false; s = sv = grab = grabV = 0; panel.moving();
                    if (was) panel.root.post(panel::settingsChanged);
                }
                return;
            }
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow(); android.view.Choreographer.getInstance().removeFrameCallback(this); ticking = false;
            if (blur != null) { blur.release(); blur = null; }
        }
    }

    static final class Panel {
        final Context activity;
        float hub, own = 1f;
        final Root root;
        LockPicker picker;
        final boolean rtl;
        boolean far;

        boolean right() { return sLock ? lockRight(activity) : homeRight(activity); }
        Look look;
        View bg;
        LinearLayout column, header, contentBox;
        FrameLayout content;
        Edge edge;
        Object weatherShown;

        FrameLayout top;
        ScrollView scroll;
        Grid grid;
        FrameLayout empty, check;
        Menu menu;
        ActionPill action;
        TextView day, date, temp, condition;
        ImageView weatherIcon;
        LinearLayout weatherBox;
        View grabber;
        float progress;
        boolean wiggle, pulling, barsOurs;
        int barsSaved;
        ValueAnimator settle;
        Object back;
        int topInset, bottomInset;

        Panel(Activity a) {
            activity = a;
            rtl = a.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            far = homeEnabled(a) && homeFar(a);
            root = new Root(a, this);
            idle = () -> { if (!pulling && (settle == null || !settle.isRunning()) && !grid.busy() && !menu.ticking && (action == null || !action.ticking)) fast(root, false); };
            root.setVisibility(View.GONE);
            root.setClickable(true);

            ViewGroup parent = null;
            View decor = a.getWindow().getDecorView();
            if (decor instanceof ViewGroup) parent = (ViewGroup) decor;
            if (parent == null) {
                ViewGroup drag = (ViewGroup) Xp.callMethod(a, "getDragLayer");
                parent = drag.getParent() instanceof ViewGroup ? (ViewGroup) drag.getParent() : drag;
            }
            parent.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.setElevation(dp(a, 1));
            root.setOnApplyWindowInsetsListener((v, insets) -> { applyInsets(insets); return insets; });
            build();
        }

        Panel(Context c, ViewGroup parent) {
            activity = c;
            rtl = c.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            root = new Root(c, this);
            idle = () -> { if ((settle == null || !settle.isRunning()) && !grid.busy() && !menu.ticking && (action == null || !action.ticking)) fast(root, false); };
            root.setVisibility(View.GONE);
            root.setClickable(true);
            parent.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            build();
        }

        Swing swing;
        long hubAt;
        final long[] hubT = new long[6]; final float[] hubP = new float[6]; int hubN;
        Swing swing() { if (swing == null) swing = new Swing(this); return swing; }
        boolean swinging() { return swing != null && swing.running; }

        void lockHub(float p) {
            long now = System.nanoTime();

            if (p > hub + 0.0005f && !root.tracking && swinging() && swing.target > 0f) { swing.stop(true); own = 1f; }
            if (p > hub + 0.0005f && !root.tracking && (settle == null || !settle.isRunning()) && !swinging()) own = 1f;

            if (p >= 1f && hub > 0.02f && hub < 1f && !root.tracking && !swinging()) {
                float v = 0f;
                for (int i = 0; i < hubN; i++) {
                    float dt = (now - hubT[i]) / 1e9f;
                    if (dt > 0.004f && dt < 0.07f) v = Math.max(v, (p - hubP[i]) / dt * swing().width());
                }
                if (hubFlick >= 0f) v = hubFlick;
                if (v > Swing.ARRIVE_DP * swing().density) swing().arrive(v);
            }
            if (p <= 0f || p >= 1f) { hubN = 0; hubFlick = -1f; }
            else { if (hubN == hubT.length) { System.arraycopy(hubT, 1, hubT, 0, hubN - 1); System.arraycopy(hubP, 1, hubP, 0, hubN - 1); hubN--; } hubT[hubN] = now; hubP[hubN] = p; hubN++; }
            hubAt = now;
            if (p != hub) moving();
            hub = p;
            lockApply();
            if (hub <= 0f && !root.tracking) { if (settle != null) settle.cancel(); if (swing != null) swing.stop(true); own = 1f; }
        }

        float hubFlick = -1f;

        void hubReleased() {
            long now = System.nanoTime(); float v = 0f;
            for (int i = 0; i < hubN; i++) {
                float dt = (now - hubT[i]) / 1e9f;
                if (dt > 0.004f && dt < 0.07f) v = Math.max(v, (hub - hubP[i]) / dt * swing().width());
            }
            hubFlick = hub > 0f && hub < 1f ? v : -1f;
        }

        void leftByFinger() {
            own = 0f;
            lockApply();
            if (sLock && lockOther(activity) && hub > 0f) Lock.snapClose();
            if (content != null) content.setTranslationX(0f);
            fast(root, false);

            MAIN.postDelayed(() -> { if (hub >= 1f && own <= 0f && !root.tracking && !swinging()) { own = 1f; lockApply(); } }, 1500);
        }

        void lockApply() {
            float now = Math.max(0f, Math.min(1f, Math.min(hub, own)));
            boolean visible = root.getVisibility() == View.VISIBLE;
            if (now > 0f && !visible) {
                if (!new Look(activity).signature.equals(built)) { build(); weatherShown = null; }
                listen(activity);
                refresh();
                root.setVisibility(View.VISIBLE);
                look.background(bg);
            }
            progress = now;
            place();
            Lock.raised(now > 0f && !Lock.pinUp);
            if (now <= 0f && visible) hidden();
        }

        void pickWidget() {
            if (!editAllowed()) return;
            if (sLock) Lock.pick(activity); else pick((Activity) activity);
        }

        void showPicker() {
            if (picker != null) return;
            picker = new LockPicker(this);
            root.addView(picker, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            picker.open();
        }

        void detach() {
            closeNow();
            if (root.getParent() instanceof ViewGroup) ((ViewGroup) root.getParent()).removeView(root);
        }

        void build() {
            Context c = activity;
            look = new Look(c);
            built = look.signature;
            root.removeAllViews();
            root.setBackground(null);

            bg = new View(c);
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
            look.background(bg);

            content = new FrameLayout(c);
            content.setClipChildren(false);
            root.addView(content, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout area = content;

            scroll = new BounceScroll(c);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setClipChildren(false);
            scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> { moving(); showGrabber(); edge.invalidate(); });
            edge = new Edge(c, this, scroll);
            area.addView(edge, new FrameLayout.LayoutParams(-1, -1));

            column = new LinearLayout(c);
            column.setOrientation(LinearLayout.VERTICAL);
            LayoutTransition lt = new LayoutTransition();
            lt.enableTransitionType(LayoutTransition.CHANGING);
            lt.setDuration(320);
            lt.setInterpolator(LayoutTransition.CHANGING, new DecelerateInterpolator(1.6f));
            column.setLayoutTransition(lt);
            content.addView(column, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
            column.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (b != ob) pads(); });

            top = new FrameLayout(c);
            column.addView(top, new LinearLayout.LayoutParams(-1, dp(c, 52)));

            buildHeader(c);
            column.addView(header, new LinearLayout.LayoutParams(-1, -2));

            contentBox = new LinearLayout(c);
            contentBox.setOrientation(LinearLayout.VERTICAL);
            contentBox.setClipChildren(false); contentBox.setClipToPadding(false);
            int side = dp(c, 16);
            contentBox.setPadding(side, dp(c, 8), side, dp(c, 24));
            scroll.addView(contentBox, new FrameLayout.LayoutParams(-1, -2));

            grid = new Grid(c, this);
            contentBox.addView(grid, new LinearLayout.LayoutParams(-1, -2));

            action = new ActionPill(c, this);
            action.setOnClickListener(v -> { if (wiggle) pickWidget(); else enterWiggle(); });
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
            ap.gravity = Gravity.CENTER_HORIZONTAL; ap.topMargin = dp(c, 16);
            contentBox.addView(action, ap);
            action.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (ot == t || ob == 0 || progress <= 0) return;
                v.setTranslationY(v.getTranslationY() + ot - t);
                v.animate().translationY(0f).setDuration(420).setInterpolator(new Spring(0.7f, 17f, 420)).start();
            });

            empty = new FrameLayout(c);
            ImageView plus = new ImageView(c);
            plus.setImageDrawable(new Glyph(Glyph.PLUS, look.onCard, dp(c, 3)));
            plus.setBackground(new PillSurface(c, look));
            plus.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            plus.setContentDescription(I18n.cs("Add widget"));
            plus.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); pickWidget(); });
            int big = dp(c, 80);
            empty.addView(plus, new FrameLayout.LayoutParams(big, big, Gravity.CENTER));
            area.addView(empty, new FrameLayout.LayoutParams(-1, -1));

            grabber = buildGrabber(c);
            grabber.setVisibility(View.GONE);
            area.addView(grabber, new FrameLayout.LayoutParams(dp(c, 28), -1, rtl ? Gravity.LEFT : Gravity.RIGHT));

            check = new FrameLayout(c);
            ImageView mark = new ImageView(c);
            mark.setImageDrawable(new Glyph(Glyph.CHECK, look.onCard, dp(c, 2.6f)));
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            check.addView(mark, new FrameLayout.LayoutParams(-1, -1));
            check.setContentDescription(I18n.cs("Done"));
            check.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); done(); });
            top.addView(check, new FrameLayout.LayoutParams(dp(c, 64), dp(c, 40), Gravity.CENTER));
            check.setBackground(new PillSurface(c, look));
            check.setScaleX(0f); check.setScaleY(0f); check.setVisibility(View.GONE);

            menu = new Menu(c, this);
            menu.item("Add widget", Glyph.PLUS, () -> pickWidget());
            menu.item("Background opacity", Glyph.OPACITY, null);
            menu.item("Feed settings", Glyph.GEAR, this::openSettings);
            content.addView(menu, new FrameLayout.LayoutParams(-1, -1));

            List<Integer> ids = load(c);
            List<Integer> kept = new ArrayList<Integer>();
            AppWidgetManager manager = AppWidgetManager.getInstance(c);
            for (Integer id : ids) {
                if (manager.getAppWidgetInfo(id) == null) { try { host(c).deleteAppWidgetId(id); } catch (Throwable ignored) { } continue; }
                kept.add(id);
                addCard(id, false, -1);
            }
            if (kept.size() != ids.size()) save(c, kept);
            updateEmpty();
            headerShown(!Feature.on(c, headerKey()), false);
            WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) applyInsets(insets);
        }

        void buildHeader(Context c) {
            header = new LinearLayout(c);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.BOTTOM);
            header.setPadding(dp(c, 24), dp(c, 4), dp(c, 24), dp(c, 12));

            LinearLayout dateBox = new LinearLayout(c);
            dateBox.setOrientation(LinearLayout.VERTICAL);
            day = new TextView(c); day.setTextSize(15); day.setTypeface(I18n.medium()); day.setTextColor(look.accent); look.shadow(day);
            date = new TextView(c); date.setTextSize(30); date.setTextColor(look.text); date.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); look.shadow(date);
            date.setSingleLine(true);
            dateBox.addView(day); dateBox.addView(date);
            dateBox.setOnClickListener(v -> openCalendar());
            dateBox.setBackground(ripple(c));
            header.addView(dateBox, new LinearLayout.LayoutParams(0, -2, 1f));

            weatherBox = new LinearLayout(c);
            weatherBox.setOrientation(LinearLayout.VERTICAL);
            weatherBox.setGravity(Gravity.END);
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            weatherIcon = new ImageView(c);
            line.addView(weatherIcon, new LinearLayout.LayoutParams(dp(c, 34), dp(c, 34)));
            temp = new TextView(c); temp.setTextSize(30); temp.setTextColor(look.text); look.shadow(temp); temp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); temp.setSingleLine(true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.setMarginStart(dp(c, 6));
            line.addView(temp, tp);
            weatherBox.addView(line);
            condition = new TextView(c); condition.setTextSize(13); condition.setTextColor(look.muted); condition.setSingleLine(true); look.shadow(condition);
            condition.setMaxWidth(dp(c, 150)); condition.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.gravity = Gravity.END;
            weatherBox.addView(condition, cp);
            weatherBox.setOnClickListener(v -> openWeather());
            weatherBox.setBackground(ripple(c));
            header.addView(weatherBox, new LinearLayout.LayoutParams(-2, -2));
        }

        Drawable ripple(Context c) {
            android.util.TypedValue tv = new android.util.TypedValue();
            c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            try { return c.getDrawable(tv.resourceId); } catch (Throwable t) { return null; }
        }

        View buildGrabber(Context c) {
            final FrameLayout touch = new FrameLayout(c);
            final View thumb = new View(c);
            thumb.setBackground(round(look.muted & 0xb3ffffff, dp(c, 3)));
            touch.addView(thumb, new FrameLayout.LayoutParams(dp(c, 6), dp(c, 52), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            touch.setAlpha(0f);
            touch.setOnTouchListener((v, ev) -> {
                if (touch.getAlpha() < 0.2f || !scrollable()) return false;
                int a = ev.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) { v.getParent().requestDisallowInterceptTouchEvent(true); v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                    float travel = Math.max(1, touch.getHeight() - thumb.getHeight());
                    float f = Math.max(0f, Math.min(1f, (ev.getY() - thumb.getHeight() / 2f) / travel));
                    scroll.scrollTo(0, Math.round(f * range()));
                    moving();
                    touch.animate().cancel(); touch.setAlpha(1f);
                }
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) hideGrabberLater();
                return true;
            });
            touch.setTag(thumb);
            return touch;
        }

        int range() { return Math.max(0, contentBox.getHeight() - scroll.getHeight() + scroll.getPaddingTop() + scroll.getPaddingBottom()); }
        boolean scrollable() { return range() > dp(activity, 48); }

        final Runnable hideGrabber = () -> grabber.animate().alpha(0f).setDuration(300).start();
        void showGrabber() {
            if (true) return;
            if (!scrollable()) return;
            View thumb = (View) grabber.getTag();
            float travel = Math.max(0, grabber.getHeight() - thumb.getHeight());
            thumb.setTranslationY(travel * scroll.getScrollY() / (float) Math.max(1, range()));
            grabber.animate().cancel(); grabber.setAlpha(1f);
            hideGrabberLater();
        }
        void hideGrabberLater() { MAIN.removeCallbacks(hideGrabber); MAIN.postDelayed(hideGrabber, 1200); }

        void applyInsets(WindowInsets insets) {
            int t = insets.getSystemWindowInsetTop(), b = insets.getSystemWindowInsetBottom();
            if (t == topInset && b == bottomInset && column.getPaddingTop() == t) return;
            topInset = t; bottomInset = b;
            column.setPadding(insets.getSystemWindowInsetLeft(), topInset, insets.getSystemWindowInsetRight(), 0);
            pads();
            menu.requestLayout();
        }

        void pads() {
            int top = column.getBottom() > 0 ? column.getBottom() : topInset + dp(activity, 52);
            if (scroll.getPaddingTop() != top || scroll.getPaddingBottom() != bottomInset) scroll.setPadding(0, top, 0, bottomInset);
            FrameLayout.LayoutParams gp = (FrameLayout.LayoutParams) grabber.getLayoutParams();
            int gb = bottomInset + dp(activity, 12);
            if (gp.topMargin != top || gp.bottomMargin != gb) { gp.topMargin = top; gp.bottomMargin = gb; grabber.setLayoutParams(gp); }
            FrameLayout.LayoutParams ep = (FrameLayout.LayoutParams) empty.getLayoutParams();
            if (ep.topMargin != top) { ep.topMargin = top; empty.setLayoutParams(ep); }
            edge.invalidate();
        }

        void headerShown(boolean shown, boolean animate) {
            if (!animate) {
                LayoutTransition lt = column.getLayoutTransition(); column.setLayoutTransition(null);
                header.setVisibility(shown ? View.VISIBLE : View.GONE);
                column.setLayoutTransition(lt);
                return;
            }
            header.setVisibility(shown ? View.VISIBLE : View.GONE);
        }

        void refresh() {
            Context c = activity;
            WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) applyInsets(insets);
            Locale l = Locale.getDefault();
            Date now = new Date();
            day.setText(DateFormat.format(DateFormat.getBestDateTimePattern(l, "EEEE"), now));
            date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(l, "dMMMM"), now));
            Weather.Now w = Weather.now;
            if (w == weatherShown && w != null) return;
            weatherShown = w;
            boolean has = w != null && w.temp != null && w.temp.length() > 0;
            weatherBox.setVisibility(has ? View.VISIBLE : View.GONE);
            condition.setVisibility(View.GONE);
            if (has) {
                temp.setText(w.temp);
                Drawable d = null;
                try { if (w.icon != null) d = w.icon.loadDrawable(c); } catch (Throwable ignored) { }
                weatherIcon.setImageDrawable(d);
                weatherIcon.setVisibility(d == null ? View.GONE : View.VISIBLE);
            }
        }

        String built;

        static String base(String signature) {
            if (signature == null) return "";
            int a = signature.lastIndexOf('/'), b = a > 0 ? signature.lastIndexOf('/', a - 1) : -1;
            return b > 0 ? signature.substring(0, b) : signature;
        }

        void recolor() {
            if (day == null || date == null || temp == null || condition == null) return;
            day.setTextColor(look.accent); date.setTextColor(look.text); temp.setTextColor(look.text); condition.setTextColor(look.muted);
            for (TextView t : new TextView[]{day, date, temp, condition}) { if (look.overWallpaper) look.shadow(t); else t.setShadowLayer(0f, 0f, 0f, 0); }
        }

        void settingsChanged() {
            Look next = new Look(activity);
            if (!next.signature.equals(built)) {
                if (base(next.signature).equals(base(built))) { look = next; built = next.signature; look.background(bg); recolor(); bars(); headerShown(!Feature.on(activity, headerKey()), progress > 0); return; }

                if (menu != null && menu.open && menu.slider) { look = next; look.background(bg); recolor(); bars(); return; }
                if (wiggle) done();
                build();
                weatherShown = null;
                if (progress > 0) { refresh(); root.setVisibility(View.VISIBLE); place(); }
                bars();
                return;
            }
            if (next.opacity != look.opacity) { look = next; look.background(bg); bars(); }
            headerShown(!Feature.on(activity, headerKey()), progress > 0);
        }

        void addCard(int id, boolean animate, int index) {
            AppWidgetHost host = host(activity);
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(activity).getAppWidgetInfo(id);
            if (info == null) return;
            AppWidgetHostView view;
            if (sLock) Lock.hostClicks(host);
            try { view = host.createView(activity, id, info); } catch (Throwable t) { Log.w("view", t); return; }
            if (sLock) Lock.clicks(view);
            Card card = new Card(activity, this, id, view, info);
            grid.add(card, animate, index);
            updateEmpty();
        }

        void remove(Card card) {
            if (!editAllowed()) {
                card.setAlpha(1f);
                card.setScaleX(1f);
                card.setScaleY(1f);
                return;
            }
            grid.remove(card);
            try { host(activity).deleteAppWidgetId(card.id); } catch (Throwable ignored) { }
            prefs(activity).edit().remove("s" + card.id).apply();
            save(activity, grid.ids());
            updateEmpty();
            if (grid.order.isEmpty() && wiggle) done();
        }

        void updateEmpty() {
            boolean none = grid.order.isEmpty();
            empty.setVisibility(none ? View.VISIBLE : View.GONE);
            action.setVisibility(none ? View.GONE : View.VISIBLE);
            action.show(wiggle, progress > 0 && !none);
        }

        void scrollToEnd() { scroll.post(() -> scroll.smoothScrollTo(0, range())); }

        boolean editAllowed() {
            if (PatronGate.ok(activity)) return true;
            if (picker != null) picker.close();
            if (wiggle) endWiggle(false);
            return false;
        }

        boolean enterWiggle() {
            if (!editAllowed()) return false;
            if (wiggle) return true;
            wiggle = true;
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            grid.wiggle(true);
            check.setVisibility(View.VISIBLE);
            check.animate().cancel();
            check.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.6f)).setListener(null).start();
            updateEmpty();
            backCallback(true);
            return true;
        }

        void done() {
            if (wiggle && editAllowed()) endWiggle(true);
        }

        private void endWiggle(boolean saveOrder) {
            wiggle = false;
            Card resizing = grid.resizing;
            grid.cancelDrag();
            if (saveOrder) save(activity, grid.ids());
            else {
                List<Integer> saved = load(activity);
                grid.order.sort((a, b) -> Integer.compare(saved.indexOf(a.id), saved.indexOf(b.id)));
                if (resizing != null) {
                    resizing.resizing = false;
                    int[] size = size(activity, resizing.id);
                    if (size == null) resizing.defaultSize();
                    else { resizing.w = size[0]; resizing.h = size[1]; }
                }
                for (Card card : grid.order) {
                    MAIN.removeCallbacks(card.longPress);
                    card.routed = null;
                }
                grid.requestLayout();
            }
            grid.wiggle(false);
            check.animate().cancel();
            check.animate().scaleX(0f).scaleY(0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { if (!wiggle) check.setVisibility(View.GONE); }
            }).start();
            updateEmpty();
        }

        void beginPull() {
            if (settle != null) settle.cancel();
            if (!new Look(activity).signature.equals(built)) { build(); weatherShown = null; }
            pulling = true; pullAt = 0; pullSpeed = 0;
            MAIN.removeCallbacks(letGo); MAIN.postDelayed(letGo, 350);
            layer(true);
            listen(activity);
            refresh();
            root.setVisibility(View.VISIBLE);
            fast(root, true);
        }

        long pullAt; float pullSpeed;

        final Runnable letGo = () -> { if (pulling) { release(0f); } };
        void pull(float p) {
            if (!pulling) { if (settle != null) settle.cancel(); pulling = true; layer(true); }
            MAIN.removeCallbacks(letGo); MAIN.postDelayed(letGo, 350);
            long now = android.os.SystemClock.uptimeMillis();
            if (pullAt != 0 && now > pullAt) { float v = (p - progress) * 1000f / (now - pullAt); pullSpeed = pullSpeed * 0.4f + v * 0.6f; }
            pullAt = now;
            setProgress(p);
        }

        void release(float velocity) {
            pulling = false;
            MAIN.removeCallbacks(letGo);
            if (android.os.SystemClock.uptimeMillis() - pullAt > 120) pullSpeed = 0;
            float target;
            if (Math.abs(pullSpeed) > 0.6f) target = pullSpeed > 0 ? 1f : 0f;
            else target = progress > 0.4f ? 1f : 0f;
            if (sLock) animateTo(target, pullSpeed); else springTo(target, pullSpeed);
        }

        static final float SETTLE_STIFFNESS = 300f, SETTLE_DAMPING = 0.8f;

        void springTo(final float target, final float velocity) {
            if (settle != null) settle.cancel();
            settleTarget = target;
            if (root.getVisibility() != View.VISIBLE && target > 0) beginPull();
            if (swing != null) swing.stop(true);
            final float from = progress, d = from - target;
            final double w = Math.sqrt(SETTLE_STIFFNESS), a = SETTLE_DAMPING * w, wd = w * Math.sqrt(1 - SETTLE_DAMPING * SETTLE_DAMPING);
            final double b = (velocity + a * d) / wd;
            final float wide = root.getWidth() > 0 ? root.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            final float density = activity.getResources().getDisplayMetrics().density;
            double reach = (Math.abs(d) + Math.abs(b)) * wide / 0.5;
            final long ms = (long) Math.max(120, Math.min(900, 1000 * Math.log(Math.max(1.0001, reach)) / a));
            final boolean[] crossed = {target <= 0f || from >= target};
            final boolean[] gone = {false};
            layer(true);
            settle = ValueAnimator.ofFloat(0f, 1f);
            settle.setDuration(ms);
            settle.setInterpolator(new android.view.animation.LinearInterpolator());
            settle.addUpdateListener(an -> {
                double s = an.getAnimatedFraction() * ms / 1000.0, e = Math.exp(-a * s), cos = Math.cos(wd * s), sin = Math.sin(wd * s);
                float x = (float) (target + e * (d * cos + b * sin));
                if (!crossed[0] && x >= target) {
                    crossed[0] = true;
                    float flick = Math.abs(velocity) * wide;
                    if (flick > Swing.ARRIVE_DP * density) swing().arrive(flick);
                }
                if (target <= 0f && x <= 0f && !gone[0]) { gone[0] = true; MAIN.post(() -> { if (settle == an && an.isRunning()) an.end(); }); }
                setProgress(x);
            });
            settle.addListener(new AnimatorListenerAdapter() {
                boolean cancelled;
                @Override public void onAnimationCancel(Animator an) { cancelled = true; }
                @Override public void onAnimationEnd(Animator an) {
                    if (cancelled) return;
                    setProgress(target);
                    if (target <= 0f) { hidden(); return; }
                    if (!swinging()) { layer(false); fast(root, false); }
                }
            });
            fast(root, true);
            settle.start();
        }

        void show() { beginPull(); pulling = false; animateTo(1f, 0f); }

        long stayUntil;
        boolean staying() { return android.os.SystemClock.uptimeMillis() < stayUntil; }
        void home() {
            if (menu != null) menu.reset();
            if (wiggle) { stayUntil = android.os.SystemClock.uptimeMillis() + 900; root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); done(); return; }
            if (staying()) return;

            if (sLock) { if (hub > 0f) Lock.close(); return; }
            if (progress > 0) animateTo(0f, 0f);
        }

        float settleTarget;

        void animateTo(final float target, float velocity) {
            if (settle != null) settle.cancel();
            settleTarget = target;
            if (sLock && target <= 0f) Lock.close();
            if (!sLock && root.getVisibility() != View.VISIBLE && target > 0) beginPull();
            final float from = progress;
            float speed = Math.abs(velocity);
            if (swing != null && !sLock) swing.stop(true);

            long ms = (long) Math.max(220, Math.min(420, 420 * Math.abs(target - from) / Math.max(1f, speed * 0.35f)));
            layer(true);
            settle = ValueAnimator.ofFloat(from, target);
            settle.setDuration(ms);
            settle.setInterpolator(new DecelerateInterpolator(speed > 0.6f ? 1.5f : 2.2f));
            settle.addUpdateListener(a -> setProgress((Float) a.getAnimatedValue()));
            settle.addListener(new AnimatorListenerAdapter() {
                boolean cancelled;
                @Override public void onAnimationCancel(Animator a) { cancelled = true; }
                @Override public void onAnimationEnd(Animator a) {
                    if (cancelled) return;
                    setProgress(target);
                    layer(false);
                    if (target <= 0f) hidden(); else fast(root, false);

                    if (sLock && target <= 0f) MAIN.postDelayed(() -> { if (hub >= 1f && own <= 0f && !root.tracking) { own = 1f; lockApply(); } }, 1500);
                }
            });
            fast(root, true);
            settle.start();
        }

        void closeNow() {
            if (settle != null) settle.cancel();
            layer(false);
            setProgress(0f);
            hidden();
        }

        void hidden() {
            if (swing != null) swing.stop(true);
            if (picker != null) { root.removeView(picker); picker = null; }
            if (wiggle) done();
            if (menu != null) menu.reset();
            grid.cancelDrag();
            root.setVisibility(View.GONE);
            layer(false);
            backCallback(false);
            fast(root, false);
            pulling = false;
            bars();
        }

        void setProgress(float p) {
            if (sLock) { own = Math.max(0f, Math.min(1f, p)); lockApply(); return; }
            progress = Math.max(0f, Math.min(1f, p));
            place();
            scrolled(progress);
            bars();
            if (progress >= 1f) backCallback(true);
        }

        void layer(boolean on) {
            int want = on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
            if (content != null && content.getLayerType() != want) content.setLayerType(want, null);
        }

        void place() {
            float w = root.getWidth() > 0 ? root.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            root.setTranslationX(Math.round((right() ? 1 : -1) * w * (1f - progress)));
        }

        void bars() {
            if (sLock) return;
            try {
                final int light = 0x2000 | 0x10;
                View decor = ((Activity) activity).getWindow().getDecorView();
                int flags = decor.getSystemUiVisibility();
                boolean want = progress > 0.5f && root.getVisibility() == View.VISIBLE && look.opacity >= 20;
                if (want) {
                    if (!barsOurs) { barsSaved = flags & light; barsOurs = true; }
                    int next = (flags & ~light) | (look.lightBackground() ? light : 0);
                    if (next != flags) decor.setSystemUiVisibility(next);
                } else if (barsOurs) {
                    barsOurs = false;
                    int next = (flags & ~light) | barsSaved;
                    if (next != flags) decor.setSystemUiVisibility(next);
                }
            } catch (Throwable ignored) { }
        }

        Runnable idle;
        void moving() { fast(root, true); MAIN.removeCallbacks(idle); MAIN.postDelayed(idle, 160); }

        void backCallback(boolean wanted) {
            if (sLock) return;
            try {
                Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(activity);
                Class<?> type = Class.forName("android.window.OnBackInvokedCallback");
                if (wanted && back == null && progress > 0) {
                    back = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, m, args) -> {
                        if ("onBackInvoked".equals(m.getName())) MAIN.post(() -> { if (menu != null && menu.open) menu.close(); else if (wiggle) done(); else animateTo(0f, 0f); });
                        return basic(self, m, args);
                    });
                    dispatcher.getClass().getMethod("registerOnBackInvokedCallback", int.class, type).invoke(dispatcher, 1000000, back);
                } else if (!wanted && back != null) {
                    dispatcher.getClass().getMethod("unregisterOnBackInvokedCallback", type).invoke(dispatcher, back);
                    back = null;
                }
            } catch (Throwable ignored) { }
        }

        void openCalendar() {
            Intent i = activity.getPackageManager().getLaunchIntentForPackage(CALENDAR_APP);
            if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()));
            launch(i);
        }

        void openWeather() {
            Intent i = activity.getPackageManager().getLaunchIntentForPackage(WEATHER_APP);
            Weather.Now w = Weather.now;
            if (i == null && w != null && w.intent instanceof Intent) i = (Intent) w.intent;
            if (i == null && w != null && w.intent instanceof android.app.PendingIntent) {
                try { ((android.app.PendingIntent) w.intent).send(); return; } catch (Throwable ignored) { }
            }
            if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"));
            launch(i);
        }

        void openSettings() {
            Intent i = new Intent();
            i.setClassName("io.github.roviandev.pixelsuite", "io.github.pixelsuite.SettingsActivity");
            i.putExtra("page", KEY);
            launch(i);
        }

        void launch(Intent i) {
            if (sLock) { Lock.start(activity, i); return; }
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); activity.startActivity(i); } catch (Throwable t) { Log.w("open", t); }
        }
    }

    static final class Edge extends FrameLayout {
        final Panel panel;
        final ScrollView scroll;
        final Paint fade = new Paint(), mask = new Paint(), layer = new Paint();
        SoftScrollEdges.NativeBlur blur;
        boolean failed, recording;
        int builtTop = -1, builtBottom = -1;

        Edge(Context c, Panel p, ScrollView s) {
            super(c);
            panel = p; scroll = s;
            follow = () -> {
                View list = s.getChildCount() > 0 ? s.getChildAt(0) : null;
                float now = list == null ? 0 : list.getTranslationY();
                if (now != seen) { seen = now; invalidate(); }
                return true;
            };
            setClipChildren(false);
            addView(s, new LayoutParams(-1, -1));
            fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }

        @Override protected void dispatchDraw(Canvas c) {
            if (recording) { super.dispatchDraw(c); return; }
            int line = scroll.getPaddingTop();
            int soft = dp(getContext(), 28);

            View list = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
            float up = Math.max(0, scroll.getScrollY()) + (list == null ? 0 : Math.max(0f, -list.getTranslationY()));
            float amount = Math.min(1f, up / (float) dp(getContext(), 16));
            if (amount <= 0f || line <= 0 || getWidth() == 0) { super.dispatchDraw(c); return; }
            int bottom = line + soft, start = Math.max(0, line - dp(getContext(), 96));
            if (builtTop != start || builtBottom != bottom) {
                builtTop = start; builtBottom = bottom;
                float span = Math.max(1, bottom - start), hold = (line - dp(getContext(), 12) - start) / span;

                fade.setShader(new android.graphics.LinearGradient(0, start, 0, bottom, new int[]{0x00ffffff, 0x00ffffff, 0xffffffff}, new float[]{0f, Math.max(0.01f, hold), 1f}, android.graphics.Shader.TileMode.CLAMP));

                mask.setShader(new android.graphics.LinearGradient(0, start, 0, bottom, new int[]{0x00ffffff, 0xffffffff, 0xffffffff, 0x00ffffff}, new float[]{0f, Math.max(0.02f, hold * 0.55f), Math.max(0.03f, hold), 1f}, android.graphics.Shader.TileMode.CLAMP));
            }
            boolean blurred = false;
            if (panel.look.blur && !failed && c.isHardwareAccelerated() && android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    if (blur == null) blur = new SoftScrollEdges.NativeBlur(Math.max(1, FrostedSurface.depthBlurRadius(getContext()) / 2));
                    recording = true;
                    Canvas rec = blur.begin(getWidth(), bottom);
                    super.dispatchDraw(rec);
                    blur.end();
                    blurred = true;
                } catch (Throwable t) { failed = true; if (blur != null) blur.finish(); }
                finally { recording = false; }
            }

            int save = c.saveLayer(0, 0, getWidth(), getHeight(), null);
            super.dispatchDraw(c);
            fade.setAlpha(255);
            c.drawRect(0, 0, getWidth(), bottom, fade);
            c.restoreToCount(save);
            if (amount < 1f) {
                layer.setAlpha(Math.round(255 * (1f - amount)));
                int s2 = c.saveLayer(0, 0, getWidth(), bottom, layer);
                c.clipRect(0, 0, getWidth(), bottom);
                super.dispatchDraw(c);
                c.restoreToCount(s2);
            }
            if (blurred) {
                layer.setAlpha(Math.round(255 * amount));
                int s3 = c.saveLayer(0, start, getWidth(), bottom, layer);
                try { blur.draw(c); c.drawRect(0, start, getWidth(), bottom, mask); } catch (Throwable ignored) { }
                c.restoreToCount(s3);
            }
        }

        float seen;
        android.view.ViewTreeObserver.OnPreDrawListener follow;
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); getViewTreeObserver().addOnPreDrawListener(follow); }
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            getViewTreeObserver().removeOnPreDrawListener(follow);
            if (blur != null) { blur.release(); blur = null; }
        }
    }

    static final class Root extends FrameLayout {
        final Panel panel;
        final int slop;
        float x0, y0;
        boolean tracking;
        VelocityTracker velocity;
        Root(Context c, Panel p) {
            super(c); panel = p; slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setOnDragListener((v, e) -> {
                try { return dragged(e); } catch (Throwable t) { Log.w("drop", t); return false; }
            });
        }

        boolean dragged(android.view.DragEvent e) {
            if (sLock) return false;
            switch (e.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return panel.progress > 0 && e.getClipDescription() != null && e.getClipDescription().hasMimeType("text/vnd.android.intent");
                case android.view.DragEvent.ACTION_DRAG_LOCATION: {
                    float zone = dp(getContext(), 96), y = e.getY();
                    if (y < panel.topInset + zone) panel.scroll.smoothScrollBy(0, -dp(getContext(), 40));
                    else if (y > getHeight() - zone) panel.scroll.smoothScrollBy(0, dp(getContext(), 40));
                    return true;
                }
                case android.view.DragEvent.ACTION_DROP: {
                    android.content.ClipData clip = e.getClipData();
                    if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getIntent() == null) return false;
                    Intent data = clip.getItemAt(0).getIntent();
                    ComponentName provider = data.getParcelableExtra("android.intent.extra.COMPONENT_NAME");
                    android.os.UserHandle user = data.getParcelableExtra(Intent.EXTRA_USER);
                    if (provider == null) return false;
                    int[] me = new int[2], g = new int[2];
                    getLocationOnScreen(me); panel.grid.getLocationOnScreen(g);
                    int index = panel.grid.indexAt(e.getX() + me[0] - g[0], e.getY() + me[1] - g[1]);
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    addProvider((Activity) panel.activity, provider, user, index);
                    return true;
                }
                default:
                    return true;
            }
        }

        float closing(float dx) { return panel.right() ? dx : -dx; }

        boolean barTouch, barDone, denied; float barY;
        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) denied = false;
            if (!denied && !tracking && (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_UP)
                    && (panel.wiggle || panel.picker != null) && !panel.editAllowed()) {
                denied = true;
                if (a != MotionEvent.ACTION_DOWN) {
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
            }
            if (denied) {
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) denied = false;
                return true;
            }
            try {

                if (a == MotionEvent.ACTION_DOWN && sLock && panel.swinging() && panel.swing.target > 0f) { return false; }

                if (a == MotionEvent.ACTION_DOWN && !sLock && panel.settle != null && panel.settle.isRunning() && panel.settleTarget <= 0f && !panel.pulling) { return false; }
                if (a == MotionEvent.ACTION_DOWN) {
                    int zone = Math.max(panel.bottomInset, WidgetPanel.dp(getContext(), 24)) + WidgetPanel.dp(getContext(), 8);

                    boolean open = sLock ? panel.hub >= 1f : panel.progress >= 1f;
                    barTouch = open && ev.getY() > getHeight() - zone; barDone = false; barY = ev.getY();
                    if (barTouch) return true;
                } else if (barTouch) {
                    if (barDone) { if (a != MotionEvent.ACTION_MOVE) barTouch = false; return true; }
                    float up = barY - ev.getY();
                    if (a == MotionEvent.ACTION_MOVE) { if (up > WidgetPanel.dp(getContext(), 40)) { barDone = true; panel.home(); } }
                    else if (a == MotionEvent.ACTION_CANCEL || a == MotionEvent.ACTION_UP) {
                        if (up > WidgetPanel.dp(getContext(), 12) || (a == MotionEvent.ACTION_CANCEL && up > 0)) { barDone = true; panel.home(); }
                        barTouch = false;
                    }
                    return true;
                }
            } catch (Throwable ignored) { }

            try {
                {
                    int act = ev.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) { gx0 = ev.getRawX(); gy0 = ev.getRawY(); }
                    else if (act == MotionEvent.ACTION_MOVE && !tracking && !barTouch && ev.getPointerCount() == 1 && (sLock ? panel.progress >= 1f : panel.progress > 0.5f) && !panel.wiggle
                            && !(panel.grid != null && panel.grid.busy()) && !(panel.menu != null && panel.menu.open)) {
                        float gdx = ev.getRawX() - gx0, gdy = ev.getRawY() - gy0;
                        if (closing(gdx) > WidgetPanel.dp(getContext(), 22) && Math.abs(gdx) > Math.abs(gdy) * 1.3f) {
                            MotionEvent cancel = MotionEvent.obtain(ev);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancel);
                            cancel.recycle();
                            tracking = true; catching = false;
                            x0 = gx0 + (panel.right() ? 1 : -1) * slop;
                            startRaw = from();
                            WidgetPanel.fast(this, true); panel.layer(true);
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) { }
            return super.dispatchTouchEvent(ev);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (panel.grid != null && panel.grid.busy()) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = ev.getRawX(); y0 = ev.getRawY(); tracking = false; startRaw = 0f;
                    downIn = panel.progress >= 1f && !panel.swinging();
                    if (velocity != null) velocity.recycle();
                    velocity = VelocityTracker.obtain(); track(ev);

                    catching = panel.swinging() || (!sLock && panel.settle != null && panel.settle.isRunning() && panel.settleTarget > 0f);
                    break;
                case MotionEvent.ACTION_MOVE:
                    track(ev);
                    float dx = ev.getRawX() - x0, dy = ev.getRawY() - y0;
                    if (catching && !tracking) {
                        boolean settling = !sLock && panel.settle != null && panel.settle.isRunning();
                        if (!panel.swinging() && !settling) catching = false;
                        else if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                            catching = false;
                            if (panel.swinging()) { startRaw = panel.swing.raw(); panel.swing.stop(false); }
                            else { panel.settle.cancel(); startRaw = from(); }
                            tracking = true; x0 = ev.getRawX();
                            WidgetPanel.fast(this, true); panel.layer(true);
                            return true;
                        } else if (Math.abs(dy) > slop) catching = false;
                    }
                    if (!tracking && starts(dx, dy)) { tracking = true; x0 = ev.getRawX(); startRaw = from(); if (!sLock && panel.settle != null) panel.settle.cancel(); WidgetPanel.fast(this, true); panel.layer(true); }
                    break;
            }
            return tracking;
        }

        boolean catching;
        float gx0, gy0;

        float startRaw;
        boolean downIn;

        float from() { return sLock ? 0f : Math.max(0f, 1f - panel.progress) * Math.max(1, getWidth()); }

        boolean starts(float dx, float dy) {
            if (Math.abs(dx) <= Math.abs(dy) * 1.4f) return false;
            if (closing(dx) > slop) return true;

            return downIn && panel.progress >= 1f && -closing(dx) > slop * 2f && Math.abs(dx) > Math.abs(dy) * 2f;
        }

        void track(MotionEvent ev) {
            if (velocity == null) velocity = VelocityTracker.obtain();
            MotionEvent copy = MotionEvent.obtain(ev);
            copy.setLocation(ev.getRawX(), ev.getRawY());
            velocity.addMovement(copy);
            copy.recycle();
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            track(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: x0 = ev.getRawX(); y0 = ev.getRawY(); return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getRawX() - x0, dy = ev.getRawY() - y0;
                    if (!tracking && starts(dx, dy)) { tracking = true; x0 = ev.getRawX(); startRaw = from(); panel.layer(true); }
                    if (tracking) {
                        if (panel.settle != null) panel.settle.cancel();
                        if (sLock) panel.swing().drag(startRaw + closing(ev.getRawX() - x0));
                        else {
                            float raw = startRaw + closing(ev.getRawX() - x0);
                            if (raw < 0f || (panel.swing != null && panel.swing.pos != 0f)) panel.swing().drag(Math.min(0f, raw));
                            panel.setProgress(1f - Math.max(0f, raw) / Math.max(1, getWidth()));
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (tracking) {
                        velocity.computeCurrentVelocity(1000);
                        float v = closing(velocity.getXVelocity());
                        float moved = sLock ? 1f - panel.own : 1f - panel.progress;
                        boolean away = v > WidgetPanel.dp(getContext(), 250) || (v > -WidgetPanel.dp(getContext(), 250) && (sLock ? moved > 0.35f : panel.progress < 0.65f));
                        if (sLock) {
                            if (away && !lockOther(getContext())) Lock.close();
                            panel.swing().release(v, away);
                        } else if (panel.swing != null && panel.swing.pos < 0f) panel.swing().release(v, false);
                        else panel.animateTo(away ? 0f : 1f, 0f);
                    }
                    tracking = false; downIn = false;
                    return true;
            }
            return true;
        }
    }

    static final class Lock {
        static Object controller, starter;
        static ViewGroup wrapper;
        static View stock, shade;
        static boolean receiving, raised;
        static android.view.ViewTreeObserver.OnPreDrawListener watcher;
        static Class<?> idle, change;
        static Object communal, blank;
        static float stockAlpha = -1f;
        static java.lang.reflect.Field source, fromScene, toScene;
        static Method stateOf, sceneOf, progressOf;

        static void install(final ClassLoader cl) throws Throwable {
            sLock = true;
            try { Weather.install(); } catch (Throwable t) { Log.w("lock weather", t); }
            Class<?> type = cl.loadClass("com.android.systemui.shade.GlanceableHubContainerController");
            Set<String> names = new HashSet<String>();
            for (Method m : type.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().startsWith("initView") && p.length == 1 && p[0] == View.class) names.add(m.getName());
            }
            if (names.isEmpty()) throw new NoSuchMethodException("initView(View)");
            for (String name : names) {
                Xp.hookAllMethods(type, name, new Xp.Callback() {
                    @Override protected void afterHookedMethod(final Xp.Param p) {
                        final Object made = p.getResult(), self = p.thisObject;
                        if (made instanceof ViewGroup) MAIN.post(() -> { try { attach(cl, self, (ViewGroup) made); } catch (Throwable t) { Log.w("lock attach", t); } });
                    }
                });
            }
            try {
                Xp.hookAllConstructors(cl.loadClass("com.android.systemui.communal.widgets.WidgetInteractionHandler"), new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try { Object s = Xp.getObjectField(p.thisObject, "activityStarter"); if (s != null) starter = s; } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable t) { Log.w("lock starter", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.ambient.touch.ShadeTouchHandler"), "getTouchInitiationRegion", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        Panel panel = sPanel;
                        if (panel == null || panel.progress <= 0f || p.args.length < 2) return;
                        if (!(p.args[0] instanceof android.graphics.Rect) || !(p.args[1] instanceof android.graphics.Region)) return;
                        android.graphics.Rect bounds = (android.graphics.Rect) p.args[0];
                        int bar = 0;
                        try { int id = android.content.res.Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android"); if (id != 0) bar = android.content.res.Resources.getSystem().getDimensionPixelSize(id); } catch (Throwable ignored) { }
                        if (bar <= 0) bar = dp(panel.activity, 28);
                        ((android.graphics.Region) p.args[1]).op(new android.graphics.Rect(bounds.left, bounds.top, bounds.right, bounds.top + bar), android.graphics.Region.Op.UNION);
                        p.setResult(null);
                    }
                });
            } catch (Throwable t) { Log.w("lock shade", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.ambient.touch.BouncerSwipeTouchHandler"), "getTouchInitiationRegion", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        Panel panel = sPanel;
                        if (panel != null && (panel.wiggle ? panel.hub > 0f : panel.hub >= 1f)) p.setResult(null);
                    }
                });
            } catch (Throwable t) { Log.w("lock edit swipe", t); }
            try {
                final Class<?> bar = cl.loadClass("com.android.systemui.statusbar.phone.KeyguardStatusBarView");
                Xp.hookAllMethods(bar, "onThemeChanged", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { if (p.args.length == 1) { barView = p.thisObject; barIcons = p.args[0]; } barTheming = true; }
                    @Override protected void afterHookedMethod(Xp.Param p) { barTheming = false; }
                });
                Xp.hookAllMethods(cl.loadClass("com.android.settingslib.Utils"), "getColorAttrDefaultColor", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { if (barTheming && barDark) p.setResult(Integer.valueOf(Color.BLACK)); }
                });
            } catch (Throwable t) { Log.w("lock status bar colours", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.classifier.BrightLineFalsingManager"), "isFalseTouch", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        if (p.args.length != 1 || !Integer.valueOf(20).equals(p.args[0]) || sPanel == null) return;
                        Object said = p.getResult();
                        if (Boolean.TRUE.equals(said)) p.setResult(Boolean.FALSE);
                    }
                });
            } catch (Throwable t) { Log.w("lock swipe false touch", t); }
            try {
                Xp.hookAllMethods(type, "onTouchEvent", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (feeding) return;
                        if (p.args.length != 1 || !(p.args[0] instanceof MotionEvent) || sPanel == null) return;
                        MotionEvent e = (MotionEvent) p.args[0];
                        int seen = e.getActionMasked();
                        if (seen == MotionEvent.ACTION_DOWN) sPanel.hubFlick = -1f;
                        else if (seen == MotionEvent.ACTION_UP || seen == MotionEvent.ACTION_CANCEL) sPanel.hubReleased();

                        if (drive(p, e)) { curved(p.thisObject, e); return; }
                        hubTouch = true; hubDown = e.getActionMasked() == MotionEvent.ACTION_DOWN;
                        hubX = Math.round(e.getRawX()); hubY = Math.round(e.getRawY());
                        curved(p.thisObject, e);

                        int act = e.getActionMasked();
                        if (act == MotionEvent.ACTION_DOWN) {
                            View host = wrapper;

                            boolean onBlank = swipeArmed;
                            try { onBlank = onBlank || (!Xp.getBooleanField(p.thisObject, "hubShowing") && (pageKind.isEmpty() || (pageKind.startsWith("idle") && pageKind.contains("blank")))); } catch (Throwable ignored) { }
                            edgeBlocked = host != null && lockOther(host.getContext()) && onBlank;
                            stockCancelled = false;
                        } else if (edgeBlocked && !driving) {
                            if (!stockCancelled) {
                                stockCancelled = true;
                                p.args[0] = made = mirrored(e, MotionEvent.ACTION_CANCEL, e.getX(), e.getY(), e.getDownTime());
                            } else p.setResult(Boolean.FALSE);
                        }
                    }
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        hubTouch = false; hubDown = false;
                        if (feeding || p.args.length != 1 || !(p.args[0] instanceof MotionEvent)) return;
                        MotionEvent e = (MotionEvent) p.args[0];
                        if (made != null && e == made) { made.recycle(); made = null; }
                    }
                });
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.util.ConvenienceExtensionsKt"), "getBoundsOnScreen", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        if (!hubTouch || p.args.length != 1 || !(p.args[0] instanceof View) || !(p.getResult() instanceof Rect)) return;
                        if (area((View) p.args[0], (Rect) p.getResult(), "media player")) p.setResult(new Rect());
                    }
                });
                Xp.hookAllMethods(View.class, "getBoundsOnScreen", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        if (!hubTouch || p.args.length < 1 || !(p.args[0] instanceof Rect) || !(p.thisObject instanceof View)) return;
                        if (area((View) p.thisObject, (Rect) p.args[0], "At a Glance")) ((Rect) p.args[0]).setEmpty();
                    }
                });
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout"), "isBelowLastNotification", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        if (hubTouch && hubDown && Boolean.FALSE.equals(p.getResult())) { swipeBlocked = true; }
                    }
                });
            } catch (Throwable t) { Log.w("lock touch areas", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.wm.shell.startingsurface.StartingWindowController$StartingSurfaceImpl"), "getBackgroundColor", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try {
                            if (!sSessionOn || p.args.length < 1 || p.args[0] == null) return;
                            for (String field : new String[]{"topActivity", "baseActivity"}) {
                                Object n = Xp.getObjectField(p.args[0], field);
                                if (n instanceof ComponentName && LOCK_PICKER.equals(((ComponentName) n).getClassName())) { p.setResult(Color.BLACK); return; }
                            }
                            Object base = Xp.getObjectField(p.args[0], "baseIntent");
                            if (base instanceof Intent && ((Intent) base).getComponent() != null && LOCK_PICKER.equals(((Intent) base).getComponent().getClassName())) p.setResult(Color.BLACK);
                        } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable t) { Log.w("lock picker backdrop", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl"), "notifyKeyguardState", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        if (p.args.length >= 2 && p.args[0] instanceof Boolean && p.args[1] instanceof Boolean) occluded = (Boolean) p.args[0] && (Boolean) p.args[1];
                        if (!occluded && shadeGone) MAIN.post(SHADE_BACK);
                    }
                });
            } catch (Throwable t) { Log.w("lock occluded", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.statusbar.phone.ScrimController"), "setOccludeAnimationPlaying", new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        if (!picking || p.args.length != 1 || !(p.args[0] instanceof Boolean)) return;
                        Runnable r = () -> {
                            if (!picking) return;
                            MAIN.removeCallbacks(SHADE_OUT);

                            shadeOut();
                        };
                        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) r.run(); else MAIN.post(r);
                    }
                });
            } catch (Throwable t) { Log.w("lock picker covered animation", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.statusbar.BlurUtils"), "applyBlur", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (p.args.length != 3 || !(p.args[1] instanceof Integer) || !(p.args[2] instanceof Float)) return;
                        blurUtils = p.thisObject;
                        if (blurOurs) return;
                        blurRadius = (Integer) p.args[1]; blurScale = (Float) p.args[2]; blurKnown = true;
                        if (shadeGone) { p.args[1] = Integer.valueOf(0); p.args[2] = Float.valueOf(1f); }
                    }
                });
            } catch (Throwable t) { Log.w("lock picker blur hook", t); }
            try {
                Xp.hookAllMethods(cl.loadClass("com.android.systemui.navigationbar.gestural.BackPanelController"), "onMotionEvent", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (p.args.length != 1 || !(p.args[0] instanceof MotionEvent)) return;
                        MotionEvent e = (MotionEvent) p.args[0];
                        int a = e.getActionMasked();
                        if (a == MotionEvent.ACTION_DOWN) { backArmed = backArm(); backOff = false; return; }
                        if (backOff) { p.setResult(null); return; }
                        Panel panel = sPanel;
                        if (!backArmed || panel == null || panel.hub < 0.02f) return;
                        backOff = true; backArmed = false;
                        if (a == MotionEvent.ACTION_CANCEL) return;
                        backCancel = MotionEvent.obtain(e);
                        backCancel.setAction(MotionEvent.ACTION_CANCEL);
                        p.args[0] = backCancel;
                    }
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        MotionEvent c = backCancel;
                        if (c != null) { backCancel = null; c.recycle(); }
                    }
                });
            } catch (Throwable t) { Log.w("lock back arrow", t); }
        }

        static volatile boolean occluded;
        static Object barView, barIcons; static boolean barTheming, barDark;
        static boolean hubTouch, hubDown; static int hubX, hubY;

        static boolean swipeArmed, swipeBlocked, swipeTaken, edgeBlocked, stockCancelled; static float swipeX, swipeY; static long swipeAt;

        static boolean feeding, driving, driveArmed; static float driveX0, driveY0, driveEdge; static long driveDown;
        static MotionEvent made;

        static boolean drive(Xp.Param p, MotionEvent e) {
            View host = wrapper;
            if (host == null || !lockOther(host.getContext())) { driving = false; driveArmed = false; return false; }
            Context c = host.getContext();
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                driving = false; driveArmed = true;
                driveX0 = e.getX(); driveY0 = e.getY();
                return false;
            }
            if (!driving) {
                if (!driveArmed || a != MotionEvent.ACTION_MOVE) return false;

                if (e.getPointerCount() > 1 || !swipeArmed || swipeBlocked || swipeTaken || sPanel == null || sPanel.hub > 0f || shadeExpansion() > 0.03f) {
                    driveArmed = false; return false;
                }
                float dx = e.getX() - driveX0, dy = e.getY() - driveY0;
                float slop = dp(c, 10);
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) { driveArmed = false; return false; }
                float towards = lockRight(c) ? -dx : dx;
                if (towards < -slop) { driveArmed = false; return false; }
                if (towards < slop || Math.abs(dx) < 1.5f * Math.abs(dy)) return false;
                View root = host.getRootView();
                float width = root != null && root.getWidth() > 0 ? root.getWidth() : host.getResources().getDisplayMetrics().widthPixels;
                driveEdge = rtlOf(c) ? dp(c, 3) : width - dp(c, 3);
                driveDown = e.getEventTime();
                driving = true; driveArmed = false;
                swipeArmed = false;

                feed(p.thisObject, e, MotionEvent.ACTION_CANCEL, e.getX(), e.getY(), e.getDownTime());
                feed(p.thisObject, e, MotionEvent.ACTION_DOWN, driveEdge, driveY0, driveDown);
                p.args[0] = made = mirrored(e, MotionEvent.ACTION_MOVE, driveEdge - dx, e.getY(), driveDown);
                handing(made);
                return true;
            }
            int act = a == MotionEvent.ACTION_UP ? MotionEvent.ACTION_UP : a == MotionEvent.ACTION_CANCEL || e.getPointerCount() > 1 ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_MOVE;
            p.args[0] = made = mirrored(e, act, driveEdge - (e.getX() - driveX0), e.getY(), driveDown);
            handing(made);
            if (act != MotionEvent.ACTION_MOVE) driving = false;
            return true;
        }

        static void handing(MotionEvent s) { hubTouch = true; hubDown = false; hubX = Math.round(s.getX()); hubY = Math.round(s.getY()); }

        static MotionEvent mirrored(MotionEvent like, int action, float x, float y, long downTime) {
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = 0; pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.x = x; pc.y = y; pc.pressure = 1f; pc.size = like.getSize();
            return MotionEvent.obtain(downTime, like.getEventTime(), action, 1, new MotionEvent.PointerProperties[]{pp}, new MotionEvent.PointerCoords[]{pc},
                    like.getMetaState(), 0, 1f, 1f, like.getDeviceId(), 0, like.getSource(), 0);
        }

        static void feed(Object hub, MotionEvent like, int action, float x, float y, long downTime) {
            MotionEvent s = mirrored(like, action, x, y, downTime);
            feeding = true;
            handing(s);
            try { Xp.callMethod(hub, "onTouchEvent", s); } catch (Throwable t) { Log.w("lock swipe feed", t); }
            finally { feeding = false; hubTouch = false; }
            s.recycle();
        }
        static void curved(Object hub, MotionEvent e) {
            try {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    swipeBlocked = false; swipeTaken = false;
                    swipeX = e.getRawX(); swipeY = e.getRawY(); swipeAt = e.getEventTime();
                    swipeArmed = Xp.getBooleanField(hub, "onLockscreen") && !Xp.getBooleanField(hub, "hubShowing")
                            && !Xp.getBooleanField(hub, "anyBouncerShowing") && !Xp.getBooleanField(hub, "shadeShowing");
                    return;
                }
                if (!swipeArmed) return;
                if (e.getPointerCount() > 1) { swipeArmed = false; return; }
                if (pageKind.startsWith("move")) swipeTaken = true;
                if (a != MotionEvent.ACTION_UP) { if (a == MotionEvent.ACTION_CANCEL) swipeArmed = false; return; }
                swipeArmed = false;
                Panel panel = sPanel;
                if (panel == null || wrapper == null || swipeBlocked || swipeTaken || panel.hub > 0f || shadeExpansion() > 0.03f) return;
                float dx = e.getRawX() - swipeX, dy = e.getRawY() - swipeY;
                long took = e.getEventTime() - swipeAt;

                float towards = lockRight(wrapper.getContext()) ? -dx : dx;
                boolean in = towards >= dp(wrapper.getContext(), 80) && Math.abs(dx) >= 1.5f * Math.abs(dy) && took <= 800;
                if (in) openHub();
            } catch (Throwable t) { Log.w("lock curved swipe", t); }
        }

        static boolean area(View v, Rect bounds, String what) {
            try {
                if (bounds == null || !bounds.contains(hubX, hubY)) return false;
                float alpha = 1f;
                for (Object o = v; o instanceof View; o = ((View) o).getParent()) alpha *= ((View) o).getAlpha();
                boolean seen = v.isAttachedToWindow() && v.isShown() && v.getWidth() > 0 && v.getHeight() > 0 && alpha > 0.05f;

                boolean counts = seen && !"At a Glance".equals(what);
                if (hubDown && counts) swipeBlocked = true;
                return !counts;
            } catch (Throwable t) { return false; }
        }

        static void statusBar(Panel p) {
            try {
                boolean dark = p.hub > 0.5f && !heldOut && p.look != null && p.look.opacity >= 20 && p.look.lightBackground();
                if (dark == barDark || barView == null || barIcons == null) return;
                barDark = dark;
                for (Method m : barView.getClass().getMethods()) {
                    if (m.getName().equals("onThemeChanged") && m.getParameterTypes().length == 1) { m.invoke(barView, barIcons); break; }
                }
            } catch (Throwable t) { Log.w("lock status bar", t); }
        }
        static boolean backArmed, backOff;
        static MotionEvent backCancel;

        static boolean backArm() {
            try {
                Panel panel = sPanel; ViewGroup w = wrapper;
                if (panel == null || w == null || occluded || panel.hub >= 1f) return false;
                android.app.KeyguardManager km = w.getContext().getSystemService(android.app.KeyguardManager.class);
                return km != null && km.isKeyguardLocked();
            } catch (Throwable t) { return false; }
        }

        static final List<ContentObserver> lockObservers = new ArrayList<ContentObserver>();

        static void attach(ClassLoader cl, Object owner, ViewGroup frame) throws Throwable {
            if (wrapper == frame) return;
            if (sPanel != null) { Panel old = sPanel; sPanel = null; if (old.root.getParent() instanceof ViewGroup) ((ViewGroup) old.root.getParent()).removeView(old.root); }
            if (watcher != null && wrapper != null) { try { wrapper.getViewTreeObserver().removeOnPreDrawListener(watcher); } catch (Throwable ignored) { } watcher = null; }
            controller = owner; wrapper = frame; stock = null; stockAlpha = -1f; shade = null; raised = false; source = null;
            try { if (veilView != null && veilView.getParent() instanceof ViewGroup) ((ViewGroup) veilView.getParent()).removeView(veilView); } catch (Throwable ignored) { }
            MAIN.removeCallbacks(SHADE_OUT); MAIN.removeCallbacks(SHADE_BACK); picking = false; shadeGone = false;
            pinView = null; pinUp = false; pinEntry = false; tapsBusy = false; pinShown = 1f;
            try { Settings.Secure.putInt(frame.getContext().getContentResolver(), LOCK_PIN_KEY, 0); } catch (Throwable ignored) { }
            shadeBack = null; veilView = null; veilOn = false; shadeFlow = null; pickerScrims(false);
            Class<?> scenes = cl.loadClass("com.android.systemui.communal.shared.model.CommunalScenes");
            communal = scenes.getField("Communal").get(null);
            blank = scenes.getField("Blank").get(null);
            idle = cl.loadClass("com.android.compose.animation.scene.content.state.TransitionState$Idle");
            change = cl.loadClass("com.android.compose.animation.scene.content.state.TransitionState$Transition$ChangeScene");
            final Context c = frame.getContext();
            for (ContentObserver o : lockObservers) try { c.getContentResolver().unregisterContentObserver(o); } catch (Throwable ignored) { }
            lockObservers.clear();
            ContentObserver observer = new ContentObserver(MAIN) {
                @Override public void onChange(boolean self) { try { apply(true); } catch (Throwable t) { Log.w("lock apply", t); } }
            };
            ContentObserver patron = new ContentObserver(MAIN) {
                @Override public void onChange(boolean self) { MAIN.postDelayed(() -> observer.onChange(self), 3100); }
            };
            for (String key : new String[]{LOCK_KEY, LOCK_HEADER_KEY, LOCK_OPACITY_KEY, LOCK_OPEN_KEY, LOCK_SIDE_KEY, SurfaceStyle.KEY, SuperDark.KEY})
                c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(PatronGate.KEY), false, patron);
            lockObservers.add(observer); lockObservers.add(patron);
            endSession(c, true);
            if (sSetupId < 0) unwatch(c);
            ContentObserver setupResults = new ContentObserver(MAIN) {
                @Override public void onChange(boolean self) { setupResult(c); }
            };
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(LOCK_CONFIG_RESULT_KEY), false, setupResults);
            lockObservers.add(setupResults);
            frame.getViewTreeObserver().addOnWindowFocusChangeListener(has -> {
                if (!has && sSetupLaunched && sSetupId >= 0) sSetupShown = true;
                if (has && sSetupShown && sSetupId >= 0) { MAIN.removeCallbacks(SETUP_GRACE); MAIN.postDelayed(SETUP_GRACE, 1500); }

                if (has && sSessionOn && android.os.SystemClock.uptimeMillis() - sSessionAt > 1000) endSession(c, false);
            });
            if (!receiving) {
                android.content.BroadcastReceiver off = new android.content.BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) { endSession(c, false); }
                };
                c.getApplicationContext().registerReceiver(off, new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), null, MAIN);
                receiving = true;
                android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        try {
                            ComponentName provider = intent.getParcelableExtra("provider");
                            android.os.UserHandle user = intent.getParcelableExtra("user");
                            if (provider != null) add(provider, user);
                        } catch (Throwable t) { Log.w("lock add", t); }
                    }
                };

                Context.class.getMethod("registerReceiver", android.content.BroadcastReceiver.class, android.content.IntentFilter.class, String.class, Handler.class, int.class)
                        .invoke(c.getApplicationContext(), receiver, new android.content.IntentFilter(LOCK_ADD), BIND, MAIN, 2 );
            }
            apply(false);
        }

        static void apply(boolean changed) throws Throwable {
            if (wrapper == null) return;
            Context c = wrapper.getContext();
            boolean on = lockEnabled(c);
            if (on && sPanel == null) {
                sPanel = new Panel(c, wrapper);
                if (watcher == null) {
                    watcher = () -> { try { follow(); } catch (Throwable t) { Log.w("lock follow", t); } return true; };
                    wrapper.getViewTreeObserver().addOnPreDrawListener(watcher);
                }
                if (changed) {
                    try { if (Settings.Secure.getInt(c.getContentResolver(), "glanceable_hub_enabled", 0) != 1) Settings.Secure.putInt(c.getContentResolver(), "glanceable_hub_enabled", 1); } catch (Throwable t) { Log.w("lock hub setting", t); }
                }
                follow();
            } else if (!on && sPanel != null) {
                endSession(c, false);
                Panel old = sPanel; sPanel = null;
                try { old.hub = 0f; old.lockApply(); } catch (Throwable ignored) { }
                if (old.root.getParent() instanceof ViewGroup) ((ViewGroup) old.root.getParent()).removeView(old.root);
                raised(false);
                drawn(true);
            } else if (on && changed) sPanel.settingsChanged();
        }

        static void drawn(boolean yes) {
            if (wrapper == null) return;
            if (stock == null || stock.getParent() != wrapper) {
                stock = null; stockAlpha = -1f;
                for (int i = 0; i < wrapper.getChildCount(); i++) { View v = wrapper.getChildAt(i); if (!(v instanceof Root) && v != shadeBack && v != veilView) { stock = v; break; } }
            }
            if (stock == null) return;
            float want = yes ? 1f : 0f;
            if (stockAlpha == want) return;
            try { View.class.getMethod("setTransitionAlpha", float.class).invoke(stock, want); stockAlpha = want; } catch (Throwable ignored) { }
        }

        static boolean pinWarned, pinUp, pinEntry, tapsBusy, wasLit;
        static View pinView;
        static android.animation.ValueAnimator pinAnim;
        static float pinShown = 1f;

        static void pinFade(final Panel p, boolean up) {
            try {
                if (pinAnim != null) pinAnim.cancel();
                final Method alpha = View.class.getMethod("setTransitionAlpha", float.class);

                if (up) { pinAnim = null; pinShown = 0f; alpha.invoke(p.root, 0f); return; }
                pinAnim = android.animation.ValueAnimator.ofFloat(pinShown, 1f);
                pinAnim.setDuration(160);
                pinAnim.addUpdateListener(a -> { pinShown = (Float) a.getAnimatedValue(); try { alpha.invoke(p.root, pinShown); } catch (Throwable ignored) { } });
                pinAnim.start();
            } catch (Throwable t) { Log.w("lock pin fade", t); }
        }

        static void raised(boolean yes) {
            if (wrapper == null || raised == yes) return;
            try {
                if (shade == null) {
                    View top = wrapper.getRootView();
                    int id = wrapper.getResources().getIdentifier("notification_panel", "id", wrapper.getContext().getPackageName());
                    shade = id != 0 ? top.findViewById(id) : null;
                }
                if (shade == null || shade.getParent() != wrapper.getParent()) return;
                raised = yes;
                shade.setTranslationZ(yes ? 1f : 0f);

                try {
                    int bid = wrapper.getResources().getIdentifier("keyguard_bouncer_container", "id", wrapper.getContext().getPackageName());
                    View pin = bid != 0 ? wrapper.getRootView().findViewById(bid) : null;
                    if (pin != null && pin.getParent() == wrapper.getParent()) pin.setTranslationZ(yes ? 2f : 0f);
                    else if (yes && !pinWarned) { pinWarned = true; android.util.Log.w("PixelSuite", "widget panel: PIN screen's container not found beside the shade (" + pin + ")"); }
                } catch (Throwable ignored) { }
            } catch (Throwable ignored) { }
        }

        static void follow() throws Throwable {
            Panel p = sPanel;
            if (p == null || wrapper == null) return;
            if (!PatronGate.ok(p.activity)) { apply(false); return; }
            drawn(false);

            try {
                boolean up = controller != null && Xp.getBooleanField(controller, "anyBouncerShowing");
                if (up != pinUp) { pinUp = up; raised(p.progress > 0f && !up); }

                boolean pin = false;
                if (up) {
                    if (pinView == null) { int bid = wrapper.getResources().getIdentifier("keyguard_bouncer_container", "id", wrapper.getContext().getPackageName()); pinView = bid != 0 ? wrapper.getRootView().findViewById(bid) : null; }

                    pin = pinView != null && pinView.isAttachedToWindow() && pinView.getVisibility() == View.VISIBLE;
                    if (pinView instanceof ViewGroup && pinView.isAttachedToWindow()) {
                        ViewGroup g = (ViewGroup) pinView;
                        pin = false;
                        for (int i = 0; i < g.getChildCount(); i++) { View ch = g.getChildAt(i); if (ch.getVisibility() == View.VISIBLE && ch.getAlpha() > 0.01f) { pin = true; break; } }
                    }
                }
                if (pin != pinEntry) {
                    pinEntry = pin;
                    pinFade(p, pin);
                }

                boolean busy = pin || p.progress > 0f;

                android.view.Display display = wrapper.getDisplay();
                boolean lit = display != null && display.getState() == android.view.Display.STATE_ON;
                if (lit && !wasLit && tapsBusy && busy) {
                    try { android.content.ContentResolver cr = wrapper.getContext().getContentResolver(); Settings.Secure.putInt(cr, LOCK_PIN_KEY, 0); Settings.Secure.putInt(cr, LOCK_PIN_KEY, 1); } catch (Throwable t) { Log.w("lock pin flag", t); }
                }
                wasLit = lit;
                if (busy != tapsBusy) {
                    tapsBusy = busy;
                    try { Settings.Secure.putInt(wrapper.getContext().getContentResolver(), LOCK_PIN_KEY, busy ? 1 : 0); } catch (Throwable t) { Log.w("lock pin flag", t); }
                }
            } catch (Throwable ignored) { }
            float now = progress();
            if (veilOn && !sSessionOn) veil(false);

            if (covered() && (heldOut || now < p.hub)) { heldOut = true; now = 0f; } else heldOut = false;
            if (now != p.hub) p.lockHub(now);
            statusBar(p);

            float e = shadeExpansion();

            if (e < 0.03f) e = 0f;

            if (e <= 0f) { shadeBlocked = false; shadeStarted = false; }
            else if (!shadeStarted) { shadeStarted = true; shadeBlocked = p.hub < 1f; }
            boolean ours = e > 0f && !shadeBlocked;
            if (!ours) e = 0f;

            if (e > 0.9f && now > 0.02f && now < 0.98f && !shadeFailed) { shadeFailed = true; e = 0f; android.util.Log.w("PixelSuite", "widget panel: shade expansion read high during the swipe in; panel fade off"); }

            backdrop(p, e <= 0f ? 0f : Math.min(1f, e * 2f));
        }

        static View shadeBack;
        static void backdrop(Panel p, float alpha) {
            try {
                if (alpha <= 0f) { if (shadeBack != null && shadeBack.getVisibility() != View.GONE) shadeBack.setVisibility(View.GONE); return; }
                if (shadeBack == null || shadeBack.getParent() != wrapper) {
                    shadeBack = new View(wrapper.getContext());

                    shadeBack.setVisibility(View.GONE);
                    wrapper.addView(shadeBack, new FrameLayout.LayoutParams(-1, -1));
                }
                if (shadeBack.getVisibility() != View.VISIBLE) {
                    boolean night = (wrapper.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                    shadeBack.setBackgroundColor(night ? Color.BLACK : 0xffbcbcc2);
                    shadeBack.bringToFront();
                    shadeBack.setVisibility(View.VISIBLE);
                }
                shadeBack.setAlpha(alpha);
            } catch (Throwable t) { Log.w("lock shade backdrop", t); }
        }

        static Object shadeFlow; static Method shadeValue; static boolean shadeFailed, shadeBlocked, shadeStarted;

        static float shadeExpansion() {
            if (shadeFailed || controller == null) return 0f;
            try {
                if (shadeFlow == null) {
                    Object shade = Xp.getObjectField(controller, "shadeInteractor");
                    Method m = shade.getClass().getMethod("getAnyExpansion"); m.setAccessible(true);
                    shadeFlow = m.invoke(shade);
                    shadeValue = shadeFlow.getClass().getMethod("getValue"); shadeValue.setAccessible(true);
                }
                Object v = shadeValue.invoke(shadeFlow);
                return v instanceof Float ? Math.max(0f, Math.min(1f, (Float) v)) : 0f;
            } catch (Throwable t) { shadeFailed = true; Log.w("lock shade expansion", t); return 0f; }
        }

        static boolean heldOut;

        static boolean covered() {
            if (!sSessionOn && sSetupId < 0) return false;
            return occluded || (wrapper != null && !wrapper.hasWindowFocus());
        }

        static float progress() throws Throwable {
            if (source == null) {
                source = controller.getClass().getDeclaredField("dataSourceDelegator"); source.setAccessible(true);
                stateOf = source.getType().getMethod("getTransitionState");
                sceneOf = stateOf.getReturnType().getMethod("getCurrentScene");
                progressOf = change.getMethod("getProgress");
                fromScene = change.getDeclaredField("fromScene"); fromScene.setAccessible(true);
                toScene = change.getDeclaredField("toScene"); toScene.setAccessible(true);
            }
            Object state = stateOf.invoke(source.get(controller));
            if (state == null) { page("none", 0f); return 0f; }
            if (change.isInstance(state)) {
                float done = ((Number) progressOf.invoke(state)).floatValue();
                page("move " + fromScene.get(state) + ">" + toScene.get(state) + " " + state.getClass().getSimpleName(), done);
                if (Float.isNaN(done)) done = 0f;
                done = Math.max(0f, Math.min(1f, done));
                if (communal.equals(toScene.get(state))) return done;
                if (communal.equals(fromScene.get(state))) return 1f - done;
                return 0f;
            }
            page("idle " + sceneOf.invoke(state), -1f);
            return communal.equals(sceneOf.invoke(state)) ? 1f : 0f;
        }

        static String pageKind = "";
        static void page(String kind, float done) {
            if (!kind.equals(pageKind)) pageKind = kind;
        }

        static void close() {
            try {
                Object scenes = Xp.getObjectField(controller, "communalSceneInteractor");
                for (Method m : scenes.getClass().getMethods()) {
                    Class<?>[] t = m.getParameterTypes();
                    if (m.getName().equals("changeScene") && t.length == 4 && t[1] == String.class) { m.invoke(scenes, blank, "pixelsuite widget panel", null, null); return; }
                }
                Object source = Xp.getObjectField(controller, "dataSourceDelegator");
                for (Method m : source.getClass().getMethods()) {
                    if (m.getName().equals("changeScene") && m.getParameterTypes().length == 2) { m.invoke(source, blank, null); return; }
                }
            } catch (Throwable t) { Log.w("lock close", t); }
        }

        static Object starter(ClassLoader cl) {
            if (starter != null) return starter;
            try {
                Class<?> type = cl.loadClass("com.android.systemui.plugins.ActivityStarter");
                Class<?> dependency = cl.loadClass("com.android.systemui.Dependency");
                Object all = dependency.getField("sDependency").get(null);
                if (all != null) starter = Xp.callMethod(all, "getDependencyInner", type);
            } catch (Throwable t) { Log.w("lock starter lookup", t); }
            return starter;
        }

        static void start(Context c, Intent i) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            final String pkg = pkgOf(c, i);
            if (overLock(c, pkg)) {
                session(c, pkg);
                picking = true; veil(true); MAIN.removeCallbacks(SHADE_BACK); MAIN.postDelayed(SHADE_BACK, 3000);
                MAIN.postDelayed(() -> { try { c.startActivity(i); } catch (Throwable t) { Log.w("lock start over", t); shadeBack(); } }, SESSION_DELAY);
                return;
            }
            try {
                Object s = starter(c.getClassLoader());
                if (s != null) {
                    for (Method m : s.getClass().getMethods()) {
                        Class<?>[] t = m.getParameterTypes();
                        if (m.getName().equals("postStartActivityDismissingKeyguard") && t.length == 2 && t[0] == Intent.class && t[1] == int.class) { m.setAccessible(true); m.invoke(s, i, 0); return; }
                    }
                }
            } catch (Throwable t) { Log.w("lock start", t); }
            try { c.startActivity(i); } catch (Throwable t) { Log.w("lock start plain", t); }
        }

        static boolean overLock(Context c, String pkg) {
            if (pkg == null || !lockEnabled(c) || !Feature.on(c, LOCK_OPEN_KEY)) return false;
            for (String always : LOCK_ALWAYS_ASK) if (always.equals(pkg)) return false;
            try { return MiscOptions.parse(Settings.Secure.getString(c.getContentResolver(), LOCK_APPS_KEY)).contains(pkg); }
            catch (Throwable t) { return false; }
        }

        static boolean setupOverLock(Context c, String pkg) {
            if (pkg == null || !lockEnabled(c)) return false;
            for (String always : LOCK_ALWAYS_ASK) if (always.equals(pkg)) return false;
            return true;
        }

        static String pkgOf(Context c, Intent i) {
            if (i == null) return null;
            if (i.getComponent() != null) return i.getComponent().getPackageName();
            if (i.getPackage() != null) return i.getPackage();
            try {
                android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(i, 0);
                if (r != null && r.activityInfo != null) return r.activityInfo.packageName;
            } catch (Throwable ignored) { }
            return null;
        }

        static boolean sSessionOn;
        static long sSessionAt;

        static void session(Context c, String what) {
            try {
                Settings.Secure.putString(c.getContentResolver(), LOCK_SESSION_KEY, what + "|" + android.os.SystemClock.uptimeMillis());
                sSessionOn = true;
                sSessionAt = android.os.SystemClock.uptimeMillis();
            } catch (Throwable t) { Log.w("lock session", t); }
        }

        static View[] scrims; static boolean scrimsHidden;
        static void pickerScrims(boolean hide) {
            try {
                if (wrapper == null) return;
                if (scrims == null || (scrims.length > 0 && scrims[0].getRootView() != wrapper.getRootView())) {
                    List<View> found = new ArrayList<View>();
                    for (String name : new String[]{"scrim_behind", "scrim_notifications"}) {
                        int id = wrapper.getResources().getIdentifier(name, "id", wrapper.getContext().getPackageName());
                        View v = id != 0 ? wrapper.getRootView().findViewById(id) : null;
                        if (v != null) found.add(v);
                    }
                    scrims = found.toArray(new View[0]);
                }
                if (scrimsHidden == hide) return;
                scrimsHidden = hide;
                for (View v : scrims) View.class.getMethod("setTransitionAlpha", float.class).invoke(v, hide ? 0f : 1f);
            } catch (Throwable t) { Log.w("lock picker scrims", t); }
        }

        static View veilView; static boolean veilOn;
        static boolean picking, shadeGone;
        static Object blurUtils; static int blurRadius; static float blurScale = 1f; static boolean blurKnown, blurOurs;

        static void depthUpdate() {
            try {
                if (blurUtils == null || !blurKnown || wrapper == null) return;
                Object root = View.class.getMethod("getViewRootImpl").invoke(wrapper);
                if (root == null) return;
                blurOurs = true;
                try {
                    for (Method m : blurUtils.getClass().getMethods()) {
                        if (!m.getName().equals("applyBlur") || m.getParameterTypes().length != 3) continue;
                        m.invoke(blurUtils, root, Integer.valueOf(shadeGone ? 0 : blurRadius), Float.valueOf(shadeGone ? 1f : blurScale));
                        break;
                    }
                } finally { blurOurs = false; }
            } catch (Throwable t) { Log.w("lock picker blur", t); }
        }
        static final Runnable SHADE_OUT = Lock::shadeOut, SHADE_BACK = Lock::shadeBack;

        static void veil(boolean on) { veil(on, SESSION_DELAY - 20); }

        static void veil(boolean on, long fade) {
            try {
                if (wrapper == null || veilOn == on) return;
                veilOn = on;
                if (veilView == null || veilView.getParent() != wrapper) {
                    if (veilView != null && veilView.getParent() instanceof ViewGroup) ((ViewGroup) veilView.getParent()).removeView(veilView);
                    veilView = new View(wrapper.getContext());
                    veilView.setBackgroundColor(Color.BLACK);
                    veilView.setAlpha(0f);
                    veilView.setVisibility(View.GONE);
                    wrapper.addView(veilView, new FrameLayout.LayoutParams(-1, -1));
                }
                final View v = veilView;
                v.animate().cancel();
                if (on) {
                    v.bringToFront();
                    v.setVisibility(View.VISIBLE);
                    v.animate().alpha(1f).setDuration(fade).setInterpolator(new DecelerateInterpolator(1.2f)).withEndAction(null).start();
                } else {
                    v.animate().alpha(0f).setDuration(260).setInterpolator(new DecelerateInterpolator(1.2f)).withEndAction(() -> v.setVisibility(View.GONE)).start();
                }
            } catch (Throwable t) { Log.w("lock picker veil", t); }
        }

        static void veilGone() {
            try {
                if (veilView != null) { veilView.animate().cancel(); veilView.setAlpha(0f); veilView.setVisibility(View.GONE); }
                veilOn = false;
            } catch (Throwable t) { Log.w("lock picker veil gone", t); }
        }

        static void windowAlpha(float alpha) throws Throwable {
            if (wrapper != null) View.class.getMethod("setTransitionAlpha", float.class).invoke(wrapper.getRootView(), alpha);
        }

        static void shadeOut() {
            MAIN.removeCallbacks(SHADE_OUT);
            if (!picking || shadeGone) return;

            try { windowAlpha(0.004f); shadeGone = true; depthUpdate(); } catch (Throwable t) { Log.w("lock picker window out", t); }
            MAIN.removeCallbacks(SHADE_BACK);
            MAIN.postDelayed(SHADE_BACK, 1100);
        }

        static void shadeBack() {
            MAIN.removeCallbacks(SHADE_OUT); MAIN.removeCallbacks(SHADE_BACK);
            picking = false;
            veilGone();
            if (!shadeGone) return;
            shadeGone = false;
            depthUpdate();
            try { windowAlpha(1f); } catch (Throwable t) { Log.w("lock picker window back", t); }
        }

        static void endSession(Context c, boolean always) {
            veil(false);
            pickerScrims(false);
            if (!sSessionOn && !always) return;
            sSessionOn = false;
            try {
                if (always && Settings.Secure.getString(c.getContentResolver(), LOCK_SESSION_KEY) == null) return;
                Settings.Secure.putString(c.getContentResolver(), LOCK_SESSION_KEY, null);
            } catch (Throwable t) { Log.w("lock session end", t); }
        }

        static String targetOf(Context c, android.app.PendingIntent pi) {
            try {
                Object i = android.app.PendingIntent.class.getMethod("getIntent").invoke(pi);
                if (i instanceof Intent) { String p = pkgOf(c, (Intent) i); if (p != null && !"android".equals(p)) return p; }
            } catch (Throwable ignored) { }
            try { return pi.getCreatorPackage(); } catch (Throwable t) { return null; }
        }

        static void snapClose() {
            try {
                Object scenes = Xp.getObjectField(controller, "communalSceneInteractor");

                for (int pass = 0; pass < 2; pass++) {
                    for (Method m : scenes.getClass().getMethods()) {
                        boolean helper = java.lang.reflect.Modifier.isStatic(m.getModifiers());
                        if (pass == 0 ? !"snapToScene".equals(m.getName()) || helper : !"snapToScene$default".equals(m.getName()) || !helper) continue;
                        Class<?>[] t = m.getParameterTypes();
                        Object[] args = new Object[t.length];
                        boolean ok = true, scene = false;
                        int maskAt = -1;
                        if (helper) for (int i = t.length - 1; i > 0 && maskAt < 0; i--) if (t[i] == int.class) maskAt = i;
                        for (int i = 0; i < t.length && ok; i++) {
                            if (helper && i == 0) { if (t[0].isInstance(scenes)) args[0] = scenes; else ok = false; }
                            else if (!scene && t[i].isInstance(blank)) { args[i] = blank; scene = true; }
                            else if (t[i] == String.class) args[i] = "pixelsuite widget panel";
                            else if (t[i] == long.class) args[i] = 0L;
                            else if (t[i] == int.class) args[i] = i == maskAt ? 0x4 | 0x8 : 0;
                            else if (t[i] == boolean.class) args[i] = Boolean.FALSE;
                            else if (t[i].isPrimitive()) ok = false;
                            else args[i] = null;
                        }
                        if (!ok || !scene) continue;
                        m.invoke(helper ? null : scenes, args);
                        return;
                    }
                }
            } catch (Throwable t) { Log.w("lock snap", t); }
            close();
        }

        static void openHub() {
            try {
                Object scenes = Xp.getObjectField(controller, "communalSceneInteractor");
                for (Method m : scenes.getClass().getMethods()) {
                    Class<?>[] t = m.getParameterTypes();
                    if (m.getName().equals("changeScene") && t.length == 4 && t[1] == String.class) { m.invoke(scenes, communal, "pixelsuite widget panel", null, null); return; }
                }
            } catch (Throwable t) { Log.w("lock open hub", t); }
        }

        static void pick(Context c) {
            if (!lockEnabled(c)) return;
            if (sPanel == null) return;
            final Intent picker = launcherPicker(c);
            if (picker != null && picker.getComponent() != null) {
                picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                session(c, picker.getComponent().getPackageName() + "/" + picker.getComponent().getClassName());
                picking = true;
                veil(true);
                MAIN.removeCallbacks(SHADE_BACK);
                MAIN.postDelayed(SHADE_BACK, 3000);
                MAIN.postDelayed(() -> { try { c.startActivity(picker); } catch (Throwable t) { Log.w("lock picker", t); endSession(c, false); shadeBack(); if (sPanel != null) sPanel.showPicker(); } }, SESSION_DELAY);
                return;
            }
            sPanel.showPicker();
        }

        static Intent launcherPicker(Context c) {
            try {
                PackageManager pm = c.getPackageManager();
                String home = null;
                android.content.pm.ResolveInfo found = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
                if (found != null && found.activityInfo != null) home = found.activityInfo.packageName;
                for (String pkg : new String[]{home, "com.google.android.apps.nexuslauncher", "com.android.launcher3"}) {
                    if (pkg == null) continue;
                    try { pm.getActivityInfo(new ComponentName(pkg, LOCK_PICKER), 0); } catch (Throwable missing) { continue; }
                    Intent pick = new Intent(Intent.ACTION_PICK);
                    pick.setClassName(pkg, LOCK_PICKER);
                    pick.putExtra("ui_surface", "widgets_hub");
                    pick.putExtra(MARK, true);
                    pick.putExtra(LOCK_MARK, true);
                    return pick;
                }
            } catch (Throwable t) { Log.w("lock picker lookup", t); }
            return null;
        }

        static boolean add(ComponentName provider, android.os.UserHandle user) {
            if (wrapper == null || sPanel == null) return false;
            Context c = wrapper.getContext();
            if (!lockEnabled(c)) return false;
            AppWidgetHost host = host(c);
            int id = host.allocateAppWidgetId();
            boolean bound = false;
            try {
                AppWidgetManager manager = AppWidgetManager.getInstance(c);
                bound = user == null ? manager.bindAppWidgetIdIfAllowed(id, provider) : manager.bindAppWidgetIdIfAllowed(id, user, provider, null);
            } catch (Throwable t) { Log.w("lock bind", t); }
            if (!bound) { host.deleteAppWidgetId(id); return false; }
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
            if (info == null) { host.deleteAppWidgetId(id); return false; }
            listen(c);
            if (info.configure != null && !configurationOptional(info)) {
                setup(c, id, info.configure);
                return true;
            }
            sPending.add(id);
            sFlushTries = 0;
            MAIN.removeCallbacks(FLUSH);
            MAIN.postDelayed(FLUSH, 250);
            return true;
        }

        static final List<Integer> sPending = new ArrayList<Integer>();
        static int sFlushTries;
        static final Runnable FLUSH = () -> { try { flush(); } catch (Throwable t) { Log.w("lock flush", t); } };

        static void flush() {
            Panel p = sPanel;
            if (p == null || sPending.isEmpty()) return;
            boolean back = p.root.hasWindowFocus();
            boolean ready = back && p.root.getVisibility() == View.VISIBLE && p.progress >= 1f;
            if (!ready && ++sFlushTries < 240) {
                if (back && p.hub < 1f && sFlushTries > 2) openHub();
                MAIN.postDelayed(FLUSH, 250);
                return;
            }
            for (Integer id : new ArrayList<Integer>(sPending)) p.addCard(id, ready, -1);
            sPending.clear();
            save(p.activity, p.grid.ids());
            p.updateEmpty();
            p.scrollToEnd();
        }

        static int sSetupId = -1;
        static ComponentName sSetupScreen;
        static boolean sSetupLaunched, sSetupShown;
        static int sSetupTries, sSetupChecks;
        static final Runnable SETUP_START = () -> { try { setupStart(); } catch (Throwable t) { Log.w("lock setup start", t); } };
        static final Runnable SETUP_CHECK = () -> { try { setupCheck(); } catch (Throwable t) { Log.w("lock setup check", t); } };
        static final Runnable SETUP_GRACE = () -> {
            try {
                if (sSetupId < 0 || wrapper == null) return;

                if (running(wrapper.getContext(), sSetupScreen)) return;
                setupDone(wrapper.getContext(), sSetupId, true);
            } catch (Throwable t) { Log.w("lock setup grace", t); }
        };
        static final Runnable UNWATCH = () -> { try { if (sSetupId < 0 && wrapper != null) unwatch(wrapper.getContext()); } catch (Throwable t) { Log.w("lock setup unwatch", t); } };

        static void setup(Context c, int id, ComponentName screen) {
            if (sSetupId >= 0 && sSetupId != id) setupDone(c, sSetupId, false);
            sSetupId = id; sSetupScreen = screen; sSetupLaunched = false; sSetupShown = false; sSetupTries = 0; sSetupChecks = 0;
            MAIN.removeCallbacks(SETUP_START); MAIN.removeCallbacks(SETUP_CHECK); MAIN.removeCallbacks(SETUP_GRACE);
            MAIN.postDelayed(SETUP_START, 100);
        }

        static void setupStart() {
            if (sSetupId < 0 || wrapper == null) return;
            boolean back = wrapper.hasWindowFocus();
            if (!back && ++sSetupTries < 30) { MAIN.postDelayed(SETUP_START, 100); return; }
            configure(wrapper.getContext(), sSetupId, sSetupScreen);
        }

        static void configure(final Context c, final int id, final ComponentName screen) {
            final String pkg = screen != null ? screen.getPackageName() : null;
            final Runnable open = () -> {
                try {
                    if (sSetupId != id) return;
                    if (running(c, screen)) return;
                    android.content.IntentSender sender = setupSender(c, id);
                    if (sender == null) { setupDone(c, id, true); return; }

                    c.startIntentSender(sender, null, Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_NEW_TASK, 0, setupOptions());
                    sSetupLaunched = true;
                } catch (Throwable t) { Log.w("lock widget setup", t); setupDone(c, id, true); }
            };
            watch(c, id);

            if (overLock(c, pkg) || setupOverLock(c, pkg)) {
                session(c, pkg);
                MAIN.postDelayed(() -> { open.run(); MAIN.removeCallbacks(SETUP_CHECK); MAIN.postDelayed(SETUP_CHECK, 3000); }, SESSION_DELAY);
                return;
            }
            dismissThen(c, open, () -> { if (sSetupId == id) setupDone(c, id, false); });
        }

        static void setupCheck() {
            if (sSetupId < 0 || sSetupShown || wrapper == null) return;

            if (!wrapper.hasWindowFocus()) { sSetupShown = sSetupLaunched; if (sSetupShown) return; }
            final Context c = wrapper.getContext();
            final int id = sSetupId; final ComponentName screen = sSetupScreen;
            if (running(c, screen) && ++sSetupChecks < 4) { MAIN.postDelayed(SETUP_CHECK, 3000); return; }
            dismissThen(c, () -> {
                try {
                    if (sSetupId != id || running(c, screen)) return;
                    android.content.IntentSender sender = setupSender(c, id);
                    if (sender != null) c.startIntentSender(sender, null, Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_NEW_TASK, 0, setupOptions());
                    else setupDone(c, id, true);
                } catch (Throwable t) { Log.w("lock widget setup after unlock", t); setupDone(c, id, true); }
            }, () -> { if (sSetupId == id && !running(c, screen)) setupDone(c, id, false); });
        }

        static void setupDone(Context c, int id, boolean ok) {
            if (id == sSetupId) {
                sSetupId = -1; sSetupScreen = null; sSetupLaunched = false; sSetupShown = false;
                MAIN.removeCallbacks(SETUP_START); MAIN.removeCallbacks(SETUP_CHECK); MAIN.removeCallbacks(SETUP_GRACE);

                MAIN.removeCallbacks(UNWATCH); MAIN.postDelayed(UNWATCH, 5000);
            }
            if (!ok) { try { host(c).deleteAppWidgetId(id); } catch (Throwable t) { Log.w("lock setup delete", t); } return; }
            sPending.add(id);
            sFlushTries = 0;
            MAIN.removeCallbacks(FLUSH);
            MAIN.postDelayed(FLUSH, 250);
        }

        static void setupResult(Context c) {
            try {
                String v = Settings.Secure.getString(c.getContentResolver(), LOCK_CONFIG_RESULT_KEY);
                if (v == null || v.isEmpty() || sSetupId < 0) return;
                String[] part = v.split("\\|");
                if (part.length < 2 || Integer.parseInt(part[0]) != sSetupId) return;
                setupDone(c, sSetupId, Integer.parseInt(part[1]) == Activity.RESULT_OK);
            } catch (Throwable t) { Log.w("lock setup result", t); }
        }

        static void watch(Context c, int id) {
            MAIN.removeCallbacks(UNWATCH);
            try {
                Settings.Secure.putString(c.getContentResolver(), LOCK_CONFIG_RESULT_KEY, null);
                Settings.Secure.putString(c.getContentResolver(), LOCK_CONFIG_KEY, id + "|" + android.os.SystemClock.uptimeMillis());
            } catch (Throwable t) { Log.w("lock setup watch", t); }
        }

        static void unwatch(Context c) {
            try {
                if (Settings.Secure.getString(c.getContentResolver(), LOCK_CONFIG_KEY) != null) Settings.Secure.putString(c.getContentResolver(), LOCK_CONFIG_KEY, null);
            } catch (Throwable t) { Log.w("lock setup unwatch", t); }
        }

        static android.content.IntentSender setupSender(Context c, int id) {
            AppWidgetHost host = host(c);
            try {
                Object s = AppWidgetHost.class.getMethod("getIntentSenderForConfigureActivity", int.class, int.class).invoke(host, id, 0);
                if (s instanceof android.content.IntentSender) return (android.content.IntentSender) s;
            } catch (Throwable t) { Log.w("lock setup sender", t); }
            try {
                java.lang.reflect.Field field = AppWidgetHost.class.getDeclaredField("sService");
                field.setAccessible(true);
                Object service = field.get(null);
                for (Method m : service.getClass().getMethods()) {
                    if (!m.getName().equals("createAppWidgetConfigIntentSender") || m.getParameterTypes().length != 3) continue;
                    return (android.content.IntentSender) m.invoke(service, c.getPackageName(), id, 0);
                }
            } catch (Throwable t) { Log.w("lock setup sender (service)", t); }
            return null;
        }

        static android.os.Bundle setupOptions() {
            try {
                android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                android.app.ActivityOptions.class.getMethod("setPendingIntentBackgroundActivityStartMode", int.class).invoke(o, 1);
                try { android.app.ActivityOptions.class.getMethod("setSplashScreenStyle", int.class).invoke(o, 0); } catch (Throwable ignored) { }
                return o.toBundle();
            } catch (Throwable t) { Log.w("lock setup options", t); return null; }
        }

        static boolean running(Context c, ComponentName screen) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                for (android.app.ActivityManager.RunningTaskInfo t : am.getRunningTasks(5)) {
                    for (ComponentName n : new ComponentName[]{t.topActivity, t.baseActivity}) {
                        if (n == null) continue;
                        if (n.equals(screen) || ("android".equals(n.getPackageName()) && CONFIG_PROXY.equals(n.getClassName()))) return true;
                    }
                }
            } catch (Throwable ignored) { }
            return false;
        }

        static void dismissThen(Context c, Runnable run, Runnable cancelled) {
            try {
                Object s = starter(c.getClassLoader());
                if (s != null) {
                    for (Method m : s.getClass().getMethods()) {
                        if (m.getName().equals("executeRunnableDismissingKeyguard") && m.getParameterTypes().length == 5) { m.setAccessible(true); m.invoke(s, run, cancelled, false, true, false); return; }
                    }
                }
            } catch (Throwable t) { Log.w("lock setup unlock", t); }
            run.run();
        }

        static Object clickProxy(final Class<?> type) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, m, args) -> {
                    if (m.getDeclaringClass() == Object.class) return basic(self, m, args);
                    if (!"onInteraction".equals(m.getName()) || args == null || args.length != 3) return false;
                    View target = (View) args[0];
                    android.app.PendingIntent intent = (android.app.PendingIntent) args[1];
                    Object response = args[2];
                    Object options = null;
                    try { options = Xp.callMethod(response, "getLaunchOptions", target); } catch (Throwable ignored) { }
                    try {
                        if (intent != null && Boolean.TRUE.equals(android.app.PendingIntent.class.getMethod("isActivity").invoke(intent))) {
                            final String pkg = targetOf(target.getContext(), intent);
                            boolean over = overLock(target.getContext(), pkg);
                            Object s = starter(target.getContext().getClassLoader());

                            Intent fill = options instanceof android.util.Pair && ((android.util.Pair<?, ?>) options).first instanceof Intent ? (Intent) ((android.util.Pair<?, ?>) options).first : null;
                            android.os.Bundle extra = null;
                            try { Object o = options instanceof android.util.Pair ? ((android.util.Pair<?, ?>) options).second : null; if (o instanceof android.app.ActivityOptions) extra = ((android.app.ActivityOptions) o).toBundle(); } catch (Throwable ignored) { }
                            if (s != null && over) {
                                Method without = null;
                                for (Method start : s.getClass().getMethods()) {
                                    if (start.getName().equals("startPendingIntentWithoutDismissing") && start.getParameterTypes().length == 6) { without = start; break; }
                                }
                                if (without != null) {
                                    without.setAccessible(true);
                                    final Method go = without; final Object starterObject = s; final android.app.PendingIntent pi = intent;
                                    final Intent fillIn = fill; final android.os.Bundle opts = extra;
                                    session(target.getContext(), pkg);

                                    picking = true;
                                    veil(true, OPEN_DELAY - 20);
                                    MAIN.removeCallbacks(SHADE_BACK);
                                    MAIN.postDelayed(SHADE_BACK, 3000);
                                    MAIN.postDelayed(() -> { try { go.invoke(starterObject, pi, false, null, null, fillIn, opts); } catch (Throwable t) { Log.w("lock widget open", t); shadeBack(); } }, OPEN_DELAY);
                                    return true;
                                }
                            }
                            if (s != null && !over) {
                                for (Method start : s.getClass().getMethods()) {
                                    if (start.getName().equals("startPendingIntentMaybeDismissingKeyguard") && start.getParameterTypes().length == 7) { start.setAccessible(true); start.invoke(s, intent, true, null, null, fill, extra, null); return true; }
                                }
                                for (Method start : s.getClass().getMethods()) {
                                    if (start.getName().equals("startPendingIntentDismissingKeyguard") && start.getParameterTypes().length == 1) { start.setAccessible(true); start.invoke(s, intent); return true; }
                                }
                            }
                        }
                    } catch (Throwable t) { Log.w("lock widget tap", t); }

                    try {
                        for (Method plain : android.widget.RemoteViews.class.getDeclaredMethods()) {
                            if (plain.getName().equals("startPendingIntent") && plain.getParameterTypes().length == 3) { plain.setAccessible(true); return plain.invoke(null, target, intent, options); }
                        }
                    } catch (Throwable t) { Log.w("lock widget send", t); }
                    return false;
                });
        }

        static Object sClicks;

        static Object clickHandler() throws Throwable {
            if (sClicks != null) return sClicks;
            final Class<?> type = Class.forName("android.widget.RemoteViews$InteractionHandler");
            sClicks = clickProxy(type);
            return sClicks;
        }

        static void hostClicks(AppWidgetHost host) {
            try { Xp.setObjectField(host, "mInteractionHandler", clickHandler()); } catch (Throwable t) { Log.w("lock widget host clicks", t); }
        }

        static void clicks(final AppWidgetHostView view) {
            try {
                Object handler = clickHandler();
                for (Method m : AppWidgetHostView.class.getMethods()) {
                    if (m.getName().equals("setInteractionHandler") && m.getParameterTypes().length == 1) { m.invoke(view, handler); return; }
                }
            } catch (Throwable t) { Log.w("lock widget clicks", t); }
        }
    }

    static final class LockPicker extends FrameLayout {
        final Panel panel; final Look look;
        final View scrim; final LinearLayout sheet; final LinearLayout list;

        LockPicker(Panel panel) {
            super(panel.activity);
            this.panel = panel; this.look = panel.look;
            Context c = getContext();

            scrim = new View(c);
            scrim.setBackgroundColor(0x99000000);
            scrim.setOnClickListener(v -> close());
            addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            sheet = new LinearLayout(c) {
                @Override protected void onMeasure(int ws, int hs) {
                    int max = Math.round(MeasureSpec.getSize(hs) * 0.82f);
                    super.onMeasure(ws, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
                }
            };
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setClickable(true);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(look.superDark ? 0xff1c1c1c : look.menu);
            float r = dp(c, 30);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            sheet.setBackground(bg);
            int pad = dp(c, 14);
            sheet.setPadding(pad, dp(c, 10), pad, dp(c, 18));

            View handle = new View(c);
            handle.setBackground(round(look.onCard & 0x40ffffff | 0x30000000, dp(c, 2)));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(c, 34), dp(c, 4));
            hp.gravity = Gravity.CENTER_HORIZONTAL; hp.bottomMargin = dp(c, 10);
            sheet.addView(handle, hp);

            TextView title = new TextView(c);
            title.setText(I18n.cs("Add widget"));
            title.setTextSize(19); title.setTypeface(I18n.medium()); title.setTextColor(look.onCard);
            title.setPadding(dp(c, 6), 0, 0, dp(c, 8));
            sheet.addView(title);

            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL);
            sv.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            sheet.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            addView(sheet, sp);

            build();
        }

        void build() {
            Context c = getContext();
            PackageManager pm = c.getPackageManager();
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            List<AppWidgetProviderInfo> all;
            try { all = mgr.getInstalledProviders(); } catch (Throwable t) { all = new ArrayList<AppWidgetProviderInfo>(); }
            Map<String, List<AppWidgetProviderInfo>> byApp = new TreeMap<String, List<AppWidgetProviderInfo>>(String.CASE_INSENSITIVE_ORDER);
            for (AppWidgetProviderInfo info : all) {
                if (info == null || info.provider == null) continue;
                String app;
                try { app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(), 0))); }
                catch (Throwable t) { app = info.provider.getPackageName(); }
                List<AppWidgetProviderInfo> group = byApp.get(app);
                if (group == null) { group = new ArrayList<AppWidgetProviderInfo>(); byApp.put(app, group); }
                group.add(info);
            }
            if (byApp.isEmpty()) {
                TextView none = new TextView(c);
                none.setText(I18n.cs("No widgets available"));
                none.setTextColor(look.onCard); none.setTextSize(15);
                none.setPadding(dp(c, 6), dp(c, 24), dp(c, 6), dp(c, 24));
                list.addView(none);
                return;
            }
            for (Map.Entry<String, List<AppWidgetProviderInfo>> e : byApp.entrySet()) {
                TextView head = new TextView(c);
                head.setText(e.getKey());
                head.setTextSize(13); head.setTypeface(I18n.medium());
                head.setTextColor(look.onCard);
                head.setAllCaps(false);
                head.setPadding(dp(c, 6), dp(c, 14), dp(c, 6), dp(c, 6));
                head.setAlpha(0.7f);
                list.addView(head);
                for (AppWidgetProviderInfo info : e.getValue()) list.addView(row(info));
            }
        }

        View row(final AppWidgetProviderInfo info) {
            Context c = getContext();
            PackageManager pm = c.getPackageManager();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(round((look.superDark ? 0xffffffff : look.onCard) & 0x14ffffff, dp(c, 18)));
            row.setClickable(true);
            int p = dp(c, 10);
            row.setPadding(p, p, p, p);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(c, 6);
            row.setLayoutParams(rp);

            ImageView img = new ImageView(c);
            Drawable preview = null;
            try { preview = info.loadPreviewImage(c, 0); } catch (Throwable ignored) { }
            if (preview == null) { try { preview = info.loadIcon(c, 0); } catch (Throwable ignored) { } }
            img.setImageDrawable(preview);
            img.setAdjustViewBounds(true);
            img.setMaxHeight(dp(c, 76));
            img.setMaxWidth(dp(c, 150));
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.rightMargin = dp(c, 12);
            row.addView(img, ip);

            TextView label = new TextView(c);
            label.setText(info.loadLabel(pm));
            label.setTextSize(15); label.setTypeface(I18n.medium()); label.setTextColor(look.onCard);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); choose(info); });
            return row;
        }

        void choose(final AppWidgetProviderInfo info) {
            if (!panel.editAllowed()) return;
            close();
            MAIN.post(() -> {
                boolean ok = Lock.add(info.provider, info.getProfile());
                if (!ok) { try { Toast.makeText(getContext(), I18n.cs("Couldn't add that widget"), Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { } }
            });
        }

        void open() {
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(160).start();
            post(() -> {
                float h = sheet.getHeight();
                sheet.setTranslationY(h > 0 ? h : dp(getContext(), 400));
                sheet.animate().translationY(0f).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            });
        }

        void close() {
            if (panel.picker != this) return;
            panel.picker = null;
            scrim.animate().alpha(0f).setDuration(140).start();
            float h = sheet.getHeight();
            sheet.animate().translationY(h > 0 ? h : dp(getContext(), 400)).setDuration(200).setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                    .withEndAction(() -> { if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this); }).start();
        }
    }

    static final class Card extends FrameLayout {
        final Panel panel;
        final int id;
        final AppWidgetHostView view;
        final AppWidgetProviderInfo info;
        final FrameLayout frame;
        final ImageView badge, handle;
        final int slop;
        int w = 6, h = 4;
        float downX, downY;
        boolean longPressed, pressed, resizing;
        ObjectAnimator wobble;
        final Runnable longPress;

        Card(Context c, Panel p, int id, AppWidgetHostView view, AppWidgetProviderInfo info) {
            super(c);
            panel = p; this.id = id; this.view = view; this.info = info;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setClipChildren(false); setClipToPadding(false);
            view.setPadding(0, 0, 0, 0);
            frame = new FrameLayout(c);
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(v.getContext(), 24)); }
            });
            frame.setClipToOutline(true);
            frame.addView(view, new FrameLayout.LayoutParams(-1, -1));
            addView(frame, new FrameLayout.LayoutParams(-1, -1));

            badge = corner(c, Glyph.MINUS, Gravity.TOP | Gravity.START);
            badge.setContentDescription(I18n.cs("Remove"));
            badge.setOnClickListener(v -> removeAnimated());

            handle = corner(c, Glyph.CORNER, Gravity.BOTTOM | Gravity.RIGHT);
            handle.setContentDescription(I18n.cs("Resize"));
            handle.setOnTouchListener(new OnTouchListener() {
                float x, y; int startW, startH;
                @Override public boolean onTouch(View v, MotionEvent ev) {
                    if (!panel.wiggle || !panel.editAllowed()) return true;
                    Grid grid = panel.grid;
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            x = ev.getRawX(); y = ev.getRawY(); startW = getWidth(); startH = getHeight();
                            resizing = true; grid.resizing = Card.this;
                            ViewParentRequest.disallow(Card.this);
                            if (wobble != null) wobble.pause();
                            setRotation(0f);
                            fast(panel.root, true);
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            return true;
                        case MotionEvent.ACTION_MOVE: {
                            int nw = snapWidth((startW + ev.getRawX() - x + grid.gap) / grid.unitW());
                            int nh = Math.max(2, Math.min(16, Math.round((startH + ev.getRawY() - y + grid.gap) / (float) grid.unitH)));
                            if (nw != w || nh != h) {
                                w = nw; h = nh;
                                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                grid.requestLayout();
                            }
                            panel.moving();
                            return true;
                        }
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            resizing = false; grid.resizing = null;
                            saveSize(getContext(), id, w, h);
                            if (wobble != null && panel.wiggle) wobble.resume();
                            panel.moving();
                            return true;
                    }
                    return true;
                }
            });

            longPress = () -> {
                if (!isAttachedToWindow() || panel.grid.busy() || !panel.enterWiggle()) return;
                longPressed = true;
                panel.grid.startDrag(this, downX, downY);
                ViewParentRequest.disallow(this);
            };
            int[] saved = size(c, id);
            if (saved != null) { w = saved[0]; h = saved[1]; } else defaultSize();
        }

        ImageView corner(Context c, int glyph, int gravity) {
            ImageView v = new ImageView(c);
            v.setImageDrawable(new Glyph(glyph, panel.look.onCard, dp(c, 2.2f)));
            GradientDrawable b = round(panel.look.badge, dp(c, 13));
            b.setStroke(Math.max(1, dp(c, 1)), panel.look.superDark ? 0xff3a3a3a : 0x22000000);
            v.setBackground(b);
            v.setElevation(dp(c, 3));
            v.setVisibility(View.GONE);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(c, 26), dp(c, 26), gravity);

            addView(v, lp);
            return v;
        }

        void defaultSize() {
            int[] cell = Grid.cell(panel.activity);
            int spanX = targetSpan(info, "targetCellWidth"), spanY = targetSpan(info, "targetCellHeight");
            if (spanX <= 0) spanX = Math.max(1, (int) Math.ceil((info.minWidth + cell[2]) / (double) (cell[0] + cell[2])));
            if (spanY <= 0) spanY = Math.max(1, (int) Math.ceil((info.minHeight + cell[2]) / (double) (cell[1] + cell[2])));
            int columns = Math.max(2, cell[3]);
            w = snapWidth(Math.min(columns, spanX) * COLS / (float) columns);
            h = Math.max(2, Math.min(16, spanY * 2));
        }

        static int targetSpan(AppWidgetProviderInfo info, String field) {
            try { return AppWidgetProviderInfo.class.getField(field).getInt(info); } catch (Throwable t) { return 0; }
        }

        void sized(int wpx, int hpx) {
            float d = getResources().getDisplayMetrics().density;
            int wd = Math.round(wpx / d), hd = Math.round(hpx / d);
            try { view.updateAppWidgetSize(null, wd, hd, wd, hd); } catch (Throwable ignored) { }
        }

        ValueAnimator growing;
        void grow(int oldW, int oldH, int newW, int newH) {
            float fromX = oldW * (growing != null ? getScaleX() : 1f) / newW, fromY = oldH * (growing != null ? getScaleY() : 1f) / newH;
            if (growing != null) growing.cancel();
            final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            setPivotX(rtl ? newW : 0); setPivotY(0);
            setScaleX(fromX); setScaleY(fromY);
            final float fx = fromX, fy = fromY;
            growing = ValueAnimator.ofFloat(0f, 1f);
            growing.setDuration(420);
            growing.setInterpolator(new Spring(0.62f, 17f, 420));
            growing.addUpdateListener(a -> { float t = (Float) a.getAnimatedValue(); setScaleX(fx + (1f - fx) * t); setScaleY(fy + (1f - fy) * t); panel.moving(); });
            growing.addListener(new AnimatorListenerAdapter() {
                boolean cancelled;
                @Override public void onAnimationCancel(Animator a) { cancelled = true; }
                @Override public void onAnimationEnd(Animator a) {
                    if (cancelled) return;
                    growing = null;
                    setScaleX(1f); setScaleY(1f);
                    setPivotX(getWidth() / 2f); setPivotY(getHeight() / 2f);
                }
            });
            growing.start();
        }

        void wiggle(boolean on, int index) {
            if (wobble != null) { wobble.cancel(); wobble = null; }
            badge.setVisibility(on ? View.VISIBLE : View.GONE);
            handle.setVisibility(on ? View.VISIBLE : View.GONE);
            if (!on) { animate().rotation(0f).setDuration(120).start(); return; }
            for (View v : new View[]{badge, handle}) {
                v.setScaleX(0f); v.setScaleY(0f);
                v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2f)).start();
            }
            float amp = w <= 4 ? 1.6f : w <= 6 ? 1.2f : 0.7f;
            wobble = ObjectAnimator.ofFloat(this, "rotation", -amp, amp);
            wobble.setDuration(130 + (index % 3) * 12);
            wobble.setRepeatCount(ValueAnimator.INFINITE);
            wobble.setRepeatMode(ValueAnimator.REVERSE);
            wobble.setStartDelay((index * 37) % 120);
            wobble.start();
        }

        void removeAnimated() {
            if (!panel.editAllowed()) return;
            badge.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (wobble != null) { wobble.cancel(); wobble = null; }
            animate().scaleX(0.4f).scaleY(0.4f).alpha(0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { animate().setListener(null); panel.remove(Card.this); }
            }).start();
        }

        View routed; float routedX, routedY;
        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL
                    || (act == MotionEvent.ACTION_MOVE && Math.hypot(ev.getRawX() - downX, ev.getRawY() - downY) > slop)) MAIN.removeCallbacks(longPress);
            if (!panel.wiggle && routed == null) return super.dispatchTouchEvent(ev);
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                routed = on(handle, ev) ? handle : on(badge, ev) ? badge : null;
                routedX = ev.getRawX(); routedY = ev.getRawY();
            }
            if (routed == null) return super.dispatchTouchEvent(ev);
            View to = routed;
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) routed = null;
            if (to == handle) {
                MotionEvent copy = MotionEvent.obtain(ev);
                copy.offsetLocation(-handle.getLeft(), -handle.getTop());
                handle.dispatchTouchEvent(copy);
                copy.recycle();
            } else if (a == MotionEvent.ACTION_UP && Math.hypot(ev.getRawX() - routedX, ev.getRawY() - routedY) < slop * 2) {
                removeAnimated();
            }
            return true;
        }

        boolean on(View corner, MotionEvent ev) {
            if (corner.getVisibility() != View.VISIBLE) return false;
            float x = ev.getX(), y = ev.getY(), extra = dp(getContext(), 18);
            return x >= corner.getLeft() - extra && x <= corner.getRight() + extra && y >= corner.getTop() - extra && y <= corner.getBottom() + extra;
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (panel.wiggle) return !resizing && !on(badge, ev) && !on(handle, ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX(); downY = ev.getRawY(); longPressed = false;
                    MAIN.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(ev.getRawX() - downX, ev.getRawY() - downY) > slop) MAIN.removeCallbacks(longPress);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    MAIN.removeCallbacks(longPress);
                    break;
            }
            return longPressed;
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            Grid grid = panel.grid;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX(); downY = ev.getRawY(); pressed = true;

                    if (panel.wiggle) { longPressed = false; MAIN.removeCallbacks(longPress); MAIN.postDelayed(longPress, ViewConfiguration.getLongPressTimeout()); }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!grid.dragging && Math.hypot(ev.getRawX() - downX, ev.getRawY() - downY) > slop) MAIN.removeCallbacks(longPress);
                    if (grid.dragging && grid.dragged == this) grid.dragTo(ev.getRawX(), ev.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pressed = false;
                    MAIN.removeCallbacks(longPress);
                    if (grid.dragging && grid.dragged == this) grid.drop();
                    longPressed = false;
                    return true;
            }
            return true;
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            MAIN.removeCallbacks(longPress);
        }
    }

    static final class ViewParentRequest {
        static void disallow(View v) { if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true); }
    }

    static final class Grid extends ViewGroup {
        final Panel panel;
        final List<Card> order = new ArrayList<Card>();
        final Map<Card, int[]> slots = new HashMap<Card, int[]>();
        final int gap, unitH;
        Card dragged, resizing;
        boolean dragging;
        float grabX, grabY, absX, absY;
        int[] home;
        boolean reordering;
        float lastRawX, lastRawY, swapX, swapY;
        long swapAt;
        final Runnable edgeScroll;

        Grid(Context c, Panel p) {
            super(c);
            panel = p;
            gap = dp(c, 12);
            int[] cell = cell(p.activity);
            unitH = Math.max(dp(c, 36), (cell[1] + gap) / 2);
            setClipChildren(false); setClipToPadding(false);
            edgeScroll = new Runnable() {
                @Override public void run() {
                    if (!dragging) return;
                    ScrollView s = panel.scroll;
                    int[] at = new int[2]; s.getLocationOnScreen(at);
                    float y = lastRawY - at[1] - s.getPaddingTop(), zone = dp(getContext(), 72);
                    int by = 0;
                    if (y < zone) by = -Math.round(dp(getContext(), 14) * (1 - y / zone));
                    else if (y > s.getHeight() - s.getPaddingTop() - s.getPaddingBottom() - zone) by = Math.round(dp(getContext(), 14) * Math.min(1f, 1 - (s.getHeight() - s.getPaddingTop() - s.getPaddingBottom() - y) / zone));
                    if (by != 0 && s.canScrollVertically(by)) { s.scrollBy(0, by); dragTo(lastRawX, lastRawY); }
                    MAIN.postDelayed(this, 16);
                }
            };
        }

        boolean busy() { return dragging || resizing != null; }

        float unitW() { return (getWidth() + gap) / (float) COLS; }

        static int[] cell(Context a) {
            int w = 0, h = 0, space = dp(a, 12), columns = 4;
            try {
                Object profile = Xp.callMethod(a, "getDeviceProfile");
                try { w = ((Number) Xp.getObjectField(profile, "cellWidthPx")).intValue(); } catch (Throwable ignored) { }
                try { h = ((Number) Xp.getObjectField(profile, "cellHeightPx")).intValue(); } catch (Throwable ignored) { }
                try { columns = ((Number) Xp.getObjectField(Xp.getObjectField(profile, "inv"), "numColumns")).intValue(); } catch (Throwable ignored) { }
            } catch (Throwable ignored) { }
            if (w <= 0) w = a.getResources().getDisplayMetrics().widthPixels / 5;
            if (h <= 0) h = Math.round(w * 1.15f);
            return new int[]{w, h, space, columns};
        }

        void add(Card c, boolean animate, int index) {
            if (index < 0 || index > order.size()) order.add(c); else order.add(index, c);
            addView(c);
            if (animate) {
                c.setAlpha(0f); c.setScaleX(0.85f); c.setScaleY(0.85f);
                c.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(new Spring(0.7f, 20f, 360)).start();
            }
            if (panel.wiggle) c.wiggle(true, order.size() - 1);
        }

        void remove(Card c) {
            if (dragged == c) { dragging = false; dragged = null; MAIN.removeCallbacks(edgeScroll); }
            if (resizing == c) resizing = null;
            order.remove(c); slots.remove(c); removeView(c); requestLayout();
        }

        List<Integer> ids() { List<Integer> out = new ArrayList<Integer>(); for (Card c : order) out.add(c.id); return out; }

        void wiggle(boolean on) { for (int i = 0; i < order.size(); i++) order.get(i).wiggle(on, i); }

        int indexAt(float x, float y) {
            for (int i = 0; i < order.size(); i++) {
                int[] s = slots.get(order.get(i));
                if (s == null) continue;
                if (y < s[1]) return i;
                if (y < s[1] + s[3] && x < s[0] + s[2] / 2f) return i;
            }
            return order.size();
        }

        Map<Card, int[]> pack(int width) {
            Map<Card, int[]> out = new HashMap<Card, int[]>();
            float unit = (width + gap) / (float) COLS;
            int x = 0, y = 0, rowH = 0;
            for (Card c : order) {
                if (x + c.w > COLS) { y += rowH + gap; x = 0; rowH = 0; }
                int left = Math.round(x * unit), right = Math.round((x + c.w) * unit) - gap;
                int height = c.h * unitH - gap;
                out.put(c, new int[]{left, y, right - left, height});
                rowH = Math.max(rowH, height);
                x += c.w;
            }
            out.put(null, new int[]{0, 0, 0, order.isEmpty() ? 0 : y + rowH});
            return out;
        }

        @Override protected void onMeasure(int ws, int hs) {
            int width = MeasureSpec.getSize(ws);
            Map<Card, int[]> p = pack(width);
            for (Card c : order) {
                int[] r = p.get(c);
                c.measure(MeasureSpec.makeMeasureSpec(r[2], MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(r[3], MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(width, p.get(null)[3]);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            Map<Card, int[]> p = pack(r - l);
            for (Card c : order) {
                int[] s = p.get(c);
                int[] old = slots.get(c);
                boolean sizeChanged = old == null || old[2] != s[2] || old[3] != s[3];
                c.layout(s[0], s[1], s[0] + s[2], s[1] + s[3]);
                if (sizeChanged) c.sized(s[2], s[3]);
                if (sizeChanged && old != null && c != dragged && s[2] > 0 && s[3] > 0) c.grow(old[2], old[3], s[2], s[3]);
                if (old != null && c != dragged && (old[0] != s[0] || old[1] != s[1])) {
                    float fromX = old[0] - s[0] + c.getTranslationX(), fromY = old[1] - s[1] + c.getTranslationY();
                    c.setTranslationX(fromX); c.setTranslationY(fromY);
                    c.animate().translationX(0f).translationY(0f).setDuration(380).setInterpolator(new Spring(0.75f, 19f, 380)).start();
                    panel.moving();
                }
                slots.put(c, s.clone());
            }
            if (dragged != null) {
                int[] s = slots.get(dragged);
                if (s != null) { home = new int[]{s[0], s[1]}; follow(lastRawX, lastRawY); }
            }
            reordering = false;
        }

        void startDrag(Card c, float rawX, float rawY) {
            if (dragging || resizing != null) return;
            dragged = c; dragging = true;
            grabX = rawX; grabY = rawY; lastRawX = rawX; lastRawY = rawY;
            int[] s = slots.get(c);
            home = s == null ? new int[]{c.getLeft(), c.getTop()} : new int[]{s[0], s[1]};
            absX = home[0] + c.getTranslationX(); absY = home[1] + c.getTranslationY();
            grabScroll = panel.scroll.getScrollY();
            c.animate().cancel();
            if (c.wobble != null) c.wobble.pause();
            c.setRotation(0f);
            c.setTranslationZ(dp(getContext(), 12));
            c.animate().scaleX(1.05f).scaleY(1.05f).setDuration(160).start();
            c.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            fast(panel.root, true);
            MAIN.post(edgeScroll);
        }

        void dragTo(float rawX, float rawY) {
            if (!dragging || dragged == null) return;
            lastRawX = rawX; lastRawY = rawY;
            Card c = dragged;
            follow(rawX, rawY);
            if (reordering) { panel.moving(); return; }

            long now = android.os.SystemClock.uptimeMillis();
            if (now - swapAt < 220 && Math.hypot(rawX - swapX, rawY - swapY) < dp(getContext(), 28)) { panel.moving(); return; }
            int[] at = new int[2]; getLocationOnScreen(at);

            float fx = rawX - at[0];
            float top = home[1] + c.getTranslationY(), moved = c.getTranslationY(), reach = Math.min(c.getHeight() / 2f, unitH);
            float fy = Math.abs(moved) < unitH / 4f ? rawY - at[1] : moved < 0 ? top + reach : top + c.getHeight() - reach;
            boolean wide = c.w >= 9;
            int mine = order.indexOf(c), target = -1, first = -1, last = -1;
            for (int i = 0; i < order.size(); i++) {
                Card o = order.get(i);
                if (o == c) continue;
                int[] s = slots.get(o);
                if (s == null || fy < s[1] || fy > s[1] + s[3]) continue;
                if (!wide && fx >= s[0] && fx <= s[0] + s[2]) { target = i; break; }
                if (first < 0) first = i;
                last = i;
            }
            if (target < 0 && first >= 0) {
                int[] own = slots.get(c);
                boolean sameRow = own != null && slots.get(order.get(first))[1] == own[1];
                if (!sameRow) target = mine > first ? first : last;
            }
            if (target >= 0 && target != mine) {
                order.remove(c);
                order.add(Math.min(target, order.size()), c);
                reordering = true; swapAt = now; swapX = rawX; swapY = rawY;
                c.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                requestLayout();
            }
            panel.moving();
        }

        int grabScroll;

        void follow(float rawX, float rawY) {
            Card c = dragged;
            if (c == null || home == null) return;
            float x = absX + rawX - grabX, y = absY + rawY - grabY + (panel.scroll.getScrollY() - grabScroll);
            float pad = dp(getContext(), 8);
            x = Math.max(0, Math.min(Math.max(0, getWidth() - c.getWidth()), x));
            y = Math.max(-pad, Math.min(Math.max(-pad, getHeight() - c.getHeight() + pad), y));
            c.setTranslationX(x - home[0]);
            c.setTranslationY(y - home[1]);
        }

        void drop() {
            if (!dragging) return;
            final Card c = dragged;
            dragging = false; dragged = null; reordering = false;
            MAIN.removeCallbacks(edgeScroll);
            if (c == null) return;
            c.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new Spring(0.78f, 18f, 380))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) {
                            c.setTranslationZ(0f);
                            c.animate().setListener(null);
                            if (c.wobble != null && panel.wiggle) c.wobble.resume();
                        }
                    }).start();
            panel.moving();
        }

        void cancelDrag() { if (dragging) drop(); resizing = null; }
    }

    static final class Weather {
        static final class Now { CharSequence temp, condition; Icon icon; Object intent; }
        static volatile Now now;
        private static final Map<Object, Object> wrappers = new WeakHashMap<Object, Object>();

        static void install() throws Throwable {
            final Class<?> session = Class.forName("android.app.smartspace.SmartspaceSession");
            final Class<?> listener = Class.forName("android.app.smartspace.SmartspaceSession$OnTargetsAvailableListener");
            Xp.Callback add = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    for (int i = 0; i < p.args.length; i++) {
                        final Object original = p.args[i];
                        if (!listener.isInstance(original) || Proxy.isProxyClass(original.getClass())) continue;
                        Object wrapper;
                        synchronized (wrappers) {
                            wrapper = wrappers.get(original);
                            if (wrapper == null) {
                                wrapper = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (self, m, args) -> {
                                    if ("onTargetsAvailable".equals(m.getName()) && args != null && args.length == 1 && args[0] instanceof List) {
                                        try { read((List<?>) args[0]); } catch (Throwable ignored) { }
                                    }
                                    if (m.getDeclaringClass() == Object.class) return basic(self, m, args);
                                    return m.invoke(original, args);
                                });
                                wrappers.put(original, wrapper);
                            }
                        }
                        p.args[i] = wrapper;
                    }
                }
            };
            Xp.Callback remove = new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    for (int i = 0; i < p.args.length; i++) {
                        Object w;
                        synchronized (wrappers) { w = wrappers.get(p.args[i]); }
                        if (w != null) p.args[i] = w;
                    }
                }
            };
            Xp.hookAllMethods(session, "addOnTargetsAvailableListener", add);
            Xp.hookAllMethods(session, "removeOnTargetsAvailableListener", remove);
        }

        static void read(List<?> targets) throws Throwable {
            for (Object t : targets) {
                int type = (Integer) t.getClass().getMethod("getFeatureType").invoke(t);
                if (type != 1) continue;
                Now n = new Now();
                Object header = t.getClass().getMethod("getHeaderAction").invoke(t);
                if (header != null) {
                    n.temp = (CharSequence) header.getClass().getMethod("getTitle").invoke(header);
                    n.condition = (CharSequence) header.getClass().getMethod("getSubtitle").invoke(header);
                    Object icon = header.getClass().getMethod("getIcon").invoke(header);
                    if (icon instanceof Icon) n.icon = (Icon) icon;
                    Object intent = header.getClass().getMethod("getIntent").invoke(header);
                    if (intent == null) intent = header.getClass().getMethod("getPendingIntent").invoke(header);
                    n.intent = intent;
                    if (n.condition == null || n.condition.length() == 0) n.condition = (CharSequence) header.getClass().getMethod("getContentDescription").invoke(header);
                }
                if (n.temp == null || n.temp.length() == 0) n.temp = templateText(t);
                if (n.temp != null && n.temp.length() > 0) {
                    if (n.condition != null && n.condition.toString().contains(n.temp.toString())) n.condition = null;
                    now = n;
                    MAIN.post(() -> { if (sPanel != null && sPanel.progress > 0) sPanel.refresh(); });
                }
                return;
            }
        }

        static CharSequence templateText(Object target) {
            try {
                Object data = target.getClass().getMethod("getTemplateData").invoke(target);
                if (data == null) return null;
                for (String getter : new String[]{"getSubtitleSupplementalItem", "getSubtitleItem", "getPrimaryItem"}) {
                    try {
                        Object item = data.getClass().getMethod(getter).invoke(data);
                        if (item == null) continue;
                        Object text = item.getClass().getMethod("getText").invoke(item);
                        if (text == null) continue;
                        Object s = text.getClass().getMethod("getText").invoke(text);
                        if (s instanceof CharSequence && ((CharSequence) s).length() > 0 && ((CharSequence) s).toString().contains("°")) return (CharSequence) s;
                    } catch (Throwable ignored) { }
                }
            } catch (Throwable ignored) { }
            return null;
        }
    }
}
