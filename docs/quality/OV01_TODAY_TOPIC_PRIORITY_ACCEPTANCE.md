# OV01 — Today temporal correctness + topic-priority correctness

## Scope

OV01 closes the first two post-bug-hunt P0 defects without changing the wider workspace design.

### Today

- The primary Today card prefers pending work scheduled on the current local day.
- When no pending work remains today, the fallback may select only the earliest pending activity scheduled on a future local day.
- Stale `MISSED` activities from earlier days are not presented as the next planned block.
- Flexible/unscheduled work is not mislabeled as a future scheduled block.
- A future-day primary activity renders weekday + local short date + time + duration. Today timeline rows retain the compact time + duration metadata.

### Public-exam topic priority

A completed session contributes to a topic only when:

1. `session.topicId == topic.id`, or
2. the session has no topic and `session.subjectId == subject.id`.

A session explicitly assigned to another topic in the same subject must not inflate the current topic's minutes, recent accuracy, recency, or cooldown evidence.

The same topic-membership predicate is reused for active-session locking so the workspace does not maintain two subtly different definitions.

## Regression coverage

- `TodayWorkspaceTemporalLogicTest`
- `PublicExamTopicSessionMatchingTest`
- `Ov01WorkspaceSourceContractTest`

## Devtool validation

Shell/source contract:

```sh
python3 scripts/verify_ov01_today_priority.py
python3 scripts/verify_bh01_08_bug_hunt_audit.py
```

Gradle task:

```text
:feature:workspace:testDebugUnitTest
```
