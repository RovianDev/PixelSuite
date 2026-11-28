package io.github.pixelsuite;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

final class SearchHearts {
    private static FrameLayout shown;

    private static LinearLayout line;
    private static boolean wiggling;
    private static View held;
    private static float grab, downX, downY;

    private static View check;
    private static boolean moved;

    private static void finish(Context c) {
        save(c);
        final View done = check;
        if (done != null) done.animate().scaleX(0).scaleY(0).setDuration(160).withEndAction(() -> done.setVisibility(View.GONE)).start();
    }

    private static void save(Context c) {
        if (!wiggling || line == null) return;
        wiggling = false;
        java.util.ArrayList<String[]> order = new java.util.ArrayList<String[]>();
        still();
        for (int i = 0; i < line.getChildCount(); i++) { Object tag = line.getChildAt(i).getTag(); if (tag instanceof String[]) order.add((String[]) tag); }
        try { android.provider.Settings.Secure.putString(c.getContentResolver(), PixelSearch.HEARTS_KEY, PixelSearch.encodeHearts(order)); } catch (Throwable ignored) { }
    }

    private static final java.util.WeakHashMap<View, android.animation.Animator> WIGGLES = new java.util.WeakHashMap<View, android.animation.Animator>();

    private static void wiggle(final View cell, int index) {
        android.animation.ObjectAnimator w = android.animation.ObjectAnimator.ofFloat(cell, "rotation", index % 2 == 0 ? -2.4f : 2.4f, index % 2 == 0 ? 2.4f : -2.4f);
        w.setDuration(150); w.setRepeatCount(android.animation.ValueAnimator.INFINITE); w.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        w.start();
        WIGGLES.put(cell, w);
    }

    private static void still() {
        for (java.util.Map.Entry<View, android.animation.Animator> e : new java.util.ArrayList<java.util.Map.Entry<View, android.animation.Animator>>(WIGGLES.entrySet())) { if (e.getValue() != null) e.getValue().cancel(); if (e.getKey() != null) e.getKey().animate().rotation(0).setDuration(160).start(); }
        WIGGLES.clear();
    }

