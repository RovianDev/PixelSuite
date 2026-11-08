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
                    if (feed != null && feed.isAttachedToWindow() && feed.getRootView() == decor.getRootView() && feed.isShown()) return;
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
        try {
            feed.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                    if (sSelf) return;
                    sScrolled += y - oy;
                }
            });
        } catch (Throwable ignored) { }
    }

    private static WeakReference<View> sRef = new WeakReference<View>(null);
    private static int sRefTop, sScrolled;
    private static boolean sSelf;
    private static long sChangedAt;
    private static final Map<View, Boolean> sChanged = new WeakHashMap<View, Boolean>();

    private static void changed(View card) {
        sChangedAt = android.os.SystemClock.uptimeMillis();
        sChanged.put(card, Boolean.TRUE);
    }

    private static void stabilize(ViewGroup feed) {
        long now = android.os.SystemClock.uptimeMillis();
        View r = sRef.get();
        int off = 0;
        boolean event = false;
        if (r != null && r.getParent() == feed && !sChanged.containsKey(r)) {
            off = r.getTop() - (sRefTop - sScrolled);
            String line = frame(feed, off);
            if (off != 0) {
                if (now - sChangedAt < 400 && Math.abs(off) < feed.getHeight()) {
                    sSelf = true;
                    try { feed.scrollBy(0, off); } catch (Throwable ignored) { } finally { sSelf = false; }
                    event = true;
                } else if (Math.abs(off) > 220) {  event = true; }
                else if (Math.abs(off) > 48) event = true;
            }
            remember(line, event);
        } else remember(frame(feed, 0) + " (no reference card)", false);
        sMoved = sScrolled;
        if (sMoved != 0) sMovedAt = android.os.SystemClock.uptimeMillis();
        sScrolled = 0;
        if (now - sChangedAt > 400) sChanged.clear();
        View pick = null;
        int mid = feed.getHeight() / 2, best = Integer.MAX_VALUE;
        for (int i = 0; i < feed.getChildCount(); i++) {
            View c = feed.getChildAt(i);
            if (c.getHeight() <= 0 || sChanged.containsKey(c) || sFolded.containsKey(c)) continue;
            int d = Math.abs((c.getTop() + c.getBottom()) / 2 - mid);
            if (d < best) { best = d; pick = c; }
        }
        sRef = new WeakReference<View>(pick);
        if (pick != null) sRefTop = pick.getTop();
    }

    private static final String[] sFrames = new String[4];
    private static int sFrameIndex, sAfter, sDumps;
    private static long sDumpWindow;

    private static String frame(ViewGroup feed, int off) {
        StringBuilder b = new StringBuilder();
        b.append(android.os.SystemClock.uptimeMillis()).append(" user scroll ").append(sScrolled).append(" off ").append(off).append(" |");
        for (int i = 0; i < feed.getChildCount(); i++) {
            View c = feed.getChildAt(i);
            int[] st = sState.get(c);
            char k = st == null ? '?' : st[0] == AD ? 'A' : st[0] == PLAIN ? 'P' : 'U';
            b.append(' ').append(Integer.toHexString(System.identityHashCode(c) & 0xfff)).append(':').append(c.getTop()).append('+').append(c.getHeight()).append(k);
            if (sFolded.containsKey(c)) b.append('F');
            if (sChanged.containsKey(c)) b.append('*');
            if (c == sRef.get()) b.append('R');
        }
        return b.toString();
    }

    private static void remember(String line, boolean event) {
        if (sAfter > 0) { sAfter--; }
        if (event) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - sDumpWindow > 10000) { sDumpWindow = now; sDumps = 0; }
            if (sDumps++ < 8) {
                for (int i = 0; i < sFrames.length; i++) {
                }
                sAfter = 3;
            }
        }
        sFrames[sFrameIndex] = line;
        sFrameIndex = (sFrameIndex + 1) % sFrames.length;
    }


    private static int sMoved;
    private static long sMovedAt;

    private static int[] state(View card) {
        int[] st = sState.get(card);
        if (st != null) return st;
        st = new int[]{UNKNOWN, 0, 0, 0};
        sState.put(card, st);
        final int[] state = st;
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { state[0] = UNKNOWN; state[1] = 0; state[2] = 0; state[3] = 0; }
            @Override public void onViewDetachedFromWindow(View v) { }
        });
        card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (state[0] == PLAIN && (r - l != or - ol || b - t != ob - ot)) { state[0] = UNKNOWN; state[1] = 0; }
            }
        });
        return st;
    }

    private static void cards(ViewGroup feed) {
        stabilize(feed);
        boolean enabled = !sUnsafe && on(feed.getContext());
        Session session = enabled ? session(feed) : null;
        for (int i = 0; i < feed.getChildCount(); i++) {
            final View card = feed.getChildAt(i);
            int[] st = state(card);
            if (!enabled) { if (sFolded.containsKey(card)) unfold(card); if (sTrimmed.containsKey(card)) untrim(card); continue; }
            if (st[0] == AD && st[3] == 1) verify(session, card, st);
            else if (st[0] == AD && session != null) fill(session, card);
            squash(card.getClass());
            long now = android.os.SystemClock.uptimeMillis();
            boolean again = st[0] == PLAIN && st[1] < 12 && (int) now - st[2] >= 400;
            if (again && (sMoved != 0 || android.os.SystemClock.uptimeMillis() - sMovedAt < 250)) again = false;
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
                    if (verdict == AD) keep(session, feed, card);
                }
            }
            if (st[0] == AD) fold(card); else if (sFolded.containsKey(card)) unfold(card);
        }
    }

    private static void note(View card) {
        changed(card);
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
        note(card);
        squash(card.getClass());
        sTrimmed.put(card, box);
        box.setVisibility(View.INVISIBLE);
        card.post(new Runnable() { public void run() { card.requestLayout(); } });
    }

    private static void untrim(final View card) {
        if (sTrimmed.containsKey(card)) note(card);
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
        ArrayList<View> trees = null;
        for (View v : views) {
            if (v.getVisibility() != View.VISIBLE) continue;
            if (described(v.getContentDescription())) return AD;
            if (v instanceof TextView) {
                CharSequence t = ((TextView) v).getText();
                if (t != null && t.length() > 0) {
                    texts++;
                    if (label(t)) return AD;
                }
            }
            try { if (v.getAccessibilityNodeProvider() != null) { if (trees == null) trees = new ArrayList<View>(); trees.add(v); } } catch (Throwable ignored) { }
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
        if (trees != null) for (View v : trees) if (provided(v)) return AD;
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
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        try { judgeOnMeasure((View) p.thisObject, p.args); } catch (Throwable ignored) { }
                    }
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

    private static void judgeOnMeasure(View v, Object[] args) {
        View feed = sFeed.get();
        if (feed == null || v.getParent() != feed || args == null || args.length != 2) return;
        if (sUnsafe || !on(v.getContext())) return;
        int[] st = state(v);
        if (st[0] != UNKNOWN) return;
        ViewGroup.LayoutParams lp0 = v.getLayoutParams();
        if (lp0 == null) return;
        Session session = session(feed);
        String key = session == null ? null : key(feed, v);
        if (key != null) sKeys.put(v, key); else sKeys.remove(v);
        if (key != null && session.ads.containsKey(key)) {
            st[0] = AD;
            st[3] = 1;
            foldNow(v, lp0, args);
            return;
        }
        if (key != null && sFolded.containsKey(v)) {
            unfoldNow(v, lp0, args);
        }
        if (st[1] >= 12) return;
        int verdict = check(v);
        if (verdict == UNKNOWN) return;
        st[1]++;
        st[2] = (int) android.os.SystemClock.uptimeMillis();
        st[0] = verdict;
        sChecked++;
        if (verdict == AD) sAds++;
        if (sChecked >= 6 && sAds * 2 > sChecked) { sUnsafe = true; unfoldAll(); return; }
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        if (verdict == AD) {
            foldNow(v, lp, args);
            keep(session, (ViewGroup) feed, v);
        } else if (sFolded.containsKey(v)) {
            unfoldNow(v, lp, args);
        }
    }

    private static void foldNow(View v, ViewGroup.LayoutParams lp, Object[] args) {
        boolean margins = lp instanceof ViewGroup.MarginLayoutParams;
        if (!sFolded.containsKey(v)) {
            int top = 0, bottom = 0;
            if (margins) { top = ((ViewGroup.MarginLayoutParams) lp).topMargin; bottom = ((ViewGroup.MarginLayoutParams) lp).bottomMargin; }
            sFolded.put(v, new int[]{lp.height, top, bottom});
        }
        lp.height = 0;
        if (margins) { ((ViewGroup.MarginLayoutParams) lp).topMargin = 0; ((ViewGroup.MarginLayoutParams) lp).bottomMargin = 0; }
        args[1] = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
        changed(v);
    }

    private static void unfoldNow(View v, ViewGroup.LayoutParams lp, Object[] args) {
        int[] was = sFolded.remove(v);
        if (was == null) return;
        lp.height = was[0];
        if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).topMargin = was[1]; ((ViewGroup.MarginLayoutParams) lp).bottomMargin = was[2]; }
        if (lp.height < 0) args[1] = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        else args[1] = View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.EXACTLY);
        changed(v);
    }

    private static final class Session {
        final WeakReference<Object> adapter;
        final java.util.HashMap<String, String> ads = new java.util.HashMap<String, String>();
        int count = -1;
        Session(Object a) { adapter = new WeakReference<Object>(a); }
    }

    private static Session sSession;
    private static final Map<View, String> sKeys = new WeakHashMap<View, String>();
    private static java.lang.reflect.Field sAdapterField, sHolderField, sIdField;
    private static java.lang.reflect.Method sCountMethod, sPositionMethod;
    private static boolean sNoMemory;

    private static Session session(View feed) {
        if (sNoMemory) return null;
        try {
            Object a = adapter(feed);
            if (a == null) return null;
            Session s = sSession;
            if (s == null || s.adapter.get() != a) { s = new Session(a); sSession = s; }
            int n = sCountMethod == null ? -1 : (Integer) sCountMethod.invoke(a);
            if (n >= 0) {
                if (s.count >= 0 && n < s.count - 2 && !s.ads.isEmpty()) s.ads.clear();
                s.count = n;
            }
            return s;
        } catch (Throwable t) {
            sNoMemory = true;
            return null;
        }
    }

    private static Object adapter(View feed) throws Exception {
        if (sAdapterField == null) {
            for (Class<?> k = feed.getClass(); k != null && k != ViewGroup.class && sAdapterField == null; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    Class<?> t = f.getType();
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || t.isPrimitive() || t.getName().startsWith("java.") || t.getName().startsWith("android.")) continue;
                    boolean observable = false;
                    for (java.lang.reflect.Field g : t.getDeclaredFields()) if (android.database.Observable.class.isAssignableFrom(g.getType())) { observable = true; break; }
                    if (!observable) continue;
                    f.setAccessible(true);
                    sAdapterField = f;
                    for (java.lang.reflect.Method m : t.getDeclaredMethods())
                        if (java.lang.reflect.Modifier.isAbstract(m.getModifiers()) && m.getReturnType() == int.class && m.getParameterTypes().length == 0) { m.setAccessible(true); sCountMethod = m; break; }
                    java.lang.reflect.Method pos = null;
                    int found = 0;
                    for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getReturnType() == int.class && p.length == 1 && p[0] == View.class) { pos = m; found++; }
                    }
                    if (found == 1) { pos.setAccessible(true); sPositionMethod = pos; }
                    break;
                }
            }
            if (sAdapterField == null) throw new IllegalStateException("no adapter field");
        }
        return sAdapterField.get(feed);
    }

    private static String key(View feed, View card) {
        try {
            Object holder = holder(card);
            if (holder != null && sIdField != null) {
                long id = sIdField.getLong(holder);
                if (id != -1) return "i" + id;
            }
            if (sPositionMethod == null) return null;
            int pos = (Integer) sPositionMethod.invoke(null, card);
            return pos < 0 ? null : "p" + pos;
        } catch (Throwable t) { return null; }
    }

    private static Object holder(View card) throws Exception {
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp == null) return null;
        if (sHolderField != null && sHolderField.getDeclaringClass().isInstance(lp)) return sHolderField.get(lp);
        for (Class<?> k = lp.getClass(); k != null && k != ViewGroup.MarginLayoutParams.class && k != ViewGroup.LayoutParams.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object h = f.get(lp);
                if (h == null || !holds(h, card)) continue;
                sHolderField = f;
                java.lang.reflect.Field id = null;
                int longs = 0;
                for (Class<?> c = h.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                    for (java.lang.reflect.Field g : c.getDeclaredFields())
                        if (!java.lang.reflect.Modifier.isStatic(g.getModifiers()) && g.getType() == long.class) { id = g; longs++; }
                if (longs == 1) { id.setAccessible(true); sIdField = id; }
                return h;
            }
        }
        return null;
    }

    private static boolean holds(Object h, View card) {
        for (Class<?> c = h.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field g : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(g.getModifiers()) || g.getType() != View.class) continue;
                try { g.setAccessible(true); if (g.get(h) == card) return true; } catch (Throwable ignored) { }
            }
        }
        return false;
    }

    private static String fingerprint(View card) {
        ArrayList<View> views = new ArrayList<View>();
        collect(card, views);
        StringBuilder b = new StringBuilder();
        for (View v : views) {
            CharSequence t = v instanceof TextView ? ((TextView) v).getText() : null;
            if (t != null && t.length() > 0) b.append(t).append('|');
            CharSequence d = v.getContentDescription();
            if (d != null && d.length() > 0) b.append(d).append('|');
            if (b.length() > 400) break;
        }
        return b.length() > 400 ? b.substring(0, 400) : b.toString();
    }

    private static void fill(Session s, View card) {
        String key = sKeys.get(card);
        String was = key == null ? null : s.ads.get(key);
        if (was == null || was.length() > 0) return;
        String now = fingerprint(card);
        if (now.length() > 0) s.ads.put(key, now);
    }

    private static boolean labelled(View card) {
        ArrayList<View> views = new ArrayList<View>();
        collect(card, views);
        for (View v : views) {
            if (described(v.getContentDescription())) return true;
            if (v instanceof TextView && label(((TextView) v).getText())) return true;
        }
        return false;
    }

    private static void keep(Session s, ViewGroup feed, View card) {
        if (s == null) return;
        String key = key(feed, card);
        if (key == null) return;
        sKeys.put(card, key);
        if (!s.ads.containsKey(key) || s.ads.get(key).length() == 0) s.ads.put(key, fingerprint(card));
    }

    private static void verify(Session s, View card, int[] st) {
        String key = sKeys.get(card);
        if (s == null || key == null) { st[3] = 2; return; }
        String now = fingerprint(card);
        if (now.length() == 0) return;
        st[3] = 2;
        String was = s.ads.get(key);
        if (was == null || was.length() == 0) { s.ads.put(key, now); return; }
        if (was.equals(now)) return;
        if (labelled(card)) { s.ads.put(key, now); return; }
        s.ads.clear();
        st[0] = UNKNOWN;
        st[1] = 0;
    }

    private static void fold(final View card) {
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp == null) return;
        boolean margins = lp instanceof ViewGroup.MarginLayoutParams;
        if (sFolded.containsKey(card)) {
            if (card.getHeight() == 0) return;
            note(card);
        } else {
            note(card);
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
        if (sFolded.containsKey(card)) note(card);
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
