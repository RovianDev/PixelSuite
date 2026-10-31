package io.github.pixelsuite;

final class DrawerFolderDots {
    private DrawerFolderDots() {}
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Runnable REFRESH = () -> { try { DrawerFolders.refreshDots(); } catch (Throwable ignored) { } };

    static void install() {
        Xp.Callback refresh = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                MAIN.removeCallbacks(REFRESH);
                MAIN.post(REFRESH);
            }
        };
        try { Xp.hookAllMethods(LauncherFeatures.type("Launcher"), "updateNotificationDots", refresh); } catch (Throwable ignored) { }
        try { Xp.hookAllMethods(LauncherFeatures.type("notification.NotificationListener"), "dispatchUpdate", refresh); } catch (Throwable ignored) { }
        try {
            Class<?> data = LauncherFeatures.type("popup.PopupDataProvider");
            if (data != null) for (String m : new String[]{"onNotificationPosted", "onNotificationRemoved", "onNotificationFullRefresh"}) Xp.hookAllMethods(data, m, refresh);
        } catch (Throwable ignored) { }
    }
}
