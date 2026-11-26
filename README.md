# OpenStandBy

[繁體中文](README.zh-TW.md)

OpenStandBy turns an Android phone into a free, full-screen landscape dashboard for a desk dock or motorcycle mount. It opens on the music player, keeps the screen on, and lets you swipe straight to a clock or GPS speedometer.

## What you can do

- **Music player** shows the current track, artist, and album art over a blurred artwork background. Large buttons handle previous, play and pause, and next. Seeking, shuffle, and repeat depend on the controls the music app exposes.
- **Lyrics mode** opens with the microphone button. Timed lyrics highlight and follow the current line, and tapping a line seeks to its timestamp when the music app supports seeking. Tap the background to show the progress bar and playback controls.
- **Clock** shows the time and date. Swipe up from the music player to open it.
- **GPS speedometer** shows your current speed in km/h. Swipe down from the music player to open it.

Each screen has its own settings. The clock and speedometer can use different photos from your library. You can crop each photo to fit the landscape view and adjust its blur independently. The interface is available in English and Traditional Chinese (Taiwan).

## First launch

1. Open OpenStandBy and tap **Open notification access settings**. Enable access for OpenStandBy, then return to the app.
2. Play a track in Spotify, YouTube Music, or another app that exposes an Android media session. OpenStandBy reads and controls that session without requiring its own account or API key.
3. Open the GPS speedometer and grant precise location access when prompted if you want to use it.

If music is playing and the player stays on the Android connection screen, tap **Reconnect**.

## Lyrics coverage

OpenStandBy looks up the track in LRCLIB first and checks LRCMux for a fallback when it needs one. It displays plain lyrics when only untimed text is available. **Some tracks still have no matching lyrics in either service.**

Use **Try again** to repeat the lookup, or import a local `.lrc` or `.txt` file from **Lyrics options** or the player settings. The imported file is saved for that track and takes priority. **Convert Simplified Chinese to Traditional** changes the script of Chinese lyrics on the device and preserves the original language.

If you know a better lyrics source or a way to match more tracks reliably, please open an issue or send a pull request. A track title, artist, and recording version help reproduce a missed match. [Issues](https://github.com/seven-317/OpenStandBy/issues) · [Pull requests](https://github.com/seven-317/OpenStandBy/pulls)

## Build from source

The app uses Kotlin, Jetpack Compose, and Material 3. It runs on Android 9 (API 28) and newer. To build it, open the repository root in Android Studio, sync Gradle, and use JDK 17 with Android SDK Platform 36.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. You can also run the `app` configuration from Android Studio on a connected phone or emulator. On Windows, use `gradlew.bat` in the commands above.

## Acknowledgements

- Thanks to the LRCLIB team and contributors for maintaining the primary lyrics database and API. [LRCLIB](https://lrclib.net/docs)
- Thanks to the LRCMux maintainers for the fallback lyrics service and its LRCLIB-compatible API. [LRCMux](https://lrcmux.dev/)
