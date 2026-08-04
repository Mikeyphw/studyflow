#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

find . -path '*/build/reports/lint*' -type f -delete
find . -path '*/build/reports/lint*' -type d -empty -delete || true

python3 scripts/verify_phase_0.py
python3 scripts/verify_phase_1.py
python3 scripts/verify_phase_2.py
python3 scripts/verify_phase_3.py
python3 scripts/verify_phase_4.py
python3 scripts/verify_phase_5.py
python3 scripts/verify_phase_6.py
python3 scripts/verify_phase_7.py
python3 scripts/verify_phase_8.py
python3 scripts/verify_phase_9.py
python3 scripts/verify_phase_10.py
python3 scripts/verify_phase_11.py
python3 scripts/verify_phase_12.py
python3 scripts/verify_phase_13.py
python3 scripts/verify_phase_14.py
python3 scripts/verify_phase_15.py
python3 scripts/verify_phase_16.py
python3 scripts/verify_phase_17.py
python3 scripts/verify_phase_18.py
python3 scripts/verify_phase_19.py
python3 scripts/verify_phase_20.py
python3 scripts/verify_phase_21.py
python3 scripts/verify_phase_22.py
python3 scripts/verify_phase_23.py
python3 scripts/verify_phase_24.py
python3 scripts/verify_phase_25.py
python3 scripts/verify_phase_26.py
python3 scripts/verify_phase_27.py
python3 scripts/verify_phase_28.py
python3 scripts/verify_phase_29.py
python3 scripts/verify_phase_30.py
python3 scripts/verify_phase_31.py
python3 scripts/verify_phase_32.py
python3 scripts/verify_phase_33.py
python3 scripts/verify_phase_34.py
python3 scripts/verify_phase_35.py

python3 scripts/verify_debug_workbench_d1_r2.py
python3 scripts/verify_debug_workbench_d2.py
python3 scripts/verify_debug_workbench_d3.py
python3 scripts/verify_debug_workbench_d2_d3_corrective_seal.py
python3 scripts/verify_debug_workbench_d4.py
python3 scripts/verify_debug_workbench_d5.py
python3 scripts/verify_debug_workbench_d6.py
python3 scripts/verify_debug_workbench_d7.py
python3 scripts/verify_debug_workbench_d8.py
python3 scripts/verify_full_app_final_user_readiness.py

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
