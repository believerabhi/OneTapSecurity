package com.onetap.security

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.onetap.security.screencapture.ScreenCaptureManager
import com.onetap.security.textprocessing.ImageProcessor
import com.onetap.security.textprocessing.RiskSeverity
import com.onetap.security.textprocessing.ScreenAnalysisResult
import com.onetap.security.textprocessing.ScreenAnalyzer
import com.onetap.security.utils.DialogService
import com.onetap.security.utils.SecurityPreferences
import kotlinx.coroutines.*

class ScreenCaptureService : Service(), CoroutineScope by MainScope(),
    ScreenCaptureManager.ScreenshotCallback {

    private val TAG = "ScreenCaptureService"
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var screenAnalyzer: ScreenAnalyzer
    private lateinit var imageProcessor: ImageProcessor

    private var isAnalyzing = false
    private var isProcessingComplete = false
    private var hasShownNotification = false

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        if (::screenCaptureManager.isInitialized) screenCaptureManager.release()
        if (::screenAnalyzer.isInitialized) screenAnalyzer.close()

        sendLocalBroadcast(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
        cancel()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")

        securityPreferences = SecurityPreferences.getInstance(this)
        imageProcessor = ImageProcessor()
        screenAnalyzer = ScreenAnalyzer(this)

        screenCaptureManager = ScreenCaptureManager(this, handler, this).apply {
            initialize()
            setScreenshotCallback(this@ScreenCaptureService)
        }

        createNotificationChannel()
        startForeground(1, createNotification())
    }

    private fun createNotificationChannel() {
        val channelId = "screencapture"
        val channelName = getString(R.string.notification_channel_name)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun createNotification(): Notification {
        val channelId = "screencapture"
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

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

        // Validate intent and projection data
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val resultData = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == null || resultData == null) {
            handleProjectionError("Missing or invalid projection data. Try restarting the app.")
            return START_NOT_STICKY
        }

        return try {
            isAnalyzing = false
            isProcessingComplete = false
            hasShownNotification = false

            sendLocalBroadcast(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_STARTED)
            screenCaptureManager.startScreenCapture(resultCode, resultData)
            START_STICKY
        } catch (e: Exception) {
            handleProjectionError("Error starting screen capture: ${e.message}", e)
            START_NOT_STICKY
        }
    }

    private fun createProcessingNotification(): Notification {
        return Notification.Builder(this, "screencapture")
            .setContentTitle(getString(R.string.notification_processing))
            .setContentText(getString(R.string.notification_analyzing))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun sendLocalBroadcast(action: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(action))
    }

    private fun handleProjectionError(message: String, e: Exception? = null) {
        Log.e(TAG, message, e)
        ProjectionStore.reset()
        sendLocalBroadcast(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }


    /**
     * Callback when screenshot is captured - direct bitmap processing
     */
    override fun onScreenshotCaptured(bitmap: Bitmap) {
        if (isAnalyzing || !securityPreferences.isSecurityEnabled() || isProcessingComplete) return

        isAnalyzing = true

        launch(Dispatchers.Main) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(2, createProcessingNotification())

            var result: ScreenAnalysisResult? = null
            var analysisError: Exception? = null

            try {
                withTimeout(15000) {
                    result = withContext(Dispatchers.Default) {
                        screenAnalyzer.analyzeBitmap(bitmap)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                analysisError = Exception("Analysis timed out")
                Log.e(TAG, analysisError.message.orEmpty())
            } catch (e: Exception) {
                analysisError = e
                Log.e(TAG, "Error in analysis: ${e.message}")
            }

            isProcessingComplete = true

            if (result != null) {
                handleScreenAnalysisResult(result!!)
            } else {
                showResultNotification(
                    "Analysis Error",
                    "Unable to analyze: ${analysisError?.message ?: "Unknown error"}"
                )
            }

            sendLocalBroadcast(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
            bitmap.recycleSafely()
        }
    }


    /**
     * Handle error in screenshot capture
     */
    override fun onError(errorMessage: String) {
        Log.e(TAG, "Screenshot capture error: $errorMessage")
        Toast.makeText(this, "Screenshot capture failed: $errorMessage", Toast.LENGTH_SHORT).show()

        if (!hasShownNotification) {
            showResultNotification("Screenshot Error", "Failed to capture: $errorMessage")
        }

        sendLocalBroadcast(FloatingWidgetService.ACTION_SCREENSHOT_PROCESSING_FINISHED)
        stopSelf()
    }

    /**
     * Handle the results of screen analysis
     */
    private fun handleScreenAnalysisResult(result: ScreenAnalysisResult) {
        launch(Dispatchers.Main) {
            Log.d(TAG, "Screen analysis completed: ${result.success}")

            if (result.success && result.securityAnalysis != null && result.extractedText != null) {
                try {
                    val risks = result.securityAnalysis.securityRisks
                    val sensitive = result.securityAnalysis.sensitiveInformation

                    // Calculate maximum severity from existing risks
                    val maxSeverity = risks.maxOfOrNull { it.severity } ?: RiskSeverity.LOW
                    Log.d(TAG, "maxSeverity completed: $maxSeverity}")

                    val title = when {
                        maxSeverity == RiskSeverity.CRITICAL ->
                            "CRITICAL SECURITY THREAT DETECTED"

                        maxSeverity == RiskSeverity.HIGH ->
                            "HIGH SECURITY RISK IDENTIFIED"

                        maxSeverity == RiskSeverity.MEDIUM ->
                            "SECURITY CONCERN DETECTED"

                        risks.isNotEmpty() ->
                            "Potential Security Issue"

                        else ->
                            "Screen Analysis Complete"
                    }


                    val riskText =
                        if (risks.isNotEmpty()) "${risks.size} security risks found" else "No risks"
                    val sensitiveText =
                        if (sensitive.isNotEmpty()) "${sensitive.size} sensitive items" else "No sensitive info"

                    // Include highest confidence risk in notification if available
                    val highestConfidenceRisk = risks.maxByOrNull { it.confidence }
                    val detailText =
                        if (highestConfidenceRisk != null && highestConfidenceRisk.confidence > 0.7f) {
                            "\n\nHighest risk: ${highestConfidenceRisk.description}"
                        } else ""

                    showResultNotification(
                        title,
                        "$riskText\n$sensitiveText$detailText",
                        maxSeverity
                    )
                } catch (e: Exception) {
                    fallbackResultHandling(result)
                }
            } else {
                fallbackResultHandling(result)
            }

            handler.postDelayed({ stopSelf() }, 5000)
        }
    }

    /**
     * Fallback handling for when AI enhancement fails
     */
    private fun fallbackResultHandling(result: ScreenAnalysisResult) {
        val risks = result.securityAnalysis?.securityRisks.orEmpty()
        val sensitive = result.securityAnalysis?.sensitiveInformation.orEmpty()
        val text = result.extractedText?.fullText

        // Calculate average confidence of risks if available
        val avgConfidence = if (risks.isNotEmpty()) {
            risks.map { it.confidence }.average().toFloat()
        } else 0f

        // Format with confidence percentage if available
        val confidenceText = if (avgConfidence > 0) {
            " (${(avgConfidence * 100).toInt()}% confidence)"
        } else ""

        val riskText =
            if (risks.isNotEmpty()) "${risks.size} security risks$confidenceText" else "No risks"
        val sensitiveText =
            if (sensitive.isNotEmpty()) "${sensitive.size} sensitive items" else "No sensitive info"

        val title =
            if (risks.isNotEmpty() || sensitive.isNotEmpty()) "Security Alert" else "Screen Analyzed"
        val content = "$riskText\n$sensitiveText"

        showResultNotification(title, content)
    }

    /**
     * Show a dialog with the analysis results
     * @param title The dialog title
     * @param content The dialog content
     */
    private fun showResultNotification(
        title: String,
        content: String,
        maxSeverity: RiskSeverity = RiskSeverity.LOW
    ) {
        Log.d(TAG, "Screen analysis completed: $hasShownNotification")
        if (hasShownNotification) return

        hasShownNotification = true

        // Instead of showing a notification, we use DialogService to show a dialog
        // This will appear on top of other apps with a title and content
        DialogService.showSecurityAlertDialog(
            context = this,
            title = title,
            content = content,
            maxSeverity = maxSeverity.name
        )
    }
}