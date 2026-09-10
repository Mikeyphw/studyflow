#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
checks = {
 "controller": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopMockExamStudioController.kt", [
   "MockExamStudioRequest", "createMockExamFromStudio", "answerCurrent", "finish", "DesktopMockSafeQuestion",
 ]),
 "services": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt", [
   "mockExamStudioController = DesktopMockExamStudioController",
 ]),
 "ui": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlannerPracticeScreens.kt", [
   "Mock Exam Studio", "Create mock exam", "Submit mock exam",
 ]),
 "test": ("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopMockExamStudioPersistenceTest.kt", [
   "frozenMockExamAndAnswersPersistAcrossRestart", "questionIds=", "answer=",
 ]),
}
for label,(name,tokens) in checks.items():
 text=(ROOT/name).read_text()
 missing=[x for x in tokens if x not in text]
 if missing: raise SystemExit(f"{label} missing: {missing}")
if "fun frozenMockExamAndAnswersPersistAcrossRestart() = runBlocking" in (ROOT/checks["test"][0]).read_text():
 raise SystemExit("DOV13 JUnit test must use a block body")
print("DESKTOP_DOV13_MOCK_EXAM_STUDIO: PASS")
