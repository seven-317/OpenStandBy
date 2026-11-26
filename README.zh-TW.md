# OpenStandBy

[English](README.md)

OpenStandBy 是一款免費的 Android 全螢幕橫向待機工具，適合放在桌面底座或機車手機架上使用。開啟後會直接進入音樂播放器並保持螢幕常亮，從播放器滑一下就能切到時鐘或 GPS 時速表。

## 主要功能

- **音樂播放器**顯示歌名、歌手與專輯封面，並把封面做成模糊背景。大按鈕可切換上一首、播放或暫停、下一首。進度拖曳、隨機播放與重複播放取決於音樂 App 提供的控制功能。
- **歌詞模式**可透過麥克風按鈕開啟。有時間軸的歌詞會自動捲動並高亮目前唱到的句子。音樂 App 支援跳轉時，點歌詞就能跳到對應時間。點畫面空白處可叫出進度條與播放控制列。
- **時鐘**顯示時間與日期。從播放器往上滑即可進入。
- **GPS 時速表**以 km/h 顯示目前速度。從播放器往下滑即可進入。

每個畫面都有自己的設定。時鐘和 GPS 時速表可以各自從相簿選照片、裁切成適合橫向畫面的範圍，再分別調整模糊程度。介面提供繁體中文（台灣）與英文。

## 初次使用

1. 開啟 OpenStandBy，點選 **開啟通知存取設定**。在 Android 設定允許 OpenStandBy 存取通知後，回到 App。
2. 用 Spotify、YouTube Music 或其他支援 Android 媒體工作階段的 App 播放歌曲。OpenStandBy 會透過 Android 讀取播放狀態並控制音樂，無需另外註冊帳號或申請 API Key。
3. 要使用 GPS 時速表時，切到該畫面並依提示允許精確位置權限。

如果音樂正在播放，畫面卻一直停在等待 Android 連線，可以點 **重新連線**。

## 歌詞來源與收錄範圍

OpenStandBy 會先從 LRCLIB 查詢，必要時再用 LRCMux 補查。如果只找到沒有時間軸的歌詞，會以純文字顯示。**即使使用兩個歌詞庫，部分歌曲還是可能找不到對應歌詞。**

可以點 **重試** 再查一次，也可以從 **歌詞選項** 或播放器設定匯入本機的 `.lrc` 或 `.txt` 檔。匯入的歌詞會儲存在該首歌，並優先顯示。**簡體轉繁體**會在裝置上轉換中文歌詞的字形，保留原本語言。

如果你知道更好的歌詞來源，或有能提高搜尋命中率的做法，歡迎開 Issue 或送 Pull Request。回報找不到歌詞時，附上歌名、歌手和歌曲版本會有助於重現。 [Issues](https://github.com/seven-317/OpenStandBy/issues) · [Pull requests](https://github.com/seven-317/OpenStandBy/pulls)

## 從原始碼建置

App 使用 Kotlin、Jetpack Compose 和 Material 3，支援 Android 9（API 28）以上。用 Android Studio 開啟專案根目錄並同步 Gradle，建置環境需要 JDK 17 和 Android SDK Platform 36。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Debug APK 會輸出到 `app/build/outputs/apk/debug/app-debug.apk`。也可以在 Android Studio 執行 `app`，安裝到連線的手機或模擬器。Windows 上請將指令中的 `./gradlew` 改為 `gradlew.bat`。

## 特別感謝

- 感謝 LRCLIB 團隊與貢獻者提供並維護主要的歌詞資料庫與 API。 [LRCLIB](https://lrclib.net/docs)
- 感謝 LRCMux 維護者提供備援歌詞服務，以及相容於 LRCLIB 的 API。 [LRCMux](https://lrcmux.dev/)
