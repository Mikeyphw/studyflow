#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/verify_ov02_session_practice_invariants.py

if command -v gradle >/dev/null 2>&1; then
  GRADLE=(gradle)
else
  GRADLE=(./gradlew)
fi

"${GRADLE[@]}" --stop >/dev/null 2>&1 || true
"${GRADLE[@]}" \
  :core:model:test \
  :core:domain:test \
  :core:portability:test \
  :desktopApp:test \
  :app:compileDebugKotlin \
  --max-workers=4 \
  --parallel

printf '%s\n' 'OV02 validator: PASS — source contracts, targeted JVM tests, Desktop tests, and Android compile gate completed.'
