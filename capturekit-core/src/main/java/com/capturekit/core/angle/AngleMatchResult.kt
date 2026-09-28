package com.capturekit.core.angle

/**
 * Represents the result of comparing the current pose against a reference pose.
 */
data class AngleMatchResult(
    /** Overall score from 0.0 (no match) to 1.0 (perfect match) */
    val overallScore: Float,
    
    /** True if the device is considered aligned according to the thresholds */
    val isAligned: Boolean,
    
    /** Delta between sensor orientations, if available */
    val sensorDelta: SensorDelta?,
    
    /** Score of feature matching, if available */
    val featureMatchScore: Float?,
    
    /** Number of matching features found, if available */
    val featureMatchCount: Int?,
    
    /** Structural Similarity Index (SSIM) score, if calculated */
    val ssimScore: Float?,
    
    /** Primary guidance to provide to the user */
    val guidance: AlignmentGuidance,
    
    /** All applicable guidances (e.g., could need to tilt and move) */
    val allGuidances: List<AlignmentGuidance>,
    
    /** True if overall quality is good enough to automatically capture */
    val readyToCapture: Boolean,
    
    /** Time taken to perform the analysis in milliseconds */
    val analysisTimeMs: Long,
    
    /** Method used for this result */
    val matchingMethod: MatchingMethod,
    
    /** Horizontal displacement ratio from homography (-0.5 to 0.5) */
    val translationX: Float = 0f,
    
    /** Vertical displacement ratio from homography (-0.5 to 0.5) */
    val translationY: Float = 0f
)

/**
 * Represents the difference in orientation based on device sensors.
 */
data class SensorDelta(
    /** Pitch difference in degrees */
    val pitchDelta: Float,
    /** Roll difference in degrees */
    val rollDelta: Float,
    /** Yaw difference in degrees */
    val yawDelta: Float,
    /** Total combined angular difference in degrees */
    val totalAngle: Float
) {
    /**
     * Checks if the total angular difference is within the specified tolerance.
     *
     * @param toleranceDegrees The maximum acceptable difference in degrees.
     * @return True if within tolerance.
     */
    fun isWithinTolerance(toleranceDegrees: Float): Boolean = totalAngle < toleranceDegrees
}
