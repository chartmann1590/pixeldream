package com.hartmann.pixeldream.analytics

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics

/**
 * Centralized Firebase Analytics event taxonomy for PixelDream. Keeping the
 * event names and params here (rather than scattered `logEvent` calls)
 * keeps the taxonomy auditable in one place and prevents typo'd event names
 * from silently fragmenting a metric in the Firebase console.
 */
object Analytics {
    private val firebaseAnalytics get() = Firebase.analytics

    // Onboarding
    fun onboardingStarted() = log("onboarding_started")
    fun contentPolicyAccepted() = log("content_policy_accepted")
    fun modelDownloadStarted(modelName: String) = log("model_download_started", "model_name" to modelName)
    fun modelDownloadCompleted(modelName: String) = log("model_download_completed", "model_name" to modelName)
    fun modelDownloadFailed(modelName: String, errorReason: String) =
        log("model_download_failed", "model_name" to modelName, "error_reason" to errorReason)
    fun onboardingCompleted() = log("onboarding_completed")

    // Generation
    fun promptSubmitted() = log("prompt_submitted")
    fun promptEnhanced(latencyMs: Long) = log("prompt_enhanced", "latency_ms" to latencyMs)
    fun generationStarted() = log("generation_started")
    fun generationSucceeded(latencyMs: Long, modelVersion: String) =
        log("generation_succeeded", "latency_ms" to latencyMs, "model_version" to modelVersion)
    fun generationFailed(errorType: String) = log("generation_failed", "error_type" to errorType)
    fun promptBlockedByFilter(category: String) = log("prompt_blocked_by_filter", "category" to category)

    // Gallery
    fun imageExported() = log("image_exported")
    fun imageShared() = log("image_shared")
    fun imageEdited(editType: String) = log("image_edited", "edit_type" to editType)

    // Monetization
    fun interstitialShown() = log("interstitial_shown")
    fun interstitialClicked() = log("interstitial_clicked")
    fun subscriptionScreenViewed() = log("subscription_screen_viewed")
    fun subscriptionPurchased() = log("subscription_purchased")
    fun subscriptionCancelled() = log("subscription_cancelled")
    fun subscriptionRestored() = log("subscription_restored")

    // Safety
    fun contentReported(reason: String) = log("content_reported", "reason" to reason)

    private fun log(name: String, vararg params: Pair<String, Any>) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        firebaseAnalytics.logEvent(name, bundle)
    }
}
