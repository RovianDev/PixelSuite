package io.github.pixelsuite;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class SearchGlyph extends Drawable {
    static final String SEARCH = "search", BACK = "back", CLEAR = "clear", CALL = "call", MESSAGE = "message", CHAT = "chat", WEB = "web", FILE = "file", FOLDER = "folder",
            EVENT = "event", SETTINGS = "settings", CALC = "calc", SHARE = "share", OPEN = "open", SUITE = "suite", PERSON = "person", ADD = "add", UP = "up", DOWN = "down", PREV = "prev", NEXT = "next", GRIP = "grip", TICK = "tick", HEART = "hearts", PIN = "pin", PLUS = "plus", MORE = "more", OPACITY = "opacity", KEYS = "keys", STAR = "star", STAR_ON = "star_on", APPS = "apps", BOLT = "bolt", ALL = "all", PAGE = "page", COIN = "coin", REFRESH = "refresh";
    final String kind;
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    SearchGlyph(String kind, int color) {
        this.kind = kind;
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setColor(color);
    }

    static void badge(final android.view.View chip, int color, final boolean pop) {
        final SearchGlyph star = new SearchGlyph(STAR_ON, color);
        final float d = chip.getResources().getDisplayMetrics().density, size = 13 * d;
        final float[] k = {pop ? 0f : 1f};
        Drawable mark = new Drawable() {
            @Override public void draw(Canvas c) {
                float cx = chip.getWidth() / 2f, cy = -13f * d + (1 - Math.min(1f, k[0])) * 8 * d, half = size * k[0] / 2;
                if (half <= 0) return;
                star.setBounds(Math.round(cx - half), Math.round(cy - half), Math.round(cx + half), Math.round(cy + half));
                star.draw(c);
            }
            @Override public void setAlpha(int a) { }
            @Override public void setColorFilter(ColorFilter f) { }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
        mark.setBounds(-4000, -4000, 4000, 4000);
        chip.getOverlay().add(mark);
        if (!pop) return;
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setStartDelay(120);
        a.setDuration(520);
        a.setInterpolator(new android.view.animation.OvershootInterpolator(3f));
        a.addUpdateListener(x -> { k[0] = (Float) x.getAnimatedValue(); chip.invalidate(); });
        a.start();
    }

    void setColor(int color) { stroke.setColor(color); fill.setColor(color); invalidateSelf(); }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()), x = b.exactCenterX(), y = b.exactCenterY(), u = s / 24f;
        stroke.setStrokeWidth(2f * u);
        path.reset();
        int save = c.save();
        if (BACK.equals(kind) && getLayoutDirection() == android.util.LayoutDirection.RTL) c.scale(-1, 1, x, y);
        switch (kind) {
            case SEARCH: c.drawCircle(x - 1.6f * u, y - 1.6f * u, 6.2f * u, stroke); c.drawLine(x + 3f * u, y + 3f * u, x + 8f * u, y + 8f * u, stroke); break;
            case BACK: path.moveTo(x + 2 * u, y - 7 * u); path.lineTo(x - 5 * u, y); path.lineTo(x + 2 * u, y + 7 * u); c.drawPath(path, stroke); c.drawLine(x - 5 * u, y, x + 8 * u, y, stroke); break;
            case CLEAR: c.drawLine(x - 6 * u, y - 6 * u, x + 6 * u, y + 6 * u, stroke); c.drawLine(x + 6 * u, y - 6 * u, x - 6 * u, y + 6 * u, stroke); break;
            case ADD: c.drawLine(x - 7 * u, y, x + 7 * u, y, stroke); c.drawLine(x, y - 7 * u, x, y + 7 * u, stroke); break;
            case UP: path.moveTo(x - 6 * u, y + 3 * u); path.lineTo(x, y - 3 * u); path.lineTo(x + 6 * u, y + 3 * u); c.drawPath(path, stroke); break;
            case PREV: path.moveTo(x + 3 * u, y - 6 * u); path.lineTo(x - 3 * u, y); path.lineTo(x + 3 * u, y + 6 * u); c.drawPath(path, stroke); break;
            case NEXT: path.moveTo(x - 3 * u, y - 6 * u); path.lineTo(x + 3 * u, y); path.lineTo(x - 3 * u, y + 6 * u); c.drawPath(path, stroke); break;
            case STAR: case STAR_ON:
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5; float rad = (i % 2 == 0 ? 8.6f : 3.9f) * u;
                    float px = x + (float) Math.cos(a) * rad, py = y + 0.6f * u + (float) Math.sin(a) * rad;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close(); c.drawPath(path, STAR_ON.equals(kind) ? fill : stroke); if (STAR_ON.equals(kind)) c.drawPath(path, stroke); break;
            case APPS:
                for (int i = 0; i < 9; i++) c.drawCircle(x + (i % 3 - 1) * 6.2f * u, y + (i / 3 - 1) * 6.2f * u, 1.7f * u, fill);
                break;
            case BOLT:
                path.moveTo(x + 2 * u, y - 9 * u); path.lineTo(x - 5.5f * u, y + 1.5f * u); path.lineTo(x - 0.5f * u, y + 1.5f * u); path.lineTo(x - 2 * u, y + 9 * u);
                path.lineTo(x + 5.5f * u, y - 1.5f * u); path.lineTo(x + 0.5f * u, y - 1.5f * u); path.close(); c.drawPath(path, fill); break;
            case ALL:
                r.set(x - 8 * u, y - 8 * u, x - 1.5f * u, y - 1.5f * u); c.drawRoundRect(r, 1.8f * u, 1.8f * u, stroke);
                r.set(x + 1.5f * u, y + 1.5f * u, x + 8 * u, y + 8 * u); c.drawRoundRect(r, 1.8f * u, 1.8f * u, stroke);
                c.drawCircle(x + 4.75f * u, y - 4.75f * u, 3.3f * u, stroke); c.drawCircle(x - 4.75f * u, y + 4.75f * u, 3.3f * u, stroke); break;
            case PAGE:
                r.set(x - 6.5f * u, y - 8.5f * u, x + 6.5f * u, y + 8.5f * u); c.drawRoundRect(r, 2 * u, 2 * u, stroke);
                c.drawLine(x - 3 * u, y - 3.5f * u, x + 3 * u, y - 3.5f * u, stroke); c.drawLine(x - 3 * u, y, x + 3 * u, y, stroke); c.drawLine(x - 3 * u, y + 3.5f * u, x + 0.5f * u, y + 3.5f * u, stroke); break;
            case COIN:
                c.drawCircle(x, y, 8.5f * u, stroke);
                r.set(x - 3 * u, y - 4.2f * u, x + 3 * u, y); c.drawArc(r, 20, -250, false, stroke);
                r.set(x - 3 * u, y, x + 3 * u, y + 4.2f * u); c.drawArc(r, 200, -250, false, stroke);
                c.drawLine(x, y - 6 * u, x, y + 6 * u, stroke); break;
            case REFRESH:
                r.set(x - 7 * u, y - 7 * u, x + 7 * u, y + 7 * u); c.drawArc(r, -50, 290, false, stroke);
                path.moveTo(x + 3 * u, y - 8.5f * u); path.lineTo(x + 7.5f * u, y - 5.2f * u); path.lineTo(x + 2.5f * u, y - 2.8f * u); c.drawPath(path, stroke); break;
            case TICK:
                path.moveTo(x - 6.5f * u, y + 0.5f * u); path.lineTo(x - 2 * u, y + 5 * u); path.lineTo(x + 7 * u, y - 5 * u); c.drawPath(path, stroke); break;
            case HEART:
                path.moveTo(x, y + 8 * u);
                path.cubicTo(x - 12 * u, y + 0.5f * u, x - 8.5f * u, y - 9.5f * u, x, y - 3.5f * u);
                path.cubicTo(x + 8.5f * u, y - 9.5f * u, x + 12 * u, y + 0.5f * u, x, y + 8 * u);
                path.close(); c.drawPath(path, fill); break;
            case PLUS:
                c.drawLine(x - 6.5f * u, y, x + 6.5f * u, y, stroke); c.drawLine(x, y - 6.5f * u, x, y + 6.5f * u, stroke); break;
            case PIN:
                c.drawCircle(x, y - 3.5f * u, 4.6f * u, fill);
                c.drawLine(x, y + 1 * u, x, y + 9 * u, stroke); break;
            case MORE:
                for (int i = -1; i <= 1; i++) c.drawCircle(x, y + i * 6.2f * u, 1.9f * u, fill);
                break;
            case OPACITY:
                c.drawCircle(x, y, 8 * u, stroke);
                r.set(x - 8 * u, y - 8 * u, x + 8 * u, y + 8 * u); c.drawArc(r, 90, 180, true, fill); break;
            case KEYS:
                r.set(x - 9.5f * u, y - 6.5f * u, x + 9.5f * u, y + 6.5f * u); c.drawRoundRect(r, 3 * u, 3 * u, stroke);
                for (int i = -1; i <= 1; i++) c.drawCircle(x + i * 4.6f * u, y - 2.2f * u, 1.05f * u, fill);
                c.drawLine(x - 4.4f * u, y + 2.6f * u, x + 4.4f * u, y + 2.6f * u, stroke); break;
            case GRIP: c.drawLine(x - 6 * u, y - 3 * u, x + 6 * u, y - 3 * u, stroke); c.drawLine(x - 6 * u, y + 3 * u, x + 6 * u, y + 3 * u, stroke); break;
            case DOWN: path.moveTo(x - 6 * u, y - 3 * u); path.lineTo(x, y + 3 * u); path.lineTo(x + 6 * u, y - 3 * u); c.drawPath(path, stroke); break;
            case "voice":
                r.set(x - 3.5f * u, y - 9 * u, x + 3.5f * u, y + 3 * u); c.drawRoundRect(r, 3.5f * u, 3.5f * u, fill);
                r.set(x - 7 * u, y - 5 * u, x + 7 * u, y + 6.5f * u); c.drawArc(r, 0, 180, false, stroke);
                c.drawLine(x, y + 6.5f * u, x, y + 9.5f * u, stroke); break;
            case "lens":
                float h = 8.5f * u, k = 3.6f * u;
                path.moveTo(x - h, y - h + k); path.lineTo(x - h, y - h + 1.2f * u); path.quadTo(x - h, y - h, x - h + 1.2f * u, y - h); path.lineTo(x - h + k, y - h);
                path.moveTo(x + h - k, y - h); path.lineTo(x + h - 1.2f * u, y - h); path.quadTo(x + h, y - h, x + h, y - h + 1.2f * u); path.lineTo(x + h, y - h + k);
                path.moveTo(x - h, y + h - k); path.lineTo(x - h, y + h - 1.2f * u); path.quadTo(x - h, y + h, x - h + 1.2f * u, y + h); path.lineTo(x - h + k, y + h);
                c.drawPath(path, stroke);
                c.drawCircle(x, y, 2.8f * u, fill); c.drawCircle(x + h - 0.6f * u, y + h - 0.6f * u, 2.1f * u, fill); break;
            case "song":
                r.set(x - 7 * u, y + 2 * u, x - 0.5f * u, y + 8 * u); c.drawOval(r, fill);
                c.drawRect(x - 2.6f * u, y - 8.5f * u, x - 0.5f * u, y + 5 * u, fill);
                path.moveTo(x - 2.6f * u, y - 8.5f * u); path.lineTo(x + 6.5f * u, y - 8.5f * u); path.lineTo(x + 6.5f * u, y - 4.5f * u); path.lineTo(x - 0.5f * u, y - 4.5f * u); path.close(); c.drawPath(path, fill); break;
            case "assistant":
                path.moveTo(x, y - 9 * u); path.quadTo(x + 1.2f * u, y - 1.2f * u, x + 9 * u, y); path.quadTo(x + 1.2f * u, y + 1.2f * u, x, y + 9 * u);
                path.quadTo(x - 1.2f * u, y + 1.2f * u, x - 9 * u, y); path.quadTo(x - 1.2f * u, y - 1.2f * u, x, y - 9 * u); path.close(); c.drawPath(path, fill); break;
            case "camera":
                r.set(x - 9 * u, y - 5.5f * u, x + 9 * u, y + 7.5f * u); c.drawRoundRect(r, 2.5f * u, 2.5f * u, stroke);
                path.moveTo(x - 4 * u, y - 5.5f * u); path.lineTo(x - 2.5f * u, y - 8 * u); path.lineTo(x + 2.5f * u, y - 8 * u); path.lineTo(x + 4 * u, y - 5.5f * u); c.drawPath(path, stroke);
                c.drawCircle(x, y + 1 * u, 3.4f * u, stroke); break;
            case "scan":
                path.moveTo(x - 8 * u, y - 4 * u); path.lineTo(x - 8 * u, y - 8 * u); path.lineTo(x - 4 * u, y - 8 * u);
                path.moveTo(x + 4 * u, y - 8 * u); path.lineTo(x + 8 * u, y - 8 * u); path.lineTo(x + 8 * u, y - 4 * u);
                path.moveTo(x + 8 * u, y + 4 * u); path.lineTo(x + 8 * u, y + 8 * u); path.lineTo(x + 4 * u, y + 8 * u);
                path.moveTo(x - 4 * u, y + 8 * u); path.lineTo(x - 8 * u, y + 8 * u); path.lineTo(x - 8 * u, y + 4 * u);
                c.drawPath(path, stroke);
                c.drawLine(x - 4 * u, y - 3 * u, x + 4 * u, y - 3 * u, stroke); c.drawLine(x - 4 * u, y, x + 4 * u, y, stroke); c.drawLine(x - 4 * u, y + 3 * u, x + 1 * u, y + 3 * u, stroke); break;
            case "crop":
                path.moveTo(x - 5 * u, y - 9 * u); path.lineTo(x - 5 * u, y + 5 * u); path.lineTo(x + 9 * u, y + 5 * u);
                path.moveTo(x - 9 * u, y - 5 * u); path.lineTo(x + 5 * u, y - 5 * u); path.lineTo(x + 5 * u, y + 9 * u);
                c.drawPath(path, stroke); break;
            case "bin":
                c.drawLine(x - 7.5f * u, y - 5.5f * u, x + 7.5f * u, y - 5.5f * u, stroke);
                path.moveTo(x - 3 * u, y - 5.5f * u); path.lineTo(x - 2 * u, y - 8.5f * u); path.lineTo(x + 2 * u, y - 8.5f * u); path.lineTo(x + 3 * u, y - 5.5f * u);
                path.moveTo(x - 5.5f * u, y - 5.5f * u); path.lineTo(x - 4.5f * u, y + 8.5f * u); path.lineTo(x + 4.5f * u, y + 8.5f * u); path.lineTo(x + 5.5f * u, y - 5.5f * u);
                c.drawPath(path, stroke); break;
            case "turn":
                r.set(x - 8 * u, y - 2 * u, x + 3 * u, y + 9 * u); c.drawRoundRect(r, 2 * u, 2 * u, stroke);
                r.set(x - 3 * u, y - 8 * u, x + 9 * u, y + 4 * u); c.drawArc(r, -90, 90, false, stroke);
                path.moveTo(x + 6 * u, y + 0.5f * u); path.lineTo(x + 9 * u, y + 4 * u); path.lineTo(x + 11.5f * u, y + 0.5f * u); c.drawPath(path, stroke); break;
            case "album":
                r.set(x - 8.5f * u, y - 7 * u, x + 8.5f * u, y + 7 * u); c.drawRoundRect(r, 2.5f * u, 2.5f * u, stroke);
                path.moveTo(x - 8 * u, y + 4.5f * u); path.lineTo(x - 2.5f * u, y - 1 * u); path.lineTo(x + 2 * u, y + 3.5f * u); path.lineTo(x + 4.5f * u, y + 1.5f * u); path.lineTo(x + 8 * u, y + 5 * u);
                c.drawPath(path, stroke); c.drawCircle(x + 3.8f * u, y - 3 * u, 1.4f * u, fill); break;
            case "past":
                r.set(x - 8 * u, y - 8 * u, x + 8 * u, y + 8 * u); c.drawArc(r, -60, 320, false, stroke);
                path.moveTo(x, y - 4.5f * u); path.lineTo(x, y + 0.5f * u); path.lineTo(x + 3.5f * u, y + 2.5f * u); c.drawPath(path, stroke);
                path.reset(); path.moveTo(x - 9.5f * u, y - 9 * u); path.lineTo(x - 8 * u, y - 4.5f * u); path.lineTo(x - 3.5f * u, y - 6 * u); c.drawPath(path, stroke); break;
            case "hide":
                path.moveTo(x - 9 * u, y); path.quadTo(x, y - 8 * u, x + 9 * u, y); path.quadTo(x, y + 8 * u, x - 9 * u, y);
                c.drawPath(path, stroke); c.drawCircle(x, y, 2.6f * u, stroke);
                c.drawLine(x - 7.5f * u, y + 7.5f * u, x + 7.5f * u, y - 7.5f * u, stroke); break;
            case "qr":
                float q = 3.4f * u;
                for (int i = 0; i < 3; i++) {
                    float cx = i == 1 ? x + 4.6f * u : x - 4.6f * u, cy = i == 2 ? y + 4.6f * u : y - 4.6f * u;
                    r.set(cx - q, cy - q, cx + q, cy + q); c.drawRoundRect(r, u, u, stroke); c.drawRect(cx - u, cy - u, cx + u, cy + u, fill);
                }
                c.drawRect(x + 2.4f * u, y + 2.4f * u, x + 4.6f * u, y + 4.6f * u, fill); c.drawRect(x + 5.8f * u, y + 5.8f * u, x + 8 * u, y + 8 * u, fill);
                c.drawRect(x + 5.8f * u, y + 2.4f * u, x + 8 * u, y + 3.8f * u, fill); break;
            case CALL:
                path.moveTo(x - 6.5f * u, y - 8 * u); path.lineTo(x - 3 * u, y - 8 * u); path.lineTo(x - 1.5f * u, y - 3.5f * u); path.lineTo(x - 3.8f * u, y - 2 * u);
                path.quadTo(x - 1.5f * u, y + 2.5f * u, x + 2 * u, y + 3.8f * u); path.lineTo(x + 3.5f * u, y + 1.5f * u); path.lineTo(x + 8 * u, y + 3 * u); path.lineTo(x + 8 * u, y + 6.5f * u);
                path.quadTo(x + 8 * u, y + 8.5f * u, x + 6 * u, y + 8.5f * u); path.quadTo(x - 8.5f * u, y + 7.5f * u, x - 8.5f * u, y - 6 * u); path.quadTo(x - 8.5f * u, y - 8 * u, x - 6.5f * u, y - 8 * u); path.close();
                c.drawPath(path, fill); break;
            case MESSAGE:
                r.set(x - 8.5f * u, y - 7.5f * u, x + 8.5f * u, y + 5 * u); c.drawRoundRect(r, 2.5f * u, 2.5f * u, stroke);
                path.moveTo(x - 4 * u, y + 5 * u); path.lineTo(x - 6 * u, y + 8.5f * u); path.lineTo(x, y + 5 * u); c.drawPath(path, stroke); break;
            case CHAT:
                c.drawCircle(x, y, 8 * u, stroke);
                path.moveTo(x - 6.2f * u, y + 5.2f * u); path.lineTo(x - 7.6f * u, y + 8.6f * u); path.lineTo(x - 3.8f * u, y + 7.4f * u); c.drawPath(path, stroke);
                c.drawCircle(x - 3 * u, y, 1.1f * u, fill); c.drawCircle(x, y, 1.1f * u, fill); c.drawCircle(x + 3 * u, y, 1.1f * u, fill); break;
            case WEB:
                c.drawCircle(x, y, 8.5f * u, stroke); r.set(x - 4 * u, y - 8.5f * u, x + 4 * u, y + 8.5f * u); c.drawOval(r, stroke);
                c.drawLine(x - 8.5f * u, y, x + 8.5f * u, y, stroke); break;
            case FILE:
                path.moveTo(x - 6.5f * u, y - 9 * u); path.lineTo(x + 2.5f * u, y - 9 * u); path.lineTo(x + 6.5f * u, y - 5 * u); path.lineTo(x + 6.5f * u, y + 9 * u); path.lineTo(x - 6.5f * u, y + 9 * u); path.close();
                c.drawPath(path, stroke); c.drawLine(x - 3 * u, y + 1 * u, x + 3 * u, y + 1 * u, stroke); c.drawLine(x - 3 * u, y + 4.5f * u, x + 1.5f * u, y + 4.5f * u, stroke); break;
            case FOLDER:
                path.moveTo(x - 9 * u, y - 6.5f * u); path.lineTo(x - 3 * u, y - 6.5f * u); path.lineTo(x - 1 * u, y - 4 * u); path.lineTo(x + 9 * u, y - 4 * u); path.lineTo(x + 9 * u, y + 7.5f * u); path.lineTo(x - 9 * u, y + 7.5f * u); path.close();
                c.drawPath(path, stroke); break;
            case EVENT:
                r.set(x - 8 * u, y - 6.5f * u, x + 8 * u, y + 8.5f * u); c.drawRoundRect(r, 2 * u, 2 * u, stroke);
                c.drawLine(x - 8 * u, y - 2 * u, x + 8 * u, y - 2 * u, stroke); c.drawLine(x - 4 * u, y - 9 * u, x - 4 * u, y - 5 * u, stroke); c.drawLine(x + 4 * u, y - 9 * u, x + 4 * u, y - 5 * u, stroke);
                c.drawRect(x + 1.5f * u, y + 2 * u, x + 4.5f * u, y + 5 * u, fill); break;
            case SETTINGS:
                c.drawCircle(x, y, 3 * u, stroke);
                for (int i = 0; i < 8; i++) { double a = Math.PI * i / 4; c.drawLine(x + (float) Math.cos(a) * 6 * u, y + (float) Math.sin(a) * 6 * u, x + (float) Math.cos(a) * 8.6f * u, y + (float) Math.sin(a) * 8.6f * u, stroke); }
                c.drawCircle(x, y, 6 * u, stroke); break;
            case CALC:
                r.set(x - 7 * u, y - 9 * u, x + 7 * u, y + 9 * u); c.drawRoundRect(r, 2 * u, 2 * u, stroke);
                c.drawLine(x - 3.5f * u, y - 5 * u, x + 3.5f * u, y - 5 * u, stroke);
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) c.drawCircle(x - 2.5f * u + j * 5 * u, y + 0.5f * u + i * 4.5f * u, 1.1f * u, fill); break;
            case SHARE:
                c.drawCircle(x + 5.5f * u, y - 6 * u, 2.4f * u, stroke); c.drawCircle(x - 5.5f * u, y, 2.4f * u, stroke); c.drawCircle(x + 5.5f * u, y + 6 * u, 2.4f * u, stroke);
                c.drawLine(x - 3.4f * u, y - 1.2f * u, x + 3.4f * u, y - 4.8f * u, stroke); c.drawLine(x - 3.4f * u, y + 1.2f * u, x + 3.4f * u, y + 4.8f * u, stroke); break;
            case OPEN:
                path.moveTo(x - 1 * u, y - 7.5f * u); path.lineTo(x - 7.5f * u, y - 7.5f * u); path.lineTo(x - 7.5f * u, y + 7.5f * u); path.lineTo(x + 7.5f * u, y + 7.5f * u); path.lineTo(x + 7.5f * u, y + 1 * u);
                c.drawPath(path, stroke); c.drawLine(x + 1 * u, y - 1 * u, x + 8 * u, y - 8 * u, stroke); c.drawLine(x + 3 * u, y - 8 * u, x + 8 * u, y - 8 * u, stroke); c.drawLine(x + 8 * u, y - 8 * u, x + 8 * u, y - 3 * u, stroke); break;
            case SUITE:
                r.set(x - 8 * u, y - 8 * u, x + 8 * u, y + 8 * u); c.drawRoundRect(r, 5 * u, 5 * u, stroke); c.drawCircle(x, y, 2.6f * u, fill); break;
            case PERSON:
                c.drawCircle(x, y - 3.5f * u, 3.8f * u, stroke); r.set(x - 7.5f * u, y + 2 * u, x + 7.5f * u, y + 14 * u); c.drawArc(r, 180, 180, false, stroke); break;
            default: c.drawCircle(x, y, 3 * u, fill);
        }
        c.restoreToCount(save);
    }

    @Override public void setAlpha(int a) { stroke.setAlpha(a); fill.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { stroke.setColorFilter(f); fill.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
