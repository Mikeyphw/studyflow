package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowActivityHandoffTest {
    @Test
    fun registryConsumesHandoffOnlyOnce() {
        var now = 1_000L
        val registry = StudyFlowActivityHandoffRegistry(StudyFlowPeerClock { now++ })
        val offer = offerFor(target = desktop())
        registry.issue(offer)

        val first = registry.consume(offer.handoffId, desktop())
        val second = registry.consume(offer.handoffId, desktop())

        assertTrue(first is StudyFlowHandoffDecision.Accepted)
        assertTrue(second is StudyFlowHandoffDecision.Rejected)
        assertTrue(registry.consumed(offer.handoffId))
    }

    @Test
    fun expiredAndWrongTargetOffersAreRejected() {
        var now = 20_000L
        val registry = StudyFlowActivityHandoffRegistry(StudyFlowPeerClock { now })
        val expired = offerFor(target = desktop()).copy(
            createdAtEpochMillis = 1L,
            expiresAtEpochMillis = 2L,
        )
        val wrongTarget = offerFor(target = desktop()).copy(targetDeviceId = StudyFlowDeviceId("device-other"))

        registry.issue(wrongTarget)
        assertTrue(registry.pendingOffers().isNotEmpty())
        assertTrue(StudyFlowHandoffPolicy.evaluate(expired, desktop(), now) is StudyFlowHandoffDecision.Rejected)
        assertTrue(registry.consume(wrongTarget.handoffId, desktop()) is StudyFlowHandoffDecision.Rejected)
    }

    @Test
    fun tvReceiversRejectWriteCapabilityHandoffs() {
        val writingSnapshot = snapshot(required = listOf(StudyFlowLinkCapabilities.WritingEdit))
        val offer = offerFor(target = webTv(), snapshot = writingSnapshot)
        val decision = StudyFlowHandoffPolicy.evaluate(offer, webTv(), nowEpochMillis = 2_000L)

        assertTrue(decision is StudyFlowHandoffDecision.Rejected)
    }

    @Test
    fun resumeAndRemoteInputUseValidSflpEnvelopes() {
        val offer = offerFor(target = desktop())
        val envelope = StudyFlowActivityHandoffWire.offer(
            messageId = StudyFlowMessageId("message-handoff-offer"),
            senderDeviceId = offer.sourceDevice.deviceId,
            sentAtEpochMillis = 1_000L,
            offer = offer,
        )
        val command = StudyFlowRemoteInputCommand(
            commandId = StudyFlowMessageId("input-1"),
            sourceDeviceId = StudyFlowDeviceId("device-desktop"),
            targetDeviceId = StudyFlowDeviceId("device-webtv"),
            kind = StudyFlowRemoteInputKind.DPAD_DOWN,
            issuedAtEpochMillis = 1_001L,
        )
        val commandEnvelope = StudyFlowActivityHandoffWire.remoteInput(
            messageId = StudyFlowMessageId("message-remote-input"),
            workspaceId = StudyFlowWorkspaceId("workspace-handoff"),
            senderDeviceId = command.sourceDeviceId,
            sentAtEpochMillis = 1_002L,
            command = command,
        )

        assertEquals(StudyFlowActivityHandoffMessageNames.Offer, envelope.name)
        assertEquals(StudyFlowLinkMessageType.REQUEST, envelope.messageType)
        assertEquals(StudyFlowActivityHandoffMessageNames.RemoteInput, commandEnvelope.name)
        assertEquals(StudyFlowLinkMessageType.EVENT, commandEnvelope.messageType)
    }

    @Test
    fun coordinatorCreatesTargetedHandoffAndAcceptsIt() {
        var now = 5_000L
        val source = android()
        val target = desktop()
        val registry = StudyFlowActivityHandoffRegistry(StudyFlowPeerClock { now++ })
        val coordinator = StudyFlowActivityHandoffCoordinator(source, registry, StudyFlowPeerClock { now++ })
        val offered = coordinator.offerTo(
            target = target,
            snapshot = snapshot(),
            handoffId = StudyFlowHandoffId("handoff-coordinator"),
            nonceHex = "00112233445566778899aabbccddeeff",
        )
        val accept = coordinator.accept(offered, target)

        assertEquals(offered.handoffId, accept.handoffId)
        assertEquals(target.deviceId, accept.acceptedBy.deviceId)
        assertFalse(registry.pendingOffers(now).contains(offered))
    }

    private fun offerFor(
        target: StudyFlowLinkPeerDescriptor?,
        snapshot: StudyFlowActivitySnapshot = snapshot(),
    ): StudyFlowHandoffOffer = StudyFlowHandoffOffer(
        handoffId = StudyFlowHandoffId("handoff-${target?.deviceId?.value ?: "broadcast"}"),
        sourceDevice = android(),
        targetDeviceId = target?.deviceId,
        snapshot = snapshot,
        createdAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 90_000L,
        oneTimeNonceHex = "00112233445566778899aabbccddeeff",
    )

    private fun snapshot(
        required: List<StudyFlowLinkCapability> = listOf(StudyFlowLinkCapabilities.SessionExecute),
    ): StudyFlowActivitySnapshot = StudyFlowActivitySnapshot(
        activityId = StudyFlowActivityId("activity-session"),
        workspaceId = StudyFlowWorkspaceId("workspace-handoff"),
        kind = StudyFlowActivityKind.STUDY_SESSION,
        entityId = StudyFlowEntityId("session-1"),
        entityRevision = StudyFlowEntityRevision(1),
        cursor = StudyFlowActivityCursor(positionToken = "question-3", questionIndex = 3, elapsedMillis = 42L),
        continuityMode = StudyFlowActivityContinuityMode.LIVE_CONTROL,
        requiredCapabilities = required,
        payloadHash = "0".repeat(64),
        payload = JsonObject(emptyMap()),
    )

    private fun android(): StudyFlowLinkPeerDescriptor = descriptor(
        id = "device-android",
        kind = StudyFlowLinkClientKind.ANDROID,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID),
    )

    private fun desktop(): StudyFlowLinkPeerDescriptor = descriptor(
        id = "device-desktop",
        kind = StudyFlowLinkClientKind.DESKTOP,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP),
    )

    private fun webTv(): StudyFlowLinkPeerDescriptor = descriptor(
        id = "device-webtv",
        kind = StudyFlowLinkClientKind.WEB_TV,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.WEB_TV),
    )

    private fun descriptor(
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
