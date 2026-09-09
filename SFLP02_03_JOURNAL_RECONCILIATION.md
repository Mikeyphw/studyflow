# SFLP02 + SFLP03 — canonical journal and reconciliation

Status: **COMPLETE**

This combined delivery establishes the replication semantics below SFLP transport and device trust.
It does not claim that Android, Desktop, Web, Android TV, or Web TV are live SFLP peers yet; client and
transport wiring remains SFLP04-SFLP12.

## SFLP02 delivered

- Canonical serializable `StudyFlowDomainOperation` with operation/workspace/origin/entity identity,
  per-origin sequence, entity revision, Lamport-style logical timestamp, operation type, wall-clock time,
  typed mutation/tombstone kind, and JSON payload.
- Canonical operation-type constants for the roadmap examples: goals, question attempts, review grading,
  session lifecycle, materials, topic priority, and schedule movement.
- Append-only `StudyFlowOperationJournal` over a storage contract, with an in-memory reference store.
- Idempotent duplicate suppression and fail-closed rejection of reused operation IDs or origin sequences.
- Automatic local origin sequence, entity revision, and logical timestamp advancement.
- Deterministic replay independent of arrival order.
- Per-origin contiguous vector frontiers that do not skip gaps.
- Missing-operation discovery bounded by a caller-selected batch limit.
- Snapshot checkpoints containing a frontier, operation count, timestamp, and canonical JSON state while
  retaining the complete append-only history, plus deterministic replay of operations recorded after a checkpoint.
- First-class tombstone operations; deletions are replicated history rather than silent physical removal.

Storage durability is intentionally adapter-owned. SFLP02 defines the canonical store contract and semantics;
Android/Web/Desktop persistence adapters are wired during their SFLP08-SFLP10 integration phases.

## SFLP03 delivered

- Typed `sync.frontier`, `sync.pull`, and `sync.operations` SFLP payloads and envelope builders.
- Peer vector-frontier exchange and missing-operation discovery.
- Bounded operation batches with explicit base and through frontiers plus `hasMore` continuation state.
- Receiver continuation checks that reject skipped batches and tell callers to restart from receiver truth.
- Batch-integrity validation before journal mutation.
- Retry-safe duplicate suppression, making replay of an already-delivered batch idempotent.
- Interrupted-sync recovery by requesting again from the receiver's current frontier.
- Deterministic conflict detection for concurrent operations targeting the same entity revision.
- Pluggable conflict handlers with deterministic-winner default and keep-all support for append-only events.
- Deterministic resolved replay while preserving every original operation in the journal.
- Language-neutral SFLP sync JSON Schema and golden frontier/pull/operations vectors mirrored into JVM tests.

## Invariants

1. Operation IDs are immutable identities. Reusing an ID with different content is corruption.
2. `(originDeviceId, originSequence)` identifies one operation only.
3. A frontier advances only through contiguous origin sequences; receiving sequence 5 never implies 4 exists.
4. Journal history is append-only. Conflict resolution affects replay/projection, not historical storage.
5. A batch is accepted only if local truth covers its base frontier and the batch exactly reaches its declared
   through frontier.
6. Reapplying an identical batch is safe and produces duplicates rather than duplicate history.
7. Peers can recover from interruption without a transport-specific cursor: the receiver frontier is the cursor.
8. No SFLP02/SFLP03 class depends on Android, browser, desktop UI, sockets, NSD, WebSocket, or trust APIs.

## Validation

`verifySflp02Sflp03JournalReconciliation` depends on `:core:link:test` and additionally checks source contracts,
wire schema/golden vectors, documentation truthfulness, line-length policy, and preservation of SFLP01 and the
PARITY01-PARITY03 baseline.
