#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

for required in \
  STUDYFLOW_RELEASE_STORE_FILE \
  STUDYFLOW_RELEASE_STORE_PASSWORD \
  STUDYFLOW_RELEASE_KEY_ALIAS \
  STUDYFLOW_RELEASE_KEY_PASSWORD; do
  [[ -n "${!required:-}" ]] || {
    echo "FINAL RELEASE ACCEPTANCE: BLOCKED — $required is required." >&2
    exit 2
  }
done

command -v devtool >/dev/null || {
  echo "FINAL RELEASE ACCEPTANCE: BLOCKED — devtool is required." >&2
  exit 3
}
command -v adb >/dev/null || {
  echo "FINAL RELEASE ACCEPTANCE: BLOCKED — adb missing." >&2
  exit 4
}

mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
[[ ${#DEVICES[@]} -eq 1 ]] || {
  echo "FINAL RELEASE ACCEPTANCE: BLOCKED — exactly one adb device required." >&2
  exit 5
}

export STUDYFLOW_RELEASE_VALIDATION_SESSION="${STUDYFLOW_RELEASE_VALIDATION_SESSION:-$(
  python3 -c 'import uuid; print(uuid.uuid4())'
)}"

rm -f build/reports/studyflow/release-host-gates.json \
  build/reports/studyflow/release-artifact-identity.json \
  build/reports/studyflow/connected-test-gates.json \
  build/reports/studyflow/full-journey.json \
  build/reports/studyflow/release-journey-initial.txt \
  build/reports/studyflow/release-journey-post-restart.txt \
  build/reports/studyflow/device-acceptance.json \
  build/reports/studyflow/final-release-verdict.json
rm -rf app/build/outputs/androidTest-results

echo "Release validation session: $STUDYFLOW_RELEASE_VALIDATION_SESSION"
echo "Release Android build/validation runner: Devtool target studyflow_release"

devtool --yes \
  -r "$ROOT_DIR" \
  --target studyflow_release \
  build

cat <<'MSG'
Automated final validation passed for one signed APK/AAB session, all required connected Android suites,
and the exact signed APK through real restore -> forced process restart -> durable re-read.

The product is still NOT sealed. Complete every explicit real-device observation with both --pass-check
and matching --evidence CHECK=OBSERVATION arguments to scripts/record_final_device_acceptance.py, then run:

  python3 scripts/run_target_release_validation.py --mode verdict

See docs/release/FINAL_ACCEPTANCE_CHECKLIST.md for the required check IDs and evidence format.
MSG
