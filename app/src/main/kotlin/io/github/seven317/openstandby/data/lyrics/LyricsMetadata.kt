package io.github.seven317.openstandby.data.lyrics

import io.github.seven317.openstandby.domain.lyrics.toTraditionalLyricText
import java.text.Normalizer
import java.util.Locale

private val whitespace = Regex("\\s+")
private val liveSuffix = Regex("""\s*(?:\(\s*live\s*版?\s*\)|[-–—]\s*live)$""", RegexOption.IGNORE_CASE)

internal fun String.normalizedLyricsMetadata(): String =
    Normalizer.normalize(this, Normalizer.Form.NFKC).trim().replace(whitespace, " ").lowercase(Locale.ROOT)

internal fun String.lyricsMetadataKey(): String = normalizedLyricsMetadata().toTraditionalLyricText()

internal fun String.liveTitleStem(): String? {
    val title = Normalizer.normalize(this, Normalizer.Form.NFKC).trim()
    val suffix = liveSuffix.find(title) ?: return null
    return title.substring(0, suffix.range.first).takeIf(String::isNotBlank)
}

internal fun String.lyricsTitleKey(): String {
    val stem = liveTitleStem() ?: return lyricsMetadataKey()
    return "${stem.lyricsMetadataKey()} (live)"
}

internal val LyricsTrack.requiresLiveAlbumMatch: Boolean
    get() = !album.isNullOrBlank() && title.liveTitleStem() != null

internal fun LyricsTrack.matchesLiveAlbum(candidateAlbum: String?): Boolean =
    !requiresLiveAlbumMatch || album?.lyricsMetadataKey() == candidateAlbum?.lyricsMetadataKey()
