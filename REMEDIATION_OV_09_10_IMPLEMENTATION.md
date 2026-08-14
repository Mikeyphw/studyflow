# StudyFlow OV-09 + OV-10 Remediation — Corrected Promise Audit

This cumulative intermediate overlay supersedes the earlier OV-09+OV-10 v1 artifact. It is built against the fresh uploaded StudyFlow snapshot representing the tree after OV-07+OV-08 (`fdb9b35` milestone per the handoff) and implements the locked OV-09/OV-10 acceptance checklist.

- **OV-09 / RM-035–RM-040:** reminder correctness/delivery identity, widget freshness/default ownership, locale/DataStore ownership and recovery, localization, accessibility and responsive UI.
- **OV-10 / RM-054–RM-055 plus residual RM-056/RM-025 adoption:** truthful recovery/retry semantics, process-death restoration, safe partial-operation reconciliation, and lifecycle/concurrency hardening.

## Corrective promise re-audit

After the first OV-09+OV-10 package was produced, the handoff checklist and BH-39, BH-40, BH-41, BH-42, BH-43, BH-44, BH-58, BH-59 and relevant BH-60 findings were re-read against the cumulative tree. The second audit found additional gaps and this corrected artifact fixes them:

1. **Pre-Android-13 locale persistence:** `localeConfig` and AppCompat locale APIs were present, but AppCompat automatic locale persistence for Android 12/API 32 and lower was not opted in. The manifest now registers `AppLocalesMetadataHolderService` with `autoStoreLocales=true`; Android/AppCompat per-app locale remains authoritative while DataStore remains only a mirror.
2. **Preferences read I/O classification:** corruption recovery existed, but ordinary Preferences DataStore read `IOException`s were not explicitly classified. Reads now rethrow `AppPreferencesStorageException`, which reaches the root `Failed` load-state boundary and its real Retry instead of silently substituting defaults.
3. **Stable reminder occurrence time:** due-review and missed-activity occurrence IDs were durable, but their original scheduled timestamps could slide forward on repeated reconciliation. The planner now reuses the persisted original schedule time keyed by occurrence ID and only recomputes quiet-hour delay.
4. **Crash-safe reminder reconciliation:** alarm replacement no longer creates a clear-before-read or schedule-before-journal gap. The coordinator builds from durable state, cancels the previous Android alarms without deleting the journal, commits the desired occurrence journal, then installs the desired alarms. A restart can reconstruct any interrupted installation without an untracked newly scheduled identity.
5. **Accessibility scope completeness:** the first package fixed Settings/Reminders and selected forms but did not cover every production Switch/Checkbox row implicated by the promise. Plan creation/settings, portability, schedule dialogs, subject bulk selection and mock-exam filtering now also use one row-level target with explicit merged descendants, the correct semantic role, and a non-clickable child Switch/Checkbox.
6. **Compact/large-font schedule forms:** schedule rule/activity dialogs now scroll vertically and their day/type controls scroll horizontally rather than becoming unreachable/overflowing.
7. **Fatal-error recovery boundary:** the shared Flow-to-load-state adapters previously rethrew cancellation but still flattened fatal `Error`s into ordinary `Failed` states. Both adapters now rethrow fatal `Error`s, matching `runRecoverableOperation`.
8. **Suspend smoke-probe boundary:** the runtime smoke workbench still wrapped suspend probe work in `runCatching`. It now uses `runRecoverableOperation`, so cancellation and fatal errors are not normalized as recoverable probe failures.

## Production-path acceptance points

### RM-035 — reminder planner enable / quiet-hour semantics

- The Review toggle gates due-review reminders only; planned activity, missed activity and daily summary candidates remain independent.
- Quiet-hour evaluation uses each candidate's scheduled occurrence time, not reconciliation time. Candidates inside quiet hours are delayed to the end of the relevant quiet window rather than dropped.
- Equal quiet-hour endpoints retain the established “quiet hours disabled” meaning.
- Original due/missed occurrence schedule time is persisted and reused across reconciliation/process recreation; quiet-delay output can be recalculated from that stable occurrence basis when settings change.
- Planner regression coverage includes future/cross-midnight delay, review-only disablement, delivered-occurrence chaining, stable non-sliding due/missed time, and quiet-hour reevaluation from persisted time. Existing planner coverage retains the equal-endpoint contract.

