package io.github.pixelsuite;

final class DrawerIconColors {
    static final String KEY = "pixelsuite_drawer_icon_colors";
    private static final ThreadLocal<Boolean> plain = new ThreadLocal<Boolean>();
    private DrawerIconColors() {}

    static void install() {
        try {
            Xp.hookAllMethods(LauncherFeatures.type("BubbleTextView"), "applyIconAndLabel", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try { if (LauncherFeatures.on(KEY) && drawerView(p.thisObject, p.args.length > 0 ? p.args[0] : null)) plain.set(Boolean.TRUE); }
                    catch (Throwable ignored) {}
                }
                protected void afterHookedMethod(Xp.Param p) { plain.remove(); }
            });
        } catch (Throwable ignored) {}
        try {
            Xp.hookAllMethods(LauncherFeatures.type("model.data.ItemInfoWithIcon"), "newIcon", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (LauncherFeatures.on(KEY) && drawerMember(p.thisObject)) {
                            Xp.setAdditionalInstanceField(p.thisObject, "pixelsuite_plain_before", new Object[]{plain.get()});
                            plain.set(Boolean.TRUE);
                        }
                    } catch (Throwable ignored) {}
                }
                protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Object mark = Xp.getAdditionalInstanceField(p.thisObject, "pixelsuite_plain_before");
                        if (!(mark instanceof Object[])) return;
                        Xp.setAdditionalInstanceField(p.thisObject, "pixelsuite_plain_before", null);
                        Object before = ((Object[]) mark)[0];
                        if (before == null) plain.remove(); else plain.set((Boolean) before);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
        try {
            Xp.hookAllMethods(LauncherFeatures.type("icons.BitmapInfo"), "newIcon", new Xp.Callback() {
                protected void beforeHookedMethod(Xp.Param p) { if (Boolean.TRUE.equals(plain.get())) strip(p.args); }
            });
        } catch (Throwable ignored) {}
    }

    private static void strip(Object[] args) {
        for (int i = 0; i < args.length; i++) if (args[i] instanceof Integer) { args[i] = ((Integer) args[i]) & ~1; return; }
    }

    private static boolean drawerView(Object view, Object info) {
        int display = ((Number) LauncherFeatures.get(view, "mDisplay")).intValue();
        if (display == 1 || display == 8 || display == 9) return true;
        if (display == 2) return drawerMember(info != null ? info : ((android.view.View) view).getTag());
        return false;
    }

    private static boolean drawerMember(Object info) {
        if (info == null) return false;
        try {
            int container = ((Number) LauncherFeatures.get(info, "container")).intValue();
            return container < DrawerFolders.CONTAINER && container > DrawerFolders.CONTAINER - 100000;
        } catch (Throwable ignored) { return false; }
    }
}
