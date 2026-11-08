package io.github.pixelsuite;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class DexPeek {
    private DexPeek() {}

    static int constBeforeCall(Context c, String className, String owner, String name, String sig) throws Exception {
        String desc = "L" + className.replace('.', '/') + ";";
        for (String apk : apks(c)) {
            ZipFile z = new ZipFile(apk);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    String n = entry.getName();
                    if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                    byte[] d = read(z, entry);
                    Dex x = new Dex(d);
                    int cls = x.findClass(desc);
                    if (cls < 0) continue;
                    int found = x.constBeforeCall(cls, owner, name, sig);
                    return found;
                }
            } finally { z.close(); }
        }
        return -1;
    }

    static int constBeforeCallNear(Context c, String literal, String owner, String name, String sig) throws Exception {
        for (String apk : apks(c)) {
            ZipFile z = new ZipFile(apk);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    String n = entry.getName();
                    if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                    Dex x = new Dex(read(z, entry));
                    int cls = x.classWithString(literal);
                    if (cls < 0) continue;
                    int found = x.constBeforeCall(cls, owner, name, sig);
                    if (found >= 0) return found;
                }
            } finally { z.close(); }
        }
        return -1;
    }

    static List<String> callsIn(Context c, String className, String method) throws Exception {
        String desc = "L" + className.replace('.', '/') + ";";
        for (String apk : apks(c)) {
            ZipFile z = new ZipFile(apk);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    String n = entry.getName();
                    if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                    Dex x = new Dex(read(z, entry));
                    int cls = x.findClass(desc);
                    if (cls < 0) continue;
                    return x.callsIn(cls, method);
                }
            } finally { z.close(); }
        }
        return null;
    }

    static List<List<String>> callsNear(Context c, String literal) throws Exception {
        for (String apk : apks(c)) {
            ZipFile z = new ZipFile(apk);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    String n = entry.getName();
                    if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                    Dex x = new Dex(read(z, entry));
                    int cls = x.classWithString(literal);
                    if (cls >= 0) return x.allCalls(cls);
                }
            } finally { z.close(); }
        }
        return null;
    }

    static java.util.Map<String, List<String>> methodCalls(Context c, String className) throws Exception {
        String desc = "L" + className.replace('.', '/') + ";";
        for (String apk : apks(c)) {
            ZipFile z = new ZipFile(apk);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    String n = entry.getName();
                    if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                    Dex x = new Dex(read(z, entry));
                    int cls = x.findClass(desc);
                    if (cls < 0) continue;
                    return x.methodCalls(cls);
                }
            } finally { z.close(); }
        }
        return null;
    }

    static String constructedWith(Context c, String ctorSuffix) throws Exception {
        String want = ".<init>" + ctorSuffix;
        for (String apk : apks(c)) {
            ZipFile z = new ZipFile(apk);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    String n = entry.getName();
                    if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                    Dex x = new Dex(read(z, entry));
                    for (int cls = 0; cls < x.classesSize; cls++) {
                        List<List<String>> all;
                        try { all = x.allCalls(cls); } catch (Throwable bad) { continue; }
                        if (all == null) continue;
                        for (List<String> m : all) for (String s : m)
                            if (s.endsWith(want) && s.startsWith("L") && s.indexOf(";.") > 0) return s.substring(1, s.indexOf(";.")).replace('/', '.');
                    }
                }
            } finally { z.close(); }
        }
        return null;
    }

    static String classNameWithString(Context c, String literal) throws Exception {
        List<String> order = new ArrayList<String>();
        if (c.getApplicationInfo().sourceDir != null) order.add(c.getApplicationInfo().sourceDir);
        for (String a : apks(c)) if (!order.contains(a)) order.add(a);
        for (String apk : order) {
            ZipFile z = new ZipFile(apk);
            try {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    String n = entry.getName();
                    if (!n.startsWith("classes") || !n.endsWith(".dex") || n.contains("/")) continue;
                    Dex x = new Dex(read(z, entry));
                    int cls = x.classWithString(literal);
                    if (cls < 0) continue;
                    String desc = x.type(x.u32(x.classesOff + cls * 32));
                    return desc.substring(1, desc.length() - 1).replace('/', '.');
                }
            } finally { z.close(); }
        }
        return null;
    }

    private static List<String> apks(Context c) {
        List<String> out = new ArrayList<String>();
        ApplicationInfo ai = c.getApplicationInfo();
        if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) if (s.contains("chrome")) out.add(s);
        if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) if (!out.contains(s)) out.add(s);
        if (ai.sourceDir != null) out.add(ai.sourceDir);
        return out;
    }

    private static byte[] read(ZipFile z, ZipEntry e) throws Exception {
        InputStream in = z.getInputStream(e);
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream((int) Math.max(e.getSize(), 1 << 16));
            byte[] b = new byte[1 << 16];
            int r;
            while ((r = in.read(b)) > 0) buf.write(b, 0, r);
            return buf.toByteArray();
        } finally { in.close(); }
    }

    static final class Dex {
        final byte[] d;
        final int stringsSize, stringsOff, typesSize, typesOff, protosOff, methodsSize, methodsOff, classesSize, classesOff;
        int pos;

        Dex(byte[] d) {
            this.d = d;
            stringsSize = u32(0x38); stringsOff = u32(0x3c);
            typesSize = u32(0x40); typesOff = u32(0x44);
            protosOff = u32(0x4c);
            methodsSize = u32(0x58); methodsOff = u32(0x5c);
            classesSize = u32(0x60); classesOff = u32(0x64);
        }

        int u16(int o) { return (d[o] & 0xff) | (d[o + 1] & 0xff) << 8; }
        int u32(int o) { return (d[o] & 0xff) | (d[o + 1] & 0xff) << 8 | (d[o + 2] & 0xff) << 16 | (d[o + 3] & 0xff) << 24; }
        int uleb() { int r = 0, s = 0, b; do { b = d[pos++] & 0xff; r |= (b & 0x7f) << s; s += 7; } while ((b & 0x80) != 0); return r; }

        String string(int i) {
            pos = u32(stringsOff + i * 4);
            int len = uleb();
            char[] out = new char[len];
            int p = pos;
            for (int k = 0; k < len; k++) {
                int a = d[p++] & 0xff;
                if (a < 0x80) out[k] = (char) a;
                else if ((a & 0xe0) == 0xc0) out[k] = (char) ((a & 0x1f) << 6 | (d[p++] & 0x3f));
                else { int b = d[p++] & 0x3f, c = d[p++] & 0x3f; out[k] = (char) ((a & 0x0f) << 12 | b << 6 | c); }
            }
            return new String(out);
        }

        String type(int i) { return string(u32(typesOff + i * 4)); }

        int findClass(String desc) {
            int lo = 0, hi = stringsSize - 1, str = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int c = string(mid).compareTo(desc);
                if (c == 0) { str = mid; break; }
                if (c < 0) lo = mid + 1; else hi = mid - 1;
            }
            if (str < 0) return -1;
            int type = -1;
            lo = 0; hi = typesSize - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int v = u32(typesOff + mid * 4);
                if (v == str) { type = mid; break; }
                if (v < str) lo = mid + 1; else hi = mid - 1;
            }
            if (type < 0) return -1;
            for (int i = 0; i < classesSize; i++) if (u32(classesOff + i * 32) == type) return i;
            return -1;
        }

        int findString(String value) {
            int lo = 0, hi = stringsSize - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int c = string(mid).compareTo(value);
                if (c == 0) return mid;
                if (c < 0) lo = mid + 1; else hi = mid - 1;
            }
            return -1;
        }

        int classWithString(String value) {
            int str = findString(value);
            if (str < 0) return -1;
            for (int cls = 0; cls < classesSize; cls++) {
                int data = u32(classesOff + cls * 32 + 24);
                if (data == 0) continue;
                pos = data;
                int sf = uleb(), inf = uleb(), dm = uleb(), vm = uleb();
                for (int k = 0; k < sf + inf; k++) { uleb(); uleb(); }
                List<Integer> codes = new ArrayList<Integer>();
                for (int k = 0; k < dm + vm; k++) { uleb(); uleb(); int code = uleb(); if (code != 0) codes.add(code); }
                for (int code : codes) if (loads(code, str)) return cls;
            }
            return -1;
        }

        private boolean loads(int code, int str) {
            int size = u32(code + 12), base = code + 16;
            int i = 0;
            while (i < size) {
                int unit = u16(base + i * 2), op = unit & 0xff, len;
                if (op == 0 && unit != 0) {
                    int kind = unit >> 8;
                    if (kind == 1) len = 4 + u16(base + i * 2 + 2) * 2;
                    else if (kind == 2) len = 2 + u16(base + i * 2 + 2) * 4;
                    else if (kind == 3) { int w = u16(base + i * 2 + 2); long n = u32(base + i * 2 + 4) & 0xffffffffL; len = (int) (4 + (n * w + 1) / 2); }
                    else len = 1;
                } else {
                    len = LEN[op];
                    if (op == 0x1a && u16(base + i * 2 + 2) == str) return true;
                    if (op == 0x1b && u32(base + i * 2 + 2) == str) return true;
                }
                i += Math.max(len, 1);
            }
            return false;
        }

        List<List<String>> allCalls(int cls) {
            List<List<String>> out = new ArrayList<List<String>>();
            int data = u32(classesOff + cls * 32 + 24);
            if (data == 0) return out;
            pos = data;
            int sf = uleb(), inf = uleb(), dm = uleb(), vm = uleb();
            for (int k = 0; k < sf + inf; k++) { uleb(); uleb(); }
            List<Integer> codes = new ArrayList<Integer>();
            for (int k = 0; k < dm + vm; k++) { uleb(); uleb(); int code = uleb(); if (code != 0) codes.add(code); }
            for (int code : codes) out.add(calls(code));
            return out;
        }

        List<String> callsIn(int cls, String method) {
            List<String> out = new ArrayList<String>();
            int data = u32(classesOff + cls * 32 + 24);
            if (data == 0) return out;
            pos = data;
            int sf = uleb(), inf = uleb(), dm = uleb(), vm = uleb();
            for (int k = 0; k < sf + inf; k++) { uleb(); uleb(); }
            int code = 0, idx = 0;
            for (int k = 0; k < dm + vm; k++) {
                if (k == dm) idx = 0;
                idx += uleb(); uleb(); int off = uleb();
                if (off != 0 && code == 0) {
                    int save = pos;
                    if (string(u32(methodsOff + idx * 8 + 4)).equals(method)) code = off;
                    pos = save;
                }
            }
            if (code == 0) return out;
            return calls(code);
        }

        java.util.LinkedHashMap<String, List<String>> methodCalls(int cls) {
            java.util.LinkedHashMap<String, List<String>> out = new java.util.LinkedHashMap<String, List<String>>();
            int data = u32(classesOff + cls * 32 + 24);
            if (data == 0) return out;
            pos = data;
            int sf = uleb(), inf = uleb(), dm = uleb(), vm = uleb();
            for (int k = 0; k < sf + inf; k++) { uleb(); uleb(); }
            int idx = 0;
            List<int[]> found = new ArrayList<int[]>();
            for (int k = 0; k < dm + vm; k++) {
                if (k == dm) idx = 0;
                idx += uleb(); uleb(); int off = uleb();
                if (off != 0) found.add(new int[] { idx, off });
            }
            for (int[] f : found) {
                String n = string(u32(methodsOff + f[0] * 8 + 4));
                if (!out.containsKey(n)) out.put(n, calls(f[1]));
            }
            return out;
        }

        List<String> calls(int code) {
            List<String> out = new ArrayList<String>();
            int size = u32(code + 12), base = code + 16;
            int i = 0;
            while (i < size) {
                int unit = u16(base + i * 2), op = unit & 0xff, len;
                if (op == 0 && unit != 0) {
                    int kind = unit >> 8;
                    if (kind == 1) len = 4 + u16(base + i * 2 + 2) * 2;
                    else if (kind == 2) len = 2 + u16(base + i * 2 + 2) * 4;
                    else if (kind == 3) { int w = u16(base + i * 2 + 2); long n = u32(base + i * 2 + 4) & 0xffffffffL; len = (int) (4 + (n * w + 1) / 2); }
                    else len = 1;
                } else {
                    len = LEN[op];
                    if ((op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78)) {
                        int m = u16(base + i * 2 + 2);
                        if (m < methodsSize) { int mo = methodsOff + m * 8; out.add(type(u16(mo)) + "." + string(u32(mo + 4)) + proto(u16(mo + 2))); }
                    }
                }
                i += Math.max(len, 1);
            }
            return out;
        }

        String proto(int p) {
            int o = protosOff + p * 12;
            StringBuilder b = new StringBuilder("(");
            int params = u32(o + 8);
            if (params != 0) { int n = u32(params); for (int k = 0; k < n; k++) b.append(type(u16(params + 4 + k * 2))); }
            return b.append(')').append(type(u32(o + 4))).toString();
        }

        int constBeforeCall(int cls, String owner, String name, String sig) {
            int data = u32(classesOff + cls * 32 + 24);
            if (data == 0) return -1;
            pos = data;
            int sf = uleb(), inf = uleb(), dm = uleb(), vm = uleb();
            for (int k = 0; k < sf + inf; k++) { uleb(); uleb(); }
            List<Integer> codes = new ArrayList<Integer>();
            for (int k = 0; k < dm + vm; k++) { uleb(); uleb(); int code = uleb(); if (code != 0) codes.add(code); }
            for (int code : codes) {
                int found = scan(code, owner, name, sig);
                if (found >= 0) return found;
            }
            return -1;
        }

        int scan(int code, String owner, String name, String sig) {
            int size = u32(code + 12), base = code + 16;
            long[] regs = new long[256];
            boolean[] known = new boolean[256];
            int i = 0;
            while (i < size) {
                int unit = u16(base + i * 2), op = unit & 0xff, len;
                if (op == 0 && unit != 0) {
                    int kind = unit >> 8;
                    if (kind == 1) len = 4 + u16(base + i * 2 + 2) * 2;
                    else if (kind == 2) len = 2 + u16(base + i * 2 + 2) * 4;
                    else if (kind == 3) { int w = u16(base + i * 2 + 2); long n = u32(base + i * 2 + 4) & 0xffffffffL; len = (int) (4 + (n * w + 1) / 2); }
                    else len = 1;
                } else {
                    len = LEN[op];
                    if (op == 0x12) { int a = (unit >> 8) & 0xf; regs[a] = (unit << 16) >> 28; known[a] = true; }
                    else if (op == 0x13) { int a = unit >> 8; regs[a] = (short) u16(base + i * 2 + 2); known[a] = true; }
                    else if (op == 0x14) { int a = unit >> 8; regs[a] = u32(base + i * 2 + 2); known[a] = true; }
                    else if (op == 0x71 || op == 0x77) {
                        int m = u16(base + i * 2 + 2);
                        if (m < methodsSize) {
                            int mo = methodsOff + m * 8;
                            if (string(u32(mo + 4)).equals(name) && type(u16(mo)).equals(owner) && proto(u16(mo + 2)).equals(sig)) {
                                int first = op == 0x71 ? u16(base + i * 2 + 4) & 0xf : u16(base + i * 2 + 4);
                                if (first < 256 && known[first]) return (int) regs[first];
                                return -1;
                            }
                        }
                    }
                }
                i += Math.max(len, 1);
            }
            return -1;
        }
    }

    private static final int[] LEN = new int[256];
    static {
        String spec =
                "00-00:1,01-01:1,02-02:2,03-03:3,04-04:1,05-05:2,06-06:3,07-07:1,08-08:2,09-09:3,0a-12:1,13-13:2,14-14:3,"
              + "15-16:2,17-17:3,18-18:5,19-1a:2,1b-1b:3,1c-1c:2,1d-1e:1,1f-20:2,21-21:1,22-23:2,24-26:3,27-28:1,29-29:2,"
              + "2a-2c:3,2d-3d:2,3e-43:1,44-6d:2,6e-72:3,73-73:1,74-78:3,79-8f:1,90-af:2,b0-cf:1,d0-e2:2,e3-f9:1,fa-fb:4,"
              + "fc-fd:3,fe-ff:2";
        for (String part : spec.split(",")) {
            String[] r = part.split("[-:]");
            for (int op = Integer.parseInt(r[0], 16); op <= Integer.parseInt(r[1], 16); op++) LEN[op] = Integer.parseInt(r[2]);
        }
    }
}
