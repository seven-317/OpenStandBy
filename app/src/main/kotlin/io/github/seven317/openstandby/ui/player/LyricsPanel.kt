package io.github.seven317.openstandby.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seven317.openstandby.R
import io.github.seven317.openstandby.data.media.PlaybackCommand
import io.github.seven317.openstandby.data.media.PlaybackSnapshot
import io.github.seven317.openstandby.domain.lyrics.LyricLine
import io.github.seven317.openstandby.domain.lyrics.Lyrics
import io.github.seven317.openstandby.domain.lyrics.LyricsSource
import io.github.seven317.openstandby.domain.lyrics.activeIndexAt
import kotlinx.coroutines.delay

@Composable
internal fun LyricsScreen(
    displayedTrack: DisplayedTrack,
    playback: PlaybackSnapshot,
    position: State<Long?>,
    lyrics: TrackLyrics?,
    traditionalLyrics: Boolean,
    onTraditionalLyricsChange: (Boolean) -> Unit,
    coverMotion: Modifier,
    metadataMotion: Modifier,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onRemoveLocal: () -> Unit,
    onCommand: (PlaybackCommand) -> Unit,
    onSeek: (Long) -> Boolean,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var interacting by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var optionsOpen by remember(playback.trackId) { mutableStateOf(false) }
    val content = lyrics?.result?.content
    val source = lyrics?.result?.source

    fun revealControls() {
        controlsVisible = true
        interaction++
    }

    fun toggleControls() {
        if (controlsVisible) controlsVisible = false else revealControls()
    }

    LaunchedEffect(controlsVisible, interaction, interacting, optionsOpen) {
        if (controlsVisible && !interacting && !optionsOpen) {
            delay(5_000)
            controlsVisible = false
        }
    }

    val controlsLabel = stringResource(if (controlsVisible) R.string.hide_controls else R.string.show_controls)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { toggleControls() } }
            .semantics {
                onClick(label = controlsLabel) {
                    toggleControls()
                    true
                }
            },
    ) {
        val bottomInset = minOf(100.dp, maxHeight * 0.43f)
        val headerWidth = minOf(340.dp, (maxWidth - 148.dp).coerceAtLeast(0.dp))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.widthIn(max = headerWidth).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlbumArtwork(displayedTrack.artwork?.bitmap, Modifier.size(38.dp).then(coverMotion), cornerPercent = 18)
                TrackMetadata(
                    displayedTrack,
                    titleStyle = MaterialTheme.typography.titleMedium,
                    bylineStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).then(metadataMotion),
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomInset),
                contentAlignment = Alignment.Center,
            ) {
                LyricsContent(playback, position, content, onRetry) { positionMs ->
                    revealControls()
                    onSeek(positionMs)
                }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { optionsOpen = true }) { Text(stringResource(R.string.lyrics_options)) }
                    DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                        val sourceName = when (source) {
                            LyricsSource.Lrclib -> "LRCLIB"
                            LyricsSource.LrcMux -> "LRCMux"
                            LyricsSource.Netease -> stringResource(R.string.lyrics_netease_source)
                            LyricsSource.Local -> stringResource(R.string.lyrics_local_source)
                            null -> null
                        }
                        if (sourceName != null) {
                            Text(
                                stringResource(R.string.lyrics_source, sourceName),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        TraditionalLyricsSwitch(
                            checked = traditionalLyrics,
                            onCheckedChange = {
                                onTraditionalLyricsChange(it)
                                optionsOpen = false
                            },
                            modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 12.dp),
                        )
                        if (lyrics?.canImport == true) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lyrics_import_file)) },
                                onClick = {
                                    optionsOpen = false
                                    onImport()
                                },
                            )
                            if (source == LyricsSource.Local) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_remove_local)) },
                                    onClick = {
                                        optionsOpen = false
                                        onRemoveLocal()
                                    },
                                )
                            }
                        }
                    }
                }
                LyricsToggle(showingLyrics = true, onClick = onClose)
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 1040.dp).fillMaxWidth(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        interacting = true
                        try {
                            while (awaitPointerEvent(PointerEventPass.Final).changes.any { it.pressed }) Unit
                        } finally {
                            interacting = false
                        }
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                key(playback.trackId) {
                    SeekBar(playback, position) { positionMs ->
                        revealControls()
                        onSeek(positionMs)
                    }
                }
                PlaybackButtons(playback, compact = true, dense = true) { command ->
                    revealControls()
                    onCommand(command)
                }
            }
        }
    }
}

