# AGENTS.md

## Project Summary

- Project name: `secretaria`
- Type: Kotlin Multiplatform + Compose Multiplatform
- Main goal: gradually migrate the existing Android app to KMP
- Reference Android project: `/Users/alex/Proyectos/Android/Kotlin/Secretaria`
- Migration strategy: small, low-risk vertical slices first
- Primary repo owner profile:
  - experienced Kotlin/Android developer
  - comfortable with Compose and coroutines
  - first serious KMP migration on this product
  - prefers safe, incremental steps over large rewrites

## High-Level Intent

- Reuse as much code as possible across platforms.
- Keep platform hosts as thin as possible.
- Avoid touching Swift unless there is a real need.
- Do not try to migrate Firebase/auth/Hilt/DataStore too early.
- Prefer learning what is truly portable before introducing heavy shared infrastructure.

## Current Architecture

- `composeApp`
  - Shared KMP module.
  - Holds shared UI, shared models, shared presentation/state logic, shared resources, and desktop/web/iOS entrypoints.
  - Uses:
    - `org.jetbrains.kotlin.multiplatform`
    - `org.jetbrains.compose`
    - `com.android.kotlin.multiplatform.library`
- `androidApp`
  - Thin Android host app.
  - Owns:
    - `AndroidManifest.xml`
    - `MainActivity`
    - Android launcher icon/resources
    - `applicationId`
    - `versionCode`
    - `versionName`
- iOS host
  - Lives in `iosApp/`.
  - Xcode project:
    - `iosApp/iosApp.xcodeproj`
  - Swift host currently embeds the shared Compose view from:
    - `composeApp/src/iosMain/kotlin/com/chemecador/secretaria/MainViewController.kt`
  - Swift side entry point currently lives at:
    - `iosApp/iosApp/ContentView.swift`

## Package / Identity

- Base package: `com.chemecador.secretaria`
- Android app id: `com.chemecador.secretaria`
- Android shared library namespace: `com.chemecador.secretaria.shared`

## Why Android Has Its Own Module

- `composeApp` must stay focused on shared KMP code.
- `androidApp` is only the Android shell/host.
- This separation is intentional and correct for KMP:
  - shared module for reusable code
  - platform host per platform for packaging and app-specific integration
- `composeApp` is not an installable app and should not own Android app metadata.

## Current Product State

- First migrated vertical is implemented:
  - shared read-only notes lists screen
- Second migrated vertical is also implemented:
  - shared read-only notes screen for a selected list
- Not migrated yet:
  - Firebase
  - authentication
  - Hilt
  - DataStore
  - note detail
  - friends/sharing
  - complex navigation
  - backend integration

## Shared Feature Implemented

