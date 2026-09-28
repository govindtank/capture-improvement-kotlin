package com.capturekit.camerax.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.capturekit.camerax.analyzer.internal.YPlaneExtractor
import com.capturekit.core.angle.AngleManager
import com.capturekit.core.angle.AngleMatchResult
import com.capturekit.core.angle.FeatureDetector
import com.capturekit.core.angle.MatchingMethod
import com.capturekit.core.blur.BlurDetector
import com.capturekit.core.blur.BlurMethod
import com.capturekit.core.blur.BlurResult
import com.capturekit.core.model.FrameAnalysisResult
import com.capturekit.core.model.QualityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main real-time analysis pipeline implementing ImageAnalysis.Analyzer.
 * Analyzes incoming frames for blur, orientation, and angle matching.
 */
class CaptureAnalyzer private constructor(
    private val config: CaptureAnalyzerConfig
) : ImageAnalysis.Analyzer {
    
    private val _resultFlow = MutableStateFlow<FrameAnalysisResult?>(null)
    /**
     * Flow of analysis results.
     */
    val resultFlow: StateFlow<FrameAnalysisResult?> = _resultFlow.asStateFlow()
    
    /**
     * The latest available analysis result.
     */
    val latestResult: FrameAnalysisResult? get() = _resultFlow.value
    
    private var resultCallback: ((FrameAnalysisResult) -> Unit)? = null
    
    /**
     * Set a callback to receive analysis results directly.
     */
    fun setResultCallback(callback: (FrameAnalysisResult) -> Unit) { resultCallback = callback }
    
    private var frameIndex: Long = 0
    private var blurDetector: BlurDetector? = null
    private var angleManager: AngleManager? = null
    
    // Pre-allocated buffers
    private var yBuffer: ByteArray? = null
    private var reusableMat: org.opencv.core.Mat? = null
    
    // Rate limiting
    private var lastAnalyzedTimestamp = 0L
    private val frameIntervalMs = 1000L / config.targetAnalysisFps
    
    // Initialize in init block based on config
    init {
        if (config.blurEnabled) {
            blurDetector = BlurDetector.configure {
                method = config.blurConfig.method
                threshold = config.blurConfig.threshold
                enableZoneAnalysis = config.blurConfig.enableZoneAnalysis
            }
        }
        if (config.angleEnabled && config.angleConfig != null) {
            angleManager = AngleManager.configure {
                matchingMethod = config.angleConfig.matchingMethod
                featureDetector = config.angleConfig.featureDetector
                angleTolerance = config.angleConfig.angleTolerance
                minFeatureMatches = config.angleConfig.minFeatureMatches
            }
            config.referenceImage?.let { ref ->
                angleManager?.setReferenceImage(ref, config.referenceSensorData)
            }
        }
    }
    
    /**
     * Analyzes an incoming camera frame.
     */
    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTimestamp < frameIntervalMs) {
            image.close()
            return
        }
        lastAnalyzedTimestamp = now
        
        try {
            val result = processFrame(image)
            _resultFlow.value = result
            resultCallback?.invoke(result)
            frameIndex++
        } finally {
            image.close()
        }
    }
    
    private fun processFrame(image: ImageProxy): FrameAnalysisResult {
        // 1. Extract Y-plane
        val (grayBytes, width, height) = YPlaneExtractor.extract(image, yBuffer)
        yBuffer = grayBytes
        
        // 2. Blur detection
        var blurResult: BlurResult? = null
        if (config.blurEnabled && blurDetector != null) {
            blurResult = blurDetector!!.analyze(grayBytes, width, height)
        }
        
        // 3. Angle matching
        var angleResult: AngleMatchResult? = null
        if (config.angleEnabled && angleManager != null && angleManager!!.hasReference) {
            val mat = YPlaneExtractor.extractToMat(image, reusableMat)
            reusableMat = mat
            angleResult = angleManager!!.compareFrame(mat, null)
        }
        
        // 4. Determine quality
        val quality = determineQuality(blurResult, angleResult)
        
        return FrameAnalysisResult(
            blurResult = blurResult,
            orientationResult = null,
            angleMatchResult = angleResult,
            overallQuality = quality,
            timestamp = System.currentTimeMillis(),
            frameIndex = frameIndex
        )
    }
    
    private fun determineQuality(blur: BlurResult?, angle: AngleMatchResult?): QualityLevel {
        val blurOk = blur == null || !blur.isBlurry
        val angleOk = angle == null || angle.isAligned
        val angleClose = angle == null || angle.overallScore >= 0.6f
        
        return when {
            blurOk && angleOk -> if ((angle?.overallScore ?: 1.0f) > 0.9f) QualityLevel.EXCELLENT else QualityLevel.GOOD
            blurOk && angleClose -> QualityLevel.ACCEPTABLE
            !blurOk && (blur?.confidence ?: 0f) < 0.2f -> QualityLevel.REJECTED
            else -> QualityLevel.POOR
        }
    }
    
    /**
     * Updates the reference image used for angle matching.
     */
    fun updateReferenceImage(bitmap: android.graphics.Bitmap, sensorOrientation: FloatArray? = null) {
        angleManager?.setReferenceImage(bitmap, sensorOrientation)
    }
    
    /**
     * Clears the current reference image.
     */
    fun clearReference() { angleManager?.clearReference() }
    
    /**
     * Releases resources held by the analyzer.
     */
    fun release() {
        reusableMat?.release()
        angleManager?.release()
    }
    
    companion object {
        /**
         * Creates a CaptureAnalyzer with the specified configuration block.
         */
        fun configure(block: CaptureAnalyzerConfigBuilder.() -> Unit): CaptureAnalyzer {
            val config = CaptureAnalyzerConfigBuilder().apply(block).build()
            return CaptureAnalyzer(config)
        }
        
        /**
         * Creates a pre-configured CaptureAnalyzer for room photography.
         */
        fun roomPhotography(referenceImage: android.graphics.Bitmap? = null): CaptureAnalyzer {
            return configure {
                targetAnalysisFps = 10
                blur {
                    method = BlurMethod.COMBINED
                    threshold = 80.0
                    enableZoneAnalysis = true
                }
                orientation {
                    enabled = true
                }
                if (referenceImage != null) {
                    angleMatching {
                        this.referenceImage = referenceImage
                        matchingMethod = MatchingMethod.HYBRID
                        featureDetector = FeatureDetector.AKAZE
                    }
                }
            }
        }
    }
}
