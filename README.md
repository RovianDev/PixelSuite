# Pixel Suite

A small LSPosed module that adds the Pixel quality-of-life tweaks Google never shipped. Every feature can be switched on or off from a settings screen inside LSPosed.

> **Tested only on a Pixel 11 Pro.** It may work on other devices, but I can't promise it. I'll keep updating it, and I'm open to ideas (see [Feedback](#feedback--ideas)).

---

## Features

🧹 **Always-visible "Clear all"**  
The "Clear all" button stays visible in Pixel Launcher's recents screen, so there's no need to scroll to the last page to find it.

🔦 **Double-press power for flashlight**  
Double-press the power button to toggle the flashlight, from anywhere.

📷 **Double-press volume down for camera**  
Double-press the volume down button to open Pixel Camera from the always-on display. It won't launch if media is playing or a phone call is active.

👆 **Tap or Double Tap to check phone**  
One master toggle, **Tap or Double tap to wake**, with everything on one page. Choose how the screen wakes: **Tap to wake** or **Double tap to wake** (turning one on turns the other off). A separate option lets you **double-tap the lock screen to turn the screen off**, anywhere on the lock screen and never while the PIN pad is open. A **Lock screen shortcuts** switch controls the lock screen cards: off by default, tapping the clock, alarm, weather and other cards doesn't launch apps and doesn't vibrate (the clock still expands); on gives you the stock behavior, including the stock vibration. The options appear in the module's settings and in **Settings → System → Gestures → Tap or Double Tap to check phone**, right above "Double tap to sleep".

📸 **Three-finger screenshot**  
Swipe down with three fingers to take a screenshot. Inspired by Chinese phones like Xiaomi, OPPO and Vivo.

🔒 **Double-tap to lock**  
Double-tap an empty spot on the home screen to lock your phone. Also inspired by Chinese phones.

📁 **Newest-first in Files by Google**  
By default, Files by Google sorts folders A-Z. This puts the newest first, so a file you just downloaded is easy to find when you're attaching it to another app like Gemini.

Every feature has its own toggle in the module settings, so you only run what you want.

## What's new in v1.1.1

- "Tap or Double tap to wake" is now a single master toggle
- New "Lock screen shortcuts" switch: no app launches or tap vibrations from lock screen cards when off
- Fixed stray vibrations, cards launching apps after a double tap, and double tap getting disarmed after swipes or a long press

## Requirements

- A rooted device (KernelSU / KernelSU-Next, Magisk or APatch)
- [LSPosed](https://lsposed.org) with LSPosed API 102
- Android version: **Android 17** (tested on Pixel 11 Pro)

## Installation

1. Download the latest APK from the [Releases](../../releases) page.
2. Install it like any other APK.
3. Grant it root access in your root manager (KernelSU / KernelSU-Next, Magisk or APatch).
4. Open **LSPosed → Modules → Pixel Suite** and enable it.
5. Make sure the recommended scope is selected: **System Framework, System UI, Settings, Pixel Launcher, Files and Files by Google**.
6. Reboot, then open the module's settings screen and choose which features you want.
7. **Refresh toggles:** Open the Settings app, swipe to close it, then reopen it to refresh the toggles in **System → Gestures**. (Note: This may crash Settings the first time – this is a Google bug, not ours. It happens even with stock gestures. After your phone boots, just wait for all settings to load.)

**Updating:** install the new APK over the old one (no uninstall needed) and reboot.

## Building from Source

Want to build Pixel Suite yourself? See [BUILD.md](src/BUILD.md) for detailed instructions.

**Quick start (Linux / macOS):**
```bash
cd src
bash build.sh
```

The compiled APK will be in `out/PixelSuite.apk`.

## Compatibility

| Device | Status |
|---|---|
| Pixel 11 Pro | Tested |
| Other Pixels / other devices | Untested. Reports welcome |

## Feedback & ideas

Found a bug, or have an idea for a new feature? Open an [issue](../../issues). For bugs, please include:

- Device and Android version
- Root method and LSPosed version
- An LSPosed log (Logs → Save)

I read every request, and good ideas make it into future versions. The "Tap or Double Tap to check phone" feature came from a request by Reddit user [Lord_Sithek](https://www.reddit.com/user/Lord_Sithek/).

## Support the project

Pixel Suite is free and open source, and always will be. If it saves you some taps and you'd like to say thanks:

- [Buy Me a Coffee ☕](https://buymeacoffee.com/RovianDev)
- [Patreon](https://patreon.com/RovianDev)

Starring the repo and sharing it helps just as much. ❤️

## Credits

- [LSPosed](https://lsposed.org): the framework that makes this possible
- Xposed API (libxposed): module API

## Disclaimer

Modules that hook system components can cause instability. Use at your own risk and keep a backup. Pixel Suite is an independent project and is **not affiliated with, endorsed by, or sponsored by Google LLC**. "Pixel", "Pixel Launcher", "Google" and "Files by Google" are trademarks of Google LLC and are used only to describe compatibility.

## License

Released under the [GPL-3.0 License](LICENSE).

---

Made by **Rovian.Dev**
