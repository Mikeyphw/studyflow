# PC16 — Flashcards + Material Viewer parity

PC16 closes the final applicable P1/P2 content-retention gap identified by the post-PC11 audit.

## Flashcards

- `Flashcard` is a first-class persisted StudyState collection and participates in validation, portable export/import, plan ownership and global search.
- BASIC and CLOZE cards preserve front/back, optional cloze text, tags, subject/topic ownership and suspension state.
- Creating a card schedules an immediate due review using the canonical review engine.
- Suspending a card dismisses pending flashcard reviews; resuming creates a new due review only when none exists.
- Completed-review history prevents destructive deletion; reviewed cards are suspended instead.
- FLASHCARDS activity deep links now open Practice → Reviews and filter to flashcard reviews rather than routing through the question bank.

## Material Viewer

Browser resource launch planning is explicit and local-first. IndexedDB attachments are classified by MIME as text/JSON, image, audio, video, PDF, or download fallback. Explicit HTTP(S) material links are exposed as user-initiated web resources. Missing local blobs and unsupported URI schemes fail closed.

This is the browser-equivalent viewer contract. Android `FileProvider`, `ACTION_VIEW`, native handler selection and private content-URI mechanics remain platform-specific exclusions and are not claimed by the web build.

No new backend, account, telemetry, remote execution or cloud dependency is introduced. The state schema number remains 7; migration normalizes missing `flashcards` to an empty collection for older schema-7 browser state.
