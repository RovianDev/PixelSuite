package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.lang.reflect.Field;

final class GoogleFeedSwipe {
    private GoogleFeedSwipe() {}

    private static Class<?> sLauncherType;
    private static volatile long sPausedAt = -1, sResumedAt = -1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final class Touch {
        long t0; float x0, y0, x, y; boolean ready, multi, engaged, panel, old, restarted, ended; VelocityTracker v; float feedLast = -1, feedMax = -1;
        MotionEvent first;
    }
    private static Touch sTouch;
    private static volatile long sLastDown = -1;
    private static final java.util.Set<Class<?>> sReceivers = new java.util.HashSet<Class<?>>();

    static void install(ClassLoader cl) throws Throwable {
        sLauncherType = cl.loadClass("com.android.launcher3.Launcher");
        Xp.hookAllMethods(cl.loadClass("com.android.launcher3.views.BaseDragLayer"), "dispatchTouchEvent", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try { if (p.args.length == 1 && p.args[0] instanceof MotionEvent) touched((View) p.thisObject, (MotionEvent) p.args[0]); } catch (Throwable t) { Log.w("google feed swipe", t); }
            }
        });

        Xp.hookAllMethods(sLauncherType, "setLauncherOverlay", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    Object o = p.args.length > 0 ? p.args[0] : null;
                    if (o != null && !java.lang.reflect.Proxy.isProxyClass(o.getClass())) { sManagerNow = o; receiver(o.getClass()); monitorReceiver(o.getClass()); }
                } catch (Throwable ignored) { }
            }
        });

        try {
            Xp.hookAllMethods(Class.forName("android.view.InputMonitor"), "pilferPointers", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (sMonSynth) return;
                    if (sMonBlock) { p.setResult(null); return; }
                    if (!sDriving) return;
                    p.setResult(null);
                }
            });
        } catch (Throwable ignored) { }
        visibility(sLauncherType);
        Xp.hookAllMethods(sLauncherType, "onCreate", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { visibility(p.thisObject.getClass()); }
        });
        Xp.hookAllMethods(Activity.class, "onPause", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { if (sLauncherType.isInstance(p.thisObject)) sPausedAt = SystemClock.uptimeMillis(); }
        });
        Xp.hookAllMethods(Activity.class, "onResume", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { if (sLauncherType.isInstance(p.thisObject)) sResumedAt = SystemClock.uptimeMillis(); }
        });
    }

    private static Field sSession, sAnimator, sAnimated;
    private static java.lang.reflect.Method sMotion;
    private static volatile Object sManagerNow;
    private static boolean sSynth;

    private static boolean sDriving;

    private static volatile boolean sFeedShown;
    private static final java.util.Set<Class<?>> sVisibility = new java.util.HashSet<Class<?>>();

    private static void receiver(Class<?> k) {
        synchronized (sReceivers) { if (!sReceivers.add(k)) return; }
        findSession(k);
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                String name = m.getName();
                boolean motion = "onOverlayMotionEvent".equals(name), fling = "onFlingVelocity".equals(name);
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;

                if (sSession != null && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == sSession.getType() && c == k) {
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void afterHookedMethod(Xp.Param p) { if (p.args[0] == null) cleared(p.thisObject); }
                    });
                    continue;
                }

                if ("onOverlayScrollChanged".equals(name) && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == float.class) {
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param p) {
                            float v = (Float) p.args[0];
                            sScrollDelta = v - sScroll; sScroll = v; sScrollAt = SystemClock.uptimeMillis();
                        }
                    });
                    continue;
                }
                if (!(motion || fling)) continue;
                final boolean isMotion = motion;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) { if (!sSynth) sDriving = false; }
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (sSynth) return;
                        Touch t = sTouch;

                        if (t == null) { if (sWidgetsHeld) { p.setResult(null); } return; }
                        sDriving = !t.panel;

                        if (t.panel) { p.setResult(null); return; }
                        if (isMotion && sMotion == null && p.method instanceof java.lang.reflect.Method) sMotion = (java.lang.reflect.Method) p.method;
                        if (isMotion && t.first == null && p.args.length > 0 && p.args[0] instanceof MotionEvent) {
                            t.first = MotionEvent.obtain((MotionEvent) p.args[0]);
                            sManagerNow = p.thisObject;
                            Object old = session(p.thisObject);
                            t.old = old != null;
                            if (t.old) endSlideOut(old);
                        }
                        t.engaged = true;
                        if (p.args.length < 2 || !(p.args[1] instanceof Float)) return;
                        float d = (Float) p.args[1]; t.feedLast = d; if (d > t.feedMax) t.feedMax = d;
                    }
                });
            }
        }
    }

    private static final class Mon {
        float x0, y0; int meta, source; long t0, at, upAt; Object receiver; java.lang.reflect.Method method; Handler handler;
        boolean down, sessionAtDown, pending, restarted, multi;
        final long[] t = new long[96]; final float[] x = new float[96], y = new float[96]; int n;
        void add(MotionEvent e) { if (n == t.length) { System.arraycopy(t, 1, t, 0, n - 1); System.arraycopy(x, 1, x, 0, n - 1); System.arraycopy(y, 1, y, 0, n - 1); n--; } t[n] = e.getEventTime(); x[n] = e.getX(); y[n] = e.getY(); n++; }
    }
    private static volatile Mon sMon;
    private static boolean sMonSynth, sMonBlock;

    private static boolean sWidgetsHeld;
    private static boolean sMonHooked;

    private static void monitorReceiver(Class<?> manager) {
        synchronized (sReceivers) { if (sMonHooked) return; sMonHooked = true; }
        String pkg = manager.getName().substring(0, manager.getName().lastIndexOf('.') + 1);
        for (char c = 'a'; c <= 'z'; c++) {
            Class<?> k;
            try { k = Class.forName(pkg + c, false, manager.getClassLoader()); } catch (Throwable notThere) { continue; }
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (!"onInputEvent".equals(m.getName()) || t.length != 1 || t[0] != android.view.InputEvent.class || java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                m.setAccessible(true);
                final java.lang.reflect.Method method = m;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        sMonBlock = false;
                        if (sMonSynth || !(p.args[0] instanceof MotionEvent)) return;
                        try { monitorEvent(p.thisObject, method, (MotionEvent) p.args[0]); }
                        catch (Throwable ignored) { }
                    }
                    @Override protected void afterHookedMethod(Xp.Param p) { sMonBlock = false; }
                });
            }
        }
    }

    private static void monitorEvent(Object receiver, java.lang.reflect.Method method, MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            Mon m = new Mon();
            m.receiver = receiver; m.method = method; m.down = true;
            m.x0 = e.getX(); m.y0 = e.getY(); m.meta = e.getMetaState(); m.source = e.getSource(); m.t0 = e.getEventTime();
            m.at = SystemClock.uptimeMillis();
            Looper l = Looper.myLooper(); m.handler = l != null ? new Handler(l) : MAIN;
            Object mgr = sManagerNow; m.sessionAtDown = mgr != null && session(mgr) != null;
            m.add(e);
            sMon = m;
            if (m.sessionAtDown && leavingNow(mgr)) return;
            if (m.sessionAtDown && !sFeedShown) settleNow(m);
            return;
        }
        Mon m = sMon; if (m == null || m.receiver != receiver) return;
        if (a == MotionEvent.ACTION_POINTER_DOWN) { m.multi = true; m.pending = false; return; }
        if (a == MotionEvent.ACTION_MOVE) {
            m.add(e);
            if (m.pending) pendingMove(m, e);
            return;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (a == MotionEvent.ACTION_UP) m.add(e);
            m.down = false; m.pending = false; m.upAt = SystemClock.uptimeMillis();
            if (a == MotionEvent.ACTION_CANCEL) m.restarted = true;
        }
    }

    private static void settleNow(final Mon m) {
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                if (sMon != m || !m.down || sFeedShown || sTouch != null || Math.abs(sLastDown - m.at) < 80) return;
                Object mgr = sManagerNow, w = mgr != null ? session(mgr) : null;
                if (w == null || sAnimated == null || sAnimator == null) return;
                try {
                    Object af = sAnimated.get(w); Object anim = af != null ? sAnimator.get(af) : null;
                    if (anim instanceof android.animation.ValueAnimator && ((android.animation.ValueAnimator) anim).isRunning()) {
                        ((android.animation.ValueAnimator) anim).end();
                    }
                } catch (Throwable ignored) { }
            }
        }, 16);
    }

    private static void slideOutNow(final Touch t) {
        if (sAnimated == null || sAnimator == null) return;
        MAIN.post(new Runnable() {
            @Override public void run() {
                Object mgr = sManagerNow, w = mgr != null ? session(mgr) : null;
                if (sTouch != t || t.ended || !sFeedShown || w == null) return;
                try {
                    Object af = sAnimated.get(w); Object anim = af != null ? sAnimator.get(af) : null;
                    if (anim instanceof android.animation.ValueAnimator && ((android.animation.ValueAnimator) anim).isRunning()) {
                        ((android.animation.ValueAnimator) anim).end();
                    }
                } catch (Throwable ignored) { }
            }
        });
    }

    private static Field sEndValue; private static boolean sEndLooked;

    private static volatile float sScroll, sScrollDelta; private static volatile long sScrollAt;
    private static boolean leavingNow(Object mgr) {
        Object w = mgr != null ? session(mgr) : null;
        if (w == null || sAnimated == null || sAnimator == null) return false;
        try {
            Object af = sAnimated.get(w); if (af == null) return false;
            Object anim = sAnimator.get(af);
            if (!(anim instanceof android.animation.ValueAnimator) || !((android.animation.ValueAnimator) anim).isRunning()) return false;
            if (!sEndLooked) {
                sEndLooked = true;
                for (Class<?> c = af.getClass(); c != null && c != Object.class && sEndValue == null; c = c.getSuperclass())
                    for (Field f : c.getDeclaredFields()) if (f.getType() == Float.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); sEndValue = f; break; }
            }
            Object end = sEndValue != null ? sEndValue.get(af) : null;

            boolean fresh = SystemClock.uptimeMillis() - sScrollAt < 80;
            boolean falling = fresh && sScrollDelta < 0f, rising = fresh && sScrollDelta > 0f;
            boolean leaving = falling || (!rising && end instanceof Float && (Float) end < 0.5f);
            if (!leaving) return false;
            sMonBlock = true;
            ((android.animation.ValueAnimator) anim).end();
            return true;
        } catch (Throwable t) { return false; }
    }

    private static int homeDir() {
        try { return android.content.res.Resources.getSystem().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1; }
        catch (Throwable t) { return 1; }
    }

    private static float dp() { try { return android.content.res.Resources.getSystem().getDisplayMetrics().density; } catch (Throwable t) { return 3f; } }

    private static void pendingMove(Mon m, MotionEvent e) {
        float d = dp();
        float back = (m.x0 - e.getX()) * homeDir(), dy = Math.abs(e.getY() - m.y0);
        if (dy >= 12 * d && dy > Math.abs(back)) { m.pending = false; return; }
        if (back < 12 * d || back < 1.5f * dy) return;
        m.pending = false;
        Object mgr = sManagerNow;
        if (!sFeedShown || mgr == null || session(mgr) != null) return;

        MotionEvent down = MotionEvent.obtain(m.t0, m.t0, MotionEvent.ACTION_DOWN, m.x0, m.y0, m.meta);
        down.setSource(m.source);
        handTo(m, down);
        long now = e.getEventTime();
        for (int i = 1; i < m.n - 1; i++) {
            if (now - m.t[i] > 120) continue;
            MotionEvent mv = MotionEvent.obtain(m.t0, m.t[i], MotionEvent.ACTION_MOVE, m.x[i], m.y[i], m.meta);
            mv.setSource(m.source);
            handTo(m, mv);
        }
    }

    private static void handTo(Mon m, MotionEvent e) {
        sMonSynth = true;
        try { m.method.invoke(m.receiver, e); }
        catch (Throwable ignored) { }
        finally { sMonSynth = false; try { e.recycle(); } catch (Throwable ignored) { } }
    }

    private static void returnRestart(final Object manager) {
        final Mon m = sMon;
        if (m == null || !m.sessionAtDown || m.restarted || m.multi || m.receiver == null || m.method == null) return;

        if (sTouch != null || Math.abs(sLastDown - m.at) < 80) return;
        m.restarted = true;
        if (m.down) { m.pending = true; return; }
        if (SystemClock.uptimeMillis() - m.upAt > 350 || m.n < 2) return;

        float d = dp();
        int last = m.n - 1;
        float back = (m.x0 - m.x[last]) * homeDir(), dy = Math.abs(m.y[last] - m.y0);
        int from = last; while (from > 1 && m.t[last] - m.t[from - 1] <= 100) from--;
        float dt = Math.max(1, m.t[last] - m.t[from == last ? last - 1 : from]) / 1000f;
        float speed = (m.x[from == last ? last - 1 : from] - m.x[last]) * homeDir() / dt / d;
        boolean meant = back >= 1.5f * dy && (back >= 64 * d || (back >= 32 * d && speed >= 800));
        if (!meant) { return; }
        final int first = from;
        m.handler.post(new Runnable() {
            @Override public void run() {
                if (sMon != m || !sFeedShown || session(manager) != null) return;
                long shift = SystemClock.uptimeMillis() - m.t[m.n - 1];
                long down = m.t0 + shift;
                MotionEvent ev = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, m.x0, m.y0, m.meta); ev.setSource(m.source);
                handTo(m, ev);
                for (int i = Math.max(1, first); i < m.n; i++) {
                    boolean up = i == m.n - 1;
                    ev = MotionEvent.obtain(down, m.t[i] + shift, up ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, m.x[i], m.y[i], m.meta); ev.setSource(m.source);
                    handTo(m, ev);
                }
            }
        });
    }

    private static int visibility(Class<?> k) {
        synchronized (sVisibility) { if (!sVisibility.add(k)) return 0; }
        Xp.Callback cb = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { if (p.args.length == 1 && p.args[0] instanceof Boolean) sFeedShown = (Boolean) p.args[0]; }
        };
        int n = 0;
        for (Class<?> c = k; c != null && c != Object.class && n == 0; c = c.getSuperclass())
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("onOverlayVisibilityChanged") || java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                try { Xp.hookMethod(m, cb); n++; } catch (Throwable ignored) { }
            }
        return n;
    }

    private static void findSession(Class<?> k) {
        try {
            Class<?> animated = k.getClassLoader().loadClass("com.android.launcher3.anim.AnimatedFloat");
            for (Field f : k.getDeclaredFields()) {
                Class<?> type = f.getType();
                if (type.isPrimitive() || type.getName().startsWith("java") || type.getName().startsWith("android")) continue;
                for (Class<?> c = type; c != null && c != Object.class && sAnimated == null; c = c.getSuperclass())
                    for (Field g : c.getDeclaredFields()) if (g.getType() == animated) { g.setAccessible(true); sAnimated = g; break; }
                if (sAnimated != null) { f.setAccessible(true); sSession = f; break; }
            }
            for (Class<?> c = animated; c != null && c != Object.class && sAnimator == null; c = c.getSuperclass())
                for (Field g : c.getDeclaredFields()) if (android.animation.ValueAnimator.class.isAssignableFrom(g.getType())) { g.setAccessible(true); sAnimator = g; break; }
        } catch (Throwable ignored) { }
    }

    private static Object session(Object manager) {
        try { return sSession == null ? null : sSession.get(manager); } catch (Throwable t) { return null; }
    }

    private static void endSlideOut(Object old) {
        try {
            if (sAnimated == null || sAnimator == null) return;
            Object af = sAnimated.get(old); if (af == null) return;
            Object anim = sAnimator.get(af);
            if (anim instanceof android.animation.ValueAnimator && ((android.animation.ValueAnimator) anim).isRunning()) {
                ((android.animation.ValueAnimator) anim).end();
            }
        } catch (Throwable ignored) { }
    }

    private static void cleared(final Object manager) {
        if (sFeedShown) returnRestart(manager);
        final Touch t = sTouch;
        if (t == null || !t.old || t.restarted || t.panel || t.ended || t.first == null || sMotion == null) return;
        if (sFeedShown) { return; }
        t.restarted = true;
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (sTouch != t || t.ended || t.panel || sFeedShown || session(manager) != null) { return; }
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, t.first.getX(), t.first.getY(), t.first.getMetaState());
                down.setSource(t.first.getSource());
                sSynth = true; sDriving = true;
                try { sMotion.invoke(manager, down, 0f); }
                catch (Throwable ignored) { }
                finally { sSynth = false; sDriving = false; down.recycle(); }
            }
        });
    }

    static boolean googleHasTouch() { Touch t = sTouch; return t != null && t.engaged && !t.panel; }

    static boolean blocksWidgets() {
        if (sScroll <= 0.02f || WidgetPanel.panelShowing()) return false;
        if (!sFeedShown && SystemClock.uptimeMillis() - sScrollAt > 500) return false;
        return !(sScrollDelta < 0f && SystemClock.uptimeMillis() - sScrollAt < 120);
    }

    static void widgetsTookTouch() { Touch t = sTouch; if (t != null) { t.panel = true; } }

    private static Activity activity(Context c) {
        for (int i = 0; i < 8 && c != null; i++) { if (c instanceof Activity) return (Activity) c; c = c instanceof ContextWrapper ? ((ContextWrapper) c).getBaseContext() : null; }
        return null;
    }

    private static void touched(View layer, MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            sLastDown = SystemClock.uptimeMillis(); sDriving = false;
            Touch old = sTouch; sTouch = null; if (old != null) { old.ended = true; if (old.v != null) old.v.recycle(); if (old.first != null) { old.first.recycle(); old.first = null; } }
            Activity a = activity(layer.getContext());
            if (a == null || !sLauncherType.isInstance(a)) return;
            try { WidgetPanel.slideAwayNow(); } catch (Throwable ignored) { }
            sWidgetsHeld = WidgetPanel.panelShowing();
            if (!eligible(a)) return;
            Touch t = new Touch();
            t.t0 = SystemClock.uptimeMillis(); t.x0 = t.x = e.getRawX(); t.y0 = t.y = e.getRawY();

            t.ready = a.hasWindowFocus() && sResumedAt >= sPausedAt;
            t.v = VelocityTracker.obtain(); t.v.addMovement(e);
            sTouch = t;
            if (sFeedShown) slideOutNow(t);
            return;
        }
        Touch t = sTouch; if (t == null) return;
        if (action == MotionEvent.ACTION_POINTER_DOWN) { t.multi = true; return; }
        boolean cancel = action == MotionEvent.ACTION_CANCEL;
        if (!cancel) { t.x = e.getRawX(); t.y = e.getRawY(); }
        if (t.v != null && !cancel) t.v.addMovement(e);
        if (action != MotionEvent.ACTION_UP && !cancel) return;
        t.ended = true;
        if (t.first != null) { t.first.recycle(); t.first = null; }
        sTouch = null;
        float vx = 0;
        if (t.v != null) { t.v.computeCurrentVelocity(1000); vx = t.v.getXVelocity(); t.v.recycle(); t.v = null; }
        if (t.multi || t.panel) return;
        Activity a = activity(layer.getContext()); if (a == null) return;
        float d = a.getResources().getDisplayMetrics().density;
        int dir = layer.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
        float dx = (t.x - t.x0) * dir, dy = Math.abs(t.y - t.y0), speed = vx * dir;
        long up = SystemClock.uptimeMillis();
        if (!t.ready) {
            boolean meant = dx >= 1.5f * dy && (cancel ? dx >= 32 * d && speed >= 400 * d : dx >= 64 * d || (dx >= 32 * d && speed >= 800 * d));
            if (meant) watch(a, t.t0, up);
            return;
        }

        boolean held = !cancel && dx >= 1.5f * dy && dx >= 64 * d && t.feedLast >= .40f && t.feedLast >= .9f * t.feedMax && speed >= -200 * d;
        if (held) { fellBack(a, up); }
    }

    private static void fellBack(final Activity a, final long up) {
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                long now = SystemClock.uptimeMillis();
                if (sLastDown > up) { return; }
                if (sResumedAt > up) {
                    if (sResumedAt - up > 550) { return; }
                    if (sResumedAt >= sPausedAt && a.hasWindowFocus() && now - sResumedAt >= 80) {
                        if (!eligible(a)) { return; }
                        open(a, "fell back " + (sResumedAt - up) + " ms after the lift, feed opened " + (now - up) + " ms after it");
                        return;
                    }
                }
                if (now - up > 900) { return; }
                MAIN.postDelayed(this, 20);
            }
        }, 20);
    }

    private static void open(Activity a, String why) {
        Object m = manager(a);
        try { Xp.callMethod(m, "openOverlay"); }
        catch (Throwable t) { Log.w("google feed open", t); }
    }

    private static boolean eligible(Activity a) {
        if (WidgetPanel.slotNow(a) != 0 || !WidgetPanel.homeNormal() || WidgetPanel.panelShowing()) return false;
        try {
            Object ws = Xp.callMethod(a, "getWorkspace");
            if (((Number) Xp.callMethod(ws, "getNextPage")).intValue() != 0) return false;
        } catch (Throwable t) { return false; }
        return manager(a) != null;
    }

    private static void watch(final Activity a, final long down, final long up) {
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                long now = SystemClock.uptimeMillis();
                if (sLastDown > up) { return; }
                if (sPausedAt > down) { return; }
                boolean back = sResumedAt >= sPausedAt && a.hasWindowFocus() && !a.isFinishing();
                if (back && now - Math.max(up, sResumedAt) >= 80) {
                    if (!eligible(a)) { return; }
                    open(a, "missed swipe, feed opened " + (now - up) + " ms after the lift");
                    return;
                }
                if (now - up > 900) { return; }
                MAIN.postDelayed(this, 20);
            }
        }, 20);
    }

    private static Field sManager;
    private static Object manager(Activity a) {
        try {
            Field f = sManager;
            if (f == null) {
                Class<?> type = a.getClassLoader().loadClass("com.android.systemui.plugins.shared.LauncherOverlayManager");
                for (Class<?> k = a.getClass(); k != null && k != Activity.class && f == null; k = k.getSuperclass())
                    for (Field x : k.getDeclaredFields()) if (type.isAssignableFrom(x.getType())) { x.setAccessible(true); f = x; break; }
                if (f == null) return null;
                sManager = f;
            }
            return f.get(a);
        } catch (Throwable t) { return null; }
    }
}
