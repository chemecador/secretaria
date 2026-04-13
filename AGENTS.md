# AGENTS.md

## Project Summary

- Project: `secretaria`
- Stack: Kotlin Multiplatform + Compose Multiplatform
- Goal: migrate the existing Android app to KMP in small, low-risk vertical slices
- Android reference app:
  - macOS: `/Users/alex/Proyectos/Android/Kotlin/Secretaria`
  - Windows: `C:\Proyectos\Android\Kotlin\secretaria-android`
- Repo owner profile:
  - experienced Kotlin/Android developer
  - comfortable with Compose and coroutines
  - first serious KMP migration on this product
  - prefers safe, incremental steps over large rewrites

## Core Rules

- Reuse as much portable code as possible in `commonMain`.
- Keep platform hosts thin.
- Avoid touching Swift unless there is a real need.
- Do not migrate Hilt, DataStore, FCM, or other Android-heavy infra too early.
- Prefer simple interfaces, `expect`/`actual`, and fakes when portability is unclear.
- Treat the original Android app as a business/domain reference, not an implementation template.

## Architecture

- `composeApp`
  - shared KMP module
  - owns shared UI, models, state, viewmodels, resources, and desktop/web/iOS entrypoints
  - plugin direction: `org.jetbrains.kotlin.multiplatform`, `org.jetbrains.compose`, `com.android.kotlin.multiplatform.library`
- `androidApp`
  - thin Android host
  - owns `AndroidManifest.xml`, `MainActivity`, launcher resources, `applicationId`, versioning
- `iosApp`
  - thin iOS host in `iosApp/`
  - embeds shared Compose from `composeApp/src/iosMain/kotlin/com/chemecador/secretaria/MainViewController.kt`
  - Swift entrypoint: `iosApp/iosApp/ContentView.swift`

## Package / Identity

- Base package: `com.chemecador.secretaria`
- Android app id: `com.chemecador.secretaria`
- Shared Android namespace: `com.chemecador.secretaria.shared`
- Debug Android app uses `applicationIdSuffix = ".debug"` so it can coexist with the production app

## Product State

- Shared flow implemented: login -> lists -> notes -> note detail
- Notes lists:
  - read, create, delete
  - sort by name/date
  - overflow menu with logout/about
- Notes:
  - read, create, delete
  - ordered/unordered display
- Note detail:
  - edit title/content
  - delete with confirmation
- Logout:
  - confirmation dialog
  - clears auth session on all platforms
- About dialog:
  - app name, version, author
- Pending or partial areas:
  - Google Sign-In en iOS/Wasm
  - session persistence on non-Android targets
  - sharing/friends
  - FCM
  - DataStore
  - richer settings/account screens

## Navigation

