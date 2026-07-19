package com.hartmann.pixeldream.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches `models_manifest.json`, which lists the current download URL,
 * checksum, size, and minimum RAM for each model. Fetched at onboarding time
 * so model versions can change server-side without an app release.
 */
class ModelManifestFetcher(private val manifestUrl: String) {

    suspend fun fetch(): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        val connection = URL(manifestUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<ModelDescriptor> {
        val root = JSONObject(body)
        val models = root.getJSONArray("models")
        return (0 until models.length()).map { i ->
            val entry = models.getJSONObject(i)
            ModelDescriptor(
                kind = ModelKind.valueOf(entry.getString("kind")),
                version = entry.getString("version"),
                url = entry.getString("url"),
                sha256 = entry.getString("sha256"),
                sizeBytes = entry.getLong("sizeBytes"),
                minRamMb = entry.optInt("minRamMb", DeviceTierDetector.MINIMUM_RAM_MB),
            )
        }
    }
}
