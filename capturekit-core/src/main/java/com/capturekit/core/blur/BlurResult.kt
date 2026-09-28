package com.capturekit.core.blur

/**
 * Represents a region of the image for detailed blur analysis.
 */
enum class Zone {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

/**
 * The result of a blur detection analysis.
 *
 * @property score Raw blur metric value (higher = sharper).
 * @property isBlurry True if the image is considered blurry based on the threshold.
 * @property confidence 0.0 (definitely blurry) to 1.0 (definitely sharp).
 * @property method Algorithm used for this result.
 * @property threshold Threshold that was applied.
 * @property analysisTimeMs Processing time in milliseconds.
 * @property zoneScores Optional per-region blur scores.
 */
data class BlurResult(
    val score: Double,
    val isBlurry: Boolean,
    val confidence: Float,
    val method: BlurMethod,
    val threshold: Double,
    val analysisTimeMs: Long,
    val zoneScores: Map<Zone, Double>? = null
)
