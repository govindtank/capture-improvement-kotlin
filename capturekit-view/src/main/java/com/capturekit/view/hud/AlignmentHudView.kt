package com.capturekit.view.hud

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import com.capturekit.core.angle.AlignmentGuidance
import com.capturekit.core.angle.AngleMatchResult
import com.capturekit.core.angle.SensorDelta
import com.capturekit.view.R

/**
 * Custom View showing directional alignment guidance and a live bubble leveler.
 */
class AlignmentHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var showHorizonLine: Boolean = true
    private var showDirectionalArrows: Boolean = true
    private var showGuidanceText: Boolean = true
    private var showAngleValues: Boolean = false
    
    private var alignedColor: Int = Color.parseColor("#4CAF50")
    private var warningColor: Int = Color.parseColor("#FFC107")
    private var errorColor: Int = Color.parseColor("#F44336")
    
    private var arrowSize: Float = 48f
    private var guidanceTextSize: Float = 16f
    
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val levelRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var currentResult: AngleMatchResult? = null
    private var currentDelta: SensorDelta? = null
    private var guidance: AlignmentGuidance? = null
    private var lastThreshold: Float = 0.80f
    
    private val arrowPath = Path()
    private val textBounds = Rect()
    
    private var pulseAnimator: ValueAnimator? = null
    private var pulseRadius: Float = 0f

    init {
        val dm = resources.displayMetrics
        val defaultArrowSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, dm)
        val defaultTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, dm)
        
        context.obtainStyledAttributes(attrs, R.styleable.AlignmentHudView).apply {
            showHorizonLine = getBoolean(R.styleable.AlignmentHudView_showHorizonLine, true)
            showDirectionalArrows = getBoolean(R.styleable.AlignmentHudView_showDirectionalArrows, true)
            showGuidanceText = getBoolean(R.styleable.AlignmentHudView_showGuidanceText, true)
            showAngleValues = getBoolean(R.styleable.AlignmentHudView_showAngleValues, false)
            alignedColor = getColor(R.styleable.AlignmentHudView_alignedColor, alignedColor)
            warningColor = getColor(R.styleable.AlignmentHudView_warningColor, warningColor)
            errorColor = getColor(R.styleable.AlignmentHudView_errorColor, errorColor)
            arrowSize = getDimension(R.styleable.AlignmentHudView_arrowSize, defaultArrowSize)
            guidanceTextSize = getDimension(R.styleable.AlignmentHudView_guidanceTextSize, defaultTextSize)
            recycle()
        }
        
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, dm)
        linePaint.color = errorColor
        
        textPaint.textSize = guidanceTextSize
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        
        arrowPaint.style = Paint.Style.FILL
        arrowPaint.color = errorColor
        
        reticlePaint.style = Paint.Style.STROKE
        reticlePaint.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, dm)
        reticlePaint.color = errorColor

        levelRingPaint.style = Paint.Style.STROKE
        levelRingPaint.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, dm)
        levelRingPaint.color = Color.WHITE

        bubblePaint.style = Paint.Style.FILL
        bubblePaint.color = Color.WHITE
    }

    /**
     * Updates the alignment state based on a full AngleMatchResult and required threshold.
     */
    fun updateAngleResult(result: AngleMatchResult, requiredThreshold: Float = 0.80f) {
        this.currentResult = result
        this.guidance = result.guidance
        this.lastThreshold = requiredThreshold
        updateColorsAndAnimations(requiredThreshold)
        post { invalidate() }
    }

    /**
     * Updates the view with sensor delta data only.
     */
    fun updateSensorDelta(delta: SensorDelta) {
        this.currentDelta = delta
        post { invalidate() }
    }

    /**
     * Resets the HUD to an initial state with no guidance.
     */
    fun reset() {
        currentResult = null
        currentDelta = null
        guidance = null
        pulseAnimator?.cancel()
        post { invalidate() }
    }

    private fun updateColorsAndAnimations(requiredThreshold: Float) {
        val result = currentResult ?: return
        
        // Aligned if the matching score meets the custom threshold
        val isAligned = result.overallScore >= requiredThreshold
        
        val color = when {
            isAligned -> alignedColor
            result.overallScore >= requiredThreshold - 0.15f -> warningColor
            else -> errorColor
        }
        
        linePaint.color = color
        arrowPaint.color = color
        reticlePaint.color = color
        
        if (result.guidance == AlignmentGuidance.HOLD_STEADY || isAligned) {
            if (pulseAnimator == null || pulseAnimator?.isRunning == false) {
                pulseAnimator = ValueAnimator.ofFloat(0f, 15f).apply {
                    duration = 800
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = LinearInterpolator()
                    addUpdateListener { 
                        pulseRadius = it.animatedValue as Float
                        post { invalidate() }
                    }
                    start()
                }
            }
        } else {
            pulseAnimator?.cancel()
            pulseRadius = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cx = width / 2f
        val cy = height / 2f
        
        // Draw reticle crosshair
        canvas.drawCircle(cx, cy, 35f + pulseRadius, reticlePaint)
        canvas.drawLine(cx - 50f, cy, cx - 20f, cy, reticlePaint)
        canvas.drawLine(cx + 20f, cy, cx + 50f, cy, reticlePaint)
        canvas.drawLine(cx, cy - 50f, cx, cy - 20f, reticlePaint)
        canvas.drawLine(cx, cy + 20f, cx, cy + 50f, reticlePaint)

        // Determine delta values (sensor vs visual homography translation)
        val hasSensor = currentResult?.sensorDelta != null
        val deltaPitch: Float
        val deltaRoll: Float
        val deltaYaw = currentResult?.sensorDelta?.yawDelta ?: currentDelta?.yawDelta ?: 0f
        
        if (hasSensor) {
            deltaPitch = currentResult?.sensorDelta?.pitchDelta ?: 0f
            deltaRoll = currentResult?.sensorDelta?.rollDelta ?: 0f
        } else {
            // For gallery references without sensors, map visual displacement (homography translation X/Y)
            // directly into virtual degrees so the leveler bubble guides the user visually!
            val tx = currentResult?.translationX ?: 0f
            val ty = currentResult?.translationY ?: 0f
            
            // A visual displacement of 0.25 (25% of screen width) shifts the bubble level by 15 degrees
            deltaRoll = -tx * 60f
            deltaPitch = ty * 60f
        }
        
        // Draw standard Leveler Bubble
        val maxOffset = 80f // Maximum offset distance from center
        val sensitivity = 4f // Pixels shifted per degree
        
        var offsetX = deltaRoll * sensitivity
        var offsetY = -deltaPitch * sensitivity // Negate so tilt down moves bubble down
        
        val distance = Math.hypot(offsetX.toDouble(), offsetY.toDouble()).toFloat()
        if (distance > maxOffset) {
            offsetX = (offsetX / distance) * maxOffset
            offsetY = (offsetY / distance) * maxOffset
        }
        
        // Outer level boundary ring
        canvas.drawCircle(cx, cy, 25f, levelRingPaint)
        
        // The bubble itself
        bubblePaint.color = if (distance < 12f) alignedColor else Color.WHITE
        bubblePaint.alpha = 200
        canvas.drawCircle(cx + offsetX, cy + offsetY, 10f, bubblePaint)
        
        if (showHorizonLine) {
            canvas.save()
            canvas.rotate(deltaRoll, cx, cy)
            canvas.drawLine(0f, cy, width.toFloat(), cy, linePaint)
            canvas.restore()
        }
        
        if (showDirectionalArrows && guidance != null) {
            drawArrowForGuidance(canvas, cx, cy)
        }
        
        if (showGuidanceText && guidance != null) {
            val text = guidance!!.description
            canvas.drawText(text, cx, cy + 120f, textPaint)
        }
        
        if (showAngleValues) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(String.format("P: %.1f", deltaPitch), 20f, 50f, textPaint)
            canvas.drawText(String.format("R: %.1f", deltaRoll), 20f, 90f, textPaint)
            canvas.drawText(String.format("Y: %.1f", deltaYaw), 20f, 130f, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }
    }

    private fun drawArrowForGuidance(canvas: Canvas, cx: Float, cy: Float) {
        val guide = guidance ?: return
        
        arrowPath.reset()
        val half = arrowSize / 2f
        
        canvas.save()
        
        when (guide) {
            AlignmentGuidance.TILT_UP, AlignmentGuidance.MOVE_UP -> canvas.translate(cx, cy - 140f)
            AlignmentGuidance.TILT_DOWN, AlignmentGuidance.MOVE_DOWN -> {
                canvas.translate(cx, cy + 140f)
                canvas.rotate(180f)
            }
            AlignmentGuidance.TURN_LEFT, AlignmentGuidance.MOVE_LEFT -> {
                canvas.translate(cx - 140f, cy)
                canvas.rotate(-90f)
            }
            AlignmentGuidance.TURN_RIGHT, AlignmentGuidance.MOVE_RIGHT -> {
                canvas.translate(cx + 140f, cy)
                canvas.rotate(90f)
            }
            else -> {
                canvas.restore()
                return
            }
        }
        
        arrowPath.moveTo(0f, -half)
        arrowPath.lineTo(half, half)
        arrowPath.lineTo(-half, half)
        arrowPath.close()
        
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
    }
}
