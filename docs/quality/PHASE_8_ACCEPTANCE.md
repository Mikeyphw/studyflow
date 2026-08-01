# Phase 8 Acceptance

Phase 8 is accepted when all of the following are true:

- Phase 0 through Phase 8 verifier scripts pass.
- `:feature:practice` is included in settings, app dependencies and `checkAll`.
- App navigation exposes `PracticeRoute` from the dashboard.
- The app container exposes `ManageStudyPractice` without importing database implementation types.
- `StudyPracticeDao` supports question create/update/read/delete plus test and mock lookup.
- Bilingual practice resources remain in English/PT-BR parity.
- Compose code does not query localized resources through `LocalContext.current`.
- New Android library modules include `consumer-rules.pro` when referenced by AGP.
