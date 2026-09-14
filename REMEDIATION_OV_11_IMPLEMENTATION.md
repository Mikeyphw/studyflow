# StudyFlow Remediation OV11 Implementation

## Campaign position

- **Overlay 11 of 30**
- Name: **SFLP reconciliation, frontier, retry & corruption recovery**
- Baseline: successful OV10 repository state (`b0b86e5e` on the user repository)
- Primary target: `studyflow_android`
- Runtime scope: `core:link`, Web, Desktop, plus Android/TV persistence consistency for the shared SFLP state family.

## Delivered remediation

OV11 closes the seven reconciliation/recovery findings assigned by the final 30-overlay roadmap:

- `S4-07` — Web frontiers are now contiguous-only. Sparse journals cannot advertise sequence gaps as acknowledged history.
- `S4-08` — browser/native interoperability now has a real committed JavaScript runtime ↔ native WebSocket envelope exchange test for both Web and Web-TV identities instead of relying only on in-process semantic vectors.
- `S4-15` — persisted per-peer exponential backoff is consulted before a socket/session is created and is also honored by Desktop's periodic reconnect loop.
- `S4-18` — malformed persisted identity/trust/membership/endpoint/queue/journal state is quarantined with SHA-256 evidence and a bounded corruption ledger before recovery defaults are used.
- `DS2-02` — malformed/invalid remote sync batches become structured terminal reconciliation results; retryable remote/transport failures become structured interrupted results.
- `DS2-03` — transient peer failures penalize only operation IDs that were actually attempted in the failed exchange, while peer-level retry state is isolated by workspace + peer.
- `DS2-29` — Web no longer trusts an advertised remote frontier as acknowledgment. Outbound suppression uses only frontiers verified by successful apply responses; spoofed/gapped frontier claims fail closed.

## Runtime details

### Native reconciliation and retry

`StudyFlowOutgoingOperationQueue` now persists `StudyFlowPeerRetryState` separately from per-operation retry metadata. `StudyFlowReconnectionCoordinator` checks this state before peer methods are invoked, records retryable connection failures consistently, distinguishes terminal integrity/protocol failures, and clears peer backoff only after successful convergence.

`StudyFlowPeerRuntime.reconcileWith` consults the coordinator's persisted backoff before route selection/session construction, ensuring a deferred retry does not create a socket. `DesktopStudyFlowLinkService` also consults the same retry deadline before its periodic reconnect attempt.

### Persisted-state recovery

`StudyFlowPeerPersistenceCodec` carries peer retry state in outgoing-queue snapshots and defines a common corruption-record format. Desktop persistence moves corrupt raw files into a quarantine directory; Android and Android TV preserve corrupt raw SharedPreferences values under quarantine keys. Each record captures state kind, original key/path, timestamp, quarantine reference, SHA-256 digest, and bounded diagnostic reason.

Recovery remains fail-safe: the corrupt primary value is removed only after quarantine evidence is written, and callers receive an empty/default model rather than a partially decoded state.

### Web frontier and acknowledgment truth

The Web SFLP runtime now validates batch bases, operation identity/sequence uniqueness, trusted origins, and declared `through` frontiers. Frontiers advance only over contiguous origin sequences. Remote frontier advertisements are hints only; successful `sync.apply` responses establish the verified peer frontier used for later outbound suppression.

Queue acknowledgment is exact-operation based for attempted pushes. A peer advertising an impossible high frontier cannot cause a local operation to be removed or skipped before the operation is actually applied.

### Browser/native acceptance

`StudyFlowWebSocketBrowserInteropTest` launches the committed `web/runtime/sflp_link.js` under Node and connects its real `StudyFlowWebSocketTransportAdapter` to the native `StudyFlowWebSocketTransport`. Both Web and Web-TV sender identities exchange encoded SFLP `sync.frontier` request/response envelopes with the native codec.

## Validation contract

The overlay registers `verifyOv11SflpReconciliationRecovery`, which owns native compile/test coverage and then invokes `scripts/validate_ov11_sflp_reconciliation_recovery.sh` for source evidence plus Web behavior/parity. The artifact validation hook invokes that Gradle task automatically during Devtool apply.

The shell validator does **not** stop Gradle daemons and does not launch nested Gradle validation.
