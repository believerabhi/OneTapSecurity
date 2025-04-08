package com.onetap.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import com.onetap.security.utils.SecurityPreferences

class ProjectionPermissionActivity : ComponentActivity() {
    private val TAG = "ProjectionPermActivity"
    private lateinit var projectionManager: MediaProjectionManager

    // Security preferences
    private lateinit var securityPreferences: SecurityPreferences

    // Media projection launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null && securityPreferences.isSecurityEnabled()) {
            Log.d(TAG, "Screen capture permission granted")
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("code", result.resultCode)
                putExtra("data", result.data)
            }
            startForegroundService(serviceIntent)
        } else {
            Log.d(TAG, "Screen capture permission denied")
            Toast.makeText(this, R.string.screen_capture_required, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        // Initialize security preferences
        securityPreferences = SecurityPreferences.getInstance(this)

        // Check if security is enabled
        if (!securityPreferences.isSecurityEnabled()) {
            Log.d(TAG, "Security monitoring is disabled")
            Toast.makeText(this, R.string.security_monitor_disabled, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjectionLauncher.launch(
                    projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                )
            } else {
                mediaProjectionLauncher.launch(
                    projectionManager.createScreenCaptureIntent()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting projection: ${e.message}")
            Toast.makeText(this, R.string.failed_projection, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}