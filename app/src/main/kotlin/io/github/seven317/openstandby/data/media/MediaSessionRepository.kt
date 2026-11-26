package io.github.seven317.openstandby.data.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.annotation.MainThread
import io.github.seven317.openstandby.domain.playback.PlaybackTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class MediaSessionRepository(context: Context) {
    private val manager = requireNotNull(context.getSystemService(MediaSessionManager::class.java))
    private val notifications = requireNotNull(context.getSystemService(NotificationManager::class.java))
    val listenerComponent = ComponentName(context, MediaNotificationListenerService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val observers = mutableMapOf<MediaSession.Token, SessionObserver>()
    private var systemOrder = emptyList<MediaSession.Token>()
    private var selectedToken: MediaSession.Token? = null
    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var notificationListener: MediaNotificationListenerService? = null
    private var foreground = false
    private var retryIndex = 0
    private val retryConnection = Runnable { refreshConnection() }
    private var nextTrackId = 0L

    private val _hasNotificationAccess = MutableStateFlow(false)
    val hasNotificationAccess = _hasNotificationAccess.asStateFlow()
    private val _state = MutableStateFlow<MediaSessionState>(MediaSessionState.Disconnected)
    val state = _state.asStateFlow()

    @MainThread
    fun setForeground(active: Boolean) {
        foreground = active
        if (active) reconnect() else mainHandler.removeCallbacks(retryConnection)
    }

    @MainThread
    fun reconnect() {
        retryIndex = 0
        refreshConnection()
    }

    @MainThread
    fun onNotificationListenerConnected(service: MediaNotificationListenerService) {
        notificationListener = service
        reconnect()
    }

    @MainThread
    fun onNotificationListenerDisconnected(service: MediaNotificationListenerService) {
        if (notificationListener !== service) return
        notificationListener = null
        reconnect()
    }

    private fun refreshConnection() {
        mainHandler.removeCallbacks(retryConnection)
        val granted = notifications.isNotificationListenerAccessGranted(listenerComponent)
        _hasNotificationAccess.value = granted
        if (!granted) {
            notificationListener = null
            clearSessions()
            return
        }

        // MediaSessionManager can use the granted component while its notification callbacks reconnect.
        try {
            if (activeSessionsListener == null) registerSessionListener()
            updateSessions(manager.getActiveSessions(listenerComponent), refreshExisting = true)
        } catch (_: SecurityException) {
            clearSessions()
        }

        if (foreground && (notificationListener == null || activeSessionsListener == null)) {
            val delay = connectionRetryDelays.getOrNull(retryIndex) ?: return
            retryIndex++
            if (notificationListener == null) {
                try {
                    NotificationListenerService.requestRebind(listenerComponent)
                } catch (_: SecurityException) {
                    // The notification access grant can change between the check and this call.
                }
            }
            mainHandler.postDelayed(retryConnection, delay)
        }
    }

    private fun registerSessionListener() {
        val listener = object : MediaSessionManager.OnActiveSessionsChangedListener {
            override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
                if (activeSessionsListener === this) updateSessions(controllers.orEmpty())
            }
        }
        manager.addOnActiveSessionsChangedListener(listener, listenerComponent, mainHandler)
        activeSessionsListener = listener
    }

    private fun clearSessions() {
        val listener = activeSessionsListener
        activeSessionsListener = null
        listener?.let(manager::removeOnActiveSessionsChangedListener)
        observers.values.forEach { it.stop() }
        observers.clear()
        systemOrder = emptyList()
        selectedToken = null
        _state.value = MediaSessionState.Disconnected
    }

    @MainThread
    fun dispatch(trackId: Long, command: PlaybackCommand) {
        selectedTrack(trackId)?.dispatch(command)
    }

    @MainThread
    fun seekTo(trackId: Long, positionMs: Long): Boolean =
        selectedTrack(trackId)?.seekTo(positionMs) ?: false

    private fun selectedTrack(trackId: Long): SessionObserver? =
        selectedToken?.let(observers::get)?.takeIf { it.trackId == trackId }

    private fun updateSessions(controllers: List<MediaController>, refreshExisting: Boolean = false) {
        systemOrder = controllers.map { it.sessionToken }
        val activeTokens = systemOrder.toSet()
        val registered = observers.iterator()
        while (registered.hasNext()) {
            val (token, observer) = registered.next()
            if (token !in activeTokens) {
                registered.remove()
                observer.stop()
            }
        }

        for (controller in controllers) {
            val token = controller.sessionToken
            val existing = observers[token]
            if (existing == null) {
                observers[token] = SessionObserver(controller).also { it.start() }
            } else if (refreshExisting) {
                existing.refresh()
            }
        }
        publishSelection()
    }

    private fun publishSelection() {
        val current = selectedToken?.let(observers::get)
        val selected = when {
            current?.status == PlaybackStatus.Playing -> current
            else -> systemOrder.firstNotNullOfOrNull { token ->
                observers[token]?.takeIf { it.status == PlaybackStatus.Playing }
            } ?: current ?: systemOrder.firstNotNullOfOrNull(observers::get)
        }

        selectedToken = selected?.token
        _state.value = selected?.let { MediaSessionState.Active(it.snapshot()) }
            ?: MediaSessionState.NoActiveSession
    }

    private inner class SessionObserver(private val controller: MediaController) : MediaController.Callback() {
        val token = controller.sessionToken
        var trackId = ++nextTrackId
            private set
        private var track = TrackIdentity(null, null, null, null)
        private var artwork = MediaArtwork(null, emptyList())
        private var durationMs: Long? = null
        private var playbackState: PlaybackState? = null
        val status: PlaybackStatus get() = playbackState.toPlaybackStatus()

        private val toggleAction: Long
            get() = if (status.showsPause) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY

        fun start() {
            controller.registerCallback(this, mainHandler)
            refresh()
        }

        fun refresh() {
            updateMetadata(controller.metadata)
            playbackState = controller.playbackState
        }

        fun stop() {
            controller.unregisterCallback(this)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (observers[token] !== this) return
            val previousTrackId = trackId
            updateMetadata(metadata)
            if (trackId != previousTrackId) playbackState = controller.playbackState
            publishSelection()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (observers[token] !== this) return
            playbackState = state
            publishSelection()
        }

        override fun onSessionDestroyed() {
            if (observers[token] !== this) return
            observers.remove(token)
            stop()
            publishSelection()
        }

        fun snapshot(): PlaybackSnapshot {
            val state = playbackState
            val status = state.toPlaybackStatus()
            return PlaybackSnapshot(
                trackId = trackId,
                sourcePackage = controller.packageName,
                mediaId = track.mediaId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                status = status,
                artwork = artwork,
                durationMs = durationMs,
                timeline = state?.takeIf { it.position >= 0 }?.let {
                    PlaybackTimeline(it.position, it.lastPositionUpdateTime, if (status.isMoving) it.playbackSpeed else 0f)
                },
                controls = PlaybackControls(
                    canSkipPrevious = supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                    canTogglePlayback = supports(toggleAction) || supports(PlaybackState.ACTION_PLAY_PAUSE),
                    canSkipNext = supports(PlaybackState.ACTION_SKIP_TO_NEXT),
                    canSeek = supports(PlaybackState.ACTION_SEEK_TO),
                    shuffleEnabled = spotifyAction(spotifyShuffleActions.keys)?.let { spotifyShuffleActions[it.action] },
                    repeatMode = spotifyAction(spotifyRepeatActions.keys)?.let { spotifyRepeatActions[it.action] },
                ),
            )
        }

        fun dispatch(command: PlaybackCommand) {
            val transport = controller.transportControls
            when (command) {
                PlaybackCommand.Previous -> if (supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) transport.skipToPrevious()
                PlaybackCommand.Next -> if (supports(PlaybackState.ACTION_SKIP_TO_NEXT)) transport.skipToNext()
                PlaybackCommand.ToggleShuffle -> spotifyAction(spotifyShuffleActions.keys)?.let {
                    transport.sendCustomAction(it, null)
                }
                PlaybackCommand.CycleRepeat -> spotifyAction(spotifyRepeatActions.keys)?.let {
                    transport.sendCustomAction(it, null)
                }
                PlaybackCommand.TogglePlayback -> when {
                    supports(toggleAction) -> if (status.showsPause) transport.pause() else transport.play()
                    supports(PlaybackState.ACTION_PLAY_PAUSE) -> {
                        controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                        controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    }
                    else -> Unit
                }
            }
        }

        fun seekTo(positionMs: Long): Boolean {
            if (!supports(PlaybackState.ACTION_SEEK_TO)) return false
            controller.transportControls.seekTo(positionMs.coerceIn(0, durationMs ?: Long.MAX_VALUE))
            return true
        }

        private fun supports(action: Long): Boolean = ((playbackState?.actions ?: 0L) and action) != 0L

        private fun spotifyAction(ids: Set<String>): PlaybackState.CustomAction? =
            if (controller.packageName == "com.spotify.music") {
                playbackState?.customActions?.firstOrNull { it.action in ids }
            } else null

        private fun updateMetadata(metadata: MediaMetadata?) {
            val updatedTrack = TrackIdentity(
                mediaId = metadata.text(MediaMetadata.METADATA_KEY_MEDIA_ID),
                title = metadata.text(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                album = metadata.text(MediaMetadata.METADATA_KEY_ALBUM),
            )
            if (updatedTrack != track) trackId = ++nextTrackId
            track = updatedTrack
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
            artwork = MediaArtwork(
                bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                uris = listOfNotNull(
                    metadata.artworkUri(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                    metadata.artworkUri(MediaMetadata.METADATA_KEY_ART_URI),
                    metadata.artworkUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
                ).distinct(),
            )
        }
    }
}

private val connectionRetryDelays = listOf(700L, 1_700L, 3_500L)

// Spotify names each action for the next mode it will turn on, so its ID also describes the current mode.
private val spotifyShuffleActions = mapOf("TURN_SHUFFLE_ON" to false, "TURN_SHUFFLE_OFF" to true)

private val spotifyRepeatActions = mapOf(
    "TURN_REPEAT_ALL_ON" to RepeatMode.Off,
    "TURN_REPEAT_ONE_ON" to RepeatMode.All,
    "TURN_REPEAT_OFF" to RepeatMode.One,
)

private data class TrackIdentity(val mediaId: String?, val title: String?, val artist: String?, val album: String?)

private fun MediaMetadata?.text(key: String): String? =
    this?.getText(key)?.toString()?.takeIf { it.isNotBlank() }

private fun MediaMetadata?.artworkUri(key: String): Uri? = this?.getString(key)
    ?.let(Uri::parse)
    ?.takeIf { it.scheme in setOf("content", "android.resource", "https") }

private fun PlaybackState?.toPlaybackStatus(): PlaybackStatus = when (this?.state) {
    PlaybackState.STATE_NONE -> PlaybackStatus.Idle
    PlaybackState.STATE_PLAYING -> PlaybackStatus.Playing
    PlaybackState.STATE_PAUSED -> PlaybackStatus.Paused
    PlaybackState.STATE_STOPPED -> PlaybackStatus.Stopped
    PlaybackState.STATE_BUFFERING -> PlaybackStatus.Buffering
    PlaybackState.STATE_CONNECTING -> PlaybackStatus.Connecting
    PlaybackState.STATE_FAST_FORWARDING -> PlaybackStatus.FastForwarding
    PlaybackState.STATE_REWINDING -> PlaybackStatus.Rewinding
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> PlaybackStatus.Skipping
    PlaybackState.STATE_ERROR -> PlaybackStatus.Error
    else -> PlaybackStatus.Unknown
}
