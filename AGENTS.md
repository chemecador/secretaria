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
- Android push notifications already cover shared lists, incoming friend requests, new notes in shared lists, shared reminders, and reminder due dates.
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
  - Android note detail can attach up to 3 photos; other targets hide the section
  - the notes list badges a note with its photo count, on the targets that support photos
  - the full-size viewer can save the photo to the device; Android only, no permission asked
  - search by title or content, and sort by name/date on unordered lists
  - Android Photo Picker reencodes to JPEG <=1600 px, targets 600 KiB and hard-caps at 1 MiB
- Reminders support:
  - flat collection, not attached to any list; shareable with friends one by one
  - a shared reminder is a single document: completion, edits and manual order are shared
  - single list ordered manually by drag and drop; no automatic sections or sorting
  - optional floating due date with optional time; overdue items are highlighted but never reordered or archived
  - manual completion only; completed reminders move to a separate "Completados" screen reachable from the overflow menu
  - long press opens an options dialog: owner gets Compartir/Eliminar, invited users get "Dejar de compartir conmigo"
  - completed reminders are deleted 30 days after completion, client-side, in a single batch on screen load
  - sharing pushes a notification, and so does the due date (see "Reminder Due Notifications")
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
- The lists and notes screens share one header: chips (lists only) and a sort icon on the top row,
  with an always-visible search field underneath. Both controls live in
  `noteslists/ListHeaderControls.kt` next to `SortOption`, the same way `reminders` reuses
  `noteslists.ListCollaborator`. The search field is deliberately persistent and never grabs focus:
  hiding it behind a magnifier made it easy to miss, and auto-focus would pop the keyboard on every
  screen open.
- Anything manually ordered hides the sort control, because `order` is the only criterion there:
  ordered note lists and ordered groups. Filtering an ordered list also disables dragging, since a
  drag over a subset would send `reorderNotes` a partial id list, which `applyNoteOrder` rejects.
- Note search and sort are pure helpers in `notes/NotesSearch.kt`; `NotesState.notes` always holds
  the full list, because reordering and the photo-count sync both address notes by id.
- Do not hardcode user-facing strings in shared UI; use `composeApp/src/commonMain/composeResources/values/strings.xml`.
- Never hardcode a user-facing message in a ViewModel either. Typed errors resolved in the screen
  are the pattern: `login.AuthError` and `noteslists.NotesListsError`, both mapped by a private
  `toStringRes()` in their screen. Raw `throwable.message` is only for repository failures.
- Use Material icons, not text-character substitutes.

## Localization

- English is the DEFAULT locale: `composeResources/values/strings.xml` is English and
  `values-es/strings.xml` is Spanish. Any device whose language is not Spanish gets English.
  Adding a language means adding `values-<lang>/`, never editing the default into another language.
