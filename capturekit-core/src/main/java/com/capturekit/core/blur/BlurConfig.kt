package com.capturekit.core.blur

import android.graphics.RectF

@DslMarker
annotation class BlurConfigDsl

/**
 * DSL builder for configuring blur detection.
 */
@BlurConfigDsl
class BlurConfigBuilder {
    var method: BlurMethod = BlurMethod.LAPLACIAN_VARIANCE
    var threshold: Double = 100.0
    var downscaleWidth: Int = 500
    var downscaleHeight: Int = 500
    var regionOfInterest: RectF? = null
    var enableZoneAnalysis: Boolean = false
    var combinedWeightLaplacian: Float = 0.6f
    var combinedWeightTenengrad: Float = 0.4f
    
    fun build(): BlurConfig = BlurConfig(
        method = method,
        threshold = threshold,
        downscaleWidth = downscaleWidth,
        downscaleHeight = downscaleHeight,
        regionOfInterest = regionOfInterest,
        enableZoneAnalysis = enableZoneAnalysis,
        combinedWeightLaplacian = combinedWeightLaplacian,
        combinedWeightTenengrad = combinedWeightTenengrad
    )
}

/**
 * Configuration for blur detection.
 */
data class BlurConfig(
    val method: BlurMethod,
    val threshold: Double,
    val downscaleWidth: Int,
    val downscaleHeight: Int,
    val regionOfInterest: RectF?,
    val enableZoneAnalysis: Boolean,
    val combinedWeightLaplacian: Float,
    val combinedWeightTenengrad: Float
) {
    companion object {
        /**
         * Default configuration using Laplacian Variance.
         */
        val DEFAULT = BlurConfig(
            method = BlurMethod.LAPLACIAN_VARIANCE,
            threshold = 100.0,
            downscaleWidth = 500,
            downscaleHeight = 500,
            regionOfInterest = null,
            enableZoneAnalysis = false,
            combinedWeightLaplacian = 0.6f,
            combinedWeightTenengrad = 0.4f
        )

        /**
         * Preset optimized for hotel room photography.
         */
        val ROOM_PHOTOGRAPHY = BlurConfig(
            method = BlurMethod.COMBINED,
            threshold = 80.0,
            downscaleWidth = 640,
            downscaleHeight = 480,
            regionOfInterest = RectF(0.05f, 0.05f, 0.95f, 0.95f),
            enableZoneAnalysis = true,
            combinedWeightLaplacian = 0.6f,
            combinedWeightTenengrad = 0.4f
        )
    }
}
