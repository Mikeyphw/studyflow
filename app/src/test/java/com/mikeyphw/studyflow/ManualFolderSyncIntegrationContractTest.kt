package com.mikeyphw.studyflow

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFolderSyncIntegrationContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    private fun source(relative: String): String = repoRoot().resolve(relative).toFile().readText()

    @Test
    fun `manual folder sync is backed by real SAF archive io`() {
        val app = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
        val repository = source(
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt",
        )
        val storage = source(
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/SafManualFolderSyncStorage.kt",
        )
        val snapshot = source(
            "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDatabaseSnapshotWriter.kt",
        )

        assertTrue("uploadManualFolderCheckpoint(password)" in app)
        assertTrue("scanManualFolderCheckpoints()" in app)
        assertTrue("previewLatestManualFolderRestore(password)" in app)
        assertTrue("inspectSyncLedger()" in app)
        assertFalse("recordManualUploadCheckpoint" in app)
        assertFalse("recordManualRemoteCheck" in app)
        assertFalse("lastManualUploadAtEpochMillis == null) 0 else 1" in app)

        assertTrue("StudyFlowDatabaseSnapshotWriter.capture(database)" in repository)
        assertTrue("writeEncryptedArchive(archive, output, password)" in repository)
        assertTrue("listCheckpointDocuments(folderUri)" in repository)
        assertTrue("readEncryptedArchive(" in repository)

        assertTrue("DocumentsContract.buildChildDocumentsUriUsingTree" in storage)
        assertTrue("DocumentsContract.buildDocumentUriUsingTree" in storage)
        assertTrue("DocumentsContract.createDocument" in storage)
        assertTrue("resolver.openOutputStream" in storage)
        assertTrue("resolver.openInputStream" in storage)
        assertTrue("DocumentsContract.renameDocument" in storage)

        assertTrue("StudyFlowDatabase.SCHEMA_VERSION" in snapshot)
        assertTrue("database.withTransaction" in repository)
    }

    @Test
    fun `manual sync actions cannot silently default to fake handlers`() {
        val screen = source(
            "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt",
        )
        assertFalse("onUploadEncryptedCheckpoint: suspend (CharArray) -> Unit =" in screen)
        assertFalse("onCheckRemoteCheckpoints: suspend () -> Unit =" in screen)
        assertFalse("onPreviewRemoteRestore: suspend (CharArray) -> Unit =" in screen)
        assertFalse("onViewSyncHistory: suspend () -> Unit =" in screen)
    }
}
