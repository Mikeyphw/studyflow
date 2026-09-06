package com.mikeyphw.studyflow.reminders

import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDeliveredHistorySourceContractTest {
    @Test
    fun `occurrence store uses ordered persistence for delivered history`() {
        val source = readSource(
            "src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderOccurrenceStore.kt",
            "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderOccurrenceStore.kt",
        )
        val markDelivered = source.substringAfter("fun markDelivered")
            .substringBefore("fun removeDelivered")

        assertTrue(source.contains("deliveredOrderKey(planId)"))
        assertTrue(source.contains("DeliveredReminderHistory.encode(delivered)"))
        assertTrue(source.contains("DeliveredReminderHistory.migrateLegacy"))
        assertTrue(markDelivered.contains("DeliveredReminderHistory.append"))
        assertFalse(markDelivered.contains("getStringSet(deliveredKey"))
        assertFalse(markDelivered.contains("remove(delivered.first())"))
    }

    private fun readSource(modulePath: String, rootPath: String): String {
        val candidates: List<Path> = listOf(Paths.get(modulePath), Paths.get(rootPath))
        return candidates.first { path -> path.toFile().isFile }.toFile().readText()
    }
}
