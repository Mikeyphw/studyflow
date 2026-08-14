package com.mikeyphw.studyflow.feature.schedule

import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAccessibilityContractTest {
    @Test
    fun `boolean rows are single checkbox targets and dialogs remain scrollable`() {
        val source = readSource()
        assertTrue(source.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(source.contains("role = Role.Checkbox"))
        assertTrue(source.contains("Checkbox(checked = checked, onCheckedChange = null)"))
        assertTrue(source.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(source.contains(".horizontalScroll(rememberScrollState())"))
    }

    private fun readSource(): String {
        val module = Paths.get("src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleDialogs.kt")
        val root = Paths.get("feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleDialogs.kt")
        return listOf(module, root).first { it.toFile().isFile }.toFile().readText()
    }
}
