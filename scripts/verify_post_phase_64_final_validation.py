#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

roadmap = read("docs/roadmap/IMPLEMENTATION_ROADMAP.md")
index = read("docs/roadmap/FINAL_ROADMAP_INDEX.md")
seal = read("FINAL_FULL_APP_ACCEPTANCE_SEAL.md")
matrix = read("docs/validation/STUDYFLOW_FINAL_ACCEPTANCE_MATRIX.md")
build = read("build.gradle.kts")
devtool = read(".devtool.toml")
journey = read("app/src/androidTest/java/com/mikeyphw/studyflow/StudyFlowFullJourneyAcceptanceTest.kt")
evidence = read("scripts/record_full_journey_evidence.py")
verdict = read("scripts/verify_final_release_verdict.py")
contract = read("scripts/verify_final_release_verdict_contract.py")
workspace_connected = read(
    "feature/workspace/src/androidTest/java/com/mikeyphw/studyflow/feature/workspace/WorkspacePhase63_64ConnectedTest.kt",
)
readme = read("README.md")

for token in ("Phase 63: Exam-date-aware", "Phase 64: Version-bound", "no documented Phase 65 feature"):
    require(token in roadmap, f"implementation roadmap missing current boundary: {token}")
for token in ("Phase 63: exam runbook", "Phase 64: preview-first seven-day study sprint", "No Phase 65 feature"):
    require(token in index, f"final roadmap index missing current boundary: {token}")
for token in ("Phase 63 exam runbook", "Phase 64 preview-first seven-day study sprint", "landed Phase 64 repository"):
    require(token in seal, f"final full-app seal is stale: {token}")
require("Exam runbook / seven-day sprint" in matrix, "final acceptance matrix omits Phase 63/64")

for token in (
    "StudyExamRunbookPlanner.build(",
    "StudySprintPlanner.preview(",
    "applyVersionedPreview(planId, sprintPreview)",
    "KEY_SPRINT_ACTIVITY_ID",
    "restoredSprintActivity",
):
    require(token in journey, f"signed Release journey missing {token}")
for token in (
    "studyflowPhase63_64InitialVerified",
    "studyflowPhase63_64PostRestartVerified",
    "reportPhase63_64Status(",
):
    require(token in journey, f"signed Release journey does not emit explicit Phase 63/64 status: {token}")
for token in (
    "require_status_marker(initial, INITIAL_PHASE_MARKER)",
    "require_status_marker(restarted, POST_RESTART_PHASE_MARKER)",
    '"phase63_64InitialVerified": True',
    '"phase63_64PostRestartVerified": True',
    '"phase63_64Verified": True',
):
    require(token in evidence, f"journey evidence is not bound to explicit Phase 63/64 status: {token}")
for token in (
    'journey.get("phase63_64InitialVerified") is not True',
    'journey.get("phase63_64PostRestartVerified") is not True',
    'journey.get("phase63_64Verified") is not True',
):
    require(token in verdict, f"final verdict does not require explicit Phase 63/64 evidence: {token}")
for token in (
    "missing initial Phase 63/64 marker falsely passed",
    "missing post-restart Phase 63/64 marker falsely passed",
    "missing Phase 63/64 journey evidence falsely passed",
):
    require(token in contract, f"false-green contract omits Phase 63/64 case: {token}")

for token in (
    "class WorkspacePhase63_64ConnectedTest",
    "phase63RunbookAndPhase64SprintUseTheVersionBoundWorkspaceBoundary",
    "StudyExamRunbookPlanner.build(",
    "StudySprintPlanner.preview(",
    "manager.applyVersionedPreview(planId, sprint)",
):
    require(token in workspace_connected, f"workspace connected Phase 63/64 coverage missing: {token}")
require(
    ":feature:workspace:connectedDebugAndroidTest" in build,
    "final automated gate no longer executes the workspace connected Android suite",
)
require(
    "UI/UX V2 and post-V2 tracks advance the source tree through Phase 64" in readme,
    "README still reports a stale pre-Phase-64 source-tree boundary",
)

for token in ("verifyPostPhase64FinalValidationRefresh", "verifyFinalReleaseAutomatedGate"):
    require(token in build, f"Gradle final validation wiring missing {token}")
require("verifyPostPhase64FinalValidationRefresh" in devtool, "Devtool source test defaults omit final refresh")

if errors:
    print("POST_PHASE_64_FINAL_VALIDATION_REFRESH: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("POST_PHASE_64_FINAL_VALIDATION_REFRESH: PASSED")
