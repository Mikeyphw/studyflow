package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowShareImportConflictReviewTest {
    private val workspace = StudyFlowWorkspaceId("workspace-main")

    @Test
    fun shareImportOfferIsTargetGatedDuplicateSafeAndDomainConvertible() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val offer = StudyFlowShareImportOffer(
            importId = StudyFlowShareImportId("import-note-1"),
            workspaceId = workspace,
            sourceDevice = phone,
            targetDeviceId = desktop.deviceId,
            intent = StudyFlowShareImportIntent.SHARE_IMPORT,
            payloadKind = StudyFlowShareImportPayloadKind.MARKDOWN,
            title = "Shared markdown note",
            mimeType = "text/markdown",
            previewText = "# Biology",
            contentSha256 = StudyFlowShareImportDefaults.sha256Hex("# Biology"),
            contentBytesBase64 = StudyFlowShareImportDefaults.base64Utf8("# Biology"),
            requiredCapabilities = StudyFlowShareImportDefaults.textCapabilitiesFor(
                StudyFlowShareImportIntent.SHARE_IMPORT,
            ),
            createdAtEpochMillis = 1_000L,
        )
        val reducer = StudyFlowShareImportReducer(desktop, StudyFlowPeerClock { 2_000L })
        val delivery = StudyFlowShareImportDelivery(
            messageId = StudyFlowMessageId("share-message-1"),
            sourceDeviceId = phone.deviceId,
            targetDeviceId = desktop.deviceId,
            sequence = 1L,
            offer = offer,
            sentAtEpochMillis = 1_100L,
        )
        assertEquals(StudyFlowShareImportStatus.ACCEPTED, reducer.apply(delivery).status)
        assertEquals(
            StudyFlowShareImportStatus.DUPLICATE,
            reducer.apply(delivery.copy(messageId = StudyFlowMessageId("share-message-2"))).status,
        )
        val op = offer.toDomainOperation(StudyFlowEntityId("material-1"), StudyFlowEntityRevision(1), 1, 1, 1_200L)
        assertEquals(StudyFlowDomainOperationTypes.ShareImported, op.operationType)
    }

    @Test
    fun tvClassPeersCanPreviewButCannotAcceptWriteImports() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val tv = descriptor("tv", StudyFlowLinkClientKind.WEB_TV, StudyFlowClientProfiles.WebTv)
        val preview = textOffer(phone, tv, StudyFlowShareImportIntent.CLIPBOARD_PREVIEW)
        val write = textOffer(phone, tv, StudyFlowShareImportIntent.SHARE_IMPORT)
        assertTrue(preview.accepts(tv, nowEpochMillis = 2_000L))
        assertFalse(write.accepts(tv, nowEpochMillis = 2_000L))
    }

    @Test
    fun conflictReviewOpensResolvesOnceAndProducesCanonicalOperation() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val web = descriptor("web", StudyFlowLinkClientKind.WEB, StudyFlowClientProfiles.Web)
        val candidate = StudyFlowConflictCandidate(
            entityId = StudyFlowEntityId("note-1"),
            localRevision = StudyFlowEntityRevision(2),
            remoteRevision = StudyFlowEntityRevision(3),
            fieldKinds = listOf(StudyFlowConflictFieldKind.CONTENT, StudyFlowConflictFieldKind.TAGS),
            localSummary = "Local note keeps the older tag.",
            remoteSummary = "Remote note has a new tag and newer body.",
        )
        val review = StudyFlowConflictReview(
            reviewId = StudyFlowConflictReviewId("conflict-note-1"),
            workspaceId = workspace,
            sourceDevice = desktop,
            targetDeviceId = web.deviceId,
            candidate = candidate,
            createdAtEpochMillis = 1_000L,
        )
        val reducer = StudyFlowConflictReviewReducer(web, StudyFlowPeerClock { 2_000L })
        assertTrue(reducer.open(review))
        val resolution = StudyFlowConflictResolution(
            resolutionId = StudyFlowConflictResolutionId("resolution-note-1"),
            reviewId = review.reviewId,
            workspaceId = workspace,
            resolverDeviceId = web.deviceId,
            targetDeviceId = web.deviceId,
            sequence = 1L,
            decision = StudyFlowConflictResolutionKind.MERGE,
            mergedSummary = "Merged note keeps both the body and tag.",
            resolvedAtEpochMillis = 2_000L,
        )
        assertEquals(StudyFlowConflictReviewStatus.RESOLVED, reducer.resolve(resolution).status)
        assertEquals(
            StudyFlowConflictReviewStatus.REJECTED,
            reducer.resolve(
                resolution.copy(resolutionId = StudyFlowConflictResolutionId("resolution-note-2")),
            ).status,
        )
        assertEquals(
            StudyFlowDomainOperationTypes.ConflictMerged,
            resolution.toDomainOperation(candidate, StudyFlowEntityRevision(4), 9L).operationType,
        )
    }

    private fun textOffer(
        source: StudyFlowLinkPeerDescriptor,
        target: StudyFlowLinkPeerDescriptor,
        intent: StudyFlowShareImportIntent,
    ) = StudyFlowShareImportOffer(
        importId = StudyFlowShareImportId("import-${intent.name.lowercase().replace('_', '-')}"),
        workspaceId = workspace,
        sourceDevice = source,
        targetDeviceId = target.deviceId,
        intent = intent,
        payloadKind = StudyFlowShareImportPayloadKind.PLAIN_TEXT,
        title = "Text import",
        mimeType = "text/plain",
        previewText = "Remember this",
        contentSha256 = StudyFlowShareImportDefaults.sha256Hex("Remember this"),
        contentBytesBase64 = StudyFlowShareImportDefaults.base64Utf8("Remember this"),
        requiredCapabilities = StudyFlowShareImportDefaults.textCapabilitiesFor(intent),
        createdAtEpochMillis = 1_000L,
    )

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
