package io.github.seven317.openstandby.data.lyrics

import com.github.houbb.opencc4j.util.ZhConverterUtil
import io.github.seven317.openstandby.domain.lyrics.LrcParser
import io.github.seven317.openstandby.domain.lyrics.LyricLine
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import io.github.seven317.openstandby.domain.lyrics.toTraditionalLyricText
import java.io.IOException
import java.lang.Character.UnicodeScript
import java.text.Normalizer
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Response

internal class KugeciLyricsProvider(
    private val api: KugeciLyricsApi,
    private val userAgent: String,
) {
    private var nextRequest: TimeMark? = null
    private var retryAt: TimeMark? = null

    suspend fun lookup(track: LyricsTrack): ResolvedLyrics {
        if (track.title.isBlank() || track.artist.isBlank()) return ResolvedLyrics(Lyrics.NotFound)
        if (retryAt?.hasNotPassedNow() == true) return ResolvedLyrics(Lyrics.RateLimited)

        val content = withTimeoutOrNull(5_000) {
            try {
                fetch(track)
            } catch (_: IOException) {
                Lyrics.Unavailable
            }
        } ?: Lyrics.Unavailable
        return ResolvedLyrics(content, LyricsSource.Kugeci.takeIf { content is Lyrics.Timed })
    }

    private suspend fun fetch(track: LyricsTrack): Lyrics {
        val queries = withContext(Dispatchers.Default) {
            val trackWithoutCredit = track.title.soundtrackTitle()?.let { track.copy(title = it) }
            listOfNotNull(track, trackWithoutCredit).flatMap { candidate ->
                candidate.title.kugeciQueryGroups().map { group -> candidate to group }
            }
        }
        for ((candidate, group) in queries) {
            val live = candidate.episodeLiveTrack()
            val exactIds = mutableSetOf<String>()
            val liveIds = mutableSetOf<String>()
            for (query in group) {
                val results = requestPage { api.search(userAgent, query) } ?: return requestFailure()
                withContext(Dispatchers.Default) {
                    exactIds += results.matchingSongIds(candidate)
                    if (live != null) liveIds += results.matchingSongIds(live)
                }
            }
            if (exactIds.size > 1) return Lyrics.NotFound
            exactIds.singleOrNull()?.let { return fetchSong(candidate, it) }
            if (live != null && liveIds.isNotEmpty()) return fetchEpisodeLyrics(live, liveIds)
        }
        return Lyrics.NotFound
    }

    private suspend fun fetchSong(track: LyricsTrack, id: String, checkEpisodeDuration: Boolean = false): Lyrics {
        val song = requestPage { api.song(userAgent, id) } ?: return requestFailure()
        return withContext(Dispatchers.Default) { song.timedLyrics(track, checkEpisodeDuration) ?: Lyrics.NotFound }
    }

    private suspend fun fetchEpisodeLyrics(track: LyricsTrack, ids: Set<String>): Lyrics {
        if (ids.size > 2) return Lyrics.NotFound
        val matching = mutableListOf<Lyrics.Timed>()
        for (id in ids) {
            when (val lyrics = fetchSong(track, id, checkEpisodeDuration = true)) {
                is Lyrics.Timed -> matching += lyrics
                Lyrics.NotFound -> Unit
                else -> return lyrics
            }
        }
        return withContext(Dispatchers.Default) {
            val first = matching.firstOrNull() ?: return@withContext Lyrics.NotFound
            val second = matching.getOrNull(1)
            if (second != null && !first.agreesWith(second)) return@withContext Lyrics.NotFound
            val durationMs = checkNotNull(track.durationSeconds) * 1_000L
            matching.minBy { lyrics -> abs(lyrics.lines.last { it.text.isNotBlank() }.timestampMs - durationMs) }
        }
    }

    private suspend fun requestPage(request: suspend () -> Response<ResponseBody>): Document? {
        nextRequest?.let { delay(-it.elapsedNow()) }
        val response = try {
            request()
        } finally {
            nextRequest = TimeSource.Monotonic.markNow() + 250.milliseconds
        }
        if (response.isSuccessful) {
            return response.body()?.use { body ->
                withContext(Dispatchers.Default) { Jsoup.parse(body.string()) }
            }
        }
        response.errorBody()?.close()
        if (response.code() == 429 || response.code() == 503) {
            val seconds = response.headers()["Retry-After"]?.toLongOrNull()?.coerceAtLeast(1) ?: 30
            retryAt = TimeSource.Monotonic.markNow() + seconds.seconds
        }
        return null
    }

    private fun requestFailure(): Lyrics =
        if (retryAt?.hasNotPassedNow() == true) Lyrics.RateLimited else Lyrics.Unavailable
}

private val songHref = Regex("""(?:https://www\.kugeci\.com)?/song/([A-Za-z0-9]+)/?""")
private val artistSeparators = Regex("""\s*[,、;/]\s*|\s+&\s+""")
private val artistFormatting = Regex("""[\s._\p{Pd}·•・]+""")
private val episodeSuffix = Regex("""第\s*[0-9一二三四五六七八九十百零〇兩两]+\s*期(?:\s*\(\s*live\s*\))?$""")
private val singerPrefix = Regex("""^\s*[^:：]{1,16}[:：]\s*""")
private val lyricPunctuation = Regex("""[^\p{L}\p{N}]""")
private val numberedPart = Regex("""^(.+?)\s*(?:[-–—(]\s*)?pt\.\s*(\d+)\s*\)?$""", RegexOption.IGNORE_CASE)
private val lrcAlbum = Regex("""^\[al:\s*(.*?)\]$""", RegexOption.IGNORE_CASE)
private const val soundtrackMedia = """(?:[電电][視视][劇剧]|[電电]影|[網网][劇剧]|[動动][畫画])"""
private val soundtrackCredit = Regex(
    """\s*\(\s*${soundtrackMedia}?\s*《[^《》]+》\s*${soundtrackMedia}?\s*(?:片[頭头]曲|片尾曲|主[題题]曲|插曲)\s*\)$""",
)

