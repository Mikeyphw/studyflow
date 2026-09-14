#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/verify_ov10_sflp_security.py
(
  cd web
  node scripts/compile_runtime.mjs
  node tests/ov10_security_runner.mjs
)
printf '%s\n' 'OV10_VALIDATION: PASS'
