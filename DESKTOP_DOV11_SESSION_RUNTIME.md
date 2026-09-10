# DOV11 — Study Session Runtime

DOV11 makes Study Sessions restart-durable and user-operable from Desktop. The workspace schema v4 stores session
state; active RUNNING/PAUSED sessions recover after process restart without counting downtime as active study time.
Starting a planned activity performs the session-owned `PLANNED/MISSED -> IN_PROGRESS` transition, completion
performs `IN_PROGRESS -> COMPLETED`, and cancellation returns it to `PLANNED`. The Planner surface exposes
start-next, quick-session, pause, resume, complete, and cancel actions.

`verifyDesktopDov08Dov11Batch` is the terminal validation gate for this four-overlay batch.

The terminal seal also compiles the legacy empty-repository Desktop screen fixtures against the expanded service
composition root. Newly required repositories therefore cannot be added to production wiring while leaving direct
test composition stale.

DOV11 v3 also hardens the terminal batch tests after device Gradle validation exposed two fixture defects:
all DOV08-DOV11 coroutine JUnit tests now use block-bodied test methods so JUnit always sees `void`, and the
DOV10 planner persistence test derives an unoccupied seeded weekday before adding its recurring block instead
of colliding with the sample Tuesday 19:00 rule. These are test-harness corrections; production schedule-overlap
validation remains unchanged.
