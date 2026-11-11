#!/usr/bin/env bash
# SFDT22: destructive test data allowed ONLY on a disposable GitHub-hosted API36 emulator.
set -euo pipefail
: "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_ATTEMPT:?}" "${RUNNER_TEMP:?}"
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_OS:-}" == Linux ]] || { echo SFDT22_HOST_REFUSED >&2; exit 2; }
[[ "${STUDYFLOW_SFDT21_HOSTED_EMULATOR:-}" == true && "${STUDYFLOW_SFDT22_HOSTED_JOURNEYS:-}" == true ]] || { echo SFDT22_DEBUG_VARIANT_REFUSED >&2; exit 2; }
[[ "${GITHUB_REPOSITORY:-}" == Mikeyphw/studyflow ]] || { echo SFDT22_REPOSITORY_REFUSED >&2; exit 2; }
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || { echo SFDT22_SOURCE_REFUSED >&2; exit 2; }
[[ "$(uname -m)" == x86_64 ]] || { echo SFDT22_ARCH_REFUSED >&2; exit 2; }
SERIAL=emulator-5554
[[ "$(adb -s "$SERIAL" get-state)" == device ]] || { echo SFDT22_DEVICE_REFUSED >&2; exit 2; }
[[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo SFDT22_PHYSICAL_REFUSED >&2; exit 2; }
[[ "$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]] || { echo SFDT22_API_REFUSED >&2; exit 2; }
# Exercise real Room repository/domain transitions on the disposable API36 emulator.
# This is a required Android JUnit run, not a source-only or mock test.
./gradlew --no-daemon --max-workers=2 --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mikeyphw.studyflow.core.data.StudySessionAtomicStateTest \
  :core:data:connectedDebugAndroidTest
echo SFDT22_HF06_REAL_ROOM_REGRESSION_PASS
python3 -B scripts/sfdt22_emulator_verify.py --run \
  --output-dir "$RUNNER_TEMP/sfdt22-emulator" \
  --source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID" \
  --attempt "$GITHUB_RUN_ATTEMPT"
