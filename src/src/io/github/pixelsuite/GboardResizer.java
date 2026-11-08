package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class GboardResizer {
    private GboardResizer() {}

    interface Saver { void save(float w, float cx, float h, int up); }

    static final float MIN_W = 0.6f, MIN_H = 0.75f, MAX_H = 1.35f;
    static final int DOWN = 0, MAX_UP = 260;
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    static void open(final Activity a, float w, float cx, float h, int up, boolean dark, int bg, int card, int text, int muted, final int accent, final Saver saver, final Runnable closed) {
        final float d = a.getResources().getDisplayMetrics().density;
        final boolean black = dark && SuperDark.inEffect(a);
        final PageDialog dialog = new PageDialog(a, dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        Window win = dialog.getWindow();
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Math.round(8 * d), Math.round(12 * d), Math.round(12 * d), Math.round(8 * d));
        TextView back = new TextView(a);
        back.setText(I18n.cs("←"));
        back.setTextSize(26);
        back.setTextColor(text);
        back.setPadding(Math.round(12 * d), 0, Math.round(16 * d), 0);
        back.setContentDescription(I18n.cs("Back"));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dialog.close(); } });
        top.addView(back);
        TextView title = new TextView(a);
        title.setText(I18n.cs("Resize"));
        title.setTextSize(22);
        title.setTextColor(text);
        title.setTypeface(I18n.medium());
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Math.round(16 * d), 0, Math.round(16 * d), 0);
        TextView tip = new TextView(a);
        tip.setText(I18n.cs("Drag the handles to resize, or the middle to move it. Apply saves; Reset goes back to Gboard's size. Portrait only."));
        tip.setTextSize(14);
        tip.setTextColor(muted);
        tip.setLineSpacing(3 * d, 1);
        tip.setPadding(Math.round(18 * d), Math.round(14 * d), Math.round(18 * d), Math.round(14 * d));
        GradientDrawable tipBg = new GradientDrawable();
        tipBg.setCornerRadius(20 * d);
        tipBg.setColor(card);
        tip.setBackground(tipBg);
        body.addView(tip, new LinearLayout.LayoutParams(-1, -2));

        final EditText field = new EditText(a);
        field.setHint(I18n.cs("Try typing here after Apply"));
        field.setTextSize(16);
        field.setTextColor(text);
        field.setHintTextColor(muted);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setPadding(Math.round(18 * d), Math.round(12 * d), Math.round(18 * d), Math.round(12 * d));
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setCornerRadius(24 * d);
        fieldBg.setColor(card);
        field.setBackground(fieldBg);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Math.round(12 * d);
        body.addView(field, flp);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        final TextView reset = button(a, "Reset", false, text, muted, accent, d);
        final TextView apply = button(a, "Apply", true, text, muted, accent, d);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Math.round(48 * d), 1);
        rp.rightMargin = Math.round(12 * d);
        buttons.addView(reset, rp);
        buttons.addView(apply, new LinearLayout.LayoutParams(0, Math.round(48 * d), 1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Math.round(12 * d);
        body.addView(buttons, blp);
        root.addView(body, new LinearLayout.LayoutParams(-1, -2));

        final Stage stage = new Stage(a, w, cx, h, up, dark, black, text, muted, accent);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        reset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            stage.animateTo(1f, 0.5f, 1f, 0);
            saver.save(1f, 0.5f, 1f, 0);
            haptic(v);
        }});
        apply.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            saver.save(stage.w, stage.cx, stage.h, Math.round(stage.up));
            haptic(v);
        }});
        field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { stage.animate().alpha(has ? 0f : 1f).setDuration(200).start(); }
        });
        stage.imeGone = new Runnable() { public void run() { if (field.hasFocus()) field.clearFocus(); } };
        stage.tapped = new Runnable() { public void run() {
            if (!field.hasFocus()) return;
            field.clearFocus();
            try { ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(), 0); } catch (Throwable ignored) { }
        }};
        root.setFocusableInTouchMode(true);

        dialog.setContentView(root);
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface di) { if (closed != null) closed.run(); }
        });
        if (win != null) {
            win.setLayout(-1, -1);
            win.setBackgroundDrawable(new ColorDrawable(bg));
            win.setStatusBarColor(bg);
            win.setNavigationBarColor(bg);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (!dark) win.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x10);
        }
        dialog.animate(root, bg);
        dialog.show();
        root.requestFocus();
    }

    private static void haptic(View v) { try { v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); } catch (Throwable ignored) { } }

    private static TextView button(Context c, String s, boolean filled, int text, int muted, int accent, float d) {
        TextView b = new TextView(c);
        b.setText(I18n.cs(s));
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(I18n.medium());
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(24 * d);
        if (filled) { g.setColor(accent); b.setTextColor(onColor(accent)); }
        else { g.setColor(0); g.setStroke(Math.max(1, Math.round(d)), Color.argb(120, Color.red(muted), Color.green(muted), Color.blue(muted))); b.setTextColor(text); }
        b.setBackground(g);
        b.setClickable(true);
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(EASE).start();
                return false;
            }
        });
        return b;
    }

    private static int onColor(int c) { return (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 150 ? 0xFF000000 : 0xFFFFFFFF; }

    static final class Stage extends View {
        final float d;
        final boolean dark, black;
        final int text, muted, accent;
        final int bgKb, keyFace, fnFace, keyInk, shade, action, chipBg;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), dash = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG);
        float w, cx, h, up;
        Runnable imeGone, tapped;
        int fullH;
        int active;
        float downX, downY, startW, startCx, startH, startUp, startLeft, startRight, startTop;
        boolean snappedX, snappedUp, snappedW, snappedH, moved;
        final GboardBarEditor.Spring[] glow = { new GboardBarEditor.Spring(), new GboardBarEditor.Spring(), new GboardBarEditor.Spring(), new GboardBarEditor.Spring(), new GboardBarEditor.Spring() };
        final GboardBarEditor.Spring enter = new GboardBarEditor.Spring();
        long lastFrame;
        ValueAnimator anim;

        Stage(Context c, float w, float cx, float h, int up, boolean dark, boolean black, int text, int muted, int accent) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            this.dark = dark; this.black = black; this.text = text; this.muted = muted; this.accent = accent;
            this.w = clamp(w, MIN_W, 1f); this.cx = clamp(cx, this.w / 2f, 1f - this.w / 2f); this.h = clamp(h, MIN_H, MAX_H); this.up = clamp(up, -DOWN, MAX_UP);
            bgKb = !dark ? 0xFFEEF0F4 : black ? 0xFF000000 : 0xFF232325;
            keyFace = !dark ? 0xFFFFFFFF : black ? 0xFF2C2C2E : 0xFF464648;
            fnFace = !dark ? 0xFFACB1BA : black ? 0xFF2C2C2E : 0xFF464648;
            keyInk = !dark ? 0xFF000000 : 0xFFFFFFFF;
            shade = !dark ? 0x59898A8D : 0;
            action = !dark ? 0xFF007AFF : black ? 0xFF0A78F0 : 0xFF0A84FF;
            chipBg = !dark ? 0xE6FFFFFF : 0xE62C2C2E;
            word.setTextAlign(Paint.Align.CENTER);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(Math.max(1f, 1.2f * d));
            dash.setPathEffect(new DashPathEffect(new float[] { 6 * d, 5 * d }, 0));
            dash.setColor(Color.argb(110, Color.red(muted), Color.green(muted), Color.blue(muted)));
            enter.set(0); enter.t = 1;
            setContentDescription(I18n.cs("Keyboard size and place. Drag the handles to resize, or the keyboard to move it."));
            loadShot();
        }

        static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

        float baseHeader() { return shotBody != null && shotHeadH > 0 ? shotHeadH : 48 * d; }
        float baseBody() { return shotBody != null && shotBodyBase > 0 ? shotBodyBase : 224 * d; }

        Bitmap shotHead, shotBody, atlasHead, atlasBody;
        int[] labelsHead, labelsBody;
        float shotHeadH = -1, shotBodyBase = -1;
        final Paint shotPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Runnable shotChanged = new Runnable() { public void run() { loadShot(); invalidate(); } };

        void loadShot() {
            try {
                Bitmap[] b = KeyboardPreviewProvider.load(getContext());
                float[] m = KeyboardPreviewProvider.meta(getContext());
                if (b[1] == null || m == null) { shotHead = shotBody = atlasHead = atlasBody = null; labelsHead = labelsBody = null; shotHeadH = shotBodyBase = -1; return; }
                shotHead = b[0]; shotBody = b[1];
                Object[] lb = KeyboardPreviewProvider.labels(getContext(), "body"), lh = KeyboardPreviewProvider.labels(getContext(), "header");
                atlasBody = lb == null ? null : (Bitmap) lb[0]; labelsBody = lb == null ? null : (int[]) lb[1];
                atlasHead = lh == null || shotHead == null ? null : (Bitmap) lh[0]; labelsHead = lh == null || shotHead == null ? null : (int[]) lh[1];
                shotHeadH = shotHead != null ? shotHead.getHeight() : m[0] > 0 ? m[0] : 48 * d;
                shotBodyBase = shotBody.getHeight() / m[1];
            } catch (Throwable ignored) { }
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); KeyboardPreviewProvider.LISTENERS.addIfAbsent(shotChanged); }
        @Override protected void onDetachedFromWindow() { KeyboardPreviewProvider.LISTENERS.remove(shotChanged); super.onDetachedFromWindow(); }

        private final android.graphics.Rect labelSrc = new android.graphics.Rect();
        private final RectF labelDst = new RectF();

        private void drawLabels(Canvas c, Bitmap atlas, int[] m, RectF part, int shotW, int shotH) {
            if (atlas == null || m == null || shotW <= 0 || shotH <= 0) return;
            float sx = part.width() / shotW, sy = part.height() / shotH, s = Math.min(1f, Math.min(sx, sy));
            for (int i = 0; i + 5 < m.length; i += 6) {
                float w = m[i + 2] * s, h = m[i + 3] * s;
                float cx = part.left + (m[i] + m[i + 2] / 2f) * sx, cy = part.top + (m[i + 1] + m[i + 3] / 2f) * sy;
                labelSrc.set(m[i + 4], m[i + 5], m[i + 4] + m[i + 2], m[i + 5] + m[i + 3]);
                labelDst.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
                c.drawBitmap(atlas, labelSrc, labelDst, shotPaint);
            }
        }

        private void drawShot(Canvas c, RectF k) {
            float r = 16 * d;
            if (!dark) { fill.setColor(0x22000000); c.drawRoundRect(k.left, k.top + 2 * d, k.right, k.bottom + 2 * d, r, r, fill); }
            int save = c.save();
            Path clip = new Path(); clip.addRoundRect(k, r, r, Path.Direction.CW); c.clipPath(clip);
            float split = k.top + baseHeader();
            RectF headR = new RectF(k.left, k.top, k.right, split), bodyR = new RectF(k.left, split, k.right, k.bottom);
            if (shotHead != null) c.drawBitmap(shotHead, null, headR, shotPaint);
            else { fill.setColor(shotBody.getPixel(Math.min(2, shotBody.getWidth() - 1), Math.min(2, shotBody.getHeight() - 1)) | 0xFF000000); c.drawRect(k.left, k.top, k.right, split + 1, fill); }
            c.drawBitmap(shotBody, null, bodyR, shotPaint);
            if (shotHead != null) drawLabels(c, atlasHead, labelsHead, headR, shotHead.getWidth(), shotHead.getHeight());
            drawLabels(c, atlasBody, labelsBody, bodyR, shotBody.getWidth(), shotBody.getHeight());
            c.restoreToCount(save);
        }
        float baseline() { return getHeight() - DOWN * d; }

        RectF kb() {
            float W = getWidth(), kw = W * w, left = W * cx - kw / 2f;
            float kh = baseHeader() + baseBody() * h, bottom = baseline() - up * d;
            return new RectF(left, bottom - kh, left + kw, bottom);
        }

        float maxUp() { return Math.max(0, Math.min(MAX_UP, (baseline() - (baseHeader() + baseBody() * h) - 12 * d) / d)); }

        void animateTo(final float tw, final float tcx, final float th, final float tup) {
            if (anim != null) anim.cancel();
            final float fw = w, fcx = cx, fh = h, fup = up;
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(380);
            anim.setInterpolator(EASE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    float f = (Float) a.getAnimatedValue();
                    w = fw + (tw - fw) * f; cx = fcx + (tcx - fcx) * f; h = fh + (th - fh) * f; up = fup + (tup - fup) * f;
                    invalidate();
                }
            });
            anim.start();
        }

        @Override protected void onSizeChanged(int nw, int nh, int ow, int oh) {
            if (nh > fullH) fullH = nh;
            if (oh > 0 && nh >= fullH * 0.95f && oh < fullH * 0.8f && imeGone != null) imeGone.run();
            if (nh >= fullH * 0.95f) up = clamp(up, -DOWN, maxUp());
        }

        int handleAt(float x, float y) {
            RectF k = kb();
            float t = 28 * d;
            if (Math.abs(y - k.top) < t && Math.abs(x - k.centerX()) < 52 * d) return 1;
            if (Math.abs(x - k.left) < t && Math.abs(y - k.centerY()) < 64 * d) return 2;
            if (Math.abs(x - k.right) < t && Math.abs(y - k.centerY()) < 64 * d) return 3;
            if (k.contains(x, y)) return 4;
            return 0;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (anim != null) anim.cancel();
                    active = handleAt(x, y);
                    moved = false;
                    if (active == 0) { if (tapped != null) tapped.run(); return true; }
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    downX = x; downY = y; startW = w; startCx = cx; startH = h; startUp = up;
                    RectF k = kb(); startLeft = k.left; startRight = k.right; startTop = k.top;
                    snappedX = Math.abs(cx - 0.5f) < 0.001f; snappedUp = Math.abs(up) < 0.01f; snappedW = Math.abs(w - 1f) < 0.001f; snappedH = Math.abs(h - 1f) < 0.001f;
                    glow[active].t = 1;
                    try { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) { }
                    lastFrame = 0;
                    postInvalidateOnAnimation();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (active == 0) return true;
                    float dx = x - downX, dy = y - downY, W = getWidth();
                    if (Math.abs(dx) + Math.abs(dy) > 2 * d) moved = true;
                    if (active == 1) {
                        float bottom = kb().bottom, newTop = startTop + dy;
                        float body = (bottom - newTop - baseHeader()) / baseBody();
                        h = clamp(body, MIN_H, Math.max(MIN_H, Math.min(MAX_H, (bottom - 12 * d - baseHeader()) / baseBody())));
                        h = snap(h, 1f, 0.02f, 0);
                    } else if (active == 2 || active == 3) {
                        float left = startLeft, right = startRight;
                        if (active == 2) left = clamp(startLeft + dx, 0, right - W * MIN_W);
                        else right = clamp(startRight + dx, left + W * MIN_W, W);
                        float nw = (right - left) / W;
                        if (Math.abs(nw - 1f) < 0.02f) { left = 0; right = W; }
                        w = clamp((right - left) / W, MIN_W, 1f);
                        cx = clamp((left + right) / 2f / W, w / 2f, 1f - w / 2f);
                        snap(w, 1f, 0.02f, 2);
                    } else if (active == 4) {
                        cx = clamp(startCx + dx / W, w / 2f, 1f - w / 2f);
                        cx = snap(cx, 0.5f, 0.015f, 1);
                        up = clamp(startUp - dy / d, -DOWN, maxUp());
                        up = snap(up, 0f, 4f, 3);
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (active != 0) glow[active].t = 0;
                    active = 0;
                    lastFrame = 0;
                    postInvalidateOnAnimation();
                    return true;
            }
            return true;
        }

        float snap(float v, float to, float range, int which) {
            boolean near = Math.abs(v - to) < range;
            boolean was = which == 0 ? snappedH : which == 1 ? snappedX : which == 2 ? snappedW : snappedUp;
            if (near && !was) try { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) { }
            if (which == 0) snappedH = near; else if (which == 1) snappedX = near; else if (which == 2) snappedW = near; else snappedUp = near;
            return near ? to : v;
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 1f / 60f : Math.min(1f / 30f, (now - lastFrame) / 1e9f);
            lastFrame = now;
            boolean moving = enter.step(dt, 160f, 24f, 0.002f);
            for (GboardBarEditor.Spring s : glow) moving |= s.step(dt, 380f, 32f, 0.003f);
            float W = getWidth();
            float base = baseline();
            if (Math.round(up) > 0) c.drawLine(16 * d, base, W - 16 * d, base, dash);
            c.save();
            c.translate(0, (1f - enter.v) * 120 * d);
            RectF k = kb();
            drawKeyboard(c, k);
            drawHandles(c, k);
            c.restore();
            drawChip(c, W);
            if (moving) postInvalidateOnAnimation(); else lastFrame = 0;
        }

        private void drawChip(Canvas c, float W) {
            String s = I18n.t("Width") + " " + Math.round(w * 100) + "%  ·  " + I18n.t("Height") + " " + Math.round(h * 100) + "%  ·  " + (Math.round(up) >= 0 ? I18n.t("Up") + " " + Math.round(up) : I18n.t("Down") + " " + -Math.round(up)) + " dp";
            word.setTextSize(13 * d);
            word.setTypeface(I18n.medium());
            float tw = word.measureText(s), ch = 30 * d, top = 12 * d;
            fill.setColor(chipBg);
            c.drawRoundRect(W / 2f - tw / 2f - 14 * d, top, W / 2f + tw / 2f + 14 * d, top + ch, ch / 2f, ch / 2f, fill);
            word.setColor(text);
            Paint.FontMetrics fm = word.getFontMetrics();
            c.drawText(s, W / 2f, top + ch / 2f - (fm.ascent + fm.descent) / 2f, word);
        }

        private void drawKeyboard(Canvas c, RectF k) {
            if (shotBody != null) { drawShot(c, k); return; }
            float r = 20 * d;
            if (!dark) { fill.setColor(0x22000000); c.drawRoundRect(k.left, k.top + 2 * d, k.right, k.bottom + 2 * d, r, r, fill); }
            fill.setColor(bgKb);
            c.drawRoundRect(k, r, r, fill);
            if (black) { line.setStrokeWidth(Math.max(1f, d)); line.setColor(0xFF2C2C2E); c.drawRoundRect(k.left + 0.5f * d, k.top + 0.5f * d, k.right - 0.5f * d, k.bottom - 0.5f * d, r, r, line); }
            float hy = k.top + baseHeader() / 2f, isz = 20 * d;
            int n = 9;
            float slot = k.width() / n;
            line.setStrokeWidth(1.9f);
            for (int i = 0; i < n; i++) {
                float x = k.left + slot * (i + 0.5f);
                c.save();
                c.translate(x - isz / 2f, hy - isz / 2f);
                c.scale(isz / 24f, isz / 24f);
                Paint p = new Paint(line); p.setColor(dark ? 0xFFC8C8CC : 0xFF5F6368);
                String id = i == 0 ? "menu" : i == n - 1 ? "smiley" : i == 1 ? "clipboard" : i == 2 ? "pixelsuite_undo" : i == 3 ? "pixelsuite_cut" : i == 4 ? "pixelsuite_copy" : i == 5 ? "pixelsuite_paste" : i == 6 ? "pixelsuite_select" : "settings";
                if ("menu".equals(id)) {
                    Paint f = new Paint(p); f.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(new RectF(4, 4, 11, 11), 2.2f, 2.2f, f); c.drawRoundRect(new RectF(13, 4, 20, 11), 2.2f, 2.2f, f);
                    c.drawRoundRect(new RectF(4, 13, 11, 20), 2.2f, 2.2f, f); c.drawRoundRect(new RectF(13, 13, 20, 20), 2.2f, 2.2f, f);
                } else GboardBarEditor.Icons.draw(c, id, p);
                c.restore();
            }
            float top = k.top + baseHeader(), rowH = (k.bottom - top - 6 * d) / 4f;
            float unit = k.width() / 10f;
            String[] r1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"}, r2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"}, r3 = {"z", "x", "c", "v", "b", "n", "m"};
            for (int i = 0; i < 10; i++) key(c, k.left + unit * i, top, unit, rowH, r1[i], false);
            for (int i = 0; i < 9; i++) key(c, k.left + unit * (i + 0.5f), top + rowH, unit, rowH, r2[i], false);
            key(c, k.left, top + rowH * 2, unit * 1.5f, rowH, "⇧", true);
            for (int i = 0; i < 7; i++) key(c, k.left + unit * (i + 1.5f), top + rowH * 2, unit, rowH, r3[i], false);
            key(c, k.left + unit * 8.5f, top + rowH * 2, unit * 1.5f, rowH, "⌫", true);
            float y4 = top + rowH * 3;
            key(c, k.left, y4, unit * 1.5f, rowH, "?123", true);
            key(c, k.left + unit * 1.5f, y4, unit, rowH, ",", false);
            key(c, k.left + unit * 2.5f, y4, unit, rowH, "🌐", true);
            key(c, k.left + unit * 3.5f, y4, unit * 4f, rowH, "space", false);
            key(c, k.left + unit * 7.5f, y4, unit, rowH, ".", false);
            key(c, k.left + unit * 8.5f, y4, unit * 1.5f, rowH, "↵", true);
        }

        private void key(Canvas c, float x, float y, float kw, float kh, String label, boolean fn) {
            float gx = 3 * d, top = y + 4 * d, bottom = y + kh * 0.86f, rad = 8 * d;
            boolean act = "↵".equals(label);
            RectF f = new RectF(x + gx, top, x + kw - gx, Math.max(top + 8 * d, bottom));
            if (shade != 0) { fill.setColor(shade); c.drawRoundRect(f.left, f.top + 1.2f * d, f.right, f.bottom + 1.2f * d, rad, rad, fill); }
            fill.setColor(act ? action : fn ? fnFace : keyFace);
            c.drawRoundRect(f, rad, rad, fill);
            word.setColor(act ? 0xFFFFFFFF : keyInk);
            word.setTypeface(Typeface.DEFAULT);
            float size = label.length() == 1 && Character.isLetter(label.charAt(0)) ? Math.min(f.height() * 0.5f, 22 * d) : Math.min(f.height() * 0.36f, 15 * d);
            word.setTextSize(size);
            android.graphics.Rect b = new android.graphics.Rect();
            word.getTextBounds(label, 0, label.length(), b);
            c.drawText(label, f.centerX(), f.centerY() - (b.top + b.bottom) / 2f, word);
        }

        private void drawHandles(Canvas c, RectF k) {
            float g;
            g = glow[1].v;
            pill(c, k.centerX(), k.top, 44 * d * (1 + 0.15f * g), 6 * d * (1 + 0.3f * g), g, false);
            g = glow[2].v;
            pill(c, Math.max(k.left, 7 * d), k.centerY(), 6 * d * (1 + 0.3f * g), 44 * d * (1 + 0.15f * g), g, false);
            g = glow[3].v;
            pill(c, Math.min(k.right, getWidth() - 7 * d), k.centerY(), 6 * d * (1 + 0.3f * g), 44 * d * (1 + 0.15f * g), g, false);
            exclude(k);
            g = glow[4].v;
            float rr = 24 * d * (1 + 0.12f * g), x = k.centerX(), y = k.centerY();
            fill.setColor(Color.argb(Math.round(60 + 40 * g), Color.red(accent), Color.green(accent), Color.blue(accent)));
            c.drawCircle(x, y, rr + 8 * d * g, fill);
            fill.setColor(accent);
            c.drawCircle(x, y, rr, fill);
            Paint p = new Paint(line);
            p.setColor(onColor(accent));
            p.setStrokeWidth(2.2f * d);
            float a = 9 * d, t = 3.2f * d;
            c.drawLine(x - a, y, x + a, y, p); c.drawLine(x, y - a, x, y + a, p);
            Path ar = new Path();
            ar.moveTo(x - a + t, y - t); ar.lineTo(x - a, y); ar.lineTo(x - a + t, y + t);
            ar.moveTo(x + a - t, y - t); ar.lineTo(x + a, y); ar.lineTo(x + a - t, y + t);
            ar.moveTo(x - t, y - a + t); ar.lineTo(x, y - a); ar.lineTo(x + t, y - a + t);
            ar.moveTo(x - t, y + a - t); ar.lineTo(x, y + a); ar.lineTo(x + t, y + a - t);
            c.drawPath(ar, p);
        }

        private int lastExcl = Integer.MIN_VALUE;

        private void exclude(RectF k) {
            int key = Math.round(k.centerY()) * 31 + Math.round(k.left) * 7 + Math.round(k.right);
            if (key == lastExcl || android.os.Build.VERSION.SDK_INT < 29) return;
            lastExcl = key;
            try {
                java.util.List<android.graphics.Rect> r = new java.util.ArrayList<android.graphics.Rect>();
                int t = Math.round(k.centerY() - 64 * d), b = Math.round(k.centerY() + 64 * d), e = Math.round(32 * d);
                r.add(new android.graphics.Rect(Math.round(k.left) - e, t, Math.round(k.left) + e, b));
                r.add(new android.graphics.Rect(Math.round(k.right) - e, t, Math.round(k.right) + e, b));
                View.class.getMethod("setSystemGestureExclusionRects", java.util.List.class).invoke(this, r);
            } catch (Throwable ignored) { }
        }

        private void pill(Canvas c, float x, float y, float pw, float ph, float g, boolean unused) {
            if (g > 0.01f) {
                fill.setColor(Color.argb(Math.round(70 * g), Color.red(accent), Color.green(accent), Color.blue(accent)));
                float e = 7 * d * g;
                c.drawRoundRect(x - pw / 2f - e, y - ph / 2f - e, x + pw / 2f + e, y + ph / 2f + e, ph / 2f + e, ph / 2f + e, fill);
            }
            fill.setColor(accent);
            float rr = Math.min(pw, ph) / 2f;
            c.drawRoundRect(x - pw / 2f, y - ph / 2f, x + pw / 2f, y + ph / 2f, rr, rr, fill);
        }
    }

    static String value(float w, float cx, float h, int up) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%d", w, cx, h, up);
    }
}
