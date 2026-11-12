package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class ShareBar {
    interface Card { Bitmap make(String player); }

    static final float BAR_UP = 160;

    final View host; final Activity a; final SharedPreferences prefs; final String game;
    final java.util.Map<String, String> cfg;
    final int ink, soft; final boolean night; final float d;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF shareAt = new RectF(), barAt = new RectF(), box = new RectF();
    android.widget.EditText field;
    int state;
    long stateAt; Bitmap card; String player; float shareW;
    private float fieldK, fieldFrom; boolean fieldUp; private long fieldAt, imeSeenAt;

    ShareBar(View host, Activity a, SharedPreferences prefs, String game, int ink, int soft, boolean night) {
        this.host = host; this.a = a; this.prefs = prefs; this.game = game; this.ink = ink; this.soft = soft; this.night = night;
        d = a.getResources().getDisplayMetrics().density;
        cfg = PixelStackShare.config(a);
        stroke.setStyle(Paint.Style.STROKE); text.setTextAlign(Paint.Align.CENTER);
    }

    boolean available() { return cfg != null; }

    void attach(android.widget.FrameLayout root) {
        if (cfg == null) return;
        final android.widget.EditText f = new android.widget.EditText(a) {
            @Override public boolean onKeyPreIme(int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_BACK && hasFocus()) { if (e.getAction() == android.view.KeyEvent.ACTION_UP) closeKeyboard(); return true; }
                return super.onKeyPreIme(code, e);
            }
        };
        f.setBackground(null); f.setPadding(0, 0, 0, 0);
        f.setSingleLine(true); f.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        f.setTextSize(15); f.setTextColor(0); f.setHintTextColor(0); f.setHighlightColor(0);
        f.setTypeface(GameKit.MEDIUM); f.setHint(I18n.t("Your name")); f.setCursorVisible(false);
        f.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        f.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        android.text.InputFilter plain = (src, st, en, dst, ds, de) -> { CharSequence part = src.subSequence(st, en); String kept = PixelStackShare.noEmoji(part); return kept.length() == part.length() ? null : kept; };
        f.setFilters(new android.text.InputFilter[]{plain, new android.text.InputFilter.LengthFilter(24)});
        final SharedPreferences names = a.getSharedPreferences("pixelstack", Context.MODE_PRIVATE);
        f.setText(names.getString("name", ""));
        f.setOnEditorActionListener((v, id, ev) -> { closeKeyboard(); return true; });
        f.setOnFocusChangeListener((v, has) -> fieldMove(has));
        f.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable e) {
                try { names.edit().putString("name", PixelStackShare.noEmoji(e.toString()).trim()).apply(); } catch (Throwable ignored) { }
                player = null;
            }
        });
        f.setVisibility(View.GONE);
        field = f;
        root.addView(f, new android.widget.FrameLayout.LayoutParams(1, 1, android.view.Gravity.TOP | android.view.Gravity.LEFT));
    }

    void reset() { state = 0; player = null; card = null; hideField(); }

    private float sp(float v) { return v * a.getResources().getDisplayMetrics().scaledDensity; }

    void fieldMove(boolean up) {
        fieldFrom = fieldK; fieldUp = up; fieldAt = SystemClock.uptimeMillis();
        GameKit.tick(a, up ? .4f : .25f);
        PixelStackSound.play(PixelStackSound.SOFT, .3f, up ? 1.12f : .9f);
        host.invalidate();
    }
    private float fieldNow(long now, boolean motion) {
        float t = motion ? (now - fieldAt) / 520f : 1f, target = fieldUp ? 1f : 0f;
        fieldK = fieldFrom + (target - fieldFrom) * (t >= 1f ? 1f : GameKit.spring(t));
        return fieldK;
    }

    boolean typing() {
        long now = SystemClock.uptimeMillis();
        if (imeShown()) imeSeenAt = now;
        return (field != null && field.hasFocus()) || fieldUp || fieldK > .02f || (imeSeenAt != 0 && now - imeSeenAt < 600);
    }
    private boolean imeShown() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 30) return false;
            android.view.WindowInsets in = host.getRootWindowInsets();
            if (in == null) return false;
            int ime = (Integer) Class.forName("android.view.WindowInsets$Type").getMethod("ime").invoke(null);
            return (Boolean) android.view.WindowInsets.class.getMethod("isVisible", int.class).invoke(in, ime);
        } catch (Throwable t) { return false; }
    }
    void closeKeyboard() {
        if (field == null) return;
        try { ((android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(), 0); } catch (Throwable ignored) { }
        field.clearFocus(); host.requestFocus();
        if (fieldUp) fieldMove(false);
    }
    void hideField() {
        if (field == null) return;
        if (field.hasFocus()) closeKeyboard();
        if (field.getVisibility() != View.GONE) field.setVisibility(View.GONE);
    }

    private boolean hit(RectF r, float x, float y) { return r.width() > 0 && x > r.left - 12 * d && x < r.right + 12 * d && y > r.top - 12 * d && y < r.bottom + 12 * d; }

    boolean tap(MotionEvent e, int score, Card maker) {
        if (cfg == null || score <= 0) return false;
        float x = e.getX(), y = e.getY();
        if (field != null && field.hasFocus()) { closeKeyboard(); if (!hit(shareAt, x, y)) return true; }
        if (state != 1 && state != 2 && hit(shareAt, x, y)) { send(score, maker); return true; }
        if (hit(barAt, x, y)) return true;
        return state == 1;
    }

    private void send(final int score, final Card maker) {
        final String who = PixelStackShare.player(a);
        closeKeyboard();
        Bitmap made = null;
        try { made = maker.make(who); } catch (Throwable ignored) { }
        final Bitmap made2 = made;
        card = made; state = 1; stateAt = SystemClock.uptimeMillis();
        GameKit.tick(a, .8f);
        PixelStackSound.play(PixelStackSound.SEND, .5f, 1f);
        new Thread(() -> {
            final boolean ok = made2 != null && PixelStackShare.post(cfg, made2, score, who, game);
            host.post(() -> {
                state = ok ? 2 : 3; stateAt = SystemClock.uptimeMillis();
                if (ok) {
                    PixelStackSound.play(PixelStackSound.SUCCESS, .6f, 1f);
                    PixelStack.buzz(a, new int[]{3, 7, 7}, new float[]{.6f, .8f, 1f}, new int[]{0, 60, 60}, new long[]{0, 20, 60, 20, 60, 20}, new int[]{0, 160, 0, 200, 0, 255});
                    host.postDelayed(() -> PixelStackShare.openSection(a, cfg), 1100);
                } else { PixelStack.buzz(a, new int[]{6}, new float[]{.8f}, new int[]{0}, new long[]{0, 60}, new int[]{0, 140}); PixelStackSound.play(PixelStackSound.FAIL, .5f, 1f); }
            });
        }, "ArcadeShare").start();
    }

    void draw(Canvas c, long now, float since, float upY, int score, boolean motion, float fade) {
        if (cfg == null || score <= 0) { shareAt.setEmpty(); barAt.setEmpty(); hideField(); return; }
        int W = host.getWidth(), H = host.getHeight();
        float k = GameKit.ease((since - 900) / 450f) * fade;
        if (k <= 0) { shareAt.setEmpty(); barAt.setEmpty(); hideField(); return; }
        long st = now - stateAt;
        if (player == null) player = PixelStackShare.player(a);
        boolean signed = PixelStackShare.signedIn(a), typing = !signed && field != null;
        boolean busy = state == 1 || state == 2;
        if (field != null && imeShown()) imeSeenAt = now;
        float bw = Math.min(W - 40 * d, 336 * d), bh = 60 * d;
        float home = H - BAR_UP * d + (1 - GameKit.spring((since - 900) / 600f)) * 40 * d;
        float fk = typing ? fieldNow(now, motion) : 0f;
        if (!typing) { fieldK = fieldFrom = 0f; fieldUp = false; }
        float cy = home + (upY + bh / 2 - home) * fk;
        float mid = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, fieldUp ? fk : 1f - fk)));
        float swell = 1f + .04f * mid, tilt = (fieldUp ? -3f : 3f) * mid;
        barAt.set(W / 2f - bw / 2, cy - bh / 2, W / 2f + bw / 2, cy + bh / 2);
        float press = state == 1 ? 1 - .04f * (float) Math.sin(Math.min(1f, st / 160f) * Math.PI) : 1f;
        int save = c.save(); c.rotate(tilt, barAt.centerX(), barAt.centerY()); c.scale(swell * press, swell * press, barAt.centerX(), barAt.centerY());
        boolean focused = field != null && field.hasFocus();
        int accent = night ? GameKit.LIGHT : GameKit.BLUE, onAccent = night ? 0xFF0B0D14 : 0xFFFFFFFF;
        fill.setShader(null); fill.setStyle(Paint.Style.FILL);
        if (!night) { fill.setColor(GameKit.alpha(0x16000000, k)); box.set(barAt); box.offset(0, 2 * d); c.drawRoundRect(box, bh / 2, bh / 2, fill); }
        fill.setColor(GameKit.alpha(night ? 0xFF161A24 : 0xFFFFFFFF, k)); c.drawRoundRect(barAt, bh / 2, bh / 2, fill);
        stroke.setStrokeCap(Paint.Cap.BUTT); stroke.setStrokeJoin(Paint.Join.MITER);
        stroke.setStrokeWidth((focused ? 1.8f : 1.2f) * d); stroke.setColor(GameKit.alpha(focused ? accent : (night ? 0x40FFFFFF : 0x22000000), k));
        c.drawRoundRect(barAt, bh / 2, bh / 2, stroke);
        boolean rtl = I18n.rtl();
        String name = signed ? player : (field == null ? "" : PixelStackShare.noEmoji(field.getText()).trim());
        float r = 20 * d, by = barAt.centerY(), bx = rtl ? barAt.right - 10 * d - r : barAt.left + 10 * d + r;
        fill.setShader(new android.graphics.LinearGradient(bx - r, by - r, bx + r, by + r, GameKit.BLUE, 0xFF4F8DF5, android.graphics.Shader.TileMode.CLAMP));
        fill.setAlpha(Math.round(255 * k)); c.drawCircle(bx, by, r, fill); fill.setShader(null);
        if (name.length() > 0) {
            String first = new String(Character.toChars(name.codePointAt(0))).toUpperCase(java.util.Locale.ROOT);
            text.setTypeface(GameKit.BOLD); text.setTextSize(sp(17)); text.setColor(GameKit.alpha(0xFFFFFFFF, k));
            c.drawText(first, bx, by - (text.descent() + text.ascent()) / 2, text);
        } else {
            fill.setColor(GameKit.alpha(0xFFFFFFFF, k * .95f));
            c.drawCircle(bx, by - 4.5f * d, 5.5f * d, fill);
            box.set(bx - 9.5f * d, by + 3 * d, bx + 9.5f * d, by + 17 * d); c.drawRoundRect(box, 8 * d, 8 * d, fill);
        }
        text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(15));
        String label = state == 1 ? I18n.t("Sharing…") : state == 2 ? I18n.t("Shared!") : I18n.t("Share");
        float want = Math.max(96 * d, Math.min(text.measureText(label) + 54 * d, bw * .5f));
        shareW = shareW <= 0 || !motion ? want : shareW + (want - shareW) * .22f;
        float bth = bh - 14 * d;
        if (rtl) shareAt.set(barAt.left + 7 * d, by - bth / 2, barAt.left + 7 * d + shareW, by + bth / 2);
        else shareAt.set(barAt.right - 7 * d - shareW, by - bth / 2, barAt.right - 7 * d, by + bth / 2);
        fill.setColor(GameKit.alpha(accent, k)); c.drawRoundRect(shareAt, bth / 2, bth / 2, fill);
        float ix = rtl ? shareAt.right - 22 * d : shareAt.left + 22 * d, iy = by;
        stroke.setColor(GameKit.alpha(onAccent, k)); stroke.setStrokeWidth(2 * d); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
        if (state == 1) { box.set(ix - 7 * d, iy - 7 * d, ix + 7 * d, iy + 7 * d); c.drawArc(box, (st / 2.2f) % 360, 260, false, stroke); }
        else if (state == 2) { android.graphics.Path pth = GameKit.path(); pth.moveTo(ix - 7 * d, iy); pth.lineTo(ix - 2 * d, iy + 5 * d); pth.lineTo(ix + 7 * d, iy - 6 * d); c.drawPath(pth, stroke); }
        else {
            int ps = c.save(); if (rtl) c.scale(-1, 1, ix, iy);
            android.graphics.Path pth = GameKit.path(); pth.moveTo(ix - 8 * d, iy - 1 * d); pth.lineTo(ix + 8 * d, iy - 7 * d); pth.lineTo(ix + 2 * d, iy + 8 * d); pth.lineTo(ix - 1 * d, iy + 2 * d); pth.close();
            c.drawPath(pth, stroke); c.drawLine(ix - 1 * d, iy + 2 * d, ix + 8 * d, iy - 7 * d, stroke);
            c.restoreToCount(ps);
        }
        text.setColor(GameKit.alpha(onAccent, k));
        float lw = shareAt.width() - 44 * d, lx = rtl ? shareAt.left + 10 * d + lw / 2 : shareAt.left + 34 * d + lw / 2;
        c.drawText(fit(label, lw), lx, by - (text.descent() + text.ascent()) / 2, text);
        float ns = rtl ? bx - r - 12 * d : bx + r + 12 * d, ne = rtl ? shareAt.right + 10 * d : shareAt.left - 10 * d, room = Math.abs(ne - ns);
        boolean focused2 = focused && typing && !busy;
        if (name.length() > 0 || signed) { text.setTypeface(GameKit.MEDIUM); text.setColor(GameKit.alpha(ink, k)); }
        else { text.setTypeface(GameKit.REGULAR); text.setColor(GameKit.alpha(soft, k * 1.6f)); }
        text.setTextSize(sp(15));
        String whole = name.length() > 0 ? name : signed ? player : I18n.t("Your name");
        String line = focused2 && name.length() > 0 ? fitEnd(whole, room - 6 * d) : fit(whole, room);
        float base = by - (text.descent() + text.ascent()) / 2;
        text.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        c.drawText(line, ns, base, text);
        if (typing && !busy) {
            float tw = text.measureText(line);
            float x = name.length() > 0 ? (rtl ? ns - tw - 2 * d : ns + tw + 2 * d) : (rtl ? ns + 3 * d : ns - 3 * d);
            float blink = (float) (.5 + .5 * Math.cos(now / (focused ? 260.0 : 380.0))); blink = blink * blink * (3 - 2 * blink);
            fill.setColor(GameKit.alpha(accent, k * (focused ? .2f + .8f * blink : .12f + .4f * blink)));
            float ch = 20 * d; box.set(x - 1 * d, by - ch / 2, x + 1 * d, by + ch / 2); c.drawRoundRect(box, 1 * d, 1 * d, fill);
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
        if (state == 3 && st < 2600) {
            text.setTypeface(GameKit.MEDIUM); text.setTextSize(sp(13)); text.setColor(GameKit.alpha(soft, fade * 1.6f * (st < 2200 ? 1 : 1 - (st - 2200) / 400f)));
            c.drawText(I18n.t("Couldn't share. Try again."), W / 2f, barAt.top - 12 * d, text);
        }
        if (state == 1 || (state == 2 && st < 900)) {
            float f = state == 1 ? Math.min(1f, st / 500f) : 1f;
            float cw = W * .62f, ch = cw * 1.25f, cx = W / 2f, ccy = H * .48f;
            float fly = state == 2 ? GameKit.easeIn(st / 900f) : 0f;
            float sc = .6f + .4f * GameKit.spring(f) - .5f * fly;
            box.set(cx - cw / 2 * sc, ccy - ch / 2 * sc - fly * H * .7f, cx + cw / 2 * sc, ccy + ch / 2 * sc - fly * H * .7f);
            int sv = c.save(); c.rotate(-6 * fly + 3 * (float) Math.sin(st / 180.0) * (1 - fly), box.centerX(), box.centerY());
            float op = f * (1 - fly);
            if (card != null && !card.isRecycled()) {
                fill.setShader(null); fill.setColor(GameKit.alpha(0x55000000, op * .6f));
                RectF sh = new RectF(box); sh.offset(0, 6 * d); c.drawRoundRect(sh, 18 * d, 18 * d, fill);
                android.graphics.Path clip = new android.graphics.Path(); clip.addRoundRect(box, 18 * d, 18 * d, android.graphics.Path.Direction.CW);
                int cs = c.save(); c.clipPath(clip);
                Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG); bp.setAlpha(Math.round(255 * op));
                c.drawBitmap(card, null, box, bp);
                c.restoreToCount(cs);
                stroke.setColor(GameKit.alpha(0x33FFFFFF, op)); stroke.setStrokeWidth(1.2f * d); c.drawRoundRect(box, 18 * d, 18 * d, stroke);
            }
            c.restoreToCount(sv);
        }
    }

    private String fitEnd(String t, float w) {
        if (t == null) return "";
        if (w <= 0 || text.measureText(t) <= w) return t;
        float dots = text.measureText("…"); int i = 0;
        while (i < t.length() && dots + text.measureText(t, i, t.length()) > w) i = t.offsetByCodePoints(i, 1);
        return "…" + t.substring(i);
    }
    private String fit(String t, float w) {
        if (t == null) return "";
        if (w <= 0 || text.measureText(t) <= w) return t;
        int n = t.length();
        while (n > 0 && text.measureText(t, 0, n) + text.measureText("…") > w) n = t.offsetByCodePoints(n, -1);
        return t.substring(0, n).trim() + "…";
    }
}
