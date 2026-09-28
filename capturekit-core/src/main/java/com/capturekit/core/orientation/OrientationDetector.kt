package com.capturekit.core.orientation

import android.content.Context
import android.graphics.Bitmap
import com.capturekit.core.orientation.internal.SensorOrientationProvider
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Public API for detecting and matching device/image orientations.
 */
class OrientationDetector(
    context: Context,
    private val levelTolerance: Float = 2.0f
) {
    private val provider = SensorOrientationProvider(context, levelTolerance)

    /**
     * Flow of continuous orientation updates.
     */
    val orientationFlow: StateFlow<DeviceOrientation> = provider.orientationFlow
    
    /**
     * The current immediate device orientation.
     */
    val currentOrientation: DeviceOrientation get() = provider.currentOrientation
    
    /**
     * The current quaternion array [w, x, y, z].
     */
    val quaternion: FloatArray get() = provider.currentQuaternion

    /**
     * Captures the current orientation to use as a reference.
     */
    fun captureReferenceOrientation(): DeviceOrientation {
        return currentOrientation
    }

    /**
     * Compares the current orientation with a reference orientation.
     */
    fun compareWith(reference: DeviceOrientation): OrientationDelta {
        val current = currentOrientation
        val pitchDelta = abs(current.pitch - reference.pitch)
        val rollDelta = abs(current.roll - reference.roll)
        val yawDelta = abs(current.yaw - reference.yaw)
        
        val totalDelta = pitchDelta + rollDelta + yawDelta
        
        val isWithinTolerance = pitchDelta <= levelTolerance && 
                                rollDelta <= levelTolerance && 
                                yawDelta <= levelTolerance

        return OrientationDelta(
            pitchDelta = pitchDelta,
            rollDelta = rollDelta,
            yawDelta = yawDelta,
            totalAngleDelta = totalDelta,
            typesMatch = current.type == reference.type,
            isWithinTolerance = isWithinTolerance
        )
    }

    /**
     * Starts listening for sensor updates.
     */
    fun start() = provider.start()

    /**
     * Stops listening for sensor updates.
     */
    fun stop() = provider.stop()

    /**
     * Cleans up resources.
     */
    fun release() = provider.release()
    
    companion object {
        /**
         * Analyzes an image bitmap for its orientation.
         */
        fun analyzeImage(bitmap: Bitmap, exifOrientation: Int = 1): ImageOrientation {
            val aspect = if (bitmap.width > bitmap.height) AspectRatioType.WIDE
                         else if (bitmap.height > bitmap.width) AspectRatioType.TALL
                         else AspectRatioType.SQUARE
                         
            val isLandscape = aspect == AspectRatioType.WIDE
            val detected = if (isLandscape) OrientationType.LANDSCAPE_LEFT else OrientationType.PORTRAIT
            
            return ImageOrientation(
                detectedOrientation = detected,
                exifRotation = exifOrientation,
                aspectRatioType = aspect,
                needsRotation = exifOrientation != 1,
                rotationToApply = 0
            )
        }

        /**
         * Checks if two orientation types match structurally (ignoring exact degrees).
         */
        fun orientationsMatch(o1: OrientationType, o2: OrientationType): Boolean {
            return o1 == o2 || (o1.isLandscape && o2.isLandscape) || (o1.isPortrait && o2.isPortrait)
        }
    }
}
