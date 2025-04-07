package com.onetap.security

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.onetap.security.ui.SecurityScreen
import com.onetap.security.ui.SecurityViewModel
import com.onetap.security.ui.theme.OneTapSecurityTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    }
    private val TAG = "MainActivity"
    private lateinit var projectionManager: MediaProjectionManager
    
    // Activity result launchers
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    
    // ViewModel
    private val viewModel: SecurityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        
        // Initialize activity result launchers
        initializeActivityResultLaunchers()
        
        // Set up Compose UI
        setContent {
            OneTapSecurityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecurityScreen(
                        securityEnabled = viewModel.securityEnabled.value,
                        onSecurityToggleChange = { enabled ->
                            viewModel.setSecurityEnabled(enabled)
                        },
                        onStartProtectionClick = {
                            if (Settings.canDrawOverlays(this)) {
                                startFloatingWidgetService()
                                finishAndRemoveTask()
                            } else {
                                requestOverlayPermission()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun startFloatingWidgetService() {
        val serviceIntent = Intent(this, FloatingWidgetService::class.java)
        startForegroundService(serviceIntent)
        Toast.makeText(this, "Floating widget enabled", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingWidgetService()
                finishAndRemoveTask()
            } else {
                Toast.makeText(
                    this,
                    "Overlay permission denied. The floating widget cannot be shown.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun initializeActivityResultLaunchers() {
        // Permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                requestMediaProjection()
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Media projection launcher
        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.d(TAG, "Screen capture permission granted")
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("code", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(serviceIntent)
                // Minimize the app but don't finish
                moveTaskToBack(true)
                Toast.makeText(this, R.string.security_started, Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Screen capture permission denied")
                Toast.makeText(this, R.string.screen_capture_required, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPermissionsAndStartService() {
        // Check for POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestMediaProjection()
            }
        } else {
            requestMediaProjection()
        }
    }
    
    private fun requestMediaProjection() {
        try {
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = projectionManager.createScreenCaptureIntent()
            mediaProjectionLauncher.launch(captureIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting media projection: ${e.message}")
            Toast.makeText(this, R.string.failed_projection, Toast.LENGTH_SHORT).show()
        }
    }
}