### RM-036 — Android reminder identity / chaining

- Alarm and app-open `PendingIntent` identity is URI/occurrence based. Request code is not used as a bounded identity bucket, and durable bookkeeping stores full plan/occurrence string identity.
- Android scheduling uses `AlarmManager.setWindow`; exact-alarm capability is not claimed as a product requirement.
- Runtime readiness checks both app notification enablement and Android 13+ `POST_NOTIFICATIONS` permission.
- Delivery re-resolves live plan/settings/review/schedule state, posts only while notifications remain allowed, marks the occurrence delivered only after a successful post, and reconciles again to advance later occurrences.
- Reconciliation preserves old durable state while repository reads occur, persists the desired replacement journal before installing the new Android alarms, and therefore makes interrupted schedule installation reconstructible.
- Android instrumentation exercises occurrence-specific PendingIntent identity, durable scheduled/delivered occurrence state across store recreation, and chaining to the later planned occurrence.

### RM-037 — widget default / freshness / loading

- Widget resolution uses only the stored canonical default Study Plan and never substitutes an arbitrary first ACTIVE plan.
- Application-owned invalidation observes default plan/language/plan changes plus canonical schedule, review, session and plan-settings flows; boot/date/time/time-zone events also reconcile secondary surfaces.
- Refresh calls are coalesced with a running/dirty single-worker gate.
- Widget rendering starts in a neutral Loading state and then resolves to NoPlan or Ready, avoiding the previous false no-plan flash.
- Ready taps carry canonical plan identity in URI-based PendingIntent identity.

### RM-038 / RM-039 — locale, DataStore and localization

- Android/AppCompat per-app locale is authoritative. Preferences reads derive language from `AppLocaleController.currentLanguage()`; the DataStore language key is a write-only mirror for app preference persistence and is never replayed over a newer platform locale.
- AppCompat automatic locale storage is enabled for Android 12/API 32 and lower in the manifest, while Android 13+ continues through platform per-app locale integration.
- Preferences DataStore has `ReplaceFileCorruptionHandler`. Ordinary read `IOException`s are explicitly classified and reach the root recoverable load-state UI; write failures reach operation boundaries rather than being silently swallowed.
- Settings writes expose Idle/Running/Succeeded/Failed state. Failed state keeps the exact mutation and Retry executes that same mutation.
- Default-plan conditional clear/set-if-absent operations remain atomic `DataStore.edit` mutations against the current stored value.
- Reminder and analytics domain output remains semantic rather than raw user-facing English; localized resources/plurals create presentation copy. Notification channel name/description are recreated safely on locale changes without replacing user-owned channel behavior, and widgets refresh after app/system locale changes.
- Domain localization regression coverage rejects the legacy raw reminder/insight presentation strings.

### RM-040 — accessibility / responsive UI

- Production Switch/Checkbox preference rows in Settings, Reminders, plan creation/settings, portability, schedule dialogs, subject bulk selection and mock-exam filtering use one row-level toggle target, explicit merged descendants, the appropriate Switch/Checkbox role, and a non-clickable child control.
- Settings language/theme rows expose one selectable RadioButton-role row; inner `RadioButton` has `onClick=null` and therefore does not create a second activation target.
- Question Editor and schedule long forms are vertically scrollable under compact/large-font constraints. Question difficulty, subject filter/sort and schedule day/type controls can scroll horizontally where fixed rows would overflow.
- Accessibility regression coverage includes source contracts for every corrected row family plus Compose instrumentation for single interactive target/labeled merged semantics and 200% font-scale compact layouts. Question Editor restoration instrumentation also exercises compact 200% font scale.

### RM-054 — truthful recovery and Retry

