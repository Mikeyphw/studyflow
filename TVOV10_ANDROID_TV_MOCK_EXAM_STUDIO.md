# TVOV10 — Android TV Mock Exam Studio

TVOV10 implements the Android TV Mock Exam Studio promised by the roadmap.

Delivered contracts:

- real `StudyFlowTvMockExamStudio` model;
- four timed sections with question counts and time budgets;
- state machine for ready, running, paused, submitted, and results;
- start, pause, resume, next-section, mark-review, submit, result-review, and Home actions;
- D-pad focus items for sections and actions;
- paired-phone commands through `StudyFlowTvControlCommand`;
- cast state through `StudyFlowTvCastState` on `StudyFlowTvSurface.MOCK_EXAM`;
- native TV shell rendering in `StudyFlowTvActivity`;
- unit tests and `verifyStudyFlowTvMockExamStudio`.

The studio stays separate from phone-only authoring, persistence, import, and debug features. It does
not add Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, Room, `core:data`, `core:database`,
or phone feature modules to the TV APK.
