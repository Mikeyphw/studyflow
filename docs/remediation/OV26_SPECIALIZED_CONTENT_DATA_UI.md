# OV26 specialized content data/UI evidence

This evidence note documents the Android production path added by Overlay 26 of 30.

- `RoomProgrammingCourseRepository` persists programming profiles, snippets, exercises, mini projects, and Termux preferences with mapper-level command-template invariants.
- `RoomLanguageLearningRepository` persists language decks, expressions, idiom/vocabulary cards, and review attempts.
- `StudyFlowDataLayer` and `StudyFlowApplication` expose `ManageProgrammingCourse`, `ManageLanguageLearning`, and `ApplySpecializedContentImport` through the production container.
- `TodayWorkspaceScreen` renders Programming Course and Language Deck panels from real snapshot flows.
- `RoomStudyAnalyticsRepository` uses programming and language tables as specialized evidence for progress/accuracy whenever such data exists.
