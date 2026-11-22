package com.example.islandpro

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
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
        private const val CHANNEL_ID = "island_channel"
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        running = true
        buildPill()
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
    }

    private fun toggle() {
        val target = if (frac < 0.5f) 1f else 0f
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
                if (frac > 0.6f) showInfo() else label?.visibility = View.GONE
            }
            start()
        }
    }

    private fun showInfo() {
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        label?.text = "🔋 $level%   •   $time"
        label?.visibility = View.VISIBLE
    }

    private fun removePill() {
        animator?.cancel()
        box?.let {
            if (it.isAttachedToWindow) wm?.removeView(it)
        }
        box = null
    }

    override fun onDestroy() {
        removePill()
        running = false
        super.onDestroy()
    }
}
