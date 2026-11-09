package io.github.lucaskfp.learnharness.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.newsession.Attachment
import io.github.lucaskfp.learnharness.ui.newsession.AttachmentType
import io.github.lucaskfp.learnharness.ui.newsession.NewSessionState
import io.github.lucaskfp.learnharness.ui.newsession.TeachingLanguage
import io.github.lucaskfp.learnharness.ui.sessions.SessionFilter
import io.github.lucaskfp.learnharness.ui.sessions.SessionKind
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase
import io.github.lucaskfp.learnharness.ui.sessions.SessionSummary
import io.github.lucaskfp.learnharness.ui.sessions.SessionsState
import io.github.lucaskfp.learnharness.ui.setup.KeyStatus
import io.github.lucaskfp.learnharness.ui.setup.ModelRole
import io.github.lucaskfp.learnharness.ui.setup.Provider
import io.github.lucaskfp.learnharness.ui.setup.SetupState

/** Setup screen states for the mockups. */
object FakeSetup {
    private const val KEY = "sk-ant-api03-mockup-key-0000000000"
    private val anthropicModels = listOf("Claude Opus 5.5", "Claude Sonnet 5.5", "Claude Haiku 5.5")

    fun empty() = SetupState()

    fun keyTesting() = SetupState(provider = Provider.Anthropic, apiKey = KEY, keyStatus = KeyStatus.Testing)

    fun keyInvalid() = SetupState(provider = Provider.Anthropic, apiKey = KEY, keyStatus = KeyStatus.Invalid)

    fun keyValid() = SetupState(
        provider = Provider.Anthropic,
        apiKey = KEY,
        keyStatus = KeyStatus.Valid,
        models = anthropicModels,
        roleModels = mapOf(
            ModelRole.Teaching to anthropicModels[0],
            ModelRole.Practice to anthropicModels[1],
            ModelRole.Jobs to anthropicModels[2],
        ),
    )

    fun customEndpoint() = SetupState(
        provider = Provider.Custom,
        baseUrl = "http://192.168.0.10:11434/v1",
        customModelName = "llama3.1:8b",
    )
}

/**
 * Sessions screen states for the mockups. The goals are string resources so each locale's
 * mockup shows learner content in that language.
 */
object FakeSessions {
    @Composable
    fun list(): List<SessionSummary> = listOf(
        SessionSummary(
            id = "crypto",
            goal = stringResource(R.string.fake_goal_crypto),
            kind = SessionKind.Teaching,
            phase = SessionPhase.Probing,
            minutesSinceActivity = 2,
            turnRunning = true,
        ),
        SessionSummary(
            id = "fourier",
            goal = stringResource(R.string.fake_goal_fourier),
            kind = SessionKind.Teaching,
            phase = SessionPhase.Teaching,
            minutesSinceActivity = 60,
            jobPending = true,
        ),
        SessionSummary(
            id = "dp",
            goal = stringResource(R.string.fake_goal_dp),
            kind = SessionKind.Practice,
            phase = SessionPhase.ProblemOpen,
            minutesSinceActivity = 3 * 60,
        ),
        SessionSummary(
            id = "sorting",
            goal = stringResource(R.string.fake_goal_sorting),
            kind = SessionKind.Teaching,
            phase = SessionPhase.Planning,
            minutesSinceActivity = 24 * 60,
        ),
        SessionSummary(
            id = "bayes",
            goal = stringResource(R.string.fake_goal_bayes),
            kind = SessionKind.Practice,
            phase = SessionPhase.ProblemConcluded,
            minutesSinceActivity = 2 * 24 * 60,
        ),
        SessionSummary(
            id = "vaccines",
            goal = stringResource(R.string.fake_goal_vaccines),
            kind = SessionKind.Teaching,
            phase = SessionPhase.Teaching,
            minutesSinceActivity = 5 * 24 * 60,
        ),
    )

    fun empty() = SessionsState()

    @Composable
    fun populated() = SessionsState(sessions = list())

    @Composable
    fun fabMenuExpanded() = SessionsState(sessions = list(), fabMenuExpanded = true)

    @Composable
    fun searchResults() = SessionsState(
        sessions = list(),
        query = stringResource(R.string.fake_search_hit),
        filter = SessionFilter.All,
    )

    @Composable
    fun searchNoMatch() = SessionsState(sessions = list(), query = stringResource(R.string.fake_search_miss))

    @Composable
    fun deleteConfirmation(): SessionsState {
        val sessions = list()
        return SessionsState(sessions = sessions, pendingDelete = sessions[3])
    }
}

/** New Session screen states for the mockups. */
object FakeNewSession {
    private val models = listOf("Claude Opus 5.5", "Claude Sonnet 5.5", "Claude Haiku 5.5")

    /** The teaching language defaults to the app's language. */
    @Composable
    private fun appLanguage(): TeachingLanguage =
        if (LocalConfiguration.current.locales[0].language == "pt") {
            TeachingLanguage.PortugueseBrazil
        } else {
            TeachingLanguage.English
        }

    @Composable
    fun emptyTeaching() = NewSessionState(
        kind = SessionKind.Teaching,
        model = models[0],
        defaultModel = models[0],
        models = models,
        teachingLanguage = appLanguage(),
    )

    @Composable
    fun filledTeaching() = emptyTeaching().copy(
        goal = stringResource(R.string.fake_goal_crypto),
        attachments = listOf(
            Attachment(stringResource(R.string.fake_file_pdf), AttachmentType.Pdf),
            Attachment(stringResource(R.string.fake_file_photo), AttachmentType.Image),
        ),
    )

    @Composable
    fun practice() = NewSessionState(
        kind = SessionKind.Practice,
        goal = stringResource(R.string.fake_goal_dp),
        model = models[1],
        defaultModel = models[1],
        models = models,
        teachingLanguage = appLanguage(),
    )

    @Composable
    fun kindMenuOpen() = filledTeaching().copy(kindMenuExpanded = true)
}
