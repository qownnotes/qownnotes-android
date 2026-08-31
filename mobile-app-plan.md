# QOwnNotes Mobile Application Plan

Status: Phase 1 complete; Phase 2 in progress
Primary platform: Android
Potential later platform: iOS
Project location: Separate repository
Minimum Android version: Android 9 / API 28

## Implementation Status

Implemented in the initial Phase 1 foundation:

- Gradle multi-module project with `app`, `core`, `data`, `backend-nextcloud`, and `markdown-android` boundaries.
- Kotlin/JVM domain models and explicit repository, backend, synchronization, naming, and link-resolver contracts.
- QOwnNotes-compatible default note naming and creation, including stable local identities and deterministic unit tests.
- Room account/note schema, exported schema history, DAO, and Room-backed repository.
- Compose Material 3 light/dark theme, note-list/detail navigation, and offline local note creation used to exercise the foundation.
- Nextcloud and Android Markdown module boundaries; their production integrations remain Phase 2 work.
- Reproducible `devenv` environment with JDK 17, Android API 36, build tools 35/36, platform tools, and an API 36 x86_64 emulator image.
- JVM tests, Android lint, debug APK assembly, and a Compose device test that launches the app and creates an offline note.
- GitHub Actions CI running formatting, tests, assembly, lint, and dependency-license validation through `devenv`.
- Spotless formatting, Licensee policy enforcement, and Dependabot updates for Gradle and GitHub Actions.
- Architecture decision records under `docs/architecture` for module boundaries, offline persistence, Nextcloud integration, Markdown presentation, and synchronization scheduling.

The Phase 1 local bootstrap account has been replaced by Nextcloud SSO account onboarding for the Phase 2 read path.

Phase 2 now has an initial end-to-end read path: Nextcloud SSO account import, account and pull-checkpoint persistence, Notes API capability validation, incremental chunked pulls, transactional Room caching, offline search, account switching, and Markwon rendering. Real-server interoperability and the remaining Markdown compatibility/security fixtures must be verified before Phase 2 is marked complete.

Verified development commands are documented in `README.md`. The baseline verification command is `devenv shell -- just check`; device tests use `just create-avd`, `just start-emulator`, and `just device-test` from inside `devenv shell`.

## Purpose

Build an Android-first QOwnNotes mobile application for viewing, creating, and editing Markdown notes. The application should initially synchronize notes through the Nextcloud Notes API, then add a local-folder-only backend. Its architecture must allow more backends later without rewriting the editor or note-list user interface.

The first release should feel similar to Nextcloud Notes for Android while preserving important QOwnNotes behavior, especially Markdown compatibility and QOwnNotes-style new-note names.

This document is intended to be usable as the complete implementation prompt in a future development session.

## Confirmed Decisions

- Android is the main focus.
- Use a native Android application written in Kotlin.
- Use Jetpack Compose and Material 3 for the application UI.
- Support Android 9 / API 28 and newer.
- Create the mobile application in a separate repository from QOwnNotes.
- Use Nextcloud as the first backend.
- Authenticate like Nextcloud Notes for Android, using the Nextcloud Android Single Sign-On integration and an account from the installed Nextcloud Files application.
- Add a local-folder-only backend after the first Nextcloud milestone.
- Design explicit backend contracts so more backends can be added later.
- Do not build iOS in the first phase.
- Keep the reusable domain and synchronization logic independent enough to extract into Kotlin Multiplatform later.
- Provide a rendered Markdown viewing mode.
- Provide a Markdown source editor with live syntax highlighting.
- Do not use a WYSIWYG editor for the first release.
- Create notes with the standard QOwnNotes dated default name and matching initial Markdown heading.

## Platform Evaluation

### Recommended: Native Kotlin and Jetpack Compose

Native Kotlin provides the best Android lifecycle, background work, account integration, secure storage, filesystem access, accessibility, performance, and text-editing behavior. It also provides direct access to the Nextcloud Android Single Sign-On library.

The reusable core can be extracted into Kotlin Multiplatform after the Android application and synchronization rules are stable. This gives iOS a practical path without making the Android MVP depend on cross-platform UI maturity.

### Alternatives Not Selected

