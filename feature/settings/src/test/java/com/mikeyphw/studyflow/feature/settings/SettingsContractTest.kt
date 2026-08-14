package com.mikeyphw.studyflow.feature.settings

import com.mikeyphw.studyflow.core.model.ThemeMode
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsContractTest {
    @Test
    fun `theme values are stable domain identifiers`() {
        assertEquals("SYSTEM_DEFAULT", ThemeMode.SYSTEM_DEFAULT.name)
    }

    @Test
    fun `dynamic color switch uses one merged labeled toggle target`() {
        val source = readSource(
            modulePath = "src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
            rootPath = "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
        )
        assertTrue(source.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(source.contains("role = Role.Switch"))
        assertTrue(source.contains("onCheckedChange = null"))
    }

    @Test
    fun `normal settings hide release validation scaffolding`() {
        val source = readSource(
            modulePath = "src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
            rootPath = "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
        )

        assertTrue(source.contains("if (diagnosticsAvailable)"))
        assertFalse(source.contains("AppShellReadinessAuditor"))
        assertFalse(source.contains("ReleaseReadinessSeal"))
        assertFalse(source.contains("ReleaseCandidatePolishAuditor"))
        assertFalse(source.contains("UserExperiencePolishAuditor"))
        assertFalse(source.contains("app_shell_readiness_title"))
        assertFalse(source.contains("release_candidate_polish_title"))
        assertFalse(source.contains("final_seal_title"))
        assertFalse(source.contains("ux_polish_title"))
    }

    private fun readSource(modulePath: String, rootPath: String): String {
        val candidates: List<Path> = listOf(Paths.get(modulePath), Paths.get(rootPath))
        return candidates.first { path -> path.toFile().isFile }.toFile().readText()
    }
}
