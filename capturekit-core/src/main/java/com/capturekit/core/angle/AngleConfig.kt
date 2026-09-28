package com.capturekit.core.angle

/**
 * Configuration builder for angle and pose matching.
 */
class AngleConfigBuilder {
    var matchingMethod: MatchingMethod = MatchingMethod.HYBRID
    var featureDetector: FeatureDetector = FeatureDetector.ORB
    var angleTolerance: Float = 5.0f
    var translationTolerance: Float = 0.15f
    var minFeatureMatches: Int = 20
    var ssimThreshold: Float = 0.70f
    var enableSsimVerification: Boolean = true
    var maxFeatures: Int = 500
    var analysisResolutionWidth: Int = 640
    var analysisResolutionHeight: Int = 480

    fun build(): AngleConfig = AngleConfig(
        matchingMethod,
        featureDetector,
        angleTolerance,
        translationTolerance,
        minFeatureMatches,
        ssimThreshold,
        enableSsimVerification,
        maxFeatures,
        analysisResolutionWidth,
        analysisResolutionHeight
    )
}

/**
 * Configuration for the angle and pose matching pipeline.
 */
data class AngleConfig(
    val matchingMethod: MatchingMethod,
    val featureDetector: FeatureDetector,
    val angleTolerance: Float,
    val translationTolerance: Float,
    val minFeatureMatches: Int,
    val ssimThreshold: Float,
    val enableSsimVerification: Boolean,
    val maxFeatures: Int,
    val analysisResolutionWidth: Int,
    val analysisResolutionHeight: Int
) {
    companion object {
        /** Default configuration for angle matching */
        val DEFAULT = AngleConfigBuilder().build()
        
        /** Preset optimized for room photography */
        val ROOM_PHOTOGRAPHY = AngleConfigBuilder().apply {
            matchingMethod = MatchingMethod.HYBRID
            featureDetector = FeatureDetector.ORB
            angleTolerance = 5.0f
            translationTolerance = 0.15f
            minFeatureMatches = 15 // Lowered to tolerate moving/shifted objects in re-photography
            ssimThreshold = 0.70f
            enableSsimVerification = false // Disabled SSIM to prevent pixel-by-pixel mismatch when objects move
            maxFeatures = 800
        }.build()
    }
}
