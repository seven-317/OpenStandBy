package io.github.seven317.openstandby.ui.standby

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import io.github.seven317.openstandby.R
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal enum class StandByBackground(val title: Int, start: Color, end: Color) {
    Midnight(R.string.background_midnight, Color(0xFF25374E), Color(0xFF111621)),
    Forest(R.string.background_forest, Color(0xFF234C42), Color(0xFF102622)),
    Dusk(R.string.background_dusk, Color(0xFF633C45), Color(0xFF301D29));

    val brush = Brush.verticalGradient(listOf(start, end))
}

internal enum class BackgroundTarget(val title: Int, val key: String) {
    Clock(R.string.clock_module, "clock"),
    Speed(R.string.speed_module, "speed"),
}

internal data class ModuleBackground(
    val preset: StandByBackground,
    val photo: File?,
    val blurPercent: Int = 0,
    val photoVersion: Long = 0,
    val importing: Boolean = false,
)

internal class StandByBackgroundStore(private val context: Context, private val preferences: SharedPreferences) {
    private val backgrounds = MutableStateFlow(BackgroundTarget.entries.associateWith { target ->
        val previousPreset = preferences.getString("standby_background", null)
        val savedPreset = preferences.getString("${target.key}_background_preset", previousPreset)
        val preset = StandByBackground.entries.firstOrNull { it.name == savedPreset } ?: StandByBackground.Midnight
        val photo = photoFile(target).takeIf { preferences.getBoolean("${target.key}_background_photo", false) && it.isFile }
        val blur = preferences.getInt("${target.key}_background_blur", 0).coerceIn(0, 100)
        ModuleBackground(preset, photo, blurPercent = blur)
    })
    val state = backgrounds.asStateFlow()

    fun selectPreset(target: BackgroundTarget, preset: StandByBackground) {
        if (state.value.getValue(target).importing) return
        preferences.edit()
            .putString("${target.key}_background_preset", preset.name)
            .putBoolean("${target.key}_background_photo", false)
            .apply()
        update(target) { it.copy(preset = preset, photo = null) }
        AtomicFile(photoFile(target)).delete()
    }

    fun setBlur(target: BackgroundTarget, percent: Int) {
        val blur = percent.coerceIn(0, 100)
        if (state.value.getValue(target).blurPercent == blur) return
        preferences.edit().putInt("${target.key}_background_blur", blur).apply()
        update(target) { it.copy(blurPercent = blur) }
    }

    suspend fun savePhoto(target: BackgroundTarget, source: Bitmap, crop: IntRect): Boolean {
        if (state.value.getValue(target).importing) return false
        update(target) { it.copy(importing = true) }
        val destination = photoFile(target)
        return try {
            withContext(Dispatchers.IO) {
                val bitmap = Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height)
                val pending = File.createTempFile(destination.nameWithoutExtension, ".jpg", destination.parentFile)
                try {
                    pending.outputStream().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw IOException("Could not encode photo")
                        output.fd.sync()
                    }
                    Files.move(pending.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                } finally {
                    pending.delete()
                    if (bitmap !== source) bitmap.recycle()
                }
            }
            preferences.edit().putBoolean("${target.key}_background_photo", true).apply()
            update(target) { it.copy(photo = destination, photoVersion = it.photoVersion + 1) }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } finally {
            update(target) { it.copy(importing = false) }
        }
    }

    private fun photoFile(target: BackgroundTarget) = File(context.noBackupFilesDir, "${target.key}-background.jpg")

    private fun update(target: BackgroundTarget, transform: (ModuleBackground) -> ModuleBackground) {
        backgrounds.update { current -> current + (target to transform(current.getValue(target))) }
    }
}

@Composable
internal fun BackgroundImage(background: ModuleBackground, modifier: Modifier, preview: Boolean = false) {
    val photo = background.photo
    val image by produceState<ImageBitmap?>(null, photo, background.photoVersion, preview) {
        value = if (photo == null) null else withContext(Dispatchers.IO) {
            try {
                decodePhoto(ImageDecoder.createSource(photo), if (preview) 720 else 2_400).asImageBitmap()
            } catch (_: IOException) {
                null
            }
        }
    }
    val hardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blur = background.blurPercent
    val legacyImage by produceState<ImageBitmap?>(null, image, blur, hardwareBlur) {
        value = if (!hardwareBlur && blur > 0) {
            delay(60)
            withContext(Dispatchers.Default) { image?.asAndroidBitmap()?.blurBackground(blur)?.asImageBitmap() }
        } else null
    }
    Box(modifier.background(background.preset.brush)) {
        image?.takeIf { photo != null }?.let { bitmap ->
            val blurModifier = if (hardwareBlur && blur > 0) {
                Modifier.blur((blur * if (preview) 0.18f else 0.44f).dp)
            } else Modifier
            Image(
                if (!hardwareBlur && blur > 0) legacyImage ?: bitmap else bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().then(blurModifier),
                contentScale = ContentScale.Crop,
            )
            if (!preview) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color(0x20101418), Color(0x92101418), Color(0x36101418))),
                    ),
                )
            }
        }
    }
}

internal suspend fun loadBackgroundPhoto(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        decodePhoto(ImageDecoder.createSource(context.contentResolver, uri), 2_400)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun decodePhoto(source: ImageDecoder.Source, maxEdge: Int): Bitmap =
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val scale = maxEdge.toFloat() / maxOf(info.size.width, info.size.height)
        if (scale < 1f) {
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
