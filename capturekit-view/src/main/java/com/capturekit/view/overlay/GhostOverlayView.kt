package com.capturekit.view.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.capturekit.core.comparison.ImageComparator
import com.capturekit.view.R

/**
 * Visualization modes for the reference image overlay.
 */
enum class GhostMode {
    NORMAL,      // Standard semi-transparent photo
    EDGES,       // Cyan Canny edge silhouette outlines
    TINTED,      // Cyan tinted semi-transparent photo
    GRAYSCALE    // Grayscale semi-transparent photo
}

/**
 * Custom View that displays a semi-transparent reference image overlaid on camera preview.
 * Includes options for alpha adjustment and displaying edge-detected silhouettes.
 */
class GhostOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var ghostAlpha: Float = 0.35f
        set(value) {
            field = value
            post { invalidate() }
        }
    var ghostScaleType: Int = 0
        set(value) {
            field = value
            updateMatrix()
            post { invalidate() }
        }
    var ghostMode: GhostMode = GhostMode.NORMAL
        set(value) {
            field = value
            if (value == GhostMode.EDGES && edgeBitmap == null) {
                referenceBitmap?.let { bmp ->
                    this.edgeBitmap = ImageComparator.extractEdges(bmp)
                }
            }
            post { invalidate() }
        }

    var showEdges: Boolean
        get() = ghostMode == GhostMode.EDGES
        set(value) {
            ghostMode = if (value) GhostMode.EDGES else GhostMode.NORMAL
        }
    var edgeColor: Int = Color.CYAN
        set(value) {
            field = value
            edgePaint.colorFilter = android.graphics.PorterDuffColorFilter(value, android.graphics.PorterDuff.Mode.SRC_IN)
            post { invalidate() }
        }
    var edgeStrokeWidth: Float = 2f
        set(value) {
            field = value
            post { invalidate() }
        }

    private var referenceBitmap: Bitmap? = null
    private var edgeBitmap: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val viewRect = RectF()
    private val bitmapRect = RectF()
    private val drawMatrix = Matrix()

    private var alphaAnimator: ValueAnimator? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.GhostOverlayView).apply {
            ghostAlpha = getFloat(R.styleable.GhostOverlayView_ghostAlpha, 0.35f)
            ghostScaleType = getInt(R.styleable.GhostOverlayView_ghostScaleType, 0)
            showEdges = getBoolean(R.styleable.GhostOverlayView_showEdges, false)
            edgeColor = getColor(R.styleable.GhostOverlayView_edgeColor, Color.CYAN)
            edgeStrokeWidth = getDimension(R.styleable.GhostOverlayView_edgeStrokeWidth, 
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics))
            recycle()
        }
        
        edgePaint.colorFilter = PorterDuffColorFilter(edgeColor, PorterDuff.Mode.SRC_IN)
    }

    /**
     * Sets the reference image to be overlaid.
     * Optionally computes edges if showEdges is enabled.
     */
    fun setReferenceImage(bitmap: Bitmap) {
        this.referenceBitmap = bitmap
        if (showEdges) {
            this.edgeBitmap = ImageComparator.extractEdges(bitmap)
        }
        updateMatrix()
        post { invalidate() }
    }

    /**
     * Clears the current reference image and edge map.
     */
    fun clearReferenceImage() {
        referenceBitmap = null
        edgeBitmap = null
        post { invalidate() }
    }

    /**
     * Animates the alpha change of the ghost image.
     */
    fun setGhostAlphaAnimated(alpha: Float, durationMs: Long = 300) {
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofFloat(this.ghostAlpha, alpha).apply {
            duration = durationMs
            addUpdateListener { animator ->
                ghostAlpha = animator.animatedValue as Float
                post { invalidate() }
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewRect.set(0f, 0f, w.toFloat(), h.toFloat())
        updateMatrix()
    }

    private fun updateMatrix() {
        val bmp = referenceBitmap ?: return
        if (viewRect.isEmpty) return
        bitmapRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        
        val scale = Math.max(viewRect.width() / bitmapRect.width(), viewRect.height() / bitmapRect.height())
        val dx = (viewRect.width() - bitmapRect.width() * scale) / 2f
        val dy = (viewRect.height() - bitmapRect.height() * scale) / 2f
        
        drawMatrix.reset()
        drawMatrix.postScale(scale, scale)
        drawMatrix.postTranslate(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = referenceBitmap ?: return

        paint.alpha = (ghostAlpha * 255).toInt()
        
        when (ghostMode) {
            GhostMode.NORMAL -> {
                paint.colorFilter = null
                canvas.drawBitmap(bmp, drawMatrix, paint)
            }
            GhostMode.EDGES -> {
                edgeBitmap?.let { edgeBmp ->
                    canvas.drawBitmap(edgeBmp, drawMatrix, edgePaint)
                }
            }
            GhostMode.TINTED -> {
                paint.colorFilter = PorterDuffColorFilter(edgeColor, PorterDuff.Mode.SRC_ATOP)
                canvas.drawBitmap(bmp, drawMatrix, paint)
            }
            GhostMode.GRAYSCALE -> {
                val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bmp, drawMatrix, paint)
            }
        }
    }
}
