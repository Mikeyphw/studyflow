#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required_files = [
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPlan.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPlanSettingsEntityMapper.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanSettingsScreen.kt",
    "docs/architecture/ADR-0007-study-plan-lifecycle.md",
    "docs/quality/PHASE_3_ACCEPTANCE.md",
    "PHASE_3_IMPLEMENTATION.md",
]
for relative in required_files:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 3 file: {relative}")

repository = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyPlanRepository.kt").read_text()
for contract in (
    "observePlanSettings",
    "duplicate(",
    "updateStatus(",
    "moveToTrash",
    "restoreFromTrash",
    "updatePlanSettings",
    "deletePermanently",
):
    if contract not in repository:
        failures.append(f"StudyPlanRepository missing lifecycle contract: {contract}")

manager = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPlan.kt").read_text()
for action in (
    "suspend fun update(",
    "suspend fun duplicate(",
    "suspend fun activate(",
    "suspend fun pause(",
    "suspend fun archive(",
    "suspend fun restoreArchived(",
    "suspend fun moveToTrash(",
    "suspend fun restoreFromTrash(",
    "suspend fun deletePermanently(",
    "suspend fun updateSettings(",
):
    if action not in manager:
        failures.append(f"ManageStudyPlan missing action: {action}")

room_repository = (
    ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanRepository.kt"
).read_text()
for contract in (
    "StudyPlanGraphDuplicator",
    "database.withTransaction",
    "StudyPlanSettings.defaults",
    "StudyPlanStatus.TRASHED",
    "StudyPlanStatus.PAUSED",
):
    if contract not in room_repository:
        failures.append(f"RoomStudyPlanRepository missing Phase 3 contract: {contract}")

# Duplication must cover every plan-owned Phase 2 table and remap relationship-heavy ids.
duplicator = (
    ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt"
).read_text()
expected_tables = {
    "study_targets", "exam_stages", "subjects", "topics", "study_strategies",
    "schedule_rules", "study_materials", "video_resources", "planned_activities",
    "study_sessions", "study_goals", "questions", "study_tests",
    "mock_exams", "flashcards", "error_entries", "review_items", "study_notes",
    "study_plan_settings",
}
table_specs_body = duplicator.split('val TableSpecs = listOf(', 1)[1].split('/**', 1)[0]
found_tables = set(re.findall(r'^\s*"([a-z_]+)",\s*$', table_specs_body, re.MULTILINE))
missing_tables = expected_tables - found_tables
if missing_tables:
    failures.append(f"graph duplicator missing tables: {sorted(missing_tables)}")
for reference in (
    '"plannedActivityId"', '"videoResourceId"', '"parentTopicId"',
    '"questionId"', '"flashcardId"', '"errorEntryId"',
):
    if reference not in duplicator:
        failures.append(f"graph duplicator missing reference remap: {reference}")
if "quoteIdentifier" not in duplicator:
    failures.append("graph duplication SQL must quote identifiers")

# File-backed annexes and snapshots require physical byte copies and therefore must not be copied by SQL.
physical_duplicator = (
    ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/PlanPhysicalResourceDuplicator.kt"
).read_text()
for marker in ("findAnnexes(sourcePlanId)", "findSnapshots(sourcePlanId)", "sourceFile.copyTo(destination",
               "payloadReference = destination.relativeTo(filesDir)"):
    if marker not in physical_duplicator:
        failures.append(f"physical plan duplication missing file-backed ownership contract: {marker}")
for table in ("annexes", "plan_snapshots"):
    if f'"{table}",' in table_specs_body:
        failures.append(f"file-backed table must not be SQL-duplicated: {table}")

preferences_model = (ROOT / "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/AppPreferences.kt").read_text()
preferences_repo = (
    ROOT / "core/i18n/src/main/java/com/mikeyphw/studyflow/core/i18n/DataStoreAppPreferencesRepository.kt"
).read_text()
if "defaultStudyPlanId" not in preferences_model:
    failures.append("AppPreferences does not store the default Study Plan")
for marker in ("DefaultStudyPlanKey", "setDefaultStudyPlan", "default_study_plan_id"):
    if marker not in preferences_repo:
        failures.append(f"DataStore default-plan support missing: {marker}")

routes = (ROOT / "core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt").read_text()
for route in ("EditPlanRoute", "PlanSettingsRoute"):
    if route not in routes:
        failures.append(f"navigation route missing: {route}")

