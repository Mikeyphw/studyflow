package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportDiagnostics
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportFeed

/**
 * TV projection for ATT09. TV can preview a calendar feed and route to phone/desktop for file export;
 * it never writes a calendar provider or claims background calendar sync from a lean-back client.
 */
class StudyFlowTvCalendarAttentionBridge {
    fun preview(feed: StudyFlowCalendarExportFeed): StudyFlowTvCalendarAttentionPreview =
        StudyFlowTvCalendarAttentionPreview(
            title = feed.preferences.calendarName,
            eventCount = feed.events.size,
            attentionCount = feed.diagnostics.attentionOccurrenceCount,
            exportOnTvSupported = false,
            continueElsewhereReason = "TV mode previews ATT09 calendar attention, then hands export to a phone or desktop.",
            diagnostics = feed.diagnostics,
        )
}

data class StudyFlowTvCalendarAttentionPreview(
    val title: String,
    val eventCount: Int,
    val attentionCount: Int,
    val exportOnTvSupported: Boolean,
    val continueElsewhereReason: String,
    val diagnostics: StudyFlowCalendarExportDiagnostics,
)
