package io.github.pixelsuite;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class GlassRim extends Drawable {
    final float radius, width; final boolean dark;Context context;final SuperDarkPalette.Rim exactRim=new SuperDarkPalette.Rim();final Rect exactBounds=new Rect();
    final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int shaderH = -1, alpha = 255;

    GlassRim(float radius, float width) { this(radius, width, false); }
    GlassRim(float radius, float width, boolean dark) {
        this.radius = radius; this.width = width; this.dark = dark;
        edge.setDither(true); shine.setDither(true);
        edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(width);
        edge.setMaskFilter(new BlurMaskFilter(width * 0.9f, BlurMaskFilter.Blur.NORMAL));
        shine.setStyle(Paint.Style.STROKE); shine.setStrokeWidth(width * 1.6f);
        shine.setMaskFilter(new BlurMaskFilter(width * 1.4f, BlurMaskFilter.Blur.NORMAL));
    }

    static final int NONE = 0, LIGHT = 1, DARK = 2;
    private static long sdAt; private static boolean sdOn;
    static int mode(Context c) {
        try {
            if (c == null) return NONE;
            boolean dark = FrostedSurface.dark(c);
            long now = android.os.SystemClock.uptimeMillis();
            if (sdAt == 0 || now - sdAt > 500) { sdOn = Feature.on(c, SuperDark.KEY); sdAt = now; }
            if (dark && sdOn) return DARK;
            if (SurfaceStyle.get(c) != SurfaceStyle.FROSTED) return NONE;
            return dark ? DARK : LIGHT;
        } catch (Throwable t) { return NONE; }
    }
    static boolean wanted(Context c) { return mode(c) != NONE; }

    static boolean superDarkRims(Context c) {
        try { return c == null || SuperDark.inEffect(c) || SurfaceStyle.get(c) != SurfaceStyle.OFF; } catch (Throwable t) { return true; }
    }
    static boolean superDarkRims() {
        try { return superDarkRims((Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)); } catch (Throwable t) { return true; }
    }

    static GlassRim of(Context c, float radiusPx) { GlassRim rim=new GlassRim(radiusPx, FrostedSurface.dp(c, 1.5f), mode(c) == DARK);rim.context=c;AppSurfaces.watch(rim);return rim; }

    static Drawable over(Context c, Drawable panel, float radiusPx) {
        return wanted(c) ? new android.graphics.drawable.LayerDrawable(new Drawable[]{panel, of(c, radiusPx)}) : panel;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || alpha == 0) return;
        draw(canvas, b.left, b.top, b.right, b.bottom, Math.min(radius, Math.min(b.width(), b.height()) / 2f), alpha);
    }

    void draw(Canvas canvas, float l, float t, float r, float bt, float rad, int a) {
        if(context!=null&&SuperDark.inEffect(context)){exactBounds.set(Math.round(l),Math.round(t),Math.round(r),Math.round(bt));exactRim.paint.setAlpha(a);exactRim.draw(canvas,exactBounds,rad,context.getResources().getDisplayMetrics().density);return;}
        if (dark) {
            if (shaderH != Math.round(bt - t)) {
                shaderH = Math.round(bt - t);
                shine.setShader(new LinearGradient(0, t, 0, bt, new int[]{0x30ffffff, 0x12ffffff, 0x0cffffff, 0x16ffffff},
                        new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            }
            shine.setAlpha(a);
            float in = width * 0.5f;
            canvas.drawRoundRect(l + in, t + in, r - in, bt - in, Math.max(0, rad - in), Math.max(0, rad - in), shine);
            return;
        }
        if (shaderH != Math.round(bt - t)) {
            shaderH = Math.round(bt - t);
            shine.setShader(new LinearGradient(0, t, 0, bt, new int[]{0xE6FFFFFF, 0x8CFFFFFF, 0x66FFFFFF, 0x80FFFFFF},
                    new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            edge.setShader(new LinearGradient(0, t, 0, bt, new int[]{0x14000000, 0x1F000000, 0x29000000},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        }
        edge.setAlpha(a); shine.setAlpha(a);
        float in = width * 0.5f;
        canvas.drawRoundRect(l + in, t + in, r - in, bt - in, Math.max(0, rad - in), Math.max(0, rad - in), edge);
        float in2 = width * 1.4f;
        canvas.drawRoundRect(l + in2, t + in2, r - in2, bt - in2, Math.max(0, rad - in2), Math.max(0, rad - in2), shine);
    }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
