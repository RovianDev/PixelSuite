package io.github.pixelsuite;

import android.content.Context;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;

final class GameSave {
    private static final int MAGIC = 0x50534731;
    private static final String OURS = "io.github.pixelsuite";

    private static final java.util.HashSet<String> SCREEN = new java.util.HashSet<String>(java.util.Arrays.asList(
            "night", "black", "dark", "ink", "page", "soft", "plate", "motion", "d", "topInset", "bottomInset", "best", "last", "introSize", "cardScreen", "cardAt", "home", "startAt"));

    static File file(Context c, int id) { return new File(c.getFilesDir(), "arcade-run-" + id + ".bin"); }
    static boolean has(Context c, int id) { try { return file(c, id).length() > 16; } catch (Throwable t) { return false; } }
    static void drop(Context c, int id) { try { file(c, id).delete(); } catch (Throwable ignored) { } }

    private static boolean keeps(Class<?> ty) {
        if (ty.isPrimitive() || ty == String.class || ty == RectF.class || ty == ArrayList.class) return true;
        if (ty.isArray()) return keeps(ty.getComponentType());
        return data(ty);
    }
    private static boolean data(Class<?> ty) {
        return ty.getName().startsWith(OURS) && !View.class.isAssignableFrom(ty) && ty != ShareBar.class && !ty.isInterface()
                && !android.graphics.drawable.Drawable.class.isAssignableFrom(ty) && ty.getEnclosingClass() != null;
    }
    private static ArrayList<Field> fields(Object o) {
        ArrayList<Field> out = new ArrayList<Field>();
        for (Class<?> k = o.getClass(); k != null && k.getName().startsWith(OURS); k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || !keeps(f.getType()) || SCREEN.contains(f.getName())) continue;
                try { f.setAccessible(true); out.add(f); } catch (Throwable ignored) { }
            }
        return out;
    }

    static void save(Context c, int id, View game) {
        File tmp = new File(c.getFilesDir(), "arcade-run-" + id + ".tmp");
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
            try {
                android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
                out.writeInt(MAGIC); out.writeInt(m.widthPixels); out.writeInt(m.heightPixels); out.writeLong(SystemClock.uptimeMillis());
                IdentityHashMap<Object, Integer> seen = new IdentityHashMap<Object, Integer>();
                writeFields(out, game, seen);
            } finally { out.close(); }
            File f = file(c, id);
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
        } catch (Throwable t) { try { tmp.delete(); } catch (Throwable ignored) { } }
    }

    private static void writeFields(DataOutputStream out, Object o, IdentityHashMap<Object, Integer> seen) throws Exception {
        ArrayList<Field> fs = fields(o);
        out.writeInt(fs.size());
        for (Field f : fs) { out.writeUTF(f.getName()); write(out, f.get(o), seen); }
    }

    private static void write(DataOutputStream out, Object v, IdentityHashMap<Object, Integer> seen) throws Exception {
        if (v == null) { out.writeByte(0); return; }
        if (v instanceof Integer) { out.writeByte('I'); out.writeInt((Integer) v); return; }
        if (v instanceof Long) { out.writeByte('J'); out.writeLong((Long) v); return; }
        if (v instanceof Float) { out.writeByte('F'); out.writeFloat((Float) v); return; }
        if (v instanceof Boolean) { out.writeByte('Z'); out.writeBoolean((Boolean) v); return; }
        if (v instanceof Double) { out.writeByte('D'); out.writeDouble((Double) v); return; }
        if (v instanceof Byte) { out.writeByte('B'); out.writeByte((Byte) v); return; }
        if (v instanceof Short) { out.writeByte('S'); out.writeShort((Short) v); return; }
        if (v instanceof Character) { out.writeByte('C'); out.writeChar((Character) v); return; }
        if (v instanceof String) { out.writeByte('T'); out.writeUTF((String) v); return; }
        if (v instanceof RectF) { RectF r = (RectF) v; out.writeByte('R'); out.writeFloat(r.left); out.writeFloat(r.top); out.writeFloat(r.right); out.writeFloat(r.bottom); return; }
        Integer known = seen.get(v);
        if (known != null) { out.writeByte('@'); out.writeInt(known); return; }
        if (v instanceof ArrayList) {
            ArrayList<?> list = (ArrayList<?>) v;
            seen.put(v, seen.size());
            boolean moments = !list.isEmpty() && list.get(0) instanceof float[];
            out.writeByte('L'); out.writeInt(moments ? 0 : list.size());
            if (!moments) for (Object e : list) write(out, e != null && (keeps(e.getClass()) || boxed(e)) ? e : null, seen);
            return;
        }
        Class<?> k = v.getClass();
        if (k.isArray()) {
            seen.put(v, seen.size());
            int n = Array.getLength(v); Class<?> ct = k.getComponentType();
            out.writeByte('A'); out.writeUTF(ct.getName()); out.writeInt(n);
            for (int i = 0; i < n; i++) write(out, Array.get(v, i), seen);
            return;
        }
        if (!data(k)) { out.writeByte(0); return; }
        seen.put(v, seen.size());
        out.writeByte('O'); out.writeUTF(k.getName());
        writeFields(out, v, seen);
    }
    private static boolean boxed(Object e) { return e instanceof Number || e instanceof Boolean || e instanceof Character || e instanceof String; }

    static boolean load(Context c, int id, View game) {
        File f = file(c, id);
        if (!has(c, id)) return false;
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
            try {
                android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
                if (in.readInt() != MAGIC || in.readInt() != m.widthPixels || in.readInt() != m.heightPixels) throw new IllegalStateException("another screen");
                in.readLong();
                ArrayList<Object> seen = new ArrayList<Object>();
                readFields(in, game, seen);
            } finally { in.close(); }
            for (Field d : fields(game)) if (d.getType() == boolean.class && d.getName().endsWith("Dirty")) d.setBoolean(game, true);
            return true;
        } catch (Throwable t) { drop(c, id); return false; }
    }

    private static void readFields(DataInputStream in, Object o, ArrayList<Object> seen) throws Exception {
        HashMap<String, Field> by = new HashMap<String, Field>();
        if (o != null) for (Field f : fields(o)) if (!by.containsKey(f.getName())) by.put(f.getName(), f);
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            String name = in.readUTF(); Field f = by.get(name);
            Object v = read(in, seen, f != null ? f.getType() : null);
            if (f == null) continue;
            try {
                if (v == null && f.getType().isPrimitive()) continue;
                f.set(o, v);
            } catch (Throwable ignored) { }
        }
    }

    private static Object read(DataInputStream in, ArrayList<Object> seen, Class<?> want) throws Exception {
        int tag = in.readByte();
        switch (tag) {
            case 0: return null;
            case 'I': return in.readInt();
            case 'J': return in.readLong();
            case 'F': return in.readFloat();
            case 'Z': return in.readBoolean();
            case 'D': return in.readDouble();
            case 'B': return in.readByte();
            case 'S': return in.readShort();
            case 'C': return in.readChar();
            case 'T': return in.readUTF();
            case 'R': return new RectF(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
            case '@': return seen.get(in.readInt());
            case 'L': {
                ArrayList<Object> list = new ArrayList<Object>(); seen.add(list);
                int n = in.readInt();
                for (int i = 0; i < n; i++) { Object e = read(in, seen, null); if (e != null) list.add(e); }
                return list;
            }
            case 'A': {
                String ct = in.readUTF(); int n = in.readInt();
                Class<?> k = type(ct);
                Object arr = Array.newInstance(k, n); seen.add(arr);
                for (int i = 0; i < n; i++) { Object e = read(in, seen, k); if (e != null || !k.isPrimitive()) Array.set(arr, i, e); }
                return arr;
            }
            case 'O': {
                String cn = in.readUTF();
                Object o = null;
                try { Class<?> k = Class.forName(cn); if (data(k)) o = make(k); } catch (Throwable ignored) { }
                seen.add(o);
                readFields(in, o, seen);
                return o;
            }
            default: throw new IllegalStateException("bad file");
        }
    }

    private static Class<?> type(String n) throws Exception {
        switch (n) {
            case "int": return int.class; case "long": return long.class; case "float": return float.class; case "boolean": return boolean.class;
            case "double": return double.class; case "byte": return byte.class; case "short": return short.class; case "char": return char.class;
            default: return Class.forName(n);
        }
    }

    private static Object make(Class<?> k) throws Exception {
        try { java.lang.reflect.Constructor<?> c0 = k.getDeclaredConstructor(); c0.setAccessible(true); return c0.newInstance(); } catch (NoSuchMethodException none) { }
        Class<?> u = Class.forName("sun.misc.Unsafe"); Field th = u.getDeclaredField("theUnsafe"); th.setAccessible(true);
        return u.getMethod("allocateInstance", Class.class).invoke(th.get(null), k);
    }
}
