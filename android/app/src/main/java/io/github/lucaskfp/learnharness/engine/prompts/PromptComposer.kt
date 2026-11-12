package io.github.lucaskfp.learnharness.engine.prompts

import io.github.lucaskfp.learnharness.engine.AssetSource

/** The language a Session teaches in. [displayName] is how the agent is told to address it. */
enum class TeachingLanguage(val displayName: String) {
    English("English"),
    PortugueseBrazil("Brazilian Portuguese"),
}

/**
 * Composes an agent's system prompt from the upstream segments copied into the app's assets
 * (see the `syncUpstreamPrompts` Gradle task). Order and joining follow upstream's
 * `composeInstructions` (`src/agent/prompts/README.md`): the base, the agent's own fragments,
 * then the shared fragments, each trimmed and joined with a `---` rule. Prompts stay in English;
 * the Session's teaching language is a trailing instruction.
 */
class PromptComposer(private val assets: AssetSource) {
    fun prober(language: TeachingLanguage): String =
        compose(listOf("system-prompt.md") + fragments("prober") + fragments("shared"), language)

    fun teacher(language: TeachingLanguage): String =
        compose(listOf("system-prompt.md") + fragments("teacher") + fragments("shared"), language)

    /**
     * The Practitioner composes only its own directory and a curated shared appendix, with no
     * teaching base (upstream `effectivePromptSegments("practice")`).
     */
    fun practitioner(language: TeachingLanguage): String =
        compose(fragments("practice") + PracticeShared.map { "shared/$it" }, language)

    private fun fragments(directory: String): List<String> =
        assets.list("$Root/$directory")
            .filter { FragmentName.matches(it) }
            .map { "$directory/$it" }

    private fun compose(segments: List<String>, language: TeachingLanguage): String =
        (segments.map { assets.read("$Root/$it") } + languageInstruction(language))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n---\n\n")

    private fun languageInstruction(language: TeachingLanguage): String = """
        ## Teaching language

        The learner learns in ${language.displayName}. Write everything the learner reads in ${language.displayName}: your messages, questions, answer options, explanations and the learner assessment. Keep tool names, argument names and option `value` identifiers exactly as their schemas specify.
    """.trimIndent()

    private companion object {
        const val Root = "upstream/prompts"

        /** The shared fragments upstream allowlists for practice; other shared fragments stay out. */
        val PracticeShared = listOf("10-voice.md", "15-accuracy.md", "16-mental-model.md", "40-formatting.md")

        /** Upstream accepts only numeric-prefixed fragments, so README files never enter a prompt. */
        val FragmentName = Regex("^\\d+-[\\w.-]+\\.md$")
    }
}
