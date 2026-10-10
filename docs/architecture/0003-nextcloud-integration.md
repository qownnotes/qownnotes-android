# ADR 0003: Nextcloud Integration

Status: Accepted for Phase 2

## Decision

The first remote backend uses the Nextcloud Android Single Sign-On library and accounts supplied by the installed Nextcloud Files application. It does not collect or store a user's normal password.

The backend adapter owns SSO, Notes REST API, and optional QOwnNotesAPI details. It exposes only `core` models and contracts to the rest of the application. Notes API 1.2 or newer is required so updates can use ETag-based optimistic concurrency. QOwnNotesAPI is an optional companion used for on-demand note versions and remote trash; its absence must not affect normal note synchronization.

The implementation uses Android Single Sign-On 1.3.4 and its Retrofit/Gson transport. The database stores only the SSO account name and public account metadata; credentials remain owned by the SSO library and Nextcloud Files app. Pulls use collection ETags, `pruneBefore`, and chunk cursors, and the local checkpoint advances only inside the transaction that applies a completed response.

Account management reads and writes the per-user `notesPath` and `fileSuffix` through Notes API
`/settings`. A folder change is serialized with synchronization, requires all local work to be
synchronized, and transactionally clears the old synchronized cache and pull checkpoint before the
remote setting can change, ensuring that interruption cannot apply an old checkpoint to the new
collection. A full pull follows a successful change. The canonical settings returned by the server
are authoritative. An extension change affects only files created afterward and does not invalidate
the collection checkpoint.

Nextcloud Deck is a second optional companion, exposed through the `NoteDeckBackend` contract.
The app detects Deck from the server capabilities instead of an opt-in setting.
The adapter lists boards, creates cards, and loads/edits linked cards through the Deck REST API
v1.1 on demand. Card links first offer the in-app editor or the external Deck app/browser, with
an optional remembered choice per account and a global reset in Settings. Card updates preserve
owner, order, type, archive state, and start date, and explicitly clear a removed due date. A fresh
server snapshot is compared before updating; Deck's card update controller does not enforce
`If-Match`, so this detects stale edits but cannot guarantee atomic protection against a
simultaneous external write. Failed saves keep input; reloading explicitly confirms discarding it.
The Deck browser loads readable boards and list-scoped cards, optionally merging archived cards
from that same list. It opens the shared card editor with a fresh server snapshot. Archiving uses
the REST archive endpoint after the same snapshot check and explicit confirmation, and never
modifies note content. Existing card links enter the ordinary editor insertion/undo path.
Deck data is
not cached in Room and Deck requests never read or write notes; only the card link inserted into
the note's Markdown enters the normal note-write path.

## Consequences

- Initial account setup requires Nextcloud Files for Android.
- Standalone Login Flow v2 remains a later backend/authentication option.
- Screen code remains independent of account and HTTP APIs.
- Changing the notes folder repoints Nextcloud Notes and every other client for that user; it does
  not move files. The account-management UI must state this consequence before saving.
- Note versions and remote trash remain network-backed, on-demand views rather than a second local
  source of truth. Restoring a version enters the normal guarded note-write path, while restoring a
  trashed note on the server is followed by a normal Notes API refresh.
