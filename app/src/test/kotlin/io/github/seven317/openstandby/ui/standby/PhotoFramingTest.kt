package io.github.seven317.openstandby.ui.standby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoFramingTest {
    @Test
    fun portraitPhotoStartsWithCenteredLandscapeCrop() {
        val crop = PhotoFraming().crop(IntSize(1_000, 1_600), 2f)

        assertEquals(IntRect(0, 550, 1_000, 1_050), crop)
    }

    @Test
    fun zoomKeepsSourceUnderPinchFocus() {
        val image = IntSize(1_600, 800)
        val framing = PhotoFraming().transform(image, 2f, newZoom = 2f, focus = Offset(0.25f, 0.75f))

        assertEquals(IntRect(200, 300, 1_000, 700), framing.crop(image, 2f))
    }

    @Test
    fun panningAndZoomAreClampedToImage() {
        val image = IntSize(1_600, 800)
        val framing = PhotoFraming().transform(image, 2f, newZoom = 10f, pan = Offset(3f, -3f))

        assertEquals(MAX_PHOTO_ZOOM, framing.zoom, 0f)
        assertEquals(IntRect(0, 600, 400, 800), framing.crop(image, 2f))
    }
}
