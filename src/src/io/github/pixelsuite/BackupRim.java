package io.github.pixelsuite;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class BackupRim extends Drawable {
    private final Context context;
    private final boolean filled, circle;
    private final float radiusDp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF frame = new RectF();

    BackupRim(Context c, boolean filledCircle, float radiusDp) { context = c; filled = filledCircle; circle = filledCircle; this.radiusDp = radiusDp; AppSurfaces.watch(this); }

    static int green(Context c) {
        boolean black = AppSurfaces.active(c), dark = FrostedSurface.dark(c);
        return black ? 0xff5bd48a : dark ? 0xff81c995 : 0xff1e8e3e;
    }

    @Override public void draw(Canvas c) {
        if (getBounds().isEmpty()) return;
        boolean black = AppSurfaces.active(context), dark = FrostedSurface.dark(context);
        boolean frosted = !black && SurfaceStyle.get(context) == SurfaceStyle.FROSTED;
        float d = context.getResources().getDisplayMetrics().density;
        frame.set(getBounds());
        if (circle) { float cx = frame.centerX(), cy = frame.centerY(); frame.set(cx - 13 * d, cy - 13 * d, cx + 13 * d, cy + 13 * d); }
        else frame.inset(1.5f * d, 1.5f * d);
        float r = circle ? 13 * d : Math.min(radiusDp * d, frame.height() / 2f);
        int g = green(context);
        if (filled) {
            paint.setStyle(Paint.Style.FILL); paint.setMaskFilter(null);
            paint.setColor(black ? 0xff161616 : dark ? 0xff25252b : AppSurfaces.normalCard(context));
            c.drawRoundRect(frame, r, r, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        if (black) {
            paint.setMaskFilter(null); paint.setStrokeWidth(1.2f * d); paint.setColor((g & 0x00ffffff) | 0xd0000000);
            c.drawRoundRect(frame, r, r, paint);
            return;
        }
        paint.setStrokeWidth(1.5f * d);
        paint.setColor((g & 0x00ffffff) | (frosted ? (dark ? 0xb0000000 : 0xa0000000) : (dark ? 0x90000000 : 0x80000000)));
        paint.setMaskFilter(new BlurMaskFilter((frosted ? 1.6f : 1.2f) * d, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(frame, r, r, paint);
        if (frosted) {
            float in = 1.6f * d;
            RectF inner = new RectF(frame.left + in, frame.top + in, frame.right - in, frame.bottom - in);
            paint.setStrokeWidth(2.2f * d);
            paint.setColor((g & 0x00ffffff) | (dark ? 0x30000000 : 0x26000000));
            paint.setMaskFilter(new BlurMaskFilter(2.2f * d, BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(inner, Math.max(0, r - in), Math.max(0, r - in), paint);
        }
        paint.setMaskFilter(null);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
