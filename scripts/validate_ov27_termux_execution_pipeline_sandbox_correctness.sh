#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 scripts/verify_ov27_termux_execution_pipeline_sandbox_correctness.py
if command -v kotlinc >/dev/null 2>&1; then
  rm -rf build/ov27-model-kotlinc
  kotlinc core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/*.kt -d build/ov27-model-kotlinc
  echo "OV27 model syntax: PASS"
else
  echo "OV27 model syntax: SKIP (kotlinc not available)"
fi
if [ "${STUDYFLOW_OV27_RUN_GRADLE:-0}" = "1" ] && [ -x ./gradlew ]; then
  ./gradlew :core:model:testDebugUnitTest :app:testDebugUnitTest --tests '*Termux*'
fi
echo "OV27 Termux execution pipeline validation: PASS"
