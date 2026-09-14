#!/usr/bin/env bash
set -euo pipefail
python3 scripts/verify_ov21_unified_tv_control_plane.py
node web/tests/ov21_tv_plane_runner.mjs
