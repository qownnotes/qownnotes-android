# QOwnNotes Mobile Application Plan

Status: Phase 3 in progress
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

Phase 2 now has an end-to-end read path: Nextcloud SSO account import, account and pull-checkpoint persistence, Notes API capability validation, incremental chunked pulls, transactional Room caching, offline search, account switching, local cache removal, reconnect handling, and Markwon rendering. The implementation and automated coverage are complete; broader real-server interoperability and the configured CI device job must be verified before Phase 2 is marked fully complete.

Phase 3 now has an initial end-to-end write path with offline-first note creation, note creation from text shared by another application, and Markdown source editing, asynchronous source highlighting, a formatting toolbar, toolbar undo and redo, debounced and lifecycle-aware Room persistence, Nextcloud creation and ETag-protected updates, and stale-response protection through persisted local revisions. Nextcloud favorites are synchronized through the same guarded write path, can be changed offline (including on read-only notes), and sort ahead of other notes in normal and searched lists. Editor focus, cursor, and keyboard input are fixed and covered by device tests. Every listed Phase 3 task is implemented, but the phase is not complete: supplemental highlighting still runs on the main thread, and large-note responsiveness, real-server title sanitization and conflict behavior, and physical-device input methods are unverified. See the Phase 3 section for the full list.

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
- Create a note from text another application shares.
- Save editor changes locally without waiting for the network.
- Synchronize created and edited notes in the background.
- Show read-only notes without enabling editing.
- Detect concurrent server changes using ETags.
- Preserve local content when synchronization fails.
- Support light and dark themes.
- Let the reader adjust the note body text size and remember that choice.
- Find text inside the note that is open.
- Select and copy text out of the rendered note.
- Handle rotation, navigation, and process recreation without losing editor text.

## First-Release Non-Goals

These features are valuable but are not required before the basic view, edit, create, and synchronize workflow is reliable:

- Trash and restoration
- Sharing a note out of the application
- Adding shared text to an existing note, which requires choosing that note
- Widgets
- Multiple simultaneous backends
- Note folder navigation and moving notes between folders
- Multiple note folders inside one Nextcloud account, which the Notes API cannot serve
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
- Open a note created from the note list directly in editing mode with the cursor after its initial
  heading.
- Send the generated name as the Nextcloud note title.
- Send the generated heading as the initial content.
- Adopt the sanitized title returned by the Nextcloud server.
- If the server changes the title because of invalid filename characters or a collision, update the local title without rewriting unrelated user content.
- Two locally created notes must remain distinct even if they are created within the same second.

For the first release, automatic title-to-first-heading renaming applies only when creating a note. Existing Nextcloud notes can intentionally have a title different from the first content line. A later setting may provide QOwnNotes-compatible title-following behavior.

## Text Shared From Another Application

A note is very often something read somewhere else first, so the application is a share target for text, as Nextcloud Notes for Android is. Sharing text creates a note from it.

- Accept `ACTION_SEND` with a text type. An attachment is a stream this release cannot store, and an intent carrying no text is an ordinary start, so neither creates a note.
- Read the shared text as a `CharSequence`, because it may be styled, and keep only its characters. No sharing application promises which Markdown its styling stood for.
- Name the note after the sharing application's subject when it sent one, sanitized by the same rule that names any other note, because a shared page or message already carries a name its reader recognizes. Without a usable subject, keep the dated default name.
- Write the name as the first heading and the shared text under it, so the note reads like every other note created here. Text that the sharing application also sent as the subject is not repeated under it.
- Create the note in the account currently being looked at and open it, so the shared text lands where the user can correct it.
- Hold a share that arrives before any account exists until onboarding has produced one, and say so on the onboarding screen. A note has to belong to an account, and dropping the text silently would lose it.
- Take the waiting share before writing the note rather than after. A share taken twice would leave a duplicate note that this release cannot delete, while text lost in that window is still held by the application that shared it.
- Keep the activity a single task and accept a share from `onNewIntent` as well, so a share reaches the window the user is looking at instead of a second copy of the application behind it.
- Accept a share from a first start only. A recreated activity is handed its intent again, and rotating the device would otherwise produce a second note.

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

### Selecting and Copying Rendered Text

A note is read far more often than it is edited, and taking a phone number, a command, or a paragraph out of a note must not require entering the editor.

