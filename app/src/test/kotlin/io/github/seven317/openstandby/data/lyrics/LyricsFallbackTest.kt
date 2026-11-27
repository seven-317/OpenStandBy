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
    fun matchesLiveTitleFormatsAndKeepsEachAlbumOnItsOwnTimeline() {
        val concert = LyricsTrack("男孩（Live）", "梁博", "尖叫之夜", 329)
        val tv = LyricsTrack("男孩 - Live", "梁博", "歌手2017 (第十期 Live)", 311)
        val misleading = record(artist = "梁博", duration = 329.0, synced = "[00:16.48]測試歌詞")
            .copy(title = "男孩(Live)", album = "尖叫之夜 - Single")
        val correctConcert = misleading.copy(title = "男孩 (Live)", album = concert.album, duration = 330.0, synced = "[00:30.43]測試歌詞")
        val correctTv = misleading.copy(title = "男孩 (Live)", album = tv.album, duration = 312.0, synced = "[00:16.80]測試歌詞")
        val records = listOf(misleading, correctTv, correctConcert)

        assertFalse(misleading.matchesTrack(concert))
        assertFalse(correctConcert.copy(album = null).matchesTrack(concert))
        assertFalse(correctConcert.copy(title = "男孩").matchesTrack(concert))
        assertEquals(Lyrics.Timed(listOf(LyricLine(30_430, "測試歌詞"))), findTimedVersion(concert, records))
        assertEquals(Lyrics.Timed(listOf(LyricLine(16_800, "測試歌詞"))), findTimedVersion(tv, records))
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
    fun usesKugeciTimedLyricsImmediatelyAndFallsBackWhenMissing() = runBlocking {
        val plain = ResolvedLyrics(Lyrics.Plain("Lyrics"), LyricsSource.Lrclib)
        val timed = ResolvedLyrics(Lyrics.Timed(listOf(LyricLine(1_000, "Lyrics"))), LyricsSource.LrcMux)
        val instrumental = ResolvedLyrics(Lyrics.Instrumental, LyricsSource.Lrclib)
        val kugeci = timed.copy(source = LyricsSource.Kugeci)
        val notFound = ResolvedLyrics(Lyrics.NotFound)

        assertEquals(kugeci, kugeci.withFallback { error("Kugeci timed lyrics should stop the lookup") })
        assertEquals(timed, notFound.withFallback { timed })
        assertEquals(timed, plain.withFallback { timed })
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
}
