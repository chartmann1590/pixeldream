package com.hartmann.pixeldream.model

/**
 * Fast, fully offline first line of defense: blocks prompts containing
 * obvious disallowed-category terms before any inference runs. Deliberately
 * coarse — this is a cheap pre-filter, not a substitute for a real
 * classifier, and false positives here are far cheaper than false
 * negatives. See docs/onboarding/content-policy.md for the categories.
 */
class LocalPromptSafetyChecker : PromptSafetyChecker {

    private data class Rule(val category: String, val pattern: Regex)

    private val rules = listOf(
        Rule(
            "child_sexual_abuse",
            Regex(
                """\b(child|children|kid|minor|underage|teen|schoolgirl|schoolboy|loli|shota)\b.{0,48}\b(nude|naked|porn|sexual|sex|fetish|erotic)\b|\b(nude|naked|porn|sexual|sex|fetish|erotic)\b.{0,48}\b(child|children|kid|minor|underage|teen|schoolgirl|schoolboy|loli|shota)\b""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ),
        Rule(
            "non_consensual_sexual_content",
            Regex(
                """\b(rape|raping|non[- ]?consensual|revenge porn|sexual assault|deepfake (nude|porn|sex)|undress (her|him|them))\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        Rule(
            "explicit_sexual_content",
            Regex(
                """\b(hardcore porn|explicit sex|graphic sex|pornographic|bestiality|incest)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        Rule(
            "extreme_violence",
            Regex(
                """\b(gore|gory|behead(ing|ed)?|dismember(ment|ed)?|decapitat(e|ed|ion)|mutilat(e|ed|ion)|graphic torture|graphic execution)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        Rule(
            "self_harm",
            Regex(
                """\b(suicide method|how to (commit suicide|kill myself)|self[- ]?harm instructions|cutting myself|encourage suicide)\b""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        Rule(
            "deceptive_documents",
            Regex(
                """\b(fake|forge[ad]?|counterfeit)\b.{0,32}\b(passport|driver'?s license|identity card|government id|banknote|currency|medical prescription)\b""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ),
        Rule(
            "targeted_harassment",
            Regex(
                """\b(create|make|generate)\b.{0,40}\b(humiliating|degrading|threatening)\b.{0,40}\b(image|picture|poster)\b.{0,40}\b(of|about)\b""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ),
    )

    override fun check(prompt: String): SafetyResult {
        val normalized = prompt.trim()
            .replace(Regex("\\s+"), " ")
            .replace(
                Regex("""\b(no|without)\s+(gore|gory detail|graphic violence)\b""", RegexOption.IGNORE_CASE),
                "",
            )
        for (rule in rules) {
            if (rule.pattern.containsMatchIn(normalized)) return SafetyResult.Blocked(rule.category)
        }
        return SafetyResult.Allowed
    }
}
