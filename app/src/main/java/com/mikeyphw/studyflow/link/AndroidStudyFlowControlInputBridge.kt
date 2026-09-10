package com.mikeyphw.studyflow.link

import com.mikeyphw.studyflow.core.link.StudyFlowControlCommand
import com.mikeyphw.studyflow.core.link.StudyFlowControlCommandKind
import com.mikeyphw.studyflow.core.link.StudyFlowControlLease
import com.mikeyphw.studyflow.core.link.StudyFlowControlLeaseToken
import com.mikeyphw.studyflow.core.link.StudyFlowControlPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowControlSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationId
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationOffer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class AndroidStudyFlowControlInputBridge(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    fun leaseTvControl(
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        sessionId: StudyFlowControlSessionId,
        token: StudyFlowControlLeaseToken,
    ): StudyFlowControlLease = StudyFlowControlPolicy.grantLease(
        workspaceId = workspaceId,
        controller = localDevice,
        target = target,
        sessionId = sessionId,
        leaseToken = token,
        allowedCommands = listOf(
            StudyFlowControlCommandKind.NAVIGATE_BACK,
            StudyFlowControlCommandKind.SELECT,
            StudyFlowControlCommandKind.FOCUS_NEXT,
            StudyFlowControlCommandKind.FOCUS_PREVIOUS,
            StudyFlowControlCommandKind.PAUSE_SESSION,
            StudyFlowControlCommandKind.RESUME_SESSION,
            StudyFlowControlCommandKind.START_PROJECTION,
            StudyFlowControlCommandKind.STOP_PROJECTION,
        ),
        nowEpochMillis = clock.nowEpochMillis(),
    )

    fun controlCommand(
        messageId: StudyFlowMessageId,
        lease: StudyFlowControlLease,
        sequence: Long,
        kind: StudyFlowControlCommandKind,
    ): StudyFlowControlCommand = StudyFlowControlCommand(
        messageId = messageId,
        sessionId = lease.sessionId,
        workspaceId = lease.workspaceId,
        sourceDeviceId = localDevice.deviceId,
        targetDeviceId = lease.targetDeviceId,
        leaseToken = lease.leaseToken,
        sequence = sequence,
        kind = kind,
        occurredAtEpochMillis = clock.nowEpochMillis(),
    )

    fun offerTextInput(
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        delegationId: StudyFlowInputDelegationId,
        nonceHex: String,
    ): StudyFlowInputDelegationOffer = StudyFlowControlPolicy.inputOffer(
        delegationId = delegationId,
        workspaceId = workspaceId,
        source = localDevice,
        target = target,
        requestedInput = listOf(StudyFlowLinkCapabilities.TextInputProvide),
        nonceHex = nonceHex,
        nowEpochMillis = clock.nowEpochMillis(),
    )
}
