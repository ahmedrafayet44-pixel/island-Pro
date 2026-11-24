package com.example.islandpro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

internal class GlyphView(context: Context, initial: Int) : View(context) {

    companion object {
        const val TORCH = 0
        const val BELL = 1
        const val VIBRATE = 2
        const val MUTE = 3
        const val GEAR = 4
        const val HEADSET = 5
        const val CLIP = 6
        const val INFO = 7
        const val WARN = 8
        const val SUN = 9
        const val BATTERY = 10
        const val BOLT = 11
    }

    var glyph: Int = initial
        set(value) {
            field = value
            invalidate()
        }

    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var filled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var shadow: Boolean = false
        set(value) {
            field = value
            setLayerType(if (value) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_NONE, null)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val t = if (pressed) 0.85f else 1f
        animate().scaleX(t).scaleY(t).setDuration(90).start()
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)

        paint.color = tint
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.8f
        if (shadow) {
            paint.setShadowLayer(1.8f, 0f, 0.8f, Color.parseColor("#80000000"))
        } else {
            paint.clearShadowLayer()
        }
        val fs = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        paint.style = Paint.Style.STROKE

        when (glyph) {
            TORCH -> {
                paint.style = fs
                path.reset()
                path.moveTo(8f, 3.2f)
                path.lineTo(16f, 3.2f)
                path.lineTo(14.6f, 8.6f)
                path.lineTo(9.4f, 8.6f)
                path.close()
                canvas.drawPath(path, paint)
                rect.set(9.4f, 8.6f, 14.6f, 21f)
                canvas.drawRoundRect(rect, 2f, 2f, paint)
                paint.style = Paint.Style.STROKE
                if (filled) {
                    canvas.drawLine(12f, 0.6f, 12f, 1.9f, paint)
                    canvas.drawLine(7.4f, 1.8f, 6.4f, 0.8f, paint)
                    canvas.drawLine(16.6f, 1.8f, 17.6f, 0.8f, paint)
                } else {
                    canvas.drawLine(12f, 12f, 12f, 14.6f, paint)
                }
            }
            BELL, MUTE -> {
                path.reset()
                path.moveTo(7f, 11f)
                rect.set(7f, 6f, 17f, 16f)
                path.arcTo(rect, 180f, 180f)
                path.lineTo(17f, 15.2f)
                path.lineTo(18.5f, 17f)
                path.lineTo(5.5f, 17f)
                path.lineTo(7f, 15.2f)
                path.close()
                canvas.drawPath(path, paint)
                rect.set(10f, 17.8f, 14f, 21.8f)
                canvas.drawArc(rect, 0f, 180f, false, paint)
                canvas.drawLine(12f, 3.8f, 12f, 6f, paint)
                if (glyph == MUTE) canvas.drawLine(4.5f, 4.5f, 19.5f, 19.5f, paint)
            }
            VIBRATE -> {
                rect.set(8f, 4.5f, 16f, 19.5f)
                canvas.drawRoundRect(rect, 2.2f, 2.2f, paint)
                canvas.drawLine(4.8f, 9f, 4.8f, 15f, paint)
                canvas.drawLine(2.2f, 10.5f, 2.2f, 13.5f, paint)
                canvas.drawLine(19.2f, 9f, 19.2f, 15f, paint)
                canvas.drawLine(21.8f, 10.5f, 21.8f, 13.5f, paint)
            }
            GEAR -> {
                canvas.drawCircle(12f, 12f, 3f, paint)
                canvas.drawCircle(12f, 12f, 6.6f, paint)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val c = cos(a).toFloat()
                    val sn = sin(a).toFloat()
                    canvas.drawLine(
                        12f + 6.6f * c, 12f + 6.6f * sn,
                        12f + 9.4f * c, 12f + 9.4f * sn,
                        paint
                    )
                }
            }
            HEADSET -> {
                path.reset()
                path.moveTo(5f, 13f)
                path.lineTo(5f, 11f)
                rect.set(5f, 4f, 19f, 18f)
                path.arcTo(rect, 180f, 180f)
                path.lineTo(19f, 13f)
                canvas.drawPath(path, paint)
                rect.set(3.5f, 12.5f, 7.5f, 19f)
                canvas.drawRoundRect(rect, 1.8f, 1.8f, paint)
                rect.set(16.5f, 12.5f, 20.5f, 19f)
                canvas.drawRoundRect(rect, 1.8f, 1.8f, paint)
            }
            CLIP -> {
                rect.set(6f, 5f, 18f, 21f)
                canvas.drawRoundRect(rect, 2f, 2f, paint)
                rect.set(9f, 3f, 15f, 6.5f)
                canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
                canvas.drawLine(9f, 11f, 15f, 11f, paint)
                canvas.drawLine(9f, 14.5f, 15f, 14.5f, paint)
                canvas.drawLine(9f, 18f, 12.5f, 18f, paint)
            }
            INFO -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawLine(12f, 11f, 12f, 16.5f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 7.8f, 1.1f, paint)
            }
            WARN -> {
                path.reset()
                path.moveTo(12f, 3.8f)
                path.lineTo(21.2f, 19.8f)
                path.lineTo(2.8f, 19.8f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(12f, 9.5f, 12f, 14f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 16.9f, 1.1f, paint)
            }
            SUN -> {
                canvas.drawCircle(12f, 12f, 4f, paint)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val c = cos(a).toFloat()
                    val sn = sin(a).toFloat()
                    canvas.drawLine(
                        12f + 6.8f * c, 12f + 6.8f * sn,
                        12f + 9.4f * c, 12f + 9.4f * sn,
                        paint
                    )
                }
            }
            BATTERY -> {
                rect.set(3f, 7.5f, 19.5f, 16.5f)
                canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                canvas.drawLine(21.5f, 10.5f, 21.5f, 13.5f, paint)
                paint.style = Paint.Style.FILL
                rect.set(5.3f, 9.6f, 14f, 14.4f)
                canvas.drawRoundRect(rect, 1f, 1f, paint)
            }
            BOLT -> {
                paint.style = fs
                path.reset()
                path.moveTo(13.5f, 2.5f)
                path.lineTo(5.5f, 13.5f)
                path.lineTo(11f, 13.5f)
                path.lineTo(10f, 21.5f)
                path.lineTo(18.5f, 10f)
                path.lineTo(13f, 10f)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }
}
