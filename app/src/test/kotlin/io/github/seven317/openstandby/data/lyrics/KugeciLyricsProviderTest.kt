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
    fun searchesBothScriptsAndFetchesTheSameSongOnlyOnce() = runBlocking {
        val results = searchPage(row("studio", "測試歌曲", "陳小明"))
        val api = FakeApi(
            results = mapOf("测试歌曲" to results, "測試歌曲" to results),
            detail = songPage("測試歌曲", "陳小明", lrc = "[00:01]測試歌詞"),
        )

        assertEquals(LyricsSource.Kugeci, KugeciLyricsProvider(api, "OpenStandBy-test").lookup(track.copy(title = "测试歌曲")).source)
        assertEquals(listOf("search:测试歌曲", "search:測試歌曲", "song:studio"), api.calls)
    }

    @Test
    fun matchesTheEpisodePerformanceOnlyWhenTheArtistsAndTimelinesAgree() = runBlocking {
        val performance = LyricsTrack(
            "飛躍經濟艙", "GAI周延, 功夫胖KUNGFU-PEN, 布瑞吉", "中國說唱巔峰對決 第九期", 213,
        )
        val title = "飞跃经济舱"
        val results = mapOf(title to searchPage(
            row("first", "$title (Live)", "GAI周延", "Kungfu-Pen", "布瑞吉Bridge"),
            row("second", "$title (LIVE版)", "GAI周延", "功夫胖KungFuPen", "布瑞吉Bridge"),
            row("cover", "$title (Live)", "GAI周延", "Kungfu-Pen", "其他歌手"),
        ))
        val text = listOf("今天天氣很好", "大家一起出門", "沿著大路往前", "終於走到門口", "看見一片藍天")
        fun performancePage(startSecond: Int, name: String, songTitle: String): String {
            val lrc = text.mapIndexed { index, line -> "[03:${startSecond + index * 3}.00]$line" }.joinToString("<br>")
            return songPage(songTitle, "GAI周延", name, "布瑞吉Bridge", lrc = lrc)
        }
        val first = performancePage(18, "Kungfu-Pen", "$title (Live)")
        val second = performancePage(19, "功夫胖KungFuPen", "$title (LIVE版)")
        val api = FakeApi(results, details = mapOf("first" to first, "second" to second))
        val result = KugeciLyricsProvider(api, "OpenStandBy-test").lookup(performance)
        assertEquals(LyricsSource.Kugeci, result.source)
        assertEquals(211_000L, (result.content as Lyrics.Timed).lines.last().timestampMs)
        assertEquals(listOf("search:飛躍經濟艙", "search:飞跃经济舱", "song:first", "song:second"), api.calls)

        val differentTiming = performancePage(22, "功夫胖KungFuPen", "$title (LIVE版)")
        val conflict = FakeApi(results, details = mapOf("first" to first, "second" to differentTiming))
        assertEquals(ResolvedLyrics(Lyrics.NotFound), KugeciLyricsProvider(conflict, "OpenStandBy-test").lookup(performance))

        val studio = FakeApi(results)
        assertEquals(ResolvedLyrics(Lyrics.NotFound), KugeciLyricsProvider(studio, "OpenStandBy-test").lookup(performance.copy(album = "錄音室專輯")))
    }

    @Test
    fun retriesWithoutTheSoundtrackCreditAndStillRequiresTheArtist() = runBlocking {
        val title = "一笑倾城 (《微微一笑很倾城》电视剧片头曲)"
        val api = FakeApi(
            results = mapOf("一笑倾城" to searchPage(
                row("cover", "一笑倾城", "其他歌手"),
                row("live", "一笑倾城 (Live)", "汪苏泷"),
                row("studio", "一笑倾城", "汪苏泷"),
            )),
            detail = songPage("一笑倾城", "汪苏泷", lrc = "[00:33.80]測試歌詞"),
        )
        val track = LyricsTrack(title, "汪蘇瀧", title, 231)

        assertEquals(
            ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(33_800, "測試歌詞"))), LyricsSource.Kugeci),
            KugeciLyricsProvider(api, "OpenStandBy-test").lookup(track),
        )
        assertEquals(
            listOf("search:$title", "search:一笑傾城 (《微微一笑很傾城》電視劇片頭曲)", "search:一笑倾城",
                "search:一笑傾城", "song:studio"),
            api.calls,
        )
    }

    @Test
    fun preservesLiveVersionWhenRemovingATrailingSoundtrackCredit() = runBlocking {
        val title = "測試歌曲 - Live（電影《測試》主題曲）"
        val api = FakeApi(
            results = mapOf("测试歌曲 (Live)" to searchPage(row("studio", "测试歌曲", "陈小明"))),
        )

        assertEquals(ResolvedLyrics(Lyrics.NotFound), KugeciLyricsProvider(api, "OpenStandBy-test").lookup(track.copy(title = title)))
        assertEquals(
            listOf("search:$title", "search:测试歌曲 - Live（电影《测试》主题曲）", "search:測試歌曲 (Live)", "search:测试歌曲 (Live)",
                "search:測試歌曲 - Live", "search:测试歌曲 - Live"),
            api.calls,
        )
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
        assertEquals(listOf("search:測試歌曲", "search:测试歌曲"), api.calls)
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
            assertEquals(listOf("search:測試歌曲", "search:测试歌曲", "song:studio"), api.calls)
        }
    }

    @Test
    fun requiresAlbumEvidenceForLiveLyricsWhenThePlayerReportsAnAlbum() = runBlocking {
        val live = LyricsTrack("男孩 - Live", "梁博", "尖叫之夜", 329)
        val results = mapOf("男孩 (Live)" to searchPage(row("concert", "男孩（Live）", "梁博")))
        val (missingAlbum, otherAlbum, sameAlbum) = listOf("", "[al:歌手2017]<br>", "[al:尖叫之夜]<br>").map { album ->
            val api = FakeApi(results, songPage("男孩(Live)", "梁博", lrc = "${album}[00:30.43]測試歌詞"))
            val result = KugeciLyricsProvider(api, "OpenStandBy-test").lookup(live)
            assertEquals(listOf("search:男孩 (Live)", "song:concert"), api.calls)
            result
        }

        assertEquals(ResolvedLyrics(Lyrics.NotFound), missingAlbum)
        assertEquals(ResolvedLyrics(Lyrics.NotFound), otherAlbum)
        assertEquals(ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(30_430, "測試歌詞"))), LyricsSource.Kugeci), sameAlbum)
    }

    private class FakeApi(
        private val results: Map<String, String>,
        private val detail: String? = null,
        private val details: Map<String, String> = emptyMap(),
    ) : KugeciLyricsApi {
        val calls = mutableListOf<String>()

        override suspend fun search(userAgent: String, title: String): Response<ResponseBody> {
            calls += "search:$title"
            return Response.success(results.getOrDefault(title, "<p>No results</p>").toResponseBody())
        }

        override suspend fun song(userAgent: String, id: String): Response<ResponseBody> {
            calls += "song:$id"
            return Response.success(checkNotNull(details[id] ?: detail).toResponseBody())
        }
    }

    private fun searchPage(vararg rows: String) =
        """<table id="tablesort"><tbody>${rows.joinToString("")}</tbody></table>"""

    private fun row(id: String, title: String, vararg artists: String) = """
        <tr><td>2026-01-01</td><td><a href="https://www.kugeci.com/song/$id">$title</a></td>
        <td>${artists.joinToString("&nbsp;") { """<a href="/singer/test">$it</a>""" }}</td></tr>
    """.trimIndent()

    private fun songPage(title: String, vararg artists: String, lrc: String) = """
        <h1>$title</h1>
        <div class="song-details-container">${artists.joinToString("") { """<a href="/singer/test">演唱： $it</a>""" }}</div>
        <div id="lrc"><div id="lyricsContainer">$lrc</div></div>
    """.trimIndent()
}
