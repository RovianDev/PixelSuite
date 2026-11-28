package io.github.pixelsuite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PixelSearchPage implements Choreographer.FrameCallback {
    static final int PICK_CONTACT = 7312;

    interface Host { boolean blocked(String key); void failed(); }

    interface Lazy { Drawable load() throws Exception; }

    static final class Item { String label, key; Drawable icon; Runnable run; PixelSearch.Slot slot; Lazy lazy; }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ExecutorService PICTURES = Executors.newFixedThreadPool(2);

    private final Activity a;
    private final Host host;
    private final int text, text2, accent, onAccent, chip;
    private final boolean dark;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HashMap<String, Integer> want = new HashMap<String, Integer>();
    private Preview preview;
    private LinearLayout lookBox, scopeBox;
    private boolean ticking;
    private long last;
    TextView indexSummary;

    PixelSearchPage(Activity a, Host host, int text, int text2, int accent, int onAccent, boolean dark) {
        this.a = a; this.host = host; this.text = text; this.text2 = text2; this.accent = accent; this.onAccent = onAccent; this.dark = dark;
        chip = dark ? 0x24ffffff : 0x12000000;
        PixelSearch.LIVE.clear();
        PixelSearch.TEXT.clear();
    }

    int dp(float v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    private TextView label(String s, float size, int color, boolean bold) { return plain(I18n.cs(s), size, color, bold); }

    private TextView plain(CharSequence s, float size, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private void toast(String s) { AppToast.makeText(a, I18n.cs(s), Toast.LENGTH_SHORT).show(); }

    private Drawable ripple(int radius) {
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(radius);
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(PixelSearch.alpha(text, 14)), null, mask);
    }

    void refresh() {
        show();
        fillPins();
        indexStatus();
    }

    private void show() {
        if (preview != null) preview.reload();
        if (lookBox != null) fillLook();
        if (scopeBox != null && !ordering) fillScopes();
        if (closeBox != null) markClosing();
    }

    private boolean apply(final String key, final String value) {
        if (host.blocked(key)) return false;
        PixelSearch.TEXT.put(key, value);
        show();
        IO.execute(() -> {
            final boolean ok = Root.run("settings put secure " + key + " '" + value.replace("'", "") + "'") != null;
            main.post(() -> {
                if (value.equals(PixelSearch.TEXT.get(key))) PixelSearch.TEXT.remove(key);
                if (a.isFinishing() || a.isDestroyed()) return;
                if (!ok) host.failed();
                show();
            });
        });
        return true;
    }

    private void live(final String key, final int value) {
        PixelSearch.LIVE.put(key, value);
        if (preview != null) preview.reload();
        synchronized (want) { boolean busy = want.containsKey(key); want.put(key, value); if (busy) return; }
        IO.execute(() -> {
            boolean ok = true;
            while (true) {
                int v;
                synchronized (want) { v = want.get(key); }
                ok = Root.run("settings put secure " + key + " " + v) != null;
                synchronized (want) { if (!ok || want.get(key) == v) { want.remove(key); break; } }
            }
            final boolean saved = ok;
            main.post(() -> {
                synchronized (want) { if (want.containsKey(key)) return; }
                PixelSearch.LIVE.remove(key);
                if (!saved && !a.isFinishing() && !a.isDestroyed()) { host.failed(); show(); }
            });
        });
    }

    View preview() {
        preview = new Preview(a);
        return preview;
    }

    final class Preview extends FrameLayout {
        final SearchDock dock;
        final SearchDrag drag;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), dots = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clip = new Path(), glass = new Path();
        final RectF box = new RectF(), shell = new RectF();
        boolean held;

        Preview(Context c) {
            super(c);
            setWillNotDraw(false);
            dock = new SearchDock(c, true);
            dock.editing = true;
            LayoutParams lp = new LayoutParams(-1, dp(86), Gravity.BOTTOM);
            lp.leftMargin = dp(12); lp.rightMargin = dp(12); lp.bottomMargin = dp(8);
            addView(dock, lp);
            hint = new Hint(c);
            LayoutParams hl = new LayoutParams(-1, dp(46), Gravity.TOP); hl.topMargin = dp(2);
            addView(hint, hl);
            setContentDescription(I18n.cs("Preview"));
            drag = new SearchDrag(dock, this, new SearchDrag.Host() {
                public boolean allowed() { return !host.blocked(PixelSearch.SLOTS_KEY); }
                public void changed(String slots) { PixelSearch.TEXT.put(PixelSearch.SLOTS_KEY, slots); dock.reload(); }
                public void dropped(String slots) {
                    if (slots == null) { PixelSearch.TEXT.remove(PixelSearch.SLOTS_KEY); dock.reload(); }
                    else apply(PixelSearch.SLOTS_KEY, slots);
                }
            });
        }

        void reload() { dock.reload(); invalidate(); }

        Hint hint;

        final class Hint extends View implements Runnable {
            private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final String words = I18n.t("Hold to edit");
            private final android.graphics.Matrix slide = new android.graphics.Matrix();
            private final Path pill = new Path();
            private final RectF shape = new RectF();
            private android.graphics.LinearGradient glow;
            private GlassRim rim;
            private boolean rimDark;
            float fog; long at;
            private float drawn = -1f;
            private java.lang.reflect.Method blur, effect;
            private boolean noBlur, running;

            Hint(Context c) {
                super(c);
                ink.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                ink.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, c.getResources().getDisplayMetrics()));
                ink.setTextAlign(Paint.Align.CENTER);
                ink.setLetterSpacing(0.03f);
                setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            }

            void wake() { if (!running && isAttachedToWindow()) { running = true; at = 0; postOnAnimation(this); } }

            @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); wake(); }
            @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); running = false; removeCallbacks(this); }
            @Override protected void onVisibilityChanged(View v, int vis) { super.onVisibilityChanged(v, vis); if (vis == VISIBLE) wake(); }

            @Override public void run() {
                if (!isAttachedToWindow() || !Preview.this.isShown()) { running = false; return; }
                long now = android.os.SystemClock.uptimeMillis();
                float dt = at == 0 ? 0f : Math.min(0.05f, (now - at) / 1000f);
                at = now;
                float want = dock.wiggling() ? 1f : 0f;
                if (fog != want) fog = want > fog ? Math.min(1f, fog + dt / 0.42f) : Math.max(0f, fog - dt / 0.6f);
                mist(fog);
                if (fog < 1f) invalidate();
                postOnAnimation(this);
            }

            private void mist(float f) {
                if (f == drawn) return;
                drawn = f;
                float e = f * f * (3 - 2 * f);
                setAlpha(1f - e);
                setScaleX(1f + 0.2f * e); setScaleY(1f + 0.3f * e);
                setTranslationY(-dp(7) * e);
                if (noBlur || android.os.Build.VERSION.SDK_INT < 31) return;
                try {
                    if (effect == null) {
                        Class<?> fx = Class.forName("android.graphics.RenderEffect");
                        blur = fx.getMethod("createBlurEffect", float.class, float.class, android.graphics.Shader.TileMode.class);
                        effect = View.class.getMethod("setRenderEffect", fx);
                    }
                    float r = dp(11) * e;
                    effect.invoke(this, r < 0.5f ? null : blur.invoke(null, r, r * 0.7f, android.graphics.Shader.TileMode.CLAMP));
                } catch (Throwable t) { noBlur = true; }
            }

            @Override protected void onDraw(Canvas c) {
                long now = android.os.SystemClock.uptimeMillis();
                PixelSearch.Look look = dock.look();
                boolean night = look != null && look.dark;
                int card = look != null ? look.card : 0xffffffff, text = look != null ? look.text : 0xff1b1b1f;
                float w = getWidth(), mid = getHeight() / 2f, base = mid - (ink.descent() + ink.ascent()) / 2f;
                float half = ink.measureText(words) / 2f + dp(15), tall = dp(15), r = tall;
                shape.set(w / 2f - half, mid - tall, w / 2f + half, mid + tall);
                pill.reset(); pill.addRoundRect(shape, r, r, Path.Direction.CW);
                int s = c.save();
                c.clipPath(pill);
                c.translate(-getLeft(), -getTop());
                wallpaper(c, true);
                c.restoreToCount(s);
                fill.setColor((card & 0xffffff) | (night ? 0x8c000000 : 0x99000000));
                c.drawRoundRect(shape, r, r, fill);
                if (rim == null || rimDark != night) { rim = new GlassRim(r, dp(1.2f), night); rimDark = night; }
                rim.draw(c, shape.left, shape.top, shape.right, shape.bottom, r, 170);
                float breathe = 0.5f + 0.5f * (float) Math.sin(now / 900.0);
                ink.setShader(null); ink.setColor(text); ink.setAlpha(Math.round(255 * (0.74f + 0.14f * breathe)));
                c.drawText(words, w / 2f, base, ink);
                if (glow == null) glow = new android.graphics.LinearGradient(0, 0, dp(80), 0, new int[]{0x00ffffff, 0xffffffff, 0x00ffffff}, new float[]{0f, 0.5f, 1f}, android.graphics.Shader.TileMode.CLAMP);
                float span = half * 2f + dp(160), run = (now % 2800L) / 2800f;
                float x = w / 2f - span / 2f + span * Math.min(1f, run / 0.55f) - dp(40);
                slide.setTranslate(x, 0); glow.setLocalMatrix(slide);
                int g = c.save(); c.clipPath(pill);
                fill.setShader(glow); fill.setColor(0xffffffff); fill.setAlpha(night ? 46 : 120);
                c.drawRoundRect(shape, r, r, fill);
                fill.setShader(null);
                c.restoreToCount(g);
            }
        }

        private boolean pressed, fired, strayed;
        private float pressX, pressY;
        private final Runnable hold = new Runnable() {
            public void run() {
                if (!pressed || strayed || drag.active()) return;
                fired = true;
                dock.pulse();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                dock.wiggle(!dock.wiggling());
                invalidate();
            }
        };

        private Object[] pinDown;
        private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SearchGlyph pinGlyph;

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN && dock.wiggling()) {
                pinDown = null;
                int[] at = new int[2]; getLocationOnScreen(at);
                for (Object[] pin : dock.pins()) {
                    float px = (Float) pin[1] - at[0], py = (Float) pin[2] - at[1] - dp(15);
                    if (Math.abs(e.getX() - px) < dp(20) && Math.abs(e.getY() - py) < dp(18)) { pinDown = pin; getParent().requestDisallowInterceptTouchEvent(true); return true; }
                }
            }
            if (pinDown != null) {
                if (act == MotionEvent.ACTION_UP) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    pinMenu((String) pinDown[0], (Float) pinDown[1], (Float) pinDown[2] - dp(26));
                }
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) pinDown = null;
                return true;
            }
            if (act == MotionEvent.ACTION_DOWN) {
                held = drag.down(e);
                pressed = held || e.getY() >= dock.getTop();
                fired = false; strayed = false; pressX = e.getX(); pressY = e.getY();
                removeCallbacks(hold);
                if (pressed) postDelayed(hold, 420);
                if (held) getParent().requestDisallowInterceptTouchEvent(true);
                return pressed;
            }
            if (!pressed) return false;
            if (act == MotionEvent.ACTION_MOVE) {
                if (Math.hypot(e.getX() - pressX, e.getY() - pressY) > dp(10)) { strayed = true; removeCallbacks(hold); }
                if (held) drag.move(e);
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                removeCallbacks(hold);
                boolean tap = act == MotionEvent.ACTION_UP && !fired && !strayed && !drag.active();
                if (held) { held = false; drag.up(act == MotionEvent.ACTION_UP); }
                if (tap) {
                    float[] spot = new float[2];
                    dock.toLocal(e.getRawX(), e.getRawY(), spot);
                    String empty = dock.emptyAt(spot[0], spot[1]);
                    float[] at = empty == null ? null : dock.spot(empty);
                    if (at != null) { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); kindsMenu(empty, at[0], at[1] - dp(4)); }
                    else if (dock.wiggling()) { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); dock.wiggle(false); }
                }
                pressed = false;
                invalidate();
            }
            return true;
        }

        private final android.graphics.Bitmap[] kept = new android.graphics.Bitmap[2];

        private void wallpaper(Canvas c, boolean soft) {
            int w = getWidth(), h = getHeight(), slot = soft ? 1 : 0;
            if (w <= 0 || h <= 0) return;
            android.graphics.Bitmap b = kept[slot];
            if (b == null || b.getWidth() != w || b.getHeight() != h) {
                try {
                    b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    paintWallpaper(new Canvas(b), soft);
                    kept[slot] = b;
                } catch (Throwable t) { kept[slot] = null; paintWallpaper(c, soft); return; }
            }
            c.drawBitmap(b, 0, 0, null);
        }

        private void paintWallpaper(Canvas c, boolean soft) {
            float scale = getWidth() / 100f;
            int s = c.save();
            c.scale(scale, scale);
            c.translate(0, -Math.max(0, (210f - getHeight() / scale) / 2f));
            FeedPreview.drawWallpaper(c, paint, soft);
            c.restoreToCount(s);
        }

        @Override protected void dispatchDraw(Canvas c) {
            box.set(0, 0, getWidth(), getHeight());
            clip.reset(); clip.addRoundRect(box, dp(28), dp(28), Path.Direction.CW);
            int s = c.save();
            c.clipPath(clip);
            wallpaper(c, false);
            PixelSearch.Look look = dock.look();
            if (look != null && look.glass && !dock.shell().isEmpty()) {
                shell.set(dock.shell()); shell.offset(dock.getLeft(), dock.getTop());
                glass.reset(); glass.addRoundRect(shell, shell.height() / 2, shell.height() / 2, Path.Direction.CW);
                int g = c.save(); c.clipPath(glass); wallpaper(c, true); c.restoreToCount(g);
            }
            if (hint != null) hint.wake();
            super.dispatchDraw(c);
            drag.draw(c);
            if (dock.wiggling() && !drag.active() && look != null) {
                if (pinGlyph == null) pinGlyph = new SearchGlyph(SearchGlyph.PIN, look.text);
                pinGlyph.setColor(look.text);
                int[] at = new int[2]; getLocationOnScreen(at);
                float lift = (float) Math.sin(android.os.SystemClock.uptimeMillis() / 260.0) * dp(1.5f);
                for (Object[] pin : dock.pins()) {
                    float px = (Float) pin[1] - at[0], py = (Float) pin[2] - at[1] - dp(15) + lift;
                    pinPaint.setColor(look.card); pinPaint.setShadowLayer(dp(4), 0, dp(1), 0x40000000);
                    c.drawCircle(px, py, dp(11), pinPaint);
                    pinGlyph.setBounds(Math.round(px - dp(8)), Math.round(py - dp(8)), Math.round(px + dp(8)), Math.round(py + dp(8)));
                    pinGlyph.draw(c);
                }
                postInvalidateOnAnimation();
            }
            c.restoreToCount(s);
        }
    }

    private String replaceKey;
    private FrameLayout menuVeil;

    boolean back() {
        if (menuVeil != null) { closeMenu(); return true; }
        if (preview != null && preview.dock.wiggling()) { preview.dock.wiggle(false); preview.invalidate(); return true; }
        return false;
    }

    private void closeMenu() {
        final FrameLayout veil = menuVeil;
        if (veil == null) return;
        menuVeil = null;
        veil.setClickable(false);
        SearchOverlay.bloomOut(veil.getChildAt(0), () -> { if (veil.getParent() instanceof ViewGroup) ((ViewGroup) veil.getParent()).removeView(veil); });
    }

    private void menu(float screenX, float screenY, String[] names, String[] glyphs, final Runnable[] runs) {
        closeMenu();
        final ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        final FrameLayout veil = new FrameLayout(a);
        veil.setOnClickListener(v -> closeMenu());
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(dark ? 0xff2a2c31 : 0xffffffff); bg.setCornerRadius(dp(24));
        card.setBackground(AppSurfaces.role(a, bg, 24, SuperDarkPalette.BACKGROUND, true));
        card.setElevation(dp(10));
        card.setClickable(true);
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(11), dp(16), dp(11));
            row.setBackground(ripple(dp(16)));
            if (glyphs != null) {
                ImageView icon = new ImageView(a);
                icon.setImageDrawable(new SearchGlyph(glyphs[i], text));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20)); ip.setMarginEnd(dp(12));
                row.addView(icon, ip);
            }
            row.addView(label(names[i], 15, text, true));
            row.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); closeMenu(); runs[which].run(); });
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        int[] base = new int[2]; decor.getLocationOnScreen(base);
        final int w = dp(190);
        float x = screenX - base[0], y = screenY - base[1];
        card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, -2, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.max(dp(8), Math.min(decor.getWidth() - w - dp(8), Math.round(x - w / 2f)));
        lp.topMargin = Math.max(dp(8), Math.round(y - dp(8) - card.getMeasuredHeight()));
        veil.addView(card, lp);
        decor.addView(veil, new ViewGroup.LayoutParams(-1, -1));
        menuVeil = veil;
        card.setPivotX(x - lp.leftMargin); card.setPivotY(card.getMeasuredHeight());
        SearchOverlay.bloomIn(card);
    }

    private void kindsMenu(final String key, float screenX, float screenY) {
        if (host.blocked(PixelSearch.SLOTS_KEY)) return;
        final String[] kinds = {"action", "app", "shortcut", "contact"};
        Runnable[] runs = new Runnable[4];
        for (int i = 0; i < 4; i++) { final String kind = kinds[i]; runs[i] = () -> { replaceKey = key; openKind(kind); }; }
        menu(screenX, screenY, new String[]{"Action", "App", "App shortcut", "Contact"}, null, runs);
    }

    private void pinMenu(final String key, float screenX, float screenY) {
        if (host.blocked(PixelSearch.SLOTS_KEY)) return;
        final boolean first = "lead".equals(key);
        menu(screenX, screenY, new String[]{first ? "Reset" : "Hide", "Replace"}, new String[]{SearchGlyph.CLEAR, SearchGlyph.REFRESH}, new Runnable[]{
            () -> {
                if (first) { apply(PixelSearch.LEAD_KEY, ""); return; }
                List<PixelSearch.Slot> list = SearchDrag.grouped(PixelSearch.slots(a));
                for (int i = 0; i < list.size(); i++) if (SearchDock.key(list.get(i)).equals(key)) {
                    list.set(i, new PixelSearch.Slot(list.get(i).side, "empty", Long.toString(System.nanoTime(), 36), ""));
                    apply(PixelSearch.SLOTS_KEY, PixelSearch.encode(list));
                    return;
                }
            },
            () -> { float[] at = first ? null : preview.dock.spot(key); kindsMenu(key, screenX, at != null ? at[1] - dp(4) : screenY); }
        });
    }

    private LinearLayout strip(LinearLayout into) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, dp(2), 0, dp(2));
        hs.addView(line);
        into.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        return line;
    }

    private TextView chip(LinearLayout line, CharSequence name, boolean on, final Runnable run) {
        TextView t = plain(name, 14, on ? onAccent : text, true);
        t.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable d = new GradientDrawable(); d.setCornerRadius(dp(20)); d.setColor(on ? accent : chip);
        t.setBackground(d);
        t.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); run.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(dp(8));
        line.addView(t, lp);
        return t;
    }

    private TextView heading(LinearLayout into, String name, boolean first) {
        TextView t = label(name, 14, text2, true);
        t.setPadding(dp(2), first ? 0 : dp(14), dp(2), dp(8));
        into.addView(t);
        return t;
    }

    private LinearLayout box() {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(18), dp(16), dp(18), dp(16));
        return b;
    }

    final class Fold {
        final FrameLayout box; final ScrollView scroll; final LinearLayout rows;
        String kind; float grow, speed, to; int height, built; List<Item> items;
        Fold(LinearLayout into) {
            box = new FrameLayout(a);
            scroll = new ScrollView(a) {
                @Override public boolean dispatchTouchEvent(MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) { touchY = e.getRawY(); if (carried == null && (canScrollVertically(1) || canScrollVertically(-1))) getParent().requestDisallowInterceptTouchEvent(true); }
                    else if (act == MotionEvent.ACTION_MOVE && carried == null) {
                        float d = e.getRawY() - touchY;
                        if (Math.abs(d) > dp(6) && !canScrollVertically(d < 0 ? 1 : -1)) getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return super.dispatchTouchEvent(e);
                }
            };
            scroll.setVerticalScrollBarEnabled(false);
            rows = new LinearLayout(a);
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setPadding(0, dp(10), 0, 0);
            scroll.addView(rows, new FrameLayout.LayoutParams(-1, -2));
            box.addView(scroll, new FrameLayout.LayoutParams(-1, -2));
            into.addView(box, new LinearLayout.LayoutParams(-1, 0));
            folds.add(this);
        }
    }

    private final List<Fold> folds = new ArrayList<Fold>();
    private Fold buttons, money;
    private List<Item> allMoney = new ArrayList<Item>();
    private String popStar;
    private float touchY;
    private Boolean shutState;
    private SearchLock lock;
    private final ArrayList<OpacitySlider> sliderList = new ArrayList<OpacitySlider>();
    private LinearLayout moneyBox;
    private Item carried;
    private Drawable ghost;
    private String carryBase, carryHad, carryValue;
    private final float[] spot = new float[2];
    private static final HashMap<String, Drawable> APP_ICONS = new HashMap<String, Drawable>();
    private static List<ResolveInfo> sLaunchable;
    private static long sLaunchableAt;

    View tray() {
        LinearLayout b = box();
        heading(b, "Add a button", true);
        LinearLayout kinds = strip(b);
        chip(kinds, I18n.cs("Action"), false, () -> toggle("action"));
        chip(kinds, I18n.cs("App"), false, () -> toggle("app"));
        chip(kinds, I18n.cs("App shortcut"), false, () -> toggle("shortcut"));
        chip(kinds, I18n.cs("Contact"), false, () -> toggle("contact"));
        buttons = new Fold(b);
        TextView hint = label("Tap a button to add it, or hold it and drag it onto the bar. Drag the buttons on the bar to move them, or off the bar to remove them.", 13, text2, false);
        hint.setPadding(dp(2), dp(12), dp(2), 0);
        b.addView(hint);
        return b;
    }

    private void toggle(String kind) {
        if (host.blocked(PixelSearch.SLOTS_KEY)) return;
        replaceKey = null;
        if (kind.equals(buttons.kind)) { fold(buttons); return; }
        openKind(kind);
    }

    private void openKind(String kind) {
        if ("action".equals(kind)) {
            ArrayList<Item> items = new ArrayList<Item>();
            for (int i = 0; i < PixelSearch.ACTIONS.length; i++) {
                final String id = PixelSearch.ACTIONS[i];
                if ("scan".equals(id) && !PixelSearch.scanOn(a)) continue;
                items.add(slot(I18n.t(PixelSearch.ACTION_NAMES[i]), new SearchGlyph(id, text), new PixelSearch.Slot('I', "action", id, "")));
            }
            unfold(buttons, kind, items);
        } else if ("contact".equals(kind)) contacts();
        else apps(kind);
    }

    private Item item(String label, Drawable icon, Runnable run) { Item i = new Item(); i.label = label; i.icon = icon; i.run = run; return i; }

    private Item slot(String label, Drawable icon, final PixelSearch.Slot slot) { Item i = item(label, icon, () -> add(slot)); i.slot = slot; return i; }

    private void apps(final String kind) {
        IO.execute(() -> {
            final ArrayList<Item> items = new ArrayList<Item>();
            final PackageManager pm = a.getPackageManager();
            try {
                List<ResolveInfo> found = sLaunchable;
                if (found == null || System.currentTimeMillis() - sLaunchableAt > 60000) {
                    found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
                    sLaunchable = found; sLaunchableAt = System.currentTimeMillis();
                }
                for (final ResolveInfo r : found) {
                    final String name = String.valueOf(r.loadLabel(pm));
                    final ComponentName cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
                    if ("pin".equals(kind) && pinKeys().contains(cn.flattenToString())) continue;
                    Item i = "shortcut".equals(kind) ? item(name, null, () -> shortcuts(cn.getPackageName(), name)) : "pin".equals(kind) ? item(name, null, () -> pin(cn.flattenToString())) : slot(name, null, new PixelSearch.Slot('I', "app", cn.flattenToString(), name));
                    i.key = cn.flattenToString();
                    i.lazy = () -> r.loadIcon(pm);
                    items.add(i);
                }
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "search page: apps", t); }
            Collections.sort(items, (x, y) -> x.label.compareToIgnoreCase(y.label));
            main.post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                unfold("pin".equals(kind) ? pinFold : buttons, kind, items);
                if ("pin".equals(kind)) { pinAll = items; fillPins(); if (pinKeys().size() >= 4) seek(true); }
            });
        });
    }

    private static final String PINS_KEY = "pixelsuite_search_featured_pins";
    private LinearLayout pinLine;
    private Fold pinFold;
    private List<Item> pinAll;
    private android.widget.EditText pinFind;

    private void closePins() {
        boolean open = pinFold != null && "pin".equals(pinFold.kind);
        fold(pinFold);
        pinAll = null;
        if (pinFind != null && pinFind.getVisibility() == View.VISIBLE) {
            try { ((android.view.inputmethod.InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pinFind.getWindowToken(), 0); } catch (Throwable ignored) { }
            pinFind.setVisibility(View.GONE);
            if (pinFind.length() > 0) pinFind.setText("");
        }
        if (open) fillPins();
    }

    private void seek(boolean show) {
        if (pinFind == null) return;
        if (!show) { pinFind.setVisibility(View.GONE); return; }
        if (pinFind.getVisibility() == View.VISIBLE) { pinFind.requestFocus(); return; }
        pinFind.setVisibility(View.VISIBLE);
        pinFind.setAlpha(0f); pinFind.setTranslationY(-dp(8));
        pinFind.animate().alpha(1f).translationY(0).setDuration(240).start();
        pinFind.requestFocus();
        main.postDelayed(() -> { try { ((android.view.inputmethod.InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(pinFind, 0); } catch (Throwable ignored) { } }, 120);
    }

    private void sift(String q) {
        final Fold f = pinFold;
        if (f == null || pinAll == null || !"pin".equals(f.kind)) return;
        String want = q.trim().toLowerCase(java.util.Locale.ROOT);
        List<Item> out = new ArrayList<Item>();
        for (Item i : pinAll) if (want.isEmpty() || (i.label != null && i.label.toLowerCase(java.util.Locale.ROOT).contains(want))) out.add(i);
        f.items = out; f.built = 0;
        f.rows.removeAllViews();
        more(f, 12);
        f.scroll.scrollTo(0, 0);
        f.height = Math.max(dp(60), Math.min(dp(10) + out.size() * dp(50), dp(300)));
        f.scroll.getLayoutParams().height = f.height;
        f.scroll.requestLayout();
        tick();
    }
    private String pinFresh;

    private List<String> pinKeys() {
        List<String> out = new ArrayList<String>();
        String raw = PixelSearch.string(a, PINS_KEY);
        if (raw != null) for (String x : raw.split("\n")) { String t = x.trim(); if (!t.isEmpty() && !out.contains(t)) out.add(t); }
        return out;
    }

    private boolean savePins(List<String> keys) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keys.size() && i < 5; i++) { if (i > 0) b.append('\n'); b.append(keys.get(i)); }
        return apply(PINS_KEY, b.toString());
    }

    private void pin(String key) {
        List<String> now = pinKeys();
        if (now.contains(key) || now.size() >= 5) { closePins(); return; }
        now.add(key);
        if (!savePins(now)) return;
        pinFresh = key;
        fold(pinFold);
        fillPins();
    }

    View pins() {
        LinearLayout b = box();
        heading(b, "Pinned apps", true);
        TextView hint = label("Up to 5 apps that always come first in Featured apps. Tap an app to unpin it.", 13, text2, false);
        hint.setPadding(dp(2), 0, dp(2), dp(10));
        b.addView(hint);
        pinLine = new LinearLayout(a);
        pinLine.setClipChildren(false);
        b.setClipChildren(false);
        b.addView(pinLine, new LinearLayout.LayoutParams(-1, -2));
        pinFind = new android.widget.EditText(a);
        pinFind.setHint(I18n.cs("Search apps")); pinFind.setSingleLine(true); pinFind.setTextColor(text); pinFind.setHintTextColor(text2);
        pinFind.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable fb = new GradientDrawable(); fb.setColor(chip); fb.setCornerRadius(dp(22));
        pinFind.setBackground(fb); pinFind.setPadding(dp(16), dp(11), dp(16), dp(11));
        pinFind.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pinFind.setVisibility(View.GONE);
        pinFind.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence q, int x, int y, int z) { }
            public void onTextChanged(CharSequence q, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable q) { sift(q.toString()); }
        });
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2); fl.setMargins(dp(2), dp(8), dp(2), dp(4));
        b.addView(pinFind, fl);
        pinFold = new Fold(b);
        fillPins();
        return b;
    }

    private void fillPins() {
        if (pinLine == null) return;
        pinLine.removeAllViews();
        final PackageManager pm = a.getPackageManager();
        final List<String> keys = pinKeys();
        int shown = 0;
        for (final String key : keys) {
            final ComponentName cn = ComponentName.unflattenFromString(key);
            Drawable icon = null; CharSequence name = null;
            try { android.content.pm.ActivityInfo info = pm.getActivityInfo(cn, 0); icon = info.loadIcon(pm); name = info.loadLabel(pm); } catch (Throwable ignored) { }
            if (icon == null || name == null) continue;
            final LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(6), 0, dp(6)); cell.setBackground(ripple(dp(16))); cell.setClipChildren(false);
            FrameLayout holder = new FrameLayout(a);
            holder.setClipChildren(false);
            ImageView iv = new ImageView(a); iv.setImageDrawable(icon);
            holder.addView(iv, new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER));
            final ImageView badge = new ImageView(a);
            badge.setImageDrawable(new SearchGlyph(SearchGlyph.PIN, onAccent));
            badge.setPadding(dp(3), dp(3), dp(3), dp(3));
            GradientDrawable disc = new GradientDrawable(); disc.setShape(GradientDrawable.OVAL); disc.setColor(accent);
            badge.setBackground(disc); badge.setRotation(40);
            holder.addView(badge, new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.TOP | Gravity.END));
            cell.addView(holder, new LinearLayout.LayoutParams(dp(54), dp(46)));
            TextView label = plain(name, 12, text, false);
            label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER);
            label.setPadding(dp(4), dp(6), dp(4), 0);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            cell.setContentDescription(I18n.cs("Pinned") + ", " + name);
            cell.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                List<String> left = pinKeys();
                left.remove(key);
                if (!savePins(left)) return;
                cell.animate().scaleX(0.4f).scaleY(0.4f).alpha(0f).setDuration(170).withEndAction(this::fillPins).start();
            });
            pinLine.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            if (key.equals(pinFresh)) {
                pinFresh = null;
                cell.setScaleX(0.5f); cell.setScaleY(0.5f); cell.setAlpha(0f);
                cell.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(460).setInterpolator(new SearchOverlay.Spring(0.5f)).start();
                badge.setScaleX(0f); badge.setScaleY(0f);
                badge.animate().scaleX(1f).scaleY(1f).setStartDelay(180).setDuration(520).setInterpolator(new SearchOverlay.Spring(0.42f)).start();
            }
            shown++;
        }
        if (shown < 5) {
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(6), 0, dp(6)); cell.setBackground(ripple(dp(16)));
            ImageView plus = new ImageView(a);
            plus.setImageDrawable(new SearchGlyph(SearchGlyph.PLUS, text));
            plus.setPadding(dp(11), dp(11), dp(11), dp(11));
            GradientDrawable ring = new GradientDrawable(); ring.setShape(GradientDrawable.OVAL); ring.setColor(chip);
            plus.setBackground(ring);
            cell.addView(plus, new LinearLayout.LayoutParams(dp(46), dp(46)));
            TextView label = label("Add", 12, text2, false);
            label.setGravity(Gravity.CENTER); label.setPadding(dp(4), dp(6), dp(4), 0);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            cell.setContentDescription(I18n.cs("Add"));
            cell.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (host.blocked(PINS_KEY)) return;
                if ("pin".equals(pinFold.kind)) closePins(); else apps("pin");
            });
            pinLine.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            shown++;
        }
        if (shown < 5 && pinFold != null && "pin".equals(pinFold.kind)) {
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(6), 0, dp(6)); cell.setBackground(ripple(dp(16)));
            ImageView glass = new ImageView(a);
            glass.setImageDrawable(new SearchGlyph(SearchGlyph.SEARCH, text));
            glass.setPadding(dp(11), dp(11), dp(11), dp(11));
            GradientDrawable ring = new GradientDrawable(); ring.setShape(GradientDrawable.OVAL); ring.setColor(chip);
            glass.setBackground(ring);
            cell.addView(glass, new LinearLayout.LayoutParams(dp(46), dp(46)));
            TextView label = label("Search", 12, text2, false);
            label.setGravity(Gravity.CENTER); label.setPadding(dp(4), dp(6), dp(4), 0);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            cell.setContentDescription(I18n.cs("Search apps"));
            cell.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); seek(true); });
            cell.setScaleX(0.5f); cell.setScaleY(0.5f); cell.setAlpha(0f);
            cell.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420).setInterpolator(new SearchOverlay.Spring(0.5f)).start();
            pinLine.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            shown++;
        }
        for (int i = shown; i < 5; i++) pinLine.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
    }

    private void shortcuts(final String pkg, final String app) {
        IO.execute(() -> {
            SearchProvider.forgetShortcuts(pkg);
            boolean ok = Root.run("settings put secure " + PixelSearch.ASK_KEY + " '" + pkg + "|" + Long.toString(System.nanoTime(), 36) + "'") != null;
            final List<String[]> found = ok ? SearchProvider.waitShortcuts(pkg, 3500) : null;
            final boolean rooted = ok;
            main.post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (!rooted) { host.failed(); return; }
                if (found == null) { toast("Go to your home screen once, then try again."); return; }
                if (found.isEmpty()) { toast("This app has no shortcuts"); return; }
                ArrayList<Item> items = new ArrayList<Item>();
                for (final String[] f : found) items.add(slot(f[1], null, new PixelSearch.Slot('I', "shortcut", pkg + "/" + f[0], f[1])));
                unfold(buttons, "shortcut:" + pkg, items);
            });
        });
    }

    private List<String[]> ask(String q) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        try (Cursor cur = a.getContentResolver().query(android.net.Uri.parse("content://" + PixelSearch.AUTHORITY + "/q"), null, null, new String[]{q, "contacts"}, null)) {
            while (cur != null && cur.moveToNext() && out.size() < 40) { String number = cur.getString(4); if (number != null && !number.isEmpty()) out.add(new String[]{cur.getString(3), cur.getString(1), number}); }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search page: contacts", t); }
        return out;
    }

    private void keep(List<String[]> more) {
        List<String[]> list = PixelSearch.hearts(a);
        for (String[] f : more) { boolean had = false; for (String[] x : list) if (x[0].equals(f[0]) && x[2].equals(f[2])) had = true; if (!had) list.add(f); }
        if (!apply(PixelSearch.HEARTS_KEY, PixelSearch.encodeHearts(list))) return;
        boolean button = false;
        for (PixelSearch.Slot x : PixelSearch.slots(a)) if ("action".equals(x.type) && "hearts".equals(x.value)) button = true;
        toast("Added to favorite contacts");
        if (!button) add(new PixelSearch.Slot('I', "action", "hearts", ""));
    }

    private View person(String[] f, int size) {
        ImageView face = new ImageView(a);
        face.setImageDrawable(new SearchDock.Avatar(a, new PixelSearch.Slot('I', "contact", "call/" + f[0] + "/" + f[2], f[1]), text));
        face.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return face;
    }

    private void contacts() {
        if (host.blocked(PixelSearch.SLOTS_KEY)) return;
        final Fold f = buttons;
        f.kind = "contact"; f.items = new ArrayList<Item>(); f.built = 0;
        f.rows.removeAllViews();
        f.scroll.scrollTo(0, 0);
        f.height = dp(330);
        f.scroll.getLayoutParams().height = f.height;
        f.grow = Math.min(f.grow, 0.55f); f.speed = 0; f.to = 1;
        final LinearLayout page = f.rows;
        final android.widget.EditText find = new android.widget.EditText(a);
        find.setHint(I18n.cs("Search contacts")); find.setSingleLine(true); find.setTextColor(text); find.setHintTextColor(text2);
        find.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable fb = new GradientDrawable(); fb.setColor(chip); fb.setCornerRadius(dp(22));
        find.setBackground(fb); find.setPadding(dp(16), dp(11), dp(16), dp(11));
        find.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2); fp.setMargins(dp(6), dp(8), dp(6), 0);
        page.addView(find, fp);
        final LinearLayout starRow = new LinearLayout(a);
        starRow.setGravity(Gravity.CENTER_VERTICAL);
        starRow.setPadding(dp(12), dp(14), dp(12), dp(10));
        ImageView heart = new ImageView(a);
        heart.setImageDrawable(new SearchGlyph(SearchGlyph.HEART, accent));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(22), dp(22)); hp.setMarginEnd(dp(12));
        starRow.addView(heart, hp);
        starRow.addView(label("Add dialer favorites", 15, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        final LinearLayout faces = new LinearLayout(a);
        starRow.addView(faces);
        page.addView(starRow);
        final LinearLayout results = new LinearLayout(a);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setMinimumHeight(dp(150));
        page.addView(results);
        final List<String[]> starred = new ArrayList<String[]>();
        final Runnable[] fill = new Runnable[1];
        final String[] wanted = {""};
        fill[0] = () -> {
            final String q = find.getText().toString().trim();
            wanted[0] = q;
            IO.execute(() -> {
                final List<String[]> found = q.isEmpty() ? new ArrayList<String[]>(starred) : ask(q);
                main.post(() -> {
                    if (!q.equals(wanted[0]) || !"contact".equals(f.kind) || results.getParent() != page || a.isFinishing() || a.isDestroyed()) return;
                    results.removeAllViews();
                    int n = 0;
                    for (final String[] one : found) {
                        LinearLayout row = new LinearLayout(a);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(12), dp(7), dp(12), dp(7));
                        row.setBackground(ripple(dp(18)));
                        row.addView(person(one, 36));
                        LinearLayout names = new LinearLayout(a);
                        names.setOrientation(LinearLayout.VERTICAL);
                        names.setPadding(dp(12), 0, 0, 0);
                        TextView name = plain(one[1], 15, text, true); name.setSingleLine(true);
                        names.addView(name);
                        names.addView(plain(one[2], 12, text2, false));
                        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                        row.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); ArrayList<String[]> picked = new ArrayList<String[]>(); picked.add(one); keep(picked); });
                        row.setAlpha(0f); row.setTranslationY(dp(8));
                        row.animate().alpha(1).translationY(0).setStartDelay(Math.min(n++, 8) * 30).setDuration(260).start();
                        results.addView(row);
                    }
                });
            });
        };
        IO.execute(() -> {
            final List<String[]> got = ask(PixelSearch.STARRED);
            main.post(() -> {
                if (results.getParent() != page || a.isFinishing() || a.isDestroyed()) return;
                starred.addAll(got);
                for (int i = 0; i < Math.min(4, got.size()); i++) { View face = person(got.get(i), 26); ((LinearLayout.LayoutParams) face.getLayoutParams()).setMarginStart(i == 0 ? 0 : -dp(8)); faces.addView(face); }
                fill[0].run();
            });
        });
        starRow.setBackground(ripple(dp(18)));
        starRow.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (starred.isEmpty()) { toast("No favorites in your dialer yet"); return; }
            keep(starred);
        });
        find.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence q, int x, int y, int z) { }
            public void onTextChanged(CharSequence q, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable q) { fill[0].run(); }
        });
        tick();
    }


    void picked(int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        String id = null, name = null, number = null;
        try (Cursor c = a.getContentResolver().query(data.getData(), new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (c != null && c.moveToFirst()) { id = String.valueOf(c.getLong(0)); name = c.getString(1); number = c.getString(2); }
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "search page: contact", t); }
        if (number == null || number.isEmpty()) { toast("This contact has no phone number"); return; }
        contact(id, name == null ? number : name, number);
    }

    private void contact(final String fid, final String fname, final String fnumber) {
        ArrayList<Item> items = new ArrayList<Item>();
        items.add(slot(I18n.t("Call") + "  ·  " + fname, new SearchGlyph(SearchGlyph.CALL, text), new PixelSearch.Slot('I', "contact", "call/" + fid + "/" + fnumber, fname)));
        items.add(slot(I18n.t("Message") + "  ·  " + fname, new SearchGlyph(SearchGlyph.MESSAGE, text), new PixelSearch.Slot('I', "contact", "sms/" + fid + "/" + fnumber, fname)));
        if (SearchOverlay.installed(a, "com.whatsapp") || SearchOverlay.installed(a, "com.whatsapp.w4b"))
            items.add(slot("WhatsApp  ·  " + fname, new SearchGlyph(SearchGlyph.CHAT, text), new PixelSearch.Slot('I', "contact", "whatsapp/" + fid + "/" + fnumber, fname)));
        unfold(buttons, "contact:" + fid, items);
    }

    private void add(PixelSearch.Slot slot) {
        List<PixelSearch.Slot> list = SearchDrag.grouped(PixelSearch.slots(a));
        String into = replaceKey;
        replaceKey = null;
        if ("lead".equals(into)) {
            slot.side = 'I';
            if (apply(PixelSearch.LEAD_KEY, slot.encode())) fold(buttons);
            return;
        }
        if (SearchDock.spare(into)) {
            char want = SearchDock.spareSide(into);
            if (PixelSearch.count(list, want) < PixelSearch.max(want)) {
                slot.side = want;
                list.add(slot);
                if (apply(PixelSearch.SLOTS_KEY, PixelSearch.encode(SearchDrag.grouped(list)))) fold(buttons);
                return;
            }
        } else if (into != null) {
            for (int i = 0; i < list.size(); i++) if (SearchDock.key(list.get(i)).equals(into)) {
                slot.side = list.get(i).side;
                list.set(i, slot);
                if (apply(PixelSearch.SLOTS_KEY, PixelSearch.encode(list))) fold(buttons);
                return;
            }
        }
        char side = 0;
        for (char s : new char[]{'I', 'R', 'L'}) if (side == 0 && PixelSearch.count(list, s) < PixelSearch.max(s)) side = s;
        if (side == 0) { toast("The bar is full. Drag a button off the bar first."); return; }
        slot.side = side;
        list.add(slot);
        if (apply(PixelSearch.SLOTS_KEY, PixelSearch.encode(SearchDrag.grouped(list)))) fold(buttons);
    }

    private View row(final Item item) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(6), dp(8));
        row.setMinimumHeight(dp(50));
        row.setBackground(ripple(dp(16)));
        if (item.icon != null || item.lazy != null) {
            final ImageView icon = new ImageView(a);
            Drawable ready = item.icon != null ? item.icon : APP_ICONS.get(item.key);
            if (ready != null) { icon.setImageDrawable(ready); item.icon = ready; }
            else PICTURES.execute(() -> {
                Drawable d = null;
                try { d = item.lazy.load(); } catch (Throwable ignored) { }
                final Drawable done = d;
                main.post(() -> { if (done == null) return; APP_ICONS.put(item.key, done); item.icon = done; icon.setImageDrawable(done); icon.setAlpha(0f); icon.animate().alpha(1).setDuration(160).start(); });
            });
            if (item.icon instanceof SearchGlyph) icon.setPadding(dp(5), dp(5), dp(5), dp(5));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(34), dp(34));
            ip.setMarginEnd(dp(14));
            row.addView(icon, ip);
        }
        TextView name = plain(item.label, 16, text, false);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); item.run.run(); });
        if (item.slot != null) {
            row.setOnLongClickListener(v -> lift(item, v));
            row.setOnTouchListener((v, e) -> carry(e));
        }
        return row;
    }

    private boolean lift(Item item, View v) {
        if (host.blocked(PixelSearch.SLOTS_KEY) || preview == null) return true;
        carried = item;
        carryHad = PixelSearch.TEXT.get(PixelSearch.SLOTS_KEY);
        carryBase = PixelSearch.encode(SearchDrag.grouped(PixelSearch.slots(a)));
        carryValue = null;
        final Drawable picture = item.icon;
        final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        disc.setColor(chip); disc.setShadowLayer(dp(8), 0, dp(3), 0x40000000);
        ghost = new Drawable() {
            @Override public void draw(Canvas c) {
                android.graphics.Rect b = getBounds();
                c.drawCircle(b.exactCenterX(), b.exactCenterY(), b.width() / 2f, disc);
                if (picture == null) return;
                android.graphics.Rect old = picture.copyBounds();
                int in = picture instanceof SearchGlyph ? b.width() / 4 : b.width() / 7;
                picture.setBounds(b.left + in, b.top + in, b.right - in, b.bottom - in);
                picture.draw(c);
                picture.setBounds(old);
            }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
        a.getWindow().getDecorView().getOverlay().add(ghost);
        v.getParent().requestDisallowInterceptTouchEvent(true);
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        return true;
    }

    private void restoreSlots() {
        if (carryHad == null) PixelSearch.TEXT.remove(PixelSearch.SLOTS_KEY); else PixelSearch.TEXT.put(PixelSearch.SLOTS_KEY, carryHad);
    }

    private boolean carry(MotionEvent e) {
        if (carried == null) return false;
        int act = e.getActionMasked();
        View decor = a.getWindow().getDecorView();
        if (act == MotionEvent.ACTION_MOVE) {
            int[] at = new int[2]; decor.getLocationOnScreen(at);
            int size = dp(58), x = Math.round(e.getRawX() - at[0]), y = Math.round(e.getRawY() - at[1]) - dp(34);
            ghost.setBounds(x - size / 2, y - size / 2, x + size / 2, y + size / 2);
            decor.invalidate();
            int[] pv = new int[2]; preview.getLocationOnScreen(pv);
            float fy = e.getRawY() - dp(34);
            boolean over = fy >= pv[1] && fy <= pv[1] + preview.getHeight() + dp(20) && e.getRawX() >= pv[0] && e.getRawX() <= pv[0] + preview.getWidth();
            String next = null;
            if (over) {
                SearchDock dock = preview.dock;
                dock.toLocal(e.getRawX(), fy, spot);
                PixelSearch.TEXT.put(PixelSearch.SLOTS_KEY, carryBase);
                List<PixelSearch.Slot> list = SearchDrag.grouped(PixelSearch.slots(a));
                char side = dock.sideAt(spot[0]);
                if (PixelSearch.count(list, side) >= PixelSearch.max(side)) { side = 0; for (char s : new char[]{'I', 'R', 'L'}) if (side == 0 && PixelSearch.count(list, s) < PixelSearch.max(s)) side = s; }
                if (side != 0) {
                    carried.slot.side = side;
                    int index = dock.indexAt(side, spot[0], SearchDock.key(carried.slot)), put = 0, seen = 0;
                    for (; put < list.size(); put++) {
                        char s = list.get(put).side;
                        if (rank(s) > rank(side)) break;
                        if (s == side) { if (seen == index) break; seen++; }
                    }
                    list.add(put, carried.slot);
                    next = PixelSearch.encode(list);
                }
            }
            if (next == null ? carryValue != null : !next.equals(carryValue)) {
                carryValue = next;
                if (next == null) restoreSlots(); else PixelSearch.TEXT.put(PixelSearch.SLOTS_KEY, next);
                preview.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                preview.reload();
            } else if (next == null) restoreSlots(); else PixelSearch.TEXT.put(PixelSearch.SLOTS_KEY, next);
            return true;
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            decor.getOverlay().remove(ghost);
            ghost = null; carried = null;
            String value = act == MotionEvent.ACTION_UP ? carryValue : null;
            carryValue = null;
            restoreSlots();
            if (value != null) { if (apply(PixelSearch.SLOTS_KEY, value)) fold(buttons); }
            else if (act == MotionEvent.ACTION_UP) { preview.reload(); toast("Drop it on the bar to add it"); }
            else preview.reload();
            return true;
        }
        return true;
    }

    private static int rank(char c) { return c == 'L' ? 0 : c == 'I' ? 1 : 2; }

    private void unfold(final Fold f, String kind, List<Item> items) {
        f.kind = kind;
        f.items = items;
        f.built = 0;
        f.rows.removeAllViews();
        more(f, 12);
        f.scroll.scrollTo(0, 0);
        f.height = Math.min(dp(10) + items.size() * dp(50), dp(300));
        f.scroll.getLayoutParams().height = f.height;
        f.grow = Math.min(f.grow, 0.55f); f.speed = 0; f.to = 1;
        tick();
    }

    private void more(final Fold f, int count) {
        final List<Item> items = f.items;
        if (items == null) return;
        int end = Math.min(items.size(), f.built + count);
        for (; f.built < end; f.built++) f.rows.addView(row(items.get(f.built)));
        if (f.built < items.size()) main.postDelayed(() -> { if (f.items == items && f.box.isAttachedToWindow()) more(f, 24); }, 60);
    }

    private void fold(Fold f) {
        if (f == null) return;
        f.kind = null;
        f.items = null;
        f.to = 0;
        tick();
    }

    private void tick() { if (ticking) return; ticking = true; last = 0; Choreographer.getInstance().postFrameCallback(this); }

    @Override public void doFrame(long nanos) {
        float dt = last == 0 ? 1 / 60f : Math.min(.05f, (nanos - last) / 1e9f); last = nanos;
        boolean busy = false;
        for (Fold f : folds) {
            if (f.grow == f.to && f.speed == 0) continue;
            float[] r = f.to > 0 ? WidgetPanel.spring(f.grow, f.speed, 1, 350, .8f, dt) : WidgetPanel.spring(f.grow, f.speed, 0, 520, .92f, dt);
            f.grow = r[0]; f.speed = r[1];
            boolean settled = Math.abs(f.grow - f.to) < .002f && Math.abs(f.speed) < .01f;
            if (settled) { f.grow = f.to; f.speed = 0; }
            if (!f.box.isAttachedToWindow()) { f.grow = f.to; f.speed = 0; continue; }
            ViewGroup.LayoutParams lp = f.box.getLayoutParams();
            lp.height = Math.max(0, Math.round(f.height * f.grow));
            f.box.setLayoutParams(lp);
            float shown = Math.max(0, Math.min(1, f.grow));
            for (int i = 0; i < Math.min(12, f.rows.getChildCount()); i++) {
                View v = f.rows.getChildAt(i);
                float al = Math.max(0, Math.min(1, shown * 1.6f - 0.2f - Math.min(i, 8) * 0.06f));
                v.setAlpha(al);
                v.setTranslationY((1 - al) * dp(12));
            }
            if (settled) { if (f.to == 0) f.rows.removeAllViews(); } else busy = true;
        }
        if (!busy) { ticking = false; return; }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private LinearLayout closeBox;
    private final List<View[]> closeCards = new ArrayList<View[]>();

    View closing() {
        closeBox = box();
        heading(closeBox, "Closing animation", true);
        LinearLayout line = strip(closeBox);
        closeCards.clear();
        for (int i = 0; i < SearchClose.STYLES.length; i++) {
            final String value = SearchClose.STYLES[i];
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(4), dp(4), dp(4), dp(6));
            final SearchClose.Preview view = new SearchClose.Preview(a, value, dark, accent);
            card.addView(view, new LinearLayout.LayoutParams(dp(112), dp(86)));
            TextView name = label(SearchClose.NAMES[i], 13, text, true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(7), 0, 0);
            card.addView(name, new LinearLayout.LayoutParams(-1, -2));
            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                view.replay();
                if (!value.equals(SearchClose.style(a))) { v.setScaleX(0.9f); v.setScaleY(0.9f); v.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start(); }
                apply(SearchClose.KEY, value);
            });
            card.setScaleX(0.86f); card.setScaleY(0.86f);
            card.animate().scaleX(1f).scaleY(1f).setStartDelay(60 + i * 45L).setDuration(380).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(6));
            line.addView(card, lp);
            closeCards.add(new View[]{card, name});
        }
        markClosing();
        return closeBox;
    }

    private void markClosing() {
        String now = SearchClose.style(a);
        for (int i = 0; i < closeCards.size() && i < SearchClose.STYLES.length; i++) {
            boolean on = SearchClose.STYLES[i].equals(now);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(18));
            d.setColor(on ? PixelSearch.alpha(accent, 14) : Color.TRANSPARENT);
            d.setStroke(dp(2), on ? accent : Color.TRANSPARENT);
            closeCards.get(i)[0].setBackground(d);
            ((TextView) closeCards.get(i)[1]).setTextColor(on ? accent : text);
        }
    }

    View money() {
        moneyBox = box();
        fillMoney();
        IO.execute(() -> {
            if (!SearchMoney.auto(a) || PixelSearch.number(a, PixelSearch.KEY, 0) != 1 || System.currentTimeMillis() - SearchMoney.updatedAt(a) < 12 * 60 * 60 * 1000L) return;
            if (SearchMoney.update(a)) main.post(() -> { if (!a.isFinishing() && !a.isDestroyed() && money != null && money.kind == null) fillMoney(); });
        });
        return moneyBox;
    }

    private void fillMoney() {
        if (money != null) folds.remove(money);
        moneyBox.removeAllViews();
        heading(moneyBox, "Currency", true);
        final String home = SearchMoney.home(a);
        LinearLayout pick = new LinearLayout(a);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(20)); bg.setColor(chip);
        pick.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{bg, ripple(dp(20))}));
        TextView flag = plain(SearchMoney.flag(home), 22, text, false);
        pick.addView(flag);
        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, dp(8), 0);
        names.addView(label("Convert to", 12, text2, false));
        TextView name = plain(home + "  ·  " + SearchMoney.name(home), 16, text, true);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(name);
        pick.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        final android.widget.EditText find = new android.widget.EditText(a);
        find.setBackground(null); find.setSingleLine(true); find.setTextColor(text); find.setHintTextColor(text2);
        find.setHint(I18n.cs("Search currencies")); find.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); find.setPadding(0, 0, 0, 0);
        find.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        find.setVisibility(View.GONE);
        names.addView(find, new LinearLayout.LayoutParams(-1, -2));
        final GlassArrow arrow = new GlassArrow();
        arrow.setContentDescription(I18n.cs("Currency"));
        pick.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(34)));
        final ImageView glass = new ImageView(a);
        glass.setImageDrawable(new SearchGlyph(SearchGlyph.SEARCH, text));
        glass.setContentDescription(I18n.cs("Search currencies"));
        glass.setPadding(dp(6), dp(6), dp(6), dp(6));
        glass.setBackground(ripple(dp(18)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(36), dp(36));
        glp.setMarginStart(dp(6));
        pick.addView(glass, glp);
        final TextView shownName = name;
        final Runnable[] rest = new Runnable[1];
        final Runnable swap = () -> {
            final boolean open = money != null && money.kind != null;
            arrow.point(open);
            glass.animate().cancel();
            glass.setScaleX(0.72f); glass.setScaleY(0.72f); glass.setRotation(open ? -40 : 40);
            glass.animate().scaleX(1).scaleY(1).rotation(0).setDuration(420).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            if (!open) {
                find.setVisibility(View.GONE); shownName.setVisibility(View.VISIBLE); find.setText("");
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(find.getWindowToken(), 0);
            }
        };
        rest[0] = swap;
        arrow.setOnClickListener(v -> pick.performClick());
        glass.setOnClickListener(v -> {
            if (money == null || money.kind == null) { pick.performClick(); if (money == null || money.kind == null) return; }
            else v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            shownName.setVisibility(View.GONE); find.setVisibility(View.VISIBLE); find.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(find, 0);
        });
        find.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable s) {
                if (money == null || money.kind == null) return;
                String q = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                ArrayList<Item> items = new ArrayList<Item>();
                for (Item i : allMoney) if (q.isEmpty() || i.label.toLowerCase(java.util.Locale.ROOT).contains(q)) items.add(i);
                money.items = items; money.built = 0; money.rows.removeAllViews(); money.scroll.scrollTo(0, 0);
                more(money, 14);
            }
        });
        moneyBox.addView(pick);
        money = new Fold(moneyBox);
        pick.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (host.blocked(SearchMoney.CURRENCY_KEY)) return;
            if (money.kind != null) { fold(money); rest[0].run(); return; }
            ArrayList<Item> items = new ArrayList<Item>();
            for (final String code : SearchMoney.codes(a))
                items.add(item(SearchMoney.flag(code) + "   " + code + "  ·  " + SearchMoney.name(code), null, () -> { fold(money); main.postDelayed(() -> { if (apply(SearchMoney.CURRENCY_KEY, code)) fillMoney(); }, 220); }));
            allMoney = items;
            unfold(money, "currency", items);
            money.height = dp(300);
            money.scroll.getLayoutParams().height = money.height;
            rest[0].run();
        });
        LinearLayout line = strip(moneyBox);
        ((View) line.getParent()).setPadding(0, dp(12), 0, 0);
        final boolean auto = SearchMoney.auto(a);
        chip(line, I18n.cs("Update automatically"), auto, () -> {
            if (host.blocked(SearchMoney.AUTO_KEY)) return;
            PixelSearch.LIVE.put(SearchMoney.AUTO_KEY, auto ? 0 : 1);
            if (apply(SearchMoney.AUTO_KEY, auto ? "0" : "1")) fillMoney();
        });
        chip(line, I18n.cs("Update now"), false, () -> {
            if (host.blocked(SearchMoney.RATES_KEY)) return;
            toast("Updating the rates…");
            IO.execute(() -> {
                final boolean ok = SearchMoney.update(a);
                main.post(() -> { if (a.isFinishing() || a.isDestroyed()) return; toast(ok ? "Rates updated" : "Couldn't update the rates"); if (ok) fillMoney(); });
            });
        });
        long when = SearchMoney.updatedAt(a);
        String status = when <= 0 ? I18n.t("Rates not downloaded yet") : I18n.t("Rates updated {0}").replace("{0}", android.text.format.DateUtils.formatDateTime(a, when, android.text.format.DateUtils.FORMAT_SHOW_DATE | android.text.format.DateUtils.FORMAT_SHOW_TIME | android.text.format.DateUtils.FORMAT_ABBREV_MONTH));
        TextView note = plain(status, 13, text2, false);
        note.setPadding(dp(2), dp(12), dp(2), 0);
        moneyBox.addView(note);
        TextView how = label("Type an amount and a currency, like 200 USD, to convert it. Rates are downloaded from the internet, and nothing about you is sent.", 13, text2, false);
        how.setPadding(dp(2), dp(6), dp(2), 0);
        moneyBox.addView(how);
    }

    final class GlassArrow extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SearchGlyph glyph = new SearchGlyph(SearchGlyph.DOWN, text);
        private GlassRim rim;
        private float rimRadius = -1, turn;
        private android.animation.ValueAnimator spin;

        GlassArrow() { super(a); setClickable(true); }

        void point(boolean up) {
            float to = up ? 180 : 0;
            if (spin != null) spin.cancel();
            spin = android.animation.ValueAnimator.ofFloat(turn, to);
            spin.setDuration(460);
            spin.setInterpolator(new android.view.animation.OvershootInterpolator(2.2f));
            spin.addUpdateListener(x -> { turn = (Float) x.getAnimatedValue(); invalidate(); });
            spin.start();
            animate().cancel();
            setScaleX(0.78f); setScaleY(0.78f);
            android.animation.ValueAnimator wide = android.animation.ValueAnimator.ofFloat(0.78f, 1f), tall = android.animation.ValueAnimator.ofFloat(0.78f, 1f);
            wide.setDuration(380); wide.setInterpolator(new android.view.animation.OvershootInterpolator(2.6f));
            wide.addUpdateListener(x -> setScaleX((Float) x.getAnimatedValue()));
            tall.setDuration(420); tall.setStartDelay(70); tall.setInterpolator(new android.view.animation.OvershootInterpolator(2.6f));
            tall.addUpdateListener(x -> setScaleY((Float) x.getAnimatedValue()));
            wide.start(); tall.start();
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - dp(1);
            if (r <= 0) return;
            fill.setColor(dark ? 0x2effffff : 0xd9ffffff);
            c.drawCircle(cx, cy, r, fill);
            if (isPressed()) { fill.setColor(PixelSearch.alpha(text, 10)); c.drawCircle(cx, cy, r, fill); }
            if (rim == null || Math.abs(rimRadius - r) > .5f) { rim = new GlassRim(r, dp(1.3f), dark); rim.context = a; rimRadius = r; }
            rim.draw(c, cx - r, cy - r, cx + r, cy + r, r, 220);
            int half = dp(11);
            c.save();
            c.rotate(turn, cx, cy);
            glyph.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
            glyph.draw(c);
            c.restore();
        }

        @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }
    }

    View look() {
        lookBox = box();
        fillLook();
        return lookBox;
    }

    private void fillLook() {
        lookBox.removeAllViews();
        heading(lookBox, "Theme", true);
        LinearLayout themes = strip(lookBox);
        String theme = PixelSearch.theme(a);
        for (int i = 0; i < PixelSearch.THEMES.length; i++) {
            final String value = PixelSearch.THEMES[i];
            TextView t = chip(themes, I18n.cs(PixelSearch.THEME_NAMES[i]), value.equals(theme), () -> apply(PixelSearch.THEME_KEY, value));
            if (!"dark".equals(value) && !"black".equals(value)) continue;
            final boolean kept = value.equals(PixelSearch.darkChoice(a));
            if (kept) { SearchGlyph.badge(t, accent, value.equals(popStar)); popStar = null; }
            t.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                popStar = kept ? null : value;
                apply(PixelSearch.DARK_KEY, kept ? "" : value);
                return true;
            });
        }
        ChipFit.fit(themes, dp(16), dp(8));
        View holder = (View) themes.getParent();
        holder.setPadding(0, dp(24), 0, 0);
        ((ViewGroup) holder).setClipToPadding(false);
        ((ViewGroup) holder).setClipChildren(false);
        themes.setClipChildren(false);
        themes.setClipToPadding(false);
        TextView tip = label("Hold Dark or Super Dark to choose which one Automatic uses at night.", 13, text2, false);
        tip.setPadding(dp(2), dp(8), dp(2), 0);
        lookBox.addView(tip);
        heading(lookBox, "Color", false);
        LinearLayout colors = strip(lookBox);
        final String chosen = PixelSearch.colorChoice(a);
        float density = a.getResources().getDisplayMetrics().density;
        for (final String sys : PixelSearch.SYSTEM_COLORS) dot(colors, new SearchSwatch(PixelSearch.systemTone(a, sys), PixelSearch.systemShade(a, sys), sys.equals(chosen), text, density), "System colors", sys);
        boolean fixed = false;
        for (int color : PixelSearch.SWATCHES) {
            String hex = String.format(Locale.ROOT, "%06x", color & 0xffffff);
            fixed |= hex.equals(chosen);
            dot(colors, new SearchSwatch(color, 0, hex.equals(chosen), text, density), "Color", hex);
        }
        heading(lookBox, "Any color", false);
        final Hue hue = new Hue(a, PixelSearch.color(a), !fixed && !PixelSearch.systemColor(a));
        hue.done = () -> apply(PixelSearch.COLOR_KEY, String.format(Locale.ROOT, "%06x", hue.color() & 0xffffff));
        lookBox.addView(hue, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void dot(LinearLayout line, Drawable d, String description, final String value) {
        View v = new View(a);
        v.setBackground(d);
        v.setContentDescription(I18n.cs(description));
        v.setOnClickListener(x -> { x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); apply(PixelSearch.COLOR_KEY, value); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.setMarginEnd(dp(8));
        line.addView(v, lp);
    }

    static final class Hue extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF bar = new RectF();
        final boolean chosen;
        float hue; Runnable done;
        Hue(Context c, int start, boolean chosen) {
            super(c);
            this.chosen = chosen;
            float[] hsv = new float[3];
            Color.colorToHSV(start == 0 ? 0xff3f5f91 : start, hsv);
            hue = hsv[0];
            knob.setStyle(Paint.Style.STROKE);
            setContentDescription(I18n.cs("Any color"));
        }
        int color() { return Color.HSVToColor(new float[]{hue, 0.55f, 0.58f}); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float d = getResources().getDisplayMetrics().density;
            bar.set(14 * d, h / 2f - 8 * d, w - 14 * d, h / 2f + 8 * d);
            int[] colors = new int[13];
            for (int i = 0; i < 13; i++) colors[i] = Color.HSVToColor(new float[]{i * 30f % 360f, 0.55f, 0.58f});
            paint.setShader(new LinearGradient(bar.left, 0, bar.right, 0, colors, null, Shader.TileMode.CLAMP));
        }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, paint);
            if (!chosen && !isPressed()) return;
            float x = bar.left + bar.width() * hue / 360f;
            knob.setColor(Color.WHITE); knob.setStrokeWidth(3 * d);
            fill.setColor(color());
            c.drawCircle(x, getHeight() / 2f, 13 * d, fill);
            c.drawCircle(x, getHeight() / 2f, 13 * d, knob);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (bar.width() <= 0) return false;
            getParent().requestDisallowInterceptTouchEvent(true);
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_CANCEL) { setPressed(false); invalidate(); return true; }
            hue = Math.max(0, Math.min(359.9f, (e.getX() - bar.left) / bar.width() * 360f));
            setPressed(act != MotionEvent.ACTION_UP);
            invalidate();
            if (act == MotionEvent.ACTION_UP && done != null) done.run();
            return true;
        }
    }

    View sliders() {
        LinearLayout b = box();
        slider(b, "Width", PixelSearch.WIDTH_KEY, Math.round((PixelSearch.width(a) - 70) * 2.5f), 70, 0.4f, 75, true);
        slider(b, "Height", PixelSearch.HEIGHT_KEY, Math.round((PixelSearch.height(a) - 70) * 2.5f), 70, 0.4f, 75, false);
        slider(b, "Outer bar", PixelSearch.SHELL_KEY, PixelSearch.number(a, PixelSearch.SHELL_KEY, 100), 0, 1f, 100, false);
        slider(b, "Search box", PixelSearch.PILL_KEY, PixelSearch.number(a, PixelSearch.PILL_KEY, 100), 0, 1f, 100, false);
        return b;
    }

    private void slider(LinearLayout into, String title, final String key, final int shown, final float from, final float step, int mark, boolean first) {
        TextView t = label(title, 14, text2, true);
        t.setPadding(dp(2), first ? 0 : dp(10), dp(2), 0);
        if (shutState == null) shutState = PixelSearch.number(a, PixelSearch.LOCK_KEY, 0) == 1;
        final boolean shut = shutState;
        if (first) {
            sliderList.clear();
            LinearLayout head = new LinearLayout(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            lock = new SearchLock(a, text, shut);
            lock.setContentDescription(I18n.cs("Lock the sliders"));
            lock.setOnClickListener(v -> {
                final boolean next = !shutState;
                if (!apply(PixelSearch.LOCK_KEY, next ? "1" : "0")) return;
                shutState = next;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                ((SearchLock) v).set(next);
                for (OpacitySlider x : sliderList) x.lock(next, true);
            });
            head.addView(lock, new LinearLayout.LayoutParams(dp(34), dp(34)));
            into.addView(head);
        } else into.addView(t);
        final OpacitySlider s = new OpacitySlider(a, accent, dark ? 0x33ffffff : 0x22000000);
        s.lock(shut, false);
        s.refused = () -> { if (lock != null) lock.refuse(); };
        sliderList.add(s);
        s.show(shown, false);
        s.mark(mark);
        s.setContentDescription(I18n.cs(title));
        s.listen(percent -> {
            if (host.blocked(key)) { s.post(() -> s.show(shown, true)); return; }
            live(key, Math.max(1, Math.round(from + percent * step)));
        });
        into.addView(s, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private boolean ordering;

    View scopes() {
        scopeBox = box();
        fillScopes();
        return scopeBox;
    }

    private void fillScopes() {
        scopeBox.removeAllViews();
        heading(scopeBox, "What to search", true);
        TextView hint = label("Hold and drag to change the order.", 13, text2, false);
        hint.setPadding(dp(2), 0, dp(2), dp(6));
        scopeBox.addView(hint);
        final List<String> list = PixelSearch.scopes(a);
        final LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        scopeBox.setClipChildren(false);
        scopeBox.addView(rows);
        final int rowHeight = dp(54);
        for (int i = 0; i < list.size(); i++) {
            final String raw = list.get(i);
            final boolean on = !raw.startsWith("-");
            final String id = on ? raw : raw.substring(1);
            final LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView grip = new ImageView(a);
            grip.setImageDrawable(new SearchGlyph(SearchGlyph.GRIP, text2));
            grip.setPadding(dp(8), dp(15), dp(12), dp(15));
            grip.setContentDescription(I18n.cs("Hold and drag to change the order."));
            row.addView(grip, new LinearLayout.LayoutParams(dp(44), rowHeight));
            TextView name = label(PixelSearch.scopeName(id), 16, on ? text : text2, false);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Switch sw = new Switch(a);
            sw.setChecked(on);
            sw.setContentDescription(I18n.cs(PixelSearch.scopeName(id)));
            sw.setOnCheckedChangeListener((b, checked) -> {
                int at = list.indexOf(raw);
                if (at < 0) return;
                ArrayList<String> next = new ArrayList<String>(list);
                next.set(at, checked ? id : "-" + id);
                if (!apply(PixelSearch.SCOPES_KEY, PixelSearch.encodeScopes(next))) b.setChecked(!checked);
            });
            row.addView(sw);
            rows.addView(row, new LinearLayout.LayoutParams(-1, rowHeight));
            final int start = i;
            grip.setOnTouchListener(new View.OnTouchListener() {
                float downY; int now;
                public boolean onTouch(View v, MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) {
                        if (host.blocked(PixelSearch.SCOPES_KEY)) return false;
                        ordering = true;
                        downY = e.getRawY(); now = start;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        row.setElevation(dp(6)); row.setScaleX(1.02f); row.setScaleY(1.02f);
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } else if (act == MotionEvent.ACTION_MOVE) {
                        float dy = Math.max(-start * rowHeight, Math.min((list.size() - 1 - start) * rowHeight, e.getRawY() - downY));
                        row.setTranslationY(dy);
                        int to = start + Math.round(dy / rowHeight);
                        if (to != now) {
                            now = to;
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            for (int j = 0; j < rows.getChildCount(); j++) {
                                if (j == start) continue;
                                float shift = j > start && j <= to ? -rowHeight : j < start && j >= to ? rowHeight : 0;
                                rows.getChildAt(j).animate().translationY(shift).setDuration(160).start();
                            }
                        }
                    } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                        ordering = false;
                        if (act == MotionEvent.ACTION_UP && now != start) {
                            ArrayList<String> next = new ArrayList<String>(list);
                            next.add(now, next.remove(start));
                            if (!apply(PixelSearch.SCOPES_KEY, PixelSearch.encodeScopes(next))) fillScopes();
                        } else fillScopes();
                    }
                    return true;
                }
            });
        }
        heading(scopeBox, "Web search", false);
        LinearLayout engines = strip(scopeBox);
        String engine = PixelSearch.engine(a);
        for (int i = 0; i < PixelSearch.ENGINES.length; i++) {
            final String value = PixelSearch.ENGINES[i];
            chip(engines, PixelSearch.ENGINE_NAMES[i], value.equals(engine), () -> apply(PixelSearch.ENGINE_KEY, value));
        }
    }

    void indexNow() {
        if (host.blocked(PixelSearch.KEY)) return;
        if (!PixelSearch.active(a)) { toast("Turn on Pixel search first"); return; }
        if (!SearchIndex.access()) { toast("Turn Pixel search off and on again to allow searching your files."); return; }
        SearchIndex.schedule(a);
        SearchIndex.refreshSoon(a, 0);
        if (indexSummary != null) indexSummary.setText(I18n.cs("Looking through your files…"));
        main.postDelayed(this::poll, 1500);
    }

    private void poll() {
        if (indexSummary == null || !indexSummary.isAttachedToWindow()) return;
        indexStatus();
        if (SearchIndex.running()) main.postDelayed(this::poll, 1500);
    }

    void indexStatus() {
        if (indexSummary == null) return;
        if (!PixelSearch.active(a)) { indexSummary.setText(I18n.cs("Your files are looked through after you turn on Pixel search.")); return; }
        if (SearchIndex.running()) {
            indexSummary.setText(I18n.t("Looking through your files… {0}").replace("{0}", String.format(Locale.getDefault(), "%,d", SearchIndex.progress())));
            return;
        }
        long files = SearchIndex.prefs(a).getLong("files", 0), docs = SearchIndex.prefs(a).getLong("docs", 0), last = SearchIndex.prefs(a).getLong("last", 0);
        if (last == 0) { indexSummary.setText(I18n.cs("Tap to look through your files now.")); return; }
        CharSequence when = android.text.format.DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS);
        indexSummary.setText(I18n.t("{0} files, {1} with text you can search. Updated {2}.")
                .replace("{0}", String.format(Locale.getDefault(), "%,d", files)).replace("{1}", String.format(Locale.getDefault(), "%,d", docs)).replace("{2}", when));
    }

    static String commands(boolean on) {
        String pkg = PixelSearch.SUITE;
        if (!on) return "";
        return "; appops set " + pkg + " MANAGE_EXTERNAL_STORAGE allow; pm grant " + pkg + " android.permission.READ_CONTACTS; pm grant " + pkg + " android.permission.READ_CALENDAR; pm grant " + pkg
                + " android.permission.CALL_PHONE; pm grant " + pkg + " android.permission.READ_EXTERNAL_STORAGE 2>/dev/null; true";
    }
}
