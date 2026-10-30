package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

final class RefreshRate90 {
    private RefreshRate90() {}

    static final String KEY = "pixelsuite_refresh_90";
    private static final String PEAK = "peak_refresh_rate", ROW = "pixelsuite_refresh_rate";
    private static final String F_ROW = "ps_rr_row", F_ANCHOR = "ps_rr_anchor";

    static boolean on(Context c) {
        try { return Settings.Secure.getInt(c.getContentResolver(), KEY, 0) == 1; } catch (Throwable t) { return false; }
    }

    private static float peak(Context c) {
        try { return Settings.System.getFloat(c.getContentResolver(), PEAK); } catch (Throwable t) { return Float.POSITIVE_INFINITY; }
    }

    private static int stopOf(float f) { return Float.isInfinite(f) || f > 60.5f ? 1 : 0; }

    static void installSettings(ClassLoader cl) {
        final Class<?> prefC = Xp.findClass("androidx.preference.Preference", cl);
        Class<?> group = Xp.findClass("androidx.preference.PreferenceGroup", cl);
        if (prefC == null || group == null) return;

        Xp.hookAllMethods(group, "addPreference", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    Object smooth = p.args[0];
                    if (smooth == null || !PEAK.equals(Xp.callMethod(smooth, "getKey"))) return;
                    if (Xp.getAdditionalInstanceField(smooth, F_ROW) != null) return;
                    Context c = (Context) Xp.callMethod(smooth, "getContext");
                    if (c == null || !on(c)) return;
                    Object row = Xp.newInstance(prefC, c);
                    Xp.setAdditionalInstanceField(row, F_ANCHOR, smooth);
                    Xp.setAdditionalInstanceField(smooth, F_ROW, row);
                    Xp.callMethod(row, "setKey", ROW);
                    Xp.callMethod(row, "setTitle", (CharSequence) "Refresh rate");
                    Xp.callMethod(row, "setSummary", (CharSequence) "Highest refresh rate. The screen still slows down when nothing moves.");
                    Xp.callMethod(row, "setPersistent", false);
                    Xp.callMethod(row, "setSelectable", false);
                    try { Xp.callMethod(row, "setIconSpaceReserved", (Boolean) Xp.callMethod(smooth, "isIconSpaceReserved")); } catch (Throwable ignored) { }
                    Xp.callMethod(row, "setOrder", (Integer) Xp.callMethod(smooth, "getOrder"));
                    Xp.callMethod(p.thisObject, "addPreference", row);
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(prefC, "compareTo", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (p.args.length != 1 || p.args[0] == null) return;
                Object a = p.thisObject, b = p.args[0];
                Object anchorA = Xp.getAdditionalInstanceField(a, F_ANCHOR), anchorB = Xp.getAdditionalInstanceField(b, F_ANCHOR);
                if (anchorA == null && anchorB == null) return;
                try {
                    if (anchorA != null) {
                        if (b == anchorA) { p.setResult(1); return; }
                        p.setResult(Xp.callMethod(anchorA, "compareTo", b) == null ? 0 : ((Integer) Xp.callMethod(anchorA, "compareTo", b) >= 0 ? 1 : -1));
                    } else {
                        if (a == anchorB) { p.setResult(-1); return; }
                        p.setResult((Integer) Xp.callMethod(a, "compareTo", anchorB) > 0 ? 1 : -1);
                    }
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(prefC, "onBindViewHolder", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (p.args.length != 1 || p.args[0] == null) return;
                    View item = (View) Xp.getObjectField(p.args[0], "itemView");
                    if (item == null) return;
                    boolean ours = Xp.getAdditionalInstanceField(p.thisObject, F_ANCHOR) != null;
                    RateSlider old = findSlider(item);
                    if (!ours) { if (old != null) ((ViewGroup) old.getParent()).removeView(old); return; }
                    if (old != null) { old.sync(false); return; }
                    View summary = item.findViewById(android.R.id.summary);
                    if (summary == null || !(summary.getParent() instanceof ViewGroup)) return;
                    ViewGroup box = (ViewGroup) summary.getParent();
                    RateSlider s = new RateSlider(item.getContext());
                    int h = dp(item.getContext(), 44);
                    if (box instanceof RelativeLayout) {
                        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(-1, h);
                        if (summary.getId() != View.NO_ID) lp.addRule(RelativeLayout.BELOW, summary.getId());
                        lp.topMargin = dp(item.getContext(), 12); lp.bottomMargin = dp(item.getContext(), 4);
                        box.addView(s, lp);
                    } else {
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h);
                        lp.topMargin = dp(item.getContext(), 12); lp.bottomMargin = dp(item.getContext(), 4);
                        box.addView(s, box.indexOfChild(summary) + 1, lp);
                    }
                } catch (Throwable ignored) { }
            }
        });
    }

    private static RateSlider findSlider(View v) {
        if (v instanceof RateSlider) return (RateSlider) v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { RateSlider r = findSlider(g.getChildAt(i)); if (r != null) return r; } }
        return null;
    }

    private static int dp(Context c, float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics())); }

    private static int sys(Context c, String name, int fallback) {
        try { int id = c.getResources().getIdentifier(name, "color", "android"); return id == 0 ? fallback : c.getColor(id); } catch (Throwable t) { return fallback; }
    }

    static final class RateSlider extends View {
        private static final String[] LABELS = {"60Hz", "120Hz"};
        final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final int cTrack, cThumb, cOn, cOff;
        float pos;
        int stop;
        boolean dragging; float downX, downPos; ValueAnimator anim;
        final ContentObserver obs;

        RateSlider(Context c) {
            super(c);
            boolean dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            boolean superDark = dark && SuperDark.inEffect(c);
            cTrack = superDark ? 0xFF161616 : dark ? sys(c, "system_neutral2_800", 0xFF2F3033) : sys(c, "system_neutral2_100", 0xFFE3E1EC);
            cThumb = dark ? sys(c, "system_accent1_200", 0xFFB9C3FF) : sys(c, "system_accent1_600", 0xFF4355B9);
            cOn = dark ? sys(c, "system_accent1_800", 0xFF22307A) : Color.WHITE;
            cOff = dark ? sys(c, "system_neutral2_200", 0xFFC6C5D0) : sys(c, "system_neutral2_700", 0xFF46464F);
            track.setColor(cTrack); thumb.setColor(cThumb);
            ink.setTextSize(14 * c.getResources().getDisplayMetrics().scaledDensity);
            ink.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            ink.setTextAlign(Paint.Align.CENTER);
            stop = stopOf(peak(c)); pos = stop;
            obs = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean self) { sync(true); }
            };
            setContentDescription("Refresh rate");
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            try { getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(PEAK), false, obs); } catch (Throwable ignored) { }
            sync(false);
        }

        @Override protected void onDetachedFromWindow() {
            try { getContext().getContentResolver().unregisterContentObserver(obs); } catch (Throwable ignored) { }
            super.onDetachedFromWindow();
        }

        void sync(boolean animate) {
            if (dragging) return;
            int s = stopOf(peak(getContext()));
            if (s == stop && Math.abs(pos - s) < 0.001f) return;
            stop = s;
            if (animate) slideTo(s); else { pos = s; invalidate(); }
        }

        private void slideTo(int s) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(pos, s).setDuration(320);
            anim.setInterpolator(new OvershootInterpolator(1.4f));
            anim.addUpdateListener(a -> { pos = (Float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        private void choose(int s) {
            boolean changed = s != stop;
            stop = s;
            slideTo(s);
            if (changed) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                float v = s == 0 ? 60f : Float.POSITIVE_INFINITY;
                try { Settings.System.putFloat(getContext().getContentResolver(), PEAK, v); } catch (Throwable ignored) { }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float seg = getWidth() / 2f;
            if (seg <= 0) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (anim != null) anim.cancel();
                    dragging = false; downX = e.getX(); downPos = pos;
                    ViewParent vp = getParent(); if (vp != null) vp.requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - downX;
                    if (!dragging && Math.abs(dx) > dp(getContext(), 6)) dragging = true;
                    if (dragging) {
                        float before = Math.round(pos);
                        pos = Math.max(0f, Math.min(1f, downPos + dx / seg));
                        if (Math.round(pos) != before) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    int target = dragging ? Math.round(pos) : (int) Math.max(0, Math.min(1, Math.floor(e.getX() / seg)));
                    dragging = false;
                    int was = stop;
                    choose(target);
                    if (target == was) slideTo(target);
                    ViewParent vp2 = getParent(); if (vp2 != null) vp2.requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    dragging = false; slideTo(stop);
                    return true;
            }
            return super.onTouchEvent(e);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f, seg = w / 2f, inset = dp(getContext(), 3);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, track);
            float x = pos * seg;
            r.set(x + inset, inset, x + seg - inset, h - inset);
            c.drawRoundRect(r, rad - inset, rad - inset, thumb);
            float ty = h / 2f - (ink.descent() + ink.ascent()) / 2f;
            for (int i = 0; i < 2; i++) {
                float near = Math.max(0f, 1f - Math.abs(pos - i));
                ink.setColor(blend(cOff, cOn, near));
                c.drawText(LABELS[i], seg * i + seg / 2f, ty, ink);
            }
        }

        private static int blend(int a, int b, float f) {
            return Color.argb(255, (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
        }
    }
}
