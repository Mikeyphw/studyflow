# StudyFlow SFDT22 HF30 — Session close, persistent root navigation, practice transition

## Evidence and scope

Full hosted API36 run 42904272703 against HF29 source `8d92149f0d3b196d3afb81fd093185bdffdd1e30` passed source checks, Room 2/2, SFLP 3/3 and the first three real UI journeys. It failed at `practice_open_root` with `compose_wait_timeout`, before questions and reviews. All previously measured plan-settings evidence matched.

The prior session journey asserted only that a title was visible after tapping Finish. On compact Android devices, the production shell replaced the four root destinations with an active-session bar whenever a session remained active; the next UI journey waited for all four tabs. That made a remaining active session or standalone navigation indistinguishable from an unavailable Practice root.

## Changes and hardening

* **User-facing accessibility**: Today/Plan/Practice/Progress compact navigation remains visible throughout active and paused sessions. The session bar stacks above the dock. Expanded rail is unchanged. Navigation uses the same original production callbacks and semantics; no deep link or alternate route.
* **Stable navigation identity**: root buttons expose deterministic test tags on real `Role.Tab` clickable controls in the shared design-system primitive. The Android test requires exactly one actionable root per destination, explicitly taps the actual UI and awaits selection.
* **Real session completion**: the third journey observes the production `no_active_session` state and absence of active Pause/Resume/Finish controls. An independent read-only SQLite query then requires exactly one session owned by the created plan, with the authored note, status COMPLETED, non-null end timestamp and zero RUNNING/PAUSED sessions. No IDs, SQL data or exception text leave the device.
* **Return navigation**: after the required Session screenshot, the real production Back arrow returns to a root workspace; the test requires all four destination controls to appear.
* **Independent fourth-journey gate**: Practice begins with the same read-only persisted-session proof, separate shell readiness, actual root click, selected-tab proof and the full original question/review behavior.
* **Diagnosability**: new failure stages and three fixed, allowlisted reason codes distinguish SQLite unavailable, incomplete session, and missing navigation. Unknown statuses or malformed JUnit output remain failure; raw logs remain excluded.
* **Focused CI**: opt-in focused Android 36 now runs FOUR real UI journeys, including Practice/review, instead of three, but remains expressly **not release qualifying**. The full five-journey, seven-screenshot, cold-stop/new-PID and both Room/SFLP acceptance gates remain unchanged.
* **Source integrity**: all touched source code, original shell contract, new test and documentation have exact SHA-256 pins; existing historical contracts run in a clean disposable Git worktree before native Devtool apply. The source commit and branch are immutable; no reset, rebase, forced push or merge.

## Qualification status

This is a source-pinned test/UX hardening overlay, **not** proof of full Android runtime acceptance. Android 36 focused and full hosted gates must pass at the resulting new commit. PR #15 remains unmerged. Failure is never converted to green by reclassifying timeout messages or allowing skips.
