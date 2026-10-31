package com.mikeyphw.studyflow

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class ManifestSecuritySourceContractTest {
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
    fun manifestAndBackupRulesAreFailClosed() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue("android:allowBackup=\"false\"" in manifest)
        assertTrue("@xml/backup_rules" in manifest)
        assertTrue("@xml/data_extraction_rules" in manifest)
        // Package visibility is permitted only for the explicit Termux command integration.
        // Do not accept arbitrary package queries or broad implicit intent visibility.
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(repoFile("app/src/main/AndroidManifest.xml"))
        val queryNodes = document.getElementsByTagName("queries")
        assertEquals(1, queryNodes.length)
        val queryChildren = queryNodes.item(0).childNodes.let { children ->
            (0 until children.length).mapNotNull { children.item(it) as? Element }
        }
        assertEquals(listOf("package", "intent"), queryChildren.map { it.tagName })
        val androidNs = "http://schemas.android.com/apk/res/android"
        assertEquals("com.termux", queryChildren[0].getAttributeNS(androidNs, "name"))
        val intentChildren = queryChildren[1].childNodes.let { children ->
            (0 until children.length).mapNotNull { children.item(it) as? Element }
        }
        assertEquals(listOf("action"), intentChildren.map { it.tagName })
        assertEquals("com.termux.RUN_COMMAND", intentChildren[0].getAttributeNS(androidNs, "name"))
        assertTrue(Regex("StudyReminderLaunchActivity[\\s\\S]*?android:exported=\\\"false\\\"")
            .containsMatchIn(manifest))

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
