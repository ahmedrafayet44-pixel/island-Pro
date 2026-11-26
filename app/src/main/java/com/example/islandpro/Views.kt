package com.example.islandpro

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.Manifest
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin

internal class BarView(context: Context) : View(context) {

    var value: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = Color.parseColor("#33FFFFFF")
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        paint.color = Color.WHITE
        rect.set(0f, 0f, maxOf(h, w * value), h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
    }
}

internal class MediaButton(context: Context, initial: Int) : View(context) {

    companion object {
        const val PREV = 0
        const val PLAY = 1
        const val PAUSE = 2
        const val NEXT = 3
    }

    var icon: Int = initial
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val target = if (pressed) 0.88f else 1f
        animate().scaleX(target).scaleY(target).setDuration(90).start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val d = minOf(w, h)
        val big = icon == PLAY || icon == PAUSE

        paint.style = Paint.Style.FILL
        paint.color = if (big) Color.WHITE else Color.parseColor("#33FFFFFF")
        canvas.drawCircle(cx, cy, d / 2f, paint)

        val s = d * (if (big) 0.40f else 0.34f)
        paint.color = if (big) Color.BLACK else Color.WHITE
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = d * 0.04f

        when (icon) {
            PLAY -> {
                path.reset()
                path.moveTo(cx - 0.38f * s, cy - 0.5f * s)
                path.lineTo(cx + 0.52f * s, cy)
                path.lineTo(cx - 0.38f * s, cy + 0.5f * s)
                path.close()
                canvas.drawPath(path, paint)
            }
            PAUSE -> {
                val bw = 0.26f * s
                val gap = 0.24f * s
                rect.set(cx - gap / 2f - bw, cy - 0.5f * s, cx - gap / 2f, cy + 0.5f * s)
                canvas.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, paint)
                rect.set(cx + gap / 2f, cy - 0.5f * s, cx + gap / 2f + bw, cy + 0.5f * s)
                canvas.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, paint)
            }
            NEXT -> {
                path.reset()
                path.moveTo(cx - 0.5f * s, cy - 0.45f * s)
                path.lineTo(cx + 0.2f * s, cy)
                path.lineTo(cx - 0.5f * s, cy + 0.45f * s)
                path.close()
                canvas.drawPath(path, paint)
                rect.set(cx + 0.32f * s, cy - 0.45f * s, cx + 0.5f * s, cy + 0.45f * s)
                canvas.drawRoundRect(rect, 0.05f * s, 0.05f * s, paint)
            }
            else -> {
                path.reset()
                path.moveTo(cx + 0.5f * s, cy - 0.45f * s)
                path.lineTo(cx - 0.2f * s, cy)
                path.lineTo(cx + 0.5f * s, cy + 0.45f * s)
                path.close()
                canvas.drawPath(path, paint)
                rect.set(cx - 0.5f * s, cy - 0.45f * s, cx - 0.32f * s, cy + 0.45f * s)
                canvas.drawRoundRect(rect, 0.05f * s, 0.05f * s, paint)
            }
        }
    }
}

internal class EqView(context: Context, private val barCount: Int = 5) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val target = FloatArray(barCount)
    private val cur = FloatArray(barCount) { 0.14f }
    private var lastFeed = 0L
    private var active = false
    private var anim: ValueAnimator? = null

    // bar colour (taken from album art when available)
    var accent: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    // Real audio levels (0..1) from the visualizer
    fun feed(levels: FloatArray) {
        if (levels.isEmpty()) return
        lastFeed = SystemClock.uptimeMillis()
        for (i in 0 until barCount) {
            val idx = (i * levels.size / barCount).coerceAtMost(levels.size - 1)
            target[i] = levels[idx].coerceIn(0f, 1f)
        }
    }

    fun setActive(a: Boolean) {
        if (a == active) return
        active = a
        if (a) {
            anim?.cancel()
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    step()
                    invalidate()
                }
                start()
            }
        } else {
            anim?.cancel()
            anim = null
            for (i in 0 until barCount) cur[i] = 0.14f
            invalidate()
        }
    }

    // Used when the visualizer gives no data (no permission, protected audio)
    private fun fake(i: Int, now: Long): Float {
        val t = now / 1000.0
        val a = sin(t * (2.1 + i * 0.83) + i * 1.7)
        val b = sin(t * (3.7 + i * 0.51) + i * 0.6)
        val c = sin(t * (0.9 + i * 0.37))
        val v = 0.5 + 0.28 * a + 0.2 * b + 0.12 * c
        return v.toFloat().coerceIn(0.12f, 1f)
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val live = now - lastFeed < 700L
        for (i in 0 until barCount) {
            val t = if (live) target[i] else fake(i, now)
            val k = if (t > cur[i]) 0.55f else 0.16f
            cur[i] += (t - cur[i]) * k
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        active = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val n = barCount
        val h = height.toFloat()
        val gap = width * (if (n > 5) 0.07f else 0.10f)
        val bw = (width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val f = 0.14f + 0.86f * cur[i].coerceIn(0f, 1f)
            val bh = maxOf(bw, h * f)
            val left = i * (bw + gap)
            rect.set(left, (h - bh) / 2f, left + bw, (h + bh) / 2f)
            paint.color = accent
            paint.alpha = 130 + (125 * cur[i].coerceIn(0f, 1f)).toInt()
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
        }
    }
}

