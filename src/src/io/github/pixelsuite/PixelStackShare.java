package io.github.pixelsuite;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PixelStackShare {
    private PixelStackShare() { }

    static Map<String, String> config(Context c) {
        try {
            int id = c.getResources().getIdentifier("pixelstack_share", "raw", c.getPackageName());
            if (id == 0) return null;
            InputStream in = c.getResources().openRawResource(id);
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[1024]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            Map<String, String> m = new HashMap<String, String>();
            for (String line : new String(b.toByteArray(), "UTF-8").split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
            String e = m.get("endpoint");
            if (e == null || !e.startsWith("https://")) return null;
            return m;
        } catch (Throwable t) { return null; }
    }

    static Bitmap card(Context c, int score, int best, boolean newBest, List<float[]> tower, float towerWidth, int ink, int page, boolean dark, int blue, int light, String player) {
        int W = 1080, H = 1350;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = W / 2f;
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{0xFF0A1440, 0xFF14307E, 0xFF1846B8}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, p); p.setShader(null);
        java.util.Random rnd = new java.util.Random(score * 7919L + 17);
        RectF r = new RectF();
        for (int i = 0; i < 46; i++) {
            float s = 10 + rnd.nextFloat() * 26, x = rnd.nextFloat() * W, y = rnd.nextFloat() * H;
            p.setColor((rnd.nextBoolean() ? 0xFFFFFFFF : light) & 0x00FFFFFF | ((10 + rnd.nextInt(22)) << 24));
            r.set(x, y, x + s, y + s); cv.drawRoundRect(r, s * .22f, s * .22f, p);
        }
        float u = 30, g = 9, ly = 128;
        for (int k = 0; k < 4; k++) {
            int col = k % 2, row = k / 2;
            float l = cx - 170 - u - g / 2 + col * (u + g), t = ly - u - g / 2 + row * (u + g);
            r.set(l, t, l + u, t + u); p.setColor(col == row ? 0xFFFFFFFF : light); cv.drawRoundRect(r, 7, 7, p);
        }
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); p.setTextSize(64); p.setColor(0xFFFFFFFF);
        cv.drawText("Pixel Stack", cx - 120, ly + 22, p);
        float ty = 470;
        p.setShader(new android.graphics.RadialGradient(cx, ty, 420, new int[]{0x88FFD54F, 0x33FFB300, 0x00FFB300}, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, ty, 420, p); p.setShader(null);
        int save = cv.save();
        for (int i = 0; i < 14; i++) {
            android.graphics.Path ray = new android.graphics.Path();
            ray.moveTo(cx, ty); ray.lineTo(cx - 34, ty - 470); ray.lineTo(cx + 34, ty - 470); ray.close();
            p.setShader(new LinearGradient(cx, ty, cx, ty - 470, 0x44FFE082, 0x00FFE082, Shader.TileMode.CLAMP));
            cv.drawPath(ray, p); cv.rotate(360f / 14, cx, ty);
        }
        cv.restoreToCount(save); p.setShader(null);
        trophy(cv, p, cx, ty, light);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); p.setTextSize(230);
        p.setShadowLayer(24, 0, 10, 0x66000000); p.setColor(0xFFFFFFFF);
        cv.drawText(String.valueOf(score), cx, 965, p); p.clearShadowLayer();
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); p.setTextSize(50); p.setColor(0xFFFFFFFF);
        String who = player;
        while (p.measureText(who) > 820 && who.length() > 2) who = who.substring(0, who.length() - 2) + "…";
        cv.drawText(who, cx, 752, p);
        float below = 1032;
        if (newBest) {
            String nb = I18n.t("New best!");
            p.setTextSize(46); float tw = p.measureText(nb);
            r.set(cx - tw / 2 - 40, below - 46, cx + tw / 2 + 40, below + 22);
            p.setShader(new LinearGradient(r.left, 0, r.right, 0, new int[]{0xFFFFC531, 0xFFFFE58A, 0xFFE09A12}, null, Shader.TileMode.CLAMP));
            cv.drawRoundRect(r, 34, 34, p); p.setShader(null);
            p.setColor(0xFF3A2400); cv.drawText(nb, cx, below + 2, p);
            below += 80;
        }
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); p.setTextSize(42); p.setColor(0xCCFFFFFF);
        cv.drawText(I18n.t("Best") + "  " + best, cx, below + (newBest ? 0 : 20), p);
        float areaTop = 1150, areaBottom = 1268, bh = 16;
        int n = tower.size();
        float scale = Math.min(1f, (areaBottom - areaTop) / Math.max(1f, n * bh));
        float wscale = Math.min(1f, 520f / Math.max(1f, towerWidth)) * .55f;
        for (int i = 0; i < n; i++) {
            float[] b = tower.get(i);
            float y = areaBottom - (i + 1) * bh * scale;
            r.set(cx + b[0] * wscale, y + 1.5f * scale, cx + b[1] * wscale, y + bh * scale - 1.5f * scale);
            p.setColor(((int) b[2]) & 0x00FFFFFF | 0xE6000000); float rad = Math.min(6, r.height() / 2);
            cv.drawRoundRect(r, rad, rad, p);
        }
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); p.setTextSize(32); p.setColor(0x99FFFFFF);
        cv.drawText("Pixel Suite", cx, 1310, p);
        return bmp;
    }

    private static void trophy(Canvas cv, Paint p, float cx, float cy, int light) {
        int[] gold = {0xFFB8730A, 0xFFFFD86B, 0xFFFFF3C4, 0xFFFFC531, 0xFFC98410};
        float[] at = {0, .28f, .42f, .7f, 1};
        float top = cy - 200, rim = 170;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(30); p.setStrokeCap(Paint.Cap.ROUND);
        p.setShader(new LinearGradient(cx - 290, 0, cx + 290, 0, gold, at, Shader.TileMode.CLAMP));
        RectF h = new RectF(cx - 262, top + 20, cx - 120, top + 190); cv.drawArc(h, 90, 180, false, p);
        h.set(cx + 120, top + 20, cx + 262, top + 190); cv.drawArc(h, -90, 180, false, p);
        p.setStyle(Paint.Style.FILL);
        android.graphics.Path bowl = new android.graphics.Path();
        bowl.moveTo(cx - rim, top);
        bowl.lineTo(cx + rim, top);
        bowl.cubicTo(cx + rim, top + 170, cx + 90, top + 255, cx + 26, top + 270);
        bowl.lineTo(cx - 26, top + 270);
        bowl.cubicTo(cx - 90, top + 255, cx - rim, top + 170, cx - rim, top);
        bowl.close();
        p.setShader(new LinearGradient(cx - rim, 0, cx + rim, 0, gold, at, Shader.TileMode.CLAMP));
        p.setShadowLayer(30, 0, 14, 0x55000000); cv.drawPath(bowl, p); p.clearShadowLayer();
        RectF rr = new RectF(cx - rim - 14, top - 22, cx + rim + 14, top + 14); cv.drawRoundRect(rr, 18, 18, p);
        RectF stem = new RectF(cx - 24, top + 262, cx + 24, top + 330); cv.drawRect(stem, p);
        RectF knot = new RectF(cx - 52, top + 300, cx + 52, top + 332); cv.drawRoundRect(knot, 16, 16, p);
        RectF base1 = new RectF(cx - 110, top + 330, cx + 110, top + 372); cv.drawRoundRect(base1, 12, 12, p);
        p.setShader(new LinearGradient(cx - 150, 0, cx + 150, 0, new int[]{0xFF0F1E55, 0xFF2A4FB0, 0xFF0F1E55}, null, Shader.TileMode.CLAMP));
        RectF base2 = new RectF(cx - 150, top + 368, cx + 150, top + 420); cv.drawRoundRect(base2, 14, 14, p);
        p.setShader(null);
        android.graphics.Path shine = new android.graphics.Path();
        shine.moveTo(cx - rim + 34, top + 18); shine.cubicTo(cx - rim + 30, top + 120, cx - 80, top + 205, cx - 40, top + 238);
        shine.lineTo(cx - 62, top + 236); shine.cubicTo(cx - 120, top + 200, cx - rim + 10, top + 120, cx - rim + 14, top + 18); shine.close();
        p.setColor(0x66FFFFFF); cv.drawPath(shine, p);
        float u = 34, g = 10, my = top + 112;
        RectF sq = new RectF();
        for (int k = 0; k < 4; k++) {
            int col = k % 2, row = k / 2;
            float l = cx - u - g / 2 + col * (u + g), t = my - u - g / 2 + row * (u + g);
            sq.set(l, t, l + u, t + u); p.setColor(col == row ? 0xFF1846B8 : 0xCC1846B8 & 0x00FFFFFF | 0x99000000);
            cv.drawRoundRect(sq, 8, 8, p);
        }
        p.setColor(0xFFFFFFFF);
        star(cv, p, cx + 205, top - 40, 22); star(cv, p, cx - 230, top + 250, 14); star(cv, p, cx + 150, top + 330, 12);
    }
    private static void star(Canvas cv, Paint p, float x, float y, float s) {
        android.graphics.Path st = new android.graphics.Path();
        st.moveTo(x, y - s); st.quadTo(x, y, x + s, y); st.quadTo(x, y, x, y + s); st.quadTo(x, y, x - s, y); st.quadTo(x, y, x, y - s); st.close();
        cv.drawPath(st, p);
    }

    static boolean post(Map<String, String> cfg, Bitmap card, int score, String player) {
        HttpURLConnection h = null;
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            card.compress(Bitmap.CompressFormat.PNG, 100, png);
            String boundary = "PixelStack" + System.nanoTime();
            h = (HttpURLConnection) new URL(cfg.get("endpoint")).openConnection();
            h.setConnectTimeout(15000); h.setReadTimeout(20000); h.setDoOutput(true); h.setRequestMethod("POST");
            h.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            OutputStream o = h.getOutputStream();
            field(o, boundary, "score", String.valueOf(score));
            field(o, boundary, "player", player);
            o.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"pixel-stack.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes("UTF-8"));
            o.write(png.toByteArray());
            o.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
            o.flush(); o.close();
            int code = h.getResponseCode();
            InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[1024]; int n;
            if (in != null) { while ((n = in.read(buf)) > 0) b.write(buf, 0, n); in.close(); }
            String reply = new String(b.toByteArray(), "UTF-8");
            boolean ok = code == 200 && reply.replace(" ", "").contains("\"ok\":true");
            if (ok) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"link\"\\s*:\\s*\"(https://[^\"\\\\]+)\"").matcher(reply);
                if (m.find()) cfg.put("link", m.group(1));
            } else android.util.Log.w("PixelStack", "share refused: " + code);
            return ok;
        } catch (Throwable t) {
            android.util.Log.w("PixelStack", "share failed: " + t.getClass().getSimpleName());
            return false;
        } finally { if (h != null) h.disconnect(); }
    }

    private static void field(OutputStream o, String boundary, String name, String value) throws Exception {
        o.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes("UTF-8"));
    }

    static String player(Context c) {
        try {
            if (PatronAccess.loggedIn(c)) {
                String n = PatronAccess.prefs(c).getString("name", null);
                if (n != null) { n = n.replace('\n', ' ').trim(); if (n.length() > 40) n = n.substring(0, 40).trim() + "…"; if (!n.isEmpty()) return n; }
            }
            String typed = c.getSharedPreferences("pixelstack", Context.MODE_PRIVATE).getString("name", "");
            if (typed != null) { typed = noEmoji(typed).trim(); if (!typed.isEmpty()) return typed; }
        } catch (Throwable ignored) { }
        return I18n.t("Unknown");
    }
    static String noEmoji(CharSequence in) {
        StringBuilder b = new StringBuilder();
        String s = in.toString();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i); i += Character.charCount(cp);
            if (emoji(cp)) continue;
            b.appendCodePoint(cp);
        }
        return b.toString();
    }
    private static boolean emoji(int cp) {
        if (cp == 0x200D || (cp >= 0xFE00 && cp <= 0xFE0F) || cp == 0x20E3 || (cp >= 0xE0020 && cp <= 0xE007F)) return true;
        if (cp >= 0x1F000 && cp <= 0x1FAFF) return true;
        if (cp >= 0x2600 && cp <= 0x27BF) return true;
        if (cp >= 0x2B00 && cp <= 0x2BFF) return true;
        if (cp >= 0x2190 && cp <= 0x21FF) return true;
        if (cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 || (cp >= 0x2300 && cp <= 0x23FF) || (cp >= 0x25A0 && cp <= 0x25FF) || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299) return true;
        int t = Character.getType(cp);
        return t == Character.OTHER_SYMBOL || t == Character.SURROGATE || t == Character.PRIVATE_USE;
    }

    static boolean signedIn(Context c) { try { return PatronAccess.loggedIn(c); } catch (Throwable t) { return false; } }

    static void openSection(Context c, Map<String, String> cfg) {
        String link = cfg.get("link");
        if (link == null || link.isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            for (String pkg : new String[]{"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"}) {
                try { c.getPackageManager().getPackageInfo(pkg, 0); i.setPackage(pkg); break; } catch (Throwable ignored) { }
            }
            c.startActivity(i);
        } catch (Throwable t) {
            try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) { }
        }
    }
}
