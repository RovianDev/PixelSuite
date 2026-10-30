package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;

final class StudioItems {
    private StudioItems() {}

    private static Class<?> sceneCls;
    private static Method mHit, mFrameDraw;
    private static Class<?> predicateType;
    private static Object predicate;
    private static boolean frameHooked;
    private static Field frameRect, frameAlpha, frameShown;

    private static Object[] pendingFrame, frame;
    private static volatile boolean capturing;
    private static Matrix captured;

    static void install(ClassLoader cl) {
        try {
            sceneCls = StudioNames.cls("scene", cl);
            Class<?> point = null;
            for (Method m : sceneCls.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (t.length == 2 && t[0] == float.class && !t[1].isPrimitive() && m.getReturnType() == void.class) point = t[1];
            }
            if (point == null) return;
            for (Method m : sceneCls.getDeclaredMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) || t.length != 2 || t[0] != point || !t[1].isInterface()) continue;
                if (m.getReturnType().isInterface() && m.getReturnType() != void.class) { mHit = m; predicateType = t[1]; }
            }
            if (mHit != null) {
                mHit.setAccessible(true);
                predicate = java.lang.reflect.Proxy.newProxyInstance(predicateType.getClassLoader(), new Class<?>[]{predicateType}, new java.lang.reflect.InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method m, Object[] args) {
                        if (m.getName().equals("equals")) return proxy == args[0];
                        if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (m.getName().equals("toString")) return "PixelSuiteAnyItem";
                        return Boolean.TRUE;
                    }
                });
            }
            Xp.hookMethod(StudioNames.cls("view", cl).getDeclaredMethod("onDraw", Canvas.class), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    pendingFrame = null;
                    if (!frameHooked) try { findFrame((View) p.thisObject); } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) { frame = pendingFrame; }
            });
            Xp.hookMethod(Canvas.class.getDeclaredMethod("concat", Matrix.class), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (capturing && captured == null && p.args[0] != null) captured = new Matrix((Matrix) p.args[0]);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void findFrame(View canvasView) throws Exception {
        Object renderer = DoodleOverlay.Bridge.renderer(canvasView);
        if (renderer == null) return;
        for (Field f : renderer.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object layers = f.get(renderer);
            if (!(layers instanceof List)) continue;
            for (Object layer : new java.util.ArrayList<Object>((List<?>) layers)) {
                if (layer == null) continue;
                for (Field g : layer.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(g.getModifiers()) || !Collection.class.isAssignableFrom(g.getType())) continue;
                    g.setAccessible(true);
                    Object items = g.get(layer);
                    if (!(items instanceof Collection)) continue;
                    for (Object item : new java.util.ArrayList<Object>((Collection<?>) items)) if (item != null && isFrame(item.getClass())) { hookFrame(item.getClass()); return; }
                }
            }
        }
    }

    private static boolean isFrame(Class<?> k) {
        int paints = 0, rects = 0, anims = 0;
        for (Field f : k.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == Paint.class) paints++; else if (f.getType() == RectF.class) rects++; else if (f.getType() == android.animation.ValueAnimator.class) anims++;
        }
        return paints == 3 && rects == 1 && anims == 1;
    }

    private static void hookFrame(Class<?> k) throws Exception {
        frameHooked = true;
        Class<?> areaCls = StudioNames.cls("area", k.getClassLoader());
        for (Field f : k.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == RectF.class) { f.setAccessible(true); frameRect = f; }
            else if (f.getType() == float.class) { f.setAccessible(true); frameAlpha = f; }
            else if (f.getType() == boolean.class) { f.setAccessible(true); frameShown = f; }
        }
        for (Method m : k.getDeclaredMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 3 && t[0] == Canvas.class && t[1] == areaCls && m.getReturnType() == void.class) mFrameDraw = m;
        }
        if (mFrameDraw == null || frameRect == null) return;
        Xp.hookMethod(mFrameDraw, new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { captured = null; capturing = true; }
            @Override protected void afterHookedMethod(Xp.Param p) {
                capturing = false;
                Matrix u = captured; captured = null;
                try {
                    if (TextTool.ownsFrame(p.thisObject)) return;
                    if (u == null) return;
                    float alpha = frameAlpha == null ? 1f : frameAlpha.getFloat(p.thisObject);
                    if (alpha < 0.05f || (frameShown != null && !frameShown.getBoolean(p.thisObject))) return;
                    RectF r = new RectF((RectF) frameRect.get(p.thisObject));
                    grabbers((Canvas) p.args[0], r, u, alpha);
                    if (alpha > 0.5f) pendingFrame = new Object[]{r, u};
                } catch (Throwable ignored) { }
            }
        });
    }

    private static final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    static { for (Paint p : new Paint[]{mark, rim}) { p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); } }

    private static void grabbers(Canvas canvas, RectF r, Matrix u, float alpha) {
        View v = ScreenshotEditor.canvasView.get();
        if (v == null) return;
        Object scene;
        try { DoodleOverlay.Bridge.resolve(v); scene = DoodleOverlay.Bridge.viewScene.get(v); } catch (Throwable t) { return; }
        Matrix toView = CropKeep.areaToView(scene);
        if (toView == null) return;
        float d = v.getResources().getDisplayMetrics().density;
        float px = u.mapRadius(1f) * toView.mapRadius(1f) * Math.max(0.01f, v.getScaleX());
        if (px <= 0) return;
        float unit = d / px;
        mark.setColor(0xffffffff); mark.setAlpha(Math.round(255 * alpha)); mark.setStrokeWidth(4f * unit);
        rim.setColor(0xcc000000); rim.setAlpha(Math.round(204 * alpha)); rim.setStrokeWidth(7f * unit);
        float size = Math.min(26f * unit, Math.min(r.width(), r.height()) * 0.6f), out = 4f * unit;
        float l = r.left - out, t = r.top - out, rr = r.right - size + out, b = r.bottom - size + out;
        RectF[] ovals = {new RectF(rr, t, rr + size, t + size), new RectF(rr, b, rr + size, b + size), new RectF(l, b, l + size, b + size)};
        float[] start = {270f, 0f, 90f};
        int save = canvas.save();
        try {
            canvas.concat(u);
            for (int i = 0; i < 3; i++) canvas.drawArc(ovals[i], start[i], 90f, false, rim);
            for (int i = 0; i < 3; i++) canvas.drawArc(ovals[i], start[i], 90f, false, mark);
        } finally { canvas.restoreToCount(save); }
    }

    static boolean injecting;
    private static float[] anchor, dir;
    private static long downTime;
    private static float ox, oy;

    static boolean typing(Activity a) {
        try {
            View root = a.getWindow().getDecorView();
            Rect r = new Rect();
            root.getWindowVisibleDisplayFrame(r);
            return root.getHeight() - r.bottom > 150f * root.getResources().getDisplayMetrics().density;
        } catch (Throwable t) { return false; }
    }

    static Object itemAt(View c, MotionEvent e) {
        if (mHit == null || predicate == null) return null;
        try {
            DoodleOverlay.Bridge.resolve(c);
            Object scene = DoodleOverlay.Bridge.viewScene.get(c);
            float[] p = toView(c, e.getRawX(), e.getRawY());
            Object pt = CropKeep.point(p[0], p[1]);
            return scene == null || pt == null ? null : mHit.invoke(scene, pt, predicate);
        } catch (Throwable t) { return null; }
    }

    static boolean onItem(View c, MotionEvent e) {
        if (mHit == null || predicate == null) return false;
        try {
            DoodleOverlay.Bridge.resolve(c);
            Object scene = DoodleOverlay.Bridge.viewScene.get(c);
            float[] p = toView(c, e.getRawX(), e.getRawY());
            Object pt = CropKeep.point(p[0], p[1]);
            return scene != null && pt != null && mHit.invoke(scene, pt, predicate) != null;
        } catch (Throwable t) { return false; }
    }

    private static float[] toView(View c, float rawX, float rawY) {
        int[] at = new int[2];
        ViewParent parent = c.getParent();
        if (parent instanceof View) ((View) parent).getLocationOnScreen(at);
        float[] p = {rawX - at[0] - c.getLeft(), rawY - at[1] - c.getTop()};
        Matrix inv = new Matrix();
        if (c.getMatrix().invert(inv)) inv.mapPoints(p);
        return p;
    }

    private static float[] toScreen(View c, float x, float y) {
        int[] at = new int[2];
        ViewParent parent = c.getParent();
        if (parent instanceof View) ((View) parent).getLocationOnScreen(at);
        float[] p = {x, y};
        c.getMatrix().mapPoints(p);
        p[0] += at[0] + c.getLeft(); p[1] += at[1] + c.getTop();
        return p;
    }

    static boolean grabStart(Activity a, View c, MotionEvent e) {
        Object[] f = frame;
        if (f == null) return false;
        try {
            DoodleOverlay.Bridge.resolve(c);
            Matrix toView = CropKeep.areaToView(DoodleOverlay.Bridge.viewScene.get(c));
            if (toView == null) return false;
            RectF r = (RectF) f[0];
            Matrix m = new Matrix((Matrix) f[1]);
            m.postConcat(toView);
            float[] pts = {r.right, r.top, r.right, r.bottom, r.left, r.bottom, r.left, r.top};
            m.mapPoints(pts);
            float[][] s = new float[4][];
            for (int i = 0; i < 4; i++) s[i] = toScreen(c, pts[i * 2], pts[i * 2 + 1]);
            float reach = 28f * c.getResources().getDisplayMetrics().density;
            int best = -1; float bestD = reach;
            for (int i = 0; i < 3; i++) {
                float dd = (float) Math.hypot(e.getRawX() - s[i][0], e.getRawY() - s[i][1]);
                if (dd <= bestD) { bestD = dd; best = i; }
            }
            if (best < 0) return false;
            int opp = (best + 2) % 4;
            ox = e.getRawX() - e.getX(); oy = e.getRawY() - e.getY();
            anchor = new float[]{s[opp][0] - ox, s[opp][1] - oy};
            dir = new float[]{s[best][0] - s[opp][0], s[best][1] - s[opp][1]};
            if (dir[0] * dir[0] + dir[1] * dir[1] < 1f) return false;
            downTime = SystemClock.uptimeMillis();
            float[] finger = along(e.getX(), e.getY());
            send(a, MotionEvent.ACTION_DOWN, 1, finger);
            send(a, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, finger);
            return true;
        } catch (Throwable t) { return false; }
    }

    static void grabMove(Activity a, MotionEvent e) { send(a, MotionEvent.ACTION_MOVE, 2, along(e.getX(), e.getY())); }

    static void grabEnd(Activity a, MotionEvent e, boolean cancel) {
        float[] finger = along(e.getX(), e.getY());
        if (cancel) { send(a, MotionEvent.ACTION_CANCEL, 2, finger); return; }
        send(a, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, finger);
        send(a, MotionEvent.ACTION_UP, 1, finger);
    }

    private static float[] along(float x, float y) {
        float len2 = dir[0] * dir[0] + dir[1] * dir[1];
        float t = Math.max(0.1f, ((x - anchor[0]) * dir[0] + (y - anchor[1]) * dir[1]) / len2);
        return new float[]{anchor[0] + dir[0] * t, anchor[1] + dir[1] * t};
    }

    private static void send(Activity a, int action, int count, float[] finger) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            props[i] = new MotionEvent.PointerProperties(); props[i].id = i; props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].pressure = 1f; coords[i].size = 1f;
        }
        coords[0].x = anchor[0]; coords[0].y = anchor[1];
        if (count > 1) { coords[1].x = finger[0]; coords[1].y = finger[1]; }
        else { coords[0].x = anchor[0]; coords[0].y = anchor[1]; }
        MotionEvent ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, count, props, coords, 0, 0, 1f, 1f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
        injecting = true;
        try { a.getWindow().superDispatchTouchEvent(ev); } catch (Throwable ignored) { } finally { injecting = false; ev.recycle(); }
    }
}
