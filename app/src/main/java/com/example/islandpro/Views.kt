package com.example.islandpro

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
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

internal class EqView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val colors = intArrayOf(
        Color.parseColor("#3DDCFF"),
        Color.parseColor("#6A5CFF"),
        Color.parseColor("#FF5CA8"),
        Color.parseColor("#3DDC84")
    )
    private val speeds = intArrayOf(2, 3, 1, 3)
    private val offsets = floatArrayOf(0f, 1.3f, 2.6f, 4.1f)
    private val rect = RectF()
    private var phase = 0f
    private var active = false
    private var anim: ValueAnimator? = null

    fun setActive(a: Boolean) {
        if (a == active) return
        active = a
        if (a) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            anim?.cancel()
            anim = null
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        active = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val n = colors.size
        val gap = width * 0.08f
        val bw = (width - gap * (n - 1)) / n
        val h = height.toFloat()
        for (i in 0 until n) {
            val f = if (active) {
                0.25f + 0.75f * abs(sin(2.0 * Math.PI * (phase * speeds[i]) + offsets[i])).toFloat()
            } else {
                0.18f
            }
            val bh = h * f
            val left = i * (bw + gap)
            rect.set(left, (h - bh) / 2f, left + bw, (h + bh) / 2f)
            paint.color = colors[i]
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
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
