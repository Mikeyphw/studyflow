#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    "SFLP06_07_TOPOLOGY_OFFLINE_QUEUE.md",
    "docs/architecture/SFLP06_07_TOPOLOGY_OFFLINE_QUEUE.md",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopology.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueue.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopologyTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueueReconnectTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopologyOfflineGoldenTest.kt",
    "protocol/sflp/v1/sflp-topology-offline.schema.json",
    "protocol/sflp/v1/golden/routed-envelope.json",
    "protocol/sflp/v1/golden/offline-queue-entry.json",
    "core/link/src/test/resources/sflp/v1/routed-envelope.json",
    "core/link/src/test/resources/sflp/v1/offline-queue-entry.json",
]


def fail(message: str) -> None:
    print(f"SFLP06_07_TOPOLOGY_OFFLINE_QUEUE: FAIL\n- {message}", file=sys.stderr)
    raise SystemExit(1)


def require_text(path: str, needles: list[str]) -> str:
    target = ROOT / path
    if not target.is_file():
        fail(f"missing required file: {path}")
    text = target.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path} missing contract token: {needle}")
    return text


def run_predecessor() -> None:
    result = subprocess.run(
        [sys.executable, "scripts/verify_sflp04_05_identity_transport.py"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        fail("SFLP04/SFLP05 predecessor seal failed:\n" + result.stdout[-4000:])


def validate_json() -> None:
    for relative in REQUIRED:
        if relative.endswith(".json"):
            try:
                json.loads((ROOT / relative).read_text(encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {relative}: {exc}")
    for name in ["routed-envelope.json", "offline-queue-entry.json"]:
        protocol = (ROOT / "protocol/sflp/v1/golden" / name).read_bytes()
        test_resource = (ROOT / "core/link/src/test/resources/sflp/v1" / name).read_bytes()
        if protocol != test_resource:
            fail(f"golden fixture drift between protocol and JVM test resource: {name}")


def validate_topology() -> None:
    topology = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopology.kt",
        [
            "class StudyFlowWorkspaceTopology",
            "data class StudyFlowWorkspacePeer",
            "data class StudyFlowPeerHealth",
            "class StudyFlowPeerHealthTracker",
            "data class StudyFlowPeerRoute",
            "StudyFlowRouteKind.DIRECT",
            "StudyFlowRouteKind.LEARNED",
            "StudyFlowRouteKind.RELAY",
            "data class StudyFlowRouteTrace",
            "class StudyFlowMessageDeduplicator",
            "class StudyFlowTopologyRouter",
            "excludedNextHops",
            "visitedDeviceIds",
            "hopLimit",
            "broadcastRoutes",
        ],
    )
    for token in ["android.", "androidx.", "NsdManager", "WebView", "window.", "document."]:
        if token.lower() in topology.lower():
            fail(f"platform-specific API leaked into topology core: {token}")


def validate_offline_queue() -> None:
    queue = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueue.kt",
        [
            "interface StudyFlowOutgoingQueueStore",
            "class StudyFlowOutgoingOperationQueue",
            "class StudyFlowRetryPolicy",
            "fun acknowledgeCovered",
            "fun dueForPeer",
            "fun nextAttemptAt",
            "interface StudyFlowSyncPeerSession",
            "class StudyFlowReconnectionCoordinator",
            "StudyFlowReconnectStatus.COMPLETE",
            "StudyFlowReconnectStatus.INTERRUPTED",
            "StudyFlowSyncPullRequest",
            "localEngine.nextBatch",
            "peer.currentFrontier()",
            "peer.nextBatch",
            "outgoingQueue.acknowledgeCovered",
        ],
    )
    if "remove after" in queue.lower() or "maxattempt" in queue.lower():
        fail("SFLP07 must not silently drop canonical delivery after a retry-count ceiling")


def validate_tests() -> None:
    topology_tests = require_text(
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopologyTest.kt",
        [
            "topologyTracksMultiplePeersAndSelectsDeterministicHealthyRoute",
            "routeSelectionExcludesVisitedNextHopsAndDisconnectedRoutes",
            "routedEnvelopePreservesOriginalSourceAndRejectsLoopsAndDuplicateMessages",
            "routerDoesNotForwardBroadcastBackToVisitedPeers",
            "messageDedupeCacheIsBoundedAndEvictsOldestIds",
            "healthTrackerDegradesThenDisconnectsAndRecovers",
        ],
    )
    reconnect_tests = require_text(
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueueReconnectTest.kt",
        [
            "outgoingQueueIsPersistentIdempotentAndUsesBoundedExponentialBackoff",
            "acknowledgingPeerFrontierRemovesOnlyDeliveriesPeerActuallyHas",
            "reconnectCatchesUpStalePeerEvenWhenOutgoingQueueWasEmpty",
            "reconnectIsBidirectionalAndConvergesOfflineMutationsFromBothPeers",
            "responseLossKeepsQueueAndNextReconnectRecoversFromReceiverTruthWithoutDuplicates",
            "localOperationCanBeQueuedForSeveralPeersWithoutSelfDelivery",
            "retryPolicyNeverDropsPendingOperationAfterManyFailures",
        ],
    )
    if "ApplyThenLoseResponseSession" not in reconnect_tests:
        fail("interrupted-transfer test must simulate remote commit followed by response loss")
    _ = topology_tests


def validate_wiring_truth() -> None:
    require_text(
        "build.gradle.kts",
        [
            'tasks.register<Exec>("verifySflp06Sflp07TopologyOfflineQueue")',
            'dependsOn("verifySflp04Sflp05IdentityTransport", ":core:link:test")',
            'commandLine("python3", "scripts/verify_sflp06_07_topology_offline_queue.py")',
        ],
    )
    config = require_text(".devtool.toml", ["verifySflp24FinalReleaseSeal"])
    if config.count("verifySflp24FinalReleaseSeal") < 2:
        fail("terminal SFLP seal must be wired into normal and resumable validation lanes")
    require_text(
        "docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md",
        [
            "SFLP06 Multi-peer workspace topology — COMPLETE",
            "SFLP07 Offline queue/reconnection — COMPLETE",
            "SFLP08 Android integration",
        ],
    )
    require_text(
        "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
        [
            "SFLP06/SFLP07 multi-peer topology plus durable offline-delivery/reconnect core",
            "SFLP08-SFLP12 work",
        ],
    )


def validate_line_lengths() -> None:
    candidates = [
        "build.gradle.kts",
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopology.kt",
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueue.kt",
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopologyTest.kt",
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueueReconnectTest.kt",
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowTopologyOfflineGoldenTest.kt",
    ]
    errors: list[str] = []
    for relative in candidates:
        for number, line in enumerate((ROOT / relative).read_text(encoding="utf-8").splitlines(), 1):
            if len(line) > 120:
                errors.append(f"{relative}:{number}:{len(line)}")
    if errors:
        fail("line length >120: " + ", ".join(errors[:20]))


def main() -> None:
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            fail(f"missing required file: {relative}")
    run_predecessor()
    validate_json()
    validate_topology()
    validate_offline_queue()
    validate_tests()
    validate_wiring_truth()
    validate_line_lengths()
    print("SFLP06_07_TOPOLOGY_OFFLINE_QUEUE: PASS")


if __name__ == "__main__":
    main()
