# Desktop DOV24 — full applicable parity seal

DOV24 closes the DOV08-DOV24 Desktop campaign and removes the preview product classification.

## Product closure

- Desktop Goals use the shared `StudyProgressRepository`, `ManageStudyProgress`, and `StudyGoalProgressEngine`.
- Goal evidence is computed from persisted sessions, practice tests, mock exams, and syllabus topics; custom goals
  retain explicit manual progress semantics.
- Goal completion feeds Desktop analytics/coaching and the canonical evidence timeline without forking the shared
  readiness scoring algorithm.
- Goals and notes are persisted atomically in `progress-v1.properties` and are purged with permanent Plan deletion.
- DOV23 native open-with completes arbitrary imported-file viewing; existing shared discovery provides global search.
- Optional Android/provider synchronization is classified `PLATFORM_SPECIFIC`, while cross-client Desktop sync remains
  the already-shipped SFLP path.
- The visible `Desktop preview` label is removed and the Desktop package identity advances to `1.0.0`.

## Acceptance

The DOV24 acceptance test proves persistent Goals across restart, evidence-backed goal evaluation, custom-goal
completion in the evidence timeline, analytics goal completion, purge-on-Plan-delete, injected native platform ports,
and deep-link routing without requiring a graphical desktop. The terminal Gradle batch seal also depends on every
DOV20-DOV24 verifier, Desktop compilation, and the Desktop test suite.
## Compile-gate hardening

The final seal also guards two Kotlin/Compose integration details found by the authoritative Desktop compiler:
`OutlinedButton` is imported from the module's Compose Material API, and DOV20 portability conversions invoke member
extensions through lambdas rather than prohibited callable references. These are compile-safety fixes only; they do not
weaken portability validation, backup safety, Goals semantics, or the DOV24 parity contract.

