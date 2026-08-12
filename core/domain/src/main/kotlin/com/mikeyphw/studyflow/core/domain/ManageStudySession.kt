package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.VideoResourceId

class ManageStudySession(
    private val repository: StudySessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSessions(planId: StudyPlanId) = repository.observeSessions(planId)

    suspend fun startQuickSession(
        sessionId: StudySessionId,
        studyPlanId: StudyPlanId,
        title: String,
        type: StudyActivityType,
        subjectId: SubjectId? = null,
        topicId: TopicId? = null,
        materialId: StudyMaterialId? = null,
        videoResourceId: VideoResourceId? = null,
        targetDurationMinutes: Int? = null,
    ): StudySession {
        require(title.isNotBlank()) { "Quick Study Session title cannot be blank." }
        require(targetDurationMinutes == null || targetDurationMinutes in 5..240) {
            "Target duration must be between 5 and 240 minutes."
        }
        require(repository.findActiveSession(studyPlanId) == null) {
            "Finish or cancel the active Study Session before starting another one."
        }
        return repository.createSession(
            StudySession(
                id = sessionId,
                studyPlanId = studyPlanId,
                plannedActivityId = null,
                subjectId = subjectId,
                topicId = topicId,
                materialId = materialId,
                videoResourceId = videoResourceId,
                type = type,
                status = StudySessionStatus.RUNNING,
                startedAtEpochMillis = clock(),
                endedAtEpochMillis = null,
                grossDurationSeconds = 0,
                pauseDurationSeconds = 0,
                pagesCompleted = 0,
                questionsAnswered = 0,
                correctAnswers = 0,
                concentrationRating = null,
                difficultyRating = null,
                notes = title.trim(),
                targetDurationMinutes = targetDurationMinutes,
            ),
        )
    }

    suspend fun startFromActivity(sessionId: StudySessionId, activity: PlannedActivity): StudySession {
        require(activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)) {
            "Only planned or missed activities can start a Study Session."
        }
        require(repository.findActiveSession(activity.studyPlanId) == null) {
            "Finish or cancel the active Study Session before starting another one."
        }
        return repository.createSession(
            StudySession(
                id = sessionId,
                studyPlanId = activity.studyPlanId,
                plannedActivityId = activity.id,
                subjectId = activity.subjectId,
                topicId = activity.topicId,
                materialId = activity.materialId,
                videoResourceId = activity.videoResourceId,
                type = activity.type,
                status = StudySessionStatus.RUNNING,
                startedAtEpochMillis = clock(),
                endedAtEpochMillis = null,
                grossDurationSeconds = 0,
                pauseDurationSeconds = 0,
                pagesCompleted = 0,
                questionsAnswered = 0,
                correctAnswers = 0,
                concentrationRating = null,
                difficultyRating = null,
                notes = activity.title,
                targetDurationMinutes = activity.plannedDurationMinutes,
            ),
            linkedActivityExpectedStatus = activity.status,
        )
    }

    suspend fun pause(planId: StudyPlanId, sessionId: StudySessionId): StudySession {
        val session = requireSession(planId, sessionId)
        return repository.transitionSession(
            expectedStatus = StudySessionStatus.RUNNING,
            session = StudySessionTimer.markPaused(session, clock()),
        )
    }

    suspend fun resume(planId: StudyPlanId, sessionId: StudySessionId): StudySession {
        val session = requireSession(planId, sessionId)
        return repository.transitionSession(
            expectedStatus = StudySessionStatus.PAUSED,
            session = StudySessionTimer.markResumed(session, clock()),
        )
    }

    suspend fun complete(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        pagesCompleted: Int,
        questionsAnswered: Int,
        correctAnswers: Int,
        concentrationRating: Int?,
        difficultyRating: Int?,
        notes: String,
    ): StudySession {
        require(pagesCompleted >= 0) { "Completed pages cannot be negative." }
        require(questionsAnswered >= 0) { "Answered questions cannot be negative." }
        require(correctAnswers in 0..questionsAnswered) { "Correct answers must be within answered questions." }
        require(concentrationRating == null || concentrationRating in 1..5) {
            "Concentration rating must be between 1 and 5."
        }
        require(difficultyRating == null || difficultyRating in 1..5) {
            "Difficulty rating must be between 1 and 5."
        }
        val now = clock()
        val current = requireSession(planId, sessionId)
        val finished = StudySessionTimer.markEnded(
            session = current,
            status = StudySessionStatus.COMPLETED,
            nowEpochMillis = now,
        ).copy(
            pagesCompleted = pagesCompleted,
            questionsAnswered = questionsAnswered,
            correctAnswers = correctAnswers,
            concentrationRating = concentrationRating,
            difficultyRating = difficultyRating,
            notes = notes.trim(),
        )
        return repository.transitionSession(
            expectedStatus = current.status,
            session = finished,
            linkedActivityExpectedStatus = PlannedActivityStatus.IN_PROGRESS,
            linkedActivityStatus = PlannedActivityStatus.COMPLETED,
            linkedActivityCompletedAtEpochMillis = now,
        )
    }

    suspend fun cancel(planId: StudyPlanId, sessionId: StudySessionId): StudySession {
        val current = requireSession(planId, sessionId)
        val cancelled = StudySessionTimer.markEnded(
            session = current,
            status = StudySessionStatus.CANCELLED,
            nowEpochMillis = clock(),
        )
        return repository.transitionSession(
            expectedStatus = current.status,
            session = cancelled,
            linkedActivityExpectedStatus = PlannedActivityStatus.IN_PROGRESS,
            linkedActivityStatus = PlannedActivityStatus.PLANNED,
            linkedActivityCompletedAtEpochMillis = null,
        )
    }

    suspend fun activeSession(planId: StudyPlanId): StudySession? = repository.findActiveSession(planId)

    suspend fun updateNotes(
        planId: StudyPlanId,
        sessionId: StudySessionId,
        notes: String,
    ): StudySession {
        return repository.updateNotes(
            planId = planId,
            sessionId = sessionId,
            notes = notes.trim(),
        )
    }

    private suspend fun requireSession(planId: StudyPlanId, sessionId: StudySessionId): StudySession =
        requireNotNull(repository.findSession(planId, sessionId)) {
            "Study Session does not belong to this Study Plan."
        }
}
