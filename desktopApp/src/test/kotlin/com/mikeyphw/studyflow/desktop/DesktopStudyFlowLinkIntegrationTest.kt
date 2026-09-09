package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceIdentityManager
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowOperationId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerEndpointRecord
import com.mikeyphw.studyflow.core.link.StudyFlowTransportEndpoint
import com.mikeyphw.studyflow.core.link.StudyFlowTransportScheme
import com.mikeyphw.studyflow.core.link.StudyFlowTrustSecret
import com.mikeyphw.studyflow.core.link.StudyFlowTrustState
import com.mikeyphw.studyflow.core.link.StudyFlowTrustedPeerRecord
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.link.StudyFlowOperationJournal
import com.mikeyphw.studyflow.core.link.StudyFlowOutgoingOperationQueue
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopStudyFlowLinkIntegrationTest {
    @Test
    fun ordinaryDesktopBootstrapStaysSideEffectFreeWhileProductionFactoryOwnsLinkStartup() {
        val appState = StudyFlowDesktopBootstrap.createAppState()
        assertNull(appState.linkState())
        assertTrue(appState.shellState.allowedSharedModules.contains(":core:link"))
    }

    @Test
    fun desktopLinkPersistenceSurvivesRecreationForIdentityTrustEndpointsJournalAndQueue() {
        val root = Files.createTempDirectory("studyflow-link-desktop-test")
        try {
            val workspaceId = StudyFlowWorkspaceId("workspace-desktop-persist")
            val first = DesktopStudyFlowLinkPersistence(root)
            val identity = StudyFlowDeviceIdentityManager(first.identityStore).getOrCreate(
                displayName = "StudyFlow Desktop Test",
                clientKind = StudyFlowLinkClientKind.DESKTOP,
                capabilities = listOf(StudyFlowLinkCapabilities.WorkspaceSync),
                nowEpochMillis = 10L,
            )
            val remoteId = StudyFlowDeviceId("device-android-persist")
            first.trustStore.put(
                StudyFlowTrustedPeerRecord(
                    peer = StudyFlowLinkPeerDescriptor(
                        deviceId = remoteId,
                        displayName = "Android peer",
                        clientKind = StudyFlowLinkClientKind.ANDROID,
                        capabilities = listOf(StudyFlowLinkCapabilities.WorkspaceSync),
                    ),
                    state = StudyFlowTrustState.TRUSTED,
                    pairedAtEpochMillis = 11L,
                    updatedAtEpochMillis = 11L,
                    trustSecret = StudyFlowTrustSecret("00".repeat(32)),
                ),
            )
            first.endpointStore.put(
                StudyFlowPeerEndpointRecord(
                    deviceId = remoteId,
                    endpoints = listOf(
                        StudyFlowTransportEndpoint(StudyFlowTransportScheme.TCP, "192.0.2.10", 4545),
                    ),
                    updatedAtEpochMillis = 12L,
                ),
            )
            val journal = StudyFlowOperationJournal(workspaceId, identity.descriptor.deviceId, first.journalStore)
            val operation = journal.appendLocalMutation(
                operationId = StudyFlowOperationId("op-desktop-persist-1"),
                entityId = StudyFlowEntityId("goal-desktop-persist"),
                operationType = "goal.completed",
                occurredAtEpochMillis = 13L,
            )
            StudyFlowOutgoingOperationQueue(workspaceId, first.queueStore).enqueue(
                peerDeviceId = remoteId,
                operation = operation,
                nowEpochMillis = 14L,
            )

            val second = DesktopStudyFlowLinkPersistence(root)
            assertEquals(identity.descriptor.deviceId, second.identityStore.load()?.descriptor?.deviceId)
            assertEquals(remoteId, second.trustStore.list().single().peer.deviceId)
            assertEquals(1, second.endpointStore.list().single().endpoints.size)
            assertEquals(1, second.journalStore.entries(workspaceId).size)
            assertEquals(1, second.queueStore.load(workspaceId).size)
            assertNotNull(second.identityStore.load())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun desktopBoundaryAllowsLinkButStillRejectsAndroidFeatureAndPersistenceModules() {
        assertTrue(StudyFlowDesktopBoundary.isAllowedSharedDependency(":core:link"))
        assertFalse(StudyFlowDesktopBoundary.isAllowedSharedDependency(":app"))
        assertTrue(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":app"))
        assertTrue(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":feature:dashboard"))
        assertTrue(StudyFlowDesktopBoundary.isForbiddenProjectDependency(":core:database"))
    }
}
