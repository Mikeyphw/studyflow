package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.ThemeMode
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DesktopAppearancePersistenceTest {
    @Test
    fun systemLightAndDarkResolveAgainstSystemState() {
        assertTrue(resolveDesktopDarkTheme(ThemeMode.SYSTEM_DEFAULT, systemDarkTheme = true))
        assertFalse(resolveDesktopDarkTheme(ThemeMode.SYSTEM_DEFAULT, systemDarkTheme = false))
        assertFalse(resolveDesktopDarkTheme(ThemeMode.LIGHT, systemDarkTheme = true))
        assertTrue(resolveDesktopDarkTheme(ThemeMode.DARK, systemDarkTheme = false))
    }

    @Test
    fun selectedThemePersistsAcrossWorkspaceRestart() {
        val root = Files.createTempDirectory("studyflow-dov22-theme-")
        try {
            val first = services(root)
            first.setTheme(ThemeMode.DARK)

            val reopened = services(root)
            assertEquals(ThemeMode.DARK, reopened.preferencesStore.current().themeMode)
            assertTrue(resolveDesktopDarkTheme(reopened.preferencesStore.current().themeMode, systemDarkTheme = false))
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
