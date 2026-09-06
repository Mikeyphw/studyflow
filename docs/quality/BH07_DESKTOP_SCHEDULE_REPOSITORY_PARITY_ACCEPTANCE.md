# BH07 Desktop Schedule Repository Parity Acceptance

BH07 closes mutation-contract drift between the desktop in-memory schedule repository and the Room-backed repository used by Android.

## Closed contracts

- Schedule observation reacts to strategy, rule, and activity changes.
- Rule deletion refuses session-owned `IN_PROGRESS` generated activities, removes only disposable generated occurrences, and retains completed history.
- Schedule CRUD cannot create/edit/delete session-owned `IN_PROGRESS` activities or rewrite terminal history through independent status transitions.
- Replace/generate paths reject cross-plan and `IN_PROGRESS` inputs, preserve locked/terminal/session-owned history, report the actual number removed, and validate generated candidates before publishing state.
- Adaptive apply is versioned, stale-state checked, lock/session/no-op protected, candidate validated, and atomic in memory.
- Missed-rebalance apply checks plan ownership and the same compare-and-set state fields as Room before publishing any mutation.
- All in-memory schedule writes are serialized through one coroutine mutex so validation and publication form one mutation boundary.

## Validation

Source contract:

```sh
python3 scripts/verify_bh07_desktop_schedule_repository_parity.py
```

Focused JVM tests:

```sh
./gradlew :desktopApp:test --no-daemon
```

The regression suite covers history preservation/removal counts, cross-plan generation, session ownership, rule deletion, adaptive stale/candidate validation, missed-rebalance stale-state rejection, and schedule observation on rule changes.
