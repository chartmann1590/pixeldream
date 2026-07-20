import java.util.Properties
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun buildSetting(name: String, fallback: String = ""): String =
    providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)
        ?: fallback

val admobAppId = buildSetting("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
val admobBannerId = buildSetting("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/6300978111")
val admobInterstitialId = buildSetting("ADMOB_INTERSTITIAL_ID", "ca-app-pub-3940256099942544/1033173712")
val feedbackProxyUrl = buildSetting(
    "FEEDBACK_PROXY_URL",
    "https://pixeldream-model-proxy.charles-h-hartmann1.workers.dev/github/",
)
val forceTestAds = buildSetting("ADMOB_FORCE_TEST_ADS", "false").toBooleanStrictOrNull() ?: false
val releaseKeystorePath = buildSetting("KEYSTORE_PATH").ifBlank { null }

//
// Version overrides for release automation.
//
// Precedence: ANDROID_VERSION_CODE / ANDROID_VERSION_NAME environment variable
// (CI release workflow) > local.properties (developer) > baseline.
// Local builds and ordinary CI keep the baseline values when neither is set,
// so existing behavior is unchanged unless a release pipeline sets the vars.
val androidVersionCode = buildSetting("ANDROID_VERSION_CODE").ifBlank { null }?.toIntOrNull()
    ?: 1
val androidVersionName = buildSetting("ANDROID_VERSION_NAME").ifBlank { null }
    ?: "0.1.0"

//
// GitHub feedback reporter configuration.
//
// Precedence: environment variable (CI) > local.properties (developer, gitignored) > blank.
// Missing values compile to empty strings; the in-app reporter disables itself
// with a clear UI message when the token or repo are not configured.
//
fun githubSetting(propertyKey: String, envName: String): String =
    providers.environmentVariable(envName).orNull
        ?: localProperties.getProperty(propertyKey)
        ?: ""

val githubRepoOwner: String = githubSetting("github.repo.owner", "GH_REPO_OWNER")
val githubRepoName: String = githubSetting("github.repo.name", "GH_REPO_NAME")

android {
    namespace = "com.hartmann.pixeldream"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.hartmann.pixeldream"
        minSdk = 29
        targetSdk = 36
        versionCode = androidVersionCode
        versionName = androidVersionName

        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("boolean", "ADMOB_FORCE_TEST_ADS", forceTestAds.toString())

        buildConfigField("String", "FEEDBACK_PROXY_URL", "\"$feedbackProxyUrl\"")
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$githubRepoOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$githubRepoName\"")
        buildConfigField("String", "FEEDBACK_ASSETS_DIR", "\"feedback-assets\"")

        ndk {
            abiFilters += "arm64-v8a"
            debugSymbolLevel = "FULL"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseKeystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = buildSetting("KEYSTORE_PASSWORD")
                keyAlias = buildSetting("KEY_ALIAS")
                keyPassword = buildSetting("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            configure<CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
            }
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Required precondition for 16 KB page-size compliance: native libs must be
    // uncompressed and page-aligned, which legacy packaging cannot guarantee.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-data"))
    implementation(project(":core-billing"))
    implementation(project(":core-ui"))
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.billing.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)

    // GitHub feedback reporter: networking + serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
