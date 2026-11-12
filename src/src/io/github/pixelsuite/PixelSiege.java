package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
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
    }
    static final class Shot { float x, y, speed; int dir; Tank from; boolean steel, dead, rocket, laser, mini; float trail, sinceTurn; Tank last; int dmg, lvl; }
    static final class Prize { float x, y; int kind; long at; }

    final ArrayList<Tank> tanks = new ArrayList<Tank>();
    final ArrayList<Shot> shots = new ArrayList<Shot>();
    final ArrayList<Prize> prizes = new ArrayList<Prize>();
    Tank me; long meDeadAt = -1;
    int[] queue = new int[20];
    boolean fireHeld; float fireCool; long lastShotAt;
    int kills, rank; long rocketUntil, doubleUntil, laserUntil, droneUntil; long rankAt = -10000;
    int baseHp = 5; static final int BASE_HP = 5; long baseHurtAt = -10000;
    float droneAngle, droneCool; Tank drone;
    static final int[] RANKS = {8, 20, 36, 56, 80, 108, 140, 176, 216};
    int starXp; long overBrokenAt = -100000;
    int xp() { return kills + starXp; }
    boolean overshield(long now) { return rank >= 5 && now - overBrokenAt > (rank >= 9 ? 6000 : 12000); }
    int power() { return Math.min(4, rank / 2); }
    void addScore(int n) { score += SystemClock.uptimeMillis() < doubleUntil ? n * 2 : n; }

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
            if (heat >= 1f) { heat = 0; explode(me, true); meDeadAt = now; }
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
        float size = Math.min(w - 20 * d, (h - topInset - bottomInset) * .62f);
        cellPx = size / N; unit = cellPx / 2f; size = cellPx * N;
        float top = topInset + 64 * d;
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
        {".............", ".S.S.S.S.S.S.", ".............", "##.##.#.##.##", "..*..*.*..*..", ".***.***.***.", "..*..*.*..*..", "##.##.#.##.##", ".............", ".#~#.#.#.#~#.", ".#~#.....#~#.", ".#.#.....#.#.", "............."}
    };

    void loadStage() {
        for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) { wall[y][x] = 0; fortress[y][x] = false; }
        wallsDirty = true;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { water[y][x] = false; bush[y][x] = false; }
        String[] m = MAPS[(stage - 1) % MAPS.length];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            char ch = m[y].charAt(x);
            byte v = ch == '#' ? (byte) 1 : ch == 'S' ? (byte) 2 : 0;
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
        tanks.clear(); shots.clear(); prizes.clear(); bits.clear(); pops.clear();
        fires.clear(); nukeAt = -1; nukeHit = false; armourFrom = armourUntil = -1; heat = 0; strikes.clear(); jetAt = -1; waveAt = -1;
        stageTanks = bossStage() ? 8 : 20;
        reserve = stageTanks; killed = 0; nextSpawn = 0; spawnTimer = 0; me = null; meDeadAt = -1;
        makeBoss();
        freezeUntil = 0; fortressUntil = 0; baseDown = false; baseHitAt = -1; baseHp = BASE_HP;
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
                for (int k = 0; k < 4; k++) { byte v = wall[ny * 2 + k / 2][nx * 2 + k % 2]; if (v == 2) steel = true; else if (v != 0) bricks++; }
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
        fromTitle = true; keepLevel = 0; kills = 0; rank = 0; starXp = 0; overBrokenAt = -100000; rocketUntil = 0; doubleUntil = 0; laserUntil = 0; droneUntil = 0; stage = 1; score = 0; lives = 5; newBest = false; cells = null; sources.clear();
        loadStage();
    }

    static final class Boss { int kind; float x, y = -4, dirX = 1; int hp, maxHp; boolean alive = true; long bornAt, hitAt = -10000, deadAt = -1, chargeAt = -1, beamAt = -1; float cool, cool2, spin, beamX; Shot lastLaser; }
    static final String[] BOSS_NAMES = {"Iron Crown", "Storm Turret", "Laser Walker"};
    Boss boss;
    final Tank bossGun = new Tank();
    static final float BOSS_Y = 3.6f, BOSS_W = 2.6f, BOSS_H = 2.2f;

    boolean bossStage() { return stage % 5 == 0; }

    void makeBoss() {
        boss = null;
        if (!bossStage()) return;
        Boss b = new Boss(); int round = stage / 5 - 1;
        b.kind = round % 3;
        b.hp = b.maxHp = 150 + round * 80 + (b.kind == 0 ? 30 : 0);
        b.x = 13; b.bornAt = SystemClock.uptimeMillis() + 1600; b.cool = 2.2f; b.cool2 = 4f;
        boss = b;
        bossGun.player = false; bossGun.alive = true; bossGun.type = 3; bossGun.tier = 3;
    }

    boolean bossIn() { return boss != null && boss.alive && SystemClock.uptimeMillis() > boss.bornAt + 1300; }

    void bossShot(float x, float y, int dir, float speed) {
        Shot s = new Shot(); s.from = bossGun; s.dir = dir; s.x = x; s.y = y; s.speed = speed; s.steel = false;
        shots.add(s);
        muzzle(px(x), py(y), boss != null && boss.kind == 1 ? 0xFF8FE3FF : 0xFFFFC2B8);
    }

    void stepBoss(float dt) {
        Boss b = boss; if (b == null) return;
        long now = SystemClock.uptimeMillis();
        if (!b.alive) return;
        if (now < b.bornAt) return;
        float arrive = GameKit.clamp((now - b.bornAt) / 1300f);
        b.y = -4 + (BOSS_Y + 4) * GameKit.spring(arrive);
        b.spin += dt * (b.kind == 1 ? (b.hp < b.maxHp / 2 ? 260 : 150) : 40);
        if (arrive < 1 || now < freezeUntil) return;
        boolean angry = b.hp < b.maxHp / 2;
        boolean firing = b.kind == 2 && (b.chargeAt > 0 || (b.beamAt > 0 && now - b.beamAt < 650));
        if (!firing) {
            float sp = b.kind == 1 ? (angry ? 4.2f : 3f) : b.kind == 2 ? 3.4f : (angry ? 2.6f : 2f);
            if (b.kind == 2 && me != null && me.alive) { float want = me.x + 1; b.dirX = Math.abs(want - b.x) < .3f ? 0 : Math.signum(want - b.x); }
            b.x += b.dirX * sp * dt;
            if (b.x < 4) { b.x = 4; b.dirX = 1; } if (b.x > 22) { b.x = 22; b.dirX = -1; }
            if (b.kind != 2 && b.dirX == 0) b.dirX = 1;
        }
        b.cool -= dt; b.cool2 -= dt;
        switch (b.kind) {
            case 0:
                if (b.cool <= 0) {
                    b.cool = angry ? 1.35f : 1.9f;
                    for (float o : new float[]{-1.7f, 0f, 1.7f}) bossShot(b.x + o, b.y + BOSS_H, 2, 12f);
                    if (angry) { bossShot(b.x - BOSS_W, b.y + .4f, 3, 11f); bossShot(b.x + BOSS_W, b.y + .4f, 1, 11f); }
                    PixelStackSound.play(PixelStackSound.SHOT, .3f, .7f); shake(3);
                }
                break;
            case 1:
                if (b.cool <= 0) {
                    b.cool = angry ? 1.5f : 2.3f;
                    for (int dir = 0; dir < 4; dir++) bossShot(b.x + DX[dir] * BOSS_W * .9f, b.y + DY[dir] * BOSS_H * .9f, dir, 13f);
                    if (angry) postDelayed(() -> { if (boss != null && boss.alive && state == PLAY) for (int dir = 1; dir < 4; dir++) bossShot(boss.x + DX[dir] * BOSS_W * .9f, boss.y + DY[dir] * BOSS_H * .9f, dir, 13f); }, 220);
                    PixelStackSound.play(PixelStackSound.SHOT, .3f, 1.25f);
                }
                break;
            default:
                if (b.chargeAt < 0 && (b.beamAt < 0 || now - b.beamAt > 650) && b.cool <= 0) { b.chargeAt = now; b.beamX = b.x; PixelStackSound.play(PixelStackSound.RISE, .35f, 1.5f); }
                if (b.chargeAt > 0 && now - b.chargeAt > (angry ? 800 : 1100)) { b.chargeAt = -1; b.beamAt = now; b.cool = angry ? 1.6f : 2.4f; beam(b); }
                if (b.cool2 <= 0) { b.cool2 = 2.8f; bossShot(b.x - 1.4f, b.y + BOSS_H, 2, 12f); bossShot(b.x + 1.4f, b.y + BOSS_H, 2, 12f); }
                if (b.beamAt > 0 && now - b.beamAt < 650 && me != null && me.alive && now >= me.shieldUntil && Math.abs(me.x + 1 - b.beamX) < 1.3f && me.y > b.y) { explode(me, true); meDeadAt = now; }
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
        if (s.laser) { if (s == b.lastLaser) return false; b.lastLaser = s; }
        b.hp -= Math.max(1, s.dmg) + (s.rocket && !s.mini ? 1 : 0); b.hitAt = now;
        burst(px(s.x), py(s.y), 0xFFFFFFFF, 5, 110, 3);
        PixelStackSound.play(PixelStackSound.TING, .3f, .7f + .6f * b.hp / (float) b.maxHp); GameKit.tick(a, .35f);
        if (b.hp <= 0) {
            b.alive = false; b.deadAt = now;
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
        switch (b.kind) {
            case 0: drawCrown(c, b, cx, cy, w, h, now, flash, k); break;
            case 1: drawStorm(c, b, cx, cy, w, h, now, flash, k); break;
            default: drawWalker(c, b, cx, cy, w, h, now, flash, k);
        }
        c.restoreToCount(save);
        if (b.kind == 2 && b.alive) {
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
        int hull = flash ? 0xFFFFFFFF : 0xFF3B4558, plateC = flash ? 0xFFFFFFFF : 0xFF5A667D, gold = 0xFFFFD23F;
        for (int i = -1; i <= 1; i += 2) { float jx = cx + i * w * .55f, f = (float) (.6 + .4 * Math.sin(now / 50.0 + i)); fill.setColor(GameKit.alpha(0xFF6FD3FF, k * .6f * f)); c.drawOval(GameKit.rf(jx - w * .14f, cy + h * .55f, jx + w * .14f, cy + h * (.9f + .2f * f)), fill); }
        for (float o : new float[]{-.65f, 0f, .65f}) { box.set(cx + o * w - unit * .18f, cy + h * .2f, cx + o * w + unit * .18f, cy + h * 1.05f); fill.setColor(GameKit.alpha(0xFF262D3A, k)); c.drawRoundRect(box, unit * .1f, unit * .1f, fill); }
        android.graphics.Path p = GameKit.path();
        p.moveTo(cx - w, cy - h * .35f); p.lineTo(cx - w * .7f, cy - h * .8f); p.lineTo(cx + w * .7f, cy - h * .8f); p.lineTo(cx + w, cy - h * .35f); p.lineTo(cx + w * .75f, cy + h * .55f); p.lineTo(cx - w * .75f, cy + h * .55f); p.close();
        fill.setColor(GameKit.alpha(hull, k)); c.drawPath(p, fill);
        stroke.setStyle(android.graphics.Paint.Style.STROKE); stroke.setStrokeWidth(Math.max(1f, unit * .08f)); stroke.setColor(GameKit.alpha(plateC, k)); c.drawPath(p, stroke);
        for (int i = -2; i <= 2; i++) { box.set(cx + i * w * .32f - w * .13f, cy - h * .25f, cx + i * w * .32f + w * .13f, cy + h * .3f); fill.setColor(GameKit.alpha(plateC, k)); c.drawRoundRect(box, unit * .12f, unit * .12f, fill); fill.setColor(GameKit.alpha(0x66FFFFFF, k)); c.drawCircle(box.centerX(), box.top + unit * .15f, unit * .06f, fill); }
        float glow = (float) (.6 + .4 * Math.sin(now / 160.0));
        box.set(cx - w * .45f, cy - h * .62f, cx + w * .45f, cy - h * .38f); fill.setColor(GameKit.alpha(0xFFFF3B3B, k * (.6f + .4f * glow))); c.drawRoundRect(box, unit * .1f, unit * .1f, fill);
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
            box.set(cx - r * .2f, cy - r, cx + r * .2f, cy - r * .72f); fill.setColor(GameKit.alpha(flash ? 0xFFFFFFFF : i % 2 == 0 ? 0xFF2C5BD6 : 0xFF6FA0F5, k)); c.drawRoundRect(box, unit * .1f, unit * .1f, fill);
        }
        c.restoreToCount(save);
        fill.setColor(GameKit.alpha(flash ? 0xFFFFFFFF : 0xFF1A2F6E, k)); c.drawCircle(cx, cy, r * .74f, fill);
        save = c.save(); c.rotate(-b.spin * .5f, cx, cy);
        for (int i = 0; i < 4; i++) { c.rotate(90, cx, cy); box.set(cx - unit * .2f, cy - r * 1.05f, cx + unit * .2f, cy - r * .5f); fill.setColor(GameKit.alpha(0xFF0D1838, k)); c.drawRoundRect(box, unit * .1f, unit * .1f, fill); }
        c.restoreToCount(save);
        float pulse = (float) (.5 + .5 * Math.sin(now / 120.0));
        fill.setColor(GameKit.alpha(0xFF8FE3FF, k * (.35f + .3f * pulse))); c.drawCircle(cx, cy, r * (.5f + .08f * pulse), fill);
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
            stroke.setStrokeWidth(unit * .22f); stroke.setColor(GameKit.alpha(0xFF2A2E38, k)); c.drawLine(cx + side * w * .3f, cy - h * .1f + i * h * .2f, kx, ky, stroke); c.drawLine(kx, ky, fx, fy, stroke);
            fill.setColor(GameKit.alpha(0xFF6C7488, k)); c.drawCircle(kx, ky, unit * .14f, fill);
        }
        fill.setColor(GameKit.alpha(flash ? 0xFFFFFFFF : 0xFF3A3F4D, k)); c.drawOval(GameKit.rf(cx - w * .6f, cy - h * .65f, cx + w * .6f, cy + h * .55f), fill);
        fill.setColor(GameKit.alpha(0x33FFFFFF, k)); c.drawOval(GameKit.rf(cx - w * .4f, cy - h * .55f, cx + w * .1f, cy - h * .2f), fill);
        float charge = b.chargeAt > 0 ? GameKit.clamp((now - b.chargeAt) / 1100f) : 0f;
        float er = h * (.3f + .12f * charge);
        fill.setColor(GameKit.alpha(0xFF14161C, k)); c.drawCircle(cx, cy + h * .05f, er * 1.25f, fill);
        fill.setColor(GameKit.alpha(GameKit.mix(0xFFB01030, 0xFFFF2D55, charge), k)); c.drawCircle(cx, cy + h * .05f, er, fill);
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
        c.drawText(I18n.t(BOSS_NAMES[b.kind]), board.centerX(), by + 9 * d, text);
        box.set(bx, by + 14 * d, bx + bw, by + 20 * d); fill.setColor(GameKit.alpha(0x44FFFFFF, fade)); c.drawRoundRect(box, 3 * d, 3 * d, fill);
        int col = b.kind == 0 ? 0xFFFFD23F : b.kind == 1 ? 0xFF8FE3FF : 0xFFFF2D55;
        box.set(bx, by + 14 * d, bx + bw * Math.max(0, b.hp) / (float) b.maxHp, by + 20 * d); fill.setColor(GameKit.alpha(col, fade)); c.drawRoundRect(box, 3 * d, 3 * d, fill);
    }

    Tank spawnPlayer(int level) {
        Tank t = new Tank(); t.player = true; t.x = 8; t.y = 24; t.dir = 0; t.speed = 5.6f; t.hp = 1; t.level = level; t.type = -1;
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
            t.speed = (t.type == 1 ? 7.2f : t.type == 3 ? 3.4f : t.type == 4 ? 3f : 3.8f) * (1 + .06f * t.tier); t.hp = t.maxHp = t.type == 4 ? 8 + 2 * t.tier : (t.type == 3 ? 4 : 1) + t.tier;
            int n = stageTanks - reserve; t.bonus = n == 3 || n == 10 || n == 17;
            t.bornAt = SystemClock.uptimeMillis(); t.cool = 1f + rnd.nextFloat(); t.turn = 1.5f + rnd.nextFloat() * 2f;
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
        if (x < -.001f || y < -.001f || x + 2 > U + .001f || y + 2 > U + .001f) return false;
        int x0 = (int) Math.floor(x + .02f), x1 = (int) Math.floor(x + 2 - .02f), y0 = (int) Math.floor(y + .02f), y1 = (int) Math.floor(y + 2 - .02f);
        for (int uy = y0; uy <= y1; uy++) for (int ux = x0; ux <= x1; ux++) if (solid(ux, uy)) return false;
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
            if (!free(nx, ny, t)) return false;
            t.x = nx; t.y = ny; t.moved += s; left -= s;
        }
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
            for (float o : offs) {
                Shot s = new Shot(); s.from = t; s.dir = t.dir; s.rocket = rocket && !laser; s.laser = laser;
                s.x = t.x + 1 + DX[t.dir] * 1.0f + (t.dir % 2 == 0 ? o : 0); s.y = t.y + 1 + DY[t.dir] * 1.0f + (t.dir % 2 == 1 ? o : 0);
                s.speed = laser ? 34f : rocket ? 16f : pw >= 1 ? 22f : 16f; s.steel = pw >= 3 || rocket;
                s.dmg = MY_DMG[pw]; s.lvl = pw;
                shots.add(s); t.shots++;
                muzzle(px(s.x), py(s.y), rocket ? 0xFFFFA23D : 0xFFFFF1A8);
            }
            PixelStackSound.play(PixelStackSound.SHOT, .42f, (rocket ? .8f : .95f) + rnd.nextFloat() * .1f); GameKit.tick(a, .3f);
            return;
        }
        if (t.shots >= (t.tier >= 3 ? 2 : 1)) return;
        Shot s = new Shot(); s.from = t; s.dir = t.dir;
        s.x = t.x + 1 + DX[t.dir] * 1.0f; s.y = t.y + 1 + DY[t.dir] * 1.0f;
        s.speed = (t.type == 2 ? 19f : 12f) * (1 + .08f * t.tier);
        s.steel = (t.tier >= 3 && t.type == 2) || t.type == 4;
        shots.add(s); t.shots++;
        PixelStackSound.play(PixelStackSound.SHOT, .16f, .8f);
        muzzle(px(s.x), py(s.y), 0xFFFFC2B8);
    }

    void muzzle(float bx, float by, int colour) { for (int i = 0; i < 4; i++) { GameKit.Bit b = spark(bx, by, colour, 90, 3); b.round = true; b.decay = 4f; } }

    @Override void action(boolean down) { fireHeld = down; if (down && me != null && me.alive && ready(me)) { fire(me); fireCool = .26f; } }

    @Override void stepPlay(float dt) {
        long now = SystemClock.uptimeMillis();
        boolean frozen = now < freezeUntil;
        if (state == DYING) { if (since() > 1600) gameOver(); }
        if (me != null && me.alive && state == PLAY && ready(me)) {
            if (dir >= 0) drive(me, dir, me.speed * dt);
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
        for (Tank t : tanks) {
            if (t.player || !t.alive || !ready(t) || frozen || state != PLAY || now < t.stunUntil) continue;
            t.turn -= dt; t.cool -= dt;
            boolean moved = drive(t, t.dir, t.speed * dt);
            boolean aligned = Math.abs(t.x - Math.round(t.x)) < .12f && Math.abs(t.y - Math.round(t.y)) < .12f;
            if (!moved || (t.turn <= 0 && aligned)) { t.dir = way(t, think(t), !moved); t.turn = 1.2f + rnd.nextFloat() * 2.4f; }
            if (!moved && ++t.bumps > 40) { t.bumps = 0; if (t.cool > .15f) t.cool = .15f; } else if (moved) t.bumps = 0;
            if (t.tier >= 1 && aligned && me != null && me.alive && rnd.nextFloat() < dt * 1.3f * t.tier) {
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
            if (t.cool <= 0) {
                fire(t);
                boolean pair = t.tier >= 3 && !t.again; t.again = pair;
                t.cool = pair ? .2f : (.7f + rnd.nextFloat() * (t.type == 2 || t.type == 4 ? .9f : 1.6f)) * (1 - .1f * t.tier);
            }
        }
        if (state == PLAY) { stepDrone(dt); stepBoss(dt); }
        stepNuke(dt, now);
        if (state == PLAY) { stepWave(now); stepStrikes(now); }
        int pwNow = rank;
        if (pwNow > shownPower && me != null && me.alive) {
            morphAt = now; rankAt = now;
            pop(I18n.t("Level {0}").replace("{0}", String.valueOf(pwNow + 1)), px(me.x + 1), py(me.y - 1), MY_SHOT[power()], 22);
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
                if (dist < 4.6f && dist > .01f) {
                    float v = (5f + (4.6f - dist) * 5f) * dt; if (v > dist) v = dist;
                    p.x += dx / dist * v; p.y += dy / dist * v;
                    if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(px(p.x + 1), py(p.y + 1), TIER_COLOUR[tierOf(p.kind)], 30, 3f); b.round = true; b.gravity = 0; b.decay = 3f; }
                }
            }
            if (me != null && me.alive && Math.abs(me.x - p.x) < 1.3f && Math.abs(me.y - p.y) < 1.3f) { take(p); prizes.remove(i); }
        }
        if (fortressUntil > 0 && now > fortressUntil) { fortressUntil = 0; for (int y = 0; y < U; y++) for (int x = 0; x < U; x++) if (fortress[y][x]) wall[y][x] = 3; wallsDirty = true; }
        for (int i = tanks.size() - 1; i >= 0; i--) if (!tanks.get(i).alive) tanks.remove(i);
        if (state == PLAY && reserve == 0 && killed >= stageTanks && !baseDown && (boss == null || (!boss.alive && now - boss.deadAt > 1500))) { go(CLEAR); PixelStackSound.play(PixelStackSound.SUCCESS, .55f, 1f); }
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
        byte v = wall[uy][ux];
        if (v == 0) return false;
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

    long crumbleAt;
    void crumble() {
        long now = SystemClock.uptimeMillis();
        if (now - crumbleAt < 90) return;
        crumbleAt = now;
        PixelStackSound.play(PixelStackSound.CRUMBLE, .34f, .85f + rnd.nextFloat() * .3f);
    }

    void kill(Shot s) { if (s.dead) return; s.dead = true; s.from.shots = Math.max(0, s.from.shots - 1); }

    void hitTank(Tank t, Shot s) {
        long now = SystemClock.uptimeMillis();
        if (t.player) {
            if (now < t.shieldUntil) { burst(px(s.x), py(s.y), GameKit.LIGHT, 6, 100, 3); PixelStackSound.play(PixelStackSound.TING, .3f, 1.4f); return; }
            if (overshield(now)) { overBrokenAt = now; burst(px(t.x + 1), py(t.y + 1), 0xFF7FE9FF, 20, 220, 4); rings.add(new float[]{px(t.x + 1), py(t.y + 1), now, 1.3f}); PixelStackSound.play(PixelStackSound.TING, .5f, .8f); GameKit.thump(a, .6f); return; }
            explode(t, true); meDeadAt = now; return;
        }
        if (t.bonus) { t.bonus = false; drop(); }
        t.hp -= Math.max(1, s.dmg) + (s.rocket && !s.mini ? 1 : 0);
        t.flashAt = now;
        if (t.hp > 0) { burst(px(s.x), py(s.y), 0xFFFFFFFF, 6, 110, 3); PixelStackSound.play(PixelStackSound.TING, .35f, .9f + .1f * t.hp); GameKit.tick(a, .4f); return; }
        explode(t, false);
        killed++;
        int pts = 100 * (t.type + 1) * (1 + t.tier); addScore(pts);
        pop("+" + (SystemClock.uptimeMillis() < doubleUntil ? pts * 2 : pts), px(t.x + 1), py(t.y), night ? 0xFFFFFFFF : 0xFF14161C, 15);
        if (rnd.nextFloat() < .1f) drop();
        kills++;
        if (rank < RANKS.length && xp() >= RANKS[rank]) {
            rank++;
            PixelStackSound.play(PixelStackSound.PICK, .6f, .9f); postDelayed(() -> PixelStackSound.play(PixelStackSound.PERFECT, .45f, 1.1f), 160);
            GameKit.thump(a, .8f);
        }
    }

    int tankColour(Tank t) {
        if (t.player) return MY_BODY[Math.max(0, Math.min(4, power()))];
        int c = t.type == 0 ? GREEN : t.type == 1 ? YELLOW : t.type == 2 ? ORANGE : t.type == 4 ? 0xFF2A2F3D : RED;
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
        if (--baseHp > 0) {
            baseHurtAt = now;
            burst(px(13), py(25), GameKit.LIGHT, 12, 200, 4);
            rings.add(new float[]{px(13), py(25), now, 1.2f});
            shake(8); GameKit.thump(a, .9f);
            PixelStackSound.play(PixelStackSound.BOOM, .45f, 1.2f);
            pop(baseHp + "/" + BASE_HP, px(13), py(23.4f), 0xFFE53935, 16);
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
        Shot s = new Shot(); s.from = drone; s.rocket = true; s.mini = true; s.speed = 14f; s.steel = false;
        float ex = best.x + 1 - dx0, ey = best.y + 1 - dy0;
        s.dir = Math.abs(ex) > Math.abs(ey) ? (ex > 0 ? 1 : 3) : (ey > 0 ? 2 : 0);
        s.x = Math.max(.2f, Math.min(U - .2f, dx0)); s.y = Math.max(.2f, Math.min(U - .2f, dy0));
        shots.add(s);
        muzzle(px(s.x), py(s.y), 0xFF9EC1FF);
        PixelStackSound.play(PixelStackSound.SHOT, .22f, 1.4f);
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
                baseHp = Math.min(BASE_HP, baseHp + 2);
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
        int save = c.save();
        c.rotate(t.dir * 90, cx, cy);
        float pop = (age < 1200 ? GameKit.spring((age - 900) / 300f) : 1f) * (!t.player && t.type == 4 ? 1.12f : 1f); c.scale(pop, pop, cx, cy);
        for (int side = -1; side <= 1; side += 2) {
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
        box.set(cx - s * wide, cy - s * .33f, cx + s * wide, cy + s * .35f);
        fill.setColor(GameKit.alpha(body, fade)); c.drawRoundRect(box, s * .12f, s * .12f, fill);
        if (black) { stroke.setStrokeWidth(d); stroke.setColor(GameKit.alpha(0x77FFFFFF, fade)); c.drawRoundRect(box, s * .12f, s * .12f, stroke); }
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
        float[] barrels = t.player && pw >= 4 ? new float[]{-.13f, 0, .13f} : (t.player && pw >= 2) || tier >= 4 ? new float[]{-.08f, .08f} : new float[]{0};
        for (float bo : barrels) {
            box.set(cx + s * bo - s * .05f, cy - s * len, cx + s * bo + s * .05f, cy - s * .05f);
            fill.setColor(GameKit.alpha(dark2, fade)); c.drawRoundRect(box, s * .05f, s * .05f, fill);
            if (t.player && pw >= 1) { box.set(cx + s * bo - s * .035f, cy - s * len, cx + s * bo + s * .035f, cy - s * (len - .06f)); fill.setColor(GameKit.alpha(now < rocketUntil ? 0xFFFFA23D : MY_SHOT[Math.max(0, Math.min(4, pw))], fade)); c.drawRoundRect(box, s * .03f, s * .03f, fill); }
        }
        float tr = s * (.17f + (t.player ? .02f * Math.min(pw, 3) : .01f * tier));
        fill.setColor(GameKit.alpha(light2, fade)); c.drawCircle(cx, cy + s * .02f, tr, fill);
        fill.setColor(GameKit.alpha(0x50FFFFFF, fade)); c.drawCircle(cx - s * .05f, cy - s * .04f, s * .06f, fill);
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
                stroke.setStrokeWidth(Math.max(1.5f, s * .035f));
                if (k >= 1f) {
                    float q = (float) (.5 + .5 * Math.sin(now / 300.0));
                    fill.setShader(null); fill.setColor(GameKit.alpha(0xFF7FE9FF, fade * (.08f + .05f * q))); c.drawCircle(cx, cy, s * .74f, fill);
                    stroke.setColor(GameKit.alpha(0xFF7FE9FF, fade * (.6f + .3f * q))); c.drawCircle(cx, cy, s * .74f, stroke);
                } else { stroke.setColor(GameKit.alpha(0xFF7FE9FF, fade * .35f)); c.drawArc(GameKit.rf(cx - s * .74f, cy - s * .74f, cx + s * .74f, cy + s * .74f), -90, 360 * k, false, stroke); }
            }
        }
        if (!t.player && t.alive) healthBar(c, cx, cy - s * .66f, s * .72f, t.hp, t.maxHp, fade);
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
            if (baseHp < BASE_HP) {
                stroke.setStrokeWidth(Math.max(1f, unit * .07f)); stroke.setColor(GameKit.alpha(night ? 0xCC000000 : 0xAA14161C, fade));
                float[][] cracks = {{.2f, .3f, .45f, .5f}, {.7f, .25f, .55f, .55f}, {.3f, .75f, .5f, .55f}, {.75f, .7f, .6f, .5f}};
                for (int i = 0; i < Math.min(4, BASE_HP - baseHp); i++) c.drawLine(r.left + r.width() * cracks[i][0], r.top + r.height() * cracks[i][1], r.left + r.width() * cracks[i][2], r.top + r.height() * cracks[i][3], stroke);
            }
            for (int i = 0; i < BASE_HP; i++) {
                float dx = r.centerX() + (i - (BASE_HP - 1) / 2f) * unit * .3f, dy = r.top + unit * .05f;
                fill.setColor(GameKit.alpha(i < baseHp ? 0xFF2BB673 : (night ? 0x44FFFFFF : 0x33000000), fade)); c.drawCircle(dx, dy, unit * .09f, fill);
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
            stroke.setStrokeWidth(unit * .5f); stroke.setColor(GameKit.alpha(0xFF4FE3FF, fade * .3f)); c.drawLine(x, y, x + tx, y + ty, stroke);
            stroke.setStrokeWidth(unit * .2f); stroke.setColor(GameKit.alpha(0xFF9FF3FF, fade)); c.drawLine(x, y, x + tx, y + ty, stroke);
            stroke.setStrokeWidth(unit * .08f); stroke.setColor(GameKit.alpha(0xFFFFFFFF, fade)); c.drawLine(x, y, x + tx * .8f, y + ty * .8f, stroke);
            return;
        }
        if (s.mini) r *= .7f;
        int col = s.mini ? 0xFF9EC1FF : s.rocket ? 0xFFFF7A3D : s.from.player ? (s.lvl == 0 && !night ? 0xFFFFB020 : MY_SHOT[Math.max(0, Math.min(4, s.lvl))]) : 0xFFFF6A5C;
        if (s.from.player && !s.rocket && !s.mini) {
            r *= 1 + .14f * s.lvl;
            if (s.lvl >= 3 && motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(x - DX[s.dir] * unit * .3f, y - DY[s.dir] * unit * .3f, col, 30, 3f); b.round = true; b.gravity = 0; b.decay = 3.2f; }
            if (s.lvl >= 4) { fill.setShader(null); fill.setColor(GameKit.alpha(0xFFFFFFFF, fade * .18f)); c.drawCircle(x, y, r * 3.4f, fill); }
        }
        if (s.rocket) { r *= 1.25f; if (motion && rnd.nextFloat() < .5f) { GameKit.Bit b = spark(x - DX[s.dir] * unit * .4f, y - DY[s.dir] * unit * .4f, night ? 0x88FFFFFF : 0x66000000, 30, 3.5f); b.round = true; b.gravity = -.05f; b.decay = 2.2f; } }
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
        float y = topInset + 40 * d;
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
        long now = SystemClock.uptimeMillis();
        float chipY = board.bottom + (tray.isEmpty() ? 26 : 70) * d, chipX = getWidth() / 2f;
        int chips = (now < rocketUntil ? 1 : 0) + (now < doubleUntil ? 1 : 0) + (now < freezeUntil ? 1 : 0) + (now < laserUntil ? 1 : 0) + (now < droneUntil ? 1 : 0);
        float cx0 = chipX - (chips - 1) * 15 * d;
        if (now < laserUntil) { chip(c, cx0, chipY, LASER, (laserUntil - now) / 9000f, fade); cx0 += 30 * d; }
        if (now < droneUntil) { chip(c, cx0, chipY, DRONE, (droneUntil - now) / 15000f, fade); cx0 += 30 * d; }
        if (now < rocketUntil) { chip(c, cx0, chipY, ROCKET, (rocketUntil - now) / 10000f, fade); cx0 += 30 * d; }
        if (now < doubleUntil) { chip(c, cx0, chipY, DOUBLE, (doubleUntil - now) / 12000f, fade); cx0 += 30 * d; }
        if (now < freezeUntil) { chip(c, cx0, chipY, FREEZE, (freezeUntil - now) / 8000f, fade); }
        {
            float sx = board.left + 4 * d + Math.min(lives, 6) * 16 * d + 8 * d;
            prizeIcon(c, STAR, sx + 5 * d, y, 5 * d, fade);
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(11)); text.setTextAlign(android.graphics.Paint.Align.LEFT); text.setColor(GameKit.alpha(night ? 0xFFFFFFFF : 0xFF14161C, fade));
            String lv = String.valueOf(rank + 1); c.drawText(lv, sx + 13 * d, y + 4 * d, text);
            float bx = sx + 13 * d + text.measureText(lv) + 5 * d, bw = 30 * d;
            int from = rank == 0 ? 0 : RANKS[rank - 1], to = rank < RANKS.length ? RANKS[rank] : from;
            float k = to > from ? Math.max(0f, Math.min(1f, (xp() - from) / (float) (to - from))) : 1f;
            box.set(bx, y - 2 * d, bx + bw, y + 2 * d); fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0x33FFFFFF : 0x22000000, fade)); c.drawRoundRect(box, 2 * d, 2 * d, fill);
            box.set(bx, y - 2 * d, bx + bw * k, y + 2 * d); fill.setColor(GameKit.alpha(0xFFFFD23F, fade)); c.drawRoundRect(box, 2 * d, 2 * d, fill);
            text.setTextAlign(android.graphics.Paint.Align.CENTER);
        }

    }

    @Override void drawPlay(Canvas c, float dt, long now) {
        RectF plate = plateRect();
        drawPlate(c, plate, 1f);
        int save = c.save(); c.clipRect(plate);
        int wd = GameKit.world(stage);
        if (wd > 0) { fill.setShader(null); fill.setColor(ground(GameKit.WORLDS[wd][0])); c.drawRoundRect(plate, 18 * d, 18 * d, fill); }
        drawField(c, 1f, null, false);
        drawBase(c, 1f, null);
        drawFires(c, now);
        for (Prize p : prizes) drawPrize(c, p, now, 1f);
        for (Tank t : tanks) if (t.alive) drawTank(c, t, 1f);
        float ak = armourK(now);
        if (ak > 0) { if (me != null && me.alive) drawArmour(c, px(me.x + 1), py(me.y + 1), unit * 2, ak, now); if (!baseDown) drawArmour(c, px(13), py(25), unit * 2, ak, now); }
        if (heat > .03f && me != null && me.alive) {
            stroke.setStrokeWidth(2.5f * d); stroke.setColor(GameKit.mix(0xFFFFB347, 0xFFE53935, heat));
            c.drawArc(GameKit.rf(px(me.x + 1) - unit * 1.35f, py(me.y + 1) - unit * 1.35f, px(me.x + 1) + unit * 1.35f, py(me.y + 1) + unit * 1.35f), -90, 360 * heat, false, stroke);
        }
        drawDrone(c, now);
        for (Shot s : shots) if (!s.dead) drawShot(c, s, 1f);
        drawField(c, 1f, null, true);
        drawBoss(c, now);
        drawRings(c, now);
        drawWave(c, now);
        drawStrikes(c, now);
        drawNuke(c, now);
        ambient(c, plate, wd, now, 1f);
        drawFrost(c, board, freezeAt, freezeUntil, now, 12 * d);
        c.restoreToCount(save);
        drawHud(c, 1f);
        drawBossBar(c, 1f);
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
            box.set(getWidth() / 2f - tw / 2, board.centerY() - 34 * d * bk, getWidth() / 2f + tw / 2, board.centerY() + 22 * d * bk);
            fill.setColor(GameKit.alpha(night ? 0xEE1846B8 : 0xF21846B8, Math.min(1f, bk))); c.drawRoundRect(box, 20 * d, 20 * d, fill);
            text.setColor(GameKit.alpha(0xFFFFFFFF, Math.min(1f, bk))); c.drawText(w, getWidth() / 2f, board.centerY() + 6 * d, text);
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
            box.set(getWidth() / 2f - tw / 2, board.centerY() - 34 * d * k, getWidth() / 2f + tw / 2, board.centerY() + 22 * d * k);
            fill.setColor(GameKit.alpha(0xF22BB673, f)); c.drawRoundRect(box, 20 * d, 20 * d, fill);
            text.setColor(GameKit.alpha(0xFFFFFFFF, f)); c.drawText(w, getWidth() / 2f, board.centerY() + 6 * d, text);
            if (!confetti) {
                confetti = true; score += 1000; pop("+1000", getWidth() / 2f, board.centerY() - 50 * d, night ? 0xFFFFFFFF : 0xFF14161C, 18);
                for (int i = 0; i < 40; i++) { GameKit.Bit b = spark(getWidth() / 2f, board.centerY(), GameKit.RAINBOW[i % GameKit.RAINBOW.length], 420, 6); b.gravity = .8f; b.decay = .7f; }
                GameKit.thump(a, .7f);
            }
        }
        if (t >= 2450) {
            confetti = false; stage++;
            keepLevel = me != null && me.alive ? me.level : 0;
            loadStage();
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
