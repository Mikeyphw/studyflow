package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowWebTvConnectivityTest {
    @Test
    fun webTvReceiverOfferCarriesQrEndpointAndPresentationCapabilities() {
        var now = 10_000L
        val session = StudyFlowWebTvConnectivitySession(
            receiverIdentity = webTv(),
            workspaceId = StudyFlowWorkspaceId("workspace-tv"),
            endpointHints = listOf("wss://studyflow.local/sflp"),
            clock = StudyFlowPeerClock { now++ },
        )
        val offer = session.receiverOffer("123456")

        assertEquals("123456", offer.pairingCode)
        assertTrue(offer.qrPayload.contains("studyflow.sflp.web_tv.receiver.v1"))
        assertTrue(offer.endpointHints.single().startsWith("wss://"))
        assertTrue(offer.receiver.capabilities.contains(StudyFlowLinkCapabilities.DevicePresentationSink))
    }

    @Test
    fun projectionSnapshotRequiresPresentationSinkAndDpad() {
        val session = StudyFlowWebTvConnectivitySession(
            receiverIdentity = webTv(),
            workspaceId = StudyFlowWorkspaceId("workspace-tv"),
            endpointHints = listOf("ws://127.0.0.1:8080/sflp"),
            clock = StudyFlowPeerClock { 1_000L },
        )
        val snapshot = session.projectionSnapshot(
            activityId = StudyFlowActivityId("projection-1"),
            entityId = StudyFlowEntityId("material-1"),
            cursor = StudyFlowActivityCursor(positionToken = "slide-2", scrollRatio = 0.5),
            payload = sampleStudyFlowProjectionPayload("Biology review"),
        )

        assertEquals(StudyFlowActivityKind.PRESENTATION_PROJECTION, snapshot.kind)
        assertEquals(StudyFlowActivityContinuityMode.PRESENTATION_SINK, snapshot.continuityMode)
        assertTrue(snapshot.requiredCapabilities.contains(StudyFlowLinkCapabilities.DevicePresentationSink))
        assertTrue(snapshot.requiredCapabilities.contains(StudyFlowLinkCapabilities.DpadInput))
    }

    @Test
    fun receiverAcceptsOnlyCommandsTargetedToIt() {
        val session = StudyFlowWebTvConnectivitySession(
            receiverIdentity = webTv(),
            workspaceId = StudyFlowWorkspaceId("workspace-tv"),
            endpointHints = listOf("ws://127.0.0.1:8080/sflp"),
            clock = StudyFlowPeerClock { 1_000L },
        )
        val accepted = StudyFlowRemoteInputCommand(
            commandId = StudyFlowMessageId("input-ok"),
            sourceDeviceId = StudyFlowDeviceId("device-web"),
            targetDeviceId = StudyFlowDeviceId("device-webtv"),
            kind = StudyFlowRemoteInputKind.SELECT,
            issuedAtEpochMillis = 1_000L,
        )
        val ignored = accepted.copy(
            commandId = StudyFlowMessageId("input-other"),
            targetDeviceId = StudyFlowDeviceId("device-other"),
        )

        assertTrue(session.acceptsRemoteInput(accepted))
        assertFalse(session.acceptsRemoteInput(ignored))
    }

    private fun webTv(): StudyFlowLinkPeerDescriptor = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId("device-webtv"),
        displayName = "StudyFlow Web TV",
        clientKind = StudyFlowLinkClientKind.WEB_TV,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.WEB_TV),
    )
}
