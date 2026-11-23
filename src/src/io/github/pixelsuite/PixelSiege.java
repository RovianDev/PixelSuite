package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;

import java.util.ArrayList;

final class PixelSiege extends GameKit.Game {
    static final int N = 13, U = 26;

    final byte[][] wall = new byte[U][U];
    final boolean[][] water = new boolean[N][N], bush = new boolean[N][N];
    final boolean[][] fortress = new boolean[U][U];
    final RectF board = new RectF();
    float unit, cellPx;
    int stage = 1, lives, reserve, killed, nextSpawn, stageTanks = 20; long spawnTimer;
    boolean baseDown, fromTitle;
    long freezeUntil, freezeAt = -10000, fortressUntil, baseHitAt = -1;

    static final class Tank {
        float x, y, moved, speed; int dir, type, hp, maxHp; boolean player, bonus, alive = true;
        long bornAt, shieldUntil, flashAt = -10000, stunUntil; float cool, turn, scorch; int shots, level, tier, bumps, waved; boolean again;
        long shockUntil, burnUntil, formAt = -10000, zapAt = -10000; int form; float formCool, burnCool, orbCool, trailCool; boolean freed;
        long firedAt = -10000, movedAt = -10000; float phase;
        float wakeCool;
        float mineCool = 2f, flameCool; long flameUntil, aimAt = -1; boolean summoned;
    }
    static final class Shot { float x, y, speed; int dir; Tank from; boolean steel, dead, rocket, laser, mini; float trail, sinceTurn; Tank last; int dmg, lvl;
        boolean charged, top; int soul, id, ink; float laid; }
    static final class Prize { float x, y; int kind; long at; boolean tapped; }

    final ArrayList<Tank> tanks = new ArrayList<Tank>();
    final ArrayList<Shot> shots = new ArrayList<Shot>();
    final ArrayList<Prize> prizes = new ArrayList<Prize>();
    Tank me; long meDeadAt = -1;
    int[] queue = new int[20];
    boolean fireHeld; float fireCool; long lastShotAt;
    int kills, rank; long rocketUntil, doubleUntil, laserUntil, droneUntil; long rankAt = -10000;
    int baseHp = 5, baseMax = 5; long baseHurtAt = -10000, sirenAt = -100000;
    int baseMaxFor() { return 5 + 2 * Math.min(rank, 9); }
    float droneAngle, droneCool; Tank drone;
    static final int[] RANKS = {8, 20, 36, 56, 80, 108, 140, 176, 216};
    int starXp; long overBrokenAt = -100000;
    int xp() { return kills + starXp; }
    boolean overshield(long now) { return rank >= 5 && now - overBrokenAt > (rank >= 9 ? 6000 : 12000); }
    int wardStyle() { return soul == THUNDER && soulLv > 0 ? WARD_STORM : soul == INFERNO && soulLv > 0 ? WARD_BLAZE : WARD_PLAIN; }
    int power() { return Math.min(4, rank / 2); }
    void addScore(int n) { score += SystemClock.uptimeMillis() < doubleUntil ? n * 2 : n; }

    static final int NONE = 0, THUNDER = 1, INFERNO = 2, TOP = 10;
    int soul, soulLv, meals, soulFrom, soulUnits; long soulDropAt = -10000; long soulAt = -10000, absorbAt = -10000, eatAt = -10000;
    boolean capReady; long capAt = -100000, frenzyUntil, chargeReadyAt;
    float zapCool = 5f, novaCool = 6f, orb, ramCool; long zapAt = -10000, novaAt = -10000; float zapX0, zapY0, zapX1, zapY1, laserAcc;
    float lastX = 13, lastY = 24; int shotIds;
    int thunder() { return soul == THUNDER ? soulLv : 0; }
    int inferno() { return soul == INFERNO ? soulLv : 0; }

    static int soulStep(int lv) { return PixelBlast.soulStep(lv); }
    static int soulTotal(int lv) { return PixelBlast.soulTotal(lv); }
    static int soulLevelOf(int units) { return PixelBlast.soulLevelOf(units); }
    int soulHave() { return soulUnits - soulTotal(soulLv); }
    int soulColour() { return soul == THUNDER ? 0xFF4FD8FF : soul == INFERNO ? 0xFFFF5A1F : 0xFF9AA3B5; }
    String soulName(int kind) { return I18n.t(kind == THUNDER ? "Thunder" : "Inferno"); }
    long chargeMs() { return Math.max(1700, 4000 - 250L * (soulLv - 1)); }
    boolean mine(Shot s) { return s.from != null && s.from.player && s.from.type == -1; }

    void soulStepUp() {
        long now = SystemClock.uptimeMillis(); soulAt = now;
        float x = px(lastX + 1), y = py(lastY + 1);
        pop(soulHave() + "/" + soulStep(soulLv), x, py(lastY - .6f), soulColour(), 18);
        burst(x, y, soulColour(), 14, 220, 4); rings.add(new float[]{x, y, now, 1.2f});
        PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.1f); GameKit.thump(a, .6f);
    }

    void soulDown() {
        if (soul == NONE || soulUnits <= 1) return;
        long now = SystemClock.uptimeMillis();
        soulUnits--;
        int lv = soulLevelOf(soulUnits);
        if (lv < soulLv) {
            soulLv = lv; soulDropAt = now; if (soul == THUNDER) capReady = false;
            postDelayed(() -> { pop("\u25BC " + soulName(soul) + " " + soulLv, board.centerX(), board.top + board.height() * .3f, soulColour(), 18); PixelStackSound.play(PixelStackSound.SOFT, .4f, .62f); }, 700);
        }
    }
    void soulSync() { if (soul != NONE && soulLv > 0 && soulUnits < soulTotal(soulLv)) soulUnits = soulTotal(soulLv); }
    void soulUp() {
        long now = SystemClock.uptimeMillis();
        soulLv = Math.min(TOP, soulLv + 1); soulAt = now;
        levelUp(soul == THUNDER ? LV_LIGHT : LV_DARK, soulName(soul) + " " + soulLv, I18n.t("Level up!"));
        float x = px(lastX + 1), y = py(lastY + 1);
        burst(x, y, soulColour(), 20 + soulLv, 260, 5); burst(x, y, soul == THUNDER ? 0xFFFFFFFF : 0xFF3A0A10, 12, 200, 4);
        rings.add(new float[]{x, y, now, 1.8f});
        shake(soulLv >= TOP ? 12 : 7); GameKit.thump(a, .8f);
        if (soul == THUNDER) capReady = true;
    }

    static final class Core { float x, y; int kind; long at, usedAt = -1; boolean used, ready = true; }
    final ArrayList<Core> cores = new ArrayList<Core>();
    long coresAt = -1, coreTakenAt = -1; static final long CORE_MS = 12000;
    int ask = -1, askLast = -1; long askAt, askShutAt = -10000; boolean askTapped;
    final RectF yesAt = new RectF(), noAt = new RectF(), askAll = new RectF();
    void dropCores(long now) {
        coresAt = now; cores.clear(); ask = -1; askLast = -1;
        ArrayList<int[]> open = new ArrayList<int[]>();
        for (int y = 0; y <= 20; y += 2) for (int x = 0; x <= 24; x += 2) {
            if (!open(x, y)) continue;
            boolean busy = false; for (Tank t : tanks) if (t.alive && Math.abs(t.x - x) < 2.2f && Math.abs(t.y - y) < 2.2f) busy = true;
            if (!busy) open.add(new int[]{x, y});
        }
        java.util.Collections.shuffle(open, rnd);
        int[] p0 = null, q0 = null; float best = -1; int n = Math.min(open.size(), 60);
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            int[] p = open.get(i), q = open.get(j);
            float apart = (float) Math.hypot(p[0] - q[0], p[1] - q[1]), dm = (float) Math.min(Math.hypot(p[0] - lastX, p[1] - lastY), Math.hypot(q[0] - lastX, q[1] - lastY));
            float score = Math.min(apart, 14f) + .6f * Math.min(dm, 6f);
            if (score > best) { best = score; p0 = p; q0 = q; }
        }
        if (p0 == null) { coreTakenAt = now - 2000; return; }
        boolean flip = rnd.nextBoolean();
        Core c0 = new Core(); c0.x = p0[0]; c0.y = p0[1]; c0.kind = flip ? THUNDER : INFERNO; c0.at = now; cores.add(c0);
        Core c1 = new Core(); c1.x = q0[0]; c1.y = q0[1]; c1.kind = flip ? INFERNO : THUNDER; c1.at = now + 200; cores.add(c1);
        PixelStackSound.play(PixelStackSound.RISE, .4f, 1.2f); postDelayed(() -> { PixelStackSound.play(PixelStackSound.THUD, .5f, .9f); GameKit.thump(a, .6f); }, 600);
    }
    void stepCores(long now) {
        if (state != PLAY || me == null || !me.alive || coreTakenAt > 0) { if (ask >= 0) shutAsk(now); return; }
        for (int i = 0; i < cores.size(); i++) {
            Core k = cores.get(i); if (k.used || now - k.at < 600) continue;
            float dx = me.x - k.x, dy = me.y - k.y;
            boolean on = Math.abs(dx) < 1.3f && Math.abs(dy) < 1.3f;
            if (on && k.ready && ask != i) { if (ask >= 0) shutAsk(now); ask = i; askAt = now; askTapped = false; k.ready = false; PixelStackSound.play(PixelStackSound.SOFT, .4f, k.kind == THUNDER ? 1.4f : .8f); GameKit.tick(a, .4f); }
            if (ask == i && !askTapped && Math.hypot(dx, dy) > 2.6f) shutAsk(now);
            if (!on && ask != i) k.ready = true;
        }
    }
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
    void answer(boolean yes) {
        long now = SystemClock.uptimeMillis();
        if (ask < 0 || ask >= cores.size()) { ask = -1; return; }
        Core k = cores.get(ask);
        shutAsk(now);
        if (!yes) { PixelStackSound.play(PixelStackSound.SOFT, .3f, .9f); GameKit.tick(a, .3f); return; }
        for (Core o : cores) if (!o.used) { o.used = true; o.usedAt = now; }
        coreTakenAt = now;
        soulFrom = soul; soulAt = now;
        soulSync();
        if (soul == k.kind) { if (soulLv < TOP) { soulUnits++; if (soulLevelOf(soulUnits) > soulLv) soulUp(); else soulStepUp(); } else { addScore(2000); pop("+2000", px(k.x + 1), py(k.y), soulColour(), 20); } }
        else { int to = soul == NONE ? 1 : Math.max(1, soulLv / 2); soul = k.kind; soulUnits = soulTotal(to); soulBits = 0; soulShown = 0; soulLv = to - 1; soulUp(); }
        chargeReadyAt = now;
        float x = px(k.x + 1), y = py(k.y + 1);
        burst(x, y, k.kind == THUNDER ? 0xFFBFF4FF : 0xFFFF5A1F, 26, 300, 5); rings.add(new float[]{x, y, now, 2.4f});
        if (k.kind == THUNDER) { PixelStackSound.play(PixelStackSound.TING, .5f, 1.6f); PixelStackSound.play(PixelStackSound.RISE, .4f, 1.5f); } else { PixelStackSound.play(PixelStackSound.FIRE, .5f, .7f); PixelStackSound.play(PixelStackSound.BOOM, .4f, .6f); }
        GameKit.rumble(a);
    }
    void drawCores(Canvas c, long now) {
        for (int i = 0; i < cores.size(); i++) {
            Core k = cores.get(i); if (now < k.at) continue;
            float drop = GameKit.clamp((now - k.at) / 600f), fall = 1 - GameKit.easeIn(drop);
            float gone = k.used ? 1 - GameKit.ease((now - k.usedAt) / 700f) : 1f;
            if (gone <= 0) continue;
            float cx = px(k.x + 1), cy = py(k.y + 1), s = unit * 1.6f, yoff = -fall * unit * 14, land = drop >= 1 ? 1 - GameKit.clamp((now - k.at - 600) / 300f) : 0f;
            boolean asking = ask == i; float pulse = motion ? (float) (.5 + .5 * Math.sin(now / (asking ? 140.0 : 320.0))) : .5f;
            boolean th = k.kind == THUNDER; int glow = th ? 0xFF4FD8FF : 0xFFFF5A1F;
            fill.setShader(null);
            fill.setColor(GameKit.alpha(0x40000000, gone * drop)); c.drawOval(GameKit.rf(cx - s * .55f * drop, cy + s * .35f, cx + s * .55f * drop, cy + s * .55f), fill);
            if (land > 0) { stroke.setStrokeWidth(3 * d * land); stroke.setColor(GameKit.alpha(night ? 0xAAFFFFFF : 0x66000000, land)); c.drawOval(GameKit.rf(cx - s * (1.4f - .6f * land), cy + s * .2f, cx + s * (1.4f - .6f * land), cy + s * .6f), stroke); }
            float y = cy + yoff;
            fill.setColor(GameKit.alpha(glow, gone * (.18f + .12f * pulse + (asking ? .12f : 0)))); c.drawCircle(cx, y, s * (.95f + .08f * pulse), fill);
            box.set(cx - s * .5f, y - s * .5f, cx + s * .5f, y + s * .5f);
            fill.setColor(GameKit.alpha(th ? 0xFF2A3346 : 0xFF2A1210, gone)); c.drawRoundRect(box, s * .16f, s * .16f, fill);
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(glow, gone * (.7f + .3f * pulse))); c.drawRoundRect(box, s * .16f, s * .16f, stroke);
            fill.setColor(GameKit.alpha(th ? 0xFF8A97B0 : 0xFF6A3A2A, gone)); for (int q = 0; q < 4; q++) c.drawCircle(cx + (q % 2 == 0 ? -1 : 1) * s * .36f, y + (q < 2 ? -1 : 1) * s * .36f, s * .05f, fill);
            fill.setColor(GameKit.alpha(glow, gone)); c.drawCircle(cx, y, s * (.3f + .03f * pulse), fill);
            tankSign(c, cx, y, s * .26f, k.kind, th ? 4 : 4, now, gone, true);
            if (motion && !k.used && rnd.nextFloat() < .3f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * s, y, th ? 0xFFBFF4FF : (rnd.nextBoolean() ? 0xFFFF7A3D : 0x99505050), th ? 70 : 35, th ? 2.2f : 3.5f); b.round = true; b.gravity = th ? 0 : -.35f; b.decay = th ? 3.5f : 1.2f; }
        }
    }
    void drawAsk(Canvas c, long now) {
        int i = ask >= 0 ? ask : askLast;
        if (i < 0 || i >= cores.size() || (state != PLAY && ask < 0)) { askLast = -1; yesAt.setEmpty(); noAt.setEmpty(); askAll.setEmpty(); return; }
        float k = ask >= 0 ? (motion ? GameKit.spring((now - askAt) / 320f) : 1f) : 1 - GameKit.ease((now - askShutAt) / 180f);
        if (ask < 0 && k <= 0) { askLast = -1; askAll.setEmpty(); return; }
        int kind = cores.get(i).kind;
        int to = soul == kind ? Math.min(TOP, soulLv + 1) : soul == NONE ? 1 : Math.max(1, soulLv / 2);
        String note = I18n.t("Level {0}").replace("{0}", String.valueOf(to));
        if (soul != NONE && soul != kind) note = I18n.t(soul == THUNDER ? "Your thunder core burns out" : "Your inferno core burns out") + " · " + note;
        askBubble(c, px(lastX + 1), py(lastY + 1), unit * 1.1f, I18n.t(kind == THUNDER ? "Fit the thunder core?" : "Fit the inferno core?"), note, kind == THUNDER ? 0xFF1E88E5 : 0xFFE5532B, kind == INFERNO, k, yesAt, noAt, askAll);
        if (ask < 0) { yesAt.setEmpty(); noAt.setEmpty(); }
    }

    static final String[] BOSS_NAMES = {"Iron Crown", "Storm Turret", "Laser Walker", "Mortar King", "Hive Mother", "Steel Serpent", "Twin Fortress", "Thunder Colossus", "Mine Layer", "Crimson Baron",
        "Shield Warden", "Inferno Engine", "Night Spider", "Titan", "Phantom Ship", "Siege Breaker", "Overlord", "Dreadnought", "Void Eye", "Emperor"};
    static final int B_SPREAD = 1, B_CROSS = 2, B_BEAM = 4, B_AIM = 8, B_MORTAR = 16, B_SUMMON = 32, B_MINES = 64, B_SHIELD = 128, B_WALLS = 256, B_TELE = 512, B_RAGE = 1024, B_SIDE = 2048;

    static final int[] BOSS_BODY = {0, 1, 2, 3, 4, 5, 0, 1, 3, 6, 0, 3, 2, 0, 5, 3, 4, 6, 1, 0};
    static final int[] BOSS_MOVES = {B_SPREAD | B_SIDE, B_CROSS, B_AIM | B_BEAM, B_MORTAR | B_SPREAD, B_SUMMON | B_MINES, B_CROSS | B_SIDE | B_RAGE, B_SPREAD | B_BEAM | B_AIM, B_CROSS | B_MORTAR | B_RAGE, B_MINES | B_AIM | B_SPREAD, B_SPREAD | B_MORTAR | B_TELE,
        B_SHIELD | B_SUMMON | B_SPREAD, B_BEAM | B_MORTAR, B_MINES | B_BEAM | B_AIM, B_SPREAD | B_CROSS | B_MORTAR | B_RAGE, B_TELE | B_AIM | B_SPREAD | B_SIDE, B_WALLS | B_MORTAR | B_CROSS, B_SUMMON | B_SHIELD | B_BEAM, B_SPREAD | B_CROSS | B_MINES | B_RAGE, B_TELE | B_MORTAR | B_CROSS | B_SHIELD,
        B_SPREAD | B_BEAM | B_MORTAR | B_SUMMON | B_SHIELD | B_RAGE | B_AIM};
    static final int[] BOSS_INK = {0xFF3B4558, 0xFF1A2F6E, 0xFF3A3F4D, 0xFF5D4037, 0xFF33691E, 0xFF546E7A, 0xFF4E342E, 0xFF283593, 0xFF37474F, 0xFFB71C1C, 0xFF00838F, 0xFFBF360C, 0xFF212121, 0xFF424242, 0xFF4A148C, 0xFF6D4C41, 0xFF1B5E20, 0xFF263238, 0xFF311B92, 0xFF8D6E00};
    static final int[] BOSS_GLOW = {0xFFFF3B3B, 0xFF8FE3FF, 0xFFFF2D55, 0xFFFF9A3D, 0xFFB2FF59, 0xFF80DEEA, 0xFFFFD23F, 0xFF7FE9FF, 0xFFFFEB3B, 0xFFFF5252, 0xFF18FFFF, 0xFFFF6E40, 0xFFFF1744, 0xFFFFAB40, 0xFFE040FB, 0xFFFFCC80, 0xFF69F0AE, 0xFFFF3D00, 0xFFB388FF, 0xFFFFE57F};
    int bossSpec() { return Math.max(0, stage / 5 - 1) % 20; }
    boolean bossHas(Boss b, int move) { return (BOSS_MOVES[b.kind % 20] & move) != 0; }

    static final class Mortar { float x, y, fx, fy, r; long at, hitAt; boolean boss, done; }
    final ArrayList<Mortar> mortars = new ArrayList<Mortar>();
    void launchMortar(float fx, float fy, float tx, float ty, boolean boss) {
        Mortar m = new Mortar(); long now = SystemClock.uptimeMillis();
        m.fx = fx; m.fy = fy; m.x = Math.max(.5f, Math.min(U - .5f, tx)); m.y = Math.max(.5f, Math.min(U - .5f, ty)); m.r = boss ? 1.8f : 1.5f; m.at = now; m.hitAt = now + (boss ? 1150 : 1300); m.boss = boss;
        mortars.add(m); PixelStackSound.play(PixelStackSound.THUD, .3f, 1.4f);
    }
    void stepMortars(long now) {
        for (int i = mortars.size() - 1; i >= 0; i--) {
            Mortar m = mortars.get(i);
            if (m.done) { if (now - m.hitAt > 600) mortars.remove(i); continue; }
            if (now < m.hitAt) continue;
            m.done = true;
            float sx = px(m.x), sy = py(m.y);
            burst(sx, sy, 0xFFFF7A3D, 20, 260, 6); burst(sx, sy, 0xFF3A3A3A, 10, 160, 6); rings.add(new float[]{sx, sy, now, m.r + .4f});
            shake(7); PixelStackSound.play(PixelStackSound.BOOM, .45f, .85f);
            for (int uy = (int) (m.y - m.r - 1); uy <= (int) (m.y + m.r + 1); uy++) for (int ux = (int) (m.x - m.r - 1); ux <= (int) (m.x + m.r + 1); ux++) {
                if (ux < 0 || uy < 0 || ux >= U || uy >= U || wall[uy][ux] != 1 || fortress[uy][ux] || Math.hypot(ux + .5f - m.x, uy + .5f - m.y) > m.r) continue;
                wall[uy][ux] = 0; wallsDirty = true;
            }
            if (me != null && me.alive && ready(me) && Math.hypot(me.x + 1 - m.x, me.y + 1 - m.y) < m.r + .4f) hitMe(now);
            if (Math.hypot(13 - m.x, 25 - m.y) < m.r + .6f) baseHit();
        }
    }
    void drawMortars(Canvas c, long now) {
        fill.setShader(null);
        for (Mortar m : mortars) {
            if (m.done) continue;
            float k = GameKit.clamp((now - m.at) / (float) (m.hitAt - m.at)), x = px(m.x), y = py(m.y), r = unit * m.r, blink = (now / 90) % 2 == 0 ? 1f : .6f;
            fill.setColor(GameKit.alpha(0xFFFF2D2D, .1f + .15f * k)); c.drawCircle(x, y, r, fill);
            stroke.setStrokeWidth(Math.max(1.5f, unit * .1f)); stroke.setColor(GameKit.alpha(0xFFFF3B3B, (.5f + .4f * k) * blink)); c.drawCircle(x, y, r * (1.25f - .25f * k), stroke);
            c.drawLine(x - r * .4f, y, x + r * .4f, y, stroke); c.drawLine(x, y - r * .4f, x, y + r * .4f, stroke);
            float sx = px(m.fx + (m.x - m.fx) * k), sy = py(m.fy + (m.y - m.fy) * k) - (float) Math.sin(Math.PI * k) * unit * 6f;
            fill.setColor(GameKit.alpha(0x44000000, .6f * k)); c.drawOval(GameKit.rf(x - unit * .35f * k, y - unit * .15f * k, x + unit * .35f * k, y + unit * .15f * k), fill);
            fill.setColor(GameKit.alpha(0xFF2A2A2A, 1f)); c.drawCircle(sx, sy, unit * .3f, fill);
            fill.setColor(GameKit.alpha(0xFFFF9A3D, 1f)); c.drawCircle(sx, sy - unit * .12f, unit * .12f, fill);
        }
    }

    static final class Mine { float x, y; long at; boolean boss; }
    final ArrayList<Mine> mines = new ArrayList<Mine>();
    void dropMine(float x, float y, boolean boss) {
        if (x < .6f || y < .6f || x > U - .6f || y > U - .6f || mines.size() >= 14) return;
        Mine m = new Mine(); m.x = x; m.y = y; m.at = SystemClock.uptimeMillis(); m.boss = boss; mines.add(m);
        PixelStackSound.play(PixelStackSound.CHIP, .25f, .7f);
    }
    void stepMines(long now) {
        for (int i = mines.size() - 1; i >= 0; i--) {
            Mine m = mines.get(i);
            if (now - m.at > 16000) { mines.remove(i); continue; }
            if (now - m.at < 700 || me == null || !me.alive || !ready(me)) continue;
            if (Math.hypot(me.x + 1 - m.x, me.y + 1 - m.y) < 1.15f) { mines.remove(i); mineBoom(m); hitMe(now); }
        }
    }
    void mineBoom(Mine m) { float x = px(m.x), y = py(m.y); burst(x, y, 0xFFFF7A3D, 16, 220, 5); burst(x, y, 0xFFFFE08A, 8, 160, 4); rings.add(new float[]{x, y, SystemClock.uptimeMillis(), 1.1f}); shake(5); PixelStackSound.play(PixelStackSound.BOOM, .4f, 1.1f); }
    void shootMines(Shot s) {
        if (mines.isEmpty() || !s.from.player) return;
        for (int i = mines.size() - 1; i >= 0; i--) { Mine m = mines.get(i); if (Math.abs(m.x - s.x) < .6f && Math.abs(m.y - s.y) < .6f) { mines.remove(i); mineBoom(m); addScore(50); if (!s.laser) kill(s); return; } }
    }
    void drawMines(Canvas c, long now) {
        fill.setShader(null);
        for (Mine m : mines) {
            float x = px(m.x), y = py(m.y), r = unit * .42f, age = now - m.at, in = GameKit.spring(age / 300f), fadeOut = age > 14500 ? 1 - (age - 14500) / 1500f : 1f;
            boolean near = me != null && me.alive && Math.hypot(me.x + 1 - m.x, me.y + 1 - m.y) < 4f;
            fill.setColor(GameKit.alpha(0x44000000, fadeOut)); c.drawCircle(x + unit * .05f, y + unit * .07f, r * in, fill);
            fill.setColor(GameKit.alpha(m.boss ? 0xFF3A1010 : 0xFF2E3440, fadeOut)); c.drawCircle(x, y, r * in, fill);
            fill.setColor(GameKit.alpha(0xFF5A6378, fadeOut)); for (int k = 0; k < 6; k++) { double ang = k * Math.PI / 3; c.drawCircle(x + (float) Math.cos(ang) * r * in, y + (float) Math.sin(ang) * r * in, r * .18f, fill); }
            boolean on = age > 700 && (now / (near ? 120 : 400)) % 2 == 0;
            fill.setColor(GameKit.alpha(on ? 0xFFFF2D2D : 0xFF5A1010, fadeOut)); c.drawCircle(x, y, r * .3f, fill);
            if (on) { fill.setColor(GameKit.alpha(0xFFFF2D2D, .25f * fadeOut)); c.drawCircle(x, y, r * .9f, fill); }
        }
    }

    void hitMe(long now) {
        Tank t = me; if (t == null || !t.alive) return;
        if (now < t.shieldUntil) { burst(px(t.x + 1), py(t.y + 1), GameKit.LIGHT, 6, 100, 3); PixelStackSound.play(PixelStackSound.TING, .3f, 1.4f); return; }
        if (thunder() >= 1 && capReady) { capReady = false; capAt = now; t.shieldUntil = now + 1500; burst(px(t.x + 1), py(t.y + 1), 0xFF7FE9FF, 22, 260, 5); rings.add(new float[]{px(t.x + 1), py(t.y + 1), now, 1.5f}); PixelStackSound.play(PixelStackSound.TING, .6f, 1.2f); PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.1f); GameKit.thump(a, .8f); return; }
        if (overshield(now)) { overBrokenAt = now; burst(px(t.x + 1), py(t.y + 1), wardColour(wardStyle()), 20, 220, 4); rings.add(new float[]{px(t.x + 1), py(t.y + 1), now, 1.3f}); PixelStackSound.play(PixelStackSound.TING, .5f, .8f); GameKit.thump(a, .6f); return; }
        hurtMe(1, now);
    }

    boolean foeSpecial(Tank t, float dt, long now) {
        boolean live = me != null && me.alive && ready(me);
        if (t.type == 9) { t.mineCool -= dt; if (t.mineCool <= 0) { t.mineCool = 2.8f + rnd.nextFloat() * 1.6f; dropMine(t.x + 1 - DX[t.dir] * 1.3f, t.y + 1 - DY[t.dir] * 1.3f, false); } }
        if (t.type == 11 && live) {
            float dx = me.x - t.x, dy = me.y - t.y, ahead = dx * DX[t.dir] + dy * DY[t.dir], side = Math.abs(dx * DY[t.dir] + dy * DX[t.dir]);
            if (ahead > 0 && ahead < 4.6f && side < 1.4f) { if (t.flameUntil < now) PixelStackSound.play(PixelStackSound.FIRE, .35f, 1f); t.flameUntil = now + 250; t.flameCool -= dt; if (t.flameCool <= 0) { t.flameCool = .7f; hitMe(now); } }
            else t.flameCool = Math.min(t.flameCool, .25f);
        }
        if (t.type == 12) {
            if (t.aimAt > 0) {
                if (now - t.aimAt > 850) { t.aimAt = -1; t.cool = 2.4f + rnd.nextFloat(); Shot s = new Shot(); s.from = t; s.dir = t.dir; s.x = t.x + 1 + DX[t.dir]; s.y = t.y + 1 + DY[t.dir]; s.speed = 30f; s.steel = true; shots.add(s); t.shots++; t.firedAt = now; muzzle(px(s.x), py(s.y), 0xFFFF5252); PixelStackSound.play(PixelStackSound.SHOT, .3f, 1.5f); }
                return true;
            }
            if (live && t.cool <= 0 && t.shots == 0) {
                float dx = me.x - t.x, dy = me.y - t.y; int aim = -1;
                if (Math.abs(dx) < .7f && Math.abs(dy) < 18) aim = dy > 0 ? 2 : 0; else if (Math.abs(dy) < .7f && Math.abs(dx) < 18) aim = dx > 0 ? 1 : 3;
                if (aim >= 0) { t.dir = aim; t.aimAt = now; PixelStackSound.play(PixelStackSound.TING, .25f, 2f); return true; }
            }
        }
        return false;
    }

    void foeFire(Tank t, long now) {
        if (t.type == 8) { if (me != null && me.alive) launchMortar(t.x + 1, t.y + 1, me.x + 1 + (rnd.nextFloat() - .5f) * 1.2f, me.y + 1 + (rnd.nextFloat() - .5f) * 1.2f, false); t.firedAt = now; t.cool = 3.4f + rnd.nextFloat() * 1.3f; return; }
        if (t.type == 11 || t.type == 12) { t.cool = .5f; return; }
        fire(t); t.cool = (.9f + rnd.nextFloat() * 1.4f) * (1 - .08f * t.tier);
    }

    void smash(Tank t) {
        int x0 = (int) Math.floor(t.x + .02f), x1 = (int) Math.floor(t.x + 2 - .02f), y0 = (int) Math.floor(t.y + .02f), y1 = (int) Math.floor(t.y + 2 - .02f);
        if (t.dir == 0) { y0 = y1 = y0 - 1; } else if (t.dir == 2) { y0 = y1 = y1 + 1; } else if (t.dir == 1) { x0 = x1 = x1 + 1; } else { x0 = x1 = x0 - 1; }
        boolean any = false;
        for (int uy = y0; uy <= y1; uy++) for (int ux = x0; ux <= x1; ux++) if (ux >= 0 && uy >= 0 && ux < U && uy < U && wall[uy][ux] == 1 && !fortress[uy][ux]) { wall[uy][ux] = 0; any = true; spark(px(ux + .5f), py(uy + .5f), brick, 120, unit / d * .3f); }
        if (any) { wallsDirty = true; t.bumps = 0; shake(3); crumble(); }
    }

    void summon(Boss b) {
        int alive = 0; for (Tank t : tanks) if (!t.player && t.alive) alive++;
        if (alive >= 6) return;
        for (int k = 0; k < 2; k++) {
            float sx = Math.max(0, Math.min(U - 2, Math.round(b.x - 1 + (k == 0 ? -3 : 3)))), sy = Math.round(b.y + BOSS_H + .4f);
            if (!free(sx, sy, null)) continue;
            Tank t = new Tank(); t.type = rnd.nextInt(4); t.x = sx; t.y = sy; t.dir = 2; t.summoned = true;
            t.tier = Math.min(5, stage / 12); t.speed = (t.type == 1 ? 7.2f : t.type == 3 ? 3.4f : 3.8f) * (1 + .06f * t.tier); t.hp = t.maxHp = (t.type == 3 ? 4 : 1) + t.tier;
            t.bornAt = SystemClock.uptimeMillis(); t.cool = 1f + rnd.nextFloat(); t.turn = 1f; t.phase = rnd.nextFloat() * 6.28f;
            tanks.add(t);
        }
        PixelStackSound.play(PixelStackSound.RISE, .3f, 1.1f);
    }

    void dropWalls(Boss b) {
        int y = 9 + rnd.nextInt(4) * 2, len = 6 + rnd.nextInt(4), x0 = 1 + rnd.nextInt(Math.max(1, U - len - 2));
        for (int uy = y; uy <= y + 1; uy++) for (int ux = x0; ux < x0 + len; ux++) {
            if (ux < 0 || ux >= U || wall[uy][ux] != 0 || water[uy / 2][ux / 2] || !clearOfTanks(ux - .5f, uy - .5f, null)) continue;
            if (me != null && me.alive && ux >= me.x - .2f && ux <= me.x + 2.2f && uy >= me.y - .2f && uy <= me.y + 2.2f) continue;
            wall[uy][ux] = 1; if (rnd.nextFloat() < .4f) spark(px(ux + .5f), py(uy + .5f), brick, 80, unit / d * .3f);
        }
        wallsDirty = true; shake(6); PixelStackSound.play(PixelStackSound.THUD, .55f, .8f);
    }

    String[] genMap(int stage) {
        java.util.Random r = new java.util.Random(stage * 104729L + 17);
        char[][] g = new char[N][N]; for (char[] row : g) java.util.Arrays.fill(row, '.');
        int style = r.nextInt(6);
        for (int y = 1; y <= 10; y++) for (int x = 0; x <= 6; x++) {
            char ch = '.';
            switch (style) {
                case 0: if (r.nextFloat() < .34f) ch = pick(r); break;
                case 1: if (x % 2 == 1 && y % 2 == 1) ch = r.nextFloat() < .25f ? 'S' : '#'; else if (r.nextFloat() < .1f) ch = '*'; break;
                case 2: if ((y % 4 == 1 || x % 4 == 1) && r.nextFloat() < .7f) ch = r.nextFloat() < .15f ? 'S' : '#'; break;
                case 3: if (x % 3 == 1 && r.nextFloat() < .8f) ch = r.nextFloat() < .2f ? 'S' : '#'; else if (x % 3 == 2 && r.nextFloat() < .12f) ch = '~'; break;
                case 4: { float dd = (float) Math.hypot(x - 3.2, (y - 5.5) * .8); if (dd < 1.6f) ch = '~'; else if (dd < 2.4f) ch = r.nextFloat() < .6f ? '*' : '#'; else if (r.nextFloat() < .15f) ch = pick(r); break; }
                default: if (r.nextFloat() < .45f && (x + y) % 2 == 1) ch = r.nextFloat() < .12f ? 'S' : '#'; break;
            }
            g[y][x] = ch;
        }
        for (int k = r.nextInt(3); k > 0; k--) { int x = 1 + r.nextInt(5), y = 2 + r.nextInt(8); g[y][x] = 'B'; }
        if (r.nextBoolean()) for (int x = 0; x <= 6; x++) if (g[11][x] == '.' && r.nextFloat() < .3f) g[11][x] = '#';
        for (int y = 0; y < N; y++) for (int x = 0; x <= 6; x++) g[y][N - 1 - x] = g[y][x];
        String[] out = new String[N]; for (int y = 0; y < N; y++) out[y] = new String(g[y]);
        return out;
    }
    static char pick(java.util.Random r) { float q = r.nextFloat(); return q < .52f ? '#' : q < .67f ? 'S' : q < .79f ? '~' : '*'; }

    void drawNewKind(Canvas c, Tank t, float cx, float cy, float s, float wide, long now, float fade) {
        fill.setShader(null);
        switch (t.type) {
            case 8: { float k = motion ? 1 - GameKit.clamp((now - t.firedAt) / 300f) : 0f; fill.setColor(GameKit.alpha(0xFF3E2723, fade)); c.drawCircle(cx, cy - s * .02f, s * (.2f + .03f * k), fill); fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawCircle(cx, cy - s * .02f, s * .12f, fill); fill.setColor(GameKit.alpha(0xFF8D6E63, fade)); for (int q = 0; q < 3; q++) { double ang = q * Math.PI * 2 / 3 + .5; c.drawCircle(cx + (float) Math.cos(ang) * s * .24f, cy + (float) Math.sin(ang) * s * .24f, s * .04f, fill); } if (k > 0) { fill.setColor(GameKit.alpha(0xFFFF9A3D, fade * k)); c.drawCircle(cx, cy - s * .02f, s * .1f, fill); } break; }
            case 9: { box.set(cx - s * .2f, cy + s * .14f, cx + s * .2f, cy + s * .34f); fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawRect(box, fill); fill.setColor(GameKit.alpha(0xFF14161C, fade)); for (float x = box.left; x < box.right; x += s * .1f) c.drawRect(x, box.top, x + s * .05f, box.bottom, fill); fill.setColor(GameKit.alpha(0xFF2E3440, fade)); c.drawCircle(cx, cy + s * .24f, s * .07f, fill); fill.setColor(GameKit.alpha((now / 300) % 2 == 0 ? 0xFFFF2D2D : 0xFF5A1010, fade)); c.drawCircle(cx, cy + s * .24f, s * .025f, fill); break; }
            case 10: { float q = motion ? (float) (.5 + .5 * Math.sin(now / 180.0)) : .5f; stroke.setStrokeWidth(Math.max(2f, s * .06f)); stroke.setColor(GameKit.alpha(0xFF4FD8FF, fade * (.6f + .3f * q))); c.drawArc(GameKit.rf(cx - s * .52f, cy - s * .62f, cx + s * .52f, cy + s * .3f), 205, 130, false, stroke); fill.setColor(GameKit.alpha(0xFF4FD8FF, fade * .12f)); c.drawArc(GameKit.rf(cx - s * .52f, cy - s * .62f, cx + s * .52f, cy + s * .3f), 205, 130, true, fill); break; }
            case 11: { fill.setColor(GameKit.alpha(0xFFC62828, fade)); for (int side = -1; side <= 1; side += 2) { box.set(cx + side * s * .1f - s * .07f, cy + s * .1f, cx + side * s * .1f + s * .07f, cy + s * .34f); c.drawRoundRect(box, s * .07f, s * .07f, fill); } if (now < t.flameUntil) { for (int q = 0; q < 3; q++) { float L = s * (1.9f + .2f * (float) Math.sin(now / 45.0 + q)), Wd = s * (.45f - .1f * q); android.graphics.Path f = GameKit.path(); f.moveTo(cx - s * .06f, cy - s * .42f); f.quadTo(cx - Wd, cy - s * .42f - L * .55f, cx, cy - s * .42f - L * (1 - .12f * q)); f.quadTo(cx + Wd, cy - s * .42f - L * .55f, cx + s * .06f, cy - s * .42f); f.close(); fill.setColor(GameKit.alpha(q == 0 ? 0xFFE5532B : q == 1 ? 0xFFFF9A3D : 0xFFFFE27A, fade * .9f)); c.drawPath(f, fill); } } else { fill.setColor(GameKit.alpha(0xFF4FC3F7, fade * .8f)); c.drawCircle(cx, cy - s * .46f, s * .03f, fill); } break; }
            case 12: { fill.setColor(GameKit.alpha(0xFF14161C, fade)); box.set(cx - s * .05f, cy - s * .18f, cx + s * .05f, cy + s * .02f); c.drawRoundRect(box, s * .03f, s * .03f, fill); fill.setColor(GameKit.alpha(t.aimAt > 0 ? 0xFFFF2D2D : 0xFF80DEEA, fade)); c.drawCircle(cx, cy - s * .2f, s * .035f, fill); break; }
            case 13: { fill.setColor(GameKit.alpha(0xFFB0BEC5, fade)); for (int k = -2; k <= 2; k++) { android.graphics.Path sp = GameKit.path(); float bx = cx + k * s * .1f, by = cy - s * .36f; sp.moveTo(bx - s * .04f, by); sp.lineTo(bx, by - s * .1f); sp.lineTo(bx + s * .04f, by); sp.close(); c.drawPath(sp, fill); } fill.setColor(GameKit.alpha(0xFFFF5252, fade * ((now / 400) % 2 == 0 ? 1f : .5f))); c.drawCircle(cx, cy + s * .2f, s * .04f, fill); break; }
            default: break;
        }
    }

    void drawAims(Canvas c, long now) {
        for (Tank t : tanks) {
            if (!t.alive || t.player || t.aimAt <= 0) continue;
            float x0 = px(t.x + 1 + DX[t.dir]), y0 = py(t.y + 1 + DY[t.dir]), x1 = t.dir == 1 ? board.right : t.dir == 3 ? board.left : x0, y1 = t.dir == 2 ? board.bottom : t.dir == 0 ? board.top : y0;
            float k = GameKit.clamp((now - t.aimAt) / 850f);
            stroke.setStrokeWidth(Math.max(1f, unit * (.05f + .08f * k))); stroke.setColor(GameKit.alpha(0xFFFF2D2D, .35f + .5f * k * ((now / 80) % 2 == 0 ? 1f : .6f))); c.drawLine(x0, y0, x1, y1, stroke);
        }
    }

    void drawRig(Canvas c, Boss b, float cx, float cy, float w, float h, long now, boolean flash, float k) {
        int ink = flash ? 0xFFFFFFFF : BOSS_INK[b.kind], glow = BOSS_GLOW[b.kind], dark = GameKit.mix(ink, 0xFF000000, .45f), lite = GameKit.mix(ink, 0xFFFFFFFF, .3f);
        for (int side = -1; side <= 1; side += 2) { box.set(cx + side * w * .78f - w * .2f, cy - h * .7f, cx + side * w * .78f + w * .2f, cy + h * .9f); fill.setColor(GameKit.alpha(dark, k)); c.drawRoundRect(box, unit * .2f, unit * .2f, fill); fill.setColor(GameKit.alpha(GameKit.mix(dark, 0xFFFFFFFF, .2f), k)); for (int q = 0; q < 5; q++) c.drawCircle(box.centerX(), box.top + (q + .5f) * box.height() / 5f, w * .07f, fill); }
        box.set(cx - w * .7f, cy - h * .6f, cx + w * .7f, cy + h * .7f); fill.setColor(GameKit.alpha(ink, k)); c.drawRoundRect(box, unit * .3f, unit * .3f, fill);
        stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .08f)); stroke.setColor(GameKit.alpha(lite, k)); c.drawRoundRect(box, unit * .3f, unit * .3f, stroke);
        int sv = c.save(); RectF band = new RectF(cx - w * .65f, cy + h * .45f, cx + w * .65f, cy + h * .65f); c.clipRect(band); fill.setColor(GameKit.alpha(0xFFFFD23F, k)); c.drawRect(band, fill); fill.setColor(GameKit.alpha(0xFF14161C, k)); for (float x = band.left - unit; x < band.right; x += unit * .6f) { android.graphics.Path st = GameKit.path(); st.moveTo(x, band.bottom); st.lineTo(x + unit * .3f, band.bottom); st.lineTo(x + unit * .7f, band.top); st.lineTo(x + unit * .4f, band.top); st.close(); c.drawPath(st, fill); } c.restoreToCount(sv);
        fill.setColor(GameKit.alpha(dark, k)); c.drawCircle(cx, cy - h * .05f, w * .42f, fill);
        fill.setColor(GameKit.alpha(0xFF14161C, k)); c.drawCircle(cx, cy - h * .05f, w * .28f, fill);
        float pulse = (float) (.5 + .5 * Math.sin(now / 150.0)); fill.setColor(GameKit.alpha(glow, k * (.5f + .4f * pulse))); c.drawCircle(cx, cy - h * .05f, w * .16f, fill);
        for (int side = -1; side <= 1; side += 2) { box.set(cx + side * w * .5f - w * .06f, cy + h * .1f, cx + side * w * .5f + w * .06f, cy + h * 1.1f); fill.setColor(GameKit.alpha(dark, k)); c.drawRoundRect(box, w * .05f, w * .05f, fill); }
        cracks(c, b, cx, cy, w * .7f, h * .7f, k);
    }
    void drawHive(Canvas c, Boss b, float cx, float cy, float w, float h, long now, boolean flash, float k) {
        int ink = flash ? 0xFFFFFFFF : BOSS_INK[b.kind], glow = BOSS_GLOW[b.kind], dark = GameKit.mix(ink, 0xFF000000, .45f), lite = GameKit.mix(ink, 0xFFFFFFFF, .3f);
        android.graphics.Path p = GameKit.path();
        p.moveTo(cx - w * .5f, cy - h * .95f); p.lineTo(cx + w * .5f, cy - h * .95f); p.lineTo(cx + w, cy - h * .1f); p.lineTo(cx + w * .6f, cy + h * .9f); p.lineTo(cx - w * .6f, cy + h * .9f); p.lineTo(cx - w, cy - h * .1f); p.close();
        fill.setColor(GameKit.alpha(ink, k)); c.drawPath(p, fill);
        stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .08f)); stroke.setColor(GameKit.alpha(lite, k)); c.drawPath(p, stroke);
        box.set(cx - w * .4f, cy + h * .25f, cx + w * .4f, cy + h * .85f); fill.setColor(GameKit.alpha(0xFF0A0A0A, k)); c.drawRoundRect(box, unit * .2f, unit * .2f, fill);
        float pulse = (float) (.5 + .5 * Math.sin(now / 200.0)); fill.setColor(GameKit.alpha(glow, k * (.25f + .35f * pulse))); c.drawRoundRect(box, unit * .2f, unit * .2f, fill);
        for (int q = -2; q <= 2; q++) { float dx = cx + q * w * .3f, dy = cy - h * .5f + Math.abs(q) * h * .1f; fill.setColor(GameKit.alpha(dark, k)); c.drawCircle(dx, dy, w * .13f, fill); fill.setColor(GameKit.alpha(glow, k * ((now / 250 + q) % 3 == 0 ? 1f : .4f))); c.drawCircle(dx, dy, w * .05f, fill); }
        cracks(c, b, cx, cy, w * .7f, h * .7f, k);
    }
    void drawSerpent(Canvas c, Boss b, float cx, float cy, float w, float h, long now, boolean flash, float k) {
        int ink = flash ? 0xFFFFFFFF : BOSS_INK[b.kind], glow = BOSS_GLOW[b.kind], dark = GameKit.mix(ink, 0xFF000000, .4f), lite = GameKit.mix(ink, 0xFFFFFFFF, .35f);
        float dir = b.dirX == 0 ? 1 : Math.signum(b.dirX);
        for (int q = 5; q >= 1; q--) {
            float sx = cx - dir * q * w * .38f, sy = cy + (float) Math.sin(now / 220.0 - q * .9) * h * .35f, r = h * (.62f - q * .06f);
            fill.setColor(GameKit.alpha(q % 2 == 0 ? ink : dark, k)); c.drawCircle(sx, sy, r, fill);
            fill.setColor(GameKit.alpha(lite, k)); c.drawCircle(sx, sy - r * .35f, r * .25f, fill);
        }
        fill.setColor(GameKit.alpha(ink, k)); c.drawCircle(cx, cy, h * .75f, fill);
        android.graphics.Path j = GameKit.path(); j.moveTo(cx + dir * h * .4f, cy + h * .2f); j.lineTo(cx + dir * h * 1.1f, cy + h * .45f); j.lineTo(cx + dir * h * .5f, cy + h * .6f); j.close(); fill.setColor(GameKit.alpha(dark, k)); c.drawPath(j, fill);
        for (int s2 = -1; s2 <= 1; s2 += 2) { float ex = cx + dir * h * .25f + s2 * h * .25f, ey = cy - h * .2f; fill.setColor(GameKit.alpha(0xFF14161C, k)); c.drawCircle(ex, ey, h * .17f, fill); fill.setColor(GameKit.alpha(glow, k)); c.drawCircle(ex, ey, h * .1f, fill); }
        cracks(c, b, cx, cy, w * .5f, h * .6f, k);
    }
    void drawDreadnought(Canvas c, Boss b, float cx, float cy, float w, float h, long now, boolean flash, float k) {
        int ink = flash ? 0xFFFFFFFF : BOSS_INK[b.kind], glow = BOSS_GLOW[b.kind], dark = GameKit.mix(ink, 0xFF000000, .45f), lite = GameKit.mix(ink, 0xFFFFFFFF, .3f);
        android.graphics.Path p = GameKit.path();
        p.moveTo(cx - w * 1.15f, cy); p.lineTo(cx - w * .8f, cy - h * .7f); p.lineTo(cx + w * .8f, cy - h * .7f); p.lineTo(cx + w * 1.15f, cy); p.lineTo(cx + w * .8f, cy + h * .7f); p.lineTo(cx - w * .8f, cy + h * .7f); p.close();
        fill.setColor(GameKit.alpha(ink, k)); c.drawPath(p, fill);
        stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .08f)); stroke.setColor(GameKit.alpha(lite, k)); c.drawPath(p, stroke);
        box.set(cx - w * .25f, cy - h * .55f, cx + w * .25f, cy + h * .1f); fill.setColor(GameKit.alpha(dark, k)); c.drawRoundRect(box, unit * .15f, unit * .15f, fill);
        fill.setColor(GameKit.alpha(glow, k * .9f)); box.set(cx - w * .18f, cy - h * .45f, cx + w * .18f, cy - h * .3f); c.drawRect(box, fill);
        float aim = me != null && me.alive ? (float) Math.toDegrees(Math.atan2(py(me.y + 1) - cy, px(me.x + 1) - cx)) - 90 : 0;
        for (int q = -1; q <= 1; q += 1) { float tx = cx + q * w * .62f, ty = cy + h * .3f; if (q == 0) ty = cy + h * .35f; int sv = c.save(); c.rotate(aim, tx, ty); box.set(tx - unit * .1f, ty, tx + unit * .1f, ty + h * .8f); fill.setColor(GameKit.alpha(0xFF14161C, k)); c.drawRect(box, fill); c.restoreToCount(sv); fill.setColor(GameKit.alpha(dark, k)); c.drawCircle(tx, ty, unit * .38f, fill); fill.setColor(GameKit.alpha(lite, k)); c.drawCircle(tx - unit * .1f, ty - unit * .1f, unit * .12f, fill); }
        for (int s2 = -1; s2 <= 1; s2 += 2) if (motion && rnd.nextFloat() < .2f) { GameKit.Bit sm = spark(cx + s2 * w * .4f, cy - h * .7f, 0x88505050, 30, 4f); sm.round = true; sm.gravity = -.3f; sm.decay = 1.2f; }
        cracks(c, b, cx, cy, w * .8f, h * .6f, k);
    }

    static final float CELL_XP = .125f;
    float soulBits, soulShown; long gainAt = -10000;
    void soulGain(float xp) {
        if (soul == NONE || soulLv >= TOP || xp <= 0) return;
        soulSync();
        soulBits += xp; gainAt = SystemClock.uptimeMillis();
        while (soulBits >= .999f) { soulBits = Math.max(0f, soulBits - 1f); soulUnits++; }
        if (soulLevelOf(soulUnits) > soulLv) soulUp();
        if (soulLv >= TOP) soulBits = 0;
    }
    float soulProgress() { return soulLv >= TOP ? 1f : GameKit.clamp((soulUnits + soulBits - soulTotal(soulLv)) / (float) soulStep(soulLv)); }

    int meMax() { int inf = inferno(); return inf > 0 ? 2 + inf / 3 : 1; }
    float meRegen; long meHealAt = -10000;

    boolean hurtMe(int n, long now) {
        if (me == null || !me.alive) return false;
        if (me.hp > n) {
            me.hp -= n; me.flashAt = now; me.shieldUntil = Math.max(me.shieldUntil, now + 700); meRegen = 0;
            hitNumber(px(me.x + 1), py(me.y) - unit * .3f, n, 0xFFFF5A5A, false);
            burst(px(me.x + 1), py(me.y + 1), 0xFFFF7A3D, 14, 200, 4); shake(6); GameKit.thump(a, .7f);
            PixelStackSound.play(PixelStackSound.BOOM, .35f, 1.4f);
            return false;
        }
        explode(me, true); meDeadAt = now; soulDown();
        return true;
    }

    static final class Wake { float x, y; long at, life; int kind; }
    final ArrayList<Wake> wakes = new ArrayList<Wake>();
    float wakeDist, wakeBossCool, lastMeX = -1, lastMeY = -1, burnSoundCool;
    void stepWays(float dt, long now) {
        boolean live = me != null && me.alive && state == PLAY && travelAt < 0;
        if (live) {
            int mx = meMax();
            if (me.maxHp != mx) { if (mx > me.maxHp) me.hp += mx - me.maxHp; me.maxHp = mx; me.hp = Math.max(1, Math.min(mx, me.hp)); }
            if (inferno() > 0 && me.hp < me.maxHp) { meRegen += dt; if (meRegen >= Math.max(3.5f, 9.5f - .6f * inferno())) { meRegen = 0; me.hp++; meHealAt = now; pop("+1", px(me.x + 1), py(me.y - .3f), 0xFF2BB673, 14); burst(px(me.x + 1), py(me.y + 1), 0xFFFFB347, 8, 140, 3); PixelStackSound.play(PixelStackSound.SPARKLE, .3f, .9f); } } else meRegen = 0;
            if (inferno() > 0) {
                int c0 = Math.max(0, (int) Math.floor((me.x + .1f) / 2)), c1 = Math.min(N - 1, (int) Math.floor((me.x + 1.9f) / 2)), r0 = Math.max(0, (int) Math.floor((me.y + .1f) / 2)), r1 = Math.min(N - 1, (int) Math.floor((me.y + 1.9f) / 2));
                for (int cy = r0; cy <= r1; cy++) for (int cx = c0; cx <= c1; cx++) if (bush[cy][cx]) {
                    bush[cy][cx] = false; wallsDirty = true;
                    for (int i = 0; i < 10; i++) { GameKit.Bit b = spark(px(cx * 2 + 1), py(cy * 2 + 1), i % 3 == 0 ? bushC : i % 3 == 1 ? 0xFFFF7A3D : 0xFFFFC94A, 120, unit / d * .3f); b.gravity = -.3f; b.decay = 1.4f; }
                    if (burnSoundCool <= 0) { burnSoundCool = .25f; PixelStackSound.play(PixelStackSound.FIRE, .35f, 1.1f); }
                }
            }
        }
        burnSoundCool -= dt;
        if (live && soul != NONE) {
            if (lastMeX >= 0) wakeDist += Math.abs(me.x - lastMeX) + Math.abs(me.y - lastMeY);
            lastMeX = me.x; lastMeY = me.y;
            if (wakeDist >= .8f) {
                wakeDist = 0; Wake w = new Wake(); w.x = me.x + 1 - DX[me.dir] * .85f; w.y = me.y + 1 - DY[me.dir] * .85f; w.at = now; w.kind = soul;
                w.life = soul == THUNDER ? 700 + 110L * soulLv : 900 + 150L * soulLv; wakes.add(w); while (wakes.size() > 70) wakes.remove(0);
            }
        } else { lastMeX = -1; wakeDist = 0; }
        for (int i = wakes.size() - 1; i >= 0; i--) if (now - wakes.get(i).at > wakes.get(i).life) wakes.remove(i);
        if (!wakes.isEmpty() && state == PLAY) {
            for (Tank t : new ArrayList<Tank>(tanks)) {
                if (t.player || !t.alive || !ready(t)) continue;
                t.wakeCool -= dt; if (t.wakeCool > 0) continue;
                for (Wake w : wakes) if (Math.abs(t.x + 1 - w.x) < 1.3f && Math.abs(t.y + 1 - w.y) < 1.3f) {
                    t.wakeCool = .6f;
                    if (w.kind == THUNDER) { int lv = Math.max(1, thunder()); t.zapAt = now; fakeHit(t, 1, 0xFF7FE9FF); if (!t.alive) break; t.stunUntil = Math.max(t.stunUntil, now + 350 + 70L * lv); if (lv >= 9 && t.type != 7) shockTank(t); PixelStackSound.play(PixelStackSound.TING, .2f, 1.8f); }
                    else { fakeHit(t, 1, 0xFFFF7A3D); if (t.alive && inferno() >= 4) burnTank(t); }
                    break;
                }
            }
            wakeBossCool -= dt;
            if (wakeBossCool <= 0) for (Wake w : wakes) if (bossTouch(w.x, w.y, .5f)) { wakeBossCool = .6f; hurtBoss(1, w.kind == THUNDER ? 0xFF7FE9FF : 0xFFFF7A3D); break; }
        }
        stepBaseWay(dt, now);
    }
    void drawWakes(Canvas c, long now) {
        fill.setShader(null);
        for (int i = 0; i < wakes.size(); i++) {
            Wake w = wakes.get(i); float age = (now - w.at) / (float) w.life; if (age < 0 || age >= 1) continue;
            float a0 = 1 - GameKit.easeIn(age), x = px(w.x), y = py(w.y);
            if (w.kind == THUNDER) {
                fill.setColor(GameKit.alpha(0xFF4FD8FF, a0 * .16f)); c.drawCircle(x, y, unit * .8f, fill);
                Wake n = i + 1 < wakes.size() ? wakes.get(i + 1) : null;
                if (n != null && n.kind == THUNDER && Math.abs(n.x - w.x) + Math.abs(n.y - w.y) < 1.8f) bolt(c, x, y, px(n.x), py(n.y), unit * .45f, 4, now / 50 + i * 13, 0xFF7FE9FF, Math.max(1.4f, unit * .1f), a0);
                if (motion && rnd.nextFloat() < .08f) { GameKit.Bit b = spark(x, y, 0xFFBFF4FF, 80, 2f); b.round = true; b.gravity = 0; b.decay = 4f; }
            } else {
                float pulse = motion ? (float) (.5 + .5 * Math.sin(now / 160.0 + i)) : .5f, r = unit * (.75f + .1f * pulse) * (age < .1f ? age / .1f : 1f);
                fill.setColor(GameKit.alpha(0xFF2A0E08, a0 * .75f)); c.drawCircle(x, y, r, fill);
                fill.setColor(GameKit.alpha(0xFFE5532B, a0 * .9f)); c.drawCircle(x, y, r * .72f, fill);
                fill.setColor(GameKit.alpha(0xFFFFB347, a0 * (.7f + .3f * pulse))); c.drawCircle(x + r * .1f, y - r * .08f, r * .42f, fill);
                fill.setColor(GameKit.alpha(0xFFFFF1A8, a0 * .8f)); c.drawCircle(x - r * .15f, y + r * .12f, r * .14f, fill);
                if (motion && rnd.nextFloat() < .05f) { GameKit.Bit b = spark(x, y, rnd.nextBoolean() ? 0xFFFF9A3D : 0x88505050, 30, 2.6f); b.round = true; b.gravity = -.35f; b.decay = 1.5f; }
            }
        }
    }

    int baseShield; float baseShieldCool, baseZapCool, baseHealCool; long baseShieldAt = -10000, baseHealAt = -10000, baseZapAt = -10000; float baseZapX, baseZapY;
    int baseShieldMax() { int th = thunder(); return th > 0 ? 1 + th / 3 : 0; }
    void stepBaseWay(float dt, long now) {
        if (baseDown || state != PLAY || travelAt > 0) return;
        int th = thunder(), inf = inferno(), sm = baseShieldMax();
        if (baseShield > sm) baseShield = sm;
        if (sm > 0 && baseShield < sm) { baseShieldCool -= dt; if (baseShieldCool <= 0) { baseShield++; baseShieldCool = Math.max(4f, 15f - th); baseShieldAt = now; PixelStackSound.play(PixelStackSound.TING, .22f, 1.6f); } }
        else baseShieldCool = Math.max(4f, 15f - th);
        if (th > 0) {
            baseZapCool -= dt;
            if (baseZapCool <= 0) {
                Tank best = null; float bd = 3.2f + .2f * th;
                for (Tank t : tanks) if (!t.player && t.alive && ready(t) && now >= t.stunUntil) { float dd = (float) Math.hypot(t.x + 1 - 13, t.y + 1 - 25); if (dd < bd) { bd = dd; best = t; } }
                if (best != null) {
                    baseZapCool = Math.max(.8f, 2.8f - .2f * th); best.stunUntil = now + 900 + 120L * th; best.zapAt = now; baseZapAt = now; baseZapX = best.x + 1; baseZapY = best.y + 1;
                    if (th >= 5) fakeHit(best, 1, 0xFF7FE9FF);
                    burst(px(baseZapX), py(baseZapY), 0xFFBFF4FF, 8, 140, 3); PixelStackSound.play(PixelStackSound.TING, .3f, 1.3f);
                } else baseZapCool = .2f;
            }
        }
        if (inf > 0 && baseHp < baseMax) {
            baseHealCool -= dt * (baseHp * 2 <= baseMax ? 2f : 1f);
            if (baseHealCool <= 0) { baseHealCool = Math.max(2.5f, 10f - .7f * inf); baseHp++; baseHealAt = now; pop("+1", px(13), py(23.2f), 0xFF2BB673, 14); burst(px(13), py(25), 0xFFFFB347, 8, 140, 3); PixelStackSound.play(PixelStackSound.SPARKLE, .25f, .9f); }
        } else baseHealCool = Math.max(2.5f, 10f - .7f * inf);
    }
    void drawBaseWay(Canvas c, long now) {
        if (baseDown) return;
        float cx = px(13), cy = py(25), pulse = motion ? (float) (.5 + .5 * Math.sin(now / 260.0)) : .5f;
        fill.setShader(null);
        if (baseShield > 0) {
            float r = unit * 1.75f, hit = GameKit.clamp(1 - (now - baseShieldAt) / 500f);
            fill.setColor(GameKit.alpha(0xFF4FD8FF, .07f + .05f * pulse + .15f * hit)); c.drawCircle(cx, cy, r, fill);
            stroke.setStrokeWidth(Math.max(1.2f, unit * .08f));
            for (int k = 0; k < baseShield; k++) { stroke.setColor(GameKit.alpha(0xFF7FE9FF, .45f + .3f * pulse - .08f * k)); c.drawCircle(cx, cy, r - k * unit * .14f, stroke); }
            if (motion && rnd.nextFloat() < .1f) { double ang = rnd.nextDouble() * Math.PI * 2; bolt(c, cx + (float) Math.cos(ang) * r * .6f, cy + (float) Math.sin(ang) * r * .6f, cx + (float) Math.cos(ang + .5) * r, cy + (float) Math.sin(ang + .5) * r, unit * .2f, 3, now / 40, 0xFFBFF4FF, Math.max(1f, unit * .06f), .8f); }
        }
        if (now - baseZapAt < 260) bolt(c, cx, py(24.2f), px(baseZapX), py(baseZapY), unit * .6f, 6, now / 40, 0xFF7FE9FF, Math.max(1.5f, unit * .12f), 1 - (now - baseZapAt) / 260f);
        if (inferno() > 0) {
            for (int k = 0; k < 6; k++) { double ang = now / 600.0 + k * Math.PI / 3; float rr = unit * (1.5f + .1f * (float) Math.sin(now / 200.0 + k)); fill.setColor(GameKit.alpha(k % 2 == 0 ? 0xFFFF9A3D : 0xFFFFC94A, .75f)); c.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr * .8f, unit * .1f, fill); }
            float h = GameKit.clamp(1 - (now - baseHealAt) / 600f);
            if (h > 0) { stroke.setStrokeWidth(Math.max(1.5f, unit * .1f) * h); stroke.setColor(GameKit.alpha(0xFF7DE38A, h)); c.drawCircle(cx, cy, unit * (1.2f + .8f * (1 - h)), stroke); }
            if (motion && rnd.nextFloat() < .12f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * unit * 2.4f, cy, 0xFFFF9A3D, 25, 2.2f); b.round = true; b.gravity = -.35f; b.decay = 1.4f; }
        }
    }

    boolean claimTap(float x, float y) {
        if (state != PLAY || me == null || !me.alive || travelAt > 0) return false;
        long now = SystemClock.uptimeMillis();
        float best = Math.max(unit * 1.6f, 28 * d); Object hit = null;
        for (Prize p : prizes) { if (p.tapped) continue; float dd = (float) Math.hypot(px(p.x + 1) - x, py(p.y + 1) - y); if (dd < best) { best = dd; hit = p; } }
        for (Charge e : charges) { if (e.tapped || e.fate != 0 || now < e.at) continue; float dd = (float) Math.hypot(px(e.x) - x, py(e.y) - y); if (dd < best) { best = dd; hit = e; } }
        for (int i = 0; i < cores.size(); i++) { Core k = cores.get(i); if (k.used || coreTakenAt > 0 || now - k.at < 600) continue; float dd = (float) Math.hypot(px(k.x + 1) - x, py(k.y + 1) - y); if (dd < Math.max(best, unit * 1.8f)) { best = dd; hit = k; } }
        if (hit == null) return false;
        if (hit instanceof Prize) { Prize p = (Prize) hit; p.tapped = true; burst(px(p.x + 1), py(p.y + 1), TIER_COLOUR[tierOf(p.kind)], 10, 170, 3); }
        else if (hit instanceof Charge) { Charge e = (Charge) hit; e.tapped = true; burst(px(e.x), py(e.y), 0xFFBFF4FF, 10, 170, 3); }
        else {
            Core k = (Core) hit; int i = cores.indexOf(k);
            if (ask >= 0 && ask != i) shutAsk(now);
            ask = i; askAt = now; askTapped = true; k.ready = false;
            PixelStackSound.play(PixelStackSound.SOFT, .4f, k.kind == THUNDER ? 1.4f : .8f); GameKit.tick(a, .4f);
            burst(px(k.x + 1), py(k.y + 1), k.kind == THUNDER ? 0xFFBFF4FF : 0xFFFF7A3D, 10, 160, 3);
            return true;
        }
        PixelStackSound.play(PixelStackSound.PICK, .35f, 1.3f); GameKit.tick(a, .35f);
        return true;
    }

    final ArrayList<int[]> drumsDue = new ArrayList<int[]>();
    void blowDrum(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= N || cy >= N) return;
        boolean any = false;
        for (int k = 0; k < 4; k++) { int ux = cx * 2 + k % 2, uy = cy * 2 + k / 2; if (wall[uy][ux] == 4) { wall[uy][ux] = 0; any = true; } }
        if (!any) return;
        wallsDirty = true;
        long now = SystemClock.uptimeMillis();
        float x = cx * 2 + 1, y = cy * 2 + 1, sx = px(x), sy = py(y);
        burst(sx, sy, 0xFFFF7A3D, 26, 320, 7); burst(sx, sy, 0xFFFFE08A, 16, 260, 5); burst(sx, sy, 0xFF3A3A3A, 10, 150, 6);
        rings.add(new float[]{sx, sy, now, 2.2f});
        shake(10); GameKit.thump(a, .9f);
        PixelStackSound.play(PixelStackSound.BOOM, .6f, .75f); PixelStackSound.play(PixelStackSound.FIRE, .4f, .8f);
        Burn z = new Burn(); z.x = x; z.y = y; z.at = now - (BURN_MS - 1800); burns.add(z);
        for (Tank t : new ArrayList<Tank>(tanks)) if (!t.player && t.alive && ready(t) && Math.hypot(t.x + 1 - x, t.y + 1 - y) < 3.2f) fakeHit(t, 3, 0xFFFF7A3D);
        if (bossTouch(x, y, 1.5f)) hurtBoss(4, 0xFFFF7A3D);
        for (int uy = (int) (y - 3); uy <= (int) (y + 3); uy++) for (int ux = (int) (x - 3); ux <= (int) (x + 3); ux++) {
            if (ux < 0 || uy < 0 || ux >= U || uy >= U || wall[uy][ux] != 1 || fortress[uy][ux] || Math.hypot(ux + .5f - x, uy + .5f - y) > 2.3f) continue;
            wall[uy][ux] = 0;
            GameKit.Bit b = spark(px(ux + .5f), py(uy + .5f), ux % 2 == 0 ? brick : brickDark, 180, unit / d * .35f); b.gravity = 1f; b.decay = 1.3f;
        }
        for (int oy = cy - 2; oy <= cy + 2; oy++) for (int ox = cx - 2; ox <= cx + 2; ox++) if (ox >= 0 && oy >= 0 && ox < N && oy < N && wall[oy * 2][ox * 2] == 4) drumsDue.add(new int[]{ox, oy, (int) ((now + 170) & 0x7FFFFFFF)});
        addScore(50);
    }
    void stepDrums(long now) {
        for (int i = drumsDue.size() - 1; i >= 0; i--) { int[] q = drumsDue.get(i); if ((now & 0x7FFFFFFF) >= q[2]) { drumsDue.remove(i); blowDrum(q[0], q[1]); } }
    }

    void drawDrum(Canvas c, float l, float t, float a0, long now) {
        float s = unit * 2, cx = l + s / 2, cy = t + s / 2, r = s * .4f;
        fill.setShader(null);
        fill.setColor(GameKit.alpha(night ? 0x66000000 : 0x2A000000, a0)); c.drawCircle(cx + s * .04f, cy + s * .06f, r, fill);
        fill.setColor(GameKit.alpha(0xFF9E1F16, a0)); c.drawCircle(cx, cy, r, fill);
        fill.setColor(GameKit.alpha(0xFFD8402F, a0)); c.drawCircle(cx, cy, r * .86f, fill);
        stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(0xFF9E1F16, a0)); c.drawCircle(cx, cy, r * .66f, stroke); c.drawCircle(cx, cy, r * .46f, stroke);
        fill.setColor(GameKit.alpha(0x55FFFFFF, a0)); c.drawOval(GameKit.rf(cx - r * .62f, cy - r * .72f, cx + r * .05f, cy - r * .38f), fill);
        android.graphics.Path f = GameKit.path(); f.moveTo(cx - r * .2f, cy + r * .25f); f.quadTo(cx - r * .28f, cy - r * .1f, cx, cy - r * .35f); f.quadTo(cx + r * .02f, cy - r * .08f, cx + r * .12f, cy - r * .12f); f.quadTo(cx + r * .3f, cy + r * .05f, cx + r * .18f, cy + r * .25f); f.close();
        fill.setColor(GameKit.alpha(0xFFFFD23F, a0)); c.drawPath(f, fill);
        fill.setColor(GameKit.alpha(0xFF5A1410, a0)); c.drawCircle(cx + r * .48f, cy + r * .38f, r * .11f, fill);
    }

    static final int DOOR_RIGHT = 0, DOOR_TOP = 1, DOOR_LEFT = 2, DOOR_BOTTOM = 3;
    static final long DOOR_DELAY = 1300, TRAVEL_MS = 1500;
    static final float TRAVEL_GAP = 3f;
    int doorSide = DOOR_RIGHT, doorLane = 12, enterSide = -1, enterLane = 12, travelSide;
    long clearAt = -1, doorOpenAt = -1, travelAt = -1, arriveAt = -100000;
    float travelX0, travelY0;
    android.graphics.Bitmap oldPic;

    void placeDoor() {
        int startX = 4, startY = 12;
        if (enterSide == DOOR_RIGHT) { startX = 12; startY = enterLane / 2; } else if (enterSide == DOOR_LEFT) { startX = 0; startY = enterLane / 2; } else if (enterSide == DOOR_BOTTOM) { startX = enterLane / 2; startY = 12; }
        boolean[] reach = new boolean[N * N]; ArrayList<Integer> q = new ArrayList<Integer>(); reach[startY * N + startX] = true; q.add(startY * N + startX);
        for (int h = 0; h < q.size(); h++) {
            int at = q.get(h), x = at % N, y = at / N;
            for (int k = 0; k < 4; k++) { int nx = x + DX[k], ny = y + DY[k]; if (nx < 0 || ny < 0 || nx >= N || ny >= N || reach[ny * N + nx] || hardCell(nx, ny)) continue; reach[ny * N + nx] = true; q.add(ny * N + nx); }
        }
        ArrayList<int[]> all = new ArrayList<int[]>(), ok = new ArrayList<int[]>();
        int[] sides = stage == 1 && enterSide < 0 ? new int[]{DOOR_RIGHT} : new int[]{DOOR_RIGHT, DOOR_TOP, DOOR_LEFT};
        for (int side : sides) {
            if (side == enterSide) continue;
            if (side == DOOR_TOP) { for (int cx : new int[]{1, 2, 3, 9, 10, 11}) { int[] o = {side, cx, 0}; all.add(o); if (reach[cx]) ok.add(o); } }
            else for (int r = 2; r <= 12; r++) { int cx = side == DOOR_RIGHT ? 12 : 0; int[] o = {side, cx, r}; all.add(o); if (reach[r * N + cx]) ok.add(o); }
        }
        int[] pick = (ok.isEmpty() ? all : ok).get(rnd.nextInt((ok.isEmpty() ? all : ok).size()));
        doorSide = pick[0]; doorLane = (pick[0] == DOOR_TOP ? pick[1] : pick[2]) * 2;
        clearCell(pick[1], pick[2]);
        carve(pick[1], pick[2], startX, startY);
    }
    boolean hardCell(int cx, int cy) {
        if (water[cy][cx] || (cx >= 5 && cx <= 7 && cy >= 11)) return true;
        for (int k = 0; k < 4; k++) { byte v = wall[cy * 2 + k / 2][cx * 2 + k % 2]; if (v == 2 || v == 4) return true; }
        return false;
    }
    void clearCell(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= N || cy >= N || (cx >= 5 && cx <= 7 && cy >= 11)) return;
        water[cy][cx] = false; bush[cy][cx] = false;
        for (int k = 0; k < 4; k++) { int ux = cx * 2 + k % 2, uy = cy * 2 + k / 2; if (!fortress[uy][ux]) wall[uy][ux] = 0; }
        wallsDirty = true;
    }

    void carve(int ax, int ay, int bx, int by) {
        int[] cost = new int[N * N], from = new int[N * N];
        java.util.Arrays.fill(cost, Integer.MAX_VALUE); java.util.Arrays.fill(from, -1);
        java.util.PriorityQueue<int[]> q = new java.util.PriorityQueue<int[]>((x, y) -> Integer.compare(x[0], y[0]));
        int start = ay * N + ax, end = by * N + bx; cost[start] = 0; q.add(new int[]{0, start});
        while (!q.isEmpty()) {
            int[] e = q.poll(); int at = e[1];
            if (e[0] > cost[at]) continue;
            if (at == end) break;
            int x = at % N, y = at / N;
            for (int k = 0; k < 4; k++) {
                int nx = x + DX[k], ny = y + DY[k]; if (nx < 0 || ny < 0 || nx >= N || ny >= N || (nx >= 5 && nx <= 7 && ny >= 11)) continue;
                int c2 = 1; if (water[ny][nx]) c2 += 40;
                for (int j = 0; j < 4; j++) { byte v = wall[ny * 2 + j / 2][nx * 2 + j % 2]; if (v == 2 || v == 4) c2 += 12; else if (v == 1) c2 += 2; }
                int to = ny * N + nx; if (e[0] + c2 < cost[to]) { cost[to] = e[0] + c2; from[to] = at; q.add(new int[]{e[0] + c2, to}); }
            }
        }
        for (int at = end; at >= 0; at = from[at]) {
            int x = at % N, y = at / N; water[y][x] = false;
            for (int j = 0; j < 4; j++) { int ux = x * 2 + j % 2, uy = y * 2 + j / 2; if (wall[uy][ux] == 2 || wall[uy][ux] == 4) wall[uy][ux] = 0; }
            if (at == start) break;
        }
        wallsDirty = true;
    }

    void openEntry() {
        if (enterSide < 0) return;
        int c0 = enterLane / 2;
        if (enterSide == DOOR_LEFT) { clearCell(0, c0); clearCell(1, c0); carve(0, c0, 4, 12); }
        else if (enterSide == DOOR_RIGHT) { clearCell(12, c0); clearCell(11, c0); carve(12, c0, 4, 12); }
        else { clearCell(c0, 12); clearCell(c0, 11); carve(c0, 12, 4, 12); }
    }

    boolean doorLets(float x, float y) {
        if (doorOpenAt < 0 || travelAt > 0) return false;
        switch (doorSide) {
            case DOOR_RIGHT: return Math.abs(y - doorLane) < .06f && x > U - 2.2f && x < U + 2f;
            case DOOR_LEFT: return Math.abs(y - doorLane) < .06f && x < .2f && x > -4f;
            default: return Math.abs(x - doorLane) < .06f && y < .2f && y > -4f;
        }
    }

    void stepDoor(float dt, long now) {
        if (state != PLAY) return;
        boolean done = reserve == 0 && killed >= stageTanks && !baseDown && (boss == null || (!boss.alive && now - boss.deadAt > 1500));
        if (done && clearAt < 0) {
            clearAt = now; score += 1000; pop("+1000", board.centerX(), board.centerY() + 40 * d, night ? 0xFFFFFFFF : 0xFF14161C, 18);
            for (int i = 0; i < 40; i++) { GameKit.Bit b = spark(board.centerX(), board.centerY(), GameKit.RAINBOW[i % GameKit.RAINBOW.length], 420, 6); b.gravity = .8f; b.decay = .7f; }
            PixelStackSound.play(PixelStackSound.SUCCESS, .55f, 1f); GameKit.thump(a, .7f);
        }
        if (clearAt > 0 && doorOpenAt < 0 && now - clearAt > DOOR_DELAY) {
            doorOpenAt = now; dropCores(now);
            { int dcx = doorSide == DOOR_TOP ? doorLane / 2 : doorSide == DOOR_RIGHT ? 12 : 0, dcy = doorSide == DOOR_TOP ? 0 : doorLane / 2;
              int mcx = me != null && me.alive ? Math.max(0, Math.min(N - 1, (int) ((me.x + 1) / 2))) : 4, mcy = me != null && me.alive ? Math.max(0, Math.min(N - 1, (int) ((me.y + 1) / 2))) : 12;
              clearCell(dcx, dcy); carve(dcx, dcy, mcx, mcy); }
            postDelayed(() -> { PixelStackSound.play(PixelStackSound.RISE, .4f, .9f); PixelStackSound.play(PixelStackSound.SPARKLE, .4f, 1.2f); }, 250);
            float[] e = doorEdge(doorSide, doorLane); rings.add(new float[]{e[0], e[1], now, 1.6f});
        }
        if (doorOpenAt < 0 || now - doorOpenAt < 450 || me == null || !me.alive || !ready(me)) return;
        int out = doorSide == DOOR_RIGHT ? 1 : doorSide == DOOR_LEFT ? 3 : 0;
        boolean near = doorSide == DOOR_RIGHT ? me.x > U - 3.2f : doorSide == DOOR_LEFT ? me.x < 1.2f : me.y < 1.2f;
        if (dir == out && near) {
            float off = (doorSide == DOOR_TOP ? me.x : me.y) - doorLane;
            if (Math.abs(off) > .001f && Math.abs(off) < 1.35f) {
                float st = Math.min(Math.abs(off), me.speed * dt) * Math.signum(off);
                float nx = doorSide == DOOR_TOP ? me.x - st : me.x, ny = doorSide == DOOR_TOP ? me.y : me.y - st;
                if (free(nx, ny, me)) { me.x = nx; me.y = ny; me.movedAt = now; }
            }
        }
        boolean through = doorSide == DOOR_RIGHT ? me.x + 1 > U + .15f : doorSide == DOOR_LEFT ? me.x + 1 < -.15f : me.y + 1 < -.15f;
        if (through) startTravel(now);
    }
    void startTravel(long now) {
        shutAsk(now); fireHeld = false;
        travelSide = doorSide;
        snapOld(now);
        Tank keep = me; float kx = me.x, ky = me.y;
        enterSide = travelSide == DOOR_RIGHT ? DOOR_LEFT : travelSide == DOOR_LEFT ? DOOR_RIGHT : DOOR_BOTTOM; enterLane = doorLane;
        stage++;
        loadStage();
        tanks.add(keep); me = keep; keepLevel = keep.level; keep.shots = 0;
        if (travelSide == DOOR_RIGHT) { me.x = kx - (U + TRAVEL_GAP); me.dir = 1; } else if (travelSide == DOOR_LEFT) { me.x = kx + (U + TRAVEL_GAP); me.dir = 3; } else { me.y = ky + (U + TRAVEL_GAP); me.dir = 0; }
        travelX0 = me.x; travelY0 = me.y; travelAt = now;
        PixelStackSound.play(PixelStackSound.RISE, .35f, 1.5f); GameKit.tick(a, .4f);
    }
    void stepTravel(float dt, long now) {
        float k = GameKit.clamp((now - travelAt) / (float) TRAVEL_MS), e = GameKit.smooth(k);
        if (travelSide == DOOR_RIGHT) me.x = travelX0 * (1 - e); else if (travelSide == DOOR_LEFT) me.x = travelX0 + (U - 2 - travelX0) * e; else me.y = travelY0 + (U - 2 - travelY0) * e;
        me.moved += me.speed * dt * .6f; me.movedAt = now;
        if (k >= 1) {
            travelAt = -1; arriveAt = now;
            if (travelSide == DOOR_RIGHT) me.x = 0; else if (travelSide == DOOR_LEFT) me.x = U - 2; else me.y = U - 2;
            lastX = me.x; lastY = me.y;
            me.shieldUntil = Math.max(me.shieldUntil, now + 1500); spawnTimer = 1800;
            if (boss != null) boss.bornAt = now;
            if (oldPic != null) { try { oldPic.recycle(); } catch (Throwable ignored) { } oldPic = null; }
            PixelStackSound.play(PixelStackSound.PERFECT, .4f, .9f); GameKit.thump(a, .5f);
        }
    }

    void snapOld(long now) {
        RectF pr = plateRect(); int w = (int) Math.ceil(pr.width()), h = (int) Math.ceil(pr.height());
        if (oldPic != null) { try { oldPic.recycle(); } catch (Throwable ignored) { } oldPic = null; }
        if (w <= 0 || h <= 0) return;
        try { oldPic = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888); } catch (Throwable t) { oldPic = null; return; }
        Canvas oc = new Canvas(oldPic); oc.translate(-pr.left, -pr.top);
        RectF plate = new RectF(pr);
        drawPlate(oc, plate, 1f);
        int wd = GameKit.world(stage); if (wd > 0) { fill.setShader(null); fill.setColor(ground(GameKit.WORLDS[wd][0])); oc.drawRoundRect(plate, 18 * d, 18 * d, fill); }
        drawField(oc, 1f, null, false); drawBase(oc, 1f, null); drawBurns(oc, now); drawField(oc, 1f, null, true);
    }

    float[] doorEdge(int side, int lane) {
        switch (side) {
            case DOOR_RIGHT: return new float[]{board.right, py(lane + 1)};
            case DOOR_LEFT: return new float[]{board.left, py(lane + 1)};
            case DOOR_TOP: return new float[]{px(lane + 1), board.top};
            default: return new float[]{px(lane + 1), board.bottom};
        }
    }
    static float doorTurn(int side) { return side == DOOR_RIGHT ? 0 : side == DOOR_TOP ? -90 : side == DOOR_LEFT ? 180 : 90; }

    void drawDoor(Canvas c, int side, int lane, float k, long now, float dx, float dy, boolean arrows) {
        float[] e = doorEdge(side, lane);
        int sv = c.save(); c.translate(e[0] + dx, e[1] + dy); c.rotate(doorTurn(side));
        float hl = unit * 1.02f, rim = 7 * d, pulse = motion ? (float) (.5 + .5 * Math.sin(now / 240.0)) : .5f;
        fill.setShader(null);
        if (k > 0) {
            for (int i = 0; i < 4; i++) { float depth = unit * (.8f + .9f * i) * k, sp = hl * (1 + .12f * i); fill.setColor(GameKit.alpha(0xFFFFE9A8, k * (.2f - .04f * i) * (.8f + .2f * pulse))); box.set(-depth, -sp, rim + (6 + 5 * i) * d * k, sp); c.drawRoundRect(box, unit * .4f, unit * .4f, fill); }
            fill.setColor(GameKit.alpha(0xFFFFF8E1, k)); box.set(-1.5f * d, -hl * .94f, rim + 2 * d, hl * .94f); c.drawRect(box, fill);
            if (arrows) {
                stroke.setStrokeWidth(Math.max(2f, unit * .14f)); stroke.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                for (int i = 0; i < 3; i++) {
                    float ph = ((now / 700f) + i / 3f) % 1f, x = -unit * (3.2f - 2.6f * ph), a1 = k * (float) Math.sin(Math.PI * ph);
                    stroke.setColor(GameKit.alpha(0xFFFFC94A, a1)); c.drawLine(x - unit * .3f, -unit * .4f, x + unit * .08f, 0, stroke); c.drawLine(x + unit * .08f, 0, x - unit * .3f, unit * .4f, stroke);
                }
                stroke.setStrokeCap(android.graphics.Paint.Cap.BUTT);
            }
        }
        float shut = hl * (1 - GameKit.ease(k));
        int panel = GameKit.mix(steel, 0xFF14161C, .25f);
        fill.setColor(GameKit.alpha(panel, 1f));
        box.set(0, -hl, rim, -hl + shut); if (shut > .5f) c.drawRect(box, fill);
        box.set(0, hl - shut, rim, hl); if (shut > .5f) c.drawRect(box, fill);
        if (k < 1) { fill.setColor(GameKit.alpha(k > 0 ? 0xFF2BB673 : 0xFFE53935, (1 - k) * (.7f + .3f * pulse))); c.drawCircle(rim / 2, 0, 2.2f * d, fill); }
        fill.setColor(GameKit.alpha(GameKit.mix(steel, 0xFFFFFFFF, .2f), 1f));
        for (int s2 = -1; s2 <= 1; s2 += 2) { box.set(-2.5f * d, s2 * hl - 3 * d, rim + 3.5f * d, s2 * hl + 3 * d); c.drawRoundRect(box, 2 * d, 2 * d, fill); }
        if (k > 0 && motion && rnd.nextFloat() < .25f * k && arrows) { GameKit.Bit b = spark(e[0] + dx, e[1] + dy, rnd.nextBoolean() ? 0xFFFFE9A8 : 0xFFFFFFFF, 60, 2.5f); b.round = true; b.gravity = 0; b.decay = 2.2f; }
        c.restoreToCount(sv);
    }
    float doorK(long now) { return doorOpenAt < 0 ? 0f : GameKit.ease((now - doorOpenAt) / 650f); }

    void drawTravel(Canvas c, long now) {
        float k = GameKit.clamp((now - travelAt) / (float) TRAVEL_MS), e = GameKit.smooth(k);
        float span = (U + TRAVEL_GAP) * unit, cam = e * span, ox = 0, oy = 0, nx = 0, ny = 0;
        if (travelSide == DOOR_RIGHT) { ox = -cam; nx = span - cam; } else if (travelSide == DOOR_LEFT) { ox = cam; nx = -span + cam; } else { oy = cam; ny = -span + cam; }
        RectF view = new RectF(plateRect());
        int save = c.save(); c.clipRect(view);
        if (oldPic != null) c.drawBitmap(oldPic, view.left + ox, view.top + oy, null);
        {
            float[] ed = doorEdge(travelSide, enterLane);
            int sv = c.save(); c.translate(ed[0] + ox, ed[1] + oy); c.rotate(doorTurn(travelSide));
            float len = TRAVEL_GAP * unit, hl = unit * 1.02f;
            fill.setShader(null); fill.setColor(GameKit.alpha(GameKit.mix(plate, 0xFF000000, night ? .4f : .12f), 1f)); box.set(0, -hl - 3 * d, len, hl + 3 * d); c.drawRect(box, fill);
            fill.setColor(GameKit.alpha(0xFFFFE9A8, .22f)); box.set(0, -hl, len, hl); c.drawRect(box, fill);
            fill.setColor(GameKit.alpha(steel, 1f)); c.drawRect(0, -hl - 3 * d, len, -hl - 1.2f * d, fill); c.drawRect(0, hl + 1.2f * d, len, hl + 3 * d, fill);
            fill.setColor(GameKit.alpha(0xFFFFF3C4, .8f)); for (int i = 0; i < 3; i++) { float ph = ((now / 260f) + i / 3f) % 1f; c.drawCircle(len * ph, 0, unit * .09f, fill); }
            c.restoreToCount(sv);
        }
        drawDoor(c, travelSide, enterLane, 1f, now, ox, oy, false);
        int s2 = c.save(); c.translate(nx, ny);
        RectF plate2 = new RectF(view); drawPlate(c, plate2, 1f);
        int wd = GameKit.world(stage); if (wd > 0) { fill.setShader(null); fill.setColor(ground(GameKit.WORLDS[wd][0])); c.drawRoundRect(plate2, 18 * d, 18 * d, fill); }
        drawField(c, 1f, null, false);
        drawBase(c, 1f, null);
        drawDoor(c, enterSide, enterLane, 1f, now, 0, 0, false);
        drawDoor(c, doorSide, doorLane, 0f, now, 0, 0, false);
        if (me != null) drawTank(c, me, 1f);
        drawField(c, 1f, null, true);
        c.restoreToCount(s2);
        c.restoreToCount(save);
        drawHud(c, 1f);
    }

    void banner(Canvas c, String w, float cy, int colour, float k, float f) {
        text.setTypeface(GameKit.BOLD); text.setTextSize(sp(28) * Math.max(.01f, k));
        float tw = text.measureText(w) + 40 * d;
        box.set(board.centerX() - tw / 2, cy - 28 * d * k, board.centerX() + tw / 2, cy + 18 * d * k);
        fill.setShader(null); fill.setColor(GameKit.alpha(colour, f)); c.drawRoundRect(box, 20 * d, 20 * d, fill);
        text.setTextAlign(android.graphics.Paint.Align.CENTER); text.setColor(GameKit.alpha(0xFFFFFFFF, f)); c.drawText(w, board.centerX(), cy + 4 * d * k, text);
    }

    void drawSiren(Canvas c, long now) {
        if (baseDown) return;
        float t = now - baseHurtAt; if (t < 0 || t > 3200) return;
        float k = t < 200 ? t / 200f : t > 2600 ? 1 - (t - 2600) / 600f : 1f;
        float cx = px(13), cy = py(24.2f), R = unit * 7f, pulse = motion ? (float) (.5 + .5 * Math.sin(now / 130.0)) : .5f;
        double ang = motion ? now / 230.0 : -Math.PI / 2;
        fill.setShader(null);
        fill.setColor(GameKit.alpha(0xFFFF2D2D, k * (.08f + .1f * pulse))); c.drawCircle(cx, py(25), unit * 2.8f, fill);
        for (int sd = 0; sd < 2; sd++) {
            double a0 = ang + sd * Math.PI;
            for (int layer = 0; layer < 3; layer++) {
                float spread = .14f + .1f * layer, rr = R * (1 - .2f * layer);
                android.graphics.Path p = GameKit.path(); p.moveTo(cx, cy);
                p.lineTo(cx + (float) Math.cos(a0 - spread) * rr, cy + (float) Math.sin(a0 - spread) * rr);
                p.lineTo(cx + (float) Math.cos(a0 + spread) * rr, cy + (float) Math.sin(a0 + spread) * rr); p.close();
                fill.setColor(GameKit.alpha(0xFFFF3B3B, k * (.05f + .025f * (2 - layer)))); c.drawPath(p, fill);
            }
        }
        box.set(cx - unit * .3f, cy + unit * .05f, cx + unit * .3f, cy + unit * .22f); fill.setColor(GameKit.alpha(0xFF3A0A0A, k)); c.drawRoundRect(box, unit * .06f, unit * .06f, fill);
        fill.setColor(GameKit.alpha(0xFFFF2D2D, k)); c.drawCircle(cx, cy, unit * .24f, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, k * (.45f + .5f * pulse))); c.drawCircle(cx - unit * .07f, cy - unit * .07f, unit * .08f, fill);
    }

    void foeShadow(Canvas c, Tank t, float cx, float cy, float s, float fade, long now) {
        fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0x66000000 : 0x26000000, fade));
        c.drawOval(GameKit.rf(cx - s * .44f, cy - s * .38f, cx + s * .56f, cy + s * .6f), fill);
        if (motion && state == PLAY && now - t.movedAt < 150 && rnd.nextFloat() < .14f) {
            GameKit.Bit b = spark(px(t.x + 1 - DX[t.dir] * 1.02f), py(t.y + 1 - DY[t.dir] * 1.02f), night ? 0x88B0B6C4 : 0x667A808C, 26, 2.8f); b.round = true; b.gravity = -.15f; b.decay = 1.6f;
        }
    }
    void foeTreads(Canvas c, Tank t, float cx, float cy, float s, int dark2, float fade) {
        float tw = t.type == 1 ? .2f : t.type == 13 ? .3f : t.type == 3 || t.type == 4 || t.type == 7 || t.type == 9 ? .27f : .24f;
        int rim = GameKit.mix(dark2, 0xFF000000, .35f), tooth = GameKit.mix(dark2, 0xFFFFFFFF, .22f), wheel = GameKit.mix(dark2, 0xFFFFFFFF, .4f);
        for (int side = -1; side <= 1; side += 2) {
            float mid = cx + side * s * .32f, tl = mid - s * tw / 2, tr = mid + s * tw / 2;
            box.set(tl, cy - s * .47f, tr, cy + s * .47f);
            fill.setShader(null); fill.setColor(GameKit.alpha(rim, fade)); c.drawRoundRect(box, s * .1f, s * .1f, fill);
            box.inset(s * .018f, s * .018f); fill.setColor(GameKit.alpha(dark2, fade)); c.drawRoundRect(box, s * .08f, s * .08f, fill);
            float roll = (t.moved * unit) % (s * .13f);
            fill.setColor(GameKit.alpha(tooth, fade));
            for (float y = cy - s * .45f + roll; y < cy + s * .43f; y += s * .13f) { box.set(tl + s * .025f, y, tr - s * .025f, y + s * .045f); c.drawRoundRect(box, s * .015f, s * .015f, fill); }
            fill.setColor(GameKit.alpha(wheel, fade * .9f)); for (int k = 0; k < 4; k++) c.drawCircle(mid, cy - s * .33f + k * s * .22f, s * tw * .26f, fill);
            fill.setColor(GameKit.alpha(rim, fade)); for (int k = 0; k < 4; k++) c.drawCircle(mid, cy - s * .33f + k * s * .22f, s * tw * .1f, fill);
        }
    }
    void foeHull(Canvas c, Tank t, float cx, float cy, float s, float wide, int body, int dark2, int light2, float fade, long now) {
        float w = s * wide, top = cy - s * .34f, bot = cy + s * .36f;
        android.graphics.Path h = GameKit.path();
        if (t.type == 1) { h.moveTo(cx - w, bot); h.lineTo(cx - w, top + s * .2f); h.lineTo(cx - w * .5f, top - s * .07f); h.lineTo(cx + w * .5f, top - s * .07f); h.lineTo(cx + w, top + s * .2f); h.lineTo(cx + w, bot); h.close(); }
        else if (t.type == 3 || t.type == 4 || t.type == 9 || t.type == 13) h.addRoundRect(new RectF(cx - w, top, cx + w, bot), s * .07f, s * .07f, android.graphics.Path.Direction.CW);
        else { h.moveTo(cx - w + s * .06f, bot); h.quadTo(cx - w, bot, cx - w, bot - s * .06f); h.lineTo(cx - w, top + s * .1f); h.lineTo(cx - w + s * .1f, top); h.lineTo(cx + w - s * .1f, top); h.lineTo(cx + w, top + s * .1f); h.lineTo(cx + w, bot - s * .06f); h.quadTo(cx + w, bot, cx + w - s * .06f, bot); h.close(); }
        fill.setShader(null); fill.setColor(GameKit.alpha(body, fade)); c.drawPath(h, fill);
        stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(black ? 0x77FFFFFF : GameKit.mix(dark2, 0xFF000000, .2f), fade)); c.drawPath(h, stroke);
        fill.setColor(GameKit.alpha(0x33FFFFFF, fade)); box.set(cx - w + s * .03f, top + s * .1f, cx - w + s * .08f, bot - s * .06f); c.drawRoundRect(box, s * .02f, s * .02f, fill);
        fill.setColor(GameKit.alpha(0x26000000, fade)); box.set(cx + w - s * .08f, top + s * .1f, cx + w - s * .03f, bot - s * .06f); c.drawRoundRect(box, s * .02f, s * .02f, fill);
        box.set(cx - w + s * .05f, bot - s * .07f, cx + w - s * .05f, bot - s * .025f); c.drawRoundRect(box, s * .02f, s * .02f, fill);
        stroke.setStrokeWidth(Math.max(1f, s * .018f)); stroke.setColor(GameKit.alpha(GameKit.mix(body, 0xFF000000, .45f), fade));
        for (int k = -1; k <= 1; k++) c.drawLine(cx + k * s * .07f, bot - s * .17f, cx + k * s * .07f, bot - s * .1f, stroke);
        switch (t.type) {
            case 1:
                fill.setColor(GameKit.alpha(GameKit.mix(body, 0xFFFFFFFF, .55f), fade));
                for (int side = -1; side <= 1; side += 2) c.drawRect(cx + side * s * .1f - s * .022f, top + s * .04f, cx + side * s * .1f + s * .022f, bot - s * .2f, fill);
                if (now - t.movedAt < 150) { float fl = (float) (.6 + .4 * Math.sin(now / 45.0 + t.phase)); for (int side = -1; side <= 1; side += 2) { fill.setColor(GameKit.alpha(0xFFFF9A3D, fade * fl)); c.drawCircle(cx + side * w * .55f, bot + s * .05f, s * .045f * fl, fill); fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawCircle(cx + side * w * .55f, bot + s * .03f, s * .02f, fill); } }
                break;
            case 2: {
                android.graphics.Path g2 = GameKit.path(); g2.moveTo(cx - w + s * .1f, top + s * .02f); g2.lineTo(cx + w - s * .1f, top + s * .02f); g2.lineTo(cx + w - s * .04f, top + s * .13f); g2.lineTo(cx - w + s * .04f, top + s * .13f); g2.close();
                fill.setColor(GameKit.alpha(light2, fade)); c.drawPath(g2, fill);
                rivets(c, cx, top, bot, w, s, dark2, fade);
                break;
            }
            case 3:
                box.set(cx - w + s * .07f, top + s * .07f, cx + w - s * .07f, bot - s * .1f); fill.setColor(GameKit.alpha(GameKit.mix(body, 0xFF000000, .18f), fade)); c.drawRoundRect(box, s * .05f, s * .05f, fill);
                stroke.setStrokeWidth(Math.max(1f, s * .02f)); stroke.setColor(GameKit.alpha(light2, fade * .7f)); c.drawRoundRect(box, s * .05f, s * .05f, stroke);
                rivets(c, cx, top, bot, w, s, light2, fade);
                break;
            case 4: {
                int sv = c.save(); box.set(cx - w + s * .04f, top + s * .03f, cx + w - s * .04f, top + s * .12f); RectF band = new RectF(box); c.clipRect(band);
                fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawRect(band, fill);
                fill.setColor(GameKit.alpha(0xFF14161C, fade));
                for (float x = band.left - s * .2f; x < band.right; x += s * .1f) { android.graphics.Path st = GameKit.path(); st.moveTo(x, band.bottom); st.lineTo(x + s * .05f, band.bottom); st.lineTo(x + s * .14f, band.top); st.lineTo(x + s * .09f, band.top); st.close(); c.drawPath(st, fill); }
                c.restoreToCount(sv);
                rivets(c, cx, top, bot, w, s, light2, fade);
                break;
            }
            case 0:
                fill.setColor(GameKit.alpha(GameKit.mix(body, 0xFF14161C, .25f), fade * .8f));
                c.drawOval(GameKit.rf(cx - w * .8f, top + s * .16f, cx - w * .25f, top + s * .3f), fill); c.drawOval(GameKit.rf(cx + w * .2f, bot - s * .3f, cx + w * .78f, bot - s * .18f), fill); c.drawOval(GameKit.rf(cx - w * .6f, bot - s * .22f, cx - w * .15f, bot - s * .12f), fill);
                break;
            default: break;
        }
        for (int side = -1; side <= 1; side += 2) {
            float hx = cx + side * w * .62f, hy = (t.type == 1 ? top + s * .05f : top) + s * .045f;
            if (night) { android.graphics.Path cone = GameKit.path(); cone.moveTo(hx, hy); cone.lineTo(hx - s * .17f, hy - s * .55f); cone.lineTo(hx + s * .17f, hy - s * .55f); cone.close(); fill.setColor(GameKit.alpha(0xFFFFF1B0, fade * .1f)); c.drawPath(cone, fill); }
            fill.setColor(GameKit.alpha(0xFFFFF1B0, fade)); c.drawCircle(hx, hy, s * .03f, fill);
        }
    }
    void rivets(Canvas c, float cx, float top, float bot, float w, float s, int colour, float fade) {
        fill.setColor(GameKit.alpha(colour, fade));
        for (int side = -1; side <= 1; side += 2) for (int k = 0; k < 3; k++) c.drawCircle(cx + side * (w - s * .045f), top + s * .15f + k * (bot - top - s * .3f) / 2f, s * .018f, fill);
    }
    void foeBarrels(Canvas c, Tank t, float cx, float cy, float s, float[] barrels, int dark2, float fade, long now) {
        if (t.type == 8) return;
        float len = t.type == 1 ? .5f : t.type == 2 ? .64f : t.type == 4 ? .6f : t.type == 12 ? .82f : t.type == 11 ? .44f : .56f, th = t.type == 1 ? .035f : t.type == 12 ? .03f : t.type == 11 ? .075f : t.type == 2 || t.type == 4 || t.type == 13 ? .06f : .048f;
        if (barrels.length > 1) th *= .85f;
        float kick = motion ? s * .09f * (1 - GameKit.clamp((now - t.firedAt) / 170f)) : 0;
        int dk = GameKit.mix(dark2, 0xFF000000, .3f);
        for (float bo : barrels) {
            float bx = cx + s * bo, tip = cy - s * len + kick;
            box.set(bx - s * th, tip, bx + s * th, cy - s * .04f); fill.setShader(null); fill.setColor(GameKit.alpha(dark2, fade)); c.drawRoundRect(box, s * th, s * th, fill);
            fill.setColor(GameKit.alpha(GameKit.mix(dark2, 0xFFFFFFFF, .3f), fade)); c.drawRect(bx - s * th * .5f, tip + s * .03f, bx - s * th * .1f, cy - s * .12f, fill);
            if (t.type == 2 || t.type == 4) box.set(bx - s * th * 1.45f, tip - s * .01f, bx + s * th * 1.45f, tip + s * .07f); else box.set(bx - s * th * 1.2f, tip, bx + s * th * 1.2f, tip + s * .035f);
            fill.setColor(GameKit.alpha(dk, fade)); c.drawRoundRect(box, s * .02f, s * .02f, fill);
            if (now - t.firedAt < 90) { fill.setColor(GameKit.alpha(0xFFFFE27A, fade * (1 - (now - t.firedAt) / 90f))); c.drawCircle(bx, tip - s * .06f, s * .08f, fill); }
        }
    }
    void foeTurret(Canvas c, Tank t, float cx, float cy, float s, float tr, int body, int dark2, float fade, long now) {
        float ty = cy + s * .02f;
        int top = GameKit.mix(body, 0xFFFFFFFF, .3f), edge = GameKit.mix(body, 0xFF000000, .35f);
        fill.setShader(null); fill.setColor(GameKit.alpha(0x33000000, fade)); c.drawCircle(cx + s * .02f, ty + s * .03f, tr * 1.05f, fill);
        stroke.setStrokeWidth(Math.max(1f, s * .02f)); stroke.setColor(GameKit.alpha(edge, fade));
        if (t.type == 3 || t.type == 4 || t.type == 13) { box.set(cx - tr * 1.05f, ty - tr, cx + tr * 1.05f, ty + tr * 1.1f); fill.setColor(GameKit.alpha(top, fade)); c.drawRoundRect(box, tr * .35f, tr * .35f, fill); c.drawRoundRect(box, tr * .35f, tr * .35f, stroke); }
        else if (t.type == 2) { box.set(cx - tr * .9f, ty - tr * 1.15f, cx + tr * .9f, ty + tr * 1.05f); fill.setColor(GameKit.alpha(top, fade)); c.drawRoundRect(box, tr * .6f, tr * .6f, fill); c.drawRoundRect(box, tr * .6f, tr * .6f, stroke); }
        else { float rr = t.type == 1 ? tr * .85f : tr; fill.setColor(GameKit.alpha(top, fade)); c.drawCircle(cx, ty, rr, fill); c.drawCircle(cx, ty, rr, stroke); }
        fill.setColor(GameKit.alpha(0x55FFFFFF, fade)); c.drawCircle(cx - tr * .35f, ty - tr * .35f, tr * .28f, fill);
        fill.setColor(GameKit.alpha(edge, fade)); c.drawCircle(cx + tr * .25f, ty + tr * .3f, tr * .32f, fill);
        fill.setColor(GameKit.alpha(GameKit.mix(top, 0xFFFFFFFF, .2f), fade)); c.drawCircle(cx + tr * .25f, ty + tr * .3f, tr * .2f, fill);
        if (t.type != 5) {
            float sway = motion ? (float) Math.sin(now / 170.0 + t.phase) * s * (now - t.movedAt < 150 ? .05f : .015f) : 0, ax = cx - tr * .55f, ay = ty + tr * .45f;
            stroke.setStrokeWidth(Math.max(1f, s * .014f)); stroke.setColor(GameKit.alpha(GameKit.mix(dark2, 0xFF000000, .2f), fade)); c.drawLine(ax, ay, ax + sway, cy + s * .44f, stroke);
            fill.setColor(GameKit.alpha(t.type == 1 ? 0xFFFF2D55 : 0xFF14161C, fade)); c.drawCircle(ax + sway, cy + s * .44f, s * .018f, fill);
        }
    }

    static final class Charge { float x, y, spin; long at, endAt = -1; int fate; boolean burst, tapped; }
    final ArrayList<Charge> charges = new ArrayList<Charge>();
    void freeCharges(Tank t) {
        long now = SystemClock.uptimeMillis(); int n = 2 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) { Charge e = new Charge(); double ang = i * Math.PI * 2 / n + rnd.nextFloat(); e.x = t.x + 1 + (float) Math.cos(ang) * .8f; e.y = t.y + 1 + (float) Math.sin(ang) * .8f; e.at = now + 160 + i * 110; e.spin = rnd.nextFloat() * 6; charges.add(e); }
        burst(px(t.x + 1), py(t.y + 1), 0xFFBFF4FF, 14, 220, 4);
        PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.5f);
    }
    void stepCharges(float dt, long now) {
        for (int i = charges.size() - 1; i >= 0; i--) {
            Charge e = charges.get(i);
            if (e.fate != 0) {
                if (e.fate == 2 && !e.burst && now - e.endAt >= 380) { e.burst = true; float x = px(e.x), y = py(e.y); for (int k = 0; k < 18; k++) { GameKit.Bit b = spark(x, y, k % 3 == 0 ? 0xFF1A0E08 : k % 3 == 1 ? 0xFFE5532B : 0xFFFFB347, 220, 3.4f); b.round = true; b.gravity = 1.2f; b.decay = 1.3f; } PixelStackSound.play(PixelStackSound.POP, .4f, .6f); PixelStackSound.play(PixelStackSound.FIRE, .3f, 1f); GameKit.thump(a, .4f); }
                if (now - e.endAt > (e.fate == 2 ? 1000 : 650)) charges.remove(i);
                continue;
            }
            if (now < e.at) continue;
            e.spin += dt * 2;
            if (now - e.at > 10000) { e.fate = 3; e.endAt = now; continue; }
            if (me == null || !me.alive || state != PLAY || (soul == NONE && !e.tapped)) continue;
            float dx = me.x + 1 - e.x, dy = me.y + 1 - e.y, dd = (float) Math.hypot(dx, dy), reach = e.tapped ? 99f : soul == THUNDER ? 7f + .2f * thunder() : 5f;
            if (dd < reach && dd > .001f) {
                float v = dt * (e.tapped ? 26f : soul == THUNDER ? 5f + (reach - dd) * 4f : 3f + (reach - dd) * 2f); if (v > dd) v = dd;
                float sw = soul == THUNDER ? .55f : 0f, ux = dx / dd, uy = dy / dd, mx = ux - uy * sw, my = uy + ux * sw, ml = (float) Math.hypot(mx, my);
                e.x += mx / ml * v; e.y += my / ml * v;
            }
            if (dd < (soul == INFERNO ? 1.3f : 1.1f)) {
                e.fate = soul == INFERNO ? 2 : 1; e.endAt = now;
                soulGain(CELL_XP);
                if (soul != INFERNO) { e.x = me.x + 1; e.y = me.y + 1; absorbAt = now; addScore(250); burst(px(e.x), py(e.y), 0xFFBFF4FF, 12, 180, 3); PixelStackSound.play(PixelStackSound.TING, .45f, 1.7f); PixelStackSound.play(PixelStackSound.SPARKLE, .4f, 1.6f); GameKit.tick(a, .5f); }
                else { for (int k = 0; k < 10; k++) { GameKit.Bit b = spark(px(e.x), py(e.y), k % 2 == 0 ? 0xAA3A3A3A : 0xAA6A5A5A, 60, 6f); b.round = true; b.gravity = -.2f; b.decay = 1.2f; } PixelStackSound.play(PixelStackSound.FART, .25f, 1.3f); }
            }
        }
    }
    void drawCharges(Canvas c, long now) {
        for (Charge e : charges) {
            if (now < e.at) continue;
            float x = px(e.x), y = py(e.y);
            fill.setShader(null);
            if (e.fate == 0 || e.fate == 3) {
                float age = now - e.at, in = GameKit.spring(age / 400f), a0 = e.fate == 3 ? 1 - GameKit.clamp((now - e.endAt) / 500f) : 1f;
                y += motion ? (float) Math.sin(now / 280.0 + e.spin) * unit * .15f : 0;
                float r = unit * .34f * in, tw = motion ? (float) (.5 + .5 * Math.sin(now / 110.0 + e.spin * 3)) : .5f;
                fill.setColor(GameKit.alpha(0xFF4FD8FF, a0 * (.2f + .1f * tw))); c.drawCircle(x, y, r * 2.4f, fill);
                int sv = c.save(); c.rotate(e.spin * 40, x, y);
                box.set(x - r * .6f, y - r, x + r * .6f, y + r); fill.setColor(GameKit.alpha(0xFFE6FAFF, a0)); c.drawRoundRect(box, r * .5f, r * .5f, fill);
                fill.setColor(GameKit.alpha(0xFF4FD8FF, a0)); box.set(x - r * .25f, y - r * 1.2f, x + r * .25f, y - r * .9f); c.drawRect(box, fill);
                android.graphics.Path z = GameKit.path(); z.moveTo(x + r * .15f, y - r * .6f); z.lineTo(x - r * .25f, y + r * .05f); z.lineTo(x + r * .02f, y + r * .05f); z.lineTo(x - r * .15f, y + r * .6f); z.lineTo(x + r * .27f, y - r * .1f); z.lineTo(x, y - r * .1f); z.close(); c.drawPath(z, fill);
                c.restoreToCount(sv);
                if (motion && rnd.nextFloat() < .12f) { GameKit.Bit b = spark(x, y, 0xFFBFF4FF, 40, 2.2f); b.round = true; b.gravity = 0; b.decay = 3f; }
            } else if (e.fate == 1) {
                float t = (now - e.endAt) / 650f, mx = px(lastX + 1), my = py(lastY + 1);
                stroke.setStrokeWidth(3 * d * (1 - t)); stroke.setColor(GameKit.alpha(0xFF4FD8FF, 1 - t)); c.drawCircle(mx, my, unit * 2.2f * (1 - GameKit.ease(t)), stroke);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, (1 - t) * .7f)); c.drawCircle(mx, my, unit * .6f * (1 - t), fill);
            } else {
                float t = now - e.endAt;
                if (t < 380) { float k = GameKit.ease(t / 380f); for (int i = 0; i < 6; i++) { double ang = i * Math.PI / 3 + t / 120.0; float rr = unit * .3f * (1 - k * .5f); fill.setColor(GameKit.alpha(GameKit.mix(0xFF6A6A6A, 0xFF2A1A10, k), .75f)); c.drawCircle(x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr, unit * (.32f + .28f * k), fill); } }
                else { float k = (t - 380) / 620f; fill.setColor(GameKit.alpha(0xFF1A0E08, (1 - k) * .85f)); c.drawCircle(x, y, unit * (.5f + .9f * GameKit.ease(Math.min(1f, k * 2))), fill); fill.setColor(GameKit.alpha(0xFFE5532B, (1 - k) * .8f)); c.drawCircle(x, y, unit * (.3f + .45f * GameKit.ease(Math.min(1f, k * 2))), fill); fill.setColor(GameKit.alpha(0xFFFFB347, (1 - k) * .7f)); c.drawCircle(x, y, unit * .18f * (1 - k), fill); }
            }
        }
    }

    static final class Crawler { float x, y, vx, vy, phase; long at, deadAt = -1; boolean alive = true; int fate; }
    final ArrayList<Crawler> crawlers = new ArrayList<Crawler>();
    void releaseCrawlers(Tank t) {
        long now = SystemClock.uptimeMillis();
        for (int k = 0; k < 2; k++) { Crawler w = new Crawler(); w.x = t.x + 1; w.y = t.y + 1; double ang = rnd.nextFloat() * Math.PI * 2; w.vx = (float) Math.cos(ang) * 6; w.vy = (float) Math.sin(ang) * 6; w.at = now; w.phase = rnd.nextFloat() * 6; crawlers.add(w); }
        pop(I18n.t("Split!"), px(t.x + 1), py(t.y - .5f), 0xFFF2C230, 14);
    }
    void stepCrawlers(float dt, long now) {
        for (int i = crawlers.size() - 1; i >= 0; i--) {
            Crawler w = crawlers.get(i);
            if (!w.alive) { if (now - w.deadAt > 700) crawlers.remove(i); continue; }
            w.phase += dt * 8;
            if (now - w.at > 16000) { w.alive = false; w.deadAt = now; w.fate = 3; continue; }
            float tx = me != null && me.alive ? me.x + 1 : w.x, ty = me != null && me.alive ? me.y + 1 : w.y;
            float dx = tx - w.x, dy = ty - w.y, dd = (float) Math.hypot(dx, dy);
            float pull = inferno() >= 4 && dd < 9 ? 7f : 3.6f;
            if (dd > .01f && me != null && me.alive) { w.vx += dx / dd * pull * dt * 3; w.vy += dy / dd * pull * dt * 3; }
            float v = (float) Math.hypot(w.vx, w.vy), max = pull; if (v > max) { w.vx *= max / v; w.vy *= max / v; }
            w.x = Math.max(.5f, Math.min(U - .5f, w.x + w.vx * dt)); w.y = Math.max(.5f, Math.min(U - .5f, w.y + w.vy * dt));
            if (me == null || !me.alive || state != PLAY) continue;
            if (soul == THUNDER) { if (dd < 3.4f + .12f * thunder()) scrapCrawler(w, now); }
            else if (dd < 1.4f) eatCrawler(w, now);
        }
    }
    void scrapCrawler(Crawler w, long now) {
        w.alive = false; w.deadAt = now; w.fate = 1; zapAt = now; zapX0 = lastX + 1; zapY0 = lastY + 1; zapX1 = w.x; zapY1 = w.y;
        int pts = 120; addScore(pts); hitNumber(px(w.x), py(w.y) - unit * .6f, 1, 0xFF7FE9FF, false);
        for (int k = 0; k < 12; k++) { GameKit.Bit b = spark(px(w.x), py(w.y), k % 3 == 0 ? 0xFF4A4A4A : 0xFF9AA3B5, 80, 3f); b.gravity = -.15f; b.decay = 1f; }
        PixelStackSound.play(PixelStackSound.TING, .3f, 1.9f); GameKit.tick(a, .35f);
    }
    void eatCrawler(Crawler w, long now) {
        w.alive = false; w.deadAt = now; w.fate = soul == INFERNO ? 2 : 0;
        int pts = 150; addScore(pts); hitNumber(px(w.x), py(w.y) - unit * .6f, 1, soul == INFERNO ? 0xFFFF5A1F : 0xFFFFC94A, false);
        burst(px(w.x), py(w.y), soul == INFERNO ? 0xFFFF7A3D : 0xFF9AA3B5, 10, 160, 3);
        PixelStackSound.play(PixelStackSound.CHIP, .4f, .8f); GameKit.thump(a, .5f);
        if (soul == INFERNO) {
            eatAt = now; meals++;
            if (inferno() >= 4) frenzyUntil = now + 4000;
            if (inferno() >= 9 && meals % 10 == 0) { lives++; pop(I18n.t("Extra life"), px(lastX + 1), py(lastY - 1), 0xFFFF5A1F, 18); PixelStackSound.play(PixelStackSound.SUCCESS, .45f, .8f); }
        }
    }
    void shootCrawlers(Shot s) {
        for (Crawler w : crawlers) if (w.alive && Math.abs(w.x - s.x) < .7f && Math.abs(w.y - s.y) < .7f) { w.alive = false; w.deadAt = SystemClock.uptimeMillis(); w.fate = 3; addScore(100); hitNumber(px(w.x), py(w.y) - unit * .6f, 1, 0xFFFFC94A, false); burst(px(w.x), py(w.y), 0xFFFFC94A, 8, 140, 3); PixelStackSound.play(PixelStackSound.CHIP, .3f, 1.2f); if (!s.laser) kill(s); return; }
    }
    void drawCrawlers(Canvas c, long now) {
        for (Crawler w : crawlers) {
            float x = px(w.x), y = py(w.y), r = unit * .45f;
            fill.setShader(null);
            if (!w.alive) {
                float t = (now - w.deadAt) / 700f; if (t >= 1) continue; float a0 = 1 - t;
                if (w.fate == 1) { fill.setColor(GameKit.alpha(GameKit.mix(0xFFBFF4FF, 0xFF3A3A3A, Math.min(1f, t * 3)), a0)); for (int i = 0; i < 6; i++) { float fx = x + ((i % 3) - 1) * r * .5f, fy = y + (i / 3 - .5f) * r * .5f + GameKit.ease(t) * r; c.drawRect(fx - r * .2f, fy - r * .2f, fx + r * .2f, fy + r * .2f, fill); } }
                else if (w.fate == 2) { float k = (float) Math.sin(Math.PI * Math.min(1f, t * 2)); fill.setColor(GameKit.alpha(0xFFFF7A3D, a0 * .7f)); c.drawCircle(x, y, r * (1 + k), fill); }
                else { fill.setColor(GameKit.alpha(0xFF9AA3B5, a0)); c.drawCircle(x, y, r * (1 - t), fill); }
                continue;
            }
            float age = now - w.at, in = GameKit.spring(age / 300f), hov = motion ? (float) Math.sin(w.phase) * unit * .08f : 0;
            fill.setColor(0x30000000); c.drawOval(GameKit.rf(x - r * in, y + r * .7f, x + r * in, y + r * 1.05f), fill);
            y += hov; r *= in;
            stroke.setStrokeWidth(Math.max(1f, r * .16f)); stroke.setColor(0xCC9AA3B5); float sp = now / 30f;
            for (int k = 0; k < 2; k++) { double ang = sp + k * Math.PI / 2; c.drawLine(x - (float) Math.cos(ang) * r * 1.3f, y - r * .9f - (float) Math.sin(ang) * r * .3f, x + (float) Math.cos(ang) * r * 1.3f, y - r * .9f + (float) Math.sin(ang) * r * .3f, stroke); }
            fill.setColor(0xFF5A6378); c.drawCircle(x, y, r, fill);
            fill.setColor(0xFFE53935); c.drawCircle(x, y, r * .38f, fill);
            fill.setColor(0xFFFFFFFF); c.drawCircle(x - r * .1f, y - r * .1f, r * .12f, fill);
        }
    }

    static final class Trail { float x, y; long at; int kind, shot; }
    final ArrayList<Trail> trails = new ArrayList<Trail>();
    static final long TRAIL_MS = 1500;
    void layTrail(Shot s) {
        Trail t = new Trail(); t.x = s.x; t.y = s.y; t.at = SystemClock.uptimeMillis(); t.kind = s.soul; t.shot = s.id; trails.add(t);
        while (trails.size() > 90) trails.remove(0);
    }
    boolean bossTouch(float x, float y, float pad) { return boss != null && boss.alive && bossIn() && Math.abs(x - boss.x) < BOSS_W + pad && Math.abs(y - boss.y) < BOSS_H + pad; }
    float bossTrailCool;
    void stepTrails(float dt, long now) {
        for (int i = trails.size() - 1; i >= 0; i--) if (now - trails.get(i).at > TRAIL_MS) trails.remove(i);
        bossTrailCool -= dt;
        if (trails.isEmpty()) return;
        for (Tank t : new ArrayList<Tank>(tanks)) {
            if (t.player || !t.alive || !ready(t)) continue;
            t.trailCool -= dt; if (t.trailCool > 0) continue;
            for (Trail tr : trails) if (Math.abs(t.x + 1 - tr.x) < 1.4f && Math.abs(t.y + 1 - tr.y) < 1.4f) {
                t.trailCool = .45f;
                if (tr.kind == THUNDER) { if (t.type == 7) fakeHit(t, 1, 0xFF7FE9FF); else shockTank(t); }
                else { if (t.type == 7) fakeHit(t, 1, 0xFFFF7A3D); else burnTank(t); }
                break;
            }
        }
        if (bossTrailCool <= 0) for (Trail tr : trails) if (bossTouch(tr.x, tr.y, .5f)) { bossTrailCool = .45f; hurtBoss(1, tr.kind == THUNDER ? 0xFF7FE9FF : 0xFFFF7A3D); break; }
    }
    void drawTrails(Canvas c, long now) {
        for (int i = 0; i < trails.size(); i++) {
            Trail t = trails.get(i); float age = (now - t.at) / (float) TRAIL_MS; if (age >= 1 || age < 0) continue;
            float a0 = 1 - GameKit.easeIn(age), x = px(t.x), y = py(t.y);
            Trail n = i + 1 < trails.size() && trails.get(i + 1).shot == t.shot ? trails.get(i + 1) : null;
            fill.setShader(null);
            if (t.kind == THUNDER) {
                fill.setColor(GameKit.alpha(0xFF4FD8FF, a0 * .2f)); c.drawCircle(x, y, unit * .9f, fill);
                if (n != null) bolt(c, x, y, px(n.x), py(n.y), unit * .5f, 5, now / 45 + i * 7, 0xFF7FE9FF, Math.max(1.5f, unit * .12f), a0);
                if (motion && rnd.nextFloat() < .15f) { GameKit.Bit b = spark(x, y, 0xFFBFF4FF, 90, 2.2f); b.round = true; b.gravity = 0; b.decay = 4f; }
            } else {
                for (int k = -1; k <= 1; k++) {
                    float fh = unit * (.8f + .3f * (float) Math.sin(now / 55.0 + k * 2 + i)) * a0, fx = x + k * unit * .4f;
                    android.graphics.Path f = GameKit.path(); f.moveTo(fx - unit * .24f, y + unit * .4f); f.quadTo(fx + (float) Math.sin(now / 70.0 + k) * unit * .12f, y + unit * .4f - fh * 1.6f, fx + unit * .24f, y + unit * .4f); f.close();
                    fill.setColor(GameKit.alpha(k == 0 ? 0xFFFF9A3D : 0xFFE5532B, a0 * .9f)); c.drawPath(f, fill);
                }
                if (motion && rnd.nextFloat() < .15f) { GameKit.Bit b = spark(x, y, 0xFFFF9A3D, 30, 2.4f); b.round = true; b.gravity = -.4f; b.decay = 1.6f; }
            }
        }
    }

    static final class Emblem { float x, y; long at; int kind; }
    final ArrayList<Emblem> emblems = new ArrayList<Emblem>();

    static final long HELL_SIGN_MS = 650;
    void drawEmblems(Canvas c, long now) {
        for (int i = emblems.size() - 1; i >= 0; i--) {
            Emblem e = emblems.get(i); float t = now - e.at; boolean hell = e.kind != THUNDER;
            if (t > (hell ? HELL_SIGN_MS + 320 : 480)) { emblems.remove(i); continue; } if (t < 0) continue;
            float in = motion ? GameKit.spring(t / 150f) : 1f, a0 = t > 300 ? 1 - GameKit.ease((t - 300) / 180f) : Math.min(1f, t / 60f);
            if (hell) {
                in = motion ? GameKit.spring(t / 320f) : 1f;
                a0 = t > HELL_SIGN_MS ? 1 - GameKit.ease((t - HELL_SIGN_MS) / 320f) : GameKit.ease(Math.min(1f, t / 260f));
                if (t > HELL_SIGN_MS && motion) in *= 1 + .4f * GameKit.ease((t - HELL_SIGN_MS) / 320f);
            }
            float cx = px(e.x), cy = py(e.y), R = unit * 2.6f * in;
            fill.setShader(null);
            if (e.kind == THUNDER) {
                int sv = c.save(); c.rotate(now / 30f % 360, cx, cy); fill.setColor(GameKit.alpha(0xFFBFF4FF, a0 * .4f));
                for (int k = 0; k < 12; k++) { android.graphics.Path ray = GameKit.path(); ray.moveTo(cx - R * .07f, cy); ray.lineTo(cx, cy - R * (k % 2 == 0 ? 1.3f : 1f)); ray.lineTo(cx + R * .07f, cy); ray.close(); c.drawPath(ray, fill); c.rotate(30, cx, cy); }
                c.restoreToCount(sv);
                fill.setColor(GameKit.alpha(0xFF1E3A8A, a0 * .85f)); c.drawCircle(cx, cy, R * .6f, fill);
                stroke.setStrokeWidth(R * .06f); stroke.setColor(GameKit.alpha(0xFF7FE9FF, a0)); c.drawCircle(cx, cy, R * .6f, stroke);
                tankSign(c, cx, cy, R * .5f, THUNDER, TOP, now, a0, false);
            } else {
                float jx = motion ? (rnd.nextFloat() - .5f) * R * .04f : 0;
                fill.setColor(GameKit.alpha(0xFF1A0508, a0 * .55f)); c.drawCircle(cx + jx, cy, R * .9f, fill);
                tankSign(c, cx + jx, cy, R * .62f, INFERNO, TOP, now, a0, false);
            }
        }
    }

    static final class Sky { float x, y, r; long at; boolean hit; }
    final ArrayList<Sky> skies = new ArrayList<Sky>();
    void stepSkies(long now) {
        for (int i = skies.size() - 1; i >= 0; i--) {
            Sky s = skies.get(i);
            if (!s.hit && now >= s.at) {
                s.hit = true;
                for (Tank t : new ArrayList<Tank>(tanks)) if (!t.player && t.alive && ready(t) && Math.hypot(t.x + 1 - s.x, t.y + 1 - s.y) <= s.r + 1) { if (t.type == 7) fakeHit(t, 3, 0xFF7FE9FF); else shockTank(t); }
                for (Crawler w : crawlers) if (w.alive && Math.hypot(w.x - s.x, w.y - s.y) <= s.r + 1) scrapCrawler(w, now);
                if (bossTouch(s.x, s.y, s.r)) hurtBoss(4, 0xFF7FE9FF);
                burst(px(s.x), py(s.y), 0xFFFFFFFF, 24, 360, 5); burst(px(s.x), py(s.y), 0xFF7FE9FF, 18, 300, 4);
                shake(15); GameKit.rumble(a);
                PixelStackSound.play(PixelStackSound.BOOM, .7f, 1.3f); PixelStackSound.play(PixelStackSound.TING, .5f, 1.9f);
            }
            if (now - s.at > 520) skies.remove(i);
        }
    }
    void drawSkies(Canvas c, long now) {
        for (Sky s : skies) {
            float t = now - s.at, x = px(s.x), y = py(s.y);
            if (t < 0) { float k = 1 + t / 220f; fill.setShader(null); fill.setColor(GameKit.alpha(0xFF0B1430, .2f * Math.max(0f, k))); c.drawCircle(x, y, unit * (s.r + 1.2f), fill); continue; }
            float k = t / 520f, a0 = 1 - GameKit.easeIn(k);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .2f)); c.drawRect(board, fill);
            bolt(c, x + (float) Math.sin(s.at) * unit * 2, board.top - unit, x, y, unit * 2.2f, 9, now / 40, 0xFF9FE8FF, Math.max(2.5f, unit * .28f), a0);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, a0 * .8f)); c.drawCircle(x, y, unit * (1f + .6f * (1 - k)), fill);
            stroke.setStrokeWidth(4 * d * a0); stroke.setColor(GameKit.alpha(0xFF7FE9FF, a0)); c.drawCircle(x, y, unit * s.r * GameKit.ease(Math.min(1f, k * 2.5f)), stroke);
            for (int j = 0; j < 4; j++) { double ang = j * Math.PI / 2 + s.at; float rr = unit * s.r * .9f; bolt(c, x, y, x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr, unit * .6f, 4, now / 40 + j, 0xFF7FE9FF, Math.max(1.5f, unit * .12f), a0 * .9f); }
        }
    }

    static final class Burn { float x, y; long at; }
    final ArrayList<Burn> burns = new ArrayList<Burn>();
    static final long BURN_MS = 4000;
    float bossBurnCool, burnSound;
    void stepBurns(float dt, long now) {
        for (int i = burns.size() - 1; i >= 0; i--) if (now - burns.get(i).at > BURN_MS) burns.remove(i);
        bossBurnCool -= dt;
        if (burns.isEmpty()) return;
        for (Tank t : new ArrayList<Tank>(tanks)) {
            if (t.player || !t.alive || !ready(t)) continue;
            for (Burn z : burns) if (now >= z.at && Math.abs(t.x + 1 - z.x) < 1.6f && Math.abs(t.y + 1 - z.y) < 1.6f) { if (t.type == 7) { t.trailCool -= dt; if (t.trailCool <= 0) { t.trailCool = .5f; fakeHit(t, 1, 0xFFFF7A3D); } } else burnTank(t); break; }
        }
        if (bossBurnCool <= 0) for (Burn z : burns) if (now >= z.at && bossTouch(z.x, z.y, .8f)) { bossBurnCool = .5f; hurtBoss(1, 0xFFFF7A3D); break; }
        burnSound -= dt; if (burnSound <= 0) { burnSound = .4f; PixelStackSound.play(PixelStackSound.FIRE, .14f, .8f + rnd.nextFloat() * .2f); }
    }
    void drawBurns(Canvas c, long now) {
        for (Burn z : burns) {
            float age = now - z.at; if (age < 0) continue;
            float a0 = Math.min(GameKit.clamp(age / 150f), GameKit.clamp((BURN_MS - age) / 600f)), x = px(z.x), y = py(z.y), s = unit * 1.1f;
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFF1A0E08, a0 * .45f)); c.drawOval(GameKit.rf(x - s, y - s * .5f + s * .3f, x + s, y + s * .5f + s * .3f), fill);
            fill.setColor(GameKit.alpha(0xFFFF7A3D, a0 * (.16f + .06f * (float) Math.sin(now / 90.0 + z.x)))); c.drawCircle(x, y, s * 1.2f, fill);
            for (int i = 0; i < 5; i++) {
                float ph = ((now / (500f + i * 37)) + i * .37f + z.y * .1f) % 1f, fx = x + (i - 2) * s * .3f + (float) Math.sin(now / 170.0 + i * 2.1) * s * .08f, fy = y + s * .35f - ph * s * 1.2f, q = s * (.32f - .2f * ph) * a0;
                fill.setColor(GameKit.alpha(ph < .3f ? 0xFFFFF1A8 : ph < .6f ? 0xFFFFB347 : 0xFFE5532B, a0 * (1 - ph * .7f)));
                android.graphics.Path f = GameKit.path(); f.moveTo(fx - q, fy + q); f.quadTo(fx, fy - q * 2.2f, fx + q, fy + q); f.close(); c.drawPath(f, fill);
            }
        }
    }

    void impact(Shot s) {
        long now = SystemClock.uptimeMillis();
        float x = GameKit.clamp(s.x / U) * U, y = GameKit.clamp(s.y / U) * U;
        if (!s.top) { burst(px(x), py(y), s.soul == THUNDER ? 0xFFBFF4FF : 0xFFFF7A3D, 10, 200, 4); return; }
        Emblem e = new Emblem(); e.x = x; e.y = y; e.at = now; e.kind = s.soul; emblems.add(e);
        if (s.soul == THUNDER) { Sky k = new Sky(); k.x = x; k.y = y; k.r = 2.8f; k.at = now + 230; skies.add(k); PixelStackSound.play(PixelStackSound.SPARKLE, .5f, 1.2f); }
        else {
            for (int i = 0; i < 5; i++) { Burn z = new Burn(); double ang = i * Math.PI * 2 / 4; z.x = i == 0 ? x : x + (float) Math.cos(ang) * 1.6f; z.y = i == 0 ? y : y + (float) Math.sin(ang) * 1.6f; z.at = now + HELL_SIGN_MS - 40 + i * 60; burns.add(z); }
            while (burns.size() > 40) burns.remove(0);
            PixelStackSound.play(PixelStackSound.FIRE, .3f, .5f);
            postDelayed(() -> { PixelStackSound.play(PixelStackSound.FIRE, .55f, .6f); PixelStackSound.play(PixelStackSound.BOOM, .4f, .45f); }, HELL_SIGN_MS - 40);
        }
    }

    void shockTank(Tank t) {
        if (t.player || !t.alive || t.shockUntil > 0 || t.type == 7) return;
        long now = SystemClock.uptimeMillis();
        t.shockUntil = now + 1500; t.burnUntil = 0; t.zapAt = now; t.flashAt = now; t.dir = awayDir(t); t.turn = .4f;
        if (t.bonus) { t.bonus = false; drop(); }
        burst(px(t.x + 1), py(t.y + 1), 0xFFBFF4FF, 10, 180, 3);
        PixelStackSound.play(PixelStackSound.BOING, .3f, 1.9f); PixelStackSound.play(PixelStackSound.TING, .25f, 2f);
    }
    int awayDir(Tank t) {
        if (me == null || !me.alive) return t.dir;
        int best = (t.dir + 2) % 4; float bd = -1;
        for (int k = 0; k < 4; k++) { if (!open(t, k)) continue; float dd = Math.abs(t.x + DX[k] - me.x) + Math.abs(t.y + DY[k] - me.y); if (dd > bd) { bd = dd; best = k; } }
        return best;
    }
    final ArrayList<float[]> glows = new ArrayList<float[]>();
    void flareTank(Tank t) {
        long now = SystemClock.uptimeMillis();
        hitNumber(px(t.x + 1), py(t.y) - unit * .3f, Math.max(1, t.hp), 0xFF7FE9FF, true);
        t.shockUntil = 0;
        glows.add(new float[]{t.x + 1, t.y + 1, now});
        float x = px(t.x + 1), y = py(t.y + 1);
        burst(x, y, 0xFFFFFFFF, 20, 320, 5); burst(x, y, 0xFFBFF4FF, 14, 240, 4); burst(x, y, 0xFFFFE9A8, 10, 200, 4);
        shake(8); GameKit.thump(a, .8f);
        PixelStackSound.play(PixelStackSound.TING, .45f, 1.6f); PixelStackSound.play(PixelStackSound.POP, .4f, 1.4f);
        enemyDown(t, true);
    }
    void burnTank(Tank t) {
        if (t.player || !t.alive || t.type == 7 || t.shockUntil > 0) return;
        if (t.burnUntil == 0) { t.burnCool = .5f; PixelStackSound.play(PixelStackSound.BOING, .25f, 1.5f); }
        t.burnUntil = SystemClock.uptimeMillis() + 2000;
    }
    Tank fakeGun;

    void fakeHit(Tank t, int n, int ink) {
        if (fakeGun == null) { fakeGun = new Tank(); fakeGun.player = true; fakeGun.alive = false; }
        Shot f = new Shot(); f.from = fakeGun; f.x = t.x + 1; f.y = t.y + 1; f.dmg = n; f.ink = ink;
        hitTank(t, f);
    }
    void hurtBoss(int n, int ink) {
        if (boss == null || !boss.alive || !bossIn()) return;
        boss.hp = Math.max(1, boss.hp - n); boss.hitAt = SystemClock.uptimeMillis();
        hitNumber(px(boss.x), py(boss.y - BOSS_H), n, ink, n >= 3);
    }
    static final long FORM_MS = 700;

    void transformTank(Tank t, long now) {
        if (t.form == 1 || !t.alive) return;
        t.form = 1; t.formAt = now; t.maxHp += 4; t.hp = Math.min(t.maxHp, t.hp + 4); t.speed *= 1.3f;
        float x = px(t.x + 1), y = py(t.y + 1);
        for (int k = 0; k < 6; k++) { GameKit.Bit b = spark(x, y, k % 2 == 0 ? 0xFF4A4F5C : 0xFF8A93A6, 260, unit / d * .5f); b.gravity = 1f; b.decay = 1f; }
        burst(x, y, 0xFFFF5A1F, 22, 280, 5); rings.add(new float[]{x, y, now, 1.8f});
        shake(10); GameKit.rumble(a);
        PixelStackSound.play(PixelStackSound.BOOM, .55f, .5f); PixelStackSound.play(PixelStackSound.FIRE, .45f, .7f);
    }

    void enemyDown(Tank t, boolean light) {
        if (light) t.alive = false; else explode(t, false);
        if (!t.summoned) killed++;
        int pts = 100 * (t.type + 1) * (1 + t.tier) * (t.type == 7 && t.form == 1 ? 2 : 1); addScore(pts);
        pop("+" + (SystemClock.uptimeMillis() < doubleUntil ? pts * 2 : pts), px(t.x + 1), py(t.y), night ? 0xFFFFFFFF : 0xFF14161C, 15);
        if (rnd.nextFloat() < .1f) drop();
        kills++;
        if (t.type == 5 && !t.freed) { t.freed = true; freeCharges(t); }
        if (t.type == 6) releaseCrawlers(t);
        if (rank < RANKS.length && xp() >= RANKS[rank]) rank++;
    }

    void stepSoul(float dt, long now) {
        soulSync();
        if (me != null && me.alive) { lastX = me.x; lastY = me.y; }
        stepCores(now);
        int th = thunder(), inf = inferno();
        boolean live = me != null && me.alive && state == PLAY && ready(me);
        if (th >= 5 && !capReady && now - capAt > (th >= 9 ? 10000 : 20000)) { capReady = true; if (me != null) burst(px(me.x + 1), py(me.y + 1), 0xFF7FE9FF, 10, 160, 3); PixelStackSound.play(PixelStackSound.TING, .35f, 1.5f); }
        if (th >= 3 && live) {
            zapCool -= dt;
            if (zapCool <= 0) {
                Tank best = null; float bd = 14f;
                for (Tank t : tanks) if (!t.player && t.alive && ready(t)) { float dd = (float) Math.hypot(t.x - me.x, t.y - me.y); if (dd < bd) { bd = dd; best = t; } }
                boolean onBoss = boss != null && boss.alive && bossIn() && Math.hypot(boss.x - me.x - 1, boss.y - me.y - 1) < Math.min(bd, 14f);
                if (best == null && !onBoss) zapCool = .5f;
                else {
                    zapCool = th >= 5 ? 3.5f : 7f; zapAt = now; zapX0 = me.x + 1; zapY0 = me.y + 1;
                    if (onBoss) { zapX1 = boss.x; zapY1 = boss.y; hurtBoss(3, 0xFF7FE9FF); }
                    else { zapX1 = best.x + 1; zapY1 = best.y + 1; best.stunUntil = Math.max(best.stunUntil, now + 600); fakeHit(best, 2, 0xFF7FE9FF); }
                    shake(5); PixelStackSound.play(PixelStackSound.TING, .45f, 1.8f); PixelStackSound.play(PixelStackSound.SHOT, .3f, 1.6f);
                }
            }
        }
        if (th >= 4 && live) {
            orb += dt * 3.2f;
            for (int k = 0; k < (th >= 7 ? 2 : 1); k++) {
                double ang = orb + k * Math.PI; float ox = me.x + 1 + (float) Math.cos(ang) * 2.6f, oy = me.y + 1 + (float) Math.sin(ang) * 2.6f;
                for (Tank t : new ArrayList<Tank>(tanks)) { if (t.player || !t.alive || !ready(t)) continue; t.orbCool -= dt / (th >= 7 ? 2 : 1); if (t.orbCool <= 0 && Math.abs(t.x + 1 - ox) < 1.3f && Math.abs(t.y + 1 - oy) < 1.3f) { t.orbCool = .6f; fakeHit(t, 1, 0xFF7FE9FF); burst(px(ox), py(oy), 0xFFBFF4FF, 6, 140, 3); } }
                for (Crawler w : crawlers) if (w.alive && Math.hypot(w.x - ox, w.y - oy) < 1.1f) scrapCrawler(w, now);
            }
        }
        if (inf >= 3 && live) {
            ramCool -= dt;
            if (ramCool <= 0) for (Tank t : new ArrayList<Tank>(tanks)) {
                if (t.player || !t.alive || !ready(t)) continue;
                if (Math.abs(t.x - me.x) < 2.25f && Math.abs(t.y - me.y) < 2.25f) { ramCool = .6f; eatAt = now; fakeHit(t, 1, 0xFFFF4D6D); burst(px((t.x + me.x) / 2 + 1), py((t.y + me.y) / 2 + 1), 0xFFFF7A3D, 8, 160, 3); PixelStackSound.play(PixelStackSound.THUD, .4f, .9f); GameKit.thump(a, .5f); break; }
            }
        }
        if (inf >= 5 && live) {
            novaCool -= dt;
            if (novaCool <= 0) {
                boolean any = false; for (Tank t : tanks) if (!t.player && t.alive && ready(t) && Math.hypot(t.x - me.x, t.y - me.y) < 6f) any = true;
                if (!any) novaCool = .5f;
                else {
                    novaCool = inf >= 7 ? 4f : 8f; novaAt = now;
                    for (Tank t : new ArrayList<Tank>(tanks)) if (!t.player && t.alive && ready(t) && Math.hypot(t.x - me.x, t.y - me.y) < 6f) { fakeHit(t, 2, 0xFFE5173F); if (t.alive && inf >= 6) burnTank(t); }
                    for (Crawler w : crawlers) if (w.alive && Math.hypot(w.x - me.x - 1, w.y - me.y - 1) < 6f) eatCrawler(w, now);
                    rings.add(new float[]{px(me.x + 1), py(me.y + 1), now, 3.4f});
                    shake(12); GameKit.rumble(a); PixelStackSound.play(PixelStackSound.BOOM, .55f, .45f); PixelStackSound.play(PixelStackSound.FIRE, .4f, .8f);
                }
            }
        }
        stepCharges(dt, now); stepCrawlers(dt, now); stepTrails(dt, now); stepSkies(now); stepBurns(dt, now);
        stepWays(dt, now);
    }

    void drawTankSoul(Canvas c, float cx, float cy, float s, float wide, long now, float fade, boolean under) {
        int th = thunder(), inf = inferno();
        float ab = now - absorbAt < 750 ? 1 - (now - absorbAt) / 750f : 0f;
        fill.setShader(null);
        if (under) {
            if (inf >= 3) for (int side = -1; side <= 1; side += 2) {
                float fl = s * (.16f + .02f * inf) * (motion ? .75f + .35f * (float) Math.sin(now / 45.0 + side) : 1f), fx = cx + side * s * .16f, fy = cy + s * .36f;
                android.graphics.Path f = GameKit.path(); f.moveTo(fx - s * .06f, fy); f.quadTo(fx, fy + fl * 1.8f, fx + s * .06f, fy); f.close();
                fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawPath(f, fill);
                fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); f = GameKit.path(); f.moveTo(fx - s * .03f, fy); f.quadTo(fx, fy + fl, fx + s * .03f, fy); f.close(); c.drawPath(f, fill);
            }
            if (inf >= 5) { fill.setColor(GameKit.alpha(0xFF1A0508, fade)); for (int side = -1; side <= 1; side += 2) for (int k = -1; k <= 1; k++) { android.graphics.Path sp = GameKit.path(); float bx = cx + side * s * (wide + .02f), by = cy + k * s * .2f; sp.moveTo(bx, by - s * .06f); sp.lineTo(bx + side * s * .12f, by); sp.lineTo(bx, by + s * .06f); sp.close(); c.drawPath(sp, fill); } }
            return;
        }
        if (th >= 1) {
            float q = motion ? (float) (.5 + .5 * Math.sin(now / 200.0)) : .5f;
            for (int side = -1; side <= 1; side += 2) { fill.setColor(GameKit.alpha(0xFF7FE9FF, fade * (.35f + .25f * q))); c.drawCircle(cx + side * s * .2f, cy - s * .3f, s * .07f, fill); fill.setColor(GameKit.alpha(0xFFE6FAFF, fade)); c.drawCircle(cx + side * s * .2f, cy - s * .3f, s * .035f, fill); }
            if (th >= 3) { stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(0xFF4FD8FF, fade * (.5f + .4f * q))); android.graphics.Path ci = GameKit.path(); ci.moveTo(cx - s * wide * .8f, cy + s * .25f); ci.lineTo(cx - s * wide * .8f, cy - s * .05f); ci.lineTo(cx - s * .1f, cy - s * .05f); ci.moveTo(cx + s * wide * .8f, cy + s * .25f); ci.lineTo(cx + s * wide * .8f, cy + s * .05f); ci.lineTo(cx + s * .1f, cy + s * .05f); c.drawPath(ci, stroke); }
            if (th >= 2) for (int side = -1; side <= 1; side += 2) { float kx = cx + side * s * .2f, ky = cy + s * .22f; fill.setColor(GameKit.alpha(0xFF8A97B0, fade)); c.drawCircle(kx, ky, s * .06f, fill); stroke.setStrokeWidth(Math.max(1f, s * .02f)); stroke.setColor(GameKit.alpha(0xFF4FD8FF, fade)); c.drawCircle(kx, ky, s * .045f, stroke); c.drawCircle(kx, ky, s * .025f, stroke); }
            if (th >= 5 && motion) bolt(c, cx - s * .2f, cy + s * .22f, cx + s * .2f, cy + s * .22f, s * .08f, 4, now / 50, 0xFF7FE9FF, Math.max(1f, s * .02f), fade);
            if (th >= TOP) for (int k = -1; k <= 1; k++) { android.graphics.Path z = GameKit.path(); float zx = cx + k * s * .09f, zy = cy - s * .08f; z.moveTo(zx + s * .02f, zy - s * .1f); z.lineTo(zx - s * .03f, zy); z.lineTo(zx + s * .01f, zy); z.lineTo(zx - s * .02f, zy + s * .08f); z.lineTo(zx + s * .04f, zy - s * .02f); z.lineTo(zx, zy - s * .02f); z.close(); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawPath(z, fill); }
        }
        if (inf >= 1) {
            float jy = cy - s * .33f, jw = s * (wide - .02f), jh = s * (.08f + .004f * inf);
            android.graphics.Path m = GameKit.path(); m.moveTo(cx - jw, jy); m.lineTo(cx + jw, jy); m.quadTo(cx, jy + jh * 2.4f, cx - jw, jy); m.close();
            fill.setColor(GameKit.alpha(0xFF2A0508, fade)); c.drawPath(m, fill);
            if (inf >= 5) { fill.setColor(GameKit.alpha(0xFFFF5A1F, fade * .5f)); c.drawOval(GameKit.rf(cx - jw * .4f, jy + jh * .3f, cx + jw * .4f, jy + jh * .9f), fill); }
            int n = Math.min(12, 2 + inf), up = (n + 1) / 2, dn = n / 2; fill.setColor(GameKit.alpha(0xFFFFFFFF, fade));
            for (int i = 0; i < up; i++) { float t = (i + .5f) / up, tx = cx - jw + 2 * jw * t, bw = jw / up * .8f; android.graphics.Path tt = GameKit.path(); tt.moveTo(tx - bw, jy); tt.lineTo(tx + bw, jy); tt.lineTo(tx, jy + jh * .8f); tt.close(); c.drawPath(tt, fill); }
            for (int i = 0; i < dn; i++) { float t = .15f + .7f * (i + .5f) / dn, tx = cx - jw + 2 * jw * t, by = jy + 2 * (1 - t) * t * jh * 2.4f, bw = jw / dn * .55f; android.graphics.Path tt = GameKit.path(); tt.moveTo(tx - bw, by); tt.lineTo(tx + bw, by); tt.lineTo(tx, by - jh * .6f); tt.close(); c.drawPath(tt, fill); }
            int iris = inf >= 8 ? 0xFFFF3D2E : inf >= 4 ? 0xFFFF9A3D : 0xFFFFE066; float q = motion ? (float) (.5 + .5 * Math.sin(now / 110.0)) : .5f;
            for (int side = -1; side <= 1; side += 2) { float ex = cx + side * s * .19f, ey = cy - s * .2f; fill.setColor(GameKit.alpha(iris, fade * (.2f + .02f * inf) * (.7f + .6f * q))); c.drawCircle(ex, ey, s * .1f, fill); android.graphics.Path e = GameKit.path(); e.moveTo(ex + side * s * .09f, ey - s * .05f); e.lineTo(ex - side * s * .08f, ey + s * .03f); e.quadTo(ex, ey + s * .06f, ex + side * s * .09f, ey - s * .05f); e.close(); fill.setColor(GameKit.alpha(iris, fade)); c.drawPath(e, fill); }
            if (inf >= 6) { float q2 = (float) (.5 + .5 * Math.sin(now / 130.0)); stroke.setStrokeWidth(Math.max(1f, s * .02f)); stroke.setColor(GameKit.alpha(0xFFFF7A3D, fade * (.5f + .4f * q2))); android.graphics.Path cr = GameKit.path(); cr.moveTo(cx - s * wide * .7f, cy + s * .05f); cr.lineTo(cx - s * wide * .5f, cy + s * .14f); cr.lineTo(cx - s * wide * .62f, cy + s * .26f); cr.moveTo(cx + s * wide * .72f, cy); cr.lineTo(cx + s * wide * .52f, cy + s * .12f); cr.lineTo(cx + s * wide * .64f, cy + s * .24f); c.drawPath(cr, stroke); }
        }
        if (now - eatAt < 300) { float k = (float) Math.sin(Math.PI * (now - eatAt) / 300f); fill.setColor(GameKit.alpha(0xFFFF7A3D, fade * k * .5f)); c.drawCircle(cx, cy - s * .36f, s * .2f * k, fill); }
        if (ab > 0) { stroke.setStrokeWidth(2.5f * d); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * ab)); c.drawCircle(cx, cy, s * 1.1f * ab, stroke); fill.setColor(GameKit.alpha(0xFF7FE9FF, fade * ab * .35f)); c.drawCircle(cx, cy, s * .5f, fill); }
    }

    void drawSoulAround(Canvas c, long now) {
        if (me == null || !me.alive) return;
        int th = thunder(), inf = inferno(); float cx = px(me.x + 1), cy = py(me.y + 1), s = unit * 2;
        fill.setShader(null);
        if (th >= 9) { float sp = now / 900f; stroke.setStrokeWidth(Math.max(1.5f, s * .03f)); stroke.setColor(GameKit.alpha(0xFF7FE9FF, .5f)); for (int k = 0; k < 3; k++) c.drawArc(GameKit.rf(cx - s * .86f, cy - s * .86f, cx + s * .86f, cy + s * .86f), sp * 360 + k * 120, 70, false, stroke); }
        if (inf >= TOP) for (int i = 0; i < 12; i++) { double ang = i * Math.PI / 6 + now / 600.0; float rr = s * .7f, fl = s * (.14f + .06f * (float) Math.sin(now / 55.0 + i * 1.7)), fx = cx + (float) Math.cos(ang) * rr, fy = cy + (float) Math.sin(ang) * rr; android.graphics.Path f = GameKit.path(); f.moveTo(fx - s * .06f, fy); f.quadTo(fx + (float) Math.cos(ang) * fl, fy + (float) Math.sin(ang) * fl - fl, fx + s * .06f, fy); f.close(); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFE5532B : 0xFFFFB347, .8f)); c.drawPath(f, fill); }
        if (inf >= 8 && motion && rnd.nextFloat() < .12f) { GameKit.Bit b = spark(cx + (rnd.nextFloat() - .5f) * s * .7f, cy, rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A, 30, 2.4f); b.round = true; b.gravity = -.4f; b.decay = 1.6f; }
        if (th >= 4) for (int k = 0; k < (th >= 7 ? 2 : 1); k++) { double ang = orb + k * Math.PI; float ox = px(me.x + 1 + (float) Math.cos(ang) * 2.6f), oy = py(me.y + 1 + (float) Math.sin(ang) * 2.6f); fill.setColor(0x554FD8FF); c.drawCircle(ox, oy, unit * .7f, fill); fill.setColor(0xFFBFF4FF); c.drawCircle(ox, oy, unit * .38f, fill); fill.setColor(0xFFFFFFFF); c.drawCircle(ox, oy, unit * .18f, fill); if (motion) bolt(c, ox, oy, ox + (float) Math.cos(now / 70.0) * unit * .7f, oy + (float) Math.sin(now / 70.0) * unit * .7f, unit * .2f, 3, now / 40 + k, 0xFF7FE9FF, Math.max(1f, unit * .06f), .9f); }
        if (th >= 1 && capReady) { float q = (float) (.5 + .5 * Math.sin(now / 300.0)); stroke.setStrokeWidth(Math.max(1.2f, s * .025f)); stroke.setColor(GameKit.alpha(0xFF7FE9FF, .3f + .25f * q)); c.drawCircle(cx, cy, s * .68f, stroke); }
        if (now - capAt < 500) { float k = (now - capAt) / 500f; stroke.setStrokeWidth(4 * d * (1 - k)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, 1 - k)); c.drawCircle(cx, cy, s * (.6f + .8f * k), stroke); }
        if (now - zapAt < 280) bolt(c, px(zapX0), py(zapY0), px(zapX1), py(zapY1), unit * .9f, 7, now / 35, 0xFF7FE9FF, Math.max(1.5f, unit * .14f), 1 - (now - zapAt) / 280f);
        if (now - novaAt < 520) { float k = (now - novaAt) / 520f, r = unit * 6f * GameKit.ease(k); fill.setColor(GameKit.alpha(0xFFFF5A1F, (1 - k) * .25f)); c.drawCircle(cx, cy, r, fill); stroke.setStrokeWidth(unit * .8f * (1 - k)); stroke.setColor(GameKit.alpha(0xFFE5173F, (1 - k) * .8f)); c.drawCircle(cx, cy, r, stroke); }
        float ct = (now - soulAt) / 900f;
        if (ct >= 0 && ct < 1 && soul != NONE) { float a0 = 1 - GameKit.easeIn(ct); if (soul == THUNDER) { bolt(c, cx, board.top - unit, cx, cy, unit * 1.4f, 8, now / 40, 0xFF9FE8FF, Math.max(2f, unit * .2f), a0); } else { for (int i = -2; i <= 2; i++) { android.graphics.Path f = GameKit.path(); float fx = cx + i * unit * .5f, h = unit * 5f * (float) Math.sin(Math.PI * Math.min(1f, ct * 1.4f)) * (1 - Math.abs(i) * .2f); f.moveTo(fx - unit * .4f, cy + unit); f.quadTo(fx, cy + unit - h, fx + unit * .4f, cy + unit); f.close(); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFE5532B : 0xFFFFB347, a0 * .8f)); c.drawPath(f, fill); } } stroke.setStrokeWidth(3 * d * a0); stroke.setColor(GameKit.alpha(soulColour(), a0)); c.drawCircle(cx, cy, unit * (1f + 3f * GameKit.ease(ct)), stroke); }
    }

    void tankSign(Canvas c, float x, float y, float r, int kind, int lv, long now, float fade, boolean small) {
        fill.setShader(null); int tier = lv >= TOP ? 3 : lv >= 7 ? 2 : lv >= 4 ? 1 : 0;
        if (kind == THUNDER) {
            if (tier >= 2) { fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); for (int side = -1; side <= 1; side += 2) for (int f = 0; f < 3; f++) { android.graphics.Path w = GameKit.path(); float wy = y - r * .1f + f * r * .18f, len = r * (1f - f * .2f); w.moveTo(x + side * r * .25f, wy); w.lineTo(x + side * (r * .25f + len), wy - r * .25f + f * r * .08f); w.lineTo(x + side * (r * .25f + len * .8f), wy + r * .08f); w.close(); c.drawPath(w, fill); } }
            if (tier >= 1) { stroke.setStrokeWidth(Math.max(1f, r * .1f)); stroke.setColor(GameKit.alpha(0xFF4FD8FF, fade)); c.drawCircle(x, y, r * .62f, stroke); }
            if (tier == 3) { fill.setColor(GameKit.alpha(0xFFFFE066, fade)); android.graphics.Path cr = GameKit.path(); float cw = r * .7f, cb = y - r * .7f, ch = r * .4f; cr.moveTo(x - cw / 2, cb); cr.lineTo(x - cw / 2, cb - ch * .7f); cr.lineTo(x - cw / 4, cb - ch * .3f); cr.lineTo(x, cb - ch); cr.lineTo(x + cw / 4, cb - ch * .3f); cr.lineTo(x + cw / 2, cb - ch * .7f); cr.lineTo(x + cw / 2, cb); cr.close(); c.drawPath(cr, fill); }
            android.graphics.Path z = GameKit.path(); z.moveTo(x + r * .14f, y - r * .6f); z.lineTo(x - r * .28f, y + r * .06f); z.lineTo(x + r * .02f, y + r * .06f); z.lineTo(x - r * .16f, y + r * .6f); z.lineTo(x + r * .3f, y - r * .1f); z.lineTo(x, y - r * .1f); z.close();
            fill.setColor(GameKit.alpha(small ? 0xFFFFFFFF : 0xFFFFE066, fade)); c.drawPath(z, fill);
        } else {
            if (tier <= 1) {
                android.graphics.Path f = GameKit.path(); f.moveTo(x - r * .4f, y + r * .45f); f.cubicTo(x - r * .55f, y, x - r * .1f, y - r * .2f, x, y - r * .7f); f.cubicTo(x + r * .1f, y - r * .2f, x + r * .55f, y, x + r * .4f, y + r * .45f); f.close();
                fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawPath(f, fill);
                fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawCircle(x, y + r * .2f, r * .2f, fill);
                if (tier == 1) { fill.setColor(GameKit.alpha(0xFFE5173F, fade)); for (int side = -1; side <= 1; side += 2) { android.graphics.Path h = GameKit.path(); h.moveTo(x + side * r * .3f, y - r * .1f); h.quadTo(x + side * r * .7f, y - r * .2f, x + side * r * .62f, y - r * .7f); h.quadTo(x + side * r * .5f, y - r * .3f, x + side * r * .2f, y); h.close(); c.drawPath(h, fill); } }
                return;
            }
            if (tier == 3) for (int i = 0; i < 8; i++) { double ang = -Math.PI / 2 + (i - 3.5) * .3; float fx = x + (float) Math.cos(ang) * r * .55f, fy = y + (float) Math.sin(ang) * r * .55f, fl = r * (.3f + .1f * (float) Math.sin(now / 60.0 + i)); android.graphics.Path f = GameKit.path(); f.moveTo(fx - r * .1f, fy); f.quadTo(fx, fy - fl * 1.6f, fx + r * .1f, fy); f.close(); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFFFF7A3D : 0xFFFFC94A, fade)); c.drawPath(f, fill); }
            fill.setColor(GameKit.alpha(0xFF2A0508, fade)); for (int side = -1; side <= 1; side += 2) { android.graphics.Path h = GameKit.path(); h.moveTo(x + side * r * .25f, y - r * .3f); h.cubicTo(x + side * r * .6f, y - r * .5f, x + side * r * .85f, y - r * .55f, x + side * r * .8f, y - r * 1f); h.cubicTo(x + side * r * .65f, y - r * .55f, x + side * r * .45f, y - r * .35f, x + side * r * .4f, y - r * .1f); h.close(); c.drawPath(h, fill); }
            fill.setColor(GameKit.alpha(0xFFF3E5C8, fade)); c.drawCircle(x, y - r * .05f, r * .48f, fill); box.set(x - r * .28f, y + r * .2f, x + r * .28f, y + r * .5f); c.drawRoundRect(box, r * .08f, r * .08f, fill);
            for (int side = -1; side <= 1; side += 2) { android.graphics.Path e = GameKit.path(); float ex = x + side * r * .18f, ey = y - r * .05f; e.moveTo(ex - side * r * .14f, ey - r * .08f); e.lineTo(ex + side * r * .12f, ey + r * .04f); e.quadTo(ex, ey + r * .12f, ex - side * r * .14f, ey - r * .08f); e.close(); fill.setColor(GameKit.alpha(tier == 3 ? 0xFFFF5A1F : 0xFF14060A, fade)); c.drawPath(e, fill); }
            stroke.setStrokeWidth(Math.max(1f, r * .06f)); stroke.setColor(GameKit.alpha(0xFF14060A, fade)); android.graphics.Path m = GameKit.path(); m.moveTo(x - r * .28f, y + r * .24f); m.quadTo(x, y + r * .42f, x + r * .28f, y + r * .24f); c.drawPath(m, stroke);
            for (int i = -2; i <= 2; i++) c.drawLine(x + i * r * .1f, y + r * .27f + (2 - Math.abs(i)) * r * .03f, x + i * r * .1f, y + r * .42f, stroke);
        }
    }

    void drawMeters(Canvas c, float l, float y, float r, float fade) { float mid = (l + r) / 2f, gap = 12 * d; rankMeter(c, l, y, mid - gap / 2, fade); soulMeter(c, mid + gap / 2, y, r, fade); }
    void rankMeter(Canvas c, float l, float y, float r, float fade) {
        int from = rank == 0 ? 0 : RANKS[rank - 1], to = rank < RANKS.length ? RANKS[rank] : from;
        float k = to > from ? Math.max(0f, Math.min(1f, (xp() - from) / (float) (to - from))) : 1f;
        long now = SystemClock.uptimeMillis();
        if (now - rankAt < 700) { float q = 1 - (now - rankAt) / 700f; fill.setShader(null); fill.setColor(GameKit.alpha(0xFF4F8DF5, fade * q * .4f)); c.drawCircle(l + 8 * d, y, (8 + 10 * (1 - q)) * d, fill); }
        badge(c, l + 8 * d, y, String.valueOf(rank + 1), rank >= RANKS.length ? 0xFFE0A800 : night ? 0xFF4F8DF5 : GameKit.BLUE, fade);
        meter(c, l + 20 * d, y, r, k, rank >= RANKS.length ? 0xFFFFC531 : night ? GameKit.LIGHT : 0xFF4F8DF5, fade);
    }
    void soulMeter(Canvas c, float l, float y, float r, float fade) {
        float ix = l + 8 * d; long now = SystemClock.uptimeMillis();
        fill.setShader(null);
        if (soul == NONE) {
            fill.setColor(GameKit.alpha(0xFF4FD8FF, fade * .8f)); c.drawArc(GameKit.rf(ix - 8 * d, y - 8 * d, ix + 8 * d, y + 8 * d), 90, 180, true, fill);
            fill.setColor(GameKit.alpha(0xFFFF5A1F, fade * .8f)); c.drawArc(GameKit.rf(ix - 8 * d, y - 8 * d, ix + 8 * d, y + 8 * d), 270, 180, true, fill);
            meter(c, l + 20 * d, y, r, 0, 0, fade);
            return;
        }
        if (now - soulAt < 700) { float q = 1 - (now - soulAt) / 700f; fill.setColor(GameKit.alpha(soulColour(), fade * q * .45f)); c.drawCircle(ix, y, (8 + 10 * (1 - q)) * d, fill); }
        badge(c, ix, y, String.valueOf(soulLv), soul == THUNDER ? 0xFF1E88E5 : 0xFFE5532B, fade);
        if (soul == THUNDER) { android.graphics.Path z = GameKit.path(); float zx = ix + 7 * d, zy = y - 9 * d; z.moveTo(zx + 1.5f * d, zy - 4 * d); z.lineTo(zx - 2 * d, zy + .5f * d); z.lineTo(zx, zy + .5f * d); z.lineTo(zx - 1.5f * d, zy + 4 * d); z.lineTo(zx + 2.2f * d, zy - 1 * d); z.lineTo(zx, zy - 1 * d); z.close(); fill.setColor(GameKit.alpha(0xFFFFE066, fade)); c.drawPath(z, fill); }
        else { android.graphics.Path f = GameKit.path(); float fx = ix + 7 * d, fy = y - 6 * d; f.moveTo(fx - 3 * d, fy); f.quadTo(fx, fy - 8 * d, fx + 3 * d, fy); f.close(); fill.setColor(GameKit.alpha(0xFFFFB347, fade)); c.drawPath(f, fill); }
        float want = soulProgress();
        if (want < soulShown || !motion) soulShown = want; else soulShown += (want - soulShown) * .14f;
        float k = soulShown;
        if (now - soulDropAt < 900) k = GameKit.clamp(k + (1 - GameKit.ease((now - soulDropAt) / 900f)) * .5f);
        meter(c, l + 20 * d, y, r, k, soulColour(), fade);
        if (now - gainAt < 600 && soulLv < TOP) { float q = 1 - (now - gainAt) / 600f, ex = l + 20 * d + (r - l - 20 * d) * k; fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * q)); c.drawCircle(ex, y, (3 + 4 * q) * d, fill); fill.setColor(GameKit.alpha(soulColour(), fade * q * .5f)); c.drawCircle(ex, y, (6 + 6 * (1 - q)) * d, fill); }
        if (soulLv < TOP) notches(c, l + 20 * d, y, r, soulStep(soulLv), fade);
        if (soulLv >= TOP && motion) { float ph = (now % 1600) / 1600f, sx = l + 20 * d + (r - l - 20 * d) * ph; fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * (float) Math.sin(Math.PI * ph))); c.drawCircle(sx, y, 3 * d, fill); }
        if (soul != NONE && chargeReadyAt > 0) { float ck = 1 - GameKit.clamp((chargeReadyAt - now) / (float) chargeMs()); stroke.setStrokeWidth(1.6f * d); stroke.setColor(GameKit.alpha(soulColour(), fade * (ck >= 1 ? 1f : .5f))); c.drawArc(GameKit.rf(ix - 10.5f * d, y - 10.5f * d, ix + 10.5f * d, y + 10.5f * d), -90, 360 * ck, false, stroke); }
    }

    static final int STAR = 0, SHIELD = 1, FREEZE = 2, BLAST = 3, FORT = 4, LIFE = 5, ROCKET = 6, DOUBLE = 7, LASER = 8, DRONE = 9, WAVE = 10, NUKE = 11;

    static final int[] MY_BODY = {0xFF1846B8, 0xFF1E88E5, 0xFF5B4BDB, 0xFFD3DEF2, 0xFFFFC531};
    static final int[] MY_SHOT = {0xFFFFE27A, 0xFF5CE1FF, 0xFFB79CFF, 0xFFFF9A3D, 0xFFFFD23F};
    static final int[] MY_DMG = {1, 1, 2, 3, 4};
    int shownPower; long morphAt = -10000;

    long nukeAt = -1, armourFrom = -1, armourUntil = -1; boolean nukeHit; float heat;
    static final long NUKE_FALL = 1100, ARMOUR_MS = 3300;
    final ArrayList<float[]> fires = new ArrayList<float[]>();
    Tank nukeGun;

    static final int[][] GUIDE = {{STAR, SHIELD, LIFE, DOUBLE}, {ROCKET, LASER, DRONE, FORT, FREEZE}, {BLAST, WAVE, NUKE}};
    @Override int[][] guide() { return GUIDE; }
    @Override int tierOf(int kind) { for (int t = 0; t < GUIDE.length; t++) for (int k : GUIDE[t]) if (k == kind) return t; return 0; }
    @Override String abilityName(int kind) {
        switch (kind) {
            case STAR: return I18n.t("Power up!"); case SHIELD: return I18n.t("Shield"); case LIFE: return I18n.t("Extra life"); case DOUBLE: return I18n.t("Double points");
            case ROCKET: return I18n.t("Rockets!"); case LASER: return I18n.t("Laser!"); case DRONE: return I18n.t("Helper drone!"); case FORT: return I18n.t("Fortress"); case FREEZE: return I18n.t("Freeze");
            case BLAST: return I18n.t("Airstrike"); case WAVE: return I18n.t("Shockwave!"); default: return I18n.t("Nuke");
        }
    }
    @Override String abilityHint(int kind) {
        switch (kind) {
            case STAR: return I18n.t("Levels up your tank"); case SHIELD: return I18n.t("Blocks every hit"); case LIFE: return I18n.t("One more try"); case DOUBLE: return I18n.t("Score counts twice");
            case ROCKET: return I18n.t("Shots chase tanks"); case LASER: return I18n.t("Cuts through everything"); case DRONE: return I18n.t("A helper that shoots"); case FORT: return I18n.t("Steel walls for the base"); case FREEZE: return I18n.t("Stops every enemy");
            case BLAST: return I18n.t("Missiles on every tank"); case WAVE: return I18n.t("Blasts and stuns all tanks"); default: return I18n.t("Wipes the whole map");
        }
    }
    @Override int abilityColour(int kind) { return kind == NUKE ? 0xFFFF7A3D : kind == FREEZE ? 0xFF4FB3F5 : kind == BLAST ? ORANGE : (night ? GameKit.LIGHT : GameKit.BLUE); }
    @Override void abilityIcon(Canvas c, int kind, float x, float y, float r, float fade) { prizeIcon(c, kind, x, y, r, fade); text.setTextAlign(android.graphics.Paint.Align.CENTER); }
    @Override void useAbility(int kind) {
        long now = SystemClock.uptimeMillis();
        float wx = px(me != null ? me.x + 1 : 13), wy = py(me != null ? me.y - .6f : 22);
        switch (kind) {
            case NUKE: nuke(); return;
            case FREEZE: freezeAt = now; freezeUntil = now + 8000; PixelStackSound.play(PixelStackSound.SPARKLE, .6f, .7f); PixelStackSound.play(PixelStackSound.TING, .4f, 1.5f); shake(6); GameKit.thump(a, .7f); break;
            case BLAST: airstrike(); break;
            default: shockwave();
        }
        pop(abilityName(kind), wx, wy, night ? GameKit.LIGHT : GameKit.BLUE, 18);
    }

    void nuke() {
        long now = SystemClock.uptimeMillis();
        if (state != PLAY || (nukeAt > 0 && now - nukeAt < 3000)) { stash(NUKE); return; }
        nukeAt = now; nukeHit = false; armourFrom = now; armourUntil = now + ARMOUR_MS; heat = 0;
        if (me != null) me.shieldUntil = Math.max(me.shieldUntil, armourUntil);
        pop(I18n.t("Nuke"), board.centerX(), board.centerY() - unit * 4, 0xFFFF7A3D, 26);
        PixelStackSound.play(PixelStackSound.RISE, .5f, .6f);
        PixelStackSound.play(PixelStackSound.TING, .4f, .7f); postDelayed(() -> PixelStackSound.play(PixelStackSound.TING, .4f, .9f), 220);
        GameKit.thump(a, .7f);
    }

    void nukeBlast() {
        long now = SystemClock.uptimeMillis();
        nukeHit = true;
        float cx = board.centerX(), cy = board.centerY();
        for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) if (wall[y][x] != 0) {
            if ((x + y * 3) % 4 == 0) { GameKit.Bit b = spark(px(x + .5f), py(y + .5f), wall[y][x] == 2 ? steel : (x % 2 == 0 ? brick : brickDark), 260, unit / d * .4f); b.gravity = 1f; b.decay = 1.1f; b.vx += (px(x + .5f) - cx) * 1.6f; b.vy += (py(y + .5f) - cy) * 1.6f; }
            wall[y][x] = 0;
        }
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) bush[y][x] = false;
        fortressUntil = 0; wallsDirty = true;
        for (Tank t : new ArrayList<Tank>(tanks)) {
            if (t.player || !t.alive) continue;
            explode(t, false); killed++; kills++; addScore(100 * (t.type + 1) * (1 + t.tier));
            if (t.type == 5 && !t.freed) { t.freed = true; freeCharges(t); }
        }
        while (rank < RANKS.length && xp() >= RANKS[rank]) rank++;
        for (Shot s : shots) if (!s.from.player) kill(s);
        if (boss != null && boss.alive && bossIn()) { boss.hp = Math.max(1, boss.hp - Math.max(12, Math.round(boss.maxHp * .45f))); boss.hitAt = now; }
        fires.clear();
        for (int tries = 0; tries < 400 && fires.size() < 15; tries++) {
            float fx = 1.5f + rnd.nextInt(12) * 2 + (rnd.nextFloat() - .5f), fy = 1.5f + rnd.nextInt(12) * 2 + (rnd.nextFloat() - .5f);
            if (water[(int) (fy / 2)][(int) (fx / 2)] || Math.hypot(fx - 13, fy - 25) < 4.5 || (me != null && Math.hypot(fx - me.x - 1, fy - me.y - 1) < 3.2)) continue;
            boolean close = false; for (float[] f : fires) if (Math.hypot(f[0] - fx, f[1] - fy) < 2.6) close = true;
            if (close) continue;
            fires.add(new float[]{fx, fy, now + rnd.nextInt(300), 11000 + rnd.nextInt(5000)});
        }
        burst(cx, cy, 0xFFFFE08A, 40, 520, 8); burst(cx, cy, 0xFFFF7A3D, 40, 420, 7); burst(cx, cy, 0xFFFFFFFF, 20, 620, 5);
        rings.add(new float[]{cx, cy, now, 6f}); rings.add(new float[]{cx, cy, now + 140, 9f}); rings.add(new float[]{cx, cy, now + 300, 12f});
        shake(30); GameKit.rumble(a); postDelayed(() -> GameKit.rumble(a), 260);
        PixelStackSound.play(PixelStackSound.BOOM, .95f, .5f); postDelayed(() -> PixelStackSound.play(PixelStackSound.BOOM, .7f, .7f), 180); postDelayed(() -> PixelStackSound.play(PixelStackSound.BOOM, .5f, .9f), 420);
    }

    void stepNuke(float dt, long now) {
        if (nukeAt > 0 && !nukeHit && now - nukeAt >= NUKE_FALL) nukeBlast();
        for (int i = fires.size() - 1; i >= 0; i--) if (now - fires.get(i)[2] > fires.get(i)[3]) fires.remove(i);
        if (state != PLAY) return;
        if (nukeGun == null) { nukeGun = new Tank(); nukeGun.player = true; nukeGun.alive = false; }
        for (Tank t : new ArrayList<Tank>(tanks)) {
            if (t.player || !t.alive || !ready(t)) continue;
            if (!inFire(t, now)) { t.scorch = 0; continue; }
            t.scorch += dt;
            if (t.scorch >= .75f) { t.scorch = 0; Shot fake = new Shot(); fake.from = nukeGun; fake.x = t.x + 1; fake.y = t.y + 1; burst(px(t.x + 1), py(t.y + 1), 0xFFFF7A3D, 6, 120, 3); hitTank(t, fake); }
        }
        if (me != null && me.alive && ready(me)) {
            boolean burning = inFire(me, now) && now >= me.shieldUntil;
            heat = GameKit.clamp(heat + (burning ? dt / 4f : -dt / 2.5f));
            if (burning && motion && rnd.nextFloat() < .2f) { GameKit.Bit b = spark(px(me.x + 1), py(me.y + 1), 0x99FFB347, 40, 4); b.round = true; b.gravity = -.2f; b.decay = 2f; }
            if (heat >= 1f) { heat = 0; explode(me, true); meDeadAt = now; soulDown(); }
        } else heat = 0;
    }
    boolean inFire(Tank t, long now) {
        for (float[] f : fires) if (now >= f[2] && Math.abs(t.x + 1 - f[0]) < 1.5f && Math.abs(t.y + 1 - f[1]) < 1.5f) return true;
        return false;
    }

    void drawArmour(Canvas c, float cx, float cy, float s, float k, long now) {
        if (k <= 0) return;
        float half = s * .6f, e = GameKit.clamp(k);
        for (int q = 0; q < 4; q++) {
            int sx = q % 2 == 0 ? -1 : 1, sy = q < 2 ? -1 : 1;
            float off = (1 - e) * s * .75f, pxm = cx + sx * (half / 2 + off), pym = cy + sy * (half / 2 + off);
            int save = c.save(); c.rotate((1 - e) * 120 * (q % 3 == 0 ? 1 : -1), pxm, pym);
            box.set(pxm - half / 2 + d, pym - half / 2 + d, pxm + half / 2 - d, pym + half / 2 - d);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFF7D8AA3, e)); c.drawRoundRect(box, s * .1f, s * .1f, fill);
            box.inset(s * .05f, s * .05f); fill.setColor(GameKit.alpha(0xFFC7D2E6, e)); c.drawRoundRect(box, s * .07f, s * .07f, fill);
            fill.setColor(GameKit.alpha(0xFF5A667D, e)); c.drawCircle(pxm + sx * half * .22f, pym + sy * half * .22f, s * .035f, fill);
            fill.setColor(GameKit.alpha(0x55FFFFFF, e)); box.set(pxm - half * .3f, pym - half * .32f, pxm + half * .1f, pym - half * .2f); c.drawRoundRect(box, s * .02f, s * .02f, fill);
            c.restoreToCount(save);
        }
        if (e >= .95f) {
            float g = (float) (.5 + .5 * Math.sin(now / 110.0));
            stroke.setStrokeWidth(Math.max(1.5f, s * .05f)); stroke.setColor(GameKit.alpha(0xFF7FE9FF, .6f + .4f * g));
            c.drawLine(cx - half, cy, cx + half, cy, stroke); c.drawLine(cx, cy - half, cx, cy + half, stroke);
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFF7FE9FF, .35f + .3f * g)); c.drawCircle(cx, cy, s * (.82f + .04f * g), stroke);
        }
    }
    float armourK(long now) {
        if (armourFrom < 0 || now > armourUntil) return 0;
        return Math.min(GameKit.ease((now - armourFrom) / 420f), 1 - GameKit.ease((now - (armourUntil - 420)) / 420f));
    }

    void drawFires(Canvas c, long now) {
        for (float[] f : fires) {
            float age = now - f[2]; if (age < 0) continue;
            float k = Math.min(GameKit.clamp(age / 400f), GameKit.clamp((f[3] - age) / 1800f));
            float x = px(f[0]), y = py(f[1]), s = unit * 1.5f;
            fill.setShader(null);
            fill.setColor(GameKit.alpha(0xFF1A0E08, k * .5f)); c.drawOval(GameKit.rf(x - s, y - s * .5f + s * .3f, x + s, y + s * .5f + s * .3f), fill);
            fill.setColor(GameKit.alpha(0xFFFF7A3D, k * (.16f + .06f * (float) Math.sin(now / 90.0 + f[0])))); c.drawCircle(x, y, s * 1.25f, fill);
            for (int i = 0; i < 7; i++) {
                float ph = ((now / (520f + i * 37)) + i * .37f + f[1] * .1f) % 1f;
                float fx = x + (i - 3) * s * .22f + (float) Math.sin(now / 170.0 + i * 2.1) * s * .08f, fy = y + s * .35f - ph * s * 1.3f;
                float q = s * (.34f - .22f * ph) * k;
                int col = ph < .3f ? 0xFFFFF1A8 : ph < .6f ? 0xFFFFB347 : 0xFFE5532B;
                fill.setColor(GameKit.alpha(col, k * (1 - ph * .7f)));
                box.set(fx - q, fy - q, fx + q, fy + q); c.drawRoundRect(box, q * .3f, q * .3f, fill);
            }
        }
    }

    void drawNuke(Canvas c, long now) {
        if (nukeAt < 0) return;
        float t = now - nukeAt, cx = board.centerX(), cy = board.centerY();
        if (t > 3600) { nukeAt = -1; return; }
        fill.setShader(null);
        if (t < NUKE_FALL) {
            float k = t / NUKE_FALL, e = GameKit.easeIn(k);
            float mark = unit * (5.5f - 3.2f * e);
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFE53935, .5f + .5f * (float) Math.sin(t / 60.0)));
            for (int i = 0; i < 8; i++) c.drawArc(GameKit.rf(cx - mark, cy - mark, cx + mark, cy + mark), i * 45 + t * .2f, 24, false, stroke);
            c.drawLine(cx - mark * 1.2f, cy, cx - mark * .6f, cy, stroke); c.drawLine(cx + mark * .6f, cy, cx + mark * 1.2f, cy, stroke);
            c.drawLine(cx, cy - mark * 1.2f, cx, cy - mark * .6f, stroke); c.drawLine(cx, cy + mark * .6f, cx, cy + mark * 1.2f, stroke);
            fill.setColor(GameKit.alpha(0xFF000000, .12f + .3f * e)); c.drawCircle(cx, cy, unit * (4.5f - 3.2f * e), fill);
            float s = unit * (9f - 7.6f * e), a0 = GameKit.clamp(k * 5);
            int save = c.save(); c.rotate(t * .25f, cx, cy);
            fill.setColor(GameKit.alpha(0xFF3A4254, a0));
            for (int i = 0; i < 4; i++) { int sv = c.save(); c.rotate(i * 90 + 45, cx, cy); box.set(cx - s * .09f, cy - s * .62f, cx + s * .09f, cy - s * .3f); c.drawRoundRect(box, s * .04f, s * .04f, fill); c.restoreToCount(sv); }
            fill.setColor(GameKit.alpha(0xFF566078, a0)); c.drawCircle(cx, cy, s * .42f, fill);
            fill.setColor(GameKit.alpha(0xFF7B869E, a0)); c.drawCircle(cx - s * .08f, cy - s * .08f, s * .3f, fill);
            fill.setColor(GameKit.alpha(0xFFFFD23F, a0)); c.drawCircle(cx, cy, s * .16f, fill);
            fill.setColor(GameKit.alpha(0xFF14161C, a0));
            for (int i = 0; i < 3; i++) { android.graphics.Path w = GameKit.path(); double a1 = Math.toRadians(i * 120 - 30), a2 = Math.toRadians(i * 120 + 30); w.moveTo(cx, cy); w.lineTo(cx + (float) Math.cos(a1) * s * .15f, cy + (float) Math.sin(a1) * s * .15f); w.lineTo(cx + (float) Math.cos(a2) * s * .15f, cy + (float) Math.sin(a2) * s * .15f); w.close(); c.drawPath(w, fill); }
            c.restoreToCount(save);
            return;
        }
        float b = t - NUKE_FALL, far = board.width() * .75f;
        float fk = GameKit.ease(b / 650f), fa = 1 - GameKit.clamp((b - 250) / 900f);
        if (fa > 0) {
            fill.setColor(GameKit.alpha(0xFFE5532B, fa * .85f)); c.drawCircle(cx, cy, far * fk, fill);
            fill.setColor(GameKit.alpha(0xFFFFB347, fa * .9f)); c.drawCircle(cx, cy, far * fk * .74f, fill);
            fill.setColor(GameKit.alpha(0xFFFFF1A8, fa)); c.drawCircle(cx, cy, far * fk * .46f, fill);
        }
        float ck = GameKit.clamp((b - 300) / 2100f);
        if (ck > 0 && ck < 1) {
            float ca = (1 - ck) * .75f;
            for (int i = 0; i < 12; i++) { double ang = i * Math.PI / 6 + ck; float rr = unit * (2 + 7 * GameKit.ease(ck)); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFF4A4038 : 0xFF6B5D50, ca)); c.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr * .9f, unit * (1.3f + 1.6f * ck), fill); }
            for (int i = 0; i < 6; i++) { double ang = i * Math.PI / 3 - ck * 2; float rr = unit * (1.2f + 1.4f * ck); fill.setColor(GameKit.alpha(i % 2 == 0 ? 0xFF8A7868 : 0xFFB89A78, ca)); c.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr, unit * (1.6f + 1.2f * ck), fill); }
            fill.setColor(GameKit.alpha(0xFFFFB347, ca * (1 - ck))); c.drawCircle(cx, cy, unit * (1.8f + ck), fill);
        }
        if (b < 380) { fill.setColor(GameKit.alpha(0xFFFFFFFF, 1 - b / 380f)); c.drawRect(board, fill); }
    }
    static final int GREEN = 0xFF2BB673, YELLOW = 0xFFF2C230, ORANGE = 0xFFF28C28, RED = 0xFFE53935;
    int brick, brickDark, steel, waterC, bushC; final int[] own = new int[5]; int worldOf = -1;
    void world() {
        int w = GameKit.world(stage);
        if (w == worldOf) return;
        worldOf = w;
        int[] k = GameKit.WORLDS[w];
        brick = k == null ? own[0] : k[2]; brickDark = k == null ? own[1] : k[3]; steel = k == null ? own[2] : k[4]; waterC = k == null ? own[3] : k[6]; bushC = k == null ? own[4] : k[7];
        wallsDirty = true;
    }

    PixelSiege(Activity a, RectF home, RectF[] from, int ink, int page, boolean dark, boolean black) {
        super(a, home, from, ink, page, dark, black, "pixelsiege", new int[]{GameKit.BLUE, GameKit.LIGHT, GameKit.LIGHT, GameKit.BLUE});
        brick = dark || black ? 0xFFE0774E : 0xFFEE8A5E; brickDark = dark || black ? 0xFFB85634 : 0xFFD06C40;
        steel = dark || black ? 0xFF8C9AB4 : 0xFFA3AEC4; waterC = dark || black ? 0xFF2C5BD6 : 0xFF6FA0F5; bushC = dark || black ? 0xFF2A9D63 : 0xFF3DBE78;
        own[0] = brick; own[1] = brickDark; own[2] = steel; own[3] = waterC; own[4] = bushC;
    }

    @Override int arcadeId() { return ArcadePreview.SIEGE; }
    @Override String shareKey() { return "siege"; }
    @Override String title() { return I18n.t("Pixel Siege"); }
    @Override String hint() { return I18n.t("Drag to move, tap to fire"); }

    @Override void layout(int w, int h) {
        wallsDirty = true; rings.clear();
        if (land) {
            float side = 150 * d, size = Math.min(h - 16 * d, w - leftInset - rightInset - 2 * side);
            cellPx = size / N; unit = cellPx / 2f; size = cellPx * N;
            float cx = (leftInset + w - rightInset) / 2f, cy = h / 2f;
            board.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            placeControlsLand(board);
            return;
        }
        float size = Math.min(w - 14 * d, (h - topInset - barH - bottomInset) * .61f);
        cellPx = size / N; unit = cellPx / 2f; size = cellPx * N;
        float top = topInset + barH + 72 * d;
        board.set((w - size) / 2f, top, (w + size) / 2f, top + size);
        placeControls(board.bottom + 8 * d);
    }

    final RectF baseRect = new RectF(), baseRect2 = new RectF(), plate0 = new RectF();
    final RectF[] baseSq = new RectF[4], buildSq = new RectF[4];
    final float[] dropBuf = new float[N * N];
    RectF plateRect() { plate0.set(board.left - 6 * d, board.top - 6 * d, board.right + 6 * d, board.bottom + 6 * d); return plate0; }

    float px(float u) { return board.left + u * unit; }
    float py(float u) { return board.top + u * unit; }

    static final String[][] MAPS = {
        {".............", ".#.#.#.#.#.#.", ".#.#.#.#.#.#.", ".#.#.###.#.#.", ".#.#.#.#.#.#.", ".....#.#.....", "S.##.....##.S", ".....#.#.....", ".#.#.###.#.#.", ".#.#.#.#.#.#.", ".#.#.....#.#.", ".#.#.....#.#.", "............."},
        {"...S...S.....", ".#.S.....#.#.", ".#....#.S#.#.", ".....#..S....", "#...S..#..S##", "**..#.~...#..", ".**...~#.....", ".##*~~~~..S#.", "...***..~~...", ".#~~#.***.#.#", ".#.#.#.#.#.#.", ".#.#.....#.#.", ".........#..."},
        {"....#...#....", ".SS.#.#.#.SS.", ".S..#...#..S.", "...###.###...", "**.........**", "**.S~~.~~S.**", "...S~~.~~S...", ".#.........#.", ".#.##.#.##.#.", ".#....#....#.", ".###.....###.", ".............", "............."},
        {".*.....*.....", ".*.#.#.*.#.#.", "**.#.#.**#.#.", "...#S#...#S#.", ".~~~...~~~...", ".~~~.#.~~~.#.", ".....#.....#.", ".S.#####.S...", "...#...#.....", "**.#.S.#.**..", "**.......**..", ".#.#.....#.#.", ".#.#.....#.#."},
        {".....S.S.....", ".###.....###.", ".#.#.***.#.#.", ".###.***.###.", ".....***.....", "S.~~~...~~~.S", "S.~~~.#.~~~.S", "......#......", ".##.#####.##.", ".#.........#.", ".#.#.###.#.#.", "...#.....#...", "............."},
        {".............", ".S.S.S.S.S.S.", ".............", "##.##.#.##.##", "..*..*.*..*..", ".***.***.***.", "..*..*.*..*..", "##.##.#.##.##", ".............", ".#~#.#.#.#~#.", ".#~#.....#~#.", ".#.#.....#.#.", "............."},

        {".............", ".##.#...#.##.", ".##.#.B.#.##.", ".....###.....", "SS.........SS", "...##.~.##...", ".B.##~~~##.B.", "...##.~.##...", "SS.........SS", ".....###.....", ".##.#...#.##.", ".##.......##.", "............."},
        {".............", ".#.#.#.#.#.#.", ".#B#.#B#.#B#.", ".#.#.#.#.#.#.", ".............", "**.SS...SS.**", "**.........**", "...B#####B...", ".....#.#.....", ".#.#.....#.#.", ".#.#.###.#.#.", "...#.....#...", "............."},
        {".............", ".~~~.#.#.~~~.", ".~~~.#.#.~~~.", ".....#.#.....", ".###.....###.", ".....S.S.....", "~~.#.....#.~~", "~~.#.B.B.#.~~", ".....###.....", ".#.~~...~~.#.", ".#.~~.#.~~.#.", ".#.........#.", "............."},
        {".............", ".#########.#.", ".#.......#.#.", ".#.#####.#.#.", ".#.#...#.#...", "...#.B.#.###.", ".###...#.....", ".....#####.#.", ".###.......#.", ".#...S.S.#.#.", ".#.##...##.#.", ".#.........#.", "............."},
        {"***.......***", "*.*.#.#.#.*.*", "***.#.#.#.***", "....#.#.#....", ".**.......**.", ".**.SS.SS.**.", ".....***.....", ".##..*B*..##.", ".##..***..##.", ".............", "**.#.....#.**", "**.#.....#.**", "............."},
        {".............", ".S.S.S.S.S.S.", ".............", ".#S#.#S#.#S#.", ".###.###.###.", ".............", "B.~~~...~~~.B", "..~~~.#.~~~..", ".............", ".#.#.#.#.#.#.", ".#.#.....#.#.", ".#.#.....#.#.", "............."},
        {".............", ".##.......##.", ".#B~~...~~B#.", "..~~*...*~~..", "..~*.....*~..", ".....###.....", ".S..#...#..S.", ".....###.....", "..~*.....*~..", "..~~*...*~~..", ".#..~~.~~..#.", ".##.......##.", "............."},
        {".............", ".####...####.", ".....#.#.....", ".###.#.#.###.", "...#.....#...", ".B.#.SSS.#.B.", "...#.....#...", ".###.#.#.###.", ".....#.#.....", ".#####.#####.", ".............", ".#.#.....#.#.", "............."}
    };

    void loadStage() {
        for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) { wall[y][x] = 0; fortress[y][x] = false; }
        wallsDirty = true;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { water[y][x] = false; bush[y][x] = false; }
        String[] m = stage <= MAPS.length ? MAPS[stage - 1] : genMap(stage);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            char ch = m[y].charAt(x);
            byte v = ch == '#' ? (byte) 1 : ch == 'S' ? (byte) 2 : ch == 'B' ? (byte) 4 : 0;
            if (v != 0) for (int k = 0; k < 4; k++) wall[y * 2 + k / 2][x * 2 + k % 2] = v;
            if (ch == '~') water[y][x] = true;
            if (ch == '*') bush[y][x] = true;
        }
        int[][] open = {{0, 0}, {6, 0}, {12, 0}, {4, 12}, {8, 12}, {5, 11}, {6, 11}, {7, 11}, {5, 12}, {6, 12}, {7, 12}};
        for (int[] o : open) { water[o[1]][o[0]] = false; bush[o[1]][o[0]] = false; for (int k = 0; k < 4; k++) wall[o[1] * 2 + k / 2][o[0] * 2 + k % 2] = 0; }
        for (int sx : new int[]{0, 6, 12}) openRoute(sx);
        for (int y = 22; y <= 25; y++) for (int x = 10; x <= 15; x++) {
            boolean inner = x >= 12 && x <= 13 && y >= 24;
            if (inner) continue;
            wall[y][x] = 3; fortress[y][x] = true;
        }
        for (int i = 0; i < 20; i++) {
            float r = rnd.nextFloat(), hard = Math.min(.75f, .08f * (stage - 1));
            queue[i] = r < .55f - hard * .5f ? 0 : r < .75f - hard * .3f ? 1 : r < .9f - hard * .1f ? 2 : 3;
        }
        if (stage >= 2) { int[] at = {9, 19, 4, 14}; for (int k = 0; k < Math.min(4, 1 + (stage - 2) / 2); k++) queue[at[k]] = 4; }
        {
            ArrayList<Integer> slots = new ArrayList<Integer>(); for (int i = 0; i < 20; i++) if (queue[i] != 4) slots.add(i);
            java.util.Collections.shuffle(slots, rnd); int at = 0;
            if (stage >= 3) for (int k = 0; k < 2 && at < slots.size(); k++) queue[slots.get(at++)] = 6;
            if (stage >= 2) for (int k = 0; k < Math.min(6, 2 + stage / 3) && at < slots.size(); k++) queue[slots.get(at++)] = 5;
            if (stage >= 10) for (int k = 0; k < Math.min(4, 2 + (stage - 10) / 3) && at < slots.size(); k++) queue[slots.get(at++)] = 7;
            int[][] joins = {{8, 16}, {9, 22}, {10, 28}, {11, 35}, {12, 45}, {13, 55}};
            for (int[] jn : joins) if (stage >= jn[1]) for (int k = 0; k < Math.min(jn[0] == 13 ? 3 : 4, (jn[0] == 13 ? 1 : 2) + (stage - jn[1]) / 15) && at < slots.size(); k++) queue[slots.get(at++)] = jn[0];
        }
        tanks.clear(); shots.clear(); prizes.clear(); bits.clear(); pops.clear();
        fires.clear(); nukeAt = -1; nukeHit = false; armourFrom = armourUntil = -1; heat = 0; strikes.clear(); jetAt = -1; waveAt = -1;
        cores.clear(); coresAt = -1; coreTakenAt = -1; ask = -1; askLast = -1; charges.clear(); crawlers.clear(); trails.clear(); emblems.clear(); skies.clear(); burns.clear(); glows.clear(); capReady = thunder() >= 1;
        stageTanks = bossStage() ? 8 : 20;
        reserve = stageTanks; killed = 0; nextSpawn = 0; spawnTimer = 0; me = null; meDeadAt = -1;
        makeBoss();
        freezeUntil = 0; fortressUntil = 0; baseDown = false; baseHitAt = -1; baseMax = baseMaxFor(); baseHp = baseMax;
        clearAt = -1; doorOpenAt = -1; travelAt = -1; drumsDue.clear();
        wakes.clear(); baseShield = baseShieldMax(); baseHealCool = 4f; baseZapCool = 1f;
        mortars.clear(); mines.clear();
        openEntry(); placeDoor();
    }

    void openRoute(int sx) {
        int[] cost = new int[N * N], from = new int[N * N];
        java.util.Arrays.fill(cost, Integer.MAX_VALUE); java.util.Arrays.fill(from, -1);
        java.util.PriorityQueue<int[]> q = new java.util.PriorityQueue<int[]>((x, y) -> Integer.compare(x[0], y[0]));
        cost[sx] = 0; q.add(new int[]{0, sx});
        int end = -1;
        while (!q.isEmpty()) {
            int[] e = q.poll(); int at = e[1];
            if (e[0] > cost[at]) continue;
            int cx = at % N, cy = at / N;
            if (cy >= 9) { end = at; break; }
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] st : steps) {
                int nx = cx + st[0], ny = cy + st[1];
                if (nx < 0 || ny < 0 || nx >= N || ny >= N || water[ny][nx]) continue;
                boolean steel = false; int bricks = 0;
                for (int k = 0; k < 4; k++) { byte v = wall[ny * 2 + k / 2][nx * 2 + k % 2]; if (v == 2 || v == 4) steel = true; else if (v != 0) bricks++; }
                if (steel) continue;
                int c = e[0] + 1 + (bricks > 0 ? 8 : 0), to = ny * N + nx;
                if (c < cost[to]) { cost[to] = c; from[to] = at; q.add(new int[]{c, to}); }
            }
        }
        for (int at = end; at >= 0; at = from[at]) {
            int cx = at % N, cy = at / N;
            for (int k = 0; k < 4; k++) { int uy = cy * 2 + k / 2, ux = cx * 2 + k % 2; if (wall[uy][ux] == 1) wall[uy][ux] = 0; }
        }
    }

    @Override void newGame() {
        shownPower = 0;
        soul = NONE; soulLv = 0; soulUnits = 0; soulBits = 0; soulShown = 0; soulDropAt = -10000; meals = 0; soulAt = -10000; absorbAt = -10000; chargeReadyAt = 0; capReady = false;
        enterSide = -1; arriveAt = -100000; fromTitle = true; keepLevel = 0; kills = 0; rank = 0; starXp = 0; overBrokenAt = -100000; rocketUntil = 0; doubleUntil = 0; laserUntil = 0; droneUntil = 0; stage = 1; score = 0; lives = 5; newBest = false; cells = null; sources.clear();
        loadStage();
    }

    static final class Boss { int kind; float x, y = -4, dirX = 1; int hp, maxHp; boolean alive = true; long bornAt, hitAt = -10000, deadAt = -1, chargeAt = -1, beamAt = -1; float cool, cool2, spin, beamX; Shot lastLaser;
        float coolC = 2.2f, coolB = 2.2f, coolM = 3f, coolS = 4f, coolMi = 3f, coolSh = 6f, coolW = 5f, coolT = 5f, fromX; long shieldUntil, teleAt = -10000; }
    Boss boss;
    final Tank bossGun = new Tank();
    static final float BOSS_Y = 3.6f, BOSS_W = 2.6f, BOSS_H = 2.2f;

    boolean bossStage() { return stage % 5 == 0; }

    void makeBoss() {
        boss = null;
        if (!bossStage()) return;
        Boss b = new Boss(); int round = stage / 5 - 1;
        b.kind = bossSpec();
        b.hp = b.maxHp = 150 + round * 80 + (b.kind == 0 ? 30 : 0);
        b.x = 13; b.bornAt = SystemClock.uptimeMillis() + 1600; b.cool = 2.2f; b.cool2 = 4f;
        boss = b;
        bossGun.player = false; bossGun.alive = true; bossGun.type = 3; bossGun.tier = 3;
    }

    boolean bossIn() { return boss != null && boss.alive && SystemClock.uptimeMillis() > boss.bornAt + 1300; }

    void bossShot(float x, float y, int dir, float speed) {
        Shot s = new Shot(); s.from = bossGun; s.dir = dir; s.x = x; s.y = y; s.speed = speed; s.steel = false;
        shots.add(s);
        muzzle(px(x), py(y), boss != null && boss.kind % 20 != 0 && boss.kind % 20 != 2 ? BOSS_GLOW[boss.kind % 20] : 0xFFFFC2B8);
    }

    void stepBoss(float dt) {
        Boss b = boss; if (b == null) return;
        long now = SystemClock.uptimeMillis();
        if (!b.alive) return;
        if (now < b.bornAt) return;
        float arrive = GameKit.clamp((now - b.bornAt) / 1300f);
        b.y = -4 + (BOSS_Y + 4) * GameKit.spring(arrive);
        int body = BOSS_BODY[b.kind % 20];
        boolean angry = b.hp < b.maxHp / 2, rage = angry && bossHas(b, B_RAGE);
        b.spin += dt * (body == 1 ? (angry ? 260 : 150) : 40);
        if (arrive < 1 || now < freezeUntil) return;
        float hurry = rage ? 1.45f : 1f;
        boolean firing = bossHas(b, B_BEAM) && (b.chargeAt > 0 || (b.beamAt > 0 && now - b.beamAt < 650));
        if (!firing && now - b.teleAt > 450) {
            float sp = body == 1 ? (angry ? 4.2f : 3f) : body == 2 ? 3.4f : body == 5 ? (angry ? 5f : 3.8f) : body == 6 ? 1.8f : (angry ? 2.6f : 2f);
            if (rage) sp *= 1.4f;
            if (bossHas(b, B_AIM) && me != null && me.alive) { float want = me.x + 1; b.dirX = Math.abs(want - b.x) < .3f ? 0 : Math.signum(want - b.x); }
            b.x += b.dirX * sp * dt;
            if (b.x < 4) { b.x = 4; b.dirX = 1; } if (b.x > 22) { b.x = 22; b.dirX = -1; }
            if (!bossHas(b, B_AIM) && b.dirX == 0) b.dirX = 1;
        }
        float t2 = dt * hurry;
        b.cool -= t2; b.cool2 -= t2; b.coolC -= t2; b.coolB -= t2; b.coolM -= t2; b.coolS -= t2; b.coolMi -= t2; b.coolSh -= t2; b.coolW -= t2; b.coolT -= t2;
        if (bossHas(b, B_SPREAD) && b.cool <= 0) {
            b.cool = angry ? 1.35f : 1.9f;
            for (float o : new float[]{-1.7f, 0f, 1.7f}) bossShot(b.x + o, b.y + BOSS_H, 2, 12f);
            if (angry && bossHas(b, B_SIDE)) { bossShot(b.x - BOSS_W, b.y + .4f, 3, 11f); bossShot(b.x + BOSS_W, b.y + .4f, 1, 11f); }
            PixelStackSound.play(PixelStackSound.SHOT, .3f, .7f); shake(3);
        }
        if (bossHas(b, B_CROSS) && b.coolC <= 0) {
            b.coolC = angry ? 1.5f : 2.3f;
            for (int dir = 0; dir < 4; dir++) bossShot(b.x + DX[dir] * BOSS_W * .9f, b.y + DY[dir] * BOSS_H * .9f, dir, 13f);
            if (angry) postDelayed(() -> { if (boss != null && boss.alive && state == PLAY) for (int dir = 1; dir < 4; dir++) bossShot(boss.x + DX[dir] * BOSS_W * .9f, boss.y + DY[dir] * BOSS_H * .9f, dir, 13f); }, 220);
            PixelStackSound.play(PixelStackSound.SHOT, .3f, 1.25f);
        }
        if (bossHas(b, B_BEAM)) {
            if (b.chargeAt < 0 && (b.beamAt < 0 || now - b.beamAt > 650) && b.coolB <= 0) { b.chargeAt = now; b.beamX = b.x; PixelStackSound.play(PixelStackSound.RISE, .35f, 1.5f); }
            if (b.chargeAt > 0 && now - b.chargeAt > (angry ? 800 : 1100)) { b.chargeAt = -1; b.beamAt = now; b.coolB = angry ? 1.6f : 2.4f; beam(b); }
            if (b.cool2 <= 0) { b.cool2 = 2.8f; bossShot(b.x - 1.4f, b.y + BOSS_H, 2, 12f); bossShot(b.x + 1.4f, b.y + BOSS_H, 2, 12f); }
            if (b.beamAt > 0 && now - b.beamAt < 650 && me != null && me.alive && now >= me.shieldUntil && Math.abs(me.x + 1 - b.beamX) < 1.3f && me.y > b.y) { hurtMe(2, now); }
        }
        if (bossHas(b, B_MORTAR) && b.coolM <= 0 && me != null && me.alive) {
            b.coolM = angry ? 2.2f : 3.2f;
            int n = (angry ? 2 : 1) + (body == 3 ? 1 : 0);
            for (int q = 0; q < n; q++) launchMortar(b.x, b.y + BOSS_H * .5f, me.x + 1 + (q == 0 ? 0 : (rnd.nextFloat() - .5f) * 5f), me.y + 1 + (q == 0 ? 0 : (rnd.nextFloat() - .5f) * 4f), true);
        }
        if (bossHas(b, B_SUMMON) && b.coolS <= 0) { b.coolS = angry ? 7f : 10f; summon(b); }
        if (bossHas(b, B_MINES) && b.coolMi <= 0) { b.coolMi = angry ? 3.5f : 5f; for (int q = 0; q < 2; q++) { float mx = me != null && me.alive ? me.x + 1 + (rnd.nextFloat() - .5f) * 9f : 2 + rnd.nextFloat() * (U - 4); dropMine(Math.max(1, Math.min(U - 1, mx)), 8 + rnd.nextFloat() * 12, true); } }
        if (bossHas(b, B_SHIELD) && b.coolSh <= 0 && now > b.shieldUntil) { b.coolSh = angry ? 7f : 9.5f; b.shieldUntil = now + 2600; PixelStackSound.play(PixelStackSound.TING, .4f, .8f); PixelStackSound.play(PixelStackSound.RISE, .3f, 1.2f); }
        if (bossHas(b, B_WALLS) && b.coolW <= 0) { b.coolW = angry ? 6f : 8.5f; dropWalls(b); }
        if (bossHas(b, B_TELE) && b.coolT <= 0) {
            b.coolT = angry ? 4.5f : 6.5f;
            for (int tries = 0; tries < 20; tries++) { float nx = 4 + rnd.nextFloat() * 18; if (Math.abs(nx - b.x) < 5) continue; burst(px(b.x), py(b.y), BOSS_GLOW[b.kind % 20], 16, 220, 5); b.fromX = b.x; b.x = nx; b.teleAt = now; burst(px(b.x), py(b.y), BOSS_GLOW[b.kind % 20], 16, 220, 5); PixelStackSound.play(PixelStackSound.SOFT, .45f, 1.6f); break; }
        }
    }

    void beam(Boss b) {
        int c0 = (int) Math.floor(b.beamX - .9f), c1 = (int) Math.floor(b.beamX + .9f);
        for (int uy = (int) (b.y + BOSS_H); uy < U; uy++) for (int ux = c0; ux <= c1; ux++) {
            if (ux < 0 || ux >= U) continue;
            if (wall[uy][ux] == 1 && !fortress[uy][ux]) { wall[uy][ux] = 0; wallsDirty = true; if (rnd.nextFloat() < .3f) spark(px(ux + .5f), py(uy + .5f), brick, 120, unit / d * .3f); }
        }
        shake(10); GameKit.thump(a, .9f);
        PixelStackSound.play(PixelStackSound.FIRE, .5f, .8f);
    }

    boolean hitBoss(Shot s) {
        Boss b = boss;
        if (b == null || !bossIn() || !s.from.player) return false;
        if (Math.abs(s.x - b.x) > BOSS_W || Math.abs(s.y - b.y) > BOSS_H) return false;
        long now = SystemClock.uptimeMillis();
        if (now < b.shieldUntil) { if (s.laser) { if (s == b.lastLaser) return false; b.lastLaser = s; } burst(px(s.x), py(s.y), 0xFF7FE9FF, 6, 120, 3); PixelStackSound.play(PixelStackSound.TING, .35f, 1.5f); return !s.laser; }
        if (s.laser) { if (s == b.lastLaser) return false; b.lastLaser = s; }
        int n = Math.max(1, s.dmg) + (s.rocket && !s.mini ? 1 : 0);
        if (s.laser) { laserAcc += n * .25f; int more = (int) laserAcc; laserAcc -= more; n += more; }
        if (s.rocket && !s.mini && mine(s) && s.soul == INFERNO) { n += 1 + inferno() / 3; rocketBurst(s); }
        else if (s.laser && mine(s) && thunder() > 0) n += 1 + thunder() / 3;
        b.hp -= n; b.hitAt = now;
        hitNumber(px(s.x), py(b.y - BOSS_H), n, s.laser ? 0xFF7FE9FF : s.charged ? (s.soul == THUNDER ? 0xFF7FE9FF : 0xFFFF7A3D) : 0xFFFFE066, n >= 3 || b.hp <= 0);
        burst(px(s.x), py(s.y), 0xFFFFFFFF, 5, 110, 3);
        PixelStackSound.play(PixelStackSound.TING, .3f, .7f + .6f * b.hp / (float) b.maxHp); GameKit.tick(a, .35f);
        if (b.hp <= 0) {
            b.alive = false; b.deadAt = now;
            for (Tank t : new ArrayList<Tank>(tanks)) if (t.summoned && t.alive) explode(t, false);
            for (int i = mines.size() - 1; i >= 0; i--) if (mines.get(i).boss) { mineBoom(mines.get(i)); mines.remove(i); }
            for (Mortar m : mortars) if (m.boss) m.done = true;
            float cx = px(b.x), cy = py(b.y);
            for (int i = 0; i < 5; i++) { final int k = i; postDelayed(() -> { burst(cx + (rnd.nextFloat() - .5f) * unit * 4, cy + (rnd.nextFloat() - .5f) * unit * 3, k % 2 == 0 ? 0xFFFFE08A : 0xFFFF7A3D, 18, 280, 6); PixelStackSound.play(PixelStackSound.BOOM, .5f, .7f + k * .1f); }, i * 160); }
            rings.add(new float[]{cx, cy, now, 2.6f});
            shake(20); GameKit.rumble(a);
            int pts = 5000 * (stage / 5); addScore(pts);
            pop("+" + (now < doubleUntil ? pts * 2 : pts), cx, cy, 0xFFFFD23F, 24);
            Prize p = new Prize(); p.kind = LIFE; p.x = Math.max(0, Math.min(U - 2, b.x - 1)); p.y = 8; p.at = now; prizes.add(p);
        }
        return !s.laser;
    }

    void drawBoss(Canvas c, long now) {
        Boss b = boss; if (b == null || now < b.bornAt) return;
        float k = 1f;
        if (!b.alive) { k = 1 - GameKit.ease((now - b.deadAt) / 700f); if (k <= 0) return; }
        float cx = px(b.x), cy = py(b.y), w = unit * BOSS_W, h = unit * BOSS_H;
        boolean flash = now - b.hitAt < 90;
        fill.setShader(null); fill.setColor(GameKit.alpha(0x40000000, k)); c.drawOval(GameKit.rf(cx - w * .8f, cy + h * 1.15f, cx + w * .8f, cy + h * 1.45f), fill);
        int save = c.save();
        float bob = motion ? (float) Math.sin(now / 300.0) * unit * .12f : 0;
        c.translate(0, bob);
        if (!b.alive) { c.rotate((1 - k) * 25, cx, cy); c.scale(1 + (1 - k) * .3f, k, cx, cy); }
        float size = 1 + Math.min(.28f, (b.kind % 20) * .014f) + Math.min(.15f, (stage / 100) * .05f); c.scale(size, size, cx, cy);
        float tele = GameKit.clamp((now - b.teleAt) / 450f); if (tele < 1) { k *= tele; }
        switch (BOSS_BODY[b.kind % 20]) {
            case 0: drawCrown(c, b, cx, cy, w, h, now, flash, k); break;
            case 1: drawStorm(c, b, cx, cy, w, h, now, flash, k); break;
            case 3: drawRig(c, b, cx, cy, w, h, now, flash, k); break;
            case 4: drawHive(c, b, cx, cy, w, h, now, flash, k); break;
            case 5: drawSerpent(c, b, cx, cy, w, h, now, flash, k); break;
            case 6: drawDreadnought(c, b, cx, cy, w, h, now, flash, k); break;
            default: drawWalker(c, b, cx, cy, w, h, now, flash, k);
        }
        if (b.alive && now < b.shieldUntil) { float q = motion ? (float) (.5 + .5 * Math.sin(now / 90.0)) : .5f, rr = Math.max(w, h) * 1.25f; fill.setShader(null); fill.setColor(GameKit.alpha(0xFF4FD8FF, (.12f + .08f * q) * k)); c.drawCircle(cx, cy, rr, fill); stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1.5f, unit * .12f)); stroke.setColor(GameKit.alpha(0xFF7FE9FF, (.6f + .3f * q) * k)); c.drawCircle(cx, cy, rr, stroke); }
        c.restoreToCount(save);
        if (bossHas(b, B_BEAM) && b.alive) {
            float bx = px(b.beamX), top = cy + h * .6f + bob, bottom = board.bottom;
            if (b.chargeAt > 0) {
                float q = GameKit.clamp((now - b.chargeAt) / 1100f), blink = (now / 70) % 2 == 0 ? 1f : .5f;
                stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .08f));
                stroke.setColor(GameKit.alpha(0xFFFF2D55, (.35f + .5f * q) * blink)); c.drawLine(bx, top, bx, bottom, stroke);
                fill.setColor(GameKit.alpha(0xFFFF2D55, .5f * q)); c.drawCircle(bx, top, unit * (.3f + .5f * q), fill);
            } else if (b.beamAt > 0 && now - b.beamAt < 650) {
                float q = 1 - (now - b.beamAt) / 650f, bw = unit * 1.8f * (.6f + .4f * q);
                fill.setColor(GameKit.alpha(0x88FF2D55, q)); c.drawRect(bx - bw / 2, top, bx + bw / 2, bottom, fill);
                fill.setColor(GameKit.alpha(0xFFFFB3C1, q)); c.drawRect(bx - bw * .25f, top, bx + bw * .25f, bottom, fill);
                fill.setColor(GameKit.alpha(0xFFFFFFFF, q)); c.drawRect(bx - bw * .08f, top, bx + bw * .08f, bottom, fill);
            }
        }
    }

    void drawCrown(Canvas c, Boss b, float cx, float cy, float w, float h, long now, boolean flash, float k) {
        int ink = BOSS_INK[b.kind % 20], hull = flash ? 0xFFFFFFFF : ink, plateC = flash ? 0xFFFFFFFF : b.kind % 20 == 0 ? 0xFF5A667D : GameKit.mix(ink, 0xFFFFFFFF, .25f), gold = b.kind % 20 >= 13 ? 0xFFFFE57F : 0xFFFFD23F;
        for (int i = -1; i <= 1; i += 2) { float jx = cx + i * w * .55f, f = (float) (.6 + .4 * Math.sin(now / 50.0 + i)); fill.setColor(GameKit.alpha(0xFF6FD3FF, k * .6f * f)); c.drawOval(GameKit.rf(jx - w * .14f, cy + h * .55f, jx + w * .14f, cy + h * (.9f + .2f * f)), fill); }
        for (float o : new float[]{-.65f, 0f, .65f}) { box.set(cx + o * w - unit * .18f, cy + h * .2f, cx + o * w + unit * .18f, cy + h * 1.05f); fill.setColor(GameKit.alpha(0xFF262D3A, k)); c.drawRoundRect(box, unit * .1f, unit * .1f, fill); }
        android.graphics.Path p = GameKit.path();
        p.moveTo(cx - w, cy - h * .35f); p.lineTo(cx - w * .7f, cy - h * .8f); p.lineTo(cx + w * .7f, cy - h * .8f); p.lineTo(cx + w, cy - h * .35f); p.lineTo(cx + w * .75f, cy + h * .55f); p.lineTo(cx - w * .75f, cy + h * .55f); p.close();
        fill.setColor(GameKit.alpha(hull, k)); c.drawPath(p, fill);
        stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .08f)); stroke.setColor(GameKit.alpha(plateC, k)); c.drawPath(p, stroke);
        for (int i = -2; i <= 2; i++) { box.set(cx + i * w * .32f - w * .13f, cy - h * .25f, cx + i * w * .32f + w * .13f, cy + h * .3f); fill.setColor(GameKit.alpha(plateC, k)); c.drawRoundRect(box, unit * .12f, unit * .12f, fill); fill.setColor(GameKit.alpha(0x66FFFFFF, k)); c.drawCircle(box.centerX(), box.top + unit * .15f, unit * .06f, fill); }
        float glow = (float) (.6 + .4 * Math.sin(now / 160.0));
        box.set(cx - w * .45f, cy - h * .62f, cx + w * .45f, cy - h * .38f); fill.setColor(GameKit.alpha(BOSS_GLOW[b.kind % 20], k * (.6f + .4f * glow))); c.drawRoundRect(box, unit * .1f, unit * .1f, fill);
        android.graphics.Path cr = GameKit.path(); float cw = w * .7f, cb = cy - h * .8f, ch = h * .55f;
        cr.moveTo(cx - cw / 2, cb); cr.lineTo(cx - cw / 2, cb - ch * .7f); cr.lineTo(cx - cw / 4, cb - ch * .35f); cr.lineTo(cx, cb - ch); cr.lineTo(cx + cw / 4, cb - ch * .35f); cr.lineTo(cx + cw / 2, cb - ch * .7f); cr.lineTo(cx + cw / 2, cb); cr.close();
        fill.setColor(GameKit.alpha(gold, k)); c.drawPath(cr, fill);
        fill.setColor(GameKit.alpha(0xFFE53935, k)); c.drawCircle(cx, cb - ch * .45f, unit * .14f, fill);
        cracks(c, b, cx, cy, w, h, k);
    }

    void drawStorm(Canvas c, Boss b, float cx, float cy, float w, float h, long now, boolean flash, float k) {
        float r = Math.min(w, h) * 1.05f;
        int save = c.save(); c.rotate(b.spin, cx, cy);
        for (int i = 0; i < 8; i++) {
            c.rotate(45, cx, cy);
            box.set(cx - r * .2f, cy - r, cx + r * .2f, cy - r * .72f); fill.setColor(GameKit.alpha(flash ? 0xFFFFFFFF : b.kind % 20 == 1 ? (i % 2 == 0 ? 0xFF2C5BD6 : 0xFF6FA0F5) : GameKit.mix(BOSS_INK[b.kind % 20], 0xFFFFFFFF, i % 2 == 0 ? .3f : .55f), k)); c.drawRoundRect(box, unit * .1f, unit * .1f, fill);
        }
        c.restoreToCount(save);
        fill.setColor(GameKit.alpha(flash ? 0xFFFFFFFF : BOSS_INK[b.kind % 20], k)); c.drawCircle(cx, cy, r * .74f, fill);
        save = c.save(); c.rotate(-b.spin * .5f, cx, cy);
        for (int i = 0; i < 4; i++) { c.rotate(90, cx, cy); box.set(cx - unit * .2f, cy - r * 1.05f, cx + unit * .2f, cy - r * .5f); fill.setColor(GameKit.alpha(b.kind % 20 == 1 ? 0xFF0D1838 : GameKit.mix(BOSS_INK[b.kind % 20], 0xFF000000, .5f), k)); c.drawRoundRect(box, unit * .1f, unit * .1f, fill); }
        c.restoreToCount(save);
        float pulse = (float) (.5 + .5 * Math.sin(now / 120.0));
        fill.setColor(GameKit.alpha(BOSS_GLOW[b.kind % 20], k * (.35f + .3f * pulse))); c.drawCircle(cx, cy, r * (.5f + .08f * pulse), fill);
        fill.setColor(GameKit.alpha(0xFFE6FAFF, k)); c.drawCircle(cx, cy, r * .28f, fill);
        if (motion) {
            stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .07f)); stroke.setColor(GameKit.alpha(0xFFB8F0FF, k * .8f));
            for (int i = 0; i < 2; i++) {
                double ang = (now / 90.0 + i * 3.1) % (Math.PI * 2);
                android.graphics.Path z = GameKit.path(); z.moveTo(cx, cy);
                for (int j = 1; j <= 4; j++) { float rr = r * .74f * j / 4f; z.lineTo(cx + (float) Math.cos(ang + (j % 2 == 0 ? .25 : -.25)) * rr, cy + (float) Math.sin(ang + (j % 2 == 0 ? .25 : -.25)) * rr); }
                c.drawPath(z, stroke);
            }
        }
        cracks(c, b, cx, cy, w * .7f, h * .7f, k);
    }

    void drawWalker(Canvas c, Boss b, float cx, float cy, float w, float h, long now, boolean flash, float k) {
        long t = now;
        stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        for (int side = -1; side <= 1; side += 2) for (int i = 0; i < 3; i++) {
            float ph = (float) Math.sin(t / 140.0 + i * 2.1 + (side > 0 ? 1.5 : 0)) * (b.chargeAt > 0 ? .2f : 1f);
            float kx = cx + side * w * (.55f + .2f * i), ky = cy - h * (.55f - .45f * i) - ph * unit * .25f;
            float fx = cx + side * w * (.95f + .1f * i), fy = cy + h * (.2f + .35f * i) + ph * unit * .15f;
            stroke.setStrokeWidth(unit * .22f); stroke.setColor(GameKit.alpha(b.kind % 20 == 2 ? 0xFF2A2E38 : GameKit.mix(BOSS_INK[b.kind % 20], 0xFF000000, .3f), k)); c.drawLine(cx + side * w * .3f, cy - h * .1f + i * h * .2f, kx, ky, stroke); c.drawLine(kx, ky, fx, fy, stroke);
            fill.setColor(GameKit.alpha(b.kind % 20 == 2 ? 0xFF6C7488 : GameKit.mix(BOSS_INK[b.kind % 20], 0xFFFFFFFF, .35f), k)); c.drawCircle(kx, ky, unit * .14f, fill);
        }
        fill.setColor(GameKit.alpha(flash ? 0xFFFFFFFF : BOSS_INK[b.kind % 20], k)); c.drawOval(GameKit.rf(cx - w * .6f, cy - h * .65f, cx + w * .6f, cy + h * .55f), fill);
        fill.setColor(GameKit.alpha(0x33FFFFFF, k)); c.drawOval(GameKit.rf(cx - w * .4f, cy - h * .55f, cx + w * .1f, cy - h * .2f), fill);
        float charge = b.chargeAt > 0 ? GameKit.clamp((now - b.chargeAt) / 1100f) : 0f;
        float er = h * (.3f + .12f * charge);
        fill.setColor(GameKit.alpha(0xFF14161C, k)); c.drawCircle(cx, cy + h * .05f, er * 1.25f, fill);
        fill.setColor(GameKit.alpha(b.kind % 20 == 2 ? GameKit.mix(0xFFB01030, 0xFFFF2D55, charge) : GameKit.mix(GameKit.mix(BOSS_GLOW[b.kind % 20], 0xFF000000, .4f), BOSS_GLOW[b.kind % 20], charge), k)); c.drawCircle(cx, cy + h * .05f, er, fill);
        fill.setColor(GameKit.alpha(0xFFFFE0E6, k * (.5f + .5f * charge))); c.drawCircle(cx, cy + h * .05f, er * .4f, fill);
        if (charge > 0) { fill.setColor(GameKit.alpha(0xFFFF2D55, k * .25f * charge)); c.drawCircle(cx, cy + h * .05f, er * 2.2f, fill); }
        cracks(c, b, cx, cy, w * .6f, h * .6f, k);
    }

    void cracks(Canvas c, Boss b, float cx, float cy, float w, float h, float k) {
        int n = Math.round(5 * (1 - b.hp / (float) b.maxHp));
        stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .06f)); stroke.setColor(GameKit.alpha(0xCC000000, k));
        float[][] cr = {{-.5f, -.2f, -.2f, .1f}, {.4f, -.3f, .15f, 0f}, {-.2f, .3f, .1f, .05f}, {.55f, .2f, .3f, .35f}, {-.6f, .25f, -.35f, .4f}};
        for (int i = 0; i < n; i++) c.drawLine(cx + w * cr[i][0], cy + h * cr[i][1], cx + w * cr[i][2], cy + h * cr[i][3], stroke);
    }

    void drawBossBar(Canvas c, float fade) {
        Boss b = boss; if (b == null || !b.alive || SystemClock.uptimeMillis() < b.bornAt) return;
        float bw = board.width() * .6f, bx = board.centerX() - bw / 2, by = board.top + 8 * d;
        text.setTypeface(GameKit.BOLD); text.setTextSize(sp(12)); text.setColor(GameKit.alpha(0xFFFFFFFF, fade));
        box.set(bx - 6 * d, by - 4 * d, bx + bw + 6 * d, by + 26 * d); fill.setShader(null); fill.setColor(GameKit.alpha(0xAA14161C, fade)); c.drawRoundRect(box, 10 * d, 10 * d, fill);
        c.drawText(I18n.t(BOSS_NAMES[b.kind % 20]), board.centerX(), by + 9 * d, text);
        box.set(bx, by + 14 * d, bx + bw, by + 20 * d); fill.setColor(GameKit.alpha(0x44FFFFFF, fade)); c.drawRoundRect(box, 3 * d, 3 * d, fill);
        int col = b.kind % 20 == 0 ? 0xFFFFD23F : b.kind % 20 == 1 ? 0xFF8FE3FF : b.kind % 20 == 2 ? 0xFFFF2D55 : BOSS_GLOW[b.kind % 20];
        box.set(bx, by + 14 * d, bx + bw * Math.max(0, b.hp) / (float) b.maxHp, by + 20 * d); fill.setColor(GameKit.alpha(col, fade)); c.drawRoundRect(box, 3 * d, 3 * d, fill);
    }

    Tank spawnPlayer(int level) {
        Tank t = new Tank(); t.player = true; t.x = 8; t.y = 24; t.dir = 0; t.speed = 5.6f; t.hp = t.maxHp = meMax(); t.level = level; t.type = -1;
        t.bornAt = SystemClock.uptimeMillis(); t.shieldUntil = t.bornAt + 900 + 3000;
        tanks.add(t); me = t; return t;
    }

    void spawnEnemy() {
        int[] xs = {0, 12, 24};
        for (int tries = 0; tries < 3; tries++) {
            int sx = xs[(nextSpawn + tries) % 3];
            if (!clearOfTanks(sx, 0, null)) continue;
            Tank t = new Tank(); t.type = queue[Math.max(0, Math.min(19, stageTanks - reserve))]; t.x = sx; t.y = 0; t.dir = 2;
            t.tier = Math.max(0, Math.min(5, (stage - 1) / 2 - (rnd.nextFloat() < .4f ? 1 : 0)));
            t.speed = (t.type == 1 ? 7.2f : t.type == 3 ? 3.4f : t.type == 4 ? 3f : t.type == 5 ? 4.4f : t.type == 6 ? 3.2f : t.type == 7 ? 3.6f : t.type == 8 ? 2.8f : t.type == 9 ? 3.4f : t.type == 10 ? 3.6f : t.type == 11 ? 4.2f : t.type == 12 ? 3.2f : t.type == 13 ? 2.4f : 3.8f) * (1 + .06f * t.tier);
            t.hp = t.maxHp = (t.type == 4 ? 8 + 2 * t.tier : t.type == 7 ? 6 + 2 * t.tier : t.type == 13 ? 12 + 3 * t.tier : (t.type == 3 ? 4 : t.type == 5 ? 2 : t.type == 6 || (t.type >= 8 && t.type <= 11) ? 3 : t.type == 12 ? 2 : 1) + t.tier) + stage / 12;
            if (t.type == 7) t.formCool = 14f + rnd.nextFloat() * 6f;
            int n = stageTanks - reserve; t.bonus = n == 3 || n == 10 || n == 17;
            t.bornAt = SystemClock.uptimeMillis(); t.cool = 1f + rnd.nextFloat(); t.turn = 1.5f + rnd.nextFloat() * 2f; t.phase = rnd.nextFloat() * 6.28f; t.mineCool = 1.5f + rnd.nextFloat() * 2f;
            tanks.add(t); reserve--; nextSpawn = (nextSpawn + tries + 1) % 3;
            PixelStackSound.play(PixelStackSound.SOFT, .22f, 1.3f);
            return;
        }
    }

    boolean ready(Tank t) { return SystemClock.uptimeMillis() - t.bornAt > 900; }

    boolean clearOfTanks(float x, float y, Tank self) {
        for (Tank o : tanks) {
            if (o == self || !o.alive) continue;
            if (x < o.x + 2 - .05f && x + 2 > o.x + .05f && y < o.y + 2 - .05f && y + 2 > o.y + .05f) {
                if (self != null && self.x < o.x + 2 - .05f && self.x + 2 > o.x + .05f && self.y < o.y + 2 - .05f && self.y + 2 > o.y + .05f) continue;
                return false;
            }
        }
        return true;
    }

    boolean solid(int ux, int uy) {
        if (ux < 0 || uy < 0 || ux >= U || uy >= U) return true;
        if (wall[uy][ux] != 0) return true;
        if (water[uy / 2][ux / 2]) return true;
        return ux >= 12 && ux <= 13 && uy >= 24 && uy <= 25;
    }

    boolean free(float x, float y, Tank self) {
        boolean out = x < -.001f || y < -.001f || x + 2 > U + .001f || y + 2 > U + .001f;
        if (out && !(self != null && self == me && doorLets(x, y))) return false;
        int x0 = (int) Math.floor(x + .02f), x1 = (int) Math.floor(x + 2 - .02f), y0 = (int) Math.floor(y + .02f), y1 = (int) Math.floor(y + 2 - .02f);
        for (int uy = y0; uy <= y1; uy++) for (int ux = x0; ux <= x1; ux++) { if (out && (ux < 0 || uy < 0 || ux >= U || uy >= U)) continue; if (solid(ux, uy)) return false; }
        return clearOfTanks(x, y, self);
    }

    static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};

    boolean drive(Tank t, int dir, float dist) {
        if (dir != t.dir) {
            if ((dir % 2) != (t.dir % 2)) {
                float nx = dir % 2 == 0 ? Math.round(t.x) : t.x, ny = dir % 2 == 1 ? Math.round(t.y) : t.y;
                if (free(nx, ny, t)) { t.x = nx; t.y = ny; }
            }
            t.dir = dir;
        }
        float left = dist;
        while (left > 0) {
            float s = Math.min(.2f, left);
            float nx = t.x + DX[dir] * s, ny = t.y + DY[dir] * s;
            if (!free(nx, ny, t)) return t.player && crush(t, nx, ny);
            t.x = nx; t.y = ny; t.moved += s; left -= s; t.movedAt = SystemClock.uptimeMillis();
        }
        return true;
    }

    int crushLevel() { int r = rank >= 5 ? 2 : rank >= 2 ? 1 : 0, f = inferno() >= 5 ? 2 : inferno() >= 1 ? 1 : 0; return Math.max(r, f); }
    boolean crush(Tank t, float x, float y) {
        int lv = crushLevel(); if (lv == 0) return false;
        if (x < -.001f || y < -.001f || x + 2 > U + .001f || y + 2 > U + .001f) return false;
        if (!clearOfTanks(x, y, t)) return false;
        int x0 = (int) Math.floor(x + .02f), x1 = (int) Math.floor(x + 2 - .02f), y0 = (int) Math.floor(y + .02f), y1 = (int) Math.floor(y + 2 - .02f);
        boolean any = false, drum = false, boil = false;
        for (int uy = y0; uy <= y1; uy++) for (int ux = x0; ux <= x1; ux++) {
            if (!solid(ux, uy)) continue;
            byte v = wall[uy][ux];
            if (fortress[uy][ux] || (ux >= 12 && ux <= 13 && uy >= 24)) return false;
            if (water[uy / 2][ux / 2] && v == 0) { if (inferno() < 1) return false; boil = true; any = true; continue; }
            if (v == 4) drum = true; else if (v == 2 && lv < 2) return false; else if (v != 1 && v != 2) return false;
            any = true;
        }
        if (!any) return false;
        if (drum) for (int uy = y0; uy <= y1; uy++) for (int ux = x0; ux <= x1; ux++) if (wall[uy][ux] == 4) blowDrum(ux / 2, uy / 2);
        if (boil) for (int cy = y0 / 2; cy <= y1 / 2; cy++) for (int cx = x0 / 2; cx <= x1 / 2; cx++) if (water[cy][cx]) {
            water[cy][cx] = false; wallsDirty = true;
            for (int i = 0; i < 14; i++) { GameKit.Bit b = spark(px(cx * 2 + 1 + (rnd.nextFloat() - .5f) * 1.6f), py(cy * 2 + 1 + (rnd.nextFloat() - .5f) * 1.6f), i % 3 == 0 ? 0xCCFFFFFF : i % 3 == 1 ? 0xAAD8E4F0 : waterC, 50, unit / d * .45f); b.round = true; b.gravity = -.35f; b.decay = 1.1f; }
            PixelStackSound.play(PixelStackSound.FIRE, .35f, 1.6f); PixelStackSound.play(PixelStackSound.FUSE, .25f, 1.3f);
        }
        for (int uy = y0; uy <= y1; uy++) for (int ux = x0; ux <= x1; ux++) {
            byte v = wall[uy][ux]; if (v != 1 && v != 2) continue;
            wall[uy][ux] = 0; wallsDirty = true;
            float cx = px(ux + .5f), cy = py(uy + .5f);
            for (int i = 0; i < 3; i++) { GameKit.Bit b = spark(cx, cy, v == 2 ? (i % 2 == 0 ? steel : 0xFFFFE08A) : (i % 2 == 0 ? brick : brickDark), 120, unit / d * .32f); b.gravity = 1f; b.decay = 1.4f; }
            if (v == 2) burst(cx, cy, 0xFFFF7A3D, 2, 60, 2.2f);
        }
        crumble(); shake(2); GameKit.tick(a, .35f);
        t.movedAt = SystemClock.uptimeMillis();
        return true;
    }

    void fire(Tank t) {
        if (t.player) {
            long now = SystemClock.uptimeMillis();
            int pw = power();
            if (t.shots >= 6 + pw * 2 || now - lastShotAt < 55) return;
            lastShotAt = now;
            float[] offs = pw >= 4 ? new float[]{0, -.75f, .75f} : pw >= 2 ? new float[]{-.42f, .42f} : new float[]{0};
            boolean rocket = now < rocketUntil, laser = now < laserUntil;
            boolean charge = soul != NONE && !laser && now >= chargeReadyAt;
            if (charge) chargeReadyAt = now + chargeMs();
            int extra = inferno() >= 8 ? 2 : inferno() >= 2 ? 1 : 0;
            for (int oi = 0; oi < offs.length; oi++) {
                float o = offs[oi];
                Shot s = new Shot(); s.from = t; s.dir = t.dir; s.rocket = rocket && !laser; s.laser = laser;
                s.x = t.x + 1 + DX[t.dir] * 1.0f + (t.dir % 2 == 0 ? o : 0); s.y = t.y + 1 + DY[t.dir] * 1.0f + (t.dir % 2 == 1 ? o : 0);
                s.speed = laser ? 34f : rocket ? (soul == INFERNO ? 13f : 16f) : pw >= 1 ? 22f : 16f; s.steel = pw >= 3 || rocket;
                s.dmg = MY_DMG[pw] + (laser ? (soul == THUNDER ? 0 : 1) : extra); s.lvl = pw; s.soul = soul;
                if (charge && oi == 0) { s.charged = true; s.top = soulLv >= TOP; s.dmg += 1; s.id = ++shotIds; s.steel = true; }
                shots.add(s); t.shots++;
                muzzle(px(s.x), py(s.y), rocket ? 0xFFFFA23D : 0xFFFFF1A8);
            }
            PixelStackSound.play(PixelStackSound.SHOT, .42f, (rocket ? .8f : .95f) + rnd.nextFloat() * .1f); GameKit.tick(a, .3f);
            if (charge) { if (soul == THUNDER) PixelStackSound.play(PixelStackSound.TING, .35f, 1.5f); else PixelStackSound.play(PixelStackSound.FIRE, .35f, 1.1f); }
            return;
        }
        if (t.shockUntil > 0 || t.burnUntil > 0) return;
        if (t.shots >= (t.tier >= 3 || (t.type == 7 && t.form == 1) ? 2 : 1)) return;
        Shot s = new Shot(); s.from = t; s.dir = t.dir;
        s.x = t.x + 1 + DX[t.dir] * 1.0f; s.y = t.y + 1 + DY[t.dir] * 1.0f;
        s.speed = (t.type == 2 ? 19f : 12f) * (1 + .08f * t.tier);
        s.steel = (t.tier >= 3 && t.type == 2) || t.type == 4 || (t.type == 7 && t.form == 1);
        shots.add(s); t.shots++; t.firedAt = SystemClock.uptimeMillis();
        PixelStackSound.play(PixelStackSound.SHOT, .16f, .8f);
        muzzle(px(s.x), py(s.y), 0xFFFFC2B8);
    }

    void muzzle(float bx, float by, int colour) { for (int i = 0; i < 4; i++) { GameKit.Bit b = spark(bx, by, colour, 90, 3); b.round = true; b.decay = 4f; } }

    @Override void action(boolean down) { fireHeld = down; if (down && me != null && me.alive && ready(me)) { fire(me); fireCool = .26f; } }

    @Override void stepPlay(float dt) {
        long now = SystemClock.uptimeMillis();
        if (travelAt > 0) { if (me != null && state == PLAY) { stepTravel(dt, now); return; } travelAt = -1; }
        boolean frozen = now < freezeUntil;
        if (state == DYING) { if (since() > 1600) gameOver(); }
        if (me != null && me.alive && state == PLAY && ready(me)) {
            if (dir >= 0) drive(me, dir, me.speed * dt * (1 + .09f * thunder()) * (now < frenzyUntil ? 1.3f : 1f));
            if (fireHeld) { fireCool -= dt; if (fireCool <= 0) { fire(me); fireCool = .22f; } }
        }
        if (state == PLAY && (me == null || !me.alive) && meDeadAt > 0 && now - meDeadAt > 1100) {
            meDeadAt = -1;
            if (lives > 0) { lives--; spawnPlayer(0); } else lose();
        }
        if (state == PLAY) {
            int alive = 0; for (Tank t : tanks) if (!t.player && t.alive) alive++;
            spawnTimer -= (long) (dt * 1000);
            if (reserve > 0 && alive < (stage > 4 ? 5 : 4) && spawnTimer <= 0) { spawnEnemy(); spawnTimer = Math.max(1100, 2600 - stage * 120); }
        }
        for (Tank t : new ArrayList<Tank>(tanks)) {
            if (t.player || !t.alive || !ready(t) || state != PLAY) continue;
            if (t.shockUntil > 0 && now >= t.shockUntil) { flareTank(t); continue; }
            if (t.burnUntil > 0) {
                if (now >= t.burnUntil) t.burnUntil = 0;
                else { t.burnCool -= dt; if (t.burnCool <= 0) { t.burnCool = .5f; fakeHit(t, 1, 0xFFFF7A3D); if (!t.alive) continue; } if (motion && rnd.nextFloat() < .4f) { GameKit.Bit b = spark(px(t.x + 1 + (rnd.nextFloat() - .5f)), py(t.y + .6f), rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A, 50, 4); b.round = true; b.gravity = -.3f; b.decay = 2.5f; } }
            }
            if (frozen || now < t.stunUntil) continue;
            if (t.shockUntil > 0) {
                t.turn -= dt;
                if (!drive(t, t.dir, t.speed * 1.5f * dt) || t.turn <= 0) { t.dir = awayDir(t); t.turn = .4f; }
                if (motion && rnd.nextFloat() < .4f) { GameKit.Bit b = spark(px(t.x + 1 + (rnd.nextFloat() - .5f) * 1.6f), py(t.y + 1 + (rnd.nextFloat() - .5f) * 1.6f), rnd.nextBoolean() ? 0xFFBFF4FF : 0xFFFFFFFF, 90, 2.4f); b.round = true; b.gravity = 0; b.decay = 4f; }
                continue;
            }
            if (t.type == 7 && t.form == 0) { t.formCool -= dt; if (t.formCool <= 0) transformTank(t, now); }
            if (now - t.formAt < FORM_MS) continue;
            t.turn -= dt; t.cool -= dt;
            if (t.type >= 8 && foeSpecial(t, dt, now)) continue;
            float heatSlow = inferno() >= 1 && me != null && me.alive && Math.hypot(t.x - me.x, t.y - me.y) < 6f ? .6f : 1f;
            boolean moved = drive(t, t.dir, t.speed * dt * heatSlow * (t.burnUntil > 0 ? 1.25f : 1f));
            if (!moved && t.type == 13) smash(t);
            boolean aligned = Math.abs(t.x - Math.round(t.x)) < .12f && Math.abs(t.y - Math.round(t.y)) < .12f;
            if (!moved || (t.turn <= 0 && aligned)) { t.dir = way(t, think(t), !moved); t.turn = 1.2f + rnd.nextFloat() * 2.4f; }
            if (!moved && ++t.bumps > 40) { t.bumps = 0; if (t.cool > .15f) t.cool = .15f; } else if (moved) t.bumps = 0;
            if ((t.tier >= 1 || (t.type == 7 && t.form == 1)) && aligned && me != null && me.alive && rnd.nextFloat() < dt * 1.3f * Math.max(t.tier, t.type == 7 && t.form == 1 ? 3 : 0)) {
                float dx = me.x - t.x, dy = me.y - t.y; int aim = -1;
                if (Math.abs(dx) < 1f) aim = dy > 0 ? 2 : 0; else if (Math.abs(dy) < 1f) aim = dx > 0 ? 1 : 3;
                if (aim >= 0) { t.dir = aim; t.turn = .9f; if (t.cool > .3f) t.cool = .3f; }
            }
            if (t.tier >= 2 && aligned && t.turn < 1.6f) for (Shot sh : shots) {
                if (sh.dead || !sh.from.player) continue;
                boolean lane = sh.dir % 2 == 0 ? Math.abs(sh.x - (t.x + 1)) < 1f && (sh.y - (t.y + 1)) * DY[sh.dir] < 0 && Math.abs(sh.y - (t.y + 1)) < 7 : Math.abs(sh.y - (t.y + 1)) < 1f && (sh.x - (t.x + 1)) * DX[sh.dir] < 0 && Math.abs(sh.x - (t.x + 1)) < 7;
                if (lane && rnd.nextFloat() < .5f + .12f * t.tier) { t.dir = (sh.dir + (rnd.nextBoolean() ? 1 : 3)) % 4; t.turn = 2f; break; }
                if (lane) { t.turn = 2f; break; }
            }
            if (t.cool <= 0 && t.type >= 8) foeFire(t, now);
            else if (t.cool <= 0) {
                fire(t);
                boolean pair = t.tier >= 3 && !t.again; t.again = pair;
                t.cool = pair ? .2f : (.7f + rnd.nextFloat() * (t.type == 2 || t.type == 4 ? .9f : 1.6f)) * (1 - .1f * t.tier);
            }
        }
        if (state == PLAY) { stepDrone(dt); stepBoss(dt); }
        stepSoul(dt, now);
        stepNuke(dt, now);
        if (state == PLAY) { stepWave(now); stepStrikes(now); }
        int pwNow = rank;
        if (pwNow > shownPower && !baseDown) { int grow = baseMaxFor() - baseMax; if (grow > 0) { baseMax += grow; baseHp += grow; } }
        if (pwNow > shownPower && me != null && me.alive) {
            morphAt = now; rankAt = now;
            String sub = I18n.t("Level {0}").replace("{0}", String.valueOf(pwNow + 1));
            if (pwNow == 2 || pwNow == 5) sub = I18n.t(pwNow == 2 ? "Crushes bricks" : "Crushes steel");
            levelUp(LV_PLAIN, I18n.t("Level up!"), sub);
            burst(px(me.x + 1), py(me.y + 1), MY_BODY[power()], 22, 240, 5);
            rings.add(new float[]{px(me.x + 1), py(me.y + 1), now, 1.6f});
        }
        shownPower = pwNow;
        for (Shot s : shots) if (!s.dead) flyShot(s, dt);
        for (int i = 0; i < shots.size(); i++) for (int j = i + 1; j < shots.size(); j++) {
            Shot p = shots.get(i), q = shots.get(j);
            if (p.dead || q.dead || p.from.player == q.from.player) continue;
            if (Math.abs(p.x - q.x) < .7f && Math.abs(p.y - q.y) < .7f) { kill(p); kill(q); burst(px(p.x), py(p.y), 0xFFFFF1A8, 6, 120, 3); }
        }
        for (int i = shots.size() - 1; i >= 0; i--) if (shots.get(i).dead) shots.remove(i);
        for (int i = prizes.size() - 1; i >= 0; i--) {
            Prize p = prizes.get(i);
            if (now - p.at > 15000) { prizes.remove(i); continue; }
            if (me != null && me.alive && state == PLAY) {
                float dx = me.x - p.x, dy = me.y - p.y, dist = (float) Math.hypot(dx, dy);
                float mag = p.tapped ? 99f : thunder() >= 2 ? 7.5f : 4.6f;
                if (dist < mag && dist > .01f) {
                    float v = (p.tapped ? 28f : 5f + (mag - dist) * 5f) * dt; if (v > dist) v = dist;
                    p.x += dx / dist * v; p.y += dy / dist * v;
                    if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(px(p.x + 1), py(p.y + 1), TIER_COLOUR[tierOf(p.kind)], 30, 3f); b.round = true; b.gravity = 0; b.decay = 3f; }
                }
            }
            if (me != null && me.alive && Math.abs(me.x - p.x) < 1.3f && Math.abs(me.y - p.y) < 1.3f) { take(p); prizes.remove(i); }
        }
        if (fortressUntil > 0 && now > fortressUntil) { fortressUntil = 0; for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) if (fortress[y][x]) wall[y][x] = 3; wallsDirty = true; }
        for (int i = tanks.size() - 1; i >= 0; i--) if (!tanks.get(i).alive) tanks.remove(i);
        stepDrums(now);
        if (state == PLAY) { stepMortars(now); stepMines(now); }
        stepDoor(dt, now);
    }

    int way(Tank t, int want, boolean blocked) {
        if (!blocked || open(t, want)) { if (open(t, want)) return want; }
        int[] order = {2, (12 - t.x) > 0 ? 1 : 3, (12 - t.x) > 0 ? 3 : 1, 0};
        for (int dir : order) if (dir != t.dir && open(t, dir)) return dir;
        return (t.dir + 2) % 4;
    }
    boolean open(Tank t, int dir) { return free(t.x + DX[dir] * .5f, t.y + DY[dir] * .5f, t); }

    int think(Tank t) {
        float r = rnd.nextFloat();
        if (t.tier >= 4 && r < .45f) { float tx = 12 - t.x; return t.y < 22 ? 2 : Math.abs(tx) < 1 ? 2 : tx > 0 ? 1 : 3; }
        if (r < .3f) return 2;
        if (r < .5f) { float tx = 12 - t.x; return Math.abs(tx) < 1 ? 2 : tx > 0 ? 1 : 3; }
        if (r < .65f && me != null && me.alive) { float dx = me.x - t.x, dy = me.y - t.y; return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0); }
        return rnd.nextInt(4);
    }

    void flyShot(Shot s, float dt) {
        float left = s.speed * dt;
        while (left > 0 && !s.dead) {
            float st = Math.min(.25f, left); left -= st;
            if (s.rocket) steer(s, st);
            s.x += DX[s.dir] * st; s.y += DY[s.dir] * st; s.trail += st;
            if (s.charged && s.top) { s.laid += st; if (s.laid >= .9f) { s.laid = 0; layTrail(s); } }
            if (s.from.player && !crawlers.isEmpty()) { shootCrawlers(s); if (s.dead) return; }
            if (s.from.player && !mines.isEmpty()) { shootMines(s); if (s.dead) return; }
            if (s.x < 0 || s.y < 0 || s.x > U || s.y > U) { kill(s); burst(px(GameKit.clamp(s.x / U) * U), py(GameKit.clamp(s.y / U) * U), steel, 5, 90, 3); PixelStackSound.play(PixelStackSound.TING, .2f, 1.2f); return; }
            if (s.x > 12 && s.x < 14 && s.y > 24 && s.y < 26) { if (s.from.player) continue; kill(s); baseHit(); return; }
            int row, col, c1, c2; softOnly = true;
            if (s.dir % 2 == 0) { row = (int) Math.floor(s.y); c1 = (int) Math.floor(s.x - .5f); c2 = c1 + 1; if ((hitWall(s, c1, row) | hitWall(s, c2, row)) && !(s.laser && softOnly)) { kill(s); return; } }
            else { col = (int) Math.floor(s.x); c1 = (int) Math.floor(s.y - .5f); c2 = c1 + 1; if ((hitWall(s, col, c1) | hitWall(s, col, c2)) && !(s.laser && softOnly)) { kill(s); return; } }
            if (hitBoss(s)) { kill(s); return; }
            for (Tank t : tanks) {
                if (!t.alive || t == s.from || !ready(t)) continue;
                if (s.x < t.x || s.x > t.x + 2 || s.y < t.y || s.y > t.y + 2) continue;
                if (s.from.player == t.player) continue;
                if (s.laser) { if (s.last != t) { s.last = t; hitTank(t, s); } continue; }
                kill(s); hitTank(t, s); return;
            }
        }
    }

    void steer(Shot s, float st) {
        s.sinceTurn += st;
        if (s.sinceTurn < 1.2f) return;
        Tank best = null; float bd = 1e9f;
        for (Tank t : tanks) if (t.alive && !t.player && ready(t)) { float dd = Math.abs(t.x + 1 - s.x) + Math.abs(t.y + 1 - s.y); if (dd < bd) { bd = dd; best = t; } }
        if (best == null) return;
        float dx = best.x + 1 - s.x, dy = best.y + 1 - s.y;
        int want = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0);
        if (want != s.dir && want != (s.dir + 2) % 4) { s.dir = want; s.sinceTurn = 0; if (s.dir % 2 == 0) s.x = Math.round(s.x * 2) / 2f; else s.y = Math.round(s.y * 2) / 2f; }
    }

    boolean softOnly;
    boolean hitWall(Shot s, int ux, int uy) {
        if (ux < 0 || uy < 0 || ux >= U || uy >= U) return false;
        if (s.laser && s.from.player) return laserBurn(ux, uy);
        byte v = wall[uy][ux];
        if (v == 0) return false;
        if (v == 4) { blowDrum(ux / 2, uy / 2); return true; }
        if (s.from.player && fortress[uy][ux]) return false;
        if (v != 1 && v != 3) softOnly = false;
        float cx = px(ux + .5f), cy = py(uy + .5f);
        if (v == 3 && !s.steel) {
            wall[uy][ux] = 1; wallsDirty = true;
            for (int i = 0; i < 3; i++) { GameKit.Bit b = spark(cx, cy, brickDark, 90, unit / d * .25f); b.gravity = 1f; b.decay = 1.5f; }
            return true;
        }
        if (v == 1 || v == 3 || s.steel) {
            wall[uy][ux] = 0; wallsDirty = true;
            for (int i = 0; i < 5; i++) { GameKit.Bit b = spark(cx, cy, i % 2 == 0 ? brick : brickDark, 120, unit / d * .35f); b.gravity = 1f; b.decay = 1.3f; }
            if (s.from.player) crumble();
        } else {
            burst(cx, cy, 0xFFE8EEF8, 4, 80, 2.5f);
            if (s.from.player) PixelStackSound.play(PixelStackSound.TING, .3f, 1f);
        }
        return true;
    }

    boolean laserBurn(int ux, int uy) {
        if (ux < 0 || uy < 0 || ux >= U || uy >= U) return false;
        float cx = px(ux + .5f), cy = py(uy + .5f);
        if (bush[uy / 2][ux / 2]) {
            bush[uy / 2][ux / 2] = false; wallsDirty = true;
            for (int i = 0; i < 6; i++) { GameKit.Bit b = spark(px((ux / 2) * 2 + 1), py((uy / 2) * 2 + 1), i % 2 == 0 ? bushC : 0xFFFFB347, 110, unit / d * .3f); b.gravity = -.2f; b.decay = 1.4f; }
            crumble();
        }
        byte v = wall[uy][ux];
        if (v == 4) { blowDrum(ux / 2, uy / 2); return false; }
        if (v == 0 || fortress[uy][ux]) return false;
        wall[uy][ux] = 0; wallsDirty = true;
        boolean hard = v == 2;
        for (int i = 0; i < (hard ? 6 : 4); i++) { GameKit.Bit b = spark(cx, cy, hard ? (i % 2 == 0 ? steel : 0xFFFFE08A) : (i % 2 == 0 ? brick : brickDark), hard ? 150 : 110, unit / d * .3f); b.gravity = hard ? .4f : 1f; b.decay = 1.4f; }
        if (hard) { burst(cx, cy, 0xFFFF7A3D, 3, 70, 2.5f); PixelStackSound.play(PixelStackSound.TING, .22f, 1.5f + rnd.nextFloat() * .2f); }
        crumble();
        return false;
    }

    long crumbleAt;
    void crumble() {
        long now = SystemClock.uptimeMillis();
        if (now - crumbleAt < 90) return;
        crumbleAt = now;
        PixelStackSound.play(PixelStackSound.CRUMBLE, .34f, .85f + rnd.nextFloat() * .3f);
    }

    void kill(Shot s) { if (s.dead) return; s.dead = true; s.from.shots = Math.max(0, s.from.shots - 1); if (s.charged && s.from.player) impact(s); }

    void hitTank(Tank t, Shot s) {
        long now = SystemClock.uptimeMillis();
        if (t.player) {
            if (now < t.shieldUntil) { burst(px(s.x), py(s.y), GameKit.LIGHT, 6, 100, 3); PixelStackSound.play(PixelStackSound.TING, .3f, 1.4f); return; }
            if (thunder() >= 1 && capReady) {
                capReady = false; capAt = now; t.shieldUntil = now + 1500;
                burst(px(t.x + 1), py(t.y + 1), 0xFF7FE9FF, 22, 260, 5); rings.add(new float[]{px(t.x + 1), py(t.y + 1), now, 1.5f});
                PixelStackSound.play(PixelStackSound.TING, .6f, 1.2f); PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.1f); GameKit.thump(a, .8f);
                return;
            }
            if (overshield(now)) { overBrokenAt = now; burst(px(t.x + 1), py(t.y + 1), wardColour(wardStyle()), 20, 220, 4); rings.add(new float[]{px(t.x + 1), py(t.y + 1), now, 1.3f}); PixelStackSound.play(PixelStackSound.TING, .5f, .8f); GameKit.thump(a, .6f); return; }
            hurtMe(1, now); return;
        }
        if (!t.alive) return;

        boolean hellRocket = s.rocket && !s.mini && mine(s) && s.soul == INFERNO;
        if (s.laser && mine(s) && t.type != 7 && thunder() > 0 && oneShot(t, thunder())) { shockTank(t); return; }
        if (t.type == 10 && !s.laser && !(hellRocket && inferno() >= 3) && s.dir == (t.dir + 2) % 4) { burst(px(s.x), py(s.y), 0xFF7FE9FF, 8, 140, 3); PixelStackSound.play(PixelStackSound.TING, .35f, 1.6f); return; }
        if (t.bonus) { t.bonus = false; drop(); }
        int n = Math.max(1, s.dmg) + (s.rocket && !s.mini ? 1 : 0);
        if (hellRocket) n = t.type != 7 && oneShot(t, inferno()) ? Math.max(n, t.hp) : n + 1 + inferno() / 2;
        else if (s.laser && mine(s) && thunder() > 0) n += 1 + thunder() / 2;
        t.hp -= n; t.flashAt = now;
        if (s.laser) t.zapAt = now;
        int ink = s.ink != 0 ? s.ink : s.laser ? 0xFF7FE9FF : s.charged ? (s.soul == THUNDER ? 0xFF7FE9FF : 0xFFFF7A3D) : mine(s) && inferno() >= 2 ? 0xFFFF7A3D : 0xFFFFE066;
        hitNumber(px(t.x + 1), py(t.y) - unit * .3f, n, ink, t.hp <= 0 || n >= 3);
        if (hellRocket) rocketWay(t, s, now);
        if (s.mini && s.soul == INFERNO && t.hp > 0) burnTank(t);
        if (t.hp > 0) {
            if (t.type == 7 && t.form == 0 && t.hp <= t.maxHp / 2) transformTank(t, now);
            if (mine(s) && thunder() >= 6) t.stunUntil = Math.max(t.stunUntil, now + 1200);
            if (mine(s) && inferno() >= 6) burnTank(t);
            burst(px(s.x), py(s.y), 0xFFFFFFFF, 6, 110, 3); PixelStackSound.play(PixelStackSound.TING, .35f, .9f + .1f * t.hp); GameKit.tick(a, .4f); return;
        }
        enemyDown(t, false);
    }

    boolean oneShot(Tank t, int lv) { return lv >= TOP || t.maxHp <= 1 + lv * 1.5f; }

    void rocketWay(Tank t, Shot s, long now) {
        if (t.hp > 0) burnTank(t);
        rocketBurst(s);
    }

    void rocketBurst(Shot s) {
        long now = SystemClock.uptimeMillis();
        int lv = Math.max(1, inferno()); float reach = 1.4f + .12f * lv;
        for (Tank o : new ArrayList<Tank>(tanks)) if (o.alive && !o.player && ready(o) && o.hp > 0 && Math.hypot(o.x + 1 - s.x, o.y + 1 - s.y) < reach) burnTank(o);
        for (int i = 0, n = 1 + lv / 4; i < n; i++) { Burn z = new Burn(); double ang = i * Math.PI * 2 / Math.max(1, n - 1) + s.id; z.x = i == 0 ? s.x : s.x + (float) Math.cos(ang) * 1.2f; z.y = i == 0 ? s.y : s.y + (float) Math.sin(ang) * 1.2f; z.at = now + i * 50; burns.add(z); }
        while (burns.size() > 40) burns.remove(0);
        burst(px(s.x), py(s.y), 0xFFFF7A3D, 14 + lv, 220 + 8 * lv, 5); burst(px(s.x), py(s.y), 0xFFFFD86B, 8 + lv / 2, 180, 3); burst(px(s.x), py(s.y), 0xFF5A0A12, 6 + lv / 2, 140, 4);
        rings.add(new float[]{px(s.x), py(s.y), now, .8f + .05f * lv});
        if (lv >= 7) rings.add(new float[]{px(s.x), py(s.y), now, 1.3f + .06f * lv});
        shake(4 + lv / 2); PixelStackSound.play(PixelStackSound.FIRE, .45f, .7f); PixelStackSound.play(PixelStackSound.BOOM, .35f, .6f);
    }

    void hellMissile(Canvas c, Shot s, float x, float y, float r, float fade) {
        long now = SystemClock.uptimeMillis();
        int lv = Math.max(1, inferno());
        float R = r * (1.3f + .04f * lv), ang = s.dir == 1 ? 0 : s.dir == 2 ? 90 : s.dir == 3 ? 180 : -90;
        fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFF5A1F, fade * (.12f + .02f * lv))); c.drawCircle(x, y, R * (2.4f + .1f * lv), fill);
        int sv = c.save(); c.translate(x, y); c.rotate(ang);
        int[] fc = {0xFFC81D2A, 0xFFFF7A3D, 0xFFFFE9A8}; float[] fw = {.9f, .62f, .32f}, fl = {3.4f, 2.5f, 1.5f};
        for (int k = 0; k < 3; k++) {
            float L = R * fl[k] * (.75f + .05f * lv) * (motion ? .85f + .15f * (float) Math.sin(now / 38.0 + k * 2.1 + s.id) : 1f), w = R * fw[k];
            android.graphics.Path f = GameKit.path(); f.moveTo(-R * 1.2f, -w);
            f.quadTo(-R * 1.2f - L * .55f, -w * (motion ? .7f + .3f * (float) Math.sin(now / 50.0 + k) : .8f), -R * 1.2f - L, 0);
            f.quadTo(-R * 1.2f - L * .55f, w * (motion ? .7f + .3f * (float) Math.cos(now / 46.0 + k) : .8f), -R * 1.2f, w); f.close();
            fill.setColor(GameKit.alpha(fc[k], fade * (k == 0 ? .8f : .95f))); c.drawPath(f, fill);
        }
        fill.setColor(GameKit.alpha(0xFF3A0A10, fade));
        android.graphics.Path fin = GameKit.path(); fin.moveTo(-R * .6f, -R * .55f); fin.lineTo(-R * 1.35f, -R * 1.05f); fin.lineTo(-R * 1.25f, -R * .5f); fin.close(); c.drawPath(fin, fill);
        fin = GameKit.path(); fin.moveTo(-R * .6f, R * .55f); fin.lineTo(-R * 1.35f, R * 1.05f); fin.lineTo(-R * 1.25f, R * .5f); fin.close(); c.drawPath(fin, fill);
        box.set(-R * 1.3f, -R * .58f, R * .75f, R * .58f); fill.setColor(GameKit.alpha(0xFF2A0508, fade)); c.drawRoundRect(box, R * .5f, R * .5f, fill);
        stroke.setStrokeWidth(Math.max(1f, R * .1f)); stroke.setColor(GameKit.alpha(0xFFFF5A1F, fade)); c.drawRoundRect(box, R * .5f, R * .5f, stroke);
        fill.setColor(GameKit.alpha(0xFFFF7A3D, fade)); c.drawRect(-R * .95f, -R * .58f, -R * .78f, R * .58f, fill);
        fill.setColor(GameKit.alpha(0xFFFFD86B, fade)); c.drawCircle(R * .62f, 0, R * .42f, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(R * .7f, 0, R * .2f, fill);
        c.restoreToCount(sv);
        if (lv >= 4) tankSign(c, x, y, R * .42f, INFERNO, lv, now, fade, true);
        if (lv >= 8) { stroke.setStrokeWidth(Math.max(1f, R * .12f)); stroke.setColor(GameKit.alpha(0xFFFFD86B, fade * (motion ? .4f + .3f * (float) Math.sin(now / 70.0) : .5f))); c.drawCircle(x, y, R * 1.6f, stroke); }
        if (motion && rnd.nextFloat() < .35f + .04f * lv) { GameKit.Bit b = spark(x - DX[s.dir] * R * 2.6f, y - DY[s.dir] * R * 2.6f, rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFD86B, 50, 2.8f); b.round = true; b.gravity = -.08f; b.decay = 2.8f; }
        if (motion && rnd.nextFloat() < .35f) { GameKit.Bit b = spark(x - DX[s.dir] * R * 3.2f, y - DY[s.dir] * R * 3.2f, night ? 0x66FFFFFF : 0x55301216, 25, 4.5f); b.round = true; b.gravity = -.05f; b.decay = 1.8f; }
    }

    final ArrayList<float[]> arcs = new ArrayList<float[]>();
    void drawArcs(Canvas c, long now) {
        for (int i = arcs.size() - 1; i >= 0; i--) {
            float[] a = arcs.get(i); float t = now - a[4];
            if (t > 260) { arcs.remove(i); continue; }
            bolt(c, px(a[0]), py(a[1]), px(a[2]), py(a[3]), unit * .6f, 6, now / 35 + i, 0xFF7FE9FF, Math.max(1.5f, unit * .12f), 1 - t / 260f);
        }
    }

    int tankColour(Tank t) {
        if (t.player) { int b = MY_BODY[Math.max(0, Math.min(4, power()))]; return thunder() > 0 ? GameKit.mix(b, 0xFF2FA8FF, .05f * thunder()) : inferno() > 0 ? GameKit.mix(b, 0xFF5A0A12, .07f * inferno()) : b; }
        int c = t.type == 0 ? GREEN : t.type == 1 ? YELLOW : t.type == 2 ? ORANGE : t.type == 4 ? 0xFF2A2F3D : t.type == 5 ? 0xFFCFDAF5 : t.type == 6 ? 0xFF2E8B7A : t.type == 7 ? (t.form == 1 ? 0xFF3A0A10 : 0xFF4A4F5C) : t.type == 8 ? 0xFF8D6E63 : t.type == 9 ? 0xFF546E7A : t.type == 10 ? 0xFF0097A7 : t.type == 11 ? 0xFFD84315 : t.type == 12 ? 0xFF7E57C2 : t.type == 13 ? 0xFF455A64 : RED;
        if (t.type == 3) c = GameKit.mix(0xFF8E2BB6, RED, (t.hp - 1) / (float) Math.max(1, t.maxHp - 1));
        if (t.tier > 0) c = GameKit.mix(c, 0xFF14161C, .08f * t.tier);
        return c;
    }

    void explode(Tank t, boolean big) {
        t.alive = false;
        float cx = px(t.x + 1), cy = py(t.y + 1);
        int c = tankColour(t);
        burst(cx, cy, c, big ? 26 : 18, 260, 6);
        burst(cx, cy, 0xFFFFE08A, 10, 200, 4);
        rings.add(new float[]{cx, cy, SystemClock.uptimeMillis(), big ? 1.6f : 1.2f});
        shake(big ? 12 : 7);
        PixelStackSound.play(PixelStackSound.BOOM, big ? .6f : .45f, big ? .8f : 1f + rnd.nextFloat() * .15f);
        if (big) GameKit.rumble(a); else GameKit.thump(a, .7f);
    }
    final ArrayList<float[]> rings = new ArrayList<float[]>();

    void baseHit() {
        if (baseDown) return;
        long now = SystemClock.uptimeMillis();
        if (now < armourUntil) { burst(px(13), py(25), 0xFF7FE9FF, 6, 120, 3); PixelStackSound.play(PixelStackSound.TING, .3f, 1.3f); return; }
        if (baseShield > 0) { baseShield--; baseShieldAt = now; burst(px(13), py(25), 0xFF7FE9FF, 12, 180, 3); rings.add(new float[]{px(13), py(25), now, 1.4f}); PixelStackSound.play(PixelStackSound.TING, .45f, 1.1f); GameKit.thump(a, .5f); return; }
        if (--baseHp > 0) {
            baseHurtAt = now;
            burst(px(13), py(25), GameKit.LIGHT, 12, 200, 4);
            rings.add(new float[]{px(13), py(25), now, 1.2f});
            shake(8); GameKit.thump(a, .9f);
            PixelStackSound.play(PixelStackSound.BOOM, .45f, 1.2f);
            pop(baseHp + "/" + baseMax, px(13), py(23.4f), 0xFFE53935, 16);
            if (now - sirenAt > 2800) { sirenAt = now; PixelStackSound.play(PixelStackSound.SIREN, .26f, 1f); }
            return;
        }
        baseDown = true; baseHitAt = now;
        float cx = px(13), cy = py(25);
        burst(cx, cy, GameKit.BLUE, 24, 300, 7); burst(cx, cy, GameKit.LIGHT, 18, 240, 6);
        rings.add(new float[]{cx, cy, baseHitAt, 2.2f});
        shake(16); GameKit.rumble(a);
        PixelStackSound.play(PixelStackSound.BOOM, .7f, .7f);
        lose();
    }

    long waveAt = -1; float waveX, waveY; int waveId; boolean waveBoss;
    static final float WAVE_MS = 950, WAVE_REACH = 40f;
    void shockwave() {
        if (me == null) return;
        long now = SystemClock.uptimeMillis();
        waveAt = now; waveX = me.x + 1; waveY = me.y + 1; waveId++; waveBoss = false;
        rings.add(new float[]{px(waveX), py(waveY), now, 3f});
        shake(18); GameKit.rumble(a);
        PixelStackSound.play(PixelStackSound.BOOM, .7f, .6f); PixelStackSound.play(PixelStackSound.RISE, .35f, 1.4f);
        for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) if (wall[y][x] == 1 && !fortress[y][x] && Math.hypot(x + .5f - waveX, y + .5f - waveY) < 4.2f) {
            wall[y][x] = 0; wallsDirty = true;
            GameKit.Bit b = spark(px(x + .5f), py(y + .5f), brick, 200, unit / d * .35f); b.gravity = 1f; b.decay = 1.3f; b.vx += (x + .5f - waveX) * 40 * d; b.vy += (y + .5f - waveY) * 40 * d;
        }
    }
    void stepWave(long now) {
        if (waveAt < 0) return;
        float t = (now - waveAt) / WAVE_MS; if (t >= 1) { waveAt = -1; return; }
        float r = WAVE_REACH * GameKit.ease(t);
        if (nukeGun == null) { nukeGun = new Tank(); nukeGun.player = true; nukeGun.alive = false; }
        for (Tank tk : new ArrayList<Tank>(tanks)) {
            if (tk.player || !tk.alive || !ready(tk) || tk.waved == waveId) continue;
            float dx = tk.x + 1 - waveX, dy = tk.y + 1 - waveY;
            if (Math.hypot(dx, dy) > r) continue;
            tk.waved = waveId;
            Shot fake = new Shot(); fake.from = nukeGun; fake.x = tk.x + 1; fake.y = tk.y + 1; fake.dmg = 3;
            hitTank(tk, fake);
            if (!tk.alive) continue;
            tk.stunUntil = now + 4000;
            drive(tk, Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0), 2.2f);
            burst(px(tk.x + 1), py(tk.y + 1), GameKit.LIGHT, 8, 160, 3);
        }
        for (Shot s : shots) if (!s.dead && !s.from.player && Math.hypot(s.x - waveX, s.y - waveY) <= r) { kill(s); burst(px(s.x), py(s.y), GameKit.LIGHT, 4, 90, 2.5f); }
        if (!waveBoss && boss != null && boss.alive && bossIn() && Math.hypot(boss.x - waveX, boss.y - waveY) <= r) { waveBoss = true; boss.hp = Math.max(1, boss.hp - 25); boss.hitAt = now; burst(px(boss.x), py(boss.y), GameKit.LIGHT, 20, 260, 5); shake(10); }
    }
    void drawWave(Canvas c, long now) {
        if (waveAt < 0) return;
        float t = (now - waveAt) / WAVE_MS; if (t >= 1 || t < 0) return;
        float r = unit * WAVE_REACH * GameKit.ease(t), cx = px(waveX), cy = py(waveY), a0 = 1 - t;
        int col = night ? GameKit.LIGHT : GameKit.BLUE;
        fill.setShader(null); fill.setColor(GameKit.alpha(col, .10f * a0)); c.drawCircle(cx, cy, r, fill);
        stroke.setStrokeWidth(unit * 2.4f * (1 - t * .5f)); stroke.setColor(GameKit.alpha(col, .28f * a0)); c.drawCircle(cx, cy, r, stroke);
        stroke.setStrokeWidth(unit * .9f); stroke.setColor(GameKit.alpha(col, .6f * a0)); c.drawCircle(cx, cy, r, stroke);
        stroke.setStrokeWidth(unit * .28f); stroke.setColor(GameKit.alpha(0xFFFFFFFF, .9f * a0)); c.drawCircle(cx, cy, r + unit * .5f, stroke);
        stroke.setStrokeWidth(unit * .2f); stroke.setColor(GameKit.alpha(col, .45f * a0)); c.drawCircle(cx, cy, Math.max(0, r - unit * 2.6f), stroke); c.drawCircle(cx, cy, Math.max(0, r - unit * 4.6f), stroke);
        stroke.setStrokeWidth(unit * .16f); stroke.setColor(GameKit.alpha(0xFFFFFFFF, .7f * a0));
        for (int i = 0; i < 20; i++) { double ang = i * Math.PI / 10 + waveId; float r0 = r + unit * .8f, r1 = r + unit * (1.6f + (i % 3) * .5f); c.drawLine(cx + (float) Math.cos(ang) * r0, cy + (float) Math.sin(ang) * r0, cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1, stroke); }
    }

    static final class Strike { Tank on; float x, y; long at; boolean done, boss; }
    final ArrayList<Strike> strikes = new ArrayList<Strike>(); long jetAt = -1;
    void airstrike() {
        long now = SystemClock.uptimeMillis(); int n = 0;
        jetAt = now;
        for (Tank t : tanks) if (!t.player && t.alive) { Strike s = new Strike(); s.on = t; s.x = t.x + 1; s.y = t.y + 1; s.at = now + 650 + n++ * 150; strikes.add(s); }
        if (boss != null && boss.alive && bossIn()) for (int k = 0; k < 3; k++) { Strike s = new Strike(); s.boss = true; s.x = boss.x + (k - 1) * 1.4f; s.y = boss.y; s.at = now + 650 + n++ * 150; strikes.add(s); }
        for (int k = 0; k < 3; k++) { Strike s = new Strike(); s.x = 2 + rnd.nextFloat() * (U - 4); s.y = 2 + rnd.nextFloat() * 16; s.at = now + 650 + n++ * 150; strikes.add(s); }
        PixelStackSound.play(PixelStackSound.RISE, .5f, 1.5f); GameKit.thump(a, .6f);
    }
    void stepStrikes(long now) {
        if (nukeGun == null) { nukeGun = new Tank(); nukeGun.player = true; nukeGun.alive = false; }
        for (int i = strikes.size() - 1; i >= 0; i--) {
            Strike s = strikes.get(i);
            if (s.on != null && s.on.alive) { s.x = s.on.x + 1; s.y = s.on.y + 1; }
            if (s.boss && boss != null && boss.alive) s.y = boss.y;
            if (now < s.at) continue;
            strikes.remove(i);
            float cx = px(s.x), cy = py(s.y);
            for (Tank t : new ArrayList<Tank>(tanks)) if (!t.player && t.alive && ready(t) && Math.hypot(t.x + 1 - s.x, t.y + 1 - s.y) < 1.9f) { Shot fake = new Shot(); fake.from = nukeGun; fake.x = t.x + 1; fake.y = t.y + 1; fake.dmg = 5; hitTank(t, fake); }
            if (boss != null && boss.alive && bossIn() && Math.abs(s.x - boss.x) < BOSS_W + .5f && Math.abs(s.y - boss.y) < BOSS_H + .5f) { boss.hp = Math.max(1, boss.hp - 12); boss.hitAt = now; }
            for (int y = (int) (s.y - 2); y <= (int) (s.y + 2); y++) for (int x = (int) (s.x - 2); x <= (int) (s.x + 2); x++) {
                if (x < 0 || y < 0 || x >= U || y >= U || wall[y][x] != 1 || fortress[y][x] || Math.hypot(x + .5f - s.x, y + .5f - s.y) > 1.8f) continue;
                wall[y][x] = 0; wallsDirty = true;
                GameKit.Bit b = spark(px(x + .5f), py(y + .5f), x % 2 == 0 ? brick : brickDark, 160, unit / d * .35f); b.gravity = 1f; b.decay = 1.3f;
            }
            burst(cx, cy, 0xFFFFE08A, 16, 300, 6); burst(cx, cy, 0xFFFF7A3D, 12, 240, 5);
            rings.add(new float[]{cx, cy, now, 2f});
            shake(11); GameKit.thump(a, .9f);
            PixelStackSound.play(PixelStackSound.BOOM, .6f, .8f + rnd.nextFloat() * .3f);
        }
    }
    void drawStrikes(Canvas c, long now) {
        if (jetAt > 0) {
            float t = (now - jetAt) / 700f;
            if (t >= 1) jetAt = -1;
            else {
                float x = board.left + board.width() * (.25f + .5f * t), y = board.bottom + unit * 3 - (board.height() + unit * 6) * t, s = unit * 2.6f;
                android.graphics.Path p = GameKit.path();
                p.moveTo(x, y - s * 1.3f); p.lineTo(x + s * .22f, y - s * .2f); p.lineTo(x + s * 1.2f, y + s * .5f); p.lineTo(x + s * .22f, y + s * .4f); p.lineTo(x + s * .5f, y + s * 1.1f); p.lineTo(x, y + s * .9f);
                p.lineTo(x - s * .5f, y + s * 1.1f); p.lineTo(x - s * .22f, y + s * .4f); p.lineTo(x - s * 1.2f, y + s * .5f); p.lineTo(x - s * .22f, y - s * .2f); p.close();
                fill.setShader(null); fill.setColor(night ? 0x55000000 : 0x33000000); c.drawPath(p, fill);
                fill.setColor(GameKit.alpha(GameKit.LIGHT, .25f)); c.drawPath(p, fill);
            }
        }
        for (Strike s : strikes) {
            float cx = px(s.x), cy = py(s.y), left = s.at - now, m = unit * 1.5f;
            float lock = GameKit.clamp(1 - (left - 300) / 500f);
            float q = m * (1.6f - .6f * lock);
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(0xFFE53935, .5f + .5f * (float) Math.sin(now / 70.0)));
            for (int k = 0; k < 4; k++) { float sx = k % 2 == 0 ? -1 : 1, sy = k < 2 ? -1 : 1; c.drawLine(cx + sx * q, cy + sy * q, cx + sx * q * .55f, cy + sy * q, stroke); c.drawLine(cx + sx * q, cy + sy * q, cx + sx * q, cy + sy * q * .55f, stroke); }
            fill.setShader(null); fill.setColor(0xFFE53935); c.drawCircle(cx, cy, unit * .16f, fill);
            if (left < 300) {
                float p = 1 - left / 300f, mx = cx - (1 - p) * unit * 5, my = cy - (1 - p) * unit * 16;
                stroke.setStrokeWidth(unit * .35f); stroke.setColor(0x66FFE08A); c.drawLine(mx - unit * .9f, my - unit * 2.9f, mx, my, stroke);
                stroke.setStrokeWidth(unit * .55f); stroke.setColor(0xFFE8EEF8); c.drawLine(mx - unit * .25f, my - unit * .8f, mx, my, stroke);
                fill.setColor(0xFFE53935); c.drawCircle(mx, my, unit * .3f, fill);
            }
        }
    }

    void stepDrone(float dt) {
        long now = SystemClock.uptimeMillis();
        if (now >= droneUntil || me == null || !me.alive) return;
        droneAngle += dt * 3.2f;
        droneCool -= dt;
        if (droneCool > 0) return;
        Tank best = null; float bd = 1e9f;
        for (Tank t : tanks) if (t.alive && !t.player && ready(t)) { float dd = Math.abs(t.x - me.x) + Math.abs(t.y - me.y); if (dd < bd) { bd = dd; best = t; } }
        if (best == null) return;
        droneCool = .8f;
        if (drone == null) { drone = new Tank(); drone.player = true; drone.alive = false; }
        float dx0 = me.x + 1 + (float) Math.cos(droneAngle) * 1.6f, dy0 = me.y + 1 + (float) Math.sin(droneAngle) * 1.6f;
        if (soul == THUNDER) {
            int th = thunder();
            if (bd > 6f + .4f * th) { droneCool = .3f; return; }
            droneCool = Math.max(.4f, 1.1f - .07f * th);
            arcs.add(new float[]{dx0, dy0, best.x + 1, best.y + 1, now});
            best.zapAt = now; best.stunUntil = Math.max(best.stunUntil, now + 450 + 40L * th);
            burst(px(best.x + 1), py(best.y + 1), 0xFFBFF4FF, 6 + th / 2, 150, 3); PixelStackSound.play(PixelStackSound.TING, .28f, 1.7f);
            Tank second = null;
            if (th >= 6) { float sd = 4f; for (Tank o : tanks) if (o != best && o.alive && !o.player && ready(o)) { float dd = (float) Math.hypot(o.x - best.x, o.y - best.y); if (dd < sd) { sd = dd; second = o; } } }
            if (second != null) { arcs.add(new float[]{best.x + 1, best.y + 1, second.x + 1, second.y + 1, now}); second.zapAt = now; second.stunUntil = Math.max(second.stunUntil, now + 300 + 30L * th); }
            fakeHit(best, 1 + th / 5, 0xFF7FE9FF);
            if (second != null && second.alive) fakeHit(second, 1, 0xFF7FE9FF);
            return;
        }
        Shot s = new Shot(); s.from = drone; s.rocket = true; s.mini = true; s.speed = 14f; s.steel = false;
        if (soul == INFERNO) { s.soul = INFERNO; s.ink = 0xFFFF7A3D; s.speed = 10f + .3f * inferno(); s.dmg = 1 + inferno() / 4; droneCool = Math.max(.4f, .95f - .055f * inferno()); }
        float ex = best.x + 1 - dx0, ey = best.y + 1 - dy0;
        s.dir = Math.abs(ex) > Math.abs(ey) ? (ex > 0 ? 1 : 3) : (ey > 0 ? 2 : 0);
        s.x = Math.max(.2f, Math.min(U - .2f, dx0)); s.y = Math.max(.2f, Math.min(U - .2f, dy0));
        shots.add(s);
        muzzle(px(s.x), py(s.y), soul == INFERNO ? 0xFFFF7A3D : 0xFF9EC1FF);
        if (soul == INFERNO) PixelStackSound.play(PixelStackSound.FIRE, .22f, 1.3f); else PixelStackSound.play(PixelStackSound.SHOT, .22f, 1.4f);
    }

    void lose() { if (state == PLAY) { go(DYING); fireHeld = false; } }

    boolean open(int x, int y) {
        if (x < 0 || y < 0 || x + 2 > U || y + 2 > U) return false;
        if (water[y / 2][x / 2] || bush[y / 2][x / 2]) return false;
        for (int uy = y; uy < y + 2; uy++) for (int ux = x; ux < x + 2; ux++) if (wall[uy][ux] != 0 || fortress[uy][ux] || (ux >= 12 && ux <= 13 && uy >= 24)) return false;
        return true;
    }

    void drop() {
        Prize p = new Prize(); p.at = SystemClock.uptimeMillis();
        float roll = rnd.nextFloat(); int[] from = GUIDE[roll < .58f ? 0 : roll < .9f ? 1 : 2];
        p.kind = from[rnd.nextInt(from.length)];
        boolean placed = false;
        for (int tries = 0; tries < 200 && !placed; tries++) {
            int x = rnd.nextInt(13) * 2, y = rnd.nextInt(12) * 2;
            if (open(x, y)) { p.x = x; p.y = y; placed = true; }
        }
        if (!placed) return;
        prizes.add(p);
        PixelStackSound.play(PixelStackSound.SPARKLE, .4f, 1.1f);
    }

    void take(Prize p) {
        long now = SystemClock.uptimeMillis();
        String words;
        switch (p.kind) {
            case STAR: starXp += 3; while (rank < RANKS.length && xp() >= RANKS[rank]) rank++; words = I18n.t("Power up!"); break;
            case SHIELD: me.shieldUntil = now + 10000; words = I18n.t("Shield"); break;
            case FREEZE: case BLAST: case WAVE: case NUKE:
                if (stash(p.kind)) words = abilityName(p.kind); else { useAbility(p.kind); words = null; }
                break;
            case FORT:
                fortressUntil = now + 15000; words = I18n.t("Fortress");
                for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) if (fortress[y][x]) wall[y][x] = 2;
                wallsDirty = true;
                baseHp = Math.min(baseMax, baseHp + 2);
                break;
            case ROCKET: rocketUntil = now + 10000; words = I18n.t("Rockets!"); break;
            case DOUBLE: doubleUntil = now + 12000; words = I18n.t("Double points"); break;
            case LASER: laserUntil = now + 9000; words = I18n.t("Laser!"); break;
            case DRONE: droneUntil = now + 15000; words = I18n.t("Helper drone!"); break;
            default: lives++; words = I18n.t("Extra life");
        }
        addScore(500);
        int tier = tierOf(p.kind);
        if (words != null) pop(words, px(p.x + 1), py(p.y), tier > 0 ? TIER_COLOUR[tier] : night ? GameKit.LIGHT : GameKit.BLUE, tier == 2 ? 22 : 18);
        if (tier == 2) { rings.add(new float[]{px(p.x + 1), py(p.y + 1), now, 2.2f}); burst(px(p.x + 1), py(p.y + 1), 0xFFFFC531, 22, 260, 5); PixelStackSound.play(PixelStackSound.SUCCESS, .5f, 1.1f); }
        burst(px(p.x + 1), py(p.y + 1), GameKit.LIGHT, 14, 180, 4);
        PixelStackSound.play(PixelStackSound.PICK, .5f, 1f);
        GameKit.thump(a, .6f);
    }

    android.graphics.Bitmap wallPic; boolean wallsDirty = true, lastBlink, waterOnly, skipWater;

    void drawField(Canvas c, float fade, float[] drop, boolean bushes) {
        long now = SystemClock.uptimeMillis();
        world();
        boolean blink = fortressUntil > 0 && fortressUntil - now < 3000 && (now / 200) % 2 == 0;
        if (drop == null && !bushes && fade >= 1f && !skipWater && !waterOnly && board.width() > 0) {
            if (blink != lastBlink) { lastBlink = blink; wallsDirty = true; }
            int pw = (int) Math.ceil(board.width()), ph = (int) Math.ceil(board.height());
            if (wallPic == null || wallPic.getWidth() != pw || wallPic.getHeight() != ph) { wallPic = android.graphics.Bitmap.createBitmap(pw, ph, android.graphics.Bitmap.Config.ARGB_8888); wallsDirty = true; }
            waterOnly = true; drawField(c, 1f, null, false); waterOnly = false;
            if (wallsDirty) {
                wallsDirty = false; wallPic.eraseColor(0);
                Canvas wc = new Canvas(wallPic); wc.translate(-board.left, -board.top);
                skipWater = true; drawField(wc, 1f, null, false); skipWater = false;
            }
            c.drawBitmap(wallPic, board.left, board.top, null);
            return;
        }
        for (int cy = 0; cy < N; cy++) for (int cx = 0; cx < N; cx++) {
            float off = drop == null ? 0 : drop[cy * N + cx];
            float a0 = drop == null ? fade : fade * GameKit.clamp(1 - Math.abs(off) / (40 * d));
            if (a0 <= 0) continue;
            if (!bushes) {
                if (water[cy][cx] && !skipWater) drawWater(c, cx, cy, off, a0, now);
                if (waterOnly) continue;
                for (int k = 0; k < 4; k++) {
                    int ux = cx * 2 + k % 2, uy = cy * 2 + k / 2;
                    byte v = wall[uy][ux];
                    if (v == 0) continue;
                    if (blink && fortress[uy][ux]) v = 3;
                    float l = px(ux), t = py(uy) + off, g = unit * .08f;
                    if (v == 4) { if (ux % 2 == 0 && uy % 2 == 0) drawDrum(c, l, t, a0, now); continue; }
                    if (v == 1 || v == 3) {
                        float h = (unit - g * 3) / 2f;
                        int shade = (ux + uy) % 2 == 0 ? brick : GameKit.mix(brick, brickDark, .35f);
                        if (v == 3) shade = GameKit.mix(shade, 0xFF5A2A18, .3f);
                        float s1 = uy % 2 == 0 ? .62f : .38f;
                        brickPiece(c, l + g, t + g, l + g + (unit - 3 * g) * s1, t + g + h, shade, a0);
                        brickPiece(c, l + 2 * g + (unit - 3 * g) * s1, t + g, l + unit - g, t + g + h, GameKit.mix(shade, brickDark, .2f), a0);
                        brickPiece(c, l + g, t + 2 * g + h, l + g + (unit - 3 * g) * (1 - s1), t + unit - g, GameKit.mix(shade, brickDark, .2f), a0);
                        brickPiece(c, l + 2 * g + (unit - 3 * g) * (1 - s1), t + 2 * g + h, l + unit - g, t + unit - g, shade, a0);
                        if (v == 3) { fill.setShader(null); fill.setColor(GameKit.alpha(0xCCE8EEF8, a0)); c.drawCircle(l + unit / 2f, t + unit / 2f, unit * .09f, fill); }
                    } else {
                        box.set(l + g, t + g, l + unit - g, t + unit - g); float r = unit * .22f;
                        fill.setShader(null); fill.setColor(GameKit.alpha(steel, a0)); c.drawRoundRect(box, r, r, fill);
                        box.set(l + unit * .26f, t + unit * .26f, l + unit * .74f, t + unit * .74f);
                        fill.setColor(GameKit.alpha(GameKit.mix(steel, 0xFFFFFFFF, .45f), a0)); c.drawRoundRect(box, r * .6f, r * .6f, fill);
                        if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x66FFFFFF, a0)); box.set(l + g, t + g, l + unit - g, t + unit - g); c.drawRoundRect(box, r, r, stroke); }
                    }
                }
            } else if (bush[cy][cx]) drawBush(c, cx, cy, off, a0, now);
        }
    }

    void brickPiece(Canvas c, float l, float t, float r, float b, int colour, float a0) {
        if (r - l < 1 || b - t < 1) return;
        box.set(l, t, r, b); float rad = Math.min(r - l, b - t) * .3f;
        fill.setShader(null); fill.setColor(GameKit.alpha(colour, a0)); c.drawRoundRect(box, rad, rad, fill);
        box.set(l + (r - l) * .15f, t + (b - t) * .15f, r - (r - l) * .15f, t + (b - t) * .4f);
        fill.setColor(GameKit.alpha(0x38FFFFFF, a0)); c.drawRoundRect(box, rad * .5f, rad * .5f, fill);
        if (black) { stroke.setStrokeWidth(Math.max(1f, d * .7f)); stroke.setColor(GameKit.alpha(0x44FFFFFF, a0)); box.set(l, t, r, b); c.drawRoundRect(box, rad, rad, stroke); }
    }

    void drawWater(Canvas c, int cx, int cy, float off, float a0, long now) {
        float l = px(cx * 2), t = py(cy * 2) + off, s = cellPx, g = s * .04f;
        box.set(l + g, t + g, l + s - g, t + s - g);
        fill.setShader(null); fill.setColor(GameKit.alpha(waterC, a0 * .85f)); c.drawRoundRect(box, s * .2f, s * .2f, fill);
        stroke.setStrokeWidth(Math.max(1.2f, s * .06f)); stroke.setColor(GameKit.alpha(0x66FFFFFF, a0));
        for (int k = 0; k < 2; k++) {
            float y = t + s * (.35f + .3f * k), ph = now / 420f + cx * .7f + cy * 1.3f + k;
            android.graphics.Path p = GameKit.path();
            for (int i = 0; i <= 8; i++) { float x = l + s * (.18f + .64f * i / 8f), yy = y + (float) Math.sin(ph + i * .9f) * s * .045f; if (i == 0) p.moveTo(x, yy); else p.lineTo(x, yy); }
            c.drawPath(p, stroke);
        }
    }

    void drawBush(Canvas c, int cx, int cy, float off, float a0, long now) {
        float l = px(cx * 2), t = py(cy * 2) + off, s = cellPx;
        float sway = motion ? (float) Math.sin(now / 700.0 + cx + cy * .5) * s * .03f : 0;
        float[][] blobs = {{.3f, .32f, .27f}, {.7f, .3f, .26f}, {.32f, .7f, .28f}, {.7f, .7f, .27f}, {.5f, .5f, .26f}};
        for (int i = 0; i < blobs.length; i++) {
            float[] b = blobs[i];
            fill.setShader(null); fill.setColor(GameKit.alpha(i == 4 ? GameKit.mix(bushC, 0xFFFFFFFF, .15f) : bushC, a0 * .93f));
            c.drawCircle(l + s * b[0] + sway, t + s * b[1], s * b[2], fill);
        }
        fill.setColor(GameKit.alpha(0x30FFFFFF, a0)); c.drawCircle(l + s * .38f + sway, t + s * .36f, s * .1f, fill);
    }

    void drawTank(Canvas c, Tank t, float fade) {
        long now = SystemClock.uptimeMillis();
        float cx = px(t.x + 1), cy = py(t.y + 1), s = unit * 2;
        long age = now - t.bornAt;
        if (age < 900) { drawStar(c, cx, cy, s, age / 900f, t.player ? GameKit.BLUE : tankColour(t), fade); return; }
        int body = tankColour(t);
        if (t.bonus && (now / 160) % 2 == 0) body = GameKit.mix(body, 0xFFFF7FC8, .7f);
        if (now - t.flashAt < 120) body = 0xFFFFFFFF;
        boolean frozen = !t.player && now < freezeUntil;
        if (frozen) body = GameKit.mix(body, 0xFFBFE3FF, .55f);
        int dark2 = GameKit.mix(body, 0xFF000000, .38f), light2 = GameKit.mix(body, 0xFFFFFFFF, .4f);
        if (!t.player) foeShadow(c, t, cx, cy, s, fade, now);
        int save = c.save();
        c.rotate(t.dir * 90, cx, cy);
        float pop = (age < 1200 ? GameKit.spring((age - 900) / 300f) : 1f) * (!t.player && t.type == 4 ? 1.12f : !t.player && t.type == 7 ? (t.form == 1 ? 1.2f : 1.1f) : !t.player && t.type == 13 ? 1.28f : 1f) * (t.player ? 1f : 1 + Math.min(.2f, (stage - 1) * .0025f));
        float ft = (now - t.formAt) / (float) FORM_MS; if (ft >= 0 && ft < 1) { pop *= 1 + .2f * (float) Math.sin(Math.PI * ft); if ((now / 60) % 2 == 0) body = 0xFFFFFFFF; }
        if (!t.player && t.shockUntil > 0 && motion) c.translate((rnd.nextFloat() - .5f) * s * .04f, (rnd.nextFloat() - .5f) * s * .04f);
        c.scale(pop, pop, cx, cy);
        if (!t.player) foeTreads(c, t, cx, cy, s, dark2, fade);
        else for (int side = -1; side <= 1; side += 2) {
            float tl = cx + side * s * .32f - s * .12f, tr = tl + s * .24f;
            box.set(tl, cy - s * .46f, tr, cy + s * .46f);
            fill.setShader(null); fill.setColor(GameKit.alpha(dark2, fade)); c.drawRoundRect(box, s * .09f, s * .09f, fill);
            float roll = (t.moved * unit) % (s * .16f);
            fill.setColor(GameKit.alpha(GameKit.mix(dark2, 0xFFFFFFFF, .22f), fade));
            for (float y = cy - s * .46f + roll; y < cy + s * .44f; y += s * .16f) { box.set(tl + s * .03f, y, tr - s * .03f, y + s * .05f); c.drawRoundRect(box, s * .02f, s * .02f, fill); }
        }
        int pw = t.player ? power() : 0, tier = t.player ? 0 : t.tier;
        float wide = t.player ? .27f + .03f * Math.min(pw, 3) : .27f + .02f * tier;
        if (t.player && now - rankAt < 700) body = GameKit.mix(body, 0xFFFFFFFF, .6f * (1 - (now - rankAt) / 700f));
        if ((t.player && pw >= 4) || tier >= 3) {
            float k = (float) (.5 + .5 * Math.sin(now / 260.0));
            fill.setColor(GameKit.alpha(t.player ? GameKit.LIGHT : 0xFFFF7A3D, fade * (.16f + .12f * k))); c.drawCircle(cx, cy, s * (.62f + .05f * k), fill);
        }
        if ((t.player && pw >= 3) || tier >= 1) {
            int plate = t.player ? 0xFFE8EEF8 : GameKit.mix(body, 0xFFFFFFFF, .45f);
            for (int side = -1; side <= 1; side += 2) { box.set(cx + side * s * (wide + .02f) - s * .05f, cy - s * .2f, cx + side * s * (wide + .02f) + s * .05f, cy + s * .22f); fill.setColor(GameKit.alpha(plate, fade)); c.drawRoundRect(box, s * .04f, s * .04f, fill); }
        }
        if (t.player) drawTankSoul(c, cx, cy, s, wide, now, fade, true);
        if (!t.player) foeHull(c, t, cx, cy, s, wide, body, dark2, light2, fade, now);
        else {
            box.set(cx - s * wide, cy - s * .33f, cx + s * wide, cy + s * .35f);
            fill.setColor(GameKit.alpha(body, fade)); c.drawRoundRect(box, s * .12f, s * .12f, fill);
            if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x77FFFFFF, fade)); c.drawRoundRect(box, s * .12f, s * .12f, stroke); }
        }
        if (!t.player) { if (t.type >= 8) drawNewKind(c, t, cx, cy, s, wide, now, fade); else drawKind(c, t, cx, cy, s, wide, now, fade); }
        if (t.player) {
            int trim = pw >= 4 ? 0xFFFFFFFF : pw == 3 ? 0xFF1E88E5 : GameKit.mix(body, 0xFFFFFFFF, .5f);
            if (pw >= 1) for (int side = -1; side <= 1; side += 2) {
                android.graphics.Path f = GameKit.path(); float bx = cx + side * s * (wide - .04f), by = cy + s * .3f;
                f.moveTo(bx, by - s * .1f); f.lineTo(bx + side * s * .13f, by + s * .14f); f.lineTo(bx - side * s * .02f, by + s * .1f); f.close();
                fill.setColor(GameKit.alpha(trim, fade)); c.drawPath(f, fill);
            }
            if (pw >= 2) for (int side = -1; side <= 1; side += 2) {
                box.set(cx + side * s * (wide - .02f) - s * .06f, cy - s * .16f, cx + side * s * (wide - .02f) + s * .06f, cy + s * .06f);
                fill.setColor(GameKit.alpha(dark2, fade)); c.drawRoundRect(box, s * .04f, s * .04f, fill);
                fill.setColor(GameKit.alpha(MY_SHOT[pw], fade)); c.drawCircle(box.centerX(), box.top + s * .05f, s * .025f, fill);
            }
            if (pw >= 3) {
                android.graphics.Path f = GameKit.path();
                f.moveTo(cx - s * (wide + .04f), cy - s * .3f); f.lineTo(cx + s * (wide + .04f), cy - s * .3f); f.lineTo(cx + s * (wide - .08f), cy - s * .42f); f.lineTo(cx - s * (wide - .08f), cy - s * .42f); f.close();
                fill.setColor(GameKit.alpha(pw >= 4 ? 0xFFFFE9A8 : 0xFF8FA0BD, fade)); c.drawPath(f, fill);
            }
            if (pw >= 4) {
                box.set(cx - s * .04f, cy + s * .12f, cx + s * .04f, cy + s * .33f); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * .8f)); c.drawRoundRect(box, s * .02f, s * .02f, fill);
            }
            float mk = (now - morphAt) / 520f;
            if (mk >= 0 && mk < 1) {
                float o = (float) Math.sin(Math.PI * mk) * s * .22f;
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * (1 - mk)));
                for (int side = -1; side <= 1; side += 2) { box.set(cx + side * (s * wide + o) - s * .04f, cy - s * .3f, cx + side * (s * wide + o) + s * .04f, cy + s * .32f); c.drawRoundRect(box, s * .03f, s * .03f, fill); }
                box.set(cx - s * wide, cy - s * .36f - o, cx + s * wide, cy - s * .3f - o); c.drawRoundRect(box, s * .03f, s * .03f, fill);
            }
        }
        if (tier >= 2) {
            fill.setColor(GameKit.alpha(GameKit.mix(body, 0xFFFFFFFF, .55f), fade));
            for (int side = -1; side <= 1; side += 2) { android.graphics.Path sp2 = GameKit.path(); float bx = cx + side * s * wide, by = cy - s * .3f; sp2.moveTo(bx, by - s * .14f); sp2.lineTo(bx - side * s * .1f, by + s * .02f); sp2.lineTo(bx + side * s * .03f, by + s * .06f); sp2.close(); c.drawPath(sp2, fill); }
        }
        float len = t.player ? .56f + (pw >= 1 ? .08f : 0) : .56f;
        float[] barrels = t.player && pw >= 4 ? new float[]{-.13f, 0, .13f} : (t.player && pw >= 2) || tier >= 4 || (!t.player && t.type == 7 && t.form == 1) ? new float[]{-.08f, .08f} : new float[]{0};
        if (!t.player) foeBarrels(c, t, cx, cy, s, barrels, dark2, fade, now);
        else for (float bo : barrels) {
            box.set(cx + s * bo - s * .05f, cy - s * len, cx + s * bo + s * .05f, cy - s * .05f);
            fill.setColor(GameKit.alpha(dark2, fade)); c.drawRoundRect(box, s * .05f, s * .05f, fill);
            if (t.player && pw >= 1) { box.set(cx + s * bo - s * .035f, cy - s * len, cx + s * bo + s * .035f, cy - s * (len - .06f)); fill.setColor(GameKit.alpha(now < rocketUntil ? 0xFFFFA23D : MY_SHOT[Math.max(0, Math.min(4, pw))], fade)); c.drawRoundRect(box, s * .03f, s * .03f, fill); }
        }
        float tr = s * (.17f + (t.player ? .02f * Math.min(pw, 3) : .01f * tier));
        if (!t.player) foeTurret(c, t, cx, cy, s, tr, body, dark2, fade, now);
        else { fill.setColor(GameKit.alpha(light2, fade)); c.drawCircle(cx, cy + s * .02f, tr, fill); fill.setColor(GameKit.alpha(0x50FFFFFF, fade)); c.drawCircle(cx - s * .05f, cy - s * .04f, s * .06f, fill); }
        if (t.player && pw >= 4) { float g = (float) (.5 + .5 * Math.sin(now / 200.0)); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * (.5f + .4f * g))); c.drawCircle(cx, cy + s * .02f, tr * (.45f + .1f * g), fill); }
        if (t.player && pw == 3) {
            android.graphics.Path st = GameKit.path();
            for (int i = 0; i < 10; i++) { double ang = -Math.PI / 2 + i * Math.PI / 5; float rr = i % 2 == 0 ? tr * .6f : tr * .26f; float qx = cx + (float) Math.cos(ang) * rr, qy = cy + s * .02f + (float) Math.sin(ang) * rr; if (i == 0) st.moveTo(qx, qy); else st.lineTo(qx, qy); }
            st.close(); fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawPath(st, fill);
        }
        if (tier >= 5) { float g = (float) (.5 + .5 * Math.sin(now / 150.0)); fill.setColor(GameKit.alpha(0xFFFF2D55, fade * (.55f + .4f * g))); c.drawCircle(cx, cy + s * .02f, tr * (.5f + .12f * g), fill); }
        if (tier >= 3) {
            fill.setColor(GameKit.alpha(0xFFFFD23F, fade));
            for (int k = -1; k <= 1; k++) c.drawCircle(cx + k * s * .07f, cy + s * .02f - tr * .2f, s * .03f, fill);
        }
        if (t.player) drawTankSoul(c, cx, cy, s, wide, now, fade, false);
        if (!t.player && t.type == 5) { fill.setColor(GameKit.alpha(0xFFE53935, fade * ((now / 120) % 5 == 0 ? .4f : 1f))); box.set(cx - tr * .45f, cy + s * .02f - tr * .45f, cx + tr * .45f, cy + s * .02f + tr * .45f); c.drawRoundRect(box, tr * .15f, tr * .15f, fill); fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawCircle(cx - tr * .15f, cy - tr * .05f, tr * .1f, fill); c.drawCircle(cx + tr * .15f, cy - tr * .05f, tr * .1f, fill); }
        if (!t.player && t.alive && (t.shockUntil > 0 || now - t.zapAt < 260)) tankXray(c, cx, cy, s, wide, now, fade);
        if (t.player) {
            int lv = rank;
            if (lv >= 1) {
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * .85f));
                for (int side = -1; side <= 1; side += 2) { box.set(cx + side * s * .09f - s * .012f, cy + s * .08f, cx + side * s * .09f + s * .012f, cy + s * .33f); c.drawRect(box, fill); }
                stroke.setStrokeWidth(Math.max(1f, s * .02f)); stroke.setColor(GameKit.alpha(0xFFE8EEF8, fade)); c.drawLine(cx + s * .2f, cy + s * .26f, cx + s * .3f, cy + s * .44f, stroke);
                fill.setColor(GameKit.alpha(0xFFFF5A5A, fade)); c.drawCircle(cx + s * .3f, cy + s * .44f, s * .028f, fill);
            }
            if (lv >= 3) {
                float fl = (float) (.6 + .4 * Math.sin(now / 55.0));
                for (int side = -1; side <= 1; side += 2) {
                    fill.setColor(GameKit.alpha(0xFFFFF6C2, fade)); c.drawCircle(cx + side * s * .17f, cy - s * .3f, s * .04f, fill);
                    fill.setColor(GameKit.alpha(0xFFFF9A3D, fade * fl)); c.drawCircle(cx + side * s * .14f, cy + s * .4f, s * .045f * fl, fill);
                }
            }
            if (lv >= 9) {
                android.graphics.Path cr = GameKit.path(); float cw = tr * 1.5f, cb = cy + s * .02f + tr * .5f, ch = tr * 1.1f;
                cr.moveTo(cx - cw / 2, cb); cr.lineTo(cx - cw / 2, cb - ch * .7f); cr.lineTo(cx - cw / 4, cb - ch * .35f); cr.lineTo(cx, cb - ch); cr.lineTo(cx + cw / 4, cb - ch * .35f); cr.lineTo(cx + cw / 2, cb - ch * .7f); cr.lineTo(cx + cw / 2, cb); cr.close();
                fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawPath(cr, fill);
            }
        }
        if (!t.player && t.type == 4) {
            stroke.setStrokeWidth(Math.max(1.5f, s * .045f)); stroke.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawCircle(cx, cy, s * .2f, stroke);
            fill.setColor(GameKit.alpha(0xFFFFD23F, fade));
            for (int side = -1; side <= 1; side += 2) for (int k = -1; k <= 1; k++) c.drawCircle(cx + side * s * .32f, cy + k * s * .28f, s * .03f, fill);
        }
        c.restoreToCount(save);
        if (t.player && t.alive) {
            int lv = rank;
            if (lv >= 7) {
                fill.setShader(null);
                for (int i = 0; i < 6; i++) { double ang = now / 380.0 + i * Math.PI / 3; fill.setColor(GameKit.alpha(MY_SHOT[power()], fade * .9f)); c.drawCircle(cx + (float) Math.cos(ang) * s * .66f, cy + (float) Math.sin(ang) * s * .66f, s * .035f, fill); }
                if (lv >= 9) for (int i = 0; i < 2; i++) { double ang = -now / 520.0 + i * Math.PI; float ox = cx + (float) Math.cos(ang) * s * .82f, oy = cy + (float) Math.sin(ang) * s * .82f; fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * .35f)); c.drawCircle(ox, oy, s * .1f, fill); fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawCircle(ox, oy, s * .06f, fill); }
            }
            if (lv >= 5) {
                float wait = lv >= 9 ? 6000f : 12000f, k = Math.min(1f, (now - overBrokenAt) / wait);
                ward(c, cx, cy, s * .74f, wardStyle(), k, now, fade, Math.max(1.5f, s * .035f));
            }
        }
        if (!t.player && t.alive) healthBar(c, cx, cy - s * (t.type == 7 && t.form == 1 ? .78f : .66f), s * .72f, t.hp, t.maxHp, fade);
        if (t.player && t.alive && t.maxHp > 1) healthBar(c, cx, cy - s * .7f, s * .72f, t.hp, t.maxHp, fade);
        if (!t.player && t.alive && t.burnUntil > 0) for (int i = -1; i <= 1; i++) {
            float fh = s * (.22f + .1f * (float) Math.sin(now / 60.0 + i * 2)), fx = cx + i * s * .2f, fy = cy - s * .1f;
            android.graphics.Path fl = GameKit.path(); fl.moveTo(fx - s * .1f, fy); fl.quadTo(fx, fy - fh * 1.6f, fx + s * .1f, fy); fl.close(); fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFF7A3D, fade * .9f)); c.drawPath(fl, fill);
            fl = GameKit.path(); fl.moveTo(fx - s * .05f, fy); fl.quadTo(fx, fy - fh * .8f, fx + s * .05f, fy); fl.close(); fill.setColor(GameKit.alpha(0xFFFFE27A, fade)); c.drawPath(fl, fill);
        }
        if (!t.player && t.alive && t.shockUntil > 0) for (int q2 = 0; q2 < 2; q2++) { double ang = rnd.nextDouble() * Math.PI * 2; bolt(c, cx, cy, cx + (float) Math.cos(ang) * s * .7f, cy + (float) Math.sin(ang) * s * .7f, s * .14f, 4, now / 40 + q2 * 31 + (long) (t.x * 10), 0xFF7FE9FF, Math.max(1.2f, s * .03f), fade); }
        if (frozen) {
            box.set(cx - s * .56f, cy - s * .56f, cx + s * .56f, cy + s * .56f);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFBFE9FF, fade * .42f)); c.drawRoundRect(box, s * .16f, s * .16f, fill);
            stroke.setStrokeWidth(1.5f * d); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * .8f)); c.drawRoundRect(box, s * .16f, s * .16f, stroke);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * .7f)); box.set(cx - s * .42f, cy - s * .44f, cx - s * .1f, cy - s * .36f); c.drawRoundRect(box, s * .03f, s * .03f, fill);
        }
        if (!t.player && now < t.stunUntil) {
            fill.setShader(null);
            for (int k = 0; k < 3; k++) { double ang = now / 180.0 + k * Math.PI * 2 / 3; fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawCircle(cx + (float) Math.cos(ang) * s * .42f, cy - s * .5f + (float) Math.sin(ang) * s * .12f, s * .07f, fill); }
        }
        if (t.player && now < t.shieldUntil) {
            float k = (now % 900) / 900f;
            stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(GameKit.LIGHT, fade * (.5f + .3f * (float) Math.sin(k * Math.PI * 2))));
            c.drawCircle(cx, cy, s * .62f, stroke);
            stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade * .5f));
            c.drawArc(GameKit.rf(cx - s * .62f, cy - s * .62f, cx + s * .62f, cy + s * .62f), k * 360, 70, false, stroke);
        }
    }

    void drawKind(Canvas c, Tank t, float cx, float cy, float s, float wide, long now, float fade) {
        fill.setShader(null);
        if (t.type == 5) {
            android.graphics.Path st = GameKit.path(); float sy = cy + s * .2f, rr = s * .09f;
            for (int i = 0; i < 10; i++) { double ang = -Math.PI / 2 + i * Math.PI / 5; float r0 = i % 2 == 0 ? rr : rr * .42f, qx = cx + (float) Math.cos(ang) * r0, qy = sy + (float) Math.sin(ang) * r0; if (i == 0) st.moveTo(qx, qy); else st.lineTo(qx, qy); }
            st.close(); fill.setColor(GameKit.alpha(0xFF1846B8, fade)); c.drawPath(st, fill);
            if (motion) { fill.setColor(GameKit.alpha(0xCCE53935, fade)); for (int k = 0; k < 3; k++) { float gy = cy - s * .3f + ((now / 70 + k * 7) % 12) * s * .055f; c.drawRect(cx - s * wide, gy, cx - s * wide + s * wide * 2 * (.3f + .5f * ((k * 37 + now / 90) % 10) / 10f), gy + s * .025f, fill); } }
            fill.setColor(GameKit.alpha((now / 300) % 2 == 0 ? 0xFFFF2D55 : 0xFF6A0A1E, fade)); c.drawCircle(cx - s * wide * .7f, cy + s * .28f, s * .035f, fill);
        } else if (t.type == 6) {
            box.set(cx - s * .2f, cy + s * .08f, cx + s * .2f, cy + s * .32f); fill.setColor(GameKit.alpha(0xFF1A3A33, fade)); c.drawRoundRect(box, s * .04f, s * .04f, fill);
            fill.setColor(GameKit.alpha(0xFF5A6378, fade)); c.drawCircle(cx - s * .09f, cy + s * .2f, s * .06f, fill); c.drawCircle(cx + s * .09f, cy + s * .2f, s * .06f, fill);
            fill.setColor(GameKit.alpha(0xFFE53935, fade)); c.drawCircle(cx - s * .09f, cy + s * .2f, s * .02f, fill); c.drawCircle(cx + s * .09f, cy + s * .2f, s * .02f, fill);
        } else if (t.type == 7) {
            if (t.form == 0) { fill.setColor(GameKit.alpha(0xFF8A93A6, fade)); for (int side = -1; side <= 1; side += 2) { box.set(cx + side * s * wide - s * .07f, cy - s * .28f, cx + side * s * wide + s * .07f, cy + s * .3f); c.drawRoundRect(box, s * .04f, s * .04f, fill); } }
            else {
                fill.setColor(GameKit.alpha(0xFF1A0508, fade));
                for (int side = -1; side <= 1; side += 2) for (int k = -1; k <= 1; k++) { android.graphics.Path sp = GameKit.path(); float bx = cx + side * s * (wide + .01f), by = cy + k * s * .22f; sp.moveTo(bx, by - s * .07f); sp.lineTo(bx + side * s * .15f, by); sp.lineTo(bx, by + s * .07f); sp.close(); c.drawPath(sp, fill); }
                float q = (float) (.5 + .5 * Math.sin(now / 110.0)); stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(0xFFFF7A3D, fade * (.6f + .4f * q)));
                android.graphics.Path cr = GameKit.path(); cr.moveTo(cx - s * wide * .6f, cy - s * .2f); cr.lineTo(cx - s * wide * .3f, cy - s * .05f); cr.lineTo(cx - s * wide * .5f, cy + s * .15f); cr.moveTo(cx + s * wide * .6f, cy + s * .2f); cr.lineTo(cx + s * wide * .35f, cy + s * .02f); cr.lineTo(cx + s * wide * .55f, cy - s * .18f); c.drawPath(cr, stroke);
                for (int side = -1; side <= 1; side += 2) { float ex = cx + side * s * .17f, ey = cy - s * .24f; fill.setColor(GameKit.alpha(0xFFFF9A3D, fade * (.35f + .25f * q))); c.drawCircle(ex, ey, s * .07f, fill); android.graphics.Path e = GameKit.path(); e.moveTo(ex + side * s * .07f, ey - s * .04f); e.lineTo(ex - side * s * .06f, ey + s * .02f); e.quadTo(ex, ey + s * .05f, ex + side * s * .07f, ey - s * .04f); e.close(); fill.setColor(GameKit.alpha(0xFFFFB347, fade)); c.drawPath(e, fill); }
            }
        }
    }

    void tankXray(Canvas c, float cx, float cy, float s, float wide, long now, float fade) {
        boolean on = !motion || (now / 45) % 2 == 0;
        int glow = on ? 0xE62FC8FF : 0xE6FFFFFF, bone = on ? 0xFFFFFFFF : 0xFF0B2A44;
        box.set(cx - s * (wide + .14f), cy - s * .46f, cx + s * (wide + .14f), cy + s * .46f); fill.setShader(null); fill.setColor(GameKit.alpha(glow, fade)); c.drawRoundRect(box, s * .12f, s * .12f, fill);
        stroke.setStrokeWidth(Math.max(1f, s * .025f)); stroke.setColor(GameKit.alpha(bone, fade));
        for (int side = -1; side <= 1; side += 2) for (int k = 0; k < 4; k++) c.drawCircle(cx + side * s * .32f, cy - s * .33f + k * s * .22f, s * .07f, stroke);
        box.set(cx - s * .12f, cy + s * .14f, cx + s * .12f, cy + s * .32f); c.drawRect(box, stroke); c.drawLine(cx - s * .12f, cy + s * .23f, cx + s * .12f, cy + s * .23f, stroke);
        c.drawLine(cx, cy, cx, cy - s * .5f, stroke);
        fill.setColor(GameKit.alpha(bone, fade)); c.drawCircle(cx, cy - s * .02f, s * .1f, fill); c.drawRect(cx - s * .06f, cy + s * .04f, cx + s * .06f, cy + s * .1f, fill);
        fill.setColor(GameKit.alpha(on ? 0xFF0B2A44 : 0xFF2FC8FF, fade)); c.drawCircle(cx - s * .04f, cy - s * .03f, s * .025f, fill); c.drawCircle(cx + s * .04f, cy - s * .03f, s * .025f, fill);
    }

    void drawGlows(Canvas c, long now) {
        for (int i = glows.size() - 1; i >= 0; i--) {
            float[] g = glows.get(i); float t = (now - g[2]) / 650f; if (t >= 1) { glows.remove(i); continue; } if (t < 0) continue;
            float e = GameKit.ease(t), a0 = 1 - GameKit.easeIn(t), cx = px(g[0]), cy = py(g[1]), s = unit * 2;
            fill.setShader(null);
            fill.setColor(GameKit.alpha(0xFFBFF4FF, a0 * .35f)); c.drawCircle(cx, cy, s * (.5f + 1.1f * e), fill);
            fill.setColor(GameKit.alpha(0xFFFFF6D0, a0 * .7f)); c.drawCircle(cx, cy, s * (.45f + .55f * e), fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, a0)); c.drawCircle(cx, cy, s * (.4f + .2f * e) * (1 - t * .6f), fill);
            int sv = c.save(); c.rotate(t * 90, cx, cy); stroke.setStrokeWidth(Math.max(1.5f, s * .05f)); stroke.setColor(GameKit.alpha(0xFFFFFFFF, a0));
            for (int k = 0; k < 8; k++) { double ang = k * Math.PI / 4; float r0 = s * (.5f + .4f * e), r1 = s * (.8f + 1.1f * e) * (k % 2 == 0 ? 1 : .7f); c.drawLine(cx + (float) Math.cos(ang) * r0, cy + (float) Math.sin(ang) * r0, cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1, stroke); }
            c.restoreToCount(sv);
        }
    }

    void drawStar(Canvas c, float cx, float cy, float s, float p, int colour, float fade) {
        float spin = p * 540, r = s * .45f * (1 - GameKit.ease(p) * .6f), sz = s * .22f * (.6f + .4f * (float) Math.sin(Math.PI * p * 3));
        for (int k = 0; k < 4; k++) {
            double ang = Math.toRadians(spin + k * 90);
            float x = cx + (float) Math.cos(ang) * r, y = cy + (float) Math.sin(ang) * r;
            block(c, x - sz / 2, y - sz / 2, x + sz / 2, y + sz / 2, k % 2 == 0 ? colour : GameKit.mix(colour, 0xFFFFFFFF, .5f), fade * GameKit.clamp(p * 4));
        }
    }

    void drawBase(Canvas c, float fade, float[] fly) {
        RectF r = baseRect; r.set(px(12), py(24), px(14), py(26));
        float rad = unit * .4f;
        if (!baseDown) {
            box.set(r.left + unit * .1f, r.top + unit * .1f, r.right - unit * .1f, r.bottom - unit * .1f);
            fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0x22FFFFFF : 0x14000000, fade)); c.drawRoundRect(box, rad, rad, fill);
            if (fly != null) return;
            RectF[] sq = baseSq; GameKit.squaresOf(r, sq);
            long now = SystemClock.uptimeMillis();
            float hurt = GameKit.clamp(1 - (now - baseHurtAt) / 450f);
            for (int k = 0; k < 4; k++) {
                float bob = motion ? (float) Math.sin(now / 380.0 + k * 1.3) * unit * .04f : 0;
                float jx = hurt > 0 ? (rnd.nextFloat() - .5f) * unit * .25f * hurt : 0;
                int colour = GameKit.mix(squareColour(k), 0xFFE53935, hurt * .7f);
                block(c, sq[k].left + jx, sq[k].top + bob, sq[k].right + jx, sq[k].bottom + bob, colour, fade);
            }
            if (baseHp < baseMax) {
                stroke.setStrokeWidth(Math.max(1f, unit * .07f)); stroke.setColor(GameKit.alpha(night ? 0xCC000000 : 0xAA14161C, fade));
                float[][] cracks = {{.2f, .3f, .45f, .5f}, {.7f, .25f, .55f, .55f}, {.3f, .75f, .5f, .55f}, {.75f, .7f, .6f, .5f}};
                int n = Math.min(4, (int) Math.ceil(4f * (baseMax - baseHp) / Math.max(1, baseMax)));
                for (int i = 0; i < n; i++) c.drawLine(r.left + r.width() * cracks[i][0], r.top + r.height() * cracks[i][1], r.left + r.width() * cracks[i][2], r.top + r.height() * cracks[i][3], stroke);
            }
            if (rank >= 3) {
                fill.setColor(GameKit.alpha(GameKit.mix(steel, 0xFFFFFFFF, .25f), fade));
                for (int k = 0; k < 4; k++) { float qx = k % 2 == 0 ? r.left + unit * .08f : r.right - unit * .38f, qy = k < 2 ? r.top + unit * .08f : r.bottom - unit * .38f; box.set(qx, qy, qx + unit * .3f, qy + unit * .3f); c.drawRoundRect(box, unit * .08f, unit * .08f, fill); }
                if (rank >= 6) { float q = motion ? (float) (.5 + .5 * Math.sin(now / 500.0)) : .5f; stroke.setStrokeWidth(Math.max(1.2f, unit * .06f)); stroke.setColor(GameKit.alpha(0xFF7FE9FF, fade * (.35f + .25f * q))); c.drawCircle(r.centerX(), r.centerY(), unit * 1.25f, stroke); }
            }
            {
                float k = GameKit.clamp(baseHp / (float) Math.max(1, baseMax)), bw = unit * 2.3f, bh = Math.max(3 * d, unit * .26f), bx = r.centerX() - bw / 2, by = r.top - unit * .42f;
                float hit = GameKit.clamp(1 - (now - baseHurtAt) / 600f);
                box.set(bx - 1.5f * d, by - 1.5f * d, bx + bw + 1.5f * d, by + bh + 1.5f * d); fill.setColor(GameKit.alpha(night ? 0xCC000000 : 0xB314161C, fade)); c.drawRoundRect(box, bh, bh, fill);
                int bar = k > .5f ? 0xFF2BB673 : k > .25f ? 0xFFF2C230 : 0xFFE53935;
                if (hit > 0) bar = GameKit.mix(bar, 0xFFFFFFFF, hit * .6f);
                box.set(bx, by, bx + bw * k, by + bh); fill.setColor(GameKit.alpha(bar, fade)); if (k > 0) c.drawRoundRect(box, bh / 2, bh / 2, fill);
                fill.setColor(GameKit.alpha(0x55FFFFFF, fade)); box.set(bx + 1 * d, by + .8f * d, Math.max(bx + 1 * d, bx + bw * k - 1 * d), by + bh * .45f); c.drawRoundRect(box, bh / 4, bh / 4, fill);
            }
        } else {
            fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0x33FFFFFF : 0x22000000, fade));
            box.set(r.left + unit * .3f, r.top + unit * .6f, r.right - unit * .3f, r.bottom - unit * .2f); c.drawRoundRect(box, rad, rad, fill);
        }
    }

    void drawDrone(Canvas c, long now) {
        if (now >= droneUntil || me == null || !me.alive) return;
        if (droneUntil - now < 2000 && (now / 140) % 2 == 0) return;
        float x = px(me.x + 1 + (float) Math.cos(droneAngle) * 1.6f), y = py(me.y + 1 + (float) Math.sin(droneAngle) * 1.6f), r = unit * .38f;
        fill.setShader(null); fill.setColor(0x33000000); c.drawOval(GameKit.rf(x - r, y + r * .9f, x + r, y + r * 1.3f), fill);
        if (soul == THUNDER) {
            float pulse = motion ? .5f + .5f * (float) Math.sin(now / 120.0) : .5f; int th = thunder();
            fill.setColor(GameKit.alpha(0xFF7FE9FF, .18f + .02f * th + .12f * pulse)); c.drawCircle(x, y, r * (1.3f + .06f * th), fill);
            fill.setColor(0xFF1E3A8A); c.drawCircle(x, y, r * .8f, fill);
            fill.setColor(0xFF7FE9FF); c.drawCircle(x, y, r * .5f, fill);
            fill.setColor(0xFFFFFFFF); c.drawCircle(x - r * .12f, y - r * .12f, r * .2f, fill);
            stroke.setStrokeWidth(Math.max(1.2f, r * .12f)); stroke.setColor(0xCCBFF4FF);
            int sv = c.save(); c.rotate(motion ? now / 6f % 360 : 0, x, y); c.drawOval(GameKit.rf(x - r * 1.15f, y - r * .4f, x + r * 1.15f, y + r * .4f), stroke); c.restoreToCount(sv);
            if (th >= 5) { sv = c.save(); c.rotate(motion ? -now / 8f % 360 + 60 : 60, x, y); c.drawOval(GameKit.rf(x - r * 1.3f, y - r * .45f, x + r * 1.3f, y + r * .45f), stroke); c.restoreToCount(sv); }
            if (motion) for (int k = 0, nb = 1 + th / 3; k < nb; k++) { double ang = now / 160.0 + k * Math.PI * 2 / nb; bolt(c, x, y, x + (float) Math.cos(ang) * r * 1.4f, y + (float) Math.sin(ang) * r * 1.4f, r * .3f, 3, now / 50 + k, 0xFF7FE9FF, Math.max(1f, r * .08f), .9f); }
            return;
        }
        if (soul == INFERNO) {
            block(c, x - r, y - r * .2f, x + r, y + r, 0xFF3A0A10, 1f);
            fill.setColor(0xFFFF5A1F); c.drawRect(x - r * .8f, y - r * .25f, x + r * .8f, y - r * .05f, fill);
            final int il = inferno();
            for (int k = 0; k < 3; k++) {
                float ph = motion ? ((now / (260f + k * 40)) + k * .33f) % 1f : .4f, fx = x + (k - 1) * r * .5f, fy = y - r * .2f, q = r * (.42f - .18f * ph) * (1 + .05f * il);
                android.graphics.Path f = GameKit.path(); f.moveTo(fx - q, fy); f.quadTo(fx, fy - q * (2.6f - ph), fx + q, fy); f.close();
                fill.setColor(k == 1 ? 0xFFFFD86B : 0xFFFF7A3D); c.drawPath(f, fill);
            }
            fill.setColor(0x44FF7A3D); c.drawCircle(x, y - r * .3f, r * 1.2f, fill);
            if (motion && rnd.nextFloat() < .1f + .03f * il) { GameKit.Bit b = spark(x, y - r * .6f, rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFD86B, 40, 2.4f); b.round = true; b.gravity = -.12f; b.decay = 2.4f; }
            stroke.setStrokeWidth(1.5f * d); stroke.setColor(0xCCFFFFFF);
            float sp2 = now / 40f;
            for (int k = 0; k < 2; k++) { double ang = sp2 + k * Math.PI / 2; c.drawLine(x - (float) Math.cos(ang) * r * 1.3f, y + r * 1.15f - (float) Math.sin(ang) * r * .3f, x + (float) Math.cos(ang) * r * 1.3f, y + r * 1.15f + (float) Math.sin(ang) * r * .3f, stroke); }
            return;
        }
        block(c, x - r, y - r, x + r, y + r, GameKit.LIGHT, 1f);
        fill.setColor(GameKit.BLUE); c.drawCircle(x, y, r * .35f, fill);
        stroke.setStrokeWidth(1.5f * d); stroke.setColor(0xCCFFFFFF);
        float sp = now / 40f;
        for (int k = 0; k < 2; k++) { double ang = sp + k * Math.PI / 2; c.drawLine(x - (float) Math.cos(ang) * r * 1.3f, y - r * 1.1f - (float) Math.sin(ang) * r * .3f, x + (float) Math.cos(ang) * r * 1.3f, y - r * 1.1f + (float) Math.sin(ang) * r * .3f, stroke); }
    }

    void drawShot(Canvas c, Shot s, float fade) {
        float x = px(s.x), y = py(s.y), r = unit * .17f;
        if (s.laser) {
            float tx = -DX[s.dir] * unit * 2.2f, ty = -DY[s.dir] * unit * 2.2f;
            int th = mine(s) ? thunder() : 0;
            if (th > 0) {
                long seed = SystemClock.uptimeMillis() / 40 + System.identityHashCode(s);
                fill.setShader(null); fill.setColor(GameKit.alpha(0xFF7FE9FF, fade * (.1f + .02f * th))); c.drawCircle(x, y, unit * (.6f + .06f * th), fill);
                stroke.setStrokeWidth(unit * (.5f + .05f * th)); stroke.setColor(GameKit.alpha(0xFF4FE3FF, fade * (.2f + .02f * th))); c.drawLine(x, y, x + tx * (1 + .06f * th), y + ty * (1 + .06f * th), stroke);
                for (int k = 0; k < 1 + th / 3; k++) bolt(c, x, y, x + tx * (1.1f + .05f * th), y + ty * (1.1f + .05f * th), unit * (.25f + .03f * th), 4, seed + k * 7, 0xFF7FE9FF, Math.max(1f, unit * .07f), fade);
            }
            stroke.setStrokeWidth(unit * .5f); stroke.setColor(GameKit.alpha(0xFF4FE3FF, fade * .3f)); c.drawLine(x, y, x + tx, y + ty, stroke);
            stroke.setStrokeWidth(unit * .2f); stroke.setColor(GameKit.alpha(0xFF9FF3FF, fade)); c.drawLine(x, y, x + tx, y + ty, stroke);
            stroke.setStrokeWidth(unit * .08f); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawLine(x, y, x + tx * .8f, y + ty * .8f, stroke);
            return;
        }
        if (s.mini) r *= .7f;
        int col = s.mini ? 0xFF9EC1FF : s.rocket ? 0xFFFF7A3D : s.from.player ? (s.lvl == 0 && !night ? 0xFFFFB020 : MY_SHOT[Math.max(0, Math.min(4, s.lvl))]) : 0xFFFF6A5C;
        if (s.from.player && !s.mini && !s.rocket && s.soul != NONE) col = s.soul == THUNDER ? 0xFF7FE9FF : 0xFFFF5A1F;
        boolean wayRocket = s.rocket && !s.mini && mine(s) && s.soul == INFERNO;
        if (wayRocket) { hellMissile(c, s, x, y, r, fade); return; }
        if (s.mini && s.soul == INFERNO) col = 0xFFFF7A3D;
        if (s.charged) {
            float R = unit * (.5f + .03f * soulLv);
            fill.setShader(null); fill.setColor(GameKit.alpha(col, fade * .3f)); c.drawCircle(x, y, R * 2f, fill);
            if (s.soul == THUNDER) bolt(c, x, y, x - DX[s.dir] * unit * 2f, y - DY[s.dir] * unit * 2f, unit * .4f, 4, SystemClock.uptimeMillis() / 45 + s.id, 0xFF7FE9FF, Math.max(1.2f, unit * .1f), fade);
            else { stroke.setStrokeWidth(R * 1.4f); stroke.setColor(GameKit.alpha(0xFFFF7A3D, fade * .5f)); c.drawLine(x, y, x - DX[s.dir] * unit * 1.6f, y - DY[s.dir] * unit * 1.6f, stroke); if (motion && rnd.nextFloat() < .6f) { GameKit.Bit b = spark(x - DX[s.dir] * R, y - DY[s.dir] * R, rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFC94A, 40, 3f); b.round = true; b.gravity = -.2f; b.decay = 2.5f; } }
            fill.setColor(GameKit.alpha(s.soul == THUNDER ? 0xFF1E3A8A : 0xFF2A0508, fade)); c.drawCircle(x, y, R, fill);
            stroke.setStrokeWidth(Math.max(1f, R * .14f)); stroke.setColor(GameKit.alpha(col, fade)); c.drawCircle(x, y, R, stroke);
            tankSign(c, x, y, R * .7f, s.soul, soulLv, SystemClock.uptimeMillis(), fade, true);
            return;
        }
        if (s.from.player && !s.rocket && !s.mini) {
            r *= 1 + .14f * s.lvl;
            if (s.lvl >= 3 && motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(x - DX[s.dir] * unit * .3f, y - DY[s.dir] * unit * .3f, col, 30, 3f); b.round = true; b.gravity = 0; b.decay = 3.2f; }
            if (s.lvl >= 4) { fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * .18f)); c.drawCircle(x, y, r * 3.4f, fill); }
        }
        if (s.mini && s.soul == INFERNO) {
            r *= s.mini ? 1.1f + .04f * inferno() : 1.45f;
            float L = unit * (s.mini ? .9f : 1.5f), fx = x - DX[s.dir] * L, fy = y - DY[s.dir] * L;
            stroke.setStrokeWidth(r * 1.9f); stroke.setColor(GameKit.alpha(0xFFE5532B, fade * .45f)); c.drawLine(x, y, fx, fy, stroke);
            stroke.setStrokeWidth(r * 1.1f); stroke.setColor(GameKit.alpha(0xFFFF9A3D, fade * .7f)); c.drawLine(x, y, x - DX[s.dir] * L * .65f, y - DY[s.dir] * L * .65f, stroke);
            if (motion && rnd.nextFloat() < .6f) { GameKit.Bit b = spark(fx, fy, rnd.nextBoolean() ? 0xFFFF7A3D : 0xFFFFD86B, 40, 2.8f); b.round = true; b.gravity = -.08f; b.decay = 2.8f; }
            if (motion && !s.mini && rnd.nextFloat() < .35f) { GameKit.Bit b = spark(fx, fy, night ? 0x66FFFFFF : 0x55301216, 25, 4.5f); b.round = true; b.gravity = -.05f; b.decay = 1.8f; }
        } else if (s.rocket) { r *= 1.25f; if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(x - DX[s.dir] * unit * .4f, y - DY[s.dir] * unit * .4f, night ? 0x88FFFFFF : 0x66000000, 30, 3.5f); b.round = true; b.gravity = -.05f; b.decay = 2.2f; } }
        fill.setShader(null); fill.setColor(GameKit.alpha(col, fade * .25f)); c.drawCircle(x, y, r * 2.4f, fill);
        float tx = -DX[s.dir] * unit * .7f, ty = -DY[s.dir] * unit * .7f;
        stroke.setStrokeWidth(r * 1.2f); stroke.setColor(GameKit.alpha(col, fade * .35f)); c.drawLine(x, y, x + tx, y + ty, stroke);
        fill.setColor(GameKit.alpha(col, fade)); c.drawCircle(x, y, r, fill);
        fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x, y, r * .45f, fill);
    }

    void drawPrize(Canvas c, Prize p, long now, float fade) {
        float age = now - p.at; if (age > 12000 && (now / 150) % 2 == 0) return;
        float cx = px(p.x + 1), cy = py(p.y + 1) + (motion ? (float) Math.sin(now / 260.0) * unit * .12f : 0), s = unit * 1.7f;
        float in = GameKit.spring(age / 500f);
        int tier = tierOf(p.kind);
        prizeGlow(c, cx, cy, s * in, tier, now, fade);
        box.set(cx - s / 2 * in, cy - s / 2 * in, cx + s / 2 * in, cy + s / 2 * in);
        fill.setShader(null); fill.setColor(GameKit.alpha(tier == 2 ? 0xFFFFF6D8 : tier == 1 ? 0xFFF4EAFF : 0xFFFFFFFF, fade)); c.drawRoundRect(box, s * .28f, s * .28f, fill);
        stroke.setStrokeWidth((tier > 0 ? 2.6f : 2f) * d); stroke.setColor(GameKit.alpha(tier > 0 ? TIER_COLOUR[tier] : GameKit.mix(GameKit.LIGHT, 0xFFFF7FC8, (float) (.5 + .5 * Math.sin(now / 200.0))), fade)); c.drawRoundRect(box, s * .28f, s * .28f, stroke);
        prizeIcon(c, p.kind, cx, cy, s * .32f * in, fade);
    }

    void prizeIcon(Canvas c, int kind, float x, float y, float r, float fade) {
        fill.setShader(null);
        if (kind == NUKE) {
            fill.setColor(GameKit.alpha(0xFF3A4254, fade));
            android.graphics.Path f = GameKit.path(); f.moveTo(x - r * .75f, y - r); f.lineTo(x + r * .75f, y - r); f.lineTo(x + r * .3f, y - r * .35f); f.lineTo(x - r * .3f, y - r * .35f); f.close(); c.drawPath(f, fill);
            fill.setColor(GameKit.alpha(0xFF566078, fade)); c.drawOval(GameKit.rf(x - r * .58f, y - r * .6f, x + r * .58f, y + r), fill);
            fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawCircle(x, y + r * .22f, r * .36f, fill);
            fill.setColor(GameKit.alpha(0xFF14161C, fade)); c.drawCircle(x, y + r * .22f, r * .12f, fill);
            return;
        }
        switch (kind) {
            case STAR: {
                android.graphics.Path p = GameKit.path();
                for (int i = 0; i < 10; i++) { double ang = -Math.PI / 2 + i * Math.PI / 5; float rr = i % 2 == 0 ? r : r * .45f; float px = x + (float) Math.cos(ang) * rr, py = y + (float) Math.sin(ang) * rr; if (i == 0) p.moveTo(px, py); else p.lineTo(px, py); }
                p.close(); fill.setColor(GameKit.alpha(YELLOW, fade)); c.drawPath(p, fill); break;
            }
            case SHIELD: { stroke.setStrokeWidth(r * .3f); stroke.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawCircle(x, y, r * .8f, stroke); fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(x, y, r * .4f, fill); break; }
            case FREEZE: { stroke.setStrokeWidth(r * .22f); stroke.setColor(GameKit.alpha(0xFF4FB3F5, fade)); for (int i = 0; i < 3; i++) { double ang = i * Math.PI / 3; c.drawLine(x - (float) Math.cos(ang) * r, y - (float) Math.sin(ang) * r, x + (float) Math.cos(ang) * r, y + (float) Math.sin(ang) * r, stroke); } break; }
            case BLAST: { fill.setColor(GameKit.alpha(ORANGE, fade)); c.drawCircle(x, y, r * .8f, fill); fill.setColor(GameKit.alpha(YELLOW, fade)); c.drawCircle(x, y, r * .45f, fill); break; }
            case FORT: { box.set(x - r * .8f, y - r * .5f, x + r * .8f, y + r * .8f); fill.setColor(GameKit.alpha(steel, fade)); c.drawRoundRect(box, r * .2f, r * .2f, fill); fill.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawRect(x - r * .25f, y - r * .2f, x + r * .25f, y + r * .3f, fill); break; }
            case ROCKET: { box.set(x - r * .3f, y - r * .9f, x + r * .3f, y + r * .5f); fill.setColor(GameKit.alpha(RED, fade)); c.drawRoundRect(box, r * .3f, r * .3f, fill); fill.setColor(GameKit.alpha(YELLOW, fade)); c.drawCircle(x, y + r * .75f, r * .25f, fill); break; }
            case LASER: { stroke.setStrokeWidth(r * .3f); stroke.setColor(GameKit.alpha(0xFF4FE3FF, fade)); c.drawLine(x - r * .8f, y + r * .5f, x + r * .8f, y - r * .5f, stroke); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawCircle(x + r * .8f, y - r * .5f, r * .2f, fill); break; }
            case DRONE: { fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(x, y + r * .2f, r * .5f, fill); fill.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawCircle(x, y + r * .2f, r * .22f, fill); stroke.setStrokeWidth(r * .15f); stroke.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawLine(x - r * .9f, y - r * .5f, x + r * .9f, y - r * .5f, stroke); break; }
            case WAVE: { stroke.setStrokeWidth(r * .18f); stroke.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawCircle(x, y, r * .35f, stroke); stroke.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(x, y, r * .75f, stroke); break; }
            case DOUBLE: { text.setTypeface(GameKit.BOLD); text.setTextSize(r * 1.5f); text.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawText("×2", x, y + r * .5f, text); break; }
            default: { box.set(x - r * .7f, y - r * .55f, x + r * .7f, y + r * .65f); fill.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawRoundRect(box, r * .25f, r * .25f, fill); fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(x, y + r * .05f, r * .3f, fill); }
        }
    }

    void chip(Canvas c, float x, float y, int kind, float left, float fade) {
        float r = 12 * d;
        fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0x26FFFFFF : 0x14000000, fade)); c.drawCircle(x, y, r, fill);
        stroke.setStrokeWidth(2 * d); stroke.setColor(GameKit.alpha(night ? GameKit.LIGHT : GameKit.BLUE, fade));
        c.drawArc(GameKit.rf(x - r, y - r, x + r, y + r), -90, 360 * GameKit.clamp(left), false, stroke);
        prizeIcon(c, kind, x, y, r * .55f, fade);
    }

    void drawRings(Canvas c, long now) {
        for (int i = rings.size() - 1; i >= 0; i--) {
            float[] r = rings.get(i); float t = (now - r[2]) / 520f;
            if (t >= 1) { rings.remove(i); continue; }
            float k = GameKit.ease(t);
            fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFF4C2, (1 - k) * .7f)); c.drawCircle(r[0], r[1], unit * r[3] * (.6f + .8f * k) * (1 - k * .5f), fill);
            stroke.setStrokeWidth(3 * d * (1 - k)); stroke.setColor(GameKit.alpha(0xFFFFB347, 1 - k)); c.drawCircle(r[0], r[1], unit * r[3] * 1.8f * k, stroke);
        }
    }

    void drawHud(Canvas c, float fade) {
        if (land) { drawHudSide(c, fade); drawChips(c, fade); return; }
        float y = topInset + barH + 40 * d;
        drawScore(c, y, fade);
        text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(13));
        text.setColor(GameKit.alpha(ink, fade * .8f));
        text.setTextAlign(android.graphics.Paint.Align.LEFT);
        c.drawText(I18n.t("Stage {0}").replace("{0}", String.valueOf(stage)), board.left + 4 * d, y - 14 * d, text);
        float s = 12 * d;
        for (int i = 0; i < Math.min(lives, 6); i++) {
            float x = board.left + 4 * d + i * (s + 4 * d);
            box.set(x, y - 6 * d, x + s, y - 6 * d + s); fill.setShader(null); fill.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawRoundRect(box, s * .3f, s * .3f, fill);
            fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(x + s / 2, y - 6 * d + s / 2, s * .22f, fill);
        }
        text.setTextAlign(android.graphics.Paint.Align.CENTER);
        float q = 6 * d, g = 2.5f * d;
        for (int i = 0; i < reserve; i++) {
            int col = i % 10, row = i / 10;
            float x = board.right - 4 * d - (10 - col) * (q + g), yy = y - 20 * d + row * (q + g);
            box.set(x, yy, x + q, yy + q); fill.setColor(GameKit.alpha(night ? 0x88FFFFFF : 0x66000000, fade)); c.drawRoundRect(box, q * .3f, q * .3f, fill);
        }
        drawChips(c, fade);
        drawMeters(c, board.left + 4 * d, topInset + barH + 57 * d, board.right - 4 * d, fade);
    }

    final float[] chipXY = new float[2];

    void drawChips(Canvas c, float fade) {
        long now = SystemClock.uptimeMillis();
        float chipY = board.bottom + (tray.isEmpty() ? 26 : 70) * d, chipX = board.centerX();
        int n = (now < rocketUntil ? 1 : 0) + (now < doubleUntil ? 1 : 0) + (now < freezeUntil ? 1 : 0) + (now < laserUntil ? 1 : 0) + (now < droneUntil ? 1 : 0), i = 0;
        if (now < laserUntil) { chipAt(i++, n, chipX, chipY, chipXY); chip(c, chipXY[0], chipXY[1], LASER, (laserUntil - now) / 9000f, fade); }
        if (now < droneUntil) { chipAt(i++, n, chipX, chipY, chipXY); chip(c, chipXY[0], chipXY[1], DRONE, (droneUntil - now) / 15000f, fade); }
        if (now < rocketUntil) { chipAt(i++, n, chipX, chipY, chipXY); chip(c, chipXY[0], chipXY[1], ROCKET, (rocketUntil - now) / 10000f, fade); }
        if (now < doubleUntil) { chipAt(i++, n, chipX, chipY, chipXY); chip(c, chipXY[0], chipXY[1], DOUBLE, (doubleUntil - now) / 12000f, fade); }
        if (now < freezeUntil) { chipAt(i, n, chipX, chipY, chipXY); chip(c, chipXY[0], chipXY[1], FREEZE, (freezeUntil - now) / 8000f, fade); }
    }

    void drawHudSide(Canvas c, float fade) {
        float lx = sideL.centerX(), y = sideL.top + 34 * d;
        drawScore(c, lx, y, fade);
        text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(13)); text.setTextAlign(android.graphics.Paint.Align.CENTER);
        text.setColor(GameKit.alpha(ink, fade * .8f));
        c.drawText(I18n.t("Stage {0}").replace("{0}", String.valueOf(stage)), lx, y + 24 * d, text);
        float s = 12 * d; int n = Math.min(lives, 6);
        for (int i = 0; i < n; i++) {
            float x = lx - (n * (s + 4 * d) - 4 * d) / 2f + i * (s + 4 * d), yy = y + 34 * d;
            box.set(x, yy, x + s, yy + s); fill.setShader(null); fill.setColor(GameKit.alpha(GameKit.BLUE, fade)); c.drawRoundRect(box, s * .3f, s * .3f, fill);
            fill.setColor(GameKit.alpha(GameKit.LIGHT, fade)); c.drawCircle(x + s / 2, yy + s / 2, s * .22f, fill);
        }
        { float mw = Math.min(sideL.width() - 16 * d, 150 * d); rankMeter(c, lx - mw / 2, y + 66 * d, lx + mw / 2, fade); soulMeter(c, lx - mw / 2, y + 90 * d, lx + mw / 2, fade); }
        float q = 6 * d, g = 2.5f * d, rx = sideR.centerX(), ry = sideR.top + 18 * d;
        for (int i = 0; i < reserve; i++) {
            int col = i % 10, row = i / 10;
            float x = rx - (10 * (q + g) - g) / 2f + col * (q + g), yy = ry + row * (q + g);
            box.set(x, yy, x + q, yy + q); fill.setColor(GameKit.alpha(night ? 0x88FFFFFF : 0x66000000, fade)); c.drawRoundRect(box, q * .3f, q * .3f, fill);
        }
        text.setTextAlign(android.graphics.Paint.Align.CENTER);
    }

    @Override void drawPlay(Canvas c, float dt, long now) {
        if (travelAt > 0 && me != null) { drawTravel(c, now); return; }
        RectF plate = plateRect();
        drawPlate(c, plate, 1f);
        int save = c.save(); c.clipRect(plate);
        int wd = GameKit.world(stage);
        if (wd > 0) { fill.setShader(null); fill.setColor(ground(GameKit.WORLDS[wd][0])); c.drawRoundRect(plate, 18 * d, 18 * d, fill); }
        drawField(c, 1f, null, false);
        drawBase(c, 1f, null);
        drawFires(c, now);
        drawBurns(c, now);
        drawWakes(c, now);
        drawMines(c, now);
        drawBaseWay(c, now);
        for (Prize p : prizes) drawPrize(c, p, now, 1f);
        drawCores(c, now);
        drawTrails(c, now);
        for (Tank t : tanks) if (t.alive) drawTank(c, t, 1f);
        drawAims(c, now);
        drawGlows(c, now);
        drawCrawlers(c, now);
        drawCharges(c, now);
        drawSoulAround(c, now);
        float ak = armourK(now);
        if (ak > 0) { if (me != null && me.alive) drawArmour(c, px(me.x + 1), py(me.y + 1), unit * 2, ak, now); if (!baseDown) drawArmour(c, px(13), py(25), unit * 2, ak, now); }
        if (heat > .03f && me != null && me.alive) {
            stroke.setStrokeWidth(2.5f * d); stroke.setColor(GameKit.mix(0xFFFFB347, 0xFFE53935, heat));
            c.drawArc(GameKit.rf(px(me.x + 1) - unit * 1.35f, py(me.y + 1) - unit * 1.35f, px(me.x + 1) + unit * 1.35f, py(me.y + 1) + unit * 1.35f), -90, 360 * heat, false, stroke);
        }
        drawDrone(c, now);
        drawArcs(c, now);
        for (Shot s : shots) if (!s.dead) drawShot(c, s, 1f);
        drawField(c, 1f, null, true);
        drawSiren(c, now);
        drawMortars(c, now);
        drawBoss(c, now);
        drawRings(c, now);
        drawWave(c, now);
        drawStrikes(c, now);
        drawNuke(c, now);
        ambient(c, plate, wd, now, 1f);
        drawFrost(c, board, freezeAt, freezeUntil, now, 12 * d);
        drawEmblems(c, now);
        drawSkies(c, now);
        c.restoreToCount(save);
        drawDoor(c, doorSide, doorLane, doorK(now), now, 0, 0, true);
        if (enterSide >= 0 && now - arriveAt < 1000) drawDoor(c, enterSide, enterLane, 1 - GameKit.ease((now - arriveAt - 150) / 800f), now, 0, 0, false);
        drawHits(c, now);
        drawHud(c, 1f);
        drawBossBar(c, 1f);
        if (coresAt > 0 && coreTakenAt < 0 && state == PLAY) {
            float bt = now - coresAt;
            if (bt < 1800) { float k = GameKit.spring(bt / 450f), f = bt > 1500 ? 1 - GameKit.ease((bt - 1500) / 300f) : 1f; text.setTypeface(GameKit.BOLD); text.setTextSize(sp(26) * Math.max(.01f, k)); String w = I18n.t("Choose a core"); float tw = text.measureText(w) + 40 * d; box.set(board.centerX() - tw / 2, board.top + board.height() * .3f - 28 * d * k, board.centerX() + tw / 2, board.top + board.height() * .3f + 18 * d * k); fill.setShader(null); fill.setColor(GameKit.alpha(0xF21846B8, f)); c.drawRoundRect(box, 18 * d, 18 * d, fill); text.setColor(GameKit.alpha(0xFFFFFFFF, f)); c.drawText(w, board.centerX(), board.top + board.height() * .3f + 5 * d, text); }
        }
        if (clearAt > 0 && now - clearAt < 1600) { float bt = now - clearAt; banner(c, I18n.t("Stage clear!"), board.centerY(), 0xF22BB673, GameKit.spring(bt / 450f), bt > 1300 ? 1 - GameKit.ease((bt - 1300) / 300f) : 1f); }
        if (now - arriveAt < 1700) { float bt = now - arriveAt; banner(c, I18n.t("Stage {0}").replace("{0}", String.valueOf(stage)), board.top + board.height() * .3f, night ? 0xEE1846B8 : 0xF21846B8, GameKit.spring(bt / 450f), bt > 1400 ? 1 - GameKit.ease((bt - 1400) / 300f) : 1f); }
        drawLevelUp(c, px(lastX + 1), py(lastY) - unit * .2f, now);
        drawAsk(c, now);
    }

    @Override boolean drawBuild(Canvas c, float t, float dt) {
        RectF plate = plateRect();
        float pin = GameKit.ease(t / 320f);
        int save0 = c.save(); float sc = .94f + .06f * GameKit.spring(t / 500f); c.scale(sc, sc, plate.centerX(), plate.centerY());
        drawPlate(c, plate, pin);
        float[] drop = dropBuf;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float delay = 150 + y * 45 + Math.abs(x - 6) * 18, p = motion ? GameKit.clamp((t - delay) / 420f) : 1f;
            drop[y * N + x] = p <= 0 ? -1e6f : -(1 - GameKit.spring(p)) * 36 * d;
        }
        int save = c.save(); c.clipRect(plate);
        drawField(c, 1f, drop, false);
        RectF baseR = baseRect2; baseR.set(px(12), py(24), px(14), py(26)); RectF[] to = buildSq; GameKit.squaresOf(baseR, to);
        float fp = motion && fromTitle && cardAt == null ? GameKit.clamp((t - 100) / 800f) : 1f;
        drawBase(c, pin, fp < 1 ? new float[0] : null);
        c.restoreToCount(save);
        if (fp < 1) for (int k = 0; k < 4; k++) {
            float p = GameKit.clamp((fp * 800 - k * 40) / 760f), m = GameKit.smooth(p);
            RectF s0 = bigAt[k], s1 = to[k];
            float cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m, cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m - (float) Math.sin(Math.PI * p) * 50 * d;
            float size = s0.width() + (s1.width() - s0.width()) * m;
            float grow = fromTitle ? 1f : GameKit.ease(t / 160f);
            int sv = c.save(); c.rotate((k % 2 == 0 ? 1 : -1) * 360 * GameKit.ease(p), cx, cy);
            block(c, cx - size * grow / 2, cy - size * grow / 2, cx + size * grow / 2, cy + size * grow / 2, squareColour(k), 1f);
            c.restoreToCount(sv);
        }
        if (fp >= 1 && !landed && fromTitle) { landed = true; PixelStackSound.play(PixelStackSound.THUD, .7f, 1f); GameKit.thump(a, .8f); burst(baseR.centerX(), baseR.centerY(), GameKit.LIGHT, 14, 200, 4); }
        c.restoreToCount(save0);
        drawHud(c, GameKit.ease((t - 500) / 400f));
        float bk = t < 1000 ? 0 : t < 1700 ? GameKit.spring((t - 1000) / 450f) : 1 - GameKit.ease((t - 1700) / 250f);
        if (bk > 0) {
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(34) * Math.max(.01f, bk));
            String w = I18n.t("Stage {0}").replace("{0}", String.valueOf(stage));
            float tw = text.measureText(w) + 40 * d;
            box.set(board.centerX() - tw / 2, board.centerY() - 34 * d * bk, board.centerX() + tw / 2, board.centerY() + 22 * d * bk);
            fill.setColor(GameKit.alpha(night ? 0xEE1846B8 : 0xF21846B8, Math.min(1f, bk))); c.drawRoundRect(box, 20 * d, 20 * d, fill);
            text.setColor(GameKit.alpha(0xFFFFFFFF, Math.min(1f, bk))); c.drawText(w, board.centerX(), board.centerY() + 6 * d, text);
        }
        if (t > 1000 && !bannerSound) { bannerSound = true; PixelStackSound.play(PixelStackSound.PERFECT, .45f, .9f); }
        if (t >= 1950) {
            landed = false; bannerSound = false; fromTitle = false;
            if (me == null) spawnPlayer(keepLevel);
            spawnTimer = 400;
            return true;
        }
        return false;
    }
    boolean landed, bannerSound; int keepLevel;

    @Override boolean drawClear(Canvas c, float t, float dt) {
        long now = SystemClock.uptimeMillis();
        if (t < 1900) {
            stepShots(dt);
            drawPlay(c, dt, now);
        } else {
            RectF plate = plateRect();
            float out = GameKit.easeIn((t - 1900) / 500f);
            drawPlate(c, plate, 1 - out);
            float[] drop = dropBuf;
            for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) drop[y * N + x] = -GameKit.clamp(((t - 1900) - (N - y) * 25) / 300f) * 40 * d;
            int save = c.save(); c.clipRect(plate);
            drawField(c, 1, drop, false); drawBase(c, 1 - out, null);
            c.restoreToCount(save);
            drawHud(c, 1);
        }
        if (t > 500 && t < 1900) {
            float k = GameKit.spring((t - 500) / 500f), f = t > 1600 ? 1 - GameKit.ease((t - 1600) / 300f) : 1f;
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(32) * Math.max(.01f, k));
            String w = I18n.t("Stage clear!");
            float tw = text.measureText(w) + 44 * d;
            box.set(board.centerX() - tw / 2, board.centerY() - 34 * d * k, board.centerX() + tw / 2, board.centerY() + 22 * d * k);
            fill.setColor(GameKit.alpha(0xF22BB673, f)); c.drawRoundRect(box, 20 * d, 20 * d, fill);
            text.setColor(GameKit.alpha(0xFFFFFFFF, f)); c.drawText(w, board.centerX(), board.centerY() + 6 * d, text);
            if (!confetti) {
                confetti = true; score += 1000; pop("+1000", board.centerX(), board.centerY() - 50 * d, night ? 0xFFFFFFFF : 0xFF14161C, 18);
                for (int i = 0; i < 40; i++) { GameKit.Bit b = spark(board.centerX(), board.centerY(), GameKit.RAINBOW[i % GameKit.RAINBOW.length], 420, 6); b.gravity = .8f; b.decay = .7f; }
                GameKit.thump(a, .7f);
            }
        }
        if (t >= 2450) {
            confetti = false; stage++;
            keepLevel = me != null && me.alive ? me.level : 0;
            enterSide = -1; loadStage();
            return true;
        }
        return false;
    }
    boolean confetti;

    void stepShots(float dt) { for (Shot s : shots) if (!s.dead) flyShot(s, dt); for (int i = shots.size() - 1; i >= 0; i--) if (shots.get(i).dead) shots.remove(i); }

    @Override void shatter() {
        sources.clear();
        for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) if (wall[y][x] != 0) {
            float cx = px(x + .5f), cy = py(y + .5f); int col = wall[y][x] == 2 ? steel : brick;
            sources.add(new float[]{cx, cy, col});
            if (rnd.nextFloat() < .35f) { GameKit.Bit b = spark(cx, cy, col, 80, unit / d * .7f); b.gravity = 1.2f; b.decay = .9f; }
        }
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (bush[y][x]) sources.add(new float[]{px(x * 2 + 1), py(y * 2 + 1), bushC});
        if (sources.isEmpty()) sources.add(new float[]{board.centerX(), board.centerY(), GameKit.BLUE});
        java.util.Collections.shuffle(sources, rnd);
        tanks.clear(); shots.clear(); prizes.clear();
    }

    @Override void drawActionIcon(Canvas c, float x, float y, float r, int colour) {
        stroke.setStrokeWidth(r * .1f); stroke.setColor(colour); c.drawCircle(x, y, r * .38f, stroke);
        fill.setShader(null); fill.setColor(colour); c.drawCircle(x, y, r * .13f, fill);
        for (int k = 0; k < 4; k++) { float ax = (float) Math.cos(k * Math.PI / 2), ay = (float) Math.sin(k * Math.PI / 2); c.drawLine(x + ax * r * .48f, y + ay * r * .48f, x + ax * r * .62f, y + ay * r * .62f, stroke); }
    }
}
