package io.github.pixelsuite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class ChromeTabTransition {
    private ChromeTabTransition() {}

    static final TimeInterpolator EMPHASIZED;
    static final TimeInterpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final TimeInterpolator ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    static final TimeInterpolator SPRING = new TimeInterpolator() {
        public float getInterpolation(float t) { return t >= 1f ? 1f : 1f - (float) (Math.exp(-6.5 * t) * Math.cos(10.5 * t)); }
    };
    static {
        Path p = new Path();
        p.moveTo(0f, 0f);
        p.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        p.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        EMPHASIZED = new PathInterpolator(p);
    }

    private static final int OPEN = 1, CLOSE = 2, NEW = 3, COVER = 4;
    private static final long OPEN_MS = 420, CLOSE_MS = 400, NEW_MS = 380, FADE_OUT_MS = 130;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Object, Flight> FLIGHTS = new WeakHashMap<Object, Flight>();
    private static Flight sCurrent;
    private static int sActive;
    private static boolean sInstalled, sBypass;
    private static final RectF sPlus = new RectF();
    private static long sPlusAt, sNewFlightAt;

    static boolean active() { return sActive > 0; }

    static boolean covering() { return sCurrent != null && sCurrent.added && !sCurrent.finished; }

    private static int sGridEstimate, sGridMeasured, sNtpColor;

    static boolean close(int a, int b, int tolerance) {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b)) <= tolerance;
    }

    static void themeChanged() {
        sNtpColor = 0; sGridMeasured = 0; sGridEstimate = 0;
        sShot = null; sTapShot = null; sShotTitle = null;
    }

    static int gridColor(View hubOrRoot) {
        int estimate = exactGrid(hubOrRoot);
        if (sGridMeasured != 0 && sGridEstimate != 0 && close(estimate, sGridEstimate, 24)) return sGridMeasured;
        return estimate;
    }

    private static int exactGrid(View root) {
        if (root == null) return 0xFFFBF8FD;
        int c = solid(find(root, "hub_toolbar"));
        if (c != 0) return c;
        View hub = "hub_main_container".equals(name(root)) ? root : find(root, "hub_main_container");
        c = solid(hub);
        if (c != 0) return c;
        return gridSurface(hub != null ? hub : root);
    }

    static int solid(View v) {
        if (v == null) return 0;
        Drawable d = v.getBackground();
        int c = 0;
        if (d instanceof ColorDrawable) c = ((ColorDrawable) d).getColor();
        else if (d instanceof android.graphics.drawable.GradientDrawable) {
            try {
                Object list = android.graphics.drawable.GradientDrawable.class.getMethod("getColor").invoke(d);
                if (list instanceof android.content.res.ColorStateList) c = ((android.content.res.ColorStateList) list).getDefaultColor();
            } catch (Throwable ignored) { }
        }
        return Color.alpha(c) == 255 ? c : 0;
    }

    private static int paintColor(View v) {
        try {
            for (Class<?> k = v.getClass(); k != null && k != ImageView.class && k != View.class; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (f.getType() != Paint.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Paint p = (Paint) f.get(v);
                    if (p != null && Color.alpha(p.getColor()) == 255) return p.getColor();
                }
            }
        } catch (Throwable ignored) { }
        return 0;
    }


    interface Got { void got(int color); }

    static android.app.Activity activityOf(View v) {
        android.content.Context ctx = v.getContext();
        for (int i = 0; i < 10 && ctx != null; i++) {
            if (ctx instanceof android.app.Activity) return (android.app.Activity) ctx;
            if (!(ctx instanceof android.content.ContextWrapper)) break;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    private static View pageSurface(View root) {
        View holder = find(root, "compositor_view_holder");
        View best = null;
        ArrayList<View> all = new ArrayList<View>();
        surfaces(holder != null ? holder : root, all);
        for (View v : all) {
            if (!v.isShown() || v.getWidth() < 100 || v.getHeight() < 100) continue;
            if (best == null || (long) v.getWidth() * v.getHeight() > (long) best.getWidth() * best.getHeight()) best = v;
        }
        return best;
    }

    private static void surfaces(View v, ArrayList<View> out) {
        if (v instanceof android.view.SurfaceView) { out.add(v); return; }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) surfaces(g.getChildAt(i), out); }
    }

    private static void pixelCopy(View v, Rect screen, final Bitmap dst, final Runnable ok) {
        try {
            android.app.Activity a = activityOf(v);
            if (a == null || screen.width() <= 0 || screen.height() <= 0) return;
            View decor = a.getWindow().getDecorView();
            int[] origin = new int[2];
            decor.getLocationOnScreen(origin);
            Rect r = new Rect(screen);
            r.offset(-origin[0], -origin[1]);
            if (r.left < 0 || r.top < 0 || r.right > decor.getWidth() || r.bottom > decor.getHeight()) return;
            Class<?> pc = Class.forName("android.view.PixelCopy");
            Class<?> lis = Class.forName("android.view.PixelCopy$OnPixelCopyFinishedListener");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(lis.getClassLoader(), new Class<?>[]{lis}, new java.lang.reflect.InvocationHandler() {
                public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args) {
                    String n = m.getName();
                    if ("onPixelCopyFinished".equals(n)) {
                        try { if (args != null && args.length == 1 && Integer.valueOf(0).equals(args[0])) ok.run(); } catch (Throwable ignored) { }
                        return null;
                    }
                    if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                    if ("equals".equals(n)) return args != null && args.length == 1 && proxy == args[0];
                    if ("toString".equals(n)) return "PixelSuite.pixelCopy";
                    return null;
                }
            });
            pc.getMethod("request", android.view.Window.class, Rect.class, Bitmap.class, lis, Handler.class).invoke(null, a.getWindow(), r, dst, listener, MAIN);
        } catch (Throwable ignored) { }
    }

    static void readBack(View v, Rect screen, final Got cb) {
        if (screen.width() <= 0 || screen.height() <= 0) return;
        final Bitmap b = Bitmap.createBitmap(screen.width(), screen.height(), Bitmap.Config.ARGB_8888);
        pixelCopy(v, screen, b, new Runnable() { public void run() { int c = dominant(b); if (c != 0) cb.got(c); } });
    }

    private static Bitmap sShot;
    private static long sShotAt;
    private static final int[] sShotOrigin = new int[2];
    private static final Rect sShotPage = new Rect();
    private static int sShotTop;
    private static final Rect sShotHole = new Rect();

    private static void pageHole(View root, View sv, int[] at, Rect out) {
        out.set(at[0], at[1], at[0] + sv.getWidth(), at[1] + sv.getHeight());
        View bar = find(root, "control_container");
        if (bar != null && bar.isShown() && bar.getHeight() > 0) {
            int[] b = new int[2];
            bar.getLocationOnScreen(b);
            if (b[1] + bar.getHeight() < out.bottom) out.top = Math.max(out.top, b[1] + bar.getHeight());
        }
        int total = ChromeLayout.barTotal();
        int[] r = new int[2];
        root.getLocationOnScreen(r);
        if (total > 0) out.bottom = Math.min(out.bottom, r[1] + root.getHeight() - total);
        if (out.bottom <= out.top) out.setEmpty();
    }

    private static boolean place(Bitmap win, Bitmap page, Rect holeOnScreen, int[] origin) {
        Rect hole = new Rect(holeOnScreen);
        hole.offset(-origin[0], -origin[1]);
        if (!hole.intersect(0, 0, win.getWidth(), win.getHeight())) return false;
        int bw = page.getWidth(), bh = page.getHeight();
        if (bw < hole.width() * 0.9f || bh < hole.height() * 0.5f) return false;
        Canvas cv = new Canvas(win);
        cv.save();
        cv.clipRect(hole);
        float sc = (float) hole.width() / bw;
        Rect dst = new Rect(hole.left, hole.top, hole.left + Math.round(bw * sc), hole.top + Math.round(bh * sc));
        cv.drawBitmap(page, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
        cv.restore();
        return true;
    }

    static String captureMode() {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class).invoke(null, "debug.pixelsuite.capture", "off");
            return String.valueOf(v);
        } catch (Throwable t) { return "off"; }
    }

    private static boolean hasContent(Bitmap b, Rect r) {
        try {
            Rect q = new Rect(r);
            if (!q.intersect(0, 0, b.getWidth(), b.getHeight()) || q.width() < 8 || q.height() < 8) return false;
            int first = 0, differing = 0, opaque = 0;
            for (int j = 0; j < 16; j++) for (int i = 0; i < 10; i++) {
                int c = b.getPixel(q.left + (q.width() - 1) * i / 9, q.top + (q.height() - 1) * j / 15);
                if (Color.alpha(c) < 255) continue;
                if (opaque++ == 0) first = c;
                else if (!close(c, first, 12)) differing++;
            }
            return opaque > 80 && differing > 6;
        } catch (Throwable t) {
            return false;
        }
    }

    static void captureScreen(View v) { captureScreen(v, null); }

    static void openSwitcher(View from, final View tabs) {
        if (tabs == null) return;
        if (!on(from)) { tabs.performClick(); return; }
        long now = SystemClock.uptimeMillis();
        if (now - sTapAt < 700 || sCover != null || (sCurrent != null && !sCurrent.finished)) {
            return;
        }
        sTapAt = now;
        if (!"off".equals(captureMode())) ChromeCapture.tapped(from);
        final boolean[] went = {false};
        final Runnable click = new Runnable() {
            public void run() {
                if (went[0]) return;
                went[0] = true;
                try { tabs.performClick(); } catch (Throwable ignored) { }
            }
        };
        final View anchor = from;
        captureScreen(from, new Runnable() { public void run() { if (!went[0]) { sTapShot = sShot; cover(anchor); } click.run(); } });
        MAIN.postDelayed(click, 250);
    }

    private static long sTapAt;
    private static Bitmap sTapShot;

    private static Stage sCover;
    private static long sCoverAt;

    private static void cover(View v) {
        try {
            removeCover();
            android.app.Activity a = activityOf(v);
            View cv = a == null ? null : a.findViewById(android.R.id.content);
            if (!(cv instanceof FrameLayout) || sShot == null) return;
            final FrameLayout content = (FrameLayout) cv;
            final Stage c = new Stage(content);
            c.kind = COVER;
            c.source = sShot;
            c.hole.set(sShotHole);
            c.toolbarShotAt[0] = sShotOrigin[0];
            c.toolbarShotAt[1] = sShotOrigin[1];
            content.addView(c, new FrameLayout.LayoutParams(-1, -1));
            sCover = c;
            sCoverAt = SystemClock.uptimeMillis();
            MAIN.postDelayed(new Runnable() { public void run() { if (sCover == c) removeCover(); } }, 700);
            content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    if (sCover != c) { try { content.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { } return true; }
                    long e = SystemClock.uptimeMillis() - sCoverAt;
                    if (e > 350) {
                        float a = 1f - smooth((e - 350) / 200f);
                        if (a <= 0.01f) { removeCover(); try { content.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { } }
                        else { c.setAlpha(a); c.invalidate(); }
                    } else c.invalidate();
                    return true;
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void removeCover() {
        Stage c = sCover;
        sCover = null;
        if (c != null && c.getParent() instanceof ViewGroup) ((ViewGroup) c.getParent()).removeView(c);
    }

    static void captureScreen(View v, final Runnable after) {
        try {
            android.app.Activity a = activityOf(v);
            if (a == null || !on(v)) return;
            View decor = a.getWindow().getDecorView();
            if (decor.getWidth() == 0 || decor.getHeight() == 0) return;
            final int[] origin = new int[2];
            decor.getLocationOnScreen(origin);
            final Bitmap b = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            final long asked = SystemClock.uptimeMillis();
            final View root = decor;
            final android.app.Activity act = a;
            final View source = v;
            final Rect hole = new Rect();
            final Runnable done = new Runnable() {
                public void run() {
                    sShot = b;
                    sShotAt = asked;
                    sShotOrigin[0] = origin[0];
                    sShotOrigin[1] = origin[1];
                    sShotPage.setEmpty();
                    sShotTitle = null;
                    sShotHole.set(hole);
                    if (sCurrent != null && sCurrent.kind == OPEN && !sCurrent.finished) sCurrent.adoptShot(b, origin);
                    if (after != null) after.run();
                }
            };
            pixelCopy(v, new Rect(origin[0], origin[1], origin[0] + decor.getWidth(), origin[1] + decor.getHeight()), b, new Runnable() {
                public void run() {
                    try {
                        View sv = pageSurface(root);
                        if (sv != null && ntp(root) == null && !ChromeLayout.ntpFeedShown()) {
                            int[] at = new int[2];
                            sv.getLocationOnScreen(at);
                            pageHole(root, sv, at, hole);
                            Rect local = new Rect(hole);
                            local.offset(-origin[0], -origin[1]);
                            if (!hole.isEmpty() && hasContent(b, local)) hole.setEmpty();
                            if (!hole.isEmpty() && "pre".equals(captureMode())) {
                                final Rect where = new Rect(hole);
                                final long asked2 = SystemClock.uptimeMillis();
                                boolean asked = ChromeCapture.page(act, source, new ChromeCapture.Got() {
                                    public void got(Bitmap pageBmp) {
                                        try { if (pageBmp != null && place(b, pageBmp, where, origin)) hole.setEmpty(); } catch (Throwable t) { }
                                        if (pageBmp != null) ChromeCapture.recache(pageBmp);
                                        done.run();
                                    }
                                });
                                if (asked) return;
                            }
                        }
                    } catch (Throwable t) { hole.setEmpty(); }
                    done.run();
                }
            });
        } catch (Throwable ignored) { }
    }

    private static int flat(Bitmap b, int l, int t, int r, int bo) {
        try {
            l = Math.max(0, l); t = Math.max(0, t); r = Math.min(b.getWidth(), r); bo = Math.min(b.getHeight(), bo);
            if (r <= l || bo <= t) return 0;
            int first = b.getPixel(l, t);
            for (int y = t; y < bo; y += 2) for (int x = l; x < r; x += 2) if (!close(b.getPixel(x, y), first, 6)) return 0;
            return first | 0xFF000000;
        } catch (Throwable e) { return 0; }
    }

    private static final java.util.WeakHashMap<Bitmap, Bitmap> sNeutral = new java.util.WeakHashMap<Bitmap, Bitmap>();
    static Bitmap neutral(Bitmap b) {
        if (b == null || b.isRecycled()) return b;
        Bitmap done = sNeutral.get(b);
        if (done != null && !done.isRecycled()) return done;
        try {
            Bitmap src = b;
            try { src.getPixel(0, 0); } catch (Throwable hardware) { src = b.copy(Bitmap.Config.ARGB_8888, false); }
            int w = src.getWidth(), h = src.getHeight();
            if ((long) w * h > 6_000_000L) return b;
            int[] px = new int[w * h];
            src.getPixels(px, 0, w, 0, 0, w, h);
            boolean changed = false;
            for (int i = 0; i < px.length; i++) {
                int c = px[i], r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
                int max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
                if (max - min == 0 || max - min > 10 || max > 110) continue;
                int v = (r + g + bl + 1) / 3;
                px[i] = (c & 0xff000000) | (v << 16) | (v << 8) | v; changed = true;
            }
            if (!changed) { sNeutral.put(b, b); return b; }
            Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            sNeutral.put(b, out);
            return out;
        } catch (Throwable t) { return b; }
    }

    private static Bitmap signature(Bitmap src, Rect region) {
        try {
            Bitmap from = src;
            try { from.getPixel(0, 0); } catch (Throwable hardware) { from = src.copy(Bitmap.Config.ARGB_8888, false); }
            if (from == null) return null;
            Bitmap out = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888);
            new Canvas(out).drawBitmap(from, region, new Rect(0, 0, 8, 12), new Paint(Paint.FILTER_BITMAP_FLAG));
            return out;
        } catch (Throwable e) { return null; }
    }


    private static int pictureDiff(Bitmap a, Bitmap b) {
        if (a == null || b == null) return 999;
        long sum = 0;
        for (int y = 0; y < 12; y++) for (int x = 0; x < 8; x++) {
            int p = a.getPixel(x, y), q = b.getPixel(x, y);
            sum += Math.abs(Color.red(p) - Color.red(q)) + Math.abs(Color.green(p) - Color.green(q)) + Math.abs(Color.blue(p) - Color.blue(q));
        }
        return (int) (sum / (8 * 12 * 3));
    }

    private static String sShotTitle;

    static boolean lateRow(View row) {
        return find(row, "hub_toolbar") == null;
    }

    private static void easeRows(final ViewGroup g) {
        final long until = SystemClock.uptimeMillis() + 1500;
        final WeakHashMap<View, Boolean> shown = new WeakHashMap<View, Boolean>();
        g.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                if (SystemClock.uptimeMillis() > until || !g.isAttachedToWindow()) {
                    try { g.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { }
                    return true;
                }
                for (int i = 0; i < g.getChildCount(); i++) {
                    View v = g.getChildAt(i);
                    if (!lateRow(v) || sRising.containsKey(v)) continue;
                    boolean on = v.isShown() && v.getWidth() > 0 && v.getHeight() > 0 && v.getAlpha() >= 0.01f;
                    if (on && !shown.containsKey(v)) {
                        shown.put(v, Boolean.TRUE);
                        v.animate().cancel();
                        rise(v, 20, 8f * v.getResources().getDisplayMetrics().density, 1f);
                    } else if (!on) shown.remove(v);
                }
                return true;
            }
        });
    }

    private static int bottomColor(Bitmap src, Rect region) {
        try {
            Bitmap b = src;
            try { b.getPixel(0, 0); } catch (Throwable hardware) { b = src.copy(Bitmap.Config.ARGB_8888, false); }
            if (b == null) return 0;
            Rect q = new Rect(region);
            if (!q.intersect(0, 0, b.getWidth(), b.getHeight()) || q.width() < 4 || q.height() < 4) return 0;
            HashMap<Integer, Integer> count = new HashMap<Integer, Integer>();
            int best = 0, bestN = 0;
            for (int row = 1; row <= 3; row++) {
                int y = q.bottom - row;
                for (int i = 0; i < 40; i++) {
                    int c = b.getPixel(q.left + (q.width() - 1) * i / 39, y);
                    if (Color.alpha(c) < 255) continue;
                    int key = c & 0xFFF8F8F8;
                    Integer n = count.get(key);
                    int nn = n == null ? 1 : n + 1;
                    count.put(key, nn);
                    if (nn > bestN) { bestN = nn; best = c; }
                }
            }
            return bestN >= 12 ? best : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean blank(Bitmap b) {
        Bitmap sig = signature(b, new Rect(0, 0, b.getWidth(), b.getHeight()));
        if (sig == null) return false;
        int first = sig.getPixel(0, 0);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 8; x++) if (!close(sig.getPixel(x, y), first, 8)) return false;
        return true;
    }

    private static String cardTitle(View thumbnail) {
        View card = null;
        for (View q = thumbnail; q != null; q = q.getParent() instanceof View ? (View) q.getParent() : null) {
            if (q.getClass().getName().endsWith("TabGridView")) { card = q; break; }
        }
        if (card == null) return null;
        View t = find(card, "tab_title");
        if (t instanceof TextView) return ((TextView) t).getText().toString();
        for (View v : textViews(card)) { CharSequence s = ((TextView) v).getText(); if (s != null && s.length() > 0) return s.toString(); }
        return null;
    }

    private static int dominant(Bitmap b) {
        HashMap<Integer, Integer> count = new HashMap<Integer, Integer>();
        int total = 0, best = 0, bestN = 0;
        for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) {
            int c = b.getPixel(x, y) | 0xFF000000;
            Integer n = count.get(c);
            int nn = n == null ? 1 : n + 1;
            count.put(c, nn);
            total++;
            if (nn > bestN) { bestN = nn; best = c; }
        }
        return total > 0 && bestN * 10 >= total * 8 ? best : 0;
    }

    private static void easeMessages(final View hub) {
        if (hub == null) return;
        final long until = SystemClock.uptimeMillis() + 900;
        final WeakHashMap<View, Boolean> done = new WeakHashMap<View, Boolean>();
        final Runnable scan = new Runnable() {
            public void run() {
                ArrayList<View> rows = new ArrayList<View>();
                findMessages(hub, rows);
                if (rows.isEmpty()) banners(hub, rows);
                int n = 0;
                for (View v : rows) {
                    if (done.containsKey(v) || !v.isShown() || v.getAlpha() < 0.01f) continue;
                    done.put(v, Boolean.TRUE);
                    rise(v, 20 + 40L * n++, 8f * v.getResources().getDisplayMetrics().density, 1f);
                }
            }
        };
        scan.run();
        final ViewTreeObserver.OnPreDrawListener[] self = new ViewTreeObserver.OnPreDrawListener[1];
        self[0] = new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                if (SystemClock.uptimeMillis() > until || !hub.isAttachedToWindow()) {
                    try { hub.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { }
                    return true;
                }
                try { scan.run(); } catch (Throwable ignored) { }
                return true;
            }
        };
        try { hub.getViewTreeObserver().addOnPreDrawListener(self[0]); } catch (Throwable ignored) { }
    }

    private static void measureGrid(final View root) {
        MAIN.postDelayed(new Runnable() {
            public void run() {
                try {
                    if (active() || !root.isAttachedToWindow()) return;
                    final View hub = find(root, "hub_main_container");
                    if (hub == null || !hub.isShown() || hub.getHeight() == 0) return;
                    final int estimate = exactGrid(hub);
                    int[] at = new int[2];
                    hub.getLocationOnScreen(at);
                    int y = at[1] + Math.round(hub.getHeight() * 0.55f);
                    readBack(hub, new Rect(at[0] + 3, y, at[0] + 37, y + 48), new Got() {
                        public void got(int color) {
                            if (active() || !hub.isShown()) return;
                            if (color != sGridMeasured || estimate != sGridEstimate) {
                            }
                            sGridEstimate = estimate;
                            sGridMeasured = close(color, estimate, 90) ? color : 0;
                            ChromeLayout.refresh();
                        }
                    });
                } catch (Throwable ignored) { }
            }
        }, 280);
    }

    static float clamp(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }
    static float smooth(float v) { v = clamp(v); return v * v * (3f - 2f * v); }
    static float lerp(float a, float b, float f) { return a + (b - a) * f; }
    static int mix(int a, int b, float f) {
        f = clamp(f);
        return Color.argb(Math.round(lerp(Color.alpha(a), Color.alpha(b), f)), Math.round(lerp(Color.red(a), Color.red(b), f)),
                Math.round(lerp(Color.green(a), Color.green(b), f)), Math.round(lerp(Color.blue(a), Color.blue(b), f)));
    }

    private static boolean on(View v) {
        try { return ChromeLayout.enabled(v.getContext()); } catch (Throwable t) { return false; }
    }

    static void install() {
        if (sInstalled) return;
        sInstalled = true;
        Xp.hookAllMethods(View.class, "performClick", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                if (sBypass || p.args.length != 0) return;
                try {
                    View v = (View) p.thisObject;
                    if (!sChipBusy && on(v) && incognitoChip(v)) { incognitoFromChip(v); return; }
                    if (v.getId() == View.NO_ID) return;
                    String n = name(v);
                    if ("toolbar_action_button".equals(n)) {
                        if (on(v) && inside(v, "hub_toolbar")) {
                            p.setResult(Boolean.TRUE);
                            final View plus = v;
                            final boolean incognito = incognitoPane(v);
                            final android.app.Activity a = activity(v.getContext());
                            spinThen(v, new Runnable() { public void run() {
                                if (incognito && a != null) { try { ChromeLayout.menuAction(a, "new_tab_menu_id"); } catch (Throwable ignored) { } return; }
                                sBypass = true;
                                try { plus.performClick(); } catch (Throwable ignored) { } finally { sBypass = false; }
                            } });
                        }
                    } else if ("action_button".equals(n)) {
                        if (on(v) && closeCard(v)) p.setResult(Boolean.TRUE);
                    }
                } catch (Throwable ignored) { }
            }
        });
    }

    private static boolean sChipBusy;

    private static boolean incognitoChip(View v) {
        if (!v.isShown() || v.getWidth() <= 0) return false;
        boolean page = false;
        View q = v;
        for (int i = 0; q != null && i < 16; i++, q = q.getParent() instanceof View ? (View) q.getParent() : null) {
            String c = q.getClass().getName();
            if (c.contains("Incognito")) return false;
            if (c.endsWith("NewTabPageLayout") || c.endsWith("NewTabPageView")) { page = true; break; }
        }
        if (!page) return false;
        View w = v;
        for (int i = 0; w != null && i < 3; i++, w = w.getParent() instanceof View ? (View) w.getParent() : null) {
            if (name(w).toLowerCase(java.util.Locale.ROOT).contains("incognito")) return true;
            CharSequence cd = w.getContentDescription();
            if (cd != null && cd.toString().toLowerCase(java.util.Locale.ROOT).contains("incognito")) return true;
        }
        return incognitoText(v, 9);
    }

    private static void incognitoFromChip(View chip) {
        try {
            final View root = chip.getRootView();
            final View holder = find(root, "compositor_view_holder");
            if (holder == null || holder.getWidth() <= 0) return;
            try { ChromeSuperDark.hold(1800); } catch (Throwable ignored) { }
            pageCover(root, true);
            final ColorDrawable plain = sPageCover;
            if (plain == null) return;
            int[] c = new int[2], h = new int[2];
            chip.getLocationOnScreen(c); holder.getLocationOnScreen(h);
            final RectF from = new RectF(c[0] - h[0], c[1] - h[1], c[0] - h[0] + chip.getWidth(), c[1] - h[1] + chip.getHeight());
            final CardCover card = new CardCover(plain.getColor(), from, Math.min(chip.getHeight(), chip.getWidth()) / 2f);
            card.setBounds(0, 0, holder.getWidth(), holder.getHeight());
            holder.getOverlay().remove(plain);
            holder.getOverlay().add(card);
            sPageCover = card;
            sChipBusy = true;
            ValueAnimator grow = ValueAnimator.ofFloat(0f, 1f).setDuration(150);
            grow.setInterpolator(DECELERATE);
            grow.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator a) { card.t = (Float) a.getAnimatedValue(); card.invalidateSelf(); holder.invalidate(); } });
            grow.start();
            final long deadline = SystemClock.uptimeMillis() + 1400;
            final ViewTreeObserver.OnPreDrawListener[] self = new ViewTreeObserver.OnPreDrawListener[1];
            self[0] = new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    try {
                        boolean late = SystemClock.uptimeMillis() > deadline;
                        View page = ntp(root);
                        if (!late && (page == null || !page.getClass().getName().contains("Incognito"))) return true;
                        root.getViewTreeObserver().removeOnPreDrawListener(this);
                        final View shown = page;
                        holder.postDelayed(new Runnable() { public void run() {
                            sChipBusy = false;
                            if (sPageCover != card) return;
                            if (shown != null) pageUncover(root, shown); else removePageCover();
                        } }, late ? 0 : 320);
                    } catch (Throwable t) { try { root.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { } sChipBusy = false; }
                    return true;
                }
            };
            root.getViewTreeObserver().addOnPreDrawListener(self[0]);
            holder.postDelayed(new Runnable() { public void run() { sChipBusy = false; if (sPageCover == card) removePageCover(); } }, 1900);
        } catch (Throwable ignored) { sChipBusy = false; }
    }

    private static final class CardCover extends ColorDrawable {
        final RectF from; final float r0; float t;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final RectF now = new RectF();
        CardCover(int colour, RectF from, float r0) { super(colour | 0xFF000000); this.from = from; this.r0 = r0; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            now.set(lerp(from.left, b.left, t), lerp(from.top, b.top, t), lerp(from.right, b.right, t), lerp(from.bottom, b.bottom, t));
            float r = lerp(r0, 0f, t);
            paint.setColor(getColor());
            if (r < 0.5f) c.drawRect(now, paint); else c.drawRoundRect(now, r, r, paint);
        }
    }

    static boolean incognitoPane(View plus) {
        CharSequence cd = plus.getContentDescription();
        if (cd != null && cd.toString().toLowerCase(java.util.Locale.ROOT).contains("incognito")) return true;
        View tb = plus;
        while (tb != null && !"hub_toolbar".equals(name(tb))) tb = tb.getParent() instanceof View ? (View) tb.getParent() : null;
        View header = tb != null && tb.getParent() instanceof View ? (View) tb.getParent() : null;
        return header != null && incognitoText(header, 0);
    }

    private static boolean incognitoText(View v, int depth) {
        if (depth > 12 || v.getVisibility() != View.VISIBLE) return false;
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText(), h = ((TextView) v).getHint();
            String s = ((t == null ? "" : t) + " " + (h == null ? "" : h)).toLowerCase(java.util.Locale.ROOT);
            return s.contains("incognito");
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) if (incognitoText(((ViewGroup) v).getChildAt(i), depth + 1)) return true;
        return false;
    }

    static android.app.Activity activity(android.content.Context c) {
        for (int i = 0; c != null && i < 8; i++) {
            if (c instanceof android.app.Activity) return (android.app.Activity) c;
            if (!(c instanceof android.content.ContextWrapper)) return null;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static final WeakHashMap<View, Boolean> sSpinning = new WeakHashMap<View, Boolean>();
    static boolean spinning(View v) { return sSpinning.containsKey(v); }

    static void spinThen(final View plus, final Runnable open) {
        if (sSpinning.containsKey(plus)) return;
        int[] at = new int[2];
        plus.getLocationOnScreen(at);
        sPlus.set(at[0], at[1], at[0] + plus.getWidth(), at[1] + plus.getHeight());
        sPlusAt = SystemClock.uptimeMillis();
        try { if ("New incognito tab".contentEquals(String.valueOf(plus.getContentDescription())) || incognitoPane(plus)) ChromeSuperDark.hold(1600); } catch (Throwable ignored) { }
        final View root = plus.getRootView();
        Bitmap b = null;
        if (plus.getWidth() > 0 && plus.getHeight() > 0 && root != null) {
            try {
                b = Bitmap.createBitmap(plus.getWidth(), plus.getHeight(), Bitmap.Config.ARGB_8888);
                boolean pressed = plus.isPressed();
                if (pressed) plus.setPressed(false);
                plus.draw(new Canvas(b));
                if (pressed) plus.setPressed(true);
            } catch (Throwable t) { b = null; }
        }
        if (b != null) {
            int[] r = new int[2];
            root.getLocationOnScreen(r);
            final Spin d = new Spin(b, at[0] - r[0], at[1] - r[1]);
            d.setBounds(0, 0, root.getWidth(), root.getHeight());
            root.getOverlay().add(d);
            sSpinning.put(plus, Boolean.TRUE);
            final float a0 = plus.getAlpha();
            plus.setAlpha(0f);
            freezeHub(root);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(240);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    float t = (Float) a.getAnimatedValue();
                    d.angle = 90f * DECELERATE.getInterpolation(Math.min(1f, t / 0.85f));
                    d.alpha = t < 0.8f ? 1f : 1f - smooth((t - 0.8f) / 0.2f);
                    d.invalidateSelf();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    try { root.getOverlay().remove(d); } catch (Throwable ignored) { }
                    d.bmp.recycle();
                    sSpinning.remove(plus);
                    plus.setAlpha(a0);
                }
            });
            va.start();
        }
        pageCover(root, "New incognito tab".contentEquals(String.valueOf(plus.getContentDescription())));
        try { open.run(); } catch (Throwable ignored) { }
        entrance(root, sPlus.centerX(), sPlus.centerY(), true);
    }

    private static String sPageKey;
    private static boolean sPageIncognito;

    static String coverKey(Context c, boolean incognito) {
        boolean night = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String mode = !night ? "l" : SuperDark.inEffect(c) ? "s" : "d";
        return "cover2_" + (incognito ? "i" : "n") + "_" + mode;
    }
    private static long sPageAt;
    private static java.lang.ref.WeakReference<View> sPageHolder = new java.lang.ref.WeakReference<View>(null);
    private static ColorDrawable sPageCover;

    private static android.content.SharedPreferences pagePrefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("pixelsuite_chrome_ui", Context.MODE_PRIVATE);
    }

    private static void pageCover(View root, boolean incognito) {
        try {
            String key = coverKey(root.getContext(), incognito);
            sPageKey = key; sPageAt = SystemClock.uptimeMillis(); sPageIncognito = incognito;
            removePageCover();
            Object v = pagePrefs(root.getContext()).getAll().get(key);
            int colour = v instanceof Integer ? (Integer) v : defaultCover(key);
            final View holder = find(root, "compositor_view_holder");
            if (holder == null || holder.getWidth() <= 0) return;
            try { View bar = bar(root); if (bar instanceof ChromeLayout.Bar) ((ChromeLayout.Bar) bar).paintBg(colour | 0xFF000000, false); } catch (Throwable ignored) { }
            final ColorDrawable d = new ColorDrawable(colour | 0xFF000000);
            d.setBounds(0, 0, holder.getWidth(), holder.getHeight());
            holder.getOverlay().add(d);
            sPageCover = d; sPageHolder = new java.lang.ref.WeakReference<View>(holder);
            holder.postDelayed(new Runnable() { public void run() { if (sPageCover == d) removePageCover(); } }, 1600);
        } catch (Throwable ignored) { }
    }

    private static int defaultCover(String key) {
        if (key.equals("cover2_i_s")) return 0xFF000000;
        if (key.startsWith("cover2_i_")) return 0xFF121212;
        return key.endsWith("_l") ? 0xFFE9E6EA : 0xFF0D0D0D;
    }

    private static View bar(View v) {
        if (v instanceof ChromeLayout.Bar) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = bar(g.getChildAt(i)); if (f != null) return f; } }
        return null;
    }

    private static void removePageCover() {
        View h = sPageHolder.get();
        if (sPageCover != null && h != null) { try { h.getOverlay().remove(sPageCover); } catch (Throwable ignored) { } }
        sPageCover = null;
    }

    private static void pageUncover(final View root, final View ntp) {
        final ColorDrawable d = sPageCover;
        final View h = sPageHolder.get();
        if (d != null && h != null) {
            ValueAnimator va = ValueAnimator.ofInt(255, 0).setDuration(160);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator a) { d.setAlpha((Integer) a.getAnimatedValue()); h.invalidate(); } });
            va.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { if (sPageCover == d) removePageCover(); } });
            va.start();
        }
        final String key = sPageKey;
        if (key == null || SystemClock.uptimeMillis() - sPageAt > 2500) return;
        sPageKey = null;
        final boolean wantIncognito = sPageIncognito;
        if (ntp.getClass().getName().contains("Incognito") != wantIncognito) return;
        ntp.postDelayed(new Runnable() { public void run() {
            try {
                if (!ntp.isShown() || ntp.getWidth() < 20 || ntp.getHeight() < 40) return;
                int[] at = new int[2];
                ntp.getLocationOnScreen(at);
                int mid = at[1] + ntp.getHeight() / 2;
                if (ntp.getClass().getName().contains("Incognito") != wantIncognito || !key.equals(coverKey(ntp.getContext(), wantIncognito))) return;
                readBack(ntp, new Rect(at[0] + 1, mid - 4, at[0] + 4, mid + 4), new Got() { public void got(int c) {
                    try { pagePrefs(root.getContext()).edit().putInt(key, c).apply(); } catch (Throwable ignored) { }
                }});
            } catch (Throwable ignored) { }
        }}, 700);
    }

    private static long sFrozenUntil;

    static boolean frozen() { return SystemClock.uptimeMillis() < sFrozenUntil; }

    private static void freezeHub(View root) {
        try {
            final View hub = find(root, "hub_toolbar");
            if (hub == null || !hub.isShown() || hub.getWidth() <= 0 || hub.getHeight() <= 0) return;
            final Bitmap shot = Bitmap.createBitmap(hub.getWidth(), hub.getHeight(), Bitmap.Config.ARGB_8888);
            int bg = exactGrid(root);
            shot.eraseColor(bg != 0 ? (bg | 0xFF000000) : 0xFFFBF8FD);
            hub.draw(new Canvas(shot));
            final BitmapDrawable d = new BitmapDrawable(hub.getResources(), shot);
            d.setBounds(0, 0, hub.getWidth(), hub.getHeight());
            hub.getOverlay().add(d);
            sFrozenUntil = SystemClock.uptimeMillis() + 650;
            hub.postDelayed(new Runnable() { public void run() {
                try { hub.getOverlay().remove(d); } catch (Throwable ignored) { }
                sFrozenUntil = 0;
                shot.recycle();
            }}, 650);
        } catch (Throwable ignored) { }
    }

    private static final class Spin extends Drawable {
        final Bitmap bmp;
        final float x, y;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float angle, alpha = 1f;
        Spin(Bitmap b, float x, float y) { bmp = b; this.x = x; this.y = y; }
        @Override public void draw(Canvas c) {
            if (bmp.isRecycled() || alpha <= 0.004f) return;
            int s = c.save();
            c.translate(x, y);
            c.rotate(angle, bmp.getWidth() / 2f, bmp.getHeight() / 2f);
            p.setAlpha(Math.round(255 * alpha));
            c.drawBitmap(bmp, 0, 0, p);
            c.restoreToCount(s);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static final WeakHashMap<View, Boolean> sClosing = new WeakHashMap<View, Boolean>();

    private static boolean closeCard(final View button) {
        View card = null;
        int depth = 0;
        for (View q = button; q != null && depth < 8; q = q.getParent() instanceof View ? (View) q.getParent() : null, depth++) {
            if (q.getClass().getName().endsWith("TabGridView")) { card = q; break; }
        }
        if (card == null || !card.isShown() || !inside(card, "tab_list_recycler_view")) return false;
        for (View q = card; q != null; q = q.getParent() instanceof View ? (View) q.getParent() : null) {
            if (q.getClass().getName().contains("Editor")) return false;
        }
        if (hasClass(card, "FaviconCluster", 0)) return false;
        if (sClosing.containsKey(card)) return true;
        sClosing.put(card, Boolean.TRUE);
        final View c = card;
        final CharSequence who = button.getContentDescription();
        final float a0 = c.getAlpha(), sx = c.getScaleX(), sy = c.getScaleY();
        c.animate().cancel();
        c.animate().alpha(0f).scaleX(sx * 0.82f).scaleY(sy * 0.82f).setDuration(190).setInterpolator(ACCELERATE).withEndAction(new Runnable() {
            public void run() {
                CharSequence now = button.getContentDescription();
                boolean same = who == null ? now == null : now != null && who.toString().equals(now.toString());
                if (same) {
                    sBypass = true;
                    try { button.performClick(); } catch (Throwable ignored) { } finally { sBypass = false; }
                }
                final View.OnAttachStateChangeListener reset = new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View v) { }
                    public void onViewDetachedFromWindow(View v) {
                        v.removeOnAttachStateChangeListener(this);
                        v.animate().cancel();
                        v.setAlpha(a0); v.setScaleX(sx); v.setScaleY(sy);
                        sClosing.remove(v);
                    }
                };
                c.addOnAttachStateChangeListener(reset);
                MAIN.postDelayed(new Runnable() {
                    public void run() {
                        if (!sClosing.containsKey(c)) return;
                        c.removeOnAttachStateChangeListener(reset);
                        sClosing.remove(c);
                        if (c.isAttachedToWindow()) c.animate().alpha(a0).scaleX(sx).scaleY(sy).setDuration(260).setInterpolator(DECELERATE).start();
                        else { c.setAlpha(a0); c.setScaleX(sx); c.setScaleY(sy); }
                    }
                }, same ? 900 : 0);
            }
        }).start();
        return true;
    }

    private static final WeakHashMap<View, ValueAnimator> sRising = new WeakHashMap<View, ValueAnimator>();

    static void entrance(final View root, final float px, final float py, final boolean grow) {
        if (root == null || !on(root)) return;
        final long deadline = SystemClock.uptimeMillis() + 1600;
        final ViewTreeObserver.OnPreDrawListener[] self = new ViewTreeObserver.OnPreDrawListener[1];
        self[0] = new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                try {
                    if (!root.isAttachedToWindow() || SystemClock.uptimeMillis() > deadline) { root.getViewTreeObserver().removeOnPreDrawListener(this); return true; }
                    View hub = find(root, "hub_main_container");
                    if (hub != null && hub.isShown() && hub.getAlpha() > 0.02f) return true;
                    if (active()) return true;
                    View ntp = ntp(root);
                    if (ntp == null) return true;
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    play(root, ntp, px, py, grow && SystemClock.uptimeMillis() - sNewFlightAt > 1500);
                } catch (Throwable t) { try { root.getViewTreeObserver().removeOnPreDrawListener(this); } catch (Throwable ignored) { } }
                return true;
            }
        };
        root.getViewTreeObserver().addOnPreDrawListener(self[0]);
    }

    private static void play(View root, View ntp, float px, float py, boolean grow) {
        try { pageUncover(root, ntp); } catch (Throwable ignored) { }
        float d = root.getResources().getDisplayMetrics().density;
        if (grow && px >= 0) {
            final View holder = find(root, "compositor_view_holder");
            if (holder != null && holder.getWidth() > 0) {
                int[] at = new int[2];
                holder.getLocationOnScreen(at);
                final float sx = holder.getScaleX(), sy = holder.getScaleY();
                holder.animate().cancel();
                holder.setPivotX(Math.max(0, Math.min(holder.getWidth(), px - at[0])));
                holder.setPivotY(Math.max(0, Math.min(holder.getHeight(), py - at[1])));
                holder.setScaleX(sx * 0.94f);
                holder.setScaleY(sy * 0.94f);
                holder.animate().scaleX(sx).scaleY(sy).setDuration(420).setInterpolator(EMPHASIZED).withEndAction(new Runnable() {
                    public void run() { resetPivot(holder); }
                }).start();
            }
        }
        ViewGroup sections = ntp instanceof ViewGroup ? (ViewGroup) ntp : null;
        if (sections == null) return;
        int screen = root.getHeight() > 0 ? root.getHeight() : root.getResources().getDisplayMetrics().heightPixels;
        int[] at = new int[2];
        int n = 0;
        for (int i = 0; i < sections.getChildCount() && n < 6; i++) {
            View c = sections.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getHeight() == 0) continue;
            c.getLocationOnScreen(at);
            if (at[1] > screen) break;
            rise(c, 30 + n * 38L, 16 * d + n * 3 * d);
            n++;
        }
    }

    private static void rise(View v, long delay, float dy) { rise(v, delay, dy, -1f); }

    private static void rise(final View v, long delay, final float dy, float to) {
        ValueAnimator old = sRising.remove(v);
        if (old != null) old.end();
        final float a0 = to >= 0f ? to : v.getAlpha();
        final float[] applied = {dy};
        v.setAlpha(0f);
        v.setTranslationY(v.getTranslationY() + dy);
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setStartDelay(delay);
        va.setDuration(420);
        va.setInterpolator(null);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float lin = (Float) a.getAnimatedValue();
                float want = dy * (1f - DECELERATE.getInterpolation(lin));
                v.setTranslationY(v.getTranslationY() - applied[0] + want);
                applied[0] = want;
                v.setAlpha(a0 * smooth(lin / 0.85f));
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            boolean done;
            void end() {
                if (done) return;
                done = true;
                v.setTranslationY(v.getTranslationY() - applied[0]);
                applied[0] = 0f;
                v.setAlpha(a0);
                if (sRising.get(v) == va) sRising.remove(v);
            }
            @Override public void onAnimationEnd(Animator a) { end(); }
            @Override public void onAnimationCancel(Animator a) { end(); }
        });
        sRising.put(v, va);
        va.start();
    }

    private static View ntp(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) return null;
        String n = v.getClass().getName();
        if (n.endsWith("NewTabPageLayout") && v.isShown() && v.getWidth() > 0) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = ntp(g.getChildAt(i)); if (f != null) return f; }
        }
        if (n.endsWith("NewTabPageView") && v.isShown() && v.getWidth() > 0) return v;
        return null;
    }

    static void start(Object animator, ImageView image, Rect from, Rect to) {
        try {
            if (!on(image)) return;
            View root = image.getRootView().findViewById(android.R.id.content);
            if (!(root instanceof FrameLayout) || !(image.getParent() instanceof View)) return;
            if (sCurrent != null) sCurrent.finish(true);
            boolean recentPlus = SystemClock.uptimeMillis() - sPlusAt < 1500;
            int kind;
            if (from.width() > to.width()) kind = OPEN;
            else if (recentPlus || from.width() < to.width() / 4 || from.height() < 60) kind = NEW;
            else kind = CLOSE;
            Flight f = new Flight(kind, image, (FrameLayout) root, from, to);
            FLIGHTS.put(animator, f);
            sCurrent = f;
            f.begin();
        } catch (Throwable t) {
        }
    }

    static void frame(Object animator, Rect current) {
        Flight f = FLIGHTS.get(animator);
        if (f != null && !f.finished && f.added && f.image.getAlpha() != 0f) f.image.setAlpha(0f);
    }

    private static final class Flight implements View.OnAttachStateChangeListener {
        final int kind;
        final ImageView image;
        final FrameLayout content;
        final Rect from, to, small, full;
        final Stage stage;
        final ValueAnimator clock;
        final float density;
        final IdentityHashMap<View, Float> hidden = new IdentityHashMap<View, Float>();
        final ArrayList<View> messages = new ArrayList<View>();
        final HashMap<View, Float> messageAlpha = new HashMap<View, Float>();
        final int[] contentAt = new int[2], parentAt = new int[2], at = new int[2];
        final RectF page = new RectF(), card = new RectF(), origin = new RectF();
        View hub, pane, header, target;
        float targetAlpha = 1f, cardRadius;
        boolean nativeDone, clockDone, finished, added, paneScaled, headerTried, looked, exact, checked, copyTried;
        long settleSince, hubGoneAt, toolbarAt, clockEndAt, beginAt;
        boolean waitCapture;

        void captureWebPage() {
            final Bitmap copy = sShot;
            final Rect where = new Rect(sShotHole);
            final int[] origin = {sShotOrigin[0], sShotOrigin[1]};
            android.app.Activity act = activityOf(content);
            final long asked = SystemClock.uptimeMillis();
            waitCapture = ChromeCapture.page(act, content, new ChromeCapture.Got() {
                public void got(Bitmap page) {
                    boolean ok = false;
                    try { ok = page != null && copy == sShot && place(copy, page, where, origin); } catch (Throwable t) { }
                    if (ok) {
                        sShotHole.setEmpty();
                        if (!finished && (!added || t <= 0.12f)) {
                            shot = null;
                            adoptShot(copy, origin);
                        }
                    }
                    waitCapture = false;
                    if (!finished && !added) render(t);
                }
            });
        }
        int failures, liveFrames;
        int readyFrames;
        Bitmap shot;
        final Rect shotSrc = new Rect();
        final ViewTreeObserver.OnPreDrawListener preDraw = new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                if (!finished) {
                    if (!added) render(t);
                    maybeFinish();
                }
                return true;
            }
        };
        float t;
        final Runnable failsafe = new Runnable() { public void run() { if (!finished) { finish(true); } } };
        boolean clockStarted;
        long clockStartAt;
        final Runnable startLate = new Runnable() {
            public void run() {
                if (finished || clockStarted) return;
                if (kind == CLOSE && stage.source == null && !stage.flatSnap) { stage.flatSnap = true; stage.fill = stage.topColor; }
                startClock();
                render(0f);
            }
        };
        final Runnable tick = new Runnable() {
            public void run() {
                if (finished) return;
                long now = SystemClock.uptimeMillis();
                if (!clockDone && now - clockStartAt > clock.getDuration() + 120) {
                    clockDone = true;
                    clockEndAt = now - 150;
                    render(1f);
                }
                maybeFinish();
                if (!finished) MAIN.postDelayed(this, 32);
            }
        };

        void startClock() {
            if (clockStarted || finished) return;
            clockStarted = true;
            clockStartAt = SystemClock.uptimeMillis();
            MAIN.removeCallbacks(startLate);
            clock.start();
            MAIN.postDelayed(tick, clock.getDuration() + 60);
        }

        String state() {
            View root = content.getRootView();
            return (kind == OPEN ? "open" : kind == CLOSE ? "close" : "new tab") + " t=" + t + " clock=" + clockStarted + "/" + clockDone
                    + " chromeDone=" + nativeDone + " snapshot=" + image.isAttachedToWindow() + "/" + image.isShown()
                    + " stage=" + added + "/" + stage.isAttachedToWindow() + " hub=" + (hub == null ? "none" : hub.isShown() + "@" + hub.getAlpha())
                    + " ntp=" + (ntp(root) != null || ChromeLayout.ntpFeedShown()) + " live=" + liveFrames + " ready=" + readyFrames
                    + " settle=" + (settleSince == 0 ? 0 : SystemClock.uptimeMillis() - settleSince);
        }

        Flight(int kind, ImageView image, FrameLayout content, Rect from, Rect to) {
            this.kind = kind;
            this.image = image;
            this.content = content;
            this.from = new Rect(from);
            this.to = new Rect(to);
            small = new Rect(kind == OPEN ? to : from);
            full = new Rect(kind == OPEN ? from : to);
            density = image.getResources().getDisplayMetrics().density;
            stage = new Stage(image);
            clock = ValueAnimator.ofFloat(0f, 1f);
            clock.setDuration(kind == OPEN ? OPEN_MS : kind == CLOSE ? CLOSE_MS : NEW_MS);
            clock.setInterpolator(null);
        }

        void begin() {
            beginAt = SystemClock.uptimeMillis();
            sActive++;
            stage.incognito = ChromeSuperDark.incognitoNow();
            if (stage.incognito) ChromeSuperDark.hold(clock.getDuration() + 600);
            if (kind == NEW) { sNewFlightAt = SystemClock.uptimeMillis(); ChromeLayout.expectNtp(); }
            image.addOnAttachStateChangeListener(this);
            ChromeLayout.snapshotStarted(image, kind == OPEN);
            View root = image.getRootView();
            hub = find(root, "hub_main_container");
            View tb = find(root, "hub_toolbar");
            header = tb != null && tb.getParent() instanceof View ? (View) tb.getParent() : null;
            pane = hub == null ? null : find(hub, "hub_pane_host");
            stage.gridColor = gridColor(hub != null ? hub : root);
            int chromeFallback = paintColor(image);
            stage.topColor = ChromeLayout.barColor(chromeFallback != 0 ? chromeFallback : stage.gridColor);
            if (kind == NEW) {
                int ntpBg = ChromeLayout.ntpColor() != 0 ? ChromeLayout.ntpColor() : sNtpColor;
                stage.fill = ntpBg != 0 && close(ntpBg, stage.topColor, 60) ? ntpBg : stage.topColor;
            }
            if (kind == OPEN) {
                if (sShot != null && SystemClock.uptimeMillis() - sShotAt < 1500 && !sShotHole.isEmpty() && "post".equals(captureMode())) captureWebPage();
                if (sShot != null && SystemClock.uptimeMillis() - sShotAt < 1500) adoptShot(sShot, sShotOrigin);
                else captureScreen(content);
                if (shot == null && stage.toolbarShot == null) stage.toolbar = capture(find(root, "control_container"), null, stage.toolbarAt);
            }
            stage.headerView = header;
            if (hub != null) {
                findMessages(hub, messages);
                if (messages.isEmpty()) banners(hub, messages);
                for (View m : messages) { ValueAnimator r = sRising.remove(m); if (r != null) r.end(); messageAlpha.put(m, 1f); }
                if (kind == OPEN) for (View m : messages) m.setAlpha(0f);
            }
            clock.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { render((Float) a.getAnimatedValue()); }
            });
            clock.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { if (!finished) { clockDone = true; clockEndAt = SystemClock.uptimeMillis(); render(1f); maybeFinish(); } }
            });
            stage.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { }
                public void onViewDetachedFromWindow(View v) { if (!finished) finish(true); }
            });
            MAIN.postDelayed(failsafe, clock.getDuration() + 1000 + 150);
            try { content.getViewTreeObserver().addOnPreDrawListener(preDraw); } catch (Throwable ignored) { }
            render(0f);
            if (kind == NEW || added) startClock();
            else MAIN.postDelayed(startLate, 150);
        }

        void adoptShot(Bitmap b, int[] origin) {
            if (finished || shot != null || kind != OPEN || (added && t > 0.12f)) return;
            measure();
            shotSrc.set(Math.round(page.left + contentAt[0] - origin[0]), Math.round(page.top + contentAt[1] - origin[1]),
                    Math.round(page.right + contentAt[0] - origin[0]), Math.round(page.bottom + contentAt[1] - origin[1]));
            if (shotSrc.isEmpty()) return;
            int w0 = shotSrc.width(), h0 = shotSrc.height();
            if (!shotSrc.intersect(0, 0, b.getWidth(), b.getHeight())) return;
            if (shotSrc.width() < w0 * 0.96f || shotSrc.height() < h0 * 0.96f) {
                return;
            }
            if (b == sShot && !sShotHole.isEmpty()) {
                stage.toolbarShot = b;
                stage.toolbarSrc.set(0, 0, b.getWidth(), shotSrc.top);
                stage.toolbarShotAt[0] = origin[0];
                stage.toolbarShotAt[1] = origin[1];
                stage.toolbar = null;
                int top = flat(b, 1, Math.max(0, shotSrc.top - 90), 6, Math.max(1, shotSrc.top - 50));
                if (top != 0) stage.topColor = top;
                return;
            }
            shot = b;
            exact = true;
            stage.setSource(b, shotSrc);
            stage.toolbarShot = b;
            stage.toolbarSrc.set(0, 0, b.getWidth(), shotSrc.top);
            stage.toolbarShotAt[0] = origin[0];
            stage.toolbarShotAt[1] = origin[1];
            stage.toolbar = null;
            int top = flat(b, 1, Math.max(0, shotSrc.top - 90), 6, Math.max(1, shotSrc.top - 50));
            if (top != 0) stage.topColor = top;
            sShotPage.set(shotSrc);
            sShotTop = stage.topColor;
            if (ChromeLayout.barShowsNtp()) { int c = flat(b, 2, shotSrc.top + 4, 12, shotSrc.top + 14); if (c != 0) sNtpColor = c; }
            image.setAlpha(0f);
            if (!added) render(t);
        }

        void checkSource(Bitmap chrome) {
            if (checked) return;
            checked = true;
            try {
                Rect whole = new Rect(0, 0, chrome.getWidth(), chrome.getHeight());
                if (kind == OPEN && shot != null) {
                    int diff = pictureDiff(signature(shot, shotSrc), signature(chrome, whole));
                    if (diff > (shot == sTapShot ? 20 : 14)) {
                        shot = null;
                        exact = false;
                        stage.source = null;
                        stage.setSource(chrome, whole);
                    }
                } else if (kind == CLOSE && copyFits() && sameTab(chrome, whole)) {
                    useCopy();
                }
            } catch (Throwable ignored) { }
        }

        boolean copyFits() {
            return sShot != null && !sShotPage.isEmpty()
                    && Math.abs(sShotPage.width() - page.width()) < 3 && Math.abs(sShotPage.height() - page.height()) <= Math.max(3, page.height() * 0.04f);
        }

        void useCopy() {
            shot = sShot;
            exact = true;
            stage.setSource(sShot, sShotPage);
            stage.topColor = sShotTop;
            stage.toolbarShot = sShot;
            stage.toolbarSrc.set(0, 0, sShot.getWidth(), sShotPage.top);
            stage.toolbarShotAt[0] = sShotOrigin[0];
            stage.toolbarShotAt[1] = sShotOrigin[1];
            stage.liveToolbar = null;
            stage.flatSnap = false;
        }

        boolean sameTab(Bitmap chrome, Rect whole) {
            int diff = chrome == null ? -1 : pictureDiff(signature(sShot, sShotPage), signature(chrome, whole));
            String title = target != null ? cardTitle(target) : null;
            boolean titled = title != null && title.equals(sShotTitle);
            if (titled) return chrome == null || blank(chrome) || diff <= 30;
            return chrome != null && diff <= 14;
        }

        boolean night() {
            return (image.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        void captureHeader() {
            if (headerTried || header == null || header.getWidth() == 0 || header.getHeight() == 0) return;
            header.getLocationOnScreen(at);
            int screen = image.getResources().getDisplayMetrics().heightPixels;
            if (at[1] < screen / 2) return;
            headerTried = true;
            stage.header = capture(header, find(header, "hub_toolbar"), null);
        }

        View findTarget() {
            if (hub == null) return null;
            ArrayList<View> thumbs = new ArrayList<View>();
            collect(hub, "tab_thumbnail", thumbs);
            int ex = small.left + parentAt[0], ey = small.top + parentAt[1];
            ArrayList<View> column = new ArrayList<View>();
            for (View v : thumbs) {
                v.getLocationOnScreen(at);
                if (Math.abs(at[0] - ex) < 6 && Math.abs(v.getWidth() - small.width()) < 6) column.add(v);
            }
            if (column.isEmpty()) return null;
            if (column.size() == 1) return column.get(0);
            View selected = selectedByTint(thumbs);
            if (selected != null && column.contains(selected)) return selected;
            View best = null;
            int bestD = Integer.MAX_VALUE;
            for (View v : column) { v.getLocationOnScreen(at); int dd = Math.abs(at[1] - ey); if (dd < bestD) { bestD = dd; best = v; } }
            return best;
        }

        View selectedByTint(ArrayList<View> thumbs) {
            Map<Integer, Integer> count = new HashMap<Integer, Integer>();
            Map<View, Integer> tint = new HashMap<View, Integer>();
            for (View v : thumbs) {
                View cardView = null;
                for (View q = v; q != null; q = q.getParent() instanceof View ? (View) q.getParent() : null) {
                    if (q.getClass().getName().endsWith("TabGridView")) { cardView = find(q, "card_view"); break; }
                }
                if (cardView == null || cardView.getBackgroundTintList() == null) continue;
                int c = cardView.getBackgroundTintList().getDefaultColor();
                tint.put(v, c);
                Integer n = count.get(c);
                count.put(c, n == null ? 1 : n + 1);
            }
            if (count.size() < 2) return null;
            int common = 0;
            for (int n : count.values()) common = Math.max(common, n);
            if (common < 2) return null;
            View only = null;
            for (Map.Entry<View, Integer> e : tint.entrySet()) {
                if (count.get(e.getValue()) == 1) { if (only != null) return null; only = e.getKey(); }
            }
            return only;
        }

        void measure() {
            if (image.getParent() instanceof View) ((View) image.getParent()).getLocationOnScreen(parentAt);
            content.getLocationOnScreen(contentAt);
            page.set(full.left + parentAt[0] - contentAt[0], full.top + parentAt[1] - contentAt[1], full.right + parentAt[0] - contentAt[0], full.bottom + parentAt[1] - contentAt[1]);
            int barTotal = ChromeLayout.barTotal();
            if (kind != NEW && barTotal > 0 && content.getHeight() > 0) page.bottom = Math.min(page.bottom, content.getHeight() - barTotal);
            if (kind == NEW) {
                if (SystemClock.uptimeMillis() - sPlusAt < 1500 && sPlus.width() > 0) origin.set(sPlus.left - contentAt[0], sPlus.top - contentAt[1], sPlus.right - contentAt[0], sPlus.bottom - contentAt[1]);
                else origin.set(small.left + parentAt[0] - contentAt[0], small.top + parentAt[1] - contentAt[1], small.right + parentAt[0] - contentAt[0], small.bottom + parentAt[1] - contentAt[1]);
                int bar = ChromeLayout.barTotal();
                page.set(0, 0, content.getWidth(), content.getHeight() - (bar > 0 ? bar : 0));
                return;
            }
            if (target == null || !target.isAttachedToWindow() || !target.isShown()) {
                View found = findTarget();
                if (found != target) {
                    if (target != null) restore(target);
                    target = found;
                    if (target != null) {
                        if (added) hide(target);
                        cardRadius = radius(target);
                    }
                }
            }
            if (target != null) {
                target.getLocationOnScreen(at);
                card.set(at[0] - contentAt[0], at[1] - contentAt[1], at[0] - contentAt[0] + target.getWidth() * scaleOf(target), at[1] - contentAt[1] + target.getHeight() * scaleOf(target));
                if (!paneScaled && pane != null && pane.getWidth() > 0) {
                    paneScaled = true;
                    int[] pa = new int[2];
                    pane.getLocationOnScreen(pa);
                    pane.setPivotX(Math.max(0, Math.min(pane.getWidth(), at[0] + target.getWidth() / 2f - pa[0])));
                    pane.setPivotY(Math.max(0, Math.min(pane.getHeight(), at[1] + target.getHeight() / 2f - pa[1])));
                }
            } else {
                card.set(small.left + parentAt[0] - contentAt[0], small.top + parentAt[1] - contentAt[1], small.right + parentAt[0] - contentAt[0], small.bottom + parentAt[1] - contentAt[1]);
            }
        }

        float scaleOf(View v) {
            float s = 1f;
            for (View q = v; q != null; q = q.getParent() instanceof View ? (View) q.getParent() : null) s *= q.getScaleX();
            return s;
        }

        void logOnce() {
        }

        void hide(View v) { if (v != null) { if (!hidden.containsKey(v)) hidden.put(v, v.getAlpha()); v.setAlpha(0f); } }
        void restore(View v) { Float a = hidden.remove(v); if (a != null) v.setAlpha(a); }

        void render(float raw) {
            if (finished) return;
            t = raw;
            try {
                measure();
                Stage s = stage;
                s.kind = kind;
                boolean webOpen = kind == OPEN && !exact;
                float p = webOpen ? DECELERATE.getInterpolation(t) : EMPHASIZED.getInterpolation(t);
                if (kind == NEW) {
                    s.snap.set(lerp(origin.left, page.left, p), lerp(origin.top, page.top, p), lerp(origin.right, page.right, p), lerp(origin.bottom, page.bottom, p));
                    s.radius = lerp(origin.height() / 2f, 0f, p);
                    s.snapAlpha = smooth(t / 0.12f);
                    s.shadow = (float) Math.sin(Math.PI * p);
                    s.backdropAlpha = 0f;
                    s.bandTop = page.bottom;
                    s.bandAlpha = smooth((t - 0.12f) / 0.4f);
                    s.pageClip = -1f;
                } else {
                    if (s.bitmap == null) {
                        Drawable d = image.getDrawable();
                        Bitmap b = d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null;
                        if (b != null && !b.isRecycled()) {
                            s.bitmap = b;
                            if (s.source == null) s.setSource(neutral(b), new Rect(0, 0, b.getWidth(), b.getHeight()));
                            checkSource(b);
                            if (kind == CLOSE && !exact && !blank(b)) s.flatSnap = false;
                            else if (kind == CLOSE && !exact) {
                                s.source = null;
                                s.shader = null;
                                s.flatSnap = true;
                                s.fill = s.topColor;
                            }
                        } else if (kind == CLOSE && !copyTried && !checked && s.source == null && target != null && copyFits()) {
                            copyTried = true;
                            if (sameTab(null, null)) { checked = true; useCopy(); }
                        }
                    }
                    boolean open = kind == OPEN;
                    float hand = open ? smooth((t - 0.88f) / 0.12f) : 0f;
                    float shrink = open ? p + (1f - p) * hand : 1f - p;
                    s.snap.set(lerp(page.left, card.left, shrink), lerp(page.top, card.top, shrink), lerp(page.right, card.right, shrink), lerp(page.bottom, card.bottom, shrink));
                    s.radius = cardRadius * shrink;
                    s.shadow = (float) Math.sin(Math.PI * p);
                    if (open) {
                        s.backdrop = mix(s.sd(s.topColor), s.sd(s.gridColor), smooth((t - 0.13f) / 0.27f));
                        s.backdropAlpha = 1f - smooth((t - 0.38f) / 0.34f);
                        s.toolbarAlpha = 1f - smooth(t / 0.16f);
                        s.toolbarDy = 0f;
                        s.headerAlpha = smooth((t - 0.30f) / 0.34f);
                        s.headerDy = 12f * density * (1f - DECELERATE.getInterpolation(clamp((t - 0.28f) / 0.5f)));
                        s.snapAlpha = target != null ? 1f - hand : 1f;
                        Float a = hidden.get(target);
                        if (target != null && a != null) target.setAlpha(hand > 0f ? a : 0f);
                    } else {
                        s.backdrop = mix(s.sd(s.gridColor), s.sd(s.topColor), smooth((p - 0.35f) / 0.6f));
                        s.backdropAlpha = smooth(t / 0.2f);
                        s.toolbarAlpha = s.toolbarShot != null ? smooth((p - 0.55f) / 0.4f) : 0f;
                        if (s.toolbarShot == null) {
                            if (s.liveToolbar == null) s.liveToolbar = find(content.getRootView(), "control_container");
                            s.liveToolbarAlpha = smooth((p - 0.55f) / 0.4f);
                        } else s.liveToolbar = null;
                        s.toolbarDy = 0f;
                        s.headerAlpha = 1f - smooth(t / 0.2f);
                        s.headerDy = 12f * density * ACCELERATE.getInterpolation(clamp(t / 0.2f));
                        s.snapAlpha = 1f;
                        for (View m : messages) { Float a = messageAlpha.get(m); if (a != null) m.setAlpha(a * (1f - smooth(t / 0.16f))); }
                    }
                    if (paneScaled && pane != null) {
                        float sc = open ? lerp(1.06f, 1f, p) : lerp(1f, 1.06f, p);
                        pane.setScaleX(sc);
                        pane.setScaleY(sc);
                    }
                    s.bandTop = -1;
                    s.bandAlpha = 1f;
                    s.headerT = t;
                    s.headerOpening = open;
                    if (header != null && hub != null && header.getHeight() > 0) {
                        hub.getLocationOnScreen(at);
                        s.bandTop = at[1] + hub.getHeight() - ChromeLayout.navInset(hub) - header.getHeight() - contentAt[1];
                        header.getLocationOnScreen(at);
                        s.headerX = at[0] - contentAt[0];
                        s.headerY = at[1] - contentAt[1];
                    }
                    float full = s.getHeight() > 0 ? s.getHeight() : content.getHeight();
                    s.pageClip = s.bandTop > 0 && full > s.bandTop ? lerp(full, s.bandTop, shrink) : -1f;
                }
                if (kind == CLOSE && !added && s.source == null && !s.flatSnap && clockStarted && t > 0.08f) {
                    s.flatSnap = true;
                    s.fill = s.topColor;
                }
                boolean holdForCapture = waitCapture && SystemClock.uptimeMillis() - beginAt < 160;
                if (!added && !holdForCapture && (kind == NEW || s.source != null || s.flatSnap)) {
                    added = true;
                    content.addView(stage, new FrameLayout.LayoutParams(-1, -1));
                    removeCover();
                    image.setAlpha(0f);
                    if (target != null) hide(target);
                    if (kind == OPEN && target != null) target.setAlpha(0f);
                    ChromeLayout.refresh();
                    if (!looked) { looked = true; logOnce(); }
                    startClock();
                }
                ChromeLayout.snapshotProgress(kind == OPEN, t);
                stage.invalidate();
                failures = 0;
            } catch (Throwable e) {
                if (++failures >= 3) finish(true);
            }
        }

        void maybeFinish() {
            if (finished || !clockDone) return;
            if (!nativeDone) {
                long since = SystemClock.uptimeMillis() - clockEndAt;
                if (!image.isAttachedToWindow() || !image.isShown() || since > 150) nativeDone = true;
                else { stage.invalidate(); return; }
            }
            if (kind == OPEN) { finish(false); return; }
            long now = SystemClock.uptimeMillis();
            if (settleSince == 0) settleSince = now;
            View root = content.getRootView();
            boolean hubGone = hub == null || !hub.isShown() || hub.getAlpha() < 0.02f;
            if (hubGone && hubGoneAt == 0) hubGoneAt = now;
            boolean ntpShown = ntp(root) != null || ChromeLayout.ntpFeedShown();
            if (ntpShown) liveFrames++; else liveFrames = 0;
            boolean live = liveFrames >= 4 || (!ntpShown && hubGoneAt != 0 && now - hubGoneAt > 90);
            if (hubGone && live) readyFrames++; else readyFrames = 0;
            if (readyFrames >= 2 || now - settleSince > 500) finish(false);
            else stage.invalidate();
        }

        void finish(boolean now) {
            if (finished) return;
            finished = true;
            final Bitmap snap = stage.bitmap;
            if (kind == OPEN && snap != null && snap.getWidth() >= page.width() * 0.95f && "hook".equals(captureMode())) {
                MAIN.post(new Runnable() { public void run() { ChromeCapture.recache(snap); } });
            }
            sActive = Math.max(0, sActive - 1);
            if (sCurrent == this) sCurrent = null;
            MAIN.removeCallbacks(failsafe);
            MAIN.removeCallbacks(startLate);
            MAIN.removeCallbacks(tick);
            try { content.getViewTreeObserver().removeOnPreDrawListener(preDraw); } catch (Throwable ignored) { }
            clock.removeAllUpdateListeners();
            clock.cancel();
            image.removeOnAttachStateChangeListener(this);
            if (!nativeDone && image.isAttachedToWindow()) image.setAlpha(1f);
            for (Map.Entry<View, Float> e : hidden.entrySet()) e.getKey().setAlpha(e.getValue());
            hidden.clear();
            if (pane != null && paneScaled) { pane.setScaleX(1f); pane.setScaleY(1f); resetPivot(pane); }
            for (Map.Entry<View, Float> e : messageAlpha.entrySet()) e.getKey().setAlpha(1f);
            if (kind == OPEN && !now) easeMessages(hub);
            if (kind == OPEN && !now && header instanceof ViewGroup) easeRows((ViewGroup) header);
            if (kind == OPEN && !now) measureGrid(content.getRootView());
            if (kind == OPEN) sShotTitle = exact && shot == sShot && target != null && !sShotPage.isEmpty() ? cardTitle(target) : null;
            else sShotTitle = null;
            boolean fade = !now && kind != OPEN && added && stage.getParent() == content;
            if (fade) {
                stage.animate().alpha(0f).setDuration(exact ? FADE_OUT_MS : 200).setInterpolator(DECELERATE).withEndAction(new Runnable() {
                    public void run() { remove(); }
                }).start();
            } else remove();
            ChromeLayout.refresh();
        }

        void remove() {
            if (stage.getParent() == content) content.removeView(stage);
            stage.release();
        }

        public void onViewAttachedToWindow(View v) { }

        public void onViewDetachedFromWindow(View v) {
            nativeDone = true;
            image.removeOnAttachStateChangeListener(this);
            maybeFinish();
        }
    }

    static final class Stage extends View {
        int kind;
        Bitmap bitmap, header, toolbar, source, toolbarShot;
        View headerView, liveToolbar;
        float liveToolbarAlpha, headerT;
        boolean headerOpening;
        final Rect src = new Rect(), toolbarSrc = new Rect(), hole = new Rect();
        final int[] toolbarShotAt = new int[2];
        BitmapShader shader;
        final Matrix shaderMatrix = new Matrix();
        final int[] toolbarAt = new int[2];
        final RectF snap = new RectF(), r = new RectF();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint fill0 = new Paint();
        final int[] me = new int[2];
        int gridColor = 0xFFFBF8FD, topColor = 0xFFFFFFFF, fill = 0xFFFFFFFF, backdrop = 0xFFFFFFFF;
        int tail;
        boolean incognito;
        private int sd(int v) { return incognito ? ChromeSuperDark.darken(v) : v; }

        boolean flatSnap;
        float radius, shadow, snapAlpha = 1f, backdropAlpha, bandAlpha = 1f, toolbarAlpha, toolbarDy, headerAlpha, headerDy, headerX, headerY, bandTop = -1;
        float pageClip = -1;

        Stage(View anchor) {
            super(anchor.getContext());
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClickable(false);
            setFocusable(false);
            setWillNotDraw(false);
        }

        void setBitmap(Bitmap b) {
            bitmap = b;
            if (source == null) setSource(b, new Rect(0, 0, b.getWidth(), b.getHeight()));
        }

        void setSource(Bitmap b, Rect region) {
            source = b;
            src.set(region);
            tail = bottomColor(b, region);
            try { shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); } catch (Throwable t) { shader = null; }
        }

        void release() {
            headerView = null;
            liveToolbar = null;
            bitmap = null;
            source = null;
            toolbarShot = null;
            shader = null;
            header = null;
            toolbar = null;
        }

        @Override protected void onDraw(Canvas c) {
            try { paint(c); } catch (Throwable t) { }
        }

        private void paint(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            int w = getWidth(), h = getHeight();
            int band = bandTop > 0 ? Math.min(Math.round(bandTop), h) : h;
            paint.setShader(null);
            if (kind == COVER) {
                if (source != null && !source.isRecycled()) {
                    getLocationOnScreen(me);
                    int save = c.save();
                    if (!hole.isEmpty()) c.clipRect(hole.left - me[0], hole.top - me[1], hole.right - me[0], hole.bottom - me[1], android.graphics.Region.Op.DIFFERENCE);
                    c.drawBitmap(source, toolbarShotAt[0] - me[0], toolbarShotAt[1] - me[1], null);
                    c.restoreToCount(save);
                }
                return;
            }
            if (kind == NEW) {
                if (band < h && bandAlpha > 0.001f) {
                    fill0.setColor(sd(topColor));
                    fill0.setAlpha(Math.round(255 * bandAlpha));
                    c.drawRect(0, band, w, h, fill0);
                }
            } else {
                if (backdropAlpha >= 0.999f) {
                    fill0.setColor(sd(backdrop));
                    c.drawRect(0, 0, w, h, fill0);
                } else {
                    if (backdropAlpha > 0.001f) {
                        fill0.setColor(sd(backdrop));
                        fill0.setAlpha(Math.round(255 * backdropAlpha));
                        c.drawRect(0, 0, w, band, fill0);
                    }
                    if (band < h) {
                        fill0.setColor(sd(backdrop));
                        c.drawRect(0, band, w, h, fill0);
                    }
                }
                if (toolbarShot != null && toolbarAlpha > 0.001f && !toolbarSrc.isEmpty()) {
                    getLocationOnScreen(me);
                    bmp.setShader(null);
                    bmp.setAlpha(Math.round(255 * toolbarAlpha));
                    int dx = toolbarShotAt[0] - me[0], dy = Math.round(toolbarShotAt[1] - me[1] + toolbarDy);
                    r.set(toolbarSrc.left + dx, toolbarSrc.top + dy, toolbarSrc.right + dx, toolbarSrc.bottom + dy);
                    c.drawBitmap(toolbarShot, toolbarSrc, r, bmp);
                } else if (toolbar != null && toolbarAlpha > 0.001f) {
                    getLocationOnScreen(me);
                    bmp.setShader(null);
                    bmp.setAlpha(Math.round(255 * toolbarAlpha));
                    c.drawBitmap(toolbar, toolbarAt[0] - me[0], Math.round(toolbarAt[1] - me[1] + toolbarDy), bmp);
                }
                if (liveToolbar != null && liveToolbarAlpha > 0.001f && liveToolbar.isAttachedToWindow() && liveToolbar.getWidth() > 0) {
                    getLocationOnScreen(me);
                    int[] at = new int[2];
                    liveToolbar.getLocationOnScreen(at);
                    int x = at[0] - me[0], y = at[1] - me[1];
                    int save = c.saveLayerAlpha(x, y, x + liveToolbar.getWidth(), y + liveToolbar.getHeight(), Math.round(255 * liveToolbarAlpha));
                    c.translate(x, y);
                    liveToolbar.draw(c);
                    c.restoreToCount(save);
                }
            }
            int under = -1;
            if (kind != NEW && pageClip > 0 && pageClip < h) { under = c.save(); c.clipRect(0, 0, w, (float) Math.ceil(pageClip)); }
            try { flying(c, d); } finally { if (under >= 0) c.restoreToCount(under); }
            if (kind != NEW) toolbarRows(c, w, h, band);
        }

        private void toolbarRows(Canvas c, int w, int h, int band) {
            if (band < h && headerView instanceof ViewGroup && headerView.isAttachedToWindow() && headerView.getWidth() > 0) {
                ViewGroup g = (ViewGroup) headerView;
                ArrayList<View> rows = new ArrayList<View>();
                for (int i = 0; i < g.getChildCount(); i++) { View ch = g.getChildAt(i); if (ch.getVisibility() == View.VISIBLE && ch.getWidth() > 0 && ch.getHeight() > 0 && ch.getAlpha() >= 0.5f && !(headerOpening && lateRow(ch))) rows.add(ch); }
                java.util.Collections.sort(rows, new java.util.Comparator<View>() { public int compare(View x, View y) { return x.getTop() - y.getTop(); } });
                int clip = c.save();
                c.clipRect(0, band, w, h);
                float hy = Math.max(headerY, band);
                for (int i = 0; i < rows.size(); i++) {
                    View ch = rows.get(i);
                    float travel = h - (hy + ch.getTop());
                    float a, dyRow;
                    if (headerOpening) {
                        float k = clamp((headerT - (0.24f + 0.05f * i)) / 0.42f);
                        a = smooth(k / 0.12f);
                        dyRow = travel * (1f - DECELERATE.getInterpolation(k));
                    } else {
                        float k = clamp(headerT / 0.26f);
                        a = 1f - smooth((k - 0.85f) / 0.15f);
                        dyRow = travel * ACCELERATE.getInterpolation(k);
                    }
                    if (a <= 0.004f) continue;
                    float x = Math.round(headerX + ch.getLeft() + ch.getTranslationX()), y = Math.round(hy + ch.getTop() + ch.getTranslationY() + dyRow);
                    float pad = 16 * ch.getResources().getDisplayMetrics().density;
                    int one = c.saveLayerAlpha(x - pad, y - pad, x + ch.getWidth() + pad, y + ch.getHeight() + pad, Math.round(255 * a));
                    c.translate(x, y);
                    ch.draw(c);
                    ChromeIncognitoPlus.drawIn(c, ch);
                    c.restoreToCount(one);
                }
                c.restoreToCount(clip);
            } else if (band < h && headerView != null && headerView.isAttachedToWindow() && headerView.getWidth() > 0 && headerAlpha > 0.001f) {
                int save = c.saveLayerAlpha(0, band, w, h, Math.round(255 * headerAlpha));
                c.clipRect(0, band, w, h);
                c.translate(Math.round(headerX), Math.round(Math.max(headerY, band) + headerDy));
                headerView.draw(c);
                ChromeIncognitoPlus.drawIn(c, headerView);
                c.restoreToCount(save);
            } else if (band < h && header != null && headerAlpha > 0.001f) {
                int save = c.save();
                c.clipRect(0, band, w, h);
                bmp.setShader(null);
                bmp.setAlpha(Math.round(255 * headerAlpha));
                c.drawBitmap(header, Math.round(headerX), Math.round(Math.max(headerY, band) + headerDy), bmp);
                c.restoreToCount(save);
            }
        }

        private void flying(Canvas c, float d) {
            if (snapAlpha <= 0.001f || snap.width() < 1 || snap.height() < 1) return;
            if (shadow > 0.01f) {
                paint.setShader(null);
                for (int i = 0; i < 3; i++) {
                    float spread = d * (1.5f + 2.5f * i), drop = d * (1f + i);
                    paint.setColor(Color.BLACK);
                    paint.setAlpha(Math.round(shadow * snapAlpha * (i == 0 ? 14 : i == 1 ? 8 : 4)));
                    r.set(snap.left - spread, snap.top - spread + drop, snap.right + spread, snap.bottom + spread + drop);
                    c.drawRoundRect(r, radius + spread, radius + spread, paint);
                }
            }
            if (kind == NEW || flatSnap) {
                paint.setShader(null);
                paint.setColor(sd(fill));
                paint.setAlpha(Math.round(255 * snapAlpha));
                c.drawRoundRect(snap, radius, radius, paint);
                return;
            }
            if (source == null || source.isRecycled() || src.width() <= 0) return;
            float scale = snap.width() / src.width();
            if (shader != null) {
                shaderMatrix.setScale(scale, scale);
                shaderMatrix.postTranslate(snap.left - src.left * scale, snap.top - src.top * scale);
                shader.setLocalMatrix(shaderMatrix);
                float imageBottom = snap.top + src.height() * scale;
                boolean short_ = imageBottom < snap.bottom - 0.5f;
                if (short_) {
                    paint.setShader(null);
                    paint.setColor(sd(tail != 0 ? tail : backdrop));
                    paint.setAlpha(Math.round(255 * snapAlpha));
                    c.drawRoundRect(snap, radius, radius, paint);
                }
                int clipSave = c.save();
                if (short_) c.clipRect(snap.left, snap.top, snap.right, (float) Math.floor(imageBottom));
                bmp.setShader(shader);
                bmp.setAlpha(Math.round(255 * snapAlpha));
                c.drawRoundRect(snap, radius, radius, bmp);
                bmp.setShader(null);
                c.restoreToCount(clipSave);
            } else {
                int save = c.save();
                Path clip = new Path();
                clip.addRoundRect(snap, radius, radius, Path.Direction.CW);
                c.clipPath(clip);
                bmp.setAlpha(Math.round(255 * snapAlpha));
                r.set(snap.left, snap.top, snap.right, snap.top + src.height() * scale);
                c.drawBitmap(source, src, r, bmp);
                c.restoreToCount(save);
            }
        }
    }

    private static Bitmap capture(View v, View opaqueChild, int[] screenAt) {
        if (v == null || v.getWidth() == 0 || v.getHeight() == 0 || !v.isAttachedToWindow()) return null;
        float childAlpha = opaqueChild != null ? opaqueChild.getAlpha() : 1f;
        Drawable fg = v.getForeground();
        Bitmap b = null;
        try {
            if (opaqueChild != null && childAlpha != 1f) opaqueChild.setAlpha(1f);
            if (fg != null) v.setForeground(null);
            b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(b));
            if (screenAt != null) v.getLocationOnScreen(screenAt);
            return b;
        } catch (Throwable t) {
            if (b != null) b.recycle();
            return null;
        } finally {
            if (opaqueChild != null && childAlpha != 1f) opaqueChild.setAlpha(childAlpha);
            if (fg != null) v.setForeground(fg);
        }
    }

    private static float radius(View v) {
        try {
            Outline o = new Outline();
            if (v.getOutlineProvider() != null) {
                v.getOutlineProvider().getOutline(v, o);
                Object r = Outline.class.getMethod("getRadius").invoke(o);
                if (r instanceof Float && (Float) r > 0) return (Float) r;
            }
        } catch (Throwable ignored) { }
        return 10f * v.getResources().getDisplayMetrics().density;
    }

    private static void resetPivot(View v) {
        try { View.class.getMethod("resetPivot").invoke(v); }
        catch (Throwable t) { v.setPivotX(v.getWidth() / 2f); v.setPivotY(v.getHeight() / 2f); }
    }

    private static String name(View v) {
        try { return v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return ""; }
    }

    private static boolean inside(View v, String id) {
        for (Object q = v.getParent(); q instanceof View; q = ((View) q).getParent()) if (id.equals(name((View) q))) return true;
        return false;
    }

    private static boolean hasClass(View v, String part, int depth) {
        if (depth > 6) return false;
        if (v.getVisibility() == View.VISIBLE && v.getClass().getName().contains(part)) return true;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) if (hasClass(g.getChildAt(i), part, depth + 1)) return true; }
        return false;
    }

    private static void collect(View v, String id, ArrayList<View> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (id.equals(name(v)) && v.getWidth() > 0) { out.add(v); return; }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), id, out); }
    }

    private static void findMessages(View v, ArrayList<View> out) {
        String n = v.getClass().getName();
        if (n.endsWith("ArchivedTabsCardView") || n.endsWith("MessageCardView") || n.endsWith("PriceCardView")) { if (v.getVisibility() == View.VISIBLE) out.add(v); return; }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) findMessages(g.getChildAt(i), out); }
    }

    private static void banners(View v, ArrayList<View> found) {
        if ("container".equals(name(v)) && v instanceof ViewGroup) {
            for (View text : textViews(v)) {
                if (((TextView) text).getText().toString().toLowerCase(java.util.Locale.ROOT).contains("inactive items")) {
                    View parent = v.getParent() instanceof View ? (View) v.getParent() : null;
                    found.add(parent != null && "card".equals(name(parent)) ? parent : v);
                    return;
                }
            }
        }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) banners(g.getChildAt(i), found); }
    }

    private static ArrayList<View> textViews(View v) {
        ArrayList<View> list = new ArrayList<View>();
        if (v instanceof TextView) list.add(v);
        else if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) list.addAll(textViews(g.getChildAt(i))); }
        return list;
    }

    private static View find(View v, String id) {
        if (v == null) return null;
        if (id.equals(name(v))) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View got = find(g.getChildAt(i), id); if (got != null) return got; } }
        return null;
    }

    private static int surface(View v, int fallback) {
        for (View q = v; q != null; q = q.getParent() instanceof View ? (View) q.getParent() : null) {
            Drawable d = q.getBackground();
            if (d instanceof ColorDrawable) { int c = ((ColorDrawable) d).getColor(); if (Color.alpha(c) == 255) return c; }
        }
        return fallback;
    }

    static int gridSurface(View root) {
        int fallback = (root.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES ? 0xFF1B1B1F : 0xFFFBF8FD;
        return sample(root, surface(root, fallback));
    }

    private static int sample(View v, int fallback) {
        if (v == null || v.getWidth() == 0 || v.getHeight() == 0) return fallback;
        Bitmap px = null;
        try {
            px = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(px);
            canvas.translate(-v.getWidth() * 0.5f + 1.5f, -v.getHeight() * 0.65f + 1.5f);
            v.draw(canvas);
            int r = 0, g = 0, b = 0;
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                int c = px.getPixel(x, y);
                if (Color.alpha(c) != 255) return fallback;
                r += Color.red(c); g += Color.green(c); b += Color.blue(c);
            }
            return Color.rgb(r / 9, g / 9, b / 9);
        } catch (Throwable ignored) {
            return fallback;
        } finally {
            if (px != null) px.recycle();
        }
    }
}
