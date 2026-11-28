package io.github.pixelsuite;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class SearchSwatch extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final int color, second, ring;
    private final boolean on;
    private final float density;

    SearchSwatch(int color, int second, boolean on, int ring, float density) {
        this.color = color | 0xff000000; this.second = second; this.on = on; this.ring = ring; this.density = density;
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float cx = b.exactCenterX(), cy = b.exactCenterY(), r = Math.min(b.width(), b.height()) / 2f;
        float disc = on ? r - 5 * density : r - 2.5f * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(cx, cy, disc, paint);
        if (second != 0) {
            int s = c.save();
            clip.reset(); clip.addCircle(cx, cy, disc, Path.Direction.CW);
            c.clipPath(clip);
            c.rotate(-45, cx, cy);
            paint.setColor(second | 0xff000000);
            c.drawRect(cx - disc, cy, cx + disc, cy + disc, paint);
            c.restoreToCount(s);
        }
        if (on) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setColor(ring);
            c.drawCircle(cx, cy, r - 1.4f * density, paint);
        }
    }

    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
