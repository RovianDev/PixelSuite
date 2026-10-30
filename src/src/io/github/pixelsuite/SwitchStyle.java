package io.github.pixelsuite;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Switch;

final class SwitchStyle {
    private SwitchStyle() {}

    static void apply(Switch sw, int trackOn, int trackOff, int thumbOn, int thumbOff) {
        sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{thumbOn, thumbOff}));
        Drawable current = sw.getTrackDrawable();
        int w = 0, h = 0;
        Object size = sw.getTag(0x7e505301);
        if (size instanceof int[]) { w = ((int[]) size)[0]; h = ((int[]) size)[1]; }
        else if (current != null) { w = current.getIntrinsicWidth(); h = current.getIntrinsicHeight(); sw.setTag(0x7e505301, new int[]{w, h}); }
        float d = sw.getResources().getDisplayMetrics().density;
        if (w <= 0) w = Math.round(52 * d);
        if (h <= 0) h = Math.round(32 * d);
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, pill(trackOn, w, h));
        track.addState(new int[]{}, pill(trackOff, w, h));
        sw.setTrackTintList(null);
        sw.setTrackDrawable(track);
        sw.refreshDrawableState();
        sw.invalidate();
    }

    private static GradientDrawable pill(int colour, int w, int h) {
        GradientDrawable g = new GradientDrawable(){@Override protected void onBoundsChange(android.graphics.Rect bounds){super.onBoundsChange(bounds);setCornerRadius(Math.min(bounds.width(),bounds.height())/2f);}};
        g.setColor(colour);
        g.setCornerRadius(h / 2f);
        g.setSize(w, h);
        return g;
    }
}
