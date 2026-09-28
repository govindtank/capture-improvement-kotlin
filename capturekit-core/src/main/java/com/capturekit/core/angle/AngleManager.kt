package com.capturekit.core.angle

import android.graphics.Bitmap
import com.capturekit.core.angle.internal.FeatureAngleComparer
import com.capturekit.core.angle.internal.FeatureMatchResult
import com.capturekit.core.angle.internal.SensorAngleComparer
import com.capturekit.core.comparison.ImageComparator
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * Orchestrates the angle and pose matching pipeline.
 */
class AngleManager private constructor(private val config: AngleConfig) {

    private val sensorComparer = SensorAngleComparer(config)
    private val featureComparer = FeatureAngleComparer(config)
    
    private var referenceSensorOrientation: FloatArray? = null
    private var referenceBitmap: Bitmap? = null

    /**
     * Checks if a reference image/orientation has been set.
     */
    val hasReference: Boolean
        get() = referenceBitmap != null

    /**
     * Sets the reference image and optional sensor orientation.
     */
    fun setReferenceImage(bitmap: Bitmap, sensorOrientation: FloatArray? = null) {
        referenceBitmap = bitmap
        referenceSensorOrientation = sensorOrientation?.clone()
        if (config.matchingMethod == MatchingMethod.HYBRID || config.matchingMethod == MatchingMethod.VISION_ONLY) {
            featureComparer.setReference(bitmap)
        }
    }

    /**
     * Clears the current reference.
     */
    fun clearReference() {
        referenceBitmap = null
        referenceSensorOrientation = null
        featureComparer.release() // Optional: clear reference
    }

