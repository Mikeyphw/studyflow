package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowActivityContinuityMode
import com.mikeyphw.studyflow.core.link.StudyFlowActivityCursor
import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowActivityKind
import com.mikeyphw.studyflow.core.link.StudyFlowActivitySnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityRevision
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopActivityHandoffControllerTest {
    @Test
    fun desktopCanPublishAndAcceptActivityHandoff() {
        var now = 1_000L
        val controller = DesktopActivityHandoffController(desktop(), StudyFlowPeerClock { now++ })
        val offer = controller.publishHandoff(
            target = phone(),
            snapshot = snapshot(),
            handoffId = StudyFlowHandoffId("handoff-desktop-phone"),
            nonceHex = "00112233445566778899aabbccddeeff",
        )
        val accepted = controller.acceptHandoff(offer, phone())

        assertEquals(offer.handoffId, accepted.handoffId)
        assertEquals(phone().deviceId, accepted.acceptedBy.deviceId)
    }

    private fun snapshot() = StudyFlowActivitySnapshot(
        activityId = StudyFlowActivityId("activity-desktop-writing"),
        workspaceId = StudyFlowWorkspaceId("workspace-desktop"),
        kind = StudyFlowActivityKind.WRITING_DRAFT,
        entityId = StudyFlowEntityId("draft-1"),
        entityRevision = StudyFlowEntityRevision(3),
        cursor = StudyFlowActivityCursor("paragraph-4"),
        continuityMode = StudyFlowActivityContinuityMode.SNAPSHOT,
        requiredCapabilities = listOf(StudyFlowLinkCapabilities.WritingEdit),
        payloadHash = "0".repeat(64),
    )

    private fun desktop() = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("device-desktop"),
        displayName = "Desktop",
        clientKind = StudyFlowLinkClientKind.DESKTOP,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP),
    )

    private fun phone() = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("device-phone"),
        displayName = "Phone",
        clientKind = StudyFlowLinkClientKind.ANDROID,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID),
    )
}
