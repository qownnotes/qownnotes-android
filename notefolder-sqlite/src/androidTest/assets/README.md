# Desktop compatibility fixture

`desktop-16-seeded.sqlite` comes from `qownnotes-nextcloud/tests/fixtures/notes-sqlite/`:
its schema was created by QOwnNotes Desktop 26.10.2; test rows are synthetic. Tag link names
are Desktop base names without suffixes. `ClientCompatibilityCaptureTest` uses the production
Android reader/writer, preserves the non-tag tables, and can export its result with the
instrumentation argument `captureTagFixture=true`. No user data or credentials are included.
