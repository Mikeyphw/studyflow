# Phase 7 Acceptance

- `:feature:sessions` is included in settings, app dependencies, and `checkAll`.
- Dashboard exposes a Study Sessions entry.
- App navigation includes `SessionsRoute`.
- The app container exposes `StudySessionRepository` and `ManageStudySession` without leaking
  Room database types to `:app`.
- Study session state transitions use `StudySessionTimer`.
- Planned activity start/finish/cancel transitions are transactional.
- English and Portuguese resources stay in parity, including plural `many` in Portuguese.
- Compose screens use composition-aware resources and avoid explicit scoped modifier imports.
