#!/usr/bin/env python3
from pathlib import Path
import sys

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


require(phase_version_at_least(ROOT, 37), "app version must be Phase 37 release candidate")
checklist = read("docs/release/FINAL_ACCEPTANCE_CHECKLIST.md")
for required in [
    "PENDING TARGET EXECUTION",
    "Fresh install",
    "Upgrade install",
    "Dashboard primary session action",
    "Notification permission",
    "Today widget",
    "Export backup",
    "Restore dry-run",
    "English and Portuguese",
    "light/dark/system theme",
    "TalkBack/focus/touch-target",
]:
    require(required in checklist, f"acceptance checklist missing marker: {required}")

report = read("FINAL_ACCEPTANCE_REPORT.md")
for required in [
    "Target execution status: **PENDING TARGET EXECUTION**",
    "Version code: `37`",
    "Version name: `1.0.0-rc1`",
    "Artifact type: pending",
    "Device/emulator: pending",
    "Fresh install: pending",
    "Upgrade install: pending",
    "Final verdict",
    "Pending. Do not mark StudyFlow release-ready",
]:
    require(required in report, f"final acceptance report missing honest pending marker: {required}")
for forbidden in [
    "Target execution status: **PASSED**",
    "release-ready until this section names",
    "All checks passed",
    "Device smoke completed.",
]:
    # The report may contain the phrase in a negative sentence only for release-ready guidance.
    if forbidden == "release-ready until this section names":
        continue
    require(forbidden not in report, f"final acceptance report must not claim target success: {forbidden}")

phase_doc = read("PHASE_37_REAL_DEVICE_ACCEPTANCE_SEAL.md")
for required in ["does not claim", "target evidence", "Fresh install", "Upgrade install"]:
    require(required in phase_doc, f"Phase 37 doc missing honest evidence marker: {required}")

validation = read("VALIDATION_REPORT.md")
for required in ["implemented through **Phase 37**", "PENDING TARGET EXECUTION", "Source validation must not mark"]:
    require(required in validation, f"validation report missing Phase 37 honesty marker: {required}")

factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
for required in ["CurrentPhase = 37", "DEVICE_SMOKE", "Must run the Phase 37 install", "sourceOnlySeal"]:
    require(required in factory, f"release validation factory missing Phase 37 target gate: {required}")
require("blocked(\n                ReleaseValidationGateKind.DEVICE_SMOKE" in factory,
        "source-only seal must keep device smoke blocked")

readiness_test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSealTest.kt")
require("assertEquals(45, report.totalPhaseCount)" in readiness_test and
        "phase.number == 36" in readiness_test and "phase.number == 37" in readiness_test,
        "release readiness test must cover 37 phases plus D1-D8")

readme = read("README.md")
for required in ["Real-device acceptance", "PENDING TARGET EXECUTION", "must be filled from a real device"]:
    require(required in readme, f"README missing Phase 37 target evidence marker: {required}")

if errors:
    print("PHASE_37_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_37_CONTRACT: PASSED (real-device acceptance framework stays evidence-based)")
