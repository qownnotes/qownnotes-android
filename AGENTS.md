# QOwnNotes Mobile Agent Guide

## Project Summary

QOwnNotes Mobile is an Android-first Kotlin application for reading, creating, editing, and
synchronizing Markdown notes. It currently uses the Nextcloud Notes REST API and Nextcloud Android
Single Sign-On. Room is the offline source of truth, and the architecture is intended to support a
local-folder backend and a portable shared core later.

Read these documents before making architectural or product decisions:

- [`mobile-app-plan.md`](mobile-app-plan.md): product scope, compatibility rules, implementation
  history, roadmap, testing strategy, and upstream source references.
- [`docs/architecture/`](docs/architecture/): accepted architecture decisions. These are more
  focused than the plan and should not be bypassed casually.
- [`README.md`](README.md): verified development, emulator, device, and release commands.
- [`docs/testing/phase-2-nextcloud-compatibility.md`](docs/testing/phase-2-nextcloud-compatibility.md):
  real-server and protocol compatibility notes.

The plan is a planning baseline, not a substitute for inspecting the current code. Some features
requested after a phase was written may already be implemented. When an implementation decision
materially changes the plan, update the plan in the same change.

## Repository Map

The Gradle modules are declared in [`settings.gradle.kts`](settings.gradle.kts):

| Module | Responsibility | Useful entry points |
| --- | --- | --- |
| `app` | Compose UI, navigation, Android lifecycle, account import, application composition, and sync orchestration | `MainActivity.kt`, `QOwnNotesApplication.kt`, `AccountImportGateway.kt`, `AppSettings.kt` |
| `core` | Portable models, contracts, naming policy, search policy, internal links, and encrypted-note detection | `Note.kt`, `Account.kt`, `Contracts.kt`, `NoteNaming.kt`, `MarkdownLinks.kt` |
| `data` | Room entities, DAOs, repositories, migrations, pull application, and push result application | `QOwnNotesDatabase.kt`, `Entities.kt`, `RoomNoteRepository.kt`, `RoomAccountRepository.kt`, `RoomPushStore.kt` |
| `backend-nextcloud` | Nextcloud SSO integration, capability negotiation, Notes API transport, DTOs, and HTTP error classification | `NextcloudBackend.kt` |
| `markdown-android` | Markwon rendering/editing, Android text widgets, safe images and links, syntax highlighting, selection, search highlights, and edit history | `MarkdownRenderer.kt`, `MarkdownEditor.kt`, `SelectableLinkMovementMethod.kt`, `TextEditHistory.kt` |

Keep these boundaries intact:

- UI code observes repositories and calls application-level operations. It must not call Room DAOs,
  Retrofit APIs, SSO APIs, or document providers directly.
- `core` must not depend on Android or backend-specific types. Put reusable policy there.
- Keep Room details in `data`, Nextcloud protocol details in `backend-nextcloud`, and Android text
  behavior in `markdown-android`.
- Prefer constructor injection and the existing `ApplicationComponent`; do not add a DI framework
  without a concrete need.

## Core Behavioral Invariants

- Room is the source of truth for Nextcloud-backed accounts. Screens render repository `Flow`
  values, not network responses.
- Persist user content and deletion intent before network work. Offline operation is normal.
- Every note has a stable local UUID. Do not use a remote ID, title, or filename as local identity.
- Never let an older pull or push response replace a newer local revision.
- Use the last known note ETag for updates. A concurrent server change must become an explicit
  conflict, never a silent overwrite.
- Preserve pending local state across refreshes, process recreation, and retryable failures.
- Pull checkpoints advance only after a complete pull is applied transactionally. Do not infer
  remote deletions from an incomplete chunked response.
- Keep account data and synchronization serialized and account-scoped.
- Treat authentication, authorization, account removal, permission, conflict, protocol, storage,
  and retryable failures as distinct cases where the existing contracts do so.
- Sanitize rendered Markdown and fail closed for unsafe URLs, HTML, images, filesystem access, and
  encrypted content.

## Nextcloud Integration

The app requires Nextcloud Notes API 1.2 or newer. The API base path is:

```text
/index.php/apps/notes/api/v1/
```

Current protocol behavior includes capability validation, incremental pulls using collection
ETags and `pruneBefore`, chunk traversal, creation, ETag-protected updates, and deletion. A Notes API
`DELETE` removes the note through Nextcloud's file handling, which normally makes it available in
the Nextcloud trash bin.

When changing the backend:

- Keep Retrofit DTOs and HTTP details inside `backend-nextcloud`.
- Add MockWebServer coverage for paths, methods, headers, bodies, canonical responses, and error
  classification.
- Handle HTTP 404 idempotently only where the operation makes that safe, such as deleting a note
  that is already absent. Do not silently recreate a missing note during update.
- Never add real server credentials, account tokens, or test accounts to the repository.
- Real SSO testing requires the Nextcloud Files Android app and an authenticated account; automated
  tests use fakes and MockWebServer.

## Room And Synchronization Changes

The current Room database and exported schema history are under `data/`. For a schema change:

1. Increase the database version.
2. Add an explicit migration and register it in production and test database builders.
3. Preserve existing data unless destructive migration is explicitly required.
4. Update exported schemas under `data/schemas/`.
5. Extend `QOwnNotesDatabaseMigrationTest` and relevant repository/store tests.

An enum value stored through the existing string converter does not by itself alter the SQL schema,
but every query that filters or transitions that state still needs coverage. Keep multi-row state
changes transactional when partial application would violate synchronization invariants.

