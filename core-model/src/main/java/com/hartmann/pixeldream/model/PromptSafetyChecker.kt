package com.hartmann.pixeldream.model

/**
 * Checks a prompt (raw or Gemma-enhanced) against disallowed content categories
 * before it is passed to the diffusion model. The interface boundary lets a future
 * server-side check (e.g. via a Cloudflare Worker) augment or replace the local
 * heuristic without callers changing.
 */
interface PromptSafetyChecker {
    fun check(prompt: String): SafetyResult
}

sealed interface SafetyResult {
    data object Allowed : SafetyResult
    data class Blocked(val category: String) : SafetyResult
}
