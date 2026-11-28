package io.github.pixelsuite;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

final class SearchEditor extends Dialog implements Choreographer.FrameCallback {
    private static final String TAG = "PixelSuite";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<SearchEditor> shown = new WeakReference<SearchEditor>(null);

    static final class Item { String label; Drawable icon; Runnable run; }

    private final SearchDock dock;
    private PixelSearch.Look look;
    private final Bloom view;
    private final ScrollView scroll;
    private final LinearLayout rows;
    private final RectF bar = new RectF(), target = new RectF(), now = new RectF();
    private final HashMap<String, Runnable> writes = new HashMap<String, Runnable>();
    private float p, pv, q, qv;
    private boolean open, ticking, sub, done;
    private long last, openedAt, closedAt;

    private SearchEditor(SearchDock dock) {
        super(dock.getContext(), android.R.style.Theme_Translucent_NoTitleBar);
        this.dock = dock;
        Context c = getContext();
        look = new PixelSearch.Look(c);
        view = new Bloom(c);
        scroll = new Bounce(c);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(30));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(14), dp(16), dp(14));
        scroll.addView(rows, new FrameLayout.LayoutParams(-1, -2));
        view.addView(scroll);
        main();
        setContentView(view);
        dock.editing = true;
        new Thread(() -> { try { appRows(dock.getContext()); } catch (Throwable ignored) { } }, "PixelSearchEditorWarm").start();
        dock.reload();
        dock.wiggle(true);
        setOnDismissListener(d -> {
            done = true;
            PixelSearch.LIVE.clear();
            PixelSearch.liveSlots = null;
            dock.dragKey = null;
            dock.editing = false;
            dock.wiggle(false);
            dock.reload();
            SearchDock.ensure();
        });
    }

    static void open(SearchDock dock) {
        if (!PatronGate.ok(dock.getContext())) { SearchDock.ensure(); return; }
        SearchEditor old = shown.get();
        if (old != null && old.isShowing()) return;
        try {
            SearchEditor e = new SearchEditor(dock);
            shown = new WeakReference<SearchEditor>(e);
            e.show();
        } catch (Throwable t) { android.util.Log.w(TAG, "search editor: open", t); dock.openSettings(); }
    }

    static void closeSoon() {
        SearchEditor e = shown.get();
        if (e != null && e.isShowing()) e.close();
    }

    static void dismissNow() {
        SearchEditor e = shown.get();
        if (e != null && e.isShowing() && !e.saving) try { e.dismiss(); } catch (Throwable ignored) { }
    }

    private boolean saving;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(-1, -1);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setWindowAnimations(0);
        try { Window.class.getMethod("setDecorFitsSystemWindows", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override protected void onStart() {
        super.onStart();
        open = true;
        openedAt = SystemClock.uptimeMillis();
        tick();
    }

    @Override public void onBackPressed() { if (sub) { main(); regrow(); } else close(); }

    void close() {
        if (!open) return;
        open = false;
        closePin();
        if (dock.wiggling()) dock.wiggle(false);
        closedAt = SystemClock.uptimeMillis();
        tick();
    }

    int dp(float v) { return Math.round(v * getContext().getResources().getDisplayMetrics().density); }

    private void tick() { if (ticking) return; ticking = true; last = 0; Choreographer.getInstance().postFrameCallback(this); }

    private void regrow() { q = 0.6f; qv = 0; scroll.scrollTo(0, 0); view.requestLayout(); tick(); }

    @Override public void doFrame(long nanos) {
        if (done) { ticking = false; return; }
        float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
        long nowMs = SystemClock.uptimeMillis();
        float to = open ? 1 : 0;
        float[] r;
        if (open) {
            r = WidgetPanel.spring(p, pv, to, 380, .62f, dt); p = r[0]; pv = r[1];
            if (nowMs - openedAt > 45) { r = WidgetPanel.spring(q, qv, to, 300, .66f, dt); q = r[0]; qv = r[1]; }
        } else {
            float u = Math.min(1f, (nowMs - closedAt) / 200f);
            p = q = 1f - u; pv = qv = 0;
        }
        view.invalidate();
        boolean settled = Math.abs(p - to) < .001f && Math.abs(pv) < .005f && Math.abs(q - to) < .001f && Math.abs(qv) < .005f;
        if (!settled) { Choreographer.getInstance().postFrameCallback(this); return; }
        p = q = to; pv = qv = 0; ticking = false;
        view.invalidate();
        if (!open) try { dismiss(); } catch (Throwable ignored) { }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }

    static final class Bounce extends ScrollView {
        private float lastY, startY, stretch, eaten;
        private boolean touching, moved, childOwns;
        private android.animation.ValueAnimator back;
        Bounce(Context c) { super(c); }
        private View body() { return getChildCount() > 0 ? getChildAt(0) : null; }
        private float limit() { return 90 * getResources().getDisplayMetrics().density; }
        private float shown(float s) { float m = limit(), x = Math.abs(s); return Math.signum(s) * m * (1 - 1 / (x * 0.55f / m + 1)); }
        private float raw(float t) { float m = limit(), x = Math.min(Math.abs(t), m * 0.98f); return Math.signum(t) * (m / (1 - x / m) - m) / 0.55f; }
        private int room() { View b = body(); return b == null ? 0 : Math.max(0, b.getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom())); }
        private void stopBack() { if (back != null) { back.cancel(); back = null; } }
        private void show(float s) { stretch = s; View b = body(); if (b != null) b.setTranslationY(shown(s)); }
        private void settle(final float from, float kick) {
            stopBack();
            final View b = body();
            if (b == null) { stretch = 0; return; }
            final float start = from;
            final float velocity = kick;
            back = android.animation.ValueAnimator.ofFloat(0f, 1f);
            back.setDuration(560);
            back.setInterpolator(null);
            back.addUpdateListener(a -> {
                float t = a.getAnimatedFraction() * 0.56f;
                double w = 15, z = 0.82, wd = w * Math.sqrt(1 - z * z), e = Math.exp(-z * w * t);
                float y = (float) (e * (start * Math.cos(wd * t) + (velocity + z * w * start) / wd * Math.sin(wd * t)));
                b.setTranslationY(y);
                stretch = raw(y);
            });
            back.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { if (back == a || back == null) { b.setTranslationY(0); stretch = 0; back = null; } }
            });
            back.start();
        }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) { if (disallow) childOwns = true; super.requestDisallowInterceptTouchEvent(disallow); }
        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            View b = body();
            if (b == null) return super.dispatchTouchEvent(e);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                touching = true; moved = false; childOwns = false; eaten = 0;
                lastY = startY = e.getRawY();
                if (back != null) {
                    float t = b.getTranslationY();
                    stopBack();
                    if (Math.abs(t) < 10 * getResources().getDisplayMetrics().density) { b.setTranslationY(0); stretch = 0; }
                    else show(raw(t));
                }
            } else if (a == MotionEvent.ACTION_MOVE) {
                float y = e.getRawY(), d = y - lastY;
                lastY = y;
                if (!moved && Math.abs(y - startY) > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) moved = true;
                if (moved && !childOwns && d != 0) {
                    float take = 0;
                    if (stretch != 0) {
                        float next = stretch + d;
                        if (next * stretch <= 0) { take = -stretch; next = 0; }
                        else take = d;
                        show(next);
                    } else {
                        int at = getScrollY(), room = room();
                        float free = d > 0 ? at : room - at;
                        if (Math.abs(d) > free) {
                            float over = d > 0 ? d - free : d + free;
                            take = over;
                            show(over);
                        }
                    }
                    eaten += take;
                }
            }
            boolean up = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
            boolean pulled = stretch != 0;
            if (up) touching = false;
            MotionEvent fed = e;
            if (up && pulled && moved && a == MotionEvent.ACTION_UP) { fed = MotionEvent.obtain(e); fed.setAction(MotionEvent.ACTION_CANCEL); }
            fed.offsetLocation(0, -eaten);
            boolean handled = super.dispatchTouchEvent(fed);
            fed.offsetLocation(0, eaten);
            if (fed != e) fed.recycle();
            if (up) { eaten = 0; if (pulled) settle(b.getTranslationY(), 0); }
            return handled || pulled;
        }
        @Override protected void onOverScrolled(int x, int y, boolean clampedX, boolean clampedY) {
            super.onOverScrolled(x, y, clampedX, clampedY);
            View b = body();
            if (touching || !clampedY || stretch != 0 || back != null || b == null || room() == 0) return;
            try { fling(0); } catch (Throwable ignored) { }
            float d = getResources().getDisplayMetrics().density;
            settle(0, (y <= 0 ? 1 : -1) * 520 * d);
        }
    }

    final class Bloom extends FrameLayout {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint();
        final Path shape = new Path(), hole = new Path();
        boolean barTouch, swallow;
        boolean holdFired;
        float holdX, holdY;
        final Runnable hold = () -> {
            if (!barTouch || Bloom.this.drag.active() || !open) return;
            holdFired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            dock.pulse();
            if (dock.wiggling()) close();
            else { dock.wiggle(true); invalidate(); }
        };
        Object[] pinDown;
        final SearchDrag drag = new SearchDrag(dock, this, new SearchDrag.Host() {
            public boolean allowed() { return SearchEditor.this.allowed(); }
            public void changed(String slots) { PixelSearch.liveSlots = slots; dock.reload(); }
            public void dropped(String slots) { PixelSearch.liveSlots = null; if (slots != null) put(PixelSearch.SLOTS_KEY, slots); else dock.reload(); }
        });
        Bloom(Context c) { super(c); setWillNotDraw(false); }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN && pinCard != null) {
                int[] at = new int[2]; pinCard.getLocationOnScreen(at);
                if (e.getRawX() < at[0] || e.getRawX() > at[0] + pinCard.getWidth() || e.getRawY() < at[1] || e.getRawY() > at[1] + pinCard.getHeight()) { closePin(); pinDown = null; swallow = true; return true; }
            }
            if (swallow) { if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) swallow = false; return true; }
            if (a == MotionEvent.ACTION_DOWN && open && dock.wiggling()) {
                pinDown = null;
                int[] at = new int[2]; getLocationOnScreen(at);
                for (Object[] pin : dock.pins()) {
                    float px = (Float) pin[1] - at[0], py = (Float) pin[2] - at[1] - dp(15);
                    if (Math.abs(e.getX() - px) < dp(20) && Math.abs(e.getY() - py) < dp(18)) { pinDown = pin; return true; }
                }
            }
            if (pinDown != null) {
                if (a == MotionEvent.ACTION_UP) {
                    int[] at = new int[2]; getLocationOnScreen(at);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    pinMenu((String) pinDown[0], (Float) pinDown[1] - at[0], (Float) pinDown[2] - at[1]);
                }
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) pinDown = null;
                return true;
            }
            if (a == MotionEvent.ACTION_DOWN) {
                barTouch = open && e.getY() >= bar.top - dp(8) && e.getY() <= bar.bottom + dp(8);
                holdFired = false; holdX = e.getRawX(); holdY = e.getRawY();
                removeCallbacks(hold);
                float[] spot = new float[2];
                dock.toLocal(e.getRawX(), e.getRawY(), spot);
                if (barTouch && dock.zoneAt(spot[0], spot[1]) == null) postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout());
                if (barTouch) { drag.down(e); return true; }
            }
            if (!barTouch) return super.dispatchTouchEvent(e);
            if (a == MotionEvent.ACTION_MOVE) {
                if (Math.hypot(e.getRawX() - holdX, e.getRawY() - holdY) > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) removeCallbacks(hold);
                if (!holdFired) drag.move(e);
            }
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                barTouch = false;
                boolean tap = a == MotionEvent.ACTION_UP && !drag.active();
                drag.up(a == MotionEvent.ACTION_UP);
                removeCallbacks(hold);
                if (holdFired) holdFired = false;
                else if (tap) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    float[] spot = new float[2];
                    dock.toLocal(e.getRawX(), e.getRawY(), spot);
                    String empty = dock.emptyAt(spot[0], spot[1]);
                    if (empty != null) {
                        float[] at = dock.spot(empty);
                        int[] me = new int[2]; getLocationOnScreen(me);
                        if (at != null) kindsMenu(empty, at[0] - me[0], at[1] - me[1]); else { replaceKey = empty; kinds(); }
                    }
                    else if (dock.wiggling()) { dock.wiggle(false); invalidate(); }
                    else close();
                }
            }
            return true;
        }

        boolean floating;
        @Override protected boolean drawChild(Canvas c, View child, long time) { return (child != pinCard || floating) && super.drawChild(c, child, time); }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int[] at = new int[2]; getLocationOnScreen(at);
            RectF on = dock.shellOnScreen();
            bar.set(on.left - at[0], on.top - at[1], on.right - at[0], on.bottom - at[1]);
            int width = Math.round(bar.width()), room = Math.round(Math.min(getHeight() * 0.62f, bar.top - dp(88)));
            scroll.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(dp(120), room), MeasureSpec.AT_MOST));
            float bottom = bar.top - dp(36);
            target.set(bar.left, bottom - scroll.getMeasuredHeight(), bar.right, bottom);
            scroll.layout(Math.round(target.left), Math.round(target.top), Math.round(target.right), Math.round(target.bottom));
            if (pinCard != null && pinCard.getParent() == this) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pinCard.getLayoutParams();
                pinCard.measure(MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                pinCard.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + pinCard.getMeasuredWidth(), lp.topMargin + pinCard.getMeasuredHeight());
            }
        }

        @Override protected void dispatchDraw(Canvas c) {
            if (getWidth() == 0 || bar.isEmpty()) return;
            float shownness = Math.max(clamp(p), clamp(q));
            if (shownness <= 0.001f) return;
            if (!open) { leaving(c); return; }
            int s0 = c.save();
            try {
                hole.reset(); hole.addRoundRect(bar, bar.height() / 2, bar.height() / 2, Path.Direction.CW);
                Canvas.class.getMethod("clipOutPath", Path.class).invoke(c, hole);
            } catch (Throwable ignored) { }
            scrim.setColor(Color.argb(Math.round((look.dark ? 120 : 70) * shownness), 0, 0, 0));
            c.drawRect(0, 0, getWidth(), getHeight(), scrim);
            c.restoreToCount(s0);
            now.set(target.left, lerp(bar.top, target.top, q), target.right, lerp(bar.bottom, target.bottom, p));
            if (now.bottom - now.top < bar.height()) now.top = now.bottom - bar.height();
            float radius = lerp(bar.height() / 2f, dp(28), clamp(p * 1.2f));
            fill.setColor(look.panel);
            fill.setAlpha(Math.round(255 * clamp(shownness * 5f)));
            c.drawRoundRect(now, radius, radius, fill);
            edge(c, now, radius, Math.round(255 * clamp(shownness * 5f)));
            int s1 = c.save();
            shape.reset(); shape.addRoundRect(now, radius, radius, Path.Direction.CW);
            c.clipPath(shape);
            scroll.setTranslationY(now.bottom - target.bottom);
            float show = clamp(q * 1.6f - 0.25f);
            for (int i = 0; i < rows.getChildCount(); i++) {
                View v = rows.getChildAt(i);
                float a = clamp(show * 1.4f - Math.min(i, 8) * 0.05f);
                v.setAlpha(a);
                v.setTranslationY((1 - a) * dp(14));
            }
            super.dispatchDraw(c);
            c.restoreToCount(s1);
            if (pinCard != null && pinCard.getParent() == this) { floating = true; drawChild(c, pinCard, getDrawingTime()); floating = false; }
            drag.draw(c);
            if (open && dock.wiggling() && !drag.active()) {
                if (pinGlyph == null) pinGlyph = new SearchGlyph(SearchGlyph.PIN, look.text);
                int[] at = new int[2]; getLocationOnScreen(at);
                float lift = (float) Math.sin(android.os.SystemClock.uptimeMillis() / 260.0) * dp(1.5f);
                for (Object[] pin : dock.pins()) {
                    float px = (Float) pin[1] - at[0], py = (Float) pin[2] - at[1] - dp(15) + lift;
                    pinPaint.setColor(look.card); pinPaint.setShadowLayer(dp(4), 0, dp(1), 0x40000000);
                    c.drawCircle(px, py, dp(11), pinPaint);
                    pinGlyph.setBounds(Math.round(px - dp(8)), Math.round(py - dp(8)), Math.round(px + dp(8)), Math.round(py + dp(8)));
                    pinGlyph.draw(c);
                }
                postInvalidateDelayed(30);
            }
        }

        private GlassRim rim;
        private void edge(Canvas c, RectF r, float radius, int alpha) {
            if (!look.rimmed) return;
            if (rim == null) rim = new GlassRim(dp(28), dp(1.5f), true);
            rim.draw(c, r.left, r.top, r.right, r.bottom, Math.min(radius, Math.min(r.width(), r.height()) / 2f), alpha);
        }

        private void leaving(Canvas c) {
            float u = clamp((SystemClock.uptimeMillis() - closedAt) / 200f), e = u * u, k = 1f - e;
            int s0 = c.save();
            try {
                hole.reset(); hole.addRoundRect(bar, bar.height() / 2, bar.height() / 2, Path.Direction.CW);
                Canvas.class.getMethod("clipOutPath", Path.class).invoke(c, hole);
            } catch (Throwable ignored) { }
            scrim.setColor(Color.argb(Math.round((look.dark ? 120 : 70) * k), 0, 0, 0));
            c.drawRect(0, 0, getWidth(), getHeight(), scrim);
            c.restoreToCount(s0);
            int s1 = c.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * k));
            c.scale(1f - 0.1f * e, 1f - 0.22f * e, target.centerX(), target.bottom);
            float radius = dp(28);
            fill.setColor(look.panel);
            fill.setAlpha(255);
            c.drawRoundRect(target, radius, radius, fill);
            edge(c, target, radius, 255);
            shape.reset(); shape.addRoundRect(target, radius, radius, Path.Direction.CW);
            c.clipPath(shape);
            scroll.setTranslationY(0);
            super.dispatchDraw(c);
            c.restoreToCount(s1);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP && !now.contains(e.getX(), e.getY())) close();
            return true;
        }
    }

    private TextView label(CharSequence text, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private void heading(String text) {
        TextView t = label(I18n.cs(text), 13, look.subText, true);
        t.setPadding(dp(4), dp(14), dp(4), dp(6));
        rows.addView(t);
    }

    private Drawable ripple(int radius) {
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(radius);
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(PixelSearch.alpha(look.text, 14)), null, mask);
    }

    private boolean allowed() {
        if (PatronGate.ok(getContext())) return true;
        close();
        SearchDock.ensure();
        return false;
    }

    private boolean put(String key, String value) {
        if (!allowed()) return false;
        try { Settings.Secure.putString(getContext().getContentResolver(), key, value); }
        catch (Throwable t) {
            android.util.Log.w(TAG, "search editor: save " + key, t);
            dock.openSettings();
            close();
            return false;
        }
        saving = true;
        try { SearchDock.ensure(); } finally { saving = false; }
        return true;
    }

    private void change(String key, String value) {
        if (!put(key, value)) return;
        look = new PixelSearch.Look(getContext());
        int y = scroll.getScrollY();
        main();
        scroll.post(() -> scroll.scrollTo(0, y));
        view.requestLayout();
        view.invalidate();
    }

    private void live(final String key, final int value) {
        if (!allowed()) return;
        PixelSearch.LIVE.put(key, value);
        saving = true;
        try { SearchDock.ensure(); } finally { saving = false; }
        if (PixelSearch.WIDTH_KEY.equals(key) || PixelSearch.HEIGHT_KEY.equals(key)) view.requestLayout();
        Runnable old = writes.get(key);
        if (old != null) MAIN.removeCallbacks(old);
        Runnable write = () -> { try { Settings.Secure.putString(getContext().getContentResolver(), key, String.valueOf(value)); } catch (Throwable t) { android.util.Log.w(TAG, "search editor: save " + key, t); } };
        writes.put(key, write);
        MAIN.postDelayed(write, 160);
    }

    private void slider(String title, final String key, int shownPercent, final float from, final float step, int mark) {
        TextView t = label(I18n.cs(title), 14, look.text, false);
        t.setPadding(dp(4), dp(8), dp(4), 0);
        if (shutState == null) shutState = PixelSearch.number(getContext(), PixelSearch.LOCK_KEY, 0) == 1;
        final boolean shut = shutState;
        if (PixelSearch.WIDTH_KEY.equals(key)) {
            sliders.clear();
            LinearLayout head = new LinearLayout(getContext());
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            lock = new SearchLock(getContext(), look.text, shut);
            lock.setContentDescription(I18n.cs("Lock the sliders"));
            lock.setOnClickListener(v -> {
                final boolean next = !shutState;
                shutState = next;
                try { android.provider.Settings.Secure.putInt(getContext().getContentResolver(), PixelSearch.LOCK_KEY, next ? 1 : 0); } catch (Throwable ignored) { }
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                ((SearchLock) v).set(next);
                for (OpacitySlider x : sliders) x.lock(next, true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMarginEnd(dp(2));
            head.addView(lock, lp);
            rows.addView(head);
        } else rows.addView(t);
        OpacitySlider s = new OpacitySlider(getContext(), look.dark ? look.chipOn : look.accent, look.dark ? 0x33ffffff : 0x22000000);
        s.lock(shut, false);
        s.refused = () -> { if (lock != null) lock.refuse(); };
        sliders.add(s);
        s.show(shownPercent, false);
        s.mark(mark);
        s.setContentDescription(I18n.cs(title));
        s.listen(percent -> live(key, Math.max(1, Math.round(from + percent * step))));
        rows.addView(s, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private LinearLayout strip() {
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setHorizontalFadingEdgeEnabled(true);
        hs.setFadingEdgeLength(dp(22));
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, dp(2), 0, dp(2));
        hs.addView(line);
        rows.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        return line;
    }

    private String popStar;
    private SearchLock lock;
    private Boolean shutState;
    private final java.util.ArrayList<OpacitySlider> sliders = new java.util.ArrayList<OpacitySlider>();

    private TextView chip(LinearLayout line, String name, boolean on, final Runnable run) {
        TextView t = label(I18n.cs(name), 14, on ? look.onChip : look.text, true);
        t.setPadding(dp(13), dp(9), dp(13), dp(9));
        GradientDrawable d = new GradientDrawable(); d.setCornerRadius(dp(20)); d.setColor(on ? look.chipOn : look.chip);
        t.setBackground(d);
        t.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(dp(8));
        line.addView(t, lp);
        return t;
    }

    private void swatch(LinearLayout line, int color, int second, boolean on, final String value) {
        View v = new View(getContext());
        v.setBackground(new SearchSwatch(color, second, on, look.text, getContext().getResources().getDisplayMetrics().density));
        v.setContentDescription(I18n.cs("Color"));
        v.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); change(PixelSearch.COLOR_KEY, value); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.setMarginEnd(dp(8));
        line.addView(v, lp);
    }

    private void main() {
        sub = false;
        final Context c = getContext();
        rows.removeAllViews();
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(I18n.cs("Edit search bar"), 18, look.text, true);
        title.setPadding(dp(4), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView ok = label(I18n.cs("Done"), 14, look.accent, true);
        ok.setPadding(dp(14), dp(8), dp(14), dp(8));
        ok.setBackground(ripple(dp(18)));
        ok.setOnClickListener(v -> close());
        top.addView(ok);
        rows.addView(top);

        slider("Width", PixelSearch.WIDTH_KEY, Math.round((PixelSearch.width(c) - 70) * 2.5f), 70, 0.4f, 75);
        slider("Height", PixelSearch.HEIGHT_KEY, Math.round((PixelSearch.height(c) - 70) * 2.5f), 70, 0.4f, 75);
        slider("Outer bar", PixelSearch.SHELL_KEY, look.shellOpacity, 0, 1f, 100);
        slider("Search box", PixelSearch.PILL_KEY, look.pillOpacity, 0, 1f, 100);

        heading("Theme");
        LinearLayout themes = strip();
        String theme = PixelSearch.theme(c);
        for (int i = 0; i < PixelSearch.THEMES.length; i++) {
            final String value = PixelSearch.THEMES[i];
            TextView t = chip(themes, PixelSearch.THEME_NAMES[i], value.equals(theme), () -> change(PixelSearch.THEME_KEY, value));
            if (!"dark".equals(value) && !"black".equals(value)) continue;
            final boolean kept = value.equals(PixelSearch.darkChoice(c));
            if (kept) { SearchGlyph.badge(t, look.accent, value.equals(popStar)); popStar = null; }
            t.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                popStar = kept ? null : value;
                change(PixelSearch.DARK_KEY, kept ? "" : value);
                return true;
            });
        }
        ChipFit.fit(themes, dp(13), dp(8));
        View holder = (View) themes.getParent();
        holder.setPadding(0, dp(24), 0, 0);
        ((ViewGroup) holder).setClipToPadding(false);
        ((ViewGroup) holder).setClipChildren(false);
        themes.setClipChildren(false);
        themes.setClipToPadding(false);

        heading("Color");
        LinearLayout colors = strip();
        String chosen = PixelSearch.colorChoice(c);
        for (String sys : PixelSearch.SYSTEM_COLORS) swatch(colors, PixelSearch.systemTone(c, sys), PixelSearch.systemShade(c, sys), sys.equals(chosen), sys);
        for (int color : PixelSearch.SWATCHES) {
            String hex = String.format(java.util.Locale.ROOT, "%06x", color & 0xffffff);
            swatch(colors, color, 0, hex.equals(chosen), hex);
        }

        LinearLayout toggles = strip();
        ((View) toggles.getParent()).setPadding(0, dp(12), 0, 0);
        chip(toggles, "Frosted Glass", look.glass, () -> change(PixelSearch.GLASS_KEY, look.glass ? "0" : "1"));
        chip(toggles, "Colorful icons", look.colorful, () -> change(PixelSearch.COLORFUL_KEY, look.colorful ? "0" : "1"));

        heading("Buttons");
        TextView hint = label(I18n.cs("Drag the buttons on the bar to move them. Drag one off the bar to remove it."), 14, look.subText, false);
        hint.setPadding(dp(4), 0, dp(4), dp(8));
        rows.addView(hint);
        LinearLayout adds = strip();
        chip(adds, "Add a button", false, this::kinds);

        TextView more = label(I18n.cs("More settings"), 15, look.accent, true);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(8), dp(14), dp(8), dp(10));
        more.setBackground(ripple(dp(16)));
        more.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); dock.openSettings(); close(); });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(10);
        rows.addView(more, mp);
    }

    private void toast(String text) { Toast.makeText(getContext(), I18n.cs(text), Toast.LENGTH_SHORT).show(); }

    private String replaceKey;
    private View pinCard;
    private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SearchGlyph pinGlyph;

    private int slotIndex(List<PixelSearch.Slot> list, String key) {
        for (int i = 0; i < list.size(); i++) if (SearchDock.key(list.get(i)).equals(key)) return i;
        return -1;
    }

    private void hide(String key) {
        List<PixelSearch.Slot> list = SearchDrag.grouped(PixelSearch.slots(getContext()));
        int i = slotIndex(list, key);
        if (i < 0) return;
        list.set(i, new PixelSearch.Slot(list.get(i).side, "empty", Long.toString(System.nanoTime(), 36), ""));
        change(PixelSearch.SLOTS_KEY, PixelSearch.encode(list));
    }

    private List<String[]> ask(String q) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        try (android.database.Cursor cur = getContext().getContentResolver().query(android.net.Uri.parse("content://" + PixelSearch.AUTHORITY + "/q"), null, null, new String[]{q, "contacts"}, null)) {
            while (cur != null && cur.moveToNext() && out.size() < 40) { String number = cur.getString(4); if (number != null && !number.isEmpty()) out.add(new String[]{cur.getString(3), cur.getString(1), number}); }
        } catch (Throwable t) { android.util.Log.w(TAG, "search editor: contacts", t); }
        return out;
    }

    private void keep(List<String[]> more) {
        List<String[]> list = PixelSearch.hearts(getContext());
        for (String[] f : more) { boolean had = false; for (String[] x : list) if (x[0].equals(f[0]) && x[2].equals(f[2])) had = true; if (!had) list.add(f); }
        try { Settings.Secure.putString(getContext().getContentResolver(), PixelSearch.HEARTS_KEY, PixelSearch.encodeHearts(list)); } catch (Throwable t) { return; }
        boolean button = false;
        for (PixelSearch.Slot s : PixelSearch.slots(getContext())) if ("action".equals(s.type) && "hearts".equals(s.value)) button = true;
        toast("Added to favorite contacts");
        if (!button) add(new PixelSearch.Slot('I', "action", "hearts", ""));
    }

    private View person(final String[] f, int size) {
        ImageView face = new ImageView(getContext());
        face.setImageDrawable(new SearchDock.Avatar(getContext(), new PixelSearch.Slot('I', "contact", "call/" + f[0] + "/" + f[2], f[1]), look.text));
        face.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return face;
    }

    private void contactsMenu() {
        closePin();
        if (!allowed()) return;
        final Context c = getContext();
        list(I18n.t("Contact"), new ArrayList<Item>());
        final LinearLayout page = rows;
        final android.widget.EditText find = new android.widget.EditText(c);
        find.setHint(I18n.cs("Search contacts")); find.setSingleLine(true); find.setTextColor(look.text); find.setHintTextColor(look.subText);
        find.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable fb = new GradientDrawable(); fb.setColor(look.chip); fb.setCornerRadius(dp(22));
        find.setBackground(fb); find.setPadding(dp(16), dp(11), dp(16), dp(11));
        find.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2); fp.topMargin = dp(8);
        page.addView(find, fp);
        final LinearLayout starRow = new LinearLayout(c);
        starRow.setGravity(Gravity.CENTER_VERTICAL);
        starRow.setPadding(dp(6), dp(14), dp(6), dp(10));
        final ImageView heart = new ImageView(c);
        heart.setImageDrawable(new SearchGlyph(SearchGlyph.HEART, look.accent));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(22), dp(22)); hp.setMarginEnd(dp(12));
        starRow.addView(heart, hp);
        starRow.addView(label(I18n.cs("Add dialer favorites"), 15, look.text, true), new LinearLayout.LayoutParams(0, -2, 1));
        final LinearLayout faces = new LinearLayout(c);
        starRow.addView(faces);
        page.addView(starRow);
        final LinearLayout results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setMinimumHeight(dp(150));
        page.addView(results);
        final List<String[]> starred = new ArrayList<String[]>();
        final Runnable[] fill = new Runnable[1];
        final String[] wanted = {""};
        fill[0] = () -> {
            final String q = find.getText().toString().trim();
            wanted[0] = q;
            new Thread(() -> {
                final List<String[]> found = q.isEmpty() ? new ArrayList<String[]>(starred) : ask(q);
                view.post(() -> {
                    if (!q.equals(wanted[0]) || rows != page || !sub) return;
                    results.removeAllViews();
                    int n = 0;
                    for (final String[] f : found) {
                        LinearLayout row = new LinearLayout(c);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(6), dp(7), dp(6), dp(7));
                        row.addView(person(f, 36));
                        LinearLayout names = new LinearLayout(c);
                        names.setOrientation(LinearLayout.VERTICAL);
                        names.setPadding(dp(12), 0, 0, 0);
                        TextView name = label(f[1], 15, look.text, true); name.setSingleLine(true);
                        names.addView(name);
                        names.addView(label(f[2], 12, look.subText, false));
                        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                        row.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); ArrayList<String[]> one = new ArrayList<String[]>(); one.add(f); keep(one); });
                        row.setAlpha(0f); row.setTranslationY(dp(8));
                        row.animate().alpha(1).translationY(0).setStartDelay(Math.min(n++, 8) * 30).setDuration(260).start();
                        results.addView(row);
                    }
                    view.requestLayout();
                });
            }).start();
        };
        new Thread(() -> {
            final List<String[]> got = ask(PixelSearch.STARRED);
            view.post(() -> {
                starred.addAll(got);
                for (int i = 0; i < Math.min(4, got.size()); i++) { View face = person(got.get(i), 26); ((LinearLayout.LayoutParams) face.getLayoutParams()).setMarginStart(i == 0 ? 0 : -dp(8)); faces.addView(face); }
                fill[0].run();
            });
        }).start();
        starRow.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (starred.isEmpty()) { toast("No favorites in your dialer yet"); return; }
            keep(starred);
        });
        find.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable s) { fill[0].run(); }
        });
        try {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        } catch (Throwable ignored) { }
        find.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(find, 0);
        });
        regrow();
    }

    private void closePin() {
        final View card = pinCard;
        if (card == null) return;
        pinCard = null;
        SearchOverlay.bloomOut(card, () -> view.removeView(card));
    }

    private void pinMenu(final String key, float x, float y) {
        closePin();
        if (!allowed()) return;
        final LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(look.panel); bg.setCornerRadius(dp(24));
        card.setBackground(look.rimmed ? new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, new GlassRim(dp(24), dp(1.5f), true)}) : bg);
        card.setElevation(dp(10));
        final boolean first = "lead".equals(key);
        String[] names = {first ? "Reset" : "Hide", "Replace"};
        String[] glyphs = {SearchGlyph.CLEAR, SearchGlyph.REFRESH};
        for (int i = 0; i < 2; i++) {
            final boolean hide = i == 0;
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(16), dp(10));
            ImageView icon = new ImageView(getContext());
            icon.setImageDrawable(new SearchGlyph(glyphs[i], look.text));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20)); ip.setMarginEnd(dp(12));
            row.addView(icon, ip);
            row.addView(label(I18n.cs(names[i]), 15, look.text, true));
            row.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                closePin();
                if (hide && first) change(PixelSearch.LEAD_KEY, "");
                else if (hide) hide(key);
                else { replaceKey = key; kinds(); }
            });
            card.addView(row);
        }
        final int w = dp(170);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, -2, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.max(dp(8), Math.min(view.getWidth() - w - dp(8), Math.round(x - w / 2f)));
        card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        lp.topMargin = Math.max(dp(8), Math.round(y - dp(34) - card.getMeasuredHeight()));
        pinCard = card;
        view.addView(card, lp);
        card.setPivotX(x - lp.leftMargin); card.setPivotY(card.getMeasuredHeight());
        SearchOverlay.bloomIn(card);
    }

    private void kindsMenu(final String key, float x, float y) {
        closePin();
        if (!allowed()) return;
        final LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(look.panel); bg.setCornerRadius(dp(24));
        card.setBackground(look.rimmed ? new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, new GlassRim(dp(24), dp(1.5f), true)}) : bg);
        card.setElevation(dp(10));
        String[] names = {"Action", "App", "App shortcut", "Contact"};
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            TextView row = label(I18n.cs(names[i]), 15, look.text, true);
            row.setPadding(dp(16), dp(11), dp(16), dp(11));
            row.setBackground(ripple(dp(16)));
            row.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                closePin();
                replaceKey = key;
                if (which == 0) actions(); else if (which == 1) apps(false); else if (which == 2) apps(true); else contactsMenu();
            });
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        final int w = dp(190);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, -2, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.max(dp(8), Math.min(view.getWidth() - w - dp(8), Math.round(x - w / 2f)));
        card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        lp.topMargin = Math.max(dp(8), Math.round(y - dp(12) - card.getMeasuredHeight()));
        pinCard = card;
        view.addView(card, lp);
        card.setPivotX(x - lp.leftMargin); card.setPivotY(card.getMeasuredHeight());
        SearchOverlay.bloomIn(card);
    }

    private void add(PixelSearch.Slot slot) {
        List<PixelSearch.Slot> list = SearchDrag.grouped(PixelSearch.slots(getContext()));
        if (SearchDock.spare(replaceKey)) {
            char want = SearchDock.spareSide(replaceKey);
            replaceKey = null;
            if (PixelSearch.count(list, want) < PixelSearch.max(want)) {
                slot.side = want;
                list.add(slot);
                change(PixelSearch.SLOTS_KEY, PixelSearch.encode(SearchDrag.grouped(list)));
                regrow();
                return;
            }
        }
        if ("lead".equals(replaceKey)) {
            replaceKey = null;
            slot.side = 'I';
            change(PixelSearch.LEAD_KEY, slot.encode());
            regrow();
            return;
        }
        if (replaceKey != null) {
            int at = slotIndex(list, replaceKey);
            replaceKey = null;
            if (at >= 0) {
                slot.side = list.get(at).side;
                list.set(at, slot);
                change(PixelSearch.SLOTS_KEY, PixelSearch.encode(list));
                regrow();
                return;
            }
        }
        char side = 0;
        for (char s : new char[]{'I', 'R', 'L'}) if (side == 0 && PixelSearch.count(list, s) < PixelSearch.max(s)) side = s;
        if (side == 0) { toast("The bar is full. Drag a button off the bar first."); main(); regrow(); return; }
        slot.side = side;
        list.add(slot);
        change(PixelSearch.SLOTS_KEY, PixelSearch.encode(SearchDrag.grouped(list)));
        regrow();
    }

    private void list(String title, List<Item> items) {
        sub = true;
        Context c = getContext();
        rows.removeAllViews();
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(c);
        back.setImageDrawable(new SearchGlyph(SearchGlyph.BACK, look.text));
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        back.setContentDescription(I18n.cs("Back"));
        back.setBackground(ripple(dp(20)));
        back.setOnClickListener(v -> { main(); regrow(); });
        top.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView t = label(title, 18, look.text, true);
        t.setPadding(dp(8), 0, 0, 0);
        top.addView(t);
        rows.addView(top);
        for (final Item item : items) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(9), dp(8), dp(9));
            row.setBackground(ripple(dp(16)));
            if (item.icon != null) {
                ImageView icon = new ImageView(c);
                icon.setImageDrawable(item.icon);
                if (item.icon instanceof SearchGlyph) icon.setPadding(dp(5), dp(5), dp(5), dp(5));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(34), dp(34));
                ip.setMarginEnd(dp(14));
                row.addView(icon, ip);
            }
            TextView name = label(item.label, 16, look.text, false);
            name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); item.run.run(); });
            rows.addView(row);
        }
        regrow();
    }

    private Item item(String label, Drawable icon, Runnable run) { Item i = new Item(); i.label = label; i.icon = icon; i.run = run; return i; }

    private void kinds() {
        ArrayList<Item> items = new ArrayList<Item>();
        items.add(item(I18n.t("Action"), null, this::actions));
        items.add(item(I18n.t("App"), null, () -> apps(false)));
        items.add(item(I18n.t("App shortcut"), null, () -> apps(true)));
        items.add(item(I18n.t("Contact"), null, this::contactsMenu));
        list(I18n.t("Add a button"), items);
    }

    private void actions() {
        ArrayList<Item> items = new ArrayList<Item>();
        for (int i = 0; i < PixelSearch.ACTIONS.length; i++) {
            final String id = PixelSearch.ACTIONS[i];
            if ("scan".equals(id) && !PixelSearch.scanOn(getContext())) continue;
            items.add(item(I18n.t(PixelSearch.ACTION_NAMES[i]), new SearchGlyph(id, look.text), () -> add(new PixelSearch.Slot('I', "action", id, ""))));
        }
        list(I18n.t("Action"), items);
    }

    private static List<Object[]> sAppRows;
    private static long sAppRowsAt;

    static synchronized List<Object[]> appRows(Context c) {
        if (sAppRows != null && android.os.SystemClock.uptimeMillis() - sAppRowsAt < 120000) return sAppRows;
        ArrayList<Object[]> out = new ArrayList<Object[]>();
        LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        for (LauncherActivityInfo info : la.getActivityList(null, android.os.Process.myUserHandle())) {
            Drawable icon = null;
            try { icon = info.getIcon(0); } catch (Throwable ignored) { }
            out.add(new Object[]{String.valueOf(info.getLabel()), info.getComponentName(), icon});
        }
        sAppRows = out; sAppRowsAt = android.os.SystemClock.uptimeMillis();
        return out;
    }

    private void apps(final boolean shortcut) {
        final Context c = getContext();
        new Thread(() -> {
            final ArrayList<Item> items = new ArrayList<Item>();
            try {
                for (Object[] row : appRows(c)) {
                    final String name = (String) row[0];
                    final ComponentName cn = (ComponentName) row[1];
                    Drawable icon = (Drawable) row[2];
                    items.add(item(name, icon, () -> {
                        if (shortcut) shortcuts(cn.getPackageName(), name);
                        else add(new PixelSearch.Slot('I', "app", cn.flattenToString(), name));
                    }));
                }
                Collections.sort(items, (x, y) -> x.label.compareToIgnoreCase(y.label));
            } catch (Throwable t) { android.util.Log.w(TAG, "search editor: apps", t); }
            MAIN.post(() -> { if (!done && open) list(I18n.t(shortcut ? "Choose an app" : "App"), items); });
        }, "PixelSearchEditorApps").start();
    }

    private void shortcuts(final String pkg, String app) {
        Context c = getContext();
        ArrayList<Item> items = new ArrayList<Item>();
        ArrayList<String> seen = new ArrayList<String>();
        for (Object info : SearchDock.shortcuts(c, pkg)) {
            final String id = SearchDock.shortcutText(info, "getId");
            String shortName = SearchDock.shortcutText(info, "getShortLabel");
            final String name = shortName.isEmpty() ? SearchDock.shortcutText(info, "getLongLabel") : shortName;
            if (id.isEmpty() || name.isEmpty() || seen.contains(id)) continue;
            seen.add(id);
            items.add(item(name, SearchDock.shortcutIcon(c, pkg, id), () -> add(new PixelSearch.Slot('I', "shortcut", pkg + "/" + id, name))));
        }
        if (items.isEmpty()) { toast("This app has no shortcuts"); return; }
        list(app, items);
    }
}
