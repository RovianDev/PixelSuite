package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

final class FeedPull extends View implements Choreographer.FrameCallback, BounceScroll.Pull {
    static final String OPEN_EXTRA = "io.github.pixelsuite.FEED_SEARCH";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final RectF field = new RectF();
    private final int[] at = new int[2];
    private final float density;
    private Bubble bubble;
    private PixelSearch.Look look;
    private float shown, speed, target, pop, popSpeed, k;
    private SearchOverlay made;
    private boolean holding, past, ticking, launching;
    private long frameAt;
    WidgetPanel.Panel panel;
    private float base, over, frost;
    private boolean handed;
    private static java.lang.reflect.Method sSet, sMake;

    FeedPull(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setTranslationZ(30 * density);
    }

    private float dp(float v) { return v * density; }

    private String said;

    private boolean say(String why, boolean answer) {
        if (!why.equals(said)) { said = why; }
        return answer;
    }

    @Override public boolean ready() {
        Context c = getContext();
        try {
            if (launching || given || handAt != 0 || SearchOverlay.showing()) return say("search open", false);
            WidgetPanel.Panel p = panel;
            if (p == null) return say("no feed", false);
            if (p.wiggle) return say("editing", false);
            if (p.progress < 0.95f) return say("feed not open", false);
            if (p.menu != null && p.menu.open) return say("menu open", false);
            if (!PixelSearch.feedOn(c)) return say("off", false);
            if (WidgetPanel.sLock && !PixelSearch.lockOn(c)) return say("off on the lock screen", false);
            if (WidgetPanel.sLock) wake(c);
            SearchOverlay.warm(c);
            if (!PatronGate.ok(c)) return say("no pass", false);
            if (look == null || !ticking) {
                look = new PixelSearch.Look(c);
                bubble = new Bubble(c, look);
            }
            return say(WidgetPanel.sLock ? "ready on lock screen" : "ready", true);
        } catch (Throwable t) { return say("error " + t.getClass().getSimpleName(), false); }
    }

    @Override public float reach() { return dp(96); }

    float shownNow() { return k; }

