#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

python scripts/verify_ov05_duplication_migrations_snapshots.py

gradle --stop || true

gradle \
  :core:database:testDebugUnitTest \
  :core:data:testDebugUnitTest \
  :core:database:compileDebugAndroidTestKotlin \
  :core:data:compileDebugAndroidTestKotlin \
  --max-workers=4 \
  -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' \
  --parallel

connected_device=""
if command -v adb >/dev/null 2>&1; then
  connected_device="$(adb devices 2>/dev/null | awk 'NR>1 && $2 == "device" { print $1; exit }')"
fi

if [[ -n "$connected_device" ]]; then
  echo "OV05: running Room migration/duplication/snapshot instrumentation on $connected_device"
  gradle \
    :core:database:connectedDebugAndroidTest \
    :core:data:connectedDebugAndroidTest \
    --max-workers=4 \
    -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' \
    --parallel
else
  echo "OV05: no authorized ADB device detected; instrumentation runtime probes are compiled and deferred to the final/device gate."
fi

echo "OV05 validator: PASS"