| Platform | Android quality | iOS potential | Main concern |
| --- | --- | --- | --- |
| Kotlin and Compose Multiplatform from day one | Good | Excellent | Adds cross-platform complexity before the domain and sync behavior are stable |
| Flutter | Good | Good | More custom integration work for Nextcloud SSO, Android folder access, and native Markdown editing |
| Qt and QML | Moderate | Good | Existing QOwnNotes C++ code is desktop-oriented and does not offset weaker mobile integration |
| React Native | Good | Good | No meaningful advantage for this application and still requires native integration work |

## Relationship to Desktop QOwnNotes

QOwnNotes desktop is a file-first application. Markdown files in a selected directory are its source of truth, and an external synchronization client usually synchronizes that directory. QOwnNotes does not use the Nextcloud Notes API for ordinary note-content synchronization.

The mobile application therefore requires a new offline synchronization engine. It should not directly port the desktop `Note` or `CloudService` classes. Those classes are coupled to Qt Widgets, Qt SQL, global settings, filesystem behavior, and desktop dialogs.

Reuse QOwnNotes behavior as specifications and compatibility fixtures, especially:

- New-note naming and initial content
- Title and filename cleanup rules where applicable
- Markdown dialect and extensions
- Wiki links and legacy internal links
- Local media and attachment path conventions
- Encrypted-note detection
- Conflict-safety principles

The portable MD4C library may be useful as a behavioral reference, but the Android application should use an Android-appropriate Markdown stack.

## Initial Product Scope

The first usable Android release must provide:

- Import a Nextcloud account through Nextcloud Android Single Sign-On.
- Detect whether the Nextcloud Notes server application and a supported API version are available.
- Download and cache existing notes.
- Display the cached note list while offline.
- Search notes by title and content.
- Open and render a note as Markdown.
- Switch from rendered view to highlighted Markdown source editing.
- Create a note with a QOwnNotes-compatible default name and initial heading.
- Save editor changes locally without waiting for the network.
- Synchronize created and edited notes in the background.
- Show read-only notes without enabling editing.
- Detect concurrent server changes using ETags.
- Preserve local content when synchronization fails.
- Support light and dark themes.
- Handle rotation, navigation, and process recreation without losing editor text.

## First-Release Non-Goals

These features are valuable but are not required before the basic view, edit, create, and synchronize workflow is reliable:

- Deleting notes
- Trash and restoration
- Favorites
- Note sharing
- Widgets
- Multiple simultaneous backends
- Attachment upload
- Image insertion
- Interactive task toggling in rendered view
- Full QOwnNotes encrypted-note editing
- QOwnNotes scripting hooks
- Automatic three-way text merging
- iOS application

The data model should avoid blocking these later features.

## New-Note Behavior

Match the standard automatic QOwnNotes naming behavior.

The generated name is the localized word `Note` followed by the device's local date and time:

```text
Note yyyy-MM-dd HHhmmsss
```

Example:

```text
Note 2026-08-31 14h08s27
```

The equivalent date-time pattern is conceptually:

```text
yyyy-MM-dd HH'h'mm's'ss
```

Initial content:

```markdown
# Note 2026-08-31 14h08s27

```

Creation rules:

- Generate the name using the device's local time zone.
- Localize the word `Note`.
- Create and persist the note locally immediately, including while offline.
- Assign a stable local UUID before attempting server synchronization.
- Send the generated name as the Nextcloud note title.
- Send the generated heading as the initial content.
- Adopt the sanitized title returned by the Nextcloud server.
- If the server changes the title because of invalid filename characters or a collision, update the local title without rewriting unrelated user content.
- Two locally created notes must remain distinct even if they are created within the same second.

For the first release, automatic title-to-first-heading renaming applies only when creating a note. Existing Nextcloud notes can intentionally have a title different from the first content line. A later setting may provide QOwnNotes-compatible title-following behavior.

## Markdown User Experience

### Viewing Mode

Viewing mode displays fully rendered Markdown and must support:

- Headings
- Paragraphs and line breaks
- Emphasis and strong emphasis
- Strikethrough
- Ordered and unordered lists
- Blockquotes
- Horizontal rules
- Inline code
- Fenced and indented code blocks
- Syntax coloring inside fenced code blocks
- Links and autolinks
- Images
- GFM tables
- Task-list markers
- QOwnNotes wiki links
- Legacy `note://` links

