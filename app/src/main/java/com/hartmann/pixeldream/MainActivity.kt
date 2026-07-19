package com.hartmann.pixeldream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hartmann.pixeldream.generation.GenerationScreen
import com.hartmann.pixeldream.onboarding.OnboardingNavGraph
import com.hartmann.pixeldream.ui.theme.PixelDreamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelDreamTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        var onboardingComplete by remember { mutableStateOf(false) }
                        if (onboardingComplete) {
                            GenerationScreen()
                        } else {
                            OnboardingNavGraph(onOnboardingComplete = { onboardingComplete = true })
                        }
                    }
                }
            }
        }
    }
}
