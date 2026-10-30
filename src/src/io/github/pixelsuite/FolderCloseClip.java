package io.github.pixelsuite;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

final class FolderCloseClip {
    private FolderCloseClip() {}
    private static final String SHIFT = "pixelsuite_clip_shift";

    static void install() {
        final Class<?> pages;
        try { pages = LauncherFeatures.type("folder.FolderPagedView"); } catch (Throwable t) { return; }
        try {
            Xp.hookAllMethods(LauncherFeatures.type("graphics.ShapeDelegate$GenericPathShape"), "createRevealAnimator", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length < 3 || !pages.isInstance(p.args[0])) return;
                        View view = (View) p.args[0];
                        int dx = p.args[2] instanceof Rect ? ((Rect) p.args[2]).left : 0;
                        Xp.setAdditionalInstanceField(view, SHIFT, dx);
                        if (dx == 0) return;
                        if (p.args[1] instanceof Rect) { Rect from = new Rect((Rect) p.args[1]); from.offset(-dx, 0); p.args[1] = from; }
                        Rect to = new Rect((Rect) p.args[2]); to.offset(-dx, 0); p.args[2] = to;
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        try {
            Xp.hookAllMethods(pages, "setClipPath", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Object wrapper = p.args.length > 0 ? p.args[0] : null;
                        Object shift = Xp.getAdditionalInstanceField(p.thisObject, SHIFT);
                        if (wrapper == null) { Xp.setAdditionalInstanceField(p.thisObject, SHIFT, 0); return; }
                        if (!(shift instanceof Integer) || (Integer) shift == 0) return;
                        int dx = (Integer) shift;
                        Object moved = wrapper.getClass().getDeclaredConstructor().newInstance();
                        Path path = (Path) LauncherFeatures.get(moved, "path");
                        path.set((Path) LauncherFeatures.get(wrapper, "path"));
                        path.offset(dx, 0);
                        RectF bounds = (RectF) LauncherFeatures.get(moved, "bounds");
                        bounds.set((RectF) LauncherFeatures.get(wrapper, "bounds"));
                        bounds.offset(dx, 0);
                        p.args[0] = moved;
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }
}
