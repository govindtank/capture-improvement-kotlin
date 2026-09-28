package com.capturekit.camerax.analyzer

import com.capturekit.core.angle.AngleConfig
import com.capturekit.core.angle.AngleConfigBuilder
import com.capturekit.core.angle.FeatureDetector
import com.capturekit.core.angle.MatchingMethod
import com.capturekit.core.blur.BlurConfig
import com.capturekit.core.blur.BlurConfigBuilder
import com.capturekit.core.orientation.OrientationType

/**
 * DSL marker for capture analyzer configuration.
 */
@DslMarker
annotation class CaptureAnalyzerDsl

/**
 * Builder for CaptureAnalyzerConfig.
 */
@CaptureAnalyzerDsl
class CaptureAnalyzerConfigBuilder {
    var targetAnalysisFps: Int = 15
    var analysisResolution: android.util.Size = android.util.Size(640, 480)
    
    // Blur sub-config
    private var blurConfig: BlurConfig? = null
    private var blurEnabled: Boolean = false
    
    /**
     * Configure blur detection.
     */
    fun blur(block: BlurConfigBuilder.() -> Unit) {
        blurEnabled = true
        blurConfig = BlurConfigBuilder().apply(block).build()
    }
    
    // Orientation sub-config
    private var orientationEnabled: Boolean = false
    private var requiredOrientation: OrientationType? = null
    
    /**
     * Configure orientation tracking.
     */
    fun orientation(block: OrientationBlock.() -> Unit) {
        this@CaptureAnalyzerConfigBuilder.orientationEnabled = true
        OrientationBlock().apply(block)
    }
    
    @CaptureAnalyzerDsl
    inner class OrientationBlock {
        var enabled: Boolean
            get() = this@CaptureAnalyzerConfigBuilder.orientationEnabled
            set(value) { this@CaptureAnalyzerConfigBuilder.orientationEnabled = value }
        var required: OrientationType?
            get() = this@CaptureAnalyzerConfigBuilder.requiredOrientation
            set(value) { this@CaptureAnalyzerConfigBuilder.requiredOrientation = value }
    }
    
    // Angle matching sub-config
    private var angleConfig: AngleConfig? = null
    private var angleEnabled: Boolean = false
    private var referenceImage: android.graphics.Bitmap? = null
    private var referenceSensorData: FloatArray? = null
    
    /**
     * Configure angle matching.
     */
    fun angleMatching(block: AngleMatchingBlock.() -> Unit) {
        this@CaptureAnalyzerConfigBuilder.angleEnabled = true
        val builder = AngleMatchingBlock().apply(block)
        angleConfig = AngleConfigBuilder().apply {
            matchingMethod = builder.matchingMethod
            featureDetector = builder.featureDetector
            angleTolerance = builder.angleTolerance
            minFeatureMatches = builder.minFeatureMatches
        }.build()
    }
    
    @CaptureAnalyzerDsl
    inner class AngleMatchingBlock {
        var enabled: Boolean
            get() = this@CaptureAnalyzerConfigBuilder.angleEnabled
            set(value) { this@CaptureAnalyzerConfigBuilder.angleEnabled = value }
        var referenceImage: android.graphics.Bitmap?
            get() = this@CaptureAnalyzerConfigBuilder.referenceImage
            set(value) { this@CaptureAnalyzerConfigBuilder.referenceImage = value }
        var referenceSensorData: FloatArray?
            get() = this@CaptureAnalyzerConfigBuilder.referenceSensorData
            set(value) { this@CaptureAnalyzerConfigBuilder.referenceSensorData = value }
        var matchingMethod: MatchingMethod = MatchingMethod.HYBRID
        var featureDetector: FeatureDetector = FeatureDetector.AKAZE
        var angleTolerance: Float = 5.0f
        var minFeatureMatches: Int = 20
    }
    
    /**
     * Builds the final CaptureAnalyzerConfig.
     */
    fun build(): CaptureAnalyzerConfig {
        val resolvedAngleConfig = if (angleEnabled) {
            angleConfig ?: AngleConfig.ROOM_PHOTOGRAPHY
        } else null
        
        return CaptureAnalyzerConfig(
            targetAnalysisFps = targetAnalysisFps,
            analysisResolution = analysisResolution,
            blurEnabled = blurEnabled,
            blurConfig = blurConfig ?: BlurConfig.DEFAULT,
            orientationEnabled = orientationEnabled,
            requiredOrientation = requiredOrientation,
            angleEnabled = angleEnabled,
            angleConfig = resolvedAngleConfig,
            referenceImage = referenceImage,
            referenceSensorData = referenceSensorData
        )
    }
}

/**
 * Configuration for CaptureAnalyzer.
 */
data class CaptureAnalyzerConfig(
    val targetAnalysisFps: Int,
    val analysisResolution: android.util.Size,
    val blurEnabled: Boolean,
    val blurConfig: BlurConfig,
    val orientationEnabled: Boolean,
    val requiredOrientation: OrientationType?,
    val angleEnabled: Boolean,
    val angleConfig: AngleConfig?,
    val referenceImage: android.graphics.Bitmap?,
    val referenceSensorData: FloatArray?
)
