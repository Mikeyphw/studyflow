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
    fun pairingOfferIsSixDigitShortLivedAndEncryptsExpiringCredential() {
        val engine = StudyFlowTvPairingEngine()
        val offer = engine.createOffer("Living Room TV", 10_000L)
        val clientKey = StudyFlowTvPairingCrypto.createClientKeyMaterial()

        assertEquals(6, offer.token.pairingCode.length)
        assertTrue(offer.token.pairingCode.all(Char::isDigit))
        assertEquals(130_000L, offer.token.expiresAtEpochMillis)
        assertTrue(offer.deepLink.startsWith("studyflow://tv/pair?"))
        assertNull(engine.accept(offer, "000000", offer.token.sessionNonce, "Pixel", clientKey.publicKeyX509Hex, 11_000L))

        val accepted = engine.accept(
            offer = offer,
            pairingCode = offer.token.pairingCode,
            sessionNonce = offer.token.sessionNonce,
            phoneDeviceName = "Pixel",
            clientKeyAgreementPublicKeyHex = clientKey.publicKeyX509Hex,
            nowEpochMillis = 11_000L,
        )
        assertNotNull(accepted)
        accepted!!
        val decrypted = StudyFlowTvPairingCrypto.decryptCredential(accepted.encryptedCredential, offer.token.sessionNonce, clientKey)
        assertEquals(accepted.credential, decrypted)
        val wire = String(StudyFlowTvWireCodec.encodeResponse(StudyFlowTvWireResponse(
            requestId = "pair",
            ok = true,
            encryptedCredential = accepted.encryptedCredential,
            respondedAtEpochMillis = 11_000L,
        )))
        assertFalse(wire.contains(accepted.credential.sessionSecret))
        assertFalse(wire.contains("sessionSecret"))
        assertEquals(StudyFlowTvPairingState.PAIRED, decrypted.toControlSession().pairingState)
    }

    @Test
    fun authenticatedWireRequestUsesReplaySafeProofInsteadOfSessionSecret() {
        val credential = StudyFlowTvSessionCredential(
            sessionId = "session-1",
            sessionSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            tvDeviceName = "TV",
            pairedDeviceName = "Phone",
            pairedAtEpochMillis = 19_000L,
            expiresAtEpochMillis = 99_000L,
        )
        val unsigned = StudyFlowTvWireRequest(
            requestId = "request-1",
            operation = StudyFlowTvWireOperation.COMMAND,
            sessionId = credential.sessionId,
            command = StudyFlowTvControlCommand(
                commandId = "command-1",
                surface = StudyFlowTvSurface.PROGRESS,
                action = StudyFlowTvControlAction.OPEN,
                issuedAtEpochMillis = 20_000L,
            ),
            issuedAtEpochMillis = 20_000L,
        )
        val request = StudyFlowTvWireAuthenticator.sign(unsigned, credential)
        assertTrue(StudyFlowTvWireAuthenticator.verify(request, credential, 20_001L))
        assertFalse(StudyFlowTvWireAuthenticator.verify(request.copy(requestId = "tampered"), credential, 20_001L))
        val payload = String(StudyFlowTvWireCodec.encodeRequest(request))
        assertFalse(payload.contains("sessionSecret"))
        assertFalse(payload.contains(credential.sessionSecret))
        assertTrue(payload.contains("authProofHex"))
        assertTrue(StudyFlowTvWireProtocol.unifiedPlaneEnvelopeRequired)

        val output = ByteArrayOutputStream()
        StudyFlowTvWireCodec.writeRequest(output, request)
        assertEquals(request, StudyFlowTvWireCodec.readRequest(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun pairingInfoDoesNotExposePairingCodeOrLongTermCredential() {
        val clientKey = StudyFlowTvPairingCrypto.createClientKeyMaterial()
        val info = StudyFlowTvPairingInfo(
            tvDeviceName = "Office TV",
            pairingActive = true,
            sessionNonce = "0123456789abcdef",
            expiresAtEpochMillis = 99_000L,
            hostKeyAgreementPublicKeyHex = clientKey.publicKeyX509Hex,
        )
        val response = StudyFlowTvWireResponse(
            requestId = "request-info",
            ok = true,
            pairingInfo = info,
            respondedAtEpochMillis = 1_000L,
        )
        val encoded = String(StudyFlowTvWireCodec.encodeResponse(response))
        assertFalse(encoded.contains("pairingCode"))
        assertFalse(encoded.contains("sessionSecret"))
        assertTrue(encoded.contains("sessionNonce"))
        assertEquals(response, StudyFlowTvWireCodec.decodeResponse(StudyFlowTvWireCodec.encodeResponse(response)))
    }

    @Test
    fun unauthenticatedPairingMetadataCarriesNoContinuityState() {
        val clientKey = StudyFlowTvPairingCrypto.createClientKeyMaterial()
        val response = StudyFlowTvWireResponse(
            requestId = "pairing-metadata",
            ok = true,
            pairingInfo = StudyFlowTvPairingInfo(
                tvDeviceName = "Living Room TV",
                pairingActive = true,
                sessionNonce = "0123456789abcdef",
                expiresAtEpochMillis = 90_000L,
                hostKeyAgreementPublicKeyHex = clientKey.publicKeyX509Hex,
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
        val result = StudyFlowTvDomainActionResult(actionId = action.actionId, ok = true, completedAtEpochMillis = 40_100L)
        val request = StudyFlowTvWireRequest(
            requestId = "domain-result",
            operation = StudyFlowTvWireOperation.DOMAIN_ACTION_RESULT,
            sessionId = "session-1",
            authNonceHex = "ab".repeat(16),
            authProofHex = "cd".repeat(32),
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
            presentationReceipt = StudyFlowTvPresentationReceipt(snapshotId = "snapshot-42", generatedAtEpochMillis = 42_000L),
            respondedAtEpochMillis = 42_100L,
        )
        val decoded = StudyFlowTvWireCodec.decodeResponse(StudyFlowTvWireCodec.encodeResponse(response))
        assertEquals("snapshot-42", decoded.presentationReceipt?.snapshotId)
        assertEquals(42_000L, decoded.presentationReceipt?.generatedAtEpochMillis)
    }
}
