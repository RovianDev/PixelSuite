package io.github.pixelsuite;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public final class ChromeTextSearch extends Activity {
    static final String CHROME = "com.android.chrome";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            CharSequence picked = getIntent() == null ? null : getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            String text = picked == null ? "" : picked.toString().trim();
            if (!text.isEmpty()) open(text);
        } catch (Throwable ignored) { }
        finish();
        overridePendingTransition(0, 0);
    }

    private void open(String text) {
        Uri uri = address(text);
        if (uri == null) uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(text));
        Intent view = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addCategory(Intent.CATEGORY_BROWSABLE);
        view.setPackage(CHROME);
        try { startActivity(view); return; } catch (Throwable noChrome) { }
        view.setPackage(null);
        try { startActivity(view); } catch (Throwable ignored) { }
    }

    static final String SELF = "io.github.roviandev.pixelsuite";
    private static final ThreadLocal<Integer> sTextQuery = new ThreadLocal<Integer>();

    static void installSystem(ClassLoader cl) {
        Xp.Callback query = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (Boolean.TRUE.equals(sInject.get())) return;
                Integer depth = sTextQuery.get();
                boolean text = depth != null && depth > 0;
                if (!text) for (Object a : p.args) if (a instanceof Intent) { text = Intent.ACTION_PROCESS_TEXT.equals(((Intent) a).getAction()); break; }
                sTextQuery.set(text ? (depth == null ? 0 : depth) + 1 : (depth == null ? 0 : depth));
            }
            @Override protected void afterHookedMethod(Xp.Param p) {
                if (Boolean.TRUE.equals(sInject.get())) return;
                Integer depth = sTextQuery.get();
                if (depth != null && depth > 0) sTextQuery.set(depth - 1);
                if (depth == null || depth != 1 || Boolean.TRUE.equals(sInject.get())) return;
                try { inject(p); } catch (Throwable t) { if (!sInjectFailed) { sInjectFailed = true; android.util.Log.w("PixelSuiteTextSearch", "couldn't add Search in Chrome", t); } }
            }
        };
        for (String k : new String[]{"com.android.server.pm.ComputerEngine", "com.android.server.pm.PackageManagerService", "com.android.server.pm.ResolveIntentHelper"}) {
            try {
                Class<?> c = Xp.findClass(k, cl);
                for (String m : new String[]{"queryIntentActivitiesInternal", "queryIntentActivitiesInternalBody", "queryIntentActivities"}) {
                    try { Xp.hookAllMethods(c, m, query); } catch (Throwable ignored) { }
                }
            } catch (Throwable ignored) { }
        }
        Xp.Callback visible = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Integer depth = sTextQuery.get();
                    if (depth == null || depth <= 0) return;
                    if (SELF.equals(target(p.args))) {
                        p.setResult(Boolean.FALSE);
                    }
                } catch (Throwable ignored) { }
            }
        };
        for (String k : new String[]{"com.android.server.pm.ComputerEngine", "com.android.server.pm.AppsFilterBase",
                "com.android.server.pm.AppsFilterImpl", "com.android.server.pm.AppsFilterSnapshotImpl", "com.android.server.pm.AppsFilterLocked",
                "com.android.server.pm.AppsFilter", "com.android.server.pm.PackageManagerService"}) {
            try {
                Class<?> c = Xp.findClass(k, cl);
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (!m.getName().startsWith("shouldFilterApplication") || m.getReturnType() != boolean.class) continue;
                    Xp.hookMethod(m, visible);
                }
            } catch (Throwable ignored) { }
        }
    }

    private static final ThreadLocal<Boolean> sInject = new ThreadLocal<Boolean>();
    private static volatile boolean sInjectFailed;
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void inject(Xp.Param p) throws Exception {
        if (!(p.getResult() instanceof java.util.List) || !(p.method instanceof java.lang.reflect.Method)) return;
        java.util.List res = (java.util.List) p.getResult();
        for (Object o : res) if (o instanceof android.content.pm.ResolveInfo && ((android.content.pm.ResolveInfo) o).activityInfo != null && SELF.equals(((android.content.pm.ResolveInfo) o).activityInfo.packageName)) return;
        Object[] a = p.args.clone(); int at = -1, uid = -1;
        for (int i = 0; i < a.length; i++) {
            if (at < 0 && a[i] instanceof Intent) at = i;
            else if (at >= 0 && uid < 0 && a[i] instanceof Integer) uid = i;
        }
        if (at < 0 || uid < 0) return;
        Intent orig = (Intent) a[at];
        if (!Intent.ACTION_PROCESS_TEXT.equals(orig.getAction())) return;
        if (orig.getComponent() != null && !SELF.equals(orig.getComponent().getPackageName())) return;
        if (orig.getPackage() != null && !SELF.equals(orig.getPackage())) return;
        Intent mine = new Intent(orig); if (mine.getComponent() == null) mine.setPackage(SELF);
        a[at] = mine; a[uid] = android.os.Process.SYSTEM_UID;
        Object more;
        sInject.set(Boolean.TRUE);
        try { java.lang.reflect.Method m = (java.lang.reflect.Method) p.method; m.setAccessible(true); more = m.invoke(p.thisObject, a); }
        finally { sInject.set(Boolean.FALSE); }
        if (!(more instanceof java.util.List) || ((java.util.List) more).isEmpty()) return;
        java.util.ArrayList merged = new java.util.ArrayList(res);
        for (Object o : (java.util.List) more) if (o instanceof android.content.pm.ResolveInfo && ((android.content.pm.ResolveInfo) o).activityInfo != null && SELF.equals(((android.content.pm.ResolveInfo) o).activityInfo.packageName)) merged.add(o);
        if (merged.size() == res.size()) return;
        p.setResult(merged);
    }

    private static String target(Object[] args) {
        String name = null;
        for (Object a : args) {
            if (a == null || a instanceof String || a instanceof Number || a instanceof Boolean) continue;
            try { Object v = a.getClass().getMethod("getPackageName").invoke(a); if (v instanceof String) name = (String) v; } catch (Throwable ignored) { }
        }
        return name;
    }

    static Uri address(String text) {
        if (text.contains(" ") || text.contains("\n") || text.length() > 2000) return null;
        String t = text;
        if (t.matches("(?i)https?://\\S+")) return Uri.parse(t);
        if (t.matches("(?i)(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,24}(:\\d+)?(/\\S*)?")) return Uri.parse("https://" + t);
        return null;
    }
}
