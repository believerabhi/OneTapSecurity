package com.onetap.security.screencapture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager class responsible for screen capture functionality
 */
class ScreenCaptureManager(
    private val context: Context,
    private val handler: Handler,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "ScreenCaptureManager"
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private var imageCaptured = false
    private var imageWidth = 0
    private var imageHeight = 0
    
    // Use a concrete callback instance rather than a nullable one
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
    
    private var onScreenCapturedListener: ((String, File) -> Unit)? = null
    private var onScreenCaptureErrorListener: ((String) -> Unit)? = null
    
    /**
     * Initialize the screen capture manager
     */
    fun initialize() {
        Log.d(TAG, "Initializing screen capture manager")
        projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    
    /**
     * Set the screen capture callback listener
     */
    fun setScreenCaptureListener(
        onSuccess: (String, File) -> Unit,
        onError: (String) -> Unit
    ) {
        onScreenCapturedListener = onSuccess
        onScreenCaptureErrorListener = onError
    }

    /**
     * Start screen capture with media projection data
     */
    fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "Starting screen capture")
            
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics().apply {
                wm.defaultDisplay.getRealMetrics(this)
            }
            
            imageWidth = metrics.widthPixels
            imageHeight = metrics.heightPixels
            
            imageReader = ImageReader.newInstance(imageWidth, imageHeight, 0x1, 2)
            
            // Get the media projection and register callback
            mediaProjection = projectionManager.getMediaProjection(resultCode, data).apply {
                // Register the callback
                registerCallback(mediaProjectionCallback, handler)
            }
            
            // Create virtual display
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                imageWidth,
                imageHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )
            
            // Schedule screenshot after a short delay
            scheduleScreenshot()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}")
            e.printStackTrace()
            onScreenCaptureErrorListener?.invoke("Failed to start screen capture: ${e.message}")
        }
    }
    
    /**
     * Schedule a screenshot to be taken after a short delay
     */
    private fun scheduleScreenshot(delayMs: Long = 1000) {
        // Reset capture flag
        imageCaptured = false
        
        // Schedule screenshot after delay
        handler.postDelayed({
            if (!imageCaptured) {
                captureScreenshot()
            }
        }, delayMs)
    }
    
    /**
     * Capture the current screen
     */
    private fun captureScreenshot() {
        try {
            imageCaptured = true
            Log.d(TAG, "Taking screenshot")
            
            val image = imageReader.acquireLatestImage()
            if (image != null) {
                val screenshotFile = saveImage(image)
                image.close()
                
                if (screenshotFile != null) {
                    Log.d(TAG, "Screenshot saved to: ${screenshotFile.absolutePath}")
                    onScreenCapturedListener?.invoke(screenshotFile.absolutePath, screenshotFile)
                } else {
                    Log.e(TAG, "Failed to save screenshot")
                    onScreenCaptureErrorListener?.invoke("Failed to save screenshot")
                }
            } else {
                Log.e(TAG, "Failed to acquire image")
                onScreenCaptureErrorListener?.invoke("Failed to acquire image")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screenshot: ${e.message}")
            e.printStackTrace()
            onScreenCaptureErrorListener?.invoke("Error capturing screenshot: ${e.message}")
        }
    }
    
    /**
     * Save the captured image to a file
     */
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
            
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshot.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Stop screen capture and release resources
     */
    fun stopScreenCapture() {
        Log.d(TAG, "Stopping screen capture")
        mediaProjection?.stop()
        virtualDisplay?.release()
        if (::imageReader.isInitialized) {
            imageReader.close()
        }
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        stopScreenCapture()
        onScreenCapturedListener = null
        onScreenCaptureErrorListener = null
    }
}