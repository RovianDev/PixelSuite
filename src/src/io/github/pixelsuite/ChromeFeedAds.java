package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

final class ChromeFeedAds {
    private ChromeFeedAds() {}

    private static final String[] LABELS = GoogleFeedAds.LABELS;
    private static final int UNKNOWN = -1, PLAIN = 0, AD = 1;
    private static int sFeedId = -1;
    private static WeakReference<View> sFeed = new WeakReference<View>(null);
    private static final Map<View, int[]> sState = new WeakHashMap<View, int[]>();
    private static final Map<View, int[]> sFolded = new WeakHashMap<View, int[]>();
    private static final Map<View, View> sTrimmed = new WeakHashMap<View, View>();
    private static int sChecked, sAds;
    private static boolean sUnsafe;
    private static TextSpy sSpy;
    private static boolean sNoSpy;

    private static TextSpy spy() {
        if (sSpy == null && !sNoSpy) {
            try { sSpy = new TextSpy(); } catch (Throwable unavailable) { sNoSpy = true; }
        }
        return sSpy;
    }

    static void install(ClassLoader cl) {
        try {
            Xp.hookAllMethods(Activity.class, "onPostResume", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { watch((Activity) p.thisObject); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static boolean on(Context c) {
        try { return GoogleFeedAds.enabled(c); } catch (Throwable t) { return false; }
    }

    private static void watch(final Activity a) {
        final View decor = a.getWindow().getDecorView();
        if (Xp.getAdditionalInstanceField(decor, "pixelsuite_feed_watch") != null) return;
        ViewTreeObserver.OnGlobalLayoutListener l = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                try {
                    View feed = sFeed.get();
                    if (feed != null && feed.isAttachedToWindow() && feed.getRootView() == decor.getRootView()) return;
                    if (!on(a)) return;
                    if (sFeedId == -1) sFeedId = a.getResources().getIdentifier("feed_stream_recycler_view", "id", a.getPackageName());
                    View found = sFeedId == 0 ? null : decor.findViewById(sFeedId);
                    if (found instanceof ViewGroup) attach((ViewGroup) found);
                } catch (Throwable ignored) { }
            }
        };
        Xp.setAdditionalInstanceField(decor, "pixelsuite_feed_watch", l);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(l);
    }

    private static void attach(final ViewGroup feed) {
        sFeed = new WeakReference<View>(feed);
        if (Xp.getAdditionalInstanceField(feed, "pixelsuite_feed_cards") != null) return;
        ViewTreeObserver.OnPreDrawListener l = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                try { cards(feed); } catch (Throwable ignored) { }
                return true;
            }
        };
        Xp.setAdditionalInstanceField(feed, "pixelsuite_feed_cards", l);
        feed.getViewTreeObserver().addOnPreDrawListener(l);
    }

