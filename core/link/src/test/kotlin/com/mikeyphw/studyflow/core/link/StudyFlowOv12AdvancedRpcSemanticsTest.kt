package com.mikeyphw.studyflow.core.link

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyFlowOv12AdvancedRpcSemanticsTest {
    private val workspace = StudyFlowWorkspaceId("workspace-ov12")
    private val activity = StudyFlowActivityId("activity-ov12")
    private val phone = descriptor("phone-ov12", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
    private val desktop = descriptor("desktop-ov12", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
    private val web = descriptor("web-ov12", StudyFlowLinkClientKind.WEB, StudyFlowClientProfiles.Web)
    private val tv = descriptor("tv-ov12", StudyFlowLinkClientKind.WEB_TV, StudyFlowClientProfiles.WebTv)
    private val clock = StudyFlowPeerClock { 10_000L }

    @Test
    fun liveSessionEventsAreFencedToSessionWorkspaceActivityAndExecutor() {
        val reducer = StudyFlowLiveSessionReducer(liveSnapshot())
        val wrongWorkspace = liveEvent("live-wrong-workspace", source = phone.deviceId, sequence = 2)
            .copy(workspaceId = StudyFlowWorkspaceId("workspace-other"), fencingToken = "fence-ov12")
        assertEquals(
            "workspace_mismatch",
            (reducer.applyVerified(wrongWorkspace) as StudyFlowLiveSessionApplyResult.Rejected).reason,
        )

        val wrongActivity = liveEvent("live-wrong-activity", source = phone.deviceId, sequence = 2)
            .copy(cursor = StudyFlowLiveSessionCursor(StudyFlowActivityId("activity-other"), "q1"), fencingToken = "fence-ov12")
        assertEquals(
            "activity_mismatch",
            (reducer.applyVerified(wrongActivity) as StudyFlowLiveSessionApplyResult.Rejected).reason,
        )

        val wrongFence = liveEvent("live-wrong-fence", source = phone.deviceId, sequence = 2).copy(fencingToken = "bad-fence")
        assertEquals(
            "fencing_token_mismatch",
            (reducer.applyVerified(wrongFence) as StudyFlowLiveSessionApplyResult.Rejected).reason,
        )

        val wrongExecutor = liveEvent("live-wrong-executor", source = desktop.deviceId, sequence = 2)
            .copy(fencingToken = "fence-ov12")
        assertEquals(
            "executor_mismatch",
            (reducer.applyVerified(wrongExecutor) as StudyFlowLiveSessionApplyResult.Rejected).reason,
        )

        val valid = liveEvent("live-valid", source = phone.deviceId, sequence = 2).copy(fencingToken = "fence-ov12")
        assertTrue(reducer.applyVerified(valid) is StudyFlowLiveSessionApplyResult.Applied)
    }

    @Test
    fun projectionFramesAreBoundToAcceptedSourceWorkspaceAndContentHash() {
        val receiver = StudyFlowPresentationProjectionReceiver(tv, clock)
        val offer = projectionOffer()
        assertTrue(receiver.accept(offer) is StudyFlowPresentationApplyResult.Accepted)

        assertEquals(
            "wrong_source",
            (receiver.applyFrame(projectionFrame("frame-wrong-source", 1).copy(sourceDeviceId = phone.deviceId))
                as StudyFlowPresentationApplyResult.Rejected).reason,
        )
        assertEquals(
            "content_hash_mismatch",
            (receiver.applyFrame(projectionFrame("frame-wrong-hash", 1).copy(contentHash = "b".repeat(64)))
                as StudyFlowPresentationApplyResult.Rejected).reason,
        )
        assertTrue(receiver.applyFrame(projectionFrame("frame-ok", 1)) is StudyFlowPresentationApplyResult.Accepted)
    }

    @Test
    fun materialStreamingCompletesOnlyAfterOrderedVerifiedBytesAndAggregateHash() {
        val reducer = StudyFlowMaterialStreamReducer(tv, clock)
        val offer = materialOffer("ABCDEF")
        assertTrue(reducer.accept(offer))

        val outOfOrderFinal = materialChunk(offer, "chunk-final-early", 1, 3, "DEF", final = true)
        assertEquals(
            "out_of_order_chunk",
            reducer.apply(outOfOrderFinal).reason,
        )

        val first = materialChunk(offer, "chunk-first", 0, 0, "ABC")
        assertEquals(StudyFlowMaterialChunkStatus.ACCEPTED, reducer.apply(first).status)

        val badHash = materialChunk(offer, "chunk-bad-hash", 1, 3, "DEF", final = true)
            .copy(chunkSha256 = StudyFlowMaterialStreamReducer.sha256Hex("BAD".toByteArray(Charsets.UTF_8)))
        assertEquals(
            "chunk_hash_mismatch",
            reducer.apply(badHash).reason,
        )

        val finalChunk = materialChunk(offer, "chunk-final", 1, 3, "DEF", final = true)
        assertEquals(StudyFlowMaterialChunkStatus.COMPLETE, reducer.apply(finalChunk).status)
    }

    @Test
    fun shareImportWriteDeliveryRequiresCompleteContentForTheBoundTargetAndSource() {
        val reducer = StudyFlowShareImportReducer(web, clock)
        val missing = shareOffer("share-missing", "# Biology", includeBytes = false)
        val missingAck = reducer.apply(shareDelivery(missing, sequence = 1))
        assertEquals(StudyFlowShareImportStatus.REJECTED, missingAck.status)
        assertEquals("missing_content_bytes", missingAck.reason)

        val wrongSource = shareDelivery(shareOffer("share-wrong-source", "# Biology"), sequence = 2)
            .copy(sourceDeviceId = phone.deviceId)
        assertEquals("wrong_source", reducer.apply(wrongSource).reason)

        val innerMismatch = shareDelivery(shareOffer("share-inner-mismatch", "# Biology"), sequence = 3)
            .copy(offer = shareOffer("share-inner-mismatch", "# Biology").copy(targetDeviceId = desktop.deviceId))
        assertEquals("inner_target_mismatch", reducer.apply(innerMismatch).reason)

        val valid = shareOffer("share-valid", "# Biology")
        assertEquals(StudyFlowShareImportStatus.ACCEPTED, reducer.apply(shareDelivery(valid, sequence = 4)).status)
    }

    @Test
    fun accountAndBackupHandoffsRejectInnerTargetSubstitution() {
        val accountReducer = StudyFlowAccountHandoffReducer(web, clock)
        val handoff = accountOffer().copy(targetDeviceId = desktop.deviceId)
        val handoffDelivery = StudyFlowAccountHandoffDelivery(
            messageId = StudyFlowMessageId("account-message-inner-mismatch"),
            sourceDeviceId = phone.deviceId,
            targetDeviceId = web.deviceId,
            sequence = 1,
            offer = handoff,
            sentAtEpochMillis = 9_000L,
        )
        assertEquals("inner_target_mismatch", accountReducer.apply(handoffDelivery).reason)

        val backupReducer = StudyFlowBackupRestoreReducer(web, clock)
        val plan = StudyFlowBackupRestorePlan(
            restoreId = StudyFlowBackupRestoreId("restore-inner-mismatch"),
            bundle = backupBundle().copy(targetDeviceId = desktop.deviceId),
            targetDeviceId = web.deviceId,
            mode = StudyFlowBackupRestoreMode.RESTORE,
            sequence = 1,
            createdAtEpochMillis = 9_000L,
        )
        assertEquals("inner_target_mismatch", backupReducer.apply(plan).reason)
    }

    private fun liveSnapshot() = StudyFlowLiveSessionSnapshot(
        sessionId = StudyFlowLiveSessionId("session-ov12"),
        workspaceId = workspace,
        activityId = activity,
        state = StudyFlowLiveSessionState.RUNNING,
        activeExecutorDeviceId = phone.deviceId,
        fencingToken = "fence-ov12",
        sessionEpoch = 1,
        lastSequence = 1,
        cursor = StudyFlowLiveSessionCursor(activity, "q1"),
        participants = listOf(
            StudyFlowLiveSessionParticipant(phone, StudyFlowLiveSessionRole.EXECUTOR, joinedAtEpochMillis = 1_000L),
            StudyFlowLiveSessionParticipant(desktop, StudyFlowLiveSessionRole.CONTROLLER, joinedAtEpochMillis = 1_100L),
        ),
        updatedAtEpochMillis = 1_200L,
    )

    private fun liveEvent(id: String, source: StudyFlowDeviceId, sequence: Long) = StudyFlowLiveSessionEvent(
        eventId = StudyFlowMessageId(id),
        sessionId = StudyFlowLiveSessionId("session-ov12"),
        workspaceId = workspace,
        sourceDeviceId = source,
        sessionEpoch = 1,
        sequence = sequence,
        kind = StudyFlowLiveSessionEventKind.CURSOR,
        cursor = StudyFlowLiveSessionCursor(activity, "q2"),
        occurredAtEpochMillis = 9_000L + sequence,
    )

    private fun projectionOffer() = StudyFlowPresentationProjectionOffer(
        projectionId = StudyFlowProjectionId("projection-ov12"),
        workspaceId = workspace,
        sourceDevice = desktop,
        targetDeviceId = tv.deviceId,
        contentKind = StudyFlowPresentationContentKind.DOCUMENT,
        entityId = StudyFlowEntityId("doc-ov12"),
        title = "OV12 doc",
        contentHash = "a".repeat(64),
        requiredCapabilities = StudyFlowPresentationProjectionPolicy.defaultSinkCapabilities(StudyFlowPresentationContentKind.DOCUMENT),
        createdAtEpochMillis = 9_000L,
        expiresAtEpochMillis = 20_000L,
    )

    private fun projectionFrame(id: String, sequence: Long) = StudyFlowPresentationFrame(
        projectionId = StudyFlowProjectionId("projection-ov12"),
        frameId = StudyFlowMessageId(id),
        workspaceId = workspace,
        sourceDeviceId = desktop.deviceId,
        sequence = sequence,
        contentRevision = StudyFlowEntityRevision(sequence),
        contentHash = "a".repeat(64),
        cursor = StudyFlowActivityCursor("slide-$sequence"),
    )

    private fun materialOffer(content: String) = StudyFlowMaterialStreamOffer(
        streamId = StudyFlowMaterialStreamId("stream-ov12"),
        workspaceId = workspace,
        materialId = StudyFlowMaterialId("material-ov12"),
        sourceDevice = desktop,
        targetDeviceId = tv.deviceId,
        materialKind = StudyFlowMaterialKind.PDF,
        title = "OV12 PDF",
        mimeType = "application/pdf",
        totalBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
        chunkSize = 3,
        contentSha256 = StudyFlowMaterialStreamReducer.sha256Hex(content.toByteArray(Charsets.UTF_8)),
        requiredCapabilities = listOf(StudyFlowLinkCapabilities.LibraryRead, StudyFlowLinkCapabilities.DocumentPresent),
        createdAtEpochMillis = 9_000L,
        expiresAtEpochMillis = 20_000L,
    )

    private fun materialChunk(
        offer: StudyFlowMaterialStreamOffer,
        id: String,
        index: Int,
        offset: Long,
        content: String,
        final: Boolean = false,
    ) = StudyFlowMaterialChunk(
        messageId = StudyFlowMessageId(id),
        streamId = offer.streamId,
        sourceDeviceId = desktop.deviceId,
        targetDeviceId = tv.deviceId,
        chunkIndex = index,
        byteOffset = offset,
        bytesBase64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)),
        chunkSha256 = StudyFlowMaterialStreamReducer.sha256Hex(content.toByteArray(Charsets.UTF_8)),
        finalChunk = final,
        sentAtEpochMillis = 9_500L + index,
    )

    private fun shareOffer(id: String, content: String, includeBytes: Boolean = true) = StudyFlowShareImportOffer(
        importId = StudyFlowShareImportId(id),
        workspaceId = workspace,
        sourceDevice = desktop,
        targetDeviceId = web.deviceId,
        intent = StudyFlowShareImportIntent.SHARE_IMPORT,
        payloadKind = StudyFlowShareImportPayloadKind.MARKDOWN,
        title = "Shared OV12 note",
        mimeType = "text/markdown",
        previewText = content,
        contentSha256 = StudyFlowShareImportDefaults.sha256Hex(content),
        contentBytesBase64 = if (includeBytes) StudyFlowShareImportDefaults.base64Utf8(content) else null,
        requiredCapabilities = StudyFlowShareImportDefaults.textCapabilitiesFor(StudyFlowShareImportIntent.SHARE_IMPORT),
        createdAtEpochMillis = 9_000L,
        expiresAtEpochMillis = 20_000L,
    )

    private fun shareDelivery(offer: StudyFlowShareImportOffer, sequence: Long) = StudyFlowShareImportDelivery(
        messageId = StudyFlowMessageId("share-message-${offer.importId.value}"),
        sourceDeviceId = desktop.deviceId,
        targetDeviceId = web.deviceId,
        sequence = sequence,
        offer = offer,
        sentAtEpochMillis = 9_100L + sequence,
    )

    private fun accountOffer() = StudyFlowAccountHandoffOffer(
        handoffId = StudyFlowAccountHandoffId("handoff-ov12"),
        workspaceId = workspace,
        sourceDevice = phone,
        targetDeviceId = web.deviceId,
        purpose = StudyFlowAccountHandoffPurpose.DEVICE_REPLACEMENT,
        manifestSha256 = StudyFlowAccountBackupDefaults.sha256Hex("manifest"),
        requiredCapabilities = StudyFlowAccountBackupDefaults.handoffCapabilitiesFor(StudyFlowAccountHandoffPurpose.DEVICE_REPLACEMENT),
        createdAtEpochMillis = 9_000L,
        expiresAtEpochMillis = 20_000L,
    )

    private fun backupBundle() = StudyFlowBackupBundleManifest(
        bundleId = StudyFlowBackupBundleId("bundle-ov12"),
        workspaceId = workspace,
        sourceDevice = phone,
        targetDeviceId = web.deviceId,
        kind = StudyFlowBackupBundleKind.FULL_WORKSPACE,
        totalBytes = 8,
        itemCount = 1,
        contentSha256 = StudyFlowAccountBackupDefaults.sha256Hex("backup"),
        encryptionMode = "local-passphrase-v1",
        createdAtEpochMillis = 9_000L,
        expiresAtEpochMillis = 20_000L,
    )

    private fun descriptor(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
    ) = StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
