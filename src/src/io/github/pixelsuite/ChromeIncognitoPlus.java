package io.github.pixelsuite;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

final class ChromeIncognitoPlus {
    private ChromeIncognitoPlus() { }

    private static final WeakHashMap<Activity, Twin> sTwins = new WeakHashMap<Activity, Twin>();

    static void ensure(Activity a, View hubToolbar) {
        try {
            if (a == null || hubToolbar == null) return;
            View plus = find(hubToolbar, "toolbar_action_button");
            if (plus == null) return;
            Twin t = sTwins.get(a);
            if (t != null && t.plus.get() == plus && t.getParent() != null) return;
            if (t != null) t.detach();
            ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            t = new Twin(a, plus);
            decor.addView(t, new FrameLayout.LayoutParams(Math.max(1, plus.getWidth()), Math.max(1, plus.getHeight())));
            sTwins.put(a, t);
            t.follow();
        } catch (Throwable ignored) { }
    }

    static void drawIn(Canvas c, View row) {
        try {
            for (Twin t : sTwins.values()) {
                View p = t == null ? null : t.plus.get();
                if (p == null || t.filter == null || p.getWidth() <= 0 || ChromeTabTransition.spinning(t) || ChromeTabTransition.frozen()) continue;
                float x = 0, y = 0;
                View v = p;
                while (v != null && v != row) {
                    x += v.getLeft() + v.getTranslationX() - (v.getParent() instanceof View ? ((View) v.getParent()).getScrollX() : 0);
                    y += v.getTop() + v.getTranslationY() - (v.getParent() instanceof View ? ((View) v.getParent()).getScrollY() : 0);
                    v = v.getParent() instanceof View ? (View) v.getParent() : null;
                }
                if (v != row || p.getVisibility() != View.VISIBLE) continue;
                int s = c.save();
                try { c.translate(x + t.offset(p), y); c.rotate(t.getRotation(), p.getWidth() / 2f, p.getHeight() / 2f); t.face(c, p); } finally { c.restoreToCount(s); }
            }
        } catch (Throwable ignored) { }
    }

    static void remove(Activity a) {
        try { Twin t = sTwins.remove(a); if (t != null) t.detach(); } catch (Throwable ignored) { }
    }

    private static final class Twin extends View implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> plus;
        final WeakReference<Activity> act;
        final Paint layer = new Paint();
        ColorMatrixColorFilter filter;
        int lastW, lastH;

        Twin(Activity a, View p) {
            super(a);
            act = new WeakReference<Activity>(a);
            plus = new WeakReference<View>(p);
            setContentDescription("New incognito tab");
            setClickable(true);
            setVisibility(INVISIBLE);
            p.getViewTreeObserver().addOnPreDrawListener(this);
        }

        View mover(View p, View sw) {
            View best = p;
            for (View v = p; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
                String n = name(v);
                if ("hub_toolbar".equals(n)) break;
                if (sw != null && holds(v, sw)) break;
                best = v;
                if ("toolbar_action_container".equals(n)) break;
            }
            return best;
        }

        static boolean holds(View parent, View child) {
            for (View v = child; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) if (v == parent) return true;
            return false;
        }

        View switcher(View p) {
            View top = p;
            for (int i = 0; i < 6 && top.getParent() instanceof View; i++) { top = (View) top.getParent(); if ("hub_toolbar".equals(name(top))) break; }
            return find(top, "pane_switcher");
        }

        float rel = -1f;

        void centre(View p) {
            View sw = switcher(p);
            View m = mover(p, sw);
            float want = 0f, side = -1f;
            if (sw != null && !holds(m, sw) && sw.isShown() && sw.getWidth() > 0 && m.getParent() instanceof View) {
                View row = (View) m.getParent();
                int[] r = new int[2], q = new int[2], ps = new int[2];
                row.getLocationOnScreen(r); sw.getLocationOnScreen(q); p.getLocationOnScreen(ps);
                float natural = ps[0] - m.getTranslationX();
                float target = q[0] - pillGap() - p.getWidth();
                if (target >= r[0] && Math.abs(target - natural) < row.getWidth() / 2f) {
                    want = target - natural;
                    side = q[0] + sw.getWidth() + pillGap() - target;
                }
            }
            if (Math.abs(m.getTranslationX() - want) > 0.5f) m.setTranslationX(want);
            rel = side;
        }

        float pillGap() { return 8 * getResources().getDisplayMetrics().density; }

        float offset(View p) { return rel >= 0f ? rel : p.getWidth() + gap(); }

