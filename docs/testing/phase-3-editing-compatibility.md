# Phase 3 Editing Compatibility

Phase 3 implementation and automated coverage are complete. Closing the phase requires real-server
and physical-device evidence for behavior that MockWebServer and an emulator cannot establish.

Do not put server addresses, account names, credentials, note contents, or authorization tokens in
this document. Record only public software versions and non-sensitive results.

## Automated Coverage

The automated suites verify:

- Offline creation and editing with stable local identities, persisted revisions, and explicit
  retry after an uncertain initial upload without creating a duplicate local note.
- Debounced, periodic, focus-loss, lifecycle, and recreation draft checkpoints.
- Highlighting that preserves source text, selection, and stale-result safety.
- Formatting, list continuation, session-scoped undo and redo, and source-text finding.
- Notes API `POST`, ETag-protected `PUT`, canonical response adoption, HTTP 404 and 412
  classification, and rejection of empty, incomplete, or wrong-note canonical responses.
- Transactional application of canonical writes and protection against stale write responses.
- Application-level canonical-title adoption, conflict-state transitions that retain local text,
  and failed conflict resolution that leaves the original note unchanged.

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

Create one table for each tested combination.

### OPPO CPH2653, Android 16

| Component | Version or result |
| --- | --- |
| Date | 2026-09-14 |
| Git commit | Working tree based on `84f5707` |
| Device | OnePlus/OPPO CPH2653 |
| Android | 16 / API 36 |
| Software keyboard | SwiftKey 9.13.14.5 and Gboard 18.1.3 |
| Nextcloud server | Version not recorded |
| Nextcloud Notes app | Version not recorded |
| Reported Notes API | Version not recorded |
| Nextcloud Files app | 34.1.1 |
| Canonical title | Pass: `Phase 3 canonical collision` became `Phase 3 canonical collision (2)` |
| HTTP 412 preservation | Pass: local text remained intact; preserve-local-copy resolution passed |
| Input methods | Pass with SwiftKey and Gboard |
| Representative large note | Pass at approximately 100 KiB; see measurements below |

The complete connected-device suite passed on this device at commit `7437652` on 2026-09-16: 5
backend tests, 38 Markdown tests, 40 data tests, and 80 application tests (163 total).

The server-version fields remain required before this environment can establish a supported server
combination. The adopt-server-only and server-unavailable conflict-resolution paths remain to be
run against the real server; automated application tests cover both paths.

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
frontmatter, HTML comments, and fenced code. Source highlighting is intentionally omitted above 64
KiB after physical testing demonstrated that Android's full-document span updates made text input
unusable. The rendered view still exercises Markwon, and smaller editor fixtures exercise both
Markwon and supplemental source highlighting.

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

Physical results on the OPPO CPH2653:

| Measurement | 100 KiB | 1 MiB stress |
| --- | --- | --- |
| Open note to rendered content | Pass; visible startup delay remains | Not run |
| Tap Edit to visible caret | Pass; visible startup delay remains | Not run |
| Continuous typing response | Pass | Not run |
| Highlight settling after typing | Not applicable: source highlighting is omitted above 64 KiB | Not run |
| Find first and last match | Pass | Not run |
| Formatting and undo | Pass | Not run |
| Fast scroll beginning to end | Pass with inertial swipe and scroll rail | Not run |
| Rotate and restore | Pass | Not run |

The first 106 KiB diagnostic fixture concentrated Markdown constructs across roughly 10,000 short
lines. It triggered input-dispatch ANRs and is retained as stress evidence rather than treated as a
representative note. A Perfetto trace showed a document-height editor between 520,000 and 564,000
pixels, with one frame taking 4.58 seconds. The bounded editor and large-source fallback reduced
the measured worst input frame to 1.20 seconds before software drawing was enabled; the final build
passed the manual responsiveness and exact-text checks. The trace contains device runtime data and
is intentionally not committed.

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
