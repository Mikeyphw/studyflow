package com.mikeyphw.studyflow

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowOv13AndroidPeerLifecycleContractTest {
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
    fun applicationNoLongerStartsGenericSflpSocketsBeforeUserEnablement() {
        val application = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").readText()
        assertTrue("linkPeer.startIfUserEnabled()" in application)
        assertTrue("Application startup must not call linkPeer.start() automatically" in application)
        assertFalse("runCatching { linkPeer.start() }" in application)
    }

    @Test
    fun androidPeerPersistsUserOptInAndGatesLocalNetworkPermissionBeforeSockets() {
        val peer = repoFile("app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt").readText()
        assertTrue("AndroidStudyFlowLocalNetworkPermission" in peer)
        assertTrue("studyflow_sflp_peer_lifecycle" in peer)
        assertTrue("enableLocalPeer" in peer)
        assertTrue("disableLocalPeer" in peer)
        assertTrue("startIfUserEnabled" in peer)
        assertTrue("Local SFLP peer must be enabled by the user before sockets start" in peer)
        assertTrue("Local network permission is required before SFLP sockets start" in peer)
        assertFalse("sflp+tcp://127.0.0.1" in peer)
    }

    @Test
    fun discoverySourcesAreReattachedAndRestartableAfterCloseOrNsdFailure() {
        val peer = repoFile("app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowLinkPeer.kt").readText()
        val nsd = repoFile("app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowNsdDiscovery.kt").readText()
        val tv = repoFile("core/tvlink/src/main/kotlin/com/mikeyphw/studyflow/core/tvlink/StudyFlowTvLocalLink.kt").readText()

        assertTrue("discoveryRegistry.add(nsdDiscovery)" in peer)
        assertTrue("pendingResolveQueue" in nsd)
        assertTrue("scheduleRestartAfterFailure" in nsd)
        assertTrue("desiredStarted" in nsd)
        assertTrue("lastFailureReason" in nsd)
        assertTrue("pendingResolveQueue" in tv)
        assertTrue("desiredDiscovering" in tv)
        assertTrue("scheduleRestartAfterFailure" in tv)
    }

    @Test
    fun failedSafSyncFolderConfigurationReleasesPersistedGrant() {
        val app = repoFile("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").readText()
        assertTrue("syncFolderGrantPersisted" in app)
        assertTrue("releasePersistableUriPermission(uri, permissionFlags)" in app)
    }
}
