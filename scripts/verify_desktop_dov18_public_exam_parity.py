#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "repository": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPublicExamPersistence.kt",
    "controller": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPublicExamController.kt",
    "screen": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPublicExamScreen.kt",
    "test": ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPublicExamParityPersistenceTest.kt",
    "services": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt",
    "session": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudySessionRepository.kt",
    "app_state": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
    "app": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt",
    "matrix": ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "root_gradle": ROOT / "build.gradle.kts",
    "desktop_gradle": ROOT / "desktopApp/build.gradle.kts",
    "devtool": ROOT / ".devtool.toml",
}
for label, path in checks.items():
    assert path.is_file(), f"missing {label}: {path}"
text = {key: value.read_text() for key, value in checks.items()}
repo = text["repository"]
for token in (
    "class DesktopPublicExamRepository",
    ": PublicExamRepository",
    "ExamNoticeRevisionPlanner.preview",
    "StudyCyclePlanner.currentEligibleItem",
    "public-exam-v1.properties",
    "sessionBindings",
    "LegislationEvidenceLink",
):
    assert token in repo, f"missing public-exam repository contract: {token}"
assert "configureCycleIntegration" in text["session"]
assert "DesktopPublicExamPersistence.open" in text["services"]
assert "publicExamRepository.onSessionTerminal" in text["services"]
assert "PublicExamDashboardBuilder.build" in text["controller"]
assert "PublicExamFinalStretchPlanner.plan" in text["controller"]

# Compile-safety guards for Desktop's Compose Material and shared content manager contracts.
assert "androidx.compose.material3" not in text["screen"]
for token in (
    "androidx.compose.material.Button",
    "androidx.compose.material.Card",
    "androidx.compose.material.OutlinedTextField",
    "androidx.compose.material.Text",
):
    assert token in text["screen"], f"Desktop public-exam screen must use Compose Material: {token}"
assert "services.manageStudyContent.saveStudyTarget(target)" in text["controller"]
assert "services.manageStudyContent.upsertStudyTarget(target)" not in text["controller"]
for token in (
    "val incomingSubjectKey = change.incomingSubjectKey",
    "val existingSubjectId = change.existingSubjectId",
    "incomingSubjects[incomingSubjectKey] = existingSubjectId",
):
    assert token in repo, f"missing cross-module smart-cast-safe revision mapping: {token}"
assert 'PUBLIC_EXAM("Concurso")' in text["app_state"]
assert "DesktopRoute.PUBLIC_EXAM -> DesktopPublicExamScreen(appState)" in text["app"]
assert "Concurso Público workflows | FULL | FULL | FULL" in text["matrix"]
for gradle_key in ("root_gradle", "desktop_gradle"):
    assert "verifyDesktopDov18PublicExamParity" in text[gradle_key]
assert '"verifyDesktopDov18PublicExamParity"' in text["devtool"]
test = text["test"]
for token in ("public-exam-v1.properties", "StudySessionStatus.COMPLETED", "sessionBindings.isEmpty()"):
    assert token in test
print("DESKTOP_DOV18_PUBLIC_EXAM_PARITY: PASS")
