#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]


def require(rel: str, *tokens: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"OV12 FAIL: missing {rel}")
    text = path.read_text(encoding="utf-8")
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"OV12 FAIL {rel}: missing {missing}")
    return text

# S4-16 + DS2-20 — live session continuity is fenced to the active session,
# workspace, activity and executor identity instead of accepting any monotonic event.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveSessionProjection.kt",
    "StudyFlowLiveSessionFencingPolicy",
    "applyVerified",
    "fencing_token_mismatch",
    "activity_mismatch",
    "executor_mismatch",
    "session_mismatch",
)
require(
    "web/src/sflp_link.ts",
    "fencingToken?:string",
    "session_mismatch",
    "workspace_mismatch",
    "activity_mismatch",
    "fencing_token_mismatch",
    "executor_mismatch",
)

# DS2-21 — projection frames are bound to the accepted offer source/workspace/hash.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveSessionProjection.kt",
    "acceptedOffers",
    "wrong_source",
    "workspace_mismatch",
    "content_hash_mismatch",
)
require(
    "web/src/sflp_link.ts",
    "private accepted=new Map<string,SflpPresentationProjectionOffer>()",
    "frame.sourceDeviceId!==offer.sourceDevice.deviceId",
    "frame.contentHash!==offer.contentHash",
)

# S4-11 + DS2-24 — material streaming validates complete bytes, chunk hashes,
# ordered offsets and aggregate content hash before COMPLETE.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSync.kt",
    "bytesBase64",
    "chunkSha256",
    "out_of_order_chunk",
    "chunk_hash_mismatch",
    "short_non_final_chunk",
    "incomplete_final_chunk",
    "content_hash_mismatch",
    "completedStreams",
)
require(
    "web/src/sflp_link.ts",
    "sha256HexBytes",
    "missing_chunk_bytes",
    "chunk_hash_mismatch",
    "out_of_order_chunk",
    "incomplete_final_chunk",
    "completedMaterialStreams",
)

# S4-20 + DS2-25 — share/import writes carry and verify full payload bytes.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowShareImportConflictReview.kt",
    "contentBytesBase64",
    "StudyFlowShareImportPayloadPolicy",
    "missing_content_bytes",
    "content_hash_mismatch",
    "inner_target_mismatch",
    "wrong_source",
)
require(
    "web/src/sflp_link.ts",
    "contentBytesBase64?: string",
    "missing_content_bytes",
    "inner_target_mismatch",
    "wrong_source",
)

# DS2-22 + DS2-23 — Web-TV remote input delegates to the same lease/sequence/token
# reducer contract as the core runtime rather than target-device-only admission.
require(
    "web/src/sflp_link.ts",
    "private readonly controlRuntime:StudyFlowControlInputRuntime",
    "rememberControlLease(lease:StudyFlowControlLeaseEnvelope)",
    "command.leaseToken===undefined",
    "applyControlCommand",
)

# DS2-26 — account and backup handoff/restore cannot smuggle another inner target.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAccountBackupValidation.kt",
    "inner_target_mismatch",
    "targetDeviceId: StudyFlowDeviceId? = null",
    "plan.bundle.targetDeviceId != null && plan.bundle.targetDeviceId != plan.targetDeviceId",
)
require(
    "web/src/sflp_link.ts",
    "plan.bundle.targetDeviceId && plan.bundle.targetDeviceId !== plan.targetDeviceId",
    "delivery.offer.targetDeviceId && delivery.offer.targetDeviceId !== delivery.targetDeviceId",
)

# Server boundary: advanced routed RPCs advertise explicit semantic families and
# authority-bearing requests require sender capabilities before hitting handlers.
require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowRoutedRpc.kt",
    "StudyFlowAdvancedRpcSemanticHandlers",
    "StudyFlowLiveSessionMessageNames.Cursor",
    "StudyFlowPresentationProjectionMessageNames.Frame",
    "StudyFlowNotificationMediaMessageNames.MaterialChunk",
    "StudyFlowShareImportWireNames.ShareImportOffer",
    "StudyFlowAccountBackupMessageNames.BackupRestorePlan",
    "StudyFlowLinkCapabilities.DeviceControl",
    "StudyFlowLinkCapabilities.SessionExecute",
)

require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOv12AdvancedRpcSemanticsTest.kt",
    "liveSessionEventsAreFencedToSessionWorkspaceActivityAndExecutor",
    "projectionFramesAreBoundToAcceptedSourceWorkspaceAndContentHash",
    "materialStreamingCompletesOnlyAfterOrderedVerifiedBytesAndAggregateHash",
    "shareImportWriteDeliveryRequiresCompleteContentForTheBoundTargetAndSource",
    "accountAndBackupHandoffsRejectInnerTargetSubstitution",
)
require(
    "web/tests/ov12_advanced_rpc_semantics_runner.mjs",
    "OV12_ADVANCED_RPC_SEMANTICS: PASS",
    "legacy target-only remote input must reject",
    "early final chunk must reject",
)
require(
    "build.gradle.kts",
    "verifyOv12SflpAdvancedRpcSemantics",
    ":core:link:test",
    ":app-tv:compileDebugKotlin",
    "scripts/validate_ov12_sflp_advanced_rpc_semantics.sh",
)
require(".devtool.toml", '"verifyOv12SflpAdvancedRpcSemantics"')

print("OV12 advanced RPC semantics source evidence: PASS (10/10 owned findings)")
