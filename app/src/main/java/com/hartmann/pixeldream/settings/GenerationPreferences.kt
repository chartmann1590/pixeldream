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

    /**
     * Bumped when the shipped defaults change in a way that must also reach people who
     * saved the old ones. Version 2: the old 256 px / 8 step defaults produced smeared,
     * multi-coloured blobs on every phone (Stable Diffusion 1.5 is a 512 px model and
     * needs ~20 steps to converge), so saved sizes below 384 px and step counts below 12
     * are lifted once. Picking a lower value again in Settings is still honoured.
     */
    private const val DEFAULTS_VERSION = 2
    private const val KEY_VERSION = "defaults_version"

    const val DEFAULT_STEPS = 20
    const val DEFAULT_CFG = 7f
    const val DEFAULT_IMAGE_SIZE = 512

    /** Sizes Stable Diffusion 1.5 renders coherently. 256 px is kept only as a crash-safe clamp. */
    val IMAGE_SIZES = listOf(384, 512)
    val STEP_CHOICES = listOf(12, 20, 30, 40)

    fun read(context: Context): GenerationDefaults {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_VERSION, 1) < DEFAULTS_VERSION) {
            val editor = preferences.edit().putInt(KEY_VERSION, DEFAULTS_VERSION)
            if (preferences.getInt("image_size", DEFAULT_IMAGE_SIZE) < 384) editor.remove("image_size")
            if (preferences.getInt("steps", DEFAULT_STEPS) < 12) editor.remove("steps")
            editor.apply()
        }
        return GenerationDefaults(
            steps = preferences.getInt("steps", DEFAULT_STEPS).coerceIn(4, 40),
            cfgScale = preferences.getFloat("cfg_scale", DEFAULT_CFG).coerceIn(1f, 15f),
            imageSize = preferences.getInt("image_size", DEFAULT_IMAGE_SIZE).let { if (it in IMAGE_SIZES) it else DEFAULT_IMAGE_SIZE },
            enhancePrompts = preferences.getBoolean("enhance_prompts", true),
        )
    }

    fun write(context: Context, defaults: GenerationDefaults) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_VERSION, DEFAULTS_VERSION)
            .putInt("steps", defaults.steps)
            .putFloat("cfg_scale", defaults.cfgScale)
            .putInt("image_size", defaults.imageSize)
            .putBoolean("enhance_prompts", defaults.enhancePrompts)
            .apply()
    }
}
