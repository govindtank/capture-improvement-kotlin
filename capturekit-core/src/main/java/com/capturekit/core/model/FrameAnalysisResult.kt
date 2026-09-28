package com.capturekit.core.model

import com.capturekit.core.angle.AngleMatchResult
// Assuming these classes exist, otherwise we use Any/placeholders. 
// For now, importing fully qualified to match request
// import com.capturekit.core.blur.BlurResult
// import com.capturekit.core.orientation.DeviceOrientation

/**
 * Represents the combined analysis result for a single camera frame.
 */
data class FrameAnalysisResult(
    val blurResult: com.capturekit.core.blur.BlurResult?,
    val orientationResult: com.capturekit.core.orientation.DeviceOrientation?,
    val angleMatchResult: com.capturekit.core.angle.AngleMatchResult?,
    val overallQuality: QualityLevel,
    val timestamp: Long,
    val frameIndex: Long
) {
    /**
     * Determines whether the frame is suitable for automatic capture.
     */
    val isReadyToCapture: Boolean
        get() = overallQuality.isGood && 
                blurResult?.isBlurry != true && 
                angleMatchResult?.isAligned != false
}
