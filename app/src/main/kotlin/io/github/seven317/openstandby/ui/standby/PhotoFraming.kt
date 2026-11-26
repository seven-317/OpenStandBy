package io.github.seven317.openstandby.ui.standby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal const val MAX_PHOTO_ZOOM = 4f

internal data class PhotoFraming(val zoom: Float = 1f, val center: Offset = Offset(0.5f, 0.5f)) {
    fun crop(image: IntSize, aspect: Float): IntRect {
        val size = visibleSize(image, aspect)
        val width = size.width.roundToInt().coerceIn(1, image.width)
        val height = size.height.roundToInt().coerceIn(1, image.height)
        val left = (center.x * image.width - width / 2f).roundToInt().coerceIn(0, image.width - width)
        val top = (center.y * image.height - height / 2f).roundToInt().coerceIn(0, image.height - height)
        return IntRect(left, top, left + width, top + height)
    }

    fun transform(
        image: IntSize,
        aspect: Float,
        newZoom: Float,
        focus: Offset = Offset(0.5f, 0.5f),
        pan: Offset = Offset.Zero,
    ): PhotoFraming {
        val before = visibleSize(image, aspect)
        val after = copy(zoom = newZoom.coerceIn(1f, MAX_PHOTO_ZOOM))
        val visible = after.visibleSize(image, aspect)
        // Keep the source pixel under the pinch in place, then follow the finger's translation.
        val x = center.x * image.width + (focus.x - 0.5f) * (before.width - visible.width) - pan.x * visible.width
        val y = center.y * image.height + (focus.y - 0.5f) * (before.height - visible.height) - pan.y * visible.height
        return after.copy(center = Offset(
            x.coerceIn(visible.width / 2f, image.width - visible.width / 2f) / image.width,
            y.coerceIn(visible.height / 2f, image.height - visible.height / 2f) / image.height,
        ))
    }

    private fun visibleSize(image: IntSize, aspect: Float): Size {
        val width = minOf(image.width.toFloat(), image.height * aspect) / zoom
        return Size(width, minOf(width / aspect, image.height.toFloat()))
    }
}
