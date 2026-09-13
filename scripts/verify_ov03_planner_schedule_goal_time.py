#!/usr/bin/env python3
"""Source-contract verifier for Overlay 3 of 30 (OV03)."""
from __future__ import annotations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def text(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path: str, *needles: str) -> None:
    body = text(path)
    for needle in needles:
        if needle not in body:
            errors.append(f"{path}: missing contract token {needle!r}")

def forbid(path: str, *needles: str) -> None:
    body = text(path)
    for needle in needles:
        if needle in body:
            errors.append(f"{path}: forbidden stale contract token {needle!r}")

planner = "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySchedulePlanner.kt"
manager = "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySchedule.kt"

# S1-03 / S1-08: rebalance and generation share recurrence-aware policy and overlap checks.
require(
    planner,
    "requirePlanOwnership(request.studyPlanId, rules, strategy)",
    "hasActiveDayOff(rules, startDate, day, dayOffset)",
    "isWithinAvailability(candidate, rules, startDate, day, dayOffset)",
    "FREQ=WEEKLY",
    'setOf("INTERVAL", "COUNT")',
    "findPolicyCompliantStart(",
    "reservedActivities + candidate",
    "validateCandidate(",
)
require(
    manager,
    "val schedule = repository.observeSchedule(planId).first()",
    "schedule.scheduleRules.all { it.studyPlanId == planId }",
    "schedule.plannedActivities.all { it.studyPlanId == planId }",
    "rules = schedule.scheduleRules",
    "repository.applyMissedRebalance(planId, mutations)",
)
forbid(manager, "currentActivities: List<PlannedActivity>")

# S1-06: no foreign rule/strategy ownership at generation boundary.
require(
    planner,
    "Schedule rules belong to another Study Plan.",
    "Study strategy belongs to another Study Plan.",
)

# S1-07: strict construction + no evaluator fallback.
require(
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPlanning.kt",
    'requireValidIsoDate(startsOnIso, "Goal start date")',
    'require(!endsOnIso.isNullOrBlank()) { "Custom goals require an end date." }',
    "requireIsoDateOnOrAfter(",
)
require(
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/IsoDateValidation.kt",
    "Strict YYYY-MM-DD is lexicographically sortable after validation.",
    "require(value >= minimum)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/GoalProgressCalculator.kt",
    "val configuredStart = LocalDate.parse(goal.startsOnIso)",
    "LocalDate.parse(requireNotNull(goal.endsOnIso)",
)
forbid(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/GoalProgressCalculator.kt",
    "getOrDefault(today)",
    "plusYears(100)",
)

# S1-09: malformed graph cannot disappear silently.
structure = "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyStructure.kt"
require(
    structure,
    "Topic tree contains duplicate topic identities.",
    "references missing parent",
    "parent belongs to another Study Plan or subject.",
    "contains a parent cycle",
    "Topic tree projection lost one or more topics.",
)
forbid(structure, ".mapNotNull { topic ->")

# S1-10: Desktop composition owns a monotonic clock independent of wall time.
desktop = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt"
require(
    desktop,
    "wallClockMillis: () -> Long = System::currentTimeMillis",
    "monotonicClockMillis: () -> Long = { System.nanoTime() / 1_000_000L }",
    "clock = wallClockMillis",
    "monotonicClockMillis = monotonicClockMillis",
)

# Behavioral regression owners.
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySchedulePolicyIntegrityTest.kt",
    "generationRejectsForeignRuleAndStrategyOwnership",
    "biweeklyDayOffOnlySuppressesItsActualOccurrences",
    "availabilityIntervalAndCountArePositiveOccurrencePolicies",
    "postponedMissedActivitySkipsDayOffAvailabilityAndExistingOverlap",
)
require(
    "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/StudyGoalAndTopicIntegrityTest.kt",
    "goalDatesAreStrictAndCustomRangesMustBeBoundedAndOrdered",
    "topicTreeRejectsOrphansInsteadOfDroppingThem",
    "topicTreeRejectsCyclesInsteadOfReturningAnIncompleteProjection",
    "topicTreeRejectsDuplicateSelfParentAndCrossOwnerGraphs",
)
require(
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopSessionMonotonicClockTest.kt",
    "desktopSessionDurationIgnoresWallClockJumps",
    "monotonicNow += 120_000L",
)

ledger = text("docs/roadmap/FINAL_AUDIT_REMEDIATION_CLOSURE_LEDGER.md")
for finding in ("S1-03", "S1-06", "S1-07", "S1-08", "S1-09", "S1-10"):
    if ledger.count(f"| {finding} |") != 1:
        errors.append(f"closure ledger must own {finding} exactly once")

if errors:
    print("OV03_PLANNER_SCHEDULE_GOAL_TIME_SEMANTICS: FAIL")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("OV03_PLANNER_SCHEDULE_GOAL_TIME_SEMANTICS: PASS")
print("findings: S1-03 S1-06 S1-07 S1-08 S1-09 S1-10")
