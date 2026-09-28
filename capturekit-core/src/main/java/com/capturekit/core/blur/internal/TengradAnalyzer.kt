package com.capturekit.core.blur.internal

import android.graphics.Bitmap
import android.graphics.Color
import com.capturekit.core.blur.Zone

/**
 * Pure Kotlin implementation of Tenengrad (Sobel gradient) blur detection.
 */
internal object TengradAnalyzer {

    /**
     * Analyzes the given raw grayscale pixel array.
     */
    fun analyze(grayPixels: ByteArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || grayPixels.isEmpty()) return 0.0

        var sum = 0L
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                val p00 = grayPixels[idx - width - 1].toInt() and 0xFF
                val p01 = grayPixels[idx - width].toInt() and 0xFF
                val p02 = grayPixels[idx - width + 1].toInt() and 0xFF
                val p10 = grayPixels[idx - 1].toInt() and 0xFF
                val p12 = grayPixels[idx + 1].toInt() and 0xFF
                val p20 = grayPixels[idx + width - 1].toInt() and 0xFF
                val p21 = grayPixels[idx + width].toInt() and 0xFF
                val p22 = grayPixels[idx + width + 1].toInt() and 0xFF

                // Sobel Gx: [[-1,0,1],[-2,0,2],[-1,0,1]]
                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                // Sobel Gy: [[-1,-2,-1],[0,0,0],[1,2,1]]
                val gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02)
                
                val magnitudeSq = gx * gx + gy * gy
                
                sum += magnitudeSq
                count++
            }
        }

        if (count == 0) return 0.0
        return sum.toDouble() / count
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
            var count = 0
            
            for (y in startY + 1 until endY - 1) {
                for (x in startX + 1 until endX - 1) {
                    val idx = y * width + x
                    
                    val p00 = grayPixels[idx - width - 1].toInt() and 0xFF
                    val p01 = grayPixels[idx - width].toInt() and 0xFF
                    val p02 = grayPixels[idx - width + 1].toInt() and 0xFF
                    val p10 = grayPixels[idx - 1].toInt() and 0xFF
                    val p12 = grayPixels[idx + 1].toInt() and 0xFF
                    val p20 = grayPixels[idx + width - 1].toInt() and 0xFF
                    val p21 = grayPixels[idx + width].toInt() and 0xFF
                    val p22 = grayPixels[idx + width + 1].toInt() and 0xFF

                    val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                    val gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02)
                    
                    val magnitudeSq = gx * gx + gy * gy
                    sum += magnitudeSq
                    count++
                }
            }
            
            result[zones[i]] = if (count > 0) sum.toDouble() / count else 0.0
        }
        
        return result
    }
}
