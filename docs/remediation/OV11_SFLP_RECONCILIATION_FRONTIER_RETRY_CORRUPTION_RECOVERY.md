# OV11 — SFLP reconciliation, frontier, retry & corruption recovery

## Objective

Make SFLP reconciliation truthful under gaps, malicious/stale peer frontiers, transport failure, malformed remote data, restart-persistent retry state, and corrupted local persistence. A peer must never be able to acknowledge history that was not proven applied, and recovery must preserve evidence instead of silently erasing bad state.

## Finding closure matrix

| Finding | Closure | Regression evidence |
|---|---|---|
| `S4-07` | Web `frontier()` advances each origin only through contiguous journal sequences. `validateSyncBatch()` requires the declared `through` frontier to equal the contiguous advance from the supplied base. | `ov11_reconciliation_runner.mjs` sparse sequence 1/3 → frontier 1, then sequence 2 → frontier 3; source verifier checks contiguous logic. |
| `S4-08` | Added an actual Node-loaded committed Web runtime connecting over RFC6455 to native `StudyFlowWebSocketTransport`, for both Web and Web-TV identities. | `StudyFlowWebSocketBrowserInteropTest.committedBrowserRuntimeExchangesRealSflpEnvelopesWithNativeTransport`. Existing PARITY04 remains as semantic/vector coverage rather than sole interoperability proof. |
| `S4-15` | Added persisted workspace/peer retry state; native runtime checks it before session/socket construction; Desktop reconnect loop checks retry deadline before invoking reconciliation; Web does the same before authenticated transport connect. | `persistedPeerBackoffDefersBeforeAnyPeerNetworkMethodRuns`, `runtimeBackoffGatesSessionCreationBeforeTrustOrSocketWork`, Web `connectCount == 1` retry/defer test, Desktop source evidence. |
| `S4-18` | Strict decode failures create bounded corruption ledger records and preserve the original encoded state in quarantine before returning recovery defaults. Desktop uses quarantine files; Android/TV use quarantine preference keys; Web uses a separate localStorage quarantine namespace. | `DesktopStudyFlowOv11PersistenceRecoveryTest`, Web invalid-JSON quarantine case, source verifier across Desktop/Android/TV/Web persistence. |
| `DS2-02` | Retryable `StudyFlowRemoteProtocolException`, continuation, and peer-unavailable errors become `INTERRUPTED`; sync-batch/journal/model integrity violations become `TERMINAL_FAILURE` rather than escaping reconciliation. | `malformedRemoteBatchBecomesStructuredTerminalFailureInsteadOfEscaping`, `retryableAndFatalRemoteErrorsAreClassifiedDifferently`. |
| `DS2-03` | Coordinator tracks `attemptedOperationIds` and marks only those entries failed. Peer retry state is maintained independently per workspace+peer. | `transientFailurePenalizesOnlyOperationsThatWereActuallyAttempted`. |
| `DS2-29` | Advertised peer frontiers are unverified hints. Web outbound suppression uses only `verifiedRemoteFrontiers` derived from successful apply responses; impossible regression/gap claims terminate reconciliation. | Web malicious frontier `999` test proves the unsent local operation is still pushed and only acknowledged after apply. |

## Native retry state model

`StudyFlowPeerRetryState` contains workspace, peer, attempt count, last attempt, next allowed attempt, and bounded failure text. It is persisted with the outgoing queue snapshot so process restart does not reset connection pressure. `StudyFlowOutgoingOperationQueue` exposes the peer deadline separately from per-entry retry deadlines.

The order of operations in `StudyFlowPeerRuntime.reconcileWith` is intentional:

1. resolve the workspace runtime;
2. inspect persisted peer backoff;
3. return `DEFERRED_BY_BACKOFF` if the deadline is in the future;
4. only then select/open the remote session and perform trust/network work.

This makes backoff a real transport gate rather than metadata attached after a socket has already been attempted.

## Remote-batch integrity

Native reconciliation converts malformed batch conditions into structured terminal results. Web validation additionally rejects:

- workspace/base mismatch;
- duplicate operation IDs;
- duplicate origin sequence numbers;
- untrusted operation origins;
- non-contiguous or falsified `through` frontiers;
- verified frontier regression.

These failures are not retried as ordinary network interruptions.

## Persistence quarantine

Every corruption record contains:

- persisted-state kind;
- original storage key/path;
- observation time;
- quarantine reference;
- SHA-256 of the raw encoded state;
- bounded decode/invariant failure reason.

The ledger is bounded to prevent diagnostics from becoming an unbounded storage sink. The quarantined payload remains available for diagnosis/recovery while the primary malformed record no longer blocks startup.

## Validation

`verifyOv11SflpReconciliationRecovery` depends on:

- `:core:link:test`;
- `:desktopApp:test`;
- `:app:compileDebugKotlin`;
- `:app-tv:compileDebugKotlin`.

It then runs the packaged OV11 validator, which recompiles the committed Web runtime, runs OV09 and OV10 compatibility runners, OV11 reconciliation/recovery cases, PARITY04 semantic vectors, and focused Python regression tests. Artifact apply invokes this Gradle gate automatically.
