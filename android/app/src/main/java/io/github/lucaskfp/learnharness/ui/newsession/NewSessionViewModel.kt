package io.github.lucaskfp.learnharness.ui.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lucaskfp.learnharness.engine.SessionEngine
import io.github.lucaskfp.learnharness.engine.StartSession
import io.github.lucaskfp.learnharness.engine.prompts.TeachingLanguage as EngineLanguage
import io.github.lucaskfp.learnharness.engine.store.SettingsRepository
import io.github.lucaskfp.learnharness.ui.sessions.SessionKind
import io.github.lucaskfp.learnharness.engine.SessionKind as EngineKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Starting a Session: the goal, model and teaching language, then the engine starts its first Turn. */
class NewSessionViewModel(
    initialKind: SessionKind,
    private val settings: SettingsRepository,
    private val engine: SessionEngine,
    appLocale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        NewSessionState(
            kind = initialKind,
            teachingLanguage = if (appLocale.language == "pt") TeachingLanguage.PortugueseBrazil else TeachingLanguage.English,
            kindSupported = initialKind == SessionKind.Teaching,
        ),
    )
    val state: StateFlow<NewSessionState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val models = settings.models()
            mutableState.update { it.copy(models = models) }
            applyDefaultModel()
        }
    }

    private suspend fun applyDefaultModel() {
        val kind = mutableState.value.kind
        val role = if (kind == SessionKind.Teaching) "teaching" else "practice"
        val default = settings.roleModel(role) ?: mutableState.value.models.firstOrNull().orEmpty()
        mutableState.update { it.copy(defaultModel = default, model = default) }
    }

    fun onGoalChange(goal: String) = mutableState.update { it.copy(goal = goal) }

    fun onModelSelect(model: String) = mutableState.update { it.copy(model = model) }

    fun onLanguageSelect(language: TeachingLanguage) = mutableState.update { it.copy(teachingLanguage = language) }

    fun onKindMenuToggle(expanded: Boolean) = mutableState.update { it.copy(kindMenuExpanded = expanded) }

    fun onKindSelect(kind: SessionKind) {
        mutableState.update { it.copy(kind = kind, kindMenuExpanded = false, kindSupported = kind == SessionKind.Teaching) }
        viewModelScope.launch { applyDefaultModel() }
    }

    /** Starts the Session; [onStarted] gets its id once its goal is saved and its first Turn has begun. */
    fun onStart(onStarted: (String) -> Unit) {
        val current = mutableState.value
        if (!current.canStart) return
        viewModelScope.launch {
            val id = engine.start(
                StartSession(
                    kind = EngineKind.Teaching,
                    goal = current.goal,
                    model = current.model,
                    teachingLanguage = when (current.teachingLanguage) {
                        TeachingLanguage.English -> EngineLanguage.English
                        TeachingLanguage.PortugueseBrazil -> EngineLanguage.PortugueseBrazil
                    },
                ),
            )
            onStarted(id)
        }
    }
}
