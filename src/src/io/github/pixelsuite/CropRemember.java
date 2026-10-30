package io.github.pixelsuite;

import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;

final class CropRemember {
    private CropRemember() {}

    private static RectF userBox, userLimits;
    private static boolean applied;
    private static Object pending;
    private static long pendingUntil;
    private static java.lang.ref.WeakReference<Object> editor = new java.lang.ref.WeakReference<Object>(null);
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    static void applied() { applied = true; textFromCrop = false; }

    private static String lastPage;
    private static volatile boolean textFromCrop;
    private static WeakReference<View> composeRef = new WeakReference<View>(null);

    static void pageSeen(String page) {
        if (page == null || page.equals(lastPage)) return;
        if ("INITIAL".equals(page)) CropKeep.newPicture();
        CropKeep.pageChanged();
        lastPage = page;
    }

    private static View compose() {
        View c = composeRef.get();
        if (c == null || !c.isAttachedToWindow()) { c = ComposeTree.composeView(ScreenshotEditor.canvasView.get()); composeRef = new WeakReference<View>(c); }
        return c;
    }

    private static String[] toolLabels() {
        java.util.List<String> l = new java.util.ArrayList<String>();
        try {
            android.content.Context c = ScreenshotEditor.app();
            if (c != null) for (String n : new String[]{"edit_menu_icon_normal_stroke_accessibility", "edit_menu_icon_highlight_stroke_accessibility", "edit_menu_icon_eraser_accessibility", "edit_menu_icon_caption_accessibility"}) {
                int id = c.getResources().getIdentifier(n, "string", ScreenshotEditor.PACKAGE);
                if (id != 0) l.add(c.getString(id));
            }
        } catch (Throwable ignored) { }
        l.add("Normal stroke"); l.add("Highlight stroke"); l.add("Eraser"); l.add("Add caption");
        return l.toArray(new String[0]);
    }

    private static ComposeTree.Node cropButton(java.util.List<ComposeTree.Node> nodes) { return ComposeTree.cropButton(nodes, toolLabels()); }

    static void itemTapped() {
        try {
            String page = TopBarKeep.currentPage();
            java.util.List<ComposeTree.Node> nodes = ComposeTree.clickables(compose());
            ComposeTree.Node crop = cropButton(nodes);
            boolean on = "CROP_TOOL".equals(page) || (crop != null && crop.selected);
            if (crop == null) { }
            if (on) textFromCrop = true;
        } catch (Throwable ignored) { }
    }

    static void donePressed() {
        boolean from = textFromCrop;
        textFromCrop = false;
        if (!from) return;
        pendingPress = true; sawCaption = false; tappedAt = -1;
        pressWhenPlaced(0);
    }

    static volatile boolean pendingPress, sawCaption;
    private static int tappedAt = -1;

    private static void pressWhenPlaced(final int attempt) {
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (!pendingPress) return;
                String page = TopBarKeep.currentPage();
                if (("READY".equals(page) || page == null) && (attempt >= 3 || sawCaption)) {
                    if (tappedAt < 0 && !sawCaption) { tappedAt = attempt; CaptionDeselect.tapAway(true); pressWhenPlaced(attempt + 1); return; }
                    if (tappedAt >= 0 && attempt < tappedAt + 2) { pressWhenPlaced(attempt + 1); return; }
                    int r = pressCrop();
                    if (r != 0 || attempt >= 60) { pendingPress = false; return; }
                } else if ("CROP_TOOL".equals(page) || attempt >= 60) { pendingPress = false; return; }
                pressWhenPlaced(attempt + 1);
            }
        }, attempt == 0 ? 0 : 25);
    }

    static int pressCrop() {
        try {
            if ("CROP_TOOL".equals(TopBarKeep.currentPage())) { return -1; }
            View compose = compose();
            java.util.List<ComposeTree.Node> nodes = ComposeTree.clickables(compose);
            ComposeTree.Node crop = cropButton(nodes);
            if (crop == null) return 0;
            if (crop.selected) { return -1; }
            return ComposeTree.press(ScreenshotEditor.activity(), compose, crop) ? 1 : -1;
        } catch (Throwable t) { return -1; }
    }

    private static void pressCropSoon(final int attempt) {
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (pressCrop() != 0) return;
                if (attempt < 15) pressCropSoon(attempt + 1);
                else { }
            }
        }, attempt == 1 ? 0 : 100);
    }

    static void newState(final Object state) {
        try {
            Object a = ScreenshotEditor.activity();
            if (a != editor.get()) { editor = new java.lang.ref.WeakReference<Object>(a); userBox = null; userLimits = null; applied = false; }
            boolean was = applied;
            applied = false;
            if (was || userBox == null || userLimits == null || same(userBox, userLimits)) { pending = null; return; }
            pending = state;
            pendingUntil = SystemClock.uptimeMillis() + 1500;
            main.post(CropRemember::put);
        } catch (Throwable ignored) { }
    }

    private static void put() {
        try {
            Object state = pending;
            if (state == null || SystemClock.uptimeMillis() > pendingUntil) { pending = null; return; }
            RectF box = ScreenshotEditor.cropRect(state), limits = ScreenshotEditor.cropBounds();
            if (box == null || limits == null || userBox == null || userLimits == null) return;
            if (!same(limits, userLimits)) { pending = null; return; }
            if (!same(box, limits)) return;
            ScreenshotEditor.setCropBox(state, userBox);
        } catch (Throwable ignored) { }
    }

    static void boxDrew(final Object state) {
        try {
            RectF box = ScreenshotEditor.cropRect(state), limits = ScreenshotEditor.cropBounds();
            if (box == null || limits == null || limits.isEmpty()) return;
            if (pending != null) {
                if (SystemClock.uptimeMillis() <= pendingUntil) {
                    if (state == pending && same(box, limits)) main.post(CropRemember::put);
                    return;
                }
                pending = null;
            }
            userBox = box; userLimits = limits;
        } catch (Throwable ignored) { }
    }

    private static boolean same(RectF a, RectF b) {
        float e = 1.5f;
        return Math.abs(a.left - b.left) < e && Math.abs(a.top - b.top) < e && Math.abs(a.right - b.right) < e && Math.abs(a.bottom - b.bottom) < e;
    }
}
