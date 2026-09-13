#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

python scripts/verify_ov07_backup_snapshot_saf_manual_sync.py

gradle --stop || true

gradle \
  :core:portability:test \
  :core:data:testDebugUnitTest \
  :core:domain:test \
  :core:model:test \
  :desktopApp:test \
  :app:testDebugUnitTest \
  :feature:plans:compileDebugKotlin \
  :feature:settings:compileDebugKotlin \
  :core:data:compileDebugAndroidTestKotlin \
  :app:compileDebugAndroidTestKotlin \
  --max-workers=4 \
  -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' \
  --parallel

connected_device=""
if command -v adb >/dev/null 2>&1; then
  connected_device="$(adb devices 2>/dev/null | awk 'NR>1 && $2 == "device" { print $1; exit }')"
fi

if [[ -n "$connected_device" ]]; then
  echo "OV07: running durability/restore instrumentation on $connected_device"
  gradle \
    :core:data:connectedDebugAndroidTest \
    :app:connectedDebugAndroidTest \
    --max-workers=4 \
    -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' \
    --parallel
else
  echo "OV07: no authorized ADB device detected; instrumentation probes are compiled and deferred to the final/device gate."
fi

echo "OV07 validator: PASS"
