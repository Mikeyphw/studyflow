#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/verify_phase_0.py
python3 scripts/verify_phase_1.py
python3 scripts/verify_phase_2.py
python3 scripts/verify_phase_3.py
python3 scripts/verify_phase_4.py
python3 scripts/verify_phase_5.py

GRADLE_LAUNCHER=""
if [[ -x ./gradlew ]]; then
  GRADLE_LAUNCHER="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_LAUNCHER="$(command -v gradle)"
fi

if [[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] && [[ -n "$GRADLE_LAUNCHER" ]]; then
  "$GRADLE_LAUNCHER" --no-daemon verifyFormatting checkAll :app:assembleDebug
else
  echo "ANDROID VALIDATION: SKIPPED (use Devtool or provide Android SDK plus Gradle 9.5.x)"
fi
