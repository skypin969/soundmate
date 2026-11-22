# Soundmate

A sound effect for phones that makes the bass fuller, vocals rounder, and the sound richer when a Bluetooth headset or speaker is connected. No root required.

Soundmate is not a Dolby product and is not affiliated with Dolby. Dolby and Dolby Atmos are trademarks of Dolby Laboratories.

## Features

- Turns on automatically when a Bluetooth headset or speaker connects, and turns off when it disconnects.
- The phone speaker and wired headsets stay normal.
- Settings are saved separately for each Bluetooth device.
- Presets: Music (Musik), Movie (Film), Game, Sound (Suara), Bass, and Flat (Datar).
- Custom 1 and Custom 2 (Kustom 1 and Kustom 2) for saving your own settings.
- 10-band equalizer, plus sliders for Bass booster (Penguat bass), Surround effect (Efek surround), Dialogue clarifier (Penjernih dialog), and Volume booster (Penguat volume).
- Does not request internet permission.

## Requirements

- Android 8.0 or newer. The most complete effects are available on Android 9 or newer.
- A Bluetooth headset or speaker.

## Installation

1. In the Releases section, download `Soundmate-v2.6.apk`.
2. Open the file. If asked, allow installation from this source.
3. If a Play Protect warning appears, choose the option to install anyway. Soundmate is not yet available on the Play Store.
4. Tap Install.

## How to use

1. Open Soundmate and allow notifications if asked.
2. Connect a Bluetooth headset or speaker. The effect turns on automatically.
3. Choose a preset, or adjust the sliders and equalizer yourself.
4. Long-press Custom 1 or Custom 2 to save the current settings. Tap it to use them again.
5. If you don't hear the effect yet, stop the song and play it again after Soundmate is active.

## Permissions used

- Foreground service (FOREGROUND_SERVICE): keeps the effect running when the screen is off.
- Notifications (POST_NOTIFICATIONS): shows the effect status.
- Run after boot (RECEIVE_BOOT_COMPLETED): turns the effect back on after the phone restarts, if it was enabled before.
- Audio settings (MODIFY_AUDIO_SETTINGS): changes sound settings.
- DUMP (optional): only for the All-apps mode.

## All-apps mode (optional)

This mode applies the effect to all apps that play sound. The DUMP permission can only be granted from a computer through ADB:

```
adb shell pm grant com.skypin.soundmate android.permission.DUMP
```

This mode does not run on every phone. Without this permission, Soundmate still runs; only the All-apps mode stays off.

## Limitations

- This is not the real Dolby Atmos. The results depend on the headset, speaker, and phone.
- Every device has its own sound character, so adjust the sliders to your taste.

## Updating and uninstalling

- To update, download the new version and install it over the old one. Your settings are kept.
- To uninstall, long-press the Soundmate icon and choose Uninstall.

## Source code and issues

This repo only contains the APK and this description. The source code is not shared here. If you find a problem, write it in the Issues tab.

Made by skypin969.
