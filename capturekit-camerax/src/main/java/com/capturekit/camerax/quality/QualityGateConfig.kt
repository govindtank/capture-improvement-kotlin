package com.capturekit.camerax.quality

import com.capturekit.core.model.QualityLevel
import com.capturekit.core.orientation.OrientationType

/**
 * DSL marker for QualityGate configuration.
 */
@DslMarker
annotation class QualityGateDsl

/**
 * Builder for QualityGateConfig.
 */
@QualityGateDsl
class QualityGateConfigBuilder {
    var minBlurScore: Double = 100.0
    var requiredOrientation: OrientationType? = null
    var minAngleMatchScore: Float = 0.80f
    var minOverallQuality: QualityLevel = QualityLevel.GOOD
    var enableHapticFeedback: Boolean = false
    
    /**
     * Builds the QualityGateConfig.
     */
    fun build(): QualityGateConfig = QualityGateConfig(
        minBlurScore,
        requiredOrientation,
        minAngleMatchScore,
        minOverallQuality,
        enableHapticFeedback
    )
}

/**
 * Configuration for QualityGate.
 */
data class QualityGateConfig(
    val minBlurScore: Double,
    val requiredOrientation: OrientationType?,
    val minAngleMatchScore: Float,
    val minOverallQuality: QualityLevel,
    val enableHapticFeedback: Boolean
)
