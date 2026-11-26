package io.github.seven317.openstandby.ui.standby

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/** Bitmap fallback for Android 9 to 11, where Compose cannot blur a graphics layer. */
internal fun Bitmap.blurBackground(percent: Int): Bitmap {
    val scale = minOf(1f, 680f / maxOf(width, height))
    val thumbnail = scale((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    val w = thumbnail.width
    val h = thumbnail.height
    val radius = (maxOf(w, h) * percent / 4_000f).roundToInt().coerceAtLeast(1)
    val pixels = IntArray(w * h)
    val transposed = IntArray(pixels.size)
    thumbnail.getPixels(pixels, 0, w, 0, 0, w, h)
    if (thumbnail !== this) thumbnail.recycle()

    // Two separable box passes approximate a Gaussian with work independent of the radius.
    repeat(2) {
        blurRows(pixels, transposed, w, h, radius)
        blurRows(transposed, pixels, h, w, radius)
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun blurRows(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int) {
    val samples = radius * 2 + 1
    for (y in 0 until height) {
        val row = y * width
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = source[row + offset.coerceIn(0, width - 1)]
            red += (pixel shr 16) and 255
            green += (pixel shr 8) and 255
            blue += pixel and 255
        }
        for (x in 0 until width) {
            target[x * height + y] =
                (255 shl 24) or ((red / samples) shl 16) or ((green / samples) shl 8) or (blue / samples)
            val outgoing = source[row + (x - radius).coerceAtLeast(0)]
            val incoming = source[row + (x + radius + 1).coerceAtMost(width - 1)]
            red += ((incoming shr 16) and 255) - ((outgoing shr 16) and 255)
            green += ((incoming shr 8) and 255) - ((outgoing shr 8) and 255)
            blue += (incoming and 255) - (outgoing and 255)
        }
    }
}