- `runRecoverableOperation` rethrows `CancellationException` and catches `Exception` only; fatal `Error`s remain fatal.
- Shared Flow-to-load-state adapters now follow the same rule: cancellation and fatal `Error`s are rethrown instead of converted into normal Failed UI state.
- The runtime smoke workbench uses the recoverable-operation boundary rather than suspend `runCatching`.
- Retry-capable error panes rerun the failed operation; no OV-09/10 Retry action is wired to navigation/back.
- Review Center, Settings and Sync mutations expose explicit in-flight/failure/success/retry state. Search and recommendations have in-screen failure Retry.
- Review Retry keeps the exact mutation and reconciles the current live queue before reissuing potentially non-idempotent writes; already-applied grade/skip/error schedule/error resolve/flashcard create is acknowledged instead of blindly duplicated. Flashcard creation retains one stable ID across retries.
- Failure-injection/regression coverage spans Settings, Sync, Search/recommendations and Review partial-commit semantics. Load-state tests now also assert fatal errors are not flattened.

### RM-055 / residual RM-056 — process death, lifecycle and concurrency

- Sample provisioning writes a durable journal before the atomic Room provisioning graph. Startup recovery resolves the provisioning key against committed metadata, acknowledges the default with set-if-absent semantics and exposes the completed plan without creating a second graph.
- Portability saves non-secret picker/export/import context and URI state, while passwords remain process-memory-only. Encrypted export returning after recreation fails closed, cleans the destination and requires password re-entry; import URIs request persistable read access.
- Question Editor restores dialog-open state and its full draft with a saveable state saver. Subject/Topic/import ownership IDs and drafts are saveable.
- Search saves query/filter/search intent, keeps result snapshots ephemeral, starts restored intent in Loading and reruns against the repository rather than displaying a false empty result.
- Ephemeral in-flight flags remain ordinary `remember`, so process recreation cannot resurrect a disabled control with no owning coroutine.
- Focus/session user drafts remain saveable while transition flags remain ephemeral. Focus note autosave continues through the previously hardened field-level session note mutation; schedule/default-plan/review paths retain the CAS/transaction/idempotency primitives established by earlier overlays.

## Regression coverage added or expanded

- `StudyReminderPlannerOv09Test` and existing reminder planner suites.
- `StudyReminderOccurrenceInstrumentationTest`.
- `Ov09LocaleReminderContractTest`.
- `DataStoreAppPreferencesRepositoryContractTest`.
- `DomainLocalizationGuardTest`.
- `RecoverableOperationTest` plus `StudyFlowLoadStateFlowTest` fatal-error coverage.
- `SettingsAccessibilityInstrumentationTest`, `SettingsContractTest`.
- `PlanSwitchAccessibilityInstrumentationTest`, `PlanAccessibilityContractTest`.
- `ScheduleAccessibilityInstrumentationTest`, `ScheduleAccessibilityContractTest`.
- `StudyRemindersScreenTest` accessibility contract.
- `StudyContentUiModelsTest` accessibility contract.
- `PracticeWorkspaceAccessibilityContractTest`.
- `QuestionEditorRestorationTest` including compact 200% font coverage.
- `SyncRecoveryInstrumentationTest`.
- `SearchStateRestorationInstrumentationTest`.
- `ReviewRecoverySemanticsTest`.
- `SamplePlanProvisionerTest` committed-completion recovery coverage.

## Promise-by-promise source audit

Before packaging the corrected artifact:

- the mandatory OV-09+OV-10 checklist was checked against production wiring and the re-read BH findings;
- **84/84 targeted cumulative source acceptance checks passed**;
- all **15 changed XML files parsed successfully**;
- changed `values` XML files had no duplicate named resources;
- `git diff --no-index --check` emitted no whitespace-error diagnostics;
- production scans found no reminder hash/modulo request-code bucket, exact-alarm capability requirement, Retry-to-navigation wiring, or remaining duplicate child click handlers on production Switch/Checkbox controls.

These are source/static integrity checks only; they are not campaign validation.

## Deferred validation

This is an intermediate overlay. **No Gradle task, unit-test execution, lint, Android-test compilation, instrumentation execution, emulator/device run, Release task, APK assembly or campaign validation was run while producing this corrected package.** The schema-v2 artifact manifest retains relevant future validation tasks, but the campaign rule remains to apply with `--no-validate` and validate only after the final remediation overlay.
