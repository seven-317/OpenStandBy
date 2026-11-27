# OpenStandBy

[English](README.md)

OpenStandBy 是一款免費的 Android 全螢幕橫向待機工具。我做這個 App，是想讓手機放在桌面底座或機車手機架上時，抬眼就能看到正在播哪首歌，也能直接控制播放。

App 開著時會保持螢幕常亮，啟動後直接進入播放器。往上滑是時鐘，往下滑是 GPS 時速表。

## 安裝與初次使用

OpenStandBy 支援 Android 9（API 28）以上。從原始碼執行時，用 Android Studio 開啟這個專案，將 Gradle JDK 設成 17，並安裝 Android SDK Platform 36。同步 Gradle 後，在手機或模擬器上執行 `app`。

> [!IMPORTANT]
> OpenStandBy 需要通知存取權限，才能讀取音樂 App 提供的 Android 媒體工作階段。

1. 開啟 OpenStandBy，點 **開啟通知存取設定**。允許 OpenStandBy 存取通知後，回到 App。
2. 在 Spotify、YouTube Music 或其他支援 Android 媒體工作階段的 App 播一首歌，再切回 OpenStandBy 看曲目和控制播放。

OpenStandBy 無需另外註冊帳號或申請 API Key。如果音樂已在播放，畫面仍停在等候連線，點 **重新連線**。

## 播放器、時鐘與時速表

- **播放器**顯示歌名、歌手和專輯封面，並將封面模糊後用作背景。可直接切歌、播放或暫停。音樂 App 支援跳轉時可拖曳進度條，Spotify 提供對應控制時也能切換隨機和重複播放。
- **時鐘**顯示日期，可選數位、指針、翻頁或七段顯示。
- **GPS 時速表**需要精確位置權限。手機取得 GPS 定位後，會以 km/h 顯示速度。

齒輪會開啟當前畫面的設定。時鐘和時速表可以各自從相簿挑照片，裁切出橫向畫面，再分別調整模糊程度。從任一畫面的設定進入 **全域設定** → **介面語言**，可選擇繁體中文（台灣）、英文或跟隨系統。

## 歌詞

點麥克風開啟歌詞。有時間軸時，畫面會跟著播放進度捲動並高亮目前那一行。音樂 App 支援跳轉時，點歌詞可跳到對應時間。點空白處可叫出進度條和播放控制，按 × 回到播放器。

線上查詢順序為 **酷歌詞 → LRCLIB → 網易雲音樂（若已啟用）→ LRCMux**。酷歌詞會搜尋繁體和簡體歌名。找到第一筆吻合的逐行歌詞就使用，只有純文字時就直接顯示文字。歌名標示為 Live 且播放器有提供專輯名稱時，歌詞來源也必須符合該專輯。LRCLIB 和 LRCMux 的曲長須與播放器提供的曲長相差四秒以內。LRC 同一行有多個時間戳時，會在每個對應的播放時間顯示那句歌詞。

點 **重試** 可重新查詢。**簡體轉繁體**可在 **歌詞選項** 或播放器設定切換。轉換在裝置上執行，保留歌詞原本的語言。

### 匯入歌詞檔

> [!TIP]
> 線上找不到歌詞或配到別的錄音時，可用歌名和歌手搜尋 LRC，挑一份與正在播放的版本相符的檔案。 [酷歌詞](https://www.kugeci.com) · [Lyricsify](https://www.lyricsify.com/)

載入該首歌後，從 **歌詞選項** 或播放器設定點 **匯入歌詞檔**。支援 UTF-8 或 UTF-16 編碼的 `.lrc`、`.yrc` 和 `.txt` 檔。匯入的檔案會優先用於這首歌。要恢復線上查詢，可在 **歌詞選項** 點 **移除本機歌詞**。

### 啟用網易雲備援

> [!NOTE]
> 一般建置使用酷歌詞、LRCLIB 和 LRCMux。建置時提供 API Enhanced 的服務網址，即可啟用網易雲備援。

架設社群維護的 API Enhanced 服務，建置 App 時將範例網址換成該服務的 HTTPS 基底網址。 [NetEase Cloud Music API Enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)

```sh
./gradlew :app:assembleDebug -PneteaseLyricsBaseUrl=https://lyrics.example.com/
```

App 會將歌名和歌手送到該服務搜尋，再用配對到的歌曲 ID 查歌詞。支援 LRC，收到 YRC 時會取出每行的開始時間。

## 建置與測試

App 使用 Kotlin、Jetpack Compose 和 Material 3。在專案根目錄執行 Gradle Wrapper，即可建置 Debug APK、跑單元測試與 Lint。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Debug APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。在 Windows 命令提示字元執行時，將 `./gradlew` 換成 `gradlew.bat`。

## 參與貢獻

回報查不到或配錯歌詞時，請在 Issue 裡附上歌名、歌手和專輯或錄音版本。也歡迎送 PR 補充歌詞來源或改善歌曲比對。 [Issues](https://github.com/seven-317/OpenStandBy/issues) · [Pull requests](https://github.com/seven-317/OpenStandBy/pulls)

## 特別感謝

- 感謝 LRCLIB 的維護者與貢獻者提供歌詞資料庫和公開 API。 [LRCLIB](https://lrclib.net/docs)
- 感謝 LRCMux 的維護者彙整歌詞來源並提供相容於 LRCLIB 的 API。 [LRCMux](https://lrcmux.dev/)
- 感謝獨立開源專案 NetEase Cloud Music API Enhanced 的開發者、維護者與貢獻者，提供網易雲備援使用的歌曲搜尋和歌詞查詢介面。 [NetEase Cloud Music API Enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)

## 授權

OpenStandBy 自行開發的程式碼採用 MIT 授權。 [LICENSE](LICENSE)
