#!/usr/bin/env bash
# Capture a fingerprint from the connected Android device.
#
# Usage: scripts/collect.sh <env-label> [-o output.json] [--apk path] [--build|--skip-build]
#
# Flow: build (if stale) -> adb install -> am start with extra_auto_export ->
# wait for the FINGERPRINT_EXPORTED logcat marker -> pull fingerprint.json
# (run-as, with external-files fallback) -> analysis.validate.
#
# Environment: ANDROID_SERIAL selects the device; JAVA_HOME must point at a
# JDK 17-21 for the Gradle build. ADB can be overridden via $ADB.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="com.pubgcompat.collector"
DEF_APK="$ROOT/collector/android/app/build/outputs/apk/debug/app-debug.apk"
ADB="${ADB:-adb}"
TIMEOUT_SECONDS=120

usage() {
  grep '^#' "$0" | sed 's/^# \{0,1\}//' | head -8
  exit 2
}

LABEL=""
OUTPUT=""
APK="$DEF_APK"
MODE="auto" # auto | build | skip

while [[ $# -gt 0 ]]; do
  case "$1" in
    -o|--output) OUTPUT="${2:?}"; shift 2 ;;
    --apk) APK="${2:?}"; shift 2 ;;
    --build) MODE="build"; shift ;;
    --skip-build) MODE="skip"; shift ;;
    -h|--help) usage ;;
    -*) echo "error: unknown option: $1" >&2; usage ;;
    *)
      [[ -z "$LABEL" ]] || { echo "error: only one env-label allowed" >&2; usage; }
      LABEL="$1"; shift ;;
  esac
done

[[ -n "$LABEL" ]] || usage
command -v "$ADB" >/dev/null || { echo "error: adb not found (set \$ADB)" >&2; exit 2; }
"$ADB" get-state >/dev/null 2>&1 || { echo "error: no device connected" >&2; exit 2; }

need_build=0
if [[ "$MODE" == "build" ]]; then
  need_build=1
elif [[ "$MODE" == "auto" ]]; then
  if [[ ! -f "$APK" ]]; then
    need_build=1
  elif find "$ROOT/collector/android/app/src" \
      "$ROOT/collector/android/app/build.gradle.kts" \
      "$ROOT/collector/android/build.gradle.kts" \
      -newer "$APK" -print -quit 2>/dev/null | grep -q .; then
    need_build=1
  fi
fi

if [[ $need_build -eq 1 ]]; then
  echo "Building APK..."
  (cd "$ROOT/collector/android" && ./gradlew assembleDebug --console=plain -q)
fi
[[ -f "$APK" ]] || { echo "error: APK not found at $APK" >&2; exit 2; }

echo "Installing $APK..."
"$ADB" install -r "$APK" >/dev/null

echo "Triggering collection (label=$LABEL)..."
"$ADB" logcat -c
# -S force-stops the app first so onCreate (and auto-export) always runs.
"$ADB" shell am start -S -n "$PKG/.MainActivity" \
  --es extra_env_label "$LABEL" --ez extra_auto_export true >/dev/null

deadline=$((SECONDS + TIMEOUT_SECONDS))
marker=""
while (( SECONDS < deadline )); do
  if "$ADB" logcat -d -s System.out:I 2>/dev/null | grep -q "FINGERPRINT_EXPORTED:"; then
    marker=1
    break
  fi
  sleep 2
done
[[ -n "$marker" ]] || {
  echo "error: timed out after ${TIMEOUT_SECONDS}s waiting for FINGERPRINT_EXPORTED" >&2
  exit 1
}

pull_fingerprint() {
  # Internal copy via run-as (debuggable build), then external-files fallback.
  if "$ADB" exec-out run-as "$PKG" cat files/fingerprint.json 2>/dev/null; then
    return 0
  fi
  "$ADB" exec-out cat "/sdcard/Android/data/$PKG/files/fingerprint.json" 2>/dev/null
}

content="$(pull_fingerprint || true)"
[[ "$content" == *'"fingerprint_sha256"'* ]] || {
  echo "error: pulled fingerprint is empty or malformed" >&2
  exit 1
}

if [[ -z "$OUTPUT" ]]; then
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  OUTPUT="$ROOT/datasets/fingerprints/${LABEL}-${stamp}.json"
fi
mkdir -p "$(dirname "$OUTPUT")"
printf '%s\n' "$content" > "$OUTPUT"
echo "Wrote $OUTPUT"

echo "Validating..."
(cd "$ROOT" && python3 -m analysis.validate "$OUTPUT")
