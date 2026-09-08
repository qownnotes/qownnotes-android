# Changelog

All notable changes to QOwnNotes Mobile are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.2] - 2026-09-08

### Changed

- Release packages no longer include the Android Gradle Plugin dependency-metadata signing block,
  allowing F-Droid to verify and publish the reproducible developer-signed APK.

## [0.3.1] - 2026-09-08

### Added

- Added GPL-3.0-only project licensing, F-Droid store metadata, privacy documentation, and an
  unsigned release-build check for F-Droid compatibility.
- Added deterministic F-Droid screenshot generation from fixed test data, with generated images
  retained as device-test CI artifacts.
- Added release-time signature-copy verification and deterministic full Git revision embedding to
  prepare the GitHub APK for reproducible F-Droid publication.

### Fixed

- The **Show category** note-list setting is now remembered independently for each account.

## [0.3.0] - 2026-09-07

### Added

- Conflicted notes can now load the current server version or preserve their local changes as a new
  note before loading the server version. Resolution fetches the note directly, so an unavailable
  server leaves the local conflict untouched.
- A per-account category selector filters the note list locally and offline. It defaults to
  **Undefined**, also offers **All categories**, remembers the selection, creates notes in the
  selected category, and hides QOwnNotes' internal `media` and `attachments` folder trees.
- Notes can be moved to the root, an existing category, or a new nested category from the note
  view's **Change category** menu action. Moves are saved offline and synchronized safely.
- Note edits are now checkpointed to local storage every five seconds during continuous typing, so
  an app crash cannot lose an indefinitely long in-memory editing session.
- An **About** menu item in the note list account menu shows the version number and short git
  commit hash. Both values link to the project's GitHub releases and commit page respectively.
- Renaming a note can optionally update the first Markdown heading to match the new file name.
  The rename dialog includes an **Update heading 1** checkbox that is enabled by default. The whole
  checkbox row is tappable, so the label is part of the target and screen readers announce one
  checked option instead of a box beside unrelated text.

### Changed

- Supplemental Markdown source highlighting now parses off the main thread and discards stale
  results, preventing whole-document regular-expression scans from blocking typing in large notes.
- Markdown images in the source editor are now highlighted as images rather than generic links.
- Remote images now load automatically in the note view. Local media images from Nextcloud notes
  are displayed using the Notes API attachment endpoint with SSO authentication. A **Load images**
  toggle in the note menu allows disabling image loading per note.

### Fixed

- Older asynchronous editor saves can no longer replace a newer draft after that newer draft has
  finished saving. Losing editor focus also checkpoints the current text locally.
- Opening and closing the editor without changing the note no longer leaves it pending or causes an
  unnecessary server update. The temporary edit reservation remains revision-guarded.
- The onscreen keyboard now closes reliably when leaving Markdown edit mode.
- Leaving edit mode no longer crashes. Editing is left after a note has been saved, and a coroutine
  resumes on whichever thread completed that save, so the keyboard and focus changes could reach
  the editor from a database thread and be rejected by the view hierarchy.
- Text shared from another application is now reliably opened after it becomes a note. Accepting
  the share cleared the state the accepting effect was keyed on, so the effect could be cancelled
  between writing the note and opening it, leaving the note in the list without showing it.
- Local `media/` images in Nextcloud notes now use the versioned Notes attachment endpoint instead
  of failing to load as red placeholders.
- Checkboxes in nested task lists can now be toggled in view mode; previously only top-level
  checkboxes responded to taps because the tap target was measured from the outermost list margin
  instead of the checkbox's actual position.

## [0.2.1] - 2026-09-04

### Fixed

- Stable GitHub releases now build directly with repository signing secrets, independently of the
  local Vaultwarden and SecretSpec signing workflow.
- Generated GitHub release notes no longer participate in source formatting checks.

## [0.2.0] - 2026-09-04

### Added

- Synchronized Nextcloud favorites with offline toggling, clickable stars, and favorites-first note
  and search-result ordering.
- A confirmed **Move to trash** action in the open note view.
- Automatic continuation for unordered, ordered, and task-list items when Return is pressed, with
  empty items ending the list.
- On-demand note version history and remote trash browsing through the optional Nextcloud
  QOwnNotesAPI app, including restoring versions and trashed notes.
