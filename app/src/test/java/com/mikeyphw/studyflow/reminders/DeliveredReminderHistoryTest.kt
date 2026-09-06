package com.mikeyphw.studyflow.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveredReminderHistoryTest {
    @Test
    fun `pruning removes the true oldest delivery and keeps the newest 256`() {
        val history = (0 until 257).fold(emptyList<String>()) { current, index ->
            DeliveredReminderHistory.append(current, "occurrence-$index", maxSize = 256)
        }

        assertEquals(256, history.size)
        assertFalse("occurrence-0" in history)
        assertEquals("occurrence-1", history.first())
        assertEquals("occurrence-256", history.last())
    }

    @Test
    fun `redelivery refreshes recency before pruning`() {
        var history = listOf("oldest", "middle", "newest")
        history = DeliveredReminderHistory.append(history, "oldest", maxSize = 3)
        history = DeliveredReminderHistory.append(history, "replacement", maxSize = 3)

        assertEquals(listOf("newest", "oldest", "replacement"), history)
        assertFalse("middle" in history)
    }

    @Test
    fun `ordered encoding round trips arbitrary reminder identities`() {
        val occurrenceIds = listOf(
            "planned:activity-1:12345",
            "due:10:id:2",
            "unicode:revisão:✓",
            "separator-like:v1;12:still-data",
        )

        val encoded = DeliveredReminderHistory.encode(occurrenceIds)

        assertEquals(occurrenceIds, DeliveredReminderHistory.decode(encoded))
        assertNull(DeliveredReminderHistory.decode("legacy-unversioned-value"))
    }

    @Test
    fun `legacy migration preserves membership within the historical cap`() {
        val legacy = (0 until 256).mapTo(linkedSetOf()) { "legacy-$it" }

        val migrated = DeliveredReminderHistory.migrateLegacy(legacy, maxSize = 256)

        assertEquals(256, migrated.size)
        assertEquals(legacy, migrated.toSet())
        assertTrue(migrated.all { it.startsWith("legacy-") })
    }
}
