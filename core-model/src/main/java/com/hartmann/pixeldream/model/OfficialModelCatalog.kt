package com.hartmann.pixeldream.model

/** Pinned publisher-hosted artifacts used by PixelDream. */
object OfficialModelCatalog {
    val models: List<ModelDescriptor> = listOf(
        ModelDescriptor(
            kind = ModelKind.GEMMA_PROMPT_ENHANCER,
            version = "gemma-4-e2b-it-litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            sizeBytes = 2_588_147_712L,
            minRamMb = 6_144,
        ),
        ModelDescriptor(
            kind = ModelKind.DIFFUSION_IMAGE_GENERATOR,
            version = "stable-diffusion-v1-5-q8-gguf",
            url = "https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF/resolve/031b5f5df991f511b3f5fa8fed6d99048ababb69/stable-diffusion-v1-5-pruned-emaonly-Q8_0.gguf",
            sha256 = "d0555243938c62faeefb4ac93f6c7a053ad373a4290c5256bce229aeb193bf94",
            sizeBytes = 1_763_578_176L,
            minRamMb = 6_144,
        ),
    )
}
