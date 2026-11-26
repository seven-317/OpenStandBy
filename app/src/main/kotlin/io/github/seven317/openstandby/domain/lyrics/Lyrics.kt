package io.github.seven317.openstandby.domain.lyrics

internal sealed interface Lyrics {
    data class Timed(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
    data object MissingTrackDetails : Lyrics
    data object Unavailable : Lyrics
    data object RateLimited : Lyrics
}

internal enum class LyricsSource { Lrclib, LrcMux, Local }

internal data class ResolvedLyrics(val content: Lyrics, val source: LyricsSource? = null)

internal data class LyricLine(val timestampMs: Long, val text: String)

internal fun List<LyricLine>.activeIndexAt(positionMs: Long): Int {
    val index = binarySearchBy(positionMs, selector = LyricLine::timestampMs)
    return if (index >= 0) index else -index - 2
}
