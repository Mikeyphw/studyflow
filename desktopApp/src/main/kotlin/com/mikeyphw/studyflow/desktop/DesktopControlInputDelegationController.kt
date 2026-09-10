package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowControlCommandKind
import com.mikeyphw.studyflow.core.link.StudyFlowControlLease
import com.mikeyphw.studyflow.core.link.StudyFlowControlLeaseToken
import com.mikeyphw.studyflow.core.link.StudyFlowControlPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowControlSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationId
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationOffer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class DesktopControlInputDelegationController(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    fun createRemoteControlLease(
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
        allowedCommands = StudyFlowControlCommandKind.entries.filterNot {
            it == StudyFlowControlCommandKind.COMPLETE_SESSION &&
                target.clientKind == com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind.ANDROID_TV
        },
        nowEpochMillis = clock.nowEpochMillis(),
    )

    fun requestKeyboardInput(
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        delegationId: StudyFlowInputDelegationId,
        nonceHex: String,
    ): StudyFlowInputDelegationOffer = StudyFlowControlPolicy.inputOffer(
        delegationId = delegationId,
        workspaceId = workspaceId,
        source = localDevice,
        target = target,
        requestedInput = listOf(StudyFlowLinkCapabilities.KeyboardInput, StudyFlowLinkCapabilities.TextInputProvide),
        nonceHex = nonceHex,
        nowEpochMillis = clock.nowEpochMillis(),
    )
}
