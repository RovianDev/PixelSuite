package io.github.pixelsuite;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.format.DateUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class SearchProvider extends ContentProvider {
    static final String[] COLUMNS = {"kind", "title", "sub", "data", "extra", "page", "id"};
    private static final HashMap<String, ArrayList<String[]>> SHORTCUTS = new HashMap<String, ArrayList<String[]>>();

    @Override public boolean onCreate() { return true; }

    private boolean allowed() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return true;
        try {
            String[] names = getContext().getPackageManager().getPackagesForUid(uid);
            if (names != null) for (String n : names) if (PixelSearch.LAUNCHER.equals(n) || "com.android.systemui".equals(n)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    private boolean ready() {
        Context c = getContext();
        return c != null && allowed() && PixelSearch.active(c) && PatronGate.ok(c);
    }

    static String token(Context c) {
        android.content.SharedPreferences p = SearchIndex.prefs(c);
        String t = p.getString("token", null);
        if (t == null) {
            byte[] b = new byte[16]; new SecureRandom().nextBytes(b);
            StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xff));
            t = s.toString(); p.edit().putString("token", t).apply();
        }
        return t;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if (!allowed()) return out;
        Context c = getContext();
        long id = Binder.clearCallingIdentity();
        try {
            if ("token".equals(method)) { if (PixelSearch.active(c) && PatronGate.ok(c)) out.putString("token", token(c)); }
            else if ("refresh".equals(method)) { if (ready()) { SearchIndex.schedule(c); SearchIndex.refreshSoon(c, 20L * 60 * 1000); } }
            else if ("delete".equals(method) && arg != null) { if (ready()) out.putBoolean("ok", remove(c, arg)); }
            else if ("status".equals(method)) {
                out.putBoolean("access", SearchIndex.access()); out.putBoolean("running", SearchIndex.running());
                out.putLong("files", SearchIndex.prefs(c).getLong("files", 0)); out.putLong("last", SearchIndex.prefs(c).getLong("last", 0));
            }
            else if ("shortcuts".equals(method) && arg != null && extras != null) {
                ArrayList<String> ids = extras.getStringArrayList("ids"), labels = extras.getStringArrayList("labels");
                ArrayList<String[]> list = new ArrayList<String[]>();
                if (ids != null && labels != null) for (int i = 0; i < ids.size() && i < labels.size(); i++) list.add(new String[]{ids.get(i), labels.get(i)});
                synchronized (SHORTCUTS) { SHORTCUTS.put(arg, list); SHORTCUTS.notifyAll(); }
            }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: call " + method, t); }
        finally { Binder.restoreCallingIdentity(id); }
        return out;
    }

    static void forgetShortcuts(String pkg) { synchronized (SHORTCUTS) { SHORTCUTS.remove(pkg); } }

    static List<String[]> waitShortcuts(String pkg, long ms) {
        long until = System.currentTimeMillis() + ms;
        synchronized (SHORTCUTS) {
            while (!SHORTCUTS.containsKey(pkg)) {
                long left = until - System.currentTimeMillis();
                if (left <= 0) return null;
                try { SHORTCUTS.wait(left); } catch (InterruptedException e) { return null; }
            }
            return SHORTCUTS.remove(pkg);
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        MatrixCursor out = new MatrixCursor(COLUMNS);
        if (!ready() || args == null || args.length == 0) return out;
        try { SearchMoney.refreshSoon(getContext()); } catch (Throwable ignored) { }
        String q = args[0] == null ? "" : args[0].trim();
        if (q.isEmpty()) return out;
        List<String> scopes = new ArrayList<String>();
        if (args.length > 1 && args[1] != null) for (String s : args[1].split(",")) if (!s.isEmpty()) scopes.add(s);
        Context c = getContext();
        long id = Binder.clearCallingIdentity();
        try {
            for (String s : scopes) {
                try {
                    if ("contacts".equals(s) && PixelSearch.STARRED.equalsIgnoreCase(q)) {
                        try (Cursor p = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, ContactsContract.CommonDataKinds.Phone.STARRED + "=1", null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
                            java.util.HashSet<Long> seen = new java.util.HashSet<Long>();
                            while (p != null && p.moveToNext()) {
                                if (p.getString(2) == null || !seen.add(p.getLong(0))) continue;
                                out.addRow(new Object[]{"contacts", p.getString(1) == null ? p.getString(2) : p.getString(1), p.getString(2), String.valueOf(p.getLong(0)), p.getString(2), 0, ""});
                            }
                        }
                    } else if ("contacts".equals(s)) contacts(c, q, out);
                    else if ("files".equals(s)) files(c, q, out);
                    else if ("content".equals(s)) content(c, q, out);
                    else if ("calendar".equals(s)) calendar(c, q, out);
                    else if ("settings".equals(s)) suite(c, q, out);
                } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: " + s, t); }
            }
        } finally { Binder.restoreCallingIdentity(id); }
        return out;
    }

    private static boolean granted(Context c, String permission) {
        return c.checkPermission(permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED;
    }

    private static void contacts(Context c, String q, MatrixCursor out) {
        if (!granted(c, "android.permission.READ_CONTACTS")) return;
        String digits = q.replaceAll("[^0-9+]", "");
        boolean number = digits.replace("+", "").length() >= 3 && digits.length() * 2 >= q.replace(" ", "").length();
        LinkedHashMap<Long, String[]> found = new LinkedHashMap<Long, String[]>();
        if (number) {
            Uri u = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(digits));
            Cursor cur = c.getContentResolver().query(u, new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY}, null, null, null);
            if (cur != null) try {
                while (cur.moveToNext() && found.size() < 8) {
                    long cid = cur.getLong(0);
                    if (found.containsKey(cid)) continue;
                    found.put(cid, new String[]{cur.getString(1), cur.getString(2), label(c, cur.getInt(3), cur.getString(4)), cur.getString(5)});
                }
            } finally { cur.close(); }
        } else {
            Uri u = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q));
            Cursor cur = c.getContentResolver().query(u, new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER, ContactsContract.Contacts.LOOKUP_KEY}, null, null, null);
            ArrayList<Long> ids = new ArrayList<Long>();
            if (cur != null) try {
                while (cur.moveToNext() && found.size() < 8) {
                    long cid = cur.getLong(0);
                    found.put(cid, new String[]{cur.getString(1), null, null, cur.getString(3)});
                    if (cur.getInt(2) > 0) ids.add(cid);
                }
            } finally { cur.close(); }
            if (!ids.isEmpty()) {
                StringBuilder in = new StringBuilder();
                for (Long x : ids) { if (in.length() > 0) in.append(','); in.append(x); }
                Cursor p = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " IN (" + in + ")", null, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC");
                if (p != null) try {
                    while (p.moveToNext()) {
                        String[] row = found.get(p.getLong(0));
                        if (row != null && row[1] == null) { row[1] = p.getString(1); row[2] = label(c, p.getInt(2), p.getString(3)); }
                    }
                } finally { p.close(); }
            }
        }
        for (java.util.Map.Entry<Long, String[]> e : found.entrySet()) {
            String[] r = e.getValue();
            out.addRow(new Object[]{"contacts", r[0], r[1] == null ? "" : (r[2] == null || r[2].isEmpty() ? r[1] : r[2] + " · " + r[1]), String.valueOf(e.getKey()), r[1] == null ? "" : r[1], 0, r[3] == null ? "" : r[3]});
        }
    }

    private static String label(Context c, int type, String custom) {
        try { CharSequence s = ContactsContract.CommonDataKinds.Phone.getTypeLabel(c.getResources(), type, custom); return s == null ? "" : s.toString(); } catch (Throwable t) { return ""; }
    }

    private static String mime(String ext) {
        String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m == null ? "*/*" : m;
    }

    private static String when(Context c, long t) {
        return DateUtils.formatDateTime(c, t, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_TIME);
    }

    private static boolean remove(Context c, String path) {
        try {
            java.io.File f = new java.io.File(path).getCanonicalFile();
            String root = SearchIndex.root().getCanonicalPath() + "/";
            if (!f.getPath().startsWith(root) || !f.isFile()) return false;
            boolean gone = f.delete();
            if (!gone) {
                try { Root.run("rm -f '" + f.getPath().replace("'", "'\\''") + "'"); } catch (Throwable ignored) { }
                gone = !f.exists();
            }
            if (gone) SearchIndex.forget(c, f.getPath());
            return gone;
        } catch (Throwable t) { return false; }
    }

    private static void files(Context c, String q, MatrixCursor out) {
        for (SearchIndex.Hit h : SearchIndex.names(c, q, 12)) {
            String folder = h.folder == null || h.folder.isEmpty() ? I18n.t("Internal storage") : h.folder;
            out.addRow(new Object[]{"files", h.name, when(c, h.mtime) + " · " + android.text.format.Formatter.formatShortFileSize(c, h.size) + " · " + folder, h.path, mime(h.ext), 0, ""});
        }
    }

    private static void content(Context c, String q, MatrixCursor out) {
        for (SearchIndex.Hit h : SearchIndex.content(c, q, 15)) {
            String snippet = h.snippet == null ? "" : h.snippet.replace('\f', ' ').replace('\n', ' ').replace('\t', ' ').replaceAll(" {2,}", " ").trim();
            String place = h.folder == null || h.folder.isEmpty() ? I18n.t("Internal storage") : h.folder;
            out.addRow(new Object[]{"content", h.name, snippet, h.path, mime(h.ext), h.page, h.mtime > 0 ? when(c, h.mtime) + " · " + place : place});
        }
    }

    private static void calendar(Context c, String q, MatrixCursor out) {
        if (!granted(c, "android.permission.READ_CALENDAR")) return;
        long now = System.currentTimeMillis(), year = 366L * 24 * 60 * 60 * 1000;
        Uri.Builder b = CalendarContract.Instances.CONTENT_SEARCH_URI.buildUpon();
        ContentUris.appendId(b, now - year); ContentUris.appendId(b, now + year); b.appendPath(q);
        Cursor cur = c.getContentResolver().query(b.build(), new String[]{CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION}, null, null, CalendarContract.Instances.BEGIN + " ASC");
        if (cur == null) return;
        ArrayList<Object[]> rows = new ArrayList<Object[]>();
        try {
            while (cur.moveToNext() && rows.size() < 60) {
                long begin = cur.getLong(2);
                int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;
                if (cur.getInt(4) == 0) flags |= DateUtils.FORMAT_SHOW_TIME;
                if (Math.abs(begin - now) > 300L * 24 * 60 * 60 * 1000) flags |= DateUtils.FORMAT_SHOW_YEAR;
                String where = cur.getString(5);
                String sub = DateUtils.formatDateTime(c, begin, flags) + (where == null || where.trim().isEmpty() ? "" : " · " + where.trim());
                rows.add(new Object[]{"calendar", cur.getString(1), sub, String.valueOf(cur.getLong(0)), String.valueOf(begin), 0, String.valueOf(cur.getLong(3))});
            }
        } finally { cur.close(); }
        final long n = now;
        Collections.sort(rows, (x, y) -> Long.compare(rank(Long.parseLong((String) x[4]), n), rank(Long.parseLong((String) y[4]), n)));
        for (int i = 0; i < rows.size() && i < 6; i++) out.addRow(rows.get(i));
    }

    private static long rank(long begin, long now) { return begin >= now - 3600000L ? begin - now : (now - begin) * 3; }

    private static void suite(Context c, String q, MatrixCursor out) {
        try {
            int n = 0;
            for (AppSearchIndex.Entry e : AppSearchIndex.load(c).find(q)) {
                if (n++ >= 5) break;
                out.addRow(new Object[]{"suite", e.title, e.breadcrumb, e.page, e.anchor, 0, e.id});
            }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: pages", t); }
    }

    private ParcelFileDescriptor engineIcon(String id) throws FileNotFoundException {
        if (id == null || !id.matches("[a-z]{2,16}")) throw new FileNotFoundException();
        long ident = Binder.clearCallingIdentity();
        try {
            int raw = getContext().getResources().getIdentifier("engine_" + id, "raw", getContext().getPackageName());
            if (raw == 0) throw new FileNotFoundException();
            java.io.File file = new java.io.File(getContext().getCacheDir(), "engine-logo-" + id);
            java.io.File part = new java.io.File(file.getPath() + ".part");
            try (InputStream in = getContext().getResources().openRawResource(raw); OutputStream o = new java.io.FileOutputStream(part)) { byte[] b = new byte[8192]; int k; while ((k = in.read(b)) > 0) o.write(b, 0, k); }
            if (!part.renameTo(file)) throw new FileNotFoundException();
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException e) { throw e; }
        catch (Throwable t) { android.util.Log.w("PixelSuite", "search: engine icon " + t); throw new FileNotFoundException(); }
        finally { Binder.restoreCallingIdentity(ident); }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!ready()) throw new FileNotFoundException();
        List<String> seg = uri.getPathSegments();
        if (seg.size() == 2 && "engine".equals(seg.get(0))) return engineIcon(seg.get(1));
        if (seg.size() != 2 || !"photo".equals(seg.get(0))) throw new FileNotFoundException();
        final Context c = getContext();
        long cid;
        try { cid = Long.parseLong(seg.get(1)); } catch (Throwable t) { throw new FileNotFoundException(); }
        long ident = Binder.clearCallingIdentity();
        try {
            if (!granted(c, "android.permission.READ_CONTACTS")) throw new FileNotFoundException();
            final InputStream in = ContactsContract.Contacts.openContactPhotoInputStream(c.getContentResolver(), ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cid), false);
            if (in == null) throw new FileNotFoundException();
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(new Runnable() { public void run() {
                OutputStream o = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                try { byte[] b = new byte[16384]; int k; while ((k = in.read(b)) > 0) o.write(b, 0, k); }
                catch (Throwable ignored) { }
                finally { try { in.close(); } catch (Throwable ignored) { } try { o.close(); } catch (Throwable ignored) { } }
            } }, "PixelSearchPhoto").start();
            return pipe[0];
        } catch (FileNotFoundException e) { throw e; }
        catch (Throwable t) { throw new FileNotFoundException(); }
        finally { Binder.restoreCallingIdentity(ident); }
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
