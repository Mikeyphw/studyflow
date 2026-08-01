# Phase 9 Implementation: Reviews and Spaced Repetition

Phase 9 adds the retention execution layer on top of the Phase 8 question bank and the Phase 7
study-session workflow.

## Highlights

- New `:feature:reviews` module with a bilingual review queue and flashcard creation screen.
- Room-backed review repository through the public data-layer boundary.
- Flashcard CRUD entry points and automatic initial review scheduling.
- Review queue buckets for due, upcoming, and recently completed items.
- Spaced repetition scheduler with ease factor, intervals, repetitions, and failed-grade reset.
- Error-entry and question-based review hooks for the future wrong-answer flow.
- Dashboard route and action menu entry for reviews.
- No Room migration: Phase 2 already created `review_items`, `flashcards`, and `error_entries`.

## Validation focus

- `validation.script.sha256` is present in the artifact manifest.
- The app does not import the Room database implementation.
- `consumer-rules.pro` is included for the new Android library module.
- Compose resource lookup and scoped modifier import seals remain active.
