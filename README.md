# OpenStandBy

[繁體中文](README.zh-TW.md)

OpenStandBy is a free, full-screen landscape dashboard for Android. I built it for a phone sitting on a desk dock or in a motorcycle mount, where I want to see what's playing and reach the playback controls without reopening the music app.

The screen stays on while OpenStandBy is open. It starts on the player. Swipe up for the clock or down for the GPS speedometer.

## Get it running

OpenStandBy runs on Android 9 (API 28) and newer. To install from source, open the repository root in Android Studio, set the Gradle JDK to 17, and install Android SDK Platform 36. Sync Gradle and run the `app` configuration on a connected phone or emulator.

1. Open OpenStandBy and tap **Open notification access settings**. Enable access for OpenStandBy, then return to the app.
2. Start a track in Spotify, YouTube Music, or another app that exposes an Android media session. Return to OpenStandBy to see the track and control playback.

Notification access lets OpenStandBy connect to the media session Android already has. OpenStandBy needs no separate account or API key. If a track is playing and the app stays on the connection screen, tap **Reconnect**.

## Player, clock, and speedometer

- **Player.** See the track, artist, and cover over a blurred version of the album art. Use the large buttons to skip tracks or play and pause. Drag the progress bar when the music app supports seeking. Spotify shuffle and repeat are available when its session exposes those actions.
- **Lyrics.** Tap the microphone to open lyrics or the × to return to the player. The current line is highlighted and scrolls into view. If the music app supports seeking, tap a timed line to jump to that point. Tap an empty part of the screen to show the playback controls and progress bar.
- **Clock.** See the time and date in the phone's format.
- **GPS speedometer.** Allow precise location on the speedometer screen. It displays your speed in km/h once it gets a GPS fix.

The gear opens settings for the screen you're on. Set a separate photo for the clock and speedometer, frame it for landscape, and adjust the blur on each screen. The interface supports English and Traditional Chinese (Taiwan).

## Lyrics coverage

The player checks LRCLIB first and uses LRCMux as a fallback. It prefers timed lyrics and shows plain text when that's what's available. Even with both sources, some songs or recordings still have no matching lyrics.

- **Try again** repeats the lookup. If you have the lyrics already, import a UTF-8 or UTF-16 `.lrc` or `.txt` file from **Lyrics options** or the player settings. The file is saved for that track and takes priority over online results.
- **Convert Simplified Chinese to Traditional** is available in both menus. It changes Simplified Chinese characters on the device and leaves the lyrics in their original language.

If you know a lyrics source that covers more songs or a better way to match recordings, open an issue or send a pull request. For a missed or incorrect match, include the track title, artist, and album or recording version. [Issues](https://github.com/seven-317/OpenStandBy/issues) · [Pull requests](https://github.com/seven-317/OpenStandBy/pulls)

## Build and test

The app uses Kotlin, Jetpack Compose, and Material 3. From the repository root, run the Gradle wrapper to build the debug APK and check the app.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. In Windows Command Prompt, use `gradlew.bat` in place of `./gradlew`.

## Acknowledgements

Thanks to the people maintaining LRCLIB's lyrics database and API, and to the LRCMux maintainers for providing the fallback service through an LRCLIB-compatible API. [LRCLIB](https://lrclib.net/docs) · [LRCMux](https://lrcmux.dev/)
