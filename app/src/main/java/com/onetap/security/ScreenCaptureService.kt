package com.onetap.security

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.onetap.security.screencapture.ScreenCaptureManager
import com.onetap.security.textprocessing.ScreenAnalyzer
import com.onetap.security.textprocessing.ScreenAnalysisResult
import com.onetap.security.utils.SecurityPreferences
import kotlinx.coroutines.*
import java.io.File

class ScreenCaptureService : Service(), CoroutineScope by MainScope() {
    private val TAG = "ScreenCaptureService"
    private val handler = Handler(Looper.getMainLooper())

    // Security preferences
    private lateinit var securityPreferences: SecurityPreferences

    // Refactored components
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var screenAnalyzer: ScreenAnalyzer

    // Flag to prevent multiple simultaneous analyses
    private var isAnalyzing = false
    private var isProcessingComplete = false
    private var hasShownNotification = false

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        // Clean up resources
        if (::screenCaptureManager.isInitialized) {
            screenCaptureManager.release()
        }
        if (::screenAnalyzer.isInitialized) {
            screenAnalyzer.close()
        }

        // Ensure floating widget is updated
        val finishProcessingIntent =
            Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .sendBroadcast(finishProcessingIntent)

        // Cancel all coroutines when service is destroyed
        cancel()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")

        // Initialize security preferences
        securityPreferences = SecurityPreferences.getInstance(this)

        // Initialize screen capture manager
        screenCaptureManager = ScreenCaptureManager(this, handler)
        screenCaptureManager.initialize()

        // Set up screen capture listener
        screenCaptureManager.setScreenCaptureListener(
            onSuccess = { screenshotPath, screenshotFile ->
                // Only process the first successful screenshot
                if (!isAnalyzing) {
                    // Process the captured screenshot
                    processScreenshot(screenshotPath)
                }
            },
            onError = { errorMessage ->
                Log.e(TAG, "Screen capture error: $errorMessage")
                Toast.makeText(this, "Screen capture failed: $errorMessage", Toast.LENGTH_SHORT)
                    .show()

                // Ensure we notify the floating widget service
                val finishProcessingIntent =
                    Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(finishProcessingIntent)

                stopSelf()
            }
        )

        // Initialize screen analyzer
        screenAnalyzer = ScreenAnalyzer(this)

        // Create notification channel first
        createNotificationChannel()

