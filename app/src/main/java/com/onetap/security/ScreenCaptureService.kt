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
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private var imageCaptured = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        virtualDisplay?.release()
        imageReader.close()
    }

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startForeground(1, createNotification())

        //val captureIntent = projectionManager.createScreenCaptureIntent()
//        val captureIntent = Intent(this, ProjectionPermissionActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(this, 0, captureIntent, PendingIntent.FLAG_IMMUTABLE)
//        val notif = Notification.Builder(this, "screencapture")
//            .setContentTitle("Screen Analyzer Active")
//            .setContentText("Tap to analyze screen")
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .setContentIntent(pendingIntent)
//            .build()
//        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        manager.notify(1, notif)
    }

    private fun createNotification(): Notification {
        val channelId = "screencapture"
        val channelName = "Screen Analyzer"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(chan)

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
        // No-op for now, we’ll handle projection setup later
//        if (intent?.hasExtra("code") != true) {
//            val permIntent = Intent(this, ProjectionPermissionActivity::class.java).apply {
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            }
//            startActivity(permIntent)
//            return START_STICKY
//        }

        val resultCode = intent?.getIntExtra("code", -1) ?: return START_NOT_STICKY
        val resultData = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics().apply {
            wm.defaultDisplay.getRealMetrics(this)
        }

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, 0x1, 2)
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        handler.postDelayed({
            if (!imageCaptured) {
                imageCaptured = true
                Log.d("ScreenCaptureService", "taking screenshot")
                val image = imageReader.acquireLatestImage() ?: return@postDelayed
                saveImage(image)
                image.close()
                stopSelf()
            }
        }, 1000)

//        imageReader.setOnImageAvailableListener({ reader ->
////            if (imageCaptured) return@setOnImageAvailableListener
////            imageCaptured = true
//            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
//            saveImage(image)
//            image.close()
//            //stopSelf()
//        }, null)

        return START_STICKY
    }

    private fun saveImage(image: Image) {
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
            Log.d("ScreenCaptureService", "Saved screenshot to: ${file.absolutePath}")
        }
    }

}