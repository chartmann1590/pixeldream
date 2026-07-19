package com.hartmann.pixeldream.data.generation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GenerationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GenerationEntity)

    @Query("DELETE FROM generations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE generations SET isFlagged = 1 WHERE id = :id")
    suspend fun markFlagged(id: String)

    @Query("SELECT * FROM generations WHERE id = :id")
    suspend fun findById(id: String): GenerationEntity?
}