- Let the reader select rendered note text with the platform's own gesture and copy it with the platform's own selection toolbar.
- Copy what the reader sees. Selection addresses the rendered text, so the copied text carries no Markdown markers.
- Keep links tappable while the text is selectable. Selection has to be the base behavior, because the framework only offers selection when the movement method reports that it can select arbitrarily and the link-only movement method also discards the selection as soon as a touch lands outside a link.
- Distinguish the gestures rather than letting them compete: only a short, stationary touch follows a link, so a press long enough to start a selection and a drag that scrolls the note leave the link alone.
- Keep the note scrollable. A selectable text view consumes touches a read-only one ignores, and scrolling is the more common gesture.

### Note Text Size

Readers must be able to enlarge note text without enlarging the rest of the interface.

- Adjust the size with `A-` and `A+` controls on the note screen, so the control is where the reading happens.
- Apply the size to both the rendered note and the Markdown source editor.
- Persist the choice for the whole application and restore it after process death.
- Use discrete steps and saturate at both ends rather than scaling without bound.
- Express sizes in scale-independent pixels so the setting composes with the system font size instead of replacing it. A reader who has already enlarged system text keeps that enlargement.
- Leave note titles, the note list, and application chrome at system size.
- Give the controls explicit accessibility descriptions, because `A-` and `A+` are announced as single letters.

Presentation preferences of this kind are device-local and are not synchronized, so they belong in `SharedPreferences` rather than in the Room schema that models note content.

### Finding Text in a Note

Notes grow long, and the note list search only says which note contains a word, not where.

- Open a find bar from the note screen and keep it visible above the note while the note scrolls.
- Search what the reader sees. The rendered note no longer contains the Markdown markers, and its offsets differ from the source offsets, so the source cannot address the rendered text.
- Match literally and case-insensitively, without overlapping matches, so a query can contain Markdown punctuation and every match is exactly as long as the query.
- Mark every match, mark the current one differently, and scroll it into sight.
- Report the position in the matches, such as `2 of 7`, and say when there are none.
- Move to the next and previous match and wrap around at both ends.
- Leave the note untouched: finding text may only add and remove its own spans.

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
- Scroll the editor as the caret moves so the active line remains visible while typing.
- Run expensive parsing or highlighting away from the main thread.
- Discard stale highlighting results if the text changed while parsing.
- Remain responsive for large notes.
- Preserve undo and redo behavior.
- Provide a mobile formatting toolbar for common Markdown operations.
- Continue unordered, ordered, and task-list markers when Return starts a new item, and leave the
  list when Return is pressed on an empty item.
- Keep editor draft persistence separate from remote synchronization.

### Undo and Redo

The framework `EditText` keeps an undo buffer, but only a hardware keyboard can reach it, so a phone needs explicit controls.

- Put undo and redo at the start of the formatting toolbar, where the writer already is, and disable them when there is nothing to step through.
- Group changes so one undo steps back over a burst of typing rather than over a single character. Continued typing, an input method rewriting its composing region, and a correction made inside what was just typed all belong to the step that produced that text. Consecutive deletions belong together. A line break ends a group. A formatting action is always its own step.
- Group structurally rather than by elapsed time, so the same edits produce the same steps on a fast and a slow device and the rules can be tested without a clock.
- Record from a text watcher. Highlighting only adds spans, which no text watcher sees, so the history cannot fill up with the editor's own presentation work.
- Keep the history out of the note. It describes an editing session, so it is bounded in length, it is not persisted, and it starts empty each time the editor opens.
- Clear the composing state before replaying a change, otherwise the input method goes on composing over text that is no longer there.
- If the text no longer matches what the history describes, forget the history rather than replay a change at a guessed position.

### Recommended Markdown Implementation

Use Markwon for the Android implementation because it is already used by Nextcloud Notes Android and provides both rendered Markdown and an `EditText` editor-highlighting module.

Jetpack Compose can host mature Android text widgets through `AndroidView`:

- Use an `AppCompatTextView` or equivalent for Markwon-rendered viewing.
- Use an `AppCompatEditText` or equivalent with `MarkwonEditor` for highlighted source editing.
- Keep these Android-specific adapters behind application interfaces so they do not enter the portable domain layer.

Constraints that apply when Compose hosts these widgets:

