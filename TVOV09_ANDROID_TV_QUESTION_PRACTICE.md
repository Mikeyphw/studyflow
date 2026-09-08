# TVOV09 — Android TV Question Practice

TVOV09 implements the Android TV question-practice surface promised by the roadmap.

Delivered contracts:

- real `StudyFlowTvQuestionPractice` model;
- three-question practice set with four D-pad-selectable answers per question;
- answer selection, previous/next navigation, explanation reveal, and completion;
- D-pad focus items using `QUESTION_CHOICE` and `PRIMARY_ACTION` roles;
- paired-phone commands through `StudyFlowTvControlCommand`;
- cast state through `StudyFlowTvCastState` on `StudyFlowTvSurface.QUESTION_SET`;
- native TV shell rendering in `StudyFlowTvActivity`;
- unit tests and `verifyStudyFlowTvQuestionPractice`.

The surface remains inside the TV dependency budget: no Compose Runtime, AndroidX TV UI, Cast SDK,
MediaRouter, Room, `core:data`, `core:database`, or phone feature modules.
