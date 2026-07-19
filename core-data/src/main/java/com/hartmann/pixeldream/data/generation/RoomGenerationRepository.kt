package com.hartmann.pixeldream.data.generation

import android.content.Context
import com.hartmann.pixeldream.data.PixelDreamDatabase
import kotlinx.coroutines.flow.Flow

class RoomGenerationRepository(context: Context) : GenerationRepository {
    private val dao = PixelDreamDatabase.getInstance(context).generationDao()

    override fun observeAll(): Flow<List<GenerationEntity>> = dao.observeAll()

    override suspend fun save(generation: GenerationEntity) = dao.insert(generation)

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun flag(id: String, reason: String) = dao.markFlagged(id)
}
