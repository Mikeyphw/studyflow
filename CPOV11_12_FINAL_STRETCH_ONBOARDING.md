# CPOV11+CPOV12 — Final stretch strategy and public-exam onboarding

## CPOV11 — 30/15/7/2/1-day final stretch

StudyFlow now derives an explicit public-exam final-stretch band from the confirmed exam countdown:
more than 30 days, 30-day, 15-day, 7-day, 2-day, and exam-eve bands. Each band exposes a bounded
allocation across new content, reviews, error-notebook work, questions, mocks, and legislation. New
content falls as the exam approaches while consolidation evidence gets a larger share.

The existing seven-day `StudySprintPlanner` remains the only mutation path. Final-stretch guidance is
attached to the sprint/runbook preview, and near the exam it can only rebalance existing unlocked
planned activities. The Phase 64 safety envelope is unchanged: no new activity IDs, no moved starts,
no status changes, no locked targets, version-bound preview/apply, and no mutation outside the approved
seven-day candidate set.

## CPOV12 — public-exam onboarding and local templates

The existing sample-plan wizard now accepts optional real public-exam target data (organization, role,
board, edital URL and exam date), weekly availability, cycle preference, 1/7/30-style review intervals,
and editable question/accuracy/mock goal presets. Target identity is never fabricated: organization,
role and board must either all be supplied or all remain blank.

Provisioning reuses the existing transaction and models. `StudyTarget`, `StudyStrategy`, `StudyGoal`,
`StudyPlanSettings`, subjects, topics and schedule rules are materialized before Room commits the graph.
The selected cycle preference maps to the existing strategy types; review intervals remain in the
versioned settings JSON envelope; goals reuse the existing evidence-backed goal engine.

Three local starter files are included under `samples/import/` for edital, question and legislation
imports. They do not fetch or imply official exam data and remain user-edited, preview-first inputs.

## Persistence and validation

No Room schema change is introduced by CPOV11 or CPOV12; schema 13 remains authoritative. New EN and
pt-BR resources are kept in parity. `verifyCpov11Cpov12FinalStretchOnboarding` checks the final-stretch
bands, unchanged sprint safety boundary, onboarding transaction ownership, local templates, localization,
and the no-schema-bump invariant. Full Android validation remains deferred to the final campaign seal.
