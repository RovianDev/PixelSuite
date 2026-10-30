package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class StrokeFence {
    private StrokeFence() {}

    private static boolean blocked, fenced;
    private static RectF cut;

    static boolean touch(Activity a, View c, MotionEvent e) {
        int am = e.getActionMasked();
        if (am == MotionEvent.ACTION_DOWN) {
            blocked = false; fenced = false; cut = null;
            if (!CropKeep.keeping() || !"STROKE_TOOL".equals(TopBarKeep.currentPage())) return false;
            if (StudioToolbar.menuShowing() || DoodleOverlay.active()) return false;
            RectF kept = CropKeep.lastCut;
            if (kept == null) return false;
            float x = e.getRawX(), y = e.getRawY();
            int h = c.getResources().getDisplayMetrics().heightPixels;
            if (y < h * 0.12f) return false;
            try {
                int[] at = new int[2]; a.getWindow().getDecorView().getLocationOnScreen(at);
                if (y >= StudioToolbar.anchorTop(a) + at[1]) return false;
            } catch (Throwable ignored) { }
            cut = CropGrab.onScreen(c, kept);
            if (cut.contains(x, y)) { fenced = true; return false; }
            blocked = true;
            return true;
        }
        if (blocked) {
            if (am == MotionEvent.ACTION_POINTER_DOWN) { blocked = false; return false; }
            if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) blocked = false;
            return true;
        }
        if (!fenced) return false;
        if (am == MotionEvent.ACTION_POINTER_DOWN || e.getPointerCount() > 1) { fenced = false; return false; }
        if (am == MotionEvent.ACTION_MOVE || am == MotionEvent.ACTION_UP) {
            float dx = e.getRawX() - e.getX(), dy = e.getRawY() - e.getY();
            float x = Math.max(cut.left, Math.min(cut.right, e.getRawX())), y = Math.max(cut.top, Math.min(cut.bottom, e.getRawY()));
            if (x != e.getRawX() || y != e.getRawY()) e.setLocation(x - dx, y - dy);
        }
        if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL) fenced = false;
        return false;
    }
}
