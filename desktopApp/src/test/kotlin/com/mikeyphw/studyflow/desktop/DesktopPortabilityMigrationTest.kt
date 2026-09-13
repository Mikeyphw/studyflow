package com.mikeyphw.studyflow.desktop

import java.nio.file.Files
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.junit.Assume.assumeTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DesktopPortabilityMigrationTest {
    @Test
    fun portableStructureAndEncryptedWorkspaceRestoreSurviveRestart() {
        val root = Files.createTempDirectory("studyflow-dov20-source-")
        val importRoot = Files.createTempDirectory("studyflow-dov20-import-")
        val outside = Files.createTempDirectory("studyflow-dov20-artifacts-")
        try {
            val source = services(root)
            val created = runBlocking { source.planContentController.createPlan("DOV20 portable plan") }
            source.planContentController.selectPlan(created.id)
            runBlocking { source.planContentController.createSubject("Portable subject") }

            val portable = outside.resolve("portable.studyplan")
            source.portabilityController.exportPortableStructure(portable)
            val preview = source.portabilityController.previewPortableStructure(portable)
            assertEquals("DOV20 portable plan", preview.planName)
            assertTrue(preview.rowCount >= 3)

            val imported = services(importRoot)
            val importedId = imported.portabilityController.importPortableStructure(portable)
            assertEquals(created.id, importedId)
            assertTrue(imported.planRepository.currentPlans().any { it.id == created.id })
            assertTrue(
                imported.contentRepository.currentSyllabus(created.id).subjects.any {
                    it.name == "Portable subject"
                },
            )

            val backup = outside.resolve("desktop-backup.sfbak")
            source.portabilityController.createEncryptedWorkspaceBackup(backup, "correct horse".toCharArray())
            val transient = runBlocking { source.planContentController.createPlan("Created after backup") }
            assertTrue(source.planRepository.currentPlans().any { it.id == transient.id })

            val restore = source.portabilityController.restoreEncryptedWorkspaceBackup(
                backup,
                "correct horse".toCharArray(),
            )
            assertTrue(restore.restartRequired)
            assertTrue(restore.safetySnapshotCreated)

            val reopened = services(root)
            assertTrue(reopened.planRepository.currentPlans().any { it.id == created.id })
            assertFalse(reopened.planRepository.currentPlans().any { it.id == transient.id })
        } finally {
            root.toFile().deleteRecursively()
            importRoot.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }


    @Test
    fun encryptedWorkspaceBackupRefusesWorkspaceSymlinkEscape() {
        val root = Files.createTempDirectory("studyflow-ov07-symlink-root-")
        val outside = Files.createTempDirectory("studyflow-ov07-symlink-outside-")
        try {
            val source = services(root)
            val external = outside.resolve("private.txt")
            Files.writeString(external, "outside workspace")
            val link = root.resolve("external-alias")
            val linked = runCatching { Files.createSymbolicLink(link, external) }.isSuccess
            assumeTrue("symbolic links are unavailable on this test platform", linked)

            assertFailsWith<IllegalArgumentException> {
                source.portabilityController.createEncryptedWorkspaceBackup(
                    outside.resolve("must-not-exist.sfbak"),
                    "correct horse".toCharArray(),
                )
            }
            assertFalse(Files.exists(outside.resolve("must-not-exist.sfbak")))
        } finally {
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun legacyWorkspaceSchemaCanBeRewrittenToCurrentVersion() {
        val root = Files.createTempDirectory("studyflow-dov20-migration-")
        try {
            services(root)
            val file = root.resolve("workspace-v1.properties")
            val legacy = Properties().apply { Files.newInputStream(file).use(::load) }
            legacy["schema.version"] = "3"
            legacy.keys
                .map { it.toString() }
                .filter { it.startsWith("session.") }
                .forEach { legacy.remove(it) }
            Files.newOutputStream(file).use { legacy.store(it, "DOV20 legacy fixture") }

            val migrated = services(root)
            assertEquals(
                DesktopWorkspacePersistence.CURRENT_SCHEMA_VERSION,
                migrated.portabilityController.migrateWorkspaceSchema(),
            )
            val rewritten = Properties().apply { Files.newInputStream(file).use(::load) }
            assertEquals(
                DesktopWorkspacePersistence.CURRENT_SCHEMA_VERSION.toString(),
                rewritten.getProperty("schema.version"),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun services(root: java.nio.file.Path): DesktopStudyFlowServices {
        val persistence = DesktopWorkspacePersistence.open(
            root = root,
            seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
        )
        return createDesktopStudyFlowServices(workspacePersistence = persistence)
    }
}
