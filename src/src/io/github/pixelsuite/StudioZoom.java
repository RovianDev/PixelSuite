package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

final class StudioZoom {
    private StudioZoom() {}

    static final float MAX = 5f;
    private static float zStart, zSpan, zTx, zTy, zMidX, zMidY, lastX, lastY;

    static View canvas() { View c = ScreenshotEditor.canvasView.get(); return c != null && c.isAttachedToWindow() ? c : null; }

    static boolean zoomed(View c) { return c != null && (c.getScaleX() != 1f || c.getTranslationX() != 0f || c.getTranslationY() != 0f); }

    static void begin(View c, float midX, float midY, float span) {
        c.animate().cancel();
        c.setPivotX(0); c.setPivotY(0);
        zStart = c.getScaleX(); zSpan = Math.max(1f, span);
        zTx = c.getTranslationX(); zTy = c.getTranslationY();
        zMidX = midX; zMidY = midY;
    }

    static void move(View c, float midX, float midY, float span) {
        float z = Math.max(0.85f, Math.min(MAX, zStart * span / zSpan));
        int[] at = new int[2]; c.getLocationOnScreen(at);
        float layoutX = at[0] - c.getTranslationX(), layoutY = at[1] - c.getTranslationY();
        float px = (zMidX - layoutX - zTx) / zStart, py = (zMidY - layoutY - zTy) / zStart;
        apply(c, z, midX - layoutX - px * z, midY - layoutY - py * z);
    }

    static void panStart(float x, float y) { lastX = x; lastY = y; }

    static void pan(View c, float x, float y) {
        float z = c.getScaleX(), w = c.getWidth(), h = c.getHeight();
        float tx = clamp(c.getTranslationX() + x - lastX, w - w * z, 0), ty = clamp(c.getTranslationY() + y - lastY, h - h * z, 0);
        lastX = x; lastY = y;
        apply(c, z, tx, ty);
    }

    static void settle(final View c) {
        if (c == null) return;
        float z = c.getScaleX();
        if (z <= 1.02f) { reset(c, true); return; }
        float w = c.getWidth(), h = c.getHeight();
        c.animate().translationX(clamp(c.getTranslationX(), w - w * z, 0)).translationY(clamp(c.getTranslationY(), h - h * z, 0))
                .setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setUpdateListener(a -> changed(c)).withEndAction(() -> changed(c)).start();
    }

    static void reset(final View c, boolean animate) {
        if (!zoomed(c)) return;
        c.setPivotX(0); c.setPivotY(0);
        if (!animate) { c.setScaleX(1f); c.setScaleY(1f); c.setTranslationX(0); c.setTranslationY(0); changed(c); return; }
        c.animate().scaleX(1f).scaleY(1f).translationX(0).translationY(0).setDuration(260)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                .setUpdateListener(a -> changed(c)).withEndAction(() -> changed(c)).start();
    }

    private static void apply(View c, float z, float tx, float ty) {
        c.setScaleX(z); c.setScaleY(z); c.setTranslationX(tx); c.setTranslationY(ty);
        changed(c);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    static void changed(View c) {
        applyNodes(c);
        InkZoom.update(c);
        DoodleOverlay.redraw();
        for (ViewParent p = c.getParent(); p instanceof View; p = p.getParent()) ((View) p).invalidate();
    }

    private static final java.util.Map<Object, float[]> cropNodes = new java.util.WeakHashMap<>();
    private static final java.util.Set<Object> loggedNodes = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());
    private static java.lang.reflect.Field recordingNode, viewNode;
    private static java.lang.reflect.Method mSetScaleX, mSetScaleY, mSetTx, mSetTy, mGetPivotX, mGetPivotY, mGetScaleX, mGetScaleY, mGetTx, mGetTy;

