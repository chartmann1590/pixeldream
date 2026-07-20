package com.hartmann.pixeldream.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

/** Anchored adaptive banner shown above app navigation for free users. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    val adView = remember(context, widthDp) {
        AdView(context).apply {
            adUnitId = AdIds.banner
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Firebase.crashlytics.log("Banner load failed: ${error.code}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
}
