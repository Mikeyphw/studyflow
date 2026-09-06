#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"OV01_TODAY_PRIORITY: FAIL: {message}")


today = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt")
temporal = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceTemporalLogic.kt")
plan = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt")
priority = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PublicExamPriorityWorkspaceLogic.kt")
strings = read("feature/workspace/src/main/res/values/strings.xml")
temporal_test = read("feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceTemporalLogicTest.kt")
priority_test = read("feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/PublicExamTopicSessionMatchingTest.kt")
source_test = read("feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/Ov01WorkspaceSourceContractTest.kt")

require("selectTodayOrNextScheduledActivity(" in today, "Today UI does not use temporal selection helper")
require("includeDate = nextActivity.isFutureDay" in today, "future-day metadata is not wired to the primary card")
require("nextActivity.activity.title" in today, "Today card does not use selected activity")
require("readySchedule.pendingActivities.minByOrNull" not in today, "old ambiguous pending fallback remains")
require("dated_activity_metadata" in strings, "dated activity metadata resource missing")
require("startAt != null && startAt >= endOfToday" in temporal, "future fallback is not bounded after today")
require("isFutureDay = true" in temporal and "isFutureDay = false" in temporal, "temporal provenance is missing")
require("PlannedActivityStatus.PLANNED" in temporal and "PlannedActivityStatus.MISSED" in temporal, "pending statuses changed unexpectedly")
require("stale missed history" in temporal_test, "stale missed regression missing")
require("flexible or completed work" in temporal_test, "unscheduled/completed regression missing")

broad = "session.topicId == topic.id || session.subjectId == subject.id"
require(broad not in plan, "broad subject predicate still pollutes topic evidence")
require("session.contributesToTopicEvidence(topic.id, subject.id)" in plan, "priority evidence does not use shared predicate")
require("activeSession?.contributesToTopicEvidence(topic.id, subject.id)" in plan, "active-session lock does not reuse shared predicate")
require(
    "this.topicId == topicId || (this.topicId == null && this.subjectId == subjectId)" in priority,
    "topic evidence predicate is not exact-topic-or-subject-only",
)
require("contributes only to its exact topic" in priority_test, "exact-topic regression missing")
require("subject-only session contributes" in priority_test, "subject-only fallback regression missing")
require("another subject never leaks" in priority_test or "broad subject matching" in priority_test, "cross-topic leak regression missing")
require("priority evidence no longer assigns every subject session" in source_test, "source wiring regression missing")

print("OV01_TODAY_PRIORITY: PASS")
