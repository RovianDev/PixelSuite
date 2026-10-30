package io.github.pixelsuite;

import android.app.Application;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.CallLog;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

final class CallRecording {
    private CallRecording() {}

    static final String PACKAGE = "com.google.android.dialer";
    static final String KEY = "pixelsuite_call_recording";
    static final String ANNOUNCE_KEY = "pixelsuite_call_recording_announcement";
    static final String FOLDER_KEY = "pixelsuite_call_recording_folder";
    static final String DEFAULT_FOLDER = "Recordings/Calls";
    static final String MODE_KEY = "pixelsuite_call_recording_mode";

    private static final String[] FLAGS = {"G__enable_call_recording", "G__use_call_recording_geofence_overrides",
            "G__force_within_call_recording_geofence_value"};
    private static final String[] AUTO_ANCHORS = {"produceShouldStartAutomaticRecordingForContact", "produceIsCallInAutoRecordContactsList"};
    private static final String HOLDER_TEXT = "null cannot be cast to non-null type T of com.google.apps.tiktok.experiments.FlagValueHolder.getProtoValue";
    private static final String MARK = "⁣⁣";
    private static final String VOLUME = "external_primary", RELATIVE_PATH = "relative_path", IS_PENDING = "is_pending";

    private static int sStartId = -1, sEndId = -1;
    private static final List<java.lang.ref.WeakReference<Object>> sMarked = new ArrayList<java.lang.ref.WeakReference<Object>>();
    private static volatile boolean sAnyMarked;
    private static void mark(Object holder) {
        synchronized (sMarked) {
            for (int i = sMarked.size() - 1; i >= 0; i--) { Object o = sMarked.get(i).get(); if (o == holder) return; if (o == null) sMarked.remove(i); }
            if (sMarked.size() >= 64) sMarked.remove(0);
            sMarked.add(new java.lang.ref.WeakReference<Object>(holder));
            sAnyMarked = true;
        }
    }
    private static boolean marked(Object holder) {
        if (!sAnyMarked) return false;
        synchronized (sMarked) { for (java.lang.ref.WeakReference<Object> r : sMarked) if (r.get() == holder) return true; }
        return false;
    }
    private static final Map<TextToSpeech, UtteranceProgressListener> sListeners = Collections.synchronizedMap(new WeakHashMap<TextToSpeech, UtteranceProgressListener>());

    static void install(ClassLoader cl, ApplicationInfo ai) {
        if (!mainProcess()) return;
        installSilence(cl);
        installFlags(cl, ai);
        startSaving(ai);
    }

    static final String WAKE_TAG = "PixelSuite:callrec";
    private static final long MAX_AWAKE = 20 * 60 * 1000L;
    private static final Map<Object, Integer> sLockPid = new HashMap<Object, Integer>();
    private static final Map<Integer, Long> sCopying = new HashMap<Integer, Long>();

