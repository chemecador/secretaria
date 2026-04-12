# AGENTS.md

## Project Summary

- Project name: `secretaria`
- Type: Kotlin Multiplatform + Compose Multiplatform
- Main goal: gradually migrate the existing Android app to KMP
- Reference Android project: MAC: `/Users/alex/Proyectos/Android/Kotlin/Secretaria`, Windows: C:\Proyectos\Android\Kotlin\secretaria-android
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
- Firebase Auth is now integrated on Android and JVM/Desktop; Firestore is integrated on Android for notes lists and notes.
- Other Firebase services (FCM) are not yet migrated.
- Do not try to migrate Hilt/DataStore too early.
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

- Notes lists screen (fully implemented):
  - read, create, delete lists
  - sorting by name/date
- Notes screen (fully implemented):
  - read, create, delete notes for a selected list
  - ordered/unordered display
- Note detail screen (fully implemented):
  - view note detail
  - edit title and content
  - delete with confirmation
- Interaction patterns:
  - FAB (+) to create via dialog
  - click on card to open detail
  - long-press on card to delete via confirmation dialog
- Partially migrated:
  - Firebase Auth (Android + JVM/Desktop for email/password and anonymous auth; Google Sign-In pending)
  - Firestore (Android only — notes lists and notes; JVM/iOS/Web still use fakes)
- Not migrated yet:
  - FCM
  - Hilt
  - DataStore
  - friends/sharing
  - backend integration

## Shared Feature Implemented

- Shared app entry:
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt`
- Shared feature packages:
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/login/`
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/noteslists/`
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
  - `NotesListsViewModel` (extends `androidx.lifecycle.ViewModel`)
- Shared repository contract:
  - `NotesListsRepository`
- Repository implementations:
  - `FirestoreNotesListsRepository` (Android, in `androidMain`)
  - `FakeNotesListsRepository` (JVM, iOS, Web)
- Platform selection:
  - `expect fun createNotesListsRepository(authRepository: AuthRepository): NotesListsRepository` in `commonMain`
  - Android `actual` returns `FirestoreNotesListsRepository`
  - JVM/iOS/Web `actual` returns `FakeNotesListsRepository`

### Notes Feature

- Shared model:
  - `Note`
- Shared state holder:
  - `NotesState`
- Shared logic:
  - `NotesViewModel` (extends `androidx.lifecycle.ViewModel`)
- Shared repository contract:
  - `NotesRepository` (getNotesForList, createNote, deleteNote, updateNote)
- Repository implementations:
  - `FirestoreNotesRepository` (Android, in `androidMain`)
  - `FakeNotesRepository` (JVM, iOS, Web)
- Platform selection:
  - `expect fun createNotesRepository(authRepository: AuthRepository): NotesRepository` in `commonMain`
  - Android `actual` returns `FirestoreNotesRepository`
  - JVM/iOS/Web `actual` returns `FakeNotesRepository`
- Screens:
  - `NotesScreen` — list of notes with create/delete
  - `NoteDetailScreen` — edit title/content, delete with confirmation

### Login / Auth Feature

- Shared contract:
  - `AuthRepository` (login, signup, loginWithGoogle, loginAsGuest, currentUserId)
- Shared error model:
  - `AuthError` enum (INVALID_USER, WRONG_PASSWORD, USER_ALREADY_EXISTS, WEAK_PASSWORD, INVALID_EMAIL, NOT_SUPPORTED, UNKNOWN)
  - `AuthException` wraps `AuthError` for use in `Result.failure`
- Shared state holder:
  - `LoginState` (isLoading, error: AuthError?, isLoggedIn)
- Shared logic:
  - `LoginViewModel` (extends `androidx.lifecycle.ViewModel`)
- Platform selection:
  - `expect fun createAuthRepository(): AuthRepository` in `commonMain`
  - Android `actual` returns `FirebaseAuthRepository` (uses Firebase Auth SDK)
  - JVM `actual` returns `FirebaseRestAuthRepository` (uses Firebase Auth REST API)
  - iOS/Web `actual` returns `FakeAuthRepository`
- Android-only implementation:
  - `composeApp/src/androidMain/kotlin/com/chemecador/secretaria/login/FirebaseAuthRepository.kt`
  - Uses `FirebaseAuth.getInstance()` with `.await()` from `kotlinx-coroutines-play-services`
  - Supports: email/password login, email/password signup, anonymous login
  - Google Sign-In: returns `NOT_SUPPORTED` (deferred — requires Activity context + Credential Manager)
- JVM-only implementation:
  - `composeApp/src/jvmMain/kotlin/com/chemecador/secretaria/login/FirebaseRestAuthRepository.kt`
  - Uses Firebase Identity Toolkit REST API via `java.net.http.HttpClient`
  - Supports: email/password login, email/password signup, anonymous login
  - Google Sign-In: returns `NOT_SUPPORTED`
  - API key is read from `-Dsecretaria.firebaseApiKey=...` or `SECRETARIA_FIREBASE_API_KEY`
  - `currentUserId` is kept in memory only (no desktop session persistence yet)
- Fake implementation:
  - `FakeAuthRepository` — always succeeds after a short delay, used on iOS/Web and in tests that need a lightweight stub
- Error localization:
  - `LoginScreen` maps `AuthError` to string resources via `AuthError.toStringRes()`
  - Error strings are in `strings.xml` (Spanish)
- Screen:
  - `LoginScreen` — email/password fields, login/signup buttons, Google Sign-In button, guest access link
- Tests:
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/login/LoginViewModelTest.kt`
  - `composeApp/src/jvmTest/kotlin/com/chemecador/secretaria/login/FirebaseRestAuthRepositoryTest.kt`

