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

    @Test
    fun `settings copy exposes optional cloud sync as off by default`() {
        val source = readSource(
            modulePath = "src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
            rootPath = "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
        )
        val strings = readSource(
            modulePath = "src/main/res/values/strings.xml",
            rootPath = "feature/settings/src/main/res/values/strings.xml",
        )

        assertTrue(source.contains("syncState.settings.providerId"))
        assertTrue(strings.contains("Optional cloud sync"))
        assertTrue(strings.contains("Cloud sync is optional, off by default"))
        assertTrue(strings.contains("provider: %2$s"))
        assertFalse(strings.contains("No cloud"))
        assertFalse(strings.contains("network sync"))
    }

    @Test
    fun `optional sync onboarding keeps offline as first class path`() {
        val source = readSource(
            modulePath = "src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt",
            rootPath = "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt",
        )
        val strings = readSource(
            modulePath = "src/main/res/values/strings.xml",
            rootPath = "feature/settings/src/main/res/values/strings.xml",
        )

        assertTrue(source.contains("OptionalSyncOnboardingCard"))
        assertTrue(source.contains("onKeepOffline"))
        assertTrue(source.contains("onChooseSyncFolder"))
        assertTrue(source.contains("onLearnMore"))
        assertTrue(strings.contains("Keep offline"))
        assertTrue(strings.contains("Choose sync folder"))
        assertTrue(strings.contains("StudyFlow works fully offline"))
        assertFalse(strings.contains("Cloud required"))
    }

    private fun readSource(modulePath: String, rootPath: String): String {
        val candidates: List<Path> = listOf(Paths.get(modulePath), Paths.get(rootPath))
        return candidates.first { path -> path.toFile().isFile }.toFile().readText()
    }
}
