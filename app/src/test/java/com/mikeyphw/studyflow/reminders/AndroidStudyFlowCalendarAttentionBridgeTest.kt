package com.mikeyphw.studyflow.reminders

import com.mikeyphw.studyflow.core.link.StudyFlowCalendarEventKind
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarScheduleItem
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidStudyFlowCalendarAttentionBridgeTest {
    @Test
    fun bridgeBuildsUserInitiatedIcsExportWithoutSilentCalendarMutation() {
        val bridge = AndroidStudyFlowCalendarAttentionBridge()
        val feed = bridge.buildExport(
            items = listOf(
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("android-calendar-1"),
                    workspaceId = StudyFlowWorkspaceId("workspace"),
                    title = "Android exported block",
                    kind = StudyFlowCalendarEventKind.STUDY_BLOCK,
                    startEpochMillis = 1_700_000L,
                ),
            ),
            nowEpochMillis = 1_000_000L,
        )
        val diagnostics = bridge.diagnostics(feed)
        assertEquals(1, diagnostics.eventCount)
        assertEquals(1, diagnostics.attentionOccurrenceCount)
        assertTrue(diagnostics.userInitiatedExportOnly)
        assertTrue(feed.icsText.contains("Android exported block"))
    }
}
