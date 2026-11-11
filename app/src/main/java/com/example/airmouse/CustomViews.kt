package com.example.airmouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ScrollView that locks scrolling while the user interacts with touch surfaces
 * such as the trackpad, rotary dials, or virtual thumbsticks.
 */
class LockableScrollView(context: Context) : ScrollView(context) {
    var isScrollLocked: Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (isScrollLocked) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (isScrollLocked) return false
        return super.onTouchEvent(ev)
    }
}

/**
 * Velocity-Sensitive Circular Rotary Dial & Jog Wheel.
 *
 * - Slow rotation: Fine 7.2° detents (multiplier = 1) for frame/fine step control.
 * - Medium rotation: 2x–3x step acceleration.
 * - Fast rotation ("Vroom" scrub): Up to 6x burst acceleration per detent.
 */
class VelocityRotaryDialView(
    context: Context,
    private val title: String,
    private var accentColorInt: Int,
    private var surfaceColorInt: Int,
    private var centerColorInt: Int,
    private var textColorInt: Int,
    private val scrollLockCallback: (Boolean) -> Unit,
    private val hapticCallback: () -> Unit,
    private val centerTextProvider: () -> String,
    private val centerSubTextProvider: () -> String,
    private val onStep: (clockwise: Boolean, velocityMultiplier: Int) -> Unit,
    private val onCenterTap: () -> Unit,
    private val onReleaseWheel: (() -> Unit)? = null
) : View(context) {

    private var currentAngleRad = -Math.PI / 2
    private var lastAngleRad = 0.0
    private var lastMoveTimeMs = 0L
    private var accumulatedAngle = 0.0
    private var currentVelocityMultiplier = 1
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3).toFloat()
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(11).toFloat()
    }
    private val centerMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(14).toFloat()
    }
    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(9).toFloat()
    }

    fun updateThemeColors(accent: Int, surface: Int, centerBg: Int, onSurface: Int) {
        accentColorInt = accent
        surfaceColorInt = surface
        centerColorInt = centerBg
        textColorInt = onSurface
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ringPaint.color = surfaceColorInt
        borderPaint.color = accentColorInt
        knobPaint.color = accentColorInt
        centerPaint.color = centerColorInt
        titlePaint.color = textColorInt
        centerMainPaint.color = textColorInt

        val cx = width / 2f
        val cy = (height / 2f) + dp(8)
        val outerR = min(width, height - dp(20)) * 0.44f
        val innerR = outerR * 0.48f

        val speedTag = if (currentVelocityMultiplier > 1) " (${currentVelocityMultiplier}x)" else ""
        canvas.drawText(title + speedTag, cx, dp(14).toFloat(), titlePaint)

        canvas.drawCircle(cx, cy, outerR, ringPaint)
        canvas.drawCircle(cx, cy, outerR, borderPaint)

        // 30 fine encoder ticks around the perimeter
        for (i in 0 until 30) {
            val a = (i * Math.PI * 2.0 / 30.0)
            val x1 = cx + (outerR * 0.80f * cos(a)).toFloat()
            val y1 = cy + (outerR * 0.80f * sin(a)).toFloat()
            val x2 = cx + (outerR * 0.92f * cos(a)).toFloat()
            val y2 = cy + (outerR * 0.92f * sin(a)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        val knobDist = outerR * 0.67f
        val kx = cx + (knobDist * cos(currentAngleRad)).toFloat()
        val ky = cy + (knobDist * sin(currentAngleRad)).toFloat()
        val knobRadius = if (currentVelocityMultiplier >= 4) dp(9).toFloat() else dp(7).toFloat()
        canvas.drawCircle(kx, ky, knobRadius, knobPaint)

        canvas.drawCircle(cx, cy, innerR, centerPaint)
        canvas.drawCircle(cx, cy, innerR, borderPaint)
        canvas.drawText(centerTextProvider(), cx, cy + dp(2), centerMainPaint)
        canvas.drawText(centerSubTextProvider(), cx, cy + dp(14), centerSubPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = (height / 2f) + dp(8)
        val outerR = min(width, height - dp(20)) * 0.44f
        val innerR = outerR * 0.48f
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollLockCallback(true)
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                lastMoveTimeMs = downTime
                lastAngleRad = atan2(dy.toDouble(), dx.toDouble())
                if (dist > innerR * 0.65f) {
                    currentAngleRad = lastAngleRad
                }
                accumulatedAngle = 0.0
                currentVelocityMultiplier = 1
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrollLockCallback(true)
                parent?.requestDisallowInterceptTouchEvent(true)
                if (dist > dp(12)) {
                    val now = System.currentTimeMillis()
                    val dtSec = ((now - lastMoveTimeMs).coerceAtLeast(4)) / 1000.0
                    lastMoveTimeMs = now

                    val angle = atan2(dy.toDouble(), dx.toDouble())
                    var delta = angle - lastAngleRad
                    if (delta > Math.PI) delta -= 2.0 * Math.PI
                    if (delta < -Math.PI) delta += 2.0 * Math.PI

                    // Compute angular velocity |rad/s| to scale precision vs high-speed scrubbing
                    val omega = abs(delta / dtSec)
                    currentVelocityMultiplier = when {
                        omega >= 6.5 -> 6 // High-speed "vroom" scrub
                        omega >= 4.0 -> 4
                        omega >= 2.2 -> 2
                        else -> 1         // Slow rotation -> frame-by-frame / 1x fine step
                    }

                    accumulatedAngle += delta
                    lastAngleRad = angle
                    currentAngleRad = angle

                    // Fine 7.2-degree detent threshold (Math.PI / 25) for responsive slow rotation
                    val stepThreshold = Math.PI / 25.0
                    while (accumulatedAngle >= stepThreshold) {
                        accumulatedAngle -= stepThreshold
                        hapticCallback()
                        onStep(true, currentVelocityMultiplier)
                    }
                    while (accumulatedAngle <= -stepThreshold) {
                        accumulatedAngle += stepThreshold
                        hapticCallback()
                        onStep(false, currentVelocityMultiplier)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrollLockCallback(false)
                parent?.requestDisallowInterceptTouchEvent(false)
                currentVelocityMultiplier = 1
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val elapsed = System.currentTimeMillis() - downTime
                    val moved = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
                    if (dist <= innerR && elapsed < 300 && moved < dp(12)) {
                        hapticCallback()
                        onCenterTap()
                    }
                }
                onReleaseWheel?.invoke()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}

/**
 * Dual Analog Virtual Joystick View with Material 3 dynamic palette support.
 */
class VirtualJoystickView(
    context: Context,
    private val label: String,
    private var accentColorInt: Int,
    private var baseColorInt: Int,
    private val scrollLockCallback: (Boolean) -> Unit,
    private val onStickMoved: (Int, Int) -> Unit
) : View(context) {

    private var knobX = 0f
    private var knobY = 0f
    private var isTouching = false

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(10).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        basePaint.color = baseColorInt
        ringPaint.color = accentColorInt
        stickPaint.color = accentColorInt

        val cx = width / 2f
        val cy = (height / 2f) + dp(5)
        val maxR = min(width, height - dp(14)) * 0.42f
        val thumbR = maxR * 0.42f

        canvas.drawText(label, cx, dp(12).toFloat(), labelPaint)
        canvas.drawCircle(cx, cy, maxR, basePaint)
        canvas.drawCircle(cx, cy, maxR, ringPaint)

        val drawX = if (isTouching) cx + knobX else cx
        val drawY = if (isTouching) cy + knobY else cy
        canvas.drawCircle(drawX, drawY, thumbR, stickPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = (height / 2f) + dp(5)
        val maxR = min(width, height - dp(14)) * 0.42f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                scrollLockCallback(true)
                parent?.requestDisallowInterceptTouchEvent(true)
                isTouching = true
                var dx = event.x - cx
                var dy = event.y - cy
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > maxR && maxR > 0) {
                    dx = (dx / dist) * maxR
                    dy = (dy / dist) * maxR
                }
                knobX = dx
                knobY = dy
                val normX = ((dx / maxR) * 127f).roundToInt().coerceIn(-127, 127)
                val normY = ((dy / maxR) * 127f).roundToInt().coerceIn(-127, 127)
                onStickMoved(normX, normY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrollLockCallback(false)
                parent?.requestDisallowInterceptTouchEvent(false)
                isTouching = false
                knobX = 0f
                knobY = 0f
                onStickMoved(0, 0)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}
