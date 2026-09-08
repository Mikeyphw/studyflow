# TVOV13 read-only schedule contracts

Acceptance requires a structured Android TV read-only schedule surface.

- Today and week context must be visible on TV.
- Review windows, study blocks, practice entries, mock-exam entries, and recovery buffers must be represented as focusable cards.
- The TV schedule may navigate and open learning destinations, but it must not edit, delete, import, export, reschedule, or access SAF/Room/repositories directly.
- Paired-phone control must use small `:core:tvcontrol` commands.
- Cast must send presentation state for `StudyFlowTvSurface.SCHEDULE`, not raw database rows or UI trees.
- The `:app-tv` dependency budget remains intact.
