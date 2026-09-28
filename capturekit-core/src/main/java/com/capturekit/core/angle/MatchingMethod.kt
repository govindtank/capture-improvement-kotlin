package com.capturekit.core.angle

/**
 * Defines the method used for pose matching.
 */
enum class MatchingMethod {
    /** Uses only device sensors (accelerometer, gyroscope, magnetometer, etc.) */
    SENSOR_ONLY,
    
    /** Uses only computer vision (feature matching) */
    VISION_ONLY,
    
    /** Combines sensor data and computer vision for robust matching */
    HYBRID
}

/**
 * Defines the algorithm used for feature detection.
 */
enum class FeatureDetector {
    /** Fast and efficient, good for most use cases */
    ORB,
    
    /** More robust but slightly slower */
    AKAZE
}
