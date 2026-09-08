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
        assertFalse(engine.isAuthorized(credential, credential.sessionId, credential.sessionSecret, credential.expiresAtEpochMillis))
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

}
