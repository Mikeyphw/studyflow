#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncRunbook.kt": [
        "StudySyncRunIntent",
        "MANUAL_UPLOAD",
        "BACKGROUND_FULL_AUTOMATIC",
        "BUILD_ENCRYPTED_BUNDLE",
        "DRY_RUN_RESTORE",
        "CONFLICT_REVIEW_REQUIRED",
        "appliesLocalData: Boolean = false",
        "StudySyncRunbookPlanner",
    ],
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncRunbookTest.kt": [
        "manual upload prepares encrypted bundle",
        "restore preview never applies local data",
        "background upload requires matching enabled background mode",
        "disabled sync blocks every remote run",
    ],
    "docs/quality/CSOV13_SYNC_RUNBOOK.md": [
        "CSOV13 sync runbook",
        "dry-run restore only",
        "Disabled sync or unconfigured providers block",
    ],
}
failures = []
for rel, markers in checks.items():
    path = ROOT / rel
    if not path.exists():
        failures.append(f"missing {rel}")
        continue
    text = path.read_text()
    for marker in markers:
        if marker not in text:
            failures.append(f"{rel}: missing {marker}")
if failures:
    raise SystemExit("CSOV13_SYNC_RUNBOOK failed:\n" + "\n".join(failures))
print("CSOV13_SYNC_RUNBOOK: PASS")
