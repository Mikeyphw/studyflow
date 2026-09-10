package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionContinuityController
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionCursor
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionParticipant
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionRole
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionState
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationContentKind
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationFrameAck
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionOffer
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowProjectionId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class DesktopLiveSessionProjectionController(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    fun controlLiveSession(snapshot: StudyFlowLiveSessionSnapshot): StudyFlowLiveSessionContinuityController =
        StudyFlowLiveSessionContinuityController(snapshot, clock)

    fun createLiveSnapshot(
        workspaceId: StudyFlowWorkspaceId,
        sessionId: StudyFlowLiveSessionId,
        activityId: StudyFlowActivityId,
        fencingToken: String,
    ): StudyFlowLiveSessionSnapshot = StudyFlowLiveSessionSnapshot(
        sessionId = sessionId,
        workspaceId = workspaceId,
        activityId = activityId,
        state = StudyFlowLiveSessionState.RUNNING,
        activeExecutorDeviceId = localDevice.deviceId,
        fencingToken = fencingToken,
        sessionEpoch = 1,
        lastSequence = 1,
        cursor = StudyFlowLiveSessionCursor(activityId, "desktop-start"),
        participants = listOf(
            StudyFlowLiveSessionParticipant(localDevice, StudyFlowLiveSessionRole.EXECUTOR, clock.nowEpochMillis()),
        ),
        updatedAtEpochMillis = clock.nowEpochMillis(),
    )

    fun createProjectionOffer(
        workspaceId: StudyFlowWorkspaceId,
        projectionId: StudyFlowProjectionId,
        targetDeviceId: StudyFlowDeviceId?,
        entityId: StudyFlowEntityId,
        title: String,
        contentHash: String,
    ): StudyFlowPresentationProjectionOffer = StudyFlowPresentationProjectionOffer(
        projectionId = projectionId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        targetDeviceId = targetDeviceId,
        contentKind = StudyFlowPresentationContentKind.DOCUMENT,
        entityId = entityId,
        title = title,
        contentHash = contentHash,
        requiredCapabilities = StudyFlowPresentationProjectionPolicy.defaultSinkCapabilities(
            StudyFlowPresentationContentKind.DOCUMENT,
        ),
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun rememberAck(ack: StudyFlowPresentationFrameAck): String =
        "${ack.projectionId.value}:${ack.acknowledgedSequence}:${ack.contentHash}"
}
