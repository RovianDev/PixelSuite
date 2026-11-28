package io.github.pixelsuite;

import android.content.ComponentName;
import android.os.UserHandle;
import android.view.View;
import java.lang.reflect.Method;

final class PausedShortcuts {
    private PausedShortcuts() {}

    private static final ThreadLocal<Object> SHOWING = new ThreadLocal<Object>();

    static boolean pausedOnly(Object info) {
        try {
            if (info == null || !LauncherFeatures.type("model.data.ItemInfoWithIcon").isInstance(info)) return false;
            int f = ((Number) LauncherFeatures.get(info, "runtimeStatusFlags")).intValue();
            return (f & 4) != 0 && (f & (1 | 2 | 8 | 16 | 32)) == 0;
        } catch (Throwable t) { return false; }
    }

    private static boolean app(Object info) {
        try { return ((Number) LauncherFeatures.get(info, "itemType")).intValue() == 0; } catch (Throwable t) { return false; }
    }

    static void install() {
        try {
            Xp.hookAllMethods(LauncherFeatures.type("popup.PopupDataProvider"), "getShortcutCountForItem", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length != 1 || !(p.getResult() instanceof Integer) || (Integer) p.getResult() != 0) return;
                        Object info = p.args[0];
                        if (!pausedOnly(info) || !app(info)) return;
                        Object target = LauncherFeatures.call(info, "getTargetComponent");
                        if (!(target instanceof ComponentName)) return;
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) LauncherFeatures.get(LauncherFeatures.get(p.thisObject, "mBgDataModel"), "deepShortcutMap");
                        Object key = LauncherFeatures.type("util.ComponentKey").getConstructor(ComponentName.class, UserHandle.class).newInstance(target, LauncherFeatures.get(info, "user"));
                        Object n = map.get(key);
                        if (n instanceof Integer && (Integer) n > 0) { p.setResult(n); }
                    } catch (Throwable t) { }
                }
            });
            for (String owner : new String[]{"popup.PopupControllerImpl", "popup.PopupControllerForAppIcon"})
                try { Class<?> c = LauncherFeatures.type(owner); Xp.deoptimizeAll(c, "showForIcon"); Xp.deoptimizeAll(c, "show"); } catch (Throwable ignored) { }
        } catch (Throwable t) { }
        try {
            Xp.hookAllMethods(LauncherFeatures.type("popup.PopupControllerForAppIcon"), "show", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Object info = p.args.length == 1 && p.args[0] instanceof View ? ((View) p.args[0]).getTag() : null;
                        if (pausedOnly(info) && app(info)) { SHOWING.set(info); }
                    } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    if (SHOWING.get() == null) return;
                    SHOWING.remove();
                }
            });
            Class<?> util = LauncherFeatures.type("util.ShortcutUtil");
            Xp.hookAllMethods(util, "isActive", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    Object info = SHOWING.get();
                    if (info != null && Boolean.FALSE.equals(p.getResult()) && p.args.length == 1 && p.args[0] == info) p.setResult(Boolean.TRUE);
                }
            });
            Xp.deoptimizeAll(util, "supportsShortcuts");
            Xp.deoptimizeAll(LauncherFeatures.type("popup.PopupDataProvider"), "getShortcutCountForItem");
            Xp.deoptimizeAll(LauncherFeatures.type("popup.PopupContainerWithArrow"), "showComposePopup");
        } catch (Throwable t) { }
        try {
            Xp.hookAllMethods(LauncherFeatures.type("popup.PopupContainerWithArrow"), "showComposePopup", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Object info = LauncherFeatures.get(p.thisObject, "itemInfo");
                        if (SHOWING.get() != null || !pausedOnly(info) || !app(info) || LauncherFeatures.hidesShortcuts(p.thisObject, info)) return;
                        LauncherFeatures.call(p.thisObject, "loadAppShortcuts", info);
                    } catch (Throwable t) { }
                }
            });
        } catch (Throwable t) { }
        try {
            Class<?> floating = LauncherFeatures.type("AbstractFloatingView");
            Method open = null;
            for (Method m : floating.getDeclaredMethods()) if (m.getName().equals("getOpenView") && m.getParameterTypes().length == 2 && m.getParameterTypes()[1] == int.class) open = m;
            final Method getOpen = open;
            if (getOpen != null) {
                getOpen.setAccessible(true);
                Xp.hookAllMethods(LauncherFeatures.type("popup.PopupContainer$Companion"), "dismissInvalidPopup", new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) {
                        try {
                            if (p.args.length != 1) return;
                            Object popup = getOpen.invoke(null, p.args[0], 2);
                            if (popup == null) return;
                            Object view = LauncherFeatures.get(popup, "originalView");
                            if (view instanceof View && ((View) view).isAttachedToWindow() && pausedOnly(LauncherFeatures.get(popup, "itemInfo"))) p.setResult(null);
                        } catch (Throwable ignored) { }
                    }
                });
            }
        } catch (Throwable t) { }
    }
}
