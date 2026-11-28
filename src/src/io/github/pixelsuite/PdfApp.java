package io.github.pixelsuite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PdfApp extends Activity {
    static final String TOOL = "tool";
    private static final int PICK_PDF = 41, PICK_PDFS = 42, PICK_DOCX = 43, PICK_PPTX = 44, IMPORT = 45;

    static final class Tool {
        final String id, name; final String icon; final String label;
        Tool(String id, String name, String icon, String label) { this.id = id; this.name = name; this.icon = icon; this.label = label; }
    }

    static final Tool IMG2PDF = new Tool("img2pdf", "Image to PDF", "ic_pdf_img2pdf", "PDF"),
            SCAN = new Tool("scan", "Smart Scan", "ic_pdf_scan", null),
            IMPORT_PDF = new Tool("import", "Import PDF", "ic_pdf_import", null),
            COMPRESS = new Tool("compress", "Compress", "ic_pdf_compress", null),
            PDF2JPG = new Tool("pdf2jpg", "PDF to JPG", "ic_pdf_pdf2jpg", "JPG"),
            MERGE = new Tool("merge", "Merge PDF", "ic_pdf_merge", null),
            DOCX2PDF = new Tool("docx2pdf", "Docx to PDF", "ic_pdf_docx2pdf", "DOC"),
            MORE = new Tool("more", "More", "ic_pdf_more", null),
            PPT2PDF = new Tool("ppt2pdf", "PPT to PDF", "ic_pdf_ppt2pdf", "PPT"),
            PDF2WORD = new Tool("pdf2word", "PDF to Word", "ic_pdf_pdf2word", "W"),
            PDF2PPT = new Tool("pdf2ppt", "PDF to PPT", "ic_pdf_pdf2ppt", "P"),
            IDCARD = new Tool("idcard", "Scan ID Card", "ic_pdf_idcard", null),
            PRINT = new Tool("print", "Print PDF", "ic_pdf_print", null),
            DOODLE = new Tool("doodle", "Doodle", "ic_pdf_doodle", null),
            ADDTEXT = new Tool("addtext", "Add Text", "ic_pdf_addtext", null),
            SIGN = new Tool("sign", "Signature", "ic_pdf_sign", null),
            LOCK = new Tool("lock", "Lock PDF", "ic_pdf_lock", null),
            UNLOCK = new Tool("unlock", "Unlock PDF", "ic_pdf_unlock", null);

    private static final Tool[] HOME = {IMG2PDF, SCAN, IMPORT_PDF, COMPRESS, PDF2JPG, MERGE, DOCX2PDF, MORE};

    static final class Item {
        File f; String name; long time, size; int pages = -1; boolean locked; boolean pdf;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread worker, thumbs;
    private Handler bg, tb;
    private float d;
    boolean dark;
    int bgc, card, ink, ink2, accent, onAccent, chip, line;
    private FrameLayout root, overlay;
    private LinearLayout column;
    private TextView title;
    private FrameLayout header;
    private LinearLayout searchBar;
    private EditText query;
    private ImageView searchBtn, settingsBtn;
    private ListView homeList, filesList;
    private Rows homeRows, filesRows;
    private TextView homeCount, filesCount, emptyHome;
    private View filesHint;
    private LinearLayout nav;
    private ImageView fab;
    private View toolsPage;
    private int tab, insetTop, insetBottom;
    private boolean searching, selecting;
    private final Set<File> picked = new HashSet<File>();
    private LinearLayout selectBar;
    private TextView selectTitle;
    private final ArrayList<Item> library = new ArrayList<Item>(), device = new ArrayList<Item>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) { @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); } };
    private Tool pending;
    private int sort;
    private boolean firstLoad = true;
    private final Set<String> fresh = new HashSet<String>();

    private SharedPreferences prefs() { return getSharedPreferences("pixel_pdf", 0); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!PdfHost.ok(this)) { PdfHost.locked(this); finish(); return; }
        scanShortcut();
        d = getResources().getDisplayMetrics().density;
        palette();
        worker = new HandlerThread("pdf"); worker.start(); bg = new Handler(worker.getLooper());
        thumbs = new HandlerThread("pdf-thumbs"); thumbs.start(); tb = new Handler(thumbs.getLooper());
        sort = prefs().getInt("sort", 0);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT); w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | (dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x10));
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgc));
        build();
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            insetTop = in.getSystemWindowInsetTop(); insetBottom = in.getStableInsetBottom();
            column.setPadding(0, in.getSystemWindowInsetTop(), 0, 0);
            ((FrameLayout.LayoutParams) nav.getLayoutParams()).height = dp(76) + in.getStableInsetBottom();
            nav.setPadding(0, 0, 0, in.getStableInsetBottom());
            ((FrameLayout.LayoutParams) fab.getLayoutParams()).bottomMargin = dp(40) + in.getStableInsetBottom();
            ((FrameLayout.LayoutParams) selectBar.getLayoutParams()).height = dp(76) + in.getStableInsetBottom();
            selectBar.setPadding(0, 0, 0, in.getStableInsetBottom());
            nav.requestLayout();
            return in.consumeSystemWindowInsets();
        });
        readIndex();
        bg.post(() -> { PdfKit.askFiles(this); trimThumbs(); main.post(this::reload); });
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handle(i); }

    private void handle(Intent i) {
        String t = i == null ? null : i.getStringExtra(TOOL);
        if (t == null) return;
        i.removeExtra(TOOL);
        for (Tool x : all()) if (x.id.equals(t)) { main.postDelayed(() -> run(x), 260); return; }
    }

    @Override protected void onResume() { super.onResume(); if (!PdfHost.ok(this)) { PdfHost.locked(this); finish(); return; } if (!firstLoad) reload(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        if (worker != null) worker.quitSafely();
        if (thumbs != null) thumbs.quitSafely();
    }

    private void palette() {
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean black = false;
        try { black = PdfHost.superDark(this); } catch (Throwable ignored) { }
        if (black) dark = true;
        bgc = black ? 0xff000000 : dark ? 0xff111318 : 0xfff3f5fb;
        card = black ? 0xff121316 : dark ? 0xff1d2025 : 0xffffffff;
        ink = dark ? 0xfff1f3f6 : 0xff15171b; ink2 = dark ? 0xffa9adb5 : 0xff6b7079;
        chip = dark ? 0x22ffffff : 0x10000000; line = dark ? 0x1fffffff : 0x14000000;
        accent = tone(dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xff8ab4f8 : 0xff3d5afe);
        onAccent = dark ? tone("system_accent1_800", 0xff0b1b33) : 0xffffffff;
    }

    private int tone(String name, int fallback) {
        try { int id = getResources().getIdentifier(name, "color", "android"); if (id != 0) return getResources().getColor(id) | 0xff000000; } catch (Throwable ignored) { }
        return fallback;
    }

    int dp(float v) { return Math.round(v * d); }

    static String t(String s) { return I18n.cs(s).toString(); }

    TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(I18n.cs(s)); v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    private void scanShortcut() {
        if (!PdfHost.APP.equals(getPackageName())) return;
        try {
            Object sm = getSystemService(Class.forName("android.content.pm.ShortcutManager"));
            if (sm == null) return;
            int icon = getResources().getIdentifier("ic_pdf_scan", "drawable", getPackageName());
            Intent go = new Intent(Intent.ACTION_VIEW).setClassName(getPackageName(), "io.github.pixelsuite.SearchScan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Class<?> kind = Class.forName("android.content.pm.ShortcutInfo$Builder");
            Object b = kind.getConstructor(Context.class, String.class).newInstance(this, "scan");
            kind.getMethod("setShortLabel", CharSequence.class).invoke(b, I18n.cs("Smart Scan"));
            kind.getMethod("setIntent", Intent.class).invoke(b, go);
            if (icon != 0) kind.getMethod("setIcon", android.graphics.drawable.Icon.class).invoke(b, android.graphics.drawable.Icon.createWithResource(this, icon));
            sm.getClass().getMethod("setDynamicShortcuts", java.util.List.class).invoke(sm, java.util.Collections.singletonList(kind.getMethod("build").invoke(b)));
        } catch (Throwable ignored) { }
    }

    void toast(String s) { PdfHost.toast(this, I18n.cs(s)); }

    static GradientDrawable round(int color, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g; }

    static GradientDrawable circle(int color) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(color); return g; }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void press(final View v) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1).scaleY(1).setDuration(380).setInterpolator(new SearchOverlay.Spring(0.42f)).start();
            return false;
        });
    }

    static void pop(View v) {
        v.setScaleX(0.82f); v.setScaleY(0.82f);
        v.animate().scaleX(1).scaleY(1).setDuration(420).setInterpolator(new SearchOverlay.Spring(0.45f)).start();
    }

    static Drawable drawable(Context c, String name) { return c.getDrawable(c.getResources().getIdentifier(name, "drawable", c.getPackageName())).mutate(); }

    ImageView res(String name, int color, int pad) {
        ImageView i = new ImageView(this);
        Drawable dr = drawable(this, name); dr.setTint(color);
        i.setImageDrawable(dr); i.setPadding(pad, pad, pad, pad);
        return i;
    }

    ImageView glyph(String kind, int color, int pad) {
        ImageView i = new ImageView(this);
        i.setImageDrawable(new SearchGlyph(kind, color)); i.setPadding(pad, pad, pad, pad);
        return i;
    }

    static final class Badge extends Drawable {
        final Drawable art; final String label; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Badge(Context c, String res, String label) { art = drawable(c, res); this.label = label; p.setColor(Color.WHITE); p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD)); p.setTextAlign(Paint.Align.CENTER); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); art.setBounds(b); art.draw(c);
            if (label == null) return;
            float w = b.width(); p.setTextSize(w * (label.length() > 2 ? 0.2f : 0.3f));
            float cx = b.left + w * 0.5f, cy = b.top + b.height() * 0.66f;
            c.drawText(label, cx, cy, p);
        }
        @Override public void setAlpha(int a) { art.setAlpha(a); p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    Drawable art(Tool tool) { return new Badge(this, tool.icon, tool.label); }

    private static Tool[] all() { return new Tool[]{IMG2PDF, SCAN, IMPORT_PDF, COMPRESS, PDF2JPG, MERGE, DOCX2PDF, PPT2PDF, PDF2WORD, PDF2PPT, IDCARD, PRINT, DOODLE, ADDTEXT, SIGN, LOCK, UNLOCK}; }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(bgc);
        column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        header = new FrameLayout(this);
        header.setPadding(dp(22), dp(18), dp(12), dp(10));
        title = text("Home", 34, ink, true);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(title, new FrameLayout.LayoutParams(-2, dp(56), Gravity.CENTER_VERTICAL | Gravity.START));
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout icons = new LinearLayout(this);
        searchBtn = glyph(SearchGlyph.SEARCH, ink, dp(11));
        searchBtn.setBackground(circle(0));
        searchBtn.setContentDescription(I18n.cs("Search"));
        searchBtn.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); openSearch(); });
        press(searchBtn);
        icons.addView(searchBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settingsBtn = res("ic_pdf_settings", ink, dp(11));
        settingsBtn.setContentDescription(I18n.cs("Settings"));
        settingsBtn.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); v.animate().rotationBy(90).setDuration(420).setInterpolator(new SearchOverlay.Spring(0.5f)).start(); openSettings(); });
        icons.addView(settingsBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(icons, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.END));

        searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(round(card, dp(28)));
        searchBar.setElevation(dp(2));
        searchBar.setPadding(dp(6), 0, dp(6), 0);
        ImageView back = glyph(SearchGlyph.BACK, ink, dp(12));
        back.setOnClickListener(v -> closeSearch());
        searchBar.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        query = new EditText(this);
        query.setBackground(null); query.setSingleLine(true); query.setTextColor(ink); query.setHintTextColor(ink2);
        query.setHint(I18n.cs("Search files")); query.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { filter(); }
        });
        searchBar.addView(query, new LinearLayout.LayoutParams(0, -1, 1));
        ImageView clear = glyph(SearchGlyph.CLEAR, ink2, dp(13));
        clear.setOnClickListener(v -> { if (query.length() > 0) { query.setText(""); pop(v); } else closeSearch(); });
        searchBar.addView(clear, new LinearLayout.LayoutParams(dp(46), dp(46)));
        searchBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams sbl = new FrameLayout.LayoutParams(-1, dp(56), Gravity.CENTER_VERTICAL);
        sbl.setMarginEnd(dp(10));
        header.addView(searchBar, sbl);
        column.addView(header, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout pages = new FrameLayout(this) {
            float x0, y0; boolean mine; final int slop = android.view.ViewConfiguration.get(PdfApp.this).getScaledTouchSlop();
            View current() { return tab == 0 ? homeList : filesList; }
            boolean rtl() { return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }
            @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); mine = false; return false; }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE && !mine && !searching && toolsPage == null) {
                    float dx = e.getX() - x0, dy = e.getY() - y0;
                    if (Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.6f) { mine = true; current().animate().cancel(); }
                }
                return mine;
            }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (!mine) return e.getActionMasked() == MotionEvent.ACTION_DOWN;
                float dx = e.getX() - x0; boolean toFiles = (rtl() ? dx > 0 : dx < 0);
                boolean can = toFiles ? tab == 0 : tab == 1;
                View v = current();
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float k = can ? 0.45f : 0.12f;
                    v.setTranslationX(dx * k); v.setAlpha(1f - Math.min(0.5f, Math.abs(dx) / getWidth()) * (can ? 1 : 0.2f));
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    mine = false;
                    if (can && e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(dx) > dp(64)) { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); show(tab == 0 ? 1 : 0); }
                    else v.animate().translationX(0).alpha(1).setDuration(420).setInterpolator(new SearchOverlay.Spring(0.6f)).start();
                }
                return true;
            }
        };
        column.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        homeRows = new Rows(true); filesRows = new Rows(false);
        homeList = list(homeRows); filesList = list(filesRows);
        homeList.addHeaderView(homeHeader(), null, false);
        filesList.addHeaderView(filesHeader(), null, false);
        homeList.addFooterView(footer(), null, false);
        filesList.addFooterView(footer(), null, false);
        homeList.setAdapter(homeRows); filesList.setAdapter(filesRows);
        pages.addView(homeList, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(filesList, new FrameLayout.LayoutParams(-1, -1));
        filesList.setVisibility(View.GONE);

        nav = new LinearLayout(this);
        nav.setBackgroundColor(card);
        nav.setElevation(dp(8));
        nav.addView(navItem(0, "ic_pdf_nav_home", "Home"), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(new View(this), new LinearLayout.LayoutParams(dp(96), -1));
        nav.addView(navItem(1, "ic_pdf_nav_files", "Files"), new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(nav, new FrameLayout.LayoutParams(-1, dp(76), Gravity.BOTTOM));

        fab = glyph(SearchGlyph.PLUS, onAccent, dp(20));
        fab.setBackground(circle(accent));
        fab.setElevation(dp(12));
        fab.setContentDescription(I18n.cs("Smart Scan"));
        press(fab);
        fab.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); v.animate().rotationBy(90).setDuration(360).setInterpolator(new SearchOverlay.Spring(0.5f)).start(); main.postDelayed(() -> run(SCAN), 120); });
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.bottomMargin = dp(40);
        root.addView(fab, fl);

        selectBar = new LinearLayout(this);
        selectBar.setBackgroundColor(card); selectBar.setElevation(dp(14)); selectBar.setGravity(Gravity.CENTER_VERTICAL);
        selectBar.addView(barButton(SearchGlyph.SHARE, "Share", this::shareSelected), new LinearLayout.LayoutParams(0, -1, 1));
        selectBar.addView(barButton(null, "Merge PDF", this::mergeSelected), new LinearLayout.LayoutParams(0, -1, 1));
        selectBar.addView(barButton("bin", "Delete", this::deleteSelected), new LinearLayout.LayoutParams(0, -1, 1));
        selectBar.setVisibility(View.GONE);
        root.addView(selectBar, new FrameLayout.LayoutParams(-1, dp(76), Gravity.BOTTOM));

        overlay = new FrameLayout(this);
        overlay.setTranslationZ(dp(48));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        paintNav();
    }

    private View barButton(String glyph, String label, Runnable run) {
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setGravity(Gravity.CENTER);
        ImageView i = glyph == null ? new ImageView(this) : glyph(glyph, "bin".equals(glyph) ? 0xffe5484d : ink, dp(2));
        if (glyph == null) { Drawable m = drawable(this, "ic_pdf_merge"); i.setImageDrawable(m); }
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(26), dp(26)); il.gravity = Gravity.CENTER_HORIZONTAL;
        b.addView(i, il);
        TextView tv = text(label, 12.5f, "bin".equals(glyph) ? 0xffe5484d : ink, true);
        tv.setGravity(Gravity.CENTER); tv.setSingleLine(true);
        tv.setPadding(0, dp(4), 0, 0);
        b.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        press(b);
        b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        return b;
    }

    private final View[] navIcons = new View[2];
    private final TextView[] navLabels = new TextView[2];

    private View navItem(final int which, String icon, String label) {
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setGravity(Gravity.CENTER);
        ImageView i = res(icon, ink2, 0);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(28), dp(28)); il.gravity = Gravity.CENTER_HORIZONTAL;
        b.addView(i, il);
        TextView tv = text(label, 14, ink2, true);
        tv.setGravity(Gravity.CENTER); tv.setSingleLine(true);
        tv.setPadding(0, dp(4), 0, 0);
        b.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        navIcons[which] = i; navLabels[which] = tv;
        b.setOnClickListener(v -> { if (tab != which) { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); show(which); } else { (which == 0 ? homeList : filesList).smoothScrollToPosition(0); } });
        return b;
    }

    private void paintNav() {
        for (int i = 0; i < 2; i++) {
            int c = i == tab ? accent : ink2;
            ((ImageView) navIcons[i]).getDrawable().setTint(c);
            navLabels[i].setTextColor(c);
        }
    }

    private void show(int which) {
        if (searching) closeSearch();
        if (selecting) endSelect();
        final int from = tab;
        tab = which;
        paintNav();
        pop(navIcons[which]);
        final View in = which == 0 ? homeList : filesList, out = which == 0 ? filesList : homeList;
        float dir = which > from ? 1 : -1;
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) dir = -dir;
        out.animate().alpha(0).translationX(-dir * dp(40)).setDuration(160).withEndAction(() -> { out.setVisibility(View.GONE); out.setTranslationX(0); out.setAlpha(1); }).start();
        in.setVisibility(View.VISIBLE); in.setAlpha(0); in.setTranslationX(dir * dp(48));
        in.animate().alpha(1).translationX(0).setDuration(420).setInterpolator(new SearchOverlay.Spring(0.82f)).start();
        swapTitle(which == 0 ? "Home" : "Files");
        if (which == 1 && device.isEmpty()) loadDevice();
    }

    private CharSequence wantTitle;

    private void swapTitle(final String s) {
        wantTitle = I18n.cs(s);
        title.animate().cancel();
        title.animate().alpha(0).translationY(-dp(8)).setDuration(110).withEndAction(() -> {
            title.setText(wantTitle); title.setTranslationY(dp(10));
            title.animate().alpha(1).translationY(0).setDuration(320).setInterpolator(new SearchOverlay.Spring(0.6f)).start();
        }).start();
    }

    private ListView list(BaseAdapter a) {
        ListView l = new ListView(this);
        l.setDivider(null); l.setSelector(new android.graphics.drawable.ColorDrawable(0));
        l.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        l.setVerticalScrollBarEnabled(false);
        l.setClipToPadding(false);
        return l;
    }

    private View footer() {
        View f = new View(this);
        f.setBackgroundColor(card);
        f.setLayoutParams(new ListView.LayoutParams(-1, getResources().getDisplayMetrics().heightPixels / 2 + dp(120)));
        return f;
    }

    private View sheetTop(boolean home) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable(); g.setColor(card);
        g.setCornerRadii(new float[]{dp(30), dp(30), dp(30), dp(30), 0, 0, 0, 0});
        top.setBackground(g);
        top.setPadding(dp(22), dp(20), dp(10), dp(6));
        LinearLayout label = new LinearLayout(this); label.setGravity(Gravity.BOTTOM);
        TextView all = text(home ? "All" : "All PDFs", 24, ink, true);
        all.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        label.addView(all);
        TextView n = text("", 16, ink2, false); n.setPadding(dp(8), 0, 0, dp(2));
        label.addView(n);
        if (home) homeCount = n; else filesCount = n;
        top.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView sel = res("ic_pdf_select", ink, dp(11));
        sel.setContentDescription(I18n.cs("Select"));
        press(sel);
        sel.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); if (selecting) endSelect(); else startSelect(null); });
        top.addView(sel, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageView so = res("ic_pdf_sort", ink, dp(11));
        so.setContentDescription(I18n.cs("Sort"));
        press(so);
        so.setOnClickListener(this::sortMenu);
        top.addView(so, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return top;
    }

    private View homeHeader() {
        LinearLayout h = new LinearLayout(this); h.setOrientation(LinearLayout.VERTICAL);
        h.setLayoutParams(new ListView.LayoutParams(-1, -2));
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(dp(6), r == 0 ? dp(6) : dp(10), dp(6), dp(4));
            for (int c = 0; c < 4; c++) row.addView(tile(HOME[r * 4 + c], r * 4 + c), new LinearLayout.LayoutParams(0, -2, 1));
            h.addView(row);
        }
        View gap = new View(this); h.addView(gap, new LinearLayout.LayoutParams(-1, dp(18)));
        h.addView(sheetTop(true));
        emptyHome = text("Scanned and made PDFs show here. Tap + to scan your first one.", 15, ink2, false);
        emptyHome.setGravity(Gravity.CENTER); emptyHome.setBackgroundColor(card);
        emptyHome.setPadding(dp(40), dp(36), dp(40), dp(10));
        emptyHome.setVisibility(View.GONE);
        h.addView(emptyHome, new LinearLayout.LayoutParams(-1, -2));
        filesHint = hint();
        h.addView(filesHint);
        return h;
    }

    private View hint() {
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setBackgroundColor(card);
        b.setPadding(dp(22), dp(10), dp(22), dp(10));
        LinearLayout in = new LinearLayout(this); in.setOrientation(LinearLayout.VERTICAL);
        in.setBackground(round(chip, dp(20))); in.setPadding(dp(18), dp(14), dp(18), dp(14));
        in.addView(text("Allow access to your files to see and save PDFs.", 15, ink, false));
        TextView go = text("Allow", 15, accent, true); go.setPadding(0, dp(10), 0, 0);
        in.addView(go);
        in.setOnClickListener(v -> {
            try { startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION").setData(Uri.parse("package:" + getPackageName()))); }
            catch (Throwable t) { try { startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")); } catch (Throwable ignored) { } }
        });
        b.addView(in);
        b.setVisibility(View.GONE);
        return b;
    }

    private View filesHeader() {
        LinearLayout h = new LinearLayout(this); h.setOrientation(LinearLayout.VERTICAL);
        h.setLayoutParams(new ListView.LayoutParams(-1, -2));
        View gap = new View(this); h.addView(gap, new LinearLayout.LayoutParams(-1, dp(6)));
        h.addView(sheetTop(false));
        return h;
    }

    private View tile(final Tool tool, int index) {
        LinearLayout t = new LinearLayout(this); t.setOrientation(LinearLayout.VERTICAL); t.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(circle(card));
        ImageView i = new ImageView(this); i.setImageDrawable(art(tool));
        disc.addView(i, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        t.addView(disc, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView n = text(tool.name, 14, ink, true);
        n.setGravity(Gravity.CENTER); n.setMaxLines(2); n.setPadding(dp(2), dp(8), dp(2), 0);
        t.addView(n, new LinearLayout.LayoutParams(-1, -2));
        press(t);
        t.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run(tool); });
        t.setAlpha(0); t.setTranslationY(dp(16));
        t.animate().alpha(1).translationY(0).setStartDelay(40 + index * 32).setDuration(460).setInterpolator(new SearchOverlay.Spring(0.6f)).start();
        return t;
    }

    void openSettings() {
        try { PdfHost.settings(this); } catch (Throwable ignored) { }
    }

    private void openSearch() {
        if (searching) return;
        searching = true;
        searchBar.setVisibility(View.VISIBLE);
        searchBar.setPivotX(searchBar.getWidth() > 0 ? searchBar.getWidth() - dp(70) : dp(300)); searchBar.setPivotY(dp(28));
        SearchOverlay.bloomIn(searchBar);
        title.animate().alpha(0).translationX(-dp(20)).setDuration(140).start();
        searchBtn.animate().alpha(0).scaleX(0.6f).scaleY(0.6f).setDuration(140).start();
        settingsBtn.animate().alpha(0).scaleX(0.6f).scaleY(0.6f).setDuration(140).start();
        query.requestFocus();
        main.postDelayed(() -> { try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(query, InputMethodManager.SHOW_IMPLICIT); } catch (Throwable ignored) { } }, 90);
    }

    private void closeSearch() {
        if (!searching) return;
        searching = false;
        try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0); } catch (Throwable ignored) { }
        query.clearFocus();
        if (query.length() > 0) query.setText("");
        SearchOverlay.bloomOut(searchBar, () -> searchBar.setVisibility(View.GONE));
        title.animate().alpha(1).translationX(0).setDuration(360).setInterpolator(new SearchOverlay.Spring(0.6f)).start();
        for (View v : new View[]{searchBtn, settingsBtn}) v.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(60).setDuration(380).setInterpolator(new SearchOverlay.Spring(0.5f)).start();
    }

    private void filter() {
        homeRows.set(filtered(library)); filesRows.set(filtered(device));
    }

    private List<Item> filtered(List<Item> src) {
        String q = query == null ? "" : query.getText().toString().trim().toLowerCase(Locale.getDefault());
        ArrayList<Item> out = new ArrayList<Item>();
        for (Item i : src) if (q.isEmpty() || i.name.toLowerCase(Locale.getDefault()).contains(q)) out.add(i);
        Comparator<Item> c;
        if (sort == 1) c = (a, b) -> Long.compare(a.time, b.time);
        else if (sort == 2) c = (a, b) -> a.name.compareToIgnoreCase(b.name);
        else if (sort == 3) c = (a, b) -> Long.compare(b.size, a.size);
        else c = (a, b) -> Long.compare(b.time, a.time);
        Collections.sort(out, c);
        return out;
    }

    private void sortMenu(View anchor) {
        anchor.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        String[] names = {"Newest first", "Oldest first", "Name", "Largest first"};
        Menu m = new Menu(anchor);
        for (int i = 0; i < names.length; i++) { final int k = i; m.add(null, names[i], sort == k, () -> { sort = k; prefs().edit().putInt("sort", k).apply(); filter(); }); }
        m.show();
    }

    void reload() {
        firstLoad = false;
        bg.post(() -> {
            final boolean access = PdfKit.canFiles();
            final ArrayList<Item> lib = new ArrayList<Item>();
            File[] fs = PdfKit.library().listFiles();
            if (fs != null) for (File f : fs) {
                if (!f.isFile()) continue;
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!(n.endsWith(".pdf") || n.endsWith(".docx") || n.endsWith(".pptx"))) continue;
                lib.add(item(f));
            }
            main.post(() -> {
                Set<String> before = new HashSet<String>();
                for (Item i : library) before.add(i.f.getPath());
                if (!before.isEmpty()) for (Item i : lib) if (!before.contains(i.f.getPath())) fresh.add(i.f.getPath());
                boolean same = same(library, lib);
                keep(library, lib);
                library.clear(); library.addAll(lib);
                homeCount.setText("(" + lib.size() + ")");
                emptyHome.setVisibility(lib.isEmpty() && access ? View.VISIBLE : View.GONE);
                filesHint.setVisibility(access ? View.GONE : View.VISIBLE);
                if (!same) { filter(); saveIndex(); }
                if (tab == 1 || !device.isEmpty()) loadDevice();
            });
        });
    }

    private static Item item(File f) {
        Item i = new Item(); i.f = f; i.name = f.getName(); i.time = f.lastModified(); i.size = f.length();
        i.pdf = f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");
        if (i.pdf && i.name.length() > 4) i.name = i.name.substring(0, i.name.length() - 4);
        return i;
    }

    private void loadDevice() {
        bg.post(() -> {
            final ArrayList<Item> out = new ArrayList<Item>();
            HashSet<String> seen = new HashSet<String>();
            Cursor c = null;
            try {
                c = getContentResolver().query(Uri.parse("content://media/external/file"), new String[]{"_data"}, "mime_type=? OR _data LIKE ?", new String[]{"application/pdf", "%.pdf"}, "date_modified DESC");
                while (c != null && c.moveToNext()) {
                    String p = c.getString(0);
                    if (p == null || !seen.add(p)) continue;
                    File f = new File(p);
                    if (f.isFile() && !p.contains("/Android/data/")) out.add(item(f));
                }
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "pdf: files", t); }
            finally { if (c != null) c.close(); }
            File[] lib = PdfKit.library().listFiles();
            if (lib != null) for (File f : lib) if (f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf") && seen.add(f.getPath())) out.add(item(f));
            main.post(() -> {
                boolean same = same(device, out);
                keep(device, out);
                device.clear(); device.addAll(out); filesCount.setText("(" + out.size() + ")");
                if (!same) { filter(); saveIndex(); }
            });
        });
    }

    private static boolean same(List<Item> a, List<Item> b) {
        if (a.size() != b.size()) return false;
        for (int n = 0; n < a.size(); n++) {
            Item x = a.get(n), y = b.get(n);
            if (x.time != y.time || x.size != y.size || !x.f.getPath().equals(y.f.getPath())) return false;
        }
        return true;
    }

    private static void keep(List<Item> old, List<Item> now) {
        if (old.isEmpty()) return;
        HashMap<String, Item> by = new HashMap<String, Item>();
        for (Item i : old) by.put(i.f.getPath(), i);
        for (Item i : now) { Item o = by.get(i.f.getPath()); if (o != null && o.time == i.time && o.size == i.size) { i.pages = o.pages; i.locked = o.locked; } }
    }

    private File indexFile() { return new File(getFilesDir(), "pdf-index"); }

    private void readIndex() {
        java.io.BufferedReader r = null;
        try {
            File f = indexFile();
            if (!f.isFile()) return;
            r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] t = line.split("\t", -1);
                if (t.length < 6) continue;
                Item i = new Item();
                i.f = new File(t[1]); i.name = i.f.getName(); i.time = Long.parseLong(t[2]); i.size = Long.parseLong(t[3]);
                i.pages = Integer.parseInt(t[4]); i.locked = "1".equals(t[5]);
                i.pdf = i.name.toLowerCase(Locale.ROOT).endsWith(".pdf");
                if (i.pdf && i.name.length() > 4) i.name = i.name.substring(0, i.name.length() - 4);
                ("L".equals(t[0]) ? library : device).add(i);
            }
            homeCount.setText("(" + library.size() + ")");
            filesCount.setText("(" + device.size() + ")");
            filter();
        } catch (Throwable t) { library.clear(); device.clear(); }
        finally { if (r != null) try { r.close(); } catch (Throwable ignored) { } }
    }

    private final Runnable writeIndex = () -> {
        final ArrayList<Item> lib = new ArrayList<Item>(library), dev = new ArrayList<Item>(device);
        bg.post(() -> {
            java.io.Writer w = null;
            try {
                File tmp = new File(getFilesDir(), "pdf-index.tmp");
                w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp), "UTF-8"));
                for (int pass = 0; pass < 2; pass++) for (Item i : pass == 0 ? lib : dev) {
                    String path = i.f.getPath();
                    if (path.indexOf('\t') >= 0 || path.indexOf('\n') >= 0) continue;
                    w.write((pass == 0 ? "L" : "D") + "\t" + path + "\t" + i.time + "\t" + i.size + "\t" + i.pages + "\t" + (i.locked ? "1" : "0") + "\n");
                }
                w.close(); w = null;
                tmp.renameTo(indexFile());
            } catch (Throwable ignored) { }
            finally { if (w != null) try { w.close(); } catch (Throwable ignored) { } }
        });
    };

    private void saveIndex() { main.removeCallbacks(writeIndex); main.postDelayed(writeIndex, 1500); }

    private File thumbFile(Item i) {
        File dir = new File(getCacheDir(), "pdf-thumbs");
        return new File(dir, Long.toHexString(((long) i.f.getPath().hashCode() << 32) ^ i.time ^ (i.size * 31)) + ".jpg");
    }

    private void trimThumbs() {
        try {
            File[] all = new File(getCacheDir(), "pdf-thumbs").listFiles();
            if (all == null || all.length < 3000) return;
            java.util.Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int n = 0; n < all.length - 2000; n++) all[n].delete();
        } catch (Throwable ignored) { }
    }

    private final HashMap<String, ArrayList<Object[]>> waiting = new HashMap<String, ArrayList<Object[]>>();
    private final Set<String> blank = new HashSet<String>();

    private void thumb(final Item i, final ImageView into, final TextView pages) {
        final String key = i.f.getPath() + ":" + i.time;
        Bitmap b = cache.get(key);
        into.setTag(key);
        into.animate().cancel(); into.setAlpha(1f);
        if (b != null) { into.setImageBitmap(b); return; }
        into.setImageDrawable(null);
        if (!i.pdf) {
            Tool t = i.f.getName().toLowerCase(Locale.ROOT).endsWith(".docx") ? PDF2WORD : PDF2PPT;
            into.setImageDrawable(new InsetArt(art(t)));
            return;
        }
        if (i.locked && i.pages >= 0) { into.setImageDrawable(new InsetArt(art(LOCK))); return; }
        if (blank.contains(key)) return;
        ArrayList<Object[]> list = waiting.get(key);
        if (list != null) { list.add(new Object[]{into, pages}); return; }
        list = new ArrayList<Object[]>();
        list.add(new Object[]{into, pages});
        waiting.put(key, list);
        final ArrayList<Object[]> mine = list;
        tb.post(() -> {
            Bitmap got = null;
            final File disk = thumbFile(i);
            boolean wanted = false;
            try { for (Object[] w : new ArrayList<Object[]>(mine)) if (key.equals(((ImageView) w[0]).getTag())) { wanted = true; break; } } catch (Throwable t) { wanted = true; }
            if (!wanted) { main.post(() -> waiting.remove(key)); return; }
            if (disk.isFile()) { try { got = android.graphics.BitmapFactory.decodeFile(disk.getPath()); } catch (Throwable ignored) { } }
            boolean known = i.pages >= 0;
            final boolean locked = got != null && known ? i.locked : PdfKit.locked(i.f);
            final int n = got != null && known ? i.pages : locked ? 0 : PdfKit.count(i.f);
            if (got == null && !locked) {
                got = PdfKit.thumb(i.f, dp(92));
                if (got != null) {
                    java.io.FileOutputStream o = null;
                    try { disk.getParentFile().mkdirs(); o = new java.io.FileOutputStream(disk); got.compress(Bitmap.CompressFormat.JPEG, 88, o); }
                    catch (Throwable ignored) { }
                    finally { if (o != null) try { o.close(); } catch (Throwable ignored) { } }
                }
            }
            final Bitmap made = got;
            main.post(() -> {
                ArrayList<Object[]> all = waiting.remove(key);
                boolean news = i.pages != n || i.locked != locked;
                i.locked = locked; i.pages = n;
                if (news) saveIndex();
                if (made != null) cache.put(key, made); else if (!locked) blank.add(key);
                if (all == null) return;
                for (Object[] w : all) {
                    ImageView view = (ImageView) w[0];
                    if (!key.equals(view.getTag())) continue;
                    if (made != null) { view.setImageBitmap(made); view.setAlpha(0f); view.animate().alpha(1).setDuration(180).start(); }
                    else if (locked) view.setImageDrawable(new InsetArt(art(LOCK)));
                    if (w[1] != null) paintPages(i, (TextView) w[1]);
                }
            });
        });
    }

    private void paintPages(Item i, TextView pages) {
        if (!i.pdf) { pages.setText(i.f.getName().toLowerCase(Locale.ROOT).endsWith(".docx") ? "DOCX" : "PPTX"); pages.setVisibility(View.VISIBLE); }
        else if (i.locked) { pages.setText("🔒"); pages.setVisibility(View.VISIBLE); }
        else if (i.pages > 0) { pages.setText(String.valueOf(i.pages)); pages.setVisibility(View.VISIBLE); }
        else pages.setVisibility(View.GONE);
    }

    final class InsetArt extends Drawable {
        final Drawable a; InsetArt(Drawable a) { this.a = a; }
        @Override public void draw(Canvas c) { Rect b = getBounds(); int s = Math.min(b.width(), b.height()) / 2; a.setBounds(b.centerX() - s / 2, b.centerY() - s / 2, b.centerX() + s / 2, b.centerY() + s / 2); a.draw(c); }
        @Override public void setAlpha(int x) { } @Override public void setColorFilter(ColorFilter f) { } @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private final class Rows extends BaseAdapter {
        final boolean home; List<Item> items = new ArrayList<Item>(); final Set<String> shown = new HashSet<String>();
        Rows(boolean home) { this.home = home; }
        void set(List<Item> l) { items = l; notifyDataSetChanged(); }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View v, ViewGroup parent) {
            Holder h = v == null ? null : (Holder) v.getTag();
            if (h == null) { h = new Holder(); v = h.make(); }
            final Item i = items.get(p);
            h.bind(i);
            String key = (home ? "h" : "f") + i.f.getPath();
            if (shown.add(key) && p < 12) {
                v.setAlpha(0); v.setTranslationY(dp(18));
                v.animate().alpha(1).translationY(0).setStartDelay(Math.min(p, 8) * 36L).setDuration(460).setInterpolator(new SearchOverlay.Spring(0.7f)).start();
            } else if (fresh.remove(i.f.getPath())) {
                v.setScaleX(0.9f); v.setScaleY(0.9f); v.setAlpha(0);
                v.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(120).setDuration(520).setInterpolator(new SearchOverlay.Spring(0.5f)).start();
            } else { v.animate().cancel(); v.setAlpha(1); v.setTranslationY(0); v.setScaleX(1); v.setScaleY(1); }
            return v;
        }
    }

    private final class Holder {
        LinearLayout row; ImageView pic, check; TextView name, pages, meta; Item item;
        View make() {
            row = new LinearLayout(PdfApp.this);
            row.setBackgroundColor(card);
            row.setPadding(dp(22), dp(10), dp(10), dp(10));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTag(this);
            FrameLayout frame = new FrameLayout(PdfApp.this);
            pic = new ImageView(PdfApp.this);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pic.setBackground(round(chip, dp(16)));
            pic.setClipToOutline(true);
            frame.addView(pic, new FrameLayout.LayoutParams(-1, -1));
            TextView tag = new TextView(PdfApp.this);
            tag.setText("PDF"); tag.setTextColor(Color.WHITE); tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); tag.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tag.setPadding(dp(7), dp(2), dp(7), dp(2));
            GradientDrawable tg = new GradientDrawable(); tg.setColor(0x80000000); tg.setCornerRadii(new float[]{0, 0, dp(16), dp(16), 0, 0, dp(10), dp(10)});
            tag.setBackground(tg);
            frame.addView(tag, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
            check = new ImageView(PdfApp.this);
            check.setImageDrawable(new SearchGlyph(SearchGlyph.TICK, onAccent)); check.setPadding(dp(5), dp(5), dp(5), dp(5));
            check.setBackground(circle(accent));
            check.setVisibility(View.GONE);
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.START);
            cl.topMargin = cl.leftMargin = dp(6);
            frame.addView(check, cl);
            row.addView(frame, new LinearLayout.LayoutParams(dp(84), dp(84)));
            LinearLayout mid = new LinearLayout(PdfApp.this); mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(dp(16), 0, 0, 0);
            name = text("", 16.5f, ink, true); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            mid.addView(name);
            LinearLayout info = new LinearLayout(PdfApp.this); info.setGravity(Gravity.CENTER_VERTICAL); info.setPadding(0, dp(8), 0, 0);
            pages = text("", 12, Color.WHITE, true); pages.setBackground(round(0xff9aa0a8, dp(6))); pages.setPadding(dp(7), dp(1), dp(7), dp(1));
            info.addView(pages);
            meta = text("", 13.5f, ink2, false); meta.setPadding(dp(10), 0, 0, 0); meta.setSingleLine(true);
            info.addView(meta);
            mid.addView(info);
            LinearLayout acts = new LinearLayout(PdfApp.this); acts.setGravity(Gravity.END); acts.setPadding(0, dp(2), 0, 0);
            ImageView share = glyph(SearchGlyph.SHARE, ink, dp(11));
            share.setContentDescription(I18n.cs("Share"));
            press(share);
            share.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); share(item.f); });
            acts.addView(share, new LinearLayout.LayoutParams(dp(42), dp(42)));
            ImageView more = glyph(SearchGlyph.MORE, ink, dp(11));
            more.setContentDescription(I18n.cs("More"));
            press(more);
            more.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); rowMenu(item, x); });
            acts.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));
            mid.addView(acts, new LinearLayout.LayoutParams(-1, -2));
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(x -> { if (selecting) toggle(item, this); else open(item.f); });
            row.setOnLongClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); if (!selecting) startSelect(item); else toggle(item, this); return true; });
            ((View) frame).setTag(tag);
            return row;
        }
        void bind(Item i) {
            item = i;
            name.setText(i.name);
            meta.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(i.time)) + "     " + PdfKit.size(i.size));
            paintPages(i, pages);
            thumb(i, pic, pages);
            boolean on = selecting && picked.contains(i.f);
            check.setVisibility(selecting ? View.VISIBLE : View.GONE);
            check.setBackground(on ? circle(accent) : ring());
            check.setImageDrawable(on ? new SearchGlyph(SearchGlyph.TICK, onAccent) : null);
            row.setBackgroundColor(on ? blend(card, accent, 0.12f) : card);
        }
    }

    private GradientDrawable ring() { GradientDrawable g = circle(0x66000000); g.setStroke(dp(2), Color.WHITE); return g; }

    static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    private void startSelect(Item first) {
        if (searching) closeSearch();
        selecting = true; picked.clear();
        if (first != null) picked.add(first.f);
        selectBar.setVisibility(View.VISIBLE);
        selectBar.setTranslationY(dp(90));
        selectBar.animate().translationY(0).setDuration(460).setInterpolator(new SearchOverlay.Spring(0.7f)).start();
        fab.animate().scaleX(0).scaleY(0).setDuration(160).start();
        swapTitle(selectTitle());
        homeRows.notifyDataSetChanged(); filesRows.notifyDataSetChanged();
    }

    private void endSelect() {
        if (!selecting) return;
        selecting = false; picked.clear();
        selectBar.animate().translationY(dp(90)).setDuration(200).withEndAction(() -> selectBar.setVisibility(View.GONE)).start();
        fab.animate().scaleX(1).scaleY(1).setStartDelay(80).setDuration(460).setInterpolator(new SearchOverlay.Spring(0.5f)).start();
        swapTitle(tab == 0 ? "Home" : "Files");
        homeRows.notifyDataSetChanged(); filesRows.notifyDataSetChanged();
    }

    private void toggle(Item i, Holder h) {
        if (!picked.remove(i.f)) picked.add(i.f);
        h.bind(i); pop(h.check);
        h.row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        updateSelect();
    }

    private String selectTitle() { return picked.isEmpty() ? t("Select") : picked.size() + " " + t("selected"); }

    private void updateSelect() {
        wantTitle = selectTitle();
        if (title.getAlpha() > 0.99f) title.setText(wantTitle);
    }

    private List<File> chosen() {
        ArrayList<File> out = new ArrayList<File>();
        for (Item i : (tab == 0 ? homeRows : filesRows).items) if (picked.contains(i.f)) out.add(i.f);
        return out;
    }

    private void shareSelected() {
        List<File> fs = chosen();
        if (fs.isEmpty()) { toast("Pick some files first"); return; }
        share(fs.toArray(new File[0]));
    }

    private void mergeSelected() {
        final List<File> fs = new ArrayList<File>();
        for (File f : chosen()) if (f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) fs.add(f);
        if (fs.size() < 2) { toast("Pick two or more PDFs to merge"); return; }
        endSelect();
        work(MERGE, p -> PdfKit.merge(fs, p));
    }

    private void deleteSelected() {
        final List<File> fs = chosen();
        if (fs.isEmpty()) { toast("Pick some files first"); return; }
        Card c = new Card(fs.size() == 1 ? "Delete this file?" : "Delete these files?");
        c.note("They can't be brought back.");
        c.button("Cancel", false, null);
        c.button("Delete", true, () -> {
            for (File f : fs) f.delete();
            PdfKit.scan(this, fs.toArray(new File[0]));
            endSelect(); reload();
        }).setBackground(round(0xffe5484d, dp(22)));
        c.show();
    }

    void share(File... fs) {
        try {
            ArrayList<Uri> us = new ArrayList<Uri>();
            for (File f : fs) us.add(PdfHost.uri(f.getAbsolutePath()));
            Intent i;
            if (us.size() == 1) i = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, us.get(0));
            else i = new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, us);
            i.setType(fs.length == 1 ? mime(fs[0]) : "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ClipData clip = ClipData.newRawUri("", us.get(0));
            for (int k = 1; k < us.size(); k++) clip.addItem(new ClipData.Item(us.get(k)));
            i.setClipData(clip);
            startActivity(Intent.createChooser(i, I18n.cs("Share")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    static String mime(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (n.endsWith(".jpg")) return "image/jpeg";
        return "*/*";
    }

    void open(File f) {
        if (f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) { startActivity(new Intent(this, PdfEdit.class).putExtra(PdfEdit.FILE, f.getPath())); return; }
        try {
            Uri u = PdfHost.uri(f.getAbsolutePath());
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Throwable t) { toast("No app can open this file"); }
    }

    private void rowMenu(final Item i, View anchor) {
        Menu m = new Menu(anchor);
        m.add(SearchGlyph.OPEN, "Open", false, () -> open(i.f));
        m.add(SearchGlyph.PAGE, "Rename", false, () -> rename(i.f));
        if (i.pdf && !i.locked) {
            m.add(COMPRESS, () -> compress(i.f));
            m.add(PDF2JPG, () -> work(PDF2JPG, p -> PdfKit.toJpg(i.f, p)));
            m.add(PDF2WORD, () -> work(PDF2WORD, p -> PdfKit.toDocx(i.f, p)));
            m.add(SIGN, () -> edit(i.f, SIGN));
            m.add(LOCK, () -> lock(i.f));
            m.add(PRINT, () -> PdfKit.print(this, i.f));
        }
        if (i.pdf && i.locked) m.add(UNLOCK, () -> unlock(i.f));
        m.add("bin", "Delete", false, () -> {
            Card c = new Card("Delete this file?");
            c.note(i.f.getName());
            c.button("Cancel", false, null);
            c.button("Delete", true, () -> { i.f.delete(); PdfKit.scan(this, i.f); reload(); }).setBackground(round(0xffe5484d, dp(22)));
            c.show();
        });
        m.show();
    }

    private void rename(final File f) {
        Card c = new Card("Rename");
        String n = f.getName(); int dot = n.lastIndexOf('.');
        final String ext = dot > 0 ? n.substring(dot) : "";
        final EditText e = c.field(dot > 0 ? n.substring(0, dot) : n, false);
        c.button("Cancel", false, null);
        c.button("Save", true, () -> {
            String base = PdfKit.clean(e.getText().toString());
            File to = new File(f.getParentFile(), base + ext);
            if (to.exists() && !to.equals(f)) { toast("A file with that name already exists"); return; }
            if (f.renameTo(to)) { PdfKit.scan(this, f, to); reload(); } else toast("Couldn't rename it");
        });
        c.show();
        e.selectAll();
    }

    void edit(File f, Tool mode) {
        startActivity(new Intent(this, PdfEdit.class).putExtra(PdfEdit.FILE, f.getPath()).putExtra(PdfEdit.MODE, mode.id));
    }

    private void compress(final File f) {
        final Card c = new Card("Compress");
        c.note(f.getName() + "  •  " + PdfKit.size(f.length()));
        final String[] names = {"Light", "Medium", "Strong"}, notes = {"Best quality", "Good balance", "Smallest file"};
        for (int k = 0; k < 3; k++) {
            final int level = k;
            c.option(names[k], notes[k], () -> { c.dismiss(); work(COMPRESS, p -> PdfKit.compress(f, level, p)); });
        }
        c.button("Cancel", false, null);
        c.show();
    }

    private void lock(final File f) {
        final Card c = new Card("Lock PDF");
        c.note("Anyone opening it will need this password.");
        final EditText a = c.field("", true), b = c.field("", true);
        a.setHint(I18n.cs("Password")); b.setHint(I18n.cs("Repeat password"));
        c.button("Cancel", false, null);
        c.button("Lock", true, () -> {
            String p = a.getText().toString();
            if (p.length() < 1) { toast("Type a password"); throw new Stay(); }
            if (!p.equals(b.getText().toString())) { toast("The passwords don't match"); throw new Stay(); }
            work(LOCK, x -> PdfKit.lock(f, p, x));
        });
        c.show();
    }

    private void unlock(final File f) {
        if (!PdfKit.passwords()) { toast("Unlocking needs a newer Android version"); return; }
        final Card c = new Card("Unlock PDF");
        c.note(f.getName());
        final EditText a = c.field("", true);
        a.setHint(I18n.cs("Password"));
        c.button("Cancel", false, null);
        c.button("Unlock", true, () -> work(UNLOCK, x -> PdfKit.unlock(f, a.getText().toString(), x)));
        c.show();
    }

    interface Job { File run(PdfKit.Progress p) throws Exception; }

    void work(final Tool tool, final Job job) {
        final Card c = new Card(tool.name);
        c.cancelable = false;
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(chip));
        bar.setIndeterminate(true);
        c.body.addView(bar, new LinearLayout.LayoutParams(-1, dp(8)));
        final TextView status = text("Working…", 14, ink2, false);
        status.setPadding(0, dp(10), 0, dp(4));
        c.body.addView(status);
        c.show();
        bg.post(() -> {
            File made = null; Throwable err = null;
            try {
                made = job.run((done, total) -> main.post(() -> { bar.setIndeterminate(false); bar.setMax(total); bar.setProgress(done); status.setText(t("Working…") + "  " + done + " / " + total); }));
                if (made != null) {
                    if (made.isDirectory()) { File[] in = made.listFiles(); if (in != null) PdfKit.scan(this, in); } else PdfKit.scan(this, made);
                }
            } catch (Throwable t) { err = t; android.util.Log.w("PixelSuite", "pdf: " + tool.id, t); }
            final File out = made; final Throwable e = err;
            main.post(() -> { c.dismiss(); done(tool, out, e); reload(); });
        });
    }

    private void done(Tool tool, final File out, Throwable e) {
        if (e != null || out == null) {
            Card c = new Card("Couldn't finish");
            c.note(e instanceof PdfKit.Wrong ? "That password isn't right." : e instanceof UnsupportedOperationException ? "This needs a newer Android version." : "This file couldn't be read. It may be damaged or locked.");
            c.button("OK", true, null);
            c.show();
            return;
        }
        Card c = new Card("Done");
        ImageView i = new ImageView(this); i.setImageDrawable(art(tool));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(56), dp(56)); il.gravity = Gravity.CENTER_HORIZONTAL; il.bottomMargin = dp(10);
        c.body.addView(i, 0, il);
        pop(i);
        c.note(out.isDirectory() ? t("Saved to") + " Pictures/Pixel PDF/" + out.getName() : out.getName() + "  •  " + PdfKit.size(out.length()));
        if (out.isDirectory()) {
            c.button("Done", false, null);
            c.button("Share", true, () -> { File[] fs = out.listFiles(); if (fs != null && fs.length > 0) { java.util.Arrays.sort(fs); share(fs); } });
        } else {
            c.button("Share", false, () -> share(out));
            c.button("Open", true, () -> open(out));
        }
        c.show();
    }

    void run(Tool tool) {
        if (!cards.isEmpty()) return;
        if (tool == MORE) { openTools(); return; }
        if (tool == SCAN || tool == IMG2PDF || tool == IDCARD) {
            Intent i = new Intent(this, SearchScan.class).putExtra(SearchScan.FROM_APP, true);
            if (tool == IMG2PDF) i.putExtra(SearchScan.MODE, "gallery");
            if (tool == IDCARD) i.putExtra(SearchScan.MODE, "id");
            startActivity(i);
            return;
        }
        pending = tool;
        if (tool == IMPORT_PDF) pick("application/pdf", true, IMPORT);
        else if (tool == MERGE) chooser(true);
        else if (tool == DOCX2PDF) pick("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, PICK_DOCX);
        else if (tool == PPT2PDF) pick("application/vnd.openxmlformats-officedocument.presentationml.presentation", false, PICK_PPTX);
        else chooser(false);
    }

    private void pick(String mime, boolean many, int code) {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
            if (many) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, code);
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    private void chooser(final boolean many) {
        final Tool tool = pending;
        final Card c = new Card(many ? "Choose PDFs to merge" : "Choose a PDF");
        c.note(tool.name);
        final ArrayList<File> order = new ArrayList<File>();
        LinearLayout listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this); sv.addView(listBox);
        sv.setVerticalScrollBarEnabled(false);
        c.body.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(dp(330), getResources().getDisplayMetrics().heightPixels / 2)));
        ArrayList<Item> src = new ArrayList<Item>();
        HashSet<String> seen = new HashSet<String>();
        for (Item i : filtered(library)) if (i.pdf && seen.add(i.f.getPath())) src.add(i);
        for (Item i : device) if (i.pdf && seen.add(i.f.getPath())) src.add(i);
        final TextView go = many ? c.button("Merge PDF", true, () -> {
            if (order.size() < 2) { toast("Pick two or more PDFs to merge"); throw new Stay(); }
            final ArrayList<File> fs = new ArrayList<File>(order);
            work(MERGE, p -> PdfKit.merge(fs, p));
        }) : null;
        for (final Item i : src) {
            final LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(6), dp(8), dp(6), dp(8));
            r.setBackground(round(0, dp(16)));
            ImageView pic = new ImageView(this); pic.setScaleType(ImageView.ScaleType.CENTER_CROP); pic.setBackground(round(chip, dp(10))); pic.setClipToOutline(true);
            r.addView(pic, new LinearLayout.LayoutParams(dp(44), dp(52)));
            thumb(i, pic, null);
            TextView n = text(i.name, 15, ink, false); n.setSingleLine(true); n.setEllipsize(TextUtils.TruncateAt.MIDDLE); n.setPadding(dp(14), 0, dp(8), 0);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
            final TextView num = text("", 13, onAccent, true); num.setGravity(Gravity.CENTER); num.setVisibility(View.GONE); num.setBackground(circle(accent));
            r.addView(num, new LinearLayout.LayoutParams(dp(26), dp(26)));
            r.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (!many) { c.dismiss(); onPdf(tool, i.f); return; }
                if (order.remove(i.f)) { num.setVisibility(View.GONE); r.setBackground(round(0, dp(16))); }
                else { order.add(i.f); num.setVisibility(View.VISIBLE); pop(num); r.setBackground(round(blend(card, accent, 0.14f), dp(16))); }
                for (int k = 0; k < listBox.getChildCount(); k++) {
                    View row = listBox.getChildAt(k); Object f = row.getTag();
                    int at = order.indexOf(f);
                    TextView badge = (TextView) ((LinearLayout) row).getChildAt(2);
                    if (at >= 0) badge.setText(String.valueOf(at + 1));
                }
                if (go != null) go.setText(order.size() >= 2 ? t("Merge PDF") + " (" + order.size() + ")" : t("Merge PDF"));
            });
            r.setTag(i.f);
            listBox.addView(r);
        }
        if (src.isEmpty()) { TextView e = text("No PDFs yet.", 15, ink2, false); e.setPadding(dp(6), dp(10), 0, dp(10)); listBox.addView(e); }
        c.button("Browse", false, () -> pick("application/pdf", many, many ? PICK_PDFS : PICK_PDF));
        c.show();
    }

    private void onPdf(Tool tool, File f) {
        if (tool == COMPRESS) compress(f);
        else if (tool == PDF2JPG) work(PDF2JPG, p -> PdfKit.toJpg(f, p));
        else if (tool == PDF2WORD) work(PDF2WORD, p -> PdfKit.toDocx(f, p));
        else if (tool == PDF2PPT) work(PDF2PPT, p -> PdfKit.toPptx(f, p));
        else if (tool == PRINT) PdfKit.print(this, f);
        else if (tool == DOODLE || tool == ADDTEXT || tool == SIGN) edit(f, tool);
        else if (tool == LOCK) lock(f);
        else if (tool == UNLOCK) unlock(f);
    }

    @Override protected void onActivityResult(int request, int result, final Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        final ArrayList<Uri> us = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) us.add(clip.getItemAt(i).getUri());
        else if (data.getData() != null) us.add(data.getData());
        if (us.isEmpty()) return;
        final Tool tool = pending;
        if (request == IMPORT) {
            work(IMPORT_PDF, p -> { File last = null; int k = 0; for (Uri u : us) { last = bring(u, PdfKit.library(), ".pdf"); p.step(++k, us.size()); } return last; });
            return;
        }
        if (request == PICK_DOCX || request == PICK_PPTX) {
            final boolean doc = request == PICK_DOCX;
            work(doc ? DOCX2PDF : PPT2PDF, p -> {
                File tmp = bring(us.get(0), new File(getCacheDir(), "pdf-in"), doc ? ".docx" : ".pptx");
                String name = PdfKit.base(tmp);
                try { return doc ? PdfKit.docx(tmp, name) : PdfKit.pptx(tmp, name); } finally { tmp.delete(); }
            });
            return;
        }
        bg.post(() -> {
            final ArrayList<File> fs = new ArrayList<File>();
            for (Uri u : us) { try { File f = local(u); if (f != null) fs.add(f); } catch (Throwable ignored) { } }
            main.post(() -> {
                if (fs.isEmpty()) { toast("Couldn't open it"); return; }
                if (request == PICK_PDFS) {
                    if (fs.size() < 2) { toast("Pick two or more PDFs to merge"); return; }
                    work(MERGE, p -> PdfKit.merge(fs, p));
                } else if (tool != null) onPdf(tool, fs.get(0));
            });
        });
    }

    private String nameOf(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[]{"_display_name"}, null, null, null);
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Throwable ignored) { } finally { if (c != null) c.close(); }
        String s = u.getLastPathSegment();
        return s == null ? "File" : s.substring(s.lastIndexOf('/') + 1);
    }

    private File bring(Uri u, File dir, String ext) throws Exception {
        String n = nameOf(u);
        String base = n.toLowerCase(Locale.ROOT).endsWith(ext) ? n.substring(0, n.length() - ext.length()) : n;
        File to = PdfKit.unique(dir, PdfKit.clean(base), ext);
        InputStream in = getContentResolver().openInputStream(u);
        if (in == null) throw new java.io.IOException("no stream");
        PdfKit.copy(in, to);
        return to;
    }

    private File local(Uri u) throws Exception {
        try {
            String id = android.provider.DocumentsContract.getDocumentId(u);
            if (id != null && id.startsWith("primary:")) { File f = new File(android.os.Environment.getExternalStorageDirectory(), id.substring(8)); if (f.isFile() && f.canRead()) return f; }
            if (id != null && id.startsWith("raw:")) { File f = new File(id.substring(4)); if (f.isFile() && f.canRead()) return f; }
        } catch (Throwable ignored) { }
        return bring(u, new File(getCacheDir(), "pdf-in"), ".pdf");
    }

    private void openTools() {
        if (toolsPage != null) return;
        final FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(bgc);
        page.setClickable(true);
        page.setElevation(dp(24));
        ScrollView sv = new ScrollView(this); sv.setVerticalScrollBarEnabled(false); sv.setClipToPadding(false);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), insetTop + dp(72), dp(16), insetBottom + dp(32));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(bgc); top.setPadding(dp(16), insetTop + dp(8), dp(16), 0); top.setElevation(dp(2));
        ImageView back = glyph(SearchGlyph.BACK, ink, dp(11));
        back.setContentDescription(I18n.cs("Back"));
        press(back);
        back.setOnClickListener(v -> closeTools());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView tt = text("All tools", 26, ink, true); tt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); tt.setPadding(dp(8), 0, 0, 0);
        top.addView(tt);

        String[] heads = {"Convert", "Popular", "Edit"};
        Tool[][] groups = {{IMG2PDF, DOCX2PDF, PPT2PDF, PDF2JPG, PDF2WORD, PDF2PPT}, {SCAN, IDCARD, IMPORT_PDF, PRINT}, {MERGE, COMPRESS, DOODLE, ADDTEXT, SIGN, LOCK, UNLOCK}};
        int n = 0;
        for (int g = 0; g < 3; g++) {
            TextView h = text(heads[g], 21, ink, true); h.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            h.setPadding(dp(8), dp(g == 0 ? 6 : 26), 0, dp(12));
            col.addView(h);
            LinearLayout row = null;
            for (int i = 0; i < groups[g].length; i++) {
                if (i % 3 == 0) { row = new LinearLayout(this); col.addView(row); }
                final Tool tool = groups[g][i];
                LinearLayout cardV = new LinearLayout(this); cardV.setOrientation(LinearLayout.VERTICAL); cardV.setGravity(Gravity.CENTER);
                cardV.setBackground(round(card, dp(24)));
                cardV.setPadding(dp(6), dp(20), dp(6), dp(18));
                ImageView ic = new ImageView(this); ic.setImageDrawable(art(tool));
                cardV.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
                TextView name = text(tool.name, 14.5f, ink, true); name.setGravity(Gravity.CENTER); name.setPadding(0, dp(12), 0, 0); name.setMaxLines(2);
                cardV.addView(name, new LinearLayout.LayoutParams(-1, -2));
                press(cardV);
                cardV.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run(tool); });
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1); cl.setMargins(dp(5), dp(5), dp(5), dp(5));
                row.addView(cardV, cl);
                cardV.setAlpha(0); cardV.setScaleX(0.86f); cardV.setScaleY(0.86f);
                cardV.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(60 + n++ * 22L).setDuration(460).setInterpolator(new SearchOverlay.Spring(0.6f)).start();
            }
            int rest = (3 - groups[g].length % 3) % 3;
            for (int k = 0; k < rest; k++) { LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, 1, 1); cl.setMargins(dp(5), 0, dp(5), 0); row.addView(new View(this), cl); }
        }
        sv.addView(col);
        page.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        page.addView(top, new FrameLayout.LayoutParams(-1, insetTop + dp(72), Gravity.TOP));
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> top.setElevation(y > 2 ? dp(4) : 0));
        top.setElevation(0);
        root.addView(page, root.indexOfChild(overlay), new FrameLayout.LayoutParams(-1, -1));
        toolsPage = page;
        page.setPivotX(getResources().getDisplayMetrics().widthPixels * 0.85f); page.setPivotY(dp(330));
        SearchOverlay.bloomIn(page);
    }

    private void closeTools() {
        final View p = toolsPage;
        if (p == null) return;
        toolsPage = null;
        p.animate().alpha(0).scaleX(0.94f).scaleY(0.94f).setDuration(180).withEndAction(() -> root.removeView(p)).start();
    }

    @Override public void onBackPressed() {
        if (topCard()) return;
        if (openMenu != null) { openMenu.dismiss(); return; }
        if (toolsPage != null) { closeTools(); return; }
        if (selecting) { endSelect(); return; }
        if (searching) { closeSearch(); return; }
        if (tab == 1) { show(0); return; }
        super.onBackPressed();
    }

    final class Menu {
        final View anchor; final LinearLayout box;
        FrameLayout scrim;
        Menu(View anchor) {
            this.anchor = anchor;
            box = new LinearLayout(PdfApp.this); box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(round(card, dp(22))); box.setElevation(dp(16));
            box.setPadding(0, dp(8), 0, dp(8));
        }
        void add(String glyph, String label, boolean checked, Runnable run) {
            LinearLayout r = new LinearLayout(PdfApp.this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(18), 0, dp(18), 0);
            r.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(chip), null, new android.graphics.drawable.ColorDrawable(Color.WHITE)));
            boolean red = "bin".equals(glyph);
            if (glyph != null || checked) { ImageView i = glyph(checked ? SearchGlyph.TICK : glyph, red ? 0xffe5484d : checked ? accent : ink, dp(1)); r.addView(i, new LinearLayout.LayoutParams(dp(22), dp(22))); }
            TextView tv = text(label, 15.5f, red ? 0xffe5484d : ink, false); tv.setPadding(glyph != null || checked ? dp(14) : 0, 0, 0, 0);
            r.addView(tv);
            r.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); dismiss(); main.postDelayed(run, 120); });
            box.addView(r, new LinearLayout.LayoutParams(-1, dp(50)));
        }
        void add(Tool tool, Runnable run) {
            LinearLayout r = new LinearLayout(PdfApp.this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(16), 0, dp(18), 0);
            r.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(chip), null, new android.graphics.drawable.ColorDrawable(Color.WHITE)));
            ImageView i = new ImageView(PdfApp.this); i.setImageDrawable(art(tool));
            r.addView(i, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView tv = text(tool.name, 15.5f, ink, false); tv.setPadding(dp(12), 0, 0, 0);
            r.addView(tv);
            r.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); dismiss(); main.postDelayed(run, 120); });
            box.addView(r, new LinearLayout.LayoutParams(-1, dp(50)));
        }
        void show() {
            if (openMenu != null) openMenu.dismiss();
            openMenu = this;
            scrim = new FrameLayout(PdfApp.this);
            scrim.setOnClickListener(v -> dismiss());
            int[] at = new int[2], r = new int[2];
            anchor.getLocationInWindow(at); root.getLocationInWindow(r);
            box.measure(View.MeasureSpec.makeMeasureSpec(dp(236), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            int h = box.getMeasuredHeight(), sh = root.getHeight();
            int x = Math.max(dp(10), Math.min(root.getWidth() - dp(236) - dp(10), at[0] - r[0] + anchor.getWidth() - dp(236)));
            int y = at[1] - r[1] + anchor.getHeight();
            boolean up = y + h > sh - dp(20);
            if (up) y = Math.max(dp(30), at[1] - r[1] - h);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(236), -2);
            lp.leftMargin = x; lp.topMargin = y;
            scrim.addView(box, lp);
            overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            box.setPivotX(dp(236) - anchor.getWidth() / 2f); box.setPivotY(up ? h : 0);
            SearchOverlay.bloomIn(box);
        }
        void dismiss() {
            if (openMenu == this) openMenu = null;
            final FrameLayout s = scrim;
            if (s == null) return;
            scrim = null;
            s.setClickable(false);
            SearchOverlay.bloomOut(box, () -> overlay.removeView(s));
            s.postDelayed(() -> overlay.removeView(s), 400);
        }
    }

    Menu openMenu;

    static final class Stay extends RuntimeException { }

    private boolean topCard() {
        if (cards.isEmpty()) return false;
        Card c = cards.get(cards.size() - 1);
        if (c.cancelable) c.dismiss();
        return true;
    }

    final class Card {
        final LinearLayout box, body, buttons; FrameLayout scrim; boolean cancelable = true;
        Card(String title) {
            box = new LinearLayout(PdfApp.this); box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(round(card, dp(30))); box.setElevation(dp(24));
            box.setPadding(dp(24), dp(22), dp(24), dp(16));
            TextView tv = text(title, 21, ink, true); tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            box.addView(tv);
            body = new LinearLayout(PdfApp.this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(0, dp(12), 0, 0);
            box.addView(body);
            buttons = new LinearLayout(PdfApp.this); buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); buttons.setPadding(0, dp(16), 0, 0);
            box.addView(buttons);
        }
        void note(String s) { TextView tv = text(s, 15, ink2, false); tv.setLineSpacing(dp(2), 1); tv.setPadding(0, 0, 0, dp(8)); body.addView(tv); }
        EditText field(String value, boolean secret) {
            EditText e = new EditText(PdfApp.this);
            e.setSingleLine(true); e.setText(value); e.setTextColor(ink); e.setHintTextColor(ink2); e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            e.setBackground(round(chip, dp(16))); e.setPadding(dp(16), dp(12), dp(16), dp(12));
            if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
            body.addView(e, lp);
            return e;
        }
        void option(String name, String note, Runnable run) {
            LinearLayout r = new LinearLayout(PdfApp.this); r.setOrientation(LinearLayout.VERTICAL);
            r.setBackground(round(chip, dp(18))); r.setPadding(dp(18), dp(12), dp(18), dp(12));
            r.addView(text(name, 16, ink, true));
            TextView n = text(note, 13.5f, ink2, false); n.setPadding(0, dp(2), 0, 0); r.addView(n);
            press(r);
            r.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run.run(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
            body.addView(r, lp);
        }
        TextView button(String label, boolean main, Runnable run) {
            TextView b = text(label, 15, main ? onAccent : accent, true);
            b.setGravity(Gravity.CENTER); b.setPadding(dp(20), 0, dp(20), 0); b.setMinWidth(dp(88));
            b.setBackground(main ? round(accent, dp(22)) : null);
            press(b);
            b.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                try { if (run != null) run.run(); } catch (Stay s) { return; }
                dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44)); lp.setMarginStart(dp(8));
            buttons.addView(b, lp);
            return b;
        }
        void show() {
            scrim = new FrameLayout(PdfApp.this);
            scrim.setBackgroundColor(0);
            scrim.setOnClickListener(v -> { if (cancelable) dismiss(); });
            box.setClickable(true);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(420)), -2, Gravity.CENTER);
            scrim.addView(box, lp);
            overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            ValueAnimator dim = ValueAnimator.ofInt(0, dark ? 150 : 90); dim.setDuration(220);
            dim.addUpdateListener(a -> scrim.setBackgroundColor(((Integer) a.getAnimatedValue()) << 24));
            dim.start();
            cards.add(this);
            SearchOverlay.bloomIn(box);
            for (int i = 0; i < body.getChildCount(); i++) if (body.getChildAt(i) instanceof EditText) { final View e = body.getChildAt(i); e.requestFocus(); main.postDelayed(() -> { try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(e, 0); } catch (Throwable ignored) { } }, 160); break; }
        }
        void dismiss() {
            final FrameLayout s = scrim;
            if (s == null) return;
            scrim = null;
            cards.remove(this);
            try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(box.getWindowToken(), 0); } catch (Throwable ignored) { }
            s.setClickable(false);
            s.animate().alpha(0).setDuration(200).start();
            SearchOverlay.bloomOut(box, () -> overlay.removeView(s));
            s.postDelayed(() -> overlay.removeView(s), 420);
        }
    }

    final ArrayList<Card> cards = new ArrayList<Card>();
}
