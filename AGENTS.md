# AGENTS.md

## Summary

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

## Working Rules

- Reuse as much portable code as possible in `commonMain`.
- Keep platform hosts thin.
- Avoid touching Swift unless there is a real need.
- Do not migrate Hilt, DataStore, FCM, or other Android-heavy infra too early.
- Prefer simple interfaces, `expect`/`actual`, and fakes when portability is unclear.
- Treat the original Android app as a business/domain reference, not an implementation template.

## Modules And Identity

- `composeApp`: shared UI, models, state, ViewModels, resources, and desktop/web/iOS entrypoints.
- `androidApp`: thin Android host; owns `AndroidManifest.xml`, `MainActivity`, launcher resources, `applicationId`, and versioning.
- `iosApp`: thin iOS host; embeds shared Compose from `composeApp/src/iosMain/kotlin/com/chemecador/secretaria/MainViewController.kt`; Swift entrypoint is `iosApp/iosApp/ContentView.swift`.
- Base package: `com.chemecador.secretaria`
- Android app id: `com.chemecador.secretaria`
- Shared Android namespace: `com.chemecador.secretaria.shared`
- Debug Android builds use `applicationIdSuffix = ".debug"` so they can coexist with production.

## Current Product State

- Shared flows implemented: login -> lists -> notes -> note detail, plus friends.
- Android push notifications already cover shared lists, incoming friend requests, and new notes in shared lists.
- Lists support:
  - read, create, delete
  - sort by name/date
  - tabs for owned vs shared lists
  - overflow menu with logout/about/amigos
  - shared-list visibility parity on Android/JVM/JS/iOS via `contributors`
  - owner can share a list with existing friends
  - only owner can rename/delete; shared users can still open
- Notes support:
  - read, create, delete
  - ordered/unordered display
- Note detail supports editing title/content and delete confirmation.
- Logout has confirmation and clears auth session on all platforms.
- Settings screen shows account email, user code, version, author, contact, and project info.
- Still pending or partial:
  - Google Sign-In on Wasm
  - FCM
  - DataStore

## Navigation And Shared Architecture

- No navigation library yet. `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt` uses a sealed `Screen`.
- Current screens: `Login`, `Lists`, `Notes`, `NoteDetail`.
- Keep this simple approach until navigation complexity clearly grows.
- Package by feature, not by technical layer. Current feature packages: `login`, `noteslists`, `notes`, `friends`.
- Typical feature shape: model, repository interface, fake repository, state, ViewModel, screen.
- Shared conventions:
  - immutable UI state with `data class`
  - repositories return stdlib `Result<T>`
  - shared logic lives in `androidx.lifecycle.ViewModel` subclasses in `commonMain`
  - expose state via `StateFlow`
  - `load()` / `refresh()` are non-suspend and launch on `viewModelScope`
  - screens call `viewModel.load()` from `LaunchedEffect(viewModel)`
  - shared Compose resolves ViewModels with `koinViewModel()`; use `key = ...` when parameters matter
- Use Koin in `composeApp` for wiring, not for hiding dependencies.
- Keep constructor injection in repositories and ViewModels.
- Keep composable-only helpers such as `remember...()` controllers outside DI until they need a stable abstraction.

## Shared Model / UI Conventions

- Do not copy Android or Firebase types into `commonMain`; shared models stay pure.
- Use `kotlin.time.Instant` for timestamps and `Long` ARGB for colors.
- Typed UI errors should live in state, usually alongside `Result<T>`.
- Reuse `noteslists/formatNotesListDate()` instead of adding new date formatters.
- Do not hardcode user-facing strings in shared UI; use `composeApp/src/commonMain/composeResources/values/strings.xml`.
- App language is Spanish.
- Use Material icons, not text-character substitutes.

## Platform Snapshot

- Real targets today: Android, JVM/Desktop, JS browser, iOS.
- Wasm still uses fake auth, fake notes lists, fake notes, and fake friends.
- Auth:
  - Android uses Firebase Auth SDK.
  - JVM/Desktop, JS, and iOS use Firebase Auth REST.
  - Real targets support email/password login, signup, and anonymous login.
  - Google Sign-In is implemented on Android, JVM/Desktop, JS browser, and iOS.
  - Non-Android real targets persist `idToken` + `refreshToken`, restore sessions on startup, and refresh expired tokens when needed.
