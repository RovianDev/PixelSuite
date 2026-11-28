package io.github.pixelsuite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

final class SearchClose {
    private SearchClose() {}

    static final String KEY = "pixelsuite_search_close";
    static final String[] STYLES = {"glide", "shutter", "balloons", "jelly", "pop", "wave", "swell", "classic"};
    static final String[] NAMES = {"Glide (default)", "Shutter", "Balloons", "Jelly", "Pop", "Wave", "Swell", "Classic"};
    static final int TRAVEL = 320, LAND = 900;
    static final float CUT = 0.85f;

    static String style(Context c) {
        String s = PixelSearch.string(c, KEY);
        for (String x : STYLES) if (x.equals(s)) return x;
        return STYLES[0];
    }

    static boolean classic(String style) { return "classic".equals(style); }

    static float clamp(float x) { return Math.max(0f, Math.min(1f, x)); }

    static float bez(float x) {
        x = clamp(x);
        float lo = 0, hi = 1, t = 0.5f;
        for (int i = 0; i < 24; i++) {
            t = (lo + hi) / 2f;
            float bx = 3 * (1 - t) * (1 - t) * t * 0.05f + 3 * (1 - t) * t * t * 0.1f + t * t * t;
            if (bx < x) lo = t; else hi = t;
        }
        return 3 * (1 - t) * (1 - t) * t * 0.7f + 3 * (1 - t) * t * t + t * t * t;
    }

    static float resp(double zeta, double omega, float seconds) {
        if (seconds <= 0) return 0;
        double wd = omega * Math.sqrt(1 - zeta * zeta), e = Math.exp(-zeta * omega * seconds);
        return (float) (1 - e * (Math.cos(wd * seconds) + zeta * omega / wd * Math.sin(wd * seconds)));
    }

    private static float wobble(double decay, double speed, float seconds) {
        return seconds <= 0 ? 0 : (float) (Math.exp(-decay * seconds) * Math.sin(speed * seconds));
    }

    static final float SMALL_W = 0.5f, SMALL_H = 0.8f;

    static float smallX(float halfWidth) { return halfWidth * (1 - SMALL_W); }

    static float smallY(float height) { return height * (1 - SMALL_H) / 2f; }

    private static float grow(float ms) { return resp(0.72, 15, ms / 1000f); }

    static void bar(String style, float ms, float height, float halfWidth, float[] out) {
        float t = ms / 1000f, g = grow(ms);
        out[0] = -(1 - g) * smallX(halfWidth); out[1] = -(1 - g) * smallY(height);
        if (classic(style)) { out[0] = out[1] = 0; return; }
        float late = t - 0.07f;
        if ("jelly".equals(style)) { float w = wobble(6, 15, late); out[0] += 0.19f * height * w; out[1] += -0.10f * height * w; }
        else if ("swell".equals(style)) { float w = wobble(9.5, 14, late); out[0] += 0.16f * height * w; out[1] += 0.16f * height * w; }
        else if ("balloons".equals(style)) { float w = wobble(6, 13, late); out[0] += 0.06f * height * w; out[1] += 0.06f * height * w; }
    }

    static float bulge(String style, float ms, float halfWidth, float height) {
        if (!"wave".equals(style) || ms < 0) return Float.NaN;
        float span = 2 * halfWidth + 2 * height;
        float x = -0.7f * height + span * (ms / 460f) - 0.64f * height;
        return x < height * 0.5f || x > 2 * halfWidth - height * 0.5f ? Float.NaN : x;
    }