External links should open through the operating system. Internal note links should navigate inside the application. Broken internal links should be visually distinguishable.

### Editing Mode

Editing mode keeps the Markdown source visible and applies live syntax highlighting. It is not a WYSIWYG editor.

Highlight at least:

- ATX and Setext headings
- Emphasis and strong emphasis
- Strikethrough
- Ordered and unordered list markers
- Task markers
- Blockquotes
- Inline code
- Backtick and tilde code fences
- Fence language identifiers
- Links
- Images
- Tables
- Wiki links
- YAML frontmatter
- HTML comments

Editor requirements:

- Preserve the exact Markdown text while styling it.
- Preserve the cursor and selection while highlighting is reapplied.
- Run expensive parsing or highlighting away from the main thread.
- Discard stale highlighting results if the text changed while parsing.
- Remain responsive for large notes.
- Preserve undo and redo behavior.
- Provide a mobile formatting toolbar for common Markdown operations.
- Keep editor draft persistence separate from remote synchronization.

### Recommended Markdown Implementation

Use Markwon for the Android implementation because it is already used by Nextcloud Notes Android and provides both rendered Markdown and an `EditText` editor-highlighting module.

Jetpack Compose can host mature Android text widgets through `AndroidView`:

- Use an `AppCompatTextView` or equivalent for Markwon-rendered viewing.
- Use an `AppCompatEditText` or equivalent with `MarkwonEditor` for highlighted source editing.
- Keep these Android-specific adapters behind application interfaces so they do not enter the portable domain layer.

Do not implement the editor solely with a Compose `BasicTextField` unless profiling and a prototype demonstrate correct cursor, selection, span, input-method, and large-document behavior.

Before adding dependencies, verify current versions, licenses, maintenance status, and compatibility with the selected Android Gradle Plugin and Kotlin versions.

### Highlighting and Save Scheduling

- Update the in-memory editor state immediately on every text change.
- Persist drafts to Room after a short debounce and whenever the editor loses focus or closes.
- Schedule Markdown highlighting independently from persistence.
- Perform remote synchronization after a longer debounce, an explicit save action, app backgrounding, or WorkManager execution.
- Never make one API request per keystroke.
- Never discard a newer local edit when an older background operation completes.

## Markdown Compatibility Scope

### Required in the MVP

- CommonMark block and inline syntax
- GitHub Flavored Markdown tables
- GitHub Flavored Markdown strikethrough
- GitHub Flavored Markdown autolinks
- Task markers `[ ]`, `[x]`, `[X]`, and QOwnNotes `[-]`
- Backtick and tilde fenced code blocks
- YAML frontmatter preservation and suppression from rendered output
- Wiki links such as `[[Note]]`
- Wiki-link aliases such as `[[Note|Label]]`
- Wiki-link headings such as `[[Note#Heading]]`
- Qualified wiki-link paths such as `[[folder/Note]]`
- Legacy `note://` links
- Safe handling of standard links and images
- Detection of QOwnNotes encrypted blocks

### Early Follow-Up

- Clickable task toggling that safely updates the corresponding source range
- QOwnNotes image dimensions such as `{ width=300 height=200 }`
- More complete fenced-code language coverage
- Local `media/` and `attachments/` resolution for the local-folder backend
- Missing wiki-link note creation
- QOwnNotes optional underline semantics

### Later

- LaTeX typesetting
- Full QOwnNotes encryption, decryption, and re-encryption compatibility
- Legacy encryption formats
- Script-defined rendering or highlighting hooks
- Exact desktop preview CSS parity

### Markdown Security

- Sanitize raw HTML before rendering.
- Do not permit scripts or active embedded content.
- Reject unsafe URL schemes.
- Do not expose unrestricted filesystem paths.
- Constrain local-folder links to the selected note-folder tree.
- Do not enable unrestricted WebView file access.
- Show encrypted notes as locked or unsupported until the encryption codec is implemented and tested.

## Proposed Architecture

Use a small number of clear modules or equivalent package boundaries:

