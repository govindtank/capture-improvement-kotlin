package com.capturekit.view.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.capturekit.core.blur.BlurResult
import com.capturekit.view.R

/**
 * Custom View showing blur analysis indication.
 */
class BlurIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var showScore: Boolean = true
    private var showLabel: Boolean = true
    private var indicatorSize: Float = 40f
    
    private var sharpColor: Int = Color.parseColor("#4CAF50")
    private var blurryColor: Int = Color.parseColor("#F44336")
    private var borderlineColor: Int = Color.parseColor("#FFC107")
    
    private var blurIndicatorStyle: Int = 0 // 0=badge, 1=bar, 2=ring
    
    private var currentResult: BlurResult? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    init {
        val dm = resources.displayMetrics
        val defaultSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, dm)
        
        context.obtainStyledAttributes(attrs, R.styleable.BlurIndicatorView).apply {
            showScore = getBoolean(R.styleable.BlurIndicatorView_showScore, true)
            showLabel = getBoolean(R.styleable.BlurIndicatorView_showLabel, true)
            indicatorSize = getDimension(R.styleable.BlurIndicatorView_indicatorSize, defaultSize)
            sharpColor = getColor(R.styleable.BlurIndicatorView_sharpColor, sharpColor)
            blurryColor = getColor(R.styleable.BlurIndicatorView_blurryColor, blurryColor)
            borderlineColor = getColor(R.styleable.BlurIndicatorView_borderlineColor, borderlineColor)
            blurIndicatorStyle = getInt(R.styleable.BlurIndicatorView_blurIndicatorStyle, 0)
            recycle()
        }
        
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, dm)
    }

    /**
     * Updates the UI with the latest blur result.
     */
    fun updateBlurResult(result: BlurResult) {
        this.currentResult = result
        post { invalidate() }
    }

    /**
     * Resets the indicator.
     */
    fun reset() {
        this.currentResult = null
        post { invalidate() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (indicatorSize * 2.5).toInt()
        val desiredHeight = indicatorSize.toInt()
        
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> Math.min(desiredWidth, widthSize)
            else -> desiredWidth
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> Math.min(desiredHeight, heightSize)
            else -> desiredHeight
        }
        
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = currentResult ?: return
        
        paint.color = if (!result.isBlurry) sharpColor else blurryColor
        
        val cx = width / 2f
        val cy = height / 2f
        
        when (blurIndicatorStyle) {
            0 -> { // Badge
                rectF.set(0f, 0f, width.toFloat(), height.toFloat())
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectF, height / 2f, height / 2f, paint)
                
                if (showScore) {
                    canvas.drawText(String.format("%.1f", result.score), cx, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
                }
            }
            1 -> { // Bar
                rectF.set(0f, cy - 10f, width.toFloat() * (result.score / 100.0).coerceIn(0.0, 1.0).toFloat(), cy + 10f)
                paint.style = Paint.Style.FILL
                canvas.drawRect(rectF, paint)
            }
            2 -> { // Ring
                val radius = Math.min(width, height) / 2f - 4f
                rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                canvas.drawArc(rectF, -90f, 360f * (result.score / 100.0).coerceIn(0.0, 1.0).toFloat(), false, paint)
            }
        }
    }
}
