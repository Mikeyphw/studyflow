#!/usr/bin/env bash
# HF07: rebuilt full Android 36 runtime gate. GitHub-hosted emulator ONLY.
# The executable room-run supplies -Pandroid.testInstrumentationRunnerArguments.class=
# com.mikeyphw.studyflow.core.data.StudySessionAtomicStateTest and runs BOTH
# :core:data:assembleDebugAndroidTest and :core:data:connectedDebugAndroidTest.
# These are mandatory REAL connected Android tests; source tests do not substitute.
set -euo pipefail
: "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_ATTEMPT:?}" "${RUNNER_TEMP:?}"
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_OS:-}" == Linux && "${RUNNER_ARCH:-}" == X64 ]] || { echo HF07_HOST_REFUSED >&2; exit 2; }
[[ "${STUDYFLOW_SFDT21_HOSTED_EMULATOR:-}" == true && "${STUDYFLOW_SFDT22_HOSTED_JOURNEYS:-}" == true ]] || { echo HF07_DEBUG_VARIANT_REFUSED >&2; exit 2; }
[[ "${GITHUB_REPOSITORY:-}" == Mikeyphw/studyflow ]] || { echo HF07_REPOSITORY_REFUSED >&2; exit 2; }
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || { echo HF07_SOURCE_REFUSED >&2; exit 2; }
[[ "$(uname -m)" == x86_64 ]] || { echo HF07_ARCH_REFUSED >&2; exit 2; }
SERIAL=emulator-5554
[[ "$(adb -s "$SERIAL" get-state)" == device ]] || { echo HF07_DEVICE_REFUSED >&2; exit 2; }
[[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo HF07_PHYSICAL_REFUSED >&2; exit 2; }
[[ "$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]] || { echo HF07_API_REFUSED >&2; exit 2; }

python3 -B scripts/sfdt22_hf30_hf07_diagnostics.py room-run --mode full \
  --output-dir "$RUNNER_TEMP/sfdt22-emulator" \
  --source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID" --attempt "$GITHUB_RUN_ATTEMPT"
echo SFDT22_HF06_REAL_ROOM_REGRESSION_PASS
# Keep the original five REAL production-UI tests, independent SFLP+Room checks,
# seven screenshots, cold restart and new PID; do not replace with mock tests.
python3 -B scripts/sfdt22_emulator_verify.py --run \
  --output-dir "$RUNNER_TEMP/sfdt22-emulator" \
  --source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID" \
  --attempt "$GITHUB_RUN_ATTEMPT"