## Current Navigation State

- There is currently no navigation library.
- `App.kt` uses a sealed `Screen` class to manage navigation between four screens:
  - `Screen.Login` — login/signup screen (initial screen)
  - `Screen.Lists` — notes lists screen
  - `Screen.Notes(listId, listName, isOrdered)` — notes for a selected list
  - `Screen.NoteDetail(listId, listName, isOrdered, note)` — edit/delete a single note
- Flow today:
  - login screen -> lists screen -> select list -> notes screen -> click note -> note detail screen
  - back navigation at each level (except login)
- This is acceptable for the current four-screen state.
- If more screens or complex back stacks are added, a navigation library should be considered.

## ViewModel layer

- Shared logic lives in `androidx.lifecycle.ViewModel` subclasses in `commonMain`.
- The `androidx.lifecycle` multiplatform artifacts (`viewmodelCompose`, `runtimeCompose`, version `2.10.0`) are already in `composeApp/build.gradle.kts` commonMain deps, so this works on Android, iOS, JVM/Desktop, JS and Wasm.
- Conventions:
  - expose state via `StateFlow<State>` backed by a private `MutableStateFlow`
  - `load()` / `refresh()` are plain (non-suspend) functions that launch on `viewModelScope`
  - the actual I/O lives in `private suspend fun fetch...()`
  - screens call `viewModel.load()` from a `LaunchedEffect(viewModel)`
  - instantiate via `androidx.lifecycle.viewmodel.compose.viewModel { ... }` in `App.kt`; use `viewModel(key = ...)` when the VM depends on a screen parameter (e.g. `listId`)
- Do not introduce Hilt/Koin yet — constructors take their dependencies directly from the `viewModel { }` factory lambda.

## Shared Architecture Conventions

- Package by feature, not by technical layer.
- Current feature packages follow a consistent shape:
  - model
  - repository interface
  - fake repository
  - state
  - viewmodel
  - screen
