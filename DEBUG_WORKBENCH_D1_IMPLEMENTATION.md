# StudyFlow Debug Workbench D1 r2 Implementation

## Scope

D1 r2 delivers the recorder and privacy foundation only. It supersedes the original unsealed D1 artifact. It does
not add a Debug Workbench screen, mutate Room, request permissions, read Logcat, enumerate system alarms, or upload
diagnostics.

## Added and sealed

- pure Kotlin `:core:diagnostics` module;
- structured event and session-policy models;
- no-op and bounded asynchronous rolling JSONL stores;
- conservative privacy redaction for generic details and caller-created exception summaries;
- collision-safe rotation with a monotonic sequence and five retained sessions;
- queue-overflow accounting with deterministic behavior coverage;
- local checksummed support-bundle skeleton that can export writer-failure health evidence;
- Debug-build application/startup instrumentation and Release-build no-op behavior;
- 18 behavior-focused tests plus an independent adversarial validator harness.

## Next

D2 may add the Settings-hosted workbench shell only after D1 r2 passes target-side Devtool compile, resources,
unit-test, lint, and package phases. D2 must not duplicate the recorder or weaken the privacy and failure gates.
