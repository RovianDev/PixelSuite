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
One toggle, **Tap or Double tap to wake**, with the choices in **Settings → System → Gestures → Tap or Double Tap to check phone**, right above "Double tap to sleep":

- **Wake the screen:** *Tap to wake* or *Double tap to wake*. With double tap, the touchscreen stays fully awake on the always-on display, so the first double tap always wakes the phone. That uses a little extra battery while the always-on display is showing (none while charging), and it pauses itself in a pocket, with Battery Saver on, or when the battery is low.
- **Double tap lock screen to sleep:** double-tap anywhere on the lock screen to turn the screen off (never while the PIN pad is open). Choose how the lock screen behaves:
  - **Modded behavior** (recommended): the alarm, weather and date under the clock act like empty space and lock screen taps don't vibrate, so a double tap never opens anything by mistake.
  - **Stock behavior**: the lock screen stays as Google made it, with tappable cards and tap vibrations. The trade-off: a double tap that lands on a card can open it instead of turning the screen off.

📸 **Three-finger screenshot**  
Swipe down with three fingers to take a screenshot. Inspired by Chinese phones like Xiaomi, OPPO and Vivo.

🔒 **Double-tap to lock**  
Double-tap an empty spot on the home screen to lock your phone. Also inspired by Chinese phones.

📁 **Newest-first in Files by Google**  
By default, Files by Google sorts folders A-Z. This puts the newest first, so a file you just downloaded is easy to find when you're attaching it to another app like Gemini.

Every feature has its own toggle in the module settings, so you only run what you want.

## What's new in v1.1.2

- **Tap or Double tap to wake is now one setting.** Double tap the lock screen to sleep is part of it, and with *Double tap to wake* one double tap now works on the always-on display too (the separate "One double tap on Always On Display" switch is gone).
- **New lock screen choice:** *Modded behavior* (recommended) or *Stock behavior*, replacing the old "Lock screen shortcuts" switch.
- **Fixed:** unlocking with your fingerprint right after locking could pull the notification shade down.
- **Fixed:** a double tap to wake right after locking could be missed while the lock screen was still coming up.
- **Lighter on battery:** the lock screen touch listener now only runs while the lock screen can show (before, it saw every touch all day), the three-finger screenshot listener only runs while that feature is on, the flashlight shares Pixel Suite's background thread instead of keeping its own, and internal lookups and Files sorting are cached.
- **Fixed:** in the module's settings screen, a switch flipped while the screen was reloading could jump back.

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

The compiled APK will be in `src/out/PixelSuite.apk`.

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
