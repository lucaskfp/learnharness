@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R

/** The live Plan in full screen, opened from the Session's top bar on phones. */
@Composable
fun PlanScreen(plan: Plan, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(pluralStringResource(R.plurals.plan_node_count, plan.nodes.size, plan.nodes.size)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        // Pan and zoom come with the engine; the mockup scrolls.
        PlanGraph(
            plan,
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}
