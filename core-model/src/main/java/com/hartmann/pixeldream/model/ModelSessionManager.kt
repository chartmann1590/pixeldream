package com.hartmann.pixeldream.model

import kotlinx.coroutines.sync.Mutex

/**
 * Owns the lifecycle of the loaded Gemma and diffusion model sessions.
 *
 * MediaPipe task objects are not safe for concurrent calls and are expensive to
 * initialize, so all inference is serialized through [sessionLock]. On lower-RAM
 * devices, callers should release the Gemma session before loading the diffusion
 * model rather than keeping both resident (see Phase 3 memory-management plan).
 */
class ModelSessionManager {
    private val sessionLock = Mutex()

    // TODO(Phase 3): wrap com.google.mediapipe.tasks.genai.llminference.LlmInference
    //  and the diffusion ImageGenerator task once the on-device diffusion runtime
    //  path is confirmed (see Phase 2 open risk).
}
