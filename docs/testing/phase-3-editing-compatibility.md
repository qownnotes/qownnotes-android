# Phase 3 Editing Compatibility

Phase 3 implementation and automated coverage are complete. Closing the phase requires real-server
and physical-device evidence for behavior that MockWebServer and an emulator cannot establish.

Do not put server addresses, account names, credentials, note contents, or authorization tokens in
this document. Record only public software versions and non-sensitive results.

## Automated Coverage

The automated suites verify:

- Offline creation and editing with stable local identities and persisted revisions.
- Debounced, periodic, focus-loss, lifecycle, and recreation draft checkpoints.
- Highlighting that preserves source text, selection, and stale-result safety.
- Formatting, list continuation, session-scoped undo and redo, and source-text finding.
- Notes API `POST`, ETag-protected `PUT`, canonical response adoption, HTTP 404 and 412
  classification, and rejection of empty, incomplete, or wrong-note canonical responses.
- Transactional application of canonical writes and protection against stale write responses.
- Application-level canonical-title adoption and conflict-state transitions that retain local text.

Run the host-side checks with:

```sh
devenv shell -- just check
```

With an emulator or device connected, run:

```sh
devenv shell -- just device-test
```

Compilation of Android tests does not count as device verification.

## Environment Record

Create one table for each tested combination:

| Component | Version or result |
| --- | --- |
| Date | |
| Git commit | |
| Device | |
| Android | |
| Software keyboard | |
| Nextcloud server | |
| Nextcloud Notes app | |
| Reported Notes API | |
| Nextcloud Files app | |
| Canonical title | Not run |
| HTTP 412 preservation | Not run |
| Input methods | Not run |
| Representative large note | Not run |

## Canonical Title

Use a disposable category and notes that contain no private information.

1. Synchronize a server note with a known title into the mobile app.
2. Create or rename another note in the same category to trigger the server's filename collision or
   title-sanitization behavior.
3. Finish editing and wait for synchronization.
4. Confirm the mobile title changes to exactly the canonical title returned by the server.
5. Confirm the Markdown content, category, favorite state, and stable local note identity are not
   replaced by values from another note.
6. Refresh again and confirm the canonical title remains stable and no duplicate upload appears.

Record the requested title, resulting title, pass/fail result, and server versions without recording
the server address or account identity.

## HTTP 412 Conflict

1. Synchronize a disposable note and open it on the mobile device without refreshing again.
2. Change and save the same note through another client so the server ETag advances.
3. Make a different local edit on mobile and finish editing.
4. Confirm synchronization reports a conflict rather than overwriting either version.
5. Confirm the complete local text remains visible and editable on the device.
6. Resolve the conflict by adopting the server version and confirm the server text is loaded.
7. Repeat the scenario and choose to preserve the local version first.
8. Confirm a new local note contains the complete local text and the original note adopts the
   server version.
9. Repeat the resolution while the server is unavailable and confirm the conflict remains unchanged.

## Input Methods

Run this section on the OPPO CPH2653 running Android 16, where the original focus defect was
reported, and on one additional physical device when available. Use at least two software
keyboards where practical.

1. Tap Edit and confirm the cursor and software keyboard appear without another tap.
2. Type Latin text continuously, use autocorrection, and accept a candidate replacement.
3. Type a non-Latin script through an input method that uses an active composing region.
4. Insert accented characters, emoji, and a supplementary Unicode character.
5. Replace a selection and confirm the caret remains at the replacement boundary.
6. Apply formatting while composition is active and confirm surrounding text is unchanged.
7. Exercise undo and redo after ordinary typing, candidate replacement, deletion, and formatting.
8. Open source Find, move through matches, close it, and confirm focus returns to the editor.
9. Press Return in unordered, ordered, and task lists, including leaving an empty item.
10. Rotate, background and restore the app, finish editing, and reopen the note.
11. Confirm persisted Markdown is exact and no composing text, cursor movement, or toolbar action
    introduced or removed characters.

## Large-Note Responsiveness

The phase gate is a representative 100 KiB mixed-Markdown note. A 1 MiB note is a stress diagnostic,
not a release blocker unless ordinary editing corrupts or loses text.

The fixture should repeat prose, headings, emphasis, links, task lists, tables, wiki links, YAML
frontmatter, HTML comments, and fenced code so both Markwon and supplemental highlighting run.

For each device and fixture size, record:

| Measurement | 100 KiB | 1 MiB stress |
| --- | --- | --- |
| Open note to rendered content | | |
| Tap Edit to visible caret | | |
| Continuous typing response | | |
| Highlight settling after typing | | |
| Find first and last match | | |
| Formatting and undo | | |
| Fast scroll beginning to end | | |
| Rotate and restore | | |

The representative note passes when:

- Every injected character appears once and in order, with no visible repeated typing stalls.
- The cursor and selection do not jump when asynchronous highlighting completes.
- Scrolling, source Find, formatting, undo, and redo remain usable.
- Backgrounding, rotation, and reopening preserve the latest persisted checkpoint.
- Memory pressure does not crash the app or replace the note with an older revision.

If responsiveness fails, capture a Perfetto or Android Studio system trace before changing the
editor. Optimize the measured bottleneck first, beginning with highlighting coalescing and stale
work cancellation before changing the editor layout.

## Completion

After every required row has passed:

1. Add the non-sensitive environment tables and results to this document.
2. Link any failures to an issue and leave the affected verification item open.
3. Run `just check` and `just device-test` at the exact commit being recorded.
4. Update `mobile-app-plan.md` from **Phase 3 in progress** to **Phase 3 complete**.
