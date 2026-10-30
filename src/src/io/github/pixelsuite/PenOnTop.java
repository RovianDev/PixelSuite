package io.github.pixelsuite;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class PenOnTop {
    private PenOnTop() {}

    private static final class Rec { Object renderer; List<Object> list; List<Object> original; boolean shapes; }
    private static final ThreadLocal<Rec> rec = new ThreadLocal<Rec>();
    private static final Map<Class<?>, Boolean> strokeClass = new java.util.HashMap<Class<?>, Boolean>();
    private static final java.util.Set<Class<?>> hooked = new java.util.HashSet<Class<?>>();
    private static Field layersField;

    @SuppressWarnings("unchecked")
    static void begin(Object renderer) throws Exception {
        Rec r = new Rec(); r.renderer = renderer;
        rec.set(r);
        if (layersField == null) {
            for (Field f : renderer.getClass().getDeclaredFields())
                if (!Modifier.isStatic(f.getModifiers()) && List.class.isAssignableFrom(f.getType())) { f.setAccessible(true); layersField = f; break; }
            if (layersField == null) return;
        }
        Object v = layersField.get(renderer);
        if (!(v instanceof List)) return;
        List<Object> list = (List<Object>) v;
        List<Object> others = new ArrayList<Object>(), strokes = new ArrayList<Object>();
        boolean seenStroke = false, needs = false;
        for (Object layer : list) {
            boolean st = strokeLayer(layer);
            if (st) { strokes.add(layer); seenStroke = true; }
            else { others.add(layer); if (seenStroke) needs = true; }
        }
        if (!needs) return;
        r.list = list; r.original = new ArrayList<Object>(list);
        list.clear(); list.addAll(others); list.addAll(strokes);
    }

    static boolean end(Object renderer) {
        Rec r = rec.get();
        rec.set(null);
        if (r == null || r.renderer != renderer) return false;
        if (r.original != null) { r.list.clear(); r.list.addAll(r.original); }
        return r.shapes;
    }

    private static boolean strokeLayer(Object layer) throws Exception {
        if (layer == null) return false;
        for (Field f : layer.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !Collection.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object items = f.get(layer);
            if (!(items instanceof Collection)) continue;
            for (Object item : (Collection<?>) items) if (item != null && isStroke(item)) return true;
        }
        return false;
    }

    private static boolean isStroke(Object item) {
        Class<?> k = item.getClass();
        Boolean known = strokeClass.get(k);
        if (known != null) return known;
        boolean st = false;
        try {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().getName().startsWith("java.") || f.getType().getName().startsWith("android.")) continue;
                f.setAccessible(true);
                Object v = f.get(item);
                if (v == null) continue;
                try { if (v.getClass().getDeclaredMethod("toString") != null && String.valueOf(v).startsWith("Stroke(brush=")) { st = true; break; } } catch (NoSuchMethodException ignored) { }
            }
        } catch (Throwable ignored) { }
        strokeClass.put(k, st);
        if (st) hookStroke(k);
        return st;
    }

    private static void hookStroke(Class<?> k) {
        if (!hooked.add(k)) return;
        for (Method m : k.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (t.length != 3 || t[0] != Canvas.class || m.getReturnType() != void.class) continue;
            Xp.hookMethod(m, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    Rec r = rec.get();
                    if (r == null || r.shapes || !(p.args[0] instanceof Canvas)) return;
                    r.shapes = true;
                    try { shapes((Canvas) p.args[0], r.renderer); } catch (Throwable ignored) { }
                }
            });
        }
    }

    private static void shapes(Canvas canvas, Object renderer) throws Exception {
        if (!DoodleOverlay.hasShapes(renderer)) return;
        List<Doodle> list = DoodleOverlay.snapshot(renderer);
        if (list.isEmpty()) return;
        int save = canvas.save();
        try {
            canvas.concat((Matrix) ScreenshotEditor.canvasMatrix.invoke(ScreenshotEditor.rendererCanvas.get(renderer)));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (Doodle d : list) d.draw(canvas, paint);
        } finally { canvas.restoreToCount(save); }
    }
}
