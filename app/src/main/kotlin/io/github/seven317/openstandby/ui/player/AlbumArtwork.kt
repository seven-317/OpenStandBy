package io.github.seven317.openstandby.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.seven317.openstandby.R
import io.github.seven317.openstandby.data.media.MediaArtwork
import io.github.seven317.openstandby.data.media.PlaybackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val ARTWORK_WAIT_MILLIS = 450L

internal data class LoadedArtwork(
    val bitmap: Bitmap,
    val playerBackground: Bitmap = bitmap,
    val lyricsBackground: Bitmap = bitmap,
)

internal data class DisplayedTrack(
    val trackId: Long,
    val title: String?,
    val byline: String?,
    val artwork: LoadedArtwork?,
)

@Composable
internal fun rememberDisplayedTrack(playback: PlaybackSnapshot): State<DisplayedTrack> {
    val context = LocalContext.current
    val artwork = playback.artwork
    val embedded = artwork.bitmap
    val useEmbedded = embedded != null && (artwork.uris.isEmpty() || embedded.shortEdge >= 640)
    val supportsRenderBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val initialArtwork = if (supportsRenderBlur) embedded?.let { LoadedArtwork(it) } else null
    val track = DisplayedTrack(playback.trackId, playback.title, playback.artist ?: playback.album, initialArtwork)
    // Artwork can arrive in a later metadata callback for the same track. Keep the original deadline.
    val deadline = remember(playback.trackId) { SystemClock.elapsedRealtime() + ARTWORK_WAIT_MILLIS }

    return produceState(track, playback.trackId, artwork, context) {
        if (artwork.uris.isEmpty() && embedded == null) {
            // Give the player a brief chance to attach artwork to the new track.
            delay(deadline - SystemClock.elapsedRealtime())
            if (value.trackId != track.trackId) value = track
            return@produceState
        }

        val preview = initialArtwork ?: embedded?.prepareArtwork(supportsRenderBlur)
        if (useEmbedded) {
            value = track.copy(artwork = preview)
            return@produceState
        }

        coroutineScope {
            val loading = async {
                val bitmap = loadArtwork(context, artwork)
                if (bitmap === embedded) preview else bitmap?.prepareArtwork(supportsRenderBlur)
            }
            if (value.trackId != track.trackId) {
                val ready = withTimeoutOrNull(deadline - SystemClock.elapsedRealtime()) { loading.await() }
                value = track.copy(artwork = ready ?: preview)
            } else if (value.artwork == null && preview != null) {
                value = track.copy(artwork = preview)
            }
            // The deadline only bounds the wait to present the track. The load can still finish later.
            loading.await()?.let { value = track.copy(artwork = it) }
        }
    }
}

private suspend fun Bitmap.prepareArtwork(supportsRenderBlur: Boolean): LoadedArtwork =
    if (supportsRenderBlur) {
        LoadedArtwork(this)
    } else {
        withContext(Dispatchers.Default) {
            LoadedArtwork(this@prepareArtwork, gaussianThumbnail(110), gaussianThumbnail(64))
        }
    }

private suspend fun loadArtwork(context: Context, artwork: MediaArtwork): Bitmap? {
    val embedded = artwork.bitmap
    for (uri in artwork.uris) {
        val request = ImageRequest.Builder(context).data(uri).size(960).allowHardware(false).build()
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val loaded = result.image.toBitmap()
            if (embedded == null || loaded.shortEdge > embedded.shortEdge) return loaded
        }
    }
    return embedded
}

private val Bitmap.shortEdge: Int get() = minOf(width, height)

@Composable
internal fun AlbumArtwork(artwork: Bitmap?, modifier: Modifier, cornerPercent: Int) {
    val shape = RoundedCornerShape(percent = cornerPercent)
    val label = stringResource(if (artwork == null) R.string.album_artwork_unavailable else R.string.album_artwork)
    Crossfade(
        targetState = artwork,
        modifier = modifier.clip(shape).background(Color(0xFF222329))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .semantics { contentDescription = label },
        animationSpec = tween(TRACK_FADE_MILLIS),
        label = "Album artwork",
    ) { source ->
        val image = remember(source) { source?.asImageBitmap() }
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.33f
                drawCircle(Color(0xFF16171A), radius)
                for (groove in 1..4) {
                    drawCircle(Color(0xFF34363B), radius * (0.50f + groove * 0.10f), style = Stroke(1.dp.toPx()))
                }
                drawCircle(Color(0xFFB8B3A9), radius * 0.30f)
                drawCircle(Color(0xFF16171A), radius * 0.047f)
            }
        }
    }
}

@Composable
internal fun ArtworkBackground(artwork: LoadedArtwork?, blurRadius: Dp, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds().background(Color(0xFF101012))) {
        Crossfade(
            targetState = artwork,
            modifier = Modifier.matchParentSize(),
            animationSpec = tween(TRACK_FADE_MILLIS),
            label = "Artwork background",
        ) { source ->
            val supportsRenderBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val background = when {
                supportsRenderBlur -> source?.bitmap
                blurRadius < 20.dp -> source?.playerBackground
                else -> source?.lyricsBackground
            }
            val image = remember(background) { background?.asImageBitmap() }
            if (image != null) {
                val blur = if (supportsRenderBlur) Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded) else Modifier
                Image(image, null, Modifier.fillMaxSize().then(blur), contentScale = ContentScale.Crop)
            }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to Color(0x2E101418),
                    0.55f to Color(0x38101418),
                    1f to Color(0x4C101418),
                ),
            ),
        )
    }
}

private fun Bitmap.gaussianThumbnail(thumbnailSize: Int): Bitmap {
    val ratio = thumbnailSize.toFloat() / maxOf(width, height)
    val scaled = scale((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1))
    val readable = if (scaled.config == Bitmap.Config.HARDWARE) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
    val pixels = IntArray(readable.width * readable.height)
    readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
    val horizontal = gaussianPass(pixels, readable.width, readable.height, horizontal = true)
    val blurred = gaussianPass(horizontal, readable.width, readable.height, horizontal = false)
    return Bitmap.createBitmap(blurred, readable.width, readable.height, Bitmap.Config.ARGB_8888)
}

private fun gaussianPass(pixels: IntArray, width: Int, height: Int, horizontal: Boolean): IntArray {
    val weights = intArrayOf(1, 4, 6, 4, 1)
    val output = IntArray(pixels.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -2..2) {
                val sampleX = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                val sampleY = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                val color = pixels[sampleY * width + sampleX]
                val weight = weights[offset + 2]
                red += ((color shr 16) and 255) * weight
                green += ((color shr 8) and 255) * weight
                blue += (color and 255) * weight
            }
            output[y * width + x] = (255 shl 24) or ((red / 16) shl 16) or ((green / 16) shl 8) or (blue / 16)
        }
    }
    return output
}
