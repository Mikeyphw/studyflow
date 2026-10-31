#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = []

def require(path: str, *needles: str) -> None:
    text = (ROOT / path).read_text()
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"missing {needle!r} in {path}")
        checks.append((path, needle))

require(
    "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeHonestyContracts.kt",
    "questionAnswerChoices(question)",
    "QuestionType.SHORT_ANSWER",
    "QuestionType.ESSAY",
    "MULTIPLE_CHOICE_ANSWER_NOT_IN_OPTIONS",
    "TRUE_FALSE_ANSWER_NOT_BOOLEAN",
)
require(
    "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt",
    "questionRequiresTextAnswer(question)",
    "draftTextAnswer",
    "savingQuestion",
    "state.domainOptions()",
    "state.domainCorrectAnswer()",
    "QuestionType.entries.forEach",
)
text = (ROOT / "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt").read_text()
if "question.options.ifEmpty { listOf(question.correctAnswer) }" in text:
    raise SystemExit("S6-01 regression: optionless questions still expose the correct answer as a button")
checks.append(("feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt", "no answer leak fallback"))
require(
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
    "canOpenPlanFromList(plan)",
    "daysUntilExamLocalCalendar",
    "next_activity_unavailable",
)
require(
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanHonestyContracts.kt",
    "StudyPlanStatus.ARCHIVED",
    "Calendar.getInstance()",
    "truthfulNextActivityAvailable",
)
require(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/FocusSessionScreen.kt",
    "shouldInterceptFocusBackWhileLoading",
    "session_load_failed",
    "onAction = onFinished",
)
require(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
    "readyOrFallback(StudyLibrarySnapshot",
    "readyOrFallback(StudyProgressSnapshot",
    "readyOrFallback(LegislationStudySnapshot",
)
require(
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt",
    "var input by rememberSaveable",
    "boundManualImportDraft(candidate)",
    "DOCUMENT_TOO_LARGE",
)
require(
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/ExamNoticeDocumentLoader.kt",
    "Ov15MaxPdfImportBytes",
    "DocumentTooLargeException",
    "length > MaxTextBytes",
)
require("build.gradle.kts", "verifyOv15AndroidProductFlowHonesty", ":feature:practice:testDebugUnitTest")
# .devtool-artifact is transaction-scoped, not a GitHub checkout source file.
# The durable, checked-in Gradle task and runner are the hosted verification authority.
runner_path = "scripts/validate_ov15_android_product_flow_honesty.sh"
require(runner_path, "python3 scripts/verify_ov15_android_product_flow_honesty.py")
runner = (ROOT / runner_path).read_text(encoding="utf-8")
if "gradle --stop" in runner or "./gradlew --stop" in runner:
    raise SystemExit("OV15 runner must not stop Gradle daemons")
checks.append((runner_path, "permanent source validation, no daemon stop"))
# When an actual Devtool artifact is active its hook remains subject to safety checks.
active_hook_path = ROOT / ".devtool-artifact/validation/validate.sh"
if active_hook_path.is_file():
    active_hook = active_hook_path.read_text(encoding="utf-8")
    if "gradle --stop" in active_hook or "./gradlew --stop" in active_hook:
        raise SystemExit("active Devtool hook must not stop Gradle daemons")
    checks.append((str(active_hook_path.relative_to(ROOT)), "active hook safety"))
require("scripts/validate_ov15_android_product_flow_honesty.sh", "verify_ov15_android_product_flow_honesty.py")
print(f"OV15 Android product-flow honesty evidence: PASS ({len(checks)} checks)")
