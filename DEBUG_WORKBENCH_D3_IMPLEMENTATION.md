# StudyFlow Debug Workbench D3

D3 replaces the shell placeholder with a typed probe registry. It adds six read-only platform-health probes and ports the six Phase 27 runtime smoke operations into the same workbench.

The read-only suite never runs reminder reconciliation or widget refresh. Those operations are labelled as visible side effects and require separate taps. Missing default-plan state is `BLOCKED`, not `FAILED`. The corrective seal records a sanitized start and terminal event for every attempted probe. Suspend work is cooperatively timed out, while blocking framework work is moved off the UI thread and wrapped in `runInterruptible` so cancellation can interrupt compatible JVM calls.

The process-exit probe exports only a count. It never reads descriptions or trace streams. The Android backup probe inspects configuration only and does not claim to execute an OS backup. The widget probe verifies device support, receiver registration, enabled state, provider metadata, and bound IDs without claiming visual rendering.
