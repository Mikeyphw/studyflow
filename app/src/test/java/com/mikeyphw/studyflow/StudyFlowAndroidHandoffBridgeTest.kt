package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.link.StudyFlowActivityContinuityMode
import com.mikeyphw.studyflow.core.link.StudyFlowActivityCursor
import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowActivityKind
import com.mikeyphw.studyflow.core.link.StudyFlowActivitySnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityRevision
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.link.AndroidStudyFlowHandoffBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyFlowAndroidHandoffBridgeTest {
    @Test
    fun androidBridgeCanOfferActivityToDesktopWithoutBecomingHub() {
        var now = 1_000L
        val bridge = AndroidStudyFlowHandoffBridge(android(), StudyFlowPeerClock { now++ })
        val offer = bridge.offerActivityToPeer(
            target = desktop(),
            snapshot = snapshot(),
            handoffId = StudyFlowHandoffId("handoff-android-desktop"),
            nonceHex = "00112233445566778899aabbccddeeff",
        )

        assertEquals(android().deviceId, offer.sourceDevice.deviceId)
        assertEquals(desktop().deviceId, offer.targetDeviceId)
    }

    private fun snapshot(): StudyFlowActivitySnapshot = StudyFlowActivitySnapshot(
        activityId = StudyFlowActivityId("activity-android-session"),
        workspaceId = StudyFlowWorkspaceId("workspace-android"),
        kind = StudyFlowActivityKind.STUDY_SESSION,
        entityId = StudyFlowEntityId("session-android"),
        entityRevision = StudyFlowEntityRevision(1),
        cursor = StudyFlowActivityCursor("question-1"),
        continuityMode = StudyFlowActivityContinuityMode.LIVE_CONTROL,
        requiredCapabilities = listOf(StudyFlowLinkCapabilities.SessionExecute),
        payloadHash = "0".repeat(64),
    )

    private fun android() = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("device-android"),
        displayName = "Android",
        clientKind = StudyFlowLinkClientKind.ANDROID,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID),
    )

    private fun desktop() = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("device-desktop"),
        displayName = "Desktop",
        clientKind = StudyFlowLinkClientKind.DESKTOP,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP),
    )
}
