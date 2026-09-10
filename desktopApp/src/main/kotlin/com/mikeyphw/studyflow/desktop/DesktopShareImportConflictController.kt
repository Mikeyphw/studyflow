package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowConflictCandidate
import com.mikeyphw.studyflow.core.link.StudyFlowConflictResolution
import com.mikeyphw.studyflow.core.link.StudyFlowConflictResolutionId
import com.mikeyphw.studyflow.core.link.StudyFlowConflictResolutionKind
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReview
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewId
import com.mikeyphw.studyflow.core.link.StudyFlowConflictReviewReducer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportDefaults
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportId
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportIntent
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportOffer
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportPayloadKind
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class DesktopShareImportConflictController(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val conflictReducer = StudyFlowConflictReviewReducer(localDevice, clock)

    fun sharedMarkdownImport(
        importId: StudyFlowShareImportId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        title: String,
        markdown: String,
    ): StudyFlowShareImportOffer = StudyFlowShareImportOffer(
        importId = importId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        targetDeviceId = target.deviceId,
        intent = StudyFlowShareImportIntent.SHARE_IMPORT,
        payloadKind = StudyFlowShareImportPayloadKind.MARKDOWN,
        title = title,
        mimeType = "text/markdown",
        previewText = markdown.take(16_384),
        contentSha256 = StudyFlowShareImportDefaults.sha256Hex(markdown),
        requiredCapabilities = StudyFlowShareImportDefaults.textCapabilitiesFor(StudyFlowShareImportIntent.SHARE_IMPORT),
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun queueConflictReview(
        reviewId: StudyFlowConflictReviewId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        candidate: StudyFlowConflictCandidate,
    ): Boolean = conflictReducer.open(
        StudyFlowConflictReview(
            reviewId = reviewId,
            workspaceId = workspaceId,
            sourceDevice = localDevice,
            targetDeviceId = target.deviceId,
            candidate = candidate,
            createdAtEpochMillis = clock.nowEpochMillis(),
        ),
    )

    fun resolveMerge(
        resolutionId: StudyFlowConflictResolutionId,
        reviewId: StudyFlowConflictReviewId,
        workspaceId: StudyFlowWorkspaceId,
        sequence: Long,
        mergedSummary: String,
    ) = conflictReducer.resolve(
        StudyFlowConflictResolution(
            resolutionId = resolutionId,
            reviewId = reviewId,
            workspaceId = workspaceId,
            resolverDeviceId = localDevice.deviceId,
            sequence = sequence,
            decision = StudyFlowConflictResolutionKind.MERGE,
            mergedSummary = mergedSummary,
            resolvedAtEpochMillis = clock.nowEpochMillis(),
        ),
    )
}
