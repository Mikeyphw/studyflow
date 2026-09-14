package com.mikeyphw.studyflow.core.tvcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOv14ReceiverBindingJournalKeysTest {
    @Test
    fun sessionCredentialBindsToTheSelectedReceiverEndpoint() {
        val selected = StudyFlowTvReceiverEndpoint("Living room TV", "fe80::1234%wlan0", 9123)
        val wrongLanPeer = StudyFlowTvReceiverEndpoint("Living room TV", "fe80::9999%wlan0", 9123)
        val wrongPort = StudyFlowTvReceiverEndpoint("Living room TV", "fe80::1234%wlan0", 9124)
        val credential = baseCredential().bindToReceiver(selected, fakePublicKey())

        assertTrue(selected.matchesCredentialBinding(credential))
        assertFalse(wrongLanPeer.matchesCredentialBinding(credential))
        assertFalse(wrongPort.matchesCredentialBinding(credential))
        assertEquals(fakePublicKey(), credential.receiverHostKeyAgreementPublicKeyHex)
    }

    @Test
    fun domainActionJournalKeysUseFullSha256InsteadOfSanitizedTruncation() {
        val first = "x".repeat(180) + ":A"
        val second = "x".repeat(180) + ":B"
        val punctuationA = "same/id"
        val punctuationB = "same_id"

        assertFalse(StudyFlowTvDomainActionJournalKeys.forAction(first) == StudyFlowTvDomainActionJournalKeys.forAction(second))
        assertFalse(StudyFlowTvDomainActionJournalKeys.forAction(punctuationA) == StudyFlowTvDomainActionJournalKeys.forAction(punctuationB))
        assertTrue(StudyFlowTvDomainActionJournalKeys.forAction(first).startsWith("action_sha256_"))
        assertTrue(StudyFlowTvDomainActionJournalKeys.sflpOperationId(second).startsWith("tv-action-sha256-"))
    }

    private fun baseCredential() = StudyFlowTvSessionCredential(
        sessionId = "session-1",
        sessionSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        tvDeviceName = "Living room TV",
        pairedDeviceName = "Phone",
        pairedAtEpochMillis = 100L,
        expiresAtEpochMillis = 10_000L,
    )

    private fun fakePublicKey(): String = "30".repeat(90)
}
