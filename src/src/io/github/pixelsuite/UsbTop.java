package io.github.pixelsuite;

import android.app.Notification;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

final class UsbTop {
    private UsbTop() { }

    static final String KEY = "pixelsuite_usb_top";

    private static final String SET_KEY = "pixelsuite_usb_top_set_mtp";
    private static final String MARK = "pixelsuite_usb_top";
    private static final long FUNCTION_MTP = 4L;
    private static final int NOTE_ADB_ACTIVE = 26, NOTE_ADB_WIFI_ACTIVE = 62;
    private static volatile Context sContext;
    private static volatile long sReadAt; private static volatile boolean sOn;

    static boolean on() {
        Context c = sContext;
        if (c == null) try { c = sContext = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable ignored) {}
        if (c == null) return false;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sReadAt > 1000) { sReadAt = now; try { sOn = Settings.Secure.getInt(c.getContentResolver(), KEY, 0) == 1; } catch (Throwable t) { sOn = false; } }
        return sOn;
    }

    static void installSystem(ClassLoader cl) throws Throwable {
        Class<?> service = cl.loadClass("com.android.server.notification.NotificationManagerService");

        Xp.hookAllMethods(service, "enqueueNotificationInternal", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (sContext == null) sContext = (Context) Xp.callMethod(p.thisObject, "getContext");
                    if (p.args.length < 7 || !"android".equals(p.args[0]) || !on()) return;
                    Notification n = null; int id = -1;
                    for (int i = 0; i < p.args.length; i++) {
                        if (p.args[i] instanceof Notification) { n = (Notification) p.args[i]; if (i > 0 && p.args[i - 1] instanceof Integer) id = (Integer) p.args[i - 1]; break; }
                    }
                    if (n == null) return;
                    String channel = channelOf(n);
                    int kind = "USB".equals(channel) ? 1 : (id == NOTE_ADB_ACTIVE || id == NOTE_ADB_WIFI_ACTIVE) ? 2 : 0;
                    if (kind == 0) return;
                    n.extras.putInt(MARK, kind);
                    n.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
                } catch (Throwable t) { android.util.Log.w("PixelSuite", "usb top: mark", t); }
            }
        });

        Xp.hookAllMethods(service, "onBootPhase", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length < 1 || !Integer.valueOf(1000).equals(p.args[0])) return;
                    final Context c = (Context) Xp.callMethod(p.thisObject, "getContext");
                    sContext = c;
                    ContentObserver watch = new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override public void onChange(boolean self) { sReadAt = 0; defaultUsb(c); }
                    };
                    c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, watch);
                    defaultUsb(c);
                } catch (Throwable t) { android.util.Log.w("PixelSuite", "usb top: boot", t); }
            }
        });
        Class<?> record = cl.loadClass("com.android.server.notification.NotificationRecord");

        Xp.hookAllMethods(record, "getImportance", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (kind(p.thisObject) == 1 && p.getResult() instanceof Integer && (Integer) p.getResult() < 3) p.setResult(3);
            }
        });
        Xp.Callback quiet = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { if (kind(p.thisObject) == 1) p.setResult(null); }
        };
        Xp.hookAllMethods(record, "getSound", quiet);
        Xp.hookAllMethods(record, "getVibration", quiet);

        Xp.hookAllMethods(cl.loadClass("com.android.server.notification.RankingHelper"), "sort", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { if (p.args.length == 1 && p.args[0] instanceof java.util.List && on()) front((java.util.List<Object>) p.args[0]); }
                catch (Throwable t) { android.util.Log.w("PixelSuite", "usb top: sort", t); }
            }
        });
        try {
            Xp.hookAllMethods(cl.loadClass("com.android.server.notification.NotificationComparator"), "compare", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length != 2) return;
                    int a = rank(p.args[0]), b = rank(p.args[1]);
                    if (a != b) p.setResult(a < b ? -1 : 1);
                }
            });
        } catch (ClassNotFoundException ignored) {  }
    }

    private static void front(java.util.List<Object> list) {
        java.util.ArrayList<Object> heads = new java.util.ArrayList<Object>(), usb = new java.util.ArrayList<Object>(), adb = new java.util.ArrayList<Object>();
        java.util.HashSet<String> groups = new java.util.HashSet<String>();
        for (Object r : list) {
            int k = kind(r); if (k == 0) continue;
            (k == 1 ? usb : adb).add(r);
            String g = groupKey(r); if (g != null) groups.add(g);
        }
        if (usb.isEmpty() && adb.isEmpty()) return;
        for (Object r : list) if (kind(r) == 0 && summary(r) && groups.contains(groupKey(r))) heads.add(r);
        java.util.ArrayList<Object> order = new java.util.ArrayList<Object>(list.size());
        order.addAll(heads); order.addAll(usb); order.addAll(adb);
        for (Object r : list) if (!heads.contains(r) && !usb.contains(r) && !adb.contains(r)) order.add(r);
        if (order.equals(list)) return;
        list.clear(); list.addAll(order);
    }

    private static String groupKey(Object record) { try { return (String) Xp.callMethod(record, "getGroupKey"); } catch (Throwable t) { return null; } }

    private static boolean summary(Object record) {
        try { Notification n = (Notification) Xp.callMethod(Xp.callMethod(record, "getSbn"), "getNotification"); return n != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0; }
        catch (Throwable t) { return false; }
    }

    static void installSystemUi(ClassLoader cl) throws Throwable {
        Class<?> slb = cl.loadClass("com.android.systemui.statusbar.notification.collection.ShadeListBuilder");
        Xp.hookAllMethods(slb, "setSectioners", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { if (p.args.length == 1 && p.args[0] instanceof java.util.List) topSectioner((java.util.List<?>) p.args[0]); }
                catch (Throwable t) { android.util.Log.w("PixelSuite", "usb top: sectioners", t); }
            }
        });
        Xp.hookAllMethods(slb, "findSection", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { if (on() && shadeRank(p.args[0]) < 2) { Object top = topSection(p.thisObject); if (top != null) p.setResult(top); } }
                catch (Throwable t) { android.util.Log.w("PixelSuite", "usb top: section", t); }
            }
        });

        Xp.hookAllConstructors(slb, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { sBuilder = p.thisObject; hookComparators(p.thisObject); }
        });
    }

    private static final java.util.Set<Class<?>> sHooked = java.util.Collections.synchronizedSet(new java.util.HashSet<Class<?>>());
    private static volatile Object sBuilder; private static volatile boolean sTopDone;

    private static void topFromBuilder() {
        Object b = sBuilder; if (sTopDone || b == null) return;
        Object section = topSection(b); if (section == null) return;
        try { topSectioner(java.util.Collections.singletonList(Xp.callMethod(section, "getSectioner"))); }
        catch (Throwable t) { sTopDone = true; android.util.Log.w("PixelSuite", "usb top: first section's sectioner", t); }
    }

    private static void topSectioner(java.util.List<?> list) {
        if (list.isEmpty() || list.get(0) == null) return;
        sTopDone = true;
        Class<?> k = list.get(0).getClass();
        if (!sHooked.add(k)) return;
        int n = 0;
        for (Class<?> c = k; c != null && c != Object.class && n == 0; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("isInSection") || m.getParameterTypes().length != 1 || java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try { if (on() && !Boolean.TRUE.equals(p.getResult()) && shadeRank(p.args[0]) < 2) p.setResult(Boolean.TRUE); } catch (Throwable ignored) {}
                    }
                });
                n++;
            }
        }
    }

    private static void hookComparators(Object builder) {
        for (java.lang.reflect.Field f : builder.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true); Object v = f.get(builder);
                if (!(v instanceof java.util.Comparator) || !sHooked.add(v.getClass())) continue;
                for (java.lang.reflect.Method m : v.getClass().getDeclaredMethods()) {
                    if (!m.getName().equals("compare") || m.getParameterTypes().length != 2) continue;
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param p) {
                            try {
                                if (!on()) return;
                                if (!sTopDone) topFromBuilder();
                                int ra = shadeRank(p.args[0]), rb = shadeRank(p.args[1]);
                                if (ra != rb && sectionIndex(p.args[0]) == sectionIndex(p.args[1])) p.setResult(ra < rb ? -1 : 1);
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "usb top: comparator " + f.getName(), t); }
        }
    }

    private static int sectionIndex(Object entry) {
        try { return (Integer) Xp.callMethod(entry, "getSectionIndex"); } catch (Throwable ignored) {}
        try {
            Object section = Xp.callMethod(Xp.callMethod(entry, "getAttachState"), "getSection");
            if (section == null) return -1;
            try { return (Integer) Xp.callMethod(section, "getIndex"); } catch (Throwable t) { return (Integer) Xp.getObjectField(section, "index"); }
        } catch (Throwable t) { return -1; }
    }

    private static Object topSection(Object builder) {
        for (java.lang.reflect.Field f : builder.getClass().getDeclaredFields()) {
            if (!java.util.List.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true); java.util.List<?> l = (java.util.List<?>) f.get(builder);
                if (l != null && !l.isEmpty() && l.get(0) != null && l.get(0).getClass().getSimpleName().equals("NotifSection")) return l.get(0);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static int shadeRank(Object entry) {
        if (entry == null) return 2;
        int best = 2;
        try { best = Math.min(best, entryRank(Xp.callMethod(entry, "getRepresentativeEntry"))); } catch (Throwable ignored) {}

        Object kids = null;
        for (String m : new String[]{"getChildren", "getRawChildren"}) { if (kids != null) break; try { kids = Xp.callMethod(entry, m); } catch (Throwable ignored) {} }
        for (String f : new String[]{"mUnmodifiableChildren", "mChildren"}) { if (kids != null) break; try { kids = Xp.getObjectField(entry, f); } catch (Throwable ignored) {} }
        if (kids instanceof java.util.List) for (Object k : (java.util.List<?>) kids) best = Math.min(best, entryRank(k));
        return best;
    }

    private static int entryRank(Object notificationEntry) {
        try {
            Notification n = (Notification) Xp.callMethod(Xp.callMethod(notificationEntry, "getSbn"), "getNotification");
            int k = n == null || n.extras == null ? 0 : n.extras.getInt(MARK, 0);
            return k == 1 ? 0 : k == 2 ? 1 : 2;
        } catch (Throwable t) { return 2; }
    }

    private static int rank(Object record) { int k = kind(record); return k == 1 ? 0 : k == 2 ? 1 : 2; }

    private static int kind(Object record) {
        try {
            if (record == null || !on()) return 0;
            Object sbn = Xp.callMethod(record, "getSbn");
            Notification n = (Notification) Xp.callMethod(sbn, "getNotification");
            return n == null || n.extras == null ? 0 : n.extras.getInt(MARK, 0);
        } catch (Throwable t) { return 0; }
    }

    private static String channelOf(Notification n) {
        try { return (String) Notification.class.getMethod("getChannelId").invoke(n); } catch (Throwable t) { return null; }
    }

    static void defaultUsb(Context c) {
        try {
            Object usb = c.getSystemService(Context.USB_SERVICE);
            if (usb == null) return;
            boolean want = Settings.Secure.getInt(c.getContentResolver(), KEY, 0) == 1;
            boolean ours = Settings.Secure.getInt(c.getContentResolver(), SET_KEY, 0) == 1;
            long now = ((Number) usb.getClass().getMethod("getScreenUnlockedFunctions").invoke(usb)).longValue();
            if (want && now != FUNCTION_MTP) {
                usb.getClass().getMethod("setScreenUnlockedFunctions", long.class).invoke(usb, FUNCTION_MTP);
                Settings.Secure.putInt(c.getContentResolver(), SET_KEY, 1);
            } else if (!want && ours) {
                if (now == FUNCTION_MTP) usb.getClass().getMethod("setScreenUnlockedFunctions", long.class).invoke(usb, 0L);
                Settings.Secure.putInt(c.getContentResolver(), SET_KEY, 0);
            }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "usb top: default usb", t); }
    }
}
