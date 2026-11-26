package com.example.islandpro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Edge Lighting around the Island pill.
 *
 * style 0 = Aurora  : a flowing two-colour ring with a bright shimmer
 * style 1 = Comet   : two glowing comets with long fading tails circle the pill
 * style 2 = Pulse   : a calm breathing ring that ripples outwards
 */
internal class GlowView(context: Context) : View(context) {

    companion object {
        const val STYLE_AURORA = 0
        const val STYLE_COMET = 1
        const val STYLE_PULSE = 2
    }

    var radius = 60f
    var onEnd: (() -> Unit)? = null

    private var c1 = 0xFF3987FF.toInt()
    private var c2 = 0xFF55D9FF.toInt()
    private var hi = 0xFFFFFFFF.toInt()
    private var thick = 6f
    private var glow = 24f
    private var pad = 0f
    private var style = STYLE_AURORA

    private var env = 0f
    private var phase = 0f
    private var breath = 1f
    private var ripple = 0f

    private var shader: SweepGradient? = null
    private var shaderW = -1
    private var shaderH = -1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val mat = Matrix()
    private var envAnim: ValueAnimator? = null
    private var spinAnim: ValueAnimator? = null

    fun configure(
        color1: Int,
        color2: Int,
        thickness: Float,
        glowSize: Float,
        padding: Float,
        styleId: Int = STYLE_AURORA
    ) {
        c1 = color1
        c2 = color2
        hi = mix(color2, Color.WHITE, 0.65f)
        thick = thickness
        glow = glowSize
        pad = padding
        style = styleId
        shader = null
        shaderW = -1
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun m(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.argb(
            255,
            m(Color.red(a), Color.red(b)),
            m(Color.green(a), Color.green(b)),
            m(Color.blue(a), Color.blue(b))
        )
    }

    private fun withAlpha(c: Int, a: Float): Int =
        Color.argb((255f * a).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun stopAnims() {
        envAnim?.removeAllListeners()
        envAnim?.cancel()
        spinAnim?.cancel()
        envAnim = null
        spinAnim = null
    }

    fun start(durationMs: Long) {
        stopAnims()
        visibility = View.VISIBLE
        val fadeIn = 380f
        val fadeOut = 900f
        val total = maxOf(900L, durationMs).toFloat() + fadeIn + fadeOut

        val spinMs = when (style) {
            STYLE_COMET -> 1700L
            STYLE_PULSE -> 5200L
            else -> 2800L
        }
        spinAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = spinMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                breath = 0.82f + 0.18f * sin(phase * 2f * PI.toFloat() * 2f)
                invalidate()
            }
            start()
        }

        envAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = total.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                val ms = (it.animatedValue as Float) * total
                var e = when {
                    ms < fadeIn -> ms / fadeIn
                    ms > total - fadeOut -> (total - ms) / fadeOut
                    else -> 1f
                }
                e = e.coerceIn(0f, 1f)
                env = e * e * (3f - 2f * e)
                // one soft ripple travels outwards right after the glow appears
                ripple = (ms / 1100f).coerceIn(0f, 1f)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                }
            })
            start()
        }
    }

    private fun finish() {
        stopNow()
        onEnd?.invoke()
    }

    fun stopNow() {
        stopAnims()
        env = 0f
        visibility = View.INVISIBLE
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopAnims()
        super.onDetachedFromWindow()
    }

    private fun buildShader(w: Float, h: Float): SweepGradient {
        val cx = w / 2f
        val cy = h / 2f
        return when (style) {
            STYLE_COMET -> {
                val t = withAlpha(c1, 0f)
                SweepGradient(
                    cx, cy,
                    intArrayOf(
                        t, withAlpha(c1, 0.55f), c2, hi, t,
                        t, withAlpha(c1, 0.55f), c2, hi, t
                    ),
                    floatArrayOf(
                        0f, 0.20f, 0.42f, 0.495f, 0.5f,
                        0.5f, 0.70f, 0.92f, 0.995f, 1f
                    )
                )
            }
            STYLE_PULSE -> SweepGradient(
                cx, cy,
                intArrayOf(c1, mix(c1, c2, 0.5f), c2, mix(c1, c2, 0.5f), c1),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            )
            else -> SweepGradient(
                cx, cy,
                intArrayOf(c1, c2, hi, c2, c1, mix(c1, c2, 0.4f), c1),
                floatArrayOf(0f, 0.2f, 0.34f, 0.5f, 0.68f, 0.84f, 1f)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (env <= 0.01f) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (shader == null || shaderW != width || shaderH != height) {
            shader = buildShader(w, h)
            shaderW = width
            shaderH = height
        }
        val angle = if (style == STYLE_PULSE) phase * 90f else phase * 360f
        mat.setRotate(angle, w / 2f, h / 2f)
        shader?.setLocalMatrix(mat)
        paint.shader = shader
        corePaint.shader = shader

        rect.set(pad, pad, w - pad, h - pad)

        val strength = when (style) {
            STYLE_PULSE -> env * breath
            STYLE_COMET -> env
            else -> env * (0.9f + 0.1f * breath)
        }

        // 1) Soft bloom: many wide strokes with a gaussian-like fall-off
        val steps = 14
        for (k in steps downTo 1) {
            val f = k / steps.toFloat()
            paint.strokeWidth = thick + 2f * glow * f
            val a = 0.075f * exp(-2.8f * f * f)
            paint.alpha = (255f * a * strength).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        // 2) Expanding ripple (a thin ring that drifts outwards and fades)
        if (ripple in 0.01f..0.99f) {
            val rf = ripple
            val grow = glow * 0.9f * rf
            paint.strokeWidth = maxOf(1f, thick * 0.6f)
            paint.alpha = (255f * 0.5f * (1f - rf) * env).toInt().coerceIn(0, 255)
            rect.set(pad - grow, pad - grow, w - pad + grow, h - pad + grow)
            canvas.drawRoundRect(rect, radius + grow, radius + grow, paint)
            rect.set(pad, pad, w - pad, h - pad)
        }

        // 3) The bright line on the pill's edge
        corePaint.strokeWidth = thick
        corePaint.alpha = (255f * strength).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, corePaint)

        // 4) A thin white highlight in the middle of the line gives it a glassy look
        corePaint.shader = null
        corePaint.color = Color.WHITE
        corePaint.strokeWidth = maxOf(1f, thick * 0.38f)
        corePaint.alpha = (255f * 0.55f * strength).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, corePaint)
    }
}
