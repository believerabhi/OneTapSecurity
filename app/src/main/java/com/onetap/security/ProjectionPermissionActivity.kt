package com.onetap.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity

class ProjectionPermissionActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_CODE = 1001
    }

    private val TAG = "ProjectionPermission"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ProjectionPermissionActivity onCreate called")
        
        // Check if we already have valid permission
        if (ProjectionStore.hasValidPermission()) {
            Log.d(TAG, "Already have projection permission, starting floating widget")
            
            // Start the floating widget service with existing permission
            val startServiceIntent = Intent(this, FloatingWidgetService::class.java)
            startForegroundService(startServiceIntent)
            
            // Finish the activity immediately
            finish()
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        try {
            val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34)
                projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                // For Android 13 and below
                projectionManager.createScreenCaptureIntent()
            }

            startActivityForResult(captureIntent, REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting projection permission: ${e.message}")
            Toast.makeText(
                this,
                "Error requesting screen capture permission: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Log.d(TAG, "Projection permission granted with resultCode: $resultCode")

            try {
                // Store the permission for later - Create a deep copy to prevent losing the data
                ProjectionStore.resultCode = resultCode
                
                // Since we can't directly clone an Intent, we need to create a new one with the same data
                val dataClone = Intent(data) 
                ProjectionStore.resultData = dataClone
                
                Log.d(TAG, "Stored in ProjectionStore: resultCode=${ProjectionStore.resultCode}, data=${ProjectionStore.resultData != null}")

                // Start only FloatingWidgetService (NOT screen capture)
                val startServiceIntent = Intent(this, FloatingWidgetService::class.java)
                startForegroundService(startServiceIntent)
                
                Toast.makeText(
                    this, 
                    "Tap the floating widget to take a screenshot", 
                    Toast.LENGTH_LONG
                ).show()
                
                // Go to home screen to put app in background
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(homeIntent)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error storing projection data: ${e.message}")
                Toast.makeText(
                    this,
                    "Error setting up screen capture: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                ProjectionStore.reset()
            }
        } else {
            Log.d(TAG, "Screenshot permission denied or cancelled")
            ProjectionStore.reset()
            
            Toast.makeText(
                this, 
                "Screenshot permission denied. The app cannot function without this permission.", 
                Toast.LENGTH_LONG
            ).show()
        }

        finish()
    }
}


object ProjectionStore {
    @Volatile
    var resultCode: Int = -1
    
    @Volatile
    var resultData: Intent? = null
    
    fun hasValidPermission(): Boolean {
        return resultCode != -1 && resultData != null
    }
    
    fun reset() {
        resultCode = -1
        resultData = null
    }
}