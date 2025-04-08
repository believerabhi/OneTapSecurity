package com.onetap.security

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity

class ProjectionPermissionActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_CODE = 1001
        var mediaProjectionData: Intent? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34)
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            // For Android 13 and below
            projectionManager.createScreenCaptureIntent()
        }

        startActivityForResult(captureIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            mediaProjectionData = data
            Log.d("ProjectionPermission", "Projection permission granted")

            // Store the permission for later
            ProjectionStore.resultCode = resultCode
            ProjectionStore.resultData = data

            // Start only FloatingWidgetService (NOT screen capture)
            val startServiceIntent = Intent(this, FloatingWidgetService::class.java)
            startForegroundService(startServiceIntent)
        } else {
            Toast.makeText(this, "Screenshot permission denied", Toast.LENGTH_LONG).show()
            Log.d("ProjectionPermission", "Screenshot permission denied")
        }

        finish()
    }
}


object ProjectionStore {
    var resultCode: Int = -1
    var resultData: Intent? = null
}