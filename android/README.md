# LearnHarness for Android

A native Android version of [LearnHarness](../README.md): Kotlin, Jetpack Compose and Material 3 Expressive, running the agent runtime entirely on the device ([ADR 0001](../docs/adr/0001-standalone-kotlin-agent-runtime.md)). The spec is [lucaskfp/learnharness#1](https://github.com/lucaskfp/learnharness/issues/1). Domain terms are defined in the root [GLOSSARY.md](../GLOSSARY.md).

This is a scaffold so far: the app opens into a navigation shell with three empty destinations (Sessions, Graph, Settings). The first screens exist as mockups only: stateless composables rendered from fake data in `@Preview`s (Setup, Sessions, New Session, and a Teaching Session through Probe, Plan and Teach; see `ui/setup`, `ui/sessions`, `ui/newsession`, `ui/teaching`, `ui/preview/FakeData.kt` and `ui/preview/FakeTeaching.kt`). The fake learner and agent content (Session goals, file names, questions, explanations) is in `fake_*` string resources, so each locale's mockups show it in that language.

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

Every `@Preview` is also a screenshot test. Roborazzi's preview test generation (configured in `app/build.gradle.kts`) finds the previews with ComposablePreviewScanner and renders each one under Robolectric. Three annotations in `ui/preview/LhPreviews.kt` set the variants:

- `@LhPreviews`: 8 variants, light/dark × phone (411×891 dp) / tablet (1280×800 dp) × English / Brazilian Portuguese. Used by screens with a tablet layout (Sessions, and Teach with the Plan beside the lesson).
- `@LhPhonePreviews`: the 4 phone variants. Used by Setup, New Session, Probe and Plan.
- `@LhLargeFontPreview`: Brazilian Portuguese, light, at font scale 1.5, on an extra-tall phone (411×2000 dp) so a scrolling screen fits in one PNG. Added to every mockup.

Roborazzi's preview test generation is itself marked `@ExperimentalRoborazziApi`. ComposablePreviewScanner is pinned to 0.9.3 because 0.10.0 is binary-incompatible with Roborazzi 1.78.0.

The baselines are committed in `app/src/test/screenshots/` and are the reviewable mockups. After an intentional UI change, run `recordRoborazziDebug` and commit the updated PNGs.

Robolectric runs at SDK 35: its SDK 36+ sandbox needs Java 21, and the toolchain here is JDK 17.

Robolectric renders in keyboard (non-touch) mode, so Compose gives initial focus to the first focusable element. That is why the Sessions search field shows a caret in its screenshots; on a device in touch mode it does not.

`MockupTextFitTest` renders every mockup state in Brazilian Portuguese (the longer locale) at font scale 1.0 and 1.5 and fails if any text is lost: past its line limit or height, ellipsized, or with a word split across lines. Text cut on purpose, such as a Session's goal in the list, is marked with `Modifier.truncatedByDesign()` (`ui/components/TruncatedByDesign.kt`). It runs with Robolectric's NATIVE graphics, which measure text for real. Add each new mockup state to its list.

## Design system

- **Theme**: `MaterialExpressiveTheme` with `MotionScheme.expressive()` (`ui/theme/Theme.kt`).
- **Color**: the monochrome LearnHarness palette (`ui/theme/Color.kt`). The dark scheme uses the desktop app's hexes (`#141414`, `#1C1C1C`, `#2A2A2A`, `#434343`, `#8D8D8D`, `#E6E6E6`), and the light scheme mirrors them. The only hue is the error color. Dynamic color is off.
- **Type**: the stock Material 3 type scale, by the owner's decision. The desktop fonts (JetBrains Mono, Source Sans 3, CMU Serif) are not bundled.
- **Logo**: adaptive launcher icon with a monochrome (themed icon) layer, the Android 12+ splash screen, and the mark (`ui/LhMark.kt`) in the navigation rail header. All are derived from `build/icon-flat.svg`.
- **Icons**: Material Symbols Outlined, copied as vector drawables (Apache-2.0) rather than pulling in `material-icons-extended`: `school`, `hub` and `settings` (filled when selected) for navigation, plus `search`, `add`, `close`, `check`, `check_circle`, `error`, `delete`, `attach_file`, `picture_as_pdf`, `image`, `keyboard_arrow_down`, `fitness_center` (Practice Session) and `hourglass_top` (Job pending); for the Teaching Session `arrow_back`, `mic`, `send`, `stop`, `open_in_full`, `account_tree` (Plan), `travel_explore` (research Job), `draw` (visual Job) and `smart_display` (demo Job).
- **Feedback colors**: the only hues besides error (`ui/theme/FeedbackColors.kt`), by the owner's decision: the desktop's red-to-emerald confidence ramp on the confidence slider, and green for a correct quiz answer.
- **Rich text**: agent prose is Markdown rendered by [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) (`ui/components/RichMarkdown.kt`). ```` ```math ```` fences are drawn with [JLatexMath for Android](https://github.com/noties/jlatexmath-android). Mermaid needs a WebView, which Robolectric cannot render, so ```` ```mermaid ```` fences are drawn by `MermaidStandIn`, which handles `sequenceDiagram` messages only and is a mockup stand-in.
- **Plan**: `layoutPlan` (`ui/teaching/PlanLayout.kt`, unit-tested) puts each node one layer below its deepest prerequisite; `PlanGraph` draws it top to bottom.
- **Top app bar**: `LargeFlexibleTopAppBar` (Expressive).
- **List–detail**: `ListDetailPaneScaffold` from `androidx.compose.material3.adaptive:adaptive-layout` (1.3.0, the same adaptive version the navigation suite pulls in). Sessions is one pane on phones and list + detail on wider windows.
- **Navigation**: Navigation 3 back stack + `NavigationSuiteScaffold`. Compact width gets a `ShortNavigationBar`, medium and expanded widths get a `WideNavigationRail`. Sessions is the start destination, and Back from Graph or Settings returns to it.

## Pinned versions

multiplatform-markdown-renderer is pinned to **0.39.2**: 0.40.0 and later are compiled for Java 21, and the unit tests run on JDK 17. Move up when the toolchain does.

## Material 3 version

`androidx.compose.material3:material3` is pinned to **1.5.0-rc01** in `gradle/libs.versions.toml`, ahead of the Compose BOM (2026.09.00, which maps to 1.4.0). Stable 1.4.0 ships none of the Expressive APIs: they were removed from it and moved to the 1.5.0 line. Move to 1.5.0 stable when it is released.

In 1.5.0-rc01, `MaterialExpressiveTheme`, the expressive `MotionScheme`, `ButtonGroup`, `SplitButton`, the FAB menu, floating toolbars, `FlexibleBottomAppBar`, wavy progress indicators, `ShortNavigationBar` and `WideNavigationRail` are stable. These APIs are **still experimental** (`@ExperimentalMaterial3ExpressiveApi`) and need an opt-in:

- `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults`
- `MaterialShapes` and `Morph.toPath` (shape morphing)
- `DropdownMenuItem` overloads that take `MenuItemShapes` (expressive menu items)
- `PullToRefreshDefaults.LoadingIndicator` and its loading-indicator colors
- an expressive `SliderDefaults.Thumb` overload

`ExposedDropdownMenuBoxScope.ExposedDropdownMenu` does not resolve from Kotlin in 1.5.0-rc01, so `ui/components/DropdownField.kt` uses a plain `DropdownMenu` sized with `exposedDropdownSize()`.

## License

AGPL-3.0, like the rest of this repository. LearnHarness is created by Amos Blomqvist and contributors at [amosblomqvist/learnharness](https://github.com/amosblomqvist/learnharness). This Android port lives in the `lucaskfp/learnharness` fork and follows upstream's prompts and tool contracts.
