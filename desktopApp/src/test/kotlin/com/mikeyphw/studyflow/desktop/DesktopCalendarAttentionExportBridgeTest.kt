package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowCalendarEventKind
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportPreferences
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarScheduleItem
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopCalendarAttentionExportBridgeTest {
    @Test
    fun suggestsPortableIcsFilenameForDesktopExport() {
        val bridge = DesktopCalendarAttentionExportBridge()
        val feed = bridge.buildExport(
            items = listOf(
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("desktop-calendar-1"),
                    workspaceId = StudyFlowWorkspaceId("workspace"),
                    title = "Desktop calendar block",
                    kind = StudyFlowCalendarEventKind.STUDY_BLOCK,
                    startEpochMillis = 1_700_000L,
                ),
            ),
            nowEpochMillis = 1_000_000L,
            preferences = StudyFlowCalendarExportPreferences(calendarName = "StudyFlow Desktop Schedule"),
        )
        assertEquals("studyflow-desktop-schedule.ics", bridge.suggestedFilename(feed))
        assertTrue(feed.icsText.contains("X-STUDYFLOW-ATTENTION-INTEGRATION:ATT09"))
    }
}
