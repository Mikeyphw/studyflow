#!/usr/bin/env bash
set -euo pipefail
python3 scripts/verify_ov14_android_receiver_widget_durability.py
# Reuse the committed cross-device gate that OV14 must not regress.
node web/tests/parity04_runner.mjs
