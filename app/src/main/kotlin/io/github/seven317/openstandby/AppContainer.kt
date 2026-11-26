package io.github.seven317.openstandby

import android.content.Context
import io.github.seven317.openstandby.data.lyrics.LocalLyricsStore
import io.github.seven317.openstandby.data.lyrics.LrclibApi
import io.github.seven317.openstandby.data.lyrics.LyricsRepository
import io.github.seven317.openstandby.data.media.MediaSessionRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal class AppContainer(context: Context) {
    val mediaSessionRepository = MediaSessionRepository(context)
    val localLyricsStore by lazy { LocalLyricsStore(context) }
    val lyricsRepository by lazy {
        LyricsRepository(
            createLyricsApi("https://lrclib.net/"),
            createLyricsApi("https://api.lrcmux.dev/compat/lrclib/"),
            "OpenStandBy/${BuildConfig.VERSION_NAME} (https://github.com/seven-317/OpenStandBy)",
        )
    }

    private fun createLyricsApi(baseUrl: String): LrclibApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LrclibApi::class.java)
}
