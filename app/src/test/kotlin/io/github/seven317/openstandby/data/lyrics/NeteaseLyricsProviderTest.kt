package io.github.seven317.openstandby.data.lyrics

import io.github.seven317.openstandby.domain.lyrics.LyricLine
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class NeteaseLyricsProviderTest {
    private val track = LyricsTrack("測試歌", "陳小明", "測試專輯", 205)

    @Test
    fun matchesTraditionalMetadataAndAvoidsCoversAndDifferentRecordings() {
        val songs = listOf(
            song(id = 1L, artist = "其他歌手"),
            song(id = 2L, duration = 232_000),
            song(id = 3L, album = "精选集"),
            song(id = 4_000_000_001L, duration = 207_100),
        )

        assertEquals(4_000_000_001L, findNeteaseSongId(track, songs))
        assertNull(findNeteaseSongId(track.copy(album = null, durationSeconds = null), songs.drop(1)))
        assertNull(findNeteaseSongId(track, songs.take(2)))
    }

    @Test
    fun usesTheSearchResultIdAndKeepsEachRepeatFromYrc() = runBlocking {
        val api = FakeApi(
            detailed = Response.success(NeteaseLyricResponse(200, section("""
                {"t":0,"c":[{"tx":"Credits"}]}
                [42000,1000](42000,500,0)再唱(42500,500,0)一次
                [8000,1000](8000,500,0)再唱(8500,500,0)一次
            """.trimIndent()), section("沒有時間軸"))),
        )

        assertEquals(
            ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(8_000, "再唱一次"), LyricLine(42_000, "再唱一次"))), LyricsSource.Netease),
            NeteaseLyricsProvider(api).lookup(track),
        )
        assertEquals(listOf("search:測試歌 陳小明", "detailed:4000000001"), api.calls)
    }

    @Test
    fun fallsBackToStandardLrcAndThenToPlainText() = runBlocking {
        val api = FakeApi(
            detailed = Response.error(403, "".toResponseBody()),
            standard = Response.success(NeteaseLyricResponse(200, null, section("[00:09][00:35]測試歌詞"))),
        )

        assertEquals(
            ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(9_000, "測試歌詞"), LyricLine(35_000, "測試歌詞"))), LyricsSource.Netease),
            NeteaseLyricsProvider(api).lookup(track),
        )
        assertEquals(listOf("search:測試歌 陳小明", "detailed:4000000001", "standard:4000000001"), api.calls)
        assertEquals(Lyrics.Plain("純文字"), NeteaseLyricResponse(200, null, section("純文字")).toLyrics())
        assertEquals(Lyrics.NotFound, NeteaseLyricResponse(200, null, section("\n")).toLyrics())
    }

    private class FakeApi(
        private val detailed: Response<NeteaseLyricResponse>,
        private val standard: Response<NeteaseLyricResponse> = Response.error(404, "".toResponseBody()),
    ) : NeteaseLyricsApi {
        val calls = mutableListOf<String>()

        override suspend fun searchSongs(keywords: String): Response<NeteaseSearchResponse> {
            calls.add("search:$keywords")
            return Response.success(NeteaseSearchResponse(200, NeteaseSearchResult(listOf(song(id = 4_000_000_001L)))))
        }

        override suspend fun detailedLyrics(songId: Long): Response<NeteaseLyricResponse> {
            calls.add("detailed:$songId")
            return detailed
        }

        override suspend fun lyrics(songId: Long): Response<NeteaseLyricResponse> {
            calls.add("standard:$songId")
            return standard
        }
    }

    private fun section(lyrics: String) = NeteaseLyricText(lyrics)

    companion object {
        private fun song(id: Long, artist: String = "陈小明", album: String = "测试专辑", duration: Long = 205_000) =
            NeteaseSong(id, "测试歌", listOf(NeteaseName(artist)), NeteaseName(album), duration)
    }
}
