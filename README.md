# CaptureKit

[![JitPack](https://jitpack.io/v/govindtank/capture-improvement-kotlin.svg)](https://jitpack.io/#govindtank/capture-improvement-kotlin)

A modular Android native library for **image blur detection**, **angle/pose matching**, and **orientation detection** — built for CameraX with easy-to-use Kotlin DSL APIs.

[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=23)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Use Case

CaptureKit was designed for **re-photography** workflows — scenarios where a user needs to capture the same scene from the same angle as a previously taken reference photo. For example:

- 🏨 **Hotel room inspection** — Admin captures reference photos, cleaning staff re-captures after work
- 🏗️ **Construction progress** — Periodic photos from identical angles for comparison
- 📋 **Insurance claims** — Before/after damage documentation
- 🏠 **Real estate** — Consistent property listing photos

## Features

| Feature | Description |
|---------|-------------|
| 🔍 **Blur Detection** | Laplacian variance + Tenengrad — pure Kotlin, zero dependencies, ~2-4ms |
| 📐 **Angle Matching** | Hybrid sensor + OpenCV feature matching for precise alignment guidance |
| 📱 **Orientation Detection** | Real-time portrait/landscape detection via hardware sensors |
| 🎯 **Visual Guidance** | Ghost overlay, alignment arrows, artificial horizon HUD |
| 📊 **Quality Gate** | Configurable pass/fail criteria with detailed feedback |
| 📷 **CameraX Integration** | Drop-in `ImageAnalysis.Analyzer` with rate-limited frame processing |

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  Your App                        │
├──────────┬──────────────┬───────────────────────┤
│capturekit│ capturekit   │   capturekit          │
│  -view   │  -camerax    │    -core              │
│          │              │                       │
│ Overlay  │ Analyzer     │ BlurDetector          │
│ HUD      │ QualityGate  │ AngleManager          │
│ Badge    │              │ OrientationDetector    │
│          │              │ ImageComparator        │
└──────────┴──────────────┴───────────────────────┘
```

## Modules

| Module | Size Impact | Description |
|--------|-------------|-------------|
| `capturekit-core` | ~3 MB (opencv-mobile) | Core algorithms — blur, orientation, angle matching, SSIM |
| `capturekit-camerax` | ~0 KB (shared) | CameraX `ImageAnalysis.Analyzer` pipeline |
| `capturekit-view` | ~0 KB | XML View-based overlays, HUD, indicators |

## Installation

Add the JitPack repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.govindtank:capture-improvement-kotlin:capturekit-core:1.0.0")
    implementation("com.github.govindtank:capture-improvement-kotlin:capturekit-camerax:1.0.0")
    implementation("com.github.govindtank:capture-improvement-kotlin:capturekit-view:1.0.0")
}
```

> [!IMPORTANT]
> After tagging a release on GitHub (`git tag v1.0.0 && git push --tags`), JitPack automatically builds and publishes the artifacts. Replace `1.0.0` with your actual tag.

### 2. Initialize OpenCV

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OpenCVLoader.initLocal()
    }
}
```

### 3. Blur Detection (Standalone)

```kotlin
// One-line blur check
val result = BlurDetector.analyze(bitmap, threshold = 100.0)
if (result.isBlurry) {
    Log.w("Quality", "Image is blurry! Score: ${result.score}")
}

// Configured detector with DSL
val detector = BlurDetector.configure {
    method = BlurMethod.COMBINED
    threshold = 80.0
    enableZoneAnalysis = true
    regionOfInterest = RectF(0.05f, 0.05f, 0.95f, 0.95f)
}
val result = detector.analyze(bitmap)
// result.score, result.isBlurry, result.confidence, result.zoneScores

// Room photography preset
val detector = BlurDetector.roomPhotography()
```

### 4. Real-Time CameraX Analysis

```kotlin
// Create analyzer with DSL
val analyzer = CaptureAnalyzer.configure {
    targetAnalysisFps = 10

    blur {
        method = BlurMethod.COMBINED
        threshold = 80.0
        enableZoneAnalysis = true
    }

    orientation {
        enabled = true
        required = OrientationType.PORTRAIT
    }

    angleMatching {
        referenceImage = referenceBitmap
        referenceSensorData = savedQuaternion
        matchingMethod = MatchingMethod.HYBRID
        featureDetector = FeatureDetector.AKAZE
        angleTolerance = 5.0f
    }
}

// Or use the room photography preset
val analyzer = CaptureAnalyzer.roomPhotography(referenceImage = bitmap)

// Attach to CameraX
val imageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
imageAnalysis.setAnalyzer(executor, analyzer)

