# QOwnNotes Mobile

QOwnNotes Mobile is an Android-first, offline-capable Markdown notes application. It preserves key
QOwnNotes behavior while synchronizing through the Nextcloud Notes API.

## Features

- Import one or more accounts from the Nextcloud Files Android app through Single Sign-On.
- Read, search, create, rename, edit, and delete notes while keeping Room as the offline source of
  truth.
- Synchronize with Nextcloud Notes API 1.2 or newer using incremental pulls, ETags, conflict-safe
  updates, and durable pending changes.
- Long-press notes to select several and move them to the Nextcloud trash bin together.
- Create a note from text shared by another Android application.
- Render CommonMark and GitHub Flavored Markdown, QOwnNotes task states, wiki links, legacy
  `note://` links, tables, fenced code, and safe remote images.
- Edit highlighted Markdown source with formatting actions, undo and redo, cursor preservation, and
  local draft persistence.
- Find text inside an open note, select and copy rendered text, and adjust the note text size.
- Detect read-only and QOwnNotes-encrypted notes and fail closed for unsafe HTML, links, images, and
  filesystem access.
- Use light and dark themes on Android 9 and newer.

The production application is named **QOwnNotes** and uses `org.qownnotes.mobile`. Development
builds are named **QOwnNotes Dev** and use `org.qownnotes.mobile.dev`, so both can be installed on
the same device. See [`CHANGELOG.md`](CHANGELOG.md) for release details.

## Nextcloud Account Setup

Install the Nextcloud Files Android app, sign in to the desired server there,
then choose **Add Nextcloud account** in QOwnNotes Mobile. The app requires the
Nextcloud Notes server app with Notes API 1.2 or newer. Downloaded notes and
account metadata remain available offline; credentials stay in Nextcloud's SSO
integration and are not copied into the QOwnNotes database.

Use **Remove** in an account's note list to delete that account reference, its
synchronization history, and its cached notes from QOwnNotes Mobile. This does
not remove the account from Nextcloud Files or delete notes from the server.

Use **New** to create a QOwnNotes-compatible note or **Edit** while viewing a
writable note. Drafts are stored locally first and synchronized through the
Nextcloud Notes API; updates use the last known ETag to avoid blindly
overwriting a concurrent server edit.

## NixOS Recipes

### Enter The Development Environment

```sh
devenv shell
```

The first invocation downloads JDK 17, the Android SDK, build tools, platform

List the available recipes:

```sh
just
```

### Build The Development APK

```sh
just build-dev
```

`just build` is an alias for `just build-dev`.

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Run On The Emulator

Create the project-local emulator once:

```sh
just create-avd
```

Start it in one `devenv shell`:

```sh
just start-emulator
```

In another `devenv shell`, install and launch the development app. The recipe waits until
Android's package manager is ready:

```sh
just deploy-dev
```

`just run` is an alias for `just deploy-dev`.

### Run On A Physical Android Device

Enable ADB access in the NixOS configuration and rebuild the system:

```nix
{
  programs.adb.enable = true;
  users.users.<username>.extraGroups = [ "adbusers" ];
}
```

Enable USB debugging on the device, connect it, accept the authorization
prompt, and verify the connection:

```sh
adb devices
```

Install and launch the development app:

```sh
just deploy-dev
```

After configuring the release signing variables described below, build and deploy the production
application over USB with:

```sh
just build-release
just deploy-release
```

### Run JVM Tests

```sh
just test
```

### Format Sources

```sh
just format
just format-check
```

### Check Dependency Licenses

```sh
just license-check
```

### Run Android Device Tests

With an emulator or physical device running:

```sh
just device-test
```

CI runs the same instrumentation suite on an API 36 emulator. Real Nextcloud
SSO interoperability remains a manual test because it requires an installed
and authenticated Nextcloud Files app.

### Run All Host-Side Checks

```sh
just check
```

### Build A Signed Release

Release builds require these environment variables:

```sh
export ANDROID_KEYSTORE_PATH=/absolute/path/to/release.jks
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=...
export ANDROID_KEY_PASSWORD=...
just release
```

The signed outputs are written to:

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

CI runs checks and uploads a debug APK for every commit on every branch. A push to the `release`
branch reads the committed version, builds signed packages, extracts that version's section from
`CHANGELOG.md`, and publishes an immutable GitHub release tagged `v<version>`. Before pushing to the
release branch, increment both values in `version.properties` and add the matching changelog
section. Configure the following GitHub Actions repository secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Generate `ANDROID_KEYSTORE_BASE64` on NixOS with:

```sh
base64 -w 0 /absolute/path/to/release.jks
```

Pushes to `main` also replace the GitHub prerelease tagged `continuous` with a signed **QOwnNotes
Dev** APK and its SHA-256 checksum. Use a separate development key so publishing continuous builds
does not expose the stable release key to the `main` workflow. Configure these additional secrets:

```text
ANDROID_DEV_KEYSTORE_BASE64
ANDROID_DEV_KEYSTORE_PASSWORD
ANDROID_DEV_KEY_ALIAS
ANDROID_DEV_KEY_PASSWORD
```

### Clean Build Outputs

```sh
just clean
```
