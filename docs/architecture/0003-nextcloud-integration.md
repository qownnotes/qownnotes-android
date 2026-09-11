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

## Consequences

- Initial account setup requires Nextcloud Files for Android.
- Standalone Login Flow v2 remains a later backend/authentication option.
- Screen code remains independent of account and HTTP APIs.
- Changing the notes folder repoints Nextcloud Notes and every other client for that user; it does
  not move files. The account-management UI must state this consequence before saving.
- Note versions and remote trash remain network-backed, on-demand views rather than a second local
  source of truth. Restoring a version enters the normal guarded note-write path, while restoring a
  trashed note on the server is followed by a normal Notes API refresh.
