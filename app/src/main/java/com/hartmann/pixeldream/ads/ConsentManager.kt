package com.hartmann.pixeldream.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Resolves UMP consent (required before any ad request in the EEA/UK, and
 * feeds Analytics consent mode) from an Activity, then invokes [onResolved]
 * regardless of outcome so the caller can proceed either way.
 */
object ConsentManager {
    fun isPrivacyOptionsRequired(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    fun requestConsentIfNeeded(activity: Activity, onResolved: (canRequestAds: Boolean) -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        var resolved = false

        fun resolveOnce() {
            if (!resolved) {
                resolved = true
                onResolved(consentInformation.canRequestAds())
            }
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { resolveOnce() }
            },
            { resolveOnce() },
        )

        if (consentInformation.canRequestAds()) resolveOnce()
    }
}
