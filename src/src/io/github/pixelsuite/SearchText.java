package io.github.pixelsuite;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class SearchText {
    static final int MAX_CHARS = 400000;
    private static final Charset LATIN = Charset.forName("ISO-8859-1");
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private SearchText() {}

    static boolean readable(String ext) {
        return "pdf".equals(ext) || "docx".equals(ext) || "xlsx".equals(ext) || "xls".equals(ext) || "pptx".equals(ext) || "csv".equals(ext) || "txt".equals(ext)
                || "md".equals(ext) || "json".equals(ext) || "xml".equals(ext) || "html".equals(ext) || "htm".equals(ext) || "log".equals(ext) || "tsv".equals(ext)
                || "rtf".equals(ext) || "vcf".equals(ext) || "ics".equals(ext);
    }

    static long limit(String ext) {
        if ("pdf".equals(ext)) return 25L << 20;
        if ("docx".equals(ext) || "xlsx".equals(ext) || "pptx".equals(ext) || "xls".equals(ext)) return 40L << 20;
        return 8L << 20;
    }

    static String extract(File f, String ext) {
        try {
            if (f.length() > limit(ext)) return null;
            String s;
            if ("pdf".equals(ext)) s = pdf(read(f));
            else if ("docx".equals(ext)) s = office(f, "word/", "t", "p", "tab");
            else if ("pptx".equals(ext)) s = office(f, "ppt/slides/slide", "t", "p", null);
            else if ("xlsx".equals(ext)) s = xlsx(f);
            else if ("xls".equals(ext)) s = xls(read(f));
            else if ("rtf".equals(ext)) s = rtf(plain(read(f)));
            else s = plain(read(f));
            if (s == null) return null;
            s = s.replace('\u0000', ' ');
            return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) : s;
        } catch (Throwable t) { return null; }
    }

    static byte[] read(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(f.length(), 64L << 20));
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }

    static String plain(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xfe) return new String(b, 2, b.length - 2, Charset.forName("UTF-16LE"));
        if (b.length >= 2 && (b[0] & 0xff) == 0xfe && (b[1] & 0xff) == 0xff) return new String(b, 2, b.length - 2, Charset.forName("UTF-16BE"));
        int start = b.length >= 3 && (b[0] & 0xff) == 0xef && (b[1] & 0xff) == 0xbb && (b[2] & 0xff) == 0xbf ? 3 : 0;
        String s = new String(b, start, b.length - start, UTF8);
        int bad = 0, n = Math.min(s.length(), 4000);
        for (int i = 0; i < n; i++) if (s.charAt(i) == '�') bad++;
        if (n > 0 && bad * 20 > n) s = new String(b, start, b.length - start, Charset.forName("windows-1256"));
        return s;
    }

    private static String rtf(String s) {
        StringBuilder b = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') { depth++; continue; }
            if (c == '}') { depth--; continue; }
            if (c == '\\') {
                int j = i + 1;
                if (j < s.length() && s.charAt(j) == '\'' && j + 2 < s.length()) {
                    try { b.append((char) Integer.parseInt(s.substring(j + 1, j + 3), 16)); } catch (Throwable ignored) { }
                    i = j + 2; continue;
                }
                while (j < s.length() && Character.isLetter(s.charAt(j))) j++;
                String word = s.substring(i + 1, j);
                int k = j; while (k < s.length() && (Character.isDigit(s.charAt(k)) || s.charAt(k) == '-')) k++;
                if ("par".equals(word) || "line".equals(word)) b.append('\n');
                if ("u".equals(word) && k > j) try { b.append((char) Integer.parseInt(s.substring(j, k))); } catch (Throwable ignored) { }
                if (k < s.length() && s.charAt(k) == ' ') k++;
                i = k - 1; continue;
            }
            if (c != '\r' && c != '\n') b.append(c);
        }
        return b.toString();
    }

    private static List<ZipEntry> entries(ZipFile z, String prefix) {
        ArrayList<ZipEntry> out = new ArrayList<ZipEntry>();
        Enumeration<? extends ZipEntry> e = z.entries();
        while (e.hasMoreElements()) {
            ZipEntry x = e.nextElement();
            String n = x.getName();
            if (n.startsWith(prefix) && n.endsWith(".xml") && !n.contains("_rels")) out.add(x);
        }
        Collections.sort(out, (a, b) -> natural(a.getName(), b.getName()));
        return out;
    }

    private static int natural(String a, String b) {
        Matcher ma = Pattern.compile("(\\d+)\\.xml$").matcher(a), mb = Pattern.compile("(\\d+)\\.xml$").matcher(b);
        if (ma.find() && mb.find() && a.substring(0, ma.start()).equals(b.substring(0, mb.start()))) {
            try { return Long.compare(Long.parseLong(ma.group(1)), Long.parseLong(mb.group(1))); } catch (Throwable ignored) { }
        }
        return a.compareTo(b);
    }

    private static String local(String name) { int i = name.indexOf(':'); return i >= 0 ? name.substring(i + 1) : name; }

    private static String office(File f, String prefix, String textTag, String paraTag, String tabTag) throws Exception {
        ZipFile z = new ZipFile(f);
        try {
            StringBuilder b = new StringBuilder();
            List<ZipEntry> list = entries(z, prefix);
            if (prefix.equals("word/")) {
                ArrayList<ZipEntry> keep = new ArrayList<ZipEntry>();
                for (ZipEntry e : list) { String n = e.getName(); if (n.equals("word/document.xml") || n.startsWith("word/header") || n.startsWith("word/footer") || n.equals("word/footnotes.xml")) keep.add(e); }
                list = keep;
            }
            for (ZipEntry e : list) {
                InputStream in = z.getInputStream(e);
                try {
                    XmlPullParser p = android.util.Xml.newPullParser();
                    p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    p.setInput(in, "UTF-8");
                    boolean inText = false;
                    for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                        if (ev == XmlPullParser.START_TAG) {
                            String n = local(p.getName());
                            if (n.equals(textTag)) inText = true;
                            else if (tabTag != null && n.equals(tabTag)) b.append('\t');
                            else if (n.equals("br")) b.append('\n');
                        } else if (ev == XmlPullParser.END_TAG) {
                            String n = local(p.getName());
                            if (n.equals(textTag)) inText = false;
                            else if (n.equals(paraTag)) b.append('\n');
                        } else if (ev == XmlPullParser.TEXT && inText) b.append(p.getText());
                        if (b.length() > MAX_CHARS) break;
                    }
                } finally { in.close(); }
                if (!prefix.equals("word/")) b.append('\f');
                if (b.length() > MAX_CHARS) break;
            }
            return b.toString();
        } finally { z.close(); }
    }

    private static String number(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.indexOf('E') >= 0 || v.indexOf('e') >= 0) {
            try { return new BigDecimal(v).stripTrailingZeros().toPlainString(); } catch (Throwable ignored) { }
        }
        if (v.endsWith(".0")) return v.substring(0, v.length() - 2);
        return v;
    }

    private static String xlsx(File f) throws Exception {
        ZipFile z = new ZipFile(f);
        try {
            ArrayList<String> shared = new ArrayList<String>();
            ZipEntry ss = z.getEntry("xl/sharedStrings.xml");
            if (ss != null) {
                InputStream in = z.getInputStream(ss);
                try {
                    XmlPullParser p = android.util.Xml.newPullParser();
                    p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    p.setInput(in, "UTF-8");
                    StringBuilder cur = null; boolean inT = false, inPhonetic = false;
                    for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                        if (ev == XmlPullParser.START_TAG) {
                            String n = local(p.getName());
                            if (n.equals("si")) cur = new StringBuilder();
                            else if (n.equals("rPh")) inPhonetic = true;
                            else if (n.equals("t")) inT = true;
                        } else if (ev == XmlPullParser.END_TAG) {
                            String n = local(p.getName());
                            if (n.equals("si") && cur != null) { shared.add(cur.toString()); cur = null; }
                            else if (n.equals("rPh")) inPhonetic = false;
                            else if (n.equals("t")) inT = false;
                        } else if (ev == XmlPullParser.TEXT && inT && !inPhonetic && cur != null) cur.append(p.getText());
                    }
                } finally { in.close(); }
            }
            StringBuilder b = new StringBuilder();
            for (ZipEntry e : entries(z, "xl/worksheets/sheet")) {
                InputStream in = z.getInputStream(e);
                try {
                    XmlPullParser p = android.util.Xml.newPullParser();
                    p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    p.setInput(in, "UTF-8");
                    String type = null; StringBuilder val = null; boolean inV = false, inT = false;
                    for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                        if (ev == XmlPullParser.START_TAG) {
                            String n = local(p.getName());
                            if (n.equals("c")) { type = p.getAttributeValue(null, "t"); val = new StringBuilder(); }
                            else if (n.equals("v")) inV = true;
                            else if (n.equals("t")) inT = true;
                        } else if (ev == XmlPullParser.END_TAG) {
                            String n = local(p.getName());
                            if (n.equals("v")) inV = false;
                            else if (n.equals("t")) inT = false;
                            else if (n.equals("c") && val != null) {
                                String v = val.toString(), out;
                                if ("s".equals(type)) { try { int i = Integer.parseInt(v.trim()); out = i >= 0 && i < shared.size() ? shared.get(i) : ""; } catch (Throwable t) { out = ""; } }
                                else if ("inlineStr".equals(type) || "str".equals(type) || "e".equals(type)) out = v;
                                else if ("b".equals(type)) out = "1".equals(v.trim()) ? "TRUE" : "FALSE";
                                else out = number(v);
                                if (!out.isEmpty()) b.append(out).append('\t');
                                val = null;
                            } else if (n.equals("row")) b.append('\n');
                        } else if (ev == XmlPullParser.TEXT && val != null && (inV || inT)) val.append(p.getText());
                        if (b.length() > MAX_CHARS) break;
                    }
                } finally { in.close(); }
                b.append('\f');
                if (b.length() > MAX_CHARS) break;
            }
            return b.toString();
        } finally { z.close(); }
    }

    private static int u16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    private static int s32(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24); }
    private static long u64(byte[] b, int o) { return (s32(b, o) & 0xffffffffL) | ((long) s32(b, o + 4) << 32); }

    private static byte[] cfbStream(byte[] d, String[] names) {
        if (d.length < 512 || s32(d, 0) != 0xe011cfd0) return null;
        int sectorShift = u16(d, 30), miniShift = u16(d, 32);
        int sector = 1 << sectorShift, mini = 1 << miniShift;
        int dirStart = s32(d, 48), miniCutoff = s32(d, 56), miniFatStart = s32(d, 60), difatStart = s32(d, 68), difatCount = s32(d, 72);
        ArrayList<Integer> fatSectors = new ArrayList<Integer>();
        for (int i = 0; i < 109; i++) { int s = s32(d, 76 + i * 4); if (s >= 0) fatSectors.add(s); }
        int next = difatStart;
        for (int k = 0; k < difatCount && next >= 0 && k < 10000; k++) {
            int off = (next + 1) * sector;
            if (off + sector > d.length) break;
            for (int i = 0; i < sector / 4 - 1; i++) { int s = s32(d, off + i * 4); if (s >= 0) fatSectors.add(s); }
            next = s32(d, off + sector - 4);
        }
        int per = sector / 4;
        int[] fat = new int[fatSectors.size() * per];
        for (int i = 0; i < fatSectors.size(); i++) {
            int off = (fatSectors.get(i) + 1) * sector;
            for (int j = 0; j < per && off + j * 4 + 4 <= d.length; j++) fat[i * per + j] = s32(d, off + j * 4);
        }
        byte[] dir = chain(d, fat, dirStart, sector, -1);
        if (dir == null) return null;
        int rootStart = -1; long rootSize = 0; int found = -1; long foundSize = 0;
        for (int o = 0; o + 128 <= dir.length; o += 128) {
            int nameLen = u16(dir, o + 64);
            if (nameLen < 2 || nameLen > 64) continue;
            String name = new String(dir, o, nameLen - 2, Charset.forName("UTF-16LE"));
            int type = dir[o + 66] & 0xff;
            int start = s32(dir, o + 116); long size = u64(dir, o + 120) & 0xffffffffL;
            if (type == 5) { rootStart = start; rootSize = size; }
            if (type == 2) for (String want : names) if (want.equalsIgnoreCase(name) && found < 0) { found = start; foundSize = size; }
        }
        if (found < 0) return null;
        if (foundSize >= miniCutoff) return trim(chain(d, fat, found, sector, foundSize), foundSize);
        byte[] miniFatBytes = chain(d, fat, miniFatStart, sector, -1);
        byte[] ministream = chain(d, fat, rootStart, sector, rootSize);
        if (miniFatBytes == null || ministream == null) return null;
        int[] miniFat = new int[miniFatBytes.length / 4];
        for (int i = 0; i < miniFat.length; i++) miniFat[i] = s32(miniFatBytes, i * 4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int s = found; int guard = 0;
        while (s >= 0 && s < miniFat.length && out.size() < foundSize && guard++ < 1000000) {
            int off = s * mini;
            if (off + mini > ministream.length) break;
            out.write(ministream, off, mini);
            s = miniFat[s];
        }
        return trim(out.toByteArray(), foundSize);
    }

    private static byte[] trim(byte[] b, long size) {
        if (b == null || size < 0 || b.length <= size) return b;
        byte[] r = new byte[(int) size]; System.arraycopy(b, 0, r, 0, r.length); return r;
    }

    private static byte[] chain(byte[] d, int[] fat, int start, int sector, long size) {
        if (start < 0) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int s = start, guard = 0;
        while (s >= 0 && s < fat.length && guard++ < 2000000) {
            int off = (s + 1) * sector;
            if (off + sector > d.length) { if (off < d.length) out.write(d, off, d.length - off); break; }
            out.write(d, off, sector);
            if (size >= 0 && out.size() >= size) break;
            s = fat[s];
        }
        return out.toByteArray();
    }

    private static String xls(byte[] file) {
        byte[] w = cfbStream(file, new String[]{"Workbook", "Book"});
        if (w == null) return null;
        ArrayList<String> sst = new ArrayList<String>();
        StringBuilder b = new StringBuilder();
        int lastRow = -1;
        int pos = 0;
        while (pos + 4 <= w.length && b.length() < MAX_CHARS) {
            int id = u16(w, pos), len = u16(w, pos + 2);
            int data = pos + 4;
            if (data + len > w.length) break;
            if (id == 0x00FC) {
                ArrayList<int[]> parts = new ArrayList<int[]>();
                parts.add(new int[]{data, len});
                int q = data + len;
                while (q + 4 <= w.length && u16(w, q) == 0x003C) { int l2 = u16(w, q + 2); parts.add(new int[]{q + 4, l2}); q += 4 + l2; }
                readSst(w, parts, sst);
                pos = q; continue;
            }
            if (id == 0x0809 && len >= 4) { int kind = u16(w, data + 2); if (kind == 0x10) { if (b.length() > 0) b.append('\f'); lastRow = -1; } }
            else if (id == 0x00FD && len >= 10) { int row = u16(w, data); int i = s32(w, data + 6); row(b, row, lastRow); lastRow = row; if (i >= 0 && i < sst.size()) b.append(sst.get(i)).append('\t'); }
            else if (id == 0x0203 && len >= 14) { int row = u16(w, data); row(b, row, lastRow); lastRow = row; b.append(num(Double.longBitsToDouble(u64(w, data + 6)))).append('\t'); }
            else if (id == 0x027E && len >= 10) { int row = u16(w, data); row(b, row, lastRow); lastRow = row; b.append(num(rk(s32(w, data + 6)))).append('\t'); }
            else if (id == 0x00BD && len >= 6) {
                int row = u16(w, data); row(b, row, lastRow); lastRow = row;
                int first = u16(w, data + 2), last = u16(w, data + len - 2);
                for (int c = 0; c <= last - first && data + 4 + c * 6 + 6 <= data + len - 2; c++) b.append(num(rk(s32(w, data + 4 + c * 6 + 2)))).append('\t');
            }
            else if (id == 0x0204 && len >= 9) {
                int row = u16(w, data); row(b, row, lastRow); lastRow = row;
                int cch = u16(w, data + 6); int flags = w[data + 8] & 0xff;
                b.append(chars(w, data + 9, cch, (flags & 1) != 0)).append('\t');
            }
            pos = data + len;
        }
        return b.toString();
    }

    private static void row(StringBuilder b, int row, int last) { if (last >= 0 && row != last) b.append('\n'); }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        try { return new BigDecimal(v).round(new java.math.MathContext(15)).stripTrailingZeros().toPlainString(); } catch (Throwable t) { return Double.toString(v); }
    }

    private static double rk(int v) {
        double d;
        if ((v & 2) != 0) d = v >> 2;
        else d = Double.longBitsToDouble(((long) (v & 0xfffffffc)) << 32);
        if ((v & 1) != 0) d /= 100;
        return d;
    }

    private static String chars(byte[] w, int o, int count, boolean wide) {
        if (wide) { int n = Math.min(count * 2, w.length - o); return n <= 0 ? "" : new String(w, o, n, Charset.forName("UTF-16LE")); }
        int n = Math.min(count, w.length - o); return n <= 0 ? "" : new String(w, o, n, LATIN);
    }

    private static void readSst(byte[] w, List<int[]> parts, List<String> out) {
        int part = 0; int p = parts.get(0)[0]; int end = p + parts.get(0)[1];
        if (end - p < 8) return;
        int unique = s32(w, p + 4); p += 8;
        for (int s = 0; s < unique && out.size() < 2000000; s++) {
            if (p + 3 > end) { if (++part >= parts.size()) return; p = parts.get(part)[0]; end = p + parts.get(part)[1]; }
            int cch = u16(w, p); int flags = w[p + 2] & 0xff; p += 3;
            int runs = 0, ext = 0;
            if ((flags & 8) != 0) { runs = u16(w, p); p += 2; }
            if ((flags & 4) != 0) { ext = s32(w, p); p += 4; }
            boolean wide = (flags & 1) != 0;
            StringBuilder sb = new StringBuilder();
            int left = cch;
            while (left > 0) {
                int avail = (end - p) / (wide ? 2 : 1);
                int take = Math.min(left, Math.max(0, avail));
                sb.append(chars(w, p, take, wide)); p += take * (wide ? 2 : 1); left -= take;
                if (left > 0) {
                    if (++part >= parts.size()) { out.add(sb.toString()); return; }
                    p = parts.get(part)[0]; end = p + parts.get(part)[1];
                    wide = (w[p] & 1) != 0; p++;
                }
            }
            out.add(sb.toString());
            int skip = runs * 4 + ext;
            while (skip > 0) {
                int step = Math.min(skip, end - p); p += step; skip -= step;
                if (skip > 0) { if (++part >= parts.size()) return; p = parts.get(part)[0]; end = p + parts.get(part)[1]; }
            }
        }
    }

    private static final class Obj {
        String dict; int streamAt = -1, streamLen = -1; byte[] own;
    }

    private static final class Pdf {
        final byte[] d;
        final HashMap<Integer, Obj> objs = new HashMap<Integer, Obj>();
        final HashMap<Integer, Font> fonts = new HashMap<Integer, Font>();
        Pdf(byte[] d) { this.d = d; }
    }

    private static final class Font {
        final TreeMap<Long, String> map = new TreeMap<Long, String>();
        int bytes = 1; boolean cmap, wide;
    }

    private static final Pattern OBJ = Pattern.compile("(\\d+)\\s+(\\d+)\\s+obj\\b");
    private static final Pattern REF = Pattern.compile("(\\d+)\\s+\\d+\\s+R");

    static String pdf(byte[] d) {
        if (d.length < 8) return null;
        Pdf pdf = new Pdf(d);
        String all = new String(d, LATIN);
        Matcher m = OBJ.matcher(all);
        while (m.find()) {
            int num;
            try { num = Integer.parseInt(m.group(1)); } catch (Throwable t) { continue; }
            int start = m.end();
            int endobj = all.indexOf("endobj", start);
            int stream = all.indexOf("stream", start);
            Obj o = new Obj();
            if (stream >= 0 && (endobj < 0 || stream < endobj) && all.lastIndexOf(">>", stream) >= start) {
                o.dict = all.substring(start, stream);
                int s = stream + 6;
                if (s < d.length && d[s] == '\r') s++;
                if (s < d.length && d[s] == '\n') s++;
                o.streamAt = s;
                int es = all.indexOf("endstream", s);
                o.streamLen = es < 0 ? d.length - s : es - s;
                Integer len = directInt(o.dict, "/Length");
                if (len != null && len >= 0 && s + len <= d.length) o.streamLen = len;
            } else o.dict = endobj < 0 ? all.substring(start, Math.min(all.length(), start + 4096)) : all.substring(start, endobj);
            pdf.objs.put(num, o);
        }
        for (Map.Entry<Integer, Obj> e : new ArrayList<Map.Entry<Integer, Obj>>(pdf.objs.entrySet())) {
            Obj o = e.getValue();
            if (o.dict == null || !o.dict.contains("/ObjStm")) continue;
            byte[] body = stream(pdf, o);
            if (body == null) continue;
            Integer n = directInt(o.dict, "/N"), first = directInt(o.dict, "/First");
            if (n == null || first == null) continue;
            String head = new String(body, 0, Math.min(first, body.length), LATIN).trim();
            String[] nums = head.split("\\s+");
            String text = new String(body, LATIN);
            for (int i = 0; i + 1 < nums.length && i / 2 < n; i += 2) {
                try {
                    int id = Integer.parseInt(nums[i]); int off = first + Integer.parseInt(nums[i + 1]);
                    int stop = i + 3 < nums.length ? first + Integer.parseInt(nums[i + 3]) : text.length();
                    if (off < 0 || off > text.length() || stop > text.length() || stop < off) continue;
                    if (!pdf.objs.containsKey(id)) { Obj x = new Obj(); x.dict = text.substring(off, stop); pdf.objs.put(id, x); }
                } catch (Throwable ignored) { }
            }
        }
        List<Integer> pages = pages(pdf, all);
        StringBuilder out = new StringBuilder();
        for (int page : pages) {
            Obj p = pdf.objs.get(page);
            if (p == null) continue;
            String res = inherited(pdf, page, "/Resources");
            ArrayList<Integer> contents = new ArrayList<Integer>();
            String c = value(p.dict, "/Contents");
            if (c != null) { Matcher r = REF.matcher(c); while (r.find()) contents.add(Integer.parseInt(r.group(1))); }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            for (int id : contents) {
                Obj s = pdf.objs.get(id);
                if (s == null) continue;
                if (s.streamAt < 0 && s.dict != null && s.dict.trim().startsWith("[")) { Matcher r = REF.matcher(s.dict); while (r.find()) { Obj t = pdf.objs.get(Integer.parseInt(r.group(1))); byte[] b = t == null ? null : stream(pdf, t); if (b != null) { body.write(b, 0, b.length); body.write('\n'); } } continue; }
                byte[] b = stream(pdf, s);
                if (b != null) { body.write(b, 0, b.length); body.write('\n'); }
            }
            run(pdf, body.toByteArray(), res, out, 0);
            out.append('\f');
            if (out.length() > MAX_CHARS) break;
        }
        return out.toString();
    }

    private static List<Integer> pages(Pdf pdf, String all) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        int at = all.lastIndexOf("/Root");
        Integer root = null;
        if (at >= 0) { Matcher r = REF.matcher(all.substring(at, Math.min(all.length(), at + 40))); if (r.find()) root = Integer.parseInt(r.group(1)); }
        Obj catalog = root == null ? null : pdf.objs.get(root);
        Integer tree = catalog == null ? null : ref(catalog.dict, "/Pages");
        if (tree != null) walk(pdf, tree, out, 0, new java.util.HashSet<Integer>());
        if (out.isEmpty()) {
            ArrayList<Integer> ids = new ArrayList<Integer>(pdf.objs.keySet());
            Collections.sort(ids);
            for (int id : ids) { Obj o = pdf.objs.get(id); if (o.dict != null && Pattern.compile("/Type\\s*/Page(?![s\\w])").matcher(o.dict).find()) out.add(id); }
        }
        return out;
    }

    private static void walk(Pdf pdf, int id, List<Integer> out, int depth, java.util.Set<Integer> seen) {
        if (depth > 40 || !seen.add(id) || out.size() > 5000) return;
        Obj o = pdf.objs.get(id);
        if (o == null || o.dict == null) return;
        if (Pattern.compile("/Type\\s*/Page(?![s\\w])").matcher(o.dict).find()) { out.add(id); return; }
        String kids = value(o.dict, "/Kids");
        if (kids == null) return;
        Matcher r = REF.matcher(kids);
        while (r.find()) walk(pdf, Integer.parseInt(r.group(1)), out, depth + 1, seen);
    }

    private static String inherited(Pdf pdf, int page, String key) {
        Integer id = page;
        for (int i = 0; i < 30 && id != null; i++) {
            Obj o = pdf.objs.get(id);
            if (o == null || o.dict == null) return null;
            String v = value(o.dict, key);
            if (v != null) return deref(pdf, v);
            id = ref(o.dict, "/Parent");
        }
        return null;
    }

    private static String deref(Pdf pdf, String v) {
        if (v == null) return null;
        String t = v.trim();
        Matcher r = Pattern.compile("^(\\d+)\\s+\\d+\\s+R").matcher(t);
        if (r.find()) { Obj o = pdf.objs.get(Integer.parseInt(r.group(1))); return o == null ? null : o.dict; }
        return t;
    }

    private static Integer ref(String dict, String key) {
        String v = value(dict, key);
        if (v == null) return null;
        Matcher r = Pattern.compile("^\\s*(\\d+)\\s+\\d+\\s+R").matcher(v);
        return r.find() ? Integer.parseInt(r.group(1)) : null;
    }

    private static Integer directInt(String dict, String key) {
        String v = value(dict, key);
        if (v == null) return null;
        Matcher r = Pattern.compile("^\\s*(\\d+)(\\s+\\d+\\s+R)?").matcher(v);
        if (!r.find() || r.group(2) != null) return null;
        try { return Integer.parseInt(r.group(1)); } catch (Throwable t) { return null; }
    }

    private static String value(String dict, String key) {
        if (dict == null) return null;
        int depth = 0;
        for (int i = 0; i < dict.length(); i++) {
            char c = dict.charAt(i);
            if (c == '(') { i = skipString(dict, i); continue; }
            if (c == '<' && i + 1 < dict.length() && dict.charAt(i + 1) == '<') { depth++; i++; continue; }
            if (c == '>' && i + 1 < dict.length() && dict.charAt(i + 1) == '>') { depth--; i++; continue; }
            if (depth == 1 && dict.startsWith(key, i)) {
                int after = i + key.length();
                if (after < dict.length() && (Character.isLetterOrDigit(dict.charAt(after)) || dict.charAt(after) == '_')) continue;
                return token(dict, after);
            }
        }
        return null;
    }

    private static int skipString(String s, int i) {
        int depth = 0;
        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '\\') { j++; continue; }
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return j;
        }
        return s.length() - 1;
    }

    private static String token(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        if (c == '<' && i + 1 < s.length() && s.charAt(i + 1) == '<') {
            int depth = 0;
            for (int j = i; j < s.length() - 1; j++) {
                if (s.charAt(j) == '(') { j = skipString(s, j); continue; }
                if (s.charAt(j) == '<' && s.charAt(j + 1) == '<') { depth++; j++; }
                else if (s.charAt(j) == '>' && s.charAt(j + 1) == '>') { depth--; j++; if (depth == 0) return s.substring(i, j + 1); }
            }
            return s.substring(i);
        }
        if (c == '[') {
            int depth = 0;
            for (int j = i; j < s.length(); j++) {
                if (s.charAt(j) == '(') { j = skipString(s, j); continue; }
                if (s.charAt(j) == '[') depth++;
                else if (s.charAt(j) == ']' && --depth == 0) return s.substring(i, j + 1);
            }
            return s.substring(i);
        }
        Matcher r = Pattern.compile("^(\\d+\\s+\\d+\\s+R|/[^\\s/<>\\[\\]()]+|[^\\s/<>\\[\\]()]+|\\([^)]*\\))").matcher(s.substring(i, Math.min(s.length(), i + 200)));
        return r.find() ? r.group(1) : null;
    }

    private static byte[] stream(Pdf pdf, Obj o) {
        if (o.own != null) return o.own;
        if (o.streamAt < 0) return null;
        int len = o.streamLen;
        String lenRef = value(o.dict, "/Length");
        if (lenRef != null) {
            Matcher r = Pattern.compile("^(\\d+)\\s+\\d+\\s+R").matcher(lenRef.trim());
            if (r.find()) { Obj x = pdf.objs.get(Integer.parseInt(r.group(1))); if (x != null && x.dict != null) try { int v = Integer.parseInt(x.dict.trim().split("\\s+")[0]); if (v >= 0 && o.streamAt + v <= pdf.d.length) len = v; } catch (Throwable ignored) { } }
        }
        if (len <= 0 || o.streamAt + len > pdf.d.length) return null;
        String filter = value(o.dict, "/Filter");
        byte[] raw = new byte[len];
        System.arraycopy(pdf.d, o.streamAt, raw, 0, len);
        if (filter == null) return raw;
        byte[] cur = raw;
        Matcher names = Pattern.compile("/(\\w+)").matcher(filter);
        while (names.find() && cur != null) {
            String f = names.group(1);
            if ("FlateDecode".equals(f) || "Fl".equals(f)) cur = inflate(cur);
            else if ("ASCII85Decode".equals(f) || "A85".equals(f)) cur = a85(cur);
            else if ("ASCIIHexDecode".equals(f) || "AHx".equals(f)) cur = ahx(cur);
            else return null;
        }
        return cur;
    }

    private static byte[] a85(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        long acc = 0; int count = 0;
        for (int i = 0; i < in.length; i++) {
            int c = in[i] & 0xff;
            if (c == '~') break;
            if (c <= ' ') continue;
            if (c == 'z' && count == 0) { out.write(0); out.write(0); out.write(0); out.write(0); continue; }
            if (c < '!' || c > 'u') return null;
            acc = acc * 85 + (c - '!'); count++;
            if (count == 5) { out.write((int) (acc >>> 24) & 0xff); out.write((int) (acc >>> 16) & 0xff); out.write((int) (acc >>> 8) & 0xff); out.write((int) acc & 0xff); acc = 0; count = 0; }
        }
        if (count > 1) {
            for (int k = count; k < 5; k++) acc = acc * 85 + 84;
            for (int k = 0; k < count - 1; k++) out.write((int) (acc >>> (24 - 8 * k)) & 0xff);
        }
        return out.toByteArray();
    }

    private static byte[] ahx(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length / 2);
        int hi = -1;
        for (byte x : in) {
            int c = x & 0xff;
            if (c == '>') break;
            int v = Character.digit((char) c, 16);
            if (v < 0) continue;
            if (hi < 0) hi = v; else { out.write(hi * 16 + v); hi = -1; }
        }
        if (hi >= 0) out.write(hi * 16);
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] raw) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(raw);
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length * 3);
            byte[] buf = new byte[65536];
            int guard = 0;
            while (!inf.finished() && guard++ < 100000) {
                int n = inf.inflate(buf);
                if (n == 0) { if (inf.needsInput() || inf.needsDictionary()) break; }
                out.write(buf, 0, n);
                if (out.size() > (48 << 20)) break;
            }
            return out.toByteArray();
        } catch (Throwable t) { return null; }
        finally { inf.end(); }
    }

    private static Font font(Pdf pdf, int id) {
        Font f = pdf.fonts.get(id);
        if (f != null) return f;
        f = new Font();
        pdf.fonts.put(id, f);
        Obj o = pdf.objs.get(id);
        if (o == null || o.dict == null) return f;
        f.wide = o.dict.contains("/Type0");
        if (f.wide) f.bytes = 2;
        Integer tu = ref(o.dict, "/ToUnicode");
        Obj cm = tu == null ? null : pdf.objs.get(tu);
        byte[] b = cm == null ? null : stream(pdf, cm);
        if (b != null) { cmap(new String(b, LATIN), f); f.cmap = !f.map.isEmpty(); }
        return f;
    }

    private static final Pattern HEX = Pattern.compile("<([0-9A-Fa-f\\s]*)>");

    private static void cmap(String s, Font f) {
        int cs = s.indexOf("begincodespacerange");
        if (cs >= 0) {
            int ce = s.indexOf("endcodespacerange", cs);
            Matcher h = HEX.matcher(s.substring(cs, ce < 0 ? s.length() : ce));
            if (h.find()) { String lo = h.group(1).replaceAll("\\s", ""); f.bytes = Math.max(1, lo.length() / 2); }
        }
        int at = 0;
        while ((at = s.indexOf("beginbfchar", at)) >= 0) {
            int end = s.indexOf("endbfchar", at); if (end < 0) break;
            Matcher h = HEX.matcher(s.substring(at, end));
            while (h.find()) {
                String src = h.group(1).replaceAll("\\s", "");
                if (!h.find()) break;
                try { f.map.put(Long.parseLong(src, 16), utf16(h.group(1))); } catch (Throwable ignored) { }
            }
            at = end;
        }
        at = 0;
        while ((at = s.indexOf("beginbfrange", at)) >= 0) {
            int end = s.indexOf("endbfrange", at); if (end < 0) break;
            String part = s.substring(at + 12, end);
            Matcher line = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*(<[0-9A-Fa-f\\s]*>|\\[[^\\]]*\\])").matcher(part);
            while (line.find()) {
                try {
                    long lo = Long.parseLong(line.group(1), 16), hi = Long.parseLong(line.group(2), 16);
                    if (hi - lo > 65535) continue;
                    String dst = line.group(3);
                    if (dst.startsWith("[")) {
                        Matcher h = HEX.matcher(dst); long code = lo;
                        while (h.find() && code <= hi) f.map.put(code++, utf16(h.group(1)));
                    } else {
                        String base = dst.substring(1, dst.length() - 1).replaceAll("\\s", "");
                        String first = utf16(base);
                        if (first.isEmpty()) continue;
                        for (long code = lo; code <= hi; code++) {
                            char[] ch = first.toCharArray();
                            ch[ch.length - 1] = (char) (ch[ch.length - 1] + (code - lo));
                            f.map.put(code, new String(ch));
                        }
                    }
                } catch (Throwable ignored) { }
            }
            at = end;
        }
    }

    private static String utf16(String hex) {
        hex = hex.replaceAll("\\s", "");
        if (hex.length() % 4 == 2) hex = "00" + hex;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i + 4 <= hex.length(); i += 4) b.append((char) Integer.parseInt(hex.substring(i, i + 4), 16));
        return b.toString();
    }

    private static HashMap<String, Integer> named(Pdf pdf, String res, String key) {
        HashMap<String, Integer> out = new HashMap<String, Integer>();
        if (res == null) return out;
        String dict = deref(pdf, value(res, key));
        if (dict == null) return out;
        Matcher m = Pattern.compile("/([^\\s/<>\\[\\]()]+)\\s+(\\d+)\\s+\\d+\\s+R").matcher(dict);
        while (m.find()) out.put(m.group(1), Integer.parseInt(m.group(2)));
        return out;
    }

    private static void run(Pdf pdf, byte[] c, String res, StringBuilder out, int depth) {
        if (depth > 3 || c == null) return;
        HashMap<String, Integer> fonts = named(pdf, res, "/Font");
        HashMap<String, Integer> forms = named(pdf, res, "/XObject");
        Font font = null;
        ArrayList<Object> ops = new ArrayList<Object>();
        int i = 0, n = c.length;
        while (i < n && out.length() < MAX_CHARS) {
            int b = c[i] & 0xff;
            if (b == ' ' || b == '\n' || b == '\r' || b == '\t' || b == '\f' || b == 0) { i++; continue; }
            if (b == '%') { while (i < n && c[i] != '\n' && c[i] != '\r') i++; continue; }
            if (b == '(') {
                ByteArrayOutputStream s = new ByteArrayOutputStream();
                int d = 1; i++;
                while (i < n && d > 0) {
                    int x = c[i] & 0xff;
                    if (x == '\\' && i + 1 < n) {
                        int y = c[++i] & 0xff;
                        if (y == 'n') s.write('\n'); else if (y == 'r') s.write('\r'); else if (y == 't') s.write('\t'); else if (y == 'b') s.write(8); else if (y == 'f') s.write(12);
                        else if (y >= '0' && y <= '7') { int v = y - '0'; for (int k = 0; k < 2 && i + 1 < n && c[i + 1] >= '0' && c[i + 1] <= '7'; k++) v = v * 8 + (c[++i] - '0'); s.write(v & 0xff); }
                        else if (y == '\r' || y == '\n') { if (y == '\r' && i + 1 < n && c[i + 1] == '\n') i++; }
                        else s.write(y);
                        i++; continue;
                    }
                    if (x == '(') d++;
                    else if (x == ')' && --d == 0) { i++; break; }
                    s.write(x); i++;
                }
                ops.add(s.toByteArray()); continue;
            }
            if (b == '<' && i + 1 < n && c[i + 1] == '<') { int d = 0; while (i + 1 < n) { if (c[i] == '<' && c[i + 1] == '<') { d++; i += 2; continue; } if (c[i] == '>' && c[i + 1] == '>') { d--; i += 2; if (d == 0) break; continue; } i++; } ops.add("<<>>"); continue; }
            if (b == '<') {
                int j = i + 1; StringBuilder hex = new StringBuilder();
                while (j < n && c[j] != '>') { char ch = (char) c[j]; if (Character.digit(ch, 16) >= 0) hex.append(ch); j++; }
                if (hex.length() % 2 == 1) hex.append('0');
                byte[] v = new byte[hex.length() / 2];
                for (int k = 0; k < v.length; k++) v[k] = (byte) Integer.parseInt(hex.substring(k * 2, k * 2 + 2), 16);
                ops.add(v); i = j + 1; continue;
            }
            if (b == '[') { ops.add("["); i++; continue; }
            if (b == ']') {
                ArrayList<Object> arr = new ArrayList<Object>();
                int k = ops.size() - 1;
                while (k >= 0 && !"[".equals(ops.get(k))) k--;
                if (k >= 0) { arr.addAll(ops.subList(k + 1, ops.size())); while (ops.size() > k) ops.remove(ops.size() - 1); }
                ops.add(arr); i++; continue;
            }
            if (b == '/') { int j = i + 1; while (j < n && !delim(c[j])) j++; ops.add("/" + new String(c, i + 1, j - i - 1, LATIN)); i = j; continue; }
            int j = i; while (j < n && !delim(c[j])) j++;
            if (j == i) { i++; continue; }
            String w = new String(c, i, j - i, LATIN);
            i = j;
            char first = w.charAt(0);
            if (Character.isDigit(first) || first == '-' || first == '+' || first == '.') { try { ops.add(Double.parseDouble(w)); } catch (Throwable t) { ops.add(0d); } continue; }
            if ("BI".equals(w)) { int end = indexOf(c, "EI", i); i = end < 0 ? n : end + 2; ops.clear(); continue; }
            if ("Tf".equals(w)) { String name = nameAt(ops, 2); Integer fid = name == null ? null : fonts.get(name); font = fid == null ? null : font(pdf, fid); }
            else if ("Tj".equals(w)) { text(out, font, last(ops)); }
            else if ("'".equals(w) || "\"".equals(w)) { newline(out); text(out, font, last(ops)); }
            else if ("TJ".equals(w)) {
                Object a = ops.isEmpty() ? null : ops.get(ops.size() - 1);
                if (a instanceof List) for (Object x : (List<?>) a) {
                    if (x instanceof byte[]) text(out, font, x);
                    else if (x instanceof Double && (Double) x < -180) space(out);
                }
            }
            else if ("Td".equals(w) || "TD".equals(w)) { Object y = ops.size() >= 1 ? ops.get(ops.size() - 1) : null; if (y instanceof Double && Math.abs((Double) y) > 0.5) newline(out); else space(out); }
            else if ("T*".equals(w) || "Tm".equals(w)) newline(out);
            else if ("ET".equals(w)) space(out);
            else if ("Do".equals(w)) {
                String name = nameAt(ops, 1); Integer fid = name == null ? null : forms.get(name);
                Obj x = fid == null ? null : pdf.objs.get(fid);
                if (x != null && x.dict != null && x.dict.contains("/Form")) {
                    String r = deref(pdf, value(x.dict, "/Resources"));
                    run(pdf, stream(pdf, x), r == null ? res : r, out, depth + 1);
                }
            }
            ops.clear();
        }
    }

    private static boolean delim(byte b) {
        int x = b & 0xff;
        return x == ' ' || x == '\n' || x == '\r' || x == '\t' || x == '\f' || x == 0 || x == '(' || x == ')' || x == '<' || x == '>' || x == '[' || x == ']' || x == '/' || x == '%' || x == '{' || x == '}';
    }

    private static int indexOf(byte[] c, String s, int from) {
        byte a = (byte) s.charAt(0), b = (byte) s.charAt(1);
        for (int i = from; i + 2 < c.length; i++) if (c[i] == a && c[i + 1] == b && (i + 2 >= c.length || delim(c[i + 2])) && i > 0 && delim(c[i - 1])) return i;
        return -1;
    }

    private static String nameAt(List<Object> ops, int fromEnd) {
        int k = ops.size() - fromEnd;
        if (k < 0) return null;
        Object o = ops.get(k);
        return o instanceof String && ((String) o).startsWith("/") ? ((String) o).substring(1) : null;
    }

    private static Object last(List<Object> ops) { return ops.isEmpty() ? null : ops.get(ops.size() - 1); }

    private static void space(StringBuilder out) { int n = out.length(); if (n > 0 && out.charAt(n - 1) != ' ' && out.charAt(n - 1) != '\n' && out.charAt(n - 1) != '\f') out.append(' '); }

    private static void newline(StringBuilder out) {
        int n = out.length();
        while (n > 0 && out.charAt(n - 1) == ' ') out.setLength(--n);
        if (n > 0 && out.charAt(n - 1) != '\n' && out.charAt(n - 1) != '\f') out.append('\n');
    }

    private static void text(StringBuilder out, Font f, Object o) {
        if (!(o instanceof byte[])) return;
        byte[] b = (byte[]) o;
        if (f != null && f.cmap) {
            int step = Math.max(1, Math.min(4, f.bytes));
            for (int i = 0; i + step <= b.length; i += step) {
                long code = 0;
                for (int k = 0; k < step; k++) code = (code << 8) | (b[i + k] & 0xff);
                String s = f.map.get(code);
                if (s == null && step == 2) { s = f.map.get((long) (b[i] & 0xff)); if (s != null) { out.append(s); i -= 1; continue; } }
                if (s != null) out.append(s);
            }
            return;
        }
        if (f != null && f.wide) return;
        for (byte x : b) { int v = x & 0xff; if (v >= 32 || v == 9) out.append(win(v)); }
    }

    private static char win(int v) {
        if (v < 128 || v >= 160) return (char) v;
        String map = "€\u0081‚ƒ„…†‡ˆ‰Š‹Œ\u008dŽ\u008f\u0090‘’“”•–—˜™š›œ\u009džŸ";
        return map.charAt(v - 128);
    }
}
