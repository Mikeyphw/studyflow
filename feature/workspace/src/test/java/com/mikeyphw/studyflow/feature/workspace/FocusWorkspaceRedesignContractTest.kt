package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusWorkspaceRedesignContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    private fun source(relative: String): String = Files.readString(repoRoot().resolve(relative))

    @Test
    fun `today is a shell-native workspace without nested Material scaffold or fab`() {
        val today = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        )
        assertFalse("Scaffold(" in today)
        assertFalse("FloatingActionButton(" in today)
        assertFalse("StudyFlowPrimaryActionCard(" in today)
        assertTrue("StudyFlowFocusActionSurface(" in today)
        assertTrue("StudyFlowTimelineItem(" in today)
        assertTrue("StudyFlowMetricStrip(" in today)
        assertTrue("StudyFlowFloatingAction(" in today)
        assertTrue("onOpenFocus" in today)
        assertTrue("onOpenReviews" in today)
        assertTrue("onOpenSchedule" in today)
        assertTrue("onOpenRecommendation" in today)
        assertTrue("onQuickAdd" in today)
    }

    @Test
    fun `plan keeps four route tabs but uses the StudyFlow tab strip and row actions`() {
        val plan = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
        )
        assertFalse("Scaffold(" in plan)
        assertFalse("StudyFlowWorkspaceTabs(" in plan)
        assertFalse("PlanActionButton(" in plan)
        assertTrue("StudyFlowTabStrip(" in plan)
        assertTrue("PlanWorkspaceTab.entries" in plan)
        assertTrue("PlanWorkspaceTab.OVERVIEW" in plan)
        assertTrue("PlanWorkspaceTab.SCHEDULE" in plan)
        assertTrue("PlanWorkspaceTab.CONTENT" in plan)
        assertTrue("PlanWorkspaceTab.LIBRARY" in plan)
        assertTrue("StudyFlowActionRow(" in plan)
        assertTrue("onEditPlan" in plan)
        assertTrue("onPlanSettings" in plan)
        assertTrue("onSessions" in plan)
        assertTrue("onReminders" in plan)
        assertTrue("onPortability" in plan)
        assertTrue("onManageSchedule" in plan)
        assertTrue("onManageContent" in plan)
        assertTrue("onManageLibrary" in plan)
    }

    @Test
    fun `workspace primitives do not reintroduce stock Material tabs or cards`() {
        val primitives = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowFocusWorkspacePrimitives.kt",
        )
        assertTrue("fun <T> StudyFlowTabStrip(" in primitives)
        assertTrue("Role.Tab" in primitives)
        assertTrue("fun StudyFlowFocusActionSurface(" in primitives)
        assertTrue("fun StudyFlowTimelineItem(" in primitives)
        assertTrue("fun StudyFlowMetricStrip(" in primitives)
        assertFalse("PrimaryScrollableTabRow(" in primitives)
        assertFalse("Card(" in primitives)
        assertFalse("FloatingActionButton(" in primitives)
    }
}
