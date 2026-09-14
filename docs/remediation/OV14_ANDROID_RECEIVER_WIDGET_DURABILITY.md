# OV14 — Android receiver, deep-link, widget & device-target durability

OV14 hardens the Android-side durable execution boundary after the SFLP transport/security waves.

## Closed findings

- `S5-03`: TV credentials are bound to the selected receiver identity before persistence. The phone refuses to send authenticated control/presentation/continuity requests when the selected receiver does not match the persisted service/host/port binding.
- `S5-04`: reminder destination context is carried on the launch Intent itself and MainActivity consumes that Intent directly, with the legacy in-memory launch store retained only as a same-process fallback.
- `S5-09`: reminder and widget BroadcastReceiver mutation paths now journal accepted work before launching asynchronous mutation coroutines and settle the record only after completion or failure.
- `DS2-06`: widget `START_NEXT` now treats only an active session for the configured widget plan as active; unrelated active sessions no longer hijack a configured widget.
- `DS2-28`: TV domain-action journal keys and TV SFLP operation/entity ids use full SHA-256 action-id digests instead of filtered/truncated identifiers.

## Validation

The overlay installs `verifyOv14AndroidReceiverWidgetDurability` and an apply-time validation hook. The hook intentionally does not stop Gradle daemons. The Gradle task compiles/tests native modules and runs the packaged source/Web parity evidence script.
