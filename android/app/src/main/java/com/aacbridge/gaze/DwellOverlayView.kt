package com.aacbridge.gaze

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Radial progress indicator for gaze dwell.
 * Subscribes to onDwellProgress callback from GazeTracker.
 */
class DwellOverlayView(context: Context) : View(context) {

    private var progress: Float = 0f
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // Emerald 500
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155") // Slate 700
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    
    private val rect = RectF()

    fun updateProgress(newProgress: Float) {
        progress = newProgress.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = Math.min(w, h).toFloat()
        val padding = paint.strokeWidth / 2f
        rect.set(
            (w - size) / 2f + padding,
            (h - size) / 2f + padding,
            (w + size) / 2f - padding,
            (h + size) / 2f - padding
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Background ring
        canvas.drawArc(rect, 0f, 360f, false, bgPaint)
        
        // Progress arc
        if (progress > 0f) {
            val sweepAngle = 360f * progress
            // Start at top (-90 degrees)
            canvas.drawArc(rect, -90f, sweepAngle, false, paint)
        }
    }
}
