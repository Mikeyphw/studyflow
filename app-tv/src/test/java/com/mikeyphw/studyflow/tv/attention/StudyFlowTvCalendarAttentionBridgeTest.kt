package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowCalendarAttentionExporter
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarEventKind
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarScheduleItem
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowTvCalendarAttentionBridgeTest {
    @Test
    fun tvPreviewDoesNotClaimCalendarProviderExport() {
        val feed = StudyFlowCalendarAttentionExporter().export(
            items = listOf(
                StudyFlowCalendarScheduleItem(
                    itemId = StudyFlowEntityId("tv-calendar-1"),
                    workspaceId = StudyFlowWorkspaceId("workspace"),
                    title = "TV preview block",
                    kind = StudyFlowCalendarEventKind.STUDY_BLOCK,
                    startEpochMillis = 1_700_000L,
                ),
            ),
            nowEpochMillis = 1_000_000L,
        )
        val preview = StudyFlowTvCalendarAttentionBridge().preview(feed)
        assertFalse(preview.exportOnTvSupported)
        assertTrue(preview.continueElsewhereReason.contains("phone or desktop"))
    }
}