```text
app
core
data
backend-nextcloud
markdown-android
```

Add later:

```text
backend-local-folder
shared-kmp
ios-app
```

Do not introduce layers without a concrete boundary. Multiple backends and later iOS sharing are concrete reasons to keep domain policy separate from Android adapters.

### Core Responsibilities

- Note and account models
- Backend capabilities
- Note naming policy
- Synchronization state and decisions
- Conflict models
- Search normalization
- Internal-link parsing
- Markdown compatibility preprocessing that does not depend on Android

### Android Responsibilities

- Compose screens and navigation
- Android text widgets and Markwon integration
- Room database
- WorkManager scheduling
- Nextcloud Android SSO
- Android secure storage
- Storage Access Framework
- Android intents for opening links and attachments

### Core Contracts

The exact signatures can evolve, but the architecture should have explicit contracts equivalent to:

```kotlin
interface NoteRepository
interface NoteBackend
interface SyncCoordinator
interface NoteNamingPolicy
interface MarkdownLinkResolver
```

UI code observes repositories and invokes use cases. It must not call Retrofit, Room DAOs, or Android document providers directly.

Avoid tying the core to Hilt or another Android-only dependency-injection framework. Constructor injection and a small application component are sufficient initially and make later extraction easier.

## Data Model

A note needs at least:

- Stable local UUID
- Local database ID if Room benefits from a separate key
- Backend/account ID
- Optional remote ID
- Title
- Markdown content
- Category
- Modified time
- Remote ETag
- Read-only state
- Synchronization state
- Last synchronized title
- Last synchronized content
- Last synchronized category
- Last synchronized favorite state when favorites are implemented
- Last synchronization error summary

Represent synchronization state with a sealed type or enum rather than combinations of booleans:

```text
Synchronized
LocallyCreated
LocallyModified
Synchronizing
Conflict
Failed
```

Deletion states can be added when deletion is implemented:

```text
LocallyDeleted
PendingRemoteDeletion
RemotelyDeleted
```

Store the last successfully synchronized state so a conflict can distinguish local-only, remote-only, and concurrent changes.

## Repository and Offline Behavior

For Nextcloud accounts, Room is the application's source of truth.

- Screens observe Room through Flow.
- Network responses update Room in transactions.
- Editor changes update Room before remote synchronization.
- The UI does not wait on the network to display or modify a note.
- WorkManager synchronizes pending changes when constraints permit.
- An explicit refresh can start immediate synchronization.
- Process recreation must recover pending local changes from Room.
- Retryable failures must retain the pending state.
- Authentication, permission, storage, read-only, and conflict failures must remain distinguishable.

## Nextcloud Backend

### Authentication

Match Nextcloud Notes Android initially:

- Use the Nextcloud Android Single Sign-On library.
- Import an account from the installed Nextcloud Files Android application.
- Do not ask for or store the user's normal password.
- Keep account-specific API clients isolated.
- Handle account removal and revoked authorization.

A standalone Nextcloud Login Flow v2 implementation can be added later if the application should work without Nextcloud Files.

### API

Use the Nextcloud Notes REST API under:

```text
/index.php/apps/notes/api/v1/
```

The mobile client should require Notes API 1.2 or newer for ETag-based optimistic concurrency and safe synchronization. If broader legacy support is later required, add it as an explicit compatibility path rather than weakening the normal synchronization rules.

Relevant note fields:

- `id`
- `etag`
- `readonly`
- `content`
- `title`
- `category`
- `favorite`
- `modified`

Relevant operations:

- `GET /notes`
- `GET /notes/{id}`
- `POST /notes`
- `PUT /notes/{id}`
- Later: `DELETE /notes/{id}`
- Optional capability-gated attachment operations from API 1.4

### Pull Synchronization

- Request server capabilities and choose the highest supported compatible Notes API version.
- Fetch notes using the previous collection `ETag` through `If-None-Match`.
- Use `Last-Modified` as `pruneBefore` to reduce transferred content.
- Use chunking where supported.
- Follow `X-Notes-Chunk-Cursor` until a response has no next cursor.
- Treat pruned records as existing unchanged notes.
- Detect remote deletion only after processing the final chunk.
- Do not overwrite a note that has local unsynchronized changes.
- Update collection synchronization metadata only after a complete successful pull.

