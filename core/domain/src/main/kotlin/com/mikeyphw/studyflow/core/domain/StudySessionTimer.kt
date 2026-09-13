package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus

object StudySessionTimer {
    /**
     * Returns active study seconds. The historical `grossDurationSeconds` persistence column stores
     * active seconds at pause/terminal checkpoints; `pauseDurationSeconds` is independent telemetry
     * and must never be subtracted from that persisted value a second time.
     */
    fun activeSeconds(session: StudySession, nowEpochMillis: Long): Long = when (session.status) {
        StudySessionStatus.RUNNING -> runningActiveSeconds(session, nowEpochMillis)
        StudySessionStatus.PAUSED,
        StudySessionStatus.COMPLETED,
        StudySessionStatus.CANCELLED,
        -> session.grossDurationSeconds.coerceAtLeast(0)
    }

    fun persistedActiveSeconds(session: StudySession): Long = session.grossDurationSeconds.coerceAtLeast(0L)

    fun pausedSeconds(session: StudySession, nowEpochMillis: Long): Long = when (session.status) {
        StudySessionStatus.PAUSED -> {
            val pausedAt = session.endedAtEpochMillis ?: nowEpochMillis
            session.pauseDurationSeconds + elapsedSeconds(pausedAt, nowEpochMillis)
        }
        else -> session.pauseDurationSeconds
    }.coerceAtLeast(0)

    fun markPaused(session: StudySession, nowEpochMillis: Long): StudySession {
        require(session.status == StudySessionStatus.RUNNING) { "Only a running Study Session can be paused." }
        return session.copy(
            status = StudySessionStatus.PAUSED,
            endedAtEpochMillis = nowEpochMillis,
            grossDurationSeconds = activeSeconds(session, nowEpochMillis),
        )
    }

    fun markResumed(session: StudySession, nowEpochMillis: Long): StudySession {
        require(session.status == StudySessionStatus.PAUSED) { "Only a paused Study Session can be resumed." }
        return session.copy(
            status = StudySessionStatus.RUNNING,
            endedAtEpochMillis = null,
            pauseDurationSeconds = pausedSeconds(session, nowEpochMillis),
        )
    }

    fun markEnded(session: StudySession, status: StudySessionStatus, nowEpochMillis: Long): StudySession {
        require(status == StudySessionStatus.COMPLETED || status == StudySessionStatus.CANCELLED) {
            "Study Session can only end as completed or cancelled."
        }
        return session.copy(
            status = status,
            endedAtEpochMillis = nowEpochMillis,
            grossDurationSeconds = activeSeconds(session, nowEpochMillis),
            pauseDurationSeconds = pausedSeconds(session, nowEpochMillis),
        )
    }

    private fun runningActiveSeconds(session: StudySession, nowEpochMillis: Long): Long =
        (elapsedSeconds(session.startedAtEpochMillis, nowEpochMillis) - session.pauseDurationSeconds)
            .coerceAtLeast(session.grossDurationSeconds)

    private fun elapsedSeconds(startEpochMillis: Long, endEpochMillis: Long): Long =
        ((endEpochMillis - startEpochMillis).coerceAtLeast(0L) / 1_000L)
}
