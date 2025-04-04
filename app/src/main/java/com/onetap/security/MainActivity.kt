package com.onetap.security

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var projectionManager: MediaProjectionManager
    private val SCREEN_CAPTURE_REQUEST_CODE = 1001
    private val PERMISSION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        
        // Check for POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
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
            startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting media projection: ${e.message}")
            Toast.makeText(this, "Failed to start media projection", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestMediaProjection()
            } else {
                Toast.makeText(this, "Notification permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
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