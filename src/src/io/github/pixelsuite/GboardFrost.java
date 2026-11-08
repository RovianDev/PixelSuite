package io.github.pixelsuite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ImageView;

import java.util.Map;
import java.util.WeakHashMap;

final class GboardFrost {
    private GboardFrost() { }
    static final String KEY = "pixelsuite_gboard_frost";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, Glass> GLASS = new WeakHashMap<View, Glass>();
    private static final Map<Drawable, Boolean> CLEARED = new WeakHashMap<Drawable, Boolean>();
    private static final Map<Drawable, Boolean> IGNORES = new WeakHashMap<Drawable, Boolean>();
    private static boolean sInstalled;

    static synchronized void install() {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Xp.hookAllMethods(Window.class, "setNavigationBarColor", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length == 1 && p.args[0] instanceof Integer && ((Integer) p.args[0]) != 0 && ours((Window) p.thisObject)) p.args[0] = 0;
                }
            });
        } catch (Throwable t) { }
        Xp.hookAllMethods(InputMethodService.class, "hideWindow", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    android.app.Dialog dg = ((InputMethodService) p.thisObject).getWindow();
                    View dec = dg == null || dg.getWindow() == null ? null : dg.getWindow().peekDecorView();
                    Glass g; synchronized (GLASS) { g = dec == null ? null : GLASS.get(dec); }
                    if (g != null) g.hiding();
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(InputMethodService.class, "showWindow", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                final InputMethodService ims = (InputMethodService) p.thisObject;
                try { if (android.os.Looper.myLooper() == Looper.getMainLooper()) apply(ims); } catch (Throwable t) { }
                MAIN.post(new Runnable() { public void run() { try { apply(ims); } catch (Throwable t) { } } });
            }
        });
    }

    private static boolean ours(Window w) {
        try { View d = w.peekDecorView(); if (d == null) return false; synchronized (GLASS) { return GLASS.containsKey(d); } } catch (Throwable t) { return false; }
    }

    private static boolean wanted() {
        try { return !GboardMain.off() && GboardPlus.opt(KEY) && android.os.Build.VERSION.SDK_INT >= 31; } catch (Throwable t) { return false; }
    }

    static void apply(InputMethodService ims) {
        if (!wanted()) return;
        android.app.Dialog dialog = ims.getWindow();
        Window w = dialog == null ? null : dialog.getWindow();
        if (w == null) return;
        final View decor = w.getDecorView();
        Glass g;
        synchronized (GLASS) { g = GLASS.get(decor); }
        if (g == null) {
            g = new Glass(ims, decor);
            synchronized (GLASS) { GLASS.put(decor, g); }
            try { w.setFormat(PixelFormat.TRANSLUCENT); } catch (Throwable ignored) { }
            try { w.setNavigationBarColor(Color.TRANSPARENT); } catch (Throwable ignored) { }
            try { Window.class.getMethod("setNavigationBarContrastEnforced", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
            w.setBackgroundDrawable(g);
            final Glass fg = g;
            decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() { try { fg.follow(); } catch (Throwable ignored) { } return true; }
            });
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() { try { fg.clearLayers(); } catch (Throwable ignored) { } }
            });
            g.clearLayers();
        } else g.clearLayers();
        g.showing();
        g.follow();
    }

    static View panel(View decor) {
        View holder = find(decor, "keyboard_holder");
        if (holder == null || !(holder.getParent() instanceof View)) return null;
        View lin = (View) holder.getParent();
        if (lin.getParent() instanceof View && ((View) lin.getParent()).getHeight() < decor.getHeight() * 0.9f) return (View) lin.getParent();
        return lin;
    }

    private static View find(View v, String idName) {
        if (v.getId() != View.NO_ID) {
            try { if (idName.equals(v.getResources().getResourceEntryName(v.getId()))) return v; } catch (Throwable ignored) { }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), idName); if (f != null) return f; }
        }
        return null;
    }

    private static View findClass(View v, String simpleName) {
        if (v.getClass().getName().endsWith(simpleName)) return v;
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View f = findClass(g.getChildAt(i), simpleName); if (f != null) return f; } }
        return null;
    }

    private static String idName(View v) {
        if (v.getId() == View.NO_ID) return "-";
        try { return v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return "#" + Integer.toHexString(v.getId()); }
    }

    static final class Glass extends Drawable {
        final InputMethodService ims; final View decor; final Context c;
        final float d;
        boolean superDark, dark;
        int tone;
        final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF shape = new RectF();
        float radius; boolean openBottom;
        Drawable blur; boolean blurTried;
        GlassRim rim;
        final int[] at = new int[2], decorAt = new int[2];
        private long shownAt = android.os.SystemClock.uptimeMillis();
        private boolean hidden;
        private int lastColor;
        static final long HOLD = 180, FADE = 220;
        void showing() { shownAt = android.os.SystemClock.uptimeMillis(); hidden = false; invalidateSelf(); decor.invalidate(); }
        void hiding() { hidden = true; invalidateSelf(); decor.invalidate(); }
        float seeThrough() {
            if (true) return 1f;
            if (hidden) return 0f;
            long t = android.os.SystemClock.uptimeMillis() - shownAt;
            if (t <= HOLD) return 0f;
            float f = Math.min(1f, (t - HOLD) / (float) FADE);
            return f * f * (3f - 2f * f);
        }
        int toneNow(float k) {
            int a = Color.alpha(tone);
            return (tone & 0xffffff) | (Math.round(255 + (a - 255) * k) << 24);
        }
        private long lastClear; private int reclears;

        Glass(InputMethodService ims, View decor) {
            this.ims = ims; this.decor = decor; c = ims;
            d = c.getResources().getDisplayMetrics().density;
            look(SuperDark.inEffect(c));
        }

        private long lookAt; private boolean sdNow;
        void checkLook() {
            long now = android.os.SystemClock.uptimeMillis();
            if (lookAt == 0 || now - lookAt >= 500) { lookAt = now; sdNow = SuperDark.inEffect(c); }
            boolean sd = sdNow;
            boolean dk = sd || FrostedSurface.dark(c);
            if (sd == superDark && dk == dark) return;
            look(sd);
            lastColor = 0;
            if (blur != null) { try { Xp.callMethod(blur, "setColor", tone); } catch (Throwable ignored) { } }
            invalidateSelf(); decor.invalidate();
        }

        private void look(boolean sd) {
            superDark = sd;
            dark = superDark || FrostedSurface.dark(c);
            int base = superDark ? Color.BLACK : dark ? LauncherFeatures.surface(c, "system_surface_container_high_dark", 0xff303137)
                    : FrostedSurface.composite(FrostedSurface.resourceColor(c, "blur_shade_panel_fg", 0x66dedee8), FrostedSurface.resourceColor(c, "blur_shade_panel_bg", 0x66f9f9fe));
            int density = superDark ? 0xd9 : 0xcc;
            tone = (base & 0xffffff) | (density << 24);
            rim = new GlassRim(24 * d, Math.max(1f, 1.5f * d), dark);
            rim.context = c;
        }

        boolean blurAllowed() {
            try { Object wm = c.getSystemService(Context.WINDOW_SERVICE); return Boolean.TRUE.equals(wm.getClass().getMethod("isCrossWindowBlurEnabled").invoke(wm)); }
            catch (Throwable t) { return true; }
        }

        void makeBlur() {
            if (blurTried) return;
            blurTried = true;
            try {
                Object root = Xp.callMethod(decor, "getViewRootImpl");
                Drawable b = (Drawable) Xp.callMethod(root, "createBackgroundBlurDrawable");
                Xp.callMethod(b, "setBlurRadius", Math.max(0, FrostedSurface.depthBlurRadius(c)));
                Xp.callMethod(b, "setColor", tone);
                blur = b;
            } catch (Throwable t) { blur = null; }
        }

        private java.lang.ref.WeakReference<View> panelRef = new java.lang.ref.WeakReference<View>(null);
        View panel() {
            View p = panelRef.get();
            if (p == null || !p.isAttachedToWindow() || p.getRootView() != decor.getRootView()) { p = GboardFrost.panel(decor); panelRef = new java.lang.ref.WeakReference<View>(p); }
            return p;
        }

        private java.lang.ref.WeakReference<View> navRef = new java.lang.ref.WeakReference<View>(null);
        private java.lang.ref.WeakReference<View> barRef = new java.lang.ref.WeakReference<View>(null);
        void clearBar() {
            View bar = barRef.get();
            if (bar == null || bar.getParent() == null) { bar = decor.findViewById(android.R.id.navigationBarBackground); barRef = new java.lang.ref.WeakReference<View>(bar); }
            if (bar == null) return;
            Drawable bg = bar.getBackground();
            if (bg instanceof ColorDrawable) {
                int col = ((ColorDrawable) bg).getColor();
                if (Color.alpha(col) != 0) {
                    ((ColorDrawable) bg).setColor(Color.TRANSPARENT);
                }
            } else if (bg != null && bar.getAlpha() != 0f) {
                bar.setAlpha(0f);
            }
        }

        private final Map<View, Boolean> cut = new WeakHashMap<View, Boolean>();
        void cutStrip(View panel, boolean open) {
            java.util.ArrayList<View> chain = new java.util.ArrayList<View>();
            boolean area = false;
            for (android.view.ViewParent p = panel.getParent(); p instanceof View && p != decor && chain.size() < 4; p = p.getParent()) {
                View v = (View) p;
                chain.add(v);
                if ("inputArea".equals(idName(v))) { area = true; break; }
            }
            if (!area) while (chain.size() > 1) chain.remove(chain.size() - 1);
            int[] pl = new int[2], vl = new int[2];
            panel.getLocationInWindow(pl);
            int panelBottom = pl[1] + panel.getHeight();
            for (View v : chain) {
                Rect want = null;
                if (open && wanted()) {
                    v.getLocationInWindow(vl);
                    int b = panelBottom - vl[1];
                    if (b > 0 && b < v.getHeight()) want = new Rect(0, 0, v.getWidth(), b);
                }
                Rect now = v.getClipBounds();
                if (want == null) {
                    if (now != null && cut.containsKey(v)) { v.setClipBounds(null); cut.remove(v); }
                    continue;
                }
                if (!want.equals(now)) {
                    v.setClipBounds(want);
                    cut.put(v, Boolean.TRUE);
                }
            }
        }

        void clearNav() {
            try { clearBar(); } catch (Throwable ignored) { }
            View nav = navRef.get();
            if (nav == null || !nav.isAttachedToWindow()) { nav = findClass(decor, "NavigationBarFrame"); navRef = new java.lang.ref.WeakReference<View>(nav); }
            if (nav == null) return;
            clearTree(nav);
        }
        private void clearTree(View v) {
            Drawable bg = v.getBackground();
            if (bg != null && !Boolean.TRUE.equals(CLEARED.get(bg))) {
                try { Drawable m = bg.mutate(); m.setAlpha(0); CLEARED.put(m, Boolean.TRUE); } catch (Throwable ignored) { }
            }
            if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View ch = g.getChildAt(i); if (!(ch instanceof android.widget.ImageView) && ch.getWidth() >= v.getWidth() * 0.85f) clearTree(ch); } }
        }

        void follow() {
            try { clearNav(); } catch (Throwable ignored) { }
            View panel = panel();
            if (panel == null || panel.getWidth() <= 0) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastClear > 250) { lastClear = now; try { clearLayers(); } catch (Throwable ignored) { } }
            panel.getLocationInWindow(at); decor.getLocationInWindow(decorAt);
            float x = at[0] - decorAt[0], y = at[1] - decorAt[1];
            Rect r = new Rect(0, 0, panel.getWidth(), panel.getHeight());
            float rad = 0;
            boolean open = true;
            try {
                if (panel.getClipToOutline() && panel.getOutlineProvider() != null) {
                    Outline o = new Outline();
                    panel.getOutlineProvider().getOutline(panel, o);
                    Rect or = new Rect();
                    if (Boolean.TRUE.equals(Outline.class.getMethod("getRect", Rect.class).invoke(o, or))) { r.set(or); rad = (Float) Outline.class.getMethod("getRadius").invoke(o); }
                }
            } catch (Throwable ignored) { }
            open = r.bottom >= panel.getHeight();
            try { cutStrip(panel, open); } catch (Throwable ignored) { }
            float top = y + r.top, bottom = open ? decor.getHeight() : y + Math.min(r.bottom, panel.getHeight());
            RectF want = new RectF(x + r.left, top, x + r.right, bottom);
            if (!want.equals(shape) || rad != radius || open != openBottom) {
                shape.set(want); radius = rad; openBottom = open;
                setBoundsFromShape();
                invalidateSelf();
                decor.invalidate();
                if (reclears < 3) { reclears++; MAIN.postDelayed(new Runnable() { public void run() { try { clearLayers(); } catch (Throwable ignored) { } } }, 700); }
            }
        }

        void setBoundsFromShape() {
            if (blur == null) return;
            blur.setBounds(Math.round(shape.left), Math.round(shape.top), Math.round(shape.right), Math.round(shape.bottom));
            float r = radius, b = openBottom ? 0 : radius;
            try { Xp.callMethod(blur, "setCornerRadius", r, r, b, b); }
            catch (Throwable t) { try { Xp.callMethod(blur, "setCornerRadius", r); } catch (Throwable ignored) { } }
        }

        @Override public void draw(Canvas canvas) {
            if (shape.isEmpty()) return;
            boolean blurOn = blurAllowed();
            if (blurOn) { makeBlur(); if (blur != null && blur.getBounds().isEmpty()) setBoundsFromShape(); }
            try { checkLook(); } catch (Throwable ignored) { }
            float k = seeThrough();
            if (k < 1f) decor.postInvalidateOnAnimation();
            if (blurOn && blur != null) {
                int col = toneNow(k);
                if (col != lastColor) { lastColor = col; try { Xp.callMethod(blur, "setColor", col); } catch (Throwable ignored) { } }
                blur.draw(canvas);
            }
            else {
                solid.setColor(tone | 0xff000000);
                float b = openBottom ? 0 : radius;
                android.graphics.Path p = new android.graphics.Path();
                p.addRoundRect(shape, new float[] { radius, radius, radius, radius, b, b, b, b }, android.graphics.Path.Direction.CW);
                canvas.drawPath(p, solid);
            }
            float extra = openBottom ? radius + 8 * d : 0;
            rim.draw(canvas, shape.left, shape.top, shape.right, shape.bottom + extra, radius, 255);
        }

        void clearLayers() {
            View panel = panel();
            if (panel == null || panel.getWidth() <= 0) return;
            walk(decor, Math.round(panel.getWidth() * 0.85f), false);
        }

        private void walk(View v, int wide, boolean inList) {
            String cls = v.getClass().getName();
            Drawable bg = v.getBackground();
            boolean nav = cls.endsWith("NavigationBarFrame");
            boolean big = !inList && v.getWidth() >= wide && v.getHeight() >= Math.round(8 * d);
            boolean key = cls.contains("SoftKeyView") || cls.endsWith("KeyView");
            if (bg != null && bg != this && (nav || (big && !key))) {
                boolean shows = bg instanceof ColorDrawable ? Color.alpha(((ColorDrawable) bg).getColor()) != 0 : safeAlpha(bg) != 0 && !Boolean.TRUE.equals(IGNORES.get(bg));
                if (!Boolean.TRUE.equals(CLEARED.get(bg)) || shows) {
                    try { Drawable m = bg.mutate(); m.setAlpha(0); if (m instanceof ColorDrawable) ((ColorDrawable) m).setColor(Color.TRANSPARENT); else if (safeAlpha(m) != 0) IGNORES.put(m, Boolean.TRUE); CLEARED.put(m, Boolean.TRUE); } catch (Throwable ignored) { }
                }
            }
            if (big && !key && v != decor) {
                float z = v.getZ();
                if (z > 0.01f && shadowOn(v)) noShadow(v);
            }
            if (v instanceof ImageView && big && !key) {
                ImageView iv = (ImageView) v;
                if (iv.getDrawable() != null && iv.getImageAlpha() != 0) iv.setImageAlpha(0);
            }
            if (key) return;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                boolean list = inList || isList(v);
                for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), wide, list);
            }
        }

        private static boolean isList(View v) {
            if (v instanceof android.widget.AbsListView || v instanceof android.widget.ScrollView) return true;
            for (Class<?> k = v.getClass(); k != null && k != View.class; k = k.getSuperclass()) if (k.getName().endsWith("RecyclerView")) return true;
            return false;
        }

        private static boolean shadowOn(View v) {
            try {
                int a = (Integer) View.class.getMethod("getOutlineAmbientShadowColor").invoke(v);
                int s = (Integer) View.class.getMethod("getOutlineSpotShadowColor").invoke(v);
                return Color.alpha(a) != 0 || Color.alpha(s) != 0;
            } catch (Throwable t) { return v.getElevation() != 0f || v.getTranslationZ() != 0f; }
        }
        private static void noShadow(View v) {
            try {
                View.class.getMethod("setOutlineAmbientShadowColor", int.class).invoke(v, Color.TRANSPARENT);
                View.class.getMethod("setOutlineSpotShadowColor", int.class).invoke(v, Color.TRANSPARENT);
            } catch (Throwable t) { v.setElevation(0f); v.setTranslationZ(0f); }
        }

        private static int safeAlpha(Drawable d) { try { return d.getAlpha(); } catch (Throwable t) { return -1; } }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