// Observe results
lifecycleScope.launch {
    analyzer.resultFlow.filterNotNull().collect { result ->
        // result.blurResult?.isBlurry
        // result.angleMatchResult?.guidance
        // result.overallQuality
    }
}
```

### 5. Visual Overlays (XML)

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <com.capturekit.view.CaptureKitOverlayLayout
        android:id="@+id/captureKitOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
```

```kotlin
// Set reference image for ghost overlay
captureKitOverlay.setReferenceImage(referenceBitmap)
captureKitOverlay.configureGhostOverlay {
    ghostAlpha = 0.35f
    showEdges = true
    edgeColor = Color.CYAN
}
captureKitOverlay.configureAlignmentHud {
    showHorizonLine = true
    showDirectionalArrows = true
    showGuidanceText = true
}

// Update from CaptureAnalyzer results
analyzer.resultFlow.filterNotNull().collect { result ->
    captureKitOverlay.updateFromResult(result)
}
```

### 6. Quality Gate

```kotlin
val qualityGate = QualityGate.configure {
    minBlurScore = 80.0
    minAngleMatchScore = 0.75f
    minOverallQuality = QualityLevel.GOOD
}

analyzer.resultFlow.filterNotNull().collect { result ->
    val status = qualityGate.evaluate(result)
    if (status.isReady) {
        // All criteria passed — enable capture button
    } else {
        // Show failure reasons
        status.failureReasons.forEach { reason -> Log.d("QualityGate", reason) }
    }
}
```

### 7. Orientation Detection

```kotlin
val orientationDetector = OrientationDetector(context)
orientationDetector.start()

// Observe real-time orientation
orientationDetector.orientationFlow.collect { orientation ->
    // orientation.type = PORTRAIT / LANDSCAPE_LEFT / LANDSCAPE_RIGHT
    // orientation.pitch, orientation.roll, orientation.yaw
    // orientation.isLevel
}

// Save reference orientation for angle matching
val referenceOrientation = orientationDetector.captureReferenceOrientation()
val quaternion = orientationDetector.quaternion.copyOf()

// Compare orientations
val delta = orientationDetector.compareWith(referenceOrientation)
// delta.pitchDelta, delta.rollDelta, delta.typesMatch
```

### 8. Angle/Pose Matching (Standalone)

```kotlin
val angleManager = AngleManager.configure {
    matchingMethod = MatchingMethod.HYBRID
    featureDetector = FeatureDetector.AKAZE
    angleTolerance = 5.0f
    minFeatureMatches = 20
}

// Set reference
angleManager.setReferenceImage(referenceBitmap, referenceQuaternion)

// Compare frames
val result = angleManager.compareFrame(currentBitmap, currentQuaternion)
// result.overallScore       -> 0.0 to 1.0
// result.isAligned          -> true/false
// result.guidance           -> AlignmentGuidance.TILT_UP / MOVE_LEFT / ALIGNED
// result.allGuidances       -> [TILT_UP, MOVE_LEFT]
// result.sensorDelta        -> SensorDelta(pitch=2.1°, roll=-0.5°, yaw=1.3°)
// result.featureMatchScore  -> 0.85
// result.readyToCapture     -> true/false

// Clean up
angleManager.release()
```

### 9. Image Comparison Utilities

```kotlin
// SSIM comparison
val ssim = ImageComparator.ssim(image1, image2)
// ssim.score = 0.92, ssim.isMatch = true

// Histogram comparison
val hist = ImageComparator.compareHistograms(image1, image2)

// Edge comparison
val edges = ImageComparator.compareEdges(image1, image2)

// Extract Canny edges as Bitmap (for overlay)
val edgeBitmap = ImageComparator.extractEdges(bitmap)
```

## Custom XML Attributes

### GhostOverlayView
| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `ghostAlpha` | float | 0.35 | Ghost image transparency (0.0-1.0) |
| `showEdges` | boolean | false | Show Canny edge overlay |
| `edgeColor` | color | Cyan | Edge line color |
| `edgeStrokeWidth` | dimension | 2dp | Edge line width |

### AlignmentHudView
| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `showHorizonLine` | boolean | true | Show artificial horizon |
| `showDirectionalArrows` | boolean | true | Show guidance arrows |
| `showGuidanceText` | boolean | true | Show text directions |
| `alignedColor` | color | #4CAF50 | Color when aligned |
| `warningColor` | color | #FFC107 | Color when close |
| `errorColor` | color | #F44336 | Color when misaligned |

### BlurIndicatorView
| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `showScore` | boolean | true | Show numeric score |
| `blurIndicatorStyle` | enum | badge | badge / bar / ring |
| `sharpColor` | color | #4CAF50 | Color when sharp |
| `blurryColor` | color | #F44336 | Color when blurry |

## Requirements

- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 35
- **Kotlin**: 2.0+
- **CameraX**: 1.4.1+

## License

```
Copyright 2025 CaptureKit

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
