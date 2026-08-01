# ADR-0015: Library and Study Resources

## Decision

Study materials, video resources, and annex metadata are managed through a dedicated library feature.
Room remains behind `:core:data`; the app receives only the domain-facing `StudyLibraryRepository` and `ManageStudyLibrary` use case.

## Consequences

- The app can organize resource work without depending on database implementation details.
- Annex entries remain metadata-only until a future physical file storage phase.
- Materials and videos can be linked by future schedule/session phases through their stable IDs.
