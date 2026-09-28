package com.capturekit.core.blur

import android.graphics.Bitmap
import android.graphics.Color
import com.capturekit.core.blur.internal.LaplacianAnalyzer
import com.capturekit.core.blur.internal.TengradAnalyzer
import kotlin.math.exp

/**
 * Main public API for detecting blur in images.
 */
class BlurDetector private constructor(private val config: BlurConfig) {
    
    /**
     * Analyzes a Bitmap for blur based on the configuration.
     * 
     * @param bitmap The image to analyze.
     * @return The blur analysis result.
     */
    fun analyze(bitmap: Bitmap): BlurResult {
        val start = System.currentTimeMillis()
        
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return createEmptyResult(config.method, config.threshold)
        
        var targetW = w
        var targetH = h
        if (w > config.downscaleWidth || h > config.downscaleHeight) {
            val scale = minOf(config.downscaleWidth.toFloat() / w, config.downscaleHeight.toFloat() / h)
            targetW = (w * scale).toInt()
            targetH = (h * scale).toInt()
        }
        
        val scaled = if (targetW != w || targetH != h) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        
        val roi = config.regionOfInterest
        val finalBitmap = if (roi != null) {
            val left = (roi.left * targetW).toInt().coerceIn(0, targetW)
            val top = (roi.top * targetH).toInt().coerceIn(0, targetH)
            val right = (roi.right * targetW).toInt().coerceIn(0, targetW)
            val bottom = (roi.bottom * targetH).toInt().coerceIn(0, targetH)
            
            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)
            
            if (width != targetW || height != targetH) {
                Bitmap.createBitmap(scaled, left, top, width, height)
            } else {
                scaled
            }
        } else {
            scaled
        }

        val fw = finalBitmap.width
        val fh = finalBitmap.height
        val pixels = IntArray(fw * fh)
        finalBitmap.getPixels(pixels, 0, fw, 0, 0, fw, fh)
        
        val grayPixels = ByteArray(fw * fh)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            grayPixels[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt().toByte()
        }
        
        return doAnalyze(grayPixels, fw, fh, start)
    }

    /**
     * Analyzes a raw grayscale byte array for blur.
     * 
     * @param grayPixels The raw grayscale pixels.
     * @param width The width of the image.
     * @param height The height of the image.
     * @return The blur analysis result.
     */
    fun analyze(grayPixels: ByteArray, width: Int, height: Int): BlurResult {
        return doAnalyze(grayPixels, width, height, System.currentTimeMillis())
    }
    
    private fun doAnalyze(grayPixels: ByteArray, width: Int, height: Int, start: Long): BlurResult {
        if (width == 0 || height == 0 || grayPixels.isEmpty()) return createEmptyResult(config.method, config.threshold)
        
        val score = when (config.method) {
            BlurMethod.LAPLACIAN_VARIANCE -> LaplacianAnalyzer.analyze(grayPixels, width, height)
            BlurMethod.TENENGRAD -> TengradAnalyzer.analyze(grayPixels, width, height)
            BlurMethod.COMBINED -> {
                val lap = LaplacianAnalyzer.analyze(grayPixels, width, height)
                val ten = TengradAnalyzer.analyze(grayPixels, width, height)
                (lap * config.combinedWeightLaplacian) + (ten * config.combinedWeightTenengrad)
            }
        }
        
        val zones = if (config.enableZoneAnalysis) {
            when (config.method) {
                BlurMethod.LAPLACIAN_VARIANCE -> LaplacianAnalyzer.analyzeZones(grayPixels, width, height)
                BlurMethod.TENENGRAD -> TengradAnalyzer.analyzeZones(grayPixels, width, height)
                BlurMethod.COMBINED -> {
                    val lapZ = LaplacianAnalyzer.analyzeZones(grayPixels, width, height)
                    val tenZ = TengradAnalyzer.analyzeZones(grayPixels, width, height)
                    val res = mutableMapOf<Zone, Double>()
                    Zone.entries.forEach {
                        val lap = lapZ[it] ?: 0.0
                        val ten = tenZ[it] ?: 0.0
                        res[it] = (lap * config.combinedWeightLaplacian) + (ten * config.combinedWeightTenengrad)
                    }
                    res
                }
            }
        } else {
            null
        }
        
        val isBlurry = score < config.threshold
        val confidence = calculateConfidence(score, config.threshold)
        val elapsed = System.currentTimeMillis() - start
        
        return BlurResult(
            score = score,
            isBlurry = isBlurry,
            confidence = confidence,
            method = config.method,
            threshold = config.threshold,
            analysisTimeMs = elapsed,
            zoneScores = zones
        )
    }
    
    private fun calculateConfidence(score: Double, threshold: Double): Float {
        // sigmoid-like mapping
        val scale = 10.0 / threshold
        val x = (score - threshold) * scale
        val sig = 1.0 / (1.0 + exp(-x))
        return sig.toFloat().coerceIn(0.0f, 1.0f)
    }
    
    private fun createEmptyResult(method: BlurMethod, threshold: Double): BlurResult {
        return BlurResult(0.0, true, 0.0f, method, threshold, 0L, null)
    }

    companion object {
        /**
         * Convenience method to quickly analyze a bitmap with Laplacian Variance.
         */
        fun analyze(bitmap: Bitmap, threshold: Double = 100.0): BlurResult {
            val detector = BlurDetector(BlurConfig.DEFAULT.copy(threshold = threshold))
            return detector.analyze(bitmap)
        }
        
        /**
         * Create a customized BlurDetector using the DSL.
         */
        fun configure(block: BlurConfigBuilder.() -> Unit): BlurDetector {
            val builder = BlurConfigBuilder()
            builder.block()
            return BlurDetector(builder.build())
        }
        
        /**
         * Preset configured specifically for hotel room re-photography.
         */
        fun roomPhotography(): BlurDetector {
            return BlurDetector(BlurConfig.ROOM_PHOTOGRAPHY)
        }
    }
}
