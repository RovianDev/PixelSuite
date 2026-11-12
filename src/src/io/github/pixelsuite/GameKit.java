package io.github.pixelsuite;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Random;

final class GameKit {
    private GameKit() { }

    static final int BLUE = 0xFF1846B8, LIGHT = 0xFF9EC1FF;
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD), MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL), REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final android.graphics.Path PATH = new android.graphics.Path();
    private static final RectF OVAL = new RectF();
    static android.graphics.Path path() { PATH.reset(); return PATH; }
    static RectF rf(float l, float t, float r, float b) { OVAL.set(l, t, r, b); return OVAL; }
    static final int[] RAINBOW = {0xFF0D2E8A, 0xFF1846B8, 0xFF4F8DF5, 0xFF2BB673, 0xFFF2C230, 0xFFF28C28, 0xFFE53935};

    static float ease(float t) { return PixelStack.ease(t); }
    static float easeIn(float t) { return PixelStack.easeIn(t); }
    static float spring(float t) { return PixelStack.spring(t); }
    static float smooth(float t) { t = clamp(t); return t < .5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }
    static float clamp(float t) { return Math.max(0f, Math.min(1f, t)); }

    static final int[][] WORLDS = {
        null,
        {0xFF3A2C1A, 0xFF46361F, 0xFFD9A441, 0xFFB07D2A, 0xFF8A6B4A, 0xFF6B4F33, 0xFF2AA6A0, 0xFF7FA650, 0xFFE8C98A},
        {0xFF2B1A26, 0xFF37212F, 0xFFF29BB8, 0xFFD9708F, 0xFF7A5A6A, 0xFF5B3F4E, 0xFF7FB8E8, 0xFFF7B6CC, 0xFFFFC2D6},
        {0xFF14222E, 0xFF1B2D3C, 0xFF9FD8F5, 0xFF6FB6E0, 0xFFDCEFFB, 0xFFA9CFE6, 0xFF3C7FD9, 0xFFE8F6FF, 0xFFFFFFFF},
        {0xFF12241A, 0xFF193223, 0xFF8C6A3F, 0xFF6B4E2B, 0xFF5E7F4A, 0xFF3F5A33, 0xFF2F8FBF, 0xFF2FBF6B, 0xFFD8F26B},
        {0xFF241210, 0xFF301714, 0xFFB3472E, 0xFF7A2A1C, 0xFF4A4448, 0xFF2E2A2D, 0xFFFF6A1F, 0xFF6B5A4A, 0xFFFF9A3D},
        {0xFF0E0B1E, 0xFF161031, 0xFFB14CFF, 0xFF7A2BD6, 0xFF23D5D5, 0xFF168F8F, 0xFFFF2D9A, 0xFF3DFFB0, 0xFFFFFFFF},
    };
    static int world(int stage) { return Math.max(0, stage - 1) % WORLDS.length; }
    private static float frac(double v) { return (float) (v - Math.floor(v)); }

    static int mix(int x, int y, float t) {
        t = clamp(t);
        int a = Math.round(((x >>> 24) & 255) + (((y >>> 24) & 255) - ((x >>> 24) & 255)) * t);
        int r = Math.round(((x >> 16) & 255) + (((y >> 16) & 255) - ((x >> 16) & 255)) * t);
        int g = Math.round(((x >> 8) & 255) + (((y >> 8) & 255) - ((x >> 8) & 255)) * t);
        int b = Math.round((x & 255) + ((y & 255) - (x & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    static int alpha(int c, float f) { return (Math.round(((c >>> 24) & 255) * clamp(f)) << 24) | (c & 0xFFFFFF); }

    static void squaresOf(RectF logo, RectF[] out) {
        float s = Math.min(logo.width(), logo.height()), u = .235f * s, g = .07f * s, cx = logo.centerX(), cy = logo.centerY();
        float x0 = cx - u - g / 2f, y0 = cy - u - g / 2f;
        for (int k = 0; k < 4; k++) {
            int col = k % 2, row = k / 2;
            if (out[k] == null) out[k] = new RectF();
            out[k].set(x0 + col * (u + g), y0 + row * (u + g), x0 + col * (u + g) + u, y0 + row * (u + g) + u);
        }
    }

    private static long sLastBuzz;
    private static boolean buzzNow() { long now = SystemClock.uptimeMillis(); if (now - sLastBuzz < 35) return false; sLastBuzz = now; return true; }
    static void tick(Context c, float strength) { if (!buzzNow()) return; PixelStack.buzz(c, new int[]{7}, new float[]{strength}, new int[]{0}, new long[]{0, 12}, new int[]{0, Math.round(80 + 170 * clamp(strength))}); }
    static void thump(Context c, float strength) { PixelStack.buzz(c, new int[]{2, 7}, new float[]{clamp(strength), clamp(strength) * .6f}, new int[]{0, 30}, new long[]{0, 50}, new int[]{0, Math.round(120 + 130 * clamp(strength))}); }
    static void rumble(Context c) { PixelStack.buzz(c, new int[]{2, 2, 7}, new float[]{1f, .8f, .5f}, new int[]{0, 60, 60}, new long[]{0, 60, 40, 50, 40, 30}, new int[]{0, 255, 0, 180, 0, 110}); }

    static Dialog window(Activity a, View content, boolean dark, final Runnable back) {
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar) {
            @Override public void onBackPressed() { if (back != null) back.run(); else dismiss(); }
        };
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            w.setWindowAnimations(0); w.setDimAmount(0f);
            w.setStatusBarColor(0); w.setNavigationBarColor(0);
            if (!dark) w.getDecorView().setSystemUiVisibility(0x2000 | 0x10 | 0x400 | 0x200 | 0x100); else w.getDecorView().setSystemUiVisibility(0x400 | 0x200 | 0x100);
            try { Window.class.getMethod("setDecorFitsSystemWindows", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
            try { WindowManager.LayoutParams lp = w.getAttributes(); lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 3); w.setAttributes(lp); } catch (Throwable ignored) { }
        }
        d.setCancelable(true);
        return d;
    }

    static final class Bit { float x, y, vx, vy, size, rot, vr, life = 1f, decay = 1f, gravity = 1f; int colour; boolean round; }
    static final class Pop { String text; float x, y, size; long at; int colour; }
    static final class Cell { float sx, sy, tx, ty, size, x, y, now, delay; int colour, col; }

    abstract static class Game extends View {
        static final int INTRO = 0, READY = 1, BUILD = 2, PLAY = 3, CLEAR = 4, OVER = 5, EXIT = 6, DYING = 7, PAUSED = 8;

        static final Game[] kept = new Game[3];
        int resumeState; long resumeStateAt, pausedAt; boolean keep;
        final RectF newGameAt = new RectF();
        static boolean paused(Activity act, int id) { Game g = kept[id]; return g != null && g.a == act && !act.isFinishing(); }
        static Activity watching;
        static void whenClosed(final Activity act) {
            if (watching == act) return;
            watching = act;
            try {
                act.getApplication().registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
                    public void onActivityCreated(Activity x, android.os.Bundle b) { } public void onActivityStarted(Activity x) { } public void onActivityResumed(Activity x) { }
                    public void onActivityPaused(Activity x) { } public void onActivityStopped(Activity x) { } public void onActivitySaveInstanceState(Activity x, android.os.Bundle b) { }
                    public void onActivityDestroyed(Activity x) {
                        if (x != act) return;
                        for (int i = 0; i < kept.length; i++) if (kept[i] != null && kept[i].a == act) kept[i] = null;
                        if (watching == act) watching = null;
                        PixelStack.forget(act);
                        try { act.getApplication().unregisterActivityLifecycleCallbacks(this); } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable ignored) { }
        }

        void resume(RectF card, Runnable gone) {
            cardScreen = card; landedHome = false; firstFrame = true; keep = false; pausedHere = false;
            try { if (getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) getParent()).removeView(this); } catch (Throwable ignored) { }
            go(PAUSED);
            try { show(gone); } catch (Throwable t) { kept[arcadeId()] = null; if (gone != null) gone.run(); return; }
            invalidate();
        }
        boolean pausedHere;
        @Override public void onWindowFocusChanged(boolean has) {
            super.onWindowFocusChanged(has);
            if (has || cardAt == null || !(state == PLAY || state == BUILD || state == CLEAR || state == DYING)) return;
            resumeState = state; resumeStateAt = stateAt; pausedAt = SystemClock.uptimeMillis(); pausedHere = true;
            go(PAUSED);
            GameSave.save(a, arcadeId(), this);
        }

        void carryOn() {
            long now = SystemClock.uptimeMillis(), by = now - pausedAt;
            long was = resumeStateAt, from = pausedAt;
            shiftClocks(this, from, by, new java.util.IdentityHashMap<Object, Boolean>(), 0);
            resumeStateAt = was; pausedAt = from;
            state = resumeState; stateAt = Math.min(now, was + by); last = now;
            joyId = actionId = -1;
            tick(a, .6f); PixelStackSound.play(PixelStackSound.SOFT, .34f, 1.2f);
        }
        static void shiftClocks(Object o, long from, long by, java.util.IdentityHashMap<Object, Boolean> seen, int depth) {
            if (o == null || depth > 3 || seen.put(o, Boolean.TRUE) != null) return;
            try {
                if (o instanceof java.util.List) { for (Object e : (java.util.List<?>) o) shiftClocks(e, from, by, seen, depth + 1); return; }
                if (o instanceof Object[]) { for (Object e : (Object[]) o) shiftClocks(e, from, by, seen, depth + 1); return; }
                if (o instanceof long[]) { long[] ls = (long[]) o; for (int i = 0; i < ls.length; i++) if (ls[i] > from - 3600000L && ls[i] > 100000L) ls[i] += by; return; }
                for (Class<?> k = o.getClass(); k != null && k.getName().startsWith("io.github.pixelsuite"); k = k.getSuperclass()) {
                    for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Class<?> ty = f.getType();
                        if (ty == long.class) { long v = f.getLong(o); if (v > from - 3600000L && v > 100000L) f.setLong(o, v + by); }
                        else if (!ty.isPrimitive()) {
                            Object v = f.get(o);
                            if (v instanceof java.util.List || v instanceof Object[] || v instanceof long[] || (v != null && v.getClass().getName().startsWith("io.github.pixelsuite") && !(v instanceof View) && !(v instanceof ShareBar))) shiftClocks(v, from, by, seen, depth + 1);
                        }
                    }
                }
            } catch (Throwable ignored) { }
        }

        void drawPaused(Canvas c, float t, long now) {
            int W = getWidth(), H = getHeight();
            float in = motion ? ease(t / 240f) : 1f;
            if (in < 1 && !pausedHere) { drawScene(c, 1 - in, now); drawTitleArcade(c, 1 - in, 0f); }
            int layer = c.saveLayerAlpha(0, 0, W, H, Math.round(255 * in), Canvas.ALL_SAVE_FLAG);
            if (resumeState == BUILD || resumeState == CLEAR) drawPlay(c, 0f, now); else drawPlay(c, 0f, now);
            drawControls(c, .35f);
            c.restoreToCount(layer);
            fill.setShader(null); fill.setColor(alpha(black ? 0xFF000000 : night ? 0xFF0B0E14 : 0xFFFFFFFF, .62f * in)); c.drawRect(0, 0, W, H, fill);
            float y = H * .42f;
            fill.setColor(alpha(night ? LIGHT : BLUE, in));
            box.set(W / 2f - 17 * d, y - 78 * d, W / 2f - 5 * d, y - 40 * d); c.drawRoundRect(box, 4 * d, 4 * d, fill);
            box.set(W / 2f + 5 * d, y - 78 * d, W / 2f + 17 * d, y - 40 * d); c.drawRoundRect(box, 4 * d, 4 * d, fill);
            text.setTextAlign(Paint.Align.CENTER); text.setTypeface(BOLD); text.setTextSize(sp(30)); text.setColor(alpha(ink, in));
            c.drawText(I18n.t("Paused"), W / 2f, y, text);
            text.setTypeface(MEDIUM); text.setTextSize(sp(15)); text.setColor(alpha(soft, in * 1.6f));
            c.drawText(String.valueOf(score), W / 2f, y + 28 * d, text);
            float pulse = .5f + .5f * (float) (.5 + .5 * Math.cos(now / 300.0));
            text.setTextSize(sp(16)); text.setColor(alpha(ink, in * pulse));
            c.drawText(I18n.t("Tap to continue"), W / 2f, y + 66 * d, text);
            text.setTypeface(MEDIUM); text.setTextSize(sp(14));
            String ng = I18n.t("New game"); float bw = text.measureText(ng) + 36 * d, by2 = H - 96 * d - bottomInset * .5f;
            newGameAt.set(W / 2f - bw / 2, by2, W / 2f + bw / 2, by2 + 40 * d);
            stroke.setStrokeWidth(1.4f * d); stroke.setColor(alpha(soft, in * 1.4f)); c.drawRoundRect(newGameAt, 20 * d, 20 * d, stroke);
            text.setColor(alpha(ink, in * .9f)); c.drawText(ng, W / 2f, newGameAt.centerY() + 5 * d, text);
        }
        static final float IN_MS = 820, HOME_MS = 660;

        Dialog dialog; Runnable onGone;
        final Activity a; final RectF home; final RectF[] startAt = new RectF[4];
        final int ink, page, soft, plate, rim; final boolean dark, black, night; final float d;
        final boolean motion; final SharedPreferences prefs; final Random rnd = new Random();
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box = new RectF();
        final ArrayList<Bit> bits = new ArrayList<Bit>();
        final ArrayList<Pop> pops = new ArrayList<Pop>();
        ArrayList<Cell> cells;
        final RectF[] logoAt = new RectF[4], bigAt = new RectF[4];
        int state = INTRO, exitFrom = INTRO, best, score; boolean newBest, introSound, landedHome, firstFrame, tapOk; float gateFrom, tapX, tapY;
        long stateAt, last, shakeAt = -10000, flashAt = -10000; float shakeHard;
        float topInset, bottomInset, introSize;
        final int[] squareColours;
        final ShareBar shareBar;
        boolean fromReady;

        Game(Activity a, RectF home, RectF[] from, int ink, int page, boolean dark, boolean black, String prefsName, int[] squareColours) {
            super(a);
            this.a = a; this.home = new RectF(home); this.ink = ink | 0xFF000000; this.page = page | 0xFF000000; this.dark = dark; this.black = black; night = dark || black;
            this.squareColours = squareColours;
            soft = (Math.round(255 * .45f) << 24) | (ink & 0xFFFFFF);
            plate = black ? 0xFF000000 : dark ? mix(this.page, 0xFFFFFFFF, .06f) : mix(this.page, 0xFFFFFFFF, .7f);
            rim = black ? 0x40FFFFFF : dark ? 0x1FFFFFFF : 0x14000000;
            d = getResources().getDisplayMetrics().density;
            float scale = 1f;
            try { scale = android.provider.Settings.Global.getFloat(a.getContentResolver(), "animator_duration_scale", 1f); } catch (Throwable ignored) { }
            motion = scale > 0f;
            prefs = a.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            best = prefs.getInt("best", 0);
            for (int k = 0; k < 4; k++) if (from != null && from[k] != null) startAt[k] = new RectF(from[k]);
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
            text.setTextAlign(Paint.Align.CENTER);
            setFocusable(true);
            stateAt = last = SystemClock.uptimeMillis();
            shareBar = new ShareBar(this, a, prefs, shareKey(), this.ink, soft, night);
        }

        RectF cardScreen, cardAt;
        static final float CARD_IN_MS = 640, CARD_OUT_MS = 560;
        Game fromCard(RectF screen) { cardScreen = new RectF(screen); return this; }

        void show(Runnable gone) {
            onGone = gone;
            android.widget.FrameLayout root = new android.widget.FrameLayout(a);
            root.addView(this, new android.widget.FrameLayout.LayoutParams(-1, -1));
            shareBar.attach(root);
            dialog = window(a, root, night, () -> { if (shareBar.typing()) shareBar.closeKeyboard(); else exit(); });
            Window w = dialog.getWindow();
            if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            dialog.show();
        }

        abstract String title();
        abstract int arcadeId();
        abstract String shareKey();
        abstract String hint();
        abstract void layout(int w, int h);
        abstract void newGame();
        abstract boolean drawBuild(Canvas c, float t, float dt);
        abstract void stepPlay(float dt);
        abstract void drawPlay(Canvas c, float dt, long now);
        abstract boolean drawClear(Canvas c, float t, float dt);
        abstract void shatter();
        abstract void action(boolean down);
        abstract void drawActionIcon(Canvas c, float x, float y, float r, int colour);

        long since() { return SystemClock.uptimeMillis() - stateAt; }
        void go(int s) { state = s; stateAt = SystemClock.uptimeMillis(); if (s == OVER && cardAt != null) GameSave.drop(a, arcadeId()); }
        float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
        int ink2() { return night ? 0xFFFFFFFF : 0xFF14161C; }

        void gameOver() {
            if (state == OVER) return;
            newBest = score > best;
            if (newBest) { best = score; try { prefs.edit().putInt("best", best).apply(); } catch (Throwable ignored) { } }
            joyId = -1; dir = -1; actionId = -1;
            shatter();
            buildWords(I18n.t("Game Over"));
            go(OVER);
            PixelStackSound.play(PixelStackSound.OVER, .55f, 1f);
            if (newBest && score > 0) postDelayed(() -> PixelStackSound.play(PixelStackSound.SUCCESS, .55f, 1.12f), 900);
            rumble(a);
        }

        void exit() {
            if (state == EXIT) return;
            if (state == INTRO || state == READY) {  }
            else squaresOf(centreLogo(), bigAt);
            gateFrom = state == INTRO && cardAt != null ? clamp(1 - since() / ArcadeCurtain.EXTRAS_MS) : 0f;
            keep = cardAt != null && (state == PLAY || state == BUILD || state == CLEAR || state == DYING || state == PAUSED);
            if (keep && state != PAUSED) { resumeState = state; resumeStateAt = stateAt; pausedAt = SystemClock.uptimeMillis(); }
            exitFrom = state;
            shareBar.hideField();
            go(EXIT);
            tick(a, .5f);
            PixelStackSound.play(PixelStackSound.SOFT, .3f, 1.12f); postDelayed(() -> PixelStackSound.play(PixelStackSound.SOFT, .26f, .9f), 120);
        }

        RectF centreLogo() { float cx = getWidth() / 2f, cy = getHeight() * .4f; return new RectF(cx - introSize / 2, cy - introSize / 2, cx + introSize / 2, cy + introSize / 2); }

        void shake(float hard) { if (!motion) return; shakeAt = SystemClock.uptimeMillis(); shakeHard = hard; }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            try {
                android.view.WindowInsets in = getRootWindowInsets();
                if (in != null) { topInset = in.getSystemWindowInsetTop(); bottomInset = in.getSystemWindowInsetBottom(); }
            } catch (Throwable ignored) { }
            if (topInset <= 0) topInset = 32 * d;
            if (bottomInset <= 0) bottomInset = 24 * d;
            introSize = Math.min(w, h) * .42f;
            squaresOf(home, logoAt);
            if (startAt[0] != null) for (int k = 0; k < 4; k++) logoAt[k] = new RectF(startAt[k]);
            squaresOf(centreLogo(), bigAt);
            if (cardScreen != null) { int[] at = new int[2]; getLocationOnScreen(at); cardAt = new RectF(cardScreen); cardAt.offset(-at[0], -at[1]); }
            layout(w, h);
        }

        final RectF pad = new RectF();
        int joyId = -1, actionId = -1, dir = -1; float joyX, joyY, knobX, knobY; long actionAt = -10000;
        float actionX, actionY, actionR;

        static final class Slot { int kind, count = 1; long at, bumpAt = -10000; }
        final ArrayList<Slot> tray = new ArrayList<Slot>();
        final RectF[] trayAt = {new RectF(), new RectF(), new RectF()};
        long trayUsedAt = -10000; float trayUsedX, trayUsedY;
        boolean stash(int kind) {
            for (Slot have : tray) if (have.kind == kind) { have.count++; have.bumpAt = SystemClock.uptimeMillis(); return true; }
            if (tray.size() >= 3) return false;
            Slot s = new Slot(); s.kind = kind; s.at = SystemClock.uptimeMillis(); tray.add(s);
            return true;
        }
        static final int[] TIER_COLOUR = {0xFF6FA0F5, 0xFFB15CFF, 0xFFFFC531};
        static final String[] TIER_NAME = {"Common", "Rare", "Legendary"};
        int tierOf(int kind) { return 0; }
        int[][] guide() { return null; }
        String abilityHint(int kind) { return ""; }

        void prizeGlow(Canvas c, float cx, float cy, float s, int tier, long now, float fade) {
            if (tier <= 0 || fade <= 0) return;
            int col = TIER_COLOUR[tier]; float pulse = motion ? (float) (.5 + .5 * Math.sin(now / 240.0)) : .5f;
            fill.setShader(null);
            fill.setColor(alpha(col, fade * (.14f + .10f * pulse))); c.drawCircle(cx, cy, s * (1.05f + .12f * pulse), fill);
            fill.setColor(alpha(col, fade * (.22f + .12f * pulse))); c.drawCircle(cx, cy, s * .78f, fill);
            float turn = motion ? now / (tier == 2 ? 9f : 14f) % 360 : 0;
            if (tier == 2) {
                int save = c.save(); c.rotate(turn, cx, cy);
                fill.setColor(alpha(0xFFFFE9A8, fade * (.34f + .2f * pulse)));
                for (int i = 0; i < 8; i++) {
                    android.graphics.Path ray = path(); float len = s * (i % 2 == 0 ? 1.5f : 1.05f), wd = s * .16f;
                    ray.moveTo(cx - wd, cy); ray.lineTo(cx, cy - len); ray.lineTo(cx + wd, cy); ray.close();
                    c.drawPath(ray, fill); c.rotate(45, cx, cy);
                }
                c.restoreToCount(save);
                if (motion && rnd.nextFloat() < .12f) { Bit b = spark(cx + (rnd.nextFloat() - .5f) * s, cy + (rnd.nextFloat() - .5f) * s, rnd.nextBoolean() ? 0xFFFFE9A8 : col, 60, 3.5f); b.gravity = -.25f; b.decay = 1.8f; }
            } else {
                stroke.setStrokeWidth(2 * d); stroke.setColor(alpha(col, fade * (.6f + .3f * pulse)));
                RectF o = rf(cx - s * .86f, cy - s * .86f, cx + s * .86f, cy + s * .86f);
                for (int i = 0; i < 6; i++) c.drawArc(o, turn + i * 60, 32, false, stroke);
            }
        }

        void drawFrost(Canvas c, RectF field, long at, long until, long now, float rad) {
            if (now >= until || now < at) return;
            float k = Math.min(clamp((now - at) / 350f), clamp((until - now) / 800f));
            fill.setShader(null); fill.setColor(alpha(0xFFBFE3FF, .13f * k)); c.drawRoundRect(field, rad, rad, fill);
            for (int i = 0; i < 6; i++) {
                float in = i * 5 * d + 2.5f * d;
                box.set(field.left + in, field.top + in, field.right - in, field.bottom - in);
                stroke.setStrokeWidth(5.4f * d); stroke.setColor(alpha(0xFFE6F6FF, k * (.5f - i * .08f))); c.drawRoundRect(box, Math.max(2 * d, rad - in), Math.max(2 * d, rad - in), stroke);
            }
            stroke.setStrokeWidth(2 * d); stroke.setColor(alpha(0xFFFFFFFF, .75f * k));
            for (int q = 0; q < 4; q++) {
                float x0 = q % 2 == 0 ? field.left + 6 * d : field.right - 6 * d, y0 = q < 2 ? field.top + 6 * d : field.bottom - 6 * d, sx = q % 2 == 0 ? 1 : -1, sy = q < 2 ? 1 : -1;
                for (int j = 0; j < 5; j++) {
                    double ang = Math.PI / 2 * (j / 4.0); float len = (j % 2 == 0 ? 62 : 38) * d * k;
                    float ex = x0 + sx * (float) Math.cos(ang) * len, ey = y0 + sy * (float) Math.sin(ang) * len;
                    c.drawLine(x0, y0, ex, ey, stroke);
                    float mx = x0 + (ex - x0) * .6f, my = y0 + (ey - y0) * .6f, b = 9 * d * k;
                    c.drawLine(mx, my, mx + sx * (float) Math.cos(ang + .8) * b, my + sy * (float) Math.sin(ang + .8) * b, stroke);
                    c.drawLine(mx, my, mx + sx * (float) Math.cos(ang - .8) * b, my + sy * (float) Math.sin(ang - .8) * b, stroke);
                }
            }
            float w = (now - at) / 700f;
            if (w < 1) {
                float e = ease(w), r = field.width() * .82f * e;
                stroke.setStrokeWidth(16 * d * (1 - w)); stroke.setColor(alpha(0xFF9FE0FF, (1 - w) * .7f)); c.drawCircle(field.centerX(), field.centerY(), r, stroke);
                stroke.setStrokeWidth(3 * d); stroke.setColor(alpha(0xFFFFFFFF, (1 - w) * .8f)); c.drawCircle(field.centerX(), field.centerY(), r * 1.04f, stroke);
            }
            if (motion && rnd.nextFloat() < .4f) { Bit b = spark(field.left + rnd.nextFloat() * field.width(), field.top + rnd.nextFloat() * field.height() * .5f, 0xFFFFFFFF, 20, 3.2f); b.round = true; b.gravity = .05f; b.decay = .7f; b.vy = Math.abs(b.vy) + 30 * d; }
        }

        void drawHaze(Canvas c, RectF field, long at, long until, long now, int colour, float rad) {
            if (now >= until || now < at) return;
            float k = Math.min(clamp((now - at) / 400f), clamp((until - now) / 900f));
            fill.setShader(null); fill.setColor(alpha(colour, .14f * k)); c.drawRoundRect(field, rad, rad, fill);
            int save = c.save(); c.clipRect(field);
            for (int i = 0; i < 12; i++) {
                float px0 = field.left + field.width() * (.5f + .5f * (float) Math.sin(now / (1900.0 + i * 170) + i * 1.7));
                float py0 = field.top + field.height() * (.5f + .5f * (float) Math.cos(now / (2300.0 + i * 130) + i * 2.3));
                fill.setColor(alpha(i % 2 == 0 ? colour : mix(colour, 0xFFFFFFFF, .35f), .12f * k)); c.drawCircle(px0, py0, (46 + 12 * (i % 4)) * d, fill);
            }
            for (int i = 0; i < 4; i++) {
                float in = i * 7 * d + 3.5f * d;
                box.set(field.left + in, field.top + in, field.right - in, field.bottom - in);
                stroke.setStrokeWidth(7.5f * d); stroke.setColor(alpha(colour, k * (.34f - i * .07f))); c.drawRoundRect(box, Math.max(2 * d, rad - in), Math.max(2 * d, rad - in), stroke);
            }
            float w = (now - at) / 800f;
            if (w < 1) { float e = ease(w), r = field.width() * .85f * e; stroke.setStrokeWidth(26 * d * (1 - w)); stroke.setColor(alpha(colour, (1 - w) * .6f)); c.drawCircle(field.centerX(), field.centerY(), r, stroke); }
            c.restoreToCount(save);
            if (motion && rnd.nextFloat() < .25f) { Bit b = spark(field.left + rnd.nextFloat() * field.width(), field.top + field.height() * (.3f + rnd.nextFloat() * .7f), mix(colour, 0xFFFFFFFF, .3f), 20, 5f); b.round = true; b.gravity = -.08f; b.decay = .9f; }
        }

        void drawGuide(Canvas c, float top, float bottom, float fade) {
            int[][] g = guide();
            if (g == null || fade <= 0 || bottom - top < 60 * d) return;
            float W = getWidth(), side = 14 * d, in = 12 * d, rowH = 36 * d, padTop = 22 * d, padBot = 8 * d, gap = 14 * d, total = 0;
            for (int[] tier : g) total += padTop + ((tier.length + 1) / 2) * rowH + padBot;
            total += gap * (g.length - 1);
            float k = Math.max(.6f, Math.min(1.16f, (bottom - top) / total));
            float spare = Math.max(0, (bottom - top) - total * k), extra = Math.min(12 * d, spare / (g.length + 1));
            int save = c.save(); c.translate(W / 2f, top + extra); c.scale(k, k); c.translate(-W / 2f, 0);
            float y = 0, colW = (W - side * 2 - in * 2) / 2f;
            for (int t = 0; t < g.length; t++) {
                int col = TIER_COLOUR[t]; int rows = (g[t].length + 1) / 2;
                float h = padTop + rows * rowH + padBot;
                box.set(side, y, W - side, y + h);
                fill.setShader(null); fill.setColor(alpha(col, fade * (night ? .09f : .07f))); c.drawRoundRect(box, 18 * d, 18 * d, fill);
                stroke.setStrokeWidth(1.2f * d); stroke.setColor(alpha(col, fade * (t == 2 ? .6f : .34f))); c.drawRoundRect(box, 18 * d, 18 * d, stroke);
                text.setTypeface(BOLD); text.setTextSize(sp(11)); text.setTextAlign(Paint.Align.CENTER);
                String name = I18n.t(TIER_NAME[t]).toUpperCase(); float tw = text.measureText(name) + 22 * d;
                boolean rtl = I18n.rtl(); float tl = rtl ? W - side - 16 * d - tw : side + 16 * d;
                box.set(tl, y - 10 * d, tl + tw, y + 10 * d);
                fill.setColor(alpha(black ? 0xFF000000 : page, fade)); c.drawRoundRect(box, 10 * d, 10 * d, fill);
                fill.setColor(alpha(col, fade * (night ? .3f : .22f))); c.drawRoundRect(box, 10 * d, 10 * d, fill);
                stroke.setColor(alpha(col, fade * .7f)); c.drawRoundRect(box, 10 * d, 10 * d, stroke);
                text.setColor(alpha(night ? mix(col, 0xFFFFFFFF, .5f) : mix(col, 0xFF000000, .35f), fade)); c.drawText(name, box.centerX(), y + 4 * d, text);
                for (int i = 0; i < g[t].length; i++) {
                    int kind = g[t][i]; int cl = i % 2; if (rtl) cl = 1 - cl;
                    float x = side + in + cl * colW, ry = y + padTop + (i / 2) * rowH, icy = ry + rowH / 2, icx = rtl ? x + colW - 15 * d : x + 15 * d;
                    fill.setColor(alpha(col, fade * (night ? .22f : .15f))); c.drawCircle(icx, icy, 14 * d, fill);
                    stroke.setStrokeWidth(1.2f * d); stroke.setColor(alpha(col, fade * (t == 0 ? .35f : .8f))); c.drawCircle(icx, icy, 14 * d, stroke);
                    text.setTextAlign(Paint.Align.CENTER);
                    abilityIcon(c, kind, icx, icy, 8.5f * d, fade);
                    float room = colW - 40 * d, tx = rtl ? icx - 22 * d : icx + 22 * d;
                    text.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
                    text.setTypeface(BOLD); text.setTextSize(sp(13)); String nm = abilityName(kind); float nw = text.measureText(nm); if (nw > room) text.setTextSize(sp(13) * room / nw);
                    text.setColor(alpha(ink, fade)); c.drawText(nm, tx, icy - 2 * d, text);
                    text.setTypeface(MEDIUM); text.setTextSize(sp(11)); String hn = abilityHint(kind); float hw = text.measureText(hn); if (hw > room) text.setTextSize(sp(11) * room / hw);
                    text.setColor(alpha(soft, fade * 1.5f)); c.drawText(hn, tx, icy + 12 * d, text);
                }
                y += h + gap + extra / k;
            }
            text.setTextAlign(Paint.Align.CENTER);
            c.restoreToCount(save);
        }

        String abilityName(int kind) { return ""; }
        int abilityColour(int kind) { return night ? LIGHT : BLUE; }
        void abilityIcon(Canvas c, int kind, float x, float y, float r, float fade) { }
        void useAbility(int kind) { }

        void layTray() {
            text.setTypeface(BOLD); text.setTextSize(sp(13));
            float h = 38 * d, gap = 8 * d, total = 0; float[] w = new float[tray.size()];
            for (int i = 0; i < tray.size(); i++) { w[i] = 40 * d + text.measureText(abilityName(tray.get(i).kind)) + 14 * d + (tray.get(i).count > 1 ? 30 * d : 0); total += w[i] + (i > 0 ? gap : 0); }
            float k = total > getWidth() - 16 * d ? (getWidth() - 16 * d) / total : 1f;
            float x = getWidth() / 2f - total * k / 2, y = pad.top + 6 * d;
            for (int i = 0; i < tray.size(); i++) { trayAt[i].set(x, y, x + w[i] * k, y + h); x += (w[i] + gap) * k; }
        }

        boolean trayTap(float x, float y) {
            if (tray.isEmpty()) return false;
            layTray();
            for (int i = 0; i < tray.size(); i++) {
                RectF r = trayAt[i];
                if (x < r.left - 4 * d || x > r.right + 4 * d || y < r.top - 8 * d || y > r.bottom + 8 * d) continue;
                Slot s = tray.get(i);
                if (--s.count <= 0) tray.remove(i); else s.bumpAt = SystemClock.uptimeMillis();
                trayUsedAt = SystemClock.uptimeMillis(); trayUsedX = r.centerX(); trayUsedY = r.centerY();
                thump(a, .8f);
                useAbility(s.kind);
                return true;
            }
            return false;
        }

        void drawTray(Canvas c, float fade) {
            long now = SystemClock.uptimeMillis();
            if (now - trayUsedAt < 420) {
                float k = ease((now - trayUsedAt) / 420f);
                stroke.setStrokeWidth(3 * d * (1 - k)); stroke.setColor(alpha(night ? LIGHT : BLUE, (1 - k) * fade)); c.drawCircle(trayUsedX, trayUsedY, (20 + 46 * k) * d, stroke);
            }
            if (tray.isEmpty() || fade <= 0) return;
            layTray();
            for (int i = 0; i < tray.size(); i++) {
                Slot s = tray.get(i); RectF r = trayAt[i];
                float in = motion ? spring((now - s.at) / 420f) * (1 + .12f * (1 - clamp((now - s.bumpAt) / 260f))) : 1f, pulse = motion ? (float) (.5 + .5 * Math.sin(now / 300.0 + i)) : .5f;
                int col = abilityColour(s.kind);
                int save = c.save(); c.scale(in, in, r.centerX(), r.centerY());
                float rad = r.height() / 2;
                fill.setShader(null); fill.setColor(alpha(col, fade * (.10f + .10f * pulse))); box.set(r.left - 3 * d, r.top - 3 * d, r.right + 3 * d, r.bottom + 3 * d); c.drawRoundRect(box, rad + 3 * d, rad + 3 * d, fill);
                fill.setColor(alpha(black ? 0xFF000000 : night ? 0xFF1B2130 : 0xFFFFFFFF, fade)); c.drawRoundRect(r, rad, rad, fill);
                stroke.setStrokeWidth(1.6f * d); stroke.setColor(alpha(col, fade * (.7f + .3f * pulse))); c.drawRoundRect(r, rad, rad, stroke);
                fill.setColor(alpha(col, fade * .22f)); c.drawCircle(r.left + rad, r.centerY(), rad - 4 * d, fill);
                abilityIcon(c, s.kind, r.left + rad, r.centerY(), rad - 7 * d, fade);
                text.setTypeface(BOLD); text.setTextSize(sp(13)); text.setTextAlign(Paint.Align.LEFT); text.setColor(alpha(ink, fade));
                float badge = s.count > 1 ? 30 * d : 0;
                if (s.count > 1) {
                    box.set(r.right - badge - 4 * d, r.top + 7 * d, r.right - 7 * d, r.bottom - 7 * d);
                    fill.setColor(alpha(col, fade)); c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill);
                    text.setTypeface(BOLD); text.setTextSize(sp(12)); text.setTextAlign(Paint.Align.CENTER); text.setColor(alpha(0xFFFFFFFF, fade));
                    c.drawText("×" + s.count, box.centerX(), box.centerY() + 4.2f * d, text);
                    text.setTextSize(sp(13)); text.setTextAlign(Paint.Align.LEFT); text.setColor(alpha(ink, fade));
                }
                float room = r.right - 12 * d - badge - (r.left + rad * 2 + 2 * d); String name = abilityName(s.kind);
                float tw = text.measureText(name); if (tw > room && tw > 0) text.setTextSize(sp(13) * room / tw);
                c.drawText(name, r.left + rad * 2 + 2 * d, r.centerY() + 4.5f * d, text);
                text.setTextAlign(Paint.Align.CENTER);
                c.restoreToCount(save);
            }
        }

        void placeControls(float top) {
            int W = getWidth(), H = getHeight();
            pad.set(0, top, W, H - bottomInset);
            actionR = Math.min(46 * d, pad.height() * .3f);
            actionX = W - actionR - 30 * d; actionY = pad.centerY();
            joyX = Math.min(W * .27f, 110 * d); joyY = pad.centerY(); knobX = joyX; knobY = joyY;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            if (state != PLAY) {
                if (state == INTRO || state == READY || state == PAUSED) {
                    if (act == MotionEvent.ACTION_DOWN) { tapX = e.getX(); tapY = e.getY(); tapOk = tapX > 34 * d && tapX < getWidth() - 34 * d; }
                    else if (act == MotionEvent.ACTION_MOVE) { if (Math.hypot(e.getX() - tapX, e.getY() - tapY) > 14 * d) tapOk = false; }
                    else if (act == MotionEvent.ACTION_CANCEL) tapOk = false;
                    else if (act == MotionEvent.ACTION_UP) { if (tapOk && e.getEventTime() - e.getDownTime() < 700) tap(e.getX(), e.getY()); tapOk = false; }
                    return true;
                }
                if (act == MotionEvent.ACTION_DOWN) {
                    if (state == OVER && (e.getX() < 34 * d || e.getX() > getWidth() - 34 * d)) return true;
                    if (state == OVER && since() > 900 && shareBar.tap(e, score, who -> PixelStackShare.arcadeCard(a, arcadeId(), title(), score, best, newBest, LIGHT, who))) return true;
                    tap(e.getX(), e.getY());
                }
                return true;
            }
            int idx = e.getActionIndex();
            switch (act) {
                case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_POINTER_DOWN: {
                    int id = e.getPointerId(idx); float x = e.getX(idx), y = e.getY(idx);
                    if (trayTap(x, y)) break;
                    if (x < getWidth() * .5f) { if (joyId == -1) { joyId = id; joyX = x; joyY = Math.max(pad.top + (tray.isEmpty() ? 40 : 76) * d, y); knobX = x; knobY = y; dir = -1; } }
                    else if (actionId == -1) { actionId = id; actionAt = SystemClock.uptimeMillis(); action(true); tick(a, .35f); }
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) if (e.getPointerId(i) == joyId) steer(e.getX(i), e.getY(i));
                    break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: case MotionEvent.ACTION_CANCEL: {
                    int id = act == MotionEvent.ACTION_CANCEL ? -2 : e.getPointerId(idx);
                    if (id == joyId || id == -2) { joyId = -1; dir = -1; knobX = joyX; knobY = joyY; }
                    if (id == actionId || id == -2) { actionId = -1; action(false); }
                    break;
                }
            }
            return true;
        }

        private void steer(float x, float y) {
            float dx = x - joyX, dy = y - joyY, len = (float) Math.hypot(dx, dy), max = 44 * d;
            if (len > max) { joyX += dx * (1 - max / len); joyY += dy * (1 - max / len); dx = x - joyX; dy = y - joyY; len = max; }
            knobX = x; knobY = y;
            int was = dir;
            if (len < 12 * d) dir = -1;
            else if (Math.abs(dx) > Math.abs(dy)) dir = dx > 0 ? 1 : 3; else dir = dy > 0 ? 2 : 0;
            if (dir != was && dir != -1) tick(a, .18f);
        }

        void tap(float x, float y) {
            switch (state) {
                case INTRO: if (cardAt != null) { fromReady = true; startGame(); } else if (since() > 450) go(READY); break;
                case READY: fromReady = true; startGame(); break;
                case PAUSED: if (newGameAt.contains(x, y)) { fromReady = false; startGame(); } else carryOn(); break;
                case OVER: if (since() > 900) { shareBar.reset(); tray.clear(); newGame(); cells = null; bits.clear(); go(BUILD); PixelStackSound.play(PixelStackSound.BOING, .6f, 1f); } break;
                default: break;
            }
        }

        void startGame() {
            if (cardAt != null) GameSave.drop(a, arcadeId());
            tray.clear();
            newGame();
            go(BUILD);
            PixelStackSound.play(PixelStackSound.BOING, .6f, 1f);
            PixelStack.buzz(a, new int[]{4}, new float[]{.9f}, new int[]{0}, new long[]{0, 40}, new int[]{0, 180});
        }

        void drawControls(Canvas c, float fade) {
            if (fade <= 0) return;
            long now = SystemClock.uptimeMillis();
            float ringR = 50 * d;
            fill.setShader(null); fill.setColor(alpha(night ? 0x1AFFFFFF : 0x12000000, fade)); c.drawCircle(joyX, joyY, ringR, fill);
            stroke.setStrokeWidth(1.5f * d); stroke.setColor(alpha(night ? 0x33FFFFFF : 0x22000000, fade)); c.drawCircle(joyX, joyY, ringR, stroke);
            for (int k = 0; k < 4; k++) {
                double ang = Math.PI / 2 * k - Math.PI / 2; float ax = joyX + (float) Math.cos(ang) * ringR * .74f, ay = joyY + (float) Math.sin(ang) * ringR * .74f;
                fill.setColor(alpha(dir == k ? (night ? LIGHT : BLUE) : (night ? 0x55FFFFFF : 0x44000000), fade));
                android.graphics.Path p = path(); float s = 5 * d;
                p.moveTo(ax + (float) Math.cos(ang) * s, ay + (float) Math.sin(ang) * s);
                p.lineTo(ax + (float) Math.cos(ang + 2.3) * s, ay + (float) Math.sin(ang + 2.3) * s);
                p.lineTo(ax + (float) Math.cos(ang - 2.3) * s, ay + (float) Math.sin(ang - 2.3) * s); p.close(); c.drawPath(p, fill);
            }
            float kx = knobX, ky = knobY, dx = kx - joyX, dy = ky - joyY, len = (float) Math.hypot(dx, dy), lim = ringR * .55f;
            if (len > lim) { kx = joyX + dx / len * lim; ky = joyY + dy / len * lim; }
            fill.setColor(alpha(joyId >= 0 ? (night ? LIGHT : BLUE) : (night ? 0x66FFFFFF : 0x55000000), fade));
            c.drawCircle(kx, ky, 21 * d, fill);
            fill.setColor(alpha(0x33FFFFFF, fade)); c.drawCircle(kx - 5 * d, ky - 6 * d, 8 * d, fill);
            float pressK = actionId >= 0 ? 1f : 0f, since = now - actionAt;
            float r = actionR * (1 - .08f * pressK);
            if (since < 380) { float k = ease(since / 380f); stroke.setStrokeWidth(3 * d * (1 - k)); stroke.setColor(alpha(night ? LIGHT : BLUE, (1 - k) * fade)); c.drawCircle(actionX, actionY, actionR * (1 + .45f * k), stroke); }
            fill.setColor(alpha(night ? 0xFF2C5BD6 : BLUE, fade)); c.drawCircle(actionX, actionY, r, fill);
            if (black) { stroke.setStrokeWidth(1.5f * d); stroke.setColor(alpha(0x66FFFFFF, fade)); c.drawCircle(actionX, actionY, r, stroke); }
            fill.setColor(alpha(0x2EFFFFFF, fade)); c.drawCircle(actionX - r * .25f, actionY - r * .3f, r * .45f, fill);
            drawActionIcon(c, actionX, actionY, r, alpha(0xFFFFFFFF, fade));
            drawTray(c, fade);
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(.034f, (now - last) / 1000f); last = now;
            int W = getWidth(), H = getHeight();
            float t = since();
            if (t < 0) { stateAt = now; t = 0; }
            if (!firstFrame) { firstFrame = true; if (state == INTRO) { stateAt = now; t = 0; } }
            boolean arcade = cardAt != null;
            float shown = arcade ? 1f : state == INTRO ? ease(t / 320f) : state == EXIT ? 1 - ease((t - 80) / 420f) : 1f;
            fill.setShader(null); fill.setColor(alpha(black ? 0xFF000000 : page, shown)); c.drawRect(0, 0, W, H, fill);
            int save = c.save();
            if (motion && now - shakeAt < 380) { float k = 1 - (now - shakeAt) / 380f; c.translate((rnd.nextFloat() - .5f) * shakeHard * d * k, (rnd.nextFloat() - .5f) * shakeHard * d * k); }

            if (state == INTRO && !introSound) { introSound = true; PixelStackSound.play(PixelStackSound.SOFT, .34f, 1f); postDelayed(() -> PixelStackSound.play(PixelStackSound.SOFT, .3f, 1.26f), 110); }
            float introT = t;
            if (state == INTRO && t > (arcade ? ArcadeCurtain.EXTRAS_MS : IN_MS)) { go(READY); t = 0; }
            if (state == INTRO) {
                if (arcade) { drawScene(c, 1f, now); drawTitleArcade(c, 1f, motion ? ease(introT / ArcadeCurtain.EXTRAS_MS) : 1f); }
                else drawLogo(c, t, 1f);
            }
            if (state == READY) {
                float k = arcade || !motion ? 1f : ease((t - 150) / 520f);
                if (k < 1f) drawLogo(c, 99999, 1f - k);
                drawScene(c, k, now);
                if (arcade) drawTitleArcade(c, 1f, 1f); else drawTitle(c, t, 1f);
            }
            if (state == PAUSED) drawPaused(c, t, now);
            if (state == BUILD) {
                if (fromReady && t < 320) drawScene(c, 1f - ease(t / 320f), now); else fromReady = false;
                if (drawBuild(c, t, dt)) go(PLAY);
            }
            if (state == PLAY || state == DYING) { stepPlay(dt); drawPlay(c, dt, now); }
            if (state == CLEAR) { if (drawClear(c, t, dt)) go(BUILD); }
            if (state == OVER) drawOver(c, t, now);
            if (state == EXIT && arcade) {
                float q = motion ? clamp(gateFrom + t / ArcadeCurtain.LEAVE_MS) : 1f, e = ease(q);
                if (exitFrom == READY || exitFrom == INTRO) { drawScene(c, 1f, now); drawTitleArcade(c, 1f, 1 - e); }
                else {
                    if (e < 1) {
                        int layer = c.saveLayerAlpha(0, 0, W, H, Math.round(255 * (1 - e)), Canvas.ALL_SAVE_FLAG);
                        if (exitFrom == PAUSED) drawPaused(c, 99999, now); else if (cells != null) drawOverWords(c, now, 1f); else { drawPlay(c, 0f, now); drawControls(c, 1f); }
                        c.restoreToCount(layer);
                    }
                    drawScene(c, e, now); drawTitleArcade(c, e, 0f);
                }
            } else if (state == EXIT) {
                if (exitFrom == READY && t < 240) drawScene(c, 1f - ease(t / 240f), now);
                if (cells != null) drawOverWords(c, now, 1 - ease(t / 200f));
                else if ((exitFrom == PLAY || exitFrom == DYING || exitFrom == CLEAR || exitFrom == BUILD) && t < 280) {
                    float k = ease(t / 280f), sc = 1 - .08f * k;
                    int layer = c.saveLayerAlpha(0, 0, W, H, Math.round(255 * (1 - k)), Canvas.ALL_SAVE_FLAG);
                    c.scale(sc, sc, W / 2f, H / 2f);
                    drawPlay(c, 0f, now);
                    c.restoreToCount(layer);
                }
            }
            drawBits(c, dt);
            drawPops(c, now);
            c.restoreToCount(save);
            if (state == PLAY || state == DYING) drawControls(c, 1f);
            if (state == BUILD) drawControls(c, ease((t - 300) / 500f));
            if (state == EXIT) {
                if (arcade) {
                    float shut = (1 - gateFrom) * ArcadeCurtain.LEAVE_MS;
                    if (!landedHome && t >= shut + 30) { landedHome = true; if (onGone != null) onGone.run(); }
                    if (t >= shut + 90) { finish(); return; }
                } else {
                    drawHome(c, t);
                    if (!landedHome && t >= HOME_MS * .78f) { landedHome = true; if (onGone != null) onGone.run(); }
                    if (t >= HOME_MS) { finish(); return; }
                }
            }
            postInvalidateOnAnimation();
        }

        private void finish() {
            if (keep) { kept[arcadeId()] = this; whenClosed(a); GameSave.save(a, arcadeId(), this); }
            else if (kept[arcadeId()] == this) kept[arcadeId()] = null;
            try { if (dialog != null) dialog.dismiss(); } catch (Throwable ignored) { }
            if (!landedHome && onGone != null) { landedHome = true; onGone.run(); }
        }

        void block(Canvas c, float l, float t, float r, float b, int colour, float alphaF) {
            box.set(l, t, r, b);
            float rad = Math.min(b - t, r - l) * .26f;
            fill.setShader(null); fill.setColor(alpha(colour, alphaF)); c.drawRoundRect(box, rad, rad, fill);
            if (black) { stroke.setStrokeWidth(Math.max(1f, d)); stroke.setColor(alpha(0x55FFFFFF, alphaF)); c.drawRoundRect(box, rad, rad, stroke); }
            float h = b - t;
            box.set(l + (r - l) * .14f, t + h * .12f, r - (r - l) * .14f, t + h * .32f);
            fill.setColor(alpha(0x40FFFFFF, alphaF)); c.drawRoundRect(box, h * .08f, h * .08f, fill);
        }

        int squareColour(int k) { return squareColours[k % squareColours.length]; }

        void drawLogo(Canvas c, float t, float fade) {
            for (int k = 0; k < 4; k++) {
                float delay = k * 45f, p = motion ? clamp((t - delay) / 620f) : 1f;
                float m = spring(p), turn = (1 - ease(p)) * (k % 2 == 0 ? 220f : -220f);
                RectF s0 = logoAt[k], s1 = bigAt[k];
                float cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m;
                float cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m - (float) Math.sin(Math.PI * ease(p)) * 60 * d;
                float size = s0.width() + (s1.width() - s0.width()) * m;
                float sx = 1f, sy = 1f;
                if (p >= 1f && motion) {
                    float[] m4 = dance(k, SystemClock.uptimeMillis() - stateAt);
                    cy -= m4[0] * size; turn += m4[1]; sx = m4[2]; sy = m4[3];
                }
                int save = c.save(); c.rotate(turn, cx, cy); c.scale(sx, sy, cx, cy + size / 2 * (sy < 1 ? 1 : 0));
                block(c, cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, squareColour(k), fade);
                c.restoreToCount(save);
            }
        }

        float[] dance(int k, long t) {
            final float period = 1700f;
            float u = ((t + k * 425) % (long) period) / period;
            float lift = 0, turn = 0, sx = 1, sy = 1, air = (u - .12f) / .5f;
            boolean flying = air > 0 && air < 1;
            if (u < .12f) { float q = (float) Math.sin(Math.PI * u / .12f); sx = 1 + .22f * q; sy = 1 - .26f * q; }
            else if (u > .62f && u < .74f) { float q = (float) Math.sin(Math.PI * (u - .62f) / .12f); sx = 1 + .2f * q; sy = 1 - .22f * q; }
            if (flying) switch (k) {
                case 0: lift = .55f * (float) Math.sin(Math.PI * air); turn = 360 * ease(air); break;
                case 1: lift = .4f * (float) Math.sin(Math.PI * air); sx = (float) Math.cos(2 * Math.PI * air); if (Math.abs(sx) < .06f) sx = .06f; break;
                case 2: { float q = (float) Math.sin(Math.PI * air); lift = .2f * q; float z = air < .45f ? 1 - .45f * ease(air / .45f) : .55f + .65f * spring((air - .45f) / .55f); sx = sy = z; break; }
                default: lift = .9f * (float) Math.sin(Math.PI * air); turn = -720 * ease(air); sy = 1 + .18f * (float) Math.sin(Math.PI * air); sx = 1 - .1f * (float) Math.sin(Math.PI * air);
            }
            return new float[]{lift, turn, sx, sy};
        }

        final RectF scene = new RectF();
        RectF sceneRect() {
            int W = getWidth(), H = getHeight();
            if (cardAt != null || guide() != null) return ArcadeCurtain.scene(arcadeId(), W, H, d, topInset, scene);
            float w = Math.min(W - 40 * d, 420 * d), h = Math.min(H * .38f, w * .82f);
            scene.set(W / 2f - w / 2, H * .4f - h / 2, W / 2f + w / 2, H * .4f + h / 2);
            return scene;
        }
        void drawScene(Canvas c, float k, long now) {
            if (k <= 0) return;
            RectF r = sceneRect();
            float sc = .9f + .1f * spring(k);
            int save = c.save(); c.scale(sc, sc, r.centerX(), r.centerY());
            ArcadePreview.draw(c, arcadeId(), r, motion ? now : 1500, 24 * d, d, night, black, k, fill, stroke);
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(black ? 1.5f * d : 1.2f * d);
            stroke.setColor(alpha(ArcadePreview.accent(arcadeId()), k * (night ? .5f : .35f))); c.drawRoundRect(r, 24 * d, 24 * d, stroke);
            c.restoreToCount(save);
        }

        void drawTitleArcade(Canvas c, float nameA, float extrasA) {
            int W = getWidth(), H = getHeight();
            RectF s = sceneRect();
            float y = ArcadeCurtain.titleY(arcadeId(), W, H, d, topInset, s);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(BOLD); text.setTextSize(sp(30)); text.setColor(alpha(ink, nameA));
            if (nameA > 0) c.drawText(title(), W / 2f, y, text);
            float k = extrasA; if (k <= 0) return;
            float lift = (1 - k) * 10 * d;
            if (guide() != null) {
                drawGuide(c, y + 68 * d + lift, H - 64 * d - bottomInset * .5f - 34 * d, k);
                text.setTypeface(MEDIUM);
                text.setTextSize(sp(14)); text.setColor(alpha(soft, k * 1.6f));
                c.drawText(I18n.t("Best") + "  " + best, W / 2f, y + 24 * d + lift, text);
                text.setTextSize(sp(13)); text.setColor(alpha(soft, k * 1.4f));
                c.drawText(hint(), W / 2f, y + 44 * d + lift, text);
            } else {
            text.setTypeface(MEDIUM);
            text.setTextSize(sp(15)); text.setColor(alpha(soft, k * 1.6f));
            c.drawText(I18n.t("Best") + "  " + best, W / 2f, y + 28 * d + lift, text);
            text.setTextSize(sp(14)); text.setColor(alpha(soft, k * 1.4f));
            c.drawText(hint(), W / 2f, y + 56 * d + lift, text);
            }
            float pulse = .5f + .5f * (float) (.5 + .5 * Math.cos(SystemClock.uptimeMillis() / 300.0));
            text.setTextSize(sp(16)); text.setColor(alpha(ink, k * pulse));
            c.drawText(I18n.t("Tap to start"), W / 2f, H - 64 * d - bottomInset * .5f, text);
        }

        void drawTitle(Canvas c, float t, float fade) {
            int W = getWidth(), H = getHeight();
            float k = ease((t - 200) / 380f) * fade;
            if (k <= 0) return;
            float lift = (1 - k) * 14 * d;
            text.setTypeface(BOLD);
            text.setTextSize(sp(30)); text.setColor(alpha(ink, k));
            boolean listed = guide() != null;
            float y = (listed ? sceneRect().bottom + 42 * d : Math.max(H * .4f + introSize / 2f, sceneRect().bottom) + 50 * d) + lift;
            if (listed) drawGuide(c, y + 72 * d, H - 64 * d - bottomInset * .5f - 30 * d, k);
            c.drawText(title(), W / 2f, y, text);
            text.setTypeface(MEDIUM);
            text.setTextSize(sp(15)); text.setColor(alpha(soft, k * 1.6f));
            c.drawText(I18n.t("Best") + "  " + best, W / 2f, y + 28 * d, text);
            text.setTextSize(sp(14)); text.setColor(alpha(soft, k * 1.4f));
            c.drawText(hint(), W / 2f, y + 56 * d, text);
            float pulse = .5f + .5f * (float) (.5 + .5 * Math.cos(SystemClock.uptimeMillis() / 300.0));
            text.setTextSize(sp(16)); text.setColor(alpha(ink, k * pulse));
            c.drawText(I18n.t("Tap to start"), W / 2f, H - 64 * d - bottomInset * .5f, text);
        }

        void drawHome(Canvas c, float t) {
            for (int k = 0; k < 4; k++) {
                float p = motion ? clamp((t - k * 30f) / (HOME_MS - 90f)) : 1f, m = smooth(p);
                RectF s0 = bigAt[k], s1 = logoAt[k];
                float cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m, cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m;
                float size = s0.width() + (s1.width() - s0.width()) * m;
                float grow = state == EXIT && t < 140 ? ease(t / 140f) : 1f;
                int save = c.save(); c.rotate((k % 2 == 0 ? 1 : -1) * 180 * ease(p), cx, cy);
                float s = size * grow;
                block(c, cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, squareColour(k), p < .8f ? 1f : Math.max(0f, 1f - ease((p - .8f) / .2f)));
                c.restoreToCount(save);
            }
        }

        Bit spark(float x, float y, int colour, float speed, float size) {
            Bit b = new Bit(); double ang = rnd.nextDouble() * Math.PI * 2; float v = speed * (.4f + rnd.nextFloat() * .8f) * d;
            b.x = x; b.y = y; b.vx = (float) Math.cos(ang) * v; b.vy = (float) Math.sin(ang) * v; b.size = size * d * (.6f + rnd.nextFloat() * .7f);
            b.vr = (rnd.nextFloat() - .5f) * 720; b.colour = colour; b.decay = 1.6f + rnd.nextFloat(); b.gravity = .35f;
            bits.add(b); return b;
        }
        int ground(int colour) { return black ? mix(colour, 0xFF000000, .55f) : night ? colour : mix(colour, 0xFFFFFFFF, .74f); }
        void ambient(Canvas c, RectF area, int world, long now, float fade) {
            if (world <= 0 || world >= WORLDS.length || !motion || area.width() <= 0) return;
            int col = night ? WORLDS[world][8] : mix(WORLDS[world][8], WORLDS[world][3], .45f);
            int save = c.save(); c.clipRect(area);
            float w = area.width(), h = area.height(); double t = now / 1000.0;
            fill.setShader(null);
            for (int i = 0; i < 22; i++) {
                float a = frac(i * .61803f + .11f), b = frac(i * .3247f + .17f), sp = .6f + .8f * frac(i * .777f);
                float x, y;
                switch (world) {
                    case 1:
                        x = area.left + frac(a + t * .10 * sp) * w; y = area.top + b * h + (float) Math.sin(t * 1.3 + i) * 4 * d;
                        stroke.setStrokeWidth(Math.max(1f, 1.2f * d)); stroke.setColor(alpha(col, fade * .38f)); c.drawLine(x, y, x + 9 * d * sp, y, stroke); break;
                    case 2: {
                        x = area.left + frac(a + t * .03 * sp + .04 * Math.sin(t * .8 + i)) * w; y = area.top + frac(b + t * .06 * sp) * h;
                        int sv = c.save(); c.rotate((float) (t * 60 * sp + i * 40), x, y); fill.setColor(alpha(col, fade * .75f)); c.drawOval(rf(x - 3.2f * d, y - 1.8f * d, x + 3.2f * d, y + 1.8f * d), fill); c.restoreToCount(sv); break; }
                    case 3:
                        x = area.left + frac(a + .02 * Math.sin(t * .9 + i * 2)) * w; y = area.top + frac(b + t * .05 * sp) * h;
                        fill.setColor(alpha(col, fade * .8f)); c.drawCircle(x, y, 1.7f * d * sp, fill); break;
                    case 4: {
                        x = area.left + frac(a + .03 * Math.sin(t * .5 + i)) * w; y = area.top + frac(b + .03 * Math.cos(t * .4 + i * 1.7)) * h;
                        float g = (float) (.5 + .5 * Math.sin(t * 2 + i * 1.3));
                        fill.setColor(alpha(col, fade * .18f * g)); c.drawCircle(x, y, 5 * d, fill); fill.setColor(alpha(col, fade * (.2f + .7f * g))); c.drawCircle(x, y, 1.6f * d, fill); break; }
                    case 5: {
                        float up = frac(b + t * .07 * sp);
                        x = area.left + frac(a + .02 * Math.sin(t * 1.1 + i)) * w; y = area.top + (1 - up) * h;
                        fill.setColor(alpha(col, fade * .85f * (1 - up))); c.drawCircle(x, y, 1.9f * d * sp, fill); break; }
                    default: {
                        x = area.left + a * w; y = area.top + b * h;
                        float g = (float) (.5 + .5 * Math.sin(t * (1.2 + sp) + i * 2.1));
                        fill.setColor(alpha(col, fade * (.15f + .6f * g))); c.drawCircle(x, y, 1.3f * d * (.7f + .6f * g), fill); }
                }
            }
            c.restoreToCount(save);
        }
        void healthBar(Canvas c, float cx, float top, float w, int hp, int max, float fade) {
            if (max <= 1 || hp <= 0) return;
            float h = Math.max(3 * d, w * .12f), k = Math.max(0f, Math.min(1f, hp / (float) max));
            fill.setShader(null);
            box.set(cx - w / 2 - d, top - d, cx + w / 2 + d, top + h + d); fill.setColor(alpha(0xFF0B0E14, .78f * fade)); c.drawRoundRect(box, h, h, fill);
            box.set(cx - w / 2, top, cx - w / 2 + w * k, top + h); fill.setColor(alpha(k > .66f ? 0xFF2BD67B : k > .33f ? 0xFFF2C230 : 0xFFE53935, fade)); c.drawRoundRect(box, h / 2, h / 2, fill);
        }
        void burst(float x, float y, int colour, int n, float speed, float size) { for (int i = 0; i < n; i++) spark(x, y, i % 3 == 0 ? mix(colour, 0xFFFFFFFF, .5f) : colour, speed, size); }

        void drawBits(Canvas c, float dt) {
            float g = 1600 * d;
            for (int i = bits.size() - 1; i >= 0; i--) {
                Bit p = bits.get(i);
                p.vy += g * p.gravity * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt; p.life -= dt * p.decay;
                p.vx *= (1 - 1.5f * dt);
                if (p.life <= 0 || p.y > getHeight() + 100 * d) { bits.remove(i); continue; }
                float s = p.size * (.5f + .5f * p.life);
                if (p.round) { fill.setShader(null); fill.setColor(alpha(p.colour, Math.min(1f, p.life * 1.5f))); c.drawCircle(p.x, p.y, s / 2, fill); continue; }
                int save = c.save(); c.rotate(p.rot, p.x, p.y);
                block(c, p.x - s / 2, p.y - s / 2, p.x + s / 2, p.y + s / 2, p.colour, Math.min(1f, p.life * 1.5f));
                c.restoreToCount(save);
            }
        }

        void pop(String words, float x, float y, int colour, float size) { Pop p = new Pop(); p.text = words; p.x = x; p.y = y; p.colour = colour; p.size = size; p.at = SystemClock.uptimeMillis(); pops.add(p); }

        void drawPops(Canvas c, long now) {
            for (int i = pops.size() - 1; i >= 0; i--) {
                Pop p = pops.get(i);
                float t = (now - p.at) / 1100f;
                if (t >= 1) { pops.remove(i); continue; }
                float k = spring(Math.min(1f, t * 2.2f)), fade = 1 - easeIn(t);
                text.setTypeface(BOLD);
                text.setTextSize(sp(p.size) * (.6f + .4f * k));
                text.setColor(alpha(p.colour, fade));
                c.drawText(p.text, p.x, p.y - 26 * d * ease(t), text);
            }
        }

        void buildWords(String words) {
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG); tp.setTypeface(BOLD); tp.setTextSize(15);
            Paint.FontMetrics fm = tp.getFontMetrics();
            String[] lines = {words};
            if (tp.measureText(words) > 64 && words.trim().contains(" ")) {
                int bestAt = -1; float bestDiff = 1e9f;
                for (int k = words.indexOf(' '); k > 0; k = words.indexOf(' ', k + 1)) { float diff = Math.abs(tp.measureText(words, 0, k) - tp.measureText(words, k + 1, words.length())); if (diff < bestDiff) { bestDiff = diff; bestAt = k; } }
                if (bestAt > 0) lines = new String[]{words.substring(0, bestAt), words.substring(bestAt + 1)};
            }
            float lineH = fm.descent - fm.ascent, widest = 0;
            for (String ln : lines) widest = Math.max(widest, tp.measureText(ln));
            int bw = Math.max(1, Math.round(widest) + 4), bhh = Math.max(1, Math.round(lineH * lines.length) + 2);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(bw, bhh, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(bmp); tp.setTextAlign(Paint.Align.CENTER);
            for (int k = 0; k < lines.length; k++) bc.drawText(lines[k], bw / 2f, 1 - fm.ascent + k * lineH, tp);
            int minX = bw, maxX = -1, minY = bhh, maxY = -1;
            boolean[][] on = new boolean[bhh][bw];
            for (int y = 0; y < bhh; y++) for (int x = 0; x < bw; x++) if ((bmp.getPixel(x, y) >>> 24) > 120) { on[y][x] = true; minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
            bmp.recycle();
            cells = new ArrayList<Cell>();
            if (maxX < 0) return;
            int cols = maxX - minX + 1, rows = maxY - minY + 1;
            float size = Math.min(getWidth() * .8f / cols, 9 * d), gx = getWidth() / 2f - cols * size / 2f, gy = getHeight() * .36f - rows * size / 2f;
            wordsBottom = gy + rows * size;
            int n = Math.max(1, sources.size()), i = 0;
            for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
                if (!on[y][x]) continue;
                Cell c = new Cell();
                float[] s = sources.isEmpty() ? new float[]{getWidth() / 2f, getHeight() / 2f, LIGHT} : sources.get((i++ * 7) % n);
                c.sx = s[0] + (rnd.nextFloat() - .5f) * 10 * d; c.sy = s[1] + (rnd.nextFloat() - .5f) * 10 * d; c.x = c.sx; c.y = c.sy;
                c.colour = (int) s[2];
                c.col = x - minX;
                c.tx = gx + c.col * size + size / 2f; c.ty = gy + (y - minY) * size + size / 2f;
                c.size = size; c.delay = 500 + (c.col / (float) Math.max(1, cols)) * 380f + rnd.nextFloat() * 90f;
                cells.add(c);
            }
            for (int k = 0; k < 9; k++) { final int q = k; postDelayed(() -> PixelStackSound.play(PixelStackSound.SPARKLE, .3f, .8f + q * .12f), 760 + k * 62); }
        }
        final ArrayList<float[]> sources = new ArrayList<float[]>();
        float wordsBottom;

        void drawOverWords(Canvas c, long now, float fade) {
            if (cells == null) return;
            float t = since();
            for (Cell e : cells) {
                float p = motion ? clamp((t - e.delay) / 620f) : 1f, m = spring(p);
                float x = e.sx + (e.tx - e.sx) * m, y = e.sy + (e.ty - e.sy) * m - (float) Math.sin(Math.PI * Math.min(1f, p)) * 46 * d;
                float sz = 4 * d + (e.size * .86f - 4 * d) * Math.min(1f, m);
                if (p >= 1f && motion) y += (float) Math.sin(now / 320.0 - e.col * .35) * 1.4f * d;
                if (state == EXIT) { x = e.x; y = e.y + 30 * d * (1 - fade); sz = e.now; } else { e.x = x; e.y = y; e.now = sz; }
                if (p <= 0 && state != EXIT) continue;
                box.set(x - sz / 2, y - sz / 2, x + sz / 2, y + sz / 2); float rr = sz * .26f;
                fill.setShader(null); fill.setColor(alpha(e.colour, fade)); c.drawRoundRect(box, rr, rr, fill);
                box.set(x - sz / 2 + sz * .14f, y - sz / 2 + sz * .12f, x + sz / 2 - sz * .14f, y - sz / 2 + sz * .34f);
                fill.setColor(alpha(0x55FFFFFF, fade)); c.drawRoundRect(box, sz * .1f, sz * .1f, fill);
            }
        }

        void drawOver(Canvas c, float t, long now) {
            drawOverWords(c, now, 1f);
            int W = getWidth(), H = getHeight();
            float k = ease((t - 900) / 400f);
            float y = Math.max(H * .36f + 60 * d, wordsBottom + 34 * d);
            text.setTypeface(BOLD);
            text.setTextSize(sp(46)); text.setColor(alpha(ink2(), k));
            c.drawText(String.valueOf(score), W / 2f, y + 30 * d, text);
            text.setTypeface(MEDIUM);
            text.setTextSize(sp(15)); text.setColor(alpha(ink, k * .8f));
            c.drawText(I18n.t("Best") + "  " + best, W / 2f, y + 58 * d, text);
            if (newBest && score > 0) {
                float pop = 1 + .15f * (1 - spring((t - 1000) / 500f));
                text.setTypeface(BOLD);
                text.setTextSize(sp(22) * Math.max(.01f, pop)); text.setColor(alpha(night ? LIGHT : BLUE, k));
                c.drawText(I18n.t("New best!"), W / 2f, y + 94 * d, text);
            }
            float pulse = .55f + .45f * (float) (.5 + .5 * Math.cos(now / 300.0));
            text.setTypeface(MEDIUM);
            text.setTextSize(sp(16)); text.setColor(alpha(ink, k * pulse));
            c.drawText(I18n.t("Tap to play again"), W / 2f, H - 64 * d - bottomInset * .5f, text);
            shareBar.draw(c, now, t, y + (newBest && score > 0 ? 112 : 78) * d, score, motion, 1f);
            text.setTextAlign(Paint.Align.CENTER);
        }

        void drawScore(Canvas c, float y, float fade) {
            text.setTypeface(BOLD);
            text.setTextSize(sp(30)); text.setColor(alpha(ink2(), fade));
            c.drawText(String.valueOf(score), getWidth() / 2f, y, text);
        }

        void drawPlate(Canvas c, RectF r, float fade) {
            float rad = 18 * d;
            fill.setShader(null); fill.setColor(alpha(plate, fade)); c.drawRoundRect(r, rad, rad, fill);
            stroke.setStrokeWidth(black ? 1.5f * d : d); stroke.setColor(alpha(black ? 0x55FFFFFF : rim, fade)); c.drawRoundRect(r, rad, rad, stroke);
        }
    }
}