- The application theme must stay a `Theme.AppCompat` descendant. AppCompat widgets resolve their default styles from AppCompat theme attributes such as `editTextStyle`, and a framework-only theme silently leaves the editor without `focusableInTouchMode`, which makes typing impossible. This caused the 2026-09-01 editing defect.
- Do not rely on the theme alone for interactive behavior. The editor view sets its own focus and input-method flags so it keeps working if the host theme changes.
- Hosted widgets do not follow the Compose color scheme. Pass the Compose surface colors into them explicitly, otherwise rendered and edited text becomes unreadable in dark mode.
- Drive editor device tests with real key-event injection. Setting text directly on the widget bypasses input focus and cannot detect a non-typable editor.
- Markwon reinstalls a link-only movement method on every render, so a movement method that also supports selection has to be supplied to Markwon rather than set on the view afterwards. Enabling selection re-sets the widget's text and movement method, so it must happen before the Markdown is applied.
- Do not observe frequently changing state inside an `AndroidView` update block. Compose reschedules that block through the holder's `View.getHandler()`, which is null while the view is detached, so a state change during a screen transition crashes. Apply such values from a composition effect against a remembered view reference instead.

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
- Category, meaning the note's folder path relative to the account's notes root, with `/` between levels and an empty string for the root
- Modified time
- Remote ETag
- Read-only state
- Favorite state
- Synchronization state
- Last synchronized title
- Last synchronized content
- Last synchronized category
- Last synchronized favorite state
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

## Note Folders

QOwnNotes users organize notes in folders, and the mobile application should offer the same organization. The word "folder" covers two different QOwnNotes concepts, and the Nextcloud Notes API supports them very differently. This section records what the API actually allows and which behavior the application will adopt.

### What QOwnNotes Provides

- Note folders are several independently configured root directories. Exactly one is current, and switching it replaces the entire note list. Each carries a name, a local path, an optional cloud connection with a remote path, subfolder visibility, excluded subfolder paths, and its own active subfolder and tag state.
- Note subfolders are the directory tree inside the current note folder, at arbitrary depth, with a "show notes from all subfolders" option and per-path exclusion.

### What the Nextcloud Notes API Provides

- One notes root per user. The `notesPath` setting is a per-user server-side setting relative to the user's files root, defaulting to the localized name `Notes`. It is readable and writable through `GET /settings` and `PUT /settings` from API 1.2. Every other API response is scoped to it, and there is no way to request a second root.
- Subfolders are the `category` attribute, available since API 1.0. The server derives it from the note file's path relative to the notes root, recursing without a depth limit, using `/` as the delimiter. An empty string means the notes root.
- Writing `category` on `POST /notes` or `PUT /notes/{id}` moves the file and creates the missing folders. Illegal characters are removed and the sanitized value is returned in the response.
- The server ignores dot-prefixed folders unless the user's `showHidden` setting is enabled, and always ignores the per-note `.attachments.<id>` folders.
- Only files with the extensions `txt`, `org`, `markdown`, `md`, `note`, or the user's configured custom suffix are treated as notes.
- `GET /notes?category=` exists since API 1.1 but matches the category string exactly. It cannot return a subtree.
- The API never returns the list of categories. The web frontend has that list, the public API does not. A client only learns about folders that currently contain at least one visible note.
- The API has no category rename or delete operation. Those endpoints exist only in the web frontend controller.
- Deleting the last note in a folder deletes the folder that just became empty.

### Decisions

One Nextcloud account is one note folder. Read `notesPath` for display and never write it. It is a single shared per-user setting, so rewriting it to switch folders would silently repoint the web interface and every other client of that account, would not move any file, and would make every other client see its whole collection change. The application must not send `PUT /settings`.

Map QOwnNotes note subfolders onto `category`. The mapping is lossless in both directions, including nesting, so this is the supported way to give QOwnNotes users folders on a Nextcloud account.

Multiple roots come from multiple accounts now and from the local-folder backend later. That backend is file-first like the desktop application, so it can model QOwnNotes note folders directly.

Folder scope is a local view, not a server query. Keep pulling the full unfiltered collection. `?category=` matches exactly and cannot express a subtree; the response `ETag` and `Last-Modified` are computed for the requested query, so mixing filtered and unfiltered requests would corrupt the single stored pull checkpoint; and the rule that a note missing from the final chunk was deleted remotely would delete every out-of-scope note. Scoping locally also makes folder switching instant and available offline.

