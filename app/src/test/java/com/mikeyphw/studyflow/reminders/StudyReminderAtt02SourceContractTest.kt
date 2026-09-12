package com.mikeyphw.studyflow.reminders

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReminderAtt02SourceContractTest {
    @Test
    fun notificationActionsCarrySelfContainedOccurrencePayload() {
        val source = repoFile("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt").readText()
        val receiver = repoFile("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderReceivers.kt").readText()

        assertTrue(source.contains("StudyReminderExtraContentKind"))
        assertTrue(source.contains("StudyReminderExtraScheduledAt"))
        assertTrue(source.contains("StudyReminderExtraFireAt"))
        assertTrue(receiver.contains("parseReminderCandidate"))
        assertTrue(receiver.contains("sourceCandidate = intent.parseReminderCandidate(parsed)"))
    }

    @Test
    fun androidAttentionUsesFourIndependentNotificationChannels() {
        val source = repoFile("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt").readText()
        listOf(
            "study_schedule",
            "study_reviews",
            "study_session_timers",
            "study_summaries",
        ).forEach { channel -> assertTrue(source.contains(channel)) }
        assertTrue(source.contains("deleteNotificationChannel(StudyReminderLegacyChannelId)"))
    }

    private fun repoFile(relative: String): Path {
        var current = Path.of("").toAbsolutePath()
        repeat(8) {
            val candidate = current.resolve(relative)
            if (Files.isRegularFile(candidate)) return candidate
            current = current.parent ?: return@repeat
        }
        error("Cannot locate repository file $relative")
    }
}
