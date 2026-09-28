package com.capturekit.core.angle.internal

import android.graphics.Bitmap
import com.capturekit.core.angle.AlignmentGuidance
import com.capturekit.core.angle.AngleConfig
import com.capturekit.core.angle.FeatureDetector
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.features2d.AKAZE
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.Feature2D
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

internal data class FeatureMatchResult(
    val score: Float,
    val matchedFeatureCount: Int,
    val totalDetectedFeatures: Int,
    val spatialGuidances: List<AlignmentGuidance>,
    val homographyValid: Boolean,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1.0f
)

internal class FeatureAngleComparer(private val config: AngleConfig) {

    private val detector: Feature2D = when (config.featureDetector) {
        FeatureDetector.ORB -> ORB.create(config.maxFeatures)
        FeatureDetector.AKAZE -> AKAZE.create()
    }

    private val matcher = BFMatcher.create(
        if (config.featureDetector == FeatureDetector.ORB) Core.NORM_HAMMING else Core.NORM_HAMMING,
        false
    )

    private val refKeypoints = MatOfKeyPoint()
    private val refDescriptors = Mat()
    private var isReferenceSet = false

    private val currentMat = Mat()
    private val currentGray = Mat()
    private val currentKeypoints = MatOfKeyPoint()
    private val currentDescriptors = Mat()
    private val knnMatches = ArrayList<MatOfDMatch>()
    private val goodMatchesMat = MatOfDMatch()

    fun setReference(bitmap: Bitmap) {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        setReference(gray)
        gray.release()
        mat.release()
    }

    fun setReference(grayMat: Mat) {
        detector.detectAndCompute(grayMat, Mat(), refKeypoints, refDescriptors)
        isReferenceSet = !refDescriptors.empty()
    }

    fun compare(bitmap: Bitmap): FeatureMatchResult {
        Utils.bitmapToMat(bitmap, currentMat)
        Imgproc.cvtColor(currentMat, currentGray, Imgproc.COLOR_BGR2GRAY)
        return compare(currentGray)
    }

    fun compare(grayMat: Mat): FeatureMatchResult {
        if (!isReferenceSet) return emptyResult()

        detector.detectAndCompute(grayMat, Mat(), currentKeypoints, currentDescriptors)

        if (currentDescriptors.empty()) return emptyResult()

        knnMatches.clear()
        matcher.knnMatch(refDescriptors, currentDescriptors, knnMatches, 2)

        val ratioThreshold = 0.75f
        val goodMatches = mutableListOf<org.opencv.core.DMatch>()

        for (match in knnMatches) {
            val dmatches = match.toArray()
            if (dmatches.size >= 2) {
                if (dmatches[0].distance < ratioThreshold * dmatches[1].distance) {
                    goodMatches.add(dmatches[0])
                }
            }
        }

        goodMatchesMat.fromList(goodMatches)
        val matchedCount = goodMatches.size
        
        if (matchedCount < config.minFeatureMatches) {
            return emptyResult(matchedCount, currentKeypoints.rows())
        }

        val refPointsList = mutableListOf<Point>()
        val currPointsList = mutableListOf<Point>()
        val refKeypointsArr = refKeypoints.toArray()
        val currKeypointsArr = currentKeypoints.toArray()

        for (m in goodMatches) {
            refPointsList.add(refKeypointsArr[m.queryIdx].pt)
            currPointsList.add(currKeypointsArr[m.trainIdx].pt)
        }

        val refPointsMat = MatOfPoint2f()
        refPointsMat.fromList(refPointsList)
        val currPointsMat = MatOfPoint2f()
        currPointsMat.fromList(currPointsList)

        val homography = Calib3d.findHomography(refPointsMat, currPointsMat, Calib3d.RANSAC, 5.0)
        
        if (homography.empty()) {
            return emptyResult(matchedCount, currentKeypoints.rows())
        }

        val h = grayMat.height().toDouble()
        val w = grayMat.width().toDouble()
        val objCorners = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val sceneCorners = MatOfPoint2f()

        Core.perspectiveTransform(objCorners, sceneCorners, homography)

        val corners = sceneCorners.toArray()
        
        // Validate projected shape to filter out fake matches
        val matOfPoint = org.opencv.core.MatOfPoint(*corners.map { org.opencv.core.Point(it.x, it.y) }.toTypedArray())
        val isConvex = Imgproc.isContourConvex(matOfPoint)
        val area = Imgproc.contourArea(matOfPoint)
        matOfPoint.release()

        val frameArea = w * h
        val homographyValid = isConvex && area > (frameArea * 0.15) && area < (frameArea * 4.0)
        
        if (!homographyValid) {
            return emptyResult(matchedCount, currentKeypoints.rows())
        }

        // Calculate translation and scale
        var minX = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE
        var minY = Double.MAX_VALUE
        var maxY = Double.MIN_VALUE
        for (pt in corners) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
        val sceneW = maxX - minX
        val sceneH = maxY - minY
        val sceneCenterX = minX + sceneW / 2
        val sceneCenterY = minY + sceneH / 2
        
        val translationXRatio = (sceneCenterX - w / 2) / w
        val translationYRatio = (sceneCenterY - h / 2) / h
        val scaleW = sceneW / w
        val scaleH = sceneH / h
        val avgScale = (scaleW + scaleH) / 2.0

        val spatialGuidances = analyzeHomography(corners, w, h, translationXRatio, translationYRatio)

        // Calculate highly accurate matching score:
        // 30% weight to feature match count confidence
        // 70% weight to visual alignment accuracy (translation and scale errors)
        val matchConfidence = Math.min(1.0f, matchedCount.toFloat() / config.minFeatureMatches)
        
        val tErr = Math.hypot(translationXRatio, translationYRatio).toFloat()
        val sErr = Math.abs(avgScale - 1.0).toFloat()
        
        val tTolerance = config.translationTolerance
        val translationAccuracy = Math.max(0f, 1.0f - (tErr / tTolerance))
        val scaleAccuracy = Math.max(0f, 1.0f - (sErr / 0.20f)) // 20% scale tolerance
        
        val visualAlignScore = translationAccuracy * 0.6f + scaleAccuracy * 0.4f
        val finalScore = matchConfidence * 0.3f + visualAlignScore * 0.7f

        return FeatureMatchResult(
            score = finalScore,
            matchedFeatureCount = matchedCount,
            totalDetectedFeatures = currentKeypoints.rows(),
            spatialGuidances = spatialGuidances,
            homographyValid = true,
            translationX = translationXRatio.toFloat(),
            translationY = translationYRatio.toFloat(),
            scale = avgScale.toFloat()
        )
    }