// Reads the real audio that is playing (FFT) and sends bar levels. Needs RECORD_AUDIO.
internal class AudioViz(private val context: Context, private val onLevels: (FloatArray) -> Unit) {

    private var viz: Visualizer? = null
    private var peak = 0.35f
    private val levels = FloatArray(7)

    val running: Boolean get() = viz != null

    fun start(): Boolean {
        if (viz != null) return true
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val v = Visualizer(0)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(minOf(range[1], 1024).coerceAtLeast(range[0]))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, wave: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft != null) process(fft)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.setEnabled(true)
            viz = v
            true
        } catch (e: Throwable) {
            viz = null
            false
        }
    }

    fun stop() {
        try {
            viz?.setEnabled(false)
            viz?.release()
        } catch (e: Throwable) {
        }
        viz = null
    }

    private fun process(fft: ByteArray) {
        val bins = fft.size / 2
        if (bins < 8) return
        val n = levels.size
        var maxLv = 0f
        var any = false
        for (b in 0 until n) {
            // log spaced bands, roughly 60 Hz to 8 kHz
            val lo = 1 + (Math.pow(bins * 0.30, b / n.toDouble()) - 1.0).toInt()
            val hi = 1 + (Math.pow(bins * 0.30, (b + 1) / n.toDouble()) - 1.0).toInt()
            val from = lo.coerceIn(1, bins - 1)
            val to = maxOf(from + 1, hi).coerceAtMost(bins)
            var sum = 0f
            var cnt = 0
            for (k in from until to) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                sum += hypot(re, im)
                cnt++
            }
            val m = if (cnt > 0) sum / cnt else 0f
            if (m > 0.5f) any = true
            var lv = (ln(1f + m) / ln(1f + 110f))
            lv *= 1f + b * 0.10f
            levels[b] = lv
            if (lv > maxLv) maxLv = lv
        }
        if (!any) return
        peak = maxOf(0.35f, maxOf(peak * 0.992f, maxLv))
        val out = FloatArray(n) { (levels[it] / peak).coerceIn(0f, 1f) }
        onLevels(out)
    }
}

// Thin One UI style seek bar. Drag to go forward or backward in the song.
internal class SeekView(context: Context) : View(context) {

