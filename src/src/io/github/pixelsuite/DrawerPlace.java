package io.github.pixelsuite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class DrawerPlace {
    private static final long SETTLE_MS = 170, MOVE_MS = 300, LAND_MS = 240;
    private static final PathInterpolator LAND = new PathInterpolator(0.3f, 0f, 0.3f, 1f);
    private static final PathInterpolator GLIDE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private final View icon, cell;
    private final ViewGroup layer;
    private final String key;
    private final long user;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<View, Boolean> moved = new IdentityHashMap<View, Boolean>();
    private ViewGroup list;
    private List<String> keys = new ArrayList<String>();
    private Map<Integer, Integer> slotOfPosition = new HashMap<Integer, Integer>();
    private int columns = 4, shown = -1, pending = -1, lastX, lastY;
    private boolean done, bottomGone;
    private int bottomEdge;
    private int scroll;
    private Runnable task;
    private final Runnable scroller = new Runnable() { public void run() { scrollStep(); } };

    DrawerPlace(View source, ViewGroup layer) {
        this.icon = source; this.cell = cellOf(source); this.layer = layer;
        Integer id = DrawerFolders.folderIds.get(source.getTag());
        this.key = "folder:" + id;
        LauncherData.Folder f = DrawerFolders.record(source.getTag());
        this.user = f == null ? 0 : f.user;
        attach();
    }

    private static View cellOf(View v) {
        View c = v;
        for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
            Object tag = ((View) p).getTag();
            if (tag != null && DrawerFolders.isFolder(tag)) c = (View) p; else break;
        }
        return c;
    }

    private void attach() {
        ViewParent p = cell.getParent();
        if (p instanceof ViewGroup) list = (ViewGroup) p;
        try { columns = ((Number) LauncherFeatures.get(LauncherFeatures.get(list, "mApps"), "mNumAppsPerRowAllApps")).intValue(); }
        catch (Throwable older) { try { columns = ((Number) LauncherFeatures.get(list, "mNumAppsPerRow")).intValue(); } catch (Throwable ignored) { } }
        if (columns <= 0) columns = 4;
        scan();
    }

    private void scan() {
        List<String> out = new ArrayList<String>(); Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        try {
            List<?> items = (List<?>) LauncherFeatures.get(LauncherFeatures.get(list, "mApps"), "mAdapterItems");
            Class<?> appInfo = LauncherFeatures.type("model.data.AppInfo");
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i); int vt = ((Number) LauncherFeatures.get(item, "viewType")).intValue();
                String k = null;
                if (DrawerFolders.folderType(vt)) k = "folder:" + ((vt & ~DrawerFolders.BASE) >>> 10);
                else if (vt == 2) {
                    Object info = LauncherFeatures.get(item, "itemInfo");
                    if (info != null && appInfo.isInstance(info) && !LauncherFeatures.privateItem(info)) k = LauncherFeatures.identity(info);
                }
                if (k == null) continue;
                map.put(i, out.size()); out.add(k);
            }
        } catch (Throwable ignored) { }
        keys = out; slotOfPosition = map;
    }

    boolean usable() { return list != null && keys.contains(key); }

    void over(int x, int y) {
        if (done || !usable()) return;
        lastX = x; lastY = y;
        if (!bottomGone && DrawerHomeTarget.updateBottom(x, y, layer)) {
            bottomEdge = DrawerHomeTarget.bottomTop(layer);
            dropBottomPill(); Haptics.tick(layer.getContext());
        }
        int dir = edge(y);
        if (dir != 0 && list.canScrollVertically(dir)) { startScroll(dir); return; }
        if (scroll != 0) stopScroll();
        int to = slotAt(x, y);
        aim(to);
    }

    private int edge(int y) {
        int[] r = listRect(); int zone = FrostedSurface.dp(layer.getContext(), 56);
        if (y < r[1] + zone) return -1;
        if (bottomGone && y > bottomStart(r, zone)) return 1;
        return 0;
    }

    private int bottomStart(int[] r, int zone) {
        int start = r[3] - zone;
        if (bottomEdge > 0) start = Math.min(start, bottomEdge - FrostedSurface.dp(layer.getContext(), 20));
        return start;
    }

    private float depth() {
        int[] r = listRect(); int zone = FrostedSurface.dp(layer.getContext(), 56);
        float d;
        if (scroll < 0) d = (r[1] + zone - lastY) / (float) zone;
        else {
            int from = bottomStart(r, zone), to = Math.max(from + zone, layer.getHeight() - FrostedSurface.dp(layer.getContext(), 12));
            d = (lastY - from) / (float) Math.max(1, to - from);
        }
        return Math.max(0f, Math.min(1f, d));
    }

    private void aim(int to) {
        if (to < 0 || to == pending) return;
        pending = to;
        if (task != null) handler.removeCallbacks(task);
        final int target = to;
        task = new Runnable() { public void run() { task = null; if (!done && target != shown) show(target); } };
        handler.postDelayed(task, shown < 0 ? SETTLE_MS + 60 : SETTLE_MS);
    }

    void externalScroll() {
        if (done) return;
        dropBottomPill();
        if (scroll != 0) stopScroll();
        if (task != null) { handler.removeCallbacks(task); task = null; }
        if (!moved.isEmpty()) resetNow();
        shown = -1; pending = -1;
    }

    private void dropBottomPill() {
        if (bottomGone) return;
        bottomGone = true; DrawerHomeTarget.hideBottom(true);
    }

    private void startScroll(int direction) {
        if (scroll == direction) return;
        dropBottomPill();
        boolean was = scroll != 0;
        scroll = direction; speed = 0f;
        if (!was) { resetNow(); shown = -1; pending = -1; if (task != null) { handler.removeCallbacks(task); task = null; } }
        list.removeCallbacks(scroller); list.postOnAnimation(scroller);
    }

    private void stopScroll() {
        scroll = 0; speed = 0f;
        if (list != null) list.removeCallbacks(scroller);
    }

    private float speed;
    private void scrollStep() {
        if (done || scroll == 0 || list == null) return;
        if (!list.canScrollVertically(scroll) || edge(lastY) != scroll) {
            stopScroll();
            over(lastX, lastY);
            return;
        }
        float density = layer.getResources().getDisplayMetrics().density;
        float d = depth();
        float want = density * (1.5f + 30f * d * d);
        speed += (want - speed) * 0.18f;
        list.scrollBy(0, Math.round(scroll * Math.max(1f, speed)));
        list.postOnAnimation(scroller);
    }

    private int[] listRect() {
        int[] a = new int[2], o = new int[2]; list.getLocationOnScreen(a); layer.getLocationOnScreen(o);
        int left = a[0] - o[0], top = a[1] - o[1];
        return new int[] {left, top + list.getPaddingTop(), left + list.getWidth(), top + list.getHeight() - list.getPaddingBottom()};
    }

    private int[] base(View v) {
        int[] a = new int[2], o = new int[2]; v.getLocationOnScreen(a); layer.getLocationOnScreen(o);
        return new int[] {Math.round(a[0] - o[0] - v.getTranslationX()), Math.round(a[1] - o[1] - v.getTranslationY())};
    }

    private int slotOf(View child) {
        try {
            int position = ((Number) LauncherFeatures.call(list, "getChildAdapterPosition", child)).intValue();
            Integer slot = slotOfPosition.get(position);
            return slot == null ? -1 : slot;
        } catch (Throwable t) { return -1; }
    }

    private final class Grid {
        final Map<Integer, View> cells = new HashMap<Integer, View>();
        final float[] colX = new float[64];
        final boolean[] known = new boolean[64];
        float y0, rowH, cellW, cellH; boolean ok, rtl;
        Grid() {
            int refSlot = -1, refY = 0, otherSlot = -1, otherY = 0;
            for (int i = 0; i < list.getChildCount(); i++) {
                View c = list.getChildAt(i); if (c.getVisibility() == View.GONE) continue;
                int s = slotOf(c); if (s < 0) continue;
                cells.put(s, c);
                int[] b = base(c); int col = s % columns;
                if (col < colX.length) { colX[col] = b[0]; known[col] = true; }
                cellW = Math.max(cellW, c.getWidth()); cellH = Math.max(cellH, c.getHeight());
                if (refSlot < 0) { refSlot = s; refY = b[1]; }
                else if (s / columns != refSlot / columns && otherSlot < 0) { otherSlot = s; otherY = b[1]; }
            }
            if (refSlot < 0) return;
            rowH = otherSlot >= 0 ? (otherY - refY) / (float) (otherSlot / columns - refSlot / columns) : cellH;
            if (rowH <= 0) rowH = cellH;
            y0 = refY - (refSlot / columns) * rowH;
            int a = -1, b = -1;
            for (int c = 0; c < Math.min(columns, colX.length); c++) if (known[c]) { if (a < 0) a = c; else { b = c; break; } }
            rtl = a >= 0 && b >= 0 ? colX[b] < colX[a] : list.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            float step = a >= 0 && b >= 0 ? (colX[b] - colX[a]) / (b - a) : (rtl ? -cellW : cellW);
            for (int c = 0; c < Math.min(columns, colX.length); c++) if (!known[c] && a >= 0) colX[c] = colX[a] + (c - a) * step;
            ok = a >= 0 && rowH > 0;
        }
        float x(int slot) { return colX[Math.min(colX.length - 1, slot % columns)]; }
        float y(int slot) { return y0 + (slot / columns) * rowH; }
    }

    private int slotAt(int x, int y) {
        Grid g = new Grid(); if (!g.ok) return -1;
        int row = (int) Math.floor((y - g.y0) / g.rowH);
        int col = 0; float best = Float.MAX_VALUE;
        for (int c = 0; c < Math.min(columns, g.colX.length); c++) {
            float d = Math.abs(g.colX[c] + g.cellW / 2f - x);
            if (d < best) { best = d; col = c; }
        }
        int slot = row * columns + col;
        int last = keys.size() - 1;
        if (row < 0) slot = 0;
        if (slot > last) slot = last;
        return Math.max(0, slot);
    }

    private List<String> moving() {
        List<String> m = new ArrayList<String>(); m.add(key);
        return m;
    }

    private List<String> orderFor(int to) {
        List<String> m = moving();
        int from = keys.indexOf(key);
        List<String> rest = new ArrayList<String>();
        int insert = 0;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i); if (m.contains(k)) continue;
            rest.add(k);
            if (from < to ? i <= to : i < to) insert++;
        }
        insert = Math.max(0, Math.min(insert, rest.size()));
        rest.addAll(insert, m);
        return rest;
    }

    private void show(int to) {
        Grid g = new Grid(); if (!g.ok) return;
        List<String> order = orderFor(to);
        List<String> m = moving();
        long delay = 0;
        List<Integer> slots = new ArrayList<Integer>(g.cells.keySet()); java.util.Collections.sort(slots);
        for (int s : slots) {
            View c = g.cells.get(s); String k = keys.get(s);
            if (m.contains(k)) continue;
            int next = order.indexOf(k); if (next < 0) continue;
            int[] b = base(c);
            float dx = g.x(next) - b[0], dy = g.y(next) - b[1];
            if (Math.abs(c.getTranslationX() - dx) < 1 && Math.abs(c.getTranslationY() - dy) < 1) continue;
            moved.put(c, Boolean.TRUE);
            c.animate().cancel();
            c.animate().translationX(dx).translationY(dy).setDuration(MOVE_MS).setStartDelay(Math.min(delay, 90)).setInterpolator(GLIDE).start();
            if (dx != 0 || dy != 0) delay += 12;
        }
        shown = to;
    }

    private void resetNow() {
        for (View v : moved.keySet()) { v.animate().cancel(); v.setTranslationX(0f); v.setTranslationY(0f); }
        moved.clear();
    }

    private void resetAnimated() {
        for (View v : moved.keySet()) {
            v.animate().cancel();
            if (v.isAttachedToWindow()) v.animate().translationX(0f).translationY(0f).setDuration(MOVE_MS).setStartDelay(0).setInterpolator(GLIDE).start();
            else { v.setTranslationX(0f); v.setTranslationY(0f); }
        }
        moved.clear();
    }

    boolean active() { return !done; }

    boolean wouldMove() {
        if (done || !usable()) return false;
        int to = target();
        return to >= 0 && !orderFor(to).equals(keys);
    }

    private int target() {
        int to = pending >= 0 ? pending : shown;
        return to >= 0 ? to : slotAt(lastX, lastY);
    }

    void restore() {
        if (done) return;
        done = true; stopScroll();
        if (task != null) { handler.removeCallbacks(task); task = null; }
        resetAnimated();
    }

    boolean commit(Object dragObject) {
        if (done) return false;
        stopScroll();
        if (task != null) { handler.removeCallbacks(task); task = null; }
        int to = target();
        if (to < 0) { restore(); return false; }
        if (to != shown) show(to);
        done = true;
        List<String> order = orderFor(to);
        if (order.equals(keys)) { resetAnimated(); return false; }
        Grid g = new Grid();
        int landing = order.indexOf(key);
        float tx = g.ok ? g.x(landing) : Float.NaN, ty = g.ok ? g.y(landing) : Float.NaN;
        if (!save(order)) { resetAnimated(); return false; }
        if (g.ok) fly(dragObject, tx, ty);
        if (!g.ok) DropThud.later(key, 0);
        LauncherFeatures.refreshOrder();
        settleAfterRebind();
        return true;
    }

    private boolean save(List<String> order) {
        List<LauncherData.Folder> folders = LauncherFeatures.data.folders();
        Map<String, LauncherData.Folder> mine = new HashMap<String, LauncherData.Folder>();
        List<LauncherData.Folder> others = new ArrayList<LauncherData.Folder>();
        for (LauncherData.Folder f : folders) { if (f.user == user && order.contains("folder:" + f.id)) mine.put("folder:" + f.id, f); else others.add(f); }
        if (mine.isEmpty()) return false;
        int tail = order.size();
        while (tail > 0 && order.get(tail - 1).startsWith("folder:")) tail--;
        List<LauncherData.Folder> next = new ArrayList<LauncherData.Folder>();
        for (int i = 0; i < order.size(); i++) {
            LauncherData.Folder f = mine.get(order.get(i)); if (f == null) continue;
            f.pos = i >= tail ? LauncherData.END + i : i;
            next.add(f);
        }
        next.addAll(others);
        return LauncherFeatures.data.save(next);
    }

    private void settleAfterRebind() {
        final View root = layer.getRootView();
        final boolean[] settled = new boolean[1];
        final Runnable settle = new Runnable() { public void run() { if (settled[0]) return; settled[0] = true; resetNow(); } };
        root.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                try { root.getViewTreeObserver().removeOnGlobalLayoutListener(this); } catch (Throwable ignored) { }
                settle.run();
            }
        });
        handler.postDelayed(settle, 500);
    }

    private void fly(Object dragObject, float cellX, float cellY) {
        final View folderIcon = icon;
        try {
            View drag = (View) LauncherFeatures.get(dragObject, "dragView");
            if (drag == null || drag.getWidth() <= 0 || drag.getHeight() <= 0) return;
            Bitmap shot = Bitmap.createBitmap(drag.getWidth(), drag.getHeight(), Bitmap.Config.ARGB_8888);
            drag.draw(new Canvas(shot));
            int[] at = new int[2], o = new int[2]; drag.getLocationOnScreen(at); layer.getLocationOnScreen(o);
            final float sw = drag.getWidth() * drag.getScaleX(), sh = drag.getHeight() * drag.getScaleY();
            final float sx = at[0] - o[0], sy = at[1] - o[1];
            float px = folderIcon.getWidth() / 2f, py = folderIcon.getPaddingTop() + folderIcon.getWidth() * 0.36f;
            try {
                Object bg = LauncherFeatures.get(folderIcon, "mBackground");
                Object ox = LauncherFeatures.get(bg, "basePreviewOffsetX"), oy = LauncherFeatures.get(bg, "basePreviewOffsetY"), size = LauncherFeatures.get(bg, "previewSize");
                if (ox instanceof Number && oy instanceof Number && size instanceof Number && ((Number) size).intValue() > 0) {
                    px = ((Number) ox).floatValue() + ((Number) size).floatValue() / 2f; py = ((Number) oy).floatValue() + ((Number) size).floatValue() / 2f;
                }
            } catch (Throwable ignored) { }
            final float ew = drag.getWidth(), eh = drag.getHeight();
            final float ex = cellX + folderIcon.getLeft() + px - ew / 2f, ey = cellY + folderIcon.getTop() + py - eh / 2f;
            final BitmapDrawable ghost = new BitmapDrawable(layer.getResources(), shot);
            ghost.setBounds(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh));
            layer.getOverlay().add(ghost);
            folderIcon.setAlpha(0f);
            final android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(LAND_MS); a.setInterpolator(LAND);
            final boolean[] landed = new boolean[1];
            final Runnable land = new Runnable() { public void run() {
                if (landed[0]) return; landed[0] = true;
                try { layer.getOverlay().remove(ghost); } catch (Throwable ignored) { }
                folderIcon.setAlpha(1f);
                DropThud.now(key);
            }};
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator v) {
                    if (landed[0]) return;
                    float t = (Float) v.getAnimatedValue();
                    float l = sx + (ex - sx) * t, tp = sy + (ey - sy) * t, w = sw + (ew - sw) * t, h = sh + (eh - sh) * t;
                    ghost.setBounds(Math.round(l), Math.round(tp), Math.round(l + w), Math.round(tp + h));
                    layer.invalidate();
                    if (v.getAnimatedFraction() >= 0.92f) land.run();
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator x) { land.run(); }
                @Override public void onAnimationCancel(android.animation.Animator x) { land.run(); }
            });
            a.start();
            handler.postDelayed(land, LAND_MS + 400);
        } catch (Throwable error) { folderIcon.setAlpha(1f); DropThud.later(key, 0); }
    }
}
