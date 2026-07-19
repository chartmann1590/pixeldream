package com.hartmann.pixeldream.data.generation

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generations")
data class GenerationEntity(
    @PrimaryKey val id: String,
    val originalPrompt: String,
    val enhancedPrompt: String,
    val imageFilePath: String,
    val thumbnailPath: String?,
    val createdAt: Long,
    val modelVersion: String,
    val isFlagged: Boolean = false,
    val isExported: Boolean = false,
)
