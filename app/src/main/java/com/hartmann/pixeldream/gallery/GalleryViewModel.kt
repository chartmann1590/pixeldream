package com.hartmann.pixeldream.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hartmann.pixeldream.data.generation.GenerationEntity
import com.hartmann.pixeldream.data.generation.GenerationRepository
import com.hartmann.pixeldream.data.generation.RoomGenerationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GenerationRepository = RoomGenerationRepository(application)

    val generations: StateFlow<List<GenerationEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(generation: GenerationEntity) {
        viewModelScope.launch {
            repository.delete(generation.id)
            java.io.File(generation.imageFilePath).delete()
            generation.thumbnailPath?.let { java.io.File(it).delete() }
        }
    }

    fun report(id: String, reason: String) {
        viewModelScope.launch { repository.flag(id, reason) }
    }
}
