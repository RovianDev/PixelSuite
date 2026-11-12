package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;

import java.util.ArrayList;

final class PixelBlast extends GameKit.Game {
    static final int W = 13, H = 11;
    static final int FLOOR = 0, PILLAR = 1, CRATE = 2;

    final int[][] map = new int[H][W];
    final int[][] hidden = new int[H][W];
    final long[][] crumbleAt = new long[H][W];
    final RectF board = new RectF();
    float cell;
    int stage = 1, lives, bombsMax, range; float speed;
    boolean fromTitle, portalOpen; int portalX = -1, portalY = -1;

    static final class Bomb { int x, y, id, slide = -1, range; long at; float fuse, off; boolean done, poison, mega, kicked, struck, bounced; int ran; }

    static final class Ball { float x, y, r; long at; boolean mega; }
    final ArrayList<Ball> balls = new ArrayList<Ball>();
    float ballReach(Bomb b) { return 1.05f + Math.min(reachOf(b), 7) * .2f; }
    int big() { return SystemClock.uptimeMillis() < bigUntil ? bigLevel : 0; }
    int reachOf(Bomb b) { return (b.range > 0 ? b.range : b.mega ? range + 3 : range) + big() + (evilLevel() >= 2 ? 1 : 0); }
    boolean pierces(Bomb b) { return b.mega || rank >= 6 || evilLevel() >= 2; }

    int evil, holy, shrineX = -1, shrineY = -1; boolean shrineUsed, haloReady;
    long portalNagAt = -10000;
    long kickAt = -10000; int kickDir;
    long shrineAt, eatAt = -10000, cleanseAt = -10000, smiteAt = -10000; float smiteCool = 6f, smiteX, smiteY;
    static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    static final int[] EVIL_AT = {3, 7, 12, 18, 25};
    int evilLevel() { int l = 0; while (l < EVIL_AT.length && evil >= EVIL_AT[l]) l++; return l; }
    int holyLevel() { return Math.min(5, holy); }
    long frenzyUntil, novaAt = -10000, haloAt, bigUntil; int bigLevel; float novaCool = 6f, orb;

    void fall() {
        int ev = evilLevel();
        if (ev > 0) { evil = ev >= 2 ? EVIL_AT[ev - 2] : 0; pop(evilLevel() > 0 ? I18n.t("Evil") + " " + ROMAN[evilLevel()] : I18n.t("Cleansed"), px(hx), py(hy - 1.1f), 0xFFE5173F, 18); }
        else if (holy > 0) { holy--; if (holy > 0) pop(I18n.t("Angelic") + " " + ROMAN[holyLevel()], px(hx), py(hy - 1.1f), 0xFFFFD23F, 18); }
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
        if (g.boss) { hurt(g, 4); return; }
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
        int was = evilLevel();
        g.alive = false; g.deadAt = now; eatAt = now; evil++;
        if (evilLevel() >= 4) frenzyUntil = now + 4000;
        if (holy > 0) { holy = 0; burst(px(hx), py(hy - .5f), 0xFFFFE9A8, 16, 200, 4); }
        int pts = 150 * Math.max(1, Math.min(evil, 6)); addScore(pts); kills++;
        pop("+" + (now < doubleUntil ? pts * 2 : pts), px(g.x), py(g.y - .4f), 0xFFFF4D6D, 15);
        burst(px(g.x), py(g.y), glitchColour(g), 8, 140, 3);
        for (int i = 0; i < 16; i++) {
            GameKit.Bit b = spark(px(hx + DX[face] * .12f), py(hy + .16f), i % 3 == 0 ? 0xFF8E0D22 : 0xFFC4122F, 210, 3.6f);
            b.round = true; b.gravity = 1.4f; b.decay = 1.5f; b.vx += DX[face] * 60 * d; b.vy -= 40 * d;
        }
        PixelStackSound.play(PixelStackSound.PICK, .6f, .6f); PixelStackSound.play(PixelStackSound.POP, .4f, .7f); GameKit.thump(a, .6f);
        if (evilLevel() > was) {
            pop(I18n.t("Evil") + " " + ROMAN[evilLevel()], px(hx), py(hy - 1.1f), 0xFFE5173F, 22);
            burst(px(hx), py(hy), 0xFFE5173F, 26, 280, 5); shake(10); GameKit.rumble(a);
            PixelStackSound.play(PixelStackSound.BOOM, .45f, .5f); PixelStackSound.play(PixelStackSound.FART, .3f, .5f);
        }
        if (glitchesLeft() == 0) openPortal();
    }

