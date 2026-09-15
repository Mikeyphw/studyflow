#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/verify_ov25_web_attention_push_tv_timing_route_safety.py
node web/tests/ov25_attention_tv_safety_runner.mjs
