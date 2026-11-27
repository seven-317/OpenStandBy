package io.github.seven317.openstandby.ui.standby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seven317.openstandby.AppLanguage
import io.github.seven317.openstandby.R
import io.github.seven317.openstandby.data.media.MediaSessionState
import io.github.seven317.openstandby.data.media.PlaybackCommand
import io.github.seven317.openstandby.ui.player.LYRICS_CONTROLS_EXIT_MILLIS
import io.github.seven317.openstandby.ui.player.MusicScreen
import io.github.seven317.openstandby.ui.player.TrackLyrics
import io.github.seven317.openstandby.ui.player.localLyricsKey
import kotlinx.coroutines.launch

private enum class StandByPage { Speed, Music, Clock }

@Composable
internal fun StandByPager(
    hasNotificationAccess: Boolean,
    mediaState: MediaSessionState,
    lyricsMode: Boolean,
    lyrics: TrackLyrics?,
    traditionalLyrics: Boolean,
    onTraditionalLyricsChange: (Boolean) -> Unit,
    backgroundStore: StandByBackgroundStore,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    updateSettings: @Composable () -> Unit,
    clockFace: ClockFace,
    onClockFaceChange: (ClockFace) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onReconnectMedia: () -> Unit,
    onPlaybackCommand: (Long, PlaybackCommand) -> Unit,
    onSeek: (Long, Long) -> Boolean,
    onToggleLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onImportLyrics: (Long) -> Unit,
    onRemoveLyrics: (Long) -> Unit,
) {
    var settingsPage by rememberSaveable { mutableStateOf<StandByPage?>(null) }
    var globalSettingsOpen by rememberSaveable { mutableStateOf(false) }
    val pager = remember {
        PagerState(currentPage = (settingsPage ?: StandByPage.Music).ordinal) { StandByPage.entries.size }
    }
    val scope = rememberCoroutineScope()
    val backgrounds by backgroundStore.state.collectAsStateWithLifecycle()
    val lyricsVisible = hasNotificationAccess && mediaState is MediaSessionState.Active && lyricsMode
    val currentPage = StandByPage.entries[pager.currentPage]
    val onLyricsPage = currentPage == StandByPage.Music && lyricsVisible
    val closeSettings: () -> Unit = {
        settingsPage?.let { pager.requestScrollToPage(it.ordinal) }
        settingsPage = null
    }
    BackHandler(enabled = settingsPage != null || currentPage != StandByPage.Music || onLyricsPage) {
        when {
            globalSettingsOpen -> globalSettingsOpen = false
            settingsPage != null -> closeSettings()
            onLyricsPage -> onToggleLyrics()
            else -> scope.launch { pager.animateScrollToPage(StandByPage.Music.ordinal) }
        }
    }

    val settingsModule = settingsPage
    if (settingsModule != null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (globalSettingsOpen) {
                GlobalSettings(
                    appLanguage,
                    onAppLanguageChange,
                    onBack = { globalSettingsOpen = false },
                    updateSettings = updateSettings,
                )
                return@Surface
            }
            val openGlobalSettings = { globalSettingsOpen = true }
            when (settingsModule) {
                StandByPage.Music -> {
                    val playback = (mediaState as? MediaSessionState.Active)?.playback?.takeIf { hasNotificationAccess }
                    val importId = remember(playback?.trackId) {
                        playback?.takeIf { it.localLyricsKey() != null }?.trackId
                    }
                    PlayerSettings(
                        trackTitle = playback?.title,
                        traditionalLyrics = traditionalLyrics,
                        onTraditionalLyricsChange = onTraditionalLyricsChange,
                        onImportLyrics = importId?.let { id -> { onImportLyrics(id) } },
                        onOpenGlobalSettings = openGlobalSettings,
                        onBack = closeSettings,
                    )
                }
                StandByPage.Clock, StandByPage.Speed -> {
                    val target = if (settingsModule == StandByPage.Clock) BackgroundTarget.Clock else BackgroundTarget.Speed
                    StandByBackgroundSettings(
                        target = target,
                        background = backgrounds.getValue(target),
                        onPreset = backgroundStore::selectPreset,
                        onBlur = backgroundStore::setBlur,
                        onSavePhoto = backgroundStore::savePhoto,
                        onOpenGlobalSettings = openGlobalSettings,
                        onBack = closeSettings,
                    ) {
                        if (target == BackgroundTarget.Clock) {
                            ClockFaceSettings(clockFace, backgrounds.getValue(target), onClockFaceChange)
                        }
                    }
                }
            }
        }
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val horizontalNavigation = Modifier.pointerInput(pager, layoutDirection) {
        val threshold = 48.dp.toPx()
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onHorizontalDrag = { _, delta -> distance += delta },
            onDragEnd = {
                val drag = if (layoutDirection == LayoutDirection.Rtl) -distance else distance
                val direction = when {
                    drag <= -threshold -> 1
                    drag >= threshold -> -1
                    else -> 0
                }
                val nextPage = pager.settledPage + direction
                if (direction != 0 && nextPage in StandByPage.entries.indices) {
                    scope.launch { pager.animateScrollToPage(nextPage) }
                }
            },
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().then(horizontalNavigation),
            userScrollEnabled = !onLyricsPage,
        ) { page ->
            val module = StandByPage.entries[page]
            Box(Modifier.fillMaxSize()) {
                when (module) {
                    StandByPage.Music -> MusicPage(
                        hasNotificationAccess = hasNotificationAccess,
                        mediaState = mediaState,
                        lyricsVisible = lyricsVisible,
                        lyrics = lyrics,
                        traditionalLyrics = traditionalLyrics,
                        onTraditionalLyricsChange = onTraditionalLyricsChange,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onReconnectMedia = onReconnectMedia,
                        onPlaybackCommand = onPlaybackCommand,
                        onSeek = onSeek,
                        onToggleLyrics = onToggleLyrics,
                        onRetryLyrics = onRetryLyrics,
                        onImportLyrics = onImportLyrics,
                        onRemoveLyrics = onRemoveLyrics,
                    )
                    StandByPage.Clock -> ClockModule(backgrounds.getValue(BackgroundTarget.Clock), clockFace)
                    StandByPage.Speed -> SpeedModule(backgrounds.getValue(BackgroundTarget.Speed), active = pager.settledPage == page)
                }
                AnimatedVisibility(
                    visible = module != StandByPage.Music || !lyricsVisible,
                    modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    enter = fadeIn(tween(160, delayMillis = LYRICS_CONTROLS_EXIT_MILLIS)),
                    exit = fadeOut(tween(120)),
                ) {
                    val label = stringResource(R.string.settings_title)
                    FilledTonalIconButton(onClick = { settingsPage = module }, modifier = Modifier.semantics { contentDescription = label }) {
                        StandByControlIcon(settings = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPage(
    hasNotificationAccess: Boolean,
    mediaState: MediaSessionState,
    lyricsVisible: Boolean,
    lyrics: TrackLyrics?,
    traditionalLyrics: Boolean,
    onTraditionalLyricsChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onReconnectMedia: () -> Unit,
    onPlaybackCommand: (Long, PlaybackCommand) -> Unit,
    onSeek: (Long, Long) -> Boolean,
    onToggleLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onImportLyrics: (Long) -> Unit,
    onRemoveLyrics: (Long) -> Unit,
) {
    when {
        !hasNotificationAccess -> CenteredMessage {
            Message(R.string.notification_access_title, R.string.notification_access_description)
            Button(onClick = onOpenNotificationSettings) {
                Text(stringResource(R.string.notification_access_button))
            }
        }
        mediaState is MediaSessionState.Active -> MusicScreen(
            playback = mediaState.playback,
            lyricsMode = lyricsVisible,
            lyrics = lyrics?.takeIf { it.trackId == mediaState.playback.trackId },
            traditionalLyrics = traditionalLyrics,
            onTraditionalLyricsChange = onTraditionalLyricsChange,
            onCommand = onPlaybackCommand,
            onSeek = onSeek,
            onToggleLyrics = onToggleLyrics,
            onRetryLyrics = onRetryLyrics,
            onImportLyrics = onImportLyrics,
            onRemoveLyrics = onRemoveLyrics,
        )
        mediaState == MediaSessionState.Disconnected -> CenteredMessage {
            Message(R.string.listener_connecting, R.string.listener_connecting_description)
            Button(onClick = onReconnectMedia) {
                Text(stringResource(R.string.listener_reconnect))
            }
        }
        else -> CenteredMessage {
            Message(R.string.no_active_session, R.string.no_active_session_description)
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun Message(title: Int, description: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(description),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}
