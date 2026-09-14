package com.mikeyphw.studyflow.core.link

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOv10SecurityTest {
    @Test fun pairingGrantEncryptsCredentialAndSingleUseConsumptionIsAtomic() {
        val host = trust("host", StudyFlowLinkClientKind.DESKTOP)
        val peer = trust("peer", StudyFlowLinkClientKind.ANDROID)
        val authority = StudyFlowPairingAuthority(host)
        val client = StudyFlowPairingClient(peer)
        val offer = authority.createOffer(1_000, listOf("sflp+tcp://127.0.0.1:4711"))
        val code = requireNotNull(authority.pairingCode(offer.pairingId, 1_001))
        val request = client.requestFromCode(offer, code, 1_002)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val futures = List(2) { pool.submit<StudyFlowPairingResult> { start.await(); authority.accept(request, 1_003) } }
        start.countDown()
        val results = futures.map { it.get() }
        pool.shutdownNow()
        assertEquals(1, results.count { it is StudyFlowPairingResult.Accepted })
        assertEquals(1, results.count { it is StudyFlowPairingResult.Rejected })
        val grant = (results.single { it is StudyFlowPairingResult.Accepted } as StudyFlowPairingResult.Accepted).grant
        assertTrue(grant.encryptedTrustSecretHex.length >= 96)
        assertFalse(grant.encryptedTrustSecretHex.contains(host.trustedPeers().single().trustSecret!!.value))
        client.acceptGrant(grant, 1_004)
        assertEquals(host.trustedPeers().single().trustSecret, peer.trustedPeers().single().trustSecret)
        assertNull(authority.activeOffer(offer.pairingId, 1_005))
    }

    @Test fun qrClientRejectsExpiredOfferBeforeSendingCredentials() {
        val host = trust("host2", StudyFlowLinkClientKind.DESKTOP)
        val peer = trust("peer2", StudyFlowLinkClientKind.WEB)
        val offer = StudyFlowPairingAuthority(host).createOffer(10_000, listOf("wss://studyflow.local/sflp"), 10)
        val result = runCatching { StudyFlowPairingClient(peer).requestFromQr(offer.qrPayload, 10_011) }
        assertTrue(result.isFailure)
    }

    private fun trust(id:String, kind:StudyFlowLinkClientKind):StudyFlowTrustManager {
        val identity=StudyFlowLocalDeviceIdentity(StudyFlowLinkPeerDescriptor(StudyFlowDeviceId("device-$id"), id, kind, capabilities=listOf(StudyFlowLinkCapabilities.WorkspaceRead, StudyFlowLinkCapabilities.WorkspaceWrite, StudyFlowLinkCapabilities.WorkspaceSync, StudyFlowLinkCapabilities.DeviceControl)),1)
        return StudyFlowTrustManager(identity, InMemoryStudyFlowTrustStore())
    }
}
