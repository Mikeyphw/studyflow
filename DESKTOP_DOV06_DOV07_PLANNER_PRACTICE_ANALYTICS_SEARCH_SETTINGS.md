# DOV06/DOV07 — Planner, Practice, Analytics, Search, and Settings

## Scope

This overlay expands the Compose Multiplatform Desktop preview from dashboard and
library into the next two roadmap overlays:

- **DOV06 planner + practice screens**
- **DOV07 analytics + search + settings screens**

It keeps the DOV03 shared-module boundary intact. Desktop still depends only on
`:core:model`, `:core:domain`, and `:core:portability`. Android app, feature,
database, data, design-system, navigation, and i18n modules remain forbidden.

## DOV06 implementation

- `DesktopRoute.PLANNER`
- `DesktopRoute.PRACTICE`
- `InMemoryStudyScheduleRepository`
- `InMemoryStudySessionRepository`
- `InMemoryStudyPracticeRepository`
- `ManageStudySchedule` wiring
- `ManageStudySession` wiring
- `ManageStudyPractice` wiring
- sample schedule rules, planned activities, sessions, questions, practice test,
  and mock exam data
- planner metrics, planned-activity list, recent sessions, and active-session
  slot
- practice summary, current question card, generated deck, and attempts list

## DOV07 implementation

- `DesktopRoute.ANALYTICS`
- `DesktopRoute.SEARCH`
- `DesktopRoute.SETTINGS`
- `InMemoryStudyAnalyticsRepository`
- `DesktopStudyDiscoveryRepository`
- `DesktopPreferencesStore`
- `ManageStudyAnalytics` wiring
- `ManageStudyDiscovery` wiring
- analytics metrics, weak areas, and insights
- global search over plans, materials, activities, and questions
- recommendations from desktop in-memory model state
- settings backed by `AppPreferences` without Android DataStore

## Deferred

- durable desktop filesystem storage
- native file pickers
- backup/restore import/export
- desktop notifications
- tray/menu/shortcut polish
- installer packaging

Those remain for later overlays.
