package com.onetap.security

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class FloatingWidgetService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var popupView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastClickTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val hidePopupRunnable = Runnable { hidePopupMessage() }
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "FloatingWidgetChannel"
        private const val NOTIFICATION_ID = 1001
        private const val CLICK_TIME_THRESHOLD = 200L
        private const val POPUP_DISPLAY_DURATION = 2000L
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        
        // Start as a foreground service
        startForeground()
        
        // Get window manager and set up layout parameters
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
        
        // Inflate the floating widget layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)
        
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
                    val moved = Math.abs(event.rawX - initialTouchX) > 10 || 
                                Math.abs(event.rawY - initialTouchY) > 10
                    
                    if (!moved && touchDuration < CLICK_TIME_THRESHOLD) {
                        // Show popup message if it was a click (not a drag)
                        showPopupMessage(layoutParams.x, layoutParams.y)
                    }
                    true
                }
                else -> false
            }
        }
        
        // Add the view to the window
        windowManager.addView(floatingView, layoutParams)
    }
    
    private fun showPopupMessage(x: Int, y: Int) {
        // Remove any existing popup
        hidePopupMessage()
        
        // Create new popup
        popupView = LayoutInflater.from(this).inflate(R.layout.popup_message, null)
        
        // Position the popup near the floating widget
        val popupParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x + 60
            this.y = y
        }
        
        // Add the popup to window
        windowManager.addView(popupView, popupParams)
        
        // Schedule to remove the popup after a delay
        handler.removeCallbacks(hidePopupRunnable)
        handler.postDelayed(hidePopupRunnable, POPUP_DISPLAY_DURATION)
    }
    
    private fun hidePopupMessage() {
        popupView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
            popupView = null
        }
    }
    
    private fun startForeground() {
        // Create notification channel (required for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        }
        
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
        // Remove all views we've added
        hidePopupMessage()
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
        handler.removeCallbacks(hidePopupRunnable)
    }
}
