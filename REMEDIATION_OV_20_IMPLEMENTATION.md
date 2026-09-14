# Remediation OV20 — Native TV materials, focus, layout & projection resilience

OV20 closes the native Android TV material/viewer/layout/presentation-feed findings assigned to overlay 20 of 30.

Closed findings:

- `S8-08` — Material projection no longer exposes phone-local `content://`, `file://`, or `android.resource://` URIs to the TV. Presentation descriptors accept inline text, TV-resolvable HTTP/cache/transfer URIs, or a `studyflow-transfer://` transfer id.
- `S8-09` — PDF navigation can use the native renderer page count after opening a document instead of remaining capped by a missing projection hint.
- `S8-10` — Native media actions capture the current `VideoView` position before play/pause or seek reduction so playback does not reset to the stale projection position.
- `S8-16` — Dynamic Android `View` focus wiring now applies the deterministic TV focus model to the live rendered graph and skips disabled items.
- `S8-17` — Long TV rows that contain schedule/library/material controls are wrapped in scroll-safe horizontal containers instead of depending on fixed-width rows.
- `S8-20` — Presentation feeds are failure-isolated; a failed mapper/source is recorded in `failedFeeds` and does not prevent healthy feeds from being projected.

Validation:

- `verifyOv20NativeTvMaterialsFocusProjectionResilience` wires native compile/tests plus the packaged OV20 source verifier.
- `.devtool-artifact/validation/validate.sh` invokes the Gradle task directly and does not stop Gradle daemons.
- Packaged source evidence checks S8-08, S8-09, S8-10, S8-16, S8-17, and S8-20.
