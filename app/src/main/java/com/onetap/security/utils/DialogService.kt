package com.onetap.security.utils

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onetap.security.ui.theme.OneTapSecurityTheme
import android.util.Log

/**
 * Activity to display a dialog with security analysis results
 * This activity uses a special window type to appear on top of other apps
 */
class DialogActivity : ComponentActivity() {

    private val TAG = "DialogActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get analysis results from intent
        val title = intent.getStringExtra("title") ?: "Security Alert"
        val content = intent.getStringExtra("content") ?: "No information available"
        val maxSeverity = intent.getStringExtra("max_severity") ?: "LOW"

        Log.d(TAG, "Creating dialog with severity: $maxSeverity")
        
        // Configure the window as a system overlay
        setupSystemOverlayWindow()
        
        // Set Compose content
        setContent {
            OneTapSecurityTheme {
                Surface(color = Color.Transparent) {
                    SecurityAlertDialog(
                        title = title,
                        content = content,
                        severityLevel = maxSeverity,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called - finishing activity")
        finish()
    }
    
    private fun setupSystemOverlayWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot draw overlays! Permission not granted")
            Toast.makeText(
                this,
                "Overlay permission is required to show security alerts",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }
        
        try {
            // Configure window to appear as a system overlay
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            
            // Additional window settings for proper overlay behavior
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            
            // Ensure the window is properly positioned
            val params = window.attributes.apply {
                gravity = Gravity.CENTER
                format = PixelFormat.TRANSLUCENT
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
            window.attributes = params
            
            Log.d(TAG, "System overlay window setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up overlay window: ${e.message}")
            Toast.makeText(this, "Error displaying alert", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

/**
 * Returns the appropriate color based on the severity level
 */
private fun getSeverityColor(severityLevel: String): Color {
    return when (severityLevel.uppercase()) {
        "CRITICAL" -> Color(0xFFD32F2F) // Deep Red
        "HIGH" -> Color(0xFFFF5722) // Orange-Red
        "MEDIUM" -> Color(0xFFFFA000) // Amber
        else -> Color(0xFF388E3C) // Green for LOW or default
    }
}

/**
 * Returns the header background gradient based on severity
 */
private fun getSeverityGradient(severityLevel: String): Brush {
    val baseColor = getSeverityColor(severityLevel)
    val darkerColor = baseColor.copy(alpha = 0.8f)
    
    return Brush.verticalGradient(
        colors = listOf(baseColor, darkerColor)
    )
}

/**
 * Shows a dialog with security analysis results
 */
@Composable
fun SecurityAlertDialog(
    title: String,
    content: String,
    severityLevel: String,
    onDismiss: () -> Unit
) {
    val severityColor = getSeverityColor(severityLevel)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Colored header based on severity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(getSeverityGradient(severityLevel))
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Content area
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Button with severity color
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/**
 * Helper class to show dialogs from services
 */
object DialogService {
    private val TAG = "DialogService"
    
    /**
     * Shows a security alert dialog from a service
     * This will appear on top of any screen the user is currently viewing
     */
    fun showSecurityAlertDialog(
        context: Context,
        title: String,
        content: String,
        maxSeverity: String = "LOW"
    ) {
        // Check if we have the overlay permission
        if (!Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Cannot show overlay dialog - permission not granted")
            // Fall back to a toast if we can't show the overlay
            Toast.makeText(
                context,
                "Security Alert: $title",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        try {
            val intent = Intent(context, DialogActivity::class.java).apply {
                // These flags are crucial for proper overlay behavior
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
                        
                putExtra("title", title)
                putExtra("content", content)
                putExtra("max_severity", maxSeverity)
            }
            
            Log.d(TAG, "Starting overlay dialog with severity: $maxSeverity")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing security alert dialog: ${e.message}")
            // Fall back to a toast if we fail to show the dialog
            Toast.makeText(
                context,
                "Security Alert: $title",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}