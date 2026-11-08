package io.github.pixelsuite;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

class PageDialog extends Dialog {
    private ViewGroup mPage;
    private boolean mClosing;

    PageDialog(Context c, int theme) { super(c, theme); }

    void animate(final View page, int bg) {
        mPage = page instanceof ViewGroup ? (ViewGroup) page : null;
        Window w = getWindow();
        if (w != null) {
            w.setWindowAnimations(0);
            w.setBackgroundDrawable(new ColorDrawable(bg));
        }
        if (mPage == null || !AppMotion.enabled(getContext())) return;
        final float rise = 20 * page.getResources().getDisplayMetrics().density;
        for (int i = 0; i < mPage.getChildCount(); i++) { View c = mPage.getChildAt(i); c.setAlpha(0f); c.setTranslationY(rise); }
        page.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                page.getViewTreeObserver().removeOnPreDrawListener(this);
                if (mClosing) return true;
                for (int i = 0; i < mPage.getChildCount(); i++)
                    mPage.getChildAt(i).animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
                return true;
            }
        });
    }

    void close() {
        if (mClosing) return;
        mClosing = true;
        if (mPage == null || mPage.getChildCount() == 0 || !AppMotion.enabled(getContext())) { dismissNow(); return; }
        float rise = 20 * mPage.getResources().getDisplayMetrics().density;
        for (int i = 0; i < mPage.getChildCount(); i++) {
            View c = mPage.getChildAt(i);
            c.animate().cancel();
            c.animate().alpha(0f).translationY(rise).setDuration(160).setInterpolator(new AccelerateInterpolator())
                    .withEndAction(i == 0 ? new Runnable() { public void run() { dismissNow(); } } : null).start();
        }
    }

    private void dismissNow() {
        try { if (isShowing()) dismiss(); } catch (Throwable ignored) { }
    }

    @Override public void onBackPressed() { close(); }
}
