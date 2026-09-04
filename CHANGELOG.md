# Changelog

All notable changes to QOwnNotes Mobile are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/qownnotes/qownnotes-android/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/qownnotes/qownnotes-android/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/qownnotes/qownnotes-android/releases/tag/v0.2.0
[0.1.0]: https://github.com/qownnotes/qownnotes-android/releases/tag/v0.1.0
