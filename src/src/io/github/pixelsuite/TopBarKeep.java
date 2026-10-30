package io.github.pixelsuite;

import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TopBarKeep {
    private TopBarKeep() {}

    private static final Map<Class<?>, List<Field>> pagePath = new HashMap<Class<?>, List<Field>>();
    private static final Map<Class<?>, Field> pageField = new HashMap<Class<?>, Field>();

    static void install(ClassLoader cl) {
        try {
            String names = StudioNames.get("touch"), m = StudioNames.get("touch.m");
            if (names == null || m == null) { return; }
            for (String name : names.split("\\|")) {
                try {
                    for (java.lang.reflect.Constructor<?> c : Class.forName(name, false, cl).getDeclaredConstructors()) {
                        Xp.hookMethod(c, new Xp.Callback() {
                            @Override protected void afterHookedMethod(Xp.Param p) {
                                Object was = last.get();
                                if (was == null || was.getClass() == p.thisObject.getClass()) { last = new java.lang.ref.WeakReference<Object>(p.thisObject); statePage = null; }
                                watchSoon(p.thisObject, 0);
                            }
                        });
                    }
                } catch (Throwable missing) { }
                try {
                    Method touch = Class.forName(name, false, cl).getDeclaredMethod(m, MotionEvent.class);
                    Xp.hookMethod(touch, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param p) {
                            try {
                                MotionEvent e = (MotionEvent) p.args[0];
                                last = new java.lang.ref.WeakReference<Object>(p.thisObject);
                                touchedAt = android.os.SystemClock.uptimeMillis();
                                String page = page(p.thisObject);
                                CropRemember.pageSeen(page);
                                watchCopies(stateOf(p.thisObject));
                                if (e == null || !"CROP_TOOL".equals(page)) return;
                                CROP.set(Boolean.TRUE);
                            } catch (Throwable error) { }
                        }
                        @Override protected void afterHookedMethod(Xp.Param p) { CROP.set(null); }
                    });
                } catch (Throwable missing) { }
            }
        } catch (Throwable error) { }
    }

    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    private static void watchSoon(final Object viewModel, final int attempt) {
        try {
            Object state = viewModel == null ? null : stateOf(viewModel);
            if (state != null) { watchCopies(state); return; }
        } catch (Throwable ignored) { }
        if (attempt >= 20 || viewModel == null) return;
        main.postDelayed(new Runnable() { @Override public void run() { watchSoon(viewModel, attempt + 1); } }, 50);
    }

    static final ThreadLocal<Boolean> CROP = new ThreadLocal<Boolean>();
    private static Class<?> watched;

    static Object stateOf(Object viewModel) {
        Class<?> k = viewModel.getClass();
        List<Field> path; synchronized (pagePath) { path = pagePath.get(k); }
        if (path == null) { page(viewModel); synchronized (pagePath) { path = pagePath.get(k); } }
        if (path == null) return null;
        Object o = viewModel;
        try { for (Field f : path) { if (o == null) return null; o = f.get(o); } } catch (Throwable t) { return null; }
        return o;
    }

    static synchronized void watchCopies(Object state) {
        if (state == null || watched == state.getClass()) return;
        Class<?> k = state.getClass(); watched = k;
        for (Method m : k.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != k || t.length < 20 || t[0] != k) continue;
            if (t[4] != boolean.class || t[t.length - 2] != int.class) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    Object mask = p.args[p.args.length - 2];
                    try { if (mask instanceof Integer && ((Integer) mask & 1) == 0 && p.args[1] instanceof Enum) { } } catch (Throwable ignored) { }
                    if (!Boolean.TRUE.equals(CROP.get())) return;
                    if (mask instanceof Integer && (Integer) mask == ~8) p.args[4] = Boolean.TRUE;
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        String pg = pageOfState(p.getResult());
                        if (pg != null) { statePage = pg; stateAt = android.os.SystemClock.uptimeMillis(); }
                        CropRemember.pageSeen(pg); CaptionDeselect.pageSet(pg); TextTool.pageSet(pg);
                        if (pg != null && !"STROKE_TOOL".equals(pg)) StudioToolbar.close();
                        if (pg != null && !"READY".equals(pg) && !"INITIAL".equals(pg) && DoodleOverlay.active()
                                && ScreenshotEditor.lastDownAt > ScreenshotEditor.openedAt) StudioToolbar.otherMenu();
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    static volatile java.lang.ref.WeakReference<Object> last = new java.lang.ref.WeakReference<Object>(null);
    private static volatile String statePage;
    private static volatile long stateAt, touchedAt;
    static String currentPage() {
        Object vm = last.get();
        String fromVm = vm == null ? null : page(vm), fromState = statePage;
        if (fromState != null && (fromVm == null || stateAt >= touchedAt)) return fromState;
        return fromVm;
    }

    static String page(Object viewModel) {
        Class<?> k = viewModel.getClass();
        List<Field> path;
        synchronized (pagePath) {
            path = pagePath.get(k);
            if (path == null) { path = find(viewModel, new ArrayList<Field>(), 0); if (path != null) pagePath.put(k, path); }
        }
        if (path == null) return null;
        Object o = viewModel;
        try { for (Field f : path) { if (o == null) return null; o = f.get(o); } } catch (Throwable t) { return null; }
        if (o == null) return null;
        Field pf = pageOf(o.getClass());
        if (pf == null) return null;
        try { Object v = pf.get(o); return v instanceof Enum ? ((Enum<?>) v).name() : null; } catch (Throwable t) { return null; }
    }

    private static List<Field> find(Object o, List<Field> path, int depth) {
        if (o == null || depth > 3) return null;
        if (depth > 0 && pageOf(o.getClass()) != null) return path;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith("java.") || c.getName().startsWith("android.")) break;
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                Object v;
                try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                if (v == null || v instanceof android.content.Context || v instanceof android.view.View) continue;
                String vn = v.getClass().getName();
                if (vn.startsWith("java.") && !vn.startsWith("java.util.concurrent.atomic.")) continue;
                if (vn.startsWith("android.") || vn.startsWith("kotlin.")) continue;
                List<Field> next = new ArrayList<Field>(path); next.add(f);
                List<Field> r = find(v, next, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    static String pageOfState(Object state) {
        if (state == null) return null;
        Field pf = pageOf(state.getClass());
        if (pf == null) return null;
        try { Object v = pf.get(state); return v instanceof Enum ? ((Enum<?>) v).name() : null; } catch (Throwable t) { return null; }
    }

    private static Field pageOf(Class<?> k) {
        synchronized (pageField) {
            if (pageField.containsKey(k)) return pageField.get(k);
            Field found = null;
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !f.getType().isEnum()) continue;
                for (Object c : f.getType().getEnumConstants()) if ("CROP_TOOL".equals(((Enum<?>) c).name())) { found = f; break; }
                if (found != null) break;
            }
            if (found != null) found.setAccessible(true);
            pageField.put(k, found);
            return found;
        }
    }
}
