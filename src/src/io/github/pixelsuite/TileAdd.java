package io.github.pixelsuite;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class TileAdd {
    private TileAdd() {}

    private static final int CHROME_RULE = 8;
    private static WeakReference<Object> sChromeListener = new WeakReference<Object>(null);
    private static Object sIts, sFactory;
    private static Method sMake, sAdd;
    private static int sMax = -1;
    private static boolean sAddHooked;

    static void listener(Object chromeTileListener) {
        if (chromeTileListener != null && sChromeListener.get() == null) sChromeListener = new WeakReference<Object>(chromeTileListener);
    }

    private static int max(Context c) {
        if (sMax < 0) {
            sMax = 0;
            try { c.getApplicationContext().getSharedPreferences("pixelsuite_chrome_ui", Context.MODE_PRIVATE).edit().remove("pin_max").remove("pin_max_r218").apply(); } catch (Throwable ignored) { }
        }
        return ChromeSpeedDial.COLUMNS * ChromeSpeedDial.ROWS - 1;
    }

    static void ensure(ViewGroup grid) {
        try {
            if (!ChromeSpeedDial.on(grid, true)) return;
            int pinned = 0;
            boolean hasAdd = false;
            for (int i = 0; i < grid.getChildCount(); i++) {
                View c = grid.getChildAt(i);
                if (!c.getClass().getName().endsWith("SuggestionsTileView")) continue;
                if (suggestion(c) == null) hasAdd = true;
                else if (TileDrag.pinned(c)) pinned++;
            }
            int folders = 0;
            for (int i = 0; i < grid.getChildCount(); i++) if (ChromeFolders.isFolder(grid.getChildAt(i))) folders++;
            int shown = 0;
            for (View v : ChromeSpeedDial.order(grid)) if (ChromeFolders.isFolder(v) || TileDrag.pinned(v)) shown++;
            if (hasAdd) {
                if (sAbsent) { sAbsent = false; for (int i = 0; i < grid.getChildCount(); i++) { View c = grid.getChildAt(i); if (c.getClass().getName().endsWith("SuggestionsTileView") && suggestion(c) == null) appear(c); } }
                return;
            }
            Context ctx = grid.getContext();
            if (pinned < CHROME_RULE) { return; }
            if (shown >= max(ctx)) { sAbsent = true;  return; }
            if (!resolve()) { return; }
            View add = (View) sMake.invoke(sFactory, args(sMake, grid));
            if (add == null) { return; }
            grid.addView(add);
            if (sAbsent) { sAbsent = false; appear(add); }
        } catch (Throwable t) {
        }
    }

    private static boolean sAbsent;

    private static void appear(final View add) {
        add.setAlpha(0f);
        add.setScaleX(0.6f);
        add.setScaleY(0.6f);
        add.post(new Runnable() { public void run() {
            add.animate().cancel();
            add.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(320)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)).withEndAction(new Runnable() { public void run() { add.setAlpha(1f); add.setScaleX(1f); add.setScaleY(1f); } }).start();
        } });
    }


    private static boolean resolve() throws Exception {
        if (sMake != null && sIts != null && sFactory != null) return true;
        Object listener = sChromeListener.get();
        if (listener == null) return false;
        ClassLoader cl = listener.getClass().getClassLoader();
        Class<?> gurl = Class.forName("org.chromium.url.GURL", false, cl);
        Class<?> layout = Class.forName("org.chromium.chrome.browser.suggestions.tile.MostVisitedTilesLayout", false, cl);
        Class<?> tile = Class.forName("org.chromium.chrome.browser.suggestions.tile.SuggestionsTileView", false, cl);
        java.util.ArrayList<Object> seen = new java.util.ArrayList<Object>();
        java.util.ArrayList<Object> level = new java.util.ArrayList<Object>();
        level.add(listener);
        for (int depth = 0; depth < 5 && (sIts == null || sFactory == null) && seen.size() < 600; depth++) {
            java.util.ArrayList<Object> next = new java.util.ArrayList<Object>();
            for (Object o : level) {
                if (o == null || seen.contains(o)) continue;
                seen.add(o);
                if (o.getClass().getName().startsWith("java.") || o.getClass().getName().startsWith("android.")) continue;
                if (sIts == null) { Method add = addMethod(o.getClass(), gurl); if (add != null) { sIts = o; sAdd = add; } }
                if (sFactory == null) { Method make = makeMethod(o.getClass(), tile, layout); if (make != null) { sFactory = o; sMake = make; } }
                if (depth < 4) for (Field f : fields(o.getClass())) { try { Object v = f.get(o); if (v != null) next.add(v); } catch (Throwable ignored) { } }
            }
            level = next;
        }
        if (sIts != null) hookAdd();
        return sIts != null && sFactory != null && sMake != null;
    }

    private static Method addMethod(Class<?> c, Class<?> gurl) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            for (Method m : k.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class) continue;
                Class<?>[] t = m.getParameterTypes();
                if (t.length < 2 || t.length > 4) continue;
                boolean str = false, url = false;
                for (Class<?> x : t) { if (x == String.class) str = true; if (x == gurl) url = true; }
                if (str && url) { m.setAccessible(true); return m; }
            }
        return null;
    }

    private static Method makeMethod(Class<?> c, Class<?> tile, Class<?> layout) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            for (Method m : k.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || !tile.isAssignableFrom(m.getReturnType())) continue;
                Class<?>[] t = m.getParameterTypes();
                boolean lay = false;
                boolean site = false;
                for (Class<?> x : t) { if (x.isAssignableFrom(layout) && x != Object.class) lay = true; if (x.getName().endsWith("SiteSuggestion") || x.getName().endsWith("Tile")) site = true; }
                if (site) continue;
                boolean handler = sIts == null;
                for (Class<?> x : t) if (sIts != null && x != Object.class && x.isInstance(sIts)) handler = true;
                if (!handler) continue;
                if (lay && t.length <= 3) { m.setAccessible(true); return m; }
            }
        return null;
    }

    private static Object[] args(Method m, ViewGroup grid) {
        Class<?>[] t = m.getParameterTypes();
        Object[] a = new Object[t.length];
        for (int i = 0; i < t.length; i++) {
            if (t[i].isInstance(grid)) a[i] = grid;
            else if (sIts != null && t[i].isInstance(sIts)) a[i] = sIts;
            else if (t[i] == boolean.class) a[i] = Boolean.FALSE;
            else if (t[i] == int.class) a[i] = 0;
            else a[i] = null;
        }
        return a;
    }

    private static int sInAdd;
    private static final java.util.HashSet<Method> sChecks = new java.util.HashSet<Method>();

    private static void hookAdd() {
        if (sAddHooked || sAdd == null) return;
        sAddHooked = true;
        Xp.hookAllMethods(sAdd.getDeclaringClass(), sAdd.getName(), new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { sInAdd++; }
            @Override protected void afterHookedMethod(Xp.Param p) {
                sInAdd = Math.max(0, sInAdd - 1);
                try {
                    View grid = TileDrag.lastGrid();
                    if (grid == null) return;
                    if (Boolean.FALSE.equals(p.getResult()) && p.args.length >= 2 && p.args[0] instanceof String && p.args[1] != null) {
                        int pinned = 0;
                        ViewGroup g = (ViewGroup) grid;
                        for (int i = 0; i < g.getChildCount(); i++) if (TileDrag.pinned(g.getChildAt(i))) pinned++;
                        String name = ((String) p.args[0]).trim();
                        Integer pos = p.args.length > 2 && p.args[2] instanceof Integer ? (Integer) p.args[2] : null;
                        if (pinned >= CHROME_RULE && !name.isEmpty() && name.length() <= 50 && ChromeFolders.addSingle(g, name, p.args[1], pos)) {
                            p.setResult(Boolean.TRUE);
                            ensure(g);
                        }
                    }
                } catch (Throwable ignored) { }
            }
        });
        java.util.ArrayList<Class<?>> classes = new java.util.ArrayList<Class<?>>();
        classes.add(sIts.getClass());
        for (Field f : fields(sIts.getClass())) {
            try {
                Object o = f.get(sIts);
                if (o == null) continue;
                String n = o.getClass().getName();
                if (n.startsWith("java.") || n.startsWith("android.") || n.startsWith("androidx.") || classes.contains(o.getClass())) continue;
                classes.add(o.getClass());
            } catch (Throwable ignored) { }
        }
        Xp.Callback room = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (sInAdd <= 0 || !Boolean.FALSE.equals(p.getResult())) return;
                p.setResult(Boolean.TRUE);

            }
        };
        for (Class<?> k : classes)
            for (Method m : k.getDeclaredMethods()) {
                if (m.getReturnType() != boolean.class || m.getParameterTypes().length != 0 || Modifier.isAbstract(m.getModifiers()) || Modifier.isNative(m.getModifiers())) continue;
                if (sChecks.add(m)) try { Xp.hookMethod(m, room); } catch (Throwable ignored) { }
            }
    }

    private static Object suggestion(View tile) {
        for (Class<?> k = tile.getClass(); k != null && k != View.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!f.getType().getName().endsWith(".SiteSuggestion")) continue;
                try { f.setAccessible(true); return f.get(tile); } catch (Throwable t) { return null; }
            }
        }
        return null;
    }

    private static java.util.List<Field> fields(Class<?> c) {
        java.util.List<Field> out = new java.util.ArrayList<Field>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try { f.setAccessible(true); out.add(f); } catch (Throwable ignored) { }
            }
        }
        return out;
    }

}
