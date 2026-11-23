package io.github.pixelsuite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

final class OpacitySlider extends View implements Choreographer.FrameCallback {
    interface Listener { void changed(int percent); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, rail;
    private final float d;
    private Listener listener;
    private float value = 1f, at = 1f, atV, grab, grabV;
    private boolean dragging, ticking;
    private long last;
    private int told = -1;

    OpacitySlider(Context c, int accent, int rail) {
        super(c);
        this.accent = accent; this.rail = rail; d = c.getResources().getDisplayMetrics().density;
        setFocusable(true);
    }

    void listen(Listener l) { listener = l; }
    boolean held() { return dragging; }
    int percent() { return Math.max(1, Math.min(100, Math.round(value * 100))); }

    void show(int percent, boolean glide) {
        if (dragging) return;
        float v = Math.max(1, Math.min(100, percent)) / 100f;
        told = Math.round(v * 100);
        value = v;
        if (!glide) { at = v; atV = 0; invalidate(); } else tick();
    }

    private float left() { return getPaddingLeft() + 44 * d; }
    private float right() { return getWidth() - getPaddingRight() - 24 * d; }

    private void put(float x) {
        value = Math.max(.01f, Math.min(1f, (x - left()) / Math.max(1f, right() - left())));
        tick();
    }

    private void tell() {
        int now = Math.max(1, Math.min(100, Math.round(at * 100)));
        if (now == told) return;
        told = now;
        if (listener != null) listener.changed(now);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            dragging = true;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            put(e.getX());
        } else if (a == MotionEvent.ACTION_MOVE && dragging) put(e.getX());
        else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { dragging = false; tick(); }
        return true;
    }

    private void tick() { if (ticking) return; ticking = true; last = 0; Choreographer.getInstance().postFrameCallback(this); }

    private static float[] spring(float x, float v, float to, float k, float zeta, float dt) {
        float c = 2 * zeta * (float) Math.sqrt(k);
        int steps = Math.max(1, Math.round(dt / .004f)); float h = dt / steps;
        for (int i = 0; i < steps; i++) { float a = -k * (x - to) - c * v; v += a * h; x += v * h; }
        return new float[]{x, v};
    }

    @Override public void doFrame(long nanos) {
        float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
        float grabTo = dragging ? 1 : 0;
        float[] r = spring(at, atV, value, 900, 1f, dt); at = Math.max(.01f, Math.min(1f, r[0])); atV = r[1];
        r = spring(grab, grabV, grabTo, 700, .7f, dt); grab = r[0]; grabV = r[1];
        boolean rest = Math.abs(at - value) < .0015f && Math.abs(atV) < .01f && Math.abs(grab - grabTo) < .002f && Math.abs(grabV) < .01f;
        if (rest) { at = value; atV = 0; grab = grabTo; grabV = 0; }
        if (dragging || told != Math.round(value * 100)) tell();
        invalidate();
        if (rest) { ticking = false; return; }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow(); Choreographer.getInstance().removeFrameCallback(this); ticking = false; dragging = false;
    }

    @Override protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f, left = left(), right = right(), x = left + (right - left) * at;

        float rr = 8 * d, rx = getPaddingLeft() + 12 * d;
        paint.setShadowLayer(0, 0, 0, 0);
        paint.setColor(accent); paint.setStyle(Paint.Style.FILL); paint.setAlpha(Math.round(255 * at));
        c.drawCircle(rx, cy, rr, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f * d); paint.setAlpha(255);
        c.drawCircle(rx, cy, rr, paint);

        float h = 3 * d + 1.2f * d * grab;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(rail);
        c.drawRoundRect(left, cy - h, right, cy + h, h, h, paint);
        paint.setColor(accent);
        c.drawRoundRect(left, cy - h, x, cy + h, h, h, paint);

        float r = 10 * d * (1 + .22f * grab);
        if (grab > 0.01f) { paint.setAlpha(Math.round(40 * grab)); c.drawCircle(x, cy, r + 9 * d * grab, paint); paint.setAlpha(255); }
        paint.setShadowLayer(4 * d, 0, 1.5f * d, 0x40000000);
        c.drawCircle(x, cy, r, paint);
        paint.clearShadowLayer();
    }
}
