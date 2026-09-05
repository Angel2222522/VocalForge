#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ]; then echo 'Set ANDROID_HOME to inspect the APK' >&2; exit 2; fi
apk=app/build/outputs/apk/debug/app-debug.apk
"$sdk/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$apk"
"$sdk/build-tools/35.0.0/aapt2" dump badging "$apk"
sha256sum "$apk"
echo 'This is a debug-signed validation APK, not a signed production release.'
