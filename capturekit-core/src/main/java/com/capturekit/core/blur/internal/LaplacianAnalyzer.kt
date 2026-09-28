package com.capturekit.core.blur.internal

import android.graphics.Bitmap
import android.graphics.Color
import com.capturekit.core.blur.Zone

/**
 * Pure Kotlin implementation of Laplacian variance blur detection.
 */
internal object LaplacianAnalyzer {

    /**
     * Analyzes the given raw grayscale pixel array.
     * Higher variance = sharper image.
     */
    fun analyze(grayPixels: ByteArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || grayPixels.isEmpty()) return 0.0

        var sum = 0L
        var sumSquares = 0L
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val p = grayPixels[idx].toInt() and 0xFF
                val left = grayPixels[idx - 1].toInt() and 0xFF
                val right = grayPixels[idx + 1].toInt() and 0xFF
                val top = grayPixels[idx - width].toInt() and 0xFF
                val bottom = grayPixels[idx + width].toInt() and 0xFF

                // [[0,1,0],[1,-4,1],[0,1,0]]
                val laplacian = top + bottom + left + right - 4 * p
                
                sum += laplacian
                sumSquares += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0.0
        val mean = sum.toDouble() / count
        return (sumSquares.toDouble() / count) - (mean * mean)
    }

    /**
     * Analyzes the given Bitmap by extracting grayscale.
     */
    fun analyze(bitmap: Bitmap): Double {
        if (bitmap.width < 3 || bitmap.height < 3) return 0.0
        
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val grayPixels = ByteArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            grayPixels[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt().toByte()
        }
        
        return analyze(grayPixels, width, height)
    }

    /**
     * Analyzes the image by dividing it into 9 zones.
     */
    fun analyzeZones(grayPixels: ByteArray, width: Int, height: Int): Map<Zone, Double> {
        val result = mutableMapOf<Zone, Double>()
        if (width < 3 || height < 3 || grayPixels.isEmpty()) {
            Zone.entries.forEach { result[it] = 0.0 }
            return result
        }

        val zoneW = width / 3
        val zoneH = height / 3
        val zones = Zone.entries.toTypedArray()
        
        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            
            val startX = col * zoneW
            val endX = if (col == 2) width else startX + zoneW
            val startY = row * zoneH
            val endY = if (row == 2) height else startY + zoneH
            
            val zW = endX - startX
            val zH = endY - startY
            
            if (zW < 3 || zH < 3) {
                result[zones[i]] = 0.0
                continue
            }
            
            var sum = 0L
            var sumSquares = 0L
            var count = 0
            
            for (y in startY + 1 until endY - 1) {
                for (x in startX + 1 until endX - 1) {
                    val idx = y * width + x
                    val p = grayPixels[idx].toInt() and 0xFF
                    val left = grayPixels[idx - 1].toInt() and 0xFF
                    val right = grayPixels[idx + 1].toInt() and 0xFF
                    val top = grayPixels[idx - width].toInt() and 0xFF
                    val bottom = grayPixels[idx + width].toInt() and 0xFF

                    val laplacian = top + bottom + left + right - 4 * p
                    
                    sum += laplacian
                    sumSquares += laplacian * laplacian
                    count++
                }
            }
            
            result[zones[i]] = if (count > 0) {
                val mean = sum.toDouble() / count
                (sumSquares.toDouble() / count) - (mean * mean)
            } else 0.0
        }
        
        return result
    }
}