    /**
     * Compares the current frame (as Bitmap) against the reference.
     */
    fun compareFrame(frameBitmap: Bitmap, currentSensorOrientation: FloatArray? = null): AngleMatchResult {
        val startTime = System.currentTimeMillis()
        var overallScore = 0f
        var isAligned = false
        var sensorDelta: SensorDelta? = null
        var featureMatchScore: Float? = null
        var featureMatchCount: Int? = null
        var ssimScore: Float? = null
        val allGuidances = mutableListOf<AlignmentGuidance>()
        var primaryGuidance = AlignmentGuidance.ALIGNED
        var readyToCapture = false
        var translationX = 0f
        var translationY = 0f

        val hasSensor = referenceSensorOrientation != null && currentSensorOrientation != null

        // 1. Dynamic Weight Assignment
        var totalWeight = 0f
        var sensorWeight = 0f
        var visionWeight = 0f
        var ssimWeight = 0f

        when (config.matchingMethod) {
            MatchingMethod.SENSOR_ONLY -> {
                if (hasSensor) {
                    sensorWeight = 1.0f
                    totalWeight = 1.0f
                }
            }
            MatchingMethod.VISION_ONLY -> {
                visionWeight = if (config.enableSsimVerification) 0.8f else 1.0f
                ssimWeight = if (config.enableSsimVerification) 0.2f else 0.0f
                totalWeight = visionWeight + ssimWeight
            }
            MatchingMethod.HYBRID -> {
                if (hasSensor) {
                    sensorWeight = 0.4f
                    visionWeight = 0.4f
                    ssimWeight = if (config.enableSsimVerification) 0.2f else 0.0f
                    totalWeight = sensorWeight + visionWeight + ssimWeight
                } else {
                    // Gracefully drop to Vision-Only weights when sensor info is absent (e.g. gallery images)
                    visionWeight = if (config.enableSsimVerification) 0.8f else 1.0f
                    ssimWeight = if (config.enableSsimVerification) 0.2f else 0.0f
                    totalWeight = visionWeight + ssimWeight
                }
            }
        }

        // 2. Sensor comparison
        if (sensorWeight > 0f && hasSensor) {
            val delta = sensorComparer.compare(referenceSensorOrientation!!, currentSensorOrientation!!)
            sensorDelta = delta
            val guidances = sensorComparer.determineAllGuidances(delta, config.angleTolerance)
            allGuidances.addAll(guidances)
            
            val sScore = Math.max(0f, 1.0f - delta.totalAngle / (config.angleTolerance * 3))
            overallScore += sScore * sensorWeight
        }

        // 3. Feature matching
        if (visionWeight > 0f) {
            val featureResult = featureComparer.compare(frameBitmap)
            featureMatchScore = featureResult.score
            featureMatchCount = featureResult.matchedFeatureCount
            translationX = featureResult.translationX
            translationY = featureResult.translationY
            allGuidances.addAll(featureResult.spatialGuidances)
            overallScore += featureResult.score * visionWeight
        }

        // Determine primary guidance
        if (allGuidances.isNotEmpty()) {
            primaryGuidance = allGuidances.first()
        }

        // 4. SSIM verification
        if (ssimWeight > 0f && referenceBitmap != null && 
            (primaryGuidance == AlignmentGuidance.ALIGNED || primaryGuidance == AlignmentGuidance.HOLD_STEADY)) {
            val ssim = ImageComparator.ssim(referenceBitmap!!, frameBitmap)
            ssimScore = ssim.score
            overallScore += ssim.score * ssimWeight
        } else if (ssimWeight > 0f) {
            // Scale vision score to fill SSIM gap when not aligned yet to prevent artificial score drop
            featureMatchScore?.let {
                overallScore += it * ssimWeight
            }
        }

        // 5. Normalize overall score
        if (totalWeight > 0f) {
            overallScore /= totalWeight
        }

        isAligned = primaryGuidance == AlignmentGuidance.ALIGNED || primaryGuidance == AlignmentGuidance.HOLD_STEADY

        // Evaluate ready to capture criteria
        if (config.matchingMethod == MatchingMethod.HYBRID) {
            val sensorCriteria = !hasSensor || (sensorDelta?.isWithinTolerance(config.angleTolerance) == true)
            readyToCapture = overallScore >= 0.80f && sensorCriteria && (featureMatchCount ?: 0) >= config.minFeatureMatches
        } else if (config.matchingMethod == MatchingMethod.VISION_ONLY) {
            readyToCapture = overallScore >= 0.80f && (featureMatchCount ?: 0) >= config.minFeatureMatches
        } else {
            readyToCapture = sensorDelta?.isWithinTolerance(config.angleTolerance) == true
        }

        if (readyToCapture) {
            primaryGuidance = AlignmentGuidance.ALIGNED
        }

        return AngleMatchResult(
            overallScore = overallScore,
            isAligned = isAligned,
            sensorDelta = sensorDelta,
            featureMatchScore = featureMatchScore,
            featureMatchCount = featureMatchCount,
            ssimScore = ssimScore,
            guidance = primaryGuidance,
            allGuidances = allGuidances.distinct(),
            readyToCapture = readyToCapture,
            analysisTimeMs = System.currentTimeMillis() - startTime,
            matchingMethod = config.matchingMethod,
            translationX = translationX,
            translationY = translationY
        )
    }

    /**
     * Compares the current frame (as Mat) against the reference.
     */
    fun compareFrame(grayMat: Mat, currentSensorOrientation: FloatArray? = null): AngleMatchResult {
        // We'll reuse the logic, but for feature extraction we'll pass Mat directly.
        // For SSIM we would need to convert to Bitmap or implement Mat-based SSIM.
        // For this assignment we'll convert Mat to Bitmap.
        val bmp = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(grayMat, bmp)
        val res = compareFrame(bmp, currentSensorOrientation)
        bmp.recycle()
        return res
    }

    /**
     * Releases resources.
     */
    fun release() {
        featureComparer.release()
        referenceBitmap = null
    }

    companion object {
        fun configure(block: AngleConfigBuilder.() -> Unit): AngleManager {
            val builder = AngleConfigBuilder()
            builder.block()
            return AngleManager(builder.build())
        }

        fun roomPhotography(): AngleManager {
            return AngleManager(AngleConfig.ROOM_PHOTOGRAPHY)
        }
    }
}
