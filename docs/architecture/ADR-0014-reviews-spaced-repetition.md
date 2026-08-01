# ADR-0014: Reviews and Spaced Repetition

## Status

Accepted.

## Context

StudyFlow already stores review items, flashcards, error entries, and question-bank records. Phase 9
needs to activate those tables without introducing another schema migration.

## Decision

Add `StudyReviewRepository`, `ManageStudyReview`, and `SpacedRepetitionScheduler` in the domain
layer. Add `RoomStudyReviewRepository` and retention entity mappers in `:core:data` and
`:core:database`. Keep Room hidden behind the data-layer factory and expose only domain-facing
interfaces to the application.

The scheduler uses a deterministic SM-2-inspired policy with the following behavior:

- grades `0..2` reset repetition count and schedule a short relearning interval;
- grades `3..5` advance intervals and update ease factor;
- ease factor never drops below `1.3`;
- review items remain active after grading with a future due date.

## Consequences

The review queue can now power flashcards, topic reviews, question reviews, and wrong-answer loops.
Full notification scheduling and completed-review history analytics remain future phases.
