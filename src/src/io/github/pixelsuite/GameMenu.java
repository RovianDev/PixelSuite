package io.github.pixelsuite;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

final class GameMenu extends View {
    static final int IN = 0, IDLE = 1, LAUNCH = 2, AWAY = 3, BACK = 4, OUT = 5;
    static final float IN_MS = 900, OUT_MS = 700, LAUNCH_MS = ArcadeCurtain.MORPH_MS;

    final Activity a; final View logo; final ImageView mark; final View left, right;
    final int ink, page, soft, plate; final boolean dark, black, night; final float d; final boolean motion;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF box = new RectF(), sqBox = new RectF(), homeBox = new RectF();
    final RectF[] iconSq = new RectF[4];
    final int[] bests = new int[3];
    final String[] tags = new String[3]; float tagRoom = -1;
    Dialog dialog;
    int state = IN; long stateAt = SystemClock.uptimeMillis();
    final RectF[] heldSquares = new RectF[4], headAt = new RectF[4], homeSquares = new RectF[4];
    RectF popAt, headRect = new RectF();
    final RectF[] cards = {new RectF(), new RectF(), new RectF()};
    final RectF[] icons = {new RectF(), new RectF(), new RectF()};
    final RectF[] views = {new RectF(), new RectF(), new RectF()};
    int pressed = -1, chosen = -1; long pressAt;
    final RectF[] landFrom = new RectF[4]; long backAt;
    float topInset, bottomInset;
    boolean landed;
    final float[] drops = new float[48];
    final java.util.Random rnd = new java.util.Random();

    static final String[] NAMES = {"Pixel Stack", "Pixel Siege", "Pixel Blast"};
    static final String[] TAGS = {"Stack it high", "Defend the logo", "Blast your way through"};
    static final String[] PREFS = {"pixelstack", "pixelsiege", "pixelblast"};