Folders are derived state, not stored entities. Build the tree from the distinct category values of the cached notes. This needs no new table and no note-schema migration, because `category` and `lastSyncedCategory` are already modeled, persisted, parsed, and sent.

An empty folder cannot exist on the server. Do not offer creating an empty folder as a durable object. A new-folder affordance may only pre-fill the category of a note that is being created.

The selected scope is a device-local preference. Persist it per account through the existing `AppSettings` rather than in Room, and fall back to the root when the remembered folder no longer contains notes.

Category normalization belongs in `core`. Split on `/`, trim each segment, drop empty, `.`, and `..` segments, remove the characters the server removes, and rejoin with `/`, where the root is the empty string. The local-folder backend needs the same policy, and the rule has to stay portable for a later Kotlin Multiplatform extraction. Always adopt the server's canonical category from the response, exactly as the canonical title is adopted today.

### Behavior

- Show folder navigation in the note list for the current account: the root plus the derived tree, with a note count per folder and a "show notes from subfolders" toggle matching QOwnNotes.
- Create new notes in the current folder scope. The naming policy takes the category, so a note created inside a folder stays there without a follow-up move.
- Scope the search to the current folder and its subtree, offer a way to search the whole account, and include the category in the matched fields as the Notes server's own search does.
- Move a note with `PUT /notes/{id}`, the new category, and `If-Match`. The server may sanitize the category, and it may also append a deduplicating suffix to the title when the target folder already holds a note with that title, so both fields must be adopted from the canonical response. A move follows the same conflict rules as a content edit, and a read-only note cannot be moved.
- Defer renaming and deleting a folder. Without an API operation, both are one guarded update per contained note: not atomic, interruptible, and able to fail halfway. If they are implemented, they must run through the synchronization queue with per-note conflict handling and a resumable record of what remains, never as a fire-and-forget loop.
- Keep resolving wiki links against the source note's category first and then across the account. That already matches the QOwnNotes preference for the current subfolder.
- Treat excluding a subfolder from the list as a view preference only. Excluded folders must still be synchronized, and excluding one must never influence the pull or remote-deletion detection.

### Query Rules

- A subtree query is `category = :scope OR category LIKE :scope || '/%'` and needs an explicit `ESCAPE` clause, because `%` and `_` are legal characters in a folder name.
- The root scope means every note of the account. Do not express it as a `LIKE` pattern.
- Add an index on `(accountId, category)` before filtering the note list by folder.
- Store and display the category exactly as the server returned it, and compare case-insensitively where QOwnNotes does, because the server's underlying storage may be case-insensitive while the attribute is a plain string.

### Capabilities

Extend `BackendCapabilities` with a `nestedCategories` flag beside the existing `categories` flag. Nextcloud and the later local-folder backend both support nesting, but a future flat backend may support categories without a hierarchy, and screen code must not assume the tree exists.

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

Status: Implementation complete; verification in progress

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
- Added safe rendered-link dispatch with an explicit HTTP/HTTPS allowlist, in-app wiki-link and legacy `note://` navigation, account-scoped deterministic resolution, aliases, qualified paths, broken-link styling, heading scrolling, and an internal-note back stack.
- Added Android instrumentation coverage for resolved and broken internal-link spans, click dispatch, and rendered heading lookup.
- Added fail-closed QOwnNotes encrypted-block detection that removes ciphertext before rendering and displays a locked state.
- Added bounded syntax coloring for common fenced-code languages and aliases, with oversized and unknown blocks falling back to plain code.
- Added HTTPS-only remote image rendering with credential, redirect, private-address, response-size, image-type, dimension, and decode-size limits; local and active URL schemes remain blocked.
- Added QOwnNotes indeterminate task rendering, one-pass encoded image URL canonicalization, and fail-closed raw HTML removal before span generation.
- Added account-scoped synchronization state, same-account refresh serialization, cancellation propagation, and targeted error persistence that cannot roll back a newer checkpoint.
- Added removed-account and revoked-authorization classification with cached-note-preserving reconnect UI, pre-persistence validation for new imports, and checkpoint-preserving reauthorization.
- Added forced transactional rollback, missing-account, targeted-error, and duplicate-remote-ID account-isolation device coverage.
- Added account-scoped local-data removal backed by Room's foreign-key cascade, with confirmation that Nextcloud Files accounts and server notes remain untouched.
- Added targeted reconnect identity checks, capability revalidation, checkpoint preservation, and separate loading states for cached accounts and notes.
- Added deterministic app instrumentation for onboarding, initial pulls, offline activity restart, reconnect, account switching, and local removal.
- Added an API 36 emulator CI job that runs all app, Room migration/storage, Nextcloud classification, and Markdown instrumentation tests.
- Verified the complete instrumentation suite on a physical Android 16 device and documented the manual Nextcloud compatibility procedure.

