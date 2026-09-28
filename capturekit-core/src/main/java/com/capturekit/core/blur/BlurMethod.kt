package com.capturekit.core.blur

/**
 * Defines the algorithm used for blur detection.
 */
enum class BlurMethod(val description: String) {
    /**
     * Fastest (~2-4ms), second-order derivative, best for documents/rooms.
     */
    LAPLACIAN_VARIANCE("Laplacian Variance"),

    /**
     * Sobel gradient-based (~3-5ms), slightly more noise-resilient.
     */
    TENENGRAD("Tenengrad Variance"),

    /**
     * Weighted average of both methods for highest accuracy.
     */
    COMBINED("Combined Laplacian and Tenengrad")
}
