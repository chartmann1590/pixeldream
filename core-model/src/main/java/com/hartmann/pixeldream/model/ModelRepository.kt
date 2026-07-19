package com.hartmann.pixeldream.model

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Onboarding-facing entry point: fetches the manifest, then downloads and
 * verifies each model, reporting per-model progress.
 */
class ModelRepository(
    context: Context,
    manifestUrl: String,
) {
    private val storage = ModelStorage(context)
    private val downloader = ModelDownloader(context, storage)
    private val manifestFetcher = ModelManifestFetcher(manifestUrl)

    suspend fun fetchManifest(): List<ModelDescriptor> = manifestFetcher.fetch()

    fun download(descriptor: ModelDescriptor): Flow<DownloadState> = downloader.download(descriptor)

    fun isReady(descriptor: ModelDescriptor): Boolean =
        storage.verify(storage.fileFor(descriptor), descriptor.sha256)

    fun localFile(descriptor: ModelDescriptor) = storage.fileFor(descriptor)
}
