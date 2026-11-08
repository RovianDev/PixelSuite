package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.TimePickerDialog;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ChromeDarkPages {
    private ChromeDarkPages() {}

    static final String ID = "dark";
    private static final String PREFS = "pixelsuite_dark";
    private static final Uri PROVIDER = Uri.parse("content://io.github.roviandev.pixelsuite.cosmetic");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final WeakHashMap<Object, Boolean> sPages = new WeakHashMap<Object, Boolean>();
    private static final Map<String, Integer> sLocal = new HashMap<String, Integer>();
    private static final Map<String, Long> sLocalAt = new HashMap<String, Long>();
    private static final List<Runnable> sWatchers = new ArrayList<Runnable>();
    private static Context sApp;
    private static boolean sInstalled, sObserving, sNight;
    private static volatile boolean sShown;
    private static final Runnable BOUNDARY = new Runnable() { public void run() { refreshAll(true); } };

    static synchronized void install(ClassLoader l) throws Throwable {
        if (sInstalled) return;
        Class<?> proxy = l.loadClass("org.chromium.content.browser.webcontents.WebContentsObserverProxy");
        Xp.Callback start = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { try { Object wc = ChromeCosmetic.webContentsOf(p.thisObject); if (wc != null) apply(wc, false); } catch (Throwable ignored) { } }
        };
        Xp.hookAllMethods(proxy, "primaryMainDocumentElementAvailable", start);
        Xp.hookAllMethods(proxy, "documentLoadedInPrimaryMainFrame", start);
        sInstalled = true;
    }

    private static Context app() {
        if (sApp != null) return sApp;
        try { sApp = ((Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)).getApplicationContext(); } catch (Throwable ignored) { }
        if (sApp != null) observe(sApp);
        return sApp;
    }

    private static void observe(final Context c) {
        if (sObserving) return;
        sObserving = true;
        ContentObserver o = new ContentObserver(MAIN) {
            @Override public void onChange(boolean self) {
                synchronized (sLocal) { sLocal.clear(); sLocalAt.clear(); }
                sActiveAt = -1;
                refreshAll(true);
            }
        };
        for (String k : new String[] { DarkPages.KEY, DarkPages.KEY_STYLE, DarkPages.KEY_WHEN, DarkPages.KEY_FROM, DarkPages.KEY_TO })
            try { c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(k), false, o); } catch (Throwable ignored) { }
        sNight = DarkPages.systemDark();
        c.registerComponentCallbacks(new ComponentCallbacks() {
            public void onConfigurationChanged(Configuration n) {
                boolean night = DarkPages.systemDark();
                if (night == sNight) return;
                sNight = night;
                sActiveAt = -1;
                if (when(c) == DarkPages.WITH_DARK_MODE) refreshAll(true);
            }
            public void onLowMemory() { }
        });
        try {
            android.content.IntentFilter f = new android.content.IntentFilter();
            f.addAction(android.content.Intent.ACTION_SCREEN_ON);
            f.addAction(android.content.Intent.ACTION_TIME_CHANGED);
            f.addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED);
            c.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context x, android.content.Intent i) {
                    sActiveAt = -1;
                    if (activeNow(c) != sShown) refreshAll(true); else schedule(c);
                }
            }, f);
        } catch (Throwable ignored) { }
    }

    static int get(Context c, String key, int def) {
        synchronized (sLocal) {
            Long at = sLocalAt.get(key);
            if (at != null && SystemClock.uptimeMillis() - at < 5000) return sLocal.get(key);
        }
        try { return Settings.Secure.getInt(c.getContentResolver(), key, def); } catch (Throwable t) { return def; }
    }
    static boolean on(Context c) { return get(c, DarkPages.KEY, 0) == 1; }
    static boolean superDark(Context c) { return get(c, DarkPages.KEY_STYLE, 0) == 1; }
    static int when(Context c) { return get(c, DarkPages.KEY_WHEN, DarkPages.ALWAYS); }
    static int from(Context c) { return get(c, DarkPages.KEY_FROM, DarkPages.DEFAULT_FROM); }
    static int to(Context c) { return get(c, DarkPages.KEY_TO, DarkPages.DEFAULT_TO); }
    static boolean premium(Context c) { try { return c != null && PatronGate.ok(c); } catch (Throwable t) { return false; } }
    private static volatile long sActiveAt = -1;
    private static volatile boolean sActive;
    static boolean activeNow(Context c) {
        long now = SystemClock.uptimeMillis();
        if (sActiveAt >= 0 && now - sActiveAt < 500) return sActive;
        sActive = premium(c) && DarkPages.activeNow(on(c), when(c), from(c), to(c), System.currentTimeMillis());
        sActiveAt = now;
        return sActive;
    }

    static void put(final Context c, final String key, final int value) {
        synchronized (sLocal) { sLocal.put(key, value); sLocalAt.put(key, SystemClock.uptimeMillis()); }
        sActiveAt = -1;
        IO.execute(new Runnable() { public void run() {
            try { Bundle b = new Bundle(); b.putInt("value", value); c.getContentResolver().call(PROVIDER, "dark", key, b); } catch (Throwable ignored) { }
        }});
        refreshAll(true);
    }

    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean offFor(Context c, String host) {
        String d = ChromeSiteSettings.domain(host);
        return d != null && prefs(c).getStringSet("off", new HashSet<String>()).contains(d);
    }
    static void setOffFor(Context c, String host, boolean off) {
        String d = ChromeSiteSettings.domain(host);
        if (d == null) return;
        Set<String> s = new HashSet<String>(prefs(c).getStringSet("off", new HashSet<String>()));
        if (off) s.add(d); else s.remove(d);
        prefs(c).edit().putStringSet("off", s).apply();
        refreshAll(true);
    }

    static void apply(final Object wc, boolean animate) {
        Context c = app();
        if (c == null || wc == null) return;
        String host = ChromeCosmetic.hostOf(wc);
        boolean active = activeNow(c);
        sShown = active;
        boolean dark = active && (host == null || !offFor(c, host));
        Boolean was;
        synchronized (sPages) { was = sPages.get(wc); sPages.put(wc, dark); }
        if (!dark && (was == null || !was) && !animate) return;
        String js = dark ? "(" + ChromeDarkPagesScript.ENGINE + ")({on:true,sd:" + superDark(c) + ",animate:" + animate + "})"
                : "window.__psDark&&window.__psDark.set({on:false,animate:" + animate + "})";
        ChromeCosmetic.runAll(wc, js);
        schedule(c);
    }

    static void refreshAll(final boolean animate) {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(new Runnable() { public void run() { refreshAll(animate); } }); return; }
        sActiveAt = -1;
        List<Object> all;
        synchronized (sPages) { all = new ArrayList<Object>(sPages.keySet()); }
        for (Object wc : all) { try { apply(wc, animate); } catch (Throwable ignored) { } }
        Context c = app();
        if (c != null) schedule(c);
        for (Runnable r : new ArrayList<Runnable>(sWatchers)) { try { r.run(); } catch (Throwable ignored) { } }
        refreshButtons();
    }

    static void watch(Runnable r) { if (r != null && !sWatchers.contains(r)) sWatchers.add(r); }
    static void unwatch(Runnable r) { sWatchers.remove(r); }

    private static void schedule(Context c) {
        MAIN.removeCallbacks(BOUNDARY);
        if (!on(c) || when(c) != DarkPages.SCHEDULE) return;
        MAIN.postDelayed(BOUNDARY, Math.max(1000, DarkPages.untilNextChange(from(c), to(c), System.currentTimeMillis()) + 500));
    }

    static final ChromeExtensions.Extension EXTENSION = new ChromeExtensions.Extension() {
        public String id() { return ID; }
        public String title() { return "Dark pages"; }
        public String summary(View anchor) { Context c = app(); return c == null ? "" : !premium(c) ? "Needs Pixel Suite premium" : status(c); }
        public boolean available() { return premium(app()); }
        public int switchState() { Context c = app(); return c != null && on(c) ? 1 : 0; }
        public void setSwitch(boolean v) { Context c = app(); if (c != null) put(c, DarkPages.KEY, v ? 1 : 0); }
        public void draw(Canvas c, float cx, float cy, float size, int ink, boolean dark) {
            Context a = app();
            boolean lit = a != null && activeNow(a);
            moon(c, cx, cy, size, !available() ? 0xFF9AA0A6 : lit ? (dark ? 0xFFFDD663 : 0xFF3B4AA8) : ink);
        }
        public View content(View anchor, Runnable dismiss) { return panel(anchor); }
    };

    static void moon(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float r = size * 0.44f;
        c.drawPath(crescent(cx, cy, size), p);
        float sx = cx + r * 0.62f, sy = cy - r * 0.7f, s = size * 0.11f;
        Path star = new Path();
        star.moveTo(sx, sy - s); star.quadTo(sx, sy, sx + s, sy); star.quadTo(sx, sy, sx, sy + s); star.quadTo(sx, sy, sx - s, sy); star.quadTo(sx, sy, sx, sy - s); star.close();
        c.drawPath(star, p);
    }

    static String status(Context c) {
        if (!on(c)) return "Off";
        String style = DarkPages.STYLES[superDark(c) ? 1 : 0];
        int w = when(c);
        boolean now = activeNow(c);
        if (w == DarkPages.WITH_DARK_MODE) return (now ? "On now · " : "Waiting for dark mode · ") + style;
        if (w == DarkPages.SCHEDULE) return (now ? "On until " + DarkPages.time(c, to(c)) : "Turns on at " + DarkPages.time(c, from(c))) + " · " + style;
        return "On · " + style;
    }

    static void open(View anchor) {
        Context c = anchor.getContext();
        boolean dark = night(c);
        int fg = dark ? 0xFFE3E3E3 : 0xFF1F1F1F;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setPadding(dp(c, 24), dp(c, 16), dp(c, 24), dp(c, 4));
        head.addView(text(c, "Dark pages", 19, fg, true));
        box.addView(head);
        box.addView(panel(anchor), new LinearLayout.LayoutParams(-1, -2));
        GradientDrawable shape = new GradientDrawable();
        int sheet = ChromeLayout.sheetColor(dark ? 0xFF1F1F1F : 0xFFFFFFFF, anchor);
        shape.setColor(sheet);
        shape.setCornerRadius(dp(c, 20));
        int outline = ChromeLayout.sheetOutline(sheet, fg);
        if (outline != 0) shape.setStroke(Math.max(1, dp(c, 1)), outline);
        ChromeLayout.refineSheet(anchor, shape, Math.max(1, dp(c, 1)), fg);
        box.setBackground(shape);
        box.setClipToOutline(true);
        ChromeSheetMotion.Sheet pw = new ChromeSheetMotion.Sheet(box, Math.min(dp(c, 340), c.getResources().getDisplayMetrics().widthPixels - dp(c, 16)), ViewGroup.LayoutParams.WRAP_CONTENT, anchor, dp(c, 20));
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(c, 12));
        pw.showAsDropDown(anchor, 0, -dp(c, 4), Gravity.END);
        final LinearLayout card = box;
        pw.open(new Runnable() { public void run() { ChromeSheetMotion.enter(card, true, null); } });
    }

    private static final List<WeakReference<Button>> sButtons = new ArrayList<WeakReference<Button>>();

    static void attach(LinearLayout row, int at, View toolbar) {
        for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i) instanceof Button) return;
        Button b = new Button(row.getContext(), toolbar);
        int w = Math.round(48 * row.getResources().getDisplayMetrics().density);
        row.addView(b, Math.min(at, row.getChildCount()), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        sButtons.add(new WeakReference<Button>(b));
        b.refresh();
    }

    static List<View> buttons() {
        List<View> out = new ArrayList<View>();
        for (int i = sButtons.size() - 1; i >= 0; i--) { Button b = sButtons.get(i).get(); if (b == null) sButtons.remove(i); else out.add(b); }
        return out;
    }

    static void refreshButtons() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(new Runnable() { public void run() { refreshButtons(); } }); return; }
        for (View b : buttons()) ((Button) b).refresh();
    }

    private static View findId(View root, String id) {
        try { if (root.getId() != View.NO_ID && id.equals(root.getResources().getResourceEntryName(root.getId()))) return root; } catch (Throwable ignored) { }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) { View f = findId(g.getChildAt(i), id); if (f != null) return f; }
        }
        return null;
    }

    static final class Button extends View {
        private final WeakReference<View> toolbar;
        private WeakReference<android.widget.ImageView> tint = new WeakReference<android.widget.ImageView>(null);
        private float press = 1f;
        private ValueAnimator pressAnim;
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

        Button(Context c, View toolbar) {
            super(c);
            this.toolbar = new WeakReference<View>(toolbar);
            setClickable(true); setFocusable(true);
            setContentDescription(I18n.cs("Dark pages"));
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) { try { setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { } }
            setOnClickListener(new OnClickListener() { public void onClick(View v) {
                try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                try { open(Button.this); } catch (Throwable ignored) { }
            }});
            setVisibility(GONE);
        }

        void refresh() {
            Context a = app();
            boolean show = ID.equals(ChromeShield.pinnedId(getContext())) && premium(a);
            if (!show) { if (getVisibility() != GONE) setVisibility(GONE); return; }
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            invalidate();
        }

        private ValueAnimator run(ValueAnimator a) { if (pressAnim != null) pressAnim.cancel(); a.start(); return a; }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    ValueAnimator a = ValueAnimator.ofFloat(press, 0.84f);
                    a.setDuration(130);
                    a.setInterpolator(ChromeTabTransition.DECELERATE);
                    a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) { press = (Float) v.getAnimatedValue(); invalidate(); } });
                    pressAnim = run(a);
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    final float start = press, dip = Math.min(start, 0.88f);
                    ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
                    a.setDuration(620);
                    a.setInterpolator(null);
                    a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator v) {
                        float f = (Float) v.getAnimatedValue();
                        press = f < 0.1f ? ChromeTabTransition.lerp(start, dip, ChromeTabTransition.DECELERATE.getInterpolation(f / 0.1f))
                                : ChromeTabTransition.lerp(dip, 1f, ChromeTabTransition.SPRING.getInterpolation((f - 0.1f) / 0.9f));
                        invalidate();
                    }});
                    pressAnim = run(a);
                    break;
                }
            }
            return super.onTouchEvent(e);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (pressAnim != null) pressAnim.cancel();
            press = 1f;
        }

        private int iconColor() {
            android.widget.ImageView src = tint.get();
            if (src == null || !src.isAttachedToWindow()) {
                View t = toolbar.get();
                View f = t == null ? null : findId(t, "tab_switcher_button");
                src = f instanceof android.widget.ImageView ? (android.widget.ImageView) f : null;
                tint = new WeakReference<android.widget.ImageView>(src);
            }
            if (src != null && src.getImageTintList() != null) return src.getImageTintList().getColorForState(src.getDrawableState(), src.getImageTintList().getDefaultColor());
            return night(getContext()) ? 0xFFE3E3E3 : 0xFF444746;
        }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, cx = getWidth() / 2f, cy = getHeight() / 2f;
            Context a = app();
            boolean lit = a != null && activeNow(a), dark = night(getContext());
            int save = c.save();
            try {
                c.scale(press, press, cx, cy);
                Path m = crescent(cx, cy, 20 * d);
                if (lit) {
                    ink.setStyle(Paint.Style.FILL);
                    ink.setColor(dark ? 0xFF8AB4F8 : 0xFF1A73E8);
                } else {
                    ink.setStyle(Paint.Style.STROKE); ink.setStrokeJoin(Paint.Join.ROUND);
                    ink.setStrokeWidth(2 * d);
                    ink.setColor(iconColor());
                }
                c.drawPath(m, ink);
            } finally { c.restoreToCount(save); }
        }
    }

    static Path crescent(float cx, float cy, float size) {
        float r = size * 0.44f;
        Path moon = new Path(), bite = new Path();
        moon.addCircle(cx - size * 0.04f, cy + size * 0.03f, r, Path.Direction.CW);
        bite.addCircle(cx + r * 0.42f, cy - r * 0.38f, r * 0.82f, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        return moon;
    }

    private static boolean night(Context c) {
        return ChromeSuperDark.incognitoSelected() || (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    private static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(I18n.cs(s)); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color);
        t.setTypeface((medium ? I18n.medium() : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)));
        return t;
    }
    private static void ripple(View v) {
        TypedValue tv = new TypedValue();
        if (v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) { try { v.setForeground(v.getContext().getDrawable(tv.resourceId)); } catch (Throwable ignored) { } }
    }

    static View panel(final View anchor) {
        final Context c = anchor.getContext();
        final Context a = app() != null ? app() : c.getApplicationContext();
        final boolean dark = night(c);
        final int fg = dark ? 0xFFE3E3E3 : 0xFF1F1F1F, muted = dark ? 0xFFA8ABAF : 0xFF5F6368, accent = dark ? 0xFF8AB4F8 : 0xFF1F5FB4;
        final Object wc = ChromeShield.current(anchor);
        final String host = ChromeSiteSettings.domain(ChromeCosmetic.hostOf(wc));

        final LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(c, 10));

        final TextView state = text(c, "", 14, muted, false);
        state.setPadding(dp(c, 20), dp(c, 2), dp(c, 20), dp(c, 8));
        root.addView(state, new LinearLayout.LayoutParams(-1, -2));

        final LinearLayout main = switchRow(c, "Dark pages", null, on(a), accent, dark, fg, muted);
        final ChromeExtensions.Toggle mainToggle = (ChromeExtensions.Toggle) main.getTag();
        root.addView(main, new LinearLayout.LayoutParams(-1, dp(c, 56)));
        final LinearLayout site = host == null ? null : switchRow(c, "On this website", host, !offFor(a, host), accent, dark, fg, muted);
        final ChromeExtensions.Toggle siteToggle = site == null ? null : (ChromeExtensions.Toggle) site.getTag();
        if (site != null) root.addView(site, new LinearLayout.LayoutParams(-1, dp(c, 60)));

        final LinearLayout options = new LinearLayout(c);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), 0);
        options.addView(label(c, "Style", muted));
        final Segments style = new Segments(c, new String[] { "Dark", "Super Dark" }, superDark(a) ? 1 : 0, dark, accent);
        options.addView(style, new LinearLayout.LayoutParams(-1, dp(c, 42)));
        options.addView(label(c, "When", muted));
        final Segments when = new Segments(c, new String[] { "Always", "Dark mode", "Schedule" }, when(a), dark, accent);
        options.addView(when, new LinearLayout.LayoutParams(-1, dp(c, 42)));

        final LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setPadding(0, dp(c, 10), 0, 0);
        final TextView fromChip = chip(c, "", fg, dark), toChip = chip(c, "", fg, dark);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(c, 44), 1); cp.rightMargin = dp(c, 8);
        times.addView(fromChip, cp);
        times.addView(toChip, new LinearLayout.LayoutParams(0, dp(c, 44), 1));
        final boolean[] timesOpen = { when(a) == DarkPages.SCHEDULE };
        times.setVisibility(timesOpen[0] ? View.VISIBLE : View.GONE);
        options.addView(times, new LinearLayout.LayoutParams(-1, -2));
        root.addView(options, new LinearLayout.LayoutParams(-1, -2));

        final TextView note = text(c, "Pictures and videos keep their colors.", 12, muted, false);
        note.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        final Runnable update = new Runnable() { public void run() {
            boolean ok = premium(a), isOn = on(a);
            state.setText(I18n.cs(ok ? status(a) : "Needs Pixel Suite premium"));
            mainToggle.set(isOn);
            if (siteToggle != null) siteToggle.set(!offFor(a, host));
            style.select(superDark(a) ? 1 : 0, true);
            when.select(when(a), true);
            fromChip.setText(I18n.cs("From  " + DarkPages.time(a, from(a))));
            toChip.setText(I18n.cs("To  " + DarkPages.time(a, to(a))));
            float dim = isOn ? 1f : 0.45f;
            options.animate().alpha(dim).setDuration(200).start();
            if (site != null) site.animate().alpha(dim).setDuration(200).start();
            boolean open = when(a) == DarkPages.SCHEDULE;
            if (open != timesOpen[0]) { timesOpen[0] = open; reveal(times, open); }
        }};
        update.run();

        main.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!premium(a)) return;
            try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
            put(a, DarkPages.KEY, on(a) ? 0 : 1);
            update.run();
        }});
        if (site != null) site.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!premium(a) || !on(a)) return;
            try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
            setOffFor(a, host, !offFor(a, host));
            update.run();
        }});
        style.listener = new Segments.Listener() { public void picked(int i) { if (premium(a) && on(a)) { put(a, DarkPages.KEY_STYLE, i); update.run(); } else update.run(); } };
        when.listener = new Segments.Listener() { public void picked(int i) { if (premium(a) && on(a)) { put(a, DarkPages.KEY_WHEN, i); update.run(); } else update.run(); } };
        View.OnClickListener pick = new View.OnClickListener() { public void onClick(final View v) {
            if (!premium(a) || !on(a)) return;
            final boolean start = v == fromChip;
            int m = start ? from(a) : to(a);
            try {
                new TimePickerDialog(c, dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,
                    new TimePickerDialog.OnTimeSetListener() { public void onTimeSet(android.widget.TimePicker p, int h, int min) {
                        put(a, start ? DarkPages.KEY_FROM : DarkPages.KEY_TO, h * 60 + min);
                        update.run();
                    }}, m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(c)).show();
            } catch (Throwable ignored) { }
        }};
        fromChip.setOnClickListener(pick);
        toChip.setOnClickListener(pick);
        ChromeSheetMotion.springy(fromChip, fromChip);
        ChromeSheetMotion.springy(toChip, toChip);

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { watch(update); }
            public void onViewDetachedFromWindow(View v) { unwatch(update); }
        });
        return root;
    }

    private static TextView label(Context c, String s, int muted) {
        TextView t = text(c, s, 12, muted, true);
        t.setPadding(dp(c, 4), dp(c, 12), 0, dp(c, 6));
        return t;
    }

    private static LinearLayout switchRow(Context c, String title, String sub, boolean on, int accent, boolean dark, int fg, int muted) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 20), 0, dp(c, 16), 0);
        ripple(row);
        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(c, title, 15, fg, false));
        if (sub != null) { TextView s = text(c, sub, 12, muted, false); s.setSingleLine(true); s.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); labels.addView(s); }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        ChromeExtensions.Toggle t = new ChromeExtensions.Toggle(c, on, accent, dark);
        row.addView(t, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 26)));
        row.setTag(t);
        return row;
    }

    private static TextView chip(Context c, String s, int fg, boolean dark) {
        TextView t = text(c, s, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0x1AFFFFFF : 0x0F000000);
        bg.setCornerRadius(dp(c, 22));
        t.setBackground(bg);
        ripple(t);
        t.setClickable(true);
        return t;
    }

    private static void reveal(final View v, final boolean open) {
        if (!ChromeSheetMotion.animating(v) && !open) { v.setVisibility(View.GONE); return; }
        v.setVisibility(View.VISIBLE);
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        v.measure(View.MeasureSpec.makeMeasureSpec(((View) v.getParent()).getWidth() - ((View) v.getParent()).getPaddingLeft() - ((View) v.getParent()).getPaddingRight(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int full = v.getMeasuredHeight(), from = open ? 0 : v.getHeight(), to = open ? full : 0;
        if (!ChromeSheetMotion.animating(v)) { lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; v.setLayoutParams(lp); return; }
        lp.height = from; v.setLayoutParams(lp);
        v.setAlpha(open ? 0f : 1f);
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(open ? 320 : 220);
        va.setInterpolator(open ? ChromeTabTransition.EMPHASIZED : ChromeTabTransition.ACCELERATE);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator x) {
            float f = (Float) x.getAnimatedValue();
            lp.height = Math.round(ChromeTabTransition.lerp(from, to, f)); v.setLayoutParams(lp);
            v.setAlpha(open ? ChromeTabTransition.clamp((f - 0.25f) / 0.75f) : 1f - f);
        }});
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean done;
            @Override public void onAnimationEnd(android.animation.Animator x) {
                if (done) return; done = true;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; v.setLayoutParams(lp);
                v.setAlpha(1f);
                if (!open) v.setVisibility(View.GONE);
            }
        });
        va.start();
    }

    static final class Segments extends View {
        interface Listener { void picked(int i); }
        final String[] items;
        int sel;
        float pos, press = -1;
        Listener listener;
        private final int track, pill, ink, inkOn;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator anim;

        Segments(Context c, String[] items, int sel, boolean dark, int accent) {
            super(c);
            this.items = I18n.all(items); this.sel = Math.max(0, Math.min(items.length - 1, sel)); pos = this.sel;
            track = dark ? 0x1AFFFFFF : 0x0F000000;
            pill = accent;
            ink = dark ? 0xFFE3E3E3 : 0xFF1F1F1F;
            inkOn = dark ? 0xFF062E6F : Color.WHITE;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(I18n.medium());
            setClickable(true);
            setContentDescription(items[this.sel]);
        }

        void select(int i, boolean animate) {
            i = Math.max(0, Math.min(items.length - 1, i));
            if (i == sel && (anim == null || !anim.isRunning())) { if (pos != i && !animate) { pos = i; invalidate(); } return; }
            sel = i;
            setContentDescription(items[i]);
            if (anim != null) anim.cancel();
            if (!animate || !ChromeSheetMotion.animating(this)) { pos = i; invalidate(); return; }
            anim = ValueAnimator.ofFloat(pos, i);
            anim.setDuration(460);
            anim.setInterpolator(ChromeTabTransition.SPRING);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator a) { pos = (Float) a.getAnimatedValue(); invalidate(); } });
            anim.start();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth() / (float) items.length;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: press = (int) Math.max(0, Math.min(items.length - 1, e.getX() / w)); invalidate(); return true;
                case MotionEvent.ACTION_MOVE: return true;
                case MotionEvent.ACTION_UP: {
                    int i = (int) Math.max(0, Math.min(items.length - 1, e.getX() / w));
                    press = -1; invalidate();
                    if (i != sel) {
                        try { Haptics.tick(getContext()); } catch (Throwable ignored) { }
                        select(i, true);
                        if (listener != null) listener.picked(i);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL: press = -1; invalidate(); return true;
            }
            return super.onTouchEvent(e);
        }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, W = getWidth(), H = getHeight(), n = items.length, seg = W / n, r = H / 2f, in = 3 * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(track);
            c.drawRoundRect(new RectF(0, 0, W, H), r, r, p);
            if (press >= 0 && press != sel) { p.setColor(track); c.drawRoundRect(new RectF(press * seg + in, in, (press + 1) * seg - in, H - in), r - in, r - in, p); }
            float x = pos * seg;
            p.setColor(pill);
            c.drawRoundRect(new RectF(x + in, in, x + seg - in, H - in), r - in, r - in, p);
            p.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
            I18n.fit(p, seg - 2 * in - 8 * d, items);
            Paint.FontMetrics fm = p.getFontMetrics();
            float ty = H / 2f - (fm.ascent + fm.descent) / 2f;
            for (int i = 0; i < items.length; i++) {
                float k = Math.max(0f, 1f - Math.abs(pos - i));
                p.setColor(ChromeTabTransition.mix(ink, inkOn, k));
                c.drawText(items[i], i * seg + seg / 2f, ty, p);
            }
        }
    }
}
