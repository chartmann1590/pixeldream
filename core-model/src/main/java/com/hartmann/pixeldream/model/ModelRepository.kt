package com.hartmann.pixeldream.model

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Onboarding-facing entry point: fetches the manifest, then downloads and
 * verifies each model, reporting per-model progress.
 */
class ModelRepository(
    context: Context,
) {
    private val storage = ModelStorage(context)
    private val downloader = ModelDownloader(context, storage)
    suspend fun fetchManifest(): List<ModelDescriptor> = OfficialModelCatalog.models

    fun download(descriptor: ModelDescriptor): Flow<DownloadState> = downloader.download(descriptor)

    /**
     * Bounded to 30s: a single Crashlytics ANR report blamed this function on the main
     * thread despite it already running on Dispatchers.IO (GitHub issue #8). The exact
     * cause wasn't reproducible, but on a large model file with unusually slow/contended
     * device storage the SHA-256 verification below could in principle run long enough to
     * matter; failing closed to "not ready" past this bound is much better than an
     * unbounded hang, and callers already treat "not ready" as "needs (re)download".
     */
    suspend fun isReady(descriptor: ModelDescriptor): Boolean = try {
        withTimeout(30_000) {
            withContext(Dispatchers.IO) {
                storage.verify(storage.fileFor(descriptor), descriptor.sha256).also { ready ->
                    if (ready) storage.pruneOtherVersions(descriptor)
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        false
    }

    fun localFile(descriptor: ModelDescriptor) = storage.fileFor(descriptor)

    suspend fun delete(descriptor: ModelDescriptor) = withContext(Dispatchers.IO) {
        downloader.delete(descriptor)
    }
}
