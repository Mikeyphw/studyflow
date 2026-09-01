package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkShellPolishContractTest {
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
    fun `dark theme exposes readable StudyFlow content colors`() {
        val theme = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowTheme.kt",
        )
        val shell = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")

        assertTrue("onBackground = Color(0xFFE7EAF3)" in theme)
        assertTrue("onSurface = Color(0xFFE7EAF3)" in theme)
        assertTrue("onSurfaceVariant = Color(0xFFC5CAD7)" in theme)
        assertTrue("contentColor = MaterialTheme.colorScheme.onBackground" in shell)
        assertFalse("Color.Black" in theme)
        assertFalse("Color.DarkGray" in theme)
    }

    @Test
    fun `shell owns status bar inset and compact dock density`() {
        val shell = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")
        val shellPrimitives = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowShellPrimitives.kt",
        )

        assertTrue("WindowInsets.statusBars" in shell)
        assertTrue("windowInsetsPadding(WindowInsets.statusBars)" in shell)
        assertTrue("val CompactDockHeight: Dp = 64.dp" in shellPrimitives)
    }

    @Test
    fun `workspace floating actions use StudyFlow action orbs`() {
        val focusPrimitives = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/" +
                "StudyFlowFocusWorkspacePrimitives.kt",
        )
        val today = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        )
        val practice = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
        )

        assertTrue("fun StudyFlowActionOrb(" in focusPrimitives)
        assertTrue("secondaryContainer" in focusPrimitives)
        assertTrue("onSecondaryContainer" in focusPrimitives)
        assertTrue("StudyFlowActionOrb(" in today)
        assertTrue("StudyFlowActionOrb(" in practice)
        assertFalse("FloatingActionButton(" in today)
        assertFalse("FloatingActionButton(" in practice)
    }

    @Test
    fun `snapshot UI regressions stay fixed`() {
        val common = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceCommon.kt",
        )
        val today = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        )
        val utility = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceUtilityScreens.kt",
        )
        val plans = source(
            "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
        )
        val sync = source(
            "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt",
        )

        assertTrue("contentColor = MaterialTheme.colorScheme.onBackground" in common)
        assertTrue("workspaceContentPadding(bottom = 180.dp)" in today)
        assertTrue("bottom = 132.dp" in plans)
        assertTrue("system_hub_all_plans_meta" in utility)
        assertTrue("system_hub_backup_meta" in utility)
        assertTrue("color = MaterialTheme.colorScheme.onSurfaceVariant" in sync)
        assertFalse("Color.Black" in sync)
    }

}
