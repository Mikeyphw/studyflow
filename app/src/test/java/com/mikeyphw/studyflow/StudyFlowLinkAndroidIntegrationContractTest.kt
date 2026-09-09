package com.mikeyphw.studyflow

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowLinkAndroidIntegrationContractTest {
    private fun repoFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }

    @Test
    fun androidApplicationOwnsGenericRecoverableSflpPeer() {
        val application = repoFile(
            "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
        ).readText()
        assertTrue("val linkPeer: AndroidStudyFlowLinkPeer" in application)
        assertTrue("override val linkPeer = AndroidStudyFlowLinkPeer(application)" in application)
        assertTrue("action = \"sflp-peer-start\"" in application)
        assertTrue("linkPeer.start()" in application)
        assertTrue("local StudyFlow remains usable offline" in application)
    }

    @Test
    fun androidPeerUsesPersistentGenericIdentityTrustSyncAndDiscoveryInsteadOfPhoneTvRoles() {
        val peer = repoFile(
            "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt",
        ).readText()
        val persistence = repoFile(
            "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkStateStore.kt",
        ).readText()
        val nsd = repoFile(
            "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowNsdDiscovery.kt",
        ).readText()

        listOf(
            "StudyFlowDeviceIdentityManager",
            "StudyFlowLinkClientKind.ANDROID",
            "StudyFlowPeerRuntime",
            "StudyFlowLanBeaconDiscovery",
            "pairFromQr",
            "rememberManualEndpoint",
            "reconcile",
            "revoke",
            "forget",
        ).forEach { token -> assertTrue(token in peer) }
        listOf(
            "StudyFlowDeviceIdentityStore",
            "StudyFlowTrustStore",
            "StudyFlowOperationJournalStore",
            "StudyFlowOutgoingQueueStore",
            "StudyFlowPeerEndpointStore",
            "StudyFlowPeerPersistenceCodec",
        ).forEach { token -> assertTrue(token in persistence) }
        assertTrue("StudyFlowMdnsContract.ServiceType" in nsd)
        assertTrue("NsdManager.PROTOCOL_DNS_SD" in nsd)
        assertFalse("phoneDeviceName" in peer)
        assertFalse("StudyFlowTv" in peer)
    }
}
