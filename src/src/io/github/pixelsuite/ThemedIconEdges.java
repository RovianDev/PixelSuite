package io.github.pixelsuite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

final class ThemedIconEdges {
    private ThemedIconEdges() {}
    private static float visible = -1f;

    static void install() {
        try {
            Xp.hookAllMethods(LauncherFeatures.type("icons.mono.ThemedIconDelegate"), "drawContent", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length < 5) return;
                        Object shape = p.args[1];
                        Canvas canvas = (Canvas) p.args[2];
                        Rect rect = (Rect) p.args[3];
                        Paint paint = (Paint) p.args[4];
                        if (shape == null || canvas == null || rect == null || paint == null || rect.isEmpty()) return;
                        Object renderer = LauncherFeatures.get(shape, "shapeRenderer");
                        if (renderer == null) return;
                        java.lang.reflect.Method render = renderer.getClass().getMethod("render", Canvas.class, Paint.class);
                        Bitmap shadow = (Bitmap) LauncherFeatures.get(shape, "shadowLayer");
                        float size = ((Number) LauncherFeatures.get(shape, "pathSize")).floatValue();
                        Object delegate = p.thisObject;
                        Bitmap mono = (Bitmap) LauncherFeatures.get(delegate, "monoIcon");
                        Rect bounds = (Rect) LauncherFeatures.get(delegate, "shapeBounds");
                        Paint monoPaint = (Paint) LauncherFeatures.get(delegate, "monoPaint");

                        if (shadow != null) canvas.drawBitmap(shadow, null, rect, paint);
                        int save = canvas.save();
                        try {
                            canvas.translate(rect.left, rect.top);
                            canvas.scale(rect.width() / size, rect.height() / size);
                            float f = visibleArea();
                            canvas.scale(f, f, size / 2f, size / 2f);
                            int layer = canvas.saveLayer(-1f, -1f, size + 1f, size + 1f, null);
                            try {
                                boolean aa = paint.isAntiAlias();
                                paint.setAntiAlias(true);
                                try { render.invoke(renderer, canvas, paint); } finally { paint.setAntiAlias(aa); }
                                if (mono != null && bounds != null && monoPaint != null && !mono.isRecycled()) {
                                    android.graphics.Xfermode before = monoPaint.getXfermode();
                                    monoPaint.setXfermode(atop());
                                    try { canvas.drawBitmap(mono, null, bounds, monoPaint); } finally { monoPaint.setXfermode(before); }
                                }
                            } finally { canvas.restoreToCount(layer); }
                        } finally { canvas.restoreToCount(save); }
                        p.setResult(null);
                    } catch (Throwable stock) {  }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static android.graphics.Xfermode atop;
    private static android.graphics.Xfermode atop() {
        if (atop == null) atop = new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP);
        return atop;
    }

    private static float visibleArea() {
        if (visible > 0f) return visible;
        float v = 0.92f;
        try { v = LauncherFeatures.type("icons.IconNormalizer").getField("ICON_VISIBLE_AREA_FACTOR").getFloat(null); } catch (Throwable ignored) { }
        visible = v;
        return v;
    }
}
