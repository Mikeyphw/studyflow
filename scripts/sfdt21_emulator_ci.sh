#!/usr/bin/env bash
# SFDT21: only on fresh, disposable, GitHub-hosted API36 x86_64 emulator.
set -euo pipefail
: "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_ATTEMPT:?}" "${RUNNER_TEMP:?}"
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_OS:-}" == Linux ]] || { echo SFDT21_HOST_REFUSED >&2; exit 2; }
[[ "${STUDYFLOW_SFDT21_HOSTED_EMULATOR:-}" == true ]] || { echo SFDT21_VARIANT_REFUSED >&2; exit 2; }
[[ "${GITHUB_REPOSITORY:-}" == Mikeyphw/studyflow ]] || { echo SFDT21_REPO_REFUSED >&2; exit 2; }
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || { echo SFDT21_SOURCE_REFUSED >&2; exit 2; }
[[ "$(uname -m)" == x86_64 ]] || { echo SFDT21_ARCH_REFUSED >&2; exit 2; }
SERIAL=emulator-5554
[[ "$(adb -s "$SERIAL" get-state)" == device ]] || { echo SFDT21_DEVICE_REFUSED >&2; exit 2; }
[[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo SFDT21_PHYSICAL_REFUSED >&2; exit 2; }
[[ "$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]] || { echo SFDT21_API_REFUSED >&2; exit 2; }
[[ -z "$(adb -s "$SERIAL" shell pm path com.mikeyphw.studyflow 2>/dev/null | tr -d '\r')" ]] || { echo SFDT21_NONFRESH_APP_REFUSED >&2; exit 2; }
# Normal phone font size. No account, credentials, networking, or restored data.
adb -s "$SERIAL" shell settings put system font_scale 1.0 >/dev/null
adb -s "$SERIAL" shell svc wifi disable >/dev/null
adb -s "$SERIAL" shell svc data disable >/dev/null
python3 -B scripts/sfdt21_emulator_verify.py --run --output-dir "$RUNNER_TEMP/sfdt21-emulator" --source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID" --attempt "$GITHUB_RUN_ATTEMPT"
