#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required = [
    "feature/reviews/build.gradle.kts",
    "feature/reviews/consumer-rules.pro",
    "feature/reviews/src/main/AndroidManifest.xml",
    "feature/reviews/src/main/java/com/mikeyphw/studyflow/feature/reviews/StudyReviewScreen.kt",
    "feature/reviews/src/main/java/com/mikeyphw/studyflow/feature/reviews/ReviewUiModels.kt",
    "feature/reviews/src/test/java/com/mikeyphw/studyflow/feature/reviews/ReviewUiModelsTest.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReviewRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyReview.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SpacedRepetitionScheduler.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/SpacedRepetitionSchedulerTest.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyReviewRepository.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyRetentionEntityMapper.kt",
    "docs/architecture/ADR-0014-reviews-spaced-repetition.md",
    "docs/quality/PHASE_9_ACCEPTANCE.md",
    "PHASE_9_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 9 file: {relative}")

settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:reviews"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:reviews")

app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if 'versionName = "0.9.0-phase09"' not in app_build:
    failures.append("app version is not Phase 9")
if 'implementation(project(":feature:reviews"))' not in app_build:
    failures.append(":app does not depend on :feature:reviews")

root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ':feature:reviews:testDebugUnitTest' not in root_build:
    failures.append("checkAll does not include feature:reviews tests")

app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("ReviewsRoute", "StudyReviewScreen", "manageStudyReview", "onOpenReviews"):
    if marker not in app_source:
        failures.append(f"app navigation missing review marker: {marker}")

application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
for marker in ("StudyReviewRepository", "ManageStudyReview", "studyReviewRepository"):
    if marker not in application_source:
        failures.append(f"application container missing review marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 9")

data_layer = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt").read_text(
    encoding="utf-8",
)
for marker in ("val studyReviewRepository: StudyReviewRepository", "RoomStudyReviewRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose review repository correctly: {marker}")

retention_dao = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyRetentionDao.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "upsertReviewItem",
    "findReviewItem",
    "deleteReviewItem",
    "upsertFlashcard",
    "findFlashcard",
    "deleteFlashcard",
    "findErrorEntry",
):
    if marker not in retention_dao:
        failures.append(f"retention DAO missing operation: {marker}")

mapper = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyRetentionEntityMapper.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "ReviewItemEntity.asExternalModel",
    "ReviewItem.asEntity",
    "FlashcardEntity.asExternalModel",
    "Flashcard.asEntity",
    "ErrorEntryEntity.asExternalModel",
    "Base64",
):
    if marker not in mapper:
        failures.append(f"retention mapper missing marker: {marker}")

repo = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyReviewRepository.kt").read_text(
    encoding="utf-8",
)
for marker in ("database.withTransaction", "requireSubject", "requireTopic", "deleteReviewItem"):
    if marker not in repo:
        failures.append(f"Room review repository missing: {marker}")

manager = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyReview.kt").read_text(
    encoding="utf-8",
)
for marker in (
    "createFlashcard",
    "scheduleTopicReview",
    "scheduleQuestionReview",
    "createErrorFromQuestion",
    "gradeReview",
    "skipReview",
):
    if marker not in manager:
        failures.append(f"review manager missing operation: {marker}")

scheduler = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SpacedRepetitionScheduler.kt").read_text(
    encoding="utf-8",
)
for marker in ("grade in 0..5", "MinimumEaseFactor", "nextIntervalDays", "ReviewQueue", "ReviewSummary"):
    if marker not in scheduler:
        failures.append(f"scheduler missing marker: {marker}")

screen = (ROOT / "feature/reviews/src/main/java/com/mikeyphw/studyflow/feature/reviews/StudyReviewScreen.kt").read_text(
    encoding="utf-8",
)
for marker in ("collectAsStateWithLifecycle", "stringResource", "createFlashcard", "gradeReview"):
    if marker not in screen:
        failures.append(f"reviews screen missing: {marker}")

# Translation parity.
def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }

en = ROOT / "feature/reviews/src/main/res/values/strings.xml"
pt = ROOT / "feature/reviews/src/main/res/values-pt-rBR/strings.xml"
if resource_keys(en) != resource_keys(pt):
    failures.append("feature:reviews translation resources are not in parity")

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
if "verify_phase_9.py" not in validate_script:
    failures.append("validate_repo.sh does not run Phase 9 contract")

if failures:
    print("PHASE_9_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print("PHASE_9_CONTRACT: PASSED (reviews, flashcards, spaced repetition, warning seals)")
