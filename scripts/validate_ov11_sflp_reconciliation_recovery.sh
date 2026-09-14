#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 scripts/verify_ov11_sflp_reconciliation_recovery.py
(
  cd web
  node scripts/compile_runtime.mjs
  node tests/ov09_interop_runner.mjs
  node tests/ov10_security_runner.mjs
  node tests/ov11_reconciliation_runner.mjs
  node tests/parity04_runner.mjs
  python3 -m unittest tests.test_ov11_reconciliation_recovery tests.test_sflp10_web_link
)
printf '%s\n' 'OV11_VALIDATION: PASS'
