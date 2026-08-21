package com.mikeyphw.studyflow.feature.progress

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyProgressShellPolishContractTest {
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
    fun `goals and notes route uses shell polish primitives`() {
        val progress = source(
            "feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/StudyProgressScreen.kt",
        )

        assertFalse("Scaffold(" in progress)
        assertFalse("TopAppBar(" in progress)
        assertFalse("FilterChip(" in progress)
        assertFalse("StudyFlowSectionCard(" in progress)
        assertTrue("WindowInsets.safeDrawing" in progress)
        assertTrue("contentColor = MaterialTheme.colorScheme.onBackground" in progress)
        assertTrue("StudyFlowMetricStrip(" in progress)
        assertTrue("StudyFlowTabStrip(" in progress)
        assertTrue("StudyFlowTimelineItem(" in progress)
        assertTrue("StudyFlowActionOrb(" in progress)
    }

    @Test
    fun `goals and notes keeps dark readable text tokens`() {
        val progress = source(
            "feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/StudyProgressScreen.kt",
        )

        assertFalse("Color.Black" in progress)
        assertFalse("Color.DarkGray" in progress)
        assertTrue("MaterialTheme.colorScheme.onSurfaceVariant" in progress)
        assertTrue("MaterialTheme.colorScheme.background" in progress)
    }
}
