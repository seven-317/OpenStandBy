package io.github.seven317.openstandby.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seven317.openstandby.R
import io.github.seven317.openstandby.data.media.PlaybackCommand
import io.github.seven317.openstandby.data.media.PlaybackSnapshot
import io.github.seven317.openstandby.data.media.PlaybackStatus
import io.github.seven317.openstandby.data.media.RepeatMode
import kotlin.math.roundToInt

internal const val TRACK_FADE_MILLIS = 2_500
internal const val LYRICS_CONTROLS_EXIT_MILLIS = 190
private const val LYRICS_TRANSITION_MILLIS = 520
private val lyricsBoundsTransform = BoundsTransform { _, _ -> tween(LYRICS_TRANSITION_MILLIS) }
private enum class SharedTrackElement { Artwork, Metadata }

@Composable
internal fun MusicScreen(
    playback: PlaybackSnapshot,
    lyricsMode: Boolean,
    lyrics: TrackLyrics?,
    traditionalLyrics: Boolean,
    onTraditionalLyricsChange: (Boolean) -> Unit,
    onCommand: (Long, PlaybackCommand) -> Unit,
    onSeek: (Long, Long) -> Boolean,
    onToggleLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onImportLyrics: (Long) -> Unit,
    onRemoveLyrics: (Long) -> Unit,
) {
    val displayedTrack by rememberDisplayedTrack(playback)
    val position = key(playback.trackId) { rememberPlaybackPosition(playback.timeline, playback.durationMs) }
    val command = { command: PlaybackCommand -> onCommand(playback.trackId, command) }
    val seek = { positionMs: Long -> onSeek(playback.trackId, positionMs) }
    var outgoingLyrics by remember(playback.trackId) { mutableStateOf<TrackLyrics?>(null) }
    SideEffect {
        // Keep the loaded text through the exit animation after the view model clears its lyric state.
        if (lyricsMode && lyrics?.result != null) outgoingLyrics = lyrics
    }
    val lyricsProgress by animateFloatAsState(
        if (lyricsMode) 1f else 0f,
        tween(LYRICS_TRANSITION_MILLIS),
        label = "Lyrics background",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centerPlayer = maxHeight < 460.dp
        ArtworkBackground(
            displayedTrack.artwork,
            blurRadius = (14f + 16f * lyricsProgress).dp,
            modifier = Modifier.matchParentSize(),
        )
        val contrast = Brush.verticalGradient(
            0f to Color(0x27101418),
            0.48f to lerp(Color(0x37101418), Color(0x81101418), if (centerPlayer) 1f else lyricsProgress),
            0.76f to lerp(Color(0xAE101418), Color(0x8E101418), if (centerPlayer) 1f else lyricsProgress),
            1f to Color(0xE0101418),
        )
        Box(Modifier.matchParentSize().background(contrast))
        SharedTransitionLayout(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            AnimatedContent(
                targetState = lyricsMode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(300, delayMillis = 110)) togetherWith fadeOut(tween(LYRICS_CONTROLS_EXIT_MILLIS))
                },
                label = "Lyrics mode",
            ) { showLyrics ->
                val coverMotion = Modifier.sharedElement(
                    rememberSharedContentState(SharedTrackElement.Artwork),
                    animatedVisibilityScope = this,
                    boundsTransform = lyricsBoundsTransform,
                )
                val metadataMotion = Modifier.sharedBounds(
                    rememberSharedContentState(SharedTrackElement.Metadata),
                    animatedVisibilityScope = this,
                    enter = fadeIn(tween(230, delayMillis = 120)),
                    exit = fadeOut(tween(230, delayMillis = 120)),
                    boundsTransform = lyricsBoundsTransform,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillHeight, Alignment.CenterStart),
                )
                if (showLyrics) {
                    LyricsScreen(
                        displayedTrack = displayedTrack,
                        playback = playback,
                        position = position,
                        lyrics = if (lyricsMode) lyrics else outgoingLyrics,
                        traditionalLyrics = traditionalLyrics,
                        onTraditionalLyricsChange = onTraditionalLyricsChange,
                        coverMotion = coverMotion,
                        metadataMotion = metadataMotion,
                        onClose = onToggleLyrics,
                        onRetry = onRetryLyrics,
                        onImport = { onImportLyrics(playback.trackId) },
                        onRemoveLocal = { onRemoveLyrics(playback.trackId) },
                        onCommand = command,
                        onSeek = seek,
                    )
                } else {
                    PlayerLayout(
                        displayedTrack, playback, position, centerPlayer, coverMotion, metadataMotion, command, seek, onToggleLyrics,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerLayout(
    displayedTrack: DisplayedTrack,
    playback: PlaybackSnapshot,
    position: State<Long?>,
    centerPlayer: Boolean,
    coverMotion: Modifier,
    metadataMotion: Modifier,
    onCommand: (PlaybackCommand) -> Unit,
    onSeek: (Long) -> Boolean,
    onToggleLyrics: () -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = if (centerPlayer) Alignment.Center else Alignment.BottomCenter,
    ) {
        val compact = maxHeight < 300.dp
        val controlsBesideCover = centerPlayer && maxWidth >= 510.dp
        val coverSize = when {
            controlsBesideCover -> (maxHeight * 0.42f).coerceIn(76.dp, 152.dp)
            compact -> 54.dp
            else -> 72.dp
        }
        val coverCorner = (100 * (if (controlsBesideCover) 16 else 11) / coverSize.value).roundToInt()
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.widthIn(max = if (centerPlayer) 880.dp else 1040.dp).fillMaxWidth()
                .verticalScroll(scrollState, enabled = scrollState.maxValue > 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (controlsBesideCover) 12.dp else 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (controlsBesideCover) 24.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtwork(
                    displayedTrack.artwork?.bitmap,
                    Modifier.size(coverSize).then(coverMotion),
                    cornerPercent = coverCorner,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
                    TrackMetadata(
                        displayedTrack,
                        titleStyle = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                        bylineStyle = MaterialTheme.typography.bodyLarge,
                        modifier = metadataMotion,
                    )
                    if (controlsBesideCover) {
                        PlaybackButtons(playback, compact, onOpenLyrics = onToggleLyrics, onCommand = onCommand)
                    }
                }
            }
            key(playback.trackId) { SeekBar(playback, position, onSeek = onSeek) }
            if (!controlsBesideCover) {
                PlaybackButtons(playback, compact, onOpenLyrics = onToggleLyrics, onCommand = onCommand)
            }
        }
    }
}

@Composable
internal fun TrackMetadata(
    displayedTrack: DisplayedTrack,
    titleStyle: TextStyle,
    bylineStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val title = displayedTrack.title ?: stringResource(R.string.unknown_track)
    val byline = displayedTrack.byline.orEmpty()
    Crossfade(
        targetState = title to byline,
        modifier = modifier,
        animationSpec = tween(TRACK_FADE_MILLIS),
        label = "Track metadata",
    ) { (trackTitle, trackByline) ->
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TrackLabel(trackTitle, titleStyle.copy(fontWeight = FontWeight.SemiBold), MaterialTheme.colorScheme.onSurface)
            if (trackByline.isNotEmpty()) {
                TrackLabel(trackByline, bylineStyle, Color(0xFFE1DDD7))
            }
        }
    }
}

