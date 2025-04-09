package com.onetap.security.screencapture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import android.graphics.Canvas
import android.graphics.Color

/**
 * Manager class responsible for screen capture functionality
 */
class ScreenCaptureManager(
    private val context: Context,
    private val handler: Handler
) {
    private val TAG = "ScreenCaptureManager"
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private var imageCaptured = false
    private var imageWidth = 0
    private var imageHeight = 0
    private var retryCount = 0
    private val MAX_RETRY_COUNT = 3 // Limit retries to prevent infinite loop

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
        projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
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
            Log.d(TAG, "Starting screen capture with resultCode=$resultCode")

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics().apply {
                wm.defaultDisplay.getRealMetrics(this)
            }

            imageWidth = metrics.widthPixels
            imageHeight = metrics.heightPixels

            Log.d(TAG, "Screen dimensions: $imageWidth x $imageHeight")

            // Use RGBA_8888 format which is more compatible across devices
            imageReader = ImageReader.newInstance(imageWidth, imageHeight, PixelFormat.RGBA_8888, 2)

            // Add image available listener to capture immediately when ready
            imageReader.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        handleImageCaptured(image)
                    } else {
                        Log.e(TAG, "Null image in OnImageAvailableListener")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in image available listener: ${e.message}")
                }
            }, handler)

            // Get the media projection and register callback
            mediaProjection = projectionManager.getMediaProjection(resultCode, data).apply {
                // Register the callback
                registerCallback(mediaProjectionCallback, handler)
            }

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null after getMediaProjection")
                onScreenCaptureErrorListener?.invoke("Failed to start media projection")
                return
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

            if (virtualDisplay == null) {
                Log.e(TAG, "VirtualDisplay is null after createVirtualDisplay")
                onScreenCaptureErrorListener?.invoke("Failed to create virtual display")
                return
            }

            Log.d(TAG, "Virtual display created, scheduling screenshot")

            // Reset retry counter
            retryCount = 0

            // Schedule screenshot after a short delay
            scheduleScreenshot(500) // Reduced delay for faster capture

        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}")
            e.printStackTrace()
            onScreenCaptureErrorListener?.invoke("Failed to start screen capture: ${e.message}")
        }
    }

    /**
     * Schedule a screenshot to be taken after a short delay
     */
    private fun scheduleScreenshot(delayMs: Long = 500) {
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
            Log.d(TAG, "Taking screenshot (attempt #${retryCount + 1})")

            // Try to acquire the latest image
            val image = imageReader.acquireLatestImage()

            if (image != null) {
                handleImageCaptured(image)
            } else {
                Log.e(TAG, "Failed to acquire image from ImageReader")

                // Try a few times, but eventually give up and create a blank image
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++
                    // Schedule another attempt after a short delay
                    handler.postDelayed({
                        Log.d(TAG, "Retrying screenshot capture")
                        captureScreenshot()
                    }, 500)
                } else {
                    Log.w(TAG, "Maximum retries reached, creating fallback image")
                    createFallbackScreenshot()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screenshot: ${e.message}")
            e.printStackTrace()

            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                handler.postDelayed({
                    Log.d(TAG, "Retrying screenshot after error")
                    captureScreenshot()
                }, 500)
            } else {
                Log.w(TAG, "Maximum retries reached after error, creating fallback image")
                createFallbackScreenshot()
            }
        }
    }

    /**
     * Process the captured image
     */
    private fun handleImageCaptured(image: Image) {
        imageCaptured = true
        Log.d(
            TAG,
            "Image acquired: width=${image.width}, height=${image.height}, format=${image.format}"
        )

        // Save the image to a file
        val screenshotFile = saveImage(image)
        image.close()

        if (screenshotFile != null) {
            // Successfully saved the screenshot
            Log.d(
                TAG,
                "Screenshot saved to: ${screenshotFile.absolutePath}, size=${screenshotFile.length()} bytes"
            )

            if (screenshotFile.length() > 0) {
                // Notify listener that screenshot was captured
                onScreenCapturedListener?.invoke(screenshotFile.absolutePath, screenshotFile)
            } else {
                Log.e(TAG, "Screenshot file is empty (0 bytes)")
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++
                    handler.postDelayed({
                        Log.d(TAG, "Trying to capture screenshot again after empty file")
                        captureScreenshot()
                    }, 500)
                } else {
                    createFallbackScreenshot()
                }
            }
        } else {
            Log.e(TAG, "Failed to save screenshot")

            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                handler.postDelayed({
                    Log.d(TAG, "Trying to capture screenshot again after save error")
                    captureScreenshot()
                }, 500)
            } else {
                createFallbackScreenshot()
            }
        }
    }

    /**
     * Create a fallback screenshot (blank image) when screen capture fails
     */
    private fun createFallbackScreenshot() {
        try {
            Log.d(TAG, "Creating fallback screenshot")

            // Create a blank bitmap with device dimensions
            val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Fill with light gray background
            canvas.drawColor(Color.LTGRAY)

            // Create file with timestamp
            val timestamp = System.currentTimeMillis()
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "fallback_screenshot_$timestamp.png"
            )

            // Save bitmap to file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            Log.d(
                TAG,
                "Fallback screenshot saved: ${file.absolutePath}, size=${file.length()} bytes"
            )

            // Notify listener with fallback image
            onScreenCapturedListener?.invoke(file.absolutePath, file)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback screenshot: ${e.message}")
            onScreenCaptureErrorListener?.invoke("Failed to create fallback screenshot: ${e.message}")
        }
    }

    /**
     * Save the captured image to a file
     */
    private fun saveImage(image: Image): File? {
        try {
            Log.d(TAG, "Saving captured image")

            if (image.planes.isEmpty()) {
                Log.e(TAG, "Image has no planes")
                return null
            }

            // Get the first plane
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            Log.d(
                TAG, "Image details: width=${image.width}, height=${image.height}, " +
                        "pixelStride=$pixelStride, rowStride=$rowStride, rowPadding=$rowPadding"
            )

            // Create bitmap from buffer
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            Log.d(TAG, "Bitmap created: width=${bitmap.width}, height=${bitmap.height}")

            // Create file with timestamp to avoid overwriting
            val timestamp = System.currentTimeMillis()
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "screenshot_$timestamp.png"
            )

            // Save bitmap to file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            Log.d(TAG, "Screenshot saved: ${file.absolutePath}, size=${file.length()} bytes")

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
    private fun stopScreenCapture() {
        Log.d(TAG, "Stopping screen capture")
        mediaProjection?.stop()
        virtualDisplay?.release()
        if (::imageReader.isInitialized) {
            try {
                imageReader.setOnImageAvailableListener(null, null)
                imageReader.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing ImageReader: ${e.message}")
            }
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