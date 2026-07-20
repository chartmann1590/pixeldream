package com.hartmann.pixeldream.ads

import com.hartmann.pixeldream.BuildConfig

/** Release IDs are injected from ignored local properties or GitHub Actions secrets. */
object AdIds {
    private const val BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"

    val banner: String
        get() = if (BuildConfig.DEBUG) BANNER_TEST_ID else BuildConfig.ADMOB_BANNER_ID

    val interstitial: String
        get() = if (BuildConfig.DEBUG) INTERSTITIAL_TEST_ID else BuildConfig.ADMOB_INTERSTITIAL_ID
}
