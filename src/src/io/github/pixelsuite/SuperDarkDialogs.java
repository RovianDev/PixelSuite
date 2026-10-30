package io.github.pixelsuite;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;

final class SuperDarkDialogs {
    private SuperDarkDialogs() {}

    static void install() {
        Xp.hookAllMethods(Dialog.class, "onStart", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    final Dialog dialog = (Dialog) p.thisObject;
                    Window w = dialog.getWindow();
                    if (w == null) return;
                    final View decor = w.getDecorView();
                    decor.post(new Runnable() { public void run() { try { rim(dialog, decor); } catch (Throwable ignored) { } } });
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void rim(Dialog dialog, View decor) {
        Context c = dialog.getContext();
        if (!dialog.isShowing() || !SuperDark.inEffect(c) || !GlassRim.superDarkRims(c)) return;
        View content = decor.findViewById(android.R.id.content);
        if (content == null || content.getWidth() <= 0 || content.getHeight() <= 0) return;
        if (content.getForeground() instanceof DoodleOverlay.Rim || Xp.getAdditionalInstanceField(content, "pixelsuite_rim") != null) return;
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        if (content.getHeight() >= m.heightPixels * 0.9f) return;
        float radius = corner(decor.getBackground());
        if (radius <= 0) radius = FrostedSurface.dp(c, 28);
        DoodleOverlay.Rim rim = new DoodleOverlay.Rim(radius, FrostedSurface.dp(c, 1.5f));
        if (content.getWidth() < m.widthPixels * 0.97f) {
            if (content.getForeground() != null) return;
            content.setForeground(rim);
            Xp.setAdditionalInstanceField(content, "pixelsuite_rim", rim);
            return;
        }
        android.graphics.Rect box = composed(content);
        if (box == null && dialog.getClass().getName().contains("compose")) {
            int inset = Math.round(content.getWidth() * 0.075f);
            box = new android.graphics.Rect(inset, 0, content.getWidth() - inset, content.getHeight());
        }
        if (box == null || box.width() <= 0 || box.height() <= 0) return;
        rim.setBounds(box);
        content.getOverlay().add(rim);
        Xp.setAdditionalInstanceField(content, "pixelsuite_rim", rim);
    }

    private static android.graphics.Rect composed(View content) {
        java.util.ArrayList<View> views = new java.util.ArrayList<View>();
        views.add(content);
        for (int i = 0; i < views.size() && i < 60; i++) {
            View v = views.get(i);
            if (v instanceof android.view.ViewGroup) for (int k = 0; k < ((android.view.ViewGroup) v).getChildCount(); k++) views.add(((android.view.ViewGroup) v).getChildAt(k));
            android.view.accessibility.AccessibilityNodeProvider provider;
            try { provider = v.getAccessibilityNodeProvider(); } catch (Throwable t) { continue; }
            if (provider == null) continue;
            try {
                java.lang.reflect.Method childId = android.view.accessibility.AccessibilityNodeInfo.class.getDeclaredMethod("getChildId", int.class);
                childId.setAccessible(true);
                java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> queue = new java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo>();
                android.view.accessibility.AccessibilityNodeInfo root = provider.createAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeProvider.HOST_VIEW_ID);
                if (root == null) continue;
                queue.add(root);
                int[] at = new int[2];
                content.getLocationOnScreen(at);
                for (int q = 0; q < queue.size() && q < 40; q++) {
                    android.view.accessibility.AccessibilityNodeInfo n = queue.get(q);
                    android.graphics.Rect r = new android.graphics.Rect();
                    n.getBoundsInScreen(r);
                    r.offset(-at[0], -at[1]);
                    if (q > 0 && r.width() > 0 && r.width() < content.getWidth() * 0.97f && r.height() > FrostedSurface.dp(content.getContext(), 80)) return r;
                    for (int ch = 0; ch < n.getChildCount() && queue.size() < 40; ch++) {
                        long id = (Long) childId.invoke(n, ch);
                        android.view.accessibility.AccessibilityNodeInfo child = provider.createAccessibilityNodeInfo((int) (id >>> 32));
                        if (child != null) queue.add(child);
                    }
                }
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static float corner(Drawable d) {
        try {
            for (int i = 0; i < 4 && d != null; i++) {
                if (d instanceof android.graphics.drawable.GradientDrawable) return (Float) Xp.callMethod(d, "getCornerRadius");
                if (d instanceof android.graphics.drawable.InsetDrawable) d = ((android.graphics.drawable.InsetDrawable) d).getDrawable();
                else if (d instanceof android.graphics.drawable.LayerDrawable && ((android.graphics.drawable.LayerDrawable) d).getNumberOfLayers() > 0) d = ((android.graphics.drawable.LayerDrawable) d).getDrawable(0);
                else return 0;
            }
        } catch (Throwable ignored) { }
        return 0;
    }
}
