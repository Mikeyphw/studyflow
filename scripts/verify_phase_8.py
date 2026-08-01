#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required = [
    "feature/practice/build.gradle.kts",
    "feature/practice/consumer-rules.pro",
    "feature/practice/src/main/AndroidManifest.xml",
    "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt",
    "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeUiModels.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeAnalytics.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyPracticeRepository.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPracticeRepository.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPracticeEntityMapper.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PracticeAnalyticsTest.kt",
    "feature/practice/src/test/java/com/mikeyphw/studyflow/feature/practice/PracticeUiModelsTest.kt",
    "docs/architecture/ADR-0013-question-bank-practice.md",
    "docs/quality/PHASE_8_ACCEPTANCE.md",
    "PHASE_8_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 8 file: {relative}")

settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:practice"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:practice")

app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if not any(marker in app_build for marker in (
    'versionName = "0.8.0-phase08"',
    'versionName = "0.9.0-phase09"',
    'versionName = "0.11.0-phase11"',
    'versionName = "0.10.0-phase10"',
)):
    failures.append("app version is not Phase 8 or later")
if 'implementation(project(":feature:practice"))' not in app_build:
    failures.append(":app does not depend on :feature:practice")

root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ':feature:practice:testDebugUnitTest' not in root_build:
    failures.append("checkAll does not include feature:practice tests")

app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("PracticeRoute", "PracticeScreen", "manageStudyPractice", "onOpenPractice"):
    if marker not in app_source:
        failures.append(f"app navigation missing practice marker: {marker}")

application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
for marker in ("StudyPracticeRepository", "ManageStudyPractice", "studyPracticeRepository"):
    if marker not in application_source:
        failures.append(f"application container missing practice marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 8")

data_layer = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt").read_text(
    encoding="utf-8",
)
for marker in ("val studyPracticeRepository: StudyPracticeRepository", "RoomStudyPracticeRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose practice repository correctly: {marker}")

practice_dao = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPracticeDao.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "upsertQuestion",
    "findQuestion",
    "deleteQuestion",
    "findTest",
    "findMockExam",
    "observeQuestions",
):
    if marker not in practice_dao:
        failures.append(f"practice DAO missing operation: {marker}")

practice_mapper = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPracticeEntityMapper.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "QuestionEntity.asExternalModel",
    "Question.asEntity",
    "StudyTestEntity.asExternalModel",
    "MockExam.asEntity",
    "Base64",
):
    if marker not in practice_mapper:
        failures.append(f"practice mapper missing marker: {marker}")

repo = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPracticeRepository.kt").read_text(
    encoding="utf-8",
)
for marker in ("database.withTransaction", "requireSubject", "requireTopic", "deleteQuestion"):
    if marker not in repo:
        failures.append(f"Room practice repository missing: {marker}")

manager = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "createQuestion",
    "toggleFavorite",
    "toggleDisputed",
    "createPracticeTest",
    "submitTest",
    "createMockExam",
    "submitMockExam",
):
    if marker not in manager:
        failures.append(f"practice manager missing operation: {marker}")

screen = (ROOT / "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt").read_text(
    encoding="utf-8",
)
for marker in ("collectAsStateWithLifecycle", "stringResource", "QuestionEditorDialog", "createPracticeTest"):
    if marker not in screen:
        failures.append(f"practice screen missing: {marker}")

# Translation parity.
def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }

en = ROOT / "feature/practice/src/main/res/values/strings.xml"
pt = ROOT / "feature/practice/src/main/res/values-pt-rBR/strings.xml"
if resource_keys(en) != resource_keys(pt):
    failures.append("feature:practice translation resources are not in parity")

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
if "verify_phase_8.py" not in validate_script:
    failures.append("validate_repo.sh does not run Phase 8 contract")

if failures:
    print("PHASE_8_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print("PHASE_8_CONTRACT: PASSED (question bank, practice tests, mocks, warning seals)")
