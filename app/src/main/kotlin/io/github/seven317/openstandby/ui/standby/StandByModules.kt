package io.github.seven317.openstandby.ui.standby

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.seven317.openstandby.R
import io.github.seven317.openstandby.data.location.GpsSpeed
import io.github.seven317.openstandby.data.location.observeGpsSpeed
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun ClockModule(background: ModuleBackground) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val now by produceState(Date(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val time = System.currentTimeMillis()
                value = Date(time)
                delay(1_000L - Math.floorMod(time, 1_000L))
            }
        }
    }
    val time = DateFormat.getTimeFormat(context).format(now)
    val date = DateFormat.getLongDateFormat(context).format(now)
    ModuleReadout(background, stringResource(R.string.clock_module), time, date)
}

@Composable
internal fun SpeedModule(background: ModuleBackground, active: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(context.hasPreciseLocation()) }
    LifecycleResumeEffect(context) {
        hasPermission = context.hasPreciseLocation()
        onPauseOrDispose { }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = context.hasPreciseLocation()
    }
    val speed by produceState<GpsSpeed>(GpsSpeed.Acquiring, active, hasPermission, context, owner) {
        value = GpsSpeed.Acquiring
        if (active && hasPermission) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val manager = context.getSystemService(LocationManager::class.java)
                value = if (manager == null) GpsSpeed.Unavailable else GpsSpeed.Acquiring
                manager?.observeGpsSpeed()?.collectLatest { reading ->
                    if (reading is GpsSpeed.Fix) {
                        val remaining = reading.elapsedRealtimeMs + 5_000L - SystemClock.elapsedRealtime()
                        if (remaining > 0L) {
                            value = reading
                            delay(remaining)
                        }
                        value = GpsSpeed.Acquiring
                    } else {
                        value = reading
                    }
                }
            }
        }
    }

    val reading = if (hasPermission) speed else GpsSpeed.PermissionRequired
    val message = when (reading) {
        GpsSpeed.Acquiring -> stringResource(R.string.gps_acquiring)
        GpsSpeed.Disabled -> stringResource(R.string.gps_disabled)
        GpsSpeed.Unavailable -> stringResource(R.string.gps_unavailable)
        GpsSpeed.PermissionRequired -> stringResource(R.string.gps_permission_description)
        is GpsSpeed.Fix -> stringResource(R.string.gps_speed_unit)
    }
    ModuleReadout(
        background = background,
        label = stringResource(R.string.speed_module),
        value = (reading as? GpsSpeed.Fix)?.kilometersPerHour?.toString() ?: "—",
        detail = message,
    ) {
        when (reading) {
            GpsSpeed.PermissionRequired -> Button(onClick = {
                requestPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text(stringResource(R.string.gps_permission_button)) }
            GpsSpeed.Disabled -> Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text(stringResource(R.string.gps_settings_button))
            }
            else -> Unit
        }
    }
}

@Composable
private fun ModuleReadout(
    background: ModuleBackground,
    label: String,
    value: String,
    detail: String,
    action: @Composable () -> Unit = {},
) {
    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        BackgroundImage(background, Modifier.matchParentSize())
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val numberSize = (minOf((maxWidth - 48.dp).coerceAtLeast(0.dp) / 5.5f, maxHeight / 2.5f, 144.dp).value / scale).sp
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp)
                .verticalScroll(scrollState, enabled = scrollState.maxValue > 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, fontSize = numberSize, lineHeight = numberSize, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            action()
        }
    }
}

private fun Context.hasPreciseLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
