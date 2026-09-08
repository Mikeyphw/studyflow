# PC14 — Intelligence + readable schedule parity

PC14 closes the audit block for the original **Phase 48/55 + OV16 + OV17** browser-applicable contracts. It does not add a network service or a second persistence model.

## Readiness

The Concurso readiness score is now nullable and multi-signal. The seven weighted dimensions match the reference engine: **24% coverage, 18% reviews, 14% practice, 14% mock exams, 10% consistency, 10% schedule adherence, 10% time feasibility**. Missing dimensions are omitted rather than invented. A score is published only when the original minimum-evidence gate is satisfied: at least one submitted mock, 30 graded questions, three completed recent sessions, or at least two behavioral dimensions with ten evidence samples. Confidence follows the reference low/medium/high thresholds.

Time feasibility is available only with a confirmed exam date and an explicit weekly study-time goal. Weak areas are shown as an extra non-weighted signal.

## Question quota planner

For an active EXAM_PLAN phase with a `questionGoal`, the next-seven-day subject allocation uses exactly:

- **35% exam weight**;
- **20% under-practice**;
- **20% recent accuracy deficit**;
- **15% recurring errors**;
- **10% overdue reviews**.

Unknown evidence contributes zero. largest-remainder integer allocation guarantees that subject quotas sum exactly to the displayed seven-day total. The existing generic weekly practice quota remains available outside an active phase target.

## Unified Evidence Timeline

The Progress timeline is no longer a sorted `Evidence[]` view. It combines browser-applicable canonical entities: completed/cancelled sessions, completed/cancelled planned activities, due/completed reviews, added questions, submitted mocks, explicitly completed goals, note materials, and non-duplicated canonical evidence. Linked completed activities are suppressed when their completed session already represents the same action. Events are plan-local, newest-first, future-safe, and bounded to 50 (30 in the UI).

The web model has no separate native `StudyTest` or pinned-`StudyNote` table. PC14 therefore does not fabricate those records: mock evidence and local note materials are used where the web model has canonical ownership.

## Readable local-time schedule

Schedule exposes **Today / This week / Next week / Calendar**. Today and Monday–Sunday week windows use the browser/device local civil date. Bounded views exclude unscheduled and out-of-window work; Calendar retains the full schedule. Subject, activity-type, status, and provenance filters compose with the view. Exact visible activity count and planned minutes are displayed, and selection/bulk lock actions operate only on the visible filtered set.

The scheduler's generated date keys and applied `queue=today` decision now use local civil dates rather than `toISOString().slice(0,10)`, eliminating the UTC-midnight rollover bug without changing persisted `YYYY-MM-DD` schedule values.
