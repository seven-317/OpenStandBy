package io.github.seven317.openstandby.data.location

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

internal sealed interface GpsSpeed {
    data object Acquiring : GpsSpeed
    data object Disabled : GpsSpeed
    data object Unavailable : GpsSpeed
    data object PermissionRequired : GpsSpeed
    data class Fix(val kilometersPerHour: Int, val elapsedRealtimeMs: Long) : GpsSpeed
}

@SuppressLint("MissingPermission")
internal fun LocationManager.observeGpsSpeed(): Flow<GpsSpeed> = callbackFlow {
    val provider = LocationManager.GPS_PROVIDER
    if (provider !in allProviders) {
        trySend(GpsSpeed.Unavailable)
        close()
        return@callbackFlow
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speed = location.speed
            val reading = if (location.hasSpeed() && speed.isFinite() && speed >= 0f) {
                GpsSpeed.Fix((speed * 3.6f).roundToInt(), location.elapsedRealtimeNanos / 1_000_000)
            } else GpsSpeed.Acquiring
            trySend(reading)
        }

        override fun onProviderEnabled(provider: String) { trySend(GpsSpeed.Acquiring) }
        override fun onProviderDisabled(provider: String) { trySend(GpsSpeed.Disabled) }

        @Deprecated("Required on Android 9 and 10")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    try {
        trySend(if (isProviderEnabled(provider)) GpsSpeed.Acquiring else GpsSpeed.Disabled)
        requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
    } catch (_: SecurityException) {
        trySend(GpsSpeed.PermissionRequired)
        close()
        return@callbackFlow
    } catch (_: IllegalArgumentException) {
        trySend(GpsSpeed.Unavailable)
        close()
        return@callbackFlow
    }
    awaitClose { removeUpdates(listener) }
}
