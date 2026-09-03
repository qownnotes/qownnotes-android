set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

default:
    @just --list

# Build the development APK.
build: build-dev

# Build the development APK.
build-dev:
    ./gradlew assembleDebug

# Build the signed release APK.
build-release: _require-release-signing
    ./gradlew assembleRelease

# Run all JVM unit tests.
test:
    ./gradlew test

# Run Android lint for the debug build.
lint:
    ./gradlew lintDebug

# Format Kotlin, Gradle, Markdown, YAML, and properties files.
format:
    ./gradlew spotlessApply

# Verify source formatting without changing files.
format-check:
    ./gradlew spotlessCheck

# Verify licenses for the complete production dependency graph.
license-check:
    ./gradlew :app:licensee

# Run all host-side checks and build the debug APK.
check:
    ./gradlew spotlessCheck test assembleDebug lintDebug :app:licensee

# Run all checks and build signed release packages.
release: _require-release-signing
    ./gradlew --no-configuration-cache spotlessCheck test lintRelease :app:licensee assembleRelease bundleRelease

# Create the project-local API 36 emulator.
create-avd:
    mkdir -p "$DEVENV_ROOT/.devenv/state/avd"
    export ANDROID_AVD_HOME="$DEVENV_ROOT/.devenv/state/avd"; \
      if ! avdmanager list avd | grep -q "Name: qownnotes-api36"; then \
        echo no | avdmanager create avd --force --name qownnotes-api36 \
          --package "system-images;android-36;google_apis;x86_64" --device pixel_6; \
      fi

# Start the API 36 emulator. Keep this terminal open.
start-emulator: create-avd
    export ANDROID_AVD_HOME="$DEVENV_ROOT/.devenv/state/avd"; \
      unset LD_LIBRARY_PATH; \
      emulator -avd qownnotes-api36 -no-snapshot -no-boot-anim

# Install and launch the development app on a connected device.
run: deploy-dev

# Install and launch the development app on a connected device.
deploy-dev: build-dev _wait-for-android
    ./gradlew installDebug
    adb shell am start -n org.qownnotes.mobile.dev/org.qownnotes.mobile.MainActivity

# Install and launch the signed release app on a connected device.
deploy-release: build-release _wait-for-android
    ./gradlew installRelease
    adb shell am start -n org.qownnotes.mobile/.MainActivity

# Run instrumented tests on a connected device.
device-test: _wait-for-android
    ./gradlew connectedDebugAndroidTest

# Wait until Android and its package manager are ready, not only ADB.
[private]
_wait-for-android:
    adb wait-for-device
    timeout 300 bash -c 'until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = "1" ] && adb shell cmd package path android >/dev/null 2>&1; do sleep 2; done'

# Verify the release signing environment is complete.
[private]
_require-release-signing:
    test -n "$ANDROID_KEYSTORE_PATH"
    test -n "$ANDROID_KEYSTORE_PASSWORD"
    test -n "$ANDROID_KEY_ALIAS"
    test -n "$ANDROID_KEY_PASSWORD"

# Remove Gradle build outputs.
clean:
    ./gradlew clean
