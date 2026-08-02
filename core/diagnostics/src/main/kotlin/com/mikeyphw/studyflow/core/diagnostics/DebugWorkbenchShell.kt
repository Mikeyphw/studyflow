package com.mikeyphw.studyflow.core.diagnostics

enum class DiagnosticRecorderHealth {
    DISABLED,
    HEALTHY,
    DEGRADED,
    FAILED,
}

data class DebugWorkbenchShellReport(
    val recorderHealth: DiagnosticRecorderHealth,
    val recordingEnabled: Boolean,
    val eventCount: Int,
    val warningCount: Int,
    val errorCount: Int,
    val failedEventCount: Int,
    val malformedLineCount: Int,
    val droppedEventCount: Long,
    val writerFailureFingerprint: String?,
    val latestTimestampEpochMillis: Long?,
    val canCopyTimeline: Boolean,
    val canClearHistory: Boolean,
    val canExportSupportBundle: Boolean,
) {
    fun toClipboardReport(): String = buildString {
        appendLine("StudyFlow Debug Workbench")
        appendLine("Recorder: ${recorderHealth.name.lowercase()}")
        appendLine("Recording enabled: $recordingEnabled")
        appendLine("Timeline events: $eventCount")
        appendLine("Warnings: $warningCount")
        appendLine("Errors: $errorCount")
        appendLine("Failed events: $failedEventCount")
        appendLine("Malformed lines: $malformedLineCount")
        appendLine("Dropped events: $droppedEventCount")
        appendLine("Writer failure: ${writerFailureFingerprint ?: "none"}")
        appendLine("Automatic upload: false")
    }
}

object DebugWorkbenchShellPolicy {
    fun evaluate(
        store: DiagnosticStore,
        timeline: DiagnosticTimelineSnapshot,
    ): DebugWorkbenchShellReport {
        val writerFailure = store.writerFailureFingerprint
        val health = when {
            writerFailure != null -> DiagnosticRecorderHealth.FAILED
            !store.enabled && timeline.eventCount == 0 -> DiagnosticRecorderHealth.DISABLED
            store.droppedEventCount > 0L || timeline.malformedLineCount > 0 -> DiagnosticRecorderHealth.DEGRADED
            else -> DiagnosticRecorderHealth.HEALTHY
        }
        return DebugWorkbenchShellReport(
            recorderHealth = health,
            recordingEnabled = store.enabled,
            eventCount = timeline.eventCount,
            warningCount = timeline.warningCount,
            errorCount = timeline.errorCount,
            failedEventCount = timeline.failedCount,
            malformedLineCount = timeline.malformedLineCount,
            droppedEventCount = store.droppedEventCount,
            writerFailureFingerprint = writerFailure,
            latestTimestampEpochMillis = timeline.latestTimestampEpochMillis,
            canCopyTimeline = timeline.eventCount > 0,
            canClearHistory = timeline.eventCount > 0 || store.droppedEventCount > 0L || writerFailure != null,
            canExportSupportBundle = store.enabled || timeline.eventCount > 0 || writerFailure != null,
        )
    }
}
