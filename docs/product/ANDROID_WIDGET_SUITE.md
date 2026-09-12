# Android Widget Suite

StudyFlow's Android widget is a configurable, per-instance glance surface backed directly by canonical plan/schedule/review/session state.

## Today
Shows due reviews, today's planned-block count, the next study item, progress and missed work. Actions open reviews or start/open the most relevant study action.

## Next
Focuses on the next planned study block with scheduled time, Start, and exact schedule navigation.

## Reviews
Shows due and upcoming review counts and opens the review workspace directly.

## Session
Shows an active session's title and elapsed/target progress. It supports Pause/Resume and exact session navigation. When idle it can start the next planned block.

## Compact
Uses a small responsive presentation for launcher cells that cannot fit the normal card. It prioritizes active session, next study, due reviews, then an all-clear state.

## Configuration and migration
Every widget stores its own mode and optional pinned plan. `null` plan selection means follow the app's canonical default. Existing widgets without ATT03 state are interpreted as Today/default-plan instances, so upgrades do not invalidate existing home-screen widgets.

## Freshness
Widget refresh scheduling is deliberately independent from Android notification permission. The app uses a dedicated inexact alarm for the closest relevant time boundary plus live domain invalidation while StudyFlow is running. System boot/time/timezone/date/package-replacement recovery re-arms the widget path.

`updatePeriodMillis=0` is intentional: the domain-aware scheduler avoids Android's coarse periodic widget refresh interval while still supporting process-dead time transitions.
