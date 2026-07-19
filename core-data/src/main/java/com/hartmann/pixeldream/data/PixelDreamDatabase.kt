package com.hartmann.pixeldream.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hartmann.pixeldream.data.generation.GenerationDao
import com.hartmann.pixeldream.data.generation.GenerationEntity

@Database(entities = [GenerationEntity::class], version = 1, exportSchema = false)
abstract class PixelDreamDatabase : RoomDatabase() {
    abstract fun generationDao(): GenerationDao

    companion object {
        @Volatile private var instance: PixelDreamDatabase? = null

        fun getInstance(context: Context): PixelDreamDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PixelDreamDatabase::class.java,
                    "pixeldream.db",
                ).build().also { instance = it }
            }
    }
}
