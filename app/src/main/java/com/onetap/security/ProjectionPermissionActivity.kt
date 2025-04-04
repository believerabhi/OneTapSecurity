package com.onetap.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProjectionPermissionActivity : AppCompatActivity() {
    private val TAG = "ProjectionPermActivity"
    private lateinit var projectionManager: MediaProjectionManager
    private val SCREEN_CAPTURE_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        
        try {
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = projectionManager.createScreenCaptureIntent()
            startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting projection: ${e.message}")
            Toast.makeText(this, "Failed to start media projection", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "Screen capture permission granted")
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("code", resultCode)
                    putExtra("data", data)
                }
                startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "Screen capture permission denied")
                Toast.makeText(this, "Screen capture permission is required", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}