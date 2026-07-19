package com.hartmann.pixeldream.ads

import android.app.Activity
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Resolves UMP consent (required before any ad request in the EEA/UK, and
 * feeds Analytics consent mode) from an Activity, then invokes [onResolved]
 * regardless of outcome so the caller can proceed either way.
 */
object ConsentManager {
    fun requestConsentIfNeeded(activity: Activity, onResolved: () -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { onResolved() }
            },
            { onResolved() },
        )
    }
}
