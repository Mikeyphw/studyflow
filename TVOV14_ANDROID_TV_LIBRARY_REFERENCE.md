# TVOV14 — Android TV limited library/reference material

TVOV14 implements the Android TV limited library/reference material surface as a full roadmap unit.

Delivered scope:

- `StudyFlowTvLibrarySurface` model with curated reference cards.
- Reference kinds for legislation, summaries, error-notebook excerpts, and checklists.
- Short TV-readable excerpts and source labels.
- Explicit file-management boundary: no import, export, SAF picker, delete, edit, repository, or Room behavior in `:app-tv`.
- D-pad focus items for references and actions.
- Paired-phone command generation through `:core:tvcontrol`.
- Cast-state emission for `StudyFlowTvSurface.LIBRARY`.
- Native shell rendering in `StudyFlowTvActivity`.
- Unit tests and `verifyStudyFlowTvLibraryReference`.

The TV library is a safe reference presentation surface. The phone/tablet app remains responsible for managing files, notes, sources, and persistence.


## Roadmap carry-forward

This overlay also preserves the complete TV roadmap and working preferences in
`TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md`, with `verifyStudyFlowTvRoadmapInstructions`
checking that future TV work remains structured, target-correct, and dependency-budgeted.
