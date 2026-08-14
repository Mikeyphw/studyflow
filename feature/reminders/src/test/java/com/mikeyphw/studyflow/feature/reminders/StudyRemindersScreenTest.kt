package com.mikeyphw.studyflow.feature.reminders

import org.junit.Assert.assertEquals
import java.nio.file.Paths
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRemindersScreenTest {
    @Test
    fun parsesMinuteOfDay() {
        assertEquals(8 * 60 + 30, parseMinuteOfDay("08:30"))
        assertNull(parseMinuteOfDay("24:00"))
        assertNull(parseMinuteOfDay("8:30"))
    }

    @Test
    fun formatsMinuteOfDay() {
        assertEquals("22:05", formatMinuteOfDay(22 * 60 + 5))
    }
    @Test
    fun preferenceSwitchUsesOneMergedLabeledTarget() {
        val module = Paths.get("src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt")
        val root = Paths.get("feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt")
        val source = listOf(module, root).first { it.toFile().isFile }.toFile().readText()
        assertTrue(source.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(source.contains("role = Role.Switch"))
        assertTrue(source.contains("Switch(checked = checked, onCheckedChange = null)"))
    }

}
