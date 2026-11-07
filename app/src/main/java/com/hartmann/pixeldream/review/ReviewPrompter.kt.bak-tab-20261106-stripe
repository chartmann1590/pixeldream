package com.hartmann.pixeldream.review

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

private val Context.reviewPromptDataStore: DataStore<Preferences> by preferencesDataStore(name = "review_prompt_prefs")

private object Keys {
    val IMAGE_COUNT = intPreferencesKey("review_prompt_image_count")
    val REQUESTED = booleanPreferencesKey("review_prompt_requested")
}

/** Images generated before we ever ask for a review. Early asks convert worse. */
private const val IMAGES_BEFORE_FIRST_ASK = 3

/**
 * Prompts the official Play In-App Review dialog after a handful of successful generations —
 * real proof the on-device model is working, not just an app-open. Google's own quota caps how
 * often the dialog can appear regardless of what we request, so this only needs to avoid asking
 * too early and never ask twice.
 */
object ReviewPrompter {
    suspend fun maybeRequestReview(activity: Activity) {
        var shouldRequest = false
        activity.applicationContext.reviewPromptDataStore.edit { prefs ->
            val alreadyRequested = prefs[Keys.REQUESTED] ?: false
            val count = (prefs[Keys.IMAGE_COUNT] ?: 0) + 1
            prefs[Keys.IMAGE_COUNT] = count
            if (!alreadyRequested && count >= IMAGES_BEFORE_FIRST_ASK) {
                prefs[Keys.REQUESTED] = true
                shouldRequest = true
            }
        }
        if (!shouldRequest) return

        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, reviewInfo).await()
        }
    }
}
