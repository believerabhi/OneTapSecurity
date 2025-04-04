package com.onetap.security

// ScreenCaptureService.kt

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.onetap.security.textprocessing.ScreenAnalyzer
import com.onetap.security.textprocessing.ScreenAnalysisResult
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ScreenCaptureService : Service(), CoroutineScope by MainScope() {
    private val TAG = "ScreenCaptureService"
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private var imageCaptured = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var screenAnalyzer: ScreenAnalyzer

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        mediaProjection?.stop()
        virtualDisplay?.release()
        if (::imageReader.isInitialized) {
            imageReader.close()
        }
        if (::screenAnalyzer.isInitialized) {
            screenAnalyzer.close()
        }
        // Cancel all coroutines when service is destroyed
        cancel()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Initialize screen analyzer
        screenAnalyzer = ScreenAnalyzer(this)
        
        // Create notification channel first
        createNotificationChannel()
        
        // Then start foreground service with notification
        startForeground(1, createNotification())
    }

    private fun createNotificationChannel() {
        val channelId = "screencapture"
        val channelName = "Screen Analyzer"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(chan)
    }
    
    private fun createNotification(): Notification {
        val channelId = "screencapture"

        val tapIntent = Intent(this, ProjectionPermissionActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, channelId)
            .setContentTitle("Screen Analyzer Running")
            .setContentText("Tap to capture screen")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        
        // Make sure we're starting with required projection data
        if (intent?.hasExtra("code") != true || intent.hasExtra("data") != true) {
            Log.e(TAG, "Missing required projection data")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val resultCode = intent.getIntExtra("code", -1)
            val resultData = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics().apply {
                wm.defaultDisplay.getRealMetrics(this)
            }

            imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, 0x1, 2)
            
            // Get the media projection and register callback
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData).apply {
                // Register callback (required for Android 12+ / API 31+)
                registerCallback(mediaProjectionCallback, handler)
            }
            
            // Create virtual display after callback is registered
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )

            // Schedule screenshot capture
            handler.postDelayed({
                if (!imageCaptured) {
                    imageCaptured = true
                    Log.d(TAG, "Taking screenshot")
                    val image = imageReader.acquireLatestImage()
                    if (image != null) {
                        val screenshotFile = saveImage(image)
                        image.close()
                        
                        if (screenshotFile != null) {
                            // Process the saved screenshot
                            processScreenshot(screenshotFile.absolutePath)
                        } else {
                            Log.e(TAG, "Failed to save screenshot")
                            stopSelf()
                        }
                    } else {
                        Log.e(TAG, "Failed to acquire image")
                        stopSelf()
                    }
                }
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    // MediaProjection callback - required for newer Android versions
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped")
            virtualDisplay?.release()
            if (::imageReader.isInitialized) {
                imageReader.close()
            }
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun saveImage(image: Image): File? {
        try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshot.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Log.d(TAG, "Saved screenshot to: ${file.absolutePath}")
            }
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Process the captured screenshot using the ScreenAnalyzer
     */
    private fun processScreenshot(screenshotPath: String) {
        launch(Dispatchers.Main) {
            try {
                // Show processing notification
                val processingNotification = createProcessingNotification()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(2, processingNotification)
                
                // Process the screenshot in the background
                val result = withContext(Dispatchers.Default) {
                    screenAnalyzer.analyzeScreenshot(screenshotPath)
                }
                
                // Handle the results
                handleScreenAnalysisResult(result, screenshotPath)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing screenshot: ${e.message}")
                e.printStackTrace()
                
                // Show error notification
                showResultNotification("Error processing screenshot", "Unable to analyze the screen contents.", null)
                
                // Stop the service
                stopSelf()
            }
        }
    }
    
    /**
     * Create a notification for when processing is happening
     */
    private fun createProcessingNotification(): Notification {
        val channelId = "screencapture"
        
        return Notification.Builder(this, channelId)
            .setContentTitle("Processing Screenshot")
            .setContentText("Analyzing screen contents...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Handle the results of screen analysis
     */
    private fun handleScreenAnalysisResult(result: ScreenAnalysisResult, screenshotPath: String) {
        Log.d(TAG, "Screen analysis completed, success: ${result.success}")
        
        if (result.success && result.securityAnalysis != null) {
            val securityRisks = result.securityAnalysis.securityRisks
            val sensitiveInfo = result.securityAnalysis.sensitiveInformation
            
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
                    result.extractedText?.fullText
                )
                
                // Here you could save the analysis results to a database
                // or perform other actions like sending alerts
                
                Log.d(TAG, "Security risks: $securityRisks")
                Log.d(TAG, "Sensitive info: $sensitiveInfo")
            } else {
                // No security risks or sensitive information detected
                showResultNotification(
                    "Screen Analyzed",
                    "No security risks or sensitive information detected",
                    result.extractedText?.fullText
                )
                Log.d(TAG, "No security risks or sensitive information detected")
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
        
        // Give time for notification to be seen, then stop service
        handler.postDelayed({
            stopSelf()
        }, 5000)
    }
    
    /**
     * Show a notification with the analysis results
     */
    private fun showResultNotification(title: String, content: String, rawText: String? = null) {
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