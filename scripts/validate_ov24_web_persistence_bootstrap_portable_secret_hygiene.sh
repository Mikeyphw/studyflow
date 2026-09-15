#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash scripts/validate_ov24_web_persistence_bootstrap_secret_hygiene.sh
