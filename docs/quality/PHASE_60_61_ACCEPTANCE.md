# Phase 60-61 Acceptance

## Source acceptance

- Smart cleanup is implemented in the domain layer and covered by behavioral tests.
- Cleanup generation is preview-only: generated actions are non-destructive and require confirmation.
- Duplicate, orphan, stale-resource, stale-plan, tag, review-link, and schedule-link checks are real.
- Plan and Progress surfaces localize structured cleanup results in English and pt-BR.
- Exam report construction stays structured and does not copy readiness/reschedule prose into export data.
- Freeform notes are omitted from the report model by default and cannot be enabled by this phase.
- HTML and native PDF are generated under local app cache and shared through FileProvider only on tap.
- The exporter contains no network client or cloud endpoint.
- Primary app/settings/workspace resource files contain no `tools:ignore` warning suppressions.
- Former plural candidates use `<plurals>` plus `pluralStringResource`.
- Phase 60-61 validation is wired into `verifyRepositoryContracts` and `checkAll`.

## Target-machine acceptance

Devtool must still run the requested Gradle unit-test, assembly, and debug/release lint tasks. Source
validation in the overlay-construction environment does not pre-claim Android lint or APK success.
