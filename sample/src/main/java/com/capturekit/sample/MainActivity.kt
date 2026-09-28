package com.capturekit.sample

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.Surface
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.capturekit.camerax.analyzer.CaptureAnalyzer
import com.capturekit.camerax.quality.QualityGate
import com.capturekit.core.angle.AngleManager
import com.capturekit.core.angle.AngleMatchResult
import com.capturekit.core.angle.FeatureDetector
import com.capturekit.core.angle.MatchingMethod
import com.capturekit.core.blur.BlurDetector
import com.capturekit.core.blur.BlurMethod
import com.capturekit.core.blur.BlurResult
import com.capturekit.core.model.FrameAnalysisResult
import com.capturekit.core.model.QualityLevel
import com.capturekit.core.orientation.OrientationDetector
import com.capturekit.core.orientation.OrientationType
import com.capturekit.view.overlay.GhostMode
import com.capturekit.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MainActivity for the CaptureKit sample app.
 * Demonstrates how to integrate CaptureAnalyzer, OrientationDetector, and QualityGate with CameraX
 * to build an intuitive re-photography experience.
 */
class MainActivity : AppCompatActivity() {
    
    // View binding
    private lateinit var binding: ActivityMainBinding
    
    // CameraX
    private lateinit var cameraExecutor: ExecutorService
    private var previewUseCase: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    
    // CaptureKit components
    private var captureAnalyzer: CaptureAnalyzer? = null
    private var orientationDetector: OrientationDetector? = null
    private var qualityGate: QualityGate? = null
    
    // Reference image and orientations
    private var referenceImage: Bitmap? = null
    private var referenceSensorData: FloatArray? = null
    private var referenceOrientation: OrientationType? = null
    private var referenceFileName: String? = null // Reference filename for tagging captured photos
    
    // Target threshold values
    private var requiredMatchScore = 0.80f // Default minimum matching threshold (can be customized)
    
    // Ghost overlay toggle
    private var ghostEnabled = true
    
    // Progress alert dialog
    private var progressDialog: AlertDialog? = null
    
