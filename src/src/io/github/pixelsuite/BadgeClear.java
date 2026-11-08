package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BadgeClear {
    private BadgeClear() {}

    private static WeakReference<Object> listener = new WeakReference<Object>(null);
    private static final Set<String> muted = Collections.synchronizedSet(new HashSet<String>());
    private static boolean loaded;
    private static final String PREF = "badge_muted";

    static void install() {
        Class<?> type = LauncherFeatures.type("notification.NotificationListener");
        Xp.Callback keep = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { listener = new WeakReference<Object>(p.thisObject); }
        };
        Xp.hookAllConstructors(type, keep);
        Xp.hookAllMethods(type, "onListenerConnected", keep);
        Xp.hookAllMethods(type, "notificationIsValidForUI", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length == 0 || !(p.args[0] instanceof android.service.notification.StatusBarNotification)) return;
                    load();
                    if (muted.contains(((android.service.notification.StatusBarNotification) p.args[0]).getKey())) p.setResult(false);
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(type, "onNotificationRemoved", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length == 0 || !(p.args[0] instanceof android.service.notification.StatusBarNotification)) return;
                    load();
                    if (muted.remove(((android.service.notification.StatusBarNotification) p.args[0]).getKey())) save();
                } catch (Throwable ignored) { }
            }
        });
    }

    private static SharedPreferences prefs() {
        try { return LauncherFeatures.data == null ? null : LauncherFeatures.data.prefs; } catch (Throwable t) { return null; }
    }
    private static void load() {
        if (loaded) return;
        SharedPreferences s = prefs(); if (s == null) return;
        loaded = true;
        try { muted.addAll(s.getStringSet(PREF, Collections.<String>emptySet())); } catch (Throwable ignored) { }
    }
    private static void save() {
        SharedPreferences s = prefs(); if (s == null) return;
        Set<String> copy; synchronized (muted) { copy = new HashSet<String>(muted); }
        while (copy.size() > 300) copy.remove(copy.iterator().next());
        s.edit().putStringSet(PREF, copy).apply();
    }

    private static List<Object[]> targets(Object info) {
        List<Object[]> out = new ArrayList<Object[]>();
        if (info == null) return out;
        try {
            if (LauncherFeatures.type("model.data.FolderInfo").isInstance(info)) {
                for (Object item : (List<?>) LauncherFeatures.get(info, "contents")) add(out, item);
            } else add(out, info);
        } catch (Throwable ignored) { }
        return out;
    }
    private static void add(List<Object[]> out, Object item) {
        try {
            String pkg = (String) LauncherFeatures.call(item, "getTargetPackage");
            Object user = LauncherFeatures.get(item, "user");
            if (pkg != null) out.add(new Object[]{pkg, user});
        } catch (Throwable ignored) { }
    }

    private static Map<?, ?> dots() {
        Object l = listener.get();
        try { if (l != null) { Object m = LauncherFeatures.get(l, "mPackageUserToDotInfos"); if (m instanceof Map) return (Map<?, ?>) m; } } catch (Throwable ignored) { }
        return null;
    }

    private static List<Object> dotInfos(Object info) {
        List<Object> out = new ArrayList<Object>();
        Map<?, ?> map = dots(); if (map == null) return out;
        List<Object[]> want = targets(info); if (want.isEmpty()) return out;
        List<Map.Entry<?, ?>> entries;
        synchronized (map) { entries = new ArrayList<Map.Entry<?, ?>>(map.entrySet()); }
        for (Map.Entry<?, ?> e : entries) {
            try {
                String pkg = (String) LauncherFeatures.get(e.getKey(), "mPackageName");
                Object user = LauncherFeatures.get(e.getKey(), "mUser");
                for (Object[] w : want) if (w[0].equals(pkg) && (w[1] == null || w[1].equals(user))) { out.add(e.getValue()); break; }
            } catch (Throwable ignored) { }
        }
        return out;
    }

    static boolean has(Object info) {
        try {
            Object l = listener.get();
            if (l instanceof Context && android.provider.Settings.Secure.getInt(((Context) l).getContentResolver(), "notification_badging", 1) == 0) return false;
            for (Object dot : dotInfos(info)) {
                List<?> keys = (List<?>) LauncherFeatures.get(dot, "mNotificationKeys");
                if (keys != null && !keys.isEmpty()) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static void clear(Object info) {
        final Object l = listener.get(); if (l == null) return;
        final List<String> keys = new ArrayList<String>();
        for (Object dot : dotInfos(info)) {
            try { for (Object k : new ArrayList<Object>((List<?>) LauncherFeatures.get(dot, "mNotificationKeys"))) { String key = (String) LauncherFeatures.get(k, "notificationKey"); if (key != null) keys.add(key); } } catch (Throwable ignored) { }
        }
        if (keys.isEmpty()) return;
        load();
        muted.addAll(keys); save();
        Runnable work = new Runnable() { public void run() {
            for (String key : keys) {
                try { ((android.service.notification.NotificationListenerService) l).cancelNotification(key); } catch (Throwable ignored) { }
            }
            try {
                Map<?, ?> map = dots();
                if (map != null) {
                    List<Object> empty = new ArrayList<Object>();
                    for (Map.Entry<?, ?> e : new ArrayList<Map.Entry<?, ?>>(map.entrySet())) {
                        List<?> list = (List<?>) LauncherFeatures.get(e.getValue(), "mNotificationKeys");
                        int removed = 0;
                        for (java.util.Iterator<?> it = list.iterator(); it.hasNext();) {
                            Object k = it.next(); String key = (String) LauncherFeatures.get(k, "notificationKey");
                            if (keys.contains(key)) { try { removed += ((Number) LauncherFeatures.get(k, "count")).intValue(); } catch (Throwable ignored) { } it.remove(); }
                        }
                        if (removed > 0) try { LauncherFeatures.set(e.getValue(), "mTotalCount", Math.max(0, ((Number) LauncherFeatures.get(e.getValue(), "mTotalCount")).intValue() - removed)); } catch (Throwable ignored) { }
                        if (list.isEmpty()) empty.add(e.getKey());
                    }
                    for (Object k : empty) map.remove(k);
                }
                LauncherFeatures.call(l, "dispatchUpdate", new java.util.function.Predicate<Object>() { public boolean test(Object o) { return true; } });
            } catch (Throwable ignored) { }
            try { Haptics.tick((Context) l); } catch (Throwable ignored) { }
        }};
        Object handler = null;
        try { handler = LauncherFeatures.get(l, "mWorkerHandler"); } catch (Throwable ignored) { }
        if (handler instanceof Handler) ((Handler) handler).post(work); else work.run();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.postDelayed(new Runnable() { public void run() { try { DrawerFolders.refreshDots(); } catch (Throwable ignored) { } } }, 250);
    }
}
