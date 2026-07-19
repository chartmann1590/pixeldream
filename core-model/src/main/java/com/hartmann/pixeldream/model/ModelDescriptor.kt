package com.hartmann.pixeldream.model

/** Identifies which on-device model a download/session refers to. */
enum class ModelKind {
    GEMMA_PROMPT_ENHANCER,
    DIFFUSION_IMAGE_GENERATOR,
}

/**
 * A single entry from the remote `models_manifest.json`. Versioned and
 * immutable per [version] so the manifest can point at a new build without
 * an app release.
 */
data class ModelDescriptor(
    val kind: ModelKind,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val minRamMb: Int,
)
