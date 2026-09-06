#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyScheduleRepository.kt"
TEST = ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyScheduleRepositoryParityTest.kt"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


source = SOURCE.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

for token in (
    "private val mutationMutex = Mutex()",
    "combine(\n        strategyState,\n        ruleState,\n        activityState,",
    "planner.validateRules(candidateRules)",
    "Cannot delete a schedule rule while one of its generated activities is in progress.",
    "activity.status in RuleDeletionStatuses",
    "An in-progress activity can only be created by starting its Study Session.",
    "Completed or terminal planned activities cannot be transitioned independently.",
    "Generated activities must belong to one Study Plan.",
    "Generated activities cannot bypass the Study Session start transition.",
    "removedUnlockedActivities = replaceable.size",
    "AdaptiveReschedulePlanner.scheduleVersionToken(currentSchedule) == preview.scheduleVersionToken",
    "Adaptive preview contains an executable no-op change.",
    "planner.validateCandidate(proposed, finalActivities, rules)",
    "Planned activity changed concurrently; no missed-rebalance changes were applied.",
    "matchesMissedRebalanceExpectation(expected)",
):
    require(token in source, f"BH07 source contract missing: {token}")

require(
    "activity.studyPlanId == planId && !activity.locked && activity.status in IndependentlyMutableStatuses" in source,
    "replace-unlocked must only remove unlocked PLANNED/MISSED activities",
)
require(
    "PlannedActivityStatus.COMPLETED" not in source.split("val IndependentlyMutableStatuses =", 1)[1].split("\n", 1)[0],
    "terminal completed history must not become replaceable",
)

for test_name in (
    "replaceUnlockedPreservesHistoryAndSessionOwnedActivitiesAndReportsActualRemovalCount",
    "generatedActivitiesRejectCrossPlanAndInProgressInputBeforeMutation",
    "crudCannotBypassStudySessionOwnershipOrRewriteTerminalHistory",
    "deletingRuleRetainsCompletedHistoryAndRefusesSessionOwnedGeneratedActivity",
    "adaptivePreviewUsesVersionAndCandidateValidationAtomically",
    "missedRebalanceRejectsStaleExpectedStateWithoutPartialMutation",
    "observeScheduleEmitsWhenRulesChange",
):
    require(f"fun {test_name}" in test, f"BH07 regression missing: {test_name}")

for path in (SOURCE, TEST):
    text = path.read_text(encoding="utf-8")
    require("\x00" not in text, f"NUL byte in {path.relative_to(ROOT)}")
    for number, line in enumerate(text.splitlines(), 1):
        require("\t" not in line, f"tab in {path.relative_to(ROOT)}:{number}")
        require(len(line) <= 120, f"line >120 chars in {path.relative_to(ROOT)}:{number}")

print("BH07_DESKTOP_SCHEDULE_REPOSITORY_PARITY: PASS")
