package com.capturekit.core.angle

/**
 * Provides guidance to the user on how to align the device with the reference capture.
 */
enum class AlignmentGuidance(val description: String) {
    TILT_UP("Tilt phone upward"),
    TILT_DOWN("Tilt phone downward"),
    ROTATE_CLOCKWISE("Rotate phone clockwise"),
    ROTATE_COUNTER_CLOCKWISE("Rotate phone counter-clockwise"),
    TURN_LEFT("Turn left"),
    TURN_RIGHT("Turn right"),
    MOVE_CLOSER("Move closer"),
    MOVE_FARTHER("Move farther away"),
    MOVE_LEFT("Move left"),
    MOVE_RIGHT("Move right"),
    MOVE_UP("Move phone up"),
    MOVE_DOWN("Move phone down"),
    HOLD_STEADY("Hold steady - almost aligned"),
    ALIGNED("Aligned - ready to capture");

    /**
     * Returns true if the device is perfectly aligned.
     */
    val isAligned: Boolean get() = this == ALIGNED

    /**
     * Returns true if the device is perfectly aligned or close enough to hold steady.
     */
    val isAlmostAligned: Boolean get() = this == HOLD_STEADY || this == ALIGNED
}
