package io.github.pixelsuite;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Rtl {
    private Rtl() {}

    private static final int DONE = 0x50000000 | 350;

    static boolean on() { return I18n.rtl(); }

    static void activity(Activity a) {
        try { Window w = a.getWindow(); if (w != null) window(w); } catch (Throwable ignored) { }
    }

    static void dialog(Dialog d) {
        try { Window w = d.getWindow(); if (w != null) window(w); } catch (Throwable ignored) { }
    }

    static void window(Window w) {
        final View decor = w.getDecorView();
        boolean rtl = on();
        decor.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        if (!rtl || decor.getTag(DONE) != null) return;
        decor.setTag(DONE, Boolean.TRUE);
        final boolean[] dirty = {true};
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> dirty[0] = true);
        decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (!dirty[0] || !on()) return true;
                dirty[0] = false;
                return !turn(decor);
            }
        });
        turn(decor);
    }

    static boolean turn(View v) {
        if (v == null) return false;
        if (v instanceof HorizontalScrollView) return false;
        if (v.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) return false;
        boolean changed = one(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) changed |= turn(g.getChildAt(i));
        }
        return changed;
    }

    private static boolean one(View v) {
        Object tag = v.getTag(DONE);
        int l = v.getPaddingLeft(), r = v.getPaddingRight();
        boolean changed = false;
        if (tag instanceof int[]) {
            int[] was = (int[]) tag;
            if ((was[0] != l || was[1] != r) && was[2] < 4) { v.setPaddingRelative(l, v.getPaddingTop(), r, v.getPaddingBottom()); v.setTag(DONE, new int[]{v.getPaddingLeft(), v.getPaddingRight(), was[2] + 1}); v.requestLayout(); changed = true; }
            return changed;
        }
        if (l != r) { v.setPaddingRelative(l, v.getPaddingTop(), r, v.getPaddingBottom()); changed = true; }
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) p;
            if (m.leftMargin != m.rightMargin && !m.isMarginRelative()) {
                int ml = m.leftMargin, mr = m.rightMargin; m.setMarginStart(ml); m.setMarginEnd(mr); changed = true;
            }
        }
        if (p instanceof LinearLayout.LayoutParams) { int g = relative(((LinearLayout.LayoutParams) p).gravity); if (g != ((LinearLayout.LayoutParams) p).gravity) { ((LinearLayout.LayoutParams) p).gravity = g; changed = true; } }
        if (p instanceof FrameLayout.LayoutParams) { int g = relative(((FrameLayout.LayoutParams) p).gravity); if (g != ((FrameLayout.LayoutParams) p).gravity) { ((FrameLayout.LayoutParams) p).gravity = g; changed = true; } }
        if (v instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) v;
            try { int was = (Integer) LinearLayout.class.getMethod("getGravity").invoke(ll), g = relative(was); if (g != was) { ll.setGravity(g); changed = true; } } catch (Throwable ignored) { }
        }
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            int g = t.getGravity(), rg = relative(g);
            if (rg != g) { t.setGravity(rg); changed = true; }
            int h = rg & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
            if (t.getTextAlignment() == View.TEXT_ALIGNMENT_GRAVITY || t.getTextAlignment() == View.TEXT_ALIGNMENT_INHERIT) {
                if (h == Gravity.START || h == 0) { t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); changed = true; }
                else if (h == Gravity.END) { t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END); changed = true; }
            } else if (t.getTextAlignment() == View.TEXT_ALIGNMENT_TEXT_START) { t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); changed = true; }
        }
        if (changed) v.requestLayout();
        v.setTag(DONE, new int[]{v.getPaddingLeft(), v.getPaddingRight(), 0});
        return changed;
    }

    static int relative(int g) {
        if ((g & Gravity.RELATIVE_LAYOUT_DIRECTION) != 0) return g;
        int h = g & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (h == Gravity.LEFT) return (g & ~Gravity.HORIZONTAL_GRAVITY_MASK) | Gravity.START;
        if (h == Gravity.RIGHT) return (g & ~Gravity.HORIZONTAL_GRAVITY_MASK) | Gravity.END;
        return g;
    }
}