        void detach() {
            View p = plus.get();
            try { for (View v = p; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) { if (v.getTranslationX() != 0f && v != p.getRootView()) { String n = name(v); if (v == p || "toolbar_action_container".equals(n)) v.setTranslationX(0f); } if ("toolbar_action_container".equals(name(v)) || "hub_toolbar".equals(name(v))) break; } } catch (Throwable ignored) { }
            try { if (p != null) p.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { }
            if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        }

        @Override public boolean onPreDraw() { follow(); return true; }

        void follow() {
            View p = plus.get();
            if (p == null || !p.isAttachedToWindow() || getParent() == null) { if (p == null) detach(); return; }
            boolean shown = p.isShown() && p.getWidth() > 0;
            float alpha = 1f;
            for (View v = p; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) if (v != p || !ChromeTabTransition.spinning(p)) alpha *= v.getAlpha();
            if (ChromeTabTransition.spinning(this) || ChromeTabTransition.frozen()) alpha = 0f;
            boolean anim = false;
            try { anim = ChromeTabTransition.active(); } catch (Throwable ignored) { }
            int want = shown && alpha > 0.01f && !anim ? VISIBLE : INVISIBLE;
            if (getVisibility() != want) setVisibility(want);
            if (want != VISIBLE && !anim) return;
            try { centre(p); } catch (Throwable ignored) { }
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null && (lp.width != p.getWidth() || lp.height != p.getHeight())) { lp.width = p.getWidth(); lp.height = p.getHeight(); setLayoutParams(lp); }
            int[] at = new int[2], base = new int[2];
            View pp = p.getParent() instanceof View ? (View) p.getParent() : p;
            pp.getLocationOnScreen(at);
            if (pp != p) { at[0] += Math.round(p.getLeft() + p.getTranslationX() - pp.getScrollX()); at[1] += Math.round(p.getTop() + p.getTranslationY() - pp.getScrollY()); }
            ((View) getParent()).getLocationOnScreen(base);
            float x = at[0] - base[0] + offset(p), y = at[1] - base[1];
            if (Math.abs(getTranslationX() - x) > 0.5f) setTranslationX(x);
            if (Math.abs(getTranslationY() - y) > 0.5f) setTranslationY(y);
            if (Math.abs(getAlpha() - alpha) > 0.01f) setAlpha(alpha);
            if (p.isPressed()) return;
            int bg = sample(p, Math.round(p.getWidth() * 0.3f), Math.round(p.getHeight() * 0.3f));
            int icon = sample(p, p.getWidth() / 2, p.getHeight() / 2);
            if (filter == null || bg != lastBg || icon != lastIcon || lastW != p.getWidth() || lastH != p.getHeight()) {
                lastBg = bg; lastIcon = icon; lastW = p.getWidth(); lastH = p.getHeight();
                filter = make(bg, icon);
                invalidate();
            }
        }

        int lastBg, lastIcon;
        Bitmap px;
        Canvas pxc;

        int sample(View p, int x, int y) {
            if (px == null) { px = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); pxc = new Canvas(px); }
            px.eraseColor(0);
            int s = pxc.save();
            try { pxc.translate(-x, -y); p.draw(pxc); } catch (Throwable ignored) { } finally { pxc.restoreToCount(s); }
            return px.getPixel(0, 0);
        }

        float gap() { return 10 * getResources().getDisplayMetrics().density; }

        @Override protected void onDraw(Canvas c) {
            View p = plus.get();
            if (p == null || p.getWidth() <= 0 || p.getHeight() <= 0 || filter == null) return;
            face(c, p);
        }

        void face(Canvas c, View p) {
            layer.setColorFilter(filter);
            int s = c.saveLayer(0, 0, p.getWidth(), p.getHeight(), layer);
            try { p.draw(c); } finally { c.restoreToCount(s); }
        }

        ColorMatrixColorFilter make(int bg, int icon) {
            if (Color.alpha(bg) < 128) return null;
            boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int orange = night ? 0xFFFF9F0A : 0xFFFF9500;
            float[] m = new float[20];
            int[] cb = { Color.red(bg), Color.green(bg), Color.blue(bg) };
            int[] ci = { Color.red(icon), Color.green(icon), Color.blue(icon) };
            int[] co = { Color.red(orange), Color.green(orange), Color.blue(orange) };
            for (int k = 0; k < 3; k++) {
                float scale, off;
                if (Math.abs(cb[k] - ci[k]) < 8) { scale = 1f; off = co[k] - cb[k]; }
                else { scale = (co[k] - ci[k]) / (float) (cb[k] - ci[k]); off = co[k] - scale * cb[k]; }
                m[k * 5 + k] = scale;
                m[k * 5 + 4] = off;
            }
            m[18] = 1f;
            return new ColorMatrixColorFilter(new ColorMatrix(m));
        }

        @Override public boolean performClick() {
            super.performClick();
            final Activity a = act.get();
            ChromeTabTransition.spinThen(this, new Runnable() { public void run() {
                if (a != null) try { ChromeLayout.menuAction(a, "new_incognito_tab_menu_id"); } catch (Throwable ignored) { }
            } });
            return true;
        }
    }

    private static String name(View v) {
        try { return v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return ""; }
    }

    private static View find(View root, String id) {
        if (root == null) return null;
        if (id.equals(name(root))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), id); if (f != null) return f; }
        }
        return null;
    }
}