Remaining verification before Phase 2 is complete:

- Record server, Notes app, and Nextcloud Files versions for supported real-server combinations, including an incremental update and a collection large enough to exercise chunking.
- Confirm the new emulator device-test job passes in GitHub Actions.

### Phase 3: Highlighted Editing and Creation

Status: In progress

- Add the highlighted Markdown source editor.
- Add asynchronous, stale-result-safe highlighting.
- Add the Markdown formatting toolbar.
- Add undo and redo controls.
- Add draft persistence and editor state restoration.
- Implement the QOwnNotes default naming policy.
- Create notes offline with a stable local identity.
- Push note creation to Nextcloud.
- Push edits using `If-Match` and the last known ETag.
- Adopt canonical server responses.

Implemented:

- Added QOwnNotes-compatible offline note creation with stable local identities and immediate editor navigation.
- Added an `AppCompatEditText` Markdown source editor with asynchronous Markwon highlighting, supplemental QOwnNotes syntax highlighting, cursor preservation, and a mobile formatting toolbar.
- Added debounced Room draft persistence, lifecycle flushing, and application-scoped draft retention across activity recreation.
- Added Room schema version 3 with monotonically increasing local revisions so stale write responses cannot replace newer editor content.
- Added Notes API `POST` creation and `PUT` updates with quoted `If-Match` ETags, strict canonical-response validation, and explicit conflict, missing-note, and insufficient-storage failures.
- Added transactional canonical response application that adopts server IDs, ETags, and sanitized titles while preserving newer local content.
- Added API, migration, repository, formatting, highlighting, read-only, creation, editing, and recreation coverage.
- Fixed the editor focus and IME defect recorded on 2026-09-01. `AppCompatEditText` resolves its default style from the AppCompat `editTextStyle` theme attribute, which only exists in `Theme.AppCompat` descendants. The application theme derived from the framework `Theme.Material.Light.NoActionBar`, so `Widget.AppCompat.EditText` was never applied and the editor was left focusable but not focusable in touch mode, making a cursor and keyboard unreachable by tapping.
- Rebased the application theme on `Theme.AppCompat.DayNight.NoActionBar` so hosted AppCompat widgets get their intended styles and follow the system dark mode like the Compose theme.
- Set the interactive flags on `MarkdownEditText` itself so editing no longer depends on the hosting theme, and removed the inherited widget background because Compose supplies the editor surface.
- Added focus and keyboard activation when edit mode opens, focus restoration after formatting-toolbar actions, keyboard dismissal when the editor is released, and IME insets so the keyboard cannot cover the editor.
- Changed formatting actions to replace only the changed range instead of the whole document, preserving undo history, spans, and in-progress input-method composition.
- Applied the Compose surface text color to the hosted editor and rendered views so note text stays legible in dark mode.
- Added an adjustable note text size, requested during Phase 3 rather than planned. `A-` and `A+` controls on the note screen step through discrete `sp` sizes, apply to both the rendered note and the source editor, persist in `SharedPreferences`, survive process death, and carry accessibility descriptions. Rendered headings and code rescale without re-rendering because Markwon sizes them relative to the view.
- Added selecting and copying rendered note text, requested during Phase 3 rather than planned. The rendered view is selectable, and links stay tappable through a movement method that selects arbitrarily and only follows a link on a short, stationary touch. Device tests cover the long-press selection gesture, copying to the clipboard, and that the note still scrolls.
- Added finding text inside the open note, requested during Phase 3 rather than planned. A find bar on the note screen marks every match in the rendered note, marks and scrolls to the current one, reports the position in the matches, and wraps around at both ends. Matching is portable policy in `core`; only the span application and the offset lookup are Android. The find highlights are a private span type, so they can be removed again without disturbing the Markdown spans they are drawn over.
- Added creating a note from text another application shares, requested during Phase 3 rather than planned. The application is a share target for text, the sharing application's subject names the note, the shared text follows that name as the body, and the new note opens. A share arriving before any account exists waits and is explained on the onboarding screen. Which note the text becomes is portable policy in `core`; only reading the intent and delivering it into the running activity are Android. Verified on a physical Android 16 device for a cold start and for a share into the running application.
- Added undo and redo. Toolbar controls step through an editor-owned history that groups a burst of typing, an input method rewriting its composing region, and consecutive deletions into single steps, ends a group at a line break, and makes each formatting action its own step. The grouping rules are structural rather than time-based and are unit-tested on the JVM; only replaying a change into the widget is Android. Replaying clears the composing state and restarts the input method, and a history that no longer matches the text is discarded rather than replayed at a guessed position.

