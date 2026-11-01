package io.github.pixelsuite;

import io.github.libxposed.api.XposedModule;

public class Module extends XposedModule {

    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String FILES = "com.google.android.apps.nbu.files";
    private static final String GMS = "com.google.android.gms";

    public Module() {
        super();
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        Xp.attach(this);
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        ClassLoader cl = param.getClassLoader();
        Safe.run(() -> Flashlight.installSystem(cl));
        Safe.run(() -> VolumeCamera.installSystem(cl));
        Safe.run(() -> ThreeFinger.installSystem(cl));
        Safe.run(() -> DoubleTapSleep.installSystem(cl));
        Safe.run(() -> DoubleTapWake.installSystem(cl));
        Safe.run(() -> PrivacyLock.installSystem(cl));
        Safe.run(() -> ScreenshotWarm.installSystem(cl));
        Safe.run(() -> MiscSystem.installSystem(cl));
        Safe.run(() -> OtaBlocker.installSystem(cl));
        Safe.run(() -> ShowFps.installSystem(cl));
        Safe.run(() -> CallRecording.installSystem(cl));
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        ClassLoader cl = param.getClassLoader();
        String pkg = param.getPackageName();
        if (GMS.equals(pkg)) return;
        Safe.run(() -> SettingsScrollEdges.install(cl, pkg));
        if (SYSTEMUI.equals(pkg) && screenshotProcess()) {
            Safe.run(() -> SuperDark.installSystemUi(cl));
            Safe.run(() -> SystemToasts.install(cl));
            Safe.run(() -> ScreenshotWarm.installScreenshotProcess(cl));
        } else if (SYSTEMUI.equals(pkg)) {
            Safe.run(() -> VolumeCamera.installSystemUi(cl));
            Safe.run(() -> ScreenRecordTidy.install(cl));
            Safe.run(() -> SuperDark.installSystemUi(cl));
            Safe.run(() -> StatusBarFade.installSystemUi(cl));
            Safe.run(() -> SystemToasts.install(cl));
            Safe.run(() -> PromptStyle.install(cl));
        } else if (LAUNCHER.equals(pkg)) {
            Safe.run(() -> ClearAll.installLauncher(cl));
            Safe.run(() -> RecentsLock.install(cl));
            Safe.run(() -> RecentsLandscape.install(cl));
            Safe.run(() -> PrivacyRecents.install(cl));
            Safe.run(() -> PrivacyFolders.install(cl));
            Safe.run(() -> PrivacyBadges.install(cl));
            Safe.run(() -> PrivacyGuard.install(cl));
            Safe.run(() -> FeedBackdrop.install());
            Safe.run(() -> OneAppFolders.install(cl));
            Safe.run(() -> LauncherFeatures.install(cl));
            Safe.run(() -> DrawerScrollEdges.install());
            Safe.run(() -> DrawerSectionLabel.install());
            Safe.run(() -> FolderRules.install());
            Safe.run(() -> SuperDarkApps.installLauncher(cl));
            Safe.run(() -> SuperDarkApps.installLauncherSettings(cl));
        } else if (ShareSheet.PACKAGE.equals(pkg)) {
            Safe.run(() -> ShareSheet.install(cl));
        } else if (FILES.equals(pkg)) {
            Safe.run(() -> FilesSort.install(cl));
        } else if (PhotosEditor.PACKAGE.equals(pkg)) {
            Safe.run(() -> PhotosEditor.install(cl));
            Safe.run(() -> PhotosDarkPanel.install());
            Safe.run(() -> PhotosToasts.install(cl));
            Safe.run(() -> PhotosShareColors.install(cl));
            Safe.run(() -> ComposeFrost.install("Google Photos editors", null, true, "VideoEditorActivity|PhotoEditorNextActivity"));
        } else if (ScreenshotEditor.PACKAGE.equals(pkg)) {
            Safe.run(() -> ScreenshotEditor.install(cl, param.getApplicationInfo()));
        } else if (StoreBlocker.PLAY.equals(pkg)) {
            Safe.run(() -> StoreBlocker.install(cl));
        } else if (WatchEsim.PACKAGE.equals(pkg)) {
            Safe.run(() -> WatchEsim.install(cl, param.getApplicationInfo()));
        } else if (DoNotAnnoy.PACKAGE.equals(pkg)) {
            Safe.run(() -> DoNotAnnoy.install(cl));
        } else if (DoNotAnnoy.PHONE.equals(pkg)) {
            Safe.run(() -> DoNotAnnoy.installDecline(cl));
        } else if (ChromeCosmetic.PACKAGE.equals(pkg)) {
            Safe.run(() -> ChromeCosmetic.install(cl));
            Safe.run(() -> ChromeFeedAds.install(cl));
            Safe.run(() -> KeyboardBarTweak.install(pkg));
            Safe.run(() -> DevOptionsHide.install(pkg));
        } else if (CallRecording.PACKAGE.equals(pkg)) {
            Safe.run(() -> CallRecording.install(cl, param.getApplicationInfo()));
        } else if (DocumentsSort.GOOGLE.equals(pkg) || DocumentsSort.AOSP.equals(pkg)) {
            Safe.run(() -> DocumentsSort.install(cl));
        } else if (AliasProvider.GOOGLE.equals(pkg)) {
            Safe.run(() -> GoogleAliases.install());
            Safe.run(() -> SuperDarkApps.install(cl, pkg));
            if (SETTINGS.equals(pkg)) Safe.run(() -> SettingsSearchTone.install());
            Safe.run(() -> GoogleFeedAds.install(cl));
        } else if (SuperDarkApps.GBOARD.equals(pkg) || SETTINGS.equals(pkg) || SuperDarkApps.SETTINGS_SEARCH.equals(pkg)
                || "com.google.android.packageinstaller".equals(pkg) || "com.android.packageinstaller".equals(pkg)) {
            Safe.run(() -> SuperDarkApps.install(cl, pkg));
            if (SETTINGS.equals(pkg)) Safe.run(() -> SettingsSearchTone.install());
            if (!SuperDarkApps.GBOARD.equals(pkg)) Safe.run(() -> SuperDarkDialogs.install());
            if (SETTINGS.equals(pkg)) Safe.run(() -> OtaBlocker.installSettings(cl));
            if (SETTINGS.equals(pkg)) Safe.run(() -> RefreshRate90.installSettings(cl));
            if (SuperDarkApps.GBOARD.equals(pkg)) Safe.run(() -> KeyboardBarTweak.install(pkg));
        } else {
            Safe.run(() -> KeyboardBarTweak.install(pkg));
            Safe.run(() -> DevOptionsHide.install(pkg));
        }
    }

    private static boolean screenshotProcess() {
        try { String name = (String) android.app.Application.class.getMethod("getProcessName").invoke(null); return name != null && name.endsWith(":screenshot"); }
        catch (Throwable t) { return false; }
    }

}
