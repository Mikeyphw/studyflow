# Phase 48-49: Readiness Engine and Adaptive Reschedule Wizard

Status: implemented as a follow-on overlay targeting the StudyFlow UI/UX V2 source state.

## Phase 48: Exam Readiness Engine

StudyFlow now computes an explainable `ExamReadinessScore` instead of a mysterious dashboard number.
The score combines coverage, review health, practice accuracy, mock-exam evidence, consistency,
schedule adherence, weak areas, and time feasibility. Route and saved-state payloads still carry only
IDs and small enums; readiness is rebuilt from repositories through domain managers.

The Progress workspace displays the score, readiness band, key components, and the strongest signals.
The engine deliberately keeps the existing AGP, Gradle, Kotlin, KSP, Navigation, Compose BOM, Room,
compileSdk, targetSdk, and minSdk versions unchanged.

## Phase 49: Adaptive Reschedule Wizard

StudyFlow now previews safe plan changes before mutating the schedule. The preview can redistribute
missed unlocked blocks, protect overdue reviews, reduce overloaded days, and steer low-accuracy plans
toward theory before another question sprint. Locked activities are never rewritten. Preview-only
recommendations stay visible but are not applied as fake changes.

Applying the preview updates only executable existing `PlannedActivity` rows through the schedule
repository. The full Schedule editor remains available for manual decisions and non-executable advice.
The wizard behaves like a careful plan assistant, not a silent calendar gremlin.

## Validation focus

- Readiness is domain-level and repository-backed.
- Adaptive changes are preview-first and reversible.
- Locked activities remain untouched.
- The Progress workspace has real score and reschedule surfaces.
- Bilingual resources remain paired.
- New repository contracts fail if the feature regresses into marker-only documentation.

## R2 audit correction

The delivery audit found that the first Phase 48/49 overlay rendered readiness signal labels,
signal messages, reschedule summaries, and reschedule reasons from domain-generated English text.
R2 keeps the engines pure and uses localized workspace resources for every visible signal,
preview reason, summary, and apply result. The verifier now rejects direct `signal.label`,
`signal.message`, `change.reason`, `actionSummary`, and truncated readiness-signal lists in the UI.

## R4 Devtool test-contract hotfix

- Fixed `ExamReadinessEngine.timeFeasibility` so an empty pending schedule is treated as "no explicit backlog" rather than fabricating uncovered work from coverage alone.
- This keeps a well-covered, high-retention plan with strong mock/practice evidence in `ON_TRACK`, matching the domain test and the promised readiness semantics.
- The pessimistic branch still applies when there are real pending planned minutes, and same-day exams still use coverage directly.

## R5 Devtool compile hotfix

- Added the missing bilingual `open_plan` workspace string used by the Progress workspace reschedule/readiness action.
- Hardened source validation so Progress workspace `R.string.*` references are checked against workspace resource keys.