    static void zone(String style, float ms, int order, float rel, boolean pill, float height, float halfWidth, float[] o) {
        o[0] = o[1] = 1; o[2] = o[3] = 0; o[4] = 1; o[5] = 0;
        float g = grow(ms), fit = 1 - (1 - g) * (1 - SMALL_H), slide = -(1 - g) * smallX(halfWidth) * rel;
        ms -= 70;
        float t = ms / 1000f;
        if ("glide".equals(style)) {
            float x = clamp(ms / 220f), e = 1 - (1 - x) * (1 - x) * (1 - x);
            o[0] = o[1] = e;
        } else if ("shutter".equals(style)) {
            float ti = t - 0.03f - order * 0.07f;
            if (ti <= 0) { o[4] = 0; return; }
            o[3] = -(1 - resp(0.5, 15, ti)) * 0.92f * height;
            o[5] = 1;
        } else if ("balloons".equals(style)) {
            float ti = t - 0.02f - order * 0.075f;
            if (ti <= 0) { o[4] = 0; return; }
            float e = resp(0.42, 17, ti), w = wobble(7, 22, ti);
            o[0] = e * (1 + 0.16f * w); o[1] = e * (1 - 0.16f * w);
        } else if ("jelly".equals(style)) {
            float e = resp(0.7, 16, t), w = wobble(6, 15, t);
            o[0] = e * (1 + 0.05f * w); o[1] = e * (1 - 0.2f * w);
            o[2] = rel * 0.19f * height * w;
        } else if ("pop".equals(style)) {
            float ti = t - 0.01f - Math.abs(rel) * 0.15f;
            if (ti <= 0) { o[4] = 0; return; }
            o[0] = o[1] = resp(0.62, 17, ti);
        } else if ("wave".equals(style)) {
            float span = 2 * halfWidth + 2 * height;
            float at = (rel + 1) * halfWidth + 0.36f * height - (pill ? 1.3f * height : 0);
            float ti = t - at / span * 0.46f;
            if (ti <= 0) { o[4] = 0; return; }
            o[0] = o[1] = resp(0.5, 17, ti);
        } else if ("swell".equals(style)) {
            o[0] = o[1] = resp(0.7, 15, t);
        }
        o[0] *= fit; o[1] *= fit; o[2] += slide;
        if (ms <= 0 || o[0] < 0.02f || o[1] < 0.02f) o[4] = 0;
    }

    static final class Preview extends View {
        private final String style;
        private final boolean dark;
        private final int accent;
        private final float d;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF card = new RectF(), bar = new RectF(), box = new RectF();
        private final Path clip = new Path(), shape = new Path(), extra = new Path();
        private final float[] o = new float[6], b = new float[2];
        private long start;
        private boolean playing;

        Preview(Context c, String style, boolean dark, int accent) {
            super(c);
            this.style = style; this.dark = dark; this.accent = accent;
            d = c.getResources().getDisplayMetrics().density;
        }

        void replay() { start = SystemClock.uptimeMillis() - 450; playing = true; invalidate(); }

        private static float lerp(float a, float c, float t) { return a + (c - a) * t; }

