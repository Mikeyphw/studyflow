package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DesktopNativePlatformDeepLinkTest {
    @Test
    fun nativePortsDriveFileImportOpenNotificationsAndDeepLinksWithoutGui() {
        val root = Files.createTempDirectory("studyflow-dov23-workspace-")
        val outside = Files.createTempDirectory("studyflow-dov23-source-")
        try {
            val source = outside.resolve("native picker.txt")
            Files.writeString(source, "DOV23 native picker evidence")
            val fake = RecordingDesktopPlatformPorts(source.toString(), outside.resolve("export.studyplan").toString())
            val persistence = DesktopWorkspacePersistence.open(
                root = root,
                seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
            )
            val state = StudyFlowDesktopBootstrap.createAppState(
                platformPorts = fake,
                workspacePersistence = persistence,
            )

            state.chooseLibraryImportFile()
            assertEquals(source.toString(), state.libraryPathDraft)
            state.importLibraryFile()
            state.openSelectedMaterialExternally()
            assertTrue(fake.openedPaths.single().contains("library-files"))
            assertTrue(fake.notificationMessages.any { it.contains("Local file imported") })

            state.updateLibraryWebTitle("StudyFlow docs")
            state.updateLibraryWebUrl("https://example.com/studyflow")
            state.addLibraryWebResource()
            state.openSelectedMaterialExternally()
            assertEquals("https://example.com/studyflow", fake.openedUrls.single())

            state.choosePortabilityExportFile()
            assertTrue(state.portabilityPathDraft.endsWith("export.studyplan"))

            assertTrue(state.handleDeepLink("studyflow://route/library"))
            assertEquals(DesktopRoute.LIBRARY, state.selectedRoute)
            val planId = state.services.defaultPlanId!!.value
            assertTrue(state.handleDeepLink("studyflow://plan/$planId"))
            assertEquals(DesktopRoute.PLAN, state.selectedRoute)
        } finally {
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }
}

private class RecordingDesktopPlatformPorts(
    importPath: String,
    exportPath: String,
) : DesktopPlatformPorts {
    val openedPaths = mutableListOf<String>()
    val openedUrls = mutableListOf<String>()
    val notificationMessages = mutableListOf<String>()

    override val fileDialog: DesktopFileDialogPort = object : DesktopFileDialogPort {
        override fun chooseImportPath(): String = importPath
        override fun chooseExportPath(suggestedFileName: String): String = exportPath
    }

    override val filesystem: DesktopFilesystemPort = object : DesktopFilesystemPort {
        override fun openPath(path: String) { openedPaths += path }
        override fun openUrl(url: String) { openedUrls += url }
    }

    override val notifications: DesktopNotificationPort = object : DesktopNotificationPort {
        override fun showInfo(title: String, message: String) { notificationMessages += "$title: $message" }
    }
}
