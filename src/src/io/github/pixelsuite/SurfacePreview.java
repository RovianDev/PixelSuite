package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Date;

final class SurfacePreview extends View {
    private static final float SCREEN_W = 100f, SCREEN_H = 210f, CORNER = 10f;
    private static final float PL = 13f, PT = 40f, PR = 87f, PB = 114f, PRAD = 9f;
    private static final float[] COLS = {17, 39, 61, 83};
    private static final float[] ROWS = {104, 126, 148};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF(), box2 = new RectF();
    private final Path path = new Path();
    private final float dp;
    private final boolean pageDark;
    private final int ink;
    private final float[] weight = new float[3], from = new float[3];
    private final String folderLabel, foldersLabel, shareLabel;
    private int style;
    private float pop;
    private ValueAnimator fade;

    private FeedPreview.Look look;
    private boolean dark, superDark;
    private int off, stock, frost, onPanel, rim, muted;
    private final int[] sheet = new int[3];
    private String time = "12:30", date = "";
    private Bitmap home, soft;
    private int builtW;

    SurfacePreview(Context c, int style, boolean pageDark, int ink) {
        super(c);
        this.pageDark = pageDark; this.ink = ink;
        dp = c.getResources().getDisplayMetrics().density;
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        folderLabel = I18n.cs("Folder").toString();
        foldersLabel = I18n.cs("Folders").toString();
        shareLabel = I18n.cs("Share menu").toString();
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(I18n.medium());
        label.setTextSize(12.5f * c.getResources().getDisplayMetrics().scaledDensity);
        this.style = Math.max(0, Math.min(2, style));
        weight[this.style] = 1f;
        colors();
    }