        private static int mix(int a, int c, float t) {
            t = clamp(t);
            int r = Math.round(lerp((a >> 16) & 255, (c >> 16) & 255, t)), g = Math.round(lerp((a >> 8) & 255, (c >> 8) & 255, t)), bl = Math.round(lerp(a & 255, c & 255, t));
            return 0xff000000 | (r << 16) | (g << 8) | bl;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            int stage = dark ? 0xff1c1d20 : 0xffe7e9ee, surface = dark ? 0xff303236 : 0xffffffff, shell = dark ? 0xff4b4e54 : 0xffc9cdd5;
            int pillColor = dark ? 0xff787c84 : 0xffffffff, line = dark ? 0xff5a5d63 : 0xffd9dce2;
            float round = 14 * d;
            clip.reset(); clip.addRoundRect(0, 0, w, h, round, round, Path.Direction.CW);
            int all = c.save();
            c.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(stage);
            c.drawRect(0, 0, w, h, paint);
            float bh = Math.min(18 * d, h * 0.24f);
            bar.set(w * 0.1f, h - bh - 9 * d, w * 0.9f, h - 9 * d);
            boolean old = classic(style);
            float travel = old ? 330 : TRAVEL * CUT;
            float end = 650 + travel + LAND;
            float ms = playing ? SystemClock.uptimeMillis() - start : end;
            if (ms >= end) { ms = end; playing = false; }
            if (ms < 650 + travel) {
                float u = ms < 650 ? 0 : (ms - 650) / travel;
                float e, lift = 0, in = 0;
                if (old) { float x = 1 - u; e = 1 - x * x * x; lift = (float) Math.sin(Math.PI * (1 - e)) * h * 0.07f; in = bh * 0.16f * clamp((e - 0.8f) * 5f); }
                else e = bez(u * CUT);
                float ix = old ? 0 : smallX(bar.width() / 2f), iy = old ? 0 : smallY(bh);
                card.set(lerp(0, bar.left + ix, e) + in, lerp(0, bar.top + iy, e) - lift * 0.4f + in, lerp(w, bar.right - ix, e) - in, lerp(h, bar.bottom - iy, e) - lift - in);
                float r = Math.min(card.height() / 2f, lerp(round, bh / 2f, e));
                float tint = old ? clamp((e - 0.84f) * 6f) : clamp((u * CUT - 0.3f) / 0.4f);
                paint.setColor(mix(surface, shell, tint));
                c.drawRoundRect(card, r, r, paint);
                float show = old ? 1 - clamp(u * 2.2f) : u > 0 ? 0 : 1;
                if (show > 0.01f) {
                    shape.reset(); shape.addRoundRect(card, r, r, Path.Direction.CW);
                    int inner = c.save();
                    c.clipPath(shape);
                    float k = old ? card.width() / w : 1f;
                    float fy = 9 * d, fh = 13 * d;
                    float ty = card.top;
                    c.translate(old ? card.left : card.centerX() - w / 2f, ty);
                    c.scale(k, k);
                    paint.setColor(line);
                    paint.setAlpha(Math.round(255 * show));
                    c.drawRoundRect(w * 0.1f, fy, w * 0.9f, fy + fh, fh / 2f, fh / 2f, paint);
                    float y = fy + fh + 9 * d;
                    for (int i = 0; i < 3; i++) { c.drawRoundRect(w * 0.1f, y, w * (0.72f - i * 0.14f), y + 5 * d, 3 * d, 3 * d, paint); y += 11 * d; }
                    paint.setAlpha(255);
                    c.restoreToCount(inner);
                }
            } else {
                float since = ms - 650 - travel;
                float ox = 0, oy = 0;
                if (old) { float t = since / 1000f; ox = oy = -(float) (bh * 0.16f * Math.exp(-0.45 * 19 * t) * Math.cos(17 * t)); }
                else { bar(style, since, bh, bar.width() / 2f, b); ox = b[0]; oy = b[1]; }
                box.set(bar.left - ox, bar.top - oy, bar.right + ox, bar.bottom + oy);
                float r = box.height() / 2f;
                shape.reset(); shape.addRoundRect(box, r, r, Path.Direction.CW);
                float bump = bulge(style, since, bar.width() / 2f, bh);
                if (!Float.isNaN(bump)) {
                    extra.reset();
                    extra.addOval(bar.left + bump - bh * 0.9f, bar.centerY() - bh * 0.62f, bar.left + bump + bh * 0.9f, bar.centerY() + bh * 0.62f, Path.Direction.CW);
                    shape.op(extra, Path.Op.UNION);
                }
                paint.setColor(shell);
                c.drawPath(shape, paint);
                float pad = bh * 0.14f, side = bh - 2 * pad, gap = pad;
                for (int i = 0; i < 3; i++) {
                    boolean pill = i == 1;
                    float l = i == 0 ? bar.left + pad : i == 1 ? bar.left + pad + side + gap : bar.right - pad - side;
                    float rr = i == 1 ? bar.right - pad - side - gap : l + side;
                    float cx = (l + rr) / 2f, cy = bar.centerY(), sx = 1, sy = 1, dx = 0, dy = 0;
                    boolean shown = true, cut = false;
                    if (old) {
                        float t = clamp((since - 40f) / 460f), e = 1 - (float) Math.pow(1 - t, 5);
                        shown = since > 40; sx = sy = 0.82f + 0.18f * e; dx = (bar.centerX() - cx) * (1 - e) * 0.55f;
                    } else {
                        zone(style, since, i, (cx - bar.centerX()) / (bar.width() / 2f), pill, bh, bar.width() / 2f, o);
                        sx = o[0]; sy = o[1]; dx = o[2]; dy = o[3]; shown = o[4] > 0.5f; cut = o[5] > 0.5f;
                    }
                    if (!shown) continue;
                    int z = c.save();
                    if (cut) c.clipPath(shape);
                    c.translate(dx, dy);
                    c.scale(sx, sy, cx, cy);
                    paint.setColor(pillColor);
                    c.drawRoundRect(l, bar.top + pad, rr, bar.bottom - pad, side / 2f, side / 2f, paint);
                    if (!pill) { paint.setColor(accent); c.drawCircle(cx, cy, side * 0.24f, paint); }
                    else { paint.setColor(line); c.drawCircle(l + side * 0.5f, cy, side * 0.2f, paint); }
                    c.restoreToCount(z);
                }
            }
            c.restoreToCount(all);
            if (playing && isShown()) postInvalidateOnAnimation();
        }

        @Override protected void onVisibilityChanged(View changed, int visibility) { super.onVisibilityChanged(changed, visibility); if (visibility == VISIBLE) invalidate(); }
    }
}
