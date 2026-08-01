# Phase 10: Library and Study Resources

Phase 10 activates the existing resource tables with a domain/data/UI execution layer.

## Added

- `:feature:library` Android module.
- `StudyLibraryRepository` domain interface.
- `ManageStudyLibrary` use case.
- `StudyLibraryProgress` and `StudyLibrarySnapshot` summaries.
- `RoomStudyLibraryRepository` and `StudyLibraryEntityMapper`.
- DAO find/delete/upsert operations for materials, videos, and annexes.
- Dashboard route and navigation to the library screen.
- Phase 10 verifier and documentation.

## Schema

No Room migration is required. Phase 10 uses the existing schema version 2 tables:

- `study_materials`
- `video_resources`
- `annexes`
