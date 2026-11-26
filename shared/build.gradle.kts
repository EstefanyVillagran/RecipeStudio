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
                // Cliente HTTP multiplataforma (URLs prefirmadas de S3)
                // api (no implementation): el tipo HttpClient aparece en el constructor de S3UploadClient y la app debe verlo
                api("io.ktor:ktor-client-core:2.3.12")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:2.3.12")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.12")   // motor HTTP de Android
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

    buildTypes {
        debug {
            // Cobertura JaCoCo del núcleo compartido: gradlew :shared:createDebugUnitTestCoverageReport
            enableUnitTestCoverage = true
        }
    }
}
