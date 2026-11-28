package io.github.pixelsuite;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.animation.OvershootInterpolator;

final class SearchLock extends View {
    private final PrivacyBadges.Glyph glyph;
    private float opened;
    private ValueAnimator turn;

    SearchLock(Context c, int color, boolean locked) {
        super(c);
        glyph = new PrivacyBadges.Glyph(color, 0, c.getResources().getDisplayMetrics().density);
        opened = locked ? 0f : 1f;
        setAlpha(locked ? 1f : 0.62f);
    }

    void set(boolean locked) {
        if (turn != null) turn.cancel();
        turn = ValueAnimator.ofFloat(opened, locked ? 0f : 1f);
        turn.setDuration(420);
        turn.setInterpolator(new OvershootInterpolator(2.4f));
        turn.addUpdateListener(a -> { opened = (Float) a.getAnimatedValue(); invalidate(); });
        turn.start();
        setScaleX(0.8f); setScaleY(0.8f);
        animate().scaleX(1).scaleY(1).alpha(locked ? 1f : 0.62f).setDuration(420).setInterpolator(new OvershootInterpolator(2.4f)).start();
    }

    void refuse() {
        float d = getResources().getDisplayMetrics().density;
        ObjectAnimator shake = ObjectAnimator.ofFloat(this, "translationX", 0, -5 * d, 5 * d, -4 * d, 4 * d, -2 * d, 0);
        shake.setDuration(380);
        shake.start();
        ObjectAnimator tilt = ObjectAnimator.ofFloat(this, "rotation", 0, -9, 9, -7, 7, -3, 0);
        tilt.setDuration(380);
        tilt.start();
    }

    @Override protected void onDraw(Canvas c) {
        glyph.draw(c, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * 0.6f, opened, 1f);
    }
}
