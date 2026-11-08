package io.github.pixelsuite;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import java.lang.reflect.Method;

final class ChromeCutout {
    private ChromeCutout() {}

    static volatile boolean sActive;
    private static Method sGetInsets, sInset, sCutType, sBarsType;

    static void install() {
        try {
            sGetInsets = WindowInsets.class.getMethod("getInsets", int.class);
            sInset = WindowInsets.class.getMethod("inset", int.class, int.class, int.class, int.class);
            Class<?> type = Class.forName("android.view.WindowInsets$Type");
            sCutType = type.getMethod("displayCutout");
            sBarsType = type.getMethod("systemBars");
        } catch (Throwable t) { return; }
        Xp.hookAllMethods(ViewGroup.class, "dispatchApplyWindowInsets", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    if (!isDecor(p.thisObject) || p.args.length != 1 || !(p.args[0] instanceof WindowInsets)) return;
                    WindowInsets out = strip((View) p.thisObject, (WindowInsets) p.args[0]);
                    if (out != null) p.args[0] = out;
                } catch (Throwable ignored) { }
            }
        });
        Xp.hookAllMethods(View.class, "getRootWindowInsets", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    if (!(p.getResult() instanceof WindowInsets)) return;
                    WindowInsets out = strip((View) p.thisObject, (WindowInsets) p.getResult());
                    if (out != null) p.setResult(out);
                } catch (Throwable ignored) { }
            }
        });
    }

    private static boolean isDecor(Object v) {
        return v != null && v.getClass().getName().endsWith(".DecorView");
    }

    private static WindowInsets strip(View v, WindowInsets in) throws Exception {
        if (!ChromeLayout.enabled(v.getContext())) return null;
        Object cut = sGetInsets.invoke(in, (Integer) sCutType.invoke(null));
        Object bars = sGetInsets.invoke(in, (Integer) sBarsType.invoke(null));
        int cl = side(cut, "left"), cr = side(cut, "right");
        if (cl == 0 && cr == 0) return null;
        int l = side(bars, "left") == 0 ? cl : 0, r = side(bars, "right") == 0 ? cr : 0;
        if (l == 0 && r == 0) return null;
        sActive = true;
                return (WindowInsets) sInset.invoke(in, l, 0, r, 0);
    }

    private static int side(Object insets, String name) throws Exception {
        return insets.getClass().getField(name).getInt(insets);
    }
}
