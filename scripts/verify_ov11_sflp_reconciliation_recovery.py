#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]


def require(rel: str, *tokens: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"OV11 FAIL: missing {rel}")
    text = path.read_text(encoding="utf-8")
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"OV11 FAIL {rel}: missing {missing}")
    return text

# S4-07 + DS2-29 — sparse operations never become contiguous acknowledgements,
# and the browser cannot trust an advertised high frontier for outbound suppression.
web = require(
    "web/src/sflp_link.ts",
    "while(set.has(contiguous+1))contiguous++",
    "verifiedRemoteFrontiers",
    "validateSyncBatch",
    "SFLP sync batch declares a spoofed or gapped through frontier",
    "SFLP peer regressed below its verified frontier",
    "this.acknowledge(peerDeviceId,workspaceId,attemptedOperationIds)",
    "quarantineReference",
    "peerRetryStates",
)
if "function safeParse(" in web:
    raise SystemExit("OV11 FAIL: corrupt Web peer state still silently collapses through safeParse")

# S4-15 + DS2-03 — peer backoff is durable and gates the actual runtime connection,
# while failure accounting touches only operations that were attempted.
offline = require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueue.kt",
    "StudyFlowPeerRetryState",
    "deferredResultIfBackoff",
    "recordConnectionFailure",
    "attemptedOperationIds",
    "outgoingQueue.markFailed(peer.deviceId, attemptedOperationIds",
    "remoteError.retryable",
    "TERMINAL_FAILURE",
)
peer = require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
    "workspaceRuntime.reconnect.deferredResultIfBackoff(peerDeviceId, now)?.let { return it }",
    "StudyFlowRemoteSyncPeerSession(",
    "fun nextReconnectAt(",
    "recordConnectionFailure(",
)
if peer.index("deferredResultIfBackoff(peerDeviceId, now)") > peer.index("StudyFlowRemoteSyncPeerSession(", peer.index("fun reconcileWith(")):
    raise SystemExit("OV11 FAIL: persisted backoff does not gate remote session construction")
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt",
    "runtime.nextReconnectAt(workspaceId, peer.peer.deviceId)",
    "if (retryAt == null || retryAt <= now)",
)

# S4-18 — every persisted native state family touched by generic SFLP has a
# quarantine ledger rather than getOrNull()/empty silent reset.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerPersistence.kt",
    "StudyFlowPersistedStateKind",
    "StudyFlowPersistenceCorruptionRecord",
    "encodeCorruptionRecords",
    "peerRetryStates",
)
for rel in [
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkPersistence.kt",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkStateStore.kt",
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpStatePersistence.kt",
]:
    require(rel, "decodeOrQuarantine", "corruptionRecords()", "encodedSha256", "StudyFlowPersistedStateKind.JOURNAL")

# DS2-02 — remote continuation/integrity/model failures are represented as a
# bounded result and cannot escape as a generic hard crash.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueue.kt",
    "catch (failure: StudyFlowSyncContinuationException)",
    "catch (failure: StudyFlowSyncBatchIntegrityException)",
    "catch (failure: StudyFlowJournalIntegrityException)",
    "catch (failure: IllegalArgumentException)",
)

# S4-08 — this must be a real JavaScript WebSocket client using the committed
# browser runtime against the native transport, not another Kotlin peer labelled WEB.
interop = require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebSocketBrowserInteropTest.kt",
    "committedBrowserRuntimeExchangesRealSflpEnvelopesWithNativeTransport",
    "web/runtime/sflp_link.js",
    "StudyFlowWebSocketTransportAdapter",
    "ProcessBuilder(\"node\"",
    "OV11_REAL_BROWSER_NATIVE_INTEROP",
)

require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOv11ReconciliationRecoveryTest.kt",
    "persistedPeerBackoffDefersBeforeAnyPeerNetworkMethodRuns",
    "transientFailurePenalizesOnlyOperationsThatWereActuallyAttempted",
    "malformedRemoteBatchBecomesStructuredTerminalFailureInsteadOfEscaping",
    "retryableAndFatalRemoteErrorsAreClassifiedDifferently",
)
require(
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowOv11PersistenceRecoveryTest.kt",
    "corruptIdentityIsQuarantinedAndRecordedInsteadOfSilentlyReset",
    "peerBackoffPersistsAcrossDesktopPersistenceRecreation",
)
require(
    "web/tests/ov11_reconciliation_runner.mjs",
    "spoofed high frontier must not suppress unverified local history",
    "deferred retry must gate the actual WebSocket connection attempt",
    "OV11_WEB_RECONCILIATION_RECOVERY: PASS",
)
require(
    "build.gradle.kts",
    "verifyOv11SflpReconciliationRecovery",
    ":core:link:test",
    ":desktopApp:test",
    "scripts/validate_ov11_sflp_reconciliation_recovery.sh",
)
require(".devtool.toml", '"verifyOv11SflpReconciliationRecovery"')

print("OV11 reconciliation/recovery source evidence: PASS (7/7 owned findings)")
