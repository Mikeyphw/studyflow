package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowCalendarAttentionExporter
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportFeed
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportPreferences
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarScheduleItem

class DesktopCalendarAttentionExportBridge(
    private val exporter: StudyFlowCalendarAttentionExporter = StudyFlowCalendarAttentionExporter(),
) {
    fun buildExport(
        items: List<StudyFlowCalendarScheduleItem>,
        nowEpochMillis: Long,
        preferences: StudyFlowCalendarExportPreferences = StudyFlowCalendarExportPreferences(),
    ): StudyFlowCalendarExportFeed = exporter.export(items, nowEpochMillis, preferences)

    fun suggestedFilename(feed: StudyFlowCalendarExportFeed): String =
        feed.preferences.calendarName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "studyflow-schedule" } + ".ics"
}
