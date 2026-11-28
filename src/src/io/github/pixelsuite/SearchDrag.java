package io.github.pixelsuite;

import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class SearchDrag {
    interface Host { boolean allowed(); void changed(String slots); void dropped(String slots); }

    private static final char[] SIDES = {'L', 'I', 'R'};
    private final SearchDock dock;
    private final View owner;
    private final Host host;
    private final float[] local = new float[2];
    private SearchDock.Zone grabbed;
    private PixelSearch.Slot moving;
    private boolean dragging, removing;
    private float downX, downY, rawX, rawY, lift;
    private String last;

    SearchDrag(SearchDock dock, View owner, Host host) { this.dock = dock; this.owner = owner; this.host = host; }

    boolean active() { return dragging; }

    static List<PixelSearch.Slot> grouped(List<PixelSearch.Slot> in) {
        ArrayList<PixelSearch.Slot> out = new ArrayList<PixelSearch.Slot>();
        for (char side : SIDES) for (PixelSearch.Slot s : in) if (s.side == side) out.add(s);
        return out;
    }

    private static int order(char c) { return c == 'L' ? 0 : c == 'I' ? 1 : 2; }

    boolean down(MotionEvent e) {
        dock.toLocal(e.getRawX(), e.getRawY(), local);
        grabbed = dock.zoneAt(local[0], local[1]);
        downX = e.getRawX(); downY = e.getRawY();
        dragging = false; removing = false; last = null;
        return grabbed != null;
    }

    void move(MotionEvent e) {
        if (grabbed == null) return;
        rawX = e.getRawX(); rawY = e.getRawY();
        if (!dragging) {
            if (SearchDock.spare(grabbed.key)) return;
            if (Math.hypot(rawX - downX, rawY - downY) < dock.dp(6)) return;
            if (!host.allowed()) { grabbed = null; return; }
            dragging = true;
            moving = grabbed.slot;
            dock.dragKey = grabbed.key;
            owner.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        dock.toLocal(rawX, rawY, local);
        boolean out = dock.outside(local[1]);
        if (out != removing) { removing = out; owner.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
        List<PixelSearch.Slot> list = grouped(PixelSearch.slots(dock.getContext()));
        for (int i = list.size() - 1; i >= 0; i--) if (SearchDock.key(list.get(i)).equals(grabbed.key)) list.remove(i);
        if (!removing) {
            char side = dock.sideAt(local[0]);
            if (PixelSearch.count(list, side) >= PixelSearch.max(side)) {
                for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).side == side && "empty".equals(list.get(i).type)) { list.remove(i); break; }
            }
            if (PixelSearch.count(list, side) >= PixelSearch.max(side)) side = moving.side;
            if (PixelSearch.count(list, side) < PixelSearch.max(side)) {
                int index = dock.indexAt(side, local[0], grabbed.key), at = 0, seen = 0;
                for (; at < list.size(); at++) {
                    char s = list.get(at).side;
                    if (order(s) > order(side)) break;
                    if (s == side) { if (seen == index) break; seen++; }
                }
                moving.side = side;
                list.add(at, moving);
            }
        }
        String next = PixelSearch.encode(list);
        if (!next.equals(last)) {
            if (last != null) owner.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            last = next;
            host.changed(next);
        }
        owner.invalidate();
    }

    void up(boolean keep) {
        boolean was = dragging;
        String value = last;
        if (was && keep && dock.dragKey != null) { dock.toLocal(rawX, rawY - dock.dp(10), local); dock.settle(dock.dragKey, local[0], local[1]); }
        dragging = false; removing = false; grabbed = null; last = null;
        dock.dragKey = null;
        if (was) host.dropped(keep ? value : null);
        owner.invalidate();
    }

    void draw(Canvas c) {
        lift += ((dragging ? 1 : 0) - lift) * 0.3f;
        if (grabbed == null || !dragging) return;
        int s = c.save();
        int[] at = new int[2]; owner.getLocationOnScreen(at);
        dock.toLocal(at[0], at[1], local);
        c.translate(-local[0], -local[1]);
        dock.toLocal(rawX, rawY - dock.dp(10), local);
        dock.drawLifted(c, grabbed, local[0], local[1], 1 + 0.18f * lift, removing ? 110 : 255);
        c.restoreToCount(s);
        if (lift < 0.98f) owner.invalidate();
    }
}
