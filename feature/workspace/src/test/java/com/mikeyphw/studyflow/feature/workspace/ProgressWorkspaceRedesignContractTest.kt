package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressWorkspaceRedesignContractTest {
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
    fun `progress root is shell-native and no longer uses stock Material dashboard chrome`() {
        val progress = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
        )
        assertFalse("Scaffold(" in progress)
        assertFalse("FilterChip(" in progress)
        assertFalse("StudyFlowPrimaryActionCard(" in progress)
        assertFalse("StudyFlowMetricPulse(" in progress)
        assertFalse("StudyFlowTimelineRow(" in progress)
        assertFalse("StudyFlowCoachCard(" in progress)
        assertTrue("StudyFlowPeriodStrip(" in progress)
        assertTrue("StudyFlowAdaptiveWorkspaceFrame(" in progress)
        assertTrue("StudyFlowFocusActionSurface(" in progress)
        assertTrue("StudyFlowProgressBand(" in progress)
        assertTrue("StudyFlowMetric(" in progress)
        assertTrue("StudyFlowTimelineItem(" in progress)
        assertTrue("StudyFlowInsightPanel(" in progress)
        assertTrue("StudyFlowActionRow(" in progress)
    }

    @Test
    fun `expanded progress layout exposes a real supporting pane with accessible progress semantics`() {
        val primitives = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowProgressPrimitives.kt",
        )
        assertTrue("SupportingPaneBreakpoint = 760.dp" in primitives)
        assertTrue("fun StudyFlowAdaptiveWorkspaceFrame(" in primitives)
        assertTrue("fun StudyFlowProgressBand(" in primitives)
        assertTrue("progressBarRangeInfo" in primitives)
        assertTrue("Role.Tab" in primitives)
        assertFalse("Card(" in primitives)
        assertFalse("FilterChip(" in primitives)
        assertFalse("NavigationRail(" in primitives)
    }

    @Test
    fun `workspace header keeps plan navigation while using StudyFlow surface language`() {
        val components = source(
            "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowWorkspaceComponents.kt",
        )
        val header = components
            .substringAfter("fun StudyFlowWorkspaceHeader(")
            .substringBefore("fun shouldStackWorkspacePair(")
        assertTrue("onPlanClick" in header)
        assertTrue("onSearchClick" in header)
        assertTrue("onSystemClick" in header)
        assertTrue(".clip(RoundedCornerShape" in header)
        assertTrue("surfaceContainerHigh.copy(alpha = 0.72f)" in header)
        assertFalse("Surface(" in header)
        assertFalse("PrimaryScrollableTabRow(" in components)
        assertFalse("StudyFlowPrimaryActionCard(" in components)
        assertFalse("StudyFlowCoachCard(" in components)
        assertFalse("StudyFlowWorkspaceTabs(" in components)
    }

    @Test
    fun `final shell preserves canonical navigation and repository-backed active sessions`() {
        val navigation = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
        val shell = source("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")
        assertFalse("NavigationSuiteScaffold" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.TODAY" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.PLAN" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.PRACTICE" in navigation)
        assertTrue("StudyFlowWorkspaceRoot.PROGRESS" in navigation)
        assertTrue("observeActiveSessions()" in navigation)
        assertTrue("StudyFlowActiveSessionBar(" in shell)
    }
}
