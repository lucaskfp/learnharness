package io.github.lucaskfp.learnharness.ui.newsession

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.sessions.SessionKind

/** The language a Session teaches in, independent of the app's language. */
enum class TeachingLanguage(@StringRes val label: Int) {
    English(R.string.language_en),
    PortugueseBrazil(R.string.language_pt_br),
}

enum class AttachmentType(@DrawableRes val icon: Int) {
    Pdf(R.drawable.ic_pdf),
    Image(R.drawable.ic_image),
}

data class Attachment(val name: String, val type: AttachmentType)

/** Everything the New Session screen shows. */
data class NewSessionState(
    val kind: SessionKind = SessionKind.Teaching,
    val goal: String = "",
    val model: String = "",
    /** The learner's default model for this kind's role. */
    val defaultModel: String = "",
    val models: List<String> = emptyList(),
    val teachingLanguage: TeachingLanguage = TeachingLanguage.English,
    val attachments: List<Attachment> = emptyList(),
    val kindMenuExpanded: Boolean = false,
    /** Whether Sessions of this [kind] can be started yet. */
    val kindSupported: Boolean = true,
) {
    val canStart: Boolean get() = goal.isNotBlank() && kindSupported
}
