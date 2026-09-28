package com.capturekit.view

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import com.capturekit.core.model.FrameAnalysisResult
import com.capturekit.view.hud.AlignmentHudView
import com.capturekit.view.hud.BlurIndicatorView
import com.capturekit.view.hud.QualityBadgeView
import com.capturekit.view.overlay.GhostOverlayView
import android.util.TypedValue

/**
 * Convenience FrameLayout wrapping all overlay views.
 */
class CaptureKitOverlayLayout @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null, 
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    val ghostOverlay: GhostOverlayView
    val alignmentHud: AlignmentHudView  
    val blurIndicator: BlurIndicatorView
    val qualityBadge: QualityBadgeView
    
    init {
        val dm = resources.displayMetrics
        val margin16 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, dm).toInt()

        ghostOverlay = GhostOverlayView(context, attrs).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        
        alignmentHud = AlignmentHudView(context, attrs).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        
        blurIndicator = BlurIndicatorView(context, attrs).apply {
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.TOP or Gravity.END
            lp.setMargins(0, margin16, margin16, 0)
            layoutParams = lp
        }
        
        qualityBadge = QualityBadgeView(context, attrs).apply {
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.setMargins(0, 0, 0, margin16)
            layoutParams = lp
        }

        addView(ghostOverlay)
        addView(alignmentHud)
        addView(blurIndicator)
        addView(qualityBadge)
    }
    
    /**
     * Updates all HUD views from a FrameAnalysisResult.
     */
    fun updateFromResult(result: FrameAnalysisResult) {
        result.angleMatchResult?.let { alignmentHud.updateAngleResult(it) }
        result.blurResult?.let { blurIndicator.updateBlurResult(it) }
        qualityBadge.updateFromResult(result)
    }
    
    /**
     * Sets the reference image for the ghost overlay.
     */
    fun setReferenceImage(bitmap: Bitmap) {
        ghostOverlay.setReferenceImage(bitmap)
    }
    
    /**
     * Allows convenient configuration of the GhostOverlayView.
     */
    fun configureGhostOverlay(block: GhostOverlayView.() -> Unit) {
        ghostOverlay.block()
    }
    
    /**
     * Allows convenient configuration of the AlignmentHudView.
     */
    fun configureAlignmentHud(block: AlignmentHudView.() -> Unit) {
        alignmentHud.block()
    }
    
    /**
     * Toggles visibility of the GhostOverlayView.
     */
    fun setGhostOverlayVisible(visible: Boolean) {
        ghostOverlay.visibility = if (visible) VISIBLE else GONE
    }
    
    /**
     * Toggles visibility of the AlignmentHudView.
     */
    fun setAlignmentHudVisible(visible: Boolean) {
        alignmentHud.visibility = if (visible) VISIBLE else GONE
    }
    
    /**
     * Toggles visibility of the BlurIndicatorView.
     */
    fun setBlurIndicatorVisible(visible: Boolean) {
        blurIndicator.visibility = if (visible) VISIBLE else GONE
    }
    
    /**
     * Toggles visibility of the QualityBadgeView.
     */
    fun setQualityBadgeVisible(visible: Boolean) {
        qualityBadge.visibility = if (visible) VISIBLE else GONE
    }
    
    /**
     * Resets all internal views.
     */
    fun reset() {
        ghostOverlay.clearReferenceImage()
        alignmentHud.reset()
        blurIndicator.reset()
    }
}
