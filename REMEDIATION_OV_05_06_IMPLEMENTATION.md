# StudyFlow OV-05 + OV-06 remediation

This intermediate remediation overlay implements the locked roadmap items assigned to:

- **OV-05 — portability / privacy / sync / sharing:** RM-001, RM-041, RM-042, RM-043, RM-044.
- **OV-06 — schedule / session / focus:** RM-021, RM-022, RM-023, RM-024, RM-025 (OV-06-owned portion).

## OV-05 acceptance contract

- Destructive restore validates and stages annex payloads before replacement, creates a durable pre-restore safety snapshot, binds staged bytes and graph changes under one Room transaction boundary, rolls back newly-created bytes on failure, and retains the safety snapshot for recovery.
- Portability dry-run validates complete graph ownership, replacement cleans superseded physical ownership only after database commit, and packages enforce entry-count and byte limits.
- Annex export uses streaming payload sources instead of materializing every annex in memory before ZIP output; streamed payload size/checksum is verified while writing.
- Android backup/device-transfer policy is explicitly disabled/excluded for StudyFlow private data.
- Metadata-private packages pseudonymize identities and annex paths/filenames; `PRIVATE_STRIPPED` always strips history/progress state.
- Authenticated encrypted-import plaintext cache files are truncated/deleted on normal completion and stale files are removed when the portability repository is constructed.
- A database-wide graph revision advances for root/settings/plan-owned mutations and backs checkpoint freshness/change counts.
- The former fake local backup queue is represented truthfully as local checkpoint readiness; preparing a checkpoint is distinct from exporting a backup and graph changes invalidate ready state.
- Failed SAF export destinations are deleted or truncated where the platform permits; generated exam/support cache artifacts have bounded retention.
- FileProvider behavior is covered with instrumentation proving the dedicated shareable annex root is allowed while private plan/snapshot storage is rejected.

## OV-06 acceptance contract

- Schedule CRUD refuses independent mutation of `IN_PROGRESS` activities and rule deletion owns the lifecycle of every non-running generated occurrence while retaining completed history.
- Planner generation honors `replaceUnlocked`, deterministic occurrence IDs, recurrence, day-off, availability, overlap, strategy ratios, lock semantics, local calendar behavior, and missed-session grace.
- Adaptive apply consumes the exact approved preview, fingerprints the schedule, validates all candidate changes before writing, and applies every mutation through CAS inside one Room transaction so stale/conflicting previews apply nothing.
- Adaptive recovery uses user-local calendar semantics, respects day-off/availability/conflicts/locks, contains no executable no-op changes, and refreshes as time advances.
- Study Session start and transitions remain conditional/atomic, preserve one-active ownership, enforce terminal one-way state, validate linked ownership, and remember whether a linked activity started from `PLANNED` or `MISSED` so cancellation restores the correct source state.
- Running/paused elapsed duration uses a monotonic runtime clock. Wall-clock timestamps remain real-world metadata but are clamped to model-valid boundaries when the wall clock moves backward.
- Focus/session screens distinguish loading from no-session state, preserve draft state with saveable state, restrict note writes to active rows, and serialize transition controls with an in-flight guard.

## Post-build promise audit corrections

A second acceptance-contract audit against the original BH-25 through BH-29 and BH-45 through BH-48 findings found and corrected additional edge cases before this overlay was applied:

- Practice/test/mock-exam `configurationJson` question references are now treated as relational ownership: dry-run rejects missing/cross-plan question IDs, CREATE_AS_NEW/MERGE remaps them, and metadata-private export pseudonymizes both `questionIds=` entries and encoded answer question IDs.
- Metadata-private annex table rows redact `displayName` as well as manifest/ZIP entry names and storage references.
- ZIP export now enforces the same entry/per-entry/total uncompressed ceilings as import before writing, so StudyFlow cannot emit a package its own reader rejects.
- Schedule recurrence accepts the app's existing RRULE-compatible `FREQ=WEEKLY[;INTERVAL=n]` form in addition to the canonical planner forms.
- Missed-session rebalance is an all-or-nothing Room CAS mutation set instead of a stale bulk overwrite.
- Adaptive apply's DAO CAS includes `locked = 0`, closing the race where an activity could become locked after preview validation but before mutation.
- Ephemeral transition-in-flight flags are intentionally not saveable across recreation; only user draft/dialog state is saveable.
- Portability rejects FULL packages containing `IN_PROGRESS` planned activities or RUNNING/PAUSED Study Sessions so a live execution state cannot be restored as ordinary graph data.
- Destructive restore staging cleanup is guaranteed even when failure occurs immediately after staging or while creating the safety snapshot.
- Legacy imported-copy and snapshot references are reference-counted during row deletion, migration, orphan cleanup, replacement cleanup, and plan-level physical cleanup so pre-remediation shared paths cannot be deleted while another surviving row still owns them.
- Schedule CRUD cannot create or independently enter `IN_PROGRESS`; linked Study Sessions must match the linked activity's subject/topic/material/video/type ownership.

These corrections preserve the same RM ownership and do not advance OV-07 or later roadmap items.

## Regression coverage added or extended

- Restore failure before database commit preserves the current graph and durable safety snapshot.
- Stale authenticated plaintext temp cleanup.
- Database-wide graph revision mutation coverage.
- FileProvider allowed/rejected root behavior.
- Package entry-count/byte ceilings, streaming annex source, metadata-private identity/filename stripping, embedded practice-question reference remapping, and history stripping.
- Deterministic schedule IDs, day-off suppression, RRULE-compatible recurrence/missed grace, strategy/adaptive semantics, stale lock rejection, missed-rebalance atomicity, and schedule versioning.
- Monotonic Study Session duration across a backward wall-clock jump.
- Cancellation of a session started from a missed activity restores `MISSED` and removes its transient source-state metadata; linked activity/session identity mismatches are rejected.
- Destructive restore staging cleanup on an immediate post-staging failure, plus legacy shared annex/snapshot ownership survival until the last reference is removed.

## Validation status

Per the remediation campaign workflow, this is an **intermediate overlay**. Gradle tests, Android lint, Debug/Release build/package gates, instrumentation, and device validation are intentionally deferred until the final overlay. Apply this artifact using Devtool's `--no-validate` flag. The artifact retains required validation-task metadata for the eventual final validation campaign; it does not weaken the acceptance gate.