        // Then start foreground service with notification
        startForeground(1, createNotification())
    }

    private fun createNotificationChannel() {
        val channelId = "screencapture"
        val channelName = getString(R.string.notification_channel_name)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(chan)
    }
    
    private fun createNotification(): Notification {
        val channelId = "screencapture"

        // Create a pending intent that goes to MainActivity instead of ProjectionPermissionActivity
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_running))
            .setContentText(getString(R.string.notification_analyzing))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")

        // Make sure we're starting with required projection data
        if (intent == null || !intent.hasExtra("resultCode") || !intent.hasExtra("data")) {
            Log.e(TAG, "Missing required projection data")
            
            // Send finish broadcast to update the widget UI
            val finishProcessingIntent = Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(finishProcessingIntent)
                
            Toast.makeText(this, "Missing projection data. Try restarting the app.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val resultData = intent.getParcelableExtra<Intent>("data") 
            if (resultData == null) {
                Log.e(TAG, "resultData is null, cannot start screen capture")
                
                // Reset the permission store as it might be corrupted
                ProjectionStore.reset()
                
                // Send finish broadcast to update the widget UI
                val finishProcessingIntent = Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(finishProcessingIntent)
                    
                Toast.makeText(this, "Media projection data is invalid. Try restarting the app.", Toast.LENGTH_LONG).show()
                stopSelf()
                return START_NOT_STICKY
            }
            
            Log.d(TAG, "Starting capture with resultCode=$resultCode and data present")

            // Start screen capture
            screenCaptureManager.startScreenCapture(resultCode, resultData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
            e.printStackTrace()
            
            // Reset the permission store as it might be corrupted
            ProjectionStore.reset()
            
            // Send finish broadcast to update the widget UI
            val finishProcessingIntent = Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(finishProcessingIntent)
                
            Toast.makeText(this, "Error starting screen capture: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }
    
    /**
     * Process the captured screenshot using the ScreenAnalyzer
     */
    private fun processScreenshot(screenshotPath: String) {
        // Check if security feature is enabled
        if (!securityPreferences.isSecurityEnabled()) {
            Log.d(TAG, "Security monitoring is disabled, ignoring screenshot")
            
            // Notify that processing has finished with error (important to update floating widget)
            val finishProcessingIntent = Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(finishProcessingIntent)
                
            stopSelf()
            return
        }

        // Set analyzing flag to prevent multiple simultaneous analyses
        isAnalyzing = true

        launch(Dispatchers.Main) {
            try {
                // Notify that processing has started (send broadcast to FloatingWidgetService)
                val startProcessingIntent = Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_STARTED)
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this@ScreenCaptureService)
                    .sendBroadcast(startProcessingIntent)
                Log.d(TAG, "Sent broadcast: Screenshot processing started")
                
                // Show processing notification
                val processingNotification = createProcessingNotification()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(2, processingNotification)
                
                var result: ScreenAnalysisResult? = null
                var analysisError: Exception? = null
                
                // First, check if a screenshot was actually captured - before even trying to analyze it
                val file = File(screenshotPath)
                if (!file.exists() || file.length().toInt() == 0) {
                    showResultNotification(
                        "Screenshot Error", 
                        "Unable to capture screenshot. The app might not have proper permissions on this device.",
                        null
                    )
                    
                    // Always notify that processing has finished, even on error
                    val finishProcessingIntent = Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
                    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this@ScreenCaptureService)
                        .sendBroadcast(finishProcessingIntent)
                    
                    stopSelf()
                    return@launch
                }

                // Clean up old screenshots
                cleanupOldScreenshots(getExternalFilesDir(Environment.DIRECTORY_PICTURES))

                // Process the screenshot in the background with timeout protection
                try {
                    withTimeout(15000) { // 15 second timeout
                        result = withContext(Dispatchers.Default) {
                            screenAnalyzer.analyzeScreenshot(screenshotPath)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    analysisError = Exception("Analysis timed out after 15 seconds")
                    Log.e(TAG, "Analysis timed out after 15 seconds")
                } catch (e: Exception) {
                    analysisError = e
                    Log.e(TAG, "Error in text analysis: ${e.message}")
                }

                if (result != null && !isProcessingComplete) {
                    isProcessingComplete = true
                    // Handle the results
                    handleScreenAnalysisResult(result!!)
                } else if (!isProcessingComplete) {
                    isProcessingComplete = true
                    // Handle the error case
                    showResultNotification(
                        "Analysis Error",
                        "Unable to analyze the screenshot: ${analysisError?.message ?: "Unknown error"}",
                        null
                    )
                }

                // Always notify that processing has finished, even on error
                val finishProcessingIntent =
                    Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this@ScreenCaptureService)
                    .sendBroadcast(finishProcessingIntent)
                Log.d(TAG, "Sent broadcast: Screenshot processing finished")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing screenshot: ${e.message}")
                e.printStackTrace()
                
                // Show error notification
                if (!hasShownNotification) {
                    hasShownNotification = true
                    showResultNotification(
                        "Error processing screenshot",
                        "Unable to analyze the screen contents.",
                        null
                    )
                }

                // Notify that processing has finished with error
                val finishProcessingIntent =
                    Intent(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this@ScreenCaptureService)
                    .sendBroadcast(finishProcessingIntent)

                // Stop the service
                stopSelf()
            }
        }
    }

    /**
     * Clean up old screenshot files to prevent filling up storage
     */
    private fun cleanupOldScreenshots(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) return

        try {
            val files = directory.listFiles() ?: return

            // Keep only the 10 most recent files
            if (files.size > 10) {
                val sortedFiles = files.filter { it.name.startsWith("screenshot_") }
                    .sortedByDescending { it.lastModified() }

                // Delete all but the 10 most recent
                sortedFiles.drop(10).forEach { file ->
                    if (file.exists() && file.isFile) {
                        val deleted = file.delete()
                        if (deleted) {
                            Log.d(TAG, "Deleted old screenshot: ${file.name}")
                        } else {
                            Log.e(TAG, "Failed to delete old screenshot: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old screenshots: ${e.message}")
        }
    }

    /**
     * Create a notification for when processing is happening
     */
    private fun createProcessingNotification(): Notification {
        val channelId = "screencapture"

        return Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_processing))
            .setContentText(getString(R.string.notification_analyzing))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    /**
     * Handle the results of screen analysis
     */
    private fun handleScreenAnalysisResult(result: ScreenAnalysisResult) {
        launch(Dispatchers.Main) {
            Log.d(TAG, "Screen analysis completed, success: ${result.success}")

            if (result.success && result.securityAnalysis != null && result.extractedText != null) {
                try {
                    val securityRisks = result.securityAnalysis.securityRisks
                    val sensitiveInfo = result.securityAnalysis.sensitiveInformation

                    // Determine notification based on verdict or risk level
                    if (securityRisks.isNotEmpty() || sensitiveInfo.isNotEmpty()) {
                        // Security risks or sensitive information detected
                        val risksText = if (securityRisks.isNotEmpty()) {
                            "${securityRisks.size} security risks found"
                        } else {
                            "No security risks found"
                        }

                        val sensitiveText = if (sensitiveInfo.isNotEmpty()) {
                            "${sensitiveInfo.size} pieces of sensitive information detected"
                        } else {
                            "No sensitive information detected"
                        }

                        showResultNotification(
                            "Security Alert",
                            "$risksText\n$sensitiveText",
                            result.extractedText.fullText
                        )

                        // Here you could save the analysis results to a database
                        // or perform other actions like sending alerts

                        Log.d(TAG, "Security risks: $securityRisks")
                        Log.d(TAG, "Sensitive info: $sensitiveInfo")
                    } else {
                        // No security risks or sensitive information detected
                        showResultNotification(
                            "Screen Analyzed",
                            "No security risks or sensitive information detected" +
                                    result.extractedText.fullText
                        )
                        Log.d(TAG, "No security risks or sensitive information detected")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing analysis results: ${e.message}")
                    fallbackResultHandling(result)
                }
            } else {
                // Analysis failed
                fallbackResultHandling(result)
            }
            
            // Give time for notification to be seen, then stop service
            handler.postDelayed({
                stopSelf()
            }, 5000)
        }
    }
    
    /**
     * Fallback handling for when AI enhancement fails
     */
    private fun fallbackResultHandling(result: ScreenAnalysisResult) {
        if (result.success && result.securityAnalysis != null) {
            val securityRisks = result.securityAnalysis.securityRisks
            val sensitiveInfo = result.securityAnalysis.sensitiveInformation
            
            if (securityRisks.isNotEmpty() || sensitiveInfo.isNotEmpty()) {
                val risksText = if (securityRisks.isNotEmpty()) {
                    "${securityRisks.size} security risks found"
                } else {
                    "No security risks found"
                }
                
                val sensitiveText = if (sensitiveInfo.isNotEmpty()) {
                    "${sensitiveInfo.size} pieces of sensitive information detected"
                } else {
                    "No sensitive information detected"
                }
                
                showResultNotification(
                    "Security Alert",
                    "$risksText\n$sensitiveText",
                    result.extractedText?.fullText
                )
            } else {
                showResultNotification(
                    "Screen Analyzed",
                    "No security risks or sensitive information detected",
                    result.extractedText?.fullText
                )
            }
        } else {
            // Analysis failed
            showResultNotification(
                "Analysis Incomplete",
                "Unable to fully analyze the screen: ${result.errorMessage ?: "Unknown error"}",
                result.extractedText?.fullText
            )
            Log.e(TAG, "Analysis failed: ${result.errorMessage}")
        }
    }
    
    /**
     * Show a notification with the analysis results
     */
    private fun showResultNotification(title: String, content: String, rawText: String? = null) {
        if (hasShownNotification) {
            Log.d(TAG, "Skipping duplicate notification")
            return
        }

        hasShownNotification = true
        val channelId = "screencapture"

        // Create intent to open activity that shows detailed results
        val resultIntent = Intent(this, ResultActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            // Pass result data to the activity
            putExtra("security_risks", title)
            putExtra("sensitive_info", content)
            rawText?.let {
                putExtra("raw_text", it)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, resultIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(3, notification)
    }
}