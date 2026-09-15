#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/verify_ov30r2_studyflow_tv_proper_validation_seal.py
