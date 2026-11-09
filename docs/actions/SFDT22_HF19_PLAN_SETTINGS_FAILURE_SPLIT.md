# SFDT22 HF19 — Exact plan-settings failure classification

## Hosted trigger

HF18 run 42542769273 on API36 compiled the real APKs, passed plan creation and subject/topic journeys, and rejected the session journey at `session_running` with `session_start_plan_settings_failed`.

That classification includes either a nullable `observeStoredPlanSettings(...).first()` result or an exception while acquiring that Flow/first emission. HF19 makes the two cases distinct while retaining the original exception and all creation requirements.

## Source changes

* Domain: two new fixed enum categories (`PLAN_SETTINGS_MISSING`, `PLAN_SETTINGS_LOOKUP_FAILED`). The existing `PLAN_SETTINGS` fallback is retained so the HF18 contract and existing callers remain valid. A local boolean prevents the fallback category from overwriting a precise category when the same exception propagates.
* UI: map the two categories to fixed non-sensitive `SnackbarHost` semantics tags. User-facing errors remain generic and no exception messages or IDs are exported.
* Android instrumentation: when real `Operation failed` feedback is seen, map new tags to stable markers; retain the visible, enabled running-control requirement. Real app, real Room database, no injected sessions.
* Host verifier: allowlist and sanitize new reason codes and retain the strict five-journey new-PID seven-screenshot gate.
* Workflow: exact SHA-256 pins for all four modified source files and new HF19 source regression contract/documentation.

## No ungrounded database modification

The actual sub-cause is not yet established. HF19 does NOT silently supply default settings, insert a settings row, bypass validation, modify Room entities, or swallow coroutine cancellation. If the next real hosted run yields `session_start_plan_settings_missing`, audit the plan wizard persistence path; if it yields `session_start_plan_settings_lookup_failed`, diagnose the Flow/Room query failure. Do not merge until the full hosted gate is green.
