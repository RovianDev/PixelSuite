package io.github.pixelsuite;
final class FeatureScopes {
 private FeatureScopes(){}
 static String doNotAnnoy(boolean pixelStack){return pixelStack?"• **IMS Stack** (com.android.imsstack)\n• **Phone Services** (com.android.phone)\n\nSelect both, then reboot. Later toggle changes apply instantly.":"• **Phone Services** (com.android.phone)\n\nSelect it, then reboot. Later toggle changes apply instantly.";}

 static String get(String id){
  if(NavButtons.KEY.equals(id))return "• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — navigation buttons beside the gesture bar\n• **System Framework** (system) — apps keep their bottom menus above the buttons\n• **Gboard** (com.google.android.inputmethod.latin) — keyboard dismiss button";
  if(id==null||"home".equals(id))return null;
  if(PowerHold.KEY.equals(id))return "• **System Framework** (system) — the power button\n• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — Circle to Search";
  if(PinAuto.KEY.equals(id))return "• **System UI** (com.android.systemui) — the lock screen PIN";
  if("aod".equals(id)||AodPlus.KEY_CHARGING.equals(id)||AodPlus.KEY_AFTER.equals(id))return "• **System UI** (com.android.systemui) — always-on display\n• **System Framework** (system) — starts the always-on display even with the other lock screen display options off";
  if(Feature.DOUBLE_TAP_SLEEP.equals(id)||Feature.TAP_CHECK.equals(id)||Feature.FLASHLIGHT.equals(id)||Feature.THREE_FINGER.equals(id))return "• **System Framework** (system)";
  if(Feature.VOLDOWN_CAMERA.equals(id)||"buttons".equals(id))return "• **System Framework** (system)\n• **System UI** (com.android.systemui)";
  if(ScreenRecordTidy.KEY.equals(id))return "• **System UI** (com.android.systemui)";
  if(Feature.SCREENSHOT_FREE_CROP.equals(id)||Feature.SCREENSHOT_DOODLES.equals(id))return "• **Pixel Studio** (com.google.android.apps.pixel.creativeassistant) — screenshot editor\n\nSave your edits first; changing this option restarts the editor.";
  if(Feature.PHOTOS_DOODLES.equals(id))return "• **Google Photos** (com.google.android.apps.photos) — photo editor\n\nSave your edits first; changing this option restarts Photos.";
  if("doodles".equals(id))return "• **Pixel Studio** (com.google.android.apps.pixel.creativeassistant) — screenshots\n• **Google Photos** (com.google.android.apps.photos) — photos";
  if("gestures".equals(id))return "• **System Framework** (system) — wake, sleep and screenshots\n• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — app dragging\n• **System UI** (com.android.systemui) — recording controls\n• **Pixel Studio** (com.google.android.apps.pixel.creativeassistant) — screenshot cropping";
  if(SuperDark.KEY.equals(id))return SettingsScrollEdges.scopeNote()+"• **System UI** (com.android.systemui)\n• **Pixel Launcher** (com.google.android.apps.nexuslauncher)\n• **Google** (com.google.android.googlequicksearchbox)\n• **Gboard** (com.google.android.inputmethod.latin)\n• **Settings** (com.android.settings)\n• **Settings Services** (com.google.android.settings.intelligence)\n• **Package installer** (com.google.android.packageinstaller / com.android.packageinstaller)\n\nFor editor styling, also select **Pixel Studio** (com.google.android.apps.pixel.creativeassistant) and **Google Photos** (com.google.android.apps.photos). Reboot after adding scopes.";
  if("themes".equals(id))return SettingsScrollEdges.scopeNote()+"• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — icons, folders, menus and the drawer soft edge\n• **System UI** (com.android.systemui) — system menus and messages\n• **Pixel Studio** (com.google.android.apps.pixel.creativeassistant)\n• **Google Photos** (com.google.android.apps.photos) — editor menus\n\nSuper Dark lists its extra scopes on its own page.";
  if(LauncherFeatures.RENAME.equals(id)||LauncherFeatures.ICONS.equals(id))return "• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — launcher icons and names\n• **Google** (com.google.android.googlequicksearchbox) — home-screen Google search";
  if("recents".equals(id))return "• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — recent apps\n• **System UI** (com.android.systemui) — landscape status bar";
  if("launcher".equals(id))return "• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — launcher features\n• **Google** (com.google.android.googlequicksearchbox) — customized apps in Google search\n• **System UI** (com.android.systemui) — toast position";
  if("clear_badge".equals(id)||"drawer".equals(id)||LauncherFeatures.GRID.equals(id)||LauncherFeatures.DRAWER.equals(id)||LauncherFeatures.DRAG_PILL.equals(id)||LauncherFeatures.HIDE_SHORTCUTS.equals(id)||LauncherFeatures.LABEL_SIZE.equals(id)||FolderDrag.TWO_FINGER.equals(id)||MultiDrag.KEY.equals(id)||Feature.CLEAR_ALL.equals(id)||RecentsLock.KEY.equals(id))return "• **Pixel Launcher** (com.google.android.apps.nexuslauncher)";
  if("files".equals(id)||Feature.FILES_SORT.equals(id))return "• **Files by Google** (com.google.android.apps.nbu.files) — file browser\n• **Files (system)** (com.google.android.documentsui / com.android.documentsui) — system file picker";
  if("share_menu".equals(id))return "• **Share sheet** (com.android.intentresolver)\n• **Google Photos** (com.google.android.apps.photos) — matching Photos share-page colors";
  if("privacy".equals(id))return "• **System Framework** (system) — app protection\n• **Pixel Launcher** (com.google.android.apps.nexuslauncher) — app and folder locks\n• **System UI** (com.android.systemui) — authentication prompts\n\nSet up your phone's screen lock, then reboot after adding scopes.";
  if("do_not_annoy".equals(id))return doNotAnnoy(false);
  if("store_blocker".equals(id))return "• **Play Store** (com.android.vending)\n• **System Framework** (system), to restore a saved version";
  if("store_backups".equals(id))return "• **System Framework** (system), to restore a saved version (also an older one)";
  if("ota_blocker".equals(id))return "• **System Framework** (system) — update blocking\n• **Settings** (com.android.settings) — update and reset controls";
  if("blockers".equals(id))return "• **Play Store** (com.android.vending) — app update blocking\n• **System Framework** (system)\n• **Settings** (com.android.settings) — OTA and factory-reset blocking";
  if("google_dialer".equals(id))return "• **Phone** (com.google.android.dialer)\n• **System Framework** (system) — recording support\n\nReopen Phone after adding its scope.";
  if("google_app".equals(id))return "• **Google** (com.google.android.googlequicksearchbox)";
  if("gboard".equals(id))return "• **Gboard** (com.google.android.inputmethod.latin)";
  if("pixel_watch".equals(id))return "• **Pixel watch** (com.google.android.apps.wear.companion)";
  if("dark_pages".equals(id))return "• **Chrome** (com.android.chrome) — Dark pages\n\nRestart Chrome after adding its scope.";
  if("chrome".equals(id)||"ad_blocker".equals(id))return "• **Chrome** (com.android.chrome) — empty ad-box cleanup\n\nRestart Chrome after adding its scope. Ad-domain blocking needs no scope.";
  if("google_news".equals(id))return "• **Chrome** (com.android.chrome) — ad-box cleanup in articles opened with Chrome\n\nGoogle News (com.google.android.apps.magazines) needs no scope. Ad-domain blocking uses the system-wide blocker.";
  if("google_apps".equals(id))return null;
  if("miscellaneous".equals(id))return "• **System Framework** (system) — secure screenshots, accessibility hiding, Hide Developer Options and FPS\n• **System UI** (com.android.systemui) — faster screenshots\n• **Settings** (com.android.settings) — refresh rate slider\n• **Gboard** (com.google.android.inputmethod.latin) — keyboard bar\n\nHide Developer Options works globally; no individual app scopes needed.";
  return null;
 }
}
