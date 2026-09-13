#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_BIN="${GRADLE_BIN:-gradle}"

python ./scripts/verify_ov04_room_graph_integrity.py

"$GRADLE_BIN" --stop >/dev/null 2>&1 || true

"$GRADLE_BIN" \
  :core:database:compileDebugKotlin \
  :core:data:compileDebugKotlin \
  :core:database:compileDebugAndroidTestKotlin \
  :core:data:compileDebugAndroidTestKotlin \
  :core:database:testDebugUnitTest \
  :core:data:testDebugUnitTest \
  --max-workers=4 \
  -Dorg.gradle.jvmargs='-Xmx2048m -XX:MaxMetaspaceSize=768m' \
  --parallel

printf '\nOV04 validator passed. Connected-device runtime cases remain packaged for the final/device gate.\n'