@Composable
private fun LyricsContent(
    playback: PlaybackSnapshot,
    position: State<Long?>,
    lyrics: Lyrics?,
    onRetry: () -> Unit,
    onSeek: (Long) -> Boolean,
) {
    key(playback.trackId) {
        when (lyrics) {
            is Lyrics.Timed -> TimedLyrics(lyrics.lines, position, playback.controls.canSeek, onSeek)
            is Lyrics.Plain -> PlainLyrics(lyrics.text)
            else -> LyricsMessage(lyrics, onRetry)
        }
    }
}

@Composable
private fun TimedLyrics(lines: List<LyricLine>, position: State<Long?>, canSeek: Boolean, onSeek: (Long) -> Boolean) {
    val activeIndex by remember(lines, position) {
        derivedStateOf { position.value?.let(lines::activeIndexAt) ?: -1 }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIndex.coerceAtLeast(0))
    var followPlayback by rememberSaveable { mutableStateOf(true) }
    val manualScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) followPlayback = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(activeIndex, followPlayback) {
        if (followPlayback) listState.animateScrollToItem(activeIndex.coerceAtLeast(0))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centerPadding = (maxHeight / 2 - 29.dp).coerceAtLeast(0.dp)
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxSize().nestedScroll(manualScroll),
            state = listState,
            contentPadding = PaddingValues(vertical = centerPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(lines, key = { _, line -> line.timestampMs }) { index, line ->
                if (line.text.isBlank()) {
                    Spacer(Modifier.height(12.dp))
                } else {
                    LyricRow(line.text, index == activeIndex, canSeek) {
                        if (onSeek(line.timestampMs)) followPlayback = true
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !followPlayback,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            ResumeLyricsButton(onClick = { followPlayback = true })
        }
    }
}

@Composable
private fun ResumeLyricsButton(onClick: () -> Unit) {
    val label = stringResource(R.string.lyrics_follow_playback)
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(24.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val arrow = Path().apply {
                    moveTo(19f, 6f)
                    lineTo(19f, 13f)
                    lineTo(5f, 13f)
                    moveTo(10f, 8f)
                    lineTo(5f, 13f)
                    lineTo(10f, 18f)
                }
                drawPath(arrow, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun LyricRow(text: String, active: Boolean, canSeek: Boolean, onClick: () -> Unit) {
    val click = if (canSeek) Modifier.clickable(onClick = onClick) else Modifier
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.76f,
        animationSpec = tween(280),
        label = "Lyric emphasis scale",
    )
    val color by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSurface else Color(0xFFCFCCC6),
        animationSpec = tween(280),
        label = "Lyric emphasis color",
    )
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
                .clip(RoundedCornerShape(12.dp)).semantics { selected = active }
                .then(click).padding(horizontal = 6.dp, vertical = 8.dp),
            // Keep measurement fixed so active-line changes do not reflow the list.
            style = MaterialTheme.typography.headlineMedium.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f),
            ),
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Column(
        modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.lyrics_plain),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LyricsMessage(lyrics: Lyrics?, onRetry: () -> Unit) {
    val message = if (lyrics == Lyrics.NotFound) {
        val messages = stringArrayResource(R.array.lyrics_not_found_messages)
        val index = remember { messages.indices.random() }
        messages[index]
    } else {
        stringResource(when (lyrics) {
            null -> R.string.lyrics_loading
            Lyrics.Instrumental -> R.string.lyrics_instrumental
            Lyrics.MissingTrackDetails -> R.string.lyrics_missing_track_details
            Lyrics.RateLimited -> R.string.lyrics_rate_limited
            else -> R.string.lyrics_unavailable
        })
    }
    Column(
        modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (lyrics == Lyrics.NotFound || lyrics == Lyrics.Unavailable || lyrics == Lyrics.RateLimited) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_lyrics)) }
        }
    }
}
