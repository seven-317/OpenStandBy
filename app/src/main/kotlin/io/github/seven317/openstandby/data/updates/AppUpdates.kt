package io.github.seven317.openstandby.data.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.JsonParseException
import io.github.seven317.openstandby.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AppUpdate(val tag: String, val version: ReleaseVersion) {
    fun releaseIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://github.com/$GITHUB_REPOSITORY/releases/tag").buildUpon().appendPath(tag).build(),
    )
}

internal class AppUpdates(context: Context, private val api: GitHubReleasesApi) {
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    val notifications = UpdateNotifications(context)
    private val installedVersion = ReleaseVersion.parse(BuildConfig.VERSION_NAME)
    private val updates = MutableStateFlow(newerRelease(preferences.getString(AVAILABLE_TAG, null)))
    val available = updates.asStateFlow()
    private val mutex = Mutex()

    var notificationPermissionRequested: Boolean
        get() = preferences.getBoolean(PERMISSION_REQUESTED, false)
        set(value) { preferences.edit().putBoolean(PERMISSION_REQUESTED, value).apply() }

    // Returns whether WorkManager should retry a transient failure.
    suspend fun check(): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val age = now - preferences.getLong(CHECKED_AT, 0)
        if (age in 0 until TimeUnit.MINUTES.toMillis(30)) {
            updateNotification(updates.value)
            return false
        }

        val response = try {
            api.latestRelease()
        } catch (_: IOException) {
            return true
        } catch (_: JsonParseException) {
            return false
        }

        val release = when {
            response.isSuccessful -> newerRelease(response.body()?.tag)
            response.code() == 404 -> null
            else -> {
                response.errorBody()?.close()
                return response.code() in 500..599
            }
        }
        response.errorBody()?.close()
        preferences.edit().putLong(CHECKED_AT, now).putString(AVAILABLE_TAG, release?.tag).apply()
        updateNotification(release)
        updates.value = release
        false
    }

    fun shouldAlert(update: AppUpdate): Boolean {
        val alerted = ReleaseVersion.parse(preferences.getString(ALERTED_VERSION, null))
        return alerted == null || update.version > alerted
    }

    fun markAlerted(update: AppUpdate) {
        preferences.edit().putString(ALERTED_VERSION, update.version.toString()).apply()
    }

    private fun updateNotification(update: AppUpdate?) {
        if (update == null) {
            notifications.cancel()
        } else if (shouldAlert(update) && notifications.post(update)) {
            markAlerted(update)
        }
    }

    private fun newerRelease(tag: String?): AppUpdate? {
        if (tag == null) return null
        val installed = installedVersion ?: return null
        val version = ReleaseVersion.parse(tag) ?: return null
        return if (version > installed) AppUpdate(tag, version) else null
    }
}

private const val AVAILABLE_TAG = "available_tag"
private const val CHECKED_AT = "checked_at"
private const val ALERTED_VERSION = "alerted_version"
private const val PERMISSION_REQUESTED = "notification_permission_requested"
