# LearnHarness for Android

A native Android version of [LearnHarness](../README.md): Kotlin, Jetpack Compose and Material 3 Expressive, running the agent runtime entirely on the device ([ADR 0001](../docs/adr/0001-standalone-kotlin-agent-runtime.md)). The spec is [lucaskfp/learnharness#1](https://github.com/lucaskfp/learnharness/issues/1). Domain terms are defined in the root [GLOSSARY.md](../GLOSSARY.md).

This is a scaffold so far: the app opens into a navigation shell with three empty destinations (Sessions, Graph, Settings).

## Requirements

- JDK 17
- Android SDK with platform 37 (`compileSdk 37`, required by Material 3 1.5.0-rc01). `local.properties` points at the SDK and is not committed.

## Commands

Run these from `android/`:

```bash
./gradlew :app:assembleDebug            # build the APK
./gradlew :app:installDebug             # install on a connected device or emulator
./gradlew :app:testDebugUnitTest        # unit + screenshot tests (Robolectric)
./gradlew :app:recordRoborazziDebug     # re-render every @Preview to PNG baselines
./gradlew :app:verifyRoborazziDebug     # fail on visual differences from the baselines
./gradlew :app:compareRoborazziDebug    # write diff images without failing
./gradlew :app:lintDebug                # lint, including missing translations
```

## Screenshots

Every `@Preview` is also a screenshot test. Roborazzi's preview test generation (configured in `app/build.gradle.kts`) finds the previews with ComposablePreviewScanner and renders each one under Robolectric. `@LhPreviews` (`ui/preview/LhPreviews.kt`) expands a preview into 8 variants: light/dark × phone (411×891 dp) / tablet (1280×800 dp) × English / Brazilian Portuguese.

Roborazzi's preview test generation is itself marked `@ExperimentalRoborazziApi`. ComposablePreviewScanner is pinned to 0.9.3 because 0.10.0 is binary-incompatible with Roborazzi 1.78.0.

The baselines are committed in `app/src/test/screenshots/` and are the reviewable mockups. After an intentional UI change, run `recordRoborazziDebug` and commit the updated PNGs.

Robolectric runs at SDK 35: its SDK 36+ sandbox needs Java 21, and the toolchain here is JDK 17.

## Design system

- **Theme**: `MaterialExpressiveTheme` with `MotionScheme.expressive()` (`ui/theme/Theme.kt`).
- **Color**: the monochrome LearnHarness palette (`ui/theme/Color.kt`). The dark scheme uses the desktop app's hexes (`#141414`, `#1C1C1C`, `#2A2A2A`, `#434343`, `#8D8D8D`, `#E6E6E6`), and the light scheme mirrors them. The only hue is the error color. Dynamic color is off.
- **Type**: the stock Material 3 type scale, by the owner's decision. The desktop fonts (JetBrains Mono, Source Sans 3, CMU Serif) are not bundled.
- **Logo**: adaptive launcher icon with a monochrome (themed icon) layer, the Android 12+ splash screen, and the mark (`ui/LhMark.kt`) in the navigation rail header. All are derived from `build/icon-flat.svg`.
- **Icons**: Material Symbols Outlined `school`, `hub` and `settings` (filled when selected), copied as vector drawables (Apache-2.0) rather than pulling in `material-icons-extended`.
- **Top app bar**: `LargeFlexibleTopAppBar` (Expressive).
- **Navigation**: Navigation 3 back stack + `NavigationSuiteScaffold`. Compact width gets a `ShortNavigationBar`, medium and expanded widths get a `WideNavigationRail`. Sessions is the start destination, and Back from Graph or Settings returns to it.

## Material 3 version

`androidx.compose.material3:material3` is pinned to **1.5.0-rc01** in `gradle/libs.versions.toml`, ahead of the Compose BOM (2026.09.00, which maps to 1.4.0). Stable 1.4.0 ships none of the Expressive APIs: they were removed from it and moved to the 1.5.0 line. Move to 1.5.0 stable when it is released.

In 1.5.0-rc01, `MaterialExpressiveTheme`, the expressive `MotionScheme`, `ButtonGroup`, `SplitButton`, the FAB menu, floating toolbars, `FlexibleBottomAppBar`, wavy progress indicators, `ShortNavigationBar` and `WideNavigationRail` are stable. These APIs are **still experimental** (`@ExperimentalMaterial3ExpressiveApi`) and need an opt-in:

- `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults`
- `MaterialShapes` and `Morph.toPath` (shape morphing)
- `DropdownMenuItem` overloads that take `MenuItemShapes` (expressive menu items)
- `PullToRefreshDefaults.LoadingIndicator` and its loading-indicator colors
- an expressive `SliderDefaults.Thumb` overload

## License

AGPL-3.0, like the rest of this repository. LearnHarness is created by Amos Blomqvist and contributors at [amosblomqvist/learnharness](https://github.com/amosblomqvist/learnharness). This Android port lives in the `lucaskfp/learnharness` fork and follows upstream's prompts and tool contracts.
