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

These checks become required when their corresponding recovery UI is implemented:

- A shared read-only note becoming read-only while a local edit is pending.
- A server-side deletion while the local note is unchanged.
- A server-side deletion while a local edit is pending.
- Side-by-side conflict review and three-way merge behavior.
- A complete, secret-redacted diagnostic report after process restart.
