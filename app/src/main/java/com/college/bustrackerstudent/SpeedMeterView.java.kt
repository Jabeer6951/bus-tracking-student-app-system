package com.college.bustrackerstudent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SpeedMeterView : View {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var speed = 0f
    private val maxSpeed = 120f

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        outerPaint.setStyle(Paint.Style.STROKE)
        outerPaint.setStrokeWidth(10f)
        outerPaint.setColor(Color.parseColor("#BDBDBD"))
        outerPaint.setStrokeCap(Paint.Cap.ROUND)

        progressPaint.setStyle(Paint.Style.STROKE)
        progressPaint.setStrokeWidth(10f)
        progressPaint.setStrokeCap(Paint.Cap.ROUND)
        progressPaint.setColor(Color.WHITE)

        innerPaint.setStyle(Paint.Style.FILL)
        innerPaint.setColor(Color.parseColor("#66BB6A"))

        speedPaint.setColor(Color.WHITE)
        speedPaint.setTextAlign(Paint.Align.CENTER)
        speedPaint.setTextSize(34f)
        speedPaint.setFakeBoldText(true)

        unitPaint.setColor(Color.WHITE)
        unitPaint.setTextAlign(Paint.Align.CENTER)
        unitPaint.setTextSize(16f)
        unitPaint.setFakeBoldText(true)
    }

    fun setSpeed(newSpeed: Float) {
        var newSpeed = newSpeed
        if (newSpeed < 0) newSpeed = 0f
        if (newSpeed > maxSpeed) newSpeed = maxSpeed
        speed = newSpeed
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = getWidth()
        val height = getHeight()

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 12f

        canvas.drawCircle(cx, cy, radius - 8f, innerPaint)

        val rect = RectF(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        canvas.drawArc(rect, 135f, 270f, false, outerPaint)

        val sweep = (speed / maxSpeed) * 270f
        canvas.drawArc(rect, 135f, sweep, false, progressPaint)

        canvas.drawText(Math.round(speed).toString(), cx, cy + 8f, speedPaint)
        canvas.drawText("km/h", cx, cy + 30f, unitPaint)
    }
}