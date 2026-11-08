package io.github.pixelsuite;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.view.WindowManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class NavStrip {
    private NavStrip() {}

    private static volatile boolean sOn;
    private static volatile int sStripPx;
    private static int sNavType = 2, sTappableType = 64;
    private static final Map<Object, Boolean> sDisplays = Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private static Handler sWorker;
    private static boolean sBooted;

    private static Field fAttrs, fActivity, fDisplay;
    private static Constructor<?> cState, cSource;
    private static Method mDisplayFrame, mSourceSize, mSourceAt, mAddSource, mGetType, mGetId, mGetFrame, mSetFrame, mVisibleFrame, mSetVisibleFrame;

    static void installSystem(ClassLoader cl) {
        try {
            Class<?> t = Class.forName("android.view.WindowInsets$Type");
            sNavType = (Integer) t.getMethod("navigationBars").invoke(null);
            sTappableType = (Integer) t.getMethod("tappableElement").invoke(null);
        } catch (Throwable ignored) { }
        try {
            Class<?> state = Class.forName("android.view.InsetsState"), source = Class.forName("android.view.InsetsSource");
            cState = state.getConstructor(state);
            cSource = source.getConstructor(source);
            mDisplayFrame = state.getMethod("getDisplayFrame");
            mSourceSize = state.getMethod("sourceSize");
            mSourceAt = state.getMethod("sourceAt", int.class);
            mAddSource = state.getMethod("addSource", source);
            mGetType = source.getMethod("getType");
            mGetId = source.getMethod("getId");
            mGetFrame = source.getMethod("getFrame");
            mSetFrame = source.getMethod("setFrame", Rect.class);
            try { mVisibleFrame = source.getMethod("getVisibleFrame"); mSetVisibleFrame = source.getMethod("setVisibleFrame", Rect.class); } catch (Throwable ignored) { }
        } catch (Throwable t) { return; }

        Class<?> policy = Xp.findClass("com.android.server.wm.InsetsPolicy", cl);
        for (Method m : policy.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if ("adjustInsetsForWindow".equals(m.getName()) && p.length == 3) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param param) {
                        try {
                            remember(param.thisObject);
                            if (!sOn || param.getResult() == null || !appWindow(param.args[0])) return;
                            Object copy = cState.newInstance(param.getResult());
                            if (widen(copy)) param.setResult(copy);
                        } catch (Throwable ignored) { }
                    }
                });
            } else if ("getInsetsForWindowMetrics".equals(m.getName()) && p.length == 2) {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param param) {
                        try {
                            remember(param.thisObject);
                            if (!sOn || param.args[1] == null || !appToken(param.args[0])) return;
                            widen(param.args[1]);
                        } catch (Throwable ignored) { }
                    }
                });
            }
        }
        Xp.hookAllMethods(Xp.findClass("com.android.server.policy.PhoneWindowManager", cl), "systemBooted", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param param) {
                try { boot((Context) Xp.getObjectField(param.thisObject, "mContext")); } catch (Throwable ignored) { }
            }
        });
    }

    private static void remember(Object insetsPolicy) {
        try {
            if (fDisplay == null) { fDisplay = insetsPolicy.getClass().getDeclaredField("mDisplayContent"); fDisplay.setAccessible(true); }
            Object dc = fDisplay.get(insetsPolicy);
            if (dc != null && !sDisplays.containsKey(dc)) sDisplays.put(dc, Boolean.TRUE);
        } catch (Throwable ignored) { }
    }

    private static boolean excluded(String pkg) {
        return pkg == null || "com.google.android.apps.nexuslauncher".equals(pkg) || "com.android.systemui".equals(pkg);
    }

    private static Method mHomeOrRecents, mWindowingMode;
    private static boolean sHomeLooked, sModeLooked;

    private static boolean homeOrRecents(Object activity) {
        try {
            if (!sHomeLooked) { sHomeLooked = true; mHomeOrRecents = method(activity.getClass(), "isActivityTypeHomeOrRecents"); }
            return mHomeOrRecents != null && Boolean.TRUE.equals(mHomeOrRecents.invoke(activity));
        } catch (Throwable ignored) { return false; }
    }

    private static Method method(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Method m = k.getDeclaredMethod(name); m.setAccessible(true); return m; } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static boolean appWindow(Object w) throws Exception {
        if (fAttrs == null) { fAttrs = field(w.getClass(), "mAttrs"); fActivity = field(w.getClass(), "mActivityRecord"); }
        WindowManager.LayoutParams a = (WindowManager.LayoutParams) fAttrs.get(w);
        if (a == null) return false;
        int type = a.type;
        if (!((type >= 1 && type <= 99) || (type >= 1000 && type <= 1999))) return false;
        Object activity = fActivity.get(w);
        if (activity == null || excluded(a.packageName) || homeOrRecents(activity)) return false;
        try {
            if (!sModeLooked) { sModeLooked = true; mWindowingMode = method(w.getClass(), "getWindowingMode"); }
            if (mWindowingMode != null) { int mode = (Integer) mWindowingMode.invoke(w); if (mode == 2 || mode == 5) return false; }
        } catch (Throwable ignored) { }
        return true;
    }

    private static boolean appToken(Object token) {
        try {
            if (token == null) return false;
            Object activity = Xp.callMethod(token, "asActivityRecord");
            if (activity == null || homeOrRecents(activity)) return false;
            Object pkg = Xp.getObjectField(activity, "packageName");
            return !excluded(pkg instanceof String ? (String) pkg : null);
        } catch (Throwable t) { return false; }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean widen(Object state) throws Exception {
        int strip = sStripPx;
        Rect display = (Rect) mDisplayFrame.invoke(state);
        if (strip <= 0 || display == null || display.isEmpty()) return false;
        int n = (Integer) mSourceSize.invoke(state);
        Object nav = null; Rect navFrame = null;
        for (int i = 0; i < n; i++) {
            Object s = mSourceAt.invoke(state, i);
            if ((Integer) mGetType.invoke(s) != sNavType) continue;
            Rect f = (Rect) mGetFrame.invoke(s);
            if (f == null || f.isEmpty() || f.bottom < display.bottom || f.width() < display.width() * 9 / 10) continue;
            nav = s; navFrame = f; break;
        }
        if (nav == null || navFrame.height() >= strip) return false;
        int owner = ((Integer) mGetId.invoke(nav)) >> 16;
        Rect wide = new Rect(navFrame.left, navFrame.bottom - strip, navFrame.right, navFrame.bottom);
        List<Object> tappable = new ArrayList<Object>();
        for (int i = 0; i < n; i++) {
            Object s = mSourceAt.invoke(state, i);
            if ((Integer) mGetType.invoke(s) == sTappableType && ((Integer) mGetId.invoke(s)) >> 16 == owner) tappable.add(s);
        }
        put(state, nav, wide);
        for (Object s : tappable) put(state, s, wide);
        return true;
    }

    private static void put(Object state, Object source, Rect frame) throws Exception {
        Object copy = cSource.newInstance(source);
        mSetFrame.invoke(copy, new Rect(frame));
        if (mVisibleFrame != null && mVisibleFrame.invoke(copy) != null) mSetVisibleFrame.invoke(copy, new Rect(frame));
        mAddSource.invoke(state, copy);
    }

    private static synchronized void boot(Context c) {
        if (sBooted || c == null) return;
        sBooted = true;
        HandlerThread t = new HandlerThread("PixelSuiteNavStrip");
        t.start();
        sWorker = new Handler(t.getLooper());
        final ContentResolver cr = c.getContentResolver();
        final Runnable read = new Runnable() { public void run() { read(cr); } };
        ContentObserver o = new ContentObserver(sWorker) { @Override public void onChange(boolean self) { read.run(); } };
        for (String k : new String[]{NavButtons.KEY, NavButtons.LEFT, NavButtons.RIGHT, "navigation_mode"}) {
            try { cr.registerContentObserver(Settings.Secure.getUriFor(k), false, o); } catch (Throwable ignored) { }
        }
        sWorker.post(read);
    }

    private static void read(ContentResolver cr) {
        boolean on;
        try {
            String left = Settings.Secure.getString(cr, NavButtons.LEFT), right = Settings.Secure.getString(cr, NavButtons.RIGHT);
            boolean bothHidden = "hide".equals(left) && "hide".equals(right);
            on = "1".equals(Settings.Secure.getString(cr, NavButtons.KEY)) && !bothHidden && Settings.Secure.getInt(cr, "navigation_mode", 0) == 2;
        } catch (Throwable t) { on = false; }
        if (sStripPx <= 0) sStripPx = strip();
        if (on == sOn) return;
        sOn = on;
        push();
    }

    private static int strip() {
        Resources r = Resources.getSystem();
        int px = 0;
        try { int id = r.getIdentifier("navigation_bar_frame_height", "dimen", "android"); if (id != 0) px = r.getDimensionPixelSize(id); } catch (Throwable ignored) { }
        return Math.max(px, Math.round(48 * r.getDisplayMetrics().density));
    }

    private static void push() {
        Object[] displays;
        synchronized (sDisplays) { displays = sDisplays.keySet().toArray(); }
        for (Object dc : displays) {
            try {
                Object wms = Xp.getObjectField(dc, "mWmService");
                Object lock = Xp.getObjectField(wms, "mGlobalLock");
                synchronized (lock) {
                    Xp.callMethod(Xp.callMethod(dc, "getInsetsStateController"), "notifyInsetsChanged");
                    try { Xp.callMethod(dc, "setLayoutNeeded"); } catch (Throwable ignored) { }
                    try { Xp.callMethod(Xp.getObjectField(wms, "mWindowPlacerLocked"), "requestTraversal"); } catch (Throwable ignored) { }
                }
            } catch (Throwable ignored) { }
        }
    }
}
