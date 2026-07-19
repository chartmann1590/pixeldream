package com.hartmann.pixeldream.model

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Downloads a [ModelDescriptor] via the OS-managed [DownloadManager], which
 * survives process death and retries transient network failures on its own —
 * simpler and more robust than a hand-rolled resumable HTTP client for this
 * use case. Progress is reported by polling the DownloadManager cursor, the
 * standard pattern for observing an in-flight download.
 */
class ModelDownloader(
    private val context: Context,
    private val storage: ModelStorage,
) {
    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    fun download(descriptor: ModelDescriptor): Flow<DownloadState> = flow {
        val destination = storage.fileFor(descriptor)
        if (destination.exists() && storage.verify(destination, descriptor.sha256)) {
            emit(DownloadState.Ready)
            return@flow
        }
        destination.delete()

        val relativePath = "models/${descriptor.kind.name.lowercase()}/${destination.name}"
        val request = DownloadManager.Request(Uri.parse(descriptor.url))
            .setTitle(descriptor.kind.name)
            .setDestinationInExternalFilesDir(context, null, relativePath)
            .setAllowedOverMetered(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val downloadId = downloadManager.enqueue(request)
        emit(DownloadState.Queued)

        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            cursor.use {
                if (!it.moveToFirst()) {
                    emit(DownloadState.Failed("Download vanished from queue"))
                    return@flow
                }
                val statusIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val bytesIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val reasonIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)

                when (it.getInt(statusIdx)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        emit(DownloadState.Verifying)
                        if (storage.verify(destination, descriptor.sha256)) {
                            emit(DownloadState.Ready)
                        } else {
                            destination.delete()
                            emit(DownloadState.Failed("Checksum mismatch"))
                        }
                        return@flow
                    }
                    DownloadManager.STATUS_FAILED -> {
                        emit(DownloadState.Failed("DownloadManager error code ${it.getInt(reasonIdx)}"))
                        return@flow
                    }
                    else -> {
                        val downloaded = it.getLong(bytesIdx)
                        val total = it.getLong(totalIdx).takeIf { t -> t > 0 } ?: descriptor.sizeBytes
                        emit(DownloadState.Downloading(downloaded, total))
                    }
                }
            }
            delay(500)
        }
    }
}
