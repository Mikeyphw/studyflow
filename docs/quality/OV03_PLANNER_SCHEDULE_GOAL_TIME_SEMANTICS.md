# OV03 — Planner, schedule, goal & time semantics

Overlay **3 of 30** closes final-audit findings **S1-03, S1-06, S1-07, S1-08, S1-09, and S1-10**.

## Production contracts

### Schedule ownership and recurrence

`StudySchedulePlanner` now requires every supplied `ScheduleRule` and optional `StudyStrategy` to belong to the requested Study Plan before generation. DAY_OFF and AVAILABILITY rules no longer bypass recurrence: `ONCE`, weekly recurrence, legacy every-N-weeks forms, and `FREQ=WEEKLY` with `INTERVAL`/`COUNT` share one occurrence evaluator. Availability is a positive policy window: when availability is configured for a weekday but is not active for that recurrence occurrence, generation does not silently treat the day as unrestricted.

### Missed-activity rebalance

`ManageStudySchedule.rebalanceMissed` loads the canonical persisted schedule rather than accepting activity evidence from the UI. The planner validates Plan ownership, reserves non-moved active activities, preserves previously placed rebalance results as reservations, and searches in local-calendar increments for a slot that satisfies recurrence-aware DAY_OFF/AVAILABILITY policy and overlap constraints. The repository still applies the resulting expected→proposed mutation set atomically.

### Goals and topic integrity

`StudyGoal` validates strict `YYYY-MM-DD` dates at construction. CUSTOM periods require an end date, and any supplied end must be on or after the start. `StudyGoalProgressEngine` no longer fabricates fallback periods for malformed persisted input. Topic-tree projection fails closed for duplicate IDs, missing/cross-owner parents, self-parenting, cycles, and incomplete projection instead of dropping nodes.

### Desktop monotonic time

Desktop service composition now supplies wall time and monotonic time separately to `ManageStudySession`: `System.currentTimeMillis` owns persisted timestamps while `System.nanoTime()/1_000_000` owns elapsed-session accounting. Tests inject both clocks and verify wall-clock jumps cannot inflate active duration.

## Validation ownership

Run `scripts/validate_ov03_planner_schedule_goal_time.sh` after applying the overlay. It executes the OV03 source-contract verifier, shared model/domain tests, Desktop tests, and Android Kotlin compile gate. The final 30-overlay release validation remains owned by Overlay 30.
