package io.github.pixelsuite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class CropKeep {
    private CropKeep() {}

    private static Class<?> sceneCls, areaCls, pointCls;
    private static Method mArea, mAreaMatrix, mAreaW, mAreaH, mZoomBy;
    private static Constructor<?> newPoint;
    private static Field fRect;

    private static WeakReference<Object> lastCropArea = new WeakReference<Object>(null);
    private static WeakReference<Object> keptScene = new WeakReference<Object>(null);
    private static Matrix kept;
    private static boolean applying;

    static void install(ClassLoader cl) {
        try {
            sceneCls = StudioNames.cls("scene", cl);
            areaCls = StudioNames.cls("area", cl);
            mArea = ScreenshotEditor.method(sceneCls, StudioNames.m("scene.area"));
            mAreaMatrix = ScreenshotEditor.method(areaCls, StudioNames.m("area.matrix"));
            mAreaW = ScreenshotEditor.method(areaCls, StudioNames.m("area.width"));
            for (Method m : areaCls.getDeclaredMethods())
                if (m.getParameterTypes().length == 0 && m.getReturnType() == int.class && !m.getName().equals(mAreaW.getName()) && !Modifier.isStatic(m.getModifiers())) { m.setAccessible(true); mAreaH = m; }
            fRect = DoodleOverlay.Bridge.firstRect(sceneCls);
            Method cropArea = null, setArea = null;
            java.util.List<Method> refits = new java.util.ArrayList<Method>();
            for (Method m : sceneCls.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] t = m.getParameterTypes();
                if (t.length == 1 && t[0] == RectF.class && m.getReturnType() == areaCls) cropArea = m;
                else if (t.length == 1 && t[0] == areaCls && m.getReturnType() == void.class) setArea = m;
                else if (t.length == 1 && t[0] == boolean.class && m.getReturnType() == void.class) refits.add(m);
                else if (t.length == 2 && t[0] == float.class && !t[1].isPrimitive() && m.getReturnType() == void.class) { mZoomBy = m; pointCls = t[1]; }
            }
            if (mAreaH == null || cropArea == null || setArea == null || refits.isEmpty() || mZoomBy == null) return;
            mZoomBy.setAccessible(true);
            newPoint = pointCls.getDeclaredConstructor(float.class, float.class);
            newPoint.setAccessible(true);

            Xp.hookMethod(cropArea, new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) { lastCropArea = new WeakReference<Object>(p.getResult()); }
            });
            Xp.hookMethod(setArea, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Object scene = p.thisObject, next = p.args[0];
                        if (next != null && next == lastCropArea.get()) {
                            CropRemember.applied();
                            Matrix m = mapping(scene);
                            if (m != null) { kept = m; keptScene = new WeakReference<Object>(scene); }
                            why("crop applied: kept place " + (m != null ? "taken" : "NOT taken (no mapping)"));
                        } else if (keptScene.get() == scene && !sameArea(mArea.invoke(scene), next)) {
                            why("kept place ended");
                            stop();
                        }
                    } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) { keep(p.thisObject); }
            });
            for (Field f : sceneCls.getDeclaredFields())
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == android.animation.ValueAnimator.class) { f.setAccessible(true); fSettle = f; }
            for (Method m : refits) {
                m.setAccessible(true);
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) { settled(p.thisObject); keep(p.thisObject); }
                });
            }
            Xp.hookMethod(StudioNames.cls("view", cl).getDeclaredMethod("onDraw", Canvas.class), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try { if (keeping()) SAVES.set(((Canvas) p.args[0]).save()); } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Integer save = SAVES.get(); SAVES.set(null);
                    Canvas canvas = (Canvas) p.args[0];
                    android.graphics.Rect editorClip = save == null ? null : canvas.getClipBounds();
                    try { if (save != null) canvas.restoreToCount(save); } catch (Throwable ignored) { }
                    try { around((View) p.thisObject, canvas); } catch (Throwable t) { why("failed: " + t); }
                    try { if (editorClip != null && !editorClip.isEmpty()) canvas.clipRect(editorClip); } catch (Throwable ignored) { }
                }
            });
            Xp.hookMethod(View.class.getDeclaredMethod("setClipBounds", android.graphics.Rect.class), new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (kept != null && keeping() && p.thisObject == clippedView.get() && p.args[0] != null) { savedClip = new android.graphics.Rect((android.graphics.Rect) p.args[0]); p.args[0] = null; }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static Field fSettle;

    private static void settled(Object scene) {
        if (kept == null || keptScene.get() != scene || fSettle == null) return;
        try {
            android.animation.ValueAnimator a = (android.animation.ValueAnimator) fSettle.get(scene);
            if (a == null) return;
            a.removeAllUpdateListeners();
            a.cancel();
            fSettle.set(scene, null);
        } catch (Throwable ignored) { }
    }

    static Matrix areaToView(Object scene) {
        try {
            if (mArea == null || scene == null) return null;
            int w = (Integer) mAreaW.invoke(mArea.invoke(scene));
            RectF e = (RectF) fRect.get(scene);
            if (w <= 0 || e == null || e.width() <= 0) return null;
            Matrix m = new Matrix();
            float b = e.width() / w;
            m.setScale(b, b);
            m.postTranslate(e.left, e.top);
            return m;
        } catch (Throwable t) { return null; }
    }

    static Class<?> pointClass() { return pointCls; }
    static Object point(float x, float y) { try { return newPoint.newInstance(x, y); } catch (Throwable t) { return null; } }

    private static final ThreadLocal<Integer> SAVES = new ThreadLocal<Integer>();
    static volatile RectF lastCut;
    static boolean keeping() {
        if (kept == null || suspended) return false;
        try {
            View v = ScreenshotEditor.canvasView.get();
            if (v == null || !v.isAttachedToWindow()) return false;
            DoodleOverlay.Bridge.resolve(v);
            Object scene = DoodleOverlay.Bridge.viewScene == null ? null : DoodleOverlay.Bridge.viewScene.get(v);
            if (scene != null && scene != keptScene.get()) { why("kept place ended (another picture)"); stop(); return false; }
            return scene != null;
        } catch (Throwable t) { return false; }
    }

    static void pageChanged() {
        final View v = ScreenshotEditor.canvasView.get();
        if (v == null) return;
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) v.invalidate(); else v.post(new Runnable() { @Override public void run() { v.invalidate(); } });
    }

    static void newPicture() { if (kept != null) { why("kept place ended (new picture)"); stop(); } }
    private static WeakReference<View> clippedView = new WeakReference<View>(null);
    private static android.graphics.Rect savedClip;

    private static void unclip(View v) {
        if (clippedView.get() != v) { clippedView = new WeakReference<View>(v); savedClip = null; }
        android.graphics.Rect r = v.getClipBounds();
        if (r != null) { savedClip = r; v.setClipBounds(null); why("view clip bounds " + r + " put aside"); }
    }

    private static void stop() {
        kept = null; keptScene = new WeakReference<Object>(null);
        nodeBitmap = null;
        lastCut = null;
        View v = clippedView.get();
        if (v != null && savedClip != null) { android.graphics.Rect r = savedClip; savedClip = null; clippedView = new WeakReference<View>(null); v.setClipBounds(r); }
    }

    private static Matrix mapping(Object scene) throws Exception {
        Object area = mArea.invoke(scene);
        int w = (Integer) mAreaW.invoke(area);
        RectF e = (RectF) fRect.get(scene);
        if (w <= 0 || e == null || e.width() <= 0) return null;
        Matrix m = new Matrix((Matrix) mAreaMatrix.invoke(area));
        float b = e.width() / w;
        m.postScale(b, b);
        m.postTranslate(e.left, e.top);
        return m;
    }

    private static boolean sameArea(Object a, Object b) throws Exception {
        if (a == null || b == null) return a == b;
        return mAreaW.invoke(a).equals(mAreaW.invoke(b)) && mAreaH.invoke(a).equals(mAreaH.invoke(b))
                && mAreaMatrix.invoke(a).equals(mAreaMatrix.invoke(b));
    }

    static volatile boolean suspended;
    private static Method mRefit;
    static boolean suspend() {
        Object scene = keptScene.get();
        if (kept == null || suspended || scene == null) return false;
        suspended = true;
        try {
            if (mRefit == null) { mRefit = sceneCls.getDeclaredMethod("u", boolean.class); mRefit.setAccessible(true); }
            mRefit.invoke(scene, true);
        } catch (Throwable t) { why("refit for typing: " + t); }
        View v = ScreenshotEditor.canvasView.get();
        if (v != null) v.invalidate();
        return true;
    }
    static void resume() {
        if (!suspended) return;
        suspended = false;
        Object scene = keptScene.get();
        if (scene != null) keep(scene);
        View v = ScreenshotEditor.canvasView.get();
        if (v != null) v.invalidate();
    }

    static RectF cutRect() {
        Object scene = keptScene.get();
        if (!suspended || scene == null) return null;
        try { return new RectF((RectF) fRect.get(scene)); } catch (Throwable t) { return null; }
    }

    static boolean fitCut(RectF target) {
        Object scene = keptScene.get();
        if (!suspended || scene == null || applying || target == null || target.width() <= 0 || target.height() <= 0) return false;
        applying = true;
        try {
            RectF e = new RectF((RectF) fRect.get(scene));
            if (e.width() <= 0 || e.height() <= 0) return false;
            float f = Math.min(target.width() / e.width(), target.height() / e.height());
            float w = e.width() * f, h = e.height() * f;
            RectF to = new RectF(target.centerX() - w / 2f, target.centerY() - h / 2f, target.centerX() + w / 2f, target.centerY() + h / 2f);
            if (Math.abs(e.left - to.left) < 1f && Math.abs(e.top - to.top) < 1f && Math.abs(e.width() - to.width()) < 1f) return false;
            float z = to.width() / e.width(), a = Math.abs(1f - z / 2f) > 0.05f ? 2f : 3f, k = z / a;
            float cx = e.centerX(), cy = e.centerY(), tx = to.centerX(), ty = to.centerY();
            mZoomBy.invoke(scene, a, newPoint.newInstance(cx, cy));
            mZoomBy.invoke(scene, k, newPoint.newInstance((tx - k * cx) / (1f - k), (ty - k * cy) / (1f - k)));
            View v = ScreenshotEditor.canvasView.get();
            if (v != null) v.invalidate();
            return true;
        } catch (Throwable t) { why("fit the cut for typing: " + t); return false; }
        finally { applying = false; }
    }

    private static void keep(Object scene) {
        if (applying || suspended || kept == null || keptScene.get() != scene) return;
        applying = true;
        try {
            Object area = mArea.invoke(scene);
            int w = (Integer) mAreaW.invoke(area), h = (Integer) mAreaH.invoke(area);
            Matrix inv = new Matrix();
            if (w <= 0 || h <= 0 || !((Matrix) mAreaMatrix.invoke(area)).invert(inv)) return;
            inv.postConcat(kept);
            RectF target = new RectF(0, 0, w, h);
            inv.mapRect(target);
            RectF e = new RectF((RectF) fRect.get(scene));
            if (e.width() <= 0 || target.width() <= 0) return;
            if (Math.abs(e.left - target.left) < 0.5f && Math.abs(e.top - target.top) < 0.5f && Math.abs(e.width() - target.width()) < 0.5f) return;
            float z = target.width() / e.width(), a = Math.abs(1f - z / 2f) > 0.05f ? 2f : 3f, k = z / a;
            float cx = e.centerX(), cy = e.centerY(), tx = target.centerX(), ty = target.centerY();
            mZoomBy.invoke(scene, a, newPoint.newInstance(cx, cy));
            mZoomBy.invoke(scene, k, newPoint.newInstance((tx - k * cx) / (1f - k), (ty - k * cy) / (1f - k)));
        } catch (Throwable ignored) {
        } finally { applying = false; }
    }

    private static final Paint veil = new Paint();
    static { veil.setColor(0x4d000000); }
    private static Object node;
    private static Bitmap nodeBitmap;
    private static float nodeRadius;
    private static Method BEGIN, END, POSITION, SET_EFFECT, CREATE_BLUR, DRAW_NODE;
    private static Field rendererPart, partBitmap;

    private static void around(View v, Canvas canvas) throws Exception {
        if ("CROP_TOOL".equals(TopBarKeep.currentPage())) return;
        if (kept == null || !keeping()) { if (DoodleOverlay.Bridge.viewScene != null) why("no kept place (crop not applied by a tool)"); return; }
        if (!canvas.isHardwareAccelerated()) { why("canvas not hardware accelerated"); return; }
        Object scene = DoodleOverlay.Bridge.viewScene == null ? null : DoodleOverlay.Bridge.viewScene.get(v);
        if (scene == null) { DoodleOverlay.Bridge.resolve(v); scene = DoodleOverlay.Bridge.viewScene.get(v); }
        if (scene != keptScene.get()) { why("another scene than the kept one"); return; }
        unclip(v);
        Bitmap raw = rawPicture(v);
        Matrix m = mapping(scene);
        if (raw == null || raw.isRecycled() || m == null) { why("raw=" + (raw == null ? "null" : raw.isRecycled() ? "recycled" : raw.getWidth() + "x" + raw.getHeight()) + " mapping=" + (m != null)); return; }
        RectF e = (RectF) fRect.get(scene);
        float d = v.getResources().getDisplayMetrics().density;
        RectF cut = new RectF(e);
        if (!cut.intersect(0, 0, v.getWidth(), v.getHeight())) { why("picture off the view " + e); return; }
        Path hole = new Path();
        hole.addRoundRect(cut, 16f * d, 16f * d, Path.Direction.CW);
        float scale = m.mapRadius(1f);
        if (!record(raw, 14f * d / Math.max(scale, 0.01f))) { why("blur not recorded"); return; }
        why("drawn: raw " + raw.getWidth() + "x" + raw.getHeight() + " cut " + cut + " view " + v.getWidth() + "x" + v.getHeight() + " clip " + canvas.getClipBounds());
        int save = canvas.save();
        try {
            canvas.clipPath(hole, android.graphics.Region.Op.DIFFERENCE);
            RectF whole = new RectF(0, 0, raw.getWidth(), raw.getHeight());
            m.mapRect(whole);
            Path outline = new Path();
            outline.addRoundRect(whole, 16f * d, 16f * d, Path.Direction.CW);
            canvas.clipPath(outline);
            canvas.concat(m);
            canvas.clipRect(0, 0, raw.getWidth(), raw.getHeight());
            DRAW_NODE.invoke(canvas, node);
            canvas.drawRect(0, 0, raw.getWidth(), raw.getHeight(), veil);
        } finally { canvas.restoreToCount(save); }
        CropLook.marks(canvas, cut, CropLook.lastMarkColour);
        lastCut = new RectF(cut);
    }

    private static final Object whys = new Object();
    private static String last;
    private static void why(String w) {
        String k = w.length() > 40 ? w.substring(0, 40) : w;
        synchronized (whys) { if (last != null && last.equals(k)) return; last = k; }
    }

    private static Bitmap rawPicture(View v) throws Exception {
        Object renderer = DoodleOverlay.Bridge.renderer(v);
        if (renderer == null) return null;
        if (rendererPart == null) {
            for (Field f : renderer.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object part = f.get(renderer);
                if (part == null) continue;
                for (Field b : part.getClass().getDeclaredFields())
                    if (!Modifier.isStatic(b.getModifiers()) && b.getType() == Bitmap.class) { b.setAccessible(true); rendererPart = f; partBitmap = b; break; }
                if (rendererPart != null) break;
            }
            if (rendererPart == null) return null;
        }
        Object part = rendererPart.get(renderer);
        if (part == null || !partBitmap.getDeclaringClass().isInstance(part)) return null;
        return (Bitmap) partBitmap.get(part);
    }

    private static boolean record(Bitmap raw, float radius) throws Exception {
        if (BEGIN == null) {
            Class<?> nt = Class.forName("android.graphics.RenderNode"), et = Class.forName("android.graphics.RenderEffect");
            BEGIN = nt.getMethod("beginRecording", int.class, int.class); END = nt.getMethod("endRecording");
            POSITION = nt.getMethod("setPosition", int.class, int.class, int.class, int.class);
            SET_EFFECT = nt.getMethod("setRenderEffect", et);
            CREATE_BLUR = et.getMethod("createBlurEffect", float.class, float.class, Shader.TileMode.class);
            DRAW_NODE = Canvas.class.getMethod("drawRenderNode", nt);
            node = nt.getConstructor(String.class).newInstance("PixelSuiteCropKeep");
        }
        boolean has = true;
        try { has = (Boolean) node.getClass().getMethod("hasDisplayList").invoke(node); } catch (Throwable ignored) { }
        if (has && nodeBitmap == raw && Math.abs(nodeRadius - radius) < 0.5f) return true;
        SET_EFFECT.invoke(node, CREATE_BLUR.invoke(null, radius, radius, Shader.TileMode.CLAMP));
        POSITION.invoke(node, 0, 0, raw.getWidth(), raw.getHeight());
        Canvas rc = (Canvas) BEGIN.invoke(node, raw.getWidth(), raw.getHeight());
        try { rc.drawBitmap(raw, 0, 0, null); } finally { END.invoke(node); }
        nodeBitmap = raw; nodeRadius = radius;
        return true;
    }
}
