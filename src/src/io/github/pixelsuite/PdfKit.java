package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class PdfKit {
    private PdfKit() { }

    interface Progress { void step(int done, int total); }

    static final class Wrong extends Exception { Wrong() { super("password"); } }

    static File library() { return new File(Environment.getExternalStorageDirectory(), "Documents/Pixel PDF"); }

    static File pictures(String name) { return new File(Environment.getExternalStorageDirectory(), "Pictures/Pixel PDF/" + name); }

    static boolean canFiles() {
        try { return (Boolean) Environment.class.getMethod("isExternalStorageManager").invoke(null); } catch (Throwable t) { return true; }
    }

    static void askFiles(Context c) {
        if (canFiles()) return;
        PdfHost.grantFiles(c);
    }

    static String clean(String s) {
        String n = s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(".pdf")) n = n.substring(0, n.length() - 4).trim();
        return n.isEmpty() ? "PDF" : n.length() > 90 ? n.substring(0, 90).trim() : n;
    }

    static String base(File f) {
        String n = f.getName(); int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    static File unique(File dir, String base, String ext) {
        dir.mkdirs();
        File f = new File(dir, base + ext);
        for (int i = 2; f.exists() && i < 999; i++) f = new File(dir, base + " (" + i + ")" + ext);
        return f;
    }

    static void scan(Context c, File... files) {
        try {
            String[] paths = new String[files.length];
            for (int i = 0; i < files.length; i++) paths[i] = files[i].getAbsolutePath();
            android.media.MediaScannerConnection.scanFile(c.getApplicationContext(), paths, null, null);
        } catch (Throwable ignored) { }
    }

    static void copy(InputStream in, File to) throws IOException {
        OutputStream o = new FileOutputStream(to);
        try { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) o.write(buf, 0, n); }
        finally { in.close(); o.close(); }
    }

    static String size(long b) {
        if (b < 1000) return b + " B";
        if (b < 1000_000) return String.format(Locale.US, "%d kB", Math.round(b / 1000.0));
        return String.format(Locale.US, "%.1f MB", b / 1000_000.0);
    }

    static PdfRenderer open(File f, String password) throws Exception {
        ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            if (password == null) return new PdfRenderer(fd);
            Class<?> builder = Class.forName("android.graphics.pdf.LoadParams$Builder");
            Object b = builder.getConstructor().newInstance();
            b = builder.getMethod("setPassword", String.class).invoke(b, password);
            Object params = builder.getMethod("build").invoke(b);
            try {
                return PdfRenderer.class.getConstructor(ParcelFileDescriptor.class, Class.forName("android.graphics.pdf.LoadParams")).newInstance(fd, params);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof SecurityException) throw new Wrong();
                throw e;
            }
        } catch (SecurityException e) {
            try { fd.close(); } catch (Throwable ignored) { }
            throw new Wrong();
        } catch (Wrong e) {
            try { fd.close(); } catch (Throwable ignored) { }
            throw e;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            try { fd.close(); } catch (Throwable ignored) { }
            throw new UnsupportedOperationException("passwords");
        }
    }

    static boolean passwords() {
        try { Class.forName("android.graphics.pdf.LoadParams"); return true; } catch (Throwable t) { return false; }
    }

    static boolean locked(File f) {
        PdfRenderer r = null;
        try { r = open(f, null); return false; } catch (Wrong w) { return true; } catch (Throwable t) { return false; }
        finally { if (r != null) try { r.close(); } catch (Throwable ignored) { } }
    }

    static int count(File f) {
        PdfRenderer r = null;
        try { r = open(f, null); return r.getPageCount(); } catch (Throwable t) { return 0; }
        finally { if (r != null) try { r.close(); } catch (Throwable ignored) { } }
    }

    static Bitmap render(PdfRenderer.Page p, float dpi, int maxSide) {
        float s = dpi / 72f;
        int w = Math.max(1, Math.round(p.getWidth() * s)), h = Math.max(1, Math.round(p.getHeight() * s));
        float k = Math.min(1f, maxSide / (float) Math.max(w, h));
        w = Math.max(1, Math.round(w * k)); h = Math.max(1, Math.round(h * k));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        Matrix m = new Matrix(); m.setScale(w / (float) p.getWidth(), h / (float) p.getHeight());
        p.render(b, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return b;
    }

    static Bitmap thumb(File f, int width) {
        PdfRenderer r = null; PdfRenderer.Page p = null;
        try {
            r = open(f, null);
            if (r.getPageCount() == 0) return null;
            p = r.openPage(0);
            float dpi = 72f * width / Math.max(1, p.getWidth());
            return render(p, dpi, width * 2);
        } catch (Throwable t) { return null; }
        finally { if (p != null) try { p.close(); } catch (Throwable ignored) { } if (r != null) try { r.close(); } catch (Throwable ignored) { } }
    }

    static String text(PdfRenderer.Page p) {
        try {
            Object list = PdfRenderer.Page.class.getMethod("getTextContents").invoke(p);
            StringBuilder sb = new StringBuilder();
            for (Object part : (List<?>) list) { Object t = part.getClass().getMethod("getText").invoke(part); if (t != null) sb.append(t); }
            return sb.toString().trim();
        } catch (Throwable t) { return ""; }
    }

    static final class Writer {
        private final OutputStream out;
        private long at;
        private final ArrayList<Long> offsets = new ArrayList<Long>();
        private final ArrayList<Integer> kids = new ArrayList<Integer>();
        private final byte[] key;
        private final String owner, user;
        private final byte[] id;
        private final byte[] o, u;
        private static final int P = -4;

        Writer(File f, String password) throws Exception {
            out = new java.io.BufferedOutputStream(new FileOutputStream(f), 1 << 16);
            id = md5((f.getAbsolutePath() + System.nanoTime()).getBytes("UTF-8"));
            if (password != null && !password.isEmpty()) {
                user = password; owner = password;
                o = ownerValue(owner, user);
                key = fileKey(user, o, id);
                u = userValue(key, id);
            } else { user = owner = null; o = u = null; key = null; }
            raw("%PDF-1.4\n%âãÏÓ\n".getBytes("ISO-8859-1"));
            offsets.add(0L); offsets.add(0L);
        }

        private void raw(byte[] b) throws IOException { out.write(b); at += b.length; }
        private void raw(String s) throws IOException { raw(s.getBytes("ISO-8859-1")); }

        private int begin() throws IOException { offsets.add(at); int n = offsets.size(); raw(n + " 0 obj\n"); return n; }

        private int reserve() { offsets.add(0L); return offsets.size(); }

        private void stream(int n, String dict, byte[] data) throws Exception {
            byte[] d = key == null ? data : rc4(objectKey(n), data);
            raw("<<" + dict + " /Length " + d.length + ">>\nstream\n");
            raw(d);
            raw("\nendstream\nendobj\n");
        }

        void page(Bitmap image, float wPt, float hPt, int quality) throws Exception {
            ByteArrayOutputStream jpg = new ByteArrayOutputStream();
            Bitmap rgb = image;
            if (image.hasAlpha()) {
                rgb = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(rgb); c.drawColor(Color.WHITE); c.drawBitmap(image, 0, 0, null);
            }
            rgb.compress(Bitmap.CompressFormat.JPEG, quality, jpg);
            int iw = rgb.getWidth(), ih = rgb.getHeight();
            if (rgb != image) rgb.recycle();
            int img = begin();
            stream(img, "/Type /XObject /Subtype /Image /Width " + iw + " /Height " + ih + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode", jpg.toByteArray());
            String w = num(wPt), h = num(hPt);
            int content = begin();
            stream(content, "", ("q " + w + " 0 0 " + h + " 0 0 cm /Im0 Do Q").getBytes("ISO-8859-1"));
            int page = begin();
            raw("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "] /Resources << /XObject << /Im0 " + img + " 0 R >> >> /Contents " + content + " 0 R >>\nendobj\n");
            kids.add(page);
        }

        int pages() { return kids.size(); }

        void finish() throws Exception {
            offsets.set(1, at);
            StringBuilder k = new StringBuilder();
            for (int p : kids) k.append(p).append(" 0 R ");
            raw("2 0 obj\n<< /Type /Pages /Kids [" + k + "] /Count " + kids.size() + " >>\nendobj\n");
            offsets.set(0, at);
            raw("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
            int enc = 0;
            if (key != null) {
                enc = begin();
                raw("<< /Filter /Standard /V 2 /R 3 /Length 128 /O <" + hex(o) + "> /U <" + hex(u) + "> /P " + P + " >>\nendobj\n");
            }
            long xref = at;
            StringBuilder x = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
            for (long off : offsets) x.append(String.format(Locale.US, "%010d 00000 n \n", off));
            raw(x.toString());
            raw("trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R" + (enc > 0 ? " /Encrypt " + enc + " 0 R" : "") + " /ID [<" + hex(id) + "> <" + hex(id) + ">] >>\nstartxref\n" + xref + "\n%%EOF\n");
            out.close();
        }

        void abort() { try { out.close(); } catch (Throwable ignored) { } }

        private byte[] objectKey(int n) throws Exception {
            byte[] k = new byte[key.length + 5];
            System.arraycopy(key, 0, k, 0, key.length);
            k[key.length] = (byte) n; k[key.length + 1] = (byte) (n >> 8); k[key.length + 2] = (byte) (n >> 16);
            byte[] h = md5(k);
            byte[] r = new byte[Math.min(key.length + 5, 16)];
            System.arraycopy(h, 0, r, 0, r.length);
            return r;
        }

        private static final byte[] PAD = {(byte) 0x28, (byte) 0xBF, (byte) 0x4E, (byte) 0x5E, (byte) 0x4E, (byte) 0x75, (byte) 0x8A, (byte) 0x41, (byte) 0x64, (byte) 0x00, (byte) 0x4E, (byte) 0x56, (byte) 0xFF, (byte) 0xFA, (byte) 0x01, (byte) 0x08,
                (byte) 0x2E, (byte) 0x2E, (byte) 0x00, (byte) 0xB6, (byte) 0xD0, (byte) 0x68, (byte) 0x3E, (byte) 0x80, (byte) 0x2F, (byte) 0x0C, (byte) 0xA9, (byte) 0xFE, (byte) 0x64, (byte) 0x53, (byte) 0x69, (byte) 0x7A};

        private static byte[] pad(String s) throws Exception {
            byte[] p = s.getBytes("ISO-8859-1"), r = new byte[32];
            int n = Math.min(32, p.length);
            System.arraycopy(p, 0, r, 0, n);
            System.arraycopy(PAD, 0, r, n, 32 - n);
            return r;
        }

        private static byte[] ownerValue(String owner, String user) throws Exception {
            byte[] h = md5(pad(owner));
            for (int i = 0; i < 50; i++) h = md5(h);
            byte[] k = new byte[16]; System.arraycopy(h, 0, k, 0, 16);
            byte[] r = rc4(k, pad(user));
            for (int i = 1; i <= 19; i++) r = rc4(xor(k, i), r);
            return r;
        }

        private static byte[] fileKey(String user, byte[] o, byte[] id) throws Exception {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(pad(user)); m.update(o);
            m.update(new byte[]{(byte) P, (byte) (P >> 8), (byte) (P >> 16), (byte) (P >> 24)});
            m.update(id);
            byte[] h = m.digest();
            for (int i = 0; i < 50; i++) { byte[] t = new byte[16]; System.arraycopy(h, 0, t, 0, 16); h = md5(t); }
            byte[] k = new byte[16]; System.arraycopy(h, 0, k, 0, 16);
            return k;
        }

        private static byte[] userValue(byte[] key, byte[] id) throws Exception {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(PAD); m.update(id);
            byte[] r = rc4(key, m.digest());
            for (int i = 1; i <= 19; i++) r = rc4(xor(key, i), r);
            byte[] u = new byte[32];
            System.arraycopy(r, 0, u, 0, 16);
            return u;
        }

        private static byte[] xor(byte[] k, int v) { byte[] r = new byte[k.length]; for (int i = 0; i < k.length; i++) r[i] = (byte) (k[i] ^ v); return r; }
    }

    static byte[] md5(byte[] b) throws Exception { return MessageDigest.getInstance("MD5").digest(b); }

    static byte[] rc4(byte[] key, byte[] data) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        for (int i = 0, j = 0; i < 256; i++) { j = (j + s[i] + (key[i % key.length] & 0xff)) & 0xff; int t = s[i]; s[i] = s[j]; s[j] = t; }
        byte[] r = new byte[data.length];
        for (int n = 0, i = 0, j = 0; n < data.length; n++) {
            i = (i + 1) & 0xff; j = (j + s[i]) & 0xff; int t = s[i]; s[i] = s[j]; s[j] = t;
            r[n] = (byte) (data[n] ^ s[(s[i] + s[j]) & 0xff]);
        }
        return r;
    }

    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format(Locale.US, "%02X", x & 0xff)); return s.toString(); }

    static String num(float v) { return String.format(Locale.US, "%.2f", v); }

    interface Pager { Bitmap page(int index, PdfRenderer.Page source); }

    static File edited(File in, String suffix, Pager pager, Progress progress) throws Exception {
        File to = unique(library(), base(in) + suffix, ".pdf");
        PdfRenderer r = open(in, null);
        Writer w = new Writer(to, null);
        try {
            int n = r.getPageCount();
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page p = r.openPage(i);
                float pw = p.getWidth(), ph = p.getHeight();
                Bitmap b;
                try { b = pager.page(i, p); } finally { p.close(); }
                w.page(b, pw, ph, 88);
                b.recycle();
                if (progress != null) progress.step(i + 1, n);
            }
            w.finish();
        } catch (Exception e) { w.abort(); to.delete(); throw e; }
        finally { r.close(); }
        return to;
    }

    static final int[][] LEVELS = {{150, 82}, {120, 68}, {96, 52}};

    static File compress(File in, int level, Progress p) throws Exception {
        File to = unique(library(), base(in) + " (compressed)", ".pdf");
        int[] l = LEVELS[Math.max(0, Math.min(2, level))];
        rasterSized(in, null, to, null, l[0], l[1], p);
        return to;
    }

    static void rasterSized(File in, String password, File to, String newPassword, float dpi, int quality, Progress progress) throws Exception {
        PdfRenderer r = open(in, password);
        Writer w = new Writer(to, newPassword);
        try {
            int n = r.getPageCount();
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page p = r.openPage(i);
                float pw = p.getWidth(), ph = p.getHeight();
                Bitmap b;
                try { b = render(p, dpi, 3400); } finally { p.close(); }
                w.page(b, pw, ph, quality);
                b.recycle();
                if (progress != null) progress.step(i + 1, n);
            }
            w.finish();
        } catch (Exception e) { w.abort(); to.delete(); throw e; }
        finally { r.close(); }
    }

    static File merge(List<File> ins, Progress progress) throws Exception {
        File to = unique(library(), "Merged " + new java.text.SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(new java.util.Date()), ".pdf");
        int total = 0;
        for (File f : ins) total += count(f);
        Writer w = new Writer(to, null);
        int done = 0;
        try {
            for (File f : ins) {
                PdfRenderer r = open(f, null);
                try {
                    for (int i = 0; i < r.getPageCount(); i++) {
                        PdfRenderer.Page p = r.openPage(i);
                        float pw = p.getWidth(), ph = p.getHeight();
                        Bitmap b;
                        try { b = render(p, 170, 3400); } finally { p.close(); }
                        w.page(b, pw, ph, 86);
                        b.recycle();
                        if (progress != null) progress.step(++done, Math.max(total, done));
                    }
                } finally { r.close(); }
            }
            w.finish();
        } catch (Exception e) { w.abort(); to.delete(); throw e; }
        return to;
    }

    static File lock(File in, String password, Progress p) throws Exception {
        File to = unique(library(), base(in) + " (locked)", ".pdf");
        rasterSized(in, null, to, password, 170, 86, p);
        return to;
    }

    static File unlock(File in, String password, Progress p) throws Exception {
        File to = unique(library(), base(in) + " (unlocked)", ".pdf");
        rasterSized(in, password, to, null, 170, 86, p);
        return to;
    }

    static File toJpg(File in, Progress progress) throws Exception {
        File dir = pictures(base(in));
        if (dir.exists()) dir = new File(dir.getParentFile(), dir.getName() + " " + System.currentTimeMillis() / 1000);
        dir.mkdirs();
        PdfRenderer r = open(in, null);
        ArrayList<File> made = new ArrayList<File>();
        try {
            int n = r.getPageCount();
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page p = r.openPage(i);
                Bitmap b;
                try { b = render(p, 200, 3000); } finally { p.close(); }
                File f = new File(dir, String.format(Locale.US, "%s %03d.jpg", base(in), i + 1));
                OutputStream o = new FileOutputStream(f);
                b.compress(Bitmap.CompressFormat.JPEG, 92, o); o.close(); b.recycle();
                made.add(f);
                if (progress != null) progress.step(i + 1, n);
            }
        } finally { r.close(); }
        return dir;
    }

    static void print(Activity a, final File f) {
        PrintManager pm = (PrintManager) a.getSystemService(Context.PRINT_SERVICE);
        final String name = f.getName();
        pm.print(name, new PrintDocumentAdapter() {
            @Override public void onLayout(PrintAttributes old, PrintAttributes now, CancellationSignal cancel, LayoutResultCallback cb, Bundle extras) {
                if (cancel.isCanceled()) { cb.onLayoutCancelled(); return; }
                cb.onLayoutFinished(new PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(count(f) > 0 ? count(f) : PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true);
            }
            @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor dest, CancellationSignal cancel, WriteResultCallback cb) {
                try {
                    InputStream in = new FileInputStream(f);
                    OutputStream o = new FileOutputStream(dest.getFileDescriptor());
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) { if (cancel.isCanceled()) { in.close(); cb.onWriteCancelled(); return; } o.write(buf, 0, n); }
                    in.close(); o.flush();
                    cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Throwable t) { cb.onWriteFailed(t.getMessage()); }
            }
        }, null);
    }

    private static byte[] read(ZipFile z, String name) throws IOException {
        ZipEntry e = z.getEntry(name);
        if (e == null) return null;
        InputStream in = z.getInputStream(e);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[32768]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toByteArray();
    }

    private static XmlPullParser parser(byte[] data) throws Exception {
        XmlPullParser x = Xml.newPullParser();
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        x.setInput(new java.io.ByteArrayInputStream(data), "UTF-8");
        return x;
    }

    private static Map<String, String> rels(ZipFile z, String path, String folder) throws Exception {
        HashMap<String, String> m = new HashMap<String, String>();
        byte[] data = read(z, path);
        if (data == null) return m;
        XmlPullParser x = parser(data);
        for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
            if (e == XmlPullParser.START_TAG && "Relationship".equals(x.getName())) {
                String t = x.getAttributeValue(null, "Target"), id = x.getAttributeValue(null, "Id");
                if (t == null || id == null) continue;
                m.put(id, resolve(folder, t));
            }
        }
        return m;
    }

    private static String resolve(String folder, String target) {
        if (target.startsWith("/")) return target.substring(1);
        String p = folder + target;
        ArrayList<String> parts = new ArrayList<String>();
        for (String s : p.split("/")) { if (s.equals("..")) { if (!parts.isEmpty()) parts.remove(parts.size() - 1); } else if (!s.isEmpty() && !s.equals(".")) parts.add(s); }
        StringBuilder b = new StringBuilder();
        for (String s : parts) { if (b.length() > 0) b.append('/'); b.append(s); }
        return b.toString();
    }

    private static String attr(XmlPullParser x, String name) {
        for (int i = 0; i < x.getAttributeCount(); i++) if (name.equals(x.getAttributeName(i))) return x.getAttributeValue(i);
        return null;
    }

    private static int color(String hex, int fallback) {
        try { return 0xff000000 | Integer.parseInt(hex, 16); } catch (Throwable t) { return fallback; }
    }

    private static final class Block {
        SpannableStringBuilder text; Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL; float size = 11; float before, after = 6; String bullet;
        byte[] image; float iw, ih; boolean pageBreak;
    }

    static File docx(File in, String name) throws Exception {
        ZipFile z = new ZipFile(in);
        ArrayList<Block> blocks = new ArrayList<Block>();
        try {
            byte[] doc = read(z, "word/document.xml");
            if (doc == null) throw new IOException("not a document");
            Map<String, String> rel = rels(z, "word/_rels/document.xml.rels", "word/");
            XmlPullParser x = parser(doc);
            Block cur = null; boolean bold = false, italic = false, under = false, inText = false, cell = false; float size = 0; int ink = 0; String style = null;
            int runStart = 0; long cx = 0, cy = 0;
            for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
                String n = x.getName();
                if (e == XmlPullParser.START_TAG) {
                    if ("w:p".equals(n)) { cur = new Block(); cur.text = new SpannableStringBuilder(); style = null; }
                    else if ("w:pStyle".equals(n) && cur != null) {
                        style = attr(x, "w:val");
                        if (style != null) {
                            String s = style.toLowerCase(Locale.ROOT);
                            if (s.startsWith("heading1") || s.equals("title")) { cur.size = 20; cur.before = 10; }
                            else if (s.startsWith("heading2")) { cur.size = 16; cur.before = 8; }
                            else if (s.startsWith("heading")) { cur.size = 13.5f; cur.before = 6; }
                        }
                    }
                    else if ("w:numPr".equals(n) && cur != null) cur.bullet = "•  ";
                    else if ("w:jc".equals(n) && cur != null) {
                        String v = attr(x, "w:val");
                        if ("center".equals(v)) cur.align = Layout.Alignment.ALIGN_CENTER;
                        else if ("right".equals(v) || "end".equals(v)) cur.align = Layout.Alignment.ALIGN_OPPOSITE;
                    }
                    else if ("w:r".equals(n)) { bold = false; italic = false; under = false; size = 0; ink = 0; }
                    else if ("w:b".equals(n)) bold = !"0".equals(attr(x, "w:val")) && !"false".equals(attr(x, "w:val"));
                    else if ("w:i".equals(n)) italic = !"0".equals(attr(x, "w:val")) && !"false".equals(attr(x, "w:val"));
                    else if ("w:u".equals(n)) under = !"none".equals(attr(x, "w:val"));
                    else if ("w:sz".equals(n)) { try { size = Integer.parseInt(attr(x, "w:val")) / 2f; } catch (Throwable ignored) { } }
                    else if ("w:color".equals(n)) { String v = attr(x, "w:val"); if (v != null && !"auto".equals(v)) ink = color(v, 0); }
                    else if ("w:t".equals(n)) { inText = true; if (cur != null) runStart = cur.text.length(); }
                    else if ("w:tab".equals(n) && cur != null) cur.text.append("    ");
                    else if ("w:br".equals(n) && cur != null) { if ("page".equals(attr(x, "w:type"))) { Block b = new Block(); b.pageBreak = true; blocks.add(b); } else cur.text.append('\n'); }
                    else if ("w:tc".equals(n)) cell = true;
                    else if ("wp:extent".equals(n)) { try { cx = Long.parseLong(attr(x, "cx")); cy = Long.parseLong(attr(x, "cy")); } catch (Throwable ignored) { } }
                    else if ("a:blip".equals(n)) {
                        String id = attr(x, "r:embed"), path = id == null ? null : rel.get(id);
                        if (path != null) {
                            Block b = new Block(); b.image = read(z, path); b.iw = cx / 12700f; b.ih = cy / 12700f;
                            if (b.image != null) blocks.add(b);
                        }
                    }
                } else if (e == XmlPullParser.TEXT && inText && cur != null) {
                    cur.text.append(x.getText());
                } else if (e == XmlPullParser.END_TAG) {
                    if ("w:t".equals(n)) {
                        inText = false;
                        if (cur != null && cur.text.length() > runStart) {
                            int a = runStart, b = cur.text.length();
                            if (bold && italic) cur.text.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            else if (bold) cur.text.setSpan(new StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            else if (italic) cur.text.setSpan(new StyleSpan(Typeface.ITALIC), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            if (under) cur.text.setSpan(new UnderlineSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            if (size > 0) cur.text.setSpan(new AbsoluteSizeSpan(Math.round(size)), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            if (ink != 0) cur.text.setSpan(new ForegroundColorSpan(ink), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    } else if ("w:p".equals(n) && cur != null) {
                        if (cur.bullet != null) cur.text.insert(0, cur.bullet);
                        if (cell) { cur.after = 2; }
                        blocks.add(cur); cur = null;
                    } else if ("w:tc".equals(n)) cell = false;
                    else if ("w:tr".equals(n)) { Block b = new Block(); b.text = new SpannableStringBuilder(); b.after = 4; blocks.add(b); }
                }
            }
        } finally { z.close(); }
        return layout(blocks, name);
    }

    private static File layout(List<Block> blocks, String name) throws Exception {
        final float W = 595, H = 842, M = 56;
        PdfDocument pdf = new PdfDocument();
        int number = 1;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder((int) W, (int) H, number).create());
        Canvas c = page.getCanvas();
        float y = M;
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setColor(0xff1a1a1a);
        Paint img = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        for (Block b : blocks) {
            if (b.pageBreak) { pdf.finishPage(page); page = pdf.startPage(new PdfDocument.PageInfo.Builder((int) W, (int) H, ++number).create()); c = page.getCanvas(); y = M; continue; }
            if (b.image != null) {
                Bitmap bm = BitmapFactory.decodeByteArray(b.image, 0, b.image.length);
                if (bm == null) continue;
                float iw = b.iw > 0 ? b.iw : bm.getWidth() * 0.75f, ih = b.ih > 0 ? b.ih : bm.getHeight() * 0.75f;
                float k = Math.min(1f, Math.min((W - 2 * M) / iw, (H - 2 * M) / ih)); iw *= k; ih *= k;
                if (y + ih > H - M) { pdf.finishPage(page); page = pdf.startPage(new PdfDocument.PageInfo.Builder((int) W, (int) H, ++number).create()); c = page.getCanvas(); y = M; }
                c.drawBitmap(bm, null, new RectF(M, y, M + iw, y + ih), img);
                bm.recycle();
                y += ih + 8;
                continue;
            }
            y += b.before;
            paint.setTextSize(b.size);
            if (b.text.length() == 0) { y += b.size * 0.9f; continue; }
            StaticLayout l = new StaticLayout(b.text, paint, (int) (W - 2 * M), b.align, 1.12f, 0, false);
            for (int i = 0; i < l.getLineCount(); i++) {
                float top = l.getLineTop(i), bottom = l.getLineBottom(i);
                if (y + (bottom - top) > H - M) { pdf.finishPage(page); page = pdf.startPage(new PdfDocument.PageInfo.Builder((int) W, (int) H, ++number).create()); c = page.getCanvas(); y = M; }
                c.save();
                c.clipRect(M, y, W - M, y + (bottom - top));
                c.translate(M, y - top);
                l.draw(c);
                c.restore();
                y += bottom - top;
            }
            y += b.after;
        }
        pdf.finishPage(page);
        File to = unique(library(), name, ".pdf");
        OutputStream o = new FileOutputStream(to);
        try { pdf.writeTo(o); } finally { o.close(); pdf.close(); }
        return to;
    }

    private static final class Shape {
        float x, y, w, h; boolean placed; int fill; byte[] image; String ph;
        final ArrayList<SpannableStringBuilder> paras = new ArrayList<SpannableStringBuilder>();
        final ArrayList<Layout.Alignment> aligns = new ArrayList<Layout.Alignment>();
    }

    static File pptx(File in, String name) throws Exception {
        ZipFile z = new ZipFile(in);
        PdfDocument pdf = new PdfDocument();
        try {
            byte[] pres = read(z, "ppt/presentation.xml");
            if (pres == null) throw new IOException("not a presentation");
            Map<String, String> prel = rels(z, "ppt/_rels/presentation.xml.rels", "ppt/");
            ArrayList<String> slides = new ArrayList<String>();
            float sw = 960, sh = 540;
            XmlPullParser x = parser(pres);
            for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
                if (e != XmlPullParser.START_TAG) continue;
                if ("p:sldSz".equals(x.getName())) { try { sw = Long.parseLong(attr(x, "cx")) / 12700f; sh = Long.parseLong(attr(x, "cy")) / 12700f; } catch (Throwable ignored) { } }
                else if ("p:sldId".equals(x.getName())) { String p = prel.get(attr(x, "r:id")); if (p != null) slides.add(p); }
            }
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            int number = 0;
            for (String path : slides) {
                byte[] data = read(z, path);
                if (data == null) continue;
                String folder = path.substring(0, path.lastIndexOf('/') + 1);
                Map<String, String> srel = rels(z, folder + "_rels/" + path.substring(path.lastIndexOf('/') + 1) + ".rels", folder);
                List<Shape> shapes = shapes(z, data, srel);
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(Math.round(sw), Math.round(sh), ++number).create());
                Canvas c = page.getCanvas();
                c.drawColor(Color.WHITE);
                for (Shape s : shapes) {
                    if (!s.placed) {
                        boolean title = s.ph != null && (s.ph.equals("title") || s.ph.equals("ctrTitle"));
                        s.x = sw * 0.06f; s.w = sw * 0.88f;
                        if (title) { s.y = sh * 0.06f; s.h = sh * 0.18f; } else { s.y = sh * 0.28f; s.h = sh * 0.64f; }
                        if (title) for (SpannableStringBuilder p : s.paras) if (p.length() > 0 && p.getSpans(0, p.length(), AbsoluteSizeSpan.class).length == 0) p.setSpan(new AbsoluteSizeSpan(36), 0, p.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (s.fill != 0) { fill.setColor(s.fill); c.drawRect(s.x, s.y, s.x + s.w, s.y + s.h, fill); }
                    if (s.image != null) {
                        Bitmap bm = BitmapFactory.decodeByteArray(s.image, 0, s.image.length);
                        if (bm != null) { c.drawBitmap(bm, null, new RectF(s.x, s.y, s.x + s.w, s.y + s.h), img); bm.recycle(); }
                    }
                    float ty = s.y + 4;
                    for (int i = 0; i < s.paras.size(); i++) {
                        SpannableStringBuilder p = s.paras.get(i);
                        paint.setTextSize(18); paint.setColor(0xff1a1a1a);
                        if (p.length() == 0) { ty += 10; continue; }
                        StaticLayout l = new StaticLayout(p, paint, Math.max(10, (int) s.w - 8), s.aligns.get(i), 1.08f, 0, false);
                        c.save(); c.translate(s.x + 4, ty); l.draw(c); c.restore();
                        ty += l.getHeight() + 4;
                    }
                }
                pdf.finishPage(page);
            }
            if (number == 0) throw new IOException("no slides");
            File to = unique(library(), name, ".pdf");
            OutputStream o = new FileOutputStream(to);
            try { pdf.writeTo(o); } finally { o.close(); }
            return to;
        } finally { z.close(); pdf.close(); }
    }

    private static List<Shape> shapes(ZipFile z, byte[] data, Map<String, String> rel) throws Exception {
        ArrayList<Shape> out = new ArrayList<Shape>();
        XmlPullParser x = parser(data);
        Shape cur = null; SpannableStringBuilder para = null; Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
        boolean inText = false, bold = false, italic = false, inSpPr = false, inRun = false, bullet = false; int size = 0, ink = 0; int start = 0;
        for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
            String n = x.getName();
            if (e == XmlPullParser.START_TAG) {
                if ("p:sp".equals(n) || "p:pic".equals(n)) { cur = new Shape(); out.add(cur); }
                else if (cur == null) continue;
                else if ("p:spPr".equals(n)) inSpPr = true;
                else if ("p:ph".equals(n)) cur.ph = attr(x, "type") == null ? "body" : attr(x, "type");
                else if ("a:off".equals(n) && inSpPr) { try { cur.x = Long.parseLong(attr(x, "x")) / 12700f; cur.y = Long.parseLong(attr(x, "y")) / 12700f; cur.placed = true; } catch (Throwable ignored) { } }
                else if ("a:ext".equals(n) && inSpPr) { try { cur.w = Long.parseLong(attr(x, "cx")) / 12700f; cur.h = Long.parseLong(attr(x, "cy")) / 12700f; } catch (Throwable ignored) { } }
                else if ("a:srgbClr".equals(n)) {
                    int v = color(attr(x, "val"), 0);
                    if (inRun) ink = v; else if (inSpPr) cur.fill = v;
                }
                else if ("a:blip".equals(n)) { String p = rel.get(attr(x, "r:embed")); if (p != null) cur.image = read(z, p); }
                else if ("a:p".equals(n)) { para = new SpannableStringBuilder(); align = Layout.Alignment.ALIGN_NORMAL; bullet = false; }
                else if ("a:pPr".equals(n)) {
                    String a = attr(x, "algn");
                    if ("ctr".equals(a)) align = Layout.Alignment.ALIGN_CENTER; else if ("r".equals(a)) align = Layout.Alignment.ALIGN_OPPOSITE;
                }
                else if ("a:buChar".equals(n)) bullet = true;
                else if ("a:r".equals(n)) { inRun = true; bold = false; italic = false; size = 0; ink = 0; }
                else if ("a:rPr".equals(n) || "a:endParaRPr".equals(n)) {
                    bold = "1".equals(attr(x, "b")); italic = "1".equals(attr(x, "i"));
                    try { size = Integer.parseInt(attr(x, "sz")) / 100; } catch (Throwable ignored) { }
                }
                else if ("a:t".equals(n)) { inText = true; if (para != null) start = para.length(); }
            } else if (e == XmlPullParser.TEXT && inText && para != null) {
                para.append(x.getText());
            } else if (e == XmlPullParser.END_TAG) {
                if ("p:spPr".equals(n)) inSpPr = false;
                else if ("a:t".equals(n) && para != null) {
                    inText = false;
                    int a = start, b = para.length();
                    if (b > a) {
                        if (bold) para.setSpan(new StyleSpan(italic ? Typeface.BOLD_ITALIC : Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        else if (italic) para.setSpan(new StyleSpan(Typeface.ITALIC), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (size > 0) para.setSpan(new AbsoluteSizeSpan(size), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (ink != 0) para.setSpan(new ForegroundColorSpan(ink), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                else if ("a:r".equals(n)) inRun = false;
                else if ("a:p".equals(n) && para != null && cur != null) {
                    if (bullet && para.length() > 0) para.insert(0, "•  ");
                    cur.paras.add(para); cur.aligns.add(align); para = null;
                }
                else if ("p:sp".equals(n) || "p:pic".equals(n)) cur = null;
            }
        }
        return out;
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '&') b.append("&amp;"); else if (ch == '<') b.append("&lt;"); else if (ch == '>') b.append("&gt;"); else if (ch == '"') b.append("&quot;");
            else if (ch < 0x20 && ch != '\t' && ch != '\n') continue; else b.append(ch);
        }
        return b.toString();
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException { z.putNextEntry(new ZipEntry(name)); z.write(data); z.closeEntry(); }
    private static void put(ZipOutputStream z, String name, String text) throws IOException { put(z, name, text.getBytes("UTF-8")); }

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    static File toDocx(File in, Progress progress) throws Exception {
        File to = unique(library(), base(in), ".docx");
        PdfRenderer r = open(in, null);
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(to));
        try {
            StringBuilder body = new StringBuilder(), rel = new StringBuilder();
            int n = r.getPageCount(), images = 0;
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page p = r.openPage(i);
                try {
                    String text = text(p);
                    if (i > 0) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
                    if (text.length() > 0) {
                        for (String line : text.split("\\r?\\n")) {
                            body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(line)).append("</w:t></w:r></w:p>");
                        }
                    } else {
                        Bitmap b = render(p, 150, 2200);
                        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
                        b.compress(Bitmap.CompressFormat.JPEG, 85, jpg);
                        images++;
                        put(z, "word/media/page" + images + ".jpg", jpg.toByteArray());
                        long cx = 5943600L, cy = Math.round(cx * (double) b.getHeight() / b.getWidth());
                        if (cy > 8229600L) { cx = Math.round(cx * 8229600.0 / cy); cy = 8229600L; }
                        b.recycle();
                        rel.append("<Relationship Id=\"rIdImg").append(images).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/page").append(images).append(".jpg\"/>");
                        body.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy)
                                .append("\"/><wp:docPr id=\"").append(images).append("\" name=\"Page ").append(i + 1).append("\"/><a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr><pic:cNvPr id=\"")
                                .append(images).append("\" name=\"page").append(images).append(".jpg\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"rIdImg").append(images).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"")
                                .append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
                    }
                } finally { p.close(); }
                if (progress != null) progress.step(i + 1, n);
            }
            put(z, "[Content_Types].xml", XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            put(z, "_rels/.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            put(z, "word/_rels/document.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + rel + "</Relationships>");
            put(z, "word/document.xml", XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"><w:body>"
                    + body + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");
            z.close();
        } catch (Exception e) { try { z.close(); } catch (Throwable ignored) { } to.delete(); throw e; }
        finally { r.close(); }
        return to;
    }

    static File toPptx(File in, Progress progress) throws Exception {
        File to = unique(library(), base(in), ".pptx");
        PdfRenderer r = open(in, null);
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(to));
        try {
            int n = r.getPageCount();
            long sw = 12192000L, sh = 6858000L;
            if (n > 0) {
                PdfRenderer.Page first = r.openPage(0);
                double ratio = first.getHeight() / (double) first.getWidth();
                first.close();
                if (ratio > 1) { sh = 9144000L; sw = Math.round(sh / ratio); } else { sw = 12192000L; sh = Math.round(sw * ratio); }
            }
            String ns = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"";
            StringBuilder types = new StringBuilder(), ids = new StringBuilder(), prels = new StringBuilder();
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page p = r.openPage(i);
                Bitmap b;
                try { b = render(p, 170, 2600); } finally { p.close(); }
                ByteArrayOutputStream jpg = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 86, jpg);
                double ratio = b.getHeight() / (double) b.getWidth();
                b.recycle();
                long cw = sw, ch = Math.round(sw * ratio);
                if (ch > sh) { ch = sh; cw = Math.round(sh / ratio); }
                long ox = (sw - cw) / 2, oy = (sh - ch) / 2;
                int k = i + 1;
                put(z, "ppt/media/page" + k + ".jpg", jpg.toByteArray());
                put(z, "ppt/slides/slide" + k + ".xml", XML + "<p:sld " + ns + "><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:pic><p:nvPicPr><p:cNvPr id=\"2\" name=\"Page " + k + "\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"rId2\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x=\"" + ox + "\" y=\"" + oy + "\"/><a:ext cx=\"" + cw + "\" cy=\"" + ch + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>");
                put(z, "ppt/slides/_rels/slide" + k + ".xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/page" + k + ".jpg\"/></Relationships>");
                types.append("<Override PartName=\"/ppt/slides/slide").append(k).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
                ids.append("<p:sldId id=\"").append(255 + k).append("\" r:id=\"rId").append(k + 2).append("\"/>");
                prels.append("<Relationship Id=\"rId").append(k + 2).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide").append(k).append(".xml\"/>");
                if (progress != null) progress.step(k, n);
            }
            put(z, "[Content_Types].xml", XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>"
                    + "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>"
                    + "<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>"
                    + "<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>"
                    + "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>" + types + "</Types>");
            put(z, "_rels/.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>");
            put(z, "ppt/presentation.xml", XML + "<p:presentation " + ns + "><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>" + ids + "</p:sldIdLst><p:sldSz cx=\"" + sw + "\" cy=\"" + sh + "\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>");
            put(z, "ppt/_rels/presentation.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>" + prels + "</Relationships>");
            String tree = "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>";
            put(z, "ppt/slideMasters/slideMaster1.xml", XML + "<p:sldMaster " + ns + ">" + tree + "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>");
            put(z, "ppt/slideMasters/_rels/slideMaster1.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/></Relationships>");
            put(z, "ppt/slideLayouts/slideLayout1.xml", XML + "<p:sldLayout " + ns + " type=\"blank\" preserve=\"1\">" + tree.replace("<p:cSld>", "<p:cSld name=\"Blank\">") + "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>");
            put(z, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>");
            put(z, "ppt/theme/theme1.xml", XML + theme());
            z.close();
        } catch (Exception e) { try { z.close(); } catch (Throwable ignored) { } to.delete(); throw e; }
        finally { r.close(); }
        return to;
    }

    private static String theme() {
        StringBuilder c = new StringBuilder();
        String[][] colors = {{"dk1", "000000"}, {"lt1", "FFFFFF"}, {"dk2", "1F2937"}, {"lt2", "F3F4F6"}, {"accent1", "1A73E8"}, {"accent2", "E8453C"}, {"accent3", "F9AB00"}, {"accent4", "34A853"}, {"accent5", "9334E6"}, {"accent6", "12B5CB"}, {"hlink", "1A73E8"}, {"folHlink", "9334E6"}};
        for (String[] x : colors) c.append("<a:").append(x[0]).append("><a:srgbClr val=\"").append(x[1]).append("\"/></a:").append(x[0]).append(">");
        String solid = "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>";
        String line = "<a:ln w=\"9525\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>";
        return "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Pixel\"><a:themeElements><a:clrScheme name=\"Pixel\">" + c + "</a:clrScheme>"
                + "<a:fontScheme name=\"Pixel\"><a:majorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont><a:minorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>"
                + "<a:fmtScheme name=\"Pixel\"><a:fillStyleLst>" + solid + solid + solid + "</a:fillStyleLst><a:lnStyleLst>" + line + line + line + "</a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst>" + solid + solid + solid + "</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>";
    }
}
