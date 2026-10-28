package com.nexusstream.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.data.CrashReporter
import com.nexusstream.app.notifications.NotificationHelper
import com.nexusstream.app.ui.NexusApp
import com.nexusstream.app.ui.theme.ThemeState
import com.nexusstream.app.ui.theme.paletteFor

class MainActivity : ComponentActivity() {
    companion object {
        private var imageLoaderInstalled = false
        /** App shortcuts and widgets: open a page ("search", "subscriptions", "shorts", "music"). */
        const val ACTION_SHORTCUT = "com.nexusstream.app.SHORTCUT"
        const val EXTRA_DESTINATION = "destination"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled silently
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        // Liquid glass edge-to-edge transparent system bars
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Settings and the theme must be ready before the first frame (no dark flash in light mode).
        AppSettings.init(this)
        CrashReporter.install(this)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ThemeState.palette = paletteFor(AppSettings.themeMode.value, systemDark)

        installImageLoader()

        PipController.enter = { enterPictureInPictureIfSupported() }
        // Only the first launch: a recreated activity must not replay an old link.
        if (savedInstanceState == null) handleLaunchIntent(intent)

        setContent {
            NexusApp()
        }
    }

    /**
     * Thumbnails and artwork: a larger memory cache so scrolling back never reloads, a 512 MB disk
     * cache that is used even when YouTube's image servers send short cache headers, and a quick
     * crossfade so pictures arrive softly instead of popping in.
     */
    private fun installImageLoader() {
        if (imageLoaderInstalled) return
        imageLoaderInstalled = true
        val app = applicationContext
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(app)
                .memoryCache { coil.memory.MemoryCache.Builder(app).maxSizePercent(0.30).build() }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(app.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(512L * 1024 * 1024)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(180)
                .build()
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    /** Opens a video from a notification tap, an opened YouTube link, or shared text. */
    private fun handleLaunchIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_DESTINATION)?.let {
            LaunchRequests.requestDestination(it)
            return
        }
        val notifiedId = intent.getStringExtra(NotificationHelper.EXTRA_VIDEO_ID)
        if (notifiedId != null) {
            LaunchRequests.requestVideo(
                VideoLaunch(
                    videoId = notifiedId,
                    title = intent.getStringExtra(NotificationHelper.EXTRA_VIDEO_TITLE).orEmpty(),
                    channel = intent.getStringExtra(NotificationHelper.EXTRA_VIDEO_CHANNEL).orEmpty()
                )
            )
            return
        }
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        DeepLinks.videoIdFrom(text)?.let { LaunchRequests.requestVideo(VideoLaunch(videoId = it)) }
    }

    override fun onDestroy() {
        PipController.enter = null
        PipController.canAutoEnter = false
        // Closing the app ends the Shorts session; a rotation or theme change keeps it.
        if (isFinishing) com.nexusstream.app.ui.youtube.ShortsSession.clear()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Only a playing video on the watch page goes to PiP; music and browsing do not.
        if (PipController.canAutoEnter) enterPictureInPictureIfSupported()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.setInPip(isInPictureInPictureMode)
    }

    private fun enterPictureInPictureIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            try {
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // PiP fallback
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
