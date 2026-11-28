package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import java.util.Locale;

final class PhoneStatus {
    static final int SAFE = 0, UNTESTED = 1, UNSAFE = 2;

    static final String[][] BUILDS = {
        {"BP4A.251205", "safe"},
        {"CP3A.261005", "safe"},
        {"CP4A.261105", "safe"},
    };

    private static java.lang.ref.WeakReference<Sheet> sOpen = new java.lang.ref.WeakReference<Sheet>(null);

    private PhoneStatus() {}

    static String key(String id) {
        if (id == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Z]{2}\\d[A-Z])\\.(\\d{6})").matcher(id.trim());
        return m.find() ? m.group(1) + "." + m.group(2) : null;
    }

    static int status() { return status(Build.ID, Build.VERSION.CODENAME); }

    static int status(String id, String codename) {
        if (codename != null && !"REL".equals(codename)) return UNTESTED;
        String key = key(id);
        if (key == null) return UNTESTED;
        for (String[] b : BUILDS) if (b[0].equals(key)) return "unsafe".equals(b[1]) ? UNSAFE : "safe".equals(b[1]) ? SAFE : UNTESTED;
        int date = Integer.parseInt(key.substring(5)), newest = -1;
        for (String[] b : BUILDS) if (b[0].charAt(0) == key.charAt(0) && "safe".equals(b[1])) newest = Math.max(newest, Integer.parseInt(b[0].substring(5)));
        return newest >= 0 && date <= newest ? SAFE : UNTESTED;
    }

    static String phone() {
        String model = Build.MODEL == null ? "" : Build.MODEL.trim(), maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        if (model.isEmpty()) return I18n.t("Unknown");
        if (!maker.isEmpty() && !"Google".equalsIgnoreCase(maker) && !model.toLowerCase(Locale.ROOT).startsWith(maker.toLowerCase(Locale.ROOT)))
            return maker.substring(0, 1).toUpperCase(Locale.ROOT) + maker.substring(1) + " " + model;
        return model;
    }

    static String android() {
        String r = Build.VERSION.RELEASE;
        try { if (Build.VERSION.SDK_INT >= 30) r = (String) Build.VERSION.class.getField("RELEASE_OR_CODENAME").get(null); } catch (Throwable ignored) { }
        return r == null || r.isEmpty() ? I18n.t("Unknown") : r;
    }

    static String build() { return Build.ID == null || Build.ID.isEmpty() ? I18n.t("Unknown") : Build.ID; }

    static String patch() {
        String s = Build.VERSION.SECURITY_PATCH;
        if (s == null || s.isEmpty()) return I18n.t("Unknown");
        try {
            java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(s);
            return java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, I18n.shownLocale()).format(d);
        } catch (Throwable t) { return s; }
    }

    static String label(int s) { return I18n.t(s == SAFE ? "Safe" : s == UNSAFE ? "Unsafe" : "Untested"); }

    static String meaning(int s) {
        return I18n.t(s == SAFE ? "Tested on this Android version. Everything should work."
                : s == UNSAFE ? "This Android version is known to break Pixel Suite features."
                : "Not tested on this Android version yet. Some features may work, some may not.");
    }

    static int base(int s) { return s == SAFE ? 0xff34c759 : s == UNSAFE ? 0xffff453a : 0xffffc20a; }

    static int tone(int s, boolean dark) {
        if (s == SAFE) return dark ? 0xff8fe3a1 : 0xff17722f;
        if (s == UNSAFE) return dark ? 0xffff948c : 0xffb3261e;
        return dark ? 0xffffd970 : 0xff845c00;
    }

    private static int dp(Context c, float v) { return FrostedSurface.dp(c, v); }

    static final int SIZE = 30;

    static View corner(final Activity a, final ScrollView scroll) {
        final boolean dark = FrostedSurface.dark(a), black = AppSurfaces.active(a);
        int page = a instanceof SettingsActivity ? ((SettingsActivity) a).pageColor() : (black ? 0xff000000 : dark ? 0xff121212 : 0xfff2f0f5);
        int ink = dark || black ? 0xffe6e6ea : 0xff1f1f1f;
        final FrameLayout hit = new FrameLayout(a);
        final FrameLayout circle = new FrameLayout(a);
        GradientDrawable mask = new GradientDrawable(); mask.setShape(GradientDrawable.OVAL); mask.setColor(Color.WHITE);
        final AppFrost glass = AppFrost.pill(circle, new View(a), dp(a, SIZE) / 2, page);
        circle.setBackground(new RippleDrawable(ColorStateList.valueOf(dark || black ? 0x33ffffff : 0x22000000), glass, mask));
        circle.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); } });
        circle.setElevation(black ? 0 : dp(a, 3));
        circle.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() { public void onViewAttachedToWindow(View v) { } public void onViewDetachedFromWindow(View v) { glass.release(); } });
        circle.addView(new Mark(a, ink), new FrameLayout.LayoutParams(-1, -1));
        circle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hit.addView(circle, new FrameLayout.LayoutParams(dp(a, SIZE), dp(a, SIZE), Gravity.CENTER));
        hit.setClickable(true); hit.setFocusable(true);
        hit.setContentDescription(I18n.cs("Phone compatibility"));
        hit.setOnClickListener(v -> {
            if (hit.getAlpha() < .3f) return;
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            circle.setPressed(false);
            open(a, circle, hit);
        });
        hit.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && hit.getAlpha() >= .3f) { circle.drawableHotspotChanged(circle.getWidth() / 2f, circle.getHeight() / 2f); circle.setPressed(true); }
            else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) circle.setPressed(false);
            return false;
        });
        final Runnable place = () -> {
            View parent = (View) hit.getParent();
            if (parent == null || parent.getWidth() == 0) return;
            int y = scroll.getScrollY();
            hit.setTranslationX(AppColumn.side(a, parent.getWidth()) + dp(a, 16) + dp(a, SIZE) / 2f - dp(a, 22) - hit.getLeft());
            hit.setTranslationY(scroll.getPaddingTop() + dp(a, 4) + dp(a, 13) - dp(a, 22) - y);
            float fade = Math.max(0f, Math.min(1f, 1f - y / (float) dp(a, 64)));
            if (hit.getVisibility() == View.VISIBLE) hit.setAlpha(fade);
        };
        scroll.getViewTreeObserver().addOnScrollChangedListener(place::run);
        hit.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place.run());
        hit.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { View parent = (View) hit.getParent(); if (parent != null) parent.addOnLayoutChangeListener((w, l, t, r, b, ol, ot, or, ob) -> place.run()); hit.post(place); }
            public void onViewDetachedFromWindow(View v) { }
        });
        return hit;
    }

    static void open(Activity a, View from, View hide) {
        Sheet old = sOpen.get();
        if (old != null && old.isAttachedToWindow()) return;
        ViewGroup host = (ViewGroup) a.findViewById(android.R.id.content);
        if (host == null) return;
        Sheet s = new Sheet(a, from, hide);
        sOpen = new java.lang.ref.WeakReference<Sheet>(s);
        host.addView(s, new FrameLayout.LayoutParams(-1, -1));
        s.expand();
    }

    static boolean back() {
        Sheet s = sOpen.get();
        if (s == null || !s.open) return false;
        s.collapse(true);
        return true;
    }

    private static final class Mark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator pulse; private float k;
        Mark(Context c, int ink) { super(c); paint.setColor(ink); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); start(); }
        @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) start(); else stop(); }
        @Override protected void onVisibilityChanged(View changed, int v) { super.onVisibilityChanged(changed, v); if (isShown()) start(); else stop(); }
        private void start() {
            if (pulse != null || !isAttachedToWindow() || !isShown() || !AppMotion.enabled(getContext())) return;
            pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(1700); pulse.setRepeatCount(ValueAnimator.INFINITE); pulse.setInterpolator(new LinearInterpolator());
            pulse.addUpdateListener(an -> { k = (Float) an.getAnimatedValue(); invalidate(); });
            pulse.start();
        }
        private void stop() { if (pulse != null) { pulse.cancel(); pulse = null; } k = 0; invalidate(); }
        @Override protected void onDraw(Canvas c) { draw(c, getWidth() / 2f, getHeight() / 2f, scale(getResources().getDisplayMetrics().density, Math.min(getWidth(), getHeight())), beat(k), paint, 1f); }
        static float scale(float d, float size) { return d * Math.max(.4f, size / (44f * d)); }
        static float beat(float k) { if (k >= .48f) return 0f; float x = (float) Math.sin(Math.PI * k / .48f); return x * x * (3 - 2 * x); }
        static void draw(Canvas c, float cx, float cy, float d, float beat, Paint paint, float alpha) {
            if (alpha <= 0.004f) return;
            float s = 1f + .2f * beat;
            int save = c.save();
            c.scale(s, s, cx, cy);
            int was = paint.getAlpha();
            paint.setAlpha(Math.round(255 * alpha * (.84f + .16f * beat)));
            float bw = 3.3f * d;
            c.drawRoundRect(cx - bw / 2f, cy - 9.4f * d, cx + bw / 2f, cy + 3f * d, bw / 2f, bw / 2f, paint);
            c.drawCircle(cx, cy + 7.4f * d, 1.95f * d, paint);
            paint.setAlpha(was);
            c.restoreToCount(save);
        }
    }

    private static final class Rimmed extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int base; private final boolean dark; private final float radius, width; private int builtH = -1;
        Rimmed(int base, boolean dark, float radius, float width) {
            this.base = base; this.dark = dark; this.radius = radius; this.width = width;
            fill.setColor((base & 0xffffff) | ((dark ? 0x2c : 0x1e) << 24));
            rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(width);
            shine.setStyle(Paint.Style.STROKE); shine.setStrokeWidth(width * 1.4f);
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); if (b.isEmpty()) return;
            if (builtH != b.height()) {
                builtH = b.height();
                int c0 = base & 0xffffff;
                rim.setShader(new LinearGradient(0, b.top, 0, b.bottom, new int[]{c0 | (dark ? 0xc0 : 0xb0) << 24, c0 | 0x58 << 24, c0 | (dark ? 0x80 : 0x70) << 24}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
                shine.setShader(new LinearGradient(0, b.top, 0, b.bottom, new int[]{dark ? 0x30ffffff : 0x80ffffff, 0x00ffffff, dark ? 0x10ffffff : 0x30ffffff}, new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP));
            }
            float r = Math.min(radius, b.height() / 2f), in = width / 2f;
            c.drawRoundRect(b.left, b.top, b.right, b.bottom, r, r, fill);
            c.drawRoundRect(b.left + in * 2.4f, b.top + in * 2.4f, b.right - in * 2.4f, b.bottom - in * 2.4f, Math.max(0, r - in * 2.4f), Math.max(0, r - in * 2.4f), shine);
            c.drawRoundRect(b.left + in, b.top + in, b.right - in, b.bottom - in, Math.max(0, r - in), Math.max(0, r - in), rim);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class Glyph extends android.text.style.ReplacementSpan {
        private final Drawable d; private final int size, gap;
        Glyph(Drawable d, int size, int gap) { this.d = d; this.size = size; this.gap = gap; }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) { return size + gap; }
        @Override public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            float mid = y + (fm.ascent + fm.descent) / 2f;
            boolean rtl = false;
            try { rtl = android.text.TextUtils.getLayoutDirectionFromLocale(I18n.shownLocale()) == View.LAYOUT_DIRECTION_RTL; } catch (Throwable ignored) { }
            int left = Math.round(rtl ? x : x + gap);
            d.setBounds(left, Math.round(mid - size / 2f), left + size, Math.round(mid + size / 2f));
            d.draw(c);
        }
    }

    private static final class Sheet extends FrameLayout implements Choreographer.FrameCallback {
        private final Activity activity; private final View from, hide; private final PanelBack backKey; private final AppFrost frost; private final float hideAlpha;
        private final boolean dark; private final int status, ink, muted, accent, page;
        private final ScrollView list; private final LinearLayout body;
        private final RectF start = new RectF(), target = new RectF(), now = new RectF();
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint(), glow = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path(); private final int[] at = new int[2], fromAt = new int[2];
        boolean open; private boolean ticking, outside, gone, rested; private float p, pv, q, qv; private long last, openedAt, closedAt;
        private RadialGradient glowShader; private final Matrix glowAt = new Matrix();

        Sheet(Activity a, View from, View hide) {
            super(a); activity = a; this.from = from; this.hide = hide; hideAlpha = hide == null ? 1f : hide.getAlpha();
            setWillNotDraw(false); setClipChildren(false); setClipToPadding(false);
            boolean black = AppSurfaces.active(a);
            dark = FrostedSurface.dark(a) || black;
            SettingsActivity s = a instanceof SettingsActivity ? (SettingsActivity) a : null;
            page = s != null ? s.pageColor() : (black ? 0xff000000 : dark ? 0xff121212 : 0xfff2f0f5);
            ink = s != null ? s.inkColor() : (dark ? 0xffe6e1e5 : 0xff1d1b20);
            muted = s != null ? s.mutedColor() : (dark ? 0xffcac4d0 : 0xff49454f);
            accent = s != null ? s.accentColor() : (dark ? 0xffd0bcff : 0xff6750a4);
            status = PhoneStatus.status();
            backKey = new PanelBack(a, new Runnable() { public void run() { collapse(true); } });
            View behind = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
            frost = AppFrost.pill(this, behind == null ? new View(a) : behind, dp(a, 28), page); frost.setCallback(this);
            shade.setColor(page | 0xff000000); shade.setShadowLayer(dp(a, 22), 0, dp(a, 8), dark ? 0x70000000 : 0x2e000000);
            mark.setColor(dark ? 0xffe6e6ea : 0xff1f1f1f);
            list = new BounceScroll(a); list.setOverScrollMode(OVER_SCROLL_NEVER); list.setVerticalScrollBarEnabled(false); list.setClipToPadding(false);
            body = new LinearLayout(a); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 22));
            list.addView(body, new LayoutParams(-1, -2));
            addView(list, new LayoutParams(0, 0));
            fill(a);
        }

        private TextView text(float size, int color, Typeface face) {
            TextView t = new TextView(activity) { @Override public boolean hasOverlappingRendering() { return false; } }; t.setTextSize(size); t.setTextColor(color); t.setTypeface(face); t.setIncludeFontPadding(false);
            return t;
        }

        private static LinearLayout flat(Context c) { return new LinearLayout(c) { @Override public boolean hasOverlappingRendering() { return false; } }; }

        private void fill(Activity a) {
            TextView head = text(13, muted, I18n.medium()); head.setText(I18n.t("Your phone")); head.setLetterSpacing(.06f);
            body.addView(head, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout state = flat(a); state.setOrientation(LinearLayout.VERTICAL);
            state.setBackground(new Rimmed(base(status), dark, dp(a, 20), dp(a, 1.5f)));
            state.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 15));
            LinearLayout top = flat(a); top.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = text(13, tone(status, dark), Typeface.create("sans-serif", Typeface.BOLD));
            badge.setText(status == SAFE ? "✓" : status == UNSAFE ? "!" : "?"); badge.setGravity(Gravity.CENTER);
            GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL); dot.setColor((base(status) & 0xffffff) | ((dark ? 0x40 : 0x2e) << 24)); badge.setBackground(dot);
            top.addView(badge, new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
            TextView name = text(18, tone(status, dark), Typeface.create("sans-serif", Typeface.BOLD)); name.setText(label(status));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2); np.setMarginStart(dp(a, 10)); top.addView(name, np);
            state.addView(top, new LinearLayout.LayoutParams(-1, -2));
            TextView means = text(14, ink, Typeface.create("sans-serif", Typeface.NORMAL)); means.setText(meaning(status)); means.setLineSpacing(0, 1.18f);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2); mp.topMargin = dp(a, 9); state.addView(means, mp);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin = dp(a, 14); body.addView(state, sp);

            String[][] rows = {{"Phone", phone()}, {"Android version", android()}, {"Build number", build()}, {"Security update", patch()}};
            for (int i = 0; i < rows.length; i++) {
                LinearLayout row = flat(a); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(a, 40));
                TextView k = text(14, muted, Typeface.create("sans-serif", Typeface.NORMAL)); k.setText(I18n.t(rows[i][0]));
                TextView v = text(14, ink, I18n.medium()); v.setText(rows[i][1]); v.setGravity(Gravity.END); v.setTextAlignment(TEXT_ALIGNMENT_VIEW_END);
                if (i == 2) v.setTextDirection(TEXT_DIRECTION_LTR);
                row.addView(k, new LinearLayout.LayoutParams(-2, -2));
                LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, -2, 1f); vp.setMarginStart(dp(a, 16)); row.addView(v, vp);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = i == 0 ? dp(a, 10) : 0; body.addView(row, rp);
            }

            TextView note = text(14, ink, Typeface.create("sans-serif", Typeface.NORMAL)); note.setLineSpacing(0, 1.24f);
            SpannableStringBuilder words = new SpannableStringBuilder();
            words.append(I18n.t("Please be advised: updating your phone can and will break parts of Pixel Suite. If you update before I give the green light, I'm not responsible for what breaks. I do this in my free time and I'm just one guy, so don't expect me to always keep up with everything Google pushes."));
            words.append("\n\n");
            linked(words, I18n.t("I recommend always keeping {0} on, so nothing installs by mistake. A surprise update could bootloop your phone or break things without you knowing."), I18n.t("OTA blocker"), "ic_ps_ota_block", new Runnable() { public void run() { otaBlocker(); } });
            words.append("\n\n");
            linked(words, I18n.t("To know when it's safe to update, follow Announcements in my {0}."), I18n.t("Telegram"), "brand_telegram_glyph", new Runnable() { public void run() { telegram(); } });
            words.append("\n\n");
            words.append(I18n.t("Thank you 💙"));
            note.setText(words);
            note.setMovementMethod(LinkMovementMethod.getInstance()); note.setHighlightColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2); tp.topMargin = dp(a, 14); body.addView(note, tp);
        }

        private void linked(SpannableStringBuilder out, String template, String link, String icon, final Runnable go) {
            int at = template.indexOf("{0}");
            if (at < 0) { out.append(template); return; }
            out.append(template, 0, at);
            int from = out.length();
            out.append(link);
            Drawable d = null;
            try { int id = getResources().getIdentifier(icon, "drawable", activity.getPackageName()); if (id != 0) d = getResources().getDrawable(id, activity.getTheme()).mutate(); } catch (Throwable ignored) { }
            if (d != null) {
                d.setTint(accent);
                int glyphAt = out.length();
                out.append("\u2060");
                out.setSpan(new Glyph(d, dpi(17), dpi(5)), glyphAt, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            final Typeface bold = Typeface.create(I18n.medium(), Typeface.BOLD);
            out.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) { widget.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); go.run(); }
                @Override public void updateDrawState(TextPaint ds) { ds.setColor(accent); ds.setUnderlineText(false); ds.setTypeface(bold); ds.setFakeBoldText(bold.getStyle() == Typeface.NORMAL); }
            }, from, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.append(template.substring(at + 3));
        }

        private void otaBlocker() {
            try {
                Intent next = new Intent(activity, SettingsActivity.class);
                next.putExtra("page", "ota_blocker"); next.putExtra("search_anchor", OtaBlocker.KEY);
                activity.startActivity(next);
            } catch (Throwable ignored) { }
            postDelayed(() -> collapse(false), 350);
        }

        private void telegram() {
            CommunityLinks.open(activity, 0);
            postDelayed(() -> collapse(false), 350);
        }

        private int dpi(float v) { return dp(getContext(), v); }
        private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
        private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
        private static float ease(float t) { t = clamp(t); return 1 - (1 - t) * (1 - t) * (1 - t); }

        private void origin() {
            if (from == null || !from.isAttachedToWindow() || from.getWidth() == 0) return;
            getLocationInWindow(at); from.getLocationInWindow(fromAt);
            start.set(fromAt[0] - at[0], fromAt[1] - at[1], fromAt[0] - at[0] + from.getWidth() * from.getScaleX(), fromAt[1] - at[1] + from.getHeight() * from.getScaleY());
        }

        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            int width = MeasureSpec.getSize(w), height = MeasureSpec.getSize(h);
            int topIn = 0, bottomIn = 0;
            WindowInsets in = getRootWindowInsets();
            if (in != null) { topIn = in.getSystemWindowInsetTop(); bottomIn = in.getSystemWindowInsetBottom(); }
            int cardW = Math.min(width - dpi(40), dpi(420));
            int room = height - topIn - bottomIn - dpi(56);
            list.measure(MeasureSpec.makeMeasureSpec(cardW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(dpi(200), room), MeasureSpec.AT_MOST));
            int cardH = list.getMeasuredHeight();
            float left = (width - cardW) / 2f, top = topIn + (height - topIn - bottomIn - cardH) / 2f;
            target.set(left, top, left + cardW, top + cardH);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            list.layout(Math.round(target.left), Math.round(target.top), Math.round(target.left) + list.getMeasuredWidth(), Math.round(target.top) + list.getMeasuredHeight());
            origin();
        }

        @Override protected boolean verifyDrawable(Drawable d) { return d == frost || super.verifyDrawable(d); }

        @Override protected void dispatchDraw(Canvas c) {
            if (getWidth() == 0 || target.isEmpty()) return;
            if (start.isEmpty()) { float cx = target.centerX(), cy = target.centerY(), r = dpi(22); start.set(cx - r, cy - r, cx + r, cy + r); }
            float pg = Math.max(0f, p), qg = Math.max(0f, q), shown = Math.max(clamp(p), clamp(q));
            if (shown <= 0.001f && !open) return;
            scrim.setColor(Color.argb(Math.round((dark ? 120 : 56) * shown), 0, 0, 0));
            c.drawRect(0, 0, getWidth(), getHeight(), scrim);
            now.set(lerp(start.left, target.left, pg), lerp(start.top, target.top, qg), lerp(start.right, target.right, pg), lerp(start.bottom, target.bottom, qg));
            float radius = Math.min(Math.min(now.width(), now.height()) / 2f, lerp(start.height() / 2f, dpi(28), clamp(p * 1.2f)));
            int alpha = open ? 255 : Math.round(255 * clamp(Math.max(p, q) * 3f));
            int save = c.save();
            if (alpha < 255) c.saveLayerAlpha(now.left - dpi(40), now.top - dpi(40), now.right + dpi(40), now.bottom + dpi(40), alpha);
            c.drawRoundRect(now, radius, radius, shade);
            frost.radius(radius); frost.setBounds(Math.round(now.left), Math.round(now.top), Math.round(now.right), Math.round(now.bottom)); frost.draw(c);
            long since = SystemClock.uptimeMillis() - openedAt;
            if (open && since < 1000) {
                float g = ease(since / 1000f), r = lerp(dpi(22), Math.max(target.width(), target.height()) * 1.15f, g);
                if (glowShader == null) { int b = base(status) & 0xffffff; glowShader = new RadialGradient(0, 0, 1, b | (84 << 24), b, Shader.TileMode.CLAMP); glow.setShader(glowShader); }
                glowAt.setScale(Math.max(1, r), Math.max(1, r)); glowAt.postTranslate(start.centerX(), start.centerY()); glowShader.setLocalMatrix(glowAt);
                glow.setAlpha(Math.round(255 * (1 - g)));
                int gs = c.save(); clip.reset(); clip.addRoundRect(now, radius, radius, Path.Direction.CW); c.clipPath(clip); c.drawRect(now, glow); c.restoreToCount(gs);
            }
            float face = clamp(1f - pg * 2.6f);
            if (face > 0) Mark.draw(c, now.centerX(), now.centerY(), Mark.scale(getResources().getDisplayMetrics().density, start.height()), 0f, mark, face);
            stagger();
            int cs = c.save(); clip.reset(); clip.addRoundRect(now, radius, radius, Path.Direction.CW); c.clipPath(clip);
            super.dispatchDraw(c);
            c.restoreToCount(cs);
            c.restoreToCount(save);
        }

        private void stagger() {
            long t = SystemClock.uptimeMillis() - openedAt;
            float out = clamp(q * 1.6f - .35f);
            for (int i = 0; i < body.getChildCount(); i++) {
                View k = body.getChildAt(i);
                float e = open ? ease((t - 120 - i * 34) / 280f) : out;
                k.setAlpha(e); k.setTranslationY((1 - e) * dpi(14));
                float sc = .96f + .04f * e; k.setScaleX(sc); k.setScaleY(sc);
            }
        }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (!open) return true;
            if (action == MotionEvent.ACTION_DOWN) outside = !now.contains(e.getX(), e.getY()) && !PanelBack.atBackEdge(this, e);
            if (outside) { if (action == MotionEvent.ACTION_UP) { outside = false; collapse(true); } else if (action == MotionEvent.ACTION_CANCEL) outside = false; return true; }
            super.dispatchTouchEvent(e);
            return true;
        }

        void expand() {
            open = true; rested = false; openedAt = SystemClock.uptimeMillis(); p = q = 0; pv = qv = 0;
            if (hide != null) { hide.animate().cancel(); hide.setVisibility(INVISIBLE); }
            frost.still(true); frost.moving(true);
            backKey.on();
            announceForAccessibility(I18n.t("Your phone") + ", " + label(status));
            if (!AppMotion.enabled(getContext())) { p = q = 1; rested = true; frost.moving(false); invalidate(); return; }
            tick();
        }

        void collapse(boolean motion) {
            if (!open || gone) return;
            open = false; closedAt = SystemClock.uptimeMillis(); backKey.off(); origin(); frost.moving(true);
            if (!motion || !AppMotion.enabled(getContext())) { finish(); return; }
            tick();
        }

        private void finish() {
            if (gone) return;
            gone = true; ticking = false;
            Choreographer.getInstance().removeFrameCallback(this);
            if (hide != null) { hide.setAlpha(hideAlpha); hide.setVisibility(VISIBLE); }
            frost.release();
            ViewParent v = getParent();
            if (v instanceof ViewGroup) ((ViewGroup) v).removeView(this);
        }

        private void tick() { if (ticking || gone) return; ticking = true; last = 0; Choreographer.getInstance().postFrameCallback(this); }

        private static float[] spring(float x, float v, float to, float k, float zeta, float dt) {
            float c = 2 * zeta * (float) Math.sqrt(k);
            int steps = Math.max(1, Math.round(dt / .004f)); float h = dt / steps;
            for (int i = 0; i < steps; i++) { float a = -k * (x - to) - c * v; v += a * h; x += v * h; }
            return new float[]{x, v};
        }

        @Override public void doFrame(long nanos) {
            if (gone) return;
            float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
            long nowMs = SystemClock.uptimeMillis();
            float to = open ? 1 : 0; float[] r;
            if (open) {
                r = spring(p, pv, to, 380, .62f, dt); p = r[0]; pv = r[1];
                if (nowMs - openedAt > 45) { r = spring(q, qv, to, 300, .66f, dt); q = r[0]; qv = r[1]; }
            } else {
                r = spring(q, qv, to, 520, .92f, dt); q = r[0]; qv = r[1];
                if (nowMs - closedAt > 40) { r = spring(p, pv, to, 460, .78f, dt); p = r[0]; pv = r[1]; }
                if (hide != null && p < .22f) { if (hide.getVisibility() != VISIBLE) hide.setVisibility(VISIBLE); hide.setAlpha(hideAlpha * clamp(1f - p / .22f)); }
            }
            invalidate();
            boolean settled = Math.abs(p - to) < .001f && Math.abs(pv) < .005f && Math.abs(q - to) < .001f && Math.abs(qv) < .005f && (!open || nowMs - openedAt > 1000);
            if (!open && p < .02f && q < .04f) { finish(); return; }
            if (settled) { p = q = to; pv = qv = 0; ticking = false; if (open && !rested) { rested = true; frost.moving(false); } invalidate(); return; }
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Choreographer.getInstance().removeFrameCallback(this); ticking = false; backKey.off();
            if (hide != null && !gone) { hide.setAlpha(hideAlpha); hide.setVisibility(VISIBLE); }
        }
    }
}
