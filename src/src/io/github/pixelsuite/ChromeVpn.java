package io.github.pixelsuite;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ChromeVpn {
    private ChromeVpn() {}

    static final String ID = "vpn";
    private static final Uri URI = Uri.parse("content://" + VpnProvider.AUTHORITY);
    private static final Uri STATE = Uri.parse("content://" + VpnProvider.AUTHORITY + "/state");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArrayList<Runnable> sListeners = new CopyOnWriteArrayList<Runnable>();
    private static final List<WeakReference<Button>> sButtons = new ArrayList<WeakReference<Button>>();
    private static volatile Bundle sState;
    private static volatile long sFetchedAt;
    private static volatile Context sApp;
    private static boolean sObserving;
    private static Boolean sPending;
    private static long sPendingAt;

    static void prime(Context c) {
        if (sApp == null) sApp = c.getApplicationContext();
        if (!sObserving) {
            sObserving = true;
            try {
                sApp.getContentResolver().registerContentObserver(STATE, false, new ContentObserver(MAIN) {
                    @Override public void onChange(boolean self) { fetch("state", null, null); }
                });
            } catch (Throwable ignored) { }
        }
        fetch("state", null, null);
    }

    static void watch(Runnable r) { sListeners.addIfAbsent(r); }
    static void unwatch(Runnable r) { sListeners.remove(r); }

    static boolean premium() {
        Bundle b = sState;
        if (b != null) return b.getBoolean("premium");
        try { return sApp != null && PatronGate.ok(sApp); } catch (Throwable t) { return false; }
    }

    static boolean chromeOn() {
        if (sPending != null && android.os.SystemClock.uptimeMillis() - sPendingAt < 4000) return sPending;
        Bundle b = sState;
        return b != null && b.getBoolean("chromeOn");
    }

    static String exitCountry(Context c) {
        long now = android.os.SystemClock.uptimeMillis();
        if (sState == null || now - sFetchedAt > 3000) {
            try {
                if (sApp == null) sApp = c.getApplicationContext();
                Bundle r = sApp.getContentResolver().call(URI, "state", null, null);
                if (r != null) { sState = r; sFetchedAt = now; }
            } catch (Throwable ignored) { }
            if (!sObserving) { final Context app = sApp; MAIN.post(new Runnable() { public void run() { try { prime(app); } catch (Throwable ignored) { } } }); }
        }
        Bundle b = sState;
        if (b == null || b.getInt("state") != PixelVpnService.ON || !b.getBoolean("chromeOn")) return null;
        return b.getString("code");
    }

    static boolean connecting() { Bundle b = sState; return b != null && b.getInt("state") == PixelVpnService.CONNECTING; }

    static String status() {
        Bundle b = sState;
        if (b == null) return "Loading…";
        if (!b.getBoolean("premium")) return "Needs Pixel Suite premium";
        int st = b.getInt("state"), scope = b.getInt("scope");
        String place = b.getString("country", "");
        if (st == PixelVpnService.CONNECTING) return "Connecting to " + place + "…";
        if (st == PixelVpnService.ON) {
            if (scope == PixelVpnService.PHONE) return "On · " + place + " · whole phone";
            if (scope == PixelVpnService.CHROME_ONLY) return "On · " + place + " · Chrome only";
            return "Off in Chrome · on for other apps";
        }
        String e = b.getString("error");
        return e != null ? e : "Off · " + place;
    }

    static String footer() {
        Bundle b = sState;
        int st = b == null ? PixelVpnService.OFF : b.getInt("state"), scope = b == null ? PixelVpnService.CHROME_ONLY : b.getInt("scope");
        if (st != PixelVpnService.OFF && scope == PixelVpnService.PHONE) return "The VPN is on for the whole phone. Switching it off here only takes Chrome out.";
        if (st != PixelVpnService.OFF && scope == PixelVpnService.PHONE_NO_CHROME) return "Other apps use the VPN; Chrome is left out. Switch it on to add Chrome.";
        return "Only Chrome uses this VPN. Other apps keep their own connection.";
    }

    private static void fetch(final String method, final String arg, final Bundle extras) {
        final Context app = sApp;
        if (app == null) return;
        IO.execute(new Runnable() { public void run() {
            Bundle r = null;
            try { r = app.getContentResolver().call(URI, method, arg, extras); } catch (Throwable ignored) { }
            final Bundle result = r;
            MAIN.post(new Runnable() { public void run() {
                if (result != null) {
                    sState = result; sFetchedAt = android.os.SystemClock.uptimeMillis();
                    if (!"state".equals(method)) sPending = null;
                    if (result.getBoolean("consent")) consent(app);
                } else if (!"state".equals(method)) { sPending = null; }
                changed();
            }});
        }});
    }

    private static void consent(Context app) {
        try {
            app.startActivity(new Intent().setClassName("io.github.roviandev.pixelsuite", "io.github.pixelsuite.VpnPermissionActivity")
                .putExtra(PixelVpnService.EXTRA_COUNTRY, sState == null ? "US" : sState.getString("chosen", "US"))
                .putExtra(PixelVpnService.EXTRA_SCOPE, PixelVpnService.CHROME_ONLY)
                .putExtra(PixelVpnService.EXTRA_CHROME, app.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
    }

    static void setOn(boolean on) {
        sPending = on; sPendingAt = android.os.SystemClock.uptimeMillis();
        Bundle e = new Bundle(); e.putBoolean("on", on);
        changed();
        fetch("chrome", null, e);
    }

    private static void pick(String code) { fetch("country", code, null); }

    private static void changed() {
        for (Runnable r : sListeners) r.run();
        refreshButtons();
    }

    static final ChromeExtensions.Extension EXTENSION = new ChromeExtensions.Extension() {
        public String id() { return ID; }
        public String title() { return "VPN"; }
        public String summary(View anchor) { return premium() ? status() : "Needs Pixel Suite premium"; }
        public boolean available() { return premium(); }
        public int switchState() { return chromeOn() ? 1 : 0; }
        public void setSwitch(boolean on) { setOn(on); }
        public void draw(Canvas c, float cx, float cy, float size, int ink, boolean dark) {
            globe(c, cx, cy, size, chromeOn() ? (dark ? 0xFF8AB4F8 : 0xFF1A73E8) : (available() ? ink : 0xFF9AA0A6), chromeOn(), dark);
        }
        public View content(View anchor, Runnable dismiss) { return panel(anchor); }
    };

    static void globe(Canvas c, float cx, float cy, float size, int color, boolean filled, boolean dark) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size * 0.46f, sw = Math.max(1.5f, size * 0.075f);
        if (filled) {
            p.setColor(color); c.drawCircle(cx, cy, r + sw / 2f, p);
            color = dark ? 0xFF062E6F : Color.WHITE;
        }
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw); p.setColor(color); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawCircle(cx, cy, r, p);
        c.drawOval(new RectF(cx - r * 0.42f, cy - r, cx + r * 0.42f, cy + r), p);
        float y = r * 0.38f, x = (float) Math.sqrt(r * r - y * y);
        c.drawLine(cx - x, cy - y, cx + x, cy - y, p);
        c.drawLine(cx - x, cy + y, cx + x, cy + y, p);
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    private static boolean night(Context c) { return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }

    private static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(I18n.cs(s)); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color);
        t.setTypeface((medium ? I18n.medium() : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)));
        return t;
    }

    private static void ripple(View v) {
        TypedValue tv = new TypedValue();
        if (v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            try { v.setForeground(v.getContext().getDrawable(tv.resourceId)); } catch (Throwable ignored) { }
        }
    }

    private static final class Capped extends ScrollView {
        final int max;
        Capped(Context c, int max) { super(c); this.max = max; setVerticalFadingEdgeEnabled(true); setFadingEdgeLength(dp(c, 24)); }
        @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST)); }
    }

    static View panel(View anchor) {
        final Context c = anchor.getContext();
        prime(c);
        final boolean dark = night(c);
        final int fg = dark ? 0xFFE3E3E3 : 0xFF1F1F1F, muted = dark ? 0xFFA8ABAF : 0xFF5F6368, accent = dark ? 0xFF8AB4F8 : 0xFF1F5FB4;
        final int bg = ChromeLayout.sheetColor(dark ? 0xFF1F1F1F : 0xFFFFFFFF, anchor);
        final int line = ChromeTabTransition.mix(bg, fg, dark ? 0.16f : 0.10f), disc = ChromeTabTransition.mix(bg, fg, dark ? 0.10f : 0.06f);

        final LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 24), dp(c, 6), dp(c, 20), dp(c, 6));
        row.setMinimumHeight(dp(c, 64));
        ripple(row);
        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(c, "VPN for Chrome", 16, fg, false));
        final TextView sub = text(c, status(), 13, muted, false);
        sub.setPadding(0, dp(c, 2), 0, 0);
        labels.addView(sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        final ChromeExtensions.Toggle toggle = new ChromeExtensions.Toggle(c, chromeOn(), accent, dark);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(c, 52), dp(c, 32)));
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
            boolean next = !chromeOn();
            toggle.set(next);
            setOn(next);
        }});
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        View rule = new View(c);
        rule.setBackgroundColor(line);
        root.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1) / 2)));

        TextView heading = text(c, "Country", 14, accent, true);
        heading.setPadding(dp(c, 24), dp(c, 14), dp(c, 24), dp(c, 6));
        root.addView(heading);

        final Capped scroll = new Capped(c, dp(c, 300));
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(c, 6));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        final TextView note = text(c, footer(), 13, muted, false);
        note.setPadding(dp(c, 24), dp(c, 8), dp(c, 24), dp(c, 16));
        root.addView(note);

        final String[] shown = {null};
        final Runnable bind = new Runnable() { public void run() {
            sub.setText(I18n.cs(status()));
            note.setText(I18n.cs(footer()));
            toggle.set(chromeOn());
            Bundle b = sState;
            ArrayList<String> rows = b == null ? null : b.getStringArrayList("countries");
            String chosen = b == null ? null : b.getString("chosen");
            String key = (rows == null ? "none" : rows.size() + ":" + (rows.isEmpty() ? "" : rows.get(rows.size() - 1))) + "|" + chosen + "|" + (b != null && b.getBoolean("loading"));
            if (key.equals(shown[0])) return;
            shown[0] = key;
            list.removeAllViews();
            if (rows == null || rows.isEmpty()) {
                TextView wait = text(c, b != null && b.getBoolean("loading") ? "Getting the free server list…" : "No servers yet. Pixel Suite loads them when you open this.", 14, muted, false);
                wait.setPadding(dp(c, 24), dp(c, 12), dp(c, 24), dp(c, 12));
                list.addView(wait);
                return;
            }
            for (String r : rows) {
                final String[] f = r.split("\t");
                if (f.length < 4) continue;
                boolean selected = f[0].equals(chosen);
                final boolean off = f.length > 4 && "unavailable".equals(f[4]);
                LinearLayout item = new LinearLayout(c);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(c, 20), 0, dp(c, 20), 0);
                item.setMinimumHeight(dp(c, 56));
                ripple(item);
                TextView flag = text(c, VpnServers.flag(f[0]), 20, fg, false);
                flag.setGravity(Gravity.CENTER);
                GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(disc); flag.setBackground(d);
                LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)); fp.rightMargin = dp(c, 14);
                item.addView(flag, fp);
                LinearLayout names = new LinearLayout(c);
                names.setOrientation(LinearLayout.VERTICAL);
                names.addView(text(c, f[1], 15, fg, selected));
                long best = 0; try { best = Long.parseLong(f[3]); } catch (NumberFormatException ignored) { }
                String count = VpnServers.FASTEST.equals(f[0]) ? "Any country" : f[2] + ("1".equals(f[2]) ? " server" : " servers");
                names.addView(text(c, off ? "Unavailable · no servers right now" : count + " · up to " + VpnServers.speed(best), 12, muted, false));
                item.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                item.addView(text(c, selected ? "✓" : "", 18, accent, true));
                item.setContentDescription(f[1] + (off ? ", unavailable" : "") + (selected ? ", selected" : ""));
                if (off) { item.setAlpha(0.45f); item.setBackground(null); item.setClickable(false); list.addView(item, new LinearLayout.LayoutParams(-1, -2)); continue; }
                item.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                    if (!premium()) return;
                    Bundle now = sState;
                    if (now != null) { now.putString("chosen", f[0]); }
                    pick(f[0]);
                    shown[0] = null;
                    for (Runnable l : sListeners) l.run();
                }});
                list.addView(item, new LinearLayout.LayoutParams(-1, -2));
            }
        }};
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { sListeners.addIfAbsent(bind); bind.run(); fetch("state", null, null); }
            public void onViewDetachedFromWindow(View v) { sListeners.remove(bind); }
        });
        bind.run();
        return root;
    }

    static void open(View anchor) {
        Context c = anchor.getContext();
        boolean dark = night(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setPadding(dp(c, 24), dp(c, 16), dp(c, 24), dp(c, 4));
        head.addView(text(c, "VPN", 19, dark ? 0xFFE3E3E3 : 0xFF1F1F1F, true));
        box.addView(head);
        box.addView(panel(anchor), new LinearLayout.LayoutParams(-1, -2));
        GradientDrawable shape = new GradientDrawable();
        int sheet = ChromeLayout.sheetColor(dark ? 0xFF1F1F1F : 0xFFFFFFFF, anchor);
        shape.setColor(sheet);
        shape.setCornerRadius(dp(c, 20));
        int fg = dark ? 0xFFE3E3E3 : 0xFF1F1F1F;
        int outline = ChromeLayout.sheetOutline(sheet, fg);
        if (outline != 0) shape.setStroke(Math.max(1, dp(c, 1)), outline);
        ChromeLayout.refineSheet(anchor, shape, Math.max(1, dp(c, 1)), fg);
        box.setBackground(shape);
        box.setClipToOutline(true);
        ChromeSheetMotion.Sheet pw = new ChromeSheetMotion.Sheet(box, Math.min(dp(c, 330), c.getResources().getDisplayMetrics().widthPixels - dp(c, 16)), ViewGroup.LayoutParams.WRAP_CONTENT, anchor, dp(c, 20));
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(c, 12));
        pw.showAsDropDown(anchor, 0, -dp(c, 4), Gravity.END);
        final LinearLayout card = box;
        pw.open(new Runnable() { public void run() { ChromeSheetMotion.enter(card, true, null); } });
    }

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

    static void refreshButtons() { for (View b : buttons()) ((Button) b).refresh(); }

    static final class Button extends View {
        private final WeakReference<View> toolbar;
        private WeakReference<ImageView> tint = new WeakReference<ImageView>(null);

        Button(Context c, View toolbar) {
            super(c);
            this.toolbar = new WeakReference<View>(toolbar);
            setClickable(true); setFocusable(true);
            setContentDescription(I18n.cs("VPN"));
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) {
                try { setBackground(c.getDrawable(tv.resourceId)); } catch (Throwable ignored) { }
            }
            setOnClickListener(new OnClickListener() { public void onClick(View v) {
                try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                try { open(Button.this); } catch (Throwable ignored) { }
            }});
            setVisibility(GONE);
            prime(c);
        }

        void refresh() {
            boolean show = ID.equals(ChromeShield.pinnedId(getContext())) && premium();
            if (!show) { if (getVisibility() != GONE) setVisibility(GONE); return; }
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
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
            boolean dark = night(getContext()), on = chromeOn();
            int color = on ? (dark ? 0xFF8AB4F8 : 0xFF1A73E8) : iconColor();
            if (connecting()) {
                int a = 110 + (int) (145 * Math.abs(Math.sin(android.os.SystemClock.uptimeMillis() / 320.0)));
                color = ((dark ? 0x008AB4F8 : 0x001A73E8) | (a << 24));
                postInvalidateDelayed(32);
            }
            globe(c, getWidth() / 2f, getHeight() / 2f, 21 * d, color, on, dark);
        }
    }

    private static View find(View root, String id) {
        try { if (root.getId() != View.NO_ID && id.equals(root.getResources().getResourceEntryName(root.getId()))) return root; } catch (Throwable ignored) { }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) { View f = find(g.getChildAt(i), id); if (f != null) return f; }
        }
        return null;
    }
}
