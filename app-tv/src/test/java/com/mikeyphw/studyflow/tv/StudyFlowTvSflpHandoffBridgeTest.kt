package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.link.StudyFlowActivityContinuityMode
import com.mikeyphw.studyflow.core.link.StudyFlowActivityCursor
import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowActivityKind
import com.mikeyphw.studyflow.core.link.StudyFlowActivitySnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowHandoffOffer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.tv.link.StudyFlowTvSflpHandoffBridge
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvSflpHandoffBridgeTest {
    @Test
    fun tvBridgeAcceptsPresentationSinkHandoffOnce() {
        val handoffId = StudyFlowHandoffId("handoff-tv-projection")
        val bridge = StudyFlowTvSflpHandoffBridge(tv(), StudyFlowPeerClock { 1_000L })
        val accepted = bridge.acceptPresentationHandoff(offer(handoffId))

        assertTrue(accepted)
        assertTrue(bridge.hasConsumed(handoffId))
    }

    private fun offer(handoffId: StudyFlowHandoffId) = StudyFlowHandoffOffer(
        handoffId = handoffId,
        sourceDevice = phone(),
        targetDeviceId = tv().deviceId,
        snapshot = StudyFlowActivitySnapshot(
            activityId = StudyFlowActivityId("activity-tv-projection"),
            workspaceId = StudyFlowWorkspaceId("workspace-tv"),
            kind = StudyFlowActivityKind.PRESENTATION_PROJECTION,
            entityId = StudyFlowEntityId("material-tv"),
            cursor = StudyFlowActivityCursor("slide-1"),
            continuityMode = StudyFlowActivityContinuityMode.PRESENTATION_SINK,
            requiredCapabilities = listOf(StudyFlowLinkCapabilities.DevicePresentationSink),
            payloadHash = "0".repeat(64),
        ),
        createdAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 90_000L,
        oneTimeNonceHex = "00112233445566778899aabbccddeeff",
    )

    private fun tv() = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("device-tv"),
        displayName = "Android TV",
        clientKind = StudyFlowLinkClientKind.ANDROID_TV,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID_TV),
    )

    private fun phone() = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("device-phone"),
        displayName = "Phone",
        clientKind = StudyFlowLinkClientKind.ANDROID,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID),
    )
}
