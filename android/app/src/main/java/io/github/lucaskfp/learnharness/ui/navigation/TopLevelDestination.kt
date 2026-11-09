package io.github.lucaskfp.learnharness.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import io.github.lucaskfp.learnharness.R
import kotlinx.serialization.Serializable

/** The three destinations reachable from the navigation bar or rail. */
@Serializable
enum class TopLevelDestination(
    @StringRes val label: Int,
    @StringRes val emptyState: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) : NavKey {
    Sessions(R.string.destination_sessions, R.string.empty_sessions, R.drawable.ic_sessions, R.drawable.ic_sessions_filled),
    Graph(R.string.destination_graph, R.string.empty_graph, R.drawable.ic_graph, R.drawable.ic_graph_filled),
    Settings(R.string.destination_settings, R.string.empty_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
}
