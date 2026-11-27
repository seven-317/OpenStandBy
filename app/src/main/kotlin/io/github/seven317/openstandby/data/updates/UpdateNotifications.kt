package io.github.seven317.openstandby.data.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.seven317.openstandby.R

internal class UpdateNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    private val channelDisabled: Boolean
        get() = manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

    fun isEnabled(): Boolean = manager.areNotificationsEnabled() && !channelDisabled

    fun settingsIntent(): Intent {
        createChannel()
        return if (manager.areNotificationsEnabled()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    fun post(update: AppUpdate): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!isEnabled()) return false

        createChannel()
        val localized = ContextCompat.getContextForLanguage(context)
        val openRelease = PendingIntent.getActivity(
            context, 0, update.releaseIntent(), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(localized, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(localized.getString(R.string.update_available_title))
            .setContentText(localized.getString(R.string.update_available_message, update.tag))
            .setContentIntent(openRelease)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun createChannel() {
        val localized = ContextCompat.getContextForLanguage(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, localized.getString(R.string.update_settings_title), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}

private const val CHANNEL_ID = "app_updates"
private const val NOTIFICATION_ID = 1
