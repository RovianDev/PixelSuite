package io.github.pixelsuite;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Paint;
import android.graphics.Shader;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

final class FullBleedEdges {
    private FullBleedEdges() {}
    private static final Map<Bitmap, Bitmap> FIXED = new WeakHashMap<Bitmap, Bitmap>();

    static void install() {
        try {
            Class<?> factory = LauncherFeatures.type("icons.FastBitmapDrawableDelegate$SimpleDelegateFactory");
            if (factory == null) return;
            Xp.hookAllMethods(factory, "newDelegate", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Object d = p.getResult();
                        if (d != null && d.getClass().getName().endsWith("FullBleedDrawableDelegate")) repair(d, p.args);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void repair(Object delegate, Object[] args) throws Throwable {
        Bitmap src = null;
        for (Object a : args) {
            if (a instanceof Bitmap) { src = (Bitmap) a; break; }
            if (a == null) continue;
            try { Object icon = LauncherFeatures.get(a, "icon"); if (icon instanceof Bitmap) { src = (Bitmap) icon; break; } } catch (Throwable ignored) { }
        }
        if (src == null) src = bitmapIn(delegate);
        if (src == null || src.isRecycled() || src.getWidth() < 4 || src.getHeight() < 4) return;
        Bitmap good = fixed(src);
        if (good == null) return;
        for (Class<?> k = delegate.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(delegate);
                if (v instanceof BitmapShader) f.set(delegate, new BitmapShader(good, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                else if (v instanceof Paint && ((Paint) v).getShader() instanceof BitmapShader) ((Paint) v).setShader(new BitmapShader(good, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            }
        }
    }

    private static Bitmap bitmapIn(Object o) throws Throwable {
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(o);
                if (v instanceof Bitmap) return (Bitmap) v;
            }
        return null;
    }

    private static Bitmap fixed(Bitmap src) {
        synchronized (FIXED) {
            if (FIXED.containsKey(src)) { Bitmap hit = FIXED.get(src); return hit == src || hit == null || hit.isRecycled() ? null : hit; }
        }
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        if (out == null) return null;
        int w = out.getWidth(), h = out.getHeight();
        if (!broken(out, w, h)) { out.recycle(); synchronized (FIXED) { FIXED.put(src, src); } return null; }
        int[] col = new int[h];
        out.getPixels(col, 0, 1, w - 2, 0, 1, h);
        out.setPixels(col, 0, 1, w - 1, 0, 1, h);
        int[] row = new int[w];
        out.getPixels(row, 0, w, 0, h - 2, w, 1);
        out.setPixels(row, 0, w, 0, h - 1, w, 1);
        out.getPixels(col, 0, 1, 1, 0, 1, h);
        out.setPixels(col, 0, 1, 0, 0, 1, h);
        out.getPixels(row, 0, w, 0, 1, w, 1);
        out.setPixels(row, 0, w, 0, 0, w, 1);
        out.setHasAlpha(src.hasAlpha());
        out.prepareToDraw();
        synchronized (FIXED) { FIXED.put(src, out); }
        return out;
    }

    private static boolean broken(Bitmap b, int w, int h) {
        return edgeOff(b, w - 1, 0, w - 2, 0, 0, 1, h) || edgeOff(b, 0, h - 1, 0, h - 2, 1, 0, w)
                || edgeOff(b, 0, 0, 1, 0, 0, 1, h) || edgeOff(b, 0, 0, 0, 1, 1, 0, w);
    }

    private static boolean edgeOff(Bitmap b, int ex, int ey, int ix, int iy, int dx, int dy, int n) {
        int off = 0, seen = 0;
        for (int i = n / 8; i < n - n / 8; i++) {
            int e = b.getPixel(ex + dx * i, ey + dy * i), in = b.getPixel(ix + dx * i, iy + dy * i);
            int d = Math.abs(((e >> 16) & 255) - ((in >> 16) & 255)) + Math.abs(((e >> 8) & 255) - ((in >> 8) & 255)) + Math.abs((e & 255) - (in & 255));
            seen++;
            if (d > 60) off++;
        }
        return seen > 0 && off * 2 > seen;
    }
}