    @Override public void pulled(float by) {
        if (launching) return;
        holding = true;
        float f = by / reach();
        boolean beyond = f >= 1f;
        if (beyond != past) {
            past = beyond;
            if (beyond) { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); popSpeed = 7f; tick(); }
        }
        target = f;
        shown = f;
        speed = 0;
        if (!handed) { base = ease(clamp(f)); frost(); }
        if (made == null && by > dp(8)) make();
        tick();
        invalidate();
    }

    @Override public void released(boolean go) {
        holding = false;
        past = false;
        if (go && look != null && launch()) return;
        made = null;
        target = 0;
        tick();
    }

    private static final String[] WHILE_LOCKED = {"tools", "apps", "contacts", "settings", "web"};
    private static android.content.BroadcastReceiver watcher;

    static boolean locked(Context c) {
        if (!WidgetPanel.sLock) return false;
        try { android.app.KeyguardManager km = (android.app.KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE); return km != null && km.isKeyguardLocked(); }
        catch (Throwable t) { return false; }
    }

    static java.util.List<String> allowed(Context c, java.util.List<String> scopes) {
        if (!locked(c)) return scopes;
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        for (String s : scopes) for (String ok : WHILE_LOCKED) if (ok.equals(s)) { out.add(s); break; }
        return out;
    }

    static void unlocked(Context c, final Runnable then) {
        if (!locked(c)) { then.run(); return; }
        try {
            ClassLoader cl = c.getClassLoader();
            Object s = WidgetPanel.Lock.starter(cl);
            final Class<?> action = cl.loadClass("com.android.systemui.plugins.ActivityStarter$OnDismissAction");
            Object act = java.lang.reflect.Proxy.newProxyInstance(action.getClassLoader(), new Class<?>[]{action}, (p, m, a) -> {
                String n = m.getName();
                if ("onDismiss".equals(n)) { MAIN.post(then); return false; }
                if ("hashCode".equals(n)) return System.identityHashCode(p);
                if ("equals".equals(n)) return a != null && a.length == 1 && p == a[0];
                if ("toString".equals(n)) return "PixelSuiteSearch";
                Class<?> r = m.getReturnType();
                return r == boolean.class ? (Object) Boolean.FALSE : null;
            });
            if (s != null) for (java.lang.reflect.Method m : s.getClass().getMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (m.getName().equals("dismissKeyguardThenExecute") && t.length == 3 && t[0] == action && t[2] == boolean.class) { m.setAccessible(true); m.invoke(s, act, null, true); return; }
            }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "lock search: unlock", t); }
        then.run();
    }

    private static Object monitor;
    private static Swipes swipes;

    private static final class Swipes extends android.view.InputEventReceiver {
        Swipes(android.view.InputChannel channel, android.os.Looper looper) { super(channel, looper); }
        @Override public void onInputEvent(android.view.InputEvent e) {
            try { if (e instanceof android.view.MotionEvent) SearchOverlay.homeSwipe((android.view.MotionEvent) e); }
            catch (Throwable ignored) { }
            finally { finishInputEvent(e, false); }
        }
    }

    static void steal() {
        Object m = monitor;
        if (m == null) return;
        try { Xp.callMethod(m, "pilferPointers"); }
        catch (Throwable t) { android.util.Log.w("PixelSuite", "search: take swipe", t); }
    }

    static void listen(Context c, boolean on) {
        if (on && swipes == null) {
            Object m = null;
            try {
                m = Xp.callMethod(c.getSystemService(Context.INPUT_SERVICE), "monitorGestureInput", "PixelSuiteSearchSwipe", android.view.Display.DEFAULT_DISPLAY);
                swipes = new Swipes((android.view.InputChannel) Xp.callMethod(m, "getInputChannel"), android.os.Looper.getMainLooper());
                monitor = m;
            } catch (Throwable t) {
                android.util.Log.w("PixelSuite", "lock search: swipe monitor", t);
                try { if (m != null) Xp.callMethod(m, "dispose"); } catch (Throwable ignored) { }
            }
        } else if (!on && (swipes != null || monitor != null)) {
            Swipes r = swipes;
            Object m = monitor;
            swipes = null;
            monitor = null;
            try { if (r != null) r.dispose(); } catch (Throwable ignored) { }
            try { if (m != null) Xp.callMethod(m, "dispose"); } catch (Throwable ignored) { }
        }
    }

    static void watch(Context c, boolean on) {
        listen(c, on);
        try {
            Context app = c.getApplicationContext() == null ? c : c.getApplicationContext();
            if (on && watcher == null) {
                watcher = new android.content.BroadcastReceiver() { @Override public void onReceive(Context x, Intent i) { SearchOverlay.dismissNow(); } };
                android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF);
                f.addAction(Intent.ACTION_USER_PRESENT);
                app.registerReceiver(watcher, f);
            } else if (!on && watcher != null) {
                android.content.BroadcastReceiver w = watcher;
                watcher = null;
                app.unregisterReceiver(w);
            }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "lock search: watch", t); }
    }

    static final class Starts extends android.content.ContextWrapper {
        Starts(Context base) { super(base); }
        private boolean quiet(Intent i) {
            try { return i != null && i.getComponent() != null && "io.github.pixelsuite.SearchAction".equals(i.getComponent().getClassName()) && "call".equals(i.getStringExtra("act")); }
            catch (Throwable t) { return false; }
        }
        @Override public void startActivity(Intent i) { startActivity(i, null); }
        @Override public void startActivity(Intent i, android.os.Bundle b) {
            if (!locked(this) || quiet(i)) { super.startActivity(i, b); return; }
            WidgetPanel.Lock.start(getBaseContext(), i);
        }
    }

    private static long wokeAt;

    private static void wake(final Context c) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - wokeAt < 20000) return;
        wokeAt = now;
        new Thread(() -> {
            try { c.getContentResolver().call(android.net.Uri.parse("content://" + PixelSearch.AUTHORITY), "status", null, null); } catch (Throwable ignored) { }
        }, "PixelSuite-search-wake").start();
    }

    private void make() {
        try {
            SearchDock dock = WidgetPanel.sLock ? null : SearchDock.current();
            if (WidgetPanel.sLock) dock = new SearchDock(new Starts(getContext()));
            else if (dock == null || dock.getContext() != getContext()) dock = new SearchDock(getContext());
            made = SearchOverlay.prepare(dock, SearchOverlay.feedField(this), SearchOverlay.feedCard(getContext(), look), this);
        } catch (Throwable t) { made = null; }
    }

    private boolean launch() {
        try {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            launching = true;
            target = 1f;
            tick();
            if (made == null) make();
            final SearchOverlay o = made;
            made = null;
            handed = true;
            if (o == null || !SearchOverlay.openPrepared(o)) { launching = false; gone(); target = 0; tick(); }
            return true;
        } catch (Throwable t) {
            launching = false;
            gone();
            android.util.Log.w("PixelSuite", "feed pull: open", t);
            return false;
        }
    }

    static void openOnHome(Activity a) {
        try {
            if (!PixelSearch.feedOn(a) || !PatronGate.ok(a) || SearchOverlay.showing()) return;
            SearchDock dock = SearchDock.current();
            if (dock == null || dock.getContext() != a) dock = new SearchDock(a);
            View root = a.getWindow().getDecorView();
            PixelSearch.Look look = new PixelSearch.Look(a);
            SearchOverlay.openFrom(dock, SearchOverlay.feedField(root), SearchOverlay.feedCard(a, look), null);
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "feed pull: open after unlock", t); }
    }

    private float top = -1f;

    float fieldTop() { return top; }

    static final long HAND = 90;
    private long handAt;
    private boolean given;

    void handing(long at) { if (handAt == 0 && !given) { handAt = at; tick(); } }

    float handed(long now) { return given ? 1f : handAt == 0 ? 0f : clamp((now - handAt) / (float) HAND); }

    private boolean plain;

    void plain(boolean on) { if (plain != on) { plain = on; frost = -1f; frost(); } }

    void level(float f) {
        over = clamp(f);
        if (handed && over >= base) { base = 0; handed = false; }
        frost();
    }

    void gone() {
        yield(false);
        over = 0; base = 0; handed = false; frost();
        if (given || handAt != 0) { given = false; handAt = 0; launching = false; shown = target = speed = 0; k = 0; invalidate(); }
    }

    private void frost() {
        float f = Math.max(base, over);
        f = f < 0.25f ? Math.round(f * 48f) / 48f : Math.round(f * 12f) / 12f;
        if (f == frost) return;
        frost = f;
        wall(f);
        if (!(getParent() instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) getParent();
        try {
            if (sSet == null) {
                Class<?> fx = Class.forName("android.graphics.RenderEffect");
                sMake = fx.getMethod("createBlurEffect", float.class, float.class, android.graphics.Shader.TileMode.class);
                sSet = View.class.getMethod("setRenderEffect", fx);
            }
            float r = plain ? 0f : dp(28) * f;
            Object effect = f == 0 ? null : sMake.invoke(null, r, r, android.graphics.Shader.TileMode.CLAMP);
            for (int i = 0; i < g.getChildCount(); i++) { View v = g.getChildAt(i); if (v != this) sSet.invoke(v, effect); }
        } catch (Throwable ignored) { }
    }

    private int wallStep;

    private void veil(int radius) {
        try {
            WidgetPanel.Panel p = panel;
            if (p == null || p.bg == null || !(p.bg.getBackground() instanceof android.graphics.drawable.LayerDrawable)) return;
            android.graphics.drawable.Drawable glass = ((android.graphics.drawable.LayerDrawable) p.bg.getBackground()).getDrawable(0);
            if (glass == null || !glass.getClass().getName().contains("BackgroundBlur")) return;
            int rest = Math.round(FrostedSurface.depthBlurRadius(getContext()) * p.look.opacity / 100f);
            LauncherFeatures.call(glass, "setBlurRadius", Math.max(rest, radius));
        } catch (Throwable t) {
        }
    }

    private boolean wallOn, wallSaid;
    private static java.lang.reflect.Method sRoot, sLayer, sBlur, sApply;
    private static Class<?> sTx;

    private void wall(float f) {
        if (android.os.Build.VERSION.SDK_INT < 31) return;
        int step = Math.round(f * 48f);
        if (step > 0 && wallStep == 0) {
            Context c = getContext();
            wallOn = !WidgetPanel.sLock && c instanceof Activity && PixelSearch.number(c, PixelSearch.FROST_KEY, 1) == 1;
            android.view.ViewParent up = getParent();
            wallSaid = false;
        }
        if (plain || !wallOn) step = 0;
        if (step == wallStep) return;
        wallStep = step;
        veil(Math.round(dp(26) * step / 48f));
        try {
            if (sApply == null) {
                sRoot = View.class.getMethod("getViewRootImpl");
                sLayer = Class.forName("android.view.ViewRootImpl").getMethod("getSurfaceControl");
                Class<?> layer = Class.forName("android.view.SurfaceControl");
                sTx = Class.forName("android.view.SurfaceControl$Transaction");
                sBlur = sTx.getMethod("setBackgroundBlurRadius", layer, int.class);
                sApply = sTx.getMethod("apply");
            }
            Object root = sRoot.invoke(this);
            Object layer = root == null ? null : sLayer.invoke(root);
            if (layer == null || !Boolean.TRUE.equals(layer.getClass().getMethod("isValid").invoke(layer))) { wallStep = 0; return; }
            Object tx = sTx.getDeclaredConstructor().newInstance();
            sBlur.invoke(tx, layer, Math.round(dp(26) * step / 48f));
            sApply.invoke(tx);
            if (!wallSaid && step >= 24) { wallSaid = true; }
        } catch (Throwable t) {
            wallOn = false; wallStep = 0;
        }
    }

    private void tick() { if (!ticking) { ticking = true; frameAt = 0; Choreographer.getInstance().postFrameCallback(this); } }

    @Override public void doFrame(long nanos) {
        float dt = frameAt == 0 ? 1 / 60f : Math.min(.05f, (nanos - frameAt) / 1e9f);
        frameAt = nanos;
        if (!holding) {
            float[] r = WidgetPanel.spring(shown, speed, target, launching ? 520 : 380, launching ? .9f : .85f, dt);
            shown = r[0]; speed = r[1];
            if (!handed && !launching && !given) { base = ease(clamp(shown)); frost(); }
        }
        float[] q = WidgetPanel.spring(pop, popSpeed, 0, 520, .42f, dt); pop = q[0]; popSpeed = q[1];
        invalidate();
        if (handAt != 0 && android.os.SystemClock.uptimeMillis() - handAt >= HAND) { handAt = 0; given = true; launching = false; holding = false; }
        boolean still = handAt == 0 && (holding || (Math.abs(shown - target) < .002f && Math.abs(speed) < .01f)) && Math.abs(pop) < .002f && Math.abs(popSpeed) < .01f;
        if (still) {
            if (!holding) { shown = target; speed = 0; if (!handed && !launching && !given) { base = ease(clamp(shown)); frost(); } }
            pop = 0; popSpeed = 0; ticking = false; invalidate(); return;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float ease(float t) { t = clamp(t); return 1f - (1f - t) * (1f - t) * (1f - t); }

    private final int[] spot = new int[2];
    private boolean yielded;
    private final RectF start = new RectF();

    RectF origin() {
        WidgetPanel.Menu m = panel == null ? null : panel.menu;
        if (m == null || m.getWidth() == 0 || !m.isAttachedToWindow() || m.getVisibility() != VISIBLE || m.pill.isEmpty() || m.open || m.p > 0.01f || m.q > 0.01f) return null;
        m.getLocationOnScreen(spot);
        start.set(m.pill); start.offset(spot[0], spot[1]);
        return start;
    }

    int originCard() { return panel == null ? 0 : panel.look.card; }
    int originInk() { return panel == null ? 0 : panel.look.onCard; }

    void back() { base = 0; handed = false; frost(); }

    void giving(float upper) {
        WidgetPanel.Menu m = panel == null ? null : panel.menu;
        if (m == null || !yielded) return;
        float w = upper <= 0f ? 1f : lower(Color.alpha(panel.look.card) / 255f, upper);
        yielded = w < 1f;
        m.setAlpha(w);
    }

    static float lower(float a, float upper) {
        a = clamp(a); upper = clamp(upper);
        if (upper >= 1f) return 0f;
        if (a <= 0.001f) return 1f - upper;
        return clamp((1f - (1f - a) / (1f - a * upper)) / a);
    }

    float underLight() {
        if (panel == null) return -1f;
        int t = panel.look.text;
        float lum = (0.299f * Color.red(t) + 0.587f * Color.green(t) + 0.114f * Color.blue(t)) / 255f;
        return lum > 0.5f ? 0.12f : 0.9f;
    }

    private void yield(boolean on) {
        WidgetPanel.Menu m = panel == null ? null : panel.menu;
        if (m == null || yielded == on) return;
        yielded = on; m.setAlpha(on ? 0f : 1f);
    }

    @Override protected void onDraw(Canvas c) {
        k = ease((Math.max(0f, shown) - 0.08f) / 0.8f);
        if (!WidgetPanel.sLock) SearchDock.quietRim(given || handAt != 0 || k > 0.004f);
        if (!given && (k <= 0.004f || bubble == null)) yield(false);
        if (given || k <= 0.004f || bubble == null) return;
        getLocationOnScreen(at);
        RectF to = SearchOverlay.feedField(this);
        top = to.top;
        field.set(to.left - at[0], to.top - at[1], to.right - at[0], to.bottom - at[1]);
        RectF o = origin();
        if (o != null) { o.offset(-at[0], -at[1]); bubble.from(o, panel.look.card, panel.look.onCard); } else bubble.from(null, 0, 0);
        yield(o != null);
        bubble.place(field, k, pop, 0f);
        float h = handed(android.os.SystemClock.uptimeMillis());
        bubble.atop = h;
        bubble.ink = h > 0f ? 0f : -1f;
        bubble.draw(c, getLayoutDirection() == LAYOUT_DIRECTION_RTL, true, null, 1f - h, 1f);
    }

    static final class Bubble {
        final RectF rect = new RectF(), field = new RectF();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), words = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path clip = new android.graphics.Path();
        private final PixelSearch.Look look;
        private final float d, sp;
        private final Drawable mark;
        private final SearchGlyph more;
        private final String hint;
        private final int card;
        private float k, scale = 1f, lift, cutRoom = -1;
        private final RectF from = new RectF();
        private boolean morph;
        private float m;
        float atop = -1f;
        float ink = -1f;
        private int fromCard, fromInk;
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

        void from(RectF r, int cardColor, int ink) {
            if (r == null || r.isEmpty()) { morph = false; return; }
            from.set(r); fromCard = cardColor; fromInk = ink; morph = true;
        }
        private String cut, cutOf;

        Bubble(Context c, PixelSearch.Look look) {
            this.look = look;
            card = SearchOverlay.feedCard(c, look);
            d = c.getResources().getDisplayMetrics().density;
            sp = c.getResources().getDisplayMetrics().scaledDensity;
            mark = SearchOverlay.engineMark(c, look, PixelSearch.engine(c));
            more = new SearchGlyph(SearchGlyph.MORE, look.ink);
            hint = I18n.t("Search your phone and the web");
        }


        float opened() { return k; }

        void place(RectF f, float v, float pop, float stretch) {
            k = clamp(v);
            field.set(f);
            scale = (0.9f + 0.1f * k) * (1f + 0.025f * pop);
            lift = -(1f - k) * 18 * d;
            if (morph) {
                scale = 1f; lift = 0f;
                m = 1f - (float) Math.cbrt(1f - k);
                float wide = m * m * (3 - 2 * m), tall = clamp(m * 1.35f); tall = tall * tall * (3 - 2 * tall);
                float grow = 0.012f * pop * f.width() * m;
                rect.set(from.left + (f.left - from.left) * wide - grow, from.top + (f.top - from.top) * tall, from.right + (f.right - from.right) * wide + grow, from.bottom + (f.bottom - from.bottom) * tall);
                return;
            }
            float cx = f.centerX(), cy = f.centerY();
            rect.set(cx + (f.left - cx) * scale, cy + (f.top - cy) * scale + lift, cx + (f.right - cx) * scale, cy + (f.bottom - cy) * scale + lift);
        }

        boolean full() { return k >= 0.999f; }

        private void bloom(Canvas c, boolean rtl, boolean content, CharSequence typed, float mul, float inside) {
            float r = rect.height() / 2f;
            int tone = look.dark ? card : PixelSearch.mix(fromCard, card, m);
            int back = Math.round(Color.alpha(tone) * (atop >= 0f ? lower(Color.alpha(tone) / 255f, atop) : clamp(mul)));
            if (back > 0) { fill.setColor((tone & 0xffffff) | (back << 24)); c.drawRoundRect(rect, r, r, fill); }
            if (!content || inside <= 0.004f) return;
            float mid = field.centerY(), mc = rtl ? field.left + 28 * d : field.right - 28 * d;
            if (k >= 0.999f) {
                more.setBounds(Math.round(mc - 10 * d), Math.round(mid - 12 * d), Math.round(mc + 10 * d), Math.round(mid + 12 * d));
                more.draw(c);
            } else {
                float inset = from.width() / 2f + (28 * d - from.width() / 2f) * m;
                float x = rtl ? rect.left + inset : rect.right - inset, y = rect.centerY();
                float gap = (6.5f - 1.33f * m) * d, size = (1.9f - 0.32f * m) * d;
                dot.setColor(PixelSearch.mix(fromInk, look.ink, m));
                float da = Color.alpha(dot.getColor()) / 255f;
                dot.setAlpha(Math.round(255 * da * clamp(inside) * (ink >= 0f ? ink : atop >= 0f ? lower(da, atop) : clamp(mul))));
                for (int i = -1; i <= 1; i++) c.drawCircle(x, y + i * gap, size, dot);
            }
            float seen = clamp((m - 0.6f) / 0.4f); seen = seen * seen * (3 - 2 * seen) * clamp(inside) * (ink >= 0f ? ink : atop >= 0f ? lower(0.65f, atop) : clamp(mul));
            if (seen <= 0.004f) return;
            clip.reset(); clip.addRoundRect(rect, r, r, android.graphics.Path.Direction.CW);
            int save = seen >= 0.999f ? c.save() : c.saveLayerAlpha(rect.left, rect.top, rect.right, rect.bottom, Math.round(255 * seen));
            c.clipPath(clip);
            float slide = (1f - seen) * 6 * d * (rtl ? -1 : 1);
            c.translate(slide, rect.centerY() - mid);
            float ix = rtl ? field.right - 30 * d : field.left + 30 * d;
            if (mark != null) { mark.setBounds(Math.round(ix - 12 * d), Math.round(mid - 12 * d), Math.round(ix + 12 * d), Math.round(mid + 12 * d)); mark.draw(c); }
            boolean has = typed != null && typed.length() > 0;
            String text = has ? typed.toString() : hint;
            if (text != null) {
                words.setColor(has ? look.ink : (look.ink & 0xffffff) | 0xa6000000);
                words.setTextSize(17 * sp);
                words.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
                float tx = rtl ? field.right - 56 * d : field.left + 56 * d, room = field.width() - 104 * d;
                if (!text.equals(cutOf) || room != cutRoom) {
                    cutOf = text; cutRoom = room;
                    cut = words.measureText(text) > room ? android.text.TextUtils.ellipsize(text, new android.text.TextPaint(words), room, android.text.TextUtils.TruncateAt.END).toString() : text;
                }
                c.drawText(cut, tx, mid - (words.descent() + words.ascent()) / 2f, words);
            }
            c.restoreToCount(save);
        }

        void draw(Canvas c, boolean rtl, boolean content, CharSequence typed, float mul, float inside) {
            if (morph) { bloom(c, rtl, content, typed, mul, inside); return; }
            float k = this.k * clamp(inside);
            if (k <= 0.004f) return;
            float r = rect.height() / 2f;
            float ea = Color.alpha(card) / 255f * this.k;
            int back = Math.round(255 * ea * (atop >= 0f ? lower(ea, atop) : clamp(mul)));
            if (back > 0) { fill.setColor((card & 0xffffff) | (back << 24)); c.drawRoundRect(rect, r, r, fill); }
            if (!content) return;
            if (ink >= 0f) k *= ink;
            if (k <= 0.004f) return;
            int save = k >= 0.999f ? c.save() : c.saveLayerAlpha(rect.left, rect.top, rect.right, rect.bottom, Math.round(255 * k));
            float cx = field.centerX(), cy = field.centerY(), mid = cy;
            c.translate(cx, cy + lift);
            c.scale(scale, scale);
            c.translate(-cx, -cy);
            float ix = rtl ? field.right - 30 * d : field.left + 30 * d;
            if (mark != null) { mark.setBounds(Math.round(ix - 12 * d), Math.round(mid - 12 * d), Math.round(ix + 12 * d), Math.round(mid + 12 * d)); mark.draw(c); }
            float mc = rtl ? field.left + 28 * d : field.right - 28 * d;
            more.setBounds(Math.round(mc - 10 * d), Math.round(mid - 12 * d), Math.round(mc + 10 * d), Math.round(mid + 12 * d));
            more.draw(c);
            boolean has = typed != null && typed.length() > 0;
            String text = has ? typed.toString() : hint;
            if (text != null) {
                words.setColor(has ? look.ink : (look.ink & 0xffffff) | 0xa6000000);
                words.setTextSize(17 * sp);
                words.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
                float tx = rtl ? field.right - 56 * d : field.left + 56 * d, room = field.width() - 104 * d;
                if (!text.equals(cutOf) || room != cutRoom) {
                    cutOf = text; cutRoom = room;
                    cut = words.measureText(text) > room ? android.text.TextUtils.ellipsize(text, new android.text.TextPaint(words), room, android.text.TextUtils.TruncateAt.END).toString() : text;
                }
                c.drawText(cut, tx, mid - (words.descent() + words.ascent()) / 2f, words);
            }
            c.restoreToCount(save);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (wallStep != 0) { boolean was = plain; plain = true; wall(0); plain = was; }
        super.onDetachedFromWindow();
        if (ticking) { Choreographer.getInstance().removeFrameCallback(this); ticking = false; }
        if (WidgetPanel.sLock) SearchOverlay.dismissNow(); else SearchDock.quietRim(false);
        yield(false);
        gone();
        made = null;
        shown = target = speed = pop = popSpeed = k = 0; holding = past = launching = false;
    }
}