@Composable
internal fun LyricsToggle(showingLyrics: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (showingLyrics) R.string.close_lyrics else R.string.show_lyrics)
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color(0x4915191D),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        PlaybackIcon(if (showingLyrics) PlaybackGlyph.Close else PlaybackGlyph.Microphone, 21.dp)
    }
}

@Composable
private fun TrackLabel(text: String, style: TextStyle, color: Color) {
    key(text) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().clipToBounds().basicMarquee(iterations = Int.MAX_VALUE, velocity = 32.dp),
            style = style.copy(shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 2f), 6f)),
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun PlaybackButtons(
    playback: PlaybackSnapshot,
    compact: Boolean,
    dense: Boolean = false,
    onOpenLyrics: (() -> Unit)? = null,
    onCommand: (PlaybackCommand) -> Unit,
) {
    val shuffleEnabled = playback.controls.shuffleEnabled
    val repeatMode = playback.controls.repeatMode
    val showsPause = playback.status.showsPause
    val toggleLabel = stringResource(if (showsPause) R.string.pause_track else R.string.play_track)
    val statusLabel = stringResource(playback.status.label)
    val sideButtons = 2 + listOf(shuffleEnabled, repeatMode, onOpenLyrics).count { it != null }
    BoxWithConstraints {
        val smallButtons = compact || dense || maxWidth < 72.dp + 72.dp * sideButtons
        val sideSize = if (smallButtons) 48.dp else 56.dp
        val mainSize = when {
            dense || maxWidth < sideSize * sideButtons + 64.dp -> 48.dp
            smallButtons -> 64.dp
            else -> 72.dp
        }
        val totalButtonWidth = sideSize * sideButtons + mainSize
        val preferredSpacing = if (dense) 4.dp else if (smallButtons) 8.dp else 16.dp
        val spacing = minOf(preferredSpacing, ((maxWidth - totalButtonWidth) / sideButtons).coerceAtLeast(0.dp))
        Row(
            modifier = if (maxWidth < totalButtonWidth) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shuffleEnabled != null) {
                PlaybackModeButton(
                    R.string.shuffle, if (shuffleEnabled) R.string.mode_on else R.string.mode_off,
                    PlaybackGlyph.Shuffle, sideSize, shuffleEnabled,
                ) { onCommand(PlaybackCommand.ToggleShuffle) }
            }
            SkipButton(R.string.previous_track, PlaybackGlyph.Previous, sideSize, playback.controls.canSkipPrevious) {
                onCommand(PlaybackCommand.Previous)
            }
            FilledIconButton(
                onClick = { onCommand(PlaybackCommand.TogglePlayback) },
                enabled = playback.controls.canTogglePlayback,
                modifier = Modifier.size(mainSize).semantics {
                    contentDescription = toggleLabel
                    stateDescription = statusLabel
                },
            ) { PlaybackIcon(if (showsPause) PlaybackGlyph.Pause else PlaybackGlyph.Play, if (mainSize == 48.dp) 26.dp else 34.dp) }
            SkipButton(R.string.next_track, PlaybackGlyph.Next, sideSize, playback.controls.canSkipNext) {
                onCommand(PlaybackCommand.Next)
            }
            if (repeatMode != null) {
                val state = when (repeatMode) {
                    RepeatMode.Off -> R.string.mode_off
                    RepeatMode.All -> R.string.repeat_all
                    RepeatMode.One -> R.string.repeat_one
                }
                PlaybackModeButton(
                    R.string.repeat, state, if (repeatMode == RepeatMode.One) PlaybackGlyph.RepeatOne else PlaybackGlyph.Repeat,
                    sideSize, repeatMode != RepeatMode.Off,
                ) { onCommand(PlaybackCommand.CycleRepeat) }
            }
            if (onOpenLyrics != null) LyricsToggle(showingLyrics = false, onClick = onOpenLyrics)
        }
    }
}

