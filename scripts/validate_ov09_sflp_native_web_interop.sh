#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/verify_ov09_sflp_native_web_interop.py
(
  cd web
  node scripts/compile_runtime.mjs
  node tests/ov09_interop_runner.mjs
  node tests/parity04_runner.mjs
  python3 -m unittest tests.test_ov09_native_web_interop
)
printf '%s\n' 'OV09_VALIDATION: PASS'
