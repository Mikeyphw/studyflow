package com.mikeyphw.studyflow

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestSecuritySourceContractTest {
    private fun repoFile(path: String): File {
        var current = File(System.getProperty("user.dir"))
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }

    @Test
    fun manifestAndBackupRulesAreFailClosed() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue("android:allowBackup=\"false\"" in manifest)
        assertTrue("@xml/backup_rules" in manifest)
        assertTrue("@xml/data_extraction_rules" in manifest)
        assertFalse("<queries" in manifest)
        assertTrue(Regex("StudyReminderLaunchActivity[\\s\\S]*?android:exported=\\\"false\\\"").containsMatchIn(manifest))

        val legacy = repoFile("app/src/main/res/xml/backup_rules.xml").readText()
        val modern = repoFile("app/src/main/res/xml/data_extraction_rules.xml").readText()
        listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
            assertTrue("domain=\"$domain\"" in legacy)
            assertTrue("domain=\"$domain\"" in modern)
        }
    }

    @Test
    fun fileProviderRootsStayNarrow() {
        val paths = repoFile("app/src/main/res/xml/file_provider_paths.xml").readText()
        assertFalse("path=\".\"" in paths)
        assertFalse("external-path" in paths)
        assertTrue("shareable-annexes/" in paths)
        assertTrue("support-bundles/" in paths)
        assertTrue("exam-reports/" in paths)
    }
}
