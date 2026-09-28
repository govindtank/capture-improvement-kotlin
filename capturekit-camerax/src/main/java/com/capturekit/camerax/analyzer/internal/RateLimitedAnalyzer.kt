package com.capturekit.camerax.analyzer.internal

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * An ImageAnalysis.Analyzer that limits the frame rate of processing.
 * Frames arriving faster than the target FPS are discarded.
 *
 * @param targetFps The desired maximum frames per second to process.
 * @param delegate The actual block to process the image.
 */
internal class RateLimitedAnalyzer(
    private val targetFps: Int,
    private val delegate: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastAnalyzedTimestamp = 0L
    private val frameIntervalMs = 1000L / targetFps
    
    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp >= frameIntervalMs) {
            lastAnalyzedTimestamp = currentTimestamp
            delegate(image)
        } else {
            image.close()
        }
    }
}
