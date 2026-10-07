# Pixel Suite

A small LSPosed module that adds Pixel features Google never shipped. Every feature has its own on/off switch in the module settings.

> **Tested only on a Pixel 11 Pro.** It may work on other devices. Feedback is welcome.

---

## Features

🧹 **Always-visible "Clear all"**
The Clear all button stays on screen in Pixel Launcher's recents, so you don't have to scroll to find it.

🔦 **Double-press power for flashlight**
Double-press the power button to toggle the flashlight.

📷 **Double-press volume down for camera**
Double-press volume down on the always-on display to open Pixel Camera. It won't open while media is playing or a call is active.

👆 **Tap or double tap to wake**
One setting, found in **Settings → System → Gestures → Tap or Double Tap to check phone**:
- **Tap to wake** or **Double tap to wake**. Double tap also works on the always-on display, which uses a little extra battery while it shows (none while charging). It pauses in a pocket, with Battery Saver on, or when the battery is low.
- **Double-tap the lock screen to sleep**: double-tap anywhere on the lock screen to turn the screen off. It never works while the PIN pad is open.
- **Lock screen behavior**:
  - **Modded** (recommended): the clock, alarm, weather and date act like empty space, so a double tap never opens anything by mistake.
  - **Stock**: the lock screen works as Google made it. A double tap that lands on a card can open it instead of turning the screen off.

📸 **Three-finger screenshot**
Swipe down with three fingers to take a screenshot.

🔒 **Double-tap to lock**
Double-tap an empty spot on the home screen to lock the phone.

📁 **Newest-first in Files by Google**
Sorts folders so the newest files appear first.

---

## Requirements

- A rooted device (KernelSU / KernelSU-Next, Magisk or APatch)
- [LSPosed](https://lsposed.org) with API 102
- Android 17

## Installation

1. Download the latest APK from the [Releases](../../releases) page and install it.
2. Grant it root access in your root manager.
3. Open **LSPosed → Modules → Pixel Suite** and enable it.
4. Select the scopes: **System Framework, System UI, Settings, Pixel Launcher, Files and Files by Google**.
5. Reboot, then open the module settings and choose your features.

**Updating:** install the new APK over the old one (no uninstall needed) and reboot.

**Note:** the first time you open **System → Gestures** after changing a toggle, Settings may crash. This is a Google bug that happens with stock gestures too. Reopen Settings after boot and wait for everything to load.

## Building from Source

See [BUILD.md](src/BUILD.md). Quick start on Linux or macOS:

```bash
cd src
bash build.sh
```

The APK will be in `src/out/PixelSuite.apk`.

## Feedback

Found a bug or have an idea? Open an [issue](../../issues). For bugs, include your device, Android version, root method, LSPosed version and an LSPosed log (Logs → Save).

## Support the project

Free and open source. If it helps you, you can [buy me a coffee ☕](https://buymeacoffee.com/RovianDev) or [support on Patreon](https://patreon.com/RovianDev). Starring the repo helps too.

## Credits

- [LSPosed](https://lsposed.org) and the libxposed module API

## Disclaimer

Modules that hook system components can cause instability. Use at your own risk and keep a backup. Pixel Suite is an independent project, not affiliated with or endorsed by Google LLC. "Pixel", "Pixel Launcher", "Google" and "Files by Google" are trademarks of Google LLC, used only to describe compatibility.

## License

Released under the [GPL-3.0 License](LICENSE).

---

Made by **Rovian.Dev**
