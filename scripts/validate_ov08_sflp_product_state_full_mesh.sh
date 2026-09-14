#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python scripts/verify_ov08_sflp_product_state_full_mesh.py
gradle --stop >/dev/null 2>&1 || true
gradle \
  :core:link:test \
  :core:data:compileDebugKotlin \
  :core:data:compileDebugAndroidTestKotlin \
  :app:compileDebugKotlin \
  :app-tv:compileDebugKotlin \
  :desktopApp:compileKotlin \
  --max-workers=4 \
  -Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=768m" \
  --parallel
printf '%s\n' 'OV08 validator: PASS'
