package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class FeedPreview extends View implements Choreographer.FrameCallback {
    static final int HOME = 0, LOCK = 1;

    private static final Set<FeedPreview> SHOWN = Collections.newSetFromMap(new WeakHashMap<FeedPreview, Boolean>());

    static void live(String key, int value) {
        for (FeedPreview p : new ArrayList<FeedPreview>(SHOWN)) p.changed(key, value);
    }

    static boolean systemRtl() {
        try { return Resources.getSystem().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; } catch (Throwable t) { return false; }
    }

    private static final int GOOGLE = 0, PAGE = 1, FEED = 2, LOCKSCREEN = 3;
    private static final int HOLD = 0, GO = 1, SWIPE = 2, TAP = 3, OVERVIEW = 4, LOOK = 5, DRAG = 6;
    private static final float SCREEN_W = 100f, SCREEN_H = 210f, CORNER = 10f;
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private final int mode;
    private final int ink, muted, pageBg;
    private final boolean pageDark;
    private final float dp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Rect seen = new Rect();
    private final Path path = new Path();

    private boolean on, other, keepGoogle, noHeader, openUnlocked, sysRtl;
    private int opacity = 100, pages = 3;
    private final Map<String, Long> liveAt = new HashMap<String, Long>();
    private final Map<String, Integer> liveValue = new HashMap<String, Integer>();

    private float opShown = 1f, headerShown = 1f;
    private float cam, camFrom, camTo, zoom = 1f, zoomFrom = 1f, zoomTo = 1f;
    private long camAt, camMs, zoomAt, zoomMs;
    private boolean finger;
    private long tapAt, tapMs;
    private int slots;
    private final LinkedHashMap<String, Item> items = new LinkedHashMap<String, Item>();

    private final ArrayDeque<Step> plan = new ArrayDeque<Step>();
    private Step step;
    private long stepAt, quietAt;

    private boolean running, still;
    private long lastFrame;

    private float clockMs;
    private long clock() { return (long) clockMs; }
    private Look look;
    private String time = "12:30", date = "", temp = "24°";
    private long textsAt;
    private final String googleLabel, homeLabel, feedLabel, lockLabel;

    private static final class Item {
        final String id; final int kind, page;
        float pos, from, to, presence, want = 1f;
        long moveAt, moveMs; boolean fly;
        Item(String id) {
            this.id = id;
            kind = "G".equals(id) ? GOOGLE : "F".equals(id) ? FEED : "L".equals(id) ? LOCKSCREEN : PAGE;
            page = kind == PAGE ? id.charAt(1) - '0' : 0;
        }
    }

    private static final class Step {
        final int type; final String target; final long ms;
        Step(int type, String target, long ms) { this.type = type; this.target = target; this.ms = ms; }
    }

    FeedPreview(Context c, int mode, int ink, int muted, int pageBg, boolean pageDark) {
        super(c);
        this.mode = mode; this.ink = ink; this.muted = muted; this.pageBg = pageBg; this.pageDark = pageDark;
        dp = c.getResources().getDisplayMetrics().density;
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        googleLabel = I18n.cs("Google feed").toString();
        homeLabel = I18n.cs("Home screen").toString();
        feedLabel = I18n.cs("Widgets feed").toString();
        lockLabel = I18n.cs("Lock screen").toString();
        setContentDescription(I18n.cs("A live preview of your feeds. It follows your settings."));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(I18n.medium());
        sysRtl = systemRtl();
        still = animationsOff(c);
        load();
        arrange(false);
        cam = slotOf(home());
        look = new Look(c, mode == LOCK);
        opShown = opacity / 100f; headerShown = noHeader ? 0f : 1f;
        if (still) showAll();
    }

    private String[] keys() {
        return mode == HOME
                ? new String[]{WidgetPanel.KEY, WidgetPanel.SIDE_KEY, WidgetPanel.NO_GOOGLE_KEY, WidgetPanel.HEADER_KEY, WidgetPanel.OPACITY_KEY, WidgetPanel.PAGES_KEY, SurfaceStyle.KEY, SuperDark.KEY}
                : new String[]{WidgetPanel.LOCK_KEY, WidgetPanel.LOCK_SIDE_KEY, WidgetPanel.LOCK_HEADER_KEY, WidgetPanel.LOCK_OPACITY_KEY, WidgetPanel.LOCK_OPEN_KEY, SurfaceStyle.KEY, SuperDark.KEY};
    }

    private boolean mine(String key) { for (String k : keys()) if (k.equals(key)) return true; return false; }

    private int read(String key, int def) {
        Long at = liveAt.get(key);
        if (at != null && SystemClock.uptimeMillis() - at < 1500) return liveValue.get(key);
        try { return Settings.Secure.getInt(getContext().getContentResolver(), key, def); } catch (Throwable t) { return def; }
    }

    private void load() {
        boolean home = mode == HOME;

        boolean demo = false;
        try { demo = !PatronAccess.allowed(getContext(), home ? WidgetPanel.KEY : WidgetPanel.LOCK_KEY); } catch (Throwable ignored) { }
        on = demo || read(home ? WidgetPanel.KEY : WidgetPanel.LOCK_KEY, 0) == 1;
        other = read(home ? WidgetPanel.SIDE_KEY : WidgetPanel.LOCK_SIDE_KEY, 0) == 1;
        keepGoogle = !home || read(WidgetPanel.NO_GOOGLE_KEY, 0) == 0;
        noHeader = read(home ? WidgetPanel.HEADER_KEY : WidgetPanel.LOCK_HEADER_KEY, 0) == 1;
        opacity = Math.max(1, Math.min(100, read(home ? WidgetPanel.OPACITY_KEY : WidgetPanel.LOCK_OPACITY_KEY, 100)));
        openUnlocked = !home && read(WidgetPanel.LOCK_OPEN_KEY, 0) == 1;

        pages = Math.max(1, Math.min(MAX_PAGES, read(WidgetPanel.PAGES_KEY, 3)));
    }

    private static final int MAX_PAGES = 6;

    private List<String> order() {
        ArrayList<String> l = new ArrayList<String>();
        if (mode == HOME) {
            if (on && !other) l.add("F"); else if (!on || keepGoogle) l.add("G");
            for (int i = 1; i <= pages; i++) l.add("P" + i);
            if (on && other) l.add("F");
            if (sysRtl) Collections.reverse(l);
        } else {
            boolean right = sysRtl == other;
            l.add("L");
            if (on) { if (right) l.add("F"); else l.add(0, "F"); }
        }
        return l;
    }

    private String home() { return mode == HOME ? "P1" : "L"; }

    private void changed(String key, int value) {
        if (!mine(key)) return;
        liveAt.put(key, SystemClock.uptimeMillis());
        liveValue.put(key, value);
        react(key);

        main.removeCallbacks(settle);
        main.postDelayed(settle, 1600);
    }

    private final Runnable settle = new Runnable() { @Override public void run() { react(null); } };

    private void react(String key) {
        if (SurfaceStyle.KEY.equals(key) || SuperDark.KEY.equals(key)) {
            try { SurfaceStyle.refresh(); } catch (Throwable ignored) { }
            look = new Look(getContext(), mode == LOCK);
            invalidate();
            return;
        }
        List<String> before = order();
        boolean header = noHeader, open = openUnlocked; int op = opacity;
        load();
        List<String> after = order();
        if (still) { arrange(false); showAll(); opShown = opacity / 100f; headerShown = noHeader ? 0f : 1f; invalidate(); return; }
        if (!before.equals(after)) { arrange(true); structure(); }
        else if (header != noHeader || op != opacity) focusLook();
        else if (open != openUnlocked && openUnlocked) tapDemo();
        kick();
        invalidate();
    }

    private final ContentObserver watch = new ContentObserver(main) {
        @Override public void onChange(boolean self, Uri uri) {
            String key = uri == null ? null : uri.getLastPathSegment();
            if (key != null && liveAt.containsKey(key) && SystemClock.uptimeMillis() - liveAt.get(key) < 1500) return;
            react(key);
        }
    };

    private static boolean animationsOff(Context c) {
        try { return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f; } catch (Throwable t) { return false; }
    }

    private void arrange(boolean animate) {
        List<String> now = order();
        long t = clock();
        for (Item it : items.values()) it.want = 0f;
        for (int i = 0; i < now.size(); i++) {
            String id = now.get(i);
            Item it = items.get(id);
            if (it == null || it.presence < 0.02f) {
                if (it == null) { it = new Item(id); items.put(id, it); }
                it.pos = it.from = it.to = i; it.moveMs = 0; it.fly = false;
                it.presence = animate ? 0f : 1f;
            } else if (it.to != i) {
                if (animate) { it.from = it.pos; it.to = i; it.moveAt = t; it.fly = Math.abs(i - it.pos) > 1.5f; it.moveMs = it.fly ? 1050 : 600; }
                else { it.pos = it.from = it.to = i; it.moveMs = 0; }
            }
            it.want = 1f;
        }
        if (!animate) for (Iterator<Item> i = items.values().iterator(); i.hasNext(); ) { Item it = i.next(); if (it.want == 0f) i.remove(); else it.presence = 1f; }
        slots = now.size();
    }

    private Item item(String id) { Item it = items.get(id); return it != null && it.want > 0f ? it : null; }

    private float slotOf(String id) { Item it = item(id); if (it == null) it = item(home()); return it == null ? 0f : it.to; }

    private float fitZoom() {
        float w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        float pw = phoneWidth(), gap = 18 * dp;
        float need = slots * (pw + gap) - gap;
        return Math.max(0.3f, Math.min(0.94f, (w - 20 * dp) / need));
    }

    private void showAll() { zoom = zoomTo = fitZoom(); cam = camTo = (slots - 1) / 2f; camMs = zoomMs = 0; }

    private void structure() {
        plan.clear(); step = null;
        plan.add(new Step(OVERVIEW, null, 2400));
        plan.add(new Step(GO, item("F") != null ? "F" : home(), 650));
        plan.add(new Step(HOLD, null, 900));
    }

    private void focusLook() {
        if (item("F") == null) return;
        quietAt = clock();
        if (step != null && step.type == LOOK) return;

        if (step != null && step.type == GO && "F".equals(step.target) && !plan.isEmpty() && plan.peek().type == LOOK) return;
        plan.clear(); step = null;
        plan.add(new Step(GO, "F", 520));
        plan.add(new Step(LOOK, null, 0));
    }

    private void tapDemo() {
        if (item("F") == null) return;
        plan.clear(); step = null;
        plan.add(new Step(GO, "F", 520));
        plan.add(new Step(TAP, null, 2300));
        plan.add(new Step(HOLD, null, 500));
    }

    private void demo() {
        String home = home();
        if (Math.abs(cam - slotOf(home)) > 0.01f || zoom < 0.999f) plan.add(new Step(GO, home, 700));
        plan.add(new Step(HOLD, null, 1300));
        Item f = item("F");
        if (mode == LOCK) {
            if (f != null) {
                plan.add(new Step(SWIPE, "F", 650)); plan.add(new Step(HOLD, null, 700));
                plan.add(new Step(TAP, null, 2300)); plan.add(new Step(HOLD, null, 500));
                plan.add(new Step(SWIPE, "L", 600));
            }
            return;
        }
        if (f != null) {
            if (Math.abs(f.to - slotOf("P1")) < 1.5f) {
                plan.add(new Step(SWIPE, "F", 650)); plan.add(new Step(HOLD, null, 2000)); plan.add(new Step(SWIPE, "P1", 600));
            } else {
                long step = pages > 3 ? 340 : 430;
                for (int i = 2; i <= pages; i++) plan.add(new Step(SWIPE, "P" + i, step));
                plan.add(new Step(SWIPE, "F", 620));
                plan.add(new Step(HOLD, null, 2000)); plan.add(new Step(GO, "P1", 900));
            }
        }
        if (item("G") != null) {
            plan.add(new Step(HOLD, null, 700)); plan.add(new Step(SWIPE, "G", 650));
            plan.add(new Step(HOLD, null, 1400)); plan.add(new Step(SWIPE, "P1", 600));
        }
    }

    private void advance(long now) {
        for (int guard = 0; guard < 8; guard++) {
            if (step != null) {
                if (step.type == DRAG) return;
                long end = step.type == LOOK ? quietAt + 2600 : stepAt + step.ms;
                if (now < end) return;
            }
            if (plan.isEmpty()) demo();
            step = plan.poll();
            stepAt = now;
            if (step == null) return;
            begin(step, now);
        }
    }

    private void begin(Step s, long now) {
        switch (s.type) {
            case GO: case SWIPE:
                camGo(slotOf(s.target), s.ms, s.type == SWIPE, now);
                zoomGo(1f, s.ms, now);
                break;
            case OVERVIEW:
                zoomGo(fitZoom(), 620, now);
                camGo((slots - 1) / 2f, 620, false, now);
                break;
            case TAP:
                tapAt = now; tapMs = s.ms;
                break;
            default: break;
        }
    }

    private void camGo(float to, long ms, boolean withFinger, long now) { camFrom = cam; camTo = to; camAt = now; camMs = Math.max(1, ms); finger = withFinger; }

    private void zoomGo(float to, long ms, long now) { if (Math.abs(zoom - to) < 0.001f && zoomMs == 0) return; zoomFrom = zoom; zoomTo = to; zoomAt = now; zoomMs = Math.max(1, ms); }

    private static float ease(float k) { k = Math.max(0f, Math.min(1f, k)); return k < 0.5f ? 4 * k * k * k : 1 - (float) Math.pow(-2 * k + 2, 3) / 2; }

    private static float easeOut(float k) { k = Math.max(0f, Math.min(1f, k)); return 1 - (1 - k) * (1 - k) * (1 - k); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        SHOWN.add(this);
        for (String key : keys()) {
            try { getContext().getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, watch); } catch (Throwable ignored) { }
        }
        getViewTreeObserver().addOnScrollChangedListener(scrolled);
        react(null);
        kick();
    }

    @Override protected void onDetachedFromWindow() {
        SHOWN.remove(this);
        try { getContext().getContentResolver().unregisterContentObserver(watch); } catch (Throwable ignored) { }
        getViewTreeObserver().removeOnScrollChangedListener(scrolled);
        main.removeCallbacks(settle);
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    private final ViewTreeObserver.OnScrollChangedListener scrolled = new ViewTreeObserver.OnScrollChangedListener() {
        @Override public void onScrollChanged() { kick(); }
    };

    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); kick(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); if (still) { showAll(); invalidate(); } }

    private boolean canRun() {
        return !still && getWindowVisibility() == View.VISIBLE && isShown() && getWindowToken() != null && getGlobalVisibleRect(seen);
    }

    private void kick() {
        if (running || !canRun()) return;
        running = true; lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long nanos) {
        if (!canRun()) { running = false; return; }
        float dt = lastFrame == 0 ? 1f / 60f : Math.min(0.05f, (nanos - lastFrame) / 1e9f);
        lastFrame = nanos;
        clockMs += dt * 1000f;
        long now = clock();
        advance(now);
        if (camMs > 0) { float k = (now - camAt) / (float) camMs; cam = camFrom + (camTo - camFrom) * ease(k); if (k >= 1f) { cam = camTo; camMs = 0; finger = false; } }
        if (zoomMs > 0) { float k = (now - zoomAt) / (float) zoomMs; zoom = zoomFrom + (zoomTo - zoomFrom) * ease(k); if (k >= 1f) { zoom = zoomTo; zoomMs = 0; } }
        for (Iterator<Item> i = items.values().iterator(); i.hasNext(); ) {
            Item it = i.next();
            if (it.moveMs > 0) { float k = (now - it.moveAt) / (float) it.moveMs; it.pos = it.from + (it.to - it.from) * ease(k); if (k >= 1f) { it.pos = it.to; it.moveMs = 0; it.fly = false; } }
            it.presence += (it.want - it.presence) * (1f - (float) Math.exp(-dt * 9f));
            if (it.want == 0f && it.presence < 0.01f) i.remove();
        }
        opShown += (opacity / 100f - opShown) * (1f - (float) Math.exp(-dt * 20f));
        headerShown += ((noHeader ? 0f : 1f) - headerShown) * (1f - (float) Math.exp(-dt * 11f));
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private float downX, downY, downCam; private boolean dragging; private VelocityTracker velocity;

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (still) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); downCam = cam; dragging = false;
                velocity = VelocityTracker.obtain(); velocity.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (!dragging) {
                    if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) return false;
                    if (Math.abs(dx) > slop) {
                        dragging = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        plan.clear(); step = new Step(DRAG, null, 0); camMs = 0; finger = false;
                        zoomGo(1f, 260, clock());
                        downX = e.getX(); downCam = cam; dx = 0;
                        kick();
                    }
                }
                if (dragging) {
                    float pitch = phoneWidth() + 18 * dp;
                    cam = Math.max(-0.35f, Math.min(slots - 0.65f, downCam - dx / pitch));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    float v = 0f;
                    if (velocity != null) { velocity.addMovement(e); velocity.computeCurrentVelocity(1000); v = velocity.getXVelocity(); }
                    float target = Math.round(cam);
                    if (Math.abs(v) > 600 * dp) target = v < 0 ? (float) Math.ceil(cam) : (float) Math.floor(cam);
                    target = Math.max(0, Math.min(slots - 1, target));
                    long now = clock();
                    camGo(target, 360, false, now);
                    if ((int) target != Math.round(downCam)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    step = null; plan.clear();
                    plan.add(new Step(HOLD, null, 3400));
                    kick();
                }
                dragging = false;
                if (velocity != null) { velocity.recycle(); velocity = null; }
                return true;
            }
            default: return true;
        }
    }

    private float phoneWidth() {
        float h = getHeight() > 0 ? getHeight() : 318 * dp;
        float w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        float strip = h - 30 * dp - 8 * dp;
        return Math.min(strip / 2.1f, w * 0.40f);
    }

    @Override protected void onDraw(Canvas c) {
        long now = clock();
        long wall = SystemClock.uptimeMillis();
        if (textsAt == 0 || wall - textsAt > 20000) texts(wall);
        float w = getWidth(), h = getHeight();
        float pw = phoneWidth(), ph = pw * 2.1f, gap = 18 * dp;
        float top = 8 * dp, stripH = h - 30 * dp - top;
        float cx = w / 2f, cy = top + stripH / 2f;
        float pitch = (pw + gap) * zoom;
        float fit = fitZoom();
        float ov = fit >= 0.999f ? 0f : Math.max(0f, Math.min(1f, (1f - zoom) / (1f - fit)));

        ArrayList<Item> list = new ArrayList<Item>(items.values());

        final float at = cam;
        Collections.sort(list, new java.util.Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                float da = a.fly ? -1 : Math.abs(a.pos - at), db = b.fly ? -1 : Math.abs(b.pos - at);
                return Float.compare(db, da);
            }
        });
        long nowMs = now;
        for (Item it : list) {
            if (it.presence < 0.01f) continue;
            float lift = 0f;
            if (it.fly && it.moveMs > 0) lift = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, (nowMs - it.moveAt) / (float) it.moveMs)));
            float d0 = Math.min(1f, Math.abs(it.pos - cam)), d = d0 * d0 * (3f - 2f * d0);
            float s = zoom * (1f - 0.1f * d * (1f - ov)) * (0.86f + 0.14f * it.presence) * (1f + 0.07f * lift);
            float dim = it.presence * (1f - 0.42f * d * (1f - ov));
            float x = cx + (it.pos - cam) * pitch;
            float iw = pw * s, ih = ph * s;
            float y = cy - lift * 0.13f * ph * zoom;
            box.set(x - iw / 2f, y - ih / 2f, x + iw / 2f, y + ih / 2f);
            if (box.right < -iw * 0.2f || box.left > w + iw * 0.2f) continue;
            if (lift > 0.01f) shadow(c, box, lift);
            screen(c, it, box, Math.max(0f, Math.min(1f, dim)), now);
            itemLabel(c, it, x, cy + ih / 2f + 17 * dp, d, ov, pitch, pw);
        }
        if (ov < 0.99f) frame(c, cx, cy, pw * zoom, ph * zoom, 1f - ov);
        if (finger && camMs > 0) fingerDot(c, cx, cy, pw * zoom, ph * zoom, now);
        if (!running) kick();
    }

    private void texts(long now) {
        textsAt = now;
        Date d = new Date();
        try {
            java.util.Locale l = I18n.shownLocale();
            boolean h24 = android.text.format.DateFormat.is24HourFormat(getContext());
            time = new java.text.SimpleDateFormat(h24 ? "H:mm" : "h:mm", l).format(d);
            date = new java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(l, "EEEMMMd"), l).format(d);
            temp = String.format(l, "%d°", 24);
        } catch (Throwable t) { time = "12:30"; date = ""; }
    }

    private void shadow(Canvas c, RectF r, float lift) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        float rad = r.width() * CORNER / SCREEN_W;
        for (int i = 3; i >= 1; i--) {
            paint.setColor(Color.argb(Math.round(18 * lift), 0, 0, 0));
            float o = i * 3 * dp * lift;
            box2.set(r.left - o * 0.4f, r.top + o * 0.6f, r.right + o * 0.4f, r.bottom + o * 1.2f);
            c.drawRoundRect(box2, rad + o, rad + o, paint);
        }
    }

    private final RectF box2 = new RectF();

    private void itemLabel(Canvas c, Item it, float x, float y, float d, float ov, float pitch, float pw) {
        String text;
        if (it.kind == GOOGLE) text = googleLabel;
        else if (it.kind == FEED) text = feedLabel;
        else if (it.kind == LOCKSCREEN) text = lockLabel;
        else if (it.page == 1) text = homeLabel;
        else return;
        float a = it.presence * (1f - 0.45f * d * (1f - ov));
        if (a <= 0.02f) return;
        float size = 12.5f * getResources().getDisplayMetrics().scaledDensity * (0.86f + 0.14f * (1f - ov));
        label.setTextSize(size);
        float max = Math.max(pw * 0.6f, Math.min(pitch, pw * 1.25f) * 0.96f);
        float width = label.measureText(text);
        if (width > max) label.setTextSize(size * max / width);
        label.setColor(d < 0.5f && ov < 0.5f ? ink : muted);
        label.setAlpha(Math.round(255 * a * (Color.alpha(label.getColor()) / 255f)));
        c.drawText(text, x, y, label);
    }

    private void frame(Canvas c, float cx, float cy, float w, float h, float a) {
        paint.setShader(null);
        float rad = w * CORNER / SCREEN_W;
        float b = 3.2f * dp * Math.max(0.6f, zoom);
        box.set(cx - w / 2f - b / 2f, cy - h / 2f - b / 2f, cx + w / 2f + b / 2f, cy + h / 2f + b / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(b);
        paint.setColor(fade(pageDark ? 0xff4a4b52 : 0xff202125, a));
        c.drawRoundRect(box, rad + b / 2f, rad + b / 2f, paint);
        paint.setStrokeWidth(Math.max(1f, 0.8f * dp));
        paint.setColor(fade(pageDark ? 0x40ffffff : 0x26000000, a));
        box.inset(-b / 2f, -b / 2f);
        c.drawRoundRect(box, rad + b, rad + b, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fade(0xff0b0b0c, a));
        c.drawCircle(cx, cy - h / 2f + w * 0.055f, Math.max(1.2f * dp, w * 0.022f), paint);
    }

    private static int fade(int color, float a) { return (color & 0xffffff) | (Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, a))) << 24); }

    private void fingerDot(Canvas c, float cx, float cy, float w, float h, long now) {
        float k = Math.max(0f, Math.min(1f, (now - camAt) / (float) camMs));
        float dir = camTo > camFrom ? 1f : -1f;
        float a = Math.min(1f, k / 0.15f) * Math.min(1f, (1f - k) / 0.25f);
        if (a <= 0f) return;
        float y = cy + h * 0.12f;
        float r = 9.5f * dp * Math.max(0.7f, zoom);
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        for (int i = 3; i >= 1; i--) {
            float kk = Math.max(0f, ease(k) - i * 0.06f);
            float x = cx + dir * (0.3f - 0.6f * kk) * w;
            paint.setColor(Color.argb(Math.round(40 * a / i), 255, 255, 255));
            c.drawCircle(x, y, r * (1f - i * 0.12f), paint);
        }
        float x = cx + dir * (0.3f - 0.6f * ease(k)) * w;
        paint.setColor(Color.argb(Math.round(170 * a), 255, 255, 255));
        c.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f * dp);
        paint.setColor(Color.argb(Math.round(70 * a), 0, 0, 0));
        c.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void screen(Canvas c, Item it, RectF r, float dim, long now) {
        int save = c.save();
        c.translate(r.left, r.top);
        float u = r.width() / SCREEN_W;
        c.scale(u, u);
        path.reset();
        box2.set(0, 0, SCREEN_W, SCREEN_H);
        path.addRoundRect(box2, CORNER, CORNER, Path.Direction.CW);
        c.clipPath(path);
        switch (it.kind) {
            case GOOGLE: google(c); break;
            case FEED: feed(c, now); break;
            case LOCKSCREEN: lockScreen(c); break;
            default: homePage(c, it.page); break;
        }
        c.restoreToCount(save);
        paint.setShader(null);
        float rad = r.width() * CORNER / SCREEN_W;
        if (dim < 0.995f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((pageBg & 0xffffff) | (Math.round(255 * (1f - dim)) << 24));
            c.drawRoundRect(r, rad, rad, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 0.7f * dp));
        paint.setColor(pageDark ? 0x24ffffff : 0x1c000000);
        c.drawRoundRect(r, rad, rad, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void fill(Canvas c, int color, float l, float t, float r, float b, float rad) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
        box2.set(l, t, r, b);
        c.drawRoundRect(box2, rad, rad, paint);
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

    private static final int LEAK_BASE = 0xff060609;
    private static final int[] LEAK_RGB = {0x7846ff, 0xff7896, 0xffb05a};
    private static final float[] LEAK_Y = {-20.5f, 1.3f, 18.3f};
    private static final float[] LEAK_EXTENT = {50.5f, 44.4f, 42f}, LEAK_EXTENT_SOFT = {78.3f, 72.2f, 69.8f};
    private static final int[][] LEAK_ALPHA = {{1, 4, 17, 44, 77, 92, 77, 44, 17, 4, 1}, {0, 2, 9, 23, 40, 48, 40, 23, 9, 2, 0}, {0, 2, 7, 16, 28, 33, 28, 16, 7, 2, 0}};
    private static final int[][] LEAK_ALPHA_SOFT = {{0, 3, 9, 24, 41, 50, 41, 24, 9, 3, 0}, {0, 2, 5, 12, 20, 24, 20, 12, 5, 2, 0}, {0, 1, 4, 8, 14, 16, 14, 8, 4, 1, 0}};
    private static Shader[] leakBeams, leakBeamsSoft;

    private static Shader[] leakBeams(boolean soft) {
        Shader[] made = soft ? leakBeamsSoft : leakBeams;
        if (made != null) return made;
        made = new Shader[3];
        float[] at = new float[11];
        for (int i = 0; i < 11; i++) at[i] = i / 10f;
        for (int b = 0; b < 3; b++) {
            float cy = SCREEN_H / 2f + LEAK_Y[b], e = (soft ? LEAK_EXTENT_SOFT : LEAK_EXTENT)[b];
            int[] colors = new int[11];
            for (int i = 0; i < 11; i++) colors[i] = ((soft ? LEAK_ALPHA_SOFT : LEAK_ALPHA)[b][i] << 24) | LEAK_RGB[b];
            made[b] = new LinearGradient(0, cy - e, 0, cy + e, colors, at, Shader.TileMode.CLAMP);
        }
        if (soft) leakBeamsSoft = made; else leakBeams = made;
        return made;
    }

    private void wallpaper(Canvas c, boolean soft) { drawWallpaper(c, paint, soft); }

    static void drawWallpaper(Canvas c, Paint paint, boolean soft) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(LEAK_BASE);
        c.drawRect(0, 0, SCREEN_W, SCREEN_H, paint);
        paint.setColor(Color.BLACK);
        int saved = c.save();
        c.clipRect(0, 0, SCREEN_W, SCREEN_H);
        c.rotate(-32f, SCREEN_W / 2f, SCREEN_H / 2f);
        Shader[] beams = leakBeams(soft);
        for (int b = 0; b < 3; b++) {
            float cy = SCREEN_H / 2f + LEAK_Y[b], e = (soft ? LEAK_EXTENT_SOFT : LEAK_EXTENT)[b];
            paint.setShader(beams[b]);
            c.drawRect(-SCREEN_H, cy - e, SCREEN_W + SCREEN_H, cy + e, paint);
        }
        c.restoreToCount(saved);
        paint.setShader(null);
    }

    private void statusBar(Canvas c, int color, boolean lock) {
        if (!lock) text(c, time, 8, 9.5f, 6.2f, color, Typeface.DEFAULT_BOLD, Paint.Align.LEFT);
        else padlock(c, 50, 13.6f, 0.9f, color);
        fill(c, color, 80, 5.4f, 89, 9.6f, 1.2f);
        dot(c, color, 76, 7.5f, 1.6f);
        dot(c, color, 72, 7.5f, 1.2f);
    }

    private void padlock(Canvas c, float x, float y, float s, int color) {
        paint.setShader(null);
        fill(c, color, x - 2.6f * s, y - 0.6f * s, x + 2.6f * s, y + 3.2f * s, 0.7f * s);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.9f * s); paint.setColor(color);
        box2.set(x - 1.6f * s, y - 3.6f * s, x + 1.6f * s, y + 0.4f * s);
        c.drawArc(box2, 180, 180, false, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void sun(Canvas c, float x, float y, float r, int color) {
        dot(c, color, x, y, r);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(r * 0.35f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(color);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4;
            c.drawLine(x + (float) Math.cos(a) * r * 1.5f, y + (float) Math.sin(a) * r * 1.5f, x + (float) Math.cos(a) * r * 2f, y + (float) Math.sin(a) * r * 2f, paint);
        }
        paint.setStyle(Paint.Style.FILL); paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private int onWall() { return look.wallLight ? 0xff1b1b1f : 0xffffffff; }

    private void icon(Canvas c, float x, float y, int i) {
        dot(c, look.iconBg, x, y, 6.2f);
        int fg = look.iconFg;
        switch (i % 4) {
            case 0: dot(c, fg, x, y, 2.5f); break;
            case 1: fill(c, fg, x - 2.4f, y - 2.4f, x + 2.4f, y + 2.4f, 0.9f); break;
            case 2: fill(c, fg, x - 3f, y - 1.2f, x + 3f, y + 1.2f, 1.2f); break;
            default: paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.3f); paint.setColor(fg); c.drawCircle(x, y, 2.4f, paint); paint.setStyle(Paint.Style.FILL); break;
        }
    }

    private void homePage(Canvas c, int page) {
        wallpaper(c, false);
        int on = onWall();
        statusBar(c, on, false);
        float[] cols = {17, 39, 61, 83};
        int n = 0;
        if (page == 1) {
            if (!date.isEmpty()) text(c, date, 8, 26, 7.4f, on, I18n.medium(), Paint.Align.LEFT);
            sun(c, 10.5f, 33.6f, 1.8f, on);
            text(c, temp, 15, 36, 6.4f, on, Typeface.DEFAULT, Paint.Align.LEFT);
            for (float y : new float[]{126, 146}) for (float x : cols) icon(c, x, y, n++);
        } else if (page % 2 == 0) {
            fill(c, look.wallLight ? 0x66ffffff : 0x40ffffff, 8, 22, 92, 58, 8);
            text(c, time, 16, 46, 15f, on, LIGHT, Paint.Align.LEFT);
            for (float y : new float[]{76, 96, 116}) for (float x : cols) icon(c, x + 0, y, n++ + 2);
        } else {
            for (float x : cols) icon(c, x, 34, n++ + 1);
            for (int i = 0; i < 2; i++) icon(c, cols[i], 54, n++ + 3);
        }

        if (pages > 1) {
            float x0 = 50 - (pages - 1) * 2.5f;
            for (int i = 0; i < pages; i++) dot(c, (on & 0xffffff) | ((i + 1 == page ? 0xee : 0x66) << 24), x0 + i * 5, 163, i + 1 == page ? 1.5f : 1.2f);
        }
        for (int i = 0; i < 4; i++) icon(c, cols[i], 177, i + 5);
        fill(c, look.wallLight ? 0xccffffff : 0x59ffffff, 8, 188, 92, 200, 6);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.1f); paint.setColor(look.wallLight ? 0xff5f6368 : 0xddffffff);
        c.drawCircle(15, 194, 2.2f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void google(Canvas c) {
        boolean dark = look.night;
        c.drawColor(dark ? 0xff1f1f21 : 0xfff3f6fb);
        int text = dark ? 0xffe3e3e6 : 0xff1f1f1f, sub = dark ? 0xff8e9099 : 0xff9aa0a6, card = dark ? 0xff2a2b2f : 0xffffffff;
        statusBar(c, text, false);
        sun(c, 10.5f, 19.5f, 1.8f, 0xfff2b300);
        text(c, temp, 15, 22, 6.4f, text, Typeface.DEFAULT, Paint.Align.LEFT);
        dot(c, look.accent, 88, 19.5f, 4.4f);
        fill(c, dark ? 0xff303134 : 0xffe8edf5, 6, 28, 94, 42, 7);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.1f); paint.setColor(sub);
        c.drawCircle(13.5f, 34.5f, 2.3f, paint); c.drawLine(15.2f, 36.2f, 16.8f, 37.8f, paint);
        paint.setStyle(Paint.Style.FILL);
        dot(c, sub, 86, 35, 1.6f);
        for (int i = 0; i < 2; i++) {
            float t = 50 + i * 78;
            fill(c, card, 6, t, 94, t + 72, 7);
            paint.setColor(Color.BLACK);
            paint.setShader(i == 0 ? look.photoA : look.photoB);
            box2.set(6, t, 94, t + 44);
            c.save(); c.clipRect(box2); box2.bottom += 8; c.drawRoundRect(box2, 7, 7, paint); c.restore();
            paint.setShader(null);
            fill(c, text, 11, t + 51, 80, t + 54.5f, 1.8f);
            fill(c, text, 11, t + 58, 62, t + 61.5f, 1.8f);
            fill(c, sub, 11, t + 65, 40, t + 67.5f, 1.2f);
        }
    }

    private void lockScreen(Canvas c) {
        wallpaper(c, false);
        c.drawColor(0x40000000);
        int white = 0xffffffff;
        padlock(c, 50, 15, 1.1f, white);
        text(c, time, 50, 62, 27f, white, LIGHT, Paint.Align.CENTER);
        if (!date.isEmpty()) text(c, date, 50, 74, 6.4f, 0xe6ffffff, I18n.medium(), Paint.Align.CENTER);
        int note = look.night ? 0xe6232428 : 0xebf3f3f7, line = look.night ? 0xffd9dade : 0xff3c3d42;
        for (int i = 0; i < 2; i++) {
            float t = 118 + i * 23;
            fill(c, note, 6, t, 94, t + 20, 7);
            dot(c, look.accent, 14, t + 10, 3.6f);
            fill(c, line, 21, t + 6.5f, 62, t + 9.5f, 1.5f);
            fill(c, (line & 0xffffff) | 0x99000000, 21, t + 12.5f, 78, t + 15f, 1.2f);
        }
        fingerprint(c, 50, 186, 1f, 0xb3ffffff);
        dot(c, 0x33ffffff, 14, 198, 5.5f);
        dot(c, 0x33ffffff, 86, 198, 5.5f);
    }

    private static final float[][] RIDGES = {{1.45f, 2.6f, 3.9f}, {2.95f, 4.3f, 2.3f}, {4.45f, 2.9f, 4.4f}};

    private void fingerprint(Canvas c, float x, float y, float s, int color) {
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.85f * s); paint.setColor(color); paint.setStrokeCap(Paint.Cap.ROUND);
        y -= 0.6f * s;
        for (float[] ridge : RIDGES) {
            float r = ridge[0] * s;
            box2.set(x - r, y - r, x + r, y + r);
            c.drawArc(box2, 180, 180, false, paint);
            c.drawLine(x - r, y, x - r, y + ridge[1] * s, paint);
            c.drawLine(x + r, y, x + r, y + ridge[2] * s, paint);
        }
        c.drawLine(x, y + 0.3f * s, x, y + 4.2f * s, paint);
        float outer = 5.95f * s;
        box2.set(x - outer, y - outer, x + outer, y + outer);
        c.drawArc(box2, 200, 140, false, paint);
        paint.setStyle(Paint.Style.FILL); paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void feed(Canvas c, long now) {
        Look k = look;
        boolean lock = mode == LOCK;
        float op = Math.max(0f, Math.min(1f, opShown));
        wallpaper(c, false);
        if (lock) c.drawColor(0x40000000);
        if (k.blur) {
            int save = c.saveLayerAlpha(0, 0, SCREEN_W, SCREEN_H, Math.round(255 * op), Canvas.ALL_SAVE_FLAG);
            wallpaper(c, true);
            c.restoreToCount(save);
            c.drawColor((k.base0 & 0xffffff) | (Math.round(0xcc * op) << 24));
        } else c.drawColor((k.base0 & 0xffffff) | (Math.round(255 * op) << 24));

        float cover = (k.blur ? 0.8f : 1f) * op;
        double lum = Look.lum(k.base0) * cover + Look.lum(k.wall) * (1 - cover);
        int words = lum > 0.6 ? 0xff1b1b1f : 0xffffffff;
        statusBar(c, words, lock);
        float hs = Math.max(0f, Math.min(1f, headerShown));
        if (hs > 0.01f) {
            int a = Math.round(255 * hs);
            if (!date.isEmpty()) text(c, date, 8, 27 - (1 - hs) * 4, 9f, (words & 0xffffff) | (a << 24), I18n.medium(), Paint.Align.LEFT);
            paint.setTextSize(7.6f); paint.setTypeface(Typeface.DEFAULT);
            float tw = paint.measureText(temp);
            text(c, temp, 93, 27 - (1 - hs) * 4, 7.6f, (words & 0xffffff) | (a << 24), Typeface.DEFAULT, Paint.Align.RIGHT);
            sun(c, 93 - tw - 4.6f, 24.4f - (1 - hs) * 4, 2.1f, (0xfff2b300 & 0xffffff) | (a << 24));
        }
        float t = 17 + 20 * hs;
        int card = k.card, on = k.onCard, faint = (k.onCard & 0xffffff) | 0x59000000;
        fill(c, card, 6, t, 94, t + 36, 7);
        text(c, time, 12, t + 22, 15f, on, LIGHT, Paint.Align.LEFT);
        fill(c, faint, 12, t + 27.5f, 44, t + 30, 1.2f);
        dot(c, k.accent, 82, t + 18, 6.5f);
        fill(c, card, 6, t + 40, 48, t + 82, 7);
        fill(c, k.accent, 11, t + 46, 24, t + 49, 1.5f);
        for (int i = 0; i < 3; i++) fill(c, i == 0 ? on : faint, 11, t + 55 + i * 7.5f, i == 1 ? 34 : 42, t + 57.6f + i * 7.5f, 1.3f);
        fill(c, card, 52, t + 40, 94, t + 82, 7);
        sun(c, 62, t + 52, 2.6f, 0xfff2b300);
        text(c, temp, 70, t + 55.5f, 8.5f, on, Typeface.DEFAULT, Paint.Align.LEFT);
        float[] bars = {6, 9, 7, 11, 8};
        for (int i = 0; i < bars.length; i++) fill(c, i == 3 ? k.accent : faint, 58 + i * 6.4f, t + 76 - bars[i], 61.5f + i * 6.4f, t + 76, 1.2f);
        fill(c, card, 6, t + 86, 94, t + 112, 7);
        for (int i = 0; i < 2; i++) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.9f); paint.setColor(on);
            c.drawCircle(13, t + 93.5f + i * 11, 2.2f, paint); paint.setStyle(Paint.Style.FILL);
            fill(c, i == 0 ? on : faint, 19, t + 92.3f + i * 11, i == 0 ? 70 : 56, t + 94.8f + i * 11, 1.2f);
        }

        dot(c, card, 50, t + 126, 7f);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.3f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(on);
        c.drawLine(47, t + 126, 53, t + 126, paint); c.drawLine(50, t + 123, 50, t + 129, paint);
        paint.setStyle(Paint.Style.FILL); paint.setStrokeCap(Paint.Cap.BUTT);
        if (tapMs > 0 && now - tapAt < tapMs) tap(c, t, (now - tapAt) / (float) tapMs);
    }

    private void tap(Canvas c, float t, float k) {
        float cx = 27, cy = t + 61;
        if (k < 0.3f) {
            float a = k < 0.05f ? k / 0.05f : 1f - (k - 0.05f) / 0.25f;
            dot(c, Color.argb(Math.round(150 * a), 255, 255, 255), cx, cy, 5f + 9f * (k / 0.3f));
        }
        if (openUnlocked) {
            float open = k < 0.22f ? 0f : k < 0.5f ? easeOut((k - 0.22f) / 0.28f) : k < 0.78f ? 1f : 1f - ease((k - 0.78f) / 0.2f);
            if (open <= 0.001f) return;
            float l = 6 + (0 - 6) * open, tt = (t + 40) + (0 - (t + 40)) * open, r = 48 + (SCREEN_W - 48) * open, b = (t + 82) + (SCREEN_H - (t + 82)) * open;
            int app = look.night ? 0xff1d1e22 : 0xfffbfbfe;
            fill(c, app, l, tt, r, b, 7 * (1 - open) + 2 * open);
            if (open > 0.6f) {
                int a = Math.round(255 * (open - 0.6f) / 0.4f);
                fill(c, (look.accent & 0xffffff) | (a << 24), 0, 0, SCREEN_W, 34, 0);
                int line = look.night ? 0xffd9dade : 0xff3c3d42;
                for (int i = 0; i < 6; i++) fill(c, (line & 0xffffff) | (Math.round(a * (i % 3 == 0 ? 1f : 0.45f)) << 24), 10, 48 + i * 14, i % 3 == 0 ? 70 : 88, 51 + i * 14, 1.5f);
            }
        } else {
            float ask = k < 0.25f ? 0f : k < 0.4f ? (k - 0.25f) / 0.15f : k < 0.85f ? 1f : 1f - (k - 0.85f) / 0.15f;
            if (ask <= 0.001f) return;
            c.drawColor(Color.argb(Math.round(120 * ask), 0, 0, 0));
            float pulse = 1f + 0.12f * (float) Math.sin((k - 0.4f) * Math.PI * 6);
            dot(c, Color.argb(Math.round(60 * ask), 255, 255, 255), 50, 186, 8.5f * pulse);
            fingerprint(c, 50, 186, 1.15f * pulse, Color.argb(Math.round(255 * ask), 255, 255, 255));
            padlock(c, 50, 160, 1.4f, Color.argb(Math.round(230 * ask), 255, 255, 255));
        }
    }

    static final class Look {
        final boolean night, blur, wallLight;
        final int base0, card, onCard, accent, iconBg, iconFg, wall;
        final Shader base, photoA, photoB;
        final Shader[] blobs, softBlobs;

        Look(Context c, boolean lock) {
            boolean dark = FrostedSurface.dark(c);
            boolean superDark = SuperDark.inEffect(c);
            night = dark || superDark;
            blur = !superDark && FrostedSurface.blurEnabled(c);
            if (superDark) { card = FrostedSurface.SUPER_DARK_CLOSED_FOLDER; base0 = Color.BLACK; }
            else if (blur) { card = dark ? 0x38ffffff : 0xa6ffffff; base0 = FrostedSurface.tone(c) | 0xff000000; }
            else {
                card = LauncherFeatures.surface(c, dark ? "system_surface_container_highest_dark" : "system_surface_container_highest_light", dark ? 0xff35353a : 0xffe2e1e7);
                base0 = LauncherFeatures.surface(c, dark ? "system_surface_container_dark" : "system_surface_container_light", dark ? 0xff1f1f23 : 0xfff3f2f7);
            }
            onCard = night ? LauncherFeatures.surface(c, "system_on_surface_dark", 0xffe3e2e6) : LauncherFeatures.surface(c, "system_on_surface_light", 0xff1b1b1f);
            accent = LauncherFeatures.surface(c, night ? "system_accent1_200" : "system_accent1_600", night ? 0xffaac7ff : 0xff3f5f91);
            iconBg = LauncherFeatures.surface(c, night ? "system_accent2_800" : "system_accent1_100", night ? 0xff3a3f4b : 0xffdbe2f9);
            iconFg = LauncherFeatures.surface(c, night ? "system_accent1_200" : "system_accent1_700", night ? 0xffaac7ff : 0xff2f4a7a);
            int[] w = wallpaperColors(c, lock, night);
            wall = w[0];
            wallLight = false;
            base = new LinearGradient(0, 0, 0, SCREEN_H, darken(w[1], 0.1f), darken(w[0], 0.25f), Shader.TileMode.CLAMP);
            blobs = new Shader[]{
                    new RadialGradient(18, 52, 78, (w[0] & 0xffffff) | 0xd9000000, w[0] & 0xffffff, Shader.TileMode.CLAMP),
                    new RadialGradient(92, 140, 82, (w[2] & 0xffffff) | 0xcc000000, w[2] & 0xffffff, Shader.TileMode.CLAMP),
                    new RadialGradient(34, 206, 62, (w[1] & 0xffffff) | 0xbf000000, w[1] & 0xffffff, Shader.TileMode.CLAMP)};
            int soft = mix(mix(w[0], w[1], 0.5f), w[2], 0.33f);
            softBlobs = new Shader[]{
                    new RadialGradient(30, 70, 120, (mix(w[0], soft, 0.5f) & 0xffffff) | 0xb3000000, soft & 0xffffff, Shader.TileMode.CLAMP),
                    new RadialGradient(80, 160, 120, (mix(w[2], soft, 0.5f) & 0xffffff) | 0xa6000000, soft & 0xffffff, Shader.TileMode.CLAMP)};
            photoA = new LinearGradient(6, 0, 94, 44, w[2] | 0xff000000, darken(w[0], 0.2f), Shader.TileMode.CLAMP);
            photoB = new LinearGradient(6, 0, 94, 44, w[1] | 0xff000000, darken(w[2], 0.3f), Shader.TileMode.CLAMP);
        }

        static double lum(int color) { return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0; }

        static int mix(int a, int b, float k) {
            return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * k), Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * k), Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
        }

        static int darken(int a, float k) { return mix(a | 0xff000000, 0xff000000, k); }

        static int[] wallpaperColors(Context c, boolean lock, boolean night) {
            int[] out = {LauncherFeatures.surface(c, "system_accent1_400", 0xff5f84c8), LauncherFeatures.surface(c, "system_accent3_300", 0xffc49ad9), LauncherFeatures.surface(c, "system_accent2_600", 0xff586078)};
            try {
                Object manager = c.getSystemService(Context.WALLPAPER_SERVICE);
                Object colors = null;
                if (lock) colors = manager.getClass().getMethod("getWallpaperColors", int.class).invoke(manager, 2);
                if (colors == null) colors = manager.getClass().getMethod("getWallpaperColors", int.class).invoke(manager, 1);
                if (colors == null) return out;
                String[] names = {"getPrimaryColor", "getSecondaryColor", "getTertiaryColor"};
                for (int i = 0; i < 3; i++) {
                    Object col = colors.getClass().getMethod(names[i]).invoke(colors);
                    if (col != null) out[i] = (Integer) col.getClass().getMethod("toArgb").invoke(col);
                    else if (i > 0) out[i] = mix(out[0], night ? 0xff000000 : 0xffffffff, 0.35f);
                }
            } catch (Throwable ignored) { }
            for (int i = 0; i < 3; i++) out[i] |= 0xff000000;
            return out;
        }
    }

    static final class Sides extends View {
        interface Listener { void picked(boolean right); }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final String left, right;
        private final int accent, onAccent, ink, track;
        private float thumb; private boolean isRight;
        private Listener listener;
        private ValueAnimator slide;
        private final float dp;

        Sides(Context c, int accent, int onAccent, int ink, int track) {
            super(c);
            this.accent = accent; this.onAccent = onAccent; this.ink = ink; this.track = track;
            dp = c.getResources().getDisplayMetrics().density;
            left = I18n.cs("Left").toString(); right = I18n.cs("Right").toString();
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(I18n.medium());
            p.setTextSize(14 * c.getResources().getDisplayMetrics().scaledDensity);
            setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            setFocusable(true);
            setClickable(true);
            describe();
        }

        void listen(Listener l) { listener = l; }

        boolean right() { return isRight; }

        void set(boolean toRight, boolean animate) {
            isRight = toRight;
            describe();
            if (slide != null) slide.cancel();
            float to = toRight ? 1f : 0f;
            if (!animate) { thumb = to; invalidate(); return; }
            slide = ValueAnimator.ofFloat(thumb, to);
            slide.setDuration(280);
            slide.setInterpolator(new DecelerateInterpolator(2f));
            slide.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { thumb = (Float) a.getAnimatedValue(); invalidate(); }
            });
            slide.start();
        }

        private void describe() { setContentDescription((isRight ? right : left)); }

        @Override protected void onMeasure(int ws, int hs) {
            float half = Math.max(p.measureText(left), p.measureText(right)) + 30 * dp;
            int w = Math.round(Math.max(half, 64 * dp) * 2 + 8 * dp);
            setMeasuredDimension(resolveSize(w, ws), resolveSize(Math.round(42 * dp), hs));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), pad = 4 * dp;
            p.setStyle(Paint.Style.FILL);
            p.setColor(track);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
            float half = (w - pad * 2) / 2f;
            float x = pad + half * thumb;
            p.setColor(accent);
            r.set(x, pad, x + half, h - pad);
            c.drawRoundRect(r, (h - pad * 2) / 2f, (h - pad * 2) / 2f, p);
            float base = h / 2f - (p.descent() + p.ascent()) / 2f;
            p.setColor(blend(onAccent, ink, thumb));
            c.drawText(left, pad + half / 2f, base, p);
            p.setColor(blend(ink, onAccent, thumb));
            c.drawText(right, pad + half * 1.5f, base, p);
        }

        private static int blend(int a, int b, float k) {
            k = Math.max(0f, Math.min(1f, k));
            return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * k), Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * k),
                    Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * k), Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); return true; }
            if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= -8 * dp && e.getY() <= getHeight() + 8 * dp) {
                boolean want = e.getX() > getWidth() / 2f;
                if (want != isRight) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (listener != null) listener.picked(want);
                }
                return true;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            if (listener != null) listener.picked(!isRight);
            return true;
        }
    }
}
