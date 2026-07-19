plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hartmann.pixeldream.model"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 29
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // MediaPipe GenAI (Gemma) task API for prompt enhancement.
    implementation(libs.mediapipe.tasks.genai)
    // Diffusion runtime dependency TBD: MediaPipe's Image Generator has no
    // pre-converted Google-hosted model (see docs/models/README.md) — wiring
    // deferred until a converted model is actually hosted.

    testImplementation(libs.junit)
}
