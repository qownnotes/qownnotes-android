# Releasing QOwnNotes Mobile

This checklist covers stable GitHub releases and the corresponding F-Droid update. The release
workflow is triggered by a push to the `release` branch. It builds and signs the APK and AAB,
verifies that the signed APK is reproducible from the unsigned F-Droid build, creates the immutable
`v<version>` tag, and publishes the GitHub release.

Do not create the stable tag or GitHub release manually. Do not replace a published tag or release
asset. Android updates and F-Droid reproducible builds depend on immutable source and artifacts and
on continued use of the existing release signing key.

## 1. Choose The Version

Use semantic versioning based on user-visible behavior and compatibility:

- Patch: compatible bug fixes, packaging fixes, and small compatible improvements.
- Minor: new backward-compatible features.
- Major: incompatible behavior, storage, protocol, or support changes.

Fetch the remote tags and inspect the latest GitHub and F-Droid versions. In
[`version.properties`](../version.properties):

- Set `VERSION_NAME` to the new public version without a `v` prefix.
- Increment `VERSION_CODE` to an integer greater than every version code ever published for
  `org.qownnotes.mobile`. Never reuse a version code that reached any public distribution channel,
  including a withdrawn release.

`ANDROID_VERSION_CODE` is only a CI override for continuous development builds. Stable releases
always use the committed `VERSION_CODE`.

## 2. Prepare Release Notes And Store Metadata

Update [`CHANGELOG.md`](../CHANGELOG.md):

1. Keep an empty `## [Unreleased]` section for future work.
2. Move all release entries into `## [<version>] - YYYY-MM-DD`.
3. Add a new `[Unreleased]` comparison link from `v<version>` to `HEAD`.
4. Add the new version comparison link from the previous tag to `v<version>`.

Create
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` with concise user-facing release
notes. The file is required even for a packaging-only release and must contain no more than 500
characters.

Update the Fastlane title, descriptions, icon, or screenshots when the release changes what users
see in the store listing. Deterministic screenshots can be regenerated on a connected device with:

```sh
devenv shell -- just update-fdroid-screenshots
```

## 3. Verify The Release Candidate

Prepare the release on a normal branch and review the complete diff. Confirm that no credentials,
keystores, local SDK paths, or generated build output are included.

Run the host checks and the unsigned build that F-Droid will reproduce:

```sh
devenv shell -- just check build-fdroid
```

Run applicable device tests when an emulator or device is available:

```sh
devenv shell -- just device-test
```

For a local signed rehearsal, with the release signing identity available through Vaultwarden or
the documented signing environment, run:

```sh
devenv shell -- just release
```

Install the release build on a representative device when the changes affect startup, account
access, synchronization, database migration, Markdown interaction, or Android integration. Perform
any required real Nextcloud compatibility checks and record checks that could not be run.

Commit the version bump, changelog section, store changelog, and any intentional metadata changes
together. Merge them to `main` only after its checks pass.

## 4. Publish The GitHub Release

Before publishing, confirm all of the following:

- The intended release commit is on `main` and the worktree is clean.
- `version.properties`, the `CHANGELOG.md` heading, and the Fastlane changelog agree.
- `v<version>` and its GitHub release do not already exist.
- The `release` branch can be fast-forwarded to the intended commit.
- The stable GitHub signing secrets still refer to the existing release key.

Publishing is the push below, where `<release-commit>` is the reviewed commit on `main`:

```sh
git push origin <release-commit>:release
```

Do not force-push the `release` branch. Monitor the `Release` GitHub Actions workflow through
completion. It must pass every step before the release is considered published.

The workflow performs these release-critical operations:

- Validates `VERSION_NAME`, `VERSION_CODE`, and the Fastlane changelog.
- Rejects an existing tag or release.
- Runs formatting, tests, release lint, dependency-license checks, and F-Droid metadata checks.
- Builds the unsigned F-Droid APK and the signed release APK and AAB.
- Uses `apksigcopier compare --unsigned` to verify that signing is the only APK difference.
- Publishes `QOwnNotes-Mobile-<version>.apk`, `QOwnNotes-Mobile-<version>.aab`, and `SHA256SUMS`.
- Creates `v<version>` at the exact release commit and publishes the GitHub release.

## 5. Verify The Published Release

After the workflow succeeds:

1. Confirm the tag and GitHub release both target `<release-commit>`.
2. Download all release assets and verify them against `SHA256SUMS`.
3. Verify the APK package, version name, version code, and signing certificate with Android build
   tools. The certificate SHA-256 digest must remain
   `c1e358ca679e6198d4116eca81456cf9527f44942244c033967b5e637db187aa`, matching the previously
   published stable APKs and F-Droid's `AllowedAPKSigningKeys` value.
4. Run the F-Droid APK scanner and confirm that it reports no non-free classes or extra signing
   blocks. In particular, the Android dependency-metadata signing block must remain absent.
5. Install or upgrade from the previous stable APK and perform a short smoke test.

If publication fails before a tag and release exist, fix the cause and push a corrected commit to
`release`. If a release was published, do not alter it: prepare a new patch version with a new,
higher version code.

## 6. Follow The F-Droid Update

The accepted `fdroiddata` recipe uses the release tags, `version.properties`, the GitHub APK naming
scheme, and the stable signing certificate. For an ordinary release, do not immediately submit a
manual `fdroiddata` edit. F-Droid's update automation should discover `v<version>`, add a build for
the new version code, rebuild the tag, copy the developer signature, and verify the result against
the GitHub APK.

After publishing:

1. Confirm the initial QOwnNotes inclusion merge request has been merged before expecting automatic
   updates.
2. Monitor F-Droid update checks and `fdroiddata` for `org.qownnotes.mobile`.
3. Confirm the generated build uses the exact immutable tag or commit, new version name and code,
   existing `Binaries` URL pattern, and existing `AllowedAPKSigningKeys` certificate digest.
4. Confirm the F-Droid build, scanner, and APK reproducibility checks pass.
5. After publication, verify the new version and changelog in the F-Droid repository and test an
   upgrade from the prior F-Droid version.

Only prepare a manual `fdroiddata` update when automatic discovery fails or the recipe itself needs
to change. Validate such a change in a public, unprotected fork branch with `fdroid lint`,
`fdroid rewritemeta`, `fdroid build`, and `check apk` before opening an upstream merge request.

Changing the GitHub asset name, tag format, package ID, signing key, build requirements, or APK
reproducibility can require a coordinated `fdroiddata` recipe change. Treat those as release
blockers rather than discovering them after publication.