- Shared code currently follows a simple pattern:
  - immutable state via `data class`
  - repository methods return stdlib `Result<T>`
  - viewmodel extends `androidx.lifecycle.ViewModel` and exposes `StateFlow`
  - `load()`/`refresh()` are non-suspend and launch on `viewModelScope`
  - screens call `viewModel.load()` from `LaunchedEffect(viewModel)`
- Platform-specific implementations use `expect`/`actual` factory functions:
  - pattern: `expect fun createXxxRepository(): XxxRepository` in `commonMain`
  - each platform provides an `actual fun` returning the appropriate implementation
  - Android returns the real (e.g. Firebase-backed) implementation
  - other platforms return `FakeXxxRepository` until real implementations are ready
  - `App.kt` calls the factory via `remember { createXxxRepository() }` and injects into ViewModels

## Model / Data Conventions

- Do not copy Android or Firebase types into `commonMain`.
- Shared models should stay pure.
- Current conventions:
  - timestamps use `kotlin.time.Instant`
  - colors use `Long` ARGB values instead of Android/Compose-specific color types in the model
  - errors are represented via `Result<T>` + typed error enums in UI state (e.g. `AuthError?` in `LoginState`)
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

- `compose.materialIconsExtended` is declared in `commonMain.dependencies` in `composeApp/build.gradle.kts`.
- Use `Icons.AutoMirrored.Filled.ArrowBack` for back navigation buttons.
- Use `Icons.Filled.Add` for FAB create buttons.
- Use `Icons.Filled.AlternateEmail` / `Icons.Filled.Lock` for email/password field leading icons.
- Do not use text characters (`←`, `+`, `@`, `*`) as icon substitutes.

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

### ViewModel scope

