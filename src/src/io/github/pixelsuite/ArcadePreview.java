package io.github.pixelsuite;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class ArcadePreview {
    private ArcadePreview() { }

    static final int STACK = 0, SIEGE = 1, BLAST = 2;
    private static final RectF R = new RectF(), Q = new RectF();
    private static final Path P = new Path();
    private static final RectF[] BASE = new RectF[4];

    static int backdrop(int game, boolean night, boolean black) {
        switch (game) {
            case STACK: return black ? 0xFF05070D : night ? 0xFF111A33 : 0xFFE6EEFF;
            case SIEGE: return black ? 0xFF060806 : night ? 0xFF121A14 : 0xFFE9F1E6;
            default: return black ? 0xFF0A0705 : night ? 0xFF1E1810 : 0xFFFFF3DE;
        }
    }
    static int accent(int game) { return game == STACK ? 0xFF4F8DF5 : game == SIEGE ? 0xFF2BB673 : 0xFFF28C28; }

    static void draw(Canvas c, int game, RectF r, long t, float radius, float d, boolean night, boolean black, float alphaF, Paint fill, Paint stroke) {
        if (alphaF <= 0 || r.width() < 2 || r.height() < 2) return;
        int save = c.save();
        P.reset(); P.addRoundRect(r, radius, radius, Path.Direction.CW); c.clipPath(P);
        fill.setShader(null); fill.setStyle(Paint.Style.FILL);
        int bg = backdrop(game, night, black);
        fill.setColor(GameKit.alpha(bg, alphaF)); c.drawRect(r, fill);
        fill.setShader(new android.graphics.RadialGradient(r.centerX(), r.bottom, Math.max(r.width(), r.height()) * .75f,
                GameKit.alpha(accent(game), alphaF * (night ? .28f : .2f)), 0, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(r, fill); fill.setShader(null);
        switch (game) {
            case STACK: stack(c, r, t, d, night, black, alphaF, fill, stroke); break;
            case SIEGE: siege(c, r, t, d, night, black, alphaF, fill, stroke); break;
            default: blast(c, r, t, d, night, black, alphaF, fill, stroke);
        }
        c.restoreToCount(save);
    }

    private static final RectF W = new RectF();
    static void window(Canvas c, int game, RectF from, RectF to, float k, float r0, float r1, long t, float d, boolean night, boolean black, float alphaF, Paint fill, Paint stroke) {
        W.set(from.left + (to.left - from.left) * k, from.top + (to.top - from.top) * k, from.right + (to.right - from.right) * k, from.bottom + (to.bottom - from.bottom) * k);
        float rad = r0 + (r1 - r0) * k;
        float travel = (float) Math.sin(Math.PI * clamp(k));
        if (travel > .01f) {
            fill.setShader(null); fill.setStyle(Paint.Style.FILL);
            fill.setColor(GameKit.alpha(0xFF000000, alphaF * .35f * travel));
            R.set(W); R.offset(0, 10 * d * travel); R.inset(-4 * d * travel, -4 * d * travel); c.drawRoundRect(R, rad + 4 * d, rad + 4 * d, fill);
        }
        draw(c, game, W, t, rad, d, night, black, alphaF, fill, stroke);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(black ? 1.5f * d : 1.2f * d);
        stroke.setColor(GameKit.alpha(accent(game), alphaF * (night ? .5f : .35f) * (.6f + .4f * travel + .4f * (1 - travel))));
        c.drawRoundRect(W, rad, rad, stroke);
    }

    static void block(Canvas c, float l, float t, float r, float b, int colour, float a, boolean black, float d, Paint fill, Paint stroke) {
        if (r - l < .5f || b - t < .5f) return;
        R.set(l, t, r, b); float rad = Math.min(r - l, b - t) * .26f;
        fill.setShader(null); fill.setColor(GameKit.alpha(colour, a)); c.drawRoundRect(R, rad, rad, fill);
        if (black) { stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, d * .8f)); stroke.setColor(GameKit.alpha(0x55FFFFFF, a)); c.drawRoundRect(R, rad, rad, stroke); }
        float h = b - t;
        R.set(l + (r - l) * .14f, t + h * .12f, r - (r - l) * .14f, t + h * .32f);
        fill.setColor(GameKit.alpha(0x40FFFFFF, a)); c.drawRoundRect(R, h * .08f, h * .08f, fill);
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }

    private static final float[] STACK_OFF = {0f, .1f, -.08f, .02f, .14f, -.05f, .0f, .09f};

    private static int stackColour(int level, boolean night) {
        float k = (level % 8) / 7f;
        int a = night ? 0xFF2C5BD6 : GameKit.BLUE, b = night ? 0xFFBFD6FF : GameKit.LIGHT;
        return GameKit.mix(a, b, level % 2 == 0 ? k * .6f : .35f + k * .6f);
    }

    private static void stack(Canvas c, RectF r, long t, float d, boolean night, boolean black, float a, Paint fill, Paint stroke) {
        final int PRE = 2, LEVELS = 5; final float STEP = 1150;
        long loop = (long) (STEP * LEVELS + 1300);
        float u = t % loop;
        int landed = PRE + Math.min(LEVELS, (int) (u / STEP)); float k = (u % STEP) / STEP;
        boolean topple = u >= STEP * LEVELS;
        final int LAST = PRE + LEVELS;
        float bh = r.height() / 8.5f, baseW = r.width() * .58f, cx = r.centerX(), floor = r.bottom - bh * 1.1f;
        float lift = landed > 4 ? (landed - 4) * bh : 0;
        if (!topple && landed < LAST && k > .77f && landed >= 4) lift += bh * clamp((k - .77f) / .23f);
        float fall = topple ? clamp((u - STEP * LEVELS) / 1100f) : 0;
        float w = baseW, l = cx - baseW / 2;
        block(c, l, floor + lift, l + w, floor + bh * .92f + lift, stackColour(0, night), a * (1 - fall), black, d, fill, stroke);
        float left = l, right = l + w;
        for (int i = 1; i <= landed; i++) {
            float off = STACK_OFF[i % STACK_OFF.length] * baseW;
            float nl = Math.max(left, left + off), nr = Math.min(right, right + off);
            if (i % 3 == 0) { nl = left; nr = right; }
            float y = floor - i * bh + lift;
            float drop = topple ? fall * fall * r.height() * (1.2f + (i % 3) * .2f) : 0;
            float tilt = topple ? (i % 2 == 0 ? 1 : -1) * fall * 40 : 0;
            int s2 = c.save(); c.rotate(tilt, (nl + nr) / 2, y);
            block(c, nl, y + drop, nr, y + bh * .9f + drop, stackColour(i, night), a * (1 - fall * .6f), black, d, fill, stroke);
            c.restoreToCount(s2);
            if (i == landed && i > PRE && !topple && k < .25f) {
                float q = clamp(k / .25f), g = bh * .5f * q;
                stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, 2 * d * (1 - q)));
                stroke.setColor(GameKit.alpha(night ? 0xFFBFD6FF : GameKit.BLUE, a * (1 - q)));
                R.set(nl - g, y - g / 2, nr + g, y + bh * .9f + g / 2); c.drawRoundRect(R, bh * .4f, bh * .4f, stroke);
            }
            left = nl; right = nr;
        }
        if (!topple && landed < LAST) {
            int i = landed + 1; float bw = right - left;
            float off = STACK_OFF[i % STACK_OFF.length] * baseW; if (i % 3 == 0) off = 0;
            float target = left + off;
            float slide = clamp(k / .77f), from = i % 2 == 0 ? r.left - bw * .2f : r.right - bw * .8f;
            float x = lerp(from, target, (float) (1 - Math.cos(Math.PI * slide)) / 2f);
            float y = floor - i * bh + lift - bh * 1.1f * (1 - clamp((k - .77f) / .1f));
            block(c, x, y, x + bw, y + bh * .9f, stackColour(i, night), a, black, d, fill, stroke);
        }
    }

    private static void tank(Canvas c, float cx, float cy, float s, float angle, int body, float a, boolean black, float d, Paint fill, Paint stroke) {
        int dark = GameKit.mix(body, 0xFF000000, .38f), light = GameKit.mix(body, 0xFFFFFFFF, .4f);
        int save = c.save(); c.rotate(angle, cx, cy);
        fill.setShader(null);
        for (int side = -1; side <= 1; side += 2) {
            float tl = cx + side * s * .32f - s * .12f;
            R.set(tl, cy - s * .46f, tl + s * .24f, cy + s * .46f); fill.setColor(GameKit.alpha(dark, a)); c.drawRoundRect(R, s * .09f, s * .09f, fill);
        }
        R.set(cx - s * .27f, cy - s * .33f, cx + s * .27f, cy + s * .35f); fill.setColor(GameKit.alpha(body, a)); c.drawRoundRect(R, s * .12f, s * .12f, fill);
        if (black) { stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, d * .8f)); stroke.setColor(GameKit.alpha(0x77FFFFFF, a)); c.drawRoundRect(R, s * .12f, s * .12f, stroke); }
        R.set(cx - s * .05f, cy - s * .58f, cx + s * .05f, cy - s * .05f); fill.setColor(GameKit.alpha(dark, a)); c.drawRoundRect(R, s * .05f, s * .05f, fill);
        fill.setColor(GameKit.alpha(light, a)); c.drawCircle(cx, cy + s * .02f, s * .17f, fill);
        fill.setColor(GameKit.alpha(0x50FFFFFF, a)); c.drawCircle(cx - s * .05f, cy - s * .04f, s * .06f, fill);
        c.restoreToCount(save);
    }

    private static void brick(Canvas c, float l, float t, float s, boolean night, float a, Paint fill) {
        int b1 = night ? 0xFFE0774E : 0xFFEE8A5E, b2 = night ? 0xFFB85634 : 0xFFD06C40;
        float g = s * .07f, h = (s - g * 3) / 2f;
        fill.setShader(null);
        fill.setColor(GameKit.alpha(b1, a)); R.set(l + g, t + g, l + g + (s - 3 * g) * .62f, t + g + h); c.drawRoundRect(R, h * .3f, h * .3f, fill);
        fill.setColor(GameKit.alpha(GameKit.mix(b1, b2, .3f), a)); R.set(l + 2 * g + (s - 3 * g) * .62f, t + g, l + s - g, t + g + h); c.drawRoundRect(R, h * .3f, h * .3f, fill);
        fill.setColor(GameKit.alpha(GameKit.mix(b1, b2, .3f), a)); R.set(l + g, t + 2 * g + h, l + g + (s - 3 * g) * .38f, t + s - g); c.drawRoundRect(R, h * .3f, h * .3f, fill);
        fill.setColor(GameKit.alpha(b1, a)); R.set(l + 2 * g + (s - 3 * g) * .38f, t + 2 * g + h, l + s - g, t + s - g); c.drawRoundRect(R, h * .3f, h * .3f, fill);
    }

    private static void boom(Canvas c, float x, float y, float s, float k, float a, Paint fill) {
        if (k <= 0 || k >= 1) return;
        fill.setShader(null);
        float g = GameKit.ease(k), fade = 1 - GameKit.easeIn(k);
        fill.setColor(GameKit.alpha(0xFFFF5A36, a * fade)); c.drawCircle(x, y, s * (.3f + .7f * g), fill);
        fill.setColor(GameKit.alpha(0xFFFFA23D, a * fade)); c.drawCircle(x, y, s * (.2f + .5f * g), fill);
        fill.setColor(GameKit.alpha(0xFFFFE27A, a * fade)); c.drawCircle(x, y, s * (.1f + .3f * g), fill);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + .3; float far = s * (.4f + 1.1f * g), q = s * .14f * fade;
            float px = x + (float) Math.cos(ang) * far, py = y + (float) Math.sin(ang) * far;
            fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFFFE27A : 0xFFFF7A3D, a * fade)); R.set(px - q, py - q, px + q, py + q); c.drawRoundRect(R, q * .4f, q * .4f, fill);
        }
    }

    private static void siege(Canvas c, RectF r, long t, float d, boolean night, boolean black, float a, Paint fill, Paint stroke) {
        final float LOOP = 4400;
        float u = t % (long) LOOP;
        float unit = Math.min(r.width() / 8f, r.height() / 7f), ox = r.centerX() - unit * 4, oy = r.centerY() - unit * 3.5f;
        boolean brickGone = u > 1550;
        for (int x = 0; x < 8; x++) {
            float l = ox + x * unit, tp = oy + unit * 3;
            if (x == 0 || x == 7) {
                R.set(l + unit * .08f, tp + unit * .08f, l + unit * .92f, tp + unit * .92f);
                int steel = night ? 0xFF8C9AB4 : 0xFFA3AEC4; fill.setShader(null); fill.setColor(GameKit.alpha(steel, a)); c.drawRoundRect(R, unit * .2f, unit * .2f, fill);
                R.set(l + unit * .28f, tp + unit * .28f, l + unit * .72f, tp + unit * .72f); fill.setColor(GameKit.alpha(GameKit.mix(steel, 0xFFFFFFFF, .45f), a)); c.drawRoundRect(R, unit * .12f, unit * .12f, fill);
            } else if (x == 3 && brickGone) {
                float k = clamp((u - 1550) / 380f);
                if (k < 1) { float s = unit * (1 - GameKit.easeIn(k)); brick(c, l + (unit - s) / 2, tp + (unit - s) / 2, s, night, a, fill); }
            } else if (x != 4) brick(c, l, tp, unit, night, a, fill);
        }
        float bx = ox + unit * 4, by = oy + unit * 6.1f, bs = unit * 1.15f;
        Q.set(bx - bs / 2, by - bs / 2, bx + bs / 2, by + bs / 2);
        RectF[] sq = BASE; GameKit.squaresOf(Q, sq);
        for (int k = 0; k < 4; k++) {
            float bob = (float) Math.sin(t / 380.0 + k * 1.3) * unit * .03f;
            block(c, sq[k].left, sq[k].top + bob, sq[k].right, sq[k].bottom + bob, k % 3 == 0 ? GameKit.BLUE : GameKit.LIGHT, a, black, d, fill, stroke);
        }
        float ex = ox + unit * 4.5f, ey;
        float enemyIn = clamp(u / 2600f);
        ey = oy + unit * (.2f + 1.5f * enemyIn);
        boolean enemyDead = u > 3050;
        if (!enemyDead) {
            float hitFlash = u > 2950 ? 1 : 0;
            tank(c, ex, ey, unit * 1.05f, 180, hitFlash > 0 ? 0xFFFFFFFF : 0xFF2BB673, a, black, d, fill, stroke);
        }
        float px, py = oy + unit * 4.9f, ang;
        if (u < 900) { px = ox + unit * lerp(1.2f, 3.5f, GameKit.smooth(u / 900f)); ang = 90; }
        else if (u < 1150) { px = ox + unit * 3.5f; ang = lerp(90, 0, GameKit.ease((u - 900) / 250f)); }
        else if (u < 2000) { px = ox + unit * 3.5f; ang = 0; }
        else if (u < 2500) { px = ox + unit * lerp(3.5f, 4.5f, GameKit.smooth((u - 2000) / 500f)); ang = 0; }
        else { px = ox + unit * 4.5f; ang = 0; }
        float recoil = (u > 1250 && u < 1400) || (u > 2650 && u < 2800) ? unit * .08f : 0;
        tank(c, px, py + recoil, unit * 1.05f, ang, GameKit.BLUE, a, black, d, fill, stroke);
        if (u > 1250 && u < 1550) { float k = (u - 1250) / 300f; shot(c, ox + unit * 3.5f, lerp(py - unit * .6f, oy + unit * 3.9f, k), unit, a, fill); }
        if (u > 2650 && u < 2950) { float k = (u - 2650) / 300f; shot(c, ox + unit * 4.5f, lerp(py - unit * .6f, ey + unit * .5f, k), unit, a, fill); }
        if (u > 1550) boom(c, ox + unit * 3.5f, oy + unit * 3.5f, unit * .7f, (u - 1550) / 500f, a, fill);
        if (u > 3000) boom(c, ex, ey, unit * 1.2f, (u - 3000) / 700f, a, fill);
    }

    private static void shot(Canvas c, float x, float y, float unit, float a, Paint fill) {
        fill.setShader(null);
        fill.setColor(GameKit.alpha(0x55FFE066, a)); c.drawCircle(x, y + unit * .15f, unit * .2f, fill);
        fill.setColor(GameKit.alpha(0xFFFFE066, a)); c.drawCircle(x, y, unit * .12f, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, a)); c.drawCircle(x, y, unit * .06f, fill);
    }

    private static void blast(Canvas c, RectF r, long t, float d, boolean night, boolean black, float a, Paint fill, Paint stroke) {
        final float LOOP = 4600;
        float u = t % (long) LOOP;
        final int GW = 9, GH = 7;
        float cell = Math.min(r.width() / GW, r.height() / GH), ox = r.centerX() - cell * GW / 2f, oy = r.centerY() - cell * GH / 2f;
        int pillar = night ? 0xFF2C5BD6 : GameKit.BLUE, edge = night ? 0xFF1A2F6E : 0xFF0D2E8A, crate = night ? 0xFF9EC1FF : 0xFFBCD4FF, crate2 = night ? 0xFF7FA6F5 : 0xFF9EC1FF;
        R.set(ox, oy, ox + cell * GW, oy + cell * GH);
        fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0x14FFFFFF : 0x0F000000, a)); c.drawRoundRect(R, cell * .3f, cell * .3f, fill);
        for (int y = 0; y < GH; y++) for (int x = 0; x < GW; x++) {
            boolean border = x == 0 || y == 0 || x == GW - 1 || y == GH - 1, inner = x % 2 == 0 && y % 2 == 0;
            if (!border && !inner) continue;
            float half = cell * .46f, cx = ox + (x + .5f) * cell, cy = oy + (y + .5f) * cell;
            block(c, cx - half, cy - half, cx + half, cy + half, border ? edge : pillar, a, black, d, fill, stroke);
        }
        int[][] crates = {{5, 3}, {7, 1}, {1, 5}, {5, 1}};
        for (int i = 0; i < crates.length; i++) {
            float half = cell * .46f;
            if (i == 0 && u > 2500) { float k = clamp((u - 2500) / 420f); half *= 1 - GameKit.easeIn(k); if (half < 1) continue; }
            float cx = ox + (crates[i][0] + .5f) * cell, cy = oy + (crates[i][1] + .5f) * cell, q = half * .9f, g2 = half * .08f;
            for (int k = 0; k < 4; k++) {
                float l = cx - q + (k % 2) * (q + g2), tp = cy - q + (k / 2) * (q + g2);
                block(c, l + g2, tp + g2, l + q - g2, tp + q - g2, (i == 0 && u > 2500 && u < 2650) ? 0xFFFFFFFF : (k == 0 || k == 3 ? crate : crate2), a, black, d, fill, stroke);
            }
        }
        if (u > 800 && u < 2500) {
            float bx = ox + 3.5f * cell, by = oy + 3.5f * cell, k = (u - 800) / 1700f;
            float pulse = 1 + .08f * (float) Math.sin(u / 1000.0 * Math.PI * 2 * (1 + k * 3)), rr = cell * .36f * pulse * GameKit.spring((u - 800) / 260f);
            fill.setColor(GameKit.alpha(k > .8f && (u / 70) % 2 == 0 ? 0xFFE53935 : night ? 0xFF3A4366 : 0xFF1B2440, a)); c.drawCircle(bx, by, rr, fill);
            fill.setColor(GameKit.alpha(0x55FFFFFF, a)); c.drawCircle(bx - rr * .35f, by - rr * .35f, rr * .28f, fill);
            float fx = bx + rr * .45f, fy = by - rr * .8f, ex = fx + cell * .14f * (1 - k), ey = fy - cell * .2f * (1 - k);
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeWidth(Math.max(1f, cell * .06f)); stroke.setColor(GameKit.alpha(night ? 0xFFC9B8A0 : 0xFF8A6D4E, a)); c.drawLine(fx, fy, ex, ey, stroke);
            float sp = cell * .08f * (1 + .4f * (float) Math.sin(u / 40.0));
            fill.setColor(GameKit.alpha(0xFFFFE066, a)); c.drawCircle(ex, ey, sp, fill);
        }
        if (u > 2500 && u < 3200) {
            float k = (u - 2500) / 700f, grow = k < .15f ? GameKit.ease(k / .15f) : 1 - GameKit.easeIn((k - .15f) / .85f) * .9f;
            int[][] cellsHit = {{3, 3}, {2, 3}, {1, 3}, {4, 3}, {5, 3}, {3, 2}, {3, 1}, {3, 4}, {3, 5}};
            int[] layers = {0xFFFF5A36, 0xFFFFA23D, 0xFFFFE27A, 0xFFFFFFFF}; float[] widths = {1f, .74f, .5f, .24f};
            for (int[] h : cellsHit) {
                float cx = ox + (h[0] + .5f) * cell, cy = oy + (h[1] + .5f) * cell, w0 = cell * .42f * grow;
                boolean across = h[1] == 3, centre = h[0] == 3 && h[1] == 3;
                for (int i = 0; i < 4; i++) {
                    float w = w0 * widths[i], hh = cell * .5f;
                    fill.setColor(GameKit.alpha(layers[i], a * (i == 3 ? .85f : 1f)));
                    if (centre) c.drawCircle(cx, cy, w * 1.1f, fill);
                    else if (across) { R.set(cx - hh, cy - w, cx + hh, cy + w); c.drawRect(R, fill); }
                    else { R.set(cx - w, cy - hh, cx + w, cy + hh); c.drawRect(R, fill); }
                }
            }
        }
        boolean ghostDead = u > 2600;
        float gx = ox + lerp(7.5f, 3.5f, clamp((u - 900) / 1600f)) * cell, gy = oy + 5.5f * cell;
        if (u < 3100) {
            float k = ghostDead ? 1 - GameKit.ease((u - 2600) / 450f) : 1f;
            ghost(c, gx, gy, cell * .74f, (float) Math.sin(u / 160.0), ghostDead, k, a, fill);
        }
        float hx, hy;
        if (u < 800) { hx = lerp(1.5f, 3.5f, GameKit.smooth(u / 800f)); hy = 3.5f; }
        else if (u < 1400) { hx = lerp(3.5f, 1.5f, GameKit.smooth((u - 800) / 600f)); hy = 3.5f; }
        else if (u < 1900) { hx = 1.5f; hy = lerp(3.5f, 1.5f, GameKit.smooth((u - 1400) / 500f)); }
        else if (u < 3600) { hx = 1.5f; hy = 1.5f; }
        else { hx = 1.5f; hy = lerp(1.5f, 3.5f, GameKit.smooth((u - 3600) / 900f)); }
        boolean walking = (u < 1900) || u > 3600;
        float hop = u > 3150 && u < 3550 ? (float) Math.abs(Math.sin((u - 3150) / 400.0 * Math.PI * 2)) * cell * .18f : 0;
        hero(c, ox + hx * cell, oy + hy * cell - hop, cell * .78f, walking ? u : 0, u > 3150 && u < 3550, a, black, d, fill, stroke);
    }

    private static void ghost(Canvas c, float cx, float cy, float s, float wob, boolean dying, float k, float a, Paint fill) {
        if (k <= 0) return;
        int save = c.save(); c.scale((1 + .06f * wob) * (dying ? 1 + (1 - k) * .6f : 1), (1 - .06f * wob) * k, cx, cy + s * .4f);
        Path p = P; p.reset();
        float l = cx - s * .45f, r = cx + s * .45f, t = cy - s * .45f, b = cy + s * .38f;
        p.moveTo(l, b); p.lineTo(l, t + s * .35f); p.cubicTo(l, t - s * .05f, r, t - s * .05f, r, t + s * .35f); p.lineTo(r, b);
        for (int i = 0; i < 4; i++) { float x0 = r - (r - l) * (i + .5f) / 4f, x1 = r - (r - l) * (i + 1) / 4f; p.quadTo(x0, b + s * .1f * (i % 2 == 0 ? 1 : -.2f), x1, b); }
        p.close();
        fill.setShader(null); fill.setColor(GameKit.alpha(dying ? 0xFFFF7A3D : 0xFF2BB673, a)); c.drawPath(p, fill);
        fill.setColor(GameKit.alpha(0x40FFFFFF, a)); c.drawCircle(cx - s * .2f, cy - s * .24f, s * .09f, fill);
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * s * .15f - s * .05f, y = cy - s * .05f;
            fill.setColor(GameKit.alpha(0xFFFFFFFF, a)); c.drawCircle(x, y, s * (dying ? .13f : .1f), fill);
            fill.setColor(GameKit.alpha(0xFF14161C, a)); c.drawCircle(x - s * .03f, y, s * (dying ? .035f : .05f), fill);
        }
        c.restoreToCount(save);
    }

    private static void hero(Canvas c, float cx, float cy, float s, float walk, boolean cheer, float a, boolean black, float d, Paint fill, Paint stroke) {
        float bob = walk > 0 ? (float) Math.abs(Math.sin(walk / 1000.0 * Math.PI * 5)) * s * .07f : 0;
        fill.setShader(null); fill.setColor(GameKit.alpha(0x30000000, a)); R.set(cx - s * .4f, cy + s * .38f, cx + s * .4f, cy + s * .52f); c.drawOval(R, fill);
        float step = walk > 0 ? (float) Math.sin(walk / 1000.0 * Math.PI * 5) * s * .1f : 0;
        fill.setColor(GameKit.alpha(GameKit.mix(GameKit.BLUE, 0xFF000000, .3f), a));
        c.drawCircle(cx - s * .2f, cy + s * .42f + step * .3f, s * .11f, fill); c.drawCircle(cx + s * .2f, cy + s * .42f - step * .3f, s * .11f, fill);
        float top = cy - s * .45f - bob, bot = cy + s * .4f - bob, half = s * .44f;
        block(c, cx - half, top, cx + half, bot, GameKit.BLUE, a, black, d, fill, stroke);
        R.set(cx - half, top + s * .1f, cx + half, top + s * .19f); fill.setColor(GameKit.alpha(0xFFFF5A5A, a)); c.drawRect(R, fill);
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * s * .16f, y = cy - s * .08f - bob;
            fill.setColor(GameKit.alpha(0xFFFFFFFF, a)); R.set(x - s * .1f, y - s * .13f, x + s * .1f, y + s * .13f); c.drawOval(R, fill);
            fill.setColor(GameKit.alpha(0xFF14161C, a)); c.drawCircle(x, y, s * .055f, fill);
        }
        if (cheer) { stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, s * .05f)); stroke.setColor(GameKit.alpha(0xFF14161C, a)); R.set(cx - s * .12f, cy + s * .06f - bob, cx + s * .12f, cy + s * .26f - bob); c.drawArc(R, 10, 160, false, stroke); }
        else { fill.setColor(GameKit.alpha(GameKit.LIGHT, a)); c.drawCircle(cx, cy + s * .2f - bob, s * .05f, fill); }
    }
}