- Shared app entry:
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt`
- Shared feature package:
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/noteslists/`
- Second shared feature package:
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/notes/`
- Shared resources:
  - `composeApp/src/commonMain/composeResources/values/strings.xml`

### Notes Lists Feature

- Shared model:
  - `NotesListSummary`
- Shared sorting:
  - `SortOption`
  - `sortedByOption(...)`
- Shared state holder:
  - `NotesListsState`
- Shared logic:
  - `NotesListsPresenter`
- Shared repository contract:
  - `NotesListsRepository`
- Current repository implementation:
  - `FakeNotesListsRepository`

### Notes Feature

- Shared model:
  - `Note`
- Shared state holder:
  - `NotesState`
- Shared logic:
  - `NotesPresenter`
- Shared repository contract:
  - `NotesRepository`
- Current repository implementation:
  - `FakeNotesRepository`

## Current Navigation State

- There is currently no navigation library.
- `App.kt` uses a small piece of shared state:
  - `selectedListId`
  - `selectedListName`
- Flow today:
  - lists screen -> select list -> notes screen
  - notes screen -> back -> lists screen
- This is acceptable for the current two-screen state.
- If a third screen or more complex back stack is added, navigation should be revisited instead of growing ad-hoc state in `App.kt`.

## Presenter vs ViewModel

- Current shared logic uses a `Presenter`, not a `ViewModel`.
- Reason:
  - keep the shared layer platform-agnostic
  - avoid coupling early KMP code to Android lifecycle assumptions
  - keep the first migration step simple and portable
- The current presenter already behaves like a lightweight shared state holder:
  - exposes `StateFlow`
  - exposes user actions
  - contains UI state logic
- Revisit later only if a proper shared ViewModel abstraction becomes useful across multiple migrated features.

## Shared Architecture Conventions

- Package by feature, not by technical layer.
- Current feature packages follow a consistent shape:
  - model
  - repository interface
  - fake repository
  - state
  - presenter
  - screen
- Shared code currently follows a simple pattern:
  - immutable state via `data class`
  - repository methods return stdlib `Result<T>`
  - presenter exposes `StateFlow`
  - screens call `presenter.load()` from `LaunchedEffect`
- Presenters should stay free of Android lifecycle APIs unless there is an explicit architecture migration.

## Model / Data Conventions

- Do not copy Android or Firebase types into `commonMain`.
- Shared models should stay pure.
- Current conventions:
  - timestamps use `kotlin.time.Instant`
  - colors use `Long` ARGB values instead of Android/Compose-specific color types in the model
  - errors are represented via `Result<T>` + `errorMessage: String?` in UI state
- Example:
  - `notes/Note.kt` uses `Instant` and `Long color`
- Reuse `noteslists/formatNotesListDate()` for date formatting instead of creating new formatters.

## UI / Resource Conventions

- Do not hardcode user-facing strings in shared UI.
- Shared strings belong in:
  - `composeApp/src/commonMain/composeResources/values/strings.xml`
- The app language is currently Spanish.
- Current screens use private helper composables in the same file for small supporting UI blocks.

## Android/KMP Build Decisions

- `composeApp` uses `com.android.kotlin.multiplatform.library`.
- `androidApp` no longer applies `org.jetbrains.kotlin.android`.
- `settings.gradle.kts` uses:
  - `@file:Suppress("UnstableApiUsage")`
  - filtered `google()` repositories with `includeGroupByRegex(...)`
- This was done to reduce Gradle/AGP 9 warnings and align with the newer Android/KMP plugin direction.

## Commit Style in This Repo

- Follow the existing repository history style unless the user asks otherwise.
- Current real examples from `git log`:
  - `Initial commit`
  - `Mock lists`
  - `Mock notes`
- Preferred style:
  - short
  - capitalized
  - no conventional-commit prefix
  - no long body unless explicitly requested

## Important Runtime/Build Pitfalls

### Compose Resources on Android

- When using the Android-KMP library plugin in `composeApp`, Android resources must be explicitly enabled:
  - `androidResources { enable = true }`
- Without this, Android may compile but crash at runtime with:
  - `org.jetbrains.compose.resources.MissingResourceException`
  - missing `.cvr` files such as `strings.commonMain.cvr`
- Current fix lives in:
  - `composeApp/build.gradle.kts`

### Android Host Icon

- The real Android launcher icon was copied from the original Android app.
- Current icon:
  - `androidApp/src/main/res/mipmap/ic_launcher.webp`
- `androidApp/src/main/AndroidManifest.xml` points both `icon` and `roundIcon` to:
  - `@mipmap/ic_launcher`
- Old template launcher resources were intentionally removed from `androidApp`.

### Material Icons

- The project currently does not rely on the Material icons extended package.
- In the shared notes screen, the back button is implemented with a text arrow (`←`) rather than a Material icon.
- If more icon-heavy UI is added later, re-evaluate whether adding icon dependencies is worth it.

### ComposeApp Android Task Names

- After migrating to the Android-KMP plugin, Android-related task names in `composeApp` changed.
- Important example:
  - old style tasks like `:composeApp:testDebugUnitTest` are no longer the right mental model for the shared Android target
  - use tasks such as `:composeApp:compileAndroidMain`

### JS/Wasm Browser Tests

- Browser test tasks may require Chrome or another configured browser.
- If the environment lacks Chrome, browser test tasks can fail even when compilation is fine.
- For routine validation, compile tasks are usually enough:
  - `:composeApp:compileKotlinJs`
  - `:composeApp:compileKotlinWasmJs`

### Presenter Scope Caveat

- Presenters currently do not own a `CoroutineScope`.
- This is fine for mock/in-memory repositories.
- Once real I/O is introduced, cancellation/lifecycle behavior should be revisited.
- At that point, likely choices are:
  - inject/manage a scope explicitly
  - migrate to a shared ViewModel approach
  - introduce a stronger shared navigation/state framework

## Current Validation Commands

### Android host

- `./gradlew :androidApp:assembleDebug`

### Shared Android target

- `./gradlew :composeApp:compileAndroidMain`

### JVM/Desktop

- `./gradlew :composeApp:compileKotlinJvm :composeApp:jvmTest`

### iOS simulator

- `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:iosSimulatorArm64Test`

### JS/Wasm compile verification

- `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs`

### Broad validation command currently known to work

- `./gradlew :composeApp:compileAndroidMain :composeApp:jvmTest :composeApp:iosSimulatorArm64Test :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinJvm :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs :androidApp:assembleDebug`

## Test Locations and Patterns

- Shared tests live in:
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/noteslists/`
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/notes/`
- Current test focus:
  - presenter loading transitions
  - empty/content/error states
  - sorting logic
  - date formatting
- Existing presenter tests use controlled repositories with `CompletableDeferred` gates to assert intermediate loading states.

## Source of Truth for the Original App

- Use the Android project at:
  - `/Users/alex/Proyectos/Android/Kotlin/Secretaria`
- That project is the reference for:
  - domain concepts
  - screen behavior
  - existing UI flows
  - current Android resources and assets
- It is also the reference for future feature scope:
  - lists
  - notes
  - sharing
  - friends
  - auth
  - settings/about
- Do not copy Android/Firebase-specific types directly into `commonMain`.

## Original Android App Context

- The original Android app is a collaborative note/list manager.
- Original stack includes, among other things:
  - Firebase Auth
  - Firestore
  - FCM
  - Hilt
  - DataStore
  - Jetpack Compose
- Treat that app as a business/domain reference, not as a requirement to mirror the exact same stack in KMP.

## Migration Rules for Future Sessions

- Prefer small vertical slices.
- Move only logic that is truly portable into `commonMain`.
- If code depends on Android, Firebase, JVM APIs, or other platform-only APIs:
  - keep it out of `commonMain`
  - or hide it behind a shared interface
- Avoid introducing new libraries into shared code unless KMP compatibility is confirmed.
- Preserve the idea that `androidApp` is a host, not the place for reusable business logic.
- Prefer fake/local data first, then introduce real backends behind interfaces.

## Good Next Steps

- Migrate a third small vertical, likely one of:
  - create list locally
  - note detail / note editing
  - basic shared navigation cleanup
- Only after a couple of successful small migrations, consider:
  - shared navigation strategy
  - shared persistence abstractions
  - Firebase/auth integration contracts
  - whether a shared ViewModel layer is actually worth it

## Known Remaining Warnings

- There are still AGP-related warnings coming from legacy/deprecated properties in `gradle.properties`.
- Those are separate from the KMP/module structure work already done.
- They should be cleaned incrementally, not mixed into feature work unless needed.

## Files Worth Reading First

- `composeApp/build.gradle.kts`
- `androidApp/build.gradle.kts`
- `settings.gradle.kts`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/noteslists/`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/notes/`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `androidApp/src/main/AndroidManifest.xml`
- `iosApp/iosApp/ContentView.swift`

## Notes for Future Agents

- This repo is early-stage KMP, so avoid over-architecting.
- Favor correctness, portability, and small safe steps over completeness.
- If a build/runtime issue appears on Android and mentions missing Compose resources, check `androidResources { enable = true }` first.
- If changing Android host resources, remember that `androidApp` now owns the real launcher assets.
- If a change affects feature behavior, compare against the original Android project for business intent before introducing new abstractions.
