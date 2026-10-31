package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

final class DropThud {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DURATION = 560;
    private static int generation;

    private DropThud() {}

    static void now(String key) {
        if (key == null) return;
        View cell = find(key);
        if (cell != null && cell.isAttachedToWindow() && cell.getWidth() > 0) { generation++; play(cell); }
        else later(key, 0);
    }

    static void later(final String key, long delay) {
        if (key == null) return;
        final int run = ++generation;
        final long until = SystemClock.uptimeMillis() + delay + 1200;
        MAIN.postDelayed(new Runnable() {
            View last; int steady;
            public void run() {
                if (run != generation) return;
                View cell = find(key);
                ViewGroup list = cell != null && cell.getParent() instanceof ViewGroup ? (ViewGroup) cell.getParent() : null;
                boolean ready = cell != null && list != null && cell.isAttachedToWindow() && cell.isShown() && cell.getWidth() > 0;
                if (ready && cell == last) steady++; else steady = 0;
                last = cell;
                if (ready && steady >= 2) { play(cell); return; }
                if (SystemClock.uptimeMillis() < until) MAIN.postDelayed(this, 16);
            }
        }, delay);
    }

    private static View find(String key) {
        try {
            Activity a = LauncherFeatures.activity.get(); if (a == null) return null;
            Object view = LauncherFeatures.call(LauncherFeatures.call(a, "getAppsView"), "getActiveRecyclerView");
            if (!(view instanceof ViewGroup)) return null;
            ViewGroup list = (ViewGroup) view;
            for (int i = 0; i < list.getChildCount(); i++) {
                View c = list.getChildAt(i);
                if (c.getVisibility() == View.VISIBLE && matches(c, key)) return c;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static boolean matches(View cell, String key) {
        Object tag = cell.getTag();
        if (tag == null) return false;
        if (key.startsWith("folder:")) return DrawerFolders.isFolder(tag) && key.equals("folder:" + DrawerFolders.folderIds.get(tag));
        return !DrawerFolders.isFolder(tag) && key.equals(LauncherFeatures.identity(tag));
    }

    private static float pressScale(float t) {
        if (t < 0.14f) { float u = t / 0.14f; return 1f - 0.11f * (u * u); }
        float u = (t - 0.14f) / 0.86f;
        return 1f - 0.11f * (float) (Math.exp(-5.2 * u) * Math.cos(u * Math.PI * 2.2));
    }

    private static float nudge(float t) {
        if (t <= 0f) return 0f;
        if (t < 0.18f) { float u = t / 0.18f; return 1f - (1f - u) * (1f - u); }
        float u = (t - 0.18f) / 0.82f;
        return (float) (Math.exp(-4.6 * u) * Math.cos(u * Math.PI * 1.6));
    }

    static void play(final View landed) {
        try {
            if (landed == null || !landed.isAttachedToWindow()) return;
            float density = landed.getResources().getDisplayMetrics().density;
            View body = landed;
            if (landed instanceof ViewGroup && ((ViewGroup) landed).getChildCount() == 1) body = ((ViewGroup) landed).getChildAt(0);
            final View target = body;
            target.animate().cancel();
            target.setPivotX(target.getWidth() / 2f);
            target.setPivotY(target.getPaddingTop() + target.getWidth() * 0.36f);

            final List<View> near = new ArrayList<View>();
            final List<float[]> push = new ArrayList<float[]>();
            if (landed.getParent() instanceof ViewGroup) {
                ViewGroup list = (ViewGroup) landed.getParent();
                float cx = landed.getLeft() + landed.getWidth() / 2f, cy = landed.getTop() + landed.getHeight() / 2f;
                float reach = Math.max(landed.getWidth(), landed.getHeight()) * 1.9f, amount = 8f * density;
                for (int i = 0; i < list.getChildCount(); i++) {
                    View v = list.getChildAt(i);
                    if (v == landed || v.getVisibility() != View.VISIBLE) continue;
                    float dx = v.getLeft() + v.getWidth() / 2f - cx, dy = v.getTop() + v.getHeight() / 2f - cy;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < 1f || dist > reach) continue;
                    float k = 1f - dist / reach; k *= k;
                    v.animate().cancel();
                    near.add(v);
                    push.add(new float[]{dx / dist * amount * k, dy / dist * amount * k, dist / landed.getWidth() * 0.05f, v.getTranslationX(), v.getTranslationY()});
                }
            }
            try { Haptics.tick(landed.getContext()); } catch (Throwable ignored) { }
            final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(DURATION);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator va) {
                    float t = va.getAnimatedFraction();
                    float s = pressScale(t);
                    target.setScaleX(1f - (1f - s) * 0.85f); target.setScaleY(s);
                    for (int i = 0; i < near.size(); i++) {
                        float[] p = push.get(i);
                        float f = nudge(Math.max(0f, t - 0.07f - p[2]) / (1f - 0.07f - p[2]));
                        View v = near.get(i);
                        v.setTranslationX(p[3] + p[0] * f); v.setTranslationY(p[4] + p[1] * f);
                    }
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator x) { settle(); }
                @Override public void onAnimationCancel(android.animation.Animator x) { settle(); }
                private void settle() {
                    target.setScaleX(1f); target.setScaleY(1f);
                    for (int i = 0; i < near.size(); i++) { float[] p = push.get(i); near.get(i).setTranslationX(p[3]); near.get(i).setTranslationY(p[4]); }
                }
            });
            a.start();
        } catch (Throwable ignored) { }
    }
}
