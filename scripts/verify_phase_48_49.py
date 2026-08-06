#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing required file: {path}")
    return candidate.read_text(encoding="utf-8") if candidate.is_file() else ""


model = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyReadiness.kt")
readiness = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ExamReadinessEngine.kt")
reschedule = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/AdaptiveReschedulePlanner.kt")
app_container = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt")
navigation = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
progress = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
root_build = text("build.gradle.kts")
devtool = text(".devtool.toml")
versions = text("gradle/libs.versions.toml")

for token in (
    "data class ExamReadinessScore",
    "enum class ExamReadinessBand",
    "data class ReadinessSignal",
    "data class AdaptiveReschedulePreview",
    "data class AdaptiveRescheduleChange",
    "data class AdaptiveRescheduleApplyResult",
):
    require(token in model, f"model contract missing: {token}")

for token in (
    "class ManageExamReadiness",
    "object ExamReadinessEngine",
    "analyticsRepository.observeSnapshot",
    "scheduleRepository.observeSchedule",
    "practiceRepository.observeMockExams",
    "Review",
    "Mock exams",
    "timeFeasibility",
    "pendingMinutes <= 0",
    "horizonScore",
    "return averagePercent(coveragePercent, horizonScore)",
):
    require(token in readiness, f"readiness engine missing: {token}")

for token in (
    "class ManageAdaptiveReschedule",
    "object AdaptiveReschedulePlanner",
    "fun observePreview",
    "suspend fun applySafePreview",
    "REDISTRIBUTE_MISSED",
    "PROTECT_REVIEWS",
    "REDUCE_OVERLOAD",
    "ADD_THEORY_BEFORE_QUESTIONS",
    "!it.locked",
    "updatePlannedActivities",
):
    require(token in reschedule, f"adaptive reschedule missing: {token}")

require("val manageExamReadiness: ManageExamReadiness" in app_container, "AppContainer lacks readiness manager")
require("val manageAdaptiveReschedule: ManageAdaptiveReschedule" in app_container, "AppContainer lacks reschedule manager")
require("override val manageExamReadiness" in app_container, "Default container does not create readiness manager")
require("override val manageAdaptiveReschedule" in app_container, "Default container does not create reschedule manager")
require("readinessManager = container.manageExamReadiness" in navigation, "Progress route misses readiness manager")
require(
    "adaptiveRescheduleManager = container.manageAdaptiveReschedule" in navigation,
    "Progress route misses adaptive manager",
)

for token in (
    "readinessManager: ManageExamReadiness",
    "adaptiveRescheduleManager: ManageAdaptiveReschedule",
    "readinessFlow",
    "rescheduleFlow",
    "readiness_score",
    "adaptive_reschedule",
    "applySafePreview(plan)",
    "CancellationException",
    "rescheduleChangeLabel",
    "readinessBandLabel",
):
    require(token in progress, f"Progress workspace missing Phase 48/49 surface: {token}")

for forbidden in (
    "signal.label",
    "signal.message",
    "actionSummary",
    "change.reason",
    ".take(4)",
):
    require(forbidden not in progress, f"Progress workspace leaks or hides Phase 48/49 data: {forbidden}")

for token in (
    "readinessSignalLabel(signal.kind)",
    "readinessSignalMessage(signal.kind)",
    "rescheduleChangeReason(change.type)",
    "rescheduleSummary(",
    "types = preview.changes.map { it.type }",
    "reschedule_apply_success",
):
    require(token in progress, f"Progress workspace missing localized Phase 48/49 rendering: {token}")

for path, tokens in {
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ExamReadinessEngineTest.kt": (
        "readinessCombinesCoverageRetentionMockPracticeAndTimeLeft",
        "lowCoverageBacklogAndPoorAccuracyBecomeCritical",
    ),
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/AdaptiveReschedulePlannerTest.kt": (
        "previewRedistributesMissedUnlockedWorkAndKeepsLockedBlocksUntouched",
        "applyPreviewUpdatesOnlyExecutableExistingActivities",
    ),
}.items():
    source = text(path)
    for token in tokens:
        require(token in source, f"missing Phase 48/49 test token: {token}")

for required_task in (
    ":core:domain:test",
    ":feature:workspace:testDebugUnitTest",
    ":app:assembleDebug",
):
    require(required_task in root_build or required_task in devtool, f"validation task missing: {required_task}")

require("python3 scripts/verify_phase_48_49.py" in root_build, "Phase 48/49 verifier is not wired")

expected_versions = {
    "agp": "9.3.0",
    "kotlin": "2.4.10",
    "composeBom": "2026.06.00",
    "navigation": "2.9.8",
    "room": "2.8.0",
    "ksp": "2.3.10",
}
for name, expected in expected_versions.items():
    require(f'{name} = "{expected}"' in versions, f"toolchain drifted: {name} must remain {expected}")

string_sets = []
for path in (
    ROOT / "feature/workspace/src/main/res/values/strings.xml",
    ROOT / "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
):
    root = ET.parse(path).getroot()
    names = [element.attrib["name"] for element in root if element.tag == "string"]
    require(len(names) == len(set(names)), f"duplicate string resources in {path.relative_to(ROOT)}")
    string_sets.append(set(names))
if len(string_sets) == 2:
    require(string_sets[0] == string_sets[1], "workspace resource key parity broke")

if string_sets:
    workspace_strings = string_sets[0]
    for match in sorted(set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", progress))):
        require(match in workspace_strings, f"Progress workspace references missing string resource: {match}")

for doc_path in (
    "PHASE_48_49_READINESS_ADAPTIVE_RESCHEDULE.md",
    "docs/architecture/ADR-0030-readiness-and-adaptive-reschedule.md",
    "docs/quality/PHASE_48_49_ACCEPTANCE.md",
):
    require((ROOT / doc_path).is_file(), f"missing Phase 48/49 documentation: {doc_path}")

for source_path in [
    ROOT / "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyReadiness.kt",
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ExamReadinessEngine.kt",
    ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/AdaptiveReschedulePlanner.kt",
    ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
]:
    source = source_path.read_text(encoding="utf-8")
    require("\x00" not in source, f"NUL byte in {source_path.relative_to(ROOT)}")
    for line_number, line in enumerate(source.splitlines(), 1):
        require(len(line) <= 120, f"{source_path.relative_to(ROOT)}:{line_number} exceeds 120 characters")

if FAILURES:
    print("PHASE_48_49_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("PHASE_48_49_VALIDATION_OK")
print("Exam readiness, adaptive reschedule preview/apply, bilingual resources, and contracts verified")
