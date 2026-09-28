package com.capturekit.core.angle.internal

import com.capturekit.core.angle.AlignmentGuidance
import com.capturekit.core.angle.AngleConfig
import com.capturekit.core.angle.SensorDelta

/**
 * Internal class responsible for comparing sensor orientations.
 */
internal class SensorAngleComparer(private val config: AngleConfig) {

    /**
     * Compares reference and current quaternions and returns the orientation delta.
     */
    fun compare(referenceQuaternion: FloatArray, currentQuaternion: FloatArray): SensorDelta {
        val relQ = QuaternionUtils.relativeQuaternion(referenceQuaternion, currentQuaternion)
        val euler = QuaternionUtils.toEulerAngles(relQ)
        val totalAngle = QuaternionUtils.angularDifference(referenceQuaternion, currentQuaternion)

        return SensorDelta(
            pitchDelta = euler[0],
            rollDelta = euler[1],
            yawDelta = euler[2],
            totalAngle = totalAngle
        )
    }

    /**
     * Determines the primary guidance based on the sensor delta.
     */
    fun determineGuidance(delta: SensorDelta, toleranceDegrees: Float): AlignmentGuidance {
        if (delta.totalAngle < toleranceDegrees) {
            return AlignmentGuidance.ALIGNED
        }
        if (delta.totalAngle < toleranceDegrees * 2) {
            return AlignmentGuidance.HOLD_STEADY
        }

        // Find largest delta
        val absPitch = Math.abs(delta.pitchDelta)
        val absRoll = Math.abs(delta.rollDelta)
        val absYaw = Math.abs(delta.yawDelta)

        return if (absPitch >= absRoll && absPitch >= absYaw) {
            if (delta.pitchDelta > 0) AlignmentGuidance.TILT_DOWN else AlignmentGuidance.TILT_UP
        } else if (absRoll >= absPitch && absRoll >= absYaw) {
            if (delta.rollDelta > 0) AlignmentGuidance.ROTATE_COUNTER_CLOCKWISE else AlignmentGuidance.ROTATE_CLOCKWISE
        } else {
            if (delta.yawDelta > 0) AlignmentGuidance.TURN_RIGHT else AlignmentGuidance.TURN_LEFT
        }
    }

    /**
     * Determines all applicable guidances based on the sensor delta.
     */
    fun determineAllGuidances(delta: SensorDelta, toleranceDegrees: Float): List<AlignmentGuidance> {
        val guidances = mutableListOf<AlignmentGuidance>()

        if (delta.totalAngle < toleranceDegrees) {
            guidances.add(AlignmentGuidance.ALIGNED)
            return guidances
        }
        if (delta.totalAngle < toleranceDegrees * 2) {
            guidances.add(AlignmentGuidance.HOLD_STEADY)
        }

        if (Math.abs(delta.pitchDelta) > toleranceDegrees) {
            guidances.add(if (delta.pitchDelta > 0) AlignmentGuidance.TILT_DOWN else AlignmentGuidance.TILT_UP)
        }
        if (Math.abs(delta.rollDelta) > toleranceDegrees) {
            guidances.add(if (delta.rollDelta > 0) AlignmentGuidance.ROTATE_COUNTER_CLOCKWISE else AlignmentGuidance.ROTATE_CLOCKWISE)
        }
        if (Math.abs(delta.yawDelta) > toleranceDegrees) {
            guidances.add(if (delta.yawDelta > 0) AlignmentGuidance.TURN_RIGHT else AlignmentGuidance.TURN_LEFT)
        }
        
        if (guidances.isEmpty()) {
             guidances.add(determineGuidance(delta, toleranceDegrees))
        }

        return guidances
    }
}
