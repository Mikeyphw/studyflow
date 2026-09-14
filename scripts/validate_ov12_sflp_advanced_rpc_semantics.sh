#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 scripts/verify_ov12_sflp_advanced_rpc_semantics.py
(
  cd web
  node scripts/compile_runtime.mjs
  node tests/ov09_interop_runner.mjs
  node tests/ov10_security_runner.mjs
  node tests/ov11_reconciliation_runner.mjs
  node tests/ov12_advanced_rpc_semantics_runner.mjs
  node tests/parity04_runner.mjs
  python3 -m unittest tests.test_ov12_advanced_rpc_semantics tests.test_sflp14_15_live_projection tests.test_sflp18_19_notification_media tests.test_sflp20_21_share_conflict tests.test_sflp22_23_account_backup_validation
)
printf '%s\n' 'OV12_VALIDATION: PASS'
