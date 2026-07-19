package com.hartmann.pixeldream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hartmann.pixeldream.onboarding.OnboardingNavGraph
import com.hartmann.pixeldream.ui.theme.PixelDreamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelDreamTheme {
                var onboardingComplete by remember { mutableStateOf(false) }
                if (onboardingComplete) {
                    MainNavGraph()
                } else {
                    OnboardingNavGraph(
                        onOnboardingComplete = { onboardingComplete = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