Every listed Phase 3 implementation task is complete, but the phase is not finished. The gaps below are open.

Known scope gaps:

- The undo history covers an editing session, not the note. It starts empty every time the editor opens, so leaving edit mode, rotating the device, or process death all discard it. Persisted editor text is unaffected. Decide whether a longer-lived history is worth serializing before this is called finished.
- Supplemental QOwnNotes highlighting runs on the main thread. `SupplementalSyntaxWatcher.afterTextChanged` scans the whole document with three regular expressions and rewrites its spans on every keystroke, so its cost grows with note length. The Markwon highlighting beside it is already pre-rendered off the main thread. This is the most likely cause of poor large-note typing latency and should be measured before being redesigned.
- Editor highlighting has not been audited against the syntax list in the Editing Mode section of this document. Coverage of Setext headings, fence language identifiers, images, and tables in the source view is assumed from the Markwon editor plugins rather than asserted by a test.
- Finding text works while reading a note but not while editing one. The editor shows the Markdown source, so it needs its own matching pass and its own way of moving the caret to a match, and the find bar would compete with the formatting toolbar and the keyboard for space. Decide whether the editor gets its own find affordance before this is called complete.
- The note text size is the first user preference, and it introduced the only preference storage in the project. Later settings should either reuse `AppSettings` or replace it deliberately; it should not be duplicated per feature. There is still no settings screen, so a preference without an obvious in-context control has nowhere to live.

Remaining verification:

- Verify canonical title sanitization and HTTP 412 conflict behavior against supported real Nextcloud and Notes server versions. Only MockWebServer coverage exists for these paths. Real-server `POST` creation and formatting-triggered `PUT` updates are confirmed.
- Measure editor responsiveness on representative large notes. No test or fixture in the repository uses a large document, so the acceptance criterion covering large-note editing is currently unverified rather than met.
- Validate non-Latin text, input-method composing text, and additional software keyboards on physical devices.
- Reconfirm typing on the OPPO CPH2653 running Android 16, where the original defect was reported. The fix is verified on an API 36 emulator only.

Resolved physical-device issue recorded on 2026-09-01:

- The user was not able to write text into a note because the editor never displayed a cursor or opened the software keyboard.
- The formatting toolbar did modify the note, and **Done** successfully synchronized that modified note to the real Nextcloud server.
- The device test suite did not catch this because it drove the editor with Espresso `replaceText`, which sets text directly and never requires input focus. It now taps the editor, asserts focus, and injects real key events with `typeText`, and a `markdown-android` test asserts the editor stays focusable in touch mode under a non-AppCompat theme.

### Phase 4: Synchronization Safety

- Implement reliable WorkManager scheduling and constraints.
- Separate retryable errors from user-action-required errors.
- Handle read-only notes.
- Handle deleted remote notes safely.
- Add conflict storage and conflict-resolution screens.
- Verify no local edit can be replaced by an older pull or push result.
- Add telemetry-free diagnostics suitable for user bug reports.

### Phase 5: Note Folders

Give Nextcloud accounts QOwnNotes-style folders through the Notes `category` attribute, as decided in the Note Folders section. This follows Phase 4 because moving a note between folders is a guarded remote update that needs the conflict infrastructure built there.

