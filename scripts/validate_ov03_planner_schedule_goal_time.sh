#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/verify_ov03_planner_schedule_goal_time.py

if command -v gradle >/dev/null 2>&1; then
  GRADLE=(gradle)
else
  GRADLE=(./gradlew)
fi

"${GRADLE[@]}" --stop >/dev/null 2>&1 || true
"${GRADLE[@]}" \
  :core:model:test \
  :core:domain:test \
  :desktopApp:test \
  :app:compileDebugKotlin \
  --max-workers=4 \
  --parallel

printf '%s\n' 'OV03 validator: PASS — source contracts, shared model/domain tests, Desktop monotonic-clock tests, and Android compile gate completed.'
