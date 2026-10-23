# Pixel Suite v2.0.1

This update brings together the folder and drag work from the recent previews, along with a cleaner recording sheet and fixes across the launcher.

## 🗂️ Folders that move with you
- Reorder apps inside drawer folders with live movement and transitions between pages. In Custom mode, apps make room under your finger and selected apps land together.
- Open a folder’s three-dot menu for **Add apps**, **Sort**, and grid size. Sort offers **Auto (A–Z)** and **Custom**, with distinct icons and animated choices.
- Home folders also get Rename and Sort in their long-press menu.
- A Remove target beside Add to home screen takes selected apps out of a drawer folder without uninstalling them.
- Folder frames fit their contents. Folder removal includes a built-in scatter animation that respects system animation preferences.

## ✌️ Move several apps
- Hold and move an app, then tap others with a second finger to build a counted stack. Drop them together into folders or onto the home screen.
- Swipe with a second finger to switch home or folder pages, or scroll the drawer. A touch stops the drawer’s momentum.
- Folder selections stay within their source folder. The drawer behind an open folder stays still.
- Remove several home shortcuts together; the apps remain installed.

## 🎥 A cleaner recording sheet
- X and Start sit beneath the recording options.
- The long warning row and its unused space are removed when Screen recording sheet is enabled. Recording still requires pressing Start.
- Turn the feature off to return to the stock prompt. These hooks target supported System UI versions.

## ✨ Menus, icons and polish
- Fixed Reset icon keeping the custom picture after resetting or changing icon shapes.
- The home-screen long-press menu stays near your hold point instead of jumping down. Its stock styling and animations are preserved.
- Taller app menus tighten their rows to keep actions such as Rename inside the panel, including apps with Bubble.
- Sort choices expand in place with their own icons and an animated arrow.
- Drawer icons follow the shape selected in Wallpaper & style.
- Fixes for invisible dragged apps, duplicate drag images, stuck or flashing folders, and a crash when moving several apps out of a home folder.
- Removed temporary diagnostics and unnecessary redraw work.

## 🔔 Official release updates
- New Updates page with automatic checks, Check now, notification controls and a direct Download and install action.
- Notices require a newer stable release on **both GitHub and Xposed**, with the same APK name, size and SHA-256 digest. Drafts, previews, tags without uploaded APKs and local builds are excluded.
- Android schedules checks about daily; opening Pixel Suite can also check, at most once every six hours. Timing depends on network and Android scheduling. This is periodic checking, not an instant push service.
- Notification permission is required. Download and install shows percentage progress, verifies the APK hash and existing signing certificate, then opens Android installation confirmation. No browser, file picker, sign-in, root installation or device telemetry is involved.

## 📖 Settings and previews
- Updated feature explanations and matching recorder, folder-sort and live-reorder demonstrations using the existing device frame, theme and animation pacing.
- Demonstrations pause between loops and stop when the page is hidden or the app closes; reduced-motion and battery-saving behavior remain respected.
- The in-app Changelog shows only the latest release under **What’s new:**, with a link to the full GitHub release history.

## Update
Install over your current build using the same signing key, keep the recommended LSPosed scope enabled, then reboot. System UI and Pixel Launcher are needed for this release’s main changes.

Designed for Pixel devices. Testing has focused on Pixel 11 Pro, Android 17 and Pixel Launcher 17; other app and system versions can behave differently. The additional update checker and new demonstrations in this release candidate still need device testing before publication.