package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudyFlowControlInputDelegationTest {
    private val workspace = StudyFlowWorkspaceId("workspace-main")
    private val phone = descriptor("android-phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
    private val tv = descriptor("living-tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
    private val desktop = descriptor("desktop-main", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
    private val clock = StudyFlowPeerClock { 1_000L }

    @Test
    fun controlLeaseAcceptsMonotonicCommandsAndRejectsDuplicates() {
        val lease = StudyFlowControlPolicy.grantLease(
            workspaceId = workspace,
            controller = phone,
            target = tv,
            sessionId = StudyFlowControlSessionId("control-tv-1"),
            leaseToken = StudyFlowControlLeaseToken("lease-token-1"),
            allowedCommands = listOf(StudyFlowControlCommandKind.NAVIGATE_BACK, StudyFlowControlCommandKind.SELECT),
            nowEpochMillis = 1_000L,
        )
        val reducer = StudyFlowControlCommandReducer(tv, clock)
        reducer.rememberLease(lease)
        val command = StudyFlowControlCommand(
            messageId = StudyFlowMessageId("msg-control-1"),
            sessionId = lease.sessionId,
            workspaceId = workspace,
            sourceDeviceId = phone.deviceId,
            targetDeviceId = tv.deviceId,
            leaseToken = lease.leaseToken,
            sequence = 1,
            kind = StudyFlowControlCommandKind.SELECT,
            occurredAtEpochMillis = 1_000L,
        )
        val result = reducer.apply(command)
        assertIs<StudyFlowControlApplyResult.Accepted>(result)
        assertEquals(1, result.ack.acknowledgedSequence)
        assertIs<StudyFlowControlApplyResult.Duplicate>(reducer.apply(command))
    }

    @Test
    fun wrongLeaseOrWriteCommandIsRejectedForTvClassSinks() {
        val lease = StudyFlowControlPolicy.grantLease(
            workspaceId = workspace,
            controller = phone,
            target = tv,
            sessionId = StudyFlowControlSessionId("control-tv-2"),
            leaseToken = StudyFlowControlLeaseToken("lease-token-2"),
            allowedCommands = listOf(StudyFlowControlCommandKind.NAVIGATE_HOME),
            nowEpochMillis = 1_000L,
        )
        val reducer = StudyFlowControlCommandReducer(tv, clock)
        reducer.rememberLease(lease)
        val result = reducer.apply(
            StudyFlowControlCommand(
                messageId = StudyFlowMessageId("msg-control-2"),
                sessionId = lease.sessionId,
                workspaceId = workspace,
                sourceDeviceId = phone.deviceId,
                targetDeviceId = tv.deviceId,
                leaseToken = StudyFlowControlLeaseToken("wrong-token"),
                sequence = 1,
                kind = StudyFlowControlCommandKind.NAVIGATE_HOME,
                occurredAtEpochMillis = 1_000L,
            ),
        )
        assertEquals(StudyFlowControlApplyResult.Rejected("bad_lease_token"), result)
    }

    @Test
    fun inputDelegationIsTargetGatedAndMonotonic() {
        val offer = StudyFlowControlPolicy.inputOffer(
            delegationId = StudyFlowInputDelegationId("input-delegation-1"),
            workspaceId = workspace,
            source = desktop,
            target = phone,
            requestedInput = listOf(StudyFlowLinkCapabilities.KeyboardInput, StudyFlowLinkCapabilities.TextInputProvide),
            nonceHex = "0123456789abcdef0123456789abcdef",
            nowEpochMillis = 1_000L,
        )
        val reducer = StudyFlowInputDelegationReducer(phone, clock)
        assertIs<StudyFlowInputDelegationResult.Accepted>(reducer.accept(offer))
        val input = StudyFlowInputEvent(
            messageId = StudyFlowMessageId("msg-input-1"),
            delegationId = offer.delegationId,
            workspaceId = workspace,
            sourceDeviceId = desktop.deviceId,
            targetDeviceId = phone.deviceId,
            sequence = 1,
            kind = StudyFlowInputEventKind.TEXT_COMMIT,
            text = "answer",
            occurredAtEpochMillis = 1_000L,
        )
        assertIs<StudyFlowInputDelegationResult.Accepted>(reducer.apply(input))
        assertEquals(StudyFlowInputDelegationResult.Rejected("stale_sequence"), reducer.apply(input.copy(messageId = StudyFlowMessageId("msg-input-2"))))
    }

    @Test
    fun controlAndInputMessageNamesAreStable() {
        assertEquals("control.command", StudyFlowControlMessageNames.Command)
        assertEquals("control.command_ack", StudyFlowControlMessageNames.CommandAck)
        assertEquals("input.delegation_offer", StudyFlowControlMessageNames.InputOffer)
        assertEquals("input.event", StudyFlowControlMessageNames.InputEvent)
    }

    private fun descriptor(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
    ) = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        clientKind = kind,
        displayName = id,
        capabilities = capabilities,
        publicKey = StudyFlowPublicKey("public-key-$id"),
    )
}