Durable WorkManager synchronization is planned in ADR 0005 but is not yet the current scheduler.
Inspect `ApplicationComponent` before changing scheduling behavior rather than assuming the planned
worker already exists.

## Compose And Markdown Guidance

- Preserve the existing Material 3 visual language and support both light and dark themes.
- The application theme must remain a `Theme.AppCompat` descendant. Markwon is hosted in
  `AppCompatTextView` and `AppCompatEditText` instances through `AndroidView`.
- Pass Compose colors into hosted views explicitly; Android views do not inherit the Compose color
  scheme automatically.
- Do not observe rapidly changing state directly in an `AndroidView` update callback when the view
  may detach during navigation. Use the established remembered-view/effect pattern.
- Highlighting and rendering must never modify the Markdown source, cursor, or selection.
- Rendered-text offsets differ from source offsets. Features acting on rendered text must use the
  rendered `Spannable`, not source positions.
- Markwon reinstalls its movement method during rendering. Keep selection and link handling in the
  existing selection-capable movement-method path.
- Editor interaction tests must tap for focus and inject real key events when testing typing;
  replacing text directly does not validate cursor, focus, or IME behavior.

## Upstream Projects And APIs

Use upstream source to answer protocol and compatibility questions instead of guessing. The desktop
and Android applications below are behavioral references; they are not modules or source trees in
this repository.

| Project or API | How it relates to this app | Links |
| --- | --- | --- |
| QOwnNotes desktop | Parent project and compatibility reference for naming, Markdown behavior, note folders, links, media conventions, encryption detection, and conflict-safety principles | [Repository](https://github.com/pbek/QOwnNotes), [note-folder model](https://github.com/pbek/QOwnNotes/blob/main/src/entities/notefolder.h) |
| Nextcloud Notes Android | Product and Android UX reference, including SSO and Markwon usage | [Repository](https://github.com/nextcloud/notes-android) |
| Nextcloud Notes server | Authoritative implementation of Notes API behavior and server-side file/category handling | [Repository](https://github.com/nextcloud/notes), [API overview](https://github.com/nextcloud/notes/blob/main/docs/api/README.md), [API v1](https://github.com/nextcloud/notes/blob/main/docs/api/v1.md) |
| Nextcloud Notes internals | Useful when public API documentation does not explain folder scanning, categories, chunking, or `notesPath` behavior | [NotesService.php](https://github.com/nextcloud/notes/blob/main/lib/Service/NotesService.php), [Controller Helper.php](https://github.com/nextcloud/notes/blob/main/lib/Controller/Helper.php), [SettingsService.php](https://github.com/nextcloud/notes/blob/main/lib/Service/SettingsService.php) |
| Nextcloud Android Single Sign-On | Actual authentication dependency; accounts and credentials remain owned by Nextcloud Files and the SSO library | [Repository](https://github.com/nextcloud/Android-SingleSignOn) |
| Nextcloud Files Android | Provides the installed account selected through SSO and is required for the current onboarding flow | [Repository](https://github.com/nextcloud/android) |
| Nextcloud Login Flow v2 | Reference for a possible future standalone login path without Nextcloud Files | [Documentation](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html) |
| Markwon | Actual Android Markdown renderer and editor dependency | [Documentation](https://noties.io/Markwon/), [repository](https://github.com/noties/Markwon) |
| MD4C | Portable Markdown parser used only as a possible QOwnNotes compatibility reference; it is not the Android rendering stack | [Repository](https://github.com/mity/md4c) |
| Retrofit and OkHttp | HTTP API abstraction and MockWebServer test infrastructure used by the Nextcloud backend | [Retrofit](https://github.com/square/retrofit), [OkHttp](https://github.com/square/okhttp) |

Dependency versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
Verify maintenance status, compatibility, and licensing before adding or upgrading dependencies.

## Build And Test Workflow

Use the reproducible environment when available:

```sh
devenv shell
```

Preferred commands are defined in [`Justfile`](Justfile):

```sh
just build          # Debug APK
just test           # JVM tests
just format         # Apply Spotless formatting
just format-check   # Verify formatting
just lint           # Android debug lint
just license-check  # Production dependency licenses
just check          # Host-side checks and debug APK
just device-test    # All connected Android instrumentation tests
```

Use focused Gradle tasks while iterating, then run `just check` before considering a change
complete. Device tests require an emulator or physical device; use `adb devices` to confirm one is
available. The project-local API 36 emulator can be created with `just create-avd` and started with
`just start-emulator`.

Test changes at the boundary they affect:

- `core/src/test`: pure policy and model behavior.
- `backend-nextcloud/src/test`: protocol and MockWebServer behavior.
- `data/src/androidTest`: Room repositories, transactions, and migrations.
- `markdown-android/src/test`: pure Markdown/editor helpers.
- `markdown-android/src/androidTest`: Android rendering and editor widgets.
- `app/src/androidTest`: Compose navigation and end-to-end user flows using
  `TestQOwnNotesApplication` and `FakePullBackend`.

If no device is connected, compile Android tests and report that they were not executed. Do not
claim device or real-server verification from compilation alone.

## Change Discipline

- Inspect the relevant implementation, tests, ADRs, and plan section before editing.
- Prefer the smallest correct change and follow existing patterns before adding abstractions.
- Add tests for behavior and regressions, especially synchronization state transitions.
- Keep test tags stable when possible; device tests use them extensively.
- Do not edit generated build output. Room schema JSON is generated but intentionally versioned.
- Run `git diff --check` and formatting checks before committing.
- Do not commit secrets, local SDK paths, emulator state, signing files, or Nextcloud credentials.
- Keep unrelated worktree changes intact.
