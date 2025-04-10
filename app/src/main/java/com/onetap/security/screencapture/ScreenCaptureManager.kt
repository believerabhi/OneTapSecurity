package com.onetap.security.screencapture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicInteger
import androidx.core.graphics.createBitmap

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
    private var retryCount = 0
    private val MAX_RETRY_COUNT = 3 // Limit retries to prevent infinite loop
    private val capturedImageCount = AtomicInteger(0)
    private val MAX_IMAGES = 1 // Just capture one image

    // Callback interface for in-memory screenshot processing
    interface ScreenshotCallback {
        fun onScreenshotCaptured(bitmap: Bitmap)
        fun onError(errorMessage: String)
    }

    private var screenshotCallback: ScreenshotCallback? = null

    // Use a concrete callback instance rather than a nullable one
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped")
            virtualDisplay?.release()
            if (::imageReader.isInitialized) {
                try {
                    imageReader.setOnImageAvailableListener(null, null)
                    imageReader.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing ImageReader: ${e.message}")
                }
            }
            handler.removeCallbacksAndMessages(null)
        }
    }

    /**
     * Initialize the screen capture manager
     */
    fun initialize() {
        Log.d(TAG, "Initializing screen capture manager")
        projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    /**
     * Set the callback for direct bitmap processing
     */
    fun setScreenshotCallback(callback: ScreenshotCallback) {
        screenshotCallback = callback
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

            // Reset the captured image counter
            capturedImageCount.set(0)

            // Add image available listener to capture immediately when ready
            imageReader.setOnImageAvailableListener({ reader ->
                try {
                    // Skip if we've already captured enough images
                    if (capturedImageCount.get() >= MAX_IMAGES) {
                        reader.acquireLatestImage()?.close() // Discard any additional images
                        return@setOnImageAvailableListener
                    }

                    // Increment the counter
                    capturedImageCount.incrementAndGet()

                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        processImageDirectly(image)
                    } else {
                        Log.e(TAG, "Null image in OnImageAvailableListener")
                        if (retryCount < MAX_RETRY_COUNT) {
                            retryCount++
                            scheduleScreenshot()
                        } else {
                            screenshotCallback?.onError("Failed to acquire image")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in image available listener: ${e.message}")
                    if (retryCount < MAX_RETRY_COUNT) {
                        retryCount++
                        scheduleScreenshot()
                    } else {
                        screenshotCallback?.onError("Error processing image: ${e.message}")
                    }
                }
            }, handler)

            // Get the media projection and register callback
            mediaProjection = projectionManager.getMediaProjection(resultCode, data).apply {
                // Register the callback
                registerCallback(mediaProjectionCallback, handler)
            }

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null after getMediaProjection")
                screenshotCallback?.onError("Failed to start media projection")
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
                screenshotCallback?.onError("Failed to create virtual display")
                return
            }

            Log.d(TAG, "Virtual display created, scheduling screenshot")

            // Reset retry counter
            retryCount = 0

            // Schedule screenshot after a short delay
            scheduleScreenshot(500) // Short delay for faster capture

        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}")
            e.printStackTrace()
            screenshotCallback?.onError("Failed to start screen capture: ${e.message}")
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
            if (!imageCaptured && capturedImageCount.get() < MAX_IMAGES) {
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
                processImageDirectly(image)
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
                    createFallbackImage()
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
                createFallbackImage()
            }
        }
    }

    /**
     * Process the captured image directly without saving to disk
     */
    private fun processImageDirectly(image: Image) {
        imageCaptured = true
        Log.d(
            TAG,
            "Image acquired: width=${image.width}, height=${image.height}, format=${image.format}"
        )

        try {
            val bitmap = imageToBitmap(image)
            image.close()

            if (bitmap != null) {
                // Process the bitmap directly
                Log.d(
                    TAG,
                    "Successfully converted image to bitmap: width=${bitmap.width}, height=${bitmap.height}"
                )
                screenshotCallback?.onScreenshotCaptured(bitmap)
            } else {
                Log.e(TAG, "Failed to convert image to bitmap")
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++
                    handler.postDelayed({
                        Log.d(TAG, "Retrying screenshot capture after bitmap conversion failure")
                        captureScreenshot()
                    }, 500)
                } else {
                    createFallbackImage()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
            image.close()

            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                handler.postDelayed({
                    Log.d(TAG, "Retrying after processing error")
                    captureScreenshot()
                }, 500)
            } else {
                createFallbackImage()
            }
        }
    }

    /**
     * Convert Image to Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        try {
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

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to bitmap: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * Create a blank image when screen capture fails
     */
    private fun createFallbackImage() {
        try {
            Log.d(TAG, "Creating fallback image")

            // Create a blank bitmap with device dimensions
            val bitmap = createBitmap(imageWidth, imageHeight)
            val canvas = Canvas(bitmap)

            // Fill with light gray background
            canvas.drawColor(Color.LTGRAY)

            Log.d(TAG, "Fallback image created")

            // Process the fallback bitmap
            screenshotCallback?.onScreenshotCaptured(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback image: ${e.message}")
            screenshotCallback?.onError("Failed to create fallback image: ${e.message}")
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
        screenshotCallback = null
    }
}