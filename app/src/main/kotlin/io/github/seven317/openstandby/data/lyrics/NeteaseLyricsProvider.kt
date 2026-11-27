package io.github.seven317.openstandby.data.lyrics

import com.google.gson.JsonParseException
import io.github.seven317.openstandby.domain.lyrics.LrcParser
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import io.github.seven317.openstandby.domain.lyrics.YrcParser
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response

internal class NeteaseLyricsProvider(private val api: NeteaseLyricsApi) {
    suspend fun lookup(track: LyricsTrack): ResolvedLyrics {
        val content = withTimeoutOrNull(5_000) {
            try {
                fetch(track)
            } catch (_: IOException) {
                Lyrics.Unavailable
            } catch (_: JsonParseException) {
                Lyrics.Unavailable
            }
        } ?: Lyrics.Unavailable
        val source = LyricsSource.Netease.takeIf { content is Lyrics.Timed || content is Lyrics.Plain }
        return ResolvedLyrics(content, source)
    }

    private suspend fun fetch(track: LyricsTrack): Lyrics {
        val response = api.searchSongs("${track.title} ${track.artist}")
        if (!response.isSuccessful) return response.errorResult()
        val search = response.body() ?: return Lyrics.Unavailable
        if (search.code != 200) return failure(search.code)
        val songId = withContext(Dispatchers.Default) {
            findNeteaseSongId(track, search.result?.songs.orEmpty().filterNotNull())
        } ?: return Lyrics.NotFound

        val detailed = requestLyrics { api.detailedLyrics(songId) }
        if (detailed is Lyrics.Timed || detailed == Lyrics.RateLimited) return detailed
        val standard = withTimeoutOrNull(1_500) { requestLyrics { api.lyrics(songId) } } ?: Lyrics.Unavailable
        return preferLyricResult(ResolvedLyrics(detailed), ResolvedLyrics(standard)).content
    }

    private suspend fun requestLyrics(request: suspend () -> Response<NeteaseLyricResponse>): Lyrics {
        val response = try {
            request()
        } catch (_: IOException) {
            return Lyrics.Unavailable
        } catch (_: JsonParseException) {
            return Lyrics.Unavailable
        }
        if (!response.isSuccessful) return response.errorResult()
        val payload = response.body() ?: return Lyrics.Unavailable
        if (payload.code != 200) return failure(payload.code)
        return withContext(Dispatchers.Default) { payload.toLyrics() }
    }

    private fun Response<*>.errorResult(): Lyrics {
        errorBody()?.close()
        return failure(code())
    }

    private fun failure(code: Int): Lyrics = when (code) {
        404 -> Lyrics.NotFound
        429, 503 -> Lyrics.RateLimited
        else -> Lyrics.Unavailable
    }
}

internal fun NeteaseLyricResponse.toLyrics(): Lyrics {
    val standard = lrc?.lyric?.trim().orEmpty()
    val lines = LrcParser.parse(standard)
    if (lines.any { it.text.isNotBlank() }) return Lyrics.Timed(lines)
    val detailed = yrc?.lyric?.let(YrcParser::parse).orEmpty()
    if (detailed.any { it.text.isNotBlank() }) return Lyrics.Timed(detailed)
    return standard.takeIf { lines.isEmpty() && it.isNotEmpty() }?.let { Lyrics.Plain(it) } ?: Lyrics.NotFound
}

internal fun findNeteaseSongId(track: LyricsTrack, songs: List<NeteaseSong>): Long? {
    val title = track.title.lyricsTitleKey()
    val artist = track.artist.lyricsMetadataKey()
    if (title.isEmpty() || artist.isEmpty()) return null
    val album = track.album?.lyricsMetadataKey()?.takeIf(String::isNotEmpty)
    val duration = track.durationSeconds?.toLong()?.times(1_000)
    val artistParts = artist.split(artistSeparator).map(String::trim).filter(String::isNotEmpty)
    val matches = songs.filter { song ->
        if (song.id == null || song.id <= 0 || song.name?.lyricsTitleKey() != title) return@filter false
        if (!track.matchesLiveAlbum(song.album?.name)) return@filter false
        val names = song.artists.orEmpty().mapNotNull { it?.name?.lyricsMetadataKey() }
        if (artist !in names && (artistParts.size < 2 || !names.containsAll(artistParts))) return@filter false
        if (duration == null) return@filter true
        val reported = song.durationMs?.takeIf { it > 0 } ?: return@filter false
        abs(reported - duration) <= 4_000
    }.distinctBy(NeteaseSong::id)

    val albumMatches = if (album == null) emptyList() else matches.filter { it.album?.name?.lyricsMetadataKey() == album }
    val candidates = albumMatches.ifEmpty { matches }
    if (duration == null) return candidates.singleOrNull()?.id
    return candidates.minByOrNull { abs(checkNotNull(it.durationMs) - duration) }?.id
}

private val artistSeparator = Regex("""[,、;]|\s+[/&]\s+""")
