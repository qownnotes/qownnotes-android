# Phase 2 Nextcloud Compatibility

Phase 2 uses two complementary verification paths:

- Authenticated manual testing verifies Android account selection, Nextcloud Files SSO, and a
  real server.
- MockWebServer and scripted device tests verify protocol edge cases without storing credentials
  in the repository or CI.

## Verified Physical Device

Verified on 2026-09-01 at commit `f944f8f` plus the Phase 2 completion worktree:

| Component | Version or result |
| --- | --- |
| Device | OPPO CPH2653 |
| Android | 16 |
| Nextcloud Android SSO library | 1.3.4 |
| Existing Nextcloud Files account selection | Passed |
| SSO authorization | Passed |
| Notes capability negotiation | Passed |
| Initial full pull and cached note display | Passed |
| Note creation through `POST /notes` | Passed |
| Note update through ETag-protected `PUT /notes/{id}` | Passed using formatting controls |
| Offline cache, reconnect, switching, and local removal flows | Passed with scripted device tests |

The server, Notes app, and Nextcloud Files version numbers were not recorded during the initial
manual test. Record them before declaring a broad compatibility matrix.

## Automated Coverage

`NotesApiMockServerTest` verifies:

- Initial and incremental query parameters and headers.
- HTTP 304 handling.
- Multi-page chunk traversal and cursor validation.
- Forward-compatible and malformed JSON handling.
- HTTP error classification and interrupted chunked pulls.

Android instrumentation verifies:

- Database migration and transactional pull behavior.
- Initial onboarding and full-pull coordination.
- Cached notes after an offline activity restart.
- Reauthorization with the previous pull checkpoint.
- Account-scoped switching and local-data removal.

The CI `device-test` job runs all module instrumentation tests on an API 36 emulator. Actual SSO
remains a manual test because it requires an installed and authenticated Nextcloud Files app.

## Manual Server Checklist

Record the server, Notes app, Nextcloud Files, device, Android version, date, and commit for each
tested combination.

1. Import an existing account and approve SSO access.
2. Confirm Notes API 1.2 or newer is accepted and an unsupported server is rejected.
3. Confirm the initial pull displays server notes and preserves their categories and read-only
   state.
4. Refresh without server changes and confirm cached notes remain unchanged.
5. Change or add a server note, refresh, and confirm the incremental result appears.
6. Test an account with more than 200 notes and confirm every chunk appears.
7. Interrupt a chunked pull and confirm no partial checkpoint is committed.
8. Restart offline and confirm cached search, rendering, and account switching work.
9. Revoke authorization, reconnect the same account, and confirm its cache and checkpoint remain.
10. Remove local data and confirm neither the Nextcloud Files account nor server notes are deleted.
