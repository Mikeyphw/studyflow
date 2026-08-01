#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required = [
    "feature/sessions/build.gradle.kts",
    "feature/sessions/consumer-rules.pro",
    "feature/sessions/src/main/AndroidManifest.xml",
    "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt",
    "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionDialogs.kt",
    "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionUiModels.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySessionRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySessionTimer.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudySessionRepository.kt",
    "docs/architecture/ADR-0012-study-session-execution.md",
    "docs/quality/PHASE_7_ACCEPTANCE.md",
    "PHASE_7_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 7 file: {relative}")

settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:sessions"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:sessions")

app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if not any(marker in app_build for marker in (
    'versionName = "0.7.0-phase07"',
    'versionName = "0.8.0-phase08"',
    'versionName = "0.9.0-phase09"',
    'versionName = "0.11.0-phase11"',
    'versionName = "0.10.0-phase10"',
)):
    failures.append("app version is not Phase 7 or later")
if 'implementation(project(":feature:sessions"))' not in app_build:
    failures.append(":app does not depend on :feature:sessions")

root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ':feature:sessions:testDebugUnitTest' not in root_build:
    failures.append("checkAll does not include feature:sessions tests")

app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("SessionsRoute", "StudySessionScreen", "manageStudySession", "onOpenSessions"):
    if marker not in app_source:
        failures.append(f"app navigation missing session marker: {marker}")

application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
for marker in ("StudySessionRepository", "ManageStudySession", "studySessionRepository"):
    if marker not in application_source:
        failures.append(f"application container missing session marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 7")

data_layer = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt").read_text(
    encoding="utf-8",
)
for marker in ("val studySessionRepository: StudySessionRepository", "RoomStudySessionRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose session repository correctly: {marker}")

planning_dao = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPlanningDao.kt").read_text(
    encoding="utf-8",
)
for marker in ("upsertStudySession", "findStudySession", "findActiveStudySession", "observeStudySessions"):
    if marker not in planning_dao:
        failures.append(f"planning DAO missing session marker: {marker}")

planning_mapper = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPlanningEntityMapper.kt").read_text(
    encoding="utf-8",
)
for marker in ("StudySessionEntity.asExternalModel", "StudySession.asEntity", "StudySessionStatus"):
    if marker not in planning_mapper:
        failures.append(f"planning mapper missing session marker: {marker}")

session_repo = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudySessionRepository.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "database.withTransaction",
    "linkedActivityStatus",
    "findActiveStudySession",
    "findPlannedActivity",
    "requireSubject",
    "requireTopic",
):
    if marker not in session_repo:
        failures.append(f"Room session repository missing: {marker}")

manager = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt").read_text(
    encoding="utf-8",
)
for marker in ("startQuickSession", "startFromActivity", "pause", "resume", "complete", "cancel"):
    if marker not in manager:
        failures.append(f"session manager missing operation: {marker}")

screen = (ROOT / "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "StudySessionTimer.activeSeconds",
    "startFromActivity",
    "startQuickSession",
    "FinishSessionDialog",
    "pluralStringResource",
):
    if marker not in screen:
        failures.append(f"session screen missing: {marker}")

# Translation parity and Portuguese plural completeness.
def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }

en = ROOT / "feature/sessions/src/main/res/values/strings.xml"
pt = ROOT / "feature/sessions/src/main/res/values-pt-rBR/strings.xml"
if resource_keys(en) != resource_keys(pt):
    failures.append("feature:sessions translation resources are not in parity")
for plurals in ET.parse(pt).getroot().findall("plurals"):
    quantities = {item.attrib.get("quantity") for item in plurals.findall("item")}
    if "many" not in quantities:
        failures.append(f"feature:sessions plural {plurals.attrib['name']} missing Portuguese quantity many")

# Prior Compose safety seals stay active.
for path in ROOT.rglob("*.kt"):
    if any(part in {"build", ".gradle", ".idea"} for part in path.parts):
        continue
    source = path.read_text(encoding="utf-8")
    for scoped in ("align", "alignBy", "alignByBaseline", "matchParentSize", "weight"):
        forbidden = f"import androidx.compose.foundation.layout.{scoped}"
        if forbidden in source:
            failures.append(f"{path.relative_to(ROOT)} imports scoped modifier {scoped}")
    if "@Composable" in source:
        for pattern in (".getString(R.", ".resources.getString(R.", ".getQuantityString(R."):
            if pattern in source:
                failures.append(f"{path.relative_to(ROOT)} uses resource lookup pattern {pattern}")

validate_script = (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8")
if "verify_phase_7.py" not in validate_script:
    failures.append("validate_repo.sh does not run Phase 7 contract")

if failures:
    print("PHASE_7_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print("PHASE_7_CONTRACT: PASSED (sessions, timer execution, planned activity linkage)")
