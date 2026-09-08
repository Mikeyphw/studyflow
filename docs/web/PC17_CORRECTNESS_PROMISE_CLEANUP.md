# PC17 — OV18 + OV19 correctness / promise cleanup parity

PC17 closes the browser-applicable correctness and promise-cleanup items from the original Android OV18 and OV19 work without claiming Android-only behavior.

## Canonical public-exam date truth

`src/promise_cleanup.ts` is the single browser-side resolver for public-exam date truth. A **confirmed** target date is official. An **expected** target date is informative only and never enables an official countdown. A legacy plan-only `examDate` remains supported when no target date carries a newer truth state. Date-distance calculations use the device/browser civil date rather than slicing UTC ISO timestamps.

The pre-edital cockpit, final-stretch guidance, readiness and runbook/report refinement paths consume this canonical resolver instead of maintaining separate interpretations.

## Study strategies and Study Cycle

The persisted schedule strategy is one of the seven original values:

- `FIXED_WEEKLY`
- `STUDY_CYCLE`
- `GOAL_BASED`
- `ADAPTIVE`
- `FREE_GUIDED`
- `INTENSIVE_SPRINT`
- `COUNTDOWN`

Today exposes Study Cycle controls only when the persisted strategy is `STUDY_CYCLE`. Other strategies do not receive a misleading Study Cycle surface.

## Coverage goals

The public-exam onboarding blueprint creates a topic-coverage goal from the **actual number of active topics provisioned into the plan**. It does not use a placeholder percentage/count such as 100. If an exam blueprint has no topics, a topic-count coverage goal is omitted rather than fabricated.

## Manual and recurring schedule ownership

Manual scheduled activities persist their selected subject, topic, explicit local date and explicit local start minute. Weekly recurring blocks persist subject/topic, weekday, local time, duration and activity type inside the existing `ScheduleConfiguration`.

When adaptive preview materializes a recurring block, its local weekday/time and subject/topic are preserved exactly. A conflict does not silently relocate it: the block remains explicitly unscheduled with a conflict reason.

## Calendar and dashboard cleanup

The full Calendar view keeps past scheduled entries collapsed until the user explicitly chooses **Show past activities**. Search matches title, subject or topic. Existing filters compose with the search.

The public-exam dashboard no longer presents a repeated raw unknown-value matrix as the default summary. **Subject evidence** shows only observed fields, using one bounded no-evidence message when none exist; the detailed edital matrix remains opt-in.

## Browser boundary

This phase ports browser-applicable product semantics only. Android locale APIs, native calendar components and other Android-specific implementation details are not represented as web parity requirements. The web remains local-first and offline-capable.


## Dark-only web shell

StudyFlow Local remains intentionally dark-only. PC17 sets the browser shell to `color-scheme: dark`, keeps the dark palette as the only CSS contract, and does not add a light-mode toggle, `prefers-color-scheme: light` branch, or alternate light theme. This is a product constraint, not an Android-parity claim.
