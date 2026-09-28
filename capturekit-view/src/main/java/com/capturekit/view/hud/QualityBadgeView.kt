package com.capturekit.view.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.capturekit.core.model.FrameAnalysisResult
import com.capturekit.core.model.QualityLevel
import com.capturekit.view.R

/**
 * Custom View indicating overall capture quality.
 */
class QualityBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var badgeSize: Float = 56f
    private var excellentColor: Int = Color.parseColor("#4CAF50")
    private var goodColor: Int = Color.parseColor("#8BC34A")
    private var poorColor: Int = Color.parseColor("#F44336")
    private var showQualityLabel: Boolean = true
    
    private var currentQuality: QualityLevel? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val dm = resources.displayMetrics
        val defaultSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, dm)
        
        context.obtainStyledAttributes(attrs, R.styleable.QualityBadgeView).apply {
            badgeSize = getDimension(R.styleable.QualityBadgeView_badgeSize, defaultSize)
            excellentColor = getColor(R.styleable.QualityBadgeView_excellentColor, excellentColor)
            goodColor = getColor(R.styleable.QualityBadgeView_goodColor, goodColor)
            poorColor = getColor(R.styleable.QualityBadgeView_poorColor, poorColor)
            showQualityLabel = getBoolean(R.styleable.QualityBadgeView_showQualityLabel, true)
            recycle()
        }
        
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, dm)
    }

    /**
     * Updates the current quality level to display.
     */
    fun updateQuality(quality: QualityLevel) {
        this.currentQuality = quality
        post { invalidate() }
    }

    /**
     * Updates quality level from a complete FrameAnalysisResult.
     */
    fun updateFromResult(result: FrameAnalysisResult) {
        updateQuality(result.overallQuality)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = badgeSize.toInt()
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val quality = currentQuality ?: return
        
        paint.color = when (quality) {
            QualityLevel.EXCELLENT -> excellentColor
            QualityLevel.GOOD -> goodColor
            else -> poorColor
        }
        
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy)
        
        canvas.drawCircle(cx, cy, radius, paint)
        
        if (showQualityLabel) {
            val text = quality.name
            canvas.drawText(text, cx, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
    }
}
