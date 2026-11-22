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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

class IslandService : Service() {

    companion object {
        @Volatile
        var running = false
        private const val CHANNEL_ID = "island_channel"
        private const val AUTO_COLLAPSE_MS = 3500L
        private const val EVENT_NONE = 0
        private const val EVENT_PLUG = 1
        private const val EVENT_UNPLUG = 2
    }

    private var wm: WindowManager? = null
    private var box: FrameLayout? = null
    private var label: TextView? = null
    private var chargeRow: LinearLayout? = null
    private var chargeView: ChargeView? = null
    private var chargeTitle: TextView? = null
    private var chargeLevel: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null

    private var smallW = 0
    private var smallH = 0
    private var bigW = 0
    private var bigH = 0
    private var frac = 0f
    private var targetFrac = 0f

    private var charging = false
    private var event = EVENT_NONE
    private var chargeShown = false

    private val handler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { animateTo(0f) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    charging = true
                    startEvent(EVENT_PLUG)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    charging = false
                    startEvent(EVENT_UNPLUG)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (event != EVENT_NONE) {
                        updateChargeTexts()
                    } else if (label?.visibility == View.VISIBLE) {
                        showInfo()
                    }
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
        buildPill()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Island", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Island is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun batteryLevel(): Int {
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

    private fun buildPill() {
        removePill()
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        smallW = dp(prefs.getInt("w", 120))
        smallH = dp(prefs.getInt("h", 34))
        val top = dp(prefs.getInt("top", 8))
        val offsetX = dp(prefs.getInt("x", 0))
        bigW = minOf(dp(320), resources.displayMetrics.widthPixels - dp(32))
        bigH = dp(80)
        frac = 0f
        targetFrac = 0f
        event = EVENT_NONE
        chargeShown = false

        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        label = tv

        val cv = ChargeView(this)
        chargeView = cv
        val title = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0AE"))
        }
        val level = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        chargeTitle = title
        chargeLevel = level
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(level)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(cv, LinearLayout.LayoutParams(dp(54), dp(54)))
            addView(
                textCol,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(14) }
            )
        }
        chargeRow = row

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
            addView(
                row,
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
            x = offsetX
            y = top
        }
        params = lp

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm?.addView(b, lp)
    }

    private fun startEvent(e: Int) {
        if (box == null) return
        event = e
        chargeShown = false
        updateChargeTexts()
        handler.removeCallbacks(collapseRunnable)
        animateTo(1f)
        scheduleCollapse()
    }

    private fun updateChargeTexts() {
        val unplug = event == EVENT_UNPLUG
        chargeTitle?.text = if (unplug) "Unplugged" else "Charging"
        chargeTitle?.setTextColor(Color.parseColor(if (unplug) "#FF9F43" else "#3DDC84"))
        chargeLevel?.text = "${batteryLevel()}%"
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
        targetFrac = target
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
                renderContent()
            }
            start()
        }
    }

    private fun renderContent() {
        if (targetFrac == 0f && frac < 0.3f && event != EVENT_NONE) {
            event = EVENT_NONE
            chargeView?.stop()
        }
        if (frac <= 0.6f) {
            label?.visibility = View.GONE
            chargeRow?.visibility = View.GONE
            return
        }
        if (event != EVENT_NONE) {
            label?.visibility = View.GONE
            chargeRow?.visibility = View.VISIBLE
            if (!chargeShown) {
                chargeShown = true
                chargeView?.play(batteryLevel(), event == EVENT_UNPLUG)
            }
        } else {
            chargeRow?.visibility = View.GONE
            if (label?.visibility != View.VISIBLE) showInfo()
        }
    }

    private fun showInfo() {
        val level = batteryLevel()
        label?.text = if (charging) {
            "⚡ Charging   $level%"
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "🔋 $level%   •   $time"
        }
        label?.visibility = View.VISIBLE
    }

    private fun removePill() {
        handler.removeCallbacks(collapseRunnable)
        animator?.cancel()
        chargeView?.stop()
        box?.let {
            if (it.isAttachedToWindow) wm?.removeView(it)
        }
        box = null
        chargeRow = null
        chargeView = null
    }

    override fun onDestroy() {
        removePill()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        running = false
        super.onDestroy()
    }
}

internal class ChargeView(context: Context) : View(context) {

    private val green = Color.parseColor("#3DDC84")
    private val orange = Color.parseColor("#FF9F43")

    private val ringBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#33FFFFFF")
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val slash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = orange
    }
    private val boltPath = Path()
    private val oval = RectF()

    private var level = 0
    private var unplug = false
    private var t = 0f
    private var pulse = 0f
    private var introAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null

    fun play(batteryLevel: Int, isUnplug: Boolean) {
        stop()
        level = batteryLevel.coerceIn(0, 100)
        unplug = isUnplug
        t = 0f
        pulse = 0f
        introAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (isUnplug) 900L else 1100L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        introAnim?.cancel()
        pulseAnim?.cancel()
        introAnim = null
        pulseAnim = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    private fun drawBolt(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        dx: Float,
        scale: Float,
        alpha: Int
    ) {
        val l = cx - size / 2f + dx
        val tp = cy - size / 2f
        boltPath.reset()
        boltPath.moveTo(l + 0.55f * size, tp)
        boltPath.lineTo(l + 0.20f * size, tp + 0.55f * size)
        boltPath.lineTo(l + 0.45f * size, tp + 0.55f * size)
        boltPath.lineTo(l + 0.40f * size, tp + size)
        boltPath.lineTo(l + 0.80f * size, tp + 0.40f * size)
        boltPath.lineTo(l + 0.55f * size, tp + 0.40f * size)
        boltPath.close()
        boltPaint.alpha = alpha.coerceIn(0, 255)
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawPath(boltPath, boltPaint)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val cy = height / 2f
        val stroke = w * 0.09f
        val r = w / 2f - stroke * 1.2f
        ringBg.strokeWidth = stroke
        ring.strokeWidth = stroke
        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, 0f, 360f, false, ringBg)

        if (!unplug) {
            // Plugged in: ring fills up, green glow pulses, bolt pops in
            ring.color = green
            canvas.drawArc(oval, -90f, 360f * level / 100f * t, false, ring)
            fill.color = green
            fill.alpha = (70 * (1f - pulse)).toInt()
            canvas.drawCircle(cx, cy, r * (0.45f + 0.5f * pulse), fill)
            drawBolt(canvas, cx, cy, r * 1.15f, 0f, 0.6f + 0.4f * t, 255)
        } else {
            // Unplugged: ring turns orange, bolt shakes and dims, slash is drawn
            ring.color = mix(green, orange, t)
            canvas.drawArc(oval, -90f, 360f * level / 100f, false, ring)
            fill.color = orange
            fill.alpha = (60 * (1f - pulse)).toInt()
            canvas.drawCircle(cx, cy, r * (0.4f + 0.6f * pulse), fill)
            val shake = sin(t * 25f) * (1f - t) * w * 0.06f
            drawBolt(canvas, cx, cy, r * 1.15f, shake, 1f, 255 - (120 * t).toInt())
            val p = (t * 1.5f - 0.3f).coerceIn(0f, 1f)
            val d = r * 0.65f
            slash.strokeWidth = stroke * 0.9f
            canvas.drawLine(cx - d, cy - d, cx - d + 2f * d * p, cy - d + 2f * d * p, slash)
        }
    }
}
