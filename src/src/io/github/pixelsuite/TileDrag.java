package io.github.pixelsuite;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

final class TileDrag implements View.OnTouchListener {
    private final View.OnTouchListener chrome;

    TileDrag(View.OnTouchListener chrome) {
        this.chrome = chrome;
        TileAdd.listener(chrome);
        if (chrome != null) ChromeFolders.prepare(chrome);
    }

    private static WeakReference<View> sLastGrid = new WeakReference<View>(null);
    static void grid(View g) { if (sLastGrid.get() != g) sLastGrid = new WeakReference<View>(g); }
    static View lastGrid() { return sLastGrid.get(); }

    private static final WeakHashMap<View, Object> sHandlers = new WeakHashMap<View, Object>();
    static void handler(View tile, Object chromeListener) { sHandlers.put(tile, chromeListener); }
    static Object handlerOf(View tile) { return sHandlers.get(tile); }

    private static WeakReference<View> sTile = new WeakReference<View>(null);
    private static WeakReference<ViewGroup> sGrid = new WeakReference<ViewGroup>(null);
    private static float sDownX, sDownY;
    private static long sDownAt;
    private static boolean sArmed, sDragging;
    private static Runnable sArm, sSettle;
    private static final ArrayList<View> sItems = new ArrayList<View>();
    private static int sFrom, sTo, sPending = -1;
    private static float sZ;
    private static long sBusyUntil;
    private static final WeakHashMap<Object, Long> sShown = new WeakHashMap<Object, Long>();
    private static WeakReference<View> sMerge = new WeakReference<View>(null);
    private static ChromeFolders.Ring sRing;

    static void shown(Object w) { if (sTile.get() != null) sShown.put(w, android.os.SystemClock.uptimeMillis()); }

    private static final ArrayList<WeakReference<View>> sOrder = new ArrayList<WeakReference<View>>();

    static boolean busy(ViewGroup g) {
        if (sGrid.get() != g) return false;
        if (sDragging) return true;
        if (android.os.SystemClock.uptimeMillis() >= sBusyUntil) return false;
        if (orderChanged(g)) { sBusyUntil = 0; return false; }
        return true;
    }

    private static ArrayList<View> tiles(ViewGroup g) {
        ArrayList<View> list = new ArrayList<View>();
        for (int i = 0; i < g.getChildCount(); i++) if (ChromeSpeedDial.isTile(g.getChildAt(i))) list.add(g.getChildAt(i));
        return list;
    }

    private static boolean orderChanged(ViewGroup g) {
        ArrayList<View> now = tiles(g);
        if (now.size() != sOrder.size()) return true;
        for (int i = 0; i < now.size(); i++) if (sOrder.get(i).get() != now.get(i)) return true;
        return false;
    }

    private static boolean draggable(View v) { return ChromeFolders.isFolder(v) || pinned(v); }

    @Override public boolean onTouch(View v, MotionEvent e) {
        try {
            if (!(v.getParent() instanceof ViewGroup) || !ChromeSpeedDial.on(v, true) || !draggable(v)) return chrome != null && chrome.onTouch(v, e);
            return handle(v, e);
        } catch (Throwable t) {
            try { return chrome != null && chrome.onTouch(v, e); } catch (Throwable ignored) { return false; }
        }
    }

