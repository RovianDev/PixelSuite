package io.github.pixelsuite;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class NoiseGrain extends Drawable {
    private static Bitmap sTile;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float radius;
    private int strength;

    NoiseGrain(float radius, int strength) {
        this.radius = radius;
        this.strength = Math.max(0, Math.min(255, strength));
        paint.setShader(new BitmapShader(tile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        paint.setAlpha(this.strength);
    }

    static synchronized Bitmap tile() {
        if (sTile != null && !sTile.isRecycled()) return sTile;
        int n = 64;
        int[] px = new int[n * n];
        long seed = 0x5DEECE66DL;
        for (int i = 0; i < px.length; i++) {
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            int r = (int) (seed >>> 40) & 0xff;
            int v = r < 128 ? 0 : 255;
            int a = 8 + (r & 7);
            px[i] = (a << 24) | (v << 16) | (v << 8) | v;
        }
        sTile = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        return sTile;
    }

    static void draw(Canvas canvas, RectF area, float radius, int strength) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(tile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        p.setAlpha(Math.max(0, Math.min(255, strength)));
        if (radius > 0) canvas.drawRoundRect(area, radius, radius, p); else canvas.drawRect(area, p);
    }

    void draw(Canvas canvas, RectF area, int alpha) {
        paint.setAlpha(strength * Math.max(0, Math.min(255, alpha)) / 255);
        if (radius > 0) canvas.drawRoundRect(area, radius, radius, paint); else canvas.drawRect(area, paint);
    }

    @Override public void draw(Canvas canvas) {
        box.set(getBounds());
        float r = Math.min(radius, Math.min(box.width(), box.height()) / 2f);
        canvas.drawRoundRect(box, r, r, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(strength * alpha / 255); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
