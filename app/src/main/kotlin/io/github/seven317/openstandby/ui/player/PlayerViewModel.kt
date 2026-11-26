package io.github.seven317.openstandby.ui.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import io.github.seven317.openstandby.OpenStandByApplication
import io.github.seven317.openstandby.data.lyrics.LocalLyricsStore
import io.github.seven317.openstandby.data.lyrics.LyricsRepository
import io.github.seven317.openstandby.data.lyrics.LyricsTrack
import io.github.seven317.openstandby.data.lyrics.localLyricsKey
import io.github.seven317.openstandby.data.media.MediaSessionState
import io.github.seven317.openstandby.data.media.PlaybackCommand
import io.github.seven317.openstandby.data.media.MediaSessionRepository
import io.github.seven317.openstandby.data.media.PlaybackSnapshot
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.ResolvedLyrics
import io.github.seven317.openstandby.domain.lyrics.toTraditionalChinese
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class PlayerViewModel(
    private val repository: MediaSessionRepository,
    lyricsRepository: LyricsRepository,
    private val localLyricsStore: LocalLyricsStore,
    private val preferences: SharedPreferences,
) : ViewModel() {
    val mediaState = repository.state
    private val _lyricsMode = MutableStateFlow(false)
    val lyricsMode = _lyricsMode.asStateFlow()
    private val _traditionalLyrics = MutableStateFlow(preferences.getBoolean("traditional_lyrics", false))
    val traditionalLyrics = _traditionalLyrics.asStateFlow()
    private val lyricsAttempt = MutableStateFlow(0)
    private var refreshTrackId: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val originalLyrics = combine(mediaState, lyricsMode, lyricsAttempt) { state, showLyrics, attempt ->
        val playback = (state as? MediaSessionState.Active)?.playback
        if (showLyrics && playback != null) {
            LyricsRequest(playback.trackId, playback.lyricsTrack(), playback.localLyricsKey(), attempt)
        } else null
    }.distinctUntilChanged().transformLatest<LyricsRequest?, TrackLyrics?> { request ->
        if (request == null) {
            emit(null)
        } else {
            val canImport = request.localKey != null
            val refresh = refreshTrackId == request.trackId
            if (refresh) refreshTrackId = null
            emit(TrackLyrics(request.trackId, null, canImport))
            val local = request.localKey?.let { localLyricsStore.lookup(it) }
            val result = when {
                local != null -> ResolvedLyrics(local, LyricsSource.Local)
                request.track != null -> lyricsRepository.lookup(request.track, refresh)
                else -> ResolvedLyrics(Lyrics.MissingTrackDetails)
            }
            emit(TrackLyrics(request.trackId, result, canImport))
        }
    }

    val lyrics = originalLyrics.combine(traditionalLyrics) { track, convert ->
        val result = track?.result
        if (convert && result != null) {
            val content = withContext(Dispatchers.Default) { result.content.toTraditionalChinese() }
            track.copy(result = result.copy(content = content))
        } else track
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun toggleLyrics() = _lyricsMode.update { !it }

    fun setTraditionalLyrics(enabled: Boolean) {
        preferences.edit { putBoolean("traditional_lyrics", enabled) }
        _traditionalLyrics.value = enabled
    }

    fun retryLyrics() {
        refreshTrackId = currentPlayback()?.trackId
        lyricsAttempt.update { it + 1 }
    }

    fun lyricsImportKey(trackId: Long): String? = currentPlayback()?.takeIf { it.trackId == trackId }?.localLyricsKey()

    suspend fun importLyrics(key: String, uri: Uri): LyricsImportResult {
        if (currentPlayback()?.localLyricsKey() != key) return LyricsImportResult.TrackChanged
        if (!localLyricsStore.importFile(key, uri)) return LyricsImportResult.InvalidFile
        lyricsAttempt.update { it + 1 }
        return LyricsImportResult.Imported
    }

    suspend fun removeLocalLyrics(trackId: Long): Boolean {
        val key = lyricsImportKey(trackId) ?: return false
        if (!localLyricsStore.remove(key)) return false
        lyricsAttempt.update { it + 1 }
        return true
    }

    private fun currentPlayback(): PlaybackSnapshot? = (mediaState.value as? MediaSessionState.Active)?.playback

    fun dispatch(trackId: Long, command: PlaybackCommand) = repository.dispatch(trackId, command)

    fun seekTo(trackId: Long, positionMs: Long): Boolean = repository.seekTo(trackId, positionMs)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as OpenStandByApplication
                val container = application.container
                PlayerViewModel(
                    container.mediaSessionRepository,
                    container.lyricsRepository,
                    container.localLyricsStore,
                    application.getSharedPreferences("player_settings", Context.MODE_PRIVATE),
                )
            }
        }
    }
}

internal enum class LyricsImportResult { Imported, TrackChanged, InvalidFile }

internal data class TrackLyrics(val trackId: Long, val result: ResolvedLyrics?, val canImport: Boolean)

private data class LyricsRequest(val trackId: Long, val track: LyricsTrack?, val localKey: String?, val attempt: Int)

internal fun PlaybackSnapshot.localLyricsKey() = localLyricsKey(sourcePackage, mediaId, title, artist, album)

private fun PlaybackSnapshot.lyricsTrack(): LyricsTrack? {
    val track = title ?: return null
    val artist = artist ?: return null
    val seconds = durationMs?.let { (it / 1_000.0).roundToInt() }?.takeIf { it in 1..3_600 }
    return LyricsTrack(track, artist, album, seconds)
}
