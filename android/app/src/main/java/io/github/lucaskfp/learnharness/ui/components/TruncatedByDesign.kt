package io.github.lucaskfp.learnharness.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Marks text that is cut to a line limit on purpose, such as a Session's goal in the list. All
 * other text must fit; the mockup text-fit test fails on any clipped text without this mark.
 */
val TruncatedByDesign = SemanticsPropertyKey<Boolean>("TruncatedByDesign")

private var SemanticsPropertyReceiver.truncatedByDesign by TruncatedByDesign

fun Modifier.truncatedByDesign(): Modifier = semantics { truncatedByDesign = true }
