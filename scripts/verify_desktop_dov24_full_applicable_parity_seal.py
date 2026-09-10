#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
paths = {
    "doc": ROOT / "DESKTOP_DOV24_FULL_APPLICABLE_PARITY_SEAL.md",
    "progress_repo": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyProgressRepository.kt",
    "goals": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopGoalWorkspaceController.kt",
    "services": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt",
    "plan": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlanContentController.kt",
    "analytics_repo": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsDiscovery.kt",
    "progress": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopProgressReadinessController.kt",
    "screen": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
    "layout": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLayout.kt",
    "test": ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopDov24ApplicableParityAcceptanceTest.kt",
    "matrix": ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "remaining": ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json",
    "root_gradle": ROOT / "build.gradle.kts",
    "desktop_gradle": ROOT / "desktopApp/build.gradle.kts",
    "devtool": ROOT / ".devtool.toml",
}
for key, path in paths.items():
    assert path.is_file(), f"missing {key}: {path}"
text = {key: path.read_text() for key, path in paths.items()}

portability_test = ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPortabilityMigrationTest.kt"
portability_test_text = portability_test.read_text()
for forbidden in ("Object::toString", "legacy::remove"):
    assert forbidden not in portability_test_text, forbidden

for token in (
    "StudyProgressRepository",
    "StudyGoalProgressEngine.evaluate",
    "progress-v1.properties",
    "fun purgePlan",
):
    assert token in text["progress_repo"], token
for token in ("createGoal", "incrementCustomGoal", "archive", "restore", "delete"):
    assert token in text["goals"], token
for token in ("ManageStudyProgress", "progressRepository", "goalWorkspaceController", "GoalEvidence"):
    assert token in text["services"], token
assert "progressRepository?.purgePlan(selected.id)" in text["plan"]
assert "GoalProgressCalculator::overallGoalPercent" in text["analytics_repo"]
for token in ("goals = goals", "notes = progressNotes", "val goals: List<StudyGoal>"):
    assert token in text["progress"], token
for token in ("Goals", "Create goal", "onIncrementGoal", "onArchiveGoal", "onRestoreGoal", "onDeleteGoal"):
    assert token in text["screen"], token
assert "import androidx.compose.material.OutlinedButton" in text["screen"]
for forbidden in ("::toPortableRow", "PortableRow::toStudyTarget", "PortableRow::toStudyCycleItem"):
    portability = (ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPortabilityController.kt").read_text()
    assert forbidden not in portability, forbidden
assert 'desktopText("Desktop workspace")' in text["layout"]
assert 'desktopText("Desktop preview")' not in text["layout"]
for token in (
    "dov24-session-goal",
    "dov24-custom-goal",
    "StudyEvidenceEventKind.GOAL_COMPLETED",
    "deleteSelectedPermanently",
    "studyflow://route/analytics",
):
    assert token in text["test"], token

matrix = text["matrix"]
assert "| Desktop | `1.0.0` | Full applicable desktop workspace" in matrix
assert "| Goals | FULL | FULL | FULL |" in matrix
assert "| Full material viewing | FULL | FULL | FULL |" in matrix
assert "| Global search | FULL | FULL | FULL |" in matrix
assert "| Optional provider sync | FULL | PLATFORM_SPECIFIC | PLATFORM_SPECIFIC |" in matrix
assert "DOV08-DOV24 is complete" in matrix

remaining = json.loads(text["remaining"])
desktop = next(row for row in remaining["remaining_tracks"] if row["id"] == "DOV08-DOV24")
assert desktop["status"] == "complete"
assert desktop["completed_through"] == "DOV24"

for key in ("root_gradle", "desktop_gradle"):
    assert "verifyDesktopDov24FullApplicableParitySeal" in text[key]
    assert "verifyDesktopDov20Dov24Batch" in text[key]
assert '"verifyDesktopDov24FullApplicableParitySeal"' in text["devtool"]
assert 'packageVersion = "1.0.0"' in text["desktop_gradle"]
print("DESKTOP_DOV24_FULL_APPLICABLE_PARITY_SEAL: PASS")