    GameMenu(Activity a, View logo, ImageView mark, View left, View right, int ink, int page, boolean dark, boolean black, RectF[] held) {
        super(a);
        this.a = a; this.logo = logo; this.mark = mark; this.left = left; this.right = right;
        this.ink = ink | 0xFF000000; this.page = page | 0xFF000000; this.dark = dark; this.black = black; night = dark || black;
        soft = (Math.round(255 * .45f) << 24) | (ink & 0xFFFFFF);
        plate = black ? 0xFF000000 : dark ? GameKit.mix(this.page, 0xFFFFFFFF, .07f) : GameKit.mix(this.page, 0xFFFFFFFF, .78f);
        d = getResources().getDisplayMetrics().density;
        float scale = 1f;
        try { scale = android.provider.Settings.Global.getFloat(a.getContentResolver(), "animator_duration_scale", 1f); } catch (Throwable ignored) { }
        motion = scale > 0f;
        int[] at = new int[2]; logo.getLocationOnScreen(at);
        RectF from = new RectF(at[0], at[1], at[0] + logo.getWidth(), at[1] + logo.getHeight());
        GameKit.squaresOf(from, homeSquares);
        if (held != null) { popAt = held[0]; for (int k = 0; k < 4; k++) heldSquares[k] = held[k + 1]; }
        else for (int k = 0; k < 4; k++) heldSquares[k] = new RectF(homeSquares[k]);
        fill.setStyle(Paint.Style.FILL); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND);
        readBests();
        text.setTextAlign(Paint.Align.CENTER);
    }

    final boolean[] saved = new boolean[3];
    void readBests() { for (int i = 0; i < 3; i++) { try { bests[i] = a.getSharedPreferences(PREFS[i], 0).getInt("best", 0); saved[i] = GameSave.has(a, i); } catch (Throwable ignored) { } } }

    static void warm(final android.content.Context c) {
        final android.content.Context app = c.getApplicationContext();
        new Thread(() -> {
            try { for (String p : PREFS) app.getSharedPreferences(p, 0).getInt("best", 0); Object o = GameKit.BOLD; } catch (Throwable ignored) { }
        }, "PixelArcadeWarm").start();
    }

    static void open(Activity a, View logo, ImageView mark, View left, View right, int ink, int page, boolean dark, boolean black) {
        if (a.isFinishing()) return;
        RectF[] held = PixelStack.heldRects(logo);
        final GameMenu m = new GameMenu(a, logo, mark, left, right, ink, page, dark, black, held);
        android.widget.FrameLayout root = new android.widget.FrameLayout(a);
        root.addView(m, new android.widget.FrameLayout.LayoutParams(-1, -1));
        m.dialog = GameKit.window(a, root, dark || black, m::leave);
        m.dialog.setOnDismissListener(x -> PixelStack.arcadeOpen(null, null));
        m.dialog.show();
        PixelStack.arcadeOpen(m.dialog, a);
        PixelStack.handOff(logo, mark, left, right);
    }

    long since() { return SystemClock.uptimeMillis() - stateAt; }
    void go(int s) { state = s; stateAt = SystemClock.uptimeMillis(); }
    float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        try { android.view.WindowInsets in = getRootWindowInsets(); if (in != null) { topInset = in.getSystemWindowInsetTop(); bottomInset = in.getSystemWindowInsetBottom(); } } catch (Throwable ignored) { }
        if (topInset <= 0) topInset = 32 * d;
        float hs = 78 * d, hy = topInset + Math.max(48 * d, h * .09f);
        headRect.set(w / 2f - hs / 2, hy, w / 2f + hs / 2, hy + hs);
        GameKit.squaresOf(headRect, headAt);
        float bottom = bottomInset > 0 ? bottomInset : 24 * d;
        float cw = Math.min(w - 32 * d, 440 * d), gap = 14 * d, top = headRect.bottom + 96 * d;
        float ch = Math.max(96 * d, Math.min(132 * d, (h - bottom - 20 * d - top - 2 * gap) / 3f));
        for (int i = 0; i < 3; i++) {
            cards[i].set(w / 2f - cw / 2, top + i * (ch + gap), w / 2f + cw / 2, top + i * (ch + gap) + ch);
            float pad = 10 * d, vw = Math.min(cw * .42f, (ch - 2 * pad) * 1.45f);
            if (I18n.rtl()) views[i].set(cards[i].right - pad - vw, cards[i].top + pad, cards[i].right - pad, cards[i].bottom - pad);
            else views[i].set(cards[i].left + pad, cards[i].top + pad, cards[i].left + pad + vw, cards[i].bottom - pad);
            float is = Math.min(views[i].width(), views[i].height()) * .78f;
            icons[i].set(views[i].centerX() - is / 2, views[i].centerY() - is / 2, views[i].centerX() + is / 2, views[i].centerY() + is / 2);
        }
    }

    int iconColour(int game, int k) {
        switch (game) {
            case 0: return (k % 2) == (k / 2) ? ink : soft;
            case 1: return k % 3 == 0 ? GameKit.BLUE : GameKit.LIGHT;
            default: return k % 3 == 0 ? 0xFFF28C28 : 0xFFF2C230;
        }
    }

    void arrangement(int game, RectF r, long now, RectF[] out) {
        float s = r.width(), u = s * .3f, cx = r.centerX(), cy = r.centerY();
        float t = motion ? now / 1000f : 0;
        for (int k = 0; k < 4; k++) if (out[k] == null) out[k] = new RectF();
        switch (game) {
            case 0:
                for (int k = 0; k < 4; k++) {
                    float lvl = 3 - k, sway = (float) Math.sin(t * 2.2 + lvl * .6) * u * .12f * lvl / 3f;
                    float x = cx + (k % 2 == 0 ? -1 : 1) * u * .1f + sway, y = r.bottom - s * .08f - u * (lvl + .5f) * .92f;
                    out[k].set(x - u / 2, y - u / 2 * .9f, x + u / 2, y + u / 2 * .9f);
                }
                break;
            case 1: {
                float roll = (float) Math.sin(t * 1.6) * u * .12f;
                for (int k = 0; k < 3; k++) { float x = cx + (k - 1) * u * 1.04f + roll; out[k + 1].set(x - u / 2, cy + u * .1f, x + u / 2, cy + u * 1.1f); }
                out[0].set(cx - u / 2 + roll, cy - u * .98f, cx + u / 2 + roll, cy + u * .02f);
                break;
            }
            default: {
                float breathe = 1 + .08f * (float) Math.sin(t * 3.4);
                float dist = u * 1.05f * breathe, q = u * .82f;
                float[][] at = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                for (int k = 0; k < 4; k++) { float x = cx + at[k][0] * dist, y = cy + at[k][1] * dist; out[k].set(x - q / 2, y - q / 2, x + q / 2, y + q / 2); }
            }
        }
    }

    void square(Canvas c, RectF r, int colour, float alphaF, float rot) {
        int save = c.save(); c.rotate(rot, r.centerX(), r.centerY());
        float rad = Math.min(r.width(), r.height()) * .26f;
        fill.setShader(null); fill.setColor(GameKit.alpha(colour, alphaF)); c.drawRoundRect(r, rad, rad, fill);
        if (black) { stroke.setStrokeWidth(Math.max(1f, d)); stroke.setColor(GameKit.alpha(0x55FFFFFF, alphaF)); c.drawRoundRect(r, rad, rad, stroke); }
        box.set(r.left + r.width() * .14f, r.top + r.height() * .12f, r.right - r.width() * .14f, r.top + r.height() * .32f);
        fill.setColor(GameKit.alpha(0x40FFFFFF, alphaF)); c.drawRoundRect(box, r.height() * .08f, r.height() * .08f, fill);
        c.restoreToCount(save);
    }

    void drawView(Canvas c, int game, long now, float alphaF) {
        RectF v = views[game]; float rad = 16 * d;
        ArcadePreview.draw(c, game, v, motion ? now : 1500, rad, d, night, black, alphaF, fill, stroke);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(black ? 1.2f * d : d);
        stroke.setColor(GameKit.alpha(ArcadePreview.accent(game), alphaF * (night ? .5f : .35f)));
        c.drawRoundRect(v, rad, rad, stroke);
    }

    void drawIcon(Canvas c, int game, RectF r, long now, float alphaF) {
        RectF[] sq = iconSq; arrangement(game, r, now, sq);
        if (game == chosen && state == BACK) {
            float k = GameKit.spring((now - backAt) / 420f);
            for (int i = 0; i < 4; i++) if (landFrom[i] != null) { RectF f = landFrom[i]; sq[i].set(f.left + (sq[i].left - f.left) * k, f.top + (sq[i].top - f.top) * k, f.right + (sq[i].right - f.right) * k, f.bottom + (sq[i].bottom - f.bottom) * k); }
        }
        if (game == 1) {
            stroke.setStrokeWidth(r.width() * .07f); stroke.setColor(GameKit.alpha(GameKit.mix(GameKit.BLUE, 0xFF000000, .25f), alphaF));
            c.drawLine(sq[0].centerX(), sq[0].centerY(), sq[0].centerX(), sq[0].top - r.width() * .16f, stroke);
        }
        if (game == 2) {
            float cx = r.centerX(), cy = r.centerY(), br = r.width() * .17f;
            fill.setShader(null); fill.setColor(GameKit.alpha(night ? 0xFF3A4366 : 0xFF1B2440, alphaF)); c.drawCircle(cx, cy, br, fill);
            fill.setColor(GameKit.alpha(0x55FFFFFF, alphaF)); c.drawCircle(cx - br * .35f, cy - br * .35f, br * .3f, fill);
            float sp = br * .3f * (1 + .35f * (float) Math.sin(now / 50.0));
            fill.setColor(GameKit.alpha(0xFFFFE066, alphaF)); c.drawCircle(cx + br * .75f, cy - br * .9f, sp, fill);
        }
        for (int k = 0; k < 4; k++) square(c, sq[k], iconColour(game, k), alphaF, 0);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked();
        if (state != IDLE) { pressed = -1; return true; }
        if (act == MotionEvent.ACTION_DOWN) {
            pressed = -1;
            for (int i = 0; i < 3; i++) if (cards[i].contains(e.getX(), e.getY())) { pressed = i; pressAt = SystemClock.uptimeMillis(); GameKit.tick(a, .3f); }
            if (pressed < 0 && e.getY() < headRect.bottom + 40 * d && headRect.contains(e.getX(), e.getY())) {
                PixelStackSound.play(PixelStackSound.BOOP, .45f, 1.1f); hopAt = SystemClock.uptimeMillis();
            }
        } else if (act == MotionEvent.ACTION_MOVE) {
            if (pressed >= 0 && !cards[pressed].contains(e.getX(), e.getY())) pressed = -1;
        } else if (act == MotionEvent.ACTION_UP) {
            if (pressed >= 0 && cards[pressed].contains(e.getX(), e.getY())) launch(pressed);
            pressed = -1;
        } else if (act == MotionEvent.ACTION_CANCEL) pressed = -1;
        invalidate();
        return true;
    }
    long hopAt = -10000;

    void launch(int game) {
        chosen = game;
        go(LAUNCH);
        PixelStackSound.play(PixelStackSound.SOFT, .34f, 1.1f);
        GameKit.thump(a, .5f);
    }

    void startGame() {
        int[] at = new int[2]; getLocationOnScreen(at);
        RectF[] sq = new RectF[4]; arrangement(chosen, icons[chosen], SystemClock.uptimeMillis(), sq);
        for (RectF r : sq) r.offset(at[0], at[1]);
        RectF home = new RectF(icons[chosen]); home.offset(at[0], at[1]);
        RectF window = new RectF(views[chosen]); window.offset(at[0], at[1]);
        Runnable gone = () -> {
            backAt = SystemClock.uptimeMillis(); go(BACK); readBests();
            GameKit.tick(a, .45f); PixelStackSound.play(PixelStackSound.BOOP, .45f, .9f);
            invalidate();
        };
        go(AWAY);
        if (chosen == 0 && PixelStack.paused(a)) PixelStack.resume(window, gone);
        else if (chosen != 0 && GameKit.Game.paused(a, chosen)) GameKit.Game.kept[chosen].resume(window, gone);
        else if (chosen == 0) { if (!PixelStack.resumeSaved(a, home, sq, ink, page, dark, black, gone, window)) PixelStack.playFrom(a, home, sq, ink, page, dark, black, gone, window); }
        else {
            GameKit.Game g = chosen == 1 ? new PixelSiege(a, home, sq, ink, page, dark, black) : new PixelBlast(a, home, sq, ink, page, dark, black);
            g.fromCard(window);
            if (GameSave.load(a, chosen, g)) g.resume(window, gone);
            else g.show(gone);
        }
    }

    void leave() {
        if (state == LAUNCH) {
            float z = GameKit.clamp(since() / LAUNCH_MS);
            backAt = SystemClock.uptimeMillis() - (long) ((1 - z) * ArcadeCurtain.MORPH_MS); go(BACK);
            GameKit.tick(a, .4f); PixelStackSound.play(PixelStackSound.SOFT, .26f, .9f);
            return;
        }
        if (state == OUT || state == AWAY) return;
        go(OUT);
        GameKit.tick(a, .5f);
        PixelStackSound.play(PixelStackSound.SOFT, .3f, 1.12f); postDelayed(() -> PixelStackSound.play(PixelStackSound.SOFT, .26f, .9f), 120);
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        int W = getWidth(), H = getHeight();
        float t = since();
        if (state == LAUNCH && t >= LAUNCH_MS) { startGame(); t = 0; }
        if (state == BACK && now - backAt > ArcadeCurtain.MORPH_MS) go(IDLE);
        float morph = chosen >= 0 && (state == LAUNCH || state == AWAY || state == BACK) ? GameKit.clamp(state == BACK ? 1 - (now - backAt) / ArcadeCurtain.MORPH_MS : state == AWAY ? 1f : t / LAUNCH_MS) : 0f;
        float e = motion ? GameKit.smooth(morph) : (morph >= 1 ? 1f : 0f);
        float shown = state == IN ? GameKit.ease(t / 320f) : state == OUT ? 1 - GameKit.ease((t - 120) / 460f) : 1f;
        fill.setShader(null); fill.setColor(GameKit.alpha(black ? 0xFF000000 : page, shown)); c.drawRect(0, 0, W, H, fill);
        if (state == IN && popAt != null && t < 420) drawPop(c, t);
        if (state == IN && t >= IN_MS) go(IDLE);
        if (state == LAUNCH && t >= LAUNCH_MS) { startGame(); t = 0; }

        int head = -1;
        if (e > 0) { head = c.saveLayerAlpha(0, 0, W, headRect.bottom + 90 * d, Math.round(255 * (1 - GameKit.clamp(e * 1.8f))), Canvas.ALL_SAVE_FLAG); c.translate(0, -70 * d * e); }
        drawHead(c, now, t);
        float tk = state == IN ? GameKit.ease((t - 260) / 380f) : state == OUT ? 1 - GameKit.ease(t / 220f) : state == LAUNCH ? 1 - GameKit.ease(t / 240f) * .0f : 1f;
        text.setTypeface(GameKit.BOLD); text.setTextSize(sp(28)); text.setColor(GameKit.alpha(ink, tk));
        c.drawText(I18n.t("Pixel Arcade"), W / 2f, headRect.bottom + 44 * d + (1 - tk) * 12 * d, text);
        text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(15)); text.setColor(GameKit.alpha(soft, tk * 1.6f));
        c.drawText(I18n.t("Choose a game"), W / 2f, headRect.bottom + 70 * d + (1 - tk) * 12 * d, text);
        if (head >= 0) c.restoreToCount(head);

        for (int i = 0; i < 3; i++) {
            float k, lift = 0, alphaF = 1f, scale = 1f;
            if (state == IN) { float p = motion ? GameKit.clamp((t - 280 - i * 90) / 560f) : 1f; k = GameKit.spring(p); lift = (1 - k) * 80 * d; alphaF = GameKit.clamp(p * 3); }
            else if (state == OUT) { float p = GameKit.clamp((t - (2 - i) * 50) / 320f); lift = GameKit.easeIn(p) * 60 * d; alphaF = 1 - GameKit.ease(p); }
            if (pressed == i) scale = .97f;
            if (e > 0 && i == chosen) continue;
            if (e > 0) { lift = (i < chosen ? -1 : 1) * e * H * .3f; alphaF = 1 - GameKit.clamp(e * 2f); }
            if (alphaF <= 0) continue;
            int save = c.save(); c.translate(0, lift); c.scale(scale, scale, cards[i].centerX(), cards[i].centerY());
            drawCard(c, i, now, alphaF);
            c.restoreToCount(save);
        }

        if (e > 0) drawMorph(c, chosen, e, now, W, H);

        if (state == OUT) {
            drawGoHome(c, t);
            if (!landed && t >= OUT_MS * .78f) { landed = true; PixelStack.landHome(a, logo, mark, left, right); }
            if (t >= OUT_MS) { try { if (dialog != null) dialog.dismiss(); } catch (Throwable ignored) { } return; }
        }
        postInvalidateOnAnimation();
    }

    final RectF morphAt = new RectF(), sceneAt = new RectF();
    boolean restOnly;
    void drawMorph(Canvas c, int i, float e, long now, int W, int H) {
        RectF r = cards[i], v = views[i]; int accent = ArcadePreview.accent(i); boolean rtl = I18n.rtl();
        morphAt.set(r.left + (-2 - r.left) * e, r.top + (-2 - r.top) * e, r.right + (W + 2 - r.right) * e, r.bottom + (H + 2 - r.bottom) * e);
        float rad = 26 * d * (1 - e);
        fill.setShader(null); fill.setColor(GameKit.mix(plate, black ? 0xFF000000 : page, e)); c.drawRoundRect(morphAt, rad, rad, fill);
        if (e < 1) {
            fill.setShader(new android.graphics.LinearGradient(rtl ? morphAt.right : morphAt.left, morphAt.top, rtl ? morphAt.left : morphAt.right, morphAt.bottom,
                    GameKit.alpha(accent, (1 - e) * (night ? .2f : .13f)), GameKit.alpha(accent, 0f), android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(morphAt, rad, rad, fill); fill.setShader(null);
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(black ? 1.5f * d : 1.2f * d);
            stroke.setColor(GameKit.alpha(accent, (1 - e) * (black ? .7f : night ? .4f : .3f))); c.drawRoundRect(morphAt, rad, rad, stroke);
            float rest = 1 - GameKit.clamp(e * 3.2f);
            if (rest > 0) { restOnly = true; drawCard(c, i, now, rest); restOnly = false; }
        }
        ArcadeCurtain.scene(i, W, H, d, topInset, sceneAt);
        morphAt.set(v.left + (sceneAt.left - v.left) * e, v.top + (sceneAt.top - v.top) * e, v.right + (sceneAt.right - v.right) * e, v.bottom + (sceneAt.bottom - v.bottom) * e);
        float vr = (16 + 8 * e) * d;
        ArcadePreview.draw(c, i, morphAt, motion ? now : 1500, vr, d, night, black, 1f, fill, stroke);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth((black ? 1.2f + .3f * e : 1f + .2f * e) * d);
        stroke.setColor(GameKit.alpha(accent, night ? .5f : .35f)); c.drawRoundRect(morphAt, vr, vr, stroke);
        String name = I18n.t(NAMES[i]);
        text.setTypeface(GameKit.BOLD); text.setTextSize(sp(20));
        float w0 = text.measureText(name), tx = rtl ? v.left - 16 * d : v.right + 16 * d;
        float x0 = rtl ? tx - w0 / 2 : tx + w0 / 2, y0 = r.centerY() - 12 * d, y1 = ArcadeCurtain.titleY(i, W, H, d, topInset, sceneAt);
        text.setTextSize(sp(20 + 10 * e)); text.setTextAlign(Paint.Align.CENTER); text.setColor(ink);
        c.drawText(name, x0 + (W / 2f - x0) * e, y0 + (y1 - y0) * e, text);
    }

    void drawCard(Canvas c, int i, long now, float alphaF) {
        RectF r = cards[i]; float rad = 26 * d;
        int accent = ArcadePreview.accent(i);
        boolean rtl = I18n.rtl();
        if (!restOnly) {
        fill.setShader(null); fill.setColor(GameKit.alpha(plate, alphaF)); c.drawRoundRect(r, rad, rad, fill);
        fill.setShader(new android.graphics.LinearGradient(rtl ? r.right : r.left, r.top, rtl ? r.left : r.right, r.bottom,
                GameKit.alpha(accent, alphaF * (night ? .2f : .13f)), GameKit.alpha(accent, 0f), android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, fill); fill.setShader(null);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(black ? 1.5f * d : 1.2f * d);
        stroke.setColor(GameKit.alpha(accent, alphaF * (black ? .7f : night ? .4f : .3f))); c.drawRoundRect(r, rad, rad, stroke);
        drawView(c, i, now, alphaF);
        }
        float tx = rtl ? views[i].left - 16 * d : views[i].right + 16 * d;
        float room = rtl ? tx - (r.left + 50 * d) : (r.right - 50 * d) - tx;
        text.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        text.setTypeface(GameKit.BOLD); text.setTextSize(sp(20)); text.setColor(GameKit.alpha(ink, alphaF));
        if (!restOnly) c.drawText(I18n.t(NAMES[i]), tx, r.centerY() - 12 * d, text);
        text.setTypeface(GameKit.REGULAR); text.setTextSize(sp(13.5f)); text.setColor(GameKit.alpha(soft, alphaF * 1.6f));
        if (tags[i] == null || tagRoom != room) {
            String tag = I18n.t(TAGS[i]);
            if (text.measureText(tag) > room) tag = android.text.TextUtils.ellipsize(tag, new android.text.TextPaint(text), room, android.text.TextUtils.TruncateAt.END).toString();
            tags[i] = tag; if (i == 2) tagRoom = room;
        }
        c.drawText(tags[i], tx, r.centerY() + 10 * d, text);
        int best = bests[i];
        text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(12.5f));
        String bs = I18n.t("Best") + "  " + best;
        float bw = text.measureText(bs) + 18 * d, by = r.centerY() + 22 * d;
        box.set(rtl ? tx - bw : tx, by, rtl ? tx : tx + bw, by + 22 * d);
        fill.setColor(GameKit.alpha(accent, alphaF * (night ? .22f : .14f))); c.drawRoundRect(box, 11 * d, 11 * d, fill);
        text.setColor(GameKit.alpha(night ? GameKit.mix(accent, 0xFFFFFFFF, .45f) : GameKit.mix(accent, 0xFF000000, .3f), alphaF));
        text.setTextAlign(Paint.Align.CENTER); c.drawText(bs, box.centerX(), box.centerY() - (text.descent() + text.ascent()) / 2, text);
        if (saved[i] || (i == 0 ? PixelStack.paused(a) : GameKit.Game.paused(a, i))) {
            String ps = I18n.t("Paused"); float pw = text.measureText(ps) + 30 * d, gap = 6 * d;
            float pl = rtl ? box.left - gap - pw : box.right + gap;
            box.set(pl, by, pl + pw, by + 22 * d);
            fill.setColor(GameKit.alpha(accent, alphaF)); c.drawRoundRect(box, 11 * d, 11 * d, fill);
            fill.setColor(GameKit.alpha(0xFFFFFFFF, alphaF));
            float ix = rtl ? box.right - 12 * d : box.left + 12 * d;
            c.drawRect(ix - 3.5f * d, box.centerY() - 4.5f * d, ix - 1 * d, box.centerY() + 4.5f * d, fill); c.drawRect(ix + 1 * d, box.centerY() - 4.5f * d, ix + 3.5f * d, box.centerY() + 4.5f * d, fill);
            text.setColor(GameKit.alpha(0xFFFFFFFF, alphaF)); c.drawText(ps, box.centerX() + (rtl ? -6 : 6) * d, box.centerY() - (text.descent() + text.ascent()) / 2, text);
        }
        float ax = rtl ? r.left + 28 * d : r.right - 28 * d, ay = r.centerY(), ar = 15 * d;
        float press = pressed == i ? .9f : 1f;
        fill.setColor(GameKit.alpha(accent, alphaF)); c.drawCircle(ax, ay, ar * press, fill);
        android.graphics.Path tri = GameKit.path(); float dir = rtl ? -1 : 1, ts = 5.5f * d * press;
        tri.moveTo(ax - ts * .7f * dir, ay - ts); tri.lineTo(ax + ts * dir, ay); tri.lineTo(ax - ts * .7f * dir, ay + ts); tri.close();
        fill.setColor(GameKit.alpha(0xFFFFFFFF, alphaF)); c.drawPath(tri, fill);
    }

    void drawHead(Canvas c, long now, float t) {
        if (state == OUT) return;
        for (int k = 0; k < 4; k++) {
            RectF s1 = headAt[k];
            float cx = s1.centerX(), cy = s1.centerY(), size = s1.width(), turn = 0, sx = 1, sy = 1;
            int colour = (k % 2) == (k / 2) ? GameKit.BLUE : GameKit.LIGHT;
            if (state == IN) {
                float p = motion ? GameKit.clamp((t - k * 45f) / 620f) : 1f, m = GameKit.spring(p);
                RectF s0 = heldSquares[k];
                cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m; cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m - (float) Math.sin(Math.PI * GameKit.ease(p)) * 40 * d;
                size = s0.width() + (s1.width() - s0.width()) * m; turn = (1 - GameKit.ease(p)) * (k % 2 == 0 ? 220f : -220f);
                colour = GameKit.mix((k % 2) == (k / 2) ? ink : soft, colour, GameKit.ease(p * 1.4f));
                if (p >= 1f && motion) { float[] m4 = dance(k, now - stateAt); cy -= m4[0] * size; turn += m4[1]; sx = m4[2]; sy = m4[3]; }
            } else if (motion) {
                float[] m4 = dance(k, now - hopAt < 1700 ? now - hopAt : 1700 * 3 + (now % 1700));
                cy -= m4[0] * size * .6f; turn += m4[1]; sx = m4[2]; sy = m4[3];
            }
            int save = c.save(); c.rotate(turn, cx, cy); c.scale(sx, sy, cx, cy + size / 2 * (sy < 1 ? 1 : 0));
            sqBox.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2); square(c, sqBox, colour, 1f, 0);
            c.restoreToCount(save);
        }
    }

    float[] dance(int k, long t) {
        final float period = 1700f;
        float u = ((t + k * 425) % (long) period) / period;
        float lift = 0, turn = 0, sx = 1, sy = 1, air = (u - .12f) / .5f;
        boolean flying = air > 0 && air < 1;
        if (u < .12f) { float q = (float) Math.sin(Math.PI * u / .12f); sx = 1 + .18f * q; sy = 1 - .2f * q; }
        else if (u > .62f && u < .74f) { float q = (float) Math.sin(Math.PI * (u - .62f) / .12f); sx = 1 + .16f * q; sy = 1 - .18f * q; }
        if (flying) switch (k) {
            case 0: lift = .45f * (float) Math.sin(Math.PI * air); turn = 360 * GameKit.ease(air); break;
            case 1: lift = .35f * (float) Math.sin(Math.PI * air); sx = (float) Math.cos(2 * Math.PI * air); if (Math.abs(sx) < .06f) sx = .06f; break;
            case 2: { float q = (float) Math.sin(Math.PI * air); lift = .2f * q; float z = air < .45f ? 1 - .4f * GameKit.ease(air / .45f) : .6f + .6f * GameKit.spring((air - .45f) / .55f); sx = sy = z; break; }
            default: lift = .7f * (float) Math.sin(Math.PI * air); turn = -720 * GameKit.ease(air);
        }
        return new float[]{lift, turn, sx, sy};
    }

    void drawGoHome(Canvas c, float t) {
        int[] at = new int[2]; getLocationOnScreen(at);
        for (int k = 0; k < 4; k++) {
            float p = motion ? GameKit.clamp((t - k * 30f) / (OUT_MS - 90f)) : 1f, m = GameKit.smooth(p);
            RectF s0 = headAt[k], s1 = homeBox; s1.set(homeSquares[k]); s1.offset(-at[0], -at[1]);
            float cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m, cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m;
            float size = s0.width() + (s1.width() - s0.width()) * m;
            int from = (k % 2) == (k / 2) ? GameKit.BLUE : GameKit.LIGHT, to = (k % 2) == (k / 2) ? ink : soft;
            sqBox.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2); square(c, sqBox, GameKit.mix(from, to, GameKit.ease(p)), p < .8f ? 1f : Math.max(0f, 1f - GameKit.ease((p - .8f) / .2f)), (k % 2 == 0 ? 1 : -1) * 180 * GameKit.ease(p));
        }
    }

    void drawPop(Canvas c, float t) {
        int[] at = new int[2]; getLocationOnScreen(at);
        RectF p0 = homeBox; p0.set(popAt); p0.offset(-at[0], -at[1]);
        float k = GameKit.ease(t / 420f), r = p0.height() / 2f, grow = 1 + .35f * k;
        box.set(p0.centerX() - p0.width() / 2 * grow, p0.centerY() - p0.height() / 2 * grow, p0.centerX() + p0.width() / 2 * grow, p0.centerY() + p0.height() / 2 * grow);
        stroke.setStrokeWidth(2.5f * d * (1 - k)); stroke.setColor(GameKit.alpha(night ? 0xFFBFD8FF : 0xFF7FA6F5, 1 - k));
        c.drawRoundRect(box, r * grow, r * grow, stroke);
        if (drops[0] == 0) for (int i = 0; i < 12; i++) {
            double ang = Math.PI * 2 * i / 12 + rnd.nextFloat() * .3;
            drops[i * 4] = (float) Math.cos(ang); drops[i * 4 + 1] = (float) Math.sin(ang);
            drops[i * 4 + 2] = (28 + rnd.nextFloat() * 34) * d; drops[i * 4 + 3] = (1.6f + rnd.nextFloat() * 2.2f) * d;
        }
        for (int i = 0; i < 12; i++) {
            float dx = drops[i * 4], dy = drops[i * 4 + 1], far = drops[i * 4 + 2], sz = drops[i * 4 + 3];
            float x = p0.centerX() + dx * (p0.width() / 2f + far * k), y = p0.centerY() + dy * (p0.height() / 2f + far * k * .7f) + 14 * d * k * k;
            fill.setShader(null); fill.setColor(GameKit.alpha(i % 3 == 0 ? 0xFFFFB3E0 : i % 3 == 1 ? 0xFFA8CCFF : 0xFFFFF1A8, (1 - k) * .95f));
            c.drawCircle(x, y, sz * (1 - .5f * k), fill);
        }
    }
}
