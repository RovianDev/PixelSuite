package io.github.pixelsuite;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

final class FolderSpeed {
    private FolderSpeed() {}

    static final String PKG = "com.android.externalstorage";
    private static final long KEEP_MS = 10000;

    private static final Map<String, Object[]> CACHE = new LinkedHashMap<String, Object[]>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) { return size() > 512; }
    };

    private static final String[] SPARED = { "com.android.externalstorage", "com.google.android.documentsui", "com.android.documentsui", "com.google.android.providers.media.module", "com.android.providers.media.module" };

    static void installSystem(ClassLoader cl) {
        Class<?> rec = Xp.findClass("com.android.server.am.ProcessRecord", cl);
        if (rec == null) return;
        for (Method m : rec.getDeclaredMethods()) {
            if (!m.getName().startsWith("kill") || Modifier.isStatic(m.getModifiers())) continue;
            boolean text = false; for (Class<?> t : m.getParameterTypes()) if (t == String.class) text = true;
            if (!text) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        boolean spam = false;
                        for (Object a : p.args) if (a instanceof String && ((String) a).contains("excessive binder traffic")) { spam = true; break; }
                        if (!spam) return;
                        Object name = Xp.getObjectField(p.thisObject, "processName");
                        for (String keep : SPARED) if (keep.equals(name)) {
                            p.setResult(p.getResult() instanceof Boolean ? Boolean.FALSE : null);
                            return;
                        }
                    } catch (Throwable ignored) { }
                }
            });
        }
    }

    static void install(ClassLoader cl) {
        Class<?> provider = Xp.findClass("com.android.externalstorage.ExternalStorageProvider", cl);
        if (provider == null) { return; }
        for (Class<?> c = provider; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                String name = m.getName();
                if (!("isTrashSupported".equals(name) || "isRestrictedPath".equals(name)) || m.getReturnType() != boolean.class || Modifier.isAbstract(m.getModifiers())) continue;
                final String what = name;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        String k = key(what, p.args); if (k == null) return;
                        synchronized (CACHE) {
                            Object[] hit = CACHE.get(k);
                            if (hit != null && android.os.SystemClock.uptimeMillis() - (Long) hit[1] < KEEP_MS) { p.setResult(hit[0]); Integer h = HITS.get(); HITS.set(h == null ? 1 : h + 1); }
                        }
                    }
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        String k = key(what, p.args); if (k == null || !(p.getResult() instanceof Boolean)) return;
                        synchronized (CACHE) { if (!CACHE.containsKey(k) || android.os.SystemClock.uptimeMillis() - (Long) CACHE.get(k)[1] >= KEEP_MS) CACHE.put(k, new Object[]{p.getResult(), android.os.SystemClock.uptimeMillis()}); }
                    }
                });
            }
        }

        for (Class<?> c = provider; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().startsWith("queryChildDocuments") || Modifier.isAbstract(m.getModifiers())) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        try {
                            Ctx x = enter(p);

                            if (x.owner == p) fromMemory(p, x);
                        } catch (Throwable ignored) { }
                    }
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        Ctx x = CTX.get();
                        try {
                            String order = inOrder(p);
                            if (x != null && order != null) x.order = order;
                            if (x != null && x.owner == p && !x.fromMemory) remember(p, x);
                        } catch (Throwable ignored) { }
                        if (x != null && x.owner == p) leave(x);
                    }
                });
            }
        }

        for (Class<?> c = provider; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (!"includeFile".equals(m.getName()) || t.length != 3 || t[1] != String.class || t[2] != File.class || Modifier.isAbstract(m.getModifiers())) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        Ctx x = CTX.get();
                        if (x == null || p.args[1] != null || !(p.args[2] instanceof File) || p.args[2] instanceof FastFile) return;
                        File f = (File) p.args[2];
                        String parent = f.getParent();
                        if (parent == null || parent.contains("/Android/") || parent.endsWith("/Android")) return;
                        if (x.parent == null) { x.parent = parent; x.firstRow = android.os.SystemClock.uptimeMillis(); } else if (!x.parent.equals(parent)) return;
                        p.args[2] = new FastFile(f.getPath(), x);
                    }
                });
            }
        }

        try {
            Class<?> fsp = Xp.findClass("com.android.internal.content.FileSystemProvider", cl);
            for (Class<?> k : fsp.getDeclaredClasses()) {
                if (!android.os.FileObserver.class.isAssignableFrom(k)) continue;
                for (Method m : k.getDeclaredMethods()) {
                    if (!"onEvent".equals(m.getName())) continue;
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param p) {
                            Object ev = p.args.length > 0 ? p.args[0] : null, name = p.args.length > 1 ? p.args[1] : null;

                            if (ev instanceof Integer && (((Integer) ev) & 0xFFF) == 0) return;
                            try { File f = fileOf(p.thisObject); if (f != null) forget(f.getPath()); else forget(null); } catch (Throwable ignored) { forget(null); }
                        }
                    });
                }
            }
        } catch (Throwable ignored) { }
        String[] changes = { "createDocument", "deleteDocument", "removeDocument", "renameDocument", "moveDocument", "copyDocument", "ejectRoot" };
        for (Class<?> c = provider; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                boolean change = false; for (String w : changes) if (w.equals(m.getName())) change = true;
                if (!change || Modifier.isAbstract(m.getModifiers())) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { forget(null); }
                    @Override protected void afterHookedMethod(Xp.Param p) { forget(null); }
                });
            }
        }
    }

    static final class Ctx {
        Xp.Param owner; int depth; int prio = Integer.MIN_VALUE;
        String key; File dir; long dirTime; boolean fromMemory; String order = "-";
        String parent; int checked; boolean first, mixed; Boolean shared; int reused; long firstRow;

        boolean canWrite(FastFile f) {
            Boolean s = shared;
            if (s != null) { reused++; return s; }
            boolean w = f.realCanWrite();
            if (checked == 0) first = w; else if (w != first) mixed = true;
            checked++;
            if (checked >= 48 && !mixed) shared = first;
            return w;
        }
    }

    static final class FastFile extends File {
        private final Ctx x;
        private android.system.StructStat st;
        private boolean looked, failed;
        FastFile(String path, Ctx x) { super(path); this.x = x; }
        boolean realCanWrite() { return super.canWrite(); }
        @Override public boolean canWrite() { return x.canWrite(this); }

        private android.system.StructStat st() {
            if (!looked) {
                looked = true;
                try { st = android.system.Os.stat(getPath()); }
                catch (android.system.ErrnoException e) { st = null; failed = e.errno != android.system.OsConstants.ENOENT; }
                catch (Throwable t) { st = null; failed = true; }
            }
            return st;
        }
        @Override public boolean exists() { android.system.StructStat s = st(); return failed ? super.exists() : s != null; }
        @Override public boolean isDirectory() { android.system.StructStat s = st(); return failed ? super.isDirectory() : s != null && android.system.OsConstants.S_ISDIR(s.st_mode); }
        @Override public boolean isFile() { android.system.StructStat s = st(); return failed ? super.isFile() : s != null && android.system.OsConstants.S_ISREG(s.st_mode); }
        @Override public long length() { android.system.StructStat s = st(); return failed ? super.length() : s == null ? 0L : s.st_size; }
        @Override public long lastModified() {
            android.system.StructStat s = st();
            if (failed) return super.lastModified();
            if (s == null) return 0L;
            long ms = millis(s);
            return ms != Long.MIN_VALUE ? ms : super.lastModified();
        }
    }

    private static volatile Field sMtim, sSec, sNsec;
    private static volatile boolean sNoMtim;

    private static long millis(android.system.StructStat s) {
        if (sNoMtim) return Long.MIN_VALUE;
        try {
            Field f = sMtim;
            if (f == null) { f = android.system.StructStat.class.getField("st_mtim"); sMtim = f; }
            Object ts = f.get(s);
            if (sSec == null) { sSec = ts.getClass().getField("tv_sec"); sNsec = ts.getClass().getField("tv_nsec"); }
            return sSec.getLong(ts) * 1000L + sNsec.getLong(ts) / 1000000L;
        } catch (Throwable t) {
            sNoMtim = true;
            return Long.MIN_VALUE;
        }
    }

    private static final ThreadLocal<Ctx> CTX = new ThreadLocal<Ctx>();

    private static Ctx enter(Xp.Param p) {
        int depth = new Throwable().getStackTrace().length;
        Ctx x = CTX.get();

        if (x != null && x.depth < depth) return x;
        x = new Ctx();
        x.owner = p; x.depth = depth;
        HITS.set(0);
        CTX.set(x);
        try {
            int tid = android.os.Process.myTid();
            int now = android.os.Process.getThreadPriority(tid);
            if (now > android.os.Process.THREAD_PRIORITY_FOREGROUND) {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
                x.prio = now;
            }
        } catch (Throwable ignored) { }
        return x;
    }

    private static void leave(Ctx x) {
        CTX.remove();
        if (x.prio != Integer.MIN_VALUE) try { android.os.Process.setThreadPriority(x.prio); } catch (Throwable ignored) { }
    }

    private static final int BIG = 300;

    private static final long REMEMBER_MS = 10 * 60 * 1000L;
    private static final long RECENT_MS = 30 * 60 * 1000L;

    private static final class Snap {
        String path; long dirTime; long made; long wall; boolean stale;
        String[] columns; Object[][] rows; Watch watch;
    }

    private static final class Watch extends android.os.FileObserver {
        final String path;
        Watch(String path) {
            super(path, CREATE | DELETE | MOVED_FROM | MOVED_TO | CLOSE_WRITE | ATTRIB | DELETE_SELF | MOVE_SELF);
            this.path = path;
        }
        @Override public void onEvent(int event, String name) {
            if ((event & 0xFFF) == 0) return;
            forget(path);
        }
    }

    private static void unwatch(final java.util.List<Snap> gone) {
        for (Snap s : gone) { Watch w = s.watch; s.watch = null; if (w != null) try { w.stopWatching(); } catch (Throwable ignored) { } }
    }

    private static final java.util.List<Snap> EVICTED = new java.util.ArrayList<Snap>();
    private static final Map<String, Snap> SNAPS = new LinkedHashMap<String, Snap>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Snap> e) { if (size() > 4) { EVICTED.add(e.getValue()); return true; } return false; }
    };

    private static void forget(String path) {
        java.util.List<Snap> gone = new java.util.ArrayList<Snap>();
        synchronized (SNAPS) {
            for (Snap s : SNAPS.values()) {
                if (s.stale || !(path == null || path.equals(s.path) || path.startsWith(s.path + "/"))) continue;
                s.stale = true; gone.add(s);
            }
        }
        unwatch(gone);
    }

    private static void fromMemory(Xp.Param p, Ctx x) throws Throwable {
        if (p.args.length < 2 || !(p.args[0] instanceof String)) return;
        String docId = (String) p.args[0];
        File dir;
        try { dir = (File) Xp.callMethod(p.thisObject, "getFileForDocId", docId); } catch (Throwable notThere) { return; }
        if (dir == null) return;
        String path = dir.getPath();
        if (path.contains("/Android/") || path.endsWith("/Android")) return;
        StringBuilder k = new StringBuilder(String.valueOf(p.method)).append('\n').append(docId).append('\n');
        Object proj = p.args[1];
        if (proj instanceof String[]) { for (String col : (String[]) proj) k.append(col).append(','); } else k.append('*');
        k.append('\n');
        for (int i = 2; i < p.args.length; i++) {
            Object a = p.args[i];
            if (a instanceof android.os.Bundle) {
                android.os.Bundle q = (android.os.Bundle) a;
                java.util.TreeSet<String> keys = new java.util.TreeSet<String>(q.keySet());
                for (String key : keys) {
                    if (key.startsWith("android:query-arg-sort-") || key.equals("android:query-arg-sql-sort-order")) continue;
                    Object v = q.get(key);
                    k.append(key).append('=').append(v instanceof String[] ? java.util.Arrays.toString((String[]) v) : String.valueOf(v)).append(';');
                }
            } else if (a != null && !(a instanceof String)) k.append(a.getClass().getName());
        }
        x.key = k.toString(); x.dir = dir; x.dirTime = dir.lastModified();
        Snap s;
        synchronized (SNAPS) { s = SNAPS.get(x.key); }
        if (s == null) return;
        if (s.stale) return;
        if (s.dirTime != x.dirTime) return;
        long age = android.os.SystemClock.uptimeMillis() - s.made;
        if (age > REMEMBER_MS) return;
        if (!path.equals(s.path)) return;
        android.database.Cursor c = rebuild(p.thisObject, docId, dir, s);
        if (c == null) return;
        x.fromMemory = true;
        p.setResult(c);
    }

    private static void remember(Xp.Param p, Ctx x) {
        if (x.key == null || x.dir == null || !(p.getResult() instanceof android.database.Cursor)) return;
        android.database.Cursor c = (android.database.Cursor) p.getResult();
        int n = c.getCount();
        if (n < BIG) return;
        long now = x.dir.lastModified();
        Snap s = new Snap();
        s.path = x.dir.getPath(); s.dirTime = now; s.made = android.os.SystemClock.uptimeMillis(); s.wall = System.currentTimeMillis();
        s.columns = c.getColumnNames();
        int cols = s.columns.length;
        Object[][] rows = new Object[n][];
        int was = c.getPosition(), i = 0;
        c.moveToPosition(-1);
        while (c.moveToNext() && i < n) {
            Object[] row = new Object[cols];
            for (int j = 0; j < cols; j++) row[j] = value(c, j);
            rows[i++] = row;
        }
        c.moveToPosition(was);
        if (i != n) return;
        s.rows = rows;
        try { s.watch = new Watch(s.path); s.watch.startWatching(); } catch (Throwable t) { s.watch = null; return; }
        java.util.List<Snap> gone = new java.util.ArrayList<Snap>();
        synchronized (SNAPS) {
            Snap old = SNAPS.put(x.key, s);
            if (old != null) gone.add(old);
            gone.addAll(EVICTED); EVICTED.clear();
        }
        unwatch(gone);
    }

    private static volatile java.lang.reflect.Constructor<?> sDirCursor;
    private static volatile boolean sNoDirCursor;

    private static android.database.Cursor rebuild(Object provider, String docId, File dir, Snap s) {
        if (sNoDirCursor) return null;
        try {
            java.lang.reflect.Constructor<?> make = sDirCursor;
            if (make == null) {
                Class<?> fsp = Class.forName("com.android.internal.content.FileSystemProvider", false, provider.getClass().getClassLoader());
                for (Class<?> k : fsp.getDeclaredClasses()) {
                    if (!android.database.MatrixCursor.class.isAssignableFrom(k)) continue;
                    for (java.lang.reflect.Constructor<?> ct : k.getDeclaredConstructors()) {
                        Class<?>[] t = ct.getParameterTypes();
                        if (t.length == 4 && t[0].isAssignableFrom(provider.getClass()) && t[1] == String[].class && t[2] == String.class && t[3] == File.class) { make = ct; break; }
                    }
                }
                if (make == null) { sNoDirCursor = true; return null; }
                make.setAccessible(true);
                sDirCursor = make;
            }
            android.database.MatrixCursor c = (android.database.MatrixCursor) make.newInstance(provider, s.columns, docId, dir);
            int iMod = indexOf(s.columns, "last_modified"), iSize = indexOf(s.columns, "_size"), iName = indexOf(s.columns, "_display_name"), iMime = indexOf(s.columns, "mime_type");
            long recent = s.wall - RECENT_MS;
            for (Object[] row : s.rows) {
                Object[] out = row;

                if (iMod >= 0 && iName >= 0 && row[iMod] instanceof Long && (Long) row[iMod] >= recent && row[iName] instanceof String) {
                    File f = new File(dir, (String) row[iName]);
                    long mod = f.lastModified();
                    if (mod == 0 && !f.exists()) continue;
                    out = row.clone();
                    if (mod > 31536000000L) out[iMod] = mod;
                    boolean folder = iMime >= 0 && "vnd.android.document/directory".equals(row[iMime]);
                    if (iSize >= 0 && row[iSize] != null && !folder) out[iSize] = f.length();
                }
                c.addRow(out);
            }
            return c;
        } catch (Throwable t) {
            sNoDirCursor = true;
            android.util.Log.w("PixelSuite", "folder speed: memory off", t);
            return null;
        }
    }

    private static Field sObserved;

    private static File fileOf(Object observer) throws Exception {
        Field f = sObserved;
        if (f == null || !f.getDeclaringClass().isInstance(observer)) {
            f = null;
            for (Class<?> k = observer.getClass(); k != null && f == null && k != Object.class; k = k.getSuperclass())
                for (Field d : k.getDeclaredFields()) if (d.getType() == File.class && !Modifier.isStatic(d.getModifiers())) { f = d; break; }
            if (f == null) return null;
            f.setAccessible(true);
            sObserved = f;
        }
        return (File) f.get(observer);
    }

    private static final Map<Object, Boolean> SORTED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, Boolean>());
    private static volatile Field sData;
    private static volatile boolean sNoData;

    private static String inOrder(Xp.Param p) {
        Object r = p.getResult();
        if (!(r instanceof android.database.MatrixCursor) || sNoData) return null;
        android.database.MatrixCursor c = (android.database.MatrixCursor) r;
        final int n = c.getCount();
        if (n < BIG || SORTED.containsKey(c)) return null;
        SORTED.put(c, Boolean.TRUE);
        String clause = null; android.os.Bundle args = null;
        for (int i = 2; i < p.args.length; i++) {
            if (p.args[i] instanceof String) { clause = (String) p.args[i]; break; }
            if (p.args[i] instanceof android.os.Bundle) { args = (android.os.Bundle) p.args[i]; break; }
        }
        String column = "last_modified"; boolean down = true;
        if (args != null) {
            clause = args.getString("android:query-arg-sql-sort-order");
            if (clause == null) {
                String[] cols = args.getStringArray("android:query-arg-sort-columns");
                if (cols != null && cols.length > 0) { column = cols[0]; down = args.getInt("android:query-arg-sort-direction", 0) == 1; }
            }
        }
        if (clause != null && clause.trim().length() > 0) {
            String t = clause.trim();
            int cut = 0; while (cut < t.length() && t.charAt(cut) != ' ' && t.charAt(cut) != ',') cut++;
            column = t.substring(0, cut);
            down = t.toUpperCase(java.util.Locale.ROOT).contains(" DESC");
        }
        try {
            Field f = sData;
            if (f == null) { f = android.database.MatrixCursor.class.getDeclaredField("data"); f.setAccessible(true); sData = f; }
            final Object[] data = (Object[]) f.get(c);
            final int cols = c.getColumnCount();
            final int key = c.getColumnIndex(column), id = c.getColumnIndex("document_id"), mime = c.getColumnIndex("mime_type");
            if (data == null || key < 0 || id < 0 || data.length < n * cols) return "unknown " + column;
            final int kind = "_display_name".equals(column) ? 1 : "mime_type".equals(column) ? 2 : "_size".equals(column) ? 3 : "last_modified".equals(column) ? 4 : 0;
            if (kind == 0) return "unknown " + column;
            final boolean desc = down;
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
                @Override public int compare(Integer a, Integer b) {
                    int ra = a * cols, rb = b * cols;
                    boolean da = mime >= 0 && "vnd.android.document/directory".equals(data[ra + mime]);
                    boolean db = mime >= 0 && "vnd.android.document/directory".equals(data[rb + mime]);
                    if (da != db) return da ? -1 : 1;
                    int k;
                    if (kind <= 2) k = text(data[ra + key], data[rb + key]);
                    else k = Long.compare(number(data[ra + key], kind), number(data[rb + key], kind));
                    if (desc) k = -k;
                    if (k == 0) k = String.valueOf(data[ra + id]).compareTo(String.valueOf(data[rb + id]));
                    return k;
                }
            });
            Object[] out = new Object[data.length];
            for (int i = 0; i < n; i++) System.arraycopy(data, order[i] * cols, out, i * cols, cols);
            f.set(c, out);
            return column + (desc ? " desc" : " asc");
        } catch (Throwable t) {
            sNoData = true;
            android.util.Log.w("PixelSuite", "folder speed: order off", t);
            return null;
        }
    }

    private static int text(Object a, Object b) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static long number(Object v, int kind) {
        long l = -1;
        if (v instanceof Number) l = ((Number) v).longValue();
        else if (v != null) try { l = Long.parseLong(v.toString()); } catch (NumberFormatException ignored) { }
        return kind == 4 && l == -1 ? Long.MAX_VALUE : l;
    }

    private static int indexOf(String[] a, String s) {
        for (int i = 0; i < a.length; i++) if (s.equals(a[i])) return i;
        return -1;
    }

    private static Object value(android.database.Cursor c, int i) {
        int type;
        try { type = c.getType(i); } catch (Throwable t) { type = -1; }
        try {
            switch (type) {
                case android.database.Cursor.FIELD_TYPE_INTEGER: return c.getLong(i);
                case android.database.Cursor.FIELD_TYPE_FLOAT: return c.getDouble(i);
                case android.database.Cursor.FIELD_TYPE_BLOB: return c.getBlob(i);
                case android.database.Cursor.FIELD_TYPE_STRING: return c.getString(i);
                default: break;
            }
        } catch (Throwable ignored) { }
        try { return c.getString(i); } catch (Throwable t) { return null; }
    }

    static void installFiles(ClassLoader cl) {
        Xp.Callback copy = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                for (int i = 0; i < p.args.length; i++) {
                    if (!(p.args[i] instanceof android.database.Cursor)) continue;
                    if (p.args[i] instanceof MemCursor) return;
                    android.database.Cursor c = (android.database.Cursor) p.args[i];
                    try {
                        if (c.isClosed() || c.getCount() < 1000) return;
                        android.database.Cursor m = MemCursor.of(c);
                        if (m == null) return;
                        p.args[i] = m;
                    } catch (Throwable ignored) { }
                    return;
                }
            }
        };
        try { Xp.hookAllMethods(Xp.findClass("com.android.documentsui.sorting.SortModel", cl), "sortCursor", copy); } catch (Throwable ignored) { }
        try { Xp.hookAllConstructors(Xp.findClass("com.android.documentsui.sorting.SortingCursorWrapper", cl), copy); } catch (Throwable ignored) { }
    }

    static final class MemCursor extends android.database.CursorWrapper {
        private final android.database.MatrixCursor mem;
        private MemCursor(android.database.Cursor real, android.database.MatrixCursor mem) { super(real); this.mem = mem; }

        static android.database.Cursor of(android.database.Cursor c) {
            String[] cols = c.getColumnNames();
            int n = c.getCount();
            android.database.MatrixCursor m = new android.database.MatrixCursor(cols, Math.max(n, 1));
            Object[] row = new Object[cols.length];
            int was = c.getPosition();
            if (c.moveToFirst()) {
                boolean[] text = new boolean[cols.length];
                for (int i = 0; i < cols.length; i++) text[i] = asText(c, i);
                do {
                    for (int i = 0; i < cols.length; i++) {
                        if (text[i]) { try { row[i] = c.getString(i); } catch (Throwable t) { row[i] = null; } }
                        else row[i] = value(c, i);
                    }
                    m.addRow(row);
                } while (c.moveToNext());
            }
            c.moveToPosition(was);
            if (m.getCount() != n) { m.close(); return null; }
            return new MemCursor(c, m);
        }

        private static boolean asText(android.database.Cursor c, int i) {
            int type;
            try { type = c.getType(i); } catch (Throwable t) { return true; }
            try {
                switch (type) {
                    case android.database.Cursor.FIELD_TYPE_INTEGER: c.getLong(i); return false;
                    case android.database.Cursor.FIELD_TYPE_FLOAT: c.getDouble(i); return false;
                    case android.database.Cursor.FIELD_TYPE_BLOB: c.getBlob(i); return false;
                    case android.database.Cursor.FIELD_TYPE_STRING: c.getString(i); return false;
                    default: try { return c.getString(i) != null; } catch (Throwable t) { return false; }
                }
            } catch (Throwable t) { return true; }
        }

        @Override public int getCount() { return mem.getCount(); }
        @Override public int getPosition() { return mem.getPosition(); }
        @Override public boolean move(int offset) { return mem.move(offset); }
        @Override public boolean moveToPosition(int position) { return mem.moveToPosition(position); }
        @Override public boolean moveToFirst() { return mem.moveToFirst(); }
        @Override public boolean moveToLast() { return mem.moveToLast(); }
        @Override public boolean moveToNext() { return mem.moveToNext(); }
        @Override public boolean moveToPrevious() { return mem.moveToPrevious(); }
        @Override public boolean isFirst() { return mem.isFirst(); }
        @Override public boolean isLast() { return mem.isLast(); }
        @Override public boolean isBeforeFirst() { return mem.isBeforeFirst(); }
        @Override public boolean isAfterLast() { return mem.isAfterLast(); }
        @Override public int getColumnIndex(String name) { return mem.getColumnIndex(name); }
        @Override public int getColumnIndexOrThrow(String name) { return mem.getColumnIndexOrThrow(name); }
        @Override public String getColumnName(int i) { return mem.getColumnName(i); }
        @Override public String[] getColumnNames() { return mem.getColumnNames(); }
        @Override public int getColumnCount() { return mem.getColumnCount(); }
        @Override public byte[] getBlob(int i) { return mem.getBlob(i); }
        @Override public String getString(int i) { return mem.getString(i); }
        @Override public void copyStringToBuffer(int i, android.database.CharArrayBuffer b) { mem.copyStringToBuffer(i, b); }
        @Override public short getShort(int i) { return mem.getShort(i); }
        @Override public int getInt(int i) { return mem.getInt(i); }
        @Override public long getLong(int i) { return mem.getLong(i); }
        @Override public float getFloat(int i) { return mem.getFloat(i); }
        @Override public double getDouble(int i) { return mem.getDouble(i); }
        @Override public int getType(int i) { return mem.getType(i); }
        @Override public boolean isNull(int i) { return mem.isNull(i); }
        @Override public void close() { try { mem.close(); } catch (Throwable ignored) { } super.close(); }
    }

    private static final ThreadLocal<Integer> HITS = new ThreadLocal<Integer>();

    private static String key(String what, Object[] args) {
        String path = null;
        for (Object a : args) {
            if (a instanceof File) { path = ((File) a).getPath(); break; }
            if (a instanceof String && ((String) a).indexOf('/') >= 0) { path = (String) a; break; }
        }
        if (path == null) return null;
        int cut = path.lastIndexOf('/');
        if (cut <= 0 || cut == path.length() - 1) return null;
        String name = path.substring(cut + 1);

        if (path.contains("/Android/") || path.endsWith("/Android")) return null;
        return what + "|" + path.substring(0, cut) + "|" + (name.indexOf('.') > 0 ? "f" : "d");
    }
}
