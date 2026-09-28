package com.capturekit.core.orientation

/**
 * Represents the device's physical orientation in space.
 */
data class DeviceOrientation(
    val type: OrientationType,
    val rotationDegrees: Int,
    val pitch: Float,
    val roll: Float,
    val yaw: Float,
    val isLevel: Boolean,
    val timestamp: Long
)

/**
 * Represents an image's orientation based on EXIF and detected layout.
 */
data class ImageOrientation(
    val detectedOrientation: OrientationType,
    val exifRotation: Int,
    val aspectRatioType: AspectRatioType,
    val needsRotation: Boolean,
    val rotationToApply: Int
)

/**
 * Enum for describing the general aspect ratio of an image.
 */
enum class AspectRatioType { TALL, WIDE, SQUARE }

/**
 * Result of comparing two orientations.
 */
data class OrientationDelta(
    val pitchDelta: Float,
    val rollDelta: Float,
    val yawDelta: Float,
    val totalAngleDelta: Float,
    val typesMatch: Boolean,
    val isWithinTolerance: Boolean
)
