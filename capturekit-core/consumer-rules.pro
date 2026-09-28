# CaptureKit Core - Consumer ProGuard Rules
# Keep all public API classes
-keep class com.capturekit.core.blur.** { *; }
-keep class com.capturekit.core.orientation.** { *; }
-keep class com.capturekit.core.angle.** { *; }
-keep class com.capturekit.core.comparison.** { *; }
-keep class com.capturekit.core.model.** { *; }

# Keep OpenCV classes
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
