# StudyFlow Desktop DOV04/DOV05 — Bootstrap, Dashboard, and Library

DOV04 and DOV05 move the desktop app beyond a placeholder shell while keeping
Android business logic and UI out of the desktop module.

## DOV04 — desktop bootstrap and dependency wiring

Implemented:

- `StudyFlowDesktopBootstrap` as the desktop composition root.
- `DesktopAppState` for route and selection state.
- `DesktopStudyFlowServices` for domain wiring.
- `InMemoryStudyPlanRepository` implementing `StudyPlanRepository`.
- `InMemoryStudyLibraryRepository` implementing `StudyLibraryRepository`.
- `ManageStudyPlan` and `ManageStudyLibrary` wired to JVM repositories.
- `AppShellReadinessAuditor` reused from `core:domain`.
- `DesktopStudyFlowSampleData` using `core:model` types.

The repositories are intentionally in-memory. They are desktop-safe adapters used
only until the filesystem/backup overlay introduces durable storage. No Android
Room, DataStore, WorkManager, navigation, resource, or feature modules are added.

## DOV05 — dashboard and library desktop screens

Implemented:

- Dashboard route with Study Plan summary metrics.
- Library route with resource summary cards.
- Scrollable material list.
- Material detail shell.
- Video list shell.
- Reusable desktop layout primitives.
- Empty, loading, content, and error state modeling.
- Keyboard/mouse-friendly scrolling through `LazyColumn` and `verticalScroll`.

## Validation

Use Devtool with separate task flags:

```bash
devtool -r "$HOME/Code/studyflow" --target studyflow_desktop validate \
  --task verifyStudyFlowDesktopFoundation \
  --task verifyStudyFlowDesktopShellContracts \
  --task verifyStudyFlowDesktopBootstrapScreens \
  --task :desktopApp:compileKotlin \
  --task :desktopApp:test
```

## Deferred

- Persistent desktop storage.
- File picker implementations.
- Backup/restore filesystem adapters.
- Resource launching.
- Desktop packaging polish.


## Recheck hardening

Empty repositories are now explicitly supported: desktop services use a nullable default plan and an empty library snapshot so the promised empty states are reachable instead of being guarded by a crashing `first()` call.
