# CSOV15 Optional Instrumentation Validation

CSOV15 adds an optional connected-device validation lane for the optional cloud-sync work.
It does not make connected hardware mandatory for ordinary source gates.

## Host behavior

- `recordOptionalInstrumentationValidation` uses `adb` when available.
- It prefers `ANDROID_SERIAL` when that device is online.
- Without `ANDROID_SERIAL`, it tries `localhost:5555` so Termux/ADB-over-localhost workflows are covered.
- It writes `build/reports/studyflow/optional-instrumentation-validation.json`.
- It records `SKIPPED when no device is connected` unless `STUDYFLOW_REQUIRE_CONNECTED_DEVICE=true` is set.
- When a device is connected, instrumentation failures are real failures.

## Root behavior

The runner probes root when available without requiring it:

- First probe: `adb shell su -c id`.
- Optional deeper probe: set `STUDYFLOW_ADB_TRY_ROOT=true` to try `adb root`.
- Root absence is recorded as unavailable, not as a failure.

## Test coverage

The default connected task is `:app:connectedDebugAndroidTest`.
Override it with `STUDYFLOW_OPTIONAL_CONNECTED_TASKS`, for example:

```bash
STUDYFLOW_OPTIONAL_CONNECTED_TASKS=":app:connectedDebugAndroidTest :feature:workspace:connectedDebugAndroidTest" \
  ./gradlew recordOptionalInstrumentationValidation
```

The added Android test verifies the optional-sync screen on-device:

- Offline/local-first path remains visible.
- Folder selection remains discoverable.
- Manual folder-sync actions remain discoverable.
- Background-sync status is visible but still optional.
