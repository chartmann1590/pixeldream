package com.hartmann.pixeldream.model

/**
 * Fast, fully offline first line of defense: blocks prompts containing
 * obvious disallowed-category terms before any inference runs. Deliberately
 * coarse — this is a cheap pre-filter, not a substitute for a real
 * classifier, and false positives here are far cheaper than false
 * negatives. See docs/onboarding/content-policy.md for the categories.
 */
class LocalPromptSafetyChecker : PromptSafetyChecker {

    private val blockedTerms: Map<String, List<String>> = mapOf(
        "csam" to listOf("child porn", "cp ", "loli", "shota"),
        "extreme_violence" to listOf("gore", "beheading", "torture"),
        "self_harm" to listOf("self harm", "suicide method"),
    )

    override fun check(prompt: String): SafetyResult {
        val normalized = " ${prompt.lowercase()} "
        for ((category, terms) in blockedTerms) {
            if (terms.any { normalized.contains(it) }) {
                return SafetyResult.Blocked(category)
            }
        }
        return SafetyResult.Allowed
    }
}
