package com.hartmann.pixeldream.model

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun isReady(descriptor: ModelDescriptor): Boolean = withContext(Dispatchers.IO) {
        storage.verify(storage.fileFor(descriptor), descriptor.sha256).also { ready ->
            if (ready) storage.pruneOtherVersions(descriptor)
        }
    }

    fun localFile(descriptor: ModelDescriptor) = storage.fileFor(descriptor)

    suspend fun delete(descriptor: ModelDescriptor) = withContext(Dispatchers.IO) {
        downloader.delete(descriptor)
    }
}
