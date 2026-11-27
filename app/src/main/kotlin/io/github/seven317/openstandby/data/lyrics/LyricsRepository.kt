package io.github.seven317.openstandby.data.lyrics

import android.os.SystemClock
import android.util.LruCache
import com.google.gson.JsonParseException
import io.github.seven317.openstandby.domain.lyrics.LrcParser
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

internal data class LyricsTrack(val title: String, val artist: String, val album: String?, val durationSeconds: Int?)

internal class LyricsRepository(
    primaryApi: LrclibApi,
    backupApi: LrclibApi,
    userAgent: String,
    neteaseApi: NeteaseLyricsApi? = null,
) {
    private val primary = RemoteLyricsProvider(primaryApi, userAgent, LyricsSource.Lrclib)
    private val backup = RemoteLyricsProvider(backupApi, userAgent, LyricsSource.LrcMux)
    private val netease = neteaseApi?.let(::NeteaseLyricsProvider)
    private val requests = Mutex()
    private val cache = LruCache<LyricsTrack, CachedLyrics>(40)

    suspend fun lookup(track: LyricsTrack, refresh: Boolean): ResolvedLyrics = requests.withLock {
        val now = SystemClock.elapsedRealtime()
        if (!refresh) cache[track]?.takeIf { now < it.expiresAtMs }?.let { return@withLock it.result }

        val first = primary.lookup(track)
        val existing = if (first.content is Lyrics.Timed || first.content == Lyrics.Instrumental) {
            first
        } else {
            preferLyricResult(first, backup.lookup(track))
        }
        val result = if (netease == null || existing.content is Lyrics.Timed || existing.content == Lyrics.Instrumental) {
            existing
        } else {
            preferLyricResult(existing, netease.lookup(track))
        }
        val ttl = when (result.content) {
            is Lyrics.Timed, Lyrics.Instrumental -> Long.MAX_VALUE
            is Lyrics.Plain, Lyrics.NotFound -> SystemClock.elapsedRealtime() + 300_000
            else -> null
        }
        if (ttl != null) cache.put(track, CachedLyrics(result, ttl))
        result
    }

    private data class CachedLyrics(val result: ResolvedLyrics, val expiresAtMs: Long)
}

internal fun preferLyricResult(primary: ResolvedLyrics, backup: ResolvedLyrics): ResolvedLyrics = when {
    backup.content is Lyrics.Timed -> backup
    primary.content is Lyrics.Plain -> primary
    backup.content is Lyrics.Plain || backup.content == Lyrics.Instrumental -> backup
    primary.content == Lyrics.RateLimited || backup.content == Lyrics.RateLimited -> ResolvedLyrics(Lyrics.RateLimited)
    primary.content == Lyrics.Unavailable || backup.content == Lyrics.Unavailable -> ResolvedLyrics(Lyrics.Unavailable)
    else -> ResolvedLyrics(Lyrics.NotFound)
}

