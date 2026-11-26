package io.github.seven317.openstandby.data.media

import android.graphics.Bitmap
import android.net.Uri
import io.github.seven317.openstandby.domain.playback.PlaybackTimeline

internal sealed interface MediaSessionState {
    data object Disconnected : MediaSessionState
    data object NoActiveSession : MediaSessionState
    data class Active(val playback: PlaybackSnapshot) : MediaSessionState
}

internal data class PlaybackSnapshot(
    val trackId: Long,
    val sourcePackage: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val status: PlaybackStatus,
    val artwork: MediaArtwork,
    val durationMs: Long?,
    val timeline: PlaybackTimeline?,
    val controls: PlaybackControls,
    val mediaId: String? = null,
)

internal data class MediaArtwork(val bitmap: Bitmap?, val uris: List<Uri>)

internal data class PlaybackControls(
    val canSkipPrevious: Boolean,
    val canTogglePlayback: Boolean,
    val canSkipNext: Boolean,
    val canSeek: Boolean,
    val shuffleEnabled: Boolean?,
    val repeatMode: RepeatMode?,
)

internal enum class RepeatMode { Off, All, One }

internal enum class PlaybackCommand { Previous, TogglePlayback, Next, ToggleShuffle, CycleRepeat }

internal enum class PlaybackStatus {
    Unknown,
    Idle,
    Playing,
    Paused,
    Stopped,
    Buffering,
    Connecting,
    FastForwarding,
    Rewinding,
    Skipping,
    Error;

    val isMoving: Boolean
        get() = this == Playing || this == FastForwarding || this == Rewinding

    val showsPause: Boolean
        get() = isMoving || this == Buffering || this == Connecting
}
