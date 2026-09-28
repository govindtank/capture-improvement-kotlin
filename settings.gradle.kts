pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CaptureKit"

include(":capturekit-core")
include(":capturekit-camerax")
include(":capturekit-view")
include(":sample")
