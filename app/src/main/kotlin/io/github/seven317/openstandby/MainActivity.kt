package io.github.seven317.openstandby

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seven317.openstandby.data.updates.AppUpdate
import io.github.seven317.openstandby.data.updates.UpdateCheckWorker
import io.github.seven317.openstandby.ui.player.LyricsImportResult
import io.github.seven317.openstandby.ui.player.PlayerViewModel
import io.github.seven317.openstandby.ui.standby.ClockFace
import io.github.seven317.openstandby.ui.standby.StandByBackgroundStore
import io.github.seven317.openstandby.ui.standby.StandByPager
import io.github.seven317.openstandby.ui.standby.UpdateSettings
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val appUpdates by lazy {
        (application as OpenStandByApplication).container.appUpdates
    }
    private val mediaRepository by lazy {
        (application as OpenStandByApplication).container.mediaSessionRepository
    }
    private val backgroundStore by lazy {
        StandByBackgroundStore(applicationContext, getPreferences(MODE_PRIVATE))
    }
    private var updateNotificationsEnabled by mutableStateOf(false)
    private var requestingUpdatePermission by mutableStateOf(false)
    private val updatePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestingUpdatePermission = false
        refreshUpdates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateNotificationsEnabled = appUpdates.notifications.isEnabled()
        UpdateCheckWorker.schedule(applicationContext)
        val preferences = getPreferences(MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = PlayerColors) {
                var appLanguage by remember { mutableStateOf(AppLanguage.fromLocales(AppCompatDelegate.getApplicationLocales())) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    appLanguage = AppLanguage.fromLocales(AppCompatDelegate.getApplicationLocales())
                }
                var clockFace by remember {
                    val savedFace = preferences.getString(CLOCK_FACE_KEY, null)
                    mutableStateOf(ClockFace.entries.firstOrNull { it.name == savedFace } ?: ClockFace.Digital)
                }
                val player: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)
                val hasNotificationAccess by mediaRepository.hasNotificationAccess.collectAsStateWithLifecycle()
                val mediaState by player.mediaState.collectAsStateWithLifecycle()
                val lyricsMode by player.lyricsMode.collectAsStateWithLifecycle()
                val lyrics by player.lyrics.collectAsStateWithLifecycle()
                val traditionalLyrics by player.traditionalLyrics.collectAsStateWithLifecycle()
                val availableUpdate by appUpdates.available.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                var importKey by rememberSaveable { mutableStateOf<String?>(null) }
                val openLyrics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    val key = importKey
                    importKey = null
                    if (uri != null && key != null) scope.launch {
                        val message = when (player.importLyrics(key, uri)) {
                            LyricsImportResult.Imported -> R.string.lyrics_imported
                            LyricsImportResult.TrackChanged -> R.string.lyrics_track_changed
                            LyricsImportResult.InvalidFile -> R.string.lyrics_invalid_file
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                StandByPager(
                    hasNotificationAccess = hasNotificationAccess,
                    mediaState = mediaState,
                    lyricsMode = lyricsMode,
                    lyrics = lyrics,
                    traditionalLyrics = traditionalLyrics,
                    onTraditionalLyricsChange = player::setTraditionalLyrics,
                    backgroundStore = backgroundStore,
                    appLanguage = appLanguage,
                    onAppLanguageChange = { language ->
                        if (appLanguage != language) {
                            appLanguage = language
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
                        }
                    },
                    updateSettings = {
                        UpdateSettings(
                            availableUpdate = availableUpdate,
                            notificationsEnabled = updateNotificationsEnabled,
                            onOpenRelease = { openRelease(it) },
                            onEnableNotifications = ::enableUpdateNotifications,
                        )
                    },
                    clockFace = clockFace,
                    onClockFaceChange = {
                        clockFace = it
                        preferences.edit().putString(CLOCK_FACE_KEY, it.name).apply()
                    },
                    onOpenNotificationSettings = ::openNotificationAccessSettings,
                    onReconnectMedia = mediaRepository::reconnect,
                    onPlaybackCommand = player::dispatch,
                    onSeek = player::seekTo,
                    onToggleLyrics = player::toggleLyrics,
                    onRetryLyrics = player::retryLyrics,
                    onImportLyrics = { trackId ->
                        val key = player.lyricsImportKey(trackId)
                        if (key != null) {
                            importKey = key
                            openLyrics.launch(arrayOf(
                                "text/*", "application/octet-stream", "application/lrc", "application/x-lrc",
                                "application/yrc", "application/x-yrc",
                            ))
                        }
                    },
                    onRemoveLyrics = { trackId ->
                        scope.launch {
                            if (!player.removeLocalLyrics(trackId)) {
                                Toast.makeText(this@MainActivity, R.string.lyrics_remove_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                var dismissedUpdate by rememberSaveable { mutableStateOf<String?>(null) }
                val update = availableUpdate
                if (!updateNotificationsEnabled && !requestingUpdatePermission && update != null &&
                    dismissedUpdate != update.tag && appUpdates.shouldAlert(update)
                ) {
                    val dismiss = {
                        appUpdates.markAlerted(update)
                        dismissedUpdate = update.tag
                    }
                    AlertDialog(
                        onDismissRequest = dismiss,
                        title = { Text(stringResource(R.string.update_available_title)) },
                        text = { Text(stringResource(R.string.update_available_message, update.tag)) },
                        confirmButton = {
                            TextButton(onClick = { if (openRelease(update)) dismiss() }) {
                                Text(stringResource(R.string.update_view_release))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUpdates()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !appUpdates.notificationPermissionRequested &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            enableUpdateNotifications()
        }
        mediaRepository.setForeground(true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onPause() {
        mediaRepository.setForeground(false)
        super.onPause()
    }

    private fun refreshUpdates() {
        updateNotificationsEnabled = appUpdates.notifications.isEnabled()
        lifecycleScope.launch { appUpdates.check() }
    }

    private fun enableUpdateNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!appUpdates.notificationPermissionRequested || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            appUpdates.notificationPermissionRequested = true
            requestingUpdatePermission = true
            updatePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!tryOpenActivity(appUpdates.notifications.settingsIntent())) {
            Toast.makeText(this, R.string.update_notification_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openRelease(update: AppUpdate): Boolean {
        val opened = tryOpenActivity(update.releaseIntent())
        if (!opened) Toast.makeText(this, R.string.update_release_unavailable, Toast.LENGTH_SHORT).show()
        return opened
    }

    private fun openNotificationAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, mediaRepository.listenerComponent.flattenToString())
            if (tryOpenActivity(detail)) return
        }

        if (!tryOpenActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            Toast.makeText(this, R.string.notification_access_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryOpenActivity(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private const val CLOCK_FACE_KEY = "clock_face"

private val PlayerColors = darkColorScheme(
    primary = Color(0xFFF9F4E9),
    onPrimary = Color(0xFF191919),
    background = Color(0xFF101012),
    onBackground = Color(0xFFF9F4E9),
    surface = Color(0xFF101012),
    onSurface = Color(0xFFF9F4E9),
    onSurfaceVariant = Color(0xFFBEBCB8),
    secondaryContainer = Color(0xFF29292E),
    onSecondaryContainer = Color(0xFFF9F4E9),
)