### Push Synchronization

- Create locally created notes with `POST /notes`.
- Store the returned remote ID, canonical title, modified time, content, category, and ETag.
- Update existing notes with `PUT /notes/{id}`.
- Send the last known ETag in `If-Match`.
- Do not update read-only notes.
- Treat HTTP 412 as a conflict.
- Treat HTTP 404 during update as a recoverable state requiring an explicit policy; do not silently recreate without preserving identity and informing the user.
- Treat transient network and server errors as retryable.
- Treat authentication and permission failures as user-action-required errors.

### Conflict Handling

Never resolve a conflict by silently overwriting local or remote content.

The initial conflict UI can offer:

- Use the server version
- Keep the local version by creating a new note
- Review local and server versions before choosing

Retain the common base version in storage. A later release may implement attribute-level merging and automatic three-way content merging.

## Local-Folder Backend

Add the local-only backend after the Nextcloud view, edit, create, and synchronization workflow is reliable.

### Android Storage Model

- Let the user select a directory through the Storage Access Framework.
- Persist the granted tree URI permission.
- Do not require unrestricted storage permissions.
- Initially index `.md` and `.txt` files.
- Treat files as the backend source of truth.
- Store private application metadata in the app database, not inside the selected note directory.
- Use atomic replacement where supported by the document provider.
- Use metadata scans to detect external changes; do not depend exclusively on filesystem watchers.
- Preserve unknown files and unsupported metadata.
- Keep all path resolution inside the selected tree.

### QOwnNotes Folder Compatibility

Support progressively:

- Note subfolders
- Markdown files with UTF-8 content
- `media/` images
- `attachments/` files
- Relative links using `../` from nested folders
- Legacy `file://media/` and `file://attachments/` forms where safely resolvable
- Wiki links that prefer the current subfolder and can use qualified paths
- Frontmatter preservation
- Configurable default extension

Do not use a mutable filename as the application's only note identity. Maintain an internal stable identity and treat the path as mutable backend metadata.

## Future Backends

The backend contract should allow later adapters without changing screen-level behavior. Possible future adapters include:

- Standalone Nextcloud Login Flow v2 accounts
- WebDAV folders
- Another Notes-compatible REST service
- Git-backed folders
- Folders synchronized externally by Syncthing or another Android application

Do not add speculative compatibility code before a backend is selected. Keep the contracts capability-based so backends can declare support for categories, hierarchy, favorites, attachments, sharing, trash, and read-only notes.

## iOS Strategy

Do not implement iOS in the initial project phase.

Prepare for it by keeping these concepts platform-neutral:

- Note models
- Backend interfaces
- Naming policy
- Notes API request and response models where practical
- Synchronization decisions
- Conflict state
- Internal-link parsing
- Markdown preprocessing rules
- Compatibility test fixtures

After Android stabilizes, extract appropriate pure Kotlin code into Kotlin Multiplatform. Keep these platform-specific:

- Android SSO
- Room and WorkManager
- Storage Access Framework
- Android Markdown widgets
- iOS Keychain and file-provider access
- Native platform UI

The iOS application can use a native SwiftUI or Compose Multiplatform UI after evaluating the ecosystem at implementation time. Shared business logic does not require committing to shared UI.

## Implementation Phases

### Phase 1: Repository and Foundation

- Create the separate Android repository.
- Add a Kotlin/Compose application targeting Android 9 and newer.
- Configure CI, formatting, linting, unit tests, dependency updates, and license checks.
- Establish the core, data, Nextcloud backend, and Markdown Android boundaries.
- Add Room and the initial account/note schema.
- Add application navigation and theme foundations.
- Record architecture decisions that affect contributors.

### Phase 2: Nextcloud Read Path

- Integrate Nextcloud Android Single Sign-On.
- Import and persist account metadata.
- Query server capabilities and validate the Notes API version.
- Implement the Notes API client.
- Download and transactionally cache notes.
- Implement incremental and chunked pulls.
- Build the note list and search.
- Build the rendered Markdown note view.
- Verify offline startup and account switching behavior if multiple accounts are already supported.

