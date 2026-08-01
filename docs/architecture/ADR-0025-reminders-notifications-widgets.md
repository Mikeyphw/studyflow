# ADR-0025: Reminders, Notifications, and Widgets

StudyFlow reminders remain local and are derived from the existing schedule, review queue, and per-plan settings. Phase 21 avoids adding a new persistence table because the existing `StudyPlanSettings`, review items, and planned activities already hold the required state.

Android notification permission is requested only on API 33+ and uses a string-literal permission constant to avoid inlined API lint failures. The Today widget is a launcher entry point with stable fallback copy; richer live widget data can be added later after a background update policy is selected.