    void show(int value, boolean animate) {
        value = Math.max(0, Math.min(2, value));
        if (value == style && weight[value] >= 1f) return;
        style = value;
        if (fade != null) fade.cancel();
        if (!animate || !isAttachedToWindow()) {
            for (int i = 0; i < 3; i++) weight[i] = i == value ? 1f : 0f;
            pop = 0f;
            invalidate();
            return;
        }
        System.arraycopy(weight, 0, from, 0, 3);
        final int target = value;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(320);
        fade.setInterpolator(new DecelerateInterpolator(1.6f));
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float k = (Float) a.getAnimatedValue();
                for (int i = 0; i < 3; i++) weight[i] = from[i] + ((i == target ? 1f : 0f) - from[i]) * k;
                pop = (float) Math.sin(Math.PI * k);
                invalidate();
            }
        });
        fade.start();
    }

    void refresh() {
        colors();
        drop();
        invalidate();
    }

    private void colors() {
        Context c = getContext();
        dark = FrostedSurface.dark(c);
        superDark = SuperDark.inEffect(c);
        look = new FeedPreview.Look(c, false);
        boolean night = dark || superDark;
        onPanel = night ? LauncherFeatures.surface(c, "system_on_surface_dark", 0xffe3e2e6) : LauncherFeatures.surface(c, "system_on_surface_light", 0xff1b1b1f);
        muted = (onPanel & 0xffffff) | 0x99000000;
        int n9 = LauncherFeatures.surface(c, "system_neutral1_900", 0xff1b1b1f), n8 = LauncherFeatures.surface(c, "system_neutral1_800", 0xff303034);
        int sheetStock = dark ? FeedPreview.Look.mix(n9, n8, 0.45f) : LauncherFeatures.surface(c, "system_neutral1_50", 0xfff2f0f4);
        sheet[SurfaceStyle.OFF] = dark ? 0xff121212 : 0xffffffff;
        sheet[SurfaceStyle.STOCK] = sheetStock;
        sheet[SurfaceStyle.FROSTED] = (sheetStock & 0xffffff) | (dark ? 0xd9000000 : 0xe6000000);
        if (superDark) {
            off = stock = frost = FrostedSurface.SUPER_DARK_PANEL; rim = 0x2effffff;
            sheet[0] = sheet[1] = sheet[2] = 0xff0e0e0e;
        } else {
            off = dark ? FrostedSurface.offDark(c) : 0xffffffff;
            stock = LauncherFeatures.surface(c, dark ? "system_surface_container_high_dark" : "system_surface_container_high_light", dark ? 0xff2b2b30 : 0xffeeedf2);
            int tone = FrostedSurface.composite(FrostedSurface.resourceColor(c, "blur_shade_panel_fg", dark ? 0x522c303d : 0x66dedee8),
                    FrostedSurface.resourceColor(c, "blur_shade_panel_bg", dark ? 0x520e0e0f : 0x66f9f9fe));
            frost = (tone & 0xffffff) | 0xcc000000;
            rim = dark ? 0x3dffffff : 0xb3ffffff;
        }
        try {
            java.util.Locale l = I18n.shownLocale();
            Date d = new Date();
            time = new java.text.SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(c) ? "H:mm" : "h:mm", l).format(d);
            date = new java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(l, "EEEMMMd"), l).format(d);
        } catch (Throwable ignored) { }
    }

    @Override protected void onDetachedFromWindow() {
        if (fade != null) { fade.cancel(); fade = null; }
        for (int i = 0; i < 3; i++) weight[i] = i == style ? 1f : 0f;
        pop = 0f;
        drop();
        super.onDetachedFromWindow();
    }

    private void drop() {
        home = null; soft = null; builtW = 0;
    }

    @Override protected void onDraw(Canvas c) {
        float gap = 18 * dp, labelH = 26 * dp;
        float h = getHeight() - 8 * dp - labelH, w = h * SCREEN_W / SCREEN_H;
        if (2 * w + gap > getWidth() - 8 * dp) { w = (getWidth() - 8 * dp - gap) / 2f; h = w * SCREEN_H / SCREEN_W; }
        if (w <= 0 || h <= 0) return;
        float top = 8 * dp, left = (getWidth() - 2 * w - gap) / 2f;
        int px = Math.round(w), py = Math.round(h);
        if (home == null || builtW != px) build(px, py);
        int under = -1;
        for (int i = 0; i < 3; i++) if (i != style && weight[i] > 0.004f && (under < 0 || weight[i] > weight[under])) under = i;
        for (int which = 0; which < 2; which++) {
            float x = left + which * (w + gap);
            float u = w / SCREEN_W;
            int save = c.save();
            c.translate(x, top);
            path.reset();
            box.set(0, 0, w, h);
            path.addRoundRect(box, CORNER * u, CORNER * u, Path.Direction.CW);
            c.clipPath(path);
            if (home != null) c.drawBitmap(home, null, box, paint);
            c.scale(u, u);
            if (which == 0) {
                fill(c, 0x47000000, 0, 0, SCREEN_W, SCREEN_H, 0);
                float s = 1f - 0.03f * pop;
                c.save();
                c.scale(s, s, (PL + PR) / 2f, (PT + PB) / 2f);
                if (under >= 0) panel(c, under, 1f);
                panel(c, style, weight[style]);
                folder(c);
                c.restore();
            } else {
                fill(c, 0x66000000, 0, 0, SCREEN_W, SCREEN_H, 0);
                if (under >= 0) sheet(c, under, 1f);
                sheet(c, style, weight[style]);
                sheetContent(c);
            }
            c.restoreToCount(save);
            frame(c, x, top, w, h);
            label.setColor(ink);
            c.drawText(which == 0 ? foldersLabel : shareLabel, x + w / 2f, top + h + 19 * dp, label);
        }
    }

    private void panel(Canvas c, int which, float a) {
        if (a <= 0.004f) return;
        int layer = c.saveLayerAlpha(PL - 4, PT - 4, PR + 4, PB + 6, Math.round(255 * Math.min(1f, a)));
        for (int i = 3; i >= 1; i--) {
            float o = i * 0.9f;
            fill(c, Color.argb(16, 0, 0, 0), PL - o * 0.4f, PT + o * 0.5f, PR + o * 0.4f, PB + o * 1.3f, PRAD + o);
        }
        int clip = c.save();
        box2.set(PL, PT, PR, PB);
        path.reset();
        path.addRoundRect(box2, PRAD, PRAD, Path.Direction.CW);
        c.clipPath(path);
        if (which == SurfaceStyle.FROSTED && !superDark && soft != null) {
            box.set(0, 0, SCREEN_W, SCREEN_H);
            c.drawBitmap(soft, null, box, paint);
            fill(c, frost, PL, PT, PR, PB, 0);
        } else fill(c, which == SurfaceStyle.OFF ? off : which == SurfaceStyle.STOCK ? stock : frost | 0xff000000, PL, PT, PR, PB, 0);
        c.restoreToCount(clip);
        if (superDark ? which != SurfaceStyle.OFF : which == SurfaceStyle.FROSTED) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.6f);
            paint.setColor(rim);
            box2.set(PL + 0.3f, PT + 0.3f, PR - 0.3f, PB - 0.3f);
            c.drawRoundRect(box2, PRAD - 0.3f, PRAD - 0.3f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        c.restoreToCount(layer);
    }

    private void folder(Canvas c) {
        text(c, folderLabel, 20, 54, 7f, onPanel, I18n.medium(), Paint.Align.LEFT);
        int n = 0;
        for (float y : new float[]{72, 95}) for (float x : new float[]{29, 50, 71}) icon(c, x, y, 7f, n++);
    }

    private static final float ST = 92f, SRAD = 10f;
    private static final String[] PEOPLE = {"S", "A", "M", "O"};

    private void sheet(Canvas c, int which, float a) {
        if (a <= 0.004f) return;
        int layer = c.saveLayerAlpha(0, ST - 4, SCREEN_W, SCREEN_H, Math.round(255 * Math.min(1f, a)));
        int clip = c.save();
        box2.set(0, ST, SCREEN_W, SCREEN_H + SRAD);
        path.reset();
        path.addRoundRect(box2, SRAD, SRAD, Path.Direction.CW);
        c.clipPath(path);
        if (which == SurfaceStyle.FROSTED && !superDark && soft != null) {
            box.set(0, 0, SCREEN_W, SCREEN_H);
            c.drawBitmap(soft, null, box, paint);
        }
        fill(c, sheet[which], 0, ST, SCREEN_W, SCREEN_H, 0);
        c.restoreToCount(clip);
        if (which != SurfaceStyle.OFF) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.6f);
            paint.setColor(superDark ? 0x2effffff : rim);
            box2.set(0.3f, ST + 0.3f, SCREEN_W - 0.3f, SCREEN_H + SRAD);
            c.drawRoundRect(box2, SRAD - 0.3f, SRAD - 0.3f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        c.restoreToCount(layer);
    }

    private void sheetContent(Canvas c) {
        fill(c, muted, 44, ST + 3.5f, 56, ST + 5f, 0.75f);
        fill(c, look.iconBg, 8, ST + 10, 22, ST + 24, 3);
        fill(c, onPanel, 26, ST + 14, 62, ST + 16.6f, 1.3f);
        fill(c, muted, 26, ST + 20, 46, ST + 22.2f, 1.1f);
        for (int i = 0; i < 4; i++) {
            float x = COLS[i], y = ST + 40;
            dot(c, look.accent, x, y, 6.6f);
            text(c, PEOPLE[i], x, y + 2.6f, 7f, dark || superDark ? 0xff1b1b1f : 0xffffffff, I18n.medium(), Paint.Align.CENTER);
            fill(c, muted, x - 6, y + 10, x + 6, y + 11.8f, 0.9f);
        }
        fill(c, (onPanel & 0xffffff) | 0x26000000, 8, ST + 59, 92, ST + 59.5f, 0);
        int n = 0;
        for (float y : new float[]{ST + 72, ST + 98}) for (float x : COLS) {
            icon(c, x, y, 6.6f, n++);
            fill(c, muted, x - 6, y + 10, x + 6, y + 11.8f, 0.9f);
        }
    }

    private void build(int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(w / SCREEN_W, h / SCREEN_H);
            FeedPreview.drawWallpaper(c, paint, false);
            int on = 0xffffffff;
            text(c, time, 8, 9.5f, 6.2f, on, Typeface.DEFAULT_BOLD, Paint.Align.LEFT);
            fill(c, on, 80, 5.4f, 89, 9.6f, 1.2f);
            dot(c, on, 76, 7.5f, 1.6f);
            dot(c, on, 72, 7.5f, 1.2f);
            if (!date.isEmpty()) text(c, date, 8, 26, 7.4f, on, I18n.medium(), Paint.Align.LEFT);
            int n = 0;
            for (float y : ROWS) for (float x : COLS) icon(c, x, y, 6.2f, n++);
            for (int i = 0; i < 4; i++) icon(c, COLS[i], 177, 6.2f, i + 5);
            fill(c, 0x59ffffff, 8, 188, 92, 200, 6);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.1f); paint.setColor(0xddffffff);
            c.drawCircle(15, 194, 2.2f, paint);
            paint.setStyle(Paint.Style.FILL);
            Bitmap cur = b;
            int cw = w, ch = h;
            while (cw > w / 16 && cw > 8) {
                cw = Math.max(2, cw / 2); ch = Math.max(2, ch / 2);
                Bitmap next = Bitmap.createScaledBitmap(cur, cw, ch, true);
                if (cur != b) cur.recycle();
                cur = next;
            }
            for (int up = 0; up < 2; up++) {
                Bitmap next = Bitmap.createScaledBitmap(cur, Math.max(2, cur.getWidth() * 2), Math.max(2, cur.getHeight() * 2), true);
                if (cur != b) cur.recycle();
                cur = next;
            }
            home = b; soft = cur; builtW = w;
        } catch (Throwable t) { home = null; soft = null; builtW = w; }
    }

    private void icon(Canvas c, float x, float y, float r, int i) {
        dot(c, look.iconBg, x, y, r);
        int fg = look.iconFg;
        float k = r / 6.2f;
        switch (i % 4) {
            case 0: dot(c, fg, x, y, 2.5f * k); break;
            case 1: fill(c, fg, x - 2.4f * k, y - 2.4f * k, x + 2.4f * k, y + 2.4f * k, 0.9f * k); break;
            case 2: fill(c, fg, x - 3f * k, y - 1.2f * k, x + 3f * k, y + 1.2f * k, 1.2f * k); break;
            default: paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.3f * k); paint.setColor(fg); c.drawCircle(x, y, 2.4f * k, paint); paint.setStyle(Paint.Style.FILL); break;
        }
    }

    private void frame(Canvas c, float left, float top, float w, float h) {
        paint.setShader(null);
        float rad = w * CORNER / SCREEN_W;
        float b = 3.2f * dp;
        box.set(left - b / 2f, top - b / 2f, left + w + b / 2f, top + h + b / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(b);
        paint.setColor(pageDark ? 0xff4a4b52 : 0xff202125);
        c.drawRoundRect(box, rad + b / 2f, rad + b / 2f, paint);
        paint.setStrokeWidth(Math.max(1f, 0.8f * dp));
        paint.setColor(pageDark ? 0x40ffffff : 0x26000000);
        box.inset(-b / 2f, -b / 2f);
        c.drawRoundRect(box, rad + b, rad + b, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff0b0b0c);
        c.drawCircle(left + w / 2f, top + w * 0.055f, Math.max(1.2f * dp, w * 0.022f), paint);
    }

    private void fill(Canvas c, int color, float l, float t, float r, float b, float rad) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
        box2.set(l, t, r, b);
        if (rad > 0) c.drawRoundRect(box2, rad, rad, paint); else c.drawRect(box2, paint);
    }

    private void dot(Canvas c, int color, float x, float y, float r) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
        c.drawCircle(x, y, r, paint);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Typeface face, Paint.Align align) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size); paint.setTypeface(face); paint.setTextAlign(align); paint.setColor(color);
        c.drawText(s, x, y, paint);
        paint.setTypeface(null); paint.setTextAlign(Paint.Align.LEFT);
    }
}
