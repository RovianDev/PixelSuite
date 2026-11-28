package io.github.pixelsuite;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfEdit extends Activity {
    static final String FILE = "file", MODE = "mode";
    private static final int DOODLE = 0, TEXT = 1, SIGN = 2, SHAPE = 3;
    private static final float UNIT = 1000f;
    private static final int[] INKS = {0xff111111, 0xff1a56db, 0xffe02424, 0xff0e9f6e, 0xff7e3af2};

    static final class Mark {
        int kind; final ArrayList<float[]> pts = new ArrayList<float[]>(); int color; float width; String text; float x, y, size, w, h; Bitmap sign; Doodle shape;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread worker; private Handler bg;
    private float d; private boolean dark;
    private int bgc, card, ink, ink2, accent, onAccent, chip;
    private File file; private String password;
    private PdfRenderer renderer;
    private final Object lock = new Object();
    private int pages; private float[] ratios;
    private final Map<Integer, List<Mark>> marks = new HashMap<Integer, List<Mark>>();
    private final LruCache<Integer, Bitmap> cache = new LruCache<Integer, Bitmap>(40 * 1024 * 1024) { @Override protected int sizeOf(Integer k, Bitmap b) { return b.getByteCount(); } };
    private FrameLayout root, overlay;
    private ListView list;
    private Adapter adapter;
    private LinearLayout tools;
    private TextView save, title;
    private Editor editor;
    private int startMode = -1;
    private int top, bottom;

    private int dp(float v) { return Math.round(v * d); }
    private void toast(String s) { PdfHost.toast(this, I18n.cs(s)); }
    private TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(I18n.cs(s)); v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    @Override protected void onResume() { super.onResume(); if (!PdfHost.ok(this)) { PdfHost.locked(this); finish(); } }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!PdfHost.ok(this)) { PdfHost.locked(this); finish(); return; }
        String path = getIntent().getStringExtra(FILE);
        final Uri sent = path == null ? getIntent().getData() : null;
        if (path == null && sent == null) { finish(); return; }
        file = path == null ? null : new File(path);
        String m = getIntent().getStringExtra(MODE);
        if ("doodle".equals(m)) startMode = DOODLE; else if ("addtext".equals(m)) startMode = TEXT; else if ("sign".equals(m)) startMode = SIGN;
        d = getResources().getDisplayMetrics().density;
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean black = false; try { black = PdfHost.superDark(this); } catch (Throwable ignored) { }
        if (black) dark = true;
        bgc = black ? 0xff000000 : dark ? 0xff111318 : 0xffe9ecf3;
        card = black ? 0xff121316 : dark ? 0xff1d2025 : 0xffffffff;
        ink = dark ? 0xfff1f3f6 : 0xff15171b; ink2 = dark ? 0xffa9adb5 : 0xff6b7079;
        chip = dark ? 0x22ffffff : 0x10000000;
        accent = tone(dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xff8ab4f8 : 0xff3d5afe);
        onAccent = dark ? tone("system_accent1_800", 0xff0b1b33) : 0xffffffff;
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT); w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | (dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x10));
        worker = new HandlerThread("pdf-view"); worker.start(); bg = new Handler(worker.getLooper());
        build();
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, in) -> { top = in.getSystemWindowInsetTop(); bottom = in.getStableInsetBottom(); place(); return in.consumeSystemWindowInsets(); });
        if (file != null) load(null); else bring(sent);
    }

    private void bring(final Uri u) {
        bg.post(() -> {
            File got = null;
            try {
                if ("file".equals(u.getScheme()) && u.getPath() != null && new File(u.getPath()).canRead()) got = new File(u.getPath());
                else {
                    String name = null;
                    android.database.Cursor c = null;
                    try { c = getContentResolver().query(u, new String[]{"_display_name"}, null, null, null); if (c != null && c.moveToFirst()) name = c.getString(0); } catch (Throwable ignored) { }
                    finally { if (c != null) try { c.close(); } catch (Throwable ignored) { } }
                    if (name == null || name.trim().isEmpty()) name = "Document.pdf";
                    name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) name += ".pdf";
                    File dir = new File(getCacheDir(), "opened"); dir.mkdirs();
                    File[] old = dir.listFiles();
                    if (old != null) for (File o : old) if (System.currentTimeMillis() - o.lastModified() > 86400000L) o.delete();
                    File to = new File(dir, name);
                    PdfKit.copy(getContentResolver().openInputStream(u), to);
                    got = to;
                }
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "pdf: open", t); }
            final File f = got;
            main.post(() -> {
                if (f == null) { toast("This file couldn't be read. It may be damaged or locked."); finish(); return; }
                file = f; title.setText(PdfKit.base(f)); load(null);
            });
        });
    }

    private int tone(String name, int fallback) {
        try { int id = getResources().getIdentifier(name, "color", "android"); if (id != 0) return getResources().getColor(id) | 0xff000000; } catch (Throwable ignored) { }
        return fallback;
    }

    private View topBar;

    private void place() {
        topBar.setPadding(dp(6), top, dp(10), 0);
        list.setPadding(0, top + dp(64), 0, bottom + dp(110));
        ((FrameLayout.LayoutParams) tools.getLayoutParams()).bottomMargin = bottom + dp(16);
        tools.requestLayout();
        if (editor != null) editor.inset();
    }

    private void build() {
        root = new FrameLayout(this); root.setBackgroundColor(bgc);
        adapter = new Adapter();
        list = new ListView(this);
        list.setDivider(null); list.setSelector(new android.graphics.drawable.ColorDrawable(0)); list.setClipToPadding(false); list.setVerticalScrollBarEnabled(false);
        list.setAdapter(adapter);
        Zoom zoom = new Zoom();
        zoom.addView(list, new FrameLayout.LayoutParams(-1, -1));
        root.addView(zoom, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor((bgc & 0xffffff) | 0xee000000);
        ImageView back = icon(SearchGlyph.BACK, ink); back.setContentDescription(I18n.cs("Back"));
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        title = text(file == null ? "" : PdfKit.base(file), 18, ink, true); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.MIDDLE); title.setPadding(dp(6), 0, dp(6), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        save = text("Save", 15, onAccent, true); save.setGravity(Gravity.CENTER); save.setPadding(dp(18), 0, dp(18), 0);
        save.setBackground(PdfApp.round(accent, dp(20))); save.setVisibility(View.GONE);
        PdfApp.press(save);
        save.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); saveAll(); });
        bar.addView(save, new LinearLayout.LayoutParams(-2, dp(40)));
        ImageView share = icon(SearchGlyph.SHARE, ink); share.setContentDescription(I18n.cs("Share"));
        share.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); if (file == null) return; if (save.getVisibility() == View.VISIBLE) { shareNext = true; saveAll(); } else share(file); });
        bar.addView(share, new LinearLayout.LayoutParams(dp(48), dp(48)));
        topBar = bar;
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        bar.setMinimumHeight(dp(64));

        tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackground(PdfApp.round(card, dp(30))); tools.setElevation(dp(14)); tools.setPadding(dp(8), dp(6), dp(8), dp(6));
        tools.addView(tool(PdfApp.DOODLE, DOODLE));
        tools.addView(tool(PdfApp.ADDTEXT, TEXT));
        tools.addView(tool(PdfApp.SIGN, SIGN));
        View pr = tool(PdfApp.PRINT, -1);
        tools.addView(pr);
        root.addView(tools, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        tools.setPivotY(dp(60)); tools.post(() -> { tools.setPivotX(tools.getWidth() / 2f); SearchOverlay.bloomIn(tools); });
    }

    private int sharp = 1;
    private final HashMap<Integer, Integer> drawn = new HashMap<Integer, Integer>();

    private final class Zoom extends FrameLayout {
        private float s = 1f, tx, lastX, lastY, downX, downY;
        private boolean two, pan;
        private final android.view.ScaleGestureDetector pinch;
        Zoom() {
            super(PdfEdit.this);
            pinch = new android.view.ScaleGestureDetector(PdfEdit.this, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(android.view.ScaleGestureDetector g) {
                    float next = Math.max(1f, Math.min(5f, s * g.getScaleFactor()));
                    if (next == s) return true;
                    float fx = g.getFocusX(), fy = g.getFocusY(), cx = (fx - tx) / s, cy = fy / s;
                    s = next;
                    tx = fx - cx * s;
                    try { list.scrollListBy(Math.round(cy - fy / s)); } catch (Throwable ignored) { }
                    apply();
                    return true;
                }
            });
        }
        void apply() {
            float min = -getWidth() * (s - 1f);
            tx = Math.max(min, Math.min(0f, tx));
            list.setPivotX(0); list.setPivotY(0);
            list.setScaleX(s); list.setScaleY(s); list.setTranslationX(tx);
        }
        void settle() {
            int want = s > 1.25f ? 2 : 1;
            if (want != sharp) { sharp = want; if (want > 1) cache.resize(120 * 1024 * 1024); adapter.notifyDataSetChanged(); }
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { two = false; pan = false; downX = lastX = e.getX(); downY = lastY = e.getY(); return false; }
            if (e.getPointerCount() >= 2) { two = true; return true; }
            if (a == MotionEvent.ACTION_MOVE && s > 1.02f && !pan) {
                float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                if (dx > dp(10) && dx > dy * 1.4f) { pan = true; lastX = e.getX(); return true; }
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (e.getPointerCount() >= 2 || two) pinch.onTouchEvent(e);
            if (a == MotionEvent.ACTION_POINTER_DOWN) two = true;
            if (pan && !two && a == MotionEvent.ACTION_MOVE) { tx += e.getX() - lastX; lastX = e.getX(); apply(); }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { two = false; pan = false; settle(); }
            return true;
        }
    }

    private ImageView icon(String g, int color) {
        ImageView i = new ImageView(this); i.setImageDrawable(new SearchGlyph(g, color)); i.setPadding(dp(12), dp(12), dp(12), dp(12));
        PdfApp.press(i);
        return i;
    }

    private View tool(final PdfApp.Tool t, final int mode) {
        LinearLayout b = new LinearLayout(this); b.setGravity(Gravity.CENTER); b.setPadding(dp(14), dp(10), dp(14), dp(10));
        ImageView i = new ImageView(this); i.setImageDrawable(new PdfApp.Badge(this, t.icon, t.label));
        b.addView(i, new LinearLayout.LayoutParams(dp(32), dp(32)));
        b.setContentDescription(I18n.cs(t.name));
        PdfApp.press(b);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (file == null) return;
            if (mode < 0) { if (password != null) toast("Unlock it first to print"); else PdfKit.print(this, file); return; }
            if (password != null) { toast("Unlock it first to edit"); return; }
            int at = list.getFirstVisiblePosition();
            View first = list.getChildAt(0);
            if (first != null && first.getBottom() < list.getHeight() / 3 && at + 1 < pages) at++;
            openEditor(Math.max(0, Math.min(pages - 1, at)), mode);
        });
        return b;
    }

    private void load(final String pw) {
        bg.post(() -> {
            try {
                PdfRenderer r = PdfKit.open(file, pw);
                float[] rs = new float[r.getPageCount()];
                for (int i = 0; i < rs.length; i++) { PdfRenderer.Page p = r.openPage(i); rs[i] = p.getHeight() / (float) Math.max(1, p.getWidth()); p.close(); }
                synchronized (lock) { renderer = r; }
                main.post(() -> {
                    password = pw; ratios = rs; pages = rs.length; adapter.notifyDataSetChanged();
                    if (startMode >= 0 && pages > 0 && pw == null) { final int m = startMode; startMode = -1; main.postDelayed(() -> openEditor(0, m), 380); }
                });
            } catch (PdfKit.Wrong w) {
                main.post(() -> askPassword(pw != null));
            } catch (Throwable t) {
                main.post(() -> { toast("This file couldn't be read. It may be damaged or locked."); finish(); });
            }
        });
    }

    private void askPassword(boolean again) {
        if (!PdfKit.passwords()) { toast("This PDF is locked. Opening it needs a newer Android version."); finish(); return; }
        final LinearLayout box = cardBox(again ? "That password isn't right." : "This PDF is locked");
        final EditText e = new EditText(this);
        e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setHint(I18n.cs("Password")); e.setTextColor(ink); e.setHintTextColor(ink2); e.setBackground(PdfApp.round(chip, dp(16))); e.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = buttons(box);
        final FrameLayout scrim = showCard(box, false);
        addButton(row, "Cancel", false, () -> { closeCard(scrim, box); finish(); });
        addButton(row, "Open", true, () -> { closeCard(scrim, box); load(e.getText().toString()); });
        e.requestFocus();
        main.postDelayed(() -> { try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(e, 0); } catch (Throwable ignored) { } }, 160);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        if (bg != null) bg.post(() -> { synchronized (lock) { if (renderer != null) try { renderer.close(); } catch (Throwable ignored) { } renderer = null; } });
        if (worker != null) worker.quitSafely();
    }

    private Bitmap page(int i, int width) {
        synchronized (lock) {
            if (renderer == null) return null;
            PdfRenderer.Page p = renderer.openPage(i);
            try { return PdfKit.render(p, 72f * width / Math.max(1, p.getWidth()), Math.min(4200, width * 2)); } finally { p.close(); }
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return pages; }
        @Override public Object getItem(int p) { return p; }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(final int p, View v, ViewGroup parent) {
            PageView pv = v instanceof FrameLayout ? (PageView) v.getTag() : null;
            if (pv == null) { pv = new PageView(); v = pv.frame; }
            pv.bind(p);
            return v;
        }
    }

    private final class PageView {
        final FrameLayout frame; final ImageView img; final MarkView marksView; int index;
        PageView() {
            frame = new FrameLayout(PdfEdit.this); frame.setTag(this);
            frame.setPadding(dp(14), dp(8), dp(14), dp(8));
            img = new ImageView(PdfEdit.this); img.setScaleType(ImageView.ScaleType.FIT_XY); img.setBackgroundColor(Color.WHITE);
            img.setElevation(dp(2));
            frame.addView(img, new FrameLayout.LayoutParams(-1, -1));
            marksView = new MarkView(); marksView.setElevation(dp(2));
            frame.addView(marksView, new FrameLayout.LayoutParams(-1, -1));
            frame.setOnClickListener(v -> { if (password == null) openEditor(index, -2); });
        }
        void bind(final int p) {
            index = p;
            final int w = getResources().getDisplayMetrics().widthPixels - dp(28);
            frame.setLayoutParams(new ListView.LayoutParams(-1, Math.round(w * ratios[p]) + dp(16)));
            marksView.page = p; marksView.invalidate();
            Bitmap b = cache.get(p);
            img.setTag(p);
            final int level = sharp;
            Integer have = drawn.get(p);
            if (b != null) { if (img.getDrawable() == null || ((android.graphics.drawable.BitmapDrawable) img.getDrawable()).getBitmap() != b) img.setImageBitmap(b); img.animate().cancel(); img.setAlpha(1f); if (have != null && have >= level) return; }
            else img.setImageDrawable(null);
            final boolean fresh = b == null;
            bg.post(() -> {
                Bitmap made = cache.get(p); Integer got = drawn.get(p);
                if (made == null || got == null || got < level) { Bitmap next = null; try { next = page(p, w * level); } catch (Throwable ignored) { } if (next != null) { made = next; cache.put(p, next); drawn.put(p, level); } }
                final Bitmap m = made;
                main.post(() -> { if (m != null && Integer.valueOf(p).equals(img.getTag())) { img.setImageBitmap(m); if (fresh) { img.setAlpha(0f); img.animate().alpha(1).setDuration(180).start(); } } });
            });
        }
    }

    private final class MarkView extends View {
        int page;
        MarkView() { super(PdfEdit.this); }
        @Override protected void onDraw(Canvas c) { List<Mark> l = marks.get(page); if (l != null) PdfEdit.draw(c, getWidth(), getHeight(), l, null); }
    }

    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG), TEXTP = new Paint(Paint.ANTI_ALIAS_FLAG), BMP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Paint SHAPEP = new Paint(Paint.ANTI_ALIAS_FLAG);
    static { STROKE.setStyle(Paint.Style.STROKE); STROKE.setStrokeCap(Paint.Cap.ROUND); STROKE.setStrokeJoin(Paint.Join.ROUND); TEXTP.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }

    static void draw(Canvas c, float W, float H, List<Mark> l, Mark picked) {
        for (Mark m : l) {
            if (m.kind == DOODLE) {
                STROKE.setColor(m.color); STROKE.setStrokeWidth(m.width * W);
                Path p = new Path();
                for (int i = 0; i < m.pts.size(); i++) { float[] q = m.pts.get(i); if (i == 0) p.moveTo(q[0] * W, q[1] * H); else { float[] o = m.pts.get(i - 1); p.quadTo(o[0] * W, o[1] * H, (o[0] + q[0]) / 2 * W, (o[1] + q[1]) / 2 * H); } }
                if (m.pts.size() == 1) p.lineTo(m.pts.get(0)[0] * W + 0.1f, m.pts.get(0)[1] * H);
                c.drawPath(p, STROKE);
            } else if (m.kind == TEXT) {
                TEXTP.setColor(m.color); TEXTP.setTextSize(m.size * W);
                float y = m.y * H;
                for (String line : m.text.split("\n")) { c.drawText(line, m.x * W, y, TEXTP); y += m.size * W * 1.2f; }
            } else if (m.kind == SIGN && m.sign != null) {
                c.drawBitmap(m.sign, null, new RectF(m.x * W, m.y * H, (m.x + m.w) * W, (m.y + m.h) * H), BMP);
            } else if (m.kind == SHAPE && m.shape != null) {
                m.shape.color = m.color; m.shape.width = m.width * UNIT;
                c.save(); c.scale(W / UNIT, W / UNIT); m.shape.draw(c, SHAPEP); c.restore();
            }
            if (m == picked && m.kind != SHAPE) {
                RectF r = bounds(m, W, H);
                Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG); dash.setStyle(Paint.Style.STROKE); dash.setStrokeWidth(W / 300f + 2); dash.setColor(0xff3d5afe);
                dash.setPathEffect(new android.graphics.DashPathEffect(new float[]{12, 8}, 0));
                c.drawRoundRect(r, 8, 8, dash);
                if (m.kind == SIGN || m.kind == TEXT) { Paint h = new Paint(Paint.ANTI_ALIAS_FLAG); h.setColor(0xff3d5afe); c.drawCircle(r.right, r.bottom, Math.max(14, W / 45f), h); }
            }
        }
    }

    static RectF bounds(Mark m, float W, float H) {
        if (m.kind == SIGN) return new RectF(m.x * W, m.y * H, (m.x + m.w) * W, (m.y + m.h) * H);
        if (m.kind == SHAPE) { float k = W / UNIT; return new RectF(m.shape.left() * k, m.shape.top() * k, m.shape.right() * k, m.shape.bottom() * k); }
        if (m.kind == TEXT) {
            TEXTP.setTextSize(m.size * W);
            float wMax = 0; String[] lines = m.text.split("\n");
            for (String s : lines) wMax = Math.max(wMax, TEXTP.measureText(s));
            float lh = m.size * W * 1.2f;
            return new RectF(m.x * W - 8, m.y * H - m.size * W - 6, m.x * W + wMax + 8, m.y * H + lh * (lines.length - 1) + m.size * W * 0.3f + 6);
        }
        RectF r = new RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (float[] q : m.pts) r.union(q[0] * W, q[1] * H);
        return r;
    }

    private void dirty() {
        boolean any = false;
        for (List<Mark> l : marks.values()) if (!l.isEmpty()) { any = true; break; }
        if (any && save.getVisibility() != View.VISIBLE) { save.setVisibility(View.VISIBLE); PdfApp.pop(save); }
        else if (!any) save.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private final class Editor {
        final FrameLayout page; final int index; int mode; final Board board; final LinearLayout bar, modes; final TextView hint;
        int color = INKS[0]; float width = 0.006f, size = 0.04f; int tool = -1; boolean fill; LinearLayout shapes; View shapeRow; ImageView fillButton, trash;
        Editor(int index, int mode) {
            this.index = index;
            page = new FrameLayout(PdfEdit.this); page.setBackgroundColor(bgc); page.setClickable(true);
            board = new Board(this);
            page.addView(board, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout head = new LinearLayout(PdfEdit.this); head.setGravity(Gravity.CENTER_VERTICAL); head.setTag("head");
            ImageView close = icon(SearchGlyph.BACK, ink); close.setContentDescription(I18n.cs("Back")); close.setOnClickListener(v -> closeEditor());
            head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView t = text(PdfApp.t("Page") + " " + (index + 1) + " / " + pages, 17, ink, true); t.setPadding(dp(6), 0, dp(4), 0); t.setSingleLine(true);
            head.addView(t, new LinearLayout.LayoutParams(-2, -2));
            if (pages > 1) { head.addView(turn(-1), new LinearLayout.LayoutParams(dp(40), dp(48))); head.addView(turn(1), new LinearLayout.LayoutParams(dp(40), dp(48))); }
            head.addView(new View(PdfEdit.this), new LinearLayout.LayoutParams(0, 1, 1));
            ImageView undo = new ImageView(PdfEdit.this); android.graphics.drawable.Drawable ud = PdfApp.drawable(PdfEdit.this, "ic_pdf_undo"); ud.setTint(ink); undo.setImageDrawable(ud); undo.setPadding(dp(12), dp(12), dp(12), dp(12));
            undo.setContentDescription(I18n.cs("Undo")); PdfApp.press(undo);
            undo.setOnClickListener(v -> { List<Mark> l = marks.get(index); if (l != null && !l.isEmpty()) { l.remove(l.size() - 1); board.picked = null; board.invalidate(); v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); PdfApp.pop(v); } });
            head.addView(undo, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView done = text("Done", 15, onAccent, true); done.setGravity(Gravity.CENTER); done.setPadding(dp(18), 0, dp(18), 0); done.setBackground(PdfApp.round(accent, dp(20)));
            PdfApp.press(done); done.setOnClickListener(v -> closeEditor());
            head.addView(done, new LinearLayout.LayoutParams(-2, dp(40)));
            page.addView(head, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
            bar = new LinearLayout(PdfEdit.this); bar.setOrientation(LinearLayout.VERTICAL); bar.setGravity(Gravity.CENTER_HORIZONTAL);
            bar.setBackground(PdfApp.round(card, dp(28))); bar.setElevation(dp(14)); bar.setPadding(dp(10), dp(10), dp(10), dp(8));
            hint = text("", 13, ink2, false); hint.setGravity(Gravity.CENTER); hint.setPadding(dp(8), 0, dp(8), dp(8));
            bar.addView(hint);
            shapes = new LinearLayout(PdfEdit.this); shapes.setGravity(Gravity.CENTER_VERTICAL);
            for (int k = -1; k < Doodle.KINDS; k++) {
                final int kind = k;
                ImageView b = new ImageView(PdfEdit.this); b.setImageDrawable(new ShapeGlyph(kind, false, ink)); b.setPadding(dp(7), dp(7), dp(7), dp(7)); b.setTag(kind);
                b.setContentDescription(I18n.cs(kind < 0 ? "Pen" : Doodle.NAMES[kind])); PdfApp.press(b);
                b.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); PdfApp.pop(v); tool = kind;
                    if (kind >= 0 && board.picked != null && board.picked.kind == SHAPE) { board.picked.shape.convertTo(kind); if (board.picked.shape.isLine()) board.picked.shape.fill = false; dirty(); }
                    else if (kind < 0) board.picked = null;
                    paintShapes(); board.invalidate();
                });
                shapes.addView(b, new LinearLayout.LayoutParams(dp(34), dp(34)));
            }
            fillButton = new ImageView(PdfEdit.this); fillButton.setPadding(dp(7), dp(7), dp(7), dp(7)); fillButton.setContentDescription(I18n.cs("Fill shape")); PdfApp.press(fillButton);
            fillButton.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); PdfApp.pop(v);
                Mark m = board.picked;
                if (m != null && m.kind == SHAPE && m.shape.fillable()) { m.shape.fill = !m.shape.fill; fill = m.shape.fill; dirty(); } else fill = !fill;
                paintShapes(); board.invalidate();
            });
            shapes.addView(fillButton, new LinearLayout.LayoutParams(dp(34), dp(34)));
            trash = new ImageView(PdfEdit.this); trash.setImageDrawable(new ShapeGlyph(100, false, 0xffe5484d)); trash.setPadding(dp(8), dp(8), dp(8), dp(8)); trash.setContentDescription(I18n.cs("Delete shape")); PdfApp.press(trash);
            trash.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (board.picked != null && board.picked.kind == SHAPE) { board.list().remove(board.picked); board.picked = null; board.invalidate(); dirty(); paintShapes(); }
            });
            shapes.addView(trash, new LinearLayout.LayoutParams(dp(34), dp(34)));
            android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(PdfEdit.this); strip.setHorizontalScrollBarEnabled(false); strip.setOverScrollMode(View.OVER_SCROLL_NEVER);
            strip.addView(shapes, new FrameLayout.LayoutParams(-2, -2)); strip.setPadding(0, 0, 0, dp(8)); shapeRow = strip;
            bar.addView(strip, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout colors = new LinearLayout(PdfEdit.this); colors.setGravity(Gravity.CENTER); colors.setTag("colors");
            for (final int c : INKS) {
                final View dot = new View(PdfEdit.this); dot.setBackground(PdfApp.circle(c)); dot.setTag(c);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(30), dp(30)); dl.setMargins(dp(6), 0, dp(6), 0);
                dot.setOnClickListener(v -> { color = c; paintDots(colors); PdfApp.pop(v); v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); if (board.picked != null && board.picked.kind != SIGN) { board.picked.color = c; board.invalidate(); if (board.picked.kind == SHAPE) dirty(); } });
                colors.addView(dot, dl);
            }
            final TextView thick = text("", 13, ink, true); thick.setGravity(Gravity.CENTER); thick.setBackground(PdfApp.round(chip, dp(15))); thick.setPadding(dp(12), 0, dp(12), 0); thick.setTag("thick");
            thick.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); PdfApp.pop(v);
                if (this.mode == DOODLE) { width = width < 0.005f ? 0.006f : width < 0.009f ? 0.012f : 0.0035f; if (board.picked != null && board.picked.kind == SHAPE) { board.picked.width = width; board.invalidate(); dirty(); } }
                else { size = size < 0.032f ? 0.04f : size < 0.05f ? 0.06f : 0.028f; if (board.picked != null && board.picked.kind == TEXT) { board.picked.size = size; board.invalidate(); } }
                paintThick(thick);
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, dp(30)); tl.setMarginStart(dp(8));
            colors.addView(thick, tl);
            bar.addView(colors);
            modes = new LinearLayout(PdfEdit.this); modes.setGravity(Gravity.CENTER); modes.setPadding(0, dp(10), 0, 0);
            modes.addView(modeChip(PdfApp.DOODLE, DOODLE)); modes.addView(modeChip(PdfApp.ADDTEXT, TEXT)); modes.addView(modeChip(PdfApp.SIGN, SIGN));
            bar.addView(modes);
            page.addView(bar, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
            paintDots(colors); paintThick(thick);
            setMode(mode < 0 ? DOODLE : mode, false);
        }
        View turn(final int by) {
            final boolean can = index + by >= 0 && index + by < pages;
            ImageView a = new ImageView(PdfEdit.this);
            a.setImageDrawable(new Chevron(by, ink));
            a.setPadding(dp(10), dp(14), dp(10), dp(14));
            a.setAlpha(can ? 1f : 0.3f);
            a.setContentDescription(I18n.cs(by < 0 ? "Previous page" : "Next page"));
            if (can) { PdfApp.press(a); a.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); turnTo(index + by, by); }); }
            return a;
        }
        void paintShapes() {
            if (shapes == null) return;
            Mark m = board.picked; boolean shape = m != null && m.kind == SHAPE;
            int shown = shape ? m.shape.kind : tool;
            for (int i = 0; i < shapes.getChildCount(); i++) { View v = shapes.getChildAt(i); if (v.getTag() instanceof Integer) v.setBackground(PdfApp.round((Integer) v.getTag() == shown ? PdfApp.blend(card, accent, 0.24f) : 0, dp(12))); }
            boolean on = shape ? m.shape.fill : fill, can = shape ? m.shape.fillable() : tool >= 0 && !Doodle.isLine(tool);
            fillButton.setImageDrawable(new ShapeGlyph(Doodle.OVAL, true, on ? accent : ink)); fillButton.setBackground(PdfApp.round(on && can ? PdfApp.blend(card, accent, 0.24f) : 0, dp(12))); fillButton.setAlpha(can ? 1f : 0.3f); fillButton.setEnabled(can);
            trash.setAlpha(shape ? 1f : 0.3f); trash.setEnabled(shape);
            if (mode == DOODLE) hint.setText(I18n.cs(tool < 0 && !shape ? "Draw on the page with your finger." : "Tap or drag to add a shape. Drag its dots to resize it."));
            if (shape) { color = m.color; width = m.width; View colors = bar.findViewWithTag("colors"); if (colors instanceof LinearLayout) paintDots((LinearLayout) colors); paintThick((TextView) bar.findViewWithTag("thick")); }
        }
        void paintDots(LinearLayout colors) {
            for (int i = 0; i < colors.getChildCount(); i++) {
                View v = colors.getChildAt(i); if (!(v.getTag() instanceof Integer)) continue;
                GradientDrawable g = PdfApp.circle((Integer) v.getTag());
                if ((Integer) v.getTag() == color) g.setStroke(dp(3), dark ? 0xffffffff : 0xffc7cbd6);
                v.setBackground(g);
            }
        }
        void paintThick(TextView t) { t.setText(mode == DOODLE ? (width < 0.005f ? "Thin" : width < 0.009f ? "Medium" : "Thick") : (size < 0.032f ? "Small" : size < 0.05f ? "Medium" : "Large")); t.setText(I18n.cs(t.getText())); }
        View modeChip(PdfApp.Tool t, final int m) {
            LinearLayout b = new LinearLayout(PdfEdit.this); b.setGravity(Gravity.CENTER_VERTICAL); b.setPadding(dp(12), dp(6), dp(14), dp(6)); b.setTag(m);
            ImageView i = new ImageView(PdfEdit.this); i.setImageDrawable(new PdfApp.Badge(PdfEdit.this, t.icon, t.label));
            b.addView(i, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView n = text(t.name, 13, ink, true); n.setPadding(dp(6), 0, 0, 0); b.addView(n);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(dp(3), 0, dp(3), 0); b.setLayoutParams(lp);
            PdfApp.press(b);
            b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); setMode(m, true); });
            return b;
        }
        void setMode(int m, boolean act) {
            mode = m; board.picked = null; board.invalidate();
            for (int i = 0; i < modes.getChildCount(); i++) { View c = modes.getChildAt(i); boolean on = (Integer) c.getTag() == m; c.setBackground(PdfApp.round(on ? PdfApp.blend(card, accent, 0.2f) : 0, dp(18))); if (on && act) PdfApp.pop(c); }
            hint.setText(I18n.cs(m == DOODLE ? "Draw on the page with your finger." : m == TEXT ? "Tap where the text goes. Drag it to move it." : "Tap to place your signature. Drag the corner to resize it."));
            View colors = bar.findViewWithTag("colors"); colors.setVisibility(m == SIGN ? View.GONE : View.VISIBLE);
            paintThick((TextView) bar.findViewWithTag("thick"));
            shapeRow.setVisibility(m == DOODLE ? View.VISIBLE : View.GONE); paintShapes(); if (act) inset();
            if (m == SIGN && act) signature(null);
        }
        void inset() {
            View head = page.findViewWithTag("head"); head.setPadding(dp(6), top, dp(12), 0);
            ((FrameLayout.LayoutParams) bar.getLayoutParams()).bottomMargin = bottom + dp(12);
            board.setPadding(dp(14), top + dp(60), dp(14), bottom + dp(mode == DOODLE ? 214 : 170));
            page.requestLayout();
        }
    }

    private final class Board extends View {
        final Editor ed; Bitmap bmp; final RectF area = new RectF(), base = new RectF(); Mark cur, picked; boolean resizing; float gx, gy, startX, startY; boolean moved;
        float zoom = 1f, panX, panY, span0, zoom0, size0, w0, midX, midY, grab0; boolean twin; int had;
        static final int S_NONE = 0, S_PENDING = 1, S_CREATE = 2, S_MOVE = 3, S_HANDLE = 4;
        int sMode, sHandle; boolean sHad, snapX, snapY, snapLevel, snapUp; float lastX, lastY; Doodle free, pinchFrom; float[] pinchMid;
        void show() {
            float w = base.width() * zoom, h = base.height() * zoom;
            float roomX = Math.max(0, (w - base.width()) / 2f + dp(40) * (zoom > 1f ? 1 : 0)), roomY = Math.max(0, (h - base.height()) / 2f + dp(40) * (zoom > 1f ? 1 : 0));
            panX = Math.max(-roomX, Math.min(roomX, panX)); panY = Math.max(-roomY, Math.min(roomY, panY));
            float cx = base.centerX() + panX, cy = base.centerY() + panY;
            area.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            invalidate();
        }
        void sharpen() {
            final int want = Math.round(Math.min(4200f, base.width() * Math.min(zoom, 2.6f)));
            if (bmp != null && bmp.getWidth() >= want * 0.9f) return;
            bg.post(() -> { Bitmap b = null; try { b = page(ed.index, want); } catch (Throwable ignored) { } final Bitmap m = b; main.post(() -> { if (m != null) { bmp = m; invalidate(); } }); });
        }
        Board(Editor ed) { super(PdfEdit.this); this.ed = ed; }
        List<Mark> list() { List<Mark> l = marks.get(ed.index); if (l == null) { l = new ArrayList<Mark>(); marks.put(ed.index, l); } return l; }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { fit(); }
        @Override public void setPadding(int l, int t, int r, int b) { super.setPadding(l, t, r, b); fit(); }
        void fit() {
            float W = getWidth() - getPaddingLeft() - getPaddingRight(), H = getHeight() - getPaddingTop() - getPaddingBottom();
            if (W <= 0 || H <= 0 || ratios == null) return;
            float r = ratios[ed.index], w = Math.min(W, H / r), h = w * r;
            float x = getPaddingLeft() + (W - w) / 2, y = getPaddingTop() + (H - h) / 2;
            base.set(x, y, x + w, y + h);
            show();
            if (bmp == null || bmp.getWidth() < w * 0.9f) {
                final int want = Math.round(w);
                bg.post(() -> { Bitmap b = null; try { b = page(ed.index, want); } catch (Throwable ignored) { } final Bitmap m = b; main.post(() -> { bmp = m; invalidate(); }); });
            }
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG); shade.setColor(Color.WHITE); shade.setShadowLayer(dp(6), 0, dp(2), 0x33000000);
            if (zoom > 1f) c.clipRect(0, getPaddingTop() - dp(8), getWidth(), getHeight() - getPaddingBottom() + dp(8));
            c.drawRect(area, shade);
            if (bmp != null) c.drawBitmap(bmp, null, area, BMP);
            c.save(); c.translate(area.left, area.top); c.clipRect(0, 0, area.width(), area.height());
            PdfEdit.draw(c, area.width(), area.height(), list(), picked);
            c.restore();
            if (picked != null && picked.kind == SHAPE) chrome(c);
        }
        float unit() { return UNIT / area.width(); }
        float[] screen(Doodle s) { float k = area.width() / UNIT; return new float[]{area.left + s.x1 * k, area.top + s.y1 * k, area.left + s.x2 * k, area.top + s.y2 * k}; }
        void chrome(Canvas c) {
            Doodle s = picked.shape; float[] q = screen(s);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (sMode == S_MOVE && (snapX || snapY)) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(accent); p.setAlpha(0xb0); p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(6), dp(5)}, 0));
                if (snapX) c.drawLine(area.centerX(), area.top, area.centerX(), area.bottom, p);
                if (snapY) c.drawLine(area.left, area.centerY(), area.right, area.centerY(), p);
                p.setPathEffect(null);
            }
            if (sMode == S_MOVE || sMode == S_HANDLE || sMode == S_CREATE || twin) return;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); p.setColor(0xcc3d5afe); p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(5), dp(4)}, 0));
            if (s.isLine()) c.drawLine(q[0], q[1], q[2], q[3], p); else c.drawRect(Math.min(q[0], q[2]), Math.min(q[1], q[3]), Math.max(q[0], q[2]), Math.max(q[1], q[3]), p);
            p.setPathEffect(null);
            float[] h = Doodle.handlePoints(s.isLine(), q);
            for (int i = 0; i < h.length / 2; i++) {
                p.setStyle(Paint.Style.FILL); p.setColor(0x33000000); c.drawCircle(h[i * 2], h[i * 2 + 1] + dp(1), dp(7.5f), p);
                p.setColor(0xffffffff); c.drawCircle(h[i * 2], h[i * 2 + 1], dp(7), p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2.5f)); p.setColor(accent); c.drawCircle(h[i * 2], h[i * 2 + 1], dp(5), p);
            }
        }
        float[] level(float fx, float fy, float x, float y) {
            float k = area.width() / UNIT, sx = area.left + fx * k, sy = area.top + fy * k, dx = x - sx, dy = y - sy;
            double angle = Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)));
            boolean far = Math.hypot(dx, dy) > dp(24), lv = far && angle < 6, up = far && angle > 84;
            if ((lv && !snapLevel) || (up && !snapUp)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            snapLevel = lv; snapUp = up;
            if (lv) y = sy; else if (up) x = sx;
            return new float[]{(x - area.left) * unit(), (y - area.top) * unit()};
        }
        Mark shapeAt(float ux, float uy, float x2, float y2) {
            Mark m = new Mark(); m.kind = SHAPE; m.color = ed.color; m.width = ed.width;
            m.shape = new Doodle(ed.tool, ux, uy, x2, y2, ed.color, ed.width * UNIT, ed.fill && !Doodle.isLine(ed.tool));
            list().add(m); return m;
        }
        boolean shapeTouch(MotionEvent e, float x, float y) {
            float u = unit(), ux = (x - area.left) * u, uy = (y - area.top) * u;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    sMode = S_NONE; free = null; snapX = snapY = snapLevel = snapUp = false; lastX = x; lastY = y;
                    if (picked != null && picked.kind == SHAPE) {
                        float[] q = screen(picked.shape);
                        sHandle = Doodle.handleAt(picked.shape.isLine(), q, x, y, dp(26));
                        if (sHandle >= 0 && !picked.shape.isLine() && Math.abs(q[2] - q[0]) < dp(44) && Math.abs(q[3] - q[1]) < dp(44) && Doodle.hits(false, q, x, y, 0)) sHandle = -1;
                        if (sHandle >= 0) { sMode = S_HANDLE; return true; }
                    }
                    List<Mark> l = list();
                    for (int i = l.size() - 1; i >= 0; i--) {
                        Mark m = l.get(i); if (m.kind != SHAPE) continue;
                        if (Doodle.hits(m.shape.isLine(), screen(m.shape), x, y, dp(m.shape.isLine() ? 18 : 8))) {
                            if (m != picked) { picked = m; ed.paintShapes(); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
                            sMode = S_MOVE; invalidate(); return true;
                        }
                    }
                    if (ed.mode != DOODLE || ed.tool < 0) { if (picked != null && picked.kind == SHAPE) { picked = null; ed.paintShapes(); invalidate(); } return false; }
                    if (hit(x, y) != null) return false;
                    sHad = picked != null; sMode = S_PENDING; return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (sMode == S_NONE) return false;
                    if (sMode == S_PENDING) {
                        if (Math.hypot(x - startX, y - startY) <= dp(6)) return true;
                        if (!area.contains(startX, startY)) { sMode = S_NONE; return true; }
                        picked = shapeAt((startX - area.left) * u, (startY - area.top) * u, ux, uy); sMode = S_CREATE; ed.paintShapes();
                    }
                    Doodle s = picked.shape;
                    if (sMode == S_CREATE) { if (s.isLine()) { float[] b = level(s.x1, s.y1, x, y); s.x2 = b[0]; s.y2 = b[1]; } else { s.x2 = ux; s.y2 = uy; } }
                    else if (sMode == S_MOVE) {
                        if (free == null) { if (Math.hypot(x - startX, y - startY) <= dp(4)) return true; free = s.copy(); }
                        free.offset((x - lastX) * u, (y - lastY) * u);
                        float cx = (free.x1 + free.x2) / 2f, cy = (free.y1 + free.y2) / 2f, midU = UNIT / 2f, midV = area.height() * u / 2f, near = dp(10) * u;
                        boolean sx = Math.abs(cx - midU) < near, sy = Math.abs(cy - midV) < near;
                        if ((sx && !snapX) || (sy && !snapY)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        snapX = sx; snapY = sy;
                        float ox = sx ? midU - cx : 0, oy = sy ? midV - cy : 0;
                        s.x1 = free.x1 + ox; s.x2 = free.x2 + ox; s.y1 = free.y1 + oy; s.y2 = free.y2 + oy;
                    } else if (sMode == S_HANDLE) {
                        if (s.isLine()) { float[] b = sHandle == 0 ? level(s.x2, s.y2, x, y) : level(s.x1, s.y1, x, y); if (sHandle == 0) { s.x1 = b[0]; s.y1 = b[1]; } else { s.x2 = b[0]; s.y2 = b[1]; } }
                        else { float[] box = Doodle.dragBoxHandle(screen(s), sHandle, x, y); s.x1 = (box[0] - area.left) * u; s.y1 = (box[1] - area.top) * u; s.x2 = (box[2] - area.left) * u; s.y2 = (box[3] - area.top) * u; sHandle = Doodle.handleAt(false, screen(s), x, y, Float.MAX_VALUE); }
                    }
                    lastX = x; lastY = y; invalidate(); return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (sMode == S_NONE) return false;
                    if (sMode == S_PENDING) {
                        if (sHad) picked = null;
                        else if (area.contains(x, y)) {
                            boolean line = Doodle.isLine(ed.tool); float half = dp(line ? 64 : 56) * u, tall = line ? 0 : (ed.tool == Doodle.RECT || ed.tool == Doodle.BUBBLE ? half * 0.75f : half);
                            picked = shapeAt(ux - half, uy - tall, ux + half, uy + tall); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); dirty();
                        }
                    } else if (sMode == S_CREATE || sMode == S_HANDLE || free != null) dirty();
                    sMode = S_NONE; free = null; snapX = snapY = snapLevel = snapUp = false; ed.paintShapes(); invalidate(); return true;
                }
                case MotionEvent.ACTION_CANCEL: if (sMode == S_NONE) return false; sMode = S_NONE; free = null; invalidate(); return true;
            }
            return false;
        }
        Mark hit(float x, float y) {
            List<Mark> l = list();
            for (int i = l.size() - 1; i >= 0; i--) { Mark m = l.get(i); if (m.kind == DOODLE || m.kind == SHAPE) continue; RectF r = bounds(m, area.width(), area.height()); r.inset(-dp(10), -dp(10)); if (r.contains(x - area.left, y - area.top)) return m; }
            return null;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY(), W = area.width(), H = area.height();
            float nx = (x - area.left) / W, ny = (y - area.top) / H;
            if (e.getPointerCount() >= 2) {
                float dx = e.getX(1) - e.getX(0), dy = e.getY(1) - e.getY(0), span = (float) Math.hypot(dx, dy), mx = (e.getX(0) + e.getX(1)) / 2f, my = (e.getY(0) + e.getY(1)) / 2f;
                if (!twin || had != e.getPointerCount() || e.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                    if (!twin && cur != null) { list().remove(cur); cur = null; }
                    twin = true; had = e.getPointerCount(); resizing = false; moved = true;
                    span0 = Math.max(1f, span); zoom0 = zoom; midX = mx; midY = my;
                    if (picked != null && picked.kind == TEXT) size0 = picked.size; else if (picked != null && picked.kind == SIGN) w0 = picked.w;
                    if (sMode == S_CREATE && picked != null) { list().remove(picked); picked = null; }
                    sMode = S_NONE; free = null;
                    if (picked != null && picked.kind == SHAPE) { pinchFrom = picked.shape.copy(); pinchMid = new float[]{(mx - area.left) * unit(), (my - area.top) * unit()}; }
                    if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP) had = -1;
                    return true;
                }
                float k = span / span0;
                if (picked != null && picked.kind == SHAPE && pinchFrom != null) {
                    Doodle s = picked.shape; s.x1 = pinchFrom.x1; s.y1 = pinchFrom.y1; s.x2 = pinchFrom.x2; s.y2 = pinchFrom.y2;
                    s.scale(k, pinchMid[0], pinchMid[1]); s.offset((mx - area.left) * unit() - pinchMid[0], (my - area.top) * unit() - pinchMid[1]);
                    invalidate(); return true;
                }
                if (picked != null && picked.kind == TEXT) { picked.size = Math.max(0.012f, Math.min(0.3f, size0 * k)); ed.size = picked.size; invalidate(); return true; }
                if (picked != null && picked.kind == SIGN && picked.sign != null) { picked.w = Math.max(0.08f, Math.min(1f, w0 * k)); picked.h = picked.w * picked.sign.getHeight() / (float) picked.sign.getWidth() * W / H; invalidate(); return true; }
                float next = Math.max(1f, Math.min(6f, zoom0 * k));
                float fx = (mx - area.centerX()) / zoom, fy = (my - area.centerY()) / zoom;
                panX += (mx - midX) + fx * (zoom - next); panY += (my - midY) + fy * (zoom - next);
                zoom = next; midX = mx; midY = my;
                if (zoom <= 1.001f) { panX = 0; panY = 0; }
                show();
                return true;
            }
            if (twin) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { twin = false; had = 0; pinchFrom = null; sharpen(); if (picked != null) dirty(); invalidate(); }
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { startX = x; startY = y; moved = false; resizing = false; }
            if (shapeTouch(e, x, y)) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    startX = x; startY = y; moved = false; resizing = false;
                    Mark h = hit(x, y);
                    if (picked != null && (picked.kind == SIGN || picked.kind == TEXT)) { RectF r = bounds(picked, W, H); if (Math.hypot(x - area.left - r.right, y - area.top - r.bottom) < dp(28)) { resizing = true; size0 = picked.size; grab0 = (float) Math.max(dp(8), Math.hypot(x - area.left - r.left, y - area.top - r.top)); return true; } }
                    if (h != null) { picked = h; gx = nx - h.x; gy = ny - h.y; invalidate(); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); return true; }
                    picked = null;
                    if (ed.mode == DOODLE && ed.tool < 0 && area.contains(x, y)) { cur = new Mark(); cur.kind = DOODLE; cur.color = ed.color; cur.width = ed.width; cur.pts.add(new float[]{nx, ny}); list().add(cur); }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (Math.hypot(x - startX, y - startY) > dp(6)) moved = true;
                    if (resizing && picked != null && picked.kind == TEXT) { RectF r = bounds(picked, W, H); float now = (float) Math.hypot(x - area.left - r.left, y - area.top - r.top); picked.size = Math.max(0.012f, Math.min(0.3f, picked.size * now / Math.max(1f, (float) Math.hypot(r.width(), r.height())))); ed.size = picked.size; invalidate(); return true; }
                    if (resizing && picked != null && picked.sign != null) { picked.w = Math.max(0.08f, Math.min(1f, nx - picked.x)); picked.h = picked.w * picked.sign.getHeight() / (float) picked.sign.getWidth() * W / H; invalidate(); return true; }
                    if (picked != null) { picked.x = Math.max(-0.05f, Math.min(0.98f, nx - gx)); picked.y = Math.max(0.02f, Math.min(1.02f, ny - gy)); invalidate(); return true; }
                    if (cur != null) { for (int i = 0; i < e.getHistorySize(); i++) cur.pts.add(new float[]{(e.getHistoricalX(i) - area.left) / W, (e.getHistoricalY(i) - area.top) / H}); cur.pts.add(new float[]{nx, ny}); invalidate(); }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (cur != null) { cur = null; dirty(); return true; }
                    if (picked != null && !moved && !resizing) { menu(picked, x, y); return true; }
                    if (picked != null) { dirty(); return true; }
                    if (!moved && area.contains(x, y)) {
                        if (ed.mode == TEXT) typeText(null, nx, ny);
                        else if (ed.mode == SIGN) signature(new float[]{nx, ny});
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL: cur = null; return true;
            }
            return true;
        }
        void menu(final Mark m, float x, float y) {
            final LinearLayout box = new LinearLayout(PdfEdit.this); box.setGravity(Gravity.CENTER_VERTICAL);
            box.setBackground(PdfApp.round(card, dp(26))); box.setElevation(dp(12)); box.setPadding(dp(6), dp(6), dp(6), dp(6)); box.setClickable(true);
            final FrameLayout scrim = new FrameLayout(PdfEdit.this);
            scrim.setOnClickListener(v -> closeCard(scrim, box));
            if (m.kind == TEXT) addButton(box, "Edit", false, () -> { closeCard(scrim, box); typeText(m, m.x, m.y); });
            TextView del = addButton(box, "Delete", true, () -> { closeCard(scrim, box); list().remove(m); picked = null; invalidate(); dirty(); });
            del.setBackground(PdfApp.round(0xffe5484d, dp(20))); del.setTextColor(0xffffffff);
            for (int i = 0; i < box.getChildCount(); i++) { LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) box.getChildAt(i).getLayoutParams(); lp.height = dp(40); lp.setMarginStart(i == 0 ? 0 : dp(4)); box.getChildAt(i).setMinimumWidth(dp(72)); }
            box.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int bw = box.getMeasuredWidth(), bh = box.getMeasuredHeight();
            int[] me = new int[2], host = new int[2]; getLocationInWindow(me); root.getLocationInWindow(host);
            RectF r = bounds(m, area.width(), area.height()); r.offset(area.left + me[0] - host[0], area.top + me[1] - host[1]);
            float left = Math.max(dp(12), Math.min(root.getWidth() - bw - dp(12), r.centerX() - bw / 2f));
            float above = r.top - bh - dp(14), below = r.bottom + dp(14);
            float topY = above >= top + dp(64) ? above : Math.min(below, root.getHeight() - bottom - bh - dp(12));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2); lp.leftMargin = Math.round(left); lp.topMargin = Math.round(Math.max(top + dp(8), topY));
            scrim.addView(box, lp);
            root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            box.setPivotX(Math.max(0, Math.min(bw, r.centerX() - left))); box.setPivotY(topY == above ? bh : 0);
            SearchOverlay.bloomIn(box);
            cards.add(new View[]{scrim, box, scrim});
        }
    }

    private void openEditor(int index, int mode) {
        if (editor != null || pages == 0) return;
        editor = new Editor(index, mode);
        overlay.addView(editor.page, new FrameLayout.LayoutParams(-1, -1));
        editor.inset();
        editor.page.setPivotX(getResources().getDisplayMetrics().widthPixels / 2f); editor.page.setPivotY(getResources().getDisplayMetrics().heightPixels / 2f);
        SearchOverlay.bloomIn(editor.page);
        tools.animate().cancel();
        tools.animate().alpha(0).scaleX(0.6f).scaleY(0.6f).setDuration(140).withEndAction(() -> { if (editor != null) tools.setVisibility(View.GONE); }).start();
    }

    private void turnTo(int index, int by) {
        final Editor old = editor;
        if (old == null || index < 0 || index >= pages) return;
        Editor next = new Editor(index, old.mode);
        next.color = old.color; next.width = old.width; next.size = old.size;
        next.paintDots((LinearLayout) next.bar.findViewWithTag("colors")); next.paintThick((TextView) next.bar.findViewWithTag("thick"));
        editor = next;
        overlay.addView(next.page, new FrameLayout.LayoutParams(-1, -1));
        next.inset();
        float w = getResources().getDisplayMetrics().widthPixels;
        next.board.setTranslationX(by * w * 0.35f); next.board.setAlpha(0f);
        next.board.animate().translationX(0).alpha(1).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        overlay.removeView(old.page);
        adapter.notifyDataSetChanged();
        list.setSelection(index);
    }

    private static final class ShapeGlyph extends android.graphics.drawable.Drawable {
        final int kind, color; final boolean fill; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ShapeGlyph(int kind, boolean fill, int color) { this.kind = kind; this.fill = fill; this.color = color; }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds(); float s = Math.min(b.width(), b.height()), w = Math.max(2f, s / 11f), l = b.centerX() - s / 2f + w, t = b.centerY() - s / 2f + w, r = l + s - 2 * w, bt = t + s - 2 * w;
            if (kind < 0) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeWidth(w); p.setColor(color);
                Path q = new Path(); float h = bt - t, ww = r - l; q.moveTo(l, t + h * 0.72f); q.cubicTo(l + ww * 0.2f, t - h * 0.1f, l + ww * 0.42f, t + h * 0.1f, l + ww * 0.5f, t + h * 0.55f); q.cubicTo(l + ww * 0.58f, t + h * 1.02f, l + ww * 0.82f, t + h * 0.95f, r, t + h * 0.3f);
                c.drawPath(q, p); return;
            }
            if (kind == 100) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeWidth(w); p.setColor(color);
                float h = bt - t, ww = r - l; Path q = new Path();
                q.moveTo(l, t + h * 0.2f); q.lineTo(r, t + h * 0.2f);
                q.moveTo(l + ww * 0.36f, t + h * 0.2f); q.lineTo(l + ww * 0.36f, t); q.lineTo(l + ww * 0.64f, t); q.lineTo(l + ww * 0.64f, t + h * 0.2f);
                q.moveTo(l + ww * 0.14f, t + h * 0.2f); q.lineTo(l + ww * 0.22f, bt); q.lineTo(l + ww * 0.78f, bt); q.lineTo(l + ww * 0.86f, t + h * 0.2f);
                c.drawPath(q, p); return;
            }
            boolean line = Doodle.isLine(kind);
            new Doodle(kind, l, line ? bt : t, r, line ? t : bt, color, w, fill).draw(c, p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static final class Chevron extends android.graphics.drawable.Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final int way;
        Chevron(int way, int color) { this.way = way; p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(color); }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float u = Math.min(b.width(), b.height()) / 20f, cx = b.exactCenterX(), cy = b.exactCenterY();
            p.setStrokeWidth(2.2f * u);
            Path path = new Path();
            path.moveTo(cx - way * 3.5f * u, cy - 7 * u); path.lineTo(cx + way * 3.5f * u, cy); path.lineTo(cx - way * 3.5f * u, cy + 7 * u);
            c.drawPath(path, p);
        }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void closeEditor() {
        final Editor e = editor;
        if (e == null) return;
        editor = null;
        dirty();
        e.page.animate().alpha(0).scaleX(0.94f).scaleY(0.94f).setDuration(180).withEndAction(() -> overlay.removeView(e.page)).start();
        tools.animate().cancel();
        tools.setVisibility(View.VISIBLE);
        tools.animate().alpha(1).scaleX(1).scaleY(1).setDuration(380).setInterpolator(new SearchOverlay.Spring(0.55f)).start();
    }

    private void typeText(final Mark existing, final float nx, final float ny) {
        final LinearLayout box = cardBox(existing == null ? "Add Text" : "Edit");
        final EditText e = new EditText(this);
        e.setTextColor(ink); e.setHintTextColor(ink2); e.setBackground(PdfApp.round(chip, dp(16))); e.setPadding(dp(16), dp(12), dp(16), dp(12));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); e.setMaxLines(5);
        if (existing != null) e.setText(existing.text);
        e.setHint(I18n.cs("Type here"));
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = buttons(box);
        final FrameLayout scrim = showCard(box, true);
        addButton(row, "Cancel", false, () -> closeCard(scrim, box));
        addButton(row, existing == null ? "Add" : "Save", true, () -> {
            String s = e.getText().toString().trim();
            closeCard(scrim, box);
            if (s.isEmpty() || editor == null) return;
            Mark m = existing;
            if (m == null) { m = new Mark(); m.kind = TEXT; m.color = editor.color; m.size = editor.size; m.x = nx; m.y = ny; editor.board.list().add(m); }
            m.text = s;
            editor.board.picked = m; editor.board.invalidate(); dirty();
        });
        e.requestFocus(); e.setSelection(e.length());
        main.postDelayed(() -> { try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(e, 0); } catch (Throwable ignored) { } }, 160);
    }

    private File signFile() { return new File(getFilesDir(), "signature.png"); }

    private void signature(final float[] at) {
        final Bitmap saved = signFile().exists() ? BitmapFactory.decodeFile(signFile().getPath()) : null;
        if (at != null && saved != null) { place(saved, at); return; }
        final LinearLayout box = cardBox("Signature");
        final Pad pad = new Pad();
        box.setClipToOutline(true);
        int w = Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(420));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(w, Math.round(w * 0.62f));
        pl.setMargins(-dp(24), 0, -dp(24), 0);
        box.addView(pad, pl);
        LinearLayout row = buttons(box);
        final FrameLayout scrim = showCard(box, true);
        addButton(row, "Clear", false, () -> { pad.clear(); });
        addButton(row, "Use", true, () -> {
            Bitmap b = pad.result();
            if (b == null) { toast("Sign inside the box first"); return; }
            closeCard(scrim, box);
            try { FileOutputStream o = new FileOutputStream(signFile()); b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close(); } catch (Throwable ignored) { }
            place(b, at == null ? new float[]{0.5f, 0.6f} : at);
        });
    }

    private void place(Bitmap b, float[] at) {
        if (editor == null) return;
        Mark m = new Mark(); m.kind = SIGN; m.sign = b;
        float pr = ratios[editor.index];
        m.w = 0.34f; m.h = m.w * b.getHeight() / (float) b.getWidth() / pr;
        m.x = Math.max(0, Math.min(1 - m.w, at[0] - m.w / 2)); m.y = Math.max(0, Math.min(1 - m.h, at[1] - m.h / 2));
        editor.board.list().add(m); editor.board.picked = m; editor.board.invalidate(); dirty();
    }

    private final class Pad extends View {
        final ArrayList<ArrayList<float[]>> lines = new ArrayList<ArrayList<float[]>>(); final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Pad() { super(PdfEdit.this); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(dark ? 0xffdfe6ff : 0xff0b1f5c); }
        void clear() { lines.clear(); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG); guide.setColor(dark ? 0x33ffffff : 0x22000000); guide.setStrokeWidth(dp(1.2f));
            guide.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(6), dp(5)}, 0));
            c.drawLine(dp(28), getHeight() * 0.72f, getWidth() - dp(28), getHeight() * 0.72f, guide);
            p.setStrokeWidth(dp(3));
            for (ArrayList<float[]> l : lines) c.drawPath(path(l, 1, 0, 0), p);
        }
        Path path(ArrayList<float[]> l, float k, float ox, float oy) {
            Path path = new Path();
            for (int i = 0; i < l.size(); i++) { float[] q = l.get(i); float x = (q[0] - ox) * k, y = (q[1] - oy) * k; if (i == 0) path.moveTo(x, y); else { float[] o = l.get(i - 1); path.quadTo((o[0] - ox) * k, (o[1] - oy) * k, ((o[0] + q[0]) / 2 - ox) * k, ((o[1] + q[1]) / 2 - oy) * k); } }
            return path;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) lines.add(new ArrayList<float[]>());
            if (lines.isEmpty()) return true;
            ArrayList<float[]> l = lines.get(lines.size() - 1);
            for (int i = 0; i < e.getHistorySize(); i++) l.add(new float[]{e.getHistoricalX(i), e.getHistoricalY(i)});
            l.add(new float[]{e.getX(), e.getY()});
            invalidate();
            return true;
        }
        Bitmap result() {
            RectF r = new RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE); int n = 0;
            for (ArrayList<float[]> l : lines) for (float[] q : l) { r.union(q[0], q[1]); n++; }
            if (n < 4) return null;
            float pad = dp(6), k = 3f;
            r.inset(-pad, -pad);
            Bitmap b = Bitmap.createBitmap(Math.max(1, Math.round(r.width() * k)), Math.max(1, Math.round(r.height() * k)), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint q = new Paint(p); q.setStrokeWidth(dp(3) * k); q.setColor(0xff0b1f5c);
            for (ArrayList<float[]> l : lines) c.drawPath(path(l, k, r.left, r.top), q);
            return b;
        }
    }

    private LinearLayout cardBox(String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(PdfApp.round(card, dp(30))); box.setElevation(dp(24)); box.setPadding(dp(24), dp(20), dp(24), dp(14));
        box.setClickable(true);
        if (title != null) { TextView t = text(title, 20, ink, true); t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); t.setPadding(0, 0, 0, dp(12)); box.addView(t); }
        return box;
    }

    private LinearLayout buttons(LinearLayout box) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); row.setPadding(0, dp(14), 0, 0);
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView addButton(LinearLayout row, String label, boolean main, Runnable run) {
        TextView b = text(label, 15, main ? onAccent : accent, true);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(20), 0, dp(20), 0); b.setMinWidth(dp(84));
        if (main) b.setBackground(PdfApp.round(accent, dp(22)));
        PdfApp.press(b);
        b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44)); lp.setMarginStart(dp(8));
        row.addView(b, lp);
        return b;
    }

    private final ArrayList<View[]> cards = new ArrayList<View[]>();

    private FrameLayout showCard(final LinearLayout box, boolean cancel) {
        final FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(dark ? 0x99000000 : 0x5a000000);
        if (cancel) scrim.setOnClickListener(v -> closeCard(scrim, box)); else scrim.setClickable(true);
        scrim.addView(box, new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(420)), -2, Gravity.CENTER));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        scrim.setAlpha(0); scrim.animate().alpha(1).setDuration(180).start();
        SearchOverlay.bloomIn(box);
        cards.add(new View[]{scrim, box, cancel ? scrim : null});
        return scrim;
    }

    private void closeCard(final FrameLayout scrim, View box) {
        for (int i = cards.size() - 1; i >= 0; i--) if (cards.get(i)[0] == scrim) cards.remove(i);
        try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(box.getWindowToken(), 0); } catch (Throwable ignored) { }
        scrim.setClickable(false);
        scrim.animate().alpha(0).setDuration(200).start();
        SearchOverlay.bloomOut(box, () -> root.removeView(scrim));
        scrim.postDelayed(() -> root.removeView(scrim), 420);
    }

    private void saveAll() {
        if (editor != null) closeEditor();
        final LinearLayout box = cardBox("Saving");
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent)); bar.setIndeterminate(true);
        box.addView(bar, new LinearLayout.LayoutParams(-1, dp(8)));
        final FrameLayout scrim = showCard(box, false);
        final Map<Integer, List<Mark>> snap = new HashMap<Integer, List<Mark>>(marks);
        bg.post(() -> {
            File out = null;
            try {
                synchronized (lock) { if (renderer != null) { renderer.close(); renderer = null; } }
                out = PdfKit.edited(file, " (edited)", (i, p) -> {
                    Bitmap b = PdfKit.render(p, 170, 3000);
                    List<Mark> l = snap.get(i);
                    if (l != null && !l.isEmpty()) { Canvas c = new Canvas(b); draw(c, b.getWidth(), b.getHeight(), l, null); }
                    return b;
                }, (done, total) -> main.post(() -> { bar.setIndeterminate(false); bar.setMax(total); bar.setProgress(done); }));
                PdfKit.scan(this, out);
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "pdf: save", t); }
            final File made = out;
            main.post(() -> {
                closeCard(scrim, box);
                if (made == null) { shareNext = false; toast("Couldn't save the PDF"); load(null); return; }
                marks.clear(); cache.evictAll();
                file = made; title.setText(PdfKit.base(made)); save.setVisibility(View.GONE);
                load(null);
                toast("Saved to Pixel PDF");
                if (shareNext) { shareNext = false; share(made); }
            });
        });
    }

    private boolean shareNext;

    private void share(File f) {
        try {
            Uri u = PdfHost.uri(f.getAbsolutePath());
            Intent i = new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("", u));
            startActivity(Intent.createChooser(i, I18n.cs("Share")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Throwable t) { toast("Couldn't open it"); }
    }

    @Override public void onBackPressed() {
        if (!cards.isEmpty()) { View[] c = cards.get(cards.size() - 1); if (c[2] != null) closeCard((FrameLayout) c[0], c[1]); return; }
        if (editor != null) { closeEditor(); return; }
        if (save.getVisibility() == View.VISIBLE) {
            final LinearLayout box = cardBox("Leave without saving?");
            TextView n = text("Your changes will be lost.", 15, ink2, false); box.addView(n);
            LinearLayout row = buttons(box);
            final FrameLayout scrim = showCard(box, true);
            addButton(row, "Leave", false, () -> { closeCard(scrim, box); finish(); });
            addButton(row, "Save", true, () -> { closeCard(scrim, box); saveAll(); });
            return;
        }
        super.onBackPressed();
    }
}
