package com.nexusstream.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nexusstream.app.ui.NexusApp

class MainActivity : ComponentActivity() {

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

        setContent {
            NexusApp()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPictureInPictureIfSupported()
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
