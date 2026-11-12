package io.github.lucaskfp.learnharness.engine.tools

/** Upstream's tool names, as the imported contracts spell them. One place, so the folds agree. */
object ToolNames {
    const val Probe = "probe"
    const val Recall = "recall"
    const val Ask = "ask_user_question"
    const val ProbeDone = "probe_done"
    const val PlanInit = "plan_init"
    const val Quiz = "quiz"
    const val Explain = "explain"
    const val Problem = "problem"
    const val Conclude = "conclude"

    /** The Teacher's questions: the only calls that carry a Plan `nodeId`. */
    val NodeTagged = setOf(Quiz, Explain)
}
