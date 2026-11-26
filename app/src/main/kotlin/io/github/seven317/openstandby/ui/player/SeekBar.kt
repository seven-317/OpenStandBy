package io.github.seven317.openstandby.ui.player

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.seven317.openstandby.R
import io.github.seven317.openstandby.data.media.PlaybackSnapshot
import io.github.seven317.openstandby.domain.playback.PlaybackTimeline
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeekBar(
    playback: PlaybackSnapshot,
    positionState: State<Long?>,
    onSeek: (Long) -> Boolean,
) {
    val timeline = playback.timeline
    val duration = playback.durationMs
    val canSeek = playback.controls.canSeek && duration != null
    val position by positionState
    var dragPosition by remember(canSeek, duration) { mutableStateOf<Long?>(null) }
    var requestedSeek by remember { mutableStateOf<RequestedSeek?>(null) }

    LaunchedEffect(requestedSeek, timeline, canSeek) {
        val request = requestedSeek ?: return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        val confirmed = timeline != null && timeline != request.previousTimeline &&
            abs(timeline.positionAt(now, duration) - request.positionMs) <= 2_000
        if (canSeek && !confirmed) delay((request.sentAtMs + 1_500 - now).coerceAtLeast(0))
        requestedSeek = null
    }

    val shownPosition = (dragPosition ?: requestedSeek?.positionMs ?: position)
        ?.coerceAtMost(duration ?: Long.MAX_VALUE)
    val fraction = if (duration != null) ((shownPosition ?: 0L).toDouble() / duration).toFloat() else 0f
    val elapsed = formatTime(shownPosition)
    val total = formatTime(duration)
    val label = stringResource(R.string.playback_progress)
    val timeDescription = stringResource(R.string.playback_progress_description, elapsed, total)
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = if (canSeek) 1f else 0.38f)
    val sliderColors = SliderDefaults.colors(
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(elapsed)
        Slider(
            value = fraction,
            onValueChange = { newFraction ->
                if (duration != null) dragPosition = (duration * newFraction.toDouble()).roundToLong()
            },
            onValueChangeFinished = {
                dragPosition?.let { target ->
                    val sentAt = SystemClock.elapsedRealtime()
                    if (onSeek(target)) requestedSeek = RequestedSeek(target, sentAt, timeline)
                }
                dragPosition = null
            },
            enabled = canSeek,
            colors = sliderColors,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = label
                stateDescription = timeDescription
            },
            thumb = { Box(Modifier.size(12.dp).background(thumbColor, CircleShape)) },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    enabled = canSeek,
                    colors = sliderColors,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
        )
        TimeLabel(total)
    }
}

@Composable
internal fun rememberPlaybackPosition(timeline: PlaybackTimeline?, duration: Long?): State<Long?> {
    val owner = LocalLifecycleOwner.current
    return produceState(timeline?.positionAt(SystemClock.elapsedRealtime(), duration), timeline, duration, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = timeline?.positionAt(SystemClock.elapsedRealtime(), duration)
            if (timeline?.isMoving == true) {
                while (true) {
                    delay(100)
                    value = timeline.positionAt(SystemClock.elapsedRealtime(), duration)
                }
            }
        }
    }
}

@Composable
private fun TimeLabel(time: String) {
    Text(
        text = time,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatTime(position: Long?): String = position?.let { DateUtils.formatElapsedTime(it / 1_000) } ?: "--:--"

private data class RequestedSeek(val positionMs: Long, val sentAtMs: Long, val previousTimeline: PlaybackTimeline?)
