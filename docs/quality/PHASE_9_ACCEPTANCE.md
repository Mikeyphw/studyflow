# Phase 9 Acceptance

Phase 9 is accepted when the repository contains:

- `:feature:reviews` with `consumer-rules.pro` and bilingual resources;
- a dashboard path to `ReviewsRoute` and `StudyReviewScreen`;
- `StudyReviewRepository`, `ManageStudyReview`, and `SpacedRepetitionScheduler`;
- Room retention entity mappers and `RoomStudyReviewRepository`;
- `StudyRetentionDao` operations for find/upsert/delete of review items and flashcards;
- `:feature:reviews:testDebugUnitTest` in `checkAll`;
- no `.devtool.toml` mutation;
- no Compose scoped modifier imports or localized string reads through `LocalContext.current`;
- Devtool artifact validation script with a recorded SHA-256 hash.
