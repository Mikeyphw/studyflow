#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
python3 scripts/verify_ov18_native_tv_durable_state_action_journal.py
python3 scripts/verify_tvov16_control_state_continuity.py
python3 scripts/verify_tvov21_presentation_projection.py
python3 scripts/verify_tvov25_domain_persistence_convergence.py
printf 'OV18_VALIDATION: PASS\n'
