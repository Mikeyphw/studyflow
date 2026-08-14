#!/usr/bin/env python3
from pathlib import Path
import sys

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
failures = []

def text(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        failures.append(f"missing {path}")
        return ""
    return target.read_text(encoding="utf-8")

app_build = text("app/build.gradle.kts")
if not phase_version_at_least(ROOT, 20):
    failures.append("app version is not Phase 20 or later")

remediator = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/WrongAnswerRemediation.kt")
for marker in (
    "object WrongAnswerRemediator",
    "data class WrongAnswerNotebook",
    "enum class RemediationPriority",
    "enum class RemediationSuggestion",
    "priorityFor",
    "suggestionFor",
):
    if marker not in remediator:
        failures.append(f"wrong-answer remediator missing marker: {marker}")

review_manager = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyReview.kt")
for marker in (
    "observeWrongAnswerNotebook",
    "recordWrongAnswer",
    "scheduleErrorReview",
    "resolveErrorEntry",
):
    if marker not in review_manager:
        failures.append(f"ManageStudyReview missing remediation marker: {marker}")

practice_engine = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt")
for marker in (
    "repository.submitTestIfOpen(",
    "repository.submitMockExamIfOpen(",
    "wrongAnswers = wrongAnswers(state)",
    "PracticeWrongAnswerCapture",
):
    if marker not in practice_engine:
        failures.append(f"Practice execution engine missing atomic wrong-answer capture marker: {marker}")

practice_repository = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPracticeRepository.kt")
for marker in (
    "database.withTransaction",
    "wrongAnswers.forEach { capture -> captureWrongAnswer(capture, submittedAt) }",
    "private suspend fun captureWrongAnswer",
    "ErrorEntry(",
    "ReviewItem(",
    ").asEntity()",
):
    if marker not in practice_repository:
        failures.append(f"Room practice repository missing transactional remediation marker: {marker}")

practice_screen = text("feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt")
if "captureWrongAnswersFromTest" in practice_screen or "captureWrongAnswersFromMockExam" in practice_screen:
    failures.append("Practice screen must not duplicate terminal wrong-answer capture outside the repository transaction")

review_screen = text("feature/reviews/src/main/java/com/mikeyphw/studyflow/feature/reviews/StudyReviewScreen.kt")
for marker in (
    "StudyFlowSectionCard(title = stringResource(R.string.wrong_answer_notebook))",
    "WrongAnswerRow",
    "scheduleErrorReview",
    "resolveErrorEntry",
    "wrong_answer_notebook",
):
    if marker not in review_screen:
        failures.append(f"Review screen missing notebook marker: {marker}")

dao = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyRetentionDao.kt")
for marker in (
    "findErrorEntryForQuestion",
    "deleteReviewsForErrorEntry",
    "deleteErrorEntry",
):
    if marker not in dao:
        failures.append(f"StudyRetentionDao missing marker: {marker}")

strings_en = text("feature/reviews/src/main/res/values/strings.xml")
strings_pt = text("feature/reviews/src/main/res/values-pt-rBR/strings.xml")
for marker in (
    "wrong_answer_notebook",
    "schedule_error_review",
    "resolve_error",
    "priority_critical",
):
    if marker not in strings_en or marker not in strings_pt:
        failures.append(f"review strings missing bilingual marker: {marker}")

if "python3 scripts/verify_phase_20.py" not in text("scripts/validate_repo.sh"):
    failures.append("validate_repo.sh does not run Phase 20 verifier")

if failures:
    print("PHASE_20_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_20_CONTRACT: PASSED (wrong-answer notebook and remediation loop)")
