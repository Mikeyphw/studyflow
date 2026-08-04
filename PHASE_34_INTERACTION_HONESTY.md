# Phase 34: Interaction Honesty and Passive Status UI

Phase 34 removes passive-but-clickable UI affordances from production screens. Status labels that do
not trigger an action now use the shared `StudyFlowStatusPill` component from `:core:designsystem`
instead of `AssistChip(onClick = {})`.

## Delivered

- Added `StudyFlowStatusPill` for non-clickable status display.
- Replaced passive empty-click chips in Library, Reviews, Runtime Smoke, Progress, Sessions,
  Reminders, Analytics, Schedule, Plans, Subjects, and Practice screens.
- Preserved real interactive chips, such as recent-search chips that execute a search.
- Added a validator that rejects empty click handlers in production source.

## Acceptance

Every visible production control must either perform an action or be rendered as passive display UI.
No normal production source may contain `onClick = {}` as a shortcut for status display.
