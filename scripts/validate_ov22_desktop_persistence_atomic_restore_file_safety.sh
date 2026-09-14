#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/verify_ov22_desktop_persistence_atomic_restore_file_safety.py
printf '%s\n' 'OV22_VALIDATION: PASS'