- No navigation library yet.
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt` uses a sealed `Screen`.
- Current screens:
  - `Screen.Login`
  - `Screen.Lists`
  - `Screen.Notes(listId, listName, isOrdered)`
  - `Screen.NoteDetail(listId, listName, isOrdered, note)`
- This is acceptable for the current app size. Revisit only if navigation complexity grows.

## Shared Architecture Conventions

- Package by feature, not by technical layer.
- Current feature packages: `login`, `noteslists`, `notes`.
- Typical feature shape:
  - model
  - repository interface
  - fake repository
  - state
  - viewmodel
  - screen
- Shared code conventions:
  - immutable UI state with `data class`
  - repositories return stdlib `Result<T>`
  - shared logic lives in `androidx.lifecycle.ViewModel` subclasses in `commonMain`
  - expose state via `StateFlow`
  - `load()` / `refresh()` are non-suspend and launch on `viewModelScope`
  - screens call `viewModel.load()` from `LaunchedEffect(viewModel)`
  - instantiate VMs with `viewModel { ... }`; use `key = ...` when parameters matter
- Do not introduce Hilt/Koin yet. Inject dependencies directly through the `viewModel { }` factory.

## Model / UI Conventions

- Do not copy Android or Firebase types into `commonMain`.
- Shared models stay pure.
- Conventions:
  - timestamps use `kotlin.time.Instant`
  - colors use `Long` ARGB values
  - typed UI errors live in state, usually alongside `Result<T>`
- Reuse `noteslists/formatNotesListDate()` instead of adding extra date formatters.
- Do not hardcode user-facing strings in shared UI.
- Shared strings belong in `composeApp/src/commonMain/composeResources/values/strings.xml`.
- App language is currently Spanish.
- Use Material icons, not text-character substitutes.

## Feature Status by Platform

| Feature     | Common                                        | Android                         | JVM/Desktop                         | JS browser                        | iOS                                | Wasm                       |
| ----------- | --------------------------------------------- | ------------------------------- | ----------------------------------- | --------------------------------- | ---------------------------------- | -------------------------- |
| Auth        | `AuthRepository`, `LoginViewModel`            | `FirebaseAuthRepository`        | `FirebaseRestAuthRepository`        | `FirebaseJsAuthRepository`        | `FirebaseIosAuthRepository`        | `FakeAuthRepository`       |
| Notes lists | `NotesListsRepository`, `NotesListsViewModel` | `FirestoreNotesListsRepository` | `FirestoreRestNotesListsRepository` | `FirestoreJsNotesListsRepository` | `FirestoreIosNotesListsRepository` | `FakeNotesListsRepository` |
| Notes       | `NotesRepository`, `NotesViewModel`           | `FirestoreNotesRepository`      | `FirestoreRestNotesRepository`      | `FirestoreJsNotesRepository`      | `FirestoreIosNotesRepository`      | `FakeNotesRepository`      |

### Auth Notes

- Android uses Firebase Auth SDK.
- JVM/Desktop, JS, and iOS use Firebase Auth REST.
- Supported today on real targets: email/password login, signup, anonymous login.
- Android also supports Google Sign-In via Credential Manager + Firebase Auth.
- JVM/Desktop also supports Google Sign-In via browser OAuth loopback + Firebase Auth REST.
- JS browser also supports Google Sign-In via Google Identity Services token popup + Firebase Auth REST.
- iOS still returns `NOT_SUPPORTED` for Google Sign-In.
- Non-Android real targets keep `idToken` + `refreshToken` in memory and refresh expired tokens.
- Wasm still uses fake auth.

### Firestore Notes

- Android uses Firebase Firestore SDK.
- JVM/Desktop, JS, and iOS use Firestore REST.
- Wasm still uses fake notes lists + fake notes.
- Current structure: `users/{userId}/noteslist/{listId}/notes`
- Android lists already use `contributors` for future sharing and query shared lists with `collectionGroup(...).whereArrayContains("contributors", userId)`.
- JVM/Desktop, JS, and iOS still use direct user-scoped paths, so sharing parity is pending.
- REST Firestore targets currently send client-clock timestamps, not server timestamps.

## Firebase / Platform Notes

- Firebase Auth and Firestore are live on Android, JVM/Desktop, JS browser, and iOS.
- `google-services.json` lives in `androidApp/` and is gitignored.
- The debug package id must also be registered in Firebase: `com.chemecador.secretaria.debug`.
- Do not use `platform(libs.firebase.bom)` in KMP source set dependencies. Pin Firebase versions directly.
- JVM/Desktop resolves Firebase config from system properties, env vars, and nearby `local.properties`; project id can also fall back to `androidApp/google-services.json`.
- JVM/Desktop Google Sign-In needs a Desktop OAuth client id exposed as `secretaria.googleDesktopClientId` or `SECRETARIA_GOOGLE_DESKTOP_CLIENT_ID`; packaged desktop builds can embed it through generated `DesktopBuildConfig`.
- Some JVM/Desktop Google OAuth clients also require `secretaria.googleDesktopClientSecret` or `SECRETARIA_GOOGLE_DESKTOP_CLIENT_SECRET` during the code exchange; packaged desktop builds can embed it through generated `DesktopBuildConfig`.
- JS receives Firebase API key, Firestore project id, and Google Web client id through generated `firebase-config.js`.
- JS Google Sign-In can resolve the Web OAuth client id from `secretaria.googleWebClientId`, `SECRETARIA_GOOGLE_WEB_CLIENT_ID`, or the type `3` client in `androidApp/google-services.json`.
- JS Google Sign-In uses the GIS popup token flow; register the web origin as an authorized JavaScript origin for the Google OAuth client.
- iOS reads Firebase config from bundled `iosApp/iosApp/GoogleService-Info.plist`.
- Web clients intentionally ship Firebase API key/project id in browser resources; security must come from Firebase rules, not secrecy.
- Firebase projects with email enumeration protection may return `INVALID_LOGIN_CREDENTIALS`; non-Android auth maps that to `WRONG_PASSWORD` to keep shared UI behavior stable.
- `collectionGroup("noteslist").whereArrayContains("contributors", userId)` requires a Firestore composite index.
- List deletion uses a batch for notes + list document. Partial failure can leave orphaned notes; acceptable for now.

## Build / Runtime Pitfalls

- In `composeApp`, Android resources must enable `androidResources { enable = true }` or Compose resources can crash at runtime with missing `.cvr` files.
- `androidApp` owns the real Android launcher icon/resources.
- Shared Android validation uses tasks such as `:composeApp:compileAndroidMain`; do not assume old `testDebugUnitTest` naming.
- JS/Wasm browser tests may require Chrome; compile tasks are often enough for routine validation.
- `viewModelScope` uses `Dispatchers.Main.immediate`, so ViewModel tests must install a test main dispatcher.
- For tests using shared ViewModels, use `Dispatchers.setMain(StandardTestDispatcher())`, then drive execution with `runCurrent()` / `advanceUntilIdle()`.

## Validation Commands

- Android host: `./gradlew :androidApp:assembleDebug`
- Shared Android target: `./gradlew :composeApp:compileAndroidMain`
- JVM/Desktop: `./gradlew :composeApp:compileKotlinJvm :composeApp:jvmTest`
- iOS simulator: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:iosSimulatorArm64Test`
- JS/Wasm: `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs`
- Broad validation:
  - `./gradlew :composeApp:compileAndroidMain :composeApp:jvmTest :composeApp:iosSimulatorArm64Test :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinJvm :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs :androidApp:assembleDebug`
