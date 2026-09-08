set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

default:
    @just --list

# Build the development APK.
build: build-dev

# Build the signed development APK with signing material from Vaultwarden.
build-dev:
    ./scripts/with-android-signing development ./gradlew assembleDebug

# Build the signed release APK with signing material from Vaultwarden.
build-release:
    ./scripts/with-android-signing release ./gradlew assembleRelease

# Build the unsigned release APK expected by F-Droid.
build-fdroid:
    env -u ANDROID_VERSION_CODE -u ANDROID_KEYSTORE_PATH -u ANDROID_KEYSTORE_PASSWORD \
      -u ANDROID_KEY_ALIAS -u ANDROID_KEY_PASSWORD \
      ./gradlew --no-configuration-cache assembleRelease
    test -f app/build/outputs/apk/release/app-release-unsigned.apk

# Verify F-Droid text, image, and release-changelog metadata.
check-fdroid-metadata:
    source version.properties; \
      metadata=fastlane/metadata/android/en-US; \
      test -s "$metadata/title.txt"; \
      test "$(wc -m < "$metadata/title.txt")" -le 50; \
      test -s "$metadata/short_description.txt"; \
      test "$(wc -m < "$metadata/short_description.txt")" -le 80; \
      test -s "$metadata/full_description.txt"; \
      test "$(wc -m < "$metadata/full_description.txt")" -le 4000; \
      test -s "$metadata/changelogs/$VERSION_CODE.txt"; \
      test "$(wc -m < "$metadata/changelogs/$VERSION_CODE.txt")" -le 500; \
      test -s "$metadata/images/icon.png"; \
      test -s "$metadata/images/phoneScreenshots/1.png"; \
      test -s "$metadata/images/phoneScreenshots/2.png"; \
      test -s "$metadata/images/phoneScreenshots/3.png"

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

# Update all dependencies declared in the Gradle version catalog.
update-dependencies:
    ./gradlew --no-configuration-cache versionCatalogUpdate

# Run all host-side checks and build the debug APK.
check: check-fdroid-metadata
    ./gradlew spotlessCheck test assembleDebug lintDebug :app:licensee

# Run all checks and build signed release packages with signing material from Vaultwarden.
release:
    ./scripts/with-android-signing release ./gradlew --no-configuration-cache spotlessCheck test lintRelease :app:licensee assembleRelease bundleRelease

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

# Install and launch the signed development app on a connected device.
deploy-dev: _wait-for-android
    ./scripts/with-android-signing development ./gradlew assembleDebug installDebug
    adb shell am start -n org.qownnotes.mobile.dev/org.qownnotes.mobile.MainActivity

# Install and launch the signed release app on a connected device.
deploy-release: _wait-for-android
    ./scripts/with-android-signing release ./gradlew assembleRelease installRelease
    adb shell am start -n org.qownnotes.mobile/.MainActivity

# Run instrumented tests on a connected device.
device-test: _wait-for-android
    ./gradlew connectedDebugAndroidTest

# Capture deterministic F-Droid screenshots on the connected Android device.
capture-fdroid-screenshots: _wait-for-android
    adb shell cmd uimode night no
    adb shell settings put system font_scale 1.0
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 0
    ./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=org.qownnotes.mobile.FdroidScreenshotTest
    screenshots=(app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/*/fdroid); \
      test "${#screenshots[@]}" -eq 1; \
      test -s "${screenshots[0]}/1.png"; \
      test -s "${screenshots[0]}/2.png"; \
      test -s "${screenshots[0]}/3.png"

# Replace the checked-in store screenshots with a fresh deterministic capture.
update-fdroid-screenshots: capture-fdroid-screenshots
    screenshots=(app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/*/fdroid); \
      destination=fastlane/metadata/android/en-US/images/phoneScreenshots; \
      install -m 0644 "${screenshots[0]}/1.png" "$destination/1.png"; \
      install -m 0644 "${screenshots[0]}/2.png" "$destination/2.png"; \
      install -m 0644 "${screenshots[0]}/3.png" "$destination/3.png"

# Wait until Android and its package manager are ready, not only ADB.
[private]
_wait-for-android:
    adb wait-for-device
    timeout 300 bash -c 'until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = "1" ] && adb shell cmd package path android >/dev/null 2>&1; do sleep 2; done'

# Remove Gradle build outputs.
clean:
    ./gradlew clean
