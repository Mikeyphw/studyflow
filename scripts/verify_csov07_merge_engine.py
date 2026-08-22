#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        failures.append(f"missing file: {rel}")
        return ""
    return path.read_text()

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt")
engine = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncMerge.kt")
test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncMergeEngineTest.kt")

for marker in [
    "StudySyncEntityVersion",
    "StudySyncEntityState",
    "StudySyncMergeAction",
    "StudySyncMergeConflictReason",
    "StudySyncMergeConflict",
    "StudySyncMergeDecision",
    "StudySyncMergePlan",
    "stableKey",
]:
    if marker not in model:
        failures.append(f"StudySync.kt missing {marker}")

for marker in [
    "object StudySyncMergeEngine",
    "fun decide(",
    "fun plan(",
    "KEEP_LOCAL",
    "IMPORT_REMOTE",
    "USE_REMOTE",
    "MANUAL_CONFLICT",
    "DELETED_LOCALLY_CHANGED_REMOTELY",
    "DELETED_REMOTELY_CHANGED_LOCALLY",
    "associateByStableKey",
]:
    if marker not in engine:
        failures.append(f"StudySyncMerge.kt missing {marker}")

for marker in [
    "local only entity stays local",
    "one sided remote change",
    "both changed uses manual conflict",
    "delete versus change becomes explicit conflict",
]:
    if marker not in test:
        failures.append(f"StudySyncMergeEngineTest.kt missing {marker}")

if failures:
    raise SystemExit("CSOV07 merge engine violations:\n" + "\n".join(failures))
print("CSOV07_MERGE_ENGINE: PASS")
