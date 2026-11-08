package io.github.pixelsuite;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class SuiteIcon extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int plate;
    private int ink, soft;
    private boolean tintable;
    private final float unit, gap, corner;
    private int alpha = 255;

    private SuiteIcon(int plate, int ink, int soft, float unit, float gap, float corner) {
        this.plate = plate; this.ink = ink; this.soft = soft; this.unit = unit; this.gap = gap; this.corner = corner;
    }

    static SuiteIcon app() { return new SuiteIcon(0xFF1846B8, 0xFFFFFFFF, 0xFF9EC1FF, 12f / 72f, 4f / 72f, 3.3f / 72f); }

    static SuiteIcon mono(boolean dark) {
        return dark ? new SuiteIcon(0xFFFFFFFF, 0xFF000000, 0xFF8A8A8A, .235f, .07f, .052f)
                    : new SuiteIcon(0xFF000000, 0xFFFFFFFF, 0xFF9A9A9A, .235f, .07f, .052f);
    }

    static SuiteIcon glyph(int colour) {
        SuiteIcon g = new SuiteIcon(0, 0, 0, .36f, .13f, .1f);
        g.tintable = true; g.colour(colour); return g;
    }

    static SuiteIcon live(android.content.Context c, int ink) {
        SuiteIcon m = new SuiteIcon(0, 0, 0, .235f, .07f, .052f); m.colour(ink);
        float scale = 1f;
        try { scale = android.provider.Settings.Global.getFloat(c.getContentResolver(), "animator_duration_scale", 1f); } catch (Throwable ignored) { }
        m.live = scale > 0f;
        return m;
    }
    private boolean live;
    private float spread;
    void spread(float f) { spread = Math.max(0f, f); invalidateSelf(); }
    private long hopAt;
    private static final long HOP = 760;
    void hop() { hopAt = android.os.SystemClock.uptimeMillis(); invalidateSelf(); }
    private float[] hopNow(int k) {
        if (hopAt == 0) return null;
        long t = android.os.SystemClock.uptimeMillis() - hopAt;
        if (t >= HOP + 3 * 70) { hopAt = 0; return null; }
        int at = 0; for (int i = 0; i < 4; i++) if (ROUND[i] == k) at = i;
        float u = (t - at * 70) / (float) HOP;
        if (u <= 0 || u >= 1) return new float[]{0, 0, 1, 1};
        float lift = 0, turn = 0, sx = 1, sy = 1;
        if (u < .14f) { float q = (float) Math.sin(Math.PI * u / .14f); sx = 1 + .25f * q; sy = 1 - .3f * q; }
        else if (u < .62f) { float a = (u - .14f) / .48f; lift = .8f * (float) Math.sin(Math.PI * a); turn = (k == 0 || k == 3 ? 1 : -1) * 180 * (1 - (1 - a) * (1 - a)); sy = 1 + .12f * (float) Math.sin(Math.PI * a); }
        else if (u < .78f) { float q = (float) Math.sin(Math.PI * (u - .62f) / .16f); sx = 1 + .22f * q; sy = 1 - .25f * q; turn = (k == 0 || k == 3 ? 1 : -1) * 180; }
        else { float a = (u - .78f) / .22f; float w = (float) (Math.sin(a * Math.PI * 3) * Math.exp(-4 * a)); sx = 1 - .06f * w; sy = 1 + .08f * w; turn = (k == 0 || k == 3 ? 1 : -1) * 180; }
        return new float[]{lift, turn, sx, sy};
    }
    private void square(Canvas c, int k, float l, float top, float s, float r) {
        float[] h = hopNow(k);
        if (h == null) { c.drawRoundRect(l, top, l + s, top + s, r, r, paint); return; }
        float cx = l + s / 2f, cy = top + s / 2f - h[0] * s;
        int save = c.save(); c.rotate(h[1], cx, cy); c.scale(h[2], h[3], cx, cy + s / 2f);
        c.drawRoundRect(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f, r, r, paint);
        c.restoreToCount(save);
        scheduleSelf(next, android.os.SystemClock.uptimeMillis() + 16);
    }
    float spreadNow() { return spread; }
    private long start;
    private static final long PERIOD = 6400;
    private static final int[] ROUND = {0, 1, 3, 2};
    private final Runnable next = new Runnable() { public void run() { invalidateSelf(); } };

    private void drawLive(Canvas c, float cx, float cy, float s, float g, float r) {
        long now = android.os.SystemClock.uptimeMillis();
        if (start == 0) start = now;
        long t = now - start;
        float x0 = cx - s - g / 2f, y0 = cy - s - g / 2f;
        for (int k = 0; k < 4; k++) {
            int col = k % 2, row = k / 2, at = 0;
            for (int i = 0; i < 4; i++) if (ROUND[i] == k) at = i;
            double phase = ((t + PERIOD - at * PERIOD / 4) % PERIOD) / (double) PERIOD;
            float light = .3f + .7f * (float) (.5 + .5 * Math.cos(2 * Math.PI * phase));
            int colour = col == row ? ink : soft;
            paint.setColor(colour); paint.setAlpha(Math.round(Color.alpha(colour) * alpha / 255f * light));
            float l = x0 + col * (s + g), top = y0 + row * (s + g);
            square(c, k, l, top, s, r);
        }
        unscheduleSelf(next); scheduleSelf(next, now + (hopAt != 0 ? 16 : 33));
    }

    private void colour(int c) { ink = c | 0xFF000000; soft = (Math.round(Color.alpha(c | 0xFF000000) * .45f) << 24) | (c & 0xFFFFFF); }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float d = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY();
        if (Color.alpha(plate) > 0) { paint.setColor(plate); paint.setAlpha(Math.round(Color.alpha(plate) * alpha / 255f)); c.drawCircle(cx, cy, d / 2f, paint); }
        float s = unit * d, g = gap * d * (1f + 2.6f * spread), r = corner * d, x0 = cx - s - g / 2f, y0 = cy - s - g / 2f;
        if (live) { drawLive(c, cx, cy, s, g, r); return; }
        for (int i = 0; i < 4; i++) {
            int col = i % 2, row = i / 2;
            int colour = col == row ? ink : soft;
            paint.setColor(colour); paint.setAlpha(Math.round(Color.alpha(colour) * alpha / 255f));
            float l = x0 + col * (s + g), t = y0 + row * (s + g);
            square(c, i, l, t, s, r);
        }
    }

    private static final class Color { static int alpha(int c) { return c >>> 24; } }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public void setTintList(ColorStateList tint) { if (tintable && tint != null) { colour(tint.getColorForState(getState(), tint.getDefaultColor())); invalidateSelf(); } }
    @Override public void setTint(int tint) { if (tintable) { colour(tint); invalidateSelf(); } }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return -1; }
    @Override public int getIntrinsicHeight() { return -1; }
}
