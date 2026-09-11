#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    "SFLP08_09_ANDROID_DESKTOP_INTEGRATION.md",
    "docs/architecture/SFLP08_09_ANDROID_DESKTOP_INTEGRATION.md",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerPersistence.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDiscoveryRegistry.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLanDiscovery.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRuntimeIntegrationTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpcGoldenTest.kt",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkStateStore.kt",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt",
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowNsdDiscovery.kt",
    "app/src/test/java/com/mikeyphw/studyflow/StudyFlowLinkAndroidIntegrationContractTest.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkPersistence.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt",
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkIntegrationTest.kt",
    "protocol/sflp/v1/sflp-peer-rpc.schema.json",
    "protocol/sflp/v1/golden/peer-reconnect-request.json",
    "protocol/sflp/v1/golden/peer-sync-apply-request.json",
    "core/link/src/test/resources/sflp/v1/peer-reconnect-request.json",
    "core/link/src/test/resources/sflp/v1/peer-sync-apply-request.json",
]


def fail(message: str) -> None:
    print(f"SFLP08_09_ANDROID_DESKTOP_INTEGRATION: FAIL\n- {message}", file=sys.stderr)
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
        [sys.executable, "scripts/verify_sflp06_07_topology_offline_queue.py"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        fail("SFLP06/SFLP07 predecessor seal failed:\n" + result.stdout[-5000:])


def validate_json_and_goldens() -> None:
    for relative in REQUIRED:
        if relative.endswith(".json"):
            try:
                json.loads((ROOT / relative).read_text(encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {relative}: {exc}")
    for name in ["peer-reconnect-request.json", "peer-sync-apply-request.json"]:
        protocol = (ROOT / "protocol/sflp/v1/golden" / name).read_bytes()
        resource = (ROOT / "core/link/src/test/resources/sflp/v1" / name).read_bytes()
        if protocol != resource:
            fail(f"golden fixture drift between protocol and JVM resource: {name}")


def validate_shared_peer_runtime() -> None:
    persistence = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerPersistence.kt",
        [
            "StudyFlowTrustSnapshot",
            "StudyFlowOutgoingQueueSnapshot",
            "StudyFlowJournalSnapshot",
            "StudyFlowPeerEndpointRecord",
            "StudyFlowPeerEndpointStore",
            "StudyFlowPeerPersistenceCodec",
            "encodeIdentity",
            "encodeTrustRecords",
            "encodeOutgoingQueue",
            "encodeJournal",
            "encodePeerEndpoints",
        ],
    )
    rpc = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
        [
            "class StudyFlowPeerServer",
            "class StudyFlowRemoteSyncPeerSession",
            "class StudyFlowNetworkPairingClient",
            "class StudyFlowPeerRuntime",
            "StudyFlowReconnectChallengeRequest",
            "StudyFlowPeerRpcMessageNames.SyncApply",
            "trustManager.verifyReconnectProof",
            "StudyFlowSyncMessageNames.Frontier",
            "StudyFlowSyncMessageNames.Pull",
            "workspace.reconciliation.applyBatch",
            "rememberEndpoints",
            "ingestDiscovery",
            "reconcileWith",
            "appendLocalMutation",
            "StudyFlowRouteKind.DIRECT",
        ],
    )
    discovery = require_text(
        "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLanDiscovery.kt",
        [
            "StudyFlowLanBeacon",
            "StudyFlowLanBeaconDiscovery",
            "StudyFlowLanEndpointResolver",
            "StudyFlowDiscoverySource.LAN_BEACON",
            "DatagramSocket",
        ],
    )
    _ = persistence, discovery
    for forbidden in ["phoneDeviceName", "StudyFlowTvPhoneController", "ANDROID_PHONE"]:
        if forbidden in rpc:
            fail(f"generic peer runtime contains legacy phone/TV role token: {forbidden}")


def validate_android_binding() -> None:
    require_text("app/build.gradle.kts", ['implementation(project(":core:link"))'])
    require_text(
        "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
        [
            "val linkPeer: AndroidStudyFlowLinkPeer",
            "override val linkPeer = AndroidStudyFlowLinkPeer(application)",
            'action = "sflp-peer-start"',
            "linkPeer.start()",
            "local StudyFlow remains usable offline",
        ],
    )
    peer = require_text(
        "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt",
        [
            "StudyFlowDeviceIdentityManager",
            "StudyFlowLinkClientKind.ANDROID",
            "StudyFlowPeerRuntime",
            "AndroidStudyFlowNsdDiscovery",
            "StudyFlowLanBeaconDiscovery",
            "StudyFlowManualDiscovery",
            "pairFromQr",
            "rememberManualEndpoint",
            "reconcile",
            "revoke",
            "forget",
            "WorkspaceSync",
        ],
    )
    require_text(
        "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkStateStore.kt",
        [
            "StudyFlowDeviceIdentityStore",
            "StudyFlowTrustStore",
            "StudyFlowOperationJournalStore",
            "StudyFlowOutgoingQueueStore",
            "StudyFlowPeerEndpointStore",
            'PreferencesName = "studyflow-sflp-peer-v1"',
        ],
    )
    require_text(
        "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowNsdDiscovery.kt",
        [
            "StudyFlowMdnsContract.ServiceType",
            "NsdManager.PROTOCOL_DNS_SD",
            "registerService",
            "discoverServices",
            "resolveService",
        ],
    )
    if "StudyFlowTv" in peer or "phoneDeviceName" in peer:
        fail("Android SFLP binding must not encode the legacy phone/TV connectivity role")


def validate_desktop_binding() -> None:
    require_text("desktopApp/build.gradle.kts", ['implementation(project(":core:link"))'])
    require_text(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopBoundary.kt",
        [
            '":core:link"',
            'forbiddenProjectDependencyPrefixes',
            '":app"',
            '":feature:"',
            '":core:database"',
        ],
    )
    require_text(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkPersistence.kt",
        [
            'root.resolve("identity.json")',
            'root.resolve("trust.json")',
            'root.resolve("endpoints.json")',
            'root.resolve("queue")',
            'root.resolve("journal")',
            "StandardCopyOption.ATOMIC_MOVE",
            '".studyflow"',
            '"link"',
            '"v1"',
        ],
    )
    require_text(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt",
        [
            "StudyFlowLinkClientKind.DESKTOP",
            "StudyFlowPeerRuntime",
            "StudyFlowLanBeaconDiscovery",
            "StudyFlowManualDiscovery",
            "createPairingOffer",
            "pairFromQr",
            "rememberManualEndpoint",
            "syncPeer",
            "syncAll",
            "backgroundReconnect",
            "revoke",
            "forget",
            "pendingOutgoingOperations",
        ],
    )
    require_text(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppBootstrap.kt",
        [
            "fun createAppState(",
            "linkService: DesktopStudyFlowLinkService? = null",
            "fun createProductionAppState(",
            "linkService.start()",
        ],
    )
    require_text(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt",
        ["StudyFlowDesktopBootstrap.createProductionAppState()"],
    )
    settings = require_text(
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
        [
            'DesktopSectionCard(title = desktopText("StudyFlow Link"))',
            'Text(desktopText("Create pairing offer"))',
            'Text(desktopText("Sync trusted peers"))',
            'Text(desktopText("Pair from QR payload"))',
            'Text(desktopText("Remember endpoint"))',
            'DesktopSectionCard(title = desktopText("Trusted StudyFlow peers"))',
            'Text(desktopText("Revoke"))',
            'Text(desktopText("Forget"))',
            'DesktopSectionCard(title = desktopText("Discovered StudyFlow peers"))',
        ],
    )
    _ = settings


def validate_tests() -> None:
    core = require_text(
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRuntimeIntegrationTest.kt",
        [
            "twoGenericPeersPairAuthenticateAndConvergeOverRealTcp",
            "StudyFlowLinkClientKind.ANDROID",
            "StudyFlowLinkClientKind.DESKTOP",
            'startServer("127.0.0.1", 0)',
            "pairFromQr",
            "op-android-1",
            "op-desktop-1",
            "StudyFlowReconnectStatus.COMPLETE",
            "revokedPeerCannotOpenAuthenticatedSyncSession",
            "persistenceCodecRoundTripsEveryPeerStateFamily",
            "lanBeaconCodecCarriesGenericPeerIdentityAndTcpPort",
        ],
    )
    require_text(
        "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkIntegrationTest.kt",
        [
            "ordinaryDesktopBootstrapStaysSideEffectFreeWhileProductionFactoryOwnsLinkStartup",
            "desktopLinkPersistenceSurvivesRecreationForIdentityTrustEndpointsJournalAndQueue",
            "desktopBoundaryAllowsLinkButStillRejectsAndroidFeatureAndPersistenceModules",
        ],
    )
    require_text(
        "app/src/test/java/com/mikeyphw/studyflow/StudyFlowLinkAndroidIntegrationContractTest.kt",
        [
            "androidApplicationOwnsGenericRecoverableSflpPeer",
            "androidPeerUsesPersistentGenericIdentityTrustSyncAndDiscoveryInsteadOfPhoneTvRoles",
        ],
    )
    require_text(
        "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpcGoldenTest.kt",
        [
            "reconnectRequestGoldenUsesGenericDeviceIdentityWithoutClientRoleAssumptions",
            "syncApplyGoldenCarriesSflp03BatchAcrossTheGenericPeerRpcBoundary",
        ],
    )
    if "realTcp" not in core and "RealTcp" not in core:
        fail("Android/Desktop integration test must explicitly prove real TCP connectivity")


def validate_wiring_truth() -> None:
    require_text(
        "build.gradle.kts",
        [
            'tasks.register<Exec>("verifySflp08Sflp09AndroidDesktopIntegration")',
            'dependsOn(',
            '"verifySflp06Sflp07TopologyOfflineQueue"',
            '":core:link:test"',
            '":desktopApp:test"',
            '":app:testReleaseUnitTest"',
            'commandLine("python3", "scripts/verify_sflp08_09_android_desktop_integration.py")',
        ],
    )
    config = require_text(".devtool.toml", ["verifySflp08Sflp09AndroidDesktopIntegration"])
    if config.count('"verifySflp08Sflp09AndroidDesktopIntegration"') < 2:
        fail("SFLP08/SFLP09 verifier must remain visible in Android and Desktop validation lanes")
    require_text(
        "docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md",
        [
            "SFLP08 Android integration — COMPLETE",
            "SFLP09 Desktop integration — COMPLETE",
            "SFLP10 Web integration",
        ],
    )
    matrix = require_text(
        "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
        [
            "Android ↔ Desktop: first-class SFLP",
            "SFLP08 and SFLP09 are now complete",
            "does **not yet** provide a universal cross-client synchronization mesh",
        ],
    )
    if "Desktop has no first-class peer link yet" in matrix:
        fail("capability matrix still claims Desktop has no peer link")
    require_text(
        "README.md",
        [
            "SFLP08/SFLP09 now give Android and Desktop",
            "longer the required hub for Android↔Desktop connectivity",
        ],
    )
    require_text(
        "docs/product/SCOPE_BOUNDARIES.md",
        [
            "SFLP08/SFLP09 provide first-class direct Android↔Desktop",
            "five-client universal mesh is still staged",
        ],
    )


def validate_line_lengths() -> None:
    candidates = [
        "build.gradle.kts",
        *[path for path in REQUIRED if path.endswith(".kt")],
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppBootstrap.kt",
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt",
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt",
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopBoundary.kt",
        "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
        "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    ]
    errors: list[str] = []
    for relative in candidates:
        path = ROOT / relative
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if len(line) > 120:
                errors.append(f"{relative}:{number}:{len(line)}")
    if errors:
        fail("line length >120: " + ", ".join(errors[:30]))


def main() -> None:
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            fail(f"missing required file: {relative}")
    run_predecessor()
    validate_json_and_goldens()
    validate_shared_peer_runtime()
    validate_android_binding()
    validate_desktop_binding()
    validate_tests()
    validate_wiring_truth()
    validate_line_lengths()
    print("SFLP08_09_ANDROID_DESKTOP_INTEGRATION: PASS")


if __name__ == "__main__":
    main()
