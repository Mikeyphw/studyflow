package com.mikeyphw.studyflow.feature.schedule

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov03ResponsiveScheduleContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    @Test
    fun `schedule wraps controls and keeps destructive actions in accessible overflow`() {
        val source = repoRoot().resolve(
            "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
        ).toFile().readText()

        assertTrue(source.contains("FlowRow("))
        assertTrue(source.contains("Icons.Outlined.MoreVert"))
        assertTrue(source.contains("DeleteOverflowMenu("))
        assertTrue(source.contains("DropdownMenuItem("))
        assertTrue(source.contains("R.string.more_actions"))
        assertTrue(source.contains("confirming = true"))
        assertFalse(source.contains("ConfirmingDeleteButton("))
    }
}
