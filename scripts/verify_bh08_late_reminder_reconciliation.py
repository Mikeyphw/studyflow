#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLANNER = ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt"
COORDINATOR = ROOT / "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt"
TEST = ROOT / "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlannerBh08LateReconciliationTest.kt"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


planner = PLANNER.read_text(encoding="utf-8")
coordinator = COORDINATOR.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

for token in (
    "candidate.occurrenceId in input.reconciliationEligiblePlannedOccurrenceIds",
    "val reconciliationEligiblePlannedOccurrenceIds: Set<String> = emptySet()",
    "plannedActivityWithinDeliveryWindow(",
    "fun plannedActivityWithinReconciliationDeliveryWindow(",
):
    require(token in planner, f"BH08 planner contract missing: {token}")

for token in (
    "private const val ReconciliationDeliverySlackMillis =",
    "MinimumFutureDelayMillis + StudyReminderPlanner.PlannedActivityDeliveryGraceMillis",
    "val previouslyScheduled = occurrenceStore.scheduled(planId)",
    "retainCandidates = runtimePlan.notificationCandidates",
    "previouslyScheduled.none { occurrence -> occurrence.matches(candidate) }",
    "val eligiblePlanned = scheduledOccurrences.asSequence()",
    ".filter { it.type == ReminderNotificationType.PLANNED_ACTIVITY }",
    ".filter { it.isWithinReconciliationDeliveryWindow(now) }",
    "reconciliationEligiblePlannedOccurrenceIds = eligiblePlanned",
    "StudyReminderPlanner.plannedActivityWithinReconciliationDeliveryWindow(",
    "reconciliationSlackMillis = ReconciliationDeliverySlackMillis",
):
    require(token in coordinator, f"BH08 coordinator contract missing: {token}")

reconcile = coordinator.split("private suspend fun reconcilePlanInternal", 1)[1].split("suspend fun deliver", 1)[0]
require(
    reconcile.index("buildRuntimePlan(planId)") < reconcile.index("occurrenceStore.replaceScheduled"),
    "runtime plan must be built before scheduled bookkeeping replacement",
)
require(
    reconcile.index("occurrenceStore.replaceScheduled") < reconcile.index("scheduleCandidate(planId, candidate)"),
    "durable desired bookkeeping must be persisted before installing new Android alarms",
)
require(
    "cancelPlanAlarms(planId, clearBookkeeping = false)" not in reconcile,
    "BH08 must not unconditionally cancel every desired alarm during reconciliation",
)

for test_name in (
    "reconciliationWindowHasFixedBoundaryBeyondNormalLateGrace",
    "scheduledPlannedOccurrenceCanSurviveBoundedReconciliationSlack",
    "reconciliationEligibilityNeverResurrectsCompletedOrDeliveredOccurrence",
    "reconciliationEligibilityIsBoundToExactScheduledOccurrenceAfterReschedule",
):
    require(f"fun {test_name}" in test, f"BH08 regression missing: {test_name}")

for path in (PLANNER, COORDINATOR, TEST):
    text = path.read_text(encoding="utf-8")
    require("\x00" not in text, f"NUL byte in {path.relative_to(ROOT)}")
    for line_number, line in enumerate(text.splitlines(), 1):
        require("\t" not in line, f"tab in {path.relative_to(ROOT)}:{line_number}")
        require(len(line) <= 120, f"line >120 chars in {path.relative_to(ROOT)}:{line_number}")

print("BH08_LATE_REMINDER_RECONCILIATION: PASS")
