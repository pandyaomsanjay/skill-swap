package com.example.sgp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Cumulative user-growth line chart. Same navy/cream palette as SwapsTrendView
 * (#1B3C53 / #456882 / #D2C1B6 / #F9F3EF) so it reads as part of the same
 * dashboard family.
 *
 * Usage: userGrowthChartView.setData(monthlyCumulativeCounts, monthLabels)
 * Tap a point to see its value + label in a tooltip (same interaction model
 * as the Swaps bar chart).
 */
class UserGrowthChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<Float> = listOf(0f, 0f)
    private var labels: List<String> = listOf("", "")

    private var axisMax: Float = -1f

    private var pointX: FloatArray = floatArrayOf()
    private var pointY: FloatArray = floatArrayOf()
    private var selectedIndex: Int = -1

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B3C53")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B3C53")
        style = Paint.Style.FILL
    }

    private val dotSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#456882")
        style = Paint.Style.FILL
    }

    private val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9F3EF")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D2C1B6")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B3C53")
        style = Paint.Style.FILL
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#456882")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tooltipValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9F3EF")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val tooltipLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D2C1B6")
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    /**
     * @param newPoints cumulative (or per-bucket) user counts to plot
     * @param newLabels per-point labels (e.g. "Jan") shown in the tooltip
     * @param newAxisMax optional scale ceiling; pass <= 0 to use the local max
     */
    fun setData(newPoints: List<Float>, newLabels: List<String> = emptyList(), newAxisMax: Float = -1f) {
        points = if (newPoints.size < 2) listOf(0f, 0f) else newPoints
        labels = if (newLabels.size == points.size) newLabels else List(points.size) { "" }
        axisMax = newAxisMax
        selectedIndex = -1
        invalidate()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                if (pointX.isEmpty()) return true
                val touchX = event.x
                var closest = 0
                var closestDist = Float.MAX_VALUE
                for (i in pointX.indices) {
                    val d = Math.abs(pointX[i] - touchX)
                    if (d < closestDist) {
                        closestDist = d
                        closest = i
                    }
                }
                selectedIndex = closest
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2 || width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val topPad = h * 0.22f  // room for tooltip above the line
        val bottomPad = h * 0.06f
        val plotTop = topPad
        val plotBottom = h - bottomPad
        val plotHeight = plotBottom - plotTop

        val localMax = (points.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it }
        val maxVal = if (axisMax > 0f) axisMax else localMax

        // --- Grid lines ---
        val gridRows = 4
        for (i in 0..gridRows) {
            val y = plotTop + (plotHeight / gridRows) * i
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        // --- Compute point geometry ---
        val slotWidth = w / (points.size - 1).coerceAtLeast(1)
        pointX = FloatArray(points.size)
        pointY = FloatArray(points.size)
        points.forEachIndexed { i, value ->
            pointX[i] = slotWidth * i
            val ratio = (value / maxVal).coerceIn(0f, 1f)
            pointY[i] = plotBottom - (ratio * plotHeight)
        }

        // --- Fill under the line (soft gradient) ---
        fillPaint.shader = LinearGradient(
            0f, plotTop, 0f, plotBottom,
            Color.parseColor("#331B3C53"), Color.parseColor("#001B3C53"),
            Shader.TileMode.CLAMP
        )
        val fillPath = Path().apply {
            moveTo(pointX[0], plotBottom)
            for (i in points.indices) lineTo(pointX[i], pointY[i])
            lineTo(pointX.last(), plotBottom)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)

        // --- The line itself ---
        val linePath = Path().apply {
            moveTo(pointX[0], pointY[0])
            for (i in 1 until points.size) lineTo(pointX[i], pointY[i])
        }
        canvas.drawPath(linePath, linePaint)

        // --- Dots ---
        for (i in points.indices) {
            val outer = if (i == selectedIndex) dotSelectedPaint else dotPaint
            val radius = if (i == selectedIndex) 9f else 6f
            canvas.drawCircle(pointX[i], pointY[i], radius, outer)
            canvas.drawCircle(pointX[i], pointY[i], radius * 0.45f, dotCorePaint)
        }

        // --- Tooltip ---
        if (selectedIndex in points.indices) {
            drawTooltip(canvas, selectedIndex, w)
        }
    }

    private fun drawTooltip(canvas: Canvas, index: Int, viewWidth: Float) {
        val value = points[index].toInt().toString()
        val label = labels.getOrElse(index) { "" }

        val boxWidth = 120f
        val boxHeight = if (label.isNotBlank()) 68f else 46f
        var boxLeft = pointX[index] - boxWidth / 2f
        if (boxLeft < 4f) boxLeft = 4f
        if (boxLeft + boxWidth > viewWidth - 4f) boxLeft = viewWidth - 4f - boxWidth
        val boxTop = (pointY[index] - boxHeight - 16f).coerceAtLeast(4f)

        val rect = android.graphics.RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
        canvas.drawRoundRect(rect, 14f, 14f, tooltipBgPaint)
        canvas.drawRoundRect(rect, 14f, 14f, tooltipBorderPaint)

        val centerX = boxLeft + boxWidth / 2f
        canvas.drawText(value, centerX, boxTop + 30f, tooltipValuePaint)
        if (label.isNotBlank()) {
            canvas.drawText(label, centerX, boxTop + 54f, tooltipLabelPaint)
        }
    }
}