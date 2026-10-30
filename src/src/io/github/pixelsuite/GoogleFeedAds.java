package io.github.pixelsuite;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class GoogleFeedAds {
    private GoogleFeedAds() {}

    static final String PACKAGE = "com.google.android.googlequicksearchbox";
    static final String KEY = "pixelsuite_google_feed_ads";
    static final String[] LABELS = {"Sponsored", "إعلان", "مُموَّل", "ممول", "مموّل", "برعاية", "Sponsorisé", "Gesponsert",
            "Patrocinado", "Sponsorizzato", "Gesponsord", "Sponsorowane", "Sponsorlu", "Спонсорская реклама", "Реклама", "スポンサー", "赞助", "贊助", "스폰서"};

    private static volatile Boolean sOn;
    private static Class<?> sLithoView, sComponentTree;
    private static Field[] sTreeFields, sStateFields;
    private static Class<?> sContextClass;
    private static int sRecyclerId = -1;
    private static final Map<Object, Boolean> sVerdict = new WeakHashMap<Object, Boolean>();
    private static final Map<Object, Integer> sTries = new WeakHashMap<Object, Integer>();
    private static final Map<View, int[]> sCollapsed = new WeakHashMap<View, int[]>();
    private static final Map<Class<?>, Field[]> sFields = new HashMap<Class<?>, Field[]>();
    private static int sChecked, sAds;
    private static volatile boolean sUnsafe;

    static void install(ClassLoader cl) {
        try {
            sLithoView = Class.forName("com.facebook.litho.LithoView", false, cl);
            sComponentTree = Class.forName("com.facebook.litho.ComponentTree", false, cl);
            List<Field> trees = new ArrayList<Field>();
            for (Class<?> k = sLithoView; k != null && k != View.class; k = k.getSuperclass())
                for (Field f : k.getDeclaredFields())
                    if (f.getType() == sComponentTree && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); trees.add(f); }
            sTreeFields = trees.toArray(new Field[0]);
            List<Field> states = new ArrayList<Field>();
            for (Field f : sComponentTree.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().getName().startsWith("java") || View.class.isAssignableFrom(f.getType())) continue;
                boolean layoutState = false;
                for (Field g : f.getType().getDeclaredFields()) if (g.getType() == android.view.accessibility.AccessibilityManager.class) layoutState = true;
                if (layoutState) { f.setAccessible(true); states.add(f); }
            }
            sStateFields = states.toArray(new Field[0]);
            for (java.lang.reflect.Constructor<?> c : sLithoView.getDeclaredConstructors()) {
                Class<?>[] t = c.getParameterTypes();
                if (t.length == 1 && !Context.class.isAssignableFrom(t[0])) sContextClass = t[0];
            }
            Xp.hookAllMethods(sLithoView, "onMeasure", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { measured((View) p.thisObject); } catch (Throwable error) { }
                }
            });
        } catch (Throwable error) { return; }
    }

    static boolean enabled(Context c) { return on(c); }

    private static boolean on(Context c) {
        return switchOn(c) && PatronGate.ok(c.getApplicationContext() != null ? c.getApplicationContext() : c);
    }

    private static boolean switchOn(Context c) {
        Boolean v = sOn;
        if (v != null) return v;
        final Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        v = read(app);
        sOn = v;
        try {
            app.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean self) { sOn = read(app); }
            });
        } catch (Throwable ignored) { }
        return v;
    }
    private static boolean read(Context c) {
        try { return !"0".equals(Settings.Secure.getString(c.getContentResolver(), KEY)); } catch (Throwable t) { return true; }
    }

    private static void measured(View lv) throws Throwable {
        View item = feedItem(lv);
        if (item == null) return;
        boolean ad = on(lv.getContext()) && isAd(lv) && !sUnsafe;
        if (ad) {
            setMeasured(lv, lv.getMeasuredWidth(), 0);
            if (item != lv) collapse(item);
        } else if (item != lv) restore(item);
    }

    private static View feedItem(View v) {
        View child = v;
        ViewParent p = v.getParent();
        for (int depth = 0; p instanceof View && depth < 8; depth++) {
            View pv = (View) p;
            if (isFeed(pv)) return child;
            child = pv;
            p = pv.getParent();
        }
        return null;
    }
    private static boolean isFeed(View v) {
        if (sRecyclerId == -1) {
            try { Resources r = v.getResources(); sRecyclerId = r.getIdentifier("googleapp_discover_recycler_view", "id", PACKAGE); } catch (Throwable t) { sRecyclerId = 0; }
        }
        if (sRecyclerId != 0 && v.getId() == sRecyclerId) return true;
        return v.getClass().getName().endsWith(".DiscoverRecyclerView");
    }

    private static boolean isAd(View lv) throws Throwable {
        boolean ad = false;
        for (Field f : sTreeFields) {
            Object tree = f.get(lv);
            if (tree == null) continue;
            for (Field sf : sStateFields) {
                Object state = sf.get(tree);
                if (state == null) continue;
                Boolean known;
                synchronized (sVerdict) { known = sVerdict.get(state); }
                if (known == null) known = look(state);
                if (known != null && known) ad = true;
            }
        }
        return ad;
    }

    private static Boolean look(Object tree) {
        int[] texts = new int[1];
        boolean found = search(tree, texts);
        Boolean verdict = null;
        if (found) verdict = Boolean.TRUE;
        else if (texts[0] > 0) verdict = Boolean.FALSE;
        else {
            int tries;
            synchronized (sVerdict) { Integer t = sTries.get(tree); tries = t == null ? 1 : t + 1; sTries.put(tree, tries); }
            if (tries >= 6) {
                verdict = Boolean.FALSE;
            }
        }
        if (verdict != null) synchronized (sVerdict) { sVerdict.put(tree, verdict); sTries.remove(tree); }
        if (verdict != null) {
            sChecked++; if (found) sAds++;
            if (!sUnsafe && sChecked >= 6 && sAds * 2 > sChecked) { sUnsafe = true; }
        }
        return verdict;
    }

    private static boolean search(Object state, int[] texts) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<Object, Boolean>();
        int[] budget = {6000};
        boolean found = false;
        for (Field f : fields(state.getClass())) {
            Object v;
            try { v = f.get(state); } catch (Throwable t) { continue; }
            if (!(v instanceof List)) continue;
            Object[] items;
            try { items = ((List<?>) v).toArray(); } catch (Throwable t) { continue; }
            for (int i = 0; i < items.length && i < 600; i++) if (drawn(items[i], 0, seen, texts, budget)) found = true;
        }
        return found;
    }
    private static boolean drawn(Object o, int depth, IdentityHashMap<Object, Boolean> seen, int[] texts, int[] budget) {
        if (o == null || --budget[0] < 0 || seen.put(o, Boolean.TRUE) != null) return false;
        if (o instanceof android.text.Layout) {
            CharSequence t = ((android.text.Layout) o).getText();
            if (t == null) return false;
            texts[0]++;
            String s = t.toString().trim();
            if (s.length() <= 40) for (String l : LABELS) if (l.equalsIgnoreCase(s)) return true;
            return false;
        }
        if (depth >= 3 || o instanceof CharSequence || o instanceof Collection || o instanceof Map || o instanceof Object[] || skip(o, null)) return false;
        boolean found = false;
        for (Field f : fields(o.getClass())) {
            try { if (drawn(f.get(o), depth + 1, seen, texts, budget)) found = true; } catch (Throwable ignored) { }
        }
        return found;
    }

    private static boolean skip(Object o, Object start) {
        if (o instanceof CharSequence) return false;
        if (o instanceof View || o instanceof Context || o instanceof Drawable || o instanceof Bitmap || o instanceof Resources
                || o instanceof Handler || o instanceof Looper || o instanceof Thread || o instanceof ClassLoader || o instanceof Class
                || o instanceof java.lang.ref.Reference || o instanceof Number || o instanceof Boolean || o instanceof Enum
                || o instanceof java.util.concurrent.Executor || o instanceof android.graphics.Paint) return true;
        if (sComponentTree.isInstance(o)) return true;
        if (sContextClass != null && sContextClass.isInstance(o)) return true;
        if (o instanceof android.text.Layout) return false;
        Class<?> k = o.getClass();
        if (k.isArray()) return k.getComponentType().isPrimitive();
        String n = k.getName();
        if (n.startsWith("com.google.android.libraries.elements.") || n.contains("CppProxy")) return true;
        if (n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("kotlin.") || n.startsWith("android.") || n.startsWith("dalvik.") || n.startsWith("sun."))
            return !(o instanceof Collection || o instanceof Map || o instanceof SparseArray);
        return false;
    }

    private static Field[] fields(Class<?> k) {
        synchronized (sFields) {
            Field[] fs = sFields.get(k);
            if (fs != null) return fs;
            List<Field> list = new ArrayList<Field>();
            for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
                String n = c.getName();
                if (n.startsWith("java.") || n.startsWith("android.") || n.startsWith("kotlin.")) break;
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    try { f.setAccessible(true); list.add(f); } catch (Throwable ignored) { }
                }
            }
            fs = list.toArray(new Field[0]);
            sFields.put(k, fs);
            return fs;
        }
    }

    private static Method sSetMeasured;
    private static void setMeasured(View v, int w, int h) throws Throwable {
        if (sSetMeasured == null) { sSetMeasured = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class); sSetMeasured.setAccessible(true); }
        sSetMeasured.invoke(v, w, h);
    }

    private static void collapse(final View item) {
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (lp == null || sCollapsed.containsKey(item)) return;
        int top = 0, bottom = 0;
        if (lp instanceof ViewGroup.MarginLayoutParams) { top = ((ViewGroup.MarginLayoutParams) lp).topMargin; bottom = ((ViewGroup.MarginLayoutParams) lp).bottomMargin; }
        sCollapsed.put(item, new int[]{lp.height, top, bottom});
        lp.height = 0;
        if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).topMargin = 0; ((ViewGroup.MarginLayoutParams) lp).bottomMargin = 0; }
        item.post(new Runnable() { public void run() { item.requestLayout(); } });
    }
    private static void restore(final View item) {
        int[] was = sCollapsed.remove(item);
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (was == null || lp == null) return;
        lp.height = was[0];
        if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).topMargin = was[1]; ((ViewGroup.MarginLayoutParams) lp).bottomMargin = was[2]; }
        item.post(new Runnable() { public void run() { item.requestLayout(); } });
    }

}
