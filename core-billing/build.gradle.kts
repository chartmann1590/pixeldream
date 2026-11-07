plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hartmann.pixeldream.billing"
    // DirectBilling: real Stripe client in direct builds (-Pstore=direct), a no-op stub in Play builds
    sourceSets.getByName("main").java.srcDir(if (((project.findProperty("store") as String?) ?: "play") == "direct") "src/hsdirect/java" else "src/hsplay/java")
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.billing.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(libs.junit)
}
