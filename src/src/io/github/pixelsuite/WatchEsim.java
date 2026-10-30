package io.github.pixelsuite;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class WatchEsim {
    private WatchEsim() {}

    static final String PACKAGE = "com.google.android.apps.wear.companion";
    static final String KEY = "pixelsuite_watch_esim_qr";
    private static final String VM_TAG = "[ConnectivitySetupOptionsViewModel]";
    private static final String PAGE_TEXT = "MobileNetworkUiData(isQrCodeAvailable=";

    static void install(final ClassLoader cl, final ApplicationInfo ai) {
        if (!mainProcess()) return;
        Map<String, String> names = load(cacheFile(ai));
        if (names != null && hook(cl, names)) return;
        Thread t = new Thread(new Runnable() { public void run() {
            try {
                Map<String, String> found = scan(ai.sourceDir);
                if (found == null) { return; }
                if (hook(cl, found)) save(cacheFile(ai), found);
            } catch (Throwable error) { }
        } }, "pixelsuite-watch-scan");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    static Map<String, String> scan(String apk) throws Exception {
        DexScan s = DexScan.read(apk);
        List<DexScan.Mth> tagged = s.withString(VM_TAG);
        String vm = null, method = null;
        for (DexScan.Mth m : tagged) {
            DexScan.Cls c = s.classes.get(m.owner.desc);
            if (c == null) continue;
            String found = null; int n = 0;
            for (DexScan.Mth x : c.methods)
                if (!x.isStatic && "V".equals(x.ret) && x.params.length == 1 && "Ljava/util/Set;".equals(x.params[0])) { found = x.name; n++; }
            if (n == 1) { vm = c.desc; method = found; break; }
        }
        String kind = null;
        List<DexScan.Mth> qr = s.withString("QR_CODE"), device = s.withString("ON_DEVICE");
        for (DexScan.Mth a : qr) for (DexScan.Mth b : device)
            if (a.owner.desc.equals(b.owner.desc) && "<clinit>".equals(a.name)) kind = a.owner.desc;
        String page = null;
        for (DexScan.Mth m : s.withString(PAGE_TEXT)) { page = m.owner.desc; break; }
        if (vm == null || kind == null || page == null) return null;
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("page", DexScan.name(page));
        out.put("vm", DexScan.name(vm));
        out.put("vm.options", method);
        out.put("kind", DexScan.name(kind));
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean hook(ClassLoader cl, Map<String, String> names) {
        try {
            Class<?> vm = Class.forName(names.get("vm"), false, cl);
            Class<?> kind = Class.forName(names.get("kind"), false, cl);
            if (!kind.isEnum()) return false;
            final Object qr = Enum.valueOf((Class) kind, "QR_CODE");
            Method options = vm.getDeclaredMethod(names.get("vm.options"), Set.class);
            Xp.hookMethod(options, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!on() || !(p.args[0] instanceof Collection) || ((Collection<?>) p.args[0]).contains(qr)) return;
                        Set<Object> more = new LinkedHashSet<Object>();
                        more.add(qr);
                        more.addAll((Collection<?>) p.args[0]);
                        p.args[0] = more;
                    } catch (Throwable error) { }
                }
            });
            Class<?> page = Class.forName(names.get("page"), false, cl);
            for (java.lang.reflect.Constructor<?> k : page.getDeclaredConstructors()) {
                Class<?>[] ps = k.getParameterTypes();
                if (ps.length < 2 || ps[0] != boolean.class) continue;
                Xp.hookMethod(k, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        if (Boolean.TRUE.equals(p.args[0]) || !on()) return;
                        p.args[0] = Boolean.TRUE;
                    }
                });
            }
            return true;
        } catch (Throwable error) { return false; }
    }

    private static boolean on() {
        try {
            Context c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return c != null && !"0".equals(Settings.Secure.getString(c.getContentResolver(), KEY)) && PatronGate.esimOk(c);
        } catch (Throwable t) { return false; }
    }

    private static boolean mainProcess() {
        try { String p = (String) Application.class.getMethod("getProcessName").invoke(null); return p == null || PACKAGE.equals(p); } catch (Throwable t) { return true; }
    }

    private static File cacheFile(ApplicationInfo ai) {
        File apk = new File(ai.sourceDir);
        return new File(new File(ai.dataDir, "cache"), "pixelsuite-watch-names-" + StudioNames.versionCode(ai) + "-" + apk.length() + "-" + apk.lastModified() + ".properties");
    }
    private static Map<String, String> load(File f) {
        if (!f.isFile()) return null;
        try {
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(f);
            try { p.load(in); } finally { in.close(); }
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (String k : p.stringPropertyNames()) m.put(k, p.getProperty(k));
            return m.containsKey("vm") && m.containsKey("page") ? m : null;
        } catch (Throwable unreadable) { return null; }
    }
    private static void save(File f, Map<String, String> m) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            File[] old = dir == null ? null : dir.listFiles();
            if (old != null) for (File o : old) if (o.getName().startsWith("pixelsuite-watch-names-")) o.delete();
            Properties p = new Properties();
            p.putAll(m);
            FileOutputStream out = new FileOutputStream(f);
            try { p.store(out, "Pixel Suite: Watch app names"); } finally { out.close(); }
        } catch (Throwable ignored) { }
    }
}
