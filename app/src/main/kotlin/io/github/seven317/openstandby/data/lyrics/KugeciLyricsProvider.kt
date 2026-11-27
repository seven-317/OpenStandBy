package io.github.seven317.openstandby.data.lyrics

import com.github.houbb.opencc4j.util.ZhConverterUtil
import io.github.seven317.openstandby.domain.lyrics.LrcParser
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import io.github.seven317.openstandby.domain.lyrics.toTraditionalLyricText
import java.io.IOException
import java.lang.Character.UnicodeScript
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
        val queries = withContext(Dispatchers.Default) { track.title.kugeciQueries() }
        for (query in queries) {
            val results = requestPage { api.search(userAgent, query) } ?: return requestFailure()
            val ids = withContext(Dispatchers.Default) { results.matchingSongIds(track) }
            if (ids.isEmpty()) continue
            val id = ids.singleOrNull() ?: return Lyrics.NotFound
            val song = requestPage { api.song(userAgent, id) } ?: return requestFailure()
            return withContext(Dispatchers.Default) { song.timedLyrics(track) ?: Lyrics.NotFound }
        }
        return Lyrics.NotFound
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
private val numberedPart = Regex("""^(.+?)\s*(?:[-–—(]\s*)?pt\.\s*(\d+)\s*\)?$""", RegexOption.IGNORE_CASE)

private fun String.kugeciQueries(): List<String> {
    val title = trim()
    val simplified = ZhConverterUtil.toSimple(title)
    val stem = numberedPart.matchEntire(simplified)?.groupValues?.get(1)
    val hanPrefix = stem?.takeIf { it.length > 1 && it.all { char -> UnicodeScript.of(char.code) == UnicodeScript.HAN } }
        ?.take(1)
    return listOfNotNull(title, simplified, stem, hanPrefix).distinct()
}

private fun Document.matchingSongIds(track: LyricsTrack): Set<String> =
    select("#tablesort tbody tr").mapNotNull { row ->
        val cells = row.children()
        val song = cells.getOrNull(1)?.selectFirst("a[href]") ?: return@mapNotNull null
        val id = songHref.matchEntire(song.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
        val artists = cells.getOrNull(2)?.select("a[href*=/singer/]")?.map { it.text() }.orEmpty()
        id.takeIf { track.matches(song.text(), artists) }
    }.toSet()

private fun Document.timedLyrics(track: LyricsTrack): Lyrics.Timed? {
    val title = selectFirst("h1")?.text() ?: return null
    val artists = select(".song-details-container a[href*=/singer/]").map { artist ->
        artist.text().replaceFirst(Regex("""^演唱\s*[:：]\s*"""), "")
    }
    if (!track.matches(title, artists)) return null
    val lrc = selectFirst("#lrc #lyricsContainer")?.wholeText() ?: return null
    val lines = LrcParser.parse(lrc)
    return Lyrics.Timed(lines).takeIf { lines.any { line -> line.text.isNotBlank() } }
}

private fun LyricsTrack.matches(candidateTitle: String, candidateArtists: List<String>): Boolean {
    if (title.kugeciTitleKey() != candidateTitle.kugeciTitleKey()) return false
    val artistName = artist.kugeciMatchKey()
    val names = candidateArtists.map { it.kugeciMatchKey() }.toSet()
    return when (names.size) {
        0 -> false
        1 -> artistName == names.single()
        else -> artistName.split(artistSeparators).map(String::trim).toSet() == names
    }
}

private fun String.kugeciTitleKey(): String {
    val key = kugeciMatchKey()
    val part = numberedPart.matchEntire(key) ?: return key
    val (stem, number) = part.destructured
    val name = if (stem.all { it.isWhitespace() || UnicodeScript.of(it.code) == UnicodeScript.HAN }) {
        stem.filterNot(Char::isWhitespace)
    } else {
        stem
    }
    return "$name pt.$number"
}

private fun String.kugeciMatchKey(): String = normalizedLyricsMetadata().toTraditionalLyricText()
