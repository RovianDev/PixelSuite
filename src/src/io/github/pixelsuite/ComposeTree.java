package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Locale;

final class ComposeTree {
    private ComposeTree() {}

    private static Method mChildId, mVirtualId;

    static final class Node {
        int id; String label; boolean selected, clickable; final Rect bounds = new Rect();
        @Override public String toString() { return "'" + label + "'" + (selected ? " selected" : "") + (clickable ? " clickable" : "") + " " + bounds.toShortString(); }
    }

    static View composeView(View from) {
        for (View v = from; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            try { if (v.getAccessibilityNodeProvider() != null) return v; } catch (Throwable ignored) { }
        }
        return null;
    }

    static java.util.List<Node> clickables(View compose) {
        java.util.List<Node> out = new java.util.ArrayList<Node>();
        if (compose == null) return out;
        try {
            AccessibilityNodeProvider p = compose.getAccessibilityNodeProvider();
            if (p == null) return out;
            if (mChildId == null) {
                mChildId = AccessibilityNodeInfo.class.getMethod("getChildId", int.class);
                mVirtualId = AccessibilityNodeInfo.class.getMethod("getVirtualDescendantId", long.class);
            }
            ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
            queue.add(AccessibilityNodeProvider.HOST_VIEW_ID);
            int seen = 0;
            while (!queue.isEmpty() && seen++ < 400) {
                int id = queue.poll();
                AccessibilityNodeInfo info = p.createAccessibilityNodeInfo(id);
                if (info == null) continue;
                try {
                    if (info.isClickable()) {
                        Node n = new Node(); n.id = id; n.clickable = true; n.selected = info.isSelected();
                        CharSequence d = info.getContentDescription(), t = info.getText();
                        n.label = d != null && d.length() > 0 ? d.toString() : t != null ? t.toString() : "";
                        info.getBoundsInScreen(n.bounds);
                        out.add(n);
                    }
                    int c = info.getChildCount();
                    for (int i = 0; i < c; i++) queue.add((Integer) mVirtualId.invoke(null, (Long) mChildId.invoke(info, i)));
                } finally { try { info.recycle(); } catch (Throwable ignored) { } }
            }
            java.util.Collections.sort(out, new java.util.Comparator<Node>() {
                @Override public int compare(Node x, Node y) { return x.bounds.top != y.bounds.top ? x.bounds.top - y.bounds.top : x.bounds.left - y.bounds.left; }
            });
        } catch (Throwable t) { }
        return out;
    }

    static Node cropButton(java.util.List<Node> nodes, String[] toolLabels) {
        Node pen = null, anchor = null;
        for (Node n : nodes) {
            if (pen == null && matches(n.label, new String[]{toolLabels.length > 0 ? toolLabels[0] : null, "Normal stroke"})) pen = n;
            if (anchor == null && matches(n.label, toolLabels)) anchor = n;
        }
        if (anchor == null) return null;
        int cy = anchor.bounds.centerY(), h = Math.max(1, anchor.bounds.height());
        java.util.List<Node> row = new java.util.ArrayList<Node>();
        for (Node n : nodes) if (Math.abs(n.bounds.centerY() - cy) <= h / 2 && !n.bounds.isEmpty()) row.add(n);
        java.util.Collections.sort(row, new java.util.Comparator<Node>() {
            @Override public int compare(Node x, Node y) { return x.bounds.left - y.bounds.left; }
        });
        if (row.isEmpty()) return null;
        if (pen != null) {
            for (Node n : row) if (n.bounds.right <= pen.bounds.left + 2) return n;
            int step = 0;
            for (int i = 1; i < row.size(); i++) {
                int d = row.get(i).bounds.centerX() - row.get(i - 1).bounds.centerX();
                if (d > 0 && (step == 0 || d < step)) step = d;
            }
            if (step <= 0) step = pen.bounds.width() + Math.round(pen.bounds.width() * 0.1f);
            Node crop = new Node();
            crop.id = -1; crop.label = "(crop, by place)"; crop.clickable = true; crop.selected = false;
            crop.bounds.set(pen.bounds.left - step, pen.bounds.top, pen.bounds.right - step, pen.bounds.bottom);
            return crop.bounds.left >= 0 ? crop : null;
        }
        return row.get(0) == anchor ? null : row.get(0);
    }

    static Rect boundsOf(View compose, String[] wants) {
        if (compose == null) return null;
        try {
            AccessibilityNodeProvider p = compose.getAccessibilityNodeProvider();
            if (p == null) return null;
            if (mChildId == null) {
                mChildId = AccessibilityNodeInfo.class.getMethod("getChildId", int.class);
                mVirtualId = AccessibilityNodeInfo.class.getMethod("getVirtualDescendantId", long.class);
            }
            ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
            queue.add(AccessibilityNodeProvider.HOST_VIEW_ID);
            int seen = 0;
            while (!queue.isEmpty() && seen++ < 400) {
                AccessibilityNodeInfo info = p.createAccessibilityNodeInfo(queue.poll());
                if (info == null) continue;
                try {
                    CharSequence d = info.getContentDescription(), t = info.getText();
                    String label = d != null && d.length() > 0 ? d.toString() : t != null ? t.toString() : null;
                    if (matches(label, wants)) { Rect r = new Rect(); info.getBoundsInScreen(r); return r; }
                    int n = info.getChildCount();
                    for (int i = 0; i < n; i++) queue.add((Integer) mVirtualId.invoke(null, (Long) mChildId.invoke(info, i)));
                } finally { try { info.recycle(); } catch (Throwable ignored) { } }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    static String describe(java.util.List<Node> nodes) {
        StringBuilder b = new StringBuilder();
        for (Node n : nodes) { if (b.length() > 700) break; b.append(n).append(" | "); }
        return b.toString();
    }

    private static boolean matches(String label, String[] wants) {
        if (label == null) return false;
        String l = label.toLowerCase(Locale.ROOT).trim();
        for (String w : wants) {
            if (w == null) continue;
            String x = w.toLowerCase(Locale.ROOT).trim();
            if (l.equals(x) || l.startsWith(x + " ") || l.endsWith(" " + x) || l.contains(" " + x + " ")) return true;
        }
        return false;
    }

    static boolean press(Activity a, View compose, Node node) {
        if (node == null) return false;
        try {
            AccessibilityNodeProvider p = compose.getAccessibilityNodeProvider();
            if (node.id >= 0 && p != null && p.performAction(node.id, AccessibilityNodeInfo.ACTION_CLICK, null)) { return true; }
        } catch (Throwable t) { }
        if (a == null || node.bounds.isEmpty()) return false;
        try {
            int[] at = new int[2]; a.getWindow().getDecorView().getLocationOnScreen(at);
            float x = node.bounds.exactCenterX() - at[0], y = node.bounds.exactCenterY() - at[1];
            long t0 = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0), up = MotionEvent.obtain(t0, t0 + 40, MotionEvent.ACTION_UP, x, y, 0);
            StudioItems.injecting = true;
            try { a.getWindow().superDispatchTouchEvent(down); a.getWindow().superDispatchTouchEvent(up); }
            finally { StudioItems.injecting = false; down.recycle(); up.recycle(); }
            return true;
        } catch (Throwable t) { return false; }
    }
}
