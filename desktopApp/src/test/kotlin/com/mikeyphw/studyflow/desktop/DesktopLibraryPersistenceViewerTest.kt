package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLibraryPersistenceViewerTest {
    @Test
    fun importedLocalFileAndViewerPersistAcrossRestart() {
        runBlocking {
            val root = Files.createTempDirectory("studyflow-dov15-")
            val source = Files.createTempFile("studyflow material ", ".txt")
            Files.writeString(source, "DOV15 persistent local material viewer")
            val sample = DesktopStudyFlowSampleData.create()

            val firstWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val first = createDesktopStudyFlowServices(workspacePersistence = firstWorkspace)
            val imported = first.libraryWorkspaceController.importLocalFile(source.toString())
            val firstViewer = first.libraryWorkspaceController.viewerState(imported.id)

            assertTrue(imported.sourceUri.orEmpty().startsWith("content://studyflow-desktop/"))
            assertEquals(DesktopMaterialViewerKind.TEXT, firstViewer.kind)
            assertTrue(firstViewer.content.contains("DOV15 persistent local material viewer"))

            val secondWorkspace = DesktopWorkspacePersistence.open(root, DesktopWorkspaceSnapshot.seed(sample))
            val second = createDesktopStudyFlowServices(workspacePersistence = secondWorkspace)
            val restored = second.libraryRepository.currentMaterials(second.defaultPlanId!!)
                .first { material -> material.id == imported.id }
            val secondViewer = second.libraryWorkspaceController.viewerState(restored.id)

            assertEquals(imported.sourceUri, restored.sourceUri)
            assertEquals(DesktopMaterialViewerKind.TEXT, secondViewer.kind)
            assertTrue(secondViewer.content.contains("DOV15 persistent local material viewer"))
            assertTrue(Files.isRegularFile(root.resolve("library-v1.properties")))
            val importedDirectory = root.resolve("library-files").resolve(imported.id.value)
            assertTrue(Files.isDirectory(importedDirectory))

            second.planContentController.trashSelected()
            second.planContentController.deleteSelectedPermanently()
            assertTrue(!Files.exists(importedDirectory))
        }
    }
}
