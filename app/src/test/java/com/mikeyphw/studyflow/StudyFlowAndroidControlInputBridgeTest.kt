package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowControlCommandKind
import com.mikeyphw.studyflow.core.link.StudyFlowControlLeaseToken
import com.mikeyphw.studyflow.core.link.StudyFlowControlSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowPublicKey
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.link.AndroidStudyFlowControlInputBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyFlowAndroidControlInputBridgeTest {
    @Test
    fun androidBridgeCreatesTargetGatedTvControlCommand() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val bridge = AndroidStudyFlowControlInputBridge(phone, StudyFlowPeerClock { 1_000L })
        val lease = bridge.leaseTvControl(
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            target = tv,
            sessionId = StudyFlowControlSessionId("session-control-1"),
            token = StudyFlowControlLeaseToken("lease-token-1"),
        )
        val command = bridge.controlCommand(
            messageId = StudyFlowMessageId("message-control-1"),
            lease = lease,
            sequence = 1,
            kind = StudyFlowControlCommandKind.SELECT,
        )
        assertEquals(phone.deviceId, command.sourceDeviceId)
        assertEquals(tv.deviceId, command.targetDeviceId)
        assertTrue(StudyFlowControlCommandKind.SELECT in lease.allowedCommands)
    }

    private fun descriptor(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability>,
    ) = StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), kind, id, capabilities, StudyFlowPublicKey("key-$id"))
}
