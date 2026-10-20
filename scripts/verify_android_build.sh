#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: Gradle 8.13 is required. Install it or use .github/workflows/android.yml." >&2
  exit 2
fi
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT/platforms/android-35" ]]; then
  echo "ERROR: Android SDK Platform 35 is missing. Set ANDROID_SDK_ROOT/ANDROID_HOME and install platforms;android-35." >&2
  exit 2
fi

# Run unit tests before assembling, then require a real APK and verify its signature.
gradle --no-daemon testDebugUnitTest assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "ERROR: Gradle exited without producing a non-empty APK: $APK" >&2
  exit 3
fi
BUILD_TOOLS="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d -print 2>/dev/null | sort -V | tail -n1)"
APKSIGNER="$BUILD_TOOLS/apksigner"
if [[ ! -x "$APKSIGNER" ]]; then
  echo "ERROR: apksigner was not found under $SDK_ROOT/build-tools." >&2
  exit 4
fi
"$APKSIGNER" verify --verbose "$APK"
sha256sum "$APK"
printf '\nAPK verified: %s\n' "$APK"