private fun String.soundtrackTitle(): String? {
    val title = Normalizer.normalize(this, Normalizer.Form.NFKC).trim()
    val credit = soundtrackCredit.find(title) ?: return null
    return title.substring(0, credit.range.first).takeIf(String::isNotBlank)
}

private fun String.kugeciQueryGroups(): List<List<String>> {
    val title = trim()
    val live = title.liveTitleStem()?.let { "$it (Live)" }
    val stem = numberedPart.matchEntire(title)?.groupValues?.get(1)
    val hanPrefix = stem?.takeIf { it.length > 1 && it.all { char -> UnicodeScript.of(char.code) == UnicodeScript.HAN } }
        ?.take(1)
    return listOfNotNull(live, title, stem, hanPrefix).distinct().map { query ->
        listOf(query, ZhConverterUtil.toSimple(query), query.toTraditionalLyricText()).distinct()
    }
}

private fun LyricsTrack.episodeLiveTrack(): LyricsTrack? {
    // Episode releases sometimes omit the Live label from the player title.
    if (title.liveTitleStem() != null || durationSeconds == null) return null
    if (album?.lyricsMetadataKey()?.contains(episodeSuffix) != true) return null
    return copy(title = "$title (Live)")
}

private fun Document.matchingSongIds(track: LyricsTrack): Set<String> =
    select("#tablesort tbody tr").mapNotNull { row ->
        val cells = row.children()
        val song = cells.getOrNull(1)?.selectFirst("a[href]") ?: return@mapNotNull null
        val id = songHref.matchEntire(song.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
        val artists = cells.getOrNull(2)?.select("a[href*=/singer/]")?.map { it.text() }.orEmpty()
        id.takeIf { track.matches(song.text(), artists) }
    }.toSet()

private fun Document.timedLyrics(track: LyricsTrack, checkEpisodeDuration: Boolean): Lyrics.Timed? {
    val title = selectFirst("h1")?.text() ?: return null
    val artists = select(".song-details-container a[href*=/singer/]").map { artist ->
        artist.text().replaceFirst(Regex("""^演唱\s*[:：]\s*"""), "")
    }
    if (!track.matches(title, artists)) return null
    val lrc = selectFirst("#lrc #lyricsContainer")?.wholeText() ?: return null
    val album = lrc.lineSequence().firstNotNullOfOrNull { lrcAlbum.matchEntire(it.trim())?.groupValues?.get(1) }
    if ((!checkEpisodeDuration || album != null) && !track.matchesLiveAlbum(album)) return null
    val lines = LrcParser.parse(lrc)
    val lastLine = lines.lastOrNull { it.text.isNotBlank() } ?: return null
    if (checkEpisodeDuration && abs(lastLine.timestampMs - checkNotNull(track.durationSeconds) * 1_000L) > 6_000) return null
    return Lyrics.Timed(lines)
}

private fun LyricsTrack.matches(candidateTitle: String, candidateArtists: List<String>): Boolean {
    if (title.kugeciTitleKey() != candidateTitle.kugeciTitleKey()) return false
    val names = candidateArtists.map(String::kugeciArtistKey).toSet()
    if (names.isEmpty() || "" in names) return false
    val expected = if (names.size == 1) setOf(artist.kugeciArtistKey())
        else artist.split(artistSeparators).map(String::kugeciArtistKey).toSet()
    if (expected.size != names.size) return false
    return expected.mapNotNull { name -> names.singleOrNull { name.matchesArtist(it) } }.toSet().size == expected.size
}

private fun String.kugeciArtistKey(): String = lyricsMetadataKey().replace(artistFormatting, "")

private fun String.matchesArtist(other: String): Boolean =
    this == other || this in other.bilingualArtistNames() || other in bilingualArtistNames()

private fun String.bilingualArtistNames(): Set<String> {
    val han = filter { UnicodeScript.of(it.code) == UnicodeScript.HAN }
    val latin = filter { it in 'a'..'z' || it in '0'..'9' }
    return if (han.length >= 2 && latin.count { it in 'a'..'z' } >= 3 && (this == han + latin || this == latin + han)) {
        setOf(han, latin)
    } else {
        emptySet()
    }
}

private fun Lyrics.Timed.agreesWith(other: Lyrics.Timed): Boolean {
    val first = lines.timedLinesByText()
    val second = other.lines.timedLinesByText()
    val aligned = first.count { (text, times) ->
        second[text]?.any { otherTime -> times.any { abs(it.timestampMs - otherTime.timestampMs) <= 1_500 } } == true
    }
    return aligned >= 5 && aligned * 4 >= minOf(first.size, second.size) * 3
}

private fun List<LyricLine>.timedLinesByText(): Map<String, List<LyricLine>> =
    groupBy { it.text.replaceFirst(singerPrefix, "").lyricsMetadataKey().replace(lyricPunctuation, "") }
        .filterKeys { it.length >= 4 }

private fun String.kugeciTitleKey(): String {
    val key = lyricsTitleKey()
    val part = numberedPart.matchEntire(key) ?: return key
    val (stem, number) = part.destructured
    val name = if (stem.all { it.isWhitespace() || UnicodeScript.of(it.code) == UnicodeScript.HAN }) {
        stem.filterNot(Char::isWhitespace)
    } else {
        stem
    }
    return "$name pt.$number"
}
