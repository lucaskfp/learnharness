# LearnHarness for Android

A native Android version of [LearnHarness](../README.md): Kotlin, Jetpack Compose and Material 3 Expressive, running the agent runtime entirely on the device ([ADR 0001](../docs/adr/0001-standalone-kotlin-agent-runtime.md)). The spec is [lucaskfp/learnharness#1](https://github.com/lucaskfp/learnharness/issues/1). Domain terms are defined in the root [GLOSSARY.md](../GLOSSARY.md).

The first Teaching Turn runs end to end ([#7](https://github.com/lucaskfp/learnharness/issues/7)): on first run the learner enters an Anthropic API key, starts a Teaching Session with a goal, the Prober asks a question, and the learner answers it with confidence and thinking. Graph and Settings are still empty destinations. The Session then runs from Probe to Plan to Teach ([#8](https://github.com/lucaskfp/learnharness/issues/8)): a successful `probe_done` hands off to the Teacher, whose `plan_init` Plan (a validated DAG, replaceable) is drawn with pan and zoom, and quizzes tagged with Plan nodes move the position. Practice Sessions, Jobs and the other providers come with later issues.

The screens are stateless composables drawn from fake data in `@Preview`s (the mockups) and are wired to the engine by thin routes and ViewModels (`ui/AppRoot.kt`, `*Route.kt`, `*ViewModel.kt`; the fake content lives in `ui/preview/FakeData.kt`, `ui/preview/FakeTeaching.kt` and the `fake_*` string resources, so each locale's mockups show it in that language).

## Session engine

`engine/` is the deep module that owns Sessions end to end; the UI only calls `SessionEngine` (`start`, `submit`, `observe`, `sessions`, `delete`).

- **Turns** follow upstream: a Turn builds a fresh agent from the saved history, loops over model calls and tools, and saves its output once, at settlement (partial output too, if cancelled or failed). The learner's input is saved before inference. One Turn runs per Session; a second input throws `TurnInProgressException`.
- **History** is stored in Room as upstream-shaped message JSON (`engine/model/Messages.kt`, one row per message), so export and import can round-trip to the desktop format. The pending question is derived from the history, so a Session resumes mid-question after a restart.
- **Agent tools** (`AgentTools`): the Prober has `probe` (or `recall` under the free-recall mechanic), `ask_user_question` and `probe_done`; the Teacher has `quiz` (or `explain`), `ask_user_question` and `plan_init`. A matching successful `probe_done` switches the request to the Teacher and wakes it with upstream's handoff text; `plan_init` parses upstream's mermaid subset into a validated DAG whose tree rides in the result's `details`, and where the lesson stands is derived from node-tagged `quiz`/`explain` calls. Arguments are healed, validated against the imported JSON Schema, then checked against upstream's authoring rules; a bad call returns to the model as an error. Options are shuffled and graded on the device (`engine/question/QuestionCore.kt`, a port of upstream's `question-core.ts`); the model reads only what the learner added.
- **Provider port** (`engine/provider`): `AnthropicProvider` streams the Messages API over SSE with text, thinking (adaptive on current models, a token budget on older ones), tool calls and usage. API keys are stored AES-GCM encrypted with an Android Keystore key (`engine/store/KeyVault.kt`); Robolectric has no Keystore, so tests use a fake vault.
- **Upstream imports**: prompts are copied into the APK by the `syncUpstreamPrompts` Gradle task (`src/agent/system-prompt.md` and the `prompts/*/` fragments, composed in upstream's order by `PromptComposer`, plus a teaching-language instruction). Tool names, descriptions and input schemas are read from `app/src/main/assets/upstream/tools.json`, generated from the real tool modules. After a tool contract changes upstream, run `npm ci --ignore-scripts && npx tsx scripts/export-android-contracts.ts` from the repository root; `tests/android-contracts.test.ts` fails when the file is stale.
- **Tests** (`app/src/test/.../engine`) drive the engine through its public interface with a scripted fake provider and an in-memory Room database; only the model is faked. `AnthropicProviderTest` checks the SSE adapter against a local server, and `AppRootTest` walks the wired app from a fresh install to an answered question.

## Mockups

The mockups of the nine screens are designed in FigCraft, in [mockups/learnharness.figcraft](mockups/learnharness.figcraft), with one page per flow (Setup, Sessions, New Session, Teaching Session, Practice, Graph, Settings) after a read-me and a foundations page. Each state is a section with four frames: Light/Dark by EN/PT-BR. Colors are variables in a `Tema` collection (Light/Dark modes) and every string is a variable in an `Idioma` collection (EN/PT-BR modes), so each screen is built once and its copies only switch modes. A PNG of each state is exported to [mockups/png/](mockups/png/). The Compose previews below were the first mockups of Setup, Sessions, New Session and the Teaching Session (#3, #4); they stay as starting code and screenshot baselines.

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
- **Icons**: Material Symbols Outlined, copied as vector drawables (Apache-2.0) rather than pulling in `material-icons-extended`: `school`, `hub` and `settings` (filled when selected) for navigation, plus `search`, `add`, `close`, `check`, `check_circle`, `error`, `delete`, `attach_file`, `picture_as_pdf`, `image`, `keyboard_arrow_down`, `fitness_center` (Practice Session) and `hourglass_top` (Job pending); for the Teaching Session `arrow_back`, `mic`, `send`, `stop`, `open_in_full`, `search` (also find in a Session, in the Session's top bar), `account_tree` (Plan), `travel_explore` (research Job), `draw` (visual Job) and `smart_display` (demo Job).
- **Feedback colors**: the only hues besides error (`ui/theme/FeedbackColors.kt`), by the owner's decision: the desktop's red-to-emerald confidence ramp on the confidence slider, and green for a correct quiz answer. The fake whiteboard photo in the Teach mockups has a photo's colors, not the theme's; the Teacher's labels on it use the brand ink (`#141414`) so they read on either theme.
- **Rich text**: agent prose is Markdown rendered by [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) (`ui/components/RichMarkdown.kt`). Display math (```` ```math ```` fences, or `$$…$$`, rewritten to a fence by `fenceDisplayMath`) is drawn with [JLatexMath for Android](https://github.com/noties/jlatexmath-android). Mermaid needs a WebView, which Robolectric cannot render, so ```` ```mermaid ```` fences are drawn by `MermaidStandIn`, which handles `sequenceDiagram` messages only and is a mockup stand-in.
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
