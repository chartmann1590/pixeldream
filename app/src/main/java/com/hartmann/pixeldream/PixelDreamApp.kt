package com.hartmann.pixeldream

import android.app.Application
import com.google.android.gms.ads.MobileAds

class PixelDreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // UMP consent (required before ad requests in EEA/UK) is resolved from
        // MainActivity, since it needs an Activity context -- see
        // ConsentManager.requestConsentIfNeeded().
        MobileAds.initialize(this) {}
    }
}
