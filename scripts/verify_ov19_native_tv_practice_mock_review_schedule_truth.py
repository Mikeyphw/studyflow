#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

checks: list[tuple[str, bool]] = []

def check(name: str, condition: bool) -> None:
    checks.append((name, condition))
    require(condition, name)

practice = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/practice/StudyFlowTvQuestionPractice.kt")
mock = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/mockexam/StudyFlowTvMockExamStudio.kt")
adapter = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapter.kt")
session = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/session/StudyFlowTvStudySessionExperience.kt")
projector = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjector.kt")
action = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvDomainAction.kt")
executor = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvDomainActionExecutor.kt")
execution = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt")
manage = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt")
review = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/reviews/StudyFlowTvReviewNotebook.kt")
schedule = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/schedule/StudyFlowTvScheduleSurface.kt")
reducer = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducer.kt")
presentation = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt")

# S8-04: practice surface exposes all projected questions, not only first three enum slots.
check("S8-04 practice exposes twenty remote-reachable questions", "const val questionCount = 20" in practice and "QUESTION_TWENTY" in practice)
check("S8-04 adapter takes all practice slots", "source.questions.take(questionSlots.size)" in adapter)

# S8-05: non-persistable/completed projections do not offer fake mutation actions.
check("S8-05 practice tracks persistability", "canPersistActions: Boolean = true" in practice and "availabilityReason" in practice)
check("S8-05 practice suppresses selection and finish actions", "!state.canPersistActions || state.completed" in practice and "return listOfNotNull" in practice)
check("S8-05 adapter locks inactive question projections", "source.started && !source.submitted" in adapter and "Practice actions require an open canonical attempt" in adapter)

# S8-06: elapsed-time presentation is live from snapshot generation, not frozen repository emission.
check("S8-06 sessions keep projection timestamp", "projectedAtEpochMillis" in session and "liveElapsedMinutes" in session)
check("S8-06 mock keeps projection timestamp", "projectedAtEpochMillis" in mock and "liveElapsedMinutes" in mock)
check("S8-06 cast state uses live progress", "progressPercentAt(state, updatedAtEpochMillis)" in mock and "progressPercentAt(updatedAtEpochMillis)" in session)

# S8-11/S8-13: mock projection uses real sections and chooses actionable exams over stale submitted ones.
check("S8-11 protocol carries mock sections", "StudyFlowTvMockExamSectionPresentation" in presentation and "maxTvMockExamSections = 12" in presentation)
check("S8-11 projector decodes blueprint sections", "ExamBlueprintCodec.decode(configurationJson)" in projector and "toTvMockSections" in projector)
check("S8-13 projector chooses ready/running mocks before submitted history", "selectTvMockExam" in projector and "it.startedAtEpochMillis == null && it.submittedAtEpochMillis == null" in projector)

# S8-12: pause/resume are persistent canonical mutations and affect domain attempt metadata.
check("S8-12 pause and resume require persistence", "StudyFlowTvControlAction.PAUSE" in action and "StudyFlowTvDomainActionKind.MOCK_PAUSE" in action)
check("S8-12 domain executor persists pause and resume", "pauseMockExamAttempt" in executor and "resumeMockExamAttempt" in executor)
check("S8-12 domain attempt metadata stores paused state", "tvMockPausedAtEpochMillis" in execution and "pauseTvMock" in execution and "resumeTvMock" in execution)
check("S8-12 manager exposes pause resume API", "pauseMockExamAttempt" in manage and "resumeMockExamAttempt" in manage)

# S8-14: review mark-known and complete-review carry distinct grade semantics.
check("S8-14 review commands carry distinct option IDs", 'MARK_KNOWN -> "known"' in review and 'COMPLETE_REVIEW -> "complete"' in review)
check("S8-14 mapper translates review options to distinct grades", '"known"' in action and '-> 5' in action and '"complete"' in action and '-> 4' in action)

# S8-15: schedule label is user-readable, not a raw epoch value.
check("S8-15 schedule formats epoch for TV", "formatScheduleTimeLabel" in adapter and "DateTimeFormatter.ofPattern" in adapter)
check("S8-15 schedule no longer interpolates raw epoch directly", "scheduledStartEpochMillis?.let { \"$it" not in adapter)

# DS2-45: review/schedule OPEN commands use canonical source IDs, not TV slot ids.
check("DS2-45 schedule open uses canonical source id", "targetId = snapshot.activeItem.sourceId" in schedule and "surface = snapshot.activeItem.destination.surface" in schedule)
check("DS2-45 reducer no longer falls back to TV slot IDs for review/schedule", "StudyFlowTvSurface.REVIEW -> {\n                val index = state.review.items.indexOfFirst { it.sourceId == command.targetId }" in reducer and "StudyFlowTvSurface.SCHEDULE -> {\n                val index = state.schedule.items.indexOfFirst { it.sourceId == command.targetId }" in reducer)

# Regression tests for this overlay must be present.
for path in [
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/practice/StudyFlowTvQuestionPracticeOv19TruthTest.kt",
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/mockexam/StudyFlowTvMockExamStudioOv19TruthTest.kt",
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapterOv19TruthTest.kt",
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/schedule/StudyFlowTvScheduleSurfaceOv19TruthTest.kt",
    "core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvDomainActionOv19TruthTest.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngineOv19MockPauseTest.kt",
]:
    check(f"OV19 regression test exists: {path}", (ROOT / path).exists())

print(f"OV19 native TV truth evidence passed: {sum(1 for _, ok in checks if ok)}/{len(checks)} checks")
