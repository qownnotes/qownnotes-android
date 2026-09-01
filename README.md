# QOwnNotes Mobile

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

### Build The Debug APK

```sh
just build
```

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

In another `devenv shell`, install and launch the app. The recipe waits until
Android's package manager is ready:

```sh
just run
```

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

Install and launch the app:

```sh
just run
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

CI runs checks and uploads a debug APK for every commit on every branch. Pushes
to the `release` branch additionally build signed release packages. Configure
the following GitHub Actions repository secrets:

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

### Clean Build Outputs

```sh
just clean
```