plan_editor = (
    ROOT / "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/CreatePlanScreen.kt"
).read_text()
for marker in (
    "WizardStepCount = 3",
    "PlanEditorForm",
    "ObjectiveStep",
    "TargetStep",
    "PreferencesStep",
    "isValidIsoDateOrBlank",
):
    if marker not in plan_editor:
        failures.append(f"plan editor missing guided-wizard contract: {marker}")

plan_list = (
    ROOT / "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt"
).read_text()
for marker in (
    "PlanCollectionFilter",
    "StudyPlanAction",
    "SET_DEFAULT",
    "DUPLICATE",
    "MOVE_TO_TRASH",
    "RESTORE_FROM_TRASH",
    "DELETE_PERMANENTLY",
    "daysUntilExam",
):
    if marker not in plan_list:
        failures.append(f"plan list missing management contract: {marker}")

app = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text()
for marker in (
    "preferences.defaultStudyPlanId",
    "container.manageStudyPlan.duplicate",
    "container.manageStudyPlan.moveToTrash",
    "container.manageStudyPlan.deletePermanently",
    "PlanSettingsScreen",
    "PlanEditorScreen",
):
    if marker not in app:
        failures.append(f"app navigation missing lifecycle wiring: {marker}")

# Compose scoped modifiers are member extensions of RowScope, ColumnScope, or
# BoxScope. Explicit imports can resolve to inaccessible internal parent-data
# symbols under AGP 9 built-in Kotlin instead of the public scoped extension.
scoped_modifier_imports = (
    "align",
    "alignBy",
    "alignByBaseline",
    "matchParentSize",
    "weight",
)
for path in sorted(ROOT.rglob("*.kt")):
    if any(part in {"build", ".gradle", ".idea"} for part in path.parts):
        continue
    source = path.read_text()
    relative = path.relative_to(ROOT).as_posix()
    for symbol in scoped_modifier_imports:
        forbidden = f"import androidx.compose.foundation.layout.{symbol}"
        if forbidden in source:
            failures.append(
                f"{relative} explicitly imports scoped Compose modifier {symbol}"
            )

# Backup files must use only valid domains. Cache is implicitly excluded by Android.
for relative in (
    "app/src/main/res/xml/backup_rules.xml",
    "app/src/main/res/xml/data_extraction_rules.xml",
):
    path = ROOT / relative
    try:
        ET.parse(path)
    except ET.ParseError as error:
        failures.append(f"invalid XML {relative}: {error}")
    if 'domain="cache"' in path.read_text():
        failures.append(f"{relative} contains invalid backup domain cache")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
if 'tools:targetApi="33"' not in manifest:
    failures.append("manifest does not scope localeConfig to API 33")

app_build = (ROOT / "app/build.gradle.kts").read_text()
if not re.search(r'versionCode\s*=\s*(?:[4-9]|[1-9][0-9]+)', app_build):
    failures.append("app version predates the landed Phase 3 baseline")
for advisory in ("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable"):
    if advisory not in app_build:
        failures.append(f"version-advisory lint suppression missing: {advisory}")

# Translation resources must remain key-for-key aligned, including plurals.
def resource_names(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {(child.tag, child.attrib.get("name", "")) for child in root if child.attrib.get("name")}

for module in ("app", "feature/plans", "feature/dashboard"):
    english = ROOT / module / "src/main/res/values/strings.xml"
    portuguese = ROOT / module / "src/main/res/values-pt-rBR/strings.xml"
    if resource_names(english) != resource_names(portuguese):
        failures.append(f"translation resource mismatch in {module}")

try:
    devtool = tomllib.loads((ROOT / ".devtool.toml").read_text())
    target = devtool["targets"]["studyflow_android"]
    test_tasks = set(target.get("tasks", {}).get("test", []))
    phase_tasks = set(target.get("validation", {}).get("phases", {}).get("unit-test", []))
    if ":feature:plans:testDebugUnitTest" not in test_tasks | phase_tasks:
        failures.append("Devtool test coverage does not include feature:plans")
except (OSError, KeyError, tomllib.TOMLDecodeError) as error:
    failures.append(f"unable to validate .devtool.toml: {error}")

if failures:
    print("PHASE_3_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print(f"PHASE_3_CONTRACT: PASSED ({len(expected_tables)} duplicated plan-owned tables)")
