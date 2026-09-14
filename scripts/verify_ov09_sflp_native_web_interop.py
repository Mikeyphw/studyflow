#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text()

def need(path, *tokens):
    body = text(path)
    missing = [token for token in tokens if token not in body]
    if missing:
        raise SystemExit(f"OV09 verification failed: {path} missing {missing}")

need("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebSocketTransport.kt",
     "class StudyFlowWebSocketTransport", "Sec-WebSocket-Accept", "studyflow.sflp.v1", "StudyFlowTransportScheme.WEBSOCKET")
ws_transport = text("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebSocketTransport.kt")
if "java.util.Base64" in ws_transport:
    raise SystemExit("OV09 verification failed: WebSocket transport regresses Android API-23 Base64 compatibility")
need("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebSocketTransport.kt", "encodeBase64", "Base64Alphabet")
need("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt",
     "StudyFlowRoutedRpcContracts.supports", "handleRoutedRpc", "startWebSocketServer", "sendRoutedRpc")
need("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowRoutedRpc.kt",
     "StudyFlowActivityHandoffMessageNames.Offer", "StudyFlowLiveSessionMessageNames.Cursor",
     "StudyFlowPresentationProjectionMessageNames.Control", "StudyFlowControlMessageNames.InputEvent",
     "StudyFlowNotificationMediaMessageNames.MaterialChunk", "StudyFlowShareImportWireNames.ConflictResolve",
     "StudyFlowAccountBackupMessageNames.BackupRestorePlan")
for path in [
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt",
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvSflpPeer.kt",
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt",
]:
    need(path, "startWebSocketServer", "StudyFlowTransportScheme.WEBSOCKET")
need("web/src/sflp_link.ts",
     'studyflow://pair?data=', 'studyflow.sflp.web.peer.v1', 'studyflow.sflp.web_tv.peer.v1',
     'link.pair', 'link.reconnect_challenge', 'link.reconnect_proof', 'sync.frontier', 'sync.apply',
     'logicalTimestamp', 'occurredAtEpochMillis', 'sendRoutedRpc', 'studyflow.sflp.v1')
need("web/tests/ov09_interop_runner.mjs", "OV09_WEB_NATIVE_INTEROP: PASS", '"b".repeat(64)')
need("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOv09NativeWebInteropTest.kt",
     "websocketBridgeUsesCanonicalPairingSyncAndRoutedRpc", "routedRegistryCoversEveryPromisedSflp14Through23Family")
need("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowWebSocketBrowserInteropTest.kt",
     "browserHandshakeAndFragmentedMaskedMessageAreAccepted", "browserFramesMustBeMasked", "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
validator = text("scripts/validate_ov09_sflp_native_web_interop.sh")
if "gradle --stop" in validator or "./gradlew" in validator or " gradle " in validator:
    raise SystemExit("OV09 verification failed: source validator must not manage/invoke Gradle")
print("OV09_SOURCE_CONTRACT: PASS")
