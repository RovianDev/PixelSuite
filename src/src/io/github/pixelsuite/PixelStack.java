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
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Random;

final class PixelStack {
    private PixelStack() { }

    private static final int BLUE = 0xFF1846B8, LIGHT = 0xFF9EC1FF;

    static void attach(final Activity a, final View logo, final ImageView mark, final View left, final View right, final int ink, final int page, final boolean dark, final boolean black) {
        sInk = ink; sDark = dark; sBlack = black;
        Dialog was = sDialog == null ? null : sDialog.get();
        Activity owner = sOwner == null ? null : sOwner.get();
        if (sOpen && (was == null || !was.isShowing() || owner != a)) {
            if (was != null && owner != a) { try { was.dismiss(); } catch (Throwable ignored) { } }
            sOpen = false;
        }
        PixelStackSound.prepare(a);
        final int slop = ViewConfiguration.get(a).getScaledTouchSlop();
        final long charge = 700;
        final float[] down = new float[2];
        final boolean[] holding = {false}, fired = {false};
        final long[] since = {0};
        final boolean[] rising = {false};
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            public void run() {
                if (!holding[0]) return;
                long held = SystemClock.uptimeMillis() - since[0];
                if (held < WAIT) { logo.postOnAnimation(this); return; }
                if (!rising[0]) { rising[0] = true; sRise = PixelStackSound.play(PixelStackSound.RISE, .55f, 1f); buzz(a, new int[]{5}, new float[]{.75f}, new int[]{0}, new long[]{0, 70, 0, 70, 0, 70, 0, 70, 0, 70, 0, 70, 0, 70, 0, 70, 0, 70, 0, 70}, ramp()); }
                float p = Math.min(1f, (held - WAIT) / (float) charge);
                float e = p * p * (3 - 2 * p);
                apply(logo, mark, left, right, e, 0f);
                if (p >= 1f) {
                    holding[0] = false; fired[0] = true;
                    PixelStackSound.stop(sRise); PixelStackSound.play(PixelStackSound.POP, .72f, 1f);
                    buzz(a, new int[]{1, 7}, new float[]{1f, .8f}, new int[]{0, 25}, new long[]{0, 18, 25, 12}, new int[]{0, 255, 0, 160});
                    open(a, logo, mark, left, right, ink, page, dark, black);
                    return;
                }
                logo.postOnAnimation(this);
            }
        };
        logo.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (sOpen) return false;
                        down[0] = e.getRawX(); down[1] = e.getRawY();
                        holding[0] = true; fired[0] = false; rising[0] = false; since[0] = SystemClock.uptimeMillis();
                        logo.postOnAnimation(tick[0]);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (holding[0] && Math.hypot(e.getRawX() - down[0], e.getRawY() - down[1]) > slop * 2) {
                            if (rising[0]) release(a, logo, mark, left, right, holding, since[0] + WAIT, charge); else holding[0] = false;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!holding[0]) return true;
                        if (rising[0]) { release(a, logo, mark, left, right, holding, since[0] + WAIT, charge); return true; }
                        holding[0] = false;
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && mark.getDrawable() instanceof SuiteIcon) {
                            ((SuiteIcon) mark.getDrawable()).hop();
                            PixelStackSound.play(PixelStackSound.BOOP, .55f, 1f); logo.postDelayed(() -> PixelStackSound.play(PixelStackSound.BOOP, .45f, 1.26f), 140);
                            buzz(a, new int[]{7, 7}, new float[]{.5f, .8f}, new int[]{0, 120}, new long[]{0, 10, 110, 14}, new int[]{0, 120, 0, 180});
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private static final long WAIT = 140;
    private static int sRise;
    private static int[] ramp() { int[] r = new int[20]; for (int i = 0; i < 10; i++) { r[i * 2] = 0; r[i * 2 + 1] = 20 + i * 22; } return r; }

    private static final float WIDE = 2.4f, TALL = .22f;

    private static final class HoldMark extends android.graphics.drawable.Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int plate, ink, soft; final boolean black, dark; final float dd;
        float cx, cy, base, e; final long born = SystemClock.uptimeMillis();
        final RectF box = new RectF();
        HoldMark(Context c, int ink, boolean dark, boolean black) {
            this.ink = ink | 0xFF000000; soft = (Math.round(255 * .45f) << 24) | (ink & 0xFFFFFF); this.black = black; this.dark = dark;
            dd = c.getResources().getDisplayMetrics().density;
            plate = black ? 0xFF000000 : dark ? 0xFF2E2F36 : FrostedDialog.tone(c);
            rim.setStyle(Paint.Style.STROKE);
        }
        RectF bubble() {
            long t = SystemClock.uptimeMillis() - born;
            float wob = e * e;
            float w = base * (1f + WIDE * e) * (1f + .035f * wob * (float) Math.sin(t / 55.0));
            float h = base * (1f + TALL * e) * (1f + .05f * wob * (float) Math.sin(t / 47.0 + 1.3));
            box.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            return box;
        }
        RectF square(int k, RectF out) {
            float f = e * e * (3 - 2 * e);
            float u = .235f * base * (1f + .2f * e), g = .07f * base;
            int col = k % 2, row = k / 2;
            float gx = (col - .5f) * (u + g), gy = (row - .5f) * (u + g);
            int[] order = {0, 2, 1, 3};
            float rx = (order[k] - 1.5f) * (u + .2f * base), ry = 0;
            long t = SystemClock.uptimeMillis() - born;
            float bob = (float) Math.sin(t / 90.0 + k) * .04f * base * e;
            float x = cx + gx + (rx - gx) * f, y = cy + gy + (ry - gy) * f + bob;
            out.set(x - u / 2f, y - u / 2f, x + u / 2f, y + u / 2f);
            return out;
        }
        @Override public void draw(Canvas c) {
            if (base <= 0) return;
            RectF b = bubble(); float r = b.height() / 2f;
            int a = Math.round(255 * (1f - .55f * e));
            fill.setShader(null); fill.setColor((plate & 0xFFFFFF) | (a << 24));
            if (!black) fill.setShadowLayer(6 * dd * (1 - e), 0, 2 * dd, 0x33000000);
            c.drawRoundRect(b, r, r, fill); fill.clearShadowLayer();
            rim.setStrokeWidth((1.2f + 1f * e) * dd);
            rim.setShader(new android.graphics.LinearGradient(b.left, b.top, b.right, b.bottom,
                    new int[]{0x99A8CCFF, 0x88FFB3E0, 0x88FFF1A8, 0x88B6FFD9, 0x99A8CCFF}, null, android.graphics.Shader.TileMode.CLAMP));
            int ra = Math.round(255 * (.35f + .65f * e)); rim.setAlpha(ra);
            c.drawRoundRect(b, r, r, rim); rim.setShader(null);
            if (e > .05f) {
                fill.setColor(((dark || black) ? 0x55FFFFFF : 0x99FFFFFF) & 0x00FFFFFF | (Math.round(((dark || black) ? 85 : 150) * e) << 24));
                RectF gl = new RectF(b.left + r * .45f, b.top + b.height() * .14f, b.left + r * .45f + b.width() * .26f, b.top + b.height() * .3f);
                c.drawRoundRect(gl, gl.height() / 2, gl.height() / 2, fill);
                c.drawCircle(b.right - r * .55f, b.top + b.height() * .28f, 1.6f * dd * e, fill);
            }
            RectF q = new RectF(); float cr = .052f * base;
            for (int k = 0; k < 4; k++) {
                int col = k % 2, row = k / 2;
                square(k, q); fill.setColor(col == row ? ink : soft); c.drawRoundRect(q, cr, cr, fill);
            }
            if (e > 0f) invalidateSelf();
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    private static final int TAG_MARK = 0x7e5c0e0c;
    private static boolean sOpen;
    private static java.lang.ref.WeakReference<Dialog> sDialog;
    private static java.lang.ref.WeakReference<Activity> sOwner;
    private static int sInk; private static boolean sDark, sBlack;

    static void apply(View logo, ImageView mark, View left, View right, float e, float tremble) { apply(logo, mark, left, right, e, tremble, true); }
    static void apply(View logo, ImageView mark, View left, View right, float e, float tremble, boolean showLogo) {
        float dd = logo.getResources().getDisplayMetrics().density;
        float push = logo.getWidth() * WIDE * e / 2f + 6 * dd * e;
        if (left != null) { left.setTranslationX(-push); left.setAlpha(1f - .25f * e); }
        if (right != null) { right.setTranslationX(push); right.setAlpha(1f - .25f * e); }
        android.view.ViewParent v = logo.getParent();
        for (int i = 0; i < 2 && v instanceof android.view.ViewGroup; i++, v = v.getParent()) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            Object was = g.getTag(0x7e5c0e0b);
            if (e > 0f) { if (was == null) { g.setTag(0x7e5c0e0b, g.getClipChildren()); g.setClipChildren(false); } }
            else if (was instanceof Boolean) { g.setClipChildren((Boolean) was); g.setTag(0x7e5c0e0b, null); }
        }
        View root = logo.getRootView();
        Object held = logo.getTag(TAG_MARK);
        HoldMark m = held instanceof HoldMark ? (HoldMark) held : null;
        if (e > 0f) {
            if (m == null) { m = new HoldMark(logo.getContext(), sInk, sDark, sBlack); logo.setTag(TAG_MARK, m); try { root.getOverlay().add(m); } catch (Throwable ignored) { } }
            int[] at = new int[2], rootAt = new int[2]; logo.getLocationOnScreen(at); root.getLocationOnScreen(rootAt);
            m.cx = at[0] - rootAt[0] + logo.getWidth() / 2f; m.cy = at[1] - rootAt[1] + logo.getHeight() / 2f;
            m.base = logo.getWidth(); m.e = e;
            m.setBounds(0, 0, root.getWidth(), root.getHeight()); m.invalidateSelf(); root.invalidate();
            logo.setAlpha(0f);
        } else {
            if (m != null) { m.e = 0f; try { root.getOverlay().remove(m); } catch (Throwable ignored) { } logo.setTag(TAG_MARK, null); root.invalidate(); }
            if (showLogo) logo.setAlpha(1f);
        }
        logo.setScaleX(1f); logo.setScaleY(1f); logo.setTranslationY(0); logo.setRotation(0);
        if (mark.getDrawable() instanceof SuiteIcon) ((SuiteIcon) mark.getDrawable()).spread(0f);
    }
    static RectF[] heldRects(View logo) {
        Object held = logo.getTag(TAG_MARK);
        if (!(held instanceof HoldMark)) return null;
        HoldMark m = (HoldMark) held; int[] rootAt = new int[2]; logo.getRootView().getLocationOnScreen(rootAt);
        RectF[] out = new RectF[5];
        out[0] = new RectF(m.bubble()); out[0].offset(rootAt[0], rootAt[1]);
        for (int k = 0; k < 4; k++) { out[k + 1] = m.square(k, new RectF()); out[k + 1].offset(rootAt[0], rootAt[1]); }
        return out;
    }

    private static void release(Context c, final View logo, final ImageView mark, final View left, final View right, boolean[] holding, long since, long charge) {
        holding[0] = false;
        PixelStackSound.stop(sRise);
        try { ((Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE)).cancel(); } catch (Throwable ignored) { }
        float p = Math.min(1f, (SystemClock.uptimeMillis() - since) / (float) charge);
        settle(logo, mark, left, right, p * p * (3 - 2 * p), 420);
    }

    static void settle(final View logo, final ImageView mark, final View left, final View right, final float e0, final long ms) {
        settle(logo, mark, left, right, e0, ms, true);
    }
    static void settle(final View logo, final ImageView mark, final View left, final View right, final float e0, final long ms, final boolean showLogo) {
        final long t0 = SystemClock.uptimeMillis();
        final float r0 = 0f;
        logo.postOnAnimation(new Runnable() {
            public void run() {
                float t = Math.min(1f, (SystemClock.uptimeMillis() - t0) / (float) ms);
                float e = Math.max(0f, e0 * (1f - spring(t)));
                if (t >= 1f || e <= 0.0005f) e = t >= 1f ? 0f : 0.0005f;
                apply(logo, mark, left, right, t >= 1f ? 0f : e, r0 * (1 - t), showLogo);
                if (t < 1f) logo.postOnAnimation(this);
            }
        });
    }

    static float spring(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return (float) (1 - Math.exp(-6.5 * t) * Math.cos(9.5 * t));
    }
    static float ease(float t) { t = Math.max(0f, Math.min(1f, t)); return 1 - (1 - t) * (1 - t) * (1 - t); }
    static float easeIn(float t) { t = Math.max(0f, Math.min(1f, t)); return t * t * t; }

    static void buzz(Context c, int[] prims, float[] scales, int[] delays, long[] timings, int[] amps) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            Class<?> fx = Class.forName("android.os.VibrationEffect");
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                try {
                    boolean all = (Boolean) Vibrator.class.getMethod("areAllPrimitivesSupported", int[].class).invoke(v, (Object) prims);
                    if (all) {
                        Object comp = fx.getMethod("startComposition").invoke(null);
                        java.lang.reflect.Method add = comp.getClass().getMethod("addPrimitive", int.class, float.class, int.class);
                        for (int i = 0; i < prims.length; i++) add.invoke(comp, prims[i], scales[i], delays[i]);
                        Object effect = comp.getClass().getMethod("compose").invoke(comp);
                        Vibrator.class.getMethod("vibrate", fx).invoke(v, effect);
                        return;
                    }
                } catch (Throwable ignored) { }
            }
            Object wave = fx.getMethod("createWaveform", long[].class, int[].class, int.class).invoke(null, timings, amps, -1);
            Vibrator.class.getMethod("vibrate", fx).invoke(v, wave);
        } catch (Throwable ignored) { }
    }
    private static void tickBuzz(Context c, float strength) { buzz(c, new int[]{7}, new float[]{strength}, new int[]{0}, new long[]{0, 12}, new int[]{0, Math.round(80 + 170 * strength)}); }

    private static void open(final Activity a, final View logo, final ImageView mark, final View left, final View right, int ink, int page, boolean dark, boolean black) {
        if (a.isFinishing()) return;
        int[] at = new int[2]; logo.getLocationOnScreen(at);
        RectF[] held = heldRects(logo);
        final RectF from = new RectF(at[0], at[1], at[0] + logo.getWidth(), at[1] + logo.getHeight());
        final Game game = new Game(a, from, ink, page, dark, black);
        if (held != null) { game.popAt = held[0]; for (int k = 0; k < 4; k++) game.startAt[k] = held[k + 1]; }
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar) {
            @Override public void onBackPressed() {
                if (game.typing()) { game.closeKeyboard(); if (game.fieldUp) game.fieldMove(false); return; }
                game.exit();
            }
        };
        game.dialog = d;
        game.onGone = () -> {
            sOpen = false;
            apply(logo, mark, left, right, 0f, 0f, false);
            logo.setRotation(0);
            if (mark.getDrawable() instanceof SuiteIcon) ((SuiteIcon) mark.getDrawable()).spread(0f);
            final long t0 = SystemClock.uptimeMillis();
            logo.postOnAnimation(new Runnable() { public void run() {
                float t = Math.min(1f, (SystemClock.uptimeMillis() - t0) / 300f), k = ease(t), s = 1.1f - .1f * k;
                logo.setAlpha(Math.min(1f, t * 1.6f)); logo.setScaleX(s); logo.setScaleY(s); if (t < 1f) logo.postOnAnimation(this);
            } });
            tickBuzz(a, .45f);
            PixelStackSound.play(PixelStackSound.BOOP, .45f, .9f);
        };
        android.widget.FrameLayout root = new android.widget.FrameLayout(a);
        root.addView(game, new android.widget.FrameLayout.LayoutParams(-1, -1));
        game.field = nameField(a, game, ink, dark || black);
        root.addView(game.field, new android.widget.FrameLayout.LayoutParams(1, 1, android.view.Gravity.TOP | android.view.Gravity.LEFT));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            w.setWindowAnimations(0); w.setDimAmount(0f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            w.setStatusBarColor(0); w.setNavigationBarColor(0);
            if (!dark && !black) w.getDecorView().setSystemUiVisibility(0x2000 | 0x10 | 0x400 | 0x200 | 0x100); else w.getDecorView().setSystemUiVisibility(0x400 | 0x200 | 0x100);
            try { Window.class.getMethod("setDecorFitsSystemWindows", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
            try { WindowManager.LayoutParams lp = w.getAttributes(); lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 3); w.setAttributes(lp); } catch (Throwable ignored) { }
        }
        d.setCancelable(true);
        d.setOnDismissListener(x -> sOpen = false);
        d.show();
        sOpen = true;
        sDialog = new java.lang.ref.WeakReference<Dialog>(d);
        sOwner = new java.lang.ref.WeakReference<Activity>(a);
        Object mk = logo.getTag(TAG_MARK);
        if (mk instanceof HoldMark) { ((HoldMark) mk).e = 0f; try { logo.getRootView().getOverlay().remove((HoldMark) mk); } catch (Throwable ignored) { } logo.setTag(TAG_MARK, null); }
        logo.setAlpha(0f);
        final long w0 = SystemClock.uptimeMillis();
        final float from0 = left.getTranslationX();
        left.postOnAnimation(new Runnable() { public void run() {
            float t = Math.min(1f, (SystemClock.uptimeMillis() - w0) / 600f), k = 1f - spring(t);
            left.setTranslationX(from0 * k); right.setTranslationX(-from0 * k); left.setAlpha(1f - .25f * k); right.setAlpha(1f - .25f * k);
            if (t < 1f) left.postOnAnimation(this); else apply(logo, mark, left, right, 0f, 0f, false);
        } });

    }

    static final float BAR_UP = 160;

    private static android.widget.EditText nameField(Context c, final Game game, int ink, boolean dark) {
        final android.widget.EditText f = new android.widget.EditText(c) {
            @Override public boolean onKeyPreIme(int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_BACK && hasFocus()) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_UP) game.closeKeyboard();
                    return true;
                }
                return super.onKeyPreIme(code, e);
            }
        };
        f.setBackground(null); f.setPadding(0, 0, 0, 0);
        f.setSingleLine(true); f.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        f.setTextSize(15); f.setTextColor(0); f.setHintTextColor(0); f.setHighlightColor(0);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setHint(I18n.t("Your name"));
        f.setCursorVisible(false);
        f.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        f.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        android.text.InputFilter plain = (src, st, en, dst, ds, de) -> {
            CharSequence part = src.subSequence(st, en);
            String kept = PixelStackShare.noEmoji(part);
            return kept.length() == part.length() ? null : kept;
        };
        f.setFilters(new android.text.InputFilter[]{plain, new android.text.InputFilter.LengthFilter(24)});
        f.setText(game.prefs.getString("name", ""));
        f.setOnEditorActionListener((v, id, ev) -> { game.closeKeyboard(); return true; });
        f.setOnFocusChangeListener((v, has) -> game.fieldMove(has));
        f.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            public void onTextChanged(CharSequence s, int a, int b, int d) { }
            public void afterTextChanged(android.text.Editable e) {
                try { game.prefs.edit().putString("name", PixelStackShare.noEmoji(e.toString()).trim()).apply(); } catch (Throwable ignored) { }
                game.player = null;
            }
        });
        f.setVisibility(View.GONE);
        return f;
    }

    private static final class Block { float l, r; int level; Block(float l, float r, int level) { this.l = l; this.r = r; this.level = level; } }
    private static final class Bit { float l, t, r, b, vx, vy, rot, vr, life; int colour; boolean square; }
    private static final class Pop { String text; float x, y; long at; int colour; float size; }

    private static final int INTRO = 0, READY = 1, MERGE = 2, PLAY = 3, OVER = 4, EXIT = 5, REFORM = 6;
    private static final class Cell { float sx, sy, tx, ty, size, x, y, now; int colour, col; float delay; }

    private static final class Game extends View {
        Dialog dialog; Runnable onGone; android.widget.EditText field;
        final Activity a; final RectF from; final int ink, page, soft; final boolean dark, black; final float d;
        final boolean motion; final SharedPreferences prefs; final Random rnd = new Random();
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box = new RectF();
        final ArrayList<Block> blocks = new ArrayList<Block>();
        final ArrayList<Bit> bits = new ArrayList<Bit>();
        final ArrayList<Pop> pops = new ArrayList<Pop>();
        int state = INTRO, score, best, combo; boolean newBest;
        final java.util.Map<String, String> share;
        final RectF shareAt = new RectF(), barAt = new RectF();
        private float shareW;
        int shareState;
        long shareAt0; android.graphics.Bitmap shareCard; String player;
        long stateAt, last, flashAt = -10000, shakeAt = -10000;
        float curL, curR, dir = 1, speed, cam, camTarget, zoom = 1f, bh, baseW, baseY, introSize;
        final RectF[] logoAt = new RectF[4], bigAt = new RectF[4], startAt = new RectF[4];
        RectF popAt;
        boolean introSound;
        final float[] drops = new float[48];

        Game(Activity a, RectF from, int ink, int page, boolean dark, boolean black) {
            super(a);
            this.a = a; this.from = from; this.ink = ink | 0xFF000000; this.page = page | 0xFF000000; this.dark = dark; this.black = black;
            soft = (Math.round(255 * .45f) << 24) | (ink & 0xFFFFFF);
            d = getResources().getDisplayMetrics().density;
            float scale = 1f;
            try { scale = android.provider.Settings.Global.getFloat(a.getContentResolver(), "animator_duration_scale", 1f); } catch (Throwable ignored) { }
            motion = scale > 0f;
            prefs = a.getSharedPreferences("pixelstack", Context.MODE_PRIVATE);
            best = prefs.getInt("best", 0);
            share = PixelStackShare.config(a);
            stroke.setStyle(Paint.Style.STROKE);
            text.setTextAlign(Paint.Align.CENTER);
            setFocusable(true);
            stateAt = last = SystemClock.uptimeMillis();
        }

        private void drawPop(Canvas c, float t) {
            float k = ease(t / 420f), r = popAt.height() / 2f;
            stroke.setShader(null);
            box.set(popAt); float grow = 1 + .35f * k;
            box.set(popAt.centerX() - popAt.width() / 2 * grow, popAt.centerY() - popAt.height() / 2 * grow, popAt.centerX() + popAt.width() / 2 * grow, popAt.centerY() + popAt.height() / 2 * grow);
            stroke.setStrokeWidth(2.5f * d * (1 - k)); stroke.setColor(alpha(dark || black ? 0xFFBFD8FF : 0xFF7FA6F5, 1 - k));
            c.drawRoundRect(box, r * grow, r * grow, stroke);
            if (drops[0] == 0) {
                for (int i = 0; i < 12; i++) {
                    double ang = Math.PI * 2 * i / 12 + rnd.nextFloat() * .3;
                    drops[i * 4] = (float) Math.cos(ang); drops[i * 4 + 1] = (float) Math.sin(ang);
                    drops[i * 4 + 2] = (28 + rnd.nextFloat() * 34) * d; drops[i * 4 + 3] = (1.6f + rnd.nextFloat() * 2.2f) * d;
                }
            }
            for (int i = 0; i < 12; i++) {
                float dx = drops[i * 4], dy = drops[i * 4 + 1], far = drops[i * 4 + 2], sz = drops[i * 4 + 3];
                float x = popAt.centerX() + dx * (popAt.width() / 2f + far * k), y = popAt.centerY() + dy * (popAt.height() / 2f + far * k * .7f) + 14 * d * k * k;
                fill.setShader(null); fill.setColor(alpha(i % 3 == 0 ? 0xFFFFB3E0 : i % 3 == 1 ? 0xFFA8CCFF : 0xFFFFF1A8, (1 - k) * .95f));
                c.drawCircle(x, y, sz * (1 - .5f * k), fill);
            }
        }
        private void homeAt() { squaresOf(from, logoAt); }

        private void squaresOf(RectF logo, RectF[] out) {
            float s = logo.width(), u = .235f * s, g = .07f * s, cx = logo.centerX(), cy = logo.centerY();
            float x0 = cx - u - g / 2f, y0 = cy - u - g / 2f;
            for (int k = 0; k < 4; k++) {
                int col = k % 2, row = k / 2;
                if (out[k] == null) out[k] = new RectF();
                out[k].set(x0 + col * (u + g), y0 + row * (u + g), x0 + col * (u + g) + u, y0 + row * (u + g) + u);
            }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            bh = 30 * d; baseW = Math.min(w * .62f, 300 * d);
            baseY = h * .72f;
            introSize = Math.min(w, h) * .42f;
            squaresOf(from, logoAt);
            if (startAt[0] != null) for (int k = 0; k < 4; k++) logoAt[k] = new RectF(startAt[k]);
            float cx = w / 2f, cy = h * .4f;
            squaresOf(new RectF(cx - introSize / 2, cy - introSize / 2, cx + introSize / 2, cy + introSize / 2), bigAt);
        }

        private long since() { return SystemClock.uptimeMillis() - stateAt; }
        private void go(int s) { state = s; stateAt = SystemClock.uptimeMillis(); }
        private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

        private static final float[] AT = {1f, .85f, .7f, .55f, .4f, .27f, .15f};
        private static final int[] RAINBOW = {0xFF0D2E8A, 0xFF1846B8, 0xFF4F8DF5, 0xFF2BB673, 0xFFF2C230, 0xFFF28C28, 0xFFE53935};
        int widthColour(float w) {
            float q = baseW <= 0 ? 1f : Math.max(0f, Math.min(1f, w / baseW));
            if (q >= AT[0]) return RAINBOW[0];
            for (int i = 1; i < AT.length; i++) if (q >= AT[i]) return mix(RAINBOW[i], RAINBOW[i - 1], (q - AT[i]) / (AT[i - 1] - AT[i]));
            return RAINBOW[RAINBOW.length - 1];
        }
        private int blockColour(int level) {
            if (level >= 0 && level < blocks.size()) { Block b = blocks.get(level); return widthColour(b.r - b.l); }
            if (state == PLAY && level == blocks.size()) return widthColour(curR - curL);
            return blocks.isEmpty() ? RAINBOW[0] : blockColour(blocks.size() - 1);
        }
        private static int mix(int x, int y, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int a = Math.round(((x >>> 24) & 255) + (((y >>> 24) & 255) - ((x >>> 24) & 255)) * t);
            int r = Math.round(((x >> 16) & 255) + (((y >> 16) & 255) - ((x >> 16) & 255)) * t);
            int g = Math.round(((x >> 8) & 255) + (((y >> 8) & 255) - ((x >> 8) & 255)) * t);
            int b = Math.round((x & 255) + ((y & 255) - (x & 255)) * t);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        private int alpha(int c, float f) { return (Math.round(((c >>> 24) & 255) * Math.max(0f, Math.min(1f, f))) << 24) | (c & 0xFFFFFF); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
            switch (state) {
                case INTRO: if (since() > 450) { go(READY); } break;
                case READY: start(); break;
                case PLAY: drop(); break;
                case OVER:
                    if (field != null && field.hasFocus()) { closeKeyboard(); if (!hit(shareAt, e)) break; }
                    if (share != null && shareState != 1 && shareState != 2 && score > 0 && since() > 900 && hit(shareAt, e)) { share(); break; }
                    if (hit(barAt, e)) break;
                    if (shareState == 1) break;
                    if (since() > 650) { if (cells != null) reform(); else restart(); }
                    break;
            }
            return true;
        }

        private void start() {
            go(MERGE);
            PixelStackSound.play(PixelStackSound.BOING, .6f, 1f);
            buzz(a, new int[]{4}, new float[]{.9f}, new int[]{0}, new long[]{0, 40}, new int[]{0, 180});
        }

        static final float GO_AT = 1250, REFORM_MS = 560;
        ArrayList<Cell> cells;

        private void buildCells() {
            String words = I18n.t("Game Over");
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG); tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); tp.setTextSize(15);
            Paint.FontMetrics fm = tp.getFontMetrics();
            String[] lines = {words};
            if (tp.measureText(words) > 64 && words.trim().contains(" ")) {
                int best = -1; float bestDiff = 1e9f;
                for (int k = words.indexOf(' '); k > 0; k = words.indexOf(' ', k + 1)) { float diff = Math.abs(tp.measureText(words, 0, k) - tp.measureText(words, k + 1, words.length())); if (diff < bestDiff) { bestDiff = diff; best = k; } }
                if (best > 0) lines = new String[]{words.substring(0, best), words.substring(best + 1)};
            }
            float lineH = fm.descent - fm.ascent;
            float widest = 0; for (String ln : lines) widest = Math.max(widest, tp.measureText(ln));
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
            float size = Math.min(getWidth() * .8f / cols, 9 * d), gx = getWidth() / 2f - cols * size / 2f, gy = getHeight() * .44f - rows * size / 2f;
            float cx = getWidth() / 2f;
            int n = blocks.size(), i = 0;
            for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
                if (!on[y][x]) continue;
                Cell c = new Cell();
                Block b = blocks.get(i++ % n);
                float by = baseY - (b.level + 1) * bh;
                float wx = b.l + rnd.nextFloat() * (b.r - b.l), wy = by + rnd.nextFloat() * bh;
                c.sx = cx + (wx - cx) * zoom; c.sy = baseY + (wy - baseY) * zoom + cam; c.x = c.sx; c.y = c.sy;
                c.col = x - minX;
                c.tx = gx + c.col * size + size / 2f; c.ty = gy + (y - minY) * size + size / 2f;
                c.size = size; c.colour = widthColour(b.r - b.l);
                c.delay = (c.col / (float) Math.max(1, cols)) * 380f + rnd.nextFloat() * 90f;
                cells.add(c);
            }
            buzz(a, new int[]{7, 7, 7}, new float[]{.5f, .6f, .8f}, new int[]{0, 90, 90}, new long[]{0, 10, 80, 10, 80, 14}, new int[]{0, 100, 0, 140, 0, 200});
            for (int k = 0; k < 9; k++) { final int q = k; postDelayed(() -> PixelStackSound.play(PixelStackSound.SPARKLE, .32f, .8f + q * .12f), 260 + k * 62); }
        }

        private void drawCells(Canvas c, long now, float fade) {
            if (cells == null) return;
            boolean reforming = state == REFORM;
            float t = reforming ? since() : since() - GO_AT;
            for (Cell e : cells) {
                float p, x, y, sz; int colour = e.colour;
                if (!reforming) {
                    p = motion ? Math.max(0f, Math.min(1f, (t - e.delay) / 620f)) : 1f;
                    float m = spring(p);
                    x = e.sx + (e.tx - e.sx) * m; y = e.sy + (e.ty - e.sy) * m - (float) Math.sin(Math.PI * Math.min(1f, p)) * 46 * d;
                    sz = 5 * d + (e.size * .86f - 5 * d) * Math.min(1f, m);
                    if (p >= 1f && motion) y += (float) Math.sin(now / 320.0 - e.col * .35) * 1.4f * d;
                    e.x = x; e.y = y; e.now = sz;
                } else {
                    p = motion ? Math.max(0f, Math.min(1f, (t - e.delay) / REFORM_MS)) : 1f;
                    float m = p < .5f ? 4 * p * p * p : 1 - (float) Math.pow(-2 * p + 2, 3) / 2;
                    x = e.sx + (e.tx - e.sx) * m; y = e.sy + (e.ty - e.sy) * m;
                    sz = e.size + ((bh - 3 * d) * .55f - e.size) * m;
                    colour = mix(e.colour, blockColour(0), m);
                }
                box.set(x - sz / 2, y - sz / 2, x + sz / 2, y + sz / 2); float rr = sz * .26f;
                fill.setShader(null); fill.setColor(alpha(colour, fade)); c.drawRoundRect(box, rr, rr, fill);
                box.set(x - sz / 2 + sz * .14f, y - sz / 2 + sz * .12f, x + sz / 2 - sz * .14f, y - sz / 2 + sz * .34f);
                fill.setColor(alpha(0x55FFFFFF, fade)); c.drawRoundRect(box, sz * .1f, sz * .1f, fill);
            }
        }

        private void reform() {
            if (cells == null) { restart(); return; }
            float l = getWidth() / 2f - baseW / 2f;
            for (Cell e : cells) {
                e.sx = e.x; e.sy = e.y; e.size = e.now;
                e.tx = l + rnd.nextFloat() * baseW; e.ty = baseY - bh / 2f + (rnd.nextFloat() - .5f) * bh * .5f;
                e.delay = (e.col / 60f) * 160f + rnd.nextFloat() * 90f;
            }
            shareState = 0; player = null;
            for (int i = bits.size() - 1; i >= 0; i--) bits.remove(i);
            go(REFORM);
            PixelStackSound.play(PixelStackSound.SOFT, .32f, .84f);
            tickBuzz(a, .7f);
        }

        private void begin() {
            blocks.clear(); score = 0; combo = 0; newBest = false; cam = camTarget = 0; zoom = 1f;
            float l = getWidth() / 2f - baseW / 2f;
            blocks.add(new Block(l, l + baseW, 0));
            next();
            go(PLAY);
        }

        private void next() {
            Block top = blocks.get(blocks.size() - 1);
            float w = top.r - top.l;
            dir = blocks.size() % 2 == 0 ? -1 : 1;
            float min = -w * .3f, max = getWidth() - w * .7f;
            curL = dir > 0 ? min : max; curR = curL + w;
            speed = getWidth() * Math.min(1.45f, .62f + .035f * score);
        }

        private void drop() {
            Block top = blocks.get(blocks.size() - 1);
            float L = Math.max(curL, top.l), R = Math.min(curR, top.r);
            int level = blocks.size();
            float y = baseY - (level + 1) * bh;
            if (R - L <= 0) {
                bits.add(piece(curL, y, curR, y + bh, blockColour(level), (curL < top.l ? -1 : 1) * 60 * d));
                over();
                return;
            }
            float off = curL - top.l;
            if (Math.abs(off) <= 4.5f * d) {
                combo++;
                float l = top.l, r = top.r;
                if (combo >= 3) { float grow = Math.min(5 * d, (baseW - (r - l)) / 2f); l -= grow; r += grow; }
                blocks.add(new Block(l, r, level));
                flashAt = SystemClock.uptimeMillis();
                Pop p = new Pop(); p.text = I18n.t("Perfect!") + (combo > 1 ? "  ×" + combo : ""); p.x = (l + r) / 2f; p.y = y - 10 * d; p.at = flashAt; p.colour = LIGHT; p.size = 18;
                pops.add(p);
                sparkle((l + r) / 2f, y + bh / 2f, r - l, 6 + Math.min(10, combo * 2));
                tickBuzz(a, 1f);
                PixelStackSound.play(PixelStackSound.PERFECT, .6f, 1f + .06f * Math.min(combo - 1, 6));
            } else {
                combo = 0;
                if (curL < L) bits.add(piece(curL, y, L, y + bh, blockColour(level), -40 * d));
                if (curR > R) bits.add(piece(R, y, curR, y + bh, blockColour(level), 40 * d));
                blocks.add(new Block(L, R, level));
                tickBuzz(a, .45f);
                PixelStackSound.play(PixelStackSound.CHIP, .45f, .9f + rnd.nextFloat() * .3f);
            }
            int[] steps = {0, 2, 4, 7, 9, 12, 14};
            PixelStackSound.play(PixelStackSound.DROP, .6f, (float) (.8 * Math.pow(2, steps[score % steps.length] / 12.0)));
            score++;
            float topY = baseY - (blocks.size()) * bh;
            camTarget = Math.max(0f, getHeight() * .42f - topY);
            next();
        }

        private Bit piece(float l, float t, float r, float b, int colour, float vx) {
            Bit p = new Bit(); p.l = l; p.t = t; p.r = r; p.b = b; p.colour = colour;
            p.vx = vx + (rnd.nextFloat() - .5f) * 30 * d; p.vy = -60 * d; p.vr = (vx < 0 ? -1 : 1) * (90 + rnd.nextFloat() * 160); p.life = 1f;
            return p;
        }

        private void sparkle(float cx, float cy, float width, int n) {
            for (int i = 0; i < n; i++) {
                Bit p = new Bit(); float s = (3 + rnd.nextFloat() * 4) * d;
                float x = cx + (rnd.nextFloat() - .5f) * width;
                p.l = x - s / 2; p.r = x + s / 2; p.t = cy - s / 2; p.b = cy + s / 2; p.square = true;
                p.vx = (rnd.nextFloat() - .5f) * 260 * d; p.vy = -(140 + rnd.nextFloat() * 260) * d; p.vr = (rnd.nextFloat() - .5f) * 720; p.life = 1f;
                p.colour = rnd.nextBoolean() ? LIGHT : (dark || black ? 0xFFFFFFFF : BLUE);
                bits.add(p);
            }
        }

        private void over() {
            go(OVER);
            shakeAt = SystemClock.uptimeMillis();
            if (score > best) { best = score; newBest = true; try { prefs.edit().putInt("best", best).apply(); } catch (Throwable ignored) { } }
            buzz(a, new int[]{2, 6}, new float[]{1f, .7f}, new int[]{0, 40}, new long[]{0, 70, 50, 40}, new int[]{0, 255, 0, 140});
            PixelStackSound.play(PixelStackSound.OVER, .55f, 1f);
            if (newBest && score > 0) {
                postDelayed(() -> PixelStackSound.play(PixelStackSound.SUCCESS, .55f, 1.12f), 900);
                Block top = blocks.get(blocks.size() - 1);
                sparkle((top.l + top.r) / 2f, baseY - blocks.size() * bh, top.r - top.l, 28);
                postDelayed(() -> buzz(a, new int[]{3, 7, 7}, new float[]{.6f, .8f, 1f}, new int[]{0, 60, 60}, new long[]{0, 20, 60, 20, 60, 20}, new int[]{0, 160, 0, 200, 0, 255}), 350);
            }
        }

        private float fieldK, fieldFrom; private boolean fieldUp; private long fieldAt;
        void fieldMove(boolean up) {
            fieldFrom = fieldK; fieldUp = up; fieldAt = SystemClock.uptimeMillis();
            tickBuzz(a, up ? .4f : .25f);
            PixelStackSound.play(PixelStackSound.SOFT, .3f, up ? 1.12f : .9f);
            if (up && motion && !barAt.isEmpty()) sparkle(barAt.centerX(), barAt.centerY(), barAt.width() * .8f, 10);
            invalidate();
        }
        private float fieldNow(long now) {
            float t = (now - fieldAt) / 520f, target = fieldUp ? 1f : 0f;
            if (!motion) t = 1f;
            fieldK = fieldFrom + (target - fieldFrom) * (t >= 1f ? 1f : spring(t));
            return fieldK;
        }

        private long imeSeenAt;
        boolean typing() {
            long now = SystemClock.uptimeMillis();
            if (imeShown()) imeSeenAt = now;
            return (field != null && field.hasFocus()) || fieldUp || fieldK > .02f || (imeSeenAt != 0 && now - imeSeenAt < 600);
        }
        private boolean imeShown() {
            try {
                if (android.os.Build.VERSION.SDK_INT < 30) return false;
                android.view.WindowInsets in = getRootWindowInsets();
                if (in == null) return false;
                int ime = (Integer) Class.forName("android.view.WindowInsets$Type").getMethod("ime").invoke(null);
                return (Boolean) android.view.WindowInsets.class.getMethod("isVisible", int.class).invoke(in, ime);
            } catch (Throwable t) { return false; }
        }

        void closeKeyboard() {
            if (field == null) return;
            try { ((android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(), 0); } catch (Throwable ignored) { }
            field.clearFocus(); requestFocus();
        }

        private boolean hit(RectF r, MotionEvent e) { return r.width() > 0 && e.getX() > r.left - 12 * d && e.getX() < r.right + 12 * d && e.getY() > r.top - 12 * d && e.getY() < r.bottom + 12 * d; }

        private void share() {
            final java.util.ArrayList<float[]> tower = new java.util.ArrayList<float[]>();
            float cx = getWidth() / 2f, widest = 1;
            for (Block b : blocks) { tower.add(new float[]{(b.l - cx) / d * 3f, (b.r - cx) / d * 3f, blockColour(b.level)}); widest = Math.max(widest, (b.r - b.l) / d * 3f); }
            final float wid = widest;
            final int sc = score, bs = best; final boolean nb = newBest; final String who = PixelStackShare.player(a);
            closeKeyboard();
            android.graphics.Bitmap made = null;
            try { made = PixelStackShare.card(a, sc, bs, nb, tower, wid, ink, page, dark || black, BLUE, LIGHT, who); } catch (Throwable ignored) { }
            final android.graphics.Bitmap card = made;
            shareCard = card;
            shareState = 1; shareAt0 = SystemClock.uptimeMillis();
            tickBuzz(a, .8f);
            PixelStackSound.play(PixelStackSound.SEND, .5f, 1f);
            new Thread(() -> {
                final android.graphics.Bitmap shown = card;
                final boolean ok = card != null && PixelStackShare.post(share, card, sc, who);
                post(() -> {
                    shareCard = shown; shareState = ok ? 2 : 3; shareAt0 = SystemClock.uptimeMillis();
                    if (ok) {
                        PixelStackSound.play(PixelStackSound.SUCCESS, .6f, 1f);
                        sparkle(getWidth() / 2f, getHeight() * .55f, getWidth() * .5f, 26);
                        buzz(a, new int[]{3, 7, 7}, new float[]{.6f, .8f, 1f}, new int[]{0, 60, 60}, new long[]{0, 20, 60, 20, 60, 20}, new int[]{0, 160, 0, 200, 0, 255});
                        postDelayed(() -> PixelStackShare.openSection(a, share), 1100);
                    } else { buzz(a, new int[]{6}, new float[]{.8f}, new int[]{0}, new long[]{0, 60}, new int[]{0, 140}); PixelStackSound.play(PixelStackSound.FAIL, .5f, 1f); }
                });
            }, "PixelStackShare").start();
        }

        private void drawShare(Canvas c, long now, float fade) {
            if (share == null || score <= 0) { shareAt.setEmpty(); barAt.setEmpty(); hideField(); return; }
            int W = getWidth(), H = getHeight();
            float k = ease((since() - 900) / 450f) * fade;
            if (k <= 0) { shareAt.setEmpty(); barAt.setEmpty(); hideField(); return; }
            long st = now - shareAt0;
            if (player == null) player = PixelStackShare.player(a);
            boolean signed = PixelStackShare.signedIn(a), typing = !signed && field != null && state == OVER;
            boolean busy = shareState == 1 || shareState == 2;
            if (field != null && imeShown()) imeSeenAt = now;
            float bw = Math.min(W - 40 * d, 336 * d), bh = 60 * d;
            float home = H - BAR_UP * d + (1 - spring((since() - 900) / 600f)) * 40 * d;
            float up = H * .17f + (newBest && score > 0 ? 84 : 48) * d + bh / 2;
            float fk = typing ? fieldNow(now) : 0f;
            if (!typing) { fieldK = fieldFrom = 0f; fieldUp = false; }
            float cy = home + (up - home) * fk;
            float mid = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, fieldUp ? fk : 1f - fk)));
            float swell = 1f + .04f * mid, tilt = (fieldUp ? -3f : 3f) * mid;
            barAt.set(W / 2f - bw / 2, cy - bh / 2, W / 2f + bw / 2, cy + bh / 2);
            float press = shareState == 1 ? 1 - .04f * (float) Math.sin(Math.min(1f, st / 160f) * Math.PI) : 1f;
            int save = c.save(); c.rotate(tilt, barAt.centerX(), barAt.centerY()); c.scale(swell * press, swell * press, barAt.centerX(), barAt.centerY());
            boolean night = dark || black, focused = field != null && field.hasFocus();
            int accent = night ? LIGHT : BLUE, onAccent = night ? 0xFF0B0D14 : 0xFFFFFFFF;
            fill.setShader(null);
            if (!night) { fill.setColor(alpha(0x16000000, k)); box.set(barAt); box.offset(0, 2 * d); c.drawRoundRect(box, bh / 2, bh / 2, fill); }
            fill.setColor(alpha(night ? 0xFF161A24 : 0xFFFFFFFF, k));
            c.drawRoundRect(barAt, bh / 2, bh / 2, fill);
            stroke.setStrokeCap(Paint.Cap.BUTT); stroke.setStrokeJoin(Paint.Join.MITER);
            stroke.setStrokeWidth((focused ? 1.8f : 1.2f) * d);
            stroke.setColor(alpha(focused ? accent : (night ? 0x40FFFFFF : 0x22000000), k));
            c.drawRoundRect(barAt, bh / 2, bh / 2, stroke);
            if (mid > .02f) {
                float gx = barAt.left + barAt.width() * (fieldUp ? fk : 1f - fk);
                int ga = Math.round(0x66 * mid * k);
                fill.setShader(new android.graphics.RadialGradient(gx, barAt.top, 60 * d, (accent & 0xFFFFFF) | (ga << 24), accent & 0xFFFFFF, android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(barAt, bh / 2, bh / 2, fill); fill.setShader(null);
            }
            boolean rtl = I18n.rtl();
            String name = signed ? player : (field == null ? "" : PixelStackShare.noEmoji(field.getText()).trim());
            float r = 20 * d, by = barAt.centerY(), bx = rtl ? barAt.right - 10 * d - r : barAt.left + 10 * d + r;
            fill.setShader(new android.graphics.LinearGradient(bx - r, by - r, bx + r, by + r, BLUE, 0xFF4F8DF5, android.graphics.Shader.TileMode.CLAMP));
            fill.setAlpha(Math.round(255 * k)); c.drawCircle(bx, by, r, fill); fill.setShader(null);
            if (name.length() > 0) {
                String first = new String(Character.toChars(name.codePointAt(0))).toUpperCase(java.util.Locale.ROOT);
                text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); text.setTextSize(sp(17)); text.setColor(alpha(0xFFFFFFFF, k));
                c.drawText(first, bx, by - (text.descent() + text.ascent()) / 2, text);
            } else {
                fill.setColor(alpha(0xFFFFFFFF, k * .95f));
                c.drawCircle(bx, by - 4.5f * d, 5.5f * d, fill);
                box.set(bx - 9.5f * d, by + 3 * d, bx + 9.5f * d, by + 17 * d);
                c.drawRoundRect(box, 8 * d, 8 * d, fill);
            }
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); text.setTextSize(sp(15));
            String label = shareState == 1 ? I18n.t("Sharing…") : shareState == 2 ? I18n.t("Shared!") : I18n.t("Share");
            float want = Math.max(96 * d, Math.min(text.measureText(label) + 54 * d, bw * .5f));
            shareW = shareW <= 0 || !motion ? want : shareW + (want - shareW) * .22f;
            float bth = bh - 14 * d;
            if (rtl) shareAt.set(barAt.left + 7 * d, by - bth / 2, barAt.left + 7 * d + shareW, by + bth / 2);
            else shareAt.set(barAt.right - 7 * d - shareW, by - bth / 2, barAt.right - 7 * d, by + bth / 2);
            fill.setColor(alpha(accent, k)); c.drawRoundRect(shareAt, bth / 2, bth / 2, fill);
            float ix = rtl ? shareAt.right - 22 * d : shareAt.left + 22 * d, iy = by;
            stroke.setColor(alpha(onAccent, k)); stroke.setStrokeWidth(2 * d); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
            if (shareState == 1) { box.set(ix - 7 * d, iy - 7 * d, ix + 7 * d, iy + 7 * d); c.drawArc(box, (st / 2.2f) % 360, 260, false, stroke); }
            else if (shareState == 2) { android.graphics.Path pth = new android.graphics.Path(); pth.moveTo(ix - 7 * d, iy); pth.lineTo(ix - 2 * d, iy + 5 * d); pth.lineTo(ix + 7 * d, iy - 6 * d); c.drawPath(pth, stroke); }
            else {
                int ps = c.save(); if (rtl) c.scale(-1, 1, ix, iy);
                android.graphics.Path pth = new android.graphics.Path(); pth.moveTo(ix - 8 * d, iy - 1 * d); pth.lineTo(ix + 8 * d, iy - 7 * d); pth.lineTo(ix + 2 * d, iy + 8 * d); pth.lineTo(ix - 1 * d, iy + 2 * d); pth.close();
                c.drawPath(pth, stroke); c.drawLine(ix - 1 * d, iy + 2 * d, ix + 8 * d, iy - 7 * d, stroke);
                c.restoreToCount(ps);
            }
            text.setColor(alpha(onAccent, k));
            float lw = shareAt.width() - 44 * d, lx = rtl ? shareAt.left + 10 * d + lw / 2 : shareAt.left + 34 * d + lw / 2;
            c.drawText(fit(label, lw), lx, by - (text.descent() + text.ascent()) / 2, text);
            float ns = rtl ? bx - r - 12 * d : bx + r + 12 * d, ne = rtl ? shareAt.right + 10 * d : shareAt.left - 10 * d, room = Math.abs(ne - ns);
            boolean focused2 = focused && typing && !busy;
            if (name.length() > 0 || signed) {
                text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); text.setTextSize(sp(15)); text.setColor(alpha(ink, k));
            } else {
                text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); text.setTextSize(sp(15)); text.setColor(alpha(soft, k * 1.6f));
            }
            String whole = name.length() > 0 ? name : signed ? player : I18n.t("Your name");
            String line = focused2 && name.length() > 0 ? fitEnd(whole, room - 6 * d) : fit(whole, room);
            float base = by - (text.descent() + text.ascent()) / 2;
            text.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
            c.drawText(line, ns, base, text);
            if (typing && !busy) {
                float tw = text.measureText(line);
                float x = name.length() > 0 ? (rtl ? ns - tw - 2 * d : ns + tw + 2 * d) : (rtl ? ns + 3 * d : ns - 3 * d);
                float blink = (float) (.5 + .5 * Math.cos(now / (focused ? 260.0 : 380.0)));
                blink = blink * blink * (3 - 2 * blink);
                fill.setColor(alpha(accent, k * (focused ? .2f + .8f * blink : .12f + .4f * blink)));
                float ch = 20 * d; box.set(x - 1 * d, by - ch / 2, x + 1 * d, by + ch / 2);
                c.drawRoundRect(box, 1 * d, 1 * d, fill);
            }
            text.setTextAlign(Paint.Align.CENTER);
            c.restoreToCount(save);
            if (typing && !busy) {
                field.setVisibility(View.VISIBLE); field.setAlpha(1f);
                float fl = rtl ? ne : barAt.left, fr = rtl ? barAt.right : ne;
                int fw = Math.round(fr - fl), fh = Math.round(bh);
                android.view.ViewGroup.LayoutParams lp = field.getLayoutParams();
                if (lp != null && (lp.width != fw || lp.height != fh)) { lp.width = fw; lp.height = fh; field.setLayoutParams(lp); }
                if (rtl) field.setPadding(0, 0, Math.round(barAt.right - ns), 0); else field.setPadding(Math.round(ns - barAt.left), 0, 0, 0);
                field.setTranslationX(fl); field.setTranslationY(barAt.top);
            } else hideField();
            if (shareState == 3 && st < 2600) {
                text.setTextSize(sp(13)); text.setColor(alpha(soft, fade * 1.6f * (st < 2200 ? 1 : 1 - (st - 2200) / 400f)));
                c.drawText(I18n.t("Couldn't share. Try again."), W / 2f, barAt.top - 12 * d, text);
            }
            if (shareState == 1 || (shareState == 2 && st < 900)) {
                float f = shareState == 1 ? Math.min(1f, st / 500f) : 1f;
                float cw = W * .62f, ch = cw * 1.25f, cx = W / 2f, ccy = H * .48f;
                float fly = shareState == 2 ? easeIn(st / 900f) : 0f;
                float sc = .6f + .4f * spring(f) - .5f * fly;
                box.set(cx - cw / 2 * sc, ccy - ch / 2 * sc - fly * H * .7f, cx + cw / 2 * sc, ccy + ch / 2 * sc - fly * H * .7f);
                int sv = c.save(); c.rotate(-6 * fly + 3 * (float) Math.sin(st / 180.0) * (1 - fly), box.centerX(), box.centerY());
                float op = f * (1 - fly);
                if (shareCard != null && !shareCard.isRecycled()) {
                    fill.setShader(null); fill.setColor(alpha(0x55000000, op * .6f));
                    RectF sh = new RectF(box); sh.offset(0, 6 * d); c.drawRoundRect(sh, 18 * d, 18 * d, fill);
                    android.graphics.Path clip = new android.graphics.Path(); clip.addRoundRect(box, 18 * d, 18 * d, android.graphics.Path.Direction.CW);
                    int cs = c.save(); c.clipPath(clip);
                    Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG); bp.setAlpha(Math.round(255 * op));
                    c.drawBitmap(shareCard, null, box, bp);
                    c.restoreToCount(cs);
                    stroke.setColor(alpha(0x33FFFFFF, op)); stroke.setStrokeWidth(1.2f * d); c.drawRoundRect(box, 18 * d, 18 * d, stroke);
                } else {
                    fill.setColor(alpha(dark || black ? 0xFF0B0D14 : 0xFFF4F6FC, op)); c.drawRoundRect(box, 18 * d, 18 * d, fill);
                    text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); text.setTextSize(sp(56) * sc); text.setColor(alpha(dark || black ? LIGHT : BLUE, op));
                    c.drawText(String.valueOf(score), box.centerX(), box.centerY() + sp(20) * sc, text);
                }
                c.restoreToCount(sv);
            }
        }

        private void hideField() {
            if (field == null) return;
            if (field.hasFocus()) closeKeyboard();
            if (field.getVisibility() != View.GONE) field.setVisibility(View.GONE);
        }

        private String fitEnd(String t, float w) {
            if (t == null) return "";
            if (w <= 0 || text.measureText(t) <= w) return t;
            float dots = text.measureText("…");
            int i = 0;
            while (i < t.length() && dots + text.measureText(t, i, t.length()) > w) i = t.offsetByCodePoints(i, 1);
            return "…" + t.substring(i);
        }

        private String fit(String t, float w) {
            if (t == null) return "";
            if (w <= 0 || text.measureText(t) <= w) return t;
            String dots = "…";
            int n = t.length();
            while (n > 0 && text.measureText(t, 0, n) + text.measureText(dots) > w) n = t.offsetByCodePoints(n, -1);
            return t.substring(0, n).trim() + dots;
        }

        private void restart() {
            shareState = 0;
            float cx = getWidth() / 2f;
            for (int i = bits.size() - 1; i >= 0; i--) bits.remove(i);
            for (Block b : blocks) {
                float y = baseY - (b.level + 1) * bh;
                float l = cx + (b.l - cx) * zoom, r = cx + (b.r - cx) * zoom, t = baseY + (y - baseY) * zoom + cam, bt = baseY + (y + bh - baseY) * zoom + cam;
                Bit p = piece(l, t, r, bt, blockColour(b.level), (rnd.nextFloat() - .5f) * 160 * d);
                p.vy = -(rnd.nextFloat() * 200) * d; bits.add(p);
            }
            tickBuzz(a, .7f);
            PixelStackSound.play(PixelStackSound.CHIP, .5f, .7f);
            begin();
        }

        void exit() {
            if (state == EXIT) return;
            homeAt();
            go(EXIT);
            tickBuzz(a, .5f);
            PixelStackSound.play(PixelStackSound.SOFT, .3f, 1.12f); postDelayed(() -> PixelStackSound.play(PixelStackSound.SOFT, .26f, .9f), 120);
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(.034f, (now - last) / 1000f); last = now;
            int W = getWidth(), H = getHeight();
            float t = since();
            if (field != null && state != OVER && field.getVisibility() == View.VISIBLE) { if (field.hasFocus()) closeKeyboard(); field.setVisibility(View.GONE); }

            float shown = state == INTRO ? ease(t / 320f) : state == EXIT ? 1 - ease((t - 80) / 420f) : 1f;
            fill.setShader(null);
            fill.setColor(alpha(black ? 0xFF000000 : page, shown));
            c.drawRect(0, 0, W, H, fill);

            if (state == INTRO && !introSound) { introSound = true; PixelStackSound.play(PixelStackSound.SOFT, .34f, 1f); postDelayed(() -> PixelStackSound.play(PixelStackSound.SOFT, .3f, 1.26f), 110); }
            if (state == INTRO && popAt != null && t < 420) drawPop(c, t);
            if (state == INTRO && t > 820) { go(READY); t = 0; homeAt(); }
            if (state == INTRO || state == READY) drawLogo(c, state == INTRO ? t : 99999, 1f);
            if (state == READY) drawTitle(c, t, 1f);
            if (state == MERGE) {
                drawTitle(c, 99999, 1 - ease(t / 180f));
                drawMerge(c, motion ? t : 99999);
                if (t >= (motion ? MERGE_MS : 0)) begin();
            }
            if (state == PLAY || state == OVER || (state == EXIT && !blocks.isEmpty())) {
                step(dt);
                float fade = state == EXIT ? 1 - ease(t / 160f) : 1f;
                float towerFade = fade;
                if (state == OVER && since() >= GO_AT && blocks.size() > 0) { if (cells == null) buildCells(); towerFade = fade * (1 - ease((since() - GO_AT) / 220f)); }
                if (state == EXIT && cells != null) towerFade = 0;
                if (towerFade > 0) drawTower(c, now, towerFade);
                if (cells != null) drawCells(c, now, fade);
                drawScore(c, now, fade);
                if (state == OVER || state == EXIT) drawShare(c, now, fade);
            }
            if (state == REFORM) {
                drawCells(c, now, 1f);
                if (t >= REFORM_MS + 260) { cells = null; begin(); flashAt = SystemClock.uptimeMillis(); PixelStackSound.play(PixelStackSound.THUD, .7f, 1f); buzz(a, new int[]{2, 7}, new float[]{.8f, .5f}, new int[]{0, 30}, new long[]{0, 45}, new int[]{0, 200}); }
            }
            drawBits(c, dt);
            drawPops(c, now);
            if (state == EXIT) {
                drawHome(c, t);
                if (!landedHome && t >= HOME_MS * .78f) { landedHome = true; if (onGone != null) onGone.run(); }
                if (t >= HOME_MS) { finish(); return; }
            }
            postInvalidateOnAnimation();
        }

        static final float HOME_MS = 660;
        private boolean landedHome;
        private void finish() {
            try { if (dialog != null) dialog.dismiss(); } catch (Throwable ignored) { }
            if (!landedHome && onGone != null) { landedHome = true; onGone.run(); }
        }

        private void step(float dt) {
            if (state == PLAY) {
                curL += dir * speed * dt; curR += dir * speed * dt;
                float w = curR - curL, min = -w * .3f, max = getWidth() - w * .7f;
                if (curL > max) { curL = max; curR = curL + w; dir = -1; }
                if (curL < min) { curL = min; curR = curL + w; dir = 1; }
            }
            if (state == OVER) {
                float k = ease((since() - 350) / 900f);
                float tower = (blocks.size() + 1) * bh, room = baseY - (getHeight() * .17f + 84 * d);
                float fit = tower > room ? room / tower : 1f;
                zoom = 1f + (fit - 1f) * k;
                camTarget = 0;
            }
            cam += (camTarget - cam) * Math.min(1f, dt * (motion ? 7f : 60f));
        }

        private void block(Canvas c, float l, float t, float r, float b, int colour, float alphaF) {
            box.set(l, t, r, b);
            float rad = Math.min(8 * d, (b - t) / 2f);
            fill.setShader(null); fill.setColor(alpha(colour, alphaF)); c.drawRoundRect(box, rad, rad, fill);
            box.set(l + 2 * d, t + 2 * d, r - 2 * d, t + Math.max(3 * d, (b - t) * .34f));
            if (box.width() > 0) { fill.setColor(alpha(0x55FFFFFF, alphaF)); float rr = Math.min(rad - 2 * d, box.height() / 2f); c.drawRoundRect(box, Math.max(0, rr), Math.max(0, rr), fill); }
            if (black || dark) {
                box.set(l, t, r, b); stroke.setStrokeWidth(1.2f * d); stroke.setColor(alpha(0x40FFFFFF, alphaF)); c.drawRoundRect(box, rad, rad, stroke);
            }
        }

        private void drawTower(Canvas c, long now, float fade) {
            int save = c.save();
            float shake = 0;
            long sk = now - shakeAt;
            if (sk < 420 && motion) shake = (float) Math.sin(sk / 22.0) * 7 * d * (1 - sk / 420f);
            c.translate(shake, cam);
            c.scale(zoom, zoom, getWidth() / 2f, baseY);
            long fl = now - flashAt;
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                float y = baseY - (b.level + 1) * bh;
                int colour = blockColour(b.level);
                if (i == blocks.size() - 1 && fl < 260) colour = mix(colour, 0xFFFFFFFF, 1 - fl / 260f);
                block(c, b.l, y + 1.5f * d, b.r, y + bh - 1.5f * d, colour, fade);
            }
            if (fl < 420) {
                Block b = blocks.get(blocks.size() - 1);
                float y = baseY - (b.level + 1) * bh, k = ease(fl / 420f), grow = 14 * d * k;
                box.set(b.l - grow, y - grow / 2, b.r + grow, y + bh + grow / 2);
                stroke.setStrokeWidth(2.5f * d * (1 - k)); stroke.setColor(alpha(LIGHT, (1 - k) * fade));
                c.drawRoundRect(box, 10 * d, 10 * d, stroke);
            }
            if (state == PLAY) {
                int level = blocks.size();
                float y = baseY - (level + 1) * bh;
                int colour = blockColour(level);
                if ((curR - curL) / Math.max(1f, baseW) < .2f) colour = mix(colour, 0xFFFFFFFF, .28f * (float) (.5 + .5 * Math.sin(now / 110.0)));
                block(c, curL, y + 1.5f * d, curR, y + bh - 1.5f * d, colour, fade);
            }
            c.restoreToCount(save);
        }

        private void drawScore(Canvas c, long now, float fade) {
            int W = getWidth(), H = getHeight();
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setTextSize(sp(68)); text.setColor(alpha(dark || black ? 0xFFFFFFFF : 0xFF14161C, fade));
            float y = H * .17f;
            c.drawText(String.valueOf(score), W / 2f, y, text);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setTextSize(sp(15)); text.setColor(alpha(ink, fade * .8f));
            c.drawText(I18n.t("Best") + "  " + best, W / 2f, y + 28 * d, text);
            if (state == OVER) {
                float k = ease((since() - 500) / 400f);
                if (newBest && score > 0) {
                    float pop = 1 + .15f * (1 - spring((since() - 400) / 500f)) ;
                    text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                    text.setTextSize(sp(22) * Math.max(.01f, pop)); text.setColor(alpha(dark || black ? LIGHT : BLUE, k * fade));
                    c.drawText(I18n.t("New best!"), W / 2f, y + 64 * d, text);
                }
                float pulse = .55f + .45f * (float) (.5 + .5 * Math.cos(now / 300.0));
                text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                text.setTextSize(sp(16)); text.setColor(alpha(ink, k * pulse * fade));
                c.drawText(I18n.t("Tap to play again"), W / 2f, H - 64 * d, text);
            } else if (score == 0) {
                float pulse = .55f + .45f * (float) (.5 + .5 * Math.cos(now / 300.0));
                text.setTextSize(sp(16)); text.setColor(alpha(ink, pulse * fade));
                c.drawText(I18n.t("Tap to drop"), W / 2f, H - 64 * d, text);
            }
        }

        private float[] dance(int k, long t) {
            final float period = 1700f;
            float u = ((t + k * 425) % (long) period) / period;
            float lift = 0, turn = 0, sx = 1, sy = 1;
            float air = (u - .12f) / .5f;
            boolean flying = air > 0 && air < 1;
            if (u < .12f) { float q = (float) Math.sin(Math.PI * u / .12f); sx = 1 + .22f * q; sy = 1 - .26f * q; }
            else if (u > .62f && u < .74f) { float q = (float) Math.sin(Math.PI * (u - .62f) / .12f); sx = 1 + .2f * q; sy = 1 - .22f * q; }
            switch (k) {
                case 0:
                    if (flying) { lift = .55f * (float) Math.sin(Math.PI * air); turn = 360 * ease(air); }
                    break;
                case 1:
                    if (flying) { lift = .4f * (float) Math.sin(Math.PI * air); sx = (float) Math.cos(2 * Math.PI * air); if (Math.abs(sx) < .06f) sx = .06f; }
                    break;
                case 2:
                    if (flying) { float q = (float) Math.sin(Math.PI * air); lift = .2f * q; float z = air < .45f ? 1 - .45f * ease(air / .45f) : .55f + .65f * spring((air - .45f) / .55f); sx = sy = z; }
                    break;
                default:
                    if (flying) { lift = .9f * (float) Math.sin(Math.PI * air); turn = -720 * ease(air); sy = 1 + .18f * (float) Math.sin(Math.PI * air); sx = 1 - .1f * (float) Math.sin(Math.PI * air); }
            }
            return new float[]{lift, turn, sx, sy};
        }

        private void drawLogo(Canvas c, float t, float fade) {
            for (int k = 0; k < 4; k++) {
                float delay = k * 45f, p = motion ? Math.max(0f, Math.min(1f, (t - delay) / 620f)) : 1f;
                float m = spring(p), turn = (1 - ease(p)) * (k % 2 == 0 ? 220f : -220f);
                RectF s0 = logoAt[k], s1 = bigAt[k];
                float cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m;
                float cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m - (float) Math.sin(Math.PI * ease(p)) * 60 * d;
                float size = s0.width() + (s1.width() - s0.width()) * m;
                int from = (k % 2) == (k / 2) ? ink : soft, to = (k % 2) == (k / 2) ? BLUE : LIGHT;
                int colour = mix(from, to, ease(p * 1.4f));
                float sx = 1f, sy = 1f;
                if (p >= 1f && motion) {
                    float[] m4 = dance(k, SystemClock.uptimeMillis() - stateAt);
                    cy -= m4[0] * size; turn += m4[1]; sx = m4[2]; sy = m4[3];
                }
                int save = c.save(); c.rotate(turn, cx, cy); c.scale(sx, sy, cx, cy + size / 2 * (sy < 1 ? 1 : 0));
                block(c, cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, colour, fade);
                c.restoreToCount(save);
            }
        }

        private void drawTitle(Canvas c, float t, float fade) {
            int W = getWidth(), H = getHeight();
            float k = ease((t - 200) / 380f) * fade;
            if (k <= 0) return;
            float lift = (1 - k) * 14 * d;
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setTextSize(sp(30)); text.setColor(alpha(ink, k));
            float y = H * .4f + introSize / 2f + 58 * d + lift;
            c.drawText(I18n.t("Pixel Stack"), W / 2f, y, text);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setTextSize(sp(15)); text.setColor(alpha(soft, k * 1.6f));
            c.drawText(I18n.t("Best") + "  " + best, W / 2f, y + 28 * d, text);
            float pulse = .5f + .5f * (float) (.5 + .5 * Math.cos(SystemClock.uptimeMillis() / 300.0));
            text.setTextSize(sp(16)); text.setColor(alpha(ink, k * pulse));
            c.drawText(I18n.t("Tap to start"), W / 2f, H - 64 * d, text);
        }

        static final float MERGE_MS = 980;
        private boolean landed;
        private void drawMerge(Canvas c, float t) {
            float l = getWidth() / 2f - baseW / 2f, q = baseW / 4f, y = baseY - bh;
            if (t < 30) landed = false;
            for (int i = 0; i < 4; i++) {
                RectF s0 = bigAt[i];
                float size = s0.width(), cx0 = s0.centerX(), cy0 = s0.centerY();
                float tl = l + i * q, tr = tl + q;
                float cx = cx0, cy = cy0, w = size, h = size, turn = 0; int colour = (i % 2) == (i / 2) ? BLUE : LIGHT;
                float delay = i * 35f, tt = t - delay;
                if (tt < 140) {
                    float k = (float) Math.sin(Math.PI / 2 * Math.max(0, tt) / 140f);
                    w = size * (1 + .25f * k); h = size * (1 - .3f * k); cy = cy0 + (size - h) / 2;
                } else if (tt < 460) {
                    float k = ease((tt - 140) / 320f);
                    cy = cy0 - 150 * d * k; cx = cx0 + (cx0 - getWidth() / 2f) * .5f * k;
                    turn = (i % 2 == 0 ? 1 : -1) * 360 * k * (i == 3 ? 2 : 1);
                    colour = mix(colour, 0xFFFFFFFF, (float) Math.sin(Math.PI * k) * .7f);
                    w = h = size * (1 - .15f * (float) Math.sin(Math.PI * k));
                } else if (tt < 760) {
                    float k = easeIn((tt - 460) / 300f);
                    float sx0 = cx0 + (cx0 - getWidth() / 2f) * .5f, sy0 = cy0 - 150 * d;
                    float ex = (tl + tr) / 2f, ey = y + bh / 2f;
                    cx = sx0 + (ex - sx0) * k; cy = sy0 + (ey - sy0) * k;
                    w = size + (q - size) * k; h = (size + (bh - 3 * d - size) * k) * (1 + .3f * (float) Math.sin(Math.PI * k));
                    colour = mix(colour, blockColour(0), k);
                } else {
                    float k = (tt - 760) / (MERGE_MS - 760 - 3 * 35f);
                    float sq = (float) Math.sin(Math.PI * Math.min(1f, k * 1.6f)) * (1 - Math.min(1f, k));
                    cx = (tl + tr) / 2f; w = q * (1 + .06f * sq) + 2 * d * ease(k); h = (bh - 3 * d) * (1 - .3f * sq); cy = y + bh - 1.5f * d - h / 2;
                    colour = mix(blockColour(0), 0xFFFFFFFF, .5f * (1 - ease(k)));
                }
                int save = c.save(); c.rotate(turn, cx, cy);
                block(c, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, colour, 1f);
                c.restoreToCount(save);
            }
            if (!landed && t >= 760 + 3 * 35f) {
                landed = true;
                PixelStackSound.play(PixelStackSound.THUD, .75f, 1f);
                buzz(a, new int[]{2, 7}, new float[]{1f, .6f}, new int[]{0, 30}, new long[]{0, 55}, new int[]{0, 230});
                for (int i = 0; i < 18; i++) {
                    Bit p = new Bit(); float s = (3 + rnd.nextFloat() * 4) * d, x = l + rnd.nextFloat() * baseW;
                    p.l = x - s / 2; p.r = x + s / 2; p.t = baseY - s; p.b = baseY; p.square = true;
                    p.vx = (x - getWidth() / 2f) * 2.2f + (rnd.nextFloat() - .5f) * 120 * d; p.vy = -(120 + rnd.nextFloat() * 220) * d;
                    p.vr = (rnd.nextFloat() - .5f) * 720; p.life = 1f; p.colour = rnd.nextBoolean() ? LIGHT : BLUE; bits.add(p);
                }
            }
        }

        private void drawHome(Canvas c, float t) {
            for (int k = 0; k < 4; k++) {
                float p = motion ? Math.max(0f, Math.min(1f, (t - k * 30f) / (HOME_MS - 90f))) : 1f;
                float m = p < .5f ? 4 * p * p * p : 1 - (float) Math.pow(-2 * p + 2, 3) / 2;
                RectF s0 = bigAt[k], s1 = logoAt[k];
                float cx = s0.centerX() + (s1.centerX() - s0.centerX()) * m, cy = s0.centerY() + (s1.centerY() - s0.centerY()) * m;
                float size = s0.width() + (s1.width() - s0.width()) * m;
                int to = (k % 2) == (k / 2) ? ink : soft, from = (k % 2) == (k / 2) ? BLUE : LIGHT;
                int save = c.save(); c.rotate((k % 2 == 0 ? 1 : -1) * 180 * ease(p), cx, cy);
                block(c, cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, mix(from, to, ease(p)), p < .8f ? 1f : Math.max(0f, 1f - ease((p - .8f) / .2f)));
                c.restoreToCount(save);
            }
        }

        private void drawBits(Canvas c, float dt) {
            float g = 2300 * d;
            for (int i = bits.size() - 1; i >= 0; i--) {
                Bit p = bits.get(i);
                p.vy += g * dt; p.l += p.vx * dt; p.r += p.vx * dt; p.t += p.vy * dt; p.b += p.vy * dt; p.rot += p.vr * dt;
                if (p.square) p.life -= dt * .9f;
                float sy = baseY + (p.t - baseY) * zoom + cam;
                if (sy > getHeight() + 200 * d || p.life <= 0) { bits.remove(i); continue; }
                int save = c.save();
                c.translate(0, cam); c.scale(zoom, zoom, getWidth() / 2f, baseY);
                c.rotate(p.rot, (p.l + p.r) / 2f, (p.t + p.b) / 2f);
                block(c, p.l, p.t, p.r, p.b, p.colour, Math.min(1f, p.life * 1.5f));
                c.restoreToCount(save);
            }
        }

        private void drawPops(Canvas c, long now) {
            for (int i = pops.size() - 1; i >= 0; i--) {
                Pop p = pops.get(i);
                float t = (now - p.at) / 900f;
                if (t >= 1) { pops.remove(i); continue; }
                float k = spring(Math.min(1f, t * 2.2f)), fade = 1 - easeIn(t);
                text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                text.setTextSize(sp(p.size) * (.6f + .4f * k));
                text.setColor(alpha(dark || black ? p.colour : BLUE, fade));
                c.drawText(p.text, p.x, p.y + cam - 28 * d * ease(t), text);
            }
        }
    }
}
