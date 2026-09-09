package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowDeviceIdentityTrustTest {
    @Test
    fun identityPersistsAcrossRefreshAndUpdatesDescriptorWithoutChangingDeviceId() {
        val store = InMemoryStudyFlowDeviceIdentityStore()
        var nextId = 0
        val manager = StudyFlowDeviceIdentityManager(store) {
            nextId += 1
            StudyFlowDeviceId("device-test-$nextId")
        }
        val first = manager.getOrCreate(
            displayName = "Tablet",
            clientKind = StudyFlowLinkClientKind.ANDROID,
            capabilities = listOf(StudyFlowLinkCapabilities.WorkspaceRead),
            nowEpochMillis = 100,
        )
        val second = manager.getOrCreate(
            displayName = "Tablet renamed",
            clientKind = StudyFlowLinkClientKind.ANDROID,
            capabilities = listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.WorkspaceSync,
            ),
            nowEpochMillis = 200,
        )
        assertEquals(first.descriptor.deviceId, second.descriptor.deviceId)
        assertEquals(first.createdAtEpochMillis, second.createdAtEpochMillis)
        assertEquals("Tablet renamed", second.descriptor.displayName)
        assertEquals(1, nextId)
    }

    @Test
    fun qrAndPairingCodeFlowsCreateReciprocalRememberedTrust() {
        val host = fixtureTrustManager("host", "Desktop", StudyFlowLinkClientKind.DESKTOP)
        val peer = fixtureTrustManager("peer", "Web", StudyFlowLinkClientKind.WEB)
        val authority = StudyFlowPairingAuthority(host)
        val client = StudyFlowPairingClient(peer)
        val offer = authority.createOffer(
            nowEpochMillis = 1_000,
            endpointHints = listOf("sflp+tcp://192.168.1.5:4711", "ws://192.168.1.5:4712/link"),
        )
        val visibleCode = requireNotNull(authority.pairingCode(offer.pairingId, 1_001))
        val decodedQr = StudyFlowPairingQrCodec.decode(offer.qrPayload)
        assertEquals(visibleCode, decodedQr.pairingCode)
        assertEquals(offer.endpointHints, decodedQr.endpointHints)

        val request = client.requestFromQr(offer.qrPayload, nowEpochMillis = 1_010)
        val accepted = authority.accept(request, nowEpochMillis = 1_020)
        assertTrue(accepted is StudyFlowPairingResult.Accepted)
        val grant = (accepted as StudyFlowPairingResult.Accepted).grant
        val peerView = client.acceptGrant(grant, nowEpochMillis = 1_021)

        assertEquals(1, host.trustedPeers().size)
        assertEquals(1, peer.trustedPeers().size)
        assertEquals(peer.localIdentity.descriptor.deviceId, host.trustedPeers().single().peer.deviceId)
        assertEquals(host.localIdentity.descriptor.deviceId, peerView.peer.deviceId)
        assertEquals(host.trustedPeers().single().trustSecret, peer.trustedPeers().single().trustSecret)
        assertNull(authority.pairingCode(offer.pairingId, 1_022))
    }

    @Test
    fun pairingOfferExpiresAndTenFailedAttemptsInvalidateIt() {
        val host = fixtureTrustManager("host", "Desktop", StudyFlowLinkClientKind.DESKTOP)
        val peer = fixtureTrustManager("peer", "Web", StudyFlowLinkClientKind.WEB)
        val authority = StudyFlowPairingAuthority(host)
        val client = StudyFlowPairingClient(peer)

        val expiring = authority.createOffer(nowEpochMillis = 10_000, lifetimeMillis = 5)
        val expiredRequest = client.requestFromCode(expiring, "000000", nowEpochMillis = 10_006)
        val expired = authority.accept(expiredRequest, nowEpochMillis = 10_006)
        assertEquals(
            StudyFlowPairingRejection.EXPIRED,
            (expired as StudyFlowPairingResult.Rejected).reason,
        )

        val offer = authority.createOffer(nowEpochMillis = 20_000)
        repeat(StudyFlowTrustManager.DefaultPairingAttemptLimit - 1) { attempt ->
            val request = client.requestFromCode(offer, "999999", nowEpochMillis = 20_001L + attempt)
            val result = authority.accept(request, nowEpochMillis = 20_001L + attempt)
            assertTrue(result is StudyFlowPairingResult.Rejected)
        }
        val finalRequest = client.requestFromCode(offer, "999999", nowEpochMillis = 20_020)
        val finalResult = authority.accept(finalRequest, nowEpochMillis = 20_020)
        assertEquals(
            StudyFlowPairingRejection.ATTEMPT_LIMIT,
            (finalResult as StudyFlowPairingResult.Rejected).reason,
        )
        assertNull(authority.activeOffer(offer.pairingId, 20_021))
    }

    @Test
    fun reconnectProofIsOneTimeAndRevocationPreventsFutureReconnects() {
        val host = fixtureTrustManager("host", "Desktop", StudyFlowLinkClientKind.DESKTOP)
        val peer = fixtureTrustManager("peer", "Android", StudyFlowLinkClientKind.ANDROID)
        pair(host, peer, now = 50_000)

        val challenge = host.issueReconnectChallenge(
            peerDeviceId = peer.localIdentity.descriptor.deviceId,
            nowEpochMillis = 60_000,
        )
        val proof = peer.createReconnectProof(challenge, nowEpochMillis = 60_001)
        assertTrue(host.verifyReconnectProof(proof, nowEpochMillis = 60_002))
        assertFalse(host.verifyReconnectProof(proof, nowEpochMillis = 60_003))

        val oldSecret = host.trustedPeers().single().trustSecret
        val revoked = host.revoke(peer.localIdentity.descriptor.deviceId, nowEpochMillis = 61_000)
        assertNotNull(revoked)
        assertEquals(StudyFlowTrustState.REVOKED, revoked?.state)
        assertNull(revoked?.trustSecret)
        assertEquals(oldSecret, peer.trustedPeers().single().trustSecret)
        assertTrue(host.trustedPeers().isEmpty())
        assertEquals(1, host.rememberedPeers().size)
        assertTrue(
            runCatching {
                host.issueReconnectChallenge(peer.localIdentity.descriptor.deviceId, 61_001)
            }.isFailure,
        )
    }

    @Test
    fun freshPairingReplacesRevokedTrustWithANewSecret() {
        val host = fixtureTrustManager("host", "Desktop", StudyFlowLinkClientKind.DESKTOP)
        val peer = fixtureTrustManager("peer", "Web", StudyFlowLinkClientKind.WEB)
        pair(host, peer, now = 100_000)
        val firstSecret = host.trustedPeers().single().trustSecret
        host.revoke(peer.localIdentity.descriptor.deviceId, 101_000)
        pair(host, peer, now = 102_000)
        val secondSecret = host.trustedPeers().single().trustSecret
        assertNotEquals(firstSecret, secondSecret)
        assertEquals(StudyFlowTrustState.TRUSTED, host.rememberedPeers().single().state)
    }

    private fun fixtureTrustManager(
        id: String,
        name: String,
        kind: StudyFlowLinkClientKind,
    ): StudyFlowTrustManager {
        val identity = StudyFlowLocalDeviceIdentity(
            descriptor = StudyFlowLinkPeerDescriptor(
                deviceId = StudyFlowDeviceId("device-$id"),
                displayName = name,
                clientKind = kind,
                capabilities = listOf(
                    StudyFlowLinkCapabilities.WorkspaceRead,
                    StudyFlowLinkCapabilities.WorkspaceSync,
                ),
            ),
            createdAtEpochMillis = 1,
        )
        return StudyFlowTrustManager(identity, InMemoryStudyFlowTrustStore())
    }

    private fun pair(
        host: StudyFlowTrustManager,
        peer: StudyFlowTrustManager,
        now: Long,
    ) {
        val authority = StudyFlowPairingAuthority(host)
        val client = StudyFlowPairingClient(peer)
        val offer = authority.createOffer(now)
        val code = requireNotNull(authority.pairingCode(offer.pairingId, now + 1))
        val request = client.requestFromCode(offer, code, now + 2)
        val accepted = authority.accept(request, now + 3) as StudyFlowPairingResult.Accepted
        client.acceptGrant(accepted.grant, now + 4)
    }
}
