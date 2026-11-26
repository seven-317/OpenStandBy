# OpenStandBy

[English](README.md)

OpenStandBy 是一款免費的 Android 全螢幕橫向待機工具。我做這個 App，是想讓手機放在桌面底座或機車手機架上時，抬眼就能看到正在播哪首歌，也能直接控制播放。

App 開著時會保持螢幕常亮。開啟後直接進入播放器，往上滑是時鐘，往下滑是 GPS 時速表。

## 安裝與初次使用

OpenStandBy 支援 Android 9（API 28）以上。從原始碼安裝時，用 Android Studio 開啟專案根目錄，將 Gradle JDK 設成 17，並安裝 Android SDK Platform 36。同步 Gradle 後，選擇手機或模擬器執行 `app`。

1. 開啟 OpenStandBy，點 **開啟通知存取設定**。允許 OpenStandBy 存取通知後，回到 App。
2. 先在 Spotify、YouTube Music 或其他支援 Android 媒體工作階段的 App 播一首歌，再切回 OpenStandBy 看曲目和控制播放。

OpenStandBy 用通知存取權限連接 Android 現有的播放工作階段，無需另外註冊帳號或申請 API Key。如果音樂已經在播，畫面卻一直停在等待 Android 連線，點 **重新連線**。

## 播放器、時鐘與時速表

- **播放器**顯示歌名、歌手和專輯封面，並將封面模糊後用作背景。用大按鈕切歌、播放或暫停。音樂 App 支援跳轉時可拖曳進度條，Spotify 提供對應控制時也能切換隨機和重複播放。
- **歌詞模式**用麥克風開啟，按 × 回到播放器。歌詞會隨播放進度捲動，目前唱到的句子會亮起。音樂 App 支援跳轉時，點有時間軸的歌詞可跳到對應時間。點畫面空白處可叫出進度條和播放控制列。
- **時鐘**依手機的格式顯示時間和日期。
- **GPS 時速表**需要精確位置權限。取得 GPS 定位後，會以 km/h 顯示目前速度。

齒輪會開啟當前畫面的設定。時鐘和時速表可以各自從相簿挑照片，裁切成想顯示的橫向範圍，再分別調整模糊程度。介面支援繁體中文（台灣）和英文。

## 歌詞來源與收錄範圍

播放器先查 LRCLIB，必要時用 LRCMux 補查。優先顯示有時間軸的歌詞，只有純文字時就直接顯示文字。即使用了兩個歌詞庫，部分歌曲或錄音版本還是可能找不到對應歌詞。

- **重試**會重新查詢。手邊有歌詞檔時，可在 **歌詞選項** 或播放器設定匯入 UTF-8 或 UTF-16 編碼的 `.lrc` 或 `.txt` 檔。匯入後，該首歌會優先顯示本機歌詞。
- **簡體轉繁體**在這兩個地方都能切換。這個功能只在裝置上轉換簡體中文字，保留歌詞原本的語言。

如果你知道收錄更廣的歌詞來源，或有更準確的歌曲比對方法，歡迎開 Issue 或送 PR。回報查不到或配錯歌詞時，附上歌名、歌手和專輯或錄音版本，方便重現。 [Issues](https://github.com/seven-317/OpenStandBy/issues) · [Pull requests](https://github.com/seven-317/OpenStandBy/pulls)

## 建置與測試

App 使用 Kotlin、Jetpack Compose 和 Material 3。在專案根目錄執行 Gradle Wrapper，即可建置 Debug APK、跑單元測試與 Lint。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Debug APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。在 Windows 命令提示字元執行時，將 `./gradlew` 換成 `gradlew.bat`。

## 特別感謝

感謝 LRCLIB 團隊和貢獻者維護歌詞資料庫及 API，也感謝 LRCMux 維護者提供備援歌詞服務與相容於 LRCLIB 的 API。 [LRCLIB](https://lrclib.net/docs) · [LRCMux](https://lrcmux.dev/)

## 授權

OpenStandBy 自行開發的程式碼採用 MIT 授權。 [LICENSE](LICENSE)
