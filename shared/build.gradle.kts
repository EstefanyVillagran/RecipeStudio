plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.biometric:biometric:1.2.0-alpha05")
                implementation("androidx.camera:camera-camera2:1.3.1")
                implementation("androidx.camera:camera-lifecycle:1.3.1")
                implementation("androidx.camera:camera-view:1.3.1")
                implementation("com.google.mlkit:barcode-scanning:17.2.0")
            }
        }
    }
}

android {
    namespace = "com.recipestudio.kitchensync.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
