package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowControlLeaseToken
import com.mikeyphw.studyflow.core.link.StudyFlowControlSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowInputDelegationId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopControlInputDelegationControllerTest {
    @Test
    fun desktopControllerCreatesInputDelegationWithoutAndroidDependencies() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val controller = DesktopControlInputDelegationController(desktop, StudyFlowPeerClock { 2_000L })
        val offer = controller.requestKeyboardInput(
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            target = phone,
            delegationId = StudyFlowInputDelegationId("input-delegation-1"),
            nonceHex = "0123456789abcdef0123456789abcdef",
        )
        assertEquals(phone.deviceId, offer.targetDeviceId)
        assertTrue(offer.acceptedInputCapabilities.isNotEmpty())
    }

    @Test
    fun desktopCanLeaseTvControlAsGenericPeer() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val controller = DesktopControlInputDelegationController(desktop, StudyFlowPeerClock { 2_000L })
        val lease = controller.createRemoteControlLease(
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            target = tv,
            sessionId = StudyFlowControlSessionId("control-session-1"),
            token = StudyFlowControlLeaseToken("lease-token-1"),
        )
        assertEquals(tv.deviceId, lease.targetDeviceId)
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
