package com.example.islandpro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator

internal class GlowView(context: Context) : View(context) {

    var radius = 60f
    var onEnd: (() -> Unit)? = null

    private var c1 = 0xFF3987FF.toInt()
    private var c2 = 0xFF55D9FF.toInt()
    private var thick = 6f
    private var glow = 24f
    private var pad = 0f
    private var env = 0f
    private var phase = 0f
    private var shader: SweepGradient? = null
    private var shaderW = -1
    private var shaderH = -1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val mat = Matrix()
    private var envAnim: ValueAnimator? = null
    private var spinAnim: ValueAnimator? = null

    fun configure(color1: Int, color2: Int, thickness: Float, glowSize: Float, padding: Float) {
        c1 = color1
        c2 = color2
        thick = thickness
        glow = glowSize
        pad = padding
        shader = null
        shaderW = -1
    }

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
        val total = (maxOf(900L, durationMs) + 300L + 700L).toFloat()

        spinAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
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
                    ms < 300f -> ms / 300f
                    ms > total - 700f -> (total - ms) / 700f
                    else -> 1f
                }
                e = e.coerceIn(0f, 1f)
                env = e * e * (3f - 2f * e)
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

    override fun onDraw(canvas: Canvas) {
        if (env <= 0.01f) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (shader == null || shaderW != width || shaderH != height) {
            shader = SweepGradient(
                w / 2f, h / 2f,
                intArrayOf(c1, c2, c1),
                floatArrayOf(0f, 0.5f, 1f)
            )
            shaderW = width
            shaderH = height
        }
        mat.setRotate(phase * 360f, w / 2f, h / 2f)
        shader?.setLocalMatrix(mat)
        paint.shader = shader

        // The bright line sits on the pill's edge; the halo spreads outwards
        rect.set(pad, pad, w - pad, h - pad)
        val steps = 8
        for (k in steps downTo 0) {
            val f = k / steps.toFloat()
            paint.strokeWidth = thick + 2f * glow * f
            val a = if (k == 0) 1f else 0.16f * (1f - f) * (1f - 0.3f * f)
            paint.alpha = (255f * a * env).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
