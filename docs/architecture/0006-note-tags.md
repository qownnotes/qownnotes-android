# ADR 0006: Note Tags From notes.sqlite

Status: Accepted

## Context

QOwnNotes desktop stores tags outside the Markdown files, in a SQLite database named
`notes.sqlite` at the root of each note folder. When the desktop note folder is the Nextcloud Notes
folder, the Nextcloud desktop client syncs this file like any other file. The Notes API does not
expose it, so mobile has to read and write it through WebDAV.

The desktop schema (database version 16) has a `tag` table (`id`, `name COLLATE NOCASE`,
`parent_id`, `priority`, `color`, `dark_color`, `created`, `updated`) and a `noteTagLink` table
(`tag_id`, `note_file_name`, `note_sub_folder_path`, `created`, `stale_date`). Links identify a note
by its file name without the suffix and its subfolder path, not by an ID. The desktop marks links to
missing notes as stale and deletes them after 10 days.

## Decision

- `notes.sqlite` is the only tag store. Mobile never creates it. If it is missing, tagging is off
  and the app explains why.
- A new `notefolder-sqlite` module reads and writes a private copy of the file with Android's
  SQLite. It opens the copy with `NO_LOCALIZED_COLLATORS`, so no `android_metadata` table is added,
  and with an explicit `DELETE` journal mode. It rejects files that fail `PRAGMA quick_check`, that
  use WAL, that lack the required columns, or that are older than version 15. Files newer than
  version 16 are read-only. Writes change only the tag tables and follow desktop conventions: tag
  lookup uses `COLLATE NOCASE`, new rows get default timestamps, ancestor tags have `updated`
  refreshed, and relinked rows get their `stale_date` cleared.
- The account's tags are mirrored in Room (`note_tags`, `note_tag_links`), with every pending
  operation already applied, so screens render offline from Room. User changes are stored as
  ordered operations (`pending_tag_operations`): link or unlink a tag path, or relink a note to a
  new name. Operations refer to tags by name path, never by file-local ID.
- Synchronization runs after the note push. It sends a conditional `GET` with the stored WebDAV
  ETag, so an unchanged file costs one small request. Pending operations are replayed on the
  newest file and uploaded with `If-Match`. On HTTP 412 the file is downloaded again and the
  operations replayed, up to three attempts. If replaying changes nothing, nothing is uploaded.
  Operations are removed in the same transaction that installs the new mirror, and only once they
  are known to be in the server file.
- A note's tag key is the name the server last confirmed (`lastSyncedTitle`/`lastSyncedCategory`),
  or its local name before the first upload. A SQLite trigger on `notes` detects any change of
  that key for a note that has links. The same statement moves the mirror links and queues a
  relink operation, so renames from any code path keep their tags.
- Deleting a note does not remove its links. The desktop's 10-day stale-link cleanup handles
  them, and a note restored from the trash within that window keeps its tags. This differs from
  the desktop, which removes links when it deletes a note itself.
- Tag failures never fail note synchronization. They are recorded on the account's tag state and
  retried on the next synchronization.

## Consequences

- An unchanged file costs one conditional request per synchronization. A changed file costs one
  download plus a Room replacement, which is bounded at 32 MiB.
- Mobile cannot overwrite a newer server version without first replaying its operations on it.
  QOwnNotes desktop and the Nextcloud desktop client can still produce a sync-conflict copy if both
  sides write within seconds of each other. Mobile does not try to merge those copies.
- Tag management that goes beyond assigning tags (renaming, deleting, reparenting, colors,
  priority) is left for later. The operation model can be extended for it.
- The local-folder backend can reuse `notefolder-sqlite` with direct file access instead of
  WebDAV.
