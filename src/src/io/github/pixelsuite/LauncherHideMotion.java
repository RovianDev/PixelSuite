package io.github.pixelsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

final class LauncherHideMotion {
    private LauncherHideMotion() {}

    interface Apply { boolean apply(); }

    private static final long FADE_MS = 160, MOVE_MS = 460, STAGGER_MS = 18;
    private static boolean running;

    private static final class Item {
        final View v;
        final boolean widget;
        final Rect from = new Rect(), to = new Rect();
        float baseX, baseY, setX, setY, sx0, sy0, px0, py0, dx, dy, kx = 1f, ky = 1f;
        boolean pivotSet;
        long delay;
        Item(View v, boolean widget) { this.v = v; this.widget = widget; }
    }

    static void run(final Activity a, final ViewGroup hotseat, final View bar, final Apply apply, final Runnable fallback) {
        if (running) return;
        running = true;
        final ArrayList<Item> items = new ArrayList<Item>();
        try {
            ViewGroup workspace = (ViewGroup) Xp.getObjectField(a, "mWorkspace");
            int page = ((Number) Xp.callMethod(workspace, "getCurrentPage")).intValue();
            if (page >= 0 && page < workspace.getChildCount()) collect(workspace.getChildAt(page), items);
            collect(hotseat, items);
        } catch (Throwable t) { items.clear(); }
        for (Item it : items) bounds(it.v, it.from);

        final float a0 = bar.getAlpha(), sx = bar.getScaleX(), sy = bar.getScaleY();
        bar.setPivotX(bar.getWidth() / 2f);
        bar.setPivotY(bar.getHeight() / 2f);
        ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(FADE_MS);
        fade.setInterpolator(new PathInterpolator(0.3f, 0f, 0.1f, 1f));
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator x) {
                float f = (Float) x.getAnimatedValue(), s = 1f - 0.08f * f;
                bar.setAlpha(a0 * (1f - f));
                bar.setScaleX(sx * s);
                bar.setScaleY(sy * s);
            }
        });
        fade.addListener(new AnimatorListenerAdapter() {
            boolean done;
            @Override public void onAnimationEnd(Animator x) {
                if (done) return;
                done = true;
                relayout(hotseat, apply, fallback, items);
            }
        });
        fade.start();
    }

    private static void relayout(ViewGroup hotseat, Apply apply, Runnable fallback, final ArrayList<Item> items) {
        boolean ok;
        try { ok = apply.apply(); } catch (Throwable t) { ok = false; }
        if (!ok) {
            running = false;
            try { fallback.run(); } catch (Throwable ignored) { }
            return;
        }
        if (items.isEmpty()) { running = false; return; }
        final View root = hotseat.getRootView();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                try { move(items); } catch (Throwable t) { for (Item it : items) restore(it); running = false; }
                return true;
            }
        });
        root.invalidate();
    }

    private static void move(final ArrayList<Item> items) {
        final ArrayList<Item> moving = new ArrayList<Item>();
        for (Item it : items) {
            if (!it.v.isAttachedToWindow()) continue;
            bounds(it.v, it.to);
            if (it.to.width() <= 0 || it.to.height() <= 0) continue;
            it.baseX = it.setX = it.v.getTranslationX();
            it.baseY = it.setY = it.v.getTranslationY();
            if (it.widget) {
                it.sx0 = it.v.getScaleX(); it.sy0 = it.v.getScaleY();
                it.pivotSet = Build.VERSION.SDK_INT >= 28 && it.v.isPivotSet();
                it.px0 = it.v.getPivotX(); it.py0 = it.v.getPivotY();
                it.kx = it.from.width() / (float) it.to.width();
                it.ky = it.from.height() / (float) it.to.height();
                it.dx = it.from.left - it.to.left;
                it.dy = it.from.top - it.to.top;
                it.v.setPivotX(0f);
                it.v.setPivotY(0f);
            } else {
                it.dx = it.from.exactCenterX() - it.to.exactCenterX();
                it.dy = it.from.exactCenterY() - it.to.exactCenterY();
            }
            if (Math.abs(it.dx) < 0.5f && Math.abs(it.dy) < 0.5f && Math.abs(it.kx - 1f) < 0.002f && Math.abs(it.ky - 1f) < 0.002f) { restore(it); continue; }
            moving.add(it);
        }
        if (moving.isEmpty()) { running = false; return; }

        ArrayList<Item> order = new ArrayList<Item>(moving);
        Collections.sort(order, new Comparator<Item>() { public int compare(Item x, Item y) { return y.to.top - x.to.top; } });
        int rank = -1, lastTop = Integer.MAX_VALUE;
        for (Item it : order) {
            int tol = Math.max(8, it.to.height() / 3);
            if (lastTop - it.to.top > tol) { rank++; lastTop = it.to.top; }
            it.delay = Math.max(0, rank) * STAGGER_MS;
        }
        long maxDelay = 0;
        for (Item it : moving) maxDelay = Math.max(maxDelay, it.delay);

        final Map<ViewGroup, Boolean> clips = new IdentityHashMap<ViewGroup, Boolean>();
        for (Item it : moving) unclip(it.v, clips);
        for (Item it : moving) at(it, 0f);

        final long total = MOVE_MS + maxDelay;
        final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
        clock.setDuration(total);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator x) {
                float t = x.getAnimatedFraction() * total;
                for (Item it : moving) at(it, spring((t - it.delay) / MOVE_MS));
            }
        });
        clock.addListener(new AnimatorListenerAdapter() {
            boolean done;
            @Override public void onAnimationEnd(Animator x) { finish(); }
            @Override public void onAnimationCancel(Animator x) { finish(); }
            void finish() {
                if (done) return;
                done = true;
                for (Item it : moving) restore(it);
                for (Map.Entry<ViewGroup, Boolean> e : clips.entrySet()) e.getKey().setClipChildren(e.getValue());
                running = false;
            }
        });
        clock.start();
    }

    private static float spring(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        final double zeta = 0.86, w = 8.0, wd = w * Math.sqrt(1 - zeta * zeta);
        double y = 1 - Math.exp(-zeta * w * x) * (Math.cos(wd * x) + zeta / Math.sqrt(1 - zeta * zeta) * Math.sin(wd * x));
        return (float) y;
    }

    private static void at(Item it, float p) {
        View v = it.v;
        float cx = v.getTranslationX(), cy = v.getTranslationY();
        if (cx != it.setX) it.baseX = cx;
        if (cy != it.setY) it.baseY = cy;
        it.setX = it.baseX + it.dx * (1f - p);
        it.setY = it.baseY + it.dy * (1f - p);
        v.setTranslationX(it.setX);
        v.setTranslationY(it.setY);
        if (it.widget) {
            v.setScaleX(it.sx0 * (it.kx + (1f - it.kx) * p));
            v.setScaleY(it.sy0 * (it.ky + (1f - it.ky) * p));
        }
    }

    private static void restore(Item it) {
        View v = it.v;
        float cx = v.getTranslationX(), cy = v.getTranslationY();
        v.setTranslationX(cx != it.setX ? cx : it.baseX);
        v.setTranslationY(cy != it.setY ? cy : it.baseY);
        if (it.widget && it.sx0 != 0f) {
            v.setScaleX(it.sx0);
            v.setScaleY(it.sy0);
            if (!it.pivotSet && Build.VERSION.SDK_INT >= 28) v.resetPivot();
            else { v.setPivotX(it.px0); v.setPivotY(it.py0); }
        }
    }

    private static void collect(View cellLayout, ArrayList<Item> out) throws Exception {
        if (cellLayout == null) return;
        ViewGroup c = (ViewGroup) Xp.getObjectField(cellLayout, "mShortcutsAndWidgets");
        for (int i = 0; i < c.getChildCount(); i++) {
            View ch = c.getChildAt(i);
            if (ch.getVisibility() != View.VISIBLE || ch.getWidth() <= 0 || ch.getHeight() <= 0) continue;
            out.add(new Item(ch, ch instanceof android.appwidget.AppWidgetHostView || ch.getClass().getName().contains("Widget")));
        }
    }

    private static final int[] AT = new int[2];
    private static void bounds(View v, Rect r) {
        v.getLocationInWindow(AT);
        r.set(AT[0], AT[1], AT[0] + v.getWidth(), AT[1] + v.getHeight());
    }

    private static void unclip(View v, Map<ViewGroup, Boolean> clips) {
        for (android.view.ViewParent p = v.getParent(); p instanceof ViewGroup; p = p.getParent()) {
            ViewGroup g = (ViewGroup) p;
            if (!clips.containsKey(g)) { clips.put(g, g.getClipChildren()); g.setClipChildren(false); }
        }
    }
}
