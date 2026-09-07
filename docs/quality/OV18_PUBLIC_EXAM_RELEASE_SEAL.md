# OV18 — Public-exam UX and correctness release seal

This seal closes the remaining public-exam correctness gaps after OV17.

- Today offers Study Cycle only when the persisted schedule strategy is `STUDY_CYCLE`.
- `PublicExamDateResolver` is the canonical countdown authority for dashboard, readiness, priority, and runbook consumers.
- Expected target dates remain planning assumptions and never become official countdowns.
- Confirmed target dates drive official countdowns; legacy plan-only dates remain supported when no StudyTarget exists.
- `TOPIC_COMPLETION` goals target actual topic counts. When the count is unknown, onboarding omits the coverage goal instead of pretending that `100` means 100 percent.
- Sample-plan provisioning passes the actual non-archived topic count when available.
- No Room migration, duplicate scheduler, or parallel analytics store is introduced.
- OV17 readable calendar views and all earlier source contracts remain sealed.
