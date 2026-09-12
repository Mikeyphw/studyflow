package com.mikeyphw.studyflow.reminders

import com.mikeyphw.studyflow.core.link.StudyFlowCalendarAttentionExporter
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportFeed
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportPreferences
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarScheduleItem

/**
 * Android-side ATT09 bridge. It deliberately returns an ICS payload and attention occurrences;
 * it does not register a hidden calendar account or background sync adapter without user export.
 */
class AndroidStudyFlowCalendarAttentionBridge(
    private val exporter: StudyFlowCalendarAttentionExporter = StudyFlowCalendarAttentionExporter(),
) {
    fun buildExport(
        items: List<StudyFlowCalendarScheduleItem>,
        nowEpochMillis: Long,
        preferences: StudyFlowCalendarExportPreferences = StudyFlowCalendarExportPreferences(),
    ): StudyFlowCalendarExportFeed = exporter.export(items, nowEpochMillis, preferences)

    fun diagnostics(feed: StudyFlowCalendarExportFeed): AndroidStudyFlowCalendarAttentionDiagnostics =
        AndroidStudyFlowCalendarAttentionDiagnostics(
            eventCount = feed.events.size,
            attentionOccurrenceCount = feed.diagnostics.attentionOccurrenceCount,
            userInitiatedExportOnly = true,
            provider = feed.preferences.provider.name.lowercase(),
            privacyMode = feed.preferences.privacyMode.name.lowercase(),
        )
}

data class AndroidStudyFlowCalendarAttentionDiagnostics(
    val eventCount: Int,
    val attentionOccurrenceCount: Int,
    val userInitiatedExportOnly: Boolean,
    val provider: String,
    val privacyMode: String,
)