- Add category normalization and folder-tree derivation to `core`.
- Derive the per-account folder tree from the cached notes rather than from a new table.
- Add folder navigation to the note list, with the current scope, a note count per folder, and a subfolder-inclusion toggle.
- Persist the selected scope per account in `AppSettings` and fall back to the root when the folder no longer exists.
- Create notes in the current folder scope.
- Scope the search to the current subtree, allow searching the whole account, and match the category as well.
- Add an indexed, correctly escaped subtree query to the note DAO.
- Display the account's notes root name from `GET /settings`, read-only.
- Move a note to another folder with `If-Match`, adopting the canonical category and title.
- Add the `nestedCategories` backend capability.
- Keep the pull unfiltered and confirm that folder scoping cannot influence remote-deletion detection.

Explicitly out of scope for this phase: writing `notesPath`, creating durable empty folders, and renaming or deleting folders.

### Phase 6: Local-Only Folder Backend

- Add folder selection and persistent URI permissions.
- Index Markdown and text files.
- Create, open, edit, and externally refresh files.
- Add safe write and rename behavior.
- Add subfolder and wiki-link resolution.
- Add local images and attachment opening.
- Reuse the same note list, viewer, and editor screens.
- Add multiple configured note folders with QOwnNotes-style per-folder settings, which this backend can support and the Nextcloud backend cannot.

### Phase 7: Extended Features

- Interactive task checkboxes
- Images and attachments
- Sharing
- Widgets
- Multiple configured backends
- Folder renaming and deletion across every contained note
- Per-folder exclusion from the note list
- QOwnNotes title-to-filename options
- QOwnNotes encryption compatibility

### Phase 8: Shared Core and iOS

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
- Naming and content of a note made from shared text, including a missing, unusable, or repeated subject
- Category normalization and folder-tree derivation
- Folder scope fallback when the remembered folder no longer exists
- Undo grouping, including typing bursts, composing-region rewrites, deletions, and line breaks

### API Tests

Use a mock HTTP server to verify:

- Capability parsing
- Unknown JSON fields are ignored
- Missing fields from older minor versions are handled
- `If-None-Match` and HTTP 304 behavior
- `pruneBefore` behavior
- Chunk cursor handling
- Deletion detection only after the final chunk
- Canonical server responses after creation and update, including a sanitized category and a deduplicated title after a move
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
- Folder subtree queries with `%` and `_` in folder names

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
- Sharing text into the application, including a share that has to wait for an account and a share that must not be repeated by activity recreation
- Folder navigation, scope persistence, and creating a note inside the current folder
- Rendered-view to edit-mode transition
- Text selection, copying, and link tapping in the rendered note
- Cursor and selection stability during highlighting
- Undo and redo from the toolbar, including that a formatting action is a single step
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
- Creating a note in a nested category and moving a note between categories
- A folder disappearing on the server after its last note is moved or deleted

## Acceptance Criteria for the Initial Release

- A user can import a Nextcloud account through the installed Nextcloud Files application.
- Existing Nextcloud notes are visible after initial synchronization.
- Previously synchronized notes remain available after restarting without network access.
- Common QOwnNotes Markdown renders correctly in light and dark modes.
- Rendered note text can be selected and copied while its links stay tappable.
- Markdown source is highlighted while remaining visible and editable.
- Highlighting never changes the stored source text.
- Editing remains responsive on representative large notes.
- A new note receives the QOwnNotes dated title and matching initial heading.
- Text shared from another application becomes a new note that opens for correction.
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
- Nextcloud Notes folder scanning and category handling: <https://github.com/nextcloud/notes/blob/main/lib/Service/NotesService.php>
- Nextcloud Notes category filtering and chunking: <https://github.com/nextcloud/notes/blob/main/lib/Controller/Helper.php>
- Nextcloud Notes `notesPath` settings handling: <https://github.com/nextcloud/notes/blob/main/lib/Service/SettingsService.php>
- QOwnNotes note folders: <https://github.com/pbek/QOwnNotes/blob/main/src/entities/notefolder.h>
- Markwon: <https://noties.io/Markwon/>

## Prompt for the Next Session

Use this document as the authoritative planning baseline. Start by confirming that the new separate repository is the intended working directory. Then inspect its current state before making changes. Implement only the next incomplete phase, preserve all confirmed decisions above, and update this document when an implementation decision materially changes the plan.
