package com.hartmann.pixeldream.data.generation

import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for saved generations. The interface boundary here is deliberate:
 * a future implementation could layer in a remote call (e.g. via a Cloudflare Worker)
 * without callers needing to change.
 */
interface GenerationRepository {
    fun observeAll(): Flow<List<GenerationEntity>>
    suspend fun save(generation: GenerationEntity)
    suspend fun delete(id: String)
    suspend fun flag(id: String, reason: String)
}
