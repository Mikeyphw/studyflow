package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StudySessionTimerTest {
    @Test
    fun runningSessionSubtractsPausedSeconds() {
        val session = session(status = StudySessionStatus.RUNNING, startedAt = 1_000, pausedSeconds = 30)

        val activeSeconds = StudySessionTimer.activeSeconds(session, nowEpochMillis = 91_000)

        assertEquals(60, activeSeconds)
    }

    @Test
    fun pausedSessionKeepsActiveDurationAndTracksPauseDuration() {
        val paused = StudySessionTimer.markPaused(
            session = session(status = StudySessionStatus.RUNNING, startedAt = 1_000),
            nowEpochMillis = 61_000,
        )

        assertEquals(StudySessionStatus.PAUSED, paused.status)
        assertEquals(60, paused.grossDurationSeconds)
        assertEquals(5, StudySessionTimer.pausedSeconds(paused, nowEpochMillis = 66_000))
    }

    @Test
    fun completedSessionFreezesElapsedTime() {
        val completed = StudySessionTimer.markEnded(
            session = session(status = StudySessionStatus.RUNNING, startedAt = 1_000),
            status = StudySessionStatus.COMPLETED,
            nowEpochMillis = 121_000,
        )

        assertEquals(StudySessionStatus.COMPLETED, completed.status)
        assertEquals(120, StudySessionTimer.activeSeconds(completed, nowEpochMillis = 999_000))
    }

    private fun session(
        status: StudySessionStatus,
        startedAt: Long,
        pausedSeconds: Long = 0,
    ) = StudySession(
        id = StudySessionId("session-1"),
        studyPlanId = StudyPlanId("plan-1"),
        plannedActivityId = null,
        subjectId = null,
        topicId = null,
        materialId = null,
        videoResourceId = null,
        type = StudyActivityType.THEORY,
        status = status,
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = if (status == StudySessionStatus.PAUSED) startedAt else null,
        grossDurationSeconds = 0,
        pauseDurationSeconds = pausedSeconds,
        pagesCompleted = 0,
        questionsAnswered = 0,
        correctAnswers = 0,
        concentrationRating = null,
        difficultyRating = null,
        notes = "Theory",
    )
}
