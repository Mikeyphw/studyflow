# ATT03 — Android Widget Suite 2.0

## Status
Implemented as the full ATT03 product slice.

## Product scope
ATT03 upgrades the existing StudyFlow Today widget in place so already-installed widgets remain valid while new instances gain per-widget configuration. One provider supports five widget modes: **Today**, **Next**, **Reviews**, **Session**, and **Compact**.

## Per-instance configuration
Each widget can independently follow the app default plan or pin an active plan, choose a mode, and decide whether review counts should appear in compact fallbacks. Configuration is persisted in `studyflow_widget_configuration_v2`, restored across widget-ID remapping, removed when an instance is deleted, and exposed through Android's reconfiguration contract.

## Freshness and notification independence
Widget freshness no longer depends on notification permission or reminder-alarm eligibility. `StudyFlowWidgetFreshnessScheduler` uses its own inexact `AlarmManager.setWindow()` wakeup and schedules the nearest meaningful domain boundary: minute ticks for a running session, the next planned block, the next upcoming review, session target completion, or local day rollover. Boot, wall-clock, timezone, date, and package replacement recovery refresh the widget even when notification reconciliation cannot deliver notifications.

The provider intentionally retains `updatePeriodMillis="0"`; ATT03 owns a more precise, domain-aware refresh scheduler rather than Android's coarse periodic widget interval.

## Modes
- **Today** — due reviews, today's planned block count, next activity, progress/missed count, review navigation, Start/Session action.
- **Next** — next planned activity, scheduled time, Start and exact schedule navigation.
- **Reviews** — due/upcoming review counts and direct Review now navigation.
- **Session** — active-session title, elapsed/target progress, pause/resume and exact session navigation; falls back to Start next when idle.
- **Compact** — responsive one-line glance surface for active session, next study, due reviews, or an all-clear state.

Small launcher allocations automatically use compact rendering regardless of selected mode.

## Actions
Widget actions are real product actions, not decorative controls. `Start` creates a Study Session from the selected plan's next planned activity. If another session is already active, StudyFlow opens that active session instead of silently failing. Session mode supports Pause/Resume using `ManageStudySession`; review/schedule/session navigation reuses the same typed reminder deep-link path as ATT02.

## Migration and recovery
Existing pre-ATT03 widgets have no configuration record and therefore migrate implicitly to `Today + follow app default plan`. A pinned plan that becomes unavailable renders an explicit recovery state and opens configuration instead of silently switching plan identity.

## Diagnostics
Debug Workbench widget health/refresh probes report bound and configured instance counts, active mode distribution, the next scheduled freshness epoch, and whether independent freshness scheduling is active.

## Validation
ATT03 adds:
- `StudyFlowWidgetSnapshotProjectorAtt03Test`
- `StudyFlowWidgetConfigurationStoreAtt03Test`
- `StudyFlowWidgetAtt03SourceContractTest`
- `scripts/verify_att03_android_widget_suite.py`
- Gradle gate `verifyAtt03AndroidWidgetSuite`

The roadmap remains intermediate: apply ATT03 with `--no-validate`; ATT12 owns the final cross-platform/full Android release validation campaign.
