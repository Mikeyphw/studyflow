# WTV10 / WTV11 — TV Mock Exam + Review Notebook

## WTV10

- Adds a real `mock` TV History API route for the shared `MOCK_EXAM` surface.
- Uses the active EXAM_PLAN saved mock blueprint and `previewMockExam`; no TV-only blueprint configuration is introduced.
- Freezes the preview for the active attempt and persists only through `StudyStore.recordMockExam`.
- Supports D-pad choice selection, previous/next, mark/unmark for review, pause/resume, guarded Back, submit confirmation and canonical result review.
- Countdown state is presentation-local and ES2019-safe. Time expiry records the same canonical mock result as manual submit.
- Back/browser exit is guarded while an attempt is active. Abandoning explicitly discards only in-memory TV attempt state.
- Typed-answer questions are left unanswered on TV instead of invoking keyboard-heavy input.

## WTV11

- Adds a real `review` TV History API route for the shared `REVIEW` surface.
- Review queue reads canonical due reviews and grades through `StudyStore.completeReview`.
- Exposes Again, Hard, Good and Easy while preserving configured interval progression and successor scheduling.
- Question/flashcard source content is resolved locally for large prompt + answer/explanation presentation.
- Error Notebook reads canonical open errors and mutates them only through `reclassifyError` and `resolveError`.
- All error categories are selectable without typing.
- Phone control remains shared protocol v1. Ratings/categories are carried in `optionId`, not new protocol actions.

## Release/compatibility rules

- No extra network dependency or backend is introduced.
- New runtime modules target ES2019 and are included in the offline shell.
- Content-addressed `web/dist/service-worker.js` and `web/dist/release-manifest.json` remain generated build outputs and must not be immutable Devtool payload entries.