@Composable
private fun PlaybackModeButton(label: Int, state: Int, glyph: PlaybackGlyph, size: Dp, active: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    val mode = stringResource(state)
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        modifier = Modifier.size(size).semantics {
            contentDescription = description
            stateDescription = mode
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PlaybackIcon(glyph, 24.dp)
            if (active) {
                Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp)) {
                    drawCircle(tint)
                }
            }
        }
    }
}

@Composable
private fun SkipButton(label: Int, glyph: PlaybackGlyph, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size).semantics { contentDescription = description },
    ) { PlaybackIcon(glyph, 24.dp) }
}

private enum class PlaybackGlyph { Previous, Play, Pause, Next, Microphone, Close, Shuffle, Repeat, RepeatOne }

@Composable
private fun PlaybackIcon(glyph: PlaybackGlyph, iconSize: Dp) {
    val tint = LocalContentColor.current
    Canvas(Modifier.size(iconSize)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            when (glyph) {
                PlaybackGlyph.Pause -> {
                    drawRoundRect(tint, Offset(6f, 5f), Size(4f, 14f), CornerRadius(1f))
                    drawRoundRect(tint, Offset(14f, 5f), Size(4f, 14f), CornerRadius(1f))
                }
                PlaybackGlyph.Play -> drawPath(triangle(8f, 19f), tint)
                PlaybackGlyph.Previous -> {
                    drawPath(triangle(19f, 8f), tint)
                    drawRoundRect(tint, Offset(4f, 5f), Size(2.5f, 14f), CornerRadius(0.75f))
                }
                PlaybackGlyph.Next -> {
                    drawPath(triangle(5f, 16f), tint)
                    drawRoundRect(tint, Offset(17.5f, 5f), Size(2.5f, 14f), CornerRadius(0.75f))
                }
                PlaybackGlyph.Microphone -> {
                    drawRoundRect(tint, Offset(9f, 3f), Size(6f, 11f), CornerRadius(3f), style = Stroke(1.8f))
                    drawArc(tint, 0f, 180f, false, Offset(5.5f, 5f), Size(13f, 13f), style = Stroke(1.8f, cap = StrokeCap.Round))
                    drawLine(tint, Offset(12f, 18f), Offset(12f, 21f), 1.8f, StrokeCap.Round)
                }
                PlaybackGlyph.Close -> {
                    drawLine(tint, Offset(6f, 6f), Offset(18f, 18f), 2f, StrokeCap.Round)
                    drawLine(tint, Offset(18f, 6f), Offset(6f, 18f), 2f, StrokeCap.Round)
                }
                PlaybackGlyph.Shuffle -> drawPath(shufflePath(), tint, style = Stroke(1.8f, cap = StrokeCap.Round))
                PlaybackGlyph.Repeat, PlaybackGlyph.RepeatOne -> {
                    drawPath(repeatPath(), tint, style = Stroke(1.8f, cap = StrokeCap.Round))
                    if (glyph == PlaybackGlyph.RepeatOne) {
                        drawLine(tint, Offset(10.5f, 11f), Offset(12.5f, 9.5f), 1.4f, StrokeCap.Round)
                        drawLine(tint, Offset(12.5f, 9.5f), Offset(12.5f, 14.6f), 1.4f, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

private fun shufflePath() = Path().apply {
    moveTo(3f, 6f)
    lineTo(7f, 6f)
    lineTo(10f, 9f)
    moveTo(3f, 18f)
    lineTo(7f, 18f)
    lineTo(17f, 6f)
    lineTo(21f, 6f)
    moveTo(18f, 3f)
    lineTo(21f, 6f)
    lineTo(18f, 9f)
    moveTo(14f, 15f)
    lineTo(17f, 18f)
    lineTo(21f, 18f)
    moveTo(18f, 15f)
    lineTo(21f, 18f)
    lineTo(18f, 21f)
}

private fun repeatPath() = Path().apply {
    moveTo(4f, 11f)
    lineTo(4f, 7f)
    quadraticTo(4f, 6f, 5f, 6f)
    lineTo(20f, 6f)
    moveTo(17f, 3f)
    lineTo(20f, 6f)
    lineTo(17f, 9f)
    moveTo(20f, 13f)
    lineTo(20f, 17f)
    quadraticTo(20f, 18f, 19f, 18f)
    lineTo(4f, 18f)
    moveTo(7f, 15f)
    lineTo(4f, 18f)
    lineTo(7f, 21f)
}

private fun triangle(baseX: Float, tipX: Float) = Path().apply {
    moveTo(baseX, 5f)
    lineTo(tipX, 12f)
    lineTo(baseX, 19f)
    close()
}

private val PlaybackStatus.label: Int
    get() = when (this) {
        PlaybackStatus.Unknown -> R.string.playback_unknown
        PlaybackStatus.Idle -> R.string.playback_idle
        PlaybackStatus.Playing -> R.string.playback_playing
        PlaybackStatus.Paused -> R.string.playback_paused
        PlaybackStatus.Stopped -> R.string.playback_stopped
        PlaybackStatus.Buffering -> R.string.playback_buffering
        PlaybackStatus.Connecting -> R.string.playback_connecting
        PlaybackStatus.FastForwarding -> R.string.playback_fast_forwarding
        PlaybackStatus.Rewinding -> R.string.playback_rewinding
        PlaybackStatus.Skipping -> R.string.playback_skipping
        PlaybackStatus.Error -> R.string.playback_error
    }
