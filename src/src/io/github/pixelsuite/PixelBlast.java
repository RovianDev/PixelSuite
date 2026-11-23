package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;

import java.util.ArrayList;

final class PixelBlast extends GameKit.Game {
    static final int W = 13, H = 11;
    static final int FLOOR = 0, PILLAR = 1, CRATE = 2;

    final int[][] map = new int[H][W];
    final int[][] hidden = new int[H][W];
    final long[][] crumbleAt = new long[H][W];

    final int[][] crack = new int[H][W], heat = new int[H][W];
    int crackNeed() { return Math.max(1, 5 - angel() / 2); }
    int heatNeed() { return Math.max(1, 6 - demon() / 2); }
    final RectF board = new RectF();
    float cell;
    int stage = 1, lives, bombsMax, range; float speed;
    boolean fromTitle, portalOpen; int portalX = -1, portalY = -1;

    static final class Bomb { int x, y, id, slide = -1, range; long at; float fuse, off; boolean done, poison, mega, kicked, struck, bounced; int ran, soul; }

    static final class Ball { float x, y, r; long at; boolean mega; }
    final ArrayList<Ball> balls = new ArrayList<Ball>();
    float ballReach(Bomb b) { return 1.05f + Math.min(reachOf(b), 7) * .2f; }

    int big() { return SystemClock.uptimeMillis() < bigUntil ? bigLevel : 0; }
    int reachOf(Bomb b) { return Math.max(1, (b.range > 0 ? b.range : b.mega ? range + 3 : range) + big() + soulReach()); }
    boolean pierces(Bomb b) { return b.mega || rank >= 6 || demon() >= 2 || angel() >= 5; }

    static final int NONE = 0, ANGEL = 1, DEMON = 2, TOP = 10;
    int soul, soulLv, meals, soulFrom, soulUnits; float soulBits, soulShown; long soulDropAt = -10000, gainAt = -10000; long soulAt = -10000, absorbAt = -10000, ashAt = -10000; float ashX, ashY;
    boolean haloReady;
    long portalNagAt = -10000;
    long kickAt = -10000; int kickDir;
    static final long KICK_MS = 440;
    long eatAt = -10000, smiteAt = -10000; float smiteCool = 6f, smiteX, smiteY;
    long frenzyUntil, novaAt = -10000, haloAt, bigUntil; int bigLevel; float novaCool = 6f, orb;
    int angel() { return soul == ANGEL ? soulLv : 0; }
    int demon() { return soul == DEMON ? soulLv : 0; }

    static int soulStep(int lv) { return lv < 3 ? 1 : lv - 1; }
    static int soulTotal(int lv) { int n = 0; for (int i = 0; i < lv; i++) n += soulStep(i); return n; }
    static int soulLevelOf(int units) { int lv = 0; while (lv < TOP && units >= soulTotal(lv + 1)) lv++; return lv; }

    int soulHave() { return soulUnits - soulTotal(soulLv); }

    static final float LIGHT_XP = .125f;
    void soulGain(float xp) {
        if (soul == NONE || soulLv >= TOP || xp <= 0) return;
        soulSync();
        soulBits += xp; gainAt = SystemClock.uptimeMillis();
        while (soulBits >= .999f) { soulBits = Math.max(0f, soulBits - 1f); soulUnits++; }
        if (soulLevelOf(soulUnits) > soulLv) soulUp();
        if (soulLv >= TOP) soulBits = 0;
    }

    float soulProgress() { return soulLv >= TOP ? 1f : GameKit.clamp((soulUnits + soulBits - soulTotal(soulLv)) / (float) soulStep(soulLv)); }
    int soulColour() { return soul == ANGEL ? 0xFFFFC531 : soul == DEMON ? 0xFFE5173F : 0xFF9AA3B5; }
    String soulName(int kind) { return I18n.t(kind == ANGEL ? "Angelic" : "Demonic"); }

    int soulReach() { int an = angel(), dm = demon(); return an >= 7 || dm >= 8 ? 2 : an >= 3 || dm >= 2 ? 1 : 0; }

    float breathLen() { int an = angel(), dm = demon(); return dm > 0 ? 2.4f + .2f * dm : an > 0 ? 2.4f - .08f * an : 2.4f; }
    float breathFan() { return .035f * demon(); }
    float breathSide() { return .55f - .012f * angel(); }
    long breathBurnMs() { int an = angel(), dm = demon(); return dm > 0 ? 1700 - 80 * dm : 1700 + 100 * an; }

    boolean breathClear(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0; int n = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) * 4));
        for (int i = 1; i < n; i++) {
            int x = (int) Math.floor(x0 + dx * i / n), y = (int) Math.floor(y0 + dy * i / n);
            if (x < 0 || y < 0 || x >= W || y >= H || map[y][x] == PILLAR || map[y][x] == CRATE && crumbleAt[y][x] == 0) return false;
        }
        return true;
    }

    void soulStepUp() {
        long now = SystemClock.uptimeMillis(); soulAt = now;
        int col = soulColour();
        pop(soulHave() + "/" + soulStep(soulLv), px(hx), py(hy - 1.2f), col, 18);
        burst(px(hx), py(hy), col, 14, 220, 4);
        PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.1f); GameKit.thump(a, .6f);
    }

    void soulDown() {
        if (soul == NONE || soulUnits <= 1) return;
        long now = SystemClock.uptimeMillis();
        soulUnits--;
        int lv = soulLevelOf(soulUnits);
        if (lv < soulLv) {
            soulLv = lv; soulDropAt = now;
            postDelayed(() -> { pop("\u25BC " + soulName(soul) + " " + soulLv, px(hx), py(hy - 1.2f), soulColour(), 18); PixelStackSound.play(PixelStackSound.SOFT, .4f, .62f); }, 700);
        }
    }

    void soulSync() { if (soul != NONE && soulLv > 0 && soulUnits < soulTotal(soulLv)) soulUnits = soulTotal(soulLv); }
    void soulUp() {
        long now = SystemClock.uptimeMillis();
        soulLv = Math.min(TOP, soulLv + 1); soulAt = now;
        int col = soulColour();
        levelUp(soul == ANGEL ? LV_LIGHT : LV_DARK, soulName(soul) + " " + soulLv, I18n.t("Level up!"));
        burst(px(hx), py(hy), col, 20 + soulLv, 260, 5); burst(px(hx), py(hy), soul == ANGEL ? 0xFFFFFFFF : 0xFF3A0A10, 12, 200, 4);
        shake(soulLv >= TOP ? 12 : 7); GameKit.thump(a, .8f);
        if (soul == ANGEL) haloReady = true;
    }

    static final class Shrine { int x, y, kind; long at, usedAt = -1; boolean used, ready = true; }
    final ArrayList<Shrine> shrines = new ArrayList<Shrine>();
    int ask = -1, askLast = -1; long askAt, askShutAt = -10000; boolean askTapped;
    final RectF yesAt = new RectF(), noAt = new RectF(), askAll = new RectF();

    void placeShrines() {
        shrines.clear(); ask = -1; askLast = -1;
        ArrayList<int[]> open = new ArrayList<int[]>();
        for (int y = 1; y < H - 1; y++) for (int x = 1; x < W - 1; x++) if (map[y][x] == FLOOR && crumbleAt[y][x] == 0 && !(x == portalX && y == portalY)) open.add(new int[]{x, y});
        if (open.size() < 2) return;
        java.util.Collections.shuffle(open, rnd);
        int[] p0 = null, q0 = null; float best = -1;
        int n = Math.min(open.size(), 70);
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            int[] p = open.get(i), q = open.get(j);
            float apart = (float) Math.hypot(p[0] - q[0], p[1] - q[1]);
            float dp = (float) Math.min(Math.hypot(p[0] - portalX, p[1] - portalY), Math.hypot(q[0] - portalX, q[1] - portalY));
            float dh = (float) Math.min(Math.hypot(p[0] + .5f - hx, p[1] + .5f - hy), Math.hypot(q[0] + .5f - hx, q[1] + .5f - hy));
            float score = Math.min(apart, 8f) + .6f * Math.min(dp, 3f) + .6f * Math.min(dh, 3f);
            if (score > best) { best = score; p0 = p; q0 = q; }
        }
        if (p0 == null) return;
        long now = SystemClock.uptimeMillis(); boolean flip = rnd.nextBoolean();
        Shrine s = new Shrine(); s.x = p0[0]; s.y = p0[1]; s.kind = flip ? ANGEL : DEMON; s.at = now; shrines.add(s);
        Shrine t = new Shrine(); t.x = q0[0]; t.y = q0[1]; t.kind = flip ? DEMON : ANGEL; t.at = now + 160; shrines.add(t);
        PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.4f); postDelayed(() -> PixelStackSound.play(PixelStackSound.FIRE, .3f, .7f), 160);
    }

    void stepShrines(long now) {
        if (state != PLAY || !heroAlive) { if (ask >= 0) shutAsk(now); return; }
        for (int i = 0; i < shrines.size(); i++) {
            Shrine s = shrines.get(i); if (s.used) continue;
            float dx = hx - s.x - .5f, dy = hy - s.y - .5f;
            boolean on = Math.abs(dx) < .45f && Math.abs(dy) < .45f;
            if (on && s.ready && ask != i) { if (ask >= 0) shutAsk(now); ask = i; askAt = now; askTapped = false; s.ready = false; PixelStackSound.play(PixelStackSound.SOFT, .4f, s.kind == ANGEL ? 1.4f : .8f); GameKit.tick(a, .4f); }
            if (ask == i && !askTapped && Math.hypot(dx, dy) > 1.05f) shutAsk(now);
            if (!on && ask != i) s.ready = true;
        }
    }
    void stepSouls(float dt, long now) {
        soulSync();
        if (state == PLAY) stepHazards(now);
        stepShrines(now);
        int an = angel(), dm = demon();
        if (an >= 5 && !haloReady && now - haloAt > (an >= 9 ? 10000 : 20000)) { haloReady = true; burst(px(hx), py(hy - .6f), 0xFFFFD23F, 10, 160, 3); PixelStackSound.play(PixelStackSound.TING, .35f, 1.5f); }
        if (an >= 4 && heroAlive) {
            orb += dt * 3.2f;
            for (int k = 0; k < (an >= 7 ? 2 : 1); k++) {
                double ang = orb + k * Math.PI; float gx = hx + (float) Math.cos(ang) * 1.15f, gy = hy + (float) Math.sin(ang) * 1.15f;
                for (Glitch g : new ArrayList<Glitch>(glitches)) if (g.alive && now - g.hitAt > 550 && Math.hypot(g.x - gx, g.y - gy) < (g.boss ? .9f : .6f)) { hurt(g, 1, 0xFFFFC531); burst(px(g.x), py(g.y), 0xFFFFE9A8, 8, 160, 3); PixelStackSound.play(PixelStackSound.TING, .3f, 1.8f); }
            }
        }
        if (dm >= 5 && heroAlive) {
            novaCool -= dt;
            if (novaCool <= 0) {
                boolean any = false; for (Glitch g : glitches) if (g.alive && Math.hypot(g.x - hx, g.y - hy) < 2.7f) any = true;
                if (!any) novaCool = .5f;
                else {
                    novaCool = dm >= 7 ? 4f : 8f; novaAt = now;
                    for (Glitch g : new ArrayList<Glitch>(glitches)) if (g.alive && Math.hypot(g.x - hx, g.y - hy) < 2.7f) { if (g.mini) eat(g); else hurt(g, 2, 0xFFE5173F); }
                    shake(12); GameKit.rumble(a); PixelStackSound.play(PixelStackSound.BOOM, .55f, .45f);
                }
            }
        }
        if (an >= 3 && heroAlive) {
            smiteCool -= dt;
            if (smiteCool <= 0) {
                Glitch best = null; double bd = 1e9;
                for (Glitch g : glitches) if (g.alive) { double dd = Math.hypot(g.x - hx, g.y - hy); if (dd < bd) { bd = dd; best = g; } }
                if (best == null) smiteCool = 1f;
                else {
                    smiteCool = an >= 5 ? 3.5f : 7f; smiteAt = now; smiteX = best.x; smiteY = best.y;
                    hurt(best, best.boss ? 1 : 2, 0xFFFFC531);
                    burst(px(best.x), py(best.y), 0xFFFFF6D0, 16, 260, 4); shake(6);
                    PixelStackSound.play(PixelStackSound.TING, .5f, 1.6f); PixelStackSound.play(PixelStackSound.BOOM, .3f, 1.4f);
                }
            }
        }
        stepLights(dt, now); stepTrails(dt, now); stepStrikes(now); stepBlazes(dt, now); stepScorch(now);
    }

    int flameInk() { return soul == ANGEL && soulLv >= 3 ? 0xFFFFE9A8 : soul == DEMON && soulLv >= 3 ? 0xFFFF5A3D : 0xFFFFC94A; }
    void shutAsk(long now) { askTapped = false; if (ask < 0) return; askLast = ask; askShutAt = now; ask = -1; yesAt.setEmpty(); noAt.setEmpty(); }

    @Override boolean playTap(float x, float y) {
        if (ask >= 0 && state == PLAY && SystemClock.uptimeMillis() - askAt >= 220) {
            float mx = 5 * d, my = 10 * d;
            if (x > yesAt.left - mx && x < yesAt.right + mx && y > yesAt.top - my && y < yesAt.bottom + my) { answer(true); return true; }
            if (x > noAt.left - mx && x < noAt.right + mx && y > noAt.top - my && y < noAt.bottom + my) { answer(false); return true; }
            if (askAll.contains(x, y)) return true;
        }
        return claimTap(x, y);
    }

    boolean claimTap(float x, float y) {
        if (state != PLAY || !heroAlive) return false;
        long now = SystemClock.uptimeMillis();
        float reach = Math.max(cell * .8f, 28 * d), best = reach; Object hit = null;
        for (Prize p : prizes) { if (p.tapped || now - p.at < 200) continue; float dd = (float) Math.hypot(px(prizeX(p)) - x, py(prizeY(p)) - y); if (dd < best) { best = dd; hit = p; } }
        for (Gem g : gems) { if (g.tapped) continue; float e = GameKit.easeIn(g.pull), dd = (float) Math.hypot(px(g.x + .5f + (hx - g.x - .5f) * e) - x, py(g.y + .5f + (hy - g.y - .5f) * e) - y); if (dd < best) { best = dd; hit = g; } }
        for (Light l : lights) { if (l.tapped || l.fate != 0 || now < l.at) continue; float dd = (float) Math.hypot(px(l.x) - x, py(l.y) - cell * .12f - y); if (dd < best) { best = dd; hit = l; } }
        for (int i = 0; i < shrines.size(); i++) { Shrine sh = shrines.get(i); if (sh.used || now - sh.at < 500) continue; float dd = (float) Math.hypot(px(sh.x + .5f) - x, py(sh.y + .5f) - y); if (dd < Math.max(best, cell * .7f) && dd < best + cell * .2f) { best = dd; hit = sh; } }
        if (hit == null) return false;
        float hxp = 0, hyp = 0; int col = 0xFFFFFFFF;
        if (hit instanceof Prize) { Prize p = (Prize) hit; p.tapped = true; hxp = px(prizeX(p)); hyp = py(prizeY(p)); col = TIER_COLOUR[tierOf(p.kind)]; }
        else if (hit instanceof Gem) { Gem g = (Gem) hit; g.tapped = true; hxp = px(g.x + .5f); hyp = py(g.y + .5f); col = GEM[g.hue]; }
        else if (hit instanceof Light) { Light l = (Light) hit; l.tapped = true; hxp = px(l.x); hyp = py(l.y); col = 0xFFFFE9A8; }
        else {
            Shrine sh = (Shrine) hit; int i = shrines.indexOf(sh);
            if (ask >= 0 && ask != i) shutAsk(now);
            ask = i; askAt = now; askTapped = true; sh.ready = false;
            PixelStackSound.play(PixelStackSound.SOFT, .4f, sh.kind == ANGEL ? 1.4f : .8f); GameKit.tick(a, .4f);
            burst(px(sh.x + .5f), py(sh.y + .5f), sh.kind == ANGEL ? 0xFFFFE9A8 : 0xFFFF5A1F, 10, 160, 3);
            return true;
        }
        burst(hxp, hyp, col, 10, 170, 3); PixelStackSound.play(PixelStackSound.PICK, .35f, 1.3f); GameKit.tick(a, .35f);
        return true;
    }
    void answer(boolean yes) {
        long now = SystemClock.uptimeMillis();
        if (ask < 0 || ask >= shrines.size()) { ask = -1; return; }
        Shrine s = shrines.get(ask);
        shutAsk(now);
        if (!yes) { PixelStackSound.play(PixelStackSound.SOFT, .3f, .9f); GameKit.tick(a, .3f); return; }
        for (Shrine o : shrines) if (!o.used) { o.used = true; o.usedAt = now; }
        takeSoul(s.kind);
        burst(px(s.x + .5f), py(s.y + .5f), s.kind == ANGEL ? 0xFFFFFFFF : 0xFFFF5A1F, 24, 280, 5);
    }

    void takeSoul(int kind) {
        long now = SystemClock.uptimeMillis();
        soulFrom = soul; soulAt = now;
        soulSync();
        if (soul == kind) { if (soulLv < TOP) { soulUnits++; if (soulLevelOf(soulUnits) > soulLv) soulUp(); else soulStepUp(); } else { addScore(2000); pop("+2000", px(hx), py(hy - 1.1f), soulColour(), 20); } }
        else { int to = soul == NONE ? 1 : Math.max(1, soulLv / 2); soul = kind; soulUnits = soulTotal(to); soulBits = 0; soulShown = 0; soulLv = to - 1; soulUp(); }
        if (kind == ANGEL) { haloReady = true; PixelStackSound.play(PixelStackSound.RISE, .4f, 1.5f); for (int i = 0; i < 18; i++) { GameKit.Bit b = spark(px(hx + (rnd.nextFloat() - .5f) * 1.2f), py(hy + .3f), i % 3 == 0 ? 0xFFFFE9A8 : 0xFFFFFFFF, 60, 4.5f); b.round = true; b.gravity = -.5f; b.decay = .9f; } }
        else { PixelStackSound.play(PixelStackSound.FART, .3f, .5f); for (int i = 0; i < 18; i++) { GameKit.Bit b = spark(px(hx), py(hy + .2f), i % 3 == 0 ? 0xFFFF5A1F : i % 3 == 1 ? 0xFF4A4A4A : 0xFF2A2A2A, 110, 7f); b.round = true; b.gravity = -.3f; b.decay = .8f; } }
        GameKit.rumble(a);
    }

    void drawShrines(Canvas c, long now) {
        for (int i = 0; i < shrines.size(); i++) {
            Shrine s = shrines.get(i);
            if (now < s.at) continue;
            float cx = px(s.x + .5f), cy = py(s.y + .5f), in = GameKit.spring((now - s.at) / 600f);
            float gone = s.used ? 1 - GameKit.ease((now - s.usedAt) / 900f) : 1f; if (gone <= 0) continue;
            boolean asking = ask == i;
            float a0 = Math.min(1f, in) * gone, pulse = motion ? (float) (.5 + .5 * Math.sin(now / (asking ? 140.0 : 320.0))) : .5f;
            fill.setShader(null);
            if (s.kind == ANGEL) {
                fill.setShader(new android.graphics.LinearGradient(cx, cy - cell * 3.2f, cx, cy + cell * .4f, 0x00FFF6D0, GameKit.alpha(0xFFFFF6D0, a0 * (.34f + .16f * pulse)), android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(cx - cell * .42f, cy - cell * 3.2f, cx + cell * .42f, cy + cell * .4f, fill); fill.setShader(null);
                fill.setColor(GameKit.alpha(0xFFFFE9A8, a0 * (.22f + .12f * pulse))); c.drawCircle(cx, cy, cell * (.78f + .08f * pulse + (asking ? .12f : 0)), fill);
                box.set(cx - cell * .4f, cy - cell * .4f, cx + cell * .4f, cy + cell * .4f);
                fill.setColor(GameKit.alpha(0xFFFFFDF2, a0)); c.drawRoundRect(box, cell * .14f, cell * .14f, fill);
                stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFFFC531, a0)); c.drawRoundRect(box, cell * .14f, cell * .14f, stroke);
                stroke.setStrokeWidth(cell * .06f); stroke.setColor(GameKit.alpha(0xFFFFC531, a0)); c.drawOval(GameKit.rf(cx - cell * .17f, cy - cell * .3f, cx + cell * .17f, cy - cell * .18f), stroke);
                fill.setColor(GameKit.alpha(0xFFBFD8FF, a0));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); w.moveTo(cx + side * cell * .03f, cy + cell * .02f); w.quadTo(cx + side * cell * .3f, cy - cell * .2f, cx + side * cell * .32f, cy + cell * .08f); w.quadTo(cx + side * cell * .2f, cy + cell * .22f, cx + side * cell * .03f, cy + cell * .14f); w.close(); c.drawPath(w, fill); }
                if (motion && !s.used && rnd.nextFloat() < .3f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * cell * .7f, cy + cell * .2f, rnd.nextBoolean() ? 0xFFFFFFFF : 0xFFFFE9A8, 30, 3f); b.round = true; b.gravity = -.35f; b.decay = 1.3f; }
            } else {
                fill.setShader(new android.graphics.LinearGradient(cx, cy - cell * 2.6f, cx, cy + cell * .4f, 0x00E5173F, GameKit.alpha(0xFFE5173F, a0 * (.3f + .16f * pulse)), android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(cx - cell * .42f, cy - cell * 2.6f, cx + cell * .42f, cy + cell * .4f, fill); fill.setShader(null);
                fill.setColor(GameKit.alpha(0xFF8E0D22, a0 * (.26f + .14f * pulse))); c.drawCircle(cx, cy, cell * (.78f + .08f * pulse + (asking ? .12f : 0)), fill);
                box.set(cx - cell * .4f, cy - cell * .4f, cx + cell * .4f, cy + cell * .4f);
                fill.setColor(GameKit.alpha(0xFF1A0508, a0)); c.drawRoundRect(box, cell * .14f, cell * .14f, fill);
                stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFE5173F, a0 * (.7f + .3f * pulse))); c.drawRoundRect(box, cell * .14f, cell * .14f, stroke);
                fill.setColor(GameKit.alpha(0xFFE5173F, a0));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path h = GameKit.path(); h.moveTo(cx + side * cell * .08f, cy - cell * .12f); h.quadTo(cx + side * cell * .3f, cy - cell * .18f, cx + side * cell * .26f, cy - cell * .36f); h.quadTo(cx + side * cell * .2f, cy - cell * .16f, cx + side * cell * .02f, cy - cell * .04f); h.close(); c.drawPath(h, fill); }
                android.graphics.Path e = GameKit.path(); e.moveTo(cx - cell * .2f, cy + cell * .1f); e.quadTo(cx, cy - cell * .04f, cx + cell * .2f, cy + cell * .1f); e.quadTo(cx, cy + cell * .24f, cx - cell * .2f, cy + cell * .1f); e.close();
                fill.setColor(GameKit.alpha(0xFFFFB347, a0 * (.75f + .25f * pulse))); c.drawPath(e, fill);
                fill.setColor(GameKit.alpha(0xFF1A0508, a0)); c.drawOval(GameKit.rf(cx - cell * .025f, cy + cell * .02f, cx + cell * .025f, cy + cell * .18f), fill);
                if (motion && !s.used && rnd.nextFloat() < .35f) { boolean ember = rnd.nextFloat() < .45f; GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * cell * .7f, cy - cell * .1f, ember ? 0xFFFF7A3D : 0x99505050, ember ? 40 : 25, ember ? 2.6f : 6f); b.round = true; b.gravity = ember ? -.5f : -.25f; b.decay = ember ? 1.4f : .9f; }
            }
        }
    }

    void drawAsk(Canvas c, long now) {
        int i = ask >= 0 ? ask : askLast;
        if (i < 0 || i >= shrines.size() || (state != PLAY && ask < 0)) { askLast = -1; yesAt.setEmpty(); noAt.setEmpty(); askAll.setEmpty(); return; }
        float k = ask >= 0 ? (motion ? GameKit.spring((now - askAt) / 320f) : 1f) : 1 - GameKit.ease((now - askShutAt) / 180f);
        if (ask < 0 && k <= 0) { askLast = -1; askAll.setEmpty(); return; }
        int kind = shrines.get(i).kind;
        int to = soul == kind ? Math.min(TOP, soulLv + 1) : soul == NONE ? 1 : Math.max(1, soulLv / 2);
        String note = I18n.t("Level {0}").replace("{0}", String.valueOf(to));
        if (soul == kind && soulLv < TOP && soulStep(soulLv) > 1) note += " · " + (soulHave() + 1) + "/" + soulStep(soulLv);
        if (soul != NONE && soul != kind) note = I18n.t(soul == ANGEL ? "Your angelic power fades" : "Your demonic power fades") + " · " + note;
        askBubble(c, px(hx), py(hy - .1f), cell * .5f, I18n.t(kind == ANGEL ? "Take the angelic power?" : "Take the demonic power?"), note, kind == ANGEL ? 0xFFD49A00 : 0xFFE5173F, kind == DEMON, k, yesAt, noAt, askAll);
        if (ask < 0) { yesAt.setEmpty(); noAt.setEmpty(); }
    }

    void drawSoulChange(Canvas c, long now) {
        float t = (now - soulAt) / 900f; if (t < 0 || t >= 1 || !heroAlive || soul == NONE) return;
        float x = px(hx), y = py(hy), a0 = 1 - GameKit.easeIn(t);
        fill.setShader(null);
        if (soul == ANGEL) {
            float w = cell * (.6f + .3f * (float) Math.sin(Math.PI * t));
            fill.setColor(GameKit.alpha(0xFFFFF6D0, a0 * .55f)); c.drawRect(x - w, board.top, x + w, y + cell * .3f, fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .8f)); c.drawRect(x - w * .3f, board.top, x + w * .3f, y + cell * .3f, fill);
            stroke.setStrokeWidth(3 * d * a0); stroke.setColor(GameKit.alpha(0xFFFFC531, a0)); c.drawCircle(x, y, cell * (.3f + 1.6f * GameKit.ease(t)), stroke);
        } else {
            for (int i = 0; i < 8; i++) { double ang = i * Math.PI / 4 + t * 2; float rr = cell * (.4f + 1.1f * GameKit.ease(t)); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFF2A2A2A : 0xFF4A3A3A, a0 * .6f)); c.drawCircle(x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr * .7f, cell * (.35f + .3f * t), fill); }
            float fh = cell * 2.4f * (float) Math.sin(Math.PI * Math.min(1f, t * 1.4f));
            for (int i = -2; i <= 2; i++) { android.graphics.Path f = GameKit.path(); float fx = x + i * cell * .22f, h = fh * (1 - Math.abs(i) * .22f); f.moveTo(fx - cell * .16f, y + cell * .3f); f.quadTo(fx + (float) Math.sin(now / 50.0 + i) * cell * .1f, y - h, fx + cell * .16f, y + cell * .3f); f.close(); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFE5532B : 0xFFFFB347, a0 * .85f)); c.drawPath(f, fill); }
            stroke.setStrokeWidth(3 * d * a0); stroke.setColor(GameKit.alpha(0xFFE5173F, a0)); c.drawCircle(x, y, cell * (.3f + 1.6f * GameKit.ease(t)), stroke);
        }
    }

    final android.graphics.Path halfClip = new android.graphics.Path();
    static final class Stain { float x, y, r; long at; int colour; }
    final ArrayList<Stain> stains = new ArrayList<Stain>();
    void gore(float gx, float gy, int colour, int chunks) {
        long now = SystemClock.uptimeMillis(); float cx = px(gx), cy = py(gy);
        for (int i = 0; i < chunks; i++) { GameKit.Bit b = spark(cx, cy, i % 4 == 0 ? GameKit.mix(colour, 0xFF000000, .3f) : colour, 320, cell / d * (.16f + rnd.nextFloat() * .16f)); b.gravity = 1.5f; b.decay = .9f; }
        for (int i = 0; i < 26; i++) { GameKit.Bit b = spark(cx, cy, i % 3 == 0 ? 0xFF8E0D22 : 0xFFC4122F, 300, 3.2f + rnd.nextFloat() * 2.5f); b.round = true; b.gravity = 1.4f; b.decay = 1.2f; }
        for (int i = 0; i < 7; i++) { Stain st = new Stain(); double ang = rnd.nextFloat() * Math.PI * 2; float far = rnd.nextFloat() * 1.1f; st.x = gx + (float) Math.cos(ang) * far; st.y = gy + (float) Math.sin(ang) * far; st.r = .1f + rnd.nextFloat() * .2f * (1.2f - far); st.at = now + (long) (far * 120); st.colour = i % 2 == 0 ? 0xFFC4122F : 0xFF8E0D22; stains.add(st); }
        while (stains.size() > 60) stains.remove(0);
    }
    void gib(Glitch g) {
        if (!g.alive) return;
        gore(g.x, g.y, glitchColour(g), g.boss ? 10 : 16);
        shake(12); GameKit.thump(a, 1f); PixelStackSound.play(PixelStackSound.POP, .6f, .6f);
        if (g.boss || g.type == 9) { hurt(g, 4, 0xFFFF4D6D); if (!g.boss) g.gibbed = !g.alive; return; }
        hitNumber(px(g.x), py(g.y) - cell * .6f, Math.max(1, g.hp), 0xFFFF4D6D, true);
        g.gibbed = true; g.split = false;
        squash(g);
    }
    void drawStains(Canvas c, long now) {
        fill.setShader(null);
        for (int i = stains.size() - 1; i >= 0; i--) {
            Stain st = stains.get(i); float age = now - st.at;
            if (age > 9000) { stains.remove(i); continue; }
            if (age < 0) continue;
            float k = GameKit.ease(age / 160f), a0 = 1 - GameKit.clamp((age - 5500) / 3500f);
            fill.setColor(GameKit.alpha(st.colour, a0 * .85f)); c.drawOval(GameKit.rf(px(st.x) - st.r * cell * k, py(st.y) - st.r * cell * k * .8f, px(st.x) + st.r * cell * k, py(st.y) + st.r * cell * k * .8f), fill);
        }
    }

    void eat(Glitch g) {
        long now = SystemClock.uptimeMillis();
        g.alive = false; g.deadAt = now; eatAt = now; meals++;
        int dm = demon();
        if (dm >= 4) frenzyUntil = now + 4000;
        int pts = 150 * Math.max(1, Math.min(1 + dm / 2, 6)); addScore(pts); kills++;
        hitNumber(px(g.x), py(g.y) - cell * .4f, 1, 0xFFFF4D6D, false);
        pop("+" + (now < doubleUntil ? pts * 2 : pts), px(g.x), py(g.y - .4f), 0xFFFF4D6D, 15);
        burst(px(g.x), py(g.y), glitchColour(g), 8, 140, 3);
        for (int i = 0; i < 16; i++) {
            GameKit.Bit b = spark(px(hx + DX[face] * .12f), py(hy + .16f), i % 3 == 0 ? 0xFF8E0D22 : 0xFFC4122F, 210, 3.6f);
            b.round = true; b.gravity = 1.4f; b.decay = 1.5f; b.vx += DX[face] * 60 * d; b.vy -= 40 * d;
        }
        PixelStackSound.play(PixelStackSound.PICK, .6f, .6f); PixelStackSound.play(PixelStackSound.POP, .4f, .7f); GameKit.thump(a, .6f);
        if (soul == DEMON) {
            if (dm >= 9 && meals % 10 == 0) { lives++; pop(I18n.t("Extra life"), px(hx), py(hy - 1.6f), 0xFFE5173F, 18); PixelStackSound.play(PixelStackSound.SUCCESS, .45f, .8f); }
        }
        if (glitchesLeft() == 0) openPortal();
    }

    void drawSoul(Canvas c, float cx, float cy, float s, float top, float half, float bob, long now, float fade, boolean under) {
        int an = angel(), dm = demon();
        fill.setShader(null);
        float ab = now - absorbAt < 750 ? 1 - (now - absorbAt) / 750f : 0f;
        float flare = ab > 0 ? (float) Math.sin(Math.PI * Math.min(1f, (1 - ab) * 2.2f)) : 0f;

        boolean powering = an > 0 && heroAlive && (now < laserUntil || breathing());
        float pw = powering ? (motion ? .32f + .06f * (float) Math.sin(now / 90.0) : .32f) : 0f;
        flare = Math.max(flare, pw);
        if (under) {
            if (dm >= 3) { float q = (float) (.5 + .5 * Math.sin(now / 160.0)); fill.setColor(GameKit.alpha(0xFF5A0A1E, fade * (.26f + .02f * dm + .12f * q))); c.drawCircle(cx, cy - bob, s * (.74f + .012f * dm + .06f * q), fill); }
            if (dm >= TOP) for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI / 6 + now / 600.0; float rr = s * .72f, fl = s * (.16f + .07f * (float) Math.sin(now / 55.0 + i * 1.7));
                float fx = cx + (float) Math.cos(ang) * rr, fy = cy - bob + (float) Math.sin(ang) * rr;
                android.graphics.Path f = GameKit.path(); f.moveTo(fx - s * .07f, fy); f.quadTo(fx + (float) Math.cos(ang) * fl, fy + (float) Math.sin(ang) * fl - fl, fx + s * .07f, fy); f.close();
                fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFE5532B : 0xFFFFB347, fade * .85f)); c.drawPath(f, fill);
            }
            if (an >= 3) {
                float q = (float) (.5 + .5 * Math.sin(now / 260.0)), R0 = s * (.78f + .012f * an + .06f * q + .3f * flare);
                fill.setColor(GameKit.alpha(0xFFFFF3C8, fade * (.05f + .005f * an + .15f * ab + .06f * flare))); c.drawCircle(cx, cy - bob, R0 * 1.25f, fill);
                fill.setColor(GameKit.alpha(0xFFFFF3C8, fade * (.08f + .007f * an + .2f * ab + .08f * flare))); c.drawCircle(cx, cy - bob, R0, fill);
                fill.setColor(GameKit.alpha(0xFFFFF6DC, fade * (.11f + .01f * an + .25f * ab + .1f * flare))); c.drawCircle(cx, cy - bob, R0 * .78f, fill);
            }
            if (an >= 5) {
                int n = an >= TOP ? 14 : 10; float step = 360f / n;
                int sv = c.save(); c.rotate(now / 40f % 360, cx, cy - bob); fill.setColor(GameKit.alpha(an >= TOP ? 0xFFFFD86B : 0xFFFFE9A8, fade * (.3f + .2f * flare)));
                for (int i = 0; i < n; i++) { android.graphics.Path ray = GameKit.path(); ray.moveTo(cx - s * .045f, cy - bob); ray.lineTo(cx, cy - bob - s * (i % 2 == 0 ? 1.25f + .02f * an : .95f)); ray.lineTo(cx + s * .045f, cy - bob); ray.close(); c.drawPath(ray, fill); c.rotate(step, cx, cy - bob); }
                c.restoreToCount(sv);
            }
            if (ab > 0) { stroke.setStrokeWidth(3 * d * ab); stroke.setColor(GameKit.alpha(0xFFFFE9A8, fade * ab)); c.drawCircle(cx, cy - bob, s * (.6f + .9f * (1 - ab)), stroke); }
            if (dm >= 7) {
                float sw = motion ? (float) Math.sin(now / 220.0) * s * .12f : 0;
                stroke.setStrokeWidth(Math.max(1.5f, s * .06f)); stroke.setColor(GameKit.alpha(0xFF2A0508, fade));
                android.graphics.Path tl = GameKit.path(); tl.moveTo(cx + half * .6f, cy + s * .28f - bob); tl.quadTo(cx + s * .9f, cy + s * .3f - bob + sw, cx + s * .8f, cy - s * .12f - bob + sw); c.drawPath(tl, stroke);
                fill.setColor(GameKit.alpha(0xFFE5173F, fade)); android.graphics.Path tp = GameKit.path(); float tx = cx + s * .8f, ty = cy - s * .12f - bob + sw; tp.moveTo(tx - s * .08f, ty + s * .03f); tp.lineTo(tx, ty - s * .15f); tp.lineTo(tx + s * .08f, ty + s * .03f); tp.close(); c.drawPath(tp, fill);
            }
            if (dm >= 4) {
                float flap = motion ? (float) Math.sin(now / 150.0) * s * .08f : 0, big = dm >= 7 ? 1.25f : 1f;
                fill.setColor(GameKit.alpha(dm >= 8 ? 0xFF1A0508 : 0xFF4A0A18, fade));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); float wx = cx + side * half * .7f, wy = cy - s * .1f - bob;
                    w.moveTo(wx, wy); w.lineTo(wx + side * s * .75f * big, wy - s * .5f * big - flap); w.lineTo(wx + side * s * .62f * big, wy - s * .1f); w.lineTo(wx + side * s * .86f * big, wy + s * .1f + flap); w.lineTo(wx + side * s * .5f * big, wy + s * .12f); w.lineTo(wx + side * s * .56f * big, wy + s * .38f); w.lineTo(wx, wy + s * .22f); w.close(); c.drawPath(w, fill); }
            }
            if (an >= 2) {
                float flapDeg = motion ? (float) Math.sin(now / 240.0) * 6f : 0f, sc = 1 + .25f * flare;
                boolean gold = an >= 5;
                for (int side = -1; side <= 1; side += 2) {
                    if (an >= 7) featherWing(c, cx + side * half * .7f, cy - s * .26f - bob, side, s * (.5f + .03f * an) * sc, -102f - 8f * flare, -72f, 4, fade, gold, flapDeg * .6f);
                    featherWing(c, cx + side * half * .85f, cy - s * .12f - bob, side, s * (.55f + .035f * an) * sc, -70f - 10f * flare, -8f, 4 + an / 3, fade, gold, flapDeg);
                    if (an >= TOP) featherWing(c, cx + side * half * .85f, cy + s * .06f - bob, side, s * .44f * sc, 14f, 52f + 8f * flare, 4, fade, gold, -flapDeg * .6f);
                }
            }
            if (motion && heroAlive && state == PLAY) {
                if (pw > 0 && rnd.nextFloat() < .12f + .02f * an) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * s * 1.2f, cy - bob + s * .2f, rnd.nextBoolean() ? 0xFFFFE082 : 0xFFFFFFFF, 25, 2.6f); b.round = true; b.gravity = -.25f; b.decay = 1.3f; }
                if (an >= 8 && rnd.nextFloat() < .08f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * s, cy - s * .3f - bob, 0xFFFFFFFF, 20, 3.4f); b.round = true; b.gravity = .12f; b.decay = .8f; }
                if (dm >= 8 && rnd.nextFloat() < .12f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * s * .8f, cy - bob, rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A, 30, 2.4f); b.round = true; b.gravity = -.4f; b.decay = 1.6f; }
                if (dm >= 6 && rnd.nextFloat() < .06f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * s * .6f, top, 0x664A4A4A, 15, 6f); b.round = true; b.gravity = -.2f; b.decay = .9f; }
            }
            return;
        }
        if (an >= 7) {
            float bot = cy + s * .4f - bob; box.set(cx - half, top, cx + half, bot); float rad = Math.min(bot - top, 2 * half) * .26f;
            stroke.setStrokeWidth(Math.max(1.2f, s * .035f)); stroke.setColor(GameKit.alpha(0xFFE8B931, fade)); c.drawRoundRect(box, rad, rad, stroke);
        }
        if (dm >= 1) {
            fill.setColor(GameKit.alpha(dm >= 2 ? 0xFFB3122F : 0xFF7A1020, fade));
            for (int side = -1; side <= 1; side += 2) {
                android.graphics.Path h = GameKit.path(); float bx = cx + side * half * .62f, ht = s * (.16f + .035f * dm);
                if (dm < 7) { h.moveTo(bx - side * s * .09f, top + s * .03f); h.lineTo(bx + side * s * (.1f + .025f * dm), top - ht); h.lineTo(bx + side * s * .1f, top + s * .03f); }
                else { h.moveTo(bx - side * s * .1f, top + s * .04f); h.cubicTo(bx + side * s * .1f, top - ht * .7f, bx + side * s * .42f, top - ht * .6f, bx + side * s * .36f, top - ht); h.cubicTo(bx + side * s * .3f, top - ht * .55f, bx + side * s * .14f, top - ht * .35f, bx + side * s * .1f, top + s * .04f); }
                h.close(); c.drawPath(h, fill);
                if (dm >= TOP) { float fx = bx + side * s * .36f, fy = top - ht, fl = s * (.1f + .04f * (float) Math.sin(now / 60.0 + side)); fill.setColor(GameKit.alpha(0xFFFF9A3D, fade)); android.graphics.Path f = GameKit.path(); f.moveTo(fx - s * .05f, fy + s * .03f); f.quadTo(fx, fy - fl * 1.8f, fx + s * .05f, fy + s * .03f); f.close(); c.drawPath(f, fill); fill.setColor(GameKit.alpha(0xFFB3122F, fade)); }
            }
        }
        if (dm >= 5) {
            for (int i = -2; i <= 2; i++) { float fh = s * (.2f + .09f * (float) Math.sin(now / 70.0 + i * 1.9)), fx = cx + i * s * .13f; android.graphics.Path fl = GameKit.path(); fl.moveTo(fx - s * .07f, top + s * .04f); fl.quadTo(fx, top - fh * 1.5f, fx + s * .07f, top + s * .04f); fl.close(); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFE5173F : 0xFF5A0A1E, fade)); c.drawPath(fl, fill); }
        }
        if (dm >= 6) {
            float q = (float) (.5 + .5 * Math.sin(now / 130.0)); stroke.setStrokeWidth(Math.max(1.2f, s * .03f)); stroke.setColor(GameKit.alpha(0xFFFF7A3D, fade * (.55f + .4f * q)));
            android.graphics.Path cr = GameKit.path(); cr.moveTo(cx - half * .8f, cy + s * .1f - bob); cr.lineTo(cx - half * .55f, cy + s * .18f - bob); cr.lineTo(cx - half * .62f, cy + s * .3f - bob); cr.moveTo(cx + half * .82f, cy + s * .02f - bob); cr.lineTo(cx + half * .6f, cy + s * .14f - bob); cr.lineTo(cx + half * .7f, cy + s * .26f - bob); c.drawPath(cr, stroke);
        }
        if (SystemClock.uptimeMillis() < frenzyUntil) { stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFE5173F, fade * (.4f + .4f * (float) Math.sin(now / 60.0)))); c.drawCircle(cx, cy - bob, s * .62f, stroke); }
        if (an >= 7) {
            box.set(cx - half, top + s * .1f, cx + half, top + s * .16f); fill.setColor(GameKit.alpha(0xFFFFC531, fade)); c.drawRect(box, fill);
            fill.setColor(GameKit.alpha(0xFF7FE9FF, fade)); c.drawCircle(cx, top + s * .13f, s * .05f, fill);
        }
        if (an >= 1) {
            float hy0 = top - s * (.16f + .006f * an) + (motion ? (float) Math.sin(now / 300.0) * s * .02f : 0), hw = s * (.24f + .006f * an);
            if (an >= TOP) { fill.setColor(GameKit.alpha(0xFFFFE9A8, fade * .9f)); for (int i = -2; i <= 2; i++) { float sx = cx + i * hw * .38f, sh = s * (i == 0 ? .2f : .13f); android.graphics.Path st = GameKit.path(); st.moveTo(sx - s * .04f, hy0); st.lineTo(sx, hy0 - sh); st.lineTo(sx + s * .04f, hy0); st.close(); c.drawPath(st, fill); } }
            if (pw > 0) { stroke.setStrokeWidth(s * .16f); stroke.setColor(GameKit.alpha(0xFFFFE57A, fade * .28f)); c.drawOval(GameKit.rf(cx - hw, hy0 - s * .07f, cx + hw, hy0 + s * .07f), stroke); }
            stroke.setStrokeWidth(s * (.06f + .002f * an)); stroke.setColor(GameKit.alpha(pw > 0 ? 0xFFFFE57A : haloReady ? 0xFFFFD23F : 0x88FFD23F, fade)); c.drawOval(GameKit.rf(cx - hw, hy0 - s * .07f, cx + hw, hy0 + s * .07f), stroke);
            if (an >= TOP) { stroke.setStrokeWidth(s * .03f); stroke.setColor(GameKit.alpha(0xFFFFF3B0, fade)); c.drawOval(GameKit.rf(cx - hw * .62f, hy0 - s * .042f, cx + hw * .62f, hy0 + s * .042f), stroke); }
            if (ab > 0) { float sp = (now / 40f) % 360; stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * ab)); c.drawArc(GameKit.rf(cx - hw, hy0 - s * .07f, cx + hw, hy0 + s * .07f), sp, 70, false, stroke); }
        }
        if (ab > 0) {
            stroke.setStrokeWidth(2.5f * d); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * ab)); c.drawCircle(cx, cy - bob, s * 1.3f * ab, stroke);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * ab * .35f)); c.drawCircle(cx, cy - bob, s * .5f, fill);
        }
    }

    static final class Light { float x, y, spin; long at, endAt = -1; int fate; boolean burst, tapped; }
    final ArrayList<Light> lights = new ArrayList<Light>();
    void freeLights(Glitch g) {
        long now = SystemClock.uptimeMillis(); int n = 2 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) { Light l = new Light(); double ang = i * Math.PI * 2 / n + rnd.nextFloat(); l.x = g.x + (float) Math.cos(ang) * .35f; l.y = g.y + (float) Math.sin(ang) * .35f; l.at = now + 160 + i * 110; l.spin = rnd.nextFloat() * 6; lights.add(l); }
        burst(px(g.x), py(g.y), 0xFFFFFFFF, 14, 220, 4); burst(px(g.x), py(g.y), 0xFFFFE9A8, 10, 160, 3);
        PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.5f);
    }
    void stepLights(float dt, long now) {
        for (int i = lights.size() - 1; i >= 0; i--) {
            Light l = lights.get(i);
            if (l.fate != 0) {
                if (l.fate == 2 && !l.burst && now - l.endAt >= 380) { l.burst = true; gore(l.x, l.y, 0xFFC4122F, 5); PixelStackSound.play(PixelStackSound.POP, .4f, .6f); GameKit.thump(a, .4f); }
                if (now - l.endAt > (l.fate == 2 ? 1000 : 650)) lights.remove(i);
                continue;
            }
            if (now < l.at) continue;
            l.spin += dt * 2;
            if (now - l.at > 10000) { l.fate = 3; l.endAt = now; continue; }
            if (!heroAlive || state != PLAY || (soul == NONE && !l.tapped)) continue;
            float dx = hx - l.x, dy = hy - l.y, dd = (float) Math.hypot(dx, dy);
            float reach = l.tapped ? 99f : soul == ANGEL ? 3.4f + .1f * angel() : 2.6f;
            if (dd < reach && dd > .001f) {
                float v = dt * (l.tapped ? 9f : soul == ANGEL ? 2.4f + (reach - dd) * 2.4f : 1.5f + (reach - dd) * 1.2f); if (v > dd) v = dd;
                float sw = soul == ANGEL ? .55f : 0f;
                float ux = dx / dd, uy = dy / dd, mx = ux - uy * sw, my = uy + ux * sw, ml = (float) Math.hypot(mx, my);
                l.x += mx / ml * v; l.y += my / ml * v;
            }
            if (dd < (soul == ANGEL ? .34f : .42f)) {
                l.fate = soul == DEMON ? 2 : 1; l.endAt = now; l.x = hx; l.y = hy;
                soulGain(LIGHT_XP);
                if (soul != DEMON) {
                    absorbAt = now; addScore(250);
                    burst(px(hx), py(hy), 0xFFFFE9A8, 12, 180, 3);
                    PixelStackSound.play(PixelStackSound.TING, .45f, 1.7f); PixelStackSound.play(PixelStackSound.SPARKLE, .4f, 1.6f); GameKit.tick(a, .5f);
                } else {
                    for (int k = 0; k < 10; k++) { GameKit.Bit b = spark(px(l.x), py(l.y), k % 2 == 0 ? 0xAA3A3A3A : 0xAA6A5A5A, 60, 6f); b.round = true; b.gravity = -.2f; b.decay = 1.2f; }
                    PixelStackSound.play(PixelStackSound.FART, .25f, 1.3f);
                }
            }
        }
    }
    void drawLights(Canvas c, long now) {
        for (Light l : lights) {
            if (now < l.at) continue;
            float x = px(l.x), y = py(l.y);
            fill.setShader(null);
            if (l.fate == 0 || l.fate == 3) {
                float age = now - l.at, in = GameKit.spring(age / 400f), a0 = l.fate == 3 ? 1 - GameKit.clamp((now - l.endAt) / 500f) : 1f;
                if (l.fate == 3) y -= cell * .5f * GameKit.clamp((now - l.endAt) / 500f);
                y += (motion ? (float) Math.sin(now / 300.0 + l.spin) * cell * .08f : 0) - cell * .12f;
                float r = cell * .17f * in, tw = motion ? (float) (.5 + .5 * Math.sin(now / 120.0 + l.spin * 3)) : .5f;
                fill.setColor(GameKit.alpha(0xFFFFF1B8, a0 * (.22f + .1f * tw))); c.drawCircle(x, y, r * 2.4f, fill);
                float flap = motion ? (float) Math.sin(now / 90.0 + l.spin) * r * .35f : 0;
                fill.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .9f));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); w.moveTo(x + side * r * .4f, y); w.quadTo(x + side * r * 1.7f, y - r * 1.2f - flap, x + side * r * 1.6f, y + r * .2f); w.quadTo(x + side * r * 1.1f, y + r * .7f + flap * .5f, x + side * r * .4f, y + r * .3f); w.close(); c.drawPath(w, fill); }
                fill.setColor(GameKit.alpha(0xFFFFF6D0, a0)); c.drawCircle(x, y, r, fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, a0)); c.drawCircle(x, y, r * .55f, fill);
                stroke.setStrokeWidth(Math.max(1f, r * .18f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, a0 * (.4f + .6f * tw)));
                float st = r * (1.5f + .6f * tw), sa = l.spin;
                c.drawLine(x - (float) Math.cos(sa) * st, y - (float) Math.sin(sa) * st, x + (float) Math.cos(sa) * st, y + (float) Math.sin(sa) * st, stroke);
                c.drawLine(x + (float) Math.sin(sa) * st, y - (float) Math.cos(sa) * st, x - (float) Math.sin(sa) * st, y + (float) Math.cos(sa) * st, stroke);
                if (motion && rnd.nextFloat() < .12f) { GameKit.Bit b = spark(x, y, 0xFFFFE9A8, 20, 2.4f); b.round = true; b.gravity = -.2f; b.decay = 2f; }
            } else if (l.fate == 1) {
                float t = (now - l.endAt) / 650f, hxp = px(hx), hyp = py(hy);
                stroke.setStrokeWidth(3 * d * (1 - t)); stroke.setColor(GameKit.alpha(0xFFFFE9A8, 1 - t)); c.drawCircle(hxp, hyp, cell * 1.1f * (1 - GameKit.ease(t)), stroke);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, (1 - t) * .7f)); c.drawCircle(hxp, hyp, cell * .32f * (1 - t), fill);
            } else {
                float t = now - l.endAt;
                if (t < 380) {
                    float k = GameKit.ease(t / 380f);
                    for (int i = 0; i < 6; i++) { double ang = i * Math.PI / 3 + t / 120.0; float rr = cell * .14f * (1 - k * .5f); fill.setColor(GameKit.alpha(GameKit.mix(0xFF6A6A6A, 0xFF4A1010, k), .75f)); c.drawCircle(x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr, cell * (.16f + .14f * k), fill); }
                    fill.setColor(GameKit.alpha(0xFF2A1010, .6f * k)); c.drawCircle(x, y, cell * .22f * k, fill);
                } else {
                    float k = (t - 380) / 620f;
                    fill.setColor(GameKit.alpha(0xFFC4122F, (1 - k) * .85f)); c.drawCircle(x, y, cell * (.25f + .45f * GameKit.ease(Math.min(1f, k * 2))), fill);
                    fill.setColor(GameKit.alpha(0xFF8E0D22, (1 - k) * .9f)); c.drawCircle(x, y, cell * (.16f + .2f * GameKit.ease(Math.min(1f, k * 2))), fill);
                }
            }
        }
    }

    static final class Trail { float x, y; long at; int kind, bomb; }
    final ArrayList<Trail> trails = new ArrayList<Trail>();
    static final long TRAIL_MS = 1500;
    void lay(Bomb b, int x, int y) {
        Trail t = new Trail(); t.x = x + .5f; t.y = y + .5f; t.at = SystemClock.uptimeMillis(); t.kind = b.soul; t.bomb = b.id; trails.add(t);
        while (trails.size() > 80) trails.remove(0);
        if (b.soul == ANGEL) PixelStackSound.play(PixelStackSound.TING, .18f, 1.8f + rnd.nextFloat() * .2f); else PixelStackSound.play(PixelStackSound.FIRE, .16f, 1.2f);
    }
    void stepTrails(float dt, long now) {
        for (int i = trails.size() - 1; i >= 0; i--) if (now - trails.get(i).at > TRAIL_MS) trails.remove(i);
        if (trails.isEmpty()) return;
        for (Glitch g : new ArrayList<Glitch>(glitches)) {
            if (!g.alive) continue;
            g.trailCool -= dt; if (g.trailCool > 0) continue;
            for (Trail t : trails) if (Math.abs(g.x - t.x) < .62f && Math.abs(g.y - t.y) < .62f) {
                g.trailCool = .45f;
                if (t.kind == ANGEL) { g.zapAt = now; if (g.boss || g.type == 9) hurt(g, 1, 0xFF7FE9FF); else shock(g, now); burst(px(g.x), py(g.y), 0xFFBFF4FF, 6, 140, 3); }
                else if (g.boss || g.type == 9) hurt(g, 1, 0xFFFF7A3D);
                else if (!fleeing(g) && g.loveUntil == 0) scorch(g, now, SCORCH_GROUND);
                break;
            }
        }
    }
    void drawTrails(Canvas c, long now) {
        for (int i = 0; i < trails.size(); i++) {
            Trail t = trails.get(i); float age = (now - t.at) / (float) TRAIL_MS; if (age >= 1 || age < 0) continue;
            float a0 = 1 - GameKit.easeIn(age), x = px(t.x), y = py(t.y);
            Trail n = i + 1 < trails.size() && trails.get(i + 1).bomb == t.bomb ? trails.get(i + 1) : null;
            fill.setShader(null);
            if (t.kind == ANGEL) {
                fill.setColor(GameKit.alpha(0xFF7FE9FF, a0 * .22f)); c.drawCircle(x, y, cell * .5f, fill);
                if (n != null) bolt(c, x, y, px(n.x), py(n.y), cell * .28f, 5, now / 45 + i * 7, 0xFF7FE9FF, Math.max(1.5f, cell * .06f), a0);
                else bolt(c, x - cell * .3f, y - cell * .2f, x + cell * .3f, y + cell * .2f, cell * .2f, 4, now / 45 + i * 13, 0xFF7FE9FF, Math.max(1.2f, cell * .05f), a0);
                if (motion && rnd.nextFloat() < .2f) { GameKit.Bit b = spark(x, y, 0xFFBFF4FF, 90, 2.2f); b.round = true; b.gravity = 0; b.decay = 4f; }
            } else {
                fill.setColor(GameKit.alpha(0xFF2A1408, a0 * .35f)); c.drawOval(GameKit.rf(x - cell * .42f, y - cell * .2f, x + cell * .42f, y + cell * .32f), fill);
                for (int k = -1; k <= 1; k++) {
                    float fh = cell * (.4f + .14f * (float) Math.sin(now / 55.0 + k * 2 + i)) * a0, fx = x + k * cell * .2f;
                    android.graphics.Path f = GameKit.path(); f.moveTo(fx - cell * .12f, y + cell * .22f); f.quadTo(fx + (float) Math.sin(now / 70.0 + k) * cell * .06f, y + cell * .2f - fh * 1.6f, fx + cell * .12f, y + cell * .22f); f.close();
                    fill.setColor(GameKit.alpha(k == 0 ? 0xFFFF9A3D : 0xFFE5532B, a0 * .9f)); c.drawPath(f, fill);
                }
                fill.setColor(GameKit.alpha(0xFFFFE27A, a0 * .8f)); c.drawCircle(x, y + cell * .12f, cell * .1f * a0, fill);
                if (motion && rnd.nextFloat() < .2f) { GameKit.Bit b = spark(x, y, 0xFFFF9A3D, 30, 2.4f); b.round = true; b.gravity = -.4f; b.decay = 1.6f; }
            }
        }
    }

    static final class Face { float x, y, r; long at; int kind; }
    final ArrayList<Face> faces = new ArrayList<Face>();
    void drawFaces(Canvas c, long now) {
        for (int i = faces.size() - 1; i >= 0; i--) {
            Face f = faces.get(i); float t = now - f.at; if (t > 480) { faces.remove(i); continue; } if (t < 0) continue;
            float in = motion ? GameKit.spring(t / 150f) : 1f, a0 = t > 300 ? 1 - GameKit.ease((t - 300) / 180f) : Math.min(1f, t / 60f);
            float cx = px(f.x), cy = py(f.y), R = cell * Math.max(1.1f, f.r) * .72f * in;
            if (f.kind == ANGEL) angelFace(c, cx, cy, R, a0, now); else demonFace(c, cx, cy, R, a0, now);
        }
    }
    void angelFace(Canvas c, float cx, float cy, float R, float a0, long now) {
        fill.setShader(null);
        int sv = c.save(); c.rotate(now / 30f % 360, cx, cy); fill.setColor(GameKit.alpha(0xFFFFE9A8, a0 * .45f));
        for (int i = 0; i < 12; i++) { android.graphics.Path ray = GameKit.path(); ray.moveTo(cx - R * .08f, cy); ray.lineTo(cx, cy - R * (i % 2 == 0 ? 1.35f : 1.05f)); ray.lineTo(cx + R * .08f, cy); ray.close(); c.drawPath(ray, fill); c.rotate(30, cx, cy); }
        c.restoreToCount(sv);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .92f));
        for (int side = -1; side <= 1; side += 2) for (int f = 0; f < 3; f++) { android.graphics.Path w = GameKit.path(); float wy = cy - R * .1f + f * R * .16f, len = R * (1.25f - f * .22f); w.moveTo(cx + side * R * .4f, wy); w.quadTo(cx + side * (R * .4f + len * .6f), wy - R * .5f + f * R * .12f, cx + side * (R * .4f + len), wy - R * .18f + f * R * .1f); w.quadTo(cx + side * (R * .4f + len * .55f), wy + R * .12f, cx + side * R * .4f, wy + R * .16f); w.close(); c.drawPath(w, fill); }
        fill.setColor(GameKit.alpha(0xFFFFE9A8, a0 * .5f)); c.drawCircle(cx, cy, R * .66f, fill);
        fill.setColor(GameKit.alpha(0xFFFFF4D6, a0)); c.drawCircle(cx, cy, R * .55f, fill);
        stroke.setStrokeWidth(R * .07f); stroke.setColor(GameKit.alpha(0xFFFFC531, a0)); c.drawOval(GameKit.rf(cx - R * .38f, cy - R * .86f, cx + R * .38f, cy - R * .64f), stroke);
        stroke.setStrokeWidth(Math.max(1.5f, R * .045f)); stroke.setColor(GameKit.alpha(0xFF8A6A2A, a0));
        for (int side = -1; side <= 1; side += 2) c.drawArc(GameKit.rf(cx + side * R * .2f - R * .12f, cy - R * .14f, cx + side * R * .2f + R * .12f, cy + R * .04f), 20, 140, false, stroke);
        c.drawArc(GameKit.rf(cx - R * .18f, cy + R * .06f, cx + R * .18f, cy + R * .3f), 20, 140, false, stroke);
        fill.setColor(GameKit.alpha(0x66FF8FB3, a0)); for (int side = -1; side <= 1; side += 2) c.drawCircle(cx + side * R * .33f, cy + R * .1f, R * .07f, fill);
    }
    void demonFace(Canvas c, float cx, float cy, float R, float a0, long now) {
        fill.setShader(null);
        float jx = motion ? (rnd.nextFloat() - .5f) * R * .04f : 0; cx += jx;
        fill.setColor(GameKit.alpha(0xFF1A0508, a0 * .55f)); c.drawCircle(cx, cy, R * .95f, fill);
        fill.setColor(GameKit.alpha(0xFF2A0508, a0));
        for (int side = -1; side <= 1; side += 2) { android.graphics.Path h = GameKit.path(); h.moveTo(cx + side * R * .2f, cy - R * .42f); h.cubicTo(cx + side * R * .55f, cy - R * .7f, cx + side * R * .85f, cy - R * .8f, cx + side * R * .78f, cy - R * 1.25f); h.cubicTo(cx + side * R * .65f, cy - R * .78f, cx + side * R * .5f, cy - R * .5f, cx + side * R * .42f, cy - R * .22f); h.close(); c.drawPath(h, fill); }
        android.graphics.Path fc = GameKit.path();
        fc.moveTo(cx - R * .55f, cy - R * .2f); fc.cubicTo(cx - R * .55f, cy - R * .72f, cx + R * .55f, cy - R * .72f, cx + R * .55f, cy - R * .2f); fc.cubicTo(cx + R * .5f, cy + R * .3f, cx + R * .15f, cy + R * .62f, cx, cy + R * .78f); fc.cubicTo(cx - R * .15f, cy + R * .62f, cx - R * .5f, cy + R * .3f, cx - R * .55f, cy - R * .2f); fc.close();
        fill.setColor(GameKit.alpha(0xFFB3122F, a0)); c.drawPath(fc, fill);
        float glow = motion ? (float) (.5 + .5 * Math.sin(now / 40.0)) : .5f;
        for (int side = -1; side <= 1; side += 2) {
            android.graphics.Path e = GameKit.path(); float ex = cx + side * R * .22f, ey = cy - R * .18f;
            e.moveTo(ex - side * R * .18f, ey - R * .1f); e.lineTo(ex + side * R * .16f, ey + R * .06f); e.quadTo(ex, ey + R * .14f, ex - side * R * .18f, ey - R * .1f); e.close();
            fill.setColor(GameKit.alpha(0xFFFFB347, a0 * (.4f + .3f * glow))); c.drawCircle(ex, ey, R * .17f, fill);
            fill.setColor(GameKit.alpha(0xFFFFE066, a0)); c.drawPath(e, fill);
            fill.setColor(GameKit.alpha(0xFF14060A, a0)); c.drawOval(GameKit.rf(ex - R * .02f, ey - R * .06f, ex + R * .02f, ey + R * .09f), fill);
        }
        android.graphics.Path m = GameKit.path(); float my = cy + R * .2f, mw = R * .42f;
        m.moveTo(cx - mw, my - R * .12f); m.quadTo(cx, my + R * .06f, cx + mw, my - R * .12f); m.quadTo(cx, my + R * .42f, cx - mw, my - R * .12f); m.close();
        fill.setColor(GameKit.alpha(0xFF14060A, a0)); c.drawPath(m, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, a0));
        for (int i = 0; i < 7; i++) { float t = .12f + .76f * (i + .5f) / 7, tx = cx - mw + 2 * mw * t, ty = (my - R * .12f) * ((1 - t) * (1 - t) + t * t) + 2 * (1 - t) * t * (my + R * .06f); android.graphics.Path th = GameKit.path(); th.moveTo(tx - mw * .1f, ty); th.lineTo(tx + mw * .1f, ty); th.lineTo(tx, ty + R * (i == 0 || i == 6 ? .13f : .09f)); th.close(); c.drawPath(th, fill); }
        for (int i = 0; i < 5; i++) { float t = .2f + .6f * (i + .5f) / 5, tx = cx - mw + 2 * mw * t, ty = (my - R * .12f) * ((1 - t) * (1 - t) + t * t) + 2 * (1 - t) * t * (my + R * .42f); android.graphics.Path th = GameKit.path(); th.moveTo(tx - mw * .09f, ty); th.lineTo(tx + mw * .09f, ty); th.lineTo(tx, ty - R * .08f); th.close(); c.drawPath(th, fill); }
    }

    static final class Strike { float x, y, r; long at; boolean hit; }
    final ArrayList<Strike> strikes = new ArrayList<Strike>();
    void stepStrikes(long now) {
        for (int i = strikes.size() - 1; i >= 0; i--) {
            Strike s = strikes.get(i);
            if (!s.hit && now >= s.at) {
                s.hit = true;
                for (Glitch g : new ArrayList<Glitch>(glitches)) if (g.alive && Math.hypot(g.x - s.x, g.y - s.y) <= s.r + .3f) { g.zapAt = now; if (g.boss || g.type == 9) hurt(g, 3, 0xFF7FE9FF); else shock(g, now); }
                burst(px(s.x), py(s.y), 0xFFFFFFFF, 24, 360, 5); burst(px(s.x), py(s.y), 0xFF7FE9FF, 18, 300, 4);
                shake(15); GameKit.rumble(a);
                PixelStackSound.play(PixelStackSound.BOOM, .7f, 1.3f); PixelStackSound.play(PixelStackSound.TING, .5f, 1.9f);
            }
            if (now - s.at > 520) strikes.remove(i);
        }
    }
    void drawStrikes(Canvas c, long now) {
        for (Strike s : strikes) {
            float t = now - s.at, x = px(s.x), y = py(s.y);
            if (t < 0) {
                float k = 1 + t / 230f; fill.setShader(null); fill.setColor(GameKit.alpha(0xFF0B1430, .18f * Math.max(0f, k))); c.drawCircle(x, y, cell * (s.r + .6f), fill); continue;
            }
            float k = t / 520f, a0 = 1 - GameKit.easeIn(k);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .22f)); c.drawRoundRect(board, 14 * d, 14 * d, fill);
            bolt(c, x + (float) Math.sin(s.at) * cell, board.top - cell * .5f, x, y, cell * 1.1f, 9, now / 40, 0xFF9FE8FF, Math.max(2.5f, cell * .14f), a0);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .8f)); c.drawCircle(x, y, cell * (.5f + .3f * (1 - k)), fill);
            stroke.setStrokeWidth(4 * d * a0); stroke.setColor(GameKit.alpha(0xFF7FE9FF, a0)); c.drawCircle(x, y, cell * s.r * GameKit.ease(Math.min(1f, k * 2.5f)), stroke);
            for (int j = 0; j < 4; j++) { double ang = j * Math.PI / 2 + s.at; float rr = cell * s.r * .9f; bolt(c, x, y, x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr, cell * .3f, 4, now / 40 + j, 0xFF7FE9FF, Math.max(1.5f, cell * .06f), a0 * .9f); }
        }
    }

    static final class Blaze { int x, y; long at; }
    final ArrayList<Blaze> blazes = new ArrayList<Blaze>();
    static final long BLAZE_MS = 4000;
    void stepBlazes(float dt, long now) {
        for (int i = blazes.size() - 1; i >= 0; i--) if (now - blazes.get(i).at > BLAZE_MS) blazes.remove(i);
        if (blazes.isEmpty()) return;
        for (Glitch g : new ArrayList<Glitch>(glitches)) {
            if (!g.alive) continue;
            int gx = (int) Math.floor(g.x), gy = (int) Math.floor(g.y);
            for (Blaze z : blazes) if (now >= z.at && z.x == gx && z.y == gy) {
                if (g.boss || g.type == 9) { if (g.trailCool <= 0) { g.trailCool = .5f; hurt(g, 1, 0xFFFF7A3D); } }
                else if (!fleeing(g) && g.loveUntil == 0) scorch(g, now, SCORCH_GROUND);
                break;
            }
        }
        fireSound -= dt; if (fireSound <= 0 && !breathing()) { fireSound = .4f; PixelStackSound.play(PixelStackSound.FIRE, .14f, .8f + rnd.nextFloat() * .2f); }
    }
    void drawBlazes(Canvas c, long now) {
        for (Blaze z : blazes) {
            float age = now - z.at; if (age < 0) continue;
            float a0 = Math.min(GameKit.clamp(age / 150f), GameKit.clamp((BLAZE_MS - age) / 600f)), x = px(z.x + .5f), y = py(z.y + .5f);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFF2A1408, a0 * .4f)); c.drawRoundRect(GameKit.rf(x - cell * .44f, y - cell * .3f, x + cell * .44f, y + cell * .42f), cell * .2f, cell * .2f, fill);
            fill.setColor(GameKit.alpha(0xFFFF7A3D, a0 * (.14f + .06f * (float) Math.sin(now / 90.0 + z.x)))); c.drawCircle(x, y, cell * .55f, fill);
            for (int k = 0; k < 3; k++) {
                float ph = ((now / (420f + k * 37)) + k * .33f + z.y * .17f) % 1f, fx = x + (k - 1) * cell * .22f + (float) Math.sin(now / 160.0 + k * 2 + z.x) * cell * .05f, fy = y + cell * .28f - ph * cell * .6f, q = cell * (.2f - .12f * ph) * a0;
                fill.setColor(GameKit.alpha(ph < .3f ? 0xFFFFF1A8 : ph < .6f ? 0xFFFFB347 : 0xFFE5532B, a0 * (1 - ph * .6f)));
                android.graphics.Path f = GameKit.path(); f.moveTo(fx - q, fy + q); f.quadTo(fx, fy - q * 2.2f, fx + q, fy + q); f.close(); c.drawPath(f, fill);
            }
            if (motion && rnd.nextFloat() < .06f) { GameKit.Bit b = spark(x, y, 0xFFFF9A3D, 30, 2.2f); b.round = true; b.gravity = -.4f; b.decay = 1.6f; }
        }
    }

    void top10(Bomb b, Ball ball, float r, long now) {
        Face f = new Face(); f.x = ball.x; f.y = ball.y; f.r = r; f.at = now; f.kind = b.soul; faces.add(f);
        if (b.soul == ANGEL) { Strike s = new Strike(); s.x = ball.x; s.y = ball.y; s.r = r + .2f; s.at = now + 230; strikes.add(s); PixelStackSound.play(PixelStackSound.SPARKLE, .5f, 1.2f); }
        else {
            int n = (int) Math.ceil(r);
            for (int y = b.y - n; y <= b.y + n; y++) for (int x = b.x - n; x <= b.x + n; x++) {
                if (x < 1 || y < 1 || x >= W - 1 || y >= H - 1 || map[y][x] == PILLAR || Math.hypot(x - b.x, y - b.y) > r) continue;
                Blaze z = new Blaze(); z.x = x; z.y = y; z.at = now + 120 + (long) (Math.hypot(x - b.x, y - b.y) * 60); blazes.add(z);
            }
            while (blazes.size() > 120) blazes.remove(0);
            PixelStackSound.play(PixelStackSound.FIRE, .55f, .6f); postDelayed(() -> PixelStackSound.play(PixelStackSound.BOOM, .4f, .45f), 120);
        }
    }

    boolean fleeing(Glitch g) { return g.burnUntil > 0 || g.shockUntil > 0; }

    void shock(Glitch g, long now) {
        if (!g.alive || g.shockUntil > 0 || g.boss || g.type == 9) return;
        g.shockUntil = now + 1500; g.burnUntil = 0; g.loveUntil = 0; g.zapAt = now; g.hitAt = now;
        if (heroAlive) { float dx = g.x - hx, dy = g.y - hy; g.dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0); }
        burst(px(g.x), py(g.y), 0xFFBFF4FF, 10, 180, 3);
        PixelStackSound.play(PixelStackSound.BOING, .3f, 1.9f); PixelStackSound.play(PixelStackSound.TING, .25f, 2f);
    }

    void flare(Glitch g) {
        if (!g.alive) return;
        float x = px(g.x), y = py(g.y);
        hitNumber(x, y - cell * .55f, Math.max(1, g.hp), 0xFF7FE9FF, true);
        g.lit = true; g.split = false; g.shockUntil = 0;
        burst(x, y, 0xFFFFFFFF, 18, 300, 5); burst(x, y, 0xFFBFF4FF, 14, 240, 4); burst(x, y, 0xFFFFE9A8, 10, 200, 4);
        shake(7); GameKit.thump(a, .7f);
        PixelStackSound.play(PixelStackSound.TING, .45f, 1.6f); PixelStackSound.play(PixelStackSound.POP, .4f, 1.4f);
        squash(g);
    }

    void ash(Glitch g) {
        long now = SystemClock.uptimeMillis();
        g.alive = false; g.deadAt = now; g.ashed = true; ashAt = now; ashX = g.x; ashY = g.y;
        int pts = 120; addScore(pts); kills++;
        hitNumber(px(g.x), py(g.y) - cell * .4f, 1, 0xFFFFC531, false);
        pop("+" + (now < doubleUntil ? pts * 2 : pts), px(g.x), py(g.y - .5f), 0xFFFFC531, 14);
        burst(px(g.x), py(g.y), 0xFFFFE9A8, 8, 140, 3);
        for (int i = 0; i < 14; i++) { GameKit.Bit b = spark(px(g.x), py(g.y), i % 3 == 0 ? 0xFF4A4A4A : 0xFF9A9A9A, 60, 3f); b.gravity = -.22f; b.decay = .9f; }
        PixelStackSound.play(PixelStackSound.FIRE, .3f, 1.6f); PixelStackSound.play(PixelStackSound.SPARKLE, .3f, 1.7f); GameKit.tick(a, .4f);
        if (glitchesLeft() == 0) openPortal();
    }

    void blastMark(Glitch g, long now) {
        if (!g.alive || g.boss || g.mini) return;
        if (angel() >= 6) { g.stunUntil = now + 1200; burst(px(g.x), py(g.y), 0xFFFFE9A8, 6, 120, 3); }
        else if (demon() >= 6 && !fleeing(g) && g.loveUntil == 0) scorch(g, now, 1200 + 150 * (demon() - 6));
    }

    static final long SCORCH_TICK = 600, SCORCH_GROUND = 1300;
    void scorch(Glitch g, long now, long ms) {
        if (!g.alive || g.boss || g.type == 9) return;
        if (now >= g.scorchUntil) { g.scorchNext = now + SCORCH_TICK; burst(px(g.x), py(g.y), 0xFFFF7A3D, 6, 120, 3); }
        g.scorchUntil = Math.max(g.scorchUntil, now + ms);
    }
    void stepScorch(long now) {
        for (Glitch g : new ArrayList<Glitch>(glitches)) {
            if (g.scorchUntil == 0) continue;
            if (!g.alive || g.burnUntil > 0) { g.scorchUntil = 0; continue; }
            if (now >= g.scorchNext && g.scorchNext <= g.scorchUntil) { g.scorchNext += SCORCH_TICK; hurt(g, 1, 0xFFFF7A3D); burst(px(g.x), py(g.y), 0xFFFF9A3D, 5, 110, 3); }
            if (now > g.scorchUntil) g.scorchUntil = 0;
        }
    }

    static final long FORM_MS = 700;
    void transform(Glitch g, long now) {
        if (g.form == 1 || !g.alive) return;
        g.form = 1; g.formAt = now; g.maxHp += 3; g.hp = Math.min(g.maxHp, g.hp + 3); g.sp *= 1.3f;
        float x = px(g.x), y = py(g.y);
        burst(x, y, 0xFFFF5A1F, 22, 280, 5); burst(x, y, 0xFF3A0A10, 14, 220, 5);
        for (int i = 0; i < 10; i++) { GameKit.Bit b = spark(x, y, i % 2 == 0 ? 0xAA5A5A5A : 0xAA2A2A2A, 80, 7f); b.round = true; b.gravity = -.25f; b.decay = .9f; }
        shake(9); GameKit.rumble(a);
        PixelStackSound.play(PixelStackSound.BOOM, .5f, .5f); PixelStackSound.play(PixelStackSound.FIRE, .45f, .7f);
    }

    void drawDemon(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        float k = 1f;
        if (!g.alive) { k = 1 - GameKit.ease((now - g.deadAt) / 450f); if (k <= 0) return; }
        boolean fiend = g.form == 1; float ft = (now - g.formAt) / (float) FORM_MS, changing = ft >= 0 && ft < 1 ? ft : -1;
        if (fiend) s *= 1.22f;
        if (changing >= 0) s *= 1 + .25f * (float) Math.sin(Math.PI * changing);
        int body = fiend ? 0xFF2B0A10 : 0xFFD32F2F, dark = fiend ? 0xFF14060A : 0xFF7A1020;
        if (now < freezeUntil) body = GameKit.mix(body, 0xFFBFE3FF, .6f);
        if (now - g.hitAt < 140 || (changing >= 0 && (now / 60) % 2 == 0)) body = 0xFFFFFFFF;
        float wob = motion ? (float) Math.sin(g.phase) : 0;
        int save = c.save(); c.scale((1 + .05f * wob) * (g.alive ? 1 : 1 + (1 - k) * .5f), (1 - .05f * wob) * k, cx, cy + s * .4f);
        if (dizzy() && g.alive) c.rotate((float) Math.sin(g.spin) * 14, cx, cy + s * .4f);
        fill.setShader(null);
        if (fiend && g.alive) { float q = (float) (.5 + .5 * Math.sin(now / 90.0 + g.phase)); fill.setColor(GameKit.alpha(0xFFFF5A1F, fade * (.18f + .12f * q))); c.drawCircle(cx, cy, s * (.68f + .06f * q), fill); }
        if (changing >= 0) { stroke.setStrokeWidth(s * .1f * (1 - changing)); stroke.setColor(GameKit.alpha(0xFFFF9A3D, fade * (1 - changing))); c.drawCircle(cx, cy, s * (.4f + .7f * changing), stroke); }

        stroke.setStrokeWidth(Math.max(1.5f, s * .06f)); stroke.setColor(GameKit.alpha(dark, fade));
        android.graphics.Path tl = GameKit.path(); float sw = motion ? (float) Math.sin(now / 200.0 + g.phase) * s * .1f : 0;
        tl.moveTo(cx + s * .3f, cy + s * .25f); tl.quadTo(cx + s * .7f + sw, cy + s * .2f, cx + s * .62f, cy - s * .15f + sw); c.drawPath(tl, stroke);
        fill.setColor(GameKit.alpha(dark, fade)); android.graphics.Path tip = GameKit.path(); float tx = cx + s * .62f, ty = cy - s * .15f + sw; tip.moveTo(tx - s * .08f, ty + s * .02f); tip.lineTo(tx, ty - s * .14f); tip.lineTo(tx + s * .08f, ty + s * .02f); tip.close(); c.drawPath(tip, fill);

        float flap = motion ? (float) Math.sin(now / (fiend ? 110.0 : 150.0) + g.phase) * s * .08f : 0, ww = fiend ? 1.25f : 1f;
        fill.setColor(GameKit.alpha(fiend ? 0xFF1A0508 : 0xFF8E1A1A, fade));
        for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); float wx = cx + side * s * .32f, wy = cy - s * .12f; w.moveTo(wx, wy); w.lineTo(wx + side * s * .5f * ww, wy - s * .38f * ww - flap); w.lineTo(wx + side * s * .42f * ww, wy - s * .06f); w.lineTo(wx + side * s * .58f * ww, wy + s * .08f + flap); w.lineTo(wx + side * s * .3f, wy + s * .1f); w.lineTo(wx, wy + s * .2f); w.close(); c.drawPath(w, fill); }

        android.graphics.Path p = GameKit.path(); float l = cx - s * .42f, r = cx + s * .42f, t = cy - s * .45f, b = cy + s * .38f;
        p.moveTo(l, b); p.lineTo(l, t + s * .35f); p.cubicTo(l, t - s * .05f, r, t - s * .05f, r, t + s * .35f); p.lineTo(r, b);
        for (int i = 0; i < 3; i++) { float x0 = r - (r - l) * (i + .5f) / 3f, x1 = r - (r - l) * (i + 1) / 3f; p.lineTo(x0, b + s * .14f); p.lineTo(x1, b); }
        p.close(); fill.setColor(GameKit.alpha(body, fade)); c.drawPath(p, fill);
        if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x88FFFFFF, fade)); c.drawPath(p, stroke); }
        if (fiend) {
            float q = (float) (.5 + .5 * Math.sin(now / 120.0 + g.phase)); stroke.setStrokeWidth(Math.max(1.2f, s * .035f)); stroke.setColor(GameKit.alpha(0xFFFF7A3D, fade * (.6f + .4f * q)));
            android.graphics.Path cr = GameKit.path(); cr.moveTo(cx - s * .28f, cy + s * .05f); cr.lineTo(cx - s * .16f, cy + s * .14f); cr.lineTo(cx - s * .2f, cy + s * .28f); cr.moveTo(cx + s * .26f, cy - s * .02f); cr.lineTo(cx + s * .14f, cy + s * .12f); cr.lineTo(cx + s * .22f, cy + s * .26f); c.drawPath(cr, stroke);
        }

        fill.setColor(GameKit.alpha(fiend ? 0xFF14060A : 0xFFF3E5C8, fade));
        for (int side = -1; side <= 1; side += 2) {
            android.graphics.Path h = GameKit.path(); float hb = cx + side * s * .2f;
            if (!fiend) { h.moveTo(hb - side * s * .06f, t + s * .08f); h.lineTo(hb + side * s * .12f, t - s * .2f); h.lineTo(hb + side * s * .08f, t + s * .1f); }
            else { h.moveTo(hb - side * s * .08f, t + s * .1f); h.cubicTo(hb + side * s * .1f, t - s * .3f, hb + side * s * .4f, t - s * .25f, hb + side * s * .36f, t - s * .45f); h.cubicTo(hb + side * s * .3f, t - s * .2f, hb + side * s * .14f, t - s * .1f, hb + side * s * .1f, t + s * .12f); }
            h.close(); c.drawPath(h, fill);
            if (fiend) { float fx = hb + side * s * .36f, fy = t - s * .45f, fl = s * (.1f + .04f * (float) Math.sin(now / 60.0 + side)); fill.setColor(GameKit.alpha(0xFFFF9A3D, fade)); android.graphics.Path f = GameKit.path(); f.moveTo(fx - s * .05f, fy + s * .03f); f.quadTo(fx, fy - fl * 1.8f, fx + s * .05f, fy + s * .03f); f.close(); c.drawPath(f, fill); fill.setColor(GameKit.alpha(0xFF14060A, fade)); }
        }

        float lx = DX[g.dir] * s * .06f, ly = DY[g.dir] * s * .04f;
        int iris = fiend ? 0xFFFF9A3D : 0xFFFFE066;
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * s * .15f + lx, y = cy - s * .06f + ly;
            if (fiend) { fill.setColor(GameKit.alpha(0xFFFF9A3D, fade * .35f)); c.drawCircle(x, y, s * .13f, fill); }
            android.graphics.Path e = GameKit.path(); e.moveTo(x - side * s * .11f, y - s * .07f); e.lineTo(x + side * s * .1f, y + s * .02f); e.quadTo(x, y + s * .1f, x - side * s * .11f, y - s * .07f); e.close();
            fill.setColor(GameKit.alpha(iris, fade)); c.drawPath(e, fill);
            fill.setColor(GameKit.alpha(0xFF14060A, fade)); c.drawOval(GameKit.rf(x - s * .018f, y - s * .05f, x + s * .018f, y + s * .07f), fill);
        }

        float my = cy + s * .16f, mw = s * (fiend ? .22f : .16f);
        android.graphics.Path m = GameKit.path(); m.moveTo(cx - mw, my - s * .04f); m.quadTo(cx, my + s * .02f, cx + mw, my - s * .04f); m.quadTo(cx, my + s * .16f, cx - mw, my - s * .04f); m.close();
        fill.setColor(GameKit.alpha(0xFF2A0508, fade)); c.drawPath(m, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); int n = fiend ? 5 : 2;
        for (int i = 0; i < n; i++) { float tt = .2f + .6f * (i + .5f) / n, tx2 = cx - mw + 2 * mw * tt, ty2 = (my - s * .04f) * ((1 - tt) * (1 - tt) + tt * tt) + 2 * (1 - tt) * tt * (my + s * .02f); android.graphics.Path th = GameKit.path(); th.moveTo(tx2 - s * .025f, ty2); th.lineTo(tx2 + s * .025f, ty2); th.lineTo(tx2, ty2 + s * .07f); th.close(); c.drawPath(th, fill); }
        if (g.alive && now - g.zapAt < 260) xray(c, cx, cy, s, l, r, t, b, now, fade, false);
        c.restoreToCount(save);
        if (g.alive) healthBar(c, cx, cy - s * (fiend ? .82f : .7f), s * .8f, g.hp, g.maxHp, fade);
        if (g.alive && fiend && motion && rnd.nextFloat() < .25f) { GameKit.Bit e2 = spark(cx + (rnd.nextFloat() - .5f) * s * .6f, cy - s * .2f, rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A, 30, 2.6f); e2.round = true; e2.gravity = -.4f; e2.decay = 1.8f; }
    }

    void possessedWings(Canvas c, float cx, float cy, float s, long now, float fade, float phase) {
        float flap = motion ? (float) Math.sin(now / 170.0 + phase) * s * .06f : 0;
        for (int side = -1; side <= 1; side += 2) {
            android.graphics.Path w = GameKit.path(); float wx = cx + side * s * .36f, wy = cy - s * .08f;
            w.moveTo(wx, wy); w.quadTo(wx + side * s * .45f, wy - s * .42f - flap, wx + side * s * .5f, wy + s * .02f); w.quadTo(wx + side * s * .32f, wy + s * .24f + flap, wx, wy + s * .16f); w.close();
            fill.setColor(GameKit.alpha(0xFFF4F6FF, fade)); c.drawPath(w, fill);
            fill.setColor(GameKit.alpha(0xFF2A1A2E, fade * .85f)); c.drawCircle(wx + side * s * .46f, wy - s * .02f, s * .07f, fill);
        }
    }

    void possessedFace(Canvas c, Glitch g, float cx, float cy, float s, float lx, float ly, long now, float fade) {
        boolean free = motion && ((now + (long) (g.phase * 1000)) % 3200) < 240;
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * s * .15f + lx, y = cy - s * .05f + ly;
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, y, s * .1f, fill);
            if (free) { fill.setColor(GameKit.alpha(0xFF4F8DF5, fade)); c.drawCircle(x + lx * .5f, y + ly * .5f, s * .055f, fill); }
            else {
                fill.setColor(GameKit.alpha(0xFFFF2D55, fade * .35f)); c.drawCircle(x, y, s * .14f, fill);
                fill.setColor(GameKit.alpha(0xFFE5173F, fade)); c.drawCircle(x + lx * .5f, y + ly * .5f, s * .06f, fill);
                stroke.setStrokeWidth(Math.max(1.2f, s * .04f)); stroke.setColor(GameKit.alpha(0xFF2A1A2E, fade)); c.drawLine(x - side * s * .12f, y - s * .14f, x + side * s * .08f, y - s * .08f, stroke);
            }
        }
        stroke.setStrokeWidth(Math.max(1.5f, s * .04f)); stroke.setColor(GameKit.alpha(0xFF2A1A2E, fade));
        if (free) c.drawArc(GameKit.rf(cx - s * .08f, cy + s * .12f, cx + s * .08f, cy + s * .24f), 200, 140, false, stroke);
        else { android.graphics.Path m = GameKit.path(); m.moveTo(cx - s * .13f, cy + s * .14f); m.quadTo(cx - s * .02f, cy + s * .22f, cx + s * .14f, cy + s * .1f); c.drawPath(m, stroke); }
    }

    void drawLightBlob(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        float t = (now - g.deadAt) / 650f; if (t >= 1 || t < 0) return;
        float e = GameKit.ease(t), a0 = fade * (1 - GameKit.easeIn(t));
        fill.setShader(null);
        fill.setColor(GameKit.alpha(0xFFBFF4FF, a0 * .35f)); c.drawCircle(cx, cy, s * (.5f + 1.1f * e), fill);
        fill.setColor(GameKit.alpha(0xFFFFF6D0, a0 * .7f)); c.drawCircle(cx, cy, s * (.45f + .55f * e), fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, a0)); c.drawCircle(cx, cy, s * (.4f + .2f * e) * (1 - t * .6f), fill);
        int sv = c.save(); c.rotate(t * 90, cx, cy); stroke.setStrokeWidth(Math.max(1.5f, s * .06f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, a0));
        for (int i = 0; i < 8; i++) { double ang = i * Math.PI / 4; float r0 = s * (.5f + .4f * e), r1 = s * (.8f + 1.1f * e) * (i % 2 == 0 ? 1 : .7f); c.drawLine(cx + (float) Math.cos(ang) * r0, cy + (float) Math.sin(ang) * r0, cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1, stroke); }
        c.restoreToCount(sv);
    }

    void drawAsh(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        float t = (now - g.deadAt) / 700f; if (t >= 1 || t < 0) return;
        float a0 = fade * (1 - GameKit.easeIn(t)), sag = GameKit.ease(t);
        fill.setShader(null); fill.setColor(GameKit.alpha(GameKit.mix(0xFFFFE9A8, 0xFF3A3A3A, Math.min(1f, t * 3)), a0));
        for (int i = 0; i < 9; i++) { float fx = cx + ((i % 3) - 1) * s * .26f, fy = cy + ((i / 3) - 1) * s * .24f + sag * s * (.3f + .12f * (i % 4)), q = s * .14f * (1 - sag * .6f); c.drawRect(fx - q, fy - q, fx + q, fy + q, fill); }
        if (t < .25f) { fill.setColor(GameKit.alpha(0xFFFFF6D0, fade * (1 - t * 4) * .7f)); c.drawCircle(cx, cy, s * .5f, fill); }
    }

    void soulSign(Canvas c, float x, float y, float r, int kind, int lv, long now, float fade) {
        fill.setShader(null); int tier = lv >= TOP ? 3 : lv >= 7 ? 2 : lv >= 4 ? 1 : 0;
        if (kind == ANGEL) {
            if (tier >= 2) {
                int sv = c.save(); c.rotate(motion ? now / 20f % 360 : 0, x, y); fill.setColor(GameKit.alpha(0xFFFFC531, fade));
                for (int i = 0; i < 8; i++) { android.graphics.Path ray = GameKit.path(); ray.moveTo(x - r * .12f, y - r * .4f); ray.lineTo(x, y - r * .95f); ray.lineTo(x + r * .12f, y - r * .4f); ray.close(); c.drawPath(ray, fill); c.rotate(45, x, y); }
                c.restoreToCount(sv); c.drawCircle(x, y, r * .45f, fill);
            }
            if (tier >= 1) { fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); w.moveTo(x + side * r * .25f, y); w.quadTo(x + side * r * .95f, y - r * .7f, x + side * r * 1f, y + r * .05f); w.quadTo(x + side * r * .6f, y + r * .45f, x + side * r * .25f, y + r * .25f); w.close(); c.drawPath(w, fill); } }
            if (tier == 3) { android.graphics.Path z = GameKit.path(); z.moveTo(x + r * .12f, y - r * .55f); z.lineTo(x - r * .18f, y + r * .05f); z.lineTo(x + r * .02f, y + r * .05f); z.lineTo(x - r * .12f, y + r * .55f); z.lineTo(x + r * .2f, y - r * .08f); z.lineTo(x, y - r * .08f); z.close(); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawPath(z, fill); }
            stroke.setStrokeWidth(Math.max(1f, r * .16f)); stroke.setColor(GameKit.alpha(tier >= 2 ? 0xFFFFFFFF : 0xFFFFC531, fade));
            if (tier < 2) c.drawOval(GameKit.rf(x - r * .45f, y - r * .25f, x + r * .45f, y + r * .1f), stroke);
            else c.drawOval(GameKit.rf(x - r * .36f, y - r * 1.05f, x + r * .36f, y - r * .8f), stroke);
        } else {
            int hornC = 0xFFE5173F;
            if (tier == 3) {
                for (int i = 0; i < 10; i++) { double ang = i * Math.PI / 5 + (motion ? now / 300.0 : 0); float fx = x + (float) Math.cos(ang) * r * .82f, fy = y + (float) Math.sin(ang) * r * .82f, fl = r * (.22f + .08f * (float) Math.sin(now / 60.0 + i)); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFFF7A3D : 0xFFFFC94A, fade)); c.drawCircle(fx, fy, fl, fill); }
            }
            fill.setColor(GameKit.alpha(hornC, fade));
            for (int side = -1; side <= 1; side += 2) { android.graphics.Path h = GameKit.path(); float hb = x + side * r * .3f, ht = tier >= 2 ? r * .95f : r * .8f; h.moveTo(hb - side * r * .14f, y - r * .25f); h.quadTo(hb + side * r * .3f, y - r * .45f, hb + side * r * .32f, y - ht); h.quadTo(hb + side * r * .12f, y - r * .5f, hb + side * r * .14f, y - r * .1f); h.close(); c.drawPath(h, fill); }
            if (tier == 0) { fill.setColor(GameKit.alpha(0xFFFF5A1F, fade)); c.drawCircle(x, y + r * .1f, r * .2f, fill); }
            else if (tier == 1 || tier == 2) {
                int eye = tier == 2 ? 0xFFE5173F : 0xFFFFE066;
                if (tier == 2) { fill.setColor(GameKit.alpha(0xFFF3E5C8, fade)); c.drawCircle(x, y, r * .5f, fill); c.drawRect(x - r * .26f, y + r * .2f, x + r * .26f, y + r * .5f, fill); }
                fill.setColor(GameKit.alpha(eye, fade));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path e = GameKit.path(); float ex = x + side * r * .2f, ey = y - r * .05f; e.moveTo(ex - side * r * .16f, ey - r * .08f); e.lineTo(ex + side * r * .14f, ey + r * .06f); e.lineTo(ex - side * r * .06f, ey + r * .1f); e.close(); c.drawPath(e, fill); }
                stroke.setStrokeWidth(Math.max(1f, r * .1f)); stroke.setColor(GameKit.alpha(tier == 2 ? 0xFF14060A : 0xFFFFE066, fade));
                android.graphics.Path m = GameKit.path(); m.moveTo(x - r * .3f, y + r * .2f); for (int i = 1; i <= 6; i++) m.lineTo(x - r * .3f + r * .1f * i, y + r * (i % 2 == 0 ? .2f : .32f)); c.drawPath(m, stroke);
            } else {
                android.graphics.Path e = GameKit.path(); e.moveTo(x - r * .6f, y); e.quadTo(x, y - r * .5f, x + r * .6f, y); e.quadTo(x, y + r * .5f, x - r * .6f, y); e.close();
                fill.setColor(GameKit.alpha(0xFFFFB347, fade)); c.drawPath(e, fill);
                fill.setColor(GameKit.alpha(0xFF14060A, fade)); c.drawOval(GameKit.rf(x - r * .07f, y - r * .3f, x + r * .07f, y + r * .3f), fill);
            }
        }
    }

    void featherWing(Canvas c, float x, float y, int side, float len, float fromDeg, float toDeg, int n, float fade, boolean goldTips, float flapDeg) {
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : i / (float) (n - 1), a = fromDeg + (toDeg - fromDeg) * t + flapDeg, L = len * (1 - .35f * t);
            int sv = c.save(); c.translate(x, y); c.scale(side, 1); c.rotate(a);
            fill.setColor(GameKit.alpha(0xFFFFFDF5, fade)); c.drawOval(GameKit.rf(0, -L * .12f, L, L * .12f), fill);
            stroke.setStrokeWidth(Math.max(.8f, L * .025f)); stroke.setColor(GameKit.alpha(0xFFE2C06A, fade * .8f)); c.drawOval(GameKit.rf(0, -L * .12f, L, L * .12f), stroke);
            fill.setColor(GameKit.alpha(goldTips ? 0xFFF5CF5A : 0xFFBFE0FF, fade)); c.drawOval(GameKit.rf(L * .76f, -L * .075f, L * .99f, L * .075f), fill);
            c.restoreToCount(sv);
        }
    }

    void holyEye(Canvas c, float x, float y, int side, float s, int an, long now, float fade, boolean laser) {
        float tw = motion ? .5f + .5f * (float) Math.sin(now / 90.0 + side) : .5f;
        fill.setColor(GameKit.alpha(0xFFFFE082, fade * (.22f + .02f * an + .1f * tw))); c.drawCircle(x, y, s * (.2f + .01f * an), fill);
        fill.setColor(GameKit.alpha(0xFFFFF6D0, fade)); c.drawOval(GameKit.rf(x - s * .12f, y - s * .1f, x + s * .12f, y + s * .1f), fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, y, s * .06f, fill);
        float st = s * (laser ? .22f + .02f * an : .14f + .012f * an) * (.85f + .3f * tw);
        int sv = c.save(); c.rotate(motion ? (now / 25f) % 90 : 0, x, y);
        android.graphics.Path p = GameKit.path(); p.moveTo(x, y - st); p.lineTo(x + st * .12f, y - st * .12f); p.lineTo(x + st, y); p.lineTo(x + st * .12f, y + st * .12f); p.lineTo(x, y + st); p.lineTo(x - st * .12f, y + st * .12f); p.lineTo(x - st, y); p.lineTo(x - st * .12f, y - st * .12f); p.close();
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * .9f)); c.drawPath(p, fill);
        c.restoreToCount(sv);
        stroke.setStrokeWidth(Math.max(1.2f, s * .045f)); stroke.setColor(GameKit.alpha(an >= 7 ? 0xFFC9A23A : 0xFF14161C, fade));
        c.drawLine(x - side * s * .13f, y - s * .13f, x + side * s * .11f, y - s * .17f, stroke);
    }
    void demonEye(Canvas c, float x, float y, int side, float s, int dm, float ex, float ey, long now, float fade) {
        float w = s * (.12f + .004f * dm), h = s * (.075f + .003f * dm), glow = motion ? (float) (.5 + .5 * Math.sin(now / 120.0 + side)) : .5f;
        int iris = dm >= 8 ? 0xFFFF3D2E : dm >= 4 ? 0xFFFF9A3D : 0xFFFFE066;
        fill.setColor(GameKit.alpha(iris, fade * (.14f + .02f * dm) * (.7f + .6f * glow))); c.drawCircle(x, y, s * (.17f + .006f * dm), fill);
        android.graphics.Path e = GameKit.path();
        e.moveTo(x - side * w, y - h * .9f); e.lineTo(x + side * w * .95f, y + h * .35f); e.quadTo(x, y + h * 1.25f, x - side * w, y - h * .9f); e.close();
        fill.setColor(GameKit.alpha(iris, fade)); c.drawPath(e, fill);
        fill.setColor(GameKit.alpha(0xFF14060A, fade)); c.drawOval(GameKit.rf(x + ex * .5f - s * .022f, y + ey * .3f - h * .55f, x + ex * .5f + s * .022f, y + ey * .3f + h * .75f), fill);
        stroke.setStrokeWidth(s * (.06f + .004f * dm)); stroke.setColor(GameKit.alpha(0xFF14060A, fade)); c.drawLine(x - side * s * .16f, y - s * .14f, x + side * s * .13f, y - s * .01f, stroke);
    }

    void demonMouth(Canvas c, float cx, float my, float s, int dm, float ex, long now, float fade) {
        float mw = s * (.15f + .012f * dm), mh = s * (.07f + .007f * dm), x0 = cx + ex * .4f, top = my - mh * .6f;
        android.graphics.Path m = GameKit.path();
        m.moveTo(x0 - mw, top); m.quadTo(x0, my + mh * .2f, x0 + mw, top); m.quadTo(x0, my + mh * 1.9f, x0 - mw, top); m.close();
        fill.setColor(GameKit.alpha(0xFF2A0508, fade)); c.drawPath(m, fill);
        if (dm >= 5) { fill.setColor(GameKit.alpha(0xFFFF5A1F, fade * (.45f + .15f * (float) Math.sin(now / 80.0)))); c.drawOval(GameKit.rf(x0 - mw * .45f, my + mh * .35f, x0 + mw * .45f, my + mh * 1.1f), fill); }
        int n = Math.min(12, 2 + dm), up = (n + 1) / 2, dn = n / 2;
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade));
        for (int i = 0; i < up; i++) { float t = .1f + .8f * (i + .5f) / up, tx = x0 - mw + 2 * mw * t, ty = top * ((1 - t) * (1 - t) + t * t) + 2 * (1 - t) * t * (my + mh * .2f), bw = mw * .8f / up, tl = mh * ((i == 0 || i == up - 1) && dm >= 6 ? .9f : .55f); android.graphics.Path th = GameKit.path(); th.moveTo(tx - bw, ty - 1); th.lineTo(tx + bw, ty - 1); th.lineTo(tx, ty + tl); th.close(); c.drawPath(th, fill); }
        for (int i = 0; i < dn; i++) { float t = .18f + .64f * (i + .5f) / dn, tx = x0 - mw + 2 * mw * t, ty = top * ((1 - t) * (1 - t) + t * t) + 2 * (1 - t) * t * (my + mh * 1.9f), bw = mw * .62f / dn; android.graphics.Path th = GameKit.path(); th.moveTo(tx - bw, ty + 1); th.lineTo(tx + bw, ty + 1); th.lineTo(tx, ty - mh * .45f); th.close(); c.drawPath(th, fill); }
    }

    static final class Gem { int x, y, hue; long at; float pull; boolean tapped; }
    final ArrayList<Gem> gems = new ArrayList<Gem>();
    int gemCount, gemRun; long gemAt = -10000;
    static final int[] GEM = {0xFF4FD8FF, 0xFFFF7FC8, 0xFFFFD23F, 0xFF8BF07A};
    static final class Flame { int x, y, bomb; long at; int shape; boolean safe; }
    static final class Glitch { float x, y, sp; int dir, type, tier, hp, maxHp, lastBomb = -1; boolean alive = true, boss, mini, split, melted, gibbed; long deadAt = -1, hitAt = -10000, burnUntil, loveUntil; float phase, think, burnCool, gasCool, spin;
        int bossKind; float mood; boolean rush; long blinkAt = -10000, slamAt = -10000; float slamCool, teleCool; float fromX, fromY;
        int look, face; long zapAt = -10000, doomAt;
        long shockUntil, stunUntil, formAt = -10000; int form; float formCool, trailCool; boolean lit, ashed, freed; long scorchUntil, scorchNext;
        float hazCool = 3f, chargeCool = 4f, shieldCool = 6f, healCool = 3f, dropCool = 3f, spitCool = 2f; int hazTurn, splits, lastX = -1, lastY = -1; long chargeUntil, shieldUntil, bubbleAt = -10000; boolean bubble, hidden; int[] hitBy = {-1, -1, -1, -1, -1, -1, -1, -1}; int hitN; }

    static final class Gas { int x, y; long at; }
    static final class Prize { int x, y, kind; long at; float pull; boolean tapped; }

    final ArrayList<Bomb> bombs = new ArrayList<Bomb>();
    final ArrayList<Flame> flames = new ArrayList<Flame>();
    final ArrayList<Glitch> glitches = new ArrayList<Glitch>();
    final ArrayList<Prize> prizes = new ArrayList<Prize>();
    final ArrayList<Gas> gas = new ArrayList<Gas>();
    int bombIds; float breathCool;
    long poisonUntil, breathUntil, shieldUntil, freezeUntil, doubleUntil, laserUntil, dizzyUntil, beamAt = -10000, fartAt = -10000, freezeAt = -10000;
    float beamX, beamY, laserCool, fireSound, meltCool, laserSound; int mood;
    static final int P_BOMB = 1, P_FIRE = 2, P_SPEED = 3, P_LIFE = 4, P_POISON = 5, P_BREATH = 6, P_SHIELD = 7, P_FREEZE = 8, P_DOUBLE = 10, P_LASER = 11, P_FART = 12;
    boolean dizzy() { return SystemClock.uptimeMillis() < dizzyUntil; }
    static final int P_KICK = 13, P_RAIN = 14, P_MEGA = 15;
    boolean kick; int megaLeft;
    int kills, rank, combo; long rankAt = -10000, lastKillAt = -10000, comboAt = -10000;

    static final int[] RANKS = {4, 9, 15, 22, 30, 39, 49, 60, 72};
    long overBrokenAt = -100000;
    boolean overshield(long now) { return rank >= 5 && now - overBrokenAt > (rank >= 9 ? 10000 : 20000); }
    int wardStyle() { return soul == ANGEL && soulLv > 0 ? WARD_HOLY : soul == DEMON && soulLv > 0 ? WARD_HELL : WARD_PLAIN; }

    boolean hitBy(Glitch g, int bomb) { if (g.hitBy == null) { g.hitBy = new int[]{-1, -1, -1, -1, -1, -1, -1, -1}; g.hitN = 0; } for (int v : g.hitBy) if (v == bomb) return true; return false; }
    void markHit(Glitch g, int bomb) { hitBy(g, bomb); g.hitBy[(g.hitN++ & 0x7FFFFFFF) % g.hitBy.length] = bomb; g.lastBomb = bomb; }

    final ArrayList<float[]> rain = new ArrayList<float[]>();

    final ArrayList<Glitch> born = new ArrayList<Glitch>();
    void addScore(int n) { score += SystemClock.uptimeMillis() < doubleUntil ? n * 2 : n; }
    boolean breathing() { return heroAlive && SystemClock.uptimeMillis() < breathUntil; }
    float hx, hy; int face = 2; float walk; boolean heroAlive; long heroBornAt, heroDeadAt = -1, safeUntil;
    long portalAt;

    static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
    int pillar, edge, crate, crate2, floorA, floorB; final int floorA0, floorB0;

    static final int[][] THEMES = {
        {GameKit.BLUE, 0xFF0D2E8A, 0xFFBCD4FF, 0xFF9EC1FF, 0xFF2C5BD6, 0xFF1A2F6E, 0xFF9EC1FF, 0xFF7FA6F5},
        {0xFF13897A, 0xFF0B5B52, 0xFFB5EDE2, 0xFF86DCCB, 0xFF18A08F, 0xFF0B5B52, 0xFF86DCCB, 0xFF5CC4B1},
        {0xFF6A3FD6, 0xFF3E2391, 0xFFDCCEFF, 0xFFBFA8FF, 0xFF7B52E8, 0xFF3E2391, 0xFFBFA8FF, 0xFF9E83F2},
        {0xFFE0663A, 0xFF9C3A1E, 0xFFFFDCBA, 0xFFFFBE8C, 0xFFE8743F, 0xFF9C3A1E, 0xFFFFBE8C, 0xFFF59A60},
        {0xFF2E9E4F, 0xFF1C6633, 0xFFCDF2D1, 0xFFA3E3AB, 0xFF34B259, 0xFF1C6633, 0xFFA3E3AB, 0xFF78CF86},
        {0xFFD9467F, 0xFF8E1F4C, 0xFFFFD1E3, 0xFFFFADCB, 0xFFE5578E, 0xFF8E1F4C, 0xFFFFADCB, 0xFFF587B0}};
    void theme() {
        int[] t = THEMES[(stage - 1) % THEMES.length]; int o = night ? 4 : 0;
        pillar = t[o]; edge = t[o + 1]; crate = t[o + 2]; crate2 = t[o + 3];
        floorA = floorA0; floorB = floorB0;
        int[] w = GameKit.WORLDS[GameKit.world(stage)];
        if (w != null) { floorA = ground(w[0]); floorB = ground(w[1]); crate = w[2]; crate2 = w[3]; pillar = w[4]; edge = w[5]; }
        fieldDirty = true;
    }

    PixelBlast(Activity a, RectF home, RectF[] from, int ink, int page, boolean dark, boolean black) {
        super(a, home, from, ink, page, dark, black, "pixelblast", new int[]{0xFFF28C28, 0xFFF2C230, 0xFFF2C230, 0xFFF28C28});
        boolean n = dark || black;
        floorA = floorA0 = black ? 0xFF000000 : n ? GameKit.mix(page | 0xFF000000, 0xFFFFFFFF, .07f) : GameKit.mix(page | 0xFF000000, 0xFFFFFFFF, .75f);
        floorB = floorB0 = black ? 0xFF0B0B0F : n ? GameKit.mix(page | 0xFF000000, 0xFFFFFFFF, .1f) : GameKit.mix(page | 0xFF000000, 0xFFFFFFFF, .6f);
    }

    @Override int arcadeId() { return ArcadePreview.BLAST; }
    @Override String shareKey() { return "blast"; }
    @Override String title() { return I18n.t("Pixel Blast"); }
    @Override String hint() { return I18n.t("Drag to move, tap to drop a bomb"); }

    @Override void layout(int w, int h) {
        fieldDirty = true;
        if (land) {
            float side = 150 * d;
            cell = Math.min((h - 8 * d) / H, (w - leftInset - rightInset - 2 * side) / W);
            float bw = cell * W, bh = cell * H, cx = (leftInset + w - rightInset) / 2f, cy = h / 2f;
            board.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
            placeControlsLand(board);
            return;
        }
        cell = Math.min((w - 8 * d) / W, (h - topInset - barH - bottomInset) * .6f / H);
        float bw = cell * W, bh = cell * H, top = topInset + barH + 64 * d;
        board.set((w - bw) / 2f, top, (w + bw) / 2f, top + bh);
        placeControls(board.bottom + 10 * d);
    }

    final float[] scaleBuf = new float[W * H];
    final RectF startRect = new RectF(); final RectF[] startSq = new RectF[4];

    float px(float x) { return board.left + x * cell; }
    float py(float y) { return board.top + y * cell; }

    static final String[] BOSS_NAMES = {"Glitch King", "Crate Golem", "Phantom Twins", "Bomb Baron", "Frost Wraith", "Slime Lord", "Storm Eye", "Iron Mech", "Shadow Twins", "Magma Golem",
        "Hex Witch", "Hydra Slime", "Grave King", "Thunder Mech", "The Watcher", "Crystal Golem", "Banshee", "Toxic Slime", "Demon Twins", "Glitch Emperor"};
    static final int BK_SUMMON = 1, BK_SLAM = 2, BK_BLINK = 4, BK_TWIN = 8, BK_CROSS = 16, BK_RING = 32, BK_RAIN = 64, BK_LINE = 128, BK_CHARGE = 256, BK_SHIELD = 512, BK_HEAL = 1024, BK_SPLIT = 2048;
    static final int HZ_FIRE = 0, HZ_ICE = 1, HZ_VOID = 2, HZ_BOLT = 3;

    static final int[] BOSS_BODY = {0, 1, 2, 0, 3, 4, 5, 6, 2, 1, 3, 4, 0, 6, 5, 1, 3, 4, 2, 0};
    static final int[] BOSS_MOVES = {BK_SUMMON, BK_SLAM, BK_BLINK | BK_TWIN, BK_RAIN | BK_CROSS, BK_LINE | BK_RING, BK_SPLIT | BK_CHARGE, BK_CROSS | BK_LINE, BK_CHARGE | BK_CROSS, BK_BLINK | BK_TWIN | BK_RAIN, BK_SLAM | BK_RING,
        BK_BLINK | BK_LINE, BK_SUMMON | BK_RING | BK_SPLIT, BK_SUMMON | BK_SHIELD | BK_CROSS, BK_LINE | BK_CHARGE | BK_RAIN, BK_BLINK | BK_RAIN | BK_RING, BK_SLAM | BK_SHIELD | BK_RING, BK_CHARGE | BK_LINE | BK_BLINK, BK_RAIN | BK_HEAL | BK_SPLIT, BK_BLINK | BK_TWIN | BK_CROSS | BK_CHARGE,
        BK_SUMMON | BK_CROSS | BK_RING | BK_RAIN | BK_SHIELD | BK_LINE};
    static final int[] BOSS_ELEM = {HZ_FIRE, HZ_FIRE, HZ_VOID, HZ_FIRE, HZ_ICE, HZ_VOID, HZ_BOLT, HZ_FIRE, HZ_VOID, HZ_FIRE, HZ_VOID, HZ_VOID, HZ_VOID, HZ_BOLT, HZ_VOID, HZ_ICE, HZ_ICE, HZ_VOID, HZ_FIRE, HZ_BOLT};
    static final int[] BOSS_INK = {0xFF8E2BB6, 0xFF8A7560, 0xFF35D0E0, 0xFFFF7A3D, 0xFF7FD6FF, 0xFF4CAF50, 0xFFFFD23F, 0xFF78909C, 0xFF5E35B1, 0xFFE5532B, 0xFFAB47BC, 0xFF26A69A, 0xFF546E7A, 0xFF29B6F6, 0xFFEC407A, 0xFF80DEEA, 0xFFCFD8DC, 0xFF9CCC65, 0xFFC62828, 0xFFFFC107};
    static final int[] BOSS_GLOW = {0xFFB15CFF, 0xFFFF9A3D, 0xFFB15CFF, 0xFFFFE066, 0xFFE1F5FE, 0xFFB2FF59, 0xFFFFFFFF, 0xFFFF5252, 0xFFE040FB, 0xFFFFD23F, 0xFF69F0AE, 0xFF84FFFF, 0xFF76FF03, 0xFFFFEB3B, 0xFFFF1744, 0xFFFFFFFF, 0xFF4FC3F7, 0xFFEEFF41, 0xFFFFAB00, 0xFFFFFFFF};
    int bossSpec() { return Math.max(0, stage / 5 - 1) % 20; }
    boolean bossDoes(Glitch g, int move) { return g.boss && (BOSS_MOVES[g.bossKind % 20] & move) != 0; }

    static final class Hazard { int x, y, kind; long at, warn, live; boolean mark, hit; }
    final ArrayList<Hazard> hazards = new ArrayList<Hazard>();
    long slowUntil;
    void hazard(int x, int y, int kind, long warn, long live, boolean mark) {
        if (x < 1 || y < 1 || x >= W - 1 || y >= H - 1 || map[y][x] == PILLAR || hazards.size() > 180) return;
        Hazard h = new Hazard(); h.x = x; h.y = y; h.kind = kind; h.at = SystemClock.uptimeMillis(); h.warn = warn; h.live = live; h.mark = mark; hazards.add(h);
    }
    void hazCross(int sx, int sy, int range, int kind, long warn) {
        hazard(sx, sy, kind, warn, 450, true);
        for (int k = 0; k < 4; k++) for (int i = 1; i <= range; i++) { int x = sx + DX[k] * i, y = sy + DY[k] * i; if (x < 1 || y < 1 || x >= W - 1 || y >= H - 1 || map[y][x] == PILLAR) break; hazard(x, y, kind, warn, 450, false); if (map[y][x] == CRATE && crumbleAt[y][x] == 0) break; }
    }
    void hazRing(int sx, int sy, int r, int kind, long warn) { for (int y = sy - r; y <= sy + r; y++) for (int x = sx - r; x <= sx + r; x++) if (Math.max(Math.abs(x - sx), Math.abs(y - sy)) == r) hazard(x, y, kind, warn, 450, false); }
    void hazRain(int n, int kind, long warn) {
        int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy);
        hazard(cx, cy, kind, warn, 450, false);
        for (int i = 1; i < n; i++) hazard(cx + rnd.nextInt(7) - 3, cy + rnd.nextInt(7) - 3, kind, warn + rnd.nextInt(300), 450, false);
    }
    void hazLine(int sx, int sy, int dir, int kind, long warn, int max) { for (int i = 1; i <= max; i++) { int x = sx + DX[dir] * i, y = sy + DY[dir] * i; if (x < 1 || y < 1 || x >= W - 1 || y >= H - 1 || map[y][x] == PILLAR) break; hazard(x, y, kind, warn, 380, false); if (map[y][x] == CRATE && crumbleAt[y][x] == 0) break; } }
    void stepHazards(long now) {
        for (int i = hazards.size() - 1; i >= 0; i--) {
            Hazard h = hazards.get(i);
            if (now > h.at + h.warn + h.live) { hazards.remove(i); continue; }
            if (now < h.at + h.warn) continue;
            if (!h.hit) {
                h.hit = true;
                if (h.kind != HZ_ICE && map[h.y][h.x] == CRATE && crumbleAt[h.y][h.x] == 0) { crumbleAt[h.y][h.x] = now; fieldDirty = true; crateBits(h.x, h.y); }
                if (h.mark || rnd.nextFloat() < .25f) burst(px(h.x + .5f), py(h.y + .5f), hazInk(h.kind), 5, 120, 3);
                if (h.mark) { shake(5); PixelStackSound.play(h.kind == HZ_ICE ? PixelStackSound.TING : PixelStackSound.BOOM, .35f, h.kind == HZ_BOLT ? 1.5f : 1f); }
            }
            if (heroAlive && state == PLAY && (int) Math.floor(hx) == h.x && (int) Math.floor(hy) == h.y && now > safeUntil && now > shieldUntil) { if (h.kind == HZ_ICE) slowUntil = now + 1200; else die(); }
        }
    }
    static int hazInk(int kind) { return kind == HZ_ICE ? 0xFF7FE9FF : kind == HZ_VOID ? 0xFFB15CFF : kind == HZ_BOLT ? 0xFFFFE066 : 0xFFFF7A3D; }
    void drawHazards(Canvas c, long now) {
        fill.setShader(null);
        for (Hazard h : hazards) {
            float l = px(h.x), t = py(h.y), s = cell, cx = l + s / 2, cy = t + s / 2; int ink = hazInk(h.kind);
            if (now < h.at + h.warn) {
                float k = GameKit.clamp((now - h.at) / (float) Math.max(1, h.warn)), blink = (now / 110) % 2 == 0 ? 1f : .55f;
                fill.setColor(GameKit.alpha(ink, .08f + .18f * k)); c.drawRect(l + s * .06f, t + s * .06f, l + s * .94f, t + s * .94f, fill);
                stroke.setStrokeWidth(Math.max(1.2f, s * .05f)); stroke.setColor(GameKit.alpha(ink, (.45f + .45f * k) * blink)); float q = s * (.45f - .3f * k); c.drawRect(cx - q, cy - q, cx + q, cy + q, stroke);
                if (h.mark && h.kind == HZ_FIRE) { fill.setColor(night ? 0xFF3A4366 : 0xFF1B2440); c.drawCircle(cx, cy + s * .05f, s * .24f, fill); fill.setColor(0xFFFFC94A); c.drawCircle(cx + s * .14f, cy - s * .2f, s * (.06f + .04f * blink), fill); }
            } else {
                float life = (now - h.at - h.warn) / (float) Math.max(1, h.live), a0 = 1 - GameKit.easeIn(life);
                if (h.kind == HZ_FIRE) { fill.setColor(GameKit.alpha(0xFFE5532B, a0 * .85f)); c.drawRect(l, t, l + s, t + s, fill); fill.setColor(GameKit.alpha(0xFFFFB347, a0)); c.drawCircle(cx, cy, s * (.38f + .06f * (float) Math.sin(now / 40.0 + h.x)), fill); fill.setColor(GameKit.alpha(0xFFFFF1A8, a0)); c.drawCircle(cx, cy, s * .18f, fill); }
                else if (h.kind == HZ_ICE) { fill.setColor(GameKit.alpha(0xFFB3E5FC, a0 * .7f)); c.drawRect(l + s * .04f, t + s * .04f, l + s * .96f, t + s * .96f, fill); stroke.setStrokeWidth(Math.max(1f, s * .04f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .9f)); for (int k = 0; k < 3; k++) { double ang = k * Math.PI / 3; c.drawLine(cx - (float) Math.cos(ang) * s * .32f, cy - (float) Math.sin(ang) * s * .32f, cx + (float) Math.cos(ang) * s * .32f, cy + (float) Math.sin(ang) * s * .32f, stroke); } }
                else if (h.kind == HZ_VOID) { fill.setColor(GameKit.alpha(0xFF2A0845, a0 * .9f)); c.drawCircle(cx, cy, s * .48f, fill); stroke.setStrokeWidth(Math.max(1.5f, s * .07f)); stroke.setColor(GameKit.alpha(0xFFB15CFF, a0)); c.drawArc(GameKit.rf(cx - s * .32f, cy - s * .32f, cx + s * .32f, cy + s * .32f), now / 3f % 360, 250, false, stroke); }
                else { fill.setColor(GameKit.alpha(0xFFFFF59D, a0 * .5f)); c.drawRect(l, t, l + s, t + s, fill); bolt(c, cx, t, cx, t + s, s * .3f, 4, now / 40 + h.x * 7 + h.y, 0xFFFFE066, Math.max(1.5f, s * .08f), a0); }
            }
        }
    }

    void bossMoves(Glitch g, float dt, long now) {
        int sp = g.bossKind % 20, mv = BOSS_MOVES[sp], el = BOSS_ELEM[sp]; boolean angry = g.hp < g.maxHp / 2;
        int bx = (int) Math.floor(g.x), by = (int) Math.floor(g.y), hcx = (int) Math.floor(hx), hcy = (int) Math.floor(hy);
        int[] list = new int[4]; int n = 0;
        if ((mv & BK_CROSS) != 0) list[n++] = BK_CROSS; if ((mv & BK_RING) != 0) list[n++] = BK_RING; if ((mv & BK_RAIN) != 0) list[n++] = BK_RAIN; if ((mv & BK_LINE) != 0) list[n++] = BK_LINE;
        if (n > 0 && heroAlive) {
            g.hazCool -= dt;
            if (g.hazCool <= 0) {
                g.hazCool = (angry ? 2.3f : 3.3f) + rnd.nextFloat() * .6f;
                long warn = angry ? 650 : 850;
                switch (list[g.hazTurn++ % n]) {
                    case BK_CROSS: hazCross(bx, by, angry ? 5 : 4, el, warn); break;
                    case BK_RING: hazRing(bx, by, 2, el, warn); if (angry) hazRing(bx, by, 3, el, warn + 350); break;
                    case BK_RAIN: hazRain(angry ? 7 : 5, el, warn); break;
                    default: hazCross(hcx, hcy, 3, el, warn + 150); break;
                }
                PixelStackSound.play(PixelStackSound.RISE, .28f, el == HZ_ICE ? 1.6f : el == HZ_BOLT ? 1.4f : 1.05f);
            }
        }
        if ((mv & BK_CHARGE) != 0) {
            g.chargeCool -= dt;
            if (g.chargeCool <= 0 && heroAlive && (bx == hcx || by == hcy) && Math.abs(bx - hcx) + Math.abs(by - hcy) <= 8) { g.chargeCool = angry ? 3.8f : 5.5f; g.chargeUntil = now + 900; PixelStackSound.play(PixelStackSound.RISE, .4f, .7f); shake(4); }
            if (now < g.chargeUntil && heroAlive) { int want = bx == hcx ? (hcy > by ? 2 : 0) : (hcx > bx ? 1 : 3); if (!barred(g, bx + DX[want], by + DY[want])) g.dir = want; }
        }
        if ((mv & BK_SHIELD) != 0) { g.shieldCool -= dt; if (g.shieldCool <= 0 && now > g.shieldUntil) { g.shieldCool = angry ? 6.5f : 9f; g.shieldUntil = now + 2500; PixelStackSound.play(PixelStackSound.TING, .4f, .8f); } }
        if ((mv & BK_HEAL) != 0 && g.hp < g.maxHp) { g.healCool -= dt; if (g.healCool <= 0) { g.healCool = 2.6f; int add = Math.max(1, g.maxHp / 30); g.hp = Math.min(g.maxHp, g.hp + add); hitNumber(px(g.x), py(g.y) - cell * 1.05f, add, 0xFF7DE38A, false); burst(px(g.x), py(g.y), 0xFF7DE38A, 6, 120, 3); } }
    }

    void drawBossFx(Canvas c, long now) {
        fill.setShader(null);
        for (Glitch g : glitches) {
            if (!g.boss || !g.alive) continue;
            float cx = px(g.x), cy = py(g.y) - cell * .25f, s = cell * 1.7f * (.72f + .09f);
            if (now < g.shieldUntil) { float q = motion ? (float) (.5 + .5 * Math.sin(now / 90.0)) : .5f; fill.setColor(GameKit.alpha(0xFF4FD8FF, .12f + .08f * q)); c.drawCircle(cx, cy, s * .78f, fill); stroke.setStrokeWidth(Math.max(1.5f, cell * .07f)); stroke.setColor(GameKit.alpha(0xFF7FE9FF, .6f + .3f * q)); c.drawCircle(cx, cy, s * .78f, stroke); }
            if (now < g.chargeUntil) { stroke.setStrokeWidth(Math.max(1.5f, cell * .06f)); stroke.setColor(GameKit.alpha(BOSS_GLOW[g.bossKind % 20], .7f)); for (int k = -1; k <= 1; k++) { float ox = -DX[g.dir] * cell * .9f, oy = -DY[g.dir] * cell * .9f, sx = DY[g.dir] * k * cell * .25f, sy = DX[g.dir] * k * cell * .25f; c.drawLine(cx + sx, cy + sy, cx + ox * (1.2f + .3f * Math.abs(k)) + sx, cy + oy * (1.2f + .3f * Math.abs(k)) + sy, stroke); } }
        }
    }

    void drawWraith(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        int ink = now - g.hitAt < 120 ? 0xFFFFFFFF : BOSS_INK[g.bossKind % 20], glow = BOSS_GLOW[g.bossKind % 20], dark = GameKit.mix(ink, 0xFF000000, .55f);
        float wave = motion ? (float) Math.sin(now / 200.0 + g.phase) : 0, k = g.alive ? 1f : 1 - GameKit.ease((now - g.deadAt) / 500f); if (k <= 0) return;
        android.graphics.Path p = GameKit.path(); float top = cy - s * .55f, bot = cy + s * .5f;
        p.moveTo(cx, top); p.cubicTo(cx + s * .5f, top, cx + s * .5f, cy, cx + s * .55f, bot);
        for (int i = 0; i < 5; i++) { float x1 = cx + s * .55f - s * 1.1f * (i + 1) / 5f; p.lineTo(x1 + s * .11f, bot - s * (.12f + .06f * wave * (i % 2 == 0 ? 1 : -1))); p.lineTo(x1, bot); }
        p.cubicTo(cx - s * .5f, cy, cx - s * .5f, top, cx, top); p.close();
        fill.setShader(null); fill.setColor(GameKit.alpha(ink, fade * k * .92f)); c.drawPath(p, fill);
        fill.setColor(GameKit.alpha(dark, fade * k)); c.drawOval(GameKit.rf(cx - s * .28f, top + s * .14f, cx + s * .28f, cy + s * .02f), fill);
        for (int side = -1; side <= 1; side += 2) { fill.setColor(GameKit.alpha(glow, fade * k)); c.drawOval(GameKit.rf(cx + side * s * .12f - s * .06f, cy - s * .2f, cx + side * s * .12f + s * .06f, cy - s * .12f), fill); }
        stroke.setStrokeWidth(Math.max(1.5f, s * .04f)); stroke.setColor(GameKit.alpha(GameKit.mix(ink, 0xFFFFFFFF, .5f), fade * k)); c.drawArc(GameKit.rf(cx + s * .3f, cy - s * .6f, cx + s * .9f, cy + s * .1f), 200, 120, false, stroke); c.drawLine(cx + s * .45f, cy - s * .4f, cx + s * .5f, cy + s * .45f, stroke);
    }
    void drawSlime(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        int ink = now - g.hitAt < 120 ? 0xFFFFFFFF : BOSS_INK[g.bossKind % 20], glow = BOSS_GLOW[g.bossKind % 20];
        float wob = motion ? (float) Math.sin(now / 160.0 + g.phase) : 0, k = g.alive ? 1f : 1 - GameKit.ease((now - g.deadAt) / 500f); if (k <= 0) return;
        float w = s * (.6f + .05f * wob), h = s * (.5f - .05f * wob) * k, bot = cy + s * .45f;
        fill.setShader(null); fill.setColor(GameKit.alpha(ink, fade * .9f)); c.drawOval(GameKit.rf(cx - w, bot - h * 2, cx + w, bot + s * .05f), fill);
        fill.setColor(GameKit.alpha(GameKit.mix(ink, 0xFFFFFFFF, .4f), fade * .6f)); c.drawOval(GameKit.rf(cx - w * .55f, bot - h * 1.8f, cx - w * .1f, bot - h * 1.3f), fill);
        for (int q = 0; q < 3; q++) { float bx = cx + (q - 1) * w * .45f, by = bot - h * (.6f + .3f * ((now / 300 + q) % 3)); fill.setColor(GameKit.alpha(glow, fade * .5f)); c.drawCircle(bx, by, s * .05f, fill); }
        for (int side = -1; side <= 1; side += 2) { float ex = cx + side * w * .32f, ey = bot - h * 1.25f; fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(ex, ey, s * .11f, fill); fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawCircle(ex + (hx > g.x ? 1 : -1) * s * .03f, ey, s * .055f, fill); }
        if ((BOSS_MOVES[g.bossKind % 20] & BK_HEAL) != 0) { fill.setColor(GameKit.alpha(0xFF7DE38A, fade * .5f)); c.drawCircle(cx, bot - h * 2.1f, s * .08f, fill); }
    }
    void drawEye(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        int ink = now - g.hitAt < 120 ? 0xFFFFFFFF : BOSS_INK[g.bossKind % 20], glow = BOSS_GLOW[g.bossKind % 20];
        float k = g.alive ? 1f : 1 - GameKit.ease((now - g.deadAt) / 500f); if (k <= 0) return;
        float r = s * .48f * k;
        stroke.setStrokeWidth(Math.max(2f, s * .07f)); stroke.setStrokeCap(android.graphics.Paint.Cap.ROUND); stroke.setColor(GameKit.alpha(GameKit.mix(ink, 0xFF000000, .3f), fade));
        for (int q = 0; q < 6; q++) { double ang = q * Math.PI / 3 + (motion ? Math.sin(now / 300.0 + q) * .25 : 0); float x1 = cx + (float) Math.cos(ang) * r * 1.5f, y1 = cy + (float) Math.sin(ang) * r * 1.5f; c.drawLine(cx + (float) Math.cos(ang) * r * .8f, cy + (float) Math.sin(ang) * r * .8f, x1, y1, stroke); }
        stroke.setStrokeCap(android.graphics.Paint.Cap.BUTT);
        fill.setShader(null); fill.setColor(GameKit.alpha(ink, fade)); c.drawCircle(cx, cy, r, fill);
        fill.setColor(GameKit.alpha(0xFFFAFAFA, fade)); c.drawCircle(cx, cy, r * .78f, fill);
        float lx = Math.max(-1f, Math.min(1f, hx - g.x)) * r * .25f, ly = Math.max(-1f, Math.min(1f, hy - g.y)) * r * .25f;
        fill.setColor(GameKit.alpha(glow == 0xFFFFFFFF ? 0xFFFF1744 : glow, fade)); c.drawCircle(cx + lx, cy + ly, r * .42f, fill);
        fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawCircle(cx + lx, cy + ly, r * .2f, fill);
        fill.setColor(GameKit.alpha(0xCCFFFFFF, fade)); c.drawCircle(cx + lx - r * .12f, cy + ly - r * .12f, r * .08f, fill);
        float blink = motion ? (float) Math.max(0, Math.sin(now / 900.0 + g.phase) - .96) * 25f : 0; if (blink > 0) { fill.setColor(GameKit.alpha(ink, fade)); c.drawRect(cx - r, cy - r, cx + r, cy - r + r * 2 * Math.min(1f, blink), fill); }
    }
    void drawMech(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        int ink = now - g.hitAt < 120 ? 0xFFFFFFFF : BOSS_INK[g.bossKind % 20], glow = BOSS_GLOW[g.bossKind % 20], dark = GameKit.mix(ink, 0xFF000000, .45f), lite = GameKit.mix(ink, 0xFFFFFFFF, .35f);
        float k = g.alive ? 1f : 1 - GameKit.ease((now - g.deadAt) / 500f); if (k <= 0) return;
        float step = motion ? (float) Math.sin(now / 140.0 + g.phase) * s * .04f : 0;
        fill.setShader(null);
        for (int side = -1; side <= 1; side += 2) { box.set(cx + side * s * .22f - s * .1f, cy + s * .15f + side * step, cx + side * s * .22f + s * .1f, cy + s * .55f + side * step); fill.setColor(GameKit.alpha(dark, fade * k)); c.drawRoundRect(box, s * .04f, s * .04f, fill); }
        box.set(cx - s * .42f, cy - s * .4f, cx + s * .42f, cy + s * .25f); fill.setColor(GameKit.alpha(ink, fade * k)); c.drawRoundRect(box, s * .1f, s * .1f, fill);
        stroke.setStrokeWidth(Math.max(1f, s * .03f)); stroke.setColor(GameKit.alpha(lite, fade * k)); c.drawRoundRect(box, s * .1f, s * .1f, stroke);
        box.set(cx - s * .3f, cy - s * .3f, cx + s * .3f, cy - s * .1f); fill.setColor(GameKit.alpha(0xFF14161C, fade * k)); c.drawRoundRect(box, s * .05f, s * .05f, fill);
        float scan = motion ? (float) Math.sin(now / 250.0) : 0; fill.setColor(GameKit.alpha(glow, fade * k)); c.drawRoundRect(GameKit.rf(cx + scan * s * .18f - s * .1f, cy - s * .26f, cx + scan * s * .18f + s * .1f, cy - s * .14f), s * .04f, s * .04f, fill);
        for (int side = -1; side <= 1; side += 2) { box.set(cx + side * s * .5f - s * .09f, cy - s * .32f, cx + side * s * .5f + s * .09f, cy + s * .05f); fill.setColor(GameKit.alpha(dark, fade * k)); c.drawRoundRect(box, s * .04f, s * .04f, fill); fill.setColor(GameKit.alpha(glow, fade * k * .8f)); c.drawCircle(box.centerX(), box.bottom, s * .04f, fill); }
        stroke.setColor(GameKit.alpha(dark, fade * k)); c.drawLine(cx, cy - s * .4f, cx, cy - s * .58f, stroke); fill.setColor(GameKit.alpha((now / 300) % 2 == 0 ? glow : dark, fade * k)); c.drawCircle(cx, cy - s * .6f, s * .04f, fill);
    }

    void bossDeco(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        int sp = g.bossKind % 20; fill.setShader(null);
        if (sp == 3) { box.set(cx - s * .22f, cy - s * .95f, cx + s * .22f, cy - s * .55f); fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawRect(box, fill); box.set(cx - s * .34f, cy - s * .6f, cx + s * .34f, cy - s * .52f); c.drawRect(box, fill); fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawRect(cx - s * .22f, cy - s * .66f, cx + s * .22f, cy - s * .6f, fill); }
        else if (sp == 12) { fill.setColor(GameKit.alpha(0xFFE8E2D0, fade)); for (int q = -2; q <= 2; q++) { android.graphics.Path b = GameKit.path(); float bx = cx + q * s * .12f, by = cy - s * .55f; b.moveTo(bx - s * .05f, by); b.lineTo(bx, by - s * (.22f + .06f * (2 - Math.abs(q)))); b.lineTo(bx + s * .05f, by); b.close(); c.drawPath(b, fill); } }
        else if (sp == 19) { android.graphics.Path cr = GameKit.path(); float cw = s * .7f, cb = cy - s * .5f, ch = s * .42f; cr.moveTo(cx - cw / 2, cb); cr.lineTo(cx - cw / 2, cb - ch * .7f); cr.lineTo(cx - cw / 4, cb - ch * .4f); cr.lineTo(cx, cb - ch); cr.lineTo(cx + cw / 4, cb - ch * .4f); cr.lineTo(cx + cw / 2, cb - ch * .7f); cr.lineTo(cx + cw / 2, cb); cr.close(); fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawPath(cr, fill); fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawCircle(cx, cb - ch * .5f, s * .06f, fill); fill.setColor(GameKit.alpha(0xFF4FC3F7, fade)); c.drawCircle(cx - cw * .3f, cb - ch * .25f, s * .04f, fill); c.drawCircle(cx + cw * .3f, cb - ch * .25f, s * .04f, fill); float q = motion ? (float) (.5 + .5 * Math.sin(now / 260.0)) : .5f; fill.setColor(GameKit.alpha(0xFFFFE57F, fade * (.12f + .1f * q))); c.drawCircle(cx, cy, s * .95f, fill); }
    }

    boolean ghostSpecial(Glitch g, float dt, long now) {
        int cx = (int) Math.floor(g.x), cy = (int) Math.floor(g.y);
        if (g.type == 13 && g.hidden) {
            if (heroAlive && Math.hypot(g.x - hx, g.y - hy) < 2.3f) revealMimic(g, now);
            return true;
        }
        if (g.type == 10) { g.dropCool -= dt; if (g.dropCool <= 0 && heroAlive) { g.dropCool = 5f + rnd.nextFloat() * 2.5f; hazCross(cx, cy, 2, HZ_FIRE, 1400); PixelStackSound.play(PixelStackSound.FUSE, .3f, 1.2f); } }
        if (g.type == 11 && (cx != g.lastX || cy != g.lastY)) { g.lastX = cx; g.lastY = cy; boolean has = false; for (Hazard h : hazards) if (h.x == cx && h.y == cy && h.kind == HZ_ICE) has = true; if (!has) hazard(cx, cy, HZ_ICE, 0, 3200, false); }
        if (g.type == 12 && !g.bubble && now - g.bubbleAt > 6000) { g.bubble = true; PixelStackSound.play(PixelStackSound.SOFT, .2f, 1.5f); }
        if (g.type == 15 && heroAlive) {
            g.spitCool -= dt;
            int hcx = (int) Math.floor(hx), hcy = (int) Math.floor(hy);
            if (g.spitCool <= 0 && (hcx == cx || hcy == cy) && Math.abs(hcx - cx) + Math.abs(hcy - cy) <= 6) { g.spitCool = 3.6f + rnd.nextFloat(); int dir = hcx == cx ? (hcy > cy ? 2 : 0) : (hcx > cx ? 1 : 3); g.dir = dir; hazLine(cx, cy, dir, HZ_VOID, 520, 6); g.slamAt = now; PixelStackSound.play(PixelStackSound.FART, .25f, 1.6f); }
        }
        return false;
    }
    void revealMimic(Glitch g, long now) {
        if (!g.hidden) return;
        g.hidden = false; g.hitAt = now; burst(px(g.x), py(g.y), 0xFFFFB74D, 14, 200, 4); PixelStackSound.play(PixelStackSound.BOING, .45f, .8f); shake(4);
        pop("!", px(g.x), py(g.y - .8f), 0xFFFF7A3D, 22);
    }

    void drawNewGhost(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        fill.setShader(null);
        switch (g.type) {
            case 10: { float by = cy - s * .62f; fill.setColor(GameKit.alpha(night ? 0xFF3A4366 : 0xFF1B2440, fade)); c.drawCircle(cx, by, s * .17f, fill); stroke.setStrokeWidth(Math.max(1f, s * .04f)); stroke.setColor(GameKit.alpha(0xFF8D6E63, fade)); c.drawLine(cx + s * .1f, by - s * .12f, cx + s * .18f, by - s * .24f, stroke); fill.setColor(GameKit.alpha((now / 100) % 2 == 0 ? 0xFFFFC94A : 0xFFFF7A3D, fade)); c.drawCircle(cx + s * .19f, by - s * .26f, s * .05f, fill); break; }
            case 11: { fill.setColor(GameKit.alpha(0xFFE1F5FE, fade)); for (int q = -1; q <= 1; q++) { android.graphics.Path p = GameKit.path(); float bx = cx + q * s * .2f, by = cy - s * .42f; p.moveTo(bx - s * .06f, by); p.lineTo(bx, by - s * (.2f + (q == 0 ? .08f : 0))); p.lineTo(bx + s * .06f, by); p.close(); c.drawPath(p, fill); } break; }
            case 12: if (g.bubble) { float q = motion ? (float) (.5 + .5 * Math.sin(now / 200.0 + g.phase)) : .5f; fill.setColor(GameKit.alpha(0xFF90CAF9, fade * (.18f + .08f * q))); c.drawCircle(cx, cy, s * .72f, fill); stroke.setStrokeWidth(Math.max(1.2f, s * .04f)); stroke.setColor(GameKit.alpha(0xFFE3F2FD, fade * .8f)); c.drawCircle(cx, cy, s * .72f, stroke); fill.setColor(GameKit.alpha(0x88FFFFFF, fade)); c.drawCircle(cx - s * .3f, cy - s * .35f, s * .08f, fill); } break;
            case 13: { box.set(cx - s * .42f, cy - s * .72f, cx + s * .42f, cy - s * .5f); fill.setColor(GameKit.alpha(0xFFFFB74D, fade)); c.drawRoundRect(box, s * .05f, s * .05f, fill); fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawRect(cx - s * .06f, box.top, cx + s * .06f, box.bottom, fill); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); for (int q = -2; q <= 2; q++) { android.graphics.Path t = GameKit.path(); float tx = cx + q * s * .09f, ty = cy + s * .12f; t.moveTo(tx - s * .04f, ty); t.lineTo(tx, ty + s * .09f); t.lineTo(tx + s * .04f, ty); t.close(); c.drawPath(t, fill); } break; }
            case 14: { stroke.setStrokeWidth(Math.max(1f, s * .03f)); stroke.setColor(GameKit.alpha(0xAA14161C, fade)); c.drawLine(cx - s * .25f, cy + s * .05f, cx + s * .2f, cy + s * .05f, stroke); for (int q = 0; q < 4; q++) { float x = cx - s * .2f + q * s * .12f; c.drawLine(x, cy - s * .01f, x, cy + s * .11f, stroke); } break; }
            case 15: { float k = GameKit.clamp(1 - (now - g.slamAt) / 400f); box.set(cx + DX[g.dir] * s * .3f - s * .1f, cy + DY[g.dir] * s * .3f - s * .1f, cx + DX[g.dir] * s * .3f + s * .1f, cy + DY[g.dir] * s * .3f + s * .1f); fill.setColor(GameKit.alpha(0xFF4A148C, fade)); c.drawOval(box, fill); fill.setColor(GameKit.alpha(0xFFE040FB, fade * (.4f + .6f * k))); c.drawCircle(box.centerX(), box.centerY(), s * .05f, fill); break; }
            default: break;
        }
    }

    void drawMimicBox(Canvas c, Glitch g, float cx, float cy, long now, float fade) {
        float s = cell * .7f, tw = motion && (now / 1700 + (long) (g.phase * 10)) % 5 == 0 ? (float) Math.sin(now / 30.0) * cell * .03f : 0;
        box.set(cx - s / 2 + tw, cy - s / 2, cx + s / 2 + tw, cy + s / 2); fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFB74D, fade)); c.drawRoundRect(box, s * .2f, s * .2f, fill);
        fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawRect(cx - s * .08f + tw, box.top, cx + s * .08f + tw, box.bottom, fill);
        text.setTypeface(GameKit.BOLD); text.setTextSize(s * .55f); text.setTextAlign(android.graphics.Paint.Align.CENTER); text.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawText("?", cx + tw, cy + s * .2f, text);
    }

    java.util.Random stageRnd() { return new java.util.Random(stage * 7919L + 131); }

    void loadStage() {
        theme();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { hidden[y][x] = 0; crumbleAt[y][x] = 0; meltAt[y][x] = 0; crack[y][x] = 0; heat[y][x] = 0; }
        fieldDirty = true;
        java.util.Random sr = stageRnd();
        layout(stage <= 5 ? (stage - 1) : sr.nextInt(12));
        if (stage > 5) sprinkle(sr);
        float density = stage <= 8 ? Math.min(.62f, .4f + stage * .03f) : .44f + sr.nextFloat() * .18f;
        ArrayList<int[]> crates = new ArrayList<int[]>();
        for (int y = 1; y < H - 1; y++) for (int x = 1; x < W - 1; x++) {
            if (map[y][x] != FLOOR || (x + y <= 3)) continue;
            if (rnd.nextFloat() < density) { map[y][x] = CRATE; crates.add(new int[]{x, y}); }
        }
        java.util.Collections.shuffle(crates, rnd);
        int at = 0;
        if (!crates.isEmpty()) { int[] p = crates.get(at++); hidden[p[1]][p[0]] = 9; portalX = p[0]; portalY = p[1]; }
        int[] kinds = {1, 2, stage % 2 == 0 ? 3 : 1, stage % 3 == 0 ? 4 : 2};
        for (int k : kinds) if (at < crates.size()) { int[] p = crates.get(at++); hidden[p[1]][p[0]] = k; }
        bombs.clear(); flames.clear(); glitches.clear(); prizes.clear(); bits.clear(); pops.clear(); hazards.clear(); slowUntil = 0;
        portalOpen = false; portalAt = 0; portalBannerAt = 0; portalSound = false; gas.clear(); rain.clear(); born.clear(); balls.clear(); gems.clear(); stains.clear(); shrines.clear(); ask = -1; askLast = -1; lights.clear(); trails.clear(); faces.clear(); strikes.clear(); blazes.clear(); haloReady = angel() >= 1; smiteCool = 6f; poisonUntil = breathUntil = shieldUntil = freezeUntil = doubleUntil = laserUntil = dizzyUntil = 0; mood = 0;
        boolean bossStage = stage % 5 == 0;
        int count = bossStage ? Math.min(10, 2 + stage / 5) : Math.min(stage >= 40 ? 11 : stage >= 20 ? 10 : 9, 3 + stage);

        ArrayList<Integer> pool = new ArrayList<Integer>();
        pool.add(0); pool.add(1); if (stage >= 3) pool.add(2); if (stage >= 6) pool.add(4); if (stage >= 8) pool.add(5); if (stage >= 10) pool.add(6); if (stage >= 12) pool.add(7);
        if (stage >= 3) pool.add(8); if (stage >= 6) pool.add(8); if (stage >= 11) pool.add(8);
        if (stage >= 10) pool.add(9); if (stage >= 14) pool.add(9);
        if (stage >= 18) pool.add(10); if (stage >= 24) pool.add(11); if (stage >= 30) pool.add(12); if (stage >= 38) pool.add(13); if (stage >= 48) pool.add(14); if (stage >= 60) pool.add(15);
        if (stage >= 70) { pool.add(10 + rnd.nextInt(6)); pool.add(10 + rnd.nextInt(6)); }
        if (stage >= 9) pool.remove(Integer.valueOf(0));
        java.util.Collections.shuffle(pool, rnd);
        for (int i = 0; i < count; i++) {
            int[] at2 = spot(7, 0);
            if (at2 == null) continue;
            Glitch g = new Glitch(); g.x = at2[0] + .5f; g.y = at2[1] + .5f;
            g.type = pool.get(i % pool.size());
            g.tier = tierFor(); g.look = rnd.nextInt(LOOKS); g.face = rnd.nextInt(FACES);
            g.hp = g.maxHp = 1 + g.tier + (g.type == 7 ? 3 : g.type == 9 ? 2 : g.type == 14 ? 4 + g.tier : g.type == 10 || g.type == 11 || g.type == 13 || g.type == 15 ? 1 : 0) + (stage >= 15 ? stage / 15 : 0);
            if (g.type == 12) g.bubble = true; if (g.type == 13) g.hidden = true; g.dropCool = 2f + rnd.nextFloat() * 3f; g.spitCool = 1.5f + rnd.nextFloat() * 2f;
            if (g.type == 9) g.formCool = 12f + rnd.nextFloat() * 6f;
            g.split = g.type < 4 && stage >= 2 && rnd.nextFloat() < Math.min(.5f, .22f + stage * .03f);
            g.sp = SPEEDS[g.type] * (1 + .05f * g.tier); g.dir = rnd.nextInt(4); g.phase = rnd.nextFloat() * 6f;
            g.mood = 1f + rnd.nextFloat() * 3f; g.teleCool = 4f + rnd.nextFloat() * 4f;
            glitches.add(g);
        }
        if (bossStage) {
            int kind = bossSpec(), n = (BOSS_MOVES[kind] & BK_TWIN) != 0 ? 2 : 1;
            for (int i = 0; i < n; i++) {
                int[] at2 = spot(12, 3);
                if (at2 == null) continue;
                Glitch g = new Glitch(); g.x = at2[0] + .5f; g.y = at2[1] + .5f; g.boss = true; g.type = 3; g.tier = 3; g.bossKind = kind;
                if (kind == 0) { g.hp = g.maxHp = 18 + stage * 2; g.sp = 1.6f; }
                else if (kind == 1) { g.hp = g.maxHp = 26 + stage * 2; g.sp = 1.15f; g.slamCool = 4.5f; }
                else if (kind == 2) { g.hp = g.maxHp = 12 + stage; g.sp = 2.3f; g.teleCool = 3.4f + i * 1.7f; }
                else { int body = BOSS_BODY[kind]; g.hp = g.maxHp = n == 2 ? 12 + stage : 20 + stage * 2; g.sp = body == 1 ? 1.15f : body == 4 ? 1.4f : body == 5 ? 1.8f : body == 6 ? 1.7f : body == 3 ? 2f : body == 2 ? 2.3f : 1.6f; g.slamCool = 4.5f; g.teleCool = 3.4f + i * 1.7f; g.hazCool = 2.5f + i * 1.2f; g.chargeCool = 3f; g.shieldCool = 5f; }
                g.dir = rnd.nextInt(4); g.phase = rnd.nextFloat() * 6f;
                glitches.add(g);
            }
        }
        hx = 1.5f; hy = 1.5f; face = 2; heroAlive = false; heroDeadAt = -1;
    }

    final long[][] meltAt = new long[H][W];

    int tierFor() {
        int top = Math.max(0, Math.min(5, (stage - 1) / 2));
        float r = rnd.nextFloat();
        if (r < .46f || top <= 1) return Math.min(top, rnd.nextInt(2));
        if (r < .8f) return Math.min(top, 2 + rnd.nextInt(2));
        return Math.min(top, 4 + rnd.nextInt(2));
    }
    static final int LOOKS = 13, FACES = 10;

    int openAround(int x, int y) {
        boolean[][] seen = new boolean[H][W]; java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<int[]>();
        q.add(new int[]{x, y}); seen[y][x] = true; int n = 0;
        while (!q.isEmpty() && n < 4) {
            int[] p = q.poll();
            for (int k = 0; k < 4; k++) { int nx = p[0] + DX[k], ny = p[1] + DY[k]; if (nx < 0 || ny < 0 || nx >= W || ny >= H || seen[ny][nx] || map[ny][nx] != FLOOR) continue; seen[ny][nx] = true; n++; q.add(new int[]{nx, ny}); }
        }
        return n;
    }

    void layout(int kind) {
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                boolean border = x == 0 || y == 0 || x == W - 1 || y == H - 1;
                map[y][x] = border || (x % 2 == 0 && y % 2 == 0) ? PILLAR : FLOOR;
            }
            if (pass == 1) return;
            switch (kind) {
                case 1: for (int y = 2; y < H - 2; y += 2) for (int x = 2; x < W - 2; x += 2) if ((x + y) % 4 == 0) map[y][x] = FLOOR; break;
                case 2: for (int y = 1; y < H - 1; y++) for (int x : new int[]{4, 8}) if (y % 2 == 1 && y != 3 && y != 7) map[y][x] = PILLAR; break;
                case 3: for (int x = 3; x <= 9; x++) for (int y = 3; y <= 7; y++) if ((x == 3 || x == 9 || y == 3 || y == 7) && x != 6 && y != 5) map[y][x] = PILLAR; break;
                case 4: for (int y = 2; y < H - 2; y += 2) for (int x = 2; x < W - 3; x += 2) if (((x / 2) + (y / 2)) % 2 == 0) map[y][x + 1] = PILLAR; break;
                case 5: for (int y = 3; y <= 7; y++) for (int x = 4; x <= 8; x++) map[y][x] = FLOOR; break;
                case 6: for (int x = 1; x < W - 1; x += 2) { if (x != 3 && x != 9) map[4][x] = PILLAR; if (x != 5 && x != 7) map[6][x] = PILLAR; } break;
                case 7: for (int x = 1; x < W - 1; x += 2) if (x != 3 && x != 9) map[5][x] = PILLAR; for (int y = 1; y < H - 1; y += 2) if (y != 3 && y != 7) map[y][6] = PILLAR; break;
                case 8: for (int y = 2; y < H - 2; y += 2) for (int x = 2; x < W - 2; x += 2) if (((x / 2) + (y / 2)) % 2 == 1) map[y][x] = FLOOR; break;
                case 9: for (int y = 2; y < H - 2; y += 2) map[y][6] = FLOOR; for (int[] q : new int[][]{{3, 2}, {9, 2}, {3, 8}, {9, 8}}) map[q[1]][q[0]] = PILLAR; break;
                case 10: for (int x = 3; x <= 9; x++) for (int y = 2; y <= 8; y++) if ((x == 3 || x == 9 || y == 2 || y == 8) && (x + y) % 2 == 1 && !(x == 6 && y == 8) && !(x == 3 && y == 5)) map[y][x] = PILLAR; break;
                case 11: for (int y = 1; y < H - 1; y++) for (int x = 1; x < W - 1; x++) if ((x + y) % 2 == 1 && (x * 7 + y * 13 + stage) % 9 == 0) map[y][x] = PILLAR; break;

                default: break;
            }
            for (int[] o : new int[][]{{1, 1}, {2, 1}, {1, 2}}) map[o[1]][o[0]] = FLOOR;
            if (connected()) return;
        }
    }

    void sprinkle(java.util.Random sr) {
        int n = sr.nextInt(4); if (n == 0) return;
        int[][] was = new int[H][W]; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) was[y][x] = map[y][x];
        for (int i = 0; i < n; i++) {
            int x = 2 + sr.nextInt(4), y = 2 + sr.nextInt(H - 4);
            if (x + y <= 4 || map[y][x] != FLOOR) continue;
            map[y][x] = PILLAR; map[y][W - 1 - x] = PILLAR;
        }
        for (int[] o : new int[][]{{1, 1}, {2, 1}, {1, 2}}) map[o[1]][o[0]] = FLOOR;
        if (!connected()) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) map[y][x] = was[y][x];
    }

    boolean connected() {
        boolean[][] seen = new boolean[H][W]; java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<int[]>();
        q.add(new int[]{1, 1}); seen[1][1] = true;
        while (!q.isEmpty()) { int[] p = q.poll(); for (int k = 0; k < 4; k++) { int x = p[0] + DX[k], y = p[1] + DY[k]; if (x < 0 || y < 0 || x >= W || y >= H || seen[y][x] || map[y][x] == PILLAR) continue; seen[y][x] = true; q.add(new int[]{x, y}); } }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (map[y][x] != PILLAR && !seen[y][x]) return false;
        return true;
    }

    @Override void newGame() {
        gemCount = 0; gemRun = 0; soul = NONE; soulLv = 0; soulUnits = 0; soulBits = 0; soulShown = 0; soulDropAt = -10000; meals = 0; soulAt = -10000; absorbAt = -10000; bigUntil = 0; bigLevel = 0; fromTitle = true; stage = 1; score = 0; lives = 5; kills = 0; rank = 0; overBrokenAt = -100000; combo = 0; kick = true;  megaLeft = 0; bombsMax = 1; range = 2; speed = 3.6f; newBest = false; cells = null; sources.clear();
        loadStage();
    }

    void spawnHero() { heroAlive = true; hx = 1.5f; hy = 1.5f; face = 2; heroBornAt = SystemClock.uptimeMillis(); safeUntil = heroBornAt + 2200; }

    boolean blocked(int x, int y, boolean forHero) {
        if (x < 0 || y < 0 || x >= W || y >= H) return true;
        if (map[y][x] != FLOOR) return true;
        if (forHero) return false;
        for (Bomb b : bombs) if (!b.done && b.x == x && b.y == y) return true;
        return false;
    }

    boolean shut(Bomb b, int x, int y) {
        if (x < 0 || y < 0 || x >= W || y >= H || map[y][x] != FLOOR) return true;
        for (Bomb o : bombs) if (o != b && !o.done && o.x == x && o.y == y) return true;
        return false;
    }

    void bounce(Bomb b) {
        int back = (b.slide + 2) % 4;
        GameKit.tick(a, .4f); PixelStackSound.play(PixelStackSound.THUD, .35f, 1.3f);
        if (b.bounced || b.ran > 2 || shut(b, b.x + DX[back], b.y + DY[back])) { b.slide = -1; b.off = 0; return; }
        b.bounced = true;
        burst(px(b.x + .5f + DX[b.slide] * .5f), py(b.y + .5f + DY[b.slide] * .5f), 0xFFFFFFFF, 5, 90, 3);
        b.off = -Math.min(b.off, .5f); b.slide = back;
    }

    void walkHero(int dir, float dt) {
        float step = speed * dt * (SystemClock.uptimeMillis() < frenzyUntil ? 1.4f : 1f) * (angel() >= 8 ? 1.12f : 1f) * (SystemClock.uptimeMillis() < slowUntil ? .55f : 1f);
        face = dir;
        int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy);
        if (kick) {
            int bx = cx + DX[dir], by = cy + DY[dir];

            float along = (dir % 2 == 1 ? hx - (cx + .5f) : hy - (cy + .5f)) * (DX[dir] + DY[dir]), across = dir % 2 == 1 ? Math.abs(hy - (cy + .5f)) : Math.abs(hx - (cx + .5f));
            for (Bomb b : bombs) if (!b.done && b.slide < 0 && b.x == bx && b.y == by && along > -.12f && across < .3f) {
                b.slide = dir; b.off = 0; b.kicked = true; b.ran = 0; b.bounced = false; b.soul = soulLv >= TOP ? soul : NONE; if (b.soul != NONE) lay(b, bx, by); kickAt = SystemClock.uptimeMillis(); kickDir = dir; PixelStackSound.play(PixelStackSound.SOFT, .4f, 1.4f); PixelStackSound.play(PixelStackSound.THUD, .3f, 1.5f); GameKit.thump(a, .6f);
                burst(px(bx + .5f), py(by + .5f), 0xFFFFFFFF, 8, 140, 3);
                for (int i = 0; i < 6; i++) { GameKit.Bit dust = spark(px(hx), py(hy + .35f), night ? 0x88FFFFFF : 0x66000000, 60, 4f); dust.round = true; dust.gravity = -.1f; dust.decay = 2.4f; }
                pop(I18n.t("Kick!"), px(bx + .5f), py(by - .1f), night ? 0xFFFFFFFF : 0xFF14161C, 13);
            }
        }
        float ccx = cx + .5f, ccy = cy + .5f;
        if (dir % 2 == 1) {
            float off = hy - ccy;
            if (Math.abs(off) > .02f) {
                int side = off > 0 ? 1 : -1;
                if (!blocked(cx + DX[dir], cy, true)) { hy -= Math.signum(off) * Math.min(Math.abs(off), step); walk += step; return; }
                if (Math.abs(off) > .18f && !blocked(cx + DX[dir], cy + side, true) && !blocked(cx, cy + side, true)) { hy += side * Math.min(step, Math.abs((ccy + side) - hy)); walk += step; return; }
            }
            float nx = hx + DX[dir] * step;
            if (blocked(cx + DX[dir], cy, true)) nx = DX[dir] > 0 ? Math.min(nx, ccx) : Math.max(nx, ccx);
            if (nx != hx) walk += Math.abs(nx - hx);
            hx = nx;
        } else {
            float off = hx - ccx;
            if (Math.abs(off) > .02f) {
                int side = off > 0 ? 1 : -1;
                if (!blocked(cx, cy + DY[dir], true)) { hx -= Math.signum(off) * Math.min(Math.abs(off), step); walk += step; return; }
                if (Math.abs(off) > .18f && !blocked(cx + side, cy + DY[dir], true) && !blocked(cx + side, cy, true)) { hx += side * Math.min(step, Math.abs((ccx + side) - hx)); walk += step; return; }
            }
            float ny = hy + DY[dir] * step;
            if (blocked(cx, cy + DY[dir], true)) ny = DY[dir] > 0 ? Math.min(ny, ccy) : Math.max(ny, ccy);
            if (ny != hy) walk += Math.abs(ny - hy);
            hy = ny;
        }
    }

    @Override void action(boolean down) {
        if (!down || !heroAlive || state != PLAY) return;
        int x = (int) Math.floor(hx), y = (int) Math.floor(hy);
        int live = 0; for (Bomb b : bombs) if (!b.done) { live++; if (b.x == x && b.y == y) return; }
        if (live >= bombsMax || map[y][x] != FLOOR) return;
        Bomb b = new Bomb(); b.x = x; b.y = y; b.at = SystemClock.uptimeMillis(); b.fuse = 2.6f; b.id = ++bombIds; b.poison = b.at < poisonUntil;
        if (megaLeft > 0) { megaLeft--; b.mega = true; }
        bombs.add(b);
        PixelStackSound.play(PixelStackSound.DROP, .5f, .8f); GameKit.tick(a, .5f);
    }

    @Override void stepPlay(float dt) {
        step(dt);
        if (!born.isEmpty()) { glitches.addAll(born); born.clear(); }
    }


    void step(float dt) {
        long now = SystemClock.uptimeMillis();
        if (state == DYING && since() > 1600) { gameOver(); return; }
        if (heroAlive && state == PLAY && now - heroBornAt > 500 && dir >= 0) walkHero(dir, dt);
        if (state == PLAY && !heroAlive && heroDeadAt <= 0) heroDeadAt = now;
        if (state == PLAY && !heroAlive && heroDeadAt > 0 && now - heroDeadAt > 1300) {
            heroDeadAt = -1;
            if (lives > 0) { lives--; spawnHero(); } else { go(DYING); }
        }

        for (Bomb b : bombs) {
            if (b.done) continue;
            if (b.slide >= 0) {
                b.off += 9f * dt;
                if (b.off >= .22f && shut(b, b.x + DX[b.slide], b.y + DY[b.slide])) bounce(b);
                while (b.slide >= 0 && b.off >= 1f) {
                    int nx = b.x + DX[b.slide], ny = b.y + DY[b.slide];
                    boolean stop = nx < 0 || ny < 0 || nx >= W || ny >= H || map[ny][nx] != FLOOR;
                    for (Bomb o : bombs) if (o != b && !o.done && o.x == nx && o.y == ny) stop = true;
                    Glitch struck = null;
                    for (Glitch g : glitches) if (g.alive && (int) Math.floor(g.x) == nx && (int) Math.floor(g.y) == ny) struck = g;
                    if (struck != null) { b.x = nx; b.y = ny; b.off = 0; b.slide = -1; b.fuse = 0; b.struck = true; if (b.soul != NONE) lay(b, nx, ny); gib(struck); break; }
                    if (stop) { bounce(b); break; }
                    b.x = nx; b.y = ny; b.off -= 1f; b.ran++;
                    if (b.soul != NONE) lay(b, nx, ny);
                }
            }
            float before = b.fuse; b.fuse -= dt;
            if ((int) (before * 4) != (int) (b.fuse * 4) && b.fuse > .2f) PixelStackSound.play(PixelStackSound.FUSE, .14f, 1f + (2.6f - b.fuse) * .15f);
            if (b.fuse <= 0) blow(b);
        }
        for (int i = bombs.size() - 1; i >= 0; i--) if (bombs.get(i).done) bombs.remove(i);
        for (int i = rain.size() - 1; i >= 0; i--) {
            float[] r = rain.get(i);
            if (now < r[2]) continue;
            rain.remove(i);
            Bomb b = new Bomb(); b.x = (int) r[0]; b.y = (int) r[1]; b.id = ++bombIds; b.range = 1; b.at = now;
            if (map[b.y][b.x] == FLOOR) { blow(b); shake(6); }
        }
        if (combo > 0 && now - lastKillAt > 1300) combo = 0;
        for (int i = flames.size() - 1; i >= 0; i--) if (now - flames.get(i).at > 620) flames.remove(i);

        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (crumbleAt[y][x] > 0 && now - crumbleAt[y][x] > 420) {
            if (map[y][x] == PILLAR) { crumbleAt[y][x] = 0; map[y][x] = FLOOR; fieldDirty = true; continue; }
            crumbleAt[y][x] = 0; map[y][x] = FLOOR; fieldDirty = true;
            int k = hidden[y][x];
            if (k == 9) { portalAt = now; if (glitchesLeft() == 0) openPortal(); }
            else if (k == 0 && rnd.nextFloat() < .12f) { Prize p = new Prize(); p.x = x; p.y = y; p.kind = bonusKind(); p.at = now; prizes.add(p); PixelStackSound.play(PixelStackSound.SPARKLE, .35f, 1.2f); }
            else if (k == 0 && rnd.nextFloat() < .4f) { Gem g = new Gem(); g.x = x; g.y = y; g.hue = rnd.nextInt(GEM.length); g.at = now; gems.add(g); }
            else if (k > 0) { Prize p = new Prize(); p.x = x; p.y = y; p.kind = k; p.at = now; prizes.add(p); hidden[y][x] = 0; PixelStackSound.play(PixelStackSound.SPARKLE, .35f, 1.2f); }
        }
        stepGems(dt, now);
        stepSouls(dt, now);

        for (Flame f : flames) {
            if (now - f.at > 480) continue;
            if (heroAlive && !f.safe && now - f.at < 160 && now > safeUntil && now > shieldUntil && (int) Math.floor(hx) == f.x && (int) Math.floor(hy) == f.y) die();
            for (Glitch g : glitches) if (g.alive && !hitBy(g, f.bomb) && (int) Math.floor(g.x) == f.x && (int) Math.floor(g.y) == f.y) { markHit(g, f.bomb); hurt(g, 1, flameInk()); blastMark(g, now); }
        }

        for (int i = gas.size() - 1; i >= 0; i--) if (now - gas.get(i).at > 3600) gas.remove(i);
        for (Glitch g : glitches) {
            if (!g.alive) continue;
            g.gasCool -= dt; g.burnCool -= dt;
            if (g.gasCool <= 0) for (Gas q : gas) if ((int) Math.floor(g.x) == q.x && (int) Math.floor(g.y) == q.y) { g.gasCool = .7f; hurt(g, 1); burst(px(g.x), py(g.y), 0xFF7BE36B, 6, 90, 3); break; }
        }

        if (breathing() && state == PLAY) {
            breathCool -= dt;
            if (breathCool <= 0) {
                breathCool = .05f;
                float mx = px(hx + DX[face] * .3f), my = py(hy - .05f + DY[face] * .3f), far = breathLen() / 2.4f, fan = breathFan();
                for (int i = 0, n = 3 + demon() / 3; i < n; i++) {
                    GameKit.Bit b = new GameKit.Bit(); b.x = mx; b.y = my; float v = (300 + rnd.nextFloat() * 160) * d * far, side = (rnd.nextFloat() - .5f) * (90 * d + v * fan * 2.2f);
                    b.vx = DX[face] * v + DY[face] * side; b.vy = DY[face] * v + DX[face] * side; b.size = cell * (.16f + rnd.nextFloat() * .12f);
                    b.colour = demon() > 0 ? (rnd.nextInt(3) == 0 ? 0xFFC81D2A : rnd.nextBoolean() ? 0xFFFF5A1F : 0xFFFFC94A) : angel() > 0 ? (rnd.nextBoolean() ? 0xFFFFE082 : 0xFFFFFFFF) : rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A;
                    b.round = true; b.gravity = -.06f; b.decay = 3.4f; bits.add(b);
                }
                if (demon() > 0 && rnd.nextFloat() < .12f + .03f * demon()) {
                    GameKit.Bit sm = new GameKit.Bit(); float v = (90 + rnd.nextFloat() * 80) * d * far, side = (rnd.nextFloat() - .5f) * (60 * d + v * fan * 3f);
                    sm.x = mx + DX[face] * cell * breathLen() * (.4f + rnd.nextFloat() * .4f); sm.y = my + DY[face] * cell * breathLen() * (.4f + rnd.nextFloat() * .4f);
                    sm.vx = DX[face] * v + DY[face] * side; sm.vy = DY[face] * v + DX[face] * side; sm.size = cell * (.32f + rnd.nextFloat() * .22f);
                    sm.colour = 0x55301216; sm.round = true; sm.gravity = -.08f; sm.decay = 1.7f; bits.add(sm);
                }
            }
            {
                fireSound -= dt;
                if (fireSound <= 0) { fireSound = .24f; PixelStackSound.play(PixelStackSound.FIRE, .2f, .9f + rnd.nextFloat() * .2f); }
                float len = breathLen(), fan = breathFan(), half = breathSide(); int an = angel(), dm = demon();
                for (Glitch g : glitches) {
                    if (!g.alive || fleeing(g) || g.type == 9) continue;
                    float ax = (g.x - hx) * DX[face] + (g.y - hy) * DY[face], side = Math.abs((g.x - hx) * DY[face] + (g.y - hy) * DX[face]);
                    if (!(ax > 0 && ax < len && side < half + ax * fan)) continue;

                    if (dm == 0 && (ax >= 2.4f || side >= .55f) && !breathClear(hx, hy, g.x, g.y)) continue;
                    if (dm > 0) {
                        if (g.boss) { if (g.burnCool <= 0) { g.burnCool = .3f; hurt(g, 2 + dm / 3, 0xFFFF3B1F); } }
                        else if (!oneShot(g, dm)) { if (g.burnCool <= 0) { g.burnCool = .3f; hurt(g, 1 + dm / 2, 0xFFFF3B1F); } }
                        else if (g.burnUntil == 0) { ignite(g); if (g.burnUntil > 0) g.burnUntil = now + breathBurnMs(); }
                    } else if (g.burnCool <= 0) { g.burnCool = .5f + .05f * an; hurt(g, 1, 0xFFFF7A3D); }
                }
                meltCool -= dt;
                if (meltCool <= 0) {
                    int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy), most = Math.max(1, (int) Math.floor(len - .3f));
                    melt: for (int i = 1; i <= most; i++) {
                        int x = cx + DX[face] * i, y = cy + DY[face] * i;
                        if (x < 0 || y < 0 || x >= W || y >= H) break;
                        if (map[y][x] == PILLAR) {
                            boolean inner = x > 0 && y > 0 && x < W - 1 && y < H - 1;
                            if (dm == 0 || !inner) break;
                            if (crumbleAt[y][x] != 0) continue;
                            meltCool = Math.max(.15f, .4f - .025f * dm); fieldDirty = true;
                            if (++heat[y][x] < heatNeed()) { burst(px(x + .5f), py(y + .5f), 0xFFFF7A3D, 4, 90, 3); break; }
                            meltAt[y][x] = now; crumbleAt[y][x] = now; addScore(20);
                            burst(px(x + .5f), py(y + .5f), 0xFFFF7A3D, 12 + dm, 160, 4); burst(px(x + .5f), py(y + .5f), 0xFFFFD86B, 6 + dm / 2, 120, 3);
                            PixelStackSound.play(PixelStackSound.FIRE, .3f, .55f);
                            break;
                        }
                        if (map[y][x] == CRATE) { if (crumbleAt[y][x] == 0) { meltCool = dm > 0 ? Math.max(.12f, .32f - .02f * dm) : .3f; meltAt[y][x] = now; crumbleAt[y][x] = now; fieldDirty = true; addScore(10); burst(px(x + .5f), py(y + .5f), 0xFFFF7A3D, 8, 140, 4); PixelStackSound.play(PixelStackSound.FIRE, .25f, .7f); } break; }
                        if (half + i * fan < .95f) continue;
                        for (int sd = -1; sd <= 1; sd += 2) {
                            int sx = x + DY[face] * sd, sy = y + DX[face] * sd;
                            if (sx < 1 || sy < 1 || sx >= W - 1 || sy >= H - 1 || map[sy][sx] != CRATE || crumbleAt[sy][sx] != 0) continue;
                            meltCool = .3f; meltAt[sy][sx] = now; crumbleAt[sy][sx] = now; fieldDirty = true; addScore(10); burst(px(sx + .5f), py(sy + .5f), 0xFFFF7A3D, 8, 140, 4); PixelStackSound.play(PixelStackSound.FIRE, .25f, .7f);
                            break melt;
                        }
                    }
                }
            }
        }

        if (heroAlive && now < laserUntil && state == PLAY) stepLaser(dt, now);
        for (Glitch g : glitches) if (g.alive && g.doomAt > 0 && now >= g.doomAt) { g.doomAt = 0; gib(g); }

        boolean frozen = now < freezeUntil, woozy = dizzy();
        for (Glitch g : glitches) {
            if (!g.alive) continue;
            if (g.loveUntil > 0) {
                if (now >= g.loveUntil) {
                    for (int i = 0; i < 14; i++) { GameKit.Bit b = spark(px(g.x), py(g.y), i % 2 == 0 ? 0xFFFF5C8A : 0xFFFFC2D4, 200, 4.5f); b.round = true; b.gravity = -.3f; b.decay = 1.1f; }
                    PixelStackSound.play(PixelStackSound.SPARKLE, .4f, 1.5f);
                    hitNumber(px(g.x), py(g.y) - cell * .55f, Math.max(1, g.hp), 0xFFFF8FB3, false);
                    g.loveUntil = 0; g.split = false; squash(g); continue;
                }
                g.phase += dt * 3;
                float dl = (float) Math.hypot(hx - g.x, hy - g.y);
                if (heroAlive && dl > .9f) { g.x += (hx - g.x) / dl * dt * 1.5f; g.y += (hy - g.y) / dl * dt * 1.5f; }
                if (motion && rnd.nextFloat() < .12f) { GameKit.Bit b = spark(px(g.x), py(g.y - .4f), 0xFFFF5C8A, 30, 4f); b.round = true; b.gravity = -.5f; b.decay = 1.4f; }
                continue;
            }
            if (g.shockUntil > 0) {
                if (now >= g.shockUntil) { flare(g); continue; }
                if (motion && rnd.nextFloat() < .4f) { GameKit.Bit b = spark(px(g.x + (rnd.nextFloat() - .5f) * .6f), py(g.y + (rnd.nextFloat() - .5f) * .6f), rnd.nextBoolean() ? 0xFFBFF4FF : 0xFFFFFFFF, 90, 2.4f); b.round = true; b.gravity = 0; b.decay = 4f; }
            }
            if (g.burnUntil > 0) {
                if (now >= g.burnUntil) { hitNumber(px(g.x), py(g.y) - cell * .55f, Math.max(1, g.hp), 0xFFFF7A3D, true); g.melted = true; squash(g); PixelStackSound.play(PixelStackSound.FART, .25f, 1.6f); continue; }
                if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(px(g.x), py(g.y - .3f), rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A, 50, 4); b.round = true; b.gravity = -.3f; b.decay = 2.5f; }
            }
            if (!frozen) g.phase += dt * (fleeing(g) ? 14 : 6);
            if (woozy) g.spin += dt * 4;
            if (state != PLAY) continue;
            if (g.doomAt > 0) continue;
            if (g.type == 9 && !g.boss && !g.mini) {
                if (now - g.formAt < FORM_MS) continue;
                if (g.form == 0 && !frozen) { g.formCool -= dt; if (g.formCool <= 0) { transform(g, now); continue; } }
            }
            if (g.boss && !frozen) bossTick(g, dt, now);
            if (!g.boss && !g.mini && g.type >= 10 && !frozen && ghostSpecial(g, dt, now)) continue;
            float near = heroAlive ? (float) Math.hypot(g.x - hx, g.y - hy) : 99f;
            if (g.mini && heroAlive && !fleeing(g)) {
                if (soul == ANGEL) { if (near < 1.55f + .06f * angel()) { ash(g); continue; } }
                else {
                    float want = demon() >= 4 ? 3f : 1.9f;
                    if (near < .8f) { eat(g); continue; }
                    if (near < want && now - g.hitAt > 350) { float pull = dt * (2.2f + (want - near) * 3f) / Math.max(.2f, near); g.x += (hx - g.x) * pull; g.y += (hy - g.y) * pull; continue; }
                }
            }
            if (now < g.stunUntil) continue;
            float dread = demon() >= 1 && !g.boss && near < 2.7f ? .5f : 1f;
            if (!frozen) moveGlitch(g, dt * dread * (fleeing(g) ? 1.8f : woozy ? .45f : 1f) * (g.boss && now < g.chargeUntil ? 2.8f : 1f));
            float reach = g.boss ? .9f : g.type == 9 && g.form == 1 ? .72f : g.type == 14 ? .8f : .62f;
            if (heroAlive && !g.mini && !fleeing(g) && Math.abs(g.x - hx) < reach && Math.abs(g.y - hy) < reach) {
                if (now < shieldUntil) { if (now - g.hitAt > 600) { hurt(g, 1, 0xFF9EC1FF); g.dir = (g.dir + 2) % 4; } }
                else if (demon() >= 3 && !g.boss && g.type != 9) { if (now - g.hitAt > 700) { hurt(g, 1, 0xFFFF4D6D); g.dir = (g.dir + 2) % 4; eatAt = now; if (demon() >= 4) frenzyUntil = now + 4000; burst(px(g.x), py(g.y), 0xFFE5173F, 8, 160, 3); PixelStackSound.play(PixelStackSound.POP, .4f, .7f); } }
                else if (now > safeUntil && !frozen) die();
            }
        }
        for (int i = glitches.size() - 1; i >= 0; i--) { Glitch g = glitches.get(i); if (!g.alive && now - g.deadAt > (g.melted ? 1150 : g.lit || g.ashed ? 700 : 600)) glitches.remove(i); }

        if (heroAlive) for (int i = prizes.size() - 1; i >= 0; i--) {
            Prize p = prizes.get(i);
            float pd = (float) Math.hypot(hx - p.x - .5f, hy - p.y - .5f);
            if (heroAlive && state == PLAY && (p.tapped || (pd < (angel() >= 2 ? 4f : 2.3f) && now - p.at > 450))) { p.pull = Math.min(1f, p.pull + dt * (p.tapped ? 4.5f : 3.6f));
                if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(px(prizeX(p)), py(prizeY(p)), TIER_COLOUR[tierOf(p.kind)], 30, 3f); b.round = true; b.gravity = 0; b.decay = 3f; } }
            else p.pull = Math.max(0f, p.pull - dt * 3f);
            if (p.pull >= 1f || ((int) Math.floor(hx) == p.x && (int) Math.floor(hy) == p.y)) { take(p); prizes.remove(i); }
        }

        if (state == PLAY && heroAlive && portalOpen && Math.abs(hx - (portalX + .5f)) < .3f && Math.abs(hy - (portalY + .5f)) < .3f) {
            pullX = hx; pullY = hy; sucked = false;
            go(CLEAR); PixelStackSound.play(PixelStackSound.SUCCESS, .55f, 1f); GameKit.thump(a, .7f);
        }
    }

    static final long LASER_MS = 12000;
    static final float BASIC_REACH = 4f;
    float laserEnd = -1;
    void stepLaser(float dt, long now) {
        int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy), f = face;

        int an = angel(), dm = demon(), pierce = an >= 1 ? 1 + an / 3 : 0; boolean through = an >= 1;
        float most = dm > 0 ? Math.max(2f, 6.5f - dm * .4f) : an > 0 ? 99f : BASIC_REACH;
        float reach = 0; int burnN = 0; int[] burnX = new int[5], burnY = new int[5];
        for (int i = 1; i < Math.max(W, H); i++) {
            int x = cx + DX[f] * i, y = cy + DY[f] * i;
            if (i > most) { reach = most; break; }
            if (x < 0 || y < 0 || x >= W || y >= H) { reach = i - .5f; break; }
            if (map[y][x] == PILLAR) {
                if (through && x > 0 && y > 0 && x < W - 1 && y < H - 1) { if (crumbleAt[y][x] == 0 && burnN < 5 && burnN <= pierce) { burnX[burnN] = x; burnY[burnN] = y; burnN++; } continue; }
                reach = i - .5f; break;
            }
            if (map[y][x] == CRATE && crumbleAt[y][x] == 0) { if (burnN < 5 && burnN <= pierce) { burnX[burnN] = x; burnY[burnN] = y; burnN++; } if (!through && burnN > pierce) { reach = i - .5f; break; } }
        }
        float along0 = (hx - (cx + .5f)) * DX[f] + (hy - (cy + .5f)) * DY[f];
        laserEnd = Math.max(.3f, reach - along0);
        laserSound -= dt;
        if (laserSound <= 0) { laserSound = .26f; PixelStackSound.play(PixelStackSound.LASER, .2f, (an > 0 ? 1.12f : dm > 0 ? .86f : 1f) + rnd.nextFloat() * .04f); }
        laserCool -= dt;
        if (laserCool > 0) return;
        laserCool = an > 0 ? Math.max(.14f, .25f - .012f * an) : dm > 0 ? .25f + .02f * dm : .25f; beamAt = now;
        boolean hit = false;
        for (int q = 0; q < burnN; q++) {
            int crateX = burnX[q], crateY = burnY[q];
            if (map[crateY][crateX] == PILLAR) {
                hit = true; fieldDirty = true;
                if (++crack[crateY][crateX] < crackNeed()) { burst(px(crateX + .5f), py(crateY + .5f), 0xFFFFE082, 4, 110, 2.5f); continue; }
                shatter(crateX, crateY, now); continue;
            }
            crumbleAt[crateY][crateX] = now; meltAt[crateY][crateX] = now; fieldDirty = true; addScore(10); crateBits(crateX, crateY);
            burst(px(crateX + .5f), py(crateY + .5f), 0xFFFF4D6D, 8, 160, 3); hit = true;
        }
        for (Glitch g : new ArrayList<Glitch>(glitches)) {
            if (!g.alive || g.doomAt > 0 || g.shockUntil > 0 || now - g.zapAt < 330) continue;
            float ax = (g.x - hx) * DX[f] + (g.y - hy) * DY[f], side = Math.abs((g.x - hx) * DY[f] + (g.y - hy) * DX[f]);
            if (ax <= -.2f || ax > laserEnd + .4f || side > (g.boss ? .75f : .5f)) continue;
            zap(g, now); hit = true;
        }
        if (hit) { PixelStackSound.play(PixelStackSound.SHOT, .26f, 1.7f + rnd.nextFloat() * .2f); GameKit.tick(a, .25f); }
    }

    boolean oneShot(Glitch g, int lv) { return lv >= TOP || g.maxHp <= 1 + lv * 1.5f; }

    int laserBoss;
    void zap(Glitch g, long now) {
        if (g.boss && blinking(g)) return;
        g.zapAt = now;
        for (int i = 0; i < 6; i++) { GameKit.Bit b = spark(px(g.x), py(g.y), i % 2 == 0 ? 0xFFBFF4FF : 0xFFFFFFFF, 220, 2.6f); b.round = true; b.gravity = .1f; b.decay = 3f; }
        int an = angel(), dm = demon();
        boolean basic = an == 0 && dm == 0;

        if (g.boss) { laserBoss++; hurt(g, basic ? 1 + laserBoss % 2 : an > 0 ? 4 + an / 2 : Math.max(1, (laserBoss % 2 == 0 ? 3 : 2) - (dm > 0 ? 1 : 0)), an > 0 ? 0xFFFFD23F : 0xFF7FE9FF); return; }
        if (g.type == 9 && !g.mini) { hurt(g, basic ? 1 : an > 0 ? 3 + an / 2 : Math.max(1, 2 - (dm > 0 ? 1 : 0)), an > 0 ? 0xFFFFD23F : 0xFF7FE9FF); return; }
        if (dm > 0 || basic) { hurt(g, 1, 0xFF7FE9FF); return; }
        if (!oneShot(g, an)) { hurt(g, 1 + an / 2, 0xFFFFD23F); return; }
        shock(g, now);
    }

    int bonusKind() { float roll = rnd.nextFloat(); int[] from = GUIDE[roll < .5f ? 0 : roll < .88f ? 1 : 2]; return from[rnd.nextInt(from.length)]; }

    void ignite(Glitch g) {
        if (g.type == 9 || g.boss || g.shockUntil > 0) return;
        g.burnUntil = SystemClock.uptimeMillis() + 1700;
        g.dir = (face + 0) % 4;
        burst(px(g.x), py(g.y), 0xFFFF7A3D, 10, 160, 4);
        PixelStackSound.play(PixelStackSound.BOING, .35f, 1.5f);
    }

    int fartKind; float fartReach;
    void fart() {
        long now = SystemClock.uptimeMillis();
        int ho = angel(), ev = demon(), tier = (Math.max(ho, ev) + 1) / 2;
        fartKind = ho > 0 ? 1 : ev > 0 ? 2 : 0; fartReach = 1.8f + tier * 1.5f;
        dizzyUntil = now + 7000 + tier * 800; fartAt = now;
        float cx = px(hx - DX[face] * .3f), cy = py(hy + .25f);
        int[] cols = fartKind == 1 ? new int[]{0xFFFFFFFF, 0xFFFFE3EE, 0xFFF4F7FF} : fartKind == 2 ? new int[]{0xFFFF7A3D, 0xFFFFC94A, 0xFFE5532B} : new int[]{0xFFB9C77A, 0xFF9DB35A, 0xFFD8D9A0};
        for (int i = 0; i < 26 + tier * 10; i++) { GameKit.Bit b = spark(cx, cy, cols[i % 3], 160 + tier * 50, 9); b.round = true; b.gravity = -.15f; b.decay = .8f; }
        PixelStackSound.play(PixelStackSound.FART, .5f + .08f * tier, 1f - .06f * tier);
        shake(5 + tier * 2); GameKit.thump(a, .6f);
        if (fartKind == 0) return;
        if (fartKind == 2) PixelStackSound.play(PixelStackSound.FIRE, .5f, .8f); else PixelStackSound.play(PixelStackSound.SPARKLE, .5f, 1.3f);
        for (Glitch g : new ArrayList<Glitch>(glitches)) {
            if (!g.alive || Math.hypot(g.x - hx, g.y - hy) > fartReach) continue;
            if (g.boss || g.type == 9) { hurt(g, tier + (fartKind == 2 ? 1 : 0), fartKind == 1 ? 0xFFFF8FB3 : 0xFFFF7A3D); continue; }
            if (g.shockUntil > 0) continue;
            if (fartKind == 1) { if (g.loveUntil == 0) { g.loveUntil = now + 2600 + rnd.nextInt(700); g.burnUntil = 0; } }
            else if (g.burnUntil == 0 && g.loveUntil == 0) ignite(g);
        }
    }
    void heart(Canvas c, float x, float y, float q, int colour) {
        fill.setShader(null); fill.setColor(colour);
        c.drawCircle(x - q * .5f, y - q * .3f, q * .58f, fill); c.drawCircle(x + q * .5f, y - q * .3f, q * .58f, fill);
        android.graphics.Path hp = GameKit.path(); hp.moveTo(x - q * 1.05f, y - q * .05f); hp.lineTo(x + q * 1.05f, y - q * .05f); hp.lineTo(x, y + q * 1.1f); hp.close(); c.drawPath(hp, fill);
    }

    void bombRain() {
        long now = SystemClock.uptimeMillis();
        ArrayList<int[]> spots = new ArrayList<int[]>();
        for (Glitch g : glitches) if (g.alive) spots.add(new int[]{(int) Math.floor(g.x), (int) Math.floor(g.y)});
        java.util.Collections.shuffle(spots, rnd);
        for (int tries = 0; spots.size() < 6 && tries < 200; tries++) { int x = 1 + rnd.nextInt(W - 2), y = 1 + rnd.nextInt(H - 2); if (map[y][x] == FLOOR && Math.abs(x + .5f - hx) + Math.abs(y + .5f - hy) > 2) spots.add(new int[]{x, y}); }
        for (int i = 0; i < Math.min(6, spots.size()); i++) rain.add(new float[]{spots.get(i)[0], spots.get(i)[1], now + 700 + i * 260});
        PixelStackSound.play(PixelStackSound.RISE, .3f, 1.3f);
    }

    static final float[] SPEEDS = {1.9f, 2.7f, 3.1f, 1.6f, 2.2f, 2.3f, 2.4f, 1.5f, 2.4f, 2.2f, 2f, 2.2f, 2.1f, 3.3f, 1.3f, 1.9f};

    int[] spot(int far, int apart) {
        for (int pass = 0; pass < 2; pass++) for (int tries = 0; tries < 400; tries++) {
            int x = 1 + rnd.nextInt(W - 2), y = 1 + rnd.nextInt(H - 2);
            if (x + y < (tries < 300 ? far : 7)) continue;
            if (pass == 0) { if (map[y][x] != FLOOR || openAround(x, y) < 2) continue; }
            else if (map[y][x] == PILLAR || (map[y][x] == CRATE && hidden[y][x] != 0)) continue;
            boolean taken = false; for (Glitch o : glitches) if (Math.abs(o.x - x - .5f) + Math.abs(o.y - y - .5f) < Math.max(1, tries < 300 ? apart : 1)) taken = true;
            if (taken) continue;
            if (pass == 1) {
                map[y][x] = FLOOR; fieldDirty = true;
                for (int k = 0, opened = 0; k < 4 && opened < 2; k++) {
                    int nx = x + DX[k], ny = y + DY[k];
                    if (nx < 1 || ny < 1 || nx >= W - 1 || ny >= H - 1 || nx + ny <= 3) continue;
                    if (map[ny][nx] == FLOOR) opened++;
                    else if (map[ny][nx] == CRATE && hidden[ny][nx] == 0) { map[ny][nx] = FLOOR; opened++; }
                }
            }
            return new int[]{x, y};
        }
        return null;
    }

    boolean barred(Glitch g, int x, int y) { return blocked(x, y, false); }

    int glitchesLeft() { int n = born.size(); for (Glitch g : glitches) if (g.alive) n++; return n; }

    void openPortal() {
        if (portalOpen || portalX < 0) return;
        if (map[portalY][portalX] == CRATE) { if (crumbleAt[portalY][portalX] == 0) { crumbleAt[portalY][portalX] = SystemClock.uptimeMillis(); fieldDirty = true; crateBits(portalX, portalY); } return; }
        if (hidden[portalY][portalX] != 9) return;
        portalOpen = true; portalAt = SystemClock.uptimeMillis(); portalBannerAt = portalAt;
        placeShrines();
        shake(6); GameKit.thump(a, .8f);
    }

    boolean threatened(int x, int y) {
        for (Bomb b : bombs) { if (b.done) continue; int r = reachOf(b); if ((b.x == x && Math.abs(b.y - y) <= r) || (b.y == y && Math.abs(b.x - x) <= r)) return true; }
        return false;
    }
    void moveGlitch(Glitch g, float dt) {
        float step = g.sp * dt;
        int kind = g.boss || g.mini ? -1 : g.type;
        if (kind == 5) {
            g.mood -= dt;
            if (g.mood <= 0) { g.rush = !g.rush; g.mood = g.rush ? .9f : 1.3f + rnd.nextFloat(); }
            step *= g.rush ? 2.3f : .4f;
        }
        if (kind == 6) {
            g.teleCool -= dt;
            if (g.teleCool <= 0 && !fleeing(g)) {
                g.teleCool = 4.5f + rnd.nextFloat() * 3f;
                for (int tries = 0; tries < 60; tries++) {
                    int x = 1 + rnd.nextInt(W - 2), y = 1 + rnd.nextInt(H - 2);
                    if (blocked(x, y, false) || Math.abs(x + .5f - hx) + Math.abs(y + .5f - hy) < 4 || threatened(x, y)) continue;
                    burst(px(g.x), py(g.y), 0xFFFF6FB5, 10, 160, 3);
                    g.fromX = g.x; g.fromY = g.y; g.x = x + .5f; g.y = y + .5f; g.blinkAt = SystemClock.uptimeMillis(); g.dir = rnd.nextInt(4);
                    burst(px(g.x), py(g.y), 0xFFFF6FB5, 10, 160, 3);
                    return;
                }
            }
        }
        if (g.mood > 0 && kind == 7) g.mood -= dt;
        int cx = (int) Math.floor(g.x), cy = (int) Math.floor(g.y);
        float ccx = cx + .5f, ccy = cy + .5f;
        boolean atCentre = Math.abs(g.x - ccx) < step + .01f && Math.abs(g.y - ccy) < step + .01f;
        if (atCentre) {
            g.think -= 1;
            boolean ahead = !barred(g, cx + DX[g.dir], cy + DY[g.dir]);
            if (kind == 7 && !ahead && !fleeing(g)) {
                int bx = cx + DX[g.dir], by = cy + DY[g.dir];
                if (bx > 0 && by > 0 && bx < W - 1 && by < H - 1 && map[by][bx] == CRATE && crumbleAt[by][bx] == 0 && g.mood <= 0) {
                    crumbleAt[by][bx] = SystemClock.uptimeMillis(); fieldDirty = true; crateBits(bx, by); shake(3); g.mood = 2.2f; g.slamAt = SystemClock.uptimeMillis();
                    g.x = ccx; g.y = ccy; return;
                }
            }
            boolean decide = !ahead || (g.type >= 10 && rnd.nextFloat() < .4f) || (g.type == 1 && rnd.nextFloat() < .35f) || (g.type == 0 && rnd.nextFloat() < .12f) || g.type == 2 || (g.type == 4 && rnd.nextFloat() < .3f) || (kind == 5 && !g.rush && rnd.nextFloat() < .5f) || (g.type == 6 && rnd.nextFloat() < .2f) || (g.type == 7 && rnd.nextFloat() < .15f) || (g.type == 8 && rnd.nextFloat() < .3f) || g.type == 9 || fleeing(g);
            if (decide) {
                g.x = ccx; g.y = ccy;
                int[] opts = new int[4]; int n = 0;
                for (int k = 0; k < 4; k++) if (!barred(g, cx + DX[k], cy + DY[k])) opts[n++] = k;
                if (n == 0) return;
                int pick = opts[rnd.nextInt(n)];
                if (fleeing(g) && heroAlive) {
                    float bestD = -1;
                    for (int k = 0; k < n; k++) { float dd = Math.abs(cx + DX[opts[k]] + .5f - hx) + Math.abs(cy + DY[opts[k]] + .5f - hy); if (dd > bestD) { bestD = dd; pick = opts[k]; } }
                } else if (((g.type == 13 || g.type == 15 || g.type == 14) && heroAlive && rnd.nextFloat() < .6f) || (g.type == 2 && heroAlive && rnd.nextFloat() < .6f) || (g.type == 9 && heroAlive && rnd.nextFloat() < (g.form == 1 ? .8f : .5f)) || (kind == 7 && heroAlive && rnd.nextFloat() < .5f) || (kind == 5 && !g.rush && heroAlive && rnd.nextFloat() < .45f) || (g.tier >= 1 && heroAlive && Math.abs(hx - g.x) + Math.abs(hy - g.y) < 3 + g.tier && rnd.nextFloat() < Math.min(.6f, .13f * g.tier))) {
                    float dx = hx - g.x, dy = hy - g.y; int want = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0);
                    for (int k = 0; k < n; k++) if (opts[k] == want) pick = want;
                } else if (ahead && g.type == 2) pick = g.dir;
                if (n > 1 && pick == (g.dir + 2) % 4 && ahead) pick = g.dir;
                if (g.tier >= 3 && n > 1 && !fleeing(g) && rnd.nextFloat() < (g.tier >= 5 ? .7f : g.tier == 4 ? .5f : .3f) && threatened(cx + DX[pick], cy + DY[pick])) {
                    for (int k = 0; k < n; k++) if (!threatened(cx + DX[opts[k]], cy + DY[opts[k]])) { pick = opts[k]; break; }
                }
                g.dir = pick;
            }
        }
        if ((g.tier >= 4 || (g.type == 9 && g.form == 1)) && heroAlive && !g.boss && !fleeing(g)) {
            float dx = hx - g.x, dy = hy - g.y;
            boolean lined = DX[g.dir] != 0 ? Math.abs(dy) < .4f && dx * DX[g.dir] > 0 && Math.abs(dx) < 4 : Math.abs(dx) < .4f && dy * DY[g.dir] > 0 && Math.abs(dy) < 4;
            if (lined) step *= 1.3f + .08f * (g.tier - 4);
        }
        float nx = g.x + DX[g.dir] * step, ny = g.y + DY[g.dir] * step;
        int tx = (int) Math.floor(nx + DX[g.dir] * .45f), ty = (int) Math.floor(ny + DY[g.dir] * .45f);
        if (barred(g, tx, ty) && !(tx == cx && ty == cy)) { g.x = ccx; g.y = ccy; g.dir = (g.dir + 2) % 4; return; }
        g.x = nx; g.y = ny;
    }

    void blow(Bomb b) {
        if (b.done) return;
        b.done = true;
        long now = SystemClock.uptimeMillis();
        if (b.kicked) { blowBall(b, now); return; }
        addFlame(b.x, b.y, 0, now, b);
        int reach = reachOf(b);
        boolean pierce = pierces(b);
        for (int k = 0; k < 4; k++) {
            for (int i = 1; i <= reach; i++) {
                int x = b.x + DX[k] * i, y = b.y + DY[k] * i;
                if (x < 0 || y < 0 || x >= W || y >= H || map[y][x] == PILLAR) break;
                if (map[y][x] == CRATE) { if (crumbleAt[y][x] == 0) { crumbleAt[y][x] = now; fieldDirty = true; addScore(10); crateBits(x, y); } if (!pierce) break; continue; }
                boolean end = i == reach;
                addFlame(x, y, end ? 3 + k : (k % 2 == 1 ? 1 : 2), now, b);
                for (Bomb o : bombs) if (!o.done && o.x == x && o.y == y) o.fuse = Math.min(o.fuse, .09f);
            }
        }
        float cx = px(b.x + .5f), cy = py(b.y + .5f);
        burst(cx, cy, 0xFFFFC94A, b.mega ? 30 : 14, b.mega ? 380 : 260, b.mega ? 7 : 5); burst(cx, cy, 0xFFFF7A3D, 8, 200, 4);
        if (b.mega) { GameKit.rumble(a); shake(16); }
        shake(9);
        PixelStackSound.play(PixelStackSound.BOOM, .55f, .9f + rnd.nextFloat() * .2f);
        GameKit.thump(a, .9f);
    }

    void blowBall(Bomb b, long now) {
        float r = ballReach(b); int n = (int) Math.ceil(r);
        for (int y = b.y - n; y <= b.y + n; y++) for (int x = b.x - n; x <= b.x + n; x++) {
            if (x < 0 || y < 0 || x >= W || y >= H || map[y][x] == PILLAR || Math.hypot(x - b.x, y - b.y) > r) continue;
            if (map[y][x] == CRATE) { if (crumbleAt[y][x] == 0) { crumbleAt[y][x] = now; fieldDirty = true; addScore(10); crateBits(x, y); } continue; }
            addFlame(x, y, 7, now, b);
            for (Bomb o : bombs) if (!o.done && o != b && o.x == x && o.y == y) o.fuse = Math.min(o.fuse, .09f);
        }
        Ball ball = new Ball(); ball.x = b.x + .5f; ball.y = b.y + .5f; ball.r = r + .35f; ball.at = now; ball.mega = b.mega; balls.add(ball);
        if (b.soul != NONE) top10(b, ball, r, now);
        float cx = px(b.x + .5f), cy = py(b.y + .5f);
        burst(cx, cy, 0xFFFFC94A, 22 + (int) (r * 6), 300 + r * 60, 6); burst(cx, cy, 0xFFFF7A3D, 14, 240, 5); burst(cx, cy, 0xFFFFFFFF, 8, 380, 4);
        shake(10 + r * 3); if (r > 3) GameKit.rumble(a);
        PixelStackSound.play(PixelStackSound.BOOM, .65f, Math.max(.55f, 1f - r * .08f));
        GameKit.thump(a, 1f);
    }

    void drawBreath(Canvas c, long now) {
        if (!heroAlive) return;
        float x0 = px(hx + DX[face] * .28f), y0 = py(hy - .05f + DY[face] * .28f), len = cell * (breathLen() - .15f), fan = breathFan();
        float ang = face == 1 ? 0 : face == 2 ? 90 : face == 3 ? 180 : -90;
        int save = c.save(); c.rotate(ang, x0, y0);
        if (demon() > 0) {
            c.translate(x0, y0); drawHellfire(c, now, len, fan); c.restoreToCount(save);
            fill.setShader(null); fill.setColor(0x44FF3B1F); c.drawCircle(x0, y0, cell * .5f, fill);
            return;
        }
        int an = angel();
        int[] cols = an > 0 ? new int[]{0xFF8FD3FF, 0xFFFFD54F, 0xFFFFF3B0, 0xFFFFFFFF} : new int[]{0xFFE5532B, 0xFFFF9A3D, 0xFFFFD86B, 0xFFFFF6D0}; float[] wide = {.5f, .38f, .24f, .1f}, reach = {1f, .88f, .7f, .45f};
        fill.setShader(null);
        for (int i = 0; i < 4; i++) {
            float fl = 1 + .1f * (float) Math.sin(now / (38.0 + i * 9) + i * 2), L = len * reach[i] * fl, Wd = cell * wide[i] * (1 + .12f * (float) Math.sin(now / (50.0 + i * 7) + i));
            android.graphics.Path p = GameKit.path();
            p.moveTo(x0, y0 - cell * .07f);
            if (fan <= 0) {
                p.cubicTo(x0 + L * .3f, y0 - Wd * 1.1f, x0 + L * .75f, y0 - Wd * (.9f + .25f * (float) Math.sin(now / 70.0 + i)), x0 + L, y0 + (float) Math.sin(now / 55.0 + i * 1.7) * Wd * .35f);
                p.cubicTo(x0 + L * .75f, y0 + Wd * (.9f + .25f * (float) Math.cos(now / 64.0 + i)), x0 + L * .3f, y0 + Wd * 1.1f, x0, y0 + cell * .07f);
            } else {
                float f = fan * wide[i] / .5f, e = Wd * .5f + L * f, wob = (float) Math.sin(now / 55.0 + i * 1.7) * Wd * .3f;
                p.cubicTo(x0 + L * .3f, y0 - Wd * 1.1f - L * .3f * f, x0 + L * .75f, y0 - Wd * (.9f + .25f * (float) Math.sin(now / 70.0 + i)) - L * .75f * f, x0 + L * .97f, y0 - e);
                p.quadTo(x0 + L * 1.1f, y0 + wob, x0 + L * .97f, y0 + e);
                p.cubicTo(x0 + L * .75f, y0 + Wd * (.9f + .25f * (float) Math.cos(now / 64.0 + i)) + L * .75f * f, x0 + L * .3f, y0 + Wd * 1.1f + L * .3f * f, x0, y0 + cell * .07f);
            }
            p.close();
            fill.setColor(GameKit.alpha(cols[i], i == 0 ? .8f : .95f)); c.drawPath(p, fill);
        }
        for (int i = 0; i < 3; i++) {
            float ph = ((now / 420f) + i / 3f) % 1f, rx = x0 + len * ph, ry = cell * (.16f + .34f * ph) + len * ph * fan * .9f;
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(an > 0 ? 0xFFFFFFFF : 0xFFFFE9A8, (1 - ph) * .6f)); c.drawOval(GameKit.rf(rx - cell * .06f, y0 - ry, rx + cell * .06f, y0 + ry), stroke);
        }
        if (an > 0) for (int k = 0, n = 3 + an / 2; k < n; k++) {
            float ph = ((now / 600f) + k / (float) n) % 1f, sx = x0 + len * (.15f + .8f * ph), sy = y0 + ((((k * 37) % 9) - 4) / 4f) * cell * .18f, st = cell * (.09f + .005f * an) * (1 - ph * .5f);
            android.graphics.Path sp = GameKit.path(); sp.moveTo(sx, sy - st); sp.lineTo(sx + st * .2f, sy - st * .2f); sp.lineTo(sx + st, sy); sp.lineTo(sx + st * .2f, sy + st * .2f); sp.lineTo(sx, sy + st); sp.lineTo(sx - st * .2f, sy + st * .2f); sp.lineTo(sx - st, sy); sp.lineTo(sx - st * .2f, sy - st * .2f); sp.close();
            fill.setColor(GameKit.alpha(0xFFFFFFFF, (1 - ph) * .9f)); c.drawPath(sp, fill);
        }
        c.restoreToCount(save);
        fill.setColor(an > 0 ? 0x40FFF3B0 : 0x33FFB347); c.drawCircle(x0, y0, cell * .45f, fill);
    }

    private android.graphics.LinearGradient[] hellShade;
    private float hellShadeLen = -1;
    private static final float[] HELL_WIDE = {.62f, .5f, .36f, .2f}, HELL_REACH = {1.06f, 1f, .84f, .6f};
    private static final int[][] HELL_COLS = {
            {0x99600818, 0x66600818, 0x00300008},
            {0xFFA30F2A, 0xF0C81D2A, 0x00C81D2A},
            {0xFFFF4A1C, 0xF0FF6A24, 0x00FF6A24},
            {0xFFFFE9A8, 0xE6FFC64A, 0x00FFC64A}};
    void drawHellfire(Canvas c, long now, float len, float fan) {
        if (len <= 0) return;
        final int dm = demon();
        if (hellShade == null || Math.abs(len - hellShadeLen) > 1f) {
            hellShade = new android.graphics.LinearGradient[4]; hellShadeLen = len;
            for (int i = 0; i < 4; i++) hellShade[i] = new android.graphics.LinearGradient(0, 0, len * HELL_REACH[i] * 1.18f, 0, HELL_COLS[i], new float[]{0f, .5f, 1f}, android.graphics.Shader.TileMode.CLAMP);
        }
        for (int i = 0; i < 4; i++) {
            float fl = 1 + .08f * (float) Math.sin(now / (36.0 + i * 9) + i * 2), L = len * HELL_REACH[i] * fl;
            float w0 = cell * HELL_WIDE[i] * .42f, f = fan * HELL_WIDE[i] / .5f, e = cell * HELL_WIDE[i] * .5f + L * f;
            float wob1 = cell * HELL_WIDE[i] * .12f * (float) Math.sin(now / 62.0 + i * 1.3), wob2 = cell * HELL_WIDE[i] * .12f * (float) Math.cos(now / 57.0 + i * 2.1);
            android.graphics.Path p = GameKit.path();
            p.moveTo(0, -w0 * .35f);
            p.cubicTo(L * .25f, -w0 - L * .2f * f + wob1, L * .55f, -e * .92f + wob2, L * .8f, -e);
            final int n = 3 + dm / 3;
            for (int j = 0; j < n; j++) {
                float ya = -e + 2 * e * j / n, yb = -e + 2 * e * (j + 1) / n, ym = (ya + yb) / 2, h = yb - ya;
                float flick = .5f + .5f * (float) Math.sin(now / (40.0 + j * 11 + i * 5) + j * 2.3 + i);
                float tip = L * (.9f + .2f * flick), sway = h * .25f * (float) Math.sin(now / 47.0 + j + i);
                float valley = j == n - 1 ? L * .8f : L * (.78f + .05f * (float) Math.sin(now / 53.0 + j * 1.7));
                p.cubicTo(L * .9f, ya, tip - L * .08f, ym - h * .3f + sway, tip, ym + sway);
                p.cubicTo(tip - L * .08f, ym + h * .3f + sway, L * .9f, yb, valley, yb);
            }
            p.cubicTo(L * .55f, e * .92f - wob1, L * .25f, w0 + L * .2f * f - wob2, 0, w0 * .35f);
            p.close();
            fill.setShader(hellShade[i]); fill.setColor(GameKit.alpha(0xFFFFFFFF, i == 0 ? .55f + .045f * dm : 1f)); c.drawPath(p, fill);
        }
        fill.setShader(null);
        if (dm >= 7) {
            for (int side = -1; side <= 1; side += 2) for (int k = 0; k < 4; k++) {
                float ph = motion ? ((now / 300f) + k / 4f + (side > 0 ? .5f : 0)) % 1f : (k + .5f) / 4f, x = len * (.2f + .75f * ph), y = side * (cell * .3f + x * fan + cell * .12f), q = cell * (.16f - .08f * ph);
                android.graphics.Path f = GameKit.path(); f.moveTo(x - q, y); f.quadTo(x, y + side * q * 2.4f, x + q, y); f.close();
                fill.setColor(GameKit.alpha(0xFF1A0508, .75f * (1 - ph))); c.drawPath(f, fill);
            }
        }
        float coreL = cell * (.55f + .06f * (float) Math.sin(now / 33.0)), coreW = cell * .1f;
        fill.setColor(0x99FFF6D0); c.drawOval(GameKit.rf(-cell * .05f, -coreW * 1.6f, coreL * 1.15f, coreW * 1.6f), fill);
        fill.setColor(0xF2FFFFFF); c.drawOval(GameKit.rf(-cell * .03f, -coreW, coreL, coreW), fill);
        final int sparks = 5 + dm;
        for (int k = 0; k < sparks; k++) {
            float ph = ((now / 520f) + k / (float) sparks) % 1f, x = len * (.12f + .88f * ph);
            float y = (((k * 37) % 11) - 5) / 5f * (cell * .22f + x * fan) * .85f + cell * .05f * (float) Math.sin(now / 90.0 + k);
            fill.setColor(GameKit.alpha(k % 2 == 0 ? 0xFFFFD86B : 0xFFFF7A3D, (1 - ph) * .9f)); c.drawCircle(x, y, cell * .035f * (1.2f - ph), fill);
        }
    }

    void drawSouls(Canvas c, long now) {
        fill.setShader(null);
        if (angel() >= 4 && heroAlive) for (int k = 0; k < (angel() >= 7 ? 2 : 1); k++) {
            double ang = orb + k * Math.PI;
            float gx = px(hx + (float) Math.cos(ang) * 1.15f), gy = py(hy + (float) Math.sin(ang) * 1.15f);
            fill.setColor(0x55FFE9A8); c.drawCircle(gx, gy, cell * .34f, fill);
            fill.setColor(0xFFFFD23F); c.drawCircle(gx, gy, cell * .17f, fill);
            fill.setColor(0xFFFFFFFF); c.drawCircle(gx - cell * .04f, gy - cell * .04f, cell * .07f, fill);
        }
        if (now - novaAt < 520) {
            float k = (now - novaAt) / 520f, r = cell * 2.7f * GameKit.ease(k), x = px(hx), y = py(hy);
            fill.setColor(GameKit.alpha(0xFF5A0A1E, (1 - k) * .3f)); c.drawCircle(x, y, r, fill);
            stroke.setStrokeWidth(cell * .5f * (1 - k)); stroke.setColor(GameKit.alpha(0xFFE5173F, (1 - k) * .8f)); c.drawCircle(x, y, r, stroke);
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, 1 - k)); c.drawCircle(x, y, r * .86f, stroke);
        }
    }

    void drawBalls(Canvas c, long now) {
        for (int i = balls.size() - 1; i >= 0; i--) {
            Ball b = balls.get(i); float t = (now - b.at) / 560f;
            if (t >= 1) { balls.remove(i); continue; }
            float grow = GameKit.ease(t / .28f), fade = 1 - GameKit.easeIn(GameKit.clamp((t - .25f) / .75f));
            float cx = px(b.x), cy = py(b.y), r = b.r * cell * (.25f + .75f * grow) * (1 + .04f * (float) Math.sin(now / 28.0));
            fill.setShader(null);
            int[] L = flameLayers();
            fill.setColor(GameKit.alpha(L == FIRE ? 0xFFE5532B : L[0], fade * .85f)); c.drawCircle(cx, cy, r, fill);
            fill.setColor(GameKit.alpha(b.mega ? 0xFFFFD23F : L == FIRE ? 0xFFFF9A3D : L[1], fade * .95f)); c.drawCircle(cx, cy, r * .8f, fill);
            fill.setColor(GameKit.alpha(L == FIRE ? 0xFFFFD86B : L[2], fade)); c.drawCircle(cx, cy, r * .58f, fill);
            fill.setColor(GameKit.alpha(L == FIRE ? 0xFFFFF6D0 : L[3], fade)); c.drawCircle(cx, cy, r * .32f, fill);
            for (int k = 0; k < 10; k++) {
                double ang = k * Math.PI / 5 + now / 300.0; float rr = r * (.92f + .1f * (float) Math.sin(now / 60.0 + k * 1.9));
                fill.setColor(GameKit.alpha(k % 2 == 0 ? (L == FIRE ? 0xFFFF7A3D : L[1]) : (L == FIRE ? 0xFFFFC94A : L[2]), fade * .9f)); c.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr, r * .16f, fill);
            }
            stroke.setStrokeWidth(3 * d * (1 - t)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, (1 - t) * .7f)); c.drawCircle(cx, cy, b.r * cell * (.4f + .9f * GameKit.ease(t)), stroke);
        }
    }

    void stepGems(float dt, long now) {
        for (int i = gems.size() - 1; i >= 0; i--) {
            Gem g = gems.get(i);
            if (now - g.at > 14000) { gems.remove(i); continue; }
            float dist = (float) Math.hypot(hx - g.x - .5f, hy - g.y - .5f);
            if (heroAlive && (g.tapped || (dist < (angel() >= 2 ? 4.4f : 2.6f) && now - g.at > 300))) g.pull = Math.min(1f, g.pull + dt * (g.tapped ? 5f : 4f)); else g.pull = Math.max(0f, g.pull - dt * 3f);
            if (g.pull < 1f && !(heroAlive && dist < .5f)) continue;
            gems.remove(i);
            gemRun = now - gemAt < 2200 ? gemRun + 1 : 1; gemAt = now; gemCount++;
            int pts = 50 * Math.min(gemRun, 8) * (angel() >= 2 ? 2 : 1); addScore(pts);
            pop("+" + (now < doubleUntil ? pts * 2 : pts), px(hx), py(hy - .7f), GEM[g.hue], 14);
            burst(px(hx), py(hy), GEM[g.hue], 8, 160, 3);
            PixelStackSound.play(PixelStackSound.TING, .4f, Math.min(2f, 1f + .09f * gemRun)); GameKit.tick(a, .4f);
            if (gemCount % 10 == 0) {
                int[] from = GUIDE[rnd.nextFloat() < .7f ? 1 : 2];
                Prize p = new Prize(); p.x = (int) Math.floor(hx); p.y = (int) Math.floor(hy); p.kind = from[rnd.nextInt(from.length)]; p.at = now - 500; prizes.add(p);
                burst(px(hx), py(hy), 0xFFFFC531, 26, 300, 5); PixelStackSound.play(PixelStackSound.SUCCESS, .55f, 1.2f); shake(6);
            }
        }
    }
    void drawGems(Canvas c, long now) {
        for (Gem g : gems) {
            float e = GameKit.easeIn(g.pull), gx = g.x + .5f + (hx - g.x - .5f) * e, gy = g.y + .5f + (hy - g.y - .5f) * e;
            float age = now - g.at; if (age > 11000 && (now / 140) % 2 == 0) continue;
            float in = GameKit.spring(age / 400f), cx = px(gx), cy = py(gy) + (motion ? (float) Math.sin(now / 240.0 + g.x) * cell * .06f : 0), s = cell * .2f * in;
            int col = GEM[g.hue];
            fill.setShader(null); fill.setColor(GameKit.alpha(col, .22f)); c.drawCircle(cx, cy, s * 2f, fill);
            android.graphics.Path p = GameKit.path(); p.moveTo(cx, cy - s * 1.25f); p.lineTo(cx + s, cy); p.lineTo(cx, cy + s * 1.25f); p.lineTo(cx - s, cy); p.close();
            fill.setColor(col); c.drawPath(p, fill);
            android.graphics.Path q = GameKit.path(); q.moveTo(cx, cy - s * 1.25f); q.lineTo(cx + s, cy); q.lineTo(cx, cy - s * .1f); q.lineTo(cx - s, cy); q.close();
            fill.setColor(GameKit.mix(col, 0xFFFFFFFF, .55f)); c.drawPath(q, fill);
            if ((now / 90 + g.x * 3 + g.y) % 14 == 0) { fill.setColor(0xFFFFFFFF); c.drawCircle(cx - s * .3f, cy - s * .5f, s * .22f, fill); }
        }
        if (gemCount > 0 || !gems.isEmpty()) {
            float x = board.right - 46 * d, y = board.top + 16 * d, s = 5 * d;
            box.set(x - 12 * d, y - 10 * d, x + 36 * d, y + 10 * d);
            fill.setShader(null); fill.setColor(night ? 0x99000000 : 0xCCFFFFFF); c.drawRoundRect(box, 10 * d, 10 * d, fill);
            android.graphics.Path p = GameKit.path(); p.moveTo(x, y - s * 1.25f); p.lineTo(x + s, y); p.lineTo(x, y + s * 1.25f); p.lineTo(x - s, y); p.close();
            fill.setColor(GEM[0]); c.drawPath(p, fill);
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(11)); text.setTextAlign(android.graphics.Paint.Align.LEFT); text.setColor(night ? 0xFFFFFFFF : 0xFF14161C);
            c.drawText((gemCount % 10) + "/10", x + 9 * d, y + 4 * d, text);
            text.setTextAlign(android.graphics.Paint.Align.CENTER);
        }
    }

    void addFlame(int x, int y, int shape, long now, Bomb b) {
        Flame f = new Flame(); f.x = x; f.y = y; f.shape = shape; f.at = now; f.bomb = b.id; f.safe = b.struck; flames.add(f);
        if (b.poison) { Gas q = new Gas(); q.x = x; q.y = y; q.at = now + 300; gas.add(q); }
    }

    void shatter(int x, int y, long now) {
        crumbleAt[y][x] = now; meltAt[y][x] = 0; fieldDirty = true; addScore(20);
        float cx = px(x + .5f), cy = py(y + .5f);
        for (int k = 0; k < 8; k++) {
            GameKit.Bit b = new GameKit.Bit(); double ang = k * Math.PI / 4 + rnd.nextFloat() * .5f; float v = (110 + rnd.nextFloat() * 140) * d;
            b.x = cx; b.y = cy; b.vx = (float) Math.cos(ang) * v; b.vy = (float) Math.sin(ang) * v - 80 * d; b.size = cell * (.16f + rnd.nextFloat() * .14f); b.vr = (rnd.nextFloat() - .5f) * 900;
            b.colour = k % 3 == 0 ? 0xFFFFF3B0 : pillar; b.decay = 1.5f; b.gravity = 1f; bits.add(b);
        }
        burst(cx, cy, 0xFFFFE082, 10, 200, 3);
        PixelStackSound.play(PixelStackSound.SHOT, .3f, 1.25f); PixelStackSound.play(PixelStackSound.TING, .25f, 1.6f);
    }

    void crateBits(int x, int y) {
        float cx = px(x + .5f), cy = py(y + .5f);
        for (int k = 0; k < 4; k++) {
            GameKit.Bit b = new GameKit.Bit(); b.x = cx + (k % 2 == 0 ? -1 : 1) * cell * .2f; b.y = cy + (k / 2 == 0 ? -1 : 1) * cell * .2f;
            b.vx = (k % 2 == 0 ? -1 : 1) * (60 + rnd.nextFloat() * 80) * d; b.vy = -(120 + rnd.nextFloat() * 120) * d; b.size = cell * .34f; b.vr = (rnd.nextFloat() - .5f) * 600;
            b.colour = k % 3 == 0 ? crate2 : crate; b.decay = 1.6f; b.gravity = 1f; bits.add(b);
        }
        PixelStackSound.play(PixelStackSound.CHIP, .3f, .9f + rnd.nextFloat() * .3f);
    }

    void bossTick(Glitch g, float dt, long now) {
        bossMoves(g, dt, now);
        if (bossDoes(g, BK_SLAM)) {
            g.slamCool -= dt;
            if (g.slamCool <= 0) {
                g.slamCool = g.hp < g.maxHp / 2 ? 3.2f : 4.5f; g.slamAt = now;
                if (g.bossKind % 20 >= 3) hazRing((int) Math.floor(g.x), (int) Math.floor(g.y), 2, BOSS_ELEM[g.bossKind % 20], 500);
                int cx = (int) Math.floor(g.x), cy = (int) Math.floor(g.y), hcx = (int) Math.floor(hx), hcy = (int) Math.floor(hy);
                for (int y = cy - 2; y <= cy + 2; y++) for (int x = cx - 2; x <= cx + 2; x++) {
                    if (x < 1 || y < 1 || x >= W - 1 || y >= H - 1 || map[y][x] != FLOOR) continue;
                    int dd = Math.abs(x - cx) + Math.abs(y - cy);
                    if (dd < 2 || dd > 2 || Math.abs(x - hcx) + Math.abs(y - hcy) < 2) continue;
                    boolean busy = false; for (Bomb b : bombs) if (!b.done && b.x == x && b.y == y) busy = true;
                    for (Glitch o : glitches) if (o.alive && (int) Math.floor(o.x) == x && (int) Math.floor(o.y) == y) busy = true;
                    if (x == portalX && y == portalY) busy = true;
                    if (busy || rnd.nextFloat() < .35f) continue;
                    map[y][x] = CRATE; crumbleAt[y][x] = 0; fieldDirty = true;
                    burst(px(x + .5f), py(y + .5f), crate, 5, 120, 4);
                }
                shake(12); GameKit.rumble(a);
                PixelStackSound.play(PixelStackSound.THUD, .7f, .7f);
            }
        }
        if (bossDoes(g, BK_BLINK)) {
            g.teleCool -= dt;
            if (g.teleCool <= 0) {
                g.teleCool = 3.2f + rnd.nextFloat() * 1.2f;
                for (int tries = 0; tries < 200; tries++) {
                    int x = 1 + rnd.nextInt(W - 2), y = 1 + rnd.nextInt(H - 2);
                    if (map[y][x] != FLOOR || openAround(x, y) < 2 || Math.abs(x + .5f - hx) + Math.abs(y + .5f - hy) < 4) continue;
                    burst(px(g.x), py(g.y), 0xFFB15CFF, 14, 200, 4);
                    g.fromX = g.x; g.fromY = g.y; g.x = x + .5f; g.y = y + .5f; g.blinkAt = now; g.dir = rnd.nextInt(4);
                    burst(px(g.x), py(g.y), 0xFF35D0E0, 14, 200, 4);
                    PixelStackSound.play(PixelStackSound.SOFT, .45f, 1.6f);
                    break;
                }
            }
        }
    }
    boolean blinking(Glitch g) { return bossDoes(g, BK_BLINK) && SystemClock.uptimeMillis() - g.blinkAt < 550; }

    void hurt(Glitch g, int n) { hurt(g, n, flameInk()); }
    void hurt(Glitch g, int n, int ink) {
        if (!g.alive) return;
        if (g.boss && blinking(g)) return;
        long now = SystemClock.uptimeMillis();
        if (g.boss && now < g.shieldUntil) { burst(px(g.x), py(g.y), 0xFF7FE9FF, 6, 120, 3); PixelStackSound.play(PixelStackSound.TING, .35f, 1.5f); return; }
        if (!g.boss && g.type == 12 && g.bubble) { g.bubble = false; g.bubbleAt = now; g.hitAt = now; burst(px(g.x), py(g.y), 0xFF90CAF9, 12, 180, 3); PixelStackSound.play(PixelStackSound.POP, .4f, 1.3f); return; }
        if (!g.boss && g.type == 13 && g.hidden) revealMimic(g, now);
        g.hp -= n; g.hitAt = now;
        hitNumber(px(g.x), py(g.y) - cell * (g.boss ? 1.05f : g.type == 9 && g.form == 1 ? .8f : .6f), n, ink, g.hp <= 0 || n >= 3);
        if (!g.boss && g.type == 9 && g.form == 0 && g.hp > 0 && g.hp <= g.maxHp / 2) transform(g, now);
        if (bossDoes(g, BK_TWIN) && g.hp <= 0) for (Glitch o : glitches) if (o != g && o.alive && o.boss) { o.sp *= 1.6f; o.teleCool = Math.min(o.teleCool, 1.5f); burst(px(o.x), py(o.y), 0xFFFF2D55, 18, 240, 5); }
        if (bossDoes(g, BK_SPLIT) && g.hp > 0 && g.splits < 2 && g.hp <= g.maxHp * (2 - g.splits) / 3) { g.splits++; for (int k = 0; k < 3; k++) { Glitch m = new Glitch(); m.x = g.x; m.y = g.y; m.mini = true; m.type = 2; m.hp = m.maxHp = 1; m.sp = 3f; m.dir = k % 4; m.phase = rnd.nextFloat() * 6; m.hitAt = now; m.lastBomb = g.lastBomb; m.hitBy = g.hitBy == null ? null : g.hitBy.clone(); m.hitN = g.hitN; born.add(m); } burst(px(g.x), py(g.y), BOSS_INK[g.bossKind % 20], 20, 240, 5); PixelStackSound.play(PixelStackSound.BOING, .5f, .7f); }
        if (bossDoes(g, BK_SUMMON) && g.hp > 0) {
            shake(6);
            if (g.hp % 6 == 0) for (int k = 0; k < 2; k++) {
                Glitch m = new Glitch(); m.x = g.x; m.y = g.y; m.mini = true; m.type = 2; m.hp = m.maxHp = 1; m.sp = 3f; m.dir = rnd.nextInt(4); m.phase = rnd.nextFloat() * 6; m.hitAt = now; m.lastBomb = g.lastBomb; m.hitBy = g.hitBy == null ? null : g.hitBy.clone(); m.hitN = g.hitN; born.add(m);
            }
        }
        if (g.hp > 0) {
            burst(px(g.x), py(g.y), 0xFFFFFFFF, 6, 120, 3);
            PixelStackSound.play(PixelStackSound.TING, .3f, .8f + .1f * g.hp); GameKit.tick(a, .4f);
            return;
        }
        squash(g);
    }

    void squash(Glitch g) {
        if (!g.alive) return;
        long now = SystemClock.uptimeMillis();
        g.alive = false; g.deadAt = now;

        combo = now - lastKillAt < 1300 ? combo + 1 : 1; lastKillAt = now;
        if (combo >= 2) {
            comboAt = now; comboShown = combo;
            PixelStackSound.play(PixelStackSound.PERFECT, .45f, .9f + .08f * Math.min(combo, 8));
            for (int i = 0; i < 6 + combo * 2; i++) { GameKit.Bit b = spark(px(g.x), py(g.y), GameKit.RAINBOW[i % GameKit.RAINBOW.length], 260, 5); b.decay = 1.2f; }
        }
        int pts = (g.boss ? 3000 : g.type == 9 ? 500 * (g.form == 1 ? 2 : 1) * (1 + g.tier) : g.type == 8 ? 300 * (1 + g.tier) : (g.type == 0 ? 100 : g.type == 1 ? 200 : 400) * (1 + g.tier)) * Math.max(1, combo); addScore(pts);
        if (!g.boss && !g.mini && g.type == 8 && !g.freed) { g.freed = true; freeLights(g); }
        pop("+" + (now < doubleUntil ? pts * 2 : pts), px(g.x), py(g.y - .4f), night ? 0xFFFFFFFF : 0xFF14161C, g.boss ? 22 : 15);
        if (g.boss) { burst(px(g.x), py(g.y), 0xFFFFD23F, 40, 380, 7); shake(18); GameKit.rumble(a); PixelStackSound.play(PixelStackSound.BOOM, .6f, .7f); }
        if (!g.boss && !g.mini && g.type == 14) for (int k = 0; k < 3; k++) { Glitch m = new Glitch(); m.x = g.x; m.y = g.y; m.mini = true; m.type = 1; m.hp = m.maxHp = 1; m.sp = 3.2f; m.dir = k; m.phase = rnd.nextFloat() * 6; m.hitAt = now; m.lastBomb = g.lastBomb; m.hitBy = g.hitBy == null ? null : g.hitBy.clone(); m.hitN = g.hitN; born.add(m); }
        if (g.split && !g.mini && !g.boss) {
            for (int k = 0; k < 2; k++) {
                Glitch m = new Glitch(); m.x = g.x; m.y = g.y; m.mini = true; m.type = g.type; m.hp = m.maxHp = 1; m.sp = 3.4f; m.look = g.look; m.face = rnd.nextInt(FACES);
                m.dir = k == 0 ? (g.dir + 1) % 4 : (g.dir + 3) % 4; m.phase = rnd.nextFloat() * 6; m.hitAt = now; m.lastBomb = g.lastBomb; m.hitBy = g.hitBy == null ? null : g.hitBy.clone(); m.hitN = g.hitN;
                born.add(m);
            }
            pop(I18n.t("Split!"), px(g.x), py(g.y - .9f), 0xFFF2C230, 14);
        }
        kills++;
        if (rank < RANKS.length && kills >= RANKS[rank]) {
            rank++; rankAt = now;
            if (rank == 1 || rank == 4) bombsMax = Math.min(8, bombsMax + 1);
            if (rank == 2 || rank == 7) range = Math.min(8, range + 1);
            if (rank == 3 || rank == 8) speed = Math.min(6f, speed * 1.06f);
            levelUp(LV_PLAIN, I18n.t("Level up!"), I18n.t("Level {0}").replace("{0}", String.valueOf(rank + 1)));
            burst(px(hx), py(hy), 0xFFFFE066, 22, 220, 4);
            GameKit.thump(a, .8f);
        }
        if (rnd.nextFloat() < .25f) {
            int cx = (int) Math.floor(g.x), cy = (int) Math.floor(g.y);
            if (map[cy][cx] == FLOOR) { Prize p = new Prize(); p.x = cx; p.y = cy; p.kind = bonusKind(); p.at = g.deadAt + 300; prizes.add(p); }
        }
        burst(px(g.x), py(g.y), glitchColour(g), 14, 220, 4);
        PixelStackSound.play(PixelStackSound.POP, .5f, 1.1f + g.type * .1f);
        if (glitchesLeft() == 0) openPortal();
    }

    void die() {
        if (!heroAlive) return;
        if (overshield(SystemClock.uptimeMillis())) {
            overBrokenAt = SystemClock.uptimeMillis(); safeUntil = overBrokenAt + 1500;
            burst(px(hx), py(hy), wardColour(wardStyle()), 22, 260, 5); shake(6);
            PixelStackSound.play(PixelStackSound.TING, .55f, .8f); GameKit.thump(a, .7f);
            return;
        }
        if (angel() >= 1 && haloReady) {
            haloReady = false; haloAt = SystemClock.uptimeMillis(); safeUntil = haloAt + 1800;
            burst(px(hx), py(hy), 0xFFFFD23F, 22, 260, 5); shake(8);
            PixelStackSound.play(PixelStackSound.TING, .6f, 1.2f); PixelStackSound.play(PixelStackSound.SPARKLE, .5f, 1f); GameKit.thump(a, .8f);
            return;
        }
        heroAlive = false; heroDeadAt = SystemClock.uptimeMillis(); breathUntil = 0; poisonUntil = 0; shieldUntil = 0;
        soulDown();
        burst(px(hx), py(hy), GameKit.BLUE, 22, 260, 5); burst(px(hx), py(hy), GameKit.LIGHT, 14, 220, 4);
        shake(12); GameKit.rumble(a);
        PixelStackSound.play(PixelStackSound.OVER, .4f, 1.3f);
    }

    static final int[][] GUIDE = {{P_BOMB, P_FIRE, P_SPEED, P_LIFE, P_DOUBLE}, {P_POISON, P_BREATH, P_SHIELD, P_LASER, P_MEGA, P_FREEZE}, {P_FART, P_RAIN}};
    @Override int[][] guide() { return GUIDE; }
    @Override int tierOf(int kind) { for (int t = 0; t < GUIDE.length; t++) for (int k : GUIDE[t]) if (k == kind) return t; return 0; }
    @Override String abilityName(int kind) {
        switch (kind) {
            case P_BOMB: return I18n.t("+1 bomb"); case P_FIRE: return I18n.t("Bigger blast"); case P_SPEED: return I18n.t("Speed up"); case P_LIFE: return I18n.t("Extra life"); case P_DOUBLE: return I18n.t("Double points");
            case P_POISON: return I18n.t("Poison bombs!"); case P_BREATH: return I18n.t("Fire breath!"); case P_SHIELD: return I18n.t("Shield"); case P_LASER: return I18n.t("Laser eyes!"); case P_KICK: return I18n.t("Kick!"); case P_MEGA: return I18n.t("Mega bombs!"); case P_FREEZE: return I18n.t("Freeze");
            case P_FART: return I18n.t("Fart"); default: return I18n.t("Bomb rain!");
        }
    }
    @Override String abilityHint(int kind) {
        switch (kind) {
            case P_BOMB: return I18n.t("Drop one more bomb"); case P_FIRE: return I18n.t("Flames reach further"); case P_SPEED: return I18n.t("Run faster"); case P_LIFE: return I18n.t("One more try"); case P_DOUBLE: return I18n.t("Score counts twice");
            case P_POISON: return I18n.t("Bombs leave poison gas"); case P_BREATH: return I18n.t("Burn what you face"); case P_SHIELD: return I18n.t("Blocks every hit"); case P_LASER: return I18n.t("Burns through crates and ghosts"); case P_KICK: return I18n.t("Kick bombs away"); case P_MEGA: return I18n.t("Three huge bombs"); case P_FREEZE: return I18n.t("Stops every enemy");
            case P_FART: return I18n.t("Makes ghosts slow and dizzy"); default: return I18n.t("Bombs fall on ghosts");
        }
    }
    @Override int abilityColour(int kind) { return kind == P_FART ? 0xFF8FA64A : kind == P_RAIN ? 0xFFF28C28 : 0xFF4FB3F5; }
    @Override void abilityIcon(Canvas c, int kind, float x, float y, float r, float fade) { prizeIcon(c, kind, x, y, r * 1.15f, fade); text.setTextAlign(android.graphics.Paint.Align.CENTER); }
    @Override void useAbility(int kind) {
        String words;
        if (kind == P_FART) { fart(); words = I18n.t("Pfft!"); }
        else if (kind == P_RAIN) { bombRain(); words = I18n.t("Bomb rain!"); }
        else { freezeAt = SystemClock.uptimeMillis(); freezeUntil = freezeAt + 6000; PixelStackSound.play(PixelStackSound.SPARKLE, .6f, .7f); PixelStackSound.play(PixelStackSound.TING, .4f, 1.5f); shake(6); GameKit.thump(a, .7f); words = I18n.t("Freeze"); }
        pop(words, px(hx), py(hy - .8f), night ? GameKit.LIGHT : GameKit.BLUE, 17);
    }

    void take(Prize p) {
        String words;
        switch (p.kind) {
            case 1: bombsMax = Math.min(8, bombsMax + 1); words = I18n.t("+1 bomb"); break;
            case 2: { long t0 = SystemClock.uptimeMillis(); bigLevel = t0 < bigUntil ? Math.min(4, bigLevel + 1) : 1; bigUntil = t0 + 20000; words = I18n.t("Bigger blast") + " +" + bigLevel; break; }
            case 3: speed = Math.min(6f, speed + .6f); words = I18n.t("Speed up"); break;
            case P_POISON: poisonUntil = SystemClock.uptimeMillis() + 15000; words = I18n.t("Poison bombs!"); break;
            case P_BREATH: breathUntil = SystemClock.uptimeMillis() + 10000; words = I18n.t("Fire breath!"); break;
            case P_SHIELD: shieldUntil = SystemClock.uptimeMillis() + 8000; words = I18n.t("Shield"); break;
            case P_FREEZE: case P_FART: case P_RAIN:
                if (stash(p.kind)) words = abilityName(p.kind); else { useAbility(p.kind); words = null; }
                break;
            case P_DOUBLE: doubleUntil = SystemClock.uptimeMillis() + 12000; words = I18n.t("Double points"); break;
            case P_LASER: laserUntil = SystemClock.uptimeMillis() + LASER_MS; laserCool = 0; words = I18n.t("Laser eyes!"); break;
            case P_KICK: kick = true; words = I18n.t("Kick!"); break;
            case P_MEGA: megaLeft = 3; words = I18n.t("Mega bombs!"); break;
            default: lives++; words = I18n.t("Extra life");
        }
        addScore(200);
        int tier = tierOf(p.kind);
        if (words != null) pop(words, px(hx), py(hy - .9f), tier > 0 ? TIER_COLOUR[tier] : night ? GameKit.LIGHT : GameKit.BLUE, tier == 2 ? 21 : 17);
        if (tier == 2) { burst(px(hx), py(hy), 0xFFFFC531, 22, 260, 5); PixelStackSound.play(PixelStackSound.SUCCESS, .5f, 1.1f); }
        burst(px(p.x + .5f), py(p.y + .5f), GameKit.LIGHT, 12, 180, 4);
        PixelStackSound.play(PixelStackSound.PICK, .5f, 1f); GameKit.thump(a, .5f);
    }

    int glitchColour(Glitch g) { return g.boss ? BOSS_INK[g.bossKind % 20] : g.type == 10 ? 0xFF616161 : g.type == 11 ? 0xFF81D4FA : g.type == 12 ? 0xFF64B5F6 : g.type == 13 ? 0xFFFFB74D : g.type == 14 ? 0xFF8D6E63 : g.type == 15 ? 0xFF7E57C2 : g.type == 0 ? 0xFF2BB673 : g.type == 1 ? 0xFFF2C230 : g.type == 4 ? 0xFFB39DFF : g.type == 5 ? 0xFFFF8A3D : g.type == 6 ? 0xFFFF6FB5 : g.type == 7 ? 0xFF607D8B : g.type == 8 ? 0xFFE9E4FF : g.type == 9 ? (g.form == 1 ? 0xFF3A0A10 : 0xFFD32F2F) : 0xFFE53935; }

    void drawFloor(Canvas c, float fade) {
        float r = 14 * d;
        box.set(board); fill.setShader(null); fill.setColor(GameKit.alpha(floorA, fade)); c.drawRoundRect(box, r, r, fill);
        fill.setColor(GameKit.alpha(floorB, fade));
        for (int y = 1; y < H - 1; y++) for (int x = 1; x < W - 1; x++) if ((x + y) % 2 == 0) c.drawRect(px(x), py(y), px(x + 1), py(y + 1), fill);
    }

    android.graphics.Bitmap fieldPic; boolean fieldDirty = true; int fieldMode;

    void drawStatic(Canvas c) {
        int pw = (int) Math.ceil(board.width()), ph = (int) Math.ceil(board.height());
        if (pw <= 0 || ph <= 0) return;
        if (fieldPic == null || fieldPic.getWidth() != pw || fieldPic.getHeight() != ph) { fieldPic = android.graphics.Bitmap.createBitmap(pw, ph, android.graphics.Bitmap.Config.ARGB_8888); fieldDirty = true; }
        if (fieldDirty) {
            fieldDirty = false; fieldPic.eraseColor(0);
            Canvas fc = new Canvas(fieldPic); fc.translate(-board.left, -board.top);
            drawFloor(fc, 1f); fieldMode = 1; drawField(fc, 1f, null); fieldMode = 0;
        }
        c.drawBitmap(fieldPic, board.left, board.top, null);
    }
    void drawCrumbling(Canvas c) { fieldMode = 2; drawField(c, 1f, null); fieldMode = 0; }

    void drawField(Canvas c, float fade, float[] scale) {
        long now = SystemClock.uptimeMillis();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int v = map[y][x];
            if (v == FLOOR && crumbleAt[y][x] == 0) continue;
            if (fieldMode == 1 && crumbleAt[y][x] > 0) continue;
            if (fieldMode == 2 && crumbleAt[y][x] == 0) continue;
            float s = scale == null ? 1f : scale[y * W + x];
            if (s <= 0.01f) continue;
            float cx = px(x + .5f), cy = py(y + .5f), half = cell * .46f * s;
            if (v == PILLAR) {
                boolean border = x == 0 || y == 0 || x == W - 1 || y == H - 1;
                float k = crumbleAt[y][x] > 0 ? GameKit.clamp((now - crumbleAt[y][x]) / 420f) : 0f;
                if (k > 0) {
                    if (meltAt[y][x] > 0) {
                        float top = cy - half + 1.7f * half * GameKit.easeIn(k), wide = half * (1 + .4f * k);
                        block(c, cx - wide, top, cx + wide, cy + half, GameKit.mix(pillar, k < .5f ? 0xFFFF7A3D : 0xFFFFD86B, Math.min(1f, k * 1.7f)), fade * (1 - .7f * k));
                    } else {
                        half *= 1 - GameKit.easeIn(k);
                        if (half < 1) continue;
                        block(c, cx - half, cy - half, cx + half, cy + half, GameKit.mix(pillar, 0xFFFFF3B0, .85f * (1 - k)), fade);
                        stroke.setStrokeWidth(Math.max(1f, half * .08f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * (1 - k)));
                        c.drawLine(cx - half * .7f, cy - half * .5f, cx + half * .1f, cy + half * .05f, stroke); c.drawLine(cx + half * .1f, cy + half * .05f, cx + half * .6f, cy - half * .6f, stroke); c.drawLine(cx + half * .1f, cy + half * .05f, cx - half * .2f, cy + half * .7f, stroke);
                    }
                    continue;
                }
                int hot = heat[y][x], cr = crack[y][x];
                block(c, cx - half, cy - half, cx + half, cy + half, border ? edge : hot > 0 ? GameKit.mix(pillar, 0xFFFF5A1F, .7f * hot / (float) Math.max(1, heatNeed())) : pillar, fade);
                if (cr > 0) {
                    stroke.setStrokeWidth(Math.max(1f, half * .07f)); stroke.setColor(GameKit.alpha(0xFFFFF3B0, fade * .9f));
                    for (int q2 = 0; q2 < Math.min(4, cr); q2++) { float a1 = q2 * 1.7f + x + y, ax = (float) Math.cos(a1) * half * .8f, ay = (float) Math.sin(a1) * half * .8f; c.drawLine(cx, cy, cx + ax, cy + ay, stroke); c.drawLine(cx + ax * .55f, cy + ay * .55f, cx + ax * .55f + ay * .3f, cy + ay * .55f - ax * .3f, stroke); }
                }
            } else if (v == CRATE) {
                float k = crumbleAt[y][x] > 0 ? GameKit.clamp((now - crumbleAt[y][x]) / 420f) : 0f;
                if (k > 0) {
                    half *= 1 - GameKit.easeIn(k);
                    if (half < 1) continue;
                }
                float q = half * .9f, g2 = half * .08f;
                box.set(cx - half, cy - half, cx + half, cy + half); float rr = half * .3f;
                int hot = meltAt[y][x] > 0 ? 0xFFFF7A3D : 0xFFFFFFFF;
                fill.setShader(null); fill.setColor(GameKit.alpha(k > 0 ? GameKit.mix(crate, hot, .7f * (1 - k) + (meltAt[y][x] > 0 ? .3f : 0)) : GameKit.mix(crate, plate, .35f), fade)); c.drawRoundRect(box, rr, rr, fill);
                for (int i = 0; i < 4; i++) {
                    float l = cx - q + (i % 2) * (q + g2), t = cy - q + (i / 2) * (q + g2);
                    block(c, l + g2, t + g2, l + q - g2, t + q - g2, i == 0 || i == 3 ? crate : crate2, fade);
                }
            }
        }
    }

    void drawReach(Canvas c, Bomb b, long now) {
        int reach = reachOf(b);
        boolean pierce = pierces(b);
        if (b.kicked) {
            float so = b.slide >= 0 ? b.off : 0, bx = px(b.x + .5f + (b.slide >= 0 ? DX[b.slide] * so : 0)), by = py(b.y + .5f + (b.slide >= 0 ? DY[b.slide] * so : 0));
            float pulse = motion ? (float) (.5 + .5 * Math.sin(now / (b.fuse < .7f ? 55.0 : 150.0))) : .5f, r = ballReach(b) * cell;
            fill.setShader(null); fill.setColor(GameKit.alpha(b.mega ? 0xFFFFC94A : 0xFFFF8A3D, .1f + .08f * pulse)); c.drawCircle(bx, by, r, fill);
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(b.mega ? 0xFFFFC94A : 0xFFFF8A3D, .45f + .35f * pulse));
            for (int k = 0; k < 12; k++) c.drawArc(GameKit.rf(bx - r, by - r, bx + r, by + r), k * 30 + now / 20f % 360, 18, false, stroke);
            return;
        }
        float beat = motion ? (float) (.5 + .5 * Math.sin(now / (b.fuse < .7f ? 55.0 : 150.0))) : .5f, in = cell * .13f, rad = cell * .2f;
        int col = b.mega ? 0xFFFFC94A : b.poison ? 0xFF7BE36B : 0xFFFF8A3D;
        fill.setShader(null);
        box.set(px(b.x) + in, py(b.y) + in, px(b.x + 1) - in, py(b.y + 1) - in);
        fill.setColor(GameKit.alpha(col, .12f + .1f * beat)); c.drawRoundRect(box, rad, rad, fill);
        for (int k = 0; k < 4; k++) for (int i = 1; i <= reach; i++) {
            int x = b.x + DX[k] * i, y = b.y + DY[k] * i;
            if (x < 0 || y < 0 || x >= W || y >= H || map[y][x] == PILLAR) break;
            box.set(px(x) + in, py(y) + in, px(x + 1) - in, py(y + 1) - in);
            if (map[y][x] == CRATE) {
                stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFFFE08A, .55f + .4f * beat)); c.drawRoundRect(box, rad, rad, stroke);
                if (!pierce) break; continue;
            }
            fill.setColor(GameKit.alpha(col, .12f + .1f * beat)); c.drawRoundRect(box, rad, rad, fill);
            if (i == reach) { stroke.setStrokeWidth(1.5f * d); stroke.setColor(GameKit.alpha(col, .4f + .3f * beat)); c.drawRoundRect(box, rad, rad, stroke); }
        }
    }

    void drawBomb(Canvas c, Bomb b, long now, float fade) {
        float so = b.slide >= 0 ? b.off : 0;
        float cx = px(b.x + .5f + (b.slide >= 0 ? DX[b.slide] * so : 0)), cy = py(b.y + .5f + (b.slide >= 0 ? DY[b.slide] * so : 0));
        if (b.mega) {
            float k = (float) (.5 + .5 * Math.sin(now / 120.0));
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFF4D4D, fade * (.18f + .14f * k))); c.drawCircle(cx, cy, cell * (.5f + .06f * k), fill);
        }
        float hurry = 1 + (2.6f - b.fuse) * 2.2f;
        float pulse = 1 + .07f * (float) Math.sin((now - b.at) / 1000f * Math.PI * 2 * hurry);
        float drop = GameKit.spring((now - b.at) / 280f);
        float r = cell * (b.mega ? .42f : .36f) * pulse * drop;
        if (b.slide >= 0) {
            stroke.setStrokeWidth(Math.max(1.5f, cell * .06f)); stroke.setColor(GameKit.alpha(night ? 0xAAFFFFFF : 0x88000000, fade));
            for (int k = -1; k <= 1; k++) {
                float ox = DY[b.slide] * r * .55f * k, oy = DX[b.slide] * r * .55f * k, l0 = r * (1.2f + .3f * Math.abs(k));
                c.drawLine(cx - DX[b.slide] * l0 + ox, cy - DY[b.slide] * l0 + oy, cx - DX[b.slide] * (l0 + r * 1.1f) + ox, cy - DY[b.slide] * (l0 + r * 1.1f) + oy, stroke);
            }
        }
        int sl = soul != NONE ? soulLv : 0;
        if (sl > 0) {
            float q = motion ? (float) (.5 + .5 * Math.sin(now / 140.0 + b.id)) : .5f;
            fill.setShader(null);
            if (soul == ANGEL) {
                fill.setColor(GameKit.alpha(0xFFFFE9A8, fade * (.1f + .025f * sl + .08f * q))); c.drawCircle(cx, cy, r * (1.3f + .05f * sl), fill);
                if (motion && rnd.nextFloat() < .05f * sl) { GameKit.Bit sp = spark(cx + (rnd.nextFloat() - .5f) * r * 2, cy + (rnd.nextFloat() - .5f) * r * 2, rnd.nextBoolean() ? 0xFFFFFFFF : 0xFFFFE9A8, 20, 2.4f); sp.round = true; sp.gravity = -.3f; sp.decay = 2f; }
            } else {
                fill.setColor(GameKit.alpha(0xFFE5173F, fade * (.1f + .02f * sl + .08f * q))); c.drawCircle(cx, cy, r * (1.25f + .04f * sl), fill);
                int n = 3 + sl / 2;
                for (int k = 0; k < n; k++) {
                    double ang = k * Math.PI * 2 / n + now / 260.0; float fl = r * (.3f + .03f * sl) * (.8f + .4f * (float) Math.sin(now / 50.0 + k * 2.3)), bx0 = cx + (float) Math.cos(ang) * r * .9f, by0 = cy + (float) Math.sin(ang) * r * .9f;
                    android.graphics.Path f = GameKit.path(); f.moveTo(bx0 - (float) Math.sin(ang) * r * .16f, by0 + (float) Math.cos(ang) * r * .16f); f.quadTo(bx0 + (float) Math.cos(ang) * fl, by0 + (float) Math.sin(ang) * fl - fl * .5f, bx0 + (float) Math.sin(ang) * r * .16f, by0 - (float) Math.cos(ang) * r * .16f); f.close();
                    fill.setColor(GameKit.alpha(k % 2 == 0 ? 0xFFFF5A1F : 0xFFFFB347, fade * .9f)); c.drawPath(f, fill);
                }
            }
        }
        int body = b.mega ? 0xFFC62828 : b.poison ? 0xFF2F8A3A : night ? 0xFF3A4366 : 0xFF1B2440;
        if (sl > 0 && !b.mega && !b.poison) body = GameKit.mix(body, soul == ANGEL ? 0xFF233A7A : 0xFF3A0A10, Math.min(.85f, .09f * sl));
        if (b.fuse < .6f && (now / 70) % 2 == 0) body = GameKit.mix(body, 0xFFE53935, .6f);
        fill.setShader(null); fill.setColor(GameKit.alpha(0x33000000, fade)); c.drawOval(GameKit.rf(cx - r, cy + r * .7f, cx + r, cy + r * 1.05f), fill);
        fill.setColor(GameKit.alpha(body, fade)); c.drawCircle(cx, cy, r, fill);
        if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x88FFFFFF, fade)); c.drawCircle(cx, cy, r, stroke); }
        fill.setColor(GameKit.alpha(0x55FFFFFF, fade)); c.drawCircle(cx - r * .35f, cy - r * .35f, r * .28f, fill);
        if (sl > 0) {
            stroke.setStrokeWidth(Math.max(1.2f, r * (.06f + .006f * sl))); stroke.setColor(GameKit.alpha(soul == ANGEL ? 0xFFFFC531 : 0xFFE5173F, fade)); c.drawCircle(cx, cy, r, stroke);
            soulSign(c, cx, cy + r * .1f, r * .5f, soul, sl, now, fade);
        }

        float fl = Math.max(.1f, b.fuse / 2.6f);
        float fx = cx + r * .45f, fy = cy - r * .8f, ex = fx + cell * .14f * fl, ey = fy - cell * .2f * fl;
        stroke.setStrokeWidth(2.2f * d); stroke.setColor(GameKit.alpha(night ? 0xFFC9B8A0 : 0xFF8A6D4E, fade)); c.drawLine(fx, fy, ex, ey, stroke);
        float sp = cell * .08f * (1 + .4f * (float) Math.sin(now / 40.0));
        fill.setColor(GameKit.alpha(0xFFFFE066, fade)); c.drawCircle(ex, ey, sp, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(ex, ey, sp * .45f, fill);
        if (motion && rnd.nextFloat() < .35f) { GameKit.Bit s = spark(ex, ey, 0xFFFFC94A, 60, 2); s.round = true; s.decay = 3.5f; s.gravity = .2f; }
    }

    static final int[] FIRE = {0xFFFF5A36, 0xFFFFA23D, 0xFFFFE27A, 0xFFFFFFFF}, HOLY = {0xFFFFB300, 0xFFFFE27A, 0xFFFFF8E1, 0xFFFFFFFF}, HELL = {0xFF7A0A1E, 0xFFD4122F, 0xFFFF5A1F, 0xFFFFC94A};
    final int[] flameBuf = new int[4];
    int[] flameLayers() {
        int[] to = soul == ANGEL ? HOLY : soul == DEMON ? HELL : null; if (to == null || soulLv <= 0) return FIRE;
        float k = Math.min(1f, soulLv / 4f); for (int i = 0; i < 4; i++) flameBuf[i] = GameKit.mix(FIRE[i], to[i], k); return flameBuf;
    }
    void drawFlame(Canvas c, Flame f, long now, float fade) {
        float t = (now - f.at) / 620f;
        if (t >= 1 || f.shape == 7) return;
        float grow = t < .15f ? GameKit.ease(t / .15f) : 1 - GameKit.easeIn((t - .15f) / .85f) * .9f;
        float flick = 1 + .08f * (float) Math.sin(now / 30.0 + f.x * 1.7 + f.y);
        float cx = px(f.x + .5f), cy = py(f.y + .5f), h = cell * .5f;
        float w0 = cell * .42f * grow * flick;
        int[] layers = flameLayers();
        float[] widths = {1f, .74f, .5f, .24f};
        for (int i = 0; i < 4; i++) {
            float w = w0 * widths[i];
            fill.setShader(null); fill.setColor(GameKit.alpha(layers[i], fade * (i == 3 ? .85f : 1f)));
            switch (f.shape) {
                case 0: c.drawCircle(cx, cy, w * 1.1f, fill); break;
                case 1: box.set(cx - h, cy - w, cx + h, cy + w); c.drawRect(box, fill); break;
                case 2: box.set(cx - w, cy - h, cx + w, cy + h); c.drawRect(box, fill); break;
                case 3: box.set(cx - h, cy - w, cx + h * .8f, cy + w); c.drawRoundRect(box, w, w, fill); break;
                case 4: box.set(cx - w, cy - h, cx + w, cy + h * .8f); c.drawRoundRect(box, w, w, fill); break;
                case 5: box.set(cx - h * .8f, cy - w, cx + h, cy + w); c.drawRoundRect(box, w, w, fill); break;
                default: box.set(cx - w, cy - h * .8f, cx + w, cy + h); c.drawRoundRect(box, w, w, fill);
            }
        }
    }

    int angelSkin(int an) {
        int to = an >= TOP ? 0xFFFFF6DC : an >= 7 ? 0xFFF4F7FF : an >= 4 ? 0xFFD8ECFF : 0xFF9FD0FF;
        return GameKit.mix(GameKit.BLUE, to, Math.min(1f, .3f + .07f * an));
    }

    void drawHero(Canvas c, long now, float fade, float scale) {
        if (!heroAlive) return;
        if (now < safeUntil && now - heroBornAt > 500 && (now / 90) % 2 == 0) fade *= .45f;
        float cx = px(hx), cy = py(hy), s = cell * .78f * scale;
        float born = GameKit.spring((now - heroBornAt) / 500f); s *= born;

        boolean kicking = now - kickAt >= 0 && now - kickAt < KICK_MS;
        float kp = kicking ? (now - kickAt) / (float) KICK_MS : 1f;

        float back = !kicking ? 0f : !motion ? 0f : kp < .14f ? GameKit.ease(kp / .14f) : 1 - GameKit.ease(GameKit.clamp((kp - .14f) / .12f));
        float kickK = !kicking ? 0f : !motion ? 1f : kp < .14f ? 0f : kp < .26f ? GameKit.ease((kp - .14f) / .12f) : 1 - GameKit.ease((kp - .26f) / .74f);
        if (kicking) { float push = s * (.16f * kickK - .1f * back); cx += DX[kickDir] * push; cy += DY[kickDir] * push; }
        boolean walking = dir >= 0 && state == PLAY;
        float bob = walking && motion ? (float) Math.abs(Math.sin(walk * Math.PI * 1.6)) * cell * .07f : 0;
        float squash = walking && motion ? 1 + .05f * (float) Math.sin(walk * Math.PI * 3.2) : 1;

        fill.setShader(null); fill.setColor(GameKit.alpha(0x30000000, fade)); c.drawOval(GameKit.rf(cx - s * .4f, cy + s * .38f, cx + s * .4f, cy + s * .52f), fill);
        float step = walking ? (float) Math.sin(walk * Math.PI * 1.6) * s * .1f : 0;
        fill.setColor(GameKit.alpha(GameKit.mix(GameKit.BLUE, 0xFF000000, .3f), fade));
        c.drawCircle(cx - s * .2f, cy + s * .42f + step * .3f, s * .11f, fill); c.drawCircle(cx + s * .2f, cy + s * .42f - step * .3f, s * .11f, fill);
        float top = cy - s * .45f - bob, bot = cy + s * .4f - bob, half = s * .44f * squash;
        if (rank >= 9) { float q = (float) (.5 + .5 * Math.sin(now / 240.0)); fill.setColor(GameKit.alpha(0xFFFFD23F, fade * (.14f + .1f * q))); c.drawCircle(cx, cy - bob, s * (.66f + .05f * q), fill); }
        if (rank >= 7 && angel() < 2) for (int side = -1; side <= 1; side += 2) {
            float flapW = motion ? (float) Math.sin(now / 170.0) * s * .06f : 0;
            android.graphics.Path wg = GameKit.path(); float wx = cx + side * half * .8f, wy = cy - s * .12f - bob;
            wg.moveTo(wx, wy); wg.lineTo(wx + side * s * .46f, wy - s * .3f - flapW); wg.lineTo(wx + side * s * .4f, wy - s * .02f); wg.lineTo(wx + side * s * .32f, wy + s * .16f + flapW); wg.lineTo(wx, wy + s * .16f); wg.close();
            fill.setColor(GameKit.alpha(demon() > 0 ? 0xFF3A0A10 : 0xFFF4F7FF, fade * .92f)); c.drawPath(wg, fill);
        }
        drawSoul(c, cx, cy, s, top, half, bob, now, fade, true);
        int an = angel(), dm = demon();
        int skin = dm > 0 ? GameKit.mix(GameKit.mix(GameKit.BLUE, 0xFF7A1020, Math.min(1f, .14f * dm)), 0xFF14060A, dm >= 8 ? .32f : 0f) : an > 0 ? angelSkin(an) : GameKit.BLUE;
        if (now - absorbAt < 500) skin = GameKit.mix(skin, 0xFFFFE9A8, .6f * (1 - (now - absorbAt) / 500f));
        if (now - eatAt < 260) { float gulp = (float) Math.sin(Math.PI * (now - eatAt) / 260f); half *= 1 + .16f * gulp; }
        if (rank >= 2) {
            float flap = walking && motion ? (float) Math.sin(walk * Math.PI * 3) * s * .06f : 0;
            android.graphics.Path cape = GameKit.path(); cape.moveTo(cx - half * .9f, top + s * .15f); cape.lineTo(cx + half * .9f, top + s * .15f); cape.lineTo(cx + half * 1.15f + flap, bot + s * .05f); cape.lineTo(cx - half * 1.15f - flap, bot + s * .05f); cape.close();
            fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawPath(cape, fill);
        }
        if (now - rankAt < 700) { float k = 1 - (now - rankAt) / 700f; block(c, cx - half, top, cx + half, bot, GameKit.mix(skin, 0xFFFFFFFF, .6f * k), fade); }
        else block(c, cx - half, top, cx + half, bot, skin, fade);
        drawSoul(c, cx, cy, s, top, half, bob, now, fade, false);
        if (rank >= 1) {
            box.set(cx - half, top + s * .1f, cx + half, top + s * .19f);
            fill.setColor(GameKit.alpha(0xFFFF5A5A, fade)); c.drawRect(box, fill);
            fill.setColor(GameKit.alpha(0xFFFF5A5A, fade)); c.drawCircle(cx + half, top + s * .145f, s * .06f, fill);
        }
        if (rank >= 3) {
            box.set(cx - half, bot - s * .2f, cx + half, bot - s * .11f); fill.setColor(GameKit.alpha(0xFF14161C, fade * .85f)); c.drawRect(box, fill);
            box.set(cx - s * .06f, bot - s * .22f, cx + s * .06f, bot - s * .09f); fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawRoundRect(box, s * .02f, s * .02f, fill);
        }
        if (rank >= 4 && demon() == 0) for (int side = -1; side <= 1; side += 2) {
            box.set(cx + side * half - s * .1f, top + s * .2f, cx + side * half + s * .1f, top + s * .36f);
            fill.setColor(GameKit.alpha(0xFFE8EEF8, fade)); c.drawRoundRect(box, s * .05f, s * .05f, fill);
            fill.setColor(GameKit.alpha(0xFF8FA0BD, fade)); c.drawCircle(box.centerX(), box.centerY(), s * .025f, fill);
        }
        if (rank >= 6) {
            android.graphics.Path cr = GameKit.path(); float cw = s * .42f, cb = top + s * .02f, ch = s * .22f;
            cr.moveTo(cx - cw / 2, cb); cr.lineTo(cx - cw / 2, cb - ch * .7f); cr.lineTo(cx - cw / 4, cb - ch * .35f); cr.lineTo(cx, cb - ch); cr.lineTo(cx + cw / 4, cb - ch * .35f); cr.lineTo(cx + cw / 2, cb - ch * .7f); cr.lineTo(cx + cw / 2, cb); cr.close();
            fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawPath(cr, fill);
        }
        float ex = DX[face] * s * .1f, ey = DY[face] * s * .07f;
        boolean farting = now - fartAt >= 0 && now - fartAt < 900;
        boolean fire = breathing(), wide = fire || mood == 1, lasers = now < laserUntil;
        float eyeW = wide ? .13f : .1f, eyeH = wide ? .16f : .13f, pupil = wide ? .04f : .055f;
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * s * .16f + ex, y = cy - s * (wide ? .12f : .08f) - bob + ey;
            if (farting && fartKind != 2) {
                stroke.setStrokeWidth(Math.max(1.5f, s * .05f)); stroke.setColor(GameKit.alpha(0xFF14161C, fade));
                if (fartKind == 0) { c.drawLine(x - side * s * .1f, y - s * .08f, x + side * s * .07f, y, stroke); c.drawLine(x + side * s * .07f, y, x - side * s * .1f, y + s * .08f, stroke); }
                else c.drawArc(GameKit.rf(x - s * .1f, y - s * .07f, x + s * .1f, y + s * .11f), 200, 140, false, stroke);
                continue;
            }
            if (kicking && !lasers) {
                float kx = DX[kickDir] * s * .04f, ky = DY[kickDir] * s * .03f;
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawOval(GameKit.rf(x - s * .12f, y - s * .045f, x + s * .12f, y + s * .05f), fill);
                fill.setColor(GameKit.alpha(dm >= 1 ? 0xFFFF2D55 : 0xFF14161C, fade)); c.drawCircle(x + kx, y + ky, s * .045f, fill);
                stroke.setStrokeWidth(s * .07f); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawLine(x - side * s * .15f, y - s * .19f, x + side * s * .11f, y - s * .06f, stroke);
                continue;
            }
            if (dm > 0 && !lasers) { demonEye(c, x, y, side, s, dm, ex, ey, now, fade); continue; }
            if (an > 0 && (lasers || fire)) { holyEye(c, x, y, side, s, an, now, fade, lasers); continue; }
            if (an >= 6) { fill.setColor(GameKit.alpha(0xFFFFE9A8, fade * (.25f + .1f * (float) Math.sin(now / 200.0 + side)))); c.drawCircle(x, y, s * (eyeW + .07f), fill); }
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawOval(GameKit.rf(x - s * eyeW, y - s * eyeH, x + s * eyeW, y + s * eyeH), fill);
            if (an > 0) {
                float ir = s * pupil * 1.55f, ix = x + ex * .5f, iy = y + ey * .5f;
                fill.setColor(GameKit.alpha(0xFF4FB3FF, fade)); c.drawCircle(ix, iy, ir, fill);
                if (an >= 4) { stroke.setStrokeWidth(Math.max(.8f, s * .015f)); stroke.setColor(GameKit.alpha(0xFFFFD54F, fade)); c.drawCircle(ix, iy, ir, stroke); }
                fill.setColor(GameKit.alpha(0xFF0D2A4A, fade)); c.drawCircle(ix, iy, ir * .45f, fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(ix - ir * .35f, iy - ir * .38f, ir * .3f, fill);
                continue;
            }
            fill.setColor(GameKit.alpha(lasers ? (an > 0 ? 0xFFFFC531 : 0xFFFF2D55) : an >= TOP ? 0xFF3A8DFF : 0xFF14161C, fade)); c.drawCircle(x + ex * .5f, y + ey * .5f, s * pupil * (lasers ? 1.3f : 1f), fill);
            if (an >= 6 && !lasers) { fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x + ex * .5f - s * .02f, y + ey * .5f - s * .025f, s * .022f, fill); }
            if (kicking) { stroke.setStrokeWidth(s * .05f); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawLine(x - side * s * .13f, y - s * (eyeH + .07f), x + side * s * .1f, y - s * (eyeH - .03f), stroke); }
            if (lasers) { fill.setColor(GameKit.alpha(an > 0 ? 0xFFFFD54F : 0xFFFF2D55, fade * (.25f + .15f * (float) Math.sin(now / 90.0)))); c.drawCircle(x + ex * .5f, y + ey * .5f, s * (an > 0 ? .22f : .16f), fill); }
        }
        if (lasers && laserEnd > 0 && state == PLAY) {
            float flare = motion ? 1 - GameKit.clamp((now - beamAt) / 180f) : 0f, hum = motion ? .85f + .15f * (float) Math.sin(now / 35.0) : 1f;
            float tx = px(hx + DX[face] * laserEnd), ty = py(hy + DY[face] * laserEnd);
            if (an > 0) {
                float bx = cx + ex, by = cy - s * (wide ? .12f : .08f) - bob + ey;
                float endX = face % 2 == 1 ? tx : bx, endY = face % 2 == 1 ? by : ty, L = (float) Math.hypot(endX - bx, endY - by);
                float ang = face == 1 ? 0 : face == 2 ? 90 : face == 3 ? 180 : -90, w = s * (.2f + .022f * an + .1f * flare) * hum;
                int sv = c.save(); c.translate(bx, by); c.rotate(ang);
                fill.setShader(null);
                for (int k = 0; k < 4; k++) {
                    float hw = w * (k == 0 ? 1.9f : k == 1 ? 1.15f : k == 2 ? .6f : .22f), h0 = hw * (k == 0 ? .45f : .3f);
                    android.graphics.Path bp = GameKit.path();
                    bp.moveTo(0, -h0); bp.lineTo(L, -hw); bp.quadTo(L + hw * .9f, 0, L, hw); bp.lineTo(0, h0); bp.close();
                    fill.setColor(GameKit.alpha(k == 0 ? 0x33FFD54F : k == 1 ? 0x88FFC531 : k == 2 ? 0xDDFFE082 : 0xFFFFFDE7, fade)); c.drawPath(bp, fill);
                }
                if (an >= 8) {
                    float br = motion ? .5f + .5f * (float) Math.sin(now / 160.0) : .5f, hw = w * (2.6f + .4f * br);
                    android.graphics.Path bp = GameKit.path(); bp.moveTo(0, -hw * .4f); bp.lineTo(L, -hw); bp.quadTo(L + hw, 0, L, hw); bp.lineTo(0, hw * .4f); bp.close();
                    fill.setColor(GameKit.alpha(0x22FFF3B0, fade)); c.drawPath(bp, fill);
                }
                final int rings = 2 + an / 3;
                for (int k = 0; k < rings; k++) {
                    float ph = motion ? ((now / 380f) + k / (float) rings) % 1f : (k + .5f) / rings, rx = L * ph, rr = w * (.55f + .75f * ph);
                    stroke.setStrokeWidth(Math.max(1.2f, s * .03f)); stroke.setColor(GameKit.alpha(0xFFFFF3B0, fade * (1 - ph) * .85f));
                    c.drawOval(GameKit.rf(rx - rr * .28f, -rr, rx + rr * .28f, rr), stroke);
                }
                c.restoreToCount(sv);
                if (an >= 5) {
                    int sv2 = c.save(); c.rotate(motion ? now / 12f % 360 : 0, endX, endY); fill.setColor(GameKit.alpha(0x66FFE082, fade));
                    for (int k = 0; k < 8; k++) { android.graphics.Path ray = GameKit.path(); float rl = s * (.6f + .05f * an) * (k % 2 == 0 ? 1f : .65f); ray.moveTo(endX - s * .06f, endY); ray.lineTo(endX, endY - rl); ray.lineTo(endX + s * .06f, endY); ray.close(); c.drawPath(ray, fill); c.rotate(45, endX, endY); }
                    c.restoreToCount(sv2);
                }
                fill.setColor(GameKit.alpha(0x55FFD54F, fade * (.6f + .3f * flare))); c.drawCircle(endX, endY, s * (.38f + .025f * an + .14f * flare), fill);
                fill.setColor(GameKit.alpha(0xFFFFE082, fade)); c.drawCircle(endX, endY, s * (.2f + .06f * flare), fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(endX, endY, s * .09f, fill);
                if (motion && rnd.nextFloat() < .3f + .05f * an) { GameKit.Bit b = spark(endX, endY, rnd.nextBoolean() ? 0xFFFFD54F : 0xFFFFFFFF, 140, 2.6f); b.round = true; b.gravity = -.1f; b.decay = 2.6f; }
            } else { for (int side = -1; side <= 1; side += 2) {
                float x0 = cx + side * s * .16f + ex, y0 = cy - s * (wide ? .12f : .08f) - bob + ey;
                float x1 = face % 2 == 1 ? tx : x0, y1 = face % 2 == 1 ? y0 : ty;
                stroke.setStrokeWidth(s * (.2f + .12f * flare) * hum); stroke.setColor(GameKit.alpha(0x55FF2D55, fade)); c.drawLine(x0, y0, x1, y1, stroke);
                stroke.setStrokeWidth(s * (.08f + .05f * flare)); stroke.setColor(GameKit.alpha(0xFFFF4D6D, fade)); c.drawLine(x0, y0, x1, y1, stroke);
                stroke.setStrokeWidth(s * .03f); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawLine(x0, y0, x1, y1, stroke);
            }
            if (face % 2 == 1) ty = cy - s * (wide ? .12f : .08f) - bob + ey; else tx = cx + ex;
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFF4D6D, fade * (.35f + .35f * flare))); c.drawCircle(tx, ty, s * (.18f + .12f * flare), fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * (.6f + .4f * flare))); c.drawCircle(tx, ty, s * .07f, fill);
            if (motion && rnd.nextFloat() < .45f) { GameKit.Bit b = spark(tx, ty, rnd.nextBoolean() ? 0xFFFF4D6D : 0xFFFFE9A8, 120, 2.6f); b.round = true; b.gravity = .3f; b.decay = 3f; } }
        }
        if (now - eatAt < 300) {
            float k = (float) Math.sin(Math.PI * (now - eatAt) / 300f), my = cy + s * .2f - bob, mw = s * (.12f + .1f * k), mh = s * (.05f + .13f * k);
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - mw + ex * .4f, my - mh, cx + mw + ex * .4f, my + mh), fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade));
            for (int side = -1; side <= 1; side += 2) { android.graphics.Path f = GameKit.path(); float fx = cx + side * mw * .55f + ex * .4f; f.moveTo(fx - s * .035f, my - mh * .8f); f.lineTo(fx + s * .035f, my - mh * .8f); f.lineTo(fx, my - mh * .8f + s * .09f); f.close(); c.drawPath(f, fill); }
        } else if (farting) {
            float my = cy + s * .2f - bob;
            if (fartKind != 2) { fill.setColor(GameKit.alpha(0xFFFF8FB3, fade * .6f)); for (int side = -1; side <= 1; side += 2) c.drawCircle(cx + side * s * .3f + ex * .4f, cy + s * .08f - bob, s * .07f, fill); }
            if (fartKind == 0) { fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - s * .05f + ex * .4f, my - s * .06f, cx + s * .05f + ex * .4f, my + s * .06f), fill); }
            else if (fartKind == 1) { stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawArc(GameKit.rf(cx - s * .13f + ex * .4f, my - s * .14f, cx + s * .13f + ex * .4f, my + s * .08f), 20, 140, false, stroke); }
            else {
                float mw = s * .2f, mh = s * (.11f + .02f * (float) Math.sin(now / 40.0));
                fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - mw + ex * .4f, my - mh, cx + mw + ex * .4f, my + mh), fill);
                fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawOval(GameKit.rf(cx - mw * .55f + ex * .4f, my, cx + mw * .55f + ex * .4f, my + mh * .85f), fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path fg = GameKit.path(); float fx = cx + side * mw * .55f + ex * .4f; fg.moveTo(fx - s * .035f, my - mh * .8f); fg.lineTo(fx + s * .035f, my - mh * .8f); fg.lineTo(fx, my - mh * .15f); fg.close(); c.drawPath(fg, fill); }
            }
        } else if (kicking) {
            float my = cy + s * .2f - bob, open = .4f + .6f * Math.max(kickK, back * .6f), mw = s * (.12f + .05f * open), mh = s * (.05f + .08f * open), mx = cx + DX[kickDir] * s * .04f;
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(mx - mw, my - mh, mx + mw, my + mh), fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawRect(mx - mw * .7f, my - mh * .85f, mx + mw * .7f, my - mh * .4f, fill);
            fill.setColor(GameKit.alpha(0xFFFF6B8B, fade)); c.drawOval(GameKit.rf(mx - mw * .45f, my + mh * .15f, mx + mw * .45f, my + mh * .8f), fill);
            fill.setColor(GameKit.alpha(0x77FF4D6D, fade)); for (int side = -1; side <= 1; side += 2) c.drawCircle(cx + side * s * .3f, cy + s * .08f - bob, s * .065f, fill);
            stroke.setStrokeWidth(Math.max(1.2f, s * .035f)); stroke.setColor(GameKit.alpha(night ? 0xCCFFFFFF : 0xAA14161C, fade * Math.max(kickK, back)));
            float ox = -DX[kickDir] != 0 ? -DX[kickDir] : 1;
            for (int k = 0; k < 3; k++) { float ax = cx + ox * s * (.5f + .02f * k), ay = top + s * (.05f + .12f * k); c.drawLine(ax, ay, ax + ox * s * .14f, ay - s * .05f, stroke); }
        } else if (mood == 1) {
            float my = cy + s * .22f - bob;
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - s * .07f, my - s * .09f, cx + s * .07f, my + s * .09f), fill);
        } else if (wide && an > 0 && fire) {
            float mo = s * (.1f + .03f * (float) Math.sin(now / 45.0)), my = cy + s * .2f - bob, mx = cx + ex * .4f;
            fill.setColor(GameKit.alpha(0xFFFFE082, fade * (.35f + .02f * an))); c.drawCircle(mx, my, s * (.18f + .01f * an), fill);
            fill.setColor(GameKit.alpha(0xFFC9A23A, fade)); c.drawOval(GameKit.rf(mx - s * .12f, my - mo, mx + s * .12f, my + mo), fill);
            fill.setColor(GameKit.alpha(0xFFFFF6D0, fade)); c.drawOval(GameKit.rf(mx - s * .09f, my - mo * .75f, mx + s * .09f, my + mo * .75f), fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawOval(GameKit.rf(mx - s * .05f, my - mo * .45f, mx + s * .05f, my + mo * .45f), fill);
        } else if (wide) {
            float mo = s * (.1f + .03f * (float) Math.sin(now / 45.0)), my = cy + s * .2f - bob;
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - s * .13f + ex * .4f, my - mo, cx + s * .13f + ex * .4f, my + mo), fill);
            fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawOval(GameKit.rf(cx - s * .07f + ex * .4f, my, cx + s * .07f + ex * .4f, my + mo * .8f), fill);
        } else if (an > 0 && lasers) {
            stroke.setStrokeWidth(Math.max(1.5f, s * .045f)); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawLine(cx - s * .07f + ex * .4f, cy + s * .18f - bob, cx + s * .07f + ex * .4f, cy + s * .18f - bob, stroke);
        } else if (fire) {
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawArc(GameKit.rf(cx - s * .1f, cy + s * .08f - bob, cx + s * .1f, cy + s * .24f - bob), 20, 140, false, stroke);
        } else if (dizzy()) {
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawArc(GameKit.rf(cx - s * .12f, cy + s * .06f - bob, cx + s * .12f, cy + s * .26f - bob), 10, 160, false, stroke);
        } else if (dm >= 1) {
            demonMouth(c, cx, cy + s * .17f - bob, s, dm, ex, now, fade);
        } else if (an >= 1) {
            stroke.setStrokeWidth(Math.max(1.5f, s * .045f)); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawArc(GameKit.rf(cx - s * .09f + ex * .4f, cy + s * .1f - bob, cx + s * .09f + ex * .4f, cy + s * .24f - bob), 20, 140, false, stroke);
        } else { fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(cx, cy + s * .2f - bob, s * .05f, fill); }
        if (rank >= 8) for (int i = 0; i < 5; i++) {
            double ang = now / 360.0 + i * Math.PI * 2 / 5; fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFE066, fade * .9f));
            c.drawCircle(cx + (float) Math.cos(ang) * s * .72f, cy - bob + (float) Math.sin(ang) * s * .72f, s * .04f, fill);
        }
        if (rank >= 5) {
            float wait = rank >= 9 ? 10000f : 20000f, k = Math.min(1f, (now - overBrokenAt) / wait);
            ward(c, cx, cy - bob, s * .82f, wardStyle(), k, now, fade, Math.max(1.5f, s * .04f));
        }
        if (kicking) {
            int legCol = GameKit.mix(GameKit.BLUE, 0xFF000000, .3f);
            float hipX = cx + (kickDir % 2 == 1 ? DX[kickDir] * s * .12f : s * .14f), hipY = cy + s * .28f - bob;
            float reach = s * (.18f + .5f * kickK - .16f * back);
            float fx = hipX + DX[kickDir] * reach, fy = hipY + (kickDir % 2 == 1 ? s * .08f * (1 - kickK) - s * .1f * kickK : DY[kickDir] * reach);
            stroke.setStrokeWidth(s * .17f); stroke.setColor(GameKit.alpha(legCol, fade)); c.drawLine(hipX, hipY, fx, fy, stroke);
            fill.setColor(GameKit.alpha(legCol, fade)); c.drawOval(GameKit.rf(fx - s * .16f + DX[kickDir] * s * .04f, fy - s * .11f, fx + s * .16f + DX[kickDir] * s * .04f, fy + s * .11f), fill);
            fill.setColor(GameKit.alpha(0x55FFFFFF, fade)); c.drawCircle(fx - s * .04f, fy - s * .04f, s * .045f, fill);
            stroke.setStrokeWidth(Math.max(1.5f, s * .045f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * .8f * kickK));
            for (int k = -1; k <= 1; k++) {
                float ox = DY[kickDir] * s * .1f * k, oy = DX[kickDir] * s * .1f * k;
                c.drawLine(fx - DX[kickDir] * s * .24f + ox, fy - DY[kickDir] * s * .24f + oy, fx - DX[kickDir] * s * .46f + ox, fy - DY[kickDir] * s * .46f + oy, stroke);
            }
            if (kp > .2f && kp < .48f && motion) {
                float q = (kp - .2f) / .28f, sx2 = fx + DX[kickDir] * s * .3f, sy2 = fy + DY[kickDir] * s * .3f, rr = s * (.18f + .2f * q);
                fill.setColor(GameKit.alpha(0xFFFFE066, fade * (1 - q)));
                android.graphics.Path st = GameKit.path();
                for (int k = 0; k < 10; k++) { double ang = k * Math.PI / 5 - Math.PI / 2; float rad = k % 2 == 0 ? rr : rr * .45f; float qx = sx2 + (float) Math.cos(ang) * rad, qy = sy2 + (float) Math.sin(ang) * rad; if (k == 0) st.moveTo(qx, qy); else st.lineTo(qx, qy); }
                st.close(); c.drawPath(st, fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * (1 - q))); c.drawCircle(sx2, sy2, rr * .3f, fill);
            }
        }
        if (now - eatAt < 1500) {
            float age = (now - eatAt) / 1500f, a0 = fade * (1 - GameKit.easeIn(age)), my = cy + s * .2f - bob;
            float[][] spl = {{-.2f, .02f, .05f}, {.19f, -.01f, .045f}, {-.11f, .12f, .035f}, {.12f, .13f, .04f}, {.26f, .08f, .03f}, {-.27f, .1f, .028f}, {0f, .16f, .03f}};
            for (int i = 0; i < spl.length; i++) {
                float grow = GameKit.clamp(age * 12 - i * .12f); if (grow <= 0) continue;
                fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFC4122F : 0xFF8E0D22, a0));
                c.drawCircle(cx + spl[i][0] * s + ex * .4f, my + spl[i][1] * s, spl[i][2] * s * grow, fill);
            }
            for (int side = -1; side <= 1; side += 2) {
                float dx0 = cx + side * s * .12f + ex * .4f, len = s * .16f * GameKit.clamp(age * 2.2f);
                box.set(dx0 - s * .018f, my + s * .08f, dx0 + s * .018f, my + s * .08f + len);
                fill.setColor(GameKit.alpha(0xFFC4122F, a0)); c.drawRoundRect(box, s * .018f, s * .018f, fill);
                c.drawCircle(dx0, my + s * .08f + len, s * .028f, fill);
            }
        }
        if (now < shieldUntil) {
            float k = (now % 1000) / 1000f, r = s * (.66f + .03f * (float) Math.sin(k * Math.PI * 2));
            boolean ending = shieldUntil - now < 1500 && (now / 120) % 2 == 0;
            stroke.setStrokeWidth(2.2f * d); stroke.setColor(GameKit.alpha(GameKit.LIGHT, fade * (ending ? .3f : .8f))); c.drawCircle(cx, cy - bob, r, stroke);
            fill.setColor(GameKit.alpha(GameKit.LIGHT, fade * .12f)); c.drawCircle(cx, cy - bob, r, fill);
            fill.setColor(GameKit.alpha(0x88FFFFFF, fade)); c.drawCircle(cx - r * .45f, cy - bob - r * .45f, r * .12f, fill);
        }
    }

    void drawGlitch(Canvas c, Glitch g, long now, float fade) {
        float cx = px(g.x), cy = py(g.y), s = cell * (.72f + .03f * g.tier) * (g.boss ? 1.7f + Math.min(.25f, (g.bossKind % 20) * .012f) : g.mini ? .6f : g.type == 7 ? 1.18f : g.type == 14 ? 1.5f : 1f) * (g.boss || g.mini ? 1f : 1 + Math.min(.22f, (stage - 1) * .0025f));
        if (!g.boss && g.type == 13 && g.hidden && g.alive) { drawMimicBox(c, g, cx, cy, now, fade); return; }
        if (!g.boss && g.type == 4) fade *= motion ? .5f + .14f * (float) Math.sin(now / 300.0 + g.phase) : .58f;
        if (!g.boss && g.type == 6 && now - g.blinkAt < 400) fade *= (now - g.blinkAt) / 400f;
        if (g.boss) cy -= cell * .25f;
        if (g.boss && BOSS_BODY[g.bossKind % 20] == 1) { drawGolem(c, g, cx, cy, s, now, fade); return; }
        if (g.boss && BOSS_BODY[g.bossKind % 20] == 2) { drawPhantom(c, g, cx, cy, s * .82f, now, fade); return; }
        if (g.boss && BOSS_BODY[g.bossKind % 20] == 3) { drawWraith(c, g, cx, cy, s * .9f, now, fade); return; }
        if (g.boss && BOSS_BODY[g.bossKind % 20] == 4) { drawSlime(c, g, cx, cy, s * .9f, now, fade); return; }
        if (g.boss && BOSS_BODY[g.bossKind % 20] == 5) { drawEye(c, g, cx, cy, s * .9f, now, fade); return; }
        if (g.boss && BOSS_BODY[g.bossKind % 20] == 6) { drawMech(c, g, cx, cy, s * .9f, now, fade); return; }
        if (!g.alive && g.lit) { drawLightBlob(c, g, cx, cy, s, now, fade); return; }
        if (!g.alive && g.ashed) { drawAsh(c, g, cx, cy, s, now, fade); return; }
        if (!g.boss && !g.mini && g.type == 9) { drawDemon(c, g, cx, cy, s, now, fade); return; }
        int col = glitchColour(g);
        if (g.alive && now < g.stunUntil) col = GameKit.mix(col, 0xFFFFF6D0, .45f);
        if (g.tier > 0) col = GameKit.mix(col, g.tier >= 3 ? 0xFF6A1B9A : 0xFF14161C, .05f * g.tier);
        if (now < freezeUntil) col = GameKit.mix(col, 0xFFBFE3FF, .6f);
        if (g.loveUntil > 0 && g.alive) col = GameKit.mix(col, 0xFFFF8FB3, .55f);
        if (now - g.hitAt < 140) col = 0xFFFFFFFF;
        if (g.tier >= 3 && g.alive) { float q = (float) (.5 + .5 * Math.sin(now / 240.0 + g.phase)); fill.setShader(null); fill.setColor(GameKit.alpha(0xFFB15CFF, fade * (.14f + .12f * q))); c.drawCircle(cx, cy, s * (.62f + .05f * q), fill); }
        float k = 1f;
        if (!g.alive && g.gibbed) return;
        if (!g.alive && g.melted) {
            float m = (now - g.deadAt) / 1100f; if (m >= 1) return;
            float sag = GameKit.ease(m / .55f), gone = 1 - GameKit.clamp((m - .55f) / .45f);
            fill.setShader(null);
            fill.setColor(GameKit.alpha(GameKit.mix(col, 0xFF2A1408, .5f), fade * gone * .9f)); c.drawOval(GameKit.rf(cx - s * (.4f + .35f * sag), cy + s * .22f, cx + s * (.4f + .35f * sag), cy + s * (.5f + .06f * sag)), fill);
            if (sag < 1) { box.set(cx - s * .42f * (1 + .25f * sag), cy - s * .42f + s * .7f * sag, cx + s * .42f * (1 + .25f * sag), cy + s * .4f); fill.setColor(GameKit.alpha(GameKit.mix(col, 0xFFFF7A3D, .35f), fade)); c.drawRoundRect(box, s * .3f, s * .3f, fill); }
            for (int i = 0; i < 3; i++) { float bub = ((now / 260f) + i * .37f) % 1f; fill.setColor(GameKit.alpha(0x88FFFFFF, fade * gone * (1 - bub))); c.drawCircle(cx + (i - 1) * s * .28f, cy + s * .36f - bub * s * .2f, s * .05f, fill); }
            if (motion && rnd.nextFloat() < .3f) { GameKit.Bit st = spark(cx + (rnd.nextFloat() - .5f) * s * .6f, cy + s * .2f, 0x88FFFFFF, 30, 4f); st.round = true; st.gravity = -.4f; st.decay = 1.6f; }
            return;
        }
        if (!g.alive) { k = 1 - GameKit.ease((now - g.deadAt) / 450f); if (k <= 0) return; }
        float wob = motion ? (float) Math.sin(g.phase) : 0;
        float sx = (1 + .06f * wob) * (g.alive ? 1 : 1 + (1 - k) * .6f), sy = (1 - .06f * wob) * k;
        int save = c.save(); c.scale(sx, sy, cx, cy + s * .4f);
        if (dizzy() && g.alive) c.rotate((float) Math.sin(g.spin) * 14, cx, cy + s * .4f);
        if (g.alive && g.shockUntil > 0 && motion) { float j = s * .05f; c.translate((rnd.nextFloat() - .5f) * j, (rnd.nextFloat() - .5f) * j); }
        if (!g.boss && !g.mini && g.type == 8 && g.alive) possessedWings(c, cx, cy, s, now, fade, g.phase);

        android.graphics.Path p = GameKit.path();
        float l = cx - s * .45f, r = cx + s * .45f, t = cy - s * .45f, b = cy + s * .38f;
        p.moveTo(l, b); p.lineTo(l, t + s * .35f);
        p.cubicTo(l, t - s * .05f, r, t - s * .05f, r, t + s * .35f); p.lineTo(r, b);
        for (int i = 0; i < 4; i++) { float x0 = r - (r - l) * (i + .5f) / 4f, x1 = r - (r - l) * (i + 1) / 4f; float dip = s * .1f * (i % 2 == 0 ? 1 : -.2f) * (1 + .3f * wob); p.quadTo(x0, b + dip, x1, b); }
        p.close();
        fill.setShader(null); fill.setColor(GameKit.alpha(col, fade)); c.drawPath(p, fill);
        if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x88FFFFFF, fade)); c.drawPath(p, stroke); }
        fill.setColor(GameKit.alpha(0x40FFFFFF, fade)); c.drawCircle(cx - s * .2f, cy - s * .24f, s * .09f, fill);
        if (g.tier >= 1) {
            box.set(l + s * .04f, t - s * .02f, r - s * .04f, t + s * .22f);
            fill.setColor(GameKit.alpha(g.tier >= 3 ? 0xFFFFD23F : 0xFFB8C2D6, fade)); c.drawRoundRect(box, s * .18f, s * .18f, fill);
            fill.setColor(GameKit.alpha(0x55FFFFFF, fade)); c.drawCircle(cx - s * .12f, t + s * .06f, s * .05f, fill);
        }
        if (g.tier >= 2) {
            fill.setColor(GameKit.alpha(g.tier >= 3 ? 0xFFFFD23F : 0xFFE8EEF8, fade));
            for (int i = -1; i <= 1; i++) { android.graphics.Path sp2 = GameKit.path(); float bx = cx + i * s * .18f; sp2.moveTo(bx - s * .07f, t + s * .02f); sp2.lineTo(bx, t - s * .16f - (i == 0 ? s * .06f : 0)); sp2.lineTo(bx + s * .07f, t + s * .02f); sp2.close(); c.drawPath(sp2, fill); }
        }
        if (g.split && !g.mini) {
            float beat = motion ? (float) (.5 + .5 * Math.sin(now / 200.0 + g.phase)) : .5f;
            halfClip.rewind(); box.set(l, t - s * .02f, r, b); halfClip.addRoundRect(box, s * .4f, s * .4f, android.graphics.Path.Direction.CW);
            int save2 = c.save(); c.clipPath(halfClip); fill.setColor(GameKit.alpha(GameKit.mix(col, 0xFFFFFFFF, .28f), fade)); c.drawRect(cx, t - s * .1f, r + s * .1f, b, fill); c.restoreToCount(save2);
            fill.setColor(GameKit.alpha(col, fade));
            for (int side = -1; side <= 1; side += 2) c.drawCircle(cx + side * s * .47f, cy + s * .06f, s * (.09f + .05f * beat), fill);
            android.graphics.Path seam = GameKit.path(); seam.moveTo(cx, t + s * .02f);
            for (int i = 1; i <= 6; i++) seam.lineTo(cx + (i % 2 == 0 ? -1 : 1) * s * .06f, t + s * .02f + (b - t) * i / 6.5f);
            stroke.setStrokeWidth(s * .09f); stroke.setColor(GameKit.alpha(0xFFFFF1A8, fade * (.3f + .4f * beat))); c.drawPath(seam, stroke);
            stroke.setStrokeWidth(s * .035f); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawPath(seam, stroke);
        }
        if (!g.boss && !g.mini && g.alive) {
            if (g.type == 5) {
                android.graphics.Path fin = GameKit.path(); fin.moveTo(cx - s * .16f, t + s * .06f); fin.lineTo(cx - DX[g.dir] * s * .22f, t - s * .3f); fin.lineTo(cx + s * .16f, t + s * .06f); fin.close();
                fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawPath(fin, fill);
                if (g.rush) { stroke.setStrokeWidth(Math.max(1.5f, s * .05f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * .7f)); for (int i = -1; i <= 1; i++) { float ox = -DX[g.dir] * s * .55f + DY[g.dir] * i * s * .2f, oy = -DY[g.dir] * s * .55f + DX[g.dir] * i * s * .2f; c.drawLine(cx + ox, cy + oy, cx + ox - DX[g.dir] * s * .28f, cy + oy - DY[g.dir] * s * .28f, stroke); } }
            } else if (g.type == 6) {
                float tw = (float) (.5 + .5 * Math.sin(now / (g.teleCool < 1 ? 50.0 : 220.0)));
                stroke.setStrokeWidth(Math.max(1.5f, s * .04f)); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawLine(cx, t + s * .02f, cx + s * .08f, t - s * .2f, stroke);
                fill.setColor(GameKit.alpha(0xFFFFF1A8, fade * (.5f + .5f * tw))); c.drawCircle(cx + s * .08f, t - s * .24f, s * (.07f + .03f * tw), fill);
            } else if (g.type == 7) {
                box.set(l + s * .02f, t + s * .06f, r - s * .02f, t + s * .24f); fill.setColor(GameKit.alpha(0xFF263238, fade)); c.drawRoundRect(box, s * .06f, s * .06f, fill);
                fill.setColor(GameKit.alpha(0xFFCFD8DC, fade)); c.drawCircle(l + s * .12f, t + s * .15f, s * .035f, fill); c.drawCircle(r - s * .12f, t + s * .15f, s * .035f, fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); for (int side = -1; side <= 1; side += 2) { android.graphics.Path tk = GameKit.path(); float tx0 = cx + side * s * .14f; tk.moveTo(tx0 - s * .05f, cy + s * .26f); tk.lineTo(tx0, cy + s * .12f); tk.lineTo(tx0 + s * .05f, cy + s * .26f); tk.close(); c.drawPath(tk, fill); }
            } else if (g.type == 8) {
                float hq = motion ? (float) Math.sin(now / 400.0 + g.phase) : 0, hy0 = t - s * .14f;
                int sv = c.save(); c.rotate(-12 + hq * 6, cx, hy0);
                stroke.setStrokeWidth(Math.max(1.5f, s * .06f)); stroke.setColor(GameKit.alpha(((now / 90) + (long) g.phase) % 17 == 0 ? 0xFFE5173F : 0xFFFFC531, fade));
                c.drawArc(GameKit.rf(cx - s * .26f, hy0 - s * .07f, cx + s * .26f, hy0 + s * .07f), 0, 150, false, stroke); c.drawArc(GameKit.rf(cx - s * .26f, hy0 - s * .07f, cx + s * .26f, hy0 + s * .07f), 175, 160, false, stroke);
                c.restoreToCount(sv);
                stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(0xAA4A1A5A, fade));
                android.graphics.Path v = GameKit.path(); v.moveTo(l + s * .1f, b - s * .05f); v.lineTo(l + s * .2f, cy + s * .1f); v.lineTo(l + s * .14f, cy - s * .05f); v.moveTo(r - s * .08f, b - s * .1f); v.lineTo(r - s * .2f, cy + s * .14f); v.lineTo(r - s * .12f, cy); c.drawPath(v, stroke);
                fill.setColor(GameKit.alpha(0xFF6A0A1E, fade));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path hn = GameKit.path(); float hx0 = cx + side * s * .24f; hn.moveTo(hx0 - s * .05f, t + s * .08f); hn.lineTo(hx0 + side * s * .05f, t - s * .07f); hn.lineTo(hx0 + s * .05f, t + s * .08f); hn.close(); c.drawPath(hn, fill); }
            } else if (g.type == 4) {
                stroke.setStrokeWidth(Math.max(1.5f, s * .04f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, Math.min(1f, fade * 1.6f)));
                for (int i = 0; i < 10; i++) { double ang = i * Math.PI / 5 + now / 900.0; c.drawPoint(cx + (float) Math.cos(ang) * s * .56f, cy + (float) Math.sin(ang) * s * .56f, stroke); }
            }
        }
        if (!g.boss && g.alive && g.type != 8) drawLook(c, g, l, r, t, b, s, cx, cy, col, now, fade, false);
        float lx = DX[g.dir] * s * .07f, ly = DY[g.dir] * s * .05f;
        boolean burning = g.burnUntil > 0 && g.alive, woozy = dizzy() && g.alive, scorched = g.alive && now < g.scorchUntil;
        if (burning || scorched) {
            for (int i = -1; i <= 1; i++) {
                float fh = s * (.22f + .1f * (float) Math.sin(now / 60.0 + i * 2 + g.phase)), fx = cx + i * s * .16f;
                android.graphics.Path fl = GameKit.path(); fl.moveTo(fx - s * .1f, t + s * .08f); fl.quadTo(fx, t - fh * 1.4f, fx + s * .1f, t + s * .08f); fl.close();
                fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawPath(fl, fill);
                fl.reset(); fl.moveTo(fx - s * .05f, t + s * .08f); fl.quadTo(fx, t - fh * .7f, fx + s * .05f, t + s * .08f); fl.close();
                fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawPath(fl, fill);
            }
        }
        if (woozy) {
            for (int side = -1; side <= 1; side += 2) {
                float x = cx + side * s * .15f, y = cy - s * .05f;
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, y, s * .11f, fill);
                stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(0xFF14161C, fade));
                android.graphics.Path sp3 = GameKit.path();
                for (int i = 0; i <= 20; i++) { double ang = g.spin * side + i * .55; float rr = s * .01f + s * .0045f * i; float qx = x + (float) Math.cos(ang) * rr, qy = y + (float) Math.sin(ang) * rr; if (i == 0) sp3.moveTo(qx, qy); else sp3.lineTo(qx, qy); }
                c.drawPath(sp3, stroke);
            }
            float ty0 = cy + s * .12f, wag = (float) Math.sin(now / 150.0) * s * .04f;
            fill.setColor(GameKit.alpha(0xFFFF6B8B, fade)); c.drawOval(GameKit.rf(cx - s * .07f + wag, ty0, cx + s * .07f + wag, ty0 + s * .2f), fill);
            c.restoreToCount(save);
            if (g.alive && !g.boss) healthBar(c, cx, cy - s * .7f, s * .8f, g.hp, g.maxHp, fade);
            return;
        }
        if (burning || g.boss) {
            for (int side = -1; side <= 1; side += 2) {
                float x = cx + side * s * .15f + lx, y = cy - s * .05f + ly;
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, y, s * (burning ? .13f : .1f), fill);
                fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawCircle(x + lx * .6f, y + ly * .6f, s * (burning ? .035f : .05f), fill);
                if (g.type == 2 || g.tier >= 2) { stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawLine(x - s * .1f * side, y - s * .17f, x + s * .06f * side, y - s * .12f, stroke); }
            }
        } else if (g.type == 8 && !g.mini) possessedFace(c, g, cx, cy, s, lx, ly, now, fade);
        else drawFace(c, g, cx, cy, s, lx, ly, now, fade);
        if (burning) {
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - s * .08f, cy + s * .1f, cx + s * .08f, cy + s * .24f), fill);
        }
        if (!g.boss && g.alive && !burning && g.type != 8) drawLook(c, g, l, r, t, b, s, cx, cy, col, now, fade, true);
        if (!g.boss && !g.mini && g.alive && g.tier >= 3) {
            fill.setColor(GameKit.alpha(g.tier >= 5 ? 0xFFFF2D55 : 0xFFE8EEF8, fade));
            for (int side = -1; side <= 1; side += 2) { android.graphics.Path hn = GameKit.path(); float hx0 = cx + side * s * .26f; hn.moveTo(hx0 - s * .08f, t + s * .08f); hn.lineTo(hx0 + side * s * .1f, t - s * .16f); hn.lineTo(hx0 + s * .08f, t + s * .08f); hn.close(); c.drawPath(hn, fill); }
            if (g.tier >= 4) { float q = (float) (.5 + .5 * Math.sin(now / 200.0 + g.phase)); stroke.setStrokeWidth(Math.max(1.5f, s * .05f)); stroke.setColor(GameKit.alpha(0xFFFF2D55, fade * (.3f + .4f * q))); c.drawCircle(cx, cy, s * (.62f + .04f * q), stroke); }
        }
        if (g.boss && g.alive) {
            android.graphics.Path cr = GameKit.path(); float cw = s * .5f, cb = t + s * .02f, ch = s * .28f;
            cr.moveTo(cx - cw / 2, cb); cr.lineTo(cx - cw / 2, cb - ch * .7f); cr.lineTo(cx - cw / 4, cb - ch * .35f); cr.lineTo(cx, cb - ch); cr.lineTo(cx + cw / 4, cb - ch * .35f); cr.lineTo(cx + cw / 2, cb - ch * .7f); cr.lineTo(cx + cw / 2, cb); cr.close();
            fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawPath(cr, fill);
            fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawCircle(cx, cb - ch * .45f, s * .035f, fill);
        }
        if (g.alive && (now - g.zapAt < 260 || g.doomAt > 0 || g.shockUntil > 0)) xray(c, cx, cy, s, l, r, t, b, now, fade, g.doomAt > 0);
        c.restoreToCount(save);
        if (g.alive && g.shockUntil > 0) for (int q2 = 0; q2 < 2; q2++) { double ang = rnd.nextDouble() * Math.PI * 2; bolt(c, cx, cy, cx + (float) Math.cos(ang) * s * .8f, cy + (float) Math.sin(ang) * s * .8f, s * .18f, 4, now / 40 + q2 * 31 + (long) (g.phase * 100), 0xFF7FE9FF, Math.max(1.2f, s * .04f), fade); }
        if (g.alive && now < g.stunUntil) { fill.setShader(null); for (int q2 = 0; q2 < 3; q2++) { double ang = now / 160.0 + q2 * Math.PI * 2 / 3; float sx2 = cx + (float) Math.cos(ang) * s * .4f, sy2 = cy - s * .62f + (float) Math.sin(ang) * s * .1f; fill.setColor(GameKit.alpha(0xFFFFE9A8, fade)); c.drawCircle(sx2, sy2, s * .06f, fill); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(sx2, sy2, s * .03f, fill); } }
        if (g.alive && !g.boss && !g.mini && g.type >= 10) drawNewGhost(c, g, cx, cy, s, now, fade);
        if (g.alive && g.boss && g.bossKind % 20 >= 3) bossDeco(c, g, cx, cy, s, now, fade);
        if (g.alive && !g.boss) healthBar(c, cx, cy - s * .7f, s * .8f, g.hp, g.maxHp, fade);
        if (g.loveUntil > 0 && g.alive) {
            float beat = motion ? (float) (.5 + .5 * Math.sin(now / 130.0 + g.phase)) : .5f;
            heart(c, cx - s * .2f, cy - s * .66f - beat * s * .06f, s * (.11f + .03f * beat), GameKit.alpha(0xFFFF4D7D, fade));
            heart(c, cx + s * .22f, cy - s * .8f + beat * s * .05f, s * .08f, GameKit.alpha(0xFFFF8FB3, fade));
        }
    }

    static boolean hat(int look) { return look == 3 || look == 4 || look == 9 || look == 10 || look == 12 || look == 5; }
    int lookOf(Glitch g) {
        int k = g.look;
        boolean topTaken = g.tier >= 1 || g.type == 5 || g.type == 6 || g.type == 7;
        if (topTaken && hat(k)) k = new int[]{1, 2, 7, 8, 11}[k % 5];
        return k;
    }
    final android.graphics.Path lookPath = new android.graphics.Path();
    void drawLook(Canvas c, Glitch g, float l, float r, float t, float b, float s, float cx, float cy, int col, long now, float fade, boolean over) {
        int k = lookOf(g);
        fill.setShader(null);
        int dark = 0xFF14161C, cloth = GameKit.mix(col, 0xFF000000, .45f);
        if (over) {
            if (k == 6) {
                fill.setColor(GameKit.alpha(dark, fade));
                for (int side = -1; side <= 1; side += 2) c.drawRoundRect(GameKit.rf(cx + side * s * .15f - s * .13f, cy - s * .12f, cx + side * s * .15f + s * .13f, cy + s * .03f), s * .05f, s * .05f, fill);
                stroke.setStrokeWidth(Math.max(1f, s * .035f)); stroke.setColor(GameKit.alpha(dark, fade)); c.drawLine(cx - s * .04f, cy - s * .08f, cx + s * .04f, cy - s * .08f, stroke);
                fill.setColor(GameKit.alpha(0x66FFFFFF, fade)); c.drawRect(cx - s * .25f, cy - s * .1f, cx - s * .2f, cy - s * .06f, fill); c.drawRect(cx + s * .05f, cy - s * .1f, cx + s * .1f, cy - s * .06f, fill);
            } else if (k == 5) {
                stroke.setStrokeWidth(Math.max(1f, s * .03f)); stroke.setColor(GameKit.alpha(dark, fade)); c.drawLine(l + s * .02f, cy - s * .2f, r - s * .02f, cy + s * .02f, stroke);
                fill.setColor(GameKit.alpha(dark, fade)); c.drawOval(GameKit.rf(cx + s * .04f, cy - s * .14f, cx + s * .27f, cy + s * .05f), fill);
            } else if (k == 11) {
                fill.setColor(GameKit.alpha(0xFF3B2416, fade));
                for (int side = -1; side <= 1; side += 2) { lookPath.rewind(); lookPath.moveTo(cx, cy + s * .08f); lookPath.quadTo(cx + side * s * .14f, cy + s * .02f, cx + side * s * .24f, cy + s * .1f); lookPath.quadTo(cx + side * s * .14f, cy + s * .14f, cx, cy + s * .12f); lookPath.close(); c.drawPath(lookPath, fill); }
            }
            return;
        }
        switch (k) {
            case 1: {
                float by = cy + s * .26f; fill.setColor(GameKit.alpha(0xFFE53935, fade));
                lookPath.rewind(); lookPath.moveTo(cx, by); lookPath.lineTo(cx - s * .16f, by - s * .08f); lookPath.lineTo(cx - s * .16f, by + s * .08f); lookPath.close();
                lookPath.moveTo(cx, by); lookPath.lineTo(cx + s * .16f, by - s * .08f); lookPath.lineTo(cx + s * .16f, by + s * .08f); lookPath.close(); c.drawPath(lookPath, fill);
                fill.setColor(GameKit.alpha(0xFFB71C1C, fade)); c.drawCircle(cx, by, s * .04f, fill); break; }
            case 2: {
                float sy = cy + s * .2f, swing = motion ? (float) Math.sin(now / 160.0 + g.phase) * s * .05f : 0;
                fill.setColor(GameKit.alpha(0xFF2E7D32, fade)); c.drawRoundRect(GameKit.rf(l - s * .01f, sy, r + s * .01f, sy + s * .1f), s * .05f, s * .05f, fill);
                fill.setColor(GameKit.alpha(0xFFFFF3C4, fade)); for (int i = 0; i < 4; i++) { float x0 = l + (r - l) * (i + .3f) / 4f; c.drawRect(x0, sy, x0 + s * .05f, sy + s * .1f, fill); }
                fill.setColor(GameKit.alpha(0xFF2E7D32, fade)); c.drawRoundRect(GameKit.rf(r - s * .2f + swing, sy + s * .05f, r - s * .08f + swing, sy + s * .3f), s * .04f, s * .04f, fill); break; }
            case 3: {
                fill.setColor(GameKit.alpha(dark, fade)); c.drawRoundRect(GameKit.rf(cx - s * .3f, t + s * .02f, cx + s * .3f, t + s * .08f), s * .03f, s * .03f, fill);
                c.drawRect(cx - s * .19f, t - s * .28f, cx + s * .19f, t + s * .04f, fill);
                fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawRect(cx - s * .19f, t - s * .06f, cx + s * .19f, t + s * .01f, fill); break; }
            case 4: {
                int dirx = DX[g.dir] != 0 ? DX[g.dir] : 1;
                fill.setColor(GameKit.alpha(0xFF1E88E5, fade)); c.drawArc(GameKit.rf(cx - s * .32f, t - s * .14f, cx + s * .32f, t + s * .26f), 180, 180, true, fill);
                c.drawRoundRect(GameKit.rf(cx + dirx * s * .05f - (dirx < 0 ? s * .42f : 0), t + s * .02f, cx + dirx * s * .05f + (dirx > 0 ? s * .42f : 0), t + s * .1f), s * .04f, s * .04f, fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(cx, t - s * .12f, s * .035f, fill); break; }
            case 5: {
                fill.setColor(GameKit.alpha(0xFFD32F2F, fade)); c.drawArc(GameKit.rf(cx - s * .4f, t - s * .06f, cx + s * .4f, t + s * .36f), 180, 180, true, fill);
                lookPath.rewind(); lookPath.moveTo(l + s * .04f, t + s * .12f); lookPath.lineTo(l - s * .16f, t + s * .05f); lookPath.lineTo(l - s * .1f, t + s * .22f); lookPath.close(); c.drawPath(lookPath, fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * s * .16f, t + s * .05f, s * .025f, fill); break; }
            case 7: {
                stroke.setStrokeWidth(Math.max(1.5f, s * .06f)); stroke.setColor(GameKit.alpha(0xFF6D4C41, fade));
                c.drawLine(cx - s * .22f, t + s * .38f, cx - s * .18f, b, stroke); c.drawLine(cx + s * .22f, t + s * .38f, cx + s * .18f, b, stroke);
                fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawCircle(cx - s * .22f, t + s * .4f, s * .04f, fill); c.drawCircle(cx + s * .22f, t + s * .4f, s * .04f, fill); break; }
            case 8: {
                stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawArc(GameKit.rf(cx - s * .24f, cy - s * .04f, cx + s * .24f, cy + s * .3f), 20, 140, false, stroke);
                fill.setColor(GameKit.alpha(0xFF4FD8FF, fade)); lookPath.rewind(); lookPath.moveTo(cx, cy + s * .26f); lookPath.lineTo(cx + s * .06f, cy + s * .33f); lookPath.lineTo(cx, cy + s * .4f); lookPath.lineTo(cx - s * .06f, cy + s * .33f); lookPath.close(); c.drawPath(lookPath, fill); break; }
            case 9: {
                float fx = cx + s * .2f, fy = t + s * .02f; fill.setColor(GameKit.alpha(0xFFFF7FC8, fade));
                for (int i = 0; i < 5; i++) { double ang = i * Math.PI * 2 / 5 + now / 900.0; c.drawCircle(fx + (float) Math.cos(ang) * s * .07f, fy + (float) Math.sin(ang) * s * .07f, s * .055f, fill); }
                fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawCircle(fx, fy, s * .045f, fill); break; }
            case 10: {
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawRect(l + s * .02f, t + s * .12f, r - s * .02f, t + s * .21f, fill);
                fill.setColor(GameKit.alpha(0xFF1E88E5, fade)); c.drawRect(l + s * .02f, t + s * .155f, r - s * .02f, t + s * .175f, fill); break; }
            case 12: {
                fill.setColor(GameKit.alpha(0xFF8E24AA, fade)); c.drawArc(GameKit.rf(cx - s * .36f, t - s * .16f, cx + s * .36f, t + s * .3f), 180, 180, true, fill);
                fill.setColor(GameKit.alpha(0xFFE1BEE7, fade)); c.drawRect(cx - s * .36f, t + s * .02f, cx + s * .36f, t + s * .09f, fill); c.drawCircle(cx, t - s * .18f, s * .08f, fill); break; }
            default: break;
        }
    }

    void drawFace(Canvas c, Glitch g, float cx, float cy, float s, float lx, float ly, long now, float fade) {
        int f = g.face, dark = 0xFF14161C, mouth = 0xFF3A0D12;
        boolean brows = f != 1 && (g.type == 2 || g.tier >= 2);
        float ey = cy - s * .05f + ly;
        fill.setShader(null);
        stroke.setStrokeWidth(Math.max(1.2f, s * .035f)); stroke.setColor(GameKit.alpha(dark, fade));
        if (f == 4) {
            float x = cx + lx;
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, ey, s * .17f, fill);
            fill.setColor(GameKit.alpha(0xFF2BB673, fade)); c.drawCircle(x + lx * .8f, ey + ly * .8f, s * .08f, fill);
            fill.setColor(GameKit.alpha(dark, fade)); c.drawCircle(x + lx * .8f, ey + ly * .8f, s * .04f, fill);
            if (brows) c.drawLine(x - s * .17f, ey - s * .22f, x + s * .17f, ey - s * .18f, stroke);
            c.drawArc(GameKit.rf(cx - s * .1f, cy + s * .1f, cx + s * .1f, cy + s * .22f), 20, 140, false, stroke);
            return;
        }
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * s * .15f + lx;
            switch (f) {
                case 2:
                    fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, ey, s * .09f, fill);
                    fill.setColor(GameKit.alpha(dark, fade)); c.drawCircle(x + lx * .5f, ey + s * .03f, s * .04f, fill);
                    fill.setColor(GameKit.alpha(GameKit.mix(glitchColour(g), 0xFF000000, .25f), fade)); c.drawRect(x - s * .1f, ey - s * .1f, x + s * .1f, ey, fill);
                    break;
                case 3:
                    fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, ey, s * .12f, fill);
                    fill.setColor(GameKit.alpha(dark, fade)); c.drawCircle(x + lx * .5f, ey + ly * .5f, s * .075f, fill);
                    fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x + lx * .5f - s * .03f, ey + ly * .5f - s * .03f, s * .025f, fill);
                    break;
                case 7:
                    fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, ey - s * .02f, s * .13f, fill);
                    fill.setColor(GameKit.alpha(dark, fade)); c.drawCircle(x + lx * .4f, ey - s * .02f + ly * .4f, s * .028f, fill);
                    break;
                case 8:
                    fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, ey, s * (side < 0 ? .1f : .12f), fill);
                    fill.setColor(GameKit.alpha(dark, fade)); c.drawCircle(x - side * s * .045f, ey + s * .01f, s * .045f, fill);
                    break;
                default:
                    fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, ey, s * .1f, fill);
                    fill.setColor(GameKit.alpha(dark, fade)); c.drawCircle(x + lx * .6f, ey + ly * .6f, s * .05f, fill);
            }
            if (f == 1) c.drawLine(x - side * s * .13f, ey - s * .2f, x + side * s * .07f, ey - s * .1f, stroke);
            else if (brows) c.drawLine(x - s * .1f * side, ey - s * .17f, x + s * .06f * side, ey - s * .12f, stroke);
        }
        float my = cy + s * .14f;
        switch (f) {
            case 1: c.drawArc(GameKit.rf(cx - s * .1f, my, cx + s * .1f, my + s * .14f), 200, 140, false, stroke); break;
            case 2: fill.setColor(GameKit.alpha(mouth, fade)); c.drawCircle(cx + lx * .3f, my + s * .04f, s * .035f, fill); break;
            case 3: c.drawArc(GameKit.rf(cx - s * .08f, my - s * .04f, cx + s * .08f, my + s * .08f), 20, 140, false, stroke);
                fill.setColor(GameKit.alpha(0x66FF7FA8, fade)); c.drawCircle(cx - s * .28f, cy + s * .08f, s * .05f, fill); c.drawCircle(cx + s * .28f, cy + s * .08f, s * .05f, fill); break;
            case 5: {
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawRoundRect(GameKit.rf(cx - s * .16f, my - s * .02f, cx + s * .16f, my + s * .1f), s * .04f, s * .04f, fill);
                c.drawRoundRect(GameKit.rf(cx - s * .16f, my - s * .02f, cx + s * .16f, my + s * .1f), s * .04f, s * .04f, stroke);
                for (int i = -1; i <= 1; i++) c.drawLine(cx + i * s * .07f, my - s * .02f, cx + i * s * .07f, my + s * .1f, stroke); break; }
            case 6: {
                c.drawLine(cx - s * .1f, my + s * .02f, cx + s * .1f, my + s * .02f, stroke);
                float wag = motion ? (float) Math.sin(now / 120.0 + g.phase) * s * .03f : 0;
                fill.setColor(GameKit.alpha(0xFFFF6B8B, fade)); c.drawOval(GameKit.rf(cx - s * .06f + wag, my + s * .02f, cx + s * .06f + wag, my + s * .17f), fill); break; }
            case 7: fill.setColor(GameKit.alpha(mouth, fade)); c.drawOval(GameKit.rf(cx - s * .06f, my - s * .02f, cx + s * .06f, my + s * .12f), fill); break;
            case 8: { lookPath.rewind(); lookPath.moveTo(cx - s * .12f, my + s * .04f); lookPath.quadTo(cx - s * .06f, my - s * .02f, cx, my + s * .04f); lookPath.quadTo(cx + s * .06f, my + s * .1f, cx + s * .12f, my + s * .04f); c.drawPath(lookPath, stroke); break; }
            case 9: {
                c.drawArc(GameKit.rf(cx - s * .13f, my - s * .08f, cx + s * .13f, my + s * .08f), 15, 150, false, stroke);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade));
                for (int side = -1; side <= 1; side += 2) { lookPath.rewind(); float fx = cx + side * s * .07f; lookPath.moveTo(fx - s * .03f, my + s * .05f); lookPath.lineTo(fx + s * .03f, my + s * .05f); lookPath.lineTo(fx, my + s * .13f); lookPath.close(); c.drawPath(lookPath, fill); }
                break; }
            default: break;
        }
    }

    void xray(Canvas c, float cx, float cy, float s, float l, float r, float t, float b, long now, float fade, boolean doomed) {
        boolean on = !motion || (now / 45) % 2 == 0 || doomed;
        if (doomed && motion) { float j = s * .04f; c.translate((rnd.nextFloat() - .5f) * j, (rnd.nextFloat() - .5f) * j); }
        lookPath.rewind(); lookPath.moveTo(l, b); lookPath.lineTo(l, t + s * .35f); lookPath.cubicTo(l, t - s * .05f, r, t - s * .05f, r, t + s * .35f); lookPath.lineTo(r, b); lookPath.close();
        fill.setShader(null); fill.setColor(GameKit.alpha(on ? 0xE62FC8FF : 0xE6FFFFFF, fade)); c.drawPath(lookPath, fill);
        int bone = on ? 0xFFFFFFFF : 0xFF0B2A44, hole = on ? 0xFF0B2A44 : 0xFF2FC8FF;

        float sy = cy - s * .1f;
        fill.setColor(GameKit.alpha(bone, fade)); c.drawCircle(cx, sy, s * .2f, fill); c.drawRoundRect(GameKit.rf(cx - s * .12f, sy + s * .08f, cx + s * .12f, sy + s * .2f), s * .04f, s * .04f, fill);
        fill.setColor(GameKit.alpha(hole, fade)); c.drawCircle(cx - s * .08f, sy - s * .02f, s * .055f, fill); c.drawCircle(cx + s * .08f, sy - s * .02f, s * .055f, fill);
        lookPath.rewind(); lookPath.moveTo(cx, sy + s * .04f); lookPath.lineTo(cx - s * .025f, sy + s * .09f); lookPath.lineTo(cx + s * .025f, sy + s * .09f); lookPath.close(); c.drawPath(lookPath, fill);
        stroke.setStrokeWidth(Math.max(1f, s * .02f)); stroke.setColor(GameKit.alpha(hole, fade));
        for (int i = -1; i <= 1; i++) c.drawLine(cx + i * s * .05f, sy + s * .13f, cx + i * s * .05f, sy + s * .2f, stroke);

        stroke.setStrokeWidth(Math.max(1.5f, s * .045f)); stroke.setColor(GameKit.alpha(bone, fade));
        c.drawLine(cx, sy + s * .2f, cx, b - s * .02f, stroke);
        stroke.setStrokeWidth(Math.max(1.2f, s * .03f));
        for (int i = 0; i < 3; i++) { float ry = sy + s * (.26f + i * .07f), rw = s * (.2f - i * .03f); c.drawArc(GameKit.rf(cx - rw, ry - s * .05f, cx + rw, ry + s * .06f), 200, 140, false, stroke); }

        stroke.setStrokeWidth(Math.max(1.5f, s * .04f)); stroke.setColor(GameKit.alpha(on ? 0xFFFFFFFF : 0xFFBFF4FF, fade));
        for (int k = 0; k < 3; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2; float x0 = cx + (float) Math.cos(ang) * s * .3f, y0 = cy + (float) Math.sin(ang) * s * .3f;
            lookPath.rewind(); lookPath.moveTo(x0, y0);
            float x = x0, y = y0;
            for (int j = 0; j < 3; j++) { x += (float) Math.cos(ang) * s * .14f + (rnd.nextFloat() - .5f) * s * .16f; y += (float) Math.sin(ang) * s * .14f + (rnd.nextFloat() - .5f) * s * .16f; lookPath.lineTo(x, y); }
            c.drawPath(lookPath, stroke);
        }
    }

    void drawGolem(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        float k = 1f;
        if (!g.alive) { k = 1 - GameKit.ease((now - g.deadAt) / 500f); if (k <= 0) return; }
        boolean flash = now - g.hitAt < 140;
        float slam = (now - g.slamAt) / 450f, rear = g.slamCool < .5f ? 1 - g.slamCool / .5f : 0f;
        float lift = rear * s * .18f - (slam < 1 ? (1 - slam) * s * .05f : 0);
        int stone = flash ? 0xFFFFFFFF : g.bossKind % 20 != 1 ? BOSS_INK[g.bossKind % 20] : night ? 0xFF7A6A58 : 0xFF8A7560, stone2 = GameKit.mix(stone, 0xFF000000, .25f), moss = g.bossKind % 20 != 1 ? BOSS_GLOW[g.bossKind % 20] : 0xFF4FA35A;
        int save = c.save(); c.scale(1, k, cx, cy + s * .45f);
        fill.setShader(null); fill.setColor(GameKit.alpha(0x40000000, fade)); c.drawOval(GameKit.rf(cx - s * .55f, cy + s * .38f, cx + s * .55f, cy + s * .55f), fill);
        float top = cy - s * .5f - lift;

        block(c, cx - s * .45f, cy + s * .15f, cx - s * .15f, cy + s * .48f, stone2, fade);
        block(c, cx + s * .15f, cy + s * .15f, cx + s * .45f, cy + s * .48f, stone2, fade);
        float arm = rear * s * .25f;
        block(c, cx - s * .68f, top + s * .25f - arm, cx - s * .42f, top + s * .7f - arm, stone2, fade);
        block(c, cx + s * .42f, top + s * .25f - arm, cx + s * .68f, top + s * .7f - arm, stone2, fade);

        float q = s * .42f, g2 = s * .04f;
        for (int i = 0; i < 4; i++) { float l = cx - q - g2 / 2 + (i % 2) * (q + g2), t = top + (i / 2) * (q + g2); block(c, l, t, l + q, t + q, i == 0 || i == 3 ? stone : stone2, fade); }
        fill.setColor(GameKit.alpha(moss, fade)); box.set(cx - q, top - s * .02f, cx + q * .3f, top + s * .07f); c.drawRoundRect(box, s * .04f, s * .04f, fill);

        int eye = g.bossKind % 20 != 1 ? GameKit.mix(BOSS_GLOW[g.bossKind % 20], 0xFFFFFFFF, rear * .5f) : GameKit.mix(0xFFFF9A3D, 0xFFFFF1A8, rear);
        for (int side = -1; side <= 1; side += 2) { fill.setColor(GameKit.alpha(eye, fade)); box.set(cx + side * s * .18f - s * .08f, top + s * .28f, cx + side * s * .18f + s * .08f, top + s * .36f); c.drawRoundRect(box, s * .03f, s * .03f, fill); }
        if (rear > 0) { fill.setColor(GameKit.alpha(0xFFFF9A3D, fade * .2f * rear)); c.drawCircle(cx, top + s * .32f, s * .45f, fill); }

        int n = Math.round(4 * (1 - g.hp / (float) g.maxHp));
        stroke.setStrokeWidth(Math.max(1f, s * .03f)); stroke.setColor(GameKit.alpha(0xCC14161C, fade));
        float[][] cr = {{-.3f, .1f, -.1f, .35f}, {.25f, .15f, .05f, .4f}, {-.2f, .55f, .1f, .7f}, {.3f, .5f, .15f, .75f}};
        for (int i = 0; i < n; i++) c.drawLine(cx + s * cr[i][0], top + s * cr[i][1], cx + s * cr[i][2], top + s * cr[i][3], stroke);
        c.restoreToCount(save);
        if (slam < 1 && slam >= 0) {
            stroke.setStrokeWidth(3 * d * (1 - slam)); stroke.setColor(GameKit.alpha(stone, fade * (1 - slam)));
            c.drawOval(GameKit.rf(cx - cell * 2.5f * slam, cy + s * .4f - cell * .8f * slam, cx + cell * 2.5f * slam, cy + s * .4f + cell * .8f * slam), stroke);
        }
    }

    void drawPhantom(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        float k = 1f;
        if (!g.alive) { k = 1 - GameKit.ease((now - g.deadAt) / 500f); if (k <= 0) return; }
        boolean first = g == firstTwin();
        int body = g.bossKind % 20 != 2 ? (first ? BOSS_INK[g.bossKind % 20] : BOSS_GLOW[g.bossKind % 20]) : first ? 0xFF35D0E0 : 0xFFB15CFF;
        if (now - g.hitAt < 140) body = 0xFFFFFFFF;
        float bt = (now - g.blinkAt) / 550f, appear = bt < 1 ? GameKit.ease(bt) : 1f;
        if (bt < .5f) {
            float f = 1 - bt * 2, ox = px(g.fromX), oy = py(g.fromY) - cell * .25f;
            fill.setShader(null); fill.setColor(GameKit.alpha(body, fade * .4f * f)); c.drawCircle(ox, oy, s * .5f * (1 + bt), fill);
        }
        float a0 = fade * k * (.55f + .3f * (float) (.5 + .5 * Math.sin(now / 200.0 + g.phase))) * appear;
        float wob = motion ? (float) Math.sin(g.phase) : 0;
        int save = c.save(); c.scale(appear * (1 + .05f * wob), appear * (1 - .05f * wob), cx, cy);

        android.graphics.Path w = GameKit.path(); float tx = cx - DX[g.dir] * s * .7f, ty = cy - DY[g.dir] * s * .7f + s * .3f;
        w.moveTo(cx - s * .3f, cy + s * .2f); w.quadTo((cx + tx) / 2 + wob * s * .1f, (cy + ty) / 2 + s * .3f, tx, ty); w.quadTo((cx + tx) / 2, cy + s * .2f, cx + s * .3f, cy + s * .2f); w.close();
        fill.setShader(null); fill.setColor(GameKit.alpha(body, a0 * .6f)); c.drawPath(w, fill);

        fill.setColor(GameKit.alpha(body, a0)); c.drawOval(GameKit.rf(cx - s * .45f, cy - s * .5f, cx + s * .45f, cy + s * .4f), fill);
        fill.setColor(GameKit.alpha(0x33FFFFFF, a0)); c.drawOval(GameKit.rf(cx - s * .3f, cy - s * .45f, cx, cy - s * .2f), fill);

        fill.setColor(GameKit.alpha(0xFFF4F6FC, fade * k * appear)); c.drawOval(GameKit.rf(cx - s * .28f, cy - s * .3f, cx + s * .28f, cy + s * .2f), fill);
        for (int side = -1; side <= 1; side += 2) {
            android.graphics.Path e = GameKit.path(); float ex = cx + side * s * .12f, ey = cy - s * .07f;
            e.moveTo(ex - s * .08f, ey - side * s * .03f); e.lineTo(ex + s * .08f, ey + side * s * .03f); e.lineTo(ex + s * .06f * side, ey + s * .05f); e.close();
            fill.setColor(GameKit.alpha(0xFF14161C, fade * k * appear)); c.drawPath(e, fill);
        }
        fill.setColor(GameKit.alpha(body, fade * k * appear)); c.drawCircle(cx, cy + s * .1f, s * .03f, fill);
        c.restoreToCount(save);
    }
    Glitch firstTwin() { for (Glitch o : glitches) if (bossDoes(o, BK_TWIN)) return o; return null; }

    float prizeX(Prize p) { float e = GameKit.easeIn(p.pull); return p.x + .5f + (hx - p.x - .5f) * e; }
    float prizeY(Prize p) { float e = GameKit.easeIn(p.pull); return p.y + .5f + (hy - p.y - .5f) * e; }

    void drawPrize(Canvas c, Prize p, long now, float fade) {
        float age = now - p.at, in = GameKit.spring(age / 450f);
        float cx = px(prizeX(p)), cy = py(prizeY(p)) + (motion ? (float) Math.sin(now / 260.0) * cell * .05f : 0), s = cell * .7f * in * (1 - .3f * p.pull);
        int tier = tierOf(p.kind);
        prizeGlow(c, cx, cy, s, tier, now, fade);
        box.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
        fill.setShader(null); fill.setColor(GameKit.alpha(tier == 2 ? 0xFFFFF6D8 : tier == 1 ? 0xFFF4EAFF : 0xFFFFFFFF, fade)); c.drawRoundRect(box, s * .28f, s * .28f, fill);
        stroke.setStrokeWidth((tier > 0 ? 2.6f : 2f) * d); stroke.setColor(GameKit.alpha(tier > 0 ? TIER_COLOUR[tier] : GameKit.mix(GameKit.LIGHT, 0xFFFF7FC8, (float) (.5 + .5 * Math.sin(now / 200.0))), fade)); c.drawRoundRect(box, s * .28f, s * .28f, stroke);
        float r = s * .3f;
        prizeIcon(c, p.kind, cx, cy, r, fade);
    }

    void prizeIcon(Canvas c, int kind, float cx, float cy, float r, float fade) {
        fill.setShader(null);
        switch (kind) {
            case P_POISON: fill.setColor(GameKit.alpha(0xFF3FAE4A, fade)); c.drawCircle(cx, cy + r * .1f, r * .75f, fill); fill.setColor(GameKit.alpha(0xFFB6F59E, fade)); c.drawCircle(cx - r * .25f, cy - r * .1f, r * .2f, fill); c.drawCircle(cx + r * .2f, cy + r * .3f, r * .14f, fill); break;
            case P_BREATH: { fill.setColor(GameKit.alpha(GameKit.BLUE, fade)); box.set(cx - r * .8f, cy - r * .7f, cx + r * .2f, cy + r * .5f); c.drawRoundRect(box, r * .25f, r * .25f, fill); fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawCircle(cx + r * .45f, cy, r * .35f, fill); fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawCircle(cx + r * .8f, cy, r * .2f, fill); break; }
            case P_SHIELD: stroke.setStrokeWidth(r * .25f); stroke.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawCircle(cx, cy, r * .75f, stroke); fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(cx, cy, r * .35f, fill); break;
            case P_FREEZE: { stroke.setStrokeWidth(r * .2f); stroke.setColor(GameKit.alpha(0xFF4FB3F5, fade)); for (int i = 0; i < 3; i++) { double ang = i * Math.PI / 3; c.drawLine(cx - (float) Math.cos(ang) * r, cy - (float) Math.sin(ang) * r, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, stroke); } break; }
            case P_LASER: { for (int side = -1; side <= 1; side += 2) { fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(cx + side * r * .4f, cy - r * .1f, r * .32f, fill); fill.setColor(GameKit.alpha(0xFFFF2D55, fade)); c.drawCircle(cx + side * r * .4f, cy - r * .1f, r * .16f, fill); } stroke.setStrokeWidth(r * .12f); stroke.setColor(GameKit.alpha(0xFFFF2D55, fade)); c.drawLine(cx - r * .4f, cy + r * .1f, cx - r * .1f, cy + r * .9f, stroke); c.drawLine(cx + r * .4f, cy + r * .1f, cx + r * .7f, cy + r * .9f, stroke); break; }
            case P_KICK: { fill.setColor(GameKit.alpha(GameKit.BLUE, fade)); box.set(cx - r * .8f, cy, cx + r * .2f, cy + r * .5f); c.drawRoundRect(box, r * .2f, r * .2f, fill); box.set(cx - r * .8f, cy - r * .8f, cx - r * .4f, cy + r * .2f); c.drawRoundRect(box, r * .15f, r * .15f, fill); fill.setColor(GameKit.alpha(0xFF1B2440, fade)); c.drawCircle(cx + r * .6f, cy + r * .1f, r * .35f, fill); break; }
            case P_RAIN: { fill.setColor(GameKit.alpha(0xFF9EC1FF, fade)); c.drawCircle(cx - r * .3f, cy - r * .4f, r * .4f, fill); c.drawCircle(cx + r * .2f, cy - r * .5f, r * .45f, fill); fill.setColor(GameKit.alpha(0xFF1B2440, fade)); c.drawCircle(cx - r * .3f, cy + r * .5f, r * .2f, fill); c.drawCircle(cx + r * .35f, cy + r * .35f, r * .2f, fill); break; }
            case P_MEGA: { fill.setColor(GameKit.alpha(0xFFC62828, fade)); c.drawCircle(cx, cy + r * .1f, r * .8f, fill); fill.setColor(GameKit.alpha(0xFFFFE066, fade)); c.drawCircle(cx + r * .6f, cy - r * .7f, r * .22f, fill); fill.setColor(GameKit.alpha(0x66FFFFFF, fade)); c.drawCircle(cx - r * .3f, cy - r * .2f, r * .2f, fill); break; }
            case P_FART: { fill.setColor(GameKit.alpha(0xFFB9C77A, fade)); c.drawCircle(cx - r * .35f, cy + r * .15f, r * .45f, fill); c.drawCircle(cx + r * .3f, cy - r * .1f, r * .5f, fill); fill.setColor(GameKit.alpha(0xFF9DB35A, fade)); c.drawCircle(cx + r * .1f, cy + r * .35f, r * .35f, fill); break; }
            case P_DOUBLE: text.setTypeface(GameKit.BOLD); text.setTextSize(r * 1.5f); text.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawText("×2", cx, cy + r * .5f, text); break;
            case 1: fill.setColor(GameKit.alpha(0xFF1B2440, fade)); c.drawCircle(cx, cy + r * .1f, r * .75f, fill); fill.setColor(GameKit.alpha(0xFFFFC94A, fade)); c.drawCircle(cx + r * .55f, cy - r * .65f, r * .2f, fill); break;
            case 2: fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawCircle(cx, cy + r * .15f, r * .7f, fill); fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawCircle(cx, cy + r * .25f, r * .4f, fill); break;
            case 3: stroke.setStrokeWidth(r * .3f); stroke.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawLine(cx - r * .7f, cy - r * .3f, cx + r * .2f, cy - r * .3f, stroke); c.drawLine(cx - r * .4f, cy + r * .3f, cx + r * .6f, cy + r * .3f, stroke); break;
            default: {
                android.graphics.Path h = GameKit.path(); h.moveTo(cx, cy + r * .8f);
                h.cubicTo(cx - r * 1.3f, cy - r * .2f, cx - r * .5f, cy - r * 1.1f, cx, cy - r * .35f);
                h.cubicTo(cx + r * .5f, cy - r * 1.1f, cx + r * 1.3f, cy - r * .2f, cx, cy + r * .8f);
                fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawPath(h, fill);
            }
        }
    }

    void drawPortal(Canvas c, long now, float fade) {
        if (portalX < 0 || map[portalY][portalX] != FLOOR || hidden[portalY][portalX] != 9) return;
        if (portalOpen) {
            float bx = px(portalX + .5f), top = board.top, bot = py(portalY + .5f), w = cell * .32f * (1 + .1f * (float) Math.sin(now / 160.0));
            fill.setShader(new android.graphics.LinearGradient(0, top, 0, bot, 0x00FFFFFF, GameKit.alpha(night ? 0x88B9D2FF : 0x889EC1FF, fade), android.graphics.Shader.TileMode.CLAMP));
            box.set(bx - w, top, bx + w, bot); c.drawRoundRect(box, w, w, fill); fill.setShader(null);
            fill.setColor(GameKit.alpha(GameKit.LIGHT, fade * .25f)); c.drawCircle(bx, bot, cell * (.7f + .08f * (float) Math.sin(now / 200.0)), fill);
        }
        float cx = px(portalX + .5f), cy = py(portalY + .5f), r = cell * .4f * (1 + .9f * portalBoost);
        float in = GameKit.spring((now - portalAt) / 500f);
        if (!portalOpen) {
            boolean on = heroAlive && Math.abs(hx - (portalX + .5f)) < .45f && Math.abs(hy - (portalY + .5f)) < .45f;
            if (on && now - portalNagAt > 1400) { portalNagAt = now; PixelStackSound.play(PixelStackSound.TING, .3f, .7f); GameKit.tick(a, .4f); }
            float nag = now - portalNagAt < 420 && motion ? (float) Math.sin((now - portalNagAt) / 420f * Math.PI * 4) * cell * .06f * (1 - (now - portalNagAt) / 420f) : 0;
            cx += nag;
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(night ? 0x66FFFFFF : 0x55000000, fade));
            for (int i = 0; i < 6; i++) c.drawArc(GameKit.rf(cx - r * in, cy - r * in, cx + r * in, cy + r * in), i * 60 + (now / 30) % 360, 30, false, stroke);
            int left = glitchesLeft();
            if (left > 0 && in > .5f) {
                int ink = night ? 0xCCFFFFFF : 0xAA14161C;
                float gw = r * .34f, gx = cx - r * .3f, gy = cy;
                android.graphics.Path gp = GameKit.path();
                gp.moveTo(gx - gw, gy + gw); gp.lineTo(gx - gw, gy - gw * .2f); gp.quadTo(gx - gw, gy - gw * 1.2f, gx, gy - gw * 1.2f); gp.quadTo(gx + gw, gy - gw * 1.2f, gx + gw, gy - gw * .2f); gp.lineTo(gx + gw, gy + gw);
                gp.lineTo(gx + gw * .5f, gy + gw * .55f); gp.lineTo(gx, gy + gw); gp.lineTo(gx - gw * .5f, gy + gw * .55f); gp.close();
                fill.setShader(null); fill.setColor(GameKit.alpha(ink, fade)); c.drawPath(gp, fill);
                text.setTypeface(GameKit.BOLD); text.setTextSize(r * .78f); text.setTextAlign(android.graphics.Paint.Align.LEFT); text.setColor(GameKit.alpha(ink, fade));
                c.drawText(String.valueOf(left), cx + r * .12f, cy + r * .27f, text);
                text.setTextAlign(android.graphics.Paint.Align.CENTER);
            }
            return;
        }
        for (int i = 0; i < 4; i++) {
            float k = ((now / 900f) + i / 4f) % 1f, rr = r * (1.1f - k) * in;
            stroke.setStrokeWidth(3 * d * (1 - k * .5f)); stroke.setColor(GameKit.alpha(GameKit.RAINBOW[(i * 2) % GameKit.RAINBOW.length], fade * (1 - k * .3f)));
            c.drawCircle(cx, cy, rr, stroke);
        }
        fill.setShader(null); fill.setColor(GameKit.alpha(0xCCFFFFFF, fade)); c.drawCircle(cx, cy, r * .22f * in, fill);

        float ay = cy - cell * (.95f + .18f * Math.abs((float) Math.sin(now / 220.0))), aw = cell * .26f;
        android.graphics.Path ar = GameKit.path(); ar.moveTo(cx - aw, ay - aw * .6f); ar.lineTo(cx + aw, ay - aw * .6f); ar.lineTo(cx, ay + aw * .6f); ar.close();
        fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawPath(ar, fill);
        if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x88FFFFFF, fade)); c.drawPath(ar, stroke); }
    }

    void drawGas(Canvas c, long now, float fade) {
        for (Gas q : gas) {
            float t = (now - q.at) / 3600f; if (t < 0) continue;
            float k = t < .1f ? GameKit.ease(t / .1f) : 1 - GameKit.easeIn((t - .9f) / .1f);
            if (t > .9f) k = 1 - GameKit.ease((t - .9f) / .1f);
            float cx = px(q.x + .5f), cy = py(q.y + .5f);
            for (int i = 0; i < 4; i++) {
                double ph = now / 380.0 + i * 1.7 + q.x + q.y;
                float ox = (float) Math.cos(ph) * cell * .16f, oy = (float) Math.sin(ph * 1.3) * cell * .12f;
                fill.setShader(null); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFF5BCB5A : 0xFF9BE58A, fade * .45f * k)); c.drawCircle(cx + ox, cy + oy, cell * (.3f + .05f * i) * k, fill);
            }
        }
    }

    void drawHud(Canvas c, float fade) {
        if (land) { drawHudSide(c, fade); return; }
        float y = topInset + barH + 40 * d;
        drawScore(c, y, fade);
        text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(13));
        text.setColor(GameKit.alpha(ink, fade * .8f)); text.setTextAlign(android.graphics.Paint.Align.LEFT);
        c.drawText(I18n.t("Stage {0}").replace("{0}", String.valueOf(stage)), board.left + 4 * d, y - 14 * d, text);
        float s = 12 * d;
        for (int i = 0; i < Math.min(lives, 6); i++) {
            float x = board.left + 4 * d + i * (s + 4 * d);
            block(c, x, y - 6 * d, x + s, y - 6 * d + s, GameKit.BLUE, fade);
        }

        text.setTextAlign(android.graphics.Paint.Align.RIGHT); text.setTypeface(GameKit.BOLD); text.setTextSize(sp(14));
        text.setColor(GameKit.alpha(ink2(), fade));
        float rx = board.right - 4 * d;
        c.drawText(String.valueOf(range), rx, y, text);
        float fw = text.measureText(String.valueOf(range));
        fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawCircle(rx - fw - 9 * d, y - 5 * d, 5.5f * d, fill);
        fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawCircle(rx - fw - 9 * d, y - 4 * d, 3 * d, fill);
        float bx = rx - fw - 26 * d;
        c.drawText(String.valueOf(bombsMax), bx, y, text);
        float bw2 = text.measureText(String.valueOf(bombsMax));
        fill.setColor(GameKit.alpha(night ? 0xFFB8C2E0 : 0xFF1B2440, fade)); c.drawCircle(bx - bw2 - 9 * d, y - 5 * d, 5.5f * d, fill);
        drawMeters(c, board.left + 4 * d, topInset + barH + 55 * d, board.right - 4 * d, fade);
        text.setTextAlign(android.graphics.Paint.Align.CENTER);
    }

    void drawMeters(Canvas c, float l, float y, float r, float fade) {
        float mid = (l + r) / 2f, gap = 12 * d;
        rankMeter(c, l, y, mid - gap / 2, fade);
        soulMeter(c, mid + gap / 2, y, r, fade);
    }
    void rankMeter(Canvas c, float l, float y, float r, float fade) {
        int from = rank == 0 ? 0 : RANKS[rank - 1], to = rank < RANKS.length ? RANKS[rank] : from;
        float k = to > from ? (kills - from) / (float) (to - from) : 1f;
        long now = SystemClock.uptimeMillis();
        if (now - rankAt < 700) { float q = 1 - (now - rankAt) / 700f; fill.setShader(null); fill.setColor(GameKit.alpha(0xFF4F8DF5, fade * q * .4f)); c.drawCircle(l + 8 * d, y, (8 + 10 * (1 - q)) * d, fill); }
        badge(c, l + 8 * d, y, String.valueOf(rank + 1), rank >= RANKS.length ? 0xFFE0A800 : night ? 0xFF4F8DF5 : GameKit.BLUE, fade);
        meter(c, l + 20 * d, y, r, k, rank >= RANKS.length ? 0xFFFFC531 : night ? GameKit.LIGHT : 0xFF4F8DF5, fade);
    }
    void soulMeter(Canvas c, float l, float y, float r, float fade) {
        float ix = l + 8 * d; long now = SystemClock.uptimeMillis();
        fill.setShader(null);
        if (soul == NONE) {
            fill.setColor(GameKit.alpha(0xFFFFC531, fade * .8f)); c.drawArc(GameKit.rf(ix - 8 * d, y - 8 * d, ix + 8 * d, y + 8 * d), 90, 180, true, fill);
            fill.setColor(GameKit.alpha(0xFFE5173F, fade * .8f)); c.drawArc(GameKit.rf(ix - 8 * d, y - 8 * d, ix + 8 * d, y + 8 * d), 270, 180, true, fill);
            meter(c, l + 20 * d, y, r, 0, 0, fade);
            return;
        }
        if (now - soulAt < 700) { float q = 1 - (now - soulAt) / 700f; fill.setColor(GameKit.alpha(soulColour(), fade * q * .45f)); c.drawCircle(ix, y, (8 + 10 * (1 - q)) * d, fill); }
        if (soul == ANGEL) { stroke.setStrokeWidth(1.6f * d); stroke.setColor(GameKit.alpha(0xFFFFC531, fade)); c.drawOval(GameKit.rf(ix - 6 * d, y - 13 * d, ix + 6 * d, y - 9.5f * d), stroke); }
        else { fill.setColor(GameKit.alpha(0xFFE5173F, fade)); for (int side = -1; side <= 1; side += 2) { android.graphics.Path h = GameKit.path(); h.moveTo(ix + side * 3 * d, y - 6 * d); h.lineTo(ix + side * 7 * d, y - 12 * d); h.lineTo(ix + side * 6.5f * d, y - 5 * d); h.close(); c.drawPath(h, fill); } }
        badge(c, ix, y, String.valueOf(soulLv), soul == ANGEL ? 0xFFD49A00 : 0xFFC4122F, fade);
        float want = soulProgress();
        if (want < soulShown || !motion) soulShown = want; else soulShown += (want - soulShown) * .14f;
        float k = soulShown;
        if (now - soulDropAt < 900) k = GameKit.clamp(k + (1 - GameKit.ease((now - soulDropAt) / 900f)) * .5f);
        meter(c, l + 20 * d, y, r, k, soulColour(), fade);
        if (now - gainAt < 600 && soulLv < TOP) { float q = 1 - (now - gainAt) / 600f, ex = l + 20 * d + (r - l - 20 * d) * k; fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * q)); c.drawCircle(ex, y, (3 + 4 * q) * d, fill); fill.setColor(GameKit.alpha(soulColour(), fade * q * .5f)); c.drawCircle(ex, y, (6 + 6 * (1 - q)) * d, fill); }
        if (soulLv < TOP) notches(c, l + 20 * d, y, r, soulStep(soulLv), fade);
        if (soulLv >= TOP && motion) { float ph = (now % 1600) / 1600f, sx = l + 20 * d + (r - l - 20 * d) * ph; fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * (float) Math.sin(Math.PI * ph))); c.drawCircle(sx, y, 3 * d, fill); }
    }

    void drawHudSide(Canvas c, float fade) {
        float lx = sideL.centerX(), y = sideL.top + 34 * d;
        drawScore(c, lx, y, fade);
        text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(13)); text.setTextAlign(android.graphics.Paint.Align.CENTER);
        text.setColor(GameKit.alpha(ink, fade * .8f));
        c.drawText(I18n.t("Stage {0}").replace("{0}", String.valueOf(stage)), lx, y + 24 * d, text);
        float s = 12 * d; int n = Math.min(lives, 6);
        for (int i = 0; i < n; i++) { float x = lx - (n * (s + 4 * d) - 4 * d) / 2f + i * (s + 4 * d); block(c, x, y + 34 * d, x + s, y + 34 * d + s, GameKit.BLUE, fade); }
        { float mw = Math.min(sideL.width() - 16 * d, 150 * d); rankMeter(c, lx - mw / 2, y + 66 * d, lx + mw / 2, fade); soulMeter(c, lx - mw / 2, y + 90 * d, lx + mw / 2, fade); }
        float rx = sideR.centerX(), ry = sideR.top + 30 * d;
        text.setTypeface(GameKit.BOLD); text.setTextSize(sp(15)); text.setColor(GameKit.alpha(ink2(), fade));
        String bs = String.valueOf(bombsMax), rs = String.valueOf(range);
        float bw = text.measureText(bs), rw = text.measureText(rs), total = 16 * d + bw + 22 * d + 16 * d + rw, x0 = rx - total / 2;
        fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0xFFB8C2E0 : 0xFF1B2440, fade)); c.drawCircle(x0 + 6 * d, ry - 5 * d, 6 * d, fill);
        text.setTextAlign(android.graphics.Paint.Align.LEFT); c.drawText(bs, x0 + 16 * d, ry, text);
        float fx = x0 + 16 * d + bw + 22 * d;
        fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawCircle(fx + 6 * d, ry - 5 * d, 6 * d, fill);
        fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawCircle(fx + 6 * d, ry - 4 * d, 3.3f * d, fill);
        c.drawText(rs, fx + 16 * d, ry, text);
        text.setTextAlign(android.graphics.Paint.Align.CENTER);
    }

    @Override void drawPlay(Canvas c, float dt, long now) {
        int sway = c.save();
        if (dizzy() && motion) { float k = Math.min(1f, (dizzyUntil - now) / 800f) * Math.min(1f, (now - fartAt) / 500f); c.rotate((float) Math.sin(now / 420.0) * 1.6f * k, board.centerX(), board.centerY()); c.translate((float) Math.sin(now / 300.0) * 3 * d * k, 0); }
        drawStatic(c);
        drawGas(c, now, 1f);
        drawStains(c, now);
        drawPortal(c, now, 1f);
        drawShrines(c, now);
        drawBlazes(c, now);
        for (Prize p : prizes) drawPrize(c, p, now, 1f);
        drawCrumbling(c);
        for (Bomb b : bombs) if (!b.done) drawReach(c, b, now);
        for (Bomb b : bombs) if (!b.done) drawBomb(c, b, now, 1f);
        for (float[] r : rain) {
            float left = (r[2] - now) / 700f; if (left > 1.6f) continue;
            float k = GameKit.clamp(1 - left), x = px(r[0] + .5f), y = py(r[1] + .5f);
            fill.setShader(null); fill.setColor(0x44000000); c.drawOval(GameKit.rf(x - cell * .32f * k, y + cell * .1f, x + cell * .32f * k, y + cell * .3f), fill);
            if (left < 1f) {
                float fy = y - (1 - GameKit.easeIn(k)) * cell * 6;
                stroke.setStrokeWidth(cell * .1f); stroke.setColor(0x55FFC94A); c.drawLine(x, fy - cell * .9f, x, fy - cell * .2f, stroke);
                fill.setColor(night ? 0xFF3A4366 : 0xFF1B2440); c.drawCircle(x, fy, cell * .3f, fill);
                fill.setColor(0x55FFFFFF); c.drawCircle(x - cell * .1f, fy - cell * .1f, cell * .08f, fill);
            }
        }
        drawHazards(c, now);
        for (Flame f : flames) drawFlame(c, f, now, 1f);
        drawTrails(c, now);
        drawBalls(c, now);
        drawGems(c, now);
        for (Glitch g : glitches) drawGlitch(c, g, now, 1f);
        drawBossFx(c, now);
        drawLights(c, now);
        if (breathing()) drawBreath(c, now);
        drawSouls(c, now);
        drawHero(c, now, 1f, 1f);
        drawSoulChange(c, now);
        if (now - smiteAt < 420) {
            float k = 1 - (now - smiteAt) / 420f, sx = px(smiteX), sy = py(smiteY);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFF6D0, k * .8f)); c.drawRect(sx - cell * .22f * k, board.top, sx + cell * .22f * k, sy, fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, k)); c.drawRect(sx - cell * .07f * k, board.top, sx + cell * .07f * k, sy, fill);
            fill.setColor(GameKit.alpha(0xFFFFE9A8, k * .6f)); c.drawCircle(sx, sy, cell * (.5f + .5f * (1 - k)), fill);
        }
        if (now - ashAt < 260 && heroAlive) { float k = 1 - (now - ashAt) / 260f; stroke.setStrokeWidth(Math.max(1.5f, cell * .08f) * k); stroke.setColor(GameKit.alpha(0xFFFFE9A8, k)); c.drawLine(px(hx), py(hy - .1f), px(ashX), py(ashY), stroke); }
        drawFaces(c, now);
        drawStrikes(c, now);
        drawHits(c, now);
        if (black) { stroke.setStrokeWidth(1.5f * d); stroke.setColor(0x55FFFFFF); c.drawRoundRect(board, 14 * d, 14 * d, stroke); }
        drawFrost(c, board, freezeAt, freezeUntil, now, 14 * d);
        drawHaze(c, board, fartAt, dizzyUntil, now, fartKind == 1 ? 0xFFFFFFFF : fartKind == 2 ? 0xFFFF7A3D : 0xFF9DB35A, 14 * d);
        if (fartKind != 0 && now - fartAt < 700) {
            float k = (now - fartAt) / 700f, r = fartReach * cell * GameKit.ease(k);
            fill.setShader(null); fill.setColor(GameKit.alpha(fartKind == 1 ? 0xFFFFFFFF : 0xFFFF9A3D, (1 - k) * .3f)); c.drawCircle(px(hx), py(hy), r, fill);
            stroke.setStrokeWidth(cell * .45f * (1 - k)); stroke.setColor(GameKit.alpha(fartKind == 1 ? 0xFFFFD6E4 : 0xFFE5532B, (1 - k) * .85f)); c.drawCircle(px(hx), py(hy), r, stroke);
        }
        ambient(c, board, GameKit.world(stage), now, 1f);
        c.restoreToCount(sway);
        drawHud(c, 1f);
        long ct = now - comboAt;
        if (comboShown >= 2 && ct < 1100) {
            float k = GameKit.spring(ct / 400f), f = ct > 800 ? 1 - GameKit.ease((ct - 800) / 300f) : 1f;
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(26 + Math.min(comboShown, 8) * 2) * Math.max(.01f, k));
            text.setColor(GameKit.alpha(GameKit.RAINBOW[comboShown % GameKit.RAINBOW.length], f));
            c.drawText(I18n.t("Combo ×{0}!").replace("{0}", String.valueOf(comboShown)), board.centerX(), board.top + 46 * d, text);
        }
        int bossHp = 0, bossMax = 0; Glitch head = null;
        for (Glitch g : glitches) if (g.boss) { bossMax += g.maxHp; if (g.alive) { bossHp += Math.max(0, g.hp); if (head == null) head = g; } }
        for (Glitch g = head; g != null; g = null) {
            float bw = board.width() * .6f, bx = board.centerX() - bw / 2, by = board.top + 10 * d;
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(12)); text.setColor(0xFFFFFFFF);
            box.set(bx - 6 * d, by - 4 * d, bx + bw + 6 * d, by + 26 * d); fill.setShader(null); fill.setColor(0xAA14161C); c.drawRoundRect(box, 10 * d, 10 * d, fill);
            c.drawText(I18n.t(BOSS_NAMES[g.bossKind % 20]), board.centerX(), by + 9 * d, text);
            box.set(bx, by + 14 * d, bx + bw, by + 20 * d); fill.setColor(0x44FFFFFF); c.drawRoundRect(box, 3 * d, 3 * d, fill);
            box.set(bx, by + 14 * d, bx + bw * bossHp / (float) Math.max(1, bossMax), by + 20 * d); fill.setColor(g.bossKind % 20 == 1 ? 0xFFFF9A3D : g.bossKind % 20 == 2 ? 0xFF35D0E0 : g.bossKind % 20 == 0 ? 0xFFB15CFF : BOSS_GLOW[g.bossKind % 20] == 0xFFFFFFFF ? BOSS_INK[g.bossKind % 20] : BOSS_GLOW[g.bossKind % 20]); c.drawRoundRect(box, 3 * d, 3 * d, fill);
        }
        float bt = now - portalBannerAt;
        if (portalBannerAt > 0 && bt < 1600) {
            float k = GameKit.spring(bt / 450f), f = bt > 1300 ? 1 - GameKit.ease((bt - 1300) / 300f) : 1f;
            banner(c, I18n.t("Portal open!"), 0xF21846B8, k, f);
            if (!portalSound) { portalSound = true; PixelStackSound.play(PixelStackSound.PERFECT, .55f, 1f); burst(px(portalX + .5f), py(portalY + .5f), GameKit.LIGHT, 22, 240, 5); }
        }

        int n = (megaLeft > 0 ? 1 : 0) + (now < bigUntil ? 1 : 0) + (now < poisonUntil ? 1 : 0) + (now < breathUntil ? 1 : 0) + (now < shieldUntil ? 1 : 0) + (now < freezeUntil ? 1 : 0) + (now < doubleUntil ? 1 : 0) + (now < laserUntil ? 1 : 0) + (now < dizzyUntil ? 1 : 0);
        float cx = getWidth() / 2f, y = board.bottom + (tray.isEmpty() ? 24 : 70) * d; int i = 0;
        if (now < laserUntil) chipN(c, i++, n, cx, y, P_LASER, (laserUntil - now) / (float) LASER_MS);
        if (now < dizzyUntil) chipN(c, i++, n, cx, y, P_FART, (dizzyUntil - now) / 7000f);
        if (megaLeft > 0) chipN(c, i++, n, cx, y, P_MEGA, megaLeft / 3f);
        if (now < poisonUntil) chipN(c, i++, n, cx, y, P_POISON, (poisonUntil - now) / 15000f);
        if (now < bigUntil) chipN(c, i++, n, cx, y, P_FIRE, (bigUntil - now) / 20000f);
        if (now < breathUntil) chipN(c, i++, n, cx, y, P_BREATH, (breathUntil - now) / 10000f);
        if (now < shieldUntil) chipN(c, i++, n, cx, y, P_SHIELD, (shieldUntil - now) / 8000f);
        if (now < freezeUntil) chipN(c, i++, n, cx, y, P_FREEZE, (freezeUntil - now) / 6000f);
        if (now < doubleUntil) chipN(c, i, n, cx, y, P_DOUBLE, (doubleUntil - now) / 12000f);
        drawLevelUp(c, px(hx), py(hy) - cell * .55f, now);
        drawAsk(c, now);
    }
    final float[] chipXY = new float[2];
    void chipN(Canvas c, int i, int n, float cx, float y, int kind, float left) { chipAt(i, n, cx, y, chipXY); chip(c, chipXY[0], chipXY[1], kind, left); }
    long portalBannerAt; boolean portalSound; int comboShown;

    void chip(Canvas c, float x, float y, int kind, float left) {
        float r = 12 * d;
        fill.setShader(null); fill.setColor(night ? 0x26FFFFFF : 0x14000000); c.drawCircle(x, y, r, fill);
        stroke.setStrokeWidth(2 * d); stroke.setColor(night ? GameKit.LIGHT : GameKit.BLUE);
        c.drawArc(GameKit.rf(x - r, y - r, x + r, y + r), -90, 360 * GameKit.clamp(left), false, stroke);
        prizeIcon(c, kind, x, y, r * .55f, 1f);
    }

    @Override boolean drawBuild(Canvas c, float t, float dt) {
        long now = SystemClock.uptimeMillis();
        float fin = GameKit.ease(t / 300f);
        drawFloor(c, fin);
        float[] scale = scaleBuf;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float delay = 120 + (x + y) * 38, p = motion ? GameKit.clamp((t - delay) / 380f) : 1f;
            scale[y * W + x] = GameKit.spring(p);
        }
        drawField(c, 1f, scale);
        for (Glitch g : glitches) { float p = GameKit.clamp((t - 900 - (g.x + g.y) * 20) / 400f); if (p > 0) { int sv = c.save(); float k = GameKit.spring(p); c.scale(k, k, px(g.x), py(g.y)); drawGlitch(c, g, now, 1f); c.restoreToCount(sv); } }

        RectF start = startRect; start.set(px(1.1f), py(1.1f), px(1.9f), py(1.9f)); RectF[] to = startSq; GameKit.squaresOf(start, to);
        float fp = motion && fromTitle && cardAt == null ? GameKit.clamp((t - 100) / 800f) : 1f;
        if (fp < 1) for (int k = 0; k < 4; k++) {
            float p = GameKit.clamp((fp * 800 - k * 40) / 760f), m = GameKit.smooth(p);
            RectF s0 = bigAt[k], s1 = to[k];
            float cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m, cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m - (float) Math.sin(Math.PI * p) * 50 * d;
            float size = s0.width() + (s1.width() - s0.width()) * m;
            int sv = c.save(); c.rotate((k % 2 == 0 ? 1 : -1) * 360 * GameKit.ease(p), cx, cy);
            block(c, cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, squareColour(k), 1f);
            c.restoreToCount(sv);
        } else if (!heroAlive) {
            spawnHero();
            if (fromTitle) { PixelStackSound.play(PixelStackSound.THUD, .7f, 1f); GameKit.thump(a, .8f); burst(px(1.5f), py(1.5f), GameKit.LIGHT, 14, 200, 4); }
        }
        drawHero(c, now, 1f, 1f);
        if (black) { stroke.setStrokeWidth(1.5f * d); stroke.setColor(GameKit.alpha(0x55FFFFFF, fin)); c.drawRoundRect(board, 14 * d, 14 * d, stroke); }
        drawHud(c, GameKit.ease((t - 500) / 400f));
        float bk = t < 1000 ? 0 : t < 1700 ? GameKit.spring((t - 1000) / 450f) : 1 - GameKit.ease((t - 1700) / 250f);
        if (bk > 0) banner(c, I18n.t("Stage {0}").replace("{0}", String.valueOf(stage)), night ? 0xEEF28C28 : 0xF2F28C28, bk, Math.min(1f, bk));
        if (t > 1000 && !bannerSound) { bannerSound = true; PixelStackSound.play(PixelStackSound.PERFECT, .45f, .9f); }
        if (t >= 1950) { bannerSound = false; fromTitle = false; safeUntil = SystemClock.uptimeMillis() + 1800; return true; }
        return false;
    }
    boolean bannerSound;

    void banner(Canvas c, String words, int colour, float k, float f) {
        text.setTypeface(GameKit.BOLD); text.setTextSize(sp(32) * Math.max(.01f, k));
        float tw = text.measureText(words) + 44 * d;
        box.set(board.centerX() - tw / 2, board.centerY() - 34 * d * k, board.centerX() + tw / 2, board.centerY() + 22 * d * k);
        fill.setShader(null); fill.setColor(GameKit.alpha(colour, f)); c.drawRoundRect(box, 20 * d, 20 * d, fill);
        text.setColor(GameKit.alpha(0xFFFFFFFF, f)); c.drawText(words, board.centerX(), board.centerY() + 6 * d, text);
    }

    @Override boolean drawClear(Canvas c, float t, float dt) {
        long now = SystemClock.uptimeMillis();
        if (t < 1900) {
            float pcx = portalX + .5f, pcy = portalY + .5f;
            float pull = GameKit.easeIn(GameKit.clamp((t - 250) / 850f));
            mood = 1;
            float lean = t < 250 ? (float) Math.sin(Math.PI * t / 250f) * .12f : 0;
            hx = pullX + (pcx - pullX) * pull - DX[face] * lean; hy = pullY + (pcy - pullY) * pull;
            drawStatic(c); drawGas(c, now, 1f);
            portalBoost = t < 1150 ? GameKit.ease(t / 300f) : 1 - GameKit.ease((t - 1150) / 250f);
            drawPortal(c, now, 1f);
            for (Prize p : prizes) drawPrize(c, p, now, 1f);
            drawCrumbling(c);
            for (Flame f : flames) drawFlame(c, f, now, 1f);
            if (t < 1100) {
                float hpX = px(hx), hpY = py(hy);
                double ang = Math.atan2(py(pcy) - hpY, px(pcx) - hpX);
                float stretch = 1 + 1.1f * (float) Math.sin(Math.PI * Math.min(1f, pull * 1.2f)), thin = 1 / (float) Math.sqrt(stretch);
                int sv = c.save();
                c.rotate((float) Math.toDegrees(ang) - 90 + 900 * pull * pull, hpX, hpY);
                c.scale(thin, stretch, hpX, hpY);
                drawHero(c, now, 1f, 1 - pull);
                c.restoreToCount(sv);
                if (motion && t > 250) for (int i = 0; i < 3; i++) {
                    double a0 = rnd.nextDouble() * Math.PI * 2; float rr = cell * (1.2f + rnd.nextFloat());
                    GameKit.Bit b = new GameKit.Bit(); b.x = px(pcx) + (float) Math.cos(a0) * rr; b.y = py(pcy) + (float) Math.sin(a0) * rr;
                    b.vx = -(float) Math.cos(a0) * rr * 3.2f; b.vy = -(float) Math.sin(a0) * rr * 3.2f; b.size = cell * .12f; b.round = true; b.gravity = 0; b.decay = 3.2f;
                    b.colour = GameKit.RAINBOW[rnd.nextInt(GameKit.RAINBOW.length)]; bits.add(b);
                }
            } else if (!sucked) {
                sucked = true; heroAlive = false;
                burst(px(pcx), py(pcy), GameKit.LIGHT, 16, 200, 4);
                PixelStackSound.play(PixelStackSound.POP, .55f, .8f); GameKit.thump(a, .7f);
            }
            if (t > 250 && !whoosh) { whoosh = true; PixelStackSound.play(PixelStackSound.RISE, .35f, 1.6f); }
            drawHud(c, 1f);
        } else {
            float out = (t - 1900);
            float[] scale = scaleBuf;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) scale[y * W + x] = 1 - GameKit.easeIn(GameKit.clamp((out - (x + y) * 20) / 300f));
            drawFloor(c, 1 - GameKit.ease(out / 600f));
            drawField(c, 1f, scale);
            drawHud(c, 1f);
        }
        if (t > 500 && t < 1900) {
            float k = GameKit.spring((t - 500) / 500f), f = t > 1600 ? 1 - GameKit.ease((t - 1600) / 300f) : 1f;
            banner(c, I18n.t("Stage clear!"), 0xF22BB673, k, f);
            if (!confetti) {
                confetti = true; score += 1000; pop("+1000", board.centerX(), board.centerY() - 50 * d, night ? 0xFFFFFFFF : 0xFF14161C, 18);
                lives++; pop(I18n.t("Extra life"), board.centerX(), board.centerY() + 70 * d, night ? GameKit.LIGHT : GameKit.BLUE, 18);
                for (int i = 0; i < 40; i++) { GameKit.Bit b = spark(board.centerX(), board.centerY(), GameKit.RAINBOW[i % GameKit.RAINBOW.length], 420, 6); b.gravity = .8f; b.decay = .7f; }
                GameKit.thump(a, .7f);
            }
        }
        if (t >= 2550) { confetti = false; whoosh = false; mood = 0; portalBoost = 0; stage++; loadStage(); return true; }
        return false;
    }
    boolean confetti, sucked, whoosh; float pullX, pullY, portalBoost;

    @Override void shatter() {
        sources.clear();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int v = map[y][x]; if (v == FLOOR) continue;
            float cx = px(x + .5f), cy = py(y + .5f);
            boolean border = x == 0 || y == 0 || x == W - 1 || y == H - 1;
            int col = v == CRATE ? crate : border ? edge : pillar;
            sources.add(new float[]{cx, cy, col});
            if (rnd.nextFloat() < .3f) { GameKit.Bit b = spark(cx, cy, col, 90, cell / d * .5f); b.gravity = 1.2f; b.decay = .9f; }
        }
        java.util.Collections.shuffle(sources, rnd);
        bombs.clear(); flames.clear(); glitches.clear(); prizes.clear(); heroAlive = false;
    }

    @Override void drawActionIcon(Canvas c, float x, float y, float r, int colour) {
        fill.setShader(null); fill.setColor(colour); c.drawCircle(x - r * .05f, y + r * .08f, r * .3f, fill);
        stroke.setStrokeWidth(r * .08f); stroke.setColor(colour); c.drawLine(x + r * .14f, y - r * .14f, x + r * .3f, y - r * .34f, stroke);
        fill.setColor(0xFFFFE066); c.drawCircle(x + r * .33f, y - r * .38f, r * .08f, fill);
    }
}