    private boolean handle(final View v, MotionEvent e) {
        final float d = v.getResources().getDisplayMetrics().density;
        int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                reset(false);
                sTile = new WeakReference<View>(v);
                sGrid = new WeakReference<ViewGroup>((ViewGroup) v.getParent());
                sDownX = e.getRawX();
                sDownY = e.getRawY();
                sDownAt = android.os.SystemClock.uptimeMillis();
                sArm = new Runnable() {
                    public void run() {
                        if (sTile.get() != v) return;
                        sArmed = true;
                        sZ = v.getZ();
                        v.setZ(sZ + 8 * d);
                        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(160).setInterpolator(ChromeTabTransition.DECELERATE).start();
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        try { Haptics.tick(v.getContext()); } catch (Throwable ignored) { }
                    }
                };
                v.postDelayed(sArm, 300);
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (sTile.get() != v) return false;
                float dx = e.getRawX() - sDownX, dy = e.getRawY() - sDownY;
                if (!sArmed) {
                    if (dx * dx + dy * dy > slop * slop) { v.removeCallbacks(sArm); reset(false); }
                    return false;
                }
                if (!sDragging && dx * dx + dy * dy > slop * slop) {
                    start(v);
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    try { v.onTouchEvent(cancel); } catch (Throwable ignored) { }
                    cancel.recycle();
                }
                if (sDragging) { move(v, dx, dy); return true; }
                return false;
            }
            case MotionEvent.ACTION_UP: {
                if (sTile.get() != v) return false;
                v.removeCallbacks(sArm);
                if (sDragging) { drop(v); return true; }
                if (sArmed) settleBack(v);
                reset(false);
                return false;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (sTile.get() != v) return false;
                v.removeCallbacks(sArm);
                if (sDragging) { revert(); return true; }
                if (sArmed) settleBack(v);
                reset(false);
                return false;
            }
        }
        return sDragging;
    }

    private static void start(View v) {
        ViewGroup g = sGrid.get();
        if (g == null) return;
        sDragging = true;
        v.cancelLongPress();
        for (java.util.Map.Entry<Object, Long> en : new ArrayList<java.util.Map.Entry<Object, Long>>(sShown.entrySet())) {
            if (en.getValue() < sDownAt) continue;
            try {
                Object w = en.getKey();
                if (w instanceof android.widget.PopupWindow) { if (((android.widget.PopupWindow) w).isShowing()) ((android.widget.PopupWindow) w).dismiss(); }
                else if (w instanceof android.app.Dialog) { if (((android.app.Dialog) w).isShowing()) ((android.app.Dialog) w).dismiss(); }
            } catch (Throwable ignored) { }
        }
        sShown.clear();
        sItems.clear();
        for (View c : ChromeSpeedDial.order(g)) if (draggable(c)) sItems.add(c);
        sFrom = sTo = sItems.indexOf(v);
        sPending = -1;
        if (sFrom < 0) sDragging = false;
    }

    private static float iconCenterY(View t) {
        return t.getTop() + t.getTranslationY() + ChromeFolders.iconTopIn(t) + ChromeFolders.iconSize(t.getContext()) / 2f;
    }

    private static void move(View v, float dx, float dy) {
        final ViewGroup g = sGrid.get();
        if (g == null || sItems.isEmpty()) return;
        v.setTranslationX(dx);
        v.setTranslationY(dy);
        float s = ChromeFolders.iconSize(v.getContext());
        float cx = v.getLeft() + dx + v.getWidth() / 2f, cy = v.getTop() + dy + ChromeFolders.iconTopIn(v) + s / 2f;
        View over = null;
        if (!ChromeFolders.isFolder(v)) {
            for (View t : sItems) {
                if (t == v) continue;
                float tx = t.getLeft() + t.getTranslationX() + t.getWidth() / 2f, ty = iconCenterY(t);
                float ddx = tx - cx, ddy = ty - cy;
                if (ddx * ddx + ddy * ddy < (s * 0.55f) * (s * 0.55f) && (ChromeFolders.canTake(t) || pinned(t))) { over = t; break; }
            }
        }
        if (over != sMerge.get()) merge(g, v, over);
        if (over != null) { if (sSettle != null) { g.removeCallbacks(sSettle); sSettle = null; sPending = -1; } return; }
        int best = sTo;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < sItems.size(); i++) {
            int[] p = ChromeSpeedDial.slot(g, ChromeSpeedDial.slotOf(g, sItems, i), v);
            float sx = p[0] + v.getWidth() / 2f - (v.getLeft() + dx + v.getWidth() / 2f), sy = p[1] + v.getHeight() / 2f - (v.getTop() + dy + v.getHeight() / 2f);
            float dd = sx * sx + sy * sy;
            if (dd < bestD) { bestD = dd; best = i; }
        }
        if (best == sTo) { if (sSettle != null) { g.removeCallbacks(sSettle); sSettle = null; sPending = -1; } return; }
        if (best != sPending) {
            if (sSettle != null) g.removeCallbacks(sSettle);
            sPending = best;
            final View dragged = v;
            sSettle = new Runnable() { public void run() {
                sSettle = null;
                if (!sDragging || sPending < 0) return;
                sTo = sPending;
                sPending = -1;
                reflow(g, dragged);
                try { Haptics.tick(dragged.getContext()); } catch (Throwable ignored) { }
            }};
            g.postDelayed(sSettle, 160);
        }
    }

    private static void merge(ViewGroup g, View dragged, View over) {
        sMerge = new WeakReference<View>(over);
        if (sRing == null) sRing = new ChromeFolders.Ring(g.getContext());
        if (over == null) {
            try { g.getOverlay().remove(sRing); } catch (Throwable ignored) { }
            sRing.scale = 0f;
            dragged.animate().scaleX(1.12f).scaleY(1.12f).setDuration(140).start();
            return;
        }
        float s = ChromeFolders.iconSize(g.getContext());
        sRing.setBounds(0, 0, g.getWidth(), g.getHeight());
        sRing.at(over.getLeft() + over.getTranslationX() + over.getWidth() / 2f, iconCenterY(over), s * 0.72f);
        try { g.getOverlay().remove(sRing); } catch (Throwable ignored) { }
        g.getOverlay().add(sRing);
        sRing.scale = 0f;
        sRing.grow(g);
        dragged.animate().scaleX(0.92f).scaleY(0.92f).setDuration(140).start();
        try { Haptics.tick(g.getContext()); } catch (Throwable ignored) { }
    }

    private static void clearRing(ViewGroup g) {
        sMerge = new WeakReference<View>(null);
        if (sRing != null && g != null) { try { g.getOverlay().remove(sRing); } catch (Throwable ignored) { } sRing.scale = 0f; }
    }

    private static void reflow(ViewGroup g, View dragged) {
        ArrayList<View> order = new ArrayList<View>(sItems);
        order.remove(dragged);
        order.add(Math.max(0, Math.min(order.size(), sTo)), dragged);
        for (int j = 0; j < order.size(); j++) {
            View t = order.get(j);
            if (t == dragged) continue;
            int[] p = ChromeSpeedDial.slot(g, ChromeSpeedDial.slotOf(g, sItems, j), t);
            t.animate().translationX(p[0] - t.getLeft()).translationY(p[1] - t.getTop()).setDuration(220).setInterpolator(ChromeTabTransition.DECELERATE).start();
        }
    }

    private void drop(final View v) {
        final ViewGroup g = sGrid.get();
        sDragging = false;
        if (sSettle != null && g != null) { g.removeCallbacks(sSettle); sSettle = null; }
        final View target = sMerge.get();
        clearRing(g);
        if (g == null) { revert(); return; }
        if (target != null) {
            float s = ChromeFolders.iconSize(v.getContext());
            float tx = target.getLeft() + target.getTranslationX() - v.getLeft(), ty = iconCenterY(target) - (v.getTop() + ChromeFolders.iconTopIn(v) + s / 2f);
            sBusyUntil = android.os.SystemClock.uptimeMillis() + 1500;
            v.animate().translationX(tx).translationY(ty).scaleX(0.4f).scaleY(0.4f).alpha(0.2f).setDuration(200)
                    .setInterpolator(ChromeTabTransition.DECELERATE).withEndAction(new Runnable() {
                        public void run() {
                            v.setZ(sZ);
                            boolean ok = ChromeFolders.merge(g, v, target);
                            v.setScaleX(1f); v.setScaleY(1f);
                            if (!ok) { v.setAlpha(1f); putBack(g); return; }
                            v.setTranslationX(0); v.setTranslationY(0);
                            putBack(g);
                        }
                    }).start();
            reset(true);
            return;
        }
        sBusyUntil = android.os.SystemClock.uptimeMillis() + 1500;
        int[] p = ChromeSpeedDial.slot(g, ChromeSpeedDial.slotOf(g, sItems, sTo), v);
        final int from = sFrom, to = sTo;
        final ArrayList<View> items = new ArrayList<View>(sItems);
        final TileDrag owner = this;
        sOrder.clear();
        for (View t : tiles(g)) sOrder.add(new WeakReference<View>(t));
        v.animate().translationX(p[0] - v.getLeft()).translationY(p[1] - v.getTop()).scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(ChromeTabTransition.DECELERATE).withEndAction(new Runnable() {
                    public void run() {
                        v.setZ(sZ);
                        if (from == to) { putBack(g); return; }
                        ArrayList<View> order = new ArrayList<View>(items);
                        order.remove(v);
                        order.add(to, v);
                        ChromeFolders.positions(g, order);
                        boolean chromeMoved = false;
                        if (!ChromeFolders.isFolder(v)) {
                            ArrayList<View> before = new ArrayList<View>(), after = new ArrayList<View>();
                            for (View t : items) if (!ChromeFolders.isFolder(t)) before.add(t);
                            for (View t : order) if (!ChromeFolders.isFolder(t)) after.add(t);
                            int was = before.indexOf(v), now = after.indexOf(v);
                            if (was != now && now >= 0 && now < before.size()) chromeMoved = owner.save(v, before.get(now));
                        }
                        if (!chromeMoved) {
                            sBusyUntil = 0;
                            for (View t : items) t.animate().cancel();
                            g.requestLayout();
                            return;
                        }
                        g.postDelayed(new Runnable() { public void run() { if (sBusyUntil != 0 && !orderChanged(g)) putBack(g); } }, 1500);
                    }
                }).start();
        reset(true);
    }

    private boolean save(View moved, View target) {
        try {
            Object a = suggestion(moved), b = suggestion(target);
            Object listener = handlerOf(moved);
            if (listener == null) listener = chrome;
            if (a == null || b == null || listener == null) return false;
            for (Class<?> k = listener.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    Class<?>[] t = m.getParameterTypes();
                    if (t.length == 3 && t[0] == int.class && t[1] == a.getClass() && t[2] == a.getClass()) {
                        m.setAccessible(true);
                        m.invoke(listener, 0, a, b);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
        }
        return false;
    }

    private static void putBack(ViewGroup g) {
        sBusyUntil = 0;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            c.animate().translationX(0).translationY(0).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(ChromeTabTransition.DECELERATE).start();
        }
    }

    private static void revert() {
        ViewGroup g = sGrid.get();
        View v = sTile.get();
        if (v != null) v.setZ(sZ);
        sDragging = false;
        clearRing(g);
        if (g != null) { if (sSettle != null) g.removeCallbacks(sSettle); putBack(g); }
        sSettle = null;
        reset(false);
    }

    private static void settleBack(View v) {
        v.setZ(sZ);
        v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(ChromeTabTransition.DECELERATE).start();
    }

    private static void reset(boolean keepGrid) {
        View v = sTile.get();
        if (v != null && sArm != null) v.removeCallbacks(sArm);
        sArm = null;
        sArmed = false;
        sDragging = false;
        sPending = -1;
        sTile = new WeakReference<View>(null);
        if (!keepGrid) sItems.clear();
    }

    private static Field sSuggestionField;

    private static Object suggestion(View tile) {
        try {
            if (sSuggestionField == null || !sSuggestionField.getDeclaringClass().isInstance(tile)) {
                sSuggestionField = null;
                for (Class<?> k = tile.getClass(); k != null && k != View.class; k = k.getSuperclass()) {
                    for (Field f : k.getDeclaredFields()) if (f.getType().getName().endsWith(".SiteSuggestion")) { f.setAccessible(true); sSuggestionField = f; break; }
                    if (sSuggestionField != null) break;
                }
            }
            return sSuggestionField == null ? null : sSuggestionField.get(tile);
        } catch (Throwable t) { return null; }
    }

    static boolean pinned(View tile) {
        if (ChromeFolders.isFolder(tile)) return false;
        Object s = suggestion(tile);
        if (s == null) return false;
        try {
            Field f = s.getClass().getDeclaredField("d");
            f.setAccessible(true);
            return f.getInt(s) == 3;
        } catch (Throwable t) { return false; }
    }
}