    private fun analyzeHomography(
        sceneCorners: Array<Point>, 
        w: Double, 
        h: Double,
        translationXRatio: Double,
        translationYRatio: Double
    ): List<AlignmentGuidance> {
        val guidances = mutableListOf<AlignmentGuidance>()
        
        // Calculate bounding box of scene corners
        var minX = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE
        var minY = Double.MAX_VALUE
        var maxY = Double.MIN_VALUE
        
        for (pt in sceneCorners) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
        
        val sceneW = maxX - minX
        val sceneH = maxY - minY
        
        val scaleW = sceneW / w
        val scaleH = sceneH / h
        val avgScale = (scaleW + scaleH) / 2

        if (Math.abs(translationXRatio) > config.translationTolerance) {
            guidances.add(if (translationXRatio > 0) AlignmentGuidance.MOVE_LEFT else AlignmentGuidance.MOVE_RIGHT)
        }
        
        if (Math.abs(translationYRatio) > config.translationTolerance) {
            guidances.add(if (translationYRatio > 0) AlignmentGuidance.MOVE_UP else AlignmentGuidance.MOVE_DOWN)
        }
        
        // Use a scale tolerance, e.g., 10%
        val scaleTolerance = 0.1
        if (avgScale > 1.0 + scaleTolerance) {
            guidances.add(AlignmentGuidance.MOVE_FARTHER)
        } else if (avgScale < 1.0 - scaleTolerance) {
            guidances.add(AlignmentGuidance.MOVE_CLOSER)
        }

        return guidances
    }

    private fun emptyResult(matchedCount: Int = 0, total: Int = 0) = FeatureMatchResult(
        score = 0f,
        matchedFeatureCount = matchedCount,
        totalDetectedFeatures = total,
        spatialGuidances = emptyList(),
        homographyValid = false,
        translationX = 0f,
        translationY = 0f,
        scale = 1.0f
    )

    fun release() {
        refKeypoints.release()
        refDescriptors.release()
        currentMat.release()
        currentGray.release()
        currentKeypoints.release()
        currentDescriptors.release()
        goodMatchesMat.release()
    }
}
