#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
required = [
    "feature/progress/build.gradle.kts",
    "feature/progress/consumer-rules.pro",
    "feature/progress/src/main/AndroidManifest.xml",
    "feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/StudyProgressScreen.kt",
    "feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/ProgressUiModels.kt",
    "feature/progress/src/test/java/com/mikeyphw/studyflow/feature/progress/ProgressUiModelsTest.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyProgressRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyProgress.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/GoalProgressCalculator.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyProgressRepository.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyProgressEntityMapper.kt",
    "docs/architecture/ADR-0016-goals-notes-progress.md",
    "docs/quality/PHASE_11_ACCEPTANCE.md",
    "PHASE_11_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 11 file: {relative}")
settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:progress"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:progress")
app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
expected_versions = [
    'versionName = "0.11.0-phase11"',
    'versionName = "0.12.0-phase12"',
    'versionName = "0.13.0-phase13"',
]
if not any(marker in app_build for marker in expected_versions):
    failures.append("app version is not Phase 11 or later")
if 'implementation(project(":feature:progress"))' not in app_build:
    failures.append(":app does not depend on :feature:progress")
root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ':feature:progress:testDebugUnitTest' not in root_build:
    failures.append("checkAll does not include feature:progress tests")
app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("ProgressRoute", "StudyProgressScreen", "manageStudyProgress", "onOpenProgress"):
    if marker not in app_source:
        failures.append(f"app navigation missing progress marker: {marker}")
application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
for marker in ("StudyProgressRepository", "ManageStudyProgress", "studyProgressRepository"):
    if marker not in application_source:
        failures.append(f"application container missing progress marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 11")
data_layer = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt").read_text(encoding="utf-8")
for marker in ("val studyProgressRepository: StudyProgressRepository", "RoomStudyProgressRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose progress repository correctly: {marker}")
planning_dao = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPlanningDao.kt").read_text(encoding="utf-8")
for marker in ("upsertStudyGoal", "observeStudyGoals", "findStudyGoal", "deleteStudyGoal"):
    if marker not in planning_dao:
        failures.append(f"planning DAO missing goal operation: {marker}")
support_dao = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudySupportDao.kt").read_text(encoding="utf-8")
for marker in ("upsertNote", "findNote", "deleteNote", "observeNotes"):
    if marker not in support_dao:
        failures.append(f"support DAO missing note operation: {marker}")
mapper = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyProgressEntityMapper.kt").read_text(encoding="utf-8")
for marker in ("StudyGoalEntity.asExternalModel", "StudyGoal.asEntity", "StudyNoteEntity.asExternalModel", "StudyNote.asEntity", "Base64"):
    if marker not in mapper:
        failures.append(f"progress mapper missing marker: {marker}")
manager = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyProgress.kt").read_text(encoding="utf-8")
for marker in ("createGoal", "updateGoalProgress", "archiveGoal", "createNote", "togglePinnedNote"):
    if marker not in manager:
        failures.append(f"progress manager missing operation: {marker}")

def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {(node.tag, node.attrib["name"]) for node in root if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"}
if resource_keys(ROOT / "feature/progress/src/main/res/values/strings.xml") != resource_keys(ROOT / "feature/progress/src/main/res/values-pt-rBR/strings.xml"):
    failures.append("feature:progress translation resources are not in parity")
for path in ROOT.rglob("*.kt"):
    if any(part in {"build", ".gradle", ".idea"} for part in path.parts):
        continue
    source = path.read_text(encoding="utf-8")
    for scoped in ("align", "alignBy", "alignByBaseline", "matchParentSize", "weight"):
        if f"import androidx.compose.foundation.layout.{scoped}" in source:
            failures.append(f"{path.relative_to(ROOT)} imports scoped modifier {scoped}")
    if "@Composable" in source:
        for pattern in (".getString(R.", ".resources.getString(R.", ".getQuantityString(R."):
            if pattern in source:
                failures.append(f"{path.relative_to(ROOT)} uses resource lookup pattern {pattern}")
if "verify_phase_11.py" not in (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8"):
    failures.append("validate_repo.sh does not run Phase 11 contract")
if failures:
    print("PHASE_11_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_11_CONTRACT: PASSED (goals, notes, progress command center, warning seals)")
