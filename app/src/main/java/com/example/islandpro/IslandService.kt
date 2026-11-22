package com.example.islandpro

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IslandService : Service() {

    companion object {
        @Volatile
        var running = false
        const val ACTION_TIMER = "com.example.islandpro.TIMER"
        const val ACTION_STOPWATCH = "com.example.islandpro.STOPWATCH"
        const val ACTION_CANCEL = "com.example.islandpro.CANCEL"
        private const val CHANNEL_ID = "island_channel"
        private const val AUTO_COLLAPSE_MS = 4000L
        private const val MODE_NONE = 0
        private const val MODE_TIMER = 1
        private const val MODE_STOPWATCH = 2
    }

    private var wm: WindowManager? = null
    private var box: FrameLayout? = null
    private var label: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null

    private var smallW = 0
    private var smallH = 0
    private var bigW = 0
    private var bigH = 0
    private var frac = 0f

    private var charging = false
    private var mode = MODE_NONE
    private var timerEndMs = 0L
    private var stopwatchStartMs = 0L
    private var message: String? = null

    private val handler = Handler(Looper.getMainLooper())

    private val collapseRunnable = Runnable {
        message = null
        animateTo(0f)
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (mode == MODE_NONE) return
            if (mode == MODE_TIMER && SystemClock.elapsedRealtime() >= timerEndMs) {
                finishTimer()
            } else {
                updateLabel()
                handler.postDelayed(this, 250)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    charging = true
                    animateTo(1f)
                    scheduleCollapse()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    charging = false
                    animateTo(1f)
                    scheduleCollapse()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (label?.visibility == View.VISIBLE) updateLabel()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        charging = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        running = true
        when (intent?.action) {
            ACTION_TIMER -> {
                ensurePill()
                startTimer(intent?.getIntExtra("sec", 60) ?: 60)
            }
            ACTION_STOPWATCH -> {
                ensurePill()
                startStopwatch()
            }
            ACTION_CANCEL -> cancelTask()
            else -> buildPill()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Island", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Island চলছে")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun ensurePill() {
        if (box == null) buildPill()
    }

    private fun buildPill() {
        removePill()
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        smallW = dp(prefs.getInt("w", 120))
        smallH = dp(prefs.getInt("h", 34))
        val top = dp(prefs.getInt("top", 8))
        bigW = minOf(dp(320), resources.displayMetrics.widthPixels - dp(32))
        bigH = dp(80)
        frac = 0f

        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        label = tv

        val b = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = 1000f
            }
            addView(
                tv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            setOnClickListener { toggle() }
            setOnLongClickListener {
                cancelTask()
                true
            }
        }
        box = b

        val lp = WindowManager.LayoutParams(
            smallW,
            smallH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = top
        }
        params = lp

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm?.addView(b, lp)

        if (mode != MODE_NONE) updateLabel()
    }

    private fun startTimer(sec: Int) {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(collapseRunnable)
        message = null
        mode = MODE_TIMER
        timerEndMs = SystemClock.elapsedRealtime() + sec * 1000L
        animateTo(1f)
        updateLabel()
        handler.post(ticker)
    }

    private fun startStopwatch() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(collapseRunnable)
        message = null
        mode = MODE_STOPWATCH
        stopwatchStartMs = SystemClock.elapsedRealtime()
        animateTo(1f)
        updateLabel()
        handler.post(ticker)
    }

    private fun cancelTask() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(collapseRunnable)
        mode = MODE_NONE
        message = null
        animateTo(0f)
    }

    private fun finishTimer() {
        mode = MODE_NONE
        message = "⏰ সময় শেষ!"
        vibrate()
        animateTo(1f)
        updateLabel()
        scheduleCollapse()
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        v.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun toggle() {
        handler.removeCallbacks(collapseRunnable)
        animateTo(if (frac < 0.5f) 1f else 0f)
    }

    private fun scheduleCollapse() {
        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, AUTO_COLLAPSE_MS)
    }

    private fun animateTo(target: Float) {
        if (box == null || params == null) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(frac, target).apply {
            duration = 350
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener { a ->
                frac = a.animatedValue as Float
                val lp = params ?: return@addUpdateListener
                lp.width = (smallW + (bigW - smallW) * frac).toInt()
                lp.height = (smallH + (bigH - smallH) * frac).toInt()
                val v = box ?: return@addUpdateListener
                if (v.isAttachedToWindow) wm?.updateViewLayout(v, lp)
                if (frac > 0.6f || mode != MODE_NONE || message != null) {
                    updateLabel()
                } else {
                    label?.visibility = View.GONE
                }
            }
            start()
        }
    }

    private fun fmt(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun updateLabel() {
        val tv = label ?: return
        val now = SystemClock.elapsedRealtime()
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1

        val text = when {
            mode == MODE_TIMER -> {
                val remaining = maxOf(0L, timerEndMs - now)
                "⏳ " + fmt((remaining + 999) / 1000)
            }
            mode == MODE_STOPWATCH -> "⏱ " + fmt((now - stopwatchStartMs) / 1000)
            message != null -> message!!
            charging -> "⚡ চার্জ হচ্ছে   $level%"
            else -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                "🔋 $level%   •   $time"
            }
        }
        tv.textSize = if (frac > 0.6f) 16f else 11f
        tv.text = text
        tv.visibility = View.VISIBLE
    }

    private fun removePill() {
        handler.removeCallbacks(collapseRunnable)
        animator?.cancel()
        box?.let {
            if (it.isAttachedToWindow) wm?.removeView(it)
        }
        box = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        mode = MODE_NONE
        removePill()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        running = false
        super.onDestroy()
    }
}
