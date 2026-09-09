package com.mikeyphw.studyflow.core.tvcontrol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvWireProtocolTest {
    @Test
    fun pairingOfferIsSixDigitShortLivedAndCreatesExpiringCredential() {
        val engine = StudyFlowTvPairingEngine()
        val offer = engine.createOffer("Living Room TV", 10_000L)

        assertEquals(6, offer.token.pairingCode.length)
        assertTrue(offer.token.pairingCode.all(Char::isDigit))
        assertEquals(130_000L, offer.token.expiresAtEpochMillis)
        assertTrue(offer.deepLink.startsWith("studyflow://tv/pair?"))
        assertNull(engine.accept(offer, "000000", offer.token.sessionNonce, "Pixel", 11_000L))

        val credential = engine.accept(
            offer = offer,
            pairingCode = offer.token.pairingCode,
            sessionNonce = offer.token.sessionNonce,
            phoneDeviceName = "Pixel",
            nowEpochMillis = 11_000L,
        )
        assertNotNull(credential)
        credential!!
        assertTrue(engine.isAuthorized(credential, credential.sessionId, credential.sessionSecret, 12_000L))
        assertFalse(engine.isAuthorized(credential, credential.sessionId, "wrong-secret", 12_000L))
        assertFalse(
            engine.isAuthorized(
                credential,
                credential.sessionId,
                credential.sessionSecret,
                credential.expiresAtEpochMillis,
            ),
        )
        assertEquals(StudyFlowTvPairingState.PAIRED, credential.toControlSession().pairingState)
    }

    @Test
    fun framedRequestRoundTripsWithoutDatabaseOrUiPayloads() {
        val request = StudyFlowTvWireRequest(
            requestId = "request-1",
            operation = StudyFlowTvWireOperation.COMMAND,
            sessionId = "session-1",
            sessionSecret = "0123456789abcdef0123456789abcdef",
            command = StudyFlowTvControlCommand(
                commandId = "command-1",
                surface = StudyFlowTvSurface.PROGRESS,
                action = StudyFlowTvControlAction.OPEN,
                issuedAtEpochMillis = 20_000L,
            ),
            issuedAtEpochMillis = 20_000L,
        )
        val output = ByteArrayOutputStream()
        StudyFlowTvWireCodec.writeRequest(output, request)
        val framed = output.toByteArray()
        val decoded = StudyFlowTvWireCodec.readRequest(ByteArrayInputStream(framed))

        assertEquals(request, decoded)
        val payload = String(StudyFlowTvWireCodec.encodeRequest(request))
        assertFalse(payload.contains("Room"))
        assertFalse(payload.contains("Dao"))
        assertFalse(payload.contains("database"))
        assertFalse(payload.contains("ViewGroup"))
    }

    @Test
    fun pairingInfoDoesNotExposePairingCodeThroughDiscoveryHandshake() {
        val info = StudyFlowTvPairingInfo(
            tvDeviceName = "Office TV",
            pairingActive = true,
            sessionNonce = "0123456789abcdef",
            expiresAtEpochMillis = 99_000L,
        )
        val response = StudyFlowTvWireResponse(
            requestId = "request-info",
            ok = true,
            pairingInfo = info,
            respondedAtEpochMillis = 1_000L,
        )
        val encoded = String(StudyFlowTvWireCodec.encodeResponse(response))

        assertFalse(encoded.contains("pairingCode"))
        assertTrue(encoded.contains("sessionNonce"))
        assertEquals(response, StudyFlowTvWireCodec.decodeResponse(StudyFlowTvWireCodec.encodeResponse(response)))
    }
    @Test
    fun unauthenticatedPairingMetadataCarriesNoContinuityState() {
        val response = StudyFlowTvWireResponse(
            requestId = "pairing-metadata",
            ok = true,
            pairingInfo = StudyFlowTvPairingInfo(
                tvDeviceName = "Living Room TV",
                pairingActive = true,
                sessionNonce = "0123456789abcdef",
                expiresAtEpochMillis = 90_000L,
            ),
            continuity = null,
            respondedAtEpochMillis = 30_000L,
        )

        val encoded = String(StudyFlowTvWireCodec.encodeResponse(response))
        assertNull(response.continuity)
        assertFalse(encoded.contains("currentDestination"))
        assertFalse(encoded.contains("activeSurfaceTitle"))
    }


    @Test
    fun domainActionResultAndPendingActionRoundTrip() {
        val action = StudyFlowTvDomainAction(
            actionId = "domain-1",
            studyPlanId = "plan-1",
            kind = StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW,
            contextId = "test-1",
            targetId = "question-1",
            reviewGrade = 3,
            requestedAtEpochMillis = 40_000L,
            origin = StudyFlowTvControlOrigin.LOCAL_REMOTE,
        )
        val result = StudyFlowTvDomainActionResult(
            actionId = action.actionId,
            ok = true,
            completedAtEpochMillis = 40_100L,
        )
        val request = StudyFlowTvWireRequest(
            requestId = "domain-result",
            operation = StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT,
            sessionId = "session-1",
            sessionSecret = "0123456789abcdef0123456789abcdef",
            domainActionResult = result,
            issuedAtEpochMillis = 40_100L,
        )
        val response = StudyFlowTvWireResponse(
            requestId = "domain-pending",
            ok = true,
            pendingDomainAction = action,
            lastDomainActionResult = result,
            respondedAtEpochMillis = 40_200L,
        )

        assertEquals(request, StudyFlowTvWireCodec.decodeRequest(StudyFlowTvWireCodec.encodeRequest(request)))
        assertEquals(response, StudyFlowTvWireCodec.decodeResponse(StudyFlowTvWireCodec.encodeResponse(response)))
    }

    @Test
    fun presentationReceiptRoundTripsForExactCastAcknowledgement() {
        val response = StudyFlowTvWireResponse(
            requestId = "presentation-ack",
            ok = true,
            applied = true,
            presentationReceipt = StudyFlowTvPresentationReceipt(
                snapshotId = "snapshot-42",
                generatedAtEpochMillis = 42_000L,
            ),
            respondedAtEpochMillis = 42_100L,
        )

        val decoded = StudyFlowTvWireCodec.decodeResponse(StudyFlowTvWireCodec.encodeResponse(response))

        assertEquals("snapshot-42", decoded.presentationReceipt?.snapshotId)
        assertEquals(42_000L, decoded.presentationReceipt?.generatedAtEpochMillis)
    }

}