    private static boolean drag(View cell, android.view.MotionEvent e, float d) {
        int act = e.getActionMasked();
        if (act == android.view.MotionEvent.ACTION_DOWN) { downX = e.getRawX(); return false; }
        if (held != cell) return false;
        if (act == android.view.MotionEvent.ACTION_MOVE) {
            if (grab == 0) grab = downX;
            if (Math.abs(e.getRawX() - downX) > 8 * d) moved = true;
            cell.setTranslationX(e.getRawX() - grab);
            int i = line.indexOfChild(cell);
            float mid = cell.getX() + cell.getWidth() / 2f;
            for (int dir = -1; dir <= 1; dir += 2) {
                int j = i + dir;
                if (j < 0 || j >= line.getChildCount()) continue;
                View other = line.getChildAt(j);
                float otherMid = other.getLeft() + other.getWidth() / 2f;
                if (dir > 0 ? mid <= otherMid : mid >= otherMid) continue;
                line.removeView(other);
                line.addView(other, i);
                grab += dir * other.getWidth();
                cell.setTranslationX(e.getRawX() - grab);
                other.setTranslationX(dir * cell.getWidth());
                other.animate().translationX(0).setDuration(300).setInterpolator(new SearchOverlay.Spring(0.7f)).start();
                cell.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                break;
            }
            return true;
        }
        if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) {
            held = null; grab = 0;
            cell.animate().translationX(0).scaleX(1).scaleY(1).setDuration(320).setInterpolator(new SearchOverlay.Spring(0.6f)).start();

        }
        return true;
    }

    static void close() {
        final FrameLayout veil = shown;
        if (veil == null) return;
        try { save(veil.getContext()); } catch (Throwable ignored) { }
        line = null; held = null; check = null;
        shown = null;
        veil.setOnTouchListener(null); veil.setOnClickListener(null); veil.setClickable(false); veil.setOnKeyListener(null);
        if (veil.getChildCount() == 0 || !veil.isAttachedToWindow()) { remove(veil); return; }
        for (int i = 1; i < veil.getChildCount(); i++) veil.getChildAt(i).animate().cancel();
        for (int i = 1; i < veil.getChildCount(); i++) veil.getChildAt(i).animate().scaleX(0).scaleY(0).alpha(0).setDuration(140).start();
        SearchOverlay.bloomOut(veil.getChildAt(0), () -> remove(veil));
        veil.postDelayed(() -> remove(veil), 360);
    }

    private static void remove(View veil) { if (veil.getParent() instanceof ViewGroup) ((ViewGroup) veil.getParent()).removeView(veil); }

    static void show(final SearchDock dock) {
        final Context c = dock.getContext();
        if (shown != null && (!shown.isAttachedToWindow() || shown.getRootView() != dock.anchor().getRootView())) { remove(shown); shown = null; line = null; held = null; check = null; wiggling = false; }
        if (shown != null) { close(); return; }
        final List<String[]> list = PixelSearch.hearts(c);
        if (list.isEmpty()) { Toast.makeText(c, I18n.cs("Hold the search bar and add contacts first"), Toast.LENGTH_SHORT).show(); return; }
        View anchor = dock.anchor();
        if (!(anchor.getRootView() instanceof ViewGroup)) return;
        final ViewGroup decor = (ViewGroup) anchor.getRootView();
        final PixelSearch.Look look = new PixelSearch.Look(c);
        final float d = c.getResources().getDisplayMetrics().density;
        final FrameLayout veil = new FrameLayout(c) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) { return shown == this && super.dispatchTouchEvent(e); }
            @Override public void onWindowFocusChanged(boolean has) { super.onWindowFocusChanged(has); if (!has && shown == this) close(); }
            @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v != View.VISIBLE && shown == this) close(); }
        };
        veil.setOnClickListener(v -> close());
        veil.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { downX = e.getRawX(); downY = e.getRawY(); }
            else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > 14 * d) { close(); return true; }
            return false;
        });
        veil.setFocusableInTouchMode(true);
        veil.setOnKeyListener((v, code, e) -> { if (code == android.view.KeyEvent.KEYCODE_BACK) { if (e.getAction() == android.view.KeyEvent.ACTION_UP) close(); return true; } return false; });
        wiggling = false;
        final HorizontalScrollView strip = new HorizontalScrollView(c) {
            float sx, sy, lastX, pull; boolean touching, pulling;
            View content() { return getChildCount() > 0 ? getChildAt(0) : null; }
            int max() { View v = content(); return v == null ? 0 : Math.max(0, v.getWidth() - getWidth()); }
            void release() {
                View v = content(); if (v == null) return;
                v.animate().cancel();
                v.animate().translationX(0).setDuration(520).setInterpolator(new SearchOverlay.Spring(0.42f)).start();
                pull = 0; pulling = false;
            }
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
                int act = e.getActionMasked();
                if (act == android.view.MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); lastX = e.getRawX(); touching = true; pulling = false; pull = 0; View v = content(); if (v != null) v.animate().cancel(); }
                else if (act == android.view.MotionEvent.ACTION_MOVE && !wiggling) {
                    float dx = Math.abs(e.getRawX() - sx), dy = Math.abs(e.getRawY() - sy);
                    if (dy > 22 * d && dy > dx * 1.3f && !pulling) { close(); return true; }
                    float step = e.getRawX() - lastX; lastX = e.getRawX();
                    boolean atStart = getScrollX() <= 0, atEnd = getScrollX() >= max();
                    boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                    if (rtl) { boolean t = atStart; atStart = atEnd; atEnd = t; }
                    if (pulling || (atStart && step > 0) || (atEnd && step < 0)) {
                        pull += step;
                        if ((pull > 0 && !atStart) || (pull < 0 && !atEnd)) pull = 0;
                        View v = content();
                        if (v != null) {
                            float limit = 90 * d, k = Math.signum(pull) * limit * (1 - (float) Math.exp(-Math.abs(pull) / (limit * 1.6f)));
                            v.setTranslationX(k);
                        }
                        pulling = pull != 0;
                        if (pulling) { getParent().requestDisallowInterceptTouchEvent(true); return true; }
                    }
                } else if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) {
                    touching = false;
                    if (pulling) { release(); android.view.MotionEvent x = android.view.MotionEvent.obtain(e); x.setAction(android.view.MotionEvent.ACTION_CANCEL); super.dispatchTouchEvent(x); x.recycle(); return true; }
                }
                return super.dispatchTouchEvent(e);
            }
            @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                if (touching || wiggling) return;
                int m = max(), speed = l - ol;
                if ((l <= 0 && speed < 0) || (l >= m && speed > 0)) {
                    View v = content(); if (v == null) return;
                    float kick = Math.max(-34 * d, Math.min(34 * d, -speed * 2.2f));
                    v.animate().cancel();
                    v.animate().translationX(kick).setDuration(110).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                            .withEndAction(() -> v.animate().translationX(0).setDuration(560).setInterpolator(new SearchOverlay.Spring(0.4f)).start()).start();
                }
            }
        };
        strip.setHorizontalScrollBarEnabled(false);
        strip.setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip.setClickable(true);
        final FrameLayout panel = new FrameLayout(c);
        final boolean black = SuperDark.inEffect(c);
        if (black || !look.glass) {
            GradientDrawable bg = new GradientDrawable(); bg.setColor(black ? FrostedSurface.SUPER_DARK_PANEL : look.card); bg.setCornerRadius(30 * d);
            panel.setBackground(bg);
        } else {
            final android.graphics.Bitmap[] shot = new android.graphics.Bitmap[1];
            android.graphics.drawable.Drawable behind = null;
            try {
                Object vri = LauncherFeatures.call(decor, "getViewRootImpl");
                behind = (android.graphics.drawable.Drawable) LauncherFeatures.call(vri, "createBackgroundBlurDrawable");
                LauncherFeatures.call(behind, "setBlurRadius", Math.round(FrostedSurface.depthBlurRadius(c) * 0.6f));
                LauncherFeatures.call(behind, "setCornerRadius", 30 * d);
                try { LauncherFeatures.call(behind, "setColor", android.graphics.Color.TRANSPARENT); } catch (Throwable ignored) { }
            } catch (Throwable t) { behind = null; }
            final android.graphics.drawable.Drawable wall = behind;
            final int tint = look.card & 0xffffff;
            panel.setBackground(new android.graphics.drawable.Drawable() {
                final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
                final android.graphics.Rect src = new android.graphics.Rect();
                final int[] at = new int[2];
                @Override public void draw(android.graphics.Canvas cv) {
                    android.graphics.Rect b = getBounds();
                    if (wall != null) { wall.setBounds(b); wall.draw(cv); }
                    android.graphics.Bitmap s = shot[0];
                    if (s != null && decor.getWidth() > 0) {
                        panel.getLocationInWindow(at);
                        float k = s.getWidth() / (float) decor.getWidth();
                        src.set(Math.round(at[0] * k), Math.round(at[1] * k), Math.round((at[0] + panel.getWidth()) * k), Math.round((at[1] + panel.getHeight()) * k));
                        paint.setAlpha(255);
                        cv.drawBitmap(s, src, b, paint);
                    }
                    paint.setColor(tint | (s != null ? 0xa6000000 : 0xe0000000));
                    cv.drawRect(b, paint);
                }
                @Override public void setAlpha(int a) { }
                @Override public void setColorFilter(android.graphics.ColorFilter f) { }
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });
            try {
                android.content.Context base = c;
                while (!(base instanceof android.app.Activity) && base instanceof android.content.ContextWrapper) base = ((android.content.ContextWrapper) base).getBaseContext();
                if (base instanceof android.app.Activity && decor.getWidth() > 0) {
                    final android.graphics.Bitmap raw = android.graphics.Bitmap.createBitmap(Math.max(1, decor.getWidth() / 8), Math.max(1, decor.getHeight() / 8), android.graphics.Bitmap.Config.ARGB_8888);
                    Class<?> copy = Class.forName("android.view.PixelCopy"), done2 = Class.forName("android.view.PixelCopy$OnPixelCopyFinishedListener");
                    Object back = java.lang.reflect.Proxy.newProxyInstance(done2.getClassLoader(), new Class<?>[]{done2}, (p, m, a) -> {
                        if ("onPixelCopyFinished".equals(m.getName()) && a != null && a.length == 1 && Integer.valueOf(0).equals(a[0])) {
                            try {
                                android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(raw, Math.max(1, raw.getWidth() / 4), Math.max(1, raw.getHeight() / 4), true);
                                shot[0] = android.graphics.Bitmap.createScaledBitmap(small, raw.getWidth(), raw.getHeight(), true);
                                panel.invalidate();
                            } catch (Throwable ignored) { }
                        }
                        return null;
                    });
                    copy.getMethod("request", android.view.Window.class, android.graphics.Bitmap.class, done2, android.os.Handler.class)
                            .invoke(null, ((android.app.Activity) base).getWindow(), raw, back, new android.os.Handler(android.os.Looper.getMainLooper()));
                }
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "hearts: frost", t); }
        }
        panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 30 * d); }
        });
        panel.setClipToOutline(true);
        if (look.glass || black) {
            final GlassRim rim = new GlassRim(30 * d, 1.5f * d, look.dark || black);
            panel.setForeground(new android.graphics.drawable.Drawable() {
                @Override public void draw(android.graphics.Canvas cv) { rim.draw(cv, 0, 0, panel.getWidth(), panel.getHeight(), 30 * d, 220); }
                @Override public void setAlpha(int a) { }
                @Override public void setColorFilter(android.graphics.ColorFilter f) { }
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });
        }
        final ImageView done = new ImageView(c);
        done.setImageDrawable(new SearchGlyph(SearchGlyph.TICK, look.text));
        GradientDrawable db = new GradientDrawable(); db.setShape(GradientDrawable.OVAL); db.setColor(black ? FrostedSurface.SUPER_DARK_PANEL : look.glass ? (look.card & 0xffffff) | 0xd0000000 : look.card);
        done.setBackground(db);
        done.setPadding(Math.round(10 * d), Math.round(10 * d), Math.round(10 * d), Math.round(10 * d));
        if (look.glass || black) {
            final GlassRim ring = new GlassRim(21 * d, 1.5f * d, look.dark || black);
            done.setForeground(new android.graphics.drawable.Drawable() {
                @Override public void draw(android.graphics.Canvas cv) { ring.draw(cv, 0, 0, done.getWidth(), done.getHeight(), done.getWidth() / 2f, 230); }
                @Override public void setAlpha(int a) { }
                @Override public void setColorFilter(android.graphics.ColorFilter f) { }
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });
        } else done.setElevation(8 * d);
        check = done;
        done.setVisibility(View.GONE);
        done.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); finish(c); });
        panel.setElevation(10 * d);
        LinearLayout row = new LinearLayout(c);
        row.setClipToPadding(false); row.setClipChildren(false);
        row.setPadding(Math.round(10 * d), Math.round(12 * d), Math.round(10 * d), Math.round(10 * d));
        for (final String[] f : list) {
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView face = new ImageView(c);
            face.setImageDrawable(new SearchDock.Avatar(c, new PixelSearch.Slot('I', "contact", "call/" + f[0] + "/" + f[2], f[1]), look.text));
            cell.addView(face, new LinearLayout.LayoutParams(Math.round(52 * d), Math.round(52 * d)));
            TextView name = new TextView(c);
            String first = f[1].trim(); int cut = first.indexOf(' '); if (cut > 0) first = first.substring(0, cut);
            name.setText(first); name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); name.setTextColor(look.text);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END); name.setGravity(Gravity.CENTER);
            name.setPadding(0, Math.round(5 * d), 0, 0);
            cell.addView(name, new LinearLayout.LayoutParams(Math.round(68 * d), -2));
            cell.setTag(f);
            cell.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                v.getParent().requestDisallowInterceptTouchEvent(true);
                moved = false;
                if (!wiggling) {
                    wiggling = true;
                    for (int i = 0; i < row.getChildCount(); i++) wiggle(row.getChildAt(i), i);
                    done.setVisibility(View.VISIBLE); done.setScaleX(0); done.setScaleY(0);
                    done.animate().scaleX(1).scaleY(1).setDuration(380).setInterpolator(new SearchOverlay.Spring(0.6f)).start();
                }
                held = v; grab = 0;
                v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(200).start();
                return true;
            });
            cell.setOnTouchListener((v, e) -> drag(v, e, d));
            cell.setOnClickListener(v -> {
                if (wiggling) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                close();
                SearchOverlay.suiteAction(c, "call", f[2]);
            });
            row.addView(cell);
        }
        strip.addView(row);
        panel.addView(strip, new FrameLayout.LayoutParams(-2, -2));
        int[] at = new int[2]; decor.getLocationOnScreen(at);
        RectF on = dock.shellOnScreen();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Math.round(decor.getHeight() - (on.top - at[1]) + 12 * d);
        lp.leftMargin = lp.rightMargin = Math.round(12 * d);
        veil.addView(panel, lp);
        line = row;
        final FrameLayout.LayoutParams dp2 = new FrameLayout.LayoutParams(Math.round(42 * d), Math.round(42 * d), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dp2.bottomMargin = lp.bottomMargin + Math.round(112 * d);
        veil.addView(done, dp2);
        panel.post(() -> { dp2.bottomMargin = lp.bottomMargin + panel.getHeight() + Math.round(16 * d); done.setLayoutParams(dp2); });
        veil.requestFocus();
        decor.addView(veil, new ViewGroup.LayoutParams(-1, -1));
        shown = veil;
        final float top0 = on.top, slack = 6 * d;
        decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (shown != veil) { if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnPreDrawListener(this); return true; }
                try { if (dock.getWindowVisibility() != View.VISIBLE || Math.abs(dock.shellOnScreen().top - top0) > slack) close(); } catch (Throwable t) { close(); }
                return true;
            }
        });
        panel.setAlpha(0f);
        panel.post(() -> { panel.setPivotX(panel.getWidth() / 2f); panel.setPivotY(panel.getHeight()); SearchOverlay.bloomIn(panel); });
    }
}
