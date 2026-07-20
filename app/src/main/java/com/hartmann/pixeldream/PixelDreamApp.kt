package com.hartmann.pixeldream

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.perf.performance

class PixelDreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.analytics.setAnalyticsCollectionEnabled(true)
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
        Firebase.crashlytics.log("PixelDream application started")
        Firebase.performance.isPerformanceCollectionEnabled = true
    }
}
