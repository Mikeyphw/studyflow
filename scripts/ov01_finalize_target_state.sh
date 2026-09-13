#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

python3 scripts/ov01_prune_stale_devtool_state.py
./scripts/refresh_gradle_dependency_integrity.sh

# Devtool owns .devtool at runtime, so the overlay cannot inventory those deletions. If any stale
# runtime files were historically tracked, record their deletion; otherwise these are no-ops.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    for path in \
        .devtool/artifacts/registry.json \
        .devtool/artifacts/registry.lock \
        .devtool/test-history.json
    do
        git add -u -- "$path" >/dev/null 2>&1 || true
    done

    # Add only integrity state produced by the canonical refresh, never unrelated working-tree files.
    find . -type f -name gradle.lockfile \
        -not -path '*/build/*' \
        -not -path '*/.gradle/*' \
        -exec git add -- {} +
    git add -- gradle/verification-metadata.xml build-logic/gradle/verification-metadata.xml

    if ! git diff --cached --quiet; then
        if git rev-parse --verify HEAD >/dev/null 2>&1; then
            git commit --amend --no-edit
        else
            git commit -m "OV01: establish repository Gradle Devtool truth baseline"
        fi
    fi
fi

python3 scripts/verify_gradle_dependency_integrity.py --require-materialized
printf '%s\n' 'OV01_TARGET_STATE_FINALIZATION: PASS'
