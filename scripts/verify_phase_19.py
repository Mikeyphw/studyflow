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
if not phase_version_at_least(ROOT, 19):
    failures.append("app version is not Phase 19 or later")

engine = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt")
for marker in (
    "class PracticeExecutionEngine",
    "object PracticeAttemptCodec",
    "data class PracticeAnswerRecord",
    "class ManagePracticeExecution",
    "answerMockQuestion",
    "finishMockExam",
):
    if marker not in engine:
        failures.append(f"practice execution engine missing marker: {marker}")

practice_manager = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt")
for marker in (
    "startPracticeTest",
    "answerPracticeQuestion",
    "finishPracticeAttempt",
    "startMockExamAttempt",
    "answerMockQuestion",
    "finishMockExamAttempt",
):
    if marker not in practice_manager:
        failures.append(f"ManageStudyPractice missing execution method: {marker}")

screen = text("feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt")
for marker in (
    "PracticeRunnerCard",
    "activeTestId",
    "activeMockId",
    "createMockExam",
    "finishPracticeAttempt",
    "finishMockExamAttempt",
    "RecentMocksCard",
):
    if marker not in screen:
        failures.append(f"Practice screen missing execution marker: {marker}")

strings = text("feature/practice/src/main/res/values/strings.xml")
for marker in (
    "active_practice_attempt",
    "active_mock_attempt",
    "answer_saved",
    "attempt_finished",
    "start_or_resume",
):
    if marker not in strings:
        failures.append(f"Practice strings missing: {marker}")

if "python3 scripts/verify_phase_19.py" not in text("scripts/validate_repo.sh"):
    failures.append("validate_repo.sh does not run Phase 19 verifier")

if failures:
    print("PHASE_19_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_19_CONTRACT: PASSED (practice/test/mock execution engine)")
