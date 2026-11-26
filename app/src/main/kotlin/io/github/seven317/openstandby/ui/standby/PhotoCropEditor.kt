package io.github.seven317.openstandby.ui.standby

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.seven317.openstandby.R
import kotlin.math.roundToInt

@Composable
internal fun PhotoCropEditor(
    uri: Uri,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (Bitmap, IntRect) -> Unit,
) {
    val context = LocalContext.current
    val photo by produceState<CropSource>(CropSource.Loading, uri, context) {
        value = loadBackgroundPhoto(context, uri)?.let(CropSource::Ready) ?: CropSource.Unavailable
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = stringResource(R.string.background_cancel_crop)
                IconButton(onClick = onCancel, enabled = !saving, modifier = Modifier.semantics { contentDescription = label }) {
                    StandByControlIcon(settings = false)
                }
                Text(stringResource(R.string.background_crop_title), style = MaterialTheme.typography.headlineSmall)
            }
            when (val source = photo) {
                CropSource.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                CropSource.Unavailable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.background_photo_failed))
                }
                is CropSource.Ready -> CropControls(source.bitmap, aspect.coerceAtLeast(0.1f), saving, onSave)
            }
        }
    }
}

private sealed interface CropSource {
    data object Loading : CropSource
    data object Unavailable : CropSource
    data class Ready(val bitmap: Bitmap) : CropSource
}

@Composable
private fun CropControls(bitmap: Bitmap, aspect: Float, saving: Boolean, onSave: (Bitmap, IntRect) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val imageSize = IntSize(image.width, image.height)
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var centerX by rememberSaveable { mutableFloatStateOf(0.5f) }
    var centerY by rememberSaveable { mutableFloatStateOf(0.5f) }
    fun framing() = PhotoFraming(zoom, Offset(centerX, centerY))
    fun applyFraming(framing: PhotoFraming) {
        zoom = framing.zoom
        centerX = framing.center.x
        centerY = framing.center.y
    }

    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val label = stringResource(R.string.background_crop_preview)
            Canvas(
                Modifier.aspectRatio(aspect).clipToBounds()
                    .semantics { contentDescription = label }
                    .pointerInput(image, aspect, saving) {
                        if (!saving) {
                            detectTransformGestures { centroid, pan, scale, _ ->
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                applyFraming(framing().transform(
                                    imageSize,
                                    aspect,
                                    zoom * scale,
                                    focus = Offset(centroid.x / width, centroid.y / height),
                                    pan = Offset(pan.x / width, pan.y / height),
                                ))
                            }
                        }
                    },
            ) {
                val crop = framing().crop(imageSize, aspect)
                drawImage(
                    image = image,
                    srcOffset = crop.topLeft,
                    srcSize = crop.size,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
                val lineColor = Color.White.copy(alpha = 0.4f)
                for (step in 1..2) {
                    val x = size.width * step / 3
                    val y = size.height * step / 3
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
            }
        }
        Column(
            Modifier.weight(0.38f).widthIn(max = 220.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.background_crop_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val zoomLabel = stringResource(R.string.background_zoom)
            Text(zoomLabel, style = MaterialTheme.typography.labelLarge)
            Slider(
                value = zoom,
                onValueChange = { applyFraming(framing().transform(imageSize, aspect, it)) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = zoomLabel },
                valueRange = 1f..MAX_PHOTO_ZOOM,
                enabled = !saving,
            )
            Button(onClick = { onSave(bitmap, framing().crop(imageSize, aspect)) }, enabled = !saving) {
                Text(stringResource(if (saving) R.string.background_saving_photo else R.string.background_save_photo))
            }
            TextButton(onClick = { applyFraming(PhotoFraming()) }, enabled = !saving) {
                Text(stringResource(R.string.background_reset_crop))
            }
        }
    }
}
