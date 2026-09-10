# Desktop DOV19 — readiness, coaching, cleanup, reports, runbooks and sprints

DOV19 closes the Desktop Progress/readiness gap by consuming the same shared domain engines as Android instead of
creating Desktop-only scoring or planning rules.

## Delivered

- `ExamReadinessEngine` derives evidence-aware readiness from the persistent Desktop Plan, analytics, schedule,
  practice/mock and Concurso target state.
- `StudyEvidenceTimelineBuilder` rebuilds a bounded evidence timeline from durable sessions, schedule, reviews and
  practice history after process restart.
- `StudyCoachWeeklyReviewBuilder` produces the weekly coach summary from readiness, adaptive planning and evidence.
- `StudyContentCleanupEngine` audits persistent syllabus/library/review/schedule/session state. DOV19 exposes only the
  preview report; it does not silently delete or normalize user content.
- `StudyExamReportBuilder` produces the structured exam report with metric provenance and no freeform study notes.
  Desktop can atomically export a local HTML rendering under the workspace `exam-reports/` directory.
- `StudyExamRunbookPlanner` derives exam-date-aware next actions from the same persistent evidence and the DOV18
  Concurso target when present.
- `StudySprintPlanner` derives the seven-day preview. Apply is explicit and delegates only to
  `ManageStudySchedule.applyVersionedPreview`, preserving version/freshness/locked-activity/mutation-envelope checks.
- The existing Analytics route exposes readiness, coach, cleanup, runbook, sprint and report surfaces without
  replacing the original analytics metrics.

## Persistence boundary

Readiness, weekly review, cleanup report, runbook, sprint preview and report snapshot are projections. They are
recomputed from DOV08-DOV18 durable repositories after restart and are not persisted as a second source of truth.
Only an explicitly exported report file and an explicitly applied sprint mutation become durable artifacts.

## Deferred work

DOV19 does not add Goals, portable backup, localization, appearance, or OS-native picker/open-with/notification/deep
link adapters. Those remain DOV20-DOV23. Desktop remains Preview until the DOV24 acceptance seal.

## Compile-safety rebase

The terminal DOV19 package also carries narrow compile repairs discovered by the first real Gradle gate:
Desktop public-exam UI stays on the module's existing Compose Material API, StudyTarget writes flow through
`ManageStudyContent.saveStudyTarget`, revision diff mapping snapshots nullable cross-module properties before
use, and report percentage rendering avoids nested escaped literals inside Kotlin string-template expressions.
These repairs do not relax public-exam, schedule, or persistence invariants.
