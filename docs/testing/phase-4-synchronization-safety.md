# Phase 4 Synchronization Safety

Phase 4 implementation is in progress. This checklist separates automated guarantees from behavior
that must be exercised with Android background scheduling and a real Nextcloud server.

Do not record server addresses, account names, credentials, authorization tokens, or private note
contents. Record only public software versions and non-sensitive results.

## Automated Coverage

The automated suites verify:

- Late push failures do not change the state or error of a newer local revision.
- Late successful updates do not undo persisted deletion intent.
- Conflict resolution cannot replace a newer local revision.
- Work requests carry an account identity, require network connectivity, and preserve the edit
  synchronization delay.
- Retryable failures request WorkManager backoff, while authentication failures and uncertain
  creates do not retry automatically.
- Local deletion intent is visible in Room before synchronization is scheduled.
- Dirty notes omitted by a completed pull enter an explicit remote-missing state without losing
  local content and can be recreated or discarded through revision-guarded transactions.
- A note that becomes read-only while protected local fields differ from their synchronized base
  keeps those changes and can preserve them as a writable copy before adopting the server version.
- Favorite-only changes remain pending when a note is read-only because the Notes API permits that
  attribute to be updated independently.
- Sanitized synchronization failures are retained in a bounded diagnostic history and the generated
  report omits internal account identities while including non-sensitive environment information.
- Conflicts retain the exact remote version while the note retains its local and common-base
  versions. Resolution validates both the local revision and reviewed remote ETag, and later pulls
  refresh the remote side without discarding local changes.
- Three-way merge combines independent scalar and contiguous line changes, while overlapping edits
  remain unresolved and cannot silently replace either version.

The complete connected-device suite passed on the OPPO CPH2653 running Android 16 on 2026-09-17:
5 backend, 57 data, 38 Markdown, and 88 application tests (188 total).

Run host checks with:

```sh
devenv shell -- just check
```

With an emulator or device connected, run:

```sh
devenv shell -- just device-test
```

## Durable Retry

Record the device, Android version, app commit, Nextcloud server version, Notes app version, Notes
API version, Nextcloud Files version, date, and result for each case.

- Edit an existing note while offline, close the editor, and verify the local edit remains visible.
- Terminate the application process before restoring connectivity.
- Restore connectivity without reopening the note and verify WorkManager eventually uploads the
  same local revision.
- Repeat with several quick edits and verify only the final local revision reaches the server.
- Queue changes in two accounts and verify each account synchronizes independently.
- Remove one account while work is pending and verify its work is cancelled without affecting the
  other account.

## Failure Classification

- Temporarily interrupt connectivity and verify the operation retries with backoff.
- Revoke Nextcloud Files authorization and verify synchronization stops with a reconnect action.
- Remove the Files account and verify synchronization stops without discarding cached notes.
- Cause a permission or storage failure and verify it does not retry indefinitely.
- Simulate a lost response to initial note creation and verify the local note remains failed until
  explicit retry, rather than being posted repeatedly.

## Remaining Phase 4 Checks

These checks require real-server evidence:

- A shared read-only note becoming read-only while a local edit is pending.
- A server-side deletion while the local note is unchanged.
- A server-side deletion while a local edit is pending.

- Review local, server, and common-base versions after restarting the application, then verify an
  independent three-way merge reaches the server and overlapping changes remain blocked.
- Confirm the secret-redacted diagnostic report remains available after terminating and restarting
  the application process, can be cleared locally, and is never sent without explicit user action.
