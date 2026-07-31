#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required = [
    "feature/subjects/build.gradle.kts",
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt",
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt",
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentUiModels.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyContentRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyContent.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyProgressCalculator.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SyllabusImportParser.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt",
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration1To2Test.kt",
    "docs/architecture/ADR-0010-syllabus-content-structure.md",
    "docs/quality/PHASE_5_ACCEPTANCE.md",
    "PHASE_5_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 5 file: {relative}")

settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:subjects"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:subjects")

app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if not any(marker in app_build for marker in ('versionName = "0.5.0-phase05"', 'versionName = "0.6.0-phase06"')):
    failures.append("app version is not Phase 5 or newer")
if 'implementation(project(":feature:subjects"))' not in app_build:
    failures.append(":app does not depend on :feature:subjects")

app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in (
    "SubjectsRoute",
    "StudyContentScreen",
    "manageStudyContent",
    "onOpenSyllabus",
):
    if marker not in app_source:
        failures.append(f"app navigation missing syllabus marker: {marker}")

application_source = (
    ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt"
).read_text(encoding="utf-8")
for marker in ("StudyContentRepository", "ManageStudyContent", "studyContentRepository"):
    if marker not in application_source:
        failures.append(f"application container missing content marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 5")

data_layer = (
    ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt"
).read_text(encoding="utf-8")
for marker in ("val studyContentRepository: StudyContentRepository", "RoomStudyContentRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose content repository correctly: {marker}")

model = (ROOT / "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyStructure.kt").read_text()
for marker in (
    "ContentProgressStatus",
    "StudySyllabus",
    "TopicNode",
    "SyllabusFilter",
    "SyllabusSort",
    "examStageId: ExamStageId? = null",
    "buildTopicTree",
):
    if marker not in model:
        failures.append(f"study structure model missing: {marker}")

domain_files = {
    "repository": "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyContentRepository.kt",
    "manager": "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyContent.kt",
    "progress": "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyProgressCalculator.kt",
    "parser": "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SyllabusImportParser.kt",
}
combined_domain = "\n".join((ROOT / path).read_text() for path in domain_files.values())
for marker in (
    "observeSyllabus",
    "reorderSubjects",
    "reorderTopics",
    "moveTopic",
    "bulkUpdateTopics",
    "importSyllabus",
    "parseStructuredText",
    "parseCsv",
    "weightedAverage",
):
    if marker not in combined_domain:
        failures.append(f"domain content contract missing: {marker}")

entities = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/StudyFlowEntities.kt").read_text()
for marker in (
    "val examStageId: String? = null",
    'Index("examStageId")',
):
    if marker not in entities:
        failures.append(f"entity schema missing optional exam stage marker: {marker}")

database = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt").read_text()
if "const val SCHEMA_VERSION = 2" not in database:
    failures.append("Room schema version must be 2 for Phase 5")
if "addMigrations(*StudyFlowMigrations.All)" not in database:
    failures.append("database builder does not install Phase 5 migrations")

migrations = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt").read_text()
for marker in (
    "Migration(1, 2)",
    "ALTER TABLE subjects ADD COLUMN examStageId",
    "ALTER TABLE topics ADD COLUMN examStageId",
    "index_subjects_examStageId",
    "index_topics_examStageId",
):
    if marker not in migrations:
        failures.append(f"migration 1->2 missing: {marker}")

repo = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt").read_text()
for marker in (
    "database.withTransaction",
    "validateParent",
    "validateExamStage",
    "recalculateProgress",
    "StudyProgressCalculator.subjectProgress",
    "StudyProgressCalculator.planProgress",
):
    if marker not in repo:
        failures.append(f"Room content repository missing: {marker}")

portability = (
    ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt"
).read_text()
if '"examStageId" !in this' not in portability:
    failures.append("Phase 4 package compatibility does not normalize missing examStageId")

duplicator = (
    ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt"
).read_text()
if 'remap = setOf("id", "examStageId")' not in duplicator:
    failures.append("subject duplication does not remap examStageId")
if 'remap = setOf("id", "subjectId", "parentTopicId", "examStageId")' not in duplicator:
    failures.append("topic duplication does not remap examStageId")

subjects_screen = (ROOT / required[1]).read_text()
for marker in (
    "SyllabusFilter.entries",
    "SyllabusSort.entries",
    "SubjectEditorDialog",
    "TopicEditorDialog",
    "BulkEditDialog",
    "ImportSyllabusDialog",
    "pluralStringResource",
):
    if marker not in subjects_screen:
        failures.append(f"syllabus screen missing: {marker}")

# Lint seal for the issue reported after Phase 4 r3: resource value lookups inside
# Compose must use stringResource/pluralStringResource instead of context resources.
compose_roots = [ROOT / "app/src/main/java", ROOT / "feature"]
resource_lookup_patterns = (
    ".getString(R.",
    ".resources.getString(R.",
    ".getQuantityString(R.",
    ".resources.getQuantityString(R.",
)
for root in compose_roots:
    if not root.exists():
        continue
    for path in root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        if "@Composable" not in source:
            continue
        for pattern in resource_lookup_patterns:
            if pattern in source:
                failures.append(f"{path.relative_to(ROOT)} uses resource lookup pattern {pattern}")

# Regression seal for explicit scoped Compose modifier imports that broke Phase 3.
for path in ROOT.rglob("*.kt"):
    if any(part in {"build", ".gradle", ".idea"} for part in path.parts):
        continue
    source = path.read_text(encoding="utf-8")
    for scoped in ("align", "alignBy", "alignByBaseline", "matchParentSize", "weight"):
        forbidden = f"import androidx.compose.foundation.layout.{scoped}"
        if forbidden in source:
            failures.append(f"{path.relative_to(ROOT)} imports scoped modifier {scoped}")

# English and pt-BR parity for the new feature.
def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }

en = ROOT / "feature/subjects/src/main/res/values/strings.xml"
pt = ROOT / "feature/subjects/src/main/res/values-pt-rBR/strings.xml"
if resource_keys(en) != resource_keys(pt):
    failures.append("feature:subjects translation resources are not in parity")
for plurals in ET.parse(pt).getroot().findall("plurals"):
    quantities = {item.attrib.get("quantity") for item in plurals.findall("item")}
    if "many" not in quantities:
        failures.append(f"feature:subjects plural {plurals.attrib['name']} missing Portuguese quantity many")

# The overlay deliberately leaves .devtool.toml untouched. Focused test tasks are
# supplied through repeated --task arguments when applying the overlay, while
# checkAll keeps a repository-local safety net for normal Gradle validation.

root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ":feature:subjects:testDebugUnitTest" not in root_build:
    failures.append("checkAll does not include feature:subjects tests")

validate_script = (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8")
if "verify_phase_5.py" not in validate_script:
    failures.append("validate_repo.sh does not run Phase 5 contract")

if failures:
    print("PHASE_5_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print("PHASE_5_CONTRACT: PASSED (syllabus, migration, content UI, lint seal)")