    static void cropLayer(android.graphics.Canvas canvas) {
        try {
            if (recordingNode == null) {
                recordingNode = Class.forName("android.graphics.RecordingCanvas").getDeclaredField("mNode"); recordingNode.setAccessible(true);
                viewNode = View.class.getDeclaredField("mRenderNode"); viewNode.setAccessible(true);
                Class<?> n = Class.forName("android.graphics.RenderNode");
                mSetScaleX = n.getMethod("setScaleX", float.class); mSetScaleY = n.getMethod("setScaleY", float.class);
                mSetTx = n.getMethod("setTranslationX", float.class); mSetTy = n.getMethod("setTranslationY", float.class);
                mGetPivotX = n.getMethod("getPivotX"); mGetPivotY = n.getMethod("getPivotY");
                mGetScaleX = n.getMethod("getScaleX"); mGetScaleY = n.getMethod("getScaleY");
                mGetTx = n.getMethod("getTranslationX"); mGetTy = n.getMethod("getTranslationY");
            }
            Object node = recordingNode.get(canvas);
            View c = canvas();
            if (node == null || c == null) return;
            boolean owned = ownedByView(c, node);
            synchronized (cropNodes) {
                if (!cropNodes.containsKey(node) && !loggedNodes.contains(node)) {
                    loggedNodes.add(node);
                }
            }
            if (owned) return;
            synchronized (cropNodes) {
                if (!cropNodes.containsKey(node))
                    cropNodes.put(node, new float[]{(Float) mGetScaleX.invoke(node), (Float) mGetScaleY.invoke(node), (Float) mGetTx.invoke(node), (Float) mGetTy.invoke(node)});
            }
            if (zoomed(c)) applyNodes(c);
        } catch (Throwable ignored) { }
    }

    private static boolean ownedByView(View c, Object node) throws Exception {
        if (viewNode.get(c) == node) return true;
        for (ViewParent p = c.getParent(); p instanceof View; p = p.getParent()) if (viewNode.get(p) == node) return true;
        return false;
    }

    private static void applyNodes(View c) {
        float z = c.getScaleX(), tx = c.getTranslationX(), ty = c.getTranslationY();
        boolean one = !zoomed(c);
        synchronized (cropNodes) {
            for (java.util.Map.Entry<Object, float[]> e : cropNodes.entrySet()) {
                Object node = e.getKey(); float[] own = e.getValue();
                try {
                    if (one) {
                        mSetScaleX.invoke(node, own[0]); mSetScaleY.invoke(node, own[1]); mSetTx.invoke(node, own[2]); mSetTy.invoke(node, own[3]);
                    } else {
                        float px = (Float) mGetPivotX.invoke(node), py = (Float) mGetPivotY.invoke(node);
                        mSetScaleX.invoke(node, z); mSetScaleY.invoke(node, z);
                        mSetTx.invoke(node, tx - px * (1f - z)); mSetTy.invoke(node, ty - py * (1f - z));
                    }
                } catch (Throwable ignored) { }
            }
        }
    }

    private static final int NONE = 0, ZOOM = 1, PAN = 2, DONE = 3, ITEM = 4, GRAB = 5, CROP = 6, PEN = 7;
    private static final Matrix toOne = new Matrix();
    private static float[] cropLast;
    private static float cropOx, cropOy;
    private static int mode;
    private static boolean penRaw;

    private static boolean itemText;

