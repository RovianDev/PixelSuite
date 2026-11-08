package io.github.pixelsuite;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.Locale;

final class LanguagePill extends FrameLayout implements Choreographer.FrameCallback {
    interface Host { int background(); int primary(); int secondary(); int accent(); void picked(String code); }

    static final String[] CODES = {"auto", "en", "zh", "hi", "es", "ar", "he", "fa", "fr", "pt", "ru", "bn", "id", "de", "ja", "tr"};
    private static final String[] NATIVE = {null, "English", "中文（简体）", "हिन्दी", "Español", "عربي", "עברית", "فارسی", "Français", "Português (Brasil)", "Русский", "বাংলা", "Bahasa Indonesia", "Deutsch", "日本語", "Türkçe"};
    private static final String[] SHORT = {null, "English", "中文", "हिन्दी", "Español", "عربي", "עברית", "فارسی", "Français", "Português", "Русский", "বাংলা", "Indonesia", "Deutsch", "日本語", "Türkçe"};

    private final Activity activity; private final ScrollView scroll; private final Host host; private final PanelBack backKey;
    private final AppFrost frost;
    private final FrameLayout panel; private final ScrollView list; private final Rows rows; private final TextView header;
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG); private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint(), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF(), target = new RectF(), now = new RectF(); private final Path chevron = new Path();
    private String choice, shown;
    private float p, pv, q, qv, press, pressV, pressTarget, spin, highlight, highlightV, pop = 1, popV;
    private int highlightAt;
    private boolean open, ticking, closingForPick; private long last, openedAt, closedAt; private String pending;
    private float offset; private float fade = 1; private final RectF hit = new RectF();

    LanguagePill(Activity a, ScrollView scroll, Host host) {
        super(a); activity = a; this.scroll = scroll; this.host = host;
        backKey = new PanelBack(a, new Runnable() { public void run() { collapse(); } });
        setWillNotDraw(false); setClipChildren(false); setClipToPadding(false);
        frost = AppFrost.pill(this, scroll, dp(13), host.background()); frost.setCallback(this);
        label.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity); label.setTypeface(I18n.medium());
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(dp(1.45f)); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
        choice = I18n.choice(a);
        shown = nameOf(choice);
        panel = new FrameLayout(a); panel.setClipChildren(true); panel.setVisibility(INVISIBLE);
        header = new TextView(a); header.setText(I18n.cs("Language")); header.setTextSize(13); header.setLetterSpacing(.06f);
        header.setTypeface(I18n.medium()); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(22), dp(6), dp(22), 0);
        panel.addView(header, new LayoutParams(-1, dp(46)));
        list = new BounceScroll(a); list.setOverScrollMode(OVER_SCROLL_NEVER); list.setVerticalScrollBarEnabled(false); list.setClipToPadding(false); list.setPadding(0, 0, 0, dp(8));
        rows = new Rows(a); list.addView(rows, new LayoutParams(-1, -2));
        LayoutParams lp = new LayoutParams(-1, -1); lp.topMargin = dp(46); panel.addView(list, lp);
        addView(panel, new LayoutParams(0, 0));
        for (int i = 0; i < CODES.length; i++) rows.addView(row(i), new LinearLayout.LayoutParams(-1, dp(58)));
        highlightAt = Math.max(0, indexOf(choice)); highlight = highlightAt;
        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> { if (!open && p < .001f) follow(); });
        setContentDescription(I18n.cs("Language") + ", " + shown);
        palette();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static int indexOf(String code) { for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i; return 0; }

    static String nameOf(String choice) {
        String code = "auto".equals(choice) ? (I18n.lang() == null ? "en" : I18n.lang()) : choice;
        return SHORT[Math.max(1, indexOf(code))];
    }

    private String subtitle(int i) {
        Locale shownIn = I18n.shownLocale();
        if (i == 0) {
            return I18n.t("Follows the phone language");
        }
        String code = CODES[i]; Locale l = "zh".equals(code) ? Locale.forLanguageTag("zh-Hans") : "pt".equals(code) ? new Locale("pt", "BR") : new Locale(code);
        String name = "zh".equals(code) || "pt".equals(code) ? l.getDisplayName(shownIn) : l.getDisplayLanguage(shownIn);
        if (name == null || name.isEmpty()) return code;
        return name.substring(0, 1).toUpperCase(shownIn) + name.substring(1);
    }

    private View row(final int i) {
        FrameLayout r = new FrameLayout(activity);
        LinearLayout words = new LinearLayout(activity); words.setOrientation(LinearLayout.VERTICAL); words.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = new TextView(activity); name.setText(i == 0 ? I18n.t("Automatic") : NATIVE[i]); name.setTextSize(16); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setTypeface(I18n.medium()); name.setTag("name"); name.setTextDirection(TEXT_DIRECTION_LTR); name.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        TextView sub = new TextView(activity); sub.setText(subtitle(i)); sub.setTextSize(12.5f); sub.setMaxLines(1); sub.setGravity(Gravity.CENTER_HORIZONTAL); sub.setEllipsize(TextUtils.TruncateAt.END); sub.setTag("sub"); sub.setTextDirection(TEXT_DIRECTION_LTR); sub.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        fit(name, 11, 16); fit(sub, 8, 12.5f);
        words.addView(name, new LinearLayout.LayoutParams(-1, dp(22))); words.addView(sub, new LinearLayout.LayoutParams(-1, dp(18)));
        name.setIncludeFontPadding(false); sub.setIncludeFontPadding(false); name.setGravity(Gravity.CENTER); sub.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL); wl.leftMargin = dp(36); wl.rightMargin = dp(36);
        r.addView(words, wl);
        TextView check = new TextView(activity); check.setText("✓"); check.setTextSize(18); check.setGravity(Gravity.CENTER); check.setTag("check");
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER_VERTICAL | Gravity.RIGHT); cl.rightMargin = dp(18);
        r.addView(check, cl);
        r.setClickable(true); r.setFocusable(true);
        r.setContentDescription(i == 0 ? I18n.t("Automatic") : NATIVE[i]);
        r.setOnClickListener(v -> choose(i));
        return r;
    }

    private static void fit(final TextView t, final float min, final float max) {
        t.setEllipsize(null); t.setSingleLine(true); t.setHorizontallyScrolling(false);
        final float scale = t.getResources().getDisplayMetrics().scaledDensity;
        Runnable refit = () -> {
            int room = t.getWidth() - t.getPaddingLeft() - t.getPaddingRight();
            CharSequence text = t.getText();
            if (room <= 0 || text == null) return;
            android.graphics.Paint paint = new android.graphics.Paint(t.getPaint());
            float sp = max;
            paint.setTextSize(sp * scale);
            while (sp > min && paint.measureText(text, 0, text.length()) > room) { sp -= .25f; paint.setTextSize(sp * scale); }
            if (Math.abs(t.getTextSize() - sp * scale) > .5f) t.setTextSize(sp);
        };
        t.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> { if (r - l != or - ol) v.post(refit); });
        t.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            public void onTextChanged(CharSequence a, int b, int c, int d) { }
            public void afterTextChanged(android.text.Editable e) { t.post(refit); }
        });
    }

    void palette() {
        int ink = host.primary(), muted = host.secondary(), accent = host.accent();
        frost.pageColor(host.background());
        label.setColor(ink); line.setColor(ink); header.setTextColor(muted);
        boolean dark = FrostedSurface.dark(getContext()) || AppSurfaces.active(getContext());
        scrim.setColor(dark ? 0x000000 : 0x000000);
        shade.setColor(host.background() | 0xff000000); shade.setShadowLayer(dp(18), 0, dp(6), dark ? 0x66000000 : 0x2a000000);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View r = rows.getChildAt(i);
            ((TextView) r.findViewWithTag("name")).setTextColor(ink);
            ((TextView) r.findViewWithTag("sub")).setTextColor(muted);
            ((TextView) r.findViewWithTag("check")).setTextColor(accent);
            GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(dp(18));
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x22ffffff : 0x18000000), null, mask));
        }
        rows.invalidate(); invalidate();
    }

    private void follow() {
        int y = scroll.getScrollY();
        offset = -y; fade = Math.max(0, Math.min(1, 1 - y / (float) dp(64)));
        invalidate();
    }

    private void measurePill() {
        float text = label.measureText(shown);
        float w = Math.min(dp(150), dp(11) + dp(13) + dp(6) + text + dp(6) + dp(7) + dp(11));
        int inset = scroll.getPaddingTop();
        float right = getWidth() - dp(16), top = inset + dp(4) + offset;
        pill.set(right - w, top, right, top + dp(26));
        hit.set(pill.left - dp(10), Math.max(inset, pill.top - dp(6)), pill.right + dp(8), pill.bottom + dp(12));
        float tw = Math.min(dp(304), getWidth() - dp(32));
        float bottomRoom = getHeight() - dp(162);
        float th = Math.min(dp(46) + CODES.length * dp(58) + dp(8), bottomRoom - pill.top);
        target.set(right - Math.round(tw * .76f), pill.top, right, pill.top + Math.max(dp(200), th));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        follow(); measurePill();
        panel.measure(MeasureSpec.makeMeasureSpec(Math.round(target.width()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(target.height()), MeasureSpec.EXACTLY));
        panel.layout(Math.round(target.left), Math.round(target.top), Math.round(target.right), Math.round(target.bottom));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    private static float ease(float t) { t = clamp(t); return 1 - (1 - t) * (1 - t) * (1 - t); }

    @Override protected boolean verifyDrawable(Drawable d) { return d == frost || super.verifyDrawable(d); }

    @Override protected void dispatchDraw(Canvas c) {
        if (getWidth() == 0) return;
        measurePill();
        float pw = clamp(p), shownness = Math.max(pw, clamp(q));
        if (fade <= 0.01f && shownness <= 0.001f) return;
        if (shownness > 0) { scrim.setAlpha(Math.round((FrostedSurface.dark(getContext()) ? 110 : 46) * shownness)); c.drawRect(0, 0, getWidth(), getHeight(), scrim); }
        float s = 1 - .04f * press;
        now.set(lerp(pill.left, target.left, p), pill.top, pill.right, lerp(pill.bottom, target.bottom, q));
        if (now.bottom < pill.bottom) now.bottom = pill.bottom;
        if (now.left > pill.left) now.left = pill.left;
        float cx = pill.centerX(), cy = pill.centerY();
        int save = c.save();
        c.scale(s, s, cx, cy);
        float radius = lerp(pill.height() / 2f, dp(28), clamp(p * 1.2f));
        int alpha = Math.round(255 * Math.max(fade, shownness));
        if (alpha < 255) c.saveLayerAlpha(now.left - dp(30), now.top - dp(30), now.right + dp(30), now.bottom + dp(30), alpha);
        shade.setAlpha(255); c.drawRoundRect(now, radius, radius, shade);
        frost.radius(radius); frost.setBounds(Math.round(now.left), Math.round(now.top), Math.round(now.right), Math.round(now.bottom)); frost.draw(c);
        long since = SystemClock.uptimeMillis() - openedAt;
        if (open && since < 900) {
            float g = ease(since / 900f), r = lerp(dp(20), Math.max(target.width(), target.height()) * 1.1f, g);
            glow.setShader(new RadialGradient(pill.right - pill.height() / 2f, cy, Math.max(1, r), (host.accent() & 0xffffff) | (Math.round(70 * (1 - g)) << 24), host.accent() & 0xffffff, Shader.TileMode.CLAMP));
            int gs = c.save(); Path clip = new Path(); clip.addRoundRect(now, radius, radius, Path.Direction.CW); c.clipPath(clip); c.drawRect(now, glow); c.restoreToCount(gs);
        }
        float faceAlpha = clamp(1 - pw * 2.6f);
        if (faceAlpha > 0) drawFace(c, faceAlpha);
        if (shownness > 0.001f) {
            panel.setVisibility(VISIBLE);
            Rect clip = new Rect(Math.round(now.left - target.left), 0, Math.round(now.right - target.left), Math.round(now.bottom - target.top));
            panel.setClipBounds(clip);
            stagger();
            super.dispatchDraw(c);
        } else panel.setVisibility(INVISIBLE);
        c.restoreToCount(save);
    }

    private void drawFace(Canvas c, float a) {
        int ink = host.primary();
        label.setColor(ink); label.setAlpha(Math.round(255 * a)); line.setColor(ink); line.setAlpha(Math.round(255 * a));
        float gx = pill.left + dp(11) + dp(6.5f), gy = pill.centerY(), r = dp(6.2f);
        c.drawCircle(gx, gy, r, line);
        float meridian = Math.abs((float) Math.cos(spin)) * r * .55f + dp(.6f);
        RectF m = new RectF(gx - meridian, gy - r, gx + meridian, gy + r); c.drawOval(m, line);
        c.drawLine(gx - r, gy, gx + r, gy, line);
        float tx = gx + r + dp(6), room = pill.right - dp(11) - dp(7) - dp(6) - tx;
        CharSequence words = TextUtils.ellipsize(shown, label, Math.max(0, room), TextUtils.TruncateAt.END);
        Paint.FontMetrics fm = label.getFontMetrics();
        c.drawText(words, 0, words.length(), tx, gy - (fm.ascent + fm.descent) / 2f, label);
        float vx = pill.right - dp(11) - dp(3.5f), vy = gy, w = dp(3.4f), h = dp(1.9f);
        int cs = c.save(); c.rotate(180 * clamp(p), vx, vy);
        chevron.reset(); chevron.moveTo(vx - w, vy - h); chevron.lineTo(vx, vy + h); chevron.lineTo(vx + w, vy - h); c.drawPath(chevron, line);
        c.restoreToCount(cs);
    }

    private void stagger() {
        long t = SystemClock.uptimeMillis() - openedAt;
        float out = open ? 1 : clamp(q * 1.6f - .35f);
        float h = open ? ease((t - 70) / 220f) : out;
        header.setAlpha(h); header.setTranslationY((1 - h) * dp(8));
        int first = rows.getChildCount();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View r = rows.getChildAt(i);
            if (r.getBottom() - list.getScrollY() < 0) continue;
            first = Math.min(first, i);
        }
        for (int i = 0; i < rows.getChildCount(); i++) {
            View r = rows.getChildAt(i);
            int order = Math.max(0, i - (first == rows.getChildCount() ? 0 : first));
            float e = open ? ease((t - 110 - order * 26) / 260f) : out;
            r.setAlpha(e); r.setTranslationY((1 - e) * dp(18)); float sc = .94f + .06f * e; r.setScaleX(sc); r.setScaleY(sc);
            View check = r.findViewWithTag("check");
            boolean on = i == indexOf(closingForPick && pending != null ? pending : choice);
            check.setVisibility(on ? VISIBLE : INVISIBLE);
            if (on) { check.setScaleX(pop); check.setScaleY(pop); }
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (!open && p < .02f) {
            if (fade < .3f) return false;
            boolean inside = hit.contains(e.getX(), e.getY()) || (e.getActionMasked() != MotionEvent.ACTION_DOWN && pressTarget > 0);
            if (!inside && e.getActionMasked() == MotionEvent.ACTION_DOWN) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: pressTarget = 1; tick(); return true;
                case MotionEvent.ACTION_UP: { boolean was = pressTarget > 0; pressTarget = 0; if (was && grown(e)) { performClick(); expand(); } tick(); return true; }
                case MotionEvent.ACTION_CANCEL: pressTarget = 0; tick(); return true;
                case MotionEvent.ACTION_MOVE: if (!grown(e)) { pressTarget = 0; tick(); } return true;
            }
            return true;
        }
        if (open && e.getActionMasked() == MotionEvent.ACTION_DOWN && !now.contains(e.getX(), e.getY())) { collapse(); return true; }
        if (!open) return true;
        return super.dispatchTouchEvent(e);
    }

    private boolean grown(MotionEvent e) { return e.getX() > hit.left - dp(16) && e.getX() < hit.right + dp(16) && e.getY() > hit.top - dp(16) && e.getY() < hit.bottom + dp(16); }

    boolean back() { if (open) { collapse(); return true; } return false; }
    boolean isOpen() { return open; }
    boolean owns(MotionEvent e) { int[] at = new int[2]; getLocationOnScreen(at); return now.contains(e.getRawX() - at[0], e.getRawY() - at[1]); }
    void close() { collapse(); }

    private void expand() {
        if (open) return;
        open = true; closingForPick = false; pending = null; openedAt = SystemClock.uptimeMillis();
        choice = I18n.choice(activity); highlightAt = indexOf(choice); highlight = highlightAt; pop = 1;
        list.post(() -> { View r = rows.getChildAt(highlightAt); if (r != null) list.scrollTo(0, Math.max(0, r.getTop() - dp(58) * 2)); });
        panel.setVisibility(VISIBLE); backKey.on();
        announceForAccessibility(I18n.t("Language"));
        tick();
    }

    private void collapse() {
        if (!open) return;
        open = false; closedAt = SystemClock.uptimeMillis(); backKey.off(); tick();
    }

    private void choose(int i) {
        if (!open || closingForPick) return;
        String code = CODES[i];
        if (code.equals(choice)) { collapse(); return; }
        pending = code; closingForPick = true; highlightAt = i; pop = .4f; popV = 0;
        tick();
        postDelayed(() -> {
            shown = nameOf(code.equals("auto") ? langPhone() : code);
            setContentDescription(I18n.t("Language") + ", " + shown);
            collapse();
        }, 300);
    }

    private static String langPhone() { String l = I18n.langOf(Locale.getDefault()); return l == null ? "en" : l; }

    private void tick() { if (ticking) return; ticking = true; last = 0; Choreographer.getInstance().postFrameCallback(this); }

    private static float[] spring(float x, float v, float to, float k, float zeta, float dt) {
        float c = 2 * zeta * (float) Math.sqrt(k);
        int steps = Math.max(1, Math.round(dt / .004f)); float h = dt / steps;
        for (int i = 0; i < steps; i++) { float a = -k * (x - to) - c * v; v += a * h; x += v * h; }
        return new float[]{x, v};
    }

    @Override public void doFrame(long nanos) {
        float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
        long nowMs = SystemClock.uptimeMillis();
        float pt = open ? 1 : 0, qt = open ? 1 : 0;
        float[] r;
        if (open) {
            r = spring(p, pv, pt, 380, .62f, dt); p = r[0]; pv = r[1];
            if (nowMs - openedAt > 45) { r = spring(q, qv, qt, 300, .66f, dt); q = r[0]; qv = r[1]; }
        } else {
            r = spring(q, qv, qt, 520, .92f, dt); q = r[0]; qv = r[1];
            if (nowMs - closedAt > 40) { r = spring(p, pv, pt, 460, .78f, dt); p = r[0]; pv = r[1]; }
        }
        r = spring(press, pressV, pressTarget, 900, .7f, dt); press = r[0]; pressV = r[1];
        r = spring(highlight, highlightV, highlightAt, 420, .74f, dt); highlight = r[0]; highlightV = r[1];
        r = spring(pop, popV, 1, 520, .38f, dt); pop = r[0]; popV = r[1];
        if (Math.abs(pv) > .01f || Math.abs(qv) > .01f) spin += dt * 9f * (open ? 1 : -1);
        else spin += (Math.round(spin / (float) Math.PI) * (float) Math.PI - spin) * Math.min(1, dt * 10);
        invalidate(); rows.invalidate();
        boolean settled = Math.abs(p - pt) < .001f && Math.abs(pv) < .005f && Math.abs(q - qt) < .001f && Math.abs(qv) < .005f
                && Math.abs(press - pressTarget) < .002f && Math.abs(pressV) < .01f && Math.abs(highlight - highlightAt) < .002f && Math.abs(highlightV) < .01f
                && Math.abs(pop - 1) < .002f && Math.abs(popV) < .01f && (!open || nowMs - openedAt > 900);
        if (settled) {
            p = pt; q = qt; pv = qv = 0; press = pressTarget; highlight = highlightAt; pop = 1; ticking = false; invalidate();
            if (!open) { panel.setVisibility(INVISIBLE); if (closingForPick && pending != null) { String code = pending; pending = null; closingForPick = false; choice = code; host.picked(code); } }
            return;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); Choreographer.getInstance().removeFrameCallback(this); ticking = false; frost.release(); }

    private final class Rows extends LinearLayout {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF box = new RectF();
        Rows(Activity a) { super(a); setOrientation(VERTICAL); setWillNotDraw(false); setPadding(dp(8), 0, dp(8), 0); }
        @Override protected void dispatchDraw(Canvas c) {
            if (getChildCount() > 0) {
                View first = getChildAt(0); float h = first.getHeight();
                float y = first.getTop() + highlight * h;
                int i = Math.max(0, Math.min(getChildCount() - 1, Math.round(highlight)));
                float a = getChildAt(i).getAlpha();
                boolean black = AppSurfaces.active(getContext());
                fill.setColor((host.accent() & 0xffffff) | ((black ? 0x38 : 0x26) << 24));
                fill.setAlpha(Math.round(fill.getAlpha() * a));
                float grow = 1 + .03f * (1 - pop);
                box.set(getPaddingLeft(), y + dp(3), getWidth() - getPaddingRight(), y + h - dp(3));
                float cx = box.centerX(), cy = box.centerY(), w = box.width() * grow / 2, hh = box.height() * grow / 2;
                box.set(cx - w, cy - hh, cx + w, cy + hh);
                c.drawRoundRect(box, dp(18), dp(18), fill);
            }
            super.dispatchDraw(c);
        }
    }
}
