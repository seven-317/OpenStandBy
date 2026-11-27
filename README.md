# OpenStandBy

[繁體中文](README.zh-TW.md)

OpenStandBy is a free, full-screen landscape dashboard for Android. I built it for a phone on a desk dock or in a motorcycle mount, where I want to see what's playing and reach the playback controls without reopening the music app.

OpenStandBy keeps the screen on and opens to the player. Swipe up for the clock or down for the GPS speedometer.

## Get it running

OpenStandBy runs on Android 9 (API 28) and newer. To run it from source, open this repository in Android Studio, set the Gradle JDK to 17, and install Android SDK Platform 36. Sync Gradle and run the `app` configuration on a phone or emulator.

> [!IMPORTANT]
> OpenStandBy needs notification access to read the Android media session shared by your music app.

1. Open OpenStandBy and tap **Open notification access settings**. Enable access for OpenStandBy, then return to the app.
2. Start a track in Spotify, YouTube Music, or another app that exposes an Android media session. Return to OpenStandBy to see the track and control playback.

OpenStandBy needs no separate account or API key. If a track is playing and the connection screen stays open, tap **Reconnect**.

## Player, clock, and speedometer

- **Player.** See the track, artist, and cover over blurred album art. Skip tracks, play, or pause from the dashboard. Drag the progress bar when the music app supports seeking. Spotify shuffle and repeat are available when its media session exposes those controls.
- **Clock.** See the date and choose a digital, analog, split-flap, or seven-segment face.
- **GPS speedometer.** Grant precise location on the speedometer screen. Speed appears in km/h once the phone gets a GPS fix.

The gear opens settings for the current screen. The clock and speedometer each have their own background. Pick a photo, frame it for landscape, and adjust its blur. From any screen's settings, open **Global settings** → **Interface language** to choose English, Traditional Chinese (Taiwan), or follow the system language.

## Lyrics

Tap the microphone to open lyrics. When a track has timed lyrics, the current line is highlighted and scrolls into view. Tap a timed line to seek if the music app supports it. Tap an empty part of the screen to show the progress bar and playback controls, or use × to return to the player.

Online lookup checks **LRCLIB → LRCMux → NetEase Cloud Music** when the NetEase fallback is enabled. It uses the first line-synced result and falls back to plain text when that's all it finds. An LRC line with multiple timestamps appears at each of those times.

**Try again** repeats the lookup. **Convert Simplified Chinese to Traditional** is available in **Lyrics options** and the player settings. Conversion runs on the device and keeps the lyrics in their original language.

### Import a lyrics file

> [!TIP]
> If the online lookup misses a track or picks another recording, search by title and artist for an LRC file that matches the version you're playing. [酷歌詞 (Kugeci)](https://www.kugeci.com) · [Lyricsify](https://www.lyricsify.com/)

While the track is loaded, open **Lyrics options** or the player settings and tap **Import lyrics file**. The app accepts UTF-8 or UTF-16 `.lrc`, `.yrc`, and `.txt` files. The imported file takes priority for that track. Use **Remove local lyrics** in **Lyrics options** to return to online lookup.

### Enable the NetEase fallback

> [!NOTE]
> A standard build uses LRCLIB and LRCMux. NetEase is enabled when the build includes an API Enhanced service URL.

Deploy an instance of the community-maintained API Enhanced project. Replace the example address with its HTTPS base URL when building the app. [NetEase Cloud Music API Enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)

```sh
./gradlew :app:assembleDebug -PneteaseLyricsBaseUrl=https://lyrics.example.com/
```

The app sends the track title and artist to that instance, then requests lyrics by the matched song ID. It accepts LRC and uses line start times when it receives YRC.

## Build and test

The app uses Kotlin, Jetpack Compose, and Material 3. From the repository root, run the Gradle wrapper to build the debug APK and check the app.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. In Windows Command Prompt, use `gradlew.bat` in place of `./gradlew`.

## Contributing

Open an issue for missing or incorrect lyrics and include the track title, artist, and album or recording version. Pull requests for additional sources and better matching are welcome. [Issues](https://github.com/seven-317/OpenStandBy/issues) · [Pull requests](https://github.com/seven-317/OpenStandBy/pulls)

## Special thanks

- Thanks to the LRCLIB maintainers and contributors for the lyrics database and public API. [LRCLIB](https://lrclib.net/docs)
- Thanks to the LRCMux maintainers for aggregating lyrics sources behind an LRCLIB-compatible API. [LRCMux](https://lrcmux.dev/)
- Thanks to the independent developers who maintain and contribute to NetEase Cloud Music API Enhanced. Their open-source project provides the song search and lyric endpoints used by the NetEase fallback. [NetEase Cloud Music API Enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)

## License

OpenStandBy's own source code is available under the MIT License. [LICENSE](LICENSE)
