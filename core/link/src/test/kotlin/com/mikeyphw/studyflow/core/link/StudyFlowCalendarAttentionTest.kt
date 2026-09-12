package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyFlowCalendarAttentionTest {
    private val workspaceId = StudyFlowWorkspaceId("workspace-att09")

    @Test
    fun exportCreatesIcsAndAttentionOccurrences() {
        val exporter = StudyFlowCalendarAttentionExporter()
        val feed = exporter.export(
            items = listOf(
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("activity-1"),
                    workspaceId = workspaceId,
                    title = "Constitution review",
                    body = "Read articles and complete spaced review.",
                    kind = StudyFlowCalendarEventKind.STUDY_BLOCK,
                    startEpochMillis = 1_800_000L,
                    durationMinutes = 50,
                    timezoneId = "America/Sao_Paulo",
                    routeHash = "#today",
                    attentionPriority = StudyFlowAttentionPriority.HIGH,
                    reminderLeadMinutes = 15,
                ),
            ),
            nowEpochMillis = 1_000_000L,
        )
        assertEquals(1, feed.events.size)
        assertEquals(1, feed.diagnostics.attentionOccurrenceCount)
        assertTrue(feed.icsText.contains("BEGIN:VCALENDAR"))
        assertTrue(feed.icsText.contains("BEGIN:VEVENT"))
        assertTrue(feed.icsText.contains("TRIGGER:-PT15M"))
        val occurrence = assertNotNull(feed.events.single().attentionOccurrence)
        assertEquals(StudyFlowAttentionCategory.PLANNED_STUDY, occurrence.category)
        assertEquals(900_000L, occurrence.scheduledAtEpochMillis)
        assertEquals("#today", occurrence.payload["routeHash"]?.toString()?.trim('"'))
    }

    @Test
    fun privacyModesHideSensitiveCalendarCopy() {
        val exporter = StudyFlowCalendarAttentionExporter()
        val feed = exporter.export(
            items = listOf(
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("private-block"),
                    workspaceId = workspaceId,
                    title = "Sensitive weak area drill",
                    body = "This should not leave the app.",
                    kind = StudyFlowCalendarEventKind.REVIEW,
                    startEpochMillis = 2_000_000L,
                ),
            ),
            nowEpochMillis = 1_000_000L,
            preferences = StudyFlowCalendarExportPreferences(
                privacyMode = StudyFlowCalendarPrivacyMode.BUSY_ONLY,
                includeReviews = true,
            ),
        )
        assertEquals("Busy", feed.events.single().summary)
        assertFalse(feed.icsText.contains("Sensitive weak area drill"))
        assertTrue(feed.icsText.contains("Details hidden"))
    }

    @Test
    fun exportSuppressesCompletedReviewsAndOutsideWindowItems() {
        val exporter = StudyFlowCalendarAttentionExporter()
        val feed = exporter.export(
            items = listOf(
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("done"),
                    workspaceId = workspaceId,
                    title = "Done",
                    kind = StudyFlowCalendarEventKind.STUDY_BLOCK,
                    startEpochMillis = 1_100_000L,
                    completed = true,
                ),
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("review"),
                    workspaceId = workspaceId,
                    title = "Review",
                    kind = StudyFlowCalendarEventKind.REVIEW,
                    startEpochMillis = 1_200_000L,
                ),
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("far"),
                    workspaceId = workspaceId,
                    title = "Far",
                    kind = StudyFlowCalendarEventKind.STUDY_BLOCK,
                    startEpochMillis = 500_000_000_000L,
                ),
            ),
            nowEpochMillis = 1_000_000L,
            preferences = StudyFlowCalendarExportPreferences(includeReviews = false, lookaheadDays = 1),
        )
        assertEquals(0, feed.events.size)
        assertEquals(1, feed.diagnostics.completedSuppressedCount)
        assertEquals(1, feed.diagnostics.reviewSuppressedCount)
        assertEquals(1, feed.diagnostics.outsideWindowSuppressedCount)
    }
}