    static boolean touch(Activity a, MotionEvent e, Xp.Param p) {
        if (StudioItems.injecting) return false;
        if (DoodleOverlay.active()) {
            View dc = canvas();
            int dam = e.getActionMasked();
            if (dc == null || ScreenshotEditor.activity() != a) { mode = NONE; return false; }
            if (dam == MotionEvent.ACTION_DOWN) {
                mode = NONE;
                if (zoomed(dc) && DoodleOverlay.editorWants(e.getRawX(), e.getRawY()) && cropCorner(a, dc, e)) { mode = CROP; cropLast = null; ScreenshotEditor.sCropDragging = false; }
            }
            if (mode == CROP) {
                p.args[0] = cropStep(dc, e, dam);
                if (dam == MotionEvent.ACTION_UP || dam == MotionEvent.ACTION_CANCEL) mode = NONE;
                return false;
            }
            mode = NONE;
            return false;
        }
        View c = canvas();
        if (c == null || ScreenshotEditor.activity() != a) return false;
        int am = e.getActionMasked();
        if (am == MotionEvent.ACTION_DOWN) {
            if (StudioItems.typing(a)) { mode = ITEM; return false; }
            if (StudioItems.grabStart(a, c, e)) { mode = GRAB; return true; }
            if (StudioItems.onItem(c, e)) { mode = ITEM; itemText = TextTool.textAt(c, e); try { CropRemember.itemTapped(); } catch (Throwable ignored) { } return false; }
        }
        if (mode == ITEM && itemText && am == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2 && !StudioItems.typing(a)) {
            MotionEvent cancel = MotionEvent.obtain(e); cancel.setAction(MotionEvent.ACTION_CANCEL);
            p.args[0] = cancel;
            float ox = e.getRawX() - e.getX(), oy = e.getRawY() - e.getY();
            mode = ZOOM; begin(c, (e.getX(0) + e.getX(1)) / 2f + ox, (e.getY(0) + e.getY(1)) / 2f + oy, spacing(e));
            return false;
        }
        if (mode == ITEM) { if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) mode = NONE; return false; }
        if (am == MotionEvent.ACTION_DOWN && zoomed(c) && cropCorner(a, c, e)) { mode = CROP; cropLast = null; ScreenshotEditor.sCropDragging = false; }
        if (mode == CROP) {
            p.args[0] = cropStep(c, e, am);
            if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) mode = NONE;
            return false;
        }
        if (am == MotionEvent.ACTION_DOWN && "STROKE_TOOL".equals(TopBarKeep.currentPage())) InkZoom.update(c);
        if (am == MotionEvent.ACTION_DOWN && zoomed(c) && "STROKE_TOOL".equals(TopBarKeep.currentPage())
                && onShown(c, e.getRawX(), e.getRawY()) && oneFor(c)) {
            mode = PEN;
            penRaw = true;
        }
        if (mode == PEN) {
            if (am == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2) {
                MotionEvent cancel = MotionEvent.obtain(e); if (!penRaw) cancel.transform(toOne); cancel.setAction(MotionEvent.ACTION_CANCEL);
                p.args[0] = cancel;
                float ox = e.getRawX() - e.getX(), oy = e.getRawY() - e.getY();
                mode = ZOOM; begin(c, (e.getX(0) + e.getX(1)) / 2f + ox, (e.getY(0) + e.getY(1)) / 2f + oy, spacing(e));
                return false;
            }
            if (!penRaw) {
                MotionEvent one = MotionEvent.obtain(e);
                one.transform(toOne);
                p.args[0] = one;
            }
            if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) mode = NONE;
            return false;
        }
        if (mode == GRAB) {
            if (am == MotionEvent.ACTION_MOVE) StudioItems.grabMove(a, e);
            else if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) { StudioItems.grabEnd(a, e, am == MotionEvent.ACTION_CANCEL); mode = NONE; }
            return true;
        }
        float ox = e.getRawX() - e.getX(), oy = e.getRawY() - e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mode = NONE;
                if (zoomed(c) && onPicture(c, e.getRawX(), e.getRawY())) { mode = PAN; panStart(e.getRawX(), e.getRawY()); return true; }
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2 && mode != ZOOM) {
                    float mx = (e.getX(0) + e.getX(1)) / 2f + ox, my = (e.getY(0) + e.getY(1)) / 2f + oy;
                    if (mode == NONE && !inside(c, mx, my)) return false;
                    if (mode == NONE) {
                        MotionEvent cancel = MotionEvent.obtain(e); cancel.setAction(MotionEvent.ACTION_CANCEL);
                        p.args[0] = cancel;
                        mode = ZOOM; begin(c, mx, my, spacing(e));
                        return false;
                    }
                    mode = ZOOM; begin(c, mx, my, spacing(e));
                }
                return mode != NONE;
            case MotionEvent.ACTION_MOVE:
                if (mode == ZOOM && e.getPointerCount() >= 2) move(c, (e.getX(0) + e.getX(1)) / 2f + ox, (e.getY(0) + e.getY(1)) / 2f + oy, spacing(e));
                else if (mode == PAN) pan(c, e.getRawX(), e.getRawY());
                return mode != NONE;
            case MotionEvent.ACTION_POINTER_UP:
                if (mode == ZOOM) { mode = DONE; settle(c); }
                return mode != NONE;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mode == NONE) return false;
                if (mode == ZOOM) settle(c);
                mode = NONE;
                return true;
        }
        return mode != NONE;
    }

    private static MotionEvent cropStep(View c, MotionEvent e, int am) {
        MotionEvent one = MotionEvent.obtain(e);
        one.transform(toOne);
        if (ScreenshotEditor.sCropDragging && (am == MotionEvent.ACTION_MOVE || am == MotionEvent.ACTION_UP)) {
            float min = 8f * c.getResources().getDisplayMetrics().density;
            if (am == MotionEvent.ACTION_UP) ScreenshotEditor.setCropCorner(ScreenshotEditor.sForcedPick, one.getX() - cropOx, one.getY() - cropOy, min);
            else if (cropLast != null) ScreenshotEditor.setCropCorner(ScreenshotEditor.sForcedPick, cropLast[0] - cropOx, cropLast[1] - cropOy, min);
        }
        if (cropLast == null) cropLast = new float[2];
        cropLast[0] = one.getX(); cropLast[1] = one.getY();
        if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) { ScreenshotEditor.sForcedPick = -1; ScreenshotEditor.sCropDragging = false; cropLast = null; }
        return one;
    }

    private static boolean injCrop;
    static MotionEvent injectedCrop(Activity a, MotionEvent e) {
        View c = canvas();
        if (c == null) return e;
        int am = e.getActionMasked();
        if (am == MotionEvent.ACTION_DOWN) {
            injCrop = zoomed(c) && cropCorner(a, c, e);
            if (injCrop) { cropLast = null; ScreenshotEditor.sCropDragging = false; }
        }
        if (!injCrop) return e;
        MotionEvent one = cropStep(c, e, am);
        if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) injCrop = false;
        return one;
    }

    private static boolean cropCorner(Activity a, View c, MotionEvent e) {
        try {
            String page = TopBarKeep.currentPage();
            if (page != null && !"READY".equals(page) && !"CROP_TOOL".equals(page) && !"INITIAL".equals(page)) return false;
            RectF box = ScreenshotEditor.currentCropRect();
            if (box == null || box.isEmpty()) return false;
            float z = c.getScaleX(), tx = c.getTranslationX(), ty = c.getTranslationY();
            if (z <= 0f) return false;
            int[] at = new int[2]; c.getLocationInWindow(at);
            float lx = at[0] - tx, ly = at[1] - ty;
            float qx = (e.getX() - lx - tx) / z, qy = (e.getY() - ly - ty) / z;
            float d = c.getResources().getDisplayMetrics().density;
            float band = Math.max(36f * d, 20f * d * z) / z;
            float dl = Math.abs(qx - box.left), dr = Math.abs(qx - box.right), dt = Math.abs(qy - box.top), db = Math.abs(qy - box.bottom);
            boolean left = dl <= dr, top = dt <= db;
            if (Math.min(dl, dr) > band || Math.min(dt, db) > band) return false;
            ScreenshotEditor.sForcedPick = top ? (left ? 4 : 5) : (left ? 6 : 7);
            cropOx = lx; cropOy = ly;
            toOne.setScale(1f / z, 1f / z);
            toOne.postTranslate(lx - (lx + tx) / z, ly - (ly + ty) / z);
            return true;
        } catch (Throwable t) { return false; }
    }

    private static boolean oneFor(View c) {
        float z = c.getScaleX(), tx = c.getTranslationX(), ty = c.getTranslationY();
        if (z <= 0f) return false;
        int[] at = new int[2]; c.getLocationInWindow(at);
        float lx = at[0] - tx, ly = at[1] - ty;
        toOne.setScale(1f / z, 1f / z);
        toOne.postTranslate(lx - (lx + tx) / z, ly - (ly + ty) / z);
        return true;
    }

    private static boolean onShown(View c, float x, float y) {
        android.graphics.RectF r = DoodleOverlay.Bridge.sceneRect(c);
        if (r == null || r.isEmpty()) return false;
        android.graphics.RectF s = CropGrab.onScreen(c, r);
        if (CropKeep.keeping() && CropKeep.lastCut != null) s.union(CropGrab.onScreen(c, CropKeep.lastCut));
        if (!s.contains(x, y)) return false;
        int h = c.getResources().getDisplayMetrics().heightPixels;
        if (y < h * 0.12f) return false;
        try {
            android.app.Activity a = ScreenshotEditor.activity();
            int[] at = new int[2]; a.getWindow().getDecorView().getLocationOnScreen(at);
            if (y >= StudioToolbar.anchorTop(a) + at[1]) return false;
        } catch (Throwable ignored) { }
        return true;
    }

    private static boolean onPicture(View c, float x, float y) {
        android.graphics.RectF r = DoodleOverlay.Bridge.sceneRect(c);
        if (r == null || r.isEmpty()) return inside(c, x, y);
        int[] at = new int[2];
        if (c.getParent() instanceof View) ((View) c.getParent()).getLocationOnScreen(at);
        float lx = x - at[0] - c.getLeft(), ly = y - at[1] - c.getTop();
        return r.contains(lx, ly);
    }

    private static boolean inside(View c, float x, float y) {
        View holder = c.getParent() instanceof View ? (View) c.getParent() : c;
        int[] at = new int[2]; holder.getLocationOnScreen(at);
        return x >= at[0] && y >= at[1] && x < at[0] + holder.getWidth() && y < at[1] + holder.getHeight();
    }

    private static float spacing(MotionEvent e) { return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)); }
}
