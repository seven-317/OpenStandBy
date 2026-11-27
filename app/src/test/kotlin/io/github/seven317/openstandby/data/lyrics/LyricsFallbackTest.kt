package io.github.seven317.openstandby.data.lyrics

import io.github.seven317.openstandby.domain.lyrics.LyricLine
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFallbackTest {
    @Test
    fun selectsNearbySameTrackAndSkipsDifferentArtistOrRecording() {
        val track = LyricsTrack("Song", "Artist", "Album", 203)
        val records = listOf(
            record(artist = "Someone else", duration = 203.0, synced = "[00:02]Wrong artist"),
            record(duration = 240.0, synced = "[00:03]Live version"),
            record(duration = 201.0, synced = null),
            record(duration = 200.08, synced = "[00:10.14]Studio version"),
        )
        assertEquals(Lyrics.Timed(listOf(LyricLine(10_140, "Studio version"))), findTimedVersion(track, records))
        assertNull(findTimedVersion(track, records.take(3)))
    }

    @Test
    fun rejectsDifferentTitleArtistOrDurationEvenForAnExactLookupResponse() {
        val track = LyricsTrack("梗 概 (pt.2)", "加木", null, 160)
        val response = record(duration = 160.0, synced = "[00:02]測試歌詞").copy(title = track.title, artist = track.artist)

        assertTrue(response.matchesTrack(track))
        assertFalse(response.copy(title = "梗 概").matchesTrack(track))
        assertFalse(response.copy(artist = "其他歌手").matchesTrack(track))
        assertFalse(response.copy(duration = 166.0).matchesTrack(track))
        assertFalse(response.copy(duration = null).matchesTrack(track))
        assertTrue(response.copy(duration = null).matchesTrack(track.copy(durationSeconds = null)))
    }

    @Test
    fun keepsLyricsThatAgreeWithKugeciAndOverridesDifferentContentEvenWithMatchingMetadata() {
        val reference = ResolvedLyrics(
            timed("雨停以後", "再看一次天空", "獨自回到家", "坐在窗戶旁邊", "等到太陽升起"),
            LyricsSource.Kugeci,
        )
        val sameSong = timed("雨停以后，", "再看一次天空。", "獨自回到家", "坐在窗戶旁邊", "等到太陽升起")
        val differentSong = timed("風把門吹開", "我帶著行李", "沿著公路往前走", "街上行人來去", "始終找不到出口")

        assertNull(kugeciOverride(sameSong, reference))
        assertEquals(reference, kugeciOverride(differentSong, reference))
        assertNull(kugeciOverride(differentSong, ResolvedLyrics(Lyrics.NotFound)))
    }

    @Test
    fun prefersBackupSyncButKeepsPrimaryTextWhenBackupIsUnavailable() {
        val plain = ResolvedLyrics(Lyrics.Plain("Lyrics"), LyricsSource.Lrclib)
        val timed = ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(1_000, "Lyrics"))), LyricsSource.LrcMux)
        assertEquals(timed, preferLyricResult(plain, timed))
        assertEquals(plain, preferLyricResult(plain, ResolvedLyrics(Lyrics.Unavailable)))
        assertEquals(
            ResolvedLyrics(Lyrics.RateLimited),
            preferLyricResult(ResolvedLyrics(Lyrics.NotFound), ResolvedLyrics(Lyrics.RateLimited)),
        )
    }

    @Test
    fun queriesKugeciForPlainOrMissingLyricsAndKeepsEarlierTimedOrInstrumentalResults() = runBlocking {
        val plain = ResolvedLyrics(Lyrics.Plain("Lyrics"), LyricsSource.Lrclib)
        val timed = ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(1_000, "Lyrics"))), LyricsSource.LrcMux)
        val instrumental = ResolvedLyrics(Lyrics.Instrumental, LyricsSource.Lrclib)
        val kugeci = timed.copy(source = LyricsSource.Kugeci)
        val notFound = ResolvedLyrics(Lyrics.NotFound)

        assertEquals(kugeci, plain.withFallback { kugeci })
        assertEquals(kugeci, notFound.withFallback { kugeci })
        assertEquals(plain, plain.withFallback { notFound })
        assertEquals(timed, timed.withFallback { error("Timed lyrics should stop the lookup") })
        assertEquals(instrumental, instrumental.withFallback { error("Instrumental tracks should stop the lookup") })
    }

    @Test
    fun importedLyricsKeepTheirTimingOrPlainText() {
        assertEquals(Lyrics.Timed(listOf(LyricLine(1520, "測試歌詞"))), parseLocalLyrics("[00:01.52]測試歌詞"))
        assertEquals(Lyrics.Timed(listOf(LyricLine(1_520, "測試歌詞"))), parseLocalLyrics("[1520,400](1520,400,0)測試歌詞"))
        assertEquals(Lyrics.Plain("測試歌詞"), parseLocalLyrics("測試歌詞"))
        assertNull(parseLocalLyrics("not\u0000text"))
    }

    @Test
    fun localKeySeparatesVersionsAndSessionSource() {
        val key = localLyricsKey("music.app", "id", "Song", "Artist", "Album")
        assertEquals(key, localLyricsKey("music.app", "id", "Song", "Artist", null))
        assertNotEquals(key, localLyricsKey("music.app", "id2", "Song", "Artist", "Album"))
        assertNotEquals(key, localLyricsKey("other.app", "id", "Song", "Artist", "Album"))
        assertNull(localLyricsKey("music.app", null, null, null, null))
    }

    private fun record(artist: String = "Artist", duration: Double, synced: String?) =
        LrclibTrack(synced, "Lyrics", "Song", artist, "Album", duration, instrumental = false)

    private fun timed(vararg lines: String) =
        Lyrics.Timed(lines.mapIndexed { index, line -> LyricLine(index * 1_000L, line) })
}
