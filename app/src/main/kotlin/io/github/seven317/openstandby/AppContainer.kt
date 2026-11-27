package io.github.seven317.openstandby

import android.content.Context
import io.github.seven317.openstandby.data.lyrics.LocalLyricsStore
import io.github.seven317.openstandby.data.lyrics.LyricsRepository
import io.github.seven317.openstandby.data.lyrics.NeteaseLyricsApi
import io.github.seven317.openstandby.data.media.MediaSessionRepository
import io.github.seven317.openstandby.data.updates.AppUpdates
import io.github.seven317.openstandby.data.updates.GitHubReleasesApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal class AppContainer(context: Context) {
    val mediaSessionRepository = MediaSessionRepository(context)
    val localLyricsStore by lazy { LocalLyricsStore(context) }
    val appUpdates by lazy { AppUpdates(context, createApi<GitHubReleasesApi>("https://api.github.com/")) }
    val lyricsRepository by lazy {
        LyricsRepository(
            createApi("https://lrclib.net/"),
            createApi("https://api.lrcmux.dev/compat/lrclib/"),
            "OpenStandBy/${BuildConfig.VERSION_NAME} (https://github.com/seven-317/OpenStandBy)",
            neteaseApi = BuildConfig.NETEASE_LYRICS_BASE_URL.takeIf(String::isNotEmpty)?.let {
                createApi<NeteaseLyricsApi>(it)
            },
        )
    }

    private inline fun <reified T> createApi(baseUrl: String): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(T::class.java)
}
