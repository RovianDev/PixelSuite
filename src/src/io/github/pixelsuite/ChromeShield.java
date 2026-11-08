package io.github.pixelsuite;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class ChromeShield {
    private ChromeShield() {}

    static final String[] NAMES = {"no filtering", "basic", "optimal", "complete"};
    static final int DEFAULT_LEVEL = 2;
    private static final String PREFS = "pixelsuite_shield";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<WeakReference<Shield>> sButtons = new ArrayList<WeakReference<Shield>>();
    private static final WeakHashMap<Object, Integer> sCounts = new WeakHashMap<Object, Integer>();
    private static final Map<Class<?>, Field> sViewFields = new HashMap<Class<?>, Field>();
    private static WeakReference<Object> sLast = new WeakReference<Object>(null);
    private static volatile boolean sEnabled, sChecked;
    private static boolean sInstalled;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int level(Context c, String host) {
        return ChromeSiteSettings.snapshot(c).level(host);
    }

    static final String KEY_ON = "pixelsuite_chrome_adblock";
    private static volatile int sOnLocal = -1;
    private static volatile long sOnAt;
    private static final android.net.Uri PROVIDER = android.net.Uri.parse("content://io.github.roviandev.pixelsuite.cosmetic");

    static boolean filtering(Context c) {
        if (sOnLocal >= 0 && android.os.SystemClock.uptimeMillis() - sOnAt < 5000) return sOnLocal == 1;
        try { return android.provider.Settings.Secure.getInt(c.getContentResolver(), KEY_ON, 1) == 1; } catch (Throwable t) { return true; }
    }

    static void setFiltering(final Context c, final boolean on) {
        sOnLocal = on ? 1 : 0; sOnAt = android.os.SystemClock.uptimeMillis();
        new Thread(new Runnable() { public void run() {
            try { android.os.Bundle b = new android.os.Bundle(); b.putInt("value", on ? 1 : 0); c.getContentResolver().call(PROVIDER, "adblock", KEY_ON, b); } catch (Throwable ignored) { }
        }}, "PixelSuite-AdBlock").start();
        Object wc = sLast.get();
        if (wc != null) reload(wc, null);
        refreshAll();
    }

    static Context app() {
        try { return ((Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)).getApplicationContext(); } catch (Throwable t) { return null; }
    }
    static void setLevel(Context c, String host, int level) {
        if(host==null)return;
        try{ChromeSiteSettings.change(c,"level",host,level,null);}catch(Throwable unavailable){}
    }
    static Set<String> hidden(Context c, String host) {
        return host==null?Collections.<String>emptySet():ChromeSiteSettings.snapshot(c).selectors(host);
    }
    private static void addHidden(Context c, String host, List<String> selectors) {
        if(host==null||selectors.isEmpty())return;
        try{ChromeSiteSettings.change(c,"addHidden",host,0,selectors);}catch(Throwable unavailable){}
    }
    private static void clearHidden(Context c, String host) {
        if(host==null)return;
        try{ChromeSiteSettings.change(c,"clearHidden",host,0,null);}catch(Throwable unavailable){}
    }

    static void install(ClassLoader l) throws Throwable {
        if (sInstalled) return;
        Class<?> toolbar = l.loadClass("org.chromium.chrome.browser.toolbar.top.ToolbarPhone");
        Xp.hookAllMethods(toolbar, "onFinishInflate", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { attach((ViewGroup) p.thisObject); } catch (Throwable ignored) { }
                try { ChromeLayout.attach((ViewGroup) p.thisObject); } catch (Throwable ignored) { }
            }
        });
        try { ChromeSpeedDial.install(l); } catch (Throwable ignored) { }
        sInstalled = true;
    }

    static void loaded(Context app, final Object wc, boolean on) {
        sEnabled = on;
        sChecked = true;
        sLast = new WeakReference<Object>(wc);
        MAIN.post(new Runnable() { public void run() { refreshAll(); } });
        if (!on) return;
        count(wc, 1500);
        count(wc, 6000);
    }

    private static void count(final Object wc, long delay) {
        MAIN.postDelayed(new Runnable() { public void run() {
            ChromeCosmetic.run(wc, COUNT, new java.util.function.Consumer<String>() { public void accept(String v) {
                int n = 0;
                try { n = (int) Double.parseDouble(v == null ? "0" : v.replace("\"", "").trim()); } catch (Throwable ignored) { }
                final int found = Math.max(0, n);
                MAIN.post(new Runnable() { public void run() { sCounts.put(wc, found); refreshAll(); } });
            }});
        }}, delay);
    }

    static void refreshAll() {
        for (int i = sButtons.size() - 1; i >= 0; i--) {
            Shield s = sButtons.get(i).get();
            if (s == null) { sButtons.remove(i); continue; }
            s.refresh();
        }
    }

    private static String name(View v) {
        try { return v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return ""; }
    }

    private static View find(View root, String id) {
        if (id.equals(name(root))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), id); if (f != null) return f; }
        }
        return null;
    }

    private static void attach(ViewGroup toolbar) {
        View box = find(toolbar, "toolbar_buttons");
        if (!(box instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) box;
        for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i) instanceof Shield) return;
        int at = -1;
        for (int i = 0; i < row.getChildCount(); i++) {
            String n = name(row.getChildAt(i));
            if (n.startsWith("optional_button") || n.startsWith("optional_toolbar_button")) at = i + 1;
        }
        if (at < 0) {
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                if (find(c, "tab_switcher_button") != null) { at = i; break; }
            }
        }
        if (at < 0) at = 0;
        Shield s = new Shield(toolbar.getContext(), toolbar);
        int w = Math.round(48 * toolbar.getResources().getDisplayMetrics().density);
        row.addView(s, at, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        sButtons.add(new WeakReference<Shield>(s));
        s.refresh();
        try { ChromeVpn.attach(row, at + 1, toolbar); } catch (Throwable ignored) { }
        try { ChromeExtensions.attach(row, at + 2, toolbar); } catch (Throwable ignored) { }
        try { ChromeDarkPages.attach(row, at + 3, toolbar); } catch (Throwable ignored) { }
    }

    static Object current(View anchor) {
        Object wc = visible(anchor.getRootView());
        return wc != null ? wc : sLast.get();
    }

    private static Object visible(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) return null;
        Class<?> type = ChromeCosmetic.webContentsClass();
        if (type == null) return null;
        if (v.getClass().getName().endsWith("ContentView") && v.isShown() && v.getWidth() > 0) {
            Field f = field(v.getClass(), type);
            if (f != null) { try { Object wc = f.get(v); if (wc != null) return wc; } catch (Throwable ignored) { } }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) { Object wc = visible(g.getChildAt(i)); if (wc != null) return wc; }
        }
        return null;
    }

    private static Field field(Class<?> k, Class<?> type) {
        if (sViewFields.containsKey(k)) return sViewFields.get(k);
        Field found = null;
        for (Class<?> c = k; c != null && c != View.class && found == null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !type.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true); found = f; break;
            }
        }
        sViewFields.put(k, found);
        return found;
    }

    private static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static Path shield(float cx, float cy, float size) {
        float w = size * 0.78f, h = size * 0.9f, l = cx - w / 2f, t = cy - h / 2f;
        Path p = new Path();
        p.moveTo(cx, t);
        p.lineTo(l + w, t + h * 0.16f);
        p.lineTo(l + w, t + h * 0.46f);
        p.cubicTo(l + w, t + h * 0.74f, cx + w * 0.22f, t + h * 0.9f, cx, t + h);
        p.cubicTo(cx - w * 0.22f, t + h * 0.9f, l, t + h * 0.74f, l, t + h * 0.46f);
        p.lineTo(l, t + h * 0.16f);
        p.close();
        return p;
    }

    static final class Shield extends View {
        private final WeakReference<View> toolbar;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), badge = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private WeakReference<ImageView> tint = new WeakReference<ImageView>(null);
        private Object shown;
        private int shownLevel = DEFAULT_LEVEL;
        private long lastScan;
        private final ViewTreeObserver.OnGlobalLayoutListener layout = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastScan < 400) return;
                lastScan = now;
                MAIN.post(new Runnable() { public void run() { refresh(); } });
            }
        };

        Shield(Context c, View toolbar) {
            super(c);
            this.toolbar = new WeakReference<View>(toolbar);
            setClickable(true);
            setFocusable(true);
            setContentDescription(I18n.cs("Ad blocker"));
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) {
                try { setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { }
            }
            line.setStyle(Paint.Style.STROKE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(I18n.medium());
            setOnClickListener(new OnClickListener() { public void onClick(View v) {
                try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                try { open(Shield.this); } catch (Throwable ignored) { }
            }});
            setVisibility(GONE);
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); getViewTreeObserver().addOnGlobalLayoutListener(layout); refresh(); }
        @Override protected void onDetachedFromWindow() { getViewTreeObserver().removeOnGlobalLayoutListener(layout); super.onDetachedFromWindow(); }

        void refresh() {
            if (!sChecked) {
                sChecked = true;
                try { sEnabled = !"0".equals(android.provider.Settings.Secure.getString(getContext().getContentResolver(), AdBlock.KEY)) && PatronGate.ok(getContext().getApplicationContext()); } catch (Throwable ignored) { }
            }
            if (!sEnabled || !pinned(getContext())) { if (getVisibility() != GONE) setVisibility(GONE); return; }
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            Object wc = current(this);
            String host = ChromeCosmetic.hostOf(wc);
            int lvl = host == null || !filtering(getContext()) ? 0 : level(getContext(), host);
            if (wc != shown || lvl != shownLevel) { shown = wc; shownLevel = lvl; }
            invalidate();
        }

        private int iconColor() {
            ImageView src = tint.get();
            if (src == null || !src.isAttachedToWindow()) {
                View t = toolbar.get();
                View f = t == null ? null : find(t, "tab_switcher_button");
                src = f instanceof ImageView ? (ImageView) f : null;
                tint = new WeakReference<ImageView>(src);
            }
            if (src != null && src.getImageTintList() != null) return src.getImageTintList().getColorForState(src.getDrawableState(), src.getImageTintList().getDefaultColor());
            return night(getContext()) ? 0xFFE3E3E3 : 0xFF444746;
        }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, size = 22 * d;
            int color = iconColor();
            boolean active = shownLevel > 0 && ChromeCosmetic.hostOf(shown) != null;
            Path p = shield(cx, cy, size);
            if (active) {
                fill.setColor(night(getContext()) ? 0xFFF28B82 : 0xFFB3261E);
                c.drawPath(p, fill);
                fill.setColor(night(getContext()) ? 0xFF601410 : Color.WHITE);
                float bw = size * 0.42f, bh = size * 0.11f;
                c.drawRoundRect(new RectF(cx - bw / 2f, cy - bh / 2f - size * 0.02f, cx + bw / 2f, cy + bh / 2f - size * 0.02f), bh / 2f, bh / 2f, fill);
            } else {
                line.setColor(color);
                line.setAlpha(150);
                line.setStrokeWidth(1.8f * d);
                c.drawPath(p, line);
            }
            Integer n = shown == null ? null : sCounts.get(shown);
            if (active && n != null && n > 0) {
                String s = n > 999 ? "999+" : String.valueOf(n);
                text.setTextSize(9.5f * d);
                float tw = text.measureText(s), bh = 13 * d, bw = Math.max(bh, tw + 7 * d);
                float right = cx + size * 0.62f, bottom = cy + size * 0.62f;
                RectF r = new RectF(right - bw, bottom - bh, right, bottom);
                badge.setColor(night(getContext()) ? 0xFF3C4043 : 0xFFE8EAED);
                c.drawRoundRect(r, bh / 2f, bh / 2f, badge);
                text.setColor(night(getContext()) ? 0xFFE8EAED : 0xFF202124);
                c.drawText(s, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2f, text);
            }
        }
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static String pinnedId(Context c) {
        SharedPreferences p = prefs(c);
        String id = p.getString("pinned_ext", null);
        if (id != null) return id;
        if (p.contains("pinned")) return p.getBoolean("pinned", true) ? "adblock" : "";
        return ChromeExtensions.ID;
    }
    static void setPinnedId(Context c, String id) {
        prefs(c).edit().putString("pinned_ext", id == null ? "" : id).apply();
        refreshAll();
        try { ChromeVpn.refreshButtons(); } catch (Throwable ignored) { }
        try { ChromeExtensions.refreshButtons(); } catch (Throwable ignored) { }
        try { ChromeDarkPages.refreshButtons(); } catch (Throwable ignored) { }
    }
    static boolean pinned(Context c) { return "adblock".equals(pinnedId(c)); }
    static void setPinned(Context c, boolean on) { setPinnedId(c, on ? "adblock" : ""); }
    static List<View> buttons() {
        List<View> out = new ArrayList<View>();
        for (int i = sButtons.size() - 1; i >= 0; i--) { Shield s = sButtons.get(i).get(); if (s == null) sButtons.remove(i); else out.add(s); }
        return out;
    }
    static boolean enabled() { return sEnabled; }
    static String summary(View anchor) {
        String host = ChromeCosmetic.hostOf(current(anchor));
        return host == null ? "Open a website to choose its filtering" : host + ": " + NAMES[filtering(anchor.getContext()) ? level(anchor.getContext(), host) : 0];
    }

    static void open(final View anchor) {
        final Context c = anchor.getContext();
        final boolean dark = night(c);
        final PopupWindow[] holder = new PopupWindow[1];
        LinearLayout root = panel(anchor, new Runnable() { public void run() { if (holder[0] != null) holder[0].dismiss(); } }, true);
        GradientDrawable shape = new GradientDrawable();
        int sheet = ChromeLayout.sheetColor(dark ? 0xFF1F1F1F : 0xFFFFFFFF, anchor);
        shape.setColor(sheet);
        shape.setCornerRadius(dp(c, 20));
        int outline = ChromeLayout.sheetOutline(sheet, dark ? 0xFFE3E3E3 : 0xFF1F1F1F);
        if (outline != 0) shape.setStroke(Math.max(1, dp(c, 1)), outline);
        ChromeLayout.refineSheet(anchor, shape, Math.max(1, dp(c, 1)), dark ? 0xFFE3E3E3 : 0xFF1F1F1F);
        root.setBackground(shape);
        root.setClipToOutline(true);
        final ChromeSheetMotion.Sheet pw = new ChromeSheetMotion.Sheet(root, dp(c, 300), ViewGroup.LayoutParams.WRAP_CONTENT, anchor, dp(c, 20));
        holder[0] = pw;
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(c, 12));
        pw.setOnDismissListener(new PopupWindow.OnDismissListener() { public void onDismiss() { refreshAll(); } });
        final boolean fromTop = anchor instanceof Shield;
        if (fromTop) pw.showAsDropDown(anchor, 0, -dp(c, 4), Gravity.END);
        else {
            int[] at = new int[2];
            anchor.getLocationInWindow(at);
            pw.showAtLocation(anchor, Gravity.BOTTOM | Gravity.START, Math.max(dp(c, 8), at[0]), anchor.getRootView().getHeight() - at[1] + dp(c, 6));
        }
        final LinearLayout card = root;
        pw.open(new Runnable() { public void run() { ChromeSheetMotion.enter(card, fromTop, null); } });
    }

    static LinearLayout panel(final View anchor, final Runnable dismiss, boolean withSite) {
        final Context c = anchor.getContext();
        final Object wc = current(anchor);
        final String host = ChromeCosmetic.hostOf(wc);
        final boolean dark = night(c);
        final int fg = dark ? 0xFFE3E3E3 : 0xFF1F1F1F;
        final int head = ChromeTabTransition.mix(ChromeLayout.sheetColor(dark ? 0xFF1F1F1F : 0xFFFFFFFF, anchor), fg, dark ? 0.08f : 0.05f);
        final int muted = dark ? 0xFFA8ABAF : 0xFF5F6368, accent = dark ? 0xFF8AB4F8 : 0xFF1F5FB4, track = dark ? 0xFF444746 : 0xFFC8C9CE;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        final Runnable close = dismiss;

        TextView title = label(c, host == null ? "This page" : host, withSite ? 17 : 15, withSite ? fg : muted, false);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        if (withSite) title.setBackgroundColor(head); else title.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 4));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        if (host == null) {
            TextView none = label(c, "Filtering isn't available on this page.", 15, muted, false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 24));
            root.addView(none, new LinearLayout.LayoutParams(-1, -2));
        } else {
            final int start = level(c, host);
            final TextView mode = label(c, NAMES[start], 20, accent, false);
            Slider slider = new Slider(c, start, accent, track, new Slider.Listener() {
                @Override public void changed(final int lvl) {
                    setLevel(c, host, lvl);
                    mode.setText(I18n.cs(NAMES[lvl]));
                    refreshAll();
                }
                @Override public void released() {
                    reload(wc, host);
                    refreshAll();
                }
            });
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, dp(c, 56));
            sl.setMargins(dp(c, 24), dp(c, 20), dp(c, 24), dp(c, 8));
            root.addView(slider, sl);
            TextView caption = label(c, "filtering mode", 15, muted, false);
            caption.setGravity(Gravity.CENTER);
            root.addView(caption, new LinearLayout.LayoutParams(-1, -2));
            mode.setGravity(Gravity.CENTER);
            mode.setPadding(0, dp(c, 2), 0, dp(c, 16));
            root.addView(mode, new LinearLayout.LayoutParams(-1, -2));

            View rule = new View(c);
            rule.setBackgroundColor(track);
            root.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1) / 2)));

            TextView section = label(c, "On this website", 14, muted, false);
            section.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 4));
            root.addView(section, new LinearLayout.LayoutParams(-1, -2));

            root.addView(row(c, Icon.BOLT, "Remove an element", fg, new Runnable() { public void run() {
                close.run();
                pick(c, wc, host, anchor);
            }}));
            final int custom = hidden(c, host).size();
            if (custom > 0) {
                root.addView(row(c, Icon.UNDO, "Restore removed elements (" + custom + ")", fg, new Runnable() { public void run() {
                    close.run();
                    clearHidden(c, host);
                    reload(wc, host);
                }}));
            }
            View gap = new View(c);
            root.addView(gap, new LinearLayout.LayoutParams(-1, dp(c, 8)));
        }

        LinearLayout foot = row(c, Icon.GEAR, "Pixel Suite settings", fg, new Runnable() { public void run() {
            close.run();
            try {
                ChromeExtensions.openPixelSuite(c, "extensions");
            } catch (Throwable ignored) { }
        }});
        foot.setBackgroundColor(head);
        root.addView(foot);
        if (wc != null) count(wc, 0);
        return root;
    }

    private static void reload(Object wc, String host) {
        if (wc == null) return;
        sCounts.remove(wc);
        ChromeCosmetic.runAll(wc, "(function () { if (window !== window.top) return 0; try { window.location.reload(); } catch (e) { window.location.replace(window.location.href); } return 1; })()");
    }

    static boolean googleSearch(String host) {
        return host != null && host.matches("^(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?$");
    }

    private static TextView label(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(I18n.cs(s));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface((bold ? I18n.medium() : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)));
        return t;
    }

    private enum Icon { BOLT, UNDO, GEAR }

    private static LinearLayout row(Context c, final Icon icon, String text, final int color, final Runnable action) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        r.setMinimumHeight(dp(c, 52));
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            try { r.setForeground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { }
        }
        View glyph = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas k) {
                float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
                p.setColor(color);
                Path path = new Path();
                if (icon == Icon.BOLT) {
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(w * 0.58f, h * 0.08f); path.lineTo(w * 0.24f, h * 0.56f); path.lineTo(w * 0.48f, h * 0.56f);
                    path.lineTo(w * 0.40f, h * 0.92f); path.lineTo(w * 0.76f, h * 0.42f); path.lineTo(w * 0.52f, h * 0.42f); path.close();
                    k.drawPath(path, p);
                } else if (icon == Icon.UNDO) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * d); p.setStrokeCap(Paint.Cap.ROUND);
                    k.drawArc(new RectF(w * 0.2f, h * 0.22f, w * 0.82f, h * 0.84f), 200, 290, false, p);
                    path.moveTo(w * 0.14f, h * 0.30f); path.lineTo(w * 0.24f, h * 0.50f); path.lineTo(w * 0.42f, h * 0.38f);
                    k.drawPath(path, p);
                } else {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * d);
                    float cx = w / 2f, cy = h / 2f;
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        k.drawLine(cx + (float) Math.cos(a) * w * 0.26f, cy + (float) Math.sin(a) * h * 0.26f, cx + (float) Math.cos(a) * w * 0.40f, cy + (float) Math.sin(a) * h * 0.40f, p);
                    }
                    k.drawCircle(cx, cy, w * 0.26f, p);
                    k.drawCircle(cx, cy, w * 0.10f, p);
                }
            }
        };
        r.addView(glyph, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
        TextView t = label(c, text, 17, color, false);
        t.setPadding(dp(c, 18), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
            action.run();
        }});
        return r;
    }

    static final class Slider extends View {
        interface Listener { void changed(int level); void released(); }
        private boolean moved;
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Listener listener;
        private int value;

        Slider(Context c, int value, int accent, int track, Listener l) {
            super(c);
            this.value = value;
            this.listener = l;
            on.setColor(accent);
            off.setColor(track);
            setContentDescription(I18n.cs("Filtering mode"));
        }

        private float seg() { return getWidth() / 4f; }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, s = seg(), h = getHeight(), bar = 18 * d, gap = 1.5f * d;
            for (int i = 0; i < 4; i++) {
                if (i == value) continue;
                RectF r = new RectF(i * s + (i == 0 ? 0 : gap / 2f), (h - bar) / 2f, (i + 1) * s - (i == 3 ? 0 : gap / 2f), (h + bar) / 2f);
                c.drawRect(r, i < value ? on : off);
            }
            RectF knob = new RectF(value * s + 2 * d, 0, (value + 1) * s - 2 * d, h);
            c.drawRoundRect(knob, 14 * d, 14 * d, on);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_CANCEL) { if (moved) { moved = false; listener.released(); } return true; }
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(true);
                if (a == MotionEvent.ACTION_DOWN) moved = false;
                int v = Math.max(0, Math.min(3, (int) (e.getX() / Math.max(1f, seg()))));
                if (v != value) {
                    value = v;
                    invalidate();
                    try { Haptics.tick(getContext()); } catch (Throwable ignored) { }
                    moved = true;
                    listener.changed(v);
                }
                if (a == MotionEvent.ACTION_UP && moved) { moved = false; listener.released(); }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    private static void pick(final Context c, final Object wc, final String host, final View anchor) {
        if (wc == null || host == null) return;
        ChromeCosmetic.run(wc, PICKER, null);
        final int[] polls = {0};
        MAIN.postDelayed(new Runnable() { public void run() {
            final Runnable self = this;
            ChromeCosmetic.run(wc, POLL, new java.util.function.Consumer<String>() { public void accept(String v) {
                boolean open = false;
                List<String> picked = new ArrayList<String>();
                try {
                    Object outer = new org.json.JSONTokener(v).nextValue();
                    org.json.JSONObject o = new org.json.JSONObject(outer instanceof String ? (String) outer : String.valueOf(outer));
                    open = o.optBoolean("open");
                    org.json.JSONArray a = o.optJSONArray("p");
                    for (int i = 0; a != null && i < a.length(); i++) picked.add(a.optString(i));
                } catch (Throwable ignored) { }
                if (!picked.isEmpty()) {
                    addHidden(c, host, picked);
                    count(wc, 300);
                }
                if (open && ++polls[0] < 400) MAIN.postDelayed(self, 600);
                else MAIN.post(new Runnable() { public void run() { refreshAll(); } });
            }});
        }}, 600);
    }

    static final String COUNT = ""
            + "(function () { var D = document, n = 0; \n"
            + "try { n = D.querySelectorAll('[data-ps-hidden]').length; } catch (e) { } \n"
            + "var s = window.__psSels; \n"
            + "if (s) for (var i = 0; i < s.length; i += 200) { var b = s.slice(i, i + 200).filter(Boolean); if (!b.length) continue; \n"
            + "try { n += D.querySelectorAll(b.join(',')).length; } catch (e) { for (var j = 0; j < b.length; j++) { try { n += D.querySelectorAll(b[j]).length; } catch (x) { } } } } \n"
            + "try { if (window.__psAnnoyN) n += window.__psAnnoyN(); } catch (e) { } \n"
            + "return n; })()";

    static final String POLL = ""
            + "(function () { var p = window.__psPicked || []; window.__psPicked = []; \n"
            + "return JSON.stringify({ p: p, open: !!window.__psPicking }); })()";

    static final String ANNOY = ""
            + "(function () { \n"
            + "if (window.__psAnnoy) return; window.__psAnnoy = 1; \n"
            + "var D = document; \n"
            + "var S = ['#onetrust-banner-sdk', '#onetrust-consent-sdk', '.onetrust-pc-dark-filter', '#CybotCookiebotDialog', '#CybotCookiebotDialogBodyUnderlay', \n"
            + "'.qc-cmp2-container', '#qc-cmp2-container', '[id^=\"sp_message_container\"]', '#didomi-host', '.didomi-popup-backdrop', '.fc-consent-root', \n"
            + "'#usercentrics-root', '#truste-consent-track', '.truste_overlay', '.cc-window', '.cc-banner', '#cookie-law-info-bar', '#cookie-notice', \n"
            + "'#cookiescript_injected', '.cookie-consent', '.cookie-banner', '.cookies-banner', '#cookie-banner', '#cookieConsent', '#gdpr-banner', \n"
            + "'[aria-label*=\"cookie\" i][role=\"dialog\"]', '[aria-label*=\"consent\" i][role=\"dialog\"]', '[class*=\"cookie-consent\" i]', '[class*=\"cookie-banner\" i]', \n"
            + "'[id*=\"cookie-consent\" i]', '[id*=\"cookie-banner\" i]', '[class*=\"newsletter-popup\" i]', '[class*=\"newsletter-modal\" i]', \n"
            + "'[class*=\"subscribe-popup\" i]', '[class*=\"subscribe-modal\" i]', '[id*=\"newsletter-popup\" i]', '.smartbanner', '#smartbanner', \n"
            + "'[class*=\"app-banner\" i]', '[class*=\"open-in-app\" i]', '[id*=\"open-in-app\" i]', '[class*=\"push-notification-prompt\" i]', \n"
            + "'[class*=\"onesignal-slidedown\" i]', '#onesignal-slidedown-container', '[class*=\"pushcrew\" i]', '.tp-modal', '.tp-backdrop']; \n"
            + "var sel = S.join(','); \n"
            + "window.__psAnnoyN = function () { try { return D.querySelectorAll(sel).length; } catch (e) { return 0; } }; \n"
            + "var css = sel + '{display:none!important}'; \n"
            + "try { var st = new CSSStyleSheet(); st.replaceSync(css); var add = function () { try { if (D.adoptedStyleSheets.indexOf(st) < 0) D.adoptedStyleSheets = D.adoptedStyleSheets.concat([st]); } catch (e) { } }; add(); D.addEventListener('DOMContentLoaded', add, true); } \n"
            + "catch (e) { try { var s2 = D.createElement('style'); s2.textContent = css; (D.head || D.documentElement).appendChild(s2); } catch (x) { } } \n"
            + "var tries = 0, unlock = function () { try { if (window.__psAnnoyN() > 0) { [D.documentElement, D.body].forEach(function (n) { if (!n) return; var cs = getComputedStyle(n); \n"
            + "if (cs.overflow === 'hidden' || cs.overflowY === 'hidden') { n.style.setProperty('overflow', 'auto', 'important'); n.style.setProperty('overflow-y', 'auto', 'important'); } \n"
            + "if (cs.position === 'fixed' && n === D.body) n.style.setProperty('position', 'static', 'important'); }); } } catch (e) { } }; \n"
            + "var t = setInterval(function () { unlock(); if (++tries >= 15) clearInterval(t); }, 1000); \n"
            + "})();";

    static final String PICKER = ""
            + "(function () { \n"
            + "var W = window, D = document; \n"
            + "if (W.__psPicking) return; W.__psPicking = 1; W.__psPicked = W.__psPicked || []; \n"
            + "var dark = W.matchMedia && W.matchMedia('(prefers-color-scheme: dark)').matches; \n"
            + "var Z = '2147483647'; \n"
            + "var box = D.createElement('div'); \n"
            + "box.style.cssText = 'position:fixed;pointer-events:none;z-index:' + Z + ';border:2px solid #1f5fb4;background:rgba(31,95,180,0.18);border-radius:6px;display:none;transition:all .08s ease-out;'; \n"
            + "var bar = D.createElement('div'); \n"
            + "bar.style.cssText = 'position:fixed;left:12px;right:12px;bottom:16px;z-index:' + Z + ';display:flex;flex-direction:column;gap:10px;padding:14px;border-radius:20px;font:15px/1.3 sans-serif;box-shadow:0 6px 24px rgba(0,0,0,.28);' \n"
            + "+ (dark ? 'background:#1f1f1f;color:#e3e3e3;' : 'background:#fff;color:#1f1f1f;'); \n"
            + "var hint = D.createElement('div'); hint.textContent = 'Tap an element to remove it'; hint.style.cssText = 'text-align:center;opacity:.8;'; \n"
            + "var row = D.createElement('div'); row.style.cssText = 'display:flex;gap:8px;'; \n"
            + "function btn(t, main) { var b = D.createElement('button'); b.textContent = t; \n"
            + "b.style.cssText = 'flex:1;border:0;border-radius:14px;padding:12px 6px;font:500 15px sans-serif;' + (main ? 'background:#1f5fb4;color:#fff;' : (dark ? 'background:#2d2f31;color:#e3e3e3;' : 'background:#e9ebf6;color:#1f1f1f;')); \n"
            + "row.appendChild(b); return b; } \n"
            + "var bigger = btn('Bigger'), hide = btn('Remove', true), cancel = btn('Done'); \n"
            + "bar.appendChild(hint); bar.appendChild(row); \n"
            + "(D.body || D.documentElement).appendChild(box); (D.body || D.documentElement).appendChild(bar); \n"
            + "var cur = null; \n"
            + "function place() { if (!cur || !cur.isConnected) { box.style.display = 'none'; return; } var r = cur.getBoundingClientRect(); \n"
            + "box.style.display = 'block'; box.style.left = (r.left - 2) + 'px'; box.style.top = (r.top - 2) + 'px'; box.style.width = (r.width) + 'px'; box.style.height = (r.height) + 'px'; } \n"
            + "function esc(s) { return (W.CSS && CSS.escape) ? CSS.escape(s) : s.replace(/([^\\w-])/g, '\\\\$1'); } \n"
            + "function stable(s) { return s && s.length < 48 && !/\\d{4,}/.test(s) && !/^[a-z]{1,3}-[A-Za-z0-9]{5,}$/.test(s); } \n"
            + "function one(q) { try { return D.querySelectorAll(q).length === 1; } catch (e) { return false; } } \n"
            + "function sel(e) { \n"
            + "if (e.id && stable(e.id) && one('#' + esc(e.id))) return '#' + esc(e.id); \n"
            + "var parts = [], n = e; \n"
            + "for (var depth = 0; n && n.nodeType === 1 && n !== D.body && n !== D.documentElement && depth < 6; depth++) { \n"
            + "if (depth > 0 && n.id && stable(n.id)) { parts.unshift('#' + esc(n.id)); var q0 = parts.join(' > '); if (one(q0)) return q0; break; } \n"
            + "var s = n.tagName.toLowerCase(); \n"
            + "var cls = [].filter.call(n.classList, stable).slice(0, 3); \n"
            + "if (cls.length) s += '.' + cls.map(esc).join('.'); \n"
            + "var p = n.parentElement; \n"
            + "if (p) { var same = [].filter.call(p.children, function (c) { return c.tagName === n.tagName; }); if (same.length > 1) s += ':nth-of-type(' + (same.indexOf(n) + 1) + ')'; } \n"
            + "parts.unshift(s); var q = parts.join(' > '); if (one(q)) return q; n = p; } \n"
            + "return parts.join(' > '); } \n"
            + "function mine(t) { return t && (bar.contains(t) || t === box); } \n"
            + "function tap(ev) { if (mine(ev.target)) return; ev.preventDefault(); ev.stopPropagation(); ev.stopImmediatePropagation(); \n"
            + "var x = ev.clientX, y = ev.clientY; if (ev.changedTouches && ev.changedTouches[0]) { x = ev.changedTouches[0].clientX; y = ev.changedTouches[0].clientY; } \n"
            + "var e = D.elementFromPoint(x, y); if (!e || mine(e) || e === D.body || e === D.documentElement) return; cur = e; place(); } \n"
            + "W.addEventListener('click', tap, true); \n"
            + "var stop = function (ev) { if (!mine(ev.target)) { ev.preventDefault(); ev.stopPropagation(); } }; \n"
            + "W.addEventListener('mousedown', stop, true); W.addEventListener('pointerup', stop, true); W.addEventListener('auxclick', stop, true); \n"
            + "W.addEventListener('scroll', place, true); W.addEventListener('resize', place, true); \n"
            + "bigger.addEventListener('click', function () { if (cur && cur.parentElement && cur.parentElement !== D.body && cur.parentElement !== D.documentElement) { cur = cur.parentElement; place(); } }); \n"
            + "hide.addEventListener('click', function () { if (!cur) return; var q = sel(cur); \n"
            + "try { var st = D.createElement('style'); st.textContent = q + '{display:none!important}'; (D.head || D.documentElement).appendChild(st); } catch (e) { } \n"
            + "cur.style.setProperty('display', 'none', 'important'); W.__psPicked.push(q); cur = null; place(); hint.textContent = 'Removed. Tap another element, or tap Done.'; }); \n"
            + "cancel.addEventListener('click', function () { W.removeEventListener('click', tap, true); W.removeEventListener('mousedown', stop, true); \n"
            + "W.removeEventListener('pointerup', stop, true); W.removeEventListener('auxclick', stop, true); W.removeEventListener('scroll', place, true); W.removeEventListener('resize', place, true); \n"
            + "try { box.remove(); bar.remove(); } catch (e) { } W.__psPicking = 0; }); \n"
            + "})();";
}
