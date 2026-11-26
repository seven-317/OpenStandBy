package io.github.seven317.openstandby.data.media

import android.service.notification.NotificationListenerService
import io.github.seven317.openstandby.OpenStandByApplication

class MediaNotificationListenerService : NotificationListenerService() {
    private val repository: MediaSessionRepository
        get() = (application as OpenStandByApplication).container.mediaSessionRepository

    override fun onListenerConnected() {
        super.onListenerConnected()
        repository.onNotificationListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        repository.onNotificationListenerDisconnected(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        repository.onNotificationListenerDisconnected(this)
        super.onDestroy()
    }
}
