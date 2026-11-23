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

    static int[] insets(android.view.WindowInsets in) {
        int[] r = {in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom()};
        try {
            Object cut = android.view.WindowInsets.class.getMethod("getDisplayCutout").invoke(in);
            if (cut != null) { Class<?> k = cut.getClass(); r[0] = Math.max(r[0], (Integer) k.getMethod("getSafeInsetLeft").invoke(cut)); r[1] = Math.max(r[1], (Integer) k.getMethod("getSafeInsetTop").invoke(cut)); r[2] = Math.max(r[2], (Integer) k.getMethod("getSafeInsetRight").invoke(cut)); r[3] = Math.max(r[3], (Integer) k.getMethod("getSafeInsetBottom").invoke(cut)); }
        } catch (Throwable ignored) { }
        return r;
    }

    static void hold(View game, Dialog dialog, boolean on) {
        int w = game.getWidth(), h = game.getHeight();
        String key = on ? w + "x" + h : "off";
        if (key.equals(game.getTag(TAG_HOLD))) return;
        if (!on && game.getTag(TAG_HOLD) == null) { game.setTag(TAG_HOLD, key); return; }
        game.setTag(TAG_HOLD, key);
        try {
            java.util.List<android.graphics.Rect> rects = new java.util.ArrayList<android.graphics.Rect>();
            if (on) {
                int edge = Math.round(64 * game.getResources().getDisplayMetrics().density);
                rects.add(new android.graphics.Rect(0, 0, edge, h)); rects.add(new android.graphics.Rect(w - edge, 0, w, h));
            }
            View.class.getMethod("setSystemGestureExclusionRects", java.util.List.class).invoke(game, rects);
        } catch (Throwable ignored) { }
        try {
            Window win = dialog == null ? null : dialog.getWindow();
            if (win != null) { WindowManager.LayoutParams lp = win.getAttributes(); lp.setTitle(on ? ArcadeSwipes.PLAYING : "PixelSuite arcade"); win.setAttributes(lp); }
        } catch (Throwable ignored) { }
    }
    private static final int TAG_HOLD = 0x7e5c0e0e;

    static Dialog window(Activity a, View content, boolean dark, final Runnable back) { return window(a, content, dark, back, false); }

    static Dialog window(Activity a, View content, boolean dark, final Runnable back, boolean bars) {
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
            int hide = bars ? 0 : 0x4 | 0x2 | 0x1000;
            w.getDecorView().setSystemUiVisibility((dark ? 0 : 0x2000 | 0x10) | 0x400 | 0x200 | 0x100 | hide);
            if (bars) try { Window.class.getMethod("setNavigationBarContrastEnforced", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
            try { Window.class.getMethod("setDecorFitsSystemWindows", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
            try { WindowManager.LayoutParams lp = w.getAttributes(); lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 3); w.setAttributes(lp); } catch (Throwable ignored) { }
        }
        d.setCancelable(true);
        return d;
    }

    static final class Bit { float x, y, vx, vy, size, rot, vr, life = 1f, decay = 1f, gravity = 1f; int colour; boolean round; }
    static final class Pop { String text; float x, y, size; long at; int colour; }
    static final class Cell { float sx, sy, tx, ty, size, x, y, now, delay; int colour, col, row; }

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
            if (has) postInvalidateOnAnimation();
            if (has || cardAt == null || !(state == PLAY || state == BUILD || state == CLEAR || state == DYING)) return;
            resumeState = state; resumeStateAt = stateAt; pausedAt = SystemClock.uptimeMillis(); pausedHere = true;
            go(PAUSED);
            GameSave.save(a, arcadeId(), this);
        }

        @Override protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            if (v == VISIBLE) postInvalidateOnAnimation();
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); postInvalidateOnAnimation(); }

        void carryOn() {
            long now = SystemClock.uptimeMillis(), by = now - pausedAt;
            long was = resumeStateAt, from = pausedAt;
            shiftClocks(this, from, by, new java.util.IdentityHashMap<Object, Boolean>(), 0);
            resumeStateAt = was; pausedAt = from;
            if (turnAt > now) turnAt = now - 10000;

            state = resumeState; stateAt = Math.min(now, was + by); last = now;
            letGo();
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

                        if (depth == 0 && GameSave.SCREEN.contains(f.getName())) continue;
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
            String ng = I18n.t("New game"); float bw = text.measureText(ng) + 36 * d, by2 = land ? Math.max(y + 86 * d, H - 58 * d - bottomInset * .5f) : H - 96 * d - bottomInset * .5f;
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

        boolean land; float leftInset, rightInset; final RectF sideL = new RectF(), sideR = new RectF(); long turnAt = -10000;
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

        void letGo() { joyId = -1; dir = -1; knobX = joyX; knobY = joyY; if (actionId != -1) { actionId = -1; action(false); } }
        void go(int s) { if (s != state) letGo(); state = s; stateAt = SystemClock.uptimeMillis(); if (s == OVER && cardAt != null) GameSave.drop(a, arcadeId()); }
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
            topInset = bottomInset = leftInset = rightInset = 0;
            try {
                android.view.WindowInsets in = getRootWindowInsets();
                if (in != null) { int[] q = insets(in); leftInset = q[0]; topInset = q[1]; rightInset = q[2]; bottomInset = q[3]; }
            } catch (Throwable ignored) { }
            boolean was = land;
            land = ArcadeCurtain.land(w, h);
            if (topInset <= 0) topInset = land ? 10 * d : 32 * d;
            if (bottomInset <= 0) bottomInset = land ? 10 * d : 24 * d;
            if (ow > 0 && oh > 0 && (w != ow || h != oh)) {
                turnAt = SystemClock.uptimeMillis(); bits.clear(); pops.clear(); letGo();
                if (was != land) PixelStackSound.play(PixelStackSound.SOFT, .22f, land ? 1.1f : .95f);
            }
            introSize = Math.min(w, h) * .42f;
            barH = land ? 0 : BAR_DP * d;
            squaresOf(home, logoAt);
            if (startAt[0] != null) for (int k = 0; k < 4; k++) logoAt[k] = new RectF(startAt[k]);
            squaresOf(centreLogo(), bigAt);
            if (cardScreen != null) { int[] at = new int[2]; getLocationOnScreen(at); cardAt = new RectF(cardScreen); cardAt.offset(-at[0], -at[1]); }
            layout(w, h);
            if (cells != null) placeWords();
        }

        float settled(long now) { return motion ? clamp((now - turnAt) / 420f) : 1f; }

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

        float guideL, guideR = -1;
        void drawGuide(Canvas c, float top, float bottom, float fade) {
            int[][] g = guide();
            if (g == null || fade <= 0 || bottom - top < 60 * d) return;
            float L = guideR > guideL ? guideL : 0, R = guideR > guideL ? guideR : getWidth();
            float W = R - L, side = 14 * d, in = 12 * d, rowH = 36 * d, padTop = 22 * d, padBot = 8 * d, gap = 14 * d, total = 0;
            for (int[] tier : g) total += padTop + ((tier.length + 1) / 2) * rowH + padBot;
            total += gap * (g.length - 1);
            float k = Math.max(.6f, Math.min(1.16f, (bottom - top) / total));
            float spare = Math.max(0, (bottom - top) - total * k), extra = Math.min(12 * d, spare / (g.length + 1));
            int save = c.save(); c.translate(L + W / 2f, top + extra); c.scale(k, k); c.translate(-W / 2f, 0);
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
            if (land) {
                int n = tray.size(); if (n == 0) return;
                float rb = Math.max(18 * d, Math.min(25 * d, (sideR.width() - 12 * d) / (n * 2f + (n - 1) * .6f))), g2 = rb * .6f, row = n * 2 * rb + (n - 1) * g2;
                float cy = Math.max(actionY - actionR - 76 * d - rb, sideR.top + 116 * d);
                if (row <= sideR.width() - 4 * d) { float x = sideR.centerX() - row / 2f + rb; for (int i = 0; i < n; i++) { trayAt[i].set(x - rb, cy - rb, x + rb, cy + rb); x += 2 * rb + g2; } }
                else { float y = cy; for (int i = 0; i < n; i++) { trayAt[i].set(sideR.centerX() - rb, y - rb, sideR.centerX() + rb, y + rb); y -= 2 * rb + 18 * d; } }
                return;
            }
            float k = total > getWidth() - 16 * d ? (getWidth() - 16 * d) / total : 1f;
            float x = getWidth() / 2f - total * k / 2, y = pad.top + 6 * d;
            for (int i = 0; i < tray.size(); i++) { trayAt[i].set(x, y, x + w[i] * k, y + h); x += (w[i] + gap) * k; }
        }

        void chipAt(int i, int n, float cx, float y, float[] out) {
            if (land) {
                int per = Math.max(1, (int) (sideR.width() / (30 * d))), row = i / per, inRow = Math.min(n - row * per, per);
                out[0] = sideR.centerX() - (inRow - 1) * 15 * d + (i % per) * 30 * d; out[1] = sideR.top + 66 * d + row * 30 * d;
                return;
            }
            out[0] = cx - (n - 1) * 15 * d + i * 30 * d; out[1] = y;
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
                if (land) { roundSlot(c, s, r, col, in, pulse, fade); continue; }
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

        void roundSlot(Canvas c, Slot s, RectF r, int col, float in, float pulse, float fade) {
            float cx = r.centerX(), cy = r.centerY(), rb = r.width() / 2;
            int save = c.save(); c.scale(in, in, cx, cy);
            fill.setShader(null);
            fill.setColor(alpha(col, fade * (.12f + .12f * pulse))); c.drawCircle(cx, cy, rb + 5 * d, fill);
            fill.setColor(alpha(black ? 0xFF000000 : night ? 0xFF1B2130 : 0xFFFFFFFF, fade)); c.drawCircle(cx, cy, rb, fill);
            fill.setColor(alpha(col, fade * (night ? .26f : .18f))); c.drawCircle(cx, cy, rb - 3.5f * d, fill);
            stroke.setStrokeWidth(2.2f * d); stroke.setColor(alpha(col, fade * (.75f + .25f * pulse))); c.drawCircle(cx, cy, rb - 1.1f * d, stroke);
            fill.setColor(alpha(0x33FFFFFF, fade)); c.drawOval(rf(cx - rb * .55f, cy - rb * .8f, cx + rb * .3f, cy - rb * .38f), fill);
            abilityIcon(c, s.kind, cx, cy, rb * .55f, fade);
            if (s.count > 1) {
                text.setTypeface(BOLD); text.setTextSize(sp(10.5f)); text.setTextAlign(Paint.Align.CENTER);
                String n = "\u00D7" + s.count; float bw = Math.max(18 * d, text.measureText(n) + 9 * d), bx = cx + rb * .72f, by = cy - rb * .74f;
                box.set(bx - bw / 2, by - 8 * d, bx + bw / 2, by + 8 * d);
                fill.setColor(alpha(black ? 0xFF000000 : page, fade)); box.inset(-1.6f * d, -1.6f * d); c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill); box.inset(1.6f * d, 1.6f * d);
                fill.setColor(alpha(col, fade)); c.drawRoundRect(box, box.height() / 2, box.height() / 2, fill);
                text.setColor(alpha(0xFFFFFFFF, fade)); c.drawText(n, bx, by + 3.8f * d, text);
            }
            text.setTypeface(BOLD); text.setTextSize(sp(10.5f)); text.setTextAlign(Paint.Align.CENTER);
            String name = abilityName(s.kind); float room = rb * 2 + rb * .55f, tw = text.measureText(name);
            if (tw > room && tw > 0) text.setTextSize(sp(10.5f) * room / tw);
            text.setColor(alpha(night ? mix(col, 0xFFFFFFFF, .55f) : mix(col, 0xFF000000, .3f), fade)); c.drawText(name, cx, cy + rb + 14 * d, text);
            c.restoreToCount(save);
        }

        boolean playTap(float x, float y) { return false; }

        void askBubble(Canvas c, float ax, float ay, float clear, String words, String note, int accent, boolean evil, float k, RectF yes, RectF no, RectF all) {
            if (k <= 0) { yes.setEmpty(); no.setEmpty(); all.setEmpty(); return; }
            float W = getWidth(), pad = 14 * d, gap = 10 * d, bh = 38 * d;
            String ys = I18n.t("Yes"), ns = I18n.t("No");
            text.setTypeface(BOLD); text.setTextSize(sp(14));
            float bw = Math.max(70 * d, Math.max(text.measureText(ys), text.measureText(ns)) + 32 * d);
            float tw = text.measureText(words);
            text.setTypeface(MEDIUM); text.setTextSize(sp(11.5f));
            float nw = note == null ? 0 : text.measureText(note);
            float w = Math.min(W - 16 * d, Math.max(Math.max(tw, nw) + pad * 2, bw * 2 + gap + pad * 2));
            float h = pad + 18 * d + (note != null ? 17 * d : 0) + 10 * d + bh + pad;
            boolean under = ay - clear - 12 * d - h < topInset + 4 * d;
            float top = under ? ay + clear + 12 * d : ay - clear - 12 * d - h;
            float cx = Math.max(8 * d + w / 2, Math.min(W - 8 * d - w / 2, ax));
            all.set(cx - w / 2, top, cx + w / 2, top + h);
            float by = top + h - pad - bh;
            yes.set(cx - gap / 2 - bw, by, cx - gap / 2, by + bh); no.set(cx + gap / 2, by, cx + gap / 2 + bw, by + bh);
            if (I18n.rtl()) { RectF t = new RectF(yes); yes.set(no); no.set(t); }
            float tipX = Math.max(all.left + 18 * d, Math.min(all.right - 18 * d, ax)), tipY = under ? ay + clear + 2 * d : ay - clear - 2 * d;
            int save = c.save(); c.scale(k, k, tipX, tipY);
            int body = evil ? 0xF21A0508 : 0xF8FFFDF6, words0 = evil ? 0xFFFFFFFF : 0xFF14161C, soft0 = evil ? 0xCCFFD6DC : 0xAA14161C;
            float rad = 18 * d;
            fill.setShader(null); fill.setColor(alpha(0x33000000, k)); box.set(all.left, all.top + 3 * d, all.right, all.bottom + 3 * d); c.drawRoundRect(box, rad, rad, fill);
            fill.setColor(alpha(accent, .22f * k)); box.set(all.left - 4 * d, all.top - 4 * d, all.right + 4 * d, all.bottom + 4 * d); c.drawRoundRect(box, rad + 4 * d, rad + 4 * d, fill);
            android.graphics.Path tail = path(); float tb = under ? all.top + 1 : all.bottom - 1;
            tail.moveTo(tipX - 11 * d, tb); tail.lineTo(tipX, tipY); tail.lineTo(tipX + 11 * d, tb); tail.close();
            fill.setColor(alpha(body, 1f)); c.drawPath(tail, fill); c.drawRoundRect(all, rad, rad, fill);
            stroke.setStrokeWidth(2 * d); stroke.setColor(alpha(accent, .9f)); c.drawRoundRect(all, rad, rad, stroke);
            text.setTypeface(BOLD); text.setTextSize(sp(14)); if (tw > w - pad * 2) text.setTextSize(sp(14) * (w - pad * 2) / tw);
            text.setTextAlign(Paint.Align.CENTER); text.setColor(words0); c.drawText(words, cx, top + pad + 14 * d, text);
            if (note != null) { text.setTypeface(MEDIUM); text.setTextSize(sp(11.5f)); if (nw > w - pad * 2) text.setTextSize(sp(11.5f) * (w - pad * 2) / nw); text.setColor(soft0); c.drawText(note, cx, top + pad + 32 * d, text); }
            long now = SystemClock.uptimeMillis(); float glow = motion ? (float) (.5 + .5 * Math.sin(now / 260.0)) : .5f;
            fill.setColor(alpha(accent, .28f + .12f * glow)); box.set(yes.left - 3 * d, yes.top - 3 * d, yes.right + 3 * d, yes.bottom + 3 * d); c.drawRoundRect(box, bh / 2 + 3 * d, bh / 2 + 3 * d, fill);
            fill.setColor(accent); c.drawRoundRect(yes, bh / 2, bh / 2, fill);
            stroke.setStrokeWidth(1.5f * d); stroke.setColor(evil ? 0x66FFFFFF : 0x44000000); c.drawRoundRect(no, bh / 2, bh / 2, stroke);
            text.setTypeface(BOLD); text.setTextSize(sp(14));
            text.setColor(0xFFFFFFFF); c.drawText(ys, yes.centerX(), yes.centerY() + 5 * d, text);
            text.setColor(words0); c.drawText(ns, no.centerX(), no.centerY() + 5 * d, text);
            c.restoreToCount(save);
        }

        static final class Hit { float x, y, vx; long at; int n, colour; boolean big; }
        final ArrayList<Hit> hits = new ArrayList<Hit>();
        final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        void hitNumber(float x, float y, int n, int colour, boolean big) {
            if (n <= 0) return;
            Hit h = new Hit(); h.x = x + (rnd.nextFloat() - .5f) * 12 * d; h.y = y; h.vx = (rnd.nextFloat() - .5f) * 36 * d; h.at = SystemClock.uptimeMillis(); h.n = n; h.colour = colour; h.big = big; hits.add(h);
            while (hits.size() > 40) hits.remove(0);
        }
        void drawHits(Canvas c, long now) {
            if (hits.isEmpty()) return;
            text.setTypeface(BOLD); text.setTextAlign(Paint.Align.CENTER);
            outline.setStyle(Paint.Style.STROKE); outline.setStrokeJoin(Paint.Join.ROUND); outline.setTypeface(BOLD); outline.setTextAlign(Paint.Align.CENTER);
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i); float t = (now - h.at) / 850f;
                if (t >= 1) { hits.remove(i); continue; } if (t < 0) continue;
                float punch = motion ? (t < .14f ? 1.55f - .55f * ease(t / .14f) : 1f) : 1f, a0 = 1 - easeIn(clamp((t - .55f) / .45f));
                float x = h.x + h.vx * ease(t), y = h.y - (motion ? 30 * d * ease(t) : 0);
                float size = sp(h.big ? 17 : 13) * punch;
                String w = String.valueOf(h.n);
                outline.setTextSize(size); outline.setStrokeWidth(Math.max(2.5f * d, size * .2f)); outline.setColor(alpha(0xE6141016, a0)); c.drawText(w, x, y, outline);
                text.setTextSize(size); text.setColor(alpha(h.colour, a0)); c.drawText(w, x, y, text);
                if (h.big && t < .3f) { text.setColor(alpha(0xFFFFFFFF, a0 * (1 - t / .3f) * .7f)); c.drawText(w, x, y, text); }
            }
        }

        static final int LV_PLAIN = 0, LV_LIGHT = 1, LV_DARK = 2;
        static final long LV_MS = 1600;
        long lvAt = -100000; int lvKind, lvNextKind = -1; String lvWords, lvSub, lvNextWords, lvNextSub;
        void levelUp(int kind, String words, String sub) {
            long now = SystemClock.uptimeMillis();
            if (lvWords != null && now - lvAt < 1100) { lvNextKind = kind; lvNextWords = words; lvNextSub = sub; return; }
            startLevelUp(kind, words, sub, now);
        }
        void startLevelUp(int kind, String words, String sub, long now) {
            lvAt = now; lvKind = kind; lvWords = words; lvSub = sub;
            int[] notes = kind == LV_LIGHT ? new int[]{7, 11, 14, 19} : kind == LV_DARK ? new int[]{-12, -9, -5, -6} : new int[]{0, 4, 7, 12};
            for (int i = 0; i < notes.length; i++) { final int nt = notes[i]; final float v = kind == LV_DARK ? .5f : .42f; postDelayed(() -> PixelStackSound.note(nt, v), i * (kind == LV_DARK ? 110 : 80)); }
            if (kind == LV_LIGHT) { PixelStackSound.play(PixelStackSound.SPARKLE, .45f, 1.3f); postDelayed(() -> PixelStackSound.play(PixelStackSound.TING, .4f, 1.6f), 330); }
            else if (kind == LV_DARK) { PixelStackSound.play(PixelStackSound.BOOM, .45f, .5f); postDelayed(() -> PixelStackSound.play(PixelStackSound.FIRE, .4f, .7f), 330); }
            else postDelayed(() -> PixelStackSound.play(PixelStackSound.PERFECT, .45f, 1.1f), 330);
            thump(a, .7f);
        }
        static boolean perLetter(String w) { for (int i = 0; i < w.length(); i++) { char ch = w.charAt(i); if (ch >= 0x0590 && ch <= 0x1CFF) return false; } return true; }
        void drawLevelUp(Canvas c, float x, float y, long now) {
            if (lvWords == null) return;
            float t = (now - lvAt) / (float) LV_MS;
            if (t >= 1) { if (lvNextKind >= 0) { startLevelUp(lvNextKind, lvNextWords, lvNextSub, now); lvNextKind = -1; t = 0; } else return; }
            if (t < 0) return;
            float W = getWidth(), out = t > .8f ? 1 - ease((t - .8f) / .2f) : 1f, rise = motion ? ease(Math.min(1f, t * 1.8f)) * 16 * d + t * 10 * d : 10 * d;
            boolean light = lvKind == LV_LIGHT, dark = lvKind == LV_DARK;
            String w = lvWords.toUpperCase(java.util.Locale.getDefault());
            float size = sp(dark || light ? 21 : 23);
            text.setTypeface(BOLD); text.setTextSize(size); text.setTextAlign(Paint.Align.CENTER);
            outline.setStyle(Paint.Style.STROKE); outline.setStrokeJoin(Paint.Join.ROUND); outline.setTypeface(BOLD); outline.setTextAlign(Paint.Align.CENTER); outline.setTextSize(size); outline.setStrokeWidth(4.5f * d);
            float tw = text.measureText(w), cx = Math.max(tw / 2 + 30 * d, Math.min(W - tw / 2 - 30 * d, x)), by = Math.max(topInset + size + 20 * d, y - 22 * d - rise);
            int body = light ? 0xFFFFD23F : dark ? 0xFFFF3B4D : 0xFFFFFFFF, edge = light ? 0xFF7A5200 : dark ? 0xFF1A0508 : 0xFF1846B8;
            float appear = motion ? spring(Math.min(1f, t * 1600f / 300f)) : 1f;
            fill.setShader(null);

            if (light) {
                int sv = c.save(); c.rotate(now / 25f % 360, cx, by - size * .35f); fill.setColor(alpha(0xFFFFE9A8, .35f * out * appear));
                for (int i = 0; i < 12; i++) { android.graphics.Path ray = path(); ray.moveTo(cx - 6 * d, by - size * .35f); ray.lineTo(cx, by - size * .35f - (tw * .5f + 26 * d) * (i % 2 == 0 ? 1f : .7f)); ray.lineTo(cx + 6 * d, by - size * .35f); ray.close(); c.drawPath(ray, fill); c.rotate(30, cx, by - size * .35f); }
                c.restoreToCount(sv);
                fill.setColor(alpha(0xFFFFFFFF, out * appear * .95f));
                for (int side = -1; side <= 1; side += 2) { float wx = cx + side * (tw / 2 + 6 * d), wy = by - size * .4f, flap = motion ? (float) Math.sin(now / 120.0) * 3 * d : 0; android.graphics.Path wg = path(); wg.moveTo(wx, wy); wg.quadTo(wx + side * 26 * d, wy - 22 * d - flap, wx + side * 30 * d, wy - 2 * d); wg.quadTo(wx + side * 18 * d, wy + 10 * d + flap, wx, wy + 6 * d); wg.close(); c.drawPath(wg, fill); }
            } else if (dark) {
                fill.setColor(alpha(0xFF1A0508, .55f * out * appear)); c.drawOval(rf(cx - tw / 2 - 22 * d, by - size * 1.15f, cx + tw / 2 + 22 * d, by + size * .45f), fill);
                for (int i = 0; i < 9; i++) { float fx = cx - tw / 2 + tw * i / 8f, fh = size * (.45f + .2f * (float) Math.sin(now / 60.0 + i * 1.7)); android.graphics.Path f = path(); f.moveTo(fx - 7 * d, by + 4 * d); f.quadTo(fx, by + 4 * d - fh, fx + 7 * d, by + 4 * d); f.close(); fill.setColor(alpha(i % 2 == 0 ? 0xFFE5532B : 0xFFFFB347, .85f * out * appear)); c.drawPath(f, fill); }
                fill.setColor(alpha(0xFF2A0508, out * appear));
                for (int side = -1; side <= 1; side += 2) { float hx0 = cx + side * (tw / 2 + 4 * d), hy0 = by - size * .55f; android.graphics.Path h = path(); h.moveTo(hx0, hy0 + 6 * d); h.quadTo(hx0 + side * 14 * d, hy0 - 4 * d, hx0 + side * 12 * d, hy0 - 20 * d); h.quadTo(hx0 + side * 6 * d, hy0 - 6 * d, hx0 - side * 4 * d, hy0); h.close(); c.drawPath(h, fill); }
            } else {
                fill.setColor(alpha(0xFF1846B8, .18f * out * appear)); c.drawOval(rf(cx - tw / 2 - 18 * d, by - size * 1.1f, cx + tw / 2 + 18 * d, by + size * .4f), fill);
                for (int i = 0; i < 6; i++) { double ang = i * Math.PI / 3 + now / 500.0; float rr = (tw / 2 + 14 * d) * (.9f + .1f * (float) Math.sin(now / 150.0 + i)), sx = cx + (float) Math.cos(ang) * rr, sy = by - size * .35f + (float) Math.sin(ang) * size * .9f, q = 3.2f * d * (.6f + .4f * (float) Math.sin(now / 90.0 + i * 2)); fill.setColor(alpha(0xFFFFE066, out * appear)); android.graphics.Path st = path(); st.moveTo(sx, sy - q * 2); st.lineTo(sx + q * .5f, sy - q * .5f); st.lineTo(sx + q * 2, sy); st.lineTo(sx + q * .5f, sy + q * .5f); st.lineTo(sx, sy + q * 2); st.lineTo(sx - q * .5f, sy + q * .5f); st.lineTo(sx - q * 2, sy); st.lineTo(sx - q * .5f, sy - q * .5f); st.close(); c.drawPath(st, fill); }
            }

            float ms = now - lvAt;
            if (perLetter(w)) {
                float lx = cx - tw / 2;
                for (int i = 0; i < w.length(); i++) {
                    String ch = String.valueOf(w.charAt(i)); float cw = text.measureText(ch), lt = (ms - i * 34) / 280f;
                    if (lt <= 0) { lx += cw; continue; }
                    float jump = motion ? (1 - spring(Math.min(1f, lt))) * 18 * d : 0, wave = motion && lt > 1 ? (float) Math.sin(ms / 130.0 - i * .7) * 2.2f * d : 0;
                    float jx = dark && motion ? (rnd.nextFloat() - .5f) * 1.6f * d : 0, sc = motion ? .4f + .6f * spring(Math.min(1f, lt)) : 1f;
                    float lcx = lx + cw / 2 + jx, ly = by + jump - wave;
                    int sv = c.save(); c.scale(sc, sc, lcx, ly - size * .35f);
                    outline.setColor(alpha(edge, out)); c.drawText(ch, lcx, ly, outline);
                    text.setColor(alpha(body, out)); c.drawText(ch, lcx, ly, text);
                    if (light || !dark) { text.setColor(alpha(0xFFFFFFFF, out * .55f * Math.max(0f, 1 - lt))); c.drawText(ch, lcx, ly, text); }
                    c.restoreToCount(sv);
                    lx += cw;
                }
            } else {
                float sc = motion ? .4f + .6f * spring(Math.min(1f, ms / 320f)) : 1f, jx = dark && motion ? (rnd.nextFloat() - .5f) * 1.6f * d : 0;
                int sv = c.save(); c.scale(sc, sc, cx, by - size * .35f);
                outline.setColor(alpha(edge, out)); c.drawText(w, cx + jx, by, outline);
                text.setColor(alpha(body, out)); c.drawText(w, cx + jx, by, text);
                c.restoreToCount(sv);
            }
            if (lvSub != null && ms > 220) {
                float st2 = motion ? ease(Math.min(1f, (ms - 220) / 260f)) : 1f, ssz = sp(12.5f);
                text.setTextSize(ssz); outline.setTextSize(ssz); outline.setStrokeWidth(3.2f * d);
                float sy = by + ssz + 6 * d + (1 - st2) * 6 * d;
                outline.setColor(alpha(edge, out * st2)); c.drawText(lvSub, cx, sy, outline);
                text.setColor(alpha(light ? 0xFFFFFFFF : dark ? 0xFFFFB3BC : 0xFFFFE066, out * st2)); c.drawText(lvSub, cx, sy, text);
            }
            text.setTextAlign(Paint.Align.CENTER);
        }

        static final int WARD_PLAIN = 0, WARD_HOLY = 1, WARD_HELL = 2, WARD_STORM = 3, WARD_BLAZE = 4;
        static int wardColour(int style) { return style == WARD_HOLY ? 0xFFFFD23F : style == WARD_HELL ? 0xFFE5173F : style == WARD_STORM ? 0xFF8FE8FF : style == WARD_BLAZE ? 0xFFFF7A3D : 0xFF7FE9FF; }
        final android.graphics.Path wardPath = new android.graphics.Path();
        void ward(Canvas c, float cx, float cy, float R, int style, float k, long now, float fade, float sw) {
            int col = wardColour(style);
            fill.setShader(null); stroke.setStrokeWidth(sw);
            if (k < 1f) {
                stroke.setColor(alpha(col, fade * .35f)); c.drawArc(rf(cx - R, cy - R, cx + R, cy + R), -90, 360 * k, false, stroke);
                if (style != WARD_PLAIN && k > .02f) { double ang = Math.toRadians(-90 + 360 * k); fill.setColor(alpha(col, fade * .8f)); c.drawCircle(cx + (float) Math.cos(ang) * R, cy + (float) Math.sin(ang) * R, sw * 1.3f, fill); }
                return;
            }
            float q = motion ? (float) (.5 + .5 * Math.sin(now / 300.0)) : .5f;
            if (style == WARD_HELL) { fill.setColor(alpha(0xFF8E0D22, fade * (.1f + .05f * q))); c.drawCircle(cx, cy, R, fill); }
            else { fill.setColor(alpha(col, fade * (.08f + .05f * q))); c.drawCircle(cx, cy, R, fill); }
            if (style == WARD_HELL || style == WARD_BLAZE) {
                int n = 12; float t = motion ? now / 1000f : 0;
                for (int i = 0; i < n; i++) {
                    double ang = i * Math.PI * 2 / n + t * (style == WARD_HELL ? -.6 : .6);
                    float len = R * (.16f + .09f * (float) Math.sin(t * 9 + i * 2.3f)), wd = R * .11f;
                    float bx = cx + (float) Math.cos(ang) * R * .96f, by = cy + (float) Math.sin(ang) * R * .96f, ux = (float) Math.cos(ang), uy = (float) Math.sin(ang);
                    wardPath.rewind(); wardPath.moveTo(bx - uy * wd, by + ux * wd); wardPath.quadTo(bx + ux * len * .5f - uy * wd * .2f, by + uy * len * .5f + ux * wd * .2f, bx + ux * len, by + uy * len); wardPath.quadTo(bx + ux * len * .5f + uy * wd * .2f, by + uy * len * .5f - ux * wd * .2f, bx + uy * wd, by - ux * wd); wardPath.close();
                    fill.setColor(alpha(i % 2 == 0 ? (style == WARD_HELL ? 0xFFB3122E : 0xFFFF5A1F) : (style == WARD_HELL ? 0xFFFF5A3D : 0xFFFFB347), fade * (.7f + .2f * q))); c.drawPath(wardPath, fill);
                }
                if (style == WARD_BLAZE && motion && rnd.nextFloat() < .2f) { double ang = rnd.nextDouble() * Math.PI * 2; Bit b = spark(cx + (float) Math.cos(ang) * R, cy + (float) Math.sin(ang) * R, rnd.nextBoolean() ? 0xFFFFB347 : 0xFFFF7A3D, 40, 2.4f); b.round = true; b.gravity = -.3f; b.decay = 1.8f; }
            }
            stroke.setStrokeWidth(sw * (style == WARD_PLAIN ? 1f : 1.25f)); stroke.setColor(alpha(col, fade * (.6f + .3f * q))); c.drawCircle(cx, cy, R, stroke);
            if (style == WARD_HELL) { stroke.setStrokeWidth(sw * .8f); stroke.setColor(alpha(0xFF2A0508, fade * .55f)); c.drawCircle(cx, cy, R * .88f, stroke); }
            if (style == WARD_HOLY || style == WARD_STORM) { stroke.setStrokeWidth(sw * .6f); stroke.setColor(alpha(0xFFFFFFFF, fade * (.35f + .25f * q))); c.drawCircle(cx, cy, R * .88f, stroke); }
            if (style == WARD_HOLY) {
                float turn = motion ? now / 1800f : 0;
                for (int i = 0; i < 6; i++) {
                    double ang = turn + i * Math.PI / 3; float sx = cx + (float) Math.cos(ang) * R, sy = cy + (float) Math.sin(ang) * R, a = R * .13f * (.8f + .2f * (float) Math.sin(now / 200.0 + i));
                    wardPath.rewind(); wardPath.moveTo(sx, sy - a); wardPath.lineTo(sx + a * .28f, sy - a * .28f); wardPath.lineTo(sx + a, sy); wardPath.lineTo(sx + a * .28f, sy + a * .28f); wardPath.lineTo(sx, sy + a); wardPath.lineTo(sx - a * .28f, sy + a * .28f); wardPath.lineTo(sx - a, sy); wardPath.lineTo(sx - a * .28f, sy - a * .28f); wardPath.close();
                    fill.setColor(alpha(0xFFFFF6D0, fade)); c.drawPath(wardPath, fill);
                }
                if (motion) { float g0 = (now / 6f) % 360; stroke.setStrokeWidth(sw * 1.6f); stroke.setColor(alpha(0xFFFFFFFF, fade * .55f)); c.drawArc(rf(cx - R, cy - R, cx + R, cy + R), g0, 34, false, stroke); }
            }
            if (style == WARD_STORM && motion) for (int i = 0; i < 3; i++) {
                double a0 = (now / 700.0) + i * Math.PI * 2 / 3, a1 = a0 + .9;
                bolt(c, cx + (float) Math.cos(a0) * R, cy + (float) Math.sin(a0) * R, cx + (float) Math.cos(a1) * R, cy + (float) Math.sin(a1) * R, R * .14f, 4, now / 45 + i * 17, 0xFF7FE9FF, Math.max(1f, sw * .5f), fade * .85f);
            }
        }

        void meter(Canvas c, float l, float y, float r, float k, int colour, float fade) {
            if (r - l < 4 * d) return;
            float h = 5 * d;
            box.set(l, y - h / 2, r, y + h / 2); fill.setShader(null); fill.setColor(alpha(night ? 0x2EFFFFFF : 0x1F000000, fade)); c.drawRoundRect(box, h / 2, h / 2, fill);
            float e = l + (r - l) * clamp(k);
            if (e > l + 1) { box.set(l, y - h / 2, e, y + h / 2); fill.setColor(alpha(colour, fade)); c.drawRoundRect(box, h / 2, h / 2, fill); fill.setColor(alpha(0x66FFFFFF, fade)); box.set(l + 1.5f * d, y - h / 2 + 1 * d, Math.max(l + 1.5f * d, e - 1.5f * d), y - h / 2 + 2.2f * d); c.drawRoundRect(box, d, d, fill); }
        }

        void notches(Canvas c, float l, float y, float r, int steps, float fade) {
            if (steps <= 1 || r - l < 4 * d) return;
            float h = 5 * d; fill.setShader(null); fill.setColor(alpha(black ? 0xFF000000 : page, fade));
            for (int i = 1; i < steps; i++) { float x = l + (r - l) * i / steps; c.drawRect(x - .8f * d, y - h / 2 - .5f * d, x + .8f * d, y + h / 2 + .5f * d, fill); }
        }

        void badge(Canvas c, float x, float y, String n, int colour, float fade) {
            fill.setShader(null); fill.setColor(alpha(colour, fade)); c.drawCircle(x, y, 8 * d, fill);
            text.setTypeface(BOLD); text.setTextSize(sp(n.length() > 1 ? 9 : 10)); text.setTextAlign(Paint.Align.CENTER); text.setColor(alpha(0xFFFFFFFF, fade));
            c.drawText(n, x, y + 3.4f * d, text);
        }

        final android.graphics.Path boltPath = new android.graphics.Path(); final java.util.Random boltRnd = new java.util.Random();
        void bolt(Canvas c, float x0, float y0, float x1, float y1, float jag, int segs, long seed, int glow, float width, float fade) {
            if (fade <= 0) return;
            boltRnd.setSeed(seed);
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy); if (len < 1) return;
            float nx = -dy / len, ny = dx / len;
            boltPath.rewind(); boltPath.moveTo(x0, y0);
            float[] mx = new float[segs + 1], my = new float[segs + 1]; mx[0] = x0; my[0] = y0;
            for (int i = 1; i < segs; i++) { float t = i / (float) segs, o = (boltRnd.nextFloat() - .5f) * 2 * jag * (float) Math.sin(Math.PI * t); mx[i] = x0 + dx * t + nx * o; my[i] = y0 + dy * t + ny * o; boltPath.lineTo(mx[i], my[i]); }
            mx[segs] = x1; my[segs] = y1; boltPath.lineTo(x1, y1);
            for (int f = 0; f < 2; f++) {
                int at = 1 + boltRnd.nextInt(Math.max(1, segs - 2)); float side = boltRnd.nextBoolean() ? 1 : -1, fl = len * (.12f + boltRnd.nextFloat() * .14f);
                float fx = mx[at] + (dx / len * .6f + nx * side * .8f) * fl, fy = my[at] + (dy / len * .6f + ny * side * .8f) * fl;
                boltPath.moveTo(mx[at], my[at]); boltPath.lineTo((mx[at] + fx) / 2 + nx * side * jag * .3f, (my[at] + fy) / 2 + ny * side * jag * .3f); boltPath.lineTo(fx, fy);
            }
            stroke.setStrokeWidth(width * 3.2f); stroke.setColor(alpha(glow, .28f * fade)); c.drawPath(boltPath, stroke);
            stroke.setStrokeWidth(width * 1.6f); stroke.setColor(alpha(glow, .85f * fade)); c.drawPath(boltPath, stroke);
            stroke.setStrokeWidth(width * .6f); stroke.setColor(alpha(0xFFFFFFFF, fade)); c.drawPath(boltPath, stroke);
        }

        void placeControls(float top) {
            int W = getWidth(), H = getHeight();
            sideL.setEmpty(); sideR.setEmpty();
            pad.set(0, top, W, H - bottomInset);
            actionR = Math.min(46 * d, pad.height() * .3f);
            actionX = W - actionR - 30 * d; actionY = pad.centerY();
            joyX = Math.min(W * .27f, 110 * d); joyY = pad.centerY(); knobX = joyX; knobY = joyY;
        }

        void placeControlsLand(RectF field) {
            int W = getWidth(), H = getHeight();
            pad.set(0, 0, W, H);
            sideL.set(leftInset + 8 * d, topInset + 6 * d, Math.max(leftInset + 60 * d, field.left - 12 * d), H - bottomInset - 6 * d);
            sideR.set(Math.min(W - rightInset - 60 * d, field.right + 12 * d), topInset + 16 * d + BAR_DP * d, W - rightInset - 8 * d, H - bottomInset - 6 * d);
            actionR = Math.max(30 * d, Math.min(46 * d, sideR.width() * .3f));
            actionX = sideR.centerX(); actionY = sideR.bottom - actionR - Math.max(16 * d, sideR.height() * .1f);
            joyX = sideL.centerX(); joyY = sideL.bottom - 50 * d - Math.max(12 * d, sideL.height() * .08f); knobX = joyX; knobY = joyY;
        }

        boolean actionSide(float x) { return x >= getWidth() / 2f; }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            if (barTouch(e)) return true;
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
                    if (playTap(x, y)) break;
                    if (trayTap(x, y)) break;
                    if (onField(x, y)) break;

                    boolean onButton = land ? actionSide(x) : Math.hypot(x - actionX, y - actionY) < actionR + 30 * d;
                    if (onButton ? actionId == -1 : joyId != -1 && actionId == -1) { actionId = id; actionAt = SystemClock.uptimeMillis(); action(true); tick(a, .35f); }
                    else if (joyId == -1 && !onButton) { joyId = id; joyX = x; joyY = land ? Math.max(pad.top + 50 * d, Math.min(getHeight() - 50 * d, y)) : Math.max(pad.top + (tray.isEmpty() ? 40 : 76) * d, y); knobX = x; knobY = y; dir = -1; }
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    if (joyId == -1) for (int i = 0; i < e.getPointerCount(); i++) {
                        int id = e.getPointerId(i); float x = e.getX(i), y = e.getY(i);
                        if (id == actionId || id == btnPtr || y < pad.top || onField(x, y) || (land ? actionSide(x) : Math.hypot(x - actionX, y - actionY) < actionR + 30 * d)) continue;
                        joyId = id; joyX = x; joyY = y; knobX = x; knobY = y; dir = -1; break;
                    }
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

        boolean onField(float x, float y) { return land ? x > sideL.right && x < sideR.left : y < pad.top; }

        static final float BAR_DP = 44;
        float barH, barK, iconFrom, iconTo; long iconAt = -10000, btnAt = -10000; int btnDown, btnPtr = -1, btnFired;
        final RectF pauseAt = new RectF(), exitAt = new RectF();
        final android.graphics.Path iconPath = new android.graphics.Path();
        boolean barShows() { return state == PLAY || state == DYING || state == BUILD || state == CLEAR || state == PAUSED; }
        void layButtons() {
            float r = 18 * d, gap = 10 * d, cy, right;

            if (land) { cy = topInset + 18 * d + r; right = (sideR.width() > 0 ? sideR.right : getWidth() - rightInset - 8 * d) - 10 * d; }
            else { cy = topInset + barH / 2f; right = getWidth() - rightInset - 14 * d; }
            exitAt.set(right - 2 * r, cy - r, right, cy + r);
            pauseAt.set(exitAt.left - gap - 2 * r, cy - r, exitAt.left - gap, cy + r);
        }
        boolean onBtn(RectF r, float x, float y, float slop) { return x > r.left - slop && x < r.right + slop && y > r.top - slop && y < r.bottom + slop; }
        boolean barTouch(MotionEvent e) {
            int act = e.getActionMasked(), idx = e.getActionIndex();
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
                if (act == MotionEvent.ACTION_DOWN) { btnDown = 0; btnPtr = -1; }
                if (!barShows() || barK < .5f || btnPtr != -1) return false;
                float x = e.getX(idx), y = e.getY(idx); layButtons();
                int which = onBtn(pauseAt, x, y, 6 * d) ? 1 : onBtn(exitAt, x, y, 6 * d) ? 2 : 0;
                if (which == 0) return false;
                btnDown = which; btnPtr = e.getPointerId(idx); tick(a, .3f);
                return true;
            }
            if (btnPtr == -1) return false;
            if (act == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < e.getPointerCount(); i++) if (e.getPointerId(i) == btnPtr && btnDown != 0 && !onBtn(btnDown == 1 ? pauseAt : exitAt, e.getX(i), e.getY(i), 18 * d)) btnDown = 0;
                return false;
            }
            if (act == MotionEvent.ACTION_CANCEL) { btnDown = 0; btnPtr = -1; return false; }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
                if (e.getPointerId(idx) != btnPtr) return false;
                int which = btnDown; btnDown = 0; btnPtr = -1;
                if (which == 0 || !barShows()) return true;
                btnFired = which; btnAt = SystemClock.uptimeMillis();
                if (which == 2) exit();
                else if (state == PAUSED) carryOn();
                else pauseGame();
                return true;
            }
            return false;
        }
        void pauseGame() {
            if (!(state == PLAY || state == BUILD || state == CLEAR || state == DYING)) return;
            resumeState = state; resumeStateAt = stateAt; pausedAt = SystemClock.uptimeMillis(); pausedHere = true;
            go(PAUSED);
            if (cardAt != null) GameSave.save(a, arcadeId(), this);
            thump(a, .5f); PixelStackSound.play(PixelStackSound.SOFT, .32f, .9f);
        }

        float playK(long now) {
            float to = state == PAUSED || (state == EXIT && exitFrom == PAUSED) ? 1f : 0f;
            float cur = iconFrom + (iconTo - iconFrom) * (motion ? ease(clamp((now - iconAt) / 380f)) : 1f);
            if (to != iconTo) { iconFrom = cur; iconTo = to; iconAt = now; }
            return iconFrom + (iconTo - iconFrom) * (motion ? ease(clamp((now - iconAt) / 380f)) : 1f);
        }
        void drawBar(Canvas c, float dt, long now) {
            boolean shows = barShows() || (state == EXIT && (exitFrom == PLAY || exitFrom == DYING || exitFrom == BUILD || exitFrom == CLEAR || exitFrom == PAUSED) && since() < 160);
            float want = shows ? 1f : 0f;
            barK = motion ? barK + (want - barK) * Math.min(1f, dt * 9f) : want;
            if (barK < .01f) { barK = want; return; }
            layButtons();
            float k = playK(now), fade = barK * ease(settled(now));
            int accent = night ? 0xFF2C5BD6 : BLUE, face = black ? 0xFF000000 : night ? 0xFF1B2130 : 0xFFFFFFFF;

            float cx = pauseAt.centerX(), cy = pauseAt.centerY(), r = pauseAt.width() / 2f;
            float press = btnDown == 1 ? .9f : 1f, pop = motion && now - iconAt < 420 ? 1 + .14f * (float) Math.sin(Math.PI * clamp((now - iconAt) / 420f)) : 1f;
            int save = c.save(); c.scale(press * pop, press * pop, cx, cy);
            if (k > 0) { float pulse = motion ? (float) (.5 + .5 * Math.sin(now / 320.0)) : .5f; fill.setShader(null); fill.setColor(alpha(accent, fade * k * (.16f + .14f * pulse))); c.drawCircle(cx, cy, r + (5 + 3 * pulse) * d, fill); }
            fill.setShader(null); fill.setColor(alpha(mix(face, accent, k), fade * (night ? .92f : .96f))); c.drawCircle(cx, cy, r, fill);
            stroke.setStrokeWidth(1.3f * d); stroke.setColor(alpha(black ? 0x66FFFFFF : night ? 0x33FFFFFF : 0x22000000, fade * (1 - k))); c.drawCircle(cx, cy, r, stroke);
            drawPlayPause(c, cx, cy, r * .42f, k, alpha(mix(ink2(), 0xFFFFFFFF, k), fade));
            c.restoreToCount(save);
            if (motion && now - iconAt < 520 && iconAt > 0) { float q = ease((now - iconAt) / 520f); stroke.setStrokeWidth(3 * d * (1 - q)); stroke.setColor(alpha(iconTo > .5f ? accent : (night ? LIGHT : BLUE), fade * (1 - q) * .8f)); c.drawCircle(cx, cy, r * (1 + .9f * q), stroke); }

            cx = exitAt.centerX(); cy = exitAt.centerY(); press = btnDown == 2 ? .9f : 1f;
            save = c.save(); c.scale(press, press, cx, cy);
            fill.setColor(alpha(face, fade * (night ? .92f : .96f))); c.drawCircle(cx, cy, r, fill);
            stroke.setStrokeWidth(1.3f * d); stroke.setColor(alpha(black ? 0x66FFFFFF : night ? 0x33FFFFFF : 0x22000000, fade)); c.drawCircle(cx, cy, r, stroke);
            drawExitIcon(c, cx, cy, r * .42f, alpha(ink2(), fade), now);
            c.restoreToCount(save);
            if (btnFired == 2 && now - btnAt < 420) { float q = ease((now - btnAt) / 420f); stroke.setStrokeWidth(3 * d * (1 - q)); stroke.setColor(alpha(night ? LIGHT : BLUE, fade * (1 - q) * .8f)); c.drawCircle(cx, cy, r * (1 + .9f * q), stroke); }
        }

        void drawPlayPause(Canvas c, float cx, float cy, float u, float k, int colour) {
            float[][] bars = {{-u, -.84f * u, u, -.84f * u, u, -.2f * u, -u, -.2f * u}, {-u, .2f * u, u, .2f * u, u, .84f * u, -u, .84f * u}};
            float[][] tri = {{-.72f * u, -u, 1.02f * u, 0, 1.02f * u, 0, -.72f * u, 0}, {-.72f * u, 0, 1.02f * u, 0, 1.02f * u, 0, -.72f * u, u}};
            int save = c.save(); c.translate(cx + .1f * u * k, cy); c.rotate(90 * (1 - k));
            fill.setShader(null); fill.setColor(colour);
            for (int b = 0; b < 2; b++) {
                iconPath.rewind();
                for (int v = 0; v < 4; v++) { float x = bars[b][v * 2] + (tri[b][v * 2] - bars[b][v * 2]) * k, y = bars[b][v * 2 + 1] + (tri[b][v * 2 + 1] - bars[b][v * 2 + 1]) * k; if (v == 0) iconPath.moveTo(x, y); else iconPath.lineTo(x, y); }
                iconPath.close(); c.drawPath(iconPath, fill);
            }
            if (k > .02f && k < .98f) { stroke.setStrokeWidth(1.2f * d); stroke.setColor(colour); for (int b = 0; b < 2; b++) { iconPath.rewind(); for (int v = 0; v < 4; v++) { float x = bars[b][v * 2] + (tri[b][v * 2] - bars[b][v * 2]) * k, y = bars[b][v * 2 + 1] + (tri[b][v * 2 + 1] - bars[b][v * 2 + 1]) * k; if (v == 0) iconPath.moveTo(x, y); else iconPath.lineTo(x, y); } iconPath.close(); c.drawPath(iconPath, stroke); } }
            c.restoreToCount(save);
        }

        void drawExitIcon(Canvas c, float cx, float cy, float u, int colour, long now) {
            float out = btnDown == 2 ? .25f * u : 0; cx -= .1f * u;
            stroke.setStrokeWidth(2 * d); stroke.setColor(colour); stroke.setStyle(Paint.Style.STROKE);
            iconPath.rewind(); iconPath.moveTo(cx + .15f * u, cy - u); iconPath.lineTo(cx - .85f * u, cy - u); iconPath.lineTo(cx - .85f * u, cy + u); iconPath.lineTo(cx + .15f * u, cy + u); c.drawPath(iconPath, stroke);
            float ax = cx + out; c.drawLine(ax - .3f * u, cy, ax + 1.05f * u, cy, stroke);
            iconPath.rewind(); iconPath.moveTo(ax + .6f * u, cy - .45f * u); iconPath.lineTo(ax + 1.05f * u, cy); iconPath.lineTo(ax + .6f * u, cy + .45f * u); c.drawPath(iconPath, stroke);
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
            hold(this, dialog, land && (state == PLAY || state == BUILD || state == CLEAR || state == DYING));
            float t = since();
            if (t < 0) { stateAt = now; t = 0; }
            if (!firstFrame) { firstFrame = true; if (state == INTRO) { stateAt = now; t = 0; } }
            boolean arcade = cardAt != null;
            float shown = arcade ? 1f : state == INTRO ? ease(t / 320f) : state == EXIT ? 1 - ease((t - 80) / 420f) : 1f;
            fill.setShader(null); fill.setColor(alpha(black ? 0xFF000000 : page, shown)); c.drawRect(0, 0, W, H, fill);
            int save = c.save();
            if (motion && now - shakeAt >= 0 && now - shakeAt < 380) { float k = 1 - (now - shakeAt) / 380f; c.translate((rnd.nextFloat() - .5f) * shakeHard * d * k, (rnd.nextFloat() - .5f) * shakeHard * d * k); }
            float turned = settled(now);
            if (turned < 1) { float sc = .94f + .06f * spring(turned); c.scale(sc, sc, W / 2f, H / 2f); }

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
            float turnedIn = ease(settled(now));
            if (state == PLAY || state == DYING) drawControls(c, turnedIn);
            if (state == BUILD) drawControls(c, Math.min(turnedIn, ease((t - 300) / 500f)));
            drawBar(c, dt, now);
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
            float y = ArcadeCurtain.titleY(arcadeId(), W, H, d, topInset, s), tx = ArcadeCurtain.titleX(W, H, s);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(BOLD); text.setTextSize(sp(30)); text.setColor(alpha(ink, nameA));
            if (nameA > 0) c.drawText(title(), tx, y, text);
            float k = extrasA; if (k <= 0) return;
            float lift = (1 - k) * 10 * d;
            if (guide() != null) {
                if (land) { guideL = W * .5f + 4 * d; guideR = W - rightInset - 8 * d; drawGuide(c, topInset + 18 * d + lift, H - 12 * d - bottomInset * .5f, k); }
                else { guideL = 0; guideR = -1; drawGuide(c, y + 68 * d + lift, H - 64 * d - bottomInset * .5f - 34 * d, k); }
                text.setTypeface(MEDIUM);
                text.setTextSize(sp(14)); text.setColor(alpha(soft, k * 1.6f));
                c.drawText(I18n.t("Best") + "  " + best, tx, y + 24 * d + lift, text);
                text.setTextSize(sp(13)); text.setColor(alpha(soft, k * 1.4f));
                c.drawText(hint(), tx, y + 44 * d + lift, text);
            } else {
            text.setTypeface(MEDIUM);
            text.setTextSize(sp(15)); text.setColor(alpha(soft, k * 1.6f));
            c.drawText(I18n.t("Best") + "  " + best, tx, y + 28 * d + lift, text);
            text.setTextSize(sp(14)); text.setColor(alpha(soft, k * 1.4f));
            c.drawText(hint(), tx, y + 56 * d + lift, text);
            }
            float pulse = .5f + .5f * (float) (.5 + .5 * Math.cos(SystemClock.uptimeMillis() / 300.0));
            text.setTextSize(sp(16)); text.setColor(alpha(ink, k * pulse));
            c.drawText(I18n.t("Tap to start"), tx, ArcadeCurtain.footY(W, H, d, bottomInset), text);
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
            wordsCols = cols; wordsRows = rows;
            int n = Math.max(1, sources.size()), i = 0;
            for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
                if (!on[y][x]) continue;
                Cell c = new Cell();
                float[] s = sources.isEmpty() ? new float[]{getWidth() / 2f, getHeight() / 2f, LIGHT} : sources.get((i++ * 7) % n);
                c.sx = s[0] + (rnd.nextFloat() - .5f) * 10 * d; c.sy = s[1] + (rnd.nextFloat() - .5f) * 10 * d; c.x = c.sx; c.y = c.sy;
                c.colour = (int) s[2];
                c.col = x - minX; c.row = y - minY;
                c.delay = 500 + (c.col / (float) Math.max(1, cols)) * 380f + rnd.nextFloat() * 90f;
                cells.add(c);
            }
            placeWords();
            for (int k = 0; k < 9; k++) { final int q = k; postDelayed(() -> PixelStackSound.play(PixelStackSound.SPARKLE, .3f, .8f + q * .12f), 760 + k * 62); }
        }

        final ArrayList<float[]> sources = new ArrayList<float[]>();
        float wordsBottom; int wordsCols, wordsRows;

        float overX() { return land ? getWidth() * .3f : getWidth() / 2f; }
        void placeWords() {
            if (cells == null || wordsCols <= 0) return;
            float room = land ? getWidth() * .34f : getWidth() * .8f;
            float size = Math.min(room / wordsCols, 9 * d);
            if (land) size = Math.min(size, getHeight() * .3f / Math.max(1, wordsRows));
            float gx = overX() - wordsCols * size / 2f, gy = getHeight() * (land ? .3f : .36f) - wordsRows * size / 2f;
            if (land) gy = Math.max(gy, topInset + 14 * d);
            wordsBottom = gy + wordsRows * size;
            for (Cell c : cells) { c.tx = gx + c.col * size + size / 2f; c.ty = gy + c.row * size + size / 2f; c.size = size; }
        }

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
            float k = ease((t - 900) / 400f), ox = overX();
            float y = Math.max(H * (land ? .3f : .36f) + (land ? 44 : 60) * d, wordsBottom + (land ? 24 : 34) * d);
            text.setTypeface(BOLD);
            text.setTextSize(sp(46)); text.setColor(alpha(ink2(), k));
            c.drawText(String.valueOf(score), ox, y + 30 * d, text);
            text.setTypeface(MEDIUM);
            text.setTextSize(sp(15)); text.setColor(alpha(ink, k * .8f));
            c.drawText(I18n.t("Best") + "  " + best, ox, y + 58 * d, text);
            if (newBest && score > 0) {
                float pop = 1 + .15f * (1 - spring((t - 1000) / 500f));
                text.setTypeface(BOLD);
                text.setTextSize(sp(22) * Math.max(.01f, pop)); text.setColor(alpha(night ? LIGHT : BLUE, k));
                c.drawText(I18n.t("New best!"), ox, y + 94 * d, text);
            }
            float pulse = .55f + .45f * (float) (.5 + .5 * Math.cos(now / 300.0));
            text.setTypeface(MEDIUM);
            text.setTextSize(sp(16)); text.setColor(alpha(ink, k * pulse));
            float sx = land ? W * .72f : W / 2f;
            c.drawText(I18n.t("Tap to play again"), sx, ArcadeCurtain.footY(W, H, d, bottomInset), text);
            if (land) { shareBar.colX = sx; shareBar.colW = W * .46f; shareBar.homeY = H * .5f; } else { shareBar.colX = -1; shareBar.homeY = -1; }
            shareBar.draw(c, now, t, land ? topInset + 14 * d : y + (newBest && score > 0 ? 112 : 78) * d, score, motion, 1f);
            text.setTextAlign(Paint.Align.CENTER);
        }

        void drawScore(Canvas c, float y, float fade) { drawScore(c, getWidth() / 2f, y, fade); }
        void drawScore(Canvas c, float x, float y, float fade) {
            text.setTypeface(BOLD);
            text.setTextSize(sp(30)); text.setColor(alpha(ink2(), fade));
            c.drawText(String.valueOf(score), x, y, text);
        }

        void drawPlate(Canvas c, RectF r, float fade) {
            float rad = 18 * d;
            fill.setShader(null); fill.setColor(alpha(plate, fade)); c.drawRoundRect(r, rad, rad, fill);
            stroke.setStrokeWidth(black ? 1.5f * d : d); stroke.setColor(alpha(black ? 0x55FFFFFF : rim, fade)); c.drawRoundRect(r, rad, rad, stroke);
        }
    }
}