Status: In progress

Implemented:

- Added Nextcloud Android Single Sign-On 1.3.4 and account import through the installed Nextcloud Files application.
- Added persisted SSO account references and server metadata without copying credentials into Room.
- Added Notes capability discovery, semantic API-version selection, and enforcement of Notes API 1.2 or newer.
- Added the Notes API v1 read client with collection ETags, `pruneBefore`, HTTP 304 handling, chunk size/cursor support, and typed authentication, permission, protocol, and retryable failures.
- Added Room schema version 2 and migration 1 to 2 for API version and collection synchronization checkpoints.
- Added transactional pull application that preserves stable local UUIDs and does not overwrite locally changed notes.
- Added account-scoped title/content search, cached note display, manual refresh, and account switching.
- Replaced the raw note detail text with a Markwon-rendered view supporting core Markdown, tables, strikethrough, task lists, and YAML-frontmatter suppression.
- Added tests for API-version negotiation, frontmatter preprocessing, and transactional pull identity/checkpoint behavior.
- Hardened chunk traversal for the Notes API's numeric pending-count header, repeated or inconsistent cursors, interrupted pulls, malformed note IDs, malformed JSON, and coroutine cancellation.
- Added a Room 1-to-2 migration fixture and pull-store coverage for pruned records, remote deletion, HTTP 304-equivalent no-op results, and preservation of every unsynchronized note state.
- Added MockWebServer coverage for incremental request headers, first-page query parameters, HTTP 304 handling, chunk traversal, forward-compatible JSON parsing, malformed responses, HTTP error classification, and network interruption between chunks.

Remaining before Phase 2 is complete:

- Verify account import, capability negotiation, full pull, incremental pull, and chunked pull against supported real Nextcloud and Notes server combinations.
- Run the Room migration and broadened transactional pull tests on an Android device in CI, and add rollback/account-isolation coverage.
- Complete safe external-link handling, internal wiki-link and `note://` navigation, images, fenced-code syntax coloring, encrypted-block detection, and the remaining Markdown compatibility fixtures.
- Improve account-removal and revoked-authorization handling and add complete onboarding, offline-restart, and account-switching device tests.

### Phase 3: Highlighted Editing and Creation

- Add the highlighted Markdown source editor.
- Add asynchronous, stale-result-safe highlighting.
- Add the Markdown formatting toolbar.
- Add draft persistence and editor state restoration.
- Implement the QOwnNotes default naming policy.
- Create notes offline with a stable local identity.
- Push note creation to Nextcloud.
- Push edits using `If-Match` and the last known ETag.
- Adopt canonical server responses.

### Phase 4: Synchronization Safety

- Implement reliable WorkManager scheduling and constraints.
- Separate retryable errors from user-action-required errors.
- Handle read-only notes.
- Handle deleted remote notes safely.
- Add conflict storage and conflict-resolution screens.
- Verify no local edit can be replaced by an older pull or push result.
- Add telemetry-free diagnostics suitable for user bug reports.

### Phase 5: Local-Only Folder Backend

- Add folder selection and persistent URI permissions.
- Index Markdown and text files.
- Create, open, edit, and externally refresh files.
- Add safe write and rename behavior.
- Add subfolder and wiki-link resolution.
- Add local images and attachment opening.
- Reuse the same note list, viewer, and editor screens.

### Phase 6: Extended Features

- Delete and trash behavior
- Favorites
- Interactive task checkboxes
- Images and attachments
- Sharing
- Widgets
- Multiple configured backends
- QOwnNotes title-to-filename options
- QOwnNotes encryption compatibility

### Phase 7: Shared Core and iOS

- Identify stable pure Kotlin components.
- Extract those components into Kotlin Multiplatform.
- Run shared protocol and compatibility tests on Android and iOS targets.
- Select and implement the iOS-specific UI, storage, account, and background execution adapters.

## Testing Strategy

### Unit Tests

- QOwnNotes name generation with an injected clock, locale, and time zone
- Same-second creation uniqueness
- Markdown preprocessing
- Frontmatter handling
- Wiki-link parsing and target resolution
- Legacy `note://` parsing
- Synchronization state transitions
- Push and pull decision logic
- Conflict detection
- Error classification
- Search normalization