    // Activity result launcher for picking reference image from gallery
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadReferenceImage(it) }
    }
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else finish()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize OpenCV
        OpenCVLoader.initLocal()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Initialize orientation detector
        orientationDetector = OrientationDetector(this)
        
        // Initialize quality gate
        qualityGate = QualityGate.roomPhotography()
        
        // Setup UI
        setupUI()
        
        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    /**
     * Set up UI controls and click listeners
     */
    private fun setupUI() {
        // Set Reference button - opens gallery picker
        binding.btnSetReference.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        
        // Capture button
        binding.btnCapture.setOnClickListener {
            captureImage()
        }
        
        // Ghost overlay toggle
        binding.btnToggleGhost.setOnClickListener {
            ghostEnabled = !ghostEnabled
            binding.captureKitOverlay.setGhostOverlayVisible(ghostEnabled)
            if (ghostEnabled) {
                binding.btnToggleGhost.setIconTintResource(android.R.color.white)
                binding.btnToggleGhost.setTextColor(Color.WHITE)
                binding.btnToggleGhost.strokeColor = ColorStateList.valueOf(Color.WHITE)
            } else {
                binding.btnToggleGhost.setIconTintResource(android.R.color.darker_gray)
                binding.btnToggleGhost.setTextColor(Color.GRAY)
                binding.btnToggleGhost.strokeColor = ColorStateList.valueOf(Color.GRAY)
            }
        }

        // Opacity SeekBar
        binding.opacitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alpha = progress / 100f
                binding.captureKitOverlay.ghostOverlay.ghostAlpha = alpha
                binding.txtOpacityValue.text = "${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Required Match SeekBar (Can be customized)
        binding.matchSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Constrain progress minimum to 50% for realistic comparison
                val minScore = Math.max(50, progress)
                requiredMatchScore = minScore / 100f
                binding.txtMatchValue.text = "${minScore}%"
                
                // Re-evaluate quality based on new threshold
                captureAnalyzer?.latestResult?.let { updateUI(it) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Ghost Mode toggle group selection
        binding.ghostModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    binding.btnModeEdges.id -> GhostMode.EDGES
                    binding.btnModeTinted.id -> GhostMode.TINTED
                    binding.btnModeGrayscale.id -> GhostMode.GRAYSCALE
                    else -> GhostMode.NORMAL
                }
                binding.captureKitOverlay.configureGhostOverlay {
                    ghostMode = mode
                }
            }
        }
    }
    
    /**
     * Start the CameraX and CaptureKit analysis pipeline
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }

            // Force all use cases to 4:3 Aspect Ratio for clean alignment
            // Preview
            previewUseCase = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build().also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }
            
            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build()
            
            // Create CaptureAnalyzer
            captureAnalyzer = CaptureAnalyzer.configure {
                targetAnalysisFps = 10
                blur {
                    method = BlurMethod.COMBINED
                    threshold = 80.0
                    enableZoneAnalysis = true
                }
                orientation {
                    enabled = true
                }
            }
            
            // ImageAnalysis with CaptureAnalyzer
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build().also {
                    it.setAnalyzer(cameraExecutor, captureAnalyzer!!)
                }
            
            // Observe results on main thread
            lifecycleScope.launch {
                captureAnalyzer!!.resultFlow.filterNotNull().collect { result ->
                    updateUI(result)
                }
            }

            // Observe orientation changes for continuous leveling bubble updates
            lifecycleScope.launch {
                orientationDetector?.orientationFlow?.collect { deviceOrientation ->
                    val delta = com.capturekit.core.angle.SensorDelta(
                        pitchDelta = deviceOrientation.pitch,
                        rollDelta = deviceOrientation.roll,
                        yawDelta = deviceOrientation.yaw,
                        totalAngle = 0f
                    )
                    binding.captureKitOverlay.alignmentHud.updateSensorDelta(delta)
                }
            }
            
            // Bind use cases
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase, imageCapture, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CaptureKit", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
        
        // Start orientation detector
        orientationDetector?.start()
    }
    
    /**
     * Update UI with the latest analysis result
     */
    private fun updateUI(result: FrameAnalysisResult) {
        // Update all overlay views
        binding.captureKitOverlay.updateFromResult(result)
        
        // Pass requiredMatchScore custom threshold to HUD
        result.angleMatchResult?.let {
            binding.captureKitOverlay.alignmentHud.updateAngleResult(it, requiredMatchScore)
        }
        
        // Evaluate quality gate
        val gateStatus = qualityGate?.evaluate(result)
        
        // Check for orientation mismatch based on resources screen configuration (bulletproof to pitch tilt)
        val currentIsLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isOrientationMismatched = referenceOrientation != null &&
                (referenceOrientation!!.isLandscape != currentIsLandscape)

        // Build status text
        val statusText = buildString {
            if (isOrientationMismatched) {
                append("⚠️ Orientation Mismatch!\nPlease rotate device to ")
                append(if (referenceOrientation?.isLandscape == true) "Landscape" else "Portrait")
            } else {
                append("Quality: ${result.overallQuality.displayName}")
                result.blurResult?.let { blur ->
                    append(" | Blur: ${"%.1f".format(blur.score)}")
                    if (blur.isBlurry) append(" ⚠️")
                }
                result.angleMatchResult?.let { angle ->
                    val accPercent = (angle.overallScore * 100).toInt()
                    append(" | Match: ${accPercent}%")
                    append("\n(Required: ${(requiredMatchScore * 100).toInt()}%)")
                    if (!angle.isAligned) {
                        append(" | ${angle.guidance.description}")
                    }
                }
            }
        }
        binding.statusText.text = statusText
        
        // Color-code status card
        val statusColor = when {
            isOrientationMismatched -> 0xCCF44336.toInt() // Translucent Red for mismatch
            result.overallQuality == QualityLevel.EXCELLENT -> 0xCC4CAF50.toInt() // Translucent Green
            result.overallQuality == QualityLevel.GOOD -> 0xCC8BC34A.toInt()      // Translucent Light Green
            result.overallQuality == QualityLevel.ACCEPTABLE -> 0xCCFFC107.toInt() // Translucent Amber
            else -> 0xCCFF9800.toInt() // Translucent Orange/Red
        }
        binding.statusCard.setCardBackgroundColor(statusColor)

        // Color-code capture FAB based on QualityGate AND custom threshold
        val angleScore = result.angleMatchResult?.overallScore ?: 1.0f
        val isCustomAligned = angleScore >= requiredMatchScore && !isOrientationMismatched
        val isReady = gateStatus?.isReady == true && isCustomAligned
        
        if (isReady) {
            binding.btnCapture.backgroundTintList = ColorStateList.valueOf(0xFF4CAF50.toInt())
            binding.btnCapture.imageTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            binding.btnCapture.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            binding.btnCapture.imageTintList = ColorStateList.valueOf(Color.BLACK)
        }
    }
    
    /**
     * Load an image from Uri and set it as reference for CaptureKit
     */
    private fun loadReferenceImage(uri: Uri) {
        try {
            // Read target filename
            referenceFileName = getFileNameFromUri(uri)
            
            val originalBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (originalBitmap != null) {
                // Parse EXIF rotation and rotate reference bitmap to match visual orientation
                val rotation = getExifRotation(uri)
                val rotatedBitmap = rotateBitmap(originalBitmap, rotation)
                
                // Center-crop reference bitmap to 4:3 aspect ratio to match camera Preview/Analysis streams
                val bitmap = cropBitmapToAspectRatio(rotatedBitmap, 4, 3)
                if (bitmap != rotatedBitmap) {
                    rotatedBitmap.recycle()
                }
                
                referenceImage = bitmap
                
                // For gallery loaded photos, clear sensor data so we rely on highly accurate feature matching
                referenceSensorData = null
                
                // Set reference orientation aspect ratio
                val isLandscape = bitmap.width > bitmap.height
                referenceOrientation = if (isLandscape) OrientationType.LANDSCAPE_LEFT
                                       else OrientationType.PORTRAIT
                                       
                // Automatically update screen orientation to match the reference photo orientation
                requestedOrientation = if (isLandscape) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
                
                // Update CaptureAnalyzer with reference
                captureAnalyzer?.updateReferenceImage(bitmap, null)
                
                // Update ghost overlay
                binding.captureKitOverlay.setReferenceImage(bitmap)
                binding.captureKitOverlay.configureGhostOverlay {
                    ghostAlpha = binding.opacitySlider.progress / 100f
                    showEdges = true
                }
                
                binding.statusText.text = "Reference set - align camera to match"
            }
        } catch (e: Exception) {
            Log.e("CaptureKit", "Failed to load reference image", e)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "Reference"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("CaptureKit", "Error getting file name from Uri", e)
        }
        return name.substringBeforeLast(".")
    }

    private fun cropBitmapToAspectRatio(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val targetRatio = targetW.toFloat() / targetH
        val currentRatio = width.toFloat() / height
        
        var cropW = width
        var cropH = height
        var x = 0
        var y = 0
        
        if (currentRatio > targetRatio) {
            cropW = (height * targetRatio).toInt()
            x = (width - cropW) / 2
        } else if (currentRatio < targetRatio) {
            cropH = (width / targetRatio).toInt()
            y = (height - cropH) / 2
        } else {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
    }

    private fun getExifRotation(uri: Uri): Int {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.media.ExifInterface(inputStream)
                } else {
                    null
                }
                
                val orientation = exifInterface?.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                ) ?: android.media.ExifInterface.ORIENTATION_NORMAL
                
                return when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
            Log.e("CaptureKit", "Error reading EXIF rotation", e)
        }
        return 0
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
    
    /**
     * Capture image using CameraX ImageCapture and confirm quality before saving
     */
    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        
        // 1. Create a temporary file in cache to inspect the picture
        val tempFile = File(cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        
        showLoading(true)
        
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processAndValidateImage(tempFile)
                }
                
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CaptureKit", "Photo capture failed: ${exc.message}", exc)
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this@MainActivity, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
    
    /**
     * Decode, analyze, and confirm whether the captured image meets the blur and angle matching criteria.
     */
    private fun processAndValidateImage(tempFile: File) {
        try {
            // Decode with scaling to avoid Out-Of-Memory exceptions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(tempFile.absolutePath, options)
            
            // Resize to standard analysis resolution (max 800px)
            val maxDim = Math.max(options.outWidth, options.outHeight)
            val scale = 800f / maxDim
            val targetWidth = if (scale < 1.0f) (options.outWidth * scale).toInt() else options.outWidth
            val targetHeight = if (scale < 1.0f) (options.outHeight * scale).toInt() else options.outHeight
            
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = if (scale < 1.0f) (1.0f / scale).toInt() else 1
            }
            val originalBmp = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
            if (originalBmp == null) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, "Failed to decode captured picture", Toast.LENGTH_SHORT).show()
                }
                return
            }
            
            val analysisBitmap = Bitmap.createScaledBitmap(originalBmp, targetWidth, targetHeight, true)
            if (analysisBitmap != originalBmp) {
                originalBmp.recycle()
            }
            
            // 1. Evaluate Blur score on the captured image
            val blurResult = BlurDetector.analyze(analysisBitmap, threshold = 80.0)
            
            // 2. Evaluate Angle Match score against reference image
            var angleResult: AngleMatchResult? = null
            if (referenceImage != null) {
                val manager = AngleManager.configure {
                    matchingMethod = MatchingMethod.HYBRID
                    featureDetector = FeatureDetector.AKAZE
                }
                manager.setReferenceImage(referenceImage!!, referenceSensorData)
                angleResult = manager.compareFrame(analysisBitmap, orientationDetector?.quaternion?.copyOf())
                manager.release()
            }
            
            // 3. Evaluate Orientation Match
            val refIsLandscape = referenceImage?.let { it.width > it.height } ?: false
            val curIsLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val orientationMatches = referenceImage == null || (refIsLandscape == curIsLandscape)
            
            // 4. Verify quality thresholds
            val blurPassed = !blurResult.isBlurry
            val anglePassed = angleResult == null || angleResult.overallScore >= requiredMatchScore
            val passed = blurPassed && anglePassed && orientationMatches
            
            analysisBitmap.recycle()
            
            runOnUiThread {
                showLoading(false)
                if (passed) {
                    // Tagged filename: e.g. MATCHED_hotel_bed_at_1715099300.jpg
                    val prefix = referenceFileName ?: "REF"
                    val displayName = "MATCHED_${prefix}_at_${System.currentTimeMillis()}.jpg"
                    
                    // Save image directly to MediaStore so it appears in the public gallery instantly
                    val savedUri = saveImageToMediaStore(tempFile, displayName)
                    tempFile.delete()
                    
                    if (savedUri != null) {
                        showSuccessDialog(displayName, blurResult.score, angleResult?.overallScore)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to save photo to MediaStore gallery", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Reject and delete temporary file
                    tempFile.delete()
                    showRejectionDialog(blurResult, angleResult, refIsLandscape, curIsLandscape)
                }
            }
        } catch (e: Exception) {
            Log.e("CaptureKit", "Error validating captured image", e)
            runOnUiThread {
                showLoading(false)
                Toast.makeText(this@MainActivity, "Error during validation: ${e.message}", Toast.LENGTH_SHORT).show()
                tempFile.delete()
            }
        }
    }
    
    /**
     * Save the validated temporary JPEG file to the public MediaStore (Pictures/CaptureKit folder)
     */
    private fun saveImageToMediaStore(tempFile: File, displayName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CaptureKit")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val resolver = contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        
        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            return imageUri
        } catch (e: Exception) {
            Log.e("CaptureKit", "Failed writing to MediaStore", e)
            resolver.delete(imageUri, null, null)
        }
        return null
    }

    /**
     * Show loading progress alert dialog
     */
    private fun showLoading(show: Boolean) {
        runOnUiThread {
            if (show) {
                val progressBar = ProgressBar(this).apply {
                    setPadding(60, 60, 60, 60)
                }
                progressDialog = AlertDialog.Builder(this)
                    .setTitle("Analyzing Quality Standards")
                    .setMessage("Validating blur sharpness and angle match accuracy... Please hold steady.")
                    .setCancelable(false)
                    .setView(progressBar)
                    .create()
                progressDialog?.show()
            } else {
                progressDialog?.dismiss()
                progressDialog = null
            }
        }
    }
    
    /**
     * Show dialog for successful capture
     */
    private fun showSuccessDialog(filename: String, blurScore: Double, matchScore: Float?) {
        val message = buildString {
            append("✓ Image is sharp (score: ${"%.1f".format(blurScore)})\n")
            if (matchScore != null) {
                append("✓ Angle matched (accuracy: ${"%.0f".format(matchScore * 100)}%)\n")
            }
            append("✓ Orientation matches\n\n")
            append("Photo saved to Gallery:\n$filename")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Capture Approved")
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Show dialog detailing the rejection reasons
     */
    private fun showRejectionDialog(
        blurResult: BlurResult,
        angleResult: AngleMatchResult?,
        refIsLandscape: Boolean,
        curIsLandscape: Boolean
    ) {
        val message = buildString {
            append("The captured photo does not meet the required standards and has NOT been saved:\n\n")
            
            // Blur validation
            if (!blurResult.isBlurry) {
                append("✓ Sharpness: Pass (score: ${"%.1f".format(blurResult.score)})\n")
            } else {
                append("❌ Sharpness: Blurry (score: ${"%.1f".format(blurResult.score)}, required >= 80.0)\n")
            }
            
            // Angle validation
            if (angleResult != null) {
                val acc = angleResult.overallScore
                if (acc >= requiredMatchScore) {
                    append("✓ Angle Alignment: Pass (${"%.0f".format(acc * 100)}%)\n")
                } else {
                    append("❌ Angle Alignment: Mismatch (${"%.0f".format(acc * 100)}%, required >= ${(requiredMatchScore * 100).toInt()}%)\n")
                }
            }
            
            // Orientation validation
            if (refIsLandscape == curIsLandscape) {
                append("✓ Orientation: Pass\n")
            } else {
                append("❌ Orientation: Mismatch (Expected: ")
                append(if (refIsLandscape) "Landscape" else "Portrait")
                append(", got: ")
                append(if (curIsLandscape) "Landscape" else "Portrait")
                append(")\n")
            }
            
            append("\nPlease hold the camera steady, match the reference outline, and try again.")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Capture Rejected")
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Try Again", null)
            .show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraRotation()
    }
    
    private fun updateCameraRotation() {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        previewUseCase?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
        imageAnalysis?.targetRotation = rotation
    }
    
    override fun onResume() {
        super.onResume()
        orientationDetector?.start()
    }
    
    override fun onPause() {
        super.onPause()
        orientationDetector?.stop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        captureAnalyzer?.release()
        orientationDetector?.release()
    }
}
