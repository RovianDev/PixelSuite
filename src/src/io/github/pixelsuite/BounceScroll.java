package io.github.pixelsuite;

import android.content.Context;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.ScrollView;

final class BounceScroll extends ScrollView implements Choreographer.FrameCallback {
    private static final int IDLE = 0, GLIDE = 1, SPRING = 2;
    private static final float RATE = 0.998f;
    private static final float RUBBER = 0.55f;
    private static final float STIFFNESS = 180f;
    private static final float OMEGA = (float) Math.sqrt(STIFFNESS);

    private final int slop, minFling, maxFling;
    private final float density;
    private VelocityTracker tracker; private long trackedAt = -1; private int trackedAction = -1;
    private int pointer = -1;
    private float downY, lastY; private long downAt = -1;
    private boolean dragging, caught, ticking, internal;
    private float pos;
    private float finger;
    private float velocity, carried, edge;
    private int mode = IDLE; private long frameAt;

    BounceScroll(Context c) {
        super(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop(); minFling = vc.getScaledMinimumFlingVelocity(); maxFling = vc.getScaledMaximumFlingVelocity();
        density = c.getResources().getDisplayMetrics().density;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private int range() {
        View c = getChildCount() > 0 ? getChildAt(0) : null;
        return c == null ? 0 : Math.max(0, c.getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom()));
    }
    private float past() { int r = range(); return pos < 0 ? pos : pos > r ? pos - r : 0f; }
    private float size() { return Math.max(1f, getHeight()); }
    private float rubber(float f) { float s = size(); return Math.signum(f) * (1f - 1f / (Math.abs(f) * RUBBER / s + 1f)) * s; }
    private float unrubber(float o) { float s = size(), a = Math.min(Math.abs(o), s * 0.99f); return Math.signum(o) * (1f / (1f - a / s) - 1f) * s / RUBBER; }

    private void show() {
        int r = range();
        float inside = Math.max(0f, Math.min(r, pos));
        int y = Math.round(inside);
        if (y != getScrollY()) { internal = true; try { super.scrollTo(getScrollX(), y); } finally { internal = false; } }
        View c = getChildCount() > 0 ? getChildAt(0) : null;
        if (c != null) c.setTranslationY(y - pos);
    }

    private void moveBy(float dy) {
        if (finger != 0f) {
            float before = finger; finger -= dy;
            if (Math.signum(finger) != Math.signum(before)) {
                float rest = finger; finger = 0f; pos = before < 0 ? 0f : range(); inside(rest);
            } else pos = (before < 0 ? 0f : range()) + rubber(finger);
        } else inside(-dy);
    }

    private void inside(float d) {
        int r = range(); float to = pos + d;
        if (to < 0f) { finger = to; pos = rubber(finger); }
        else if (to > r) { finger = to - r; pos = r + rubber(finger); }
        else { finger = 0f; pos = to; }
    }

    private void track(MotionEvent e) {
        if (tracker == null) tracker = VelocityTracker.obtain();
        if (e.getEventTime() == trackedAt && e.getActionMasked() == trackedAction) return;
        trackedAt = e.getEventTime(); trackedAction = e.getActionMasked();
        if (trackedAction == MotionEvent.ACTION_DOWN) tracker.clear();
        tracker.addMovement(e);
    }

    private boolean down(MotionEvent e) {
        downAt = e.getEventTime();
        pointer = e.getPointerId(0); downY = lastY = e.getY(); dragging = false;
        caught = mode == SPRING || (mode == GLIDE && Math.abs(velocity) > 120f * density);
        carried = mode == GLIDE && Math.abs(velocity) > 300f * density ? velocity : 0f;
        stop();
        float o = past();
        finger = o == 0f ? 0f : unrubber(o);
        if (o != 0f) { dragging = true; hold(); }
        return caught || dragging;
    }

    private void hold() { ViewParent p = getParent(); if (p != null) p.requestDisallowInterceptTouchEvent(true); }

    private void pointerUp(MotionEvent e) {
        int i = e.getActionIndex();
        if (e.getPointerId(i) != pointer || e.getPointerCount() < 2) return;
        int n = i == 0 ? 1 : 0;
        pointer = e.getPointerId(n); lastY = downY = e.getY(n);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: return down(e);
            case MotionEvent.ACTION_MOVE: {
                if (dragging) return true;
                int i = e.findPointerIndex(pointer); if (i < 0) return caught;
                float y = e.getY(i);
                if (Math.abs(y - downY) > slop) { dragging = true; lastY = y; hold(); return true; }
                return caught;
            }
            case MotionEvent.ACTION_POINTER_UP: pointerUp(e); break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: finish(); break;
        }
        return dragging || caught;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: if (e.getEventTime() != downAt) down(e); return true;
            case MotionEvent.ACTION_POINTER_DOWN: { int i = e.getActionIndex(); pointer = e.getPointerId(i); lastY = downY = e.getY(i); break; }
            case MotionEvent.ACTION_POINTER_UP: pointerUp(e); break;
            case MotionEvent.ACTION_MOVE: {
                int i = e.findPointerIndex(pointer); if (i < 0) break;
                float y = e.getY(i);
                if (!dragging && Math.abs(y - downY) > slop) { dragging = true; lastY = y; hold(); }
                if (dragging) { float dy = y - lastY; lastY = y; if (dy != 0f) { moveBy(dy); show(); } }
                break;
            }
            case MotionEvent.ACTION_UP: {
                float v = 0f;
                if (dragging && tracker != null && pointer >= 0) { tracker.computeCurrentVelocity(1000, maxFling); v = -tracker.getYVelocity(pointer); }
                finish(); release(v); break;
            }
            case MotionEvent.ACTION_CANCEL: finish(); release(0f); break;
        }
        return true;
    }

    private void finish() {
        dragging = false; caught = false; pointer = -1; trackedAt = -1;
        if (tracker != null) { tracker.recycle(); tracker = null; }
    }

    private void release(float v) {
        float o = past();
        finger = 0f;
        float more = carried; carried = 0f;
        if (o != 0f) {
            int r = range(); edge = o < 0 ? 0f : r;
            boolean outward = (o < 0 && v < 0) || (o > 0 && v > 0);
            velocity = outward ? soften(v) : v;
            mode = SPRING;
        } else if (Math.abs(v) >= minFling) {
            if (more != 0f && Math.signum(more) == Math.signum(v)) v += more;
            velocity = Math.max(-maxFling, Math.min(maxFling, v));
            mode = GLIDE;
        } else { mode = IDLE; velocity = 0f; return; }
        start();
    }

    private float soften(float v) {
        float most = Math.min(size() * 0.14f, 90f * density);
        float cap = most * OMEGA * (float) Math.E;
        return cap <= 0f ? 0f : cap * (float) Math.tanh(v / cap);
    }

    private void start() { if (!ticking) { ticking = true; frameAt = 0; Choreographer.getInstance().postFrameCallback(this); } }
    private void stop() { mode = IDLE; velocity = 0f; if (ticking) { ticking = false; Choreographer.getInstance().removeFrameCallback(this); } }

    @Override public void doFrame(long nanos) {
        if (!ticking) return;
        float dt;
        if (frameAt == 0) { float hz = 60f; try { if (getDisplay() != null) hz = Math.max(30f, getDisplay().getRefreshRate()); } catch (Throwable ignored) { } dt = 1f / hz; }
        else dt = (nanos - frameAt) / 1e9f;
        frameAt = nanos;
        dt = Math.max(0f, Math.min(dt, 1f / 20f));
        int steps = Math.max(1, (int) Math.ceil(dt / 0.002f)); float h = dt / steps;
        int r = range();
        for (int i = 0; i < steps && mode != IDLE; i++) step(h, r);
        show();
        if (mode == IDLE) { ticking = false; return; }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void step(float h, int r) {
        if (mode == GLIDE) {
            velocity *= (float) Math.pow(RATE, h * 1000f);
            pos += velocity * h;
            if (pos < 0f || pos > r) { edge = pos < 0f ? 0f : r; velocity = soften(velocity); mode = SPRING; }
            else if (Math.abs(velocity) < 12f * density) { velocity = 0f; mode = IDLE; }
        } else if (mode == SPRING) {
            float o = pos - edge;
            velocity += (-STIFFNESS * o - 2f * OMEGA * velocity) * h;
            o += velocity * h; pos = edge + o;
            boolean backInside = r > 0 && (edge == 0f ? pos > 0f : pos < r);
            if (backInside) mode = GLIDE;
            else if (Math.abs(o) < 0.25f && Math.abs(velocity) < 6f * density) { pos = edge; velocity = 0f; mode = IDLE; }
        }
    }

    @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (internal) return;
        stop(); finger = 0f; pos = t;
        View c = getChildCount() > 0 ? getChildAt(0) : null; if (c != null) c.setTranslationY(0f);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mode == IDLE && !dragging) { float in = Math.max(0f, Math.min(range(), pos)); if (in != pos) { pos = in; finger = 0f; show(); } }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop(); finish(); finger = 0f; pos = Math.max(0f, Math.min(range(), pos));
        View c = getChildCount() > 0 ? getChildAt(0) : null; if (c != null) c.setTranslationY(0f);
    }
}