### API Tests

Use a mock HTTP server to verify:

- Capability parsing
- Unknown JSON fields are ignored
- Missing fields from older minor versions are handled
- `If-None-Match` and HTTP 304 behavior
- `pruneBefore` behavior
- Chunk cursor handling
- Deletion detection only after the final chunk
- Canonical server responses after creation and update
- `If-Match` headers
- HTTP 401, 403, 404, 412, 5xx, and insufficient-storage behavior
- Network interruption during multi-chunk synchronization

### Database and Worker Tests

- Migrations
- Transactional pull application
- Pending local edits survive process recreation
- Worker retries do not duplicate note creation
- Older synchronization results cannot replace newer local edits
- Account removal cleans up the correct local state

### Markdown Compatibility Tests

Create shared fixtures for:

- CommonMark and GFM basics
- QOwnNotes task states
- Backtick and tilde code fences
- Tables
- Frontmatter
- Wiki-link aliases, headings, and qualified paths
- Existing and missing internal links
- Encoded links and image paths
- Encrypted-block detection
- HTML sanitization and unsafe schemes

Test rendered output and editor highlighting separately because they use different presentation paths.

### UI and Device Tests

- Note list loading and offline state
- Rendered-view to edit-mode transition
- Cursor and selection stability during highlighting
- Input methods, composing text, and non-Latin text
- Large-note responsiveness
- Rotation and process recreation
- Back navigation with unsaved text
- Light and dark themes
- Screen readers and scalable font sizes
- Phones and tablets

### Integration Tests

Run against supported Nextcloud/Notes server combinations and verify:

- Initial account import
- Initial full synchronization
- Offline creation followed by reconnect
- Editing the same note on desktop and mobile
- HTTP 412 conflict preservation
- Read-only shared notes
- Server title sanitization
- Server-side deletion
- Large note collections and chunked synchronization

## Acceptance Criteria for the Initial Release

- A user can import a Nextcloud account through the installed Nextcloud Files application.
- Existing Nextcloud notes are visible after initial synchronization.
- Previously synchronized notes remain available after restarting without network access.
- Common QOwnNotes Markdown renders correctly in light and dark modes.
- Markdown source is highlighted while remaining visible and editable.
- Highlighting never changes the stored source text.
- Editing remains responsive on representative large notes.
- A new note receives the QOwnNotes dated title and matching initial heading.
- A note can be created and edited while offline.
- Offline changes synchronize after connectivity returns.
- A server response that sanitizes a title is adopted safely.
- A concurrent server edit produces a conflict instead of silent data loss.
- A read-only server note cannot accidentally be modified.
- Rotation, navigation, and process death do not lose persisted editor text.

## Implementation Principles

- Prefer the smallest correct implementation for each phase.
- Make offline state the normal operating model, not an error case.
- Persist user content before starting network work.
- Never silently discard or overwrite note content.
- Keep backend-specific behavior out of screen code.
- Use stable local identities rather than mutable filenames.
- Preserve unknown server fields and capabilities through forward-compatible parsing behavior.
- Sanitize rendered content and constrain local resource access.
- Add compatibility behavior only when there is a concrete persisted-data or interoperability need.
- Verify dependencies and licenses before adoption.
- Keep the first pull requests focused; do not introduce the entire roadmap in one change.

## Source References

- QOwnNotes repository: <https://github.com/pbek/QOwnNotes>
- Nextcloud Notes Android: <https://github.com/nextcloud/notes-android>
- Nextcloud Notes server: <https://github.com/nextcloud/notes>
- Nextcloud Notes API overview: <https://github.com/nextcloud/notes/blob/main/docs/api/README.md>
- Nextcloud Notes API v1: <https://github.com/nextcloud/notes/blob/main/docs/api/v1.md>
- Nextcloud Login Flow: <https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html>
- Markwon: <https://noties.io/Markwon/>

## Prompt for the Next Session

Use this document as the authoritative planning baseline. Start by confirming that the new separate repository is the intended working directory. Then inspect its current state before making changes. Implement only the next incomplete phase, preserve all confirmed decisions above, and update this document when an implementation decision materially changes the plan.
