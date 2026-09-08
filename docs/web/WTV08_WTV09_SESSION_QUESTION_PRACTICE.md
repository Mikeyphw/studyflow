# WTV08-WTV09 — TV Study Session + Question Set

## WTV08 — dedicated Study Session

The browser-TV adapter now exposes `STUDY_SESSION` as a real TV-only history surface instead of routing it back to Today. It reuses the canonical StudyFlow focus state and evidence pipeline while adding presentation-only segments for 10-foot readability.

Delivered behavior:

- TV-only `#session` history surface opened from Today, activity deep links, or protocol-v1 `OPEN/STUDY_SESSION` commands.
- Deterministic visible session segments derived from the configured activity duration; segment movement never invents database/evidence records.
- Ready/running/paused/completed presentation states backed by canonical `startFocus`, `pauseFocus` and `completeFocus` mutations.
- START, PAUSE, RESUME, NEXT and COMPLETE phone-command bindings.
- Play/Pause media-key bridge for browsers that expose `MediaPlayPause`, Play/Pause, or common TV key codes.
- Large session clock, progress bar and focused segment presentation for 720p/1080p viewing.
- Writing activities remain explicitly off-TV for drafting/rubric/checklist completion.

## WTV09 — dedicated Question Set

`QUESTION_SET` is now a dedicated TV-only question-practice surface rather than only a read-only Practice tab.

Delivered behavior:

- TV-only `#questions` history surface opened from Practice, question deep links, or protocol-v1 `OPEN/QUESTION_SET` commands.
- D-pad selectable answer-choice cards with stable `choice-N` protocol option IDs.
- PREVIOUS, NEXT, SELECT_ANSWER, SUBMIT, SHOW_EXPLANATION and COMPLETE bindings.
- Submitted choices call canonical `StudyStore.answerQuestion`, so correctness evidence, generated due review, and Error Notebook behavior are identical to normal web practice.
- Typed-answer questions remain viewable but are explicitly delegated to phone/computer for graded entry.
- Local set completion summary counts submitted/correct answers without creating a second persistence model.
- `/remote` now tracks the currently opened surface and adds A-D choice shortcuts plus SUBMIT.

## Architecture constraints

- No separate TV database/state store.
- No screen mirroring.
- No new backend dependency.
- Presentation-only state (segment cursor, selected choice, reveal state, local set summary) is ephemeral.
- Canonical StudyFlow focus/question evidence remains owned by the existing web domain/store.