- Both files must keep the same keys, the same order, and the same `%1$s` placeholders.
- Compose resources only unescape `\n`, `\t`, `\uXXXX` and `\` (see `handleSpecialCharacters` in
  the Compose Gradle plugin). `\'` is NOT unescaped, so apostrophes go in raw: write `don't`, not
  the escaped form, or the backslash shows up on screen. Real Android resources under `res/values*`
  are the opposite and still need the escape.
- Android host resources are separate and also split by locale: `androidApp/src/main/res/values`
  (English) and `values-es` hold the four notification channel names and descriptions.
- Dates and clock times are not fixed: `format/DateTimeFormat.kt` resolves them from two tokens in
  strings.xml, `format_date_order` (`month_first` / anything else) and `format_clock` (`12h` /
  anything else), and travels via `LocalDateTimeFormat` provided once in `App.kt`. The resolution
  is resource-driven because kotlinx-datetime has no locale support and Compose resources expose
  no locale API, so the app language is the only signal available. Unknown token values fall back
  to the Spanish original, `dd/MM/yyyy` and 24h.
- `formatNotesListDate` and `formatReminderTime` are `@Composable` now; the pure logic they wrap is
  `DateTimeFormat.formatDate` / `formatTime`, which is what the tests exercise.
- Push notification texts live in `NOTIFICATION_TEXTS` in `firebase/functions/src/index.ts`, not at
  the call sites: `sendPushToUser` / `sendPushToTokens` take a builder that receives the recipient's
  texts. The language comes from `users/{uid}/fcm_tokens/{token}.language`, written next to
  `timeZoneId`. A token without that field falls back to Spanish (`LEGACY_LANGUAGE`), because those
  are pre-existing devices of the Spanish user base; an unknown language falls back to English.

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
- `whereArrayContains` over a collection group needs a single-field `fieldOverrides` entry with `queryScope: COLLECTION_GROUP`, NOT an `indexes` entry. A composite index with one `arrayConfig` field is rejected by the API with "this index is not necessary, configure using single field index controls", and that error aborts the whole index deploy. `noteslist.contributors`, `noteslist.contributorsIds` and `reminders.contributors` are declared that way in `firebase/firestore.indexes.json`.
- Deploy indexes with `cd firebase && firebase deploy --only firestore:indexes --project <projectId>`. Never pass `--force` without checking the diff: it deletes every index in the project that is missing from the file.
- List deletion uses a batch for notes + list document. Partial failure can leave orphaned notes; acceptable for now.
- Security rules live in `firebase/firestore.rules` and are wired in `firebase.json`. That file is the source of truth: a deploy overwrites whatever the console has.
- Note photo metadata lives in `users/{ownerId}/noteslist/{listId}/notes/{noteId}/photos`; binaries live under the matching `note-images/...` Storage prefix. Clients can only read authorised objects and never write metadata or Storage directly.
- The note document carries a server-maintained `photoCount`, written inside the same transactions
  that create or delete the photo metadata, so the notes list can badge photos without one read per
  note. Rules reject any client write that adds or changes it, and `removePhotoRecord` skips that
  write when the note no longer exists, because the note-deletion trigger would otherwise resurrect
  the document. Notes written before the field existed read as 0. `NotePhotosState.hasLoaded` is
  what lets `App.kt` push a fresh count into `NotesViewModel.syncPhotoCount` without a refetch, and
  keeps a failed photo read from clearing a badge that is actually correct.
- Note photo writes use `uploadNotePhoto` / `deleteNotePhoto` callables in `europe-west1`. The server reencodes with Sharp, uses payload-bound idempotent reservations with at most 3 processing attempts, cleans stale uploads every 30 minutes and enforces: 3/note, 50 photos + 50 MiB/account, 10 uploads/day, 50 uploads/month, 100 photo calls/account/day, 250 uploads/day global, 1,000 photo calls/day global and 2 GiB global. Set `notePhotoSystem/global.uploadsEnabled = false` for an emergency stop.
- Saving a photo to the device goes through `rememberNotePhotoDownloadController`, the same
  `@Composable expect` shape as the picker, and is null everywhere except Android. It reuses the
  bytes the viewer already holds, so a save never re-downloads. Android takes one of two
  permission-free paths by API level: from 29 it inserts into `Pictures/Secretaria` via MediaStore
  with `IS_PENDING` so a half-written file is never scanned, and below 29 it falls back to the
  system `CreateDocument` picker, because MediaStore there would require `WRITE_EXTERNAL_STORAGE`.
  Never add that permission to make the older path look like the newer one.
- `NotePhotoDownloadResult.Saved` carries an opaque platform `location` (an Android content Uri as
  a String) that the same controller feeds back to `open()`. The viewer reports the outcome with a
  real snackbar hosted inside its own `Dialog` — a Scaffold-level host would render behind it — and
  its "Open" action fires `ACTION_VIEW`. `SnackbarHostState.showSnackbar` owns the timing, so there
  is no hand-rolled delay; `dismissDownloadFeedback()` must stay the last statement in that
  `LaunchedEffect`, since flipping the state cancels the coroutine running it.
- Photo READS have no quota and never will have one cheaply: they go straight to Storage from the
  SDK, so no callable counts them. `consumeCallBudget` and every limit next to it only guard
  `uploadNotePhoto` / `deleteNotePhoto`. Each read also costs two `firestore.get()` calls made by
  the Storage rules to authorise it. `CachingNotePhotosRepository` (commonMain, wraps the Android
  repository in `platformModule`) is the defence that matters: bytes are immutable once uploaded,
  so an in-memory LRU is always valid and only a delete evicts. The Storage SDK has no disk cache
  of its own — verified by killing the network after a load. App Check enforcement on Cloud Storage
  is the other half; it is a console setting, not code, and it is now on.
- Note photos require a non-anonymous account. Android installs App Check Debug in debug and Play Integrity in release. App Check is now ENFORCED on Cloud Storage (console, APIs tab) and on the photo callables (`enforceAppCheck` in `CALLABLE_OPTIONS`), on top of auth, the transactional quotas and `concurrency: 1`, which all remain mandatory.
- The Functions emulator honours `enforceAppCheck` and cannot mint a valid token, so
  `CALLABLE_OPTIONS` turns enforcement off when `FUNCTIONS_EMULATOR` is set; that variable is never
  set during a deploy. Without the carve-out every call in the note-photos integration test fails
  with `UNAUTHENTICATED`.
- A debug build needs its App Check debug token registered in the console or photos break on that
  device only; release builds attest through Play Integrity. The token is printed to logcat by
  `DebugAppCheckProvider` on every start.
- Security-rule regression tests are `npm --prefix firebase/functions run test:rules` under Firestore + Storage emulators. Deploy photos with the five named functions plus `firestore:rules,storage`; never grant client writes as a shortcut.
- The end-to-end callable test is `npm --prefix firebase/functions run test:integration:note-photos`; it uses isolated emulator ports and covers upload, idempotency, quotas, anonymous rejection, Storage objects and uploader cleanup after removal.
- Reminders live under `users/{uid}/reminders`. The owner rule must NOT depend on `resource`: a list query over the whole collection is evaluated document by document, so a single pre-sharing reminder without `contributors` would deny the entire read, and `create` has a null `resource`.
- A recursive wildcard nested inside `/users/{userId}` does not apply to collection group queries. `collectionGroup("reminders")` needs `match /{path=**}/reminders/{reminderId}` at the root of the rules file. Same for `noteslist`.
- `collectionGroup("reminders").whereArrayContains("contributors", userId)` needs its own `fieldOverrides` entry, already declared in `firebase/firestore.indexes.json` and deployed.

## Reminders

- Firestore path is `users/{userId}/reminders/{reminderId}`. `ownerId` is derived from the path, never stored.
- Document fields: `text`, `dueDate` (string `"yyyy-MM-dd"` or null), `dueTime` (string `"HH:mm"` or null), `completed`, `completedAt` (timestamp or null), `order` (int), `date` (createdAt), `contributors` (array of uids including the owner).
- The due date is a FLOATING local date/time (`LocalDate` + optional `LocalTime`), not an `Instant`. Deliberate: it renders the same calendar day on every target regardless of device timezone, and manual ordering means the server never sorts by it. There is intentionally no `timeZoneId`. `createdAt` and `completedAt` are still `Instant`.
- `dueTime == null` means "all day": never render `00:00`, and it only counts as overdue once the whole day has passed.
- `completedAt` is written with the CLIENT clock on all five targets and is produced by `RemindersViewModel`, not by the repositories, so the optimistic UI value is identical to the persisted one and to what the 30-day purge compares against.
- The 30-day purge is client-side: `remindersToPurge()` (pure, `commonMain`) computes the ids and `RemindersViewModel.load()` calls `deleteReminders(ids)` once. `getReminders()` already returns pending + completed in one read, so the purge costs no extra request. Purge failures are swallowed and never surface as an error.
- The purge runs on `load()` only, never on `refresh()`.
- `RemindersScreen` and `CompletedRemindersScreen` share ONE `RemindersViewModel` hoisted in `App.kt`. `CompletedRemindersScreen` must not call `load()`.
- Marking complete / restoring is OPTIMISTIC with rollback, like `NotesViewModel.reorderNotes`. Create, update and delete stay fire-and-refetch, like the rest of the app.
- Restoring a reminder sends it to the end of the pending list (`maxPendingOrder + 1`) so it cannot collide with an existing `order`.
- `reorderReminders` only ever receives pending keys; completed reminders keep their stored `order` and are never reordered.
- `NotesReorderState` (package `notes`) is the SHARED manual-reorder controller; `noteslists` and `reminders` both reuse it despite the package name.
- Manual-ordering helpers are duplicated on purpose in `notes/NotesOrdering.kt`, `noteslists/NotesListsGrouping.kt` and `reminders/RemindersOrdering.kt`. Full unification is impossible because the lists case uses a composite key (`NotesListKey`) and a different field (`groupOrder`), so generalising would only merge two of three while touching working features. Revisit only if a fourth case appears.

## Reminder Sharing

- Mirrors list sharing, minus groups: one `contributors` array, no `directContributors` / `inheritedGroupContributors`.
- `getReminders()` does TWO reads: own reminders by path plus `contributors`-array-contains over the `reminders` collection group, merged with `distinctBy(Reminder::key)`. Reading own reminders by path is deliberate: documents created before sharing existed have no `contributors` field, so a collection-group-only query would make them vanish. No migration is needed because of this.
- `ReminderKey(ownerId, reminderId)` is the identity everywhere a write can land on someone else's document: update, complete, reorder, delete and leave all take keys. Only `shareReminder` / `unshareReminder` take a bare id, because only the owner can call them.
- A shared reminder is ONE document, so `completed`, `order` and the text are shared state, exactly like notes inside a shared list. Deliberate: two people sharing "comprar pan" want to see it disappear when either buys it.
- `order` can collide between owners (each user's counter starts at 0), so `pendingReminders` sorts by `order`, then `createdAt`, then `id`. The first manual drag renumbers everything and removes the collision.
- The 30-day purge only deletes the CURRENT user's own completed reminders: an invited user has no permission to delete, and a batch that mixes owners would fail as a whole.
- Only the owner can delete a shared reminder; an invited user gets "Dejar de compartir conmigo", which removes their uid from `contributors`.
- `RemindersViewModel` needs `AuthRepository` (ownership checks, purge scope) and `FriendsRepository` (resolving collaborator names), like `NotesListsViewModel`.
- The reminders feature reuses `noteslists.ListCollaborator` instead of duplicating it; the sharing dialog is a copy of `ShareListDialog` because the Compose bodies are private to their screens.
- `onReminderShared` in `firebase/functions/src/index.ts` pushes to newly added contributors on the `reminder_shared` Android channel. Reminders have no `creator` field, so the notification shows the reminder text rather than the sharer's name; add a `creator` field first if that ever matters.
- Deploy after touching this: `firebase deploy --only firestore:indexes,functions --project <projectId>`.
- `sendPushToUser` is now `loadUserTokens` + `sendPushToTokens`. The split exists so the due-date sweep can read a user's tokens once per pass (it needs the timezone before deciding whether to send) and reuse them; the event-driven functions are unaffected.

## Reminder Due Notifications

- Implemented as PUSH, not as local alarms. An earlier draft of this file planned `AlarmManager` / `UNUserNotificationCenter`; that was dropped on purpose. Push works with the app closed, needs no `BOOT_COMPLETED` receiver and no per-platform scheduler, and delivers to every contributor of a shared reminder. The cost is that it only reaches targets that register an FCM token: today that is Android only, because JVM/JS/Wasm/iOS still use `NoopFcmTokenRegister`. Wiring a token register on iOS is what would extend this to iOS; nothing else has to change.
- `onReminderDue` in `firebase/functions/src/index.ts` is a `onSchedule("every 5 minutes")` sweep, not a Firestore trigger. It has to be a sweep: `dueDate`/`dueTime` are FLOATING, so the document never says at which absolute instant it is due and there is nothing to schedule ahead of time.
- Each pass queries the `reminders` collection group with `completed == false` and `dueDate` inside `[utcToday - 1d, utcToday + 1d]`. Real UTC offsets run from -12 to +14, so a local calendar day is never more than one day away from the UTC one and that window provably covers every timezone. This is what keeps the sweep from reading the whole collection.
- The floating due is resolved against the timezone of EACH recipient, which preserves the "avisame a las 09:00 este donde este" semantics and is why no `timeZoneId` is stored on the reminder.
- The device timezone lives on the FCM token document (`users/{uid}/fcm_tokens/{token}.timeZoneId`), not on the user or the reminder: it describes a device, and a push is delivered to a device. Written by `FirestoreFcmTokenRegister` (login / session restore) and by `SecretariaMessagingService.onNewToken`. If a user has several devices, the most recently updated token wins. Missing or unknown zone falls back to `Europe/Madrid`.
- A reminder with `dueTime == null` ("todo el dia") notifies at `ALL_DAY_DUE_TIME` (09:00 local). That constant is the single point of change if it ever becomes a user setting.
- Delivery bookkeeping is a map on the reminder document: `dueNotified: { uid: signature }`, where `signature` is `"yyyy-MM-ddTHH:mm"` or `"yyyy-MM-ddTall-day"`. Editing the due date changes the signature, so the reminder notifies again; completing it drops out of the query; restoring one whose due already passed does NOT re-notify, because the signature is already recorded.
- Sending happens BEFORE marking. If the mark write fails the next pass resends, and Android collapses the repeat because both notifications carry the same `tag`. Losing a reminder is worse than a duplicate that cannot actually duplicate on screen.
- `MAX_LATE_MS` (1 hour) drops avisos that are already stale. It also caps the burst on first deploy, when the window is full of reminders that were due before the feature existed.
- The `dueNotified` write re-triggers `onReminderShared` on the same path. That is harmless: contributors are unchanged, so it returns immediately.
- Needs a composite index (`completed` ASC + `dueDate` ASC, `COLLECTION_GROUP` scope), already declared in `firebase/firestore.indexes.json`. Scheduled functions also need Cloud Scheduler enabled on the project.
- Android side: own channel `reminder_due` with `IMPORTANCE_HIGH` so due avisos can be silenced without losing the sharing ones. Tapping opens the Reminders screen through `com.chemecador.secretaria.OPEN_REMINDERS` -> `NotificationOpenRemindersIntent` -> `App(openRemindersRequest = ...)`, mirroring the `OPEN_LIST` path.
- A notification intent extra arrives with TWO different types depending on who painted the notification: a real `Boolean` when `SecretariaMessagingService` builds it in the foreground, and a `String` when the system paints it in the background, because there the extras come straight from the FCM `data` payload. `getBooleanExtra` silently returns the default in the background case, so any boolean extra read from a notification intent must accept both.
- The timezone helpers in `index.ts` (`floatingDueToEpochMs` / `timeZoneOffsetMs`) use `Intl` with a two-pass offset correction instead of a date library, so `functions/package.json` keeps zero extra dependencies. Verified against DST transitions, sub-hour offsets (+05:30, +05:45, +12:45) and the extremes (+14, -11).
- If "notify at the timezone where I created it" is ever required, add a nullable `dueTimeZoneId` field on the reminder and prefer it over the device zone; existing documents keep the current behavior. It is an additive change.
- Turning on the "Recordar en…" switch checks the notification permission through `rememberNotificationPermissionController()` (`commonMain/NotificationPermission.kt`, root package like `PlatformBackHandler`). The actual is real only on Android; everywhere else it returns null, which disables the check, because those targets use `NoopFcmTokenRegister` and the aviso would never arrive. The state is queried at that instant, never cached: the user can turn notifications off from system settings without reopening the app.
- The dialog only informs; the due date is saved either way, since what is lost without permission is the push, not the date. `requestNotifications()` re-asks the system while `shouldShowRequestPermissionRationale` allows it and falls back to the app notification settings screen (and then to the app details screen, which some manufacturers are the only ones to resolve).

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
- FCM token registration on iOS, which is all that reminder due notifications need to reach iOS

## Notes For Future Agents

- Keep changes small, safe, and portable.
- Prefer durable context over session-by-session changelog detail.
- If behavior changes, compare against the original Android app to confirm domain intent.
- If Android runtime errors mention missing Compose resources, check `androidResources { enable = true }` first.
- If you touch Android host resources, remember `androidApp` owns them.
- After meaningful architectural or migration progress, update this file and keep it short.
