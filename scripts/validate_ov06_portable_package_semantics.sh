#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

python scripts/verify_ov06_portable_package_semantics.py

gradle --stop || true

gradle \
  :core:portability:test \
  :core:database:testDebugUnitTest \
  :core:data:testDebugUnitTest \
  :desktopApp:test \
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
  echo "OV06: running portability/restore instrumentation on $connected_device"
  gradle \
    :core:database:connectedDebugAndroidTest \
    :core:data:connectedDebugAndroidTest \
    --max-workers=4 \
    -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' \
    --parallel
else
  echo "OV06: no authorized ADB device detected; instrumentation probes are compiled and deferred to the final/device gate."
fi

echo "OV06 validator: PASS"
