package com.hartmann.pixeldream.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Preloads and shows the between-generations interstitial for free-tier
 * users. Every call site must check [EntitlementRepository.isAdFree] before
 * invoking [showIfLoaded] -- this class does not know about entitlement.
 */
class InterstitialAdManager(private val context: Context) {
    private var ad: InterstitialAd? = null
    private var isLoading = false

    fun preload() {
        if (ad != null || isLoading) return
        isLoading = true
        InterstitialAd.load(
            context,
            AdIds.INTERSTITIAL_TEST_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    isLoading = false
                    ad = interstitialAd
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    ad = null
                }
            },
        )
    }

    fun showIfLoaded(activity: Activity, onDismissed: () -> Unit) {
        val loadedAd = ad
        if (loadedAd == null) {
            onDismissed()
            return
        }
        loadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                preload()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                preload()
                onDismissed()
            }
        }
        loadedAd.show(activity)
    }
}
