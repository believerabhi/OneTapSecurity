package com.onetap.security

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.math.abs

class FloatingWidgetService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var loadingIndicator: ProgressBar
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastClickTime = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "FloatingWidgetChannel"
        private const val NOTIFICATION_ID = 1001
        private const val CLICK_TIME_THRESHOLD = 200L
        
        // Broadcast Actions
        const val ACTION_SCREENSHOT_PROCESSING_STARTED = "com.onetap.security.ACTION_SCREENSHOT_PROCESSING_STARTED"
        const val ACTION_SCREENSHOT_PROCESSING_FINISHED = "com.onetap.security.ACTION_SCREENSHOT_PROCESSING_FINISHED"
        const val TAG = "FloatingWidgetService"
    }
    
    // Broadcast receiver to handle screenshot processing status
    private val screenshotProcessingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SCREENSHOT_PROCESSING_STARTED -> {
                    Log.d(TAG, "Received broadcast: Screenshot processing started")
                    showLoadingIndicator()
                }
                ACTION_SCREENSHOT_PROCESSING_FINISHED -> {
                    Log.d(TAG, "Received broadcast: Screenshot processing finished")
                    hideLoadingIndicator()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingWidgetService onCreate called")

        // Start as a foreground service
        startForeground()

        // Get window manager and set up layout parameters
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        // Inflate the floating widget layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)
        
        // Initialize the loading indicator
        loadingIndicator = floatingView.findViewById(R.id.loading_indicator)

        // Set up touch listener for dragging
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastClickTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val touchDuration = System.currentTimeMillis() - lastClickTime
                    val moved = abs(event.rawX - initialTouchX) > 10 ||
                            abs(event.rawY - initialTouchY) > 10

                    if (!moved && touchDuration < CLICK_TIME_THRESHOLD) {
                        Log.d(TAG, "Widget clicked - Permission status: hasValidPermission=${ProjectionStore.hasValidPermission()}, resultCode=${ProjectionStore.resultCode}, data=${ProjectionStore.resultData != null}")
                        
                        // Check if we have projection permission
                        if (ProjectionStore.hasValidPermission()) {
                            Log.d(TAG, "Using stored projection permission for screen capture")
                            
                            // Show a toast indicating that screenshot is being taken
                            Toast.makeText(
                                this@FloatingWidgetService,
                                "Taking screenshot for analysis...",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Start the screen capture service with the stored permission data
                            val captureIntent = Intent(this@FloatingWidgetService, ScreenCaptureService::class.java).apply {
                                putExtra("resultCode", ProjectionStore.resultCode)
                                putExtra("data", ProjectionStore.resultData)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startForegroundService(captureIntent)
                            
                            // Show loading indicator
                            showLoadingIndicator()
                            
                            // Set a timeout to hide the loading indicator if the broadcast is never received
                            handler.postDelayed({
                                hideLoadingIndicator()
                            }, 20000) // 20 seconds timeout
                        } else {
                            // We need to get permission first - clear any existing data
                            Log.d(TAG, "No valid projection permission found, requesting now")
                            ProjectionStore.reset() // Clear any potentially corrupt data
                            
                            Toast.makeText(
                                this@FloatingWidgetService,
                                "Screenshot permission required",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Launch projection permission activity
                            val intent = Intent(this@FloatingWidgetService, ProjectionPermissionActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        }
                    }
                    true
                }

                else -> false
            }
        }

        // Add the view to the window
        windowManager.addView(floatingView, layoutParams)
        
        // Register broadcast receiver for screenshot processing status
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_SCREENSHOT_PROCESSING_STARTED)
            addAction(ACTION_SCREENSHOT_PROCESSING_FINISHED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(screenshotProcessingReceiver, intentFilter)
        
        // Go to the home screen to put the app in the background after a delay
        handler.postDelayed({
            Log.d(TAG, "Moving app to background")
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
        }, 1000) // Delay to ensure widget is properly displayed
    }
    
    private fun showLoadingIndicator() {
        // Hide the icon and show the loading indicator
        val floatingIcon = floatingView.findViewById<View>(R.id.floating_icon)
        floatingIcon.visibility = View.GONE
        loadingIndicator.visibility = View.VISIBLE
    }
    
    private fun hideLoadingIndicator() {
        // Show the icon and hide the loading indicator
        val floatingIcon = floatingView.findViewById<View>(R.id.floating_icon)
        floatingIcon.visibility = View.VISIBLE
        loadingIndicator.visibility = View.GONE
    }

    private fun startForeground() {
        // Create notification channel (required for Android 8.0+)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Floating Widget Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Channel for floating widget service"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        // Create notification for foreground service
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Shortcut Widget")
            .setContentText("Floating widget is active")
            .setSmallIcon(R.drawable.ic_shortcut)
            .setContentIntent(pendingIntent)
            .build()

        // Start service in foreground with the notification
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
        
        // Unregister broadcast receiver
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(screenshotProcessingReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
}