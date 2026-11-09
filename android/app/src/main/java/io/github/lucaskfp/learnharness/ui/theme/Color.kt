package io.github.lucaskfp.learnharness.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// LearnHarness is monochrome: the dark scheme uses the desktop app's hexes
// (app/globals.css), the light scheme mirrors them. The only hue is error.

private val Ink = Color(0xFF141414)
private val Raised = Color(0xFF1C1C1C)
private val RaisedHigh = Color(0xFF2A2A2A)
private val Edge = Color(0xFF434343)
private val Muted = Color(0xFF8D8D8D)
private val Paper = Color(0xFFE6E6E6)

// Desktop --destructive: oklch(0.704 0.191 22.216).
private val ErrorDark = Color(0xFFFF6467)
private val ErrorLight = Color(0xFFC10007)

val LhDarkColors: ColorScheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Edge,
    onPrimaryContainer = Paper,
    inversePrimary = Ink,
    secondary = Muted,
    onSecondary = Ink,
    secondaryContainer = RaisedHigh,
    onSecondaryContainer = Paper,
    tertiary = Paper,
    onTertiary = Ink,
    tertiaryContainer = RaisedHigh,
    onTertiaryContainer = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Raised,
    onSurfaceVariant = Muted,
    surfaceTint = Paper,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    error = ErrorDark,
    onError = Ink,
    errorContainer = Color(0xFF5C1214),
    onErrorContainer = Color(0xFFFFDAD8),
    outline = Edge,
    outlineVariant = RaisedHigh,
    scrim = Color.Black,
    surfaceBright = Color(0xFF303030),
    surfaceDim = Ink,
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Raised,
    surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = RaisedHigh,
)

private val LightPaper = Color(0xFFF5F5F5)
private val LightRaised = Color(0xFFEDEDED)
private val LightRaisedHigh = Color(0xFFE0E0E0)
private val LightEdge = Color(0xFFC4C4C4)
private val LightMuted = Color(0xFF5F5F5F)

val LhLightColors: ColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = LightPaper,
    primaryContainer = LightEdge,
    onPrimaryContainer = Ink,
    inversePrimary = Paper,
    secondary = LightMuted,
    onSecondary = LightPaper,
    secondaryContainer = LightRaisedHigh,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    onTertiary = LightPaper,
    tertiaryContainer = LightRaisedHigh,
    onTertiaryContainer = Ink,
    background = LightPaper,
    onBackground = Ink,
    surface = LightPaper,
    onSurface = Ink,
    surfaceVariant = LightRaised,
    onSurfaceVariant = LightMuted,
    surfaceTint = Ink,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410004),
    outline = LightEdge,
    outlineVariant = LightRaisedHigh,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDADADA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F0F0),
    surfaceContainer = LightRaised,
    surfaceContainerHigh = Color(0xFFE7E7E7),
    surfaceContainerHighest = LightRaisedHigh,
)
