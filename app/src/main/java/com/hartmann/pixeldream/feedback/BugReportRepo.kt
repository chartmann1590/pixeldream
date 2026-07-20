package com.hartmann.pixeldream.feedback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.feedbackDataStore by preferencesDataStore(name = "feedback_bug_reports")

/**
 * Persists the list of feedback reports filed from this device using DataStore
 * Preferences. Stores a single JSON-serialized list under [STORAGE_KEY].
 * Corrupt stored JSON is ignored (treated as empty) so a bad write can never
 * crash the app.
 */
class BugReportRepo(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val storageKey = stringPreferencesKey("bug_reports_list")
    private val listSerializer = ListSerializer(BugReport.serializer())

    val bugReports: Flow<List<BugReport>> = context.feedbackDataStore.data.map { prefs ->
        prefs[storageKey]?.let { decode(it) } ?: emptyList()
    }

    suspend fun saveBugReport(report: BugReport) {
        val updated = bugReports.first().upsert(report)
        write(updated)
    }

    suspend fun updateBugReports(reports: List<BugReport>) {
        write(reports.distinctBy { it.number }.sortedByDescending { it.number })
    }

    suspend fun getBugReportsList(): List<BugReport> = bugReports.first()

    private suspend fun write(reports: List<BugReport>) {
        context.feedbackDataStore.edit { prefs ->
            if (reports.isEmpty()) {
                prefs.remove(storageKey)
            } else {
                prefs[storageKey] = json.encodeToString(listSerializer, reports)
            }
        }
    }

    private fun decode(raw: String): List<BugReport> = runCatching {
        json.decodeFromString(listSerializer, raw)
    }.getOrDefault(emptyList())

    private fun List<BugReport>.upsert(report: BugReport): List<BugReport> {
        val without = filterNot { it.number == report.number }
        return (without + report).sortedByDescending { it.number }
    }
}

