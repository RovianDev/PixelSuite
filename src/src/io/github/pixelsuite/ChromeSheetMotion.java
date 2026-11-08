package io.github.pixelsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;

import java.util.ArrayList;
import java.util.List;

final class ChromeSheetMotion {
    private ChromeSheetMotion() {}

    static final long OPEN_MS = 420, CLOSE_MS = 260, SWAP_MS = 320;

    static boolean animating(View v) {
        return ValueAnimator.areAnimatorsEnabled() && v != null && v.isAttachedToWindow();
    }

    private static float dp(View v, float x) { return x * v.getResources().getDisplayMetrics().density; }

    static final class Sheet extends PopupWindow {
        final View card;
        final View anchor;
        final float radius;
        float reveal = 1f;
        float px = -1, py = -1, start;
        boolean closing;
        private ValueAnimator anim;

        Sheet(View card, int width, int height, View anchor, float radius) {
            super(card, width, height, true);
            this.card = card;
            this.anchor = anchor;
            this.radius = radius;
            setAnimationStyle(0);
            start = Math.min(dp(card, 48), Math.max(dp(card, 24), Math.min(anchor.getWidth(), anchor.getHeight())));
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    int w = v.getWidth(), h = v.getHeight();
                    float f = reveal;
                    if (f >= 1f || px < 0) { o.setRoundRect(0, 0, w, h, Sheet.this.radius); return; }
                    float s = Math.min(start, Math.min(w, h)), hx = Math.max(s / 2f, Math.min(w - s / 2f, px)), hy = Math.max(s / 2f, Math.min(h - s / 2f, py));
                    float l = ChromeTabTransition.lerp(hx - s / 2f, 0, f), t = ChromeTabTransition.lerp(hy - s / 2f, 0, f);
                    float r = ChromeTabTransition.lerp(hx + s / 2f, w, f), b = ChromeTabTransition.lerp(hy + s / 2f, h, f);
                    o.setRoundRect(Math.round(l), Math.round(t), Math.round(r), Math.round(b), ChromeTabTransition.lerp(s / 2f, Sheet.this.radius, f));
                }
            });
            card.setClipToOutline(true);
        }

        void open(final Runnable content) {
            if (!animating(anchor)) { if (content != null) content.run(); return; }
            reveal = 0f;
            card.setAlpha(0f);
            card.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    card.getViewTreeObserver().removeOnPreDrawListener(this);
                    pivot();
                    card.invalidateOutline();
                    if (content != null) content.run();
                    run(0f, 1f, OPEN_MS, ChromeTabTransition.EMPHASIZED, null);
                    card.animate().alpha(1f).setDuration(150).setInterpolator(ChromeTabTransition.DECELERATE).start();
                    return true;
                }
            });
        }

        private void pivot() {
            try {
                int[] a = new int[2], k = new int[2];
                anchor.getLocationOnScreen(a);
                card.getLocationOnScreen(k);
                px = a[0] + anchor.getWidth() / 2f - k[0];
                py = a[1] + anchor.getHeight() / 2f - k[1];
            } catch (Throwable t) { px = card.getWidth() / 2f; py = 0; }
        }

        private void run(float from, final float to, long ms, android.animation.TimeInterpolator curve, final Runnable end) {
            if (anim != null) anim.cancel();
            final ValueAnimator a = ValueAnimator.ofFloat(from, to);
            a.setDuration(ms);
            a.setInterpolator(curve);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { reveal = (Float) v.getAnimatedValue(); card.invalidateOutline(); } });
            a.addListener(new AnimatorListenerAdapter() {
                boolean done;
                @Override public void onAnimationEnd(Animator x) { if (done) return; done = true; reveal = to; card.invalidateOutline(); if (end != null) end.run(); }
                @Override public void onAnimationCancel(Animator x) { done = true; }
            });
            anim = a;
            a.start();
        }

        @Override public void dismiss() {
            if (closing) return;
            closing = true;
            if (!isShowing() || !animating(card) || !anchor.isAttachedToWindow()) { superDismiss(); return; }
            if (px < 0) pivot();
            if (card instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) card;
                for (int i = 0; i < g.getChildCount(); i++) { View c = g.getChildAt(i); c.animate().cancel(); c.animate().alpha(0f).setStartDelay(0).setDuration(90).setInterpolator(ChromeTabTransition.ACCELERATE).start(); }
            }
            card.animate().cancel();
            card.animate().alpha(0f).setStartDelay(CLOSE_MS - 120).setDuration(120).setInterpolator(ChromeTabTransition.ACCELERATE).start();
            run(reveal, 0f, CLOSE_MS, ChromeTabTransition.ACCELERATE, new Runnable() { public void run() { superDismiss(); } });
            card.postDelayed(new Runnable() { public void run() { superDismiss(); } }, CLOSE_MS + 400);
        }

        private boolean gone;
        private void superDismiss() {
            if (gone) return;
            gone = true;
            if (anim != null) anim.cancel();
            try { super.dismiss(); } catch (Throwable ignored) { }
        }
    }

    static void enter(ViewGroup card, boolean fromTop, View skip) {
        if (!animating(card)) return;
        float dy = dp(card, 10) * (fromTop ? -1 : 1);
        int n = 0;
        for (int i = 0; i < card.getChildCount(); i++) {
            View c = card.getChildAt(i);
            if (c == skip || c.getVisibility() != View.VISIBLE) continue;
            c.animate().cancel();
            c.setAlpha(0f);
            c.setTranslationY(dy);
            c.animate().alpha(1f).translationY(0f).setStartDelay(70 + 45L * n++).setDuration(300).setInterpolator(ChromeTabTransition.DECELERATE).start();
        }
    }

    static void popIn(View v, long delay) {
        if (!animating(v)) return;
        v.animate().cancel();
        v.setScaleX(0.45f); v.setScaleY(0.45f); v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(delay).setDuration(560).setInterpolator(ChromeTabTransition.SPRING).start();
    }

    static void swap(final ViewGroup card, final boolean forward, final Runnable build) {
        if (!animating(card) || card.getHeight() <= 0) { build.run(); return; }
        final float dx = dp(card, 28) * (forward ? 1 : -1);
        final int h0 = card.getHeight();
        final List<View> old = new ArrayList<View>();
        for (int i = 0; i < card.getChildCount(); i++) { View c = card.getChildAt(i); c.animate().cancel(); old.add(c); }
        final ValueAnimator out = ValueAnimator.ofFloat(0f, 1f);
        out.setDuration(110);
        out.setInterpolator(ChromeTabTransition.ACCELERATE);
        out.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) {
            float f = (Float) v.getAnimatedValue();
            for (View c : old) { c.setAlpha(1f - f); c.setTranslationX(-dx * 0.45f * f); }
        }});
        out.addListener(new AnimatorListenerAdapter() {
            boolean done;
            @Override public void onAnimationEnd(Animator x) {
                if (done) return; done = true;
                build.run();
                if (!card.isAttachedToWindow()) return;
                int w = card.getWidth();
                card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                final int h1 = card.getMeasuredHeight();
                final ViewGroup.LayoutParams lp = card.getLayoutParams();
                final int keep = lp == null ? ViewGroup.LayoutParams.MATCH_PARENT : lp.height;
                if (lp != null) { lp.height = h0; card.setLayoutParams(lp); }
                final List<View> now = new ArrayList<View>();
                for (int i = 0; i < card.getChildCount(); i++) { View c = card.getChildAt(i); now.add(c); c.setAlpha(0f); c.setTranslationX(dx); }
                ValueAnimator in = ValueAnimator.ofFloat(0f, 1f);
                in.setDuration(SWAP_MS);
                in.setInterpolator(null);
                in.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) {
                    float f = (Float) v.getAnimatedValue();
                    if (lp != null) { lp.height = Math.round(ChromeTabTransition.lerp(h0, h1, ChromeTabTransition.EMPHASIZED.getInterpolation(f))); card.setLayoutParams(lp); }
                    float g = ChromeTabTransition.DECELERATE.getInterpolation(f);
                    for (int i = 0; i < now.size(); i++) {
                        View c = now.get(i);
                        float k = ChromeTabTransition.clamp((f - 0.06f * i) / 0.7f);
                        c.setAlpha(k);
                        c.setTranslationX(dx * (1f - g));
                    }
                }});
                in.addListener(new AnimatorListenerAdapter() {
                    boolean fin;
                    @Override public void onAnimationEnd(Animator x) {
                        if (fin) return; fin = true;
                        if (lp != null) { lp.height = keep; card.setLayoutParams(lp); }
                        for (View c : now) { c.setAlpha(1f); c.setTranslationX(0f); }
                    }
                });
                in.start();
            }
        });
        out.start();
    }

    static void springy(final View touch, final View target) {
        touch.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        target.animate().cancel();
                        target.animate().scaleX(0.88f).scaleY(0.88f).setStartDelay(0).setDuration(120).setInterpolator(ChromeTabTransition.DECELERATE).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        target.animate().cancel();
                        target.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(520).setInterpolator(ChromeTabTransition.SPRING).start();
                        break;
                }
                return false;
            }
        });
    }
}
