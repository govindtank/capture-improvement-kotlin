package com.capturekit.core.orientation

/**
 * Defines the device or image orientation.
 */
enum class OrientationType(val degrees: Int) {
    PORTRAIT(0), 
    LANDSCAPE_LEFT(90), 
    LANDSCAPE_RIGHT(270), 
    UPSIDE_DOWN(180);
    
    /** Indicates if orientation is landscape */
    val isLandscape: Boolean get() = this == LANDSCAPE_LEFT || this == LANDSCAPE_RIGHT
    
    /** Indicates if orientation is portrait */
    val isPortrait: Boolean get() = this == PORTRAIT || this == UPSIDE_DOWN
    
    companion object {
        /**
         * Returns the closest OrientationType for a given degree (0-360).
         */
        fun fromDegrees(degrees: Int): OrientationType {
            val normalized = ((degrees % 360) + 360) % 360
            return when {
                normalized >= 315 || normalized < 45 -> PORTRAIT
                normalized in 45..134 -> LANDSCAPE_LEFT
                normalized in 135..224 -> UPSIDE_DOWN
                else -> LANDSCAPE_RIGHT
            }
        }
    }
}
