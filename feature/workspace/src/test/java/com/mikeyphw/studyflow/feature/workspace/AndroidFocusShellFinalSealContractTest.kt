package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidFocusShellFinalSealContractTest {
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
    fun `focus shell owns navigation and active session presentation`() {
        val navigation = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
        val shell = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")

        assertFalse("NavigationSuiteScaffold" in navigation)
        assertFalse("NavigationBar(" in navigation)
        assertFalse("NavigationRail(" in navigation)
        assertTrue("observeActiveSessions()" in navigation)
        assertTrue("StudyFlowCompactDock(" in shell)
        assertTrue("StudyFlowExpandedRail(" in shell)
        assertTrue("StudyFlowActiveSessionBar(" in shell)
        assertTrue("studyflow-session-dock-morph" in shell)
        assertTrue("import androidx.compose.runtime.getValue" in shell)
        assertTrue("private fun StudyFlowExpandedSessionBar(" in shell)
    }

    @Test
    fun `legacy workspace Material wrappers stay retired`() {
        val components = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/" +
                "StudyFlowWorkspaceComponents.kt",
        )
        val common = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceCommon.kt",
        )

        assertFalse("StudyFlowPrimaryActionCard" in components)
        assertFalse("StudyFlowCoachCard" in components)
        assertFalse("StudyFlowWorkspaceTabs" in components)
        assertFalse("PrimaryScrollableTabRow(" in components)
        assertFalse("AssistChip(" in components)
        assertFalse("ElevatedCard(" in components)
        assertFalse("WorkspaceUtilityScaffold" in common)
        assertFalse("Scaffold(" in common)
        assertTrue("WorkspaceUtilityPane(" in common)
    }

    @Test
    fun `motion honors accessibility and immersive focus uses predictive back`() {
        val motion = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowMotion.kt",
        )
        val focus = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/FocusSessionScreen.kt",
        )
        val quickAdd = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceUtilityScreens.kt",
        )
        val components = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/" +
                "StudyFlowWorkspaceComponents.kt",
        )

        assertTrue("ANIMATOR_DURATION_SCALE" in motion)
        assertTrue("snap()" in motion)
        assertTrue("PredictiveBackHandler(" in focus)
        assertFalse("TopAppBar(" in focus)
        assertFalse("Scaffold(" in focus)
        assertTrue("AnimatedVisibility(" in quickAdd)
        assertTrue("slideInVertically(" in quickAdd)
        assertTrue("studyflow-plan-switcher-expansion" in components)
    }

    @Test
    fun `StudyFlow palette is default while dynamic color remains optional`() {
        val preferences = source("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/AppPreferences.kt")
        val repository = source(
            "core/i18n/src/main/java/com/mikeyphw/studyflow/core/i18n/" +
                "DataStoreAppPreferencesRepository.kt",
        )
        val theme = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowTheme.kt",
        )

        assertTrue("dynamicColorEnabled: Boolean = false" in preferences)
        assertTrue("?: false" in repository)
        assertTrue("dynamicColorEnabled && Build.VERSION.SDK_INT" in theme)
    }
}