    var progress: Float = 0f
        set(v) {
            if (dragging) return
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    var onScrub: ((Float) -> Unit)? = null
    var onCommit: ((Float) -> Unit)? = null
    var onTouchState: ((Boolean) -> Unit)? = null

    private var dragging = false
    private var shown = 0f
    private var grow = 0f
    private var growAnim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun animGrow(to: Float) {
        growAnim?.cancel()
        growAnim = ValueAnimator.ofFloat(grow, to).apply {
            duration = 140
            addUpdateListener {
                grow = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun fracAt(x: Float): Float {
        val pad = height / 2f
        val w = maxOf(1f, width - 2 * pad)
        return ((x - pad) / w).coerceIn(0f, 1f)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                onTouchState?.invoke(true)
                shown = fracAt(e.x)
                animGrow(1f)
                onScrub?.invoke(shown)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                shown = fracAt(e.x)
                onScrub?.invoke(shown)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                animGrow(0f)
                if (e.action == MotionEvent.ACTION_UP) {
                    progress = shown
                    onCommit?.invoke(shown)
                }
                onTouchState?.invoke(false)
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val cy = h / 2f
        val pad = h / 2f
        val tw = width - 2 * pad
        val th = h * (0.16f + 0.14f * grow)
        val f = if (dragging) shown else progress
        rect.set(pad, cy - th / 2f, pad + tw, cy + th / 2f)
        paint.color = Color.parseColor("#40FFFFFF")
        canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)
        rect.set(pad, cy - th / 2f, pad + tw * f, cy + th / 2f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)
        val r = h * (0.17f + 0.09f * grow)
        canvas.drawCircle(pad + tw * f, cy, r, paint)
    }
}

// Phone handset (Material "call" shape) drawn as a path in a 24x24 box
internal object PhonePath {
    fun fill(p: Path) {
        p.reset()
        p.moveTo(6.62f, 10.79f)
        p.cubicTo(8.06f, 13.62f, 10.38f, 15.93f, 13.21f, 17.38f)
        p.lineTo(15.41f, 15.18f)
        p.cubicTo(15.68f, 14.91f, 16.08f, 14.82f, 16.43f, 14.94f)
        p.cubicTo(17.55f, 15.31f, 18.76f, 15.51f, 20.0f, 15.51f)
        p.cubicTo(20.55f, 15.51f, 21.0f, 15.96f, 21.0f, 16.51f)
        p.lineTo(21.0f, 20.0f)
        p.cubicTo(21.0f, 20.55f, 20.55f, 21.0f, 20.0f, 21.0f)
        p.cubicTo(10.61f, 21.0f, 3.0f, 13.39f, 3.0f, 4.0f)
        p.cubicTo(3.0f, 3.45f, 3.45f, 3.0f, 4.0f, 3.0f)
        p.lineTo(7.5f, 3.0f)
        p.cubicTo(8.05f, 3.0f, 8.5f, 3.45f, 8.5f, 4.0f)
        p.cubicTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f)
        p.cubicTo(9.18f, 7.92f, 9.10f, 8.31f, 8.82f, 8.59f)
        p.lineTo(6.62f, 10.79f)
        p.close()
    }
}

// Small plain phone icon (used on the left of the pill during a call)
internal class PhoneIconView(context: Context) : View(context) {

    var tint: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    init {
        PhonePath.fill(path)
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)
        paint.color = tint
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

// Round One UI call buttons: green accept, red decline, grey mute
internal class CallButton(context: Context, initialKind: Int) : View(context) {

    var kind: Int = initialKind
        set(value) {
            field = value
            invalidate()
        }


    companion object {
        const val ACCEPT = 0
        const val DECLINE = 1
        const val MUTE = 2
    }

    var muted: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    init {
        PhonePath.fill(path)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val t = if (pressed) 0.86f else 1f
        animate().scaleX(t).scaleY(t).setDuration(90).start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        paint.style = Paint.Style.FILL
        paint.color = when (kind) {
            ACCEPT -> Color.parseColor("#30D158")
            DECLINE -> Color.parseColor("#FF453A")
            else -> if (muted) Color.parseColor("#F2F2F7") else Color.parseColor("#40FFFFFF")
        }
        canvas.drawCircle(cx, cy, d / 2f, paint)

        if (kind == MUTE) {
            val ink = if (muted) Color.parseColor("#1C1C1E") else Color.WHITE
            paint.color = ink
            paint.style = Paint.Style.FILL
            val mw = d * 0.17f
            val mh = d * 0.30f
            rect.set(cx - mw, cy - mh * 0.95f, cx + mw, cy + mh * 0.35f)
            canvas.drawRoundRect(rect, mw, mw, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d * 0.055f
            paint.strokeCap = Paint.Cap.ROUND
            rect.set(cx - d * 0.25f, cy - d * 0.27f, cx + d * 0.25f, cy + d * 0.23f)
            canvas.drawArc(rect, 20f, 140f, false, paint)
            canvas.drawLine(cx, cy + d * 0.23f, cx, cy + d * 0.33f, paint)
            if (!muted) {
                paint.strokeWidth = d * 0.07f
                canvas.drawLine(cx - d * 0.27f, cy - d * 0.27f, cx + d * 0.27f, cy + d * 0.30f, paint)
            }
            return
        }

        val s = d * 0.5f / 24f
        canvas.save()
        canvas.translate(cx, cy)
        if (kind == DECLINE) canvas.rotate(135f)
        canvas.scale(s, s)
        canvas.translate(-12f, -12f)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

// Water drop ripple: rings spread out of the pill when a new notification comes
internal class RippleView(context: Context) : View(context) {

    var pad = 0f
    var radius = 60f
    private var t = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    var onEnd: (() -> Unit)? = null

    fun play(color: Int) {
        anim?.removeAllListeners()
        anim?.cancel()
        paint.color = color
        visibility = View.VISIBLE
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    visibility = View.INVISIBLE
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    fun stopNow() {
        anim?.removeAllListeners()
        anim?.cancel()
        anim = null
        visibility = View.INVISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        if (t <= 0f) return
        val w = width.toFloat()
        val h = height.toFloat()
        for (ring in 0 until 3) {
            val local = ((t - ring * 0.16f) / (1f - ring * 0.16f)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val grow = pad * local
            val a = (1f - local)
            paint.alpha = (150 * a * a).toInt()
            paint.strokeWidth = (3.2f - ring * 0.8f) * resources.displayMetrics.density * (1f - local * 0.5f)
            rect.set(pad - grow, pad - grow, w - pad + grow, h - pad + grow)
            val r = radius + grow
            canvas.drawRoundRect(rect, r, r, paint)
        }
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
            ring.color = green
            canvas.drawArc(oval, -90f, 360f * level / 100f * t, false, ring)
            fill.color = green
            fill.alpha = (70 * (1f - pulse)).toInt()
            canvas.drawCircle(cx, cy, r * (0.45f + 0.5f * pulse), fill)
            drawBolt(canvas, cx, cy, r * 1.15f, 0f, 0.6f + 0.4f * t, 255)
        } else {
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
