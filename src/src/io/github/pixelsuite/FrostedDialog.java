package io.github.pixelsuite;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

final class FrostedDialog extends Drawable {
    private final Context context; private final float radius; private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF(); private GlassRim rim; private int rimMode = -1;

    FrostedDialog(Context c, float radiusPx) { context = c; radius = radiusPx; AppSurfaces.watch(this); }

    static boolean wanted(Context c) {
        try { return !AppSurfaces.active(c) && SurfaceStyle.get(c) == SurfaceStyle.FROSTED; } catch (Throwable t) { return false; }
    }

    static int tone(Context c) {
        if (FrostedSurface.dark(c)) return LauncherFeatures.surface(c, "system_surface_container_high_dark", 0xff303137) | 0xff000000;
        int t = FrostedSurface.composite(FrostedSurface.resourceColor(c, "blur_shade_panel_fg", 0x66dedee8), FrostedSurface.resourceColor(c, "blur_shade_panel_bg", 0x66f9f9fe));
        return t | 0xff000000;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds(); if (b.isEmpty()) return;
        box.set(b); fill.setColor(tone(context)); canvas.drawRoundRect(box, radius, radius, fill);
        int mode = GlassRim.mode(context);
        if (mode != rimMode) { rimMode = mode; rim = mode == GlassRim.NONE ? null : GlassRim.of(context, radius); }
        if (rim != null) rim.draw(canvas, box.left, box.top, box.right, box.bottom, radius, 255);
    }
    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }
    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
