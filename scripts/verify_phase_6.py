#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required = [
    "feature/schedule/build.gradle.kts",
    "feature/schedule/consumer-rules.pro",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleDialogs.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleUiModels.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyScheduleRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySchedule.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySchedulePlanner.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyScheduleRepository.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPlanningEntityMapper.kt",
    "docs/architecture/ADR-0011-study-schedule-strategy.md",
    "docs/quality/PHASE_6_ACCEPTANCE.md",
    "PHASE_6_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 6 file: {relative}")

settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:schedule"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:schedule")

app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if not phase_version_at_least(ROOT, 6):
    failures.append("app version is not Phase 6 or later")
if 'implementation(project(":feature:schedule"))' not in app_build:
    failures.append(":app does not depend on :feature:schedule")

app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("ScheduleRoute", "StudyScheduleScreen", "manageStudySchedule", "onOpenSchedule"):
    if marker not in app_source:
        failures.append(f"app navigation missing schedule marker: {marker}")

application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
for marker in ("StudyScheduleRepository", "ManageStudySchedule", "studyScheduleRepository"):
    if marker not in application_source:
        failures.append(f"application container missing schedule marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 6")

data_layer = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt").read_text(encoding="utf-8")
for marker in ("val studyScheduleRepository: StudyScheduleRepository", "RoomStudyScheduleRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose schedule repository correctly: {marker}")

planning_model = (ROOT / "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPlanning.kt").read_text(encoding="utf-8")
for marker in (
    "MissedActivityHandling",
    "StudySchedule",
    "ScheduleGenerationRequest",
    "ScheduleGenerationResult",
    "RebalanceResult",
):
    if marker not in planning_model:
        failures.append(f"planning model missing: {marker}")

planning_dao = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPlanningDao.kt").read_text(encoding="utf-8")
for marker in (
    "observeStrategy",
    "observeScheduleRules",
    "findScheduleRules",
    "findPlannedActivities",
    "deleteUnlockedPlannedActivities",
    "deleteScheduleRule",
    "deletePlannedActivity",
):
    if marker not in planning_dao:
        failures.append(f"planning DAO missing: {marker}")

schedule_repo = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyScheduleRepository.kt").read_text(encoding="utf-8")
for marker in (
    "database.withTransaction",
    "combine(",
    "replaceUnlockedActivities",
    "updatePlannedActivityStatus",
    "requireSubject",
    "requireTopic",
):
    if marker not in schedule_repo:
        failures.append(f"Room schedule repository missing: {marker}")

schedule_screen = (ROOT / "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt").read_text(encoding="utf-8")
for marker in (
    "StudyStrategyType.entries",
    "generateActivities",
    "rebalanceMissed",
    "completeActivity",
    "markActivityMissed",
    "pluralStringResource",
):
    if marker not in schedule_screen:
        failures.append(f"schedule screen missing: {marker}")

# Regression seal for the warnings reported after Phase 4+5 bridge r2.
subjects_strings = (ROOT / "feature/subjects/src/main/res/values/strings.xml").read_text(encoding="utf-8")
for unused in (
    "sort_content",
    "clear_exam_stage",
    "difficulty_value",
    "minutes_value",
    "invalid_number",
    "required_name",
):
    if f'name="{unused}"' in subjects_strings:
        failures.append(f"unused subject string remains: {unused}")

# Translation parity for the new schedule feature.
def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }

en = ROOT / "feature/schedule/src/main/res/values/strings.xml"
pt = ROOT / "feature/schedule/src/main/res/values-pt-rBR/strings.xml"
if resource_keys(en) != resource_keys(pt):
    failures.append("feature:schedule translation resources are not in parity")
for plurals in ET.parse(pt).getroot().findall("plurals"):
    quantities = {item.attrib.get("quantity") for item in plurals.findall("item")}
    if "many" not in quantities:
        failures.append(f"feature:schedule plural {plurals.attrib['name']} missing Portuguese quantity many")

# Compose safety seals from prior incidents.
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

root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ":feature:schedule:testDebugUnitTest" not in root_build:
    failures.append("checkAll does not include feature:schedule tests")

validate_script = (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8")
if "verify_phase_6.py" not in validate_script:
    failures.append("validate_repo.sh does not run Phase 6 contract")

if failures:
    print("PHASE_6_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print("PHASE_6_CONTRACT: PASSED (schedule, strategy, recovery, warning seal)")