- Notes / lists / friends:
  - Android uses Firebase SDKs.
  - JVM/Desktop, JS, and iOS use Firestore REST.
  - Firestore path is `users/{userId}/noteslist/{listId}/notes`.
  - Shared lists use the `contributors` array.
  - REST targets query shared lists with `runQuery` + `allDescendants` on `noteslist`, and note CRUD routes through the list `ownerId`.
  - Sharing a list appends the friend's uid to `contributors` on all real targets.
  - REST Firestore targets currently send client-clock timestamps, not server timestamps.
- Friends / requests:
  - live in root collection `friendships`
  - document shape mirrors the Android app reference
  - accepted friendship = non-null `acceptanceDate`
  - pending incoming/outgoing = null `acceptanceDate` filtered by `receiverId` / `senderId`
  - KMP ensures `users/{uid}.usercode` exists on first friends load
  - REST targets use the Android-style `dateKey + counter` format with Firestore preconditions because Firebase ID token REST auth does not provide read-write transactions

## Firebase / Platform Notes

- `google-services.json` lives in `androidApp/` and is gitignored.
- Register the debug app id in Firebase too: `com.chemecador.secretaria.debug`.
- Do not use `platform(libs.firebase.bom)` in KMP source set dependencies; pin Firebase versions directly.
- JVM/Desktop resolves Firebase config from system properties, env vars, and nearby `local.properties`; project id can fall back to `androidApp/google-services.json`.
- JVM/Desktop Google Sign-In may need `secretaria.googleDesktopClientId` / `SECRETARIA_GOOGLE_DESKTOP_CLIENT_ID` and sometimes `secretaria.googleDesktopClientSecret` / `SECRETARIA_GOOGLE_DESKTOP_CLIENT_SECRET`.
- JS gets Firebase API key, Firestore project id, and Google Web client id from generated `firebase-config.js`; Google client id can also come from env/property or the type `3` client in `androidApp/google-services.json`.
- iOS reads Firebase config from `iosApp/iosApp/GoogleService-Info.plist`; Google Sign-In also needs the reversed client id in `iosApp/iosApp/Info.plist` under `CFBundleURLTypes`.
- Browser Firebase config is intentionally public; security must come from Firebase rules.
- Firebase projects with email enumeration protection may return `INVALID_LOGIN_CREDENTIALS`; non-Android auth maps that to `WRONG_PASSWORD` to keep shared UI behavior stable.
- `collectionGroup("noteslist").whereArrayContains("contributors", userId)` needs a composite index. Indexes live in `firebase/firestore.indexes.json`.
- Deploy indexes with `cd firebase && firebase deploy --only firestore:indexes --project <projectId>`.
- List deletion uses a batch for notes + list document. Partial failure can leave orphaned notes; acceptable for now.

## Build And Test Pitfalls

- In `composeApp`, Android resources must keep `androidResources { enable = true }` or Compose resources can fail at runtime with missing `.cvr` files.
- `androidApp` owns the real Android launcher icon/resources.
- Shared Android validation uses tasks such as `:composeApp:compileAndroidMain`; do not assume old `testDebugUnitTest` naming.
- JS/Wasm browser tests may require Chrome; compile tasks are usually enough for routine validation.
- `viewModelScope` uses `Dispatchers.Main.immediate`, so shared ViewModel tests must install a test main dispatcher.
- For shared ViewModel tests, use `Dispatchers.setMain(StandardTestDispatcher())` and drive execution with `runCurrent()` / `advanceUntilIdle()`.

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

## Tests And Reading Order

- Shared tests:
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/login/`
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/noteslists/`
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/notes/`
- iOS native repository tests:
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/login/`
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/noteslists/`
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/notes/`
- Current test focus: ViewModel loading transitions, empty/content/error states, sorting logic, date formatting, and JVM/iOS Firestore request-response mapping.
- Read first when orienting:
  - `composeApp/build.gradle.kts`
  - `androidApp/build.gradle.kts`
  - `settings.gradle.kts`
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt`
  - `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/{login,noteslists,notes,friends}/`
  - `composeApp/src/commonMain/composeResources/values/strings.xml`
  - platform feature folders in `androidMain`, `jvmMain`, `jsMain`, and `iosMain`
  - `androidApp/src/main/AndroidManifest.xml`
  - `iosApp/iosApp/ContentView.swift`
  - `iosApp/iosApp/GoogleService-Info.plist`

## Near-Term Roadmap

- notifications expansion

## Notes For Future Agents

- Keep changes small, safe, and portable.
- Prefer durable context over session-by-session changelog detail.
- If behavior changes, compare against the original Android app to confirm domain intent.
- If Android runtime errors mention missing Compose resources, check `androidResources { enable = true }` first.
- If you touch Android host resources, remember `androidApp` owns them.
- After meaningful architectural or migration progress, update this file and keep it short.
