package com.hartmann.pixeldream.model

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Downloads large model artifacts through Android's resumable DownloadManager. */
class ModelDownloader(
    private val context: Context,
    private val storage: ModelStorage,
) {
    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    private val preferences by lazy {
        context.getSharedPreferences("model_downloads", Context.MODE_PRIVATE)
    }

    fun download(descriptor: ModelDescriptor): Flow<DownloadState> = flow {
        val destination = storage.fileFor(descriptor)
        if (destination.exists() && storage.verify(destination, descriptor.sha256)) {
            emit(DownloadState.Ready)
            return@flow
        }

        val preferenceKey = "${descriptor.kind.name}:${descriptor.version}"
        var downloadId = preferences.getLong(preferenceKey, -1L)

        if (downloadId < 0 || !downloadExists(downloadId)) {
            destination.delete()
            val requiredBytes = descriptor.sizeBytes + MIN_FREE_SPACE_BYTES
            if (storage.directoryFor(descriptor.kind).usableSpace < requiredBytes) {
                emit(DownloadState.Failed("Not enough storage. Free ${formatBytes(requiredBytes)} and try again."))
                return@flow
            }

            val relativePath = "models/${descriptor.kind.name.lowercase()}/${destination.name}"
            val request = DownloadManager.Request(Uri.parse(descriptor.url))
                .setTitle(modelTitle(descriptor.kind))
                .setDescription("Required for private, on-device AI")
                .setDestinationInExternalFilesDir(context, null, relativePath)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

            downloadId = downloadManager.enqueue(request)
            preferences.edit().putLong(preferenceKey, downloadId).apply()
            emit(DownloadState.Queued)
        }

        while (true) {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    preferences.edit().remove(preferenceKey).apply()
                    emit(DownloadState.Failed("The system lost this download. Tap retry."))
                    return@flow
                }

                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val reportedTotal = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                )

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        emit(DownloadState.Verifying)
                        preferences.edit().remove(preferenceKey).apply()
                        if (storage.verify(destination, descriptor.sha256)) {
                            storage.pruneOtherVersions(descriptor)
                            emit(DownloadState.Ready)
                        } else {
                            destination.delete()
                            emit(DownloadState.Failed("Checksum mismatch. Tap retry for a clean copy."))
                        }
                        return@flow
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        preferences.edit().remove(preferenceKey).apply()
                        destination.delete()
                        emit(DownloadState.Failed(friendlyFailure(reason)))
                        return@flow
                    }
                    else -> {
                        val total = reportedTotal.takeIf { it > 0 } ?: descriptor.sizeBytes
                        emit(DownloadState.Downloading(downloaded.coerceAtLeast(0L), total))
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    fun delete(descriptor: ModelDescriptor) {
        val preferenceKey = preferenceKey(descriptor)
        val downloadId = preferences.getLong(preferenceKey, -1L)
        if (downloadId >= 0) downloadManager.remove(downloadId)
        preferences.edit().remove(preferenceKey).apply()
        storage.delete(descriptor)
    }

    private fun downloadExists(id: Long): Boolean =
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { it.moveToFirst() }

    private fun friendlyFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage for this model."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is unavailable."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "The connection was interrupted. Tap retry to resume."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The model host redirected too many times."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The model host rejected the download."
        else -> "Download failed (system code $reason). Tap retry."
    }

    private fun modelTitle(kind: ModelKind): String = when (kind) {
        ModelKind.GEMMA_PROMPT_ENHANCER -> "Gemma 4 prompt model"
        ModelKind.DIFFUSION_IMAGE_GENERATOR -> "Stable Diffusion image model"
    }

    private fun preferenceKey(descriptor: ModelDescriptor) =
        "${descriptor.kind.name}:${descriptor.version}"

    private fun formatBytes(bytes: Long): String = "%.1f GB".format(bytes / 1_073_741_824.0)

    private companion object {
        const val POLL_INTERVAL_MS = 750L
        const val MIN_FREE_SPACE_BYTES = 512L * 1024L * 1024L
    }
}
