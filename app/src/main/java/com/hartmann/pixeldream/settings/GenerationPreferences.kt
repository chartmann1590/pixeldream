package com.hartmann.pixeldream.settings

import android.content.Context

data class GenerationDefaults(
    val steps: Int,
    val cfgScale: Float,
    val imageSize: Int,
    val enhancePrompts: Boolean,
)

object GenerationPreferences {
    private const val FILE = "generation_settings"

    fun read(context: Context): GenerationDefaults {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return GenerationDefaults(
            steps = preferences.getInt("steps", 8).coerceIn(4, 40),
            cfgScale = preferences.getFloat("cfg_scale", 7f).coerceIn(1f, 15f),
            imageSize = preferences.getInt("image_size", 256).let { if (it == 512) 512 else 256 },
            enhancePrompts = preferences.getBoolean("enhance_prompts", true),
        )
    }

    fun write(context: Context, defaults: GenerationDefaults) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("steps", defaults.steps)
            .putFloat("cfg_scale", defaults.cfgScale)
            .putInt("image_size", defaults.imageSize)
            .putBoolean("enhance_prompts", defaults.enhancePrompts)
            .apply()
    }
}
