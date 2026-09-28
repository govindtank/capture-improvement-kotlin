package com.capturekit.camerax.quality

import com.capturekit.core.model.FrameAnalysisResult
import com.capturekit.core.model.QualityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quality feedback engine that evaluates incoming analysis results against a set of quality criteria.
 */
class QualityGate private constructor(private val config: QualityGateConfig) {
    private val _qualityFlow = MutableStateFlow(QualityGateStatus.NOT_READY)
    
    /**
     * Flow of quality gate status updates.
     */
    val qualityFlow: StateFlow<QualityGateStatus> = _qualityFlow.asStateFlow()
    
    private var qualityCallback: ((QualityGateStatus) -> Unit)? = null
    
    /**
     * Set a callback to receive quality gate status updates directly.
     */
    fun setQualityCallback(callback: (QualityGateStatus) -> Unit) { qualityCallback = callback }
    
    /**
     * Evaluates a frame analysis result against the configured quality criteria.
     * 
     * @param result The analysis result to evaluate.
     * @return The resulting quality status.
     */
    fun evaluate(result: FrameAnalysisResult): QualityGateStatus {
        val blurRes = result.blurResult
        val orientationRes = result.orientationResult
        val angleRes = result.angleMatchResult

        val blurPassed = blurRes == null || 
            (blurRes.score >= config.minBlurScore && !blurRes.isBlurry)
        val orientationPassed = config.requiredOrientation == null ||
            orientationRes?.type == config.requiredOrientation
        val anglePassed = angleRes == null ||
            angleRes.overallScore >= config.minAngleMatchScore
        val qualityPassed = result.overallQuality.ordinal <= config.minOverallQuality.ordinal
        
        val failureReasons = mutableListOf<String>()
        if (!blurPassed) failureReasons.add("Image is too blurry (score: ${blurRes?.score})")
        if (!orientationPassed) failureReasons.add("Wrong orientation: ${orientationRes?.type}, expected: ${config.requiredOrientation}")
        if (!anglePassed) failureReasons.add("Angle not matched (score: ${angleRes?.overallScore})")
        if (!qualityPassed) failureReasons.add("Quality too low: ${result.overallQuality}")
        
        val status = QualityGateStatus(
            isReady = blurPassed && orientationPassed && anglePassed && qualityPassed,
            blurPassed = blurPassed,
            orientationPassed = orientationPassed,
            anglePassed = anglePassed,
            qualityPassed = qualityPassed,
            currentQuality = result.overallQuality,
            failureReasons = failureReasons
        )
        _qualityFlow.value = status
        qualityCallback?.invoke(status)
        return status
    }
    
    companion object {
        val NOT_READY = QualityGateStatus(false, false, false, false, false, QualityLevel.REJECTED, listOf("Not initialized"))
        
        /**
         * Configures a new QualityGate with the specified builder block.
         */
        fun configure(block: QualityGateConfigBuilder.() -> Unit): QualityGate = QualityGate(QualityGateConfigBuilder().apply(block).build())
        
        /**
         * Creates a pre-configured QualityGate for room photography.
         */
        fun roomPhotography(): QualityGate = configure {
            minBlurScore = 80.0
            minAngleMatchScore = 0.75f
            minOverallQuality = QualityLevel.GOOD
        }
    }
}

/**
 * Status of the quality gate evaluation.
 */
data class QualityGateStatus(
    val isReady: Boolean,
    val blurPassed: Boolean,
    val orientationPassed: Boolean,
    val anglePassed: Boolean,
    val qualityPassed: Boolean,
    val currentQuality: QualityLevel,
    val failureReasons: List<String>
) {
    companion object {
        val NOT_READY = QualityGateStatus(false, false, false, false, false, QualityLevel.REJECTED, listOf("Not initialized"))
    }
}
