package com.mikeyphw.studyflow.core.tvcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvUnifiedControlPlaneOv21Test {
    private val endpoint = StudyFlowTvReceiverEndpoint(
        serviceName = "Living Room TV",
        hostAddress = "192.168.1.42",
        port = 48221,
    )
    private val credential = StudyFlowTvSessionCredential(
        sessionId = "session-ov21",
        sessionSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        tvDeviceName = "Living Room TV",
        pairedDeviceName = "Pixel",
        pairedAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 120_000L,
    ).bindToReceiver(endpoint)

    @Test
    fun signedPlaneEnvelopeBindsCommandToSessionReceiverOperationAndPayload() {
        val request = StudyFlowTvWireRequest(
            requestId = "request-ov21-command",
            operation = StudyFlowTvWireOperation.COMMAND,
            sessionId = credential.sessionId,
            command = StudyFlowTvControlCommand(
                commandId = "command-ov21",
                surface = StudyFlowTvSurface.TODAY,
                action = StudyFlowTvControlAction.OPEN,
                issuedAtEpochMillis = 10_000L,
            ),
            issuedAtEpochMillis = 10_000L,
        )
        val withPlane = StudyFlowTvUnifiedControlPlane.attachEnvelope(request, credential, endpoint, 10_000L)
        val signed = StudyFlowTvWireAuthenticator.sign(withPlane, credential)

        assertTrue(StudyFlowTvWireAuthenticator.verify(signed, credential, 10_001L))
        assertEquals(StudyFlowTvPlaneOperation.COMMAND, signed.planeEnvelope?.operation)
        assertEquals(endpoint.serviceName, signed.planeEnvelope?.receiverServiceName)
        assertTrue(StudyFlowTvUnifiedControlPlane.authorizeAuthenticatedRequest(signed, credential, 10_001L).accepted)

        val tampered = signed.copy(command = signed.command?.copy(action = StudyFlowTvControlAction.BACK))
        val decision = StudyFlowTvUnifiedControlPlane.authorizeAuthenticatedRequest(tampered, credential, 10_001L)
        assertFalse(decision.accepted)
        assertEquals("tv-plane-payload-hash-mismatch", decision.reason)
    }

    @Test
    fun presentationAndStopCastCarryCastScope() {
        val snapshot = StudyFlowTvPresentationSnapshot.waitingForPlan("plan-1", 12_000L)
        val presentationRequest = StudyFlowTvWireRequest(
            requestId = "presentation-1",
            operation = StudyFlowTvWireOperation.PRESENTATION,
            sessionId = credential.sessionId,
            presentation = snapshot,
            issuedAtEpochMillis = 12_000L,
        )
        val presentation = StudyFlowTvUnifiedControlPlane.attachEnvelope(presentationRequest, credential, endpoint, 12_000L)
        assertEquals("plan-1", presentation.planeEnvelope?.castScopeId)
        assertEquals(snapshot.snapshotId, presentation.planeEnvelope?.presentationSnapshotId)

        val stopCast = StudyFlowTvWireRequest(
            requestId = "stop-cast-1",
            operation = StudyFlowTvWireOperation.COMMAND,
            sessionId = credential.sessionId,
            command = StudyFlowTvControlCommand(
                commandId = "stop-cast-command",
                surface = StudyFlowTvSurface.STUDY_SESSION,
                action = StudyFlowTvControlAction.STOP_CAST,
                targetId = "plan-1",
                issuedAtEpochMillis = 12_500L,
            ),
            issuedAtEpochMillis = 12_500L,
        )
        val signedStop = StudyFlowTvWireAuthenticator.sign(
            StudyFlowTvUnifiedControlPlane.attachEnvelope(stopCast, credential, endpoint, 12_500L),
            credential,
        )
        assertTrue(StudyFlowTvUnifiedControlPlane.authorizeAuthenticatedRequest(signedStop, credential, 12_501L).accepted)
    }
}
