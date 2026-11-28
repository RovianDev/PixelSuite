package io.github.pixelsuite;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SearchOverlay extends Dialog implements Choreographer.FrameCallback {
    private static final String TAG = "PixelSuite";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final ExecutorService ICONS = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Drawable> ICON_CACHE = new LruCache<String, Drawable>(240);
    private static WeakReference<SearchOverlay> shown = new WeakReference<SearchOverlay>(null);
    private static String token;

    static final class App { String label, norm, alt, t9, pkg; ComponentName cn; UserHandle user; LauncherActivityInfo info; Object bitmap; }
    static final class Shortcut { String label, norm, app, pkg, id; Object info; }
    static final class Setting { String label, norm; Intent intent; }
    private static List<App> sApps;
    private static List<Shortcut> sShortcuts;
    private static List<Setting> sSettings;
    private static long sAppsAt;

    static final class Result {
        String scope, title, sub, data, extra, id; int page; Drawable icon; Runnable open, longPress; List<App> apps;
    }

    private final SearchDock dock;
    private final boolean feed;
    private static RectF sOrigin;
    private static int sOriginColor;
    private static FeedPull sBehind;
    private final FeedPull behind;
    private final int cardColor;
    private final boolean rimmed;
    private int surfaceColor, veil = 44;
    private boolean frosty = true;
    private final RectF pill = new RectF(), final_ = new RectF();
    private float pillRadius, startK = -1f;
    private boolean told, headerUp, gaveBack, takenAll, takenNoted, wasOpen, earlyNoted;
    private long backAt;
    private float mine = 1f;
    private FeedPull.Bubble bubble;
    private final int[] bloomAt = new int[2];
    private final PixelSearch.Look look;
    private final RectF from;
    private final float fromRadius;
    private final int fromColor;
    private final float[] spot = new float[2];
    private float ghostX, ghostY, ghostScale = 1;
    private final boolean glass;
    private final float puff;
    private final boolean styled;
    private float closeU, closeFrom = 1;
    private Drawable blur;
    private float blurRadius = -1, rimRadius = -1;
    private GlassRim rim;
    private int ghostAlpha;
    private String firstApp;
    private boolean wide;
    private TextView heldChip;
    private float chipGrab;
    private final Root root;
    private final LinearLayout header, content, list;
    private final EditText field;
    private final ImageView back, clear;
    private final LinearLayout chips;
    private final ScrollView scroll;
    private final TextView hint;
    private float p, pv, q, qv;
    private boolean finished, open, running, closing, launched;
    private long last, openedAt, closedAt;
    private int topInset, bottomInset;
    private String filter = null;
    private int generation;
    private List<Result> results = new ArrayList<Result>();
    private final List<String> scopes;
    static Runnable whenGone;
    private final Runnable gone;
    private String engine;
    private int backdrop = 100;
    private LinearLayout keysPill;
    private boolean keysShown;
    private FrameLayout popup;
    private java.util.HashSet<String> shownKeys = new java.util.HashSet<String>();

    private SearchOverlay(SearchDock dock) {
        super(dock.getContext(), dock.look() != null && dock.look().dark ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        this.dock = dock;
        Context c = getContext();
        look = new PixelSearch.Look(c);
        RectF origin = sOrigin;
        sOrigin = null;
        feed = origin != null;
        behind = feed ? sBehind : null;
        sBehind = null;
        cardColor = feed ? feedCard(c, look) : look.card;
        rimmed = rimmed(c, look);
        if (feed) bubble = new FeedPull.Bubble(c, look);
        frosty = PixelSearch.number(c, PixelSearch.FROST_KEY, 1) == 1;
        veil = Math.max(0, Math.min(100, PixelSearch.number(c, PixelSearch.FEED_BACKDROP_KEY, look.dark ? 44 : 38)));
        surfaceColor = feed ? feedColor() : look.surface;
        if (behind != null) behind.plain(!frosty);
        from = feed ? origin : dock.shellOnScreen();
        fromRadius = feed ? origin.height() / 2f : dock.shellRadius();
        fromColor = feed ? sOriginColor : dock.shellColor();
        glass = look.glass;
        puff = feed ? 0 : dock.puffStart();
        styled = !feed && !SearchClose.classic(SearchClose.style(c));
        scopes = FeedPull.allowed(c, PixelSearch.enabledScopes(c));
        gone = whenGone;
        whenGone = null;
        engine = PixelSearch.engine(c);
        root = new Root(c);
        header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(8), 0);
        backdrop = feed ? 100 : Math.max(0, Math.min(100, PixelSearch.number(c, PixelSearch.BACKDROP_KEY, 100)));
        back = icon(engineMark(), "Search engine");
        back.setOnClickListener(x -> engines());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        field = new EditText(c);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setTextColor(look.ink);
        field.setHintTextColor(feed ? (look.ink & 0xffffff) | 0xa6000000 : PixelSearch.mix(look.ink, look.pill, 0.45f));
        field.setHint(I18n.cs("Search your phone and the web"));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setPrivateImeOptions(GboardPlus.SEARCH_FIELD);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setPadding(dp(4), 0, dp(4), 0);
        header.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        clear = icon(new SearchGlyph(SearchGlyph.CLEAR, look.ink), "Clear");
        clear.setVisibility(View.INVISIBLE);
        clear.setOnClickListener(x -> { field.setText(""); showKeyboard(); });
        header.addView(clear, new LinearLayout.LayoutParams(dp(40), dp(44)));
        final ImageView more = icon(new SearchGlyph(SearchGlyph.MORE, look.ink), "More");
        more.setOnClickListener(x -> options(more));
        header.addView(more, new LinearLayout.LayoutParams(dp(40), dp(44)));
        GradientDrawable hb = new GradientDrawable(); hb.setColor(feed ? Color.TRANSPARENT : look.card); hb.setCornerRadius(dp(28));
        header.setBackground(feed ? hb : panel(look.card, 28));
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(12), dp(4), dp(12), dp(4));
        chips.setClipToPadding(false); chips.setClipChildren(false);
        chipScroll.setClipToPadding(false); chipScroll.setClipChildren(false);
        content.setClipChildren(false);
        chipScroll.addView(chips);
        scroll = new ScrollView(c);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(4), dp(12), dp(24));
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        final Edge edge = new Edge(c);
        edge.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout body = new FrameLayout(c);
        body.setClipChildren(false);
        body.addView(edge, new FrameLayout.LayoutParams(-1, -1));
        body.addView(chipScroll, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        chipScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (scroll.getPaddingTop() != b - t) scroll.setPadding(0, b - t, 0, 0); });
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> edge.invalidate());
        hint = new TextView(c);
        hint.setTextColor(look.subText);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(32), dp(28), dp(32), dp(8));
        hint.setText(I18n.cs("Search apps, contacts, files, settings and more"));
        list.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.addView(header, new FrameLayout.LayoutParams(-1, dp(56)));
        buildChips();
        render();
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            public void onTextChanged(CharSequence s, int a, int b, int d) { }
            public void afterTextChanged(Editable s) { clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.INVISIBLE); query(s.toString()); }
        });
        field.setOnEditorActionListener((tv, action, ev) -> { enter(); return true; });
        setContentView(root);
        setCancelable(true);
        setOnDismissListener(d -> { finished = true; if (clipOpen) { clipOpen = false; LockClipboard.mark(getContext(), false); } if (WidgetPanel.sLock) FeedPull.watch(getContext(), false); else FeedPull.listen(getContext(), false); restoreHome(); if (behind != null) behind.gone(); if (gone != null) gone.run(); });
    }

    static void open(SearchDock dock) {
        SearchOverlay old = shown.get();
        if (old != null && old.isShowing()) return;
        try {
            SearchOverlay o = new SearchOverlay(dock);
            shown = new WeakReference<SearchOverlay>(o);
            o.show();
        } catch (Throwable t) { android.util.Log.w(TAG, "search: open", t); dock.setHidden(0); }
    }

    static SearchOverlay prepare(SearchDock dock, RectF origin, int color, FeedPull behind) {
        sOrigin = new RectF(origin);
        sOriginColor = color;
        sBehind = behind;
        try { warm(dock.getContext()); return new SearchOverlay(dock); }
        catch (Throwable t) { android.util.Log.w(TAG, "search: prepare", t); return null; }
        finally { sOrigin = null; sBehind = null; }
    }

    static boolean openPrepared(SearchOverlay o) {
        SearchOverlay old = shown.get();
        if (old != null && old.isShowing()) return false;
        try { shown = new WeakReference<SearchOverlay>(o); o.show(); return true; }
        catch (Throwable t) { android.util.Log.w(TAG, "search: open prepared", t); return false; }
    }

    static void openFrom(SearchDock dock, RectF origin, int color, FeedPull behind) {
        sOrigin = new RectF(origin);
        sOriginColor = color;
        sBehind = behind;
        try { open(dock); } finally { sOrigin = null; sBehind = null; }
    }

    static int feedCard(Context c, PixelSearch.Look look) {
        if (look.black) return look.card;
        if (SurfaceStyle.get(c) == SurfaceStyle.OFF) return look.card;
        return look.dark ? 0x26ffffff : 0xa6ffffff;
    }

    static boolean rimmed(Context c, PixelSearch.Look look) {
        try { return look.glass || look.black || SurfaceStyle.get(c) == SurfaceStyle.FROSTED; } catch (Throwable t) { return look.glass; }
    }

    private Drawable panel(int color, float radius) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius));
        if (!rimmed) return bg;
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{bg, new GlassRim(dp(radius), dp(1.5f), look.dark)});
    }

    private Drawable floating(float radius) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(look.rimmed ? look.panel : look.card); bg.setCornerRadius(dp(radius));
        if (!look.rimmed) return bg;
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{bg, new GlassRim(dp(radius), dp(1.5f), true)});
    }

    static int topOf(View v) {
        try {
            WindowInsets in = v.getRootWindowInsets();
            if (in != null) {
                try {
                    Class<?> type = Class.forName("android.view.WindowInsets$Type");
                    int mask = (Integer) type.getMethod("statusBars").invoke(null) | (Integer) type.getMethod("displayCutout").invoke(null);
                    Object i = WindowInsets.class.getMethod("getInsets", int.class).invoke(in, mask);
                    return i.getClass().getField("top").getInt(i);
                } catch (Throwable ignored) { }
                return in.getSystemWindowInsetTop();
            }
        } catch (Throwable ignored) { }
        try { int id = v.getResources().getIdentifier("status_bar_height", "dimen", "android"); if (id != 0) return v.getResources().getDimensionPixelSize(id); } catch (Throwable ignored) { }
        return 0;
    }

    static RectF feedField(View v) {
        Context c = v.getContext();
        int width = v.getRootView().getWidth();
        float d = c.getResources().getDisplayMetrics().density;
        int top = topOf(v);
        float m = Math.round(12 * d), t = top + Math.round(10 * d);
        return new RectF(m, t, width - m, t + Math.round(56 * d));
    }

    private static float sHomeY = -1f, sHomeX;
    private static boolean sHomeTaken;
    private static long sHomeAt;

    static void homeSwipe(MotionEvent e) {
        SearchOverlay o = shown.get();
        if (o == null || !o.isShowing() || o.closing || o.launched) { sHomeY = -1f; return; }
        int a = e.getActionMasked();
        float d = o.getContext().getResources().getDisplayMetrics().density;
        if (a == MotionEvent.ACTION_DOWN) {
            View top = o.root.getRootView();
            float h = top.getHeight() > 0 ? top.getHeight() : o.getContext().getResources().getDisplayMetrics().heightPixels;
            sHomeY = e.getRawY() >= h - 56f * d ? e.getRawY() : -1f;
            sHomeX = e.getRawX();
            sHomeAt = e.getEventTime();
            sHomeTaken = false;
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (sHomeY < 0f) return;
            float up = sHomeY - e.getRawY(), across = Math.abs(e.getRawX() - sHomeX);
            if (e.getEventTime() - sHomeAt > 650) { sHomeY = -1f; return; }
            if (!sHomeTaken && up > 10f * d && up > across) { sHomeTaken = true; FeedPull.steal(); }
            if (up > 48f * d && up > across * 1.2f) {
                sHomeY = -1f;
                MAIN.post(() -> { if (o.isShowing() && !o.closing) o.close(); });
            }
        } else if (a == MotionEvent.ACTION_UP || (a == MotionEvent.ACTION_CANCEL && !sHomeTaken)) sHomeY = -1f;
    }

    static boolean showing() { SearchOverlay o = shown.get(); return o != null && o.isShowing(); }

    static void closeSoon() {
        SearchOverlay o = shown.get();
        if (o == null || !o.isShowing() || o.launched) return;
        if (o.popup != null) { o.root.removeView(o.popup); o.popup = null; }
        o.close();
    }

    static void dismissNow() {
        SearchOverlay o = shown.get();
        if (o != null && o.isShowing()) { o.finished = true; o.restoreHome(); try { o.dismiss(); } catch (Throwable ignored) { } }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (WidgetPanel.sLock) {
            w.setType(2017);
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            try {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.setTitle("PixelSuiteSearch");
                java.lang.reflect.Field pf = WindowManager.LayoutParams.class.getField("privateFlags");
                pf.setInt(lp, pf.getInt(lp) | 0x10);
                w.setAttributes(lp);
            } catch (Throwable ignored) { }
        }
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setWindowAnimations(0);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        try { Window.class.getMethod("setDecorFitsSystemWindows", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
        try { WindowManager.LayoutParams lp = w.getAttributes(); WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode").setInt(lp, 3); w.setAttributes(lp); } catch (Throwable ignored) { }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!look.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x10;
        w.getDecorView().setSystemUiVisibility(flags);
        try { tintTexts(); } catch (Throwable ignored) { }
        root.setOnApplyWindowInsetsListener((view, in) -> { insets(in); return in; });
        try {
            View.class.getMethod("setWindowInsetsAnimationCallback", WindowInsetsAnimation.Callback.class).invoke(root, new WindowInsetsAnimation.Callback(0) {
                @Override public WindowInsets onProgress(WindowInsets in, List<WindowInsetsAnimation> running) { insets(in); return in; }
            });
        } catch (Throwable ignored) { }
    }

    private boolean clipOpen;

    @Override protected void onStart() {
        super.onStart();
        open = true;
        wasOpen = true;
        openedAt = SystemClock.uptimeMillis();
        kick();
        showKeyboard();
        if (WidgetPanel.sLock) FeedPull.watch(getContext(), true); else FeedPull.listen(getContext(), true);
        if (WidgetPanel.sLock && PixelSearch.lockClipOn(getContext()) && PatronGate.ok(getContext())) { clipOpen = true; LockClipboard.mark(getContext(), true); }
        MAIN.postDelayed(this::showKeyboard, 60);
        MAIN.postDelayed(watchKeys, 1200);
        fetchEngines();
        warm(getContext());
        refreshIndex();
        status();
    }

    private void status() {
        if (!scopes.contains("files") && !scopes.contains("content")) return;
        final Context c = getContext();
        WORK.execute(() -> {
            Bundle b = null;
            try { b = c.getContentResolver().call(Uri.parse("content://" + PixelSearch.AUTHORITY), "status", null, null); } catch (Throwable ignored) { }
            final Bundle s = b;
            MAIN.post(() -> {
                if (s == null || field.length() > 0) return;
                if (!s.getBoolean("access", true)) hint.setText(TextUtils.concat(I18n.cs("Search apps, contacts, files, settings and more"), "\n\n", I18n.cs("Turn Pixel search off and on again in Pixel Suite to allow searching your files.")));
                else if (s.getLong("files", 0) == 0) hint.setText(TextUtils.concat(I18n.cs("Search apps, contacts, files, settings and more"), "\n\n", I18n.cs("Getting your files ready. They show up here in a few minutes.")));
            });
        });
    }

    private void refreshIndex() {
        final Context c = getContext();
        WORK.execute(() -> { try { c.getContentResolver().call(Uri.parse("content://" + PixelSearch.AUTHORITY), "refresh", null, null); } catch (Throwable ignored) { } });
    }

    private void insets(WindowInsets in) {
        int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
        try {
            Class<?> type = Class.forName("android.view.WindowInsets$Type");
            int mask = (Integer) type.getMethod("statusBars").invoke(null) | (Integer) type.getMethod("displayCutout").invoke(null);
            Object i = WindowInsets.class.getMethod("getInsets", int.class).invoke(in, mask);
            top = i.getClass().getField("top").getInt(i);
            int bm = (Integer) type.getMethod("ime").invoke(null) | (Integer) type.getMethod("navigationBars").invoke(null);
            Object j = WindowInsets.class.getMethod("getInsets", int.class).invoke(in, bm);
            bottom = j.getClass().getField("bottom").getInt(j);
        } catch (Throwable ignored) { }
        if (top != topInset || bottom != bottomInset) { topInset = top; bottomInset = bottom; place(); }
    }

    private ImageView icon(Drawable d, String description) {
        ImageView i = new ImageView(getContext());
        i.setImageDrawable(d);
        i.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        i.setPadding(dp(10), dp(10), dp(10), dp(10));
        i.setContentDescription(I18n.cs(description));
        GradientDrawable ripple = new GradientDrawable(); ripple.setShape(GradientDrawable.OVAL); ripple.setColor(Color.TRANSPARENT);
        i.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(PixelSearch.alpha(look.ink, 20)), null, ripple));
        return i;
    }

    int dp(float x) { return Math.round(x * getContext().getResources().getDisplayMetrics().density); }

    private void restoreHome() { if (!feed) dock.setHidden(0); }

    private void land() { if (!feed) { dock.setHidden(0); dock.assemble(); } }

    private void kick() {
        if (running) return;
        running = true; last = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long nanos) {
        if (finished) { running = false; return; }
        float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
        long nowMs = SystemClock.uptimeMillis();
        float to = open ? 1 : 0;
        float[] r;
        if (open) {
            r = WidgetPanel.spring(p, pv, to, 350, .92f, dt); p = r[0]; pv = r[1];
            if (nowMs - openedAt > 45) { r = WidgetPanel.spring(q, qv, to, 270, .95f, dt); q = r[0]; qv = r[1]; }
        } else if (styled) {
            closeU = (nowMs - closedAt) / (float) SearchClose.TRAVEL;
            p = closeU >= SearchClose.CUT ? 0 : closeFrom * (1 - SearchClose.bez(closeU));
            pv = 0; q = 0; qv = 0;
        } else {
            r = WidgetPanel.spring(q, qv, to, 520, .92f, dt); q = r[0]; qv = r[1];
            if (nowMs - closedAt > 40) { r = WidgetPanel.spring(p, pv, to, 460, .78f, dt); p = r[0]; pv = r[1]; }
        }
        boolean settled = Math.abs(p - to) < .001f && Math.abs(pv) < .005f && Math.abs(q - to) < .001f && Math.abs(qv) < .005f;
        if (!open && p < 0.012f) settled = true;
        if (!open && feed && behind != null && !gaveBack && nowMs - closedAt < 1500) settled = false;
        if (settled) { p = q = to; pv = qv = 0; }
        place();
        if (!settled) { Choreographer.getInstance().postFrameCallback(this); return; }
        running = false;
        if (!open) finishClose();
    }

    private float clamp(float x) { return Math.max(0f, Math.min(1f, x)); }
    private float ease(float t) { t = clamp(t); return 1f - (1f - t) * (1f - t) * (1f - t); }
    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private void place() {
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) { root.post(this::place); return; }
        int[] at = new int[2]; root.getLocationOnScreen(at);
        int m = dp(12), hh = dp(56), ht = topInset + dp(10);
        if (feed && behind != null && behind.fieldTop() >= 0) ht = Math.round(behind.fieldTop()) - at[1];
        boolean stock = styled && !open;
        float g = open || stock ? p : Math.max(0f, (p - 0.012f) / 0.988f);
        float cp = clamp(g), fl = from.left - at[0], ft = from.top - at[1], fr = from.right - at[0], fb = from.bottom - at[1];
        float lift = stock || feed ? 0 : dp(46) * (float) Math.sin(Math.PI * cp);
        float in = open || stock ? 0 : puff * (1 - clamp(cp * 5f));
        float sx = stock ? SearchClose.smallX((fr - fl) / 2f) : 0, sy = stock ? SearchClose.smallY(fb - ft) : 0;
        root.card.set(lerp(fl + sx, 0, g) + in, lerp(ft + sy, 0, g) - lift * 0.4f + in, lerp(fr - sx, w, g) - in, lerp(fb - sy, h, g) - lift - in);
        boolean rest = open && p == 1f && q == 1f;
        root.radius = rest ? 0 : Math.min(root.card.height() / 2f, lerp(fromRadius, dp(32), clamp(cp * 1.4f)));
        root.tint = stock ? 1 - clamp((closeU - 0.3f) / 0.4f) : clamp(cp * 6f);
        root.crop = stock;
        root.cropY = 0;
        root.fade = cp;
        wallBlur(cp);
        if (feed) {
            root.card.set(0, 0, w, h);
            root.radius = 0;
            root.tint = 1;
            final_.set(m, ht, w - m, ht + hh);
            long nowAt = SystemClock.uptimeMillis();
            float taken = behind == null ? 1f : behind.handed(nowAt);
            if (taken >= 1f) takenAll = true; else if (takenAll && backAt == 0) { if (!takenNoted) { takenNoted = true; } taken = 1f; }
            if (behind != null && taken < 1f && backAt == 0) startK = behind.shownNow();
            float k = behind != null && taken < 1f && backAt == 0 ? startK : open && behind != null ? Math.max(startK, ease(cp)) : behind != null ? ease(clamp((cp - 0.035f) / 0.965f)) : ease(cp);
            if (!open && wasOpen && behind != null && cp < 0.035f && backAt == 0) { backAt = nowAt; behind.back(); }
            else if (!open && !wasOpen && behind != null && cp < 0.035f && backAt == 0 && !earlyNoted) { earlyNoted = true; }
            float left = backAt == 0 ? 1f : 1f - clamp((nowAt - backAt) / (float) FeedPull.HAND);
            mine = Math.min(taken, left);
            if (behind != null) {
                RectF o = behind.origin();
                if (o != null) { root.getLocationOnScreen(bloomAt); o.offset(-bloomAt[0], -bloomAt[1]); bubble.from(o, behind.originCard(), behind.originInk()); } else if (!open) bubble.from(null, 0, 0);
            }
            if (backAt != 0 && behind != null && !gaveBack) { behind.giving(left); if (left <= 0f) gaveBack = true; }
            bubble.place(final_, k, 0f, 0f);
            pill.set(bubble.rect);
            pillRadius = pill.height() / 2f;
            headerUp = open && bubble.full() && taken >= 1f;
            if (behind != null) behind.level(cp);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) header.getLayoutParams();
        if (lp.width != w - 2 * m || lp.height != hh) { lp.width = w - 2 * m; lp.height = hh; header.setLayoutParams(lp); }
        header.setTranslationX(m);
        header.setTranslationY(ht);
        content.setTranslationY(0);
        content.setPadding(0, ht + hh + dp(6), 0, bottomInset);
        float show = stock ? 0 : clamp(q * 1.5f - 0.2f);
        header.setAlpha(feed ? (headerUp ? 1f : 0f) : clamp(show * 1.6f));
        chips.setAlpha(clamp(show * 1.4f - 0.08f));
        chips.setTranslationY((1 - chips.getAlpha()) * dp(14));
        scroll.setAlpha(clamp(show * 1.4f - 0.16f));
        scroll.setTranslationY((1 - scroll.getAlpha()) * dp(18));
        dock.toLocal(at[0], at[1], spot);
        ghostX = root.card.left - fl - spot[0]; ghostY = root.card.top - ft - spot[1];
        ghostAlpha = open && !feed ? Math.round(255 * clamp(1 - cp * 18f)) : 0;
        ghostScale = 1 - 0.3f * clamp(cp * 18f);
        if (!feed) dock.setHidden(1);
        root.invalidate();
    }

    private void showKeyboard() {
        if (closing) return;
        field.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        try {
            Object controller = Window.class.getMethod("getInsetsController").invoke(getWindow());
            int ime = (Integer) Class.forName("android.view.WindowInsets$Type").getMethod("ime").invoke(null);
            Class.forName("android.view.WindowInsetsController").getMethod("show", int.class).invoke(controller, ime);
        } catch (Throwable ignored) { }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    void close() {
        if (popup != null) { closePopup(); return; }
        if (closing) return;
        closing = true;
        hidePill();
        open = false;
        closeFrom = clamp(p); closeU = 0;
        closedAt = SystemClock.uptimeMillis();
        hideKeyboard();
        kick();
    }

    private void finishClose() {
        if (finished) return;
        finished = true;
        land();
        Choreographer.getInstance().postFrameCallback(n -> { try { dismiss(); } catch (Throwable ignored) { } });
    }

    private void launched() {
        launched = true;
        try { remember(); } catch (Throwable ignored) { }
        hideKeyboard();
        MAIN.postDelayed(() -> { finished = true; restoreHome(); try { dismiss(); } catch (Throwable ignored) { } }, 180);
    }

    @Override public void onBackPressed() { close(); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) { close(); return true; }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(e);
    }

    final class Edge extends FrameLayout {
        final Paint fade = new Paint(), mask = new Paint(), layer = new Paint(), foot = new Paint();
        SoftScrollEdges.NativeBlur soft;
        boolean failed, recording;
        int builtTop = -1, builtBottom = -1, builtFoot = -1;
        Edge(Context c) {
            super(c);
            fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            foot.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }
        @Override protected void dispatchDraw(Canvas c) {
            if (recording) { super.dispatchDraw(c); return; }
            int w = getWidth(), h = getHeight(), line = scroll.getPaddingTop(), reach = dp(28);
            float amount = Math.min(1f, Math.max(0, scroll.getScrollY()) / (float) dp(16));
            boolean more = !feed && scroll.canScrollVertically(1);
            if (w == 0 || h == 0 || line <= 0 || (amount <= 0f && !more)) { super.dispatchDraw(c); return; }
            int bottom = line + reach, start = 0;
            if (builtTop != start || builtBottom != bottom) {
                builtTop = start; builtBottom = bottom;
                float span = Math.max(1, bottom - start), hold = Math.max(0.03f, (line - dp(12) - start) / span);
                fade.setShader(new android.graphics.LinearGradient(0, start, 0, bottom, new int[]{0x00ffffff, 0x00ffffff, 0xffffffff}, new float[]{0f, hold, 1f}, android.graphics.Shader.TileMode.CLAMP));
                mask.setShader(new android.graphics.LinearGradient(0, start, 0, bottom, new int[]{0x00ffffff, 0xffffffff, 0xffffffff, 0x00ffffff}, new float[]{0f, Math.max(0.02f, hold * 0.55f), hold, 1f}, android.graphics.Shader.TileMode.CLAMP));
            }
            if (builtFoot != h) {
                builtFoot = h;
                foot.setShader(new android.graphics.LinearGradient(0, h - dp(22), 0, h, 0xffffffff, 0x00ffffff, android.graphics.Shader.TileMode.CLAMP));
            }
            boolean blurred = false;
            if (amount > 0f && !failed && c.isHardwareAccelerated() && android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    if (soft == null) soft = new SoftScrollEdges.NativeBlur(Math.max(1, FrostedSurface.depthBlurRadius(getContext()) / 2));
                    recording = true;
                    Canvas rec = soft.begin(w, bottom);
                    super.dispatchDraw(rec);
                    soft.end();
                    blurred = true;
                } catch (Throwable t) { failed = true; if (soft != null) soft.finish(); }
                finally { recording = false; }
            }
            int save = c.saveLayer(0, 0, w, h, null);
            super.dispatchDraw(c);
            if (amount > 0f) { fade.setAlpha(255); c.drawRect(0, 0, w, bottom, fade); }
            if (more) c.drawRect(0, h - dp(22), w, h, foot);
            c.restoreToCount(save);
            if (amount <= 0f) return;
            if (amount < 1f) {
                layer.setAlpha(Math.round(255 * (1f - amount)));
                int s2 = c.saveLayer(0, 0, w, bottom, layer);
                c.clipRect(0, 0, w, bottom);
                super.dispatchDraw(c);
                c.restoreToCount(s2);
            }
            if (blurred) {
                layer.setAlpha(Math.round(255 * amount));
                int s3 = c.saveLayer(0, start, w, bottom, layer);
                try { soft.draw(c); c.drawRect(0, start, w, bottom, mask); } catch (Throwable ignored) { }
                c.restoreToCount(s3);
            }
        }
        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); if (soft != null) { try { soft.finish(); soft.release(); } catch (Throwable ignored) { } soft = null; } }
    }

    final class Root extends FrameLayout {
        final RectF card = new RectF();
        float radius, fade, tint, downX, downY, slide, cropY;
        boolean swiping, crop;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clip = new Path(), hole = new Path();
        Root(Context c) { super(c); setWillNotDraw(false); }
        @Override protected void onDraw(Canvas c) {
            if (feed) {
                float f = Math.max(0f, Math.min(1f, fade));
                if (behind == null && frosty) {
                    if (blur == null) blur = frost();
                    if (blur != null) {
                        if (blurRadius != 0) { try { LauncherFeatures.call(blur, "setCornerRadius", 0f); } catch (Throwable ignored) { } blurRadius = 0; }
                        blur.setBounds(0, 0, getWidth(), getHeight());
                        blur.setAlpha(Math.round(255 * f));
                        blur.draw(c);
                    }
                }
                int sa = Math.round(Color.alpha(surfaceColor) * f);
                scrim.setColor((surfaceColor & 0xffffff) | (sa << 24));
                float share = behind == null || gaveBack ? 1f : !told ? 0f : mine;
                if (share < 1f && !bubble.rect.isEmpty()) {
                    float pr = bubble.rect.height() / 2f;
                    hole.reset(); hole.addRoundRect(bubble.rect, pr, pr, Path.Direction.CW); hole.setFillType(Path.FillType.INVERSE_WINDING);
                    c.drawPath(hole, scrim);
                    scrim.setColor((surfaceColor & 0xffffff) | (Math.round(sa * share) << 24));
                    c.drawRoundRect(bubble.rect, pr, pr, scrim);
                } else c.drawRect(0, 0, getWidth(), getHeight(), scrim);
                if (!told && behind != null) { told = true; behind.handing(SystemClock.uptimeMillis()); mine = 0f; }
                bubble.ink = behind != null && backAt == 0 && !closing ? 1f : -1f;
                if (!gaveBack) bubble.draw(c, getLayoutDirection() == LAYOUT_DIRECTION_RTL, !headerUp, field.getText(), mine, 1f);
                return;
            }
            scrim.setColor(Color.argb(Math.round(120 * fade), 0, 0, 0));
            c.drawRect(0, 0, getWidth(), getHeight(), scrim);
            int color = PixelSearch.mix(fromColor, (look.surface & 0xffffff) | (Math.round(Color.alpha(look.surface) * shade() / 100f) << 24), tint);
            if (frosty && (glass || tint >= 1f) && Color.alpha(color) < 255) {
                if (blur == null) blur = frost();
                if (blur != null) {
                    if (Math.abs(blurRadius - radius) > .5f) { try { LauncherFeatures.call(blur, "setCornerRadius", radius); } catch (Throwable ignored) { } blurRadius = radius; }
                    blur.setBounds(Math.round(card.left), Math.round(card.top), Math.round(card.right), Math.round(card.bottom));
                    blur.draw(c);
                }
            }
            fill.setColor(color);
            c.drawRoundRect(card, radius, radius, fill);
            if (glass && tint < 1f) {
                if (rim == null || Math.abs(rimRadius - radius) > .5f) { rim = new GlassRim(radius, dp(1.5f), look.dark); rimRadius = radius; }
                rim.draw(c, card.left, card.top, card.right, card.bottom, radius, Math.round((1 - tint) * 255 * Math.max(.35f, look.shellOpacity / 100f)));
            }
        }
        private Drawable frost() {
            try {
                Object vri = LauncherFeatures.call(this, "getViewRootImpl");
                Drawable d = (Drawable) LauncherFeatures.call(vri, "createBackgroundBlurDrawable");
                LauncherFeatures.call(d, "setBlurRadius", Math.round(FrostedSurface.depthBlurRadius(getContext()) * 0.6f));
                try { LauncherFeatures.call(d, "setColor", Color.TRANSPARENT); } catch (Throwable ignored) { }
                return d;
            } catch (Throwable t) { return null; }
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); blur = null; blurRadius = -1; }
        @Override protected void dispatchDraw(Canvas c) {
            int s = c.save();
            clip.reset(); clip.addRoundRect(card, radius, radius, Path.Direction.CW);
            c.clipPath(clip);
            int inner = c.save();
            if (crop) c.translate(card.centerX() - getWidth() / 2f, card.top + Math.min(0f, cropY));
            else {
                c.translate(card.left, card.top);
                float k = card.width() / Math.max(1, getWidth());
                c.scale(k, k);
            }
            drawChild(c, content, getDrawingTime());
            if (feed) { int hs = c.save(); clip.reset(); clip.addRoundRect(pill, pillRadius, pillRadius, Path.Direction.CW); c.clipPath(clip); drawChild(c, header, getDrawingTime()); c.restoreToCount(hs); }
            else drawChild(c, header, getDrawingTime());
            c.restoreToCount(inner);
            if (ghostAlpha > 0) {
                c.translate(ghostX, ghostY);
                dock.drawGhost(c, ghostScale, ghostAlpha);
            }
            c.restoreToCount(s);
            if (popup != null) drawChild(c, popup, getDrawingTime());
            if (keysPill != null && keysPill.getVisibility() == View.VISIBLE) drawChild(c, keysPill, getDrawingTime());
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); place(); }

        private float tapX, tapY;
        private long tapAt;
        private boolean tapSolid, tapMoved;

        private boolean over(View v, float x, float y) {
            if (v == null || !v.isShown() || v.getAlpha() <= 0.01f || v.getWidth() <= 0) return false;
            int[] l = new int[2];
            v.getLocationOnScreen(l);
            return x >= l[0] && y >= l[1] && x < l[0] + v.getWidth() && y < l[1] + v.getHeight();
        }

        private boolean solidAt(float x, float y) {
            if (popup != null || over(header, x, y) || (keysPill != null && over(keysPill, x, y))) return true;
            for (int i = 0; i < chips.getChildCount(); i++) if (over(chips.getChildAt(i), x, y)) return true;
            for (int i = 0; i < list.getChildCount(); i++) { View v = list.getChildAt(i); if (v != hint && over(v, x, y)) return true; }
            return false;
        }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { tapX = e.getRawX(); tapY = e.getRawY(); tapAt = e.getEventTime(); tapMoved = false; tapSolid = solidAt(tapX, tapY); }
            else if (a == MotionEvent.ACTION_MOVE && Math.hypot(e.getRawX() - tapX, e.getRawY() - tapY) > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) tapMoved = true;
            else if (a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_CANCEL) tapMoved = true;
            if (WidgetPanel.sLock) edgeSwipe(e, a);
            boolean popupBefore = popup != null;
            boolean handled = super.dispatchTouchEvent(e);
            if (a == MotionEvent.ACTION_UP && !tapMoved && !tapSolid && !popupBefore && !swiping && !closing && e.getEventTime() - tapAt < 400) close();
            return handled;
        }

        private int edgeFrom;

        private void edgeSwipe(MotionEvent e, int a) {
            float d = getResources().getDisplayMetrics().density;
            if (a == MotionEvent.ACTION_DOWN) {
                float x = e.getRawX();
                edgeFrom = x <= 24 * d ? 1 : x >= getRootView().getWidth() - 24 * d ? -1 : 0;
            } else if (a == MotionEvent.ACTION_MOVE && edgeFrom != 0) {
                float dx = (e.getRawX() - tapX) * edgeFrom, dy = Math.abs(e.getRawY() - tapY);
                if (e.getEventTime() - tapAt > 600) edgeFrom = 0;
                else if (dx > 56 * d && dx > dy * 1.5f) {
                    edgeFrom = 0;
                    tapMoved = true;
                    if (closing || popup != null) return;
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    if (bottomInset > 160 * d) hideKeyboard(); else close();
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) edgeFrom = 0;
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { downX = e.getRawX(); downY = e.getRawY(); swiping = false; return false; }
            if (edgeFrom != 0) return false;
            if (a != MotionEvent.ACTION_MOVE || closing || popup != null || chips.getChildCount() < 2) return false;
            int[] at = new int[2]; scroll.getLocationOnScreen(at);
            if (downY < at[1] + scroll.getPaddingTop()) return false;
            float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
            if (Math.abs(dx) < android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop() * 1.5f || Math.abs(dx) < Math.abs(dy) * 1.6f) return false;
            swiping = true;
            return true;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!swiping) return super.onTouchEvent(e);
            int a = e.getActionMasked();
            float dx = e.getRawX() - downX;
            if (a == MotionEvent.ACTION_MOVE) {
                slide = dx * 0.55f;
                scroll.setTranslationX(slide);
                scroll.setAlpha(Math.max(0.35f, 1 - Math.abs(slide) / dp(220)));
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                swiping = false;
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                int step = a == MotionEvent.ACTION_UP && Math.abs(dx) > dp(56) ? (dx < 0 == !rtl ? 1 : -1) : 0;
                if (step != 0 && turn(step)) {
                    scroll.setTranslationX(-Math.signum(dx) * dp(70));
                    scroll.setAlpha(0f);
                }
                scroll.animate().translationX(0).alpha(1).setDuration(420).setInterpolator(new Spring(0.62f)).start();
            }
            return true;
        }
    }

    private boolean turn(int step) {
        int now = 0;
        for (int i = 0; i < chips.getChildCount(); i++) {
            String tag = (String) chips.getChildAt(i).getTag();
            if ((filter == null && tag.isEmpty()) || tag.equals(filter)) now = i;
        }
        int next = now + step;
        if (next < 0 || next >= chips.getChildCount()) return false;
        View chip = chips.getChildAt(next);
        String tag = (String) chip.getTag();
        filter = tag.isEmpty() ? null : tag;
        chip.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        styleChips();
        pop(chip);
        if (chips.getParent() instanceof HorizontalScrollView) ((HorizontalScrollView) chips.getParent()).smoothScrollTo(Math.max(0, chip.getLeft() - dp(48)), 0);
        shownKeys = new java.util.HashSet<String>();
        render();
        return true;
    }

    private void buildChips() {
        chips.removeAllViews();
        ArrayList<String> tabs = new ArrayList<String>();
        for (String s : scopes) if (!"tools".equals(s) && !"web".equals(s)) tabs.add(s);
        int at = Math.max(0, Math.min(tabs.size(), PixelSearch.number(getContext(), PixelSearch.ALL_AT_KEY, 0)));
        for (int i = 0; i <= tabs.size(); i++) {
            if (i == at) addChip(null, "All");
            if (i < tabs.size()) addChip(tabs.get(i), chipName(tabs.get(i)));
        }
        if (!tabChosen) {
            tabChosen = true;
            if (at > 0 && !tabs.isEmpty()) { filter = tabs.get(0); styleChips(); }
        }
    }

    private boolean tabChosen, chipDragging;

    private void tabMenu(final TextView t, final String scope) {
        closePopup();
        LinearLayout card = menu(t, false);
        try {
            int[] at = new int[2], base = new int[2];
            t.getLocationOnScreen(at); root.getLocationOnScreen(base);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            lp.width = dp(190);
            if (root.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) lp.setMarginStart(Math.max(dp(12), Math.min(at[0] - base[0], root.getWidth() - dp(190) - dp(12))));
            card.setLayoutParams(lp);
        } catch (Throwable ignored) { }
        menuRow(card, new SearchGlyph(SearchGlyph.PIN, look.text), I18n.cs("Pin"), false, () -> {
            closePopup();
            chips.removeView(t); chips.addView(t, 0);
            if (chips.getParent() instanceof HorizontalScrollView) ((HorizontalScrollView) chips.getParent()).smoothScrollTo(0, 0);
            pop(t);
            saveChipOrder();
        });
        if (scope != null) menuRow(card, new SearchGlyph("hide", look.text), I18n.cs("Hide"), false, () -> {
            closePopup();
            Context c = getContext();
            List<String> all = PixelSearch.scopes(c);
            for (int i = 0; i < all.size(); i++) if (scope.equals(all.get(i))) all.set(i, "-" + scope);
            scopes.remove(scope);
            if (scope.equals(filter)) filter = null;
            try { android.provider.Settings.Secure.putString(c.getContentResolver(), PixelSearch.SCOPES_KEY, PixelSearch.encodeScopes(all)); } catch (Throwable ignored) { }
            buildChips();
            styleChips();
            shownKeys = new java.util.HashSet<String>();
            query(field.getText().toString());
        });
    }

    private String chipName(String s) {
        if ("content".equals(s)) return "Inside files";
        if ("files".equals(s)) return "Files";
        return PixelSearch.scopeName(s);
    }

    private void addChip(final String scope, String name) {
        final TextView t = new TextView(getContext());
        t.setText(I18n.cs(name));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(12), dp(8), dp(16), dp(8));
        t.setTag(scope == null ? "" : scope);
        t.setSingleLine(true);
        SearchGlyph mark = new SearchGlyph(PixelSearch.scopeGlyph(scope), look.text);
        mark.setBounds(0, 0, dp(16), dp(16));
        t.setCompoundDrawablesRelative(mark, null, null, null);
        t.setCompoundDrawablePadding(dp(6));
        t.setOnLongClickListener(x -> {
            heldChip = t; chipDragging = false;
            x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            x.getParent().requestDisallowInterceptTouchEvent(true);
            if (chips.getParent() != null) chips.getParent().requestDisallowInterceptTouchEvent(true);
            pop(t);
            tabMenu(t, scope);
            return true;
        });
        t.setOnTouchListener((x, e) -> chipTouch(t, e));
        t.setOnClickListener(x -> {
            x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            filter = scope;
            styleChips();
            pop(x);
            render();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(dp(8));
        chips.addView(t, lp);
        styleChips();
    }

    private void styleChips() {
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView t = (TextView) chips.getChildAt(i);
            String s = (String) t.getTag();
            boolean on = (filter == null && s.isEmpty()) || s.equals(filter);
            t.setBackground(on ? panel(look.chipOn, 18) : panel(feed ? cardColor : look.chip, 18));
            t.setTextColor(on ? look.onChip : look.text);
            Drawable mark = t.getCompoundDrawablesRelative()[0];
            if (mark instanceof SearchGlyph) ((SearchGlyph) mark).setColor(on ? look.onChip : look.text);
        }
    }

    private float chipDown;

    private boolean chipTouch(TextView t, MotionEvent e) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) { chipDown = e.getRawX(); return false; }
        if (heldChip != t) return false;
        if (act == MotionEvent.ACTION_MOVE) {
            if (!chipDragging) {
                if (Math.abs(e.getRawX() - chipDown) < dp(12)) return true;
                chipDragging = true;
                closePopup();
                t.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                t.setElevation(dp(6));
                t.animate().scaleX(1.1f).scaleY(1.1f).setDuration(240).setInterpolator(new Spring(0.5f)).start();
                chipDown = e.getRawX();
                MAIN.removeCallbacks(chipEdge);
                MAIN.post(chipEdge);
            }
            chipAt = e.getRawX();
            dragChip(t, chipAt);
            return true;
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            boolean moved = chipDragging;
            MAIN.removeCallbacks(chipEdge);
            heldChip = null; chipGrab = 0; chipDragging = false;
            if (moved) {
                t.animate().translationX(0).scaleX(1).scaleY(1).setDuration(360).setInterpolator(new Spring(0.6f)).withEndAction(() -> t.setElevation(0)).start();
                saveChipOrder();
            }
            return true;
        }
        return true;
    }

    private float chipAt;

    private final Runnable chipEdge = new Runnable() {
        public void run() {
            TextView t = heldChip;
            if (t == null || !chipDragging || !(chips.getParent() instanceof HorizontalScrollView)) return;
            HorizontalScrollView hs = (HorizontalScrollView) chips.getParent();
            int[] at = new int[2];
            hs.getLocationOnScreen(at);
            float edge = dp(56), left = chipAt - at[0], right = at[0] + hs.getWidth() - chipAt;
            int step = left < edge ? -Math.round(dp(11) * (1 - Math.max(0f, left) / edge) + dp(2)) : right < edge ? Math.round(dp(11) * (1 - Math.max(0f, right) / edge) + dp(2)) : 0;
            if (step != 0) {
                int before = hs.getScrollX();
                hs.scrollBy(step, 0);
                int moved = hs.getScrollX() - before;
                if (moved != 0) { chipGrab -= moved; dragChip(t, chipAt); }
            }
            MAIN.postDelayed(this, 16);
        }
    };

    private void dragChip(TextView t, float rawX) {
        if (chipGrab == 0) chipGrab = chipDown;
        t.setTranslationX(rawX - chipGrab);
        int i = chips.indexOfChild(t);
        boolean rtl = chips.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float mid = t.getX() + t.getWidth() / 2f;
        for (int dir = -1; dir <= 1; dir += 2) {
            int j = i + (rtl ? -dir : dir);
            if (j < 0 || j >= chips.getChildCount()) continue;
            View other = chips.getChildAt(j);
            float otherMid = other.getLeft() + other.getWidth() / 2f;
            if (dir > 0 ? mid <= otherMid : mid >= otherMid) continue;
            float mine = other.getWidth() + dp(8), theirs = t.getWidth() + dp(8);
            chips.removeView(other);
            chips.addView(other, i);
            chipGrab += dir * mine;
            t.setTranslationX(rawX - chipGrab);
            other.animate().cancel();
            other.setTranslationX(dir * theirs);
            other.animate().translationX(0).setDuration(320).setInterpolator(new Spring(0.7f)).start();
            t.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            break;
        }
    }

    private void saveChipOrder() {
        ArrayList<String> order = new ArrayList<String>();
        int allAt = 0;
        for (int i = 0; i < chips.getChildCount(); i++) { String tag = (String) chips.getChildAt(i).getTag(); if (!tag.isEmpty()) order.add(tag); else allAt = order.size(); }
        Context c = getContext();
        try { android.provider.Settings.Secure.putInt(c.getContentResolver(), PixelSearch.ALL_AT_KEY, allAt); PixelSearch.LIVE.remove(PixelSearch.ALL_AT_KEY); } catch (Throwable ignored) { }
        List<String> all = PixelSearch.scopes(c);
        int next = 0;
        for (int i = 0; i < all.size() && next < order.size(); i++) if (order.contains(all.get(i))) all.set(i, order.get(next++));
        next = 0;
        for (int i = 0; i < scopes.size() && next < order.size(); i++) if (order.contains(scopes.get(i))) scopes.set(i, order.get(next++));
        try { android.provider.Settings.Secure.putString(c.getContentResolver(), PixelSearch.SCOPES_KEY, PixelSearch.encodeScopes(all)); }
        catch (Throwable t) { android.util.Log.w(TAG, "search: tab order", t); }
        shownKeys = new java.util.HashSet<String>();
        render();
    }

    private void pop(View x) {
        x.setScaleX(0.9f); x.setScaleY(0.9f);
        x.animate().scaleX(1).scaleY(1).setDuration(380).setInterpolator(new Spring(0.45f)).start();
    }

    static final class Spring implements android.animation.TimeInterpolator {
        final float ratio; Spring(float ratio) { this.ratio = ratio; }
        @Override public float getInterpolation(float t) {
            float w = 12f, zeta = ratio;
            if (zeta >= 1) return (float) (1 - Math.exp(-w * t) * (1 + w * t));
            double wd = w * Math.sqrt(1 - zeta * zeta);
            return (float) (1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + zeta * w / wd * Math.sin(wd * t)));
        }
    }

    private void enter() {
        String q = field.getText().toString().trim();
        if (q.isEmpty()) return;
        if (PixelSearch.number(getContext(), PixelSearch.ENTER_APP_KEY, 1) != 1) { web(q); return; }
        for (Result r : results) {
            if ("apps".equals(r.scope) && r.apps != null && !r.apps.isEmpty() && (filter == null || "apps".equals(filter))) { launchApp(r.apps.get(0), null); return; }
        }
        for (Result r : results) if ("tools".equals(r.scope)) { copy(r.title); return; }
        web(q);
    }

    private void web(String q) {
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PixelSearch.webUrl(engine, q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            launched();
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("result", text));
            toast("Copied");
        } catch (Throwable ignored) { }
    }

    private void toast(String s) { Toast.makeText(getContext(), I18n.cs(s), Toast.LENGTH_SHORT).show(); }

    static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private static final String[] T9 = {"0", "1", "abc2", "def3", "ghi4", "jkl5", "mno6", "pqrs7", "tuv8", "wxyz9"};

    static String t9(String norm) {
        StringBuilder b = new StringBuilder(norm.length());
        for (int i = 0; i < norm.length(); i++) {
            char c = norm.charAt(i);
            if (c == ' ') { b.append(' '); continue; }
            char d = 0;
            for (int k = 0; k < T9.length && d == 0; k++) if (T9[k].indexOf(c) >= 0) d = (char) ('0' + k);
            if (d != 0) b.append(d);
        }
        return b.toString();
    }

    static int score(String norm, String q) {
        if (q.isEmpty()) return 0;
        if (norm.equals(q)) return 120;
        if (norm.startsWith(q)) return 100;
        int i = norm.indexOf(q);
        if (i > 0 && !Character.isLetterOrDigit(norm.charAt(i - 1))) return 85;
        if (i > 0) return 60;
        int at = 0;
        for (int k = 0; k < q.length(); k++) { at = norm.indexOf(q.charAt(k), at); if (at < 0) return 0; at++; }
        return q.length() >= 3 ? 25 : 0;
    }

    private static void fill() {
        SearchOverlay o = shown.get();
        if (o == null || !o.isShowing() || o.closing || o.finished || o.field.length() > 0 || o.popup != null || o.early) return;
        try { o.render(); } catch (Throwable t) { android.util.Log.w(TAG, "search: fill", t); }
    }

    static void warm(final Context c) {
        WORK.execute(() -> {
            long now = SystemClock.uptimeMillis();
            if (sApps != null && now - sAppsAt < 60000) return;
            final boolean first = sApps == null || sApps.isEmpty();
            sApps = loadApps(c);
            ICON_CACHE.evictAll();
            if (first) MAIN.post(SearchOverlay::fill);
            sShortcuts = loadShortcuts(c);
            if (sSettings == null) sSettings = loadSettings(c);
            sAppsAt = now;
        });
    }

    private static List<App> loadApps(Context c) {
        ArrayList<App> out = new ArrayList<App>();
        try {
            LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            UserManager um = (UserManager) c.getSystemService(Context.USER_SERVICE);
            for (UserHandle u : um.getUserProfiles()) {
                for (LauncherActivityInfo i : la.getActivityList(null, u)) {
                    App a = new App();
                    a.label = String.valueOf(i.getLabel());
                    a.norm = norm(a.label);
                    a.t9 = t9(a.norm);
                    a.cn = i.getComponentName(); a.pkg = a.cn.getPackageName(); a.user = u; a.info = i;
                    out.add(a);
                }
            }
            try {
                HashMap<String, Object> shown = new HashMap<String, Object>();
                for (Object o : LauncherFeatures.apps()) {
                    Object cn = LauncherFeatures.get(o, "componentName");
                    if (cn instanceof ComponentName) shown.put(((ComponentName) cn).flattenToString() + "#" + LauncherFeatures.get(o, "user"), o);
                }
                for (App a : out) {
                    Object o = shown.get(a.cn.flattenToString() + "#" + a.user);
                    if (o == null) continue;
                    Object title = LauncherFeatures.get(o, "title");
                    if (title instanceof CharSequence && ((CharSequence) title).length() > 0 && !title.toString().equals(a.label)) {
                        a.alt = a.norm; a.label = title.toString(); a.norm = norm(a.label); a.t9 = t9(a.norm);
                    }
                    a.bitmap = LauncherFeatures.get(o, "bitmap");
                }
            } catch (Throwable t) { android.util.Log.w(TAG, "search: launcher names", t); }
            Collections.sort(out, (x, y) -> x.norm.compareTo(y.norm));
        } catch (Throwable t) { android.util.Log.w(TAG, "search: apps", t); }
        return out;
    }

    private static List<Shortcut> loadShortcuts(Context c) {
        ArrayList<Shortcut> out = new ArrayList<Shortcut>();
        HashMap<String, String> names = new HashMap<String, String>();
        if (sApps != null) for (App a : sApps) if (!names.containsKey(a.pkg)) names.put(a.pkg, a.label);
        for (Object info : SearchDock.shortcuts(c, null)) {
            Shortcut s = new Shortcut();
            s.label = SearchDock.shortcutText(info, "getShortLabel");
            if (s.label.isEmpty()) s.label = SearchDock.shortcutText(info, "getLongLabel");
            if (s.label.isEmpty()) continue;
            s.pkg = SearchDock.shortcutText(info, "getPackage"); s.id = SearchDock.shortcutText(info, "getId"); s.info = info;
            s.app = names.containsKey(s.pkg) ? names.get(s.pkg) : s.pkg;
            s.norm = norm(s.label);
            out.add(s);
        }
        return out;
    }

    private static final String[][] SETTINGS = {
            {"android.settings.WIFI_SETTINGS", "Wi-Fi", "wifi wireless internet network"},
            {"android.settings.BLUETOOTH_SETTINGS", "Bluetooth", "bluetooth pair headphones"},
            {"android.settings.DATA_ROAMING_SETTINGS", "Mobile network", "sim mobile data roaming carrier"},
            {"android.settings.WIRELESS_SETTINGS", "Network & internet", "network internet"},
            {"android.settings.AIRPLANE_MODE_SETTINGS", "Airplane mode", "airplane flight"},
            {"android.settings.NFC_SETTINGS", "NFC", "nfc tap pay contactless"},
            {"android.settings.CAST_SETTINGS", "Cast", "cast screen mirror tv"},
            {"android.settings.DISPLAY_SETTINGS", "Display", "display screen brightness timeout font size"},
            {"android.settings.NIGHT_DISPLAY_SETTINGS", "Night Light", "night light blue light eye comfort"},
            {"android.settings.DARK_THEME_SETTINGS", "Dark theme", "dark theme mode black"},
            {"android.settings.SOUND_SETTINGS", "Sound & vibration", "sound volume ringtone vibration"},
            {"android.settings.ZEN_MODE_SETTINGS", "Do Not Disturb", "do not disturb modes silent focus"},
            {"android.settings.ALL_APPS_NOTIFICATION_SETTINGS", "Notifications", "notifications alerts"},
            {"android.settings.APPLICATION_SETTINGS", "Apps", "apps applications installed"},
            {"android.settings.MANAGE_DEFAULT_APPS_SETTINGS", "Default apps", "default apps browser phone sms"},
            {"android.intent.action.POWER_USAGE_SUMMARY", "Battery", "battery usage power"},
            {"android.settings.BATTERY_SAVER_SETTINGS", "Battery Saver", "battery saver power saving"},
            {"android.settings.INTERNAL_STORAGE_SETTINGS", "Storage", "storage space memory free up"},
            {"android.settings.SECURITY_SETTINGS", "Security & privacy", "security lock fingerprint face unlock pin"},
            {"android.settings.PRIVACY_SETTINGS", "Privacy", "privacy permissions"},
            {"android.settings.LOCATION_SOURCE_SETTINGS", "Location", "location gps"},
            {"android.settings.ACCESSIBILITY_SETTINGS", "Accessibility", "accessibility talkback magnify"},
            {"android.settings.DATE_SETTINGS", "Date & time", "date time clock timezone"},
            {"android.settings.LOCALE_SETTINGS", "Languages", "language languages locale"},
            {"android.settings.INPUT_METHOD_SETTINGS", "Keyboard", "keyboard input gboard"},
            {"android.settings.APPLICATION_DEVELOPMENT_SETTINGS", "Developer options", "developer options usb debugging"},
            {"android.settings.DEVICE_INFO_SETTINGS", "About phone", "about phone model build number android version"},
            {"android.settings.SYNC_SETTINGS", "Passwords & accounts", "accounts passwords sync google account"},
            {"android.settings.DATA_USAGE_SETTINGS", "Data usage", "data usage mobile data"},
            {"android.settings.USER_SETTINGS", "Users", "users guest multiple"},
            {"android.settings.VPN_SETTINGS", "VPN", "vpn"},
            {"android.settings.PRINT_SETTINGS", "Printing", "print printer"},
            {"android.settings.SYSTEM_UPDATE_SETTINGS", "System update", "system update software update"},
            {"android.intent.action.SET_WALLPAPER", "Wallpaper & style", "wallpaper style theme colors"},
            {"android.settings.SETTINGS", "Settings", "settings"}};

    private static List<Setting> loadSettings(Context c) {
        ArrayList<Setting> out = new ArrayList<Setting>();
        PackageManager pm = c.getPackageManager();
        String generic = null;
        try { ResolveInfo r = pm.resolveActivity(new Intent("android.settings.SETTINGS"), 0); if (r != null) generic = String.valueOf(r.loadLabel(pm)); } catch (Throwable ignored) { }
        for (String[] s : SETTINGS) {
            try {
                Intent i = new Intent(s[0]);
                ResolveInfo r = pm.resolveActivity(i, 0);
                if (r == null || r.activityInfo == null) continue;
                Setting x = new Setting();
                String label = String.valueOf(r.loadLabel(pm));
                boolean main = "android.settings.SETTINGS".equals(s[0]);
                x.label = !main && (label.isEmpty() || label.equals(generic)) ? I18n.t(s[1]) : label;
                x.norm = norm(x.label + " " + s[1] + " " + s[2]);
                x.intent = i;
                out.add(x);
            } catch (Throwable ignored) { }
        }
        return out;
    }

    private void query(final String raw) {
        final int gen = ++generation;
        final String q = raw.trim();
        if (q.isEmpty()) { results = new ArrayList<Result>(); render(); return; }
        final Context c = getContext();
        MAIN.postDelayed(() -> {
            if (gen != generation) return;
            WORK.execute(() -> {
                if (gen != generation) return;
                final List<Result> local = local(c, q);
                MAIN.post(() -> { if (gen == generation) { results = local; render(); } });
                final List<Result> remote = remote(c, q, gen);
                if (remote.isEmpty()) return;
                MAIN.post(() -> {
                    if (gen != generation) return;
                    ArrayList<Result> merged = new ArrayList<Result>(local);
                    merged.addAll(remote);
                    results = merged;
                    render();
                });
            });
        }, 40);
    }

    private List<Result> local(Context c, String q) {
        ArrayList<Result> out = new ArrayList<Result>();
        String n = norm(q);
        String digits = n.replaceAll("[^0-9]", "");
        boolean allDigits = digits.length() >= 2 && digits.length() == n.replace(" ", "").length();
        if (scopes.contains("tools")) {
            String calc = SearchCalc.calculate(q.replaceAll("(?<=[0-9)])\\s*[xX\u00d7]\\s*(?=[0-9(.])", "*").replace('\u00f7', '/'));
            String conv = calc == null ? SearchCalc.convert(q) : null;
            String[] money = calc == null && conv == null ? SearchMoney.convert(c, q) : null;
            if (calc != null || conv != null || money != null) {
                Result r = new Result(); r.scope = "tools";
                r.title = money != null ? money[1] : calc != null ? calc : conv.substring(conv.indexOf('=') + 1).trim();
                r.sub = money != null ? money[0] : calc != null ? q.trim() + " =" : conv.substring(0, conv.indexOf('=')).trim() + " =";
                final String copyText = r.title;
                r.open = () -> copy(copyText);
                out.add(r);
            }
        }
        if (scopes.contains("apps") && sApps != null) {
            ArrayList<App> found = new ArrayList<App>();
            final HashMap<App, Integer> sc = new HashMap<App, Integer>();
            for (App a : sApps) {
                int s = score(a.norm, n);
                if (a.alt != null) s = Math.max(s, score(a.alt, n));
                if (s == 0 && allDigits) { if (a.t9.startsWith(digits)) s = 50; else if (a.t9.contains(" " + digits)) s = 40; }
                if (s > 0) { sc.put(a, s); found.add(a); }
            }
            Collections.sort(found, (x, y) -> sc.get(y) - sc.get(x));
            if (!found.isEmpty()) { Result r = new Result(); r.scope = "apps"; r.apps = found.size() > 10 ? new ArrayList<App>(found.subList(0, 10)) : found; out.add(r); }
        }
        if (scopes.contains("shortcuts") && sShortcuts != null && n.length() >= 2) {
            int k = 0;
            for (final Shortcut s : sShortcuts) {
                if (score(s.norm, n) < 60 && score(norm(s.app), n) < 100) continue;
                Result r = new Result(); r.scope = "shortcuts"; r.title = s.label; r.sub = s.app; r.data = s.pkg + "/" + s.id;
                r.open = () -> { if (FeedPull.locked(c)) { launched(); FeedPull.unlocked(c, () -> SearchDock.startShortcut(c, s.pkg, s.id, null)); } else if (SearchDock.startShortcut(c, s.pkg, s.id, null)) launched(); };
                out.add(r);
                if (++k >= 6) break;
            }
        }
        if (scopes.contains("settings") && sSettings != null && n.length() >= 2) {
            int k = 0;
            for (final Setting s : sSettings) {
                if (score(s.norm, n) < 60 && !(" " + s.norm).contains(" " + n)) continue;
                Result r = new Result(); r.scope = "settings"; r.title = s.label; r.sub = I18n.t("Android settings"); r.data = "android"; r.extra = s.intent.toUri(0);
                r.open = () -> { try { c.startActivity(new Intent(s.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); launched(); } catch (Throwable t) { toast("Couldn't open it"); } };
                out.add(r);
                if (++k >= 4) break;
            }
        }
        if (scopes.contains("web")) {
            Result r = new Result(); r.scope = "web";
            r.title = I18n.t("Search {0} for “{1}”").replace("{0}", PixelSearch.engineName(engine)).replace("{1}", q);
            r.sub = PixelSearch.engineName(engine);
            r.open = () -> web(q);
            out.add(r);
        }
        return out;
    }

    private List<Result> remote(final Context c, String q, int gen) {
        ArrayList<Result> out = new ArrayList<Result>();
        StringBuilder want = new StringBuilder();
        for (String s : scopes) if ("contacts".equals(s) || "files".equals(s) || "content".equals(s) || "calendar".equals(s) || "settings".equals(s)) { if (want.length() > 0) want.append(','); want.append(s); }
        if (want.length() == 0) return out;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Uri.parse("content://" + PixelSearch.AUTHORITY + "/q"), null, null, new String[]{q, want.toString()}, null);
            if (cur == null) return out;
            while (cur.moveToNext() && gen == generation) {
                final Result r = new Result();
                r.scope = cur.getString(0); r.title = cur.getString(1); r.sub = cur.getString(2); r.data = cur.getString(3); r.extra = cur.getString(4); r.page = cur.getInt(5); r.id = cur.getString(6);
                if ("suite".equals(r.scope)) r.scope = "settings";
                wire(c, r);
                out.add(r);
            }
        } catch (Throwable t) { android.util.Log.w(TAG, "search: remote", t); }
        finally { if (cur != null) cur.close(); }
        return out;
    }

    private void wire(final Context c, final Result r) {
        if ("settings".equals(r.scope)) {
            if ("android".equals(r.data)) r.open = () -> { try { c.startActivity(Intent.parseUri(r.extra, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); launched(); } catch (Throwable t) { toast("Couldn't open it"); } };
            else r.open = () -> openSuite(c, r.data, r.extra);
        } else if ("shortcuts".equals(r.scope)) {
            final String[] p = r.data.split("/", 2);
            r.open = () -> { if (p.length == 2 && FeedPull.locked(c)) { launched(); FeedPull.unlocked(c, () -> SearchDock.startShortcut(c, p[0], p[1], null)); } else if (p.length == 2 && SearchDock.startShortcut(c, p[0], p[1], null)) launched(); else toast("Couldn't open it"); };
        } else if ("contacts".equals(r.scope)) r.open = () -> openContact(c, r);
        else if ("files".equals(r.scope) || "content".equals(r.scope)) { r.open = () -> { suiteAction(c, "open", r.data); launched(); }; r.longPress = () -> preview(r); }
        else if ("calendar".equals(r.scope)) r.open = () -> openEvent(c, r);
    }

    private static String[] record(Result r) {
        return new String[]{r.scope, r.title == null ? "" : r.title, r.sub == null ? "" : r.sub, r.data == null ? "" : r.data, r.extra == null ? "" : r.extra, String.valueOf(r.page), r.id == null ? "" : r.id};
    }

    private static String[] record(App a) { return new String[]{"apps", a.label, "", a.cn.flattenToString(), "", "0", ""}; }

    private static String favKey(String[] f) { return f[0] + "|" + f[3] + ("apps".equals(f[0]) ? "" : "|" + f[1]); }

    private boolean favorite(String[] rec) {
        String key = favKey(rec);
        for (String[] f : PixelSearch.favorites(getContext())) if (favKey(f).equals(key)) return true;
        return false;
    }

    private void toggleFavorite(String[] rec, View from) {
        Context c = getContext();
        List<String[]> list = PixelSearch.favorites(c);
        String key = favKey(rec);
        boolean had = false;
        for (int i = list.size() - 1; i >= 0; i--) if (favKey(list.get(i)).equals(key)) { list.remove(i); had = true; }
        if (!had) list.add(rec);
        try { android.provider.Settings.Secure.putString(c.getContentResolver(), PixelSearch.FAVS_KEY, PixelSearch.encodeFavorites(list)); }
        catch (Throwable t) { toast("Couldn't save it"); return; }
        if (from != null) { from.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); pop(from); }
        toast(had ? "Removed from favorites" : "Added to favorites");
        final int at = scroll.getScrollY();
        MAIN.postDelayed(() -> { render(); scroll.post(() -> scroll.scrollTo(0, at)); }, 420);
    }

    private List<Result> favoriteResults(Context c, String n) {
        ArrayList<Result> out = new ArrayList<Result>();
        ArrayList<App> apps = new ArrayList<App>();
        for (String[] f : PixelSearch.favorites(c)) {
            if (!n.isEmpty() && !norm(f[1]).contains(n)) continue;
            if ("apps".equals(f[0])) {
                if (sApps != null) for (App a : sApps) if (a.cn.flattenToString().equals(f[3])) { apps.add(a); break; }
                continue;
            }
            Result r = new Result();
            r.scope = f[0]; r.title = f[1]; r.sub = f[2]; r.data = f[3]; r.extra = f[4]; r.id = f[6];
            try { r.page = Integer.parseInt(f[5]); } catch (Throwable ignored) { }
            wire(c, r);
            out.add(r);
        }
        if (!apps.isEmpty()) { Result r = new Result(); r.scope = "apps"; r.apps = apps; out.add(0, r); }
        return out;
    }

    private void openSuite(Context c, String page, String anchor) {
        try {
            c.startActivity(new Intent().setClassName(PixelSearch.SUITE, "io.github.pixelsuite.SettingsActivity").putExtra("page", page).putExtra("search_anchor", anchor).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            launched();
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    private void openContact(Context c, Result r) {
        try {
            Uri u = r.id != null && !r.id.isEmpty() ? Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, r.id) : Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, r.data);
            c.startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            launched();
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    private void openEvent(Context c, Result r) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/events/" + r.data)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { i.putExtra("beginTime", Long.parseLong(r.extra)); i.putExtra("endTime", Long.parseLong(r.id)); } catch (Throwable ignored) { }
            c.startActivity(i);
            launched();
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    static String token(Context c) {
        if (token != null) return token;
        try {
            Bundle b = c.getContentResolver().call(Uri.parse("content://" + PixelSearch.AUTHORITY), "token", null, null);
            token = b == null ? null : b.getString("token");
        } catch (Throwable t) { android.util.Log.w(TAG, "search: token", t); }
        return token;
    }

    static void suiteAction(final Context c, final String act, final String value) {
        String t = token(c);
        if (t == null) { Toast.makeText(c, I18n.cs("Couldn't open it"), Toast.LENGTH_SHORT).show(); return; }
        try {
            c.startActivity(new Intent().setClassName(PixelSearch.SUITE, "io.github.pixelsuite.SearchAction").putExtra("token", t).putExtra("act", act).putExtra("value", value).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Throwable e) { android.util.Log.w(TAG, "search: action", e); }
    }

    static boolean whatsapp(Context c, String number) {
        String digits = number == null ? "" : number.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return false;
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=" + digits)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Throwable t) { return false; }
    }

    private void render() { renderList(); try { tintTexts(); } catch (Throwable t) { android.util.Log.w(TAG, "search: text colors", t); } }

    private void renderList() {
        list.removeAllViews();
        String q = field.getText().toString().trim();
        boolean favView = scopes.contains("favorites") && ("favorites".equals(filter) || (q.isEmpty() && filter == null));
        List<Result> source = favView ? favoriteResults(getContext(), norm(q)) : results;
        int extra = 0;
        if (q.isEmpty() && this.filter == null) { wide = false; try { extra = extras(); } catch (Throwable t) { android.util.Log.w(TAG, "search: start", t); } }
        if (q.isEmpty() && (!favView || source.isEmpty())) {
            firstApp = null; shownKeys = new java.util.HashSet<String>();
            if (extra > 0) return;
            hint.setText(I18n.cs("favorites".equals(filter) ? "Hold an app, a contact or a file to add it here" : "Search apps, contacts, files, settings and more"));
            list.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        final String filter = favView ? null : this.filter;
        wide = favView || filter != null;
        LinkedHashMap<String, ArrayList<Result>> by = new LinkedHashMap<String, ArrayList<Result>>();
        for (String s : scopes) by.put(s, new ArrayList<Result>());
        for (Result r : source) { ArrayList<Result> l = by.get(r.scope); if (l == null) { l = new ArrayList<Result>(); by.put(r.scope, l); } l.add(r); }
        if (by.containsKey("files") && by.containsKey("content")) {
            ArrayList<String> named = new ArrayList<String>();
            for (Result r : by.get("files")) named.add(r.data);
            ArrayList<Result> keep = new ArrayList<Result>();
            for (Result r : by.get("content")) if (!named.contains(r.data)) keep.add(r);
            by.put("content", keep);
        }
        int delay = 0;
        java.util.HashSet<String> keys = new java.util.HashSet<String>();
        for (String s : by.keySet()) {
            ArrayList<Result> l = by.get(s);
            if (l.isEmpty()) continue;
            if (filter != null && !filter.equals(s)) continue;
            if (!"tools".equals(s) && !"web".equals(s)) { View head = section(sectionName(s)); String hk = "section:" + s; keys.add(hk); if (!shownKeys.contains(hk)) { enterAnim(head, delay); delay += 14; } }
            int max = wide ? 50 : "contacts".equals(s) ? 4 : "files".equals(s) || "content".equals(s) ? 5 : 6;
            int k = 0;
            for (Result r : l) {
                if (k++ >= max) break;
                View v = "apps".equals(s) ? apps(r) : "tools".equals(s) ? tool(r) : row(r, q);
                list.addView(v, new LinearLayout.LayoutParams(-1, -2));
                String key = s + ":" + ("apps".equals(s) ? "grid" : "tools".equals(s) || "web".equals(s) ? "" : r.title + "|" + r.data);
                keys.add(key);
                if (!shownKeys.contains(key)) { enterAnim(v, delay); delay += 14; }
            }
        }
        shownKeys = keys;
        if (list.getChildCount() == 0) {
            TextView none = new TextView(getContext());
            none.setText(I18n.cs("No results"));
            none.setTextColor(look.subText);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(28), 0, 0);
            list.addView(none, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private static final String PINS_KEY = "pixelsuite_search_featured_pins";
    private String justPinned;

    private static List<String> pins(Context c) {
        List<String> out = new ArrayList<String>();
        String raw = PixelSearch.string(c, PINS_KEY);
        if (raw != null) for (String s : raw.split("\n")) { String t = s.trim(); if (!t.isEmpty() && !out.contains(t)) out.add(t); }
        return out;
    }

    private void appMenu(final App a, View anchor) {
        final Context c = getContext();
        closePopup();
        LinearLayout card = menu(anchor, false);
        try {
            int[] at = new int[2], base = new int[2];
            anchor.getLocationOnScreen(at); root.getLocationOnScreen(base);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            if (root.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) lp.setMarginStart(Math.max(dp(12), Math.min(at[0] - base[0], root.getWidth() - dp(250) - dp(12))));
            card.setLayoutParams(lp);
        } catch (Throwable ignored) { }
        final String key = a.cn.flattenToString();
        final List<String> now = pins(c);
        final boolean pinned = now.contains(key);
        menuRow(card, new SearchGlyph(SearchGlyph.PIN, look.text), I18n.cs(pinned ? "Unpin" : "Pin"), false, () -> {
            closePopup();
            if (pinned) now.remove(key); else { now.add(0, key); justPinned = key; }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < now.size() && i < 5; i++) { if (i > 0) b.append('\n'); b.append(now.get(i)); }
            try { android.provider.Settings.Secure.putString(c.getContentResolver(), PINS_KEY, b.toString()); } catch (Throwable ignored) { }
            renderList();
        });
        final boolean kept = favorite(record(a));
        menuRow(card, new SearchGlyph(kept ? SearchGlyph.STAR_ON : SearchGlyph.STAR, look.text), I18n.cs(kept ? "Remove from favorites" : "Add to favorites"), false, () -> { closePopup(); toggleFavorite(record(a), null); });
    }

    private void remember() {
        Context c = getContext();
        String q = field.getText().toString().trim();
        if (q.length() < 2 || q.length() > 80 || q.indexOf('\n') >= 0 || PixelSearch.number(c, PixelSearch.RECENTS_KEY, 1) != 1) return;
        List<String> all = PixelSearch.recents(c);
        for (java.util.Iterator<String> it = all.iterator(); it.hasNext(); ) if (it.next().equalsIgnoreCase(q)) it.remove();
        all.add(0, q);
        PixelSearch.recents(c, all);
    }

    private boolean early;

    private static List<App> few(Context c, List<String> keys) {
        ArrayList<App> out = new ArrayList<App>();
        try {
            LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            UserHandle me = android.os.Process.myUserHandle();
            int asked = 0;
            for (String key : keys) {
                ComponentName cn = ComponentName.unflattenFromString(key);
                if (cn == null) continue;
                if (++asked > 8 || out.size() >= 5) break;
                for (LauncherActivityInfo i : la.getActivityList(cn.getPackageName(), me)) {
                    if (!cn.equals(i.getComponentName())) continue;
                    App a = new App();
                    a.label = String.valueOf(i.getLabel());
                    a.norm = norm(a.label);
                    a.t9 = t9(a.norm);
                    a.cn = cn; a.pkg = cn.getPackageName(); a.user = me; a.info = i;
                    out.add(a);
                    break;
                }
            }
        } catch (Throwable t) { android.util.Log.w(TAG, "search: first apps", t); }
        return out;
    }

    private int extras() {
        final Context c = getContext();
        int made = 0;
        if (PixelSearch.number(c, PixelSearch.FEATURED_KEY, 1) == 1) {
            List<App> top = new ArrayList<App>();
            ArrayList<String> keys = new ArrayList<String>(pins(c));
            for (String key : PixelSearch.used(c).keySet()) if (!keys.contains(key)) keys.add(key);
            List<App> pool = sApps;
            if (pool == null) { pool = few(c, keys); early = !pool.isEmpty(); }
            for (String key : keys) {
                for (App a : pool) if (a.cn != null && key.equals(a.cn.flattenToString())) { top.add(a); break; }
                if (top.size() >= 5) break;
            }
            if (!top.isEmpty()) {
                section("Featured apps");
                Result r = new Result(); r.scope = "apps"; r.apps = top; r.id = "featured";
                list.addView(apps(r), new LinearLayout.LayoutParams(-1, -2));
                made++;
            }
        }
        if (PixelSearch.number(c, PixelSearch.RECENTS_KEY, 1) == 1) {
            final List<String> past = PixelSearch.recents(c);
            if (!past.isEmpty()) {
                LinearLayout head = new LinearLayout(c);
                head.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = new TextView(c);
                name.setText(I18n.cs("Recent searches")); name.setTextColor(look.subText); name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                name.setPadding(dp(8), dp(16), dp(8), dp(6));
                head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                final TextView wipe = new TextView(c);
                wipe.setText(I18n.cs("Clear")); wipe.setTextColor(look.accent); wipe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                wipe.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                wipe.setPadding(dp(12), dp(7), dp(12), dp(7));
                wipe.setBackground(ripple(dp(16)));
                wipe.setOnClickListener(x -> {
                    x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    closePopup();
                    LinearLayout card = menu(wipe, true);
                    TextView ask = new TextView(c);
                    ask.setText(I18n.cs("Clear recent searches?")); ask.setTextColor(look.text); ask.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    ask.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    ask.setPadding(dp(12), dp(8), dp(12), dp(8));
                    card.addView(ask);
                    int red = look.dark ? 0xfff28b82 : 0xffd93025;
                    View yes = menuRow(card, new SearchGlyph("bin", red), I18n.cs("Clear"), false, () -> {
                        closePopup();
                        PixelSearch.recents(c, new ArrayList<String>());
                        renderList();
                    });
                    if (yes instanceof LinearLayout && ((LinearLayout) yes).getChildAt(1) instanceof TextView) ((TextView) ((LinearLayout) yes).getChildAt(1)).setTextColor(red);
                    menuRow(card, new SearchGlyph(SearchGlyph.CLEAR, look.text), I18n.cs("Cancel"), false, this::closePopup);
                });
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2); wl.topMargin = dp(8);
                head.addView(wipe, wl);
                list.addView(head, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout card = new LinearLayout(c);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(panel(cardColor, 24));
                card.setPadding(dp(4), dp(6), dp(4), dp(6));
                for (final String s : past) {
                    LinearLayout row = new LinearLayout(c);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(14), dp(11), dp(14), dp(11));
                    row.setBackground(ripple(dp(18)));
                    ImageView i = new ImageView(c);
                    i.setImageDrawable(new SearchGlyph("past", look.subText));
                    row.addView(i, new LinearLayout.LayoutParams(dp(22), dp(22)));
                    TextView t = new TextView(c);
                    t.setText(s); t.setTextColor(look.text); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setPadding(dp(14), 0, 0, 0);
                    row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
                    row.setOnClickListener(x -> { field.setText(s); field.setSelection(field.getText().length()); });
                    row.setOnLongClickListener(x -> {
                        x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        List<String> left = PixelSearch.recents(c);
                        left.remove(s);
                        PixelSearch.recents(c, left);
                        PixelSearch.TEXT.remove(PixelSearch.PAST_KEY);
                        renderList();
                        return true;
                    });
                    card.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
                list.addView(card, new LinearLayout.LayoutParams(-1, -2));
                made++;
            }
        }
        return made;
    }

    private String sectionName(String s) {
        if ("files".equals(s)) return "Files";
        if ("content".equals(s)) return "Inside files";
        if ("settings".equals(s)) return "Settings";
        return PixelSearch.scopeName(s);
    }

    private void enterAnim(View v, int delay) {
        v.setAlpha(0); v.setTranslationY(dp(10));
        v.animate().alpha(1).translationY(0).setStartDelay(Math.min(delay, 160)).setDuration(260).setInterpolator(new Spring(0.8f)).start();
    }

    private View section(String name) {
        TextView t = new TextView(getContext());
        t.setText(I18n.cs(name));
        t.setTextColor(look.subText);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(8), dp(16), dp(8), dp(6));
        list.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private View apps(Result r) {
        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackground(panel(cardColor, 24));
        grid.setPadding(dp(4), dp(10), dp(4), dp(6));
        int perRow = 5, rows = wide ? 4 : 1;
        final boolean typed = field.getText().toString().trim().length() > 0;
        final List<String> pinned = "featured".equals(r.id) ? pins(getContext()) : null;
        LinearLayout line = null;
        for (int i = 0; i < r.apps.size() && i < perRow * rows; i++) {
            if (i % perRow == 0) { line = new LinearLayout(getContext()); line.setOrientation(LinearLayout.HORIZONTAL); grid.addView(line, new LinearLayout.LayoutParams(-1, -2)); }
            final App a = r.apps.get(i);
            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(4), 0, dp(4));
            final ImageView iv = new ImageView(getContext());
            final String pinKey = a.cn == null ? "" : a.cn.flattenToString();
            if (pinned != null && pinned.contains(pinKey)) {
                FrameLayout holder = new FrameLayout(getContext());
                holder.setClipChildren(false); holder.setClipToPadding(false);
                holder.addView(iv, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
                ImageView badge = new ImageView(getContext());
                badge.setImageDrawable(new SearchGlyph(SearchGlyph.PIN, look.onChip));
                badge.setPadding(dp(3), dp(3), dp(3), dp(3));
                GradientDrawable disc = new GradientDrawable(); disc.setShape(GradientDrawable.OVAL); disc.setColor(look.chipOn); disc.setStroke(dp(2), look.card);
                badge.setBackground(disc);
                badge.setRotation(40);
                badge.setContentDescription(I18n.cs("Pinned"));
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
                holder.addView(badge, bl);
                cell.setClipChildren(false);
                cell.addView(holder, new LinearLayout.LayoutParams(dp(60), dp(52)));
                if (pinKey.equals(justPinned)) {
                    justPinned = null;
                    badge.setScaleX(0); badge.setScaleY(0);
                    badge.animate().scaleX(1).scaleY(1).setStartDelay(160).setDuration(520).setInterpolator(new Spring(0.42f)).start();
                }
                cell.setTag(pinKey);
                line.setClipChildren(false); grid.setClipChildren(false);
                movable(line, cell);
            } else cell.addView(iv, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView label = new TextView(getContext());
            label.setText(a.label); label.setTextColor(look.text); label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER);
            label.setPadding(dp(6), 0, dp(6), 0);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2); ll.topMargin = dp(6);
            cell.addView(label, ll);
            cell.setOnClickListener(x -> launchApp(a, x));
            cell.setOnLongClickListener(x -> {
                if ("featured".equals(r.id)) { x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); pop(x); heldCell = x; appMenu(a, x); }
                else toggleFavorite(record(a), x);
                return true;
            });
            if (i == 0 && typed) {
                GradientDrawable mark = new GradientDrawable();
                mark.setCornerRadius(dp(18));
                mark.setColor(PixelSearch.alpha(look.chipOn, 24));
                mark.setStroke(dp(1.5f), PixelSearch.alpha(look.chipOn, 70));
                cell.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{mark, ripple(dp(18))}));
                label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                String key = a.cn.flattenToString();
                if (!key.equals(firstApp)) {
                    firstApp = key;
                    cell.setScaleX(0.82f); cell.setScaleY(0.82f);
                    cell.animate().scaleX(1).scaleY(1).setDuration(520).setInterpolator(new Spring(0.42f)).start();
                }
            } else cell.setBackground(ripple(dp(16)));
            line.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            final int side = dp(52);
            loadIcon("app:" + a.cn.flattenToString() + a.user.hashCode(), iv, () -> {
                if (a.bitmap != null) {
                    android.graphics.Picture shown = LauncherIcon.shown(getContext(), a.bitmap, side);
                    if (shown != null) return new android.graphics.drawable.PictureDrawable(shown);
                }
                return a.info.getBadgedIcon(0);
            });
        }
        if (line != null) for (int i = line.getChildCount(); i < perRow; i++) line.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        grid.setLayoutParams(lp);
        return grid;
    }

    private View heldCell;

    private void movable(final LinearLayout line, final View cell) {
        cell.setOnTouchListener(new View.OnTouchListener() {
            float downX, width; boolean dragging; int from, to, count;
            public boolean onTouch(View v, MotionEvent e) {
                int act = e.getActionMasked();
                if (act == MotionEvent.ACTION_DOWN) { downX = e.getRawX(); dragging = false; heldCell = null; return false; }
                if (act == MotionEvent.ACTION_MOVE) {
                    if (!dragging) {
                        if (heldCell != v || Math.abs(e.getRawX() - downX) < dp(10)) return false;
                        count = 0;
                        for (int i = 0; i < line.getChildCount(); i++) { if (line.getChildAt(i).getTag() instanceof String) count = i + 1; else break; }
                        from = line.indexOfChild(v); to = from; width = v.getWidth();
                        if (count < 2 || from < 0 || from >= count || width <= 0) { heldCell = null; return false; }
                        dragging = true;
                        closePopup();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        v.setPressed(false);
                        v.setElevation(dp(8));
                        v.animate().scaleX(1.14f).scaleY(1.14f).setDuration(160).start();
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    int dir = line.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
                    float low = -from * width, high = (count - 1 - from) * width;
                    float along = Math.max(low, Math.min(high, (e.getRawX() - downX) * dir));
                    v.setTranslationX(along * dir);
                    int next = Math.max(0, Math.min(count - 1, from + Math.round(along / width)));
                    if (next != to) {
                        to = next;
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        for (int i = 0; i < count; i++) {
                            if (i == from) continue;
                            float shift = i > from && i <= to ? -width : i < from && i >= to ? width : 0;
                            line.getChildAt(i).animate().translationX(shift * dir).setDuration(220).setInterpolator(new Spring(0.7f)).start();
                        }
                    }
                    return true;
                }
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    if (!dragging) return false;
                    dragging = false; heldCell = null;
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (act == MotionEvent.ACTION_UP && to != from) {
                        ArrayList<String> shown = new ArrayList<String>();
                        for (int i = 0; i < count; i++) shown.add((String) line.getChildAt(i).getTag());
                        shown.add(to, shown.remove(from));
                        Context c = getContext();
                        for (String rest : pins(c)) if (!shown.contains(rest)) shown.add(rest);
                        StringBuilder b = new StringBuilder();
                        for (int i = 0; i < shown.size() && i < 5; i++) { if (i > 0) b.append('\n'); b.append(shown.get(i)); }
                        try { android.provider.Settings.Secure.putString(c.getContentResolver(), PINS_KEY, b.toString()); } catch (Throwable ignored) { }
                        renderList();
                    } else {
                        v.animate().translationX(0).scaleX(1).scaleY(1).setDuration(260).setInterpolator(new Spring(0.6f)).withEndAction(() -> v.setElevation(0)).start();
                        for (int i = 0; i < count; i++) if (i != from) line.getChildAt(i).animate().translationX(0).setDuration(220).start();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void launchApp(App a, View from) {
        try {
            LauncherApps la = (LauncherApps) getContext().getSystemService(Context.LAUNCHER_APPS_SERVICE);
            Rect b = null;
            if (from != null) { int[] at = new int[2]; from.getLocationOnScreen(at); b = new Rect(at[0], at[1], at[0] + from.getWidth(), at[1] + from.getHeight()); }
            if (FeedPull.locked(getContext())) {
                final LauncherApps apps = la;
                final App app = a;
                if (android.os.Process.myUserHandle().equals(a.user)) getContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                else FeedPull.unlocked(getContext(), () -> { try { apps.startMainActivity(app.cn, app.user, null, null); } catch (Throwable ignored) { } });
            } else la.startMainActivity(a.cn, a.user, b, null);
            try { PixelSearch.used(getContext(), a.cn.flattenToString()); } catch (Throwable ignored) { }
            launched();
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    private Drawable ripple(int radius) {
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(radius);
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(PixelSearch.alpha(look.text, 14)), null, mask);
    }

    interface Loader { Drawable load() throws Exception; }

    private void loadIcon(final String key, final ImageView iv, final Loader loader) {
        Drawable cached = ICON_CACHE.get(key);
        if (cached != null) { iv.setImageDrawable(cached); return; }
        iv.setTag(key);
        ICONS.execute(() -> {
            Drawable d = null;
            try { d = loader.load(); } catch (Throwable ignored) { }
            final Drawable done = d;
            if (done != null) ICON_CACHE.put(key, done);
            MAIN.post(() -> { if (done != null && key.equals(iv.getTag())) { iv.setImageDrawable(done); iv.setAlpha(0f); iv.animate().alpha(1).setDuration(140).start(); } });
        });
    }

    private View tool(Result r) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(panel(cardColor, 24));
        box.setPadding(dp(20), dp(14), dp(20), dp(16));
        TextView sub = new TextView(getContext());
        sub.setText(r.sub); sub.setTextColor(look.subText); sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setSingleLine(true); sub.setEllipsize(TextUtils.TruncateAt.START);
        box.addView(sub);
        TextView big = new TextView(getContext());
        big.setText(r.title); big.setTextColor(look.text); big.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        big.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        box.addView(big);
        final Runnable open = r.open;
        box.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); pop(x); open.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    private CharSequence highlight(String text, String q) {
        if (text == null) return "";
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (text.indexOf('\u0001') >= 0) {
            int i = 0;
            while (i < text.length()) {
                int s = text.indexOf('\u0001', i);
                if (s < 0) { b.append(text.substring(i)); break; }
                b.append(text.substring(i, s));
                int e = text.indexOf('\u0002', s);
                if (e < 0) e = text.length();
                int at = b.length();
                b.append(text.substring(s + 1, e));
                b.setSpan(new StyleSpan(Typeface.BOLD), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.setSpan(new ForegroundColorSpan(look.accent), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = e + 1;
            }
            return b;
        }
        b.append(text);
        String low = norm(text);
        if (low.length() != text.length()) return b;
        for (String token : norm(q).split("\\s+")) {
            if (token.isEmpty()) continue;
            int at = low.indexOf(token);
            if (at >= 0) b.setSpan(new StyleSpan(Typeface.BOLD), at, at + token.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }

    private View row(final Result r, String q) {
        final Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setMinimumHeight(dp(64));
        row.setBackground(ripple(dp(20)));
        final ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor(look.chip);
        iv.setBackground(circle);
        int pad = dp(9);
        String glyph = glyphFor(r);
        if (glyph != null) { iv.setPadding(pad, pad, pad, pad); iv.setImageDrawable(new SearchGlyph(glyph, look.text)); }
        row.addView(iv, new LinearLayout.LayoutParams(dp(42), dp(42)));
        if ("contacts".equals(r.scope)) { iv.setPadding(0, 0, 0, 0); iv.setImageDrawable(new Initial(r.title, look)); loadIcon("photo:" + r.data, iv, () -> photo(c, r.data)); }
        if ("shortcuts".equals(r.scope)) { final String[] p = r.data.split("/", 2); iv.setPadding(dp(4), dp(4), dp(4), dp(4)); loadIcon("sc:" + r.data, iv, () -> { Drawable d = SearchDock.shortcutIcon(c, p[0], p[1]); return d != null ? d : c.getPackageManager().getApplicationIcon(p[0]); }); }
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, dp(8), 0);
        TextView title = new TextView(c);
        title.setText(highlight(r.title, q));
        title.setTextColor(look.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(title);
        if (r.sub != null && !r.sub.isEmpty()) {
            TextView sub = new TextView(c);
            CharSequence s = "content".equals(r.scope) ? highlight(r.sub, q) : r.sub;
            if ("content".equals(r.scope) && r.page > 0) s = TextUtils.concat(I18n.t("Page {0}").replace("{0}", String.valueOf(r.page)), " · ", s);
            sub.setText(s);
            sub.setTextColor(look.subText);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            sub.setMaxLines("content".equals(r.scope) ? 2 : 1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(sub);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if ("contacts".equals(r.scope) && r.extra != null && !r.extra.isEmpty()) {
            row.addView(action(SearchGlyph.CALL, "Call", () -> { suiteAction(c, "call", r.extra); launched(); }));
            row.addView(action(SearchGlyph.MESSAGE, "Message", () -> { try { c.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", r.extra, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); launched(); } catch (Throwable t) { toast("Couldn't open it"); } }));
            if (installed(c, "com.whatsapp") || installed(c, "com.whatsapp.w4b")) row.addView(action(SearchGlyph.CHAT, "WhatsApp", () -> { if (whatsapp(c, r.extra)) launched(); else toast("Couldn't open it"); }));
        }
        final boolean keepable = !"web".equals(r.scope) && !"tools".equals(r.scope);
        if (keepable && favorite(record(r))) {
            ImageView star = new ImageView(c);
            star.setImageDrawable(new SearchGlyph(SearchGlyph.STAR_ON, look.accent));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(16), dp(16)); sp.setMarginEnd(dp(8));
            row.addView(star, sp);
        }
        final Runnable open = r.open, hold = r.longPress;
        row.setOnClickListener(x -> { if (open != null) open.run(); });
        if (hold != null) row.setOnLongClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); hold.run(); return true; });
        else if (keepable) row.setOnLongClickListener(x -> { toggleFavorite(record(r), x); return true; });
        return row;
    }

    private String glyphFor(Result r) {
        if ("files".equals(r.scope) || "content".equals(r.scope)) return SearchGlyph.FILE;
        if ("calendar".equals(r.scope)) return SearchGlyph.EVENT;
        if ("settings".equals(r.scope)) return "android".equals(r.data) ? SearchGlyph.SETTINGS : SearchGlyph.SUITE;
        if ("web".equals(r.scope)) return SearchGlyph.WEB;
        if ("contacts".equals(r.scope)) return null;
        if ("shortcuts".equals(r.scope)) return null;
        return SearchGlyph.SEARCH;
    }

    private View action(String glyph, String description, final Runnable run) {
        ImageView b = icon(new SearchGlyph(glyph, look.accent), description);
        b.setPadding(dp(11), dp(11), dp(11), dp(11));
        b.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); run.run(); });
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return b;
    }

    static final class Initial extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG); final String letter;
        Initial(String name, PixelSearch.Look look) {
            String n = name == null ? "" : name.trim();
            letter = n.isEmpty() ? "#" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase();
            fill.setColor(look.chipOn); text.setColor(look.onChip); text.setTextAlign(Paint.Align.CENTER); text.setFakeBoldText(true);
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float r = Math.min(b.width(), b.height()) / 2f;
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, fill);
            text.setTextSize(r);
            c.drawText(letter, b.exactCenterX(), b.exactCenterY() - (text.descent() + text.ascent()) / 2, text);
        }
        @Override public void setAlpha(int a) { fill.setAlpha(a); text.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    static final class Round extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final Bitmap bitmap; final Matrix m = new Matrix();
        Round(Bitmap b) { bitmap = b; paint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float r = Math.min(b.width(), b.height()) / 2f;
            float s = 2 * r / Math.min(bitmap.getWidth(), bitmap.getHeight());
            m.setScale(s, s); m.postTranslate(b.exactCenterX() - bitmap.getWidth() * s / 2, b.exactCenterY() - bitmap.getHeight() * s / 2);
            paint.getShader().setLocalMatrix(m);
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, paint);
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return -1; }
        @Override public int getIntrinsicHeight() { return -1; }
    }

    private static Drawable photo(Context c, String id) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://" + PixelSearch.AUTHORITY + "/photo/" + id))) {
            Bitmap b = BitmapFactory.decodeStream(in);
            return b == null ? null : new Round(b);
        }
    }

    private void preview(final Result r) {
        if (popup != null) return;
        final Context c = getContext();
        hideKeyboard();
        popup = new FrameLayout(c);
        popup.setBackgroundColor(0x66000000);
        popup.setOnClickListener(x -> closePopup());
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setBackground(floating(28));
        card.setPadding(dp(22), dp(20), dp(22), dp(12));
        TextView title = new TextView(c);
        title.setText(r.title); title.setTextColor(look.text); title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title);
        TextView where = new TextView(c);
        String folder = "content".equals(r.scope) ? r.id : (r.sub == null ? "" : r.sub);
        where.setText(r.page > 0 ? TextUtils.concat(I18n.t("Page {0}").replace("{0}", String.valueOf(r.page)), " · ", folder) : folder);
        where.setTextColor(look.subText); where.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        where.setPadding(0, dp(4), 0, dp(12));
        card.addView(where);
        if ("content".equals(r.scope)) {
            TextView body = new TextView(c);
            body.setText(highlight(r.sub, field.getText().toString()));
            body.setTextColor(look.text); body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            body.setLineSpacing(dp(2), 1);
            body.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable sb = new GradientDrawable(); sb.setColor(look.chip); sb.setCornerRadius(dp(16));
            body.setBackground(sb);
            card.addView(body);
        }
        final boolean kept = favorite(record(r));
        final int red = look.dark ? 0xfff28b82 : 0xffd93025;
        final LinearLayout rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(12), 0, 0);
        final LinearLayout confirm = new LinearLayout(c);
        confirm.setOrientation(LinearLayout.VERTICAL);
        confirm.setGravity(Gravity.CENTER_HORIZONTAL);
        confirm.setPadding(0, dp(16), 0, dp(4));
        confirm.setVisibility(View.GONE);
        final View[] bin = new View[1];
        View[] first = {
            button(kept ? SearchGlyph.STAR_ON : SearchGlyph.STAR, kept ? "Remove" : "Favorite", look.accent, () -> { closePopup(); toggleFavorite(record(r), null); }),
            button(SearchGlyph.FOLDER, "Show in folder", look.accent, () -> { closePopup(); suiteAction(c, "folder", r.data); launched(); }),
            bin[0] = button("bin", "Delete", red, () -> { rows.setVisibility(View.GONE); confirm.setVisibility(View.VISIBLE); bloomIn(confirm); })};
        View[] second = {
            button(SearchGlyph.OPEN, "Open", look.accent, () -> { closePopup(); suiteAction(c, "open", r.data); launched(); }),
            button(SearchGlyph.SHARE, "Share", look.accent, () -> { closePopup(); suiteAction(c, "share", r.data); launched(); }),
            null};
        for (View[] set : new View[][]{first, second}) {
            LinearLayout line = new LinearLayout(c);
            for (View v : set) {
                FrameLayout cell = new FrameLayout(c);
                if (v != null) cell.addView(v, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
                line.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            }
            rows.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        card.addView(rows);
        TextView ask = new TextView(c);
        ask.setText(I18n.cs("Delete this file?")); ask.setTextColor(look.text); ask.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ask.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        confirm.addView(ask);
        final ImageView can = new ImageView(c);
        can.setImageDrawable(new SearchGlyph("bin", red));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(40), dp(40)); cl.topMargin = dp(10);
        confirm.addView(can, cl);
        LinearLayout pair = new LinearLayout(c);
        pair.setGravity(Gravity.CENTER);
        pair.setPadding(0, dp(8), 0, 0);
        pair.addView(button(SearchGlyph.CLEAR, "Cancel", look.accent, () -> { confirm.setVisibility(View.GONE); rows.setVisibility(View.VISIBLE); bloomIn(rows); }));
        pair.addView(button(SearchGlyph.TICK, "Delete", red, () -> {
            can.animate().rotation(-22).setDuration(110).withEndAction(() -> can.animate().rotation(22).setDuration(150).withEndAction(() ->
                can.animate().rotation(0).scaleX(0.1f).scaleY(0.1f).alpha(0f).translationY(dp(14)).setDuration(260).setInterpolator(new Spring(0.7f)).withEndAction(() -> erase(r)).start()).start()).start();
        }));
        confirm.addView(pair);
        card.addView(confirm);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.leftMargin = dp(20); lp.rightMargin = dp(20);
        popup.addView(card, lp);
        root.addView(popup, new FrameLayout.LayoutParams(-1, -1));
        popup.setAlpha(0); popup.animate().alpha(1).setDuration(160).start();
        card.setAlpha(0f);
        card.post(() -> bloomIn(card));
    }

    private void erase(final Result r) {
        final Context c = getContext();
        WORK.execute(() -> {
            boolean ok = false;
            try {
                Bundle b = c.getContentResolver().call(Uri.parse("content://" + PixelSearch.AUTHORITY), "delete", r.data, null);
                ok = b != null && b.getBoolean("ok", false);
            } catch (Throwable t) { android.util.Log.w(TAG, "search: delete", t); }
            final boolean gone = ok;
            MAIN.post(() -> {
                closePopup();
                toast(gone ? "Deleted" : "Couldn't delete it");
                if (!gone) return;
                for (java.util.Iterator<Result> it = results.iterator(); it.hasNext(); ) { Result x = it.next(); if (r.data != null && r.data.equals(x.data) && ("files".equals(x.scope) || "content".equals(x.scope))) it.remove(); }
                render();
            });
        });
    }

    private View button(String glyph, String label, int color, final Runnable run) {
        View b = button(glyph, label, run);
        if (b instanceof LinearLayout) {
            LinearLayout l = (LinearLayout) b;
            if (l.getChildAt(0) instanceof ImageView) ((ImageView) l.getChildAt(0)).setImageDrawable(new SearchGlyph(glyph, color));
            l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER); l.setPadding(dp(6), dp(10), dp(6), dp(10));
            if (l.getChildAt(1) instanceof TextView) {
                TextView t = (TextView) l.getChildAt(1);
                t.setTextColor(color); t.setSingleLine(false); t.setMaxLines(2); t.setEllipsize(android.text.TextUtils.TruncateAt.END); t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); t.setPadding(0, dp(4), 0, 0);
            }
        }
        return b;
    }

    private View button(String glyph, String label, final Runnable run) {
        LinearLayout b = new LinearLayout(getContext());
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(12), dp(10), dp(14), dp(10));
        b.setBackground(ripple(dp(20)));
        ImageView i = new ImageView(getContext());
        i.setImageDrawable(new SearchGlyph(glyph, look.accent));
        b.addView(i, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = new TextView(getContext());
        t.setText(I18n.cs(label)); t.setTextColor(look.accent); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(6), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(x -> run.run());
        return b;
    }

    private Drawable engineIcon(String id) { return engineIcon(getContext(), id); }

    static Drawable engineIcon(Context ctx, String id) {
        String pkg = "google".equals(id) ? "com.google.android.googlequicksearchbox" : "duckduckgo".equals(id) ? "com.duckduckgo.mobile.android" : "brave".equals(id) ? "com.brave.browser" : "perplexity".equals(id) ? "ai.perplexity.app.android" : "com.microsoft.bing";
        Drawable icon = null;
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            Drawable bare = SearchDock.bare(d);
            icon = bare != null ? bare : d;
        } catch (Throwable ignored) { }
        if (icon == null) icon = ENGINE_ICONS.get(id);
        if (icon == null) return null;
        final Drawable inner = icon;
        return new Drawable() {
            @Override public void draw(Canvas cv) { inner.setAlpha(255); inner.setBounds(getBounds()); inner.draw(cv); }
            @Override public void setAlpha(int a) { }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Drawable> ENGINE_ICONS = new java.util.concurrent.ConcurrentHashMap<String, Drawable>();

    private void fetchEngines() {
        final Context c = getContext();
        ICONS.execute(() -> {
            boolean got = false;
            for (String id : PixelSearch.ENGINES) {
                if (ENGINE_ICONS.containsKey(id)) continue;
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://" + PixelSearch.AUTHORITY + "/engine/" + id))) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) { ENGINE_ICONS.put(id, new android.graphics.drawable.BitmapDrawable(c.getResources(), b)); got = true; }
                } catch (Throwable ignored) { }
            }
            if (got) MAIN.post(() -> { if (isShowing()) back.setImageDrawable(engineMark()); });
        });
    }

    private float wallpaperLight = -1;
    private int glow;
    private final java.util.WeakHashMap<View, String> textKinds = new java.util.WeakHashMap<View, String>();

    private void tintTexts() {
        if (list == null) return;
        boolean plain = shade() >= 100;
        int main = look.text, soft = look.subText, glow = 0;
        if (!plain && feed) {
            float under = behind != null ? behind.underLight() : -1f;
            if (under < 0f) under = look.dark ? 0.12f : 0.9f;
            int sf = feedColor();
            float own = (0.299f * Color.red(sf) + 0.587f * Color.green(sf) + 0.114f * Color.blue(sf)) / 255f, k = shade() / 100f;
            boolean bright = under + (own - under) * k > 0.52f;
            main = bright ? 0xff1b1b1f : 0xffffffff; soft = bright ? 0xcc000000 : 0xe6ffffff;
            glow = shade() < 60 ? (bright ? 0x66ffffff : 0xaa000000) : 0;
        } else if (!plain) {
            if (wallpaperLight < 0) {
                wallpaperLight = 0.1f;
                try {
                    Object colors = android.app.WallpaperManager.class.getMethod("getWallpaperColors", int.class).invoke(android.app.WallpaperManager.getInstance(getContext()), 1);
                    Object first = colors.getClass().getMethod("getPrimaryColor").invoke(colors);
                    int argb = (Integer) first.getClass().getMethod("toArgb").invoke(first);
                    wallpaperLight = (0.299f * Color.red(argb) + 0.587f * Color.green(argb) + 0.114f * Color.blue(argb)) / 255f;
                } catch (Throwable ignored) { }
            }
            int sf = look.surface;
            float own = (0.299f * Color.red(sf) + 0.587f * Color.green(sf) + 0.114f * Color.blue(sf)) / 255f, k = shade() / 100f * Color.alpha(sf) / 255f;
            boolean bright = wallpaperLight + (own - wallpaperLight) * k > 0.52f;
            main = bright ? 0xff1b1b1f : 0xffffffff; soft = bright ? 0xb3000000 : 0xccffffff;
        }
        this.glow = glow;
        tint(list, main, soft);
        try {
            boolean darkIcons = plain ? !look.dark : main == 0xff1b1b1f;
            View decor = getWindow().getDecorView();
            int vis = decor.getSystemUiVisibility();
            vis = darkIcons ? vis | 0x2000 : vis & ~0x2000;
            if (!plain) vis = darkIcons ? vis | 0x10 : vis & ~0x10;
            decor.setSystemUiVisibility(vis);
        } catch (Throwable ignored) { }
    }

    private void tint(ViewGroup group, int main, int soft) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                String was = textKinds.get(t);
                if (was == null) { int now = t.getCurrentTextColor(); was = now == look.subText ? "soft" : now == look.text ? "main" : "keep"; textKinds.put(t, was); }
                if ("main".equals(was)) t.setTextColor(main); else if ("soft".equals(was)) t.setTextColor(soft);
                if (feed && !"keep".equals(was)) { if (glow != 0) t.setShadowLayer(dp(4), 0, dp(1), glow); else if (t.getShadowRadius() > 0) t.setShadowLayer(0, 0, 0, 0); }
            } else if (v instanceof ViewGroup) {
                android.graphics.drawable.Drawable bg = v.getBackground();
                if (bg instanceof GradientDrawable || bg instanceof android.graphics.drawable.LayerDrawable && !(bg instanceof android.graphics.drawable.RippleDrawable)) continue;
                tint((ViewGroup) v, main, soft);
            }
        }
    }

    static void bloomIn(final View card) {
        card.setScaleX(0.3f); card.setScaleY(0.2f); card.setAlpha(0f);
        android.animation.ValueAnimator p = android.animation.ValueAnimator.ofFloat(0f, 1f);
        p.setDuration(430); p.setInterpolator(new Spring(0.62f));
        p.addUpdateListener(a -> { card.setScaleX(0.3f + 0.7f * (Float) a.getAnimatedValue()); card.setAlpha(Math.min(1f, a.getAnimatedFraction() * 4f)); });
        android.animation.ValueAnimator q = android.animation.ValueAnimator.ofFloat(0f, 1f);
        q.setStartDelay(45); q.setDuration(470); q.setInterpolator(new Spring(0.66f));
        q.addUpdateListener(a -> card.setScaleY(0.2f + 0.8f * (Float) a.getAnimatedValue()));
        p.start(); q.start();
        if (card instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) card).getChildCount(); i++) {
            View v = ((ViewGroup) card).getChildAt(i);
            v.setAlpha(0f); v.setTranslationY(-10 * card.getResources().getDisplayMetrics().density);
            v.animate().alpha(1).translationY(0).setStartDelay(110 + i * 45).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
    }

    static void bloomOut(final View card, final Runnable end) {
        card.animate().cancel();
        card.animate().scaleX(0.3f).scaleY(0.2f).alpha(0f).setStartDelay(0).setDuration(170).setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f)).withEndAction(end).start();
    }

    private Drawable engineMark() { return engineMark(getContext(), look, engine); }

    static Drawable engineMark(Context c, PixelSearch.Look look, String engine) {
        final Drawable picture = engineIcon(c, engine);
        final String letter = PixelSearch.engineName(engine).substring(0, 1);
        final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(look.ink); ink.setTextAlign(Paint.Align.CENTER); ink.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(look.ink); line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
        final Path arrow = new Path();
        return new Drawable() {
            @Override public void draw(Canvas cv) {
                android.graphics.Rect b = getBounds();
                float u = Math.min(b.width(), b.height()) / 24f, x = b.exactCenterX(), y = b.exactCenterY() - 2.5f * u;
                if (picture != null) { int h = Math.round(9.5f * u); picture.setBounds(Math.round(x) - h, Math.round(y) - h, Math.round(x) + h, Math.round(y) + h); picture.draw(cv); }
                else { ink.setTextSize(15 * u); cv.drawText(letter, x, y - (ink.descent() + ink.ascent()) / 2f, ink); }
                line.setStrokeWidth(1.7f * u);
                arrow.reset(); arrow.moveTo(x - 3.2f * u, y + 10.2f * u); arrow.lineTo(x, y + 12.8f * u); arrow.lineTo(x + 3.2f * u, y + 10.2f * u);
                cv.drawPath(arrow, line);
            }
            @Override public void setAlpha(int a) { }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private LinearLayout menu(View anchor, boolean end) {
        final Context c = getContext();
        hidePill();
        popup = new FrameLayout(c);
        popup.setOnClickListener(x -> closePopup());
        final LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(floating(26));
        card.setElevation(dp(10));
        int[] at = new int[2], base = new int[2];
        anchor.getLocationOnScreen(at); root.getLocationOnScreen(base);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(250), -2, Gravity.TOP | (end ? Gravity.END : Gravity.START));
        lp.topMargin = at[1] - base[1] + anchor.getHeight() + dp(4);
        lp.setMarginStart(dp(12)); lp.setMarginEnd(dp(12));
        popup.addView(card, lp);
        root.addView(popup, new FrameLayout.LayoutParams(-1, -1));
        final boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        popup.setTag("menu");
        card.setAlpha(0f);
        card.post(() -> {
            card.setPivotX(end != rtl ? card.getWidth() - dp(20) : dp(20)); card.setPivotY(0);
            bloomIn(card);
        });
        return card;
    }

    private View menuRow(LinearLayout card, Drawable glyph, CharSequence text, boolean on, Runnable run) {
        final Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        GradientDrawable press = new GradientDrawable(); press.setCornerRadius(dp(18)); press.setColor(on ? look.chipOn : 0);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(look.dark ? 0x22ffffff : 0x18000000), press, null));
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(glyph);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(22), dp(22)); ip.setMarginEnd(dp(14));
        row.addView(icon, ip);
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f); t.setTextColor(on ? look.onChip : look.text);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (run != null) row.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void engines() {
        if (popup != null) return;
        LinearLayout card = menu(back, false);
        for (int i = 0; i < PixelSearch.ENGINES.length; i++) {
            final String id = PixelSearch.ENGINES[i];
            final boolean on = id.equals(engine);
            final String name = PixelSearch.ENGINE_NAMES[i];
            Drawable mark = engineIcon(id);
            menuRow(card, mark != null ? mark : new SearchGlyph(SearchGlyph.WEB, on ? look.onChip : look.text), name, on, () -> {
                closePopup();
                engine = id;
                try { android.provider.Settings.Secure.putString(getContext().getContentResolver(), PixelSearch.ENGINE_KEY, id); } catch (Throwable ignored) { }
                back.setImageDrawable(engineMark());
                pop(back);
                query(field.getText().toString());
            });
        }
    }

    private final Runnable saveBackdrop = () -> { try { android.provider.Settings.Secure.putInt(getContext().getContentResolver(), PixelSearch.BACKDROP_KEY, backdrop); } catch (Throwable ignored) { } };

    private int blurStep = -1;

    private void wallBlur(float amount) {
        if (!feed || android.os.Build.VERSION.SDK_INT < 31) return;
        int step = frosty && behind == null ? Math.round(clamp(amount) * 6f) : 0;
        if (step == blurStep) return;
        blurStep = step;
        try {
            Window w = getWindow();
            if (w == null) return;
            WindowManager.LayoutParams lp = w.getAttributes();
            WindowManager.LayoutParams.class.getMethod("setBlurBehindRadius", int.class).invoke(lp, Math.round(dp(26) * step / 6f));
            if (step > 0) lp.flags |= 0x4; else lp.flags &= ~0x4;
            w.setAttributes(lp);
        } catch (Throwable ignored) { }
    }

    private int feedColor() {
        int base = frosty ? (look.dark ? 0x000000 : 0xf2f2f5) : look.surface & 0xffffff;
        return base | (Math.round(255 * shade() / 100f) << 24);
    }

    private int shade() { return !frosty ? 100 : feed ? veil : backdrop; }

    private final Runnable saveVeil = () -> { try { android.provider.Settings.Secure.putInt(getContext().getContentResolver(), PixelSearch.FEED_BACKDROP_KEY, veil); } catch (Throwable ignored) { } };

    private void options(View anchor) {
        if (popup != null) return;
        hideKeyboard();
        LinearLayout card = menu(anchor, true);
        final Toggle toggle = new Toggle(getContext(), look.dark ? look.chipOn : look.accent, look.dark ? 0x33ffffff : 0x22000000, look.dark ? look.onChip : 0xffffffff, frosty);
        final View[] shading = new View[2];
        View glassRow = menuRow(card, new Panes(look.text), I18n.cs("Frosted Glass"), false, () -> {
            frosty = !frosty;
            toggle.set(frosty);
            for (View v : shading) if (v != null) { v.setEnabled(frosty); v.animate().alpha(frosty ? 1f : .38f).setDuration(180).start(); }
            if (feed) { surfaceColor = feedColor(); if (behind != null) behind.plain(!frosty); blurStep = -1; wallBlur(1f); }
            root.invalidate();
            tintTexts();
            try { android.provider.Settings.Secure.putInt(getContext().getContentResolver(), PixelSearch.FROST_KEY, frosty ? 1 : 0); } catch (Throwable ignored) { }
        });
        ((LinearLayout) glassRow).addView(toggle, new LinearLayout.LayoutParams(dp(38), dp(22)));
        shading[0] = menuRow(card, new SearchGlyph(SearchGlyph.OPACITY, look.text), I18n.cs("Background opacity"), false, null);
        OpacitySlider slider = new OpacitySlider(getContext(), look.dark ? look.chipOn : look.accent, look.dark ? 0x33ffffff : 0x22000000);
        shading[1] = slider;
        slider.setOnTouchListener((v, e) -> !v.isEnabled());
        for (View v : shading) { v.setEnabled(frosty); v.setAlpha(frosty ? 1f : .38f); }
        slider.show(feed ? veil : backdrop, false);
        slider.listen(percent -> {
            if (feed) { veil = percent; surfaceColor = feedColor(); root.invalidate(); tintTexts(); MAIN.removeCallbacks(saveVeil); MAIN.postDelayed(saveVeil, 250); }
            else { backdrop = percent; root.invalidate(); tintTexts(); MAIN.removeCallbacks(saveBackdrop); MAIN.postDelayed(saveBackdrop, 250); }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(40)); sp.setMargins(dp(8), 0, dp(8), dp(4));
        card.addView(slider, sp);
        menuRow(card, new SearchGlyph(SearchGlyph.SETTINGS, look.text), I18n.cs("Search settings"), false, () -> { closePopup(); openSuite(getContext(), "pixel_search", null); });
    }

    private static final class Toggle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int onColor, offColor, knob;
        private float at, to;
        Toggle(Context c, int onColor, int offColor, int knob, boolean on) { super(c); this.onColor = onColor; this.offColor = offColor; this.knob = knob; at = to = on ? 1f : 0f; }
        void set(boolean on) { to = on ? 1f : 0f; postInvalidateOnAnimation(); }
        @Override protected void onDraw(Canvas c) {
            if (at != to) { at += (to - at) * 0.3f; if (Math.abs(to - at) < 0.01f) at = to; postInvalidateOnAnimation(); }
            float w = getWidth(), h = getHeight(), r = h / 2f, pad = h * 0.14f, k = r - pad;
            paint.setColor(PixelSearch.mix(offColor, onColor, at));
            c.drawRoundRect(0, 0, w, h, r, r, paint);
            paint.setColor(at > 0.5f ? knob : 0xffffffff);
            c.drawCircle(r + (w - 2 * r) * at, r, k * (0.82f + 0.18f * at), paint);
        }
    }

    private static final class Panes extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final android.graphics.Path front = new android.graphics.Path();
        Panes(int color) { this.color = color; }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float s = Math.min(b.width(), b.height()), u = s / 24f, x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
            front.reset(); front.addRoundRect(x + 8.6f * u, y + 8.6f * u, x + 20.6f * u, y + 20.6f * u, 3.4f * u, 3.4f * u, android.graphics.Path.Direction.CW);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(1.7f * u);
            int back = c.save();
            c.clipPath(front, android.graphics.Region.Op.DIFFERENCE);
            paint.setStyle(Paint.Style.STROKE); paint.setColor(color);
            c.drawRoundRect(x + 3.4f * u, y + 3.4f * u, x + 15.4f * u, y + 15.4f * u, 3.4f * u, 3.4f * u, paint);
            c.restoreToCount(back);
            paint.setStyle(Paint.Style.FILL); paint.setColor((color & 0xffffff) | 0x30000000);
            c.drawPath(front, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setColor(color);
            c.drawPath(front, paint);
            paint.setStrokeWidth(1.4f * u);
            c.drawLine(x + 11.8f * u, y + 15.6f * u, x + 15.6f * u, y + 11.8f * u, paint);
            c.drawLine(x + 15.2f * u, y + 17.6f * u, x + 17.6f * u, y + 15.2f * u, paint);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private boolean keyboardUp() {
        try {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            return (Integer) InputMethodManager.class.getMethod("getInputMethodWindowVisibleHeight").invoke(imm) > 0;
        } catch (Throwable ignored) { }
        try {
            Object insets = root.getRootWindowInsets();
            Object ime = Class.forName("android.view.WindowInsets$Type").getMethod("ime").invoke(null);
            return (Boolean) insets.getClass().getMethod("isVisible", int.class).invoke(insets, ime);
        } catch (Throwable t) { return true; }
    }

    private final Runnable watchKeys = new Runnable() {
        @Override public void run() {
            if (!isShowing() || closing) return;
            boolean want = !keyboardUp() && popup == null;
            if (want != keysShown) { if (want) showPill(); else hidePill(); }
            MAIN.postDelayed(this, 220);
        }
    };

    private void showPill() {
        final Context c = getContext();
        keysShown = true;
        if (keysPill == null) {
            keysPill = new LinearLayout(c);
            keysPill.setGravity(Gravity.CENTER_VERTICAL);
            keysPill.setPadding(dp(16), dp(11), dp(18), dp(11));
            keysPill.setBackground(panel(look.card, 26));
            keysPill.setElevation(dp(6));
            ImageView keys = new ImageView(c);
            keys.setImageDrawable(new SearchGlyph(SearchGlyph.KEYS, look.text));
            keysPill.addView(keys, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView t = new TextView(c);
            t.setText(I18n.cs("Show keyboard")); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); t.setTextColor(look.text);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setPadding(dp(10), 0, dp(8), 0);
            keysPill.addView(t);
            ImageView up = new ImageView(c);
            up.setImageDrawable(new SearchGlyph(SearchGlyph.UP, look.text));
            keysPill.addView(up, new LinearLayout.LayoutParams(dp(18), dp(18)));
            keysPill.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); hidePill(); showKeyboard(); });
            android.animation.ObjectAnimator bob = android.animation.ObjectAnimator.ofFloat(up, "translationY", dp(2), -dp(3));
            bob.setDuration(620); bob.setRepeatCount(android.animation.ValueAnimator.INFINITE); bob.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            bob.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            bob.start();
            android.animation.ObjectAnimator beat = android.animation.ObjectAnimator.ofPropertyValuesHolder(keys, android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.14f), android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.14f));
            beat.setDuration(620); beat.setRepeatCount(android.animation.ValueAnimator.INFINITE); beat.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            beat.start();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = dp(56);
            root.addView(keysPill, lp);
        }
        keysPill.animate().cancel();
        keysPill.setVisibility(View.VISIBLE);
        keysPill.setAlpha(0f); keysPill.setScaleX(0.6f); keysPill.setScaleY(0.6f); keysPill.setTranslationY(dp(46));
        keysPill.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setStartDelay(0).setDuration(480).setInterpolator(new Spring(0.62f)).withEndAction(null).start();
    }

    private void hidePill() {
        keysShown = false;
        final View p = keysPill;
        if (p == null || p.getVisibility() != View.VISIBLE) return;
        p.animate().cancel();
        p.animate().alpha(0).scaleX(0.7f).scaleY(0.7f).translationY(dp(30)).setDuration(160).setInterpolator(null).withEndAction(() -> p.setVisibility(View.GONE)).start();
    }

    private void closePopup() {
        final FrameLayout p = popup;
        if (p == null) return;
        popup = null;
        if ("menu".equals(p.getTag()) && p.getChildCount() > 0) { bloomOut(p.getChildAt(0), () -> root.removeView(p)); return; }
        p.animate().alpha(0).setDuration(140).withEndAction(() -> root.removeView(p)).start();
        if (p.getChildCount() > 0) p.getChildAt(0).animate().scaleX(0.92f).scaleY(0.92f).setDuration(140).start();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (closing) return true;
        return super.dispatchTouchEvent(e);
    }
}