private class RemoteLyricsProvider(
    private val api: LrclibApi,
    private val userAgent: String,
    private val source: LyricsSource,
) {
    private var retryAfterMs = 0L
    private var nextRequestAtMs = 0L

    suspend fun lookup(track: LyricsTrack): ResolvedLyrics {
        if (SystemClock.elapsedRealtime() < retryAfterMs) return ResolvedLyrics(Lyrics.RateLimited)
        val exact = withTimeoutOrNull(6_000) { fetch(track) } ?: Lyrics.Unavailable
        val lyrics = if (source == LyricsSource.Lrclib && track.durationSeconds != null &&
            (exact == Lyrics.NotFound || exact is Lyrics.Plain)
        ) {
            withTimeoutOrNull(2_500) { searchForTimedVersion(track) } ?: exact
        } else exact
        return ResolvedLyrics(lyrics, source.takeIf { lyrics is Lyrics.Timed || lyrics is Lyrics.Plain || lyrics == Lyrics.Instrumental })
    }

    private suspend fun fetch(track: LyricsTrack): Lyrics = try {
        val precise = request(track, track.album)
        val response = if (precise.code() == 404 && track.album != null && source == LyricsSource.Lrclib) {
            precise.errorBody()?.close()
            request(track, album = null)
        } else precise

        if (response.isSuccessful) {
            withContext(Dispatchers.Default) {
                val record = response.body()
                val reported = record?.duration?.takeIf { it > 0 }
                val expected = track.durationSeconds
                if (source == LyricsSource.LrcMux && expected != null && reported != null && abs(reported - expected) > 6) {
                    Lyrics.NotFound
                } else record?.toLyrics() ?: Lyrics.Unavailable
            }
        } else {
            failure(response)
        }
    } catch (_: IOException) {
        Lyrics.Unavailable
    } catch (_: JsonParseException) {
        Lyrics.Unavailable
    }

    private suspend fun searchForTimedVersion(track: LyricsTrack): Lyrics.Timed? = try {
        val response = pacedRequest { api.searchLyrics(userAgent, track.title, track.artist) }
        if (response.isSuccessful) {
            withContext(Dispatchers.Default) {
                findTimedVersion(track, response.body().orEmpty())
            }
        } else {
            failure(response)
            null
        }
    } catch (_: IOException) {
        null
    } catch (_: JsonParseException) {
        null
    }

    private fun failure(response: Response<*>): Lyrics {
        response.errorBody()?.close()
        return when (response.code()) {
            404 -> Lyrics.NotFound
            429, 503 -> {
                val now = SystemClock.elapsedRealtime()
                val seconds = response.headers()["Retry-After"]?.toLongOrNull()?.coerceAtLeast(0) ?: 30
                val waitMs = TimeUnit.SECONDS.toMillis(seconds).coerceAtMost(Long.MAX_VALUE - now)
                val jitterMs = Random.nextLong(waitMs / 4 + 1)
                retryAfterMs = now + waitMs + jitterMs.coerceAtMost(Long.MAX_VALUE - now - waitMs)
                Lyrics.RateLimited
            }
            else -> Lyrics.Unavailable
        }
    }

    private suspend fun request(track: LyricsTrack, album: String?): Response<LrclibTrack> = pacedRequest {
        api.getLyrics(userAgent, track.title, track.artist, album, track.durationSeconds)
    }

    private suspend fun <T> pacedRequest(call: suspend () -> Response<T>): Response<T> {
        delay((nextRequestAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        return try {
            call()
        } finally {
            nextRequestAtMs = SystemClock.elapsedRealtime() + 250
        }
    }

    private fun LrclibTrack.toLyrics(): Lyrics {
        if (instrumental) return Lyrics.Instrumental
        val lines = synced?.let(LrcParser::parse).orEmpty()
        if (lines.any { it.text.isNotBlank() }) return Lyrics.Timed(lines)
        return plain?.trim()?.takeIf(String::isNotEmpty)?.let { Lyrics.Plain(it) } ?: Lyrics.NotFound
    }
}

internal fun findTimedVersion(track: LyricsTrack, records: List<LrclibTrack>): Lyrics.Timed? {
    val duration = track.durationSeconds ?: return null
    val title = track.title.normalizedLyricsMetadata()
    val artist = track.artist.normalizedLyricsMetadata()
    val album = track.album?.normalizedLyricsMetadata()
    return records.asSequence()
        .filter { !it.instrumental && !it.synced.isNullOrBlank() && it.duration != null && abs(it.duration - duration) <= 4 }
        .filter { it.title?.normalizedLyricsMetadata() == title && it.artist?.normalizedLyricsMetadata() == artist }
        .sortedWith(compareBy<LrclibTrack> { abs(checkNotNull(it.duration) - duration) }
            .thenBy { album != null && it.album?.normalizedLyricsMetadata() != album })
        .firstNotNullOfOrNull { record ->
            val lines = LrcParser.parse(checkNotNull(record.synced))
            if (lines.any { it.text.isNotBlank() }) Lyrics.Timed(lines) else null
        }
}

private val whitespace = Regex("\\s+")

internal fun String.normalizedLyricsMetadata(): String =
    Normalizer.normalize(this, Normalizer.Form.NFKC).trim().replace(whitespace, " ").lowercase(Locale.ROOT)
