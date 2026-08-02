# StudyFlow Debug Workbench D2/D3 Corrective Seal

This corrective seal closes the promise gaps found by the independent D2/D3 audit. It does not add a new workbench lab or expand product scope.

## D2 storage and sharing boundary

`StudyFlowDebugWorkbenchActions` owns timeline reads, timeline copies, history clearing, support-bundle preparation, and share launching. Storage and FileProvider preparation execute on an injected I/O coroutine context. Android share launching returns an explicit result when no activity can receive the intent or when the platform rejects the grant.

Cancellation remains cancellation and fatal JVM errors are not converted into ordinary workbench failures.

## D3 execution boundary

`StudyFlowDebugProbeRegistry` now depends on an injected suspend operation boundary. It records `STARTED` before evaluating default-plan preconditions and records one terminal event for blocked, passed, warning, failed, timed-out, or cancelled execution.

Suspend probes use cooperative coroutine cancellation. Blocking Android framework probes are dispatched away from the UI thread and wrapped in `runInterruptible`, allowing timeout cancellation to interrupt blocking JVM calls that respond to thread interruption. The workbench does not claim an uninterruptible hard deadline for arbitrary platform or vendor code.

The registry catches recoverable `Exception` values only. Cancellation is recorded and rethrown, while fatal `Error` values propagate.

## Platform-state policies

The widget probe distinguishes:

- devices without app-widget support;
- missing receiver registration;
- missing widget-provider metadata;
- disabled providers;
- registered providers with no bound instances; and
- providers with bound instances.

The backup probe combines runtime `allowBackup` state, packaged resource presence, and build-time evidence that both backup-rule resources are linked from the application manifest. Missing or unlinked rules produce a failed configuration result rather than a false pass.

FileProvider health requires a present, non-exported provider with URI grants and path metadata. Process-exit and notification probes preserve their documented API and privacy limits.

## Executable proof

The seal adds real tests against the registry and workbench action classes, including:

- blocked probe start/terminal event pairing;
- cooperative timeout;
- interruptible blocking timeout;
- external cancellation propagation;
- recoverable exception conversion;
- fatal error propagation;
- default-plan forwarding;
- I/O dispatcher use for timeline and clear operations;
- I/O dispatcher use for bundle preparation;
- unavailable bundle preparation;
- share-launch failure; and
- bundle cancellation propagation.

Pure platform-policy tests cover notification, widget, backup, FileProvider, and process-exit classifications.
