package io.github.pixelsuite;

import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class InkZoom {
    private InkZoom() {}

    private static WeakReference<View> ink = new WeakReference<View>(null);
    private static Class<?> actionCls;
    private static boolean noAction;

    static void update(View c) {
        try {
            View v = find(c);
            if (v == null) return;
            Matrix m = new Matrix(c.getMatrix());
            int[] at = new int[2], inkAt = new int[2];
            c.getLocationOnScreen(at); v.getLocationOnScreen(inkAt);
            m.postTranslate(at[0] - c.getTranslationX() - inkAt[0], at[1] - c.getTranslationY() - inkAt[1]);
            Field own = matrixField(v.getClass());
            if (own != null) ((Matrix) own.get(v)).set(m);
            send(v, m);
        } catch (Throwable ignored) { }
    }

    private static View find(View c) {
        View v = ink.get();
        if (v != null && v.isAttachedToWindow()) return v;
        v = search(c.getRootView());
        ink = new WeakReference<View>(v);
        if (v != null) { }
        return v;
    }

    private static View search(View v) {
        if (v instanceof FrameLayout && isInk(v.getClass())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = search(g.getChildAt(i)); if (f != null) return f; }
        }
        return null;
    }

    private static boolean isInk(Class<?> k) {
        if (k.getName().contains(".") || k.getSuperclass() != FrameLayout.class) return false;
        boolean matrix = false, set = false, map = false;
        for (Field f : k.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == Matrix.class) matrix = true;
            else if (f.getType() == java.util.Set.class) set = true;
            else if (f.getType() == java.util.Map.class) map = true;
        }
        return matrix && set && map;
    }

    private static Field matrixField(Class<?> k) {
        for (Field f : k.getDeclaredFields())
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == Matrix.class) { f.setAccessible(true); return f; }
        return null;
    }

    private static void send(View v, Matrix m) throws Exception {
        if (noAction) return;
        if (actionCls == null) {
            try {
                Class<?> k = Class.forName("dyx", false, v.getClass().getClassLoader());
                k.getConstructor(Matrix.class);
                actionCls = k;
            } catch (Throwable t) { noAction = true; return; }
        }
        for (Field f : v.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().getName().contains(".")) continue;
            f.setAccessible(true);
            Object holder = f.get(v);
            if (holder == null) continue;
            for (Field g : holder.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(g.getModifiers()) || g.getType().isPrimitive() || g.getType().getName().contains(".")) continue;
                g.setAccessible(true);
                Object renderer = g.get(holder);
                if (renderer == null) continue;
                for (Method x : renderer.getClass().getDeclaredMethods()) {
                    Class<?>[] t = x.getParameterTypes();
                    if (t.length != 1 || x.getReturnType() != void.class || Modifier.isStatic(x.getModifiers())) continue;
                    if (t[0] == Object.class || !t[0].isInterface() || !t[0].isAssignableFrom(actionCls)) continue;
                    x.setAccessible(true);
                    x.invoke(renderer, actionCls.getConstructor(Matrix.class).newInstance(new Matrix(m)));
                    return;
                }
            }
        }
    }
}
