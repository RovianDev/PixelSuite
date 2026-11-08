package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class CaptionDeselect {
    private CaptionDeselect() {}

    private static volatile long doneAt;
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    static void donePressed() { doneAt = SystemClock.uptimeMillis(); }

    static void pageSet(String page) {
        if (!"CAPTION".equals(page) || SystemClock.uptimeMillis() - doneAt > 800) return;
        if (!CropRemember.pendingPress) return;
        CropRemember.sawCaption = true;
        doneAt = 0;
        main.postDelayed(new Runnable() { @Override public void run() { tapAway(false); } }, CropRemember.pendingPress ? 0 : 120);
    }

    static boolean tapAway(boolean any) {
        try {
            if (!any && !"CAPTION".equals(TopBarKeep.currentPage())) return false;
            View c = ScreenshotEditor.canvasView.get();
            Activity a = ScreenshotEditor.activity();
            if (c == null || a == null || !c.isAttachedToWindow()) return false;
            int[] at = new int[2]; c.getLocationOnScreen(at);
            RectF r = DoodleOverlay.Bridge.sceneRect(c);
            if (r == null || r.isEmpty()) r = new RectF(0, 0, c.getWidth(), c.getHeight());
            int h = c.getRootView().getHeight();
            float[][] spots = {{0.1f, 0.9f}, {0.9f, 0.9f}, {0.1f, 0.1f}, {0.9f, 0.1f}, {0.5f, 0.95f}, {0.5f, 0.05f}, {0.05f, 0.5f}, {0.95f, 0.5f}};
            for (float[] s : spots) {
                float[] p = {r.left + r.width() * s[0], r.top + r.height() * s[1]};
                c.getMatrix().mapPoints(p);
                float x = at[0] + p[0], y = at[1] + p[1];
                if (y < h * 0.12f || y > h * 0.85f) continue;
                long t0 = SystemClock.uptimeMillis();
                MotionEvent probe = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0);
                boolean item;
                try { item = StudioItems.onItem(c, probe); } finally { probe.recycle(); }
                if (item) continue;
                int[] win = new int[2]; a.getWindow().getDecorView().getLocationOnScreen(win);
                MotionEvent down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x - win[0], y - win[1], 0);
                MotionEvent up = MotionEvent.obtain(t0, t0 + 30, MotionEvent.ACTION_UP, x - win[0], y - win[1], 0);
                StudioItems.injecting = true;
                try { a.getWindow().superDispatchTouchEvent(down); a.getWindow().superDispatchTouchEvent(up); }
                finally { StudioItems.injecting = false; down.recycle(); up.recycle(); }
                return true;
            }
            android.graphics.Rect seen = new android.graphics.Rect();
            if (!c.getGlobalVisibleRect(seen)) return false;
            RectF pic = new RectF(r); c.getMatrix().mapRect(pic); pic.offset(at[0] - c.getTranslationX(), at[1] - c.getTranslationY());
            float[] ys = {0.8f, 0.2f, 0.65f, 0.35f, 0.5f}, xs = {0.12f, 0.88f, 0.3f, 0.7f, 0.5f};
            for (float fy : ys) for (float fx : xs) {
                float x = seen.left + seen.width() * fx, y = seen.top + seen.height() * fy;
                if (y < h * 0.12f || y > h * 0.85f || !pic.contains(x, y)) continue;
                if (TextTool.nearSelectedFrame(x, y)) continue;
                long t0 = SystemClock.uptimeMillis();
                MotionEvent probe = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0);
                boolean item;
                try { item = StudioItems.onItem(c, probe); } finally { probe.recycle(); }
                if (item) continue;
                int[] win = new int[2]; a.getWindow().getDecorView().getLocationOnScreen(win);
                MotionEvent down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x - win[0], y - win[1], 0);
                MotionEvent up = MotionEvent.obtain(t0, t0 + 30, MotionEvent.ACTION_UP, x - win[0], y - win[1], 0);
                StudioItems.injecting = true;
                try { a.getWindow().superDispatchTouchEvent(down); a.getWindow().superDispatchTouchEvent(up); }
                finally { StudioItems.injecting = false; down.recycle(); up.recycle(); }
                return true;
            }
        } catch (Throwable t) { }
        return false;
    }
}
