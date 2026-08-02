package com.mikeyphw.studyflow.core.diagnostics

data class DiagnosticSessionPolicy(
    val enabled: Boolean,
    val sessionId: String,
    val expiresAtEpochMillis: Long? = null,
) {
    init {
        require(sessionId.isNotBlank()) { "Diagnostic session ID must not be blank." }
    }

    fun allowsRecording(nowEpochMillis: Long): Boolean =
        enabled && (expiresAtEpochMillis == null || nowEpochMillis < expiresAtEpochMillis)

    companion object {
        fun disabled(): DiagnosticSessionPolicy = DiagnosticSessionPolicy(
            enabled = false,
            sessionId = "disabled",
        )

        fun debug(sessionId: String): DiagnosticSessionPolicy = DiagnosticSessionPolicy(
            enabled = true,
            sessionId = sessionId,
        )

        fun timeLimited(
            sessionId: String,
            nowEpochMillis: Long,
            durationMillis: Long,
        ): DiagnosticSessionPolicy {
            require(durationMillis > 0L) { "Diagnostic session duration must be positive." }
            return DiagnosticSessionPolicy(
                enabled = true,
                sessionId = sessionId,
                expiresAtEpochMillis = Math.addExact(nowEpochMillis, durationMillis),
            )
        }
    }
}