- ViewModels use their built-in `viewModelScope`, which is cancelled automatically when the `viewModel { }` owner leaves composition.
- This means fake/mock loads and future real I/O both get proper cancellation for free.
- `viewModelScope` uses `Dispatchers.Main.immediate` by default, so tests must install a test dispatcher via `Dispatchers.setMain(StandardTestDispatcher())` in `@BeforeTest` and `Dispatchers.resetMain()` in `@AfterTest`, then drive coroutines with `runCurrent()` / `advanceUntilIdle()`.

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
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/login/`
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/noteslists/`
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/notes/`
- Current test focus:
  - viewmodel loading transitions
  - empty/content/error states
  - sorting logic
  - date formatting
- ViewModel tests install a `StandardTestDispatcher` as Main (`Dispatchers.setMain` / `resetMain`) and use `runTest(dispatcher) { ... }` plus `runCurrent()` / `advanceUntilIdle()` to drive `viewModelScope`.
- Controlled repositories still use `CompletableDeferred` gates to assert intermediate loading states.

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

## Firebase Integration State

- Firebase Auth is live on Android via `FirebaseAuthRepository` in `androidMain`.
- Firebase Auth is live on JVM/Desktop via `FirebaseRestAuthRepository` in `jvmMain`.
- Firestore is live on Android via `FirestoreNotesListsRepository` and `FirestoreNotesRepository` in `androidMain`.
  - Firestore structure: `users/{userId}/noteslist` (lists), `users/{userId}/noteslist/{listId}/notes` (notes)
  - Lists use `contributors` array for future sharing; queried via `collectionGroup("noteslist").whereArrayContains("contributors", userId)`
  - New lists are created under `users/{currentUserId}/noteslist` with `contributors = [userId]`
  - Delete list uses `WriteBatch` to remove all notes + the list document
  - Repositories receive `AuthRepository` in constructor and read `currentUserId` lazily at each call
- Dependencies:
  - `firebase-auth` (version pinned directly, not via BOM — Kotlin 2.3 deprecated `platform()` in KMP source set deps)
  - `firebase-firestore` (version pinned directly, same reason)
  - `kotlinx-coroutines-play-services` (for `.await()` on Firebase Tasks)
  - `google-services` plugin applied in `androidApp/build.gradle.kts`
- `google-services.json` lives in `androidApp/` and is in `.gitignore`.
  - Contains clients for both `com.chemecador.secretaria` (release) and `com.chemecador.secretaria.debug` (debug).
- `androidApp` has `applicationIdSuffix = ".debug"` for debug builds — this is intentional so the KMP debug app coexists with the production Android app on the same device.
- Firebase auto-initializes via the `google-services.json` metadata in the app module.

### Firebase pitfalls

- Do not use `platform(libs.firebase.bom)` in KMP source set dependencies — `platform()` is deprecated in Kotlin 2.3. Pin Firebase library versions directly instead.
- `google-services.json` must include the debug package name (`com.chemecador.secretaria.debug`) as a registered client, otherwise Firebase will fail on debug builds.
- JVM/Desktop auth does not auto-configure Firebase. Set `SECRETARIA_FIREBASE_API_KEY` or `-Dsecretaria.firebaseApiKey=...` before running desktop auth flows.
- For local desktop development, `:composeApp:run` can also read `secretaria.firebaseApiKey` from the repo root `local.properties` and pass it to the JVM automatically.
- Firebase projects with email enumeration protection enabled may return `INVALID_LOGIN_CREDENTIALS` for invalid sign-in attempts. On JVM/Desktop this is mapped to `WRONG_PASSWORD` to keep the shared UI/API unchanged.
- The `collectionGroup("noteslist")` query with `whereArrayContains("contributors", userId)` requires a Firestore composite index. If it does not exist, the SDK logs the exact URL to create it in the Firebase console on first query failure.
- Firestore list deletion uses `WriteBatch` (notes + list doc). If the batch fails partway (e.g. network loss), orphaned notes may remain. This is acceptable for now.
- Currently all lists are assumed to be under `users/{currentUserId}/noteslist`. When sharing is implemented, the owner UID will need to be stored in `NotesListSummary` to construct the correct notes subcollection path.

## Good Next Steps

- Google Sign-In on Android (requires Credential Manager + Activity context abstraction)
- Auto-login / session persistence (check `FirebaseAuth.currentUser` on startup, skip login screen)
- Logout (add to `AuthRepository` interface when settings screen is built)
- iOS Firebase Auth (requires Firebase iOS SDK via CocoaPods/SPM)

## Known Remaining Warnings

- There are still AGP-related warnings coming from legacy/deprecated properties in `gradle.properties`.
- Those are separate from the KMP/module structure work already done.
- They should be cleaned incrementally, not mixed into feature work unless needed.

## Files Worth Reading First

- `composeApp/build.gradle.kts`
- `androidApp/build.gradle.kts`
- `settings.gradle.kts`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/login/`
- `composeApp/src/androidMain/kotlin/com/chemecador/secretaria/login/FirebaseAuthRepository.kt`
- `composeApp/src/androidMain/kotlin/com/chemecador/secretaria/noteslists/FirestoreNotesListsRepository.kt`
- `composeApp/src/androidMain/kotlin/com/chemecador/secretaria/notes/FirestoreNotesRepository.kt`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/noteslists/`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/notes/`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `androidApp/src/main/AndroidManifest.xml`
- `iosApp/iosApp/ContentView.swift`

## Session Update (2026-04-12)

- JVM/Desktop Firestore is now live for notes lists and notes.
- JVM `actual` repositories now use:
  - `composeApp/src/jvmMain/kotlin/com/chemecador/secretaria/noteslists/FirestoreRestNotesListsRepository.kt`
  - `composeApp/src/jvmMain/kotlin/com/chemecador/secretaria/notes/FirestoreRestNotesRepository.kt`
