package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowPresentationProjectionTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-projection")
    private val source = peer("desktop-projector", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
    private val sink = peer("web-tv-sink", StudyFlowLinkClientKind.WEB_TV, StudyFlowClientProfiles.WebTv)
    private val clock = StudyFlowPeerClock { 5_000L }

    @Test
    fun projectionOfferRequiresSinkCapabilitiesAndRejectsWrites() {
        val receiver = StudyFlowPresentationProjectionReceiver(sink, clock)
        val offer = projectionOffer()

        assertTrue(receiver.accept(offer) is StudyFlowPresentationApplyResult.Accepted)

        val unsafe = offer.copy(
            projectionId = StudyFlowProjectionId("projection-unsafe"),
            requiredCapabilities = offer.requiredCapabilities + StudyFlowLinkCapabilities.WorkspaceWrite,
        )
        val rejected = receiver.accept(unsafe) as StudyFlowPresentationApplyResult.Rejected
        assertEquals("projection_write_capability", rejected.reason)
    }

    @Test
    fun framesAreMonotonicAndAcknowledgedByContentHash() {
        val receiver = StudyFlowPresentationProjectionReceiver(sink, clock)
        receiver.accept(projectionOffer())
        val frame1 = frame("frame-1", sequence = 1)
        val frame2 = frame("frame-2", sequence = 2, cursor = StudyFlowActivityCursor("slide-2"))

        assertTrue(receiver.applyFrame(frame1) is StudyFlowPresentationApplyResult.Accepted)
        assertEquals("stale_frame", (receiver.applyFrame(frame("frame-old", 1)) as StudyFlowPresentationApplyResult.Rejected).reason)
        assertTrue(receiver.applyFrame(frame2) is StudyFlowPresentationApplyResult.Accepted)

        val ack = receiver.acknowledge(StudyFlowProjectionId("projection-main"))
        assertNotNull(ack)
        assertEquals(2, ack!!.acknowledgedSequence)
        assertEquals("a".repeat(64), ack.contentHash)
        assertEquals("slide-2", receiver.latestFrame(StudyFlowProjectionId("projection-main"))!!.cursor.positionToken)
    }

    @Test
    fun projectionWireEnvelopeCarriesCanonicalMessageNames() {
        val envelope = StudyFlowPresentationProjectionWire.frame(
            messageId = StudyFlowMessageId("message-frame"),
            workspaceId = workspaceId,
            senderDeviceId = source.deviceId,
            sentAtEpochMillis = 5_100L,
            frame = frame("frame-wire", 3),
        )
        assertEquals(StudyFlowPresentationProjectionMessageNames.Frame, envelope.name)
        val decoded = StudyFlowLinkCodec.decodePayload(StudyFlowPresentationFrame.serializer(), envelope.payload)
        assertEquals(3, decoded.sequence)
    }

    private fun projectionOffer(): StudyFlowPresentationProjectionOffer = StudyFlowPresentationProjectionOffer(
        projectionId = StudyFlowProjectionId("projection-main"),
        workspaceId = workspaceId,
        sourceDevice = source,
        targetDeviceId = sink.deviceId,
        contentKind = StudyFlowPresentationContentKind.STUDY_SESSION,
        entityId = StudyFlowEntityId("session-live"),
        title = "Live Study Session",
        contentHash = "a".repeat(64),
        requiredCapabilities = StudyFlowPresentationProjectionPolicy.defaultSinkCapabilities(
            StudyFlowPresentationContentKind.STUDY_SESSION,
        ),
        createdAtEpochMillis = 4_000L,
        expiresAtEpochMillis = 10_000L,
    )

    private fun frame(
        id: String,
        sequence: Long,
        cursor: StudyFlowActivityCursor = StudyFlowActivityCursor("slide-1"),
    ): StudyFlowPresentationFrame = StudyFlowPresentationFrame(
        projectionId = StudyFlowProjectionId("projection-main"),
        frameId = StudyFlowMessageId(id),
        workspaceId = workspaceId,
        sourceDeviceId = source.deviceId,
        sequence = sequence,
        contentRevision = StudyFlowEntityRevision(sequence),
        contentHash = "a".repeat(64),
        cursor = cursor,
    )

    private fun peer(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
    ): StudyFlowLinkPeerDescriptor = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        displayName = id,
        clientKind = kind,
        capabilities = capabilities,
    )
}