    private static void cards(ViewGroup feed) {
        boolean enabled = !sUnsafe && on(feed.getContext());
        for (int i = 0; i < feed.getChildCount(); i++) {
            final View card = feed.getChildAt(i);
            int[] st = sState.get(card);
            if (st == null) {
                st = new int[]{UNKNOWN, 0, 0};
                sState.put(card, st);
                final int[] state = st;
                card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View v) { state[0] = UNKNOWN; state[1] = 0; state[2] = 0; }
                    @Override public void onViewDetachedFromWindow(View v) { }
                });
                card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                        if (state[0] == PLAIN && (r - l != or - ol || b - t != ob - ot)) { state[0] = UNKNOWN; state[1] = 0; }
                    }
                });
            }
            if (!enabled) { if (sFolded.containsKey(card)) unfold(card); if (sTrimmed.containsKey(card)) untrim(card); continue; }
            long now = android.os.SystemClock.uptimeMillis();
            boolean again = st[0] == PLAIN && st[1] < 12 && (int) now - st[2] >= 400;
            if ((st[0] == UNKNOWN || again) && st[1] < 12 && card.getWidth() > 0) {
                st[1]++; st[2] = (int) now;
                boolean first = st[0] == UNKNOWN;
                int verdict = check(card);
                trim(card);
                if (verdict != UNKNOWN && (first || verdict == AD)) {
                    st[0] = verdict;
                    if (first) sChecked++;
                    if (verdict == AD) sAds++;
                    if (sChecked >= 6 && sAds * 2 > sChecked) { sUnsafe = true; unfoldAll(); return; }
                }
            }
            if (st[0] == AD) fold(card); else if (sFolded.containsKey(card)) unfold(card);
        }
    }

    private static View feedback(View card) {
        ArrayList<View> views = new ArrayList<View>();
        collect(card, views);
        for (View v : views) {
            CharSequence d = v.getContentDescription();
            if (d == null) continue;
            String s = d.toString().trim();
            if (!"Very good".equalsIgnoreCase(s) && !"Very bad".equalsIgnoreCase(s)) continue;
            if (v.getParent() instanceof ViewGroup && v.getParent() != card) return (View) v.getParent();
        }
        return null;
    }

    private static void trim(final View card) {
        View box = feedback(card);
        View was = sTrimmed.get(card);
        if (box == was) return;
        if (was != null) untrim(card);
        if (box == null) return;
        squash(card.getClass());
        sTrimmed.put(card, box);
        box.setVisibility(View.INVISIBLE);
        card.post(new Runnable() { public void run() { card.requestLayout(); } });
    }

    private static void untrim(final View card) {
        View box = sTrimmed.remove(card);
        if (box != null && box.getVisibility() == View.INVISIBLE) box.setVisibility(View.VISIBLE);
        card.post(new Runnable() { public void run() { card.requestLayout(); } });
    }

    private static int trimmedHeight(View card, View box) {
        int top = 0;
        View v = box;
        while (v != null && v != card) {
            top += v.getTop();
            if (!(v.getParent() instanceof View)) return -1;
            v = (View) v.getParent();
        }
        if (v != card || top <= 0) return -1;
        return top + Math.round(16 * card.getResources().getDisplayMetrics().density);
    }

    private static int check(View card) {
        int texts = 0;
        ArrayList<View> views = new ArrayList<View>();
        collect(card, views);
        for (View v : views) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (described(v.getContentDescription())) return AD;
            if (provided(v)) return AD;
            if (!(v instanceof TextView)) continue;
            CharSequence t = ((TextView) v).getText();
            if (t == null || t.length() == 0) continue;
            texts++;
            if (label(t)) return AD;
        }
        TextSpy spy = spy();
        int runs = 0;
        if (spy != null) {
            spy.reset();
            int save = spy.save();
            try { card.draw(spy); } catch (Throwable ignored) { } finally { try { spy.restoreToCount(save); } catch (Throwable ignored) { } }
            if (spy.ad) return AD;
            runs = spy.runs;
        }
        return texts + runs > 0 ? PLAIN : UNKNOWN;
    }

    private static void collect(View v, ArrayList<View> out) {
        if (out.size() > 300) return;
        out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    private static boolean described(CharSequence d) {
        if (d == null || d.length() == 0) return false;
        String all = d.toString().trim();
        int colon = all.indexOf(':');
        if (colon > 0 && colon <= 24) {
            String head = all.substring(0, colon).trim().replace('\u2019', '\'').toLowerCase(java.util.Locale.ROOT);
            if (head.equals("ad's title") || head.equals("ad title") || head.equals("ad") || head.equals("ads") || label(head)) return true;
        }
        for (String part : d.toString().split("[\\n,|\u00b7\u2022]")) if (label(part)) return true;
        return false;
    }

    private static boolean provided(View v) {
        android.view.accessibility.AccessibilityNodeProvider provider;
        try { provider = v.getAccessibilityNodeProvider(); } catch (Throwable t) { return false; }
        if (provider == null) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo root = provider.createAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeProvider.HOST_VIEW_ID);
            if (root == null) return false;
            java.lang.reflect.Method childId = android.view.accessibility.AccessibilityNodeInfo.class.getDeclaredMethod("getChildId", int.class);
            childId.setAccessible(true);
            ArrayList<android.view.accessibility.AccessibilityNodeInfo> queue = new ArrayList<android.view.accessibility.AccessibilityNodeInfo>();
            queue.add(root);
            for (int i = 0; i < queue.size() && i < 200; i++) {
                android.view.accessibility.AccessibilityNodeInfo n = queue.get(i);
                if (label(n.getText()) || described(n.getContentDescription())) return true;
                for (int c = 0; c < n.getChildCount() && queue.size() < 200; c++) {
                    long id = (Long) childId.invoke(n, c);
                    android.view.accessibility.AccessibilityNodeInfo child = provider.createAccessibilityNodeInfo((int) (id >>> 32));
                    if (child != null) queue.add(child);
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static boolean label(CharSequence t) {
        if (t == null || t.length() > 48) return false;
        String s = t.toString().replace(' ', ' ').trim();
        for (String l : LABELS) {
            if (l.equalsIgnoreCase(s)) return true;
            if (s.length() > l.length() && s.regionMatches(true, 0, l, 0, l.length())) {
                char next = s.charAt(l.length());
                if (next == ' ' || next == '·' || next == '•' || next == '|' || next == ':') return true;
            }
        }
        return false;
    }

    private static final class TextSpy extends Canvas {
        boolean ad; int runs;
        void reset() { ad = false; runs = 0; }
        private void seen(CharSequence t) {
            if (t == null || t.length() == 0) return;
            runs++;
            if (!ad && label(t)) ad = true;
        }
        private void seen(char[] text, int index, int count) { if (text != null && index >= 0 && count > 0 && index + count <= text.length) seen(new String(text, index, count)); }
        private void seen(CharSequence text, int start, int end) { if (text != null && start >= 0 && end <= text.length() && start < end) seen(text.subSequence(start, end)); }
        @Override public boolean getClipBounds(Rect bounds) { if (bounds != null) bounds.set(-1000000, -1000000, 1000000, 1000000); return true; }
        @Override public boolean quickReject(RectF rect, EdgeType type) { return false; }
        @Override public boolean quickReject(Path path, EdgeType type) { return false; }
        @Override public boolean quickReject(float left, float top, float right, float bottom, EdgeType type) { return false; }
        public boolean quickReject(RectF rect) { return false; }
        public boolean quickReject(Path path) { return false; }
        public boolean quickReject(float left, float top, float right, float bottom) { return false; }
        @Override public boolean clipRect(RectF rect, Region.Op op) { return true; }
        @Override public boolean clipRect(Rect rect, Region.Op op) { return true; }
        @Override public boolean clipRect(RectF rect) { return true; }
        @Override public boolean clipRect(Rect rect) { return true; }
        @Override public boolean clipRect(float left, float top, float right, float bottom, Region.Op op) { return true; }
        @Override public boolean clipRect(float left, float top, float right, float bottom) { return true; }
        @Override public boolean clipRect(int left, int top, int right, int bottom) { return true; }
        @Override public boolean clipPath(Path path, Region.Op op) { return true; }
        @Override public boolean clipPath(Path path) { return true; }
        @Override public void drawText(char[] text, int index, int count, float x, float y, Paint paint) { seen(text, index, count); }
        @Override public void drawText(String text, float x, float y, Paint paint) { seen(text); }
        @Override public void drawText(String text, int start, int end, float x, float y, Paint paint) { seen(text, start, end); }
        @Override public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) { seen(text, start, end); }
        @Override public void drawTextRun(char[] text, int index, int count, int contextIndex, int contextCount, float x, float y, boolean isRtl, Paint paint) { seen(text, index, count); }
        @Override public void drawTextRun(CharSequence text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, Paint paint) { seen(text, start, end); }
        @Override public void drawTextOnPath(char[] text, int index, int count, Path path, float hOffset, float vOffset, Paint paint) { seen(text, index, count); }
        @Override public void drawTextOnPath(String text, Path path, float hOffset, float vOffset, Paint paint) { seen(text); }
        @Override public void drawPosText(char[] text, int index, int count, float[] pos, Paint paint) { seen(text, index, count); }
        @Override public void drawPosText(String text, float[] pos, Paint paint) { seen(text); }
        @Override public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) { }
        @Override public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) { }
        @Override public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) { }
        @Override public void drawBitmap(int[] colors, int offset, int stride, float x, float y, int width, int height, boolean hasAlpha, Paint paint) { }
        @Override public void drawBitmap(int[] colors, int offset, int stride, int x, int y, int width, int height, boolean hasAlpha, Paint paint) { }
        @Override public void drawBitmap(Bitmap bitmap, Matrix matrix, Paint paint) { }
        @Override public void drawBitmapMesh(Bitmap bitmap, int meshWidth, int meshHeight, float[] verts, int vertOffset, int[] colors, int colorOffset, Paint paint) { }
        @Override public void drawPicture(Picture picture) { }
        @Override public void drawPicture(Picture picture, RectF dst) { }
        @Override public void drawPicture(Picture picture, Rect dst) { }
        @Override public void drawColor(int color) { }
        @Override public void drawColor(int color, PorterDuff.Mode mode) { }
        @Override public void drawARGB(int a, int r, int g, int b) { }
        @Override public void drawRGB(int r, int g, int b) { }
        @Override public void drawPaint(Paint paint) { }
        @Override public void drawPoint(float x, float y, Paint paint) { }
        @Override public void drawPoints(float[] pts, int offset, int count, Paint paint) { }
        @Override public void drawPoints(float[] pts, Paint paint) { }
        @Override public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) { }
        @Override public void drawLines(float[] pts, int offset, int count, Paint paint) { }
        @Override public void drawLines(float[] pts, Paint paint) { }
        @Override public void drawRect(RectF rect, Paint paint) { }
        @Override public void drawRect(Rect r, Paint paint) { }
        @Override public void drawRect(float left, float top, float right, float bottom, Paint paint) { }
        @Override public void drawOval(RectF oval, Paint paint) { }
        @Override public void drawOval(float left, float top, float right, float bottom, Paint paint) { }
        @Override public void drawCircle(float cx, float cy, float radius, Paint paint) { }
        @Override public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint paint) { }
        @Override public void drawArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean useCenter, Paint paint) { }
        @Override public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) { }
        @Override public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint) { }
        @Override public void drawPath(Path path, Paint paint) { }
        @Override public void drawVertices(VertexMode mode, int vertexCount, float[] verts, int vertOffset, float[] texs, int texOffset, int[] colors, int colorOffset, short[] indices, int indexOffset, int indexCount, Paint paint) { }
    }

    private static final java.util.Set<Class<?>> sMeasureHooked = new java.util.HashSet<Class<?>>();
    private static java.lang.reflect.Method sSetMeasured;

    private static void squash(Class<?> type) {
        if (!sMeasureHooked.add(type)) return;
        for (Class<?> k = type; k != null && k != View.class; k = k.getSuperclass()) {
            java.lang.reflect.Method m;
            try { m = k.getDeclaredMethod("onMeasure", int.class, int.class); } catch (NoSuchMethodException none) { continue; }
            if (k != type && !sMeasureHooked.add(k)) return;
            try {
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param p) {
                        try {
                            View v = (View) p.thisObject;
                            int height;
                            if (sFolded.containsKey(v)) height = 0;
                            else {
                                View box = sTrimmed.get(v);
                                if (box == null) return;
                                height = trimmedHeight(v, box);
                                if (height <= 0 || height >= v.getMeasuredHeight()) return;
                            }
                            if (sSetMeasured == null) { sSetMeasured = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class); sSetMeasured.setAccessible(true); }
                            sSetMeasured.invoke(v, v.getMeasuredWidth(), height);
                        } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable ignored) { }
            return;
        }
    }

    private static void fold(final View card) {
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp == null) return;
        boolean margins = lp instanceof ViewGroup.MarginLayoutParams;
        if (sFolded.containsKey(card)) {
            if (card.getHeight() == 0) return;
        } else {
            squash(card.getClass());
            int top = 0, bottom = 0;
            if (margins) { top = ((ViewGroup.MarginLayoutParams) lp).topMargin; bottom = ((ViewGroup.MarginLayoutParams) lp).bottomMargin; }
            sFolded.put(card, new int[]{lp.height, top, bottom});
        }
        lp.height = 0;
        if (margins) { ((ViewGroup.MarginLayoutParams) lp).topMargin = 0; ((ViewGroup.MarginLayoutParams) lp).bottomMargin = 0; }
        card.post(new Runnable() { public void run() { card.requestLayout(); } });
    }

    private static void unfold(final View card) {
        int[] was = sFolded.remove(card);
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (was == null || lp == null) return;
        lp.height = was[0];
        if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).topMargin = was[1]; ((ViewGroup.MarginLayoutParams) lp).bottomMargin = was[2]; }
        card.post(new Runnable() { public void run() { card.requestLayout(); } });
    }

    private static void unfoldAll() {
        for (View card : new ArrayList<View>(sFolded.keySet())) unfold(card);
    }
}
