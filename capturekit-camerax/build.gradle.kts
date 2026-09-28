plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.capturekit.camerax"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // CaptureKit Core
    api(project(":capturekit-core"))

    // CameraX
    api(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    api(libs.androidx.camera.lifecycle)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime)

    // Testing
    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.capturekit"
                artifactId = "capturekit-camerax"
                version = "1.0.0"

                pom {
                    name.set("CaptureKit CameraX")
                    description.set("CameraX integration for CaptureKit - real-time image analysis pipeline")
                    url.set("https://github.com/capturekit/capturekit-android")
                }
            }
        }
    }
}
