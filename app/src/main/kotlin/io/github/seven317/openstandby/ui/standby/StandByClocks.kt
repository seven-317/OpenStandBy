package io.github.seven317.openstandby.ui.standby

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.seven317.openstandby.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

internal enum class ClockFace(val title: Int) {
    Digital(R.string.clock_face_digital),
    Analog(R.string.clock_face_analog),
    Flip(R.string.clock_face_flip),
    Segmented(R.string.clock_face_segmented),
}

@Composable
internal fun rememberClockTime(): Long {
    val owner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val time = System.currentTimeMillis()
                value = time
                delay(1_000L - Math.floorMod(time, 1_000L))
            }
        }
    }
    return now
}

@Composable
internal fun ClockModule(background: ModuleBackground, face: ClockFace) {
    val context = LocalContext.current
    val now = rememberClockTime()
    val date = DateFormat.getLongDateFormat(context).format(Date(now))
    if (face == ClockFace.Digital) {
        val time = DateFormat.getTimeFormat(context).format(Date(now))
        ModuleReadout(background, stringResource(R.string.clock_module), time, date)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BackgroundImage(background, Modifier.matchParentSize())
        val faceHeight = (maxHeight - 80.dp).coerceIn(44.dp, 264.dp)
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(scrollState, enabled = scrollState.maxValue > 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClockFaceArtwork(face, now, Modifier.fillMaxWidth().height(faceHeight))
            Text(date, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun ClockFaceArtwork(face: ClockFace, now: Long, modifier: Modifier) {
    val context = LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(now))
    val localTime = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val locale = LocalConfiguration.current.locales[0]
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val ink = MaterialTheme.colorScheme.onSurface

    BoxWithConstraints(
        modifier.clearAndSetSemantics { contentDescription = time },
        contentAlignment = Alignment.Center,
    ) {
        when (face) {
            ClockFace.Digital -> {
                val fontSize = (minOf(maxWidth / 5.5f, maxHeight, 144.dp).value / fontScale).sp
                Text(time, fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            ClockFace.Analog -> AnalogClock(localTime, ink, Modifier.size(minOf(maxWidth, maxHeight)))
            ClockFace.Flip, ClockFace.Segmented -> {
                val use24HourTime = DateFormat.is24HourFormat(context)
                val hour = if (use24HourTime) localTime.hour else (localTime.hour + 11) % 12 + 1
                val minute = localTime.minute
                val period = if (use24HourTime) null else DateTimeFormatter.ofPattern("a", locale).format(localTime)
                val availableHeight = if (period == null) maxHeight else (maxHeight - 24.dp).coerceAtLeast(0.dp)
                val width = minOf(maxWidth, availableHeight * 3f, 540.dp)
                val faceModifier = Modifier.width(width).height(width / 3f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (face == ClockFace.Flip) {
                        val numbers = NumberFormat.getIntegerInstance(locale).apply { minimumIntegerDigits = 2 }
                        val digitSize = (width.value / (4.2f * fontScale)).sp
                        FlipClock(numbers.format(hour), numbers.format(minute), digitSize, ink, faceModifier)
                    } else {
                        SegmentedClock(hour, minute, ink, faceModifier)
                    }
                    if (period != null) Text(period, fontSize = (13f / fontScale).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AnalogClock(time: ZonedDateTime, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        drawCircle(Color(0x8C101216), radius * 0.99f)
        drawCircle(ink.copy(alpha = 0.38f), radius * 0.97f, style = Stroke(radius * 0.012f))
        for (tick in 0 until 60) {
            val hourMark = tick % 5 == 0
            val quarterMark = tick % 15 == 0
            drawLine(
                color = if (hourMark) ink else ink.copy(alpha = 0.43f),
                start = dialPoint(tick.toFloat(), radius * if (quarterMark) 0.72f else if (hourMark) 0.78f else 0.85f),
                end = dialPoint(tick.toFloat(), radius * 0.90f),
                strokeWidth = radius * if (hourMark) 0.026f else 0.010f,
                cap = StrokeCap.Round,
            )
        }
        val minute = time.minute + time.second / 60f
        val hour = (time.hour % 12) * 5 + minute / 12f
        drawLine(ink, center, dialPoint(hour, radius * 0.53f), radius * 0.066f, StrokeCap.Round)
        drawLine(ink, center, dialPoint(minute, radius * 0.69f), radius * 0.039f, StrokeCap.Round)
        val seconds = time.second.toFloat()
        val accent = Color(0xFFF0A17F)
        drawLine(accent, dialPoint(seconds + 30f, radius * 0.18f), dialPoint(seconds, radius * 0.82f), radius * 0.014f)
        drawCircle(accent, radius * 0.041f)
    }
}

private fun DrawScope.dialPoint(tick: Float, radius: Float): Offset {
    val angle = (tick / 30f - 0.5f) * PI
    return center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
}

@Composable
private fun FlipClock(
    hour: String,
    minute: String,
    digitSize: TextUnit,
    ink: Color,
    modifier: Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            FlipPanel(hour, digitSize, ink, Modifier.weight(1f))
            Canvas(Modifier.weight(0.18f).height(22.dp)) {
                val dotRadius = minOf(size.width * 0.13f, size.height * 0.13f)
                drawCircle(ink, dotRadius, Offset(center.x, size.height * 0.25f))
                drawCircle(ink, dotRadius, Offset(center.x, size.height * 0.75f))
            }
            FlipPanel(minute, digitSize, ink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlipPanel(
    digits: String,
    digitSize: TextUnit,
    ink: Color,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF24272D), Color(0xFF15171B))))
            .drawWithContent {
                drawContent()
                drawLine(Color(0xD9000000), Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.5.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            digits,
            style = TextStyle(
                color = ink,
                fontSize = digitSize,
                lineHeight = digitSize,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
    }
}

private val digitSegments = intArrayOf(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f)

@Composable
private fun SegmentedClock(hour: Int, minute: Int, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val digits = intArrayOf(hour / 10, hour % 10, minute / 10, minute % 10)
        val digitStarts = floatArrayOf(0f, 48f, 122f, 170f)
        scale(size.width / 210f, size.height / 70f, pivot = Offset.Zero) {
            for ((index, digit) in digits.withIndex()) {
                val x = digitStarts[index]
                val segments = digitSegments[digit]
                fun tint(bit: Int) = if (segments and (1 shl bit) != 0) ink else ink.copy(alpha = 0.15f)
                drawSegment(Offset(x + 20f, 4f), 0f, tint(0))
                drawSegment(Offset(x + 36f, 19.5f), 90f, tint(1))
                drawSegment(Offset(x + 36f, 50.5f), 90f, tint(2))
                drawSegment(Offset(x + 20f, 66f), 0f, tint(3))
                drawSegment(Offset(x + 4f, 50.5f), 90f, tint(4))
                drawSegment(Offset(x + 4f, 19.5f), 90f, tint(5))
                drawSegment(Offset(x + 20f, 35f), 0f, tint(6))
            }
            drawCircle(ink, 3.8f, Offset(105f, 24f))
            drawCircle(ink, 3.8f, Offset(105f, 46f))
        }
    }
}

private fun DrawScope.drawSegment(center: Offset, angle: Float, color: Color) {
    val segment = Path().apply {
        moveTo(center.x - 13.5f, center.y)
        lineTo(center.x - 9.5f, center.y - 3.5f)
        lineTo(center.x + 9.5f, center.y - 3.5f)
        lineTo(center.x + 13.5f, center.y)
        lineTo(center.x + 9.5f, center.y + 3.5f)
        lineTo(center.x - 9.5f, center.y + 3.5f)
        close()
    }
    rotate(angle, pivot = center) { drawPath(segment, color) }
}
