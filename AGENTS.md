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

## Git

- Always commit directly on `main`. Do not create feature branches for this project.
- Never add a `Co-Authored-By` trailer to commit messages.
- Commit subjects follow the existing style: `Feat: ...`, `Refactor: ...`, `Fix: ...`.

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
  - list groups as shareable containers; child lists keep optional `groupOwnerId` + `groupId`
  - accessible shared lists can be added to groups owned by the current user
  - group shares propagate inherited contributors while individual list shares stay direct
  - only owner can rename/delete; shared users can still open
- Notes support:
  - read, create, delete
  - ordered/unordered display
- Reminders support:
  - personal flat collection, not attached to any list, never shared
  - single list ordered manually by drag and drop; no automatic sections or sorting
  - optional floating due date with optional time; overdue items are highlighted but never reordered or archived
  - manual completion only; completed reminders move to a separate "Completados" screen reachable from the overflow menu
  - completed reminders are deleted 30 days after completion, client-side, in a single batch on screen load
  - no notifications yet (see "Pending: Reminder Notifications")
- Note detail supports editing title/content and delete confirmation.
- Logout has confirmation and clears auth session on all platforms.
- Settings screen shows account email, user code, version, author, contact, and project info.
- Still pending or partial:
  - Google Sign-In on Wasm
  - FCM
  - DataStore

## Navigation And Shared Architecture

- No navigation library yet. `composeApp/src/commonMain/kotlin/com/chemecador/secretaria/App.kt` uses a sealed `Screen`.
- Current screens: `Login`, `Lists`, `ListGroup`, `Notes`, `NoteDetail`, `Reminders`, `CompletedReminders`.
- Keep this simple approach until navigation complexity clearly grows.
- Package by feature, not by technical layer. Current feature packages: `login`, `noteslists`, `notes`, `friends`, `reminders`.
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
- Reminders live under `users/{uid}/reminders`. Confirm the Firestore security rules cover that subcollection; the rules are not in this repo.

## Reminders

- Firestore path is `users/{userId}/reminders/{reminderId}`. No `contributors`, no sharing, no `ownerId` in repository signatures.
- Document fields: `text`, `dueDate` (string `"yyyy-MM-dd"` or null), `dueTime` (string `"HH:mm"` or null), `completed`, `completedAt` (timestamp or null), `order` (int), `date` (createdAt).
- The due date is a FLOATING local date/time (`LocalDate` + optional `LocalTime`), not an `Instant`. Deliberate: it renders the same calendar day on every target regardless of device timezone, and manual ordering means the server never sorts by it. There is intentionally no `timeZoneId`. `createdAt` and `completedAt` are still `Instant`.
- `dueTime == null` means "all day": never render `00:00`, and it only counts as overdue once the whole day has passed.
- `completedAt` is written with the CLIENT clock on all five targets and is produced by `RemindersViewModel`, not by the repositories, so the optimistic UI value is identical to the persisted one and to what the 30-day purge compares against.
- The 30-day purge is client-side: `remindersToPurge()` (pure, `commonMain`) computes the ids and `RemindersViewModel.load()` calls `deleteReminders(ids)` once. `getReminders()` already returns pending + completed in one read, so the purge costs no extra request. Purge failures are swallowed and never surface as an error.
- The purge runs on `load()` only, never on `refresh()`.
- `RemindersScreen` and `CompletedRemindersScreen` share ONE `RemindersViewModel` hoisted in `App.kt`. `CompletedRemindersScreen` must not call `load()`.
- Marking complete / restoring is OPTIMISTIC with rollback, like `NotesViewModel.reorderNotes`. Create, update and delete stay fire-and-refetch, like the rest of the app.
- Restoring a reminder sends it to the end of the pending list (`maxPendingOrder + 1`) so it cannot collide with an existing `order`.
- `reorderReminders` only ever receives pending ids; completed reminders keep their stored `order` and are never reordered.
- `NotesReorderState` (package `notes`) is the SHARED manual-reorder controller; `noteslists` and `reminders` both reuse it despite the package name.
- Manual-ordering helpers are duplicated on purpose in `notes/NotesOrdering.kt`, `noteslists/NotesListsGrouping.kt` and `reminders/RemindersOrdering.kt`. Full unification is impossible because the lists case uses a composite key (`NotesListKey`) and a different field (`groupOrder`), so generalising would only merge two of three while touching working features. Revisit only if a fourth case appears.
- Reminders need no Firestore composite index and no Cloud Function.

## Pending: Reminder Notifications

- Phase 1 of reminders shipped deliberately WITHOUT notifications: no FCM, no local alarms, no scheduling, no permissions.
- When notifications are added:
  - Use LOCAL scheduling per platform, not server-side FCM fan-out. A personal reminder has exactly one recipient, so the existing Cloud Functions in `firebase/functions/src/index.ts` (shared lists, friend requests, notes in shared lists) are not a template here.
  - Android: `AlarmManager` (or WorkManager) + `POST_NOTIFICATIONS` runtime permission on API 33+ + reschedule on `BOOT_COMPLETED`.
  - iOS: `UNUserNotificationCenter` local notifications.
  - JVM/JS/Wasm: out of scope; use a no-op.
  - Resolve the floating `dueDate`/`dueTime` against the DEVICE timezone at scheduling time. That is the desired "remind me at 09:00 wherever I am" semantics and the reason no `timeZoneId` is stored.
  - Reminders with no `due` are never schedulable.
  - Reschedule triggers: create, edit due, complete, restore, delete, session restore on app start, timezone change, Android reboot.
  - Suggested shape: a `ReminderScheduler` interface in `commonMain` with a `NoopReminderScheduler`, wired per platform in `di/PlatformModule.*.kt` and called from `RemindersViewModel` after successful mutations. This mirrors `FcmTokenRegister` / `NoopFcmTokenRegister`.
  - If "notify at the timezone where I created it" is ever required, add a nullable `dueTimeZoneId` field; existing documents default to the device timezone. It is an additive change.

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
  - `composeApp/src/commonTest/kotlin/com/chemecador/secretaria/reminders/`
- iOS native repository tests:
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/login/`
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/noteslists/`
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/notes/`
  - `composeApp/src/iosSimulatorArm64Test/kotlin/com/chemecador/secretaria/reminders/`
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
- reminder notifications (see "Pending: Reminder Notifications")

## Notes For Future Agents

- Keep changes small, safe, and portable.
- Prefer durable context over session-by-session changelog detail.
- If behavior changes, compare against the original Android app to confirm domain intent.
- If Android runtime errors mention missing Compose resources, check `androidResources { enable = true }` first.
- If you touch Android host resources, remember `androidApp` owns them.
- After meaningful architectural or migration progress, update this file and keep it short.
