package com.onetap.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.onetap.security.ui.SecurityScreen
import com.onetap.security.ui.SecurityViewModel
import com.onetap.security.ui.theme.OneTapSecurityTheme
import com.onetap.security.utils.SecurityPreferences
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    }

    private val TAG = "MainActivity"

    // ViewModel
    private val viewModel: SecurityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

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
                            if (!enabled) {
                                stopFloatingWidgetService()
                            }
                        },
                        onStartProtectionClick = {
                            if (Settings.canDrawOverlays(this)) {
                                startProjectionPermissionService()
                            } else {
                                requestOverlayPermission()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun startProjectionPermissionService() {
        startActivity(Intent(this, ProjectionPermissionActivity::class.java))
    }

    private fun stopFloatingWidgetService() {
        val serviceIntent = Intent(this, FloatingWidgetService::class.java)
        if (isServiceRunning(FloatingWidgetService::class.java)) {
            stopService(serviceIntent)
            Toast.makeText(this, "Floating widget disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startProjectionPermissionService()
            } else {
                Toast.makeText(
                    this,
                    "Overlay permission denied. The floating widget cannot be shown.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}