# PC15 — Intelligence refinement parity

PC15 closes the narrower post-PC14 semantics that remained in the StudyFlow Local web port. It ports the original Phase 58–64 behavior for the local Weekly Coach, Smart Content Cleanup, source-backed exam report, exam runbook, seven-day study sprint, and the four previewable onboarding starter blueprints. The cloud sync queue from Phase 58 is intentionally not ported: StudyFlow Local web remains local-first and does not invent a network queue.

## Weekly Coach

The coach evaluates the current local Monday–Sunday week and emits at most five distinct, evidence-backed items. The original ordering is retained: readiness risk/stability, executable reschedule, overdue review recovery, low practice accuracy, schedule drift, weak mock signal, consistency win, then empty-evidence guidance. Empty evidence is never interpreted as a zero score.

## Smart Content Cleanup

Cleanup now understands the original seven finding classes: duplicate questions, orphan topics, stale resources, stale plans, inconsistent tags, stale reviews, and broken schedule alignment. Health uses the original severity deductions: **2 points per info finding, 5 per review finding, and 10 per action finding**.

The preview is deliberately non-destructive. Every suggested cleanup operation is a review/navigation instruction with `destructive=false` and `requiresConfirmation=true`; applying the preview directly is refused. Changes must be confirmed on the canonical content surface. This supersedes PC09's earlier narrower auto-apply behavior.

## Exam report

The report remains generated entirely on-device and now includes readiness, coach output, seven-day study/evidence totals, weak areas, missed schedule work, due/overdue reviews, practice-test and mock metrics, executable reschedule count, content health, safe next actions, and explicit metric provenance. `freeformNotesIncluded=false` is part of the contract: arbitrary session/freeform notes are not copied into the report.

Both HTML and PDF exports are local. The PDF writer is dependency-free and emits a self-contained `%PDF-1.4` document; no server or external rendering service is used.

## Exam runbook

Date urgency is shown only for an officially confirmed exam date. Without one, the runbook returns `NO_EXAM_DATE` semantics and no invented countdown. With a confirmed date, actions preserve the original bounded ordering: recover overdue reviews, final review at exam eve, protect the schedule, attack up to two weak areas, practice recall when accuracy is low, mock rehearsal when useful, or maintain momentum when no stronger intervention is supported. At most six actions are emitted.

## Seven-day study sprint

The sprint no longer reuses the general adaptive schedule generator. It only considers **existing, unlocked, planned activities already scheduled in the next seven local calendar days**, with at most seven candidates. Applying a fresh preview may only change activity type and/or shorten duration. It cannot create or delete IDs, move start dates/times, change status, touch locked activities, or increase duration. Candidate identity, count, minutes and a version token are revalidated at apply time, and previews expire after 120 seconds.

## Onboarding starter blueprints

Four original Phase-62 blueprints are now first-class previewable choices:

- **12-week public exam** — 20 h/week, 50-minute preferred session, 3 subjects / 7 topics, with Mon 19:00 90 min, Wed 19:00 90 min, Fri 19:00 75 min, Sat 09:00 120 min blocks.
- **University semester** — 12 h/week, 45-minute preferred session, 3 subjects / 6 topics, with Tue 18:00 75 min, Thu 18:00 90 min, Sat 10:00 120 min blocks.
- **Certification bootcamp** — 10 h/week, 40-minute preferred session, 2 subjects / 4 topics, with Mon 20:00 60 min, Wed 20:00 60 min, Sat 11:00 120 min blocks.
- **Free-study habit** — 6 h/week, 30-minute preferred session, 2 subjects / 4 topics, with Tue 20:00 45 min, Fri 20:00 45 min, Sun 10:00 60 min blocks.

The UI shows workload, subjects/topics and concrete weekly blocks before creation. Provisioning is explicit, local-only and atomic: one store commit creates the selected plan, its content tree, weekly study-hours goal and seed schedule. BB Agente Comercial remains the separate PC13 preset.

## Validation boundary

PC15 keeps the Devtool `studyflow_web` target and uses focused artifact validation only. The artifact intentionally has no `validation.tasks`; its validator covers PC15 static contracts, TypeScript/runtime parity, the affected PC09/PC10/PC14 regressions, the PC15 suite, and release-integrity checks.
