package io.github.pixelsuite;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SearchIndex {
    static final int JOB_ID = 7311, WATCH_ID = 7312;
    static final String AUTO_KEY = "pixelsuite_search_auto_index";
    private static volatile boolean sSyncing;
    private static final Object LOCK = new Object();
    private static SQLiteDatabase sDb;
    private static volatile boolean sRunning;
    private static volatile int sDone;

    private SearchIndex() {}

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("pixel_search", Context.MODE_PRIVATE); }

    static SQLiteDatabase db(Context c) {
        synchronized (LOCK) {
            if (sDb != null && sDb.isOpen()) return sDb;
            File f = c.getApplicationContext().getDatabasePath("pixel_search.db");
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(f, null);
            try { db.enableWriteAheadLogging(); } catch (Throwable ignored) { }
            db.execSQL("CREATE TABLE IF NOT EXISTS files(id INTEGER PRIMARY KEY, path TEXT UNIQUE, name TEXT, folder TEXT, ext TEXT, size INTEGER, mtime INTEGER, gen INTEGER, body INTEGER)");
            db.execSQL("CREATE INDEX IF NOT EXISTS files_name ON files(name)");
            boolean made = false;
            try { db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS docs USING fts4(name, body, tokenize=unicode61 \"remove_diacritics=1\")"); made = true; } catch (Throwable ignored) { }
            if (!made) db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS docs USING fts4(name, body)");
            sDb = db;
            return db;
        }
    }

    static File root() { return Environment.getExternalStorageDirectory(); }

    static boolean access() {
        try {
            Object ok = Environment.class.getMethod("isExternalStorageManager").invoke(null);
            if (ok instanceof Boolean && !(Boolean) ok) return false;
        } catch (Throwable ignored) { }
        File r = root();
        return r != null && r.canRead() && r.list() != null;
    }

    static boolean running() { return sRunning; }
    static int progress() { return sDone; }

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            watch(c, auto(c));
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
            JobInfo.Builder b = new JobInfo.Builder(JOB_ID, new ComponentName(c, SearchIndexJob.class)).setPeriodic(6L * 60 * 60 * 1000).setPersisted(true);
            try { JobInfo.Builder.class.getMethod("setRequiresBatteryNotLow", boolean.class).invoke(b, true); } catch (Throwable ignored) { }
            js.schedule(b.build());
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: schedule", t); }
    }

    static void cancel(Context c) {
        try { JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE); if (js != null) { js.cancel(JOB_ID); js.cancel(WATCH_ID); } } catch (Throwable ignored) { }
    }

    static boolean auto(Context c) { return PixelSearch.number(c, AUTO_KEY, 1) == 1; }

    static void watch(Context c, boolean on) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            if (!on) { js.cancel(WATCH_ID); return; }
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == WATCH_ID) return;
            JobInfo.Builder b = new JobInfo.Builder(WATCH_ID, new ComponentName(c, SearchIndexJob.class));
            String[] kinds = {"content://media/external/file", "content://media/external/images/media", "content://media/external/video/media", "content://media/external/audio/media", "content://media/external/downloads"};
            Class<?> trigger = Class.forName("android.app.job.JobInfo$TriggerContentUri");
            java.lang.reflect.Constructor<?> make = trigger.getConstructor(android.net.Uri.class, int.class);
            java.lang.reflect.Method add = JobInfo.Builder.class.getMethod("addTriggerContentUri", trigger);
            for (String k : kinds) add.invoke(b, make.newInstance(android.net.Uri.parse(k), 1));
            JobInfo.Builder.class.getMethod("setTriggerContentUpdateDelay", long.class).invoke(b, 1200L);
            JobInfo.Builder.class.getMethod("setTriggerContentMaxDelay", long.class).invoke(b, 5000L);
            js.schedule(b.build());
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: watch", t); }
    }

    private static long mediaGeneration(Context c) {
        try {
            Object g = android.provider.MediaStore.class.getMethod("getGeneration", Context.class, String.class).invoke(null, c, "external_primary");
            if (g instanceof Long) return (Long) g;
        } catch (Throwable ignored) { }
        return -1;
    }

    private static boolean skipped(String path, String rootPath) {
        if (!path.startsWith(rootPath + "/")) return true;
        String rel = path.substring(rootPath.length());
        return rel.contains("/.") || rel.startsWith("/Android/data/") || rel.startsWith("/Android/obb/") || rel.contains("/node_modules/") || rel.contains("/lost+found/") || depth(path, rootPath) > 14;
    }

    static void sync(Context c, boolean gone) {
        if (!PixelSearch.active(c) || !PatronGate.ok(c) || !auto(c)) return;
        SharedPreferences p = prefs(c);
        long last = p.getLong("last", 0);
        if (last == 0 || sRunning) return;
        synchronized (LOCK) {
            if (sSyncing) return;
            sSyncing = true;
        }
        try {
            if (!access()) return;
            SQLiteDatabase db = db(c);
            String rootPath = root().getAbsolutePath();
            long began = System.currentTimeMillis();
            long generation = mediaGeneration(c), seen = p.getLong("media", -1);
            long since = Math.min(p.getLong("synced", last), last) / 1000 - 3;
            String where; String[] args;
            if (generation >= 0 && seen >= 0 && seen <= generation) { where = "generation_modified>?"; args = new String[]{String.valueOf(seen)}; }
            else { where = "date_modified>=? OR date_added>=?"; args = new String[]{String.valueOf(since), String.valueOf(since)}; }
            ArrayList<String> paths = new ArrayList<String>();
            Cursor q = null;
            try {
                q = c.getContentResolver().query(android.net.Uri.parse("content://media/external/file"), new String[]{"_data"}, where, args, null);
                while (q != null && q.moveToNext() && paths.size() <= 3000) { String d = q.getString(0); if (d != null) paths.add(d); }
            } finally { if (q != null) q.close(); }
            if (paths.size() > 3000) { sSyncing = false; refreshSoon(c, 0); return; }
            SQLiteStatement find = db.compileStatement("SELECT size || ':' || mtime || ':' || id FROM files WHERE path=?");
            int changed = 0;
            boolean moved = gone;
            for (String path : paths) {
                if (skipped(path, rootPath)) continue;
                File f = new File(path);
                if (!f.isFile()) { if (!f.exists()) moved = true; continue; }
                long size = f.length(), mtime = f.lastModified();
                String known = null;
                find.bindString(1, path);
                try { known = find.simpleQueryForString(); } catch (Throwable ignored) { }
                if (known != null) {
                    String[] k = known.split(":");
                    if (k.length == 3 && k[0].equals(String.valueOf(size)) && k[1].equals(String.valueOf(mtime))) continue;
                }
                String name = f.getName(), ext = ext(name);
                String body = SearchText.readable(ext) ? SearchText.extract(f, ext) : null;
                if (known == null) {
                    ArrayList<String> twins = new ArrayList<String>();
                    Cursor t = db.rawQuery("SELECT path FROM files WHERE size=? AND mtime=?", new String[]{String.valueOf(size), String.valueOf(mtime)});
                    try { while (t.moveToNext()) twins.add(t.getString(0)); } finally { t.close(); }
                    for (String old : twins) if (!new File(old).exists()) forget(c, old);
                }
                db.beginTransaction();
                try {
                    put(db, known == null ? -1 : Long.parseLong(known.substring(known.lastIndexOf(':') + 1)), path, name, folder(path, rootPath), ext, size, mtime, 0, body);
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                changed++;
            }
            boolean pruned = false;
            if (moved || p.getBoolean("stale", false)) {
                if (began - p.getLong("pruned", 0) > 90000L) { prune(c, db); pruned = true; }
                else p.edit().putBoolean("stale", true).apply();
            }
            SharedPreferences.Editor e = p.edit().putLong("synced", began);
            if (generation >= 0) e.putLong("media", generation);
            if (pruned) e.putLong("pruned", began).putBoolean("stale", false);
            if (changed > 0 || pruned) {
                long count = 0, docs = 0;
                Cursor n = db.rawQuery("SELECT COUNT(*), SUM(body=1) FROM files", null);
                try { if (n.moveToFirst()) { count = n.getLong(0); docs = n.getLong(1); } } finally { n.close(); }
                e.putLong("files", count).putLong("docs", docs);
            }
            if (!sRunning) e.putLong("last", System.currentTimeMillis());
            e.apply();
        } catch (Throwable t) {
            android.util.Log.w("PixelSuite", "search: sync", t);
        } finally {
            sSyncing = false;
        }
    }

    private static void prune(Context c, SQLiteDatabase db) {
        if (!access()) return;
        ArrayList<Long> dead = new ArrayList<Long>();
        Cursor q = db.rawQuery("SELECT id, path FROM files", null);
        try { while (q.moveToNext()) { if (sRunning) return; if (!new File(q.getString(1)).exists()) dead.add(q.getLong(0)); } } finally { q.close(); }
        if (dead.isEmpty() || !access()) return;
        db.beginTransaction();
        try {
            for (Long id : dead) { String[] one = {String.valueOf(id)}; db.delete("docs", "docid=?", one); db.delete("files", "id=?", one); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    static void refreshSoon(final Context c, long olderThan) {
        final Context app = c.getApplicationContext();
        if (sRunning) return;
        long last = prefs(app).getLong("last", 0);
        if (System.currentTimeMillis() - last < olderThan) return;
        new Thread(new Runnable() { public void run() { crawl(app); } }, "PixelSearchIndex").start();
    }

    static void crawl(Context c) {
        if (!PixelSearch.active(c) || !PatronGate.ok(c)) return;
        synchronized (SearchIndex.class) {
            if (sRunning) return;
            sRunning = true;
        }
        sDone = 0;
        try {
            if (!access()) return;
            SQLiteDatabase db = db(c);
            long gen = System.currentTimeMillis();
            File base = root();
            String rootPath = base.getAbsolutePath();
            SQLiteStatement find = db.compileStatement("SELECT size || ':' || mtime || ':' || id FROM files WHERE path=?");
            SQLiteStatement touch = db.compileStatement("UPDATE files SET gen=? WHERE id=?");
            ArrayDeque<File> dirs = new ArrayDeque<File>();
            dirs.add(base);
            int files = 0, texts = 0, batch = 0;
            long started = SystemClock.uptimeMillis();
            db.beginTransaction();
            try {
                while (!dirs.isEmpty() && files < 250000) {
                    File dir = dirs.poll();
                    File[] list = dir.listFiles();
                    if (list == null) continue;
                    for (File f : list) {
                        String name = f.getName();
                        if (name.startsWith(".")) continue;
                        if (f.isDirectory()) {
                            String p = f.getAbsolutePath();
                            if (p.equals(rootPath + "/Android/data") || p.equals(rootPath + "/Android/obb") || "node_modules".equals(name) || "lost+found".equals(name)) continue;
                            if (depth(p, rootPath) < 14) dirs.add(f);
                            continue;
                        }
                        if (!f.isFile()) continue;
                        files++;
                        String path = f.getAbsolutePath();
                        long size = f.length(), mtime = f.lastModified();
                        String known = null;
                        find.bindString(1, path);
                        try { known = find.simpleQueryForString(); } catch (Throwable ignored) { }
                        if (known != null) {
                            String[] k = known.split(":");
                            if (k.length == 3 && k[0].equals(String.valueOf(size)) && k[1].equals(String.valueOf(mtime))) {
                                touch.bindLong(1, gen); touch.bindLong(2, Long.parseLong(k[2])); touch.executeUpdateDelete();
                                if (++batch % 400 == 0) { db.setTransactionSuccessful(); db.endTransaction(); db.beginTransaction(); }
                                continue;
                            }
                        }
                        String ext = ext(name);
                        String body = SearchText.readable(ext) ? SearchText.extract(f, ext) : null;
                        if (body != null) texts++;
                        put(db, known == null ? -1 : Long.parseLong(known.substring(known.lastIndexOf(':') + 1)), path, name, folder(path, rootPath), ext, size, mtime, gen, body);
                        sDone = files;
                        if (++batch % (body != null ? 20 : 400) == 0) { db.setTransactionSuccessful(); db.endTransaction(); db.beginTransaction(); }
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            db.beginTransaction();
            try {
                db.execSQL("DELETE FROM docs WHERE docid IN (SELECT id FROM files WHERE gen<>?)", new Object[]{gen});
                db.execSQL("DELETE FROM files WHERE gen<>?", new Object[]{gen});
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            long count = 0, docs = 0;
            Cursor q = db.rawQuery("SELECT COUNT(*), SUM(body=1) FROM files", null);
            try { if (q.moveToFirst()) { count = q.getLong(0); docs = q.getLong(1); } } finally { q.close(); }
            prefs(c).edit().putLong("last", System.currentTimeMillis()).putLong("files", count).putLong("docs", docs).putLong("took", SystemClock.uptimeMillis() - started).apply();
        } catch (Throwable t) {
            android.util.Log.w("PixelSuite", "search: index", t);
        } finally {
            sRunning = false;
        }
    }

    private static int depth(String p, String root) {
        int n = 0;
        for (int i = root.length(); i < p.length(); i++) if (p.charAt(i) == '/') n++;
        return n;
    }

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 || i == name.length() - 1 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static String folder(String path, String root) {
        int i = path.lastIndexOf('/');
        String f = i <= 0 ? "" : path.substring(0, i);
        if (f.startsWith(root)) f = f.substring(root.length());
        if (f.startsWith("/")) f = f.substring(1);
        return f;
    }

    private static void put(SQLiteDatabase db, long id, String path, String name, String folder, String ext, long size, long mtime, long gen, String body) {
        ContentValues v = new ContentValues();
        v.put("path", path); v.put("name", name); v.put("folder", folder); v.put("ext", ext); v.put("size", size); v.put("mtime", mtime); v.put("gen", gen);
        v.put("body", body == null ? 0 : 1);
        if (id >= 0) {
            db.update("files", v, "id=?", new String[]{String.valueOf(id)});
            db.delete("docs", "docid=?", new String[]{String.valueOf(id)});
        } else id = db.insert("files", null, v);
        if (id < 0) return;
        ContentValues d = new ContentValues();
        d.put("docid", id); d.put("name", spaced(name)); d.put("body", body == null ? "" : body);
        db.insert("docs", null, d);
    }

    static String spaced(String name) {
        StringBuilder b = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(name.charAt(i - 1))) b.append(' ');
            b.append(c == '_' || c == '-' || c == '.' ? ' ' : c);
        }
        return b.toString();
    }

    static List<String> tokens(String q) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i <= q.length(); i++) {
            char c = i < q.length() ? q.charAt(i) : ' ';
            if (Character.isLetterOrDigit(c)) cur.append(Character.toLowerCase(c));
            else if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
        }
        return out;
    }

    static String match(String column, List<String> tokens) {
        StringBuilder b = new StringBuilder();
        for (String t : tokens) { if (b.length() > 0) b.append(' '); if (column != null) b.append(column).append(':'); b.append(t).append('*'); }
        return b.toString();
    }

    static final class Hit {
        String path, name, folder, ext, snippet; long size, mtime; int page;
    }

    static List<Hit> names(Context c, String q, int limit) {
        ArrayList<Hit> out = new ArrayList<Hit>();
        List<String> tokens = tokens(q);
        if (tokens.isEmpty()) return out;
        SQLiteDatabase db = db(c);
        ArrayList<String> seen = new ArrayList<String>();
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT f.path, f.name, f.folder, f.ext, f.size, f.mtime FROM docs JOIN files f ON f.id=docs.docid WHERE docs MATCH ? ORDER BY f.mtime DESC LIMIT ?", new String[]{match("name", tokens), String.valueOf(limit)});
            while (cur.moveToNext()) { Hit h = hit(cur); seen.add(h.path); out.add(h); }
        } catch (Throwable ignored) { } finally { if (cur != null) cur.close(); }
        if (out.size() < limit && q.trim().length() >= 2) {
            try {
                String like = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                cur = db.rawQuery("SELECT path, name, folder, ext, size, mtime FROM files WHERE name LIKE ? ESCAPE '\\' ORDER BY mtime DESC LIMIT ?", new String[]{like, String.valueOf(limit)});
                while (cur.moveToNext() && out.size() < limit) { Hit h = hit(cur); if (!seen.contains(h.path)) out.add(h); }
            } catch (Throwable ignored) { } finally { if (cur != null) cur.close(); }
        }
        return out;
    }

    static List<Hit> content(Context c, String q, int limit) {
        ArrayList<Hit> out = new ArrayList<Hit>();
        List<String> tokens = tokens(q);
        if (tokens.isEmpty()) return out;
        SQLiteDatabase db = db(c);
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT f.path, f.name, f.folder, f.ext, f.size, f.mtime, snippet(docs, '\u0001', '\u0002', '…', 1, 16), docs.docid FROM docs JOIN files f ON f.id=docs.docid WHERE docs MATCH ? AND f.body=1 LIMIT ?", new String[]{match("body", tokens), String.valueOf(limit)});
            int n = 0;
            while (cur.moveToNext()) {
                Hit h = hit(cur);
                h.snippet = cur.getString(6);
                if (n++ < 10) h.page = page(db, cur.getLong(7), tokens.get(0), h.ext);
                out.add(h);
            }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: content", t); } finally { if (cur != null) cur.close(); }
        return out;
    }

    private static int page(SQLiteDatabase db, long docid, String token, String ext) {
        if (!"pdf".equals(ext) && !"xlsx".equals(ext) && !"xls".equals(ext) && !"pptx".equals(ext)) return 0;
        Cursor cur = db.rawQuery("SELECT body FROM docs WHERE docid=?", new String[]{String.valueOf(docid)});
        try {
            if (!cur.moveToFirst()) return 0;
            String body = cur.getString(0);
            if (body == null) return 0;
            String low = body.toLowerCase(Locale.ROOT);
            int at = low.indexOf(token);
            if (at < 0) return 0;
            int page = 1;
            for (int i = 0; i < at; i++) if (body.charAt(i) == '\f') page++;
            return page;
        } finally { cur.close(); }
    }

    private static Hit hit(Cursor c) {
        Hit h = new Hit();
        h.path = c.getString(0); h.name = c.getString(1); h.folder = c.getString(2); h.ext = c.getString(3); h.size = c.getLong(4); h.mtime = c.getLong(5);
        return h;
    }

    static void forget(Context c, String path) {
        try {
            SQLiteDatabase db = db(c);
            android.database.Cursor cur = db.rawQuery("SELECT id FROM files WHERE path=?", new String[]{path});
            long id = -1;
            try { if (cur.moveToFirst()) id = cur.getLong(0); } finally { cur.close(); }
            if (id < 0) return;
            db.delete("docs", "docid=?", new String[]{String.valueOf(id)});
            db.delete("files", "id=?", new String[]{String.valueOf(id)});
        } catch (Throwable ignored) { }
    }

    static void clear(Context c) {
        try {
            SQLiteDatabase db = db(c);
            db.execSQL("DELETE FROM docs");
            db.execSQL("DELETE FROM files");
            prefs(c).edit().remove("last").remove("files").remove("docs").apply();
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: clear", t); }
    }
}
