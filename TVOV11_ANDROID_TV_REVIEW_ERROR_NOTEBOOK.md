# TVOV11 — Android TV Review and error notebook

TVOV11 implements the Android TV Review and error-notebook surface as a real source-backed TV feature.

Delivered contracts:

- structured spaced-review queue with due state, subject, reason, confidence and active item;
- review state machine for ready, reviewing, explanation, error-notebook and completed states;
- remote actions for start, next, mark known, add error note, show explanation, complete and return to Today;
- paired-phone command generation through `:core:tvcontrol` using the `REVIEW` surface;
- cast-state generation through `StudyFlowTvCastState` without mirroring UI or exposing database rows;
- D-pad focus items for review cards and review actions;
- native shell rendering inside `StudyFlowTvActivity`;
- unit tests and `verifyStudyFlowTvReviewNotebook`.

Dependency boundary: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, or phone feature-module fanout is added to `:app-tv`.
