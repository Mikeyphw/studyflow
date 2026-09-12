package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryState
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowInMemoryAttentionLedgerStorage
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import java.nio.file.Files
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopAttentionRuntimeTest {
    @Test
    fun `test notification uses rich desktop port`() {
        val root = Files.createTempDirectory("studyflow-att04-runtime")
        val persistence = DesktopWorkspacePersistence.open(
            root = root,
            seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
        )
        val notifications = RecordingNotificationPort()
        val ports = RecordingDesktopPorts(notifications)
        val services = createDesktopStudyFlowServices(
            platformPorts = ports,
            workspacePersistence = persistence,
        )
        val runtime = assertNotNull(services.attentionRuntime)
        runtime.showTestNotification()
        assertEquals("attention-test", notifications.lastNotification?.id)
        assertTrue(notifications.lastNotification?.message.orEmpty().contains("while StudyFlow is running"))
        runtime.close()
    }

    @Test
    fun `startup reconciliation records recovery and tray dashboard`() {
        val root = Files.createTempDirectory("studyflow-att04-startup")
        val persistence = DesktopWorkspacePersistence.open(
            root = root,
            seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
        )
        val notifications = RecordingNotificationPort()
        val appState = StudyFlowDesktopBootstrap.createAppState(
            platformPorts = RecordingDesktopPorts(notifications),
            workspacePersistence = persistence,
        )
        val snapshot = assertNotNull(appState.services.attentionRuntime).snapshot()
        assertTrue(snapshot.running)
        assertTrue(snapshot.recoveryCount >= 1)
        assertNotNull(notifications.lastTray)
        assertTrue(snapshot.reliabilityEvidence.platforms.isNotEmpty())
        assertEquals("desktop", snapshot.reliabilityEvidence.platforms.single().clientKind.name.lowercase())
        appState.close()
    }
    @Test
    fun `unsupported native surface never pretends a reminder was delivered`() {
        val root = Files.createTempDirectory("studyflow-att04-unsupported")
        val notifications = RecordingNotificationPort(nativeSupported = false)
        val services = createDesktopStudyFlowServices(platformPorts = RecordingDesktopPorts(notifications))
        val ledger = StudyFlowAttentionLedger(StudyFlowInMemoryAttentionLedgerStorage())
        val occurrence = StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId("att04-test-due"),
            workspaceId = StudyFlowWorkspaceId("workspace-att04"),
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            scheduledAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 10_000_000L,
            payload = JsonObject(mapOf("title" to JsonPrimitive("Due reminder"), "body" to JsonPrimitive("Open StudyFlow"))),
        )
        ledger.upsertScheduled(occurrence, 500L)
        val runtime = DesktopAttentionRuntime(
            services = services,
            preferences = DesktopAttentionPreferences.open(root),
            attentionLedger = ledger,
            clock = { 2_000L },
        )
        runtime.reconcileNow()
        assertEquals(StudyFlowAttentionDeliveryState.SCHEDULED, ledger.entry(occurrence.occurrenceId)?.state)
        runtime.close()
    }

}

private class RecordingDesktopPorts(
    override val notifications: DesktopNotificationPort,
) : DesktopPlatformPorts {
    override val fileDialog: DesktopFileDialogPort = object : DesktopFileDialogPort {
        override fun chooseImportPath(): String? = null
        override fun chooseExportPath(suggestedFileName: String): String? = null
    }
    override val filesystem: DesktopFilesystemPort = NoOpDesktopFilesystemPort
}

private class RecordingNotificationPort(private val nativeSupported: Boolean = true) : DesktopNotificationPort {
    var lastNotification: DesktopNotification? = null
    var lastTray: DesktopTrayDashboard? = null
    var handler: ((DesktopAttentionSurfaceAction, String?) -> Unit)? = null

    override fun showInfo(title: String, message: String) = Unit
    override fun capabilities(): DesktopNotificationCapabilities = DesktopNotificationCapabilities(
        nativeNotificationsSupported = nativeSupported,
        traySupported = nativeSupported,
        actionButtonsSupported = false,
        clickRoutingSupported = true,
    )
    override fun show(notification: DesktopNotification) { lastNotification = notification }
    override fun updateTray(dashboard: DesktopTrayDashboard?) { lastTray = dashboard }
    override fun setActionHandler(handler: (DesktopAttentionSurfaceAction, String?) -> Unit) { this.handler = handler }
}
