package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowControlApplyResult
import com.mikeyphw.studyflow.core.link.StudyFlowControlCommand
import com.mikeyphw.studyflow.core.link.StudyFlowControlCommandKind
import com.mikeyphw.studyflow.core.link.StudyFlowControlLeaseToken
import com.mikeyphw.studyflow.core.link.StudyFlowControlPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowControlSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowPublicKey
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.tv.link.StudyFlowTvControlInputReceiver
import kotlin.test.Test
import kotlin.test.assertIs

class StudyFlowTvControlInputReceiverTest {
    @Test
    fun tvReceiverAcceptsOnlyLeasedControl() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val lease = StudyFlowControlPolicy.grantLease(
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            controller = phone,
            target = tv,
            sessionId = StudyFlowControlSessionId("control-session-1"),
            leaseToken = StudyFlowControlLeaseToken("lease-token-1"),
            allowedCommands = listOf(StudyFlowControlCommandKind.SELECT),
            nowEpochMillis = 1_000L,
        )
        val receiver = StudyFlowTvControlInputReceiver(tv, StudyFlowPeerClock { 1_000L })
        receiver.rememberLease(lease)
        assertIs<StudyFlowControlApplyResult.Accepted>(
            receiver.applyControl(
                StudyFlowControlCommand(
                    messageId = StudyFlowMessageId("control-message-1"),
                    sessionId = lease.sessionId,
                    workspaceId = lease.workspaceId,
                    sourceDeviceId = phone.deviceId,
                    targetDeviceId = tv.deviceId,
                    leaseToken = lease.leaseToken,
                    sequence = 1,
                    kind = StudyFlowControlCommandKind.SELECT,
                    occurredAtEpochMillis = 1_000L,
                ),
            ),
        )
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), kind, id, capabilities, StudyFlowPublicKey("key-$id"))
}
