package com.capturekit.camerax.analyzer.internal

import androidx.camera.core.ImageProxy

/**
 * Utility for extracting the Y-plane (luminance/grayscale) from a YUV_420_888 ImageProxy.
 * Provides zero-copy buffer extraction where possible, or minimal-copy row-by-row extraction.
 */
internal object YPlaneExtractor {
    /**
     * Extracts Y (luminance/grayscale) plane from YUV_420_888 ImageProxy.
     * @param imageProxy CameraX ImageProxy
     * @param outputBuffer Optional pre-allocated buffer to reuse
     * @return Triple of (grayBytes, width, height)
     */
    fun extract(imageProxy: ImageProxy, outputBuffer: ByteArray? = null): Triple<ByteArray, Int, Int> {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = yPlane.rowStride
        
        val size = width * height
        val output = if (outputBuffer != null && outputBuffer.size >= size) outputBuffer 
                     else ByteArray(size)
        
        if (rowStride == width) {
            // Direct copy - no padding
            yBuffer.position(0)
            yBuffer.get(output, 0, size)
        } else {
            // Row-by-row copy - skip padding
            for (row in 0 until height) {
                yBuffer.position(row * rowStride)
                yBuffer.get(output, row * width, width)
            }
        }
        
        return Triple(output, width, height)
    }
    
    /**
     * Extracts Y-plane and creates OpenCV Mat.
     * 
     * @param imageProxy The ImageProxy to extract from.
     * @param reusableMat Optional pre-allocated Mat to reuse.
     * @return An OpenCV Mat containing the Y-plane data.
     */
    fun extractToMat(imageProxy: ImageProxy, reusableMat: org.opencv.core.Mat? = null): org.opencv.core.Mat {
        val (bytes, width, height) = extract(imageProxy)
        val mat = reusableMat ?: org.opencv.core.Mat(height, width, org.opencv.core.CvType.CV_8UC1)
        if (mat.rows() != height || mat.cols() != width) {
            mat.create(height, width, org.opencv.core.CvType.CV_8UC1)
        }
        mat.put(0, 0, bytes)
        return mat
    }
    
    /**
     * Converts ImageProxy to Bitmap using built-in CameraX conversion.
     * 
     * @param imageProxy The ImageProxy to convert.
     * @return A Bitmap representation of the image.
     */
    fun toBitmap(imageProxy: ImageProxy): android.graphics.Bitmap {
        return imageProxy.toBitmap()
    }
}
