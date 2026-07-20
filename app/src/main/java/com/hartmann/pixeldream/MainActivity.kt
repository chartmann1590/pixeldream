package com.hartmann.pixeldream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hartmann.pixeldream.ads.ConsentManager
import com.google.android.gms.ads.MobileAds
import com.hartmann.pixeldream.onboarding.OnboardingNavGraph
import com.hartmann.pixeldream.ui.theme.PixelDreamTheme

class MainActivity : ComponentActivity() {
    private var adsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ConsentManager.requestConsentIfNeeded(this) { canRequestAds ->
            if (canRequestAds) {
                MobileAds.initialize(this) { runOnUiThread { adsReady = true } }
            }
        }
        setContent {
            PixelDreamTheme {
                val preferences = remember { getSharedPreferences("app_state", MODE_PRIVATE) }
                var onboardingComplete by remember {
                    mutableStateOf(
                        preferences.getBoolean("onboarding_complete", false) &&
                            preferences.getInt("model_catalog_version", 0) == MODEL_CATALOG_VERSION,
                    )
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (onboardingComplete) {
                        MainNavGraph(adsReady = adsReady)
                    } else {
                        OnboardingNavGraph(
                            onOnboardingComplete = {
                                preferences.edit()
                                    .putBoolean("onboarding_complete", true)
                                    .putInt("model_catalog_version", MODEL_CATALOG_VERSION)
                                    .apply()
                                onboardingComplete = true
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val MODEL_CATALOG_VERSION = 3
    }
}