    static void installSystem(ClassLoader cl) {
        try {
            Class<?> pms = Xp.findClass("com.android.server.power.PowerManagerService", cl);
            Xp.hookAllMethods(pms, "acquireWakeLockInternal", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        List<String> texts = new ArrayList<String>(); int pid = -1;
                        for (Object a : p.args) { if (a instanceof String) texts.add((String) a); else if (a instanceof Integer) pid = (Integer) a; }
                        if (texts.size() < 2 || !WAKE_TAG.equals(texts.get(0)) || !PACKAGE.equals(texts.get(1)) || pid <= 0 || !(p.args[0] instanceof android.os.IBinder)) return;
                        synchronized (sCopying) { sLockPid.put(p.args[0], pid); sCopying.put(pid, System.currentTimeMillis() + MAX_AWAKE); }
                    } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(pms, "releaseWakeLockInternal", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) { if (p.args.length > 0) letGo(p.args[0]); }
            });
            Xp.hookAllMethods(pms, "handleWakeLockDeath", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) { try { letGo(Xp.getObjectField(p.args[0], "mLock")); } catch (Throwable ignored) { } }
            });
            Class<?> fh = Xp.findClass("com.android.server.am.CachedAppOptimizer$FreezeHandler", cl);
            Xp.hookAllMethods(fh, "freezeProcess", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (sCopying.isEmpty() || p.args.length < 1 || p.args[0] == null) return;
                        int pid = (Integer) Xp.callMethod(p.args[0], "getPid");
                        synchronized (sCopying) {
                            Long until = sCopying.get(pid);
                            if (until == null) return;
                            if (until < System.currentTimeMillis()) { sCopying.remove(pid); return; }
                        }
                        Xp.callMethod(Xp.getObjectField(p.args[0], "mOptRecord"), "setPendingFreeze", false);
                        p.setResult(null);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable error) { }
    }
    private static void letGo(Object lock) {
        if (lock == null) return;
        synchronized (sCopying) { Integer pid = sLockPid.remove(lock); if (pid != null) sCopying.remove(pid); }
    }

    private static Context app() {
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); }
        catch (Throwable t) { return null; }
    }
    private static String setting(String key) {
        Context c = app(); if (c == null) return null;
        try { return Settings.Secure.getString(c.getContentResolver(), key); } catch (Throwable t) { return null; }
    }
    static boolean on() { return "1".equals(setting(KEY)) && PatronGate.ok(app()); }
    private static boolean silent() { return on() && !"1".equals(setting(ANNOUNCE_KEY)); }
    private static boolean automatic() { return on() && !"manual".equals(setting(MODE_KEY)); }

    static String folder() {
        String f = setting(FOLDER_KEY);
        if (f == null) return DEFAULT_FOLDER;
        f = f.trim().replace('\\', '/');
        while (f.startsWith("/")) f = f.substring(1);
        while (f.endsWith("/")) f = f.substring(0, f.length() - 1);
        if (f.isEmpty() || f.contains("..")) return DEFAULT_FOLDER;
        String root = f.contains("/") ? f.substring(0, f.indexOf('/')) : f;
        for (String ok : new String[]{"Recordings", "Music", "Podcasts", "Audiobooks", "Download", "Documents"}) if (ok.equalsIgnoreCase(root)) return ok + f.substring(root.length());
        return DEFAULT_FOLDER;
    }

    private static boolean mainProcess() {
        try { String p = (String) Application.class.getMethod("getProcessName").invoke(null); return p == null || PACKAGE.equals(p); } catch (Throwable t) { return true; }
    }

    private static int installFlags(final ClassLoader cl, final ApplicationInfo ai) {
        Map<String, String> names = load(cacheFile(ai));
        if (names != null && names.containsKey("auto.0") && hookFlags(cl, names)) { hookAuto(cl, names); return 2; }
        Thread t = new Thread(new Runnable() { public void run() {
            try {
                Map<String, String> found = scan(ai.sourceDir);
                if (found == null) { return; }
                if (hookFlags(cl, found)) {
                    hookAuto(cl, found);
                    for (int i = 0; i < 120 && !unlocked(); i++) Thread.sleep(5000);
                    save(cacheFile(ai), found);
                }
            } catch (Throwable error) { }
        } }, "pixelsuite-dialer-scan");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return 0;
    }

    static Map<String, String> scan(String apk) throws Exception {
        DexScan s = DexScan.read(apk);
        List<DexScan.Mth> ms = s.withString(HOLDER_TEXT);
        if (ms.isEmpty()) return null;
        String holder = ms.get(0).owner.desc;
        String bool = null;
        for (DexScan.Mth m : s.classes.get(holder).methods) if (!m.isStatic && m.params.length == 0 && "Z".equals(m.ret)) { if (bool != null) return null; bool = m.name; }
        List<String> getters = new ArrayList<String>();
        for (DexScan.Mth m : s.allMethods)
            if (!m.isStatic && holder.equals(m.ret) && m.params.length == 2 && "Ljava/lang/String;".equals(m.params[0]) && "Ljava/lang/String;".equals(m.params[1]))
                getters.add(DexScan.name(m.owner.desc) + "#" + m.name);
        if (bool == null || getters.isEmpty()) return null;
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("holder", DexScan.name(holder));
        out.put("holder.bool", bool);
        for (int i = 0; i < getters.size(); i++) out.put("getter." + i, getters.get(i));
        int a = 0;
        for (String anchor : AUTO_ANCHORS) {
            List<DexScan.Mth> found = s.withString(anchor);
            if (found.size() != 1) continue;
            DexScan.Mth m = found.get(0);
            if (m.isStatic || m.params.length != 1 || !"Ljava/lang/Object;".equals(m.params[0])) continue;
            out.put("auto." + a++, DexScan.name(m.owner.desc) + "#" + m.name);
        }
        if (a == 0) out.put("auto.0", "none");
        return out;
    }

    private static boolean hookFlags(ClassLoader cl, Map<String, String> names) {
        try {
            Class<?> holder = Class.forName(names.get("holder"), false, cl);
            Method bool = holder.getDeclaredMethod(names.get("holder.bool"));
            if (bool.getReturnType() != boolean.class) return false;
            List<Method> getters = new ArrayList<Method>();
            for (int i = 0; names.containsKey("getter." + i); i++) {
                String[] g = names.get("getter." + i).split("#");
                Method m = Class.forName(g[0], false, cl).getDeclaredMethod(g[1], String.class, String.class);
                if (m.getReturnType() != holder) return false;
                getters.add(m);
            }
            if (getters.isEmpty()) return false;
            final Set<String> flags = new HashSet<String>();
            Collections.addAll(flags, FLAGS);
            final java.util.Set<Object> shared = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            for (java.lang.reflect.Field f : holder.getDeclaredFields())
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == holder) { f.setAccessible(true); Object v = f.get(null); if (v != null) shared.add(v); }
            final Class<?> holderClass = holder;
            for (Method m : getters) Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object r = p.getResult();
                    if (!(p.args[1] instanceof String) || !flags.contains(p.args[1]) || r == null || !on()) return;
                    Object own = trueHolder(holderClass, r);
                    if (own != null) p.setResult(own);
                    else if (!shared.contains(r)) mark(r);
                }
            });
            Xp.hookMethod(bool, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (marked(p.thisObject) && on()) p.setResult(Boolean.TRUE);
                }
            });
            return true;
        } catch (Throwable notThisBuild) { return false; }
    }

    private static volatile java.lang.reflect.Constructor<?> sHolderCtor;
    private static Object trueHolder(Class<?> holder, Object like) {
        try {
            java.lang.reflect.Constructor<?> k = sHolderCtor;
            if (k == null) {
                for (java.lang.reflect.Constructor<?> c : holder.getDeclaredConstructors()) {
                    Class<?>[] ps = c.getParameterTypes();
                    if (ps.length >= 1 && ps[0].isInterface()) { k = c; break; }
                }
                if (k == null) return null;
                k.setAccessible(true);
                sHolderCtor = k;
            }
            Class<?>[] ps = k.getParameterTypes();
            Object[] args = new Object[ps.length];
            final Class<?> supplier = ps[0];
            args[0] = java.lang.reflect.Proxy.newProxyInstance(supplier.getClassLoader(), new Class<?>[]{supplier}, new java.lang.reflect.InvocationHandler() {
                @Override public Object invoke(Object proxy, Method m, Object[] a) {
                    if ("hashCode".equals(m.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(m.getName())) return proxy == a[0];
                    if ("toString".equals(m.getName())) return "PixelSuite:true";
                    return Boolean.TRUE;
                }
            });
            java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<java.lang.reflect.Field>();
            for (java.lang.reflect.Field f : holder.getDeclaredFields()) if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); fields.add(f); }
            for (int i = 1; i < ps.length; i++) {
                for (java.util.Iterator<java.lang.reflect.Field> it = fields.iterator(); it.hasNext(); ) {
                    java.lang.reflect.Field f = it.next();
                    if (f.getType() == ps[i]) { args[i] = f.get(like); it.remove(); break; }
                }
                if (args[i] == null && ps[i].isPrimitive()) args[i] = ps[i] == boolean.class ? (Object) Boolean.FALSE : ps[i] == int.class ? (Object) 0 : ps[i] == long.class ? (Object) 0L : null;
            }
            return k.newInstance(args);
        } catch (Throwable error) { return null; }
    }

    private static volatile java.lang.reflect.Constructor<?> sDone;
    private static void hookAuto(ClassLoader cl, Map<String, String> names) {
        for (int i = 0; names.containsKey("auto." + i); i++) {
            String v = names.get("auto." + i);
            if (!v.contains("#")) continue;
            try {
                String[] g = v.split("#");
                Method m = Class.forName(g[0], false, cl).getDeclaredMethod(g[1], Object.class);
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        Object r = p.getResult();
                        if (r == null || !automatic()) return;
                        Object yes = done(r, Boolean.TRUE);
                        if (yes != null) p.setResult(yes);
                    }
                });
            } catch (Throwable notThisBuild) { }
        }
    }

    private static Object done(Object future, Object value) {
        try {
            java.lang.reflect.Constructor<?> k = sDone;
            if (k == null || !k.getDeclaringClass().isInstance(future)) {
                for (java.lang.reflect.Constructor<?> c : future.getClass().getDeclaredConstructors()) {
                    Class<?>[] ps = c.getParameterTypes();
                    if (ps.length == 1 && ps[0] == Object.class) { c.setAccessible(true); k = c; break; }
                }
                if (k != null) sDone = k; else k = sDone;
            }
            if (k == null) return null;
            if (!(future instanceof java.util.concurrent.Future)) return null;
            java.util.concurrent.Future<?> f = (java.util.concurrent.Future<?>) future;
            if (f.isDone() && Boolean.TRUE.equals(f.get())) return null;
            return k.newInstance(value);
        } catch (Throwable error) { return null; }
    }

    private static File cacheFile(ApplicationInfo ai) {
        File apk = new File(ai.sourceDir);
        return new File(new File(ai.dataDir, "cache"), "pixelsuite-dialer-names-" + StudioNames.versionCode(ai) + "-" + apk.length() + "-" + apk.lastModified() + ".properties");
    }
    private static Map<String, String> load(File f) {
        if (!f.isFile()) return null;
        try {
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(f);
            try { p.load(in); } finally { in.close(); }
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (String k : p.stringPropertyNames()) m.put(k, p.getProperty(k));
            return m;
        } catch (Throwable unreadable) { return null; }
    }
    private static void save(File f, Map<String, String> m) {
        try {
            File dir = f.getParentFile();
            if (dir != null) {
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File o : old) if (o.getName().startsWith("pixelsuite-dialer-names-")) o.delete();
            }
            Properties p = new Properties();
            for (Map.Entry<String, String> e : m.entrySet()) p.setProperty(e.getKey(), e.getValue());
            FileOutputStream out = new FileOutputStream(f);
            try { p.store(out, "Pixel Suite: Phone app names found by reading the app"); } finally { out.close(); }
        } catch (Throwable notSaved) { }
    }

    private static int installSilence(ClassLoader cl) {
        int n = 0;
        try {
            n += Xp.hookAllMethods(Resources.class, "getText", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
                    int id = (Integer) p.args[0];
                    if (sStartId == -1) ids((Resources) p.thisObject);
                    if ((id == sStartId || id == sEndId) && id != 0 && silent()) p.setResult(MARK);
                }
            }).size();
            Xp.Callback available = new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object r = p.getResult();
                    if (r instanceof Integer && (Integer) r < 0 && on()) { p.setResult(TextToSpeech.LANG_AVAILABLE); }
                }
            };
            n += Xp.hookAllMethods(TextToSpeech.class, "isLanguageAvailable", available).size();
            n += Xp.hookAllMethods(TextToSpeech.class, "setLanguage", available).size();
            n += Xp.hookAllMethods(TextToSpeech.class, "setOnUtteranceProgressListener", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    TextToSpeech t = (TextToSpeech) p.thisObject;
                    if (p.args[0] instanceof UtteranceProgressListener) sListeners.put(t, (UtteranceProgressListener) p.args[0]); else sListeners.remove(t);
                }
            }).size();
            n += Xp.hookAllMethods(TextToSpeech.class, "synthesizeToFile", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length != 4 || !(p.args[0] instanceof CharSequence) || !MARK.equals(p.args[0].toString().trim())) return;
                    try {
                        if (p.args[2] instanceof File) writeSilence(new FileOutputStream((File) p.args[2]));
                        else if (p.args[2] instanceof ParcelFileDescriptor) writeSilence(new FileOutputStream(((ParcelFileDescriptor) p.args[2]).getFileDescriptor()));
                        else return;
                        final UtteranceProgressListener l = sListeners.get((TextToSpeech) p.thisObject);
                        final String id = (String) p.args[3];
                        if (l != null) new Thread(new Runnable() { public void run() {
                            try { Thread.sleep(30); l.onStart(id); l.onDone(id); } catch (Throwable ignored) { }
                        } }, "pixelsuite-silence").start();
                        p.setResult(TextToSpeech.SUCCESS);
                    } catch (Throwable error) { }
                }
            }).size();
        } catch (Throwable error) { }
        try {
            Class<?> files = Class.forName("j$.nio.file.Files", false, cl);
            n += Xp.hookAllMethods(files, "copy", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length < 2 || !(p.args[0] instanceof InputStream) || p.args[1] == null) return;
                    String target = p.args[1].toString();
                    if (!target.contains("/callrecordingprompt/") || !silent()) return;
                    try {
                        p.setResult((long) writeSilence(new FileOutputStream(target)));
                        try { ((InputStream) p.args[0]).close(); } catch (Throwable ignored) { }
                    } catch (Throwable error) { }
                }
            }).size();
        } catch (Throwable missing) { }
        return n;
    }

    private static void ids(Resources r) {
        Context c = app(); if (c == null) return;
        try {
            Resources own = c.getResources();
            sEndId = own.getIdentifier("call_recording_ending_voice", "string", PACKAGE);
            sStartId = own.getIdentifier("call_recording_starting_voice", "string", PACKAGE);
        } catch (Throwable error) { sStartId = 0; }
    }

    private static int writeSilence(OutputStream out) throws IOException {
        int rate = 16000, samples = rate / 4, data = samples * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes("US-ASCII")).putInt(36 + data).put("WAVE".getBytes("US-ASCII"));
        b.put("fmt ".getBytes("US-ASCII")).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes("US-ASCII")).putInt(data);
        try { out.write(b.array()); } finally { out.close(); }
        return b.capacity();
    }

    private static final List<FileObserver> sObservers = new ArrayList<FileObserver>();
    private static Handler sWorker;
    private static final Map<String, Runnable> sPending = new HashMap<String, Runnable>();

    private static void startSaving(final ApplicationInfo ai) {
        HandlerThread t = new HandlerThread("pixelsuite-callrec");
        t.start();
        sWorker = new Handler(t.getLooper());
        sWorker.post(new Runnable() { public void run() {
            if (!unlocked()) { sWorker.postDelayed(this, 5000); return; }
            watch(ai);
        } });
    }

    private static boolean unlocked() {
        Context c = app(); if (c == null) return false;
        try { Object um = c.getSystemService(Context.USER_SERVICE); return (Boolean) um.getClass().getMethod("isUserUnlocked").invoke(um); } catch (Throwable t) { return true; }
    }

    private static void watch(ApplicationInfo ai) {
        List<File> roots = new ArrayList<File>();
        roots.add(new File(ai.dataDir, "files"));
        try { Object de = ApplicationInfo.class.getField("deviceProtectedDataDir").get(ai); if (de != null) roots.add(new File((String) de, "files")); } catch (Throwable ignored) { }
        int waiting = 0;
        for (File files : roots) {
            try {
                File[] old = new File(files, "callrecordingprompt").listFiles();
                if (old != null) for (File f : old) f.delete();
                final File dir = new File(files, "callrecording");
                if (!dir.isDirectory() && files.isDirectory()) dir.mkdirs();
                if (!dir.isDirectory()) continue;
                FileObserver o = new FileObserver(dir.getAbsolutePath(), FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                    @Override public void onEvent(int event, String name) {
                        if (name != null && name.startsWith("record-") && name.endsWith(".wav")) later(new File(dir, name), 3000);
                    }
                };
                o.startWatching();
                sObservers.add(o);
                File[] all = dir.listFiles();
                List<String> copied = readLines(new File(files, "pixelsuite-callrec-copied.txt"));
                if (all != null) for (File f : all)
                    if (f.getName().startsWith("record-") && f.getName().endsWith(".wav") && f.length() > 44 && !copied.contains(f.getName() + ":" + f.length())) { later(f, 15000 + 2000L * waiting); waiting++; }
            } catch (Throwable error) { }
        }
    }

    private static void later(final File f, long delay) {
        synchronized (sPending) {
            Runnable was = sPending.remove(f.getName());
            if (was != null) sWorker.removeCallbacks(was); else hold(1);
            Runnable r = new Runnable() { public void run() {
                synchronized (sPending) { sPending.remove(f.getName()); }
                try { copy(f); } catch (Throwable error) { }
                finally { hold(-1); }
            } };
            sPending.put(f.getName(), r);
            sWorker.postDelayed(r, delay);
        }
    }

    private static PowerManager.WakeLock sWake;
    private static int sBusy;
    private static void hold(int change) {
        synchronized (sPending) {
            sBusy = Math.max(0, sBusy + change);
            try {
                if (sWake == null) {
                    Context c = app(); if (c == null) return;
                    sWake = ((PowerManager) c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
                    sWake.setReferenceCounted(false);
                }
                if (sBusy > 0 && !sWake.isHeld()) sWake.acquire(MAX_AWAKE);
                else if (sBusy == 0 && sWake.isHeld()) sWake.release();
            } catch (Throwable error) { }
        }
    }

    private static void copy(File wav) throws Exception {
        if (!on() || !wav.isFile() || wav.length() <= 44) return;
        Context c = app(); if (c == null) { later(wav, 5000); return; }
        File done = new File(wav.getParentFile().getParentFile(), "pixelsuite-callrec-copied.txt");
        List<String> copied = readLines(done);
        String id = wav.getName() + ":" + wav.length();
        if (copied.contains(id)) return;
        String[] who = new String[1];
        String name = fileName(c, wav, who);
        if (who[0] == null && System.currentTimeMillis() - wav.lastModified() < 60000) { later(wav, 15000); return; }
        cleanPartial(c.getContentResolver(), wav);
        String raw = setting(FOLDER_KEY), folder = folder();
        Uri uri = null;
        if (raw != null && raw.startsWith("tree:")) {
            try { uri = saveThroughPixelSuite(c.getContentResolver(), wav, name); folder = "the picked folder"; }
            catch (Throwable error) { }
        }
        if (uri == null) uri = saveM4a(c.getContentResolver(), wav, folder.equals("the picked folder") ? DEFAULT_FOLDER : folder, name);
        copied.add(id);
        while (copied.size() > 300) copied.remove(0);
        writeLines(done, copied);
    }

    private static String fileName(Context c, File wav, String[] found) {
        long when = wav.lastModified(), start = -1;
        try { long t = Long.parseLong(wav.getName().substring(7, wav.getName().length() - 4)); if (t > 1000000000000L) start = t; } catch (Throwable ignored) { }
        String who = null;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI, new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    null, null, CallLog.Calls.DATE + " DESC LIMIT 10");
            long best = Long.MAX_VALUE;
            while (cur != null && cur.moveToNext()) {
                long date = cur.getLong(2), end = date + cur.getLong(3) * 1000L;
                long off = Math.abs(end - when);
                if (off > 10 * 60 * 1000L || off >= best) continue;
                best = off;
                String nm = cur.getString(1), num = cur.getString(0);
                who = nm != null && !nm.trim().isEmpty() ? nm.trim() : num;
                if (start < 0) start = date;
            }
        } catch (Throwable error) { }
        finally { if (cur != null) cur.close(); }
        found[0] = who;
        String time = new SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(new Date(start > 0 ? start : when));
        String base = (who == null || who.isEmpty() ? "Call" : who) + " " + time;
        return base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private static Uri saveM4a(ContentResolver cr, File wav, String folder, String name) throws Exception {
        String root = folder.contains("/") ? folder.substring(0, folder.indexOf('/')) : folder;
        Uri collection = "Download".equals(root) ? Uri.parse("content://media/" + VOLUME + "/downloads")
                : "Documents".equals(root) ? MediaStore.Files.getContentUri(VOLUME)
                : MediaStore.Audio.Media.getContentUri(VOLUME);
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".m4a");
        v.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
        v.put(RELATIVE_PATH, folder + "/");
        v.put(IS_PENDING, 1);
        Uri uri;
        try { uri = cr.insert(collection, v); }
        catch (Throwable refused) {
            v.put(RELATIVE_PATH, DEFAULT_FOLDER + "/");
            uri = cr.insert(MediaStore.Audio.Media.getContentUri(VOLUME), v);
        }
        if (uri == null) return null;
        partial(wav, uri.toString(), true);
        ParcelFileDescriptor pfd = null;
        try {
            pfd = cr.openFileDescriptor(uri, "rw");
            encode(wav, pfd);
            ContentValues ready = new ContentValues();
            ready.put(IS_PENDING, 0);
            cr.update(uri, ready, null, null);
            partial(wav, uri.toString(), false);
            return uri;
        } catch (Throwable error) {
            try { cr.delete(uri, null, null); } catch (Throwable ignored) { }
            throw error instanceof Exception ? (Exception) error : new RuntimeException(error);
        } finally { if (pfd != null) try { pfd.close(); } catch (Throwable ignored) { } }
    }

    private static Uri saveThroughPixelSuite(ContentResolver cr, File wav, String name) throws Exception {
        Uri base = Uri.parse("content://" + CallRecordingProvider.AUTHORITY);
        android.os.Bundle made = cr.call(base, "create", name + ".m4a", null);
        String doc = made == null ? null : made.getString("doc");
        if (doc == null) throw new IOException("Pixel Suite could not make the file");
        partial(wav, doc, true);
        ParcelFileDescriptor pfd = null;
        try {
            ContentProviderClient client = cr.acquireUnstableContentProviderClient(CallRecordingProvider.AUTHORITY);
            if (client == null) throw new IOException("Pixel Suite is unavailable");
            try { pfd = client.openFile(base.buildUpon().appendPath("doc").appendQueryParameter("u", doc).build(), "rw"); }
            finally { client.release(); }
            if (pfd == null) throw new IOException("Pixel Suite gave no file");
            encode(wav, pfd);
            partial(wav, doc, false);
            return Uri.parse(doc);
        } catch (Throwable error) {
            try { cr.call(base, "delete", doc, null); } catch (Throwable ignored) { }
            throw error instanceof Exception ? (Exception) error : new RuntimeException(error);
        } finally { if (pfd != null) try { pfd.close(); } catch (Throwable ignored) { } }
    }

    private static void encode(File wav, ParcelFileDescriptor out) throws Exception {
        RandomAccessFile in = new RandomAccessFile(wav, "r");
        MediaCodec codec = null; MediaMuxer muxer = null;
        try {
            byte[] h = new byte[12]; in.readFully(h);
            if (!"RIFF".equals(new String(h, 0, 4, "US-ASCII")) || !"WAVE".equals(new String(h, 8, 4, "US-ASCII"))) throw new IOException("not a WAV file");
            int rate = 0, channels = 0, bits = 0; long dataStart = -1, dataLen = 0;
            while (in.getFilePointer() + 8 <= in.length()) {
                byte[] ch = new byte[8]; in.readFully(ch);
                String id = new String(ch, 0, 4, "US-ASCII");
                long len = (ch[4] & 0xffL) | (ch[5] & 0xffL) << 8 | (ch[6] & 0xffL) << 16 | (ch[7] & 0xffL) << 24;
                if ("fmt ".equals(id)) {
                    byte[] f = new byte[(int) Math.min(len, 64)]; in.readFully(f);
                    channels = (f[2] & 0xff) | (f[3] & 0xff) << 8;
                    rate = (f[4] & 0xff) | (f[5] & 0xff) << 8 | (f[6] & 0xff) << 16 | (f[7] & 0xff) << 24;
                    bits = (f[14] & 0xff) | (f[15] & 0xff) << 8;
                    in.seek(in.getFilePointer() - f.length + len + (len & 1));
                } else if ("data".equals(id)) {
                    dataStart = in.getFilePointer();
                    dataLen = (len == 0 || len == 0xffffffffL || dataStart + len > in.length()) ? in.length() - dataStart : len;
                    break;
                } else in.seek(in.getFilePointer() + len + (len & 1));
            }
            if (dataStart < 0 || rate <= 0 || channels <= 0 || bits != 16) throw new IOException("unsupported WAV (" + rate + " Hz, " + channels + " ch, " + bits + " bit)");
            in.seek(dataStart);
            MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels);
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, rate >= 32000 ? 64000 * channels : 32000 * channels);
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            muxer = MediaMuxer.class.getConstructor(java.io.FileDescriptor.class, int.class).newInstance(out.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int track = -1, frame = 2 * channels;
            long fed = 0; boolean inputDone = false, outputDone = false; byte[] chunk = new byte[16384];
            while (!outputDone) {
                if (!inputDone) {
                    int ix = codec.dequeueInputBuffer(10000);
                    if (ix >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(ix); buf.clear();
                        int want = (int) Math.min(Math.min(buf.remaining(), chunk.length), dataLen - fed);
                        want -= want % frame;
                        long pts = (fed / frame) * 1000000L / rate;
                        if (want <= 0) { codec.queueInputBuffer(ix, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                        else { in.readFully(chunk, 0, want); buf.put(chunk, 0, want); fed += want; codec.queueInputBuffer(ix, 0, want, pts, 0); }
                    }
                }
                int ox = codec.dequeueOutputBuffer(info, 10000);
                if (ox == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.getOutputFormat()); muxer.start(); }
                else if (ox >= 0) {
                    ByteBuffer o = codec.getOutputBuffer(ox);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && track >= 0) { o.position(info.offset); o.limit(info.offset + info.size); muxer.writeSampleData(track, o, info); }
                    codec.releaseOutputBuffer(ox, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            if (track < 0) throw new IOException("encoder gave no output");
            muxer.stop();
        } finally {
            if (codec != null) try { codec.stop(); } catch (Throwable ignored) { }
            if (codec != null) try { codec.release(); } catch (Throwable ignored) { }
            if (muxer != null) try { muxer.release(); } catch (Throwable ignored) { }
            in.close();
        }
    }

    private static File partialFile(File wav) { return new File(wav.getParentFile().getParentFile(), "pixelsuite-callrec-partial.txt"); }
    private static void partial(File wav, String uri, boolean add) {
        File f = partialFile(wav);
        List<String> lines = readLines(f);
        String line = wav.getName() + "\t" + uri;
        if (add) { if (!lines.contains(line)) lines.add(line); } else lines.remove(line);
        writeLines(f, lines);
    }
    private static void cleanPartial(ContentResolver cr, File wav) {
        File f = partialFile(wav);
        List<String> lines = readLines(f), keep = new ArrayList<String>();
        boolean changed = false;
        for (String l : lines) {
            int tab = l.indexOf('\t');
            if (tab < 0 || !l.substring(0, tab).equals(wav.getName())) { keep.add(l); continue; }
            changed = true;
            String uri = l.substring(tab + 1);
            try {
                if (uri.startsWith("content://media/")) cr.delete(Uri.parse(uri), null, null);
                else cr.call(Uri.parse("content://" + CallRecordingProvider.AUTHORITY), "delete", uri, null);
            } catch (Throwable error) { }
        }
        if (changed) writeLines(f, keep);
    }

    private static List<String> readLines(File f) {
        List<String> out = new ArrayList<String>();
        if (!f.isFile()) return out;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new FileInputStream(f), "UTF-8"));
            try { String l; while ((l = r.readLine()) != null) if (!l.isEmpty()) out.add(l); } finally { r.close(); }
        } catch (Throwable ignored) { }
        return out;
    }
    private static void writeLines(File f, List<String> lines) {
        try {
            StringBuilder b = new StringBuilder();
            for (String l : lines) b.append(l).append('\n');
            FileOutputStream o = new FileOutputStream(f);
            try { o.write(b.toString().getBytes("UTF-8")); } finally { o.close(); }
        } catch (Throwable ignored) { }
    }
}
