#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 scripts/verify_ov28_language_review_desktop_specialized_parity.py

if command -v kotlinc >/dev/null 2>&1; then
  tmpdir="${TMPDIR:-/tmp}/studyflow-ov28-$$"
  rm -rf "$tmpdir"
  mkdir -p "$tmpdir"
  trap 'rm -rf "$tmpdir"' EXIT

  core_sources_file="$tmpdir/core-model-sources.txt"
  find core/model/src/main/kotlin -name '*.kt' | sort > "$core_sources_file"
  kotlinc @"$core_sources_file" -d "$tmpdir/core-model.jar"

  kotlinc -cp "$tmpdir/core-model.jar" \
    desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModePersistence.kt \
    -d "$tmpdir/desktop-content-mode-persistence.jar"

  echo "OV28 model and Desktop persistence syntax: PASS"
else
  echo "kotlinc not found; skipped OV28 Kotlin syntax/behavior compile"
fi

if [[ "${STUDYFLOW_RUN_GRADLE_OV28:-0}" == "1" && -x ./gradlew ]]; then
  ./gradlew --no-daemon :core:model:test :desktopApp:test --tests '*LanguageReviewPracticeTest*' --tests '*DesktopContentModePersistenceExecutionTest*'
fi
