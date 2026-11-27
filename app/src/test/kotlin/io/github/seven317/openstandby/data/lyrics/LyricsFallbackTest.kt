package io.github.seven317.openstandby.data.lyrics

import io.github.seven317.openstandby.domain.lyrics.LyricLine
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
