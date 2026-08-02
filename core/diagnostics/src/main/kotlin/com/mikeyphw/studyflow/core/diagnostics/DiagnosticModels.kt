package com.mikeyphw.studyflow.core.diagnostics

import java.io.File

/** Stable subsystem labels used by the Debug Workbench timeline and support reports. */
enum class DiagnosticArea {
    APP_LIFECYCLE,
    INITIALIZATION,
    DATABASE,
    STUDY_PLANS,
    SCHEDULE,
    STUDY_SESSIONS,
    PRACTICE,
    REVIEWS,
    REMINDERS,
    NOTIFICATIONS,
    WIDGET,
    BACKUP_RESTORE,
    ANNEX_STORAGE,
    SEARCH_RECOMMENDATIONS,
    SYNC_FOUNDATION,
    PREFERENCES,
    VALIDATION,
}

enum class DiagnosticSeverity {
    TRACE,
    INFO,
    WARNING,
    ERROR,
}

enum class DiagnosticOutcome {
    STARTED,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    CANCELLED,
}

data class DiagnosticExceptionSummary(
    val type: String,
    val fingerprint: String,
)

data class DiagnosticEvent(
    val sessionId: String,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val area: DiagnosticArea,
    val severity: DiagnosticSeverity,
    val action: String,
    val outcome: DiagnosticOutcome,
    val durationMillis: Long? = null,
    val correlationId: String? = null,
    val safeDetails: Map<String, String> = emptyMap(),
    val exception: DiagnosticExceptionSummary? = null,
) {
    fun sanitized(): DiagnosticEvent = copy(
        sessionId = DiagnosticRedactor.safeSessionId(sessionId),
        action = DiagnosticRedactor.safeAction(action),
        correlationId = correlationId?.let(DiagnosticRedactor::fingerprint),
        safeDetails = DiagnosticRedactor.redactDetails(safeDetails),
        exception = exception?.let(DiagnosticRedactor::sanitizeExceptionSummary),
    )

    fun toJsonLine(): String = DiagnosticJson.encodeEvent(sanitized())
}

/** Injectable sink. Production call sites can keep a no-op implementation with zero file side effects. */
interface DiagnosticRecorder {
    val enabled: Boolean

    fun record(event: DiagnosticEvent)

    fun record(
        area: DiagnosticArea,
        action: String,
        outcome: DiagnosticOutcome,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        durationMillis: Long? = null,
        correlationId: String? = null,
        safeDetails: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
        timestampEpochMillis: Long = System.currentTimeMillis(),
    ) {
        if (!enabled) return
        record(
            DiagnosticEvent(
                sessionId = "current",
                sequence = 0L,
                timestampEpochMillis = timestampEpochMillis,
                area = area,
                severity = severity,
                action = action,
                outcome = outcome,
                durationMillis = durationMillis,
                correlationId = correlationId,
                safeDetails = safeDetails,
                exception = throwable?.let(DiagnosticRedactor::summarizeThrowable),
            ),
        )
    }

    fun flush(timeoutMillis: Long = 2_000L): Boolean = true
}

interface DiagnosticStore : DiagnosticRecorder {
    val droppedEventCount: Long
    val writerFailureFingerprint: String?

    fun copySanitizedTimeline(maxChars: Int = 64 * 1024): String
    fun clear(): Boolean
    fun exportSupportBundle(destinationZip: File, metadata: Map<String, String> = emptyMap()): File?
}

object NoOpDiagnosticRecorder : DiagnosticStore {
    override val enabled: Boolean = false
    override val droppedEventCount: Long = 0L
    override val writerFailureFingerprint: String? = null

    override fun record(event: DiagnosticEvent) = Unit
    override fun copySanitizedTimeline(maxChars: Int): String = ""
    override fun clear(): Boolean = true
    override fun exportSupportBundle(destinationZip: File, metadata: Map<String, String>): File? = null
}

interface DiagnosticRecorderProvider {
    val diagnosticRecorder: DiagnosticStore
}
