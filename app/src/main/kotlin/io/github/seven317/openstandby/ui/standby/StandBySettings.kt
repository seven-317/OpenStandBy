package io.github.seven317.openstandby.ui.standby

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.seven317.openstandby.AppLanguage
import io.github.seven317.openstandby.R
import io.github.seven317.openstandby.ui.player.TraditionalLyricsSwitch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
internal fun StandByBackgroundSettings(
    target: BackgroundTarget,
    background: ModuleBackground,
    onPreset: (BackgroundTarget, StandByBackground) -> Unit,
    onBlur: (BackgroundTarget, Int) -> Unit,
    onSavePhoto: suspend (BackgroundTarget, Bitmap, IntRect) -> Boolean,
    onOpenGlobalSettings: () -> Unit,
    onBack: () -> Unit,
    displaySettings: @Composable () -> Unit,
) {
    var cropUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }

    val uri = cropUri
    if (uri != null) {
        val cancel = { if (!saving) cropUri = null }
        BackHandler(onBack = cancel)
        PhotoCropEditor(uri, saving, onCancel = cancel) { bitmap, crop ->
            if (!saving) {
                saving = true
                scope.launch {
                    val saved = try {
                        onSavePhoto(target, bitmap, crop)
                    } finally {
                        saving = false
                    }
                    if (saved) cancel()
                    val message = if (saved) R.string.background_photo_saved else R.string.background_photo_failed
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return
    }

    SettingsPage(
        title = stringResource(R.string.module_settings_title, stringResource(target.title)),
        onBack = onBack,
        onOpenGlobalSettings = onOpenGlobalSettings,
    ) {
        displaySettings()
        BackgroundSettings(
            target = target,
            background = background,
            onPreset = { onPreset(target, it) },
            onBlur = { onBlur(target, it) },
            onPhoto = { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        )
    }
}

@Composable
internal fun ClockFaceSettings(face: ClockFace, background: ModuleBackground, onSelect: (ClockFace) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF20252D),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.clock_face_section), style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in ClockFace.entries) {
                    FilterChip(
                        selected = face == option,
                        onClick = { onSelect(option) },
                        label = { Text(stringResource(option.title)) },
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(10.dp))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                BackgroundImage(background, Modifier.matchParentSize(), preview = true)
                ClockFaceArtwork(face, rememberClockTime(), Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
internal fun PlayerSettings(
    trackTitle: String?,
    traditionalLyrics: Boolean,
    onTraditionalLyricsChange: (Boolean) -> Unit,
    onImportLyrics: (() -> Unit)?,
    onOpenGlobalSettings: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(stringResource(R.string.player_settings_title), onBack, onOpenGlobalSettings = onOpenGlobalSettings) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF20252D),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.player_lyrics_settings), style = MaterialTheme.typography.titleLarge)
                TraditionalLyricsSwitch(traditionalLyrics, onTraditionalLyricsChange)
                HorizontalDivider()
                val detail = if (onImportLyrics == null) stringResource(R.string.lyrics_import_no_track) else trackTitle
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(onClick = { onImportLyrics?.invoke() }, enabled = onImportLyrics != null) {
                    Text(stringResource(R.string.lyrics_import_file))
                }
            }
        }
    }
}

@Composable
internal fun GlobalSettings(language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.global_settings_title), onBack, backLabel = R.string.back_to_module_settings) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF20252D),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(R.string.interface_language), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.selectableGroup()) {
                    for (option in AppLanguage.entries) {
                        val selected = language == option
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onLanguageChange(option) },
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    @StringRes backLabel: Int = R.string.close_settings,
    onOpenGlobalSettings: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val description = stringResource(backLabel)
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = description }) {
                    StandByControlIcon(settings = false)
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            if (onOpenGlobalSettings != null) {
                OutlinedButton(onClick = onOpenGlobalSettings) {
                    Text(stringResource(R.string.global_settings_title))
                }
            }
            content()
        }
    }
}

@Composable
private fun BackgroundSettings(
    target: BackgroundTarget,
    background: ModuleBackground,
    onPreset: (StandByBackground) -> Unit,
    onBlur: (Int) -> Unit,
    onPhoto: () -> Unit,
) {
    val title = stringResource(target.title)
    val previewLabel = stringResource(R.string.background_preview, title)
    val pickLabel = stringResource(R.string.background_photo_for, title)
    var blur by remember(background.blurPercent) { mutableFloatStateOf(background.blurPercent.toFloat()) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF20252D),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.background_settings_section), style = MaterialTheme.typography.titleLarge)
            BackgroundImage(
                background.copy(blurPercent = blur.roundToInt()),
                Modifier.fillMaxWidth().aspectRatio(3.65f).clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = previewLabel },
                preview = true,
            )
            Button(
                onClick = onPhoto,
                enabled = !background.importing,
                modifier = Modifier.semantics { contentDescription = pickLabel },
            ) {
                val label = when {
                    background.importing -> R.string.background_loading_photo
                    background.photo != null -> R.string.background_change_photo
                    else -> R.string.background_choose_photo
                }
                Text(stringResource(label))
            }
            if (background.photo != null) {
                val label = stringResource(R.string.background_blur_for, title)
                Text(stringResource(R.string.background_blur, blur.roundToInt()), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = blur,
                    onValueChange = { blur = it },
                    onValueChangeFinished = { onBlur(blur.roundToInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                    enabled = !background.importing,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (preset in StandByBackground.entries) {
                    FilterChip(
                        selected = background.photo == null && background.preset == preset,
                        enabled = !background.importing,
                        onClick = { onPreset(preset) },
                        label = { Text(stringResource(preset.title)) },
                        leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(preset.brush)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun StandByControlIcon(settings: Boolean) {
    val tint = MaterialTheme.colorScheme.onSurface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            if (settings) {
                val center = Offset(12f, 12f)
                val teeth = Path().apply {
                    for (step in 0 until 32) {
                        val radius = if (step % 4 in 1..2) 10f else 8f
                        val angle = (step - 1.5) * PI / 16
                        val x = center.x + (radius * cos(angle)).toFloat()
                        val y = center.y + (radius * sin(angle)).toFloat()
                        if (step == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(teeth, tint, style = Stroke(1.6f))
                drawCircle(tint, 3f, center, style = Stroke(1.6f))
            } else {
                scale(if (rtl) -1f else 1f, 1f, pivot = Offset(12f, 12f)) {
                    val arrow = Path().apply {
                        moveTo(11f, 5f)
                        lineTo(4f, 12f)
                        lineTo(11f, 19f)
                        moveTo(4f, 12f)
                        lineTo(20f, 12f)
                    }
                    drawPath(arrow, tint, style = Stroke(1.8f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
