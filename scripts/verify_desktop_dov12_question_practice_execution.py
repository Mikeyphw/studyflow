#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "persistence": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPracticePersistence.kt", [
        "practice-v1.properties", "ATOMIC_MOVE", "DesktopPracticeSnapshot",
    ]),
    "repository": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyPracticeRepository.kt", [
        "onChanged", "purgePlan", "currentQuestions", "attachWrongAnswerSink",
    ]),
    "controller": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPracticeExecutionController.kt", [
        "createAttempt", "answerCurrent", "finish", "DesktopSafeQuestion",
    ]),
    "services": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt", [
        "DesktopPracticePersistence.open", "practiceExecutionController", "practiceRepository = practiceRepository",
    ]),
    "ui": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerPracticeScreens.kt", [
        "Practice Test execution", "answer hidden during active execution", "Save answer",
    ]),
    "test": ("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPracticeExecutionPersistenceTest.kt", [
        "practiceAttemptAnswersAndQuestionFlagsPersistAcrossRestart", "practice-v1.properties",
    ]),
}
for label, (name, tokens) in checks.items():
    text = (ROOT / name).read_text()
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"{label} missing: {missing}")
ui = (ROOT / checks["ui"][0]).read_text()
active = ui[ui.index('DesktopSectionCard(title = "Practice Test execution")'):]
if 'Text("Answer:' in active:
    raise SystemExit("DOV12 active Practice Test UI leaks the answer key")
test = (ROOT / checks["test"][0]).read_text()
if "fun practiceAttemptAnswersAndQuestionFlagsPersistAcrossRestart() = runBlocking" in test:
    raise SystemExit("DOV12 JUnit test must use a block body")
build = (ROOT / "build.gradle.kts").read_text()
if 'tasks.register<Exec>("verifyDesktopDov12QuestionPracticeExecution")' not in build:
    raise SystemExit("DOV12 Gradle verification task is not registered")
devtool = (ROOT / ".devtool.toml").read_text()
if '"verifyDesktopDov12QuestionPracticeExecution"' not in devtool:
    raise SystemExit("DOV12 Devtool target does not include its verification task")
print("DESKTOP_DOV12_QUESTION_PRACTICE_EXECUTION: PASS")