    void placeShrine() {
        shrineX = -1; shrineUsed = false;
        for (int tries = 0; tries < 400; tries++) {
            int x = 1 + rnd.nextInt(W - 2), y = 1 + rnd.nextInt(H - 2);
            if (map[y][x] != FLOOR || (x == portalX && y == portalY)) continue;
            if (Math.hypot(x - portalX, y - portalY) < (tries < 200 ? 4 : 2) || Math.hypot(x + .5f - hx, y + .5f - hy) < (tries < 200 ? 3 : 1.5)) continue;
            shrineX = x; shrineY = y; shrineAt = SystemClock.uptimeMillis();
            PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.4f);
            return;
        }
    }

    void stepSouls(float dt, long now) {
        if (shrineX >= 0 && !shrineUsed && heroAlive && Math.abs(hx - shrineX - .5f) < .5f && Math.abs(hy - shrineY - .5f) < .5f) {
            shrineUsed = true; cleanseAt = now;
            if (evil == 0) {
                if (holy < 5) holy++;
                haloReady = true;
                pop(I18n.t("Angelic") + " " + ROMAN[holyLevel()], px(hx), py(hy - 1.1f), 0xFFFFD23F, 22);
            } else { evil = 0; pop(I18n.t("Cleansed"), px(hx), py(hy - 1.1f), night ? 0xFFFFFFFF : 0xFF14161C, 20); }
            burst(px(hx), py(hy), 0xFFFFFFFF, 26, 300, 5); burst(px(hx), py(hy), 0xFFFFE9A8, 20, 240, 4);
            PixelStackSound.play(PixelStackSound.SUCCESS, .6f, 1.3f); PixelStackSound.play(PixelStackSound.SPARKLE, .5f, 1.2f); GameKit.thump(a, .8f);
        }
        if (holyLevel() >= 5 && !haloReady && now - haloAt > 20000) { haloReady = true; burst(px(hx), py(hy - .6f), 0xFFFFD23F, 10, 160, 3); PixelStackSound.play(PixelStackSound.TING, .35f, 1.5f); }
        if (holyLevel() >= 4 && heroAlive) {
            orb += dt * 3.2f;
            float gx = hx + (float) Math.cos(orb) * 1.15f, gy = hy + (float) Math.sin(orb) * 1.15f;
            for (Glitch g : new ArrayList<Glitch>(glitches)) if (g.alive && now - g.hitAt > 550 && Math.hypot(g.x - gx, g.y - gy) < (g.boss ? .9f : .6f)) { hurt(g, 1); burst(px(g.x), py(g.y), 0xFFFFE9A8, 8, 160, 3); PixelStackSound.play(PixelStackSound.TING, .3f, 1.8f); }
        }
        if (evilLevel() >= 5 && heroAlive) {
            novaCool -= dt;
            if (novaCool <= 0) {
                boolean any = false; for (Glitch g : glitches) if (g.alive && Math.hypot(g.x - hx, g.y - hy) < 2.7f) any = true;
                if (!any) novaCool = .5f;
                else {
                    novaCool = 8f; novaAt = now;
                    for (Glitch g : new ArrayList<Glitch>(glitches)) if (g.alive && Math.hypot(g.x - hx, g.y - hy) < 2.7f) { if (g.mini) eat(g); else hurt(g, 2); }
                    shake(12); GameKit.rumble(a); PixelStackSound.play(PixelStackSound.BOOM, .55f, .45f);
                }
            }
        }
        if (holyLevel() >= 3 && heroAlive) {
            smiteCool -= dt;
            if (smiteCool <= 0) {
                Glitch best = null; double bd = 1e9;
                for (Glitch g : glitches) if (g.alive) { double dd = Math.hypot(g.x - hx, g.y - hy); if (dd < bd) { bd = dd; best = g; } }
                if (best == null) smiteCool = 1f;
                else {
                    smiteCool = holyLevel() >= 5 ? 3.5f : 7f; smiteAt = now; smiteX = best.x; smiteY = best.y;
                    hurt(best, best.boss ? 1 : 2);
                    burst(px(best.x), py(best.y), 0xFFFFF6D0, 16, 260, 4); shake(6);
                    PixelStackSound.play(PixelStackSound.TING, .5f, 1.6f); PixelStackSound.play(PixelStackSound.BOOM, .3f, 1.4f);
                }
            }
        }
    }

    void drawShrine(Canvas c, long now) {
        if (shrineX < 0) return;
        float cx = px(shrineX + .5f), cy = py(shrineY + .5f), in = GameKit.spring((now - shrineAt) / 600f);
        float gone = shrineUsed ? 1 - GameKit.ease((now - cleanseAt) / 900f) : 1f; if (gone <= 0) return;
        float a0 = Math.min(1f, in) * gone, pulse = motion ? (float) (.5 + .5 * Math.sin(now / 320.0)) : .5f;
        fill.setShader(null);
        fill.setShader(new android.graphics.LinearGradient(cx, cy - cell * 3.2f, cx, cy + cell * .4f, 0x00FFF6D0, GameKit.alpha(0xFFFFF6D0, a0 * (.34f + .16f * pulse)), android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(cx - cell * .42f, cy - cell * 3.2f, cx + cell * .42f, cy + cell * .4f, fill); fill.setShader(null);
        fill.setColor(GameKit.alpha(0xFFFFE9A8, a0 * (.22f + .12f * pulse))); c.drawCircle(cx, cy, cell * (.78f + .08f * pulse), fill);
        box.set(cx - cell * .4f, cy - cell * .4f, cx + cell * .4f, cy + cell * .4f);
        fill.setColor(GameKit.alpha(0xFFFFFDF2, a0)); c.drawRoundRect(box, cell * .14f, cell * .14f, fill);
        stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFFFD23F, a0)); c.drawRoundRect(box, cell * .14f, cell * .14f, stroke);
        stroke.setStrokeWidth(cell * .06f); stroke.setColor(GameKit.alpha(0xFFFFC531, a0)); c.drawOval(GameKit.rf(cx - cell * .17f, cy - cell * .3f, cx + cell * .17f, cy - cell * .18f), stroke);
        fill.setColor(GameKit.alpha(0xFFBFD8FF, a0));
        for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); w.moveTo(cx + side * cell * .03f, cy + cell * .02f); w.quadTo(cx + side * cell * .3f, cy - cell * .2f, cx + side * cell * .32f, cy + cell * .08f); w.quadTo(cx + side * cell * .2f, cy + cell * .22f, cx + side * cell * .03f, cy + cell * .14f); w.close(); c.drawPath(w, fill); }
        if (motion && !shrineUsed && rnd.nextFloat() < .3f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * cell * .7f, cy + cell * .2f, rnd.nextBoolean() ? 0xFFFFFFFF : 0xFFFFE9A8, 30, 3f); b.round = true; b.gravity = -.35f; b.decay = 1.3f; }
    }

    void drawSoul(Canvas c, float cx, float cy, float s, float top, float half, float bob, long now, float fade, boolean under) {
        int ev = evilLevel(), ho = holyLevel();
        fill.setShader(null);
        if (under) {
            if (ev >= 3) { float q = (float) (.5 + .5 * Math.sin(now / 160.0)); fill.setColor(GameKit.alpha(0xFF5A0A1E, fade * (.3f + .14f * q))); c.drawCircle(cx, cy - bob, s * (.78f + .06f * q), fill); }
            if (ho >= 3) { float q = (float) (.5 + .5 * Math.sin(now / 260.0)); fill.setColor(GameKit.alpha(0xFFFFE9A8, fade * (.26f + .12f * q))); c.drawCircle(cx, cy - bob, s * (.8f + .06f * q), fill); }
            if (ho >= 5) {
                int sv = c.save(); c.rotate(now / 40f % 360, cx, cy - bob); fill.setColor(GameKit.alpha(0xFFFFE9A8, fade * .5f));
                for (int i = 0; i < 10; i++) { android.graphics.Path ray = GameKit.path(); ray.moveTo(cx - s * .07f, cy - bob); ray.lineTo(cx, cy - bob - s * (i % 2 == 0 ? 1.25f : .95f)); ray.lineTo(cx + s * .07f, cy - bob); ray.close(); c.drawPath(ray, fill); c.rotate(36, cx, cy - bob); }
                c.restoreToCount(sv);
            }
            if (ev >= 4) {
                float flap = motion ? (float) Math.sin(now / 150.0) * s * .08f : 0;
                fill.setColor(GameKit.alpha(ev >= 5 ? 0xFF1A0508 : 0xFF4A0A18, fade));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); float wx = cx + side * half * .7f, wy = cy - s * .1f - bob;
                    w.moveTo(wx, wy); w.lineTo(wx + side * s * .75f, wy - s * .5f - flap); w.lineTo(wx + side * s * .62f, wy - s * .1f); w.lineTo(wx + side * s * .86f, wy + s * .1f + flap); w.lineTo(wx + side * s * .5f, wy + s * .12f); w.lineTo(wx + side * s * .56f, wy + s * .38f); w.lineTo(wx, wy + s * .22f); w.close(); c.drawPath(w, fill); }
            }
            if (ho >= 4) {
                float flap = motion ? (float) Math.sin(now / 280.0 + 1) * s * .07f : 0;
                fill.setColor(GameKit.alpha(0xFFFFF1B8, fade * .9f));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); w.moveTo(cx + side * half * .6f, cy - s * .2f - bob); w.quadTo(cx + side * s * 1.2f, cy - s * .95f - bob - flap, cx + side * s * 1.12f, cy - s * .1f - bob); w.quadTo(cx + side * s * .8f, cy + s * .2f - bob + flap, cx + side * half * .6f, cy + s * .05f - bob); w.close(); c.drawPath(w, fill); }
            }
            if (ho >= 2) {
                float flap = motion ? (float) Math.sin(now / 240.0) * s * .06f : 0;
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade));
                for (int side = -1; side <= 1; side += 2) { android.graphics.Path w = GameKit.path(); w.moveTo(cx + side * half * .7f, cy - s * .12f - bob); w.quadTo(cx + side * s * .9f, cy - s * .55f - bob - flap, cx + side * s * .86f, cy + s * .02f - bob); w.quadTo(cx + side * s * .66f, cy + s * .26f - bob + flap, cx + side * half * .7f, cy + s * .16f - bob); w.close(); c.drawPath(w, fill); }
                fill.setColor(GameKit.alpha(0xFFD9E6FF, fade));
                for (int side = -1; side <= 1; side += 2) c.drawCircle(cx + side * s * .7f, cy - s * .02f - bob, s * .06f, fill);
            }
            return;
        }
        if (ev >= 1) {
            fill.setColor(GameKit.alpha(ev >= 2 ? 0xFFB3122F : 0xFF7A1020, fade));
            for (int side = -1; side <= 1; side += 2) { android.graphics.Path h = GameKit.path(); float bx = cx + side * half * .62f; h.moveTo(bx - side * s * .09f, top + s * .03f); h.lineTo(bx + side * s * (.1f + .04f * ev), top - s * (.16f + .05f * ev)); h.lineTo(bx + side * s * .1f, top + s * .03f); h.close(); c.drawPath(h, fill); }
        }
        if (ev >= 5) {
            for (int i = -2; i <= 2; i++) { float fh = s * (.2f + .09f * (float) Math.sin(now / 70.0 + i * 1.9)), fx = cx + i * s * .13f; android.graphics.Path fl = GameKit.path(); fl.moveTo(fx - s * .07f, top + s * .04f); fl.quadTo(fx, top - fh * 1.5f, fx + s * .07f, top + s * .04f); fl.close(); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFE5173F : 0xFF5A0A1E, fade)); c.drawPath(fl, fill); }
        }
        if (SystemClock.uptimeMillis() < frenzyUntil) { stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFE5173F, fade * (.4f + .4f * (float) Math.sin(now / 60.0)))); c.drawCircle(cx, cy - bob, s * .62f, stroke); }
        if (ho >= 1) {
            float hy0 = top - s * .16f + (motion ? (float) Math.sin(now / 300.0) * s * .02f : 0);
            stroke.setStrokeWidth(s * .07f); stroke.setColor(GameKit.alpha(haloReady ? 0xFFFFD23F : 0x88FFD23F, fade)); c.drawOval(GameKit.rf(cx - s * .24f, hy0 - s * .07f, cx + s * .24f, hy0 + s * .07f), stroke);
        }
    }

    static final class Gem { int x, y, hue; long at; float pull; }
    final ArrayList<Gem> gems = new ArrayList<Gem>();
    int gemCount, gemRun; long gemAt = -10000;
    static final int[] GEM = {0xFF4FD8FF, 0xFFFF7FC8, 0xFFFFD23F, 0xFF8BF07A};
    static final class Flame { int x, y, bomb; long at; int shape; boolean safe; }
    static final class Glitch { float x, y, sp; int dir, type, tier, hp, maxHp, lastBomb = -1; boolean alive = true, boss, mini, split, melted, gibbed; long deadAt = -1, hitAt = -10000, burnUntil, loveUntil; float phase, think, burnCool, gasCool, spin;
        int bossKind; long blinkAt = -10000, slamAt = -10000; float slamCool, teleCool; float fromX, fromY; }
    static final String[] BOSS_NAMES = {"Glitch King", "Crate Golem", "Phantom Twins"};
    int bossKind() { return (stage / 5 - 1) % 3; }
    static final class Gas { int x, y; long at; }
    static final class Prize { int x, y, kind; long at; float pull; }

    final ArrayList<Bomb> bombs = new ArrayList<Bomb>();
    final ArrayList<Flame> flames = new ArrayList<Flame>();
    final ArrayList<Glitch> glitches = new ArrayList<Glitch>();
    final ArrayList<Prize> prizes = new ArrayList<Prize>();
    final ArrayList<Gas> gas = new ArrayList<Gas>();
    int bombIds; float breathCool;
    long poisonUntil, breathUntil, shieldUntil, freezeUntil, doubleUntil, laserUntil, dizzyUntil, beamAt = -10000, fartAt = -10000, freezeAt = -10000;
    float beamX, beamY, laserCool, fireSound, meltCool; int mood;
    static final int P_BOMB = 1, P_FIRE = 2, P_SPEED = 3, P_LIFE = 4, P_POISON = 5, P_BREATH = 6, P_SHIELD = 7, P_FREEZE = 8, P_DOUBLE = 10, P_LASER = 11, P_FART = 12;
    boolean dizzy() { return SystemClock.uptimeMillis() < dizzyUntil; }
    static final int P_KICK = 13, P_RAIN = 14, P_MEGA = 15;
    boolean kick; int megaLeft;
    int kills, rank, combo; long rankAt = -10000, lastKillAt = -10000, comboAt = -10000;
    static final int[] RANKS = {4, 9, 15, 22, 30, 39, 49, 60, 72};
    long overBrokenAt = -100000;
    boolean overshield(long now) { return rank >= 5 && now - overBrokenAt > (rank >= 9 ? 10000 : 20000); }
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
        cell = Math.min((w - 16 * d) / W, (h - topInset - bottomInset) * .6f / H);
        float bw = cell * W, bh = cell * H, top = topInset + 64 * d;
        board.set((w - bw) / 2f, top, (w + bw) / 2f, top + bh);
        placeControls(board.bottom + 10 * d);
    }

    final float[] scaleBuf = new float[W * H];
    final RectF startRect = new RectF(); final RectF[] startSq = new RectF[4];

    float px(float x) { return board.left + x * cell; }
    float py(float y) { return board.top + y * cell; }

    void loadStage() {
        theme();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { hidden[y][x] = 0; crumbleAt[y][x] = 0; meltAt[y][x] = 0; }
        fieldDirty = true;
        layout(stage <= 5 ? (stage - 1) : rnd.nextInt(5));
        float density = Math.min(.62f, .4f + stage * .03f);
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
        bombs.clear(); flames.clear(); glitches.clear(); prizes.clear(); bits.clear(); pops.clear();
        portalOpen = false; portalAt = 0; portalBannerAt = 0; portalSound = false; gas.clear(); rain.clear(); born.clear(); balls.clear(); gems.clear(); stains.clear(); shrineX = -1; shrineUsed = false; haloReady = holy >= 1; smiteCool = 6f; poisonUntil = breathUntil = shieldUntil = freezeUntil = doubleUntil = laserUntil = dizzyUntil = 0; mood = 0;
        boolean bossStage = stage % 5 == 0;
        int count = bossStage ? 2 + stage / 5 : Math.min(9, 3 + stage);
        for (int i = 0; i < count; i++) {
            for (int tries = 0; tries < 200; tries++) {
                int x = 1 + rnd.nextInt(W - 2), y = 1 + rnd.nextInt(H - 2);
                if (map[y][x] != FLOOR || x + y < 7 || openAround(x, y) < 2) continue;
                boolean taken = false; for (Glitch g : glitches) if ((int) g.x == x && (int) g.y == y) taken = true;
                if (taken) continue;
                Glitch g = new Glitch(); g.x = x + .5f; g.y = y + .5f;
                float r = rnd.nextFloat(); g.type = stage >= 3 && r < .25f + stage * .03f ? 2 : r < .55f ? 0 : 1;
                g.tier = Math.max(0, Math.min(5, (stage - 1) / 2 - (rnd.nextFloat() < .4f ? 1 : 0)));
                g.hp = g.maxHp = 1 + g.tier;
                g.split = stage >= 2 && rnd.nextFloat() < Math.min(.5f, .22f + stage * .03f);
                g.sp = (g.type == 0 ? 1.9f : g.type == 1 ? 2.7f : 3.1f) * (1 + .07f * g.tier); g.dir = rnd.nextInt(4); g.phase = rnd.nextFloat() * 6f;
                glitches.add(g); break;
            }
        }
        if (bossStage) {
            int kind = bossKind(), n = kind == 2 ? 2 : 1;
            for (int i = 0; i < n; i++) for (int tries = 0; tries < 300; tries++) {
                int x = 1 + rnd.nextInt(W - 2), y = 1 + rnd.nextInt(H - 2);
                if (map[y][x] != FLOOR || x + y < 12 || openAround(x, y) < 2) continue;
                boolean taken = false; for (Glitch o : glitches) if (Math.abs(o.x - x - .5f) + Math.abs(o.y - y - .5f) < 3) taken = true;
                if (taken) continue;
                Glitch g = new Glitch(); g.x = x + .5f; g.y = y + .5f; g.boss = true; g.type = 3; g.tier = 3; g.bossKind = kind;
                if (kind == 0) { g.hp = g.maxHp = 18 + stage * 2; g.sp = 1.6f; }
                else if (kind == 1) { g.hp = g.maxHp = 26 + stage * 2; g.sp = 1.15f; g.slamCool = 4.5f; }
                else { g.hp = g.maxHp = 12 + stage; g.sp = 2.3f; g.teleCool = 3.4f + i * 1.7f; }
                g.dir = rnd.nextInt(4); g.phase = rnd.nextFloat() * 6f;
                glitches.add(g); break;
            }
        }
        hx = 1.5f; hy = 1.5f; face = 2; heroAlive = false; heroDeadAt = -1;
    }

    final long[][] meltAt = new long[H][W];

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
                default: break;
            }
            for (int[] o : new int[][]{{1, 1}, {2, 1}, {1, 2}}) map[o[1]][o[0]] = FLOOR;
            if (connected()) return;
        }
    }

    boolean connected() {
        boolean[][] seen = new boolean[H][W]; java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<int[]>();
        q.add(new int[]{1, 1}); seen[1][1] = true;
        while (!q.isEmpty()) { int[] p = q.poll(); for (int k = 0; k < 4; k++) { int x = p[0] + DX[k], y = p[1] + DY[k]; if (x < 0 || y < 0 || x >= W || y >= H || seen[y][x] || map[y][x] == PILLAR) continue; seen[y][x] = true; q.add(new int[]{x, y}); } }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (map[y][x] != PILLAR && !seen[y][x]) return false;
        return true;
    }

    @Override void newGame() {
        gemCount = 0; gemRun = 0; evil = 0; holy = 0; bigUntil = 0; bigLevel = 0; fromTitle = true; stage = 1; score = 0; lives = 5; kills = 0; rank = 0; overBrokenAt = -100000; combo = 0; kick = true;  megaLeft = 0; bombsMax = 1; range = 2; speed = 3.6f; newBest = false; cells = null; sources.clear();
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
        float step = speed * dt * (SystemClock.uptimeMillis() < frenzyUntil ? 1.4f : 1f);
        face = dir;
        int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy);
        if (kick) {
            int bx = cx + DX[dir], by = cy + DY[dir];
            float along = (dir % 2 == 1 ? hx - (cx + .5f) : hy - (cy + .5f)) * (DX[dir] + DY[dir]), across = dir % 2 == 1 ? Math.abs(hy - (cy + .5f)) : Math.abs(hx - (cx + .5f));
            for (Bomb b : bombs) if (!b.done && b.slide < 0 && b.x == bx && b.y == by && along > -.12f && across < .3f) {
                b.slide = dir; b.off = 0; b.kicked = true; b.ran = 0; b.bounced = false; kickAt = SystemClock.uptimeMillis(); kickDir = dir; PixelStackSound.play(PixelStackSound.SOFT, .4f, 1.4f); GameKit.tick(a, .5f);
                burst(px(bx + .5f), py(by + .5f), 0xFFFFFFFF, 6, 100, 3);
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
                    if (struck != null) { b.x = nx; b.y = ny; b.off = 0; b.slide = -1; b.fuse = 0; b.struck = true; gib(struck); break; }
                    if (stop) { bounce(b); break; }
                    b.x = nx; b.y = ny; b.off -= 1f; b.ran++;
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
            for (Glitch g : glitches) if (g.alive && g.lastBomb != f.bomb && (int) Math.floor(g.x) == f.x && (int) Math.floor(g.y) == f.y) { g.lastBomb = f.bomb; hurt(g, 1); }
            for (int i = prizes.size() - 1; i >= 0; i--) { Prize p = prizes.get(i); if (p.x == f.x && p.y == f.y && now - p.at > 700) { prizes.remove(i); burst(px(p.x + .5f), py(p.y + .5f), 0xFFFFFFFF, 8, 120, 3); } }
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
                float mx = px(hx + DX[face] * .3f), my = py(hy - .05f + DY[face] * .3f);
                for (int i = 0; i < 3; i++) {
                    GameKit.Bit b = new GameKit.Bit(); b.x = mx; b.y = my; float v = (300 + rnd.nextFloat() * 160) * d, side = (rnd.nextFloat() - .5f) * 90 * d;
                    b.vx = DX[face] * v + DY[face] * side; b.vy = DY[face] * v + DX[face] * side; b.size = cell * (.16f + rnd.nextFloat() * .12f);
                    b.colour = rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A; b.round = true; b.gravity = -.06f; b.decay = 3.4f; bits.add(b);
                }
            }
            {
                fireSound -= dt;
                if (fireSound <= 0) { fireSound = .24f; PixelStackSound.play(PixelStackSound.FIRE, .2f, .9f + rnd.nextFloat() * .2f); }
                for (Glitch g : glitches) {
                    if (!g.alive || g.burnUntil > 0) continue;
                    float ax = (g.x - hx) * DX[face] + (g.y - hy) * DY[face], side = Math.abs((g.x - hx) * DY[face] + (g.y - hy) * DX[face]);
                    if (ax > 0 && ax < 2.4f && side < .55f) { if (g.boss) { if (g.burnCool <= 0) { g.burnCool = .5f; hurt(g, 1); } } else ignite(g); }
                }
                meltCool -= dt;
                if (meltCool <= 0) {
                    int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy);
                    for (int i = 1; i <= 2; i++) {
                        int x = cx + DX[face] * i, y = cy + DY[face] * i;
                        if (x < 0 || y < 0 || x >= W || y >= H || map[y][x] == PILLAR) break;
                        if (map[y][x] == CRATE) { if (crumbleAt[y][x] == 0) { meltCool = .3f; meltAt[y][x] = now; crumbleAt[y][x] = now; fieldDirty = true; addScore(10); burst(px(x + .5f), py(y + .5f), 0xFFFF7A3D, 8, 140, 4); PixelStackSound.play(PixelStackSound.FIRE, .25f, .7f); } break; }
                    }
                }
            }
        }
        if (heroAlive && now < laserUntil && state == PLAY) {
            laserCool -= dt;
            if (laserCool <= 0) {
                Glitch target = null; float bestAx = 99;
                for (Glitch g : glitches) {
                    if (!g.alive) continue;
                    float ax = (g.x - hx) * DX[face] + (g.y - hy) * DY[face], side = Math.abs((g.x - hx) * DY[face] + (g.y - hy) * DX[face]);
                    if (ax > 0 && side < .45f && ax < bestAx && clearLine(face, ax)) { bestAx = ax; target = g; }
                }
                if (target != null) {
                    laserCool = .45f; beamAt = now; beamX = target.x; beamY = target.y; hurt(target, 1);
                    PixelStackSound.play(PixelStackSound.SHOT, .3f, 1.6f); GameKit.tick(a, .3f);
                    burst(px(target.x), py(target.y), 0xFFFF4D6D, 8, 140, 3);
                }
            }
        }
        boolean frozen = now < freezeUntil, woozy = dizzy();
        for (Glitch g : glitches) {
            if (!g.alive) continue;
            if (g.loveUntil > 0) {
                if (now >= g.loveUntil) {
                    for (int i = 0; i < 14; i++) { GameKit.Bit b = spark(px(g.x), py(g.y), i % 2 == 0 ? 0xFFFF5C8A : 0xFFFFC2D4, 200, 4.5f); b.round = true; b.gravity = -.3f; b.decay = 1.1f; }
                    PixelStackSound.play(PixelStackSound.SPARKLE, .4f, 1.5f);
                    g.loveUntil = 0; g.split = false; squash(g); continue;
                }
                g.phase += dt * 3;
                float dl = (float) Math.hypot(hx - g.x, hy - g.y);
                if (heroAlive && dl > .9f) { g.x += (hx - g.x) / dl * dt * 1.5f; g.y += (hy - g.y) / dl * dt * 1.5f; }
                if (motion && rnd.nextFloat() < .12f) { GameKit.Bit b = spark(px(g.x), py(g.y - .4f), 0xFFFF5C8A, 30, 4f); b.round = true; b.gravity = -.5f; b.decay = 1.4f; }
                continue;
            }
            if (g.burnUntil > 0) {
                if (now >= g.burnUntil) { g.melted = true; squash(g); PixelStackSound.play(PixelStackSound.FART, .25f, 1.6f); continue; }
                if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(px(g.x), py(g.y - .3f), rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A, 50, 4); b.round = true; b.gravity = -.3f; b.decay = 2.5f; }
            }
            if (!frozen) g.phase += dt * (g.burnUntil > 0 ? 14 : 6);
            if (woozy) g.spin += dt * 4;
            if (state != PLAY) continue;
            if (g.boss && !frozen) bossTick(g, dt, now);
            float near = heroAlive ? (float) Math.hypot(g.x - hx, g.y - hy) : 99f;
            if (g.mini && heroAlive) {
                float want = evilLevel() >= 4 ? 3f : 1.9f;
                if (near < .8f) { eat(g); continue; }
                if (near < want && now - g.hitAt > 350) { float pull = dt * (2.2f + (want - near) * 3f) / Math.max(.2f, near); g.x += (hx - g.x) * pull; g.y += (hy - g.y) * pull; continue; }
            }
            float dread = evilLevel() >= 1 && !g.boss && near < 2.7f ? .5f : 1f;
            if (!frozen) moveGlitch(g, dt * dread * (g.burnUntil > 0 ? 1.8f : woozy ? .45f : 1f));
            float reach = g.boss ? .9f : .62f;
            if (heroAlive && !g.mini && g.burnUntil == 0 && Math.abs(g.x - hx) < reach && Math.abs(g.y - hy) < reach) {
                if (now < shieldUntil) { if (now - g.hitAt > 600) { hurt(g, 1); g.dir = (g.dir + 2) % 4; } }
                else if (evilLevel() >= 3 && !g.boss) { if (now - g.hitAt > 700) { hurt(g, 1); g.dir = (g.dir + 2) % 4; eatAt = now; if (evilLevel() >= 4) frenzyUntil = now + 4000; burst(px(g.x), py(g.y), 0xFFE5173F, 8, 160, 3); PixelStackSound.play(PixelStackSound.POP, .4f, .7f); } }
                else if (now > safeUntil && !frozen) die();
            }
        }
        for (int i = glitches.size() - 1; i >= 0; i--) { Glitch g = glitches.get(i); if (!g.alive && now - g.deadAt > (g.melted ? 1150 : 600)) glitches.remove(i); }
        if (heroAlive) for (int i = prizes.size() - 1; i >= 0; i--) {
            Prize p = prizes.get(i);
            float pd = (float) Math.hypot(hx - p.x - .5f, hy - p.y - .5f);
            if (heroAlive && state == PLAY && pd < (holyLevel() >= 2 ? 4f : 2.3f) && now - p.at > 450) { p.pull = Math.min(1f, p.pull + dt * 3.6f); if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(px(prizeX(p)), py(prizeY(p)), TIER_COLOUR[tierOf(p.kind)], 30, 3f); b.round = true; b.gravity = 0; b.decay = 3f; } }
            else p.pull = Math.max(0f, p.pull - dt * 3f);
            if (p.pull >= 1f || ((int) Math.floor(hx) == p.x && (int) Math.floor(hy) == p.y)) { take(p); prizes.remove(i); }
        }
        if (state == PLAY && heroAlive && portalOpen && Math.abs(hx - (portalX + .5f)) < .3f && Math.abs(hy - (portalY + .5f)) < .3f) {
            pullX = hx; pullY = hy; sucked = false;
            go(CLEAR); PixelStackSound.play(PixelStackSound.SUCCESS, .55f, 1f); GameKit.thump(a, .7f);
        }
    }

    int bonusKind() { float roll = rnd.nextFloat(); int[] from = GUIDE[roll < .5f ? 0 : roll < .88f ? 1 : 2]; return from[rnd.nextInt(from.length)]; }

    boolean clearLine(int f, float ax) {
        int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy);
        for (int i = 1; i < Math.ceil(ax - .3f); i++) { int x = cx + DX[f] * i, y = cy + DY[f] * i; if (x < 0 || y < 0 || x >= W || y >= H || map[y][x] != FLOOR) return false; }
        return true;
    }

    void ignite(Glitch g) {
        g.burnUntil = SystemClock.uptimeMillis() + 1700;
        g.dir = (face + 0) % 4;
        burst(px(g.x), py(g.y), 0xFFFF7A3D, 10, 160, 4);
        PixelStackSound.play(PixelStackSound.BOING, .35f, 1.5f);
    }

    int fartKind; float fartReach;
    void fart() {
        long now = SystemClock.uptimeMillis();
        int ho = holyLevel(), ev = evilLevel(), tier = Math.max(ho, ev);
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
            if (g.boss) { hurt(g, tier + (fartKind == 2 ? 1 : 0)); continue; }
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

    int glitchesLeft() { int n = born.size(); for (Glitch g : glitches) if (g.alive) n++; return n; }

    void openPortal() {
        if (portalOpen || portalX < 0) return;
        if (map[portalY][portalX] == CRATE) { if (crumbleAt[portalY][portalX] == 0) { crumbleAt[portalY][portalX] = SystemClock.uptimeMillis(); fieldDirty = true; crateBits(portalX, portalY); } return; }
        if (hidden[portalY][portalX] != 9) return;
        portalOpen = true; portalAt = SystemClock.uptimeMillis(); portalBannerAt = portalAt;
        placeShrine();
        shake(6); GameKit.thump(a, .8f);
    }

    boolean threatened(int x, int y) {
        for (Bomb b : bombs) { if (b.done) continue; int r = reachOf(b); if ((b.x == x && Math.abs(b.y - y) <= r) || (b.y == y && Math.abs(b.x - x) <= r)) return true; }
        return false;
    }
    void moveGlitch(Glitch g, float dt) {
        float step = g.sp * dt;
        int cx = (int) Math.floor(g.x), cy = (int) Math.floor(g.y);
        float ccx = cx + .5f, ccy = cy + .5f;
        boolean atCentre = Math.abs(g.x - ccx) < step + .01f && Math.abs(g.y - ccy) < step + .01f;
        if (atCentre) {
            g.think -= 1;
            boolean ahead = !blocked(cx + DX[g.dir], cy + DY[g.dir], false);
            boolean decide = !ahead || (g.type == 1 && rnd.nextFloat() < .35f) || (g.type == 0 && rnd.nextFloat() < .12f) || g.type == 2 || g.burnUntil > 0;
            if (decide) {
                g.x = ccx; g.y = ccy;
                int[] opts = new int[4]; int n = 0;
                for (int k = 0; k < 4; k++) if (!blocked(cx + DX[k], cy + DY[k], false)) opts[n++] = k;
                if (n == 0) return;
                int pick = opts[rnd.nextInt(n)];
                if (g.burnUntil > 0 && heroAlive) {
                    float bestD = -1;
                    for (int k = 0; k < n; k++) { float dd = Math.abs(cx + DX[opts[k]] + .5f - hx) + Math.abs(cy + DY[opts[k]] + .5f - hy); if (dd > bestD) { bestD = dd; pick = opts[k]; } }
                } else if ((g.type == 2 && heroAlive && rnd.nextFloat() < .7f) || (g.tier >= 1 && heroAlive && Math.abs(hx - g.x) + Math.abs(hy - g.y) < 4 + g.tier && rnd.nextFloat() < .22f * g.tier)) {
                    float dx = hx - g.x, dy = hy - g.y; int want = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0);
                    for (int k = 0; k < n; k++) if (opts[k] == want) pick = want;
                } else if (ahead && g.type == 2) pick = g.dir;
                if (n > 1 && pick == (g.dir + 2) % 4 && ahead) pick = g.dir;
                if (g.tier >= 2 && n > 1 && g.burnUntil == 0 && threatened(cx + DX[pick], cy + DY[pick])) {
                    for (int k = 0; k < n; k++) if (!threatened(cx + DX[opts[k]], cy + DY[opts[k]])) { pick = opts[k]; break; }
                }
                g.dir = pick;
            }
        }
        if (g.tier >= 3 && heroAlive && !g.boss) {
            float dx = hx - g.x, dy = hy - g.y;
            boolean lined = DX[g.dir] != 0 ? Math.abs(dy) < .4f && dx * DX[g.dir] > 0 && Math.abs(dx) < 4 : Math.abs(dx) < .4f && dy * DY[g.dir] > 0 && Math.abs(dy) < 4;
            if (lined) step *= 1.45f + .1f * (g.tier - 3);
        }
        float nx = g.x + DX[g.dir] * step, ny = g.y + DY[g.dir] * step;
        int tx = (int) Math.floor(nx + DX[g.dir] * .45f), ty = (int) Math.floor(ny + DY[g.dir] * .45f);
        if (blocked(tx, ty, false) && !(tx == cx && ty == cy)) { g.x = ccx; g.y = ccy; g.dir = (g.dir + 2) % 4; return; }
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
        float cx = px(b.x + .5f), cy = py(b.y + .5f);
        burst(cx, cy, 0xFFFFC94A, 22 + (int) (r * 6), 300 + r * 60, 6); burst(cx, cy, 0xFFFF7A3D, 14, 240, 5); burst(cx, cy, 0xFFFFFFFF, 8, 380, 4);
        shake(10 + r * 3); if (r > 3) GameKit.rumble(a);
        PixelStackSound.play(PixelStackSound.BOOM, .65f, Math.max(.55f, 1f - r * .08f));
        GameKit.thump(a, 1f);
    }
    void drawBreath(Canvas c, long now) {
        if (!heroAlive) return;
        float x0 = px(hx + DX[face] * .28f), y0 = py(hy - .05f + DY[face] * .28f), len = cell * 2.25f;
        float ang = face == 1 ? 0 : face == 2 ? 90 : face == 3 ? 180 : -90;
        int save = c.save(); c.rotate(ang, x0, y0);
        int[] cols = {0xFFE5532B, 0xFFFF9A3D, 0xFFFFD86B, 0xFFFFF6D0}; float[] wide = {.5f, .38f, .24f, .1f}, reach = {1f, .88f, .7f, .45f};
        fill.setShader(null);
        for (int i = 0; i < 4; i++) {
            float fl = 1 + .1f * (float) Math.sin(now / (38.0 + i * 9) + i * 2), L = len * reach[i] * fl, Wd = cell * wide[i] * (1 + .12f * (float) Math.sin(now / (50.0 + i * 7) + i));
            android.graphics.Path p = GameKit.path();
            p.moveTo(x0, y0 - cell * .07f);
            p.cubicTo(x0 + L * .3f, y0 - Wd * 1.1f, x0 + L * .75f, y0 - Wd * (.9f + .25f * (float) Math.sin(now / 70.0 + i)), x0 + L, y0 + (float) Math.sin(now / 55.0 + i * 1.7) * Wd * .35f);
            p.cubicTo(x0 + L * .75f, y0 + Wd * (.9f + .25f * (float) Math.cos(now / 64.0 + i)), x0 + L * .3f, y0 + Wd * 1.1f, x0, y0 + cell * .07f);
            p.close();
            fill.setColor(GameKit.alpha(cols[i], i == 0 ? .8f : .95f)); c.drawPath(p, fill);
        }
        for (int i = 0; i < 3; i++) {
            float ph = ((now / 420f) + i / 3f) % 1f, rx = x0 + len * ph;
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFFFE9A8, (1 - ph) * .6f)); c.drawOval(GameKit.rf(rx - cell * .06f, y0 - cell * (.16f + .34f * ph), rx + cell * .06f, y0 + cell * (.16f + .34f * ph)), stroke);
        }
        c.restoreToCount(save);
        fill.setColor(0x33FFB347); c.drawCircle(x0, y0, cell * .45f, fill);
    }

    void drawSouls(Canvas c, long now) {
        fill.setShader(null);
        if (holyLevel() >= 4 && heroAlive) {
            float gx = px(hx + (float) Math.cos(orb) * 1.15f), gy = py(hy + (float) Math.sin(orb) * 1.15f);
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
            fill.setColor(GameKit.alpha(0xFFE5532B, fade * .85f)); c.drawCircle(cx, cy, r, fill);
            fill.setColor(GameKit.alpha(b.mega ? 0xFFFFD23F : 0xFFFF9A3D, fade * .95f)); c.drawCircle(cx, cy, r * .8f, fill);
            fill.setColor(GameKit.alpha(0xFFFFD86B, fade)); c.drawCircle(cx, cy, r * .58f, fill);
            fill.setColor(GameKit.alpha(0xFFFFF6D0, fade)); c.drawCircle(cx, cy, r * .32f, fill);
            for (int k = 0; k < 10; k++) {
                double ang = k * Math.PI / 5 + now / 300.0; float rr = r * (.92f + .1f * (float) Math.sin(now / 60.0 + k * 1.9));
                fill.setColor(GameKit.alpha(k % 2 == 0 ? 0xFFFF7A3D : 0xFFFFC94A, fade * .9f)); c.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr, r * .16f, fill);
            }
            stroke.setStrokeWidth(3 * d * (1 - t)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, (1 - t) * .7f)); c.drawCircle(cx, cy, b.r * cell * (.4f + .9f * GameKit.ease(t)), stroke);
        }
    }

    void stepGems(float dt, long now) {
        for (int i = gems.size() - 1; i >= 0; i--) {
            Gem g = gems.get(i);
            if (now - g.at > 14000) { gems.remove(i); continue; }
            float dist = (float) Math.hypot(hx - g.x - .5f, hy - g.y - .5f);
            if (heroAlive && dist < (holyLevel() >= 2 ? 4.4f : 2.6f) && now - g.at > 300) g.pull = Math.min(1f, g.pull + dt * 4f); else g.pull = Math.max(0f, g.pull - dt * 3f);
            if (g.pull < 1f && !(heroAlive && dist < .5f)) continue;
            gems.remove(i);
            gemRun = now - gemAt < 2200 ? gemRun + 1 : 1; gemAt = now; gemCount++;
            int pts = 50 * Math.min(gemRun, 8) * (holyLevel() >= 2 ? 2 : 1); addScore(pts);
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
        if (g.bossKind == 1) {
            g.slamCool -= dt;
            if (g.slamCool <= 0) {
                g.slamCool = g.hp < g.maxHp / 2 ? 3.2f : 4.5f; g.slamAt = now;
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
        } else if (g.bossKind == 2) {
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
    boolean blinking(Glitch g) { return g.bossKind == 2 && SystemClock.uptimeMillis() - g.blinkAt < 550; }

    void hurt(Glitch g, int n) {
        if (!g.alive) return;
        if (g.boss && blinking(g)) return;
        long now = SystemClock.uptimeMillis();
        g.hp -= n; g.hitAt = now;
        if (g.boss && g.bossKind == 2 && g.hp <= 0) for (Glitch o : glitches) if (o != g && o.alive && o.boss) { o.sp *= 1.6f; o.teleCool = Math.min(o.teleCool, 1.5f); burst(px(o.x), py(o.y), 0xFFFF2D55, 18, 240, 5); }
        if (g.boss && g.bossKind == 0 && g.hp > 0) {
            shake(6);
            if (g.hp % 6 == 0) for (int k = 0; k < 2; k++) {
                Glitch m = new Glitch(); m.x = g.x; m.y = g.y; m.mini = true; m.type = 2; m.hp = m.maxHp = 1; m.sp = 3f; m.dir = rnd.nextInt(4); m.phase = rnd.nextFloat() * 6; m.hitAt = now; m.lastBomb = g.lastBomb; born.add(m);
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
        int pts = (g.boss ? 3000 : (g.type == 0 ? 100 : g.type == 1 ? 200 : 400) * (1 + g.tier)) * Math.max(1, combo); addScore(pts);
        pop("+" + (now < doubleUntil ? pts * 2 : pts), px(g.x), py(g.y - .4f), night ? 0xFFFFFFFF : 0xFF14161C, g.boss ? 22 : 15);
        if (g.boss) { burst(px(g.x), py(g.y), 0xFFFFD23F, 40, 380, 7); shake(18); GameKit.rumble(a); PixelStackSound.play(PixelStackSound.BOOM, .6f, .7f); }
        if (g.split && !g.mini && !g.boss) {
            for (int k = 0; k < 2; k++) {
                Glitch m = new Glitch(); m.x = g.x; m.y = g.y; m.mini = true; m.type = g.type; m.hp = m.maxHp = 1; m.sp = 3.4f;
                m.dir = k == 0 ? (g.dir + 1) % 4 : (g.dir + 3) % 4; m.phase = rnd.nextFloat() * 6; m.hitAt = now; m.lastBomb = g.lastBomb;
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
            pop(I18n.t("Level {0}").replace("{0}", String.valueOf(rank + 1)), px(hx), py(hy - 1), night ? GameKit.LIGHT : GameKit.BLUE, 20);
            burst(px(hx), py(hy), 0xFFFFE066, 22, 220, 4);
            PixelStackSound.play(PixelStackSound.PICK, .6f, .9f); postDelayed(() -> PixelStackSound.play(PixelStackSound.PERFECT, .45f, 1.1f), 160);
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
            burst(px(hx), py(hy), 0xFF7FE9FF, 22, 260, 5); shake(6);
            PixelStackSound.play(PixelStackSound.TING, .55f, .8f); GameKit.thump(a, .7f);
            return;
        }
        if (holyLevel() >= 1 && haloReady) {
            haloReady = false; haloAt = SystemClock.uptimeMillis(); safeUntil = haloAt + 1800;
            burst(px(hx), py(hy), 0xFFFFD23F, 22, 260, 5); shake(8);
            PixelStackSound.play(PixelStackSound.TING, .6f, 1.2f); PixelStackSound.play(PixelStackSound.SPARKLE, .5f, 1f); GameKit.thump(a, .8f);
            return;
        }
        fall();
        heroAlive = false; heroDeadAt = SystemClock.uptimeMillis(); breathUntil = 0; poisonUntil = 0; shieldUntil = 0;
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
            case P_POISON: return I18n.t("Bombs leave poison gas"); case P_BREATH: return I18n.t("Burn what you face"); case P_SHIELD: return I18n.t("Blocks every hit"); case P_LASER: return I18n.t("Zap in a straight line"); case P_KICK: return I18n.t("Kick bombs away"); case P_MEGA: return I18n.t("Three huge bombs"); case P_FREEZE: return I18n.t("Stops every enemy");
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
            case P_LASER: laserUntil = SystemClock.uptimeMillis() + 12000; words = I18n.t("Laser eyes!"); break;
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

    int glitchColour(Glitch g) { return g.boss ? (g.bossKind == 1 ? 0xFF8A7560 : g.bossKind == 2 ? 0xFF35D0E0 : 0xFF8E2BB6) : g.type == 0 ? 0xFF2BB673 : g.type == 1 ? 0xFFF2C230 : 0xFFE53935; }

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
                block(c, cx - half, cy - half, cx + half, cy + half, border ? edge : pillar, fade);
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
        int body = b.mega ? 0xFFC62828 : b.poison ? 0xFF2F8A3A : night ? 0xFF3A4366 : 0xFF1B2440;
        if (b.fuse < .6f && (now / 70) % 2 == 0) body = GameKit.mix(body, 0xFFE53935, .6f);
        fill.setShader(null); fill.setColor(GameKit.alpha(0x33000000, fade)); c.drawOval(GameKit.rf(cx - r, cy + r * .7f, cx + r, cy + r * 1.05f), fill);
        fill.setColor(GameKit.alpha(body, fade)); c.drawCircle(cx, cy, r, fill);
        if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x88FFFFFF, fade)); c.drawCircle(cx, cy, r, stroke); }
        fill.setColor(GameKit.alpha(0x55FFFFFF, fade)); c.drawCircle(cx - r * .35f, cy - r * .35f, r * .28f, fill);
        float fl = Math.max(.1f, b.fuse / 2.6f);
        float fx = cx + r * .45f, fy = cy - r * .8f, ex = fx + cell * .14f * fl, ey = fy - cell * .2f * fl;
        stroke.setStrokeWidth(2.2f * d); stroke.setColor(GameKit.alpha(night ? 0xFFC9B8A0 : 0xFF8A6D4E, fade)); c.drawLine(fx, fy, ex, ey, stroke);
        float sp = cell * .08f * (1 + .4f * (float) Math.sin(now / 40.0));
        fill.setColor(GameKit.alpha(0xFFFFE066, fade)); c.drawCircle(ex, ey, sp, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(ex, ey, sp * .45f, fill);
        if (motion && rnd.nextFloat() < .35f) { GameKit.Bit s = spark(ex, ey, 0xFFFFC94A, 60, 2); s.round = true; s.decay = 3.5f; s.gravity = .2f; }
    }

    void drawFlame(Canvas c, Flame f, long now, float fade) {
        float t = (now - f.at) / 620f;
        if (t >= 1 || f.shape == 7) return;
        float grow = t < .15f ? GameKit.ease(t / .15f) : 1 - GameKit.easeIn((t - .15f) / .85f) * .9f;
        float flick = 1 + .08f * (float) Math.sin(now / 30.0 + f.x * 1.7 + f.y);
        float cx = px(f.x + .5f), cy = py(f.y + .5f), h = cell * .5f;
        float w0 = cell * .42f * grow * flick;
        int[] layers = {0xFFFF5A36, 0xFFFFA23D, 0xFFFFE27A, 0xFFFFFFFF};
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

    void drawHero(Canvas c, long now, float fade, float scale) {
        if (!heroAlive) return;
        if (now < safeUntil && now - heroBornAt > 500 && (now / 90) % 2 == 0) fade *= .45f;
        float cx = px(hx), cy = py(hy), s = cell * .78f * scale;
        float born = GameKit.spring((now - heroBornAt) / 500f); s *= born;
        boolean kicking = now - kickAt >= 0 && now - kickAt < 260;
        float kickK = kicking && motion ? (float) Math.sin(Math.PI * (now - kickAt) / 260f) : kicking ? 1f : 0f;
        if (kicking) { cx += DX[kickDir] * s * .12f * kickK; cy += DY[kickDir] * s * .12f * kickK; }
        boolean walking = dir >= 0 && state == PLAY;
        float bob = walking && motion ? (float) Math.abs(Math.sin(walk * Math.PI * 1.6)) * cell * .07f : 0;
        float squash = walking && motion ? 1 + .05f * (float) Math.sin(walk * Math.PI * 3.2) : 1;
        fill.setShader(null); fill.setColor(GameKit.alpha(0x30000000, fade)); c.drawOval(GameKit.rf(cx - s * .4f, cy + s * .38f, cx + s * .4f, cy + s * .52f), fill);
        float step = walking ? (float) Math.sin(walk * Math.PI * 1.6) * s * .1f : 0;
        fill.setColor(GameKit.alpha(GameKit.mix(GameKit.BLUE, 0xFF000000, .3f), fade));
        c.drawCircle(cx - s * .2f, cy + s * .42f + step * .3f, s * .11f, fill); c.drawCircle(cx + s * .2f, cy + s * .42f - step * .3f, s * .11f, fill);
        float top = cy - s * .45f - bob, bot = cy + s * .4f - bob, half = s * .44f * squash;
        if (rank >= 9) { float q = (float) (.5 + .5 * Math.sin(now / 240.0)); fill.setColor(GameKit.alpha(0xFFFFD23F, fade * (.14f + .1f * q))); c.drawCircle(cx, cy - bob, s * (.66f + .05f * q), fill); }
        if (rank >= 7) for (int side = -1; side <= 1; side += 2) {
            float flapW = motion ? (float) Math.sin(now / 170.0) * s * .06f : 0;
            android.graphics.Path wg = GameKit.path(); float wx = cx + side * half * .8f, wy = cy - s * .12f - bob;
            wg.moveTo(wx, wy); wg.lineTo(wx + side * s * .46f, wy - s * .3f - flapW); wg.lineTo(wx + side * s * .4f, wy - s * .02f); wg.lineTo(wx + side * s * .32f, wy + s * .16f + flapW); wg.lineTo(wx, wy + s * .16f); wg.close();
            fill.setColor(GameKit.alpha(0xFFF4F7FF, fade * .92f)); c.drawPath(wg, fill);
        }
        drawSoul(c, cx, cy, s, top, half, bob, now, fade, true);
        int skin = evilLevel() > 0 ? GameKit.mix(GameKit.mix(GameKit.BLUE, 0xFF7A1020, Math.min(1f, .3f * evilLevel())), 0xFF14060A, evilLevel() >= 5 ? .45f : 0f) : holyLevel() > 0 ? GameKit.mix(GameKit.BLUE, holyLevel() >= 5 ? 0xFFFFF1B8 : 0xFFEAF2FF, .17f * holyLevel()) : GameKit.BLUE;
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
        if (rank >= 4) for (int side = -1; side <= 1; side += 2) {
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
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawOval(GameKit.rf(x - s * eyeW, y - s * eyeH, x + s * eyeW, y + s * eyeH), fill);
            fill.setColor(GameKit.alpha(lasers || evilLevel() >= 2 ? 0xFFFF2D55 : 0xFF14161C, fade)); c.drawCircle(x + ex * .5f, y + ey * .5f, s * pupil * (lasers ? 1.3f : 1f), fill);
            if (evilLevel() >= 2 && !lasers) { fill.setColor(GameKit.alpha(0xFFFF2D55, fade * (.2f + .12f * (float) Math.sin(now / 110.0)))); c.drawCircle(x + ex * .5f, y + ey * .5f, s * .14f, fill); }
            if (evilLevel() >= 1 || kicking) { stroke.setStrokeWidth(s * .05f); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawLine(x - side * s * .13f, y - s * (eyeH + .07f), x + side * s * .1f, y - s * (eyeH - .03f), stroke); }
            if (lasers) { fill.setColor(GameKit.alpha(0xFFFF2D55, fade * (.25f + .15f * (float) Math.sin(now / 90.0)))); c.drawCircle(x + ex * .5f, y + ey * .5f, s * .16f, fill); }
        }
        if (lasers && now - beamAt < 160) {
            float k = 1 - (now - beamAt) / 160f;
            for (int side = -1; side <= 1; side += 2) {
                float x0 = cx + side * s * .16f + ex, y0 = cy - s * .08f - bob + ey;
                stroke.setStrokeWidth(s * .12f * k); stroke.setColor(GameKit.alpha(0x66FF2D55, fade)); c.drawLine(x0, y0, px(beamX) + side * s * .05f, py(beamY), stroke);
                stroke.setStrokeWidth(s * .04f * k); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawLine(x0, y0, px(beamX) + side * s * .05f, py(beamY), stroke);
            }
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
            float my = cy + s * .2f - bob, mw = s * .15f, mh = s * .07f;
            box.set(cx - mw + ex * .4f, my - mh, cx + mw + ex * .4f, my + mh);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawRoundRect(box, mh * .6f, mh * .6f, fill);
            stroke.setStrokeWidth(Math.max(1f, s * .03f)); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawRoundRect(box, mh * .6f, mh * .6f, stroke);
            for (int k = -1; k <= 1; k++) c.drawLine(cx + k * mw * .5f + ex * .4f, my - mh, cx + k * mw * .5f + ex * .4f, my + mh, stroke);
            c.drawLine(cx - mw + ex * .4f, my, cx + mw + ex * .4f, my, stroke);
        } else if (mood == 1) {
            float my = cy + s * .22f - bob;
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - s * .07f, my - s * .09f, cx + s * .07f, my + s * .09f), fill);
        } else if (wide) {
            float mo = s * (.1f + .03f * (float) Math.sin(now / 45.0)), my = cy + s * .2f - bob;
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - s * .13f + ex * .4f, my - mo, cx + s * .13f + ex * .4f, my + mo), fill);
            fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawOval(GameKit.rf(cx - s * .07f + ex * .4f, my, cx + s * .07f + ex * .4f, my + mo * .8f), fill);
        } else if (fire) {
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawArc(GameKit.rf(cx - s * .1f, cy + s * .08f - bob, cx + s * .1f, cy + s * .24f - bob), 20, 140, false, stroke);
        } else if (dizzy()) {
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawArc(GameKit.rf(cx - s * .12f, cy + s * .06f - bob, cx + s * .12f, cy + s * .26f - bob), 10, 160, false, stroke);
        } else if (evilLevel() >= 1) {
            float my = cy + s * .17f - bob;
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawArc(GameKit.rf(cx - s * .15f + ex * .4f, my - s * .1f, cx + s * .15f + ex * .4f, my + s * .1f), 15, 150, false, stroke);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade));
            for (int side = -1; side <= 1; side += 2) { android.graphics.Path f = GameKit.path(); float fx = cx + side * s * .08f + ex * .4f; f.moveTo(fx - s * .03f, my + s * .05f); f.lineTo(fx + s * .03f, my + s * .05f); f.lineTo(fx, my + s * .13f); f.close(); c.drawPath(f, fill); }
        } else { fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(cx, cy + s * .2f - bob, s * .05f, fill); }
        if (rank >= 8) for (int i = 0; i < 5; i++) {
            double ang = now / 360.0 + i * Math.PI * 2 / 5; fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFE066, fade * .9f));
            c.drawCircle(cx + (float) Math.cos(ang) * s * .72f, cy - bob + (float) Math.sin(ang) * s * .72f, s * .04f, fill);
        }
        if (rank >= 5) {
            float wait = rank >= 9 ? 10000f : 20000f, k = Math.min(1f, (now - overBrokenAt) / wait);
            stroke.setStrokeWidth(Math.max(1.5f, s * .04f));
            if (k >= 1f) {
                float q = (float) (.5 + .5 * Math.sin(now / 300.0));
                fill.setShader(null); fill.setColor(GameKit.alpha(0xFF7FE9FF, fade * (.08f + .05f * q))); c.drawCircle(cx, cy - bob, s * .82f, fill);
                stroke.setColor(GameKit.alpha(0xFF7FE9FF, fade * (.6f + .3f * q))); c.drawCircle(cx, cy - bob, s * .82f, stroke);
            } else { stroke.setColor(GameKit.alpha(0xFF7FE9FF, fade * .35f)); c.drawArc(GameKit.rf(cx - s * .82f, cy - bob - s * .82f, cx + s * .82f, cy - bob + s * .82f), -90, 360 * k, false, stroke); }
        }
        if (kicking) {
            float fx = cx + DX[kickDir] * s * (.3f + .34f * kickK), fy = cy + s * .4f + DY[kickDir] * s * (.05f + .3f * kickK) - (kickDir % 2 == 1 ? s * .12f * kickK : 0);
            fill.setColor(GameKit.alpha(GameKit.mix(GameKit.BLUE, 0xFF000000, .3f), fade)); c.drawCircle(fx, fy, s * .14f, fill);
            stroke.setStrokeWidth(Math.max(1.5f, s * .04f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * .75f * kickK));
            for (int k = -1; k <= 1; k += 2) {
                float ox = DY[kickDir] * s * .09f * k, oy = DX[kickDir] * s * .09f * k;
                c.drawLine(fx - DX[kickDir] * s * .2f + ox, fy - DY[kickDir] * s * .2f + oy, fx - DX[kickDir] * s * .36f + ox, fy - DY[kickDir] * s * .36f + oy, stroke);
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
        float cx = px(g.x), cy = py(g.y), s = cell * (.72f + .03f * g.tier) * (g.boss ? 1.7f : g.mini ? .6f : 1f);
        if (g.boss) cy -= cell * .25f;
        if (g.boss && g.bossKind == 1) { drawGolem(c, g, cx, cy, s, now, fade); return; }
        if (g.boss && g.bossKind == 2) { drawPhantom(c, g, cx, cy, s * .82f, now, fade); return; }
        int col = glitchColour(g);
        if (g.tier > 0) col = GameKit.mix(col, g.tier >= 3 ? 0xFF6A1B9A : 0xFF14161C, .12f * g.tier);
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
        float lx = DX[g.dir] * s * .07f, ly = DY[g.dir] * s * .05f;
        boolean burning = g.burnUntil > 0 && g.alive, woozy = dizzy() && g.alive;
        if (burning) {
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
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * s * .15f + lx, y = cy - s * .05f + ly;
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, y, s * (burning ? .13f : .1f), fill);
            fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawCircle(x + lx * .6f, y + ly * .6f, s * (burning ? .035f : .05f), fill);
            if (g.type == 2 || g.tier >= 2) { stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawLine(x - s * .1f * side, y - s * .17f, x + s * .06f * side, y - s * .12f, stroke); }
        }
        if (burning) {
            fill.setColor(GameKit.alpha(0xFF3A0D12, fade)); c.drawOval(GameKit.rf(cx - s * .08f, cy + s * .1f, cx + s * .08f, cy + s * .24f), fill);
        }
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
        c.restoreToCount(save);
        if (g.alive && !g.boss) healthBar(c, cx, cy - s * .7f, s * .8f, g.hp, g.maxHp, fade);
        if (g.loveUntil > 0 && g.alive) {
            float beat = motion ? (float) (.5 + .5 * Math.sin(now / 130.0 + g.phase)) : .5f;
            heart(c, cx - s * .2f, cy - s * .66f - beat * s * .06f, s * (.11f + .03f * beat), GameKit.alpha(0xFFFF4D7D, fade));
            heart(c, cx + s * .22f, cy - s * .8f + beat * s * .05f, s * .08f, GameKit.alpha(0xFFFF8FB3, fade));
        }
    }

    void drawGolem(Canvas c, Glitch g, float cx, float cy, float s, long now, float fade) {
        float k = 1f;
        if (!g.alive) { k = 1 - GameKit.ease((now - g.deadAt) / 500f); if (k <= 0) return; }
        boolean flash = now - g.hitAt < 140;
        float slam = (now - g.slamAt) / 450f, rear = g.slamCool < .5f ? 1 - g.slamCool / .5f : 0f;
        float lift = rear * s * .18f - (slam < 1 ? (1 - slam) * s * .05f : 0);
        int stone = flash ? 0xFFFFFFFF : night ? 0xFF7A6A58 : 0xFF8A7560, stone2 = GameKit.mix(stone, 0xFF000000, .25f), moss = 0xFF4FA35A;
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
        int eye = GameKit.mix(0xFFFF9A3D, 0xFFFFF1A8, rear);
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
        int body = first ? 0xFF35D0E0 : 0xFFB15CFF;
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
    Glitch firstTwin() { for (Glitch o : glitches) if (o.boss && o.bossKind == 2) return o; return null; }

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
        float y = topInset + 40 * d;
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
        text.setTextAlign(android.graphics.Paint.Align.CENTER);
    }

    @Override void drawPlay(Canvas c, float dt, long now) {
        int sway = c.save();
        if (dizzy() && motion) { float k = Math.min(1f, (dizzyUntil - now) / 800f) * Math.min(1f, (now - fartAt) / 500f); c.rotate((float) Math.sin(now / 420.0) * 1.6f * k, board.centerX(), board.centerY()); c.translate((float) Math.sin(now / 300.0) * 3 * d * k, 0); }
        drawStatic(c);
        drawGas(c, now, 1f);
        drawStains(c, now);
        drawPortal(c, now, 1f);
        drawShrine(c, now);
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
        for (Flame f : flames) drawFlame(c, f, now, 1f);
        drawBalls(c, now);
        drawGems(c, now);
        for (Glitch g : glitches) drawGlitch(c, g, now, 1f);
        if (breathing()) drawBreath(c, now);
        drawSouls(c, now);
        drawHero(c, now, 1f, 1f);
        if (now - smiteAt < 420) {
            float k = 1 - (now - smiteAt) / 420f, sx = px(smiteX), sy = py(smiteY);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFF6D0, k * .8f)); c.drawRect(sx - cell * .22f * k, board.top, sx + cell * .22f * k, sy, fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, k)); c.drawRect(sx - cell * .07f * k, board.top, sx + cell * .07f * k, sy, fill);
            fill.setColor(GameKit.alpha(0xFFFFE9A8, k * .6f)); c.drawCircle(sx, sy, cell * (.5f + .5f * (1 - k)), fill);
        }
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
            c.drawText(I18n.t("Combo ×{0}!").replace("{0}", String.valueOf(comboShown)), getWidth() / 2f, board.top + 46 * d, text);
        }
        int bossHp = 0, bossMax = 0; Glitch head = null;
        for (Glitch g : glitches) if (g.boss) { bossMax += g.maxHp; if (g.alive) { bossHp += Math.max(0, g.hp); if (head == null) head = g; } }
        for (Glitch g = head; g != null; g = null) {
            float bw = board.width() * .6f, bx = board.centerX() - bw / 2, by = board.top + 10 * d;
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(12)); text.setColor(0xFFFFFFFF);
            box.set(bx - 6 * d, by - 4 * d, bx + bw + 6 * d, by + 26 * d); fill.setShader(null); fill.setColor(0xAA14161C); c.drawRoundRect(box, 10 * d, 10 * d, fill);
            c.drawText(I18n.t(BOSS_NAMES[g.bossKind]), board.centerX(), by + 9 * d, text);
            box.set(bx, by + 14 * d, bx + bw, by + 20 * d); fill.setColor(0x44FFFFFF); c.drawRoundRect(box, 3 * d, 3 * d, fill);
            box.set(bx, by + 14 * d, bx + bw * bossHp / (float) Math.max(1, bossMax), by + 20 * d); fill.setColor(g.bossKind == 1 ? 0xFFFF9A3D : g.bossKind == 2 ? 0xFF35D0E0 : 0xFFB15CFF); c.drawRoundRect(box, 3 * d, 3 * d, fill);
        }
        float bt = now - portalBannerAt;
        if (portalBannerAt > 0 && bt < 1600) {
            float k = GameKit.spring(bt / 450f), f = bt > 1300 ? 1 - GameKit.ease((bt - 1300) / 300f) : 1f;
            banner(c, I18n.t("Portal open!"), 0xF21846B8, k, f);
            if (!portalSound) { portalSound = true; PixelStackSound.play(PixelStackSound.PERFECT, .55f, 1f); burst(px(portalX + .5f), py(portalY + .5f), GameKit.LIGHT, 22, 240, 5); }
        }
        int n = (megaLeft > 0 ? 1 : 0) + (now < bigUntil ? 1 : 0) + (now < poisonUntil ? 1 : 0) + (now < breathUntil ? 1 : 0) + (now < shieldUntil ? 1 : 0) + (now < freezeUntil ? 1 : 0) + (now < doubleUntil ? 1 : 0) + (now < laserUntil ? 1 : 0) + (now < dizzyUntil ? 1 : 0);
        float x = getWidth() / 2f - (n - 1) * 15 * d, y = board.bottom + (tray.isEmpty() ? 24 : 70) * d;
        if (now < laserUntil) { chip(c, x, y, P_LASER, (laserUntil - now) / 12000f); x += 30 * d; }
        if (now < dizzyUntil) { chip(c, x, y, P_FART, (dizzyUntil - now) / 7000f); x += 30 * d; }
        if (megaLeft > 0) { chip(c, x, y, P_MEGA, megaLeft / 3f); x += 30 * d; }
        if (now < poisonUntil) { chip(c, x, y, P_POISON, (poisonUntil - now) / 15000f); x += 30 * d; }
        if (now < bigUntil) { chip(c, x, y, P_FIRE, (bigUntil - now) / 20000f); x += 30 * d; }
        if (now < breathUntil) { chip(c, x, y, P_BREATH, (breathUntil - now) / 10000f); x += 30 * d; }
        if (now < shieldUntil) { chip(c, x, y, P_SHIELD, (shieldUntil - now) / 8000f); x += 30 * d; }
        if (now < freezeUntil) { chip(c, x, y, P_FREEZE, (freezeUntil - now) / 6000f); x += 30 * d; }
        if (now < doubleUntil) { chip(c, x, y, P_DOUBLE, (doubleUntil - now) / 12000f); }
    }
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
        box.set(getWidth() / 2f - tw / 2, board.centerY() - 34 * d * k, getWidth() / 2f + tw / 2, board.centerY() + 22 * d * k);
        fill.setShader(null); fill.setColor(GameKit.alpha(colour, f)); c.drawRoundRect(box, 20 * d, 20 * d, fill);
        text.setColor(GameKit.alpha(0xFFFFFFFF, f)); c.drawText(words, getWidth() / 2f, board.centerY() + 6 * d, text);
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
                confetti = true; score += 1000; pop("+1000", getWidth() / 2f, board.centerY() - 50 * d, night ? 0xFFFFFFFF : 0xFF14161C, 18);
                lives++; pop(I18n.t("Extra life"), getWidth() / 2f, board.centerY() + 70 * d, night ? GameKit.LIGHT : GameKit.BLUE, 18);
                for (int i = 0; i < 40; i++) { GameKit.Bit b = spark(getWidth() / 2f, board.centerY(), GameKit.RAINBOW[i % GameKit.RAINBOW.length], 420, 6); b.gravity = .8f; b.decay = .7f; }
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