- Continuous development APK publishing and versioned, signed GitHub release automation.

### Changed

- Switching accounts now opens an account chooser when more than two accounts are configured,
  while two-account switching remains a direct toggle.
- A newly added Nextcloud account now becomes the active account immediately.
- Unreachable-server errors in the note list and editor now offer a details dialog with
  local-server troubleshooting, sanitized exception text, and a copy action while confirming that
  local edits remain saved.
- Horizontal action bars now use recognizable icons with accessible descriptions; list and
  checkbox-list tools remain directly beside Undo and Redo in the editor.
- The note view now keeps Find and Edit as icon buttons and moves secondary actions into a compact
  three-dot menu.
- Existing note content is now syntax-highlighted immediately when edit mode opens, without waiting
  for the first text change.
- Continuous and stable release signing now validate missing GitHub secrets before installing the
  build environment, with documented commands for generating and configuring signing keys.
- Local development and release builds now retrieve their keystore and signing environment
  attachments from Vaultwarden and validate the variables through SecretSpec without retaining
  local copies.
- Newly created notes now open directly in edit mode with the cursor on a blank body line below the
  generated heading.
- The Markdown editor now scrolls as the cursor moves to keep the active line visible.
- Markdown source highlighting now explicitly covers headings, emphasis, strikethrough, lists,
  tasks, blockquotes, inline and fenced code, links, images, tables, wiki links, frontmatter, and
  HTML comments.
- Development and release build and deployment recipes now target their corresponding application
  variants.

### Fixed

- Pressing Return on an automatically opened empty list or checklist item now removes its marker
  even when the keyboard resets the caret after inserting the preceding line break.

## [0.1.0] - 2026-09-03

### Added

- Android 9 and newer support with a native Kotlin, Compose Material 3 interface and light and dark
  themes.
- Nextcloud account import through the Nextcloud Files Android app and Android Single Sign-On,
  including multiple accounts, account switching, reconnect handling, and local account-data
  removal.
- Nextcloud Notes API 1.2 or newer support with capability negotiation, incremental and chunked
  pulls, collection ETags, offline caching, and explicit authentication, permission, protocol,
  storage, conflict, and retryable error handling.
- Offline-first note creation and editing with stable local identities, debounced Room persistence,
  ETag-protected updates, canonical server response handling, and protection against stale network
  responses replacing newer local edits.
- QOwnNotes-compatible dated note names and initial Markdown headings.
- Note renaming with server filename sanitization support.
- Long-press multi-selection in the note list and durable deletion through the Nextcloud trash bin.
- Creation of a new note from text shared by another Android application, including shares received
  during onboarding or while the application is already open.
- Account-scoped offline search across note titles and content.
- Rendered Markdown with headings, lists, tables, strikethrough, task lists including the QOwnNotes
  indeterminate state, blockquotes, links, images, inline code, and syntax-highlighted fenced code.
- QOwnNotes wiki links, aliases, heading links, qualified paths, and legacy `note://` links with
  in-app navigation and broken-link styling.
- Fail-closed handling for raw HTML, unsafe links, remote images, private network addresses,
  oversized image responses, filesystem access, and QOwnNotes encrypted blocks.
- Selectable rendered note text with copy support while preserving link taps and scrolling.
- In-note text search with highlighted matches, next and previous navigation, and wraparound.
- Adjustable persisted note text size for both rendered and source views.
- Markdown source editing with asynchronous Markwon highlighting, supplemental QOwnNotes syntax,
  cursor and selection preservation, mobile formatting actions, and toolbar undo and redo.
- Read-only note presentation that prevents editing and renaming.
- Separate installable production and development applications named `QOwnNotes` and
  `QOwnNotes Dev`.
- JVM, MockWebServer, Room, Markdown widget, Compose, migration, and Android device test coverage.

[Unreleased]: https://github.com/qownnotes/qownnotes-android/compare/v0.3.2...HEAD
[0.3.2]: https://github.com/qownnotes/qownnotes-android/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/qownnotes/qownnotes-android/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/qownnotes/qownnotes-android/releases/tag/v0.3.0
[0.2.1]: https://github.com/qownnotes/qownnotes-android/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/qownnotes/qownnotes-android/releases/tag/v0.2.0
[0.1.0]: https://github.com/qownnotes/qownnotes-android/releases/tag/v0.1.0
