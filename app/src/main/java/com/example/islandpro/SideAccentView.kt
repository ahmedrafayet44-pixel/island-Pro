package com.example.islandpro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

internal class SideAccentView(
    context: Context,
    private val leftSide: Boolean
) : View(context) {

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 72, 132, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val path = Path()

        if (leftSide) {
            path.moveTo(w * 0.76f, h * 0.08f)
            path.cubicTo(
                w * 0.18f, h * 0.25f,
                w * 0.18f, h * 0.75f,
                w * 0.76f, h * 0.92f
            )
        } else {
            path.moveTo(w * 0.24f, h * 0.08f)
            path.cubicTo(
                w * 0.82f, h * 0.25f,
                w * 0.82f, h * 0.75f,
                w * 0.24f, h * 0.92f
            )
        }

        canvas.drawPath(path, accentPaint)
    }
}
