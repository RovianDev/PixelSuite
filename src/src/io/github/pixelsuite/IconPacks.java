package io.github.pixelsuite;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class IconPacks {
    private IconPacks() {}

    static final String KEY = "pixelsuite_icon_pack";

    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES", "org.adw.launcher.icons.ACTION_PICK_ICON", "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME", "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK", "com.dlto.atom.launcher.THEME", "net.oneplus.launcher.icons.ACTION_PICK_ICON",
            "app.lawnchair.icons.THEMED_ICON", "ch.deletescape.lawnchair.ICONPACK", "com.lge.launcher2.THEME",
            "com.gridappsinc.launcher.theme.apkv2", "com.anddoes.launcher.icons.ACTION_PICK_ICON",
    };
    private static final String[] CATEGORIES = { "com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK", "com.teslacoilsw.launcher.THEME" };

    static final class Info {
        final String pkg, label;
        Info(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    static List<Info> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Info> found = new LinkedHashMap<String, Info>();
        List<ResolveInfo> all = new ArrayList<ResolveInfo>();
        for (String a : ACTIONS) {
            try { all.addAll(pm.queryIntentActivities(new Intent(a), PackageManager.GET_META_DATA)); } catch (Throwable ignored) { }
        }
        for (String cat : CATEGORIES) {
            try { all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), PackageManager.GET_META_DATA)); } catch (Throwable ignored) { }
        }
        for (ResolveInfo r : all) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (found.containsKey(pkg) || pkg.equals(c.getPackageName())) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (!ai.enabled) continue;
                found.put(pkg, new Info(pkg, String.valueOf(pm.getApplicationLabel(ai))));
            } catch (Throwable ignored) { }
        }
        List<Info> out = new ArrayList<Info>(found.values());
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    static final class Pack {
        final String pkg;
        final Resources res;
        final Map<String, String> components = new HashMap<String, String>();
        final Map<String, String> packages = new HashMap<String, String>();
        final List<String> backs = new ArrayList<String>();
        String mask, upon;
        float scale = 1f;
        final List<String> all = new ArrayList<String>();
        final long version;

        Pack(Context c, String pkg) throws Exception {
            this.pkg = pkg;
            PackageManager pm = c.getPackageManager();
            res = pm.getResourcesForApplication(pkg);
            long v;
            try { v = pm.getPackageInfo(pkg, 0).lastUpdateTime; } catch (Throwable t) { v = 0; }
            version = v;
            Set<String> order = new LinkedHashSet<String>();
            XmlPullParser x = parser("appfilter");
            if (x != null) {
                for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
                    if (e != XmlPullParser.START_TAG) continue;
                    String tag = x.getName();
                    if ("item".equals(tag)) {
                        String comp = x.getAttributeValue(null, "component"), draw = x.getAttributeValue(null, "drawable");
                        if (comp == null || draw == null || draw.isEmpty()) continue;
                        String flat = flatten(comp);
                        if (flat == null) continue;
                        if (!components.containsKey(flat)) components.put(flat, draw);
                        String p = flat.substring(0, flat.indexOf('/'));
                        if (!packages.containsKey(p)) packages.put(p, draw);
                        order.add(draw);
                    } else if ("iconback".equals(tag)) {
                        for (int i = 0; i < x.getAttributeCount(); i++) if (x.getAttributeName(i).startsWith("img")) backs.add(x.getAttributeValue(i));
                    } else if ("iconmask".equals(tag)) {
                        mask = x.getAttributeValue(null, "img1");
                    } else if ("iconupon".equals(tag)) {
                        upon = x.getAttributeValue(null, "img1");
                    } else if ("scale".equals(tag)) {
                        try { scale = Float.parseFloat(x.getAttributeValue(null, "factor")); } catch (Throwable ignored) { }
                        if (!(scale > 0.1f && scale <= 2f)) scale = 1f;
                    }
                }
            }
            Set<String> listed = new LinkedHashSet<String>();
            XmlPullParser d = parser("drawable");
            if (d != null) {
                for (int e = d.getEventType(); e != XmlPullParser.END_DOCUMENT; e = d.next()) {
                    if (e == XmlPullParser.START_TAG && "item".equals(d.getName())) {
                        String draw = d.getAttributeValue(null, "drawable");
                        if (draw != null && !draw.isEmpty()) listed.add(draw);
                    }
                }
            }
            all.addAll(listed.isEmpty() ? order : listed);
            if (!listed.isEmpty()) for (String o : order) if (!listed.contains(o)) all.add(o);
        }

        private XmlPullParser parser(String name) {
            try {
                int id = res.getIdentifier(name, "xml", pkg);
                if (id != 0) return res.getXml(id);
            } catch (Throwable ignored) { }
            try {
                InputStream in = res.getAssets().open(name + ".xml");
                XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
                p.setInput(in, "UTF-8");
                return p;
            } catch (Throwable ignored) { }
            return null;
        }

        int id(String name) {
            if (name == null || name.isEmpty()) return 0;
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id == 0) id = res.getIdentifier(name, "mipmap", pkg);
            return id;
        }

        boolean has(String name) { return id(name) != 0; }

        Drawable drawable(String name) {
            int id = id(name);
            if (id == 0) return null;
            try { return res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null); }
            catch (Throwable t) {
                try { return res.getDrawable(id, null); } catch (Throwable u) { return null; }
            }
        }

        String nameFor(ComponentName cn) {
            if (cn == null) return null;
            String flat = (cn.getPackageName() + "/" + cn.getClassName()).toLowerCase(Locale.ROOT);
            String n = components.get(flat);
            if (n != null && has(n)) return n;
            n = packages.get(cn.getPackageName().toLowerCase(Locale.ROOT));
            return n != null && has(n) ? n : null;
        }

        List<String> suggestions(ComponentName cn, String label) {
            Set<String> out = new LinkedHashSet<String>();
            if (cn != null) {
                String flat = (cn.getPackageName() + "/" + cn.getClassName()).toLowerCase(Locale.ROOT);
                String own = components.get(flat);
                if (own != null && has(own)) out.add(own);
                String pkgLower = cn.getPackageName().toLowerCase(Locale.ROOT);
                for (Map.Entry<String, String> e : components.entrySet()) {
                    if (e.getKey().startsWith(pkgLower + "/") && has(e.getValue())) out.add(e.getValue());
                }
            }
            String word = label == null ? "" : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (word.length() >= 3) {
                for (String n : all) {
                    if (out.size() >= 12) break;
                    if (n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains(word)) out.add(n);
                }
            }
            return new ArrayList<String>(out);
        }

        boolean styles() { return !backs.isEmpty(); }

        Bitmap styled(Bitmap own, int size, int seed) {
            if (backs.isEmpty() || own == null) return null;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            Bitmap layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas lc = new Canvas(layer);
            int inner = Math.round(size * scale), at = (size - inner) / 2;
            lc.drawBitmap(own, null, new Rect(at, at, at + inner, at + inner), p);
            Drawable m = drawable(mask);
            if (m != null) {
                Bitmap mb = render(m, size);
                Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                lc.drawBitmap(mb, 0, 0, cut);
                mb.recycle();
            }
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Drawable back = drawable(backs.get(Math.abs(seed) % backs.size()));
            if (back != null) { back.setBounds(0, 0, size, size); back.draw(c); }
            c.drawBitmap(layer, 0, 0, null);
            layer.recycle();
            Drawable u = drawable(upon);
            if (u != null) { u.setBounds(0, 0, size, size); u.draw(c); }
            return out;
        }
    }

    private static final Map<String, Pack> PACKS = new HashMap<String, Pack>();
    private static final Map<String, Long> CHECKED = new HashMap<String, Long>();

    static Pack get(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (PACKS) {
            Long at = CHECKED.get(pkg);
            Pack cached = PACKS.get(pkg);
            if (cached != null && at != null && now - at < 5000) return cached;
        }
        long v;
        try { v = c.getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime; } catch (Throwable t) { return null; }
        synchronized (PACKS) {
            Pack p = PACKS.get(pkg);
            if (p != null && p.version == v) { CHECKED.put(pkg, now); return p; }
        }
        try {
            Pack p = new Pack(c, pkg);
            synchronized (PACKS) { PACKS.put(pkg, p); CHECKED.put(pkg, now); }
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    static void forget(String pkg) { synchronized (PACKS) { PACKS.remove(pkg); CHECKED.remove(pkg); } }

    static String flatten(String comp) {
        String s = comp.trim();
        int open = s.indexOf('{'), close = s.lastIndexOf('}');
        if (open >= 0 && close > open) s = s.substring(open + 1, close);
        int slash = s.indexOf('/');
        if (slash <= 0 || slash == s.length() - 1) return null;
        String p = s.substring(0, slash), cls = s.substring(slash + 1);
        if (cls.startsWith(".")) cls = p + cls;
        return (p + "/" + cls).toLowerCase(Locale.ROOT);
    }

    static Bitmap render(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return b;
    }

    static Bitmap fill(Bitmap in) {
        if (in == null) return null;
        int w = in.getWidth(), h = in.getHeight();
        int[] px = new int[w * h];
        in.getPixels(px, 0, w, 0, 0, w, h);
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if ((px[row + x] >>> 24) > 24) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < 0) return in;
        if (glass(px, w, left, top, right, bottom)) return in;
        float cx = (left + right + 1) / 2f, cy = (top + bottom + 1) / 2f;
        float side = Math.max(right - left + 1, bottom - top + 1);
        int background = edge(px, w, h, left, top, right, bottom);
        boolean plateless = background == 0;
        side *= plateless ? 1.3f : 0.96f;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        android.graphics.RectF src = new android.graphics.RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRectToRect(src, new android.graphics.RectF(0, 0, w, h), android.graphics.Matrix.ScaleToFit.FILL);
        c.drawBitmap(in, m, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        in.recycle();
        int[] o = new int[w * h];
        out.getPixels(o, 0, w, 0, 0, w, h);
        if (!plateless && !extendInside(o, w, h)) {
            for (int i = 0; i < o.length; i++) o[i] = over(o[i], background);
        }
        out.setPixels(o, 0, w, 0, 0, w, h);
        return out;
    }

    private static boolean glass(int[] px, int w, int l, int t, int r, int b) {
        long seen = 0, soft = 0;
        int step = Math.max(1, Math.min(r - l + 1, b - t + 1) / 96);
        for (int y = t; y <= b; y += step) {
            int row = y * w;
            for (int x = l; x <= r; x += step) {
                int a = px[row + x] >>> 24;
                if (a <= 24) continue;
                seen++;
                if (a < 236) soft++;
            }
        }
        return seen > 0 && soft > seen * 0.22f;
    }

    static boolean opaqueSquare(Bitmap b) {
        if (b == null) return true;
        int w = b.getWidth(), h = b.getHeight(), m = Math.max(1, w / 64);
        int[][] p = {{m, m}, {w - 1 - m, m}, {m, h - 1 - m}, {w - 1 - m, h - 1 - m}, {w / 2, m}, {w / 2, h - 1 - m}, {m, h / 2}, {w - 1 - m, h / 2}};
        int solid = 0;
        for (int[] q : p) if ((b.getPixel(q[0], q[1]) >>> 24) >= 250) solid++;
        return solid >= 7;
    }

    private static boolean extendInside(int[] o, int w, int h) {
        int n = w * h;
        boolean[] solid = new boolean[n];
        int count = 0;
        for (int i = 0; i < n; i++) if ((o[i] >>> 24) >= 250) { solid[i] = true; count++; }
        if (count < n * 0.6f) return false;
        int erode = Math.max(3, w / 48);
        for (int pass = 0; pass < erode; pass++) {
            boolean[] next = new boolean[n];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = y * w + x;
                next[i] = solid[i] && x > 0 && y > 0 && x < w - 1 && y < h - 1 && solid[i - 1] && solid[i + 1] && solid[i - w] && solid[i + w];
            }
            solid = next;
        }
        count = 0;
        for (int i = 0; i < n; i++) if (solid[i]) count++;
        if (count < n * 0.45f) return false;
        int[] ring = new int[n], nextRing = new int[n];
        int ringSize = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (solid[i]) continue;
            if ((x > 0 && solid[i - 1]) || (x < w - 1 && solid[i + 1]) || (y > 0 && solid[i - w]) || (y < h - 1 && solid[i + w])) ring[ringSize++] = i;
        }
        boolean[] queued = new boolean[n];
        for (int k = 0; k < ringSize; k++) queued[ring[k]] = true;
        while (ringSize > 0) {
            int[] colour = new int[ringSize];
            for (int k = 0; k < ringSize; k++) {
                int i = ring[k], x = i % w, y = i / w;
                long r = 0, g = 0, b = 0, cnt = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    int j = yy * w + xx;
                    if (!solid[j]) continue;
                    int v = o[j];
                    r += (v >> 16) & 255; g += (v >> 8) & 255; b += v & 255; cnt++;
                }
                colour[k] = cnt == 0 ? o[i] | 0xff000000 : 0xff000000 | (int) (r / cnt) << 16 | (int) (g / cnt) << 8 | (int) (b / cnt);
            }
            int nextSize = 0;
            for (int k = 0; k < ringSize; k++) { int i = ring[k]; o[i] = colour[k]; solid[i] = true; }
            for (int k = 0; k < ringSize; k++) {
                int i = ring[k], x = i % w, y = i / w;
                int[] around = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                for (int j : around) if (j >= 0 && !solid[j] && !queued[j]) { queued[j] = true; nextRing[nextSize++] = j; }
            }
            int[] t = ring; ring = nextRing; nextRing = t; ringSize = nextSize;
        }
        return true;
    }

    private static int over(int top, int bottom) {
        int a = top >>> 24;
        if (a == 255) return top;
        int r = (((top >> 16) & 255) * a + ((bottom >> 16) & 255) * (255 - a)) / 255;
        int g = (((top >> 8) & 255) * a + ((bottom >> 8) & 255) * (255 - a)) / 255;
        int b = ((top & 255) * a + (bottom & 255) * (255 - a)) / 255;
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int edge(int[] px, int w, int h, int l, int t, int r, int b) {
        long sr = 0, sg = 0, sb = 0, n = 0;
        int inset = Math.max(2, (r - l) / 12);
        int x0 = Math.min(r, l + inset), x1 = Math.max(l, r - inset), y0 = Math.min(b, t + inset), y1 = Math.max(t, b - inset);
        int[][] pts = new int[64][];
        for (int i = 0; i < 16; i++) {
            int fx = x0 + (x1 - x0) * i / 15, fy = y0 + (y1 - y0) * i / 15;
            pts[i] = new int[]{x0, fy}; pts[16 + i] = new int[]{x1, fy};
            pts[32 + i] = new int[]{fx, y0}; pts[48 + i] = new int[]{fx, y1};
        }
        for (int[] p : pts) {
            int v = px[p[1] * w + p[0]];
            if ((v >>> 24) < 200) continue;
            sr += (v >> 16) & 255; sg += (v >> 8) & 255; sb += v & 255; n++;
        }
        if (n < 8) return 0;
        return 0xff000000 | (int) (sr / n) << 16 | (int) (sg / n) << 8 | (int) (sb / n);
    }

    static String title(String name) {
        return name.replace('_', ' ').replace('-', ' ').trim();
    }

    static List<Drawable> preview(Context c, String pkg, int n) {
        List<Drawable> out = new ArrayList<Drawable>();
        Pack p = get(c, pkg);
        if (p == null) return out;
        String[] wanted = {
                "com.google.android.dialer", "com.google.android.apps.messaging", "com.android.chrome", "com.google.android.GoogleCamera",
                "com.google.android.apps.photos", "com.google.android.gm", "com.google.android.apps.maps", "com.google.android.youtube",
        };
        for (String w : wanted) {
            if (out.size() >= n) break;
            String name = p.packages.get(w.toLowerCase(Locale.ROOT));
            Drawable d = name == null ? null : p.drawable(name);
            if (d != null) out.add(d);
        }
        for (String name : p.all) {
            if (out.size() >= n) break;
            Drawable d = p.drawable(name);
            if (d != null) out.add(d);
        }
        return out;
    }
}
