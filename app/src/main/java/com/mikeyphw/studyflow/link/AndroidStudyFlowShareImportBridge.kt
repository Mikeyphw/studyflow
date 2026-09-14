package com.mikeyphw.studyflow.link

import com.mikeyphw.studyflow.core.link.StudyFlowConflictCandidate
import com.mikeyphw.studyflow.core.link.StudyFlowConflictResolution
import com.mikeyphw.studyflow.core.link.StudyFlowConflictResolutionId
import com.mikeyphw.studyflow.core.link.StudyFlowConflictResolutionKind
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReview
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewId
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewReducer
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewStatus
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportAck
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportDefaults
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportId
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportIntent
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportOffer
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportPayloadKind
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportReducer
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class AndroidStudyFlowShareImportBridge(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val imports = StudyFlowShareImportReducer(localDevice, clock)
    private val conflicts = StudyFlowConflictReviewReducer(localDevice, clock)

    fun clipboardTextOffer(
        importId: StudyFlowShareImportId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        title: String,
        text: String,
        intent: StudyFlowShareImportIntent = StudyFlowShareImportIntent.CLIPBOARD_APPLY,
    ): StudyFlowShareImportOffer = StudyFlowShareImportOffer(
        importId = importId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        targetDeviceId = target.deviceId,
        intent = intent,
        payloadKind = StudyFlowShareImportPayloadKind.PLAIN_TEXT,
        title = title,
        mimeType = "text/plain",
        previewText = text.take(16_384),
        contentSha256 = StudyFlowShareImportDefaults.sha256Hex(text),
        contentBytesBase64 = StudyFlowShareImportDefaults.base64Utf8(text),
        requiredCapabilities = StudyFlowShareImportDefaults.textCapabilitiesFor(intent),
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun deliverImport(
        messageId: StudyFlowMessageId,
        target: StudyFlowLinkPeerDescriptor,
        sequence: Long,
        offer: StudyFlowShareImportOffer,
    ): StudyFlowShareImportDelivery = StudyFlowShareImportDelivery(
        messageId = messageId,
        sourceDeviceId = localDevice.deviceId,
        targetDeviceId = target.deviceId,
        sequence = sequence,
        offer = offer,
        sentAtEpochMillis = clock.nowEpochMillis(),
    )

    fun receiveImport(delivery: StudyFlowShareImportDelivery): StudyFlowShareImportAck = imports.apply(delivery)

    fun openConflictReview(review: StudyFlowConflictReview): Boolean = conflicts.open(review)

    fun conflictReview(
        reviewId: StudyFlowConflictReviewId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        candidate: StudyFlowConflictCandidate,
    ): StudyFlowConflictReview = StudyFlowConflictReview(
        reviewId = reviewId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        targetDeviceId = target.deviceId,
        candidate = candidate,
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun resolveConflict(
        resolutionId: StudyFlowConflictResolutionId,
        reviewId: StudyFlowConflictReviewId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        sequence: Long,
        decision: StudyFlowConflictResolutionKind,
        mergedSummary: String? = null,
    ): StudyFlowConflictReviewStatus = conflicts.resolve(
        StudyFlowConflictResolution(
            resolutionId = resolutionId,
            reviewId = reviewId,
            workspaceId = workspaceId,
            resolverDeviceId = localDevice.deviceId,
            targetDeviceId = target.deviceId,
            sequence = sequence,
            decision = decision,
            mergedSummary = mergedSummary,
            resolvedAtEpochMillis = clock.nowEpochMillis(),
        ),
    ).status
}
