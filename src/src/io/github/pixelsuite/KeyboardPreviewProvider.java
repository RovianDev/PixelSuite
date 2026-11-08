package io.github.pixelsuite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KeyboardPreviewProvider extends ContentProvider {
    private static final String[] CALLERS = {"com.google.android.inputmethod.latin"};
    static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override public boolean onCreate() { return true; }

    private boolean allowed() {
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null) for (String p : packages) for (String c : CALLERS) if (c.equals(p)) return true;
        return false;
    }

    private void gboardState(Bundle extras) {
        android.content.SharedPreferences.Editor e = state(getContext()).edit();
        if (extras.containsKey("autocorrect")) e.putBoolean("autocorrect", extras.getBoolean("autocorrect")).putBoolean("known", true);
        String[] l = extras.getStringArray("languages");
        if (l != null && l.length > 0 && l.length < 100) {
            StringBuilder b = new StringBuilder();
            for (String x : l) { if (x == null || x.indexOf('\t') <= 0 || x.length() > 120) continue; if (b.length() > 0) b.append('\n'); b.append(x); }
            if (b.length() > 0) e.putString("languages", b.toString());
        }
        e.apply();
        MAIN.post(new Runnable() { public void run() { for (Runnable r : LISTENERS) { try { r.run(); } catch (Throwable ignored) { } } } });
    }

    private static long sLastTouch;
    private Bundle touch(Bundle e) {
        Bundle out = new Bundle();
        try {
            String ime = android.provider.Settings.Secure.getString(getContext().getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime == null || !ime.startsWith(CALLERS[0] + "/")) return out;
            long now = android.os.SystemClock.uptimeMillis();
            synchronized (KeyboardPreviewProvider.class) { if (now - sLastTouch < 250) return out; sLastTouch = now; }
            float x = e.getFloat("x", -1), y = e.getFloat("y", -1);
            android.util.DisplayMetrics dm = android.content.res.Resources.getSystem().getDisplayMetrics();
            int big = Math.max(dm.widthPixels, dm.heightPixels) + 400;
            if (!(x >= 0 && y >= 0 && x < big && y < big)) return out;
            String at = Math.round(x) + " " + Math.round(y);
            boolean ok = RootShell.run("cmd input tap " + at, 1500);
            if (!ok) ok = Root.run("input tap " + at) != null;
            out.putBoolean("ok", ok);
            out.putLong("ms", android.os.SystemClock.uptimeMillis() - now);
        } catch (Throwable ignored) { }
        return out;
    }

    static android.content.SharedPreferences state(Context c) { return c.getSharedPreferences("pixelsuite_gboard_state", Context.MODE_PRIVATE); }

    static File dir(Context c) { return new File(c.getFilesDir(), "keyboard-preview"); }

    static String theme(Context c) {
        if (SuperDark.inEffect(c)) return "s";
        return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES ? "d" : "l";
    }
    static File themeDir(Context c) { return new File(dir(c), theme(c)); }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!allowed()) throw new SecurityException("Caller not allowed");
        if ("gboard_state".equals(method) && extras != null) { gboardState(extras); return null; }
        if ("touch".equals(method) && extras != null) return touch(extras);
        if ("warm".equals(method)) { new Thread(new Runnable() { public void run() { RootShell.warm(); } }, "pixelsuite-root-warm").start(); return null; }
        if (!"put".equals(method) || extras == null) return null;
        byte[] body = extras.getByteArray("body"), header = extras.getByteArray("header");
        if (body == null || body.length == 0 || body.length > 900 * 1024 || (header != null && header.length > 900 * 1024)) return null;
        if (!picture(body) || (header != null && !picture(header))) return null;
        String look = extras.getString("theme");
        File d = "l".equals(look) || "d".equals(look) || "s".equals(look) ? new File(dir(getContext()), look) : dir(getContext());
        if (!d.isDirectory() && !d.mkdirs()) return null;
        write(new File(d, "body.webp"), body);
        part(d, "body", extras);
        if (header != null) { write(new File(d, "header.webp"), header); part(d, "header", extras); }
        try { write(new File(d, "meta.txt"), (Math.max(0, extras.getInt("headH")) + " " + extras.getFloat("geoH", 1f)).getBytes("UTF-8")); } catch (Throwable ignored) { }
        MAIN.post(new Runnable() { public void run() { for (Runnable r : LISTENERS) { try { r.run(); } catch (Throwable ignored) { } } } });
        return null;
    }

    private static void part(File d, String name, Bundle extras) {
        byte[] atlas = extras.getByteArray(name + "Atlas");
        int[] meta = extras.getIntArray(name + "Labels");
        File a = new File(d, name + "_atlas.webp"), m = new File(d, name + "_labels.txt");
        if (atlas == null || meta == null || meta.length % 6 != 0 || meta.length > 6 * 400 || atlas.length > 900 * 1024 || !picture(atlas)) { a.delete(); m.delete(); return; }
        write(a, atlas);
        StringBuilder sb = new StringBuilder();
        for (int v : meta) { if (sb.length() > 0) sb.append(' '); sb.append(v); }
        try { write(m, sb.toString().getBytes("UTF-8")); } catch (Throwable ignored) { }
    }

    static Object[] labels(Context c, String name) {
        try {
            File d = themeDir(c), a = new File(d, name + "_atlas.webp"), m = new File(d, name + "_labels.txt");
            if (!a.isFile() || !m.isFile()) return null;
            Bitmap b = BitmapFactory.decodeFile(a.getPath());
            if (b == null) return null;
            java.io.FileInputStream in = new java.io.FileInputStream(m);
            byte[] buf = new byte[(int) m.length()];
            try { int off = 0, n; while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n; } finally { in.close(); }
            String t = new String(buf, "UTF-8").trim();
            if (t.isEmpty()) return null;
            String[] p = t.split(" ");
            int[] meta = new int[p.length];
            for (int i = 0; i < p.length; i++) meta[i] = Integer.parseInt(p[i]);
            return meta.length % 6 == 0 ? new Object[] { b, meta } : null;
        } catch (Throwable t) { return null; }
    }

    private static boolean picture(byte[] b) {
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        return o.outWidth > 0 && o.outHeight > 0 && o.outWidth <= 4096 && o.outHeight <= 4096;
    }

    private static void write(File f, byte[] b) {
        File tmp = new File(f.getPath() + ".tmp");
        try { FileOutputStream out = new FileOutputStream(tmp); try { out.write(b); } finally { out.close(); } tmp.renameTo(f); } catch (Throwable ignored) { }
    }

    static Bitmap[] load(Context c) {
        File d = themeDir(c);
        Bitmap h = null, b = null;
        try { File f = new File(d, "header.webp"); if (f.isFile()) h = BitmapFactory.decodeFile(f.getPath()); } catch (Throwable ignored) { }
        try { File f = new File(d, "body.webp"); if (f.isFile()) b = BitmapFactory.decodeFile(f.getPath()); } catch (Throwable ignored) { }
        return new Bitmap[] { h, b };
    }

    static float[] meta(Context c) {
        try {
            File f = new File(themeDir(c), "meta.txt");
            if (!f.isFile()) return null;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[64]; int n; try { n = in.read(b); } finally { in.close(); }
            String[] p = new String(b, 0, Math.max(0, n), "UTF-8").trim().split(" ");
            float h = Float.parseFloat(p[0]), g = Float.parseFloat(p[1]);
            return g > 0.3f && g < 3f ? new float[] { h, g } : null;
        } catch (Throwable t) { return null; }
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
