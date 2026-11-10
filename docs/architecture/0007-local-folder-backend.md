# ADR 0007: Local-Folder Backend

Status: Accepted

## Context

Some QOwnNotes users do not use Nextcloud. They synchronize the desktop note folder with their
phone through Syncthing or another file synchronizer and want to use the mobile app on that folder
directly ([#47](https://github.com/qownnotes/qownnotes-android/issues/47)). Nextcloud remains the
primary way to use the app. A local folder is an additional account type that must not change how
Nextcloud accounts behave.

The rest of the application already depends on backend contracts rather than on Nextcloud (ADR
0001), and Room is the offline source of truth (ADR 0002). Optional features such as versions,
trash, media uploads, tags, Deck, and sharing are separate backend interfaces.

## Decision

- A local folder is an account. `Account.localFolderUri` holds the granted folder, and local
  account IDs start with `local-folder-` so code that only knows an ID, such as the WorkManager
  scheduler, can tell the account apart. Room schema version 11 adds the nullable column; existing
  accounts remain Nextcloud accounts.
- A new `backend-localfolder` module implements `NoteBackend`, `NoteMediaBackend`,
  `NoteTagFileBackend`, and the trash part of `NoteArchiveBackend`. `ApplicationComponent` routes
  each call by account, so the synchronization engine, pull store, push store, conflict handling,
  and screens are shared with Nextcloud unchanged.
- The folder plays the part of the server. Files are the backend source of truth and Room remains
  the source of truth for the screens. Changes reach the folder through the existing push queue,
  and changes made by other programs arrive through pulls.
- Access uses the Storage Access Framework. The user selects a folder in the system picker; the app
  takes a persistable read/write grant and needs no broad storage permission. A lost grant is
  reported as an authorization problem, and **Reconnect** opens the picker for the same folder.
- The layout matches a QOwnNotes desktop note folder. Subfolders are categories, a note's file name
  without extension is its name, `.md` and `.txt` files are notes, and new notes use `.md`. Hidden
  entries and the top-level `media` and `attachments` folders are not scanned for notes.
- A note's remote ID is a stable 63-bit hash of its relative path. A rename made in the app returns
  the new ID, which the push store adopts. A rename made elsewhere looks like a delete and a create,
  as it does for QOwnNotes desktop.
- A note's ETag is a hash of its bytes. An update reads the file first and refuses to write when it
  no longer matches, so a change delivered by a file synchronizer becomes an explicit conflict.
  Renames and moves write the new file before deleting the old one, so an interruption can leave a
  duplicate but never lose a note.
- A pull lists the folder and reads only files whose size, modification time, or writability
  changed since the last pull the database applied in this process. The pull token stored as the
  collection ETag identifies that pull, so a pull that was not applied, or a new process, reads
  everything again. A listing identical to the applied one is reported as not modified.
- Files that are not valid UTF-8 or that the provider reports as not writable are read-only.
- Deleting moves the note into an app-private trash, because Android has no trash for shared
  folders. The trash dialog lists and restores those notes. Favorites, which have no place in the
  folder, are kept in app-private storage by path. Both are removed with the account.
- Images and attachments are read from the folder relative to the note, confined to the top-level
  `media` and `attachments` folders. Uploaded images and recordings are written there with unique
  names.
- `notes.sqlite` is transferred with content-hash ETags through the existing tag synchronizer.
- Synchronization of a local folder does not require a network connection.
- Server-only features are hidden for local folders: note versions, sharing, Nextcloud Deck, and
  server note settings. Wording that refers to a server has local-folder variants.

## Consequences

- Nextcloud accounts keep their behavior, schema, and protocol. Local folders reuse the offline
  queue, conflict resolution, folder tree, search, tags, bookmarks, widgets, and editor.
- Storage Access Framework writes are not atomic. The conflict check happens right before writing,
  so a change written by another program in that short window can still be overwritten. File
  synchronizers usually keep their own conflict copies, which appear as separate notes.
- Shared storage on Android ignores letter case, so a rename that only changes letter case keeps
  the current file name.
- Changes made by other programs are picked up by refreshes, not by file watchers. The existing
  refresh triggers apply: opening the app, pull to refresh, and synchronization after edits.
- Uninstalling the app or removing the account discards its private trash and favorites. Note files
  in the folder are never deleted by removing an account.
