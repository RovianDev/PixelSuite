package io.github.pixelsuite;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class ChromeExtensions {
    private ChromeExtensions() {}

    interface Extension {
        default String id() { return title(); }
        String title();
        String summary(View anchor);
        boolean available();
        void draw(Canvas c, float cx, float cy, float size, int ink, boolean dark);
        View content(View anchor, Runnable dismiss);
        default int switchState() { return -1; }
        default void setSwitch(boolean on) { }
    }

    static final List<Extension> EXTENSIONS = new ArrayList<Extension>();
    static final String ID = "extensions";
    static final Extension SELF = new Extension() {
        public String id() { return ID; }
        public String title() { return "Extensions"; }
        public String summary(View anchor) { return "Opens this sheet from the toolbar"; }
        public boolean available() { return true; }
        public void draw(Canvas c, float cx, float cy, float size, int ink, boolean dark) { puzzle(c, cx, cy, size, ink); }
        public View content(View anchor, Runnable dismiss) { return null; }
    };
    static {
        EXTENSIONS.add(SELF);
        EXTENSIONS.add(ChromeVpn.EXTENSION);
        EXTENSIONS.add(new Extension() {
            public String id() { return "adblock"; }
            public String title() { return "Ad Blocker"; }
            public String summary(View anchor) { return available() ? ChromeShield.summary(anchor) : "Needs Pixel Suite premium and the ad blocker on"; }
            public boolean available() { return ChromeShield.enabled(); }
            public int switchState() { Context a = ChromeShield.app(); return available() && a != null && ChromeShield.filtering(a) ? 1 : 0; }
            public void setSwitch(boolean on) { Context a = ChromeShield.app(); if (a != null) ChromeShield.setFiltering(a, on); }
            public void draw(Canvas c, float cx, float cy, float size, int ink, boolean dark) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                Context a = ChromeShield.app();
                boolean lit = available() && (a == null || ChromeShield.filtering(a));
                float w = size * 0.78f, h = size * 0.9f, l = cx - w / 2f, t = cy - h / 2f;
                Path s = new Path();
                s.moveTo(cx, t); s.lineTo(l + w, t + h * 0.16f); s.lineTo(l + w, t + h * 0.46f);
                s.cubicTo(l + w, t + h * 0.74f, cx + w * 0.22f, t + h * 0.9f, cx, t + h);
                s.cubicTo(cx - w * 0.22f, t + h * 0.9f, l, t + h * 0.74f, l, t + h * 0.46f);
                s.lineTo(l, t + h * 0.16f); s.close();
                p.setColor(!available() ? 0xFF9AA0A6 : lit ? (dark ? 0xFFF28B82 : 0xFFB3261E) : ink);
                c.drawPath(s, p);
                p.setColor(!lit && available() ? (dark ? 0xFF1F1F1F : Color.WHITE) : dark ? 0xFF601410 : Color.WHITE);
                float bw = size * 0.42f, bh = size * 0.11f;
                c.drawRoundRect(new RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f), bh / 2f, bh / 2f, p);
            }
            public View content(View anchor, Runnable dismiss) { return ChromeShield.panel(anchor, dismiss, false); }
        });
        EXTENSIONS.add(ChromeDarkPages.EXTENSION);
    }

    static void puzzle(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float s = size * 0.92f, l = cx - s / 2f, t = cy - s / 2f;
        float bl = l, bt = t + s * 0.2f, br = l + s * 0.8f, bb = t + s, k = s * 0.13f, rad = s * 0.1f;
        Path body = new Path();
        body.addRoundRect(new RectF(bl, bt, br, bb), rad, rad, Path.Direction.CW);
        Path knobs = new Path();
        knobs.addCircle((bl + br) / 2f, bt, k, Path.Direction.CW);
        knobs.addRect((bl + br) / 2f - k * 0.7f, bt - k * 0.2f, (bl + br) / 2f + k * 0.7f, bt + k, Path.Direction.CW);
        knobs.addCircle(br, (bt + bb) / 2f, k, Path.Direction.CW);
        knobs.addRect(br - k, (bt + bb) / 2f - k * 0.7f, br + k * 0.2f, (bt + bb) / 2f + k * 0.7f, Path.Direction.CW);
        if (android.os.Build.VERSION.SDK_INT >= 19) { body.op(knobs, Path.Op.UNION); c.drawPath(body, p); }
        else { c.drawPath(body, p); c.drawPath(knobs, p); }
    }

    private static final List<java.lang.ref.WeakReference<Button>> sButtons = new ArrayList<java.lang.ref.WeakReference<Button>>();

    static void attach(LinearLayout row, int at, View toolbar) {
        for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i) instanceof Button) return;
        Button b = new Button(row.getContext(), toolbar);
        int w = Math.round(48 * row.getResources().getDisplayMetrics().density);
        row.addView(b, Math.min(at, row.getChildCount()), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        sButtons.add(new java.lang.ref.WeakReference<Button>(b));
        b.refresh();
    }

    static List<View> buttons() {
        List<View> out = new ArrayList<View>();
        for (int i = sButtons.size() - 1; i >= 0; i--) { Button b = sButtons.get(i).get(); if (b == null) sButtons.remove(i); else out.add(b); }
        return out;
    }

    static void refreshButtons() { for (View b : buttons()) ((Button) b).refresh(); }

    private static View findId(View root, String id) {
        try { if (root.getId() != View.NO_ID && id.equals(root.getResources().getResourceEntryName(root.getId()))) return root; } catch (Throwable ignored) { }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) { View f = findId(g.getChildAt(i), id); if (f != null) return f; }
        }
        return null;
    }

    static final class Button extends View {
        private final java.lang.ref.WeakReference<View> toolbar;
        private java.lang.ref.WeakReference<android.widget.ImageView> tint = new java.lang.ref.WeakReference<android.widget.ImageView>(null);
        Button(Context c, View toolbar) {
            super(c);
            this.toolbar = new java.lang.ref.WeakReference<View>(toolbar);
            setClickable(true); setFocusable(true);
            setContentDescription(I18n.cs("Extensions"));
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) { try { setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { } }
            setOnClickListener(new OnClickListener() { public void onClick(View v) {
                try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                flourish();
                try { show(Button.this); } catch (Throwable ignored) { }
            }});
            setVisibility(GONE);
        }

        private float press = 1f, tap = -1f;
        private android.animation.ValueAnimator pressAnim, tapAnim;
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        private android.animation.ValueAnimator run(android.animation.ValueAnimator old, android.animation.ValueAnimator a) { if (old != null) old.cancel(); a.start(); return a; }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(press, 0.84f);
                    a.setDuration(130);
                    a.setInterpolator(ChromeTabTransition.DECELERATE);
                    a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator v) { press = (Float) v.getAnimatedValue(); invalidate(); } });
                    pressAnim = run(pressAnim, a);
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: release(); break;
            }
            return super.onTouchEvent(e);
        }

        private void release() {
            final float start = press, dip = Math.min(start, 0.88f);
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(620);
            a.setInterpolator(null);
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator v) {
                float f = (Float) v.getAnimatedValue();
                press = f < 0.1f ? ChromeTabTransition.lerp(start, dip, ChromeTabTransition.DECELERATE.getInterpolation(f / 0.1f))
                        : ChromeTabTransition.lerp(dip, 1f, ChromeTabTransition.SPRING.getInterpolation((f - 0.1f) / 0.9f));
                invalidate();
            }});
            pressAnim = run(pressAnim, a);
        }

        private void flourish() {
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(560);
            a.setInterpolator(null);
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator v) { tap = (Float) v.getAnimatedValue(); invalidate(); } });
            a.addListener(new android.animation.AnimatorListenerAdapter() { @Override public void onAnimationEnd(android.animation.Animator x) { tap = -1f; invalidate(); } });
            tapAnim = run(tapAnim, a);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (pressAnim != null) pressAnim.cancel();
            if (tapAnim != null) tapAnim.cancel();
            press = 1f; tap = -1f;
        }
        void refresh() {
            boolean on = ID.equals(ChromeShield.pinnedId(getContext()));
            if (!on) { if (getVisibility() != GONE) setVisibility(GONE); return; }
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            invalidate();
        }
        private int iconColor() {
            android.widget.ImageView src = tint.get();
            if (src == null || !src.isAttachedToWindow()) {
                View t = toolbar.get();
                View f = t == null ? null : findId(t, "tab_switcher_button");
                src = f instanceof android.widget.ImageView ? (android.widget.ImageView) f : null;
                tint = new java.lang.ref.WeakReference<android.widget.ImageView>(src);
            }
            if (src != null && src.getImageTintList() != null) return src.getImageTintList().getColorForState(src.getDrawableState(), src.getImageTintList().getDefaultColor());
            return night(getContext()) ? 0xFFE3E3E3 : 0xFF444746;
        }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, cx = getWidth() / 2f, cy = getHeight() / 2f, s = 11 * d;
            int save = c.save();
            try {
                c.scale(press, press, cx, cy);
                if (tap >= 0f) c.rotate(16f * (float) Math.sin(3 * Math.PI * tap) * (1f - tap), cx, cy + s * 0.2f);
                ink.setStyle(Paint.Style.STROKE); ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
                ink.setStrokeWidth(2 * d); ink.setColor(iconColor());
                ChromeBarIcons.extensions(c, path, ink, cx, cy, s);
            } finally { c.restoreToCount(save); }
        }
    }

    static void openPixelSuite(Context c, String page) {
        try {
            c.startActivity(new Intent().setClassName("io.github.roviandev.pixelsuite", "io.github.pixelsuite.SettingsActivity").putExtra("page", page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (Throwable ignored) { }
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static boolean night(Context c) {
        return ChromeSuperDark.incognitoSelected() || (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(I18n.cs(s));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface((medium ? I18n.medium() : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)));
        return t;
    }

    private static void ripple(View v) {
        TypedValue tv = new TypedValue();
        if (v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            try { v.setForeground(v.getContext().getDrawable(tv.resourceId)); } catch (Throwable ignored) { }
        }
    }

    private static View gear(final Context c, final int color) {
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas k) {
                float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density, r = 9 * d, cx = w / 2f, cy = h / 2f;
                p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f * d); p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    k.drawLine(cx + (float) Math.cos(a) * r * 0.62f, cy + (float) Math.sin(a) * r * 0.62f, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, p);
                }
                k.drawCircle(cx, cy, r * 0.62f, p);
                k.drawCircle(cx, cy, r * 0.24f, p);
            }
        };
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) { try { v.setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { } }
        v.setContentDescription(I18n.cs("Manage extensions"));
        return v;
    }

    private static View glyph(final Context c, final int color, final boolean back) {
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas k) {
                float w = getWidth(), h = getHeight(), s = Math.min(w, h) * 0.18f, d = getResources().getDisplayMetrics().density;
                p.setColor(color); p.setStrokeWidth(2 * d); p.setStrokeCap(Paint.Cap.ROUND); p.setStyle(Paint.Style.STROKE);
                if (back) {
                    k.drawLine(w / 2 + s * 0.6f, h / 2 - s * 1.2f, w / 2 - s * 0.6f, h / 2, p);
                    k.drawLine(w / 2 - s * 0.6f, h / 2, w / 2 + s * 0.6f, h / 2 + s * 1.2f, p);
                } else {
                    k.drawLine(w / 2 - s, h / 2 - s, w / 2 + s, h / 2 + s, p);
                    k.drawLine(w / 2 + s, h / 2 - s, w / 2 - s, h / 2 + s, p);
                }
            }
        };
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) { try { v.setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { } }
        v.setContentDescription(back ? "Back" : "Close");
        return v;
    }

    private static List<Extension> ordered(Context c) {
        return new ArrayList<Extension>(EXTENSIONS);
    }

    private static final android.view.animation.Interpolator EMPHASIZED = new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);

    private static final class Cell extends LinearLayout {
        final Extension e;
        final Icon icon;
        Toggle mini;
        Cell(Context c, Extension e, Icon icon) { super(c); this.e = e; this.icon = icon; }
    }

    private static final class Icon extends View {
        final Extension e; final int disc, fg, accent; final boolean dark;
        float badge, ring;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c, Extension e, int disc, int fg, int accent, boolean dark, boolean pinned) {
            super(c); this.e = e; this.disc = disc; this.fg = fg; this.accent = accent; this.dark = dark; badge = pinned ? 1f : 0f;
        }
        @Override protected void onDraw(Canvas k) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.FILL); p.setColor(disc);
            k.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - 3 * d, p);
            e.draw(k, w / 2f, h / 2f, (Math.min(w, h) - 6 * d) * 0.55f, fg, dark);
            if (ring > 0f) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * d); p.setColor(accent); p.setAlpha(Math.round(255 * ring));
                k.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - 1.5f * d, p); p.setAlpha(255);
            }
            if (badge > 0f) {
                float r = 9.5f * d * badge, cx = w - 9.5f * d, cy = 9.5f * d;
                p.setStyle(Paint.Style.FILL); p.setColor(accent); k.drawCircle(cx, cy, r, p);
                pin(k, cx, cy, r * 1.15f, dark ? 0xFF062E6F : Color.WHITE, p);
            }
        }
    }

    static void pin(Canvas k, float cx, float cy, float size, int color, Paint p) {
        p.setColor(color); p.setStyle(Paint.Style.FILL);
        float s = size * 0.5f;
        k.save(); k.rotate(45, cx, cy);
        k.drawRoundRect(new RectF(cx - s * 0.42f, cy - s * 0.95f, cx + s * 0.42f, cy - s * 0.15f), s * 0.18f, s * 0.18f, p);
        k.drawRect(cx - s * 0.7f, cy - s * 0.2f, cx + s * 0.7f, cy + s * 0.05f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, s * 0.16f)); p.setStrokeCap(Paint.Cap.ROUND);
        k.drawLine(cx, cy, cx, cy + s * 0.85f, p);
        k.restore(); p.setStyle(Paint.Style.FILL);
    }

    static void show(final View anchor) {
        final Context c = anchor.getContext();
        try { ChromeVpn.prime(c); } catch (Throwable ignored) { }
        final boolean dark = night(c);
        final int fg = dark ? 0xFFE3E3E3 : 0xFF1F1F1F, muted = dark ? 0xFFA8ABAF : 0xFF5F6368;
        final int bg = ChromeLayout.sheetColor(dark ? 0xFF1F1F1F : 0xFFFFFFFF, anchor);
        final int line = ChromeTabTransition.mix(bg, fg, dark ? 0.16f : 0.10f), accent = dark ? 0xFF8AB4F8 : 0xFF1F5FB4;
        final int discColor = ChromeTabTransition.mix(bg, fg, dark ? 0.10f : 0.06f);

        final LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(bg);
        shape.setCornerRadius(dp(c, 22));
        int outline = ChromeLayout.sheetOutline(bg, fg);
        if (outline != 0) shape.setStroke(Math.max(1, dp(c, 1)), outline);
        ChromeLayout.refineSheet(anchor, shape, Math.max(1, dp(c, 1)), fg);
        root.setBackground(shape);
        root.setClipToOutline(true);
        int width = Math.min(c.getResources().getDisplayMetrics().widthPixels - dp(c, 24), dp(c, 348));
        final ChromeSheetMotion.Sheet pw = new ChromeSheetMotion.Sheet(root, width, ViewGroup.LayoutParams.WRAP_CONTENT, anchor, dp(c, 22));
        final Runnable dismiss = new Runnable() { public void run() { pw.dismiss(); } };
        final Runnable[] home = new Runnable[1];

        home[0] = new Runnable() { public void run() {
            root.removeAllViews();
            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(c, 20), dp(c, 6), dp(c, 6), 0);
            head.addView(text(c, "Extensions", 18, fg, true), new LinearLayout.LayoutParams(0, -2, 1));
            View manage = gear(c, fg);
            manage.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pw.dismiss(); openPixelSuite(c, "extensions"); } });
            head.addView(manage, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
            View close = glyph(c, fg, false);
            close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pw.dismiss(); } });
            head.addView(close, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
            ChromeSheetMotion.springy(manage, manage);
            ChromeSheetMotion.springy(close, close);
            root.addView(head, new LinearLayout.LayoutParams(-1, -2));

            final LinearLayout grid = new LinearLayout(c);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            grid.setPadding(dp(c, 8), 0, dp(c, 8), dp(c, 6));
            final List<Cell> cells = new ArrayList<Cell>();
            final Extension[] target = new Extension[1];

            final android.widget.FrameLayout strip = new android.widget.FrameLayout(c);
            final TextView hint = text(c, "Hold an extension to pin it to the toolbar", 13, muted, false);
            hint.setGravity(Gravity.CENTER_VERTICAL);
            hint.setPadding(dp(c, 20), 0, dp(c, 16), 0);
            strip.addView(hint, new android.widget.FrameLayout.LayoutParams(-1, dp(c, 44)));
            final LinearLayout pinRow = new LinearLayout(c);
            pinRow.setGravity(Gravity.CENTER_VERTICAL);
            pinRow.setPadding(dp(c, 20), 0, dp(c, 14), 0);
            ripple(pinRow);
            final View pinGlyph = new View(c) { @Override protected void onDraw(Canvas k) { pin(k, getWidth() / 2f, getHeight() / 2f, getHeight() * 0.9f, accent, new Paint(Paint.ANTI_ALIAS_FLAG)); } };
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18)); gp.rightMargin = dp(c, 12);
            pinRow.addView(pinGlyph, gp);
            final TextView pinText = text(c, "", 14, fg, false);
            pinRow.addView(pinText, new LinearLayout.LayoutParams(0, -2, 1));
            final Toggle pinToggle = new Toggle(c, false, accent, dark);
            pinRow.addView(pinToggle, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 26)));
            pinRow.setVisibility(View.INVISIBLE);
            strip.addView(pinRow, new android.widget.FrameLayout.LayoutParams(-1, dp(c, 44)));

            final Runnable[] lay = new Runnable[1];
            lay[0] = new Runnable() { public void run() {
                final java.util.Map<Cell, Integer> before = new java.util.HashMap<Cell, Integer>();
                for (Cell k : cells) if (k.getParent() != null) before.put(k, k.getLeft());
                grid.removeAllViews();
                for (Extension e : ordered(c)) for (Cell k : cells) if (k.e == e) grid.addView(k, new LinearLayout.LayoutParams(0, -2, 1));
                if (before.isEmpty()) return;
                grid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                    public boolean onPreDraw() {
                        grid.getViewTreeObserver().removeOnPreDrawListener(this);
                        for (Cell k : cells) {
                            Integer old = before.get(k);
                            if (old == null || old == k.getLeft()) continue;
                            k.setTranslationX(old - k.getLeft());
                            k.animate().translationX(0).setDuration(380).setInterpolator(EMPHASIZED).start();
                        }
                        return true;
                    }
                });
            }};

            for (final Extension e : EXTENSIONS) {
                final Icon icon = new Icon(c, e, discColor, fg, accent, dark, e.id().equals(ChromeShield.pinnedId(c)));
                final Cell cell = new Cell(c, e, icon);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, dp(c, 6), 0, dp(c, 6));
                ripple(cell);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(c, 52), dp(c, 52)));
                final TextView name = text(c, e.title(), 12, e.available() ? fg : muted, false);
                name.setGravity(Gravity.CENTER);
                name.setPadding(0, dp(c, 4), 0, 0);
                cell.addView(name, new LinearLayout.LayoutParams(-2, -2));
                if (e.switchState() >= 0) {
                    final Toggle mini = new Toggle(c, e.switchState() == 1, accent, dark);
                    cell.mini = mini;
                    android.widget.FrameLayout hit = new android.widget.FrameLayout(c);
                    hit.addView(mini, new android.widget.FrameLayout.LayoutParams(dp(c, 32), dp(c, 20), Gravity.CENTER));
                    hit.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                        if (!e.available()) { openDetail(c, root, e, anchor, dismiss, home[0], fg, muted, line); return; }
                        try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                        boolean next = !mini.on;
                        mini.set(next);
                        e.setSwitch(next);
                        icon.invalidate();
                    }});
                    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(c, 56), dp(c, 28)); hp.topMargin = dp(c, 2);
                    cell.addView(hit, hp);
                    final Runnable update = new Runnable() { public void run() {
                        mini.set(e.switchState() == 1); icon.invalidate(); name.setTextColor(e.available() ? fg : muted); cell.setAlpha(e.available() ? 1f : 0.5f);
                    }};
                    cell.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        public void onViewAttachedToWindow(View v) { ChromeVpn.watch(update); ChromeDarkPages.watch(update); }
                        public void onViewDetachedFromWindow(View v) { ChromeVpn.unwatch(update); ChromeDarkPages.unwatch(update); }
                    });
                }
                cell.setAlpha(e.available() ? 1f : 0.5f);
                ChromeSheetMotion.springy(cell, icon);
                cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                    if (e == SELF) {
                        icon.animate().cancel();
                        icon.animate().scaleX(1.08f).scaleY(1.08f).setStartDelay(0).setDuration(110).setInterpolator(EMPHASIZED).withEndAction(new Runnable() { public void run() {
                            icon.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                        }}).start();
                        if (pinRow.getVisibility() != View.VISIBLE) { hint.setAlpha(0.4f); hint.animate().alpha(1f).setDuration(300).start(); }
                        return;
                    }
                    openDetail(c, root, e, anchor, dismiss, home[0], fg, muted, line);
                }});
                cell.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    target[0] = e;
                    for (final Cell k : cells) {
                        final float from = k.icon.ring, to = k == cell ? 1f : 0f;
                        if (from == to) continue;
                        android.animation.ValueAnimator r = android.animation.ValueAnimator.ofFloat(from, to).setDuration(200);
                        r.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator a) { k.icon.ring = (Float) a.getAnimatedValue(); k.icon.invalidate(); } });
                        r.start();
                    }
                    icon.animate().cancel();
                    icon.animate().scaleX(1.12f).scaleY(1.12f).setStartDelay(0).setDuration(130).setInterpolator(EMPHASIZED).withEndAction(new Runnable() { public void run() {
                        icon.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
                    }}).start();
                    boolean ok = e.available();
                    pinText.setText(I18n.cs(ok ? "Pin " + e.title() + " to toolbar" : e.title() + " needs Pixel Suite premium"));
                    pinToggle.setVisibility(ok ? View.VISIBLE : View.INVISIBLE);
                    pinToggle.snap(e.id().equals(ChromeShield.pinnedId(c)));
                    if (pinRow.getVisibility() != View.VISIBLE) {
                        pinRow.setVisibility(View.VISIBLE);
                        pinRow.setAlpha(0f); pinRow.setTranslationY(dp(c, 10));
                        pinRow.animate().alpha(1f).translationY(0).setDuration(240).setInterpolator(EMPHASIZED).start();
                        hint.animate().alpha(0f).translationY(-dp(c, 10)).setDuration(160).start();
                    } else {
                        pinText.setAlpha(0f); pinText.animate().alpha(1f).setDuration(180).start();
                    }
                    return true;
                }});
                cells.add(cell);
            }
            lay[0].run();
            root.addView(grid, new LinearLayout.LayoutParams(-1, -2));

            View rule = new View(c);
            rule.setBackgroundColor(line);
            root.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1) / 2)));

            pinRow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                final Extension e = target[0];
                if (e == null || !e.available()) return;
                try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                final String old = ChromeShield.pinnedId(c);
                final String now = old.equals(e.id()) ? "" : e.id();
                pinToggle.set(!now.isEmpty());
                for (final Cell k : cells) {
                    final float from = k.icon.badge, to = k.e.id().equals(now) ? 1f : 0f;
                    if (from == to) continue;
                    android.animation.ValueAnimator b = android.animation.ValueAnimator.ofFloat(from, to).setDuration(to > from ? 360 : 180);
                    b.setStartDelay(to > from ? 140 : 0);
                    if (to > from) b.setInterpolator(new android.view.animation.OvershootInterpolator(2.2f));
                    else b.setInterpolator(new android.view.animation.AccelerateInterpolator());
                    b.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator a) { k.icon.badge = (Float) a.getAnimatedValue(); k.icon.invalidate(); } });
                    b.start();
                }
                swapToolbar(c, old, now, new Runnable() { public void run() { lay[0].run(); } });
            }});
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(c, 44)); sp.bottomMargin = dp(c, 4);
            root.addView(strip, sp);
        }};
        home[0].run();

        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(c, 16));
        pw.setOnDismissListener(new PopupWindow.OnDismissListener() { public void onDismiss() { ChromeShield.refreshAll(); try { ChromeVpn.refreshButtons(); } catch (Throwable ignored) { } try { refreshButtons(); } catch (Throwable ignored) { } try { ChromeDarkPages.refreshButtons(); } catch (Throwable ignored) { } } });
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int rootH = anchor.getRootView().getHeight();
        final boolean fromTop = at[1] + anchor.getHeight() / 2 < rootH / 2;
        if (fromTop) pw.showAtLocation(anchor, Gravity.TOP | Gravity.END, dp(c, 8), at[1] + anchor.getHeight() - dp(c, 2));
        else pw.showAtLocation(anchor, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, rootH - at[1] + dp(c, 6));
        pw.open(new Runnable() { public void run() { enterHome(root, fromTop); } });
    }

    private static void enterHome(LinearLayout root, boolean fromTop) {
        View grid = null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            if (v instanceof LinearLayout && ((LinearLayout) v).getChildCount() > 0 && ((LinearLayout) v).getChildAt(0) instanceof Cell) grid = v;
        }
        ChromeSheetMotion.enter(root, fromTop, grid);
        if (grid instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) grid;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (!(g.getChildAt(i) instanceof Cell)) continue;
                Cell k = (Cell) g.getChildAt(i);
                ChromeSheetMotion.popIn(k.icon, 110 + 55L * i);
                for (int j = 1; j < k.getChildCount(); j++) {
                    View under = k.getChildAt(j);
                    under.setAlpha(0f);
                    under.animate().alpha(1f).setStartDelay(170 + 55L * i).setDuration(260).setInterpolator(ChromeTabTransition.DECELERATE).start();
                }
            }
        }
    }

    private static void openDetail(final Context c, final LinearLayout root, final Extension e, final View anchor, final Runnable dismiss, final Runnable home, final int fg, final int muted, final int line) {
        ChromeSheetMotion.swap(root, true, new Runnable() { public void run() { detail(c, root, e, anchor, dismiss, home, fg, muted, line); } });
    }

    private static List<View> toolbarViews(String id) {
        List<View> out = new ArrayList<View>();
        try { if ("adblock".equals(id)) out.addAll(ChromeShield.buttons()); else if (ChromeVpn.ID.equals(id)) out.addAll(ChromeVpn.buttons()); else if (ChromeDarkPages.ID.equals(id)) out.addAll(ChromeDarkPages.buttons()); else if (ID.equals(id)) out.addAll(buttons()); } catch (Throwable ignored) { }
        return out;
    }

    private static void swapToolbar(final Context c, String old, final String now, final Runnable then) {
        final List<View> outs = new ArrayList<View>();
        for (View v : toolbarViews(old)) if (v.getVisibility() == View.VISIBLE && v.isAttachedToWindow()) outs.add(v);
        final Runnable swap = new Runnable() {
            boolean done;
            public void run() {
                if (done) return; done = true;
                ChromeShield.setPinnedId(c, now);
                for (View v : outs) { v.animate().cancel(); v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(1f); v.setRotation(0f); }
                for (View v : toolbarViews(now)) {
                    if (v.getVisibility() != View.VISIBLE) continue;
                    v.animate().cancel();
                    v.setScaleX(0.25f); v.setScaleY(0.25f); v.setAlpha(0f); v.setRotation(-40f);
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f).setDuration(420).setInterpolator(new android.view.animation.OvershootInterpolator(1.9f)).start();
                }
                if (then != null) then.run();
            }
        };
        if (outs.isEmpty()) { swap.run(); return; }
        for (View v : outs) {
            v.animate().cancel();
            v.animate().scaleX(0.25f).scaleY(0.25f).alpha(0f).rotation(40f).setDuration(170).setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f)).withEndAction(swap).start();
        }
    }

    private static void detail(Context c, LinearLayout root, Extension e, View anchor, Runnable dismiss, final Runnable home, int fg, int muted, int line) {
        root.removeAllViews();
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(c, 6), dp(c, 6), dp(c, 6), 0);
        View back = glyph(c, fg, true);
        final LinearLayout card = root;
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ChromeSheetMotion.swap(card, false, home); } });
        head.addView(back, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        ChromeSheetMotion.springy(back, back);
        TextView title = text(c, e.title(), 18, fg, true);
        title.setPadding(dp(c, 4), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        View close = glyph(c, fg, false);
        final Runnable d = dismiss;
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.run(); } });
        head.addView(close, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        ChromeSheetMotion.springy(close, close);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        if (e.available()) {
            root.addView(e.content(anchor, dismiss), new LinearLayout.LayoutParams(-1, -2));
        } else {
            TextView none = text(c, e.summary(anchor), 15, muted, false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(dp(c, 24), dp(c, 24), dp(c, 24), dp(c, 32));
            root.addView(none, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    static final class Toggle extends View {
        boolean on;
        private float pos;
        private android.animation.ValueAnimator anim;
        private final int accent;
        private final boolean dark;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Toggle(Context c, boolean on, int accent, boolean dark) { super(c); this.on = on; pos = on ? 1f : 0f; this.accent = accent; this.dark = dark; }
        void set(boolean v) {
            if (v == on && anim == null) return;
            on = v;
            if (!isAttachedToWindow()) { snap(v); return; }
            if (anim != null) anim.cancel();
            anim = android.animation.ValueAnimator.ofFloat(pos, v ? 1f : 0f).setDuration(380);
            anim.setInterpolator(ChromeTabTransition.SPRING);
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator a) { pos = (Float) a.getAnimatedValue(); invalidate(); } });
            anim.addListener(new android.animation.AnimatorListenerAdapter() { public void onAnimationEnd(android.animation.Animator a) { anim = null; } });
            anim.start();
        }
        void snap(boolean v) { if (anim != null) { anim.cancel(); anim = null; } on = v; pos = v ? 1f : 0f; invalidate(); }
        @Override public boolean onTouchEvent(MotionEvent e) { return false; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            float inset = Math.max(2 * d, h * 0.125f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ChromeTabTransition.mix(dark ? 0xFF444746 : 0xFFE3E3E8, accent, pos));
            c.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, p);
            p.setColor(ChromeTabTransition.mix(dark ? 0xFF8E918F : 0xFF79747E, dark ? 0xFF062E6F : Color.WHITE, pos));
            float rOff = h / 2f - inset * 2f, rOn = h / 2f - inset;
            float r = rOff + (rOn - rOff) * pos;
            float x = h / 2f + (w - h) * pos;
            c.drawCircle(x, h / 2f, r, p);
        }
    }
}
