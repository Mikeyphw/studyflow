package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerRetryState
import com.mikeyphw.studyflow.core.link.StudyFlowPersistedStateKind
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopStudyFlowOv11PersistenceRecoveryTest {
    @Test
    fun corruptIdentityIsQuarantinedAndRecordedInsteadOfSilentlyReset() {
        val root = Files.createTempDirectory("studyflow-ov11-corruption")
        try {
            Files.writeString(root.resolve("identity.json"), "{ definitely-not-valid-json")
            val persistence = DesktopStudyFlowLinkPersistence(root)

            assertNull(persistence.identityStore.load())
            val records = persistence.corruptionRecords()
            assertEquals(1, records.size)
            assertEquals(StudyFlowPersistedStateKind.IDENTITY, records.single().stateKind)
            assertEquals("identity.json", records.single().storageKey)
            assertTrue(records.single().encodedSha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(Files.exists(root.resolve(records.single().quarantineReference)))
            assertTrue(!Files.exists(root.resolve("identity.json")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun peerBackoffPersistsAcrossDesktopPersistenceRecreation() {
        val root = Files.createTempDirectory("studyflow-ov11-backoff")
        val workspace = StudyFlowWorkspaceId("workspace-ov11-desktop")
        val peer = StudyFlowDeviceId("device-ov11-remote")
        try {
            val first = DesktopStudyFlowLinkPersistence(root)
            val state = StudyFlowPeerRetryState(workspace, peer, 2, 1_000L, 3_000L, "offline")
            first.queueStore.savePeerRetryStates(workspace, listOf(state))

            val second = DesktopStudyFlowLinkPersistence(root)
            assertEquals(listOf(state), second.queueStore.loadPeerRetryStates(workspace))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
