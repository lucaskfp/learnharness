package io.github.lucaskfp.learnharness.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.lucaskfp.learnharness.ui.destinations.DestinationScreen
import io.github.lucaskfp.learnharness.ui.navigation.TopLevelDestination
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme

@Composable
fun LearnHarnessApp() {
    LearnHarnessTheme { NavigationShell() }
}

/**
 * Top-level navigation: Sessions is the start destination; another top-level destination sits
 * on top of it, so Back from Graph or Settings returns to Sessions.
 */
@Composable
private fun NavigationShell() {
    val backStack = rememberNavBackStack(TopLevelDestination.Sessions)
    val current = backStack.last() as TopLevelDestination
    val suiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val isRail = suiteType == NavigationSuiteType.WideNavigationRailCollapsed ||
        suiteType == NavigationSuiteType.WideNavigationRailExpanded

    NavigationSuiteScaffold(
        navigationSuiteType = suiteType,
        // The rail's header carries the mark; on a navigation bar this slot would float above it.
        primaryActionContent = { if (isRail) RailHeader() },
        navigationItems = {
            TopLevelDestination.entries.forEach { destination ->
                NavigationSuiteItem(
                    selected = destination == current,
                    navigationSuiteType = suiteType,
                    onClick = { backStack.select(destination) },
                    icon = {
                        val icon = if (destination == current) destination.selectedIcon else destination.icon
                        Icon(painterResource(icon), contentDescription = null)
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            entryProvider = entryProvider {
                TopLevelDestination.entries.forEach { destination ->
                    entry(destination) { DestinationScreen(destination) }
                }
            },
        )
    }
}

/** The mark, centered over the collapsed rail's items. */
@Composable
private fun RailHeader() {
    Box(Modifier.width(RailWidth).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        LhMark()
    }
}

private val RailWidth = 96.dp

private fun NavBackStack<NavKey>.select(destination: TopLevelDestination) {
    clear()
    add(TopLevelDestination.Sessions)
    if (destination != TopLevelDestination.Sessions) add(destination)
}
