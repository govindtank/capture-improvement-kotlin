package com.capturekit.core.comparison

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

data class SsimResult(val score: Float, val isMatch: Boolean, val threshold: Float = 0.70f)
enum class HistogramMethod { CORRELATION, CHI_SQUARE, INTERSECTION, BHATTACHARYYA }
data class HistogramResult(val score: Double, val method: HistogramMethod, val isMatch: Boolean)
data class EdgeComparisonResult(val similarity: Float, val edgeOverlapRatio: Float, val isMatch: Boolean)

object ImageComparator {

    /**
     * Computes SSIM in pure Kotlin using byte arrays.
     */
    fun ssim(image1: Bitmap, image2: Bitmap, windowSize: Int = 11): SsimResult {
        if (image1.width != image2.width || image1.height != image2.height) {
            return SsimResult(0f, false)
        }
        val width = image1.width
        val height = image1.height
        
        val bytes1 = getGrayBytes(image1)
        val bytes2 = getGrayBytes(image2)
        
        return ssim(bytes1, bytes2, width, height)
    }

    private fun getGrayBytes(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val bytes = ByteArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // simple grayscale
            val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            bytes[i] = gray.toByte()
        }
        return bytes
    }

    fun ssim(gray1: ByteArray, gray2: ByteArray, width: Int, height: Int): SsimResult {
        // Constants C1 and C2 as per paper
        val C1 = (0.01 * 255).let { it * it }
        val C2 = (0.03 * 255).let { it * it }
        
        // We'll compute a global SSIM by computing means and variances over the entire image
        // (A true local SSIM would use a sliding window, which is more complex in pure Kotlin but doable.
        // For brevity and performance here, we'll do global or simplified local)
        var sum1 = 0.0
        var sum2 = 0.0
        for (i in gray1.indices) {
            val v1 = gray1[i].toInt() and 0xFF
            val v2 = gray2[i].toInt() and 0xFF
            sum1 += v1
            sum2 += v2
        }
        val n = gray1.size.toDouble()
        val mu1 = sum1 / n
        val mu2 = sum2 / n

        var var1 = 0.0
        var var2 = 0.0
        var cov = 0.0
        for (i in gray1.indices) {
            val v1 = gray1[i].toInt() and 0xFF
            val v2 = gray2[i].toInt() and 0xFF
            var1 += (v1 - mu1) * (v1 - mu1)
            var2 += (v2 - mu2) * (v2 - mu2)
            cov += (v1 - mu1) * (v2 - mu2)
        }
        var1 /= n
        var2 /= n
        cov /= n

        val numerator = (2 * mu1 * mu2 + C1) * (2 * cov + C2)
        val denominator = (mu1 * mu1 + mu2 * mu2 + C1) * (var1 + var2 + C2)
        val score = (numerator / denominator).toFloat()
        val threshold = 0.70f
        return SsimResult(score, score >= threshold, threshold)
    }

    fun compareHistograms(image1: Bitmap, image2: Bitmap, method: HistogramMethod = HistogramMethod.CORRELATION): HistogramResult {
        val mat1 = Mat()
        val mat2 = Mat()
        Utils.bitmapToMat(image1, mat1)
        Utils.bitmapToMat(image2, mat2)

        val hist1 = Mat()
        val hist2 = Mat()
        val channels = MatOfInt(0)
        val histSize = MatOfInt(256)
        val ranges = MatOfFloat(0f, 256f)
        
        // Convert to HSV and use Hue channel, or just use grayscale. Here, use grayscale.
        val gray1 = Mat()
        val gray2 = Mat()
        Imgproc.cvtColor(mat1, gray1, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(mat2, gray2, Imgproc.COLOR_BGR2GRAY)

        Imgproc.calcHist(listOf(gray1), channels, Mat(), hist1, histSize, ranges)
        Imgproc.calcHist(listOf(gray2), channels, Mat(), hist2, histSize, ranges)

        val cvMethod = when (method) {
            HistogramMethod.CORRELATION -> Imgproc.CV_COMP_CORREL
            HistogramMethod.CHI_SQUARE -> Imgproc.CV_COMP_CHISQR
            HistogramMethod.INTERSECTION -> Imgproc.CV_COMP_INTERSECT
            HistogramMethod.BHATTACHARYYA -> Imgproc.CV_COMP_BHATTACHARYYA
        }

        val score = Imgproc.compareHist(hist1, hist2, cvMethod)
        val isMatch = if (cvMethod == Imgproc.CV_COMP_CORREL || cvMethod == Imgproc.CV_COMP_INTERSECT) {
            score > 0.8
        } else {
            score < 0.2
        }

        mat1.release(); mat2.release()
        gray1.release(); gray2.release()
        hist1.release(); hist2.release()
        
        return HistogramResult(score, method, isMatch)
    }

    fun compareEdges(image1: Bitmap, image2: Bitmap): EdgeComparisonResult {
        val edges1 = extractEdgesMat(image1)
        val edges2 = extractEdgesMat(image2)
        
        // Compute overlap
        val overlap = Mat()
        org.opencv.core.Core.bitwise_and(edges1, edges2, overlap)
        
        val count1 = org.opencv.core.Core.countNonZero(edges1).toFloat()
        val count2 = org.opencv.core.Core.countNonZero(edges2).toFloat()
        val countOverlap = org.opencv.core.Core.countNonZero(overlap).toFloat()
        
        val similarity = if (count1 + count2 > 0) {
            2 * countOverlap / (count1 + count2)
        } else {
            1.0f
        }
        
        edges1.release(); edges2.release(); overlap.release()
        return EdgeComparisonResult(similarity, similarity, similarity > 0.6f)
    }

    fun extractEdges(bitmap: Bitmap, lowThreshold: Double = 50.0, highThreshold: Double = 150.0): Bitmap {
        val edgesMat = extractEdgesMat(bitmap, lowThreshold, highThreshold)
        val alpha = edgesMat
        val colorMat = Mat(edgesMat.rows(), edgesMat.cols(), org.opencv.core.CvType.CV_8UC1, org.opencv.core.Scalar(255.0))
        val channels = java.util.ArrayList<Mat>()
        channels.add(colorMat) // R
        channels.add(colorMat) // G
        channels.add(colorMat) // B
        channels.add(alpha)    // A
        
        val rgbaMat = Mat()
        org.opencv.core.Core.merge(channels, rgbaMat)
        
        val outBmp = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgbaMat, outBmp)
        
        edgesMat.release()
        colorMat.release()
        rgbaMat.release()
        return outBmp
    }

    private fun extractEdgesMat(bitmap: Bitmap, lowThreshold: Double = 50.0, highThreshold: Double = 150.0): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        val edges = Mat()
        Imgproc.Canny(gray, edges, lowThreshold, highThreshold)
        mat.release()
        gray.release()
        return edges
    }
}
