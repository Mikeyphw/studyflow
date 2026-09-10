package com.mikeyphw.studyflow.link

import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowActivityCursor
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionCursor
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionParticipant
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionRole
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionState
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationApplyResult
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationContentKind
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationFrame
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionOffer
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionReceiver
import com.mikeyphw.studyflow.core.link.StudyFlowProjectionId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class AndroidStudyFlowLiveProjectionBridge(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    fun liveSessionSnapshot(
        workspaceId: StudyFlowWorkspaceId,
        sessionId: StudyFlowLiveSessionId,
        activityId: StudyFlowActivityId,
        positionToken: String,
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
        cursor = StudyFlowLiveSessionCursor(activityId, positionToken),
        participants = listOf(
            StudyFlowLiveSessionParticipant(localDevice, StudyFlowLiveSessionRole.EXECUTOR, clock.nowEpochMillis()),
        ),
        updatedAtEpochMillis = clock.nowEpochMillis(),
    )

    fun projectionOffer(
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
        contentKind = StudyFlowPresentationContentKind.STUDY_SESSION,
        entityId = entityId,
        title = title,
        contentHash = contentHash,
        requiredCapabilities = StudyFlowPresentationProjectionPolicy.defaultSinkCapabilities(
            StudyFlowPresentationContentKind.STUDY_SESSION,
        ),
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun acceptProjectionFrame(
        sink: StudyFlowLinkPeerDescriptor,
        offer: StudyFlowPresentationProjectionOffer,
        frame: StudyFlowPresentationFrame,
    ): Boolean {
        val receiver = StudyFlowPresentationProjectionReceiver(sink, clock)
        if (receiver.accept(offer) !is StudyFlowPresentationApplyResult.Accepted) return false
        return receiver.applyFrame(frame) is StudyFlowPresentationApplyResult.Accepted
    }
}

