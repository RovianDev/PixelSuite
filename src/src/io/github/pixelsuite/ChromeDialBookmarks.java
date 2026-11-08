package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class ChromeDialBookmarks {
    private ChromeDialBookmarks() {}

    static final String NAME = "Speed Dial";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WeakReference<Object> sModel = new WeakReference<Object>(null);
    private static boolean sLoaded, sReady, sFailed;
    private static Method mAdd, mDelete, mChildren, mTop, mItem, mCount, mDefault, mParse;
    private static Field fPtr;
    private static Class<?> sIdClass, sGurlClass, sItemClass;
    private static int sFolderFn = -2;
    private static boolean sLooking;
    private static final ThreadLocal<Boolean> RAW = new ThreadLocal<Boolean>();
    private static volatile List<ChromeFolders.Site> sTiles;

    static void install(final ClassLoader l) {
        try {
            Class<?> bridge = l.loadClass("org.chromium.chrome.browser.bookmarks.BookmarkBridge");
            Class<?> model = l.loadClass("org.chromium.chrome.browser.bookmarks.BookmarkModel");
            sIdClass = l.loadClass("org.chromium.components.bookmarks.BookmarkId");
            sItemClass = l.loadClass("org.chromium.components.bookmarks.BookmarkItem");
            sGurlClass = l.loadClass("org.chromium.url.GURL");
            for (Method m : bridge.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] p = m.getParameterTypes();
                Class<?> r = m.getReturnType();
                if (r == sIdClass && p.length == 4 && p[0] == sIdClass && p[1] == int.class && p[2] == String.class && p[3] == sGurlClass) mAdd = m;
                else if (r == void.class && p.length == 1 && p[0] == sIdClass) mDelete = m;
                else if (r == ArrayList.class && p.length == 1 && p[0] == sIdClass) mChildren = m;
                else if (r == ArrayList.class && p.length == 1 && p[0] == int.class) mTop = m;
                else if (r == sItemClass && p.length == 1 && p[0] == sIdClass) mItem = m;
                else if (r == int.class && p.length == 1 && p[0] == sIdClass) mCount = m;
            }
            for (Method m : model.getDeclaredMethods())
                if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == sIdClass && m.getParameterTypes().length == 0) mDefault = m;
            for (Method m : sIdClass.getDeclaredMethods())
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == sIdClass && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == String.class) mParse = m;
            for (Field f : bridge.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType() == long.class) fPtr = f;
            for (Method m : new Method[]{mAdd, mDelete, mChildren, mTop, mItem, mCount, mDefault, mParse}) if (m != null) m.setAccessible(true);
            if (fPtr != null) fPtr.setAccessible(true);
            sReady = mAdd != null && mDelete != null && mChildren != null && mItem != null && mDefault != null && fPtr != null;
            if (!sReady) return;
            Xp.hookAllMethods(bridge, "createBookmarkModel", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { if (p.getResult() != null) sModel = new WeakReference<Object>(p.getResult()); }
            });
            Xp.hookAllMethods(bridge, "bookmarkModelLoaded", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { sModel = new WeakReference<Object>(p.thisObject); sLoaded = true; schedule(); }
            });
            Xp.hookMethod(mChildren, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (Boolean.TRUE.equals(RAW.get()) || !(p.getResult() instanceof ArrayList)) return;
                        Object ours = ours(p.thisObject);
                        if (ours == null) return;
                        @SuppressWarnings("unchecked") ArrayList<Object> list = (ArrayList<Object>) p.getResult();
                        Object target = sTarget;
                        if (target == null || p.args[0] == null) return;
                        if (sTopListed && p.args[0].equals(target)) list.remove(ours);
                    } catch (Throwable ignored) { }
                }
            });
            if (mTop != null) Xp.hookMethod(mTop, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (Boolean.TRUE.equals(RAW.get()) || !(p.getResult() instanceof ArrayList)) return;
                        Object ours = ours(p.thisObject);
                        if (ours == null) return;
                        Object target = sTarget != null ? sTarget : target(p.thisObject);
                        @SuppressWarnings("unchecked") ArrayList<Object> list = (ArrayList<Object>) p.getResult();
                        if (target == null || !list.contains(target) || list.contains(ours)) return;
                        list.add(ours);
                                            } catch (Throwable ignored) { }
                }
            });
            if (mCount != null) Xp.hookMethod(mCount, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (Boolean.TRUE.equals(RAW.get()) || !(p.getResult() instanceof Integer)) return;
                        Object ours = ours(p.thisObject);
                        if (ours == null || p.args[0] == null) return;
                        Object target = sTarget;
                        if (target != null && sTopListed && p.args[0].equals(target) && raw(p.thisObject, mChildren, target) instanceof ArrayList && ((ArrayList<?>) raw(p.thisObject, mChildren, target)).contains(ours) && (Integer) p.getResult() > 0) p.setResult((Integer) p.getResult() - 1);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) {
        }
    }

    static void tiles(List<ChromeFolders.Site> mine) {
        List<ChromeFolders.Site> old = sTiles;
        sTiles = mine;
        if (old == null || !same(old, mine)) schedule();
    }

    private static boolean same(List<ChromeFolders.Site> a, List<ChromeFolders.Site> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!eq(a.get(i).url, b.get(i).url) || !eq(a.get(i).title, b.get(i).title)) return false;
        return true;
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    private static final Runnable SYNC = new Runnable() { public void run() { try { sync(); } catch (Throwable t) { } } };

    static void schedule() {
        if (!sReady || sFailed) return;
        Context c = app();
        if (c != null && prefs(c).getBoolean(guardKey(c), false)) {
            sFailed = true;
            return;
        }
        MAIN.removeCallbacks(SYNC);
        MAIN.postDelayed(SYNC, 1200);
    }

    private static Object raw(Object model, Method m, Object... args) throws Exception {
        Boolean was = RAW.get();
        RAW.set(Boolean.TRUE);
        try { return m.invoke(model, args); } finally { RAW.set(was); }
    }

    private static Object sOurs;

    private static Object ours(Object model) {
        try {
            if (sOurs != null && raw(model, mItem, sOurs) != null) return sOurs;
            sOurs = null;
            Context c = app();
            String saved = c == null ? null : prefs(c).getString("sd_bookmarks_folder", null);
            if (saved != null && mParse != null) {
                Object id = mParse.invoke(null, saved);
                if (id != null && raw(model, mItem, id) != null) sOurs = id;
            }
        } catch (Throwable ignored) { }
        return sOurs;
    }

    private static volatile Object sTarget, sRoot;
    private static volatile boolean sTopListed;
    private static Method sMobile;
    private static boolean sTargetLooking, sTargetFailed;

    private static Object target(Object model) {
        if (sTarget != null) return sTarget;
        if (sMobile == null) { findTarget(); return null; }
        try {
            Object id = raw(model, sMobile);
            Object item = id == null ? null : raw(model, mItem, id);
            if (item == null) return null;
            String u = url(item);
            if (u != null && u.length() > 0) return null;
            sRoot = parentOf(model, id);
            sTarget = id;
        } catch (Throwable t) { return null; }
        return sTarget;
    }

    private static void findTarget() {
        if (sTargetLooking || sTargetFailed || mAdd == null) return;
        final Context app = app();
        if (app == null) return;
        final String key = "sd_bm_mobile_" + version(app);
        String cached = prefs(app).getString(key, null);
        if (cached != null) { pick(cached); return; }
        sTargetLooking = true;
        new Thread(new Runnable() { public void run() {
            String found = null;
            try {
                java.util.List<java.util.List<String>> all = DexPeek.callsNear(app, "cr_BookmarkUtils");
                String addName = mAdd.getName();
                if (all != null) for (java.util.List<String> calls : all) {
                    boolean clears = false;
                    for (String c : calls) if (c.contains(".clearLastUsedParent()V")) clears = true;
                    if (!clears) continue;
                    String last = null;
                    for (String c : calls) {
                        if (!c.startsWith("Lorg/chromium/chrome/browser/bookmarks/Bookmark")) continue;
                        String name = c.substring(c.indexOf(".") + 1, c.indexOf("("));
                        String sig = c.substring(c.indexOf("("));
                        if (sig.equals("()Lorg/chromium/components/bookmarks/BookmarkId;")) last = name;
                        else if (name.equals(addName) && sig.startsWith("(Lorg/chromium/components/bookmarks/BookmarkId;ILjava/lang/String;Lorg/chromium/url/GURL;)")) { if (last != null) { found = last; break; } }
                    }
                    if (found != null) break;
                }
            } catch (Throwable t) { }
            final String f = found;
            MAIN.post(new Runnable() { public void run() {
                sTargetLooking = false;
                if (f == null) { sTargetFailed = true; return; }
                prefs(app).edit().putString(key, f).apply();
                pick(f);
                schedule();
            }});
        }}, "PixelSuite-speed-dial-target").start();
    }

    private static void pick(String name) {
        try {
            sMobile = method(mAdd.getDeclaringClass(), name);
            if (sMobile == null) sTargetFailed = true;
        } catch (Throwable t) { sTargetFailed = true; }
    }

    private static Method method(Class<?> k, String name) {
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterTypes().length == 0) { m.setAccessible(true); return m; }
        return null;
    }

    private static Object parentOf(Object model, Object id) throws Exception {
        Object item = raw(model, mItem, id);
        if (item == null) return null;
        for (Field f : sItemClass.getDeclaredFields()) {
            if (f.getType() != sIdClass) continue;
            f.setAccessible(true);
            Object v = f.get(item);
            if (v != null && !v.equals(id)) return v;
        }
        return null;
    }

    private static String title(Object item) throws Exception {
        for (Field f : sItemClass.getDeclaredFields()) if (f.getType() == String.class) { f.setAccessible(true); return (String) f.get(item); }
        return null;
    }

    private static String url(Object item) throws Exception {
        for (Field f : sItemClass.getDeclaredFields()) if (f.getType() == sGurlClass) { f.setAccessible(true); return spec(f.get(item)); }
        return null;
    }

    private static String spec(Object gurl) throws Exception {
        if (gurl == null) return null;
        for (Class<?> k = gurl.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) if (f.getType() == String.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return (String) f.get(gurl); }
        return null;
    }

    private static Object gurl(String url) throws Exception { return sGurlClass.getConstructor(String.class).newInstance(url); }

    @SuppressWarnings("unchecked")
    private static ArrayList<Object> children(Object model, Object id) throws Exception {
        Object r = raw(model, mChildren, id);
        return r instanceof ArrayList ? (ArrayList<Object>) r : new ArrayList<Object>();
    }

    private static Object addFolder(Object model, Object parent, int index, String name) throws Exception {
        if (sFolderFn < 0) return null;
        long ptr = fPtr.getLong(model);
        if (ptr == 0) return null;
        Method m = Class.forName("J.N", false, model.getClass().getClassLoader()).getDeclaredMethod("OIJOO", int.class, int.class, long.class, Object.class, Object.class);
        m.setAccessible(true);
        Context c = app();
        if (c != null) prefs(c).edit().putBoolean(guardKey(c), true).commit();
        Object id = m.invoke(null, sFolderFn, index, ptr, parent, name);
        if (c != null) prefs(c).edit().remove(guardKey(c)).commit();
        return id;
    }

    private static String guardKey(Context c) { return "sd_bm_adding_" + version(c); }

    private static void findFolderCall(final Context app) {
        if (sFolderFn >= 0 || sLooking) return;
        String key = "sd_bm_folder_fn_" + version(app);
        int cached = prefs(app).getInt(key, -2);
        if (cached >= 0) { sFolderFn = cached; return; }
        if (cached == -1) { sFolderFn = -1; sFailed = true; return; }
        sLooking = true;
        final String k = key;
        new Thread(new Runnable() { public void run() {
            int id = -1;
            try { id = DexPeek.constBeforeCallNear(app, "cr_BookmarkUtils", "LJ/N;", "OIJOO", "(IIJLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"); }
            catch (Throwable t) { }
            final int found = id;
            MAIN.post(new Runnable() { public void run() {
                sLooking = false;
                sFolderFn = found;
                prefs(app).edit().putInt(k, found).apply();
                if (found < 0) sFailed = true; else schedule();
            }});
        }}, "PixelSuite-speed-dial-bookmarks").start();
    }

    private static final class Node {
        final String title, url;
        final List<ChromeFolders.Site> sites;
        Node(String title, String url, List<ChromeFolders.Site> sites) { this.title = title; this.url = url; this.sites = sites; }
    }

    private static List<Node> wanted(Context c, List<ChromeFolders.Site> tiles) {
        List<ChromeFolders.Folder> folders = new ArrayList<ChromeFolders.Folder>(ChromeFolders.folders(c));
        java.util.Collections.sort(folders, new java.util.Comparator<ChromeFolders.Folder>() {
            public int compare(ChromeFolders.Folder a, ChromeFolders.Folder b) { return Integer.compare(a.pos, b.pos); }
        });
        List<Node> out = new ArrayList<Node>();
        int n = tiles.size() + folders.size(), ti = 0, fi = 0;
        for (int i = 0; i < n; i++) {
            if (fi < folders.size() && (folders.get(fi).pos <= i || ti >= tiles.size())) { ChromeFolders.Folder f = folders.get(fi++); if (f.single && !f.sites.isEmpty()) out.add(new Node(f.sites.get(0).title, f.sites.get(0).url, null)); else out.add(new Node(f.name, null, new ArrayList<ChromeFolders.Site>(f.sites))); }
            else { ChromeFolders.Site s = tiles.get(ti++); out.add(new Node(s.title, s.url, null)); }
        }
        return out;
    }

    private static String describe(List<Node> nodes) {
        StringBuilder b = new StringBuilder();
        for (Node n : nodes) {
            if (n.sites == null) b.append("B|").append(n.title).append('|').append(n.url).append('\n');
            else {
                b.append("F|").append(n.title).append('\n');
                for (ChromeFolders.Site s : n.sites) b.append(" B|").append(s.title).append('|').append(s.url).append('\n');
            }
        }
        return b.toString();
    }

    private static String describeExisting(Object model, Object folder) throws Exception {
        StringBuilder b = new StringBuilder();
        for (Object id : children(model, folder)) {
            Object item = raw(model, mItem, id);
            if (item == null) continue;
            String u = url(item);
            if (u != null && u.length() > 0) b.append("B|").append(title(item)).append('|').append(u).append('\n');
            else {
                b.append("F|").append(title(item)).append('\n');
                for (Object cid : children(model, id)) {
                    Object ci = raw(model, mItem, cid);
                    if (ci != null) b.append(" B|").append(title(ci)).append('|').append(url(ci)).append('\n');
                }
            }
        }
        return b.toString();
    }

    private static void sync() throws Exception {
        Object model = sModel.get();
        Context c = app();
        List<ChromeFolders.Site> tiles = sTiles;
        if (model == null || c == null || !sLoaded || tiles == null) return;
        if (!ChromeLayout.enabled(c)) return;
        findFolderCall(c);
        if (sFolderFn < 0) return;
        Object parent = target(model);
        if (parent == null) { if (sTargetFailed) sFailed = true; return; }
        Object folder = ours(model);
        if (folder != null && !parent.equals(parentOf(model, folder))) {
            raw(model, mDelete, folder);
            sOurs = null;
            folder = null;
            prefs(c).edit().remove("sd_bookmarks_folder").apply();
        }
        if (folder == null) {
            folder = addFolder(model, parent, children(model, parent).size(), NAME);
            if (folder == null) { return; }
            sOurs = folder;
            prefs(c).edit().putString("sd_bookmarks_folder", folder.toString()).apply();
        }
        List<Node> want = wanted(c, tiles);
        String wantText = describe(want);
        if (wantText.equals(describeExisting(model, folder))) return;
        for (Object id : children(model, folder)) raw(model, mDelete, id);
        int index = 0, sites = 0;
        for (Node n : want) {
            if (n.sites == null) { raw(model, mAdd, folder, index++, n.title == null ? "" : n.title, gurl(n.url)); sites++; continue; }
            Object sub = addFolder(model, folder, index++, n.title == null || n.title.isEmpty() ? "Folder" : n.title);
            if (sub == null) continue;
            int j = 0;
            for (ChromeFolders.Site s : n.sites) { raw(model, mAdd, sub, j++, s.title == null ? "" : s.title, gurl(s.url)); sites++; }
        }
    }

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("pixelsuite_chrome_ui", Context.MODE_PRIVATE); }

    private static Context app() {
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable t) { return null; }
    }

    private static int version(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Throwable t) { return 0; }
    }
}
