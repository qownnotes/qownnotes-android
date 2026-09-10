---
name: release-qownnotes-mobile
description: Use when preparing, publishing, verifying, or troubleshooting a QOwnNotes Mobile stable release, including version.properties, CHANGELOG.md, Fastlane changelogs, GitHub Release, signing, reproducibility, and F-Droid updates.
---

# Release QOwnNotes Mobile

Use [`docs/releasing.md`](../../../docs/releasing.md) as the authoritative checklist. Inspect the
current repository and automation before acting; do not rely only on this skill if the workflow has
changed.

## Safety Boundary

Distinguish these intents:

- **Prepare**: edit release files and run verification, but do not push `release`, create a tag, or
  publish anything.
- **Publish**: perform the prepared release and monitor it. Only publish when the user explicitly
  asks to create, publish, or ship the release. If intent is unclear, ask one short confirmation
  immediately before pushing `release`.
- **Verify or troubleshoot**: inspect existing tags, releases, assets, workflows, and F-Droid state
  without changing published artifacts.

Never create the stable tag or GitHub release manually. Never force-push `release`, replace a tag or
asset, reuse a publicly distributed version code, change the stable signing identity, expose
credentials, or claim a release/F-Droid build passed without checking the authoritative result.

## Establish Current State

Before editing:

1. Read `docs/releasing.md`, `.github/workflows/release.yml`, `version.properties`, the top and link
   definitions of `CHANGELOG.md`, `Justfile`, and the relevant Fastlane metadata.
2. Inspect the worktree, current branch, recent commits, remote branches, existing tags, latest
   GitHub release, and release workflow runs.
3. Determine the highest version code ever published. Include GitHub and F-Droid history; do not
   assume the current file contains the highest value.
4. Preserve unrelated worktree changes and stop if they conflict directly with release files.
5. Establish the requested version. Infer a semantic-version recommendation from the changes, but
   ask when the intended version or compatibility impact is ambiguous.

## Prepare

Make the smallest release-only edits:

1. Set both values in `version.properties`; the version name has no `v` prefix and the version code
   is a new monotonically increasing integer.
2. Move `CHANGELOG.md` entries from `Unreleased` into a dated version section, retain an empty
   `Unreleased` section, and update comparison links.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` with user-facing text of at
   most 500 characters.
4. Update store descriptions or deterministic screenshots only when user-visible store metadata is
   stale.
5. Check that tag format, GitHub APK filename, package ID, signing certificate, and F-Droid
   expectations remain compatible.

Run:

```sh
devenv shell -- just check build-fdroid
```

Run applicable device and real-server tests. If release signing is available, also run:

```sh
devenv shell -- just release
```

Report tests not executed. Inspect `git diff --check`, the complete diff, and repository status.
Commit or push only when requested.

## Publish

Before publication, verify the prepared commit is reviewed and on `main`, all required CI is green,
the worktree is clean, no matching tag/release exists, and `release` can fast-forward to it.

Push the exact reviewed commit, not an unverified working branch:

```sh
git push origin <release-commit>:release
```

Monitor the `Release` workflow until completion. Do not stop after the push or infer success from an
intermediate job.

## Verify

After success:

1. Verify `v<version>` and the GitHub release target the exact release commit.
2. Download APK, AAB, and `SHA256SUMS`; verify checksums.
3. Verify APK package/version information and the stable certificate digest with `apksigner`.
4. Run the F-Droid scanner against the APK and reject extra signing blocks or non-free findings.
5. Confirm the release workflow's unsigned comparison succeeded and smoke-test install or upgrade
   when possible.

If publication already produced a public release and a defect is found, prepare a new patch version
and higher version code. Never repair a release by mutating its tag or assets.

## F-Droid

For a routine release after initial inclusion, monitor automatic tag discovery instead of editing
`fdroiddata` immediately. Verify the resulting recipe/build uses the exact source, version code,
binary URL, and existing allowed signing key, and that build, scanner, and `check apk` pass.

Prepare a manual `fdroiddata` merge request only if automatic discovery fails or build metadata must
change. Keep the fork public and branch unprotected, validate the isolated recipe with `fdroid lint`,
`fdroid rewritemeta`, `fdroid build`, and `check apk`, and wait for the fork pipeline before opening
the upstream merge request.