- Shared app wiring did not change in `commonMain`; only the JVM factories switched from fakes to Firestore REST.
- JVM/Desktop Firestore implementation details:
  - Uses Firestore REST API via `java.net.http.HttpClient`
  - Uses `kotlinx-serialization-json` in `jvmMain` to build/parse Firestore payloads
  - Reuses the Firebase Auth REST session `idToken` as `Authorization: Bearer ...`
  - `FirebaseRestAuthRepository` now keeps `idToken` + `refreshToken` in memory and refreshes expired tokens through the Secure Token REST endpoint
  - Resolves Firebase `projectId` from `-Dsecretaria.firebaseProjectId=...`, `SECRETARIA_FIREBASE_PROJECT_ID`, or local `androidApp/google-services.json`
  - For now, JVM/Desktop Firestore reads/writes direct user-scoped paths (`users/{currentUserId}/noteslist/...`) instead of collection-group queries; this is acceptable until sharing is migrated
  - JVM/Desktop Firestore currently sends client-clock timestamps in the REST payload instead of Firestore server timestamps
- New JVM-only support package:
  - `composeApp/src/jvmMain/kotlin/com/chemecador/secretaria/firestore/`
- New/updated JVM tests:
  - `composeApp/src/jvmTest/kotlin/com/chemecador/secretaria/login/FirebaseRestAuthRepositoryTest.kt`
  - `composeApp/src/jvmTest/kotlin/com/chemecador/secretaria/firestore/FirebaseFirestoreConfigTest.kt`
  - `composeApp/src/jvmTest/kotlin/com/chemecador/secretaria/noteslists/FirestoreRestNotesListsRepositoryTest.kt`
  - `composeApp/src/jvmTest/kotlin/com/chemecador/secretaria/notes/FirestoreRestNotesRepositoryTest.kt`
- Validation used for this slice:
  - `./gradlew :composeApp:compileKotlinJvm`
  - `./gradlew :composeApp:jvmTest`
- JVM/Desktop startup config follow-up:
  - `resolveFirebaseApiKey()` now also falls back to `secretaria.firebaseApiKey` in the nearest parent `local.properties`, so desktop launches from IDE/main class do not depend on Gradle injecting `-Dsecretaria.firebaseApiKey`
  - `resolveFirebaseProjectId()` now also accepts `secretaria.firebaseProjectId` in `local.properties` before falling back to `androidApp/google-services.json`
  - `readLocalProperty()` no longer relies only on the process working directory; it also searches ancestor directories of the JVM code source / classpath roots, which is important for Android Studio run configurations
  - The same nearby-file search is now used for `androidApp/google-services.json`, so JVM/Desktop runs from Android Studio can resolve the Firebase `projectId` even when the IDE uses a different working directory
- Web auth follow-up:
  - `AuthRepository` is now real on the JS browser target via `composeApp/src/jsMain/kotlin/com/chemecador/secretaria/login/FirebaseJsAuthRepository.kt`
  - JS web auth currently supports email/password login, email/password signup, and anonymous login via Firebase Auth REST API
  - Google Sign-In remains `NOT_SUPPORTED` on web for now
  - Web notes lists / notes repositories are still fake
  - Wasm auth still uses `FakeAuthRepository`; only the JS browser target is wired to real Firebase auth for this slice
  - The web API key is injected at build time into a generated `firebase-config.js` resource from `secretaria.firebaseApiKey` (Gradle property, env var, or `local.properties`)
  - Useful validation for this slice:
    - `./gradlew :composeApp:jsProcessResources :composeApp:wasmJsProcessResources :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs`

## Notes for Future Agents

- After completing any change or feature, update this `AGENTS.md` to reflect the new state (new files, conventions, migration progress, pitfalls discovered, etc.). This file is the primary context for future sessions.
- This repo is early-stage KMP, so avoid over-architecting.
- Favor correctness, portability, and small safe steps over completeness.
- If a build/runtime issue appears on Android and mentions missing Compose resources, check `androidResources { enable = true }` first.
- If changing Android host resources, remember that `androidApp` now owns the real launcher assets.
- If a change affects feature behavior, compare against the original Android project for business intent before introducing new abstractions.
