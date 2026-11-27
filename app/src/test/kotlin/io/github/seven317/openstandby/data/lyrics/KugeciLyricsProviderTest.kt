package io.github.seven317.openstandby.data.lyrics

import io.github.seven317.openstandby.domain.lyrics.LyricLine
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class KugeciLyricsProviderTest {
    private val track = LyricsTrack("測試歌曲", "陳小明", null, 210)

    @Test
    fun retriesWithSimplifiedTitleAndParsesOnlyTheMatchingSongsTimedLyrics() = runBlocking {
        val api = FakeApi(
            results = mapOf("测试歌曲" to searchPage(
                row("cover", "测试歌曲", "其他歌手"),
                row("live", "测试歌曲 (Live)", "陈小明"),
                row("duet", "测试歌曲", "陈小明", "其他歌手"),
                row("studio", "测试歌曲", "陈小明"),
            )),
            detail = songPage("测试歌曲", "陈小明", lrc = "[00:01.52][00:04.00]測試 &amp; 歌詞<br>[00:06.125]下一行"),
        )

        assertEquals(
            ResolvedLyrics(
                Lyrics.Timed(listOf(LyricLine(1_520, "測試 & 歌詞"), LyricLine(4_000, "測試 & 歌詞"), LyricLine(6_125, "下一行"))),
                LyricsSource.Kugeci,
            ),
            KugeciLyricsProvider(api, "OpenStandBy-test").lookup(track),
        )
        assertEquals(listOf("search:測試歌曲", "search:测试歌曲", "song:studio"), api.calls)
    }

    @Test
    fun skipsMultipleMatchingRecordings() = runBlocking {
        val api = FakeApi(
            results = mapOf("測試歌曲" to searchPage(
                row("first", "測試歌曲", "陳小明"),
                row("second", "測試歌曲", "陳小明"),
            )),
        )

        assertEquals(ResolvedLyrics(Lyrics.NotFound), KugeciLyricsProvider(api, "OpenStandBy-test").lookup(track))
        assertEquals(listOf("search:測試歌曲"), api.calls)
    }

    @Test
    fun searchesByStemButOnlyAppliesTheRequestedPart() = runBlocking {
        val results = searchPage(
            row("original", "梗 概", "加木"),
            row("sequel", "梗 概pt.2", "加木"),
            row("otherpart", "梗 概pt.3", "加木"),
            row("cover", "梗 概pt.2", "其他歌手"),
        )
        val detail = songPage("梗 概pt.2", "加木", lrc = "[00:01.52]測試歌詞")
        for ((title, matchingQuery) in mapOf("梗 概 - pt.2" to "梗 概", "梗概-pt.2" to "梗")) {
            val api = FakeApi(mapOf(matchingQuery to results), detail)
            assertEquals(
                ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(1_520, "測試歌詞"))), LyricsSource.Kugeci),
                KugeciLyricsProvider(api, "OpenStandBy-test").lookup(LyricsTrack(title, "加木", null, 160)),
            )
            assertEquals("song:sequel", api.calls.last())
            assertEquals(2, api.calls.count { it == "search:$title" || it == "search:$matchingQuery" })
        }
    }

    @Test
    fun skipsMismatchedSongPagesAndLyricsWithoutTimestamps() = runBlocking {
        val results = mapOf("測試歌曲" to searchPage(row("studio", "測試歌曲", "陳小明")))
        for (detail in listOf(
            songPage("其他歌曲", "陳小明", lrc = "[00:03]測試歌詞"),
            songPage("測試歌曲", "其他歌手", lrc = "[00:03]測試歌詞"),
            songPage("測試歌曲", "陳小明", lrc = "測試歌詞"),
        )) {
            val api = FakeApi(results, detail)
            assertEquals(ResolvedLyrics(Lyrics.NotFound), KugeciLyricsProvider(api, "OpenStandBy-test").lookup(track))
            assertEquals(listOf("search:測試歌曲", "song:studio"), api.calls)
        }
    }

    private class FakeApi(
        private val results: Map<String, String>,
        private val detail: String? = null,
    ) : KugeciLyricsApi {
        val calls = mutableListOf<String>()

        override suspend fun search(userAgent: String, title: String): Response<ResponseBody> {
            calls += "search:$title"
            return Response.success(results.getOrDefault(title, "<p>No results</p>").toResponseBody())
        }

        override suspend fun song(userAgent: String, id: String): Response<ResponseBody> {
            calls += "song:$id"
            return Response.success(checkNotNull(detail).toResponseBody())
        }
    }

    private fun searchPage(vararg rows: String) =
        """<table id="tablesort"><tbody>${rows.joinToString("")}</tbody></table>"""

    private fun row(id: String, title: String, vararg artists: String) = """
        <tr><td>2026-01-01</td><td><a href="https://www.kugeci.com/song/$id">$title</a></td>
        <td>${artists.joinToString("&nbsp;") { """<a href="/singer/test">$it</a>""" }}</td></tr>
    """.trimIndent()

    private fun songPage(title: String, artist: String, lrc: String) = """
        <h1>$title</h1>
        <div class="song-details-container"><a href="/singer/test">演唱： $artist</a></div>
        <div id="lrc"><div id="lyricsContainer">$lrc</div></div>
    """.trimIndent()
}
