package com.capturekit.core.angle.internal

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Utility functions for working with quaternions.
 * Format is [w, x, y, z].
 */
internal object QuaternionUtils {
    
    /**
     * Computes the angular difference between two quaternions in degrees.
     */
    fun angularDifference(q1: FloatArray, q2: FloatArray): Float {
        val dot = dot(q1, q2)
        val absDot = min(1.0f, max(-1.0f, Math.abs(dot)))
        return (2.0 * acos(absDot.toDouble()) * 180.0 / Math.PI).toFloat()
    }

    /**
     * Computes the relative quaternion from qRef to qLive.
     * Δq = qRef⁻¹ ⊗ qLive
     */
    fun relativeQuaternion(qRef: FloatArray, qLive: FloatArray): FloatArray {
        val qRefInverse = inverse(qRef)
        return multiply(qRefInverse, qLive)
    }

    /**
     * Extracts Euler angles (pitch, roll, yaw) in degrees from a quaternion.
     * Returns float array of [pitch, roll, yaw].
     */
    fun toEulerAngles(q: FloatArray): FloatArray {
        val w = q[0]
        val x = q[1]
        val y = q[2]
        val z = q[3]

        // Roll (x-axis rotation)
        val sinr_cosp = 2.0 * (w * x + y * z)
        val cosr_cosp = 1.0 - 2.0 * (x * x + y * y)
        val roll = atan2(sinr_cosp, cosr_cosp)

        // Pitch (y-axis rotation)
        val sinp = 2.0 * (w * y - z * x)
        val pitch = if (Math.abs(sinp) >= 1.0) {
            Math.copySign(Math.PI / 2.0, sinp) // use 90 degrees if out of range
        } else {
            asin(sinp)
        }

        // Yaw (z-axis rotation)
        val siny_cosp = 2.0 * (w * z + x * y)
        val cosy_cosp = 1.0 - 2.0 * (y * y + z * z)
        val yaw = atan2(siny_cosp, cosy_cosp)

        return floatArrayOf(
            (pitch * 180.0 / Math.PI).toFloat(),
            (roll * 180.0 / Math.PI).toFloat(),
            (yaw * 180.0 / Math.PI).toFloat()
        )
    }

    /**
     * Computes the inverse of a quaternion.
     */
    fun inverse(q: FloatArray): FloatArray {
        val norm2 = q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]
        if (norm2 == 0.0f) return floatArrayOf(1f, 0f, 0f, 0f)
        val invNorm2 = 1.0f / norm2
        return floatArrayOf(
            q[0] * invNorm2,
            -q[1] * invNorm2,
            -q[2] * invNorm2,
            -q[3] * invNorm2
        )
    }

    /**
     * Multiplies two quaternions q1 and q2.
     */
    fun multiply(q1: FloatArray, q2: FloatArray): FloatArray {
        val w1 = q1[0]; val x1 = q1[1]; val y1 = q1[2]; val z1 = q1[3]
        val w2 = q2[0]; val x2 = q2[1]; val y2 = q2[2]; val z2 = q2[3]

        return floatArrayOf(
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
            w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2
        )
    }

    /**
     * Computes the dot product of two quaternions.
     */
    fun dot(q1: FloatArray, q2: FloatArray): Float {
        return q1[0] * q2[0] + q1[1] * q2[1] + q1[2] * q2[2] + q1[3] * q2[3]
    }

    /**
     * Normalizes a quaternion.
     */
    fun normalize(q: FloatArray): FloatArray {
        val norm = sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3])
        if (norm == 0.0f) return floatArrayOf(1f, 0f, 0f, 0f)
        val invNorm = 1.0f / norm
        return floatArrayOf(q[0]*invNorm, q[1]*invNorm, q[2]*invNorm, q[3]*invNorm)
    }

    /**
     * Converts a 3x3 rotation matrix to a quaternion.
     */
    fun fromRotationMatrix(m: FloatArray): FloatArray {
        val m00 = m[0]; val m01 = m[1]; val m02 = m[2]
        val m10 = m[3]; val m11 = m[4]; val m12 = m[5]
        val m20 = m[6]; val m21 = m[7]; val m22 = m[8]

        val trace = m00 + m11 + m22
        var w = 0f; var x = 0f; var y = 0f; var z = 0f

        if (trace > 0) {
            val s = 0.5f / sqrt(trace + 1.0f)
            w = 0.25f / s
            x = (m21 - m12) * s
            y = (m02 - m20) * s
            z = (m10 - m01) * s
        } else {
            if (m00 > m11 && m00 > m22) {
                val s = 2.0f * sqrt(1.0f + m00 - m11 - m22)
                w = (m21 - m12) / s
                x = 0.25f * s
                y = (m01 + m10) / s
                z = (m02 + m20) / s
            } else if (m11 > m22) {
                val s = 2.0f * sqrt(1.0f + m11 - m00 - m22)
                w = (m02 - m20) / s
                x = (m01 + m10) / s
                y = 0.25f * s
                z = (m12 + m21) / s
            } else {
                val s = 2.0f * sqrt(1.0f + m22 - m00 - m11)
                w = (m10 - m01) / s
                x = (m02 + m20) / s
                y = (m12 + m21) / s
                z = 0.25f * s
            }
        }
        return normalize(floatArrayOf(w, x, y, z))
    }
}
