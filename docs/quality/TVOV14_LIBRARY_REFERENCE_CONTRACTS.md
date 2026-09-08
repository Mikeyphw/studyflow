# TVOV14 limited library/reference contracts

Acceptance requires a structured Android TV limited library/reference material surface.

- Curated reference cards must be visible on TV.
- Legislation, summaries, error-notebook excerpts, and checklist reference kinds must be represented.
- Short TV-readable excerpts and source labels must be available without direct file access.
- The TV library may open references and related practice/review destinations, but it must not import, export, delete, edit, launch SAF, or access Room/repositories directly.
- Paired-phone control must use small `:core:tvcontrol` commands.
- Cast must send presentation state for `StudyFlowTvSurface.LIBRARY`, not raw files, paths, database rows, or UI trees.
- The `:app-tv` dependency budget remains intact.