- Windows desktop distributable:
  - `./gradlew :composeApp:createDistributable`
  - output: `composeApp/build/compose/binaries/main/app/com.chemecador.secretaria/`

## Test Locations

- Shared tests:
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/login/`
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/noteslists/`
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/notes/`
- iOS native repository tests:
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/noteslists/`
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/notes/`
- Current test focus:
  - ViewModel loading transitions
  - empty/content/error states
  - sorting logic
  - date formatting
  - JVM/iOS Firestore request-response mapping

## Files Worth Reading First

- `composeApp/build.gradle.kts`
- `androidApp/build.gradle.kts`
- `settings.gradle.kts`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/login/`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/noteslists/`
- `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/notes/`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/androidMain/kotlin/com/chemecador/secretaria/login/`
- `composeApp/src/androidMain/kotlin/com/chemecador/secretaria/noteslists/`
- `composeApp/src/androidMain/kotlin/com/chemecador/secretaria/notes/`
- `composeApp/src/jvmMain/kotlin/com/chemecador/secretaria/login/`
- `composeApp/src/jvmMain/kotlin/com/chemecador/secretaria/noteslists/`
- `composeApp/src/jvmMain/kotlin/com/chemecador/secretaria/notes/`
- `composeApp/src/jsMain/kotlin/com/chemecador/secretaria/login/`
- `composeApp/src/jsMain/kotlin/com/chemecador/secretaria/noteslists/`
- `composeApp/src/jsMain/kotlin/com/chemecador/secretaria/notes/`
- `composeApp/src/iosMain/kotlin/com/chemecador/secretaria/login/`
- `composeApp/src/iosMain/kotlin/com/chemecador/secretaria/firestore/`
- `composeApp/src/iosMain/kotlin/com/chemecador/secretaria/noteslists/`
- `composeApp/src/iosMain/kotlin/com/chemecador/secretaria/notes/`
- `androidApp/src/main/AndroidManifest.xml`
- `iosApp/iosApp/ContentView.swift`
- `iosApp/iosApp/GoogleService-Info.plist`

## Likely Next Steps

- session persistence / auto-login on non-Android targets
- sharing parity for JVM/JS/iOS Firestore by storing owner UID and removing direct user-scoped assumptions
- Google Sign-In parity for iOS if it becomes a product priority
- settings/account/notifications expansion

## Desktop Distributable (Windows .exe)

To generate a standalone `.exe` with embedded JRE (no Java needed on the target machine):

```bash
./gradlew :composeApp:createDistributable
```

Output: `composeApp/build/compose/binaries/main/app/com.chemecador.secretaria/`

## Notes for Future Agents

- Keep changes small, safe, and portable.
- Prefer context that remains true over session-by-session changelog detail.
- If behavior changes, compare against the original Android app to confirm domain intent.
- If a build/runtime issue mentions missing Compose resources on Android, check `androidResources { enable = true }` first.
- If you touch Android host resources, remember `androidApp` owns them.
- After meaningful architectural or migration progress, update this file so it stays short but current.
