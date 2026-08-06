# Phase 52-53: Mock Exam Studio and Home Widget Actions

Phase 52 adds a preview-first Mock Exam Studio. It ranks eligible questions deterministically,
keeps warnings language-neutral in the domain layer, and creates real `MockExam` records only after
an explicit user action.

Phase 53 upgrades the existing Android home widget from a static summary into a small action surface.
The widget now routes its primary action, reviews action, and background tap through the same
plan-aware launch extras used by StudyFlow reminders.

## Acceptance notes

- Mock Exam Studio filters essay/incomplete questions before selection.
- Studio blueprints expose selected questions, section breakdown, time limit, passing score, and warnings.
- Practice workspace exposes a localized Studio card and creation dialog.
- The widget remains a classic AppWidgetProvider with receiver metadata and localized resources.
- Widget buttons maintain at least 48 dp touch height.
- No Navigation 3, toolchain, Room, or schema changes are introduced.

## r2 audit closure

The Studio now exposes exam board, source, tag, and disputed-question filters, recomputes its preview as inputs change, and displays all planner warnings. Disputed questions are excluded unless explicitly included.
