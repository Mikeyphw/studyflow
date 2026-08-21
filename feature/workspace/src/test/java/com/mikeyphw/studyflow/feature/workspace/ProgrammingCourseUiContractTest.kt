package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammingCourseUiContractTest {
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
    fun `programming Today panel uses shell primitives and syntax code blocks`() {
        val panel = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/" +
                "ProgrammingCourseWorkspacePanel.kt",
        )

        assertFalse("Scaffold(" in panel)
        assertFalse("TopAppBar(" in panel)
        assertTrue("StudyFlowFocusActionSurface" in panel)
        assertTrue("StudyFlowMetricStrip" in panel)
        assertTrue("StudyFlowCodeBlock" in panel)
        assertTrue("ProgrammingLanguage.KOTLIN" in panel)
    }

    @Test
    fun `Today and Plan routes expose programming mode labels`() {
        val today = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
        )
        val plan = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
        )

        assertTrue("StudyContentMode.PROGRAMMING_COURSE" in today)
        assertTrue("ProgrammingCourseWorkspacePanel" in today)
        assertTrue("termuxRuntimeReady = plan.contentMode.capabilities.supportsTermuxRuntime" in today)
        assertTrue("planPrimaryDateLabel(plan)" in plan)
        assertTrue("programming_modules" in plan)
        assertTrue("programming_resources" in plan)
    }
}
